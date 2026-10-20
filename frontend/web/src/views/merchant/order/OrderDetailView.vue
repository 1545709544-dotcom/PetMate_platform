<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'

const route = useRoute()
const router = useRouter()
const orderNo = computed(() => String(route.params.id || ''))
const loading = ref(false)
const order = ref<any>(null)
const remarkText = ref('')
const savingRemark = ref(false)

const STATUS_MAP: Record<string, { label: string; cls: string }> = {
  pending_pay: { label: '待付款', cls: 'pending' },
  paid: { label: '已付款', cls: 'paid' },
  shipping: { label: '已发货', cls: 'shipping' },
  done: { label: '已完成', cls: 'done' },
  completed: { label: '已完成', cls: 'done' },
  cancelled: { label: '已取消', cls: 'cancelled' },
  refunding: { label: '退款中', cls: 'refunding' },
  refunded: { label: '已退款', cls: 'refunded' },
  review: { label: '售后中', cls: 'refunding' },
}

function parseReceiver() {
  const snap = order.value?.receiver_snapshot
  if (!snap) return null
  try { return typeof snap === 'string' ? JSON.parse(snap) : snap }
  catch { return null }
}

// 状态流转时间线
const timeline = computed(() => {
  const o = order.value
  if (!o) return { cancelled: false, refund: false, steps: [] as any[] }
  const s = (o.status || '').toLowerCase()
  if (s === 'cancelled') return { cancelled: true, refund: false, steps: [] as any[] }
  const refund = s === 'refunding' || s === 'refunded'
  const mapTo = (st: string): number => {
    switch (st) {
      case 'pending': case 'pending_pay': return 0
      case 'paid': case 'ship': return 1
      case 'shipping': return 2
      case 'verify': case 'receiving': return 3
      case 'done': case 'completed': case 'review': return 4
      default: return 0
    }
  }
  const idx = mapTo(s)
  const fmt = (t?: string) => (t ? String(t).replace('T', ' ').slice(0, 16) : '')
  if (refund) {
    return {
      cancelled: false,
      refund: true,
      steps: [
        { label: '提交订单', icon: 'receipt_long', time: fmt(o.created_at), state: 'done' },
        { label: '付款成功', icon: 'payments', time: fmt(o.paid_at), state: 'done' },
        {
          label: s === 'refunded' ? '已退款' : '退款处理中',
          icon: 'assignment_return',
          time: '',
          state: s === 'refunded' ? 'done' : 'active',
        },
      ],
    }
  }
  const raw = [
    { label: '提交订单', icon: 'receipt_long', time: o.created_at, needed: 0 },
    { label: '付款成功', icon: 'payments', time: o.paid_at, needed: 1 },
    { label: '商家发货', icon: 'local_shipping', time: o.delivered_at, needed: 2 },
    { label: '确认收货', icon: 'inventory_2', time: null, needed: 3 },
    { label: '订单完成', icon: 'check_circle', time: null, needed: 4 },
  ]
  const steps = raw.map((step) => ({
    label: step.label,
    icon: step.icon,
    time: fmt(step.time),
    state: step.needed < idx ? 'done' : step.needed === idx ? 'active' : 'todo',
  }))
  return { cancelled: false, refund: false, steps }
})

const discount = computed(() => {
  const o = order.value
  if (!o) return 0
  return Number(o.coupon_amount || 0) + Number(o.points_amount || 0)
})

async function load() {
  loading.value = true
  try {
    order.value = await merchantApi.orderDetail(orderNo.value)
    remarkText.value = order.value?.remark || ''
  } catch (e: any) {
    ElMessage.error(e?.message || '加载订单失败')
  } finally {
    loading.value = false
  }
}

async function ship() {
  try {
    await merchantApi.shipOrder(orderNo.value)
    ElMessage.success('已发货')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '发货失败')
  }
}

async function saveRemark() {
  savingRemark.value = true
  try {
    await merchantApi.updateOrderRemark(orderNo.value, remarkText.value.trim())
    ElMessage.success('备注已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存备注失败')
  } finally {
    savingRemark.value = false
  }
}

const handling = ref(false)
const refund = computed(() => ({
  id: order.value?.refund_id,
  reason: order.value?.refund_reason,
  status: order.value?.refund_status,
  type: order.value?.refund_type,
}))

async function approveRefund() {
  if (!refund.value.id) return
  const needReturn = refund.value.type === 'RETURN_REFUND' || refund.value.type === 'EXCHANGE'
  let returnAddress = ''
  if (needReturn) {
    try {
      const { value } = await ElMessageBox.prompt(
        '该售后需买家寄回商品，请填写退货地址（将发给买家）',
        '同意退款',
        {
          type: 'warning',
          confirmButtonText: '确认同意',
          cancelButtonText: '取消',
          inputType: 'textarea',
          inputValidator: (v: string) => (v && v.trim() ? true : '请填写退货地址'),
        },
      )
      returnAddress = value.trim()
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm('确认同意该退款申请？同意后订单关闭并恢复库存。', '同意退款', {
        type: 'warning', confirmButtonText: '确认同意', cancelButtonText: '取消',
      })
    } catch {
      return
    }
  }
  handling.value = true
  try {
    await merchantApi.approveRefund(Number(refund.value.id), { returnAddress: returnAddress || undefined })
    ElMessage.success('已同意退款')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    handling.value = false
  }
}

async function rejectRefund() {
  if (!refund.value.id) return
  let note = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写拒绝理由', '拒绝退款', {
      inputType: 'textarea', confirmButtonText: '确认拒绝', cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim() ? true : '请填写拒绝理由'),
    })
    note = value.trim()
  } catch { return }
  handling.value = true
  try {
    await merchantApi.rejectRefund(Number(refund.value.id), note)
    ElMessage.success('已拒绝退款')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    handling.value = false
  }
}

// t_refund 状态机 11 态，与后端 RefundStatus 对齐，避免列表里出现裸英文值
const refundStatusLabel: Record<string, string> = {
  pending: '待商家处理',
  approved: '已同意',
  rejected: '已拒绝',
  returning: '买家待寄回',
  returned: '买家已寄回',
  exchanging: '商家待换货',
  reshipped: '商家已重发',
  refunded: '已退款',
  exchanged: '已换货完成',
  appealing: '平台介入中',
  arbitrated: '已仲裁',
  closed: '已撤销',
}

onMounted(load)
</script>

<template>
  <div class="merchant-order-detail" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">订单详情</h1>
        <p class="page-sub mono">订单号: {{ orderNo }}</p>
      </div>
      <div class="head-actions">
        <button class="btn-outline" @click="router.back()">
          <span class="material-symbols-outlined">arrow_back</span>
          返回
        </button>
        <button v-if="order?.status === 'paid'" class="btn-primary" @click="ship">
          <span class="material-symbols-outlined">local_shipping</span>
          发货
        </button>
        <button
          v-if="order?.status === 'refunding' && refund.id"
          class="btn-primary"
          :disabled="handling"
          @click="approveRefund"
        >
          <span class="material-symbols-outlined">check_circle</span>
          同意退款
        </button>
        <button
          v-if="order?.status === 'refunding' && refund.id"
          class="btn-danger"
          :disabled="handling"
          @click="rejectRefund"
        >
          <span class="material-symbols-outlined">block</span>
          拒绝退款
        </button>
      </div>
    </div>

    <div v-if="order" class="detail-grid">
      <!-- 左列 -->
      <div class="col-main">
        <!-- 状态时间线 -->
        <section class="card timeline-card">
          <div v-if="timeline.cancelled" class="cancelled-banner">
            <span class="material-symbols-outlined">cancel</span>
            该订单已取消
          </div>
          <ul v-else class="timeline">
            <li
              v-for="(step, i) in timeline.steps"
              :key="i"
              class="tl-step"
              :class="step.state"
            >
              <span class="tl-icon">
                <span class="material-symbols-outlined">{{ step.icon }}</span>
              </span>
              <div class="tl-body">
                <span class="tl-label">{{ step.label }}</span>
                <span v-if="step.time" class="tl-time mono">{{ step.time }}</span>
              </div>
              <span v-if="step.state === 'active'" class="tl-pulse"></span>
            </li>
          </ul>
        </section>

        <!-- 商品 -->
        <section v-if="order?.items?.length" class="card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">shopping_bag</span>
            商品明细 <span class="count">共 {{ order.items.length }} 件</span>
          </h2>
          <ul class="items">
            <li v-for="it in order.items" :key="it.id" class="item">
              <span class="img-stub" v-if="!it.product_image">🛍️</span>
              <img v-else :src="it.product_image" :alt="it.product_name" loading="lazy" />
              <div class="item-info">
                <div class="item-name">{{ it.product_name }}</div>
                <div class="item-meta">SKU ID: {{ it.sku_id }}</div>
              </div>
              <div class="item-price">¥{{ Number(it.price).toFixed(2) }} × {{ it.quantity }}</div>
            </li>
          </ul>
        </section>

        <!-- 退款申请 -->
        <section v-if="order?.status === 'refunding' && refund.id" class="card refund-card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">assignment_return</span>
            退款申请
            <span class="refund-status" :class="refund.status">{{ refundStatusLabel[refund.status] || refund.status }}</span>
          </h2>
          <div class="refund-reason">
            <span class="kv-label">退款原因</span>
            <p>{{ refund.reason || '用户未填写原因' }}</p>
          </div>
          <p class="refund-hint">请尽快处理：同意后将关闭订单并恢复库存，拒绝需填写理由。</p>
        </section>

        <!-- 收货信息 -->
        <section v-if="parseReceiver()" class="card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">location_on</span>
            收货信息
          </h2>
          <div class="kv-grid">
            <div class="kv"><span class="kv-label">收件人</span><span>{{ parseReceiver().receiver }}</span></div>
            <div class="kv"><span class="kv-label">电话</span><span class="mono">{{ parseReceiver().phone }}</span></div>
            <div class="kv full"><span class="kv-label">地址</span><span>{{ parseReceiver().address }}</span></div>
          </div>
        </section>

        <!-- 备注 -->
        <section class="card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">sticky_note_2</span>
            订单备注（内部）
          </h2>
          <textarea v-model="remarkText" class="remark-input" rows="3" placeholder="添加内部备注，仅商家可见..." maxlength="500"></textarea>
          <button class="btn-primary btn-sm" :disabled="savingRemark" @click="saveRemark" style="margin-top: 8px;">
            {{ savingRemark ? '保存中...' : '保存备注' }}
          </button>
        </section>
      </div>

      <!-- 右列 -->
      <aside class="col-aside">
        <!-- 概要 -->
        <section class="card summary-card">
          <div class="summary-status">
            <span class="status-pill" :class="STATUS_MAP[order.status]?.cls">
              {{ STATUS_MAP[order.status]?.label || order.status }}
            </span>
          </div>
          <div class="kv-grid one-col">
            <div class="kv"><span class="kv-label">下单时间</span><span class="mono">{{ (order.created_at || '').replace('T', ' ').slice(0, 19) }}</span></div>
            <div class="kv"><span class="kv-label">买家</span><span>{{ order.buyer_name || '—' }} <em v-if="order.buyer_phone" class="buyer-phone">{{ order.buyer_phone }}</em></span></div>
            <div class="kv"><span class="kv-label">用户 ID</span><span class="mono">{{ order.user_id }}</span></div>
          </div>
        </section>

        <!-- 费用 -->
        <section class="card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">receipt_long</span>
            费用明细
          </h2>
          <div class="amount-list">
            <div class="amount-row"><span>商品总额</span><b>¥{{ Number(order.total_amount).toFixed(2) }}</b></div>
            <div class="amount-row" v-if="discount > 0"><span>优惠抵扣</span><b class="discount">-¥{{ discount.toFixed(2) }}</b></div>
            <div class="amount-row"><span>运费</span><b>¥{{ Number(order.freight_amount || 0).toFixed(2) }}</b></div>
            <div class="amount-row total"><span>实付金额</span><b>¥{{ Number(order.pay_amount).toFixed(2) }}</b></div>
          </div>
        </section>
      </aside>
    </div>

    <div v-if="order && !order.items?.length" class="empty">订单暂无明细</div>
  </div>
</template>

<style lang="scss" scoped>
.merchant-order-detail { display: flex; flex-direction: column; gap: $spacing-lg; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: $spacing-sm; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }
.mono { font-family: $font-family-mono; }

.head-actions { display: flex; gap: $spacing-sm; flex-wrap: wrap; }
.btn-primary, .btn-outline {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md; font-weight: 700; cursor: pointer; border: 1px solid transparent;
}
.btn-primary { background: $color-brand; color: #fff; }
.btn-outline { background: transparent; color: $color-brand; border-color: $color-brand; }
.btn-danger {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md; font-weight: 700; cursor: pointer; border: 1px solid $color-emergency;
  background: transparent; color: $color-emergency;
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

// ============ 两栏布局 ============
.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;
  @include desktop { grid-template-columns: 1fr 340px; align-items: start; }
}
.col-main, .col-aside { display: flex; flex-direction: column; gap: $spacing-lg; min-width: 0; }

// ============ 卡片通用 ============
.card { padding: $spacing-lg; background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; }
.card-title {
  font-family: $font-family-headline; font-size: $font-size-headline-md; font-weight: 600;
  color: $color-on-surface; margin-bottom: $spacing-md;
  display: flex; align-items: center; gap: $spacing-xs;
  .material-symbols-outlined { font-size: 20px; color: $color-brand; }
  .count { font-size: $font-size-label-md; font-weight: 400; color: $color-on-surface-variant; }
}

// ============ 时间线 ============
.timeline-card { padding: $spacing-xl $spacing-lg; }
.timeline { list-style: none; margin: 0; padding: 0; }
.tl-step {
  position: relative;
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding-bottom: $spacing-lg;
  &:last-child { padding-bottom: 0; }
  &::before {
    content: '';
    position: absolute;
    left: 17px;
    top: 36px;
    bottom: -4px;
    width: 2px;
    background: $color-outline-variant;
  }
  &:last-child::before { display: none; }

  .tl-icon {
    position: relative;
    z-index: 1;
    flex-shrink: 0;
    width: 36px; height: 36px;
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    background: $color-surface-container;
    color: $color-on-surface-variant;
    .material-symbols-outlined { font-size: 20px; }
  }
  .tl-body { display: flex; flex-direction: column; }
  .tl-label { font-size: $font-size-body-md; font-weight: 700; color: $color-on-surface; }
  .tl-time { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
  .tl-pulse {
    margin-left: auto;
    width: 8px; height: 8px; border-radius: 50%;
    background: $color-brand;
    box-shadow: 0 0 0 0 rgba($color-brand, 0.5);
    animation: pulse 1.6s infinite;
  }

  &.done {
    .tl-icon { background: rgba($color-success, 0.15); color: $color-success; }
    &::before { background: $color-success; }
  }
  &.active .tl-icon { background: rgba($color-brand, 0.15); color: $color-brand; }
  &.todo { opacity: 0.6; }
}
@keyframes pulse {
  0% { box-shadow: 0 0 0 0 rgba($color-brand, 0.5); }
  70% { box-shadow: 0 0 0 10px rgba($color-brand, 0); }
  100% { box-shadow: 0 0 0 0 rgba($color-brand, 0); }
}
.cancelled-banner {
  display: flex; align-items: center; gap: $spacing-sm;
  color: $color-on-surface-variant; font-weight: 700;
  .material-symbols-outlined { color: $color-emergency; }
}

// ============ 概要 / 费用 ============
.summary-card { .summary-status { margin-bottom: $spacing-md; } }
.kv-grid { display: grid; grid-template-columns: 1fr 1fr; gap: $spacing-md; }
.kv-grid.one-col { grid-template-columns: 1fr; }
.kv { display: flex; flex-direction: column; gap: 4px; min-width: 0; }
.kv.full { grid-column: 1 / -1; }
.kv-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; flex-shrink: 0; }
.kv span:not(.kv-label) { font-size: $font-size-body-md; color: $color-on-surface; word-break: break-all; }
.buyer-phone { font-style: normal; color: $color-on-surface-variant; margin-left: 4px; }

.status-pill { display: inline-flex; align-items: center; gap: 4px; padding: 4px $spacing-md; border-radius: $radius-full; font-size: $font-size-label-md; font-weight: 700; }
.status-pill.pending { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
.status-pill.paid { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
.status-pill.shipping { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
.status-pill.done { background: rgba(82, 196, 26, 0.15); color: $color-success; }
.status-pill.cancelled { background: $color-surface-container; color: $color-on-surface-variant; }
.status-pill.refunding { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
.status-pill.refunded { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }

// ============ 商品 ============
.items { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: $spacing-sm; }
.item { display: flex; align-items: center; gap: $spacing-sm; padding: $spacing-sm; background: $color-surface-container-low; border-radius: $radius-default; }
.item img, .img-stub { width: 56px; height: 56px; border-radius: $radius-default; object-fit: cover; flex-shrink: 0; }
.img-stub { background: $color-surface-container; display: flex; align-items: center; justify-content: center; font-size: 28px; }
.item-info { flex: 1; min-width: 0; }
.item-name { font-weight: 700; color: $color-on-surface; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.item-meta { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.item-price { font-weight: 700; color: $color-brand; white-space: nowrap; }

// ============ 退款 ============
.refund-card { border: 1px solid rgba($color-warning, 0.4); background: rgba(250, 173, 20, 0.06); }
.refund-card .card-title { color: #b45309; }
.refund-reason { display: flex; flex-direction: column; gap: 4px; margin-bottom: $spacing-sm; }
.refund-reason p { margin: 0; font-size: $font-size-body-md; color: $color-on-surface; line-height: 1.6; }
.refund-hint { margin: 0; font-size: $font-size-label-md; color: $color-on-surface-variant; }
.refund-status { font-size: $font-size-label-sm; font-weight: 700; padding: 2px $spacing-md; border-radius: $radius-full; margin-left: auto; }
.refund-status.pending { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
.refund-status.approved { background: rgba(82, 196, 26, 0.15); color: $color-success; }
.refund-status.rejected { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }

// ============ 费用 ============
.amount-list { display: flex; flex-direction: column; gap: $spacing-sm; }
.amount-row { display: flex; justify-content: space-between; padding: $spacing-sm 0; border-bottom: 1px dashed $color-outline-variant; }
.amount-row b { font-weight: 700; color: $color-on-surface; }
.amount-row .discount { color: $color-success; }
.amount-row.total { border-top: 1px solid $color-on-surface; border-bottom: none; padding-top: $spacing-md; font-family: $font-family-headline; font-size: $font-size-headline-md; font-weight: 700; }
.amount-row.total b { color: $color-brand; }

// ============ 备注 ============
.btn-sm { padding: $spacing-xs $spacing-md; font-size: $font-size-label-md; }
.remark-input {
  width: 100%; padding: $spacing-sm $spacing-md;
  border: 1px solid $color-outline-variant; border-radius: $radius-md;
  background: #fff; font-size: $font-size-body-md; font-family: inherit;
  color: $color-on-surface; outline: none; resize: vertical;
  &:focus { border-color: $color-brand; }
}

.empty { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }

@media (max-width: 768px) {
  .kv-grid { grid-template-columns: 1fr; }
}
</style>
