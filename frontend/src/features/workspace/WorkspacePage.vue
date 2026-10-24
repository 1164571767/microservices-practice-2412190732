<script setup lang="ts">
import { computed } from 'vue'
import { FirstAidKit, User, CircleCheck, Calendar, Lock, Right, Box, DataAnalysis } from '@element-plus/icons-vue'
import { useAuthStore } from '../auth/store'
import { roleLabels } from '../../shared/api/types'

const auth = useAuthStore()
const date = new Intl.DateTimeFormat('zh-CN', { dateStyle: 'full', timeZone: 'Asia/Shanghai' }).format(new Date())
const greeting = computed(() => {
  const hour = Number(new Intl.DateTimeFormat('en-GB', { hour: 'numeric', hourCycle: 'h23', timeZone: 'Asia/Shanghai' }).format(new Date()))
  return hour < 12 ? '上午好' : hour < 18 ? '下午好' : '晚上好'
})
</script>

<template>
  <div class="page-heading"><div><div class="breadcrumb">工作空间 <Right /> 医院工作台</div><h1>医院工作台</h1><p>{{ date }}<span class="heading-separator">·</span>爱和动物医院</p></div><span class="version-badge"><span class="status-dot" /> 基础工作台</span></div>
  <section class="welcome-card" aria-labelledby="welcome-heading"><div><span class="eyebrow">一个有序的开始</span><h2 id="welcome-heading">{{ greeting }}，{{ auth.user?.displayName }}。</h2><p>欢迎回到你的工作空间，让我们一起用心照护每一位伙伴。</p><div class="welcome-tags"><span><CircleCheck /> 身份验证成功</span><span><Lock /> 员工专属空间</span></div></div><div class="welcome-symbol" aria-hidden="true"><FirstAidKit /><span class="welcome-symbol__plus">+</span></div></section>
  <div class="workspace-grid">
    <section class="surface work-area"><div class="section-heading"><h2>我的工作空间</h2><span class="quiet-badge">日常协作</span></div><div class="workspace-shortcuts"><template v-if="auth.user?.roles.some(role => ['STAFF','DOCTOR','ADMIN'].includes(role))"><RouterLink to="/appointments"><Calendar /><strong>预约与接待</strong><span>预约、签到与医生候诊叫号</span><Right /></RouterLink><RouterLink to="/medical"><FirstAidKit /><strong>诊疗工作台</strong><span>恢复草稿、填写病历处方与完成诊疗</span><Right /></RouterLink><RouterLink to="/clients"><User /><strong>客户与宠物</strong><span>检索患者与健康档案</span><Right /></RouterLink></template><RouterLink to="/pharmacy"><Box /><strong>药房与库存</strong><span>处方交接、药品目录与库存查询</span><Right /></RouterLink></div><div class="work-area-note"><Lock /><span>按当前角色开放工作入口，患者数据在业务页面查询。</span></div></section>
    <aside class="identity-column"><section class="surface identity-card"><div class="section-heading"><h2>当前账号</h2><User /></div><div class="identity-profile"><div class="identity-avatar">{{ auth.user?.displayName?.slice(0, 1) || '医' }}</div><div><strong>{{ auth.user?.displayName }}</strong><span>{{ auth.user?.username }}</span></div></div><dl><div><dt>账号角色</dt><dd class="role-tags"><span v-for="role in auth.user?.roles" :key="role">{{ roleLabels[role] }}</span></dd></div><div><dt>账号状态</dt><dd class="identity-active"><span class="status-dot" /> 正常</dd></div></dl><div class="identity-note"><CircleCheck /> 账号信息来自当前登录身份</div></section><section class="workspace-note"><span class="note-icon"><Lock /></span><h3>离开之前，记得退出</h3><p>共同保护每一份诊疗资料。暂时离开电脑时，请先退出工作台。</p><span>账号与权限问题请联系医院管理员</span></section></aside>
    <RouterLink v-if="auth.user?.roles.includes('ADMIN')" to="/reporting" class="surface reporting-shortcut"><DataAnalysis /><span><strong>数据报表</strong><small>门诊工作量、收入趋势与药品消耗</small></span><Right /></RouterLink>
  </div>
</template>
<style scoped>
.reporting-shortcut{display:flex;align-items:center;gap:16px;padding:22px;margin-top:22px}.reporting-shortcut>svg{width:24px;color:var(--color-primary)}.reporting-shortcut>svg:last-child{width:16px;margin-left:auto}.reporting-shortcut span{display:flex;flex-direction:column;gap:8px;font-size:14px}.reporting-shortcut small{font-size:12px;color:var(--color-text-secondary)}
.workspace-shortcuts{display:flex;flex-direction:column;gap:16px;padding:32px 0;min-height:260px}.workspace-shortcuts a{display:grid;grid-template-columns:36px 1fr 20px;align-items:center;gap:8px 12px;padding:22px;background:var(--color-primary-tint);border:1px solid var(--color-border);border-radius:10px}.workspace-shortcuts svg:first-child{grid-row:span 2;color:var(--color-primary);width:26px}.workspace-shortcuts strong{font-size:14px}.workspace-shortcuts span{grid-column:2;font-size:12px;color:var(--color-text-secondary)}.workspace-shortcuts svg:last-child{grid-column:3;grid-row:1 / span 2;width:16px;color:var(--color-primary)}
</style>
