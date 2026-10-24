import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../features/auth/store'

export const safeReturnPath = (value: unknown) =>
  typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') &&
  !value.includes('\\') && !value.startsWith('/login') ? value : '/'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('../features/auth/LoginPage.vue'), meta: { title: '员工登录' } },
    {
      path: '/', component: () => import('../layouts/AppLayout.vue'), meta: { requiresAuth: true },
      children: [
        { path: 'settings/users', name: 'accounts', component: () => import('../features/users/AccountsPage.vue'), meta: { title: '员工账号管理', adminOnly: true } },
        { path: 'settings/password', name: 'change-password', component: () => import('../features/users/ChangePasswordPage.vue'), meta: { title: '修改密码' } },
        { path: 'notifications', name: 'notifications', component: () => import('../features/notifications/NotificationsPage.vue'), meta: { title: '通知中心' } },
        { path: '', name: 'home', component: () => import('../features/workspace/WorkspacePage.vue'), meta: { title: '医院工作台' } },
        { path: 'appointments', name: 'appointments', component: () => import('../features/appointments/AppointmentsPage.vue'), meta: { title: '预约与接待', clinicOnly: true } },
        { path: 'medical', name: 'medical', component: () => import('../features/medical/MedicalPage.vue'), meta: { title: '诊疗工作台', clinicOnly: true } },
        { path: 'billing', name: 'billing', component: () => import('../features/billing/BillingPage.vue'), meta: { title: '收费与账单', billingOnly: true } },
        { path: 'pharmacy', name: 'pharmacy', component: () => import('../features/pharmacy/PharmacyPage.vue'), meta: { title: '药房与库存', pharmacyReadOnly: true } },
        { path: 'reporting', name: 'reporting', component: () => import('../features/reporting/ReportingPage.vue'), meta: { title: '数据报表', adminOnly: true } },
        { path: 'clients', name: 'clients', component: () => import('../features/clients/ClientsPage.vue'), meta: { title: '客户与宠物', clinicOnly: true } },
        { path: 'clients/:id', name: 'client-detail', component: () => import('../features/clients/ClientDetailPage.vue'), meta: { title: '客户档案', clinicOnly: true } },
        { path: 'pets/:id', name: 'pet-detail', component: () => import('../features/clients/PetDetailPage.vue'), meta: { title: '宠物健康档案', clinicOnly: true } },
        { path: 'settings/users/new', name: 'create-employee', component: () => import('../features/users/CreateEmployeePage.vue'), meta: { title: '开通员工账号', adminOnly: true } },
        { path: 'forbidden', name: 'forbidden', component: () => import('../shared/ui/ForbiddenPage.vue'), meta: { title: '无权访问' } },
      ],
    },
    { path: '/:pathMatch(.*)*', component: () => import('../shared/ui/NotFoundPage.vue'), meta: { title: '页面不存在' } },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.adminOnly && !auth.user?.roles.includes('ADMIN')) return { name: 'forbidden' }
  if (to.meta.billingOnly && !auth.user?.roles.some(role => ['STAFF', 'ADMIN'].includes(role))) return { name: 'forbidden' }
  if (to.meta.pharmacyReadOnly && !auth.user?.roles.some(role => ['STAFF', 'DOCTOR', 'PHARMACY', 'ADMIN'].includes(role))) return { name: 'forbidden' }
  if (to.meta.clinicOnly && !auth.user?.roles.some((role) => ['STAFF', 'DOCTOR', 'ADMIN'].includes(role))) return { name: 'forbidden' }
  if (to.name === 'login' && auth.authenticated) return safeReturnPath(to.query.redirect)
})
router.afterEach((to) => { document.title = `${String(to.meta.title ?? '工作台')} · 爱和动物医院` })
