<script setup lang="ts">
import { ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElDrawer, ElPagination } from 'element-plus'
import 'element-plus/theme-chalk/el-drawer.css'
import { adjust, adjustments, stock, pharmacyKey } from './api'
import { isUncertain } from './model'
import { queryClient } from '../../app/services'
import { errorMessage } from '../../shared/api/client'
import { dateTime } from '../clients/model'
import AppState from '../../shared/ui/AppState.vue'
const props = defineProps<{ id: string; writable: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const page = ref(0), delta = ref(''), reason = ref(''), busy = ref(false), uncertain = ref(false), message = ref(''), confirmed = ref(false)
const result = useQuery({ queryKey: [...pharmacyKey, 'stock', props.id], queryFn: () => stock(props.id), staleTime: 0 })
const history = useQuery({ queryKey: [...pharmacyKey, 'adjustments', props.id, page], queryFn: () => adjustments(props.id, page.value) })
async function submit() {
  if (busy.value || uncertain.value || !confirmed.value) return
  const n = Number(delta.value)
  if (!Number.isInteger(n) || !n || Math.abs(n) > 2147483647 || !reason.value.trim()) { message.value = '调整数量需为非零整数，并填写调整原因。'; return }
  if (result.isError.value || !result.data.value) { message.value = '请先成功读取当前库存后核对。'; return }
  busy.value = true; message.value = ''
  try { const saved = await adjust(props.id, n, reason.value.trim()); message.value = `库存调整已确认：${saved.quantityBefore} → ${saved.quantityAfter}。`; delta.value = ''; reason.value = ''; confirmed.value = false; emit('changed'); void queryClient.invalidateQueries({ queryKey: pharmacyKey }) }
  catch (e) { uncertain.value = isUncertain(e); message.value = uncertain.value ? '库存调整结果未确认，不能直接重提。请刷新库存及调整流水，按操作人、原因、数量和时间核实；核实后关闭重新进入。' : errorMessage(e) }
  finally { busy.value = false }
}
</script>
<template>
  <ElDrawer :model-value="true" title="库存详情与调整流水" size="min(640px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <div class="pharmacy-detail">
<ElButton :disabled="busy" @click="result.refetch(); history.refetch()">刷新库存与流水</ElButton><AppState v-if="result.isPending.value" kind="loading" title="正在读取库存" /><AppState v-else-if="result.isError.value" kind="error" title="库存读取失败" :description="errorMessage(result.error.value)" action-label="重试库存" @action="result.refetch()" /><section v-else-if="result.data.value" class="pharmacy-detail-head"><h2>{{ result.data.value.drugName }}</h2><p>{{ result.data.value.specification }}</p><strong>{{ result.data.value.quantity }} <small>{{ result.data.value.unit }}</small></strong><p>预警阈值 {{ result.data.value.lowStockThreshold }} · {{ result.data.value.lowStock ? '低库存，请及时补充' : '未低于预警阈值' }}</p></section>
    <form v-if="writable" class="pharmacy-form" @submit.prevent="submit"><h3>人工库存调整</h3><fieldset :disabled="busy || uncertain"><label>调整数量（正数入库，负数出库）<input v-model="delta" type="number" required step="1" min="-2147483647" max="2147483647" /></label><label>调整原因<textarea v-model="reason" required maxlength="255" rows="2" placeholder="填写入库、盘点或报损原因" /></label><label class="pharmacy-check"><input v-model="confirmed" type="checkbox" />已核对实物、单位和本次增减数量</label></fieldset><p v-if="message" role="status" class="pharmacy-alert">{{ message }}</p><ElButton type="primary" native-type="submit" :loading="busy" :disabled="uncertain || !confirmed || result.isError.value || !result.data.value">确认库存调整</ElButton></form>
    <h3>人工调整流水</h3><p class="pharmacy-muted">包含初始入库；处方发药记录在“处方发药”的已发药列表查看。</p><AppState v-if="history.isPending.value" kind="loading" title="正在读取流水" /><AppState v-else-if="history.isError.value" kind="error" title="调整流水读取失败" :description="errorMessage(history.error.value)" action-label="重试流水" @action="history.refetch()" /><AppState v-else-if="!history.data.value?.items.length" kind="empty" title="暂无人工调整流水" /><ol v-else class="pharmacy-timeline"><li v-for="a in history.data.value.items" :key="a.id"><strong>{{ a.delta > 0 ? '+' : '' }}{{ a.delta }} · {{ a.quantityBefore }} → {{ a.quantityAfter }}</strong><p>{{ a.reason }}</p><small>{{ dateTime(a.createdAt) }}<br />操作人：{{ a.operatorId }}</small></li></ol><ElPagination v-if="history.data.value" :current-page="page + 1" :total="history.data.value.totalElements" :page-size="10" layout="prev, pager, next" @update:current-page="page = $event - 1" />
</div>
  </ElDrawer>
</template>
