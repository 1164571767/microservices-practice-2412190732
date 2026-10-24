import { afterEach, describe, expect, it, vi } from 'vitest'
import { authSession } from '../../app/services'
import { consultation, saveDraft, submit, complete } from './api'
import { emptyDraft, type Consultation } from './model'
const id = '12345678-1234-4123-8123-123456789012'
const other = '12345678-1234-4123-8123-123456789013'
const value = (): Consultation => ({ appointmentId: id, petId: id, doctorId: id, appointmentStatus: 'CALLED', version: 0, canWrite: true, draft: null, prescriptionChoice: 'PENDING', completedAt: null, updatedAt: null, record: null })
afterEach(() => vi.restoreAllMocks())
describe('诊疗写入结果核实', () => {
  it('拒绝错预约响应', async () => {
    vi.spyOn(authSession, 'request').mockResolvedValue({ ...value(), appointmentId: other })
    await expect(consultation(id)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('保存要求正确版本和正文，但JSON属性次序不影响核实', async () => {
    const d = emptyDraft()
    const request = vi.spyOn(authSession, 'request').mockResolvedValue({ ...value(), version: 1, draft: { items: [], prescriptionChoice: 'PENDING', advice: '', diagnosis: '', symptoms: '' } })
    await expect(saveDraft(id, 0, d)).resolves.toMatchObject({ version: 1 })
    request.mockResolvedValue({ ...value(), version: 1, draft: { ...d, diagnosis: '其他内容' } })
    await expect(saveDraft(id, 0, d)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
    request.mockResolvedValue({ ...value(), version: 0, draft: d })
    await expect(saveDraft(id, 0, d)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
  it('提交和完成不能将缺失凭证的200误判为成功', async () => {
    vi.spyOn(authSession, 'request').mockResolvedValue({ ...value(), version: 1 })
    await expect(submit(id, 0)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
    await expect(complete(id, 0, true)).rejects.toMatchObject({ code: 'INVALID_RESPONSE' })
  })
})
