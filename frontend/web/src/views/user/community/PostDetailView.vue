<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { communityApi } from '@/api/modules/community'
import type { Post, Comment } from '@/api/modules/community'
import { userApi, type UserProfile } from '@/api/modules/user'
import { metaApi, type CommunityActivity } from '@/api/modules/meta'
import { useUserStore } from '@/stores/user'
import ReportDialog from '@/components/common/ReportDialog.vue'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'
import type { ReportTargetType } from '@/api/modules/report'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const postId = computed(() => Number(route.params.id))

const post = ref<Post | null>(null)
const comments = ref<Comment[]>([])
const loading = ref(false)
const commentText = ref('')
const submitting = ref(false)
const following = ref(false)

// 作者其他动态（来自数据库）
const authorPosts = ref<Post[]>([])

// 侧边栏：作者资料 + 热门活动 + 热门话题
const authorProfile = ref<UserProfile | null>(null)
const activities = ref<CommunityActivity[]>([])
const topics = ref<{ id: number; name: string }[]>([])
const sidebarLoading = ref(false)

// 头像加载失败兜底（离线环境下 dicebear 外链不可达，改用昵称首字母占位）
const headerAvatarError = ref(false)
const profileAvatarError = ref(false)
const myAvatarError = ref(false)

// 评论分页
const commentPage = ref(1)
const commentSize = 6

const isOwnPost = computed(
  () => !!post.value && post.value.userId === userStore.userId,
)

// 配图布局
const imgLayout = computed(() => {
  const n = post.value?.images?.length || 0
  if (n === 1) return 'one'
  if (n === 2) return 'two'
  return 'multi'
})

// VIP 等级徽章文字（"LV.5 资深铲屎官"）
function vipBadgeText(level?: number) {
  if (!level) return ''
  return `LV.${level} 资深铲屎官`
}

// 性别文字：0 未知 / 1 男 / 2 女
function genderText(g?: number): string {
  return g === 1 ? '男' : g === 2 ? '女' : ''
}

// 相对时间格式化（基于数据库存储的真实创建时间）
function timeAgo(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso.includes('T') ? iso : iso.replace(' ', 'T'))
  if (isNaN(d.getTime())) return iso
  const diff = Date.now() - d.getTime()
  const min = Math.floor(diff / 60000)
  if (min < 1) return '刚刚'
  if (min < 60) return `${min}分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}小时前`
  const day = Math.floor(hr / 24)
  if (day < 30) return `${day}天前`
  const mon = Math.floor(day / 30)
  if (mon < 12) return `${mon}个月前`
  return `${Math.floor(mon / 12)}年前`
}

async function loadPost() {
  loading.value = true
  try {
    post.value = await communityApi.postDetail(postId.value)
    // 登录态下静默记录社区动态浏览足迹（不阻塞详情加载，失败忽略）
    if (userStore.isLoggedIn) {
      userApi.addFootprint({ type: 'community', targetId: postId.value }).catch(() => {})
    }
    if (userStore.isLoggedIn && post.value && !isOwnPost.value) {
      try {
        const r = await userApi.followStatus(post.value.userId)
        following.value = !!r.following
      } catch {
        /* 忽略 */
      }
    }
    commentPage.value = 1
    await loadComments()
  } catch (e: any) {
    ElMessage.error(e?.message || '帖子加载失败')
    post.value = null
  } finally {
    loading.value = false
  }
}

async function loadComments() {
  if (!post.value) return
  // 后端当前不分页（一次性返回全部），本地按 page 切片模拟「加载更多」
  const all = (await communityApi.comments(post.value.id)) || []
  comments.value = all
}

async function loadSidebar() {
  if (!post.value) return
  sidebarLoading.value = true
  try {
    const [profile, activityResp, topicResp, authorPostsResp] = await Promise.allSettled([
      userApi.getUserProfile(post.value.userId),
      metaApi.communityActivities(),
      communityApi.topicList(),
      communityApi.postsByUser(post.value.userId, 1, 6),
    ])
    if (profile.status === 'fulfilled') authorProfile.value = profile.value
    if (activityResp.status === 'fulfilled') {
      activities.value = (activityResp.value as any) || []
    }
    if (topicResp.status === 'fulfilled') {
      topics.value = (topicResp.value as any) || []
    }
    if (authorPostsResp.status === 'fulfilled') {
      authorPosts.value = ((authorPostsResp.value as any)?.list || [])
        .filter((p: Post) => p.id !== post.value!.id)
        .slice(0, 4)
    }
  } catch {
    /* 静默失败 */
  } finally {
    sidebarLoading.value = false
  }
}

onMounted(async () => {
  await loadPost()
  if (post.value) loadSidebar()
})

function requireLogin(): boolean {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return false
  }
  return true
}

async function toggleLike() {
  if (!post.value || !requireLogin()) return
  try {
    const r = await communityApi.like(post.value.id)
    post.value.liked = r.liked
    post.value.likeCount = r.likeCount
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function toggleCollect() {
  if (!post.value || !requireLogin()) return
  try {
    const r = await communityApi.collect(post.value.id)
    post.value.collected = r.collected
    post.value.collectCount = r.collectCount
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function toggleFollow() {
  if (!post.value || !requireLogin()) return
  try {
    const r = await userApi.follow(post.value.userId)
    following.value = !!r.following
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function submitComment() {
  if (!post.value || !requireLogin()) return
  const text = commentText.value.trim()
  if (!text) {
    ElMessage.warning('写点评论吧~')
    return
  }
  submitting.value = true
  try {
    await communityApi.comment(post.value.id, text)
    commentText.value = ''
    post.value.commentCount = (post.value.commentCount || 0) + 1
    commentPage.value = 1
    await loadComments()
    ElMessage.success('评论成功')
  } catch (e: any) {
    if (!e?.__handled) ElMessage.error(e?.message || '评论失败')
  } finally {
    submitting.value = false
  }
}

const visibleComments = computed(() =>
  comments.value.slice(0, commentPage.value * commentSize),
)
const hasMoreComments = computed(
  () => visibleComments.value.length < (post.value?.commentCount || 0)
    && visibleComments.value.length < comments.value.length,
)

async function loadMoreComments() {
  if (!post.value || loading.value) return
  commentPage.value += 1
}

function goBack() {
  // 携带进入详情前的 Tab（推荐/附近），返回社区时落到对应信息流
  const tab = (route.query.tab as string) || 'recommend'
  router.push({ path: '/community', query: { tab } })
}

function goUser(userId?: number) {
  if (!userId) return
  if (userId === userStore.userId) router.push('/user')
  else router.push(`/user/home/${userId}`)
}

function goActivity(a: { to?: string }) {
  if (a.to) router.push(a.to)
}

// 头像兜底：取昵称首字符（支持中文/英文）
function initials(name?: string): string {
  const n = (name || '').trim()
  return n ? Array.from(n)[0].toUpperCase() : '?'
}

function goTopic(t: { id: number; name: string }) {
  router.push({ path: '/community', query: { topic: String(t.id) } })
}

function countFmt(n: number): string {
  if (!n) return '0'
  if (n >= 10000) return (n / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

// ==================== 举报 ====================
const reportVisible = ref(false)
const reportType = ref<ReportTargetType>('post')
const reportId = ref(0)
const reportTitle = ref('举报')

function openReport(type: ReportTargetType, id: number, title: string) {
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再举报')
    router.push('/login')
    return
  }
  reportType.value = type
  reportId.value = id
  reportTitle.value = title
  reportVisible.value = true
}

const deleting = ref(false)
async function deletePost() {
  if (!post.value || !isOwnPost.value) return
  try {
    await ElMessageBox.confirm('确定删除这条动态吗？删除后不可恢复。', '删除动态', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  deleting.value = true
  try {
    await communityApi.deletePost(post.value.id)
    ElMessage.success('已删除')
    router.push('/community')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  } finally {
    deleting.value = false
  }
}

async function sharePost() {
  if (!post.value) return
  const url = window.location.href
  try {
    if ((navigator as any).share) {
      await (navigator as any).share({
        title: 'PetMate 动态',
        text: post.value.content,
        url,
      })
    } else {
      await navigator.clipboard.writeText(url)
      ElMessage.success('链接已复制')
    }
  } catch {
    /* 取消 */
  }
}

function bookmark() {
  toggleCollect()
}
</script>

<template>
  <div class="post-detail">
    <div class="page-container">
      <!-- 左侧：帖子 + 评论 -->
      <main class="main-col">
        <div v-if="loading" class="state-card">加载中...</div>

        <PetEmptyState
          v-else-if="!post"
          class="state-card"
          icon="sentiment_dissatisfied"
          title="帖子不存在或已删除"
        >
          <template #action>
            <button class="ghost-btn" @click="goBack">返回</button>
          </template>
        </PetEmptyState>

        <template v-else>
          <!-- 帖子主体卡片 -->
          <article class="post-card pet-card">
            <header class="post-head">
              <div class="author-row" @click="goUser(post.userId)">
                <img
                  v-if="post.userAvatar && !headerAvatarError"
                  :src="post.userAvatar"
                  :alt="post.userNickname"
                  class="author-avatar"
                  @error="headerAvatarError = true"
                />
                <span v-else class="author-avatar ph">{{ initials(post.userNickname) }}</span>
                <div class="author-meta">
                  <div class="author-line1">
                    <span class="author-name">{{ post.userNickname || '匿名用户' }}</span>
                    <span
                      v-if="vipBadgeText(authorProfile?.vipLevel)"
                      class="vip-badge"
                    >{{ vipBadgeText(authorProfile?.vipLevel) }}</span>
                  </div>
                  <div class="author-line2">
                    {{ timeAgo(post.createdAt) }}<template v-if="post.topicName"> · 发布于{{ post.topicName }}</template>
                  </div>
                </div>
              </div>
              <button
                v-if="!isOwnPost"
                class="follow-pill"
                :class="{ following }"
                @click="toggleFollow"
              >
                <span class="material-symbols-outlined">{{ following ? 'check' : 'add' }}</span>
                {{ following ? '已关注' : '关注' }}
              </button>
              <button
                v-if="!isOwnPost"
                class="icon-btn"
                title="举报该作者"
                @click="openReport('user', post.userId, '举报作者')"
              >
                <span class="material-symbols-outlined">report_problem</span>
              </button>
              <button
                v-else
                class="icon-btn danger"
                :disabled="deleting"
                title="删除"
                @click="deletePost"
              >
                <span class="material-symbols-outlined">delete</span>
              </button>
            </header>

            <p class="post-content">{{ post.content }}</p>

            <div
              v-if="post.images && post.images.length"
              class="post-imgs"
              :class="imgLayout"
            >
              <img
                v-for="(img, i) in post.images"
                :key="i"
                :src="img"
                alt="配图"
                loading="lazy"
                @error="($event.target as HTMLImageElement).style.display = 'none'"
              />
            </div>

            <div class="post-actions">
              <button class="action" :class="{ active: post.liked }" @click="toggleLike">
                <span class="material-symbols-outlined">
                  {{ post.liked ? 'favorite' : 'favorite_border' }}
                </span>
                {{ countFmt(post.likeCount || 0) }}
              </button>
              <a class="action" href="#comments">
                <span class="material-symbols-outlined">chat_bubble</span>
                {{ countFmt(post.commentCount || 0) }}
              </a>
              <button class="action" @click="sharePost">
                <span class="material-symbols-outlined">share</span>
                分享
              </button>
              <button
                class="action bookmark"
                :class="{ active: post.collected }"
                @click="bookmark"
              >
                <span class="material-symbols-outlined">
                  {{ post.collected ? 'bookmark' : 'bookmark_border' }}
                </span>
              </button>
            </div>
          </article>

          <!-- 评论区卡片 -->
          <PageSection
            id="comments"
            class="comment-card"
            :title="`评论 (${post.commentCount || 0})`"
            icon="chat_bubble"
          >
            <div class="comment-editor">
              <img
                v-if="userStore.user?.avatar && !myAvatarError"
                :src="userStore.user.avatar"
                class="me-avatar"
                :alt="userStore.user?.nickname"
                @error="myAvatarError = true"
              />
              <span v-else class="me-avatar ph">{{ initials(userStore.user?.nickname) }}</span>
              <input
                v-model="commentText"
                class="comment-input"
                placeholder="分享你的看法..."
                @keyup.enter="submitComment"
              />
              <button class="send-btn" :disabled="submitting" @click="submitComment">
                {{ submitting ? '发送中' : '发送' }}
              </button>
            </div>

            <PetEmptyState
              v-if="comments.length === 0"
              icon="chat_bubble"
              title="还没有评论"
              desc="来抢沙发~"
            />

            <ul v-else class="comment-list">
              <li v-for="c in visibleComments" :key="c.id" class="comment-item">
                <img
                  v-if="c.userAvatar"
                  :src="c.userAvatar"
                  :alt="c.userNickname"
                  class="c-avatar"
                  @error="c.userAvatar = ''"
                  @click="goUser(c.userId)"
                />
                <span v-else class="c-avatar ph" @click="goUser(c.userId)">{{ initials(c.userNickname) }}</span>
                <div class="c-body">
                  <div class="c-name" @click="goUser(c.userId)">
                    {{ c.userNickname || '匿名用户' }}
                  </div>
                  <p class="c-text">{{ c.content }}</p>
                  <div class="c-meta">
                    <span class="c-time">{{ timeAgo(c.createdAt) }}</span>
                    <button
                      class="c-reply"
                      @click="commentText = `回复 @${c.userNickname || '匿名用户'}: `"
                    >
                      <span class="material-symbols-outlined">reply</span>
                      回复
                    </button>
                  </div>
                </div>
                <button class="c-report" @click="openReport('comment', c.id, '举报评论')">
                  <span class="material-symbols-outlined">report_problem</span>
                </button>
              </li>
            </ul>

            <button
              v-if="hasMoreComments"
              class="load-more"
              @click="loadMoreComments"
            >
              加载更多评论
            </button>
          </PageSection>
        </template>
      </main>

      <!-- 右侧：作者资料 / 热门活动 / 热门话题 -->
      <aside v-if="post" class="side-col">
        <section class="side-card profile-card">
          <div class="profile-top">
            <img
              v-if="authorProfile?.avatar && !profileAvatarError"
              :src="authorProfile.avatar"
              :alt="authorProfile?.nickname"
              class="big-avatar"
              @error="profileAvatarError = true"
            />
            <span
              v-else
              class="big-avatar ph"
            >{{ initials(authorProfile?.nickname || post.userNickname) }}</span>
            <div class="big-name">{{ authorProfile?.nickname || post.userNickname || '匿名用户' }}</div>
            <div class="big-bio">
              {{ authorProfile?.bio || '这个人很懒，什么都没留下~' }}
            </div>
            <div class="profile-meta" v-if="genderText(authorProfile?.gender) || authorProfile?.region">
              <span v-if="genderText(authorProfile?.gender)" class="pm-tag">
                <span class="material-symbols-outlined">{{ authorProfile?.gender === 1 ? 'male' : 'female' }}</span>{{ genderText(authorProfile?.gender) }}
              </span>
              <span v-if="authorProfile?.region" class="pm-tag">
                <span class="material-symbols-outlined">location_on</span>{{ authorProfile?.region }}
              </span>
            </div>
          </div>
          <div class="profile-stats">
            <div class="stat">
              <div class="stat-num">{{ countFmt(authorProfile?.followerCount || 0) }}</div>
              <div class="stat-label">粉丝</div>
            </div>
            <div class="stat-divider"></div>
            <div class="stat">
              <div class="stat-num">{{ countFmt(authorProfile?.totalLikes || 0) }}</div>
              <div class="stat-label">获赞</div>
            </div>
          </div>
          <button
            v-if="!isOwnPost"
            class="follow-author-btn"
            :class="{ following }"
            @click="toggleFollow"
          >
            {{ following ? '已关注' : '关注作者' }}
          </button>
        </section>

        <PageSection
          v-if="authorPosts.length"
          class="side-card"
          title="作者其他动态"
          icon="account_circle"
        >
          <template #actions>
            <a class="side-more" @click="goUser(post.userId)">查看主页</a>
          </template>
          <ul class="mini-post-list">
            <li
              v-for="p in authorPosts"
              :key="p.id"
              class="mini-post-item"
              @click="router.push({ path: `/community/post/${p.id}`, query: { tab: route.query.tab || 'recommend' } })"
            >
              <img
                v-if="p.images && p.images.length"
                :src="p.images[0]"
                class="mini-cover"
                :alt="p.content"
                @error="($event.target as HTMLImageElement).style.display = 'none'"
              />
              <div class="mini-body">
                <div class="mini-text">{{ p.content }}</div>
                <div class="mini-meta">
                  <span class="mini-stat">
                    <span class="material-symbols-outlined">favorite</span>{{ p.likeCount || 0 }}
                  </span>
                  <span class="mini-stat">
                    <span class="material-symbols-outlined">chat_bubble</span>{{ p.commentCount || 0 }}
                  </span>
                </div>
              </div>
            </li>
          </ul>
        </PageSection>

        <PageSection class="side-card" title="热门活动" icon="celebration">
          <template #actions>
            <a class="side-more" @click="router.push('/community')">查看全部</a>
          </template>
          <div v-if="activities.length === 0 && !sidebarLoading" class="side-tip">
            暂无活动
          </div>
          <div v-else class="side-activities">
            <div
              v-for="a in activities"
              :key="a.title"
              class="side-activity"
              @click="goActivity(a)"
            >
              <div
                class="activity-grad"
                :class="['variant-' + a.variant]"
                :style="{ backgroundImage: 'url(' + a.cover + ')' }"
              >
                <span class="deco material-symbols-outlined">{{ a.icon }}</span>
              </div>
              <div class="activity-mask">
                <div class="activity-title">{{ a.title }}</div>
                <div class="activity-desc">{{ a.desc }}</div>
              </div>
            </div>
          </div>
        </PageSection>

        <PageSection class="side-card" title="热门话题" icon="local_fire_department">
          <div v-if="topics.length === 0" class="side-tip">暂无话题</div>
          <div v-else class="tag-cloud">
            <a
              v-for="t in topics.slice(0, 8)"
              :key="t.id"
              class="tag-pill hot"
              @click="goTopic(t)"
            >#{{ t.name }}</a>
          </div>
        </PageSection>
      </aside>
    </div>

    <ReportDialog
      v-model="reportVisible"
      :target-type="reportType"
      :target-id="reportId"
      :title="reportTitle"
    />
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

.post-detail {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: $spacing-2xl;
}

.page-container {
  @include container-max;
  padding-top: $spacing-xl;
  display: grid;
  grid-template-columns: 1fr;
  gap: $width-gutter-desktop;

  @include desktop {
    grid-template-columns: minmax(0, 1fr) 360px;
  }
}

.main-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  min-width: 0;
}

.state-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  padding: $spacing-2xl;
  text-align: center;
  color: $color-text-secondary;

  &.empty {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: $spacing-sm;

    .material-symbols-outlined {
      font-size: 48px;
      opacity: 0.5;
    }
  }
}

.ghost-btn {
  padding: 8px 20px;
  background: $color-surface-container-low;
  color: $color-text-secondary;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;

  &:hover {
    background: $color-surface-container;
  }
}

.post-card,
.comment-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  padding: $spacing-xl;

  @include desktop {
    padding: $spacing-2xl;
  }
}

.post-head {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.author-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  cursor: pointer;
  flex: 1;
  min-width: 0;
  border-radius: $radius-full;
  transition: opacity $transition-fast;

  &:hover {
    opacity: 0.85;
  }
}

.author-avatar {
  width: 48px;
  height: 48px;
  border-radius: $radius-full;
  object-fit: cover;
  flex-shrink: 0;

  &.ph {
    background: $color-tertiary-fixed;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: $color-brand;
    font-weight: $font-weight-bold;
    font-size: 18px;
  }
}

.author-meta {
  min-width: 0;
}

.author-line1 {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.author-name {
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  line-height: 1.2;
}

.vip-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  background: linear-gradient(135deg, $color-tertiary-container, $color-brand-light);
  color: $color-on-brand-container;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  border-radius: $radius-full;
  letter-spacing: 0.3px;
}

.author-line2 {
  margin-top: 2px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

.follow-pill {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 6px 18px;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    background: $color-brand-dark;
    transform: translateY(-1px);
  }

  &.following {
    background: $color-surface-container-low;
    color: $color-on-surface-variant;
    border: 1px solid $color-outline-variant;

    &:hover {
      background: $color-surface-container;
    }
  }
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: $radius-full;
  background: transparent;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast;

  .material-symbols-outlined {
    font-size: 20px;
  }

  &:hover {
    background: $color-surface-container;
    color: $color-emergency;
  }
}

.post-content {
  font-size: 18px;
  line-height: 1.7;
  color: $color-on-surface;
  margin-bottom: $spacing-lg;
  white-space: pre-wrap;
  word-break: break-word;
}

.post-imgs {
  display: grid;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;
  border-radius: $radius-lg;
  overflow: hidden;

  img {
    width: 100%;
    object-fit: cover;
    background: $color-surface-container-low;
  }

  &.one {
    grid-template-columns: 1fr;

    img {
      max-height: 520px;
      border-radius: $radius-lg;
    }
  }

  &.two {
    grid-template-columns: repeat(2, 1fr);

    img {
      aspect-ratio: 4 / 3;
    }
  }

  &.multi {
    grid-template-columns: repeat(2, 1fr);

    img {
      aspect-ratio: 1;
    }
  }
}

.post-actions {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  padding-top: $spacing-md;
  border-top: 1px solid $color-outline-variant;
  margin-top: $spacing-md;
}

.action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: transparent;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface-variant;
  cursor: pointer;
  padding: 4px 0;
  transition: color $transition-fast;
  text-decoration: none;

  .material-symbols-outlined {
    font-size: 22px;
  }

  &:hover {
    color: $color-brand;
  }

  &.active {
    color: $color-brand;
  }

  &.bookmark {
    margin-left: auto;
  }
}

.comment-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}

.comment-editor {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-full;
  margin-bottom: $spacing-lg;
}

.me-avatar {
  width: 32px;
  height: 32px;
  border-radius: $radius-full;
  object-fit: cover;
  flex-shrink: 0;

  &.ph {
    background: $color-tertiary-fixed;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: $color-brand;
    font-weight: $font-weight-bold;
    font-size: 14px;
  }
}

.comment-input {
  flex: 1;
  border: none;
  background: transparent;
  font-family: inherit;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  outline: none;
  min-width: 0;

  &::placeholder {
    color: $color-text-tertiary;
  }
}

.send-btn {
  padding: 0 18px;
  height: 32px;
  background: transparent;
  color: $color-brand;
  border: none;
  font-weight: $font-weight-bold;
  cursor: pointer;
  flex-shrink: 0;

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.comment-empty {
  text-align: center;
  color: $color-text-tertiary;
  padding: $spacing-xl 0;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.comment-item {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;

  &:last-child {
    border-bottom: none;
    padding-bottom: 0;
  }
}

.c-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  object-fit: cover;
  flex-shrink: 0;
  cursor: pointer;

  &.ph {
    background: $color-secondary-container;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: $color-brand;
    font-weight: $font-weight-bold;
    font-size: 15px;
  }
}

.c-body {
  flex: 1;
  min-width: 0;
}

.c-name {
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  cursor: pointer;
  margin-bottom: 4px;

  &:hover {
    color: $color-brand;
  }
}

.c-text {
  font-size: $font-size-body-md;
  line-height: 1.6;
  color: $color-on-surface;
  word-break: break-word;
  margin-bottom: 4px;
}

.c-meta {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.c-time {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}

.c-reply {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 2px 0;
  background: transparent;
  border: none;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: color $transition-fast;

  .material-symbols-outlined {
    font-size: 14px;
  }

  &:hover {
    color: $color-brand;
  }
}

.c-report {
  flex-shrink: 0;
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: $radius-full;
  background: transparent;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: color $transition-fast, background $transition-fast;

  .material-symbols-outlined {
    font-size: 16px;
  }

  &:hover {
    color: $color-emergency;
    background: $color-error-container;
  }
}

.load-more {
  width: 100%;
  margin-top: $spacing-md;
  padding: 10px 0;
  background: transparent;
  border: 1px solid $color-brand;
  color: $color-brand;
  border-radius: $radius-md;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast;

  &:hover:not(:disabled) {
    background: $color-brand;
    color: $color-on-brand;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.side-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.side-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  padding: $spacing-lg;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.side-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;

  .material-symbols-outlined {
    font-size: 20px;
    color: $color-brand;
  }
}

.side-more {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: color $transition-fast;

  &:hover {
    color: $color-brand;
  }
}

.side-tip {
  text-align: center;
  font-size: $font-size-label-md;
  color: $color-text-tertiary;
  padding: $spacing-md 0;
}

.profile-card {
  text-align: center;
}

.profile-top {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-md 0;
}

.big-avatar {
  width: 88px;
  height: 88px;
  border-radius: $radius-full;
  object-fit: cover;
  background: $color-tertiary-fixed;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-family: $font-family-headline;
  font-size: 36px;
  font-weight: $font-weight-bold;
  color: $color-on-brand-container;
  margin-bottom: $spacing-md;
  box-shadow: 0 4px 16px rgba(255, 122, 50, 0.18);
  border: 3px solid $color-surface-container-lowest;
}

.big-name {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.big-bio {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1.5;
  max-width: 280px;
  margin: 0 auto;
}

.profile-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: $spacing-xs;
  margin-top: $spacing-sm;
}

.pm-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 10px;
  border-radius: $radius-full;
  background: $color-surface-container-low;
  color: $color-on-surface-variant;
  font-size: $font-size-label-sm;

  .material-symbols-outlined {
    font-size: 15px;
  }
}

.profile-stats {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xl;
  padding: $spacing-md 0;
}

.stat {
  text-align: center;
}

.stat-num {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  line-height: 1.1;
}

.stat-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 2px;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: $color-outline-variant;
}

.follow-author-btn {
  display: block;
  width: 100%;
  margin-top: $spacing-sm;
  padding: 10px 0;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-body-md;
  font-weight: $font-weight-bold;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  &:hover {
    background: $color-brand-dark;
    transform: translateY(-1px);
  }

  &.following {
    background: $color-surface-container-low;
    color: $color-on-surface-variant;
    border: 1px solid $color-outline-variant;
  }
}

.side-activities {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.side-activity {
  position: relative;
  height: 104px;
  border-radius: $radius-md;
  overflow: hidden;
  cursor: pointer;
  box-shadow: $shadow-card;

  &:hover .activity-grad {
    transform: scale(1.08);
  }

  &:hover .activity-grad .deco {
    transform: scale(1.12) rotate(-6deg);
    background: rgba(255, 255, 255, 0.34);
  }
}

.activity-grad {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background-color: $color-surface-container;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  transition: transform 700ms cubic-bezier(0.16, 1, 0.3, 1), filter 700ms ease;
  will-change: transform;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.16) 0%, transparent 45%);
    pointer-events: none;
    z-index: 1;
  }

  .deco {
    position: absolute;
    top: $spacing-sm;
    right: $spacing-sm;
    width: 32px;
    height: 32px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.22);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    color: #ffffff;
    font-size: 18px;
    z-index: 2;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    transition: transform 350ms cubic-bezier(0.16, 1, 0.3, 1), background 250ms ease;
  }
}

.activity-grad.variant-donate {
  background-color: #ff7a32;
}
.activity-grad.variant-photo {
  background-color: #ffb199;
}

.activity-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: $spacing-md;
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.78) 0%, rgba(0, 0, 0, 0.45) 40%, transparent 70%);
  z-index: 3;
}

.activity-title {
  color: #ffffff;
  font-size: $font-size-label-md;
  font-weight: 700;
  margin-bottom: 2px;
}

.activity-desc {
  color: rgba(255, 255, 255, 0.8);
  font-size: 10px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.tag-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 14px;
  background: $color-surface-container-low;
  color: $color-on-surface-variant;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast, transform $transition-fast;

  &:hover {
    background: $color-brand-container;
    color: $color-brand-dark;
    transform: translateY(-1px);
  }

  &.hot {
    background: $color-error-container;
    color: $color-danger;

    &:hover {
      background: $color-emergency;
      color: #ffffff;
    }
  }
}

.mini-post-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.mini-post-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-xs;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container-low;
  }
}

.mini-cover {
  width: 56px;
  height: 56px;
  border-radius: $radius-sm;
  object-fit: cover;
  background: $color-surface-container-low;
  flex-shrink: 0;
}

.mini-body {
  flex: 1;
  min-width: 0;
}

.mini-text {
  font-size: $font-size-label-md;
  color: $color-on-surface;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 4px;
}

.mini-meta {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.mini-stat {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

@include mobile {
  .post-card,
  .comment-card,
  .side-card {
    border-radius: $radius-md;
    padding: $spacing-md;
  }

  .post-content {
    font-size: 16px;
  }

  .page-container {
    padding-left: $width-gutter-mobile;
    padding-right: $width-gutter-mobile;
  }
}
</style>
