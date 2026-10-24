<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { Search, ArrowRight } from '@element-plus/icons-vue'
import type { InputInstance } from 'element-plus'
import { useAuthStore } from '../auth/store'
import { listClients, listPets } from '../clients/api'
import { errorMessage } from '../../shared/api/client'
import { canSearchArchives, searchShortcuts } from './model'
const auth = useAuthStore(), route = useRoute()
const opened = ref(false), text = ref(''), query = ref(''), input = ref<InputInstance>()
let debounce: ReturnType<typeof setTimeout> | undefined
const roles = computed(() => auth.user?.roles ?? [])
const archiveAllowed = computed(() => canSearchArchives(roles.value))
const enabled = computed(() => opened.value && archiveAllowed.value && !!query.value && auth.authenticated)
const params = computed(() => new URLSearchParams({ query: query.value, page: '0', size: '5' }))
const key = computed(() => ['global-search', auth.user?.id, roles.value.join(','), query.value])
const clients = useQuery({ queryKey: computed(() => [...key.value, 'clients']), queryFn: ({ signal }) => listClients(params.value, signal), enabled })
const pets = useQuery({ queryKey: computed(() => [...key.value, 'pets']), queryFn: ({ signal }) => listPets(params.value, signal), enabled })
const shortcuts = computed(() => searchShortcuts(roles.value, text.value))
watch(text, () => { clearTimeout(debounce); query.value = ''; debounce = setTimeout(() => { query.value = text.value.trim() }, 300) })
watch(opened, open => { if (!open) { clearTimeout(debounce); text.value = ''; query.value = '' } })
watch(() => route.fullPath, () => { opened.value = false })
function shortcut(event: KeyboardEvent) { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); opened.value = !opened.value } }
onMounted(() => window.addEventListener('keydown', shortcut))
onUnmounted(() => { window.removeEventListener('keydown', shortcut); clearTimeout(debounce) })
</script>
<template>
  <button class="global-search global-search-trigger" aria-label="打开全局搜索" aria-haspopup="dialog" @click="opened = true"><Search /><span>搜索客户、宠物或工作入口</span><kbd>Ctrl K</kbd></button>
  <ElDialog v-model="opened" title="全局搜索" width="min(680px, 94vw)" top="8vh" class="global-search-dialog" @opened="input?.focus()">
<ElInput ref="input" v-model="text" aria-label="全局搜索关键词" :placeholder="archiveAllowed ? '输入客户姓名、联系电话或宠物名字' : '搜索工作入口'" :prefix-icon="Search" :maxlength="100" clearable /><p class="search-scope">{{ archiveAllowed ? '检索客户、宠物（含归档）并跳转档案；预约、病历等请进入对应工作台查询。' : '当前角色可搜索有权访问的工作入口。' }}</p>
    <section v-if="shortcuts.length" class="search-group"><h2>工作入口</h2><div class="search-shortcuts"><RouterLink v-for="item in shortcuts" :key="item.path" :to="item.path" @click="opened = false">{{ item.title }}<ArrowRight /></RouterLink></div></section>
    <template v-if="enabled">
<section class="search-group" aria-label="客户搜索结果"><h2>客户档案 <small v-if="clients.data.value && !clients.isError.value">共 {{ clients.data.value.totalElements }} 份 · 最多显示 5 份</small></h2><p v-if="clients.isPending.value" role="status">正在搜索客户…</p><div v-else-if="clients.isError.value" role="alert" class="search-error">客户搜索失败：{{ errorMessage(clients.error.value) }} <ElButton text type="primary" @click="clients.refetch()">重试客户搜索</ElButton></div><p v-else-if="!clients.data.value?.items.length">未找到客户档案</p><RouterLink v-for="client in clients.isError.value ? [] : clients.data.value?.items" :key="client.id" class="search-result" :to="`/clients/${client.id}`" @click="opened = false"><span><strong>{{ client.name }}</strong><small>{{ client.phone }}{{ client.archived ? ' · 已归档' : '' }}</small></span><ArrowRight /></RouterLink></section>
      <section class="search-group" aria-label="宠物搜索结果"><h2>宠物档案 <small v-if="pets.data.value && !pets.isError.value">共 {{ pets.data.value.totalElements }} 份 · 最多显示 5 份</small></h2><p v-if="pets.isPending.value" role="status">正在搜索宠物…</p><div v-else-if="pets.isError.value" role="alert" class="search-error">宠物搜索失败：{{ errorMessage(pets.error.value) }} <ElButton text type="primary" @click="pets.refetch()">重试宠物搜索</ElButton></div><p v-else-if="!pets.data.value?.items.length">未找到宠物档案</p><RouterLink v-for="entry in pets.isError.value ? [] : pets.data.value?.items" :key="entry.pet.id" class="search-result" :to="`/pets/${entry.pet.id}`" @click="opened = false"><span><strong>{{ entry.pet.name }}</strong><small>{{ entry.pet.species }} · {{ entry.ownerName }} · {{ entry.ownerPhone }}{{ entry.pet.archived || entry.ownerArchived ? ' · 已归档' : '' }}</small></span><ArrowRight /></RouterLink></section>
</template>
    <p v-if="!shortcuts.length && !enabled" class="search-scope">{{ text.trim() ? '没有匹配的工作入口' : '输入关键词开始搜索' }}</p>
  </ElDialog>
</template>
<style>
.global-search-trigger { cursor:pointer;text-align:left;font-family:inherit }.global-search-trigger:focus-visible { outline:2px solid var(--color-primary) }.search-scope { color:var(--color-text-secondary);font-size:12px;line-height:1.8 }.search-group { margin-top:24px }.search-group h2 { font-size:13px;display:flex;align-items:center;gap:10px;flex-wrap:wrap }.search-group h2 small { color:var(--color-text-muted);font-size:11px;font-weight:400 }.search-group p { color:var(--color-text-secondary);font-size:12px }.search-shortcuts { display:flex;flex-wrap:wrap;gap:8px }.search-shortcuts a { padding:9px 12px;background:var(--color-primary-tint);border:1px solid var(--color-border);border-radius:6px;font-size:12px;display:flex;align-items:center;gap:8px }.search-shortcuts svg,.search-result svg { width:14px;color:var(--color-primary) }.search-result { display:flex;align-items:center;justify-content:space-between;padding:12px 8px;border-bottom:1px solid var(--color-border) }.search-result:hover { background:var(--color-primary-tint) }.search-result span { min-width:0;overflow-wrap:anywhere }.search-result strong { font-size:13px }.search-result small { display:block;color:var(--color-text-secondary);font-size:11px;margin-top:6px }.search-error { font-size:12px;color:var(--color-danger) }.global-search-dialog { max-height:84dvh;overflow:auto }@media(max-width:640px) { .global-search.global-search-trigger { display:flex;width:32px;min-width:32px;padding:7px;background:transparent;border:0 }.global-search-trigger span,.global-search-trigger kbd { display:none } }
</style>
