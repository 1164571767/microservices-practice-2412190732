import { authSession } from '../../app/services'
import { ApiError } from '../../shared/api/client'
import { checkedPage } from '../clients/model'
import { checked, isBillDetail, isBillRow, isCharge, isRecordOption, type BillInput, type BillStatus } from './model'
const root = '/api/billing'
export const billingKey = ['billing']
export const bills = async (params: URLSearchParams) => checkedPage(await authSession.request<unknown>(`${root}/workbench/bills?${params}`), isBillRow)
export const records = async (petId: string, page: number) => {
  const value = checkedPage(await authSession.request<unknown>(`${root}/workbench/records?${new URLSearchParams({ petId, page: String(page), size: '5' })}`), isRecordOption)
  if (value.items.some(v => v.record.petId !== petId)) throw new ApiError('INVALID_RESPONSE', '就诊索引与所选宠物不匹配')
  return value
}
export const charges = async (query: string, page: number) => checkedPage(await authSession.request<unknown>(`${root}/charge-items?${new URLSearchParams({ query, page: String(page), size: '10' })}`), isCharge)
export const detail = async (id: string) => checked(await authSession.request<unknown>(`${root}/bills/${id}`), (v): v is import('./model').BillDetail => isBillDetail(v) && v.id === id)
export const createBill = async (body: BillInput, key: string) => checked(await authSession.request<unknown>(`${root}/bills`, { method: 'POST', body, idempotencyKey: key }), (v): v is import('./model').BillDetail => isBillDetail(v) && v.medicalRecordId === body.medicalRecordId && v.prescriptionId === body.prescriptionId && v.items.length === body.items.length && body.items.every(l => v.items.some(i => i.chargeItemId === l.chargeItemId && i.quantity === l.quantity)))
export const act = async (id: string, action: 'pay' | 'refund' | 'void', reason: string, key?: string) => {
  const expected: BillStatus = action === 'pay' ? 'PAID' : action === 'refund' ? 'REFUNDED' : 'VOID'
  return checked(await authSession.request<unknown>(`${root}/bills/${id}/${action}`, { method: 'POST', ...(action === 'pay' ? { idempotencyKey: key } : { body: { reason } }) }), (v): v is import('./model').BillDetail => isBillDetail(v) && v.id === id && v.status === expected)
}
export const saveCharge = async (body: { name: string; amount: number; active: boolean }, id?: string) => checked(await authSession.request<unknown>(`${root}/charge-items${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body }), (v): v is import('./model').ChargeItem => isCharge(v) && (!id || v.id === id) && v.name === body.name && String(v.amount) === String(body.amount) && v.active === (id ? body.active : true))
