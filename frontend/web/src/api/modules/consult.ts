import request from '@/api/request'
import { tokenManager } from '@/utils/auth'

// AI 问诊消息
export interface ConsultMessage {
  id: number
  consultationId: number
  role: 'user' | 'assistant' | 'system'
  content: string
  riskLevel?: 'urgent' | 'normal' | 'good'
  products?: RecommendProduct[]
  images?: string[]
  /** 流式输出进行中（逐字渲染） */
  streaming?: boolean
  /** 是否为进入页面时的 AI 自我介绍（不展示"仅供参考"免责声明） */
  isIntro?: boolean
  createdAt: string
}

/** 流式问诊的 SSE 事件回调 */
export interface ConsultStreamHandlers {
  /** 收到一段文本片段 */
  onToken: (chunk: string) => void
  /** 流式结束，携带落库后的完整元信息 */
  onDone: (data: {
    id: number
    consultationId: number
    userMessageId?: number
    riskLevel?: 'urgent' | 'normal' | 'good'
    products?: RecommendProduct[]
    createdAt: string
  }) => void
  /** 流式异常（连接级 / 后端 error 事件） */
  onError?: (message: string) => void
}

export interface RecommendProduct {
  id: number
  name: string
  image: string
  price: number
}

export interface Consultation {
  id: number
  petId: number
  title: string
  status: 'active' | 'closed'
  riskLevel: 'urgent' | 'normal' | 'good'
  messageCount: number
  createdAt: string
  /** 历史列表里的首条 user 消息摘要（后端附带） */
  preview?: string
}

export type ConsultHistoryRange = 'today' | '7d' | '30d' | 'all'

export const consultApi = {
  // 发送消息（同步，整段返回；保留兼容）
  send(params: { consultationId?: number; petId?: number; content: string; images?: string[] }) {
    return request<ConsultMessage & { userMessageId?: number }>({
      url: '/consult/send',
      method: 'POST',
      data: params,
    })
  },

  /**
   * 流式发送（SSE）。用 fetch 读取 text/event-stream，逐事件回调。
   * 事件协议（后端定义）：
   *   event: token  data: {"chunk":"..."}
   *   event: done   data: {"id","consultationId","userMessageId","riskLevel","products","createdAt"}
   *   event: error  data: {"message":"..."}
   */
  sendStream(
    params: { consultationId?: number; petId?: number; content: string; images?: string[] },
    handlers: ConsultStreamHandlers,
  ): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const token = tokenManager.get()
      const base = import.meta.env.VITE_API_BASE_URL || '/api/v1'
      fetch(`${base}/consult/send/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify(params),
      })
        .then((resp) => {
          if (!resp.ok || !resp.body) {
            reject(new Error(`问诊服务异常（HTTP ${resp.status}）`))
            return
          }
          const reader = resp.body.getReader()
          const decoder = new TextDecoder()
          let buf = ''

          const handleEvent = (raw: string) => {
            let name = ''
            const dataLines: string[] = []
            for (const line of raw.split('\n')) {
              if (line.startsWith('event:')) name = line.slice(6).trim()
              else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
            }
            const dataStr = dataLines.join('\n')
            if (!dataStr) return
            let data: any
            try {
              data = JSON.parse(dataStr)
            } catch {
              return
            }
            if (name === 'token' && handlers.onToken) handlers.onToken(data.chunk ?? '')
            else if (name === 'done' && handlers.onDone) handlers.onDone(data)
            else if (name === 'error' && handlers.onError) handlers.onError(data.message ?? 'AI 服务异常')
          }

          const pump = (): Promise<void> =>
            reader.read().then(({ done, value }) => {
              if (done) {
                if (buf.trim()) handleEvent(buf)
                resolve()
                return
              }
              buf += decoder.decode(value, { stream: true })
              let idx: number
              while ((idx = buf.indexOf('\n\n')) >= 0) {
                const raw = buf.slice(0, idx)
                buf = buf.slice(idx + 2)
                handleEvent(raw)
              }
              return pump()
            })

          return pump()
        })
        .catch((e) => reject(e))
    })
  },

  // 问诊历史
  // range: today / 7d / 30d / all  （默认 all）
  history(range: ConsultHistoryRange = 'all') {
    return request<Consultation[]>({
      url: '/consult/list',
      method: 'GET',
      params: { range },
    })
  },

  // 问诊会话详情
  detail(consultationId: number) {
    return request<ConsultMessage[]>({
      url: `/consult/${consultationId}/messages`,
      method: 'GET',
    })
  },

  // 创建新会话
  create(petId?: number) {
    return request<Consultation>({
      url: '/consult/create',
      method: 'POST',
      data: { petId },
    })
  },

  /** AI 问诊额度：普通用户最多 10 次，返回 { limit, used, remaining, unlimited } */
  quota() {
    return request<{ limit: number; used: number; remaining: number; unlimited: boolean }>({
      url: '/consult/quota',
      method: 'GET',
    })
  },

  // 删除会话
  remove(consultationId: number) {
    return request<void>({
      url: `/consult/${consultationId}`,
      method: 'DELETE',
    })
  },

  // 删除某条用户消息里指定索引的图片（永久删除，后端落库）
  removeMessageImage(messageId: number, index: number) {
    return request<void>({
      url: `/consult/message/${messageId}/image`,
      method: 'DELETE',
      params: { index },
    })
  },
}