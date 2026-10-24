import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { adminCredentials, loginEmployee, newEmployee } from './support/real-backend'
import { clinicDate, bookingDateRange, slotLabel } from '../src/features/appointments/model'
import type { BillDetail } from '../src/features/billing/model'
test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要真实后端与 MySQL，不使用伪造成功数据')
const root = '/api/billing'
const pictures = resolve('.artifacts/screenshots')
mkdirSync(pictures, { recursive: true })
let token = ''
const headers = () => ({ Authorization: `Bearer ${token}` })
test.beforeAll(async ({ request }) => {
  const response = await request.post('/api/auth/login', { data: adminCredentials() }); expect(response.status()).toBe(200)
  token = (await response.json() as { accessToken: string }).accessToken
})
test.beforeEach(({ page }) => { page.setDefaultTimeout(15_000) })
async function fixture(request: APIRequestContext) {
  const suffix = randomUUID().slice(0, 8), account = newEmployee(['DOCTOR'])
  const user = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(user.status()).toBe(201)
  const doctorId = (await user.json() as { id: string }).id
  const owner = await request.post('/api/owner/clients', { headers: headers(), data: { name: `收费联调客户${suffix}`, phone: '13800138000', address: '收费测试' } }); expect(owner.status()).toBe(201)
  const ownerId = (await owner.json() as { id: string }).id
  const petName = `收费小满${suffix}`
  const pet = await request.post(`/api/owner/clients/${ownerId}/pets`, { headers: headers(), data: { details: { name: petName, species: '猫', birthDate: '2024-06-01', weightKg: 4 }, birthDateEstimated: false, version: 0 } }); expect(pet.status()).toBe(201)
  const petId = (await pet.json() as { id: string }).id
  const date = clinicDate()
  const slot = { startAt: new Date(Date.now() + 300_000).toISOString(), endAt: new Date(Date.now() + 2100_000).toISOString() }
  const schedule = await request.post('/api/appointment/schedules', { headers: headers(), data: { doctorId, ...slot, slotMinutes: 30 } }); expect(schedule.status()).toBe(201)
  const appointment = await request.post('/api/appointment/appointments', { headers: headers(), data: { petId, doctorId, ...slot } }); expect(appointment.status()).toBe(201)
  const appointmentId = (await appointment.json() as { id: string }).id
  expect(clinicDate(new Date(slot.startAt))).toBe(date)
  // Formal medical writes now require the real check-in and call lifecycle.
  expect((await request.post(`/api/appointment/appointments/${appointmentId}/check-in`, { headers: headers() })).status()).toBe(200)
  expect((await request.post(`/api/appointment/queue/call?doctorId=${doctorId}`, { headers: headers() })).status()).toBe(200)
  const login = await request.post('/api/auth/login', { data: account }); expect(login.status()).toBe(200)
  const doctorToken = (await login.json() as { accessToken: string }).accessToken
  const record = await request.post('/api/medical/records', { headers: { Authorization: `Bearer ${doctorToken}` }, data: { appointmentId, symptoms: '收费联调症状', diagnosis: '不应出现在收费索引中的诊断', advice: '收费联调医嘱' } }); expect(record.status()).toBe(201)
  const recordId = (await record.json() as { id: string }).id
  const itemName = `收费联调护理${suffix}`
  const charge = await request.post(`${root}/charge-items`, { headers: headers(), data: { name: itemName, amount: 12.5 } }); expect(charge.status()).toBe(201)
  const itemId = (await charge.json() as { id: string }).id
  return { petId, petName, recordId, itemId, itemName, doctorId, date, slot }
}
type Fixture = Awaited<ReturnType<typeof fixture>>
const body = (f: Fixture) => ({ medicalRecordId: f.recordId, prescriptionId: null, items: [{ chargeItemId: f.itemId, quantity: 2 }] })
async function create(request: APIRequestContext, f: Fixture) {
  const response = await request.post(`${root}/bills`, { headers: { ...headers(), 'Idempotency-Key': randomUUID() }, data: body(f) }); expect(response.status()).toBe(201)
  return response.json() as Promise<BillDetail>
}
async function openCreate(page: Page, f: Fixture) {
  await loginEmployee(page, adminCredentials(), `/billing?petId=${f.petId}`)
  await page.getByRole('button', { name: '手工开单', exact: true }).click()
  await page.getByLabel('检索客户或宠物', { exact: true }).fill(f.petName)
  await page.getByRole('button', { name: '查找档案', exact: true }).click()
  await page.getByRole('button', { name: new RegExp(f.petName) }).click()
  await page.getByLabel('就诊记录', { exact: true }).selectOption(f.recordId)
  await page.getByLabel('查找收费项目', { exact: true }).fill(f.itemName)
  await page.getByRole('button', { name: '查找项目', exact: true }).click()
  await page.getByRole('button', { name: new RegExp(f.itemName) }).click()
  await page.getByLabel(`${f.itemName}数量`, { exact: true }).fill('2')
}
test('正常实机：手工开单、模拟收款、全额退款、流水与移动界面', async ({ page, request }) => {
  const f = await fixture(request), errors: string[] = []
  page.on('pageerror', e => errors.push(e.message))
  await openCreate(page, f)
  await expect(page.locator('.billing-estimate')).toBeInViewport()
  await page.screenshot({ path: resolve(pictures, 'billing-create.png'), fullPage: true, animations: 'disabled' })
  const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === `${root}/bills`)
  await page.getByRole('button', { name: '确认开单', exact: true }).dblclick()
  const created = await response; expect(created.status()).toBe(201)
  const bill = await created.json() as BillDetail
  await expect(page.locator('.billing-detail-head')).toContainText('¥25.00')
  await page.getByRole('button', { name: '模拟收款', exact: true }).click()
  await page.getByRole('button', { name: '确认模拟收款', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('已收款')
  await page.getByRole('button', { name: '全额退款', exact: true }).click()
  await page.getByLabel('退款原因', { exact: true }).fill('服务取消，全额退费')
  await page.getByRole('button', { name: '确认全额退款', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('已退款')
  await expect(page.locator('.billing-timeline')).toContainText('服务取消，全额退费')
  expect((await request.post(`${root}/bills`, { headers: headers(), data: body(f) })).status()).toBe(409)
  await page.screenshot({ path: resolve(pictures, 'billing-detail.png'), fullPage: true, animations: 'disabled' })
  await page.locator('.el-drawer__close-btn').click()
  await expect(page.locator('.el-drawer')).toHaveCount(0)
  await expect(page.locator(`[data-bill-id="${bill.id}"]`)).toContainText('已退款')
  await page.screenshot({ path: resolve(pictures, 'billing-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.locator('.billing-welcome')).toBeInViewport()
  await page.screenshot({ path: resolve(pictures, 'billing-mobile.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
test('正常实机：作废保留历史后重开；状态筛选', async ({ request, page }) => {
  const f = await fixture(request), bill = await create(request, f)
  await loginEmployee(page, adminCredentials(), `/billing?petId=${f.petId}&billId=${bill.id}`)
  await page.getByRole('button', { name: '作废账单', exact: true }).click()
  await page.getByLabel('作废原因', { exact: true }).fill('明细有误，作废重开')
  await page.getByRole('button', { name: '确认作废', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('已作废')
  const next = await create(request, f); expect(next.id).not.toBe(bill.id)
  await page.locator('.el-drawer__close-btn').click()
  await page.getByLabel('账单状态', { exact: true }).selectOption('VOID')
  await expect(page.locator('[data-bill-id]')).toHaveCount(1)
  await expect(page.locator('[data-bill-id]')).toContainText('已作废')
})
test('正常实机：开单采用目录最新价格，前台账号可收款且无目录维护入口', async ({ request, page }) => {
  const f = await fixture(request), account = newEmployee(['STAFF'])
  await openCreate(page, f)
  expect((await request.put(`${root}/charge-items/${f.itemId}`, { headers: headers(), data: { name: f.itemName, amount: 20, active: true } })).status()).toBe(200)
  await page.getByRole('button', { name: '确认开单', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('¥40.00')
  const id = new URL(page.url()).searchParams.get('billId')!
  expect((await request.post('/api/identity/users', { headers: headers(), data: account })).status()).toBe(201)
  await page.locator('.el-drawer__close-btn').click()
  await expect(page.locator('.el-drawer')).toHaveCount(0)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await loginEmployee(page, account, `/billing?billId=${id}`)
  await expect(page.getByRole('button', { name: '收费项目管理', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '模拟收款', exact: true }).click()
  await page.getByRole('button', { name: '确认模拟收款', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('已收款')
})
test('异常实机：开单前项目停用，明确拒绝后保留输入', async ({ request, page }) => {
  const f = await fixture(request)
  await openCreate(page, f)
  expect((await request.put(`${root}/charge-items/${f.itemId}`, { headers: headers(), data: { name: f.itemName, amount: 12.5, active: false } })).status()).toBe(200)
  await page.getByRole('button', { name: '确认开单', exact: true }).click()
  await expect(page.locator('.billing-create [role=alert]')).toContainText('收费项目已停用')
  await expect(page.getByLabel(`${f.itemName}数量`, { exact: true })).toHaveValue('2')
  await expect(page.getByLabel('就诊记录', { exact: true })).toHaveValue(f.recordId)
  const list = await request.get(`${root}/workbench/bills?petId=${f.petId}`, { headers: headers() })
  expect((await list.json() as { totalElements: number }).totalElements).toBe(0)
})
test('正常实机：管理员收费项目新增、改价与停用', async ({ page }) => {
  const name = `目录联调${randomUUID().slice(0, 8)}`
  await loginEmployee(page, adminCredentials(), '/billing')
  await page.getByRole('button', { name: '收费项目管理', exact: true }).click()
  await page.getByRole('button', { name: '新增收费项目', exact: true }).click()
  await page.getByLabel('收费项目名称', { exact: true }).fill(name)
  await page.getByLabel('单价（元）', { exact: true }).fill('18.90')
  await page.getByRole('button', { name: '保存收费项目', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByLabel('项目名称', { exact: true }).fill(name)
  await page.getByRole('button', { name: '查询项目', exact: true }).click()
  await page.getByRole('button', { name: '编辑项目', exact: true }).click()
  await page.getByLabel('单价（元）', { exact: true }).fill('20.00')
  await page.getByLabel('启用此收费项目', { exact: true }).uncheck()
  await page.getByRole('button', { name: '保存收费项目', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.billing-table tbody')).toContainText('¥20.00')
  await expect(page.locator('.billing-table tbody')).toContainText('已停用')
})
test('异常实测：重复开单并发、非法数量、越权与诊疗信息裁剪', async ({ request, page }) => {
  const f = await fixture(request)
  const responses = await Promise.all(Array.from({ length: 4 }, () => request.post(`${root}/bills`, { headers: { ...headers(), 'Idempotency-Key': randomUUID() }, data: body(f) })))
  expect(responses.filter(r => r.status() === 201)).toHaveLength(1)
  expect(responses.filter(r => r.status() === 409)).toHaveLength(3)
  const bill = await responses.find(r => r.status() === 201)!.json() as BillDetail
  expect((await request.post(`${root}/bills`, { headers: headers(), data: { ...body(f), items: [{ chargeItemId: f.itemId, quantity: 0 }] } })).status()).toBe(400)
  const index = await request.get(`${root}/workbench/records?petId=${f.petId}`, { headers: headers() })
  expect(index.status()).toBe(200); expect(await index.text()).not.toContain('不应出现在收费索引中的诊断')
  for (const role of ['DOCTOR', 'OWNER', 'PHARMACY']) {
    const account = newEmployee([role]); expect((await request.post('/api/identity/users', { headers: headers(), data: account })).status()).toBe(201)
    const login = await request.post('/api/auth/login', { data: account }); const access = (await login.json() as { accessToken: string }).accessToken
    const h = { Authorization: `Bearer ${access}` }
    expect((await request.post(`${root}/bills/${bill.id}/pay`, { headers: h })).status()).toBe(403)
    expect((await request.get(`${root}/workbench/bills`, { headers: h })).status()).toBe(403)
    if (role !== 'OWNER') { await loginEmployee(page, account, '/billing'); await expect(page.getByRole('heading', { name: '你没有访问此页面的权限' })).toBeVisible() }
  }
})
for (const fault of ['http503', 'html502', 'malformed201', 'connection-reset', 'timeout', 'offline'] as const) {
  test(`开单故障注入：${fault}，冻结输入且不自动重放`, async ({ request, page, context }) => {
    const f = await fixture(request)
    await openCreate(page, f)
    let calls = 0
    if (fault === 'offline') await context.setOffline(true)
    else await page.route(`**${root}/bills`, route => {
      if (route.request().method() !== 'POST') return route.continue()
      calls++
      if (fault === 'connection-reset') return route.abort('connectionreset')
      if (fault === 'timeout') return
      return route.fulfill({ status: fault === 'malformed201' ? 201 : fault === 'html502' ? 502 : 503, contentType: fault === 'html502' ? 'text/html' : 'application/json', body: fault === 'malformed201' ? '{}' : fault === 'html502' ? '<h1>Bad Gateway</h1>' : '{"code":"INTERNAL_ERROR","message":"依赖故障"}' })
    })
    await page.getByRole('button', { name: '确认开单', exact: true }).click()
    await expect(page.locator('.billing-create [role=alert]')).toContainText('开单结果未确认', { timeout: 20_000 })
    await expect(page.getByLabel('就诊记录', { exact: true })).toBeDisabled()
    await expect(page.getByRole('button', { name: '使用原请求安全重试', exact: true })).toBeEnabled()
    if (fault !== 'offline') expect(calls).toBe(1)
    await context.setOffline(false)
  })
}
test('真实落库后响应丢失：同键显式重试只生成一张账单', async ({ request, page }) => {
  const f = await fixture(request)
  await openCreate(page, f)
  const keys: string[] = []
  await page.route(`**${root}/bills`, async route => {
    if (route.request().method() !== 'POST') return route.continue()
    keys.push(route.request().headers()['idempotency-key']!)
    const actual = await route.fetch(); expect(actual.status()).toBe(201)
    if (keys.length === 1) await route.abort('connectionreset')
    else await route.fulfill({ response: actual })
  })
  await page.getByRole('button', { name: '确认开单', exact: true }).click()
  await expect(page.locator('.billing-create [role=alert]')).toContainText('开单结果未确认')
  await page.getByRole('button', { name: '使用原请求安全重试', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('待收款')
  expect(keys).toHaveLength(2); expect(keys[0]).toBeTruthy(); expect(keys[1]).toBe(keys[0])
  const list = await request.get(`${root}/workbench/bills?petId=${f.petId}`, { headers: headers() }); expect((await list.json() as { totalElements: number }).totalElements).toBe(1)
})
test('真实收款响应丢失：原键重试同一笔支付，退款响应丢失先核实', async ({ request, page }) => {
  const f = await fixture(request), bill = await create(request, f), keys: string[] = []
  await loginEmployee(page, adminCredentials(), `/billing?billId=${bill.id}`)
  await page.route(`**${root}/bills/${bill.id}/pay`, async route => { keys.push(route.request().headers()['idempotency-key']!); const actual = await route.fetch(); expect(actual.status()).toBe(201); if (keys.length === 1) await route.abort('connectionreset'); else await route.fulfill({ response: actual }) })
  await page.getByRole('button', { name: '模拟收款', exact: true }).click(); await page.getByRole('button', { name: '确认模拟收款', exact: true }).click()
  await expect(page.locator('.billing-feedback').last()).toContainText('操作结果未确认')
  await page.getByRole('button', { name: '使用原请求安全重试', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('已收款')
  expect(keys).toHaveLength(2); expect(keys[1]).toBe(keys[0])
  let refunds = 0
  await page.route(`**${root}/bills/${bill.id}/refund`, async route => { refunds++; const actual = await route.fetch(); expect(actual.status()).toBe(200); await route.abort('connectionreset') })
  await page.getByRole('button', { name: '全额退款', exact: true }).click(); await page.getByLabel('退款原因', { exact: true }).fill('退款响应丢失验证'); await page.getByRole('button', { name: '确认全额退款', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认全额退款', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '刷新核实账单与流水', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('已退款')
  await page.getByRole('button', { name: '我已核实，继续处理', exact: true }).click()
  expect(refunds).toBe(1)
})
test('读取依赖故障：账单错误不显示为空数据，恢复后可重读', async ({ page }) => {
  await page.route(`**${root}/workbench/bills?**`, route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"联系资料依赖不可用"}' }))
  await loginEmployee(page, adminCredentials(), '/billing')
  await expect(page.getByRole('heading', { name: '账单列表读取失败' })).toBeVisible()
  await page.unroute(`**${root}/workbench/bills?**`)
  await page.getByRole('button', { name: '重试账单列表', exact: true }).click()
  await expect(page.getByRole('heading', { name: '账单列表读取失败' })).toHaveCount(0)
})
test('患者读取故障：账单仍可查看，核对资料前暂停新收款', async ({ request, page }) => {
  const f = await fixture(request), bill = await create(request, f)
  const path = `**/api/owner/pets/${f.petId}`
  await page.route(path, route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"档案依赖不可用"}' }))
  await loginEmployee(page, adminCredentials(), `/billing?billId=${bill.id}`)
  await expect(page.locator('.billing-detail')).toContainText('患者联系资料读取失败')
  await expect(page.getByRole('button', { name: '模拟收款', exact: true })).toBeDisabled()
  await expect(page.locator('.billing-detail-head')).toContainText('¥25.00')
  await page.unroute(path)
  await page.getByRole('button', { name: '重新读取患者资料', exact: true }).click()
  await expect(page.getByRole('button', { name: '模拟收款', exact: true })).toBeEnabled()
})
test('真实作废响应丢失：不重放，刷新核实后原病历可重开', async ({ request, page }) => {
  const f = await fixture(request), bill = await create(request, f)
  await loginEmployee(page, adminCredentials(), `/billing?billId=${bill.id}`)
  let calls = 0
  await page.route(`**${root}/bills/${bill.id}/void`, async route => { calls++; const actual = await route.fetch(); expect(actual.status()).toBe(200); await route.abort('connectionreset') })
  await page.getByRole('button', { name: '作废账单', exact: true }).click()
  await page.getByLabel('作废原因', { exact: true }).fill('响应丢失测试')
  await page.getByRole('button', { name: '确认作废', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认作废', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '刷新核实账单与流水', exact: true }).click()
  await expect(page.locator('.billing-detail-head')).toContainText('已作废')
  await page.getByRole('button', { name: '我已核实，继续处理', exact: true }).click()
  expect(calls).toBe(1)
  expect((await create(request, f)).id).not.toBe(bill.id)
})
test('预约正常实机：次日可预约，日期上限为下月同日，越界禁止提交', async ({ page, request }) => {
  const f = await fixture(request)
  // The medical fixture is already called. Add an independent next-day schedule for booking.
  f.date = clinicDate(new Date(Date.now() + 86400000))
  f.slot = { startAt: new Date(`${f.date}T09:00:00+08:00`).toISOString(), endAt: new Date(`${f.date}T09:30:00+08:00`).toISOString() }
  expect((await request.post('/api/appointment/schedules', { headers: headers(), data: { doctorId: f.doctorId, ...f.slot, slotMinutes: 30 } })).status()).toBe(201)
  await loginEmployee(page, adminCredentials(), `/appointments?petId=${f.petId}`)
  await page.getByRole('button', { name: '新建预约', exact: true }).click()
  const input = page.getByLabel('就诊日期（上海时间）', { exact: true })
  await expect(input).toHaveAttribute('max', bookingDateRange().max)
  await input.fill(f.date)
  await page.getByLabel('接诊医生', { exact: true }).last().selectOption(f.doctorId)
  await page.getByRole('button', { name: slotLabel(f.slot), exact: true }).click()
  await expect(page.getByRole('button', { name: '确认预约', exact: true })).toBeEnabled()
  await input.fill('2099-01-01')
  await expect(page.getByRole('button', { name: '确认预约', exact: true })).toBeDisabled()
  await input.fill(f.date)
  await page.getByRole('button', { name: slotLabel(f.slot), exact: true }).click()
  const response = page.waitForResponse(r => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/appointment/appointments')
  await page.getByRole('button', { name: '确认预约', exact: true }).click()
  expect((await response).status()).toBe(201)
})
