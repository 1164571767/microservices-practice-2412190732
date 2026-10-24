<script setup lang="ts">
import { useRouter } from 'vue-router'
import AppState from './AppState.vue'
const router = useRouter()
</script>

<template>
  <AppState kind="error" title="你没有访问此页面的权限" description="当前角色未获此功能的访问权限。如需调整权限，请联系医院管理员。" action-label="返回工作台" @action="router.replace('/')" />
</template>
