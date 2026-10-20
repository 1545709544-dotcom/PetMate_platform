<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { communityApi } from '@/api/modules/community'
import { useUserStore } from '@/stores/user'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()
const userStore = useUserStore()

interface ContestPost {
  rank: number
  id: number
  userId: number
  userNickname: string
  userAvatar?: string
  content: string
  images?: string[]
  likeCount: number
  commentCount: number
  liked: boolean
  collected: boolean
  createdAt?: string
}
interface Stats {
  participantCount: number
  postCount: number
  totalLikes: number
}
interface Mine {
  participated: boolean
  postCount: number
  bestLikeCount: number
}

const HASHTAG = '#宠物摄影大赛'

const loading = ref(false)
const list = ref<ContestPost[]>([])
const stats = ref<Stats>({ participantCount: 0, postCount: 0, totalLikes: 0 })
const mine = ref<Mine>({ participated: false, postCount: 0, bestLikeCount: 0 })
const likingId = ref<number | null>(null)

const isLogin = computed(() => userStore.isLoggedIn)
const podium = computed(() => list.value.slice(0, 3))
const rest = computed(() => list.value.slice(3))

async function load() {
  loading.value = true
  try {
    const res = await communityApi.photoContest(50)
    list.value = (res?.list || []).map((x) => ({
      ...x,
      likeCount: Number(x.likeCount || 0),
    }))
    stats.value = res?.stats || { participantCount: 0, postCount: 0, totalLikes: 0 }
    mine.value = res?.mine
      ? (res.mine as Mine)
      : { participated: false, postCount: 0, bestLikeCount: 0 }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

// 点赞即比赛投票：乐观更新 + 服务端确认；排名名次保留为加载时刻快照，刷新后重排
async function toggleLike(p: ContestPost) {
  if (!isLogin.value) {
    ElMessage.warning('请先登录后再为作品点赞~')
    router.push({ name: 'Login' })
    return
  }
  if (likingId.value === p.id) return
  likingId.value = p.id
  const prevLiked = p.liked
  const prevCount = p.likeCount
  p.liked = !p.liked
  p.likeCount += p.liked ? 1 : -1
  try {
    const res = await communityApi.like(p.id)
    p.liked = res.liked
    p.likeCount = res.likeCount
  } catch (e: any) {
    p.liked = prevLiked
    p.likeCount = prevCount
    ElMessage.error(e?.message || '操作失败')
  } finally {
    likingId.value = null
  }
}

function goPublish() {
  if (!isLogin.value) {
    ElMessage.warning('请先登录后再参赛~')
    router.push({ name: 'Login' })
    return
  }
  router.push({ path: '/community/publish', query: { hashtag: '宠物摄影大赛' } })
}

function goDetail(id: number) {
  router.push({ name: 'PostDetail', params: { id } })
}

function avatarText(name?: string) {
  return name ? name.slice(0, 1) : '?'
}

// 领奖台渐变
const podiumGradient: Record<number, string> = {
  1: 'linear-gradient(135deg,#f9d423,#ff7a32)',
  2: 'linear-gradient(135deg,#bdc3c7,#e8edf0)',
  3: 'linear-gradient(135deg,#cd7f32,#e6a86b)',
}
const medalIcon: Record<number, string> = {
  1: 'military_tech',
  2: 'workspace_premium',
  3: 'emoji_events',
}
</script>

<template>
  <div class="pc-view">
    <!-- 顶部 hero -->
    <header class="pc-hero">
      <div class="pc-hero-inner">
        <div class="pc-hero-icon">
          <span class="material-symbols-outlined">photo_camera</span>
        </div>
        <div class="pc-hero-text">
          <h1 class="pc-title">宠物摄影大赛</h1>
          <p class="pc-sub">发帖带上 {{ HASHTAG }} 即自动参赛 · 以点赞数为比赛数据排名</p>
        </div>
        <div class="pc-hero-stats">
          <div class="pc-stat">
            <div class="pc-stat-num">{{ stats.participantCount }}</div>
            <div class="pc-stat-label">参赛铲屎官</div>
          </div>
          <div class="pc-stat">
            <div class="pc-stat-num">{{ stats.postCount }}</div>
            <div class="pc-stat-label">参赛作品</div>
          </div>
          <div class="pc-stat">
            <div class="pc-stat-num">{{ stats.totalLikes }}</div>
            <div class="pc-stat-label">累计点赞</div>
          </div>
        </div>
      </div>
    </header>

    <div class="pc-container">
      <div v-if="loading" class="pc-loading">加载中...</div>

      <template v-else>
        <!-- 我的参赛面板 -->
        <section v-if="isLogin" class="pc-mine" :class="{ 'pc-mine-active': mine.participated }">
          <div class="pc-mine-info">
            <span class="material-symbols-outlined pc-mine-icon">emoji_events</span>
            <div>
              <div class="pc-mine-line">
                <template v-if="mine.participated">
                  已发布 <b>{{ mine.postCount }}</b> 篇参赛动态 · 取最高赞 <b>{{ mine.bestLikeCount }}</b> 计入榜单
                </template>
                <template v-else>还没有参赛作品，发布带话题的动态即可参赛~</template>
              </div>
              <div class="pc-mine-tip">发布动态时在正文带上 {{ HASHTAG }} 即视为参赛，点赞越高排名越靠前</div>
            </div>
          </div>
          <button class="pc-go-btn" @click="goPublish">
            <span class="material-symbols-outlined">add_a_photo</span>
            {{ mine.participated ? '再发一篇' : '去参赛' }}
          </button>
        </section>

        <!-- 领奖台 Top3 -->
        <section v-if="podium.length" class="pc-podium">
          <div
            v-for="p in [podium[1], podium[0], podium[2]].filter(Boolean)"
            :key="p.id"
            class="pc-pod"
            :class="['pc-pod-' + p.rank]"
            @click="goDetail(p.id)"
          >
            <div class="pc-pod-avatar" :style="{ background: podiumGradient[p.rank] }">
              <img v-if="p.userAvatar" :src="p.userAvatar" :alt="p.userNickname" />
              <span v-else>{{ avatarText(p.userNickname) }}</span>
              <div class="pc-pod-medal">
                <span class="material-symbols-outlined">{{ medalIcon[p.rank] }}</span>
              </div>
            </div>
            <div class="pc-pod-name">{{ p.userNickname }}</div>
            <button
              class="pc-pod-like"
              :class="{ 'is-liked': p.liked }"
              @click.stop="toggleLike(p)"
            >
              <span class="material-symbols-outlined">{{ p.liked ? 'favorite' : 'favorite_border' }}</span>
              {{ p.likeCount }}
            </button>
            <div class="pc-pod-rank">No.{{ p.rank }}</div>
          </div>
        </section>

        <!-- 完整榜单 -->
        <PageSection class="pc-list" title="参赛榜单 · 按点赞排名" icon="leaderboard">
          <PetEmptyState
            v-if="!list.length"
            icon="photo_camera"
            title="还没有参赛作品"
            :desc="`快来发布第一篇带 ${HASHTAG} 的动态吧~`"
          >
            <template #action>
              <button class="pc-go-btn pc-go-btn-sm" @click="goPublish">
                <span class="material-symbols-outlined">add_a_photo</span> 去参赛
              </button>
            </template>
          </PetEmptyState>
          <div v-else class="pc-list-rows">
            <div
              v-for="p in rest"
              :key="p.id"
              class="pc-row"
              :class="{ 'pc-row-me': p.userId === userStore.userId }"
              @click="goDetail(p.id)"
            >
              <div class="pc-row-rank" :class="{ 'pc-row-rank-top': p.rank <= 3 }">{{ p.rank }}</div>
              <div class="pc-row-avatar">
                <img v-if="p.userAvatar" :src="p.userAvatar" :alt="p.userNickname" />
                <span v-else>{{ avatarText(p.userNickname) }}</span>
              </div>
              <div class="pc-row-body">
                <div class="pc-row-name">
                  {{ p.userNickname }}
                  <span v-if="p.userId === userStore.userId" class="pc-row-me-tag">我</span>
                </div>
                <div class="pc-row-content">{{ p.content }}</div>
                <div v-if="p.images && p.images.length" class="pc-row-thumbs">
                  <img
                    v-for="(img, i) in p.images.slice(0, 3)"
                    :key="i"
                    :src="img"
                    alt="作品"
                    class="pc-thumb"
                  />
                </div>
              </div>
              <button
                class="pc-row-like"
                :class="{ 'is-liked': p.liked }"
                @click.stop="toggleLike(p)"
              >
                <span class="material-symbols-outlined">{{ p.liked ? 'favorite' : 'favorite_border' }}</span>
                <b>{{ p.likeCount }}</b>
              </button>
            </div>
            <p v-if="!rest.length" class="pc-list-note">前三名已在上方领奖台展示 🏆</p>
          </div>
        </PageSection>

        <!-- 规则说明 -->
        <PageSection class="pc-rules" title="活动规则" icon="gavel">
          <ul class="pc-rule-list">
            <li><span class="material-symbols-outlined">campaign</span>发布动态时，在正文带上话题 <b>{{ HASHTAG }}</b> 即视为自动参赛，无需额外报名。</li>
            <li><span class="material-symbols-outlined">thumb_up</span>比赛数据以帖子的 <b>点赞数</b> 为准，点赞越高排名越靠前。</li>
            <li><span class="material-symbols-outlined">auto_awesome</span>每人仅以点赞最高的 <b>1 篇</b> 作品计入榜单，多发布可刷新你的最好成绩。</li>
            <li><span class="material-symbols-outlined">redeem</span>为喜欢的作品点赞，也是在为它投票助力登顶领奖台。</li>
          </ul>
        </PageSection>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.pc-view {
  min-height: 100vh;
  background: $color-background;
}

// ============ Hero ============
.pc-hero {
  background: linear-gradient(135deg, #ff7a32 0%, #ff4d4f 100%);
  color: #fff;
  padding: $spacing-2xl 0 $spacing-xl;
}
.pc-hero-inner {
  @include container-max;
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  flex-wrap: wrap;
}
.pc-hero-icon {
  width: 64px;
  height: 64px;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  .material-symbols-outlined {
    font-size: 38px;
    color: #fff;
  }
}
.pc-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  margin: 0 0 4px;
}
.pc-sub {
  font-size: $font-size-label-md;
  opacity: 0.92;
  margin: 0;
}
.pc-hero-stats {
  margin-left: auto;
  display: flex;
  gap: $spacing-xl;
}
.pc-stat {
  text-align: center;
}
.pc-stat-num {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 800;
}
.pc-stat-label {
  font-size: $font-size-label-sm;
  opacity: 0.9;
}

// ============ 容器 ============
.pc-container {
  @include container-max;
  padding: $spacing-xl 0 $spacing-3xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
}
.pc-loading {
  text-align: center;
  padding: $spacing-2xl;
  color: $color-on-surface-variant;
}
.pc-section-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0 0 $spacing-md;
}

// ============ 我的参赛 ============
.pc-mine {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
  &.pc-mine-active {
    border-color: $color-brand;
    box-shadow: 0 4px 16px rgba(255, 122, 50, 0.18);
  }
}
.pc-mine-info {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}
.pc-mine-icon {
  font-size: 34px;
  color: $color-brand;
}
.pc-mine-line {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  b { color: $color-brand; }
}
.pc-mine-tip {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 4px;
}
.pc-go-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: $spacing-sm $spacing-lg;
  background: linear-gradient(135deg, #ff7a32, #ff4d4f);
  color: #fff;
  border: none;
  border-radius: $radius-full;
  font-weight: 700;
  font-size: $font-size-label-md;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: transform $transition-fast;
  .material-symbols-outlined { font-size: 20px; }
  &:hover { transform: scale(1.05); }
  &-sm { margin-top: $spacing-md; }
}

// ============ 领奖台 ============
.pc-podium {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-md;
  align-items: end;
}
.pc-pod {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-lg $spacing-md $spacing-md;
  text-align: center;
  border: 1px solid rgba(223, 192, 179, 0.2);
  transition: transform $transition-fast;
  cursor: pointer;
  &:hover { transform: translateY(-4px); }
}
.pc-pod-1 { order: 2; transform: translateY(-12px); }
.pc-pod-2 { order: 1; }
.pc-pod-3 { order: 3; }
.pc-pod-avatar {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: $radius-full;
  margin: 0 auto $spacing-sm;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: $font-size-headline-md;
  font-weight: 800;
  overflow: visible;
  img { width: 100%; height: 100%; border-radius: $radius-full; object-fit: cover; }
}
.pc-pod-medal {
  position: absolute;
  right: -6px;
  bottom: -6px;
  width: 28px;
  height: 28px;
  border-radius: $radius-full;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-sm;
  .material-symbols-outlined { font-size: 20px; color: #ffb300; }
  .pc-pod-2 & .material-symbols-outlined { color: #9aa6b2; }
  .pc-pod-3 & .material-symbols-outlined { color: #cd7f32; }
}
.pc-pod-name {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}
.pc-pod-like {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  padding: 3px 12px;
  border: 1px solid $color-outline;
  background: $color-surface-container-low;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  font-weight: 700;
  font-size: $font-size-label-sm;
  cursor: pointer;
  transition: all $transition-fast;
  .material-symbols-outlined { font-size: 16px; }
  &:hover { border-color: $color-emergency; color: $color-emergency; }
  &.is-liked {
    border-color: $color-emergency;
    color: $color-emergency;
    background: $color-error-container;
  }
}
.pc-pod-rank {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 2px;
}

// ============ 榜单 ============
.pc-list-rows {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}
.pc-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid transparent;
  transition: border-color $transition-fast;
  cursor: pointer;
  &:hover { border-color: rgba(223, 192, 179, 0.4); }
  &.pc-row-me {
    border-color: $color-brand;
    background: $color-brand-container;
  }
}
.pc-row-rank {
  width: 28px;
  text-align: center;
  font-weight: 800;
  color: $color-on-surface-variant;
  font-family: $font-family-headline;
  &.pc-row-rank-top { color: $color-brand; }
}
.pc-row-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  background: $color-tertiary-fixed;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-tertiary;
  font-weight: 700;
  overflow: hidden;
  flex-shrink: 0;
  img { width: 100%; height: 100%; object-fit: cover; }
}
.pc-row-body {
  flex: 1;
  min-width: 0;
}
.pc-row-name {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
  display: flex;
  align-items: center;
  gap: 6px;
}
.pc-row-me-tag {
  font-size: 10px;
  background: $color-brand;
  color: #fff;
  border-radius: $radius-full;
  padding: 1px 7px;
}
.pc-row-content {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 2px;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.pc-row-thumbs {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}
.pc-thumb {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  object-fit: cover;
}
.pc-row-like {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid $color-outline;
  background: $color-surface-container-low;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  cursor: pointer;
  flex-shrink: 0;
  transition: all $transition-fast;
  .material-symbols-outlined { font-size: 18px; }
  b { font-weight: 800; }
  &:hover { border-color: $color-emergency; color: $color-emergency; }
  &.is-liked {
    border-color: $color-emergency;
    color: $color-emergency;
    background: $color-error-container;
  }
}
.pc-list-note {
  text-align: center;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin: $spacing-sm 0 0;
}
.pc-empty {
  text-align: center;
  padding: $spacing-2xl;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 44px; opacity: 0.5; }
  p { margin: $spacing-sm 0 0; }
}

// ============ 规则 ============
.pc-rules {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
}
.pc-rule-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  li {
    display: flex;
    align-items: flex-start;
    gap: $spacing-sm;
    font-size: $font-size-label-md;
    color: $color-on-surface;
    .material-symbols-outlined {
      font-size: 20px;
      color: $color-brand;
      flex-shrink: 0;
      margin-top: 1px;
    }
    b { color: $color-brand; }
  }
}

// ============ 响应式 ============
@include desktop {
}
@media (max-width: 768px) {
  .pc-hero-stats { margin-left: 0; width: 100%; justify-content: space-around; }
  .pc-podium { grid-template-columns: 1fr; }
  .pc-pod-1 { order: 1; transform: none; }
  .pc-pod-2 { order: 2; }
  .pc-pod-3 { order: 3; }
  .pc-row-thumbs { display: none; }
}
</style>
