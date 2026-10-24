<script setup lang="ts">
import { computed, nextTick, onUnmounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, Right, CircleCheck, Plus, InfoFilled, Setting, Phone } from '@element-plus/icons-vue'
import type { FormInstance, FormRules, InputInstance } from 'element-plus'
import { useAuthStore } from '../auth/store'
import { roleLabels, type UserProfile } from '../../shared/api/types'
import { createEmployee } from './api'
import { classifyCreateFailure, employeeRoleOptions, type CreateEmployeeRequest, type SubmissionFailure } from './model'
import './users.css'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const nameInputRef = ref<InputInstance>()
const feedbackRef = ref<HTMLElement>()
const initial = () => ({ username: '', displayName: '', phone: '', password: '', confirmPassword: '', roles: [] as CreateEmployeeRequest['roles'] })
const form = reactive(initial())
const submitting = ref(false)
const result = ref<UserProfile | null>(null)
const failure = ref<SubmissionFailure | null>(null)
const offline = ref(!navigator.onLine)
const privileged = computed(() => auth.user?.roles.includes('ADMIN') === true)
const locked = computed(() => submitting.value || failure.value?.uncertain === true || result.value !== null)
const rules: FormRules = {
  username: [
    { required: true, whitespace: true, message: '请输入登录账号', trigger: 'blur' },
    { validator: (_, value: string, done) => {
      const name = value.trim()
      done(name.length >= 3 && name.length <= 50 && /^[A-Za-z0-9_.-]+$/.test(name)
        ? undefined : new Error('账号须为 3～50 位字母、数字或 _ . -'))
    }, trigger: 'blur' },
  ],
  displayName: [{ required: true, whitespace: true, message: '请输入员工姓名', trigger: 'blur' }, { max: 100, message: '姓名最多 100 个字符', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }, { validator: (_, value: string, done) => done(/^\+?[0-9]{6,20}$/.test(value.trim()) ? undefined : new Error('请输入 6～20 位数字，可带国家码 +')), trigger: 'blur' }],
  password: [{ required: true, message: '请设置初始密码', trigger: 'blur' }, { min: 8, max: 72, message: '初始密码须为 8～72 个字符', trigger: 'blur' }],
  confirmPassword: [{ required: true, message: '请再次输入初始密码', trigger: 'blur' }, { validator: (_, value: string, done) => done(value === form.password ? undefined : new Error('两次输入的密码不一致')), trigger: 'blur' }],
  roles: [{ type: 'array', required: true, min: 1, message: '请至少选择一个员工角色', trigger: 'change' }],
}
let mounted = true
let validating = false
const updateNetwork = () => { offline.value = !navigator.onLine }
window.addEventListener('online', updateNetwork)
window.addEventListener('offline', updateNetwork)
watch(privileged, (allowed) => {
  // A cleared session is handled by the global expiry/logout flow. Only a
  // still-authenticated account losing ADMIN should go to the forbidden page.
  if (auth.authenticated && !allowed) void router.replace('/forbidden')
})
onUnmounted(() => {
  mounted = false
  form.password = ''; form.confirmPassword = ''
  window.removeEventListener('online', updateNetwork)
  window.removeEventListener('offline', updateNetwork)
})

async function reset() {
  Object.assign(form, initial())
  result.value = null
  failure.value = null
  formRef.value?.clearValidate()
  await nextTick()
  nameInputRef.value?.focus()
}

async function submit() {
  if (locked.value || validating || !privileged.value || offline.value) return
  // Guard validation without disabling focused inputs or moving keyboard focus.
  validating = true
  failure.value = null
  try {
    if (!await formRef.value?.validate().catch(() => false)) return
    submitting.value = true
    const request: CreateEmployeeRequest = {
      username: form.username, displayName: form.displayName, phone: form.phone,
      password: form.password, roles: [...form.roles],
    }
    const created = await createEmployee(request)
    if (mounted) {
      result.value = created
      form.password = ''; form.confirmPassword = ''
    }
  } catch (error) {
    if (mounted) {
      failure.value = classifyCreateFailure(error)
      await nextTick()
      feedbackRef.value?.focus({ preventScroll: true })
      feedbackRef.value?.scrollIntoView({ block: 'nearest' })
    }
  } finally { validating = false; if (mounted) submitting.value = false }
}
</script>

<template>
  <div class="page-heading employee-heading">
    <div>
      <div class="breadcrumb">基础设置 <Right /> 员工账号 <Right /> 开通账号</div>
      <h1>开通员工账号</h1>
      <p>为医院同事建立工作身份，按实际职责分配权限。</p>
    </div>
    <ElButton @click="router.push('/settings/users')">返回账号管理</ElButton>
  </div>

  <section v-if="result" class="surface employee-success" aria-labelledby="create-success-title" data-testid="create-success">
    <span class="employee-success__icon"><CircleCheck /></span>
    <span class="eyebrow">账号已开通</span>
    <h2 id="create-success-title">{{ result.displayName }}，欢迎加入爱和</h2>
    <p>员工现在可以使用新账号与设置的初始密码登录工作台。</p>
    <dl class="employee-result">
      <div><dt>登录账号</dt><dd>{{ result.username }}</dd></div>
      <div><dt>员工姓名</dt><dd>{{ result.displayName }}</dd></div>
      <div><dt>联系电话</dt><dd>{{ result.phone }}</dd></div>
      <div><dt>账号角色</dt><dd class="role-tags"><span v-for="role in result.roles" :key="role">{{ roleLabels[role] }}</span></dd></div>
      <div><dt>账号状态</dt><dd class="identity-active"><span class="status-dot" /> 正常</dd></div>
    </dl>
    <div class="employee-result-note"><Lock /> 初始密码已从当前表单清除，请通过合适的方式告知员工。</div>
    <div class="employee-success__actions"><ElButton type="primary" :icon="Plus" @click="reset">继续开通账号</ElButton><ElButton @click="router.push('/')">返回工作台</ElButton></div>
  </section>

  <div v-else class="employee-grid">
    <section class="surface employee-form-panel" aria-labelledby="employee-form-heading">
      <div class="section-heading"><h2 id="employee-form-heading"><User /> 员工账号资料</h2><span class="employee-required-note">带 * 的项目为必填</span></div>
      <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="locked" class="employee-form" @submit.prevent="submit">
        <div v-if="failure" ref="feedbackRef" tabindex="-1" class="employee-feedback"><ElAlert :title="failure.title" :description="failure.message" :type="failure.uncertain ? 'warning' : 'error'" :closable="false" show-icon data-testid="create-error" /></div>
        <ElAlert v-if="offline" title="网络已断开，恢复连接后才能提交" type="warning" :closable="false" show-icon class="employee-feedback" />
        <div class="employee-section-label"><span>01</span><h3>基本信息</h3></div>
        <div class="employee-fields">
          <ElFormItem label="登录账号" prop="username" for="employee-username">
            <ElInput id="employee-username" ref="nameInputRef" v-model="form.username" placeholder="例如 dr.wang" autocomplete="off" :prefix-icon="User" :maxlength="50" />
            <span class="employee-field-hint">3～50 位字母、数字或 _ . -，大小写视为同一账号</span>
          </ElFormItem>
          <ElFormItem label="员工姓名" prop="displayName" for="employee-name"><ElInput id="employee-name" v-model="form.displayName" placeholder="请输入工作台显示的姓名" autocomplete="off" :maxlength="100" /></ElFormItem>
          <ElFormItem label="手机号" prop="phone" for="employee-phone"><ElInput id="employee-phone" v-model="form.phone" placeholder="请输入员工联系电话" inputmode="tel" autocomplete="off" :prefix-icon="Phone" :maxlength="21" /><span class="employee-field-hint">用于员工账号，同一手机号只能开通一个账号</span></ElFormItem>
        </div>
        <div class="employee-section-label"><span>02</span><h3>登录凭据</h3></div>
        <div class="employee-fields">
          <ElFormItem label="初始密码" prop="password" for="employee-password"><ElInput id="employee-password" v-model="form.password" type="password" placeholder="设置 8～72 位初始密码" autocomplete="new-password" :prefix-icon="Lock" show-password :maxlength="72" /></ElFormItem>
          <ElFormItem label="确认初始密码" prop="confirmPassword" for="employee-confirm"><ElInput id="employee-confirm" v-model="form.confirmPassword" type="password" placeholder="再次输入初始密码" autocomplete="new-password" :prefix-icon="Lock" show-password :maxlength="72" /></ElFormItem>
        </div>
        <div class="employee-section-label"><span>03</span><h3>工作角色</h3><span>可选择多个角色</span></div>
        <ElFormItem prop="roles" class="employee-roles-item">
          <div class="employee-role-options" role="group" aria-label="工作角色">
            <label v-for="option in employeeRoleOptions" :key="option.value" class="employee-role-option" :class="{ 'is-selected': form.roles.includes(option.value) }">
              <input v-model="form.roles" type="checkbox" :value="option.value" :disabled="locked" @change="formRef?.validateField('roles').catch(() => undefined)" />
              <span><strong>{{ option.label }}</strong><span>{{ option.description }}</span></span>
              <Setting v-if="option.value === 'ADMIN'" /><CircleCheck v-else />
            </label>
          </div>
        </ElFormItem>
        <div class="employee-form-actions"><span><Lock /> 仅管理员可开通员工账号</span><ElButton type="primary" native-type="submit" :loading="submitting" :disabled="locked || offline || !privileged" :icon="Plus">{{ submitting ? '正在开通' : failure?.uncertain ? '请先核实开通结果' : '开通账号' }}</ElButton></div>
      </ElForm>
      <div v-if="failure?.uncertain" class="employee-uncertain-actions"><span>已核实上一笔结果后，可清空资料重新填写。</span><ElButton @click="reset">已核实，重新填写</ElButton></div>
    </section>
    <aside class="employee-guidance">
      <section class="surface"><div class="section-heading"><h2>开通前，请确认</h2><InfoFilled /></div><ol><li><strong>按职责选择角色</strong><p>一个账号可以承担多个职责。管理员角色拥有账号与基础配置权限，请按实际工作需要分配。</p></li><li><strong>核对账号与手机号</strong><p>两者都不能与已有账号重复。员工账号的手机号规则与客户联系电话不同。</p></li><li><strong>妥善交接初始密码</strong><p>密码不会显示在创建结果中，也不会保存在浏览器存储。</p></li></ol></section>
      <div class="workspace-note"><span class="note-icon"><User /></span><h3>这里开通的是员工账号</h3><p>客户档案、宠物档案与主人账号绑定在客户与宠物模块中办理。</p><RouterLink to="/settings/users">查看员工账号与权限</RouterLink></div>
    </aside>
  </div>
</template>
