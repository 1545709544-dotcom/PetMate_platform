import { http, HttpResponse } from 'msw'

/**
 * MSW 默认 handler 集合。
 *
 * 【设计原则】
 * 这里只放「happy path」——各接口的正常成功响应。
 * 异常场景（业务错误码、HTTP 500、超时）不写在这里，而是由具体用例
 * 用 server.use() 就近覆盖。理由：把异常响应做成全局默认，会让用例读起来
 * 不知道自己在测什么，也容易互相干扰。
 *
 * 【响应格式】
 * 严格遵循后端统一格式（PRD §7.1）：{ code, msg, data, traceId }。
 * 注意后端所有响应的 HTTP 状态码都是 200，业务码在 body.code
 * （这是后端计划 B-E1 记录的设计问题，前端完全依赖 body.code 判断）。
 */

/** 统一成功响应包装。 */
export function ok<T>(data: T) {
  return HttpResponse.json({
    code: 0,
    msg: 'success',
    data,
    traceId: 'test-trace-id',
  })
}

/**
 * 统一业务错误响应包装。
 *
 * 注意：HTTP 状态码仍是 200 —— 如实还原后端行为。
 * 若某个用例要测 HTTP 层错误（401/403/500），请直接用
 * `new HttpResponse(null, { status: 401 })`。
 */
export function fail(code: number, msg = '业务失败') {
  return HttpResponse.json({
    code,
    msg,
    data: null,
    traceId: 'test-trace-id',
  })
}

/** 接口基础路径，与 request.ts 的 baseURL 保持一致。 */
export const API = '*/api/v1'

export const handlers = [
  // ---------- 认证 ----------
  http.post(`${API}/auth/login`, () =>
    ok({
      token: 'test-jwt-token',
      user: { id: 1001, nickname: '测试用户', role: 'user' },
    }),
  ),

  http.post(`${API}/auth/logout`, () => ok(null)),

  // ---------- 用户 ----------
  http.get(`${API}/user/profile`, () =>
    ok({
      id: 1001,
      nickname: '测试用户',
      phone: '138****8888',
      role: 'user',
      vipLevel: 0,
      verified: false,
      status: 1,
    }),
  ),

  // ---------- 购物车 ----------
  http.get(`${API}/cart`, () => ok({ items: [] })),
  http.post(`${API}/cart`, () => ok(null)),
  http.put(`${API}/cart/*`, () => ok(null)),
  http.delete(`${API}/cart/*`, () => ok(null)),

  // ---------- 埋点 ----------
  // track.ts 不走 request.ts 封装，直接 fetch/sendBeacon。
  // 这里兜住，避免各用例因埋点上报未 mock 而触发 onUnhandledRequest: 'error'。
  http.post(`${API}/event/report`, () => ok(null)),
  http.post(`${API}/event`, () => ok(null)),

  // ---------- 元数据 ----------
  http.get(`${API}/category/tree`, () => ok([])),
]
