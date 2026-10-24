import { ApiError } from '../../shared/api/client'
import { isUuid } from '../clients/model'
import { isPatient, type Patient } from '../appointments/model'
export const billLabels = { UNPAID: '待收款', PAID: '已收款', REFUNDED: '已退款', VOID: '已作废' }
export type BillStatus = keyof typeof billLabels
export type Money = number | string
export interface ChargeItem { id: string; name: string; amount: Money; active: boolean; createdAt: string; updatedAt: string }
export interface Bill { id: string; petId: string; medicalRecordId: string; prescriptionId: string | null; totalAmount: Money; currency: 'CNY'; status: BillStatus; createdBy: string; createdAt: string; updatedAt: string }
export interface BillLine { id: string; chargeItemId: string; itemName: string; unitPrice: Money; quantity: number; amount: Money }
export interface Payment { id: string; billId: string; amount: Money; currency: 'CNY'; method: 'MOCK'; status: 'SUCCEEDED' | 'REFUNDED'; paidBy: string; paidAt: string; refundedBy: string | null; refundedAt: string | null; refundReason: string | null }
export interface Operation { id: string; billId: string; action: 'CREATED' | 'PAID' | 'REFUNDED' | 'VOIDED'; fromStatus: BillStatus | null; toStatus: BillStatus; reason: string | null; operatorId: string; createdAt: string }
export interface BillDetail extends Bill { items: BillLine[]; payment: Payment | null; operations: Operation[] }
export interface BillRow { bill: Bill; patient: Patient | null }
export interface RecordOption { record: { recordId: string; petId: string; doctorId: string; visitedAt: string; prescriptionId: string | null }; doctorName: string; existingBillId: string | null; existingBillStatus: BillStatus | null }
export interface BillInput { medicalRecordId: string; prescriptionId: string | null; items: { chargeItemId: string; quantity: number }[] }
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object'
const instant = (v: unknown): v is string => typeof v === 'string' && /(?:Z|[+-]\d\d:\d\d)$/.test(v) && Number.isFinite(Date.parse(v))
const status = (v: unknown): v is BillStatus => typeof v === 'string' && Object.hasOwn(billLabels, v)
const nullableUuid = (v: unknown) => v === null || isUuid(v)
export function cents(v: unknown): bigint | undefined {
  if (typeof v !== 'number' && typeof v !== 'string') return
  const match = /^(\d{1,17})(?:\.(\d{1,2}))?$/.exec(String(v))
  if (!match) return
  const value = BigInt(match[1]!) * 100n + BigInt((match[2] ?? '').padEnd(2, '0'))
  if (typeof v === 'number' && value > BigInt(Number.MAX_SAFE_INTEGER)) return
  return value
}
export function money(value: Money) {
  const amount = cents(value)
  return amount === undefined ? '金额不可用' : `¥${(amount / 100n).toLocaleString('zh-CN')}.${String(amount % 100n).padStart(2, '0')}`
}
export const isCharge = (v: unknown): v is ChargeItem => object(v) && isUuid(v.id) && typeof v.name === 'string' && cents(v.amount) !== undefined && typeof v.active === 'boolean' && instant(v.createdAt) && instant(v.updatedAt)
export const isBill = (v: unknown): v is Bill => object(v) && isUuid(v.id) && isUuid(v.petId) && isUuid(v.medicalRecordId) && nullableUuid(v.prescriptionId) && cents(v.totalAmount) !== undefined && v.currency === 'CNY' && status(v.status) && isUuid(v.createdBy) && instant(v.createdAt) && instant(v.updatedAt)
export const isBillRow = (v: unknown): v is BillRow => object(v) && isBill(v.bill) && (v.patient === null || isPatient(v.patient) && v.patient.petId === v.bill.petId)
export const isRecordOption = (v: unknown): v is RecordOption => object(v) && object(v.record) && isUuid(v.record.recordId) && isUuid(v.record.petId) && isUuid(v.record.doctorId) && instant(v.record.visitedAt) && nullableUuid(v.record.prescriptionId) && typeof v.doctorName === 'string' && nullableUuid(v.existingBillId) && (v.existingBillStatus === null || status(v.existingBillStatus)) && ((v.existingBillId === null) === (v.existingBillStatus === null))
export function isBillDetail(v: unknown): v is BillDetail {
  if (!isBill(v) || !object(v)) return false
  const lines = v.items
  if (!Array.isArray(lines) || !lines.length || !lines.every(l => object(l) && isUuid(l.id) && isUuid(l.chargeItemId) && typeof l.itemName === 'string' && Number.isSafeInteger(l.quantity) && Number(l.quantity) > 0 && cents(l.unitPrice) !== undefined && cents(l.amount) !== undefined && cents(l.unitPrice)! * BigInt(Number(l.quantity)) === cents(l.amount))) return false
  if (new Set(lines.map(l => l.chargeItemId)).size !== lines.length || lines.reduce((sum, l) => sum + cents(l.amount)!, 0n) !== cents(v.totalAmount)) return false
  const p = v.payment
  if (v.status === 'PAID' || v.status === 'REFUNDED') {
    if (!object(p) || !isUuid(p.id) || p.billId !== v.id || cents(p.amount) !== cents(v.totalAmount) || p.currency !== 'CNY' || p.method !== 'MOCK' || p.status !== (v.status === 'PAID' ? 'SUCCEEDED' : 'REFUNDED') || !isUuid(p.paidBy) || !instant(p.paidAt)) return false
    if (v.status === 'REFUNDED' && (!isUuid(p.refundedBy) || !instant(p.refundedAt) || typeof p.refundReason !== 'string' || !p.refundReason.trim())) return false
  } else if (p !== null) return false
  if (!Array.isArray(v.operations) || !v.operations.length) return false
  const transitions = { CREATED: [null, 'UNPAID'], PAID: ['UNPAID', 'PAID'], REFUNDED: ['PAID', 'REFUNDED'], VOIDED: ['UNPAID', 'VOID'] }
  return v.operations.every(o => object(o) && isUuid(o.id) && o.billId === v.id && isUuid(o.operatorId) && instant(o.createdAt) && typeof o.action === 'string' && Object.hasOwn(transitions, o.action) && transitions[o.action as keyof typeof transitions][0] === o.fromStatus && transitions[o.action as keyof typeof transitions][1] === o.toStatus && (o.reason === null || typeof o.reason === 'string')) && v.operations.at(-1)?.toStatus === v.status
}
export function checked<T>(v: unknown, guard: (v: unknown) => v is T): T {
  if (!guard(v)) throw new ApiError('INVALID_RESPONSE', '收费服务响应异常，请先核实账单结果')
  return v
}
export const isUncertain = (e: unknown) => !(e instanceof ApiError && [400, 401, 403, 404, 409].includes(e.status) && ['VALIDATION_FAILED', 'AUTH_FORBIDDEN', 'AUTH_UNAUTHENTICATED', 'RESOURCE_NOT_FOUND', 'OPERATION_NOT_ALLOWED', 'PAYMENT_ALREADY_PAID', 'PAYMENT_INVALID_STATE', 'IDEMPOTENCY_CONFLICT', 'CHARGE_ITEM_ALREADY_EXISTS', 'CONCURRENT_MODIFICATION'].includes(e.code))
