<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { Calendar, Plus, RefreshRight, Clock, ArrowRight } from '@element-plus/icons-vue'
import { useAuthStore } from '../auth/store'
import { appointmentKey, doctors, reception, queue, checkIn, cancel, callNext } from './api'
import { clinicDate, slotLabel, timeLabel, statusLabels, isUncertain, type ReceptionEntry, type AppointmentStatus, type Appointment } from './model'
import { isUuid } from '../clients/model'
import { errorMessage } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import AppState from '../../shared/ui/AppState.vue'
import BookingDialog from './BookingDialog.vue'
import SchedulePanel from './SchedulePanel.vue'
import '../clients/clients.css'
import './appointments.css'

const route = useRoute(), router = useRouter(), auth = useAuthStore()
const date = ref(clinicDate()), doctorId = ref(''), status = ref<AppointmentStatus | ''>(''), page = ref(0)
const now = ref(new Date()), today = computed(() => clinicDate(now.value))
const view = ref<'appointments' | 'schedules'>('appointments'), queueDoctor = ref('')
const scheduleLocked = ref(false)
const booking = ref(false), editing = ref<ReceptionEntry>(), cancelling = ref<ReceptionEntry>()
const busy = ref(false), feedback = ref(''), uncertain = ref(false), refreshedAfterUnknown = ref(false)
const petId = computed(() => isUuid(route.query.petId) ? route.query.petId : '')
const doctorQuery = useQuery({ queryKey: [...appointmentKey, 'doctors'], queryFn: doctors })
watch(doctorQuery.data, values => { if (!queueDoctor.value && values?.length) queueDoctor.value = values.find(d => d.id === auth.user?.id)?.id ?? values[0]!.id }, { immediate: true })
watch([date, doctorId, status, petId], () => { page.value = 0 })
const params = computed(() => { const p = new URLSearchParams({ date: date.value, page: String(page.value), size: '10' }); if (doctorId.value) p.set('doctorId', doctorId.value); if (status.value) p.set('status', status.value); if (petId.value) p.set('petId', petId.value); return p })
const paramsKey = computed(() => params.value.toString())
const listQuery = useQuery({ queryKey: [...appointmentKey, 'reception', paramsKey], queryFn: () => reception(params.value), enabled: computed(() => !!date.value) })
const queueQuery = useQuery({ queryKey: [...appointmentKey, 'queue', queueDoctor, today], queryFn: () => queue(queueDoctor.value), enabled: computed(() => !!queueDoctor.value), refetchInterval: 30_000 })
const queueName = computed(() => doctorQuery.data.value?.find(d => d.id === queueDoctor.value)?.displayName ?? '医生')
const queueFresh = computed(() => !queueQuery.isPending.value && !queueQuery.isError.value && !queueQuery.isFetching.value)
const writable = computed(() => !busy.value && !uncertain.value && !scheduleLocked.value && !listQuery.isFetching.value && !listQuery.isError.value)
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => { timer = setInterval(() => { now.value = new Date() }, 30_000) })
onUnmounted(() => { if (timer) clearInterval(timer) })
async function refresh() {
  const results = await Promise.all([listQuery.refetch(), ...(queueDoctor.value ? [queueQuery.refetch()] : [])])
  if (uncertain.value && results.every(r => !r.isError)) refreshedAfterUnknown.value = true
  if (view.value === 'schedules') void queryClient.invalidateQueries({ queryKey: [...appointmentKey, 'schedules'] })
}
async function act(action: () => Promise<Appointment>, success: string | ((result: Appointment) => string)) {
  if (busy.value || uncertain.value) return
  busy.value = true; feedback.value = ''; refreshedAfterUnknown.value = false
  try {
    const result = await action()
    feedback.value = typeof success === 'string' ? success : success(result)
    cancelling.value = undefined
    void queryClient.invalidateQueries({ queryKey: appointmentKey })
  } catch (error) {
    uncertain.value = isUncertain(error)
    feedback.value = uncertain.value ? '操作结果未确认。请刷新预约与候诊核实，勿重复签到、取消或叫号；叫号重试可能叫到下一位。' : errorMessage(error)
    if (!uncertain.value) void queryClient.invalidateQueries({ queryKey: appointmentKey })
  } finally { busy.value = false }
}
function saved() { booking.value = false; editing.value = undefined; feedback.value = '预约已保存，请按就诊日期查询预约记录。' }
function call() {
  const name = queueName.value, id = queueDoctor.value, waiting = queueQuery.data.value ?? []
  return act(() => callNext(id), result => {
    const patient = waiting.find(row => row.appointmentId === result.id)?.patient
    return patient ? `已叫号：${patient.petName} / ${patient.ownerName} · 接诊医生：${name}。` : `叫号成功 · 医生：${name} · 预约编号：${result.id}，请核对已叫号记录。`
  })
}
async function showCalled() { date.value = today.value; doctorId.value = queueDoctor.value; status.value = 'CALLED'; page.value = 0; view.value = 'appointments'; await nextTick(); await refresh() }
const clearPet = () => router.replace({ query: {} })
</script>

<template>
  <div class="archive-page reception-page">
    <header class="archive-heading"><div><div class="eyebrow">APPOINTMENTS & RECEPTION</div><h1>预约与接待</h1><p>安排每一次到院，让等待更有序。</p></div><ElButton type="primary" :disabled="busy || uncertain || doctorQuery.isError.value || !doctorQuery.data.value?.length" @click="booking = true"><Plus />新建预约</ElButton></header>
    <section class="reception-welcome"><div><span class="reception-welcome__label"><Calendar /> 有序接待 · 用心照护</span><h2>从预约到叫号，衔接每一份关爱。</h2><p>到院后签到，按签到先后进入医生当日候诊队列。</p><span class="reception-welcome__note">上海时间 · 仅就诊当日可签到，迟到仍可办理</span></div><img src="/images/reception-care.png" alt="" width="280" height="170" /></section>
    <div v-if="feedback" class="reception-feedback" :class="{ 'reception-feedback--uncertain': uncertain }" role="status"><p>{{ feedback }}</p><div v-if="uncertain"><ElButton :disabled="busy" @click="refresh">刷新并核实状态</ElButton><ElButton @click="showCalled">查看已叫号记录</ElButton><ElButton :disabled="!refreshedAfterUnknown || busy" @click="uncertain = false; feedback = ''">已核对，继续接待</ElButton></div></div>
    <AppState v-if="doctorQuery.isPending.value" kind="loading" title="正在读取医生目录" />
    <AppState v-else-if="doctorQuery.isError.value" kind="error" title="医生目录读取失败" :description="errorMessage(doctorQuery.error.value)" action-label="重新读取医生" @action="doctorQuery.refetch()" />
    <p v-else-if="!doctorQuery.data.value?.length" class="archive-notice">暂无可选医生。请联系管理员核实医生账号与排班。</p>
    <div class="reception-grid">
      <section class="archive-card archive-directory reception-records">
        <div class="archive-directory__top"><div class="archive-tabs" role="group" aria-label="接待视图"><button :class="{ active: view === 'appointments' }" :aria-pressed="view === 'appointments'" @click="view = 'appointments'">预约记录</button><button :class="{ active: view === 'schedules' }" :aria-pressed="view === 'schedules'" @click="view = 'schedules'">医生排班</button></div><button class="icon-button" aria-label="刷新预约与候诊" :disabled="busy || listQuery.isFetching.value" @click="refresh"><RefreshRight /></button></div>
        <form class="reception-filters" @submit.prevent="refresh"><label>就诊日期<input v-model="date" type="date" required /></label><label>接诊医生<select v-model="doctorId" aria-label="接诊医生"><option value="">全部医生</option><option v-for="doctor in doctorQuery.data.value" :key="doctor.id" :value="doctor.id">{{ doctor.displayName }}</option></select></label><label v-if="view === 'appointments'">预约状态<select v-model="status" aria-label="预约状态"><option value="">全部状态</option><option v-for="(label, value) in statusLabels" :key="value" :value="value">{{ label }}</option></select></label><ElButton native-type="submit">查询</ElButton></form>
        <div v-if="petId" class="reception-pet-filter">当前仅显示所选宠物的预约<ElButton text @click="clearPet">查看全部宠物</ElButton></div>
        <template v-if="view === 'appointments'">
          <AppState v-if="listQuery.isPending.value" kind="loading" title="正在读取预约" />
          <AppState v-else-if="listQuery.isError.value" kind="error" title="预约读取失败" :description="errorMessage(listQuery.error.value)" action-label="重新读取预约" @action="listQuery.refetch()" />
          <AppState v-else-if="!listQuery.data.value?.items.length" title="当前条件下暂无预约" description="调整日期、医生或状态，或为已有宠物新建预约。" />
          <template v-else>
            <p class="reception-table-note">{{ date }} · 共 {{ listQuery.data.value.totalElements }} 条预约 · 左右滑动查看完整记录</p>
            <div class="archive-table-wrap"><table class="archive-table reception-table"><thead><tr><th>到院时段</th><th>宠物 / 客户</th><th>医生</th><th>状态</th><th>接待操作</th></tr></thead><tbody><tr v-for="entry in listQuery.data.value.items" :key="entry.appointment.id" :data-appointment-id="entry.appointment.id"><td><strong>{{ slotLabel(entry.appointment) }}</strong></td><td><template v-if="entry.patient"><RouterLink :to="`/pets/${entry.patient.petId}`" class="archive-person"><span class="archive-avatar archive-avatar--pet">{{ entry.patient.petName.slice(0, 1) }}</span><div><strong>{{ entry.patient.petName }} <span class="reception-species">{{ entry.patient.species }}</span></strong><small>{{ entry.patient.ownerName }} · {{ entry.patient.ownerPhone }}</small></div></RouterLink><small v-if="entry.patient.petArchived || entry.patient.ownerArchived">档案已归档 · 历史保留</small></template><span v-else>患者资料不可用<small>预约编号 {{ entry.appointment.id }}</small></span></td><td>{{ entry.doctorName }}</td><td><span class="reception-status" :class="`reception-status--${entry.appointment.status.toLowerCase()}`">{{ statusLabels[entry.appointment.status] }}</span></td><td><div class="reception-row-actions"><button v-if="entry.appointment.status === 'SCHEDULED'" class="text-button" :disabled="!writable || clinicDate(new Date(entry.appointment.startAt)) !== today" :title="clinicDate(new Date(entry.appointment.startAt)) !== today ? '仅就诊当日可签到' : '确认已到院后签到'" @click="act(() => checkIn(entry.appointment.id), '到院签到成功，已进入当日候诊队列。')">签到</button><button v-if="entry.appointment.status === 'SCHEDULED'" class="text-button" :disabled="!writable || !entry.patient || entry.patient.petArchived || entry.patient.ownerArchived" @click="editing = entry">改期</button><button v-if="['SCHEDULED', 'CHECKED_IN'].includes(entry.appointment.status)" class="text-button reception-cancel" :disabled="!writable" @click="feedback = ''; cancelling = entry">取消预约</button><RouterLink v-if="entry.appointment.status === 'CALLED'" class="text-button" :to="{ path: '/medical', query: { appointmentId: entry.appointment.id, date } }">诊疗 / 交接</RouterLink><span v-if="entry.appointment.status === 'CANCELLED'" class="archive-muted">记录保留</span></div></td></tr></tbody></table></div>
            <div class="archive-pagination"><span>第 {{ page + 1 }} / {{ listQuery.data.value.totalPages || 1 }} 页</span><div><ElButton :disabled="page === 0 || busy" @click="page--">上一页</ElButton><ElButton :disabled="page + 1 >= listQuery.data.value.totalPages || busy" @click="page++">下一页</ElButton></div></div>
          </template>
        </template>
        <SchedulePanel v-show="view === 'schedules'" :active="view === 'schedules'" :date="date" :doctor-id="doctorId" :doctors="doctorQuery.data.value ?? []" @locked="scheduleLocked = $event" @selected="(doctor, day) => { doctorId = doctor; date = day }" @appointments="view = 'appointments'; status = ''; clearPet()" />
      </section>
      <aside class="archive-card reception-queue">
<div class="reception-queue__heading"><h2><Clock />当日候诊</h2><span>{{ today }}</span></div><label class="reception-queue__doctor"><span class="sr-only">候诊医生</span><select v-model="queueDoctor" aria-label="候诊医生"><option value="" disabled>选择候诊医生</option><option v-for="doctor in doctorQuery.data.value" :key="doctor.id" :value="doctor.id">{{ doctor.displayName }}</option></select></label>
        <AppState v-if="!queueDoctor" title="选择医生查看候诊" />
        <AppState v-else-if="queueQuery.isPending.value" kind="loading" title="正在读取候诊" />
        <AppState v-else-if="queueQuery.isError.value" kind="error" title="候诊读取失败" :description="errorMessage(queueQuery.error.value)" action-label="重新读取候诊" @action="queueQuery.refetch()" />
        <template v-else><div class="reception-queue__count"><strong>{{ queueQuery.data.value?.length ?? 0 }}</strong><span>位正在等待 · {{ queueName }}</span></div><p v-if="!queueQuery.data.value?.length" class="reception-queue__empty">当前没有候诊患者</p><ol v-else class="reception-queue__list"><li v-for="entry in queueQuery.data.value" :key="entry.appointmentId"><span>{{ entry.position.toString().padStart(2, '0') }}</span><div><RouterLink v-if="entry.patient" :to="`/pets/${entry.petId}`">{{ entry.patient.petName }}<small>{{ entry.patient.ownerName }}</small></RouterLink><strong v-else>患者资料不可用</strong><small>{{ timeLabel(entry.checkedInAt) }} 签到 · 预约 {{ timeLabel(entry.startAt) }}</small></div><span v-if="entry.position === 1" class="reception-next">下一位</span></li></ol></template>
        <ElButton type="primary" class="reception-call" :loading="busy" :disabled="!queueDoctor || !queueFresh || !queueQuery.data.value?.length || busy || uncertain" @click="call">叫下一位<ArrowRight /></ElButton><p class="reception-queue__note">每 30 秒刷新 · 仅处理当日已签到预约<br />已叫号表示完成叫号交接，诊疗进度请另行确认。</p>
      </aside>
    </div>
    <BookingDialog v-if="booking || editing" :doctors="doctorQuery.data.value ?? []" :entry="editing" :initial-pet-id="petId || undefined" @close="booking = false; editing = undefined" @saved="saved" />
    <ElDialog v-if="cancelling" :model-value="true" title="取消这次预约？" width="min(440px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="cancelling = undefined"><p>{{ cancelling.patient?.petName || '所选宠物' }} · {{ cancelling.doctorName }} · {{ clinicDate(new Date(cancelling.appointment.startAt)) }} {{ slotLabel(cancelling.appointment) }}</p><p class="reception-dialog-note">取消后保留历史记录并释放号源；如已签到，也会移出候诊队列。</p><p v-if="feedback" class="archive-feedback" role="alert">{{ feedback }}</p><template #footer><ElButton :disabled="busy" @click="cancelling = undefined">返回</ElButton><ElButton type="danger" :loading="busy" :disabled="uncertain" @click="act(() => cancel(cancelling!.appointment.id), '预约已取消，历史记录已保留。')">确认取消预约</ElButton></template></ElDialog>
  </div>
</template>
