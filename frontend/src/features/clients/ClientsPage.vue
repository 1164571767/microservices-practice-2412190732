<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { Plus, Search, RefreshRight, User, FirstAidKit, ArrowRight } from '@element-plus/icons-vue'
import { clientKey, listClients, listPets } from './api'
import { ageLabel, type Client, type Pet } from './model'
import { errorMessage } from '../../shared/api/client'
import AppState from '../../shared/ui/AppState.vue'
import ArchiveEditor from './ArchiveEditor.vue'
import './clients.css'
const router = useRouter(), route = useRoute()
const view = computed(() => route.query.view === 'pets' ? 'pets' : 'clients')
const search = ref(''), queryText = ref(''), filter = ref('active'), page = ref(0), editor = ref(false)
const params = computed(() => {
  const p = new URLSearchParams({ query: queryText.value, page: String(page.value), size: '10' })
  if (filter.value !== 'all') p.set('archived', String(filter.value === 'archived'))
  return p
})
// URLSearchParams hashes as an empty object; use its serialized value as the cache key.
const paramsKey = computed(() => params.value.toString())
const clientQuery = useQuery({ queryKey: [...clientKey, 'clients', paramsKey], queryFn: () => listClients(params.value), enabled: computed(() => view.value === 'clients') })
const petQuery = useQuery({ queryKey: [...clientKey, 'pets', paramsKey], queryFn: () => listPets(params.value), enabled: computed(() => view.value === 'pets') })
const pending = computed(() => view.value === 'clients' ? clientQuery.isPending.value : petQuery.isPending.value)
const failed = computed(() => view.value === 'clients' ? clientQuery.isError.value : petQuery.isError.value)
const error = computed(() => view.value === 'clients' ? clientQuery.error.value : petQuery.error.value)
const total = computed(() => view.value === 'clients' ? clientQuery.data.value?.totalElements : petQuery.data.value?.totalElements)
const pages = computed(() => view.value === 'clients' ? clientQuery.data.value?.totalPages : petQuery.data.value?.totalPages)
function refresh() { return view.value === 'clients' ? clientQuery.refetch() : petQuery.refetch() }
function changeView(next: string) { page.value = 0; void router.replace({ query: { view: next } }) }
function submitSearch() {
  const unchanged = page.value === 0 && queryText.value === search.value.trim()
  page.value = 0; queryText.value = search.value.trim()
  if (unchanged) void refresh()
}
function saved(result: Client | Pet) { editor.value = false; void router.push(`/clients/${result.id}`) }
</script>
<template>
  <div class="archive-page">
    <header class="archive-heading"><div><div class="eyebrow">PATIENT RELATIONSHIPS</div><h1>客户与宠物</h1><p>从一份档案开始，持续守护每一次成长。</p></div><ElButton type="primary" @click="editor = true"><Plus />新建客户</ElButton></header>
    <div class="archive-overview"><div class="archive-overview__icon"><User /></div><div><strong>先建档，后绑定</strong><p>联系电话允许重复，登录账号可在核实身份后由管理员绑定。</p></div><span class="archive-overview__hint">档案归档后保留历史</span></div>
    <section class="archive-card archive-directory">
      <div class="archive-directory__top"><div class="archive-tabs" role="group" aria-label="档案类型"><button :class="{ active: view === 'clients' }" :aria-pressed="view === 'clients'" @click="changeView('clients')"><User />客户档案</button><button :class="{ active: view === 'pets' }" :aria-pressed="view === 'pets'" @click="changeView('pets')"><FirstAidKit />宠物档案</button></div><button class="icon-button" aria-label="刷新档案列表" @click="refresh"><RefreshRight /></button></div>
      <form class="archive-search" @submit.prevent="submitSearch"><label class="archive-search__input"><Search /><span class="sr-only">搜索客户或宠物</span><input v-model="search" maxlength="100" placeholder="搜索客户姓名、联系电话或宠物名字" /></label><label class="archive-filter"><span class="sr-only">档案状态</span><select v-model="filter" @change="page = 0"><option value="active">正常档案</option><option value="archived">已归档</option><option value="all">全部状态</option></select></label><ElButton native-type="submit" type="primary">搜索</ElButton></form>
      <AppState v-if="pending" kind="loading" title="正在读取档案" />
      <AppState v-else-if="failed" kind="error" title="档案读取失败" :description="errorMessage(error)" action-label="重新读取" @action="refresh" />
      <AppState v-else-if="total === 0" title="没有找到档案" description="可以调整检索条件，或新建一份客户档案。" />
      <template v-else>
        <p class="archive-table-hint">左右滑动查看完整档案，点击姓名进入详情</p>
        <div class="archive-table-wrap">
<table v-if="view === 'clients'" class="archive-table"><thead><tr><th>客户</th><th>联系电话</th><th>账号绑定</th><th>档案状态</th><th><span class="sr-only">操作</span></th></tr></thead><tbody><tr v-for="client in clientQuery.data.value?.items" :key="client.id"><td><RouterLink :to="`/clients/${client.id}`" class="archive-person"><span class="archive-avatar">{{ client.name.slice(0, 1) }}</span><strong>{{ client.name }}</strong></RouterLink></td><td>{{ client.phone }}</td><td><span class="archive-badge" :class="{ 'archive-badge--muted': !client.userId }">{{ client.userId ? '已绑定' : '待绑定' }}</span></td><td><span class="archive-badge" :class="{ 'archive-badge--muted': client.archived }">{{ client.archived ? '已归档' : '正常' }}</span></td><td><RouterLink :to="`/clients/${client.id}`" class="archive-link">查看档案<ArrowRight /></RouterLink></td></tr></tbody></table>
          <table v-else class="archive-table"><thead><tr><th>宠物</th><th>年龄 / 体重</th><th>所属客户</th><th>档案状态</th><th><span class="sr-only">操作</span></th></tr></thead><tbody><tr v-for="entry in petQuery.data.value?.items" :key="entry.pet.id"><td><RouterLink :to="`/pets/${entry.pet.id}`" class="archive-person"><span class="archive-avatar archive-avatar--pet"><FirstAidKit /></span><div><strong>{{ entry.pet.name }}</strong><small>{{ entry.pet.species }}</small></div></RouterLink></td><td>{{ ageLabel(entry.pet) }}<small>{{ entry.pet.weightKg }} kg</small></td><td><RouterLink :to="`/clients/${entry.pet.ownerId}`">{{ entry.ownerName }}</RouterLink><small>{{ entry.ownerPhone }}</small></td><td><span class="archive-badge" :class="{ 'archive-badge--muted': entry.pet.archived || entry.ownerArchived }">{{ entry.pet.archived ? '已归档' : entry.ownerArchived ? '客户已归档' : '正常' }}</span></td><td><RouterLink :to="`/pets/${entry.pet.id}`" class="archive-link">查看档案<ArrowRight /></RouterLink></td></tr></tbody></table>
        </div>
        <div class="archive-pagination"><span>共 {{ total }} 份档案</span><div><ElButton :disabled="page === 0" @click="page--">上一页</ElButton><span>{{ page + 1 }} / {{ pages || 1 }}</span><ElButton :disabled="page + 1 >= (pages || 1)" @click="page++">下一页</ElButton></div></div>
      </template>
    </section>
    <ArchiveEditor v-if="editor" kind="client" @close="editor = false" @saved="saved" />
  </div>
</template>
