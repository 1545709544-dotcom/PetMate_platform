<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { orderApi } from '@/api/modules/order'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.params.orderNo || ''))
const amount = ref(0)
const loading = ref(false)
const orderStatus = ref<string>('')

// 支付宝真实支付（电脑网站支付）：收银台表单 + 轮询
const pageBody = ref('') // 支付宝返回的表单 HTML
const payUrl = ref('') // 兜底跳转 URL
const outTradeNo = ref('')
const redirecting = ref(false)
let pollTimer: number | null = null

// ============================================================
// 倒计时（基于后端 expire_at, 刷新/重进页面不会重置）
// ============================================================
const remaining = ref(0)  // 剩余秒数, 由 expireAt 算出
let timer: number | null = null

/** 把后端 expireAt (ISO 字符串) 换算成剩余秒数; 过期/无效返回 0 */
function calcRemaining(expireAt?: string | null): number {
  if (!expireAt) return 0
  const t = new Date(expireAt.replace(' ', 'T')).getTime()
  if (Number.isNaN(t)) return 0
  return Math.max(0, Math.floor((t - Date.now()) / 1000))
}

const countdownText = computed(() => {
  const m = Math.floor(remaining.value / 60)
    .toString()
    .padStart(2, '0')
  const s = (remaining.value % 60).toString().padStart(2, '0')
  return `${m}:${s}`
})

const isExpired = computed(() => remaining.value <= 0)

onMounted(async () => {
  if (!orderNo.value) {
    ElMessage.error('订单号缺失')
    return
  }
  loading.value = true
  try {
    const order = await orderApi.orderDetail(orderNo.value)
    // 优先用 payAmount，缺失则降级为 totalAmount
    amount.value = (order as any).payAmount ?? order.totalAmount ?? 0
    orderStatus.value = order.status
    // 从后端 expireAt 取真实截止时间（不会因刷新而重置）
    remaining.value = calcRemaining((order as any).expireAt)
    // 非待支付订单不允许继续支付
    if (order.status && order.status !== 'pending_pay') {
      ElMessage.warning('该订单当前状态不支持支付')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '订单加载失败')
  } finally {
    loading.value = false
  }

  timer = window.setInterval(async () => {
    if (remaining.value > 0) {
      remaining.value -= 1
    } else if (timer) {
      clearInterval(timer)
      timer = null
      // 倒计时归零: 拉一次最新订单状态, 后端 scheduler 已把订单置 cancelled
      try {
        const order = await orderApi.orderDetail(orderNo.value)
        if (order.status === 'cancelled') {
          ElMessage.warning('订单超时未支付, 已自动取消')
        }
      } catch { /* 静默 */ }
    }
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (pollTimer) clearInterval(pollTimer)
})

// ============================================================
// 支付方式
// ============================================================
type PayChannel = 'alipay'

interface PayChannelOption {
  id: PayChannel
  name: string
  desc: string
  iconPath: string
  iconBg: string
  iconColor: string
  badge?: string
}

const channels: PayChannelOption[] = [
  {
    id: 'alipay',
    name: '支付宝',
    desc: '跳转支付宝收银台,支持余额/银行卡',
    iconPath:
      'M19.695 15.07c3.426 1.158 4.203 1.22 4.203 1.22V3.846c0-2.124-1.705-3.845-3.81-3.845H3.914C1.808.001.102 1.722.102 3.846v16.31c0 2.123 1.706 3.845 3.813 3.845h16.173c2.105 0 3.81-1.722 3.81-3.845v-.157s-6.19-2.602-9.315-4.119c-2.096 2.602-4.8 4.181-7.607 4.181-4.75 0-6.361-4.19-4.112-6.949.49-.602 1.324-1.175 2.617-1.497 2.025-.502 5.247.313 8.266 1.317a16.796 16.796 0 0 0 1.341-3.302H5.781v-.952h4.799V6.975H4.77v-.953h5.81V3.591s0-.409.411-.409h2.347v2.84h5.744v.951h-5.744v1.704h4.69a19.453 19.453 0 0 1-1.986 5.06c1.424.52 2.702 1.011 3.654 1.333m-13.81-2.032c-.596.06-1.71.325-2.321.869-1.83 1.608-.735 4.55 2.968 4.55 2.151 0 4.301-1.388 5.99-3.61-2.403-1.182-4.438-2.028-6.637-1.809',
    iconBg: '#1677ff',
    iconColor: '#ffffff',
  },
]

const selected = ref<PayChannel>('alipay')

// ============================================================
// 支付流程
// ============================================================
type PayState = 'idle' | 'paying' | 'success' | 'failed'
const payState = ref<PayState>('idle')

function formatAmount(v: number) {
  return `¥${v.toFixed(2)}`
}

async function submitPay() {
  if (isExpired.value) {
    ElMessage.warning('订单已超时,请重新下单')
    return
  }
  payState.value = 'paying'
  try {
    const res = await orderApi.pay(orderNo.value, selected.value)
    if (!res?.success) {
      payState.value = 'failed'
      ElMessage.error('支付失败,请重试')
      return
    }
    // 演示态（支付宝未启用）：直接成功
    if (res.demoMode) {
      payState.value = 'success'
      ElMessage.success('支付成功')
      return
    }
    // 真实支付宝（电脑网站支付）：注入收银台表单并新窗口跳转，同时继续轮询
    if (res.pageBody || res.payUrl) {
      pageBody.value = res.pageBody || ''
      payUrl.value = res.payUrl || ''
      outTradeNo.value = res.outTradeNo || ''
      redirecting.value = true
      startPoll()
      openAlipay()
    } else {
      payState.value = 'failed'
      ElMessage.error('未获取到支付宝支付信息')
    }
  } catch (e: any) {
    payState.value = 'failed'
    ElMessage.error(e?.message || '支付失败,请重试')
  }
}

// 把支付宝表单注入隐藏容器，或以跳转 URL 进入收银台
function openAlipay() {
  const html = pageBody.value
  if (html) {
    const mount = document.getElementById('alipay-form-mount')
    if (mount) {
      mount.innerHTML = html
      const form = mount.querySelector('form') as HTMLFormElement | null
      if (form) {
        // 新窗口跳转收银台：PayView 上下文保留，后台 3s 轮询可继续推进订单状态
        // (沙箱异步 notify 不可达，必须靠轮询 /order/pay/status 触发 tradeQuery 兜底)
        form.target = '_blank'
        form.submit()
        ElMessage.success('已在新窗口打开支付宝收银台,完成支付后请返回本页面')
        return
      }
    }
  }
  // 兜底：302 跳转地址（收银台 URL）— 新窗口打开
  if (payUrl.value) {
    window.open(payUrl.value, '_blank')
    ElMessage.success('已在新窗口打开支付宝收银台,完成支付后请返回本页面')
    return
  }
  ElMessage.warning('跳转失败,请重试')
}

// 手动重新跳转（自动跳转被拦截时）
function reopenAlipay() {
  openAlipay()
}

// 轮询订单状态：调 /order/pay/status（pending_pay 时由后端主动 tradeQuery 并置 paid）
function startPoll() {
  stopPoll()
  pollTimer = window.setInterval(async () => {
    if (isExpired.value) {
      stopPoll()
      return
    }
    try {
      const r: any = await orderApi.orderPayStatus(orderNo.value)
      if (r && r.paid) {
        stopPoll()
        payState.value = 'success'
        ElMessage.success('支付成功,正在返回上一页…')
        // 等用户看到 toast,再 router.back 回上一页(点「立即付款」的那个页面)
        setTimeout(() => router.back(), 1500)
      }
    } catch {
      // 忽略单次查询异常，继续轮询
    }
  }, 3000)
}

function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function retryPay() {
  payState.value = 'idle'
  pageBody.value = ''
  payUrl.value = ''
  redirecting.value = false
}

function goOrderList() {
  router.push('/order/list')
}

function goCart() {
  router.push('/cart')
}

async function cancelOrder() {
  if (!orderNo.value) {
    router.push('/cart')
    return
  }
  try {
    await orderApi.cancelOrder(orderNo.value)
    ElMessage.success('订单已取消')
  } catch (e: any) {
    ElMessage.error(e?.message || '取消订单失败')
  }
  router.push('/order/list')
}
</script>

<template>
  <div class="pay-view">
    <div class="pay-container">
      <!-- 顶部状态横幅 -->
      <div class="status-banner" :class="{ expired: isExpired }">
        <div class="banner-icon">
          <span class="material-symbols-outlined">
            {{ isExpired ? 'error' : 'check_circle' }}
          </span>
        </div>
        <div class="banner-text">
          <div class="banner-title">
            {{ isExpired ? '订单已超时' : '订单提交成功！' }}
          </div>
          <div class="banner-sub">
            <template v-if="isExpired">
              请重新创建订单后再发起支付。
            </template>
            <template v-else>
              请在 <span class="countdown">{{ countdownText }}</span> 内完成支付,超时订单将自动取消。
            </template>
          </div>
        </div>
      </div>

      <!-- 支付详情卡 -->
      <div class="card detail-card">
        <div class="detail-head">
          <span class="detail-title">支付详情</span>
          <span class="status-badge pending">待支付</span>
        </div>
        <div class="detail-rows">
          <div class="detail-row">
            <span class="row-label">订单编号</span>
            <span class="row-value">{{ orderNo }}</span>
          </div>
          <div class="detail-row total-row">
            <span class="row-label">应付总额</span>
            <span class="row-value amount">{{ formatAmount(amount) }}</span>
          </div>
        </div>
      </div>

      <!-- 选择支付方式 -->
      <div class="pay-section">
        <h3 class="section-title">选择支付方式</h3>

        <button
          v-for="c in channels"
          :key="c.id"
          class="channel-card"
          :class="{ active: selected === c.id }"
          @click="selected = c.id"
        >
          <span
            class="channel-icon"
            :style="{ background: c.iconBg, color: c.iconColor }"
          >
            <svg class="channel-logo" viewBox="0 0 24 24" aria-hidden="true">
              <path :d="c.iconPath" />
            </svg>
          </span>
          <div class="channel-text">
            <div class="channel-name">
              {{ c.name }}
              <span v-if="c.badge" class="channel-badge">{{ c.badge }}</span>
            </div>
            <div class="channel-desc">{{ c.desc }}</div>
          </div>
          <span
            class="channel-radio"
            :class="{ checked: selected === c.id }"
          >
            <span v-if="selected === c.id" class="radio-dot"></span>
          </span>
        </button>

        <div class="trust-row">
          <span class="material-symbols-outlined">shield</span>
          <span>支付环境已受加密保护,请放心支付</span>
        </div>
      </div>

      <!-- 支付宝电脑网站支付：跳转中 -->
      <div v-if="payState === 'paying' && redirecting" class="redirect-card">
        <div class="redirect-head">
          <span class="material-symbols-outlined">open_in_new</span>
          <span>正在跳转到支付宝收银台…</span>
        </div>
        <div class="redirect-box">
          <span class="redirect-spinner" />
        </div>
        <div class="redirect-tip">正在跳转到支付宝收银台,完成支付后将自动返回购买页面</div>
        <button class="redirect-link" type="button" @click="reopenAlipay">
          未自动打开?点此手动跳转
        </button>
        <div v-if="outTradeNo" class="qr-no">流水号 {{ outTradeNo }}</div>
      </div>

      <div class="bottom-spacer" />

      <!-- 底部固定支付条 -->
      <div class="pay-bar">
        <div class="bar-info">
          <div class="bar-label">实付款</div>
          <div class="bar-amount">{{ formatAmount(amount) }}</div>
        </div>
        <button
          v-if="!isExpired && payState !== 'success'"
          class="pay-btn"
          :disabled="payState === 'paying'"
          @click="submitPay"
        >
          <template v-if="payState === 'paying'">正在跳转支付宝…</template>
          <template v-else>
            立即支付
            <span class="material-symbols-outlined">chevron_right</span>
          </template>
        </button>
        <button v-else-if="isExpired" class="pay-btn secondary" @click="cancelOrder">
          重新下单
        </button>
        <button v-else class="pay-btn secondary" @click="goOrderList">
          查看订单
        </button>
      </div>
    </div>

    <!-- 支付宝收银台表单挂载点（隐藏，提交即跳转） -->
    <div id="alipay-form-mount" style="display: none" />

    <!-- 支付成功弹层 -->
    <transition name="fade">
      <div v-if="payState === 'success'" class="success-mask" @click.self="goOrderList">
        <div class="success-dialog">
          <div class="success-icon">
            <span class="material-symbols-outlined">check_circle</span>
          </div>
          <h2 class="success-title">支付成功</h2>
          <p class="success-sub">
            订单 <span class="order-no">{{ orderNo }}</span> 已支付成功,我们将尽快为您发货。
          </p>
          <div class="success-amount">{{ formatAmount(amount) }}</div>
          <div class="success-actions">
            <button class="btn-secondary" @click="goOrderList">查看订单</button>
            <button class="btn-primary" @click="goCart">去购物车</button>
          </div>
        </div>
      </div>
    </transition>

    <!-- 支付失败弹层 -->
    <transition name="fade">
      <div v-if="payState === 'failed'" class="success-mask" @click.self="retryPay">
        <div class="success-dialog">
          <div class="success-icon failed">
            <span class="material-symbols-outlined">cancel</span>
          </div>
          <h2 class="success-title">支付失败</h2>
          <p class="success-sub">支付未完成,请检查支付方式后重试。</p>
          <div class="success-actions">
            <button class="btn-secondary" @click="goOrderList">查看订单</button>
            <button class="btn-primary" @click="retryPay">重新支付</button>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style lang="scss" scoped>
.pay-view {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: 120px;
}

.pay-container {
  @include container-max;
  padding-top: $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

// ============ 状态横幅 ============
.status-banner {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: $color-brand;
  color: $color-on-brand;
  border-radius: $radius-md;
  box-shadow: $shadow-card;

  &.expired {
    background: $color-error-container;
    color: $color-on-brand-container;
    box-shadow: none;

    .banner-icon {
      background: rgba(186, 26, 26, 0.15);
      color: $color-danger;
    }

    .countdown {
      color: inherit;
      font-weight: 700;
    }
  }
}

.banner-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.2);
  border-radius: $radius-full;

  .material-symbols-outlined {
    font-size: 26px;
  }
}

.banner-text {
  flex: 1;
  min-width: 0;
}

.banner-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 700;
  margin-bottom: 4px;
}

.banner-sub {
  font-size: $font-size-label-md;
  opacity: 0.95;

  .countdown {
    font-family: $font-family-mono;
    font-weight: 700;
    color: #ffffff;
    background: rgba(255, 255, 255, 0.18);
    padding: 0 6px;
    border-radius: $radius-sm;
  }
}

// ============ 支付详情卡 ============
.card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-lg;
}

.detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.detail-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.status-badge {
  padding: 2px $spacing-sm;
  font-size: $font-size-label-sm;
  font-weight: 600;
  border-radius: $radius-full;

  &.pending {
    background: $color-tertiary-fixed;
    color: $color-tertiary;
  }
}

.detail-rows {
  display: flex;
  flex-direction: column;
}

.detail-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-sm 0;
  border-bottom: 1px dashed $color-outline-variant;
  font-size: $font-size-body-md;

  &:last-child {
    border-bottom: none;
  }

  &.total-row {
    border-top: 1px solid $color-outline-variant;
    margin-top: $spacing-xs;
    padding-top: $spacing-md;
    border-bottom: none;
  }
}

.row-label {
  color: $color-on-surface-variant;
}

.row-value {
  color: $color-on-surface;
  font-weight: 500;
  font-family: $font-family-mono;

  &.amount {
    font-family: $font-family-headline;
    font-size: 28px;
    font-weight: 700;
    color: $color-emergency;
    line-height: 1;
  }
}

.row-extra {
  font-size: $font-size-label-sm;
  color: $color-emergency;
  font-weight: 600;
}

// ============ 支付方式 ============
.pay-section {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.section-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
  margin: $spacing-xs 0 $spacing-xs $spacing-xs;
}

.channel-card {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-lowest;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-md;
  text-align: left;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand-light;
  }

  &.active {
    border-color: $color-brand;
    background: linear-gradient(135deg, rgba(255, 122, 50, 0.05), rgba(255, 122, 50, 0.02));
    box-shadow: 0 0 0 3px rgba(255, 122, 50, 0.08);
  }
}

.channel-icon {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-default;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);

  .channel-logo {
    width: 26px;
    height: 26px;
    fill: currentColor;
    display: block;
  }
}

.channel-text {
  flex: 1;
  min-width: 0;
}

.channel-name {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

.channel-badge {
  display: inline-flex;
  padding: 1px 6px;
  background: $color-brand-container;
  color: $color-on-brand-container;
  font-size: 10px;
  font-weight: 600;
  border-radius: $radius-sm;
}

.channel-desc {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 2px;
}

.channel-radio {
  width: 22px;
  height: 22px;
  border-radius: $radius-full;
  border: 2px solid $color-outline-variant;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: all $transition-fast;

  &.checked {
    border-color: $color-brand;
  }

  .radio-dot {
    width: 12px;
    height: 12px;
    border-radius: $radius-full;
    background: $color-brand;
  }
}

.trust-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-md 0;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-secondary;
  }
}

// ============ 底部支付条 ============
.pay-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: $z-index-modal - 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md $width-gutter-mobile;
  background: $color-surface-container-lowest;
  border-top: 1px solid $color-outline-variant;
  box-shadow: 0 -4px 16px rgba(22, 29, 31, 0.06);
  gap: $spacing-md;

  @include desktop {
    padding: $spacing-md $width-margin-desktop;
  }
}

.bar-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bar-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.bar-amount {
  font-family: $font-family-headline;
  font-size: 24px;
  font-weight: 700;
  color: $color-emergency;
  line-height: 1;
}

.pay-btn {
  flex: 1;
  max-width: 360px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: $spacing-md $spacing-lg;
  background: $color-brand;
  color: $color-on-brand;
  border-radius: $radius-md;
  font-size: $font-size-body-lg;
  font-weight: 700;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: background $transition-fast;

  &:hover:not(:disabled) {
    background: $color-brand-dark;
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }

  &.secondary {
    background: $color-surface-container-high;
    color: $color-on-surface;
    box-shadow: none;

    &:hover:not(:disabled) {
      background: $color-surface-container-highest;
    }
  }
}

.bottom-spacer {
  height: $spacing-md;
}

// ============ 支付宝电脑网站支付：跳转中 ============
.redirect-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border: 1.5px solid $color-brand-light;
  border-radius: $radius-lg;
  box-shadow: $shadow-card;
  text-align: center;
}

.redirect-head {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;

  .material-symbols-outlined {
    color: $color-brand;
    font-size: 24px;
  }
}

.redirect-box {
  padding: $spacing-sm 0;
}

.redirect-spinner {
  display: inline-block;
  width: 32px;
  height: 32px;
  border: 3px solid $color-brand-light;
  border-top-color: $color-brand;
  border-radius: $radius-full;
  animation: redirect-spin 0.8s linear infinite;
}

@keyframes redirect-spin {
  to {
    transform: rotate(360deg);
  }
}

.redirect-tip {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

.redirect-link {
  margin-top: $spacing-xs;
  padding: $spacing-xs $spacing-md;
  background: transparent;
  border: 1px solid $color-brand;
  color: $color-brand;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: rgba(255, 122, 50, 0.06);
  }
}

.qr-no {
  font-family: $font-family-mono;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

// ============ 支付结果弹层 ============
.success-mask {
  position: fixed;
  inset: 0;
  background: rgba(22, 29, 31, 0.5);
  z-index: $z-index-modal;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-lg;
}

.success-dialog {
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

  &.failed {
    background: rgba(255, 77, 79, 0.12);
    color: $color-emergency;
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
  margin-bottom: $spacing-md;
  line-height: $line-height-normal;

  .order-no {
    font-family: $font-family-mono;
    color: $color-on-surface;
    font-weight: 600;
  }
}

.success-amount {
  font-family: $font-family-headline;
  font-size: 36px;
  font-weight: 700;
  color: $color-emergency;
  margin-bottom: $spacing-lg;
  line-height: 1;
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
</style>