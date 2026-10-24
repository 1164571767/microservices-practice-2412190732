<script setup lang="ts">
import { Box, Warning, Loading } from '@element-plus/icons-vue'
withDefaults(defineProps<{
  kind?: 'loading' | 'empty' | 'error'
  title: string
  description?: string
  actionLabel?: string
}>(), { kind: 'empty', description: '', actionLabel: '' })
defineEmits<{ action: [] }>()
</script>

<template>
  <div class="app-state" :class="`app-state--${kind}`" :role="kind === 'error' ? 'alert' : 'status'" :aria-busy="kind === 'loading'">
    <div class="app-state__icon"><component :is="kind === 'loading' ? Loading : kind === 'error' ? Warning : Box" :class="{ spinning: kind === 'loading' }" /></div>
    <h2>{{ title }}</h2>
    <p v-if="description">{{ description }}</p>
    <ElButton v-if="actionLabel" @click="$emit('action')">{{ actionLabel }}</ElButton>
    <slot />
  </div>
</template>
