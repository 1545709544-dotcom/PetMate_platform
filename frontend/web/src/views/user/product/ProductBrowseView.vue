<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi, type Product as ApiProduct } from '@/api/modules/product'
import { useProductCategories } from '@/composables/useProductCategories'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

// 评价数缓存：productId -> reviewCount（避免重复拉同一个商品的 summary）
const reviewCountMap = ref<Record<number, number>>({})

const router = useRouter()
const route = useRoute()

// ============ 顶部小分类（截图右上）============
// 分类结构统一取自 useProductCategories（单一数据源，与数据库 t_product_category 实时同步）
const { topTabs, tabCategoryMap, ensureTree } = useProductCategories()
const activeTopTab = ref('all')

// ============ 左侧筛选 ============
const sortOptions = [
  { id: 'all', label: '所有产品', icon: 'apps' },
  { id: 'price_asc', label: '价格：由低到高', icon: 'payments' },
  { id: 'sales', label: '畅销排行', icon: 'trending_up' },
  { id: 'new', label: '新品上架', icon: 'auto_awesome' },
  { id: 'rating', label: '高好评', icon: 'star' },
]
const activeSort = ref('all')

// ============ 顶部排序 tabs ============
const sortTabs = [
  { id: 'composite', label: '综合排序' },
  { id: 'sales', label: '销量' },
  { id: 'new', label: '新品' },
  { id: 'price', label: '价格' },
]
const activeSortTab = ref('composite')
const priceOrder = ref<'asc' | 'desc'>('asc')

function togglePrice() {
  if (activeSortTab.value !== 'price') {
    activeSortTab.value = 'price'
    priceOrder.value = 'asc'
  } else {
    priceOrder.value = priceOrder.value === 'asc' ? 'desc' : 'asc'
  }
}

// ============ 视图模式 ============
const viewMode = ref<'grid' | 'list'>('grid')

// ============ 商品数据 ============
// 展示用结构（由后端 Product 映射而来）
interface Product {
  id: number
  categoryId: number
  cover: string
  name: string
  tag?: 'hot' | 'new' | null
  tags: string[]
  price: number
  originPrice: number
  reviews: number
  rating: number
  sales: number
  createdAt?: string
}

// 后端 Product -> 展示 Product 映射
//  - tag：后端无对应字段，固定 null（不展示角标）
//  - reviews：从 reviewCountMap 取真实评价数；缺数据时回落 0
function mapProduct(p: ApiProduct): Product {
  const tags: string[] = (() => {
    if (!p.tags) return []
    try {
      return typeof p.tags === 'string' ? JSON.parse(p.tags as unknown as string) : p.tags
    } catch {
      return []
    }
  })()
  return {
    id: p.id,
    categoryId: p.categoryId,
    cover: p.mainImage,
    name: p.name,
    tag: null,
    tags,
    price: p.salePrice,
    originPrice: p.originalPrice ?? p.salePrice,
    reviews: reviewCountMap.value[p.id] ?? 0,
    rating: p.rating ?? 5,
    sales: p.saleCount ?? 0,
    createdAt: (p as Record<string, unknown>).createdAt as string | undefined,
  }
}

const keyword = ref('')
const loading = ref(false)
const products = ref<Product[]>([])
const total = ref(0)

// ============ 分页 ============
const page = ref(1)
const pageSize = 24
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const pageNumbers = computed(() => {
  const tp = totalPages.value
  if (tp <= 5) return Array.from({ length: tp }, (_, i) => i + 1)
  // 简化：显示 1 ... (page-1, page, page+1) ... last
  const set = new Set<number>([1, tp, page.value - 1, page.value, page.value + 1])
  return Array.from(set).filter((n) => n >= 1 && n <= tp).sort((a, b) => a - b)
})

// 排序 + 客户端分页（浏览/搜索均已一次取回全量，故在此统一切片）
const pagedProducts = computed(() => {
  const list = [...products.value]
  if (activeSort.value === 'price_asc') list.sort((a, b) => a.price - b.price)
  else if (activeSort.value === 'sales') list.sort((a, b) => b.sales - a.sales)
  else if (activeSort.value === 'rating') list.sort((a, b) => b.rating - a.rating)
  else if (activeSort.value === 'new') list.sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))

  if (activeSortTab.value === 'sales') list.sort((a, b) => b.sales - a.sales)
  else if (activeSortTab.value === 'new') list.sort((a, b) => (b.createdAt || '').localeCompare(a.createdAt || ''))
  else if (activeSortTab.value === 'price') {
    list.sort((a, b) => (priceOrder.value === 'asc' ? a.price - b.price : b.price - a.price))
  }

  const start = (page.value - 1) * pageSize
  return list.slice(start, start + pageSize)
})

// ============ 数据加载 ============
// 浏览页核心修复：
//  - 默认加载 / 切换小分类时，用 /product/list 取全量商品，再按当前小分类映射的
//    L2 类目 id 过滤（之前用中文标签“宠物食品”当关键词调 /search，name LIKE 匹配不到 → 空白）。
//  - 搜索时走 /search，但同样一次取全量，排序/分页在前端统一处理。
async function loadProducts() {
  loading.value = true
  try {
    const kw = keyword.value.trim()
    const resp = kw
      ? await productApi.search({ keyword: kw, page: 1, size: 200 })
      : await productApi.listByCategory(undefined, 1, 200)

    let rows = resp.list || []
    // 浏览模式按顶部小分类过滤；搜索模式不做类目过滤
    if (!kw) {
      const ids = tabCategoryMap.value[activeTopTab.value]
      if (ids && ids.length) rows = rows.filter((r) => ids.includes(r.categoryId))
    }
    // 列表接口已直接返回真实 reviewCount，不再为每件商品单发一次 summary 请求
    // （原来 200 件商品 = 200 个并发请求，首屏被拖慢且经常打满连接池）
    rows.forEach((r) => {
      reviewCountMap.value[r.id] = Number(r.reviewCount ?? 0)
    })
    products.value = rows.map(mapProduct)
    total.value = products.value.length
  } catch {
    ElMessage.error('商品加载失败，请稍后重试')
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  // 先确保分类树加载完成,顶部小分类 tab 才能正确渲染与过滤
  try {
    await ensureTree()
  } catch {
    /* 分类加载失败不阻塞商品加载 */
  }
  // 首页「甄选好物」分类卡跳转带 ?cat=一级分类id，匹配到对应 tab
  const catQuery = route.query.cat
  if (catQuery && topTabs.value.some((t) => t.key === String(catQuery))) {
    activeTopTab.value = String(catQuery)
  }
  loadProducts()
})

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  // 数据已在前端内存中，无需重新请求，仅切片渲染
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// ============ 操作 ============
function confirmFilter() {
  page.value = 1
  loadProducts()
}

// 切换顶部小分类时重新按关键词加载
function selectTopTab(id: string) {
  activeTopTab.value = id
  page.value = 1
  loadProducts()
}

function goDetail(id: number) {
  router.push(`/product/${id}`)
}

function search() {
  page.value = 1
  loadProducts()
}
</script>

<template>
  <div class="browse-view">
    <div class="browse-container">
      <!-- 分类筛选 + 搜索（页面内过滤；品牌/购物车等已由全局 AppHeader 提供，此处不再重复）-->
      <div class="top-tabs">
        <div class="search-wrap">
          <span class="material-symbols-outlined search-icon">search</span>
          <input class="search-input" v-model="keyword" placeholder="搜索爱宠宠物用品..." @keydown.enter="search" />
        </div>
        <nav class="top-nav">
          <a
            v-for="t in topTabs"
            :key="t.key"
            class="top-tab"
            :class="{ active: activeTopTab === t.key }"
            href="javascript:;"
            @click="selectTopTab(t.key)"
          >{{ t.label }}</a>
        </nav>
      </div>

      <!-- 面包屑 -->
      <div class="breadcrumb no-vw">
        <a href="javascript:;" @click="router.push('/')">首页</a>
        <span class="sep">/</span>
        <span class="current">宠物商城</span>
      </div>

      <div class="layout">
        <!-- 左侧筛选 -->
        <aside class="filter-side">
          <div class="filter-card">
            <div class="filter-head">
              <span class="material-symbols-outlined filter-ico">tune</span>
              <div>
                <h3 class="filter-title">筛选与排序</h3>
                <p class="filter-sub">精挑细选的爱宠</p>
              </div>
            </div>
            <div class="filter-list">
              <button
                v-for="opt in sortOptions"
                :key="opt.id"
                class="filter-item"
                :class="{ active: activeSort === opt.id }"
                @click="activeSort = opt.id"
              >
                <span class="material-symbols-outlined filter-item-ico">{{ opt.icon }}</span>
                <span>{{ opt.label }}</span>
              </button>
            </div>
            <button class="confirm-btn" @click="confirmFilter">确认筛选</button>
          </div>
        </aside>

        <!-- 右侧主区 -->
        <main class="main-side">
          <!-- 排序 tabs -->
          <div class="sort-bar">
            <div class="sort-tabs">
              <button
                v-for="t in sortTabs"
                :key="t.id"
                class="sort-tab"
                :class="{ active: activeSortTab === t.id, 'price-active': t.id === 'price' && activeSortTab === 'price' }"
                @click="t.id === 'price' ? togglePrice() : (activeSortTab = t.id)"
              >
                <span>{{ t.label }}</span>
                <span
                  v-if="t.id === 'price'"
                  class="material-symbols-outlined price-arrow"
                  :class="{ asc: priceOrder === 'asc', desc: priceOrder === 'desc' }"
                >unfold_more</span>
              </button>
            </div>
            <div class="view-toggle">
              <button
                class="view-btn"
                :class="{ active: viewMode === 'grid' }"
                @click="viewMode = 'grid'"
                aria-label="网格视图"
              >
                <span class="material-symbols-outlined">grid_view</span>
              </button>
              <button
                class="view-btn"
                :class="{ active: viewMode === 'list' }"
                @click="viewMode = 'list'"
                aria-label="列表视图"
              >
                <span class="material-symbols-outlined">view_list</span>
              </button>
            </div>
          </div>

          <!-- 加载中 -->
          <div v-if="loading" class="state-block">
            <span class="material-symbols-outlined spin">progress_activity</span>
            <p>加载中…</p>
          </div>

          <!-- 空状态 -->
          <PetEmptyState
            v-else-if="pagedProducts.length === 0"
            icon="inventory_2"
            title="暂无相关商品"
            desc="换个分类或筛选条件试试吧～"
          />

          <!-- 商品网格 -->
          <div v-else-if="viewMode === 'grid'" class="prod-grid">
            <article
              v-for="p in pagedProducts"
              :key="p.id"
              class="prod-card pet-card"
              @click="goDetail(p.id)"
            >
              <div class="prod-cover">
                <img :src="p.cover" :alt="p.name" />
                <span v-if="p.tag === 'hot'" class="prod-badge hot">热销</span>
                <span v-else-if="p.tag === 'new'" class="prod-badge new">新品</span>
              </div>
              <div class="prod-tags">
                <span v-for="t in p.tags" :key="t" class="prod-tag">{{ t }}</span>
              </div>
              <h4 class="prod-name">{{ p.name }}</h4>
              <div class="prod-price-row">
                <span class="prod-price">¥{{ p.price.toFixed(2) }}</span>
                <span class="prod-origin">¥{{ p.originPrice.toFixed(2) }}</span>
              </div>
            </article>
          </div>

          <!-- 列表视图 -->
          <div v-else class="prod-list">
            <article
              v-for="p in pagedProducts"
              :key="p.id"
              class="list-card pet-card"
              @click="goDetail(p.id)"
            >
              <div class="list-cover">
                <img :src="p.cover" :alt="p.name" />
              </div>
              <div class="list-body">
                <h4 class="list-name">{{ p.name }}</h4>
                <div class="list-tags">
                  <span v-for="t in p.tags" :key="t" class="prod-tag">{{ t }}</span>
                </div>
                <p class="list-desc">★★★★★ {{ p.rating }} · {{ p.reviews }} 条评价 · 已售 {{ p.sales }}</p>
              </div>
              <div class="list-right">
                <div class="list-price-row">
                  <span class="prod-price">¥{{ p.price.toFixed(2) }}</span>
                  <span class="prod-origin">¥{{ p.originPrice.toFixed(2) }}</span>
                </div>
              </div>
            </article>
          </div>

          <!-- 分页 -->
          <nav v-if="total > 0" class="pagination">
            <button class="page-btn arrow" :disabled="page === 1" @click="goPage(page - 1)">
              <span class="material-symbols-outlined">chevron_left</span>
            </button>
            <template v-for="(n, idx) in pageNumbers" :key="n">
              <span v-if="idx > 0 && pageNumbers[idx - 1] !== n - 1" class="ellipsis">...</span>
              <button
                class="page-btn"
                :class="{ active: n === page }"
                @click="goPage(n)"
              >{{ n }}</button>
            </template>
            <button class="page-btn arrow" :disabled="page === totalPages" @click="goPage(page + 1)">
              <span class="material-symbols-outlined">chevron_right</span>
            </button>
          </nav>
        </main>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.browse-view {
  min-height: 100vh;
  background: $color-background;
}

.browse-container {
  max-width: $width-content-max;
  margin: 0 auto;
  padding: $spacing-md $width-gutter-mobile $spacing-2xl;

  @include desktop {
    padding: $spacing-md $width-margin-desktop $spacing-2xl;
  }
}

// ====== 顶部（分类筛选 + 搜索，非导航栏，避免与全局 AppHeader 重复）======
.top-tabs {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md 0 $spacing-lg;
  flex-wrap: wrap;
}

.search-wrap {
  position: relative;
  flex: 1;
  min-width: 240px;
  max-width: 480px;
}

.search-icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: $color-text-tertiary;
  font-size: 20px;
}

.search-input {
  width: 100%;
  padding: 10px 16px 10px 42px;
  background: $color-surface-container-low;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  outline: none;
  font-family: inherit;

  &:focus {
    box-shadow: 0 0 0 3px rgba($color-secondary, 0.15);
  }
}

.top-nav {
  display: flex;
  gap: $spacing-lg;

  @media (max-width: 768px) {
    display: none;
  }
}

.top-tab {
  font-size: $font-size-body-md;
  color: $color-text-secondary;
  cursor: pointer;
  text-decoration: none;
  transition: color $transition-fast;

  &:hover {
    color: $color-brand;
  }

  &.active {
    color: $color-brand;
    font-weight: $font-weight-semibold;
  }
}

// ====== 面包屑 ======
.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  margin-bottom: $spacing-md;

  .sep {
    color: $color-text-tertiary;
  }

  .current {
    color: $color-on-surface;
    font-weight: $font-weight-semibold;
  }

  a {
    color: $color-text-secondary;
    text-decoration: none;

    &:hover {
      color: $color-brand;
    }
  }
}

// ====== Layout ======
.layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;

  @include desktop {
    grid-template-columns: 256px 1fr;
  }
}

// ====== 左侧筛选 ======
.filter-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  padding: $spacing-lg;
  position: sticky;
  top: 96px;
}

.filter-head {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
}

.filter-ico {
  width: 36px;
  height: 36px;
  background: $color-brand-container;
  color: $color-on-brand;
  border-radius: $radius-default;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px !important;
}

.filter-title {
  font-family: $font-family-headline;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
}

.filter-sub {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 2px 0 0;
}

.filter-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: $spacing-lg;
}

.filter-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  width: 100%;
  padding: 10px $spacing-md;
  background: transparent;
  border: none;
  border-radius: $radius-default;
  color: $color-on-surface;
  font-size: $font-size-body-md;
  font-family: inherit;
  cursor: pointer;
  text-align: left;
  transition: background $transition-fast, color $transition-fast;
}

.filter-item:hover {
  background: $color-surface-container-low;
}

.filter-item.active {
  background: $color-secondary-container;
  color: $color-on-secondary;
  font-weight: $font-weight-semibold;
}

.filter-item-ico {
  font-size: 18px !important;
  color: $color-text-tertiary;
}

.filter-item.active .filter-item-ico {
  color: $color-on-secondary;
}

.confirm-btn {
  width: 100%;
  padding: 12px 0;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  cursor: pointer;
  box-shadow: 0 6px 16px rgba($color-brand, 0.3);
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-2px);
  }

  &:active {
    transform: translateY(0);
  }
}

// ====== 右侧主区 ======
.main-side {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  min-width: 0;
}

.sort-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-xs $spacing-md;
  box-shadow: $shadow-card;
}

.sort-tabs {
  display: flex;
  gap: $spacing-xs;
}

.sort-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: transparent;
  border: none;
  border-radius: $radius-full;
  color: $color-on-surface;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast;
}

.sort-tab:hover {
  background: $color-surface-container-low;
}

.sort-tab.active {
  background: $color-brand;
  color: $color-on-brand;
  font-weight: $font-weight-semibold;
}

.sort-tab.price-active {
  background: $color-brand;
  color: $color-on-brand;
  font-weight: $font-weight-semibold;
}

.price-arrow {
  font-size: 18px !important;
}

.price-arrow.asc {
  transform: rotate(180deg);
  color: $color-on-brand;
}

.price-arrow.desc {
  color: $color-on-brand;
}

.view-toggle {
  display: flex;
  gap: 4px;
  background: $color-surface-container-low;
  border-radius: $radius-default;
  padding: 4px;
}

.view-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  border-radius: $radius-sm;
  color: $color-text-tertiary;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.view-btn.active {
  background: $color-surface-container-lowest;
  color: $color-brand;
  box-shadow: $shadow-sm;
}

// ====== 网格商品 ======
.prod-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include tablet {
    grid-template-columns: repeat(3, 1fr);
  }

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
  }
}

.prod-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  overflow: hidden;
  cursor: pointer;
  box-shadow: $shadow-card;
  transition: transform $transition-fast, box-shadow $transition-fast;
  display: flex;
  flex-direction: column;
}

.prod-card:hover {
  transform: translateY(-4px);
  box-shadow: $shadow-lg;
}

.prod-cover {
  position: relative;
  aspect-ratio: 1;
  background: $color-surface-container;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.prod-badge {
  position: absolute;
  top: $spacing-sm;
  left: $spacing-sm;
  padding: 2px 8px;
  border-radius: $radius-sm;
  font-size: 11px;
  font-weight: $font-weight-bold;
  color: #fff;
}

.prod-badge.hot {
  background: $color-emergency;
}

.prod-badge.new {
  background: $color-secondary;
}

.prod-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: $spacing-sm $spacing-sm 0;
  margin-bottom: 6px;
}

.prod-tag {
  font-size: 10px;
  padding: 2px 6px;
  background: rgba($color-secondary, 0.12);
  color: $color-secondary;
  border-radius: $radius-sm;
}

.prod-name {
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  margin: 0 $spacing-sm $spacing-xs;
  @include text-ellipsis(2);
  line-height: 1.4;
  min-height: 2.8em;
}

.prod-price-row {
  display: flex;
  align-items: baseline;
  gap: $spacing-xs;
  padding: 0 $spacing-sm $spacing-sm;
}

.prod-price {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-brand;
}

.prod-origin {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  text-decoration: line-through;
}

// ====== 列表商品 ======
.prod-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.list-card {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  cursor: pointer;
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-2px);
  }
}

.list-cover {
  width: 96px;
  height: 96px;
  border-radius: $radius-default;
  overflow: hidden;
  flex-shrink: 0;
  background: $color-surface-container;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.list-body {
  flex: 1;
  min-width: 0;
}

.list-name {
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  margin: 0 0 4px;
}

.list-tags {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}

.list-desc {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 0;
}

.list-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: $spacing-sm;
  flex-shrink: 0;
}

.list-price-row {
  display: flex;
  align-items: baseline;
  gap: $spacing-xs;
}

// ====== 分页 ======
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: $spacing-lg;
}

.page-btn {
  min-width: 36px;
  height: 36px;
  padding: 0 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  color: $color-on-surface;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 20px;
  }

  &:hover:not(:disabled):not(.active) {
    border-color: $color-brand;
    color: $color-brand;
  }

  &.active {
    background: $color-brand;
    color: $color-on-brand;
    border-color: $color-brand;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.page-btn.arrow {
  background: $color-surface-container-low;
}

.ellipsis {
  padding: 0 4px;
  color: $color-text-tertiary;
}

// ====== 加载/空状态 ======
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-2xl 0;
  color: $color-text-secondary;

  .material-symbols-outlined {
    font-size: 48px;
    color: $color-text-tertiary;
  }

  p {
    margin: 0;
    font-size: $font-size-body-md;
  }

  .spin {
    animation: state-spin 1s linear infinite;
  }
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
