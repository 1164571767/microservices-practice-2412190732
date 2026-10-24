import { ref } from 'vue'
import { ApiError, errorMessage } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import { clientKey } from './api'

export function useArchiveWrite() {
  const busy = ref(false), message = ref(''), uncertain = ref(false)
  async function run<T>(action: () => Promise<T>, success: (result: T) => void) {
    if (busy.value || uncertain.value) return
    busy.value = true; message.value = ''
    try {
      const result = await action()
      // Don't turn an already committed write into a failure when a subsequent read fails.
      void queryClient.invalidateQueries({ queryKey: clientKey })
      success(result)
    } catch (error) {
      uncertain.value = !(error instanceof ApiError && [400, 401, 403, 404, 409].includes(error.status))
      message.value = uncertain.value ? '操作结果未确认。请关闭窗口并刷新档案核实，避免重复提交；输入仍保留在此。' : errorMessage(error)
    } finally { busy.value = false }
  }
  return { busy, message, uncertain, run }
}
