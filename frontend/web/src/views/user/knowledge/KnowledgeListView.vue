<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { communityApi } from '@/api/modules/community'
import type { ArticleCategory } from '@/api/modules/community'
import { metaApi, type KnowledgeTip } from '@/api/modules/meta'
import { resolveCover, heroImg } from '@/utils/articleCovers'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

// 分类导航：从后端 GET /article/categories 拉取
const categories = ref<ArticleCategory[]>([])
const categoryMap = computed(() => {
  const m: Record<number, ArticleCategory> = {}
  categories.value.forEach((c) => (m[c.id] = c))
  return m
})

// 全部文章 tab（不传 categoryId）
const ALL_CATEGORY_ID = -1
const activeCategory = ref<number>(ALL_CATEGORY_ID)
const sortMode = ref<'hot' | 'new'>('new')
const loading = ref(false)
const catLoading = ref(false)

// 分页状态（后端 PageResp 返回 total）
const currentPage = ref(1)
const pageSize = 9
const total = ref(0)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))

interface ArticleCard {
  id: number
  categoryId: number
  categoryLabel: string
  title: string
  summary: string
  cover: string
  viewCount: number
}

const articles = ref<ArticleCard[]>([])

// ============ 百科小贴士（来自 metaApi.knowledgeTip()） ============
const knowledgeTips = ref<KnowledgeTip[]>([])
const currentTip = ref<KnowledgeTip | null>(null)
async function loadKnowledgeTips() {
  try {
    const data = await metaApi.knowledgeTip()
    knowledgeTips.value = data.tips || []
    currentTip.value = knowledgeTips.value[0] || null
  } catch (e) {
    console.warn('[KnowledgeList] tips 加载失败', e)
  }
}
function pickRandomTip() {
  if (knowledgeTips.value.length === 0) return
  const pool = knowledgeTips.value.filter((t) => t.title !== currentTip.value?.title)
  const next = pool[Math.floor(Math.random() * pool.length)] ?? knowledgeTips.value[0]
  currentTip.value = next
}

function extractSummary(content?: string): string {
  if (!content) return ''
  // 去除 HTML/Markdown 简单标记,截前 90 字
  const plain = content.replace(/<[^>]+>/g, '').replace(/[#>*_`]/g, '').replace(/\s+/g, ' ').trim()
  return plain.length > 90 ? plain.slice(0, 90) + '...' : plain
}

async function loadCategories() {
  catLoading.value = true
  try {
    const list = await communityApi.articleCategories()
    // 兼容后端可能返回 null / records / list
    const arr = (list as any)?.list ?? (list as any)?.records ?? list ?? []
    categories.value = Array.isArray(arr) ? arr : []
  } catch (e: any) {
    ElMessage.error(e?.message || '分类加载失败')
    categories.value = []
  } finally {
    catLoading.value = false
  }
}

async function loadArticles() {
  loading.value = true
  try {
    const cid = activeCategory.value === ALL_CATEGORY_ID ? undefined : activeCategory.value
    const res = await communityApi.articleList(cid, currentPage.value, pageSize)
    const list = (res as any)?.list ?? (res as any)?.records ?? []
    total.value = (res as any)?.total ?? 0
    articles.value = list.map((a: any) => {
      // 后端返回 snake/camel 两种命名，兜底 0 避免用 undefined 作索引
      const cid: number = Number(a.category_id ?? a.categoryId ?? 0)
      return {
        id: a.id,
        categoryId: cid,
        categoryLabel:
          categoryMap.value[cid]?.name ||
          (typeof a.category_name === 'string' && a.category_name) ||
          '其他',
        title: a.title,
        summary: extractSummary(a.content),
        cover: resolveCover(a.cover),
        viewCount: a.view_count ?? a.viewCount ?? 0,
      }
    })
  } catch (e: any) {
    ElMessage.error(e?.message || '文章加载失败')
    articles.value = []
  } finally {
    loading.value = false
  }
}

// 分页控件：上一页 / 页码（带省略号）/ 下一页
const pageRange = computed<(number | '...')[]>(() => {
  const tp = totalPages.value
  const cur = currentPage.value
  if (tp <= 7) {
    return Array.from({ length: tp }, (_, i) => i + 1)
  }
  const out: (number | '...')[] = [1]
  if (cur > 3) out.push('...')
  const start = Math.max(2, cur - 1)
  const end = Math.min(tp - 1, cur + 1)
  for (let i = start; i <= end; i++) out.push(i)
  if (cur < tp - 2) out.push('...')
  out.push(tp)
  return out
})

function goPage(p: number) {
  if (p < 1 || p > totalPages.value || p === currentPage.value) return
  currentPage.value = p
  loadArticles()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 分类 icon 可能是图标字体名，也可能是图片 URL（后端返回 icons8 图片链接）
function isImageIcon(icon?: string): boolean {
  if (!icon) return false
  return /^https?:\/\//.test(icon) || icon.startsWith('/') || /\.(png|jpe?g|gif|webp|svg)$/i.test(icon)
}

// 图片类 icon 加载失败时回退到图标字体字形
const imgErrorIds = ref<Set<number>>(new Set())
function onIconError(id: number) {
  const s = new Set(imgErrorIds.value)
  s.add(id)
  imgErrorIds.value = s
}
// 非图片或图片加载失败时展示的字形（图片型回退为通用 article 图标）
function displayGlyph(c: ArticleCategory): string {
  return isImageIcon(c.icon) ? 'article' : c.icon || 'article'
}

// 每个分类的独立配色（背景渐变 + 图标色），与 Material Symbols 实心版本搭配
const CATEGORY_THEME: Record<number, { bg: string; fg: string }> = {
  1: { bg: 'linear-gradient(135deg, #FFB36B 0%, #FF7A32 100%)', fg: '#FFFFFF' }, // 喂养指南 - 暖橙
  2: { bg: 'linear-gradient(135deg, #6BB6FF 0%, #4A90E2 100%)', fg: '#FFFFFF' }, // 健康护理 - 信任蓝
  3: { bg: 'linear-gradient(135deg, #B98CFF 0%, #8A5BD9 100%)', fg: '#FFFFFF' }, // 行为心理 - 紫
  4: { bg: 'linear-gradient(135deg, #6BD89E 0%, #36B36B 100%)', fg: '#FFFFFF' }, // 训练技巧 - 翠绿
}
function catTheme(id: number) {
  return CATEGORY_THEME[id] || { bg: 'linear-gradient(135deg, #E0E0E0 0%, #BDBDBD 100%)', fg: '#FFFFFF' }
}

onMounted(async () => {
  await loadKnowledgeTips()
  pickRandomTip()
  await loadCategories()
  await loadArticles()
})
watch(activeCategory, () => {
  currentPage.value = 1
  loadArticles()
})
watch(sortMode, () => {
  currentPage.value = 1
})

const featured = computed(() => articles.value[0])

const filteredArticles = computed(() =>
  sortMode.value === 'hot'
    ? [...articles.value].sort((x, y) => (y.viewCount || 0) - (x.viewCount || 0))
    : articles.value,
)

function goDetail(id: number) {
  router.push(`/knowledge/${id}`)
}
</script>

<template>
  <div class="knowledge-view">
    <div class="knowledge-container">
      <!-- 精选 Banner -->
      <section class="featured">
        <div class="featured-card" @click="featured && goDetail(featured.id)">
          <div class="featured-mask"></div>
          <img
            :src="heroImg"
            :alt="featured?.title || '精选'"
            class="featured-img"
          />
          <div class="featured-body">
            <span class="featured-tag">编辑精选</span>
            <h1 class="featured-title">{{ featured?.title || '正在加载精选文章...' }}</h1>
            <p class="featured-desc">
              {{ featured?.summary || '请稍候,精彩内容即将呈现。' }}
            </p>
            <button class="featured-btn">
              开始阅读
              <span class="material-symbols-outlined">arrow_forward</span>
            </button>
          </div>
        </div>
      </section>

      <!-- 主体：侧栏 + 网格 -->
      <div class="content-layout">
        <!-- 侧栏 -->
        <aside class="sidebar">
          <div class="cat-card pet-card">
            <h2 class="cat-title">分类导航</h2>
            <ul class="cat-list">
              <li>
                <button
                  class="cat-item"
                  :class="{ active: activeCategory === ALL_CATEGORY_ID }"
                  @click="activeCategory = ALL_CATEGORY_ID"
                >
                  <span class="cat-item-left">
                    <span class="cat-icon-wrap all-icon-wrap">
                      <span class="material-symbols-outlined cat-icon">apps</span>
                    </span>
                    <span class="cat-label">全部</span>
                  </span>
                  <span class="material-symbols-outlined chevron">chevron_right</span>
                </button>
              </li>
              <li v-for="c in categories" :key="c.id">
                <button
                  class="cat-item"
                  :class="{ active: activeCategory === c.id }"
                  @click="activeCategory = c.id"
                >
                  <span class="cat-item-left">
                    <span
                      v-if="isImageIcon(c.icon) && !imgErrorIds.has(c.id)"
                      class="cat-icon-wrap"
                      :style="{ background: catTheme(c.id).bg }"
                    >
                      <img :src="c.icon" class="cat-icon-img" :alt="c.name" @error="onIconError(c.id)" />
                    </span>
                    <span
                      v-else
                      class="cat-icon-wrap"
                      :style="{ background: catTheme(c.id).bg, color: catTheme(c.id).fg }"
                    >
                      <span class="material-symbols-outlined cat-icon">{{ displayGlyph(c) }}</span>
                    </span>
                    <span class="cat-label">{{ c.name }}</span>
                  </span>
                  <span class="material-symbols-outlined chevron">chevron_right</span>
                </button>
              </li>
            </ul>
            <div v-if="catLoading" class="cat-loading">分类加载中...</div>
            <div v-else-if="!catLoading && categories.length === 0" class="cat-loading">暂无分类</div>
          </div>

          <!-- 百科小贴士（每次刷新随机轮换） -->
          <div v-if="currentTip" class="tip-card pet-card" @click="pickRandomTip" title="点击换一条小贴士">
            <div class="tip-body">
              <h3 class="tip-title">
                <span class="tip-title-main">
                  <span class="material-symbols-outlined tip-icon">lightbulb</span>
                  百科小贴士
                </span>
                <span class="tip-emoji">{{ currentTip.emoji }}</span>
              </h3>
              <p class="tip-text">{{ currentTip.text }}</p>
            </div>
          </div>
        </aside>

        <!-- 文章网格 -->
        <div class="article-area">
          <PageSection title="全部文章" icon="menu_book">
            <template #actions>
              <div class="sort-tabs">
                <button
                  class="sort-tab"
                  :class="{ active: sortMode === 'hot' }"
                  @click="sortMode = 'hot'"
                >
                  最热
                </button>
                <button
                  class="sort-tab"
                  :class="{ active: sortMode === 'new' }"
                  @click="sortMode = 'new'"
                >
                  最新
                </button>
              </div>
            </template>

          <div class="article-grid">
            <div v-if="loading" class="grid-loading">加载中...</div>
            <PetEmptyState
              v-else-if="filteredArticles.length === 0"
              class="grid-empty"
              icon="menu_book"
              title="暂无文章"
            />
            <article
              v-for="a in filteredArticles"
              :key="a.id"
              class="article-card pet-card"
              @click="goDetail(a.id)"
            >
              <div class="card-cover">
                <img :src="a.cover || heroImg" :alt="a.title" />
                <span class="card-tag">{{ a.categoryLabel }}</span>
              </div>
              <div class="card-body">
                <h3 class="card-title">{{ a.title }}</h3>
                <p class="card-summary">{{ a.summary || '暂无摘要' }}</p>
                <div class="card-meta">
                  <span class="views">
                    <span class="material-symbols-outlined">visibility</span>
                    {{ a.viewCount || 0 }}
                  </span>
                  <span class="read-more">阅读全文</span>
                </div>
              </div>
            </article>
          </div>
          </PageSection>

          <!-- 分页 -->
          <div class="pagination" v-if="totalPages > 1">
            <button
              class="page-btn"
              :class="{ disabled: currentPage === 1 }"
              :disabled="currentPage === 1"
              @click="goPage(currentPage - 1)"
            >
              <span class="material-symbols-outlined">chevron_left</span>
            </button>
            <template v-for="(p, idx) in pageRange" :key="idx">
              <span v-if="p === '...'" class="page-ellipsis">…</span>
              <button
                v-else
                class="page-btn"
                :class="{ active: p === currentPage }"
                @click="goPage(p)"
              >
                {{ p }}
              </button>
            </template>
            <button
              class="page-btn"
              :class="{ disabled: currentPage === totalPages }"
              :disabled="currentPage === totalPages"
              @click="goPage(currentPage + 1)"
            >
              <span class="material-symbols-outlined">chevron_right</span>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.knowledge-view {
  min-height: 100vh;
  background: $color-background;
}

.knowledge-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-3xl;
}

// ============ 精选 Banner ============
.featured {
  margin-bottom: $spacing-2xl;
}

.featured-card {
  position: relative;
  width: 100%;
  height: 400px;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-lg;
  cursor: pointer;

  @include desktop {
    height: 500px;
  }

  &:hover .featured-img {
    transform: scale(1.05);
  }
}

.featured-mask {
  position: absolute;
  inset: 0;
  z-index: 10;
  background: linear-gradient(
    90deg,
    rgba(22, 29, 31, 0.8),
    rgba(22, 29, 31, 0.4) 50%,
    transparent
  );
}

.featured-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 700ms ease;
}

.featured-body {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: $spacing-xl;
  max-width: 640px;

  @include desktop {
    padding: $spacing-2xl;
  }
}

.featured-tag {
  align-self: flex-start;
  padding: 4px $spacing-md;
  background: $color-brand;
  color: $color-on-brand-container;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 700;
  margin-bottom: $spacing-md;
}

.featured-title {
  color: #ffffff;
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  line-height: 1.25;
  margin-bottom: $spacing-md;
}

.featured-desc {
  color: rgba(255, 255, 255, 0.9);
  font-size: $font-size-body-lg;
  line-height: 1.5;
  margin-bottom: $spacing-xl;
  @include text-ellipsis(2);
}

.featured-btn {
  align-self: flex-start;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-xl;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-full;
  font-weight: 700;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: all $transition-fast;

  &:hover {
    background: #7b2f00;
  }

  &:active {
    transform: scale(0.95);
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

// ============ 主体布局 ============
.content-layout {
  display: flex;
  flex-direction: column;
  gap: $width-gutter-desktop;

  @include desktop {
    flex-direction: row;
  }
}

// ============ 侧栏 ============
.sidebar {
  width: 100%;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;

  @include desktop {
    width: 288px;
  }
}

.cat-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
}

.cat-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-brand;
  margin-bottom: $spacing-lg;
}

.cat-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.cat-loading {
  margin-top: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  text-align: center;
}

.cat-item {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm;
  border-radius: $radius-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  .cat-item-left {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }

  .cat-icon-wrap {
    width: 32px;
    height: 32px;
    border-radius: 50%;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    box-shadow: 0 2px 6px rgba(0, 0, 0, 0.08);
    transition: transform $transition-fast, box-shadow $transition-fast;
  }

  .all-icon-wrap {
    background: linear-gradient(135deg, #D7DBE0 0%, #B8BEC6 100%);
    color: #FFFFFF;
  }

  .cat-icon {
    font-size: 20px;
    // Material Symbols 实心变体：填充 + 中等字重
    font-variation-settings: 'FILL' 1, 'wght' 500, 'GRAD' 0, 'opsz' 24;
    line-height: 1;
  }

  .cat-icon-img {
    width: 22px;
    height: 22px;
    object-fit: contain;
    border-radius: 4px;
  }

  .cat-label {
    font-size: $font-size-label-md;
    font-weight: 500;
    color: $color-on-surface;
  }

  .chevron {
    font-size: 18px;
    opacity: 0;
    transition: opacity $transition-fast;
  }

  &:hover {
    background: $color-surface-container;

    .cat-icon-wrap {
      transform: scale(1.08) rotate(-4deg);
      box-shadow: 0 4px 10px rgba(0, 0, 0, 0.12);
    }

    .chevron {
      opacity: 1;
    }
  }

  &.active {
    background: rgba(255, 219, 204, 0.5);

    .cat-icon-wrap {
      transform: scale(1.1);
      box-shadow: 0 4px 12px rgba(255, 122, 50, 0.35);
    }

    .cat-label {
      color: $color-on-brand-container;
      font-weight: 700;
    }

    .chevron {
      opacity: 1;
      color: $color-on-brand-container;
    }
  }
}

// ============ 百科小贴士 ============
.tip-card {
  position: relative;
  padding: $spacing-lg;
  background: $color-secondary-light;
  color: #ffffff;
  border-radius: $radius-xl;
  box-shadow: $shadow-lg;
  overflow: hidden;
}

.tip-body {
  position: relative;
  z-index: 10;
}

.tip-emoji {
  font-size: 22px;
  line-height: 1;
}

.tip-icon {
  font-size: 22px;
  margin-right: 8px;
  font-variation-settings: 'FILL' 1;
}

.tip-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  margin-bottom: $spacing-xs;
}

.tip-title-main {
  display: flex;
  align-items: center;
}

.tip-text {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: $font-size-body-md;
  line-height: 1.6;
  opacity: 0.9;
}


// ============ 文章区 ============
.article-area {
  flex: 1;
  min-width: 0;
}

.area-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-xl;
}

.area-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

.sort-tabs {
  display: flex;
  gap: $spacing-xs;
}

.sort-tab {
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
  background: $color-surface-container-high;
  color: $color-on-surface-variant;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand;
  }

  &.active {
    background: $color-brand;
    color: #ffffff;
    border-color: $color-brand;
  }
}

.article-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;

  @include tablet {
    grid-template-columns: repeat(2, 1fr);
  }

  @include desktop {
    grid-template-columns: repeat(3, 1fr);
  }
}

.grid-loading,
.grid-empty {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-2xl;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 48px;
    opacity: 0.5;
  }

  p {
    margin: 0;
    font-size: $font-size-body-md;
  }
}

.article-card {
  display: flex;
  flex-direction: column;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.1);
  cursor: pointer;
  transition: all $transition-base;

  &:hover {
    box-shadow: $shadow-md;

    .card-cover img {
      transform: scale(1.1);
    }

    .card-title {
      color: $color-brand;
    }
  }
}

.card-cover {
  position: relative;
  height: 192px;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 500ms ease;
  }
}

.card-tag {
  position: absolute;
  top: $spacing-md;
  left: $spacing-md;
  padding: 4px $spacing-sm;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(4px);
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-brand-dark;
  box-shadow: $shadow-sm;
}

.card-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: $spacing-md;
}

.card-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;
  line-height: 1.3;
  @include text-ellipsis(2);
  transition: color $transition-fast;
}

.card-summary {
  flex: 1;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: 1.5;
  margin-bottom: $spacing-lg;
  @include text-ellipsis(3);
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: $spacing-md;
  border-top: 1px solid rgba(223, 192, 179, 0.2);
}

.views {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 16px;
  }
}

.read-more {
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-brand;
}

// ============ 分页 ============
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  margin-top: $spacing-2xl;
}

.page-btn {
  min-width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 $spacing-sm;
  border-radius: $radius-full;
  background: $color-surface-container-lowest;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: $color-surface-container-high;
  }

  &.active {
    background: $color-brand;
    color: #ffffff;
  }

  &.disabled,
  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;

    &:hover {
      background: $color-surface-container-lowest;
    }
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.page-ellipsis {
  color: $color-text-tertiary;
  padding: 0 4px;
}

// ============ 手机端响应式（≤768px）：用 rem 还原被 px-to-viewport 缩小的高度与内边距 ============
@include mobile {
  .knowledge-container {
    padding-top: 1rem;
    padding-bottom: 2rem;
  }

  .featured {
    margin-bottom: 1.5rem;
  }

  .featured-card {
    height: 14rem; // ≈224px，避免被缩放成小条
  }

  .featured-body {
    padding: 1rem;
    max-width: 100%;
  }

  .featured-title {
    font-size: 1.5rem; // ≈24px
  }

  .featured-desc {
    font-size: 0.875rem;
    margin-bottom: 1rem;
  }

  .sidebar {
    gap: 0.75rem;
  }

  .cat-card {
    padding: 0.75rem;
  }

  .cat-title {
    margin-bottom: 0.75rem;
  }

  .tip-card {
    padding: 0.75rem;
  }

  .card-cover {
    height: 10rem; // ≈160px
  }

  .card-body {
    padding: 0.75rem;
  }

  .card-summary {
    margin-bottom: 0.75rem;
  }

  .pagination {
    margin-top: 1.5rem;
    flex-wrap: wrap;
  }
}
</style>
