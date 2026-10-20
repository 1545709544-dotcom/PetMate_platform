/**
 * 前端埋点上报
 *
 * - 事件先入本地队列，按「阈值 / 定时 / 页面隐藏」三种时机批量 flush
 * - 优先用 navigator.sendBeacon（页面卸载也能发出），降级用 fetch(keepalive)
 * - 匿名上报：后端 /track/event 不强制鉴权（未登录 userId=0），无需带 token
 *
 * 用法：
 *   import { trackEvent } from '@/api/track'
 *   trackEvent('pv', { page: 'product_detail', targetType: 'product', targetId: 3001 })
 *   trackEvent('addCart', { targetType: 'product', targetId: 3001 })
 */

export interface TrackEventInput {
  event: string
  page?: string
  targetType?: string
  targetId?: number | string
  merchantId?: number
  payload?: string
  traceId?: string
}

interface QueuedEvent {
  event: string
  page?: string
  targetType?: string
  targetId?: number | string
  merchantId?: number
  payload?: string
  traceId?: string
}

const queue: QueuedEvent[] = []
let timer: number | null = null
const FLUSH_DELAY = 4000
const FLUSH_THRESHOLD = 10

function endpoint(): string {
  // 与 axios baseURL 保持一致（dev: /api/v1，走 Vite 代理；prod: 同源）
  const base = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  return `${location.origin}${base}/track/event`
}

function flush() {
  if (timer != null) {
    clearTimeout(timer)
    timer = null
  }
  if (!queue.length) return
  const batch = queue.splice(0, queue.length)
  const body = JSON.stringify({ events: batch })
  try {
    if (typeof navigator !== 'undefined' && navigator.sendBeacon) {
      const ok = navigator.sendBeacon(endpoint(), new Blob([body], { type: 'application/json' }))
      if (ok) return
    }
  } catch {
    /* sendBeacon 失败，走 fetch 降级 */
  }
  fetch(endpoint(), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body,
    keepalive: true,
  }).catch(() => {
    /* 上报失败不应影响主流程，静默丢弃 */
  })
}

function scheduleFlush() {
  if (timer != null) return
  timer = window.setTimeout(() => {
    timer = null
    flush()
  }, FLUSH_DELAY)
}

export function trackEvent(event: string, opts: Omit<TrackEventInput, 'event'> = {}): void {
  queue.push({ event, ...opts })
  if (queue.length >= FLUSH_THRESHOLD) flush()
  else scheduleFlush()
}

if (typeof document !== 'undefined') {
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden') flush()
  })
  window.addEventListener('pagehide', flush)
}
