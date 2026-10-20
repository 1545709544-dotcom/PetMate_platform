import type { RouteRecordRaw } from 'vue-router'

/**
 * 全部 68 个路由定义（PRD §5.1-5.3）
 * - layout 标记决定使用哪套布局壳
 * - meta.requiresAuth: 是否需要登录
 * - meta.source: 来源 Stitch HTML（迁移追溯）
 */

export type LayoutType = 'user' | 'merchant' | 'admin' | 'blank'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    layout?: LayoutType
    requiresAuth?: boolean
    // 实名认证门禁：未实名用户访问该路由时拦截并引导去 /user/edit 完成认证
    requiresVerified?: boolean
    source?: string
    hideInMenu?: boolean
  }
}

const Placeholder = () => import('@/components/_stub/PlaceholderPage.vue')

const routes: RouteRecordRaw[] = [
  // ==============================================
  // 用户端（30 个页面，PRD §5.1）
  // ==============================================
  {
    path: '/',
    component: () => import('@/layouts/UserLayout.vue'),
    meta: { layout: 'user' },
    children: [
      // P01 首页
      {
        path: '',
        name: 'Home',
        component: () => import('@/views/user/home/HomeView.vue'),
        meta: { title: 'PetMate 智能宠物管家', source: 'frontend/petmate_1/code.html' },
      },
      // P02 分类
      {
        path: 'category',
        name: 'Category',
        component: () => import('@/views/user/product/ProductListView.vue'),
        meta: { title: '商品分类', source: 'frontend/_19/code.html' },
      },
      // P02.1 商品浏览（"查看更多精品商品"目标页）
      {
        path: 'category/browse',
        name: 'CategoryBrowse',
        component: () => import('@/views/user/product/ProductBrowseView.vue'),
        meta: { title: '宠物商城', hideInMenu: true },
      },
      // P03 搜索
      {
        path: 'search',
        name: 'Search',
        component: () => import('@/views/user/product/SearchView.vue'),
        meta: { title: '搜索' },
      },
      // P04 商品详情
      {
        path: 'product/:id',
        name: 'ProductDetail',
        component: () => import('@/views/user/product/ProductDetailView.vue'),
        meta: { title: '商品详情', requiresAuth: false },
      },
      // P05 服务列表（本地化服务展示与预约入口）
      {
        path: 'service',
        name: 'ServiceList',
        component: () => import('@/views/user/product/ServiceListView.vue'),
        meta: { title: '本地服务', source: 'frontend/_15/code.html' },
      },
      // P05 服务详情
      {
        path: 'service/:id',
        name: 'ServiceDetail',
        component: () => import('@/views/user/product/ServiceDetailView.vue'),
        meta: { title: '服务详情', source: 'frontend/_15/code.html' },
      },
      // P06 购物车
      {
        path: 'cart',
        name: 'Cart',
        component: () => import('@/views/user/product/CartView.vue'),
        meta: { title: '购物车', requiresAuth: true, source: 'frontend/_14/code.html' },
      },
      // P07 结算
      {
        path: 'checkout',
        name: 'Checkout',
        component: () => import('@/views/user/product/CheckoutView.vue'),
        meta: { title: '确认订单', requiresAuth: true, source: 'frontend/_14/code.html' },
      },
      // P07.1 任务中心（鱼干积分）
      {
        path: 'task-center',
        name: 'TaskCenter',
        component: () => import('@/views/user/product/TaskCenterView.vue'),
        meta: { title: '任务中心', requiresAuth: true },
      },
      // P08 支付
      {
        path: 'pay/:orderNo',
        name: 'Pay',
        component: () => import('@/views/user/product/PayView.vue'),
        meta: { title: '订单支付', requiresAuth: true },
      },
      // P09 宠物列表 —— 已移除（宠物管理改为 /pet/detail/:id 内嵌 chip 切换器）
      // P10 宠物编辑
      {
        path: 'pet/edit/:id',
        name: 'PetEdit',
        component: () => import('@/views/user/pet/PetEditView.vue'),
        meta: { title: '宠物档案', requiresAuth: true, source: 'frontend/_9/code.html' },
      },
      // P10.1 创建宠物档案（3 步式向导）
      {
        path: 'pet/create',
        name: 'PetCreate',
        component: () => import('@/views/user/pet/PetCreateView.vue'),
        meta: { title: '创建宠物档案', requiresAuth: true, hideInMenu: true },
      },
      // P10.2 宠物详情
      {
        path: 'pet/detail/:id',
        name: 'PetDetail',
        component: () => import('@/views/user/pet/PetDetailView.vue'),
        meta: { title: '宠物档案', requiresAuth: true, hideInMenu: true },
      },
      // P10.3 成长日记照片墙（「查看更多照片」跳转目标）
      {
        path: 'pet/photos/:id',
        name: 'PetPhotos',
        component: () => import('@/views/user/pet/PetPhotosView.vue'),
        meta: { title: '成长日记', requiresAuth: true, hideInMenu: true },
      },
      // P11 健康档案
      {
        path: 'pet/health/:id',
        name: 'PetHealth',
        component: () => import('@/views/user/pet/PetHealthView.vue'),
        meta: { title: '健康档案', requiresAuth: true, source: 'frontend/_6/code.html' },
      },
      // P12 AI 问诊室
      {
        path: 'consult',
        name: 'Consult',
        component: () => import('@/views/user/consult/ConsultView.vue'),
        meta: { title: 'AI 智能问诊', requiresAuth: true, source: 'frontend/ai/code.html' },
      },
      // P13 问诊记录
      {
        path: 'consult/history',
        name: 'ConsultHistory',
        component: () => import('@/views/user/consult/ConsultHistoryView.vue'),
        meta: { title: '问诊记录', requiresAuth: true },
      },
      // P14 我的预约
      {
        path: 'appointments',
        name: 'Appointments',
        component: () => import('@/views/user/product/AppointmentsView.vue'),
        meta: { title: '我的预约', requiresAuth: true },
      },
      // P15 预约下单
      {
        path: 'appointment/booking/:id',
        name: 'AppointmentBooking',
        component: () => import('@/views/user/product/AppointmentBookingView.vue'),
        meta: { title: '预约下单', requiresAuth: true },
      },
      // P16 社区首页
      {
        path: 'community',
        name: 'Community',
        component: () => import('@/views/user/community/CommunityView.vue'),
        meta: { title: '社区动态', source: 'frontend/_22/code.html' },
      },
      // P16.1 爱心捐助排行
      {
        path: 'community/donate-rank',
        name: 'DonateRank',
        component: () => import('@/views/user/community/DonateRankView.vue'),
        meta: { title: '爱心捐助排行' },
      },
      // P16.2 宠物摄影大赛
      {
        path: 'community/photo-contest',
        name: 'PhotoContest',
        component: () => import('@/views/user/community/PhotoContestView.vue'),
        meta: { title: '宠物摄影大赛' },
      },
      // P17 帖子详情
      {
        path: 'community/post/:id',
        name: 'PostDetail',
        component: () => import('@/views/user/community/PostDetailView.vue'),
        meta: { title: '帖子详情' },
      },
      // P19 知识科普
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('@/views/user/knowledge/KnowledgeListView.vue'),
        meta: { title: '宠友百科', source: 'frontend/_1/code.html' },
      },
      // P20 文章详情
      {
        path: 'knowledge/:id',
        name: 'KnowledgeDetail',
        component: () => import('@/views/user/knowledge/KnowledgeArticleView.vue'),
        meta: { title: '文章详情' },
      },
      // P21 领养列表
      {
        path: 'adoption',
        name: 'Adoption',
        component: () => import('@/views/user/adoption/AdoptionListView.vue'),
        meta: { title: '领养救助' },
      },
      // P22 领养详情
      {
        path: 'adoption/detail/:id',
        name: 'AdoptionDetail',
        component: () => import('@/views/user/adoption/AdoptionDetailView.vue'),
        meta: { title: '领养详情', requiresAuth: true },
      },
      // P22b 我的领养申请
      {
        path: 'adoption/my',
        name: 'MyAdoptions',
        component: () => import('@/views/user/adoption/MyApplicationsView.vue'),
        meta: { title: '我的领养申请', requiresAuth: true },
      },
      // P22c 发布领养
      {
        path: 'adoption/publish',
        name: 'AdoptionPublish',
        component: () => import('@/views/user/adoption/AdoptionPublishView.vue'),
        meta: { title: '发布领养', requiresAuth: true, requiresVerified: true },
      },
      // P22d 我的领养数据管理
      {
        path: 'adoption/manage',
        name: 'AdoptionManage',
        component: () => import('@/views/user/adoption/MyAdoptionsManageView.vue'),
        meta: { title: '我的领养管理', requiresAuth: true },
      },
      // P23 领券中心
      {
        path: 'coupon-center',
        name: 'CouponCenter',
        component: () => import('@/views/user/product/CouponCenterView.vue'),
        meta: { title: '我的优惠券', requiresAuth: true },
      },
      // P24 秒杀
      {
        path: 'seckill',
        name: 'Seckill',
        component: () => import('@/views/user/product/SeckillView.vue'),
        meta: { title: '限时秒杀' },
      },
      // P25 我的订单
      {
        path: 'order/list',
        name: 'OrderList',
        component: () => import('@/views/user/order/OrderListView.vue'),
        meta: { title: '我的订单', requiresAuth: true, source: 'frontend/_7/code.html' },
      },
      // P26 订单详情
      {
        path: 'order/detail/:id',
        name: 'OrderDetail',
        component: () => import('@/views/user/order/OrderDetailView.vue'),
        meta: { title: '订单详情', requiresAuth: true },
      },
      // P26.1 我的售后（售后中心列表）
      {
        path: 'aftersale',
        name: 'AftersaleCenter',
        component: () => import('@/views/user/order/AftersaleCenterView.vue'),
        meta: { title: '我的售后', requiresAuth: true },
      },
      // P26.2 售后进度详情
      {
        path: 'aftersale/:id',
        name: 'AftersaleDetail',
        component: () => import('@/views/user/order/AftersaleDetailView.vue'),
        meta: { title: '售后详情', requiresAuth: true, hideInMenu: true },
      },
      // P27 我的
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/user/ProfileHubView.vue'),
        meta: { title: '我的', requiresAuth: true, source: 'frontend/_5/code.html' },
      },
      // P27.0 关注 / 粉丝列表
      {
        path: 'user/follow-list',
        name: 'FollowList',
        component: () => import('@/views/user/user/FollowListView.vue'),
        meta: { title: '关注与粉丝', requiresAuth: true, hideInMenu: true },
      },
      // P27.0.1 他人主页（点击头像进入「对面主页」）
      {
        path: 'user/home/:id',
        name: 'UserHome',
        component: () => import('@/views/user/user/UserHomeView.vue'),
        meta: { title: '用户主页', requiresAuth: true, hideInMenu: true },
      },
      // P27.1 编辑个人资料
      {
        path: 'user/edit',
        name: 'UserEdit',
        component: () => import('@/views/user/user/ProfileEditView.vue'),
        meta: { title: '编辑个人资料', requiresAuth: true, hideInMenu: true },
      },
      // P28 地址簿
      {
        path: 'user/address',
        name: 'Address',
        component: () => import('@/views/user/user/AddressView.vue'),
        meta: { title: '地址簿', requiresAuth: true },
      },
      // P28.1 地址编辑（新增/修改，AddressView 跳转目标）
      {
        path: 'user/address/edit/:id?',
        name: 'AddressEdit',
        component: () => import('@/views/user/user/AddressEditView.vue'),
        meta: { title: '编辑地址', requiresAuth: true, hideInMenu: true },
      },
      // P29 我的收藏
      {
        path: 'user/collect',
        name: 'Collect',
        component: () => import('@/views/user/user/CollectView.vue'),
        meta: { title: '我的收藏', requiresAuth: true },
      },
      // P29.2 商品足迹
      {
        path: 'user/footprint',
        name: 'Footprint',
        component: () => import('@/views/user/user/FootprintView.vue'),
        meta: { title: '商品足迹', requiresAuth: true },
      },
      // P27.3 我的动态（查看全部已发布动态）
      {
        path: 'user/posts',
        name: 'MyPosts',
        component: () => import('@/views/user/community/MyPostsView.vue'),
        meta: { title: '我的动态', requiresAuth: true, hideInMenu: true },
      },
      // P29.1 会员开通页
      {
        path: 'vip/open',
        name: 'VipOpen',
        component: () => import('@/views/user/vip/VipOpenView.vue'),
        meta: { title: '开通会员', requiresAuth: true },
      },
      // P29.2 会员支付结果页（支付宝同步回跳落地）
      {
        path: 'vip/result/:outTradeNo',
        name: 'VipResult',
        component: () => import('@/views/user/vip/VipResultView.vue'),
        meta: { title: '会员支付结果', requiresAuth: true },
      },
      // P29.3 我的消息中心（系统/订单/售后/会员/互动消息聚合）
      {
        path: 'user/messages',
        name: 'MessageCenter',
        component: () => import('@/views/user/message/MessageCenterView.vue'),
        meta: { title: '我的消息', requiresAuth: true },
      },
      // P29.4 联系客服（C 端入口，提交问题 → 站内信 → 后台 support 角色处理）
      {
        path: 'support',
        name: 'SupportContact',
        component: () => import('@/views/user/support/SupportContactView.vue'),
        meta: { title: '联系客服', requiresAuth: true },
      },
    ],
  },

  // P30 登录注册（独立 layout，不带用户端导航）
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/user/auth/LoginView.vue'),
    meta: { layout: 'blank', title: '登录 PetMate', source: 'frontend/petmate_2/code.html' },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/user/auth/RegisterView.vue'),
    meta: { layout: 'blank', title: '注册 PetMate', source: 'frontend/petmate_2/code.html' },
  },

  // ==============================================
  // 商家端（16 个页面，PRD §5.2）
  // ==============================================
  // 商家入驻/登录（独立 blank 布局，不套 MerchantLayout 侧栏）
  {
    path: '/merchant/landing',
    name: 'MerchantLanding',
    component: () => import('@/views/merchant/auth/MerchantLandingView.vue'),
    meta: { layout: 'blank', title: '商家入驻', hideInMenu: true, source: 'frontend/_17/code.html' },
  },
  {
    path: '/merchant/apply',
    name: 'MerchantApply',
    component: () => import('@/views/merchant/auth/MerchantApplyView.vue'),
    meta: { layout: 'blank', title: '申请入驻', hideInMenu: true },
  },
  {
    path: '/merchant/apply/result',
    name: 'MerchantApplyResult',
    component: () => import('@/views/merchant/auth/MerchantApplyResultView.vue'),
    meta: { layout: 'blank', title: '申请状态', hideInMenu: true },
  },
  {
    path: '/merchant',
    component: () => import('@/layouts/MerchantLayout.vue'),
    meta: { layout: 'merchant' },
    children: [
      { path: '', redirect: '/merchant/dashboard' },
      // M01 工作台
      {
        path: 'dashboard',
        name: 'MerchantDashboard',
        component: () => import('@/views/merchant/dashboard/WorkbenchView.vue'),
        meta: { title: '工作台', source: 'frontend/_12/code.html' },
      },
      // M02 订单管理
      {
        path: 'order',
        name: 'MerchantOrder',
        component: () => import('@/views/merchant/order/OrderListView.vue'),
        meta: { title: '订单管理', source: 'frontend/_16/code.html' },
      },
      // M03 订单详情
      {
        path: 'order/:id',
        name: 'MerchantOrderDetail',
        component: () => import('@/views/merchant/order/OrderDetailView.vue'),
        meta: { title: '订单详情' },
      },
      // M04 商品管理
      {
        path: 'product/list',
        name: 'MerchantProductList',
        component: () => import('@/views/merchant/product/ProductManageView.vue'),
        meta: { title: '商品管理', source: 'frontend/_10/code.html' },
      },
      // M05 商品编辑
      {
        path: 'product/edit',
        name: 'MerchantProductEdit',
        component: () => import('@/views/merchant/product/ProductEditView.vue'),
        meta: { title: '商品编辑' },
      },
      // M06 服务管理
      {
        path: 'service/list',
        name: 'MerchantServiceList',
        component: () => import('@/views/merchant/service/ServiceManageView.vue'),
        meta: { title: '服务管理', source: 'frontend/_21/code.html' },
      },
      // M06b 预约管理（线下服务履约）
      {
        path: 'appointments',
        name: 'MerchantAppointments',
        component: () => import('@/views/merchant/appointment/AppointmentManageView.vue'),
        meta: { title: '预约管理', source: 'frontend/_21/code.html' },
      },
      // M07 服务编辑
      {
        path: 'service/edit',
        name: 'MerchantServiceEdit',
        component: () => import('@/views/merchant/service/ServiceEditView.vue'),
        meta: { title: '服务编辑' },
      },
      // M08 排班管理
      {
        path: 'schedule',
        name: 'MerchantSchedule',
        component: () => import('@/views/merchant/service/ScheduleView.vue'),
        meta: { title: '排班管理' },
      },
      // M09 评价管理
      {
        path: 'review',
        name: 'MerchantReview',
        component: () => import('@/views/merchant/review/ReviewManageView.vue'),
        meta: { title: '评价管理', source: 'frontend/_2/code.html' },
      },
      // M14 客户管理
      {
        path: 'customers',
        name: 'MerchantCustomers',
        component: () => import('@/views/merchant/customer/CustomerListView.vue'),
        meta: { title: '客户管理' },
      },
      // 操作日志（审计）
      {
        path: 'operation-log',
        name: 'MerchantOperationLog',
        component: () => import('@/views/merchant/audit/OperationLogView.vue'),
        meta: { title: '操作日志' },
      },
      // 员工管理（RBAC，仅店主）
      {
        path: 'staff',
        name: 'MerchantStaff',
        component: () => import('@/views/merchant/staff/StaffManageView.vue'),
        meta: { title: '员工管理' },
      },
      // 售后退款（商家侧售后处理，OWNER/OPERATOR）
      {
        path: 'refunds',
        name: 'MerchantRefunds',
        component: () => import('@/views/merchant/refund/RefundManageView.vue'),
        meta: { title: '售后退款' },
      },
      // M10 门店信息
      {
        path: 'store/info',
        name: 'MerchantStoreInfo',
        component: () => import('@/views/merchant/store/StoreInfoView.vue'),
        meta: { title: '门店信息', source: 'frontend/_13/code.html' },
      },
      // M11 实景相册
      {
        path: 'store/photos',
        name: 'MerchantStorePhotos',
        component: () => import('@/views/merchant/store/StorePhotosView.vue'),
        meta: { title: '实景相册' },
      },
      // M12 销售看板
      {
        path: 'data/sales',
        name: 'MerchantSales',
        component: () => import('@/views/merchant/dashboard/HomeView.vue'),
        meta: { title: '销售看板', source: 'frontend/_13/code.html' },
      },
      // M13 客流分析
      {
        path: 'data/visitor',
        name: 'MerchantVisitor',
        component: () => import('@/views/merchant/dashboard/VisitorView.vue'),
        meta: { title: '客流分析' },
      },
      // M14 转化漏斗
      {
        path: 'data/convert',
        name: 'MerchantConvert',
        component: () => import('@/views/merchant/dashboard/ConvertView.vue'),
        meta: { title: '转化漏斗' },
      },
      // M15 财务结算
      {
        path: 'finance',
        name: 'MerchantFinance',
        component: () => import('@/views/merchant/finance/FinanceView.vue'),
        meta: { title: '财务结算' },
      },
      // 只读提示页（移动端访问编辑页）
      {
        path: 'readonly',
        name: 'MerchantReadonly',
        component: Placeholder,
        meta: { title: '请使用桌面浏览器', hideInMenu: true },
        props: {
          pageTitle: '请使用桌面浏览器',
          pageDesc: '商家端编辑功能需要在 ≥768px 宽度的设备上使用',
        },
      },
    ],
  },

  // ==============================================
  // 管理后台（22 个页面，PRD §5.3）
  // ==============================================
  // 后台登录（独立 blank 布局，不套 AdminLayout 侧栏）
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/admin/auth/AdminLoginView.vue'),
    meta: { layout: 'blank', title: '后台登录', hideInMenu: true, source: 'frontend/_11/code.html' },
  },
  {
    path: '/admin',
    component: () => import('@/layouts/AdminLayout.vue'),
    meta: { layout: 'admin' },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      // A01 仪表盘
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/dashboard/AnalyticsView.vue'),
        meta: { title: '运营仪表盘', source: 'frontend/_3/code.html' },
      },
      // A02 用户管理
      {
        path: 'user/list',
        name: 'AdminUserList',
        component: () => import('@/views/admin/user/UserManageView.vue'),
        meta: { title: '用户管理', source: 'frontend/_20/code.html' },
      },
      // A03 用户详情
      {
        path: 'user/detail/:id',
        name: 'AdminUserDetail',
        component: () => import('@/views/admin/user/AdminUserDetailView.vue'),
        meta: { title: '用户详情' },
      },
      // A05 入驻申请审核（v4 新增；商家管理/审核原走 t_store 演示态已合并到此）
      {
        path: 'merchant/apply/audit',
        name: 'AdminApplyAudit',
        component: () => import('@/views/admin/user/AdminApplyAuditView.vue'),
        meta: { title: '入驻申请审核' },
      },
      // A05b 已入驻商家管理（v5 新增）
      {
        path: 'merchant/list',
        name: 'AdminMerchantList',
        component: () => import('@/views/admin/user/AdminMerchantManageView.vue'),
        meta: { title: '商家管理' },
      },
      // A05c 商家详情
      {
        path: 'merchant/detail/:id',
        name: 'AdminMerchantDetail',
        component: () => import('@/views/admin/user/AdminMerchantDetailView.vue'),
        meta: { title: '商家详情', hideInMenu: true },
      },
      // A06 商品管理
      {
        path: 'product/list',
        name: 'AdminProductList',
        component: () => import('@/views/admin/product/ProductListView.vue'),
        meta: { title: '商品管理' },
      },
      // A07 分类管理
      {
        path: 'product/category',
        name: 'AdminCategory',
        component: () => import('@/views/admin/product/CategoryView.vue'),
        meta: { title: '分类管理' },
      },
      // A08 订单管理
      {
        path: 'order/list',
        name: 'AdminOrderList',
        component: () => import('@/views/admin/user/AdminOrderListView.vue'),
        meta: { title: '订单管理' },
      },
      // A09 退款审核
      {
        path: 'order/refund',
        name: 'AdminRefundAudit',
        component: () => import('@/views/admin/user/AdminRefundView.vue'),
        meta: { title: '退款审核' },
      },
      // A10 帖子审核
      {
        path: 'community/post',
        name: 'AdminPostAudit',
        component: () => import('@/views/admin/community/PostAuditView.vue'),
        meta: { title: '帖子审核', source: 'frontend/_4/code.html' },
      },
      // A11 举报处理
      {
        path: 'community/report',
        name: 'AdminReport',
        component: () => import('@/views/admin/community/AdminReportView.vue'),
        meta: { title: '举报处理' },
      },
      // A13 领养审核
      {
        path: 'adoption/audit',
        name: 'AdminAdoptionAudit',
        component: () => import('@/views/admin/adoption/AdoptionAdminView.vue'),
        meta: { title: '领养审核' },
      },
      // A14 秒杀配置
      {
        path: 'marketing/seckill',
        name: 'AdminSeckill',
        component: () => import('@/views/admin/marketing/SeckillView.vue'),
        meta: { title: '秒杀配置' },
      },
      // A15 优惠券配置
      {
        path: 'marketing/coupon',
        name: 'AdminCoupon',
        component: () => import('@/views/admin/marketing/CouponView.vue'),
        meta: { title: '优惠券配置' },
      },
      // A17 敏感词管理（独立管理页）
      {
        path: 'risk/sensitive',
        name: 'AdminSensitive',
        component: () => import('@/views/admin/risk/SensitiveWordsManageView.vue'),
        meta: { title: '敏感词管理' },
      },
      // A17b 风控中心（紧急熔断）
      {
        path: 'risk/center',
        name: 'AdminRiskCenter',
        component: () => import('@/views/admin/risk/SensitiveWordView.vue'),
        meta: { title: '风控中心' },
      },
      // A19 角色管理
      {
        path: 'role/list',
        name: 'AdminRoleList',
        component: () => import('@/views/admin/role/RoleListView.vue'),
        meta: { title: '角色管理' },
      },
      // A20 权限分配
      {
        path: 'role/permission',
        name: 'AdminPermission',
        component: () => import('@/views/admin/role/PermissionView.vue'),
        meta: { title: '权限分配' },
      },
      // A22 操作日志
      {
        path: 'system/log',
        name: 'AdminLog',
        component: () => import('@/views/admin/user/AdminLogView.vue'),
        meta: { title: '操作日志' },
      },
      // A23 系统参数配置
      {
        path: 'system/config',
        name: 'AdminSystemConfig',
        component: () => import('@/views/admin/system/ConfigView.vue'),
        meta: { title: '系统参数' },
      },
      // A24 轮播 Banner 管理
      {
        path: 'marketing/banner',
        name: 'AdminBanner',
        component: () => import('@/views/admin/marketing/BannerManageView.vue'),
        meta: { title: '轮播管理' },
      },
      // A25 平台公告管理
      {
        path: 'content/announcement',
        name: 'AdminAnnouncement',
        component: () => import('@/views/admin/content/AnnouncementManageView.vue'),
        meta: { title: '公告管理' },
      },
      // 后台不支持提示（< 1024px 拦截）
      {
        path: 'unsupported',
        name: 'AdminUnsupported',
        component: Placeholder,
        meta: { layout: 'blank', title: '请使用桌面浏览器', hideInMenu: true },
        props: {
          pageTitle: '请使用桌面浏览器',
          pageDesc: '后台管理系统需要在 ≥1024px 宽度的桌面设备上访问',
        },
      },
    ],
  },

  // ==============================================
  // 404 兜底
  // ==============================================
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: Placeholder,
    meta: { layout: 'blank', title: '404 页面未找到' },
    props: { pageTitle: '404', pageDesc: '页面不存在或正在开发中' },
  },
]

export default routes