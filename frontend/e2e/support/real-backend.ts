import { expect, type Page } from '@playwright/test'
import { existsSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { randomBytes } from 'node:crypto'

const path = resolve('../monolith/.env')
const text = existsSync(path) ? readFileSync(path, 'utf8') : ''
const value = (key: string) => text.match(new RegExp(`^${key}=(.*)$`, 'm'))?.[1]?.trim().replace(/^(['"])(.*)\1$/, '$2')
export const adminCredentials = () => {
  const username = process.env.E2E_USERNAME || value('BOOTSTRAP_ADMIN_USERNAME')
  const password = process.env.E2E_PASSWORD || value('BOOTSTRAP_ADMIN_PASSWORD')
  if (!username || !password) throw new Error('需要通过 E2E_USERNAME/E2E_PASSWORD 或已有 monolith/.env 提供真实测试管理员；不使用 Mock 代替。')
  return { username, password }
}
export function newEmployee(roles = ['STAFF']) {
  const suffix = `${Date.now()}${randomBytes(2).toString('hex')}`
  return {
    username: `e2e.${suffix}`, displayName: '联调测试员工',
    phone: `99${Date.now()}${Math.floor(Math.random() * 1000).toString().padStart(3, '0')}`,
    password: randomBytes(16).toString('hex'), roles,
  }
}
export async function loginEmployee(page: Page, credentials = adminCredentials(), target = '/') {
  await page.goto(`/login?redirect=${encodeURIComponent(target)}`)
  await page.getByLabel('员工账号', { exact: true }).fill(credentials.username)
  await page.getByLabel('登录密码', { exact: true }).fill(credentials.password)
  const response = page.waitForResponse((r) => r.url().endsWith('/api/auth/login') && r.request().method() === 'POST')
  await page.getByRole('button', { name: '登录工作台' }).click()
  const result = await response
  expect(result.status(), '真实测试员工必须能够登录').toBe(200)
  const pair = await result.json() as { accessToken: string; refreshToken: string }
  await expect(page.getByRole('button', { name: '退出登录', exact: true })).toBeVisible()
  return pair
}
export async function fillEmployee(page: Page, data: ReturnType<typeof newEmployee>) {
  await page.getByLabel('登录账号', { exact: true }).fill(data.username)
  await page.getByLabel('员工姓名', { exact: true }).fill(data.displayName)
  await page.getByLabel('手机号', { exact: true }).fill(data.phone)
  await page.getByLabel('初始密码', { exact: true }).fill(data.password)
  await page.getByLabel('确认初始密码', { exact: true }).fill(data.password)
  const labels: Record<string, string> = { STAFF: '前台', DOCTOR: '医生', PHARMACY: '药房', ADMIN: '管理员' }
  for (const role of data.roles) await page.getByRole('checkbox', { name: new RegExp(`^${labels[role]}`) }).check()
}
