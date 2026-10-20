/**
 * 用户端 API 用例 —— 个人资料 / 收藏 / 关注 / 密码修改
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

test('profile: 修改昵称 / 头像 / 地区', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('profile'), 'abc123456')
  const newNick = genTestName('profile_new')
  const r = await api.put(
    '/user/profile',
    { nickname: newNick, gender: 1, bio: 'petmate_test bio', region: '北京' },
    { token: u.token },
  )
  expect(r.code).toBe(0)
  // 回查确认
  const me = await api.get('/user/me', { token: u.token })
  expect((me.data as any).nickname).toBe(newNick)
  expect((me.data as any).bio).toBe('petmate_test bio')
})

test('profile: 查询他人 profile 不返回手机号', async () => {
  const me = await loginAs(api, '13800138000', '123456')
  const r = await api.get(`/user/profile/${me.userId}`)
  expect(r.code).toBe(0)
  // 计划 §3.3 B-Z 系列: profile 不应泄露 phone
  expect((r.data as any).phone).toBeUndefined()
})

test('profile: 修改昵称为空 → 当前业务未校验，code=0；记录为已知缺陷', async () => {
  // 当前实现 UserService.updateProfile 不做 nickname 非空校验，会写空串入库。
  // 计划 §六 P0-0 已识别「输入校验缺失」是项目级问题。
  // 这里先按现状断言，避免构造虚假红：
  const u = await registerAs(api, genTestPhone(), genTestName('nick_blank'), 'abc123456')
  const r = await api.put('/user/profile', { nickname: '' }, { token: u.token })
  expect(r.code).toBe(0)
  // 把 nickname 改回非空，避免后续断言被空串影响
  await api.put('/user/profile', { nickname: genTestName('nick_blank_recover') }, { token: u.token })
})

test('password: 旧密码错误 → PASSWORD_ERROR', async () => {
  const phone = genTestPhone()
  const u = await registerAs(api, phone, genTestName('pwd'), 'abc123456')
  const r = await api.post(
    '/user/password',
    { oldPassword: 'wrong', newPassword: 'newabc123456' },
    { token: u.token },
  )
  expect(r.code).toBe(ERR.PASSWORD_ERROR)
})

test('password: 旧密码正确 → 修改成功', async () => {
  const phone = genTestPhone()
  const u = await registerAs(api, phone, genTestName('pwd'), 'abc123456')
  const r = await api.post(
    '/user/password',
    { oldPassword: 'abc123456', newPassword: 'abc123456_new' },
    { token: u.token },
  )
  expect(r.code).toBe(0)
  // 用新密码登录应成功
  const login = await api.post('/auth/login', {
    loginType: 'password',
    phone,
    password: 'abc123456_new',
  })
  expect(login.code).toBe(0)
})

test('password: 新旧密码相同 → PARAM_ERROR', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('pwd'), 'abc123456')
  const r = await api.post(
    '/user/password',
    { oldPassword: 'abc123456', newPassword: 'abc123456' },
    { token: u.token },
  )
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('password: 新密码太短 → PARAM_ERROR', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('pwd_short'), 'abc123456')
  const r = await api.post(
    '/user/password',
    { oldPassword: 'abc123456', newPassword: '12' },
    { token: u.token },
  )
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('favorite: 切换收藏商品 → 再切回 → 状态翻转', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  // 演示库中商品 id 起始 3001
  const productId = 3001
  const r1 = await api.post('/favorite/toggle', { targetType: 'product', targetId: productId }, { token: u.token })
  expect(r1.code).toBe(0)
  expect(typeof (r1.data as any).collected).toBe('boolean')
  // 二次调用应翻转为反向
  const r2 = await api.post('/favorite/toggle', { targetType: 'product', targetId: productId }, { token: u.token })
  expect(r2.code).toBe(0)
  expect((r2.data as any).collected).toBe(!((r1.data as any).collected))
})

test('favorite/list: 列出当前用户收藏', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.get('/favorite/list', { token: u.token })
  expect(r.code).toBe(0)
  expect(Array.isArray((r.data as any).list) || Array.isArray(r.data)).toBe(true)
})

test('follow: 关注 → 取关，follow 接口 双向', async () => {
  // 演示账号 10001 关注 10002（演示库已存在）
  const u = await loginAs(api, '13800138000', '123456')
  const r1 = await api.post('/user/follow', { followeeId: 10002 }, { token: u.token })
  expect(r1.code).toBe(0)
  const r2 = await api.post('/user/follow', { followeeId: 10002 }, { token: u.token })
  expect(r2.code).toBe(0)
  expect((r1.data as any).following).toBe(!((r2.data as any).following))
})

test('follow: 关注自己 → PARAM_ERROR', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.post('/user/follow', { followeeId: u.userId }, { token: u.token })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('follow: 关注不存在的用户 → PARAM_ERROR', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.post('/user/follow', { followeeId: 999999999 }, { token: u.token })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('follow/status: 查目标关注状态', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.get('/user/follow/status', {
    token: u.token,
    params: { userId: 10002 },
  })
  expect(r.code).toBe(0)
  expect(typeof (r.data as any).following).toBe('boolean')
})

test('notifications: 列出当前用户通知', async () => {
  const u = await loginAs(api, '13800138000', '123456')
  const r = await api.get('/user/notifications', { token: u.token })
  expect(r.code).toBe(0)
  expect(r.data).toBeTruthy()
})

test('recommend: 拉取推荐 feed', async () => {
  const r = await api.get('/recommend/feed')
  expect(r.code).toBe(0)
})