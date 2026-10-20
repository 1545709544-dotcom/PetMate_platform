<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api/modules/order'
import type { Order as ApiOrder } from '@/api/modules/order'
import { productApi } from '@/api/modules/product'
import AftersaleApplyDialog from './AftersaleApplyDialog.vue'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const route = useRoute()
const router = useRouter()

// 路由参数 :id 实际承载的是后端 orderNo
const orderNo = computed(() => String(route.params.id || ''))

const order = ref<ApiOrder | null>(null)
const loading = ref(false)
const errorMsg = ref('')

// 后端 status -> UI 展示文案与 type（与订单列表保持一致）
const STATUS_MAP: Record<string, { label: string; type: string }> = {
  pending_pay: { label: '待付款', type: 'pending' },
  paid: { label: '已付款 / 待发货', type: 'shipped' },
  shipping: { label: '已发货 / 待收货', type: 'receiving' },
  done: { label: '待评价', type: 'review' },
  cancelled: { label: '已取消', type: 'cancelled' },
  refunding: { label: '退款中', type: 'refunding' },
  refunded: { label: '已退款', type: 'refunded' },
}

const statusInfo = computed(() => {
  const s = order.value?.status || ''
  return STATUS_MAP[s] || { label: s, type: 'cancelled' }
})

const productTotal = computed(() => {
  const items = (order.value?.items || []) as any[]
  return items.reduce((sum, it) => sum + (it.price || 0) * (it.quantity || 0), 0)
})

const freight = computed(() => Number((order.value as any)?.freightAmount ?? 0))
const payAmount = computed(() => Number((order.value as any)?.payAmount ?? productTotal.value))
const donateAmount = computed(() => Number((order.value as any)?.donateAmount ?? 0))

// 收货地址快照：后端返回 JSON 字符串 {"receiver","phone","address"}
const receiver = computed(() => {
  const raw = (order.value as any)?.receiverSnapshot
  if (!raw) return null
  try {
    const o = typeof raw === 'string' ? JSON.parse(raw) : raw
    return { name: o.receiver, phone: o.phone, address: o.address }
  } catch {
    return null
  }
})

function formatDate(s?: string) {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 19)
}

async function loadDetail() {
  if (!orderNo.value) {
    errorMsg.value = '订单号缺失'
    return
  }
  loading.value = true
  errorMsg.value = ''
  try {
    const data = await orderApi.orderDetail(orderNo.value)
    order.value = data as ApiOrder
  } catch (e: any) {
    errorMsg.value = e?.message || '订单加载失败'
    order.value = null
  } finally {
    loading.value = false
  }
}

let pollTimer: number | null = null
async function pollPayStatus() {
  if (!orderNo.value) return
  try {
    const s: any = await orderApi.orderPayStatus(orderNo.value)
    if (s.paid) {
      await loadDetail()
      stopPoll()
    }
  } catch {
    // 忽略单次异常，继续轮询
  }
}
function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

onMounted(async () => {
  await loadDetail()
  // 沙箱异步通知不可达：若仍为待付款，主动轮询支付宝支付结果并刷新为已付
  if (order.value?.status === 'pending_pay') {
    pollTimer = window.setInterval(pollPayStatus, 3000)
  }
  // 加载已评价商品列表（用于"立即评价"按钮文案判断）
  await loadReviewed()
  if (route.query.review === '1' && order.value?.status === 'done' && !allReviewed.value) {
    openReview()
  }
})

onUnmounted(stopPoll)

function goBack() {
  router.back()
}

function goPay() {
  router.push(`/pay/${orderNo.value}`)
}

async function cancelOrder() {
  try {
    await ElMessageBox.confirm('确定取消该订单？', '提示', {
      confirmButtonText: '取消订单',
      cancelButtonText: '不取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await orderApi.cancelOrder(orderNo.value)
    ElMessage.success('订单已取消')
    loadDetail()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消失败')
  }
}

// ============ 确认收货 ============
async function confirmReceive() {
  try {
    await ElMessageBox.confirm('确认已收到商品？', '确认收货', {
      confirmButtonText: '确认收货',
      cancelButtonText: '再想想',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await orderApi.receiveOrder(orderNo.value)
    ElMessage.success('已确认收货，期待你的评价~')
    loadDetail()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ============ 申请售后 ============
// 后端 buildOrderDetail 会挂载最近一张售后单，前端据此在「申请售后」与
// 「查看售后进度」之间切换，避免用户重复提交或提交后无处可查
const aftersale = computed(() => (order.value as any)?.aftersale || null)
const canApplyAftersale = computed(() => Boolean((order.value as any)?.canApplyAftersale))
const applyDialogVisible = ref(false)

function openApplyDialog() {
  applyDialogVisible.value = true
}

function goAftersaleDetail() {
  if (aftersale.value?.id) router.push(`/aftersale/${aftersale.value.id}`)
}

function onApplySuccess() {
  loadDetail()
}

// ============ 物流轨迹 ============
const logisticsVisible = ref(false)
const logisticsLoading = ref(false)
const logisticsSteps = ref<
  Array<{ title: string; time: string; done: boolean; desc: string }>
>([])

async function showLogistics() {
  logisticsVisible.value = true
  logisticsLoading.value = true
  logisticsSteps.value = []
  try {
    const steps = await orderApi.logistics(orderNo.value)
    logisticsSteps.value = steps || []
  } catch (e: any) {
    ElMessage.error(e?.message || '物流信息加载失败')
  } finally {
    logisticsLoading.value = false
  }
}

// ============ 评价（多商品独立） ============
const reviewVisible = ref(false)
const reviewList = ref<Array<{
  productId: number
  productName: string
  productImage: string
  rating: number
  content: string
  images?: string | null
  createdAt?: string
  done: boolean
}>>([])
const reviewSubmitting = ref(false)
const reviewedProductIds = ref<Set<number>>(new Set())
const reviewMap = ref<Record<number, { rating: number; content: string; images?: string | null; createdAt?: string }>>({})
const allReviewed = computed(() => {
  const items = order.value?.items || []
  if (!items.length) return false
  return items.every((it: any) => reviewedProductIds.value.has(it.productId))
})

async function loadReviewed() {
  if (!orderNo.value || order.value?.status !== 'done') return
  try {
    const res = await productApi.reviewedProducts(orderNo.value)
    reviewedProductIds.value = new Set(res.reviewedProductIds || [])
    const map: Record<number, { rating: number; content: string; images?: string | null; createdAt?: string }> = {}
    for (const r of res.reviews || []) {
      map[r.productId] = {
        rating: r.rating,
        content: r.content,
        images: r.images,
        createdAt: r.createdAt,
      }
    }
    reviewMap.value = map
  } catch {
    // 忽略，不影响页面
  }
}

async function openReview() {
  if (!order.value?.items?.length) {
    ElMessage.warning('订单暂无可评价商品')
    return
  }
  // 确保已评价列表是最新的
  await loadReviewed()
  reviewList.value = order.value.items.map((it) => {
    const saved = reviewMap.value[it.productId]
    return {
      productId: it.productId,
      productName: it.productName,
      productImage: it.productImage,
      rating: saved?.rating ?? 5,
      content: saved?.content ?? '',
      images: saved?.images,
      createdAt: saved?.createdAt,
      done: reviewedProductIds.value.has(it.productId),
    }
  })
  reviewVisible.value = true
}

async function submitAllReviews() {
  const pending = reviewList.value.filter((r) => r.content.trim() && !r.done)
  if (pending.length === 0) return ElMessage.warning('至少填写一条评价')
  reviewSubmitting.value = true
  try {
    let success = 0
    let fail = 0
    for (const r of reviewList.value) {
      if (!r.content.trim() || r.done) continue
      try {
        await productApi.addReview(r.productId, {
          orderNo: orderNo.value,
          rating: r.rating,
          content: r.content.trim(),
        })
        r.done = true
        reviewedProductIds.value.add(r.productId)
        reviewMap.value[r.productId] = {
          rating: r.rating,
          content: r.content.trim(),
          images: r.images,
        }
        success++
      } catch (e: any) {
        fail++
        // 后端返回"已评价过"时标记为 done
        if (e?.message?.includes('已评价过')) {
          r.done = true
          reviewedProductIds.value.add(r.productId)
        }
      }
    }
    if (success) ElMessage.success(`已成功提交 ${success} 条评价${fail ? `，${fail} 条失败` : ''}`)
    else if (fail) ElMessage.error('评价提交失败')
    if (success && !fail) {
      setTimeout(() => { reviewVisible.value = false }, 800)
    }
  } finally {
    reviewSubmitting.value = false
  }
}

// 售后按钮：能申请就给申请，已有单就给进度入口，两者互斥
type ActionBtn = { label: string; type: 'outline' | 'primary'; action: () => void }
function aftersaleBtn(): ActionBtn[] {
  if (canApplyAftersale.value) {
    return [{ label: '申请售后', type: 'outline', action: openApplyDialog }]
  }
  if (aftersale.value) {
    return [{ label: '查看售后进度', type: 'outline', action: goAftersaleDetail }]
  }
  return []
}

// 根据状态动态计算底部按钮
function actionsFor(): ActionBtn[] {
  switch (order.value?.status) {
    case 'pending_pay':
      return [
        { label: '取消订单', type: 'outline', action: cancelOrder },
        { label: '立即付款', type: 'primary', action: goPay },
      ]
    case 'paid':
      return [
        ...aftersaleBtn(),
        { label: '查看物流', type: 'outline', action: showLogistics },
      ]
    case 'shipping':
      return [
        { label: '查看物流', type: 'outline', action: showLogistics },
        ...aftersaleBtn(),
        { label: '确认收货', type: 'primary', action: confirmReceive },
      ]
    case 'done':
      return [
        { label: '再次购买', type: 'outline', action: () => router.push('/') },
        ...aftersaleBtn(),
        ...(allReviewed.value
          ? [{ label: '已评价', type: 'outline' as const, action: () => ElMessage.info('该订单商品已全部评价') }]
          : [{ label: '立即评价', type: 'primary' as const, action: openReview }]),
      ]
    // 退款中 / 已退款：过去这里是空操作，用户点进来完全看不到进度
    case 'refunding':
    case 'refunded':
      return aftersale.value
        ? [{ label: '查看售后进度', type: 'primary', action: goAftersaleDetail }]
        : [{ label: '返回订单列表', type: 'outline', action: () => router.push('/order/list') }]
    default:
      return [{ label: '返回订单列表', type: 'outline', action: () => router.push('/order/list') }]
  }
}
</script>

<template>
  <div class="order-detail">
    <div class="detail-container">
      <nav class="topbar">
        <button class="back-btn" @click="goBack">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <span class="topbar-title">订单详情</span>
      </nav>

      <!-- 加载 -->
      <div v-if="loading" class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <span>加载中…</span>
      </div>

      <!-- 错误 -->
      <PetEmptyState v-else-if="errorMsg" icon="error_outline" :title="errorMsg">
        <template #action>
          <button class="retry-btn" @click="loadDetail">重新加载</button>
        </template>
      </PetEmptyState>

      <template v-else-if="order">
        <!-- 状态横幅 -->
        <div class="status-banner" :class="`status-${statusInfo.type}`">
          <span class="material-symbols-outlined banner-icon">
            {{ statusInfo.type === 'pending' ? 'payments' : statusInfo.type === 'cancelled' ? 'cancel' : (statusInfo.type === 'refunding' || statusInfo.type === 'refunded') ? 'replay' : 'local_shipping' }}
          </span>
          <div class="banner-text">
            <div class="banner-title">{{ statusInfo.label }}</div>
            <div class="banner-sub">
              订单号：{{ order.orderNo }}
              <span v-if="statusInfo.type === 'refunding'" class="banner-hint">退款审核中，预计 1-3 个工作日</span>
              <span v-if="statusInfo.type === 'refunded'" class="banner-hint">退款已原路返回至付款账户</span>
            </div>
          </div>
        </div>

        <!-- 售后进度快捷卡片：有售后单时常驻，一眼看到进展 -->
        <div v-if="aftersale" class="aftersale-card pet-card" @click="goAftersaleDetail">
          <span class="material-symbols-outlined as-icon">support_agent</span>
          <div class="as-body">
            <div class="as-title">
              {{ aftersale.typeText }}
              <em class="as-status" :class="{ done: aftersale.isFinal }">{{ aftersale.statusText }}</em>
            </div>
            <div class="as-sub">
              <template v-if="aftersale.handleNote">商家回复：{{ aftersale.handleNote }}</template>
              <template v-else-if="aftersale.reason">申请原因：{{ aftersale.reason }}</template>
              <template v-else>点击查看完整售后进度</template>
            </div>
          </div>
          <span class="material-symbols-outlined as-arrow">chevron_right</span>
        </div>

        <!-- 商品清单 -->
        <PageSection class="card" title="商品清单" icon="shopping_bag">
          <div class="item-list">
            <div v-for="it in order.items" :key="it.id" class="item">
              <div class="item-cover">
                <img :src="it.productImage" :alt="it.productName" />
              </div>
              <div class="item-body">
                <h4 class="item-name">{{ it.productName }}</h4>
                <p class="item-qty">数量: x{{ it.quantity }}</p>
                <div v-if="reviewMap[it.productId]" class="item-review">
                  <div class="ir-head">
                    <span class="ir-tag">已评价</span>
                    <el-rate :model-value="reviewMap[it.productId].rating" :max="5" disabled />
                  </div>
                  <p class="ir-content">{{ reviewMap[it.productId].content }}</p>
                  <span v-if="reviewMap[it.productId].createdAt" class="ir-time">
                    评价于 {{ formatDate(reviewMap[it.productId].createdAt) }}
                  </span>
                </div>
              </div>
              <div class="item-price">
                <span class="yen">¥</span>
                <span class="amount">{{ Number(it.price).toFixed(2) }}</span>
              </div>
            </div>
          </div>
        </PageSection>

        <!-- 收货信息 -->
        <PageSection class="card" title="收货信息" icon="location_on">
          <div v-if="receiver" class="addr-body">
            <div class="addr-line">
              <span class="addr-name">{{ receiver.name }}</span>
              <span class="addr-phone">{{ receiver.phone }}</span>
            </div>
            <div class="addr-detail">{{ receiver.address }}</div>
          </div>
          <div v-else class="addr-empty">暂无收货信息</div>
        </PageSection>

        <!-- 费用概览 -->
        <PageSection class="card" title="费用明细" icon="receipt_long">
          <div class="sum-row">
            <span>商品金额 ({{ order.items?.length || 0 }}件)</span>
            <span class="sum-val">¥{{ productTotal.toFixed(2) }}</span>
          </div>
          <div class="sum-row">
            <span>运费</span>
            <span :class="freight === 0 ? 'sum-free' : 'sum-val'">
              {{ freight === 0 ? '免运费' : `¥${freight.toFixed(2)}` }}
            </span>
          </div>
          <div v-if="donateAmount > 0" class="sum-row">
            <span>爱心捐赠</span>
            <span class="sum-val">¥{{ donateAmount.toFixed(2) }}</span>
          </div>
          <div class="sum-total">
            <div class="total-label">实付总额</div>
            <div class="total-val">¥{{ payAmount.toFixed(2) }}</div>
          </div>
        </PageSection>

        <p class="created-at">下单时间：{{ formatDate((order as any)?.createdAt) }}</p>
      </template>
    </div>

    <!-- 底部操作条 -->
    <div v-if="order && actionsFor().length" class="action-bar">
      <button
        v-for="a in actionsFor()"
        :key="a.label"
        class="action-btn"
        :class="a.type"
        @click="a.action"
      >
        {{ a.label }}
      </button>
    </div>

    <!-- 物流轨迹 -->
    <el-dialog v-model="logisticsVisible" title="物流轨迹" width="420px" align-center>
      <div v-if="logisticsLoading" class="logi-loading">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <span>加载中…</span>
      </div>
      <div v-else-if="logisticsSteps.length === 0" class="logi-empty">暂无物流信息</div>
      <ul v-else class="logi-timeline">
        <li
          v-for="(s, i) in logisticsSteps"
          :key="i"
          class="logi-item"
          :class="{ done: s.done }"
        >
          <span class="logi-dot"></span>
          <div class="logi-body">
            <div class="logi-title">{{ s.title }}</div>
            <div class="logi-desc">{{ s.desc }}</div>
            <div class="logi-time">{{ s.time || '—' }}</div>
          </div>
        </li>
      </ul>
    </el-dialog>

    <!-- 评价弹窗（多商品独立评价） -->
    <el-dialog v-model="reviewVisible" title="发表评价" width="560px" align-center>
      <div v-if="reviewList.length" class="review-box">
        <div v-for="r in reviewList" :key="r.productId" class="review-row" :class="{ done: r.done }">
          <div class="review-product">
            <img :src="r.productImage" :alt="r.productName" />
            <span class="review-pname">{{ r.productName }}</span>
            <span v-if="r.done" class="review-tag">已评价</span>
          </div>
          <div class="review-rating">
            <span class="rr-label">评分</span>
            <el-rate v-model="r.rating" :max="5" :disabled="r.done" />
          </div>
          <div v-if="r.done" class="review-my-content">
            <p>{{ r.content }}</p>
            <span v-if="r.createdAt" class="review-time">{{ formatDate(r.createdAt) }}</span>
          </div>
          <textarea
            v-else
            v-model="r.content"
            class="review-textarea"
            rows="2"
            maxlength="200"
            placeholder="说说这件商品的使用感受吧~"
          ></textarea>
        </div>
      </div>
      <template #footer>
        <button class="review-cancel" @click="reviewVisible = false">取消</button>
        <button class="review-submit" :disabled="reviewSubmitting" @click="submitAllReviews">
          {{ reviewSubmitting ? '提交中…' : '提交全部' }}
        </button>
      </template>
    </el-dialog>

    <!-- 售后申请弹窗：类型 + 原因 + 凭证图 -->
    <AftersaleApplyDialog
      v-model="applyDialogVisible"
      :order-no="orderNo"
      :order-status="order?.status || ''"
      :pay-amount="payAmount"
      @success="onApplySuccess"
    />
  </div>
</template>

<style lang="scss" scoped>
.order-detail {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: 96px;
}

.detail-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
}

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

// ============ 状态块 ============
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-2xl $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;

  .material-symbols-outlined {
    font-size: 48px;
    opacity: 0.7;
  }

  &.error {
    color: $color-emergency;
  }

  .spin {
    font-size: 32px;
    color: $color-brand;
    animation: spin 1s linear infinite;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}

.retry-btn {
  padding: $spacing-xs $spacing-lg;
  background: $color-brand;
  color: $color-on-brand;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
}

// ============ 状态横幅 ============
// 售后进度快捷卡片
.aftersale-card {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 14px;
  border: 1px solid $color-error-container;
  background: rgba(255, 218, 214, 0.35);
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-emergency;
    transform: translateY(-1px);
    box-shadow: 0 4px 14px rgba(255, 77, 79, 0.14);
  }
}

.as-icon {
  font-size: 26px;
  color: $color-danger;
  flex-shrink: 0;
}

.as-body {
  flex: 1;
  min-width: 0;
}

.as-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-danger;
  margin-bottom: 3px;
}

.as-status {
  font-style: normal;
  font-size: $font-size-label-sm;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 999px;
  background: $color-emergency;
  color: #fff;

  &.done {
    background: $color-on-surface-variant;
  }
}

.as-sub {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.as-arrow {
  color: $color-on-surface-variant;
  flex-shrink: 0;
}

.status-banner {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  border-radius: $radius-md;
  color: #fff;
  margin-bottom: $spacing-md;
  box-shadow: $shadow-card;

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

  .banner-title {
    font-family: $font-family-headline;
    font-size: $font-size-body-lg;
    font-weight: 700;
  }

  .banner-sub {
    font-size: $font-size-label-md;
    opacity: 0.92;
  }

  .banner-hint {
    display: block;
    margin-top: 2px;
    font-size: $font-size-label-sm;
    opacity: 0.85;
  }

  &.status-pending {
    background: $color-emergency;
  }
  &.status-shipped {
    background: $color-secondary;
  }
  &.status-receiving {
    background: $color-tertiary;
  }
  &.status-review {
    background: $color-warning;
  }
  &.status-cancelled,
  &.status-refunding {
    background: $color-surface-container-high;
    color: $color-on-surface-variant;
  }

  &.status-refunded {
    background: $color-success;
  }
}

// ============ 卡片通用 ============
.card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  border: 1px solid rgba(223, 192, 179, 0.1);
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
  @include text-ellipsis(2);
}

.item-qty {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.item-review {
  margin-top: $spacing-sm;
  padding: $spacing-sm;
  background: $color-surface-container-low;
  border-radius: $radius-default;

  .ir-head {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: 4px;
  }

  .ir-tag {
    padding: 1px $spacing-sm;
    background: rgba(82, 196, 26, 0.15);
    color: $color-success;
    border-radius: $radius-full;
    font-size: $font-size-label-sm;
    font-weight: 700;
  }

  .ir-content {
    margin: 0;
    font-size: $font-size-body-md;
    color: $color-on-surface;
    line-height: $line-height-normal;
    word-break: break-word;
  }

  .ir-time {
    display: block;
    margin-top: 4px;
    font-size: $font-size-label-sm;
    color: $color-text-tertiary;
  }
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

// ============ 收货信息 ============
.addr-body {
  padding: $spacing-md;
  background: linear-gradient(135deg, rgba(255, 122, 50, 0.04), rgba(0, 96, 172, 0.04));
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

.addr-detail {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: $line-height-normal;
}

.addr-empty {
  padding: $spacing-md;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  text-align: center;
}

// ============ 费用明细 ============
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
}

.sum-total {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-top: $spacing-md;
  margin-top: $spacing-sm;
  border-top: 1px dashed $color-outline-variant;

  .total-label {
    font-family: $font-family-headline;
    font-size: $font-size-body-lg;
    font-weight: 600;
    color: $color-on-surface;
  }

  .total-val {
    font-family: $font-family-headline;
    font-size: 32px;
    font-weight: 700;
    color: $color-emergency;
    line-height: 1;
  }
}

.created-at {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  text-align: center;
  margin: $spacing-md 0 0;
}

// ============ 底部操作条 ============
.action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: $z-index-modal - 1;
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  padding: $spacing-md $width-gutter-mobile;
  background: $color-surface-container-lowest;
  border-top: 1px solid $color-outline-variant;
  box-shadow: 0 -4px 16px rgba(22, 29, 31, 0.06);

  @include desktop {
    padding: $spacing-md $width-margin-desktop;
  }
}

.action-btn {
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: all $transition-fast;

  &.outline {
    background: transparent;
    border: 1px solid $color-outline-variant;
    color: $color-on-surface;

    &:hover {
      border-color: $color-brand;
      color: $color-brand;
    }
  }

  &.primary {
    background: $color-brand;
    border: 1px solid $color-brand;
    color: #ffffff;

    &:hover {
      background: $color-brand-dark;
    }
  }
}

// ============ 物流时间线 ============
.logi-loading,
.logi-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-lg;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;

  .spin {
    color: $color-brand;
    animation: spin 1s linear infinite;
  }
}

.logi-timeline {
  list-style: none;
  margin: 0;
  padding: 0 0 0 $spacing-md;
}

.logi-item {
  position: relative;
  padding: 0 0 $spacing-lg 20px;
  border-left: 2px solid $color-outline-variant;

  &:last-child {
    border-left-color: transparent;
    padding-bottom: 0;
  }

  &.done {
    .logi-dot {
      background: $color-success;
      border-color: $color-success;
    }
  }
}

.logi-dot {
  position: absolute;
  left: -7px;
  top: 2px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: $color-outline-variant;
  border: 2px solid #fff;
  box-shadow: 0 0 0 2px $color-surface-container-lowest;
}

.logi-title {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
}

.logi-desc {
  margin-top: 2px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.logi-time {
  margin-top: 2px;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}

// ============ 评价弹窗 ============
.review-box {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.review-product {
  display: flex;
  align-items: center;
  gap: $spacing-sm;

  img {
    width: 56px;
    height: 56px;
    border-radius: $radius-default;
    object-fit: cover;
    background: $color-surface-container;
  }

  .review-pname {
    font-size: $font-size-body-md;
    font-weight: 600;
    color: $color-on-surface;
  }
}

.review-box {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  max-height: 60vh;
  overflow-y: auto;
}

.review-row {
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;

  &.done { opacity: 0.65; background: $color-surface-container; }
}

.review-product {
  display: flex;
  align-items: center;
  gap: $spacing-sm;

  img {
    width: 48px;
    height: 48px;
    border-radius: $radius-default;
    object-fit: cover;
  }
}

.review-pname {
  font-weight: 700;
  color: $color-on-surface;
  flex: 1;
  @include text-ellipsis(1);
}

.review-tag {
  padding: 2px $spacing-sm;
  background: rgba(82, 196, 26, 0.15);
  color: $color-success;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
}

.review-rating {
  display: flex;
  align-items: center;
  gap: $spacing-md;

  .rr-label {
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;
  }
}

.review-textarea {
  width: 100%;
  padding: $spacing-sm;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  font-family: $font-family-body;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  resize: vertical;
  outline: none;

  &:focus {
    border-color: $color-brand;
  }
}

.review-my-content {
  padding: $spacing-sm;
  background: $color-surface-container-low;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  line-height: $line-height-normal;

  p {
    margin: 0 0 $spacing-xs 0;
  }

  .review-time {
    font-size: $font-size-label-sm;
    color: $color-text-tertiary;
  }
}

.review-cancel,
.review-submit {
  padding: $spacing-xs $spacing-lg;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
}

.review-cancel {
  background: transparent;
  border: 1px solid $color-outline-variant;
  color: $color-on-surface;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

.review-submit {
  background: $color-brand;
  border: 1px solid $color-brand;
  color: #ffffff;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

// ============ 手机端响应式重排 ============
// 尺寸类 px(缩略图/返回按钮/状态块/评价区/按钮)被 px-to-viewport 转 vw，手机(375)下缩成约 1/5：
// 商品缩略图 80px→16px、返回按钮 36px→7px、各 padding 拥挤。用 rem(不缩放)重写。
@include mobile {
  .detail-container {
    padding-top: 1rem;
    padding-bottom: 2rem;
  }
  .topbar {
    gap: 0.5rem;
    margin-bottom: 1rem;
  }
  .back-btn {
    width: 2.25rem;
    height: 2.25rem;
  }
  .state-block {
    gap: 0.5rem;
    padding: 1.5rem 1rem;
  }
  .item {
    gap: 0.75rem;
  }
  .item-cover {
    width: 4.5rem;
    height: 4.5rem;
  }
  .item-review {
    padding: 0.6rem;

    .ir-head {
      gap: 0.4rem;
      margin-bottom: 0.3rem;
    }
    .ir-time {
      margin-top: 0.3rem;
    }
  }
  .addr-body {
    padding: 0.85rem;
  }
  .review-cancel,
  .review-submit {
    padding: 0.6rem 1rem;
  }
  .logi-item {
    padding: 0 0 1rem 1.1rem;
  }
  .logi-dot {
    left: -0.4rem;
    width: 0.7rem;
    height: 0.7rem;
  }
}
</style>
