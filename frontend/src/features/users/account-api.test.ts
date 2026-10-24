import { afterEach, describe, expect, it, vi } from 'vitest'
import { authSession } from '../../app/services'
import { ApiError } from '../../shared/api/client'
import { employees, updateEmployee, resetPassword, changePassword, passwordError, uncertainWrite } from './account-api'
const id = '12345678-1234-4123-8123-123456789012'
const employee = { id, username: 'test.staff', phone: '13800000000', displayName: '员工', roles: ['STAFF'], status: 'ACTIVE', version: 0, createdAt: '2026-10-24T01:00:00Z' }
afterEach(() => vi.restoreAllMocks())
describe('员工账号与密码边界', () => {
  it('密码按UTF-8字节核对且不裁剪有效密码', () => {
    expect(passwordError('StrongPass!','StrongPass!')).toBe('')
    expect(passwordError('short','short')).not.toBe('')
    expect(passwordError('密'.repeat(25),'密'.repeat(25))).not.toBe('')
    expect(passwordError('密'.repeat(24),'密'.repeat(24))).toBe('')
    expect(passwordError('StrongPass!','different')).toContain('不一致')
  })
  it('列表拒绝纯主人账号和缺失版本', async () => {
    const request = vi.spyOn(authSession, 'request').mockResolvedValue({ items: [employee], page: 0, size: 10, totalElements: 1, totalPages: 1 })
    await expect(employees(new URLSearchParams())).resolves.toMatchObject({ totalElements: 1 })
    request.mockResolvedValue({ items: [{ ...employee, roles: ['OWNER'] }], page: 0, size: 10, totalElements: 1, totalPages: 1 })
    await expect(employees(new URLSearchParams())).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('账号更新必须核对服务端返回身份和目标内容', async () => {
    const body = { version: 0, displayName: '新员工', phone: employee.phone, roles: ['STAFF'] as const, status: 'ACTIVE' as const, reason: '修改资料' }
    const request = vi.spyOn(authSession, 'request').mockResolvedValue({ ...employee, displayName: '新员工', version: 1 })
    await expect(updateEmployee(id, { ...body, roles: [...body.roles] })).resolves.toMatchObject({ version: 1 })
    request.mockResolvedValue(employee)
    await expect(updateEmployee(id, { ...body, roles: [...body.roles] })).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('改密请求仅发送必要字段，重置保留版本与原因', async () => {
    const request = vi.spyOn(authSession, 'request').mockResolvedValue(undefined)
    await changePassword(' old-pass ', ' new-pass ')
    expect(request).toHaveBeenCalledWith('/api/identity/me/password', { method: 'POST', body: { oldPassword: ' old-pass ', newPassword: ' new-pass ' } })
    await resetPassword(id, 2, 'new-pass', '身份核实')
    expect(request).toHaveBeenLastCalledWith(`/api/identity/employees/${id}/password-reset`, { method: 'POST', body: { version: 2, newPassword: 'new-pass', reason: '身份核实' } })
  })
  it('区分明确拒绝和上下游/网络未知结果', () => {
    expect(uncertainWrite(new ApiError('VALIDATION_FAILED','',400))).toBe(false)
    expect(uncertainWrite(new ApiError('CONCURRENT_MODIFICATION','',409))).toBe(false)
    for (const status of [0,500,502,503]) expect(uncertainWrite(new ApiError('FAULT','',status))).toBe(true)
  })
  it('重置超时不自动重试', async () => {
    const request = vi.spyOn(authSession, 'request').mockRejectedValue(new ApiError('REQUEST_TIMEOUT',''))
    await expect(resetPassword(id,0,'new-password','身份核实')).rejects.toMatchObject({ code: 'REQUEST_TIMEOUT' })
    expect(request).toHaveBeenCalledTimes(1)
  })
  it('密码接口必须返回约定的空204结果', async () => {
    vi.spyOn(authSession, 'request').mockResolvedValue({})
    await expect(changePassword('old-password','new-password')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
    await expect(resetPassword(id,0,'new-password','核实')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})
