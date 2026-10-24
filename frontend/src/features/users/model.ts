import { ApiError } from '../../shared/api/client'
import type { Role, UserProfile } from '../../shared/api/types'

export const employeeRoleOptions = [
  { value: 'DOCTOR', label: '医生', description: '查看接诊信息，填写病历与处方' },
  { value: 'STAFF', label: '前台', description: '接待预约，办理签到与收费' },
  { value: 'PHARMACY', label: '药房', description: '维护药品库存，核对处方与发药' },
  { value: 'ADMIN', label: '管理员', description: '开通账号，维护排班与基础配置' },
] as const
export type EmployeeRole = typeof employeeRoleOptions[number]['value']
export interface CreateEmployeeRequest {
  username: string
  displayName: string
  phone: string
  password: string
  roles: EmployeeRole[]
}

export function normalizeEmployee(input: CreateEmployeeRequest): CreateEmployeeRequest {
  return {
    username: input.username.trim().toLowerCase(), displayName: input.displayName.trim(),
    phone: input.phone.trim(), password: input.password, roles: [...new Set(input.roles)],
  }
}

export function validateCreatedEmployee(value: unknown, request: CreateEmployeeRequest): UserProfile {
  const user = value as Partial<UserProfile> | null
  const expected = normalizeEmployee(request)
  const matches = user && typeof user.id === 'string' &&
    /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(user.id) &&
    user.username === expected.username && user.displayName === expected.displayName &&
    user.phone === expected.phone && user.status === 'ACTIVE' && Array.isArray(user.roles) &&
    user.roles.every((role: Role) => expected.roles.includes(role as EmployeeRole)) &&
    new Set(user.roles).size === expected.roles.length
  if (!matches) throw new ApiError('INVALID_RESPONSE', '开通结果未确认，请先核实账号是否已创建')
  return user as UserProfile
}

export interface SubmissionFailure { uncertain: boolean; title: string; message: string }
export function classifyCreateFailure(error: unknown): SubmissionFailure {
  if (error instanceof ApiError) {
    if (error.code === 'USER_ALREADY_EXISTS') return {
      uncertain: false, title: '账号或手机号已被使用', message: '请核对现有账号信息。若刚才曾提交过，请先确认该账号是否已经开通。',
    }
    if (error.status === 403) return {
      uncertain: false, title: '当前账号无权开通员工', message: '只有管理员可以开通员工账号，请联系医院管理员。',
    }
    if (error.status === 401 || error.code === 'SESSION_EXPIRED' || error.code === 'SESSION_CHANGED') return {
      uncertain: false, title: '登录状态已失效', message: '请重新登录后再核对开通结果。',
    }
    if (error.code === 'VALIDATION_FAILED' || error.status === 400) return {
      uncertain: false, title: '资料未通过校验', message: '请检查账号、手机号、密码及角色后重新提交。',
    }
  }
  // A failed transport/response does not prove the write was rolled back.
  return {
    uncertain: true, title: '开通结果未确认',
    message: '服务响应异常或连接中断，账号可能已经创建。请先联系管理员核实，避免重复开通；当前资料已保留，不能直接再次提交。',
  }
}
