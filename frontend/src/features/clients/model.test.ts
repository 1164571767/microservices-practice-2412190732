import { describe, expect, it } from 'vitest'
import { ageLabel, currentRecords, checkedPage, checked, isPet, isClient } from './model'
describe('客户宠物展示与契约', () => {
  it('估算生日明确标记近似，周年之前不提前加岁', () => {
    expect(ageLabel({ birthDate: '2024-10-24', birthDateEstimated: true }, '2026-10-23')).toBe('约 1 岁 11 个月')
    expect(ageLabel({ birthDate: '2024-10-23', birthDateEstimated: false }, '2026-10-23')).toBe('2 岁')
  })
  it('更正链只显示最新记录，同时保留不相关记录', () => {
    const records = [{ id: 'a', supersedesId: null }, { id: 'b', supersedesId: 'a' }, { id: 'c', supersedesId: 'b' }, { id: 'd', supersedesId: null }]
    expect(currentRecords(records).map((r) => r.id)).toEqual(['c', 'd'])
    expect(records).toHaveLength(4)
  })
  it('拒绝缺失估算或版本字段的响应，不能把不完整201当保存成功', () => {
    expect(() => checked({ id: 'not-a-uuid' }, isClient)).toThrow('服务响应不符合')
    expect(() => checked({ birthDateEstimated: undefined }, isPet)).toThrow('服务响应不符合')
    expect(() => checkedPage({ items: [], page: -1, size: 20, totalElements: 0, totalPages: 0 }, isPet)).toThrow()
    expect(checkedPage({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }, isPet).items).toEqual([])
  })
})
