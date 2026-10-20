/**
 * 设备守卫 composable
 * 根据视口判断当前路由是否可访问（PRD §11.4.4）
 */
export function useDeviceGuard() {
  function checkDevice(path: string): { allowed: boolean; redirect?: string } {
    if (typeof window === 'undefined') return { allowed: true }
    const w = window.innerWidth

    // 后台路由：< 1024 不可访问
    if (path.startsWith('/admin') && w < 1024) {
      return { allowed: false, redirect: '/admin/unsupported' }
    }

    // 商家路由：< 768 仅可读（编辑页需拦截）
    if (path.startsWith('/merchant') && w < 768) {
      // 编辑类页面需要拦截
      if (
        path.includes('/edit') ||
        path.includes('/schedule') ||
        path.includes('/photos')
      ) {
        return { allowed: false, redirect: '/merchant/readonly' }
      }
    }

    return { allowed: true }
  }

  return { checkDevice }
}