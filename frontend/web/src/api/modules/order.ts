import request from '@/api/request'
import type { PageResp } from '@/api/request'

// 购物车项
export interface CartItem {
  id: number
  skuId: number
  productId: number
  productName: string
  productImage: string
  price: number
  quantity: number
  selected: boolean
  specValues?: Record<string, string>
  /** 是否有效（商品已下架/删除/售罄 => false，前端据此标记失效并排除结算） */
  valid?: boolean
  /** 失效原因（如「商品已下架」「已售罄」），valid=false 时后端返回 */
  invalidReason?: string | null
}

// 订单
export interface Order {
  id: number
  orderNo: string
  userId: number
  totalAmount: number
  payAmount: number
  freightAmount: number
  status: 'pending_pay' | 'paid' | 'shipping' | 'done' | 'cancelled' | 'refunding' | 'refunded'
  items: OrderItem[]
  receiverName?: string
  receiverPhone?: string
  receiverAddress?: string
  createdAt: string
  /** 该订单最近一张售后单摘要，后端 buildOrderDetail 挂载 */
  aftersale?: OrderAftersale | null
  /** 是否还能发起售后：订单状态允许 且 无进行中的售后单 */
  canApplyAftersale?: boolean
  /** done 订单是否已全部评价完（前端「待评价」tab 据此剔除已评价订单） */
  reviewedAll?: boolean
}

/** 订单详情里携带的售后摘要 */
export interface OrderAftersale {
  id: number
  type: string
  typeText: string
  status: string
  statusText: string
  amount: number
  refundAmount: number | null
  reason?: string
  handleNote?: string
  createdAt?: string
  isFinal: boolean
}

export interface OrderItem {
  id: number
  productId: number
  productName: string
  productImage: string
  price: number
  quantity: number
}

export const orderApi = {
  // 购物车
  getCart() {
    return request<CartItem[]>({
      url: '/cart',
      method: 'GET',
    })
  },

  // 加入购物车
  addCart(params: { skuId: number; quantity: number }) {
    return request<void>({
      url: '/cart',
      method: 'POST',
      data: params,
    })
  },

  // 更新购物车项
  updateCart(id: number, params: { quantity?: number; selected?: boolean }) {
    return request<void>({
      url: `/cart/${id}`,
      method: 'PUT',
      data: params,
    })
  },

  // 删除购物车项
  deleteCart(ids: number[]) {
    return request<void>({
      url: '/cart',
      method: 'DELETE',
      data: { ids },
    })
  },

  // 提交订单
  submitOrder(params: {
    cartIds: number[]
    addressId: number
    couponId?: number
    remark?: string
    donate?: number
    /** 使用的鱼干数（10 鱼干=¥1，后端按 10% 上限截断） */
    pointsUse?: number
  }) {
    return request<Order>({
      url: '/order',
      method: 'POST',
      data: params,
    })
  },

  // 订单列表
  // status: 单状态过滤（兼容旧调用）；statuses: 多状态 CSV（前端"已完成"=done+refunded 用此）
  listOrders(status?: string, page = 1, size = 10, statuses?: string[]) {
    const params: Record<string, any> = { page, size }
    if (statuses && statuses.length) {
      params.statuses = statuses.join(',')
    } else if (status) {
      params.status = status
    }
    return request<PageResp<Order>>({
      url: '/order/list',
      method: 'GET',
      params,
    })
  },

  // 订单详情
  orderDetail(orderNo: string) {
    return request<Order>({
      url: `/order/${orderNo}`,
      method: 'GET',
    })
  },

  // 发起支付
  pay(orderNo: string, channel: 'alipay') {
    return request<{
      success: boolean
      payUrl?: string
      /** 电脑网站支付：支付宝返回的收银台表单 HTML，注入页面后 submit 即跳转 */
      pageBody?: string
      outTradeNo?: string
      demoMode?: boolean
    }>({
      url: `/pay/${orderNo}`,
      method: 'POST',
      // returnBase：前端当前 origin（动态），避免支付宝回跳硬编码 localhost 在代理/预览域名下 404
      data: { channel, returnBase: window.location.origin },
    })
  },

  // 订单支付结果轮询（沙箱异步通知不可达时由前端驱动主动查询支付宝）
  orderPayStatus(orderNo: string) {
    return request<{ status: string; paid: boolean }>({
      url: `/order/pay/status`,
      method: 'GET',
      params: { orderNo },
    })
  },

  // 取消订单
  cancelOrder(orderNo: string) {
    return request<void>({
      url: `/order/${orderNo}/cancel`,
      method: 'POST',
    })
  },

  // 确认收货
  receiveOrder(orderNo: string) {
    return request<void>({
      url: `/order/${orderNo}/receive`,
      method: 'POST',
    })
  },

  // 物流轨迹
  logistics(orderNo: string) {
    return request<Array<{ title: string; time: string; done: boolean; desc: string }>>({
      url: `/order/${orderNo}/logistics`,
      method: 'GET',
    })
  },

  // 清空失效购物车商品
  clearInvalidCart() {
    return request<{ removed: number }>({
      url: `/cart/clear-invalid`,
      method: 'DELETE',
    })
  },

  // 清空购物车全部商品
  clearCart() {
    return request<{ removed: number }>({
      url: `/cart/clear`,
      method: 'DELETE',
    })
  },
}