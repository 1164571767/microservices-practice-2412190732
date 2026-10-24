<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Bell } from '@element-plus/icons-vue'
import { useAuthStore } from '../auth/store'
import { notificationKey, unreadCount } from './api'
const auth = useAuthStore()
const count = useQuery({ queryKey: computed(() => [...notificationKey, auth.user?.id, 'count']), queryFn: ({ signal }) => unreadCount(signal), enabled: computed(() => auth.authenticated), refetchInterval: 60_000 })
const label = computed(() => count.isError.value ? '通知中心，未读数暂不可用' : count.isPending.value ? '通知中心，正在读取未读数' : `通知中心，${count.data.value ?? 0} 条未读`)
</script>
<template>
  <RouterLink to="/notifications" class="icon-button notification-bell" :aria-label="label" :title="count.isError.value ? '通知数读取失败，打开通知中心重试' : '通知中心'"><Bell /><span v-if="count.isError.value" class="notification-count">!</span><span v-else-if="count.data.value" class="notification-count">{{ count.data.value > 99 ? '99+' : count.data.value }}</span></RouterLink>
</template>
<style scoped>
.notification-bell { position: relative; text-decoration: none; }.notification-count { position: absolute; right: -6px; top: -4px; padding: 0 4px; border-radius: 9px; min-width: 16px; font-size: 10px; line-height: 16px; background: var(--color-danger); color: white; text-align: center; }
</style>
