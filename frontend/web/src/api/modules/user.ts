import request from '@/api/request'
import type { PageResp } from '@/api/request'

// 收货地址
export interface Address {
  id: number
  userId: number
  receiver: string
  phone: string
  province: string
  city: string
  district: string
  detail: string
  isDefault: boolean
  tag?: 'home' | 'company' | 'school'
}

// 收藏
export interface Favorite {
  id: number
  userId: number
  targetType: 'product' | 'post' | 'article' | 'service'
  targetId: number
  title: string
  image?: string
  price?: number
  createdAt: string
}

// 商品足迹（浏览历史）
export interface Footprint {
  id: number
  productId: number
  type?: 'product' | 'knowledge' | 'community'
  name: string | null
  mainImage?: string | null
  salePrice?: number | null
  originalPrice?: number | null
  viewCount: number
  lastViewAt: string | null
  createdAt: string | null
  offline?: boolean
}

// 优惠券
export interface Coupon {
  id: number
  name: string
  type: 'reduction' | 'discount' | 'category'
  threshold: number
  amount: number
  discount?: number
  validStart: string
  validEnd: string
  status?: 'available' | 'claimed' | 'used' | 'expired'
}

// 个人统计
export interface UserStats {
  followerCount: number
  followingCount: number
  postCount: number
  petCount?: number
  likeCount?: number
  favoriteCount?: number
}

// 关注/粉丝列表中的用户摘要
export interface FollowUser {
  id: number
  nickname: string
  avatar?: string
  bio?: string
  vipLevel?: number
  /** 当前登录用户是否关注了对方（粉丝列表用于「回关」判断） */
  following: boolean
}

// 用户活动流
export interface UserActivity {
  type: 'post' | 'review'
  refId: number
  title: string
  description: string
  time: string
}

// 他人主页公开资料（点击头像进入「对面主页」）
export interface UserProfile {
  id: number
  nickname: string
  avatar?: string
  bio?: string
  vipLevel?: number
  /** 性别：0 未知 / 1 男 / 2 女 */
  gender?: 0 | 1 | 2
  /** 所在地区(省/市/区 拼接字符串) */
  region?: string
  followerCount: number
  followingCount: number
  postCount: number
  /** 该用户所有帖子的累计获赞数（动态详情侧栏「获赞」使用） */
  totalLikes?: number
  /** 当前登录用户是否已关注对方 */
  following: boolean
}

// 鱼干（积分）
export interface PointsSummary {
  balance: number
  totalEarned: number
  totalSpent: number
  dailyEarned: number
  dailyLimit: number
}

export interface TaskItem {
  id: number
  code: string
  title: string
  description?: string
  reward: number
  daily: boolean
  sort: number
  completed: boolean
}

export interface TaskResult {
  done: boolean
  alreadyDone: boolean
  granted: number
  balance: number
  dailyLimitReached?: boolean
  taskCode: string
}

export const userApi = {
  // 当前用户信息
  me() {
    return request<Record<string, any>>({
      url: '/user/me',
      method: 'GET',
    })
  },

  // 更新个人资料
  updateProfile(patch: Record<string, any>) {
    return request<Record<string, any>>({
      url: '/user/profile',
      method: 'PUT',
      data: patch,
    })
  },

  // 修改密码
  changePassword(oldPassword: string, newPassword: string) {
    return request<{ ok: boolean }>({
      url: '/user/password',
      method: 'POST',
      data: { oldPassword, newPassword },
    })
  },

  // 实名认证（提交真实姓名 + 18 位身份证号）
  realname(realName: string, idCard: string) {
    return request<Record<string, any>>({
      url: '/user/realname',
      method: 'POST',
      data: { realName, idCard },
    })
  },

  // 注销账号（校验登录密码后软删除）
  deleteAccount(password: string) {
    return request<{ ok: boolean }>({
      url: '/user/account',
      method: 'DELETE',
      data: { password },
    })
  },

  // 上传头像(multipart)
  uploadAvatar(file: File) {
    const form = new FormData()
    form.append('file', file)
    return request<{ url: string; user: Record<string, any> }>({
      url: '/user/avatar',
      method: 'POST',
      data: form,
    })
  },

  // 地址列表
  addresses() {
    return request<Address[]>({
      url: '/user/address',
      method: 'GET',
    })
  },

  // 单条地址（编辑页直接拉一条）
  getAddress(id: number) {
    return request<Address | null>({
      url: `/user/address/${id}`,
      method: 'GET',
    })
  },

  // 新增/编辑地址（全量 PUT/POST，不混用设默认）
  saveAddress(address: Partial<Address>) {
    return request<Address>({
      url: address.id ? `/user/address/${address.id}` : '/user/address',
      method: address.id ? 'PUT' : 'POST',
      data: address,
    })
  },

  // 设为默认（专用接口，只改默认标志，不动其他字段）
  setDefaultAddress(id: number) {
    return request<void>({
      url: `/user/address/${id}/default`,
      method: 'PUT',
    })
  },

  // 删除地址
  deleteAddress(id: number) {
    return request<void>({
      url: `/user/address/${id}`,
      method: 'DELETE',
    })
  },

  // 收藏列表
  favorites(targetType?: Favorite['targetType']) {
    return request<Favorite[]>({
      url: '/favorite/list',
      method: 'GET',
      params: { targetType },
    })
  },

  // 添加/取消收藏
  toggleFavorite(targetType: Favorite['targetType'], targetId: number) {
    return request<{ collected: boolean }>({
      url: '/favorite/toggle',
      method: 'POST',
      data: { targetType, targetId },
    })
  },

  // ============ 商品足迹 ============
  // 足迹列表（按最后浏览时间倒序，最多 100 条）
  footprints() {
    return request<Footprint[]>({
      url: '/user/footprint',
      method: 'GET',
    })
  },

  // 删除单条足迹（按足迹 id）
  deleteFootprint(id: number) {
    return request<void>({
      url: `/user/footprint/${id}`,
      method: 'DELETE',
    })
  },
  // 记录浏览足迹（商品 / 知识科普 / 社区动态通用）
  addFootprint(params: { type: 'product' | 'knowledge' | 'community'; targetId: number }) {
    return request<void>({
      url: '/user/footprint',
      method: 'POST',
      data: params,
    })
  },

  // 清空全部足迹
  clearFootprints() {
    return request<void>({
      url: '/user/footprint',
      method: 'DELETE',
    })
  },

  // 优惠券列表
  coupons() {
    return request<Coupon[]>({
      url: '/coupon/list',
      method: 'GET',
    })
  },

  // 领券
  claimCoupon(couponId: number) {
    return request<{ claimed: boolean }>({
      url: '/coupon/claim',
      method: 'POST',
      data: { couponId },
    })
  },

  // 个人统计(粉丝/关注/动态)
  stats() {
    return request<UserStats>({
      url: '/user/stats',
      method: 'GET',
    })
  },

  // 开通 / 续费 / 升级 VIP（走支付宝沙箱）
  // 返回二选一：
  //  - 演示态：{ demoMode:true, vipLevel, vip, vipExpireAt, action }
  //  - 真实支付宝：{ demoMode:false, payUrl, pageBody, outTradeNo }
  // plan 取后端 /vip/plan 返回的 key（当前为 l1 / l2 / l3），不做前端硬编码枚举
  openVip(plan?: string) {
    return request<{
      demoMode?: boolean
      vipLevel?: number
      vip?: boolean
      vipExpireAt?: string
      action?: 'open' | 'renew' | 'upgrade'
      payUrl?: string
      pageBody?: string
      outTradeNo?: string
    }>({
      url: '/user/vip/open',
      method: 'POST',
      // returnBase：前端当前 origin（动态），避免支付宝回跳硬编码 localhost 在代理/预览域名下 404
      data: { ...(plan ? { plan } : {}), returnBase: window.location.origin },
    })
  },

  // 会员支付结果轮询（打开收银台后轮询，直到异步通知置 paid）
  vipPayStatus(outTradeNo: string) {
    return request<{ paid: boolean; status: string; vipLevel?: number; vipExpireAt?: string }>({
      url: '/user/vip/pay/status',
      method: 'GET',
      params: { outTradeNo },
    })
  },

  // 个人活动流
  activities(page = 1, size = 10) {
    return request<PageResp<UserActivity>>({
      url: '/user/activities',
      method: 'GET',
      params: { page, size },
    })
  },

  // 关注/取消关注
  follow(followeeId: number) {
    return request<{ following: boolean }>({
      url: '/user/follow',
      method: 'POST',
      data: { followeeId },
    })
  },

  // 关注状态
  followStatus(userId: number) {
    return request<{ following: boolean; userId: number }>({
      url: '/user/follow/status',
      method: 'GET',
      params: { userId },
    })
  },

  // 我关注的人（关注列表）
  following(page = 1, size = 50) {
    return request<PageResp<FollowUser>>({
      url: '/user/following',
      method: 'GET',
      params: { page, size },
    })
  },

  // 我的粉丝（粉丝列表）
  followers(page = 1, size = 50) {
    return request<PageResp<FollowUser>>({
      url: '/user/followers',
      method: 'GET',
      params: { page, size },
    })
  },

  // 他人主页公开资料（点击头像进入「对面主页」）
  getUserProfile(id: number) {
    return request<UserProfile>({
      url: `/user/profile/${id}`,
      method: 'GET',
    })
  },

  // 推荐关注（社区侧栏）：返回我尚未关注的其他活跃用户，按粉丝数降序
  recommend(limit = 5) {
    return request<FollowUser[]>({
      url: '/user/recommend',
      method: 'GET',
      params: { limit },
    })
  },

  // ============ 鱼干（积分） ============
  // 余额与每日上限进度
  pointsBalance() {
    return request<PointsSummary>({
      url: '/points/balance',
      method: 'GET',
    })
  },

  // 任务中心列表
  taskList() {
    return request<TaskItem[]>({
      url: '/task/list',
      method: 'GET',
    })
  },

  // 领取任务/签到奖励
  completeTask(taskCode: string) {
    return request<TaskResult>({
      url: '/task/complete',
      method: 'POST',
      data: { taskCode },
    })
  },

  // ============ 我的消息（站内信） ============
  // 消息列表 + 未读数
  notifications(params: { page?: number; size?: number; type?: string; unreadOnly?: boolean } = {}) {
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
      url: '/user/notifications',
      method: 'GET',
      params,
    })
  },

  // 未读数量（顶栏角标轮询）
  notificationUnreadCount() {
    return request<{ count: number }>({
      url: '/user/notifications/unread-count',
      method: 'GET',
    })
  },

  // 标记单条已读
  readNotification(id: number) {
    return request<void>({
      url: `/user/notifications/${id}/read`,
      method: 'POST',
    })
  },

  // 全部已读
  readAllNotifications() {
    return request<void>({
      url: '/user/notifications/read-all',
      method: 'POST',
    })
  },
}