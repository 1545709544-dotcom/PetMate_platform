<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi, type MerchantFinanceRecord } from '@/api/modules/merchant'

const loading = ref(false)
const activeTab = ref('all')
const records = ref<MerchantFinanceRecord[]>([])
const summary = ref({ totalIncome: 0, pendingSettlement: 0, settled: 0 })

const tabs = [
  { id: 'all', label: '全部' },
  { id: 'pending', label: '待结算' },
  { id: 'settled', label: '已结算' },
  { id: 'cancelled', label: '已取消' },
]

async function load() {
  loading.value = true
  try {
    const [sum, recs] = await Promise.all([
      merchantApi.finance(),
      merchantApi.financeRecords({ status: filterStatus(activeTab.value), limit: 100 }),
    ])
    summary.value = sum
    records.value = recs
  } catch (e: any) {
    ElMessage.error(e?.message || '财务数据加载失败')
  } finally {
    loading.value = false
  }
}

// 把 UI 上的 tab 映射到后端 /merchant/finance/records 的 status 入参，
// 让「全/待结算/已结算/已取消」真正各自打到对应查询，而不是只在前端内存过滤。
// 'all' / undefined 才拉全量。
function filterStatus(id: string): 'pending' | 'settled' | 'cancelled' | undefined {
  if (id === 'all') return undefined
  if (id === 'pending') return 'pending'
  if (id === 'settled') return 'settled'
  if (id === 'cancelled') return 'cancelled'
  return undefined
}

// 当 tab 切换时主动重拉数据，避免前端 100 条之外的记录被永久丢弃
async function setTab(id: string) {
  activeTab.value = id
  await load()
}

// 兜底：如果后端返了意外 status，再用前端映射过滤一次（不破坏主链路）
const filtered = computed(() => {
  if (activeTab.value === 'all') return records.value
  const s = filterStatus(activeTab.value)
  if (!s) return records.value
  if (s === 'pending') return records.value.filter(r => ['paid', 'shipping'].includes((r.status || '').toLowerCase()))
  if (s === 'settled') return records.value.filter(r => (r.status || '').toLowerCase() === 'done')
  return records.value.filter(r => (r.status || '').toLowerCase() === s)
})

function statusLabel(s: string) {
  const k = (s || '').toLowerCase()
  const map: Record<string, string> = {
    paid: '已支付', shipping: '配送中', done: '已完成',
    pending_pay: '待支付', cancelled: '已取消', refunding: '退款中',
    refunded: '已退款',
  }
  return map[k] || s
}

function statusCls(s: string) {
  const k = (s || '').toLowerCase()
  if (k === 'done') return 'green'
  if (k === 'cancelled' || k === 'refunded') return 'gray'
  if (k === 'refunding') return 'cyan'
  return 'gold'
}

onMounted(load)
</script>

<template>
  <div class="finance-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">财务结算</h1>
        <p class="page-sub">查看订单回款、待结算与已结算账单，掌握现金流动态。</p>
      </div>
    </div>

    <!-- KPI -->
    <section class="kpi-grid">
      <div class="kpi-card border-orange">
        <p class="kpi-label">累计收入</p>
        <h3 class="kpi-value">¥{{ Number(summary.totalIncome).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }}</h3>
        <span class="kpi-sub">来自所有订单回款</span>
      </div>
      <div class="kpi-card border-gold">
        <p class="kpi-label">待结算</p>
        <h3 class="kpi-value">¥{{ Number(summary.pendingSettlement).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }}</h3>
        <span class="kpi-sub">已支付未完成</span>
      </div>
      <div class="kpi-card border-blue">
        <p class="kpi-label">已结算</p>
        <h3 class="kpi-value">¥{{ Number(summary.settled).toLocaleString('zh-CN', { maximumFractionDigits: 2 }) }}</h3>
        <span class="kpi-sub">已完成的订单</span>
      </div>
      <div class="kpi-card border-red">
        <p class="kpi-label">账单数</p>
        <h3 class="kpi-value">{{ records.length }}</h3>
        <span class="kpi-sub">最近 100 条</span>
      </div>
    </section>

    <!-- Tabs -->
    <div class="tabs">
      <button v-for="t in tabs" :key="t.id" class="tab"
        :class="{ active: activeTab === t.id }"
        @click="activeTab = t.id">{{ t.label }}</button>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>金额</th>
            <th>状态</th>
            <th>下单时间</th>
            <th>支付时间</th>
            <th>结算时间</th>
          </tr>
        </thead>
        <tbody v-if="filtered.length === 0">
          <tr><td colspan="6" class="state-cell">暂无账单记录</td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="r in filtered" :key="r.id">
            <td class="order-no">{{ r.orderNo }}</td>
            <td class="amount">¥{{ Number(r.payAmount).toFixed(2) }}</td>
            <td><span class="status-pill" :class="statusCls(r.status)">{{ statusLabel(r.status) }}</span></td>
            <td>{{ r.date || '—' }}</td>
            <td>{{ r.paidAt || '—' }}</td>
            <td>{{ r.settledAt || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.finance-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}
.page-sub {
  margin-top: 4px;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;
  @include tablet { grid-template-columns: repeat(4, 1fr); }
}
.kpi-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border-top: 4px solid;
  &.border-orange { border-top-color: $color-brand; }
  &.border-blue { border-top-color: $color-secondary; }
  &.border-gold { border-top-color: $color-warning; }
  &.border-red { border-top-color: $color-emergency; }
}
.kpi-label { font-size: $font-size-label-md; color: $color-on-surface-variant; margin-bottom: $spacing-sm; }
.kpi-value {
  font-family: $font-family-headline;
  font-size: 28px;
  font-weight: 700;
  color: $color-on-surface;
}
.kpi-sub { font-size: $font-size-label-sm; color: $color-on-surface-variant; margin-top: 4px; }

.tabs {
  display: flex;
  gap: $spacing-lg;
  border-bottom: 1px solid $color-outline-variant;
}
.tab {
  position: relative;
  padding: $spacing-sm 0;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface-variant;
  cursor: pointer;
  &.active {
    color: $color-brand;
    &::after {
      content: '';
      position: absolute;
      bottom: -1px; left: 0; right: 0;
      height: 3px;
      background: $color-brand;
      border-radius: $radius-full;
    }
  }
}

.table-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  overflow: hidden;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
  th {
    text-align: left;
    padding: $spacing-md;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    font-weight: 600;
    background: $color-surface-container-low;
    border-bottom: 1px solid $color-outline-variant;
  }
  td {
    padding: $spacing-md;
    border-bottom: 1px solid $color-outline-variant;
    font-size: $font-size-body-md;
  }
}
.state-cell { text-align: center; padding: $spacing-2xl !important; color: $color-on-surface-variant; }
.order-no { font-weight: 700; color: $color-on-surface; font-family: $font-family-mono; }
.amount { font-weight: 700; color: $color-brand; }
.status-pill {
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;
  &.green { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.cyan { background: rgba(32, 178, 178, 0.15); color: #20b2b2; }
  &.gray { background: $color-surface-container; color: $color-on-surface-variant; }
}
</style>