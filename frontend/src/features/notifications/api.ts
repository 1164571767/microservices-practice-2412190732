import { authSession } from '../../app/services'
import { ApiError } from '../../shared/api/client'
import type { Page } from '../../shared/api/types'

export const notificationKey = ['notifications']
export const notificationTypes = { APPOINTMENT_BOOKED: '预约成功', QUEUE_CALLED: '叫号提醒', BILL_PAID: '支付成功', LOW_STOCK: '低库存预警', REVISIT_REMINDER: '复诊提醒' }
export interface Notification {
  id: string; type: keyof typeof notificationTypes; title: string; body: string
  status: 'UNREAD' | 'READ'; sourceId: string; createdAt: string; readAt: string | null
}
const root = '/api/notification/notifications'
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object'
const uuid = (v: unknown) => typeof v === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(v)
const date = (v: unknown) => typeof v === 'string' && Number.isFinite(Date.parse(v))
export function isNotification(v: unknown): v is Notification {
  return object(v) && uuid(v.id) && uuid(v.sourceId) && typeof v.type === 'string' && Object.hasOwn(notificationTypes, v.type) &&
    typeof v.title === 'string' && typeof v.body === 'string' && date(v.createdAt) &&
    ((v.status === 'UNREAD' && v.readAt === null) || (v.status === 'READ' && date(v.readAt)))
}
function invalid(): never { throw new ApiError('INVALID_RESPONSE', '通知响应不完整，请重新读取核实') }
export async function listNotifications(status: string, page: number, signal?: AbortSignal): Promise<Page<Notification>> {
  const params = new URLSearchParams({ page: String(page), size: '10' })
  if (status) params.set('status', status)
  const v = await authSession.request<unknown>(`${root}?${params}`, { signal })
  if (!object(v) || !Array.isArray(v.items) || !v.items.every(isNotification) || !['page', 'size', 'totalElements', 'totalPages'].every(k => Number.isSafeInteger(v[k]) && Number(v[k]) >= 0)) invalid()
  return v as unknown as Page<Notification>
}
export async function unreadCount(signal?: AbortSignal): Promise<number> {
  const v = await authSession.request<unknown>(`${root}/unread-count`, { signal })
  if (!object(v) || !Number.isSafeInteger(v.unread) || Number(v.unread) < 0) invalid()
  return v.unread as number
}
export async function markRead(id: string) {
  const v = await authSession.request<unknown>(`${root}/${encodeURIComponent(id)}/read`, { method: 'POST' })
  if (!isNotification(v) || v.id !== id || v.status !== 'READ') invalid()
  return v
}
export async function markAllRead() {
  const v = await authSession.request<unknown>(`${root}/read-all`, { method: 'POST' })
  if (!object(v) || !Number.isSafeInteger(v.updated) || Number(v.updated) < 0) invalid()
  return v.updated as number
}
