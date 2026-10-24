<script setup lang="ts">
import { computed, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { listPets } from '../clients/api'
import type { PetDirectory } from '../clients/model'
import { errorMessage } from '../../shared/api/client'
import AppState from '../../shared/ui/AppState.vue'
const props = defineProps<{ disabled?: boolean }>()
const emit = defineEmits<{ select: [pet: PetDirectory] }>()
const text = ref(''), query = ref(''), page = ref(0)
const result = useQuery({ queryKey: ['billing', 'patients', query, page], queryFn: () => listPets(new URLSearchParams({ query: query.value, page: String(page.value), size: '5' })), enabled: computed(() => !props.disabled) })
function search() { const unchanged = query.value === text.value.trim() && page.value === 0; query.value = text.value.trim(); page.value = 0; if (unchanged) void result.refetch() }
</script>
<template>
  <div class="billing-patient-picker">
    <div class="billing-search"><label>检索客户或宠物<input v-model="text" :disabled="disabled" maxlength="100" placeholder="客户姓名、联系电话、宠物名" @keydown.enter.prevent="search" /></label><ElButton :disabled="disabled" @click="search">查找档案</ElButton></div>
    <AppState v-if="result.isPending.value" kind="loading" title="正在读取宠物档案" />
    <AppState v-else-if="result.isError.value" kind="error" title="档案读取失败" :description="errorMessage(result.error.value)" action-label="重试档案" @action="result.refetch()" />
    <p v-else-if="!result.data.value?.items.length" class="billing-muted">没有匹配的宠物档案。</p>
    <div v-else class="billing-options"><button v-for="p in result.data.value.items" :key="p.pet.id" type="button" :disabled="disabled" @click="emit('select', p)"><strong>{{ p.pet.name }} · {{ p.pet.species }} <small v-if="p.pet.archived || p.ownerArchived">已归档</small></strong><span>{{ p.ownerName }} · {{ p.ownerPhone }}</span></button></div>
    <div v-if="(result.data.value?.totalPages ?? 0) > 1" class="billing-pages"><ElButton :disabled="disabled || page === 0" @click="page--">前一页档案</ElButton><span>{{ page + 1 }} / {{ result.data.value?.totalPages }}</span><ElButton :disabled="disabled || page + 1 >= (result.data.value?.totalPages ?? 0)" @click="page++">后一页档案</ElButton></div>
  </div>
</template>
