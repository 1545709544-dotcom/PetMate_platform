import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi, type UserInfo, type RegisterParams } from '@/api/modules/auth'
import { tokenManager } from '@/utils/auth'

/** 角色类型 */
export type UserRole = 'admin' | 'merchant' | 'user'

/** 后端下发的合法角色值 */
const VALID_ROLES: UserRole[] = ['admin', 'merchant', 'user']

/**
 * 角色规范化。
 *
 * 角色的唯一权威来源是后端 t_user.role（登录 / 注册 / /user/me 均下发 role 字段）。
 * 早期前端按 userId 阈值推断（>=90000 admin、>=20000 merchant），与真实数据完全对不上
 * ——例如普通用户 90004 会被判成 admin。该推断已废弃，仅在后端未返回 role 时降级为 'user'，
 * 宁可少给权限也不能凭 id 号段擅自提权。
 */
export function normalizeRole(role: unknown): UserRole {
  const r = String(role || '').toLowerCase() as UserRole
  return VALID_ROLES.includes(r) ? r : 'user'
}

/**
 * 商家子角色类型（RBAC）
 */
export type MerchantSubRole = 'OWNER' | 'OPERATOR' | 'CS' | 'WAREHOUSE'

/**
 * 用户 Store
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string>(tokenManager.get() || '')
  const user = ref<UserInfo | null>(null)
  const vipLevel = ref<number>(0)
  const role = ref<UserRole>('user')
  // 商家子角色 / 归属商家 / 员工id（RBAC）
  const subRole = ref<MerchantSubRole | ''>('')
  const merchantId = ref<number>(0)
  const staffId = ref<number | null>(null)
  const isStaff = ref<boolean>(false)

  // 初始会话恢复的 Promise：store 初始化时若已有 token 会触发 fetchUser，
  // 将其缓存下来，供路由守卫 await，避免在用户信息拉取完成前就误判角色。
  let initPromise: Promise<unknown> | null = null

  async function whenReady(): Promise<unknown> {
    return initPromise ?? Promise.resolve()
  }

  const isLoggedIn = computed(() => !!token.value)
  const isVip = computed(() => vipLevel.value >= 1)
  // 实名认证状态：1 表示已通过实名。后端 /user/me 的 verified 字段（0/1）。
  const isVerified = computed(() => (user.value?.verified ?? 0) === 1)
  const userId = computed(() => user.value?.id || 0)
  const isAdmin = computed(() => role.value === 'admin')
  const isMerchant = computed(() => role.value === 'merchant')
  // 角色判断
  const isOwner = computed(() => isMerchant.value && subRole.value === 'OWNER')
  const isOperator = computed(() => isMerchant.value && (subRole.value === 'OWNER' || subRole.value === 'OPERATOR'))
  const isCs = computed(() => isMerchant.value && (subRole.value === 'OWNER' || subRole.value === 'CS'))
  const isWarehouse = computed(() => isMerchant.value && (subRole.value === 'OWNER' || subRole.value === 'WAREHOUSE'))

  async function login(loginParams: Parameters<typeof authApi.login>[0]) {
    const res = await authApi.login(loginParams)
    token.value = res.token
    tokenManager.set(res.token)
    vipLevel.value = res.vipLevel
    role.value = normalizeRole(res.role)
    subRole.value = (res.subRole as MerchantSubRole) || (role.value === 'merchant' ? 'OWNER' : '')
    merchantId.value = res.merchantId || 0
    staffId.value = res.staffId ?? null
    isStaff.value = !!res.isStaff
    // 立即获取用户信息
    await fetchUser()
    return res
  }

  /** 注册:成功后与登录等价,token 写入会话存储 */
  async function register(params: RegisterParams) {
    const res = await authApi.register(params)
    token.value = res.token
    tokenManager.set(res.token)
    vipLevel.value = res.vipLevel
    role.value = normalizeRole(res.role)
    subRole.value = (res.subRole as MerchantSubRole) || (role.value === 'merchant' ? 'OWNER' : '')
    merchantId.value = res.merchantId || 0
    staffId.value = res.staffId ?? null
    isStaff.value = !!res.isStaff
    await fetchUser()
    return res
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      /* 即使接口失败也清空本地 */
    }
    token.value = ''
    tokenManager.clear()
    user.value = null
    vipLevel.value = 0
    role.value = 'user'
    subRole.value = ''
    merchantId.value = 0
    staffId.value = null
    isStaff.value = false
  }

  async function fetchUser() {
    if (!token.value) return null
    try {
      const info = await authApi.me()
      // authApi.me() 失败时返回 null（不伪造身份）。这里必须显式判空，
      // 否则后续取值抛异常被 catch 吞掉，角色/VIP 会停留在上一个账号的残留值。
      if (!info) return null
      user.value = info
      vipLevel.value = info.vipLevel
      role.value = normalizeRole(info.role)
      subRole.value = (info.subRole as MerchantSubRole) || (role.value === 'merchant' ? 'OWNER' : '')
      merchantId.value = info.merchantId || 0
      staffId.value = info.staffId ?? null
      isStaff.value = !!info.isStaff
      return info
    } catch {
      return null
    }
  }

  // ===== 会话内恢复：若当前会话已有 token，静默拉取用户信息 =====
  // token 存于 sessionStorage（关闭浏览器即失效），此处仅在同会话内重建 user / role，
  // 避免刷新后守卫放行但用户信息为空导致页面空白。不实现跨会话“记住登录态”。
  if (token.value) {
    // 缓存 Promise，供路由守卫 await，避免刷新后守卫在用户信息拉取完成前误判角色。
    initPromise = fetchUser().catch(() => {
      // token 失效（过期 / 被踢）则清空，回到未登录
      token.value = ''
      tokenManager.clear()
      user.value = null
    })
  }

  return {
    token,
    user,
    vipLevel,
    role,
    subRole,
    merchantId,
    staffId,
    isStaff,
    isLoggedIn,
    isVip,
    isVerified,
    userId,
    isAdmin,
    isMerchant,
    isOwner,
    isOperator,
    isCs,
    isWarehouse,
    login,
    register,
    logout,
    fetchUser,
    whenReady,
  }
})