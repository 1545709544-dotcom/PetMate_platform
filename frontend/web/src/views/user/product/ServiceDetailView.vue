<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  serviceApi,
  type ServiceItem,
  type StoreDetail,
  type StoreService,
  type ServiceReview,
  type ServiceSpec,
} from '@/api/modules/service'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const route = useRoute()
const router = useRouter()
const serviceId = Number(route.params.id)

const service = ref<ServiceItem | null>(null)
const store = ref<StoreDetail | null>(null)
const reviews = ref<ServiceReview[]>([])
const reviewTotal = ref(0)
const reviewDist = ref<Record<string, number>>({})
const otherServices = ref<StoreService[]>([])
const loading = ref(false)
const coverError = ref(false)
const reviewLoading = ref(false)

const HOURS_MAP: Record<string, string> = {
  mon: '周一', tue: '周二', wed: '周三', thu: '周四',
  fri: '周五', sat: '周六', sun: '周日',
}

function parseHours(raw: unknown): Array<{ label: string; time: string }> {
  if (!raw) return []
  let obj: Record<string, unknown> = raw as Record<string, unknown>
  if (typeof raw === 'string') {
    try { obj = JSON.parse(raw) } catch { return [] }
  }
  if (!obj || typeof obj !== 'object') return []
  return Object.keys(obj)
    .filter((k) => HOURS_MAP[k])
    .map((k) => ({ label: HOURS_MAP[k], time: String(obj[k] || '休息') }))
}

/** 价格区间：有多档时展示 min - max */
const priceText = computed(() => {
  const s = service.value
  if (!s) return ''
  const specs = s.specs || []
  if (specs.length > 1) {
    const prices = specs.map((x) => Number(x.price))
    const min = Math.min(...prices)
    const max = Math.max(...prices)
    if (max > min) return `${min} - ${max}`
    return String(min)
  }
  return String(Number(s.price))
})

/** 评分分布百分比，用于条形图 */
const distBars = computed(() => {
  const total = reviewTotal.value || 0
  return [5, 4, 3, 2, 1].map((star) => {
    const cnt = Number(reviewDist.value[String(star)] || 0)
    return { star, cnt, pct: total ? Math.round((cnt / total) * 100) : 0 }
  })
})

/** 规格按宠物类型分组展示，符合「犬类 / 猫类」的行业阅读习惯 */
const specGroups = computed(() => {
  const specs = service.value?.specs || []
  if (!specs.length) return []
  const groups: Array<{ key: string; label: string; icon: string; items: ServiceSpec[] }> = []
  const push = (key: string, label: string, icon: string, item: ServiceSpec) => {
    let g = groups.find((x) => x.key === key)
    if (!g) { g = { key, label, icon, items: [] }; groups.push(g) }
    g.items.push(item)
  }
  for (const s of specs) {
    if (s.petKind === 'dog') push('dog', '犬类', 'sound_detection_dog_barking', s)
    else if (s.petKind === 'cat') push('cat', '猫类', 'pets', s)
    else push('all', '通用档位', 'pets', s)
  }
  return groups
})

function weightText(s: ServiceSpec) {
  const min = s.weightMin != null ? Number(s.weightMin) : null
  const max = s.weightMax != null ? Number(s.weightMax) : null
  if (min != null && max != null) return `${min}-${max}kg`
  if (max != null) return `${max}kg 以下`
  if (min != null) return `${min}kg 以上`
  return '—'
}

function modeText(m?: string) {
  return m === 'onsite' ? '上门服务' : '到店服务'
}
function petText(p?: string) {
  if (p === 'dog') return '仅限狗狗'
  if (p === 'cat') return '仅限猫咪'
  return '猫狗通用'
}

async function load() {
  loading.value = true
  try {
    const s = await serviceApi.detail(serviceId)
    service.value = s
    if (s.store?.id) {
      const sd = await serviceApi.store(s.store.id)
      store.value = sd
      otherServices.value = (sd.services || []).filter((x) => x.id !== serviceId).slice(0, 6)
    }
  } catch (e) {
    ElMessage.error((e as Error)?.message || '服务详情加载失败')
  } finally {
    loading.value = false
  }
  loadReviews()
}

async function loadReviews() {
  reviewLoading.value = true
  try {
    const r = await serviceApi.reviews(serviceId, { page: 1, size: 10 })
    reviews.value = r?.list || []
    reviewTotal.value = r?.total ?? 0
    reviewDist.value = r?.dist || {}
  } catch {
    reviews.value = []
  } finally {
    reviewLoading.value = false
  }
}

function book() {
  router.push(`/appointment/booking/${serviceId}`)
}
function bookSpec(spec: ServiceSpec) {
  router.push({ path: `/appointment/booking/${serviceId}`, query: { specId: String(spec.id) } })
}
function goService(id: number) {
  router.push(`/service/${id}`)
  setTimeout(() => window.location.reload(), 0)
}
function goList() {
  router.push('/service')
}
function stars(rating: number) {
  const full = Math.round(rating)
  return Array.from({ length: 5 }, (_, i) => i < full)
}

onMounted(load)
</script>

<template>
  <div class="detail-view">
    <div class="detail-container">
      <button class="back-btn" @click="goList">
        <span class="material-symbols-outlined">arrow_back</span> 返回服务列表
      </button>

      <div v-if="loading" class="state">加载中…</div>

      <template v-else-if="service">
        <!-- 头部 -->
        <section class="hero">
          <div class="hero-cover">
            <img
              v-if="service.cover && !coverError"
              :src="service.cover"
              :alt="service.name"
              @error="coverError = true"
            />
            <div v-else class="cover-ph">
              <span class="material-symbols-outlined">{{ service.icon || 'pets' }}</span>
            </div>
            <span class="cat-badge">{{ service.subCategory || service.category }}</span>
          </div>

          <div class="hero-info">
            <h1 class="service-name">{{ service.name }}</h1>

            <div class="badge-row">
              <span class="hero-badge" :class="service.serviceMode">
                <span class="material-symbols-outlined">
                  {{ service.serviceMode === 'onsite' ? 'directions_car' : 'storefront' }}
                </span>
                {{ modeText(service.serviceMode) }}
              </span>
              <span class="hero-badge ghost">
                <span class="material-symbols-outlined">pets</span>
                {{ petText(service.petType) }}
              </span>
              <span v-if="service.duration" class="hero-badge ghost">
                <span class="material-symbols-outlined">schedule</span>
                约 {{ service.duration }} 分钟
              </span>
              <span v-if="service.advanceDays" class="hero-badge ghost">
                <span class="material-symbols-outlined">event_available</span>
                可约未来 {{ service.advanceDays }} 天
              </span>
            </div>

            <div class="stat-row">
              <div class="stat">
                <span class="stat-val rating">
                  <span class="material-symbols-outlined">star</span>
                  {{ service.rating ? Number(service.rating).toFixed(1) : '新' }}
                </span>
                <span class="stat-key">综合评分</span>
              </div>
              <div class="stat-sep"></div>
              <div class="stat">
                <span class="stat-val">{{ service.sales || 0 }}</span>
                <span class="stat-key">累计服务</span>
              </div>
              <div class="stat-sep"></div>
              <div class="stat">
                <span class="stat-val">{{ service.reviewCount || 0 }}</span>
                <span class="stat-key">用户评价</span>
              </div>
            </div>

            <div class="price-block">
              <span class="price-symbol">¥</span>
              <span class="price">{{ priceText }}</span>
              <span class="price-unit">/{{ service.priceUnit || '次' }}</span>
              <span v-if="service.originalPrice" class="price-origin">
                ¥{{ Number(service.originalPrice) }}
              </span>
              <span v-if="service.onsiteFee && Number(service.onsiteFee) > 0" class="onsite-fee">
                上门费 ¥{{ Number(service.onsiteFee) }}
              </span>
            </div>

            <div v-if="service.tags?.length" class="tag-row">
              <span v-for="t in service.tags" :key="t" class="svc-tag">{{ t }}</span>
            </div>

            <p v-if="service.description" class="service-desc">{{ service.description }}</p>

            <div class="hero-actions">
              <button class="primary-btn" @click="book">
                <span class="material-symbols-outlined">event_available</span> 选规格 · 立即预约
              </button>
              <span class="pay-hint">
                <span class="material-symbols-outlined">info</span>
                预约成功后到店支付，出示核销码即可开始服务
              </span>
            </div>
          </div>
        </section>

        <!-- 规格价目表 -->
        <PageSection
          v-if="specGroups.length"
          class="block"
          title="规格与价目"
          icon="price_change"
        >
          <template #actions>
            <span class="title-sub">按体型 / 毛长分档，实际以到店称重为准</span>
          </template>
          <div v-for="g in specGroups" :key="g.key" class="spec-group">
            <div v-if="specGroups.length > 1" class="spec-group-head">
              <span class="material-symbols-outlined">{{ g.icon }}</span>
              {{ g.label }}
            </div>
            <div class="spec-table">
              <div class="spec-head">
                <span class="col-name">规格档位</span>
                <span class="col-weight">体重范围</span>
                <span class="col-dur">时长</span>
                <span class="col-price">价格</span>
                <span class="col-act"></span>
              </div>
              <div v-for="sp in g.items" :key="sp.id" class="spec-row">
                <span class="col-name">
                  <strong>{{ sp.name }}</strong>
                  <em v-if="sp.coat && sp.coat !== '不限'">{{ sp.coat }}</em>
                </span>
                <span class="col-weight">{{ weightText(sp) }}</span>
                <span class="col-dur">{{ sp.duration }} 分钟</span>
                <span class="col-price">¥{{ Number(sp.price) }}</span>
                <span class="col-act">
                  <button class="spec-btn" @click="bookSpec(sp)">预约</button>
                </span>
              </div>
            </div>
          </div>
        </PageSection>

        <!-- 可选加购 -->
        <PageSection
          v-if="service.addons?.length"
          class="block"
          title="可选加购项"
          icon="add_shopping_cart"
        >
          <template #actions>
            <span class="title-sub">下单时可自由勾选，费用另计</span>
          </template>
          <div class="addon-grid">
            <div v-for="a in service.addons" :key="a.id" class="addon-card">
              <div class="addon-top">
                <span class="addon-name">{{ a.name }}</span>
                <span class="addon-price">+¥{{ Number(a.price) }}</span>
              </div>
              <p v-if="a.description" class="addon-desc">{{ a.description }}</p>
              <span v-if="a.duration" class="addon-dur">
                <span class="material-symbols-outlined">schedule</span>
                +{{ a.duration }} 分钟
              </span>
            </div>
          </div>
        </PageSection>

        <!-- 服务包含 -->
        <PageSection
          v-if="service.includeItems?.length"
          class="block"
          title="服务包含"
          icon="checklist"
        >
          <ul class="include-list">
            <li v-for="(it, i) in service.includeItems" :key="i">
              <span class="material-symbols-outlined">check_circle</span>
              {{ it }}
            </li>
          </ul>
        </PageSection>

        <!-- 预约须知 + 退改政策 -->
        <div class="policy-grid">
          <PageSection
            v-if="service.notices?.length"
            class="block"
            title="预约须知"
            icon="info"
          >
            <ol class="notice-list">
              <li v-for="(n, i) in service.notices" :key="i">{{ n }}</li>
            </ol>
          </PageSection>

          <PageSection
            v-if="service.refundPolicy"
            class="block"
            title="退改政策"
            icon="policy"
          >
            <p class="refund-text">{{ service.refundPolicy }}</p>
            <div class="refund-tip">
              <span class="material-symbols-outlined">payments</span>
              本服务为「到店支付」，预约不收取任何费用
            </div>
          </PageSection>
        </div>

        <!-- 门店信息 -->
        <PageSection v-if="store" class="block" title="服务提供门店" icon="store">
          <div class="store-card">
            <div class="store-head">
              <img v-if="store.logo" :src="store.logo" class="store-logo" :alt="store.name" />
              <span v-else class="material-symbols-outlined store-ico">store</span>
              <div class="store-meta">
                <div class="store-name">{{ store.name }}</div>
                <div class="store-sub">
                  <span v-if="store.averageRating" class="store-rating">
                    <span class="material-symbols-outlined">star</span>
                    {{ Number(store.averageRating).toFixed(1) }}
                  </span>
                  <span v-if="store.reviewCount" class="store-reviews">
                    {{ store.reviewCount }} 条评价
                  </span>
                </div>
              </div>
              <div v-if="service.openTime" class="store-hours-badge">
                营业 {{ service.openTime }} - {{ service.closeTime }}
              </div>
            </div>
            <p class="store-addr">
              <span class="material-symbols-outlined">location_on</span> {{ store.address }}
            </p>
            <p v-if="store.phone" class="store-addr">
              <span class="material-symbols-outlined">call</span> {{ store.phone }}
            </p>
            <div v-if="parseHours((store as any).businessHours).length" class="hours">
              <div
                v-for="h in parseHours((store as any).businessHours)"
                :key="h.label"
                class="hour-row"
              >
                <span class="hour-label">{{ h.label }}</span>
                <span class="hour-time">{{ h.time }}</span>
              </div>
            </div>
          </div>
        </PageSection>

        <!-- 用户评价 -->
        <PageSection class="block" title="用户评价" icon="reviews">
          <template #actions>
            <span v-if="reviewTotal" class="title-sub">共 {{ reviewTotal }} 条</span>
          </template>

          <div v-if="reviewTotal" class="review-summary">
            <div class="summary-score">
              <div class="score-num">{{ Number(service.rating || 0).toFixed(1) }}</div>
              <div class="score-stars">
                <span
                  v-for="(on, i) in stars(Number(service.rating || 0))"
                  :key="i"
                  class="material-symbols-outlined"
                  :class="{ filled: on }"
                >star</span>
              </div>
              <div class="score-cnt">{{ reviewTotal }} 人评价</div>
            </div>
            <div class="summary-bars">
              <div v-for="b in distBars" :key="b.star" class="bar-row">
                <span class="bar-star">{{ b.star }} 星</span>
                <span class="bar-track"><i :style="{ width: b.pct + '%' }"></i></span>
                <span class="bar-cnt">{{ b.cnt }}</span>
              </div>
            </div>
          </div>

          <div v-if="reviewLoading" class="state">评价加载中…</div>
          <PetEmptyState
            v-else-if="!reviews.length"
            icon="reviews"
            title="暂无评价"
            desc="这项服务还没有评价，完成服务后来分享你的体验吧～"
          />
          <div v-else class="review-list">
            <div v-for="r in reviews" :key="r.id" class="review-item">
              <div class="review-top">
                <img v-if="r.avatar" :src="r.avatar" class="reviewer-avatar" :alt="r.nickname" />
                <span v-else class="reviewer-avatar ph material-symbols-outlined">person</span>
                <div class="reviewer-meta">
                  <span class="reviewer">{{ r.nickname || '匿名用户' }}</span>
                  <span v-if="r.specName" class="review-spec">{{ r.specName }}</span>
                </div>
                <span class="stars">
                  <span
                    v-for="(on, i) in stars(r.rating)"
                    :key="i"
                    class="material-symbols-outlined"
                    :class="{ filled: on }"
                  >star</span>
                </span>
              </div>
              <p v-if="r.content" class="review-content">{{ r.content }}</p>
              <div v-if="r.images?.length" class="review-imgs">
                <img v-for="(img, i) in r.images" :key="i" :src="img" alt="评价图片" />
              </div>
              <div v-if="r.reply" class="review-reply">
                <span class="reply-tag">商家回复</span>{{ r.reply }}
              </div>
              <p class="review-time">{{ r.createdAt }}</p>
            </div>
          </div>
        </PageSection>

        <!-- 同店其他服务 -->
        <PageSection v-if="otherServices.length" class="block" title="该门店其他服务" icon="list_alt">
          <div class="other-list">
            <div v-for="o in otherServices" :key="o.id" class="other-item" @click="goService(o.id)">
              <div class="other-main">
                <span class="other-cat">{{ o.subCategory || o.category }}</span>
                <span class="other-name">{{ o.name }}</span>
                <span v-if="o.sales" class="other-sales">已售 {{ o.sales }}</span>
              </div>
              <div class="other-right">
                <span class="other-price">¥{{ Number(o.price) }} 起</span>
                <span class="material-symbols-outlined">chevron_right</span>
              </div>
            </div>
          </div>
        </PageSection>

        <!-- 吸底预约条 -->
        <div class="sticky-bar">
          <div class="sticky-price">
            <span class="sticky-symbol">¥</span>
            <span class="sticky-val">{{ priceText }}</span>
            <span class="sticky-unit">/{{ service.priceUnit || '次' }}</span>
            <span class="sticky-hint">到店支付</span>
          </div>
          <button class="sticky-btn" @click="book">
            <span class="material-symbols-outlined">event_available</span> 立即预约
          </button>
        </div>
      </template>

      <PetEmptyState v-else icon="search_off" title="未找到该服务">
        <template #action>
          <button class="link-btn" @click="goList">去服务页看看</button>
        </template>
      </PetEmptyState>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.detail-view {
  min-height: 100vh;
  background: $color-background;
}
.detail-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: 120px;
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}
.back-btn {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  &:hover { color: $color-brand; }
}
.state {
  padding: $spacing-2xl;
  text-align: center;
  color: $color-on-surface-variant;
}
.title-sub {
  font-size: $font-size-label-sm;
  color: $color-outline;
  font-weight: 400;
}

// ============ Hero ============
.hero {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-xl;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  @include desktop {
    grid-template-columns: 380px 1fr;
    align-items: start;
  }
}
.hero-cover {
  position: relative;
  width: 100%;
  height: 280px;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $color-surface-container-low;
  img { width: 100%; height: 100%; object-fit: cover; }
}
.cover-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, $color-brand-container, $color-secondary-container);
  .material-symbols-outlined {
    font-size: 72px;
    color: $color-on-brand-container;
    font-variation-settings: 'FILL' 1;
  }
}
.cat-badge {
  position: absolute;
  top: $spacing-md;
  left: $spacing-md;
  padding: 4px $spacing-md;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-brand;
}
.hero-info { min-width: 0; }
.service-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  line-height: 1.25;
}
.badge-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: $spacing-sm;
}
.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px $spacing-sm;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  background: rgba(0, 96, 172, 0.1);
  color: #0060ac;

  .material-symbols-outlined { font-size: 15px; }
  &.onsite { background: rgba(255, 122, 50, 0.12); color: $color-brand; }
  &.ghost {
    background: $color-surface-container-low;
    color: $color-on-surface-variant;
    font-weight: 600;
  }
}
.stat-row {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-lg;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.stat-val {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  display: inline-flex;
  align-items: center;
  gap: 2px;

  &.rating { color: #f5a524; }
  .material-symbols-outlined {
    font-size: 18px;
    font-variation-settings: 'FILL' 1;
  }
}
.stat-key {
  font-size: $font-size-label-sm;
  color: $color-outline;
}
.stat-sep {
  width: 1px;
  height: 28px;
  background: $color-outline-variant;
}
.price-block {
  display: flex;
  align-items: baseline;
  gap: 2px;
  flex-wrap: wrap;
  margin-top: $spacing-md;
}
.price-symbol {
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-brand;
}
.price {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: 800;
  color: $color-brand;
  line-height: 1;
}
.price-unit {
  font-size: $font-size-label-md;
  color: $color-outline;
  margin-left: 2px;
}
.price-origin {
  font-size: $font-size-label-md;
  color: $color-outline;
  text-decoration: line-through;
  margin-left: $spacing-sm;
}
.onsite-fee {
  margin-left: $spacing-sm;
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.1);
  color: $color-brand;
  font-size: $font-size-label-sm;
  font-weight: 600;
}
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: $spacing-sm;
}
.svc-tag {
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.08);
  border: 1px solid rgba($color-brand, 0.18);
  font-size: 11px;
  font-weight: 600;
  color: $color-brand;
}
.service-desc {
  margin-top: $spacing-md;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: 1.7;
}
.hero-actions {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  margin-top: $spacing-lg;
}
.primary-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: $spacing-md $spacing-2xl;
  background: $color-brand;
  color: $color-on-brand;
  border-radius: $radius-full;
  font-weight: 700;
  font-size: $font-size-label-md;
  cursor: pointer;
  transition: all $transition-fast;
  align-self: flex-start;

  &:hover { background: $color-brand-dark; transform: translateY(-1px); box-shadow: $shadow-md; }
  &:active { transform: scale(0.97); }
}
.pay-hint {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-outline;
  .material-symbols-outlined { font-size: 15px; }
}

.block { margin: 0; }

// ============ 规格价目表 ============
.spec-group + .spec-group { margin-top: $spacing-lg; }
.spec-group-head {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: $spacing-sm;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
  .material-symbols-outlined { font-size: 18px; color: $color-brand; }
}
.spec-table {
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  overflow: hidden;
}
.spec-head,
.spec-row {
  display: grid;
  grid-template-columns: 1.6fr 1fr 0.9fr 0.9fr 80px;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  font-size: $font-size-label-md;
}
.spec-head {
  background: $color-surface-container-low;
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-outline;
}
.spec-row {
  border-top: 1px solid $color-outline-variant;
  transition: background $transition-fast;
  &:hover { background: rgba($color-brand, 0.04); }

  .col-name {
    display: flex;
    align-items: center;
    gap: 4px;
    strong { font-weight: 700; color: $color-on-surface; }
    em {
      font-style: normal;
      padding: 1px 5px;
      border-radius: $radius-sm;
      background: $color-surface-container-low;
      font-size: 11px;
      color: $color-on-surface-variant;
    }
  }
  .col-weight,
  .col-dur { color: $color-on-surface-variant; }
  .col-price {
    font-family: $font-family-headline;
    font-weight: 700;
    color: $color-brand;
  }
  .col-act { text-align: right; }
}
.spec-btn {
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  border: 1px solid $color-brand;
  background: transparent;
  color: $color-brand;
  font-size: $font-size-label-sm;
  font-weight: 700;
  cursor: pointer;
  transition: all $transition-fast;
  &:hover { background: $color-brand; color: $color-on-brand; }
}

// ============ 加购项 ============
.addon-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: $spacing-md;
}
.addon-card {
  padding: $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  background: $color-surface-container-lowest;
  transition: all $transition-fast;
  &:hover { border-color: rgba($color-brand, 0.5); box-shadow: $shadow-sm; }
}
.addon-top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $spacing-sm;
}
.addon-name {
  font-weight: 700;
  font-size: $font-size-label-md;
  color: $color-on-surface;
}
.addon-price {
  font-family: $font-family-headline;
  font-weight: 700;
  color: $color-brand;
  white-space: nowrap;
}
.addon-desc {
  margin-top: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  line-height: 1.5;
}
.addon-dur {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-top: 6px;
  font-size: 11px;
  color: $color-outline;
  .material-symbols-outlined { font-size: 13px; }
}

// ============ 服务包含 ============
.include-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: $spacing-sm;
  list-style: none;
  padding: 0;
  margin: 0;

  li {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    font-size: $font-size-body-md;
    color: $color-on-surface-variant;
    line-height: 1.6;

    .material-symbols-outlined {
      font-size: 18px;
      color: #4caf50;
      flex-shrink: 0;
      margin-top: 1px;
    }
  }
}

// ============ 须知 / 退改 ============
.policy-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;
  @include desktop { grid-template-columns: 1fr 1fr; }
}
.notice-list {
  margin: 0;
  padding-left: 1.2em;
  li {
    font-size: $font-size-body-md;
    color: $color-on-surface-variant;
    line-height: 1.8;
  }
}
.refund-text {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: 1.8;
  white-space: pre-line;
}
.refund-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-default;
  background: rgba(76, 175, 80, 0.1);
  color: #2e7d32;
  font-size: $font-size-label-sm;
  font-weight: 600;
  .material-symbols-outlined { font-size: 17px; }
}

// ============ 门店 ============
.store-card {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}
.store-head {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}
.store-logo {
  width: 48px;
  height: 48px;
  border-radius: $radius-md;
  object-fit: cover;
}
.store-ico {
  font-size: 40px;
  color: $color-brand;
}
.store-meta { flex: 1; min-width: 0; }
.store-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}
.store-sub {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-top: 2px;
  font-size: $font-size-label-sm;
  color: $color-outline;
}
.store-rating {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: #f5a524;
  font-weight: 700;
  .material-symbols-outlined { font-size: 15px; font-variation-settings: 'FILL' 1; }
}
.store-hours-badge {
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  background: $color-surface-container-low;
  font-size: $font-size-label-sm;
  font-weight: 600;
  color: $color-on-surface-variant;
  white-space: nowrap;
}
.store-addr {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 18px; color: $color-brand; }
}
.hours {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(130px, 1fr));
  gap: 4px;
  margin-top: $spacing-xs;
}
.hour-row {
  display: flex;
  justify-content: space-between;
  padding: 3px $spacing-sm;
  border-radius: $radius-sm;
  background: $color-surface-container-low;
  font-size: $font-size-label-sm;
}
.hour-label { color: $color-outline; }
.hour-time { color: $color-on-surface; font-weight: 600; }

// ============ 评价 ============
.review-summary {
  display: flex;
  align-items: center;
  gap: $spacing-2xl;
  padding: $spacing-lg;
  margin-bottom: $spacing-lg;
  background: $color-surface-container-low;
  border-radius: $radius-lg;
  flex-wrap: wrap;
}
.summary-score {
  text-align: center;
  min-width: 100px;
}
.score-num {
  font-family: $font-family-headline;
  font-size: 44px;
  font-weight: 800;
  color: $color-brand;
  line-height: 1;
}
.score-stars {
  margin-top: 4px;
  .material-symbols-outlined {
    font-size: 16px;
    color: $color-outline-variant;
    &.filled { color: #f5a524; font-variation-settings: 'FILL' 1; }
  }
}
.score-cnt {
  margin-top: 2px;
  font-size: $font-size-label-sm;
  color: $color-outline;
}
.summary-bars {
  flex: 1;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.bar-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.bar-star { width: 34px; flex-shrink: 0; }
.bar-track {
  flex: 1;
  height: 7px;
  border-radius: $radius-full;
  background: $color-outline-variant;
  overflow: hidden;
  i {
    display: block;
    height: 100%;
    border-radius: $radius-full;
    background: linear-gradient(90deg, #f5a524, #ff7a32);
    transition: width 600ms cubic-bezier(0.16, 1, 0.3, 1);
  }
}
.bar-cnt { width: 26px; text-align: right; flex-shrink: 0; }

.review-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.review-item {
  padding: $spacing-md;
  border-radius: $radius-lg;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
}
.review-top {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}
.reviewer-avatar {
  width: 32px;
  height: 32px;
  border-radius: $radius-full;
  object-fit: cover;
  flex-shrink: 0;
  &.ph {
    display: flex;
    align-items: center;
    justify-content: center;
    background: $color-surface-container-low;
    color: $color-outline;
    font-size: 20px;
  }
}
.reviewer-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.reviewer {
  font-weight: 700;
  font-size: $font-size-label-md;
  color: $color-on-surface;
}
.review-spec {
  font-size: 11px;
  color: $color-outline;
}
.stars {
  .material-symbols-outlined {
    font-size: 15px;
    color: $color-outline-variant;
    &.filled { color: #f5a524; font-variation-settings: 'FILL' 1; }
  }
}
.review-content {
  margin-top: $spacing-sm;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: 1.7;
}
.review-imgs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: $spacing-sm;
  img {
    width: 84px;
    height: 84px;
    border-radius: $radius-md;
    object-fit: cover;
  }
}
.review-reply {
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;
  background: $color-surface-container-low;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1.6;
}
.reply-tag {
  display: inline-block;
  margin-right: 6px;
  padding: 1px 6px;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.12);
  color: $color-brand;
  font-size: 11px;
  font-weight: 700;
}
.review-time {
  margin-top: 6px;
  font-size: 11px;
  color: $color-outline;
}

// ============ 同店其他服务 ============
.other-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}
.other-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;
  background: $color-surface-container-low;
  cursor: pointer;
  transition: all $transition-fast;
  &:hover { background: rgba($color-brand, 0.08); transform: translateX(3px); }
}
.other-main {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  min-width: 0;
}
.other-cat {
  padding: 1px $spacing-sm;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.12);
  color: $color-brand;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}
.other-name {
  font-size: $font-size-label-md;
  color: $color-on-surface;
  font-weight: 600;
  @include text-ellipsis(1);
}
.other-sales {
  font-size: 11px;
  color: $color-outline;
  white-space: nowrap;
}
.other-right {
  display: flex;
  align-items: center;
  gap: 2px;
  color: $color-outline;
  .material-symbols-outlined { font-size: 18px; }
}
.other-price {
  font-family: $font-family-headline;
  font-weight: 700;
  color: $color-brand;
  white-space: nowrap;
}

// ============ 吸底条 ============
.sticky-bar {
  position: fixed;
  left: 50%;
  bottom: $spacing-lg;
  transform: translateX(-50%);
  z-index: 40;
  display: flex;
  align-items: center;
  gap: $spacing-2xl;
  padding: $spacing-sm $spacing-sm $spacing-sm $spacing-xl;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(30px) saturate(180%);
  border: 1px solid rgba(223, 192, 179, 0.4);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.14);
}
.sticky-price {
  display: flex;
  align-items: baseline;
  gap: 2px;
}
.sticky-symbol {
  font-weight: 700;
  color: $color-brand;
}
.sticky-val {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 800;
  color: $color-brand;
}
.sticky-unit {
  font-size: $font-size-label-sm;
  color: $color-outline;
}
.sticky-hint {
  margin-left: $spacing-sm;
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  background: rgba(76, 175, 80, 0.12);
  color: #2e7d32;
  font-size: 11px;
  font-weight: 700;
}
.sticky-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-sm $spacing-xl;
  border-radius: $radius-full;
  background: $color-brand;
  color: $color-on-brand;
  font-weight: 700;
  cursor: pointer;
  transition: all $transition-fast;
  .material-symbols-outlined { font-size: 18px; }
  &:hover { background: $color-brand-dark; }
  &:active { transform: scale(0.96); }
}
.link-btn {
  color: $color-brand;
  font-weight: 700;
  cursor: pointer;
  text-decoration: underline;
}

// ============ 手机端响应式（≤768px）：用 rem 还原被缩高度/留白，规格表横滚，吸底条全宽贴底 ============
@include mobile {
  .detail-container {
    padding-top: 1rem;
    padding-bottom: 7rem; // ≈112px，留出吸底条空间，避免被 px-to-viewport 缩没了
  }

  .hero {
    padding: 1rem;
    gap: 1rem;
  }

  .hero-cover {
    height: 12rem; // ≈192px
  }

  .service-name {
    font-size: 1.5rem; // ≈24px
  }

  .spec-table {
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }

  .spec-head,
  .spec-row {
    min-width: 30rem; // ≈480px，保证 5 列在窄屏可读、整体横向滚动
  }

  .sticky-bar {
    left: 0.75rem;
    right: 0.75rem;
    bottom: 0.75rem;
    transform: none;
    width: auto;
    gap: 0.75rem;
    padding: 0.5rem 0.75rem;
    justify-content: space-between;
  }
}
</style>
