<script setup lang="ts">
import { onErrorCaptured, onMounted, onUnmounted, ref } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import { authSession } from './app/services'
import AppState from './shared/ui/AppState.vue'

const router = useRouter()
const crashed = ref(false)
const offline = ref(!navigator.onLine)
const setNetwork = () => { offline.value = !navigator.onLine }
const unsubscribe = authSession.subscribe((_, reason) => {
  if (reason === 'expired') void router.replace({ name: 'login', query: { reason: 'expired' } })
})
onErrorCaptured(() => { crashed.value = true; return false })
onMounted(() => {
  window.addEventListener('online', setNetwork)
  window.addEventListener('offline', setNetwork)
})
onUnmounted(() => {
  unsubscribe()
  window.removeEventListener('online', setNetwork)
  window.removeEventListener('offline', setNetwork)
})
</script>

<template>
  <div v-if="offline" class="network-banner" role="status">网络已断开，请检查连接后重试。当前输入会保留。</div>
  <AppState v-if="crashed" kind="error" title="页面暂时无法显示" description="请重新加载页面后再试。" action-label="重新加载" @action="router.go(0)" />
  <RouterView v-else v-slot="{ Component }">
    <Suspense>
      <component :is="Component" />
      <template #fallback><AppState kind="loading" title="正在准备工作台" /></template>
    </Suspense>
  </RouterView>
</template>
