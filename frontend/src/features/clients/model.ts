import { ApiError } from '../../shared/api/client'

export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface Archive {
  id: string; name: string; version: number; archived: boolean; archivedAt: string | null
  archivedBy: string | null; archiveReason: string | null; createdAt: string; updatedAt: string
}
export interface Client extends Archive { userId: string | null; phone: string; address: string | null }
export interface Pet extends Archive { ownerId: string; species: string; birthDate: string; birthDateEstimated: boolean; weightKg: number }
export interface PetDirectory { pet: Pet; ownerName: string; ownerPhone: string; ownerArchived: boolean }
export interface Actor { recordedBy: string | null; recordedByName: string | null; createdAt: string; supersedesId: string | null; correctionReason: string | null }
export interface Vaccination extends Actor { id: string; petId: string; vaccineName: string; administeredOn: string; nextDueOn: string | null; note: string | null }
export interface Allergy extends Actor { id: string; petId: string; allergen: string; severity: 'MILD' | 'MODERATE' | 'SEVERE'; note: string | null }
export interface MedicalSummary { recordId: string; petId: string; visitedAt: string; diagnosis: string; doctorId: string }
export interface Operation { id: string; action: string; actorId: string; actorName: string; reason: string | null; beforeJson: string | null; afterJson: string; createdAt: string }
export interface BindingCandidate { id: string; username: string; displayName: string; phone: string }
export interface ClientInput { name: string; phone: string; address: string }
export interface PetInput { name: string; species: string; birthDate: string; weightKg: number; birthDateEstimated: boolean }
export const severityLabels = { MILD: '轻微', MODERATE: '中度', SEVERE: '严重' }
export const operationLabels: Record<string, string> = {
  CREATE_CLIENT: '建立客户档案', UPDATE_CLIENT: '修改客户资料', ARCHIVE_CLIENT: '归档客户',
  CREATE_PET: '建立宠物档案', UPDATE_PET: '修改宠物资料', ARCHIVE_PET: '归档宠物', BIND_ACCOUNT: '绑定主人账号',
}
export const dateTime = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
export const today = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Shanghai' }).format(new Date())
export function ageLabel(pet: Pick<Pet, 'birthDate' | 'birthDateEstimated'>, now = today()) {
  const [year, month, day] = pet.birthDate.split('-').map(Number) as [number, number, number]
  const [y, m, d] = now.split('-').map(Number) as [number, number, number]
  const months = Math.max(0, (y - year) * 12 + m - month - (d < day ? 1 : 0))
  const age = months >= 12 ? `${Math.floor(months / 12)} 岁${months % 12 ? ` ${months % 12} 个月` : ''}` : `${months} 个月`
  return `${pet.birthDateEstimated ? '约 ' : ''}${age}`
}
export function currentRecords<T extends { id: string; supersedesId: string | null }>(records: T[]) {
  const superseded = new Set(records.map((r) => r.supersedesId).filter(Boolean))
  return records.filter((r) => !superseded.has(r.id))
}
const object = (v: unknown): v is Record<string, unknown> => !!v && typeof v === 'object'
export const isUuid = (v: unknown): v is string => typeof v === 'string' && /^[a-f\d]{8}(?:-[a-f\d]{4}){3}-[a-f\d]{12}$/i.test(v)
const nullableString = (v: unknown) => v === null || typeof v === 'string'
export function archiveResponse(v: unknown): v is Archive {
  return object(v) && isUuid(v.id) && typeof v.name === 'string' && typeof v.version === 'number' && Number.isSafeInteger(v.version) && v.version >= 0 && typeof v.archived === 'boolean' && typeof v.createdAt === 'string' && typeof v.updatedAt === 'string' && nullableString(v.archiveReason)
}
export const isClient = (v: unknown): v is Client => archiveResponse(v) && object(v) && (v.userId === null || isUuid(v.userId)) && typeof v.phone === 'string' && nullableString(v.address)
export const isPet = (v: unknown): v is Pet => archiveResponse(v) && object(v) && isUuid(v.ownerId) && typeof v.species === 'string' && typeof v.birthDate === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(v.birthDate) && typeof v.birthDateEstimated === 'boolean' && typeof v.weightKg === 'number' && Number.isFinite(v.weightKg)
export const isPetDirectory = (v: unknown): v is PetDirectory => object(v) && isPet(v.pet) && typeof v.ownerName === 'string' && typeof v.ownerPhone === 'string' && typeof v.ownerArchived === 'boolean'
const isActor = (v: Record<string, unknown>) => isUuid(v.id) && isUuid(v.petId) && typeof v.createdAt === 'string' && nullableString(v.recordedByName) && (v.recordedBy === null || isUuid(v.recordedBy)) && (v.supersedesId === null || isUuid(v.supersedesId)) && nullableString(v.correctionReason)
export const isVaccination = (v: unknown): v is Vaccination => object(v) && isActor(v) && typeof v.vaccineName === 'string' && typeof v.administeredOn === 'string' && nullableString(v.nextDueOn) && nullableString(v.note)
export const isAllergy = (v: unknown): v is Allergy => object(v) && isActor(v) && typeof v.allergen === 'string' && typeof v.severity === 'string' && v.severity in severityLabels && nullableString(v.note)
export const isSummary = (v: unknown): v is MedicalSummary => object(v) && isUuid(v.recordId) && isUuid(v.petId) && typeof v.visitedAt === 'string' && typeof v.diagnosis === 'string' && isUuid(v.doctorId)
export const isOperation = (v: unknown): v is Operation => object(v) && isUuid(v.id) && typeof v.action === 'string' && typeof v.actorName === 'string' && typeof v.afterJson === 'string' && nullableString(v.beforeJson) && typeof v.createdAt === 'string'
export const isCandidate = (v: unknown): v is BindingCandidate => object(v) && isUuid(v.id) && typeof v.username === 'string' && typeof v.displayName === 'string' && typeof v.phone === 'string'
export function checked<T>(value: unknown, guard: (v: unknown) => v is T): T {
  if (!guard(value)) throw new ApiError('INVALID_RESPONSE', '服务响应不符合档案接口契约，请先核实结果')
  return value
}
export const checkedArray = <T>(v: unknown, guard: (v: unknown) => v is T): T[] => checked(v, (v): v is T[] => Array.isArray(v) && v.every(guard))
export function checkedPage<T>(value: unknown, guard: (v: unknown) => v is T): Page<T> {
  return checked(value, (v): v is Page<T> => object(v) && Array.isArray(v.items) && v.items.every(guard) && ['page', 'size', 'totalElements', 'totalPages'].every((key) => typeof v[key] === 'number' && Number.isSafeInteger(v[key]) && v[key] >= 0))
}
