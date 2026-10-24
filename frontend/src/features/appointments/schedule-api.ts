import { authSession } from '../../app/services'
import { checked, checkedArray, isSchedule, type Schedule } from './model'
import { isUuid } from '../clients/model'
const root = '/api/appointment/schedules'
export interface ScheduleInput { doctorId: string; startAt: string; endAt: string; slotMinutes: number }
export type ScheduleAction = 'stop-booking' | 'reopen' | 'cancel' | 'delete'
export const actionLabels = { 'stop-booking': '停止放号', reopen: '恢复放号', cancel: '停诊', delete: '删除排班' }
export interface ScheduleOperation { id: string; actorId: string; actorName: string; action: string; reason: string; occurredAt: string }
export const createSchedule = async (body: ScheduleInput) => {
  const s = checked(await authSession.request<unknown>(root, { method: 'POST', body }), isSchedule)
  return checked(s, (v): v is Schedule => isSchedule(v) && v.doctorId === body.doctorId && Date.parse(v.startAt) === Date.parse(body.startAt) && Date.parse(v.endAt) === Date.parse(body.endAt) && v.slotMinutes === body.slotMinutes && v.state === 'OPEN')
}
export async function changeSchedule(s: Schedule, action: ScheduleAction, reason: string) {
  if (action === 'delete') {
    await authSession.request(`${root}/${s.id}?${new URLSearchParams({ version: String(s.version), reason })}`, { method: 'DELETE' })
    return
  }
  const result = checked(await authSession.request<unknown>(`${root}/${s.id}/${action}`, { method: 'POST', body: { version: s.version, reason } }), isSchedule)
  const state = action === 'stop-booking' ? 'BOOKING_CLOSED' : action === 'reopen' ? 'OPEN' : 'CANCELLED'
  return checked(result, (v): v is Schedule => isSchedule(v) && v.id === s.id && v.doctorId === s.doctorId && v.startAt === s.startAt && v.endAt === s.endAt && v.slotMinutes === s.slotMinutes && v.state === state && v.version > s.version)
}
export const scheduleOperations = async (id: string, page: number) => checkedArray(await authSession.request<unknown>(`${root}/${id}/operations?page=${page}`), (v): v is ScheduleOperation => {
  if (!v || typeof v !== 'object') return false
  const o = v as Record<string, unknown>
  return isUuid(o.id) && isUuid(o.actorId) && typeof o.actorName === 'string' && typeof o.action === 'string' && ['CREATE', 'OPEN', 'BOOKING_CLOSED', 'CANCELLED', 'DELETE'].includes(o.action) && typeof o.reason === 'string' && typeof o.occurredAt === 'string' && Number.isFinite(Date.parse(o.occurredAt))
})
