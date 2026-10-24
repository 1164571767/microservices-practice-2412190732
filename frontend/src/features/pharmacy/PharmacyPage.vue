<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { isUuid } from '../clients/model'
import { useQuery } from '@tanstack/vue-query'
import { ElPagination } from 'element-plus'
import 'element-plus/theme-chalk/el-pagination.css'
import { Box, RefreshRight, Plus, Document } from '@element-plus/icons-vue'
import { drugs, inventory, tasks, pharmacyKey } from './api'
import type { Drug } from './model'
import { useAuthStore } from '../auth/store'
import { errorMessage } from '../../shared/api/client'
import { dateTime } from '../clients/model'
import { queryClient } from '../../app/services'
import AppState from '../../shared/ui/AppState.vue'
import DrugEditor from './DrugEditor.vue'
import InventoryDrawer from './InventoryDrawer.vue'
import PrescriptionDrawer from './PrescriptionDrawer.vue'
import './pharmacy.css'
const auth = useAuthStore(), route = useRoute(), router = useRouter()
const writable = computed(() => !!auth.user?.roles.some(r => ['PHARMACY', 'ADMIN'].includes(r)))
const tab = ref<'tasks' | 'inventory' | 'catalog'>(writable.value ? 'tasks' : 'inventory')
const text = ref(''), query = ref(''), page = ref(0), low = ref(false), status = ref('PENDING'), editing = ref(false), selectedDrug = ref<Drug>(), stockId = ref(''), prescriptionId = ref(''), notice = ref('')
watch(() => route.query.prescriptionId, value => { prescriptionId.value = writable.value && isUuid(value) ? value : '' }, { immediate: true })
function closePrescription() { prescriptionId.value = ''; void router.replace({ query: { ...route.query, prescriptionId: undefined } }); refresh() }
const drugList = useQuery({ queryKey: [...pharmacyKey, 'drugs', query, page], queryFn: () => drugs(query.value, page.value), enabled: computed(() => tab.value === 'catalog') })
const inventoryList = useQuery({ queryKey: [...pharmacyKey, 'inventory', query, page, low], queryFn: () => inventory(query.value, page.value, low.value), enabled: computed(() => tab.value === 'inventory') })
const taskList = useQuery({ queryKey: [...pharmacyKey, 'tasks', query, page, status], queryFn: () => tasks(query.value, page.value, status.value), enabled: computed(() => tab.value === 'tasks' && writable.value) })
const current = computed(() => tab.value === 'catalog' ? drugList : tab.value === 'inventory' ? inventoryList : taskList)
watch(tab, () => { text.value = ''; query.value = ''; page.value = 0 })
watch([low, status], () => { page.value = 0 })
function search() { const same = query.value === text.value.trim() && page.value === 0; query.value = text.value.trim(); page.value = 0; if (same) void current.value.refetch() }
function edit(drug?: Drug) { selectedDrug.value = drug; editing.value = true }
function refresh() { void queryClient.invalidateQueries({ queryKey: pharmacyKey }) }
function saved() { editing.value = false; notice.value = '药品保存已确认。'; refresh() }
</script>
<template>
  <div class="pharmacy-page">
<div class="page-heading"><div><div class="page-eyebrow">PHARMACY & INVENTORY</div><h1>药房与库存</h1><p>核对每一张处方，守护每一次安心用药。</p></div><div class="pharmacy-actions"><ElButton :icon="RefreshRight" :loading="current.isFetching.value" @click="current.refetch()">刷新列表</ElButton><ElButton v-if="writable && tab === 'catalog'" type="primary" :icon="Plus" @click="edit()">新增药品</ElButton></div></div>
    <section class="pharmacy-welcome"><div><span><Box /> 药房工作台</span><h2>有序储备，安心交付</h2><p>从库存预警到处方交接，让每一次药品流转清晰可查。</p><small>整单发药 · 库存预警 · 调整留痕</small></div><img src="/images/pharmacy-care.png" alt="" /></section><p v-if="notice" role="status" class="pharmacy-alert is-success">{{ notice }}</p>
    <div class="pharmacy-grid">
<section class="surface pharmacy-main">
<nav class="pharmacy-tabs" aria-label="药房模块页签"><button v-if="writable" :aria-pressed="tab === 'tasks'" @click="tab = 'tasks'">处方发药</button><button :aria-pressed="tab === 'inventory'" @click="tab = 'inventory'">库存与预警</button><button :aria-pressed="tab === 'catalog'" @click="tab = 'catalog'">药品目录</button></nav>
    <form class="pharmacy-filters" @submit.prevent="search"><label>{{ tab === 'tasks' ? '宠物 / 客户姓名' : '药品名称 / 规格' }}<input v-model="text" maxlength="100" :placeholder="tab === 'tasks' ? '搜索宠物或客户姓名' : '搜索药品名称或规格'" /></label><label v-if="tab === 'tasks'">发药状态<select v-model="status" aria-label="发药状态"><option value="PENDING">待发药</option><option value="DISPENSED">已发药</option></select></label><label v-if="tab === 'inventory'" class="pharmacy-check"><input v-model="low" type="checkbox" />仅看低库存</label><ElButton native-type="submit" type="primary" plain>查询</ElButton></form>
    <AppState v-if="current.isPending.value" kind="loading" title="正在读取药房数据" /><AppState v-else-if="current.isError.value" kind="error" title="药房数据读取失败" :description="errorMessage(current.error.value)" action-label="重试列表" @action="current.refetch()" /><AppState v-else-if="!current.data.value?.items.length" kind="empty" :title="tab === 'tasks' ? '暂无匹配的处方' : tab === 'inventory' && low ? '暂无匹配的低库存药品' : '暂无匹配的药品'" description="可调整查询条件后重试。" />
    <div v-else class="pharmacy-table-wrap">
<table v-if="tab === 'tasks'" class="pharmacy-table"><thead><tr><th>宠物 / 客户</th><th>处方 / 开方时间</th><th>医生</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="t in taskList.data.value?.items" :key="t.prescriptionId" :data-prescription-id="t.prescriptionId"><td><strong>{{ t.patient?.petName ?? '患者资料缺失' }}</strong><small>{{ t.patient?.ownerName ?? '请联系管理员核实' }}<template v-if="t.patient"> · {{ t.patient.species }}</template></small></td><td><strong :title="t.prescriptionId">{{ t.prescriptionId.slice(-8).toUpperCase() }}</strong><small>{{ dateTime(t.issuedAt) }}</small></td><td>{{ t.doctorName }}</td><td><span class="pharmacy-badge" :class="t.dispensed ? 'is-success' : 'is-warning'">{{ t.dispensed ? '已发药' : '待发药' }}</span></td><td><ElButton text type="primary" @click="prescriptionId = t.prescriptionId">{{ t.dispensed ? '查看凭证' : '核对发药' }}</ElButton></td></tr></tbody></table>
    <table v-else-if="tab === 'inventory'" class="pharmacy-table"><thead><tr><th>药品 / 规格</th><th>当前库存</th><th>预警阈值</th><th>库存状态</th><th>操作</th></tr></thead><tbody><tr v-for="i in inventoryList.data.value?.items" :key="i.drugId" :data-drug-id="i.drugId"><td><strong>{{ i.drugName }}</strong><small>{{ i.specification }}</small></td><td class="pharmacy-quantity">{{ i.quantity }} <small>{{ i.unit }}</small></td><td>{{ i.lowStockThreshold }} {{ i.unit }}</td><td><span class="pharmacy-badge" :class="i.lowStock ? 'is-warning' : 'is-success'">{{ i.lowStock ? '库存偏低' : '库存充足' }}</span></td><td><ElButton text type="primary" @click="stockId = i.drugId">{{ writable ? '调整 / 流水' : '查看流水' }}</ElButton></td></tr></tbody></table>
    <table v-else class="pharmacy-table"><thead><tr><th>药品 / 规格</th><th>单位</th><th>预警阈值</th><th>目录状态</th><th>操作</th></tr></thead><tbody><tr v-for="d in drugList.data.value?.items" :key="d.id" :data-drug-id="d.id"><td><strong>{{ d.name }}</strong><small>{{ d.specification }}</small></td><td>{{ d.unit }}</td><td>{{ d.lowStockThreshold }}</td><td><span class="pharmacy-badge" :class="d.active ? 'is-success' : 'is-muted'">{{ d.active ? '启用中' : '已停用' }}</span></td><td><ElButton v-if="writable" text type="primary" @click="edit(d)">编辑药品</ElButton><span v-else class="pharmacy-muted">只读</span></td></tr></tbody></table>
</div>
    <div v-if="!current.isError.value && current.data.value" class="pharmacy-pages"><span>共 {{ current.data.value.totalElements }} 条记录</span><ElPagination :current-page="page + 1" :page-size="10" :total="current.data.value.totalElements" layout="prev, pager, next" @update:current-page="page = $event - 1" /></div>
</section><aside class="surface pharmacy-guide"><h2><Document /> 用药与库存交接</h2><ol><li><strong>先核对，再交付</strong><p>核对患者、过敏提示和处方用法；按实物单位与规格配药。</p></li><li><strong>整单处理，结果可查</strong><p>每张处方只发药一次；结果未知时先核实，安全重试沿用原幂等键。</p></li><li><strong>每次调整都有原因</strong><p>入库、盘点和报损填写调整原因，保留数量与操作人记录。</p></li></ol><p class="pharmacy-muted">库存低于阈值时预警。发药不要求先付款，退款不自动退库。</p><p v-if="!writable" class="pharmacy-muted">当前为临床只读模式，药房或管理员可维护和发药。</p></aside>
</div>
    <DrugEditor v-if="editing && writable" :drug="selectedDrug" @close="editing = false; refresh()" @saved="saved" /><InventoryDrawer v-if="stockId" :id="stockId" :key="stockId" :writable="writable" @close="stockId = ''; refresh()" @changed="refresh" /><PrescriptionDrawer v-if="prescriptionId && writable" :id="prescriptionId" :key="prescriptionId" @close="closePrescription" @changed="refresh" />
  </div>
</template>
