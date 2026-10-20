import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { CartItem } from '@/api/modules/order'
import { orderApi } from '@/api/modules/order'
import { useUser } from '@/composables/useUser'
import { trackEvent } from '@/api/track'

/**
 * 购物车 Store
 */
export const useCartStore = defineStore('cart', () => {
  const items = ref<CartItem[]>([])
  // 初始为 true：进网页时先隐藏红点,等真实数据拉回后再决定是否显示,
  // 避免持久化的旧 items 在首帧闪现成"异常红点"
  const loading = ref(true)

  // 计算属性
  const totalCount = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

  const selectedItems = computed(() =>
    items.value.filter((item) => item.selected && item.valid !== false),
  )

  // 有效商品（未下架/未删除/有库存）：用于合计与结算，失效商品不计入
  const validItems = computed(() => items.value.filter((item) => item.valid !== false))

  // 失效商品（已下架/已删除/售罄）：展示在「失效商品」分组，不参与结算
  const invalidItems = computed(() => items.value.filter((item) => item.valid === false))

  const totalAmount = computed(() =>
    selectedItems.value.reduce((sum, item) => sum + item.price * item.quantity, 0),
  )

  const selectedCount = computed(() => selectedItems.value.length)

  // 加载购物车（进网页 / 登录后调用一次即可，保证红点与后端一致）
  async function fetch() {
    const { isLoggedIn } = useUser()
    // 未登录：购物车是用户维度的，没有数据，直接清空并隐藏红点
    if (!isLoggedIn.value) {
      items.value = []
      loading.value = false
      return
    }
    loading.value = true
    try {
      const data = await orderApi.getCart()
      items.value = data || []
    } catch {
      // 任何失败（网络/鉴权失效）都清空,绝不保留可能异常的脏数据
      items.value = []
    } finally {
      loading.value = false
    }
  }

  // 加入购物车
  async function add(skuId: number, quantity = 1, meta?: { productId?: number; merchantId?: number }) {
    await orderApi.addCart({ skuId, quantity })
    // 埋点：加购事件（商品维度，供运营漏斗统计真实加购转化）
    trackEvent('addCart', {
      targetType: 'product',
      targetId: meta?.productId,
      merchantId: meta?.merchantId,
    })
  }

  // 更新数量
  async function updateQuantity(id: number, quantity: number) {
    const item = items.value.find((i) => i.id === id)
    if (!item) return
    const oldQty = item.quantity
    item.quantity = Math.max(1, quantity)
    try {
      await orderApi.updateCart(id, { quantity: item.quantity })
    } catch {
      item.quantity = oldQty
    }
  }

  // 切换选中
  async function toggleSelect(id: number, selected?: boolean) {
    const item = items.value.find((i) => i.id === id)
    if (!item) return
    item.selected = selected ?? !item.selected
    try {
      await orderApi.updateCart(id, { selected: item.selected })
    } catch {
      item.selected = !item.selected
    }
  }

  // 全选 / 全不选：只作用于有效商品（失效商品本就不可结算），并同步落库
  async function toggleSelectAll(selected: boolean) {
    const targets = items.value.filter((i) => i.valid !== false && i.selected !== selected)
    if (!targets.length) return
    targets.forEach((i) => (i.selected = selected))
    try {
      await Promise.all(targets.map((i) => orderApi.updateCart(i.id, { selected })))
    } catch {
      targets.forEach((i) => (i.selected = !selected))
    }
  }

  // 删除
  async function remove(ids: number[]) {
    const backup = items.value.filter((i) => !ids.includes(i.id))
    items.value = backup
    try {
      await orderApi.deleteCart(ids)
    } catch {
      // 失败回滚
    }
  }

  // 清空（仅本地，不请求后端——用于登出 / 切账号等本地状态重置场景）
  function clear() {
    items.value = []
  }

  // 清空购物车全部商品（请求后端，物理删除当前用户 t_cart 行）
  async function clearAll() {
    const backup = items.value
    items.value = []
    try {
      const res = await orderApi.clearCart()
      return res?.removed ?? backup.length
    } catch (e) {
      // 失败回滚，避免 UI 显示已清空但后端还在
      items.value = backup
      throw e
    }
  }

  return {
    items,
    loading,
    totalCount,
    totalAmount,
    selectedItems,
    selectedCount,
    validItems,
    invalidItems,
    fetch,
    add,
    updateQuantity,
    toggleSelect,
    toggleSelectAll,
    remove,
    clear,
    clearAll,
  }
}, {
  persist: {
    key: 'pm-cart-store',
    storage: localStorage,
    paths: ['items'],
  },
})