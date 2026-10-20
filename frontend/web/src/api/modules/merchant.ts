import request from '@/api/request'

// 商家工作台 KPI
export interface DashboardKpi {
  todayOrders: number
  todaySales: number
  pendingOrders: number
  newCustomers: number
}

// 销售看板
export interface SalesData {
  date: string
  orderCount: number
  salesAmount: number
  visitorCount: number
  conversionRate: number
}

// 商家门店
export interface Store {
  id: number
  merchantId: number
  name: string
  type: 'grooming' | 'medical' | 'boarding' | 'comprehensive'
  address: string
  lng: number
  lat: number
  businessHours: Record<string, string>
  photos: string[]
  logo: string
  description: string
  auditStatus: 0 | 1 | 2
  productCount?: number
  serviceCount?: number
  reviewCount?: number
  appointmentCount?: number
}

// 商家评价
export interface Review {
  id: number
  orderId: number
  userId: number
  userNickname: string
  userAvatar: string
  rating: number
  content: string
  images?: string[]
  reply?: string
  repliedAt?: string
  createdAt: string
  // 服务评价（GET /merchant/reviews）走 t_service_review，没有 orderId，
  // 用「服务名 · 规格名」定位被评价对象。后端已返回这几个字段，此处补齐类型。
  serviceId?: number
  serviceName?: string
  specName?: string
  appointmentId?: number
}

// 商家端服务评价（t_service_review，复数 /merchant/reviews）
export interface ServiceReview {
  id: number
  serviceId: number
  serviceName: string
  appointmentId: number
  userId: number
  nickname: string
  avatar?: string
  rating: number
  content: string
  images?: string[] | string | null
  specName?: string
  reply?: string
  createdAt: string
}

// 商家订单（兼容 snake_case 和 camelCase 字段）
export interface MerchantOrder {
  id: number
  orderNo?: string
  order_no?: string
  totalAmount?: number
  total_amount?: number
  payAmount?: number
  pay_amount?: number
  status: string
  createdAt?: string
  created_at?: string
  userId?: number
  user_id?: number
  buyerName?: string
  buyer_name?: string
  buyerPhone?: string
  buyer_phone?: string
  productSummary?: string
  product_summary?: string
  productImage?: string
  product_image?: string
}

// 商家订单列表（分页）
export interface MerchantOrderPage {
  list: MerchantOrder[]
  total: number
  page: number
  size: number
}

// 商家商品（兼容 snake_case 和 camelCase）
export interface MerchantProduct {
  id: number
  name: string
  salePrice?: number
  sale_price?: number
  originalPrice?: number
  original_price?: number
  status: string
  saleCount?: number
  sale_count?: number
  viewCount?: number
  view_count?: number
  mainImage?: string
  main_image?: string
  categoryId?: number
  categoryName?: string
  totalStock?: number
  total_stock?: number
  skuCount?: number
  sku_count?: number
}

// 商家侧商品详情（GET /merchant/product/{id}）
// 注意：JDBC 直出，skus 为 snake_case，specs / subImages 等 JSON 列是字符串，需前端解析
export interface MerchantProductDetail {
  id: number
  name: string
  categoryId?: number
  brandId?: number
  mainImage?: string
  subImages?: string | string[] | null
  detail?: string
  detailImages?: string | string[] | null
  status?: number | string
  salePrice?: number
  originalPrice?: number
  tags?: string | string[] | null
  specs?: string | ProductSpecGroup[] | null
  skus?: Array<{
    id: number
    sku_code?: string
    spec_values?: string | Record<string, string> | null
    price?: number
    stock?: number
    image?: string
  }>
}

// 规格组（如：净含量 -> [1kg, 2kg]）
export interface ProductSpecGroup {
  name: string
  values: string[]
}

// 提交时的单个 SKU（规格组合 + 价格 + 库存 + 图片）
export interface ProductSkuInput {
  id?: number
  specValues: Record<string, string>
  price: number
  stock: number
  image?: string
}

export interface MerchantAlert {
  id: string
  type: string
  tag: 'red' | 'gold' | 'blue'
  content: string
  user: string
  views: string
  source?: 'review' | 'order' | 'store'
}

export interface MerchantTodo {
  id: string
  icon: string
  color: string
  title: string
  desc: string
  urgent: boolean
}

export interface MerchantHeatmapItem {
  day: string
  date: string
  value: number
  rawCount: number
  peak: boolean
}

export interface MerchantFinanceRecord {
  id: number
  orderNo: string
  payAmount: number
  status: string
  date: string
  paidAt?: string | null
  settledAt?: string | null
}

// 售后单（11 态状态机）。后端 SQL 直出下划线字段，这里按原样声明避免映射时漏字段
export type RefundStateValue =
  | 'pending' | 'returning' | 'returned' | 'exchanging' | 'reshipped'
  | 'refunded' | 'exchanged' | 'rejected' | 'appealing' | 'arbitrated' | 'closed'

export interface MerchantRefundRow {
  id: number
  order_no: string
  user_id: number
  userName: string | null
  userPhone: string | null
  amount: number
  refund_amount: number | null
  reason: string | null
  status: RefundStateValue
  orig_status: string | null
  handle_note: string | null
  created_at: string
  handled_at: string | null
  shipped_at: string | null
  finished_at: string | null
  refund_type: 'REFUND_ONLY' | 'RETURN_REFUND' | 'EXCHANGE' | string
  evidence_images: string | null
  logistics_no: string | null
  logistics_company: string | null
  return_address: string | null
  new_logistics_no: string | null
  new_logistics_company: string | null
  reshipped_at: string | null
  appeal_reason: string | null
  appealed_at: string | null
  arbitrate_result: string | null
  arbitrate_note: string | null
  buyer_name: string | null
  buyer_phone: string | null
  total_amount: number | null
  pay_amount: number | null
  // 后端补充的展示字段
  typeText: string
  statusText: string
  isFinal: boolean
}

// ============ 服务项编辑（v13 行业化：规格分档 + 加购 + 时段容量 + 须知/退改）============
export interface ServiceSpecInput {
  id?: number
  name: string
  petKind?: 'all' | 'dog' | 'cat'
  coat?: string
  weightMin?: number | null
  weightMax?: number | null
  price: number
  duration: number
}

export interface ServiceAddonInput {
  id?: number
  name: string
  price: number
  duration: number
  description?: string
}

export interface ServiceCategoryNode {
  name: string
  subs: string[]
  icon: string
  priceUnit: string
  dailyPriced: boolean
}

export interface ServiceCatalogDict {
  categories: ServiceCategoryNode[]
  petTypes: Array<{ value: string; label: string }>
  serviceModes: Array<{ value: string; label: string }>
  coats: string[]
  petKinds: string[]
}

export interface ServiceEditPayload {
  name?: string
  category?: string
  subCategory?: string
  serviceMode?: 'instore' | 'onsite'
  petType?: 'all' | 'dog' | 'cat'
  price?: number
  originalPrice?: number
  onsiteFee?: number
  duration?: number
  cover?: string
  description?: string
  status?: number
  tags?: string
  includeItems?: string
  notice?: string
  refundPolicy?: string
  openTime?: string
  closeTime?: string
  slotMinutes?: number
  slotCapacity?: number
  advanceDays?: number
  specs?: ServiceSpecInput[]
  addons?: ServiceAddonInput[]
}

export const merchantApi = {
  // 工作台 KPI
  dashboard() {
    return request<DashboardKpi>({
      url: '/merchant/dashboard',
      method: 'GET',
    })
  },

  // 销售看板
  sales(range: '7d' | '30d' | '90d' = '7d') {
    return request<SalesData[]>({
      url: '/merchant/data/sales',
      method: 'GET',
      params: { range },
    })
  },

  // 客流分析（后端 MerchantController.visitor 与 sales 同解析，支持 90d）
  visitor(range: '7d' | '30d' | '90d' = '7d') {
    return request<SalesData[]>({
      url: '/merchant/data/visitor',
      method: 'GET',
      params: { range },
    })
  },

  // 转化漏斗（后端 MerchantController.convert 支持 90d）
  convert(range: '7d' | '30d' | '90d' = '7d') {
    return request<{
      pv: number
      uv: number
      cartCount: number
      orderCount: number
      paidCount: number
    }>({
      url: '/merchant/data/convert',
      method: 'GET',
      params: { range },
    })
  },

  // 门店信息
  store() {
    return request<Store>({
      url: '/merchant/store',
      method: 'GET',
    })
  },

  // 更新门店信息
  updateStore(store: Partial<Store>) {
    return request<Store>({
      url: '/merchant/store',
      method: 'PUT',
      data: store,
    })
  },

  // 上传图片（商品/相册/Logo 复用 /merchant/product/upload，返回 { urls: string[] }）
  uploadImages(files: File[]) {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return request<{ urls: string[] }>({
      url: '/merchant/product/upload',
      method: 'POST',
      data: form,
    })
  },

  // 评价列表
  reviews() {
    return request<Review[]>({
      url: '/merchant/review',
      method: 'GET',
    })
  },

  // 回复评价（商品评价，单数 /merchant/review）
  replyReview(reviewId: number, reply: string) {
    return request<void>({
      url: `/merchant/review/${reviewId}/reply`,
      method: 'POST',
      data: { reply },
    })
  },

  // 服务评价列表（t_service_review，复数 /merchant/reviews）
  serviceReviews(params: {
    serviceId?: number
    unrepliedOnly?: boolean
    page?: number
    size?: number
  } = {}) {
    return request<{ list: ServiceReview[]; total: number }>({
      url: '/merchant/reviews',
      method: 'GET',
      params,
    })
  },

  // 回复服务评价（复数 /merchant/reviews/{id}/reply）
  replyServiceReview(reviewId: number, reply: string) {
    return request<void>({
      url: `/merchant/reviews/${reviewId}/reply`,
      method: 'POST',
      data: { reply },
    })
  },

  // 财务结算
  finance() {
    return request<{
      totalIncome: number
      pendingSettlement: number
      settled: number
      records: Array<{
        id: number
        date: string
        amount: number
        status: 'pending' | 'settled'
      }>
    }>({
      url: '/merchant/finance',
      method: 'GET',
    })
  },

  // 商家订单列表（支持搜索 / 状态过滤 / 分页）
  orders(params?: {
    keyword?: string
    status?: string
    page?: number
    size?: number
  }) {
    return request<MerchantOrderPage>({
      url: '/merchant/order',
      method: 'GET',
      params,
    })
  },

  // 发货（paid -> shipping）
  shipOrder(orderNo: string) {
    return request<void>({
      url: `/merchant/order/${orderNo}/ship`,
      method: 'POST',
    })
  },

  // 商家商品列表（只读）
  products() {
    return request<MerchantProduct[]>({
      url: '/merchant/product',
      method: 'GET',
    })
  },

  // 商品图片本地上传（主图/副图/详情图/规格图，多图）
  uploadProductImages(files: File[]) {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return request<{ urls: string[] }>({
      url: '/merchant/product/upload',
      method: 'POST',
      data: form,
    })
  },

  // 新增商品
  createProduct(data: Partial<MerchantProduct> & {
    name: string
    categoryId: number
    mainImage?: string
    salePrice: number
    originalPrice?: number
    detail?: string
    status?: string
    subImages?: string[]
    detailImages?: string[]
    specs?: ProductSpecGroup[]
    skus?: ProductSkuInput[]
    stock?: number
  }) {
    return request<MerchantProduct>({
      url: '/merchant/product',
      method: 'POST',
      data,
    })
  },

  // 编辑商品
  updateProduct(id: number, data: Partial<MerchantProduct> & {
    subImages?: string[]
    detailImages?: string[]
    specs?: ProductSpecGroup[]
    skus?: ProductSkuInput[]
    stock?: number
  }) {
    return request<MerchantProduct>({
      url: `/merchant/product/${id}`,
      method: 'PUT',
      data,
    })
  },

  // 删除商品（软删）
  deleteProduct(id: number) {
    return request<void>({
      url: `/merchant/product/${id}`,
      method: 'DELETE',
    })
  },

  // 上下架切换（单个，后端取反）
  toggleProductStatus(id: number) {
    return request<MerchantProduct>({
      url: `/merchant/product/${id}/toggle-status`,
      method: 'POST',
    })
  },

  // 幂等设值上下架：批量操作必须用这个，避免"取反"在混合状态下把已上架的又下架（P0#9）
  setProductStatus(id: number, status: 0 | 1) {
    return request<{ id: number; status: number }>({
      url: `/merchant/product/${id}/status`,
      method: 'PUT',
      data: { status },
    })
  },

  // 商家侧商品详情（带归属校验，返回 status + 未软删的 skus，不计浏览量）（P0#14）
  productDetail(id: number | string) {
    return request<MerchantProductDetail>({
      url: `/merchant/product/${id}`,
      method: 'GET',
    })
  },

  // ============ 预约管理(线下服务履约) ============
  // 本商家门店的预约单(可按状态筛选)；后端返回 { list, total, stat }
  appointments(params: { status?: string } = {}) {
    return request<{
      list: import('@/api/modules/appointment').MerchantAppointment[]
      total: number
      stat: Record<string, number>
    }>({
      url: '/merchant/appointments',
      method: 'GET',
      params,
    })
  },

  // 商家变更预约状态(确认 confirmed / 完成 done / 取消 cancelled / 拒绝 rejected)
  // rejected / cancelled 必须带 reason，否则后端 400
  updateAppointmentStatus(
    id: number,
    status: 'confirmed' | 'done' | 'cancelled' | 'rejected',
    reason?: string,
  ) {
    return request<{ id: number; status: string }>({
      url: `/merchant/appointment/${id}/status`,
      method: 'POST',
      data: reason != null && reason !== '' ? { status, reason } : { status },
    })
  },

  // 商家改期：更新预约时间(appointmentTime: yyyy-MM-dd HH:mm:ss)
  rescheduleAppointment(id: number, appointmentTime: string) {
    return request<{ id: number; appointmentTime: string; status: string }>({
      url: `/merchant/appointment/${id}/reschedule`,
      method: 'POST',
      data: { appointmentTime },
    })
  },

  // ============ 数据看板扩展 ============
  // 7 天活跃热力图
  heatmap() {
    return request<MerchantHeatmapItem[]>({
      url: '/merchant/data/heatmap',
      method: 'GET',
    })
  },

  // 内容审核预警列表
  alerts() {
    return request<MerchantAlert[]>({
      url: '/merchant/data/alerts',
      method: 'GET',
    })
  },

  // 工作台待办
  todos() {
    return request<MerchantTodo[]>({
      url: '/merchant/todos',
      method: 'GET',
    })
  },

  // 商品库存聚合（真实库存 + 分类 + SKU 数）
  productStock() {
    return request<MerchantProduct[]>({
      url: '/merchant/product/stock',
      method: 'GET',
    })
  },

  // 财务结算明细（完整账单）
  financeRecords(params: { status?: string; limit?: number } = {}) {
    return request<MerchantFinanceRecord[]>({
      url: '/merchant/finance/records',
      method: 'GET',
      params,
    })
  },

  // 商家服务列表（含 cover/description/bookedCount）
  services() {
    return request<Array<{
      id: number
      name: string
      category: string
      price: number
      duration: number
      status: number
      cover: string | null
      description: string | null
      booked_count: number
    }>>({
      url: '/merchant/service',
      method: 'GET',
    })
  },

  // ============ 服务项 CRUD（v13 行业化） ============
  // 编辑表单用的标准分类字典：一级 + 二级 + 图标 + 计价单位 + 适用宠物/服务方式/毛长
  serviceCatalog() {
    return request<ServiceCatalogDict>({
      url: '/merchant/service/catalog',
      method: 'GET',
    })
  },
  getService(id: number) {
    return request<Record<string, any>>({
      url: `/merchant/service/${id}`,
      method: 'GET',
    })
  },
  createService(data: ServiceEditPayload) {
    return request<{ id: number }>({
      url: '/merchant/service',
      method: 'POST',
      data,
    })
  },
  updateService(id: number, data: ServiceEditPayload) {
    return request<void>({
      url: `/merchant/service/${id}`,
      method: 'PUT',
      data,
    })
  },
  deleteService(id: number) {
    return request<void>({
      url: `/merchant/service/${id}`,
      method: 'DELETE',
    })
  },

  // ============ 排班 (business_hours) ============
  getSchedule() {
    return request<{ schedule: Record<string, string> }>({
      url: '/merchant/schedule',
      method: 'GET',
    })
  },
  saveSchedule(schedule: Record<string, string>) {
    return request<void>({
      url: '/merchant/schedule',
      method: 'PUT',
      data: { schedule },
    })
  },

  // ============ 相册 (store.photos) ============
  savePhotos(photos: string[]) {
    return request<void>({
      url: '/merchant/store/photos',
      method: 'PUT',
      data: { photos },
    })
  },

  // ============ 商家订单详情 ============
  orderDetail(orderNo: string) {
    return request<Record<string, any>>({
      url: `/merchant/order/${orderNo}`,
      method: 'GET',
    })
  },

  // ============ 商家入驻申请（匿名） ============
  applyMerchant(body: Record<string, any>) {
    return request<{ id: number; status: number }>({
      url: '/merchant/apply',
      method: 'POST',
      data: body,
    })
  },
  // 营业执照 / 资质图片上传（匿名）
  uploadLicense(file: File) {
    const form = new FormData()
    form.append('file', file)
    return request<{ url: string }>({
      url: '/merchant/apply/upload',
      method: 'POST',
      data: form,
    })
  },
  queryApplyStatus(phone: string) {
    return request<Record<string, any>>({
      url: '/merchant/apply/status',
      method: 'GET',
      params: { phone },
    })
  },

  // ============ 客户管理 ============
  customers(keyword?: string) {
    return request<Array<{
      userId: number
      nickname: string
      avatar: string | null
      phone: string
      vipLevel: number
      orderCount: number
      totalAmount: number
      lastOrderAt: string
    }>>({
      url: '/merchant/customers',
      method: 'GET',
      params: keyword ? { keyword } : undefined,
    })
  },

  // ============ 订单备注 ============
  updateOrderRemark(orderNo: string, remark: string) {
    return request<void>({
      url: `/merchant/order/${orderNo}/remark`,
      method: 'PUT',
      data: { remark },
    })
  },

  // ============ 消息通知中心 ============
  notifications(params: { page?: number; size?: number; type?: string; unreadOnly?: boolean }) {
    return request<{
      list: Array<{
        id: number
        type: string
        title: string
        content: string | null
        relatedId: string | null
        isRead: number
        createdAt: string
      }>
      total: number
      unreadCount: number
      page: number
      size: number
    }>({
      url: '/merchant/notifications',
      method: 'GET',
      params,
    })
  },

  notificationUnreadCount() {
    return request<{ count: number }>({
      url: '/merchant/notifications/unread-count',
      method: 'GET',
    })
  },

  readNotification(id: number) {
    return request<void>({
      url: `/merchant/notifications/${id}/read`,
      method: 'POST',
    })
  },

  readAllNotifications() {
    return request<void>({
      url: '/merchant/notifications/read-all',
      method: 'POST',
    })
  },

  // ============ RBAC 员工管理（仅店主） ============
  staffList() {
    return request<Array<{
      id: number
      username: string
      nickname: string
      role: string
      status: number
      createdAt: string
    }>>({
      url: '/merchant/staff',
      method: 'GET',
    })
  },

  createStaff(body: { username: string; nickname?: string; password?: string; role: string }) {
    return request<{ id: number }>({
      url: '/merchant/staff',
      method: 'POST',
      data: body,
    })
  },

  updateStaff(id: number, body: { nickname?: string; role?: string; status?: number }) {
    return request<void>({
      url: `/merchant/staff/${id}`,
      method: 'PUT',
      data: body,
    })
  },

  deleteStaff(id: number) {
    return request<void>({
      url: `/merchant/staff/${id}`,
      method: 'DELETE',
    })
  },

  roleDefs() {
    return request<Array<{
      key: string
      name: string
      desc: string
      perms: string[]
    }>>({
      url: '/merchant/roles',
      method: 'GET',
    })
  },

  // ============ 操作审计日志 ============
  operationLogs(params: { page?: number; size?: number; module?: string }) {
    return request<{
      list: Array<{
        id: number
        operatorName: string
        role: string
        module: string
        action: string
        target: string
        detail: string
        createdAt: string
      }>
      total: number
    }>({
      url: '/merchant/operation-logs',
      method: 'GET',
      params,
    })
  },

  // ============ 退款处理（商家侧售后） ============
  // 列出本商家售后单
  // status: todo(需商家动作) / processing(进行中) / handled(已完结) / all / 或单个状态值
  refunds(params: { status?: 'todo' | 'processing' | 'handled' | 'all' | string; page?: number; size?: number } = {}) {
    return request<{
      list: MerchantRefundRow[]
      total: number
      pendingCount: number
      todoCount: number
      returnedCount: number
      appealingCount: number
      refundedCount: number
      exchangedCount: number
      rejectedCount: number
    }>({
      url: '/merchant/refunds',
      method: 'GET',
      params,
    })
  },

  // 售后进度时间线
  refundTimeline(id: number) {
    return request<Array<Record<string, any>>>({
      url: `/merchant/refund/${id}/timeline`,
      method: 'GET',
    })
  },

  // 同意售后
  // 仅退款 → 一步到位；退货退款/换货 → 必须带 returnAddress，否则后端 400
  approveRefund(
    id: number,
    payload: { note?: string; returnAddress?: string; refundAmount?: number } = {},
  ) {
    return request<void>({
      url: `/merchant/refund/${id}/approve`,
      method: 'POST',
      data: payload,
    })
  },

  // 确认收到买家寄回的商品（退货退款 → 完成退款；换货 → 流转到待重发）
  confirmRefund(id: number, note?: string) {
    return request<void>({
      url: `/merchant/refund/${id}/confirm`,
      method: 'POST',
      data: note ? { note } : {},
    })
  },

  // 换货重新发货
  reshipRefund(id: number, payload: { logisticsCompany: string; logisticsNo: string; note?: string }) {
    return request<void>({
      url: `/merchant/refund/${id}/reship`,
      method: 'POST',
      data: payload,
    })
  },

  // 驳回售后
  rejectRefund(id: number, note: string) {
    return request<void>({
      url: `/merchant/refund/${id}/reject`,
      method: 'POST',
      data: { note },
    })
  },
}