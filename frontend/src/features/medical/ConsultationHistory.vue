<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { history, operations } from './api'
import { dateTime } from './model'
import { errorMessage } from '../../shared/api/client'
import AppState from '../../shared/ui/AppState.vue'
const props = defineProps<{ petId: string; appointmentId: string }>()
const page = ref(0), auditPage = ref(0)
const records = useQuery({ queryKey: computed(() => ['consultation-history', props.petId, page.value]), queryFn: () => history(props.petId, page.value) })
const audit = useQuery({ queryKey: computed(() => ['consultation-audit', props.appointmentId, auditPage.value]), queryFn: () => operations(props.appointmentId, auditPage.value) })
const labels = { DRAFT_SAVED: '保存草稿', SUBMITTED: '提交正式病历与处方决策', COMPLETED: '完成诊疗' }
</script>
<template>
  <section class="consult-history">
<h3>宠物历史病历</h3><AppState v-if="records.isPending.value" kind="loading" title="正在读取历史病历" /><AppState v-else-if="records.isError.value" kind="error" title="历史病历读取失败" :description="errorMessage(records.error.value)" action-label="重新读取历史" @action="records.refetch()" /><p v-else-if="!records.data.value?.items.length" class="consult-muted">暂无历史病历。</p><template v-else><details v-for="record in records.data.value.items" :key="record.id" class="consult-history-record"><summary>{{ dateTime(record.visitedAt) }} · {{ record.diagnosis }}</summary><dl><dt>症状</dt><dd>{{ record.symptoms }}</dd><dt>医嘱</dt><dd>{{ record.advice || '未填写' }}</dd></dl><div v-for="rx in record.prescriptions" :key="rx.id"><h4>处方</h4><p v-for="line in rx.items" :key="line.id">{{ line.drugName }} {{ line.specification }} · {{ line.dosage }} / {{ line.frequency }} · {{ line.days }} 天 · 总量 {{ line.quantity }}<br />{{ line.note }}</p></div></details><div class="consult-pagination"><ElButton :disabled="page === 0 || records.isFetching.value" @click="page--">上一页病历</ElButton><span>第 {{ page + 1 }} 页</span><ElButton :disabled="page + 1 >= records.data.value.totalPages || records.isFetching.value" @click="page++">下一页病历</ElButton></div></template>
    <h3>本次诊疗留痕</h3><AppState v-if="audit.isError.value" kind="error" title="诊疗留痕读取失败" action-label="重新读取留痕" @action="audit.refetch()" /><p v-else-if="audit.isPending.value" class="consult-muted">正在读取留痕…</p><template v-else><p v-if="!audit.data.value?.length" class="consult-muted">暂无工作台操作记录；历史接口记录以原凭证为准。</p><ol class="consult-audit"><li v-for="op in audit.data.value" :key="op.id"><strong>{{ labels[op.action] }}</strong><small>{{ dateTime(op.occurredAt) }} · 操作人 {{ op.actorId.slice(-8) }}</small></li></ol><div class="consult-pagination"><ElButton :disabled="auditPage === 0 || audit.isFetching.value" @click="auditPage--">上一页留痕</ElButton><span>第 {{ auditPage + 1 }} 页</span><ElButton :disabled="(audit.data.value?.length ?? 0) < 20 || audit.isFetching.value" @click="auditPage++">下一页留痕</ElButton></div></template>
  </section>
</template>
