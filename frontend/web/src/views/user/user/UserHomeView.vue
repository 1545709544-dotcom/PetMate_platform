<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, type UserProfile } from '@/api/modules/user'
import { communityApi, type Post } from '@/api/modules/community'
import { useUserStore } from '@/stores/user'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const userId = computed(() => Number(route.params.id) || 0)

const profile = ref<UserProfile | null>(null)
const loadingProfile = ref(false)
const posts = ref<Post[]>([])
const loadingPosts = ref(false)
const toggling = ref(false)

async function loadProfile() {
  loadingProfile.value = true
  try {
    const p = await userApi.getUserProfile(userId.value)
    // 看自己 → 直接去个人中心（需求：点自己头像进自己个人中心）
    if (p.id === userStore.userId) {
      router.replace('/user')
      return
    }
    profile.value = p
  } catch (e: any) {
    ElMessage.error(e?.message || '用户不存在')
    profile.value = null
  } finally {
    loadingProfile.value = false
  }
}

async function loadPosts() {
  loadingPosts.value = true
  try {
    const res = await communityApi.postsByUser(userId.value, 1, 10)
    posts.value = res.list || []
  } catch (e) {
    posts.value = []
  } finally {
    loadingPosts.value = false
  }
}

async function toggleFollow() {
  if (!profile.value) return
  toggling.value = true
  try {
    const res = await userApi.follow(profile.value.id)
    const nowFollowing = !!res.following
    profile.value.following = nowFollowing
    if (nowFollowing) profile.value.followerCount += 1
    else profile.value.followerCount = Math.max(0, profile.value.followerCount - 1)
    ElMessage.success(nowFollowing ? '已关注' : '已取消关注')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    toggling.value = false
  }
}

function goBack() {
  router.back()
}

function onImgError() {
  if (profile.value) profile.value.avatar = ''
}

function openPost(id: number) {
  router.push(`/community/post/${id}`)
}

function formatTime(s?: string): string {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 16)
}

function genderText(g?: number): string {
  return g === 1 ? '男' : g === 2 ? '女' : ''
}

function gridClass(n: number): string {
  if (n === 1) return 'grid-1'
  if (n === 2 || n === 4) return 'grid-2'
  return 'grid-3'
}

onMounted(() => {
  loadProfile()
  loadPosts()
})
</script>

<template>
  <div class="home-view">
    <div class="home-container">
      <!-- 顶部栏 -->
      <header class="topbar">
        <button class="back-btn" @click="goBack" aria-label="返回">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <h1 class="topbar-title">
          {{ loadingProfile ? '加载中…' : (profile ? profile.nickname + ' 的主页' : '用户主页') }}
        </h1>
      </header>

      <!-- 资料卡 -->
      <section v-if="loadingProfile" class="state">加载中…</section>

      <PetEmptyState
        v-else-if="!profile"
        icon="person_off"
        title="用户不存在或已注销"
      >
        <template #action>
          <button class="empty-action" @click="goBack">返回</button>
        </template>
      </PetEmptyState>

      <section v-else class="profile-card">
        <div class="pc-avatar">
          <img v-if="profile.avatar" :src="profile.avatar" :alt="profile.nickname" @error="onImgError" />
          <span v-else class="pc-avatar-fallback">{{ (profile.nickname || '?').charAt(0) }}</span>
        </div>

        <div class="pc-main">
          <div class="pc-name-row">
            <span class="pc-name">{{ profile.nickname }}</span>
            <span v-if="profile.vipLevel && profile.vipLevel > 0" class="pc-vip">VIP{{ profile.vipLevel }}</span>
          </div>
          <p class="pc-bio">{{ profile.bio || '这个人很懒，什么都没留下~' }}</p>
          <div class="pc-meta-tags" v-if="genderText(profile.gender) || profile.region">
            <span v-if="genderText(profile.gender)" class="pc-tag">
              <span class="material-symbols-outlined">{{ profile.gender === 1 ? 'male' : 'female' }}</span>{{ genderText(profile.gender) }}
            </span>
            <span v-if="profile.region" class="pc-tag">
              <span class="material-symbols-outlined">location_on</span>{{ profile.region }}
            </span>
          </div>
        </div>

        <button
          class="pc-follow"
          :class="{ following: profile.following }"
          :disabled="toggling"
          @click="toggleFollow"
        >
          {{ profile.following ? '已关注' : '关注' }}
        </button>

        <!-- 统计 -->
        <div class="pc-stats">
          <div class="pc-stat">
            <span class="pc-stat-value">{{ profile.followerCount }}</span>
            <span class="pc-stat-label">粉丝</span>
          </div>
          <div class="pc-stat">
            <span class="pc-stat-value">{{ profile.followingCount }}</span>
            <span class="pc-stat-label">关注</span>
          </div>
          <div class="pc-stat">
            <span class="pc-stat-value">{{ profile.postCount }}</span>
            <span class="pc-stat-label">动态</span>
          </div>
        </div>
      </section>

      <!-- TA 的动态 -->
      <PageSection class="posts-section" title="TA 的动态" icon="forum">
        <div v-if="loadingPosts" class="state">加载中…</div>

        <PetEmptyState
          v-else-if="posts.length === 0"
          icon="post_add"
          title="还没有发布动态"
          desc="TA 还没有分享过毛孩子的日常～"
        />

        <ul v-else class="post-list">
          <li
            v-for="p in posts"
            :key="p.id"
            class="post-card pet-card"
            @click="openPost(p.id)"
          >
            <p class="post-content">{{ p.content }}</p>

            <div
              v-if="p.images && p.images.length"
              class="post-images"
              :class="gridClass(p.images.length)"
            >
              <img
                v-for="(img, i) in p.images.slice(0, 9)"
                :key="i"
                :src="img"
                :alt="'动态图片' + (i + 1)"
                class="post-image"
                @error="($event.target as HTMLImageElement).style.display = 'none'"
              />
            </div>

            <div class="post-meta">
              <span class="post-meta-item">
                <span class="material-symbols-outlined">favorite</span>
                {{ p.likeCount || 0 }}
              </span>
              <span class="post-meta-item">
                <span class="material-symbols-outlined">chat_bubble</span>
                {{ p.commentCount || 0 }}
              </span>
              <span class="post-meta-time">{{ formatTime(p.createdAt) }}</span>
            </div>
          </li>
        </ul>
      </PageSection>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.home-view {
  min-height: 100vh;
  background: $color-background;
}

.home-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
}

// ============ 顶部栏 ============
.topbar {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: $radius-full;
  background: $color-surface-container-low;
  color: $color-on-surface;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container;
  }

  .material-symbols-outlined {
    font-size: 22px;
  }
}

.topbar-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  flex: 1;
  @include text-ellipsis(1);
}

// ============ 状态 ============
.state {
  padding: $spacing-2xl;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
}

.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
}

.empty-icon {
  font-size: 56px;
  opacity: 0.4;
  color: $color-on-surface-variant;
}

.empty-text {
  margin: 0;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

.empty-action {
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-xl;
  border: 1px solid $color-brand;
  border-radius: $radius-full;
  background: transparent;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: rgba(255, 122, 50, 0.08);
  }
}

// ============ 资料卡 ============
.profile-card {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-2xl;
  margin-bottom: $spacing-2xl;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.3);

  @include desktop {
    flex-direction: row;
    align-items: flex-start;
    flex-wrap: wrap;
  }
}

.pc-avatar {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: $radius-full;
  overflow: hidden;
  background: $color-brand-container;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 3px solid #ffffff;
  box-shadow: $shadow-md;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.pc-avatar-fallback {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-brand;
}

.pc-main {
  flex: 1;
  min-width: 0;
  text-align: center;

  @include desktop {
    text-align: left;
  }
}

.pc-name-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;

  @include desktop {
    justify-content: flex-start;
  }
}

.pc-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}

.pc-vip {
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
  background: $color-tertiary-fixed;
  color: #5b4300;
  font-size: $font-size-label-sm;
  font-weight: 600;
}

.pc-bio {
  margin: $spacing-xs 0 0;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  @include text-ellipsis(2);
}

.pc-meta-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  margin-top: $spacing-sm;
}

.pc-tag {
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

.pc-follow {
  flex-shrink: 0;
  padding: $spacing-sm $spacing-xl;
  border: none;
  border-radius: $radius-full;
  background: $color-brand;
  color: #ffffff;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast, opacity $transition-fast;

  &:hover:not(:disabled) {
    background: $color-brand-dark;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &.following {
    background: $color-surface-container-low;
    color: $color-on-surface-variant;
    border: 1px solid $color-outline-variant;

    &:hover:not(:disabled) {
      background: $color-surface-container;
      color: $color-emergency;
    }
  }
}

.pc-stats {
  display: flex;
  justify-content: center;
  gap: $spacing-2xl;
  width: 100%;
  padding-top: $spacing-md;
  border-top: 1px solid $color-outline-variant;

  @include desktop {
    justify-content: flex-start;
  }
}

.pc-stat {
  display: flex;
  flex-direction: column;
  align-items: center;

  @include desktop {
    align-items: flex-start;
  }
}

.pc-stat-value {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.pc-stat-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  letter-spacing: 0.05em;
}

// ============ 动态 ============
.section-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-lg;

  .material-symbols-outlined {
    color: $color-brand;
  }
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  list-style: none;
  margin: 0;
  padding: 0;
}

.post-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.3);
  cursor: pointer;
  transition: box-shadow $transition-base;

  &:hover {
    box-shadow: $shadow-md;
  }
}

.post-content {
  margin: 0 0 $spacing-md;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  white-space: pre-wrap;
  word-break: break-word;
  @include text-ellipsis(4);
}

.post-images {
  display: grid;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;

  &.grid-1 {
    grid-template-columns: minmax(0, 240px);
  }

  &.grid-2 {
    grid-template-columns: repeat(2, 1fr);
  }

  &.grid-3 {
    grid-template-columns: repeat(3, 1fr);
  }
}

.post-image {
  width: 100%;
  aspect-ratio: 1 / 1;
  object-fit: cover;
  border-radius: $radius-md;
  background: $color-surface-container-low;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  color: $color-on-surface-variant;
}

.post-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;

  .material-symbols-outlined {
    font-size: 18px;
    font-variation-settings: 'FILL' 1;
  }
}

.post-meta-time {
  margin-left: auto;
  font-size: $font-size-label-sm;
  color: $color-outline-variant;
}
</style>
