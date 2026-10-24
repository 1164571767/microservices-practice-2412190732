import { test, expect, type Page } from '@playwright/test'
import { existsSync, mkdirSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

const screenshots = resolve('.artifacts/screenshots')
mkdirSync(screenshots, { recursive: true })
const localEnvPath = resolve('../monolith/.env')
const localConfig = existsSync(localEnvPath) ? readFileSync(localEnvPath, 'utf8') : ''
function config(key: string) {
  const value = process.env[key] || localConfig.match(new RegExp(`^${key}=(.*)$`, 'm'))?.[1]?.trim()
  return value?.replace(/^(['"])(.*)\1$/, '$2')
}
const username = process.env.E2E_USERNAME || config('BOOTSTRAP_ADMIN_USERNAME')
const password = process.env.E2E_PASSWORD || config('BOOTSTRAP_ADMIN_PASSWORD')

async function login(page: Page) {
  if (!username || !password) throw new Error('Real E2E requires test credentials via E2E_USERNAME/E2E_PASSWORD or monolith/.env; credentials are never substituted with mock data.')
  await page.goto('/login')
  await page.getByLabel('员工账号', { exact: true }).fill(username)
  await page.getByLabel('登录密码', { exact: true }).fill(password)
  const response = page.waitForResponse((r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '登录工作台' }).click()
  const result = await response
  expect(result.status(), 'Real backend login must succeed').toBe(200)
  const pair = await result.json() as { accessToken: string; refreshToken: string; expiresIn: number }
  await expect(page.getByRole('heading', { name: '医院工作台', exact: true })).toBeVisible()
  return pair
}

test('登录页桌面和移动布局、必填校验、受保护路由', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/')
  await expect(page).toHaveURL(/\/login/)
  await page.screenshot({ path: resolve(screenshots, 'login-desktop.png'), fullPage: true })
  await page.getByRole('button', { name: '登录工作台' }).click()
  await expect(page.getByText('请输入员工账号', { exact: true })).toBeVisible()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: resolve(screenshots, 'login-mobile.png'), fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  expect(errors).toEqual([])
})

test.describe('真实后端认证联调（无网络拦截或 Mock）', () => {
  test.skip(process.env.E2E_REAL_BACKEND !== '1', 'Set E2E_REAL_BACKEND=1 to explicitly run against the real test backend')

  test('错误凭据由真实后端拒绝', async ({ page }) => {
    await page.goto('/login')
    await page.getByLabel('员工账号', { exact: true }).fill(`missing.${Date.now()}`)
    await page.getByLabel('登录密码', { exact: true }).fill('invalid-test-password')
    const response = page.waitForResponse((r) => r.url().endsWith('/api/auth/login'))
    await page.getByRole('button', { name: '登录工作台' }).click()
    expect((await response).status()).toBe(401)
    await expect(page.getByTestId('login-error')).toContainText('用户名或密码错误')
  })

  test('真实登录、身份查询、工作台响应式截图、真实退出与返回保护', async ({ page }) => {
    const errors: string[] = []
    page.on('pageerror', (error) => errors.push(error.message))
    await login(page)
    await page.screenshot({ path: resolve(screenshots, 'workspace-desktop.png'), fullPage: true })
    await expect(page.getByText('账号信息来自当前登录身份')).toBeVisible()
    expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0)
    await page.getByRole('button', { name: '收起导航' }).click()
    await page.screenshot({ path: resolve(screenshots, 'workspace-collapsed.png'), fullPage: true })
    await page.getByRole('button', { name: '展开导航' }).click()
    await page.setViewportSize({ width: 390, height: 844 })
    await page.screenshot({ path: resolve(screenshots, 'workspace-mobile.png'), fullPage: true })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.getByRole('button', { name: '打开导航' }).click()
    await expect(page.getByRole('complementary', { name: '主导航' })).toBeVisible()
    await page.getByRole('button', { name: '关闭导航', exact: true }).last().click()
    const logout = page.waitForResponse((r) => r.url().endsWith('/api/auth/logout'))
    await page.getByRole('button', { name: '退出登录', exact: true }).click()
    const loggedOut = await logout
    expect(loggedOut.status()).toBe(204)
    await expect(page.getByText('已安全退出，感谢今天的用心照护。')).toBeVisible()
    // Prove server-side revocation rather than just a client redirect.
    const refreshToken = (loggedOut.request().postDataJSON() as { refreshToken: string }).refreshToken
    const revoked = await page.request.post('/api/auth/refresh', { data: { refreshToken } })
    expect(revoked.status()).toBe(401)
    await page.goto('/')
    await expect(page).toHaveURL(/\/login/)
    expect(errors).toEqual([])
  })

  test('刷新页面结束内存会话', async ({ page }) => {
    await login(page)
    await page.reload()
    await expect(page).toHaveURL(/\/login/)
  })

  test('真实短时令牌自然到期：刷新轮换及刷新令牌失效后回到登录页', async ({ page }) => {
    test.setTimeout(90_000)
    const original = await login(page)
    test.skip(original.expiresIn > 15, 'Natural expiry test needs a test backend with access TTL <=15s and refresh TTL 45s; normal environments are not changed automatically')
    await page.waitForTimeout((original.expiresIn + 1) * 1000)
    const refresh = page.waitForResponse((r) => r.url().endsWith('/api/auth/refresh'))
    await page.getByRole('button', { name: '刷新身份', exact: true }).click()
    const rotated = await refresh
    expect(rotated.status()).toBe(200)
    const next = await rotated.json() as { accessToken: string; refreshToken: string }
    expect(next.accessToken === original.accessToken).toBe(false)
    expect(next.refreshToken === original.refreshToken).toBe(false)
    await expect(page.getByRole('heading', { name: '医院工作台', exact: true })).toBeVisible()
    // Test-only runtime TTL configured at backend startup; no token tampering.
    await page.waitForTimeout(47_000)
    const expired = page.waitForResponse((r) => r.url().endsWith('/api/auth/refresh'))
    await page.getByRole('button', { name: '刷新身份', exact: true }).click()
    expect((await expired).status()).toBe(401)
    await expect(page).toHaveURL(/reason=expired/)
    await expect(page.getByText('登录已失效，请重新登录后继续。')).toBeVisible()
    await page.screenshot({ path: resolve(screenshots, 'session-expired.png'), fullPage: true })
  })
})
