<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { Plus, ArrowLeft, FirstAidKit } from '@element-plus/icons-vue'
import { clientKey, getClient, listPets } from './api'
import { ageLabel, dateTime, type Client, type Pet } from './model'
import { errorMessage } from '../../shared/api/client'
import { useAuthStore } from '../auth/store'
import AppState from '../../shared/ui/AppState.vue'
import ArchiveEditor from './ArchiveEditor.vue'
import BindingDialog from './BindingDialog.vue'
import OperationHistory from './OperationHistory.vue'
import './clients.css'
const route = useRoute(), router = useRouter(), auth = useAuthStore()
const id = computed(() => String(route.params.id)), page = ref(0)
const editor = ref<'client' | 'pet' | 'archive-client'>(), binding = ref(false)
const query = useQuery({ queryKey: [...clientKey, 'client', id], queryFn: () => getClient(id.value) })
const { data: client, isPending, isError, error } = query
const petQuery = useQuery({ queryKey: [...clientKey, 'client-pets', id, page], queryFn: () => listPets(new URLSearchParams({ ownerId: id.value, page: String(page.value), size: '10' })), enabled: computed(() => !!client.value) })
function saved(result: Client | Pet) { const kind = editor.value; editor.value = undefined; if (kind === 'pet') void router.push(`/pets/${result.id}`) }
</script>
<template>
  <div class="archive-page">
<RouterLink to="/clients" class="archive-back"><ArrowLeft />客户与宠物</RouterLink>
    <AppState v-if="isPending" kind="loading" title="正在读取客户档案" /><AppState v-else-if="isError" kind="error" title="客户档案读取失败" :description="errorMessage(error)" action-label="重新读取" @action="query.refetch()" />
    <template v-else-if="client">
      <header class="archive-heading"><div class="archive-person"><span class="archive-avatar archive-avatar--large">{{ client.name.slice(0, 1) }}</span><div><h1>{{ client.name }}</h1><p>客户档案 · {{ client.userId ? '已绑定登录账号' : '待绑定登录账号' }}</p></div></div><div class="archive-actions"><ElButton v-if="!client.archived" @click="editor = 'client'">编辑资料</ElButton><ElButton v-if="!client.archived" type="primary" @click="editor = 'pet'"><Plus />新增宠物</ElButton></div></header>
      <div v-if="client.archived" class="archive-notice" role="status"><strong>客户已归档</strong> · {{ client.archiveReason }}<small>{{ dateTime(client.archivedAt!) }} · 历史仍可查阅，禁止新增预约与健康资料。</small></div>
      <div class="archive-detail-grid">
<aside class="archive-card archive-contact"><h2>联系资料</h2><dl><dt>联系电话</dt><dd>{{ client.phone }}</dd><dt>联系地址</dt><dd>{{ client.address || '未填写' }}</dd><dt>建档时间</dt><dd>{{ dateTime(client.createdAt) }}</dd><dt>登录账号</dt><dd><span class="archive-badge" :class="{ 'archive-badge--muted': !client.userId }">{{ client.userId ? '已绑定' : '待绑定' }}</span></dd></dl><ElButton v-if="auth.user?.roles.includes('ADMIN') && !client.userId && !client.archived" @click="binding = true">绑定主人账号</ElButton><p v-if="!client.userId" class="archive-muted">未绑定也可正常建档及就诊。绑定需由管理员核实身份。</p><details><summary>档案编号</summary><code>{{ client.id }}</code><template v-if="client.userId"><p>登录账号编号</p><code>{{ client.userId }}</code></template></details><button v-if="!client.archived" class="archive-danger-link" @click="editor = 'archive-client'">归档客户</button></aside>
        <section class="archive-card">
<div class="archive-section-title"><h2>名下宠物</h2><span>{{ petQuery.data.value?.totalElements ?? '—' }} 只</span></div>
          <AppState v-if="petQuery.isPending.value" kind="loading" title="读取宠物档案" /><AppState v-else-if="petQuery.isError.value" kind="error" title="名下宠物读取失败" :description="errorMessage(petQuery.error.value)" action-label="重新读取" @action="petQuery.refetch()" /><AppState v-else-if="!petQuery.data.value?.items.length" title="尚未登记宠物" description="完成客户建档后，可以继续添加第一只宠物。" />
          <div v-else class="archive-pet-grid"><RouterLink v-for="entry in petQuery.data.value.items" :key="entry.pet.id" :to="`/pets/${entry.pet.id}`" class="archive-pet-card"><span class="archive-avatar archive-avatar--pet"><FirstAidKit /></span><h3>{{ entry.pet.name }}</h3><span>{{ entry.pet.species }} · {{ ageLabel(entry.pet) }}</span><p>{{ entry.pet.weightKg }} kg</p><small>{{ entry.pet.archived ? '已归档' : client.archived ? '客户已归档' : '查看健康档案 →' }}</small></RouterLink></div>
          <div v-if="petQuery.data.value && petQuery.data.value.totalPages > 1" class="archive-pagination"><ElButton :disabled="page === 0" @click="page--">上一页</ElButton><span>{{ page + 1 }} / {{ petQuery.data.value.totalPages }}</span><ElButton :disabled="page + 1 >= petQuery.data.value.totalPages" @click="page++">下一页</ElButton></div>
        </section>
</div>
      <OperationHistory :id="client.id" :key="client.id" kind="client" />
      <ArchiveEditor v-if="editor" :key="editor" :kind="editor" :client="client" :owner-id="client.id" @close="editor = undefined" @saved="saved" />
      <BindingDialog v-if="binding" :client="client" @close="binding = false" @saved="binding = false" />
    </template>
  </div>
</template>
