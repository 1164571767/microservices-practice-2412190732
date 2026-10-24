<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean; inverse?: boolean }>(), { compact: false, inverse: false })
</script>

<template>
  <div class="brand" :class="{ 'brand--inverse': inverse }">
    <svg class="brand__mark" viewBox="0 0 48 48" fill="none" aria-hidden="true">
      <rect width="48" height="48" rx="12" fill="currentColor" />
      <path d="M21 10h6v11h11v6H27v11h-6V27H10v-6h11z" fill="white" />
      <path d="M4 43l8-9 5 6-13 3z" fill="#78d5ce" />
    </svg>
    <span v-if="!compact" class="brand__text"><strong>爱和动物医院</strong><span>动物医院管理系统</span></span>
  </div>
</template>
