<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi, type Footprint } from '@/api/modules/user'
import defaultCover from '@/assets/images/category/p1-catfood.jpg'
import { resolveCover } from '@/utils/articleCovers'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

// 分类 Tab（与截图一致）
type CatTab = 'all' | 'product' | 'knowledge' | 'community'
const activeTab = ref<CatTab>('all')
const catTabs: { key: CatTab; label: string }[] = [
  { key: 'all', label: '全部' },
  { key: 'product', label: '商品' },
  { key: 'knowledge', label: '知识科普' },
  { key: 'community', label: '社区动态' },
]

// 按分类 Tab 真实过滤（商品/知识科普/社区动态各自独立）
const filteredList = computed<TimeGroup[]>(() => {
  if (activeTab.value === 'all') return grouped.value
  const t = activeTab.value
  return grouped.value
    .map((g) => ({ ...g, items: g.items.filter((f) => (f.type || 'product') === t) }))
    .filter((g) => g.items.length > 0)
})

const loading = ref(false)
const all = ref<Footprint[]>([])
const removing = ref<number | null>(null)
const clearing = ref(false)

const isEmpty = computed(() => !loading.value && filteredList.value.length === 0)

// 空状态提示随分类切换
const emptyTitle = computed(() => {
  switch (activeTab.value) {
    case 'product':
      return '还没有浏览过商品'
    case 'knowledge':
      return '还没有看过知识科普'
    case 'community':
      return '还没有看过社区动态'
    default:
      return '还没有留下任何足迹'
  }
})

const emptySub = computed(() => {
  switch (activeTab.value) {
    case 'product':
      return '你浏览过的商品会出现在这里'
    case 'knowledge':
      return '你看过的小知识科普会出现在这里'
    case 'community':
      return '你看过的社区动态会出现在这里'
    default:
      return '逛逛商城，你看过的好物都会悄悄留在这里'
  }
})

async function load() {
  loading.value = true
  try {
    all.value = await userApi.footprints()
  } catch {
    ElMessage.error('足迹加载失败')
    all.value = []
  } finally {
    loading.value = false
  }
}

// 封面按类型解析：商品用 /static 直链，知识科普需经 resolveCover 把源码路径转真实资源
function coverOf(f: Footprint): string {
  if (f.type === 'knowledge') return resolveCover(f.mainImage)
  return f.mainImage || defaultCover
}

function openItem(f: Footprint) {
  if (f.type === 'knowledge') {
    router.push(`/knowledge/${f.productId}`)
    return
  }
  // 社区动态走帖子详情，不能复用商品路由（旧实现会跳到不存在的商品页）
  if (f.type === 'community') {
    if (f.offline) {
      ElMessage.warning('该动态已被删除')
      return
    }
    router.push(`/community/post/${f.productId}`)
    return
  }
  if (f.offline) {
    ElMessage.warning('该商品已下架')
    return
  }
  router.push(`/product/${f.productId}`)
}

async function remove(f: Footprint) {
  if (removing.value) return
  removing.value = f.id
  try {
    await userApi.deleteFootprint(f.id)
    ElMessage.success('已删除该足迹')
    all.value = all.value.filter((x) => x.id !== f.id)
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    removing.value = null
  }
}

async function clearAll() {
  if (clearing.value || all.value.length === 0) return
  try {
    await ElMessageBox.confirm('确定要清空全部浏览足迹吗？', '清空足迹', {
      confirmButtonText: '清空',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  clearing.value = true
  try {
    await userApi.clearFootprints()
    ElMessage.success('已清空全部足迹')
    all.value = []
  } catch (e: any) {
    ElMessage.error(e?.message || '清空失败')
  } finally {
    clearing.value = false
  }
}

// ===== 时间分组 =====
interface TimeGroup {
  key: string
  label: string
  items: Footprint[]
}

function getTimeGroup(d: Date): string {
  const now = new Date()
  const today = now.toDateString()
  if (d.toDateString() === today) return 'today'
  const yest = new Date(now)
  yest.setDate(now.getDate() - 1)
  if (d.toDateString() === yest.toDateString()) return 'yesterday'
  return 'earlier'
}

const groupLabels: Record<string, string> = {
  today: '今天',
  yesterday: '昨天',
  earlier: '更早之前',
}

const grouped = computed<TimeGroup[]>(() => {
  const map = new Map<string, Footprint[]>()
  for (const f of all.value) {
    const d = f.lastViewAt ? new Date(f.lastViewAt.replace(' ', 'T')) : new Date()
    if (isNaN(d.getTime())) continue
    const k = getTimeGroup(d)
    if (!map.has(k)) map.set(k, [])
    map.get(k)!.push(f)
  }
  // 固定顺序：今天 → 昨天 → 更早之前
  const order = ['today', 'yesterday', 'earlier']
  return order
    .filter((k) => map.has(k))
    .map((k) => ({ key: k, label: groupLabels[k], items: map.get(k)! }))
})

// 相对时间显示（卡片底部 meta 行）
function formatCardTime(value?: string | null): string {
  if (!value) return ''
  const d = new Date(value.replace(' ', 'T'))
  if (isNaN(d.getTime())) return value
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  const sameDay = d.toDateString() === now.toDateString()
  if (sameDay) return `今天 ${hm}`
  const y = new Date(now)
  y.setDate(now.getDate() - 1)
  if (d.toDateString() === y.toDateString()) return `昨天 ${hm}`
  return `${d.getMonth() + 1}月${d.getDate()}日 ${hm}`
}

onMounted(load)
</script>

<template>
  <div class="footprint-view">
    <div class="fp-container">
      <!-- 面包屑 -->
      <nav class="breadcrumb">
        <span>个人中心</span>
        <span class="sep">›</span>
        <span class="current">我的足迹</span>
      </nav>

      <!-- 页头 -->
      <div class="page-head">
        <h1 class="page-title">我的足迹</h1>
        <div class="head-actions">
          <button class="action-btn outline" :disabled="clearing" @click="clearAll">
            <span class="material-symbols-outlined">{{ clearing ? 'progress_activity' : 'delete_sweep' }}</span>
            <span>{{ clearing ? '清空中…' : '清空记录' }}</span>
          </button>
        </div>
      </div>

      <!-- 分类 Tabs -->
      <div class="cat-tabs">
        <button
          v-for="t in catTabs"
          :key="t.key"
          class="cat-tab"
          :class="{ active: activeTab === t.key }"
          @click="activeTab = t.key"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- 主内容 -->
      <main class="fp-body">
        <!-- 骨架屏 -->
        <div v-if="loading" class="skeleton-grid">
          <div v-for="n in 6" :key="n" class="sk-card">
            <div class="sk-img skeleton" />
            <div class="sk-info">
              <div class="sk-line skeleton" />
              <div class="sk-line short skeleton" />
            </div>
          </div>
        </div>

        <!-- 空状态 -->
        <PetEmptyState v-else-if="isEmpty" icon="history" :title="emptyTitle" :desc="emptySub">
          <template #action>
            <button
              class="retry-btn"
              @click="router.push(activeTab === 'community' ? '/community' : activeTab === 'knowledge' ? '/knowledge' : '/category')"
            >
              去逛逛
            </button>
          </template>
        </PetEmptyState>

        <!-- 时间分组列表 -->
        <template v-else>
          <section v-for="group in filteredList" :key="group.key" class="time-group">
            <div class="group-header">
              <span class="group-dot" :class="{ hot: group.key === 'today' }"></span>
              <h2 class="group-label">{{ group.label }}</h2>
            </div>

            <div class="card-grid">
              <article
                v-for="(f, idx) in group.items"
                :key="f.id"
                class="fp-card pet-card"
                :class="{ offline: f.offline, 'is-knowledge': f.type === 'knowledge' }"
                :style="{ '--i': idx }"
                @click="openItem(f)"
              >
                <div class="fp-cover">
                  <img :src="coverOf(f)" :alt="f.name || '内容'" />
                  <!-- 左上角类型小图标：商品 / 知识科普 / 社区动态 -->
                  <span class="fp-type" :class="'t-' + f.type">
                    <span class="material-symbols-outlined">{{ f.type === 'knowledge' ? 'menu_book' : f.type === 'community' ? 'forum' : 'shopping_bag' }}</span>
                  </span>
                  <span v-if="f.offline" class="fp-off">{{ f.type === 'product' || !f.type ? '已下架' : '已删除' }}</span>
                </div>
                <div class="fp-info">
                  <h3 class="fp-name">{{ f.name || '内容已删除' }}</h3>
                  <div v-if="(f.type || 'product') === 'product'" class="fp-price-row">
                    <span v-if="f.salePrice != null" class="fp-price">¥{{ Number(f.salePrice).toFixed(2) }}</span>
                    <span class="fp-cart-ico"><span class="material-symbols-outlined">shopping_bag</span></span>
                  </div>
                  <div class="fp-meta">
                    <span class="meta-item">
                      <span class="material-symbols-outlined">visibility</span>
                      {{ f.viewCount ?? 0 }} 浏览
                    </span>
                    <span class="meta-item">
                      <span class="material-symbols-outlined">schedule</span>
                      {{ formatCardTime(f.lastViewAt) }}
                    </span>
                  </div>
                </div>
                <!-- 删除按钮（悬停显示） -->
                <button
                  class="fp-del"
                  :class="{ removing: removing === f.id }"
                  :disabled="removing === f.id"
                  @click.stop="remove(f)"
                  title="删除足迹"
                >
                  <span class="material-symbols-outlined">{{ removing === f.id ? 'progress_activity' : 'close' }}</span>
                </button>
              </article>
            </div>
          </section>
        </template>
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

.footprint-view {
  min-height: 100vh;
  background: $color-background;
}

.fp-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
}

/* ── 面包屑 ── */
.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-md;

  .sep { color: $color-text-tertiary; }
  .current { color: $color-brand; font-weight: 600; }
}

/* ── 页头 ── */
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.head-actions {
  display: flex;
  gap: $spacing-sm;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined { font-size: 18px; }

  &.outline {
    background: transparent;
    border: 1px solid $color-outline-variant;
    color: $color-on-surface;

    &:hover:not(:disabled) {
      border-color: $color-danger;
      color: $color-danger;
    }
  }

  &:disabled { opacity: 0.45; cursor: not-allowed; }
}

/* ── 分类 Tabs（通栏分段） ── */
.cat-tabs {
  display: flex;
  gap: $spacing-xs;
  margin-bottom: $spacing-xl;
  background: $color-surface-container-low;
  padding: 4px;
  border-radius: $radius-full;
  width: fit-content;
}

.cat-tab {
  padding: $spacing-sm $spacing-lg;
  border: none;
  border-radius: $radius-full;
  background: transparent;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  &.active {
    background: $color-brand;
    color: #ffffff;
    box-shadow: 0 4px 12px rgba(255, 122, 50, 0.28);
  }

  &:hover:not(.active) {
    color: $color-on-surface;
    background: $color-surface-container-high;
  }
}

/* ── 主内容区 ── */
.fp-body {
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
}

/* ── 时间分组 ── */
.time-group {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.group-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.group-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: $color-outline-variant;
  flex-shrink: 0;

  &.hot {
    background: $color-brand;
    box-shadow: 0 0 8px rgba(255, 122, 50, 0.5);
  }
}

.group-label {
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0;
}

/* ── 卡片网格 ── */
.card-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
  }
}

/* ── 单张卡片 ── */
.fp-card {
  position: relative;
  display: flex;
  flex-direction: column;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  overflow: hidden;
  cursor: pointer;
  opacity: 0;
  animation: fp-in 0.45s cubic-bezier(0.16, 1, 0.3, 1) both;
  animation-delay: calc(var(--i, 0) * 50ms);
  transition: box-shadow $transition-base, transform $transition-fast, border-color $transition-fast;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 14px 32px rgba(0, 0, 0, 0.1);
    border-color: rgba($color-brand, 0.35);

    .fp-cover img { transform: scale(1.06); }
    .fp-del { opacity: 1; }
  }

  &.offline {
    opacity: 0.7;
    .fp-cover { filter: grayscale(0.4); }
  }
}

.fp-cover {
  position: relative;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: $color-surface-container;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.55s cubic-bezier(0.16, 1, 0.3, 1);
  }
}

// 左上角类型小图标
.fp-type {
  position: absolute;
  top: $spacing-xs;
  left: $spacing-xs;
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: $color-brand;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(2px);
  z-index: 2;

  .material-symbols-outlined {
    font-size: 16px;
    line-height: 1;
  }

  &.t-knowledge { background: #2f7de1; }
  &.t-community { background: #7a5cff; }
  &.t-product { background: $color-brand; }
}

// 右上角「已下架」状态
.fp-off {
  position: absolute;
  top: $spacing-xs;
  right: $spacing-xs;
  padding: 2px 8px;
  border-radius: $radius-sm;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(2px);
  z-index: 2;
}

.fp-info {
  display: flex;
  flex-direction: column;
  gap: 5px;
  padding: $spacing-xs $spacing-sm $spacing-sm;
}

.fp-name {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0;
  @include text-ellipsis(2);
  line-height: 1.4;
  min-height: calc(#{$font-size-body-md} * 1.4 * 2);
}

.fp-price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
}

.fp-price {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-brand;
}

.fp-cart-ico {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand;
  color: #fff;
  border-radius: $radius-default;

  .material-symbols-outlined { font-size: 16px; }
}

.fp-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-top: 2px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;

  .material-symbols-outlined { font-size: 14px; }
}

/* 删除按钮（悬停显） */
.fp-del {
  position: absolute;
  top: $spacing-sm;
  right: $spacing-sm;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: $radius-full;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  cursor: pointer;
  opacity: 0;
  transition: opacity $transition-fast, background $transition-fast, transform $transition-fast;

  .material-symbols-outlined { font-size: 16px; }

  &:hover:not(:disabled) {
    background: $color-danger;
    transform: scale(1.1);
  }

  &:active:not(:disabled) { transform: scale(0.9); }

  &.removing .material-symbols-outlined { animation: spin 0.8s linear infinite; }
  &:disabled { cursor: not-allowed; opacity: 0.7; }
}

/* ── 骨架屏 ── */
.skeleton-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include desktop { grid-template-columns: repeat(4, 1fr); }
}

.sk-card {
  display: flex;
  flex-direction: column;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  overflow: hidden;
}

.sk-img {
  aspect-ratio: 4 / 3;
}

.sk-info {
  padding: $spacing-sm $spacing-md $spacing-md;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sk-line {
  height: 14px;
  border-radius: 6px;
  width: 75%;
}
.sk-line.short { width: 45%; }

.skeleton {
  background: linear-gradient(
    90deg,
    $color-surface-container 0%,
    $color-surface-container-lowest 50%,
    $color-surface-container 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s ease infinite;
}

/* ── 空状态 ── */
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-3xl $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  text-align: center;
}

.state-icon-wrap {
  width: 84px;
  height: 84px;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand-container;
  color: $color-brand;
  margin-bottom: $spacing-xs;

  .material-symbols-outlined { font-size: 56px; opacity: 0.6; }
}

.state-title {
  margin: 0;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.state-sub {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  max-width: 280px;
  line-height: 1.5;
}

.retry-btn {
  margin-top: $spacing-sm;
  padding: $spacing-xs $spacing-xl;
  background: $color-brand;
  color: #fff;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 8px 20px rgba($color-brand, 0.3);
  }
  &:active { transform: scale(0.96); }
}

/* ── 动画 ── */
@keyframes fp-in {
  from { opacity: 0; transform: translateY(12px); }
  to   { opacity: 1; transform: none; }
}
@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

@media (prefers-reduced-motion: reduce) {
  .fp-card { animation: none; opacity: 1; }
  .skeleton { animation: none; }
  .fp-del.removing .material-symbols-outlined { animation: none; }
}
</style>
