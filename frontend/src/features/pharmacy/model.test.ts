import { describe, it, expect, vi } from 'vitest'
import { ApiError } from '../../shared/api/client'
import { isAdjustment, isInventory, isReview, isUncertain } from './model'
const id = '11111111-1111-4111-8111-111111111111', other = '22222222-2222-4222-8222-222222222222', now = '2026-10-24T01:00:00Z'
const inventory = { drugId: id, drugName: '药品', specification: '0.5g', unit: '盒', quantity: 5, lowStockThreshold: 5, lowStock: false, updatedAt: now }
const adjustment = { id, drugId: id, delta: -3, quantityBefore: 10, quantityAfter: 7, reason: '报损', operatorId: id, createdAt: now }
const dispenseResponse = { id, prescriptionId: id, medicalRecordId: id, petId: id, doctorId: id, dispensedBy: id, dispensedAt: now, items: [{ drugId: id, drugName: '药品', specification: '0.5g', unit: '盒', quantity: 3, stockAfter: 7 }] }
const reviewResponse = { prescription: { id, medicalRecordId: id, petId: id, doctorId: id, issuedAt: now, items: [{ id, drugName: '药品', specification: '0.5g', dosage: '1片', frequency: '每日2次', days: 5, quantity: 3, note: null }] }, patient: { petId: id, petName: '小满', species: '猫', ownerId: id, ownerName: '客户', ownerPhone: '13800138000', petArchived: false, ownerArchived: false }, doctorName: '医生', allergies: [], dispense: dispenseResponse }
describe('药房契约和异常边界', () => {
  it('等于阈值不预警，低于阈值预警', () => { expect(isInventory(inventory)).toBe(true); expect(isInventory({ ...inventory, quantity: 4, lowStock: true })).toBe(true); expect(isInventory({ ...inventory, lowStock: true })).toBe(false) })
  it('拒绝负库存、溢出及不一致的流水', () => { expect(isAdjustment(adjustment)).toBe(true); for (const patch of [{ quantityAfter: -1 }, { quantityAfter: 8 }, { delta: 0 }, { delta: 0.5 }]) expect(isAdjustment({ ...adjustment, ...patch })).toBe(false); expect(isInventory({ ...inventory, quantity: 2147483648 })).toBe(false) })
  it('处方、患者、发药归属一致才接受；过敏缺失不可当无过敏', () => { expect(isReview(reviewResponse)).toBe(true); for (const patch of [{ allergies: null }, { patient: { ...reviewResponse.patient, petId: other } }, { dispense: { ...dispenseResponse, prescriptionId: other } }, { prescription: { ...reviewResponse.prescription, items: [] } }]) expect(isReview({ ...reviewResponse, ...patch })).toBe(false) })
  it.each(['NETWORK_ERROR', 'REQUEST_TIMEOUT', 'INVALID_RESPONSE', 'INTERNAL_ERROR'])('%s 视为写入结果未知', code => expect(isUncertain(new ApiError(code, '故障', 503))).toBe(true))
  it.each(['INSUFFICIENT_STOCK', 'OPERATION_NOT_ALLOWED', 'IDEMPOTENCY_CONFLICT', 'DRUG_ALREADY_EXISTS'])('%s 为明确业务拒绝', code => expect(isUncertain(new ApiError(code, '拒绝', 409))).toBe(false))
  it('未知错误码不伪装成已确认失败', () => expect(isUncertain(new ApiError('UNKNOWN', '未知', 409))).toBe(true))
})
const { request } = vi.hoisted(() => ({ request: vi.fn() }))
vi.mock('../../app/services', () => ({ authSession: { request } }))
import { dispense, dispenseResult, adjust, saveDrug } from './api'
describe('药房写入与结果核实契约', () => {
  it('显式发药重试使用相同处方和原幂等键', async () => { request.mockResolvedValue(dispenseResponse); await dispense(id, 'original-key'); await dispense(id, 'original-key'); expect(request.mock.calls.slice(-2)).toEqual(Array(2).fill([`/api/pharmacy/prescriptions/${id}/dispense`, { method: 'POST', idempotencyKey: 'original-key' }])) })
  it('核实结果使用 GET，不触发发药', async () => { request.mockResolvedValue(dispenseResponse); await dispenseResult(id); expect(request).toHaveBeenLastCalledWith(`/api/pharmacy/workbench/prescriptions/${id}/dispense`) })
  it('错误归属或数量响应不显示成功', async () => { request.mockResolvedValue({ ...dispenseResponse, prescriptionId: other }); await expect(dispense(id, 'key')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' }); request.mockResolvedValue({ ...adjustment, delta: -2, quantityAfter: 8 }); await expect(adjust(id, -3, '报损')).rejects.toMatchObject({ code: 'INVALID_RESPONSE' }) })
  it('编辑药品不发送初始库存，维护不会重复入库', async () => { request.mockResolvedValue({ id, name: '药品', specification: '0.5g', unit: '盒', active: false, lowStockThreshold: 5, createdAt: now, updatedAt: now }); await saveDrug({ name: '药品', specification: '0.5g', unit: '盒', active: false, lowStockThreshold: 5, initialQuantity: 100 }, id); expect(request).toHaveBeenLastCalledWith(`/api/pharmacy/drugs/${id}`, { method: 'PUT', body: { name: '药品', specification: '0.5g', unit: '盒', lowStockThreshold: 5, active: false } }) })
})
