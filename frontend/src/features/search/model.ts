import type { Role } from '../../shared/api/types'
const clinic: Role[] = ['STAFF','DOCTOR','ADMIN']
const shortcuts: { title: string; path: string; roles?: Role[] }[] = [
  { title: '医院工作台', path: '/' },
  { title: '客户与宠物', path: '/clients', roles: clinic },
  { title: '预约与接待', path: '/appointments', roles: clinic },
  { title: '诊疗工作台', path: '/medical', roles: clinic },
  { title: '收费与账单', path: '/billing', roles: ['STAFF','ADMIN'] },
  { title: '药房与库存', path: '/pharmacy', roles: ['STAFF','DOCTOR','PHARMACY','ADMIN'] },
  { title: '数据报表', path: '/reporting', roles: ['ADMIN'] },
  { title: '员工账号管理', path: '/settings/users', roles: ['ADMIN'] },
  { title: '修改密码', path: '/settings/password' },
  { title: '通知中心', path: '/notifications' },
]
export const canSearchArchives = (roles: Role[]) => roles.some(r => clinic.includes(r))
export const searchShortcuts = (roles: Role[], query: string) => shortcuts.filter(item => (!item.roles || item.roles.some(r => roles.includes(r))) && item.title.includes(query.trim()))
