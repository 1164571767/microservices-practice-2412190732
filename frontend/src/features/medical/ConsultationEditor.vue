<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { consultation, saveDraft, submit, complete } from './api'
import { emptyDraft, emptyLine, submissionError, dateTime, isDraft, type Consultation, type Draft, type Choice } from './model'
import { getPet, getClient, allergies } from '../clients/api'
import { drugs } from '../pharmacy/api'
import { isUncertain, statusLabels } from '../appointments/model'
import { errorMessage, ApiError } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import { useAuthStore } from '../auth/store'
import AppState from '../../shared/ui/AppState.vue'
import ConsultationHandoff from './ConsultationHandoff.vue'
import ConsultationHistory from './ConsultationHistory.vue'
const auth = useAuthStore()
const props = defineProps<{ id: string }>()
const emit = defineEmits<{ locked: [value: boolean] }>()
const server = ref<Consultation>(), draft = ref<Draft>(emptyDraft()), baseline = ref(JSON.stringify(emptyDraft()))
const loading = ref(true), busy = ref(false), loadError = ref(''), feedback = ref(''), unresolved = ref(false), reconciled = ref(false)
const confirm = ref<'submit' | 'complete' | ''>(''), noPrescription = ref(false), tab = ref('current')
const searchText = ref(''), searchQuery = ref(''), drugPage = ref(0), picker = ref(false)
const dirty = computed(() => JSON.stringify(draft.value) !== baseline.value)
const writable = computed(() => !!server.value?.canWrite && !server.value.record && !loading.value && !loadError.value && !busy.value && !unresolved.value)
const patient = useQuery({ queryKey: computed(() => ['consult-patient', server.value?.petId]), queryFn: () => getPet(server.value!.petId), enabled: computed(() => !!server.value), retry: false })
const owner = useQuery({ queryKey: computed(() => ['consult-owner', patient.data.value?.ownerId]), queryFn: () => getClient(patient.data.value!.ownerId), enabled: computed(() => !!patient.data.value), retry: false })
const allergy = useQuery({ queryKey: computed(() => ['consult-allergies', server.value?.petId]), queryFn: () => allergies(server.value!.petId), enabled: computed(() => !!server.value), retry: false })
const catalog = useQuery({ queryKey: ['consult-drug-options', searchQuery, drugPage], queryFn: () => drugs(searchQuery.value, drugPage.value), enabled: picker, retry: false })
watch([busy, unresolved, dirty], () => emit('locked', busy.value || unresolved.value || dirty.value), { immediate: true })
function adopt() {
  draft.value = JSON.parse(JSON.stringify(server.value?.draft ?? emptyDraft())) as Draft
  baseline.value = JSON.stringify(draft.value)
  unresolved.value = false; reconciled.value = false
}
async function load(initial = false) {
  if (busy.value) return
  loading.value = true; loadError.value = ''; reconciled.value = false
  try {
    server.value = await consultation(props.id)
    noPrescription.value = server.value.prescriptionChoice === 'NONE'
    if (initial || !dirty.value && !unresolved.value) adopt()
    if (unresolved.value) reconciled.value = true
  } catch (error) { loadError.value = errorMessage(error) }
  finally { loading.value = false }
}
async function write(action: 'save' | 'submit' | 'complete') {
  if (!server.value || busy.value || loading.value || unresolved.value || loadError.value) return
  if (action === 'save' && !isDraft(draft.value)) { feedback.value = '请核对草稿长度、数量和天数后保存'; return }
  if (action === 'submit' && (dirty.value || submissionError(draft.value))) { feedback.value = dirty.value ? '请先保存最新草稿' : submissionError(draft.value); return }
  busy.value = true; feedback.value = ''
  try {
    const version = server.value.version
    server.value = action === 'save' ? await saveDraft(props.id, version, JSON.parse(JSON.stringify(draft.value)) as Draft) : action === 'submit' ? await submit(props.id, version) : await complete(props.id, version, noPrescription.value)
    adopt(); confirm.value = ''
    noPrescription.value = server.value.prescriptionChoice === 'NONE'
    feedback.value = action === 'save' ? '草稿已保存到服务端，可重新登录后恢复。' : action === 'submit' ? '正式病历与处方决策已提交，请核对后完成诊疗。' : '诊疗已完成，已进入收费与发药交接。'
    void queryClient.invalidateQueries({ queryKey: ['consultation-handoff', props.id] })
    void queryClient.invalidateQueries({ queryKey: ['consultation-history', server.value.petId] })
    void queryClient.invalidateQueries({ queryKey: ['consultation-audit', props.id] })
    void queryClient.invalidateQueries({ queryKey: ['appointments'] })
  } catch (error) {
    confirm.value = ''
    unresolved.value = isUncertain(error) || error instanceof ApiError && error.code === 'CONCURRENT_MODIFICATION'
    feedback.value = unresolved.value ? '写入结果未确认或内容已有更新。本机文本已保留，请先重新读取核实，勿重复提交。' : errorMessage(error)
  } finally { busy.value = false }
}
function resolve(useServer: boolean) {
  if (!reconciled.value || loadError.value) return
  if (useServer) { if (dirty.value && !window.confirm('采用服务端内容会替换本机未保存内容，是否继续？')) return; adopt() }
  else { unresolved.value = false; reconciled.value = false }
  feedback.value = server.value?.record ? '已核实正式记录，请核对诊疗与交接状态。' : '已核实服务端版本，可继续编辑。'
}
function choose(value: Choice, event: Event) {
  if (value === 'NONE' && draft.value.items.length) {
    if (!window.confirm('选择无需处方会清空当前草稿用药明细，是否继续？')) {
      const radios = (event.target as HTMLInputElement).form?.elements.namedItem('prescription-choice')
      if (radios instanceof RadioNodeList) radios.value = draft.value.prescriptionChoice
      return
    }
    draft.value.items = []
  }
  draft.value.prescriptionChoice = value
}
function canLeave() {
  if (!auth.authenticated) return true
  if (busy.value || unresolved.value) { feedback.value = '请先核实当前写入结果，再切换患者或离开。'; return false }
  return !dirty.value || window.confirm('有未保存的诊疗草稿，离开会丢失本机修改。是否继续？')
}
onBeforeRouteLeave(canLeave)
onBeforeRouteUpdate((to, from) => to.query.appointmentId === from.query.appointmentId || canLeave())
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value || busy.value || unresolved.value) { event.preventDefault(); event.returnValue = '' } }
onMounted(() => { window.addEventListener('beforeunload', beforeUnload); void load(true) })
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); emit('locked', false) })
</script>

<template>
  <section class="surface consult-editor" aria-label="本次诊疗">
    <AppState v-if="loading && !server" kind="loading" title="正在读取诊疗内容" />
    <AppState v-else-if="loadError" kind="error" title="诊疗内容读取失败" :description="loadError" action-label="重新读取诊疗" @action="load()" />
    <template v-if="server && !loadError">
      <header class="consult-patient"><div class="consult-patient-avatar">{{ patient.data.value?.name.slice(0, 1) || '宠' }}</div><div><h2>{{ patient.data.value?.name || '正在核实患者' }}<small>{{ patient.data.value?.species }}</small></h2><p>{{ owner.data.value?.name || '客户资料待核实' }} · {{ owner.data.value?.phone || '—' }} <RouterLink :to="`/pets/${server.petId}`">健康档案 →</RouterLink></p></div><span class="consult-badge" :class="{ 'is-complete': server.completedAt }">{{ server.completedAt ? '诊疗已完成' : server.record ? '已提交 · 待完成' : server.draft ? '诊疗草稿' : statusLabels[server.appointmentStatus] }}</span></header>
      <div v-if="patient.isError.value || owner.isError.value" class="consult-warning" role="alert">患者联系资料读取失败，请核对档案。<ElButton text @click="patient.refetch(); owner.refetch()">重新读取患者</ElButton></div>
      <div v-if="allergy.isError.value" class="consult-warning" role="alert">过敏资料读取失败，不能视为无过敏史。<ElButton text @click="allergy.refetch()">重新读取过敏史</ElButton></div>
      <div v-else-if="allergy.isPending.value" class="consult-warning">正在读取过敏提示…</div>
      <div v-else class="consult-allergies" :class="{ 'has-allergy': allergy.data.value?.length }"><strong>过敏提示</strong><span v-if="allergy.data.value?.length">{{ allergy.data.value.map(a => `${a.allergen}（${a.severity === 'SEVERE' ? '重度' : a.severity === 'MODERATE' ? '中度' : '轻度'}）${a.note ? ' · ' + a.note : ''}`).join('；') }}</span><span v-else>未登记有效过敏记录，请结合问诊核对。</span></div>
      <div class="consult-tabs" role="group" aria-label="诊疗内容视图"><button :class="{ active: tab === 'current' }" @click="tab = 'current'">本次诊疗</button><button :class="{ active: tab === 'history' }" @click="tab = 'history'">历史与留痕</button><ElButton text :disabled="busy || loading" @click="load()">{{ loading ? '读取中' : '重新读取核实' }}</ElButton></div>
      <div v-if="feedback" class="consult-feedback" :class="{ 'consult-warning': unresolved }" role="status">{{ feedback }}<div v-if="unresolved"><ElButton :disabled="busy || loading" @click="load()">查询服务端结果</ElButton><template v-if="reconciled"><ElButton @click="resolve(true)">采用服务端内容</ElButton><ElButton v-if="!server.record && server.canWrite" @click="resolve(false)">保留本机内容继续编辑</ElButton></template></div></div>
      <p v-if="!server.canWrite && !server.completedAt" class="consult-muted">{{ server.appointmentStatus !== 'CALLED' ? '完成签到与叫号后，接诊医生才能编辑。' : '当前为只读视图，仅预约接诊医生可编辑和完成。' }}</p>
      <template v-if="tab === 'current'">
        <ol class="consult-flow" aria-label="就诊进度"><li class="done">已预约</li><li :class="{ done: ['CHECKED_IN', 'CALLED'].includes(server.appointmentStatus) }">已签到</li><li :class="{ done: server.appointmentStatus === 'CALLED' }">已叫号</li><li :class="{ done: server.record }">病历 / 处方</li><li :class="{ done: server.completedAt }">诊疗完成</li><li>收费 / 发药</li></ol>
        <div v-if="server.record" class="consult-formal"><div class="section-heading"><h3>正式病历</h3><span>已提交 · 只读</span></div><dl><dt>症状 / 主诉</dt><dd>{{ server.record.symptoms }}</dd><dt>诊断</dt><dd>{{ server.record.diagnosis }}</dd><dt>医嘱</dt><dd>{{ server.record.advice || '未填写' }}</dd></dl><p class="consult-muted">就诊时间 {{ dateTime(server.record.visitedAt) }}</p><template v-for="rx in server.record.prescriptions" :key="rx.id"><h3>正式处方</h3><div v-for="line in rx.items" :key="line.id" class="consult-rx-read"><strong>{{ line.drugName }} {{ line.specification }}</strong><span>{{ line.dosage }} · {{ line.frequency }} · {{ line.days }} 天 · 发药总量 {{ line.quantity }}</span><small>{{ line.note }}</small></div></template><p v-if="!server.record.prescriptions.length" class="consult-muted">{{ server.prescriptionChoice === 'NONE' ? '接诊医生已明确：本次无需处方。' : '历史正式病历尚无处方决策，请接诊医生核对。' }}</p><label v-if="server.canWrite && server.prescriptionChoice === 'PENDING' && !server.record.prescriptions.length" class="consult-checkbox"><input v-model="noPrescription" type="checkbox" :disabled="busy || unresolved" />确认本次无需处方</label><p v-if="server.completedAt" class="consult-completed">诊疗完成于 {{ dateTime(server.completedAt) }}</p><ElButton v-else-if="server.canWrite" type="primary" :disabled="busy || unresolved || loading || (server.prescriptionChoice === 'PENDING' && !noPrescription && !server.record.prescriptions.length)" @click="confirm = 'complete'">完成诊疗</ElButton></div>
        <form v-else class="consult-form" @submit.prevent="write('save')">
<fieldset :disabled="!writable">
<div class="section-heading"><h3>诊疗记录</h3><span>{{ dirty ? '本机有未保存修改' : server.updatedAt ? '草稿已保存 · ' + dateTime(server.updatedAt) : '尚未保存草稿' }}</span></div><label>症状 / 主诉<textarea v-model="draft.symptoms" aria-label="症状 / 主诉" maxlength="2000" rows="3" placeholder="记录本次症状、持续时间与问诊要点" /></label><label>诊断<textarea v-model="draft.diagnosis" aria-label="诊断" maxlength="2000" rows="2" placeholder="填写诊断结论" /></label><label>医嘱<textarea v-model="draft.advice" aria-label="医嘱" maxlength="2000" rows="2" placeholder="护理建议、观察要点与复诊安排" /></label><div class="section-heading"><h3>处方决策</h3><span>正式提交前须明确选择</span></div><div class="consult-choice"><label><input type="radio" name="prescription-choice" value="PRESCRIPTION" :checked="draft.prescriptionChoice === 'PRESCRIPTION'" @change="choose('PRESCRIPTION', $event)" />开具处方</label><label><input type="radio" name="prescription-choice" value="NONE" :checked="draft.prescriptionChoice === 'NONE'" @change="choose('NONE', $event)" />本次无需处方</label></div>
          <template v-if="draft.prescriptionChoice === 'PRESCRIPTION'">
<p class="consult-muted">建议从目录选取，药房按药名与规格匹配；发药总数量须与实物单位核对。</p><div class="consult-rx-tools"><ElButton :disabled="!writable || draft.items.length >= 50" @click="picker = !picker">从药品目录添加</ElButton><ElButton :disabled="!writable || draft.items.length >= 50" @click="draft.items.push(emptyLine())">手工添加药品</ElButton></div><div v-if="picker" class="consult-drug-picker"><div><input v-model="searchText" aria-label="查找处方药品" placeholder="药名或规格" /><ElButton :disabled="catalog.isFetching.value" @click="searchQuery = searchText.trim(); drugPage = 0; catalog.refetch()">查找药品</ElButton></div><AppState v-if="catalog.isError.value" kind="error" title="药品目录读取失败" :description="errorMessage(catalog.error.value)" action-label="重读药品目录" @action="catalog.refetch()" /><p v-else-if="catalog.isPending.value" class="consult-muted">正在读取药品目录…</p><template v-else><p v-if="!catalog.data.value?.items.length" class="consult-muted">暂无匹配药品</p><button v-for="drug in catalog.data.value?.items" :key="drug.id" type="button" :disabled="!drug.active || draft.items.length >= 50" @click="draft.items.push({ ...emptyLine(), drugName: drug.name, specification: drug.specification }); picker = false">{{ drug.name }} · {{ drug.specification }} / {{ drug.unit }}{{ drug.active ? '' : '（已停用）' }}</button><div class="consult-pagination"><ElButton :disabled="!drugPage || catalog.isFetching.value" @click="drugPage--">上一页药品</ElButton><ElButton :disabled="drugPage + 1 >= (catalog.data.value?.totalPages ?? 0) || catalog.isFetching.value" @click="drugPage++">下一页药品</ElButton></div></template></div>
          <div v-for="(line, index) in draft.items" :key="index" class="consult-rx-line"><div class="section-heading"><h4>用药 {{ index + 1 }}</h4><ElButton text type="danger" :disabled="!writable" @click="draft.items.splice(index, 1)">移除用药 {{ index + 1 }}</ElButton></div><div class="consult-line-grid"><label>药品名称<input v-model="line.drugName" :aria-label="`药品名称 ${index + 1}`" maxlength="100" /></label><label>规格<input v-model="line.specification" :aria-label="`规格 ${index + 1}`" maxlength="100" /></label><label>单次剂量<input v-model="line.dosage" :aria-label="`单次剂量 ${index + 1}`" maxlength="100" /></label><label>频次<input v-model="line.frequency" :aria-label="`频次 ${index + 1}`" maxlength="100" /></label><label>发药总数量<input v-model.number="line.quantity" :aria-label="`发药总数量 ${index + 1}`" type="number" min="1" max="1000000" /></label><label>用药天数<input v-model.number="line.days" :aria-label="`用药天数 ${index + 1}`" type="number" min="1" max="3650" /></label></div><label>用药备注<input v-model="line.note" :aria-label="`用药备注 ${index + 1}`" maxlength="255" /></label></div>
</template>
        </fieldset><div v-if="server.canWrite" class="consult-actions"><ElButton native-type="submit" :loading="busy" :disabled="!writable || !dirty">保存诊疗草稿</ElButton><ElButton type="primary" :disabled="!writable || dirty || !server.draft || !!submissionError(draft)" @click="confirm = 'submit'">提交正式病历与处方</ElButton><p>先保存，再正式提交。正式内容本轮只读；修改与更正另行处理。</p></div>
</form>
        <details v-if="server.record && dirty" class="consult-local-copy"><summary>查看保留的本机未保存文本</summary><pre>{{ JSON.stringify(draft, null, 2) }}</pre></details>
        <ConsultationHandoff :value="server" />
      </template>
      <ConsultationHistory v-else :pet-id="server.petId" :appointment-id="id" />
    </template>
    <ElDialog :model-value="!!confirm" :title="confirm === 'submit' ? '提交正式病历与处方？' : '确认完成本次诊疗？'" width="min(500px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="confirm = ''"><p v-if="confirm === 'submit'">{{ patient.data.value?.name }} · 正式提交后病历和处方只读，请核对症状、诊断与用药。{{ draft.prescriptionChoice === 'NONE' ? '本次明确无需处方。' : `本次包含 ${draft.items.length} 条用药。` }}</p><p v-else>请确认病历与处方决策准确。完成后锁定本次诊疗，由前台和药房独立办理收费与发药。</p><template #footer><ElButton :disabled="busy" @click="confirm = ''">返回核对</ElButton><ElButton type="primary" :loading="busy" @click="write(confirm === 'submit' ? 'submit' : 'complete')">{{ confirm === 'submit' ? '确认正式提交' : '确认完成诊疗' }}</ElButton></template></ElDialog>
  </section>
</template>
