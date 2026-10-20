/**
 * 用户端 API 用例 —— 地址簿 CRUD + 默认地址
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

test('address: 新增 → 列出 → 设为默认 → 编辑 → 删除', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('addr'), 'abc123456')

  // 新增
  const create = await api.post(
    '/user/address',
    {
      receiver: 'petmate_test_收件人',
      phone: '13900099001',
      province: '北京',
      city: '北京',
      district: '朝阳区',
      detail: 'petmate_test_某街某号',
      tag: 'home',
      isDefault: true,
    },
    { token: u.token },
  )
  expect(create.code).toBe(0)
  const newId = (create.data as any).id
  expect(newId).toBeGreaterThan(0)

  // 列表
  const list = await api.get('/user/address', { token: u.token })
  expect(list.code).toBe(0)
  const items = (list.data as any).list || list.data
  expect(Array.isArray(items)).toBe(true)
  const found = items.find((a: any) => a.id === newId)
  expect(found).toBeTruthy()
  expect(found.receiver).toBe('petmate_test_收件人')

  // 编辑
  const upd = await api.put(
    `/user/address/${newId}`,
    {
      receiver: 'petmate_test_改收件人',
      phone: '13900099002',
      province: '上海',
      city: '上海',
      district: '浦东新区',
      detail: 'petmate_test_改地址',
    },
    { token: u.token },
  )
  expect(upd.code).toBe(0)

  // 再查
  const after = await api.get(`/user/address/${newId}`, { token: u.token })
  expect(after.code).toBe(0)
  expect((after.data as any).receiver).toBe('petmate_test_改收件人')
  expect((after.data as any).city).toBe('上海')

  // 设为默认
  const setDef = await api.put(`/user/address/${newId}/default`, {}, { token: u.token })
  expect(setDef.code).toBe(0)

  // 删除
  const del = await api.del(`/user/address/${newId}`, { token: u.token })
  expect(del.code).toBe(0)

  // 再查应拿不到（或返回空）
  const after2 = await api.get(`/user/address/${newId}`, { token: u.token })
  expect([0, ERR.PARAM_ERROR]).toContain(after2.code)
})

test('address: 收件人为空 → PARAM_ERROR', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.post(
    '/user/address',
    { receiver: '', phone: '13800138000', province: '北京', city: '北京', district: '朝阳区', detail: '某街' },
    { token: u.token },
  )
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('address: 手机号非法 → PARAM_ERROR', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.post(
    '/user/address',
    { receiver: 'x', phone: '12345', province: '北京', city: '北京', district: 'x', detail: 'x' },
    { token: u.token },
  )
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('address: 无 token 创建地址 → NOT_LOGIN', async () => {
  const r = await api.post('/user/address', { receiver: 'x', phone: '13800138000', province: '北京', city: '北京', district: 'x', detail: 'x' })
  expect(r.code).toBe(ERR.NOT_LOGIN)
})

test('address: 用户A 不能删除用户B 的地址', async () => {
  // 用户A 建一条地址
  const a = await registerAs(api, genTestPhone(), genTestName('addrA'), 'abc123456')
  const create = await api.post(
    '/user/address',
    {
      receiver: 'petmate_test_A收件',
      phone: '13900099001',
      province: '北京',
      city: '北京',
      district: '朝阳区',
      detail: 'A的地址',
    },
    { token: a.token },
  )
  expect(create.code).toBe(0)
  const addrId = (create.data as any).id

  // 用户B 试着删
  const b = await loginAs(api, '13800138001', '123456')
  const del = await api.del(`/user/address/${addrId}`, { token: b.token })
  expect(del.code).not.toBe(0)

  // 清理：A 自己删
  await api.del(`/user/address/${addrId}`, { token: a.token })
})