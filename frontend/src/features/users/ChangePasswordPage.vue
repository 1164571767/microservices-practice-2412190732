<script setup lang="ts">
import { onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { authSession } from '../../app/services'
import { errorMessage } from '../../shared/api/client'
import { useAuthStore } from '../auth/store'
import { changePassword, passwordError, uncertainWrite } from './account-api'
import './accounts.css'
const router = useRouter(), auth = useAuthStore()
const form = reactive({ oldPassword: '', password: '', confirm: '' })
const busy = ref(false), failure = ref(''), unknown = ref(false)
onUnmounted(() => { form.oldPassword = ''; form.password = ''; form.confirm = '' })
function login() { authSession.forget(); void router.replace({ name: 'login', query: { reason: unknown.value ? 'password-unconfirmed' : 'password-changed' } }) }
async function submit() {
  if (busy.value || unknown.value) return
  failure.value = passwordError(form.password, form.confirm)
  if (!form.oldPassword) failure.value = '请输入当前密码'
  else if (form.oldPassword === form.password) failure.value = '新密码不能与当前密码相同'
  if (failure.value) return
  busy.value = true
  try { await changePassword(form.oldPassword, form.password); login() }
  catch (error) {
    unknown.value = uncertainWrite(error)
    failure.value = unknown.value ? '密码修改结果未确认，请使用新密码重新登录核实；若新密码无效可尝试原密码或联系管理员。系统不会自动重复提交。' : errorMessage(error)
    if (unknown.value) { form.oldPassword = ''; form.password = ''; form.confirm = '' }
  } finally { busy.value = false }
}
</script>
<template>
  <div class="page-heading"><div><div class="page-eyebrow">ACCOUNT SECURITY</div><h1>修改密码</h1><p>管理你的登录凭据，守护工作资料。</p></div></div>
  <div class="account-security-grid"><section class="surface account-password-panel"><h2><Lock /> 登录密码</h2><p class="account-muted">当前账号：{{ auth.user?.username }} · {{ auth.user?.displayName }}</p><ElAlert v-if="failure" :title="failure" :type="unknown ? 'warning' : 'error'" :closable="false" show-icon /><ElForm label-position="top" :disabled="busy || unknown" @submit.prevent="submit"><ElFormItem label="当前密码" for="current-password" required><ElInput id="current-password" v-model="form.oldPassword" type="password" autocomplete="current-password" show-password :maxlength="72" /></ElFormItem><ElFormItem label="新密码" for="new-password" required><ElInput id="new-password" v-model="form.password" type="password" autocomplete="new-password" show-password :maxlength="72" /></ElFormItem><ElFormItem label="确认新密码" for="confirm-new-password" required><ElInput id="confirm-new-password" v-model="form.confirm" type="password" autocomplete="new-password" show-password :maxlength="72" /></ElFormItem><p class="account-muted">至少 8 位，最多 72 个 UTF-8 字节。修改后所有设备需重新登录。</p><ElButton type="primary" native-type="submit" :loading="busy" :disabled="unknown">修改密码并重新登录</ElButton></ElForm><ElButton v-if="unknown" class="account-recovery" @click="login">前往登录核实</ElButton></section><aside class="surface account-security-guide"><img src="/images/account-care.png" alt="柯基与白猫守护账号安全" /><div><span class="eyebrow">让关爱，安心相伴</span><h2>每一份资料，都值得守护</h2><p>建议使用独立的工作密码。忘记当前密码时，请联系医院管理员核实身份并重置。</p><p>密码仅用于本次提交，不保存在浏览器存储中。</p></div></aside></div>
</template>
