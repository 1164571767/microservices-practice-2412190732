import { test, expect, type Route } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { createServer, request as httpRequest } from 'node:http'
import { adminCredentials, fillEmployee, loginEmployee, newEmployee } from './support/real-backend'

const createPath = '/settings/users/new'
const apiPath = '/api/identity/users'
const pictures = resolve('.artifacts/screenshots')
mkdirSync(pictures, { recursive: true })
test.skip(process.env.E2E_REAL_BACKEND !== '1', '需要显式开启真实后端验证；故障注入也建立在真实管理员登录基础上')

test.describe('员工开通：真实后端正常与异常', () => {
  let token = ''
  const staff = newEmployee()
  test.beforeAll(async ({ request }) => {
    const login = await request.post('/api/auth/login', { data: adminCredentials() })
    expect(login.status(), '真实管理员登录').toBe(200)
    token = (await login.json() as { accessToken: string }).accessToken
    const response = await request.post(apiPath, { headers: { Authorization: `Bearer ${token}` }, data: staff })
    expect(response.status(), '建立用于权限验证的真实 STAFF 测试账号').toBe(201)
  })

  test('管理员入口、校验和移动布局，非法输入不发送创建请求', async ({ page }) => {
    await loginEmployee(page)
    await page.getByRole('link', { name: '基础设置，员工账号管理' }).click()
    await page.getByRole('link', { name: '开通员工账号', exact: true }).click()
    await expect(page).toHaveURL(new RegExp(`${createPath}$`))
    await page.screenshot({ path: resolve(pictures, 'employee-create-desktop.png'), fullPage: true })
    let posts = 0
    page.on('request', (r) => { if (r.url().endsWith(apiPath) && r.method() === 'POST') posts++ })
    await page.getByRole('button', { name: '开通账号', exact: true }).click()
    await expect(page.getByText('请输入登录账号', { exact: true })).toBeVisible()
    await expect(page.getByText('请至少选择一个员工角色', { exact: true })).toBeVisible()
    await page.getByLabel('登录账号', { exact: true }).fill('不合法账号')
    await page.getByLabel('员工姓名', { exact: true }).fill(' ')
    await page.getByLabel('手机号', { exact: true }).fill('abc')
    await page.getByLabel('初始密码', { exact: true }).fill('short')
    await page.getByLabel('确认初始密码', { exact: true }).fill('different')
    await page.getByRole('button', { name: '开通账号', exact: true }).click()
    await expect(page.getByText('账号须为 3～50 位字母、数字或 _ . -')).toBeVisible()
    await expect(page.getByText('请输入 6～20 位数字，可带国家码 +')).toBeVisible()
    await expect(page.getByText('两次输入的密码不一致')).toBeVisible()
    expect(posts).toBe(0)
    await page.setViewportSize({ width: 390, height: 844 })
    expect(await page.locator('.skip-link').evaluate((link) => link.matches(':focus-visible'))).toBe(false)
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: resolve(pictures, 'employee-create-mobile.png'), fullPage: true })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  })

  test('真实创建多角色员工、快速双击只发一笔、新账号可实际登录', async ({ page }) => {
    const employee = newEmployee(['DOCTOR', 'PHARMACY'])
    await loginEmployee(page, adminCredentials(), createPath)
    await fillEmployee(page, employee)
    let posts = 0
    page.on('request', (r) => { if (r.url().endsWith(apiPath) && r.method() === 'POST') posts++ })
    const response = page.waitForResponse((r) => r.url().endsWith(apiPath) && r.request().method() === 'POST')
    await page.getByRole('button', { name: '开通账号', exact: true }).dblclick()
    const result = await response
    expect(result.status()).toBe(201)
    await expect(page.getByTestId('create-success')).toContainText(employee.username)
    expect(posts).toBe(1)
    const body = await result.json() as Record<string, unknown>
    expect('password' in body || 'passwordHash' in body).toBe(false)
    expect((await page.locator('body').innerText()).includes(employee.password)).toBe(false)
    expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)
    await page.screenshot({ path: resolve(pictures, 'employee-create-success.png'), fullPage: true })
    await page.getByRole('button', { name: '继续开通账号' }).click()
    await expect(page.getByLabel('初始密码', { exact: true })).toHaveValue('')
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    await expect(page).toHaveURL(/\/login/)
    await loginEmployee(page, employee)
    await expect(page.getByText('联调测试员工', { exact: true }).first()).toBeVisible()
    await expect(page.getByRole('link', { name: '基础设置，员工账号管理' })).toHaveCount(0)
    await expect(page.getByText('医生 / 药房', { exact: true })).toBeVisible()
  })

  for (const field of ['username', 'phone'] as const) {
    test(`真实重复${field === 'username' ? '账号' : '手机号'}返回409并保留输入`, async ({ page }) => {
      const employee = newEmployee()
      employee[field] = staff[field]
      await loginEmployee(page, adminCredentials(), createPath)
      await fillEmployee(page, employee)
      const response = page.waitForResponse((r) => r.url().endsWith(apiPath) && r.request().method() === 'POST')
      await page.getByRole('button', { name: '开通账号', exact: true }).click()
      expect((await response).status()).toBe(409)
      await expect(page.getByTestId('create-error')).toContainText('账号或手机号已被使用')
      await expect(page.getByLabel('登录账号', { exact: true })).toHaveValue(employee.username)
      await expect(page.getByRole('button', { name: '开通账号', exact: true })).toBeEnabled()
    })
  }

  test('真实STAFF无入口且深链接被拒绝，直接调用后端创建仍返回403', async ({ page }) => {
    const pair = await loginEmployee(page, staff, createPath)
    await expect(page).toHaveURL(/\/forbidden$/)
    await expect(page.getByText('你没有访问此页面的权限')).toBeVisible()
    await expect(page.getByRole('link', { name: '基础设置，员工账号管理' })).toHaveCount(0)
    const response = await page.request.post(apiPath, { headers: { Authorization: `Bearer ${pair.accessToken}` }, data: newEmployee() })
    expect(response.status()).toBe(403)
    await page.screenshot({ path: resolve(pictures, 'employee-forbidden.png'), fullPage: true })
  })

  test('绕过前端的真实无效参数由后端返回400，匿名创建返回401', async ({ request }) => {
    const invalid = await request.post(apiPath, { headers: { Authorization: `Bearer ${token}` }, data: { ...newEmployee(), roles: [], password: '' } })
    expect(invalid.status()).toBe(400)
    expect((await invalid.json() as { code: string }).code).toBe('VALIDATION_FAILED')
    const anonymous = await request.post(apiPath, { data: newEmployee() })
    expect(anonymous.status()).toBe(401)
  })
})

test.describe('员工开通：实机故障注入（不代表外部服务真实宕机）', () => {
  test('实际反向代理上游连接被拒绝产生502，真实认证可用且创建结果不被误判', async ({ browser }) => {
    let attempts = 0
    // Real HTTP proxy: ordinary requests reach Vite/the backend; only the
    // creation upstream is disconnected. No manufactured HTTP response via page.route.
    const proxy = createServer((incoming, outgoing) => {
      const creating = incoming.method === 'POST' && incoming.url === apiPath
      if (creating) attempts++
      const target = creating ? 'http://127.0.0.1:1' : (process.env.E2E_BASE_URL || 'http://127.0.0.1:5173')
      const forward = httpRequest(new URL(incoming.url || '/', target), {
        method: incoming.method, headers: { ...incoming.headers, host: new URL(target).host },
      }, (response) => {
        outgoing.writeHead(response.statusCode || 502, response.headers)
        response.pipe(outgoing)
      })
      forward.on('error', () => {
        outgoing.writeHead(502, { 'Content-Type': 'application/json' })
        outgoing.end(JSON.stringify({ code: 'UPSTREAM_UNAVAILABLE', message: 'Upstream connection refused' }))
      })
      incoming.pipe(forward)
    })
    await new Promise<void>((done) => proxy.listen(0, '127.0.0.1', done))
    const address = proxy.address()
    if (!address || typeof address === 'string') throw new Error('本地故障代理未能启动')
    const context = await browser.newContext({ baseURL: `http://127.0.0.1:${address.port}`, viewport: { width: 1440, height: 1000 } })
    try {
      const page = await context.newPage()
      await loginEmployee(page, adminCredentials(), createPath)
      await fillEmployee(page, newEmployee())
      const failed = page.waitForResponse((r) => r.url().endsWith(apiPath) && r.request().method() === 'POST')
      await page.getByRole('button', { name: '开通账号', exact: true }).click()
      expect((await failed).status()).toBe(502)
      await expect(page.getByTestId('create-error')).toContainText('开通结果未确认')
      await expect(page.getByRole('button', { name: '请先核实开通结果' })).toBeDisabled()
      expect(attempts).toBe(1)
      await page.evaluate(() => window.scrollTo(0, 0))
      await page.screenshot({ path: resolve(pictures, 'employee-proxy-upstream-down.png'), fullPage: true })
    } finally {
      await context.close()
      proxy.closeAllConnections()
      await new Promise<void>((done, reject) => proxy.close((error) => error ? reject(error) : done()))
    }
  })

  for (const fault of ['500', '503', '502-html', 'malformed-201', 'connection-reset', 'timeout'] as const) {
    test(`真实登录后注入${fault}，保留资料且不自动重试未知结果`, async ({ page }) => {
      await loginEmployee(page, adminCredentials(), createPath)
      const employee = newEmployee()
      await fillEmployee(page, employee)
      let attempts = 0
      let release: (() => void) | undefined
      await page.route(`**${apiPath}`, async (route: Route) => {
        if (route.request().method() !== 'POST') return route.continue()
        attempts++
        if (fault === 'connection-reset') return route.abort('connectionreset')
        if (fault === 'timeout') { await new Promise<void>((resolve) => { release = resolve }); return route.abort().catch(() => undefined) }
        if (fault === '502-html') return route.fulfill({ status: 502, contentType: 'text/html', body: '<html>Upstream unavailable</html>' })
        if (fault === 'malformed-201') return route.fulfill({ status: 201, contentType: 'application/json', body: '{}' })
        return route.fulfill({ status: Number(fault), contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'injected upstream failure' }) })
      })
      await page.getByRole('button', { name: '开通账号', exact: true }).click()
      await expect(page.getByTestId('create-error')).toContainText('开通结果未确认', { timeout: 20_000 })
      release?.()
      await expect(page.getByLabel('登录账号', { exact: true })).toHaveValue(employee.username)
      expect(await page.getByLabel('初始密码', { exact: true }).inputValue() === employee.password).toBe(true)
      await expect(page.getByRole('button', { name: '请先核实开通结果' })).toBeDisabled()
      expect(attempts).toBe(1)
      if (fault === '503') {
        await page.evaluate(() => window.scrollTo(0, 0))
        await page.screenshot({ path: resolve(pictures, 'employee-upstream-unconfirmed.png'), fullPage: true })
      }
      await page.getByRole('button', { name: '已核实，重新填写' }).click()
      await expect(page.getByLabel('登录账号', { exact: true })).toHaveValue('')
      await expect(page.getByLabel('初始密码', { exact: true })).toHaveValue('')
    })
  }

  test('实机断网禁止发送，恢复连接后可继续操作', async ({ page, context }) => {
    await loginEmployee(page, adminCredentials(), createPath)
    const employee = newEmployee()
    await fillEmployee(page, employee)
    let attempts = 0
    page.on('request', (r) => { if (r.url().endsWith(apiPath) && r.method() === 'POST') attempts++ })
    await context.setOffline(true)
    await expect(page.getByText('网络已断开，恢复连接后才能提交')).toBeVisible()
    await expect(page.getByRole('button', { name: '开通账号', exact: true })).toBeDisabled()
    await context.setOffline(false)
    await expect(page.getByRole('button', { name: '开通账号', exact: true })).toBeEnabled()
    await expect(page.getByLabel('登录账号', { exact: true })).toHaveValue(employee.username)
    expect(attempts).toBe(0)
  })

  test('真实后端已提交但响应丢失，页面不误报失败或重试，账号确实可查', async ({ page }) => {
    const pair = await loginEmployee(page, adminCredentials(), createPath)
    const employee = newEmployee()
    await fillEmployee(page, employee)
    let id = ''
    let attempts = 0
    await page.route(`**${apiPath}`, async (route) => {
      if (route.request().method() !== 'POST') return route.continue()
      attempts++
      const response = await route.fetch()
      expect(response.status()).toBe(201)
      id = (await response.json() as { id: string }).id
      await route.abort('connectionreset')
    })
    await page.getByRole('button', { name: '开通账号', exact: true }).click()
    await expect(page.getByTestId('create-error')).toContainText('开通结果未确认')
    expect(attempts).toBe(1)
    const exists = await page.request.get(`${apiPath}/${id}`, { headers: { Authorization: `Bearer ${pair.accessToken}` } })
    expect(exists.status()).toBe(200)
    expect((await exists.json() as { username: string }).username).toBe(employee.username)
  })

  test('登录过期的创建响应不自动重放，回到真实登录入口', async ({ page }) => {
    await loginEmployee(page, adminCredentials(), createPath)
    await fillEmployee(page, newEmployee())
    let attempts = 0
    await page.route(`**${apiPath}`, async (route) => {
      attempts++
      await route.fulfill({ status: 401, contentType: 'application/json', body: JSON.stringify({ code: 'AUTH_UNAUTHENTICATED', message: 'expired' }) })
    })
    await page.getByRole('button', { name: '开通账号', exact: true }).click()
    await expect(page).toHaveURL(/reason=expired/)
    expect(attempts).toBe(1)
  })
})
