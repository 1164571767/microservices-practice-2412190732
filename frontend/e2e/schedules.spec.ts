import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { createServer, request as httpRequest } from 'node:http'
import { adminCredentials, loginEmployee, newEmployee } from './support/real-backend'
import { clinicDate, type Schedule } from '../src/features/appointments/model'

test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要真实后端/MySQL，不以模拟成功数据代替')
test.use({ actionTimeout: 10_000 })
const root = '/api/appointment', pictures = resolve('.artifacts/screenshots')
mkdirSync(pictures, { recursive: true })
let token = ''
const headers = () => ({ Authorization: `Bearer ${token}` })
test.beforeAll(async ({ request }) => {
  const r = await request.post('/api/auth/login', { data: adminCredentials() }); expect(r.status()).toBe(200)
  token = (await r.json() as { accessToken: string }).accessToken
})
async function fixture(request: APIRequestContext) {
  const account = newEmployee(['DOCTOR']); account.displayName = `排班联调${randomUUID().slice(0, 6)}`
  const r = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(r.status()).toBe(201)
  const doctorId = (await r.json() as { id: string }).id
  const date = clinicDate(new Date(Date.now() + 86400000))
  const body = { doctorId, startAt: new Date(`${date}T09:00:00+08:00`).toISOString(), endAt: new Date(`${date}T12:00:00+08:00`).toISOString(), slotMinutes: 30 }
  return { account, doctorId, date, body }
}
type Fixture = Awaited<ReturnType<typeof fixture>>
async function open(page: Page, f: Fixture, account = adminCredentials()) {
  await loginEmployee(page, account, '/appointments')
  await page.getByRole('button', { name: '医生排班', exact: true }).click()
  await page.getByLabel('接诊医生', { exact: true }).selectOption(f.doctorId)
  await page.getByLabel('就诊日期', { exact: true }).fill(f.date)
}
async function createDialog(page: Page, f: Fixture) {
  await page.getByRole('button', { name: '新增排班', exact: true }).click()
  await page.getByLabel('排班医生', { exact: true }).selectOption(f.doctorId)
  await page.getByLabel('排班日期', { exact: true }).fill(f.date)
}
async function create(request: APIRequestContext, f: Fixture) {
  const r = await request.post(`${root}/schedules`, { headers: headers(), data: f.body }); expect(r.status()).toBe(201)
  return r.json() as Promise<Schedule>
}
const row = (page: Page, id: string) => page.locator(`[data-schedule-id="${id}"]`)
async function action(page: Page, id: string, label: string, reason: string) {
  await row(page, id).getByRole('button', { name: label, exact: true }).click()
  await page.getByLabel('操作原因', { exact: true }).fill(reason)
  await page.getByRole('button', { name: `确认${label}`, exact: true }).click()
}
test('正常实机：新增放号、停止、恢复、停诊、删除及桌面手机布局', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f); await createDialog(page, f)
  const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === `${root}/schedules`)
  await page.getByRole('button', { name: '确认新增排班', exact: true }).dblclick()
  const r = await response; expect(r.status()).toBe(201); const s = await r.json() as Schedule
  await expect(row(page, s.id)).toContainText('放号中')
  const slots = await request.get(`${root}/availability?doctorId=${f.doctorId}&date=${f.date}`, { headers: headers() }); expect(await slots.json()).toHaveLength(6)
  await action(page, s.id, '停止放号', '保留原预约，暂停新增'); await expect(row(page, s.id)).toContainText('已停止放号')
  expect(await (await request.get(`${root}/availability?doctorId=${f.doctorId}&date=${f.date}`, { headers: headers() })).json()).toEqual([])
  await action(page, s.id, '恢复放号', '恢复出诊'); await expect(row(page, s.id)).toContainText('放号中')
  await action(page, s.id, '停诊', '尚无预约，取消出诊'); await expect(row(page, s.id)).toContainText('已停诊')
  await row(page, s.id).getByRole('button', { name: '操作留痕', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '排班操作留痕' })).toContainText('恢复出诊')
  await page.getByRole('dialog', { name: '排班操作留痕' }).getByRole('button', { name: 'Close this dialog' }).click()
  await expect(page.getByRole('dialog', { name: '排班操作留痕' })).not.toBeVisible()
  await page.evaluate(() => { window.scrollTo(0, 0); (document.activeElement as HTMLElement)?.blur() })
  await page.screenshot({ path: resolve(pictures, 'schedules-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 }); await page.screenshot({ path: resolve(pictures, 'schedules-mobile.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await action(page, s.id, '删除排班', '空排班录入错误'); await expect(row(page, s.id)).toHaveCount(0)
  const ops = await request.get(`${root}/schedules/${s.id}/operations`, { headers: headers() }); expect(ops.status()).toBe(200)
  expect((await ops.json() as { action: string }[]).some(o => o.action === 'DELETE')).toBe(true)
})

test('异常实机：重叠、未处理预约、历史删除及只读权限', async ({ page, request }) => {
  const f = await fixture(request), s = await create(request, f)
  expect((await request.post(`${root}/schedules`, { headers: headers(), data: { ...f.body, startAt: new Date(`${f.date}T09:15:00+08:00`).toISOString() } })).status()).toBe(409)
  const client = await request.post('/api/owner/clients', { headers: headers(), data: { name: `排班客户${randomUUID().slice(0,6)}`, phone: '13800138000', address: '排班联调' } }); expect(client.status()).toBe(201)
  const pet = await request.post(`/api/owner/clients/${(await client.json() as { id: string }).id}/pets`, { headers: headers(), data: { details: { name: '排班测试猫', species: '猫', birthDate: '2024-01-01', weightKg: 4 }, birthDateEstimated: false, version: 0 } }); expect(pet.status()).toBe(201)
  const booking = await request.post(`${root}/appointments`, { headers: headers(), data: { doctorId: f.doctorId, petId: (await pet.json() as { id: string }).id, startAt: f.body.startAt, endAt: new Date(`${f.date}T09:30:00+08:00`).toISOString() } }); expect(booking.status()).toBe(201)
  const appointmentId = (await booking.json() as { id: string }).id
  await open(page, f); await action(page, s.id, '停诊', '医生请假')
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('仍有待到院')
  await page.getByRole('button', { name: '返回', exact: true }).click()
  expect((await request.post(`${root}/appointments/${appointmentId}/cancel`, { headers: headers() })).status()).toBe(200)
  await action(page, s.id, '删除排班', '取消后尝试删除')
  await expect(page.getByRole('dialog').getByRole('alert')).toContainText('有历史')
  await page.getByRole('button', { name: '返回', exact: true }).click()
  await action(page, s.id, '停诊', '预约已人工取消'); await expect(row(page, s.id)).toContainText('已停诊')
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await open(page, f, f.account)
  await expect(page.getByRole('button', { name: '新增排班', exact: true })).toHaveCount(0)
  await expect(row(page, s.id)).toContainText('已停诊')
})

test('依赖故障注入：排班读取503显示错误，可恢复且不能操作旧列表', async ({ page, request }) => {
  const f = await fixture(request); await create(request, f)
  await page.route('**/api/appointment/schedules?*', r => r.fulfill({ status: 503, json: { code: 'INTERNAL_ERROR', message: '排班依赖不可用' } }))
  await open(page, f); await expect(page.getByText('排班读取失败', { exact: true })).toBeVisible()
  await expect(page.locator('[data-schedule-id]')).toHaveCount(0)
  await page.unroute('**/api/appointment/schedules?*'); await page.getByRole('button', { name: '重新读取排班', exact: true }).click()
  await expect(page.locator('[data-schedule-id]')).toHaveCount(1)
})

for (const fault of ['503', 'HTML502', 'malformed201', 'abort', 'offline', 'timeout'] as const) {
  test(`写入故障注入：${fault}冻结新增输入，不自动重放`, async ({ page, request, context }) => {
    const f = await fixture(request); await open(page, f); await createDialog(page, f)
    let writes = 0
    page.on('request', r => { if (r.method() === 'POST' && new URL(r.url()).pathname === `${root}/schedules`) writes++ })
    if (fault === 'offline') await context.setOffline(true)
    else await page.route('**/api/appointment/schedules', async r => {
      if (fault === 'abort') await r.abort('connectionreset')
      else if (fault === 'timeout') await new Promise<void>(resolve => { const timer = setTimeout(resolve, 20_000); r.request().response().finally(() => { clearTimeout(timer); resolve() }).catch(() => {}) })
      else if (fault === 'HTML502') await r.fulfill({ status: 502, contentType: 'text/html', body: '<h1>Bad Gateway</h1>' })
      else await r.fulfill({ status: fault === '503' ? 503 : 201, json: fault === '503' ? { code: 'INTERNAL_ERROR', message: '故障注入' } : { invalid: true } })
    })
    await page.getByRole('button', { name: '确认新增排班', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('结果未确认', { timeout: 25_000 })
    await expect(page.getByLabel('排班日期', { exact: true })).toBeDisabled()
    expect(writes).toBe(1)
    if (fault === 'offline') await context.setOffline(false)
    await page.unroute('**/api/appointment/schedules')
    await page.getByRole('button', { name: '刷新排班与留痕核实', exact: true }).click()
    await expect(page.getByRole('button', { name: '已核实，继续操作', exact: true })).toBeEnabled()
    await page.getByRole('button', { name: '已核实，继续操作', exact: true }).click()
    expect(writes).toBe(1)
  })
}

test('真实提交后响应丢失：新增和停止放号均可查询核实，不重复写入', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f); await createDialog(page, f)
  let writes = 0
  await page.route('**/api/appointment/schedules', async r => { writes++; const response = await r.fetch(); expect(response.status()).toBe(201); await r.abort('connectionreset') })
  await page.getByRole('button', { name: '确认新增排班', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('结果未确认')
  await page.unroute('**/api/appointment/schedules')
  await page.getByRole('button', { name: '刷新排班与留痕核实', exact: true }).click()
  await expect(page.getByText(/放号中 · 排班编号/)).toBeVisible()
  await page.getByRole('button', { name: '已核实，继续操作', exact: true }).click()
  const list = await request.get(`${root}/schedules?doctorId=${f.doctorId}`, { headers: headers() }), rows = await list.json() as Schedule[]
  expect(rows).toHaveLength(1); const s = rows[0]!
  await page.route(`**/api/appointment/schedules/${s.id}/stop-booking`, async r => { writes++; const response = await r.fetch(); expect(response.status()).toBe(200); await r.abort('connectionreset') })
  await action(page, s.id, '停止放号', '响应丢失核实')
  await expect(page.getByRole('alert')).toContainText('结果未确认')
  await page.getByRole('button', { name: '刷新排班与留痕核实', exact: true }).click()
  await expect(page.getByText(/最近操作：停止放号（响应丢失核实）/)).toBeVisible()
  await page.getByRole('button', { name: '已核实，继续操作', exact: true }).click()
  await expect(row(page, s.id)).toContainText('已停止放号'); expect(writes).toBe(2)
})

test('第三方代理实测：上游连接拒绝产生502，不重放排班写入', async ({ page, request }) => {
  const f = await fixture(request), upstreamUrl = new URL(process.env.E2E_BASE_URL || 'http://127.0.0.1:5173')
  let refused = 0
  const server = createServer((incoming, outgoing) => {
    const broken = incoming.url === `${root}/schedules` && incoming.method === 'POST'
    const upstream = httpRequest({ host: '127.0.0.1', port: broken ? 1 : Number(upstreamUrl.port), path: incoming.url, method: incoming.method, headers: { ...incoming.headers, host: upstreamUrl.host } }, response => { outgoing.writeHead(response.statusCode || 502, response.headers); response.pipe(outgoing) })
    upstream.on('error', (error: NodeJS.ErrnoException) => { if (broken && error.code === 'ECONNREFUSED') refused++; outgoing.writeHead(502, { 'Content-Type': 'text/html' }); outgoing.end('Upstream unavailable') })
    incoming.pipe(upstream)
  })
  await new Promise<void>(resolve => server.listen(0, '127.0.0.1', resolve))
  const address = server.address(); if (!address || typeof address === 'string') throw new Error('Proxy startup failed')
  try {
    await page.goto(`http://127.0.0.1:${address.port}/login?redirect=/appointments`)
    await page.getByLabel('员工账号', { exact: true }).fill(adminCredentials().username)
    await page.getByLabel('登录密码', { exact: true }).fill(adminCredentials().password)
    await page.getByRole('button', { name: '登录工作台', exact: true }).click()
    await page.getByRole('button', { name: '医生排班', exact: true }).click()
    await createDialog(page, f)
    await page.getByRole('button', { name: '确认新增排班', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('结果未确认')
    expect(refused).toBe(1)
  } finally { server.closeAllConnections(); await new Promise<void>(resolve => server.close(() => resolve())) }
})
