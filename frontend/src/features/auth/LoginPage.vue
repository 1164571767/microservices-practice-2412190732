<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock, Right, CircleCheck, Calendar, FirstAidKit, OfficeBuilding } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import BrandMark from '../../shared/ui/BrandMark.vue'
import { useAuthStore } from './store'
import { safeReturnPath } from '../../app/router'
import { errorMessage } from '../../shared/api/client'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const formRef = ref<FormInstance>()
const form = reactive({ username: '', password: '' })
const loading = ref(false)
const error = ref('')
const rules: FormRules = {
  username: [{ required: true, whitespace: true, message: '请输入员工账号', trigger: 'blur' }, { max: 50, message: '账号最多 50 个字符', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { max: 72, message: '密码最多 72 个字符', trigger: 'blur' }],
}
const notice = computed(() => {
  if (route.query.reason === 'expired') return '登录已失效，请重新登录后继续。'
  if (route.query.reason === 'logout-unconfirmed') return '已退出本机登录，但服务端退出未确认。请联系管理员核查。'
  if (route.query.reason === 'logged-out') return '已安全退出，感谢今天的用心照护。'
  if (route.query.reason === 'password-changed') return '密码已修改，所有旧会话已撤销，请使用新密码登录。'
  if (route.query.reason === 'password-unconfirmed') return '密码修改结果未确认，请先使用新密码登录核实；必要时尝试原密码或联系管理员。'
  return ''
})
async function submit() {
  if (loading.value) return
  error.value = ''
  if (!await formRef.value?.validate().catch(() => false)) return
  loading.value = true
  try {
    await auth.login({ username: form.username.trim(), password: form.password })
    form.password = ''
    await router.replace(safeReturnPath(route.query.redirect))
  } catch (cause) { error.value = errorMessage(cause) }
  finally { loading.value = false }
}
</script>

<template>
  <div class="login-page">
    <section class="login-story" aria-label="爱和动物医院">
      <BrandMark />
      <div class="login-story__content">
        <span class="eyebrow"><span class="status-dot" /> 用心照护 · 有序协作</span>
        <h1>让每一次照护，<br />都井然有序。</h1>
        <p class="login-story__lead">从到院的第一声问候，<br />到每一份细致的诊疗记录。</p>
        <div class="care-illustration" aria-hidden="true">
          <div class="care-orbit care-orbit--one" /><div class="care-orbit care-orbit--two" />
          <div class="care-core"><FirstAidKit /><span>以关爱为中心</span></div>
          <div class="care-node care-node--appointment"><Calendar /><span>有序接待</span><CircleCheck /></div>
          <div class="care-node care-node--record"><OfficeBuilding /><span>协同照护</span><CircleCheck /></div>
          <span class="care-dot care-dot--one" /><span class="care-dot care-dot--two" />
        </div>
        <div class="login-story__footnote"><span class="small-line" /> 让协作更从容，让关爱更专注</div>
      </div>
      <div class="login-story__bottom">PETCARE <span>·</span> 员工工作空间</div>
    </section>

    <main id="main-content" class="login-main">
      <div class="login-topnote"><span class="status-dot" /> 医院员工专属入口</div>
      <section class="login-form-panel" aria-labelledby="login-heading">
        <div class="login-form-panel__icon"><Lock /></div>
        <span class="eyebrow login-eyebrow">WELCOME BACK</span>
        <h2 id="login-heading">欢迎回到爱和</h2>
        <p class="login-intro">登录你的员工账号，开始今天的照护。</p>
        <ElAlert v-if="notice" :title="notice" :type="route.query.reason === 'logged-out' ? 'success' : 'warning'" :closable="false" show-icon class="login-notice" />
        <ElAlert v-if="error" :title="error" type="error" :closable="false" show-icon class="login-notice" data-testid="login-error" />
        <ElForm ref="formRef" :model="form" :rules="rules" label-position="top" :disabled="loading" @submit.prevent="submit">
          <ElFormItem label="员工账号" prop="username" for="username">
            <ElInput id="username" v-model="form.username" name="username" autocomplete="username" placeholder="请输入员工账号" :prefix-icon="User" size="large" :maxlength="50" />
          </ElFormItem>
          <ElFormItem label="登录密码" prop="password" for="password">
            <ElInput id="password" v-model="form.password" name="password" type="password" autocomplete="current-password" placeholder="请输入登录密码" :prefix-icon="Lock" show-password size="large" :maxlength="72" />
          </ElFormItem>
          <div class="login-form-help"><span>账号由医院管理员统一开通</span><ElTooltip content="请联系医院管理员确认账号或重置密码" placement="top"><button type="button" class="text-button">无法登录？</button></ElTooltip></div>
          <ElButton class="login-submit" type="primary" native-type="submit" size="large" :loading="loading">{{ loading ? '正在验证身份' : '登录工作台' }}<Right v-if="!loading" /></ElButton>
        </ElForm>
        <div class="login-security"><CircleCheck /><span>为保护诊疗资料，刷新或关闭页面后需重新登录。</span></div>
      </section>
      <footer class="login-footer"><span>爱和动物医院</span><span class="footer-separator" /> 用心守护每一个生命</footer>
    </main>
  </div>
</template>
