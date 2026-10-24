<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElPagination } from 'element-plus'
import 'element-plus/theme-chalk/el-pagination.css'
import { Plus, RefreshRight, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '../auth/store'
import { queryClient } from '../../app/services'
import { errorMessage } from '../../shared/api/client'
import { employeeRoles, roleLabels } from '../../shared/api/types'
import AppState from '../../shared/ui/AppState.vue'
import { employees, updateEmployee, resetPassword, accountHistory, accountKey, passwordError, uncertainWrite, type Employee, type EmployeeUpdate } from './account-api'
import './accounts.css'
const auth = useAuthStore()
const search = ref(''), query = ref(''), status = ref(''), role = ref(''), page = ref(0)
const params = computed(() => new URLSearchParams({ query: query.value, ...(status.value ? { status: status.value } : {}), ...(role.value ? { role: role.value } : {}), page: String(page.value), size: '10' }))
const key = computed(() => [...accountKey, auth.user?.id, auth.user?.roles.join(',')])
const list = useQuery({ queryKey: computed(() => [...key.value, 'list', params.value.toString()]), queryFn: ({ signal }) => employees(params.value, signal), enabled: computed(() => !!auth.user?.roles.includes('ADMIN')) })
const selected = ref<Employee>(), mode = ref<'edit' | 'reset' | 'history'>('edit'), dialog = ref(false), busy = ref(false), failure = ref(''), uncertain = ref(false), success = ref(''), historyPage = ref(0)
const form = reactive<EmployeeUpdate>({ version: 0, displayName: '', phone: '', roles: [], status: 'ACTIVE', reason: '' })
const password = ref(''), confirm = ref('')
const history = useQuery({ queryKey: computed(() => [...key.value, 'history', selected.value?.id, historyPage.value]), queryFn: ({ signal }) => accountHistory(selected.value!.id, historyPage.value, signal), enabled: computed(() => dialog.value && mode.value === 'history' && !!selected.value) })
const isSelf = computed(() => selected.value?.id === auth.user?.id)
const dateTime = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })
const actions: Record<string,string> = { UPDATE_EMPLOYEE: '维护员工账号', CHANGE_PASSWORD: '员工自助修改密码', RESET_PASSWORD: '管理员重置密码' }
const roleText = (employee: Employee) => employee.roles.map(r => roleLabels[r]).join(' / ')
function apply() { query.value = search.value.trim(); page.value = 0; void list.refetch() }
function open(employee: Employee, action: typeof mode.value) {
  selected.value = employee; mode.value = action; failure.value = ''; uncertain.value = false; password.value = ''; confirm.value = ''; historyPage.value = 0
  Object.assign(form, { version: employee.version, displayName: employee.displayName, phone: employee.phone, roles: [...employee.roles], status: employee.status, reason: '' })
  dialog.value = true
}
function close() { if (busy.value) return; dialog.value = false; password.value = ''; confirm.value = ''; failure.value = ''; uncertain.value = false }
async function submit() {
  if (busy.value || uncertain.value || !selected.value) return
  failure.value = ''
  if (!form.reason.trim()) failure.value = '请填写操作原因'
  else if (mode.value === 'reset') failure.value = passwordError(password.value, confirm.value)
  else if (!form.displayName.trim() || !/^\+?[0-9]{6,20}$/.test(form.phone.trim()) || form.phone.trim().length > 20 || !form.roles.length) failure.value = '请核对姓名、手机号和工作角色'
  if (failure.value) return
  busy.value = true; success.value = ''
  try {
    if (mode.value === 'reset') { await resetPassword(selected.value.id, form.version, password.value, form.reason.trim()); success.value = `${selected.value.displayName} 的密码已重置，所有旧会话已撤销。请妥善交接新密码。` }
    else { await updateEmployee(selected.value.id, { ...form, displayName: form.displayName.trim(), phone: form.phone.trim(), roles: [...form.roles], reason: form.reason.trim() }); success.value = '员工账号已更新；角色或状态变更后，员工需重新登录。' }
    busy.value = false; close()
    if (isSelf.value) await auth.verify()
  } catch (error) {
    uncertain.value = uncertainWrite(error)
    failure.value = uncertain.value ? '操作结果未确认，请关闭窗口后刷新员工列表、查看操作记录核实。密码重置还需员工使用新密码登录核实；系统不会自动重复提交。' : errorMessage(error)
    if (uncertain.value) { password.value = ''; confirm.value = '' }
  } finally { busy.value = false; await queryClient.invalidateQueries({ queryKey: key.value }) }
}
</script>
<template>
  <div class="page-heading"><div><div class="page-eyebrow">PEOPLE & ACCESS</div><h1>员工账号管理</h1><p>让每位同事拥有合适的工作权限。</p></div><RouterLink to="/settings/users/new"><ElButton type="primary" :icon="Plus">开通员工账号</ElButton></RouterLink></div>
  <section class="account-management-intro"><img src="/images/account-care.png" alt="" /><div><strong>安心协作，从工作身份开始</strong><p>维护员工资料与职责，按需启停账号。角色、状态及密码变更会撤销员工旧会话，操作记录可追溯。</p></div></section>
  <section class="surface">
<form class="account-controls" aria-label="员工账号筛选" @submit.prevent="apply"><ElInput v-model="search" aria-label="搜索员工" placeholder="账号、姓名或手机号" :prefix-icon="Search" :maxlength="100" /><select v-model="status" aria-label="账号状态筛选" @change="page = 0"><option value="">全部状态</option><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select><select v-model="role" aria-label="员工角色筛选" @change="page = 0"><option value="">全部角色</option><option v-for="r in employeeRoles" :key="r" :value="r">{{ roleLabels[r] }}</option></select><ElButton native-type="submit" type="primary">查询员工</ElButton><ElButton :icon="RefreshRight" :loading="list.isFetching.value" @click="list.refetch()">刷新列表</ElButton></form>
    <p v-if="success" class="account-results-feedback" role="status">{{ success }}</p>
    <AppState v-if="list.isPending.value" kind="loading" title="正在读取员工账号" /><AppState v-else-if="list.isError.value" kind="error" title="员工账号读取失败" :description="errorMessage(list.error.value)" action-label="重试员工列表" @action="list.refetch()" /><AppState v-else-if="!list.data.value?.items.length" title="没有找到员工账号" description="可调整姓名、账号、手机号或角色和状态筛选。" :action-label="page ? '返回第一页' : ''" @action="page = 0" />
    <template v-else><div class="account-table-wrap"><table class="account-table"><thead><tr><th>员工 / 登录账号</th><th>联系电话</th><th>工作角色</th><th>账号状态</th><th>管理操作</th></tr></thead><tbody><tr v-for="employee in list.data.value.items" :key="employee.id"><td><strong>{{ employee.displayName }}</strong><small>{{ employee.username }}{{ employee.id === auth.user?.id ? ' · 本人' : '' }}</small></td><td>{{ employee.phone }}</td><td>{{ roleText(employee) }}</td><td><span class="account-status" :class="{ 'is-disabled': employee.status === 'DISABLED' }">{{ employee.status === 'ACTIVE' ? '启用' : '停用' }}</span></td><td><ElButton text type="primary" @click="open(employee, 'edit')">维护账号</ElButton><ElButton v-if="employee.id !== auth.user?.id" text type="primary" @click="open(employee, 'reset')">重置密码</ElButton><RouterLink v-else to="/settings/password">修改密码</RouterLink><ElButton text @click="open(employee, 'history')">操作记录</ElButton></td></tr></tbody></table></div><div class="account-pages"><span>共 {{ list.data.value.totalElements }} 位员工</span><ElPagination :current-page="page + 1" :total="list.data.value.totalElements" :page-size="10" layout="prev, pager, next" @update:current-page="page = $event - 1" /></div></template>
  </section>
  <ElDialog :model-value="dialog" :title="`${mode === 'edit' ? '维护账号' : mode === 'reset' ? '重置密码' : '操作记录'} · ${selected?.displayName ?? ''}`" width="min(640px, 94vw)" :close-on-click-modal="false" :close-on-press-escape="!busy" :show-close="!busy" :before-close="close" @update:model-value="(value: boolean) => { if (!value) close() }">
    <template v-if="mode === 'history'"><AppState v-if="history.isPending.value" kind="loading" title="正在读取操作记录" /><AppState v-else-if="history.isError.value" kind="error" title="操作记录读取失败" :description="errorMessage(history.error.value)" action-label="重试操作记录" @action="history.refetch()" /><AppState v-else-if="!history.data.value?.items.length" title="暂无操作记录" /><template v-else><article v-for="item in history.data.value.items" :key="item.id" class="account-audit"><h3>{{ actions[item.action] ?? item.action }}</h3><small>{{ dateTime(item.createdAt) }} · 操作人 {{ item.actorId }}</small><p>原因：{{ item.reason }}</p><p v-if="item.beforeSummary">变更前：{{ item.beforeSummary }}</p><p v-if="item.afterSummary">变更后：{{ item.afterSummary }}</p></article><ElPagination :current-page="historyPage + 1" :total="history.data.value.totalElements" :page-size="10" layout="prev, pager, next" @update:current-page="historyPage = $event - 1" /></template></template>
    <template v-else><ElAlert v-if="failure" class="account-dialog-feedback" :title="failure" :type="uncertain ? 'warning' : 'error'" :closable="false" show-icon /><ElForm class="account-editor" label-position="top" :disabled="busy || uncertain" @submit.prevent="submit"><template v-if="mode === 'edit'"><ElFormItem label="员工姓名" for="edit-employee-name" required><ElInput id="edit-employee-name" v-model="form.displayName" :maxlength="100" /></ElFormItem><ElFormItem label="员工手机号" for="edit-employee-phone" required><ElInput id="edit-employee-phone" v-model="form.phone" :maxlength="20" /></ElFormItem><ElFormItem label="工作角色" required><div class="account-role-options"><label v-for="r in employeeRoles" :key="r"><input v-model="form.roles" type="checkbox" :value="r" :disabled="busy || uncertain || isSelf" />{{ roleLabels[r] }}</label></div></ElFormItem><ElFormItem label="账号状态" for="edit-employee-status"><select id="edit-employee-status" v-model="form.status" :disabled="busy || uncertain || isSelf"><option value="ACTIVE">启用</option><option value="DISABLED">停用</option></select></ElFormItem><p v-if="isSelf" class="account-muted">本人只能维护姓名和手机号；角色及状态请由其他管理员调整。</p></template><template v-else><p class="account-muted">重置后该员工的所有旧会话立即失效，需使用新密码重新登录。</p><ElFormItem label="重置新密码" for="reset-password" required><ElInput id="reset-password" v-model="password" type="password" autocomplete="new-password" show-password :maxlength="72" /></ElFormItem><ElFormItem label="确认重置密码" for="confirm-reset-password" required><ElInput id="confirm-reset-password" v-model="confirm" type="password" autocomplete="new-password" show-password :maxlength="72" /></ElFormItem></template><ElFormItem label="操作原因" for="account-reason" required><ElInput id="account-reason" v-model="form.reason" type="textarea" :rows="3" :maxlength="500" /></ElFormItem><ElButton native-type="submit" type="primary" :loading="busy" :disabled="uncertain">{{ mode === 'edit' ? '保存账号变更' : '确认重置密码' }}</ElButton></ElForm></template><template #footer><ElButton :disabled="busy" @click="close">{{ uncertain ? '关闭并核实结果' : '关闭' }}</ElButton></template>
  </ElDialog>
</template>
