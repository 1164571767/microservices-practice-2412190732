import { describe, it, expect } from 'vitest'
import { emptyDraft, emptyLine, isConsultation, isDraft, isHandoff, isRecord, submissionError, type Consultation, type Handoff } from './model'
const id = '12345678-1234-4123-8123-123456789012'
const other = '12345678-1234-4123-8123-123456789013'
const now = '2026-10-24T00:00:00Z'
const record = () => ({ id, appointmentId: id, petId: id, doctorId: id, symptoms: '症状', diagnosis: '诊断', advice: null, visitedAt: now, createdAt: now, prescriptions: [] })
const value = (): Consultation => ({ appointmentId: id, petId: id, doctorId: id, appointmentStatus: 'CALLED', version: 0, canWrite: true, draft: null, prescriptionChoice: 'PENDING', completedAt: null, updatedAt: null, record: null })
describe('诊疗契约与提交规则', () => {
  it('允许不完整草稿，正式提交要求诊断和明确处方决策', () => {
    const d = emptyDraft(); expect(isDraft(d)).toBe(true); expect(submissionError(d)).toContain('症状')
    d.symptoms = '症状'; d.diagnosis = '诊断'; expect(submissionError(d)).toContain('明确')
    d.prescriptionChoice = 'NONE'; expect(submissionError(d)).toBe('')
    d.items.push(emptyLine()); expect(submissionError(d)).toContain('移除')
  })
  it('处方要求药名、剂量、频次，拒绝越界和非整数数量', () => {
    const d = { ...emptyDraft(), symptoms: '症状', diagnosis: '诊断', prescriptionChoice: 'PRESCRIPTION' as const, items: [emptyLine()] }
    expect(submissionError(d)).toContain('补全')
    Object.assign(d.items[0]!, { drugName: '药品', dosage: '1片', frequency: '每日一次' }); expect(submissionError(d)).toBe('')
    for (const quantity of [0, -1, 1.5, NaN, 1_000_001]) { d.items[0]!.quantity = quantity; expect(isDraft(d)).toBe(false) }
    d.items[0]!.quantity = 1; d.items[0]!.days = 3651; expect(isDraft(d)).toBe(false)
  })
  it('拒绝残缺响应、错误患者归属、非法版本与伪完成', () => {
    expect(isConsultation(value())).toBe(true)
    for (const patch of [{ version: -1 }, { version: 1.5 }, { completedAt: now }, { appointmentStatus: 'CHECKED_IN' }, { draft: {} }]) expect(isConsultation({ ...value(), ...patch })).toBe(false)
    expect(isConsultation({ ...value(), record: { ...record(), petId: other } })).toBe(false)
    expect(isConsultation({ ...value(), record: record(), prescriptionChoice: 'NONE', completedAt: now, canWrite: false })).toBe(true)
  })
  it('正式处方必须归属于当前病历和医生', () => {
    expect(isRecord(record())).toBe(true)
    expect(isRecord({ ...record(), prescriptions: [{ id, medicalRecordId: other, doctorId: id, issuedAt: now, items: [{ ...emptyLine(), id, drugName: '药品', dosage: '1片', frequency: '每日一次' }] }] })).toBe(false)
  })
  it('交接区分实际无账单/无发药和错误契约，支持退款状态', () => {
    const h: Handoff = { appointmentId: id, medicalRecordId: id, prescriptionId: id, bill: null, dispense: null }
    expect(isHandoff(h)).toBe(true)
    expect(isHandoff({ ...h, bill: { id, status: 'REFUNDED' } })).toBe(true)
    expect(isHandoff({ ...h, bill: { id, status: 'VOID' } })).toBe(false)
    expect(isHandoff({ ...h, prescriptionId: null, dispense: { id, dispensedAt: now } })).toBe(false)
    expect(isHandoff({ appointmentId: id })).toBe(false)
  })
})
