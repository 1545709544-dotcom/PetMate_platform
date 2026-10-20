<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { orderApi } from '@/api/modules/order'
import type { Order as ApiOrder } from '@/api/modules/order'
import AftersaleApplyDialog from './AftersaleApplyDialog.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()
const route = useRoute()
const activeTab = ref('all')

const tabs = [
  { id: 'all', label: '全部', status: '' as string | undefined, statuses: undefined as string[] | undefined },
  { id: 'pending', label: '待付款', status: 'pending_pay' as string | undefined, statuses: undefined },
  { id: 'shipped', label: '待发货', status: 'paid' as string | undefined, statuses: undefined },
  { id: 'receiving', label: '待收货', status: 'shipping' as string | undefined, statuses: undefined },
  { id: 'review', label: '待评价', status: 'done' as string | undefined, statuses: undefined },
  // 已完成：订单已结案（含已评价的 done + 售后结案的 refunded），集成售后入口
  { id: 'finished', label: '已完成', status: undefined, statuses: ['done', 'refunded'] as string[] },
]

const orders = ref<ApiOrder[]>([])
const loading = ref(false)

// 后端 status -> UI 展示文案与 type
const STATUS_MAP: Record<string, { label: string; type: string }> = {
  pending_pay: { label: '待付款', type: 'pending' },
  paid: { label: '已付款/待发货', type: 'shipped' },
  shipping: { label: '已发货/待收货', type: 'receiving' },
  done: { label: '待评价', type: 'review' },
  cancelled: { label: '已取消', type: 'cancelled' },
  refunding: { label: '退款中', type: 'refunding' },
  refunded: { label: '已退款', type: 'refunding' },
}

interface UIOrder {
  id: string
  date: string
  status: string
  statusType: string
  items: Array<{ cover: string; name: string; qty: number }>
  total: number
  raw: ApiOrder
}

const uiOrders = computed<UIOrder[]>(() =>
  orders.value.map((o) => {
    // 已收货且全部评价完的 done 订单，在「已完成」tab 里应显示「已完成」而非「待评价」
    const reviewedDone = o.status === 'done' && o.reviewedAll
    return {
      id: o.orderNo,
      date: formatDate(o.createdAt),
      status: reviewedDone ? '已完成' : (STATUS_MAP[o.status]?.label || o.status),
      statusType: reviewedDone ? 'finished' : (STATUS_MAP[o.status]?.type || 'pending'),
      items: (o.items || []).map((it) => ({
        cover: it.productImage,
        name: it.productName,
        qty: it.quantity,
      })),
      total: (o as any).payAmount ?? o.totalAmount ?? 0,
      raw: o,
    }
  }),
)

function formatDate(s: string) {
  if (!s) return ''
  // 兼容 ISO 与已含空格的格式
  return s.replace('T', ' ').slice(0, 16)
}

// 订单内商品总件数（不同 SKU 的 quantity 累加）
function totalQty(o: UIOrder) {
  return (o.items || []).reduce((sum, it) => sum + (it.qty || 0), 0)
}

async function loadOrders() {
  loading.value = true
  try {
    const tab = tabs.find((t) => t.id === activeTab.value)
    const status = tab?.status
    const statuses = tab?.statuses
    const page = await orderApi.listOrders(status || undefined, 1, 20, statuses)
    let list = (page?.list || []) as ApiOrder[]
    // 待评价 tab：剔除已评价完的 done 订单（后端 reviewedAll 标记）
    if (tab?.id === 'review') {
      list = list.filter((o) => !o.reviewedAll)
    }
    orders.value = list
  } catch (e: any) {
    ElMessage.error(e?.message || '订单加载失败')
    orders.value = []
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 支付回跳：query.paid=1 表示刚从支付成功过来
  if (route.query.tab && tabs.some((t) => t.id === route.query.tab)) {
    activeTab.value = String(route.query.tab)
  }
  await loadOrders()
  if (route.query.paid === '1') {
    const on = String(route.query.orderNo || '')
    // 弹可点击的 toast,带"去查看订单"按钮直接跳详情; 订单号会从原 tab 列表里消失(已 paid)
    ElMessageBox.alert(
      on ? `订单 ${on} 已支付成功` : '支付成功',
      '支付完成',
      {
        type: 'success',
        confirmButtonText: '查看订单详情',
        cancelButtonText: '留在列表',
        showCancelButton: true,
        callback: (action: string) => {
          // 清除 query,避免刷新重复弹
          router.replace({ path: '/order/list', query: { tab: activeTab.value } })
          if (action === 'confirm' && on) {
            router.push('/order/detail/' + on)
          }
        },
      },
    )
  }
})

function switchTab(id: string) {
  activeTab.value = id
  loadOrders()
}

function goDetail(id: string) {
  router.push(`/order/detail/${id}`)
}

async function cancelOrder(o: UIOrder) {
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
    await orderApi.cancelOrder(o.id)
    ElMessage.success('订单已取消')
    loadOrders()
  } catch (e: any) {
    ElMessage.error(e?.message || '取消失败')
  }
}

function payNow(o: UIOrder) {
  router.push(`/pay/${o.id}`)
}

async function confirmReceive(o: UIOrder) {
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
    await orderApi.receiveOrder(o.id)
    ElMessage.success('已确认收货，期待你的评价~')
    loadOrders()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// 物流轨迹弹窗
const logisticsVisible = ref(false)
const logisticsLoading = ref(false)
const logisticsSteps = ref<
  Array<{ title: string; time: string; done: boolean; desc: string }>
>([])

async function showLogistics(o: UIOrder) {
  logisticsVisible.value = true
  logisticsLoading.value = true
  logisticsSteps.value = []
  try {
    const steps = await orderApi.logistics(o.id)
    logisticsSteps.value = steps || []
  } catch (e: any) {
    ElMessage.error(e?.message || '物流信息加载失败')
  } finally {
    logisticsLoading.value = false
  }
}

function act(action: string, o: UIOrder) {
  if (action === '立即付款') payNow(o)
  else if (action === '取消订单') cancelOrder(o)
  else if (action === '查看详情') goDetail(o.id)
  else if (action === '再次购买') router.push('/')
  else if (action === '确认收货') confirmReceive(o)
  else if (action === '查看物流') showLogistics(o)
  else if (action === '立即评价')
    router.push({ name: 'OrderDetail', params: { id: o.id }, query: { review: '1' } })
  else if (action === '申请售后') openApplyDialog(o)
  else if (action === '查看售后') goAftersaleDetail(o)
}

// 售后申请弹窗
const applyDialogVisible = ref(false)
const applyTarget = ref<UIOrder | null>(null)
function openApplyDialog(o: UIOrder) {
  applyTarget.value = o
  applyDialogVisible.value = true
}
function goAftersaleDetail(o: UIOrder) {
  const id = o.raw.aftersale?.id
  if (id) {
    router.push(`/aftersale/${id}`)
  } else {
    router.push('/aftersale')
  }
}
async function onApplySuccess() {
  applyDialogVisible.value = false
  ElMessage.success('售后申请已提交')
  await loadOrders()
}

// 根据状态动态计算按钮
function actionsFor(o: UIOrder) {
  const aftersaleBtn = () => {
    if (o.raw.canApplyAftersale) {
      return [{ label: '申请售后', type: 'outline' as const }]
    }
    if (o.raw.aftersale) {
      return [{ label: '查看售后', type: 'outline' as const }]
    }
    return []
  }
  switch (o.raw.status) {
    case 'pending_pay':
      return [
        { label: '取消订单', type: 'outline' as const },
        { label: '立即付款', type: 'primary' as const },
      ]
    case 'paid':
      return [
        ...aftersaleBtn(),
        { label: '查看详情', type: 'outline' as const },
      ]
    case 'shipping':
      return [
        { label: '查看物流', type: 'outline' as const },
        { label: '确认收货', type: 'primary' as const },
      ]
    case 'done':
      // 已完成 Tab：收货后主操作改为「申请售后」(已有售后单则「查看售后」)，不再引导评价
      if (activeTab.value === 'finished') {
        return [
          { label: '再次购买', type: 'outline' as const },
          ...aftersaleBtn(),
        ]
      }
      // 待评价 Tab：引导用户评价
      return [
        { label: '再次购买', type: 'outline' as const },
        { label: '立即评价', type: 'primary' as const },
      ]
    case 'refunded':
      // 已完成（已退款）tab 下的订单：售后结案，展示售后进度入口
      return [
        { label: '再次购买', type: 'outline' as const },
        { label: '查看售后', type: 'primary' as const },
      ]
    default:
      return [{ label: '查看详情', type: 'outline' as const }]
  }
}
</script>

<template>
  <div class="orders-view">
    <div class="orders-container">
      <nav class="breadcrumb">
        <span>个人中心</span>
        <span class="sep">›</span>
        <span class="current">我的订单</span>
      </nav>

      <h1 class="page-title">我的订单</h1>

      <!-- 状态 Tabs -->
      <div class="tabs">
        <button
          v-for="t in tabs"
          :key="t.id"
          class="tab"
          :class="{ active: activeTab === t.id }"
          @click="switchTab(t.id)"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- 订单列表 -->
      <div v-if="loading" class="order-loading">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <span>加载中…</span>
      </div>

      <PetEmptyState v-else-if="uiOrders.length === 0" icon="inbox" title="暂无相关订单" />

      <div v-else class="order-list">
        <div v-for="o in uiOrders" :key="o.id" class="order-card pet-card">
          <div class="order-head">
            <span class="order-no">订单号: {{ o.id }}</span>
            <span class="order-sep">|</span>
            <span class="order-date">{{ o.date }}</span>
            <!-- 售后状态徽章：订单挂有售后单时直接显示，避免再点详情看 -->
            <span
              v-if="o.raw.aftersale"
              class="aftersale-badge"
              :class="{ done: o.raw.aftersale.isFinal }"
              @click="goAftersaleDetail(o)"
            >
              <span class="material-symbols-outlined badge-ico">assignment_return</span>
              <span class="badge-type">{{ o.raw.aftersale.typeText }}</span>
              <span class="badge-sep">·</span>
              <span class="badge-status">{{ o.raw.aftersale.statusText }}</span>
            </span>
            <span class="order-status" :class="`status-${o.statusType}`">{{ o.status }}</span>
          </div>

          <div class="order-body">
            <div class="order-products" @click="goDetail(o.id)">
              <div v-for="(it, i) in o.items.slice(0, 3)" :key="i" class="prod">
                <img :src="it.cover" :alt="it.name" />
              </div>
              <div v-if="o.items.length" class="prod-meta">
                <div class="prod-names">{{ o.items[0].name }}<template v-if="o.items.length > 1"> 等 {{ o.items.length }} 件</template></div>
                <div class="prod-qty">共 {{ totalQty(o) }} 件</div>
              </div>
            </div>

            <div class="order-amount">
              <span class="amount-label">实付金额</span>
              <span class="amount-val">¥{{ o.total.toFixed(2) }}</span>
            </div>

            <div class="order-actions">
              <button
                v-for="a in actionsFor(o)"
                :key="a.label"
                class="action-btn"
                :class="a.type"
                @click="act(a.label, o)"
              >
                {{ a.label }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- 物流轨迹弹窗 -->
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

      <!-- 售后申请弹窗（列表中点击「申请售后」时复用） -->
      <AftersaleApplyDialog
        v-if="applyTarget"
        v-model="applyDialogVisible"
        :order-no="applyTarget.id"
        :order-status="applyTarget.raw.status"
        :pay-amount="applyTarget.total"
        @success="onApplySuccess"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.orders-view {
  min-height: 100vh;
  background: $color-background;
}

.orders-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-md;

  .sep {
    color: $color-text-tertiary;
  }

  .current {
    color: $color-brand;
    font-weight: 600;
  }
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-lg;
}

// ============ Tabs ============
.tabs {
  display: flex;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  margin-bottom: $spacing-lg;
  overflow: hidden;
  box-shadow: $shadow-sm;
}

.tab {
  flex: 1;
  padding: $spacing-md 0;
  text-align: center;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;
  position: relative;

  &:hover {
    color: $color-brand;
  }

  &.active {
    color: $color-brand;
    font-weight: 700;

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 25%;
      right: 25%;
      height: 3px;
      background: $color-brand;
      border-radius: $radius-full;
    }
  }
}

// ============ 订单卡 ============
.order-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.order-loading,
.order-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-2xl $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  gap: $spacing-sm;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;

  .empty-icon {
    font-size: 56px;
    opacity: 0.6;
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

.order-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  overflow: hidden;
  box-shadow: $shadow-sm;
}

.order-head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-lg;
  background: $color-surface-container;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  flex-wrap: wrap;
}

.order-no {
  font-weight: 600;
}

.order-sep {
  color: $color-text-tertiary;
}

// ============ 售后状态徽章 ============
.aftersale-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px $spacing-sm;
  margin-left: $spacing-sm;
  border-radius: $radius-full;
  background: $color-error-container;
  color: $color-danger;
  font-size: $font-size-label-sm;
  font-weight: 600;
  cursor: pointer;
  transition: filter $transition-fast;
  white-space: nowrap;

  &:hover { filter: brightness(0.95); }

  &.done {
    background: $color-surface-container;
    color: $color-on-surface-variant;
  }

  .badge-ico {
    font-size: 14px;
    font-variation-settings: 'FILL' 1;
  }

  .badge-sep { opacity: 0.5; }
}

.order-status {
  margin-left: auto;
  font-weight: 700;

  &.status-pending {
    color: $color-emergency;
  }
  &.status-shipped {
    color: $color-secondary;
  }
  &.status-review {
    color: $color-warning;
  }
  &.status-finished {
    color: $color-success;
  }
  &.status-receiving {
    color: $color-tertiary;
  }
  &.status-cancelled,
  &.status-refunding {
    color: $color-on-surface-variant;
  }
}

.order-body {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  padding: $spacing-lg;

  @include desktop {
    grid-template-columns: 2fr 1fr auto;
    align-items: center;
  }
}

.order-products {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  cursor: pointer;

  .prod {
    width: 80px;
    height: 80px;
    border-radius: $radius-default;
    overflow: hidden;
    background: $color-surface-container;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .prod-meta {
    font-size: $font-size-label-sm;
    color: $color-on-surface;

    .prod-names {
      color: $color-on-surface;
      font-weight: 600;
      @include text-ellipsis(1);
      max-width: 240px;
    }

    .prod-qty {
      margin-top: 4px;
      color: $color-on-surface-variant;
    }
  }
}

.order-amount {
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  @include desktop {
    align-items: flex-end;
  }

  .amount-label {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    margin-bottom: 4px;
  }

  .amount-val {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 700;
    color: $color-emergency;
  }
}

.order-actions {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;

  @include desktop {
    align-items: flex-end;
  }
}

.action-btn {
  padding: $spacing-xs $spacing-md;
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
      background: #ff9461;
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

// ============ 手机端响应式重排 ============
// 尺寸类 px(缩略图/卡片内边距/tab/操作按钮)被 px-to-viewport 转 vw，手机(375)下缩成约 1/5：
// 订单缩略图 80px→16px、各 padding 拥挤、操作按钮贴边难点。用 rem(不缩放)重写。
@include mobile {
  .orders-container {
    padding-top: 1rem;
    padding-bottom: 2rem;
  }
  .tabs {
    margin-bottom: 1rem;
  }
  .tab {
    padding: 0.7rem 0;
  }
  .order-list {
    gap: 0.75rem;
  }
  .order-head {
    gap: 0.4rem;
    padding: 0.6rem 1rem;
  }
  .order-body {
    gap: 0.75rem;
    padding: 1rem;
  }
  .order-products {
    gap: 0.5rem;

    .prod {
      width: 4.5rem;
      height: 4.5rem;
    }

    .prod-meta .prod-names {
      max-width: 100%;
    }
  }
  .order-actions {
    gap: 0.5rem;
    align-items: stretch;
  }
  .action-btn {
    padding: 0.6rem 1rem;
    text-align: center;
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