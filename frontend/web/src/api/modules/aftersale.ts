import request from '@/api/request'
import type { PageResp } from '@/api/request'

/** 售后类型 */
export type AftersaleType = 'REFUND_ONLY' | 'RETURN_REFUND' | 'EXCHANGE'

/** 售后状态（与后端 RefundStatus 保持一致） */
export type AftersaleStatus =
  | 'pending' // 待商家处理
  | 'returning' // 已同意退货，待寄回
  | 'exchanging' // 已同意换货，待寄回
  | 'returned' // 已寄回，待商家收货
  | 'reshipped' // 商家已重发（换货）
  | 'refunded' // 退款成功
  | 'exchanged' // 换货完成
  | 'rejected' // 商家驳回
  | 'appealing' // 平台介入中
  | 'arbitrated' // 平台已裁定
  | 'closed' // 已撤销

export interface AftersaleItem {
  product_id: number
  product_name: string
  product_image: string
  price: number
  quantity: number
}

/** 进度时间线节点 */
export interface AftersaleTimelineNode {
  id?: number
  actor_type: 'user' | 'merchant' | 'admin' | 'system'
  actor_name?: string
  action: string
  from_status?: string
  to_status?: string
  note?: string
  created_at: string
}

export interface Aftersale {
  id: number
  order_no: string
  refund_type: AftersaleType
  status: AftersaleStatus
  typeText: string
  statusText: string
  amount: number
  refund_amount: number | null
  reason?: string
  handle_note?: string
  store_name?: string
  created_at: string
  handled_at?: string
  shipped_at?: string
  finished_at?: string
  // 退货 / 换货
  return_address?: string
  logistics_no?: string
  logistics_company?: string
  new_logistics_no?: string
  new_logistics_company?: string
  reshipped_at?: string
  // 平台介入
  appeal_reason?: string
  appealed_at?: string
  arbitrate_result?: 'refund' | 'reject'
  arbitrate_note?: string
  arbitrated_at?: string
  // 后端算好的动作开关，前端直接用，避免各页面各写一套状态判断
  canFillLogistics: boolean
  canCancel: boolean
  canAppeal: boolean
  canReceive: boolean
  isFinal: boolean
  evidenceList: string[]
  appealImageList: string[]
  items?: AftersaleItem[]
  timeline?: AftersaleTimelineNode[]
}

export interface AftersaleCount {
  total: number
  processing: number
  /** 待买家寄回，需要用户操作 */
  todo: number
}

export const aftersaleApi = {
  /** 我的售后列表 */
  list(params: { status?: string; page?: number; size?: number } = {}) {
    return request<PageResp<Aftersale>>({
      url: '/refund/list',
      method: 'GET',
      params,
    })
  },

  /** 售后数量统计（角标） */
  count() {
    return request<AftersaleCount>({
      url: '/refund/count',
      method: 'GET',
    })
  },

  /** 售后详情（含进度时间线） */
  detail(id: number) {
    return request<Aftersale>({
      url: `/refund/${id}`,
      method: 'GET',
    })
  },

  /** 按订单号查最近一张售后单 */
  byOrder(orderNo: string) {
    return request<Aftersale | null>({
      url: `/refund/by-order/${orderNo}`,
      method: 'GET',
    })
  },

  /** 提交售后申请 */
  apply(
    orderNo: string,
    data: { type: AftersaleType; reason: string; images?: string[] },
  ) {
    return request<{ success: boolean; orderNo: string; refundId: number }>({
      url: `/order/${orderNo}/refund`,
      method: 'POST',
      data,
    })
  },

  /** 回填寄回物流 */
  fillLogistics(id: number, data: { logisticsCompany: string; logisticsNo: string }) {
    return request<void>({
      url: `/refund/${id}/logistics`,
      method: 'POST',
      data,
    })
  },

  /** 换货：确认收到重发商品 */
  confirmReceive(id: number) {
    return request<void>({
      url: `/refund/${id}/receive`,
      method: 'POST',
    })
  },

  /** 撤销售后申请 */
  cancel(id: number) {
    return request<void>({
      url: `/refund/${id}/cancel`,
      method: 'POST',
    })
  },

  /** 申请平台客服介入 */
  appeal(id: number, data: { reason: string; images?: string[] }) {
    return request<void>({
      url: `/refund/${id}/appeal`,
      method: 'POST',
      data,
    })
  },
}

/** 状态 → Element Plus tag 类型，列表与详情共用 */
export function aftersaleTagType(
  status: AftersaleStatus,
): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  switch (status) {
    case 'refunded':
    case 'exchanged':
      return 'success'
    case 'rejected':
      return 'danger'
    case 'appealing':
      return 'warning'
    case 'closed':
    case 'arbitrated':
      return 'info'
    default:
      return 'primary'
  }
}

/** 常用快递公司，回填物流下拉用 */
export const LOGISTICS_COMPANIES = [
  '顺丰速运',
  '京东物流',
  '中通快递',
  '圆通速递',
  '申通快递',
  '韵达速递',
  '邮政EMS',
  '极兔速递',
  '德邦快递',
]
