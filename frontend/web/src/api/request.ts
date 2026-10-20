import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
  type AxiosResponse,
} from 'axios'
import { ElMessage } from 'element-plus'
import { tokenManager } from '@/utils/auth'

/**
 * axios 二次封装
 * - 统一响应格式（PRD §7.1）：{ code, msg, data, traceId }
 * - 错误码处理（PRD §7.3）
 * - Idempotency-Key（PRD §7.4）
 */

// 错误码段映射
const ERROR_CODE_MSG: Record<string, string> = {
  '20001': '未登录，请重新登录',
  '20002': '账号或密码错误',
  '20003': '账号已被封禁',
  '30001': '库存不足',
  '30002': '商品已下架',
  '40001': '订单不存在',
  '40002': '订单状态异常',
  '50001': '余额不足',
  '50002': '支付超时',
  '60001': '内容包含敏感词',
  '70001': 'AI 服务繁忙，请稍后再试',
  '90001': '无权限操作',
}

const instance: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // FormData 上传:删除默认 Content-Type,让 axios 自动加 multipart boundary
    if (typeof FormData !== 'undefined' && config.data instanceof FormData && config.headers) {
      // axios v1 的 AxiosHeaders 提供 delete 方法;同时降级兼容 plain object
      const h: any = config.headers
      if (typeof h.delete === 'function') {
        h.delete('Content-Type')
        h.delete('content-type')
      } else {
        delete h['Content-Type']
        delete h['content-type']
      }
    }

    // 注入 token(走 storage 封装,值是裸 JWT 字符串,不是 JSON 包装)
    const token = tokenManager.get()
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }

    // 注入幂等键（写操作）
    if (
      config.method &&
      ['post', 'put', 'delete', 'patch'].includes(config.method.toLowerCase())
    ) {
      const idemKey = `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
      if (config.headers) {
        config.headers['Idempotency-Key'] = idemKey
      }
    }

    return config
  },
  (error) => Promise.reject(error),
)

// 响应拦截器
instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const res = response.data
    // 二进制流（文件下载）直接返回
    if (response.config.responseType === 'blob') return response

    if (res.code !== 0) {
      const msg = ERROR_CODE_MSG[String(res.code)] || res.msg || '请求失败'
      ElMessage.error(msg)

      // 会话已失效：后端把「未登录 / 账号封禁 / 账号注销」以业务码下发（HTTP 仍是 200），
      // 此前只在 HTTP 401 时清会话，导致被封禁的账号顶着旧 token 一路弹错误提示却退不出登录。
      // JWT 有效期 7 天，必须在这里强制下线。
      if (res.code === 20001 || res.code === 20003) {
        tokenManager.clear()
        const target = window.location.pathname.startsWith('/admin') ? '/admin/login' : '/login'
        if (window.location.pathname !== target) {
          setTimeout(() => {
            window.location.href = target
          }, 600)
        }
      }

      // 标记该错误已由全局拦截器统一提示过，业务层 catch 不要再重复弹窗（避免双提示）
      const err = new Error(msg) as Error & { __handled?: boolean }
      err.__handled = true
      return Promise.reject(err)
    }
    return res.data
  },
  (error) => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        ElMessage.error('登录已过期，请重新登录')
        tokenManager.clear()
        window.location.href = '/login'
      } else if (status === 403) {
        ElMessage.error('无权访问')
      } else if (status === 404) {
        ElMessage.error('资源不存在')
      } else if (status >= 500) {
        ElMessage.error('服务器异常，请稍后再试')
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  },
)

export default request

export { instance }

// 通用类型：分页响应
export interface PageResp<T> {
  list: T[]
  total: number
  page: number
  size: number
}

// 通用类型：统一响应
export interface ApiResp<T = unknown> {
  code: number
  msg: string
  data: T
  traceId?: string
}

/**
 * 请求函数 —— 包裹 axios，让 TS 类型反映拦截器的解包行为（返回 T 而非 AxiosResponse<T>）。
 */
async function request<T = unknown>(config: AxiosRequestConfig): Promise<T> {
  // 响应拦截器已 return res.data，此处拿到的即业务数据本身。
  // axios 的类型仍标注为 AxiosResponse<T>，故强制断言为 T，避免再取一次 .data 造成双重解包。
  return instance(config) as unknown as Promise<T>
}