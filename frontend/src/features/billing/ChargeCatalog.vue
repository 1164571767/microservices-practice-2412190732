<script setup lang="ts">
import { ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElPagination } from 'element-plus'
import { billingKey, charges, saveCharge } from './api'
import { cents, isUncertain, money, type ChargeItem } from './model'
import { errorMessage } from '../../shared/api/client'
import { queryClient } from '../../app/services'
import AppState from '../../shared/ui/AppState.vue'
const text = ref(''), query = ref(''), page = ref(0), editing = ref(false), id = ref<string>(), name = ref(''), amount = ref(''), active = ref(true), busy = ref(false), uncertain = ref(false), message = ref('')
const result = useQuery({ queryKey: [...billingKey, 'catalog', query, page], queryFn: () => charges(query.value, page.value) })
function search() { const unchanged = query.value === text.value.trim() && page.value === 0; query.value = text.value.trim(); page.value = 0; if (unchanged) void result.refetch() }
function edit(item?: ChargeItem) { id.value = item?.id; name.value = item?.name ?? ''; amount.value = item ? String(item.amount) : ''; active.value = item?.active ?? true; editing.value = true; message.value = ''; uncertain.value = false }
async function submit() {
  if (busy.value || uncertain.value) return
  const parsed = cents(amount.value)
  if (!name.value.trim() || parsed === undefined || parsed > 99999999999n) { message.value = '请输入项目名称和 0～999999999.99 的金额，最多两位小数。'; return }
  busy.value = true; message.value = ''
  try {
    await saveCharge({ name: name.value.trim(), amount: Number(amount.value), active: active.value }, id.value)
    editing.value = false
    void queryClient.invalidateQueries({ queryKey: billingKey })
  } catch (error) {
    uncertain.value = isUncertain(error)
    message.value = uncertain.value ? '项目保存结果未确认，输入已保留。请关闭后刷新价目表核实，勿直接重复保存。' : errorMessage(error)
  } finally { busy.value = false }
}
</script>
<template>
  <div>
    <div class="billing-search billing-catalog-toolbar"><label>项目名称<input v-model="text" maxlength="100" placeholder="搜索收费项目" @keydown.enter.prevent="search" /></label><ElButton @click="search">查询项目</ElButton><ElButton type="primary" @click="edit()">新增收费项目</ElButton></div>
    <AppState v-if="result.isPending.value" kind="loading" title="正在读取收费项目" />
    <AppState v-else-if="result.isError.value" kind="error" title="收费项目读取失败" :description="errorMessage(result.error.value)" action-label="重试收费项目" @action="result.refetch()" />
    <AppState v-else-if="!result.data.value?.items.length" kind="empty" title="暂无匹配的收费项目" description="调整查询条件，或新增收费项目后开始手工开单。" />
    <div v-else class="billing-table-wrap"><table class="billing-table"><thead><tr><th>项目名称</th><th>目录单价</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="c in result.data.value.items" :key="c.id"><td><strong>{{ c.name }}</strong></td><td>{{ money(c.amount) }}</td><td><span class="billing-status" :class="c.active ? 'billing-status--paid' : 'billing-status--void'">{{ c.active ? '启用中' : '已停用' }}</span></td><td><ElButton text type="primary" @click="edit(c)">编辑项目</ElButton></td></tr></tbody></table></div>
    <div v-if="result.data.value" class="billing-pages"><span>共 {{ result.data.value.totalElements }} 项</span><ElPagination :current-page="page + 1" :page-size="10" :total="result.data.value.totalElements" layout="prev, pager, next" @update:current-page="page = $event - 1" /></div>
    <ElDialog v-if="editing" :model-value="true" :title="id ? '编辑收费项目' : '新增收费项目'" width="min(480px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" @close="editing = false">
      <form class="billing-form" @submit.prevent="submit"><fieldset :disabled="busy || uncertain"><label>收费项目名称<input v-model="name" required maxlength="100" /></label><label>单价（元）<input v-model="amount" required type="text" inputmode="decimal" placeholder="例如 50.00" /></label><label v-if="id" class="billing-checkbox"><input v-model="active" type="checkbox" />启用此收费项目</label><p class="billing-muted">停用后不可用于新开单；改名、改价不影响历史账单。</p></fieldset><p v-if="message" role="alert" class="billing-feedback">{{ message }}</p><div class="billing-actions"><ElButton :disabled="busy" @click="editing = false">{{ uncertain ? '关闭并核实价目表' : '返回' }}</ElButton><ElButton type="primary" native-type="submit" :loading="busy" :disabled="uncertain">保存收费项目</ElButton></div></form>
    </ElDialog>
  </div>
</template>
