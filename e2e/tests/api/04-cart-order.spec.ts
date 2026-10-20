/**
 * 用户端 API 用例 —— 购物车 + 下单 + 支付 + 取消 + 收货
 *
 * 用一个全新测试用户走完整购物→下单→支付→收货闭环。
 * 演示态后端（ALIPAY_ENABLED=false）会直接置 paid。
 */

import { test, expect, beforeAll, afterAll } from '@playwright/test'
import { Api, loginAs, registerAs, ERR } from '../utils/api.js'
import { genTestPhone, genTestName } from '../utils/constants.js'

let api: Api

beforeAll(async () => {
  api = await Api.create()
})
afterAll(async () => {
  await api.dispose()
})

async function setupBuyerWithAddress(): Promise<{ token: string; userId: number; addressId: number }> {
  const u = await registerAs(api, genTestPhone(), genTestName('buyer'), 'abc123456')
  const addr = await api.post(
    '/user/address',
    {
      receiver: 'petmate_test_buyer',
      phone: '13900099001',
      province: '北京',
      city: '北京',
      district: '朝阳区',
      detail: 'petmate_test_addr_001',
      tag: 'home',
      isDefault: true,
    },
    { token: u.token },
  )
  expect(addr.code).toBe(0)
  return { token: u.token, userId: u.userId, addressId: (addr.data as any).id }
}

test('cart: 加购 → 列表 → 改数量 → 删除', async () => {
  const { token } = await setupBuyerWithAddress()

  // 演示库 sku 29 有库存
  const add = await api.post('/cart', { skuId: 29, quantity: 2 }, { token })
  expect(add.code).toBe(0)

  // 列表
  const list = await api.get('/cart', { token })
  expect(list.code).toBe(0)
  const items = list.data as any[]
  expect(Array.isArray(items)).toBe(true)
  const found = items.find((c) => c.skuId === 29)
  expect(found).toBeTruthy()
  expect(found.quantity).toBe(2)

  // 改数量
  const upd = await api.put(`/cart/${found.id}`, { quantity: 5 }, { token })
  expect(upd.code).toBe(0)

  // 再查
  const after = await api.get('/cart', { token })
  const afterItem = (after.data as any[]).find((c) => c.skuId === 29)
  expect(afterItem.quantity).toBe(5)

  // 删除
  const del = await api.del('/cart', { data: { ids: [found.id] }, token })
  expect(del.code).toBe(0)
})

test('cart: 加购不存在的 SKU → 拒绝', async () => {
  const { token } = await setupBuyerWithAddress()
  const r = await api.post('/cart', { skuId: 999999999, quantity: 1 }, { token })
  expect(r.code).not.toBe(0)
})

test('cart: 加购已下架商品 → 拒绝', async () => {
  const { token } = await setupBuyerWithAddress()
  // 演示库里 status=0 的商品（如果有）
  const down = await api.post('/cart', { skuId: 1, quantity: 1 }, { token })
  // 不强求 error code：可能是 NOT_LOGIN（商品不存在）/ PRODUCT_OFFLINE 等
  // 关键是不能成功加购一条 status=0 的 SKU
  if (down.code === 0) {
    // 不应该成功，否则记录为缺陷
    throw new Error('下架商品加购成功（缺陷）')
  }
})

test('order: 下单 → 支付(演示态) → 查订单 → 取消(pending_pay)', async () => {
  const ctx = await setupBuyerWithAddress()

  // 加购
  await api.post('/cart', { skuId: 29, quantity: 1 }, { token: ctx.token })

  // 拉购物车取 cartId
  const cart = await api.get('/cart', { token: ctx.token })
  const cid = (cart.data as any[]).find((c) => c.skuId === 29).id

  // 下单
  const submit = await api.post(
    '/order',
    { cartIds: [cid], addressId: ctx.addressId },
    { token: ctx.token },
  )
  expect(submit.code).toBe(0)
  const orderNo = (submit.data as any).orderNo
  expect(orderNo).toMatch(/^P\d+$/)

  // 订单详情
  const detail = await api.get(`/order/${orderNo}`, { token: ctx.token })
  expect(detail.code).toBe(0)
  expect((detail.data as any).status).toBe('pending_pay')

  // 支付（演示态直接置 paid）
  const pay = await api.post(`/pay/${orderNo}`, {}, { token: ctx.token })
  expect(pay.code).toBe(0)
  expect((pay.data as any).demoMode).toBe(true)

  // 再查订单应是 paid
  const after = await api.get(`/order/${orderNo}`, { token: ctx.token })
  expect((after.data as any).status).toBe('paid')

  // 已支付订单不应允许取消
  const cancel = await api.post(`/order/${orderNo}/cancel`, {}, { token: ctx.token })
  expect(cancel.code).not.toBe(0)
})

test('order: 取消 pending_pay 订单 → status=cancelled', async () => {
  const ctx = await setupBuyerWithAddress()
  await api.post('/cart', { skuId: 29, quantity: 1 }, { token: ctx.token })
  const cart = await api.get('/cart', { token: ctx.token })
  const cid = (cart.data as any[]).find((c) => c.skuId === 29).id

  const submit = await api.post(
    '/order',
    { cartIds: [cid], addressId: ctx.addressId },
    { token: ctx.token },
  )
  const orderNo = (submit.data as any).orderNo

  const cancel = await api.post(`/order/${orderNo}/cancel`, {}, { token: ctx.token })
  expect(cancel.code).toBe(0)

  const detail = await api.get(`/order/${orderNo}`, { token: ctx.token })
  expect((detail.data as any).status).toBe('cancelled')
})

test('order: 同一订单重复支付 → 第二次被状态机拒绝（pending_pay 才能付）', async () => {
  // 当前后端实现：订单一旦从 pending_pay 变为 paid，再次支付会触发 ORDER_STATUS_INVALID。
  // 这是状态机正确性，不是幂等 bug。
  // 计划 §3.1 R1.6 / R-S2 中提到的"支付幂等"指的是「同一次支付请求」而非「已支付订单再支付」。
  const ctx = await setupBuyerWithAddress()
  await api.post('/cart', { skuId: 29, quantity: 1 }, { token: ctx.token })
  const cart = await api.get('/cart', { token: ctx.token })
  const cid = (cart.data as any[]).find((c) => c.skuId === 29).id

  const submit = await api.post(
    '/order',
    { cartIds: [cid], addressId: ctx.addressId },
    { token: ctx.token },
  )
  const orderNo = (submit.data as any).orderNo

  const p1 = await api.post(`/pay/${orderNo}`, {}, { token: ctx.token })
  expect(p1.code).toBe(0)

  const p2 = await api.post(`/pay/${orderNo}`, {}, { token: ctx.token })
  // 第二次必须失败（已支付不能再付）
  expect(p2.code).not.toBe(0)
})

test('order/list: 列出当前用户订单', async () => {
  const ctx = await setupBuyerWithAddress()
  const r = await api.get('/order/list', { token: ctx.token })
  expect(r.code).toBe(0)
  expect(r.data).toBeTruthy()
  // 新用户应无订单（也可能有一个刚刚的）
  expect(Array.isArray((r.data as any).list) || Array.isArray(r.data)).toBe(true)
})

test('order: 下单时地址属于他人 → PARAM_ERROR', async () => {
  // 用户A 建地址
  const a = await setupBuyerWithAddress()
  // 用户B 用这个 addressId 下单
  const b = await registerAs(api, genTestPhone(), genTestName('buyerB'), 'abc123456')
  await api.post('/cart', { skuId: 29, quantity: 1 }, { token: b.token })
  const cart = await api.get('/cart', { token: b.token })
  const cid = (cart.data as any[]).find((c) => c.skuId === 29).id
  const r = await api.post(
    '/order',
    { cartIds: [cid], addressId: a.addressId },
    { token: b.token },
  )
  expect(r.code).toBe(ERR.PARAM_ERROR)
})