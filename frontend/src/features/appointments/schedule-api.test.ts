import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../shared/api/client'
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../app/services', () => ({ authSession: { request } }))
import { createSchedule, changeSchedule } from './schedule-api'
import { isSchedule, type Schedule } from './model'
const id = '11111111-1111-4111-8111-111111111111'
const s: Schedule = { id, doctorId: id, startAt: '2026-11-10T00:00:00Z', endAt: '2026-11-10T04:00:00Z', slotMinutes: 30, state: 'OPEN', version: 0 }
describe('排班写入响应与网络边界', () => {
  it('拒绝未知状态与缺失版本，防止旧后端伪装支持管理', () => {
    expect(isSchedule(s)).toBe(true)
    expect(isSchedule({ ...s, state: 'DONE' })).toBe(false)
    expect(isSchedule({ ...s, version: undefined })).toBe(false)
  })
  it('新增响应必须与原输入一致', async () => {
    request.mockResolvedValue({ ...s, slotMinutes: 60 })
    await expect(createSchedule(s)).rejects.toThrow('响应异常')
  })
  it('状态操作必须是同一排班、新版本和正确状态', async () => {
    for (const bad of [{ ...s }, { ...s, state: 'BOOKING_CLOSED', version: 0 }, { ...s, id: '22222222-2222-4222-8222-222222222222', state: 'BOOKING_CLOSED', version: 1 }]) {
      request.mockResolvedValue(bad)
      await expect(changeSchedule(s, 'stop-booking', '暂停')).rejects.toThrow('响应异常')
    }
    request.mockResolvedValue({ ...s, state: 'BOOKING_CLOSED', version: 1 })
    await expect(changeSchedule(s, 'stop-booking', '暂停')).resolves.toMatchObject({ version: 1 })
  })
  it('网络故障不自动重放新增、状态变更或删除', async () => {
    for (const action of [() => createSchedule(s), () => changeSchedule(s, 'cancel', '停诊'), () => changeSchedule(s, 'delete', '录入错误')]) {
      request.mockClear().mockRejectedValue(new ApiError('NETWORK_ERROR', '断连'))
      await expect(action()).rejects.toThrow('断连')
      expect(request).toHaveBeenCalledTimes(1)
    }
  })
})
