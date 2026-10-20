<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'

const range = ref<'7d' | '30d' | '90d'>('7d')
const loading = ref(false)
const data = ref({
  pv: 0,
  uv: 0,
  cartCount: 0,
  orderCount: 0,
  paidCount: 0,
})

interface FunnelStep {
  key: string
  label: string
  value: number
  color: string
}

const steps = ref<FunnelStep[]>([])
const cvr = ref('0%')

async function load() {
  loading.value = true
  try {
    const r = await merchantApi.convert(range.value)
    data.value = {
      pv: r.pv,
      uv: r.uv,
      cartCount: r.cartCount,
      orderCount: r.orderCount,
      paidCount: r.paidCount,
    }
    const base = r.pv || 1
    // UV 真实依赖埋点接入，演示态后端恒 0；为 0 时把「访客数」与「商品浏览」合并为一栏，
    // 避免出现「断崖式 0 占比」的误导视图，同时在下方说明栏告知原因。
    if (!r.uv || r.uv <= 0) {
      steps.value = [
        { key: 'pv', label: '商品浏览 (PV)', value: r.pv, color: '#a14000' },
        { key: 'cart', label: '加入购物车', value: r.cartCount, color: '#faad14' },
        { key: 'order', label: '提交订单', value: r.orderCount, color: '#52c41a' },
        { key: 'paid', label: '完成支付', value: r.paidCount, color: '#cb9900' },
      ]
    } else {
      steps.value = [
        { key: 'pv', label: '商品浏览 (PV)', value: r.pv, color: '#a14000' },
        { key: 'uv', label: '访客数 (UV)', value: r.uv, color: '#0060ac' },
        { key: 'cart', label: '加入购物车', value: r.cartCount, color: '#faad14' },
        { key: 'order', label: '提交订单', value: r.orderCount, color: '#52c41a' },
        { key: 'paid', label: '完成支付', value: r.paidCount, color: '#cb9900' },
      ]
    }
    cvr.value = ((r.paidCount / base) * 100).toFixed(2) + '%'
  } catch (e: any) {
    ElMessage.error(e?.message || '漏斗数据加载失败')
  } finally {
    loading.value = false
  }
}

function pct(step: FunnelStep) {
  const base = data.value.pv || 1
  return ((step.value / base) * 100).toFixed(2) + '%'
}

const dropRates = computed(() => {
  const out: Array<{ from: string; to: string; rate: string }> = []
  for (let i = 1; i < steps.value.length; i++) {
    const prev = steps.value[i - 1].value
    const cur = steps.value[i].value
    if (prev > 0) {
      const r = ((cur / prev) * 100).toFixed(1) + '%'
      out.push({ from: steps.value[i - 1].label, to: steps.value[i].label, rate: r })
    } else {
      out.push({ from: steps.value[i - 1].label, to: steps.value[i].label, rate: '—' })
    }
  }
  return out
})

onMounted(load)
</script>

<template>
  <div class="convert-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">转化漏斗</h1>
        <p class="page-sub">整体转化率 {{ cvr }}（支付 / 浏览）<span v-if="!data.uv" class="uv-hint"> · UV 依赖埋点接入，演示态已隐藏</span></p>
      </div>
      <div class="range-tabs">
        <button v-for="r in (['7d', '30d', '90d'] as const)" :key="r" class="range-tab" :class="{ active: range === r }" @click="range = r; load()">
          {{ r === '7d' ? '近 7 天' : r === '30d' ? '近 30 天' : '近 90 天' }}
        </button>
      </div>
    </div>

    <section class="funnel-card">
      <div v-for="(s, i) in steps" :key="s.key" class="funnel-row">
        <div class="funnel-label">
          <span class="funnel-index">第 {{ i + 1 }} 步</span>
          <span class="funnel-name">{{ s.label }}</span>
        </div>
        <div class="funnel-bar-wrap">
          <div class="funnel-bar" :style="{ width: pct(s), background: s.color }">
            <span class="funnel-value">{{ s.value.toLocaleString() }}</span>
          </div>
        </div>
        <span class="funnel-pct">{{ pct(s) }}</span>
      </div>
    </section>

    <section class="drop-card">
      <h2 class="chart-title">各步转化率</h2>
      <table class="drop-table">
        <thead>
          <tr>
            <th>从</th>
            <th>到</th>
            <th>转化率</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(d, i) in dropRates" :key="i">
            <td>{{ d.from }}</td>
            <td>{{ d.to }}</td>
            <td class="rate">{{ d.rate }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.convert-view { display: flex; flex-direction: column; gap: $spacing-md; }

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

  .uv-hint {
    color: $color-on-surface-variant;
    font-size: $font-size-label-sm;
    opacity: 0.75;
  }
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

.funnel-card,
.drop-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.funnel-card {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.funnel-row {
  display: grid;
  grid-template-columns: 200px 1fr 80px;
  gap: $spacing-md;
  align-items: center;
}

.funnel-label {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.funnel-index {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.funnel-name {
  font-weight: 700;
  color: $color-on-surface;
}

.funnel-bar-wrap {
  width: 100%;
  height: 36px;
  background: $color-surface-container;
  border-radius: $radius-md;
  overflow: hidden;
}

.funnel-bar {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 $spacing-md;
  min-width: 80px;
  border-radius: $radius-md;
  transition: width 0.3s ease;
}

.funnel-value {
  color: #ffffff;
  font-weight: 700;
  font-size: $font-size-label-md;
}

.funnel-pct {
  text-align: right;
  font-family: $font-family-headline;
  font-weight: 700;
  color: $color-on-surface;
}

.chart-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}

.drop-table {
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
    font-size: $font-size-body-md;
    color: $color-on-surface;
  }

  .rate {
    font-weight: 700;
    color: $color-brand;
  }
}

@media (max-width: 768px) {
  .funnel-row {
    grid-template-columns: 1fr;
  }
}
</style>
