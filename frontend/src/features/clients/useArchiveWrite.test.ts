import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../shared/api/client'
vi.mock('../../app/services', () => ({ queryClient: { invalidateQueries: vi.fn(() => Promise.resolve()) } }))
vi.mock('./api', () => ({ clientKey: ['clinic-archives'] }))
import { useArchiveWrite } from './useArchiveWrite'
describe('档案提交结果边界', () => {
  it('不重放结果未知的写入，锁定重复点击并保留说明', async () => {
    const writer = useArchiveWrite(), action = vi.fn().mockRejectedValue(new ApiError('NETWORK_ERROR', '网络错误'))
    await writer.run(action, vi.fn()); await writer.run(action, vi.fn())
    expect(action).toHaveBeenCalledTimes(1)
    expect(writer.uncertain.value).toBe(true)
    expect(writer.message.value).toContain('核实')
  })
  it('版本冲突是确定失败，允许核对后重新操作', async () => {
    const writer = useArchiveWrite()
    await writer.run(() => Promise.reject(new ApiError('CONCURRENT_MODIFICATION', '档案已变化', 409)), vi.fn())
    expect(writer.uncertain.value).toBe(false)
    const success = vi.fn()
    await writer.run(() => Promise.resolve('saved'), success)
    expect(success).toHaveBeenCalledWith('saved')
  })
  it('在请求尚未返回时阻止第二次提交', async () => {
    const writer = useArchiveWrite()
    let finish: (v: string) => void = () => undefined
    const action = vi.fn(() => new Promise<string>((resolve) => { finish = resolve }))
    const first = writer.run(action, vi.fn())
    await writer.run(action, vi.fn())
    expect(action).toHaveBeenCalledTimes(1)
    finish('saved'); await first
    expect(writer.busy.value).toBe(false)
  })
})
