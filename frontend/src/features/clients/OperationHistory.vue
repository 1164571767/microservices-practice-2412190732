<script setup lang="ts">
import { ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { clientKey, operations } from './api'
import { dateTime, operationLabels } from './model'
import AppState from '../../shared/ui/AppState.vue'
import { errorMessage } from '../../shared/api/client'
const props = defineProps<{ kind: 'client' | 'pet'; id: string }>()
const page = ref(0)
const query = useQuery({ queryKey: [...clientKey, 'operations', props.kind, props.id, page], queryFn: () => operations(props.kind, props.id, page.value) })
const { data, isPending, isError, error } = query
</script>
<template>
  <section class="archive-card">
<h2>操作留痕</h2><AppState v-if="isPending" kind="loading" title="读取操作记录" /><AppState v-else-if="isError" kind="error" title="操作记录读取失败" :description="errorMessage(error)" action-label="重试" @action="query.refetch()" />
    <ul v-else class="archive-timeline"><li v-for="item in data?.items" :key="item.id"><div><strong>{{ operationLabels[item.action] ?? item.action }}</strong><span>{{ item.actorName }} · {{ dateTime(item.createdAt) }}</span></div><p v-if="item.reason">{{ item.reason }}</p><details><summary>查看变更快照</summary><div v-if="item.beforeJson"><small>变更前</small><pre>{{ item.beforeJson }}</pre></div><small>变更后</small><pre>{{ item.afterJson }}</pre></details></li><li v-if="!data?.items.length" class="archive-muted">暂无留痕记录；历史档案不会补造操作人。</li></ul>
    <div v-if="data && data.totalPages > 1" class="archive-pagination"><ElButton :disabled="page === 0" @click="page--">上一页</ElButton><span>{{ page + 1 }} / {{ data.totalPages }}</span><ElButton :disabled="page + 1 >= data.totalPages" @click="page++">下一页</ElButton></div>
  </section>
</template>
