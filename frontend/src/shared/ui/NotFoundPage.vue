<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppState from './AppState.vue'
const router = useRouter()
</script>

<template>
  <main class="standalone-state"><AppState title="这个页面暂时找不到了" description="请检查地址，或返回工作台继续。" action-label="返回工作台" @action="router.replace('/')" /></main>
</template>
