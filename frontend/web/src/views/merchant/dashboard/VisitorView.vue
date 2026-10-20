<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi, type SalesData } from '@/api/modules/merchant'

const range = ref<'7d' | '30d' | '90d'>('7d')
const loading = ref(false)
const visitor = ref<SalesData[]>([])

const days = computed(() => visitor.value.map((d) => d.date))
const counts = computed(() => visitor.value.map((d) => d.visitorCount))
const total = computed(() => counts.value.reduce((a, b) => a + b, 0))
const peak = computed(() => {
  let max = 0
  let idx = 0
  counts.value.forEach((v, i) => {
    if (v > max) { max = v; idx = i }
  })
  return idx
})
const avg = computed(() => counts.value.length ? Math.round(total.value / counts.value.length) : 0)

function buildBars(values: number[]): { line: string; area: string } {
  const w = 700
  const h = 280
  const top = 20
  const bottom = 40
  if (!values.length) return { line: '', area: '' }
  const max = Math.max(...values, 1)
  const stepX = values.length > 1 ? w / (values.length - 1) : w
  const points = values.map((v, i) => ({
    x: i * stepX,
    y: top + (1 - v / max) * (h - top - bottom),
  }))
  const line = points
    .map((p, i, arr) => {
      if (i === 0) return `M ${p.x.toFixed(1)} ${p.y.toFixed(1)}`
      const prev = arr[i - 1]
      const cx = (prev.x + p.x) / 2
      return `Q ${cx.toFixed(1)} ${prev.y.toFixed(1)} ${cx.toFixed(1)} ${((prev.y + p.y) / 2).toFixed(1)} T ${p.x.toFixed(1)} ${p.y.toFixed(1)}`
    })
    .join(' ')
  const area = `${line} L ${w} ${h} L 0 ${h} Z`
  return { line, area }
}

const paths = computed(() => buildBars(counts.value))

async function load() {
  loading.value = true
  try {
    const data = await merchantApi.visitor(range.value)
    visitor.value = data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '客流数据加载失败')
    visitor.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="visitor-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">客流分析</h1>
        <p class="page-sub">访客访问量与来源构成</p>
      </div>
      <div class="range-tabs">
        <button v-for="r in (['7d', '30d', '90d'] as const)" :key="r" class="range-tab" :class="{ active: range === r }" @click="range = r; load()">
          {{ r === '7d' ? '近 7 天' : r === '30d' ? '近 30 天' : '近 90 天' }}
        </button>
      </div>
    </div>

    <div class="kpi-grid">
      <div class="kpi-card">
        <p class="kpi-label">总访客</p>
        <h3 class="kpi-value">{{ total.toLocaleString() }}</h3>
      </div>
      <div class="kpi-card">
        <p class="kpi-label">日均访客</p>
        <h3 class="kpi-value">{{ avg.toLocaleString() }}</h3>
      </div>
      <div class="kpi-card">
        <p class="kpi-label">峰值日</p>
        <h3 class="kpi-value">{{ peak >= 0 ? days[peak] : '—' }}</h3>
      </div>
      <div class="kpi-card">
        <p class="kpi-label">峰值访客</p>
        <h3 class="kpi-value">{{ peak >= 0 ? counts[peak].toLocaleString() : '—' }}</h3>
      </div>
    </div>

    <section class="chart-card">
      <h2 class="chart-title">访客访问趋势</h2>
      <div class="chart-canvas">
        <svg viewBox="0 0 700 280" preserveAspectRatio="none" class="line-svg">
          <defs>
            <linearGradient id="grad-vis" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stop-color="#0060ac" stop-opacity="0.4" />
              <stop offset="100%" stop-color="#0060ac" stop-opacity="0" />
            </linearGradient>
          </defs>
          <path :d="paths.line" fill="none" stroke="#0060ac" stroke-width="3" />
          <path :d="paths.area" fill="url(#grad-vis)" opacity="0.3" />
        </svg>
        <div class="chart-x-labels">
          <span v-for="d in days" :key="d">{{ d.slice(5) }}</span>
        </div>
      </div>
    </section>

    <section class="source-card">
      <h2 class="chart-title">访客来源</h2>
      <p class="empty-tip">访客来源统计依赖前端埋点接入,当前未部署埋点采集,暂不展示。</p>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.visitor-view { display: flex; flex-direction: column; gap: $spacing-md; }

.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
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

.range-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: $color-surface-container;
  border-radius: $radius-full;
}

.range-tab {
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  cursor: pointer;

  &.active {
    background: $color-brand;
    color: #ffffff;
    font-weight: 700;
  }
}

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
  }
}

.kpi-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.kpi-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

.kpi-value {
  margin-top: 4px;
  font-family: $font-family-headline;
  font-size: 28px;
  font-weight: 700;
  color: $color-on-surface;
}

.chart-card,
.source-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.chart-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}

.chart-canvas {
  position: relative;
  width: 100%;
  height: 280px;
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

.source-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.source-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.source-dot {
  width: 10px;
  height: 10px;
  border-radius: $radius-full;
  flex-shrink: 0;
}

.source-name {
  width: 80px;
  font-size: $font-size-label-md;
  color: $color-on-surface;
}

.source-bar {
  flex: 1;
  height: 8px;
  background: $color-surface-container;
  border-radius: $radius-full;
  overflow: hidden;
}

.source-bar-fill {
  display: block;
  height: 100%;
  border-radius: $radius-full;
  transition: width 0.3s ease;
}

.source-count {
  width: 80px;
  text-align: right;
  font-family: $font-family-headline;
  font-weight: 700;
  color: $color-on-surface;
}
</style>
