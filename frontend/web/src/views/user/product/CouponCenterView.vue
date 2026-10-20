<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, type Coupon } from '@/api/modules/user'
import PageSection from '@/components/common/PageSection.vue'

const router = useRouter()

const loading = ref(false)
const coupons = ref<Coupon[]>([])
// 正在领取的券 id（防重复点击）
const claimingId = ref<number | null>(null)

// 状态筛选 Tab：全部 / 可使用(已领取) / 已使用 / 已过期
type TabKey = 'all' | 'usable' | 'used' | 'expired'
const activeTab = ref<TabKey>('all')

const tabs: { key: TabKey; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'usable', label: '可使用' },
  { key: 'used', label: '已使用' },
  { key: 'expired', label: '已过期' },
]

async function load() {
  loading.value = true
  try {
    coupons.value = await userApi.coupons()
  } catch {
    ElMessage.error('优惠券加载失败')
    coupons.value = []
  } finally {
    loading.value = false
  }
}

// 可领取的券：后端 status='available' 表示「用户尚未持有」
const claimableCoupons = computed(() => coupons.value.filter((c) => c.status === 'available'))

// 我的优惠券 = 已和用户产生关系(已领取/已使用/已过期)的券,不包含纯展示的「可领取」券
const myCoupons = computed(() =>
  coupons.value.filter((c) => c.status && c.status !== 'available'),
)

// 领券：成功后就地把该券状态翻成 claimed，无需整页重拉
async function claim(c: Coupon) {
  if (claimingId.value) return
  claimingId.value = c.id
  try {
    await userApi.claimCoupon(c.id)
    ElMessage.success(`已领取「${c.name}」`)
    const hit = coupons.value.find((x) => x.id === c.id)
    if (hit) hit.status = 'claimed'
  } catch (e: any) {
    const msg = e?.message || '领取失败'
    ElMessage.warning(msg)
    // 已领过 → 同步真实状态，避免按钮一直可点
    if (msg.includes('已领')) {
      const hit = coupons.value.find((x) => x.id === c.id)
      if (hit) hit.status = 'claimed'
    }
  } finally {
    claimingId.value = null
  }
}

const counts = computed(() => ({
  all: myCoupons.value.length,
  usable: myCoupons.value.filter((c) => c.status === 'claimed').length,
  used: myCoupons.value.filter((c) => c.status === 'used').length,
  expired: myCoupons.value.filter((c) => c.status === 'expired').length,
}))

const displayCoupons = computed(() => {
  if (activeTab.value === 'all') return myCoupons.value
  if (activeTab.value === 'usable') return myCoupons.value.filter((c) => c.status === 'claimed')
  if (activeTab.value === 'used') return myCoupons.value.filter((c) => c.status === 'used')
  return myCoupons.value.filter((c) => c.status === 'expired')
})

// 券面额文案
function faceValue(c: Coupon): string {
  if (c.type === 'discount') {
    const d = c.discount ?? 0
    return `${d}折`
  }
  return `¥${c.amount}`
}

// 使用门槛文案
function thresholdText(c: Coupon): string {
  if (c.threshold && c.threshold > 0) return `满${c.threshold}元可用`
  if (c.type === 'discount') return '全场通用'
  return '无门槛'
}

function statusMeta(s?: Coupon['status']): { label: string; cls: string } {
  if (s === 'claimed') return { label: '可使用', cls: 'usable' }
  if (s === 'used') return { label: '已使用', cls: 'used' }
  if (s === 'expired') return { label: '已过期', cls: 'expired' }
  return { label: '—', cls: 'expired' }
}

function goUse() {
  router.push('/category/browse')
}

// 「去领券」：本页顶部就是领券中心，直接滚过去；确实没券可领时才去逛商城
const claimSectionRef = ref<HTMLElement | null>(null)
function goClaim() {
  if (claimableCoupons.value.length > 0) {
    claimSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }
  router.push('/category')
}

onMounted(load)
</script>

<template>
  <div class="coupon-view">
    <div class="coupon-container">
      <nav class="breadcrumb">
        <span>个人中心</span>
        <span class="sep">›</span>
        <span class="current">我的优惠券</span>
      </nav>

      <h1 class="page-title">我的优惠券</h1>

      <!-- ============ 领券中心：未持有的券在这里领 ============ -->
      <section
        v-if="!loading && claimableCoupons.length > 0"
        ref="claimSectionRef"
        class="claim-section"
      >
        <PageSection title="领券中心" icon="redeem">
          <template #actions>
            <span class="pet-pill">{{ claimableCoupons.length }} 张可领</span>
          </template>

        <div class="claim-grid">
          <article
            v-for="(c, idx) in claimableCoupons"
            :key="c.id"
            class="claim-card"
            :style="{ '--i': idx }"
          >
            <div class="claim-left">
              <span class="claim-value">{{ faceValue(c) }}</span>
              <span class="claim-threshold">{{ thresholdText(c) }}</span>
            </div>
            <div class="claim-body">
              <p class="claim-name">{{ c.name }}</p>
              <p class="claim-valid">有效期至 {{ (c.validEnd || '').slice(0, 10) || '—' }}</p>
            </div>
            <button
              class="claim-btn"
              :disabled="claimingId === c.id"
              @click="claim(c)"
            >
              <span
                v-if="claimingId === c.id"
                class="material-symbols-outlined spin"
              >progress_activity</span>
              <span v-else>立即领取</span>
            </button>
          </article>
        </div>
      </PageSection>
      </section>

      <h2 v-if="!loading && claimableCoupons.length > 0" class="sub-section-title">我的券包</h2>

      <!-- 状态筛选 Tab（通栏分段） -->
      <nav class="tabs" v-if="!loading && myCoupons.length > 0">
        <button
          v-for="t in tabs"
          :key="t.key"
          class="tab"
          :class="{ active: activeTab === t.key }"
          @click="activeTab = t.key"
        >
          {{ t.label }}
          <span class="tab-count">{{ counts[t.key] }}</span>
        </button>
      </nav>

      <main class="coupon-list">
        <div v-if="loading" class="state-block">
          <span class="material-symbols-outlined spin">progress_activity</span>
          <p>加载中…</p>
        </div>

        <!-- 空态:还没有任何优惠券 -->
        <div v-else-if="myCoupons.length === 0" class="state-block">
          <div class="state-icon-wrap">
            <span class="material-symbols-outlined">confirmation_number</span>
          </div>
          <p class="state-title">还没有领到优惠券</p>
          <p class="state-sub">新用户专享「新宠大礼包」,3 张券免费领</p>
          <button class="retry-btn" @click="goClaim">去领券</button>
        </div>

        <!-- 当前 Tab 下无数据(如切到「已使用」但暂无) -->
        <div v-else-if="displayCoupons.length === 0" class="state-block">
          <span class="material-symbols-outlined empty-icon">inventory_2</span>
          <p class="state-title">该分类下暂无优惠券</p>
        </div>

        <article
          v-for="c in displayCoupons"
          :key="c.id"
          class="coupon-card"
          :class="statusMeta(c.status).cls"
        >
          <div class="coupon-left">
            <span class="coupon-value">{{ faceValue(c) }}</span>
            <span class="coupon-threshold">{{ thresholdText(c) }}</span>
          </div>
          <div class="coupon-body">
            <p class="coupon-name">{{ c.name }}</p>
            <p class="coupon-valid">有效期至 {{ (c.validEnd || '').slice(0, 10) || '—' }}</p>
          </div>
          <div class="coupon-right">
            <span class="status-pill" :class="statusMeta(c.status).cls">
              {{ statusMeta(c.status).label }}
            </span>
            <button
              v-if="c.status === 'claimed'"
              class="use-btn"
              @click="goUse"
            >
              去使用
            </button>
          </div>
          <!-- 已使用 / 已过期 角标印章 -->
          <span
            v-if="c.status === 'used' || c.status === 'expired'"
            class="stamp"
          >{{ statusMeta(c.status).label }}</span>
        </article>
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

.coupon-view {
  min-height: 100vh;
  background: $color-background;
}

.coupon-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
}

// ============ 面包屑 + 标题（与我的订单一致） ============
.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-md;

  .sep {
    color: $color-text-tertiary;
  }

  .current {
    color: $color-brand;
    font-weight: 600;
  }
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-lg;
}

// ============ 领券中心 ============
.claim-section {
  margin-bottom: $spacing-xl;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba($color-brand, 0.16);
}

.section-head {
  margin-bottom: $spacing-md;
}

.section-title-wrap {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

.section-ico {
  font-size: 22px;
  color: $color-brand;
}

.section-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0;
}

.section-badge {
  margin-left: $spacing-xs;
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;
  background: rgba($color-brand, 0.12);
  color: $color-brand;
}

.section-sub {
  margin: 4px 0 0;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.claim-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: repeat(3, 1fr);
  }
}

.claim-card {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md $spacing-sm 0;
  background: $color-surface-container-lowest;
  border: 1px dashed rgba($color-brand, 0.45);
  border-radius: $radius-md;
  overflow: hidden;
  opacity: 0;
  animation: claim-in 0.4s cubic-bezier(0.16, 1, 0.3, 1) both;
  animation-delay: calc(var(--i, 0) * 50ms);
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    transform: translateY(-2px);
    border-color: $color-brand;
    box-shadow: 0 10px 24px rgba($color-brand, 0.16);
  }
}

.claim-left {
  flex-shrink: 0;
  width: 88px;
  align-self: stretch;
  padding: $spacing-md $spacing-xs;
  background: linear-gradient(135deg, $color-brand, $color-brand-dark);
  color: $color-on-brand;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.claim-value {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 800;
  line-height: 1.1;
}

.claim-threshold {
  font-size: 11px;
  margin-top: 2px;
  opacity: 0.9;
}

.claim-body {
  flex: 1;
  min-width: 0;
}

.claim-name {
  margin: 0 0 2px;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
  @include text-ellipsis(1);
}

.claim-valid {
  margin: 0;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.claim-btn {
  flex-shrink: 0;
  min-width: 72px;
  padding: $spacing-xs $spacing-md;
  border: none;
  border-radius: $radius-full;
  background: $color-brand;
  color: $color-on-brand;
  font-size: $font-size-label-sm;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 16px;
    vertical-align: middle;
  }

  &:hover:not(:disabled) {
    background: $color-brand-dark;
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: scale(0.95);
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
}

.sub-section-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0 0 $spacing-md;
}

@keyframes claim-in {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: none; }
}

.claim-btn .spin {
  animation: state-spin 1s linear infinite;
}

@media (prefers-reduced-motion: reduce) {
  .claim-card { animation: none; opacity: 1; }
}

/* 状态筛选 Tab（通栏分段，与我的订单一致） */
.tabs {
  display: flex;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  margin-bottom: $spacing-lg;
  overflow: hidden;
  box-shadow: $shadow-sm;
}

.tab {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: $spacing-md 0;
  border: none;
  background: transparent;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  font-family: inherit;
  font-weight: 600;
  cursor: pointer;
  transition: all $transition-fast;
  position: relative;

  &:hover {
    color: $color-brand;
  }

  &.active {
    color: $color-brand;
    font-weight: 700;

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 25%;
      right: 25%;
      height: 3px;
      background: $color-brand;
      border-radius: $radius-full;
    }
  }

  .tab-count {
    font-size: $font-size-label-sm;
    opacity: 0.85;
    min-width: 14px;
    text-align: center;
  }
}

.coupon-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.coupon-card {
  display: flex;
  align-items: center;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  overflow: hidden;
  position: relative;
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-md;
  }

  /* 已使用 / 已过期:整体降噪 */
  &.used,
  &.expired {
    opacity: 0.62;

    .coupon-left {
      background: linear-gradient(135deg, $color-text-tertiary, $color-on-surface-variant);
    }
  }
}

.coupon-left {
  flex-shrink: 0;
  width: 110px;
  padding: $spacing-lg $spacing-md;
  background: linear-gradient(135deg, $color-brand, $color-brand-dark);
  color: $color-on-brand;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  align-self: stretch;
}

.coupon-value {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 800;
  line-height: 1.1;
}

.coupon-threshold {
  font-size: $font-size-label-sm;
  margin-top: 4px;
  opacity: 0.9;
}

.coupon-body {
  flex: 1;
  padding: $spacing-md $spacing-lg;
  min-width: 0;
}

.coupon-name {
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0 0 4px;
}

.coupon-valid {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin: 0;
}

.coupon-right {
  flex-shrink: 0;
  margin-right: $spacing-md;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: $spacing-xs;
}

.status-pill {
  font-size: $font-size-label-sm;
  font-weight: 600;
  padding: 2px $spacing-sm;
  border-radius: $radius-full;

  &.usable {
    background: rgba($color-success, 0.14);
    color: $color-success;
  }

  &.used,
  &.expired {
    background: $color-surface-container;
    color: $color-on-surface-variant;
  }
}

.use-btn {
  padding: $spacing-xs $spacing-lg;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  background: $color-brand;
  color: $color-on-brand;
  transition: background $transition-fast, transform $transition-fast;

  &:hover {
    background: $color-brand-dark;
    transform: translateY(-1px);
  }

  &:active {
    transform: scale(0.96);
  }
}

/* 已使用 / 已过期 角标印章 */
.stamp {
  position: absolute;
  top: 12px;
  right: 14px;
  font-family: $font-family-headline;
  font-size: $font-size-body-md;
  font-weight: 800;
  letter-spacing: 2px;
  color: $color-danger;
  border: 2px solid $color-danger;
  border-radius: $radius-sm;
  padding: 2px 8px;
  transform: rotate(-12deg);
  opacity: 0.7;
  pointer-events: none;
}

/* 空状态（与我的订单一致） */
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-2xl $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  text-align: center;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 56px;
    color: $color-text-tertiary;
    opacity: 0.6;
  }

  p {
    margin: 0;
    font-size: $font-size-body-md;
  }

  .state-sub {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    opacity: 0.85;
  }

  .spin {
    animation: state-spin 1s linear infinite;
  }
}

.state-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-brand;

  .material-symbols-outlined {
    font-size: 56px;
    opacity: 0.6;
  }
}

.state-title {
  margin: 0;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.retry-btn {
  margin-top: $spacing-sm;
  padding: $spacing-xs $spacing-xl;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  &:hover {
    background: $color-brand-dark;
    transform: translateY(-1px);
  }

  &:active {
    transform: scale(0.96);
  }
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
