/**
 * 用户端 API 用例 —— 宠物 + 商品搜索/详情 + 收藏足迹 + AI 问诊
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

// ===== 宠物 =====

test('pet: 新增 → 列表 → 改名 → 删除', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('po'), 'abc123456')
  // 宠物名最长 20 字符
  const petName = `pmt_d_${Date.now() % 1e7}`

  const create = await api.post(
    '/pet',
    { name: petName, categoryId: 1, breedId: 3, gender: 1 },
    { token: u.token },
  )
  expect(create.code).toBe(0)
  const petId = (create.data as any).id
  expect(petId).toBeGreaterThan(0)

  const list = await api.get('/pet/list', { token: u.token })
  expect(list.code).toBe(0)
  const pets = (list.data as any).list || list.data
  expect(Array.isArray(pets)).toBe(true)
  expect(pets.find((p: any) => p.id === petId)).toBeTruthy()

  // 改名
  const upd = await api.put(
    `/pet/${petId}`,
    { name: petName + '_v2', categoryId: 1, breedId: 3, gender: 1 },
    { token: u.token },
  )
  expect(upd.code).toBe(0)

  // 删除
  const del = await api.del(`/pet/${petId}`, { token: u.token })
  expect(del.code).toBe(0)
})

test('pet: 名字为空 → PARAM_ERROR', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.post('/pet', { name: '', categoryId: 1, breedId: 1 }, { token: u.token })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('pet: 缺 categoryId → PARAM_ERROR', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.post('/pet', { name: 'x' }, { token: u.token })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('pet: 用户A 不能编辑用户B 的宠物', async () => {
  const a = await registerAs(api, genTestPhone(), genTestName('pa'), 'abc123456')
  const create = await api.post(
    '/pet',
    { name: `pmt_a_${Date.now() % 1e7}`, categoryId: 1, breedId: 3, gender: 1 },
    { token: a.token },
  )
  expect(create.code).toBe(0)
  const petId = (create.data as any).id

  const b = await loginAs(api, '13800138001', '123456')
  const upd = await api.put(`/pet/${petId}`, { name: 'pwned' }, { token: b.token })
  expect(upd.code).not.toBe(0)

  // 清理
  await api.del(`/pet/${petId}`, { token: a.token })
})

// ===== 商品 =====

test('product/list: 商品列表分页', async () => {
  const r = await api.get('/product/list', { params: { page: 1, size: 10 } })
  expect(r.code).toBe(0)
  const items = (r.data as any).list || r.data
  expect(Array.isArray(items)).toBe(true)
})

test('product/{id}: 商品详情', async () => {
  // 演示库 sku 29 对应 product 3001
  const r = await api.get('/product/3001')
  expect(r.code).toBe(0)
  expect((r.data as any).id).toBe(3001)
})

test('product/{id}: 不存在的商品 → PRODUCT_OFFLINE', async () => {
  const r = await api.get('/product/999999999')
  expect(r.code).toBe(ERR.PRODUCT_OFFLINE)
})

test('search: 商品关键词搜索', async () => {
  const r = await api.get('/search', { params: { keyword: '皇家' } })
  expect(r.code).toBe(0)
  const items = (r.data as any).list || r.data
  expect(Array.isArray(items)).toBe(true)
})

test('search: 空关键词 → 全部商品或空', async () => {
  const r = await api.get('/search', { params: { keyword: '' } })
  // 不强求 result：可能空列表、可能全量 —— 都不应 5xx
  expect([0, ERR.PARAM_ERROR]).toContain(r.code)
})

test('category/tree: 分类树', async () => {
  const r = await api.get('/category/tree')
  expect(r.code).toBe(0)
  expect(Array.isArray(r.data)).toBe(true)
})

test('product/{id}/footprint: 记录足迹 → 列出足迹', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const rec = await api.post('/product/3001/footprint', {}, { token: u.token })
  expect([0, ERR.PARAM_ERROR]).toContain(rec.code)

  const list = await api.get('/user/footprint', { token: u.token })
  expect(list.code).toBe(0)
})

// ===== 收藏足迹（t_product_footprint / t_favorite） =====

test('footprint: 列出足迹 → 清空', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const before = await api.get('/user/footprint', { token: u.token })
  expect(before.code).toBe(0)
  // 清空
  const clear = await api.del('/user/footprint', { token: u.token })
  expect(clear.code).toBe(0)
})

// ===== AI 问诊 =====

test('consult: 启动新会话 → 拿 sessionId', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.post('/consult/create', {}, { token: u.token })
  expect(r.code).toBe(0)
})

test('consult/list: 拉取历史会话', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.get('/consult/list', { token: u.token })
  expect(r.code).toBe(0)
  expect(Array.isArray(r.data) || Array.isArray((r.data as any).list)).toBe(true)
})