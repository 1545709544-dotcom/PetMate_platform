<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, type Address, type Coupon } from '@/api/modules/user'
import { useCartStore } from '@/stores/cart'
import { usePointsStore } from '@/stores/points'
import { useUserStore } from '@/stores/user'
import type { CartItem } from '@/api/modules/order'
import { orderApi } from '@/api/modules/order'

const router = useRouter()
const route = useRoute()
const cart = useCartStore()
const pointsStore = usePointsStore()
const userStore = useUserStore()

// ============================================================
// 收货地址
// ============================================================
const addresses = ref<Address[]>([])
const selectedAddressId = ref<number | null>(null)
const showAddressSheet = ref(false)

function regionOf(a: Address) {
  return [a.province, a.city, a.district].filter(Boolean).join(' ')
}

function tagLabel(tag?: Address['tag']) {
  if (!tag) return ''
  if (tag === 'home') return '家'
  if (tag === 'company') return '公司'
  if (tag === 'school') return '学校'
  return ''
}

// ============================================================
// 商品清单（从 store 选中的购物车项）
// ============================================================
const items = computed<CartItem[]>(() => cart.selectedItems)

function formatSpec(spec?: Record<string, string> | string) {
  if (!spec) return ''
  let obj: Record<string, string> = {}
  if (typeof spec === 'string') {
    try {
      obj = JSON.parse(spec)
    } catch {
      return spec
    }
  } else {
    obj = spec
  }
  return Object.entries(obj)
    .map(([k, v]) => `${k}: ${v}`)
    .join(' / ')
}

// ============================================================
// 订单备注 & 提交状态
// ============================================================
const remark = ref('')
const submitting = ref(false)

// ============================================================
// 爱心捐赠（随订单提交 body.donate，后端已落库 t_order.donate_amount 并在订单详情回显）
// ============================================================
const donateEnabled = ref(false)
const DONATE_PRESETS = [1, 5, 10, 20, 50]

function setDonateAmount(v: number) {
  donateAmountInput.value = Math.max(1, Math.floor(v || 1))
}
const donateAmountInput = ref(1)

// ============================================================
// 优惠券
//
// 后端 POST /order 一直支持 couponId（会校验门槛、核销 t_user_coupon 并写入 coupon_amount），
// 但结算页此前完全没有券的概念：购物车里选好的券到这里就丢了，下单金额也不含券。
// 这里补齐链路：接收购物车带过来的 couponId → 展示/可改 → 随订单提交。
// ============================================================
const coupons = ref<Coupon[]>([])
const selectedCouponId = ref<number | null>(null)
const showCouponPanel = ref(false)

// 单券实际抵扣金额（满减 / 折扣两种类型），与购物车口径保持一致
function couponValue(c: Coupon): number {
  if (c.type === 'discount' && c.discount) {
    return Math.round((productTotal.value || 0) * (1 - c.discount) * 100) / 100
  }
  return Math.min(c.amount || 0, productTotal.value || 0)
}

// 已领取且未使用的券（后端把 DB 的 available 映射为 claimed 下发）
const myCoupons = computed(() => coupons.value.filter((c) => c.status === 'claimed'))

// 达到门槛才可用
const applicableCoupons = computed(() =>
  myCoupons.value.filter((c) => (Number(c.threshold) || 0) <= (productTotal.value || 0)),
)

const appliedCoupon = computed<Coupon | null>(
  () => applicableCoupons.value.find((c) => c.id === selectedCouponId.value) || null,
)

const couponDiscount = computed(() =>
  appliedCoupon.value ? couponValue(appliedCoupon.value) : 0,
)

function pickCoupon(id: number | null) {
  selectedCouponId.value = id
  showCouponPanel.value = false
}

async function loadCoupons() {
  try {
    coupons.value = (await userApi.coupons()) ?? []
  } catch {
    coupons.value = []
    return
  }
  // 若购物车明确带了一张券过来，则沿用；否则不自动选（尊重用户在购物车主动取消的操作，
  // 也不要悄悄替用户勾上"力度最大的一张"——面板里已有"不使用优惠券"选项可供手动选择）
  const fromCart = Number(route.query.couponId)
  const carried = applicableCoupons.value.find((c) => c.id === fromCart)
  selectedCouponId.value = carried ? carried.id : null
}

// ============================================================
// 鱼干抵扣（10 鱼干 = ¥1，单笔最多抵订单金额 10%）
// ============================================================
const pointsApplied = ref(false)
const MAX_POINTS_DISCOUNT = 10 // 每笔订单鱼干抵扣上限（¥）

// 本单最多可用鱼干数：账户全部余额均可参与抵扣，仅受「封顶 ¥10（100 鱼干）」约束
const maxPointsUsable = computed(() => {
  if (pointsStore.balance <= 0) return 0
  const byCap = MAX_POINTS_DISCOUNT * 10 // ¥10 = 100 鱼干
  return Math.max(0, Math.min(pointsStore.balance, byCap))
})

// 实际抵扣金额（¥）：前端预演，后端会再次截断
const pointsDiscount = computed(() => {
  if (!pointsApplied.value || maxPointsUsable.value <= 0) return 0
  return Math.min(pointsStore.balance, maxPointsUsable.value) / 10
})

function togglePoints() {
  if (pointsStore.balance <= 0) {
    ElMessage.info('暂无鱼干可用')
    return
  }
  pointsApplied.value = !pointsApplied.value
}

// ============================================================
// 加载
// ============================================================
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    // 先确保购物车数据已加载
    if (cart.items.length === 0) {
      await cart.fetch()
    }
    await loadAddresses()
    await loadCoupons()
    // 鱼干余额（用于抵扣）
    try {
      await pointsStore.fetch()
    } catch {
      // 不影响结算
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '结算信息加载失败')
  } finally {
    loading.value = false
  }
})

async function loadAddresses() {
  const addrs = await userApi.addresses()
  addresses.value = addrs || []
  const def = addresses.value.find((a) => a.isDefault) || addresses.value[0]
  if (def && selectedAddressId.value == null) selectedAddressId.value = def.id
}

// 打开切换地址抽屉时刷新地址列表（覆盖从新增地址页返回的场景）
// 没有地址时按钮直接跳新增页，避免打开一个空抽屉让用户多一次操作
function openAddressSheet() {
  if (addresses.value.length === 0) {
    goAddAddress()
    return
  }
  showAddressSheet.value = true
  loadAddresses()
}

// 前往新增收货地址（路由 AddressEdit 不带 id 即新建）
function goAddAddress() {
  router.push({ name: 'AddressEdit' })
}

// ============================================================
// 计算
// ============================================================
const productCount = computed(() => items.value.length)
const productTotal = computed(() =>
  items.value.reduce((s, i) => s + i.price * i.quantity, 0),
)
// 运费：VIP 用户（黄金 vipLevel>=2 且未过期）免邮；普通用户固定 10 元（与后端 default_freight 一致）
const shippingFee = computed(() => {
  const v = userStore.user?.vipLevel ?? 0
  return v >= 2 ? 0 : 10
})
const freeShipping = computed(() => shippingFee.value === 0)
// 应付总额 = 商品 + 运费 - 优惠券 - 鱼干抵扣 + 爱心捐赠
const finalTotal = computed(() =>
  Math.max(
    0,
    productTotal.value +
      shippingFee.value -
      couponDiscount.value -
      pointsDiscount.value +
      donateAmount.value,
  ),
)
const donateAmount = computed(() =>
  donateEnabled.value ? Math.max(1, Math.floor(donateAmountInput.value || 1)) : 0,
)

// ============================================================
// 操作
// ============================================================
const selectedAddress = computed(
  () => addresses.value.find((a) => a.id === selectedAddressId.value) || addresses.value[0],
)

function selectAddress(id: number) {
  selectedAddressId.value = id
  showAddressSheet.value = false
}

async function submitOrder() {
  if (!selectedAddress.value) {
    ElMessage.warning('请选择收货地址')
    return
  }
  if (items.value.length === 0) {
    ElMessage.warning('请先选择要结算的商品')
    return
  }
  submitting.value = true
  try {
    const order = await orderApi.submitOrder({
      cartIds: items.value.map((i) => i.id),
      addressId: selectedAddress.value.id,
      couponId: appliedCoupon.value?.id,
      remark: remark.value || undefined,
      donate: donateEnabled.value ? Math.max(1, Math.floor(donateAmountInput.value || 1)) : 0,
      pointsUse: pointsApplied.value ? Math.min(pointsStore.balance, maxPointsUsable.value) : 0,
    })
    ElMessage.success('订单已创建，正在跳转支付…')
    router.push(`/pay/${order.orderNo}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '提交订单失败')
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.back()
}
</script>

<template>
  <div class="checkout-view">
    <div class="checkout-container">
      <nav class="topbar">
        <button class="back-btn" @click="goBack">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <span class="topbar-title">确认订单</span>
      </nav>

      <div class="layout">
        <!-- 左主区 -->
        <div class="main">
          <!-- 收货地址 -->
          <section class="card address-card">
            <div class="card-head">
              <span class="material-symbols-outlined head-icon">location_on</span>
              <span class="card-title">收货地址</span>
              <button class="switch-btn" @click="openAddressSheet">
                {{ addresses.length ? '切换地址' : '新增地址' }}
              </button>
            </div>
            <div v-if="selectedAddress" class="address-body">
              <div class="addr-line">
                <span class="addr-name">{{ selectedAddress.receiver }}</span>
                <span class="addr-phone">{{ selectedAddress.phone }}</span>
                <span v-if="selectedAddress.tag" class="addr-tag">{{ tagLabel(selectedAddress.tag) }}</span>
              </div>
              <div class="addr-detail">
                {{ regionOf(selectedAddress) }} {{ selectedAddress.detail }}
              </div>
            </div>
            <div v-else class="address-empty" @click="goAddAddress">
              暂无收货地址，<span class="link">点击新增</span>
            </div>
          </section>

          <!-- 商品清单 -->
          <section class="card">
            <div class="card-head">
              <span class="material-symbols-outlined head-icon">shopping_cart</span>
              <span class="card-title">商品清单</span>
            </div>
            <div v-if="items.length === 0" class="item-empty">
              暂无可结算的商品
            </div>
            <div v-else class="item-list">
              <div v-for="it in items" :key="it.id" class="item">
                <div class="item-cover">
                  <img :src="it.productImage" :alt="it.productName" />
                </div>
                <div class="item-body">
                  <h4 class="item-name">{{ it.productName }}</h4>
                  <p class="item-spec">{{ formatSpec(it.specValues) }}</p>
                  <p class="item-qty">数量: x{{ it.quantity }}</p>
                </div>
                <div class="item-price">
                  <span class="yen">¥</span>
                  <span class="amount">{{ it.price.toFixed(2) }}</span>
                </div>
              </div>
            </div>
          </section>

          <!-- 配送方式（全场统一标准配送，仅一种） -->
          <section class="card">
            <div class="card-head">
              <span class="material-symbols-outlined head-icon">local_shipping</span>
              <span class="card-title">配送方式</span>
            </div>
            <div class="ship-single">
              <span class="ship-radio static">
                <span class="material-symbols-outlined">check</span>
              </span>
              <div class="ship-main">
                <div class="ship-head">
                  <span class="ship-name">标准配送</span>
                  <span v-if="freeShipping" class="ship-tag free">VIP 免邮</span>
                  <span v-else class="ship-tag paid">运费 ¥{{ shippingFee.toFixed(2) }}</span>
                </div>
                <div class="ship-desc">
                  {{ freeShipping ? '您是黄金及以上 VIP，会员专享免邮' : '普通用户固定运费 10 元 / 单' }} · 预计 3-5 个工作日内送达
                </div>
              </div>
            </div>
          </section>

          <!-- 备注 -->
          <section class="card">
            <div class="card-head">
              <span class="material-symbols-outlined head-icon">edit_note</span>
              <span class="card-title">订单备注</span>
              <span class="card-sub">选填</span>
            </div>
            <textarea
              v-model="remark"
              class="remark-input"
              placeholder="请输入您的特殊需求,例如:放门口即可、节后再发货等"
              rows="3"
              maxlength="100"
            />
          </section>
        </div>

        <!-- 右栏 -->
        <aside class="side">
          <div class="card">
            <h3 class="side-title">费用概览</h3>
            <div class="sum-row">
              <span>商品总计 ({{ productCount }}件)</span>
              <span class="sum-val">¥{{ productTotal.toFixed(2) }}</span>
            </div>
            <div class="sum-row">
              <span>运费</span>
              <span :class="freeShipping ? 'sum-free' : 'sum-val'">
                {{ freeShipping ? '会员免运费' : `¥${shippingFee.toFixed(2)}` }}
              </span>
            </div>
            <!-- 优惠券：点击展开可用券列表，选中后计入应付总额并随订单提交 -->
            <div
              class="sum-row coupon-row"
              :class="{ inactive: !appliedCoupon, disabled: applicableCoupons.length === 0 }"
              @click="applicableCoupons.length && (showCouponPanel = !showCouponPanel)"
            >
              <span class="coupon-label">
                <span class="material-symbols-outlined">confirmation_number</span>
                优惠券
              </span>
              <span :class="appliedCoupon ? 'sum-discount' : 'sum-original'">
                <template v-if="applicableCoupons.length === 0">暂无可用券</template>
                <template v-else-if="appliedCoupon">
                  -¥{{ couponDiscount.toFixed(2) }}
                  <span class="material-symbols-outlined caret">expand_more</span>
                </template>
                <template v-else>
                  {{ applicableCoupons.length }} 张可用
                  <span class="material-symbols-outlined caret">expand_more</span>
                </template>
              </span>
            </div>
            <div v-if="showCouponPanel" class="coupon-panel">
              <button
                v-for="c in applicableCoupons"
                :key="c.id"
                class="coupon-opt"
                :class="{ active: c.id === selectedCouponId }"
                @click.stop="pickCoupon(c.id)"
              >
                <span class="opt-name">{{ c.name }}</span>
                <span class="opt-val">-¥{{ couponValue(c).toFixed(2) }}</span>
              </button>
              <button
                class="coupon-opt"
                :class="{ active: selectedCouponId === null }"
                @click.stop="pickCoupon(null)"
              >
                <span class="opt-name">不使用优惠券</span>
              </button>
            </div>
            <div
              class="sum-row points-row"
              :class="{ inactive: !pointsApplied, disabled: maxPointsUsable <= 0 }"
              @click="togglePoints"
            >
              <span class="points-label">
                <span class="points-emoji">🐟</span>
                鱼干抵扣
              </span>
              <span :class="pointsApplied ? 'sum-discount' : 'sum-original'">
                <template v-if="pointsStore.balance <= 0">暂无鱼干</template>
                <template v-else-if="pointsApplied">已抵 ¥{{ pointsDiscount.toFixed(2) }}（{{ Math.min(pointsStore.balance, maxPointsUsable) }} 鱼干）</template>
                <template v-else>可用 {{ pointsStore.balance }} 鱼干 · 本单最多抵 ¥{{ MAX_POINTS_DISCOUNT }}</template>
              </span>
            </div>
            <div v-if="donateEnabled" class="sum-row donate-row">
              <span class="donate-label">
                <span class="material-symbols-outlined">volunteer_activism</span>
                爱心捐赠
              </span>
              <span class="sum-donate">+¥{{ donateAmount.toFixed(2) }}</span>
            </div>
            <div class="sum-total">
              <div class="total-label">
                <span class="lbl">应付总额</span>
                <span class="sub">已含税费</span>
              </div>
              <div class="total-val">¥{{ finalTotal.toFixed(2) }}</div>
            </div>
            <button class="submit-btn" :disabled="submitting" @click="submitOrder">
              <template v-if="submitting">提交中…</template>
              <template v-else>
                立即下单支付
                <span class="material-symbols-outlined">arrow_forward</span>
              </template>
            </button>
            <ul class="trust-list">
              <li><span class="material-symbols-outlined">verified_user</span>支付宝安全保护中</li>
              <li><span class="material-symbols-outlined">autorenew</span>7天无理由退货（部分商品除外）</li>
            </ul>
          </div>

          <!-- 爱心捐赠（可开关 + 金额可选，随订单提交 donate 字段） -->
          <div
            class="donate-card"
            :class="{ on: donateEnabled }"
            role="checkbox"
            :aria-checked="donateEnabled"
            tabindex="0"
            @click="donateEnabled = !donateEnabled"
            @keyup.enter.space.prevent="donateEnabled = !donateEnabled"
          >
            <div class="donate-icon">
              <span class="material-symbols-outlined">volunteer_activism</span>
            </div>
            <div class="donate-text">
              <div class="donate-title">爱心捐赠</div>
              <div class="donate-desc">
                为流浪猫救助计划献出一份爱心
              </div>
            </div>
            <span class="donate-toggle" :class="{ active: donateEnabled }">
              <span class="material-symbols-outlined">{{ donateEnabled ? 'check' : 'add' }}</span>
            </span>
          </div>
          <div v-if="donateEnabled" class="donate-amounts">
            <button
              v-for="p in DONATE_PRESETS"
              :key="p"
              class="donate-chip"
              :class="{ active: donateAmountInput === p }"
              @click="setDonateAmount(p)"
            >¥{{ p }}</button>
            <label class="donate-custom">
              <span class="yen">¥</span>
              <input
                type="number"
                min="1"
                v-model.number="donateAmountInput"
                @input="setDonateAmount(donateAmountInput)"
              />
            </label>
          </div>
        </aside>
      </div>
    </div>

    <!-- 切换地址抽屉 -->
    <el-drawer v-model="showAddressSheet" title="选择收货地址" direction="rtl" size="420px">
      <div class="addr-sheet">
        <div
          v-for="a in addresses"
          :key="a.id"
          class="addr-item"
          :class="{ active: selectedAddressId === a.id }"
          @click="selectAddress(a.id)"
        >
          <div class="addr-line">
            <span class="addr-name">{{ a.receiver }}</span>
            <span class="addr-phone">{{ a.phone }}</span>
            <span v-if="a.tag" class="addr-tag">{{ tagLabel(a.tag) }}</span>
          </div>
          <div class="addr-detail">{{ regionOf(a) }} {{ a.detail }}</div>
          <span
            v-if="selectedAddressId === a.id"
            class="material-symbols-outlined addr-check"
          >check_circle</span>
        </div>
        <button class="add-addr" @click="goAddAddress">
          <span class="material-symbols-outlined">add</span>
          新增收货地址
        </button>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.checkout-view {
  min-height: 100vh;
  background: $color-background;
}

.checkout-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
}

// ============ 顶部条 ============
.topbar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;
}

.back-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  color: $color-on-surface;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container;
  }
}

.topbar-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

// ============ 布局 ============
.layout {
  display: flex;
  flex-direction: column;
  gap: $width-gutter-desktop;

  @include desktop {
    flex-direction: row;
  }
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.side {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  width: 100%;

  @include desktop {
    width: 380px;
    flex-shrink: 0;
  }
}

// ============ 卡片通用 ============
.card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-lg;
}

.card-head {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
}

.head-icon {
  font-size: 20px;
  color: $color-brand;
}

.card-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.card-sub {
  margin-left: auto;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.switch-btn {
  margin-left: auto;
  font-size: $font-size-label-md;
  color: $color-secondary;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

// ============ 地址卡 ============
.address-body {
  padding: $spacing-md;
  background: $color-brand-container;
  border-radius: $radius-default;
  border-left: 3px solid $color-brand;
}

.addr-line {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xs;
}

.addr-name {
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.addr-phone {
  font-size: $font-size-body-md;
  color: $color-on-surface;
}

.addr-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px $spacing-xs;
  background: $color-brand-container;
  color: $color-on-brand-container;
  font-size: $font-size-label-sm;
  font-weight: 600;
  border-radius: $radius-sm;
}

.addr-detail {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: $line-height-normal;
}

.address-empty,
.item-empty {
  padding: $spacing-md;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  text-align: center;
}

.address-empty {
  cursor: pointer;

  .link {
    color: $color-brand;
    font-weight: 600;

    &:hover {
      text-decoration: underline;
    }
  }
}

.inline-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: $spacing-sm;
  padding: 6px $spacing-md;
  background: rgba(255, 122, 50, 0.1);
  border: 1.5px solid $color-brand;
  border-radius: $radius-md;
  color: $color-brand-dark;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 16px;
  }

  &:hover {
    background: rgba(255, 122, 50, 0.18);
  }
}

// ============ 商品清单 ============
.item-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.item-cover {
  width: 80px;
  height: 80px;
  flex-shrink: 0;
  background: $color-surface-container;
  border-radius: $radius-default;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.item-body {
  flex: 1;
  min-width: 0;
}

.item-name {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: 4px;
  @include text-ellipsis(1);
}

.item-spec {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-bottom: 4px;
}

.item-qty {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.item-price {
  display: flex;
  align-items: baseline;
  flex-shrink: 0;

  .yen {
    font-size: $font-size-label-md;
    font-weight: 700;
    color: $color-on-surface;
  }

  .amount {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 600;
    color: $color-on-surface;
  }
}

// ============ 配送方式 ============
.ship-single {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-default;
}

.ship-card {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-default;
  text-align: left;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand-light;
  }

  &.active {
    border-color: $color-brand;
    background: $color-brand-container;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.15);
  }
}

.ship-radio {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid $color-outline;
  background: $color-surface-container-lowest;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 16px;
    color: #fff;
    opacity: 0;
    transform: scale(0.4);
    transition: all $transition-fast;
  }

  &.static {
    border-color: $color-brand;
    background: $color-brand;

    .material-symbols-outlined {
      opacity: 1;
      transform: scale(1);
    }
  }
}

.ship-main {
  flex: 1;
  min-width: 0;
}

.ship-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
  margin-bottom: 4px;
}

.ship-name {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
}

.ship-tag {
  flex-shrink: 0;
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-on-surface-variant;

  &.free {
    color: $color-success;
  }

  &.paid {
    padding: 1px $spacing-sm;
    background: rgba(255, 122, 50, 0.1);
    border-radius: $radius-full;
    color: $color-brand-dark;
  }
}

.ship-desc {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.ship-check {
  display: none;
}

// ============ 备注 ============
.remark-input {
  width: 100%;
  padding: $spacing-sm;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  font-family: $font-family-body;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  resize: vertical;
  min-height: 80px;
  transition: border-color $transition-fast;

  &::placeholder {
    color: $color-text-tertiary;
  }

  &:focus {
    outline: none;
    border-color: $color-brand;
    background: $color-surface-container-lowest;
  }
}

// ============ 费用概览卡 ============
.side-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}

.sum-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-sm 0;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;

  .sum-val {
    color: $color-on-surface;
    font-weight: 500;
  }

  .sum-free {
    color: $color-success;
    font-weight: 700;
  }

  .sum-discount {
    color: $color-emergency;
    font-weight: 600;
  }

  .sum-original {
    color: $color-secondary;
    font-weight: 500;
    font-size: $font-size-label-md;
  }
}

.points-row,
.coupon-row {
  cursor: pointer;
  padding: $spacing-sm;
  margin: 0 (-$spacing-sm);
  border-radius: $radius-default;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container-low;
  }

  &.inactive {
    opacity: 0.85;
  }

  &.disabled {
    cursor: not-allowed;
    opacity: 0.55;

    &:hover {
      background: transparent;
    }
  }
}

.coupon-label {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
  }
}

.caret {
  font-size: 16px !important;
  vertical-align: -3px;
  color: $color-secondary;
}

.coupon-panel {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: $spacing-sm;
  margin: 0 (-$spacing-sm) $spacing-xs;
  background: $color-surface-container-low;
  border-radius: $radius-default;

  .coupon-opt {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: $spacing-sm;
    width: 100%;
    padding: 8px 10px;
    background: $color-surface;
    border: 1px solid transparent;
    border-radius: $radius-default;
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;
    cursor: pointer;
    transition: all $transition-fast;

    &:hover {
      border-color: rgba($color-brand, 0.4);
    }

    &.active {
      border-color: $color-brand;
      background: rgba($color-brand, 0.08);
      color: $color-on-surface;
      font-weight: 600;
    }

    .opt-val {
      color: $color-emergency;
      font-weight: 700;
      white-space: nowrap;
    }
  }
}

  .points-label {
    display: inline-flex;
    align-items: center;
    gap: $spacing-xs;
    color: $color-on-surface-variant;

    .material-symbols-outlined {
      font-size: 18px;
      color: $color-brand;
    }

    .points-emoji {
      font-size: 18px;
      line-height: 1;
    }
  }

  .donate-row {
    .donate-label {
      display: inline-flex;
      align-items: center;
      gap: $spacing-xs;
      color: $color-on-surface-variant;

      .material-symbols-outlined {
        font-size: 18px;
        color: $color-brand;
      }
    }

    .sum-donate {
      color: $color-brand;
      font-weight: 700;
    }
  }

.sum-total {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-top: $spacing-md;
  margin-top: $spacing-sm;
  border-top: 1px dashed $color-outline-variant;
}

.total-label {
  display: flex;
  flex-direction: column;
  gap: 2px;

  .lbl {
    font-family: $font-family-headline;
    font-size: $font-size-body-lg;
    font-weight: 600;
    color: $color-on-surface;
  }

  .sub {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
  }
}

.total-val {
  font-family: $font-family-headline;
  font-size: 32px;
  font-weight: 700;
  color: $color-brand;
  line-height: 1;
}

.submit-btn {
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
  font-size: $font-size-body-lg;
  font-weight: 600;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: background $transition-fast;

  &:hover:not(:disabled) {
    background: $color-brand-dark;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.trust-list {
  list-style: none;
  padding: 0;
  margin: $spacing-md 0 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;

  li {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;

    .material-symbols-outlined {
      font-size: 16px;
      color: $color-secondary;
    }
  }
}

// ============ 爱心捐赠 ============
.donate-card {
  display: flex;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: $color-brand-container;
  border-radius: $radius-md;
  align-items: center;
  cursor: pointer;
  border: 1.5px solid transparent;
  transition: all $transition-fast;
  user-select: none;

  &:hover {
    border-color: $color-brand-light;
  }

  &.on {
    border-color: $color-brand;
    background: $color-brand-container;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.15);
  }

  &:focus-visible {
    outline: none;
    border-color: $color-brand;
  }
}

.donate-toggle {
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.65);
  color: $color-on-surface-variant;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &.active {
    background: $color-brand;
    color: #ffffff;
    transform: scale(1.05);
  }
}

.donate-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-full;
  flex-shrink: 0;

  .material-symbols-outlined {
    font-size: 22px;
  }
}

.donate-text {
  flex: 1;
  min-width: 0;
}

.donate-title {
  font-size: $font-size-body-md;
  font-weight: 700;
  color: $color-on-brand-container;
  margin-bottom: 2px;
}

  .donate-desc {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    line-height: $line-height-normal;

    .amount {
      color: $color-brand;
      font-weight: 700;
    }
  }

.donate-amounts {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-sm;
  padding: $spacing-sm;
  background: $color-surface-container-low;
  border-radius: $radius-md;
}

.donate-chip {
  min-width: 3rem;
  padding: 0.4rem 0.75rem;
  background: $color-surface-container-lowest;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-full;
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand-light;
  }

  &.active {
    border-color: $color-brand;
    background: $color-brand-container;
    color: $color-on-brand-container;
  }
}

.donate-custom {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0.4rem 0.75rem;
  background: $color-surface-container-lowest;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-full;

  .yen {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
  }

  input {
    width: 4rem;
    border: none;
    background: transparent;
    font-size: $font-size-body-md;
    font-weight: 600;
    color: $color-on-surface;
    outline: none;

    &::-webkit-outer-spin-button,
    &::-webkit-inner-spin-button {
      -webkit-appearance: none;
      margin: 0;
    }
    &[type='number'] {
      -moz-appearance: textfield;
      appearance: textfield;
    }
  }
}

// ============ 抽屉：地址列表 ============
.addr-sheet {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  padding: $spacing-xs 0;
}

.addr-item {
  position: relative;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-default;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand-light;
  }

  &.active {
    border-color: $color-brand;
    background: $color-brand-container;
  }
}

.addr-check {
  position: absolute;
  top: $spacing-md;
  right: $spacing-md;
  font-size: 22px;
  color: $color-brand;
}

.add-addr {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-md;
  background: transparent;
  border: 1.5px dashed $color-outline-variant;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
    background: rgba($color-brand, 0.06);
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}
@include mobile {
  .checkout-container {
    padding-top: 1rem;
    padding-bottom: 1.5rem;
  }
  .topbar {
    gap: 0.5rem;
    margin-bottom: 1rem;
  }
  .back-btn {
    width: 2.25rem;
    height: 2.25rem;
  }
  .layout {
    gap: 1rem;
  }
  .main,
  .side {
    gap: 0.75rem;
  }
  .card {
    padding: 1rem;
  }
  .card-head {
    margin-bottom: 0.75rem;
  }
  .address-body {
    padding: 0.75rem;
  }
  .item-cover {
    width: 5rem;
    height: 5rem;
  }
  .remark-input {
    padding: 0.5rem;
    min-height: 5rem;
  }
  .side-title {
    margin-bottom: 0.75rem;
  }
  .sum-row {
    padding: 0.5rem 0;
  }
  .points-row {
    padding: 0.5rem;
    margin-left: -0.5rem;
    margin-right: -0.5rem;
  }
  .ship-single {
    padding: 0.75rem;
  }
  .donate-card {
    padding: 0.75rem;
  }
  .donate-amounts {
    gap: 0.5rem;
  }
  .donate-chip,
  .donate-custom {
    flex: 1 1 auto;
  }
  .donate-chip {
    text-align: center;
  }
  .addr-item,
  .add-addr {
    padding: 0.75rem;
  }
  .addr-check {
    top: 0.75rem;
    right: 0.75rem;
  }
  .submit-btn {
    padding: 0.875rem;
  }
  .total-val {
    font-size: 2rem;
  }
  .trust-list {
    gap: 0.375rem;
  }
}

</style>