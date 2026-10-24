<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElDrawer } from 'element-plus'
import 'element-plus/theme-chalk/el-drawer.css'
import { act, billingKey, detail } from './api'
import { billLabels, isUncertain, money, type BillDetail } from './model'
import { dateTime } from '../clients/model'
import { getClient, getPet } from '../clients/api'
import { ApiError, errorMessage } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import AppState from '../../shared/ui/AppState.vue'
const props = defineProps<{ id: string }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const query = useQuery({ queryKey: [...billingKey, 'detail', props.id], queryFn: () => detail(props.id) })
const action = ref<'pay' | 'refund' | 'void'>(), reason = ref(''), busy = ref(false), uncertain = ref(false), message = ref(''), verified = ref(false)
const key = ref('')
const confirmed = ref<BillDetail>()
const bill = computed(() => confirmed.value ?? query.data.value)
const patientQuery = useQuery({ queryKey: [...billingKey, 'patient', computed(() => bill.value?.petId)], enabled: computed(() => !!bill.value), queryFn: async () => {
  const pet = await getPet(bill.value!.petId)
  const owner = await getClient(pet.ownerId)
  if (pet.id !== bill.value!.petId || owner.id !== pet.ownerId) throw new ApiError('INVALID_RESPONSE', '患者联系资料不匹配，请重新核对')
  return { pet, owner }
} })
const labels = { pay: '确认模拟收款', refund: '确认全额退款', void: '确认作废' }
const operationLabels = { CREATED: '开单', PAID: '模拟收款', REFUNDED: '全额退款', VOIDED: '作废' }
function start(value: 'pay' | 'refund' | 'void') { action.value = value; reason.value = ''; key.value = crypto.randomUUID(); message.value = ''; verified.value = false }
async function refresh() {
  verified.value = false
  const result = await query.refetch()
  if (result.isSuccess) { confirmed.value = result.data; verified.value = true }
}
async function submit() {
  if (busy.value || !action.value || (!uncertain.value && action.value === 'pay' && !patientQuery.isSuccess.value) || (uncertain.value && action.value !== 'pay') || (action.value !== 'pay' && !reason.value.trim())) return
  busy.value = true; message.value = ''
  try {
    confirmed.value = await act(props.id, action.value, reason.value.trim(), key.value)
    message.value = `${labels[action.value].replace('确认', '')}已完成。`
    action.value = undefined; uncertain.value = false
    void queryClient.invalidateQueries({ queryKey: billingKey }); emit('changed')
  } catch (error) {
    uncertain.value = isUncertain(error); verified.value = false
    message.value = uncertain.value ? '操作结果未确认，请核实流水。模拟收款可使用原请求安全重试；退款、作废请刷新核实后再继续处理。' : errorMessage(error)
    if (!uncertain.value) void refresh()
  } finally { busy.value = false }
}
function resume() { if (!verified.value) return; uncertain.value = false; action.value = undefined; message.value = '已完成核实，请依据当前账单状态继续。' }
</script>
<template>
  <ElDrawer :model-value="true" title="账单详情" size="min(680px, 96vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <div class="billing-detail">
      <AppState v-if="query.isPending.value" kind="loading" title="正在读取账单" />
      <AppState v-else-if="query.isError.value" kind="error" title="账单详情读取失败" :description="errorMessage(query.error.value)" action-label="重试详情" @action="refresh" />
      <template v-else-if="bill">
        <div class="billing-detail-head"><span :class="`billing-status billing-status--${bill.status.toLowerCase()}`">{{ billLabels[bill.status] }}</span><strong>{{ money(bill.totalAmount) }}</strong><p>账单编号 {{ bill.id }}</p><p>开单时间 {{ dateTime(bill.createdAt) }}</p></div>
        <p v-if="patientQuery.data.value && !patientQuery.isError.value" class="billing-selected"><strong>{{ patientQuery.data.value.pet.name }} · {{ patientQuery.data.value.pet.species }}</strong><br />{{ patientQuery.data.value.owner.name }} · {{ patientQuery.data.value.owner.phone }}</p>
        <p v-else-if="patientQuery.isError.value" class="billing-feedback" role="alert">患者联系资料读取失败，核对资料后方可收款。<button class="text-button" @click="patientQuery.refetch()">重新读取患者资料</button></p>
        <p v-else class="billing-muted">正在读取患者联系资料…</p>
        <p class="billing-muted">病历 {{ bill.medicalRecordId }}<br />开单人 {{ bill.createdBy }}<br v-if="bill.prescriptionId" /><span v-if="bill.prescriptionId">关联处方 {{ bill.prescriptionId }}</span></p>
        <h3>收费明细</h3>
        <div v-for="line in bill.items" :key="line.id" class="billing-line"><div><strong>{{ line.itemName }}</strong><small>{{ money(line.unitPrice) }} × {{ line.quantity }}</small></div><strong>{{ money(line.amount) }}</strong></div>
        <p class="billing-muted">明细为开单时的名称和价格快照，后续价目表调整不影响本单。</p>
        <h3>支付记录</h3>
        <div v-if="bill.payment" class="billing-payment"><strong>{{ bill.payment.status === 'REFUNDED' ? '已全额退款' : '模拟收款成功' }} · {{ money(bill.payment.amount) }}</strong><p>流水号 {{ bill.payment.id }}</p><p>收款时间 {{ dateTime(bill.payment.paidAt) }}</p><p>收款人 {{ bill.payment.paidBy }}</p><p v-if="bill.payment.refundedAt">退款时间 {{ dateTime(bill.payment.refundedAt) }}<br />退款人 {{ bill.payment.refundedBy }}<br />退款原因 {{ bill.payment.refundReason }}</p></div>
        <p v-else class="billing-muted">暂无支付记录。</p>
        <h3>操作流水</h3>
        <ol class="billing-timeline"><li v-for="op in bill.operations" :key="op.id"><strong>{{ operationLabels[op.action] }}</strong><time>{{ dateTime(op.createdAt) }}</time><p>操作人 {{ op.operatorId }}</p><p v-if="op.reason">原因：{{ op.reason }}</p></li></ol>
        <p v-if="message" role="alert" class="billing-feedback" :class="{ 'billing-feedback--success': !uncertain && !action }">{{ message }}</p>
        <div v-if="uncertain" class="billing-actions"><ElButton :disabled="busy || query.isFetching.value" @click="refresh">刷新核实账单与流水</ElButton><ElButton :disabled="busy || !verified" @click="resume">我已核实，继续处理</ElButton></div>
        <form v-if="action" class="billing-form billing-confirm" @submit.prevent="submit">
          <p>{{ action === 'pay' ? '按账单总额进行模拟收款，不会发生真实扣款。' : action === 'refund' ? '仅支持全额退款；退款后保留原单，不释放本次就诊开单资格。不联动退库。' : '仅未收款账单可作废，保留原单及原因；作废后可为同次就诊重新开单。' }}</p>
          <label v-if="action !== 'pay'">{{ action === 'refund' ? '退款原因' : '作废原因' }}<textarea v-model="reason" required maxlength="255" rows="3" :disabled="busy || uncertain" /></label>
          <div class="billing-actions"><ElButton :disabled="busy || uncertain" @click="action = undefined">返回详情</ElButton><ElButton type="primary" native-type="submit" :loading="busy" :disabled="uncertain ? action !== 'pay' : action !== 'pay' && !reason.trim()">{{ uncertain && action === 'pay' ? '使用原请求安全重试' : labels[action] }}</ElButton></div>
        </form>
        <div v-else-if="!uncertain" class="billing-actions"><ElButton :disabled="busy || query.isFetching.value" @click="refresh">刷新账单</ElButton><template v-if="bill.status === 'UNPAID'"><ElButton @click="start('void')">作废账单</ElButton><ElButton type="primary" :disabled="!patientQuery.isSuccess.value" @click="start('pay')">模拟收款</ElButton></template><ElButton v-if="bill.status === 'PAID'" type="danger" plain @click="start('refund')">全额退款</ElButton></div>
      </template>
    </div>
  </ElDrawer>
</template>
