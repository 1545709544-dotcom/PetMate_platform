<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { merchantApi, type MerchantTodo, type MerchantOrder } from '@/api/modules/merchant'

const router = useRouter()

interface KpiItem {
  label: string
  value: string
  trend: string
  icon: string
  color: string
}

interface SalesPoint {
  day: string
  value: number
}

interface RecentOrderItem {
  id: string
  orderNo: string
  pet: string
  petDesc: string
  service: string
  amount: number
  time: string
  status: string
}

const loading = ref(false)

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 11) return '早安'
  if (h < 13) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const shopName = ref('您的店铺')
const kpis = ref<KpiItem[]>([])

const chartMode = ref<'sales' | 'orders'>('sales')

const salesData = ref<SalesPoint[]>([])
const ordersData = ref<SalesPoint[]>([])

const chartDisplay = computed<SalesPoint[]>(() =>
  chartMode.value === 'sales' ? salesData.value : ordersData.value,
)
const maxSales = computed(() =>
  chartDisplay.value.length ? Math.max(...chartDisplay.value.map((d) => d.value)) : 0,
)
const hasData = computed(() => chartDisplay.value.some((d) => d.value > 0))

const todos = ref<MerchantTodo[]>([])
const recentOrders = ref<RecentOrderItem[]>([])

const statusMap: Record<string, string> = {
  '服务中': 'orange',
  '已支付': 'gold',
  '已发货': 'cyan',
  '已完成': 'green',
  '已取消': 'red',
  '退款中': 'cyan',
  '待支付': 'gold',
}

const now = ref(new Date().toLocaleTimeString('zh-CN'))
let timer: number

function formatTime(t: string) {
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  return d.toTimeString().slice(0, 8)
}

function mapOrderStatus(s: string): string {
  switch (s) {
    case 'pending_pay': return '待支付'
    case 'paid': return '已支付'
    case 'shipping': return '服务中'
    case 'done': return '已完成'
    case 'cancelled': return '已取消'
    case 'refunding': return '退款中'
    default: return s
  }
}

function orderSummary(o: MerchantOrder): RecentOrderItem {
  const orderNo = o.orderNo ?? o.order_no ?? String(o.id)
  const time = o.createdAt ?? o.created_at ?? ''
  const summary = o.productSummary ?? o.product_summary ?? '订单'
  const amount = Number(o.payAmount ?? o.pay_amount ?? o.totalAmount ?? o.total_amount ?? 0)
  return {
    id: String(o.id),
    orderNo,
    pet: summary,
    petDesc: o.buyerName || o.buyer_name || (o.userId ? `用户ID ${o.userId}` : '匿名用户'),
    service: summary,
    amount,
    time: formatTime(time),
    status: mapOrderStatus((o.status || '').toLowerCase()),
  }
}

async function loadWorkbench() {
  loading.value = true
  try {
    const [kpi, sales, todoData, orderData, storeData] = await Promise.all([
      merchantApi.dashboard(),
      merchantApi.sales('7d'),
      merchantApi.todos(),
      merchantApi.orders(),
      merchantApi.store().catch(() => null),
    ])

    kpis.value = [
      {
        label: '今日销售额',
        value: `¥ ${Number(kpi.todaySales).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
        trend: '',
        icon: 'payments',
        color: 'orange',
      },
      {
        label: '今日订单',
        value: String(kpi.todayOrders),
        trend: '',
        icon: 'shopping_bag',
        color: 'blue',
      },
      {
        label: '待处理订单',
        value: String(kpi.pendingOrders),
        trend: kpi.pendingOrders > 0 ? '需加急' : '',
        icon: 'event',
        color: 'gold',
      },
      {
        label: '今日新增客户',
        value: String(kpi.newCustomers),
        trend: '',
        icon: 'task_alt',
        color: 'red',
      },
    ]

    salesData.value = sales.map((d) => ({ day: d.date, value: d.salesAmount }))
    ordersData.value = sales.map((d) => ({ day: d.date, value: d.orderCount }))

    todos.value = todoData || []
    recentOrders.value = (orderData?.list || []).slice(0, 8).map(orderSummary)

    if (storeData?.name) shopName.value = storeData.name
  } catch (e: any) {
    ElMessage.error(e?.message || '加载工作台失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = new Date().toLocaleTimeString('zh-CN')
  }, 1000)
  loadWorkbench()
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="workbench-view">
    <!-- ========== 顶部：欢迎 + 快捷操作 ========== -->
    <div class="top-bar">
      <div class="welcome">
        <h2 class="welcome-title">{{ greeting }}，{{ shopName }}</h2>
        <p class="welcome-sub">这是您今天的店铺概览，看起来是个忙碌的好日子。</p>
      </div>
      <div class="quick-actions">
        <button class="qa-btn" @click="router.push('/merchant/product/edit')">
          <span class="material-symbols-outlined">add_box</span>
          <span>发布商品</span>
        </button>
        <button class="qa-btn" @click="router.push('/merchant/order')">
          <span class="material-symbols-outlined">local_shipping</span>
          <span>处理订单</span>
        </button>
        <button class="qa-btn" @click="router.push('/merchant/review')">
          <span class="material-symbols-outlined">reply_all</span>
          <span>回复评价</span>
        </button>
        <button class="qa-btn" @click="router.push('/merchant/appointments')">
          <span class="material-symbols-outlined">event_available</span>
          <span>预约管理</span>
        </button>
        <button class="qa-btn" @click="router.push('/merchant/service/edit')">
          <span class="material-symbols-outlined">add_task</span>
          <span>发布服务</span>
        </button>
      </div>
    </div>

    <!-- ========== KPI ========== -->
    <section v-loading="loading" class="kpi-grid">
      <div
        v-for="k in kpis"
        :key="k.label"
        class="kpi-card"
        :class="`border-${k.color}`"
      >
        <div class="kpi-head">
          <div class="kpi-icon" :class="`bg-${k.color}`">
            <span class="material-symbols-outlined">{{ k.icon }}</span>
          </div>
          <span v-if="k.trend" class="kpi-trend" :class="`text-${k.color}`">
            <span class="material-symbols-outlined">trending_up</span>
            {{ k.trend }}
          </span>
        </div>
        <div>
          <p class="kpi-label">{{ k.label }}</p>
          <h3 class="kpi-value">{{ k.value }}</h3>
        </div>
      </div>
    </section>

    <!-- ========== 趋势 + 待办 ========== -->
    <section class="row">
      <!-- 趋势 -->
      <div class="trend-card">
        <div class="trend-head">
          <div>
            <h3 class="section-title">销售趋势</h3>
            <p class="trend-sub">最近7天的经营业绩波动图</p>
          </div>
          <div class="trend-tabs">
            <button
              v-for="t in ['sales', 'orders'] as const"
              :key="t"
              class="trend-tab"
              :class="{ active: chartMode === t }"
              @click="chartMode = t"
            >
              {{ t === 'sales' ? '销售额' : '订单量' }}
            </button>
          </div>
        </div>
        <div class="chart" v-if="hasData">
          <div
            v-for="(d, i) in chartDisplay"
            :key="i"
            class="chart-bar"
          >
            <span class="bar-value">{{ chartMode === 'sales' ? '¥' + d.value : d.value }}</span>
            <div class="bar-wrap" :title="`${d.day}: ${d.value}`">
              <div
                class="bar-fill"
                :style="{ height: `${(d.value / (maxSales || 1)) * 100}%` }"
              ></div>
            </div>
            <span class="bar-label">{{ d.day.slice(5) }}</span>
          </div>
        </div>
        <div v-else class="empty">
          近 7 天暂无{{ chartMode === 'sales' ? '销售额' : '订单量' }}数据
        </div>
      </div>

      <!-- 待办 -->
      <div class="todo-card">
        <div class="todo-head">
          <h3 class="section-title">待办任务</h3>
          <a class="todo-more" @click="router.push('/merchant/order')">全部</a>
        </div>
        <div v-if="todos.length" class="todo-list">
          <div
            v-for="t in todos"
            :key="t.id"
            class="todo-item"
            :class="`theme-${t.color}`"
          >
            <div class="todo-icon">
              <span class="material-symbols-outlined">{{ t.icon }}</span>
            </div>
            <div class="todo-body">
              <h4 class="todo-title-text">{{ t.title }}</h4>
              <p class="todo-desc">{{ t.desc }}</p>
            </div>
          </div>
        </div>
        <div v-else class="empty">暂无待办</div>
      </div>
    </section>

    <!-- ========== 实时订单 ========== -->
    <section class="orders-card">
      <div class="orders-head">
        <h3 class="section-title">实时订单动态</h3>
        <span class="orders-update">
          <span class="dot"></span>
          实时更新中 · {{ now }}
        </span>
      </div>
      <table v-if="recentOrders.length" class="orders-table">
        <thead>
          <tr>
            <th>订单编号</th>
            <th>商品信息</th>
            <th>实付金额</th>
            <th>下单时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in recentOrders" :key="o.id">
            <td class="cell-id">{{ o.id }}</td>
            <td>
              <div class="pet-cell">
                <div class="pet-avatar">
                  <span class="material-symbols-outlined">pets</span>
                </div>
                <div>
                  <div class="pet-name">{{ o.pet }}</div>
                  <div class="pet-desc">{{ o.petDesc }}</div>
                </div>
              </div>
            </td>
            <td class="cell-amount">¥ {{ o.amount.toFixed(2) }}</td>
            <td class="cell-time">{{ o.time }}</td>
            <td>
              <span class="status-pill" :class="`status-${statusMap[o.status] || 'gold'}`">{{ o.status }}</span>
            </td>
            <td><a class="cell-action" @click="router.push(`/merchant/order/${o.orderNo ?? o.order_no ?? o.id}`)">详情</a></td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无订单</div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.workbench-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

// ============ 顶部栏 ============
.top-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-lg;
  flex-wrap: wrap;
}
.welcome-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;
}
.welcome-sub {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

// 快捷操作
.quick-actions {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: wrap;
}
.qa-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: $spacing-sm $spacing-md;
  min-width: 80px;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  cursor: pointer;
  transition: all 0.2s;
  font-family: inherit;
  .material-symbols-outlined { font-size: 24px; color: $color-brand; }
  span:last-child { font-size: $font-size-label-sm; font-weight: 600; color: $color-on-surface; }
  &:hover { border-color: $color-brand; background: rgba($color-brand, 0.04); transform: translateY(-2px); }
}

// ============ KPI ============
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include tablet {
    grid-template-columns: repeat(4, 1fr);
  }
}

.kpi-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-sm;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: box-shadow $transition-base;
  border-left: 4px solid;

  &.border-orange { border-left-color: $color-brand; }
  &.border-blue { border-left-color: $color-secondary; }
  &.border-gold { border-left-color: $color-warning; }
  &.border-red { border-left-color: $color-emergency; }

  &:hover { box-shadow: $shadow-md; }
}

.kpi-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $spacing-md;
}

.kpi-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-lg;

  .material-symbols-outlined { font-size: 28px; }

  &.bg-orange { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.bg-blue { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  &.bg-gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.bg-red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
}

.kpi-trend {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: $font-size-label-sm;
  font-weight: 600;
  padding: 2px $spacing-xs;
  border-radius: $radius-full;

  &.text-orange { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.text-blue { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  &.text-gold { background: rgba(250, 173, 20, 0.2); color: $color-warning; }
  &.text-red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }

  .material-symbols-outlined { font-size: 14px; }
}

.kpi-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: 4px;
}

.kpi-value {
  font-family: $font-family-headline;
  font-size: 32px;
  font-weight: 700;
  color: $color-on-surface;
}

// ============ 双栏行 ============
.row {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: 1.6fr 1fr;
  }
}

.trend-card,
.todo-card,
.orders-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
}

.section-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

// ============ 趋势图 ============
.trend-head,
.todo-head,
.orders-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
}

.trend-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 4px;
}

.trend-tabs {
  display: flex;
  gap: $spacing-xs;
  padding: 4px;
  background: $color-surface-container;
  border-radius: $radius-full;
}

.trend-tab {
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  &.active {
    background: $color-surface-container-lowest;
    color: $color-brand;
    font-weight: 700;
    box-shadow: $shadow-sm;
  }
}

.chart {
  display: flex;
  align-items: flex-end;
  gap: $spacing-sm;
  height: 180px;
  padding-top: $spacing-md;
}

.chart-bar {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  height: 100%;
}

.bar-wrap {
  width: 100%;
  flex: 1;
  min-height: 4px;
  display: flex;
  align-items: flex-end;
  border-radius: $radius-sm;
}

.bar-fill {
  width: 100%;
  background: linear-gradient(180deg, $color-brand, $color-brand-dark);
  border-radius: $radius-sm;
  transition: height $transition-base;
  min-height: 4px;
}

.bar-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.bar-value {
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-brand;
  margin-bottom: 2px;
  white-space: nowrap;
}

// ============ 待办 ============
.todo-more {
  font-size: $font-size-label-md;
  color: $color-brand;
  cursor: pointer;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.todo-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;

  &.theme-orange { background: rgba(255, 122, 50, 0.08); }
  &.theme-blue { background: rgba(0, 96, 172, 0.08); }
  &.theme-gold { background: rgba(250, 173, 20, 0.12); }
  &.theme-cyan { background: rgba(32, 178, 178, 0.08); }
}

.todo-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;

  .theme-orange & { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  .theme-blue & { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  .theme-gold & { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  .theme-cyan & { background: rgba(32, 178, 178, 0.15); color: #20b2b2; }

  .material-symbols-outlined { font-size: 18px; }
}

.todo-body { flex: 1; }

.todo-title-text {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}

.todo-desc {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

// ============ 订单 ============
.orders-update {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-sm;
  color: $color-success;
  font-weight: 600;

  .dot {
    width: 8px;
    height: 8px;
    background: $color-success;
    border-radius: $radius-full;
    animation: blink 1.5s infinite;
  }
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.orders-table {
  width: 100%;
  border-collapse: collapse;

  th {
    text-align: left;
    padding: $spacing-sm $spacing-md;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    font-weight: 600;
    border-bottom: 1px solid $color-outline-variant;
  }

  td {
    padding: $spacing-md;
    font-size: $font-size-body-md;
    color: $color-on-surface;
    border-bottom: 1px solid $color-outline-variant;
  }
}

.cell-id { font-weight: 700; color: $color-on-surface; }

.pet-cell {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.pet-avatar {
  width: 40px;
  height: 40px;
  background: $color-brand-container;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;

  .material-symbols-outlined {
    color: $color-brand;
    font-size: 20px;
    font-variation-settings: 'FILL' 1;
  }
}

.pet-name { font-weight: 700; }
.pet-desc { font-size: $font-size-label-sm; color: $color-on-surface-variant; }

.cell-amount { font-weight: 700; color: $color-brand; }
.cell-time { font-size: $font-size-label-sm; color: $color-on-surface-variant; font-family: $font-family-mono; }

.status-pill {
  padding: 4px $spacing-sm;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;

  &.status-orange { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.status-green { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.status-gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.status-red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
}

.cell-action {
  font-size: $font-size-label-md;
  color: $color-brand;
  cursor: pointer;
  &:hover { text-decoration: underline; }
}

.empty {
  padding: $spacing-md;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
}

// ============ 响应式 ============
@media (max-width: 768px) {
  .top-bar { flex-direction: column; }
  .quick-actions { width: 100%; justify-content: space-between; }
  .qa-btn { min-width: 60px; padding: $spacing-xs $spacing-sm; }
}
</style>