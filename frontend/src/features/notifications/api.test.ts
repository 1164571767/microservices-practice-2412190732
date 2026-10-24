import { afterEach, describe, expect, it, vi } from 'vitest'
import { authSession } from '../../app/services'
import { ApiError } from '../../shared/api/client'
import { isNotification, listNotifications, markAllRead, markRead, unreadCount } from './api'
const id = '12345678-1234-4123-8123-123456789012'
const notification = () => ({ id, sourceId: id, type: 'LOW_STOCK', title: '库存预警', body: '测试药品库存不足', status: 'UNREAD', createdAt: '2026-10-24T01:00:00Z', readAt: null })
afterEach(() => vi.restoreAllMocks())
describe('通知真实契约边界', () => {
  it('接受合法通知、状态过滤和分页', async () => {
    const request = vi.spyOn(authSession, 'request').mockResolvedValue({ items: [notification()], page: 0, size: 10, totalElements: 1, totalPages: 1 })
    expect((await listNotifications('UNREAD', 0)).items).toHaveLength(1)
    expect(request).toHaveBeenCalledWith(expect.stringContaining('status=UNREAD'), expect.anything())
  })
  it('不将非法日期、已读状态不一致或缺失正文当作通知', () => {
    expect(isNotification(notification())).toBe(true)
    for (const values of [{ createdAt: 'bad-date' }, { status: 'READ' }, { body: null }, { id: 'bad-id' }]) expect(isNotification({ ...notification(), ...values })).toBe(false)
  })
  it('不将缺失/负数未读数展示为零', async () => {
    const request = vi.spyOn(authSession, 'request').mockResolvedValue({ unread: -1 })
    await expect(unreadCount()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
    request.mockResolvedValue({ unread: 5 }); await expect(unreadCount()).resolves.toBe(5)
  })
  it('核对单条已读响应的ID与状态，并核对全部已读结果', async () => {
    const request = vi.spyOn(authSession, 'request').mockResolvedValue({ ...notification(), status: 'READ', readAt: '2026-10-24T02:00:00Z' })
    await expect(markRead(id)).resolves.toMatchObject({ status: 'READ' })
    request.mockResolvedValue(notification()); await expect(markRead(id)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
    request.mockResolvedValue({ updated: 0 }); await expect(markAllRead()).resolves.toBe(0)
    request.mockResolvedValue({}); await expect(markAllRead()).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  for (const [code, status] of [['NETWORK_ERROR',0],['REQUEST_TIMEOUT',0],['UPSTREAM_UNAVAILABLE',503],['AUTH_FORBIDDEN',403]] as const) {
    it(`${code} 不自动重放已读写请求`, async () => {
      const request = vi.spyOn(authSession, 'request').mockRejectedValue(new ApiError(code, '故障注入', status))
      await expect(markRead(id)).rejects.toMatchObject({ code }); expect(request).toHaveBeenCalledTimes(1)
    })
  }
})
