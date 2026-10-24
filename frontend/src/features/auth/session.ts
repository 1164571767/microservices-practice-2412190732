import { ApiClient, ApiError, type RequestOptions } from '../../shared/api/client'
import { employeeRoles, type LoginRequest, type TokenPair, type UserProfile } from '../../shared/api/types'

export type SessionReason = 'expired' | 'logout' | 'login' | 'updated'
type Listener = (user: UserProfile | null, reason: SessionReason) => void

/** Tokens live only in this instance; never persisted or placed in Pinia/devtools. */
export class AuthSession {
  #tokens: TokenPair | null = null
  #expiresAt = 0
  #user: UserProfile | null = null
  #epoch = 0
  #refreshFlight: Promise<void> | null = null
  #loginFlight = false
  #loggingOut = false
  #listeners = new Set<Listener>()

  constructor(private readonly client = new ApiClient(), private readonly now = Date.now) {}
  get user() { return this.#user }
  forget() { this.#clear('logout') }

  subscribe(listener: Listener) {
    this.#listeners.add(listener)
    return () => this.#listeners.delete(listener)
  }

  #emit(reason: SessionReason) {
    this.#listeners.forEach((listener) => listener(this.#user, reason))
  }

  #accept(tokens: TokenPair) {
    if (!tokens.accessToken || !tokens.refreshToken || tokens.tokenType !== 'Bearer' ||
      !Number.isFinite(tokens.expiresIn) || tokens.expiresIn <= 0) {
      throw new ApiError('INVALID_RESPONSE', '登录响应不完整，请联系管理员')
    }
    this.#tokens = tokens
    this.#expiresAt = this.now() + tokens.expiresIn * 1000
  }

  #clear(reason: SessionReason) {
    this.#epoch++
    this.#tokens = null
    this.#user = null
    this.#expiresAt = 0
    this.#emit(reason)
  }

  async login(credentials: LoginRequest) {
    if (this.#loginFlight || this.#loggingOut) {
      throw new ApiError('OPERATION_PENDING', '正在处理登录状态，请稍候')
    }
    this.#loginFlight = true
    const epoch = ++this.#epoch
    try {
      const tokens = await this.client.request<TokenPair>('/api/auth/login', {
        method: 'POST', body: credentials,
      })
      if (epoch !== this.#epoch) throw new ApiError('SESSION_CHANGED', '登录状态已变化，请重新登录')
      this.#accept(tokens)
      const user = await this.client.request<UserProfile>('/api/identity/me', { accessToken: tokens.accessToken })
      if (epoch !== this.#epoch) throw new ApiError('SESSION_CHANGED', '登录状态已变化，请重新登录')
      if (user.status !== 'ACTIVE' || !user.roles.some((role) => employeeRoles.includes(role))) {
        // An OWNER account is real, but this application is the employee portal.
        try {
          await this.client.request<void>('/api/auth/logout', {
            method: 'POST', accessToken: tokens.accessToken, body: { refreshToken: tokens.refreshToken },
          })
        } catch { /* The local session will still be discarded. */ }
        throw new ApiError('EMPLOYEE_REQUIRED', '此入口仅供医院员工使用，请联系管理员确认账号权限')
      }
      this.#user = user
      this.#emit('login')
      return user
    } catch (error) {
      if (epoch === this.#epoch) this.#clear('logout')
      throw error
    } finally { this.#loginFlight = false }
  }

  async #refresh() {
    if (this.#refreshFlight) return this.#refreshFlight
    if (!this.#tokens) throw new ApiError('SESSION_EXPIRED', '登录已失效，请重新登录', 401)
    const epoch = this.#epoch
    const refreshToken = this.#tokens.refreshToken
    const flight = (async () => {
      try {
        // Never retry a refresh: reusing a consumed token revokes the token family.
        const tokens = await this.client.request<TokenPair>('/api/auth/refresh', {
          method: 'POST', body: { refreshToken },
        })
        if (epoch !== this.#epoch) throw new ApiError('SESSION_CHANGED', '登录状态已变化', 401)
        this.#accept(tokens)
      } catch {
        if (epoch === this.#epoch) this.#clear('expired')
        throw new ApiError('SESSION_EXPIRED', '登录已失效，请重新登录', 401)
      }
    })()
    this.#refreshFlight = flight
    try { await flight } finally {
      if (this.#refreshFlight === flight) this.#refreshFlight = null
    }
  }

  async request<T>(path: string, options: Omit<RequestOptions, 'accessToken'> = {}): Promise<T> {
    if (!this.#tokens || this.#loggingOut) throw new ApiError('SESSION_EXPIRED', '请重新登录', 401)
    const epoch = this.#epoch
    if (this.now() >= this.#expiresAt - 1000) await this.#refresh()
    const usedToken = this.#tokens?.accessToken
    try {
      const result = await this.client.request<T>(path, { ...options, accessToken: usedToken })
      if (epoch !== this.#epoch) throw new ApiError('SESSION_CHANGED', '登录状态已变化', 401)
      return result
    } catch (error) {
      if (!(error instanceof ApiError) || error.status !== 401 || epoch !== this.#epoch) throw error
      // Unsafe writes have an unknown outcome; only GET can be replayed here.
      if ((options.method ?? 'GET') !== 'GET') {
        this.#clear('expired')
        throw error
      }
      if (usedToken === this.#tokens?.accessToken) await this.#refresh()
      try {
        const result = await this.client.request<T>(path, { ...options, accessToken: this.#tokens?.accessToken })
        if (epoch !== this.#epoch) throw new ApiError('SESSION_CHANGED', '登录状态已变化', 401)
        return result
      } catch (retryError) {
        if (retryError instanceof ApiError && retryError.status === 401 && epoch === this.#epoch) this.#clear('expired')
        throw retryError
      }
    }
  }

  async verify() {
    const user = await this.request<UserProfile>('/api/identity/me')
    if (user.status !== 'ACTIVE' || !user.roles.some((role) => employeeRoles.includes(role))) {
      this.#clear('expired')
      throw new ApiError('SESSION_EXPIRED', '账号权限已变化，请重新登录', 401)
    }
    this.#user = user
    this.#emit('updated')
    return user
  }

  async logout() {
    if (this.#loggingOut) return
    this.#loggingOut = true
    try {
      if (this.#refreshFlight) await this.#refreshFlight
      if (!this.#tokens) return
      if (this.now() >= this.#expiresAt - 1000) await this.#refresh()
      const tokens = this.#tokens
      if (tokens) await this.client.request<void>('/api/auth/logout', {
        method: 'POST', accessToken: tokens.accessToken, body: { refreshToken: tokens.refreshToken },
      })
    } finally {
      this.#clear('logout')
      this.#loggingOut = false
    }
  }
}
