<script setup lang="ts">
import { computed, ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { communityApi } from '@/api/modules/community'
import type { Article, Comment } from '@/api/modules/community'
import { userApi } from '@/api/modules/user'
import { resolveCover, heroImg } from '@/utils/articleCovers'
import { useUserStore } from '@/stores/user'
import ReportDialog from '@/components/common/ReportDialog.vue'
import type { ReportTargetType } from '@/api/modules/report'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const articleId = computed(() => route.params.id as string)
const loading = ref(false)
const loadError = ref<string | null>(null)

// 分类名从后端 GET /article/categories 动态获取
const categoryMap = ref<Record<number, string>>({})

interface ArticleVM {
  id: number | string
  categoryLabel: string
  title: string
  author: string
  authorId?: number
  authorAvatar: string
  publishDate: string
  cover: string
  contentHtml: string
  viewCount: number
  commentCount: number
}

const article = ref<ArticleVM>({
  id: '',
  categoryLabel: '知识科普',
  title: '加载中...',
  author: 'PetMate 官方团队',
  authorAvatar: heroImg,
  publishDate: '',
  cover: heroImg,
  contentHtml: '',
  viewCount: 0,
  commentCount: 0,
})

const liked = ref(false)
const likeCount = ref(0)
const collected = ref(false)
const commentCount = ref(0)
const comments = ref<Comment[]>([])
const showComments = ref(false)
const commentText = ref('')
const postingComment = ref(false)

// 作者关注态
const authorFollowing = ref(false)
const authorFollowerCount = ref(0)
const followLoading = ref(false)

// 导读目录
interface TocItem { id: string; text: string; level: number }
const toc = ref<TocItem[]>([])
const activeToc = ref('')

// 阅读进度
const progress = ref(0)

async function loadCategories() {
  try {
    const list = await communityApi.articleCategories()
    const arr = (list as any)?.list ?? (list as any)?.records ?? list ?? []
    const m: Record<number, string> = {}
    ;(arr as any[]).forEach((c: any) => (m[c.id] = c.name))
    categoryMap.value = m
  } catch {
    categoryMap.value = {}
  }
}

async function loadArticle(id: string | number) {
  loading.value = true
  loadError.value = null
  try {
    const data: Article = await communityApi.articleDetail(id)
    const categoryId = (data as any).category_id ?? (data as any).categoryId
    const rawAuthorId = (data as any).author_id ?? (data as any).authorId
    article.value = {
      id: data.id,
      categoryLabel: categoryMap.value[categoryId as number] || '知识科普',
      title: data.title,
      author: 'PetMate 官方团队',
      authorId: rawAuthorId ? Number(rawAuthorId) : undefined,
      authorAvatar: heroImg,
      publishDate: data.createdAt || '',
      cover: resolveCover(data.cover),
      contentHtml: renderMarkdown(data.content),
      viewCount: Number((data as any).view_count ?? (data as any).viewCount ?? 0),
      commentCount: Number((data as any).commentCount ?? (data as any).comment_count ?? 0),
    }
    liked.value = !!(data as any).liked
    likeCount.value = Number((data as any).likeCount) || 0
    collected.value = !!(data as any).collected
    commentCount.value = article.value.commentCount
    loadRelated(categoryId as number, data.id)
    loadAuthorInfo()
    buildToc()
    userApi.addFootprint({ type: 'knowledge', targetId: Number(data.id) }).catch(() => {})
  } catch (e: any) {
    loadError.value = e?.message || '文章加载失败'
    article.value = {
      ...article.value,
      id,
      title: '文章不存在或加载失败',
      contentHtml: '',
    }
  } finally {
    loading.value = false
  }
}

interface RelatedItem {
  id: number | string
  cover: string
  title: string
  categoryLabel: string
  views: string
}
const relatedArticles = ref<RelatedItem[]>([])

async function loadRelated(categoryId: number, excludeId: number | string) {
  if (!categoryId) {
    relatedArticles.value = []
    return
  }
  try {
    const res = await communityApi.articleList(categoryId, 1, 8)
    const list = (res as any)?.list ?? []
    relatedArticles.value = (list as any[])
      .filter((a: any) => String(a.id) !== String(excludeId))
      .slice(0, 4)
      .map((a: any) => {
        const cid = (a as any).category_id ?? (a as any).categoryId
        return {
          id: a.id,
          cover: resolveCover(a.cover),
          title: a.title,
          categoryLabel: categoryMap.value[cid as number] || '知识科普',
          views: formatCount(a.view_count ?? a.viewCount),
        }
      })
  } catch {
    relatedArticles.value = []
  }
}

async function loadAuthorInfo() {
  const aid = article.value.authorId
  if (!aid) {
    authorFollowing.value = false
    authorFollowerCount.value = 0
    return
  }
  try {
    const info = await userApi.getUserProfile(aid)
    authorFollowerCount.value = Number((info as any).followerCount ?? 0)
    authorFollowing.value = !!(info as any).following
  } catch {
    authorFollowing.value = false
    authorFollowerCount.value = 0
  }
}

async function toggleFollowAuthor() {
  const aid = article.value.authorId
  if (!aid) {
    ElMessage.info('官方团队账号暂不支持关注')
    return
  }
  if (!userStore.token) {
    ElMessage.warning('请先登录')
    return
  }
  if (followLoading.value) return
  followLoading.value = true
  try {
    const res = await userApi.follow(aid)
    authorFollowing.value = !!(res as any).following
    ElMessage.success(authorFollowing.value ? '已关注作者' : '已取消关注')
    loadAuthorInfo()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}

function formatCount(n?: number): string {
  const v = Number(n) || 0
  if (v >= 10000) return `${(v / 10000).toFixed(1)}w`
  if (v >= 1000) return `${(v / 1000).toFixed(1)}k`
  return String(v)
}

// ===== Markdown -> 安全 HTML（带 id 锚点，供目录使用） =====
function escapeHtml(s: string): string {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function inlineMd(s: string): string {
  let t = escapeHtml(s)
  t = t.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  t = t.replace(/`([^`]+)`/g, '<code>$1</code>')
  return t
}

function boldLabel(text: string): string {
  // 自动把「标签：内容」或「标签: 内容」中的标签加粗
  const m = text.match(/^(.{1,20}?)([:：])(\s*)(.*)$/)
  if (!m) return inlineMd(text)
  const prefix = escapeHtml(m[1] + m[2])
  const suffix = m[4]
  return `<strong>${prefix}</strong>${suffix ? inlineMd(suffix) : ''}`
}

function renderMarkdown(md?: string): string {
  if (!md) return ''
  const lines = md.split('\n')
  const html: string[] = []
  let inList = false
  let para: string[] = []
  let headingSeq = 0

  const flushPara = () => {
    if (para.length) {
      const text = para.join(' ').trim()
      if (text) html.push(`<p>${inlineMd(text)}</p>`)
      para = []
    }
  }
  const closeList = () => {
    if (inList) {
      html.push('</ul>')
      inList = false
    }
  }

  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '')
    if (!line.trim()) {
      flushPara()
      closeList()
      continue
    }
    const h = line.match(/^(#{1,3})\s+(.*)$/)
    if (h) {
      flushPara()
      closeList()
      const level = h[1].length
      const text = h[2].trim()
      const id = `heading-${++headingSeq}`
      html.push(`<h${level} id="${id}" class="md-h${level}">${inlineMd(text)}</h${level}>`)
      continue
    }
    const bq = line.match(/^>\s?(.*)$/)
    if (bq) {
      flushPara()
      closeList()
      const text = bq[1].trim()
      const isTip =
        /^[小贴士小提示]/.test(text) ||
        text.startsWith('💡') ||
        /建议[:：]/.test(text)
      if (isTip) {
        html.push(`<div class="article-tip"><span class="tip-icon material-symbols-outlined">lightbulb</span><div class="tip-body"><span class="tip-label">小贴士</span>${boldLabel(text)}</div></div>`)
      } else {
        html.push(`<blockquote>${inlineMd(text)}</blockquote>`)
      }
      continue
    }
    const li = line.match(/^[-*]\s+(.*)$/)
    if (li) {
      flushPara()
      if (!inList) {
        html.push('<ul class="md-list">')
        inList = true
      }
      html.push(`<li class="md-list-item"><span class="md-check material-symbols-outlined">check</span><span class="md-check-text">${boldLabel(li[1])}</span></li>`)
      continue
    }
    para.push(line)
  }
  flushPara()
  closeList()
  return html.join('\n')
}

function buildToc() {
  nextTick(() => {
    const container = document.querySelector('.article-body')
    if (!container) return
    const items: TocItem[] = []
    container.querySelectorAll('h2[id], h3[id]').forEach((el) => {
      const id = el.getAttribute('id') || ''
      const level = el.tagName === 'H2' ? 2 : 3
      items.push({ id, text: el.textContent || '', level })
    })
    toc.value = items
    observeHeadings()
  })
}

function observeHeadings() {
  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          activeToc.value = entry.target.getAttribute('id') || ''
        }
      })
    },
    { rootMargin: '-80px 0px -60% 0px', threshold: 0 }
  )
  document.querySelectorAll('.article-body h2[id], .article-body h3[id]').forEach((el) => observer.observe(el))
}

function scrollToHeading(id: string) {
  const el = document.getElementById(id)
  if (el) {
    const y = el.getBoundingClientRect().top + window.scrollY - 96
    window.scrollTo({ top: y, behavior: 'smooth' })
    activeToc.value = id
  }
}

function onScroll() {
  const el = document.querySelector('.article-page')
  if (!el) return
  const h = (el as HTMLElement).scrollHeight - window.innerHeight
  progress.value = h > 0 ? Math.min(100, Math.max(0, (window.scrollY / h) * 100)) : 0
}

// 互动操作
const liking = ref(false)
async function toggleLike() {
  if (liking.value || !article.value.id) return
  liking.value = true
  try {
    const res = await communityApi.likeArticle(article.value.id as number)
    liked.value = !!res.liked
    likeCount.value = Number(res.likeCount) || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    liking.value = false
  }
}

async function toggleCollect() {
  if (!article.value.id) return
  try {
    const res = await userApi.toggleFavorite('article', article.value.id as number)
    collected.value = !!res?.collected
    ElMessage.success(collected.value ? '已收藏' : '已取消收藏')
  } catch (e: any) {
    ElMessage.error(e?.message || '收藏失败')
  }
}

async function shareLink() {
  const url = window.location.href
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url)
    } else {
      throw new Error('clipboard unavailable')
    }
  } catch {
    try {
      const ta = document.createElement('textarea')
      ta.value = url
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
    } catch {
      ElMessage.info('请手动复制链接：' + url)
      return
    }
  }
  ElMessage.success('链接已复制到剪贴板')
}

async function loadComments() {
  if (!article.value.id) return
  try {
    const list = await communityApi.articleComments(article.value.id as number)
    comments.value = list || []
  } catch {
    comments.value = []
  }
}

async function postComment() {
  const content = commentText.value.trim()
  if (!content) {
    ElMessage.warning('请输入评论内容')
    return
  }
  if (!article.value.id) return
  if (!userStore.token) {
    ElMessage.warning('请先登录')
    return
  }
  postingComment.value = true
  try {
    const res = await communityApi.commentArticle(article.value.id as number, content)
    commentCount.value = Number((res as any).commentCount) ?? commentCount.value + 1
    commentText.value = ''
    ElMessage.success('评论发布成功')
    await loadComments()
  } catch (e: any) {
    ElMessage.error(e?.message || '评论失败')
  } finally {
    postingComment.value = false
  }
}

function toggleComments() {
  showComments.value = !showComments.value
  if (showComments.value) {
    nextTick(() => {
      const el = document.querySelector('.article-comments')
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
    })
    loadComments()
  }
}

// ===== 举报 =====
const reportVisible = ref(false)
const reportType = ref<ReportTargetType>('article')
const reportId = ref<number>(0)
const reportTitle = ref('举报文章')

function openReport() {
  if (!userStore.token) {
    ElMessage.warning('请先登录后再举报')
    return
  }
  reportType.value = 'article'
  reportId.value = Number(article.value.id) || 0
  reportTitle.value = '举报文章'
  reportVisible.value = true
}

onMounted(async () => {
  await loadCategories()
  await loadArticle(articleId.value)
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})
watch(articleId, (v) => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
  loadArticle(v)
})
onUnmounted(() => window.removeEventListener('scroll', onScroll))

function back() {
  router.back()
}

function goRelated(id: number | string) {
  router.push(`/knowledge/${id}`)
}

function goCategory() {
  router.push('/knowledge')
}

function goExpert() {
  router.push({ name: 'Consult' })
}
</script>

<template>
  <div class="article-page">
    <!-- 阅读进度条 -->
    <div class="progress-container">
      <div class="progress-bar" :style="{ width: progress + '%' }"></div>
    </div>

    <main class="page-main">
      <div class="layout">
        <!-- 左侧浮动操作栏 -->
        <aside class="left-rail">
          <div class="rail-panel glass-panel">
            <button class="rail-btn rail-like" :class="{ active: liked }" :disabled="liking" @click="toggleLike">
              <span class="material-symbols-outlined">{{ liked ? 'favorite' : 'favorite_border' }}</span>
              <span class="rail-num">{{ formatCount(likeCount) }}</span>
            </button>
            <button class="rail-btn rail-collect" :class="{ active: collected }" @click="toggleCollect">
              <span class="material-symbols-outlined">{{ collected ? 'bookmark' : 'bookmark_border' }}</span>
              <span class="rail-num">收藏</span>
            </button>
            <div class="rail-divider"></div>
            <button class="rail-btn rail-share" @click="shareLink">
              <span class="material-symbols-outlined">share</span>
              <span class="rail-num">分享</span>
            </button>
          </div>
        </aside>

        <!-- 主内容 -->
        <article class="article-card">
          <!-- Hero 大图（卡片内顶部，圆角裁切） -->
          <div class="hero-cover">
            <img :src="article.cover" :alt="article.title" />
          </div>

          <div class="article-inner">
            <!-- 面包屑 -->
            <nav class="breadcrumb">
              <a href="javascript:;" @click="router.push('/')">首页</a>
              <span class="sep">/</span>
              <a href="javascript:;" @click="goCategory">知识科普</a>
              <span class="sep">/</span>
              <span class="current">{{ article.categoryLabel }}</span>
            </nav>

            <!-- 标题 + 作者 -->
            <header class="article-head">
              <h1 class="article-title">{{ article.title }}</h1>
              <div class="author-row">
                <div class="author-info">
                  <div class="author-avatar-wrap">
                    <img :src="article.authorAvatar" :alt="article.author" class="author-avatar" />
                    <span class="author-verified material-symbols-outlined">verified</span>
                  </div>
                  <div class="author-text">
                    <span class="author-name">{{ article.author }}</span>
                    <span class="author-meta">PetMate Living · 发布于 {{ article.publishDate || '近期' }}</span>
                  </div>
                </div>
                <button class="follow-btn" :class="{ following: authorFollowing }" :disabled="followLoading" @click="toggleFollowAuthor">
                  {{ authorFollowing ? '已关注' : '关注作者' }}
                </button>
              </div>
            </header>

            <!-- 错误提示 -->
            <div v-if="loadError" class="article-error">{{ loadError }}</div>

            <!-- 正文内容 -->
            <div class="article-body" v-html="article.contentHtml"></div>

            <!-- 评论区 -->
            <section v-if="showComments" class="article-comments">
              <h3 class="comments-title">评论 {{ commentCount }}</h3>
              <div class="comment-input-box">
                <input
                  v-model="commentText"
                  class="comment-input"
                  placeholder="写下你的评论..."
                  maxlength="500"
                  @keyup.enter="postComment"
                />
                <button class="comment-submit" :disabled="postingComment || !commentText.trim()" @click="postComment">
                  发送
                </button>
              </div>
              <div class="comment-list">
                <div v-for="c in comments" :key="c.id" class="comment-item">
                  <img :src="c.userAvatar || heroImg" class="comment-avatar" />
                  <div class="comment-main">
                    <div class="comment-head">
                      <span class="comment-user">{{ c.userNickname }}</span>
                      <span class="comment-time">{{ c.createdAt }}</span>
                    </div>
                    <p class="comment-content">{{ c.content }}</p>
                  </div>
                </div>
                <div v-if="!comments.length" class="comment-empty">暂无评论，来说两句吧~</div>
              </div>
            </section>

            <!-- 底部互动栏 -->
            <div class="article-footer-bar">
              <div class="footer-stats">
                <span>阅读 {{ formatCount(article.viewCount) }}</span>
                <span class="dot">·</span>
                <span class="footer-comment" @click="toggleComments">评论 {{ formatCount(commentCount) }}</span>
              </div>
              <div class="footer-actions">
                <button class="footer-btn" title="举报" @click="openReport">
                  <span class="material-symbols-outlined">warning</span>
                  <span>举报</span>
                </button>
              </div>
            </div>
          </div>
        </article>

        <!-- 右侧栏 -->
        <aside class="right-rail">
          <!-- 导读目录 -->
          <section class="rail-card toc-card">
            <h2 class="rail-card-title">
              <span class="material-symbols-outlined rail-card-icon">menu_book</span>
              导读目录
            </h2>
            <nav class="toc-list">
              <a
                v-for="(item, idx) in toc"
                :key="item.id"
                class="toc-link"
                :class="{ active: activeToc === item.id }"
                @click.prevent="scrollToHeading(item.id)"
              >
                {{ idx + 1 }}. {{ item.text }}
              </a>
            </nav>
          </section>

          <!-- 相关推荐 -->
          <section class="rail-card">
            <h2 class="rail-card-title">
              <span class="material-symbols-outlined rail-card-icon">auto_stories</span>
              相关推荐
            </h2>
            <div class="related-list">
              <a
                v-for="r in relatedArticles"
                :key="r.id"
                class="related-item"
                @click="goRelated(r.id)"
              >
                <div class="related-cover">
                  <img :src="r.cover" :alt="r.title" />
                </div>
                <div class="related-info">
                  <p class="related-title">{{ r.title }}</p>
                  <div class="related-meta">
                    <span class="related-tag">{{ r.categoryLabel }}</span>
                    <span class="related-views">{{ r.views }} 阅读</span>
                  </div>
                </div>
              </a>
            </div>
            <button class="rail-more" @click="goCategory">查看更多内容</button>
          </section>

          <!-- 有问必答专家卡 -->
          <section class="expert-card" @click="goExpert">
            <div class="expert-icon-wrap">
              <span class="material-symbols-outlined expert-icon">smart_toy</span>
            </div>
            <h4 class="expert-title">有问必答专家</h4>
            <p class="expert-desc">PetMate AI 助手 24/7 在线为您解答任何宠物健康与行为疑虑。</p>
            <button class="expert-btn">立即发起咨询</button>
          </section>
        </aside>
      </div>
    </main>

    <!-- 举报对话框 -->
    <ReportDialog
      v-model="reportVisible"
      :target-type="reportType"
      :target-id="reportId"
      :title="reportTitle"
    />
  </div>
</template>

<style lang="scss" scoped>
$brand: #ff7a32;
$brand-blue: #1d63af;
$gray-50: #f8fafc;
$gray-100: #f1f5f9;
$gray-200: #e2e8f0;
$gray-300: #cbd5e1;
$gray-400: #94a3b8;
$gray-500: #64748b;
$gray-600: #475569;
$gray-700: #334155;
$gray-900: #111827;

.article-page {
  min-height: 100vh;
  background: #fdfdfd;
  padding-bottom: 64px;
  position: relative;
  letter-spacing: 0.01em;
}

// ===== 阅读进度条 =====
.progress-container {
  position: fixed;
  top: 64px;
  left: 0;
  width: 100%;
  height: 3px;
  background: transparent;
  z-index: 60;
}
.progress-bar {
  height: 3px;
  background: $brand;
  width: 0%;
  transition: width 0.1s linear;
}

.page-main {
  max-width: 1480px;
  margin: 0 auto;
  padding: 48px 24px;

  @media (min-width: 1024px) {
    padding: 48px 32px;
  }
}

// ===== 三栏布局 =====
.layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: 32px;
  position: relative;

  @media (min-width: 1024px) and (max-width: 1279px) {
    grid-template-columns: 56px 1fr;
    align-items: flex-start;
    gap: 48px;
  }

  @media (min-width: 1280px) {
    grid-template-columns: 56px 1fr 320px;
    align-items: flex-start;
    gap: 48px;
  }
}

// ===== 左侧浮动栏 =====
.left-rail {
  display: none;

  @media (min-width: 1024px) {
    display: block;
    position: sticky;
    top: 100px;
  }
}

.rail-panel {
  border-radius: 20px;
  padding: 10px;
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.glass-panel {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.rail-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 44px;
  height: 44px;
  padding: 0;
  background: $gray-50;
  border: none;
  border-radius: 12px;
  color: $gray-400;
  font-size: 11px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.3s ease;

  .material-symbols-outlined {
    font-size: 22px;
    line-height: 1;
  }

  &:hover {
    transform: translateY(-2px);
  }

  &.rail-like:hover {
    background: #fef2f2;
    color: #ef4444;
  }
  &.rail-like.active {
    background: #fef2f2;
    color: #ef4444;
    font-weight: 700;
  }
  &.rail-collect:hover {
    background: #fefce8;
    color: #eab308;
  }
  &.rail-collect.active {
    background: #fefce8;
    color: #eab308;
    font-weight: 700;
  }
  &.rail-share:hover {
    background: #eff6ff;
    color: #3b82f6;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.rail-num {
  font-size: 11px;
  font-weight: 700;
  line-height: 1;
}

.rail-divider {
  width: 32px;
  height: 1px;
  background: $gray-200;
  margin: 4px 0;
}

// ===== 文章主卡 =====
.article-card {
  position: relative;
  background: #fff;
  border-radius: 20px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  border: 1px solid rgba(241, 245, 249, 0.6);
  overflow: hidden;
  max-width: 960px;
  margin: 0 auto;
  min-width: 0;
}

.hero-cover {
  width: 100%;
  aspect-ratio: 21 / 9;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.7s ease;

    &:hover {
      transform: scale(1.05);
    }
  }
}

.article-inner {
  padding: 32px;

  @media (min-width: 768px) {
    padding: 48px 64px;
  }
}

// ===== 面包屑 =====
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: $gray-400;
  margin-bottom: 32px;

  .sep {
    color: $gray-300;
  }

  .current {
    color: $brand;
  }

  a {
    color: $gray-400;
    text-decoration: none;
    cursor: pointer;

    &:hover {
      color: $brand;
    }
  }
}

// ===== 标题 + 作者 =====
.article-head {
  margin-bottom: 48px;
}

.article-title {
  font-size: 36px;
  font-weight: 700;
  color: $gray-900;
  line-height: 1.15;
  letter-spacing: -0.02em;
  margin: 0 0 32px;

  @media (min-width: 768px) {
    font-size: 48px;
  }
}

.author-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 24px 0;
  border-top: 1px solid $gray-100;
  border-bottom: 1px solid $gray-100;
  flex-wrap: wrap;
}

.author-info {
  display: flex;
  align-items: center;
  gap: 16px;
}

.author-avatar-wrap {
  position: relative;
  width: 56px;
  height: 56px;
  flex-shrink: 0;
}

.author-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  object-fit: cover;
  border: 2px solid #fff;
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.12);
}

.author-verified {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 20px;
  height: 20px;
  background: #3b82f6;
  border-radius: 50%;
  border: 2px solid #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  color: #fff;
  font-variation-settings: 'FILL' 1;
}

.author-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.author-name {
  font-size: 18px;
  font-weight: 700;
  color: $gray-900;
}

.author-meta {
  font-size: 14px;
  color: $gray-400;
}

.follow-btn {
  padding: 12px 32px;
  background: $brand;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;
  box-shadow: 0 8px 20px rgba(255, 122, 50, 0.25);

  &:active {
    transform: scale(0.95);
  }

  &:hover {
    background: #ea6a26;
  }

  &.following {
    background: $gray-100;
    color: $gray-500;
    box-shadow: none;
  }

  &:disabled {
    opacity: 0.7;
  }
}

// ===== 正文 =====
.article-body {
  :deep(h2) {
    font-size: 30px;
    font-weight: 700;
    margin: 48px 0 24px;
    color: $gray-900;
    letter-spacing: -0.02em;
    scroll-margin-top: 96px;
  }

  :deep(h3) {
    font-size: 21px;
    font-weight: 600;
    margin: 32px 0 16px;
    color: $gray-700;
    scroll-margin-top: 96px;
  }

  :deep(p) {
    margin: 0 0 24px;
    line-height: 1.9;
    color: $gray-600;
    font-size: 17px;
  }

  :deep(ul.md-list) {
    list-style: none;
    padding: 0;
    margin: 0 0 32px;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  :deep(li.md-list-item) {
    display: flex;
    align-items: flex-start;
    gap: 12px;
    font-size: 16px;
    line-height: 1.7;
    color: $gray-600;
  }

  :deep(.md-check) {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    margin-top: 2px;
    border-radius: 50%;
    background: rgba(255, 122, 50, 0.1);
    color: $brand;
    font-size: 14px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-variation-settings: 'FILL' 1;
  }

  :deep(.md-check-text strong) {
    color: $gray-900;
  }

  :deep(blockquote) {
    margin: 0 0 24px;
    padding: 16px 24px;
    background: rgba(255, 122, 50, 0.05);
    border-left: 4px solid $brand;
    border-radius: 8px;
    color: $gray-600;

    p { margin: 0; }
    strong { color: $gray-900; }
  }

  :deep(.article-tip) {
    display: flex;
    gap: 12px;
    margin: 0 0 24px;
    padding: 24px;
    background: rgba(255, 122, 50, 0.06);
    border: 1px solid rgba(255, 122, 50, 0.18);
    border-radius: 16px;
    position: relative;
    overflow: hidden;

    .tip-icon {
      flex-shrink: 0;
      color: $brand;
      font-size: 18px;
      font-variation-settings: 'FILL' 1;
    }

    .tip-body {
      flex: 1;
      font-size: 16px;
      color: $gray-600;
      line-height: 1.7;
      font-style: italic;

      .tip-label {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        margin-right: 6px;
        color: $brand;
        font-size: 14px;
        font-weight: 700;
        text-transform: uppercase;
        letter-spacing: 0.06em;
        font-style: normal;
      }

      strong {
        color: $gray-900;
        font-style: normal;
      }
    }
  }

  :deep(strong) {
    color: $gray-900;
    font-weight: 700;
  }

  :deep(code) {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    background: $gray-100;
    padding: 2px 6px;
    border-radius: 4px;
    font-size: 0.9em;
    color: $gray-900;
  }
}

.article-error {
  padding: 16px 24px;
  background: rgba(255, 122, 50, 0.08);
  border-left: 4px solid $brand;
  border-radius: 8px;
  color: $gray-600;
  margin-bottom: 24px;
}

// ===== 评论区 =====
.article-comments {
  margin-top: 64px;
  padding-top: 32px;
  border-top: 1px solid $gray-100;
}

.comments-title {
  font-size: 24px;
  font-weight: 700;
  color: $gray-900;
  margin: 0 0 24px;
}

.comment-input-box {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
}

.comment-input {
  flex: 1;
  padding: 10px 20px;
  border: 1px solid $gray-200;
  border-radius: 999px;
  background: $gray-50;
  color: $gray-900;
  font-size: 16px;
  outline: none;

  &:focus {
    border-color: $brand;
  }
}

.comment-submit {
  padding: 0 28px;
  background: $brand;
  color: #fff;
  font-size: 14px;
  font-weight: 700;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.3s ease;

  &:hover:not(:disabled) {
    background: #ea6a26;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.comment-item {
  display: flex;
  gap: 12px;
}

.comment-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.comment-main {
  flex: 1;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.comment-user {
  font-size: 14px;
  font-weight: 700;
  color: $gray-900;
}

.comment-time {
  font-size: 12px;
  color: $gray-400;
}

.comment-content {
  font-size: 16px;
  color: $gray-600;
  line-height: 1.6;
  margin: 0;
}

.comment-empty {
  text-align: center;
  padding: 32px 0;
  color: $gray-400;
  font-size: 16px;
}

// ===== 底部互动栏 =====
.article-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  margin-top: 64px;
  padding-top: 32px;
  border-top: 1px solid $gray-100;
  flex-wrap: wrap;
}

.footer-stats {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: $gray-400;

  .dot {
    color: $gray-300;
  }

  .footer-comment {
    cursor: pointer;

    &:hover {
      color: $brand;
    }
  }
}

.footer-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.footer-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: transparent;
  border: none;
  border-radius: 999px;
  color: $gray-400;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.3s ease;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    color: $brand;
  }

  &.active {
    color: $brand;
  }

  &:disabled {
    opacity: 0.6;
  }
}

// ===== 右侧栏 =====
.right-rail {
  display: none;

  @media (min-width: 1280px) {
    display: block;
    position: sticky;
    top: 100px;
  }

  @media (min-width: 1024px) and (max-width: 1279px) {
    display: none;
  }
}

.rail-card {
  background: #fff;
  border-radius: 20px;
  border: 1px solid $gray-100;
  padding: 24px;
  margin-bottom: 32px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.rail-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 700;
  color: $gray-900;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin: 0 0 16px;
}

.rail-card-icon {
  font-size: 18px;
  color: $brand;
}

// ===== 导读目录 =====
.toc-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.toc-link {
  font-size: 14px;
  font-weight: 500;
  color: $gray-500;
  border-left: 2px solid transparent;
  padding: 2px 0 2px 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  text-decoration: none;

  &:hover {
    color: $brand;
    border-left-color: $brand;
    padding-left: 16px;
  }

  &.active {
    color: $brand;
    border-left-color: $brand;
    font-weight: 700;
  }
}

// ===== 相关推荐 =====
.related-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;
}

.related-item {
  display: block;
  background: #fff;
  border: 1px solid $gray-100;
  border-radius: 16px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  text-decoration: none;

  &:hover {
    box-shadow: 0 12px 24px rgba(15, 23, 42, 0.12);
    transform: translateY(-4px);

    .related-title {
      color: $brand;
    }

    .related-cover img {
      transform: scale(1.1);
    }
  }
}

.related-cover {
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: $gray-100;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.5s ease;
  }
}

.related-info {
  padding: 16px;
}

.related-title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.4;
  color: $gray-900;
  margin: 0 0 8px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.3s ease;
}

.related-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;

  .related-tag {
    font-size: 11px;
    color: $gray-400;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.06em;
  }

  .related-views {
    font-size: 11px;
    color: $gray-400;
    margin: 0;
  }
}

.rail-more {
  width: 100%;
  padding: 12px 0;
  background: $gray-50;
  color: $gray-400;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  border: none;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    background: $gray-100;
    color: $gray-600;
  }
}

// ===== 专家卡 =====
.expert-card {
  position: relative;
  overflow: hidden;
  background: linear-gradient(135deg, $brand 0%, $brand-blue 100%);
  border-radius: 20px;
  padding: 28px;
  text-align: center;
  color: #fff;
  cursor: pointer;
  transition: transform 0.3s ease;
  box-shadow: 0 12px 24px rgba(255, 122, 50, 0.2);

  &:hover {
    transform: translateY(-2px);
  }
}

.expert-icon-wrap {
  width: 56px;
  height: 56px;
  margin: 0 auto 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.expert-icon {
  font-size: 28px !important;
  color: #fff;
  font-variation-settings: 'FILL' 1;
}

.expert-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 12px;
}

.expert-desc {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.8);
  margin: 0 0 32px;
  line-height: 1.6;
}

.expert-btn {
  width: 100%;
  padding: 14px 0;
  background: #fff;
  color: $brand;
  font-size: 13px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.3s ease;

  &:hover {
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.15);
  }
}

// ============ 手机端响应式（≤768px）：用 rem 还原被 px-to-viewport 缩成几像素的内边距/间距 ============
// 注意：px 会被插件再转 vw 导致手机上塌缩，块内一律用 rem
@media (max-width: 768px) {
  .article-page {
    padding-bottom: 2rem;
  }

  .page-main {
    padding: 1.5rem 1rem;
  }

  .article-inner {
    padding: 1rem;
  }

  .breadcrumb {
    margin-bottom: 1rem;
  }

  .article-head {
    margin-bottom: 1.5rem;
  }

  .article-title {
    font-size: 1.625rem; // ≈26px
    margin: 0 0 1rem;
  }

  .author-row {
    padding: 1rem 0;
    gap: 0.75rem;
  }

  .article-body :deep(p) {
    font-size: 1rem;
    line-height: 1.75;
  }

  .article-body :deep(h2) {
    font-size: 1.375rem; // ≈22px
    margin: 1.75rem 0 0.875rem;
  }

  .article-body :deep(h3) {
    font-size: 1.125rem; // ≈18px
    margin: 1.375rem 0 0.75rem;
  }

  .article-comments {
    margin-top: 2rem;
    padding-top: 1.25rem;
  }

  .article-footer-bar {
    margin-top: 2rem;
    padding-top: 1.25rem;
    gap: 0.75rem;
  }

  .comment-input-box {
    gap: 0.5rem;
  }
}
</style>
