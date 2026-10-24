import { authSession } from '../../app/services'
import { checkedPage } from '../clients/model'
import { checked, draftFingerprint, isConsultation, isHandoff, isRecord, isOperation, type Draft, type Consultation } from './model'
const root = '/api/medical'
const path = (id: string) => `${root}/consultations/${id}`
const result = (v: unknown, id: string) => checked(v, (v): v is Consultation => isConsultation(v) && v.appointmentId === id)
export const consultation = async (id: string) => result(await authSession.request<unknown>(path(id)), id)
export const saveDraft = async (id: string, version: number, draft: Draft) => {
  const value = result(await authSession.request<unknown>(`${path(id)}/draft`, { method: 'PUT', body: { version, draft } }), id)
  return checked(value, (v): v is Consultation => isConsultation(v) && v.version === version + 1 && v.record === null && v.draft !== null && draftFingerprint(v.draft) === draftFingerprint(draft))
}
export const submit = async (id: string, version: number) => {
  const value = result(await authSession.request<unknown>(`${path(id)}/submit`, { method: 'POST', body: { version } }), id)
  return checked(value, (v): v is Consultation => isConsultation(v) && v.version === version + 1 && v.record !== null && v.draft === null)
}
export const complete = async (id: string, version: number, noPrescription: boolean) => {
  const value = result(await authSession.request<unknown>(`${path(id)}/complete`, { method: 'POST', body: { version, noPrescription } }), id)
  return checked(value, (v): v is Consultation => isConsultation(v) && v.version === version + 1 && v.completedAt !== null && !v.canWrite)
}
export const handoff = async (id: string) => checked(await authSession.request<unknown>(`${path(id)}/handoff`), (v): v is import('./model').Handoff => isHandoff(v) && v.appointmentId === id)
export const history = async (petId: string, page: number) => checkedPage(await authSession.request<unknown>(`${root}/pets/${petId}/records?page=${page}&size=5`), (v): v is import('./model').MedicalRecord => isRecord(v) && v.petId === petId)
export const operations = async (id: string, page: number) => checked(await authSession.request<unknown>(`${path(id)}/operations?page=${page}`), (v): v is import('./model').Operation[] => Array.isArray(v) && v.every(isOperation))
