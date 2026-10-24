import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { adminCredentials, loginEmployee, newEmployee } from './support/real-backend'
import { clinicToday, money, type RevenueReport, type DrugReport } from '../src/features/reporting/model'

test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要真实后端与 MySQL；故障注入单独标记')
const root = '/api/reporting'
const pictures = resolve('.artifacts/screenshots'); mkdirSync(pictures, { recursive: true })
let token = '', doctorId = '', billId = '', drugId = '', drugName = ''
const headers = () => ({ Authorization: `Bearer ${token}` })
const period = () => `from=${clinicToday()}&to=${clinicToday()}`
async function adminLogin(request: APIRequestContext) {
  const response = await request.post('/api/auth/login', { data: adminCredentials() })
  expect(response.status()).toBe(200); token = (await response.json() as { accessToken: string }).accessToken
}
test.beforeAll(async ({ request }) => {
  await adminLogin(request)
  const suffix = randomUUID().slice(0, 8), account = newEmployee(['DOCTOR'])
  account.displayName = `报表医生${suffix}`
  const user = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(user.status()).toBe(201)
  doctorId = (await user.json() as { id: string }).id
  const owner = await request.post('/api/owner/clients', { headers: headers(), data: { name: `报表联调${suffix}`, phone: '13800138000' } }); expect(owner.status()).toBe(201)
  const ownerId = (await owner.json() as { id: string }).id
  const pet = await request.post(`/api/owner/clients/${ownerId}/pets`, { headers: headers(), data: { details: { name: `报表小满${suffix}`, species: '猫', birthDate: '2024-06-01', weightKg: 4 }, birthDateEstimated: false, version: 0 } }); expect(pet.status()).toBe(201)
  const petId = (await pet.json() as { id: string }).id
  const date = clinicToday(), slot = { startAt: new Date(Date.now() + 300_000).toISOString(), endAt: new Date(Date.now() + 2100_000).toISOString() }
  expect((await request.post('/api/appointment/schedules', { headers: headers(), data: { doctorId, ...slot, slotMinutes: 30 } })).status()).toBe(201)
  const appointment = await request.post('/api/appointment/appointments', { headers: headers(), data: { petId, doctorId, ...slot } }); expect(appointment.status()).toBe(201)
  const appointmentId = (await appointment.json() as { id: string }).id
  expect(clinicToday(new Date(slot.startAt))).toBe(date)
  // Formal medical writes now require the real check-in and call lifecycle.
  expect((await request.post(`/api/appointment/appointments/${appointmentId}/check-in`, { headers: headers() })).status()).toBe(200)
  expect((await request.post(`/api/appointment/queue/call?doctorId=${doctorId}`, { headers: headers() })).status()).toBe(200)
  const login = await request.post('/api/auth/login', { data: account }); expect(login.status()).toBe(200)
  let doctorHeaders = { Authorization: `Bearer ${(await login.json() as { accessToken: string }).accessToken}` }
  const record = await request.post('/api/medical/records', { headers: doctorHeaders, data: { appointmentId, symptoms: '报表联调', diagnosis: '报表测试' } }); expect(record.status()).toBe(201)
  const recordId = (await record.json() as { id: string }).id
  const items = []
  for (let i = 0; i < 11; i++) {
    const name = `报表联调药${suffix}-${i}`
    const drug = await request.post('/api/pharmacy/drugs', { headers: headers(), data: { name, specification: '0.5g', unit: '盒', lowStockThreshold: 1, initialQuantity: 500 } }); expect(drug.status()).toBe(201)
    if (i === 10) { drugId = (await drug.json() as { id: string }).id; drugName = name }
    items.push({ drugName: name, specification: '0.5g', dosage: '1片', frequency: '每日一次', quantity: 100 + i, days: 1 })
  }
  // Refresh fixture credentials explicitly; the development backend may use short token lifetimes.
  await adminLogin(request)
  const doctorLogin = await request.post('/api/auth/login', { data: account }); expect(doctorLogin.status()).toBe(200)
  doctorHeaders = { Authorization: `Bearer ${(await doctorLogin.json() as { accessToken: string }).accessToken}` }
  const prescription = await request.post(`/api/medical/records/${recordId}/prescriptions`, { headers: doctorHeaders, data: { items } }); expect(prescription.status()).toBe(201)
  const prescriptionId = (await prescription.json() as { id: string }).id
  expect((await request.post(`/api/pharmacy/prescriptions/${prescriptionId}/dispense`, { headers: headers() })).status()).toBe(201)
  const charge = await request.post('/api/billing/charge-items', { headers: headers(), data: { name: `报表收费${suffix}`, amount: 100.25 } }); expect(charge.status()).toBe(201)
  const chargeId = (await charge.json() as { id: string }).id
  const bill = await request.post('/api/billing/bills', { headers: headers(), data: { medicalRecordId: recordId, items: [{ chargeItemId: chargeId, quantity: 1 }] } }); expect(bill.status()).toBe(201)
  billId = (await bill.json() as { id: string }).id
  expect((await request.post(`/api/billing/bills/${billId}/pay`, { headers: headers() })).status()).toBe(201)
  expect((await request.put(`/api/pharmacy/drugs/${drugId}`, { headers: headers(), data: { name: `${drugName}已更名`, specification: '0.5g', unit: '盒', lowStockThreshold: 1, active: true } })).status()).toBe(200)
})
test.beforeEach(async ({ request }) => { await adminLogin(request) })
async function openToday(page: Page) {
  await loginEmployee(page, adminCredentials(), '/reporting')
  await page.getByRole('button', { name: '今天', exact: true }).click()
  await expect(page.locator('.report-applied')).toContainText(clinicToday())
  await expect(page.getByTestId('total-visits')).toBeVisible()
}

test('正常实机：真实病历投影、医生筛选、按日明细、桌面与移动布局', async ({ page }) => {
  const errors: string[] = []; page.on('pageerror', e => errors.push(e.message))
  await openToday(page)
  await page.getByRole('combobox', { name: '医生范围' }).selectOption(doctorId)
  await expect(page.getByTestId('total-visits')).toHaveText('1次')
  await page.locator('.report-daily summary').click()
  await expect(page.locator('.report-daily tbody')).toContainText(clinicToday())
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: resolve(pictures, 'reporting-visits-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: resolve(pictures, 'reporting-mobile.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(errors).toEqual([])
})

test('正常实机：支付及全额退款投影与收入卡片一致', async ({ page, request }) => {
  await openToday(page)
  await page.getByRole('button', { name: '收入统计', exact: true }).click()
  const before = await (await request.get(`${root}/revenue?${period()}`, { headers: headers() })).json() as RevenueReport
  await expect(page.getByTestId('paid-amount')).toHaveText(money(before.paidAmount))
  await expect(page.getByTestId('net-amount')).toHaveText(money(before.netAmount))
  expect((await request.post(`/api/billing/bills/${billId}/refund`, { headers: headers(), data: { reason: '报表联调全额退款' } })).status()).toBe(200)
  await page.getByRole('button', { name: '刷新报表', exact: true }).click()
  await expect(page.getByTestId('net-amount')).toHaveText(money(before.netAmount - 100.25))
  await page.locator('.report-daily summary').click()
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: resolve(pictures, 'reporting-revenue-desktop.png'), fullPage: true, animations: 'disabled' })
})

test('正常实机：药品分页、区间汇总不随分页改变、历史名称筛选', async ({ page, request }) => {
  await openToday(page); await page.getByRole('button', { name: '药品消耗', exact: true }).click()
  const data = await (await request.get(`${root}/drug-consumption?${period()}&page=0&size=10`, { headers: headers() })).json() as DrugReport
  await expect(page.getByTestId('total-dispenses')).toHaveText(`${data.totalDispenses.toLocaleString('zh-CN')}单`)
  await page.locator('.report-pages .btn-next').click()
  await expect(page.locator('.el-pager .is-active')).toHaveText('2')
  await expect(page.getByTestId('total-dispenses')).toHaveText(`${data.totalDispenses.toLocaleString('zh-CN')}单`)
  await page.locator('.report-pages .btn-prev').click()
  await expect(page.locator('.el-pager .is-active')).toHaveText('1')
  const row = page.getByRole('row').filter({ hasText: drugName }).filter({ hasNotText: '已更名' })
  // Repeated test runs retain business history; find the fixture across all real pages.
  for (let i = 1; i < data.totalPages && await row.count() === 0; i++) {
    await page.locator('.report-pages .btn-next').click()
    await expect(page.locator('.el-pager .is-active')).toHaveText(String(i + 1))
  }
  await row.getByRole('button', { name: '仅看此药' }).click()
  await expect(page.locator('.report-table tbody tr')).toHaveCount(1)
  await expect(page.locator('.report-table tbody')).toContainText('110')
  await expect(page.locator('.report-table tbody')).not.toContainText('已更名')
  await page.evaluate(() => window.scrollTo(0, 0))
  await page.screenshot({ path: resolve(pictures, 'reporting-drugs-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '清除药品筛选' }).click()
  await expect(page.locator('.report-table tbody tr')).toHaveCount(10)
})

test('异常实机：非法区间本地阻止、真实空报表与服务端校验', async ({ page, request }) => {
  await openToday(page)
  let calls = 0; page.on('request', r => { if (r.url().includes(root)) calls++ })
  await page.getByLabel('开始日期', { exact: true }).fill('2026-10-20'); await page.getByLabel('结束日期', { exact: true }).fill('2026-10-01')
  await page.getByRole('button', { name: '查询报表' }).click(); await expect(page.getByRole('alert')).toContainText('开始日期不能晚于结束日期'); expect(calls).toBe(0)
  await page.getByLabel('开始日期', { exact: true }).fill('2024-01-01'); await page.getByLabel('结束日期', { exact: true }).fill('2025-01-01')
  await page.getByRole('button', { name: '查询报表' }).click(); await expect(page.getByRole('alert')).toContainText('最多 366 天'); expect(calls).toBe(0)
  await page.getByLabel('开始日期', { exact: true }).fill('1990-01-01'); await page.getByLabel('结束日期', { exact: true }).fill('1990-01-02'); await page.getByRole('button', { name: '查询报表' }).click()
  await expect(page.getByText('该区间暂无门诊统计记录', { exact: true })).toBeVisible()
  expect((await request.get(`${root}/revenue?from=2024-01-01&to=2025-01-01`, { headers: headers() })).status()).toBe(400)
})

for (const role of ['STAFF', 'DOCTOR', 'PHARMACY']) test(`异常实机：${role} 无报表菜单、路由与三类API均拒绝`, async ({ page, request }) => {
  const account = newEmployee([role])
  expect((await request.post('/api/identity/users', { headers: headers(), data: account })).status()).toBe(201)
  const pair = await loginEmployee(page, account, '/reporting')
  await expect(page.getByRole('heading', { name: '你没有访问此页面的权限' })).toBeVisible()
  await expect(page.getByRole('link', { name: '数据报表', exact: true })).toHaveCount(0)
  for (const path of ['visits', 'revenue', 'drug-consumption']) expect((await request.get(`${root}/${path}?${period()}`, { headers: { Authorization: `Bearer ${pair.accessToken}` } })).status()).toBe(403)
})

test('三方故障注入：503与网络中断不显示零数据，切换独立报表及重试恢复', async ({ page }) => {
  await openToday(page)
  await page.route(`**${root}/visits?**`, route => route.fulfill({ status: 503, json: { code: 'DEPENDENCY_UNAVAILABLE', message: '统计依赖暂时不可用' } }))
  await page.getByRole('button', { name: '刷新报表', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('统计依赖暂时不可用')
  await expect(page.getByTestId('total-visits')).toHaveCount(0)
  await page.getByRole('button', { name: '收入统计', exact: true }).click(); await expect(page.getByTestId('net-amount')).toBeVisible()
  await page.unroute(`**${root}/visits?**`)
  await page.getByRole('button', { name: '门诊与医生工作量', exact: true }).click()
  await expect(page.getByTestId('total-visits')).toBeVisible()
  await page.route(`**${root}/visits?**`, route => route.abort('internetdisconnected'))
  await page.getByRole('button', { name: '刷新报表', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('无法连接服务')
  await page.unroute(`**${root}/visits?**`); await page.getByRole('button', { name: '重试报表', exact: true }).click(); await expect(page.getByTestId('total-visits')).toBeVisible()
})

test('三方故障注入：不完整响应与错误范围被拒绝、迟到响应不能覆盖新查询', async ({ page }) => {
  await openToday(page)
  await page.route(`**${root}/revenue?**`, route => route.fulfill({ status: 200, json: { from: clinicToday(), to: clinicToday(), paidAmount: 123 } }))
  await page.getByRole('button', { name: '收入统计', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('报表响应不完整或口径不一致'); await expect(page.getByTestId('paid-amount')).toHaveCount(0)
  await page.unroute(`**${root}/revenue?**`)
  await page.getByRole('button', { name: '门诊与医生工作量', exact: true }).click()
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route(`**${root}/visits?**`, async route => { if (new URL(route.request().url()).searchParams.get('from') === '1990-01-01') { await gate; await route.fulfill({ json: { from: '1990-01-01', to: '1990-01-02', totalVisits: 0, daily: [], doctors: [] } }).catch(() => {}) } else await route.continue() })
  await page.getByLabel('开始日期', { exact: true }).fill('1990-01-01'); await page.getByLabel('结束日期', { exact: true }).fill('1990-01-02')
  const waiting = page.waitForRequest(r => r.url().includes('from=1990-01-01'))
  await page.getByRole('button', { name: '查询报表' }).click(); await waiting
  await page.getByRole('button', { name: '今天', exact: true }).click(); await expect(page.getByTestId('total-visits')).toBeVisible()
  release(); await page.unroute(`**${root}/visits?**`)
  await expect(page.locator('.report-applied')).toContainText(`${clinicToday()} — ${clinicToday()}`)
  await expect(page.getByText('该区间暂无门诊统计记录', { exact: true })).toHaveCount(0)
})
