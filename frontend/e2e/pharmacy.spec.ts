import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { adminCredentials, loginEmployee, newEmployee } from './support/real-backend'
import { clinicDate } from '../src/features/appointments/model'
test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要真实后端与 MySQL；故障注入不替代真实成功证据')
const root = '/api/pharmacy', workbench = `${root}/workbench/prescriptions`
const pictures = resolve('.artifacts/screenshots'); mkdirSync(pictures, { recursive: true })
let token = ''
const headers = () => ({ Authorization: `Bearer ${token}` })
test.beforeAll(async ({ request }) => { const r = await request.post('/api/auth/login', { data: adminCredentials() }); expect(r.status()).toBe(200); token = (await r.json() as { accessToken: string }).accessToken })
async function fixture(request: APIRequestContext, quantity = 10) {
  const suffix = randomUUID().slice(0, 8), account = newEmployee(['DOCTOR'])
  const user = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(user.status()).toBe(201)
  const doctorId = (await user.json() as { id: string }).id
  const owner = await request.post('/api/owner/clients', { headers: headers(), data: { name: `药房联调客户${suffix}`, phone: '13800138000', address: '药房测试' } }); expect(owner.status()).toBe(201)
  const ownerId = (await owner.json() as { id: string }).id, petName = `药房小满${suffix}`
  const pet = await request.post(`/api/owner/clients/${ownerId}/pets`, { headers: headers(), data: { details: { name: petName, species: '猫', birthDate: '2024-06-01', weightKg: 4 }, birthDateEstimated: false, version: 0 } }); expect(pet.status()).toBe(201)
  const petId = (await pet.json() as { id: string }).id
  expect((await request.post(`/api/owner/pets/${petId}/allergies`, { headers: headers(), data: { allergen: '联调过敏提示', severity: 'SEVERE', note: '配药前核对' } })).status()).toBe(201)
  const date = clinicDate(), slot = { startAt: new Date(Date.now() + 300_000).toISOString(), endAt: new Date(Date.now() + 2100_000).toISOString() }
  expect((await request.post('/api/appointment/schedules', { headers: headers(), data: { doctorId, ...slot, slotMinutes: 30 } })).status()).toBe(201)
  const appointment = await request.post('/api/appointment/appointments', { headers: headers(), data: { petId, doctorId, ...slot } }); expect(appointment.status()).toBe(201)
  const appointmentId = (await appointment.json() as { id: string }).id
  expect(clinicDate(new Date(slot.startAt))).toBe(date)
  // Formal medical writes now require the real check-in and call lifecycle.
  expect((await request.post(`/api/appointment/appointments/${appointmentId}/check-in`, { headers: headers() })).status()).toBe(200)
  expect((await request.post(`/api/appointment/queue/call?doctorId=${doctorId}`, { headers: headers() })).status()).toBe(200)
  const login = await request.post('/api/auth/login', { data: account }); expect(login.status()).toBe(200)
  const doctorHeaders = { Authorization: `Bearer ${(await login.json() as { accessToken: string }).accessToken}` }
  const record = await request.post('/api/medical/records', { headers: doctorHeaders, data: { appointmentId, symptoms: '药房联调症状', diagnosis: '不应向药房暴露的诊断', advice: '不应向药房暴露的建议' } }); expect(record.status()).toBe(201)
  const recordId = (await record.json() as { id: string }).id, drugName = `药房联调药${suffix}`
  const drug = await request.post(`${root}/drugs`, { headers: headers(), data: { name: drugName, specification: '0.5g', unit: '盒', lowStockThreshold: 8, initialQuantity: quantity } }); expect(drug.status()).toBe(201)
  const drugId = (await drug.json() as { id: string }).id
  const prescription = await request.post(`/api/medical/records/${recordId}/prescriptions`, { headers: doctorHeaders, data: { items: [{ drugName, specification: '0.5g', dosage: '1片', frequency: '每日2次', quantity: 3, days: 5, note: '饭后服用' }] } }); expect(prescription.status()).toBe(201)
  const prescriptionId = (await prescription.json() as { id: string }).id
  return { petId, petName, drugId, drugName, prescriptionId, recordId }
}
type Fixture = Awaited<ReturnType<typeof fixture>>
async function openPrescription(page: Page, f: Fixture) {
  await loginEmployee(page, adminCredentials(), '/pharmacy')
  await page.getByLabel('宠物 / 客户姓名', { exact: true }).fill(f.petName)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.locator(`[data-prescription-id="${f.prescriptionId}"]`).getByRole('button', { name: '核对发药' }).click()
  await expect(page.locator('.pharmacy-detail')).toContainText('联调过敏提示')
  await page.getByLabel('已核对患者、过敏提示、规格、用法和实物数量').check()
}
async function searchStock(page: Page, f: Fixture) {
  await page.getByRole('button', { name: '库存与预警', exact: true }).click()
  await page.getByLabel('药品名称 / 规格', { exact: true }).fill(f.drugName)
  await page.getByRole('button', { name: '查询', exact: true }).click()
  return page.locator(`[data-drug-id="${f.drugId}"]`)
}
test('正常实机：处方核对、整单发药、凭证、低库存、桌面与移动界面', async ({ page, request }) => {
  const f = await fixture(request), errors: string[] = []; page.on('pageerror', e => errors.push(e.message))
  await openPrescription(page, f)
  await expect(page.locator('.pharmacy-detail')).toContainText('每日2次'); await expect(page.locator('.pharmacy-detail')).toContainText('饭后服用')
  await expect(page.locator('.pharmacy-detail')).not.toContainText('不应向药房暴露的诊断')
  await page.screenshot({ path: resolve(pictures, 'pharmacy-review.png'), fullPage: true })
  await page.getByRole('button', { name: '确认整单发药', exact: true }).dblclick()
  await expect(page.locator('.pharmacy-receipt')).toContainText('发出 3 盒'); await expect(page.locator('.pharmacy-receipt')).toContainText('发药后库存 7 盒')
  await page.screenshot({ path: resolve(pictures, 'pharmacy-receipt.png'), fullPage: true })
  await page.locator('.el-drawer__close-btn').click()
  await expect(page.locator('.el-drawer')).toHaveCount(0)
  await page.getByLabel('发药状态', { exact: true }).selectOption('DISPENSED')
  await expect(page.locator(`[data-prescription-id="${f.prescriptionId}"]`)).toContainText('已发药')
  const row = await searchStock(page, f); await expect(row).toContainText('库存偏低')
  await page.getByLabel('仅看低库存', { exact: true }).check(); await expect(row).toBeVisible()
  await page.mouse.move(0, 0)
  await expect(page.locator('[role="tooltip"]:visible')).toHaveCount(0)
  await page.screenshot({ path: resolve(pictures, 'pharmacy-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 }); await page.screenshot({ path: resolve(pictures, 'pharmacy-mobile.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true); expect(errors).toEqual([])
})
test('正常实机：药品新建编辑、停用保留库存、人工调整及留痕', async ({ page }) => {
  await loginEmployee(page, adminCredentials(), '/pharmacy')
  await page.getByRole('button', { name: '药品目录', exact: true }).click(); await page.getByRole('button', { name: '新增药品', exact: true }).click()
  const name = `药房页面新建${randomUUID().slice(0, 8)}`
  await page.getByLabel('药品名称', { exact: true }).fill(name); await page.getByLabel('规格', { exact: true }).fill('20片'); await page.getByLabel('库存单位', { exact: true }).fill('盒'); await page.getByLabel('初始库存', { exact: true }).fill('10')
  await page.getByRole('button', { name: '保存药品', exact: true }).click(); await expect(page.locator('.el-dialog')).toHaveCount(0)
  await page.getByLabel('药品名称 / 规格', { exact: true }).fill(name); await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.getByRole('button', { name: '编辑药品', exact: true }).click(); await page.getByLabel('启用此药品', { exact: true }).uncheck(); await page.getByRole('button', { name: '保存药品', exact: true }).click(); await expect(page.locator('.pharmacy-table')).toContainText('已停用')
  await page.getByRole('button', { name: '库存与预警', exact: true }).click(); await page.getByLabel('药品名称 / 规格', { exact: true }).fill(name); await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.getByRole('button', { name: '调整 / 流水', exact: true }).click(); await expect(page.locator('.pharmacy-detail-head')).toContainText('10')
  await page.getByLabel('调整数量（正数入库，负数出库）').fill('5'); await page.getByLabel('调整原因', { exact: true }).fill('药房页面补货'); await page.getByLabel('已核对实物、单位和本次增减数量').check(); await page.getByRole('button', { name: '确认库存调整', exact: true }).click()
  await expect(page.locator('.pharmacy-detail-head')).toContainText('15'); await expect(page.locator('.pharmacy-timeline')).toContainText('药房页面补货'); await expect(page.locator('.pharmacy-timeline')).toContainText('初始入库')
})
test('异常实机：库存不足整单不扣减，补货后可发药', async ({ page, request }) => {
  const f = await fixture(request, 2); await openPrescription(page, f)
  await page.getByRole('button', { name: '确认整单发药', exact: true }).click(); await expect(page.locator('.pharmacy-detail [role="status"]')).toContainText('库存不足')
  expect((await request.get(`${root}/inventory/${f.drugId}`, { headers: headers() })).status()).toBe(200)
  expect((await (await request.get(`${root}/inventory/${f.drugId}`, { headers: headers() })).json() as { quantity: number }).quantity).toBe(2)
  expect((await request.post(`${root}/inventory/${f.drugId}/adjustments`, { headers: headers(), data: { delta: 8, reason: '库存不足后补货' } })).status()).toBe(201)
  await page.getByRole('button', { name: '确认整单发药', exact: true }).click(); await expect(page.locator('.pharmacy-receipt')).toContainText('发药后库存 7 盒')
})
test('异常实机：临床只读、发药接口越权拒绝、目录重复与负库存', async ({ page, request }) => {
  const f = await fixture(request), account = newEmployee(['STAFF'])
  expect((await request.post('/api/identity/users', { headers: headers(), data: account })).status()).toBe(201)
  const pair = await loginEmployee(page, account, '/pharmacy')
  await expect(page.getByRole('button', { name: '处方发药', exact: true })).toHaveCount(0); await expect(page.getByRole('button', { name: '新增药品', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '药品目录', exact: true }).click(); await expect(page.getByRole('button', { name: '编辑药品', exact: true })).toHaveCount(0)
  const staff = { Authorization: `Bearer ${pair.accessToken}` }
  expect((await request.get(workbench, { headers: staff })).status()).toBe(403)
  expect((await request.post(`${root}/prescriptions/${f.prescriptionId}/dispense`, { headers: staff })).status()).toBe(403)
  expect((await request.post(`${root}/drugs`, { headers: headers(), data: { name: f.drugName, specification: '0.5g', unit: '盒', lowStockThreshold: 1, initialQuantity: 0 } })).status()).toBe(409)
  expect((await request.post(`${root}/inventory/${f.drugId}/adjustments`, { headers: headers(), data: { delta: -11, reason: '无效负库存' } })).status()).toBe(409)
})
test('三方故障注入：发药提交成功后响应丢失，只读核实不再扣库存', async ({ page, request }) => {
  const f = await fixture(request); await openPrescription(page, f)
  let writes = 0
  await page.route(`**${root}/prescriptions/${f.prescriptionId}/dispense`, async route => { writes++; const r = await route.fetch(); expect(r.status()).toBe(201); await route.abort('connectionreset') })
  await page.getByRole('button', { name: '确认整单发药', exact: true }).click(); await expect(page.getByRole('button', { name: '查询发药结果', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '查询发药结果', exact: true }).click(); await expect(page.locator('.pharmacy-receipt')).toContainText('发药后库存 7 盒'); expect(writes).toBe(1)
})
test('三方故障注入：请求未到达，显式重试保持原处方及幂等键', async ({ page, request }) => {
  const f = await fixture(request); await openPrescription(page, f)
  const keys: (string | undefined)[] = []
  await page.route(`**${root}/prescriptions/${f.prescriptionId}/dispense`, async route => { keys.push(route.request().headers()['idempotency-key']); if (keys.length === 1) await route.abort('timedout'); else await route.continue() })
  await page.getByRole('button', { name: '确认整单发药', exact: true }).click(); await expect(page.getByRole('button', { name: '使用原幂等键重试发药' })).toBeVisible()
  await page.getByRole('button', { name: '使用原幂等键重试发药' }).click(); await expect(page.locator('.pharmacy-receipt')).toContainText('发出 3 盒'); expect(keys).toHaveLength(2); expect(keys[0]).toBeTruthy(); expect(keys[1]).toBe(keys[0])
})
test('三方故障注入：人工调整成功后断连，冻结输入并核实流水', async ({ page, request }) => {
  const f = await fixture(request); await loginEmployee(page, adminCredentials(), '/pharmacy'); const row = await searchStock(page, f); await row.getByRole('button', { name: '调整 / 流水', exact: true }).click()
  await expect(page.locator('.pharmacy-detail-head')).toContainText('10')
  await page.route(`**${root}/inventory/${f.drugId}/adjustments`, async route => { if (route.request().method() === 'POST') { const r = await route.fetch(); expect(r.status()).toBe(201); await route.abort('connectionreset') } else await route.continue() })
  await page.getByLabel('调整数量（正数入库，负数出库）').fill('5'); await page.getByLabel('调整原因', { exact: true }).fill('断连核实补货'); await page.getByLabel('已核对实物、单位和本次增减数量').check(); await page.getByRole('button', { name: '确认库存调整', exact: true }).click()
  await expect(page.getByRole('button', { name: '确认库存调整', exact: true })).toBeDisabled(); await expect(page.getByLabel('调整原因', { exact: true })).toHaveValue('断连核实补货')
  await page.getByRole('button', { name: '刷新库存与流水', exact: true }).click(); await expect(page.locator('.pharmacy-detail-head')).toContainText('15'); await expect(page.locator('.pharmacy-timeline')).toContainText('断连核实补货')
})
test('三方故障注入：核对上游 503 与目录网络异常显示错误及恢复', async ({ page, request }) => {
  const f = await fixture(request); await loginEmployee(page, adminCredentials(), '/pharmacy')
  await page.getByLabel('宠物 / 客户姓名', { exact: true }).fill(f.petName); await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.route(`**${workbench}/${f.prescriptionId}`, route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'HTTP_ERROR', message: '上游资料暂不可用' }) }))
  await page.locator(`[data-prescription-id="${f.prescriptionId}"]`).getByRole('button', { name: '核对发药' }).click(); await expect(page.getByRole('button', { name: '确认整单发药', exact: true })).toHaveCount(0); await expect(page.locator('.pharmacy-detail')).toContainText('上游资料暂不可用')
  await page.unroute(`**${workbench}/${f.prescriptionId}`); await page.getByRole('button', { name: '重试核对资料', exact: true }).click(); await expect(page.locator('.pharmacy-detail')).toContainText('联调过敏提示')
  await page.locator('.el-drawer__close-btn').click(); await page.route(`**${root}/drugs?**`, route => route.abort('internetdisconnected'))
  await page.getByRole('button', { name: '药品目录', exact: true }).click(); await expect(page.getByText('药房数据读取失败', { exact: true })).toBeVisible(); await page.unroute(`**${root}/drugs?**`); await page.getByRole('button', { name: '重试列表', exact: true }).click(); await expect(page.locator('.pharmacy-table')).toBeVisible()
})

test('三方故障注入：发药已确认后核对上游失效，仍保留服务端凭证', async ({ page, request }) => {
  const f = await fixture(request); await openPrescription(page, f)
  await page.route(`**${workbench}/${f.prescriptionId}`, route => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"核对依赖故障"}' }))
  await page.getByRole('button', { name: '确认整单发药', exact: true }).click()
  await expect(page.locator('.pharmacy-receipt')).toContainText('发药后库存 7 盒')
  await expect(page.getByRole('button', { name: '确认整单发药', exact: true })).toHaveCount(0)
})
