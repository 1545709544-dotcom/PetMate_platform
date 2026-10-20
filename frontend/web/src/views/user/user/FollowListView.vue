<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, type FollowUser } from '@/api/modules/user'
import { useUserStore } from '@/stores/user'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

type Tab = 'following' | 'fans'
const activeTab = ref<Tab>((route.query.tab as Tab) === 'fans' ? 'fans' : 'following')

const list = ref<FollowUser[]>([])
const total = ref(0)
const loading = ref(false)
const toggling = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    const res =
      activeTab.value === 'following'
        ? await userApi.following(1, 50)
        : await userApi.followers(1, 50)
    list.value = res.list || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(activeTab, load)

const title = computed(() => (activeTab.value === 'following' ? '关注' : '粉丝'))

async function toggle(u: FollowUser) {
  toggling.value = u.id
  try {
    const res = await userApi.follow(u.id)
    const nowFollowing = !!res.following
    // 在「关注」列表里取消关注 → 直接从列表移除
    if (activeTab.value === 'following' && !nowFollowing) {
      list.value = list.value.filter((x) => x.id !== u.id)
      total.value = Math.max(0, total.value - 1)
      ElMessage.success('已取消关注')
      return
    }
    u.following = nowFollowing
    ElMessage.success(nowFollowing ? '已关注' : '已取消关注')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    toggling.value = null
  }
}

function onImgError(u: FollowUser) {
  u.avatar = ''
}

function goBack() {
  router.back()
}

// 点头像/昵称：自己 → 个人中心；他人 → 对面主页
function goUser(u: FollowUser) {
  if (u.id === userStore.userId) {
    router.push('/user')
  } else {
    router.push(`/user/home/${u.id}`)
  }
}

function switchTab(t: Tab) {
  if (t === activeTab.value) return
  activeTab.value = t
  router.replace({ path: '/user/follow-list', query: { tab: t } })
}
</script>

<template>
  <div class="follow-view">
    <div class="follow-container">
      <!-- 顶部栏 -->
      <header class="topbar">
        <button class="back-btn" @click="goBack" aria-label="返回">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <h1 class="topbar-title">{{ title }}</h1>
        <span class="topbar-count">{{ total }}</span>
      </header>

      <!-- Tabs -->
      <div class="tabs">
        <button class="tab" :class="{ active: activeTab === 'following' }" @click="switchTab('following')">
          关注
        </button>
        <button class="tab" :class="{ active: activeTab === 'fans' }" @click="switchTab('fans')">
          粉丝
        </button>
      </div>

      <!-- 加载 / 空态 / 列表 -->
      <div v-if="loading" class="state">加载中…</div>

      <PetEmptyState
        v-else-if="list.length === 0"
        :icon="activeTab === 'following' ? 'person_add_disabled' : 'group_off'"
        :title="activeTab === 'following' ? '还没有关注任何人' : '还没有粉丝'"
      >
        <template #action>
          <button class="empty-action" @click="goBack">去发现感兴趣的人</button>
        </template>
      </PetEmptyState>

      <ul v-else class="user-list">
        <li v-for="u in list" :key="u.id" class="user-item">
          <div class="u-avatar" @click="goUser(u)">
            <img v-if="u.avatar" :src="u.avatar" :alt="u.nickname" @error="onImgError(u)" />
            <span v-else class="u-avatar-fallback">{{ (u.nickname || '?').charAt(0) }}</span>
          </div>

          <div class="u-info" @click="goUser(u)">
            <div class="u-name-row">
              <span class="u-name">{{ u.nickname || '用户' + u.id }}</span>
              <span v-if="u.vipLevel && u.vipLevel > 0" class="u-vip">VIP{{ u.vipLevel }}</span>
            </div>
            <p class="u-bio">{{ u.bio || '这个人很懒，什么都没留下~' }}</p>
          </div>

          <button
            class="u-action"
            :class="{ following: u.following }"
            :disabled="toggling === u.id"
            @click="toggle(u)"
          >
            {{ u.following ? '已关注' : '回关' }}
          </button>
        </li>
      </ul>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.follow-view {
  min-height: 100vh;
  background: $color-background;
}

.follow-container {
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
}

.topbar-count {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  background: $color-surface-container-low;
  padding: 4px $spacing-md;
  border-radius: $radius-full;
}

// ============ Tabs ============
.tabs {
  display: flex;
  gap: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
  margin-bottom: $spacing-lg;
}

.tab {
  position: relative;
  padding: 10px 4px;
  margin-right: $spacing-lg;
  border: none;
  background: transparent;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-semibold;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: color $transition-fast;

  &::after {
    content: '';
    position: absolute;
    left: 0;
    right: 0;
    bottom: -1px;
    height: 2px;
    background: transparent;
    transition: background $transition-fast;
  }

  &.active {
    color: $color-brand;

    &::after {
      background: $color-brand;
    }
  }
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

// ============ 用户列表 ============
.user-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  list-style: none;
  margin: 0;
  padding: 0;
}

.user-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.3);
  transition: box-shadow $transition-base;

  &:hover {
    box-shadow: $shadow-md;
  }
}

.u-avatar {
  width: 56px;
  height: 56px;
  flex-shrink: 0;
  border-radius: $radius-full;
  overflow: hidden;
  background: $color-brand-container;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: transform $transition-fast;

  &:hover {
    transform: scale(1.05);
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.u-avatar-fallback {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-brand;
}

.u-info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}

.u-name-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.u-name {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.u-vip {
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
  background: $color-tertiary-fixed;
  color: #5b4300;
  font-size: $font-size-label-sm;
  font-weight: 600;
}

.u-bio {
  margin: 4px 0 0;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  @include text-ellipsis(1);
}

.u-action {
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
</style>
