import { storeToRefs } from 'pinia'
import { useUserStore } from '@/stores/user'

/**
 * 当前用户信息 composable
 */
export function useUser() {
  const userStore = useUserStore()
  const { user, isLoggedIn, token, vipLevel } = storeToRefs(userStore)

  const isVip = () => vipLevel.value >= 1
  const isGuest = () => !isLoggedIn.value

  return {
    user,
    isLoggedIn,
    token,
    vipLevel,
    isVip,
    isGuest,
    login: userStore.login,
    logout: userStore.logout,
    fetchUser: userStore.fetchUser,
  }
}