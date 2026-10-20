<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

const loading = ref(false)
const range = ref<'7d' | '30d' | '90d'>('7d')
const lastUpdated = ref('')

// 顶部 KPI
const kpis = ref<Array<{ label: string; value: string; trend?: string; icon: string; color: string }>>([
  { label: '今日销售额', value: '—', icon: 'payments', color: 'orange' },
  { label: '今日活跃用户', value: '—', icon: 'group', color: 'blue' },
  { label: '订单转化率', value: '—', icon: 'shopping_bag', color: 'gold' },
  { label: '客单价', value: '—', icon: 'payments', color: 'red' },
])

const bottomKpis = ref<Array<{ label: string; value: string; unit: string; color: string }>>([
  { label: '库存预警', value: '—', unit: 'SKUs', color: 'red' },
  { label: '待处理工单', value: '—', unit: 'Ticket', color: 'gold' },
  { label: '客户好评率', value: '—', unit: '', color: 'green' },
])

// 客流趋势 (用于图表)
const trend = ref<Array<{ date: string; orderCount: number; visitorCount: number }>>([])
const funnel = ref<Record<string, any>>({})
const categoryShare = ref<Array<{ name: string; amount: number; orderCount: number }>>([])

// 热销商品 TOP N（按销量）
interface HotProduct {
  id: number
  name: string
  category_id: number
  main_image: string
  sale_price: number
  sale_count: number
  rating: number
}
const hotProducts = ref<HotProduct[]>([])

const totalCategoryAmount = computed(() =>
  categoryShare.value.reduce((s, c) => s + (Number(c.amount) || 0), 0)
)

const funnelNoTracking = computed(() => {
  const f = funnel.value || {}
  // 真实模式下：已有订单但浏览/加购埋点全为 0 → 尚未采集
  return !!f.noTracking || (Number(f.pv || 0) === 0 && Number(f.addCart || 0) === 0 && Number(f.order || 0) > 0)
})
const funnelBase = computed(() => {
  const f = funnel.value || {}
  // 无埋点时以订单数为基准，避免 pv=0 把真实段压成 2%
  if (funnelNoTracking.value) return Math.max(Number(f.order) || 0, Number(f.paid) || 0, 1)
  return Math.max(Number(f.pv) || 0, 1)
})

const funnelSteps = computed(() => {
  const f = funnel.value || {}
  const pv = Number(f.pv) || 0
  const cart = Number(f.cart) || Number(f.addCart) || 0
  const order = Number(f.order) || 0
  const paid = Number(f.paid) || 0
  const no = funnelNoTracking.value
  const loss = (a: number, b: number) => (a > 0 ? (((a - b) / a) * 100).toFixed(1) + '%' : '—')
  return [
    { step: '1. 商品浏览', count: no ? '待采集' : `${pv.toLocaleString()} (UV)`, loss: null as string | null, muted: no },
    { step: '2. 加入购物车', count: no ? '待采集' : `${cart.toLocaleString()} (人)`, loss: pv > 0 ? loss(pv, cart) : '—', muted: no },
    { step: '3. 确认订单', count: `${order.toLocaleString()} (单)`, loss: cart > 0 ? loss(cart, order) : '—', muted: false },
    { step: '4. 支付成功', count: `${paid.toLocaleString()} (单)`, loss: order > 0 ? loss(order, paid) : '—', muted: false },
  ]
})

const funnelBars = computed(() => {
  const f = funnel.value || {}
  const base = funnelBase.value
  const pv = Number(f.pv) || 0
  const cart = Number(f.cart) || Number(f.addCart) || 0
  const order = Number(f.order) || 0
  const paid = Number(f.paid) || 0
  return [
    Math.max(pv > 0 ? 2 : 4, Math.round((pv / base) * 100)),
    Math.max(cart > 0 ? 2 : 4, Math.round((cart / base) * 100)),
    Math.max(order > 0 ? 2 : 4, Math.round((order / base) * 100)),
    Math.max(paid > 0 ? 2 : 4, Math.round((paid / base) * 100)),
  ]
})

async function loadAll() {
  loading.value = true
  try {
    const [ov, f, trendData, share, hot] = await Promise.all([
      adminApi.overview(),
      adminApi.funnel(),
      adminApi.visitorTrend(range.value),
      adminApi.categoryShare(),
      adminApi.hotProducts(10),
    ])
    // 顶部 KPI
    kpis.value = [
      { label: '今日销售额', value: '¥' + Number(ov.todaySales ?? 0).toLocaleString(),
        icon: 'payments', color: 'orange' },
      { label: '今日活跃用户', value: Number(ov.todayActiveUsers ?? 0).toLocaleString(),
        icon: 'group', color: 'blue' },
      { label: '订单转化率', value: ((Number(ov.conversionRate ?? 0)) * 100).toFixed(1) + '%',
        icon: 'shopping_bag', color: 'gold' },
      { label: '客单价', value: '¥' + Number(ov.avgOrderAmount ?? 0).toFixed(2),
        icon: 'payments', color: 'red' },
    ]
    funnel.value = f || {}
    trend.value = trendData || []
    categoryShare.value = share || []
    hotProducts.value = Array.isArray(hot) ? hot : []
    // 底部 KPI
    bottomKpis.value = [
      { label: '库存预警', value: String(ov.stockAlerts ?? 0), unit: 'SKUs', color: 'red' },
      { label: '待处理工单', value: String(ov.pendingTickets ?? 0), unit: 'Ticket', color: 'gold' },
      { label: '客户好评率', value: ((Number(ov.positiveRate ?? 0)) * 100).toFixed(1) + '%',
        unit: '', color: 'green' },
    ]
  } catch (e: any) {
    ElMessage.error(e?.message || '加载数据看板失败')
  } finally {
    loading.value = false
    lastUpdated.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  }
}

function setRange(r: typeof range.value) {
  range.value = r
  loadAll()
}

function refresh() {
  loadAll()
}

onMounted(loadAll)

// ===== 客流图表 SVG =====
function buildPath(values: number[]): string {
  const w = 700, h = 280, top = 20, bottom = 40
  if (!values.length) return ''
  const max = Math.max(...values, 1)
  const stepX = values.length > 1 ? w / (values.length - 1) : w
  const pts = values.map((v, i) => ({ x: i * stepX, y: top + (1 - v / max) * (h - top - bottom) }))
  return pts.map((p, i, arr) => {
    if (i === 0) return `M ${p.x.toFixed(1)} ${p.y.toFixed(1)}`
    const prev = arr[i - 1]
    const cx = (prev.x + p.x) / 2
    return `Q ${cx.toFixed(1)} ${prev.y.toFixed(1)} ${cx.toFixed(1)} ${((prev.y + p.y) / 2).toFixed(1)} T ${p.x.toFixed(1)} ${p.y.toFixed(1)}`
  }).join(' ')
}

const visitorPath = computed(() => buildPath(trend.value.map((t) => Number(t.visitorCount) || 0)))
const visitorArea = computed(() => {
  const p = visitorPath.value
  if (!p) return ''
  return `${p} L 700 280 L 0 280 Z`
})
const orderPath = computed(() => buildPath(trend.value.map((t) => Number(t.orderCount) || 0)))
const xLabels = computed(() => trend.value.map((t) => (t.date || '').slice(5)))

function hideImg(e: Event) {
  ;(e.target as HTMLImageElement).style.display = 'none'
}
</script>

<template>
  <div class="analytics-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">数据看板 <span class="version">v3.13.6</span></h1>
        <p class="page-sub">实时监控平台经营动态与用户转化数据</p>
      </div>
      <div class="head-controls">
        <span class="updated" v-if="lastUpdated">更新于 {{ lastUpdated }}</span>
        <button class="refresh-btn" :disabled="loading" @click="refresh">
          <span class="material-symbols-outlined">refresh</span>
          {{ loading ? '刷新中' : '刷新' }}
        </button>
        <div class="range-tabs">
          <button
            v-for="r in (['7d','30d','90d'] as const)"
            :key="r"
            class="range-tab"
            :class="{ active: range === r }"
            @click="setRange(r)"
          >
            {{ r === '7d' ? '近 7 天' : r === '30d' ? '近 30 天' : '近 90 天' }}
          </button>
        </div>
      </div>
    </div>

    <div class="kpi-grid">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-icon" :class="`bg-${k.color}`">
          <span class="material-symbols-outlined">{{ k.icon }}</span>
        </div>
        <p class="kpi-label">{{ k.label }}</p>
        <h3 class="kpi-value">{{ k.value }}</h3>
      </div>
    </div>

    <div class="middle-row">
      <section class="chart-card">
        <div class="chart-head">
          <div>
            <h3 class="chart-title">客流趋势与下单转化</h3>
            <p class="chart-sub">每日活跃用户与下单曲线（基于 t_order 去重 user_id）</p>
          </div>
          <div class="chart-legend">
            <span><i class="dot blue"></i> 访客数</span>
            <span><i class="dot orange"></i> 订单数</span>
          </div>
        </div>
        <div v-if="!trend.length" class="empty-tip">暂无数据</div>
        <div v-else class="chart-canvas">
          <svg viewBox="0 0 700 280" preserveAspectRatio="none" class="line-chart">
            <line v-for="i in 9" :key="i" x1="0" :y1="(i - 1) * 35 + 5" x2="700" :y2="(i - 1) * 35 + 5"
                  stroke="#e2e9ec" stroke-width="1" />
            <path :d="visitorPath" fill="none" stroke="#0060ac" stroke-width="3" />
            <path :d="visitorArea" fill="rgba(0, 96, 172, 0.08)" />
            <path :d="orderPath" fill="none" stroke="#ff7a32" stroke-width="3" />
          </svg>
          <div class="x-labels">
            <span v-for="(l, i) in xLabels" :key="i">{{ l }}</span>
          </div>
        </div>
      </section>

      <section class="ring-card">
        <h3 class="chart-title">销售品类分布</h3>
        <p class="chart-sub">按一级分类聚合的实付订单金额</p>
        <div v-if="!categoryShare.length || totalCategoryAmount === 0" class="empty-tip">暂无销售数据</div>
        <template v-else>
          <div class="ring-wrap">
            <svg viewBox="0 0 200 200" class="donut">
              <template v-for="(c, i) in categoryShare" :key="i">
                <circle v-if="(c.amount as number) > 0"
                        cx="100" cy="100" r="70" fill="none"
                        :stroke="['#a14000','#0060ac','#785900','#ff7a32','#20b2aa','#faad14','#52c41a','#722ed1'][i % 8]"
                        stroke-width="32"
                        :stroke-dasharray="`${(Number(c.amount) / totalCategoryAmount * 440).toFixed(2)} ${(440 - Number(c.amount) / totalCategoryAmount * 440).toFixed(2)}`"
                        :stroke-dashoffset="-categoryShare.slice(0, i).reduce((s: number, p: any) => s + Number(p.amount) / totalCategoryAmount * 440, 0)"
                        transform="rotate(-90 100 100)" />
              </template>
            </svg>
          </div>
          <div class="ring-legend">
            <div v-for="(c, i) in categoryShare" :key="i" class="legend-item">
              <i class="dot" :style="{ background: ['#a14000','#0060ac','#785900','#ff7a32','#20b2aa','#faad14','#52c41a','#722ed1'][i % 8] }"></i>
              {{ c.name }} ({{ totalCategoryAmount > 0 ? ((Number(c.amount) / totalCategoryAmount) * 100).toFixed(1) : 0 }}%)
            </div>
          </div>
        </template>
      </section>
    </div>

    <div class="bottom-row">
      <section class="funnel-card">
        <h3 class="chart-title">转化漏斗</h3>
        <p class="chart-sub">从触达到成交的关键路径（基于 t_event / MetricService）</p>
        <div v-if="funnelNoTracking" class="funnel-tip">
          <span class="material-symbols-outlined">info</span>
          浏览 / 加购数据需前端埋点上报后展示，当前仅显示真实订单与支付转化。
        </div>
        <div class="funnel">
          <div v-for="(s, i) in funnelSteps" :key="i" class="funnel-step" :class="{ muted: s.muted }">
            <div class="step-row">
              <span class="step-num">{{ s.step }}</span>
              <span class="step-count">{{ s.count }}</span>
              <span v-if="s.loss" class="step-loss">↓ 流失率: {{ s.loss }}</span>
            </div>
            <div class="step-bar">
              <div class="step-fill" :style="{ width: funnelBars[i] + '%' }"></div>
            </div>
          </div>
        </div>
      </section>

      <section class="hot-card">
        <div class="hot-head">
          <h3 class="chart-title">销售品类排行</h3>
          <span class="more-tip">TOP 8 分类</span>
        </div>
        <div v-if="!categoryShare.length" class="empty-tip">暂无销售数据</div>
        <div v-else class="hot-list">
          <div v-for="(c, i) in categoryShare" :key="i" class="hot-item">
            <span class="hot-rank" :class="`rank-${Math.min(i, 3)}`">{{ i + 1 }}</span>
            <div class="hot-info">
              <h4 class="hot-name">{{ c.name }}</h4>
              <p class="hot-meta">{{ c.orderCount }} 单</p>
            </div>
            <div class="hot-amount">
              <div class="amount-val">¥{{ Number(c.amount).toFixed(2) }}</div>
              <div class="amount-sold">{{ totalCategoryAmount > 0 ? ((Number(c.amount) / totalCategoryAmount) * 100).toFixed(1) : 0 }}%</div>
            </div>
          </div>
        </div>

        <div class="hot-metrics">
          <div v-for="m in bottomKpis" :key="m.label" class="metric">
            <span class="metric-label">{{ m.label }}</span>
            <span class="metric-val" :class="m.color">{{ m.value }} <span class="metric-unit">{{ m.unit }}</span></span>
          </div>
        </div>
      </section>
    </div>

    <section class="hot-prod-card">
      <div class="hot-head">
        <h3 class="chart-title">热销商品 TOP 10</h3>
        <span class="more-tip">按销量排序</span>
      </div>
      <div v-if="!hotProducts.length" class="empty-tip">暂无销售数据</div>
      <div v-else class="hot-prod-list">
        <div v-for="(p, i) in hotProducts" :key="p.id" class="hot-prod-item">
          <span class="hot-rank" :class="`rank-${Math.min(i, 3)}`">{{ i + 1 }}</span>
          <img v-if="p.main_image" :src="p.main_image" class="hot-thumb" alt="" @error="hideImg" />
          <span v-else class="hot-thumb ph"><span class="material-symbols-outlined">image</span></span>
          <div class="hot-info">
            <h4 class="hot-name">{{ p.name }}</h4>
            <p class="hot-meta">销量 {{ p.sale_count }} · 评分 {{ p.rating != null ? p.rating : '—' }}</p>
          </div>
          <div class="hot-amount">
            <div class="amount-val">¥{{ Number(p.sale_price).toFixed(2) }}</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.analytics-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: $spacing-md; }
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  .version {
    margin-left: $spacing-sm;
    padding: 2px $spacing-xs;
    background: rgba(0, 96, 172, 0.1);
    color: $color-secondary;
    border-radius: $radius-sm;
    font-size: $font-size-label-sm;
    font-weight: 600;
    vertical-align: middle;
  }
}
.page-sub { margin-top: 4px; font-size: $font-size-body-md; color: $color-on-surface-variant; }
.range-tabs { display: flex; gap: 4px; padding: 4px; background: $color-surface-container; border-radius: $radius-full; }
.range-tab {
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  cursor: pointer;
  &.active { background: $color-brand; color: #ffffff; font-weight: 700; }
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;
  @include desktop { grid-template-columns: repeat(4, 1fr); }
}
.kpi-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.kpi-icon {
  width: 48px; height: 48px;
  border-radius: $radius-md;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: $spacing-md;
  &.bg-orange { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.bg-blue { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  &.bg-gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.bg-red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
  .material-symbols-outlined { font-size: 24px; }
}
.kpi-label { font-size: $font-size-label-md; color: $color-on-surface-variant; margin-bottom: 4px; }
.kpi-value {
  font-family: $font-family-headline;
  font-size: 28px;
  font-weight: 700;
  color: $color-on-surface;
}
.empty-tip { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }

.middle-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  @include desktop { grid-template-columns: 2fr 1fr; }
}
.chart-card, .ring-card, .funnel-card, .hot-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.chart-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
}
.chart-sub { margin-top: 4px; font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.chart-head {
  display: flex; justify-content: space-between; align-items: flex-start;
  margin-bottom: $spacing-lg; flex-wrap: wrap; gap: $spacing-md;
}
.chart-legend { display: flex; gap: $spacing-lg;
  span { display: flex; align-items: center; gap: 4px; font-size: $font-size-label-sm; color: $color-on-surface-variant; }
}
.dot {
  width: 10px; height: 10px; border-radius: $radius-full;
  &.blue { background: $color-secondary; }
  &.orange { background: $color-brand; }
}
.chart-canvas { position: relative; width: 100%; }
.line-chart { width: 100%; height: 280px; }
.x-labels {
  display: flex; justify-content: space-between;
  padding-top: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.ring-wrap { display: flex; justify-content: center; margin: $spacing-md 0; }
.donut { width: 200px; height: 200px; }
.ring-legend {
  display: grid; grid-template-columns: 1fr 1fr;
  gap: $spacing-sm;
}
.legend-item {
  display: flex; align-items: center; gap: $spacing-xs;
  font-size: $font-size-label-sm; color: $color-on-surface;
}
.dot { width: 10px; height: 10px; border-radius: $radius-full; }

.funnel { display: flex; flex-direction: column; gap: $spacing-md; margin-top: $spacing-md; }
.funnel-step { display: flex; flex-direction: column; gap: 4px; }
.step-row { display: flex; align-items: center; gap: $spacing-md; flex-wrap: wrap; }
.step-num { font-weight: 700; color: $color-on-surface; min-width: 100px; }
.step-count { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.step-loss {
  padding: 2px $spacing-xs;
  background: rgba(255, 77, 79, 0.15);
  color: $color-emergency;
  border-radius: $radius-sm;
  font-size: 10px;
  font-weight: 700;
}
.step-bar { height: 8px; background: $color-surface-container; border-radius: $radius-full; overflow: hidden; }
.step-fill { height: 100%; background: $color-secondary; border-radius: $radius-full; transition: width $transition-base; }

.bottom-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  @include desktop { grid-template-columns: 1fr 1fr; }
}
.hot-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: $spacing-md;
}
.more-tip { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.hot-list { display: flex; flex-direction: column; gap: $spacing-md; }
.hot-item { display: flex; align-items: center; gap: $spacing-md; }
.hot-rank {
  width: 28px; height: 28px;
  display: flex; align-items: center; justify-content: center;
  border-radius: $radius-full;
  background: $color-surface-container;
  color: $color-on-surface-variant;
  font-size: $font-size-label-sm; font-weight: 700;
  &.rank-0 { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.rank-1 { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  &.rank-2 { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
}
.hot-info { flex: 1; min-width: 0; }
.hot-name { font-weight: 700; color: $color-on-surface; font-size: $font-size-body-md; @include text-ellipsis(1); }
.hot-meta { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.hot-amount { text-align: right; }
.amount-val { font-family: $font-family-headline; font-size: $font-size-body-lg; font-weight: 700; color: $color-brand; }
.amount-sold { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.hot-metrics {
  display: grid; grid-template-columns: repeat(3, 1fr);
  gap: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1px solid $color-outline-variant;
  margin-top: $spacing-md;
}
.metric { display: flex; flex-direction: column; align-items: center; }
.metric-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; margin-bottom: 4px; }
.metric-val {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  &.red { color: $color-emergency; }
  &.gold { color: $color-warning; }
  &.green { color: $color-success; }
}
.metric-unit { font-size: $font-size-label-sm; font-weight: 400; color: $color-on-surface-variant; }

.hot-prod-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.hot-prod-list { display: flex; flex-direction: column; gap: $spacing-sm; margin-top: $spacing-md; }
.hot-prod-item { display: flex; align-items: center; gap: $spacing-md; }
.hot-thumb {
  width: 48px; height: 48px; border-radius: $radius-default;
  object-fit: cover; flex-shrink: 0;
  background: $color-surface-container;
  display: inline-flex; align-items: center; justify-content: center;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 22px; }
}

.head-controls { display: flex; flex-direction: column; align-items: flex-end; gap: $spacing-sm; }
.updated { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.refresh-btn {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  background: $color-surface-container-lowest;
  color: $color-on-surface;
  font-size: $font-size-label-sm;
  cursor: pointer;
  transition: all $transition-base;
}
.refresh-btn:hover:not(:disabled) { border-color: $color-brand; color: $color-brand; }
.refresh-btn:disabled { opacity: 0.6; cursor: default; }
.refresh-btn .material-symbols-outlined { font-size: 18px; }

.funnel-tip {
  display: flex; align-items: center; gap: $spacing-xs;
  margin-bottom: $spacing-md;
  padding: $spacing-sm $spacing-md;
  background: rgba(0, 96, 172, 0.08);
  color: $color-secondary;
  border-radius: $radius-sm;
  font-size: $font-size-label-sm;
}
.funnel-tip .material-symbols-outlined { font-size: 18px; }
.funnel-step.muted { opacity: 0.55; }
.funnel-step.muted .step-fill { background: $color-outline-variant; }

.kpi-card { transition: transform $transition-base, box-shadow $transition-base; }
.kpi-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.10); }
</style>