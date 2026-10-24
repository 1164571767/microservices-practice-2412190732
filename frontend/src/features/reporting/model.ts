import { ApiError } from '../../shared/api/client'

export interface Period { from: string; to: string }
export interface DailyVisit { date: string; visits: number }
export interface DoctorWorkload { doctorId: string; doctorName: string | null; visits: number }
export interface VisitReport extends Period { totalVisits: number; daily: DailyVisit[]; doctors: DoctorWorkload[] }
export interface DailyRevenue { date: string; paidAmount: number; refundedAmount: number; netAmount: number }
export interface RevenueReport extends Period { currency: 'CNY'; paidAmount: number; refundedAmount: number; netAmount: number; paidCount: number; refundedCount: number; daily: DailyRevenue[] }
export interface DrugLine { drugId: string; drugName: string; specification: string; unit: string; quantity: number; dispenseCount: number }
export interface DrugReport extends Period { totalQuantity: number; totalDispenses: number; items: DrugLine[]; page: number; size: number; totalElements: number; totalPages: number }

const day = 86_400_000
export const clinicToday = (now = new Date()) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(now)
export const validDate = (v: unknown): v is string => typeof v === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v) && Number.isFinite(Date.parse(v)) && new Date(v).toISOString().slice(0, 10) === v
export function periodError(p: Period) {
  if (!validDate(p.from) || !validDate(p.to)) return '请选择有效的开始和结束日期。'
  if (p.from > p.to) return '开始日期不能晚于结束日期。'
  if ((Date.parse(p.to) - Date.parse(p.from)) / day + 1 > 366) return '查询区间含首尾，最多 366 天。'
  return ''
}
export function presetPeriod(preset: 'today' | 'week' | 'month', now = new Date()): Period {
  const to = clinicToday(now)
  return { from: preset === 'today' ? to : preset === 'month' ? `${to.slice(0, 7)}-01` : new Date(Date.parse(to) - 6 * day).toISOString().slice(0, 10), to }
}
export function dates(p: Period) {
  if (periodError(p)) return []
  return Array.from({ length: (Date.parse(p.to) - Date.parse(p.from)) / day + 1 }, (_, i) => new Date(Date.parse(p.from) + i * day).toISOString().slice(0, 10))
}
export function visitDays(v: VisitReport): DailyVisit[] {
  const byDate = new Map(v.daily.map(r => [r.date, r.visits]))
  return dates(v).map(date => ({ date, visits: byDate.get(date) ?? 0 }))
}
export function revenueDays(v: RevenueReport): DailyRevenue[] {
  const byDate = new Map(v.daily.map(r => [r.date, r]))
  return dates(v).map(date => byDate.get(date) ?? { date, paidAmount: 0, refundedAmount: 0, netAmount: 0 })
}
export const money = (value: number) => new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY', minimumFractionDigits: 2 }).format(value)
export const doctorLabel = (d: DoctorWorkload) => d.doctorName || `医生资料缺失（${d.doctorId}）`
type Row = Record<string, unknown>
const row = (v: unknown): v is Row => typeof v === 'object' && v !== null && !Array.isArray(v)
const count = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0
const amount = (v: unknown): v is number => typeof v === 'number' && Number.isFinite(v) && Math.abs(v * 100) <= Number.MAX_SAFE_INTEGER && Math.abs(v * 100 - Math.round(v * 100)) < 0.001
const uuid = (v: unknown): v is string => typeof v === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v)
const text = (v: unknown): v is string => typeof v === 'string' && !!v.trim()
const sameMoney = (a: number, b: number) => Math.abs(a - b) < 0.005
const sum = <T>(values: T[], field: (v: T) => number) => values.reduce((s, v) => s + field(v), 0)
const dailyDates = (values: Row[], p: Period) => values.every((v, i) => validDate(v.date) && v.date >= p.from && v.date <= p.to && (i === 0 || String(values[i - 1]?.date) < v.date))
function base(v: unknown, p: Period): v is Row & Period { return row(v) && v.from === p.from && v.to === p.to && !periodError(p) }
function invalid(): never { throw new ApiError('INVALID_RESPONSE', '报表响应不完整或口径不一致，请重试或联系管理员。') }

export function checkedVisits(v: unknown, p: Period, doctorId = ''): VisitReport {
  if (!base(v, p) || !count(v.totalVisits) || !Array.isArray(v.daily) || !Array.isArray(v.doctors)) return invalid()
  if (!v.daily.every((r: unknown) => row(r) && validDate(r.date) && count(r.visits)) || !dailyDates(v.daily, p)) return invalid()
  if (!v.doctors.every((r: unknown) => row(r) && uuid(r.doctorId) && (r.doctorName === null || text(r.doctorName)) && count(r.visits) && (!doctorId || r.doctorId === doctorId))) return invalid()
  const data = v as unknown as VisitReport
  if (new Set(data.doctors.map(d => d.doctorId)).size !== data.doctors.length || sum(data.daily, d => d.visits) !== data.totalVisits || sum(data.doctors, d => d.visits) !== data.totalVisits) return invalid()
  return data
}
export function checkedRevenue(v: unknown, p: Period): RevenueReport {
  if (!base(v, p) || v.currency !== 'CNY' || !count(v.paidCount) || !count(v.refundedCount) || !amount(v.paidAmount) || v.paidAmount < 0 || !amount(v.refundedAmount) || v.refundedAmount < 0 || !amount(v.netAmount) || !Array.isArray(v.daily)) return invalid()
  if (!v.daily.every((r: unknown) => row(r) && validDate(r.date) && amount(r.paidAmount) && r.paidAmount >= 0 && amount(r.refundedAmount) && r.refundedAmount >= 0 && amount(r.netAmount) && sameMoney(r.netAmount, r.paidAmount - r.refundedAmount)) || !dailyDates(v.daily, p)) return invalid()
  const data = v as unknown as RevenueReport
  if ((!data.paidCount && data.paidAmount !== 0) || (!data.refundedCount && data.refundedAmount !== 0)) return invalid()
  if (!sameMoney(data.netAmount, data.paidAmount - data.refundedAmount) || !sameMoney(sum(data.daily, d => d.paidAmount), data.paidAmount) || !sameMoney(sum(data.daily, d => d.refundedAmount), data.refundedAmount)) return invalid()
  return data
}
export function checkedDrugs(v: unknown, p: Period, page: number, drugId = ''): DrugReport {
  if (!base(v, p) || !count(v.totalQuantity) || !count(v.totalDispenses) || v.page !== page || v.size !== 10 || !count(v.totalElements) || !count(v.totalPages) || v.totalPages !== Math.ceil(v.totalElements / 10) || !Array.isArray(v.items)) return invalid()
  if (v.items.length !== Math.max(0, Math.min(10, v.totalElements - page * 10)) || !v.items.every((r: unknown) => row(r) && uuid(r.drugId) && (!drugId || r.drugId === drugId) && text(r.drugName) && text(r.specification) && text(r.unit) && count(r.quantity) && r.quantity > 0 && count(r.dispenseCount) && r.dispenseCount > 0 && r.dispenseCount <= Number(v.totalDispenses))) return invalid()
  const data = v as unknown as DrugReport
  const keys = data.items.map(r => JSON.stringify([r.drugId, r.drugName, r.specification, r.unit]))
  if (new Set(keys).size !== keys.length || sum(data.items, d => d.quantity) > data.totalQuantity || (!data.totalElements && (data.totalQuantity || data.totalDispenses))) return invalid()
  return data
}
