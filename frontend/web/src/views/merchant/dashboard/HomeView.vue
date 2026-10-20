<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { merchantApi, type MerchantAlert, type MerchantHeatmapItem } from '@/api/modules/merchant'

const router = useRouter()

interface KpiItem {
  label: string
  value: string
  trend: string
  icon: string
  color: string
}

const loading = ref(false)

const kpis = ref<KpiItem[]>([])
const chartData = ref<{ revenue: number[]; traffic: number[]; users: number[] }>({
  revenue: [],
  traffic: [],
  users: [],
})
const days = ref<string[]>([])
const chartMode = ref<'revenue' | 'traffic' | 'users'>('revenue')

const currentData = computed(() => chartData.value[chartMode.value])
const maxVal = computed(() =>
  currentData.value.length ? Math.max(...currentData.value) : 0,
)

// KPI 卡底部小柱条高度：以最近 7 天销售额的峰值为基准自适应，
// 避免销量上涨后小柱被固定高度撑爆；空数据时给到 2px 占位。
const kpiBarMax = computed(() => Math.max(1, maxVal.value))

/** 将一组数值映射到 SVG y 轴（280 高度，留 20 顶部余量），并平滑连线 */
function buildPath(values: number[]): { line: string; area: string } {
  const w = 700
  const h = 280
  const topPad = 20
  const bottomPad = 40
  if (!values.length) return { line: '', area: '' }
  const max = Math.max(...values, 1)
  const n = values.length
  const stepX = n > 1 ? w / (n - 1) : w
  const points = values.map((v, i) => {
    const x = i * stepX
    const y = topPad + (1 - v / max) * (h - topPad - bottomPad)
    return { x, y }
  })
  // 平滑曲线 (Catmull-Rom → Bezier)
  const line = points
    .map((p, i, arr) => {
      if (i === 0) return `M ${p.x.toFixed(1)} ${p.y.toFixed(1)}`
      const prev = arr[i - 1]
      const cx = (prev.x + p.x) / 2
      return `Q ${cx.toFixed(1)} ${prev.y.toFixed(1)}, ${cx.toFixed(1)} ${((prev.y + p.y) / 2).toFixed(1)} T ${p.x.toFixed(1)} ${p.y.toFixed(1)}`
    })
    .join(' ')
  const area = `${line} L ${w} ${h} L 0 ${h} Z`
  return { line, area }
}

function linePath(mode: 'revenue' | 'traffic' | 'users') {
  return buildPath(chartData.value[mode]).line
}
function areaPath(mode: 'revenue' | 'traffic' | 'users') {
  return buildPath(chartData.value[mode]).area
}

const alerts = ref<MerchantAlert[]>([])
const heatmap = ref<MerchantHeatmapItem[]>([])

const peakDay = computed(() => {
  const peak = heatmap.value.find((d) => d.peak)
  return peak ? peak.day : ''
})

const aiInsight = computed(() => {
  if (!heatmap.value.length) return '暂无活跃数据, 请稍后再来查看。'
  const total = heatmap.value.reduce((s, d) => s + d.rawCount, 0)
  const peak = heatmap.value.find((d) => d.peak)
  if (!peak || total === 0) return '本周互动较少, 建议加大营销推广, 提升订单与预约量。'
  return `本周「${peak.day}」互动量最高（${peak.rawCount} 次），建议在该日前后安排活动/优惠投放。整体 7 天累计 ${total} 次有效互动。`
})

// 图表底部三项指标，无单独接口字段，沿用 dashboard 派生
const weeklySales = computed(() => {
  const total = chartData.value.revenue.reduce((a, b) => a + b, 0)
  return `¥${total.toLocaleString('zh-CN')}`
})
const avgOrder = computed(() => {
  const total = chartData.value.revenue.reduce((a, b) => a + b, 0)
  const traffic = chartData.value.traffic.reduce((a, b) => a + b, 0)
  if (!traffic) return '¥0.00'
  return `¥${(total / traffic).toFixed(2)}`
})
const conversionRate = ref('0%')

// 底部趋势：基于最近 7 天和前 7 天对比（演示态无前 7 天数据 → 留空）
const weeklyTrend = ref('')        // 本周销售环比
const avgOrderTrend = ref('')      // 客单价环比
const cvrTrend = ref('')           // 转化率环比

const now = ref(new Date().toLocaleString('zh-CN'))

async function loadDashboard() {
  loading.value = true
  try {
    const [kpi, sales, visitor, convert, alertData, heatmapData] = await Promise.all([
      merchantApi.dashboard(),
      merchantApi.sales('7d'),
      merchantApi.visitor('7d'),
      merchantApi.convert('7d'),
      merchantApi.alerts(),
      merchantApi.heatmap(),
    ])

    kpis.value = [
      {
        label: '今日订单',
        value: String(kpi.todayOrders),
        trend: '',
        icon: 'shopping_bag',
        color: 'gold',
      },
      {
        label: '今日销售额',
        value: `¥${Number(kpi.todaySales).toLocaleString('zh-CN')}`,
        trend: '',
        icon: 'payments',
        color: 'blue',
      },
      {
        label: '待处理订单',
        value: String(kpi.pendingOrders),
        trend: '',
        icon: 'rule',
        color: 'red',
      },
      {
        label: '今日新增客户',
        value: String(kpi.newCustomers),
        trend: '',
        icon: 'group',
        color: 'orange',
      },
    ]

    // 销售看板数据（日期 / 销售额 / 流量 / 用户）
    const dayLabels = sales.map((d) => d.date)
    days.value = dayLabels
    chartData.value.revenue = sales.map((d) => d.salesAmount)
    // 流量与用户量来自 visitor 接口（按日期）
    chartData.value.traffic = visitor.map((d) => d.visitorCount)
    chartData.value.users = sales.map((d) => d.orderCount)

    if (convert.uv > 0) {
      const rate = (convert.paidCount / convert.uv) * 100
      conversionRate.value = `${rate.toFixed(2)}%`
    } else {
      conversionRate.value = '0%'
    }

    alerts.value = alertData || []
    heatmap.value = heatmapData || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载商家仪表盘失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadDashboard)
</script>

<template>
  <div class="store-view">
    <!-- 标题 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">门店数据看板</h1>
        <p class="page-sub">实时更新至 {{ now }}</p>
      </div>
    </div>

    <!-- KPI -->
    <div v-loading="loading" class="kpi-grid">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-icon" :class="`bg-${k.color}`">
          <span class="material-symbols-outlined">{{ k.icon }}</span>
        </div>
        <span class="kpi-trend" :class="`text-${k.color}`">{{ k.trend }}</span>
        <p class="kpi-label">{{ k.label }}</p>
        <h3 class="kpi-value">{{ k.value }}</h3>
        <div class="kpi-bars">
          <span
            v-for="(d, i) in chartData.revenue.slice(0, 6)"
            :key="i"
            class="kpi-bar"
            :style="{ height: `${Math.max(2, (d / kpiBarMax) * 24)}px` }"
          ></span>
        </div>
      </div>
    </div>

    <!-- 主图区 -->
    <section class="chart-card">
      <div class="chart-head">
        <div>
          <h2 class="chart-title">全平台流量与营收趋势</h2>
          <p class="chart-sub">实时更新至 {{ now }}</p>
        </div>
        <div class="chart-tabs">
          <button
            v-for="t in ['revenue', 'traffic', 'users'] as const"
            :key="t"
            class="chart-tab"
            :class="{ active: chartMode === t }"
            @click="chartMode = t"
          >
            {{ t === 'revenue' ? '营收' : t === 'traffic' ? '流量' : '用户' }}
          </button>
        </div>
      </div>

      <!-- 折线图（SVG） -->
      <div class="chart-canvas">
        <svg viewBox="0 0 700 280" preserveAspectRatio="none" class="line-svg">
          <defs>
            <linearGradient id="grad-rev" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#a14000" stop-opacity="0.4" />
              <stop offset="100%" stop-color="#a14000" stop-opacity="0" />
            </linearGradient>
          </defs>

          <!-- 营收曲线（实线 + 渐变面积） -->
          <template v-if="chartMode === 'revenue'">
            <path
              :d="linePath('revenue')"
              fill="none"
              stroke="#a14000"
              stroke-width="3"
            />
            <path
              :d="areaPath('revenue')"
              fill="url(#grad-rev)"
              opacity="0.3"
            />
          </template>

          <!-- 流量曲线（虚线） -->
          <path
            v-if="chartMode === 'traffic'"
            :d="linePath('traffic')"
            fill="none"
            stroke="#0060ac"
            stroke-width="3"
            stroke-dasharray="6 4"
          />

          <!-- 用户曲线 -->
          <path
            v-if="chartMode === 'users'"
            :d="linePath('users')"
            fill="none"
            stroke="#cb9900"
            stroke-width="3"
          />
        </svg>
        <div class="chart-x-labels">
          <span v-for="d in days" :key="d">{{ d }}</span>
        </div>
      </div>

      <!-- 底部 3 项指标 -->
      <div class="chart-bottom">
        <div class="bottom-item">
          <span class="bottom-dot orange"></span>
          <div>
            <span class="bottom-label">本周销售汇总</span>
            <span class="bottom-val">
              {{ weeklySales }}
              <span v-if="weeklyTrend" class="trend-up">↑ {{ weeklyTrend }}</span>
            </span>
          </div>
        </div>
        <div class="bottom-item">
          <span class="bottom-dot blue"></span>
          <div>
            <span class="bottom-label">平均客单价</span>
            <span class="bottom-val">
              {{ avgOrder }}
              <span v-if="avgOrderTrend" class="trend-up">↑ {{ avgOrderTrend }}</span>
            </span>
          </div>
        </div>
        <div class="bottom-item">
          <span class="bottom-dot gold"></span>
          <div>
            <span class="bottom-label">转化率 (CVR)</span>
            <span class="bottom-val">
              {{ conversionRate }}
              <span v-if="cvrTrend" :class="cvrTrend.startsWith('-') ? 'trend-down' : 'trend-up'">
                {{ cvrTrend.startsWith('-') ? '↓ ' + cvrTrend.slice(1) : '↑ ' + cvrTrend }}
              </span>
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 内容审核 + 用户活跃 -->
    <section class="bottom-row">
      <div class="alerts-card">
        <div class="alerts-head">
          <div class="alerts-title-row">
            <span class="material-symbols-outlined alert-icon">warning</span>
            <h3>内容审核预警列表</h3>
          </div>
          <a class="more" @click="router.push('/merchant/review')">查看全部</a>
        </div>
        <table v-if="alerts.length" class="alerts-table">
          <thead>
            <tr>
              <th>触发类型</th>
              <th>内容摘要</th>
              <th>发布者</th>
              <th>热度</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(a, i) in alerts" :key="a.id">
              <td><span class="alert-tag" :class="a.tag">{{ a.type }}</span></td>
              <td class="alert-content">{{ a.content }}</td>
              <td>
                <div class="user-cell">
                  <span class="user-dot" :class="`u${(i % 3) + 1}`">{{ (a.user || '?').charAt(0) }}</span>
                  {{ a.user }}
                </div>
              </td>
              <td class="alert-views">{{ a.views }}</td>
              <td><a class="alert-action" @click="router.push('/merchant/review')">查看</a></td>
            </tr>
          </tbody>
        </table>
        <div v-else class="empty">暂无预警</div>
      </div>

      <div class="heatmap-card">
        <div class="heatmap-head">
          <h3 class="heatmap-title">
            <span class="material-symbols-outlined">monitoring</span>
            用户活跃热力图
          </h3>
          <p class="heatmap-sub">最近 7 天活跃度（订单 + 预约）</p>
        </div>
        <div v-if="heatmap.length" class="heatmap-grid">
          <div
            v-for="d in heatmap"
            :key="d.date"
            class="heat-cell"
            :class="{ peak: d.peak }"
            :style="{ background: d.peak ? 'rgba(255, 77, 79, 0.85)' : `rgba(255, 122, 50, ${Math.max(d.value, 8) / 100})` }"
          >
            <span class="heat-val">{{ d.value }}%</span>
            <span class="heat-day">{{ d.day }}</span>
          </div>
        </div>
        <div v-else class="empty">暂无活跃数据</div>
        <p class="heatmap-peak">活跃高峰: {{ peakDay || '—' }}</p>
        <div class="ai-insight">
          <span class="material-symbols-outlined">auto_awesome</span>
          <p><strong>AI 洞察:</strong> {{ aiInsight }}</p>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.store-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.page-sub {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

// ============ KPI ============
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
  }
}

.kpi-card {
  position: relative;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.kpi-icon {
  width: 36px;
  height: 36px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;

  &.bg-orange { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.bg-blue { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  &.bg-gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.bg-red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.kpi-trend {
  position: absolute;
  top: $spacing-lg;
  right: $spacing-lg;
  font-size: $font-size-label-sm;
  font-weight: 700;

  &.text-orange { color: $color-success; }
  &.text-blue { color: $color-secondary; }
  &.text-gold { color: $color-text-tertiary; }
  &.text-red { color: $color-emergency; }
}

.kpi-label {
  margin-top: $spacing-sm;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

.kpi-value {
  font-family: $font-family-headline;
  font-size: 28px;
  font-weight: 700;
  color: $color-on-surface;
  margin-top: 4px;
}

.kpi-bars {
  display: flex;
  gap: 4px;
  align-items: flex-end;
  margin-top: $spacing-md;
  height: 24px;
}

.kpi-bar {
  flex: 1;
  background: $color-brand-container;
  border-radius: 2px 2px 0 0;
}

// ============ Chart ============
.chart-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.chart-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $spacing-md;
  flex-wrap: wrap;
  gap: $spacing-md;
}

.chart-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

.chart-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 4px;
}

.chart-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: $color-surface-container;
  border-radius: $radius-full;
}

.chart-tab {
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  cursor: pointer;

  &.active {
    background: #ffffff;
    color: $color-brand;
    font-weight: 700;
    box-shadow: $shadow-sm;
  }
}

.chart-canvas {
  position: relative;
  width: 100%;
  height: 280px;
  margin: $spacing-md 0;
}

.line-svg {
  width: 100%;
  height: 100%;
}

.chart-x-labels {
  display: flex;
  justify-content: space-between;
  padding-top: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.chart-bottom {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1px solid $color-outline-variant;

  @include tablet {
    grid-template-columns: repeat(3, 1fr);
  }
}

.bottom-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.bottom-dot {
  width: 10px;
  height: 10px;
  border-radius: $radius-full;

  &.orange { background: $color-brand; }
  &.blue { background: $color-secondary; }
  &.gold { background: $color-warning; }
}

.bottom-label {
  display: block;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.bottom-val {
  display: block;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}

.trend-up {
  font-size: $font-size-label-sm;
  color: $color-success;
  margin-left: $spacing-xs;
}

.trend-down {
  font-size: $font-size-label-sm;
  color: $color-emergency;
  margin-left: $spacing-xs;
}

// ============ Bottom Row ============
.bottom-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: 2fr 1fr;
  }
}

.alerts-card,
.heatmap-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.alerts-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: $spacing-md;
}

.alerts-title-row {
  display: flex;
  align-items: center;
  gap: $spacing-xs;

  h3 {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 600;
    color: $color-on-surface;
  }

  .alert-icon {
    color: $color-emergency;
  }
}

.more {
  font-size: $font-size-label-md;
  color: $color-brand;
  cursor: pointer;
}

.alerts-table {
  width: 100%;
  border-collapse: collapse;

  th {
    text-align: left;
    padding: $spacing-sm;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    border-bottom: 1px solid $color-outline-variant;
  }

  td {
    padding: $spacing-sm;
    border-bottom: 1px solid $color-outline-variant;
    font-size: $font-size-label-sm;
  }
}

.alert-tag {
  padding: 2px $spacing-xs;
  border-radius: $radius-sm;
  font-weight: 700;

  &.red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
  &.gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.blue { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
}

.alert-content {
  color: $color-on-surface;
  max-width: 240px;
  @include text-ellipsis(1);
}

.user-cell {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

.user-dot {
  width: 28px;
  height: 28px;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 700;
  color: #ffffff;

  &.u1 { background: $color-secondary; }
  &.u2 { background: $color-warning; }
  &.u3 { background: $color-brand; }
}

.alert-views {
  font-weight: 700;
  color: $color-on-surface;
}

.alert-action {
  color: $color-brand;
  cursor: pointer;
}

// Heatmap
.heatmap-head {
  margin-bottom: $spacing-md;
}

.heatmap-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;

  .material-symbols-outlined {
    color: $color-secondary;
  }
}

.heatmap-sub {
  margin-top: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.heatmap-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: $spacing-xs;
  margin-bottom: $spacing-sm;
}

.heat-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-md $spacing-xs;
  border-radius: $radius-default;
  color: #ffffff;
  aspect-ratio: 1;
}

.heat-val {
  font-size: $font-size-label-md;
  font-weight: 700;
}

.heat-day {
  font-size: 10px;
  opacity: 0.8;
}

.heatmap-peak {
  text-align: right;
  font-size: $font-size-label-sm;
  color: $color-emergency;
  font-weight: 700;
  margin-bottom: $spacing-sm;
}

.ai-insight {
  display: flex;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: rgba(0, 96, 172, 0.05);
  border-radius: $radius-md;

  .material-symbols-outlined {
    color: $color-secondary;
    flex-shrink: 0;
  }

  p {
    font-size: $font-size-label-sm;
    color: $color-on-surface;
    line-height: 1.6;
  }
}

.empty {
  padding: $spacing-md;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
}
</style>