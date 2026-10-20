<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { consultApi, type Consultation, type ConsultHistoryRange } from '@/api/modules/consult'

const router = useRouter()

const ranges: { id: ConsultHistoryRange; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: 'today', label: '今天' },
  { id: '7d', label: '近 7 天' },
  { id: '30d', label: '近 30 天' },
]
const activeRange = ref<ConsultHistoryRange>('all')

const loading = ref(false)
const list = ref<Consultation[]>([])

async function load() {
  loading.value = true
  try {
    list.value = await consultApi.history(activeRange.value)
  } catch {
    ElMessage.error('加载问诊记录失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function switchRange(r: ConsultHistoryRange) {
  activeRange.value = r
  load()
}

function statusText(s: Consultation['status']) {
  return s === 'active' ? '进行中' : '已结束'
}

function riskText(level: 'urgent' | 'normal' | 'good' | number | string) {
  if (level === 'urgent' || level === 2) return { text: '高风险', danger: true }
  if (level === 'good' || level === 0) return { text: '正常', ok: true }
  if (level === 'normal' || level === 1) return { text: '注意', warn: true }
  return { text: '正常', ok: true }
}

function openConsult(id?: number | string) {
  router.push(id ? { path: '/consult', query: { session: String(id) } } : '/consult')
}

onMounted(load)
</script>

<template>
  <div class="history-view">
    <header class="topbar">
      <button class="back-btn" @click="router.back()">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <span class="topbar-title">问诊记录</span>
      <button class="new-btn" @click="openConsult">
        <span class="material-symbols-outlined">add</span>
      </button>
    </header>

    <!-- 范围筛选 -->
    <nav class="range-tabs">
      <button
        v-for="r in ranges"
        :key="r.id"
        class="range-tab"
        :class="{ active: activeRange === r.id }"
        @click="switchRange(r.id)"
      >{{ r.label }}</button>
    </nav>

    <main class="history-list">
      <div v-if="loading" class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <p>加载中…</p>
      </div>

      <div v-else-if="list.length === 0" class="state-block">
        <span class="material-symbols-outlined">forum</span>
        <p>暂无问诊记录</p>
        <button class="retry-btn" @click="openConsult">去问诊</button>
      </div>

      <article
        v-for="c in list"
        :key="c.id"
        class="history-card"
        @click="openConsult(c.id)"
      >
        <div class="card-head">
          <span class="card-title">{{ c.title || 'AI 智能问诊' }}</span>
          <span class="status-badge" :class="c.status">{{ statusText(c.status) }}</span>
        </div>
        <p class="card-preview">{{ c.preview || '（无对话内容）' }}</p>
        <div class="card-foot">
          <span class="risk" :class="riskText(c.riskLevel).ok ? 'ok' : (riskText(c.riskLevel).danger ? 'danger' : 'warn')">
            {{ riskText(c.riskLevel).text }}
          </span>
          <span class="meta">共 {{ c.messageCount }} 条 · {{ c.createdAt }}</span>
        </div>
      </article>
    </main>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.history-view {
  min-height: 100vh;
  background: $color-background;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 $spacing-md;
  background: $color-surface-container-lowest;
  border-bottom: 1px solid $color-outline-variant;
}

.back-btn,
.new-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: $color-on-surface;
  border-radius: $radius-full;
  cursor: pointer;

  &:hover {
    background: $color-surface-container;
  }
}

.topbar-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

.range-tabs {
  display: flex;
  gap: $spacing-xs;
  padding: $spacing-md $spacing-md 0;
}

.range-tab {
  padding: 6px $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  &.active {
    background: $color-brand;
    color: $color-on-brand;
    border-color: $color-brand;
    font-weight: 600;
  }
}

.history-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.history-card {
  background: $color-surface-container-lowest;
  border: 1px solid rgba(223, 192, 179, 0.12);
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  padding: $spacing-lg;
  cursor: pointer;
  transition: box-shadow $transition-base, transform $transition-fast;

  &:hover {
    box-shadow: $shadow-md;
    transform: translateY(-2px);
  }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-xs;
}

.card-title {
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
}

.status-badge {
  font-size: $font-size-label-sm;
  padding: 2px 8px;
  border-radius: $radius-sm;
  font-weight: 600;

  &.active {
    background: $color-brand-container;
    color: $color-on-brand-container;
  }

  &.closed {
    background: $color-surface-container;
    color: $color-on-surface-variant;
  }
}

.card-preview {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  margin: 0 0 $spacing-sm;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.risk {
  font-size: $font-size-label-sm;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: $radius-sm;

  &.ok {
    background: $color-secondary-container;
    color: $color-on-secondary;
  }

  &.warn {
    background: rgba(255, 193, 7, 0.18);
    color: #b26a00;
  }

  &.danger {
    background: $color-error-container;
    color: $color-emergency;
  }
}

.meta {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
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

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
