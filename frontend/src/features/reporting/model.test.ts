import { describe, expect, it } from 'vitest'
import { checkedDrugs, checkedRevenue, checkedVisits, clinicToday, dates, doctorLabel, periodError, presetPeriod, revenueDays, visitDays } from './model'

const p = { from: '2026-10-01', to: '2026-10-03' }
const id = '019a0000-0000-7000-8000-000000000001'
const visit = () => ({ ...p, totalVisits: 2, daily: [{ date: p.from, visits: 2 }], doctors: [{ doctorId: id, doctorName: null, visits: 2 }] })
const revenue = () => ({ ...p, currency: 'CNY', paidAmount: 0, refundedAmount: 100.25, netAmount: -100.25, paidCount: 0, refundedCount: 1, daily: [{ date: p.from, paidAmount: 0, refundedAmount: 100.25, netAmount: -100.25 }] })
const drug = () => ({ ...p, totalQuantity: 11, totalDispenses: 1, items: [{ drugId: id, drugName: '历史名称', specification: '0.5g', unit: '盒', quantity: 1, dispenseCount: 1 }], page: 1, size: 10, totalElements: 11, totalPages: 2 })
describe('报表正常功能', () => {
  it('上海跨日和本月至今不依赖浏览器本地时区', () => {
    const now = new Date('2026-09-30T16:01:00Z')
    expect(clinicToday(now)).toBe('2026-10-01')
    expect(presetPeriod('month', now)).toEqual({ from: '2026-10-01', to: '2026-10-01' })
    expect(presetPeriod('week', now).from).toBe('2026-09-25')
  })
  it('接受含首尾的闰年366天，保留单日', () => {
    expect(periodError({ from: '2024-01-01', to: '2024-12-31' })).toBe('')
    expect(dates({ from: p.from, to: p.from })).toEqual([p.from])
  })
  it('成功返回后补齐无记录日期并保留医生缺失身份', () => {
    const v = checkedVisits(visit(), p)
    expect(visitDays(v).map(r => r.visits)).toEqual([2, 0, 0])
    expect(doctorLabel(v.doctors[0]!)).toContain(id)
  })
  it('允许跨月退款导致负净收入，并补齐无资金发生日', () => {
    expect(revenueDays(checkedRevenue(revenue(), p)).map(r => r.netAmount)).toEqual([-100.25, 0, 0])
  })
  it('药品全区间汇总不等于当前页，保留原名称与单位', () => {
    const d = checkedDrugs(drug(), p, 1)
    expect(d.totalDispenses).toBe(1); expect(d.totalQuantity).toBe(11)
    expect(d.items[0]?.drugName).toBe('历史名称')
  })
  it('空报表是真实的零汇总和空数组', () => {
    expect(checkedVisits({ ...p, totalVisits: 0, daily: [], doctors: [] }, p).totalVisits).toBe(0)
    expect(checkedDrugs({ ...p, totalQuantity: 0, totalDispenses: 0, items: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }, p, 0).items).toEqual([])
  })
})
describe('报表异常与不可信响应', () => {
  it.each([
    { from: '', to: p.to }, { from: '2026-02-30', to: p.to },
    { from: p.to, to: p.from }, { from: '2024-01-01', to: '2025-01-01' },
  ])('拒绝非法、倒序或超长区间 %o', input => { expect(periodError(input)).not.toBe('') })
  it('拒绝错误的日期范围和医生筛选数据', () => {
    expect(() => checkedVisits({ ...visit(), from: '2026-09-01' }, p)).toThrow()
    expect(() => checkedVisits(visit(), p, '019a0000-0000-7000-8000-000000000002')).toThrow()
  })
  it('拒绝重复或无序统计日、汇总不一致和非数值', () => {
    expect(() => checkedVisits({ ...visit(), daily: [...visit().daily, ...visit().daily] }, p)).toThrow()
    expect(() => checkedVisits({ ...visit(), totalVisits: 3 }, p)).toThrow()
    expect(() => checkedVisits({ ...visit(), totalVisits: NaN }, p)).toThrow()
  })
  it('拒绝币种、金额精度和收支公式异常', () => {
    expect(() => checkedRevenue({ ...revenue(), currency: 'USD' }, p)).toThrow()
    expect(() => checkedRevenue({ ...revenue(), netAmount: 100.25 }, p)).toThrow()
    expect(() => checkedRevenue({ ...revenue(), paidAmount: 0.001 }, p)).toThrow()
    expect(() => checkedRevenue({ ...revenue(), paidAmount: Number.MAX_SAFE_INTEGER }, p)).toThrow()
  })
  it('拒绝分页错位、药品筛选错位与全区间汇总矛盾', () => {
    expect(() => checkedDrugs(drug(), p, 0)).toThrow()
    expect(() => checkedDrugs(drug(), p, 1, '019a0000-0000-7000-8000-000000000002')).toThrow()
    expect(() => checkedDrugs({ ...drug(), totalQuantity: 0 }, p, 1)).toThrow()
    expect(() => checkedDrugs({ ...drug(), totalDispenses: 0 }, p, 1)).toThrow()
  })
})
