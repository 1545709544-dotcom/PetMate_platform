/**
 * 用户端 API 用例 —— 注册 / 登录 / 当前用户 / 登出
 *
 * 覆盖：注册成功、重复注册、密码错误、用户不存在、
 *       /user/me 鉴权、登出后再请求受保护接口、验证码 / token 校验
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

test('注册: 合法手机号 + 昵称 + 密码 → code=0 并返回 token', async () => {
  const phone = genTestPhone()
  const nickname = genTestName('reg_ok')
  const r = await registerAs(api, phone, nickname, 'abc123456')
  expect(r.token).toBeTruthy()
  expect(r.token.split('.').length).toBe(3) // JWT 三段
  expect(r.userId).toBeGreaterThan(0)
  expect(r.role).toBe('user')
  expect(r.nickname).toBe(nickname)
})

test('注册: 同一手机号重复注册 → 报错', async () => {
  const phone = genTestPhone()
  const nickname = genTestName('reg_dup')
  await registerAs(api, phone, nickname, 'abc123456')
  const r = await api.post('/auth/register', { phone, nickname: nickname + '_v2', password: 'abc123456' })
  expect(r.code).not.toBe(0)
  // 计划 §3.1 B-A 系列: 重复注册走 PARAM_ERROR 分支
  expect([ERR.PARAM_ERROR]).toContain(r.code)
})

test('注册: 非法手机号 → PARAM_ERROR', async () => {
  const r = await api.post('/auth/register', { phone: '12345', nickname: 'x', password: 'abc123456' })
  expect(r.code).toBe(ERR.PARAM_ERROR)
  expect(r.msg).toMatch(/手机号/)
})

test('注册: 密码太短 → PARAM_ERROR', async () => {
  const r = await api.post('/auth/register', {
    phone: genTestPhone(),
    nickname: genTestName('short_pwd'),
    password: '123',
  })
  expect(r.code).toBe(ERR.PARAM_ERROR)
  expect(r.msg).toMatch(/密码/)
})

test('登录: 演示账号密码登录 → 拿到 user token', async () => {
  const r = await loginAs(api, '13800138000', '123456')
  expect(r.role).toBe('user')
  expect(r.userId).toBe(10001)
  // 演示账号昵称 "李丽"
  expect(r.nickname).toBe('李丽')
})

test('登录: 密码错误 → PASSWORD_ERROR', async () => {
  const r = await api.post('/auth/login', {
    loginType: 'password',
    phone: '13800138000',
    password: 'wrong_pwd',
  })
  expect(r.code).toBe(ERR.PASSWORD_ERROR)
})

test('登录: 不存在的手机号 → PASSWORD_ERROR（不暴露用户存在性）', async () => {
  const r = await api.post('/auth/login', {
    loginType: 'password',
    phone: '13900000000',
    password: '123456',
  })
  expect(r.code).toBe(ERR.PASSWORD_ERROR)
})

test('登录: 不支持的 loginType → PARAM_ERROR', async () => {
  const r = await api.post('/auth/login', {
    loginType: 'magic_link',
    phone: '13800138000',
  })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('user/me: 带 token → 返回完整用户信息（含 role）', async () => {
  const { token } = await loginAs(api, '13800138000', '123456')
  const r = await api.get('/user/me', { token })
  expect(r.code).toBe(0)
  expect(r.data).toBeTruthy()
  expect((r.data as any).id).toBe(10001)
  expect((r.data as any).role).toBe('user')
  expect((r.data as any).subRole).toBe('OWNER')
  expect((r.data as any).isStaff).toBe(false)
})

test('user/me: 无 token → 报 NOT_LOGIN', async () => {
  const r = await api.get('/user/me')
  expect(r.code).toBe(ERR.NOT_LOGIN)
})

test('user/me: 篡改 token → 报 NOT_LOGIN（验签拒绝）', async () => {
  const r = await api.get('/user/me', { token: 'eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxMDAwMSJ9.fakefakefake' })
  expect(r.code).toBe(ERR.NOT_LOGIN)
})

test('user/me: alg=none 攻击 token → 验签拒绝', async () => {
  // 构造 alg=none 的 JWT（无签名）：header.payload.空签名
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url')
  const payload = Buffer.from(
    JSON.stringify({ sub: '90001', role: 'admin', iat: Math.floor(Date.now() / 1000), exp: Math.floor(Date.now() / 1000) + 3600 }),
  ).toString('base64url')
  const none_token = `${header}.${payload}.`
  const r = await api.get('/user/me', { token: none_token })
  expect(r.code).toBe(ERR.NOT_LOGIN)
})

test('登出: 调用 /auth/logout → 返回 ok', async () => {
  const { token } = await loginAs(api, '13800138000', '123456')
  const r = await api.post('/auth/logout', {}, { token })
  expect(r.code).toBe(0)
})

test('登出后: 带原 token 调 /user/me 仍可能通过（计划 R3.13/3.14：JWT 无服务端吊销）', async () => {
  // 这一条是已知缺陷：计划 §3.1 R3.13/3.14 记录了「JWT 无黑名单阻断」，
  // 我们把它固化为一个【预期失败】的断言：期望被拒（NOT_LOGIN），但实际可能仍通过。
  // 用 test.fail() 标记，让报告里能看到这个预期失败（= 缺陷已被记录）。
  const { token } = await loginAs(api, '13800138000', '123456')
  await api.post('/auth/logout', {}, { token })
  const r = await api.get('/user/me', { token })
  // 期望被拒（期望失败以暴露缺陷）
  expect(r.code).toBe(ERR.NOT_LOGIN)
})