import request from '@/api/request'

// 登录入参
export interface LoginParams {
  phone?: string
  username?: string
  password?: string
  loginType: 'password' | 'staff'
}

// 登录返回
export interface LoginResult {
  token: string
  userId: number
  /** 后端返回的角色：admin / merchant / user（员工登录返回 merchant） */
  role?: string
  nickname: string
  avatar: string
  vipLevel: number
  // 商家子角色（RBAC）：OWNER / OPERATOR / CS / WAREHOUSE
  subRole?: string
  merchantId?: number
  staffId?: number | null
  isStaff?: boolean
}

// 用户信息
export interface UserInfo {
  id: number
  nickname: string
  avatar: string
  phone: string
  vipLevel: number
  gender: 0 | 1 | 2
  /** 所在地区(省/市/区 拼接字符串) */
  region?: string
  birthday?: string
  bio?: string
  /** 实名认证状态：0 未认证 / 1 已认证（后端 /user/me 返回） */
  verified?: number
  /** 所在城市(如 北京)，用于社区「附近/市区」分类 */
  city?: string
  /** 后端返回的角色：admin / merchant / user（员工登录返回 merchant） */
  role?: string
  // 商家子角色（RBAC）
  subRole?: string
  merchantId?: number
  staffId?: number | null
  isStaff?: boolean
}

// ============================================================
// 本地演示兜底（无后端时使用，PRD §3.1.1 演示凭证）
// 仅当显式配置 VITE_AUTH_MOCK=true 时启用，避免生产环境携带 mock
// ============================================================
const AUTH_MOCK = import.meta.env.VITE_AUTH_MOCK === 'true'
const MOCK_TOKEN = 'demo-token-' + Math.random().toString(36).slice(2, 10)
const MOCK_USER: UserInfo = {
  id: 10001,
  nickname: '萌宠家长',
  avatar: 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate',
  phone: '13800138000',
  vipLevel: 1,
  gender: 1,
  birthday: '1995-06-15',
  bio: '爱宠物的产品经理',
}

// ============================================================
// 注册入参 / 返回
// ============================================================
export interface RegisterParams {
  phone: string
  nickname: string
  password: string
}
export type RegisterResult = LoginResult

function sleep(ms: number) {
  return new Promise((r) => setTimeout(r, ms))
}

export const authApi = {
  // 登录（直接调用真实接口，本项目已不实现短信验证码，无 mock 兜底）
  async login(params: LoginParams) {
    return await request<LoginResult>({
      url: '/auth/login',
      method: 'POST',
      data: params,
    })
  },

  // 注册:POST /auth/register { phone, nickname, password } -> 同样返回 token+用户信息
  async register(params: RegisterParams) {
    return await request<RegisterResult>({
      url: '/auth/register',
      method: 'POST',
      data: params,
    })
  },

  // 找回密码（演示态）：POST /auth/reset-password { phone, password }
  async resetPassword(phone: string, password: string) {
    return await request<void>({
      url: '/auth/reset-password',
      method: 'POST',
      data: { phone, password },
    })
  },

  // 登出
  async logout() {
    try {
      await request<void>({ url: '/auth/logout', method: 'POST' })
    } catch {
      /* 即使接口失败也清空本地 */
    }
  },

  // 获取当前用户信息（mock）
  async me(): Promise<UserInfo | null> {
    try {
      return await request<UserInfo>({ url: '/user/me', method: 'GET' })
    } catch {
      // 即便接口失败也不能伪造用户身份,免得把无 token 状态伪造成已登录
      return null
    }
  },
}
