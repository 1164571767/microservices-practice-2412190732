// @vitest-environment node
import { describe, expect, it } from 'vitest'
import { ApiError } from '../src/shared/api/client'
import { classifyCreateFailure, normalizeEmployee, validateCreatedEmployee } from '../src/features/users/model'

const request = { username: ' Dr.Lin ', displayName: ' 林医生 ', phone: ' 13900001234 ', password: ' keep spaces ', roles: ['DOCTOR', 'DOCTOR'] as const }
const data = () => ({ ...request, roles: [...request.roles] })
const valid = { id: '01a14833-2d06-798e-a0f5-daafba2ffaf7', username: 'dr.lin', displayName: '林医生', phone: '13900001234', roles: ['DOCTOR'], status: 'ACTIVE' }

describe('员工开通的写入及结果边界', () => {
  it('账号按后端规范归一化，但不修改密码，也不修改调用方对象', () => {
    const input = data()
    expect(normalizeEmployee(input)).toEqual({ username: 'dr.lin', displayName: '林医生', phone: '13900001234', password: ' keep spaces ', roles: ['DOCTOR'] })
    expect(input.username).toBe(' Dr.Lin ')
    expect(input.roles).toHaveLength(2)
  })

  it('仅完整且属于此次请求的结果可作为创建成功', () => {
    expect(validateCreatedEmployee(valid, data())).toEqual(valid)
    for (const response of [null, {}, { ...valid, id: 'invalid' }, { ...valid, username: 'someone' }, { ...valid, phone: 'other' }, { ...valid, roles: ['ADMIN'] }, { ...valid, status: 'DISABLED' }]) {
      expect(() => validateCreatedEmployee(response, data())).toThrow(ApiError)
    }
  })

  it.each([
    new ApiError('NETWORK_ERROR', 'offline'),
    new ApiError('REQUEST_TIMEOUT', 'timeout'),
    new ApiError('INVALID_RESPONSE', 'bad response', 201),
    new ApiError('HTTP_ERROR', 'upstream failure', 502),
    new ApiError('INTERNAL_ERROR', 'internal', 500),
    new Error('unexpected'),
  ])('传输或服务异常不被误判为未创建：%s', (error) => {
    expect(classifyCreateFailure(error).uncertain).toBe(true)
  })

  it.each([
    [new ApiError('USER_ALREADY_EXISTS', 'duplicate', 409), '账号或手机号已被使用'],
    [new ApiError('VALIDATION_FAILED', 'validation', 400), '资料未通过校验'],
    [new ApiError('AUTH_FORBIDDEN', 'forbidden', 403), '当前账号无权开通员工'],
    [new ApiError('AUTH_UNAUTHENTICATED', 'expired', 401), '登录状态已失效'],
  ] as const)('有明确拒绝响应时区分原因：%s', (error, title) => {
    expect(classifyCreateFailure(error)).toMatchObject({ uncertain: false, title })
  })
})
