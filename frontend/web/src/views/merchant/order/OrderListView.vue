<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'
import type { MerchantOrder } from '@/api/modules/merchant'

const router = useRouter()

const activeTab = ref('all')

const tabs = [
  { id: 'all', label: '全部订单' },
  { id: 'pending', label: '待支付' },
  { id: 'processing', label: '待发货' },
  { id: 'receiving', label: '待收货' },
  { id: 'completed', label: '已完成' },
  { id: 'aftersale', label: '售后/退款' },
]

const TAB_STATUS: Record<string, string[]> = {
  pending: ['pending', 'pending_pay'],
  processing: ['paid'],
  receiving: ['shipping', 'receiving'],
  completed: ['done', 'completed'],
  aftersale: ['review', 'refund', 'refunding'],
}

interface Order {
  id: string
  orderNo: string
  time: string
  status: string
  statusLabel: string
  statusColor: string
  amount: number
  final: number
  buyerName: string
  buyerPhone: string
  productSummary: string
  productImage: string
  actions: string[]
}

const STATUS_LABEL: Record<string, string> = {
  pending: '待支付', pending_pay: '待支付',
  paid: '已支付', ship: '待发货', shipping: '配送中',
  verify: '待核销', receiving: '待收货',
  done: '已完成', completed: '已完成',
  review: '售后中', refund: '退款中', refunding: '退款中',
  cancelled: '已取消',
}
const STATUS_COLOR: Record<string, string> = {
  pending: 'gold', pending_pay: 'gold',
  paid: 'blue', ship: 'blue', shipping: 'blue',
  verify: 'orange', receiving: 'orange',
  done: 'green', completed: 'green',
  review: 'cyan', refund: 'cyan', refunding: 'cyan',
  cancelled: 'gray',
}

function normalize(o: MerchantOrder): Order {
  const status = (o.status || '').toLowerCase()
  const orderNo = o.orderNo ?? o.order_no ?? String(o.id)
  const time = o.createdAt ?? o.created_at ?? ''
  const actions: string[] = ['查看详情']
  if (status === 'paid') actions.push('点击发货')
  if (status === 'refunding' && refundMap.value[orderNo]) {
    actions.push('同意退款', '拒绝退款')
  }
  return {
    id: String(o.id),
    orderNo,
    time,
    status,
    statusLabel: STATUS_LABEL[status] || o.status || '未知',
    statusColor: STATUS_COLOR[status] || 'gray',
    amount: Number(o.totalAmount ?? o.total_amount ?? 0),
    final: Number(o.payAmount ?? o.pay_amount ?? 0),
    buyerName: o.buyerName ?? o.buyer_name ?? (o.userId ? `用户 ${o.userId}` : '匿名用户'),
    buyerPhone: o.buyerPhone ?? o.buyer_phone ?? '',
    productSummary: o.productSummary ?? o.product_summary ?? '订单明细',
    productImage: o.productImage ?? o.product_image ?? '',
    actions,
  }
}

// 搜索 / 分页状态
const searchInput = ref('')
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const orders = ref<Order[]>([])
const loading = ref(false)
const refundMap = ref<Record<string, number>>({})

let debounceTimer: number | undefined
function onSearchInput() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(() => {
    keyword.value = searchInput.value.trim()
    currentPage.value = 1
    loadOrders()
  }, 400)
}
function clearSearch() {
  if (debounceTimer) clearTimeout(debounceTimer)
  searchInput.value = ''
  keyword.value = ''
  currentPage.value = 1
  loadOrders()
}

async function loadOrders() {
  loading.value = true
  try {
    const statusParam = activeTab.value === 'all' ? undefined : TAB_STATUS[activeTab.value]?.join(',')
    const data = await merchantApi.orders({
      keyword: keyword.value || undefined,
      status: statusParam,
      page: currentPage.value,
      size: pageSize.value,
    })
    orders.value = (data.list || []).map(normalize)
    total.value = data.total || 0
    // 拉取「需商家动作」的售后单（pending + returned），建立 orderNo -> refundId 映射。
    // 仅 status='pending' 会漏掉「买家已寄回、等商家收货确认」的场景，所以走 todo 标记。
    try {
      const rf = await merchantApi.refunds({ status: 'todo', size: 100 })
      const map: Record<string, number> = {}
      for (const r of rf.list || []) {
        const no = r.order_no ?? (r as any).orderNo
        if (no) map[no] = r.id
      }
      refundMap.value = map
    } catch {
      refundMap.value = {}
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '订单加载失败')
    orders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

watch(activeTab, () => {
  currentPage.value = 1
  loadOrders()
})
function onPageChange(p: number) {
  currentPage.value = p
  loadOrders()
}
function onSizeChange(s: number) {
  pageSize.value = s
  currentPage.value = 1
  loadOrders()
}

onMounted(loadOrders)

async function doAction(a: string, o?: Order) {
  if (a === '查看详情') {
    if (o) router.push(`/merchant/order/${o.orderNo}`)
    return
  }
  if (a === '点击发货' && o) {
    try {
      await merchantApi.shipOrder(o.orderNo)
      ElMessage.success(`订单 ${o.orderNo} 已发货`)
      await loadOrders()
    } catch (e: any) {
      ElMessage.error(e?.message || '发货失败')
    }
    return
  }
  if ((a === '同意退款' || a === '拒绝退款') && o) {
    const rid = refundMap.value[o.orderNo]
    if (!rid) return
    if (a === '拒绝退款') {
      let note = ''
      try {
        const { value } = await ElMessageBox.prompt('请填写拒绝理由', '拒绝退款', {
          inputType: 'textarea', confirmButtonText: '确认拒绝', cancelButtonText: '取消',
          inputValidator: (v) => (v && v.trim() ? true : '请填写拒绝理由'),
        })
        note = value.trim()
      } catch {
        return
      }
      try {
        await merchantApi.rejectRefund(rid, note)
        ElMessage.success('已拒绝退款')
        await loadOrders()
      } catch (e: any) {
        ElMessage.error(e?.message || '操作失败')
      }
      return
    }
    try {
      await ElMessageBox.confirm(`确认同意订单 ${o.orderNo} 的退款？`, '同意退款', {
        type: 'warning', confirmButtonText: '确认同意', cancelButtonText: '取消',
      })
    } catch {
      return
    }
    try {
      await merchantApi.approveRefund(rid)
      ElMessage.success('已同意退款')
      await loadOrders()
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    }
  }
}

function formatTime(s: string) {
  if (!s) return '—'
  return s.replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div class="orders-view">
    <!-- 标题 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">订单管理 <span class="count">{{ total }}</span></h1>
        <p class="page-sub">管理并跟进您的商城销售订单</p>
      </div>
      <button class="btn-refresh" :disabled="loading" @click="loadOrders">
        <span class="material-symbols-outlined" :class="{ spinning: loading }">refresh</span>
        {{ loading ? '加载中' : '刷新' }}
      </button>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <div class="search-box">
        <span class="material-symbols-outlined">search</span>
        <input
          v-model="searchInput"
          type="text"
          placeholder="搜索订单号 / 买家昵称 / 手机号 / 商品名"
          @input="onSearchInput"
          @keyup.enter="onSearchInput"
        />
        <button v-if="searchInput" class="clear-btn" @click="clearSearch">
          <span class="material-symbols-outlined">close</span>
        </button>
      </div>
      <div class="result-line" v-if="!loading">
        共 <b>{{ total }}</b> 笔订单
        <span v-if="keyword" class="kw">关键词「{{ keyword }}」</span>
      </div>
    </div>

    <!-- Tab 栏 -->
    <div class="tabs-bar">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="tab"
        :class="{ active: activeTab === t.id }"
        @click="activeTab = t.id"
      >
        {{ t.label }}
        <span v-if="t.id === activeTab && total > 0" class="badge">{{ total }}</span>
      </button>
    </div>

    <!-- 状态 -->
    <div v-if="loading" class="state">
      <span class="material-symbols-outlined spinning">progress_activity</span>
      <p>加载中...</p>
    </div>
    <div v-else-if="orders.length === 0" class="state">
      <span class="material-symbols-outlined">inbox</span>
      <p>{{ keyword ? `未找到与「${keyword}」相关的订单` : '该状态下暂无订单' }}</p>
    </div>

    <!-- 订单列表 -->
    <div v-else class="order-list">
      <div
        v-for="o in orders"
        :key="o.id"
        class="order-card"
        @click="router.push(`/merchant/order/${o.orderNo}`)"
      >
        <!-- 头部：订单号 / 买家 / 时间 / 状态 -->
        <div class="card-top">
          <div class="top-left">
            <span class="order-no">{{ o.orderNo }}</span>
            <span class="dot-sep">·</span>
            <span class="buyer-name">{{ o.buyerName }}</span>
            <span v-if="o.buyerPhone" class="buyer-phone">{{ o.buyerPhone }}</span>
            <span class="dot-sep">·</span>
            <span class="order-time">{{ formatTime(o.time) }}</span>
          </div>
          <span class="status-tag" :class="o.statusColor">
            <span class="dot"></span>{{ o.statusLabel }}
          </span>
        </div>

        <!-- 主体：商品 + 金额 -->
        <div class="card-body">
          <div class="product-area">
            <div class="product-thumb">
              <img v-if="o.productImage" :src="o.productImage" :alt="o.productSummary" loading="lazy" />
              <span v-else class="material-symbols-outlined">inventory_2</span>
            </div>
            <div class="product-info">
              <h4 class="product-name" :title="o.productSummary">{{ o.productSummary }}</h4>
              <div class="product-meta">
                <span class="meta-tag">共 1 件</span>
                <span class="meta-dot">·</span>
                <span>含运费</span>
              </div>
            </div>
          </div>

          <div class="amount-area">
            <div class="amount-paid">¥{{ o.final.toFixed(2) }}</div>
            <div class="amount-meta">
              <span v-if="o.amount !== o.final" class="amount-original">¥{{ o.amount.toFixed(2) }}</span>
              <span class="amount-label">实付</span>
            </div>
          </div>
        </div>

        <!-- 底部：操作按钮（右对齐） -->
        <div class="card-actions" @click.stop>
          <button
            v-for="a in o.actions"
            :key="a"
            class="action-btn"
            :class="a === '点击发货' ? 'primary' : a === '拒绝退款' ? 'danger' : a === '同意退款' ? 'approve' : 'outline'"
            @click="doAction(a, o)"
          >
            {{ a }}
          </button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="!loading && total > 0" class="pager">
      <el-pagination
        :current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<style lang="scss" scoped>
.orders-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

// ============ 标题 ============
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  .count {
    font-size: $font-size-body-md;
    font-weight: 400;
    color: $color-on-surface-variant;
    margin-left: 4px;
  }
}
.page-sub {
  margin-top: 4px;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}
.btn-refresh {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: $spacing-xs $spacing-lg;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  background: $color-surface-container-lowest;
  color: $color-on-surface;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  .material-symbols-outlined { font-size: 18px; }
  &:hover { border-color: $color-brand; color: $color-brand; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

// ============ 搜索栏 ============
.search-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-md;
}
.search-box {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex: 1;
  min-width: 260px;
  max-width: 420px;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  transition: all 0.2s;
  .material-symbols-outlined { font-size: 20px; color: $color-on-surface-variant; }
  &:focus-within {
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.12);
  }
  input {
    flex: 1;
    border: none;
    outline: none;
    background: transparent;
    font-size: $font-size-body-md;
    font-family: inherit;
    color: $color-on-surface;
    &::placeholder { color: $color-on-surface-variant; }
  }
  .clear-btn {
    display: flex;
    align-items: center;
    justify-content: center;
    border: none;
    background: transparent;
    color: $color-on-surface-variant;
    cursor: pointer;
    padding: 2px;
    border-radius: 50%;
    transition: all 0.2s;
    .material-symbols-outlined { font-size: 16px; }
    &:hover { background: $color-surface-container; color: $color-on-surface; }
  }
}
.result-line {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  b { color: $color-on-surface; font-weight: 700; }
  .kw {
    margin-left: $spacing-xs;
    padding: 1px $spacing-sm;
    background: rgba($color-brand, 0.1);
    color: $color-brand;
    border-radius: $radius-full;
    font-weight: 600;
  }
}

// ============ Tabs ============
.tabs-bar {
  display: flex;
  gap: $spacing-xs;
  padding: $spacing-xs;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  overflow-x: auto;
}
.tab {
  padding: $spacing-xs $spacing-lg;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  gap: 6px;

  &:hover { background: $color-surface-container; }

  &.active {
    background: $color-brand;
    color: #fff;
    .badge { background: rgba(255,255,255,0.25); color: #fff; }
  }
}
.badge {
  padding: 0 6px;
  background: $color-brand;
  color: #fff;
  border-radius: $radius-full;
  font-size: 10px;
  font-weight: 700;
  min-width: 18px;
  text-align: center;
  line-height: 18px;
}

// ============ 空状态 ============
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-3xl;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 48px; opacity: 0.3; }
  p { margin: 0; font-size: $font-size-body-md; }
}

// ============ 订单卡 ============
.order-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.order-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  overflow: hidden;
  cursor: pointer;
  transition: box-shadow 0.3s cubic-bezier(0.16, 1, 0.3, 1),
              transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  &:hover {
    box-shadow: $shadow-md;
    transform: translateY(-3px);
  }
}

// 卡片头部
.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-lg;
  background: linear-gradient(180deg, $color-surface-container 0%, $color-surface-container-lowest 100%);
  border-bottom: 1px solid $color-outline-variant;
  gap: $spacing-md;
}
.top-left {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-wrap: wrap;
  min-width: 0;
}
.order-no {
  font-family: $font-family-mono;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
  letter-spacing: 0.3px;
}
.dot-sep {
  color: $color-on-surface-variant;
  opacity: 0.4;
  font-size: $font-size-label-sm;
}
.buyer-name {
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
}
.buyer-phone {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  font-family: $font-family-mono;
}
.order-time {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

// 状态标签
.status-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  flex-shrink: 0;
  .dot { width: 6px; height: 6px; border-radius: 50%; }

  &.gold { background: rgba(250, 173, 20, 0.12); color: #b45309; .dot { background: $color-warning; } }
  &.blue { background: rgba(0, 96, 172, 0.1); color: $color-secondary; .dot { background: $color-secondary; } }
  &.orange { background: rgba(255, 122, 50, 0.12); color: $color-brand; .dot { background: $color-brand; } }
  &.green { background: rgba(82, 196, 26, 0.12); color: $color-success; .dot { background: $color-success; } }
  &.cyan { background: rgba(32, 178, 178, 0.1); color: #20b2b2; .dot { background: #20b2b2; } }
  &.gray { background: $color-surface-container-high; color: $color-on-surface-variant; .dot { background: $color-on-surface-variant; } }
}

// 卡片主体
.card-body {
  display: grid;
  grid-template-columns: 1fr auto;
  align-items: center;
  gap: $spacing-lg;
  padding: $spacing-lg;

  @include tablet {
    gap: $spacing-xl;
  }
}

// 商品区
.product-area {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  min-width: 0;
}
.product-thumb {
  width: 80px;
  height: 80px;
  border-radius: $radius-md;
  background: $color-surface-container;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  overflow: hidden;
  border: 1px solid $color-outline-variant;
  transition: transform 0.3s cubic-bezier(0.16, 1, 0.3, 1);
  .order-card:hover & { transform: scale(1.05); }
  img { width: 100%; height: 100%; object-fit: cover; }
  .material-symbols-outlined { font-size: 32px; color: $color-on-surface-variant; }
}
.product-info {
  min-width: 0;
  flex: 1;
}
.product-name {
  font-size: $font-size-body-md;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0 0 6px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}
.product-meta {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  flex-wrap: wrap;
}
.meta-tag {
  padding: 1px $spacing-sm;
  background: $color-surface-container;
  border-radius: $radius-sm;
  font-weight: 600;
}
.meta-dot { opacity: 0.5; }

// 金额区
.amount-area {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  min-width: 100px;
}
.amount-paid {
  font-family: $font-family-mono;
  font-size: 20px;
  font-weight: 700;
  color: $color-emergency;
  line-height: 1.2;
  letter-spacing: -0.3px;
}
.amount-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.amount-original {
  text-decoration: line-through;
  opacity: 0.6;
}
.amount-label { font-weight: 500; }

// 操作区（卡片底部，右对齐胶囊按钮）
.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-lg;
  border-top: 1px dashed $color-outline-variant;
  background: linear-gradient(180deg, transparent 0%, rgba($color-surface-container, 0.3) 100%);
}
.action-btn {
  padding: 6px $spacing-lg;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s cubic-bezier(0.16, 1, 0.3, 1);
  border: 1px solid transparent;

  &.primary {
    background: $color-brand;
    color: #fff;
    border-color: $color-brand;
    &:hover {
      background: $color-brand-dark;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba($color-brand, 0.3);
    }
  }
  &.outline {
    background: transparent;
    border-color: $color-outline-variant;
    color: $color-on-surface;
    &:hover { border-color: $color-brand; color: $color-brand; }
  }
  &.approve {
    background: $color-success;
    color: #fff;
    border-color: $color-success;
    &:hover { filter: brightness(0.95); transform: translateY(-1px); }
  }
  &.danger {
    background: transparent;
    border-color: $color-emergency;
    color: $color-emergency;
    &:hover { background: rgba($color-emergency, 0.08); }
  }
}

// ============ 分页 ============
.pager {
  display: flex;
  justify-content: flex-end;
  padding-top: $spacing-xs;
}
.pager :deep(.el-pagination) {
  font-weight: 500;
}
.pager :deep(.el-pagination.is-background .el-pager li.is-active) {
  background: $color-brand;
}

// ============ 响应式 ============
@media (max-width: 768px) {
  .card-body {
    grid-template-columns: 1fr;
    gap: $spacing-md;
    .amount-area {
      align-items: flex-start;
      flex-direction: row;
      gap: $spacing-lg;
      align-items: baseline;
    }
  }
  .card-top {
    flex-wrap: wrap;
    .top-left { gap: $spacing-xs; }
  }
  .card-actions { justify-content: stretch; }
  .action-btn { flex: 1; }
  .search-bar { flex-direction: column; align-items: stretch; }
  .search-box { max-width: none; }
}
</style>
