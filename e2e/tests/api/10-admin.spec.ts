/**
 * 管理端 API 用例 —— 登录、仪表盘、用户/商家/订单/帖子审核、敏感词
 *
 * 使用演示管理员 13700000000/123456 (uid=90001)
 */

import { test, expect, beforeAll, afterAll } from '@playwright/test'
import { Api, loginAs, ERR } from '../utils/api.js'
import { DEMO_ADMIN } from '../utils/constants.js'

let api: Api

beforeAll(async () => {
  api = await Api.create()
})
afterAll(async () => {
  await api.dispose()
})

async function adm(): Promise<{ token: string; userId: number; role: string }> {
  return loginAs(api, DEMO_ADMIN.phone, DEMO_ADMIN.password)
}

// ===== 管理员登录 =====

test('admin: 演示管理员登录 → role=admin', async () => {
  const r = await loginAs(api, DEMO_ADMIN.phone, DEMO_ADMIN.password)
  expect(r.role).toBe('admin')
})

test('admin: 普通用户调管理端 → 失败', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.get('/admin/dashboard', { token: u.token })
  expect(r.code).not.toBe(0)
})

// ===== 仪表盘 =====

test('admin/dashboard: 概览数据', async () => {
  const a = await adm()
  const r = await api.get('/admin/dashboard', { token: a.token })
  expect(r.code).toBe(0)
})

test('admin/dashboard/visitor-trend: 访客趋势', async () => {
  const a = await adm()
  const r = await api.get('/admin/dashboard/visitor-trend', { params: { days: 7 }, token: a.token })
  expect(r.code).toBe(0)
})

test('admin/dashboard/category-share: 分类占比', async () => {
  const a = await adm()
  const r = await api.get('/admin/dashboard/category-share', { token: a.token })
  expect(r.code).toBe(0)
})

test('admin/dashboard/funnel: 转化漏斗', async () => {
  const a = await adm()
  const r = await api.get('/admin/dashboard/funnel', { token: a.token })
  expect(r.code).toBe(0)
})

test('admin/dashboard/hot-products: 热销商品', async () => {
  const a = await adm()
  const r = await api.get('/admin/dashboard/hot-products', { token: a.token })
  expect(r.code).toBe(0)
})

// ===== 用户管理 =====

test('admin/user/list: 用户列表', async () => {
  const a = await adm()
  const r = await api.get('/admin/user/list', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})

test('admin/user/{id}: 用户详情', async () => {
  const a = await adm()
  const list = await api.get('/admin/user/list', { params: { page: 1, size: 5 }, token: a.token })
  const items = (list.data as any).list || list.data
  if (items.length === 0) return
  const r = await api.get(`/admin/user/${items[0].id}`, { token: a.token })
  expect(r.code).toBe(0)
})

test('admin/user/{id}/ban: 封禁用户', async () => {
  const a = await adm()
  // 用一个真实 demo 用户测试；演示态不会真封
  const r = await api.post('/admin/user/10002/ban', { reason: 'petmate_test 临时封禁', durationDays: 1 }, { token: a.token })
  expect([0, ERR.PARAM_ERROR]).toContain(r.code)
})

// ===== 商家管理 =====

test('admin/merchant/list: 商家列表', async () => {
  const a = await adm()
  const r = await api.get('/admin/merchant/list', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})

test('admin/merchant/{id}: 商家详情', async () => {
  const a = await adm()
  const list = await api.get('/admin/merchant/list', { params: { page: 1, size: 5 }, token: a.token })
  const items = (list.data as any).list || list.data
  if (items.length === 0) return
  const r = await api.get(`/admin/merchant/${items[0].id}`, { token: a.token })
  expect(r.code).toBe(0)
})

test('admin/merchant/apply/list: 商家申请列表', async () => {
  const a = await adm()
  const r = await api.get('/admin/merchant/apply/list', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})

// ===== 订单审核 =====

test('admin/order/list: 订单列表', async () => {
  const a = await adm()
  const r = await api.get('/admin/order/list', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})

test('admin/order/refund: 退款总览', async () => {
  const a = await adm()
  const r = await api.get('/admin/order/refund', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})

test('admin/order/refund/stat: 退款统计', async () => {
  const a = await adm()
  const r = await api.get('/admin/order/refund/stat', { token: a.token })
  expect(r.code).toBe(0)
})

// ===== 帖子审核 =====

test('admin/post/audit: 待审帖子列表', async () => {
  const a = await adm()
  const r = await api.get('/admin/post/audit', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})

// ===== 敏感词 =====

test('admin/sensitive: 敏感词列表', async () => {
  const a = await adm()
  const r = await api.get('/admin/sensitive', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})

test('admin/sensitive/test: 检测文本是否含敏感词', async () => {
  const a = await adm()
  const r = await api.post('/admin/sensitive/test', { text: 'petmate_test 正常文本' }, { token: a.token })
  expect(r.code).toBe(0)
})

// ===== 日志 =====

test('admin/log: 操作日志', async () => {
  const a = await adm()
  const r = await api.get('/admin/log', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})

// ===== 通知下发 =====

test('admin/notify: 给用户发系统通知', async () => {
  const a = await adm()
  const r = await api.post('/admin/notify', { userId: 10001, title: 'petmate_test 系统通知', content: 'petmate_test 测试' }, { token: a.token })
  expect([0, ERR.PARAM_ERROR]).toContain(r.code)
})

// ===== 举报处理 =====

test('admin/report/list: 举报列表', async () => {
  const a = await adm()
  const r = await api.get('/admin/report/list', { params: { page: 1, size: 10 }, token: a.token })
  expect(r.code).toBe(0)
})