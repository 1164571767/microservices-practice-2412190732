import { ApiError } from '../../shared/api/client'
import { isUuid } from '../clients/model'
import { isPatient, type Patient } from '../appointments/model'

export interface Drug { id: string; name: string; specification: string; unit: string; lowStockThreshold: number; active: boolean; createdAt: string; updatedAt: string }
export interface Inventory { drugId: string; drugName: string; specification: string; unit: string; quantity: number; lowStockThreshold: number; lowStock: boolean; updatedAt: string }
export interface Adjustment { id: string; drugId: string; delta: number; quantityBefore: number; quantityAfter: number; reason: string; operatorId: string; createdAt: string }
export interface Task { prescriptionId: string; petId: string; issuedAt: string; patient: Patient | null; doctorName: string; dispensed: boolean }
export interface Prescription { id: string; medicalRecordId: string; petId: string; doctorId: string; issuedAt: string; items: { id: string; drugName: string; specification: string | null; dosage: string; frequency: string; quantity: number; days: number; note: string | null }[] }
export interface Dispense { id: string; prescriptionId: string; medicalRecordId: string; petId: string; doctorId: string; dispensedBy: string; dispensedAt: string; items: { drugId: string; drugName: string; specification: string; unit: string; quantity: number; stockAfter: number }[] }
export interface Review { prescription: Prescription; patient: Patient; doctorName: string; allergies: { allergen: string; severity: 'MILD' | 'MODERATE' | 'SEVERE'; note: string | null }[]; dispense: Dispense | null }
export interface DrugInput { name: string; specification: string; unit: string; lowStockThreshold: number; initialQuantity: number; active: boolean }
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object'
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim()
const optional = (v: unknown) => v === null || typeof v === 'string'
const instant = (v: unknown) => typeof v === 'string' && /(?:Z|[+-]\d\d:\d\d)$/.test(v) && Number.isFinite(Date.parse(v))
export const nonnegative = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0 && v <= 2147483647
const positive = (v: unknown) => nonnegative(v) && v > 0
export const isDrug = (v: unknown): v is Drug => object(v) && isUuid(v.id) && ['name', 'specification', 'unit'].every(k => text(v[k])) && nonnegative(v.lowStockThreshold) && typeof v.active === 'boolean' && instant(v.createdAt) && instant(v.updatedAt)
export const isInventory = (v: unknown): v is Inventory => object(v) && isUuid(v.drugId) && ['drugName', 'specification', 'unit'].every(k => text(v[k])) && nonnegative(v.quantity) && nonnegative(v.lowStockThreshold) && v.lowStock === (v.quantity < v.lowStockThreshold) && instant(v.updatedAt)
export const isAdjustment = (v: unknown): v is Adjustment => object(v) && isUuid(v.id) && isUuid(v.drugId) && Number.isSafeInteger(v.delta) && v.delta !== 0 && nonnegative(v.quantityBefore) && nonnegative(v.quantityAfter) && v.quantityBefore + Number(v.delta) === v.quantityAfter && text(v.reason) && isUuid(v.operatorId) && instant(v.createdAt)
export const isTask = (v: unknown): v is Task => object(v) && isUuid(v.prescriptionId) && isUuid(v.petId) && instant(v.issuedAt) && text(v.doctorName) && typeof v.dispensed === 'boolean' && (v.patient === null || isPatient(v.patient) && v.patient.petId === v.petId)
export const isDispense = (v: unknown): v is Dispense => object(v) && ['id', 'prescriptionId', 'medicalRecordId', 'petId', 'doctorId', 'dispensedBy'].every(k => isUuid(v[k])) && instant(v.dispensedAt) && Array.isArray(v.items) && v.items.length > 0 && v.items.every(i => object(i) && isUuid(i.drugId) && text(i.drugName) && text(i.specification) && text(i.unit) && positive(i.quantity) && nonnegative(i.stockAfter)) && new Set(v.items.map(i => i.drugId)).size === v.items.length
export const isPrescription = (v: unknown): v is Prescription => object(v) && ['id', 'medicalRecordId', 'petId', 'doctorId'].every(k => isUuid(v[k])) && instant(v.issuedAt) && Array.isArray(v.items) && v.items.length > 0 && v.items.every(i => object(i) && isUuid(i.id) && text(i.drugName) && optional(i.specification) && text(i.dosage) && text(i.frequency) && positive(i.quantity) && positive(i.days) && optional(i.note))
export const isReview = (v: unknown): v is Review => object(v) && isPrescription(v.prescription) && isPatient(v.patient) && v.patient.petId === v.prescription.petId && text(v.doctorName) && Array.isArray(v.allergies) && v.allergies.every(a => object(a) && text(a.allergen) && ['MILD', 'MODERATE', 'SEVERE'].includes(String(a.severity)) && optional(a.note)) && (v.dispense === null || isDispense(v.dispense) && v.dispense.prescriptionId === v.prescription.id && v.dispense.petId === v.prescription.petId && v.dispense.medicalRecordId === v.prescription.medicalRecordId && v.dispense.doctorId === v.prescription.doctorId)
export function checked<T>(v: unknown, guard: (v: unknown) => v is T): T {
  if (!guard(v)) throw new ApiError('INVALID_RESPONSE', '药房服务响应异常，请先核实操作结果')
  return v
}
export const isUncertain = (e: unknown) => !(e instanceof ApiError && [400, 401, 403, 404, 409].includes(e.status) && ['VALIDATION_FAILED', 'AUTH_FORBIDDEN', 'AUTH_UNAUTHENTICATED', 'RESOURCE_NOT_FOUND', 'DRUG_ALREADY_EXISTS', 'INSUFFICIENT_STOCK', 'INVENTORY_INVALID_STATE', 'OPERATION_NOT_ALLOWED', 'IDEMPOTENCY_CONFLICT', 'CONCURRENT_MODIFICATION'].includes(e.code))
