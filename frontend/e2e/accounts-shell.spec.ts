import { test, expect, type APIRequestContext } from '@playwright/test'
import { randomUUID } from 'node:crypto'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { adminCredentials, loginEmployee, newEmployee } from './support/real-backend'
test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要真实后端和 MySQL；故障注入不替代真实正常功能证据')
const pictures = resolve('.artifacts/screenshots'); mkdirSync(pictures, { recursive: true })
let token = ''
const headers = () => ({ Authorization: `Bearer ${token}` })
test.beforeAll(async ({ request }) => {
  const response = await request.post('/api/auth/login', { data: adminCredentials() }); expect(response.status()).toBe(200)
  token = (await response.json() as { accessToken: string }).accessToken
})
async function employee(request: APIRequestContext, roles = ['STAFF']) {
  const account = newEmployee(roles)
  const response = await request.post('/api/identity/users', { headers: headers(), data: account }); expect(response.status()).toBe(201)
  return { ...account, ...(await response.json() as { id: string }) }
}
async function archive(request: APIRequestContext) {
  const name = `全局检索${randomUUID().slice(0,8)}`
  const client = await request.post('/api/owner/clients', { headers: headers(), data: { name, phone: '13800138000', address: '全局检索测试' } }); expect(client.status()).toBe(201)
  const ownerId = (await client.json() as { id: string }).id
  const pet = await request.post(`/api/owner/clients/${ownerId}/pets`, { headers: headers(), data: { details: { name, species: '猫', birthDate: '2024-01-01', weightKg: 4 }, birthDateEstimated: false, version: 0 } }); expect(pet.status()).toBe(201)
  return { name, ownerId, petId: (await pet.json() as { id: string }).id }
}
test('正常：员工资料、角色、停用启用、重置及操作记录完整闭环', async ({ page, request }) => {
  const account = await employee(request)
  const original = await request.post('/api/auth/login', { data: account }); expect(original.status()).toBe(200)
  const oldToken = (await original.json() as { accessToken: string }).accessToken
  await loginEmployee(page, adminCredentials(), '/settings/users')
  await page.getByLabel('搜索员工', { exact: true }).fill(account.username); await page.getByRole('button', { name: '查询员工', exact: true }).click()
  const row = page.locator('.account-table tbody tr').filter({ hasText: account.username }); await expect(row).toBeVisible()
  await row.getByRole('button', { name: '维护账号', exact: true }).click()
  await page.getByLabel('员工姓名', { exact: true }).fill('账号维护联调员工')
  await page.getByRole('checkbox', { name: '医生', exact: true }).check()
  await page.getByLabel('操作原因', { exact: true }).fill('增加医生职责')
  await page.getByRole('button', { name: '保存账号变更', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(row).toContainText('医生')
  expect((await request.get('/api/identity/me', { headers: { Authorization: `Bearer ${oldToken}` } })).status()).toBe(401)
  await row.getByRole('button', { name: '维护账号', exact: true }).click(); await page.getByLabel('账号状态', { exact: true }).selectOption('DISABLED'); await page.getByLabel('操作原因', { exact: true }).fill('测试停用'); await page.getByRole('button', { name: '保存账号变更', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0); await expect(row).toContainText('停用')
  expect((await request.post('/api/auth/login', { data: account })).status()).toBe(403)
  await row.getByRole('button', { name: '维护账号', exact: true }).click(); await page.getByLabel('账号状态', { exact: true }).selectOption('ACTIVE'); await page.getByLabel('操作原因', { exact: true }).fill('测试恢复'); await page.getByRole('button', { name: '保存账号变更', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0)
  await row.getByRole('button', { name: '重置密码', exact: true }).click()
  const password = randomUUID(); await page.getByLabel('重置新密码', { exact: true }).fill(password); await page.getByLabel('确认重置密码', { exact: true }).fill(password); await page.getByLabel('操作原因', { exact: true }).fill('已核实员工身份'); await page.getByRole('button', { name: '确认重置密码', exact: true }).click(); await expect(page.getByRole('dialog')).toHaveCount(0)
  expect((await request.post('/api/auth/login', { data: { username: account.username, password } })).status()).toBe(200)
  expect((await request.post('/api/auth/login', { data: account })).status()).toBe(401)
  await page.screenshot({ path: resolve(pictures, 'accounts-management.png'), fullPage: true })
  await row.getByRole('button', { name: '操作记录', exact: true }).click(); await expect(page.getByRole('dialog')).toContainText('已核实员工身份'); await expect(page.getByRole('dialog')).not.toContainText(password)
  await page.screenshot({ path: resolve(pictures, 'accounts-audit.png'), fullPage: true })
})
test('正常/异常：员工自助改密、旧密码校验、全部旧会话失效与重新登录', async ({ page, request }) => {
  const account = await employee(request), password = randomUUID()
  const pair = await loginEmployee(page, account, '/settings/password')
  await page.getByLabel('当前密码', { exact: true }).fill('wrong-password'); await page.getByLabel('新密码', { exact: true }).fill(password); await page.getByLabel('确认新密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '修改密码并重新登录', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('当前密码不正确')
  await page.getByLabel('当前密码', { exact: true }).fill(account.password); await page.getByRole('button', { name: '修改密码并重新登录', exact: true }).click(); await expect(page).toHaveURL(/reason=password-changed/)
  expect((await request.get('/api/identity/me', { headers: { Authorization: `Bearer ${pair.accessToken}` } })).status()).toBe(401)
  expect((await request.post('/api/auth/refresh', { data: { refreshToken: pair.refreshToken } })).status()).toBe(401)
  await loginEmployee(page, { username: account.username, password }, '/settings/password')
  await expect(page.getByLabel('当前密码', { exact: true })).toHaveValue('')
  expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)
  await page.screenshot({ path: resolve(pictures, 'password-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 }); await page.screenshot({ path: resolve(pictures, 'password-mobile.png'), fullPage: true }); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.setViewportSize({ width: 320, height: 740 }); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
test('异常：员工不能访问账号管理，药房搜索不请求临床档案', async ({ page, request }) => {
  const account = await employee(request, ['PHARMACY'])
  const pair = await loginEmployee(page, account, '/settings/users'); await expect(page).toHaveURL(/forbidden$/)
  expect((await request.get('/api/identity/employees', { headers: { Authorization: `Bearer ${pair.accessToken}` } })).status()).toBe(403)
  let archiveCalls = 0; page.on('request', r => { if (/\/api\/owner\/(clients|clinic-pets)\?/.test(r.url())) archiveCalls++ })
  await page.getByRole('button', { name: '打开全局搜索', exact: true }).click(); await page.getByLabel('全局搜索关键词', { exact: true }).fill('药房'); await expect(page.getByRole('dialog').getByRole('link', { name: '药房与库存' })).toBeVisible(); expect(archiveCalls).toBe(0)
})
test('正常：顶部全局检索真实客户/宠物，跳转详情与移动端快捷键', async ({ page, request }) => {
  const f = await archive(request); await loginEmployee(page)
  await page.keyboard.press('Control+k'); await page.getByLabel('全局搜索关键词', { exact: true }).fill(f.name)
  await expect(page.getByRole('region', { name: '客户搜索结果' }).getByRole('link')).toContainText(f.name)
  await expect(page.getByRole('region', { name: '宠物搜索结果' }).getByRole('link')).toContainText(f.name)
  await page.screenshot({ path: resolve(pictures, 'global-search.png'), fullPage: true })
  await page.getByRole('region', { name: '宠物搜索结果' }).getByRole('link').click(); await expect(page).toHaveURL(new RegExp(`/pets/${f.petId}$`)); await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.setViewportSize({ width: 390, height: 844 }); await page.getByRole('button', { name: '打开全局搜索', exact: true }).click(); await expect(page.getByLabel('全局搜索关键词', { exact: true })).toBeVisible(); expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})
test('正常：真实低库存事件生成通知、角标、筛选和单条/全部已读', async ({ page, request }) => {
  const account = await employee(request, ['PHARMACY']), name = `通知测试药${randomUUID().slice(0,8)}`
  const created = await request.post('/api/pharmacy/drugs', { headers: headers(), data: { name, specification: '测试规格', unit: '盒', lowStockThreshold: 8, initialQuantity: 2 } }); expect(created.status()).toBe(201)
  const pair = await loginEmployee(page, account, '/notifications')
  const item = page.locator('.notification-item').filter({ hasText: name }); await expect(item).toBeVisible(); await expect(item).toContainText('未读')
  await item.getByRole('button', { name: '标为已读', exact: true }).click(); await expect(item).toContainText('已读于')
  await page.getByRole('button', { name: '未读', exact: true }).click(); await expect(page.locator('.notification-item').filter({ hasText: name })).toHaveCount(0)
  const list = await request.get('/api/notification/notifications', { headers: { Authorization: `Bearer ${pair.accessToken}` } }); expect(list.status()).toBe(200)
  const id = (await list.json() as { items: { id: string }[] }).items[0]!.id
  expect((await request.get(`/api/notification/notifications/${id}`, { headers: headers() })).status()).toBe(403)
  await page.getByRole('button', { name: '全部通知', exact: true }).click(); await page.getByRole('button', { name: '全部标为已读', exact: true }).click(); await expect(page.getByRole('status').filter({ hasText: '标为已读' })).toBeVisible()
  await page.screenshot({ path: resolve(pictures, 'notifications.png'), fullPage: true })
})
test('三方故障：客户上游503时宠物查询仍返回真实结果', async ({ page, request }) => {
  const f = await archive(request); await loginEmployee(page)
  await page.route('**/api/owner/clients?*', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE', message: '上游暂不可用' }) }))
  await page.getByRole('button', { name: '打开全局搜索', exact: true }).click(); await page.getByLabel('全局搜索关键词', { exact: true }).fill(f.name)
  await expect(page.getByRole('region', { name: '客户搜索结果' })).toContainText('客户搜索失败'); await expect(page.getByRole('region', { name: '宠物搜索结果' }).getByRole('link')).toContainText(f.name)
})
test('三方故障：通知服务503不展示假零，恢复后可重新查询', async ({ page }) => {
  await page.route('**/api/notification/notifications**', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE', message: '通知服务暂不可用' }) }))
  await loginEmployee(page, adminCredentials(), '/notifications'); await expect(page.getByText('通知读取失败', { exact: true })).toBeVisible(); await expect(page.getByRole('link', { name: '通知中心，未读数暂不可用' })).toBeVisible()
  await page.unroute('**/api/notification/notifications**'); await page.getByRole('button', { name: '重试通知', exact: true }).click(); await expect(page.getByText('通知读取失败', { exact: true })).toHaveCount(0)
})
for (const fault of ['network', '503', 'committed-response-lost'] as const) {
  test(`三方故障：密码重置${fault}不重复写入，保留可核实路径`, async ({ page, request }) => {
    const account = await employee(request), password = randomUUID(); await loginEmployee(page, adminCredentials(), '/settings/users')
    await page.getByLabel('搜索员工', { exact: true }).fill(account.username); await page.getByRole('button', { name: '查询员工', exact: true }).click(); await page.locator('.account-table tbody tr').filter({ hasText: account.username }).getByRole('button', { name: '重置密码', exact: true }).click()
    await page.getByLabel('重置新密码', { exact: true }).fill(password); await page.getByLabel('确认重置密码', { exact: true }).fill(password); await page.getByLabel('操作原因', { exact: true }).fill('故障测试重置')
    let attempts = 0
    await page.route(`**/api/identity/employees/${account.id}/password-reset`, async route => {
      attempts++
      if (fault === 'committed-response-lost') { expect((await route.fetch()).status()).toBe(204); return route.abort('connectionreset') }
      if (fault === 'network') return route.abort('connectionreset')
      return route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'DEPENDENCY_UNAVAILABLE', message: '上游不可用' }) })
    })
    await page.getByRole('button', { name: '确认重置密码', exact: true }).click(); await expect(page.getByRole('dialog')).toContainText('操作结果未确认'); expect(attempts).toBe(1); await expect(page.getByRole('button', { name: '确认重置密码', exact: true })).toBeDisabled(); await expect(page.getByLabel('重置新密码', { exact: true })).toHaveValue('')
    if (fault === 'committed-response-lost') expect((await request.post('/api/auth/login', { data: { username: account.username, password } })).status()).toBe(200)
  })
}

for (const committed of [false, true]) {
  test(`三方故障：自助改密${committed ? '已提交响应丢失' : '网络断连'}需重新登录核实，不重复提交`, async ({ page, request }) => {
    const account = await employee(request), password = randomUUID(); await loginEmployee(page, account, '/settings/password')
    let attempts = 0
    await page.route('**/api/identity/me/password', async route => { attempts++; if (committed) expect((await route.fetch()).status()).toBe(204); await route.abort('connectionreset') })
    await page.getByLabel('当前密码', { exact: true }).fill(account.password); await page.getByLabel('新密码', { exact: true }).fill(password); await page.getByLabel('确认新密码', { exact: true }).fill(password); await page.getByRole('button', { name: '修改密码并重新登录', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('密码修改结果未确认'); await expect(page.getByRole('button', { name: '修改密码并重新登录', exact: true })).toBeDisabled(); expect(attempts).toBe(1)
    await expect(page.getByLabel('当前密码', { exact: true })).toHaveValue(''); await expect(page.getByLabel('新密码', { exact: true })).toHaveValue('')
    await page.getByRole('button', { name: '前往登录核实', exact: true }).click(); await expect(page).toHaveURL(/reason=password-unconfirmed/)
    const effectivePassword = committed ? password : account.password
    await loginEmployee(page, { username: account.username, password: effectivePassword })
  })
}
