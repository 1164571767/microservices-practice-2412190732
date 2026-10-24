import { authSession } from '../../app/services'
import { checkedDrugs, checkedRevenue, checkedVisits, type Period } from './model'

export const reportingKey = ['reporting']
export async function visits(p: Period, doctorId = '', signal?: AbortSignal) {
  const query = new URLSearchParams({ ...p, ...(doctorId ? { doctorId } : {}) })
  return checkedVisits(await authSession.request<unknown>(`/api/reporting/visits?${query}`, { signal }), p, doctorId)
}
export async function revenue(p: Period, signal?: AbortSignal) {
  return checkedRevenue(await authSession.request<unknown>(`/api/reporting/revenue?${new URLSearchParams({ ...p })}`, { signal }), p)
}
export async function consumption(p: Period, page: number, drugId = '', signal?: AbortSignal) {
  const query = new URLSearchParams({ ...p, page: String(page), size: '10', ...(drugId ? { drugId } : {}) })
  return checkedDrugs(await authSession.request<unknown>(`/api/reporting/drug-consumption?${query}`, { signal }), p, page, drugId)
}
