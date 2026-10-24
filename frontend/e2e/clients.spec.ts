import { test, expect, type APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { createServer, request as httpRequest } from 'node:http'
import { adminCredentials, loginEmployee, newEmployee } from './support/real-backend'
import type { Client, Pet, Allergy, Page as ResultPage, Operation } from '../src/features/clients/model'

test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要真实后端/MySQL和管理员；无 Mock 成功替代')
const root = '/api/owner'
const pictures = resolve('.artifacts/screenshots')
mkdirSync(pictures, { recursive: true })
let token = '', staffToken = '', doctorToken = '', ownerToken = '', pharmacyToken = '', doctorId = ''
const staff = newEmployee(), doctor = newEmployee(['DOCTOR']), pharmacy = newEmployee(['PHARMACY']), owner = newEmployee(['OWNER'])
const headers = () => ({ Authorization: `Bearer ${token}` })
const contact = (name = `联调客户${randomUUID().slice(0, 8)}`) => ({ name, phone: '13800138000', address: '联调测试地址' })
const petDetails = (name = '小满') => ({ name, species: '猫', birthDate: '2024-06-01', weightKg: 4.25, birthDateEstimated: true })
async function client(request: APIRequestContext, name?: string): Promise<Client> {
  const r = await request.post(`${root}/clients`, { headers: headers(), data: contact(name) }); expect(r.status()).toBe(201); return r.json() as Promise<Client>
}
async function pet(request: APIRequestContext, c: Client): Promise<Pet> {
  const r = await request.post(`${root}/clients/${c.id}/pets`, { headers: headers(), data: { details: petDetails(), birthDateEstimated: true, version: 0 } }); expect(r.status()).toBe(201); return r.json() as Promise<Pet>
}
test.beforeAll(async ({ request }) => {
  const login = await request.post('/api/auth/login', { data: adminCredentials() }); expect(login.status()).toBe(200)
  token = (await login.json() as { accessToken: string }).accessToken
  for (const account of [staff, doctor, pharmacy, owner]) {
    const created = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(created.status()).toBe(201)
    if (account === doctor) doctorId = (await created.json() as { id: string }).id
    const response = await request.post('/api/auth/login', { data: { username: account.username, password: account.password } }); expect(response.status()).toBe(200)
    const access = (await response.json() as { accessToken: string }).accessToken
    if (account === staff) staffToken = access
    if (account === doctor) doctorToken = access
    if (account === owner) ownerToken = access
    if (account === pharmacy) pharmacyToken = access
  }
})

test('实机闭环：建客户、估算生日宠物、健康记录与留痕更正、账号绑定、归档', async ({ page }) => {
  const name = `UI联调客户${randomUUID().slice(0, 6)}`
  const runtimeErrors: string[] = []
  page.on('pageerror', (e) => runtimeErrors.push(e.message))
  await loginEmployee(page, adminCredentials(), '/clients')
  await page.getByRole('button', { name: '新建客户', exact: true }).click()
  await page.getByLabel('客户姓名', { exact: true }).fill(name)
  await page.getByLabel('联系电话', { exact: true }).fill('13800138000')
  await page.getByLabel('联系地址（选填）').fill('杭州市 · 联调演示')
  const creation = page.waitForResponse((r) => r.url().endsWith(`${root}/clients`) && r.request().method() === 'POST')
  await page.getByRole('button', { name: '保存档案', exact: true }).dblclick()
  const created = await creation; expect(created.status()).toBe(201)
  const c = await created.json() as Client
  await expect(page).toHaveURL(new RegExp(`/clients/${c.id}$`))
  await expect(page.getByRole('heading', { name })).toBeVisible()
  await expect(page.getByText('待绑定', { exact: true })).toBeVisible()
  await page.screenshot({ path: resolve(pictures, 'client-detail-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.getByRole('button', { name: '新增宠物', exact: true }).click()
  await page.getByLabel('宠物名字', { exact: true }).fill('小满')
  await page.getByLabel('物种', { exact: true }).fill('猫')
  await page.getByLabel('出生日期', { exact: true }).fill('2024-06-01')
  await page.getByLabel('体重（kg）', { exact: true }).fill('4.25')
  await page.getByLabel('出生日期为估算').check()
  await page.getByRole('button', { name: '保存档案', exact: true }).click()
  await expect(page).toHaveURL(/\/pets\/[a-f\d-]+$/)
  await expect(page.getByRole('heading', { name: '小满', exact: true })).toBeVisible()
  await expect(page.getByText('估算', { exact: true })).toBeVisible()
  await expect(page.locator('.archive-heading')).toContainText('约 ')
  const petId = page.url().split('/').pop()!
  await page.getByRole('button', { name: '新增疫苗记录' }).click()
  await page.getByLabel('疫苗名称', { exact: true }).fill('猫三联')
  await page.getByLabel('接种日期', { exact: true }).fill('2026-01-10')
  await page.getByLabel('下次应接种日期（选填）').fill('2027-01-10')
  await page.getByRole('button', { name: '保存记录', exact: true }).click()
  await expect(page.getByRole('heading', { name: '猫三联', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '过敏资料', exact: true }).click()
  await page.getByRole('button', { name: '新增过敏记录' }).click()
  await page.getByLabel('过敏原', { exact: true }).fill('青霉素')
  await page.getByLabel('严重程度').selectOption('SEVERE')
  await page.getByRole('button', { name: '保存记录', exact: true }).click()
  await expect(page.getByText('过敏提示', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '留痕更正', exact: true }).click()
  await page.getByLabel('过敏原', { exact: true }).fill('花粉')
  await page.getByLabel('严重程度').selectOption('MILD')
  await page.getByLabel('更正原因').fill('核对原始登记后更正过敏原')
  await page.getByRole('button', { name: '提交更正', exact: true }).click()
  await expect(page.getByRole('heading', { name: /花粉/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: /青霉素/ })).toHaveCount(0)
  await page.getByLabel('显示更正前记录').check()
  await expect(page.getByRole('heading', { name: /青霉素/ })).toBeVisible()
  await expect(page.getByText('已被更正', { exact: true })).toBeVisible()
  await expect(page.locator('.archive-health-list')).toContainText('操作人：')
  await page.screenshot({ path: resolve(pictures, 'pet-health-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: resolve(pictures, 'pet-health-mobile.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.getByRole('link', { name: `${name}的客户档案` }).click()
  await page.getByRole('button', { name: '绑定主人账号' }).click()
  await page.getByLabel('准确登录账号').fill(owner.username)
  await page.getByRole('button', { name: '查询账号', exact: true }).click()
  await expect(page.getByTestId('binding-candidate')).toContainText(owner.username)
  await page.getByLabel('我已核对姓名、电话及本人身份，确认绑定').check()
  await page.getByRole('button', { name: '确认绑定', exact: true }).click()
  await expect(page.getByText('已绑定登录账号', { exact: false })).toBeVisible()
  const ownerView = await page.request.get(`${root}/pets/${petId}`, { headers: { Authorization: `Bearer ${ownerToken}` } }); expect(ownerView.status()).toBe(200)
  await page.getByRole('button', { name: '归档客户', exact: true }).click()
  await page.getByLabel('归档原因').fill('实机闭环验收，保留历史档案')
  await page.getByRole('button', { name: '确认归档', exact: true }).click()
  await expect(page.locator('.archive-notice').getByText('客户已归档', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '新增宠物', exact: true })).toHaveCount(0)
  await expect(page.getByText('归档客户', { exact: true })).toBeVisible()
  expect(runtimeErrors).toEqual([])
})

test('真实检索、分页、重复联系电话、估算属性及写入审计', async ({ request, page }) => {
  const prefix = `搜索${randomUUID().slice(0, 6)}`
  const clients: Client[] = []
  for (let n = 0; n < 11; n++) clients.push(await client(request, `${prefix}-${n}`))
  const p = await pet(request, clients[0]!)
  const list = await request.get(`${root}/clients?query=${encodeURIComponent(prefix)}&size=10&page=1`, { headers: headers() })
  expect(list.status()).toBe(200)
  const body = await list.json() as ResultPage<Client>
  expect(body.totalElements).toBe(11); expect(body.totalPages).toBe(2); expect(body.items).toHaveLength(1)
  expect(p.birthDateEstimated).toBe(true)
  const update = await request.put(`${root}/clinic-pets/${p.id}`, { headers: headers(), data: { details: { ...petDetails(), weightKg: 4.5 }, birthDateEstimated: true, version: p.version } }); expect(update.status()).toBe(200)
  const events = await request.get(`${root}/clinic-pets/${p.id}/operations`, { headers: headers() }); expect(events.status()).toBe(200)
  const audit = await events.json() as ResultPage<Operation>
  expect(audit.items.map((r) => r.action)).toEqual(['UPDATE_PET', 'CREATE_PET'])
  expect(audit.items[0]!.beforeJson).toContain('4.25'); expect(audit.items[0]!.afterJson).toContain('4.5')
  await loginEmployee(page, adminCredentials(), '/clients')
  await page.getByLabel('搜索客户或宠物').fill(prefix)
  await page.getByRole('button', { name: '搜索', exact: true }).click()
  await expect(page.getByText('共 11 份档案')).toBeVisible()
  await page.getByRole('button', { name: '下一页' }).click()
  await expect(page.getByText('2 / 2')).toBeVisible()
  await expect(page.locator('.archive-table tbody tr')).toHaveCount(1)
  await page.screenshot({ path: resolve(pictures, 'clients-directory-desktop.png'), fullPage: true, animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  const overflow = await page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth, elements: Array.from(document.querySelectorAll('body *')).filter((e) => e.getBoundingClientRect().right > innerWidth + 1).map((e) => ({ tag: e.tagName, class: e.className, right: e.getBoundingClientRect().right })).slice(0, 20) }))
  if (overflow.scroll > overflow.width) console.log('Mobile overflow diagnostics:', JSON.stringify(overflow))
  await page.screenshot({ path: resolve(pictures, 'clients-directory-mobile.png'), fullPage: true, animations: 'disabled' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('真实权限隔离：OWNER/PHARMACY不可检索全院，员工可维护且仅ADMIN绑定', async ({ request, page }) => {
  const c = await client(request), p = await pet(request, c)
  for (const access of [ownerToken, pharmacyToken]) {
    const auth = { Authorization: `Bearer ${access}` }
    expect((await request.get(`${root}/clients`, { headers: auth })).status()).toBe(403)
    expect((await request.get(`${root}/clinic-pets`, { headers: auth })).status()).toBe(403)
    expect((await request.post(`${root}/clients`, { headers: auth, data: contact() })).status()).toBe(403)
    expect((await request.get(`${root}/pets/${p.id}`, { headers: auth })).status()).toBe(403)
  }
  for (const access of [staffToken, doctorToken]) {
    const auth = { Authorization: `Bearer ${access}` }
    expect((await request.get(`${root}/clients`, { headers: auth })).status()).toBe(200)
    const allergy = await request.post(`${root}/pets/${p.id}/allergies`, { headers: auth, data: { allergen: '权限联调', severity: 'MILD', recordedBy: randomUUID(), recordedByName: '伪造操作人' } })
    expect(allergy.status()).toBe(201)
    const saved = await allergy.json() as Allergy
    expect(saved.recordedByName).toBe(access === staffToken ? staff.username : doctor.username)
    expect((await request.get(`${root}/clients/binding-candidate?username=${owner.username}`, { headers: auth })).status()).toBe(403)
    expect((await request.post(`${root}/clients/${c.id}/bind`, { headers: auth, data: { version: c.version, username: owner.username, expectedUserId: randomUUID(), confirmed: true } })).status()).toBe(403)
  }
  expect((await request.get(`${root}/clients`)).status()).toBe(401)
  await loginEmployee(page, pharmacy, '/clients')
  await expect(page).toHaveURL(/\/forbidden$/)
  await expect(page.getByRole('link', { name: '客户与宠物', exact: true })).toHaveCount(0)
})

test('真实参数错误、缺失资源、错误账号身份与绑定保护', async ({ request }) => {
  expect((await request.post(`${root}/clients`, { headers: headers(), data: { name: ' ', phone: 'x' } })).status()).toBe(400)
  expect((await request.get(`${root}/clients?query=${'a'.repeat(101)}`, { headers: headers() })).status()).toBe(400)
  expect((await request.get(`${root}/owners/${randomUUID()}`, { headers: headers() })).status()).toBe(404)
  const c = await client(request), p = await pet(request, c)
  for (const data of [{ ...petDetails(), birthDate: '2999-01-01' }, { ...petDetails(), weightKg: 0 }]) {
    expect((await request.post(`${root}/clients/${c.id}/pets`, { headers: headers(), data: { details: data, version: 0, birthDateEstimated: false } })).status()).toBe(400)
  }
  expect((await request.post(`${root}/pets/${p.id}/vaccinations`, { headers: headers(), data: { vaccineName: '疫苗', administeredOn: '2026-01-10', nextDueOn: '2025-01-01' } })).status()).toBe(400)
  expect((await request.get(`${root}/clients/binding-candidate?username=${staff.username}`, { headers: headers() })).status()).toBe(409)
  const freshOwner = newEmployee(['OWNER'])
  const created = await request.post('/api/identity/users', { headers: headers(), data: freshOwner }); expect(created.status()).toBe(201)
  const accountId = (await created.json() as { id: string }).id
  const binding = { username: freshOwner.username, expectedUserId: accountId, version: c.version, confirmed: true }
  expect((await request.post(`${root}/clients/${c.id}/bind`, { headers: headers(), data: { ...binding, confirmed: false } })).status()).toBe(400)
  expect((await request.post(`${root}/clients/${c.id}/bind`, { headers: headers(), data: { ...binding, expectedUserId: randomUUID() } })).status()).toBe(409)
  expect((await request.post(`${root}/clients/${c.id}/bind`, { headers: headers(), data: binding })).status()).toBe(200)
  const another = await client(request)
  expect((await request.post(`${root}/clients/${another.id}/bind`, { headers: headers(), data: { ...binding, version: another.version } })).status()).toBe(409)
  const latest = await (await request.get(`${root}/owners/${c.id}`, { headers: headers() })).json() as Client
  expect((await request.post(`${root}/clients/${c.id}/bind`, { headers: headers(), data: { ...binding, version: latest.version } })).status()).toBe(409)
  // Legacy physical deletion no longer removes health/history.
  expect((await request.delete(`${root}/pets/${p.id}`, { headers: headers() })).status()).toBe(409)
  expect((await request.get(`${root}/pets/${p.id}`, { headers: headers() })).status()).toBe(200)
})

test('真实并发：同版本编辑只允许一笔；同一健康原记录只允许一次更正', async ({ request }) => {
  const c = await client(request), p = await pet(request, c)
  const updates = await Promise.all(['并发甲', '并发乙'].map((name) => request.put(`${root}/clients/${c.id}`, { headers: headers(), data: { details: contact(name), version: c.version } })))
  expect(updates.map((r) => r.status()).sort()).toEqual([200, 409])
  const health = await request.post(`${root}/pets/${p.id}/allergies`, { headers: headers(), data: { allergen: '原始登记', severity: 'SEVERE' } })
  expect(health.status()).toBe(201)
  const old = await health.json() as Allergy
  const corrections = await Promise.all(['更正甲', '更正乙'].map((allergen) => request.post(`${root}/pets/${p.id}/allergies/${old.id}/corrections`, { headers: headers(), data: { record: { allergen, severity: 'MILD' }, reason: '核对原始材料' } })))
  expect(corrections.map((r) => r.status()).sort()).toEqual([201, 409])
  const history = await (await request.get(`${root}/pets/${p.id}/allergies`, { headers: headers() })).json() as Allergy[]
  expect(history).toHaveLength(2); expect(history.find((a) => a.id === old.id)?.allergen).toBe('原始登记')
})

test('真实归档保护：客户与宠物均禁止新增健康/宠物/预约，历史保留', async ({ request }) => {
  const startAt = new Date(Date.now() + 86_400_000); startAt.setUTCMinutes(0, 0, 0)
  const endAt = new Date(startAt.getTime() + 30 * 60_000)
  expect((await request.post('/api/appointment/schedules', { headers: headers(), data: { doctorId, startAt: startAt.toISOString(), endAt: endAt.toISOString(), slotMinutes: 30 } })).status()).toBe(201)
  for (const kind of ['client', 'pet']) {
    const c = await client(request), p = await pet(request, c)
    const original = await request.post(`${root}/pets/${p.id}/allergies`, { headers: headers(), data: { allergen: '归档前记录', severity: 'MILD' } }); expect(original.status()).toBe(201)
    const target = kind === 'client' ? `clients/${c.id}` : `clinic-pets/${p.id}`
    expect((await request.post(`${root}/${target}/archive`, { headers: headers(), data: { version: 0, reason: '实机归档保护测试' } })).status()).toBe(200)
    expect((await request.post(`${root}/pets/${p.id}/allergies`, { headers: headers(), data: { allergen: '不应写入', severity: 'MILD' } })).status()).toBe(409)
    expect((await request.post('/api/appointment/appointments', { headers: headers(), data: { petId: p.id, doctorId, startAt: startAt.toISOString(), endAt: endAt.toISOString() } })).status()).toBe(409)
    expect((await request.get(`${root}/pets/${p.id}/allergies`, { headers: headers() })).status()).toBe(200)
    if (kind === 'client') expect((await request.post(`${root}/clients/${c.id}/pets`, { headers: headers(), data: { details: petDetails(), version: 0, birthDateEstimated: true } })).status()).toBe(409)
    const archived = await request.get(`${root}/clinic-pets?archived=true&ownerId=${c.id}`, { headers: headers() }); expect(archived.status()).toBe(200)
    expect((await archived.json() as ResultPage<unknown>).totalElements).toBe(1)
  }
})

test('真实旧版本编辑冲突在界面保留输入', async ({ page, request }) => {
  const c = await client(request)
  await loginEmployee(page, adminCredentials(), `/clients/${c.id}`)
  await page.getByRole('button', { name: '编辑资料', exact: true }).click()
  const concurrent = await request.put(`${root}/clients/${c.id}`, { headers: headers(), data: { details: contact('其他员工已更新'), version: c.version } }); expect(concurrent.status()).toBe(200)
  await page.getByLabel('客户姓名', { exact: true }).fill('我的未保存输入')
  await page.getByRole('button', { name: '保存档案', exact: true }).click()
  await expect(page.getByTestId('archive-write-error')).toContainText('档案已被其他操作更新')
  await expect(page.getByLabel('客户姓名', { exact: true })).toHaveValue('我的未保存输入')
  await page.screenshot({ path: resolve(pictures, 'client-version-conflict.png'), fullPage: true, animations: 'disabled' })
})

test('真实旧宠物接口兼容：生日标记省略时可建档，编辑不抹掉已有估算属性', async ({ request }) => {
  const account = newEmployee(['OWNER'])
  expect((await request.post('/api/identity/users', { headers: headers(), data: account })).status()).toBe(201)
  const login = await request.post('/api/auth/login', { data: { username: account.username, password: account.password } })
  expect(login.status()).toBe(200)
  const auth = { Authorization: `Bearer ${(await login.json() as { accessToken: string }).accessToken}` }
  const c = await request.put(`${root}/me`, { headers: auth, data: contact() }); expect(c.status()).toBe(200)
  const details = { name: '旧接口宠物', species: '犬', birthDate: '2024-01-01', weightKg: 5 }
  const created = await request.post(`${root}/pets`, { headers: auth, data: details }); expect(created.status()).toBe(201)
  const p = await created.json() as Pet; expect(p.birthDateEstimated).toBe(false)
  const estimated = await request.put(`${root}/clinic-pets/${p.id}`, { headers: headers(), data: { details, birthDateEstimated: true, version: p.version } }); expect(estimated.status()).toBe(200)
  const legacyUpdate = await request.put(`${root}/pets/${p.id}`, { headers: auth, data: { ...details, weightKg: 5.1 } }); expect(legacyUpdate.status()).toBe(200)
  expect((await legacyUpdate.json() as Pet).birthDateEstimated).toBe(true)
})

test('真实预约与归档并发：新预约和健康记录不得在归档后穿透，旧预约可读可取消', async ({ request }) => {
  const start = new Date(Date.now() + 3 * 86_400_000); start.setUTCMinutes(0, 0, 0)
  const scheduleEnd = new Date(start.getTime() + 4 * 3_600_000)
  expect((await request.post('/api/appointment/schedules', { headers: headers(), data: { doctorId, startAt: start.toISOString(), endAt: scheduleEnd.toISOString(), slotMinutes: 30 } })).status()).toBe(201)
  for (let n = 0; n < 4; n++) {
    const c = await client(request), p = await pet(request, c)
    const startAt = new Date(start.getTime() + n * 3_600_000).toISOString(), endAt = new Date(start.getTime() + n * 3_600_000 + 30 * 60_000).toISOString()
    const booking = { petId: p.id, doctorId, startAt, endAt }
    if (n === 0) {
      const existing = await request.post('/api/appointment/appointments', { headers: headers(), data: booking }); expect(existing.status()).toBe(201)
      const id = (await existing.json() as { id: string }).id
      expect((await request.post(`${root}/clients/${c.id}/archive`, { headers: headers(), data: { reason: '保留既有预约历史', version: c.version } })).status()).toBe(200)
      expect((await request.get(`/api/appointment/appointments/${id}`, { headers: headers() })).status()).toBe(200)
      expect((await request.post(`/api/appointment/appointments/${id}/cancel`, { headers: headers() })).status()).toBe(200)
      continue
    }
    const [booked, health, archived] = await Promise.all([
      request.post('/api/appointment/appointments', { headers: headers(), data: booking }),
      request.post(`${root}/pets/${p.id}/allergies`, { headers: headers(), data: { allergen: '并发归档前登记', severity: 'MILD' } }),
      request.post(`${root}/clients/${c.id}/archive`, { headers: headers(), data: { reason: '并发归档验证', version: c.version } }),
    ])
    expect(archived.status()).toBe(200)
    expect([201, 409]).toContain(booked.status()); expect([201, 409]).toContain(health.status())
    const cutoff = (await archived.json() as Client).archivedAt!
    if (health.status() === 201) expect(Date.parse((await health.json() as Allergy).createdAt)).toBeLessThanOrEqual(Date.parse(cutoff))
    expect((await request.post('/api/appointment/appointments', { headers: headers(), data: booking })).status()).toBe(409)
    expect((await request.post(`${root}/pets/${p.id}/allergies`, { headers: headers(), data: { allergen: '归档后', severity: 'MILD' } })).status()).toBe(409)
  }
})

for (const fault of ['http500', 'html502', 'malformed201', 'network-reset', 'timeout', 'offline'] as const) {
  test(`真实登录后的客户提交故障注入：${fault}，结果未知时不重放`, async ({ page, context }) => {
    await loginEmployee(page, adminCredentials(), '/clients')
    await page.getByRole('button', { name: '新建客户', exact: true }).click()
    const name = `故障联调${randomUUID().slice(0, 6)}`
    await page.getByLabel('客户姓名', { exact: true }).fill(name)
    await page.getByLabel('联系电话', { exact: true }).fill('13800138000')
    let posts = 0
    if (fault === 'offline') await context.setOffline(true)
    else await page.route(`**${root}/clients`, async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      posts++
      if (fault === 'network-reset') return route.abort('connectionreset')
      if (fault === 'timeout') return // ApiClient's real 15-second AbortSignal timeout.
      if (fault === 'malformed201') return route.fulfill({ status: 201, contentType: 'application/json', body: '{}' })
      if (fault === 'html502') return route.fulfill({ status: 502, contentType: 'text/html', body: '<h1>Bad Gateway</h1>' })
      return route.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"故障注入"}' })
    })
    await page.getByRole('button', { name: '保存档案', exact: true }).click()
    await expect(page.getByTestId('archive-write-error')).toContainText('操作结果未确认', { timeout: 20_000 })
    await expect(page.getByLabel('客户姓名', { exact: true })).toHaveValue(name)
    await expect(page.getByRole('button', { name: '保存档案', exact: true })).toBeDisabled()
    if (fault !== 'offline') expect(posts).toBe(1)
    await context.setOffline(false)
  })
}

test('真实后端已落库但响应丢失：不重复创建，查询核实唯一记录', async ({ page, request }) => {
  await loginEmployee(page, adminCredentials(), '/clients')
  await page.getByRole('button', { name: '新建客户', exact: true }).click()
  const name = `响应丢失${randomUUID().slice(0, 8)}`
  await page.getByLabel('客户姓名', { exact: true }).fill(name)
  await page.getByLabel('联系电话', { exact: true }).fill('13800138000')
  let writes = 0
  await page.route(`**${root}/clients`, async (route) => {
    if (route.request().method() !== 'POST') return route.continue()
    writes++
    const actual = await route.fetch(); expect(actual.status()).toBe(201)
    await route.abort('connectionreset')
  })
  await page.getByRole('button', { name: '保存档案', exact: true }).click()
  await expect(page.getByTestId('archive-write-error')).toContainText('操作结果未确认')
  await expect(page.getByRole('button', { name: '保存档案', exact: true })).toBeDisabled()
  const lookup = await request.get(`${root}/clients?query=${encodeURIComponent(name)}`, { headers: headers() }); expect(lookup.status()).toBe(200)
  expect((await lookup.json() as ResultPage<Client>).totalElements).toBe(1); expect(writes).toBe(1)
  await page.screenshot({ path: resolve(pictures, 'client-result-unknown.png'), fullPage: true, animations: 'disabled' })
})

test('第三方反向代理实测：建档上游连接拒绝返回502，其余请求通真实后端', async ({ page }) => {
  let refused = 0
  const server = createServer((incoming, outgoing) => {
    const broken = incoming.url === `${root}/clients` && incoming.method === 'POST'
    const upstream = httpRequest({ host: '127.0.0.1', port: broken ? 1 : 5173, path: incoming.url, method: incoming.method, headers: { ...incoming.headers, host: '127.0.0.1:5173' } }, (response) => { outgoing.writeHead(response.statusCode || 502, response.headers); response.pipe(outgoing) })
    upstream.on('error', (e: NodeJS.ErrnoException) => { if (broken && e.code === 'ECONNREFUSED') refused++; outgoing.writeHead(502, { 'Content-Type': 'text/html' }); outgoing.end('Upstream unavailable') })
    incoming.pipe(upstream)
  })
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  const address = server.address(); if (!address || typeof address === 'string') throw new Error('Proxy startup failed')
  try {
    const base = `http://127.0.0.1:${address.port}`
    await page.goto(`${base}/login?redirect=/clients`)
    await page.getByLabel('员工账号', { exact: true }).fill(adminCredentials().username)
    await page.getByLabel('登录密码', { exact: true }).fill(adminCredentials().password)
    await page.getByRole('button', { name: '登录工作台' }).click()
    await page.getByRole('button', { name: '新建客户', exact: true }).click()
    await page.getByLabel('客户姓名', { exact: true }).fill('上游故障验证')
    await page.getByLabel('联系电话', { exact: true }).fill('13800138000')
    await page.getByRole('button', { name: '保存档案', exact: true }).click()
    await expect(page.getByTestId('archive-write-error')).toContainText('操作结果未确认')
    expect(refused).toBe(1)
    await page.screenshot({ path: resolve(pictures, 'client-upstream-unavailable.png'), fullPage: true, animations: 'disabled' })
  } finally { server.closeAllConnections(); await new Promise<void>((resolve) => server.close(() => resolve())) }
})

test('列表读取失败与重新读取恢复，不把错误显示为空档案', async ({ page }) => {
  await page.route(`**${root}/clients?**`, (route) => route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"档案服务暂不可用"}' }))
  await loginEmployee(page, adminCredentials(), '/clients')
  await expect(page.getByRole('heading', { name: '档案读取失败', exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '没有找到档案' })).toHaveCount(0)
  await page.unroute(`**${root}/clients?**`)
  await page.getByRole('button', { name: '重新读取', exact: true }).click()
  await expect(page.locator('.archive-table')).toBeVisible()
})
