<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { House, Calendar, FirstAidKit, User, Coin, Box, DataAnalysis, Setting, ArrowLeft, ArrowRight, SwitchButton, QuestionFilled, RefreshRight, Menu, Close, Lock } from '@element-plus/icons-vue'
import BrandMark from '../shared/ui/BrandMark.vue'
import NotificationBell from '../features/notifications/NotificationBell.vue'
import GlobalSearch from '../features/search/GlobalSearch.vue'
import { useAuthStore } from '../features/auth/store'
import { roleLabels, type Role } from '../shared/api/types'
import { errorMessage } from '../shared/api/client'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const mobileOpen = ref(false)
const helpOpen = ref(false)
const busy = ref(false)
const logoutBusy = ref(false)
const statusError = ref('')
const verifiedAt = ref(new Date())
const roles = computed(() => (Object.keys(roleLabels) as Role[]).filter(role => auth.user?.roles.includes(role)).map(role => roleLabels[role]).join(' / '))
const timeLabel = computed(() => verifiedAt.value.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', timeZone: 'Asia/Shanghai' }))
async function verify() {
  if (busy.value || logoutBusy.value) return
  busy.value = true
  statusError.value = ''
  try { await auth.verify(); verifiedAt.value = new Date() }
  catch (error) { if (auth.authenticated) statusError.value = errorMessage(error) }
  finally { busy.value = false }
}
async function logout() {
  if (logoutBusy.value) return
  logoutBusy.value = true
  let reason = 'logged-out'
  try { await auth.logout() } catch { reason = 'logout-unconfirmed' }
  finally { await router.replace({ name: 'login', query: { reason } }); logoutBusy.value = false }
}
const onFocus = () => { if (auth.authenticated) void verify() }
let timer: ReturnType<typeof setInterval> | undefined
onMounted(() => { window.addEventListener('focus', onFocus); timer = setInterval(onFocus, 60_000) })
onUnmounted(() => { window.removeEventListener('focus', onFocus); if (timer) clearInterval(timer) })
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--collapsed': collapsed, 'app-shell--mobile-open': mobileOpen }">
    <a class="skip-link" href="#workspace-main">跳转到主要内容</a>
    <button v-if="mobileOpen" class="sidebar-scrim" aria-label="关闭导航" @click="mobileOpen = false" />
    <aside class="sidebar" aria-label="主导航">
      <div class="sidebar-brand"><BrandMark :compact="collapsed" /><button class="icon-button mobile-close" aria-label="关闭导航" @click="mobileOpen = false"><Close /></button></div>
      <div class="sidebar-section-label">{{ collapsed ? '·' : '工作空间' }}</div>
      <nav>
        <RouterLink to="/" class="nav-item" :class="{ 'nav-item--active': route.name === 'home' }" :aria-current="route.name === 'home' ? 'page' : undefined" @click="mobileOpen = false"><House /><span v-if="!collapsed">医院工作台</span></RouterLink>
        <RouterLink v-if="auth.user?.roles.some((role) => ['STAFF', 'DOCTOR', 'ADMIN'].includes(role))" to="/clients" class="nav-item" :class="{ 'nav-item--active': ['clients', 'client-detail', 'pet-detail'].includes(String(route.name)) }" :aria-current="['clients', 'client-detail', 'pet-detail'].includes(String(route.name)) ? 'page' : undefined" aria-label="客户与宠物" @click="mobileOpen = false"><User /><span v-if="!collapsed">客户与宠物</span></RouterLink>
        <RouterLink v-if="auth.user?.roles.some((role) => ['STAFF', 'DOCTOR', 'ADMIN'].includes(role))" to="/appointments" class="nav-item" :class="{ 'nav-item--active': route.name === 'appointments' }" :aria-current="route.name === 'appointments' ? 'page' : undefined" aria-label="预约与接待" @click="mobileOpen = false"><Calendar /><span v-if="!collapsed">预约与接待</span></RouterLink>
        <RouterLink v-if="auth.user?.roles.some(role => ['STAFF', 'ADMIN'].includes(role))" to="/billing" class="nav-item" :class="{ 'nav-item--active': route.name === 'billing' }" :aria-current="route.name === 'billing' ? 'page' : undefined" aria-label="收费与账单" @click="mobileOpen = false"><Coin /><span v-if="!collapsed">收费与账单</span></RouterLink>
        <RouterLink v-if="auth.user?.roles.includes('ADMIN')" to="/reporting" class="nav-item" :class="{ 'nav-item--active': route.name === 'reporting' }" :aria-current="route.name === 'reporting' ? 'page' : undefined" aria-label="数据报表" @click="mobileOpen = false"><DataAnalysis /><span v-if="!collapsed">数据报表</span></RouterLink>
        <RouterLink v-if="auth.user?.roles.some(role => ['STAFF', 'DOCTOR', 'ADMIN'].includes(role))" to="/medical" class="nav-item" :class="{ 'nav-item--active': route.name === 'medical' }" :aria-current="route.name === 'medical' ? 'page' : undefined" aria-label="诊疗工作台" @click="mobileOpen = false"><FirstAidKit /><span v-if="!collapsed">诊疗工作台</span></RouterLink>
        <RouterLink v-if="auth.user?.roles.some(role => ['STAFF', 'DOCTOR', 'PHARMACY', 'ADMIN'].includes(role))" to="/pharmacy" class="nav-item" :class="{ 'nav-item--active': route.name === 'pharmacy' }" :aria-current="route.name === 'pharmacy' ? 'page' : undefined" aria-label="药房与库存" @click="mobileOpen = false"><Box /><span v-if="!collapsed">药房与库存</span></RouterLink>
        <RouterLink v-if="auth.user?.roles.includes('ADMIN')" to="/settings/users" class="nav-item" :class="{ 'nav-item--active': ['accounts', 'create-employee'].includes(String(route.name)) }" :aria-current="['accounts', 'create-employee'].includes(String(route.name)) ? 'page' : undefined" aria-label="基础设置，员工账号管理" @click="mobileOpen = false"><Setting /><span v-if="!collapsed">基础设置</span><span v-if="!collapsed" class="nav-soon">员工账号</span></RouterLink>
      </nav>
      <div class="sidebar-bottom"><div v-if="!collapsed" class="sidebar-care"><FirstAidKit /><span>让协作更从容<br /><strong>让关爱更专注</strong></span></div><button class="collapse-button" :aria-label="collapsed ? '展开导航' : '收起导航'" @click="collapsed = !collapsed"><component :is="collapsed ? ArrowRight : ArrowLeft" /><span v-if="!collapsed">收起导航</span></button></div>
    </aside>

    <div class="workspace">
      <header class="topbar">
        <button class="icon-button mobile-menu" aria-label="打开导航" @click="mobileOpen = true"><Menu /></button>
        <GlobalSearch />
        <div class="topbar-actions"><NotificationBell /><ElTooltip content="修改密码"><RouterLink to="/settings/password" class="icon-button" aria-label="修改密码"><Lock /></RouterLink></ElTooltip><ElTooltip content="工作台使用说明"><button class="icon-button" aria-label="使用说明" @click="helpOpen = true"><QuestionFilled /></button></ElTooltip><span class="topbar-divider" /><div class="user-avatar">{{ auth.user?.displayName?.slice(0, 1) || '医' }}</div><div class="user-summary"><strong>{{ auth.user?.displayName }}</strong><span>{{ roles }}</span></div><ElTooltip content="退出登录"><button class="icon-button logout-button" aria-label="退出登录" :disabled="logoutBusy" @click="logout"><SwitchButton /></button></ElTooltip></div>
      </header>
      <main id="workspace-main" class="workspace-main">
        <ElAlert v-if="statusError" :title="statusError" type="error" :closable="false" show-icon class="workspace-error" />
        <RouterView />
      </main>
      <footer class="workspace-footer"><span>爱和动物医院 · 用心守护每一个生命</span><div class="session-status"><span class="status-dot" /><span>身份已验证 · {{ timeLabel }}</span><button class="text-button" :disabled="busy || logoutBusy" aria-label="刷新身份" @click="verify"><RefreshRight :class="{ spinning: busy }" />{{ busy ? '验证中' : '刷新身份' }}</button></div></footer>
    </div>
    <ElDialog v-model="helpOpen" title="关于这个工作台" width="min(480px, 90vw)" align-center>
      <p>这里是医院员工的统一工作入口。已开放账号登录、客户与宠物档案、预约与接待；管理员可在基础设置中维护员工账号、角色及状态和重置密码。</p>
      <p>在预约与接待中办理预约、签到与叫号。接诊医生在诊疗工作台保存草稿、提交正式病历和可选处方、完成诊疗。前台与管理员可查看诊疗交接；收费与发药各自办理。</p>
      <p>管理员可在数据报表中查询门诊、收入和药品消耗；统计可能延迟或缺失，请以业务凭证核实结果。</p>
      <p>为保护诊疗资料，刷新或关闭页面会结束本机登录。可使用顶部搜索查找档案与工作入口，在通知中心查看本人通知，点击锁形按钮修改密码。</p>
      <template #footer><ElButton type="primary" @click="helpOpen = false">我知道了</ElButton></template>
    </ElDialog>
  </div>
</template>

<style scoped>
@media (max-width: 420px) {
  .topbar-actions { gap: 6px; min-width: 0; }
  .topbar-actions .user-avatar { display: none; }
  .user-summary { min-width: 0; }
  .user-summary strong { max-width: 80px; }
  .logout-button { margin-left: 0; }
}
</style>
