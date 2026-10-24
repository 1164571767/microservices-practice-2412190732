<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { handoff } from './api'
import { checked, isHandoff, dateTime, type Consultation, type Handoff } from './model'
import { useAuthStore } from '../auth/store'
import { errorMessage } from '../../shared/api/client'
import AppState from '../../shared/ui/AppState.vue'
const props = defineProps<{ value: Consultation }>()
const auth = useAuthStore()
const result = useQuery({ queryKey: computed(() => ['consultation-handoff', props.value.appointmentId, props.value.version]), queryFn: async () => checked(await handoff(props.value.appointmentId), (v): v is Handoff => isHandoff(v) && v.medicalRecordId === (props.value.record?.id ?? null) && v.prescriptionId === (props.value.record?.prescriptions[0]?.id ?? null)), refetchInterval: 30_000, staleTime: 0, refetchOnMount: 'always' })
const canBill = computed(() => auth.user?.roles.some(r => ['STAFF', 'ADMIN'].includes(r)))
const canDispense = computed(() => auth.user?.roles.some(r => ['PHARMACY', 'ADMIN'].includes(r)))
const billLabels = { UNPAID: '待收款', PAID: '已支付', REFUNDED: '已全额退款 · 仍占用开单资格' }
</script>
<template>
  <section class="consult-handoff" aria-label="收费与发药交接">
<div class="section-heading"><h3>收费与发药交接</h3><ElButton text :disabled="result.isFetching.value" @click="result.refetch()">刷新交接进度</ElButton></div>
    <AppState v-if="result.isPending.value" kind="loading" title="正在核实交接进度" />
    <AppState v-else-if="result.isError.value" kind="error" title="交接进度暂时无法核实" :description="errorMessage(result.error.value)" action-label="重新核实交接" @action="result.refetch()" />
    <div v-else-if="result.data.value" class="consult-handoff-grid"><div><span>前台收费</span><strong>{{ result.data.value.bill ? billLabels[result.data.value.bill.status] : result.data.value.medicalRecordId ? '待手工开单' : '等待正式病历' }}</strong><RouterLink v-if="canBill && value.record" :to="{ path: '/billing', query: { petId: value.petId, ...(result.data.value.bill ? { billId: result.data.value.bill.id } : {}) } }">前往收费工作台 →</RouterLink><small v-else>由前台或管理员办理</small></div><div><span>药房发药</span><strong>{{ result.data.value.dispense ? '已发药' : result.data.value.prescriptionId ? '待药房核对发药' : value.prescriptionChoice === 'NONE' ? '无需处方' : '等待处方决策' }}</strong><small v-if="result.data.value.dispense">{{ dateTime(result.data.value.dispense.dispensedAt) }}</small><RouterLink v-if="canDispense && result.data.value.prescriptionId" :to="{ path: '/pharmacy', query: { prescriptionId: result.data.value.prescriptionId } }">前往处方核对 →</RouterLink><small v-else-if="value.prescriptionChoice !== 'NONE'">由药房或管理员办理</small></div></div>
    <p class="consult-muted">手工计费 · 收费与发药独立办理 · 诊疗完成后仍可查询交接进度</p>
  </section>
</template>
