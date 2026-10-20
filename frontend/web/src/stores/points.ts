import { defineStore } from 'pinia'
import { ref } from 'vue'
import { userApi, type PointsSummary } from '@/api/modules/user'

/**
 * 鱼干（积分）全局状态。
 * 顶栏余额、签到、任务中心、结算抵扣页共用，避免重复拉取。
 */
export const usePointsStore = defineStore('points', () => {
  const summary = ref<PointsSummary | null>(null)
  const balance = ref(0)
  const loading = ref(false)

  async function fetch(force = false) {
    if (loading.value && !force) return
    loading.value = true
    try {
      const data = await userApi.pointsBalance()
      summary.value = data
      balance.value = data.balance
    } catch {
      // 未登录或接口异常时静默，不阻塞页面
    } finally {
      loading.value = false
    }
  }

  function setBalance(v: number) {
    balance.value = v
    if (summary.value) summary.value = { ...summary.value, balance: v }
  }

  return { summary, balance, loading, fetch, setBalance }
})
