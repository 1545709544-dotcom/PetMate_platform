import request from '@/api/request'
import type { ServiceMode } from './service'

export type AppointmentStatus = 'pending' | 'confirmed' | 'cancelled' | 'done' | 'rejected'

/** 用户端预约单 */
export interface Appointment {
  id: number
  serviceId: number
  serviceName: string
  serviceCover?: string
  storeId: number
  storeName: string
  storeAddress?: string
  storePhone?: string
  category?: string
  // 规格 / 加购 / 宠物
  specId?: number
  specName?: string
  addonNames?: string
  petId?: number
  petName?: string
  // 服务方式
  serviceMode?: ServiceMode
  address?: string
  contactName?: string
  contactPhone?: string
  // 时间
  appointmentTime: string
  endTime?: string
  days?: number
  duration?: number
  // 金额（到店支付，仅记录应付）
  baseAmount?: number
  addonAmount?: number
  onsiteFee?: number
  amount?: number
  // 核销与评价
  verifyCode?: string
  reviewed?: boolean
  note?: string
  cancelReason?: string
  status: AppointmentStatus
  createTime: string
}

/** 下单入参 */
export interface AppointmentPayload {
  serviceId: number
  specId?: number
  addonIds?: number[]
  petId?: number
  days?: number
  serviceMode?: ServiceMode
  appointmentTime: string       // "yyyy-MM-dd HH:mm:ss"
  contactName: string
  contactPhone: string
  address?: string              // 上门服务必填
  note?: string
}

/** 评价入参 */
export interface ReviewPayload {
  rating: number
  content?: string
  images?: string[]
}

/** 商家端预约单（含预约人信息与核销码） */
export interface MerchantAppointment extends Appointment {
  userId?: number
  userName?: string
  userPhone?: string
  userAvatar?: string
}

export interface MerchantAppointmentStat {
  pendingCount: number
  confirmedCount: number
  todoCount: number
  doneCount: number
  cancelledCount: number
  rejectedCount: number
  todayCount: number
  doneAmount: number
}

export const appointmentApi = {
  /** 创建预约（到店支付，只记录应付金额） */
  create(payload: AppointmentPayload) {
    return request<{ id: number; status: string; amount: number; verifyCode: string }>({
      url: '/appointment',
      method: 'POST',
      data: payload,
    })
  },

  /** 我的预约列表（可按状态 / 分类筛选） */
  list(params: { status?: string; category?: string } = {}) {
    return request<Appointment[]>({
      url: '/appointment/list',
      method: 'GET',
      params,
    })
  },

  /** 预约详情（含门店地址电话、费用明细、核销码） */
  detail(id: number | string) {
    return request<Appointment>({
      url: `/appointment/${id}`,
      method: 'GET',
    })
  },

  /** 变更预约状态（用户侧仅 cancelled / done） */
  updateStatus(id: number, status: AppointmentStatus, reason?: string) {
    return request<{ id: number; status: string }>({
      url: `/appointment/${id}/status`,
      method: 'POST',
      data: { status, reason },
    })
  },

  /** 服务完成后评价，回写服务评分与销量 */
  review(id: number, payload: ReviewPayload) {
    return request<{ id: number; rating: number }>({
      url: `/appointment/${id}/review`,
      method: 'POST',
      data: payload,
    })
  },

  // ============ 商家端 ============
  /** 本商家门店的预约单（状态 / 日期 / 关键词筛选 + 各状态计数） */
  merchantList(params: { status?: string; date?: string; keyword?: string; serviceId?: number } = {}) {
    return request<{ list: MerchantAppointment[]; total: number; stat: MerchantAppointmentStat }>({
      url: '/merchant/appointments',
      method: 'GET',
      params,
    })
  },

  /** 商家侧预约详情（含宠物档案、评价） */
  merchantDetail(id: number | string) {
    return request<MerchantAppointment & { pet?: Record<string, unknown>; review?: Record<string, unknown> }>({
      url: `/merchant/appointment/${id}`,
      method: 'GET',
    })
  },

  /** 商家变更预约状态（确认 / 完成 / 取消 / 拒绝，取消与拒绝需填原因） */
  merchantUpdateStatus(id: number, status: AppointmentStatus, reason?: string) {
    return request<{ id: number; status: string }>({
      url: `/merchant/appointment/${id}/status`,
      method: 'POST',
      data: { status, reason },
    })
  },

  /** 商家改期（校验营业时段与余位） */
  merchantReschedule(id: number, appointmentTime: string) {
    return request<{ id: number; appointmentTime: string; status: string }>({
      url: `/merchant/appointment/${id}/reschedule`,
      method: 'POST',
      data: { appointmentTime },
    })
  },

  /** 到店核销：输入 6 位核销码完成服务 */
  merchantVerify(code: string) {
    return request<MerchantAppointment>({
      url: '/merchant/appointment/verify',
      method: 'POST',
      data: { code },
    })
  },

  /** 商家回复服务评价（与商品评价 /merchant/review 区分） */
  merchantReplyReview(reviewId: number, reply: string) {
    return request<void>({
      url: `/merchant/reviews/${reviewId}/reply`,
      method: 'POST',
      data: { reply },
    })
  },

  /** 商家侧评价列表 */
  merchantReviews(params: { serviceId?: number; unrepliedOnly?: boolean; page?: number; size?: number } = {}) {
    return request<{ list: Array<Record<string, unknown>>; total: number }>({
      url: '/merchant/reviews',
      method: 'GET',
      params,
    })
  },
}
