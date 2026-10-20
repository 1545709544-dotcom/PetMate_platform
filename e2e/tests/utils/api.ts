/**
 * API 直接调用 helper —— 绕过 Vite 代理，直连后端 8080
 *
 * 测试 API 层用例时用这个，可以：
 *  - 跑得比 UI 快
 *  - 直接拿到 HTTP 状态码 / 响应体
 *  - 失败时更容易定位是前端 bug 还是后端 bug
 */

import { APIRequestContext, request as pwRequest } from '@playwright/test'
import { API_BASE } from './constants.js'

export interface ApiResp<T = any> {
  code: number
  msg: string
  data: T | null
  traceId?: string
}

function url(path: string): string {
  // API_BASE 已经含 /api/v1，path 以 / 开头
  return `${API_BASE}${path}`
}

export class Api {
  private ctx: APIRequestContext

  constructor(ctx: APIRequestContext) {
    this.ctx = ctx
  }

  static async create(): Promise<Api> {
    // 不传 baseURL（Playwright 内部解析时会把 baseURL 的 path 当作 context-path，
    // 与 /api/v1 重复出现，导致后端找不到路由 → 返回 HTML 404）。
    const ctx = await pwRequest.newContext()
    return new Api(ctx)
  }

  async dispose() {
    await this.ctx.dispose()
  }

  async get<T = any>(path: string, opts: { token?: string; params?: any } = {}): Promise<ApiResp<T>> {
    const headers: Record<string, string> = {}
    if (opts.token) headers.Authorization = `Bearer ${opts.token}`
    const res = await this.ctx.get(url(path), { headers, params: opts.params })
    return res.json() as Promise<ApiResp<T>>
  }

  async post<T = any>(path: string, body: any, opts: { token?: string } = {}): Promise<ApiResp<T>> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (opts.token) headers.Authorization = `Bearer ${opts.token}`
    const res = await this.ctx.post(url(path), { headers, data: body })
    return res.json() as Promise<ApiResp<T>>
  }

  async put<T = any>(path: string, body: any, opts: { token?: string } = {}): Promise<ApiResp<T>> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (opts.token) headers.Authorization = `Bearer ${opts.token}`
    const res = await this.ctx.put(url(path), { headers, data: body })
    return res.json() as Promise<ApiResp<T>>
  }

  async del<T = any>(path: string, opts: { token?: string; data?: any } = {}): Promise<ApiResp<T>> {
    const headers: Record<string, string> = { 'Content-Type': 'application/json' }
    if (opts.token) headers.Authorization = `Bearer ${opts.token}`
    const res = await this.ctx.delete(url(path), { headers, data: opts.data })
    return res.json() as Promise<ApiResp<T>>
  }
}

/**
 * 登录拿 token —— 全项目接口都返回 {code,msg,data:{token,userId,...}}
 */
export async function loginAs(
  api: Api,
  phone: string,
  password: string,
): Promise<{ token: string; userId: number; role: string; nickname: string }> {
  const r = await api.post('/auth/login', {
    loginType: 'password',
    phone,
    password,
  })
  if (r.code !== 0 || !r.data) {
    throw new Error(`登录失败: phone=${phone} resp=${JSON.stringify(r)}`)
  }
  return {
    token: r.data.token,
    userId: r.data.userId,
    role: r.data.role,
    nickname: r.data.nickname,
  }
}

export async function registerAs(
  api: Api,
  phone: string,
  nickname: string,
  password: string,
): Promise<{ token: string; userId: number; role: string; nickname: string }> {
  const r = await api.post('/auth/register', { phone, nickname, password })
  if (r.code !== 0 || !r.data) {
    throw new Error(`注册失败: phone=${phone} resp=${JSON.stringify(r)}`)
  }
  return {
    token: r.data.token,
    userId: r.data.userId,
    role: r.data.role,
    nickname: r.data.nickname,
  }
}

/** 标准错误码 */
export const ERR = {
  PARAM_ERROR: 10001,
  NOT_LOGIN: 20001,
  ACCOUNT_BANNED: 20003,
  PASSWORD_ERROR: 20002,
  SYSTEM_ERROR: 10500,
  STOCK_NOT_ENOUGH: 30001,
  PRODUCT_OFFLINE: 30002,
  ORDER_NOT_FOUND: 40001,
  ORDER_STATUS_INVALID: 40002,
  OK: 0,
} as const