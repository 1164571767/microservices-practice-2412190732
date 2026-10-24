<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { FirstAidKit, RefreshRight, ArrowRight } from '@element-plus/icons-vue'
import { useAuthStore } from '../auth/store'
import { doctors, reception, queue, callNext, appointmentKey } from '../appointments/api'
import { clinicDate, slotLabel, statusLabels, isUncertain, type AppointmentStatus } from '../appointments/model'
import { isUuid } from '../clients/model'
import { errorMessage } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import AppState from '../../shared/ui/AppState.vue'
import ConsultationEditor from './ConsultationEditor.vue'
import './medical.css'
const auth = useAuthStore(), route = useRoute(), router = useRouter()
const date = ref(typeof route.query.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(route.query.date) ? route.query.date : clinicDate())
const doctorId = ref(auth.user?.roles.includes('DOCTOR') ? auth.user.id : ''), status = ref<AppointmentStatus | ''>(''), page = ref(0)
const busy = ref(false), locked = ref(false), unknown = ref(false), checked = ref(false), feedback = ref('')
const id = computed(() => isUuid(route.query.appointmentId) ? route.query.appointmentId : '')
const doctorList = useQuery({ queryKey: [...appointmentKey, 'doctors'], queryFn: doctors })
const params = computed(() => { const p = new URLSearchParams({ date: date.value, page: String(page.value), size: '10' }); if (doctorId.value) p.set('doctorId', doctorId.value); if (status.value) p.set('status', status.value); return p })
const list = useQuery({ queryKey: computed(() => [...appointmentKey, 'reception', params.value.toString()]), queryFn: () => reception(params.value), enabled: computed(() => !!date.value) })
const waiting = useQuery({ queryKey: [...appointmentKey, 'queue', doctorId], queryFn: () => queue(doctorId.value), enabled: computed(() => !!doctorId.value), refetchInterval: 30_000 })
watch([date, doctorId, status], () => { page.value = 0 })
async function refresh() {
  const results = await Promise.all([list.refetch(), ...(doctorId.value ? [waiting.refetch()] : [])])
  if (unknown.value && results.every(r => !r.isError)) checked.value = true
}
async function call() {
  if (busy.value || locked.value || unknown.value || !doctorId.value) return
  busy.value = true; feedback.value = ''; checked.value = false
  try {
    const result = await callNext(doctorId.value)
    date.value = clinicDate(); status.value = 'CALLED'
    await router.replace({ query: { appointmentId: result.id, date: date.value } })
    feedback.value = '已叫下一位，请核对患者并开始诊疗。'
    void queryClient.invalidateQueries({ queryKey: appointmentKey })
  } catch (error) { unknown.value = isUncertain(error); feedback.value = unknown.value ? '叫号结果未确认，请核实已叫号记录；重复叫号可能叫到下一位。' : errorMessage(error) }
  finally { busy.value = false }
}
function select(appointmentId: string) { if (!busy.value && !unknown.value) void router.replace({ query: { appointmentId, date: date.value } }) }
</script>
<template>
  <div class="medical-page">
<header class="consult-heading"><div><span class="eyebrow">DOCTOR'S WORKSPACE</span><h1>诊疗工作台</h1><p>连接每一次接诊，让照护有迹可循。</p></div><RouterLink to="/appointments">预约与接待 <ArrowRight /></RouterLink></header>
    <section class="consult-welcome"><div><span><FirstAidKit /> 专注诊疗 · 有序交接</span><h2>从候诊到诊疗，悉心守护每一位伙伴。</h2><p>恢复诊疗草稿，完成病历与处方，衔接前台和药房。</p></div><img src="/images/consultation-care.png" alt="" width="300" height="130" /></section>
    <div v-if="feedback" class="consult-feedback" role="status">{{ feedback }}<div v-if="unknown"><ElButton @click="status = 'CALLED'; date = clinicDate(); refresh()">核实已叫号记录</ElButton><ElButton :disabled="!checked" @click="unknown = false; feedback = ''">已核对叫号结果</ElButton></div></div>
    <AppState v-if="doctorList.isError.value" kind="error" title="医生目录读取失败" :description="errorMessage(doctorList.error.value)" action-label="重新读取医生" @action="doctorList.refetch()" />
    <div class="consult-grid">
<aside class="surface consult-directory">
<div class="section-heading"><h2>就诊列表</h2><button class="icon-button" aria-label="刷新就诊列表" :disabled="busy || list.isFetching.value" @click="refresh"><RefreshRight /></button></div><form class="consult-filters" @submit.prevent="refresh"><label>就诊日期<input v-model="date" type="date" required /></label><label>接诊医生<select v-model="doctorId" aria-label="诊疗接诊医生" :disabled="busy || unknown"><option value="">全部医生</option><option v-if="auth.user?.roles.includes('DOCTOR') && !doctorList.data.value?.some(d => d.id === auth.user?.id)" :value="auth.user.id">{{ auth.user.displayName }}</option><option v-for="doctor in doctorList.data.value" :key="doctor.id" :value="doctor.id">{{ doctor.displayName }}</option></select></label><label>预约状态<select v-model="status" aria-label="诊疗预约状态"><option value="">全部状态</option><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label><ElButton native-type="submit">查询就诊</ElButton></form>
      <div class="consult-call"><div><strong>当日候诊</strong><span v-if="!doctorId">选择医生后查看</span><span v-else-if="waiting.isError.value">暂时无法读取</span><span v-else>{{ waiting.data.value?.length ?? '…' }} 位等待</span></div><ElButton type="primary" :loading="busy" :disabled="!doctorId || waiting.isPending.value || waiting.isError.value || waiting.isFetching.value || !waiting.data.value?.length || locked || unknown || busy" @click="call">叫下一位</ElButton><p v-if="locked">先保存草稿并核实当前操作，再叫下一位。</p><p v-else>候诊每 30 秒更新 · 仅当日已签到患者</p></div>
      <AppState v-if="list.isPending.value" kind="loading" title="正在读取就诊列表" /><AppState v-else-if="list.isError.value" kind="error" title="就诊列表读取失败" :description="errorMessage(list.error.value)" action-label="重新读取就诊列表" @action="list.refetch()" /><AppState v-else-if="!list.data.value?.items.length" title="暂无匹配就诊" description="调整查询条件，或前往预约与接待办理签到。" /><div v-else class="consult-patients"><button v-for="entry in list.data.value.items" :key="entry.appointment.id" type="button" :data-consultation-id="entry.appointment.id" :class="{ active: id === entry.appointment.id }" :aria-pressed="id === entry.appointment.id" :disabled="busy || unknown" @click="select(entry.appointment.id)"><span class="consult-avatar">{{ entry.patient?.petName.slice(0, 1) || '宠' }}</span><span><strong>{{ entry.patient?.petName || '患者资料不可用' }}<small>{{ entry.patient?.species }}</small></strong><small>{{ entry.patient?.ownerName || '联系资料待核实' }} · {{ slotLabel(entry.appointment) }}</small><small>{{ entry.doctorName }} · {{ entry.consultation?.completedAt ? '诊疗已完成' : entry.consultation?.recordId ? '已提交 · 待完成' : entry.consultation?.hasDraft ? '诊疗草稿' : statusLabels[entry.appointment.status] }}</small></span><ArrowRight /></button></div><div v-if="list.data.value && !list.isError.value" class="consult-pagination"><ElButton :disabled="page === 0 || list.isFetching.value" @click="page--">上一页</ElButton><span>{{ page + 1 }} / {{ list.data.value.totalPages || 1 }}</span><ElButton :disabled="page + 1 >= list.data.value.totalPages || list.isFetching.value" @click="page++">下一页</ElButton></div>
    </aside><ConsultationEditor v-if="id" :id="id" :key="id" @locked="locked = $event" /><section v-else class="surface consult-empty"><FirstAidKit /><h2>选择一位患者，开始本次诊疗</h2><p>接诊医生在叫号后编辑；前台与管理员可查看正式记录及交接。</p><span>服务端保存草稿 · 正式提交后只读 · 明确记录诊疗完成</span></section>
</div>
  </div>
</template>
