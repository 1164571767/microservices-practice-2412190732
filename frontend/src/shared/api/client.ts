export class ApiError extends Error {
  constructor(public readonly code: string, message: string, public readonly status = 0) {
    super(message)
    this.name = 'ApiError'
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  accessToken?: string
  signal?: AbortSignal
  idempotencyKey?: string
}

export class ApiClient {
  constructor(private readonly fetcher: typeof fetch = (...args) => fetch(...args)) {}

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    if (!path.startsWith('/api/') || path.includes('://')) {
      throw new ApiError('INVALID_API_PATH', '请求地址不正确')
    }
    const headers = new Headers({ Accept: 'application/json' })
    if (options.body !== undefined) headers.set('Content-Type', 'application/json')
    if (options.accessToken) headers.set('Authorization', `Bearer ${options.accessToken}`)
    if (options.idempotencyKey) headers.set('Idempotency-Key', options.idempotencyKey)
    const signal = options.signal
      ? AbortSignal.any([options.signal, AbortSignal.timeout(15_000)])
      : AbortSignal.timeout(15_000)
    let response: Response
    try {
      response = await this.fetcher(path, {
        method: options.method ?? 'GET', headers, signal,
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        credentials: 'omit', cache: 'no-store', redirect: 'error',
      })
    } catch (error) {
      if (options.signal?.aborted) throw error
      if (signal.aborted && signal.reason instanceof DOMException && signal.reason.name === 'TimeoutError') {
        throw new ApiError('REQUEST_TIMEOUT', '请求超时，操作结果尚未确认，请先核实后再处理')
      }
      throw new ApiError('NETWORK_ERROR', '暂时无法连接服务，请检查网络后重试')
    }
    if (response.status === 204) return undefined as T
    let payload: unknown
    try { payload = await response.json() } catch {
      throw new ApiError('INVALID_RESPONSE', '服务暂时不可用，请稍后重试', response.status)
    }
    if (!response.ok) {
      const error = payload as { code?: unknown; message?: unknown } | null
      throw new ApiError(
        typeof error?.code === 'string' ? error.code : 'HTTP_ERROR',
        typeof error?.message === 'string' ? error.message : '请求失败，请稍后重试',
        response.status,
      )
    }
    return payload as T
  }
}

export const errorMessage = (error: unknown): string =>
  error instanceof ApiError ? error.message : '操作未完成，请稍后重试'
