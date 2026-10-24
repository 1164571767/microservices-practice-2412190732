<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useQuery } from '@tanstack/vue-query'
import { ArrowLeft, FirstAidKit, Plus } from '@element-plus/icons-vue'
import { clientKey, getClient, getPet, vaccinations, allergies, summaries } from './api'
import { ageLabel, dateTime, currentRecords, severityLabels, type Vaccination, type Allergy } from './model'
import { errorMessage } from '../../shared/api/client'
import AppState from '../../shared/ui/AppState.vue'
import ArchiveEditor from './ArchiveEditor.vue'
import HealthEditor from './HealthEditor.vue'
import OperationHistory from './OperationHistory.vue'
import './clients.css'
const route = useRoute(), id = computed(() => String(route.params.id))
const editor = ref<'pet' | 'archive-pet'>(), tab = ref<'vaccinations' | 'allergies' | 'medical'>('vaccinations')
const healthEditor = ref<{ kind: 'vaccinations' | 'allergies'; record?: Vaccination | Allergy }>()
const showHistory = ref(false)
const query = useQuery({ queryKey: [...clientKey, 'pet', id], queryFn: () => getPet(id.value) })
const { data: pet, isPending, isError, error } = query
const ownerId = computed(() => pet.value?.ownerId ?? '')
const clientQuery = useQuery({ queryKey: [...clientKey, 'client', ownerId], queryFn: () => getClient(ownerId.value), enabled: computed(() => !!ownerId.value) })
const vaccineQuery = useQuery({ queryKey: [...clientKey, 'vaccinations', id], queryFn: () => vaccinations(id.value), enabled: computed(() => !!pet.value) })
const allergyQuery = useQuery({ queryKey: [...clientKey, 'allergies', id], queryFn: () => allergies(id.value), enabled: computed(() => !!pet.value) })
const medicalQuery = useQuery({ queryKey: [...clientKey, 'medical-summary', id], queryFn: () => summaries(id.value), enabled: computed(() => !!pet.value && tab.value === 'medical') })
const writable = computed(() => !!pet.value && !pet.value.archived && !!clientQuery.data.value && !clientQuery.data.value.archived)
const vaccines = computed(() => showHistory.value ? vaccineQuery.data.value ?? [] : currentRecords(vaccineQuery.data.value ?? []))
const allergyRecords = computed(() => showHistory.value ? allergyQuery.data.value ?? [] : currentRecords(allergyQuery.data.value ?? []))
const activeAllergies = computed(() => currentRecords(allergyQuery.data.value ?? []))
const allergyText = computed(() => activeAllergies.value.map((a) => `${a.allergen}（${severityLabels[a.severity]}）`).join('、'))
const superseded = computed(() => new Set([...(vaccineQuery.data.value ?? []), ...(allergyQuery.data.value ?? [])].map((r) => r.supersedesId)))
const activeQuery = computed(() => tab.value === 'vaccinations' ? vaccineQuery : tab.value === 'allergies' ? allergyQuery : medicalQuery)
const healthEmpty = computed(() => tab.value === 'vaccinations' ? vaccines.value.length === 0 : tab.value === 'allergies' ? allergyRecords.value.length === 0 : !medicalQuery.data.value?.length)
</script>
<template>
  <div class="archive-page">
<RouterLink :to="pet ? `/clients/${pet.ownerId}` : '/clients?view=pets'" class="archive-back"><ArrowLeft />{{ clientQuery.data.value?.name ? `${clientQuery.data.value.name}的客户档案` : '客户与宠物' }}</RouterLink>
    <AppState v-if="isPending" kind="loading" title="正在读取宠物档案" /><AppState v-else-if="isError" kind="error" title="宠物档案读取失败" :description="errorMessage(error)" action-label="重新读取" @action="query.refetch()" />
    <template v-else-if="pet">
      <header class="archive-heading"><div class="archive-person"><span class="archive-avatar archive-avatar--large archive-avatar--pet"><FirstAidKit /></span><div><h1>{{ pet.name }}</h1><p>{{ pet.species }} · {{ ageLabel(pet) }} · {{ pet.weightKg }} kg</p></div></div><div class="archive-actions"><RouterLink :to="`/appointments?petId=${pet.id}`" class="archive-link">查看预约 / 办理预约</RouterLink><ElButton v-if="writable" @click="editor = 'pet'">编辑宠物资料</ElButton></div></header>
      <div v-if="pet.archived || clientQuery.data.value?.archived" class="archive-notice" role="status"><strong>{{ pet.archived ? '宠物已归档' : '所属客户已归档' }}</strong> · {{ pet.archiveReason || clientQuery.data.value?.archiveReason }}<small>历史资料仍可查阅，禁止新增预约与健康资料。</small></div>
      <div v-if="allergyQuery.isError.value" class="archive-notice archive-notice--danger" role="alert">过敏资料读取失败，当前无法确认过敏风险。<button class="text-button" @click="allergyQuery.refetch()">重新读取</button></div>
      <div v-else-if="activeAllergies.length" class="archive-notice archive-notice--danger"><strong>过敏提示</strong> · {{ allergyText }}</div>
      <div class="archive-detail-grid">
<aside class="archive-card archive-contact"><h2>宠物资料</h2><dl><dt>出生日期</dt><dd>{{ pet.birthDate }} <span v-if="pet.birthDateEstimated" class="archive-badge">估算</span></dd><dt>体重</dt><dd>{{ pet.weightKg }} kg</dd><dt>所属客户</dt><dd><RouterLink v-if="clientQuery.data.value" :to="`/clients/${pet.ownerId}`" class="archive-link">{{ clientQuery.data.value.name }}</RouterLink><span v-else>{{ clientQuery.isError.value ? '客户资料读取失败' : '读取中…' }}</span></dd><dt>联系电话</dt><dd>{{ clientQuery.data.value?.phone || '—' }}</dd></dl><button v-if="clientQuery.isError.value" class="text-button" @click="clientQuery.refetch()">重新读取客户资料</button><details><summary>档案编号</summary><code>{{ pet.id }}</code></details><button v-if="writable" class="archive-danger-link" @click="editor = 'archive-pet'">归档宠物</button></aside>
        <section class="archive-card archive-health">
<div class="archive-directory__top"><div class="archive-tabs" role="group" aria-label="健康资料类型"><button :class="{ active: tab === 'vaccinations' }" :aria-pressed="tab === 'vaccinations'" @click="tab = 'vaccinations'">疫苗记录</button><button :class="{ active: tab === 'allergies' }" :aria-pressed="tab === 'allergies'" @click="tab = 'allergies'">过敏资料</button><button :class="{ active: tab === 'medical' }" :aria-pressed="tab === 'medical'" @click="tab = 'medical'">诊疗摘要</button></div></div>
          <div class="archive-health__tools"><label v-if="tab !== 'medical'" class="archive-check"><input v-model="showHistory" type="checkbox" />显示更正前记录</label><ElButton v-if="writable && tab !== 'medical'" type="primary" @click="healthEditor = { kind: tab }"><Plus />{{ tab === 'vaccinations' ? '新增疫苗记录' : '新增过敏记录' }}</ElButton></div>
          <AppState v-if="activeQuery.isPending.value" kind="loading" title="正在读取健康资料" /><AppState v-else-if="activeQuery.isError.value" kind="error" title="健康资料读取失败" :description="errorMessage(activeQuery.error.value)" action-label="重新读取" @action="activeQuery.refetch()" /><AppState v-else-if="healthEmpty" :title="tab === 'medical' ? '暂无诊疗摘要' : '暂无已登记记录'" description="未登记不代表已确认没有相关病史。" />
          <ul v-else class="archive-health-list">
            <template v-if="tab === 'vaccinations'"><li v-for="record in vaccines" :key="record.id" :class="{ 'archive-record--superseded': superseded.has(record.id) }"><div class="archive-section-title"><h3>{{ record.vaccineName }}</h3><span v-if="superseded.has(record.id)" class="archive-badge archive-badge--muted">已被更正</span><button v-else-if="writable" class="text-button" @click="healthEditor = { kind: 'vaccinations', record }">留痕更正</button></div><p>接种：{{ record.administeredOn }}<span v-if="record.nextDueOn"> · 下次应接种：{{ record.nextDueOn }}</span></p><p v-if="record.note">{{ record.note }}</p><small>操作人：{{ record.recordedByName || '历史记录，操作人未知' }} · {{ dateTime(record.createdAt) }}</small><p v-if="record.correctionReason" class="archive-correction">更正原因：{{ record.correctionReason }}</p></li></template>
            <template v-else-if="tab === 'allergies'"><li v-for="record in allergyRecords" :key="record.id" :class="{ 'archive-record--superseded': superseded.has(record.id) }"><div class="archive-section-title"><h3>{{ record.allergen }} <span class="archive-badge" :class="{ 'archive-badge--danger': record.severity === 'SEVERE' }">{{ severityLabels[record.severity] }}</span></h3><span v-if="superseded.has(record.id)" class="archive-badge archive-badge--muted">已被更正</span><button v-else-if="writable" class="text-button" @click="healthEditor = { kind: 'allergies', record }">留痕更正</button></div><p v-if="record.note">{{ record.note }}</p><small>操作人：{{ record.recordedByName || '历史记录，操作人未知' }} · {{ dateTime(record.createdAt) }}</small><p v-if="record.correctionReason" class="archive-correction">更正原因：{{ record.correctionReason }}</p></li></template>
            <template v-else><li v-for="record in medicalQuery.data.value" :key="record.recordId"><strong>{{ record.diagnosis }}</strong><p>{{ dateTime(record.visitedAt) }}</p><small>病历编号：{{ record.recordId }}</small></li></template>
          </ul>
        </section>
</div>
      <OperationHistory :id="pet.id" :key="pet.id" kind="pet" />
      <ArchiveEditor v-if="editor" :key="editor" :kind="editor" :pet="pet" @close="editor = undefined" @saved="editor = undefined" />
      <HealthEditor v-if="healthEditor" :kind="healthEditor.kind" :pet-id="pet.id" :record="healthEditor.record" @close="healthEditor = undefined" @saved="healthEditor = undefined" />
    </template>
  </div>
</template>
