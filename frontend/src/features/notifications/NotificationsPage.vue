<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Bell, RefreshRight, Check } from '@element-plus/icons-vue'
import { ElPagination } from 'element-plus'
import 'element-plus/theme-chalk/el-pagination.css'
import { queryClient } from '../../app/services'
import { ApiError, errorMessage } from '../../shared/api/client'
import { useAuthStore } from '../auth/store'
import AppState from '../../shared/ui/AppState.vue'
import { listNotifications, markAllRead, markRead, notificationKey, notificationTypes } from './api'
const auth = useAuthStore()
const status = ref(''), page = ref(0), busy = ref(''), failure = ref(''), feedback = ref('')
const key = computed(() => [...notificationKey, auth.user?.id])
const list = useQuery({ queryKey: computed(() => [...key.value, 'list', status.value, page.value]), queryFn: ({ signal }) => listNotifications(status.value, page.value, signal), enabled: computed(() => auth.authenticated) })
const dateTime = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
function filter(value: string) { status.value = value; page.value = 0; failure.value = ''; feedback.value = '' }
async function read(id?: string) {
  if (busy.value) return
  busy.value = id ?? 'all'; failure.value = ''; feedback.value = ''
  try {
    if (id) { await markRead(id); feedback.value = '通知已标记为已读' }
    else { const updated = await markAllRead(); feedback.value = `已将 ${updated} 条通知标为已读` }
  } catch (error) {
    failure.value = error instanceof ApiError && (error.status >= 500 || !error.status || error.code === 'INVALID_RESPONSE') ? '已读结果未确认，请刷新通知核实；系统不会自动重复提交。' : errorMessage(error)
  } finally {
    busy.value = ''
    await queryClient.invalidateQueries({ queryKey: key.value })
  }
}
</script>
<template>
  <div class="page-heading"><div><div class="page-eyebrow">STAY CONNECTED</div><h1>通知中心</h1><p>关注属于你的业务提醒，让每一份照护及时衔接。</p></div><ElButton :icon="RefreshRight" :loading="list.isFetching.value" @click="list.refetch()">刷新通知</ElButton></div>
  <section class="surface notification-panel">
    <div class="notification-toolbar"><nav aria-label="通知状态"><button v-for="item in [{ value: '', label: '全部通知' }, { value: 'UNREAD', label: '未读' }, { value: 'READ', label: '已读' }]" :key="item.value" :aria-pressed="status === item.value" @click="filter(item.value)">{{ item.label }}</button></nav><ElButton :icon="Check" :loading="busy === 'all'" :disabled="!!busy || !list.data.value?.totalElements" @click="read()">全部标为已读</ElButton></div>
    <ElAlert v-if="failure" :title="failure" type="warning" :closable="false" show-icon />
    <p v-if="feedback" class="notification-feedback" role="status">{{ feedback }}</p>
    <AppState v-if="list.isPending.value" kind="loading" title="正在读取通知" />
    <AppState v-else-if="list.isError.value" kind="error" title="通知读取失败" :description="errorMessage(list.error.value)" action-label="重试通知" @action="list.refetch()" />
    <AppState v-else-if="!list.data.value?.items.length" title="暂无符合条件的通知" description="可切换筛选或稍后刷新，业务结果请以原始凭证为准。" :action-label="page ? '返回第一页' : ''" @action="page = 0" />
    <template v-else><article v-for="item in list.data.value.items" :key="item.id" class="notification-item" :class="{ 'is-unread': item.status === 'UNREAD' }"><div class="notification-symbol"><Bell /></div><div class="notification-content"><div class="notification-meta"><span>{{ notificationTypes[item.type] }}</span><time :datetime="item.createdAt">{{ dateTime(item.createdAt) }}</time><span>{{ item.status === 'UNREAD' ? '未读' : '已读' }}</span></div><h2>{{ item.title }}</h2><p>{{ item.body }}</p><small v-if="item.readAt">已读于 {{ dateTime(item.readAt) }}</small></div><ElButton v-if="item.status === 'UNREAD'" text type="primary" :disabled="!!busy" :loading="busy === item.id" @click="read(item.id)">标为已读</ElButton></article><div class="notification-pages"><span>共 {{ list.data.value.totalElements }} 条 · 上海时间</span><ElPagination :current-page="page + 1" :total="list.data.value.totalElements" :page-size="10" layout="prev, pager, next" @update:current-page="page = $event - 1" /></div></template>
  </section>
  <p class="notification-note">仅显示当前账号收到的站内通知。通知由业务事件生成，可能延迟或缺失；刷新仅重新查询。</p>
</template>
<style scoped>
.notification-toolbar, .notification-meta, .notification-pages { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }.notification-toolbar { padding: 20px 24px; border-bottom: 1px solid var(--color-border); }.notification-toolbar nav { display: flex; gap: 8px; }.notification-toolbar nav button { border: 0; padding: 9px 14px; border-radius: 7px; background: transparent; color: var(--color-text-secondary); cursor: pointer; }.notification-toolbar button[aria-pressed=true] { color: var(--color-primary); background: var(--color-primary-soft); }.notification-item { display: flex; align-items: flex-start; gap: 18px; padding: 24px; border-bottom: 1px solid var(--color-border); }.notification-item.is-unread { background: #f6fbfa; }.notification-symbol { padding: 12px; border-radius: 12px; background: var(--color-primary-soft); color: var(--color-primary); }.notification-symbol svg { width: 20px; display: block; }.notification-content { flex: 1; min-width: 0; }.notification-meta { justify-content: flex-start; color: var(--color-text-secondary); font-size: 11px; }.notification-item h2 { font-size: 15px; margin: 10px 0; }.notification-item p { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.9; margin: 0 0 8px; }.notification-item small { color: var(--color-text-muted); }.notification-pages { padding: 20px 24px; font-size: 12px; color: var(--color-text-secondary); }.notification-note, .notification-feedback { color: var(--color-text-secondary); font-size: 12px; line-height: 1.9; }.notification-feedback { padding: 0 24px; }.notification-panel > .el-alert { margin: 16px; width: auto; }@media(max-width:640px) { .notification-item { padding: 18px 14px; gap: 10px; flex-wrap: wrap; }.notification-item > .el-button { margin-left: 52px; }.notification-toolbar { padding: 16px; }.notification-symbol { padding: 8px; } }
</style>
