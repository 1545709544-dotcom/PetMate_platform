import request from '@/api/request'
import type { PageResp } from '@/api/request'

// 运营仪表盘
export interface DashboardOverview {
  totalUsers: number
  totalMerchants: number
  activeMerchants: number
  bannedMerchants: number
  totalOrders: number
  totalSales: number
  todaySales: number
  todayOrders: number
  todayActiveUsers: number
  todayNewUsers: number
  avgOrderAmount: number
  conversionRate: number
  stockAlerts: number
  pendingAudits: number
  pendingTickets: number
  positiveRate: number
  vipCount: number
  abnormalCount: number
}

// 用户列表项
export interface AdminUser {
  id: number
  nickname: string
  avatar: string
  phone: string
  vipLevel: number
  status: 0 | 1 | 2
  registerDate: string
  lastLoginAt?: string
  /** VIP 到期时间（ISO 字符串）。L0 或永久有效时为 null/undefined。 */
  vipExpireAt?: string | null
}

// 商家列表项
export interface AdminMerchant {
  id: number
  name: string
  ownerName: string
  ownerPhone: string
  type: string
  /** 0=营业中 1=已封禁 2=已注销/退出 */
  status: 0 | 1 | 2
  auditStatus: 0 | 1 | 2
  submitDate: string
  approvedAt?: string
  /** 门店地址（短串，可选） */
  address?: string
  /** 关联的店主 userId（用于跳转用户详情） */
  ownerUserId?: number
  /** 订单/商品统计（可选，详情页才返回） */
  orderCount?: number
  productCount?: number
  serviceCount?: number
}

// 举报项
export interface Report {
  id: number
  reporterId: number
  targetType: string
  targetId: number
  reason: string
  status: 'pending' | 'resolved' | 'rejected'
  handleNote?: string
  createdAt: string
  handledAt?: string
}

// ============ 敏感词 ============
export interface SensitiveWordDTO {
  id: number
  word: string
  category: string
  level: number
  action: string // block(拦截) / mask(替换*) / review(送审)
  enabled?: boolean | number // 是否启用（true/1 启用；停用词不进入 DFA 词库）
  hitCount: number
  createdAt: string
}

export interface SensitiveTestHit {
  word: string
  category: string
  level: number
  action: string
  start: number
  end: number
  matched: string
}

export const SENSITIVE_CATEGORIES = ['政治', '暴力', '色情', '广告', '侮辱', '其他'] as const
export const SENSITIVE_LEVELS = [
  { value: 1, label: '低' },
  { value: 2, label: '中' },
  { value: 3, label: '高' },
]
export const SENSITIVE_ACTIONS = [
  { value: 'block', label: '拦截' },
  { value: 'mask', label: '替换*' },
  { value: 'review', label: '送审' },
]

export const adminApi = {
  // 仪表盘概览
  overview() {
    return request<DashboardOverview>({
      url: '/admin/dashboard',
      method: 'GET',
    })
  },

  // 用户列表（支持状态/会员等级/关键字/注册日期筛选）
  userList(params: {
    page?: number
    size?: number
    status?: number
    vip?: number
    keyword?: string
    dateFrom?: string
    dateTo?: string
  } = {}) {
    return request<PageResp<AdminUser>>({
      url: '/admin/user/list',
      method: 'GET',
      params,
    })
  },

  // 封禁/解封用户
  banUser(userId: number, banned: boolean) {
    return request<void>({
      url: `/admin/user/${userId}/ban`,
      method: 'POST',
      data: { banned },
    })
  },

  // 编辑用户（昵称/手机号/会员等级/状态）
  editUser(userId: number, data: { nickname?: string; phone?: string; vipLevel?: number; status?: number }) {
    return request<void>({
      url: `/admin/user/${userId}`,
      method: 'PUT',
      data,
    })
  },

  // 新增虚拟用户
  createUser(data: { nickname: string; phone: string; password?: string; vipLevel?: number }) {
    return request<{ id: number; nickname: string; phone: string }>({
      url: '/admin/user/create',
      method: 'POST',
      data,
    })
  },

  // 发送系统通知（记操作日志，无独立消息表）
  notifyUser(data: { title?: string; content?: string; target?: string }) {
    return request<void>({
      url: '/admin/notify',
      method: 'POST',
      data,
    })
  },

  // ============ 入驻申请审核（v4 新增；旧版商家管理/审核接口已删除） ============
  applyList(page = 1, size = 20, status?: number) {
    return request<PageResp<Record<string, any>>>({
      url: '/admin/merchant/apply/list',
      method: 'GET',
      params: { page, size, status },
    })
  },
  auditApply(applyId: number, approved: boolean, reason?: string) {
    return request<Record<string, any>>({
      url: '/admin/merchant/apply/audit',
      method: 'POST',
      data: { applyId, approved, reason },
    })
  },

  // ============ 已入驻商家管理 ============
  // 商家列表（支持状态/类型/关键字筛选）
  merchantList(params: {
    page?: number
    size?: number
    status?: number
    type?: string
    keyword?: string
    dateFrom?: string
    dateTo?: string
  } = {}) {
    return request<PageResp<AdminMerchant>>({
      url: '/admin/merchant/list',
      method: 'GET',
      params,
    })
  },
  // 商家详情
  merchantDetail(id: number) {
    return request<Record<string, any>>({
      url: `/admin/merchant/${id}`,
      method: 'GET',
    })
  },
  // 启用 / 封禁商家（status: 0 营业中 / 1 封禁）
  toggleMerchantStatus(id: number, status: 0 | 1, reason?: string) {
    return request<void>({
      url: `/admin/merchant/${id}/status`,
      method: 'POST',
      data: { status, reason },
    })
  },
  // 强制解除关联（严重违规：下架门店 + 禁用账号 + 注销）
  forceRemoveMerchant(id: number, reason: string) {
    return request<void>({
      url: `/admin/merchant/${id}/force-remove`,
      method: 'POST',
      data: { reason },
    })
  },

  // 帖子审核
  postAuditList(status: 'pending' | 'approved' | 'rejected' = 'pending', page = 1, size = 20) {
    return request<PageResp<{ id: number; content: string; userNickname: string; createdAt: string }>>({
      url: '/admin/post/audit',
      method: 'GET',
      params: { status, page, size },
    })
  },

  // 审核帖子
  auditPost(postId: number, approved: boolean, reason?: string) {
    return request<void>({
      url: '/admin/post/audit',
      method: 'POST',
      data: { postId, approved, reason },
    })
  },

  // 操作日志（审计）：支持模块/操作人/关键字/时间区间过滤
  logs(params: {
    page?: number
    size?: number
    module?: string
    operator?: string
    keyword?: string
    start?: string
    end?: string
  } = {}) {
    return request<PageResp<AdminLogItem>>({
      url: '/admin/log',
      method: 'GET',
      params,
    })
  },

  // 敏感词列表（分页 + 筛选）
  sensitiveList(params: {
    page?: number
    size?: number
    keyword?: string
    category?: string
    enabled?: '' | '0' | '1'
  }) {
    return request<PageResp<SensitiveWordDTO>>({
      url: '/admin/sensitive',
      method: 'GET',
      params,
    })
  },

  // 新增敏感词（单条或批量）
  sensitiveAdd(payload: { word?: string; words?: string[]; category?: string; level?: number; action?: string }) {
    return request<{ added: number; skipped: number }>({
      url: '/admin/sensitive',
      method: 'POST',
      data: payload,
    })
  },

  // 编辑敏感词
  sensitiveUpdate(id: number, payload: { word?: string; category?: string; level?: number; action?: string }) {
    return request<void>({
      url: `/admin/sensitive/${id}`,
      method: 'PUT',
      data: payload,
    })
  },

  // 删除敏感词（按 id）
  sensitiveDelete(id: number) {
    return request<void>({
      url: `/admin/sensitive/${id}`,
      method: 'DELETE',
    })
  },

  // 批量删除敏感词（按 id 列表）
  sensitiveBatchDelete(ids: number[]) {
    return request<{ deleted: number }>({
      url: '/admin/sensitive/batch-delete',
      method: 'POST',
      data: { ids },
    })
  },

  // 批量启停敏感词（enabled=true 启用 / false 停用；停用词不进入 DFA 词库）
  sensitiveBatchStatus(ids: number[], enabled: boolean) {
    return request<{ updated: number }>({
      url: '/admin/sensitive/batch-status',
      method: 'POST',
      data: { ids, enabled },
    })
  },

  // 敏感词测试（不计入命中统计）
  sensitiveTest(text: string) {
    return request<SensitiveTestHit[]>({
      url: '/admin/sensitive/test',
      method: 'POST',
      data: { text },
    })
  },

  // 举报列表（支持按状态 / 类型 / 关键词筛选 + 分页）
  reportList(options: {
    status?: 'pending' | 'resolved' | 'rejected'
    targetType?: string
    keyword?: string
    page?: number
    size?: number
  } = {}) {
    const { status = 'pending', targetType, keyword, page = 1, size = 20 } = options
    return request<PageResp<Report>>({
      url: '/admin/report/list',
      method: 'GET',
      params: { status, targetType, keyword, page, size },
    })
  },

  // 处理举报（可附带对被举报内容的处置动作：remove / ban / none）
  handleReport(
    id: number,
    action: 'resolve' | 'reject',
    note?: string,
    targetAction?: 'remove' | 'ban' | 'none',
  ) {
    return request<{ id: number; status: string; targetAction: string }>({
      url: `/admin/report/${id}/handle`,
      method: 'POST',
      data: { action, note, targetAction },
    })
  },

  // 解析举报目标（评论可定位到所属帖子），用于后台「查看」跳转
  reportTarget(id: number) {
    return request<{ type: string; targetId: number; postId?: number; snippet?: string }>({
      url: `/admin/report/${id}/target`,
      method: 'GET',
    })
  },

  // 用户详情
  userDetail(id: number) {
    return request<Record<string, any>>({
      url: `/admin/user/${id}`,
      method: 'GET',
    })
  },

  // 订单管理列表（status / keyword 过滤）
  orderList(params: { page?: number; size?: number; status?: string; keyword?: string }) {
    return request<PageResp<Record<string, any>>>({
      url: '/admin/order/list',
      method: 'GET',
      params,
    })
  },

  // 退款申请列表
  refundList(params: { status?: string; page?: number; size?: number }) {
    return request<PageResp<Record<string, any>>>({
      url: '/admin/order/refund',
      method: 'GET',
      params,
    })
  },

  // 售后单进度时间线（仲裁时看完整往来）
  refundTimeline(id: number) {
    return request<Record<string, any>[]>({
      url: `/admin/order/refund/${id}/timeline`,
      method: 'GET',
    })
  },

  // 售后各状态计数（Tab 角标，介入工单要显眼）
  refundStat() {
    return request<Record<string, number>>({
      url: '/admin/order/refund/stat',
      method: 'GET',
    })
  },

  // 处理售后：approve 同意 / confirm 确认收货 / reject 驳回 / arbitrate 平台仲裁
  // arbitrate 时必须带 result：refund=支持买家，reject=维持商家决定
  refundHandle(
    id: number,
    action: 'approve' | 'confirm' | 'reject' | 'arbitrate',
    note?: string,
    result?: 'refund' | 'reject',
  ) {
    return request<void>({
      url: `/admin/order/refund/${id}/handle`,
      method: 'POST',
      data: { action, note, result },
    })
  },

  // ============ 商品管理（管理端） ============
  productList(params: { page?: number; size?: number; keyword?: string; status?: string } = {}) {
    return request<PageResp<Record<string, any>>>({
      url: '/admin/product/list',
      method: 'GET',
      params,
    })
  },
  toggleProductStatus(id: number, status: number) {
    return request<void>({
      url: `/admin/product/${id}/status`,
      method: 'POST',
      data: { status },
    })
  },
  deleteProduct(id: number) {
    return request<void>({
      url: `/admin/product/${id}`,
      method: 'DELETE',
    })
  },

  // ============ 分类管理 ============
  categories() {
    return request<Array<Record<string, any>>>({
      url: '/admin/category/list',
      method: 'GET',
    })
  },
  createCategory(data: { name: string; parentId: number; icon?: string; sort?: number; level?: number }) {
    return request<Record<string, any>>({
      url: '/admin/category',
      method: 'POST',
      data,
    })
  },
  updateCategory(id: number, data: Record<string, any>) {
    return request<void>({
      url: `/admin/category/${id}`,
      method: 'PUT',
      data,
    })
  },
  deleteCategory(id: number) {
    return request<void>({
      url: `/admin/category/${id}`,
      method: 'DELETE',
    })
  },

  // 仪表盘 funnel 漏斗
  funnel() {
    return request<Record<string, any>>({
      url: '/admin/dashboard/funnel',
      method: 'GET',
    })
  },

  // 仪表盘 客流趋势（近 N 天）
  visitorTrend(range: '7d' | '30d' | '90d' = '7d') {
    return request<Array<{ date: string; orderCount: number; visitorCount: number }>>({
      url: '/admin/dashboard/visitor-trend',
      method: 'GET',
      params: { range },
    })
  },

  // 仪表盘 品类销售分布
  categoryShare() {
    return request<Array<{ id: number; name: string; amount: number; orderCount: number }>>({
      url: '/admin/dashboard/category-share',
      method: 'GET',
    })
  },

  // 仪表盘 热销商品 TOP N（按销量）
  hotProducts(limit = 10) {
    return request<
      Array<{
        id: number
        name: string
        category_id: number
        main_image: string
        sale_price: number
        original_price: number
        sale_count: number
        rating: number
        status: number
      }>
    >({
      url: '/admin/dashboard/hot-products',
      method: 'GET',
      params: { limit },
    })
  },

  // 系统参数配置（通用 key-value 管理，t_system_config）
  systemConfigList() {
    return request<Array<{ config_key: string; config_value: string; updated_at: string }>>({
      url: '/admin/system-config',
      method: 'GET',
    })
  },
  updateSystemConfig(config: Record<string, string>) {
    return request<void>({
      url: '/admin/system-config',
      method: 'PUT',
      data: config,
    })
  },

  // ============ 轮播 Banner 管理 ============
  bannerList() {
    return request<Array<Record<string, any>>>({
      url: '/admin/banner/list',
      method: 'GET',
    })
  },
  createBanner(data: Record<string, any>) {
    return request<void>({
      url: '/admin/banner',
      method: 'POST',
      data,
    })
  },
  updateBanner(id: number, data: Record<string, any>) {
    return request<void>({
      url: `/admin/banner/${id}`,
      method: 'PUT',
      data,
    })
  },
  deleteBanner(id: number) {
    return request<void>({
      url: `/admin/banner/${id}`,
      method: 'DELETE',
    })
  },
  toggleBannerStatus(id: number, status: 0 | 1) {
    return request<void>({
      url: `/admin/banner/${id}/status`,
      method: 'PUT',
      data: { status },
    })
  },

  // ============ 平台公告管理 ============
  announcementList(page = 1, size = 20) {
    return request<Array<Record<string, any>>>({
      url: '/admin/announcement/list',
      method: 'GET',
      params: { page, size },
    })
  },
  createAnnouncement(data: Record<string, any>) {
    return request<void>({
      url: '/admin/announcement',
      method: 'POST',
      data,
    })
  },
  updateAnnouncement(id: number, data: Record<string, any>) {
    return request<void>({
      url: `/admin/announcement/${id}`,
      method: 'PUT',
      data,
    })
  },
  deleteAnnouncement(id: number) {
    return request<void>({
      url: `/admin/announcement/${id}`,
      method: 'DELETE',
    })
  },
  pushAnnouncement(id: number) {
    return request<void>({
      url: `/admin/announcement/${id}/push`,
      method: 'POST',
    })
  },

  // ============ 秒杀管理 ============
  seckillList() {
    return request<Array<Record<string, any>>>({
      url: '/admin/seckill/list',
      method: 'GET',
    })
  },
  createSeckill(data: Record<string, any>) {
    return request<Record<string, any>>({
      url: '/admin/seckill',
      method: 'POST',
      data,
    })
  },
  updateSeckill(id: number, data: Record<string, any>) {
    return request<void>({
      url: `/admin/seckill/${id}`,
      method: 'PUT',
      data,
    })
  },
  toggleSeckillStatus(id: number) {
    return request<void>({
      url: `/admin/seckill/${id}/toggle`,
      method: 'POST',
    })
  },
  deleteSeckill(id: number) {
    return request<void>({
      url: `/admin/seckill/${id}`,
      method: 'DELETE',
    })
  },

  // ============ 优惠券管理 ============
  couponList() {
    return request<Array<Record<string, any>>>({
      url: '/admin/coupon/list',
      method: 'GET',
    })
  },
  createCoupon(data: Record<string, any>) {
    return request<Record<string, any>>({
      url: '/admin/coupon',
      method: 'POST',
      data,
    })
  },
  updateCoupon(id: number, data: Record<string, any>) {
    return request<void>({
      url: `/admin/coupon/${id}`,
      method: 'PUT',
      data,
    })
  },
  deleteCoupon(id: number) {
    return request<void>({
      url: `/admin/coupon/${id}`,
      method: 'DELETE',
    })
  },

  // ============ 紧急熔断 ============
  getEmergencyBreak() {
    return request<{ enabled: boolean; updatedBy: string; updatedAt: string }>({
      url: '/admin/risk/emergency-break',
      method: 'GET',
    })
  },
  setEmergencyBreak(enabled: boolean) {
    return request<{ enabled: boolean; updatedBy: string; updatedAt: string }>({
      url: '/admin/risk/emergency-break',
      method: 'PUT',
      data: { enabled },
    })
  },

  // ============ 角色 / 权限（用 t_system_config 存 JSON） ============
  roles() {
    return request<{
      roles: AdminRole[]
      permTree: AdminPermModule[]
      permCatalog: string[]
    }>({
      url: '/admin/roles',
      method: 'GET',
    })
  },
  saveRoles(roles: AdminRole[]) {
    return request<void>({
      url: '/admin/roles',
      method: 'PUT',
      data: { roles },
    })
  },
}

// ============ 角色 / 权限 DTO ============
export interface AdminRole {
  id: string
  name: string
  description?: string
  /** 内置角色（如 admin）不可删除，admin 必须保留 * */
  builtin?: boolean
  perms: string[]
}

export interface AdminPermModule {
  /** 模块 key，如 user / merchant */
  module: string
  /** 模块中文名 */
  label: string
  perms: Array<{ key: string; label: string }>
}

// ============ 操作日志（审计）项 ============
export interface AdminLogItem {
  id: number
  operatorId: number
  operatorName: string | null
  role: string | null
  module: string
  action: string
  target: string
  detail: string
  ip: string
  createdAt: string
}