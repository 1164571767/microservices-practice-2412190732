import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../shared/api/client'
import { bookingDateRange, isBookingDate, clinicDate, checked, isAppointment, isQueueEntry, isReceptionEntry, isUncertain, slotLabel } from './model'
const id = '11111111-1111-4111-8111-111111111111'
const appointment = { id, petId: id, doctorId: id, startAt: '2026-11-10T01:00:00Z', endAt: '2026-11-10T01:30:00Z', status: 'SCHEDULED', checkedInAt: null, calledAt: null }
describe('预约与接待契约及院内时间', () => {
  it('今天至下月同日可预约，月底及上海午夜正确处理', () => {
    const now = new Date('2026-01-30T16:00:00Z')
    expect(bookingDateRange(now)).toEqual({ min: '2026-01-31', max: '2026-02-28' })
    expect(isBookingDate('2026-02-28', now)).toBe(true)
    expect(isBookingDate('2026-02-01', now)).toBe(true)
    expect(isBookingDate('2026-01-30', now)).toBe(false)
    expect(isBookingDate('2026-03-01', now)).toBe(false)
    expect(isBookingDate('2026-02-31', now)).toBe(false)
    expect(isBookingDate('', now)).toBe(false)
    expect(bookingDateRange(new Date('2027-12-31T01:00:00Z')).max).toBe('2028-01-31')
  })
  it('上海日期正确跨越 UTC 日期边界', () => {
    expect(clinicDate(new Date('2026-11-09T16:00:00Z'))).toBe('2026-11-10')
    expect(clinicDate(new Date('2026-11-09T15:59:59Z'))).toBe('2026-11-09')
    expect(slotLabel(appointment)).toBe('09:00–09:30')
  })
  it('合法预约和缺失历史资料可读，非法状态和反向区间被拒绝', () => {
    expect(isAppointment(appointment)).toBe(true)
    expect(isReceptionEntry({ appointment, patient: null, doctorName: '历史医生' })).toBe(true)
    for (const patch of [{ status: 'COMPLETED' }, { startAt: appointment.endAt }, { endAt: 'bad' }, { status: 'CHECKED_IN' }, { status: 'CALLED' }, { id: 'not-an-id' }]) expect(isAppointment({ ...appointment, ...patch })).toBe(false)
    expect(() => checked({}, isAppointment)).toThrow('预约服务响应异常')
  })
  it('错误患者关联不允许出现在接待资料或候诊内', () => {
    const patient = { petId: '22222222-2222-4222-8222-222222222222', petName: '小满', species: '猫', ownerId: id, ownerName: '李女士', ownerPhone: '13800138000', petArchived: false, ownerArchived: false }
    expect(isReceptionEntry({ appointment, patient, doctorName: '医生' })).toBe(false)
    expect(isQueueEntry({ position: 1, appointmentId: id, petId: id, doctorId: id, startAt: appointment.startAt, checkedInAt: appointment.startAt, patient })).toBe(false)
  })
  it.each(['NETWORK_ERROR', 'REQUEST_TIMEOUT', 'INVALID_RESPONSE', 'INTERNAL_ERROR'])('%s 视为结果未知', code => {
    expect(isUncertain(new ApiError(code, '故障', code === 'INTERNAL_ERROR' ? 500 : 0))).toBe(true)
  })
  it.each(['APPOINTMENT_CONFLICT', 'APPOINTMENT_INVALID_STATE', 'CONCURRENT_MODIFICATION', 'OPERATION_NOT_ALLOWED'])('%s 是明确拒绝', code => {
    expect(isUncertain(new ApiError(code, '明确拒绝', 409))).toBe(false)
  })
  it('未知 409 和畸形成功响应不能假定未提交', () => {
    expect(isUncertain(new ApiError('HTTP_ERROR', '冲突', 409))).toBe(true)
    expect(isUncertain(new ApiError('INVALID_RESPONSE', '异常响应', 201))).toBe(true)
  })
})

const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../app/services', () => ({ authSession: { request } }))
import { book, callNext, checkIn, reschedule } from './api'
describe('写入响应与重试边界', () => {
  it('创建预约两次显式核实保持同一幂等键和正文', async () => {
    request.mockResolvedValue(appointment)
    const body = { petId: id, doctorId: id, startAt: appointment.startAt, endAt: appointment.endAt }
    await book(body, 'same-operation'); await book(body, 'same-operation')
    expect(request.mock.calls.slice(-2).map(args => args[1])).toEqual([{ method: 'POST', body, idempotencyKey: 'same-operation' }, { method: 'POST', body, idempotencyKey: 'same-operation' }])
  })
  it('签到返回另一预约或错误状态不能显示成功', async () => {
    request.mockResolvedValue(appointment)
    await expect(checkIn(id)).rejects.toThrow('响应异常')
    request.mockResolvedValue({ ...appointment, id: '22222222-2222-4222-8222-222222222222', status: 'CHECKED_IN', checkedInAt: appointment.startAt })
    await expect(checkIn(id)).rejects.toThrow('响应异常')
  })
  it('改期返回旧时段不能显示已改期', async () => {
    request.mockResolvedValue(appointment)
    await expect(reschedule(id, { startAt: '2026-11-10T02:00:00Z', endAt: '2026-11-10T02:30:00Z' })).rejects.toThrow('响应异常')
  })
  it('叫号网络失败只发送一次请求', async () => {
    request.mockClear().mockRejectedValue(new ApiError('NETWORK_ERROR', '连接中断'))
    await expect(callNext(id)).rejects.toThrow('连接中断')
    expect(request).toHaveBeenCalledTimes(1)
  })
})
