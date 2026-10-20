<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, type TaskItem } from '@/api/modules/user'
import { usePointsStore } from '@/stores/points'

const router = useRouter()
const pointsStore = usePointsStore()

const tasks = ref<TaskItem[]>([])
const loading = ref(false)
const claimingCode = ref<string | null>(null)

// 每日进度
const dailyPercent = computed(() => {
  const s = pointsStore.summary
  if (!s || s.dailyLimit <= 0) return 0
  return Math.min(100, Math.round((s.dailyEarned / s.dailyLimit) * 100))
})

async function load() {
  loading.value = true
  try {
    await pointsStore.fetch(true)
    const list = await userApi.taskList()
    tasks.value = list ?? []
  } catch {
    ElMessage.error('任务列表加载失败')
  } finally {
    loading.value = false
  }
}

async function claim(t: TaskItem) {
  if (t.completed || claimingCode.value) return
  claimingCode.value = t.code
  try {
    const res = await userApi.completeTask(t.code)
    pointsStore.setBalance(res.balance)
    if (res.alreadyDone) {
      ElMessage.info('该任务已完成啦~')
    } else if (res.granted <= 0) {
      ElMessage.warning('今日鱼干获取已达上限，明天再来吧')
    } else {
      ElMessage.success(`领取成功，获得 ${res.granted} 鱼干 🐟`)
    }
    // 刷新任务完成态
    await load()
  } catch {
    ElMessage.error('领取失败，请稍后重试')
  } finally {
    claimingCode.value = null
  }
}

function goToCheckout() {
  router.push('/checkout')
}

const iconOf = (code: string) =>
  code === 'signin' ? 'calendar_month'
  : code === 'complete_profile' ? 'badge'
  : code === 'first_post' ? 'campaign'
  : code === 'share' ? 'share'
  : 'task_alt'

onMounted(load)
</script>

<template>
  <div class="task-center">
    <div class="tc-container">
      <!-- 顶部余额卡 -->
      <section class="balance-card">
        <div class="balance-glow"></div>
        <div class="balance-main">
          <div class="balance-icon">🐟</div>
          <div class="balance-meta">
            <p class="balance-label">我的鱼干</p>
            <p class="balance-value">{{ pointsStore.balance }}</p>
          </div>
        </div>
        <div class="balance-extra">
          <div class="extra-item">
            <span class="extra-num">{{ pointsStore.summary?.totalEarned ?? 0 }}</span>
            <span class="extra-label">累计获得</span>
          </div>
          <div class="extra-item">
            <span class="extra-num">{{ pointsStore.summary?.totalSpent ?? 0 }}</span>
            <span class="extra-label">累计消耗</span>
          </div>
          <button class="use-btn" @click="goToCheckout">
            去抵扣
            <span class="material-symbols-outlined">arrow_forward</span>
          </button>
        </div>
        <!-- 每日上限进度 -->
        <div class="daily-bar">
          <div class="daily-bar-head">
            <span>今日已得 {{ pointsStore.summary?.dailyEarned ?? 0 }} / {{ pointsStore.summary?.dailyLimit ?? 50 }} 鱼干</span>
            <span v-if="(pointsStore.summary?.dailyEarned ?? 0) >= (pointsStore.summary?.dailyLimit ?? 50)" class="daily-full">已达上限</span>
          </div>
          <div class="daily-track">
            <div class="daily-fill" :style="{ width: dailyPercent + '%' }"></div>
          </div>
        </div>
      </section>

      <!-- 任务列表 -->
      <section class="task-section">
        <h3 class="section-title"><span class="bar"></span>做任务赚鱼干</h3>
        <ul class="task-list">
          <li
            v-for="t in tasks"
            :key="t.code"
            class="task-item"
            :class="{ done: t.completed }"
          >
            <div class="task-icon">
              <span class="material-symbols-outlined">{{ iconOf(t.code) }}</span>
            </div>
            <div class="task-body">
              <p class="task-title">{{ t.title }}</p>
              <p class="task-desc">{{ t.description }}</p>
            </div>
            <div class="task-reward">+{{ t.reward }} 🐟</div>
            <button
              class="task-btn"
              :class="{ claimed: t.completed }"
              :disabled="t.completed || claimingCode === t.code"
              @click="claim(t)"
            >
              <span v-if="claimingCode === t.code" class="mini-spinner"></span>
              <span v-else-if="t.completed">已完成</span>
              <span v-else>领取</span>
            </button>
          </li>
        </ul>
      </section>

      <!-- 获取/抵扣说明 -->
      <section class="rule-section">
        <h3 class="section-title"><span class="bar"></span>鱼干怎么玩</h3>
        <div class="rule-grid">
          <div class="rule-card">
            <span class="material-symbols-outlined">add_circle</span>
            <p><b>获取：</b>每日签到 +5 · 发动态 +10 · 完成任务 +20 · 每消费 ¥1 = 1 鱼干</p>
          </div>
          <div class="rule-card">
            <span class="material-symbols-outlined">remove_circle</span>
            <p><b>抵扣：</b>10 鱼干 = ¥1，下单时单笔最多抵订单金额 10%</p>
          </div>
          <div class="rule-card">
            <span class="material-symbols-outlined">timer</span>
            <p><b>每日上限：</b>获取类鱼干每日最多 50，次日 0 点重置</p>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.task-center {
  min-height: 100%;
  background: $color-surface-container-lowest;
  padding: $spacing-xl 0 calc($spacing-2xl + env(safe-area-inset-bottom));
}

.tc-container {
  @include container-max;
  max-width: 720px;
  padding: 0 $width-gutter-mobile;
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;

  @include desktop {
    padding: 0 $width-gutter-desktop;
  }
}

// 余额卡
.balance-card {
  position: relative;
  overflow: hidden;
  border-radius: $radius-xl;
  padding: $spacing-xl;
  background: linear-gradient(135deg, $color-brand, #ff9a5a);
  color: #fff;
  box-shadow: $shadow-lg;
}

.balance-glow {
  position: absolute;
  top: -40%;
  right: -10%;
  width: 240px;
  height: 240px;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.35), transparent 70%);
  pointer-events: none;
}

.balance-main {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  position: relative;
  z-index: 1;
}

.balance-icon {
  font-size: 44px;
  line-height: 1;
  filter: drop-shadow(0 4px 8px rgba(0, 0, 0, 0.15));
}

.balance-label {
  margin: 0;
  font-size: $font-size-label-md;
  opacity: 0.9;
}

.balance-value {
  margin: 2px 0 0;
  font-family: $font-family-headline;
  font-weight: $font-weight-extrabold;
  font-size: $font-size-display-lg;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.balance-extra {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  margin-top: $spacing-md;
  position: relative;
  z-index: 1;
}

.extra-item {
  display: flex;
  flex-direction: column;
}

.extra-num {
  font-weight: $font-weight-bold;
  font-size: $font-size-body-lg;
  font-variant-numeric: tabular-nums;
}

.extra-label {
  font-size: $font-size-label-sm;
  opacity: 0.85;
}

.use-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  border: none;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.92);
  color: $color-brand-dark;
  font-weight: $font-weight-semibold;
  font-size: $font-size-label-md;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(0, 0, 0, 0.18);
  }

  &:active {
    transform: scale(0.97);
  }
}

.daily-bar {
  margin-top: $spacing-lg;
  position: relative;
  z-index: 1;

  .daily-bar-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: $font-size-label-sm;
    margin-bottom: $spacing-xs;
  }

  .daily-full {
    background: rgba(255, 255, 255, 0.25);
    padding: 1px 8px;
    border-radius: $radius-full;
    font-weight: 600;
  }
}

.daily-track {
  height: 8px;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.28);
  overflow: hidden;
}

.daily-fill {
  height: 100%;
  border-radius: $radius-full;
  background: #fff;
  transition: width $transition-base;
}

// 通用区块标题
.section-title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-text-primary;
  margin: 0 0 $spacing-md;

  .bar {
    width: 4px;
    height: 18px;
    border-radius: $radius-full;
    background: $color-brand;
  }
}

// 任务列表
.task-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.task-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-md;
    border-color: rgba($color-brand, 0.3);
  }

  &.done {
    opacity: 0.7;
  }
}

.task-icon {
  @include flex-center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: $radius-full;
  background: $color-brand-container;
  color: $color-brand;

  .material-symbols-outlined {
    font-size: 24px;
  }
}

.task-body {
  flex: 1;
  min-width: 0;
}

.task-title {
  margin: 0;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
}

.task-desc {
  margin: 2px 0 0;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

.task-reward {
  flex-shrink: 0;
  font-weight: $font-weight-bold;
  color: $color-brand-dark;
  font-size: $font-size-body-md;
  font-variant-numeric: tabular-nums;
}

.task-btn {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: $radius-full;
  background: $color-brand;
  color: #fff;
  font-weight: $font-weight-semibold;
  font-size: $font-size-label-md;
  cursor: pointer;
  transition: transform $transition-fast, background $transition-fast;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    background: $color-brand-dark;
  }

  &:active:not(:disabled) {
    transform: scale(0.96);
  }

  &:disabled {
    cursor: default;
  }

  &.claimed {
    background: $color-surface-container;
    color: $color-text-secondary;
  }
}

.mini-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

// 规则
.rule-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-sm;

  @include tablet {
    grid-template-columns: repeat(3, 1fr);
  }
}

.rule-card {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;

  .material-symbols-outlined {
    font-size: 24px;
    color: $color-brand;
  }

  p {
    margin: 0;
    font-size: $font-size-label-md;
    color: $color-text-secondary;
    line-height: 1.5;
  }
}
</style>
