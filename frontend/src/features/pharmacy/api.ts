import { authSession } from '../../app/services'
import { checkedPage } from '../clients/model'
import { checked, isAdjustment, isDispense, isDrug, isInventory, isReview, isTask, type DrugInput, type Drug, type Adjustment, type Review, type Dispense } from './model'
const root = '/api/pharmacy'
export const pharmacyKey = ['pharmacy']
const params = (query: string, page: number) => new URLSearchParams({ query, page: String(page), size: '10' })
export const drugs = async (q: string, page: number) => checkedPage(await authSession.request<unknown>(`${root}/drugs?${params(q, page)}`), isDrug)
export const inventory = async (q: string, page: number, low: boolean) => checkedPage(await authSession.request<unknown>(`${root}/inventory${low ? '/low-stock' : ''}?${params(q, page)}`), isInventory)
export const stock = async (id: string) => checked(await authSession.request<unknown>(`${root}/inventory/${id}`), (v): v is import('./model').Inventory => isInventory(v) && v.drugId === id)
export const adjustments = async (id: string, page: number) => checkedPage(await authSession.request<unknown>(`${root}/inventory/${id}/adjustments?${params('', page)}`), (v): v is Adjustment => isAdjustment(v) && v.drugId === id)
export const adjust = async (id: string, delta: number, reason: string) => checked(await authSession.request<unknown>(`${root}/inventory/${id}/adjustments`, { method: 'POST', body: { delta, reason } }), (v): v is Adjustment => isAdjustment(v) && v.drugId === id && v.delta === delta && v.reason === reason)
export const saveDrug = async (body: DrugInput, id?: string) => {
  const { initialQuantity, active, ...fields } = body
  return checked(await authSession.request<unknown>(`${root}/drugs${id ? `/${id}` : ''}`, { method: id ? 'PUT' : 'POST', body: id ? { ...fields, active } : { ...fields, initialQuantity } }), (v): v is Drug => isDrug(v) && (!id || v.id === id) && v.name === body.name && v.specification === body.specification && v.unit === body.unit && v.lowStockThreshold === body.lowStockThreshold && v.active === (id ? body.active : true))
}
export const tasks = async (q: string, page: number, status: string) => checkedPage(await authSession.request<unknown>(`${root}/workbench/prescriptions?${params(q, page)}&status=${status}`), isTask)
export const review = async (id: string) => checked(await authSession.request<unknown>(`${root}/workbench/prescriptions/${id}`), (v): v is Review => isReview(v) && v.prescription.id === id)
export const dispense = async (id: string, key: string) => checked(await authSession.request<unknown>(`${root}/prescriptions/${id}/dispense`, { method: 'POST', idempotencyKey: key }), (v): v is Dispense => isDispense(v) && v.prescriptionId === id)
export const dispenseResult = async (id: string) => checked(await authSession.request<unknown>(`${root}/workbench/prescriptions/${id}/dispense`), (v): v is Dispense => isDispense(v) && v.prescriptionId === id)
