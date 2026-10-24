<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Plus, Calendar, RefreshRight } from '@element-plus/icons-vue'
import { useAuthStore } from '../auth/store'
import { appointmentKey, schedules } from './api'
import { clinicDate, slotLabel, scheduleLabels, isUncertain, type Doctor, type Schedule } from './model'
import { createSchedule, changeSchedule, scheduleOperations, actionLabels, type ScheduleAction } from './schedule-api'
import { errorMessage } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import AppState from '../../shared/ui/AppState.vue'
import './schedules.css'

const props = defineProps<{ date: string; doctorId: string; doctors: Doctor[]; active: boolean }>()
const emit = defineEmits<{ appointments: []; locked: [value: boolean]; selected: [doctorId: string, date: string] }>()
const admin = computed(() => useAuthStore().user?.roles.includes('ADMIN'))
const query = useQuery({ queryKey: computed(() => [...appointmentKey, 'schedules', props.doctorId]), queryFn: () => schedules(props.doctorId), enabled: computed(() => props.active && !!props.doctorId) })
const visible = computed(() => query.data.value?.filter(s => clinicDate(new Date(s.startAt)) <= props.date && clinicDate(new Date(Date.parse(s.endAt) - 1)) >= props.date) ?? [])
const creating = ref(false), target = ref<{ schedule: Schedule; action: ScheduleAction }>()
const busy = ref(false), unknown = ref(false), verified = ref(false), message = ref(''), reason = ref('')
const verificationText = ref('')
const form = reactive({ doctorId: '', date: '', start: '09:00', end: '12:00', slotMinutes: 30 })
const historyId = ref(''), historyPage = ref(0)
const history = useQuery({ queryKey: computed(() => [...appointmentKey, 'schedule-history', historyId.value, historyPage.value]), queryFn: () => scheduleOperations(historyId.value, historyPage.value), enabled: computed(() => !!historyId.value && !!admin.value) })
const historyLabels: Record<string, string> = { CREATE: '新增排班', OPEN: '恢复放号', BOOKING_CLOSED: '停止放号', CANCELLED: '停诊', DELETE: '删除排班' }
const canWrite = computed(() => admin.value && !busy.value && !unknown.value && !query.isError.value && !query.isFetching.value)
const future = (s: Schedule) => Date.parse(s.endAt) > Date.now()
function openCreate() {
  if (!admin.value || busy.value || unknown.value) return
  Object.assign(form, { doctorId: props.doctorId || props.doctors[0]?.id || '', date: props.date, start: '09:00', end: '12:00', slotMinutes: 30 })
  message.value = ''; creating.value = true
}
function openAction(s: Schedule, action: ScheduleAction) { if (!canWrite.value) return; message.value = ''; reason.value = ''; target.value = { schedule: { ...s }, action } }
async function run(write: () => Promise<unknown>, success: string) {
  if (busy.value || unknown.value) return
  busy.value = true; emit('locked', true); message.value = ''; verified.value = false
  try {
    await write(); message.value = success; creating.value = false; target.value = undefined
    void queryClient.invalidateQueries({ queryKey: appointmentKey })
  } catch (error) {
    unknown.value = isUncertain(error)
    message.value = unknown.value ? '排班操作结果未确认。请保留原信息，刷新排班与操作留痕核实，勿直接重复提交。' : errorMessage(error)
    if (!unknown.value) void query.refetch()
  } finally { busy.value = false; emit('locked', unknown.value) }
}
function submitCreate() {
  if (busy.value || unknown.value) return
  const startAt = new Date(`${form.date}T${form.start}:00+08:00`), endAt = new Date(`${form.date}T${form.end}:00+08:00`)
  if (!form.doctorId || !Number.isFinite(startAt.getTime()) || !Number.isFinite(endAt.getTime()) || startAt.getTime() <= Date.now() || endAt.getTime() - startAt.getTime() < form.slotMinutes * 60_000 || !Number.isInteger(form.slotMinutes) || form.slotMinutes < 1 || form.slotMinutes > 480) {
    message.value = '请选择有效医生和未来时段，结束须晚于开始且至少容纳一个完整号源，每号1—480分钟。'; return
  }
  const body = { doctorId: form.doctorId, startAt: startAt.toISOString(), endAt: endAt.toISOString(), slotMinutes: form.slotMinutes }
  void run(async () => { await createSchedule(body); emit('selected', body.doctorId, form.date) }, '排班已新增，预约页面可查询该时段内尚未被占用的号源。')
}
function submitAction() {
  if (!target.value || !reason.value.trim() || busy.value || unknown.value) return
  const { schedule, action } = target.value, note = reason.value.trim()
  void run(() => changeSchedule(schedule, action, note), `${actionLabels[action]}成功，操作记录已保留。`)
}
async function verify() {
  if (busy.value) return
  busy.value = true
  try {
    const doctor = target.value?.schedule.doctorId || form.doctorId || props.doctorId
    if (!doctor) return
    const rows = await schedules(doctor)
    const matching = target.value ? rows.filter(s => s.id === target.value!.schedule.id) : rows.filter(s => Date.parse(s.startAt) === Date.parse(`${form.date}T${form.start}:00+08:00`) && Date.parse(s.endAt) === Date.parse(`${form.date}T${form.end}:00+08:00`) && s.slotMinutes === form.slotMinutes)
    verificationText.value = matching.length ? matching.map(s => `${slotLabel(s)} · ${scheduleLabels[s.state]} · 排班编号 ${s.id}`).join('；') : '当前读取未查到对应排班，请结合操作留痕及所选医生、时段人工核实。'
    if (target.value) {
      const ops = await scheduleOperations(target.value.schedule.id, 0)
      verificationText.value += `；最近操作：${ops.slice(0, 3).map(o => `${historyLabels[o.action]}（${o.reason}）`).join('；') || '暂无留痕'}`
    }
    await queryClient.invalidateQueries({ queryKey: appointmentKey })
    verified.value = true
  } catch (error) { message.value = `核实读取失败：${errorMessage(error)}。原操作结果仍未确认。` }
  finally { busy.value = false }
}
function acknowledge() { if (!verified.value) return; unknown.value = false; verified.value = false; creating.value = false; target.value = undefined; message.value = '已按人工核对继续，请以实际排班和操作留痕为准。'; emit('locked', false) }
function closeEditor() { if (!busy.value && !unknown.value) { creating.value = false; target.value = undefined } }
</script>

<template>
  <section class="schedule-panel" aria-label="医生排班管理">
    <div class="schedule-banner"><div><h2>让每一段出诊有序安排</h2><p>先排班，再放号。停止放号保留已有预约；停诊前须完成人工处理。</p><small>上海时间 · 单次排班 · {{ admin ? '管理员可维护' : '当前账号仅可查看' }}</small></div><img src="/images/schedule-care.png" alt="" /></div>
    <div class="schedule-toolbar"><p>同一医生可分上午、下午创建；历史时段与预约记录保留。</p><ElButton v-if="admin" type="primary" :icon="Plus" :disabled="busy || unknown || !doctors.length" @click="openCreate">新增排班</ElButton><ElButton :icon="RefreshRight" :disabled="busy" @click="query.refetch()">刷新排班</ElButton></div>
    <p v-if="message && !creating && !target" class="archive-feedback" role="status">{{ message }}</p>
    <AppState v-if="!doctorId" title="请选择一位医生" description="选择医生与日期查看排班；管理员可新增排班。" />
    <AppState v-else-if="query.isPending.value" kind="loading" title="正在读取排班" />
    <AppState v-else-if="query.isError.value" kind="error" title="排班读取失败" :description="errorMessage(query.error.value)" action-label="重新读取排班" @action="query.refetch()" />
    <AppState v-else-if="!visible.length" title="当天暂无排班" description="管理员可新增出诊时段；未排班的日期不会生成号源。" />
    <ul v-else class="reception-schedules schedule-list"><li v-for="s in visible" :key="s.id" :data-schedule-id="s.id"><span class="archive-avatar"><Calendar /></span><div class="schedule-description"><strong>{{ slotLabel(s) }}</strong><p>{{ clinicDate(new Date(s.startAt)) }} · 每个号源 {{ s.slotMinutes }} 分钟</p><span class="archive-badge">{{ scheduleLabels[s.state] }}{{ !future(s) ? ' · 历史只读' : '' }}</span><small class="schedule-id">排班编号 {{ s.id }}</small><div v-if="admin" class="schedule-actions"><button v-if="s.state === 'OPEN' && future(s)" class="text-button" :disabled="!canWrite" @click="openAction(s, 'stop-booking')">停止放号</button><button v-if="s.state === 'BOOKING_CLOSED' && future(s)" class="text-button" :disabled="!canWrite" @click="openAction(s, 'reopen')">恢复放号</button><button v-if="s.state !== 'CANCELLED' && future(s)" class="text-button" :disabled="!canWrite" @click="openAction(s, 'cancel')">停诊</button><button v-if="Date.parse(s.startAt) > Date.now()" class="text-button reception-cancel" :disabled="!canWrite" @click="openAction(s, 'delete')">删除排班</button><button class="text-button" @click="historyId = s.id; historyPage = 0">操作留痕</button><button class="text-button" @click="emit('appointments')">查看当日预约</button></div></div></li></ul>
    <p class="schedule-note">有任何预约历史（包括已取消或已改出）均不可删除。已签到或已叫号患者会阻止停诊；已叫号须待后续诊疗流程处理，不能当作已完成。</p>

    <ElDialog :model-value="creating || !!target" :title="creating ? '新增医生排班' : target ? `${actionLabels[target.action]}？` : ''" width="min(540px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy && !unknown" :show-close="!busy && !unknown" @close="closeEditor">
      <form class="schedule-form" @submit.prevent="creating ? submitCreate() : submitAction()">
        <fieldset :disabled="busy || unknown">
<template v-if="creating"><label>排班医生<select v-model="form.doctorId" aria-label="排班医生" required><option value="" disabled>选择启用医生</option><option v-for="doctor in doctors" :key="doctor.id" :value="doctor.id">{{ doctor.displayName }}</option></select></label><label>排班日期<input v-model="form.date" type="date" :min="clinicDate()" required /></label><div class="schedule-times"><label>出诊开始<input v-model="form.start" type="time" required /></label><label>出诊结束<input v-model="form.end" type="time" required /></label></div><label>每号分钟数<input v-model.number="form.slotMinutes" type="number" min="1" max="480" step="1" required /></label><p class="schedule-note">单次录入同一天的一段出诊时段；不足一个完整号源的尾段不放号。排班可提前安排，预约仍限今天至下个月同日。</p></template>
        <template v-else-if="target"><p>{{ clinicDate(new Date(target.schedule.startAt)) }} {{ slotLabel(target.schedule) }} · {{ scheduleLabels[target.schedule.state] }}</p><p v-if="target.action === 'stop-booking'">立即停止新预约及改入，已有预约继续接诊。</p><p v-else-if="target.action === 'reopen'">恢复尚未开始号源，重新核验医生状态和时段冲突。</p><p v-else-if="target.action === 'cancel'">不会自动取消或转移预约。请先人工改期或取消所有未处理预约；已签到、已叫号患者会阻止停诊。停诊后不可恢复，可另建排班。</p><p v-else>仅未开始且从未关联预约的排班可删除。已取消或已改出的预约也属于历史，不能删除；操作留痕仍保留。</p><label>操作原因<textarea v-model="reason" required maxlength="500" rows="3" /></label></template>
</fieldset>
        <p v-if="message" class="archive-feedback" role="alert">{{ message }}</p>
        <div v-if="unknown" class="schedule-unknown"><p v-if="verified">{{ verificationText }}</p><p>请核对所选医生、时段与操作记录。读取成功仅供核实，不代表原请求失败。</p><ElButton :loading="busy" @click="verify">刷新排班与留痕核实</ElButton><ElButton :disabled="busy || !verified" @click="acknowledge">已核实，继续操作</ElButton></div>
        <div v-else class="schedule-footer"><ElButton :disabled="busy" @click="closeEditor">返回</ElButton><ElButton type="primary" native-type="submit" :loading="busy" :disabled="!creating && !reason.trim()">{{ creating ? '确认新增排班' : target ? `确认${actionLabels[target.action]}` : '确认' }}</ElButton></div>
      </form>
    </ElDialog>
    <ElDialog :model-value="!!historyId" title="排班操作留痕" width="min(560px, 94vw)" @close="historyId = ''"><p class="schedule-id">排班编号 {{ historyId }}</p><AppState v-if="history.isPending.value" kind="loading" title="正在读取操作留痕" /><AppState v-else-if="history.isError.value" kind="error" title="操作留痕读取失败" :description="errorMessage(history.error.value)" action-label="重试" @action="history.refetch()" /><p v-else-if="!history.data.value?.length">暂无本页操作记录；历史迁移前的操作未补造留痕。</p><ol v-else class="schedule-history"><li v-for="item in history.data.value" :key="item.id"><strong>{{ historyLabels[item.action] }}</strong><p>{{ item.reason }}</p><small>{{ item.actorName }} · {{ new Date(item.occurredAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' }) }}</small></li></ol><template #footer><ElButton :disabled="!historyPage || history.isFetching.value" @click="historyPage--">上一页</ElButton><ElButton :disabled="history.isFetching.value || history.isError.value || history.data.value?.length !== 20" @click="historyPage++">下一页</ElButton></template></ElDialog>
  </section>
</template>
