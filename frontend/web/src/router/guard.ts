import type { Router } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

/**
 * 全局路由守卫
 * 1. 视口守卫：PRD §11.4.4
 *    - /admin/** 在 < 1024px 跳 AdminUnsupported
 *    - /merchant/** 在 < 768px + 编辑类页 跳 MerchantReadonly
 * 2. 鉴权守卫：未登录访问 requiresAuth 页面 跳 /login
 */
export function setupGuards(router: Router) {
  router.beforeEach(async (to, _from, next) => {
    // 在 SSR 环境下 window 不存在，跳过视口判断
    if (typeof window === 'undefined') return next()

    // ===== 支付宝同步回跳重定向 =====
    // return_url 改为「根路径 + 查询参数」模式（由后端拼接当前 origin 根路径），
    // 根路径任何 host 都返回 index.html，规避 history 模式深链在代理/预览域名下 404。
    // 此处根据 query.payType 重定向到正确落地页，避免硬编码 localhost 回跳。
    if (to.query.payType === 'vip' && to.query.outTradeNo) {
      return next({ path: '/vip/result/' + String(to.query.outTradeNo), replace: true })
    }
    if (to.query.payType === 'order' && to.query.orderNo) {
      // 支付完成后回跳到「我的订单」列表(保留用户原本 tab,通常就是「待付款」),
      // 由 OrderListView 检测 paid=1 query 弹「已支付」toast。
      // 注意：路由 /order/list 挂在 UserLayout 下,完整路径就是 /order/list,不是 /user/order/list
      return next({
        path: '/order/list',
        query: { paid: '1', orderNo: String(to.query.orderNo) },
        replace: true,
      })
    }

    // ===== 视口守卫 =====
    const width = window.innerWidth

    if (to.path.startsWith('/admin') && to.path !== '/admin/unsupported') {
      if (width < 1024) {
        return next({ name: 'AdminUnsupported', replace: true })
      }
    }

    if (to.path.startsWith('/merchant') && to.path !== '/merchant/readonly') {
      if (width < 768) {
        const needReadonly =
          to.path.includes('/edit') ||
          to.path.includes('/schedule') ||
          to.path.includes('/photos')
        if (needReadonly) {
          return next({ name: 'MerchantReadonly', replace: true })
        }
      }
    }

    // ===== 等待会话内用户信息就绪 =====
    // 刷新 / 重开浏览器后 token 已从 sessionStorage 恢复，但角色(role)依赖
    // fetchUser() 的异步结果，守卫同步执行时 role 仍是默认值 'user'，
    // 会误判为「无权访问」。先 await 初始拉取完成，再判断角色。
    const userStore = useUserStore()
    await userStore.whenReady()

    // ===== 鉴权守卫 =====
    if (to.meta.requiresAuth) {
      if (!userStore.isLoggedIn) {
        return next({
          name: 'Login',
          query: { redirect: to.fullPath },
          replace: true,
        })
      }
    }

    // ===== 实名认证门禁 =====
    // 未实名用户禁止进入发布类页面（发动态 / 发领养）。
    // requiresAuth 已先行拦截未登录，此处 userStore.user 必然已就绪（上方 whenReady），
    // 因此可同步读取 isVerified，绝不存在「先渲染表单再隐藏」的竞态窗口。
    //
    // 守卫仅作兜底：静默跳转到 /user/edit?needVerify=1（不弹 toast）。
    // 正常入口（发动态/申请领养/发布领养按钮）已由 requireVerified() 弹警告弹窗拦截，
    // 用户点「去实名认证」后跳转过来。直接输 URL 的场景由守卫兜底跳转。
    if (to.meta.requiresVerified && !userStore.isVerified) {
      return next({ name: 'UserEdit', query: { needVerify: '1' }, replace: true })
    }

    // ===== 角色守卫 =====
    // 角色以后端 t_user.role 为准（登录/注册//user/me 均下发 role，见 stores/user.ts normalizeRole）。
    // 仅对已登录用户做访问控制:admin/** -> admin;merchant/** -> merchant
    // 登录入口页(/merchant/landing、/admin/login)以及只读/不支持页不强制角色
    if (userStore.isLoggedIn && to.path !== '/admin/login') {
      if (to.path.startsWith('/admin') && !userStore.isAdmin) {
        ElMessage.error('无权访问后台')
        return next({ name: 'Home', replace: true })
      }
    }
    if (userStore.isLoggedIn && to.path !== '/merchant/landing') {
      const isMerchantArea =
        to.path.startsWith('/merchant') && to.path !== '/merchant/readonly'
      if (isMerchantArea && !userStore.isMerchant) {
        ElMessage.error('无权访问商家中心')
        return next({ name: 'Home', replace: true })
      }
    }

    next()
  })

  // 设置页面标题
  router.afterEach((to) => {
    if (to.meta.title) {
      document.title = `${to.meta.title} - PetMate`
    } else {
      document.title = 'PetMate - 智能宠物管家'
    }
  })
}