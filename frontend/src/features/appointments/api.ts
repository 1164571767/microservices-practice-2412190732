import { authSession } from '../../app/services'
import { checked, checkedArray, isAppointment, isTimeSlot, isSchedule, isQueueEntry, isDoctor, isReceptionEntry, type Booking, type TimeSlot } from './model'
import { checkedPage } from '../clients/model'
export const appointmentKey = ['appointments']
const root = '/api/appointment'
const get = (path: string) => authSession.request<unknown>(`${root}${path}`)
const post = (path: string, body?: unknown, idempotencyKey?: string) => authSession.request<unknown>(`${root}${path}`, { method: 'POST', body, idempotencyKey })
export const doctors = async () => checkedArray(await get('/doctors'), isDoctor)
export const reception = async (params: URLSearchParams) => checkedPage(await get(`/appointments?${params}`), isReceptionEntry)
export const getAppointment = async (id: string) => checked(await get(`/appointments/${id}`), isAppointment)
export const petAppointments = async (id: string) => checkedArray(await get(`/pets/${id}/appointments`), isAppointment)
export const availability = async (doctorId: string, date: string) => checkedArray(await get(`/availability?${new URLSearchParams({ doctorId, date })}`), isTimeSlot)
export const schedules = async (doctorId: string) => checkedArray(await get(`/schedules?${new URLSearchParams({ doctorId })}`), isSchedule)
export const queue = async (doctorId: string) => checkedArray(await get(`/queue?${new URLSearchParams({ doctorId })}`), isQueueEntry)
export const book = async (body: Booking, key: string) => {
  const result = checked(await post('/appointments', body, key), isAppointment)
  return checked(result, (v): v is typeof result => isAppointment(v) && v.petId === body.petId && v.doctorId === body.doctorId && Date.parse(v.startAt) === Date.parse(body.startAt) && Date.parse(v.endAt) === Date.parse(body.endAt))
}
export const reschedule = async (id: string, body: TimeSlot) => {
  const result = checked(await post(`/appointments/${id}/reschedule`, body), isAppointment)
  return checked(result, (v): v is typeof result => isAppointment(v) && v.id === id && v.status === 'SCHEDULED' && Date.parse(v.startAt) === Date.parse(body.startAt) && Date.parse(v.endAt) === Date.parse(body.endAt))
}
export const checkIn = async (id: string) => {
  const result = checked(await post(`/appointments/${id}/check-in`), isAppointment)
  return checked(result, (v): v is typeof result => isAppointment(v) && v.id === id && v.status === 'CHECKED_IN')
}
export const cancel = async (id: string) => {
  const result = checked(await post(`/appointments/${id}/cancel`), isAppointment)
  return checked(result, (v): v is typeof result => isAppointment(v) && v.id === id && v.status === 'CANCELLED')
}
export const callNext = async (doctorId: string) => {
  const result = checked(await post(`/queue/call?${new URLSearchParams({ doctorId })}`), isAppointment)
  return checked(result, (v): v is typeof result => isAppointment(v) && v.doctorId === doctorId && v.status === 'CALLED')
}
