import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { createServer, request as httpRequest } from 'node:http'
import { adminCredentials, loginEmployee, newEmployee } from './support/real-backend'
import { clinicDate, slotLabel, type Appointment, type TimeSlot } from '../src/features/appointments/model'

test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要真实后端与 MySQL；不以模拟成功响应代替')
const root = '/api/appointment'
const pictures = resolve('.artifacts/screenshots')
mkdirSync(pictures, { recursive: true })
let token = ''
const headers = () => ({ Authorization: `Bearer ${token}` })
test.beforeAll(async ({ request }) => {
  const login = await request.post('/api/auth/login', { data: adminCredentials() })
  expect(login.status()).toBe(200)
  token = (await login.json() as { accessToken: string }).accessToken
})
async function fixture(request: APIRequestContext) {
  const suffix = randomUUID().slice(0, 8), account = newEmployee(['DOCTOR'])
  account.displayName = `接待联调医生${suffix}`
  const user = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(user.status()).toBe(201)
  const doctorId = (await user.json() as { id: string }).id
  const client = await request.post('/api/owner/clients', { headers: headers(), data: { name: `接待联调客户${suffix}`, phone: '13800138000', address: '接待联调' } }); expect(client.status()).toBe(201)
  const ownerId = (await client.json() as { id: string }).id
  const pet = await request.post(`/api/owner/clients/${ownerId}/pets`, { headers: headers(), data: { details: { name: `接待小满${suffix}`, species: '猫', birthDate: '2024-06-01', weightKg: 4.25 }, birthDateEstimated: true, version: 0 } }); expect(pet.status()).toBe(201)
  const petId = (await pet.json() as { id: string }).id, date = clinicDate()
  const schedule = await request.post(`${root}/schedules`, { headers: headers(), data: { doctorId, startAt: new Date(`${date}T00:00:00+08:00`).toISOString(), endAt: new Date(`${date}T23:59:00+08:00`).toISOString(), slotMinutes: 5 } }); expect(schedule.status()).toBe(201)
  const response = await request.get(`${root}/availability?doctorId=${doctorId}&date=${date}`, { headers: headers() }); expect(response.status()).toBe(200)
  const slots = (await response.json() as TimeSlot[]).filter(s => Date.parse(s.startAt) > Date.now() + 60_000)
  expect(slots.length, '当日测试需要剩余号源，请勿在跨午夜前最后数分钟运行').toBeGreaterThan(6)
  return { doctorId, doctorName: account.displayName, account, ownerId, petId, petName: `接待小满${suffix}`, date, slots }
}
type Fixture = Awaited<ReturnType<typeof fixture>>
async function booking(request: APIRequestContext, data: Fixture, slot = data.slots[0]!) {
  const response = await request.post(`${root}/appointments`, { headers: { ...headers(), 'Idempotency-Key': randomUUID() }, data: { petId: data.petId, doctorId: data.doctorId, ...slot } }); expect(response.status()).toBe(201)
  return response.json() as Promise<Appointment>
}
async function openBooking(page: Page, data: Fixture) {
  await loginEmployee(page, adminCredentials(), `/appointments?petId=${data.petId}`)
  await page.getByRole('button', { name: '新建预约', exact: true }).click()
  await expect(page.locator('.booking-selected')).toContainText(data.petName)
  await page.getByLabel('接诊医生', { exact: true }).last().selectOption(data.doctorId)
  await page.getByRole('button', { name: slotLabel(data.slots[0]!), exact: true }).click()
}
const row = (page: Page, id: string) => page.locator(`[data-appointment-id="${id}"]`)

test('正常实机：未绑定宠物预约、改期、取消、签到、叫号及响应式界面', async ({ page, request }) => {
  const f = await fixture(request), errors: string[] = []
  page.on('pageerror', e => errors.push(e.message))
  await openBooking(page, f)
  let posts = 0
  page.on('request', r => { if (r.method() === 'POST' && new URL(r.url()).pathname === `${root}/appointments`) posts++ })
  const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === `${root}/appointments`)
  await page.getByRole('button', { name: '确认预约', exact: true }).dblclick()
  const created = await response; expect(created.status()).toBe(201)
  const first = await created.json() as Appointment
  await expect(row(page, first.id)).toContainText('待到院'); expect(posts).toBe(1)
  await row(page, first.id).getByRole('button', { name: '改期', exact: true }).click()
  await expect(page.getByLabel('接诊医生', { exact: true }).last()).toBeDisabled()
  await page.getByRole('button', { name: slotLabel(f.slots[1]!), exact: true }).click()
  await page.getByRole('button', { name: '确认改期', exact: true }).click()
  await expect(row(page, first.id)).toContainText(slotLabel(f.slots[1]!))
  await row(page, first.id).getByRole('button', { name: '取消预约', exact: true }).click()
  await page.getByRole('button', { name: '确认取消预约', exact: true }).click()
  await expect(row(page, first.id)).toContainText('已取消')
  const second = await booking(request, f, f.slots[2]!)
  await page.getByRole('button', { name: '刷新预约与候诊', exact: true }).click()
  await row(page, second.id).getByRole('button', { name: '签到', exact: true }).click()
  await page.getByLabel('候诊医生', { exact: true }).selectOption(f.doctorId)
  await expect(page.locator('.reception-queue__list')).toContainText(f.petName)
  await page.screenshot({ path: resolve(pictures, 'appointments-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '叫下一位', exact: true }).click()
  await expect(row(page, second.id)).toContainText('已叫号')
  await expect(page.locator('.reception-feedback')).toContainText(`已叫号：${f.petName}`)
  await expect(page.getByRole('button', { name: '叫下一位', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '医生排班', exact: true }).click()
  await page.getByLabel('接诊医生', { exact: true }).selectOption(f.doctorId)
  await expect(page.locator('.reception-schedules')).toContainText('每个号源 5 分钟')
  await page.getByRole('button', { name: '预约记录', exact: true }).click()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: resolve(pictures, 'appointments-mobile.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(errors).toEqual([])
})

test('异常实测：跨日签到、占用号源、非法状态、错误过滤与越权', async ({ request, page }) => {
  const f = await fixture(request), a = await booking(request, f)
  const body = { petId: f.petId, doctorId: f.doctorId, ...f.slots[0]! }
  expect((await request.post(`${root}/appointments`, { headers: headers(), data: body })).status()).toBe(409)
  // 过去日期的新预约现被一个月预约窗口拒绝；历史签到由后端固定时钟集成测试覆盖。
  for (const offset of [1]) {
    const startAt = new Date(Date.parse(a.startAt) + offset * 86400000).toISOString(), endAt = new Date(Date.parse(a.endAt) + offset * 86400000).toISOString()
    expect((await request.post(`${root}/schedules`, { headers: headers(), data: { doctorId: f.doctorId, startAt, endAt, slotMinutes: 5 } })).status()).toBe(201)
    const other = await request.post(`${root}/appointments`, { headers: headers(), data: { ...body, startAt, endAt } }); expect(other.status()).toBe(201)
    const id = (await other.json() as Appointment).id
    expect((await request.post(`${root}/appointments/${id}/check-in`, { headers: headers() })).status()).toBe(409)
  }
  expect((await request.post(`${root}/appointments/${a.id}/check-in`, { headers: headers() })).status()).toBe(200)
  expect((await request.post(`${root}/appointments/${a.id}/check-in`, { headers: headers() })).status()).toBe(409)
  expect((await request.post(`${root}/appointments/${a.id}/reschedule`, { headers: headers(), data: f.slots[1] })).status()).toBe(409)
  expect((await request.get(`${root}/appointments?date=${f.date}&status=DONE`, { headers: headers() })).status()).toBe(400)
  expect((await request.get(`${root}/doctors`)).status()).toBe(401)
  for (const role of ['OWNER', 'PHARMACY']) {
    const account = newEmployee([role])
    expect((await request.post('/api/identity/users', { headers: headers(), data: account })).status()).toBe(201)
    const login = await request.post('/api/auth/login', { data: { username: account.username, password: account.password } })
    const access = (await login.json() as { accessToken: string }).accessToken
    for (const path of ['/doctors', `/appointments?date=${f.date}`, `/queue?doctorId=${f.doctorId}`]) expect((await request.get(`${root}${path}`, { headers: { Authorization: `Bearer ${access}` } })).status()).toBe(403)
    if (role === 'PHARMACY') { await loginEmployee(page, account, '/appointments'); await expect(page.getByRole('heading', { name: '你没有访问此页面的权限' })).toBeVisible() }
  }
})

test('异常并发实测：抢同一号源仅一单成功，相同幂等键回放同一预约', async ({ request }) => {
  const f = await fixture(request), body = { petId: f.petId, doctorId: f.doctorId, ...f.slots[0]! }
  const responses = await Promise.all(Array.from({ length: 4 }, () => request.post(`${root}/appointments`, { headers: { ...headers(), 'Idempotency-Key': randomUUID() }, data: body })))
  expect(responses.filter(r => r.status() === 201)).toHaveLength(1)
  expect(responses.filter(r => r.status() === 409)).toHaveLength(3)
  const key = randomUUID(), next = { ...body, ...f.slots[1]! }
  const replay = await Promise.all(Array.from({ length: 3 }, () => request.post(`${root}/appointments`, { headers: { ...headers(), 'Idempotency-Key': key }, data: next })))
  for (const r of replay) expect(r.status()).toBe(201)
  expect(new Set(await Promise.all(replay.map(async r => (await r.json() as Appointment).id))).size).toBe(1)
})

test('正常并发实测：四路叫号分别认领四位患者', async ({ request }) => {
  const f = await fixture(request)
  for (const slot of f.slots.slice(0, 4)) {
    const a = await booking(request, f, slot)
    expect((await request.post(`${root}/appointments/${a.id}/check-in`, { headers: headers() })).status()).toBe(200)
  }
  const responses = await Promise.all(Array.from({ length: 4 }, () => request.post(`${root}/queue/call?doctorId=${f.doctorId}`, { headers: headers() })))
  for (const r of responses) expect(r.status()).toBe(200)
  expect(new Set(await Promise.all(responses.map(async r => (await r.json() as Appointment).id))).size).toBe(4)
})

test('异常实机：号源被其他员工占用，明确冲突保留输入并刷新号源', async ({ page, request }) => {
  const f = await fixture(request)
  await openBooking(page, f)
  await booking(request, f)
  await page.getByRole('button', { name: '确认预约', exact: true }).click()
  await expect(page.locator('.booking-form [role=alert]')).toBeVisible()
  await expect(page.locator('.booking-selected')).toContainText(f.petName)
  await expect(page.getByRole('button', { name: slotLabel(f.slots[0]!), exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '确认预约', exact: true })).toBeDisabled()
})

for (const [path, heading] of [['/appointments?**', '预约读取失败'], ['/doctors', '医生目录读取失败'], ['/queue?**', '候诊读取失败']] as const) {
  test(`依赖故障注入：${heading}，错误不作空数据且可恢复`, async ({ page }) => {
    await page.route(`**${root}${path}`, route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"接待依赖不可用"}' }))
    await loginEmployee(page, adminCredentials(), '/appointments')
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible()
    if (heading === '候诊读取失败') await expect(page.getByRole('button', { name: '叫下一位', exact: true })).toBeDisabled()
    await page.unroute(`**${root}${path}`)
    await page.getByRole('button', { name: heading === '预约读取失败' ? '重新读取预约' : heading === '医生目录读取失败' ? '重新读取医生' : '重新读取候诊', exact: true }).click()
    await expect(page.getByRole('heading', { name: heading, exact: true })).toHaveCount(0)
  })
}

for (const fault of ['http503', 'html502', 'malformed201', 'connection-reset', 'timeout', 'offline'] as const) {
  test(`预约写入故障注入：${fault}，保留输入且无自动重放`, async ({ page, request, context }) => {
    const f = await fixture(request)
    await openBooking(page, f)
    let posts = 0
    if (fault === 'offline') await context.setOffline(true)
    else await page.route(`**${root}/appointments`, route => {
      if (route.request().method() !== 'POST') return route.continue()
      posts++
      if (fault === 'connection-reset') return route.abort('connectionreset')
      if (fault === 'timeout') return
      return route.fulfill({ status: fault === 'malformed201' ? 201 : fault === 'html502' ? 502 : 503, contentType: fault === 'html502' ? 'text/html' : 'application/json', body: fault === 'malformed201' ? '{}' : fault === 'html502' ? '<h1>Bad Gateway</h1>' : '{"code":"INTERNAL_ERROR","message":"上游故障"}' })
    })
    await page.getByRole('button', { name: '确认预约', exact: true }).click()
    await expect(page.locator('.booking-form [role=alert]')).toContainText('预约结果未确认', { timeout: 20_000 })
    await expect(page.locator('.booking-selected')).toContainText(f.petName)
    await expect(page.getByLabel('就诊日期（上海时间）')).toBeDisabled()
    await expect(page.getByRole('button', { name: '使用原请求安全重试', exact: true })).toBeEnabled()
    if (fault !== 'offline') expect(posts).toBe(1)
    await context.setOffline(false)
  })
}

test('真实落库后响应丢失：原请求同键显式重试仍只有一份预约', async ({ page, request }) => {
  const f = await fixture(request)
  await openBooking(page, f)
  const keys: string[] = []
  await page.route(`**${root}/appointments`, async route => {
    if (route.request().method() !== 'POST') return route.continue()
    keys.push(route.request().headers()['idempotency-key']!)
    const actual = await route.fetch(); expect(actual.status()).toBe(201)
    if (keys.length === 1) await route.abort('connectionreset')
    else await route.fulfill({ response: actual })
  })
  await page.getByRole('button', { name: '确认预约', exact: true }).click()
  await expect(page.locator('.booking-form [role=alert]')).toContainText('预约结果未确认')
  await page.screenshot({ path: resolve(pictures, 'appointment-result-unknown.png'), fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('.booking-form [role=alert]')).toBeInViewport()
  await expect(page.getByRole('button', { name: '使用原请求安全重试', exact: true })).toBeInViewport()
  await page.screenshot({ path: resolve(pictures, 'appointment-result-unknown-mobile.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '使用原请求安全重试', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  expect(keys).toHaveLength(2); expect(keys[0]).toBeTruthy(); expect(keys[1]).toBe(keys[0])
  const list = await request.get(`${root}/pets/${f.petId}/appointments`, { headers: headers() })
  expect((await list.json() as Appointment[]).length).toBe(1)
})

test('真实叫号成功后响应丢失：阻止再叫下一位，刷新核实后由员工继续', async ({ page, request }) => {
  const f = await fixture(request)
  for (const slot of f.slots.slice(0, 2)) { const a = await booking(request, f, slot); expect((await request.post(`${root}/appointments/${a.id}/check-in`, { headers: headers() })).status()).toBe(200) }
  await loginEmployee(page, adminCredentials(), `/appointments?petId=${f.petId}`)
  await page.getByLabel('候诊医生', { exact: true }).selectOption(f.doctorId)
  await expect(page.locator('.reception-queue__list li')).toHaveCount(2)
  let calls = 0
  await page.route(`**${root}/queue/call?**`, async route => { calls++; const actual = await route.fetch(); expect(actual.status()).toBe(200); await route.abort('connectionreset') })
  await page.getByRole('button', { name: '叫下一位', exact: true }).click()
  await expect(page.locator('.reception-feedback')).toContainText('操作结果未确认')
  await expect(page.getByRole('button', { name: '叫下一位', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '查看已叫号记录', exact: true }).click()
  await expect(page.locator('.reception-queue__list li')).toHaveCount(1)
  await expect(page.locator('.reception-table')).toContainText('已叫号')
  await expect(page.getByLabel('预约状态', { exact: true })).toHaveValue('CALLED')
  expect(calls).toBe(1)
  await page.getByRole('button', { name: '已核对，继续接待', exact: true }).click()
  await expect(page.getByRole('button', { name: '叫下一位', exact: true })).toBeEnabled()
})

test('第三方代理实测：上游连接拒绝产生502，预约结果未知且不自动重试', async ({ page, request }) => {
  const f = await fixture(request)
  let refused = 0
  const server = createServer((incoming, outgoing) => {
    const broken = incoming.url === `${root}/appointments` && incoming.method === 'POST'
    const upstream = httpRequest({ host: '127.0.0.1', port: broken ? 1 : 5173, path: incoming.url, method: incoming.method, headers: { ...incoming.headers, host: '127.0.0.1:5173' } }, response => { outgoing.writeHead(response.statusCode || 502, response.headers); response.pipe(outgoing) })
    upstream.on('error', (e: NodeJS.ErrnoException) => { if (broken && e.code === 'ECONNREFUSED') refused++; outgoing.writeHead(502, { 'Content-Type': 'text/html' }); outgoing.end('Upstream unavailable') })
    incoming.pipe(upstream)
  })
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  const address = server.address(); if (!address || typeof address === 'string') throw new Error('Proxy startup failed')
  try {
    await page.goto(`http://127.0.0.1:${address.port}/login?redirect=${encodeURIComponent(`/appointments?petId=${f.petId}`)}`)
    await page.getByLabel('员工账号', { exact: true }).fill(adminCredentials().username)
    await page.getByLabel('登录密码', { exact: true }).fill(adminCredentials().password)
    await page.getByRole('button', { name: '登录工作台' }).click()
    await page.getByRole('button', { name: '新建预约', exact: true }).click()
    await expect(page.locator('.booking-selected')).toContainText(f.petName)
    await page.getByLabel('接诊医生', { exact: true }).last().selectOption(f.doctorId)
    await page.getByRole('button', { name: slotLabel(f.slots[0]!), exact: true }).click()
    await page.getByRole('button', { name: '确认预约', exact: true }).click()
    await expect(page.locator('.booking-form [role=alert]')).toContainText('预约结果未确认')
    expect(refused).toBe(1)
  } finally { server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve())) }
})

test('正常分页与状态筛选实测：第二页和第一页面向同一条件', async ({ page, request }) => {
  const f = await fixture(request)
  for (const slot of f.slots.slice(0, 11)) await booking(request, f, slot)
  await loginEmployee(page, adminCredentials(), `/appointments?petId=${f.petId}`)
  await expect(page.locator('.reception-table tbody tr')).toHaveCount(10)
  await expect(page.locator('.reception-table-note')).toContainText('共 11 条预约')
  await page.getByRole('button', { name: '下一页', exact: true }).click()
  await expect(page.locator('.reception-table tbody tr')).toHaveCount(1)
  await expect(page.locator('.archive-pagination')).toContainText('第 2 / 2 页')
  await page.getByLabel('预约状态', { exact: true }).selectOption('CANCELLED')
  await expect(page.getByRole('heading', { name: '当前条件下暂无预约', exact: true })).toBeVisible()
  await page.getByLabel('预约状态', { exact: true }).selectOption('SCHEDULED')
  await expect(page.locator('.reception-table tbody tr')).toHaveCount(10)
  await expect(page.locator('.archive-pagination')).toContainText('第 1 / 2 页')
})

test('号源服务故障注入：不把读取失败显示为无号且禁止预约', async ({ page, request }) => {
  const f = await fixture(request)
  await page.route(`**${root}/availability?**`, route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"号源依赖故障"}' }))
  await loginEmployee(page, adminCredentials(), `/appointments?petId=${f.petId}`)
  await page.getByRole('button', { name: '新建预约', exact: true }).click()
  await page.getByLabel('接诊医生', { exact: true }).last().selectOption(f.doctorId)
  await expect(page.getByRole('heading', { name: '号源读取失败', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认预约', exact: true })).toBeDisabled()
  await page.unroute(`**${root}/availability?**`)
  await page.getByRole('button', { name: '重新读取', exact: true }).click()
  await expect(page.getByRole('button', { name: slotLabel(f.slots[0]!), exact: true })).toBeVisible()
})

test('改期服务故障注入：原输入保留且禁止重放', async ({ page, request }) => {
  const f = await fixture(request), a = await booking(request, f)
  await loginEmployee(page, adminCredentials(), `/appointments?petId=${f.petId}`)
  await row(page, a.id).getByRole('button', { name: '改期', exact: true }).click()
  await page.getByRole('button', { name: slotLabel(f.slots[1]!), exact: true }).click()
  let posts = 0
  await page.route(`**${root}/appointments/${a.id}/reschedule`, route => { posts++; return route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"改期依赖故障"}' }) })
  await page.getByRole('button', { name: '确认改期', exact: true }).click()
  await expect(page.locator('.booking-form [role=alert]')).toContainText('改期结果未确认')
  await expect(page.getByRole('button', { name: '确认改期', exact: true })).toBeDisabled()
  expect(posts).toBe(1)
  const unchanged = await request.get(`${root}/appointments/${a.id}`, { headers: headers() })
  expect((await unchanged.json() as Appointment).startAt).toBe(a.startAt)
})

for (const operation of ['check-in', 'cancel'] as const) {
  test(`真实${operation}成功后断连：不自动重放并可查询核实`, async ({ page, request }) => {
    const f = await fixture(request), a = await booking(request, f)
    await loginEmployee(page, adminCredentials(), `/appointments?petId=${f.petId}`)
    let posts = 0
    await page.route(`**${root}/appointments/${a.id}/${operation}`, async route => { posts++; const actual = await route.fetch(); expect(actual.status()).toBe(200); await route.abort('connectionreset') })
    if (operation === 'cancel') {
      await row(page, a.id).getByRole('button', { name: '取消预约', exact: true }).click()
      await page.getByRole('button', { name: '确认取消预约', exact: true }).click()
      await expect(page.getByRole('button', { name: '确认取消预约', exact: true })).toBeDisabled()
      await page.getByRole('dialog').getByRole('button', { name: '返回', exact: true }).click()
    } else await row(page, a.id).getByRole('button', { name: '签到', exact: true }).click()
    await expect(page.locator('.reception-feedback')).toContainText('操作结果未确认')
    await page.getByRole('button', { name: '刷新并核实状态', exact: true }).click()
    await expect(row(page, a.id)).toContainText(operation === 'cancel' ? '已取消' : '候诊中')
    expect(posts).toBe(1)
  })
}
