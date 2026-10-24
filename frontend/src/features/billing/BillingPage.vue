<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useRoute, useRouter } from 'vue-router'
import { Coin, Plus, RefreshRight, Document } from '@element-plus/icons-vue'
import { ElPagination } from 'element-plus'
import 'element-plus/theme-chalk/el-pagination.css'
import { bills, billingKey } from './api'
import { billLabels, money, type BillDetail, type BillStatus } from './model'
import { dateTime, isUuid, type PetDirectory } from '../clients/model'
import { useAuthStore } from '../auth/store'
import { errorMessage } from '../../shared/api/client'
import AppState from '../../shared/ui/AppState.vue'
import PatientPicker from './PatientPicker.vue'
import CreateBillDialog from './CreateBillDialog.vue'
import BillDrawer from './BillDrawer.vue'
import ChargeCatalog from './ChargeCatalog.vue'
import './billing.css'
const auth = useAuthStore(), route = useRoute(), router = useRouter()
const tab = ref<'bills' | 'catalog'>('bills'), page = ref(0), status = ref<BillStatus | ''>(''), pet = ref<PetDirectory>(), picker = ref(false), creating = ref(false), notice = ref('')
const initial = typeof route.query.petId === 'string' && isUuid(route.query.petId) ? route.query.petId : ''
const petId = ref(initial)
const selectedId = computed(() => typeof route.query.billId === 'string' && isUuid(route.query.billId) ? route.query.billId : '')
const result = useQuery({ queryKey: [...billingKey, 'bills', petId, status, page], queryFn: () => { const p = new URLSearchParams({ page: String(page.value), size: '10' }); if (petId.value) p.set('petId', petId.value); if (status.value) p.set('status', status.value); return bills(p) } })
watch([petId, status], () => { page.value = 0 })
watch(() => route.query.petId, value => { petId.value = typeof value === 'string' && isUuid(value) ? value : ''; if (pet.value?.pet.id !== petId.value) pet.value = undefined })
function choose(p: PetDirectory) { pet.value = p; petId.value = p.pet.id; picker.value = false; void router.replace({ query: { ...route.query, petId: p.pet.id } }) }
function clearPet() { pet.value = undefined; petId.value = ''; void router.replace({ query: { ...route.query, petId: undefined } }) }
function openBill(id: string) { void router.replace({ query: { ...route.query, billId: id } }) }
function created(bill: BillDetail) { creating.value = false; notice.value = '账单已生成，请核对服务端金额后进行模拟收款。'; openBill(bill.id); void result.refetch() }
</script>
<template>
  <div class="billing-page">
    <div class="page-heading"><div><div class="page-eyebrow">BILLING & PAYMENTS</div><h1>收费与账单</h1><p>手工核对每一项费用，让每一笔收付都有迹可循。</p></div><div class="billing-actions"><ElButton :icon="RefreshRight" aria-label="刷新账单列表" :loading="result.isFetching.value" @click="result.refetch()">刷新</ElButton><ElButton type="primary" :icon="Plus" @click="creating = true">手工开单</ElButton></div></div>
    <section class="billing-welcome"><div><span><Coin /> 收费工作台</span><h2>清楚计费，安心交接</h2><p>选择就诊记录与收费项目，核对账单后完成模拟收款。</p><small>手工计费 · 全额退款 · 操作留痕</small></div><img src="/images/billing-care.png" alt="柯基与小猫陪伴的温暖收费台插画" /></section>
    <p v-if="notice" role="status" class="billing-feedback billing-feedback--success">{{ notice }}</p>
    <div class="billing-grid">
<section class="surface billing-main">
<div class="billing-tabs"><button :aria-pressed="tab === 'bills'" @click="tab = 'bills'">账单记录</button><button v-if="auth.user?.roles.includes('ADMIN')" :aria-pressed="tab === 'catalog'" @click="tab = 'catalog'">收费项目管理</button></div>
      <ChargeCatalog v-if="tab === 'catalog' && auth.user?.roles.includes('ADMIN')" />
      <template v-else>
        <div class="billing-filters"><label>账单状态<select v-model="status" aria-label="账单状态"><option value="">全部状态</option><option v-for="(label, value) in billLabels" :key="value" :value="value">{{ label }}</option></select></label><div><span class="billing-filter-label">客户 / 宠物</span><ElButton @click="picker = !picker">{{ pet ? `${pet.pet.name} / ${pet.ownerName}` : petId ? '已筛选宠物' : '查找客户与宠物' }}</ElButton><button v-if="petId" class="text-button" @click="clearPet">清除筛选</button></div></div>
        <div v-if="picker" class="billing-filter-picker"><PatientPicker @select="choose" /></div>
        <AppState v-if="result.isPending.value" kind="loading" title="正在读取账单" />
        <AppState v-else-if="result.isError.value" kind="error" title="账单列表读取失败" :description="errorMessage(result.error.value)" action-label="重试账单列表" @action="result.refetch()" />
        <AppState v-else-if="!result.data.value?.items.length" kind="empty" title="暂无匹配的账单" description="可调整状态或宠物筛选；需要收费时，选择已有就诊记录手工开单。" />
        <div v-else class="billing-table-wrap"><table class="billing-table"><thead><tr><th>账单 / 开单时间</th><th>宠物 / 客户</th><th>金额</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="row in result.data.value.items" :key="row.bill.id" :data-bill-id="row.bill.id"><td><strong :title="row.bill.id">{{ row.bill.id.slice(-8).toUpperCase() }}</strong><small>{{ dateTime(row.bill.createdAt) }}</small></td><td><strong>{{ row.patient?.petName ?? '历史宠物' }}<span class="billing-species">{{ row.patient?.species }}</span></strong><small>{{ row.patient?.ownerName ?? '联系资料不可用' }}<template v-if="row.patient"> · {{ row.patient.ownerPhone }}</template></small></td><td class="billing-amount">{{ money(row.bill.totalAmount) }}</td><td><span :class="`billing-status billing-status--${row.bill.status.toLowerCase()}`">{{ billLabels[row.bill.status] }}</span></td><td><ElButton text type="primary" @click="openBill(row.bill.id)">{{ row.bill.status === 'UNPAID' ? '核对与收款' : '查看详情' }}</ElButton></td></tr></tbody></table></div>
        <div v-if="result.data.value" class="billing-pages"><span>共 {{ result.data.value.totalElements }} 张账单</span><ElPagination :current-page="page + 1" :page-size="10" :total="result.data.value.totalElements" layout="prev, pager, next" @update:current-page="page = $event - 1" /></div>
      </template>
    </section><aside class="surface billing-guide"><h2><Document /> 收费交接提示</h2><ol><li><strong>按就诊记录开单</strong><p>同次就诊仅保留一张有效账单，费用逐项手工选择。</p></li><li><strong>核对后模拟收款</strong><p>核对宠物、客户与账单明细。收款仅用于本系统演示。</p></li><li><strong>保留历史与原因</strong><p>未收款可作废重开；已收款支持全额退款。退款后不可重复开单。</p></li></ol><p class="billing-guide-note">费用与发药独立处理。退款不自动退库；通知或报表异常不应改变已确认的账单结果。</p></aside>
</div>
    <CreateBillDialog v-if="creating" @close="creating = false; result.refetch()" @saved="created" />
    <BillDrawer v-if="selectedId" :id="selectedId" :key="selectedId" @close="router.replace({ query: { ...route.query, billId: undefined } })" @changed="result.refetch()" />
  </div>
</template>
