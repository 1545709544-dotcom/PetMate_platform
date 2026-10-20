<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi, type Review as ApiReview } from '@/api/modules/merchant'

interface Review extends ApiReview {
  status: 'pending' | 'replied' | 'low' | 'appeal'
}

const loading = ref(false)
const activeTab = ref('all')
const activeSource = ref<'product' | 'service'>('product')

const reviews = ref<Review[]>([])

const tabs = [
  { id: 'all', label: '全部评价' },
  { id: 'pending', label: '待回复' },
  { id: 'low', label: '低分评价' },
]

const kpis = ref<Array<{ label: string; value: string; trend?: string; sub?: string; action?: string; actionTab?: string; icon?: string; color: string }>>([])

const sortBy = ref<'time' | 'rating'>('time')

function mapStatus(r: ApiReview): Review['status'] {
  if (r.reply) return 'replied'
  if (r.rating <= 2) return 'low'
  return 'pending'
}

function formatDate(t: string) {
  const d = new Date(String(t).replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return t
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadReviews() {
  loading.value = true
  try {
    let raw: any[]
    if (activeSource.value === 'service') {
      const r = await merchantApi.serviceReviews({ page: 1, size: 50 })
      raw = r.list.map((x: any) => ({
        id: x.id,
        userId: x.userId,
        userNickname: x.nickname,
        orderId: undefined as unknown as number,
        serviceName: x.serviceName,
        specName: x.specName,
        rating: x.rating,
        content: x.content,
        images: x.images,
        reply: x.reply,
        repliedAt: undefined as unknown as string,
        createdAt: x.createdAt,
      }))
    } else {
      raw = await merchantApi.reviews()
    }
    reviews.value = raw.map((r) => ({ ...r, status: mapStatus(r) }))

    // 同步 KPI
    const total = raw.length
    const pending = raw.filter((r) => !r.reply).length
    const low = raw.filter((r) => r.rating <= 2).length
    const avg = total
      ? (raw.reduce((sum: number, r: any) => sum + r.rating, 0) / total).toFixed(1)
      : '0.0'
    kpis.value = [
      { label: '累计评价', value: total.toLocaleString('zh-CN'), color: 'orange' },
      { label: '平均评分', value: avg, color: 'gold' },
      { label: '待回复评价', value: String(pending), action: '立即处理', actionTab: 'pending', color: 'orange' },
      { label: '低分评价', value: String(low), icon: 'warning', color: 'blue' },
    ]
  } catch (e: any) {
    ElMessage.error(e?.message || '加载评价列表失败')
  } finally {
    loading.value = false
  }
}

const filteredReviews = computed(() => {
  let list = reviews.value
  if (activeTab.value === 'pending') list = list.filter((r) => r.status === 'pending')
  else if (activeTab.value === 'low') list = list.filter((r) => r.status === 'low')
  // 排序
  if (sortBy.value === 'rating') return [...list].sort((a, b) => a.rating - b.rating)
  return [...list].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
})

const replyTemplates = ref<string[]>(JSON.parse(localStorage.getItem('petmate-reply-templates') || '[]'))

async function reply(r: Review, prefill = '') {
  try {
    const { value } = await ElMessageBox.prompt('请输入回复内容', '回复评价', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputValue: prefill,
      inputValidator: (val) => (val && val.trim() ? true : '回复内容不能为空'),
    })
    if (activeSource.value === 'service') await merchantApi.replyServiceReview(r.id, value.trim())
    else await merchantApi.replyReview(r.id, value.trim())
    r.reply = value.trim()
    r.repliedAt = new Date().toISOString()
    r.status = 'replied'
    ElMessage.success('回复成功')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '回复失败')
  }
}

function toggleSort() {
  sortBy.value = sortBy.value === 'time' ? 'rating' : 'time'
}

function kpiAction(tab?: string) {
  if (tab) activeTab.value = tab
}

function switchSource(s: 'product' | 'service') {
  if (activeSource.value === s) return
  activeSource.value = s
  loadReviews()
}

const DEFAULT_TEMPLATES = [
  '感谢您的好评！我们会继续努力，为您提供更优质的产品和服务~',
  '非常抱歉给您带来不好的体验，已为您处理，如有问题请随时联系客服~',
  '感谢您的反馈！我们已记录并改进，期待您的下次光临~',
]

async function replyWithTemplate(r: Review) {
  const all = [...DEFAULT_TEMPLATES, ...replyTemplates.value]
  if (!all.length) { reply(r); return }
  try {
    const { value: idx } = await ElMessageBox.prompt(
      '选择一个回复模板（输入序号），或直接关闭手动输入',
      '快捷回复模板',
      {
        inputType: 'text',
        inputValue: '1',
        inputValidator: (v) => (Number(v) >= 1 && Number(v) <= all.length) || `请输入 1-${all.length}`,
        confirmButtonText: '使用模板',
        cancelButtonText: '手动输入',
      },
    )
    reply(r, all[Number(idx) - 1])
  } catch {
    reply(r)
  }
}

onMounted(loadReviews)
</script>

<template>
  <div class="review-view" v-loading="loading">
    <div class="page-head">
      <h1 class="page-title">评价管理</h1>
      <p class="page-sub">查看、回复并分析客户的真实反馈，持续提升服务品质。</p>
    </div>

    <!-- 评价来源切换 -->
    <div class="tabs-row" style="margin-bottom: 16px">
      <div class="tabs">
        <button
          class="tab"
          :class="{ active: activeSource === 'product' }"
          @click="switchSource('product')"
        >商品评价</button>
        <button
          class="tab"
          :class="{ active: activeSource === 'service' }"
          @click="switchSource('service')"
        >服务评价</button>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpi-grid">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <p class="kpi-label">{{ k.label }}</p>
        <h3 class="kpi-value">{{ k.value }}</h3>
        <div class="kpi-foot">
          <span v-if="k.trend" class="kpi-trend">↗{{ k.trend }}</span>
          <span v-if="k.sub" class="kpi-sub">⭐ {{ k.sub }}</span>
          <button v-if="k.action && k.actionTab" class="kpi-action" @click="kpiAction(k.actionTab)">{{ k.action }}</button>
          <span v-if="k.icon" class="material-symbols-outlined kpi-icon">{{ k.icon }}</span>
        </div>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tabs-row">
      <div class="tabs">
        <button
          v-for="t in tabs"
          :key="t.id"
          class="tab"
          :class="{ active: activeTab === t.id }"
          @click="activeTab = t.id"
        >
          {{ t.label }}
        </button>
      </div>
      <div class="tools">
        <button class="tool-btn" @click="toggleSort">
          {{ sortBy === 'rating' ? '按评分' : '按时间' }} <span class="material-symbols-outlined">expand_more</span>
        </button>
        <button class="tool-btn">
          <span class="material-symbols-outlined">filter_list</span> 筛选
        </button>
      </div>
    </div>

    <!-- 评价列表 -->
    <div v-if="filteredReviews.length" class="review-list">
      <div v-for="r in filteredReviews" :key="r.id" class="review-card">
        <div class="review-left">
          <div class="user-avatar">
            <span class="material-symbols-outlined">person</span>
          </div>
          <div class="user-info">
            <div class="user-name-row">
              <h4 class="user-name">{{ r.userNickname }}</h4>
            </div>
            <div class="user-meta">
              <span class="material-symbols-outlined">id_card</span>
              ID: {{ r.userId }}
            </div>
            <div class="user-meta">
              <span class="material-symbols-outlined">schedule</span>
              {{ formatDate(r.createdAt) }}
            </div>
          </div>
        </div>

        <div class="review-body">
          <div class="rating-row">
            <div class="stars">
              <span
                v-for="i in 5"
                :key="i"
                class="material-symbols-outlined star"
                :class="{ filled: i <= r.rating }"
              >star</span>
            </div>
            <span v-if="r.status === 'low'" class="low-tag">
              <span class="material-symbols-outlined">warning</span>
              低分评价
            </span>
            <span class="product-tag" v-if="r.orderId">订单号：#{{ r.orderId }}</span>
            <span class="product-tag" v-else-if="r.serviceName">{{ r.serviceName }}<template v-if="r.specName"> · {{ r.specName }}</template></span>
          </div>

          <p class="review-text">{{ r.content }}</p>

          <div v-if="r.images && r.images.length" class="review-images">
            <img v-for="(img, i) in r.images" :key="i" :src="img" :alt="`评价图片-${i}`" />
          </div>

          <div v-if="r.reply" class="reply-card">
            <p>{{ r.reply }}</p>
            <span v-if="r.repliedAt" class="reply-date">回复时间: {{ formatDate(r.repliedAt) }}</span>
          </div>

          <div class="review-actions">
            <template v-if="r.status === 'pending'">
              <button class="btn-primary" @click="reply(r)">回复</button>
              <button class="btn-outline" @click="replyWithTemplate(r)">模板回复</button>
            </template>
            <template v-else-if="r.status === 'replied'">
              <button class="btn-disabled">已回复</button>
              <button class="btn-outline" @click="reply(r)">修改回复</button>
            </template>
            <template v-else>
              <button class="btn-primary" @click="reply(r)">立即回复</button>
              <button class="btn-outline" @click="replyWithTemplate(r)">模板回复</button>
            </template>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="empty">暂无评价</div>

    <!-- 分页 -->
    <div class="pagination">
      <span class="page-info">共 {{ reviews.length }} 条评价</span>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.review-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.page-head {
  margin-bottom: $spacing-md;
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.page-sub {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
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
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.kpi-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-xs;
}

.kpi-value {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-sm;
}

.kpi-foot {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

.kpi-trend {
  font-size: $font-size-label-sm;
  color: $color-success;
  font-weight: 600;
}

.kpi-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.kpi-action {
  padding: 2px $spacing-sm;
  background: $color-brand-container;
  color: #ffffff;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;
}

.kpi-icon {
  color: $color-secondary;
  font-size: 24px;
}

// ============ Tabs ============
.tabs-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
  padding-bottom: $spacing-sm;
}

.tabs {
  display: flex;
  gap: $spacing-lg;
}

.tab {
  position: relative;
  padding: $spacing-sm 0;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;

  .tab-count {
    font-weight: 400;
    color: $color-on-surface-variant;
  }

  &.active {
    color: $color-brand;

    &::after {
      content: '';
      position: absolute;
      bottom: -3px;
      left: 0;
      right: 0;
      height: 3px;
      background: $color-brand;
      border-radius: $radius-full;
    }
  }
}

.tools {
  display: flex;
  gap: $spacing-sm;
}

.tool-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  color: $color-on-surface;
  cursor: pointer;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

// ============ Review ============
.review-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.review-card {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;

  @include desktop {
    flex-direction: row;
  }
}

.review-left {
  display: flex;
  gap: $spacing-md;
  min-width: 220px;
  flex-shrink: 0;
}

.user-avatar {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-secondary-container;
  color: $color-secondary;
  border-radius: $radius-full;

  .material-symbols-outlined {
    font-size: 24px;
  }
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;
}

.user-name {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.user-vip {
  padding: 2px $spacing-xs;
  background: $color-tertiary-fixed;
  color: #5b4300;
  border-radius: $radius-sm;
  font-size: 10px;
  font-weight: 700;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-bottom: 4px;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

.review-body {
  flex: 1;
  min-width: 0;
}

.rating-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-sm;
  flex-wrap: wrap;
}

.stars {
  display: flex;
  gap: 2px;

  .star {
    font-size: 22px;
    color: $color-outline-variant;

    &.filled {
      color: $color-warning;
      font-variation-settings: 'FILL' 1;
    }
  }
}

.low-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px $spacing-xs;
  background: rgba(255, 77, 79, 0.1);
  color: $color-emergency;
  border-radius: $radius-sm;
  font-size: 10px;
  font-weight: 700;
  border: 1px solid $color-emergency;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

.product-tag {
  margin-left: auto;
  padding: 4px $spacing-sm;
  background: $color-surface-container-high;
  border-radius: $radius-sm;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.review-text {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  line-height: 1.7;
  margin-bottom: $spacing-md;
}

.review-images {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
  flex-wrap: wrap;

  img {
    width: 96px;
    height: 96px;
    object-fit: cover;
    border-radius: $radius-default;
  }
}

.reply-card {
  background: rgba(255, 122, 50, 0.05);
  border-left: 3px solid $color-brand;
  padding: $spacing-md;
  border-radius: $radius-md;
  margin-bottom: $spacing-md;

  p {
    font-size: $font-size-label-md;
    color: $color-on-surface;
    line-height: 1.6;
    margin-bottom: $spacing-xs;
  }

  .reply-date {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
  }
}

.review-actions {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.btn-primary,
.btn-outline,
.btn-disabled {
  padding: $spacing-xs $spacing-lg;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary {
  background: $color-brand;
  color: #ffffff;
  border: 1px solid $color-brand;
}

.btn-outline {
  background: transparent;
  border: 1px solid $color-outline-variant;
  color: $color-on-surface;
}

.btn-disabled {
  background: transparent;
  border: 1px solid $color-outline-variant;
  color: $color-on-surface-variant;
  cursor: not-allowed;
}

// ============ Pagination ============
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-lg;
}

.page-info {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.page-buttons {
  display: flex;
  gap: $spacing-xs;
}

.page-btn {
  min-width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 $spacing-sm;
  border-radius: $radius-default;
  background: $color-surface-container-lowest;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  cursor: pointer;
  border: 1px solid $color-outline-variant;

  &:hover {
    background: $color-surface-container;
  }

  &.active {
    background: $color-brand;
    color: #ffffff;
    border-color: $color-brand;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.page-ellipsis {
  color: $color-text-tertiary;
  padding: 0 4px;
}

.empty {
  padding: $spacing-xl;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
}
</style>