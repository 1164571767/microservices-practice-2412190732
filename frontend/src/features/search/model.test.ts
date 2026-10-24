import { describe, expect, it } from 'vitest'
import { canSearchArchives, searchShortcuts } from './model'
describe('全局搜索权限范围', () => {
  it('药房只有授权工作入口，不查询临床档案', () => {
    expect(canSearchArchives(['PHARMACY'])).toBe(false)
    expect(searchShortcuts(['PHARMACY'],'').map(x => x.path)).toEqual(['/','/pharmacy','/settings/password','/notifications'])
  })
  it('前台可以检索档案，不能搜索管理入口或报表', () => {
    expect(canSearchArchives(['STAFF'])).toBe(true)
    expect(searchShortcuts(['STAFF'],'账号管理')).toEqual([])
    expect(searchShortcuts(['STAFF'],'数据报表')).toEqual([])
  })
  it('管理员可按名称检索入口', () => {
    expect(searchShortcuts(['ADMIN'],' 员工账号 ')).toEqual([{ title: '员工账号管理', path: '/settings/users', roles: ['ADMIN'] }])
  })
})
