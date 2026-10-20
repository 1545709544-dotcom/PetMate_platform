<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, type Favorite } from '@/api/modules/user'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

type Tab = 'all' | Favorite['targetType']
const tabs: { id: Tab; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: 'product', label: '商品' },
  { id: 'post', label: '帖子' },
  { id: 'article', label: '文章' },
]
const activeTab = ref<Tab>('all')

const loading = ref(false)
const all = ref<Favorite[]>([])
const removing = ref<string | null>(null)

const typeMeta: Record<Favorite['targetType'], { label: string; icon: string; to: (id: number) => string }> = {
  product: { label: '商品', icon: 'shopping_bag', to: (id) => `/product/${id}` },
  post: { label: '帖子', icon: 'forum', to: (id) => `/community/post/${id}` },
  article: { label: '文章', icon: 'article', to: (id) => `/knowledge/${id}` },
  service: { label: '服务', icon: 'spa', to: (id) => `/service/${id}` },
}

const list = computed(() =>
  activeTab.value === 'all' ? all.value : all.value.filter((f) => f.targetType === activeTab.value)
)

async function load() {
  loading.value = true
  try {
    all.value = await userApi.favorites()
  } catch {
    ElMessage.error('收藏加载失败')
    all.value = []
  } finally {
    loading.value = false
  }
}

function openItem(f: Favorite) {
  const meta = typeMeta[f.targetType]
  if (meta) router.push(meta.to(f.targetId))
}

async function remove(f: Favorite) {
  const key = `${f.targetType}:${f.targetId}`
  if (removing.value) return
  removing.value = key
  try {
    await userApi.toggleFavorite(f.targetType, f.targetId)
    ElMessage.success('已取消收藏')
    all.value = all.value.filter((x) => !(x.targetType === f.targetType && x.targetId === f.targetId))
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    removing.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="collect-view">
    <div class="collect-container">
      <nav class="breadcrumb">
        <span>个人中心</span>
        <span class="sep">›</span>
        <span class="current">我的收藏</span>
      </nav>

      <h1 class="page-title">我的收藏</h1>

      <!-- 分类 Tabs（通栏分段） -->
      <nav class="tabs">
        <button
          v-for="t in tabs"
          :key="t.id"
          class="tab"
          :class="{ active: activeTab === t.id }"
          @click="activeTab = t.id"
        >{{ t.label }}</button>
      </nav>

      <main class="collect-list">
        <div v-if="loading" class="skeleton-wrap">
          <div v-for="n in 6" :key="n" class="skeleton-card">
            <div class="sk-cover skeleton" />
            <div class="sk-body">
              <div class="sk-tag skeleton" />
              <div class="sk-line skeleton" />
              <div class="sk-line short skeleton" />
            </div>
          </div>
        </div>

        <PetEmptyState
          v-else-if="list.length === 0"
          icon="favorite_border"
          title="还没有收藏的内容"
          desc="遇到心动的好物或好文，点个收藏就不会丢啦"
        >
          <template #action>
            <button class="retry-btn" @click="router.push('/')">去发现</button>
          </template>
        </PetEmptyState>

        <article
          v-for="(f, index) in list"
          :key="f.id"
          class="collect-card pet-card"
          :style="{ '--i': index }"
          @click="openItem(f)"
        >
          <div class="card-cover">
            <img v-if="f.image" :src="f.image" :alt="f.title" />
            <span v-else class="material-symbols-outlined cover-ph">{{ typeMeta[f.targetType].icon }}</span>
          </div>
          <div class="card-body">
            <span class="type-tag">{{ typeMeta[f.targetType].label }}</span>
            <p class="card-title">{{ f.title }}</p>
            <span v-if="f.price != null" class="card-price">¥{{ Number(f.price).toFixed(2) }}</span>
          </div>
          <button
            class="remove-btn"
            :class="{ removing: removing === `${f.targetType}:${f.targetId}` }"
            :disabled="removing === `${f.targetType}:${f.targetId}`"
            @click.stop="remove(f)"
            title="取消收藏"
          >
            <span class="material-symbols-outlined">{{ removing === `${f.targetType}:${f.targetId}` ? 'progress_activity' : 'delete' }}</span>
          </button>
        </article>
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

.collect-view {
  min-height: 100vh;
  background: $color-background;
}

.collect-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
}

// ============ 面包屑 + 标题（与我的订单一致） ============
.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-md;

  .sep {
    color: $color-text-tertiary;
  }

  .current {
    color: $color-brand;
    font-weight: 600;
  }
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-lg;
}

// ============ Tabs（通栏分段，与我的订单一致） ============
.tabs {
  display: flex;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  margin-bottom: $spacing-lg;
  overflow: hidden;
  box-shadow: $shadow-sm;
}

.tab {
  flex: 1;
  padding: $spacing-md 0;
  text-align: center;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;
  position: relative;
  font-family: inherit;
  border: none;
  background: transparent;

  &:hover {
    color: $color-brand;
  }

  &.active {
    color: $color-brand;
    font-weight: 700;

    &::after {
      content: '';
      position: absolute;
      bottom: 0;
      left: 25%;
      right: 25%;
      height: 3px;
      background: $color-brand;
      border-radius: $radius-full;
    }
  }
}

.collect-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.collect-card {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-sm;
  cursor: pointer;
  opacity: 0;
  animation: card-in 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
  animation-delay: calc(var(--i, 0) * 55ms);
  transition: box-shadow $transition-base, border-color $transition-fast, transform $transition-fast;

  &:hover {
    box-shadow: 0 12px 30px rgba(0, 0, 0, 0.1);
    border-color: rgba($color-brand, 0.4);
    transform: translateY(-2px);

    .card-cover img {
      transform: scale(1.1);
    }
  }
}

.card-cover {
  width: 76px;
  height: 76px;
  border-radius: $radius-default;
  overflow: hidden;
  flex-shrink: 0;
  background: $color-surface-container;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.04);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.55s cubic-bezier(0.16, 1, 0.3, 1);
  }

  .cover-ph {
    font-size: 32px;
    color: $color-on-surface-variant;
  }
}

.card-body {
  flex: 1;
  min-width: 0;
}

.type-tag {
  display: inline-block;
  font-size: $font-size-label-sm;
  color: $color-brand;
  background: $color-brand-container;
  padding: 1px 8px;
  border-radius: $radius-sm;
  margin-bottom: 5px;
}

.card-title {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0 0 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color $transition-fast;
}

.card-price {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-brand;
}

.remove-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: $color-surface-container-low;
  color: $color-on-surface-variant;
  border-radius: $radius-full;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover:not(:disabled) {
    background: $color-error-container;
    color: $color-danger;
    transform: scale(1.08);
  }

  &:active:not(:disabled) {
    transform: scale(0.92);
  }

  &.removing .material-symbols-outlined {
    animation: state-spin 1s linear infinite;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

/* 骨架屏 */
.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.skeleton-card {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  padding: $spacing-sm;
}

.sk-cover {
  width: 76px;
  height: 76px;
  border-radius: $radius-default;
  flex-shrink: 0;
}

.sk-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.sk-tag {
  width: 48px;
  height: 16px;
  border-radius: $radius-sm;
}

.sk-line {
  height: 14px;
  border-radius: 6px;
  width: 80%;
}

.sk-line.short {
  width: 40%;
}

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

/* 空状态（与我的订单一致） */
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-2xl $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
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

  .material-symbols-outlined {
    font-size: 56px;
    opacity: 0.6;
  }
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
  color: $color-on-brand;
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

  &:active {
    transform: scale(0.96);
  }
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes shimmer {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .collect-card {
    animation: none;
    opacity: 1;
  }
  .skeleton {
    animation: none;
  }
  .remove-btn.removing .material-symbols-outlined {
    animation: none;
  }
}
</style>
