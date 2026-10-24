import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { adminCredentials, loginEmployee, newEmployee } from './support/real-backend'
import { clinicDate } from '../src/features/appointments/model'
import type { Consultation } from '../src/features/medical/model'

test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要真实后端与 MySQL')
let token = ''
const headers = () => ({ Authorization: `Bearer ${token}` })
const pictures = resolve('.artifacts/screenshots'); mkdirSync(pictures, { recursive: true })
test.beforeAll(async ({ request }) => { const r = await request.post('/api/auth/login', { data: adminCredentials() }); expect(r.status()).toBe(200); token = (await r.json() as { accessToken: string }).accessToken })
test.beforeEach(({ page }) => { page.setDefaultTimeout(15_000) })
async function fixture(request: APIRequestContext, called = true) {
  const account = newEmployee(['DOCTOR']), suffix = randomUUID().slice(0, 8)
  const user = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(user.status()).toBe(201)
  const doctorId = (await user.json() as { id: string }).id
  const client = await request.post('/api/owner/clients', { headers: headers(), data: { name: `诊疗客户${suffix}`, phone: '13800138000', address: '诊疗联调' } }); expect(client.status()).toBe(201)
  const petName = `诊疗小满${suffix}`
  const pet = await request.post(`/api/owner/clients/${(await client.json() as { id: string }).id}/pets`, { headers: headers(), data: { details: { name: petName, species: '猫', birthDate: '2024-06-01', weightKg: 4 }, birthDateEstimated: false, version: 0 } }); expect(pet.status()).toBe(201)
  const petId = (await pet.json() as { id: string }).id
  const start = Math.ceil((Date.now() + 300_000) / 60_000) * 60_000
  expect(clinicDate(new Date(start))).toBe(clinicDate())
  const slot = { startAt: new Date(start).toISOString(), endAt: new Date(start + 1800_000).toISOString() }
  expect((await request.post('/api/appointment/schedules', { headers: headers(), data: { doctorId, ...slot, slotMinutes: 30 } })).status()).toBe(201)
  const booked = await request.post('/api/appointment/appointments', { headers: headers(), data: { petId, doctorId, ...slot } }); expect(booked.status()).toBe(201)
  const id = (await booked.json() as { id: string }).id
  expect((await request.post(`/api/appointment/appointments/${id}/check-in`, { headers: headers() })).status()).toBe(200)
  if (called) expect((await request.post(`/api/appointment/queue/call?doctorId=${doctorId}`, { headers: headers() })).status()).toBe(200)
  const login = await request.post('/api/auth/login', { data: account }); expect(login.status()).toBe(200)
  const doctorToken = (await login.json() as { accessToken: string }).accessToken
  return { account, doctorId, doctorToken, petId, petName, id, url: `/api/medical/consultations/${id}`, target: `/medical?appointmentId=${id}&date=${clinicDate()}` }
}
type Fixture = Awaited<ReturnType<typeof fixture>>
const doctorHeaders = (f: Fixture) => ({ Authorization: `Bearer ${f.doctorToken}` })
async function read(request: APIRequestContext, f: Fixture) { const r = await request.get(f.url, { headers: doctorHeaders(f) }); expect(r.status()).toBe(200); return r.json() as Promise<Consultation> }
async function open(page: Page, f: Fixture) { await loginEmployee(page, f.account, f.target); await expect(page.getByRole('heading', { name: f.petName, exact: false })).toBeVisible() }
async function fill(page: Page) {
  await page.getByLabel('症状 / 主诉', { exact: true }).fill('持续两天食欲下降，精神尚可')
  await page.getByLabel('诊断', { exact: true }).fill('门诊观察诊断')
  await page.getByLabel('医嘱', { exact: true }).fill('观察饮食与精神，按医嘱复诊')
}
async function save(page: Page) { await page.getByRole('button', { name: '保存诊疗草稿', exact: true }).click(); await expect(page.getByText('草稿已保存到服务端，可重新登录后恢复。', { exact: true })).toBeVisible() }
async function submit(page: Page) { await page.getByRole('button', { name: '提交正式病历与处方', exact: true }).click(); await page.getByRole('button', { name: '确认正式提交', exact: true }).click() }
async function complete(page: Page) { await page.getByRole('button', { name: '完成诊疗', exact: true }).click(); await page.getByRole('button', { name: '确认完成诊疗', exact: true }).click(); await expect(page.getByText('诊疗已完成', { exact: true }).first()).toBeVisible() }

test('正常闭环：预约签到、医生叫号、草稿恢复、处方、完成、收费发药及移动布局', async ({ page, request }) => {
  const f = await fixture(request, false), errors: string[] = []; page.on('pageerror', e => errors.push(e.message))
  const drugName = `诊疗联调药${randomUUID().slice(0, 8)}`
  const drug = await request.post('/api/pharmacy/drugs', { headers: headers(), data: { name: drugName, specification: '联调规格', unit: '盒', lowStockThreshold: 2, initialQuantity: 20 } }); expect(drug.status()).toBe(201)
  await loginEmployee(page, f.account, '/medical')
  await page.getByRole('button', { name: '叫下一位', exact: true }).click()
  await expect(page.getByRole('heading', { name: f.petName, exact: false })).toBeVisible()
  await fill(page); await page.getByRole('radio', { name: '开具处方', exact: true }).check()
  await page.getByRole('button', { name: '从药品目录添加', exact: true }).click()
  await page.getByLabel('查找处方药品', { exact: true }).fill(drugName)
  await page.getByRole('button', { name: '查找药品', exact: true }).click()
  await page.getByRole('button', { name: new RegExp(drugName) }).click()
  await page.getByLabel('单次剂量 1', { exact: true }).fill('遵医嘱用量')
  await page.getByLabel('频次 1', { exact: true }).fill('每日一次')
  await page.getByLabel('发药总数量 1', { exact: true }).fill('3')
  await page.getByLabel('用药天数 1', { exact: true }).fill('3')
  await save(page)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await open(page, f); await expect(page.getByLabel('诊断', { exact: true })).toHaveValue('门诊观察诊断')
  await expect(page.getByLabel('药品名称 1', { exact: true })).toHaveValue(drugName)
  await page.screenshot({ path: resolve(pictures, 'medical-desktop.png'), fullPage: true })
  await submit(page); await complete(page)
  const value = await read(request, f); expect(value.record!.prescriptions).toHaveLength(1)
  await page.getByRole('button', { name: '历史与留痕', exact: true }).click(); await expect(page.getByText('提交正式病历与处方决策', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await loginEmployee(page, adminCredentials(), f.target)
  await expect(page.getByRole('button', { name: '保存诊疗草稿', exact: true })).toHaveCount(0)
  const charge = await request.post('/api/billing/charge-items', { headers: headers(), data: { name: `诊疗检查${randomUUID().slice(0, 8)}`, amount: 20 } }); expect(charge.status()).toBe(201)
  const bill = await request.post('/api/billing/bills', { headers: headers(), data: { medicalRecordId: value.record!.id, prescriptionId: value.record!.prescriptions[0]!.id, items: [{ chargeItemId: (await charge.json() as { id: string }).id, quantity: 1 }] } }); expect(bill.status()).toBe(201)
  const billId = (await bill.json() as { id: string }).id
  expect((await request.post(`/api/billing/bills/${billId}/pay`, { headers: headers() })).status()).toBe(201)
  await page.getByRole('button', { name: '刷新交接进度', exact: true }).click(); await expect(page.getByText('已支付', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: '前往收费工作台 →', exact: true }).click(); await expect(page).toHaveURL(new RegExp(`billId=${billId}`)); await page.locator('.el-drawer__close-btn').click()
  await page.getByRole('link', { name: '诊疗工作台', exact: true }).click()
  await page.getByLabel('诊疗接诊医生', { exact: true }).selectOption(f.doctorId)
  await page.locator(`[data-consultation-id="${f.id}"]`).click()
  await page.getByRole('link', { name: '前往处方核对 →', exact: true }).click()
  const drawer = page.getByRole('dialog', { name: '处方核对与发药' }); await expect(drawer).toBeVisible()
  await drawer.getByRole('checkbox').check(); await drawer.getByRole('button', { name: '确认整单发药', exact: true }).click()
  await expect(drawer.getByRole('heading', { name: '发药凭证', exact: true })).toBeVisible()
  await page.locator('.el-drawer__close-btn').click()
  await page.getByRole('link', { name: '诊疗工作台', exact: true }).click()
  await page.getByLabel('诊疗接诊医生', { exact: true }).selectOption(f.doctorId)
  await page.locator(`[data-consultation-id="${f.id}"]`).click()
  await expect(page.getByText('已发药', { exact: true })).toBeVisible()
  await page.screenshot({ path: resolve(pictures, 'medical-handoff.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await expect(page.getByText('已发药', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await page.screenshot({ path: resolve(pictures, 'medical-mobile.png'), fullPage: true })
  expect(errors).toEqual([])
})

test('正常无处方：明确无需处方，完成后锁定', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f); await fill(page)
  await expect(page.getByRole('button', { name: '提交正式病历与处方', exact: true })).toBeDisabled()
  await page.getByRole('radio', { name: '本次无需处方', exact: true }).check(); await save(page); await submit(page); await complete(page)
  const value = await read(request, f); expect(value.prescriptionChoice).toBe('NONE'); expect(value.record!.prescriptions).toHaveLength(0); expect(value.canWrite).toBe(false)
})

test('异常与权限：未叫号只读，非接诊医生和前台管理员不可写', async ({ page, request }) => {
  const f = await fixture(request, false); await open(page, f)
  await expect(page.getByLabel('诊断', { exact: true })).toBeDisabled()
  expect((await request.post(f.url + '/complete', { headers: doctorHeaders(f), data: { version: 0, noPrescription: true } })).status()).toBe(409)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  for (const roles of [['STAFF'], ['DOCTOR'], ['ADMIN']]) {
    const account = newEmployee(roles), created = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(created.status()).toBe(201)
    const pair = await loginEmployee(page, account, f.target)
    await expect(page.getByRole('button', { name: '保存诊疗草稿', exact: true })).toHaveCount(0)
    expect((await request.put(f.url + '/draft', { headers: { Authorization: `Bearer ${pair.accessToken}` }, data: { version: 0, draft: { symptoms: '', diagnosis: '', advice: '', prescriptionChoice: 'NONE', items: [] } } })).status()).toBe(403)
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
  }
})

test('并发编辑：旧版本拒绝覆盖，本机内容保留后按最新版本保存', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f); await fill(page)
  expect((await request.put(f.url + '/draft', { headers: doctorHeaders(f), data: { version: 0, draft: { symptoms: '另一窗口症状', diagnosis: '另一窗口诊断', advice: '', prescriptionChoice: 'NONE', items: [] } } })).status()).toBe(200)
  await page.getByRole('button', { name: '保存诊疗草稿', exact: true }).click(); await expect(page.getByText(/写入结果未确认或内容已有更新/)).toBeVisible()
  await page.getByRole('button', { name: '查询服务端结果', exact: true }).click()
  await expect(page.getByLabel('诊断', { exact: true })).toHaveValue('门诊观察诊断')
  await page.getByRole('button', { name: '保留本机内容继续编辑', exact: true }).click(); await save(page)
  expect((await read(request, f)).draft!.diagnosis).toBe('门诊观察诊断')
})

test('网络故障：保存已落库但响应丢失，查询恢复且不重复写入', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f); await fill(page); let writes = 0
  await page.route(`**${f.url}/draft`, async route => { writes++; const response = await route.fetch(); expect(response.status()).toBe(200); await route.abort('connectionreset') })
  await page.getByRole('button', { name: '保存诊疗草稿', exact: true }).click(); await expect(page.getByText(/写入结果未确认或内容已有更新/)).toBeVisible()
  await expect(page.getByRole('button', { name: '保存诊疗草稿', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '查询服务端结果', exact: true }).click()
  page.once('dialog', dialog => dialog.accept()); await page.getByRole('button', { name: '采用服务端内容', exact: true }).click()
  expect(writes).toBe(1); expect((await read(request, f)).version).toBe(1)
  await expect(page.getByLabel('诊断', { exact: true })).toHaveValue('门诊观察诊断')
})

test('网络故障：正式提交与完成响应丢失，核实真实凭证后恢复', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f); await fill(page); await page.getByRole('radio', { name: '本次无需处方', exact: true }).check(); await save(page)
  let submits = 0, completes = 0
  await page.route(`**${f.url}/submit`, async route => { submits++; expect((await route.fetch()).status()).toBe(200); await route.abort('connectionreset') })
  await submit(page); await expect(page.getByText(/写入结果未确认或内容已有更新/)).toBeVisible()
  await page.getByRole('button', { name: '查询服务端结果', exact: true }).click(); await page.getByRole('button', { name: '采用服务端内容', exact: true }).click()
  await expect(page.getByRole('heading', { name: '正式病历', exact: true })).toBeVisible(); expect(submits).toBe(1)
  await page.route(`**${f.url}/complete`, async route => { completes++; expect((await route.fetch()).status()).toBe(200); await route.abort('connectionreset') })
  await page.getByRole('button', { name: '完成诊疗', exact: true }).click(); await page.getByRole('button', { name: '确认完成诊疗', exact: true }).click()
  await expect(page.getByText(/写入结果未确认或内容已有更新/)).toBeVisible()
  await page.getByRole('button', { name: '查询服务端结果', exact: true }).click(); await page.getByRole('button', { name: '采用服务端内容', exact: true }).click()
  expect(completes).toBe(1); expect((await read(request, f)).completedAt).toBeTruthy()
})

test('上下游故障：诊疗读取503、过敏读取失败及交接503分别报错并可恢复', async ({ page, request }) => {
  const f = await fixture(request)
  await page.route(`**${f.url}`, route => route.fulfill({ status: 503, json: { code: 'INTERNAL_ERROR', message: '预约依赖不可用' } }))
  await loginEmployee(page, f.account, f.target)
  await expect(page.getByText('诊疗内容读取失败', { exact: true })).toBeVisible()
  await expect(page.getByLabel('诊断', { exact: true })).toHaveCount(0)
  await page.unroute(`**${f.url}`)
  await page.route(`**/api/owner/pets/${f.petId}/allergies`, route => route.abort('internetdisconnected'))
  await page.route(`**${f.url}/handoff`, route => route.fulfill({ status: 503, json: { code: 'INTERNAL_ERROR', message: '收费服务不可用' } }))
  await page.getByRole('button', { name: '重新读取诊疗', exact: true }).click()
  await expect(page.getByText('交接进度暂时无法核实', { exact: true })).toBeVisible(); await expect(page.getByText(/过敏资料读取失败/)).toBeVisible()
  await page.unroute(`**${f.url}/handoff`); await page.getByRole('button', { name: '重新核实交接', exact: true }).click()
  await expect(page.getByText('等待正式病历', { exact: true })).toBeVisible()
  await page.unroute(`**/api/owner/pets/${f.petId}/allergies`); await page.getByRole('button', { name: '重新读取过敏史', exact: true }).click()
  await expect(page.getByText('未登记有效过敏记录，请结合问诊核对。', { exact: true })).toBeVisible()
})

test('异常响应：残缺成功正文拒绝，网络未发送时保存文本并可继续', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f); await fill(page)
  await page.route(`**${f.url}/draft`, route => route.fulfill({ status: 200, json: { version: 1 } }))
  await page.getByRole('button', { name: '保存诊疗草稿', exact: true }).click(); await expect(page.getByText(/写入结果未确认或内容已有更新/)).toBeVisible()
  await page.unroute(`**${f.url}/draft`); await page.getByRole('button', { name: '查询服务端结果', exact: true }).click()
  await page.getByRole('button', { name: '保留本机内容继续编辑', exact: true }).click(); await save(page)
  expect((await read(request, f)).version).toBe(1)
})

test('异常离开：未保存草稿切换页面可取消，并保留输入', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f); await fill(page)
  page.once('dialog', dialog => dialog.dismiss())
  await page.getByRole('link', { name: '诊疗工作台', exact: true }).click()
  await expect(page.getByLabel('诊断', { exact: true })).toHaveValue('门诊观察诊断')
  await expect(page).toHaveURL(new RegExp(f.id))
  await save(page)
  await page.getByRole('link', { name: '诊疗工作台', exact: true }).click()
  await expect(page.getByRole('heading', { name: '选择一位患者，开始本次诊疗', exact: true })).toBeVisible()
})

test('叫号响应丢失：核实已叫号记录前禁止再叫下一位', async ({ page, request }) => {
  const f = await fixture(request, false); let calls = 0
  await page.route('**/api/appointment/queue/call?*', async route => { calls++; expect((await route.fetch()).status()).toBe(200); await route.abort('connectionreset') })
  await loginEmployee(page, f.account, '/medical')
  await page.getByRole('button', { name: '叫下一位', exact: true }).click()
  await expect(page.getByText(/叫号结果未确认/)).toBeVisible()
  await expect(page.getByRole('button', { name: '叫下一位', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: '核实已叫号记录', exact: true }).click()
  await expect(page.locator(`[data-consultation-id="${f.id}"]`)).toContainText('已叫号')
  await page.getByRole('button', { name: '已核对叫号结果', exact: true }).click()
  await page.locator(`[data-consultation-id="${f.id}"]`).click()
  await expect(page.getByLabel('诊断', { exact: true })).toBeEnabled()
  expect(calls).toBe(1)
})

test('异常处方决策：取消清空时保持开方选项与明细一致', async ({ page, request }) => {
  const f = await fixture(request); await open(page, f)
  await page.getByRole('radio', { name: '开具处方', exact: true }).check()
  await page.getByRole('button', { name: '手工添加药品', exact: true }).click()
  await page.getByLabel('药品名称 1', { exact: true }).fill('需保留的草稿药品')
  page.once('dialog', dialog => dialog.dismiss())
  // click is intentional: a cancelled radio choice must remain unchecked.
  await page.getByRole('radio', { name: '本次无需处方', exact: true }).click()
  await expect(page.getByRole('radio', { name: '开具处方', exact: true })).toBeChecked()
  await expect(page.getByLabel('药品名称 1', { exact: true })).toHaveValue('需保留的草稿药品')
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('radio', { name: '本次无需处方', exact: true }).check()
  await expect(page.getByLabel('药品名称 1', { exact: true })).toHaveCount(0)
  await save(page); expect((await read(request, f)).draft!.items).toHaveLength(0)
})
