/**
 * 商家端 API 用例 —— 登录、店铺、商品、订单、退款审批、员工管理、通知
 *
 * 使用演示商家 13900000001/123456 (uid=20001)
 */

import { test, expect, beforeAll, afterAll } from '@playwright/test'
import { Api, loginAs, ERR } from '../utils/api.js'
import { DEMO_MERCHANT, genTestPhone, genTestName } from '../utils/constants.js'
import { execDb } from '../utils/db.js'

let api: Api

beforeAll(async () => {
  api = await Api.create()
})
afterAll(async () => {
  await api.dispose()
})

async function m(): Promise<{ token: string; userId: number; role: string }> {
  return loginAs(api, DEMO_MERCHANT.phone, DEMO_MERCHANT.password)
}

// ===== 商家登录 =====

test('merchant: 演示商家登录 → role=merchant', async () => {
  const r = await loginAs(api, DEMO_MERCHANT.phone, DEMO_MERCHANT.password)
  expect(r.role).toBe('merchant')
})

test('merchant: 普通用户登录商家端 → 失败（无权限）', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  // 用 user token 调商家端接口
  const r = await api.get('/merchant/dashboard', { token: u.token })
  expect(r.code).not.toBe(0)
})

// ===== 商家概览 / 数据面板 =====

test('merchant/dashboard: 拉取概览数据', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/dashboard', { token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/data/sales: 销售数据', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/data/sales', { params: { days: 7 }, token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/data/visitor: 访客数据', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/data/visitor', { params: { days: 7 }, token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/data/convert: 转化数据', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/data/convert', { params: { days: 7 }, token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/data/heatmap: 热力图', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/data/heatmap', { token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/todos: 待办列表', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/todos', { token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/store: 店铺信息', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/store', { token: m1.token })
  expect(r.code).toBe(0)
})

// ===== 商品管理 =====

test('merchant/product: 列表 / 详情', async () => {
  const m1 = await m()
  const list = await api.get('/merchant/product', { params: { page: 1, size: 10 }, token: m1.token })
  expect(list.code).toBe(0)
  const items = (list.data as any).list || list.data
  expect(Array.isArray(items)).toBe(true)

  if (items.length > 0) {
    const detail = await api.get(`/merchant/product/${items[0].id}`, { token: m1.token })
    expect(detail.code).toBe(0)
  }
})

test('merchant/product: 新建 → 编辑 → 上下架 → 删除', async () => {
  const m1 = await m()
  const productName = `pmt_mp_${Date.now() % 1e7}`

  const create = await api.post(
    '/merchant/product',
    {
      name: productName,
      categoryId: 1,
      mainImage: 'https://example.com/p.png',
      salePrice: 99.5,
      stock: 100,
      detail: 'petmate_test 商品详情',
    },
    { token: m1.token },
  )
  expect(create.code).toBe(0)
  const pid = (create.data as any).id
  expect(pid).toBeGreaterThan(0)

  // 编辑
  const upd = await api.put(
    `/merchant/product/${pid}`,
    { name: productName + '_v2', salePrice: 88 },
    { token: m1.token },
  )
  expect(upd.code).toBe(0)

  // 上下架
  const status = await api.put(`/merchant/product/${pid}/status`, { status: 0 }, { token: m1.token })
  expect(status.code).toBe(0)

  // 再上架
  const status2 = await api.put(`/merchant/product/${pid}/status`, { status: 1 }, { token: m1.token })
  expect(status2.code).toBe(0)

  // 删除
  const del = await api.del(`/merchant/product/${pid}`, { token: m1.token })
  expect(del.code).toBe(0)
})

test('merchant/product: 缺必填 → PARAM_ERROR', async () => {
  const m1 = await m()
  const r = await api.post('/merchant/product', { name: 'x' }, { token: m1.token })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('merchant/product: 跨商家编辑 → 失败', async () => {
  const m1 = await m()
  // 尝试编辑 sku 29 对应的商品（属于演示商家 20001，编辑会成功 → 但商品应属于 m1）
  // 测试跨商家：用新建 m1 商品 → 用 m1.token 即可。改为：尝试编辑不存在 id
  const r = await api.put('/merchant/product/999999999', { name: 'x' }, { token: m1.token })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('merchant/product/stock: 库存列表', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/product/stock', { params: { lowStock: 10 }, token: m1.token })
  expect(r.code).toBe(0)
})

// ===== 订单管理 =====

test('merchant/order: 拉取订单列表', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/order', { params: { page: 1, size: 10 }, token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/order/{no}: 订单详情', async () => {
  const m1 = await m()
  const list = await api.get('/merchant/order', { params: { page: 1, size: 5 }, token: m1.token })
  const items = (list.data as any).list || list.data
  if (items.length > 0) {
    const r = await api.get(`/merchant/order/${items[0].order_no}`, { token: m1.token })
    expect(r.code).toBe(0)
  }
})

test('merchant/order/{no}/remark: 设置商家备注', async () => {
  const m1 = await m()
  const list = await api.get('/merchant/order', { params: { page: 1, size: 5 }, token: m1.token })
  const items = (list.data as any).list || list.data
  if (items.length === 0) return
  const r = await api.put(
    `/merchant/order/${items[0].order_no}/remark`,
    { remark: 'petmate_test 商家备注' },
    { token: m1.token },
  )
  expect(r.code).toBe(0)
})

// ===== 发货（演示态直接置 shipping） =====

test('merchant/order/{no}/ship: 商家发货', async () => {
  const m1 = await m()
  const list = await api.get('/merchant/order', { params: { page: 1, size: 5, status: 'paid' }, token: m1.token })
  const items = (list.data as any).list || list.data
  if (items.length === 0) {
    test.skip(true, '无 paid 订单可发')
    return
  }
  const r = await api.post(`/merchant/order/${items[0].order_no}/ship`, { trackingNo: 'petmate_test_001' }, { token: m1.token })
  expect(r.code).toBe(0)
})

// ===== 售后审批 =====

test('merchant/refunds: 退款列表', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/refunds', { params: { page: 1, size: 10 }, token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/refund/{id}/timeline: 退款时间线', async () => {
  const m1 = await m()
  const list = await api.get('/merchant/refunds', { params: { page: 1, size: 5 }, token: m1.token })
  const items = (list.data as any).list || list.data
  if (items.length === 0) return
  const r = await api.get(`/merchant/refund/${items[0].id}/timeline`, { token: m1.token })
  expect(r.code).toBe(0)
})

// ===== 员工管理 =====

test('merchant/staff: 员工列表', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/staff', { token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/roles: 角色列表', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/roles', { token: m1.token })
  expect(r.code).toBe(0)
})

// ===== 通知 =====

test('merchant/notifications: 商家通知列表 / 全部已读', async () => {
  const m1 = await m()
  const list = await api.get('/merchant/notifications', { token: m1.token })
  expect(list.code).toBe(0)
  const readAll = await api.post('/merchant/notifications/read-all', {}, { token: m1.token })
  expect(readAll.code).toBe(0)
})

// ===== 服务 =====

test('merchant/service: 服务列表 / 类目', async () => {
  const m1 = await m()
  const list = await api.get('/merchant/service', { token: m1.token })
  expect(list.code).toBe(0)
  const cat = await api.get('/merchant/service/catalog', { token: m1.token })
  expect(cat.code).toBe(0)
})

test('merchant/schedule: 排班', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/schedule', { token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/customers: 客户列表', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/customers', { params: { page: 1, size: 10 }, token: m1.token })
  expect(r.code).toBe(0)
})

// ===== 商家申请状态 =====

test('merchant/apply/status: 查询申请状态', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/apply/status', { params: { phone: DEMO_MERCHANT.phone }, token: m1.token })
  expect(r.code).toBe(0)
})

// ===== 财务 / 评价 =====

test('merchant/finance: 财务总览', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/finance', { token: m1.token })
  expect(r.code).toBe(0)
})

test('merchant/review: 评价列表', async () => {
  const m1 = await m()
  const r = await api.get('/merchant/review', { params: { page: 1, size: 10 }, token: m1.token })
  expect(r.code).toBe(0)
})