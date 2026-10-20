/**
 * 用户端 API 用例 —— 收藏夹 + 通知 + AI 问诊 + 订单确认收货 + 物流
 */

import { test, expect, beforeAll, afterAll } from '@playwright/test'
import { Api, loginAs, registerAs, ERR } from '../utils/api.js'
import { genTestPhone, genTestName } from '../utils/constants.js'
import { execDb } from '../utils/db.js'

let api: Api

beforeAll(async () => {
  api = await Api.create()
})
afterAll(async () => {
  await api.dispose()
})

async function paidOrder(api: Api): Promise<{ token: string; orderNo: string; addressId: number }> {
  const u = await registerAs(api, genTestPhone(), genTestName('paid'), 'abc123456')
  const addr = await api.post(
    '/user/address',
    {
      receiver: 'p', phone: '13900099001', province: '北京', city: '北京',
      district: '朝阳区', detail: 'p', tag: 'home', isDefault: true,
    },
    { token: u.token },
  )
  const addressId = (addr.data as any).id

  await api.post('/cart', { skuId: 29, quantity: 1 }, { token: u.token })
  const cart = await api.get('/cart', { token: u.token })
  const cid = (cart.data as any[]).find((c) => c.skuId === 29).id

  const submit = await api.post('/order', { cartIds: [cid], addressId }, { token: u.token })
  const orderNo = (submit.data as any).orderNo

  await api.post(`/pay/${orderNo}`, {}, { token: u.token })
  return { token: u.token, orderNo, addressId }
}

// ===== 订单：确认收货 / 物流 =====

test('order/receive: 已发货订单确认收货 → status=done', async () => {
  const ctx = await paidOrder(api)
  // 演示态没有商家手动发货，需要直接从 db 推进状态为 shipping
  // 这里走 db helper 直接 update
  await execDb(`UPDATE t_order SET status='shipping' WHERE order_no='${ctx.orderNo}'`)

  const r = await api.post(`/order/${ctx.orderNo}/receive`, {}, { token: ctx.token })
  expect(r.code).toBe(0)
  const detail = await api.get(`/order/${ctx.orderNo}`, { token: ctx.token })
  expect((detail.data as any).status).toBe('done')
})

test('order/receive: pending_pay 订单不能直接确认收货', async () => {
  const ctx = await paidOrder(api)
  // 强行回退到 pending_pay
  await execDb(`UPDATE t_order SET status='pending_pay' WHERE order_no='${ctx.orderNo}'`)

  const r = await api.post(`/order/${ctx.orderNo}/receive`, {}, { token: ctx.token })
  expect(r.code).not.toBe(0)
})

test('order/logistics: 拉取订单物流轨迹', async () => {
  const ctx = await paidOrder(api)
  const r = await api.get(`/order/${ctx.orderNo}/logistics`, { token: ctx.token })
  expect(r.code).toBe(0)
})

test('order/pay/status: 支付状态轮询', async () => {
  const ctx = await paidOrder(api)
  const r = await api.get('/order/pay/status', { params: { orderNo: ctx.orderNo }, token: ctx.token })
  expect(r.code).toBe(0)
})

// ===== 收藏 =====

test('favorite: 收藏商品 → 列出 → 取消', async () => {
  const u = await loginAs(api, '13800138000', '123456')

  const toggle = await api.post('/favorite/toggle', { targetType: 'product', targetId: 3001 }, { token: u.token })
  expect(toggle.code).toBe(0)

  const list = await api.get('/favorite/list', { params: { targetType: 'product' }, token: u.token })
  expect(list.code).toBe(0)
  const items = (list.data as any).list || list.data
  expect(Array.isArray(items)).toBe(true)

  // 再 toggle 一次取消
  const toggle2 = await api.post('/favorite/toggle', { targetType: 'product', targetId: 3001 }, { token: u.token })
  expect(toggle2.code).toBe(0)
})

// ===== 通知 =====

test('notifications: 列表 / 未读数 / 标记已读 / 全部已读', async () => {
  const u = await loginAs(api, '13800138000', '123456')

  const list = await api.get('/user/notifications', { token: u.token })
  expect(list.code).toBe(0)

  const unread = await api.get('/user/notifications/unread-count', { token: u.token })
  expect(unread.code).toBe(0)

  const readAll = await api.post('/user/notifications/read-all', {}, { token: u.token })
  expect(readAll.code).toBe(0)
})

// ===== AI 问诊 =====

test('consult: create → send → list messages → delete', async () => {
  const u = await loginAs(api, '13800138000', '123456')

  const create = await api.post('/consult/create', {}, { token: u.token })
  expect(create.code).toBe(0)
  const sessionId = (create.data as any).id || (create.data as any).sessionId
  expect(sessionId).toBeGreaterThan(0)

  const send = await api.post('/consult/send', { sessionId, content: 'petmate_test 我的狗狗最近没精神' }, { token: u.token })
  expect([0, ERR.PARAM_ERROR]).toContain(send.code)

  const msgs = await api.get(`/consult/${sessionId}/messages`, { token: u.token })
  expect(msgs.code).toBe(0)

  const del = await api.del(`/consult/${sessionId}`, { token: u.token })
  expect(del.code).toBe(0)
})

// ===== 用户统计 / 活动流 =====

test('user/stats: 拉取我的统计', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.get('/user/stats', { token: u.token })
  expect(r.code).toBe(0)
})

test('user/activities: 拉取我的活动流', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.get('/user/activities', { token: u.token })
  expect(r.code).toBe(0)
})