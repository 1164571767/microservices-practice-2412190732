import { ApiError } from '../../shared/api/client'
import { isUuid } from '../clients/model'

export const statusLabels = { SCHEDULED: '待到院', CHECKED_IN: '候诊中', CALLED: '已叫号', CANCELLED: '已取消' }
export type AppointmentStatus = keyof typeof statusLabels
export interface Appointment {
  id: string; petId: string; doctorId: string; startAt: string; endAt: string
  status: AppointmentStatus; checkedInAt: string | null; calledAt: string | null
}
export interface TimeSlot { startAt: string; endAt: string }
export const scheduleLabels = { OPEN: '放号中', BOOKING_CLOSED: '已停止放号', CANCELLED: '已停诊' }
export interface Schedule extends TimeSlot { id: string; doctorId: string; slotMinutes: number; state: keyof typeof scheduleLabels; version: number }
export interface Patient { petId: string; petName: string; species: string; ownerId: string; ownerName: string; ownerPhone: string; petArchived: boolean; ownerArchived: boolean }
export interface Doctor { id: string; displayName: string }
export interface ReceptionEntry { appointment: Appointment; patient: Patient | null; doctorName: string; consultation?: { appointmentId: string; recordId: string | null; hasDraft: boolean; completedAt: string | null } | null }
export interface QueueEntry { position: number; appointmentId: string; petId: string; doctorId: string; startAt: string; checkedInAt: string; patient: Patient | null }
export interface Booking extends TimeSlot { petId: string; doctorId: string }
export const clinicDate = (value = new Date()) => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(value)
export function bookingDateRange(now = new Date()) {
  const min = clinicDate(now)
  const [year, month, day] = min.split('-').map(Number) as [number, number, number]
  const lastDay = new Date(Date.UTC(year, month + 1, 0)).getUTCDate()
  const max = new Date(Date.UTC(year, month, Math.min(day, lastDay))).toISOString().slice(0, 10)
  return { min, max }
}
export function isBookingDate(date: string, now = new Date()) {
  const { min, max } = bookingDateRange(now)
  const parsed = new Date(`${date}T00:00:00Z`)
  return /^\d{4}-\d{2}-\d{2}$/.test(date) && date >= min && date <= max && Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === date
}
export const timeLabel = (value: string) => new Date(value).toLocaleTimeString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false, hour: '2-digit', minute: '2-digit' })
export const slotLabel = (slot: TimeSlot) => `${timeLabel(slot.startAt)}–${timeLabel(slot.endAt)}`
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object'
const instant = (v: unknown): v is string => typeof v === 'string' && /(?:Z|[+-]\d\d:\d\d)$/.test(v) && Number.isFinite(Date.parse(v))
const nullableInstant = (v: unknown) => v === null || instant(v)
export const isTimeSlot = (v: unknown): v is TimeSlot => object(v) && instant(v.startAt) && instant(v.endAt) && Date.parse(v.endAt) > Date.parse(v.startAt)
export const isAppointment = (v: unknown): v is Appointment => object(v) && isTimeSlot(v) && isUuid(v.id) && isUuid(v.petId) && isUuid(v.doctorId) && typeof v.status === 'string' && Object.hasOwn(statusLabels, v.status) && nullableInstant(v.checkedInAt) && nullableInstant(v.calledAt) && (v.status !== 'CHECKED_IN' || instant(v.checkedInAt)) && (v.status !== 'CALLED' || instant(v.calledAt))
export const isSchedule = (v: unknown): v is Schedule => object(v) && isTimeSlot(v) && isUuid(v.id) && isUuid(v.doctorId) && typeof v.slotMinutes === 'number' && Number.isInteger(v.slotMinutes) && v.slotMinutes > 0 && v.slotMinutes <= 480 && typeof v.state === 'string' && Object.hasOwn(scheduleLabels, v.state) && Number.isSafeInteger(v.version) && Number(v.version) >= 0
export const isDoctor = (v: unknown): v is Doctor => object(v) && isUuid(v.id) && typeof v.displayName === 'string'
export const isPatient = (v: unknown): v is Patient => object(v) && isUuid(v.petId) && isUuid(v.ownerId) && ['petName', 'species', 'ownerName', 'ownerPhone'].every(k => typeof v[k] === 'string') && typeof v.petArchived === 'boolean' && typeof v.ownerArchived === 'boolean'
export const isReceptionEntry = (v: unknown): v is ReceptionEntry => object(v) && isAppointment(v.appointment) && (v.patient === null || isPatient(v.patient) && v.patient.petId === v.appointment.petId) && typeof v.doctorName === 'string' && (v.consultation === undefined || v.consultation === null || object(v.consultation) && v.consultation.appointmentId === v.appointment.id && (v.consultation.recordId === null || isUuid(v.consultation.recordId)) && typeof v.consultation.hasDraft === 'boolean' && nullableInstant(v.consultation.completedAt))
export const isQueueEntry = (v: unknown): v is QueueEntry => object(v) && typeof v.position === 'number' && Number.isInteger(v.position) && v.position > 0 && isUuid(v.appointmentId) && isUuid(v.petId) && isUuid(v.doctorId) && instant(v.startAt) && instant(v.checkedInAt) && (v.patient === null || isPatient(v.patient) && v.patient.petId === v.petId)
export function checked<T>(value: unknown, guard: (v: unknown) => v is T): T {
  if (!guard(value)) throw new ApiError('INVALID_RESPONSE', '预约服务响应异常，请刷新核实结果')
  return value
}
export const checkedArray = <T>(v: unknown, guard: (v: unknown) => v is T): T[] => checked(v, (v): v is T[] => Array.isArray(v) && v.every(guard))
export const isUncertain = (error: unknown) => !(error instanceof ApiError && ['AUTH_FORBIDDEN', 'AUTH_UNAUTHENTICATED', 'VALIDATION_FAILED', 'RESOURCE_NOT_FOUND', 'APPOINTMENT_CONFLICT', 'APPOINTMENT_NOT_AVAILABLE', 'APPOINTMENT_INVALID_STATE', 'OPERATION_NOT_ALLOWED', 'CONCURRENT_MODIFICATION', 'IDEMPOTENCY_CONFLICT'].includes(error.code) && [400, 401, 403, 404, 409].includes(error.status))
