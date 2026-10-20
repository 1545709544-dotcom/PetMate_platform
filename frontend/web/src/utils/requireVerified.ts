import { ElMessageBox } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/stores/user'

/**
 * 读取当前登录用户的实名认证状态（from user store）。
 */
export function isVerified(): boolean {
  return useUserStore().isVerified
}

/**
 * 重新拉取最新用户资料并返回实名状态。
 * 在「刚完成实名认证」或「进入受限页面」时调用，确保状态为最新。
 */
export async function ensureVerified(): Promise<boolean> {
  const userStore = useUserStore()
  await userStore.fetchUser()
  return isVerified()
}

/**
 * 受限功能门禁：未实名时弹出警告弹窗，可选择「一键去实名」。
 *
 * - 已实名 → 返回 true，调用方继续执行操作。
 * - 未实名 → 弹出 ElMessageBox 警告弹窗（「该功能需要先完成实名认证」），
 *   点「去实名认证」跳转 /user/edit；点「暂不」或关闭则留在当前页。
 *   两种情况都返回 false，调用方应中止操作。
 *
 * 使用 router 单例（而非 useRouter()），确保在 ElMessageBox 异步回调中
 * 也能正确跳转——useRouter() 依赖 setup 上下文，在 .then() 回调里可能失效。
 */
export function requireVerified(): boolean {
  if (isVerified()) return true
  ElMessageBox.confirm(
    '该功能需要先完成实名认证，以保障平台安全与用户权益。',
    '需要实名认证',
    {
      confirmButtonText: '去实名认证',
      cancelButtonText: '暂不',
      type: 'warning',
      confirmButtonClass: 'el-button--warning',
    },
  )
    .then(() => {
      router.replace({ name: 'UserEdit', query: { needVerify: '1' } })
    })
    .catch(() => {
      // 用户点「暂不」或关闭弹窗，不做任何操作
    })
  return false
}
