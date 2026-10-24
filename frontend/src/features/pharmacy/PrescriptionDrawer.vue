<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElDrawer } from 'element-plus'
import 'element-plus/theme-chalk/el-drawer.css'
import { review, dispense, dispenseResult, pharmacyKey } from './api'
import { isUncertain, type Dispense } from './model'
import { queryClient } from '../../app/services'
import { errorMessage } from '../../shared/api/client'
import { dateTime, severityLabels } from '../clients/model'
import AppState from '../../shared/ui/AppState.vue'
const props = defineProps<{ id: string }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const key = `pharmacy-${crypto.randomUUID()}`
const result = useQuery({ queryKey: [...pharmacyKey, 'review', props.id], queryFn: () => review(props.id), staleTime: 0 })
const busy = ref(false), checking = ref(false), uncertain = ref(false), confirmed = ref(false), message = ref(''), saved = ref<Dispense>()
const record = computed(() => saved.value ?? result.data.value?.dispense)
async function submit() {
  if (busy.value || checking.value || record.value || !confirmed.value || !result.data.value || result.isError.value || result.isFetching.value) return
  busy.value = true; message.value = ''
  try {
    const value = await dispense(props.id, key), p = result.data.value.prescription
    if (value.petId !== p.petId || value.medicalRecordId !== p.medicalRecordId || value.doctorId !== p.doctorId) throw new Error('identity mismatch')
    saved.value = value; uncertain.value = false; message.value = '整张处方发药已确认，库存已按服务端结果扣减。'; emit('changed'); void queryClient.invalidateQueries({ queryKey: pharmacyKey })
  } catch (e) { uncertain.value = isUncertain(e); message.value = uncertain.value ? '发药结果未确认。请先查询发药结果；未查到时仍可能正在处理，可显式使用原幂等键安全重试。请勿转为人工出库。' : errorMessage(e) }
  finally { busy.value = false }
}
async function verify() {
  if (busy.value || checking.value) return
  checking.value = true
  try { const value = await dispenseResult(props.id); const p = result.data.value?.prescription; if (!p || value.petId !== p.petId || value.medicalRecordId !== p.medicalRecordId || value.doctorId !== p.doctorId) throw new Error('identity mismatch'); saved.value = value; uncertain.value = false; message.value = '已核实发药成功，无需重复发药。'; emit('changed'); void queryClient.invalidateQueries({ queryKey: pharmacyKey }) }
  catch (e) { message.value = `${errorMessage(e)}。查询未成功不能证明尚未发药，请保留原处方和幂等键。` }
  finally { checking.value = false }
}
</script>
<template>
  <ElDrawer :model-value="true" title="处方核对与发药" size="min(690px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy && !checking" :show-close="!busy && !checking" @close="emit('close')">
    <div class="pharmacy-detail"><AppState v-if="result.isPending.value" kind="loading" title="正在读取处方与过敏提示" /><AppState v-else-if="result.isError.value && !record" kind="error" title="处方核对资料读取失败" :description="errorMessage(result.error.value)" action-label="重试核对资料" @action="result.refetch()" /><template v-else-if="result.data.value"><section class="pharmacy-detail-head"><span class="pharmacy-badge" :class="record ? 'is-success' : 'is-warning'">{{ record ? '已发药' : '待发药' }}</span><h2>{{ result.data.value.patient.petName }} <small>{{ result.data.value.patient.species }}</small></h2><p>{{ result.data.value.patient.ownerName }} · {{ result.data.value.patient.ownerPhone }}</p><p>{{ result.data.value.doctorName }} · {{ dateTime(result.data.value.prescription.issuedAt) }}</p><p>处方：{{ id }}</p><p v-if="result.data.value.patient.petArchived || result.data.value.patient.ownerArchived">历史档案已归档，请核对本次处方及患者身份。</p></section><div class="pharmacy-alert" :class="{ 'is-success': !result.data.value.allergies.length }"><strong>{{ result.data.value.allergies.length ? '过敏提示 · 发药前请人工核对' : '当前档案未记录有效过敏信息' }}</strong><p v-for="(a, i) in result.data.value.allergies" :key="i">{{ a.allergen }} · {{ severityLabels[a.severity] }}{{ a.note ? ` · ${a.note}` : '' }}</p><small>档案提示供核对，不替代药师审核。</small></div><h3>医生处方</h3><div v-for="line in result.data.value.prescription.items" :key="line.id" class="pharmacy-prescription-line"><strong>{{ line.drugName }} <span>{{ line.specification || '未指定规格，需核对目录匹配' }}</span></strong><p>{{ line.dosage }} · {{ line.frequency }} · {{ line.days }} 天</p><p>处方总数量：{{ line.quantity }}<span v-if="line.note"> · {{ line.note }}</span></p></div><section v-if="record" class="pharmacy-receipt"><h3>发药凭证</h3><p>{{ dateTime(record.dispensedAt) }}</p><p>发药人：{{ record.dispensedBy }}</p><p>发药单：{{ record.id }}</p><div v-for="line in record.items" :key="line.drugId"><strong>{{ line.drugName }} · {{ line.specification }}</strong><p>发出 {{ line.quantity }} {{ line.unit }} · 发药后库存 {{ line.stockAfter }} {{ line.unit }}</p></div><small>同药品处方行由服务端合并；此处为发药当时的快照。</small></section><template v-else><p class="pharmacy-muted">确认后整张处方一次发药，任一药品缺失、停用、匹配有歧义或库存不足时整单不扣库存。发药与收费独立。</p><label class="pharmacy-check"><input v-model="confirmed" type="checkbox" :disabled="busy || uncertain || checking" />已核对患者、过敏提示、规格、用法和实物数量</label><div class="pharmacy-actions"><ElButton v-if="!uncertain" type="primary" :disabled="!confirmed || result.isFetching.value" :loading="busy" @click="submit">确认整单发药</ElButton><template v-else><ElButton :disabled="busy" :loading="checking" @click="verify">查询发药结果</ElButton><ElButton type="primary" :disabled="checking || result.isFetching.value || result.isError.value" :loading="busy" @click="submit">使用原幂等键重试发药</ElButton></template></div></template></template><p v-if="message" role="status" class="pharmacy-alert">{{ message }}</p></div>
  </ElDrawer>
</template>
