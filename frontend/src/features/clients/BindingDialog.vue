<script setup lang="ts">
import { ref, watch } from 'vue'
import { findCandidate, bindAccount } from './api'
import { useArchiveWrite } from './useArchiveWrite'
import { errorMessage } from '../../shared/api/client'
import type { Client, BindingCandidate } from './model'
const props = defineProps<{ client: Client }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const snapshot = { id: props.client.id, version: props.client.version }
const username = ref(''), lookupBusy = ref(false), lookupError = ref(''), confirmed = ref(false)
const candidate = ref<BindingCandidate>()
const { busy, message, uncertain, run } = useArchiveWrite()
watch(username, () => { candidate.value = undefined; confirmed.value = false; lookupError.value = '' })
async function lookup() {
  if (lookupBusy.value) return
  lookupBusy.value = true; lookupError.value = ''; candidate.value = undefined; confirmed.value = false
  const snapshot = username.value.trim()
  try { const result = await findCandidate(snapshot); if (username.value.trim() === snapshot) candidate.value = result }
  catch (e) { lookupError.value = errorMessage(e) }
  finally { lookupBusy.value = false }
}
function submit() {
  if (!candidate.value || !confirmed.value) return
  const selected = candidate.value
  void run(() => bindAccount(snapshot.id, snapshot.version, selected.username, selected.id), () => emit('saved'))
}
</script>
<template>
  <ElDialog :model-value="true" title="绑定主人登录账号" width="min(560px, 94vw)" :close-on-click-modal="false" :show-close="!busy" :close-on-press-escape="!busy" @close="emit('close')">
    <div class="archive-form">
      <p class="archive-note">绑定会授予该账号查看此客户名下宠物的权限。请先核实本人身份；联系电话允许重复，不能仅凭同号判断归属。</p>
      <div class="archive-binding-card"><strong>当前客户：{{ client.name }}</strong><span>联系电话：{{ client.phone }}</span></div>
      <form class="archive-form" @submit.prevent="lookup"><label>准确登录账号<input v-model="username" required maxlength="50" :disabled="busy || uncertain || lookupBusy" /></label><ElButton native-type="submit" :loading="lookupBusy" :disabled="busy || uncertain">查询账号</ElButton></form>
      <p v-if="lookupError" role="alert" class="archive-feedback">{{ lookupError }}</p>
      <div v-if="candidate" class="archive-binding-card" data-testid="binding-candidate"><strong>{{ candidate.displayName }} · {{ candidate.username }}</strong><span>账号手机号：{{ candidate.phone }}</span><small>账号具备 OWNER 角色，绑定时将再次校验状态和唯一归属。</small></div>
      <label v-if="candidate" class="archive-check"><input v-model="confirmed" type="checkbox" :disabled="busy || uncertain" />我已核对姓名、电话及本人身份，确认绑定</label>
      <p v-if="message" role="alert" class="archive-feedback">{{ message }}</p>
      <div class="archive-form__actions"><ElButton :disabled="busy" @click="emit('close')">{{ uncertain ? '关闭并核实' : '取消' }}</ElButton><ElButton type="primary" :loading="busy" :disabled="!candidate || !confirmed || uncertain" @click="submit">确认绑定</ElButton></div>
    </div>
  </ElDialog>
</template>
