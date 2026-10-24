import { ApiError } from '../../shared/api/client'
import { isUuid } from '../clients/model'

export type Choice = 'PENDING' | 'NONE' | 'PRESCRIPTION'
export interface Line { drugName: string; specification: string | null; dosage: string; frequency: string; quantity: number; days: number; note: string | null }
export interface Draft { symptoms: string; diagnosis: string; advice: string; prescriptionChoice: Choice; items: Line[] }
export interface Prescription { id: string; medicalRecordId: string; doctorId: string; issuedAt: string; items: (Line & { id: string })[] }
export interface MedicalRecord { id: string; appointmentId: string; petId: string; doctorId: string; symptoms: string; diagnosis: string; advice: string | null; visitedAt: string; createdAt: string; prescriptions: Prescription[] }
export interface Consultation { appointmentId: string; petId: string; doctorId: string; appointmentStatus: 'SCHEDULED' | 'CHECKED_IN' | 'CALLED' | 'CANCELLED'; version: number; canWrite: boolean; draft: Draft | null; prescriptionChoice: Choice; completedAt: string | null; updatedAt: string | null; record: MedicalRecord | null }
export interface Handoff { appointmentId: string; medicalRecordId: string | null; prescriptionId: string | null; bill: { id: string; status: 'UNPAID' | 'PAID' | 'REFUNDED' } | null; dispense: { id: string; dispensedAt: string } | null }
export interface Operation { id: string; actorId: string; action: 'DRAFT_SAVED' | 'SUBMITTED' | 'COMPLETED'; occurredAt: string }
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object'
const instant = (v: unknown): v is string => typeof v === 'string' && /(?:Z|[+-]\d\d:\d\d)$/.test(v) && Number.isFinite(Date.parse(v))
const optionalText = (v: unknown) => v === null || typeof v === 'string'
const choice = (v: unknown): v is Choice => typeof v === 'string' && ['PENDING', 'NONE', 'PRESCRIPTION'].includes(v)
const integer = (v: unknown, min: number, max: number) => typeof v === 'number' && Number.isSafeInteger(v) && v >= min && v <= max
export const emptyDraft = (): Draft => ({ symptoms: '', diagnosis: '', advice: '', prescriptionChoice: 'PENDING', items: [] })
export const emptyLine = (): Line => ({ drugName: '', specification: '', dosage: '', frequency: '', quantity: 1, days: 1, note: '' })
export const draftFingerprint = (d: Draft) => JSON.stringify([d.symptoms, d.diagnosis, d.advice, d.prescriptionChoice, d.items.map(i => [i.drugName, i.specification, i.dosage, i.frequency, i.quantity, i.days, i.note])])
export const isLine = (v: unknown): v is Line => object(v) && ['drugName', 'dosage', 'frequency'].every(k => typeof v[k] === 'string' && (v[k] as string).length <= 100) && optionalText(v.specification) && (v.specification === null || String(v.specification).length <= 100) && optionalText(v.note) && (v.note === null || String(v.note).length <= 255) && integer(v.quantity, 1, 1_000_000) && integer(v.days, 1, 3650)
export const isDraft = (v: unknown): v is Draft => object(v) && ['symptoms', 'diagnosis', 'advice'].every(k => typeof v[k] === 'string' && (v[k] as string).length <= 2000) && choice(v.prescriptionChoice) && Array.isArray(v.items) && v.items.length <= 50 && v.items.every(isLine)
export const isRecord = (v: unknown): v is MedicalRecord => object(v) && ['id', 'appointmentId', 'petId', 'doctorId'].every(k => isUuid(v[k])) && typeof v.symptoms === 'string' && !!v.symptoms.trim() && typeof v.diagnosis === 'string' && !!v.diagnosis.trim() && optionalText(v.advice) && instant(v.visitedAt) && instant(v.createdAt) && Array.isArray(v.prescriptions) && v.prescriptions.length <= 1 && v.prescriptions.every(p => object(p) && isUuid(p.id) && p.medicalRecordId === v.id && p.doctorId === v.doctorId && instant(p.issuedAt) && Array.isArray(p.items) && p.items.length > 0 && p.items.every(i => isLine(i) && object(i) && isUuid(i.id) && !!i.drugName.trim() && !!i.dosage.trim() && !!i.frequency.trim()))
export const isConsultation = (v: unknown): v is Consultation => object(v) && ['appointmentId', 'petId', 'doctorId'].every(k => isUuid(v[k])) && typeof v.appointmentStatus === 'string' && ['SCHEDULED', 'CHECKED_IN', 'CALLED', 'CANCELLED'].includes(v.appointmentStatus) && integer(v.version, 0, Number.MAX_SAFE_INTEGER) && typeof v.canWrite === 'boolean' && choice(v.prescriptionChoice) && (v.draft === null || isDraft(v.draft)) && (v.completedAt === null || instant(v.completedAt)) && (v.updatedAt === null || instant(v.updatedAt)) && (v.record === null || isRecord(v.record) && v.record.appointmentId === v.appointmentId && v.record.petId === v.petId && v.record.doctorId === v.doctorId) && (v.completedAt === null || v.record !== null && v.prescriptionChoice !== 'PENDING' && v.canWrite === false) && (v.canWrite === false || v.appointmentStatus === 'CALLED') && (v.record === null || v.draft === null && (v.prescriptionChoice === 'PRESCRIPTION') === ((v.record as MedicalRecord).prescriptions.length > 0))
export const isHandoff = (v: unknown): v is Handoff => object(v) && isUuid(v.appointmentId) && (v.medicalRecordId === null || isUuid(v.medicalRecordId)) && (v.prescriptionId === null || isUuid(v.prescriptionId) && isUuid(v.medicalRecordId)) && (v.bill === null || object(v.bill) && isUuid(v.bill.id) && isUuid(v.medicalRecordId) && typeof v.bill.status === 'string' && ['UNPAID', 'PAID', 'REFUNDED'].includes(v.bill.status)) && (v.dispense === null || object(v.dispense) && isUuid(v.dispense.id) && isUuid(v.prescriptionId) && instant(v.dispense.dispensedAt))
export const isOperation = (v: unknown): v is Operation => object(v) && isUuid(v.id) && isUuid(v.actorId) && typeof v.action === 'string' && ['DRAFT_SAVED', 'SUBMITTED', 'COMPLETED'].includes(v.action) && instant(v.occurredAt)
export function checked<T>(v: unknown, guard: (v: unknown) => v is T): T { if (!guard(v)) throw new ApiError('INVALID_RESPONSE', '诊疗服务响应异常，请重新读取核实结果'); return v }
export function submissionError(d: Draft): string {
  if (!isDraft(d)) return '草稿格式有误，请核对文本长度和用药数量'
  if (!d.symptoms.trim() || !d.diagnosis.trim()) return '请填写症状和诊断'
  if (d.prescriptionChoice === 'PENDING') return '请明确选择开具处方或无需处方'
  if (d.prescriptionChoice === 'NONE' && d.items.length) return '无需处方时请移除用药明细'
  if (d.prescriptionChoice === 'PRESCRIPTION' && (!d.items.length || d.items.some(i => !i.drugName.trim() || !i.dosage.trim() || !i.frequency.trim()))) return '请补全处方药品、剂量和频次'
  return ''
}
export const dateTime = (v: string) => new Date(v).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
