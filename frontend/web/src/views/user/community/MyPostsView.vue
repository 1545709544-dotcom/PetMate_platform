<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { userApi } from '@/api/modules/user'
import { communityApi, type Post } from '@/api/modules/community'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

const posts = ref<Post[]>([])
const total = ref(0)
const page = ref(1)
const size = 12
const loading = ref(false)
const finished = ref(false)
const userId = ref<number | null>(null)

function fmtTime(s?: string): string {
  return (s || '').replace('T', ' ').slice(0, 16)
}

// 缩略图取第一张图；无图时回退到品牌渐变占位
function thumb(p: Post): string {
  return p.images && p.images.length ? p.images[0] : ''
}

async function loadMe() {
  try {
    const u: any = await userApi.me()
    userId.value = u.id
    await loadPosts(true)
  } catch (e) {
    console.error('[MyPosts] loadMe failed:', e)
  }
}

async function loadPosts(reset = false) {
  if (!userId.value || loading.value) return
  if (reset) {
    page.value = 1
    posts.value = []
    finished.value = false
  }
  loading.value = true
  try {
    const resp = await communityApi.postsByUser(userId.value, page.value, size)
    const list = resp.list || []
    posts.value = reset ? list : posts.value.concat(list)
    total.value = resp.total ?? posts.value.length
    if (posts.value.length >= total.value) finished.value = true
    else page.value += 1
  } catch (e) {
    console.error('[MyPosts] loadPosts failed:', e)
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (!finished.value && !loading.value) loadPosts(false)
}

onMounted(loadMe)
</script>

<template>
  <div class="my-posts">
    <div class="mp-container">
      <!-- 顶部导航 -->
      <div class="mp-topbar">
        <button class="mp-back" @click="router.back()" aria-label="返回">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <h1 class="mp-title">我的动态</h1>
        <button class="mp-publish" @click="router.push('/community/publish')">
          <span class="material-symbols-outlined">add</span>发布动态
        </button>
      </div>

      <p class="mp-sub" v-if="total">共 {{ total }} 条动态</p>

      <!-- 动态列表 -->
      <div class="mp-grid" v-if="posts.length">
        <div
          v-for="p in posts"
          :key="p.id"
          class="mp-card pet-card"
          @click="router.push(`/community/post/${p.id}`)"
        >
          <div class="mp-thumb">
            <img
              v-if="thumb(p)"
              :src="thumb(p)"
              :alt="p.content || '动态'"
              loading="lazy"
              @error="(e:any)=>e.target.style.display='none'"
            />
            <span v-else class="material-symbols-outlined mp-thumb-ph">pets</span>
          </div>
          <div class="mp-body">
            <h3 class="mp-text">{{ p.content }}</h3>
            <div class="mp-meta">
              <span class="mp-time">{{ fmtTime(p.createdAt) }}</span>
              <span class="mp-stat">
                <span class="material-symbols-outlined">favorite</span>{{ p.likeCount }}
              </span>
              <span class="mp-stat">
                <span class="material-symbols-outlined">chat_bubble</span>{{ p.commentCount }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 空态 -->
      <PetEmptyState
        v-else-if="!loading"
        icon="pets"
        title="你还没有发布过动态"
        desc="分享你和毛孩子的精彩瞬间吧～"
      >
        <template #action>
          <button class="mp-empty-btn" @click="router.push('/community/publish')">去发布第一条</button>
        </template>
      </PetEmptyState>

      <!-- 加载更多 -->
      <div class="mp-more-wrap" v-if="posts.length && !finished">
        <button class="mp-more" :disabled="loading" @click="loadMore">
          {{ loading ? '加载中…' : '加载更多' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.my-posts {
  min-height: 100vh;
  background: $color-background;
}

.mp-container {
  @include container-max;
  max-width: 1080px;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
}

.mp-topbar {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-sm;
}

.mp-back {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  border: 1px solid $color-outline-variant;
  background: #ffffff;
  color: $color-on-surface;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  &:hover {
    background: $color-surface-container-low;
    transform: translateX(-2px);
  }

  .material-symbols-outlined {
    font-size: 22px;
  }
}

.mp-title {
  flex: 1;
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.mp-publish {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-sm $spacing-lg;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: background $transition-fast;

  &:hover {
    background: $color-brand-dark;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.mp-sub {
  margin: 0 0 $spacing-lg;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

.mp-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;

  @include tablet {
    grid-template-columns: repeat(2, 1fr);
  }

  @include desktop {
    grid-template-columns: repeat(2, 1fr);
  }
}

.mp-card {
  display: flex;
  gap: $spacing-md;
  padding: $spacing-md;
  background: #ffffff;
  border-radius: $radius-lg;
  border: 1px solid rgba(223, 192, 179, 0.3);
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: box-shadow $transition-base, transform $transition-base;

  &:hover {
    box-shadow: $shadow-md;
    transform: translateY(-2px);

    .mp-thumb img {
      transform: scale(1.06);
    }
  }
}

.mp-thumb {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: $radius-default;
  overflow: hidden;
  background: linear-gradient(135deg, $color-brand-container 0%, $color-surface-container 100%);
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform $transition-base;
  }

  .mp-thumb-ph {
    font-size: 40px;
    color: $color-brand;
    font-variation-settings: 'FILL' 1;
  }
}

.mp-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.mp-text {
  margin: 0 0 6px;
  font-size: $font-size-body-md;
  font-weight: 500;
  color: $color-on-surface;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.mp-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;

  .mp-stat {
    display: inline-flex;
    align-items: center;
    gap: 2px;

    .material-symbols-outlined {
      font-size: 14px;
    }
  }
}

.mp-time {
  margin-right: auto;
}

.mp-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-2xl $spacing-lg;
  text-align: center;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 56px;
    color: $color-brand-container;
  }

  p {
    margin: 0;
    font-size: $font-size-body-lg;
  }
}

.mp-empty-btn {
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-xl;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: background $transition-fast;

  &:hover {
    background: $color-brand-dark;
  }
}

.mp-more-wrap {
  display: flex;
  justify-content: center;
  margin-top: $spacing-xl;
}

.mp-more {
  padding: $spacing-sm $spacing-2xl;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  color: $color-on-surface;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover:not(:disabled) {
    background: $color-surface-container-low;
  }

  &:disabled {
    opacity: 0.6;
    cursor: default;
  }
}
</style>
