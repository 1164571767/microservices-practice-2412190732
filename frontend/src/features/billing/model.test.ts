import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '../../shared/api/client'
import { cents, isBillDetail, isUncertain, money } from './model'
const id = '11111111-1111-4111-8111-111111111111', other = '22222222-2222-4222-8222-222222222222', now = '2026-10-24T01:00:00Z'
const bill = { id, petId: id, medicalRecordId: id, prescriptionId: null, totalAmount: 0.58, currency: 'CNY', status: 'UNPAID', createdBy: id, createdAt: now, updatedAt: now, items: [{ id, chargeItemId: id, itemName: '护理', unitPrice: 0.29, quantity: 2, amount: 0.58 }], payment: null, operations: [{ id, billId: id, action: 'CREATED', fromStatus: null, toStatus: 'UNPAID', reason: null, operatorId: id, createdAt: now }] }
describe('收费金额、契约和故障边界', () => {
  it('以整数分精确计算，并拒绝超过数值安全范围的响应', () => {
    expect(cents(0.29)).toBe(29n); expect(money(1234.5)).toBe('¥1,234.50')
    for (const v of [-1, NaN, Infinity, '1.001', '1e3', Number('99999999999999999')]) expect(cents(v)).toBeUndefined()
    expect(isBillDetail(bill)).toBe(true)
  })
  it('金额、明细、支付状态或归属不一致时不显示成功', () => {
    for (const patch of [{ totalAmount: 1 }, { items: [] }, { items: [...bill.items, ...bill.items] }, { status: 'PAID' }, { currency: 'USD' }, { payment: {} }, { operations: [{ ...bill.operations[0], billId: other }] }]) expect(isBillDetail({ ...bill, ...patch })).toBe(false)
  })
  it.each(['NETWORK_ERROR', 'REQUEST_TIMEOUT', 'INVALID_RESPONSE', 'INTERNAL_ERROR'])('%s 视为结果未知', code => expect(isUncertain(new ApiError(code, '故障', 503))).toBe(true))
  it.each(['PAYMENT_INVALID_STATE', 'PAYMENT_ALREADY_PAID', 'OPERATION_NOT_ALLOWED', 'IDEMPOTENCY_CONFLICT'])('%s 为明确拒绝', code => expect(isUncertain(new ApiError(code, '冲突', 409))).toBe(false))
  it('不把未知 409 当作明确失败', () => expect(isUncertain(new ApiError('UNKNOWN', '未知', 409))).toBe(true))
})
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../app/services', () => ({ authSession: { request } }))
import { act, createBill, detail } from './api'
describe('收费写入契约', () => {
  it('开单显式重试保持正文和幂等键，不发送客户端金额', async () => {
    request.mockResolvedValue(bill)
    const body = { medicalRecordId: id, prescriptionId: null, items: [{ chargeItemId: id, quantity: 2 }] }
    await createBill(body, 'original-key'); await createBill(body, 'original-key')
    expect(request.mock.calls.slice(-2).map(c => c[1])).toEqual([{ method: 'POST', body, idempotencyKey: 'original-key' }, { method: 'POST', body, idempotencyKey: 'original-key' }])
  })
  it('错误账单关联与异常成功响应被拒绝', async () => {
    request.mockResolvedValue({ ...bill, id: other })
    await expect(detail(id)).rejects.toThrow('收费服务响应异常')
    request.mockResolvedValue({ ...bill, medicalRecordId: other })
    await expect(createBill({ medicalRecordId: id, prescriptionId: null, items: [{ chargeItemId: id, quantity: 2 }] }, 'key')).rejects.toThrow('响应异常')
  })
  it('收款与退款断连不自动重放', async () => {
    request.mockClear().mockRejectedValue(new ApiError('NETWORK_ERROR', '连接中断'))
    await expect(act(id, 'pay', '', 'pay-key')).rejects.toThrow('连接中断')
    await expect(act(id, 'refund', '重复收费')).rejects.toThrow('连接中断')
    expect(request).toHaveBeenCalledTimes(2)
  })
})
