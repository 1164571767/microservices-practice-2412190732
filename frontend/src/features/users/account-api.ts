import { authSession } from '../../app/services'
import { ApiError } from '../../shared/api/client'
import { employeeRoles, type Role, type UserProfile, type Page } from '../../shared/api/types'
export interface Employee extends UserProfile { version: number; createdAt: string }
export interface AccountAudit { id: string; actorId: string; action: string; reason: string; beforeSummary: string | null; afterSummary: string | null; createdAt: string }
export interface EmployeeUpdate { version: number; displayName: string; phone: string; roles: Role[]; status: Employee['status']; reason: string }
export const accountKey = ['employee-accounts']
const root = '/api/identity'
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object'
const uuid = (v: unknown) => typeof v === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(v)
function invalid(): never { throw new ApiError('INVALID_RESPONSE', '账号响应不完整，请重新读取核实结果') }
export const isEmployee = (v: unknown): v is Employee => object(v) && uuid(v.id) && ['username','displayName','phone','createdAt'].every(k => typeof v[k] === 'string') && Number.isSafeInteger(v.version) && Number(v.version) >= 0 && ['ACTIVE','DISABLED'].includes(String(v.status)) && Array.isArray(v.roles) && v.roles.length > 0 && v.roles.every(r => typeof r === 'string' && [...employeeRoles,'OWNER'].includes(r as Role)) && v.roles.some(r => employeeRoles.includes(r as Role))
export function accountPage<T>(v: unknown, guard: (v: unknown) => v is T): Page<T> {
  if (!object(v) || !Array.isArray(v.items) || !v.items.every(guard) || !['page','size','totalElements','totalPages'].every(k => Number.isSafeInteger(v[k]) && Number(v[k]) >= 0)) invalid()
  return v as unknown as Page<T>
}
export async function employees(params: URLSearchParams, signal?: AbortSignal) { return accountPage(await authSession.request(`${root}/employees?${params}`, { signal }), isEmployee) }
export async function updateEmployee(id: string, body: EmployeeUpdate) {
  const v = await authSession.request<unknown>(`${root}/employees/${id}`, { method: 'PUT', body })
  if (!isEmployee(v) || v.id !== id || v.displayName !== body.displayName.trim() || v.phone !== body.phone.trim() || v.status !== body.status || [...v.roles].sort().join() !== [...body.roles].sort().join() || v.version < body.version) invalid()
  return v
}
export async function resetPassword(id: string, version: number, newPassword: string, reason: string) { const result = await authSession.request<unknown>(`${root}/employees/${id}/password-reset`, { method: 'POST', body: { version, newPassword, reason } }); if (result !== undefined) invalid() }
export async function changePassword(oldPassword: string, newPassword: string) { const result = await authSession.request<unknown>(`${root}/me/password`, { method: 'POST', body: { oldPassword, newPassword } }); if (result !== undefined) invalid() }
const isAudit = (v: unknown): v is AccountAudit => object(v) && uuid(v.id) && uuid(v.actorId) && ['action','reason','createdAt'].every(k => typeof v[k] === 'string') && ['beforeSummary','afterSummary'].every(k => v[k] === null || typeof v[k] === 'string')
export async function accountHistory(id: string, page: number, signal?: AbortSignal) { return accountPage(await authSession.request(`${root}/employees/${id}/operations?page=${page}`, { signal }), isAudit) }
export function passwordError(password: string, confirmation: string) {
  if (password.length < 8 || new TextEncoder().encode(password).length > 72 || !password.trim()) return '新密码须至少 8 位，最多 72 个 UTF-8 字节'
  if (password !== confirmation) return '两次输入的新密码不一致'
  return ''
}
export const uncertainWrite = (error: unknown) => !(error instanceof ApiError) || !error.status || error.status >= 500 || error.code === 'INVALID_RESPONSE'
