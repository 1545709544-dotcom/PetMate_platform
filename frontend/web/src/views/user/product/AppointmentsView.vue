<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { appointmentApi, type Appointment } from '@/api/modules/appointment'

const router = useRouter()
const list = ref<Appointment[]>([])
const loading = ref(false)

const activeCategory = ref('')
const activeStatus = ref('') // ''=全部

// 状态筛选：覆盖后端全部状态；C 端用更直观的命名
const STATUS_TABS = [
  { value: '', label: '全部' },
  { value: 'pending', label: '待确认' },
  { value: 'confirmed', label: '待服务' },
  { value: 'done', label: '已完成' },
  { value: 'rejected', label: '已拒绝' },
  { value: 'cancelled', label: '已取消' },
]

// step: 正常流程进度（已预约 → 待服务 → 已完成）；terminal 表示终态（不显示进度轴）
const statusMeta: Record<string, { label: string; cls: string; step: number; terminal?: boolean }> = {
  pending: { label: '待确认', cls: 'pending', step: 0 },
  confirmed: { label: '待服务', cls: 'confirmed', step: 1 },
  done: { label: '已完成', cls: 'done', step: 2 },
  rejected: { label: '已拒绝', cls: 'rejected', step: -1, terminal: true },
  cancelled: { label: '已取消', cls: 'cancelled', step: -1, terminal: true },
}
const STEP_LABELS = ['已预约', '待服务', '已完成']

const categoryTabs = computed(() => {
  const set = new Set<string>()
  list.value.forEach((a) => a.category && set.add(a.category))
  return ['', ...Array.from(set)]
})

const filtered = computed(() =>
  list.value.filter(
    (a) =>
      (!activeCategory.value || a.category === activeCategory.value) &&
      (!activeStatus.value || a.status === activeStatus.value),
  ),
)

// 顶部概览统计（基于全量数据，与筛选无关）
const stats = computed(() => {
  const c = { pending: 0, confirmed: 0, done: 0, ended: 0 }
  for (const a of list.value) {
    if (a.status === 'pending') c.pending++
    else if (a.status === 'confirmed') c.confirmed++
    else if (a.status === 'done') c.done++
    else c.ended++
  }
  return c
})

async function load() {
  loading.value = true
  try {
    list.value = (await appointmentApi.list()) ?? []
  } catch (e: any) {
    ElMessage.error(e?.message || '预约列表加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function rebook(a: Appointment) {
  router.push(`/appointment/booking/${a.serviceId}`)
}

// 用户侧仅可取消（pending/confirmed）；完成(done)由商家核销，C 端不暴露
async function cancel(a: Appointment) {
  try {
    await ElMessageBox.confirm('确定取消该预约吗？取消后如需服务请重新预约。', '取消预约', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想',
    })
  } catch {
    return
  }
  try {
    await appointmentApi.updateStatus(a.id, 'cancelled')
    ElMessage.success('已取消预约')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

function goBook() {
  router.push('/service')
}

onMounted(load)
</script>

<template>
  <div class="appointments-view">
    <div class="appointments-container">
      <nav class="breadcrumb">
        <span>个人中心</span>
        <span class="sep">›</span>
        <span class="current">我的预约</span>
      </nav>

      <div class="page-head">
        <div>
          <h1 class="page-title">我的预约</h1>
          <p class="page-sub">查看你预约的到店服务，状态由门店确认与核销。</p>
        </div>
        <button class="action-btn primary" @click="goBook">
          <span class="material-symbols-outlined">add</span> 新的预约
        </button>
      </div>

      <!-- 概览统计 -->
      <div v-if="!loading && list.length" class="stats">
        <div class="stat">
          <span class="stat-num pending">{{ stats.pending }}</span>
          <span class="stat-label">待确认</span>
        </div>
        <div class="stat">
          <span class="stat-num confirmed">{{ stats.confirmed }}</span>
          <span class="stat-label">待服务</span>
        </div>
        <div class="stat">
          <span class="stat-num done">{{ stats.done }}</span>
          <span class="stat-label">已完成</span>
        </div>
        <div class="stat">
          <span class="stat-num ended">{{ stats.ended }}</span>
          <span class="stat-label">已结束</span>
        </div>
      </div>

      <!-- 分类 Tab（仅存在多种分类时显示） -->
      <nav v-if="!loading && list.length && categoryTabs.length > 2" class="tabs">
        <button
          v-for="c in categoryTabs"
          :key="'c' + c"
          class="tab"
          :class="{ active: activeCategory === c }"
          @click="activeCategory = c"
        >
          {{ c === '' ? '全部' : c }}
        </button>
      </nav>

      <!-- 状态 Tab -->
      <nav v-if="!loading && list.length" class="tabs status-tabs">
        <button
          v-for="s in STATUS_TABS"
          :key="'s' + s.value"
          class="tab"
          :class="{ active: activeStatus === s.value }"
          @click="activeStatus = s.value"
        >
          {{ s.label }}
        </button>
      </nav>

      <div v-if="loading" class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <p>加载中…</p>
      </div>

      <div v-else-if="!list.length" class="state-block">
        <div class="state-icon-wrap">
          <span class="material-symbols-outlined">event_available</span>
        </div>
        <p class="state-title">还没有预约记录</p>
        <p class="state-sub">浏览服务，为毛孩子预约一次贴心护理</p>
        <button class="retry-btn" @click="goBook">去预约</button>
      </div>

      <div v-else-if="!filtered.length" class="state-block">
        <p class="state-title">该筛选下暂无预约</p>
      </div>

      <div v-else class="list">
        <div v-for="a in filtered" :key="a.id" class="card" :class="statusMeta[a.status]?.cls">
          <!-- 进度时间轴（非终态） -->
          <div v-if="!statusMeta[a.status]?.terminal" class="timeline">
            <div
              v-for="(label, i) in STEP_LABELS"
              :key="label"
              class="tl-step"
              :class="{ done: i <= (statusMeta[a.status]?.step ?? 0) }"
            >
              <span class="tl-dot"></span>
              <span class="tl-label">{{ label }}</span>
            </div>
          </div>
          <!-- 终态横幅 -->
          <div v-else class="terminal-banner" :class="statusMeta[a.status]?.cls">
            <span class="material-symbols-outlined">{{ a.status === 'cancelled' ? 'cancel' : 'block' }}</span>
            {{ a.status === 'cancelled' ? '预约已取消' : '预约已被门店拒绝' }}
          </div>

          <div class="card-main">
            <div class="card-top">
              <div>
                <span v-if="a.category" class="cat-tag">{{ a.category }}</span>
                <h3 class="service-name">{{ a.serviceName }}</h3>
              </div>
              <span class="status" :class="statusMeta[a.status]?.cls">
                {{ statusMeta[a.status]?.label || a.status }}
              </span>
            </div>
            <p class="store">
              <span class="material-symbols-outlined">store</span> {{ a.storeName }}
            </p>
            <p class="time">
              <span class="material-symbols-outlined">schedule</span> {{ a.appointmentTime }}
            </p>
            <p v-if="a.note" class="note">
              <span class="material-symbols-outlined">edit_note</span> 备注：{{ a.note }}
            </p>

            <div class="card-actions">
              <button
                v-if="a.status === 'pending' || a.status === 'confirmed'"
                class="act danger"
                @click="cancel(a)"
              >取消预约</button>
              <button class="act" @click="rebook(a)">再次预约</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

.appointments-view {
  min-height: 100vh;
  background: $color-background;
}
.appointments-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

// ============ 面包屑 + 标题（与我的订单一致） ============
.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;

  .sep {
    color: $color-text-tertiary;
  }

  .current {
    color: $color-brand;
    font-weight: 600;
  }
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}
.page-sub {
  margin-top: 4px;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

// ============ 操作按钮（与我的订单一致：圆角 pill） ============
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;
  border: 1px solid transparent;

  .material-symbols-outlined {
    font-size: 20px;
  }

  &.primary {
    background: $color-brand;
    border-color: $color-brand;
    color: #fff;
    &:hover { background: #ff9461; border-color: #ff9461; }
  }
}

// ====== 概览统计 ======
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-md;
}
.stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.stat-num {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 800;
  line-height: 1.1;
  &.pending { color: $color-brand; }
  &.confirmed { color: $color-success; }
  &.done { color: $color-secondary; }
  &.ended { color: $color-on-surface-variant; }
}
.stat-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  font-weight: 600;
}

// ============ Tabs（通栏分段，与我的订单一致） ============
.tabs {
  display: flex;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  overflow: hidden;
  box-shadow: $shadow-sm;
}
.tab {
  flex: 1;
  padding: $spacing-md 0;
  text-align: center;
  border: none;
  background: transparent;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;
  position: relative;

  &:hover { color: $color-brand; }
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
.status-tabs { margin-top: -$spacing-sm; }

// ============ 空状态（与我的订单一致） ============
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
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 56px;
    color: $color-brand;
    opacity: 0.6;
  }
  p { margin: 0; font-size: $font-size-body-md; }
  &.spin { animation: state-spin 1s linear infinite; }

  .state-title {
    font-family: $font-family-headline;
    font-size: $font-size-body-lg;
    font-weight: 600;
    color: $color-on-surface;
  }
  .state-sub {
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;
  }
}
.state-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  .material-symbols-outlined { font-size: 56px; opacity: 0.6; color: $color-brand; }
}
.retry-btn {
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-xl;
  background: $color-brand;
  color: #fff;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  &:hover { background: #ff9461; }
}

.list { display: flex; flex-direction: column; gap: $spacing-md; }
.card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border-left: 4px solid transparent;
  transition: box-shadow $transition-fast;
  &.pending { border-left-color: $color-brand; }
  &.confirmed { border-left-color: $color-success; }
  &.done { border-left-color: $color-secondary; }
  &.cancelled { border-left-color: $color-outline-variant; opacity: 0.85; }
  &.rejected { border-left-color: $color-emergency; opacity: 0.9; }
  &:hover { box-shadow: $shadow-md; }
}

.timeline {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
}
.tl-step {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-outline;
  .tl-dot {
    width: 10px;
    height: 10px;
    border-radius: $radius-full;
    background: $color-outline-variant;
    transition: background $transition-fast;
  }
  &.done {
    color: $color-on-surface;
    font-weight: 600;
    .tl-dot { background: $color-brand; }
  }
  &:not(:last-child)::after {
    content: '';
    width: 18px;
    height: 2px;
    background: $color-outline-variant;
    margin-left: 4px;
  }
}
.terminal-banner {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
  color: $color-on-surface-variant;
  font-weight: 600;
  .material-symbols-outlined { color: $color-emergency; }
  &.cancelled .material-symbols-outlined { color: $color-on-surface-variant; }
}

.card-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-md;
  margin-bottom: $spacing-sm;
}
.cat-tag {
  display: inline-block;
  padding: 2px $spacing-sm;
  background: $color-brand-container;
  color: $color-on-brand-container;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  margin-bottom: 4px;
}
.service-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}
.status {
  padding: 2px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;
  white-space: nowrap;
  &.pending { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.confirmed { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.cancelled { background: rgba(0, 0, 0, 0.06); color: $color-on-surface-variant; }
  &.done { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  &.rejected { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
}
.store, .time, .note {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 18px; }
}
.time { margin-top: 4px; color: $color-on-surface; font-weight: 600; }
.note { margin-top: 4px; }

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-md;
}
.act {
  padding: $spacing-xs $spacing-lg;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  background: $color-surface-container-lowest;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;
  &:hover { border-color: $color-brand; color: $color-brand; }
  &.danger { color: $color-emergency; border-color: rgba(255, 77, 79, 0.4); }
  &.danger:hover { background: rgba(255, 77, 79, 0.1); }
}

@keyframes state-spin {
  to { transform: rotate(360deg); }
}
</style>
