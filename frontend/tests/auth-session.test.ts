// @vitest-environment node
import { describe, expect, it, vi } from 'vitest'
import { AuthSession } from '../src/features/auth/session'
import { ApiClient, ApiError } from '../src/shared/api/client'
import type { TokenPair, UserProfile } from '../src/shared/api/types'

const profile: UserProfile = { id: 'user-1', username: 'staff', phone: '', displayName: '前台', roles: ['STAFF'], status: 'ACTIVE' }
const tokens = (suffix = '1', expiresIn = 900): TokenPair => ({ accessToken: `access-${suffix}`, refreshToken: `refresh-${suffix}`, tokenType: 'Bearer', expiresIn })
const json = (value: unknown, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
function setup(handler?: (path: string, init?: RequestInit) => Response | Promise<Response>) {
  const fetcher = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path === '/api/auth/login') return json(tokens())
    if (path === '/api/auth/logout') return new Response(null, { status: 204 })
    return handler?.(path, init) ?? json(profile)
  })
  const clock = { value: 1000 }
  const session = new AuthSession(new ApiClient(fetcher as typeof fetch), () => clock.value)
  return { session, fetcher, clock }
}

describe('真实认证契约下的会话协调（单元测试使用受控 HTTP 响应）', () => {
  it('登录后必须读取服务端身份，再确认员工会话', async () => {
    const { session, fetcher } = setup()
    await session.login({ username: 'staff', password: 'test-only' })
    expect(session.user).toEqual(profile)
    expect(fetcher.mock.calls.map(([path]) => path)).toEqual(['/api/auth/login', '/api/identity/me'])
    expect(new Headers(fetcher.mock.calls[1]?.[1]?.headers).get('Authorization')).toBe('Bearer access-1')
  })

  it('主人账号不能进入员工门户，且尝试撤销刚签发的刷新令牌', async () => {
    const { session, fetcher } = setup(() => json({ ...profile, roles: ['OWNER'] }))
    await expect(session.login({ username: 'owner', password: 'test-only' })).rejects.toMatchObject({ code: 'EMPLOYEE_REQUIRED' })
    expect(session.user).toBeNull()
    expect(fetcher.mock.calls.some(([path]) => path === '/api/auth/logout')).toBe(true)
  })

  it('并发 401 只轮换一次，后到的旧令牌失败也复用新令牌', async () => {
    const { session, fetcher } = setup(async (path, init) => {
      if (path === '/api/auth/refresh') return json(tokens('2'))
      if (path === '/api/protected' && new Headers(init?.headers).get('Authorization') === 'Bearer access-1') {
        await Promise.resolve()
        return json({ code: 'AUTH_UNAUTHENTICATED', message: 'expired' }, 401)
      }
      return json(path === '/api/identity/me' ? profile : { ok: true })
    })
    await session.login({ username: 'staff', password: 'test-only' })
    const results = await Promise.all([session.request('/api/protected'), session.request('/api/protected'), session.request('/api/protected')])
    expect(results).toEqual([{ ok: true }, { ok: true }, { ok: true }])
    expect(fetcher.mock.calls.filter(([path]) => path === '/api/auth/refresh')).toHaveLength(1)
  })

  it('本地到期后的并发请求共享刷新，登出使用最新令牌对', async () => {
    const { session, fetcher, clock } = setup((path) => path === '/api/auth/refresh' ? json(tokens('2')) : json(profile))
    await session.login({ username: 'staff', password: 'test-only' })
    clock.value += 900_000
    await Promise.all([session.verify(), session.verify()])
    await session.logout()
    expect(fetcher.mock.calls.filter(([path]) => path === '/api/auth/refresh')).toHaveLength(1)
    const logout = fetcher.mock.calls.find(([path]) => path === '/api/auth/logout')
    expect(logout?.[1]?.body).toBe(JSON.stringify({ refreshToken: 'refresh-2' }))
    expect(new Headers(logout?.[1]?.headers).get('Authorization')).toBe('Bearer access-2')
    expect(session.user).toBeNull()
  })

  it('刷新失败只尝试一次，丢弃会话并通知过期', async () => {
    const { session, fetcher, clock } = setup((path) => path === '/api/auth/refresh'
      ? json({ code: 'AUTH_INVALID_CREDENTIALS', message: 'expired' }, 401) : json(profile))
    const listener = vi.fn()
    session.subscribe(listener)
    await session.login({ username: 'staff', password: 'test-only' })
    clock.value += 900_000
    await expect(session.verify()).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })
    await expect(session.verify()).rejects.toMatchObject({ status: 401 })
    expect(session.user).toBeNull()
    expect(listener).toHaveBeenLastCalledWith(null, 'expired')
    expect(fetcher.mock.calls.filter(([path]) => path === '/api/auth/refresh')).toHaveLength(1)
  })

  it('退出请求失败仍清除本机状态，同时向调用方保留未确认错误', async () => {
    const { session, fetcher } = setup()
    await session.login({ username: 'staff', password: 'test-only' })
    fetcher.mockImplementationOnce(async () => { throw new Error('offline') })
    await expect(session.logout()).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    expect(session.user).toBeNull()
  })

  it('登出后返回的旧身份请求不能恢复已清除会话', async () => {
    let resolve: (response: Response) => void = () => undefined
    const pending = new Promise<Response>((done) => { resolve = done })
    const { session, fetcher } = setup()
    await session.login({ username: 'staff', password: 'test-only' })
    fetcher.mockImplementationOnce(() => pending)
    const result = session.verify().catch((error: unknown) => error)
    await session.logout()
    resolve(json(profile))
    expect(await result).toBeInstanceOf(ApiError)
    expect(session.user).toBeNull()
  })

  it('未授权的写请求不会被自动重放', async () => {
    const { session, fetcher } = setup((path) => path === '/api/write'
      ? json({ code: 'AUTH_UNAUTHENTICATED', message: 'expired' }, 401) : json(profile))
    await session.login({ username: 'staff', password: 'test-only' })
    await expect(session.request('/api/write', { method: 'POST', body: { delta: 1 } })).rejects.toMatchObject({ status: 401 })
    expect(fetcher.mock.calls.filter(([path]) => path === '/api/write')).toHaveLength(1)
    expect(fetcher.mock.calls.filter(([path]) => path === '/api/auth/refresh')).toHaveLength(0)
    expect(session.user).toBeNull()
  })

  it('刷新后的请求仍然 401 时结束会话，不产生刷新循环', async () => {
    const { session, fetcher } = setup((path) => {
      if (path === '/api/auth/refresh') return json(tokens('2'))
      return path === '/api/protected' ? json({ code: 'AUTH_UNAUTHENTICATED', message: 'expired' }, 401) : json(profile)
    })
    await session.login({ username: 'staff', password: 'test-only' })
    await expect(session.request('/api/protected')).rejects.toMatchObject({ status: 401 })
    expect(fetcher.mock.calls.filter(([path]) => path === '/api/auth/refresh')).toHaveLength(1)
    expect(session.user).toBeNull()
  })
})

describe('API 客户端契约', () => {
  it('读取统一错误体，处理无响应体的 204', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(json({ code: 'AUTH_FORBIDDEN', message: '无权执行该操作' }, 403)).mockResolvedValueOnce(new Response(null, { status: 204 }))
    const client = new ApiClient(fetcher)
    await expect(client.request('/api/test')).rejects.toMatchObject({ code: 'AUTH_FORBIDDEN', status: 403 })
    await expect(client.request('/api/test', { method: 'POST' })).resolves.toBeUndefined()
  })

  it('代理 HTML 错误归一化，网络失败不自动重试', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response('<html>proxy error</html>', { status: 502 })).mockRejectedValueOnce(new Error('offline'))
    const client = new ApiClient(fetcher)
    await expect(client.request('/api/test')).rejects.toMatchObject({ code: 'INVALID_RESPONSE', status: 502 })
    await expect(client.request('/api/test')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    expect(fetcher).toHaveBeenCalledTimes(2)
  })

  it('拒绝把访问令牌发送到任意外部地址', async () => {
    const fetcher = vi.fn()
    await expect(new ApiClient(fetcher).request('https://example.com/api/test', { accessToken: 'private' })).rejects.toMatchObject({ code: 'INVALID_API_PATH' })
    expect(fetcher).not.toHaveBeenCalled()
  })
})
