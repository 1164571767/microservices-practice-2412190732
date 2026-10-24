// Matched to the current identity DTOs; no success envelope is added.
export type Role = 'OWNER' | 'DOCTOR' | 'STAFF' | 'PHARMACY' | 'ADMIN'
export interface Page<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface UserProfile {
  id: string
  username: string
  phone: string
  displayName: string
  roles: Role[]
  status: 'ACTIVE' | 'DISABLED'
}
export interface TokenPair {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}
export interface LoginRequest { username: string; password: string }
export const roleLabels: Record<Role, string> = {
  OWNER: '宠物主人', DOCTOR: '医生', STAFF: '前台', PHARMACY: '药房', ADMIN: '管理员',
}
export const employeeRoles: Role[] = ['DOCTOR', 'STAFF', 'PHARMACY', 'ADMIN']
