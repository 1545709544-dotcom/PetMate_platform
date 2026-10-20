<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi, type Product } from '@/api/modules/product'
import { metaApi } from '@/api/modules/meta'
import { useCartStore } from '@/stores/cart'
import { useUser } from '@/composables/useUser'

const router = useRouter()
const route = useRoute()
const cart = useCartStore()
const { isLoggedIn } = useUser()

const keyword = ref((route.query.keyword as string) || '')
const loading = ref(false)
const products = ref<Product[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

const hotWords = ref<string[]>([])

async function loadHotWords() {
  try {
    const list = await metaApi.hotWords()
    hotWords.value = list || []
  } catch (e) {
    console.warn('[Search] hotWords 加载失败', e)
    hotWords.value = []
  }
}

async function doSearch(reset = true) {
  const kw = keyword.value.trim()
  if (!kw) {
    products.value = []
    total.value = 0
    return
  }
  if (reset) page.value = 1
  loading.value = true
  try {
    const resp = await productApi.search({ keyword: kw, page: page.value, size: pageSize })
    products.value = resp.list || []
    total.value = resp.total || 0
  } catch {
    ElMessage.error('搜索失败，请稍后重试')
    products.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  doSearch(false)
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function goDetail(id: number) {
  router.push(`/product/${id}`)
}

// 加购：后端搜索结果已带 defaultSkuId，直接下单该默认规格；
// 多规格商品需要用户选规格，无 defaultSkuId 时才回退到详情页（按钮文案同步区分，#5）
const addingIds = ref<Set<number>>(new Set())

function isMultiSpec(p: Product) {
  return !(p as any).defaultSkuId
}

async function addToCart(p: Product, e: Event) {
  e.stopPropagation()
  const skuId = (p as any).defaultSkuId as number | undefined
  if (!skuId) {
    router.push(`/product/${p.id}`)
    return
  }
  if (!isLoggedIn.value) {
    ElMessage.warning('请先登录后再加入购物车')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (addingIds.value.has(p.id)) return
  const s = new Set(addingIds.value)
  s.add(p.id)
  addingIds.value = s
  try {
    await cart.add(skuId, 1)
    await cart.fetch()
    ElMessage.success(`已加入购物车：${p.name}`)
  } catch (err: any) {
    ElMessage.error(err?.message || '加购失败，请重试')
  } finally {
    const s2 = new Set(addingIds.value)
    s2.delete(p.id)
    addingIds.value = s2
  }
}

// 角标样式：热销→红、新品→蓝、其余→品牌橙
function badgeClass(tag: string): string {
  if (tag === '热销') return 'hot'
  if (tag === '新品') return 'new'
  return ''
}

function fillHot(w: string) {
  keyword.value = w
  doSearch()
}

onMounted(async () => {
  await loadHotWords()
  if (keyword.value) doSearch()
})
</script>

<template>
  <div class="search-view">
    <!-- 顶部搜索栏 -->
    <header class="search-bar">
      <button class="back-btn" @click="router.back()">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <div class="search-input-wrap">
        <span class="material-symbols-outlined search-icon">search</span>
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索爱宠用品 / 服务..."
          @keydown.enter="doSearch()"
        />
        <button v-if="keyword" class="clear-btn" @click="keyword = ''">
          <span class="material-symbols-outlined">close</span>
        </button>
      </div>
      <button class="search-btn" @click="doSearch()">搜索</button>
    </header>

    <!-- 热门搜索 -->
    <section v-if="!keyword" class="hot-section">
      <p class="hot-title">热门搜索</p>
      <div class="hot-tags">
        <button v-for="w in hotWords" :key="w" class="hot-tag" @click="fillHot(w)">{{ w }}</button>
      </div>
    </section>

    <!-- 结果区 -->
    <main class="result-area">
      <div v-if="loading" class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <p>搜索中…</p>
      </div>

      <div v-else-if="products.length === 0 && keyword" class="state-block">
        <span class="material-symbols-outlined">search_off</span>
        <p>未找到「{{ keyword }}」相关商品</p>
        <button class="retry-btn" @click="keyword = ''">换个关键词</button>
      </div>

      <template v-else>
        <p v-if="keyword" class="result-count">找到 {{ total }} 件相关商品</p>
        <div class="prod-grid">
          <article
            v-for="p in products"
            :key="p.id"
            class="prod-card"
            @click="goDetail(p.id)"
          >
            <div class="prod-cover">
              <img :src="p.mainImage" :alt="p.name" />
              <span v-if="p.tags && p.tags.length" class="prod-badge" :class="badgeClass(p.tags[0])">{{ p.tags[0] }}</span>
            </div>
            <button
              class="add-cart"
              :disabled="addingIds.has(p.id)"
              @click="addToCart(p, $event)"
            >
              <span class="material-symbols-outlined" :class="{ spin: addingIds.has(p.id) }">
                {{ addingIds.has(p.id) ? 'progress_activity' : isMultiSpec(p) ? 'tune' : 'shopping_cart' }}
              </span>
              <span>{{ addingIds.has(p.id) ? '加入中…' : isMultiSpec(p) ? '选规格' : '加入购物车' }}</span>
            </button>
            <div v-if="p.tags && p.tags.length > 1" class="prod-tags">
              <span v-for="t in p.tags.slice(1)" :key="t" class="prod-tag">{{ t }}</span>
            </div>
            <h4 class="prod-name">{{ p.name }}</h4>
            <div class="prod-price-row">
              <span class="prod-price">¥{{ (p.salePrice || 0).toFixed(2) }}</span>
              <span v-if="p.originalPrice" class="prod-origin">¥{{ p.originalPrice.toFixed(2) }}</span>
            </div>
          </article>
        </div>

        <!-- 分页 -->
        <nav v-if="totalPages > 1" class="pagination">
          <button class="page-btn" :disabled="page === 1" @click="goPage(page - 1)">
            <span class="material-symbols-outlined">chevron_left</span>
          </button>
          <button
            v-for="n in totalPages"
            :key="n"
            class="page-btn"
            :class="{ active: n === page }"
            @click="goPage(n)"
          >{{ n }}</button>
          <button class="page-btn" :disabled="page === totalPages" @click="goPage(page + 1)">
            <span class="material-symbols-outlined">chevron_right</span>
          </button>
        </nav>
      </template>
    </main>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.search-view {
  min-height: 100vh;
  background: $color-background;
}

.search-bar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-lowest;
  border-bottom: 1px solid $color-outline-variant;
}

.back-btn,
.search-btn {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  cursor: pointer;
  font-family: inherit;
}

.back-btn {
  width: 36px;
  height: 36px;
  border-radius: $radius-full;
  background: transparent;
  color: $color-on-surface;

  &:hover {
    background: $color-surface-container;
  }
}

.search-input-wrap {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
}

.search-icon {
  position: absolute;
  left: 14px;
  color: $color-text-tertiary;
  font-size: 20px;
  pointer-events: none;
}

.search-input {
  width: 100%;
  padding: 9px 38px 9px 42px;
  background: $color-surface-container-low;
  border: 1px solid transparent;
  border-radius: $radius-full;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  outline: none;
  font-family: inherit;

  &:focus {
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.15);
  }
}

.clear-btn {
  position: absolute;
  right: 8px;
  width: 26px;
  height: 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: $color-text-tertiary;
  cursor: pointer;
  border-radius: 50%;

  &:hover {
    background: $color-surface-container;
  }
}

.search-btn {
  padding: 0 $spacing-md;
  height: 38px;
  border-radius: $radius-full;
  background: $color-brand;
  color: $color-on-brand;
  font-size: $font-size-label-md;
  font-weight: 600;

  &:active {
    transform: scale(0.97);
  }
}

.hot-section {
  max-width: $width-content-max;
  margin: 0 auto;
  padding: $spacing-xl $width-gutter-mobile;

  @include desktop {
    padding: $spacing-xl $width-margin-desktop;
  }
}

.hot-title {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0 0 $spacing-md;
}

.hot-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.hot-tag {
  padding: $spacing-xs $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

.result-area {
  max-width: $width-content-max;
  margin: 0 auto;
  padding: $spacing-md $width-gutter-mobile $spacing-2xl;

  @include desktop {
    padding: $spacing-md $width-margin-desktop $spacing-2xl;
  }
}

.result-count {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin: 0 0 $spacing-md;
}

.prod-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-sm;

  @include tablet {
    grid-template-columns: repeat(4, 1fr);
  }

  @include desktop {
    grid-template-columns: repeat(5, 1fr);
  }
}

.prod-card {
  display: flex;
  flex-direction: column;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-card;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-lg;
  }
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
  background: $color-brand;

  &.hot {
    background: $color-emergency;
  }

  &.new {
    background: $color-secondary;
  }
}

.add-cart {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: calc(100% - 20px);
  margin: 0 10px;
  padding: 7px 0;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  font-family: inherit;
  cursor: pointer;
  transform: translateY(-12px);
  box-shadow: 0 4px 12px rgba($color-brand, 0.3);
  transition: background $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    background: $color-brand-dark;
  }
}

.prod-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 0 $spacing-sm;
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
  font-weight: 600;
  color: $color-on-surface;
  margin: $spacing-sm $spacing-sm $spacing-xs;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
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
  font-weight: 700;
  color: $color-brand;
}

.prod-origin {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  text-decoration: line-through;
}

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-3xl 0;
  color: $color-on-surface-variant;

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

.retry-btn {
  margin-top: $spacing-sm;
  padding: $spacing-xs $spacing-lg;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;
}

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

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}
@include mobile {
  .search-bar {
    padding: 0.5rem 0.75rem;
    gap: 0.5rem;
  }
  .back-btn {
    width: 2.25rem;
    height: 2.25rem;
  }
  .search-input {
    padding: 0.5625rem 2.375rem 0.5625rem 2.625rem;
  }
  .search-icon {
    left: 0.875rem;
  }
  .clear-btn {
    right: 0.5rem;
    width: 1.625rem;
    height: 1.625rem;
  }
  .search-btn {
    padding: 0 0.75rem;
    height: 2.375rem;
  }
  .hot-section {
    padding: 1rem 0.75rem;
  }
  .result-area {
    padding: 0.75rem 0.75rem 1.5rem;
  }
  .prod-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 0.5rem;
  }
  .add-cart {
    width: calc(100% - 1.25rem);
    margin: 0 0.625rem;
    padding: 0.4375rem 0;
  }
  .prod-tags {
    padding: 0 0.5rem;
  }
  .prod-name {
    margin: 0.5rem 0.5rem 0.25rem;
  }
  .prod-price-row {
    padding: 0 0.5rem 0.5rem;
  }
  .state-block {
    padding: 1.5rem 0;
  }
  .pagination {
    gap: 0.25rem;
    margin-top: 1rem;
  }
  .page-btn {
    min-width: 2.25rem;
    height: 2.25rem;
    padding: 0 0.625rem;
  }
}

</style>
