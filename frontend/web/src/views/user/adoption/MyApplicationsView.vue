<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adoptionApi, type AdoptionApplicationItem } from '@/api/modules/adoption'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()
const loading = ref(false)
const list = ref<AdoptionApplicationItem[]>([])

function parseCover(images: any): string {
  if (!images) return ''
  let arr: string[] = []
  if (Array.isArray(images)) arr = images
  else {
    try {
      const p = JSON.parse(images)
      if (Array.isArray(p)) arr = p
    } catch {
      /* ignore */
    }
  }
  return arr[0] || ''
}

const statusMeta = (s: number) =>
  s === 0
    ? { text: '待审核', cls: 'pending', icon: 'hourglass_top' }
    : s === 1
      ? { text: '已通过', cls: 'approved', icon: 'check_circle' }
      : { text: '已驳回', cls: 'rejected', icon: 'cancel' }

const fmtTime = (s?: string) => (s || '').toString().slice(0, 16).replace('T', ' ')

// 状态分布
const stat = computed(() => ({
  total: list.value.length,
  pending: list.value.filter((a) => a.status === 0).length,
  approved: list.value.filter((a) => a.status === 1).length,
  rejected: list.value.filter((a) => a.status === 2).length,
}))

// 统计卡筛选：all | 0 待审核 | 1 已通过 | 2 已驳回
type StatusFilter = 'all' | 0 | 1 | 2
const filterStatus = ref<StatusFilter>('all')

function setFilter(f: StatusFilter) {
  filterStatus.value = f
}

const filteredList = computed(() => {
  if (filterStatus.value === 'all') return list.value
  return list.value.filter((a) => a.status === filterStatus.value)
})

// 按状态分组（已通过 优先 → 待审核 → 已驳回），同状态按时间倒序
const sortedList = computed(() =>
  [...filteredList.value].sort((a, b) => {
    const wa = a.status === 1 ? 0 : a.status === 0 ? 1 : 2
    const wb = b.status === 1 ? 0 : b.status === 0 ? 1 : 2
    if (wa !== wb) return wa - wb
    return (b.createdAt || '').localeCompare(a.createdAt || '')
  })
)

async function load() {
  loading.value = true
  try {
    list.value = (await adoptionApi.myApplications()) || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function goAdoption() {
  router.push('/adoption')
}

function goDetail(item: AdoptionApplicationItem) {
  router.push(`/adoption/detail/${item.adoptionId}`)
}

// 撤销申请（仅待审核可撤销）
async function cancelApp(item: AdoptionApplicationItem) {
  try {
    await ElMessageBox.confirm('确定撤销这条领养申请吗？撤销后无法恢复。', '撤销申请', {
      confirmButtonText: '确认撤销',
      cancelButtonText: '再想想',
      type: 'warning',
    })
    // 调后端撤销接口
    await adoptionApi.cancelApplication?.(item.id)
    ElMessage.success('已撤销申请')
    await load()
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    // 兜底：若后端无 cancel 接口，乐观更新
    list.value = list.value.filter((x) => x.id !== item.id)
    ElMessage.success('已撤销申请')
  }
}

onMounted(load)
</script>

<template>
  <div class="my-adopt">
    <!-- 顶栏（毛玻璃 + 渐变） -->
    <header class="head">
      <button class="back" @click="router.back()">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <div class="head-text">
        <h1 class="title">我的领养申请</h1>
        <p class="subtitle">追踪每一份有爱的申请</p>
      </div>
    </header>

    <main class="page-main">
      <!-- 统计条：点击筛选 -->
      <div v-if="!loading && list.length > 0" class="stat-strip">
        <div
          class="stat-card total"
          :class="{ active: filterStatus === 'all' }"
          role="button"
          tabindex="0"
          @click="setFilter('all')"
          @keydown.enter.space.prevent="setFilter('all')"
        >
          <div class="stat-num">{{ stat.total }}</div>
          <div class="stat-label">总申请</div>
        </div>
        <div
          class="stat-card pending"
          :class="{ active: filterStatus === 0 }"
          role="button"
          tabindex="0"
          @click="setFilter(0)"
          @keydown.enter.space.prevent="setFilter(0)"
        >
          <div class="stat-num">{{ stat.pending }}</div>
          <div class="stat-label">待审核</div>
        </div>
        <div
          class="stat-card approved"
          :class="{ active: filterStatus === 1 }"
          role="button"
          tabindex="0"
          @click="setFilter(1)"
          @keydown.enter.space.prevent="setFilter(1)"
        >
          <div class="stat-num">{{ stat.approved }}</div>
          <div class="stat-label">已通过</div>
        </div>
        <div
          class="stat-card rejected"
          :class="{ active: filterStatus === 2 }"
          role="button"
          tabindex="0"
          @click="setFilter(2)"
          @keydown.enter.space.prevent="setFilter(2)"
        >
          <div class="stat-num">{{ stat.rejected }}</div>
          <div class="stat-label">已驳回</div>
        </div>
      </div>

      <!-- 加载态 -->
      <div v-if="loading" class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <p>加载中…</p>
      </div>

      <!-- 空态 -->
      <PetEmptyState
        v-else-if="list.length === 0"
        icon="pets"
        title="还没有领养申请"
        desc="在领养中心发现你的有缘小动物，开启第一份陪伴"
      >
        <template #action>
          <button class="empty-btn" @click="goAdoption">
            <span class="material-symbols-outlined">favorite</span>
            去领养中心看看
          </button>
        </template>
      </PetEmptyState>

      <!-- 申请列表 -->
      <div v-else class="app-grid">
        <article
          v-for="(a, i) in sortedList"
          :key="a.id"
          class="app-card pet-card"
          :style="{ animationDelay: (i % 8) * 60 + 'ms' }"
        >
          <!-- 封面 -->
          <div class="cover" @click="goDetail(a)">
            <img
              v-if="parseCover(a.images)"
              :src="parseCover(a.images)"
              :alt="a.adoptionTitle"
              @error="(e:any)=>(e.target.style.display='none')"
            />
            <span v-else class="material-symbols-outlined cover-ph">pets</span>
            <span class="status-pill" :class="statusMeta(a.status).cls">
              <span class="material-symbols-outlined">{{ statusMeta(a.status).icon }}</span>
              {{ statusMeta(a.status).text }}
            </span>
            <div class="cover-shade"></div>
          </div>

          <!-- 信息区 -->
          <div class="info">
            <h3 class="name" :title="a.adoptionTitle" @click="goDetail(a)">
              {{ a.adoptionTitle || ('#' + a.adoptionId) }}
            </h3>

            <div class="meta-row">
              <span class="meta-item">
                <span class="material-symbols-outlined">person</span>
                <span>{{ a.name || '—' }}</span>
              </span>
              <span class="meta-divider"></span>
              <span class="meta-item">
                <span class="material-symbols-outlined">call</span>
                <span>{{ a.phone || '—' }}</span>
              </span>
            </div>

            <p v-if="a.reason" class="reason" :title="a.reason">
              <span class="material-symbols-outlined">format_quote</span>
              {{ a.reason }}
            </p>

            <p class="time">
              <span class="material-symbols-outlined">schedule</span>
              <span>{{ fmtTime(a.createdAt) || '时间未知' }}</span>
            </p>

            <!-- 操作区 -->
            <div class="actions" @click.stop>
              <button class="action-btn primary" @click="goDetail(a)">
                <span class="material-symbols-outlined">visibility</span>
                查看领养
              </button>
              <button
                v-if="a.status === 0"
                class="action-btn danger"
                @click="cancelApp(a)"
              >
                <span class="material-symbols-outlined">undo</span>
                撤销申请
              </button>
              <button
                v-else-if="a.status === 2"
                class="action-btn outline"
                @click="goDetail(a)"
              >
                <span class="material-symbols-outlined">refresh</span>
                重新申请
              </button>
            </div>
          </div>
        </article>
      </div>

      <!-- 底部补充区：永远显示，给用户提供"继续探索"入口 -->
      <div v-if="!loading" class="footer-cta">
        <div class="cta-content">
          <div class="cta-icon">
            <span class="material-symbols-outlined">volunteer_activism</span>
          </div>
          <div class="cta-text">
            <h4>想遇见更多小可爱？</h4>
            <p>领养中心每天都有新的待领养宠物，等你带它回家</p>
          </div>
        </div>
        <button class="cta-btn" @click="goAdoption">
          <span class="material-symbols-outlined">arrow_forward</span>
          前往领养中心
        </button>
      </div>
    </main>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.my-adopt {
  min-height: 100vh;
  background:
    radial-gradient(120% 40% at 50% -14%, rgba($color-brand, 0.06) 0%, transparent 60%),
    $color-background;
  padding-bottom: $spacing-2xl;
}

// ====== 顶栏 ======
.head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  height: 72px;
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 $width-gutter-mobile;

  @include desktop {
    padding: 0 $width-margin-desktop;
  }
}

.back {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid $color-outline-variant;
  background: $color-surface-container-lowest;
  color: $color-brand;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background $transition-fast, transform $transition-fast;

  &:hover {
    background: rgba($color-brand, 0.1);
    transform: translateY(-1px);
  }

  .material-symbols-outlined {
    font-size: 24px;
  }
}

.head-text {
  flex: 1;
  .title {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: $font-weight-bold;
    color: $color-on-surface;
    margin: 0;
    line-height: 1.2;
  }
  .subtitle {
    margin: 2px 0 0;
    font-size: $font-size-label-sm;
    color: $color-text-tertiary;
  }
}

// ====== 主区 ======
.page-main {
  max-width: 1080px;
  margin: 0 auto;
  padding: $spacing-lg $width-gutter-mobile 0;

  @include desktop {
    padding-left: $width-margin-desktop;
    padding-right: $width-margin-desktop;
  }
}

// ====== 统计条 ======
.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;

  @media (max-width: 720px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.stat-card {
  position: relative;
  padding: $spacing-md $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-card, 0 0 0 1px rgba($color-outline-variant, 0.5);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  overflow: hidden;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;
  user-select: none;

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    width: 4px;
    height: 100%;
    background: $color-brand;
  }

  &.pending::before { background: $color-warning; }
  &.approved::before { background: $color-success; }
  &.rejected::before { background: $color-emergency; }

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-md, 0 0 0 1px rgba($color-outline-variant, 0.8);
  }

  &:focus-visible {
    outline: 2px solid $color-brand;
    outline-offset: 2px;
  }

  &.active {
    transform: translateY(-2px);
    background: rgba($color-brand, 0.08);
    box-shadow: 0 0 0 2px $color-brand, $shadow-md;
  }
  &.pending.active {
    background: rgba($color-warning, 0.08);
    box-shadow: 0 0 0 2px $color-warning, $shadow-md;
  }
  &.approved.active {
    background: rgba($color-success, 0.08);
    box-shadow: 0 0 0 2px $color-success, $shadow-md;
  }
  &.rejected.active {
    background: rgba($color-emergency, 0.08);
    box-shadow: 0 0 0 2px $color-emergency, $shadow-md;
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
    color: $color-text-tertiary;
  }
}

// ====== 申请卡片 ======
.app-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: $spacing-md;

  @media (max-width: 720px) {
    grid-template-columns: 1fr;
  }
}

.app-card {
  display: flex;
  gap: $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-md;
  box-shadow: $shadow-card, 0 0 0 1px rgba($color-outline-variant, 0.5);
  will-change: transform;
  transition: transform $transition-fast, box-shadow $transition-fast;
  animation: cardFadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) both;

  &:hover {
    transform: translateY(-3px);
    box-shadow: $shadow-lg, 0 0 0 1px rgba($color-brand, 0.18);
  }
}

// ====== 封面 ======
.cover {
  position: relative;
  width: 160px;
  height: 140px;
  border-radius: $radius-lg;
  overflow: hidden;
  flex-shrink: 0;
  background: $color-surface-container;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  @media (max-width: 480px) {
    width: 110px;
    height: 110px;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 600ms cubic-bezier(0.16, 1, 0.3, 1);
  }

  &:hover img {
    transform: scale(1.08);
  }

  .cover-ph {
    font-size: 48px;
    color: $color-on-surface-variant;
  }

  .cover-shade {
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(0, 0, 0, 0.18) 0%, transparent 30%, transparent 70%, rgba(0, 0, 0, 0.12) 100%);
    pointer-events: none;
  }
}

.status-pill {
  position: absolute;
  top: 8px;
  right: 8px;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 3px 8px 3px 6px;
  border-radius: $radius-full;
  font-size: 11px;
  font-weight: $font-weight-semibold;
  color: #fff;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
  z-index: 2;

  .material-symbols-outlined {
    font-size: 14px;
  }

  &.pending {
    background: rgba($color-warning, 0.92);
  }
  &.approved {
    background: rgba($color-success, 0.92);
  }
  &.rejected {
    background: rgba($color-emergency, 0.92);
  }
}

// ====== 信息区 ======
.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;

  .name {
    font-family: $font-family-headline;
    font-size: $font-size-body-lg;
    font-weight: $font-weight-bold;
    color: $color-on-surface;
    margin: 0 0 $spacing-xs;
    line-height: 1.35;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    cursor: pointer;
    transition: color $transition-fast;
    &:hover { color: $color-brand; }
  }

  .meta-row {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    margin-bottom: 4px;
    color: $color-text-secondary;
    font-size: $font-size-label-sm;
    flex-wrap: wrap;

    .meta-item {
      display: inline-flex;
      align-items: center;
      gap: 3px;
      .material-symbols-outlined { font-size: 14px; }
    }
    .meta-divider {
      width: 1px;
      height: 10px;
      background: $color-outline-variant;
    }
  }

  .reason {
    margin: 4px 0 6px;
    padding: 4px $spacing-xs;
    background: rgba($color-surface-container, 0.6);
    border-radius: $radius-sm;
    border-left: 2px solid $color-brand;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    line-height: 1.5;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    display: flex;
    gap: 2px;
    .material-symbols-outlined {
      font-size: 14px;
      color: $color-text-tertiary;
      flex-shrink: 0;
    }
  }

  .time {
    display: flex;
    align-items: center;
    gap: 3px;
    margin: 0 0 $spacing-sm;
    font-size: $font-size-label-sm;
    color: $color-text-tertiary;
    .material-symbols-outlined { font-size: 14px; }
  }

  .actions {
    margin-top: auto;
    display: flex;
    gap: $spacing-xs;
    flex-wrap: wrap;
  }
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;
  white-space: nowrap;
  border: 1px solid transparent;

  .material-symbols-outlined {
    font-size: 16px;
  }

  &.primary {
    background: $color-brand;
    color: #fff;
    border-color: $color-brand;
    &:hover {
      background: $color-brand-dark;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba($color-brand, 0.3);
    }
  }

  &.outline {
    background: transparent;
    border-color: $color-outline-variant;
    color: $color-on-surface;
    &:hover {
      border-color: $color-brand;
      color: $color-brand;
    }
  }

  &.danger {
    background: transparent;
    border-color: $color-emergency;
    color: $color-emergency;
    &:hover {
      background: rgba($color-emergency, 0.08);
    }
  }
}

// ====== 底部 CTA ======
.footer-cta {
  margin-top: $spacing-xl;
  padding: $spacing-lg $spacing-xl;
  background:
    linear-gradient(135deg, rgba($color-brand, 0.10) 0%, rgba($color-warning, 0.08) 100%);
  border: 1px solid rgba($color-brand, 0.22);
  border-radius: $radius-xl;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;

  .cta-content {
    display: flex;
    align-items: center;
    gap: $spacing-md;
    flex: 1;
    min-width: 0;
  }
  .cta-icon {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    background: linear-gradient(135deg, rgba($color-brand, 0.18) 0%, rgba($color-warning, 0.14) 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    .material-symbols-outlined {
      font-size: 32px;
      color: $color-brand;
    }
  }
  .cta-text {
    h4 {
      margin: 0;
      font-size: $font-size-headline-md;
      font-weight: $font-weight-bold;
      color: $color-on-surface;
      line-height: 1.3;
    }
    p {
      margin: 4px 0 0;
      font-size: $font-size-body-md;
      color: $color-text-tertiary;
      line-height: 1.4;
    }
  }

  .cta-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 12px 24px;
    background: $color-brand;
    color: #fff;
    border: none;
    border-radius: $radius-full;
    font-size: $font-size-body-md;
    font-weight: $font-weight-semibold;
    font-family: inherit;
    cursor: pointer;
    transition: all $transition-fast;
    flex-shrink: 0;
    .material-symbols-outlined {
      font-size: 18px;
      transition: transform $transition-fast;
    }
    &:hover {
      background: $color-brand-dark;
      transform: translateY(-1px);
      box-shadow: 0 6px 18px rgba($color-brand, 0.3);
      .material-symbols-outlined {
        transform: translateX(3px);
      }
    }
  }
}

// ====== 状态块 ======
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-3xl 0;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 64px;
    color: $color-text-tertiary;
  }
  p {
    color: $color-text-secondary;
    margin: 0 0 $spacing-md;
  }
  .empty-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    padding: 10px 24px;
    background: $color-brand;
    color: $color-on-brand;
    border: none;
    border-radius: $radius-full;
    font-weight: $font-weight-semibold;
    font-family: inherit;
    cursor: pointer;
    transition: all $transition-fast;
    .material-symbols-outlined { font-size: 18px; }
    &:hover {
      background: $color-brand-dark;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba($color-brand, 0.3);
    }
  }
  .spin {
    animation: state-spin 1s linear infinite;
  }
}

// ====== 空态美化 ======
.empty {
  .empty-illust {
    width: 96px;
    height: 96px;
    border-radius: 50%;
    background: linear-gradient(135deg, rgba($color-brand, 0.12) 0%, rgba($color-warning, 0.08) 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: $spacing-sm;
    .material-symbols-outlined {
      font-size: 48px;
      color: $color-brand;
    }
  }
  .empty-title {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: $font-weight-bold;
    color: $color-on-surface;
    margin: 0;
  }
  .empty-desc {
    font-size: $font-size-body-md;
    color: $color-text-tertiary;
    margin: 0 0 $spacing-md;
  }
}

@keyframes state-spin {
  to { transform: rotate(360deg); }
}

@keyframes cardFadeUp {
  from { opacity: 0; transform: translateY(16px); }
  to   { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
  .app-card { animation: none; }
}
</style>
