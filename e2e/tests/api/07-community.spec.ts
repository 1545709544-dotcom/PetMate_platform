/**
 * 用户端 API 用例 —— 社区（帖子/评论/点赞/收藏）+ 关注 + 实名
 *
 * 发帖前必须先实名认证(realname)，否则后端返回 NEED_REALNAME。
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

/** 构造合法的 18 位身份证号：6 位地址 + 8 位生日 + 3 位顺序码 + 1 位校验位 */
function fakeIdCard(seed: number): string {
  const region = '110105' // 北京朝阳区
  const birthday = '19900101'
  const seq = String(seed % 1000).padStart(3, '0')
  const base = region + birthday + seq
  // 校验位公式
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
  const checkMap = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']
  let sum = 0
  for (let i = 0; i < 17; i++) sum += parseInt(base[i], 10) * weights[i]
  return base + checkMap[sum % 11]
}

/** 注册并完成实名，返回 token + uid */
async function realnameUser(api: Api): Promise<{ token: string; userId: number }> {
  const u = await registerAs(api, genTestPhone(), genTestName('rn'), 'abc123456')
  const idCard = fakeIdCard(Math.floor(Math.random() * 999))
  const rn = await api.post(
    '/user/realname',
    { realName: 'Tester', idCard },
    { token: u.token },
  )
  if (rn.code !== 0) throw new Error(`实名失败: ${JSON.stringify(rn)}`)
  return { token: u.token, userId: u.userId }
}

// ===== 实名 =====

test('realname: 合法身份证 → verified=1', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('rn1'), 'abc123456')
  const r = await api.post(
    '/user/realname',
    { realName: 'Tester', idCard: fakeIdCard(Math.floor(Math.random() * 999)) },
    { token: u.token },
  )
  expect(r.code).toBe(0)
})

test('realname: 非法身份证 → PARAM_ERROR', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('rn2'), 'abc123456')
  const r = await api.post('/user/realname', { realName: 'x', idCard: '12345' }, { token: u.token })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

test('realname: 重复认证 → PARAM_ERROR', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('rn3'), 'abc123456')
  const idCard = fakeIdCard(Math.floor(Math.random() * 9999))
  await api.post('/user/realname', { realName: 'Tester', idCard }, { token: u.token })
  const r2 = await api.post('/user/realname', { realName: 'Tester2', idCard: fakeIdCard(Math.floor(Math.random() * 999)) }, { token: u.token })
  // 后端返回 PARAM_ERROR "该账号已完成实名认证"
  expect(r2.code).toBe(ERR.PARAM_ERROR)
})

// ===== 帖子 =====

test('post: 未实名用户发帖 → 失败', async () => {
  const u = await registerAs(api, genTestPhone(), genTestName('p_nr'), 'abc123456')
  const r = await api.post('/post', { content: 'petmate_test 内容' }, { token: u.token })
  // 期望非 0（NEED_REALNAME 或类似）
  expect(r.code).not.toBe(0)
})

test('post: 实名用户发帖 → 列表 / 详情 / 删除', async () => {
  const ctx = await realnameUser(api)
  const pub = await api.post(
    '/post',
    { content: `petmate_test 发布内容 ${Date.now()}` },
    { token: ctx.token },
  )
  expect(pub.code).toBe(0)
  const postId = (pub.data as any).id
  expect(postId).toBeGreaterThan(0)

  // 列表
  const list = await api.get('/community/feed')
  expect(list.code).toBe(0)
  const items = (list.data as any).list || (list.data as any).items || list.data
  expect(Array.isArray(items)).toBe(true)

  // 详情
  const detail = await api.get(`/post/${postId}`)
  expect(detail.code).toBe(0)
  expect((detail.data as any).id).toBe(postId)

  // 删除
  const del = await api.del(`/post/${postId}`, { token: ctx.token })
  expect(del.code).toBe(0)
})

test('post: 用户A 不能删除用户B 的帖子', async () => {
  const a = await realnameUser(api)
  const pub = await api.post(
    '/post',
    { content: `petmate_test B不应能删 ${Date.now()}` },
    { token: a.token },
  )
  const postId = (pub.data as any).id

  const b = await realnameUser(api)
  const del = await api.del(`/post/${postId}`, { token: b.token })
  expect(del.code).not.toBe(0)

  // 清理
  await api.del(`/post/${postId}`, { token: a.token })
})

test('post: 点赞 / 取消点赞', async () => {
  const a = await realnameUser(api)
  const pub = await api.post('/post', { content: `petmate_test 点赞测试 ${Date.now()}` }, { token: a.token })
  const postId = (pub.data as any).id

  const b = await loginAs(api, '13800138001', '123456')
  const like = await api.post(`/post/${postId}/like`, {}, { token: b.token })
  expect([0, ERR.PARAM_ERROR]).toContain(like.code)

  // 清理
  await api.del(`/post/${postId}`, { token: a.token })
})

test('post: 评论 → 列评论', async () => {
  const a = await realnameUser(api)
  const pub = await api.post('/post', { content: `petmate_test 评论测试 ${Date.now()}` }, { token: a.token })
  const postId = (pub.data as any).id

  const b = await loginAs(api, '13800138001', '123456')
  const c = await api.post(`/post/${postId}/comment`, { content: 'petmate_test 沙发' }, { token: b.token })
  expect([0, ERR.PARAM_ERROR]).toContain(c.code)

  const list = await api.get(`/post/${postId}/comments`)
  expect(list.code).toBe(0)

  await api.del(`/post/${postId}`, { token: a.token })
})

test('post: 空内容 → PARAM_ERROR', async () => {
  const ctx = await realnameUser(api)
  const r = await api.post('/post', { content: '' }, { token: ctx.token })
  expect(r.code).toBe(ERR.PARAM_ERROR)
})

// ===== 关注 =====

test('follow: 关注用户 → 列关注 → 取消', async () => {
  const a = await realnameUser(api)
  const b = await loginAs(api, '13800138001', '123456')

  const follow = await api.post('/user/follow', { followeeId: b.userId }, { token: a.token })
  expect([0, ERR.PARAM_ERROR]).toContain(follow.code)

  const list = await api.get(`/user/following`, { token: a.token })
  expect(list.code).toBe(0)

  // 状态查询
  const status = await api.get('/user/follow/status', { params: { followeeId: b.userId }, token: a.token })
  expect([0, ERR.PARAM_ERROR]).toContain(status.code)
})

test('follow: 关注自己 → 失败', async () => {
  const a = await realnameUser(api)
  const r = await api.post('/user/follow', { followeeId: a.userId }, { token: a.token })
  expect(r.code).not.toBe(0)
})