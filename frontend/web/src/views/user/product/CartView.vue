<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { productApi } from '@/api/modules/product'
import type { Product } from '@/api/modules/product'
import { userApi } from '@/api/modules/user'
import type { Coupon } from '@/api/modules/user'
import { orderApi } from '@/api/modules/order'
import PageSection from '@/components/common/PageSection.vue'
import ProductCard from '@/components/common/ProductCard.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()
const route = useRoute()
const cart = useCartStore()
const userStore = useUserStore()

// ==================== 购买成功弹窗 ====================
const showPaySuccess = ref(false)
const successOrderNo = ref('')

function closePaySuccess() {
  showPaySuccess.value = false
}

function goOrderDetail() {
  showPaySuccess.value = false
  if (successOrderNo.value) router.push('/order/detail/' + successOrderNo.value)
}

function goShoppingContinue() {
  showPaySuccess.value = false
  router.push('/category')
}

// ==================== 推荐商品 ====================
// 推荐卡片复用公共 ProductCard 组件（含加购/loading 逻辑）
const recommended = ref<Product[]>([])

function formatSpec(spec?: Record<string, string> | string): string {
  if (!spec) return ''
  let obj: Record<string, string> = {}
  if (typeof spec === 'string') {
    try {
      obj = JSON.parse(spec)
    } catch {
      return spec // 解析失败直接回显原始串，避免乱码
    }
  } else {
    obj = spec
  }
  return Object.entries(obj)
    .map(([k, v]) => `${k}: ${v}`)
    .join(' | ')
}

onMounted(async () => {
  try {
    await cart.fetch()
  } catch (e: any) {
    ElMessage.error(e?.message || '购物车加载失败')
  }
  // 推荐商品：拉取热门好物 feed（按销量+浏览排序，恒有内容）
  try {
    const list = await productApi.feed()
    recommended.value = (list || []).slice(0, 4)
  } catch (e: any) {
    ElMessage.error(e?.message || '推荐商品加载失败')
    recommended.value = []
  }
  // 优惠券：拉取真实可用券
  loadCoupons()

  // 支付回跳兜底：guard.ts 已把 return_url 重定向到 /order/detail/{orderNo}，
  // 此处仅为兼容用户手动收藏 /cart?paySuccess=1&orderNo= 的情况,直接跳订单详情。
  if (route.query.paySuccess === '1') {
    const on = String(route.query.orderNo || '')
    if (on) router.replace({ path: '/order/detail/' + on })
  }
})

// ==================== 优惠券（真实后端数据）====================
const coupons = ref<Coupon[]>([])
// 当前选中的券 id（null = 不使用券）；手动选中的优先；初始默认勾选在 loadCoupons 里设置
const appliedCouponId = ref<number | null>(null)

// 单券实际抵扣金额（满减 / 折扣两种类型）
function couponValue(c: Coupon): number {
  if (c.type === 'discount' && c.discount) {
    return Math.round((totalPrice.value || 0) * (1 - c.discount) * 100) / 100
  }
  return Math.min(c.amount || 0, totalPrice.value || 0)
}

// 我拥有的券：仅展示「已领取且有效」的券（claimed）；
// 已使用(used)/已失效(expired)/仅展示用(available) 一律不在购物车出现
const myCoupons = computed(() => coupons.value.filter((c) => c.status === 'claimed'))

// 是否达到使用门槛
function isApplicable(c: Coupon): boolean {
  return (Number(c.threshold) || 0) <= (totalPrice.value || 0)
}

// 可用券：未使用/未过期，且达到门槛
const applicableCoupons = computed(() =>
  myCoupons.value.filter(
    (c) => c.status !== 'used' && c.status !== 'expired' && isApplicable(c),
  ),
)

// 当前生效的券：完全以用户手动选择为准。appliedCouponId 为 null 即不使用任何券，
// 不再自动补选"力度最大的一张"——否则会覆盖用户主动取消的操作，导致券永远取消不掉。
const appliedCoupon = computed<Coupon | null>(() => {
  if (appliedCouponId.value == null) return null
  return applicableCoupons.value.find((c) => c.id === appliedCouponId.value) ?? null
})

const couponDiscount = computed(() => (appliedCoupon.value ? couponValue(appliedCoupon.value) : 0))

// 折叠：券过多时默认只展示前 N 张；已选中的券始终保留可见，避免被折起后看不到选中态
const COUPON_COLLAPSE_LIMIT = 2
const showAllCoupons = ref(false)
const displayCoupons = computed<Coupon[]>(() => {
  if (showAllCoupons.value || myCoupons.value.length <= COUPON_COLLAPSE_LIMIT) {
    return myCoupons.value
  }
  const head = myCoupons.value.slice(0, COUPON_COLLAPSE_LIMIT)
  if (appliedCoupon.value && !head.some((c) => c.id === appliedCoupon.value!.id)) {
    const rest = myCoupons.value.filter((c) => c.id !== appliedCoupon.value!.id)
    return rest.slice(0, COUPON_COLLAPSE_LIMIT)
  }
  return head
})

// 点击切换某张券的选用状态
function toggleCoupon(c: Coupon) {
  if (!isApplicable(c)) return
  appliedCouponId.value = appliedCouponId.value === c.id ? null : c.id
}

async function loadCoupons() {
  try {
    const list = await userApi.coupons()
    coupons.value = list ?? []
    // 默认勾选力度最大的一张可用券（若都不可用则不勾选）
    appliedCouponId.value = applicableCoupons.value.length
      ? applicableCoupons.value.reduce((a, b) => (couponValue(b) > couponValue(a) ? b : a)).id
      : null
  } catch (e: any) {
    ElMessage.error(e?.message || '优惠券加载失败')
    coupons.value = []
  }
}

// 一键领取一张尚未领取的券
async function claimTopCoupon() {
  const top = coupons.value.find((c) => c.status === 'available')
  if (!top) {
    ElMessage.info('暂无可领取的优惠券')
    return
  }
  try {
    await userApi.claimCoupon(top.id)
    ElMessage.success(`已领取：${top.name}`)
    await loadCoupons()
  } catch (e: any) {
    ElMessage.error(e?.message || '领取失败')
  }
}

const selectedCount = computed(() => cart.validItems.filter((i) => i.selected).length)
const totalPrice = computed(() =>
  cart.validItems.filter((i) => i.selected).reduce((s, i) => s + i.price * i.quantity, 0),
)
// 运费：VIP（vipLevel>=2 且未过期）免邮；其他用户固定 10 元（与后端 default_freight 一致）
const shippingFee = computed(() => {
  const v = userStore.user?.vipLevel ?? 0
  return v >= 2 ? 0 : 10
})
const freeShipping = computed(() => shippingFee.value === 0)
// 全选态：全部有效商品都勾上才算全选；部分勾选时 checkbox 显示 indeterminate 半选样式
const allSelected = computed(
  () => cart.validItems.length > 0 && selectedCount.value === cart.validItems.length,
)
const someSelected = computed(
  () => selectedCount.value > 0 && selectedCount.value < cart.validItems.length,
)
function onToggleAll(e: Event) {
  cart.toggleSelectAll((e.target as HTMLInputElement).checked)
}
const finalPrice = computed(() =>
  Math.max(0, totalPrice.value + shippingFee.value - couponDiscount.value),
)

async function remove(id: number) {
  await ElMessageBox.confirm('确定删除该商品？', '提示', {
    confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning',
  })
  try {
    await cart.remove([id])
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function checkout() {
  if (cart.selectedItems.length === 0) {
    ElMessage.warning('请先选择要结算的商品')
    return
  }
  if (cart.invalidItems.length > 0) {
    ElMessage.info(`已自动排除 ${cart.invalidItems.length} 件失效商品`)
  }
  // 把购物车里已生效的优惠券带到结算页。
  // 历史实现只 push('/checkout')，用户在购物车看到的立减金额到了结算页凭空消失，
  // 下单时也从未把 couponId 提交给后端（后端其实一直支持），券等于白选。
  const cid = appliedCoupon.value?.id
  router.push(cid ? { path: '/checkout', query: { couponId: String(cid) } } : { path: '/checkout' })
}

// 清空失效（下架/库存不足）的商品
async function clearInvalid() {
  try {
    const res = await orderApi.clearInvalidCart()
    const removed = res?.removed ?? 0
    await cart.fetch()
    if (removed > 0) {
      ElMessage.success(`已清空 ${removed} 件失效商品`)
    } else {
      ElMessage.info('当前没有失效商品')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '清空失效商品失败')
  }
}

// 清空购物车全部商品
async function clearAll() {
  if (cart.items.length === 0) {
    ElMessage.info('购物车已经是空的')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定清空购物车的全部 ${cart.items.length} 件商品？此操作不可恢复`,
      '提示',
      {
        confirmButtonText: '清空',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return // 用户取消
  }
  try {
    const removed = await cart.clearAll()
    ElMessage.success(removed > 0 ? `已清空 ${removed} 件商品` : '购物车已是空的')
  } catch (e: any) {
    ElMessage.error(e?.message || '清空购物车失败')
  }
}
</script>

<template>
  <div class="cart-view">
    <div class="cart-container">
      <div class="cart-layout">
        <!-- 左列 -->
        <div class="cart-main">
          <div class="cart-head">
            <h1 class="cart-title">
              购物车 <span class="cart-count">({{ cart.validItems.length }}件商品)</span>
            </h1>
            <div class="cart-head__actions">
              <button class="clear-btn clear-btn--danger" @click="clearAll">
                <span class="material-symbols-outlined">delete_sweep</span>
                <span>清空购物车</span>
              </button>
            </div>
          </div>

          <PetEmptyState
            v-if="cart.items.length === 0"
            icon="shopping_cart"
            title="购物车空空如也"
            desc="去添加几件心仪的商品吧～"
          >
            <template #action>
              <button class="empty-go" @click="router.push('/category/browse')">去逛逛</button>
            </template>
          </PetEmptyState>

          <div v-else class="cart-list">
            <!-- 全选条：没有勾选入口时用户无法排除任何商品，结算必然是"全买"（#1） -->
            <div v-if="cart.validItems.length" class="select-bar">
              <label class="select-all">
                <input
                  type="checkbox"
                  class="cart-check"
                  :checked="allSelected"
                  :indeterminate.prop="someSelected"
                  @change="onToggleAll"
                />
                <span>全选</span>
              </label>
              <span class="select-tip">已选 {{ selectedCount }} / {{ cart.validItems.length }} 件</span>
            </div>

            <!-- 有效商品 -->
            <div
              v-for="item in cart.validItems"
              :key="item.id"
              class="cart-item pet-card"
              :class="{ 'cart-item--checked': item.selected }"
            >
              <label class="item-check">
                <input
                  type="checkbox"
                  class="cart-check"
                  :checked="item.selected"
                  @change="cart.toggleSelect(item.id, ($event.target as HTMLInputElement).checked)"
                />
              </label>
              <div class="item-cover">
                <img :src="item.productImage" :alt="item.productName" />
              </div>
              <div class="item-body">
                <div class="item-row">
                  <div class="item-info">
                    <h3 class="item-name">{{ item.productName }}</h3>
                    <span class="item-spec">{{ formatSpec(item.specValues) }}</span>
                  </div>
                  <button class="item-delete" @click="remove(item.id)">
                    <span class="material-symbols-outlined">delete</span>
                  </button>
                </div>
                <div class="item-foot">
                  <div class="item-price">
                    <span class="yen">¥</span>
                    <span class="amount">{{ item.price.toFixed(2) }}</span>
                  </div>
                  <div class="stepper">
                    <button @click="cart.updateQuantity(item.id, item.quantity - 1)">−</button>
                    <span class="stepper-val">{{ item.quantity }}</span>
                    <button @click="cart.updateQuantity(item.id, item.quantity + 1)">+</button>
                  </div>
                </div>
              </div>
            </div>

            <!-- 失效商品（已下架 / 已删除 / 售罄）：灰显、不可结算 -->
            <div v-if="cart.invalidItems.length" class="invalid-group">
              <div class="invalid-head">
                <span class="invalid-title">失效商品 · {{ cart.invalidItems.length }} 件</span>
                <button class="clear-btn" @click="clearInvalid">
                  <span class="material-symbols-outlined">cleaning_services</span>
                  <span>清空失效</span>
                </button>
              </div>
              <div
                v-for="item in cart.invalidItems"
                :key="item.id"
                class="cart-item pet-card cart-item--invalid"
              >
                <label class="item-check">
                  <input type="checkbox" class="cart-check" disabled />
                </label>
                <div class="item-cover">
                  <img :src="item.productImage" :alt="item.productName" />
                </div>
                <div class="item-body">
                  <div class="item-row">
                    <div class="item-info">
                      <h3 class="item-name">{{ item.productName }}</h3>
                      <span class="item-spec">{{ formatSpec(item.specValues) }}</span>
                      <span class="invalid-badge">{{ item.invalidReason || '商品已失效' }}</span>
                    </div>
                    <button class="item-delete" @click="remove(item.id)">
                      <span class="material-symbols-outlined">delete</span>
                    </button>
                  </div>
                  <div class="item-foot">
                    <div class="item-price item-price--muted">
                      <span class="yen">¥</span>
                      <span class="amount">{{ item.price.toFixed(2) }}</span>
                    </div>
                    <div class="stepper stepper--disabled">
                      <button disabled>−</button>
                      <span class="stepper-val">{{ item.quantity }}</span>
                      <button disabled>+</button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- 为您推荐 -->
          <PageSection class="recommend-section" title="为您推荐" icon="recommend">
            <div class="recommend-grid">
              <ProductCard
                v-for="r in recommended"
                :key="r.id"
                :id="r.id"
                :cover="r.mainImage"
                :name="r.name"
                :price="r.salePrice"
                :sale-count="r.saleCount ?? 0"
                :sku-id="r.defaultSkuId"
              />
            </div>
          </PageSection>
        </div>

        <!-- 右栏结算 -->
        <aside class="cart-side">
          <PageSection class="side-card" title="优惠券与折扣" icon="confirmation_number">
            <template v-if="myCoupons.length">
              <div
                v-for="c in displayCoupons"
                :key="c.id"
                class="coupon-row"
                :class="{
                  disabled: !isApplicable(c) || c.status === 'used' || c.status === 'expired',
                  active: appliedCoupon && c.id === appliedCoupon.id,
                }"
              >
                <span class="material-symbols-outlined coupon-icon">confirmation_number</span>
                <div class="coupon-text">
                  <div class="coupon-title">{{ c.name }}</div>
                  <div class="coupon-expire">
                    <template v-if="c.status === 'used'">已使用</template>
                    <template v-else-if="c.status === 'expired'">已过期</template>
                    <template v-else-if="!isApplicable(c)">还差 ¥{{ Math.max(0, (Number(c.threshold) || 0) - (totalPrice || 0)) }} 可用</template>
                    <template v-else>有效期至 {{ (c.validEnd || '').slice(0, 10) || '—' }}</template>
                  </div>
                </div>
                <button
                  v-if="c.status !== 'used' && c.status !== 'expired' && isApplicable(c)"
                  class="coupon-toggle"
                  @click="toggleCoupon(c)"
                >
                  <span class="material-symbols-outlined check" v-if="appliedCoupon && c.id === appliedCoupon.id">check_circle</span>
                  <span class="material-symbols-outlined" v-else>radio_button_unchecked</span>
                </button>
              </div>

              <button
                v-if="myCoupons.length > COUPON_COLLAPSE_LIMIT"
                class="coupon-collapse"
                @click="showAllCoupons = !showAllCoupons"
              >
                <span>{{ showAllCoupons ? '收起' : `展开全部 ${myCoupons.length} 张` }}</span>
                <span class="material-symbols-outlined">{{ showAllCoupons ? 'expand_less' : 'expand_more' }}</span>
              </button>
            </template>

            <div class="coupon-empty" v-else>
              <span class="material-symbols-outlined coupon-icon">confirmation_number</span>
              <span class="coupon-empty-text">暂无优惠券，先去领券</span>
            </div>
            <button class="use-coupon" @click="claimTopCoupon">
              <span class="material-symbols-outlined">add_circle</span>
              领取优惠券
            </button>
          </PageSection>

          <PageSection class="side-card" title="结算明细" icon="receipt_long">
            <div class="summary-row">
              <span>商品总计</span>
              <span class="amount-val">¥{{ totalPrice.toFixed(2) }}</span>
            </div>
            <div class="summary-row">
              <span>运费</span>
              <span :class="freeShipping ? 'free' : 'amount-val'">
                {{ freeShipping ? (userStore.user?.vipLevel >= 2 ? 'VIP 免邮' : '免运费') : `¥${shippingFee.toFixed(2)}` }}
              </span>
            </div>
            <div class="summary-row">
              <span>优惠减免</span>
              <span class="discount">-¥{{ couponDiscount.toFixed(2) }}</span>
            </div>
            <div class="summary-total">
              <span class="total-label">合计应付</span>
              <span class="total-val">¥{{ finalPrice.toFixed(2) }}</span>
            </div>
            <button class="checkout-btn" @click="checkout">
              去结算 <span class="material-symbols-outlined">arrow_forward</span>
            </button>
          </PageSection>
        </aside>
      </div>
    </div>
  </div>

  <!-- 购买成功弹窗 -->
  <transition name="fade">
    <div v-if="showPaySuccess" class="pay-success-mask" @click.self="closePaySuccess">
      <div class="pay-success-dialog">
        <div class="success-icon">
          <span class="material-symbols-outlined">check_circle</span>
        </div>
        <h2 class="success-title">购买成功</h2>
        <p class="success-sub">
          订单 <span class="order-no">{{ successOrderNo }}</span> 已支付成功，我们将尽快为您发货。
        </p>
        <div class="success-actions">
          <button class="btn-secondary" @click="goOrderDetail">查看订单</button>
          <button class="btn-primary" @click="goShoppingContinue">继续购物</button>
        </div>
      </div>
    </div>
  </transition>
</template>

<style lang="scss" scoped>
.cart-view {
  min-height: 100vh;
  background: $color-background;
}

.cart-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
}

.cart-layout {
  display: flex;
  flex-direction: column;
  gap: $width-gutter-desktop;

  @include desktop {
    flex-direction: row;
  }
}

.cart-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-2xl;
}

.cart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.cart-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

.cart-count {
  font-size: $font-size-body-md;
  font-weight: 400;
  color: $color-on-surface-variant;
  margin-left: $spacing-xs;
}

.clear-btn {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  height: 32px;
  padding: 0 $spacing-md;
  font-size: $font-size-label-md;
  font-weight: 500;
  color: $color-brand;
  background: $color-surface;
  border: 1px solid rgba($color-brand, 0.4);
  border-radius: 999px;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease;

  .material-symbols-outlined {
    font-size: 18px;
    line-height: 1;
  }

  &:hover {
    background: rgba($color-brand, 0.08);
    border-color: $color-brand;
    text-decoration: none;
  }

  &:active {
    background: rgba($color-brand, 0.16);
  }
}

.cart-head__actions {
  display: flex;
  gap: $spacing-sm;
}

.clear-btn--danger {
  color: $color-danger;
  border-color: rgba($color-danger, 0.4);

  &:hover {
    background: rgba($color-danger, 0.08);
    border-color: $color-danger;
    color: $color-danger;
  }

  &:active {
    background: rgba($color-danger, 0.16);
  }
}

.cart-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.cart-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.1);
  gap: $spacing-md;

  .empty-icon {
    font-size: 56px;
    color: $color-on-surface-variant;
    opacity: 0.6;
  }

  .empty-text {
    font-size: $font-size-body-md;
    color: $color-on-surface-variant;
  }

  .empty-go {
    padding: $spacing-xs $spacing-lg;
    background: $color-brand;
    color: $color-on-brand;
    border-radius: $radius-full;
    font-size: $font-size-label-md;
    font-weight: 600;
    cursor: pointer;
    transition: background $transition-fast;

    &:hover {
      background: $color-brand-dark;
    }
  }
}

/* 勾选条 + 单项勾选框（#1） */
.select-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-lg;
  background: $color-surface-container-lowest;
  border: 1px solid rgba(223, 192, 179, 0.18);
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.select-all {
  display: inline-flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  cursor: pointer;
  user-select: none;
}

.select-tip {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.cart-check {
  width: 18px;
  height: 18px;
  accent-color: $color-brand;
  cursor: pointer;

  &:disabled {
    cursor: not-allowed;
    opacity: 0.45;
  }
}

.item-check {
  position: absolute;
  top: $spacing-md;
  left: $spacing-md;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 3px;
  background: rgba(255, 255, 255, 0.92);
  border-radius: $radius-sm;
  cursor: pointer;

  @include desktop {
    position: static;
    align-self: center;
    padding: 0;
    background: transparent;
  }
}

.cart-item {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.1);
  transition: box-shadow $transition-base, border-color $transition-base;

  &--checked {
    border-color: rgba($color-brand, 0.42);
  }

  @include desktop {
    flex-direction: row;
    padding: $spacing-lg;
  }

  &:hover {
    box-shadow: $shadow-md;
  }
}

.item-cover {
  position: relative;
  width: 100%;
  height: 128px;
  background: $color-surface-container;
  border-radius: $radius-default;
  overflow: hidden;
  flex-shrink: 0;

  @include desktop {
    width: 128px;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.item-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-width: 0;
}

.item-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: $spacing-md;
}

.item-info {
  min-width: 0;
}

.item-name {
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.item-spec {
  display: inline-block;
  padding: 2px $spacing-xs;
  background: $color-surface-container-low;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  border-radius: $radius-sm;
}

.item-delete {
  color: $color-outline;
  cursor: pointer;
  transition: color $transition-fast;

  &:hover {
    color: $color-emergency;
  }
}

.item-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: $spacing-md;
}

.item-price {
  display: flex;
  align-items: baseline;
  gap: 2px;

  .yen {
    font-size: $font-size-label-md;
    font-weight: 700;
    color: $color-brand;
  }

  .amount {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 600;
    color: $color-brand;
  }

  .origin {
    margin-left: $spacing-xs;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    text-decoration: line-through;
  }
}

.stepper {
  display: flex;
  align-items: center;
  border: 1px solid rgba(223, 192, 179, 0.3);
  border-radius: $radius-full;
  overflow: hidden;

  button {
    width: 36px;
    height: 36px;
    font-size: $font-size-headline-md;
    color: $color-on-surface;
    cursor: pointer;
    transition: background $transition-fast;

    &:hover {
      background: $color-surface-container;
    }
  }

  .stepper-val {
    min-width: 40px;
    text-align: center;
    font-size: $font-size-body-md;
    font-weight: 600;
    border-left: 1px solid rgba(223, 192, 179, 0.3);
    border-right: 1px solid rgba(223, 192, 179, 0.3);
    padding: $spacing-xs 0;
  }
}

// ============ 失效商品 ============
.invalid-group {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1px dashed $color-outline-variant;
}

.invalid-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.invalid-title {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface-variant;
}

.cart-item--invalid {
  opacity: 0.6;
  background: $color-surface-container-low;

  .item-cover img {
    filter: grayscale(1);
  }
}

.invalid-badge {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  margin-top: 4px;
  padding: 1px $spacing-sm;
  font-size: $font-size-label-sm;
  font-weight: 600;
  color: #fff;
  background: $color-emergency;
  border-radius: $radius-sm;
}

.item-price--muted {
  .yen,
  .amount {
    color: $color-on-surface-variant;
    text-decoration: line-through;
  }
}

.stepper--disabled {
  opacity: 0.7;

  button {
    cursor: not-allowed;
    color: $color-outline;
  }
}

// ============ 推荐 ============
.recommend-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}

.recommend-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
  }
}

// ============ 侧栏 ============
.cart-side {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  width: 100%;

  @include desktop {
    width: 360px;
    // 不再被左栏拉伸到整列高度，改为跟随页面滚动时固定在导航下方
    align-self: flex-start;
    position: sticky;
    top: 84px; // 64px 固定导航 + 20px 间距
    // 防止优惠券过多时侧栏高度超过视口，导致 sticky 失效
    max-height: calc(100vh - 100px);
    overflow-y: auto;
    // 隐藏内部滚动条（仅在需要时滚动），保持视觉整洁
    scrollbar-width: thin;
    &::-webkit-scrollbar {
      width: 6px;
    }
    &::-webkit-scrollbar-thumb {
      background: rgba(223, 192, 179, 0.4);
      border-radius: 999px;
    }
  }
}

.side-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.1);
}

.side-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}

.coupon-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm;
  background: rgba(255, 122, 50, 0.05);
  border: 1px solid rgba(255, 122, 50, 0.3);
  border-radius: $radius-md;
  margin-bottom: $spacing-sm;
  transition: border-color $transition-fast, background $transition-fast, box-shadow $transition-fast;

  &.active {
    border-color: $color-brand;
    background: rgba(255, 122, 50, 0.14);
    box-shadow: 0 0 0 1px $color-brand;
  }

  &.disabled {
    opacity: 0.55;
    background: $color-surface-container-low;
    border-color: $color-outline-variant;
    border-style: dashed;

    .coupon-icon {
      color: $color-on-surface-variant;
    }
  }
}

.coupon-icon {
  color: $color-brand;
}

.coupon-empty {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm;
  background: $color-surface-container-low;
  border-radius: $radius-md;
  margin-bottom: $spacing-sm;

  .coupon-icon {
    color: $color-on-surface-variant;
  }
}

.coupon-empty-text {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

.coupon-text {
  flex: 1;
  min-width: 0;
}

.coupon-title {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}

.coupon-expire {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.coupon-toggle {
  display: flex;
  align-items: center;
  cursor: pointer;

  .material-symbols-outlined {
    font-size: 24px;
    color: $color-brand;
  }
}

.use-coupon {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-sm;
  border: 1px dashed $color-outline-variant;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  color: $color-on-surface;
  cursor: pointer;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

.coupon-collapse {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-xs;
  margin-bottom: $spacing-sm;
  background: transparent;
  border: none;
  border-radius: $radius-md;
  font-size: $font-size-label-sm;
  font-family: inherit;
  color: $color-brand;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: rgba(255, 122, 50, 0.08);
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.summary-row {
  display: flex;
  justify-content: space-between;
  padding: $spacing-sm 0;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;

  .amount-val {
    color: $color-on-surface;
    font-weight: 500;
  }

  .free {
    color: $color-success;
    font-weight: 700;
  }

  .discount {
    color: $color-emergency;
  }
}

.summary-total {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  padding-top: $spacing-md;
  border-top: 1px solid $color-outline-variant;
  margin-top: $spacing-sm;

  .total-label {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 600;
    color: $color-on-surface;
  }

  .total-val {
    font-family: $font-family-headline;
    font-size: $font-size-headline-lg;
    font-weight: 700;
    color: $color-emergency;
  }
}

.checkout-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-md;
  margin-top: $spacing-md;
  background: $color-brand;
  color: $color-on-brand;
  border-radius: $radius-md;
  font-size: $font-size-headline-md;
  font-weight: 600;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: background $transition-fast;

  &:hover {
    background: #ff9461;
  }
}
// ============ 购买成功弹窗（复用支付成功视觉） ============
.pay-success-mask {
  position: fixed;
  inset: 0;
  background: rgba(22, 29, 31, 0.5);
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-lg;
}

.pay-success-dialog {
  width: 100%;
  max-width: 420px;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-2xl $spacing-lg $spacing-lg;
  text-align: center;
  box-shadow: $shadow-lg;
}

.success-icon {
  width: 72px;
  height: 72px;
  margin: 0 auto $spacing-md;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(82, 196, 26, 0.12);
  color: $color-success;
  border-radius: $radius-full;

  .material-symbols-outlined {
    font-size: 44px;
    font-variation-settings: 'FILL' 1, 'wght' 600;
  }
}

.success-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;
}

.success-sub {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-lg;
  line-height: $line-height-normal;

  .order-no {
    font-family: $font-family-mono;
    color: $color-on-surface;
    font-weight: 600;
  }
}

.success-actions {
  display: flex;
  gap: $spacing-sm;

  button {
    flex: 1;
    padding: $spacing-sm $spacing-md;
    border-radius: $radius-md;
    font-size: $font-size-body-md;
    font-weight: 600;
    cursor: pointer;
    transition: all $transition-fast;
  }

  .btn-secondary {
    background: transparent;
    border: 1px solid $color-outline-variant;
    color: $color-on-surface;

    &:hover {
      border-color: $color-brand;
      color: $color-brand;
    }
  }

  .btn-primary {
    background: $color-brand;
    border: 1px solid $color-brand;
    color: $color-on-brand;

    &:hover {
      background: $color-brand-dark;
    }
  }
}

// ============ 过渡 ============
.fade-enter-active,
.fade-leave-active {
  transition: opacity $transition-base;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
@include mobile {
  .cart-container {
    padding-top: 1rem;
    padding-bottom: 1.5rem;
  }
  .cart-layout {
    gap: 1rem;
  }
  .cart-main {
    gap: 1.5rem;
  }
  .select-bar {
    padding: 0.5rem 0.75rem;
    flex-wrap: wrap;
    gap: 0.5rem;
  }
  .cart-item {
    padding: 0.75rem;
    gap: 0.75rem;
  }
  .item-cover {
    height: 8rem;
  }
  .item-name {
    font-size: 0.9375rem;
  }
  .item-foot {
    margin-top: 0.5rem;
  }
  .stepper button {
    width: 2.25rem;
    height: 2.25rem;
  }
  .stepper .stepper-val {
    min-width: 2.5rem;
    padding: 0.375rem 0;
  }
  .recommend-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 0.5rem;
  }
  .cart-side {
    width: 100%;
  }
  .side-card {
    padding: 0.75rem;
  }
  .coupon-row,
  .coupon-empty,
  .use-coupon,
  .coupon-toggle {
    padding-left: 0.75rem;
    padding-right: 0.75rem;
  }
  .checkout-btn {
    padding: 0.875rem;
  }
  .pay-success-mask {
    padding: 1rem;
  }
  .pay-success-dialog {
    padding: 1.5rem 1rem 1rem;
  }
  .success-actions button {
    padding: 0.625rem 0.75rem;
  }
}

</style>