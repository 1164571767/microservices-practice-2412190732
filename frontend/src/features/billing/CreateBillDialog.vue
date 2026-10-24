<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import PatientPicker from './PatientPicker.vue'
import { billingKey, charges, createBill, records } from './api'
import { billLabels, cents, isUncertain, money, type BillDetail, type BillInput, type ChargeItem } from './model'
import { dateTime, type PetDirectory } from '../clients/model'
import { errorMessage } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import AppState from '../../shared/ui/AppState.vue'
const emit = defineEmits<{ close: []; saved: [bill: BillDetail] }>()
const pet = ref<PetDirectory>(), recordId = ref(''), recordPage = ref(0), linkPrescription = ref(false)
const query = ref(''), search = ref(''), chargePage = ref(0)
const rows = ref<{ item: ChargeItem; quantity: number }[]>([])
const busy = ref(false), uncertain = ref(false), message = ref('')
const key = crypto.randomUUID()
let snapshot: BillInput | undefined
const recordQuery = useQuery({ queryKey: [...billingKey, 'records', computed(() => pet.value?.pet.id), recordPage], queryFn: () => records(pet.value!.pet.id, recordPage.value), enabled: computed(() => !!pet.value) })
const chargeQuery = useQuery({ queryKey: [...billingKey, 'charges', search, chargePage], queryFn: () => charges(search.value, chargePage.value) })
const selectedRecord = computed(() => recordQuery.data.value?.items.find(r => r.record.recordId === recordId.value))
const valid = computed(() => !!selectedRecord.value && !selectedRecord.value.existingBillId && !recordQuery.isFetching.value && !recordQuery.isError.value && rows.value.length > 0 && rows.value.length <= 100 && rows.value.every(r => Number.isSafeInteger(r.quantity) && r.quantity >= 1 && r.quantity <= 2147483647))
const estimate = computed(() => { const amount = rows.value.reduce((sum, r) => sum + cents(r.item.amount)! * BigInt(Number.isSafeInteger(r.quantity) ? r.quantity : 0), 0n); return money(`${amount / 100n}.${String(amount % 100n).padStart(2, '0')}`) })
watch(pet, () => { recordId.value = ''; recordPage.value = 0; linkPrescription.value = false })
watch(recordPage, () => { recordId.value = ''; linkPrescription.value = false })
watch(recordId, () => { linkPrescription.value = false })
function add(item: ChargeItem) { if (!rows.value.some(r => r.item.id === item.id) && rows.value.length < 100) rows.value.push({ item, quantity: 1 }) }
function findCharges() { const unchanged = search.value === query.value.trim() && chargePage.value === 0; search.value = query.value.trim(); chargePage.value = 0; if (unchanged) void chargeQuery.refetch() }
async function submit() {
  if (busy.value || (!uncertain.value && !valid.value)) return
  if (!uncertain.value) snapshot = { medicalRecordId: recordId.value, prescriptionId: linkPrescription.value ? selectedRecord.value!.record.prescriptionId : null, items: rows.value.map(r => ({ chargeItemId: r.item.id, quantity: r.quantity })) }
  busy.value = true; message.value = ''
  try {
    const bill = await createBill(snapshot!, key)
    void queryClient.invalidateQueries({ queryKey: billingKey })
    emit('saved', bill)
  } catch (error) {
    uncertain.value = isUncertain(error)
    message.value = uncertain.value ? '开单结果未确认，原输入已保留并冻结。请使用原请求安全重试，或关闭后在账单列表核实。' : errorMessage(error)
    if (!uncertain.value) { void recordQuery.refetch(); void chargeQuery.refetch() }
  } finally { busy.value = false }
}
</script>
<template>
  <ElDialog :model-value="true" title="手工开单" width="min(820px, 95vw)" top="4vh" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="emit('close')">
    <form class="billing-form billing-create" @submit.prevent="submit">
      <fieldset :disabled="busy || uncertain">
        <h3>1. 选择宠物与就诊记录</h3>
        <PatientPicker :disabled="busy || uncertain" @select="pet = $event" />
        <p v-if="pet" class="billing-selected">已选：{{ pet.pet.name }} / {{ pet.ownerName }} · {{ pet.ownerPhone }}</p>
        <AppState v-if="pet && recordQuery.isPending.value" kind="loading" title="正在读取就诊记录" />
        <AppState v-else-if="recordQuery.isError.value" kind="error" title="就诊索引读取失败" :description="errorMessage(recordQuery.error.value)" action-label="重试就诊索引" @action="recordQuery.refetch()" />
        <template v-else-if="pet">
          <p v-if="!recordQuery.data.value?.items.length" class="billing-muted">尚无正式病历，请在医生完成记录后开单。</p>
          <label v-else>就诊记录<select v-model="recordId" aria-label="就诊记录" required><option value="" disabled>请选择就诊记录</option><option v-for="r in recordQuery.data.value?.items" :key="r.record.recordId" :value="r.record.recordId" :disabled="!!r.existingBillId">{{ dateTime(r.record.visitedAt) }} · {{ r.doctorName }} · {{ r.record.recordId.slice(-8) }}{{ r.existingBillStatus ? `（${billLabels[r.existingBillStatus]}，不可重复开单）` : '' }}</option></select></label>
          <div v-if="(recordQuery.data.value?.totalPages ?? 0) > 1" class="billing-pages"><ElButton :disabled="recordPage === 0" @click="recordPage--">前一页就诊</ElButton><span>{{ recordPage + 1 }} / {{ recordQuery.data.value?.totalPages }}</span><ElButton :disabled="recordPage + 1 >= (recordQuery.data.value?.totalPages ?? 0)" @click="recordPage++">后一页就诊</ElButton></div>
          <label v-if="selectedRecord?.record.prescriptionId" class="billing-checkbox"><input v-model="linkPrescription" type="checkbox" />关联本次处方（手工选择费用，不自动计算药费）</label>
        </template>
        <h3>2. 选择收费项目</h3>
        <div class="billing-search"><label>查找收费项目<input v-model="query" maxlength="100" placeholder="输入收费项目名称" @keydown.enter.prevent="findCharges" /></label><ElButton @click="findCharges">查找项目</ElButton></div>
        <AppState v-if="chargeQuery.isPending.value" kind="loading" title="正在读取价目表" />
        <AppState v-else-if="chargeQuery.isError.value" kind="error" title="价目表读取失败" :description="errorMessage(chargeQuery.error.value)" action-label="重试价目表" @action="chargeQuery.refetch()" />
        <div v-else class="billing-charge-options"><button v-for="c in chargeQuery.data.value?.items" :key="c.id" type="button" :disabled="!c.active || rows.some(r => r.item.id === c.id)" @click="add(c)"><strong>{{ c.name }}</strong><span>{{ money(c.amount) }}{{ !c.active ? ' · 已停用' : rows.some(r => r.item.id === c.id) ? ' · 已添加' : ' ＋' }}</span></button><p v-if="!chargeQuery.data.value?.items.length" class="billing-muted">没有匹配的收费项目，请联系管理员维护价目表。</p></div>
        <div v-if="(chargeQuery.data.value?.totalPages ?? 0) > 1" class="billing-pages"><ElButton :disabled="chargePage === 0" @click="chargePage--">前一页项目</ElButton><span>{{ chargePage + 1 }} / {{ chargeQuery.data.value?.totalPages }}</span><ElButton :disabled="chargePage + 1 >= (chargeQuery.data.value?.totalPages ?? 0)" @click="chargePage++">后一页项目</ElButton></div>
        <h3>3. 核对收费明细</h3>
        <div v-for="(row, index) in rows" :key="row.item.id" class="billing-line"><div><strong>{{ row.item.name }}</strong><small>{{ money(row.item.amount) }} / 项</small></div><label>数量<input v-model.number="row.quantity" type="number" required min="1" max="2147483647" step="1" :aria-label="`${row.item.name}数量`" /></label><ElButton text type="danger" @click="rows.splice(index, 1)">移除</ElButton></div>
        <p v-if="!rows.length" class="billing-muted">请至少添加一项费用。</p>
      </fieldset>
      <p class="billing-estimate">目录预估 <strong>{{ estimate }}</strong><small>最终金额以开单后的账单为准；目录改价后按服务端最新价格生成。</small></p>
      <p v-if="message" class="billing-feedback" role="alert">{{ message }}</p>
      <div class="billing-actions"><ElButton :disabled="busy" @click="emit('close')">{{ uncertain ? '关闭并查询账单' : '返回' }}</ElButton><ElButton type="primary" native-type="submit" :loading="busy" :disabled="!uncertain && !valid">{{ uncertain ? '使用原请求安全重试' : '确认开单' }}</ElButton></div>
    </form>
  </ElDialog>
</template>
