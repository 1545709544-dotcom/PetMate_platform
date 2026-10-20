<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { seckillApi, type Seckill } from '@/api/modules/seckill'

const router = useRouter()

const loading = ref(false)
const list = ref<Seckill[]>([])
const grabbingId = ref<number | null>(null)
const now = ref(Date.now())
let timer: number | undefined

async function load() {
  loading.value = true
  try {
    list.value = await seckillApi.list()
  } catch {
    ElMessage.error('秒杀活动加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function progress(s: Seckill): number {
  if (s.stock <= 0) return 100
  return Math.min(100, Math.round((s.sold / s.stock) * 100))
}

// 倒计时（基于统一的 now 每秒刷新）
function remain(s: Seckill): { h: string; m: string; s: string; ended: boolean } {
  const end = new Date(s.endTime.replace(/-/g, '/')).getTime()
  let diff = Math.max(0, end - now.value)
  const ended = diff <= 0
  const h = Math.floor(diff / 3600000)
  const m = Math.floor((diff % 3600000) / 60000)
  const sec = Math.floor((diff % 60000) / 1000)
  const pad = (n: number) => String(n).padStart(2, '0')
  return { h: pad(h), m: pad(m), s: pad(sec), ended }
}

async function grab(s: Seckill) {
  if (grabbingId.value) return
  grabbingId.value = s.id
  try {
    const res = await seckillApi.grab(s.id)
    ElMessage.success('抢购成功，正在前往支付…')
    router.push(`/pay/${res.orderNo}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '抢购失败')
  } finally {
    grabbingId.value = null
  }
}

onMounted(() => {
  load()
  timer = window.setInterval(() => (now.value = Date.now()), 1000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="seckill-view">
    <header class="hero">
      <button class="back-btn" @click="router.back()">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <div class="hero-title">
        <span class="material-symbols-outlined bolt">bolt</span>
        <span>限时秒杀</span>
      </div>
      <div style="width: 40px" />
    </header>

    <main class="seckill-list">
      <div v-if="loading" class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <p>加载中…</p>
      </div>

      <div v-else-if="list.length === 0" class="state-block">
        <span class="material-symbols-outlined">bolt</span>
        <p>当前没有进行中的秒杀活动</p>
      </div>

      <article v-for="s in list" :key="s.id" class="sk-card">
        <div class="sk-cover">
          <img :src="s.productImage" :alt="s.productName" />
          <span class="sk-tag">秒杀</span>
        </div>
        <div class="sk-body">
          <p class="sk-title">{{ s.title || s.productName }}</p>

          <div class="sk-countdown">
            <span class="cd-label">距结束</span>
            <span class="cd-box">{{ remain(s).h }}</span>
            <span class="cd-colon">:</span>
            <span class="cd-box">{{ remain(s).m }}</span>
            <span class="cd-colon">:</span>
            <span class="cd-box">{{ remain(s).s }}</span>
          </div>

          <div class="sk-progress">
            <div class="sk-progress-bar" :style="{ width: progress(s) + '%' }"></div>
            <span class="sk-progress-text">已抢 {{ progress(s) }}%</span>
          </div>

          <div class="sk-foot">
            <div class="sk-price">
              <span class="sk-cur">¥</span>
              <span class="sk-amount">{{ s.seckillPrice.toFixed(2) }}</span>
              <span v-if="s.originalPrice" class="sk-origin">¥{{ s.originalPrice.toFixed(2) }}</span>
            </div>
            <button
              class="sk-grab"
              :disabled="grabbingId === s.id || remain(s).ended"
              @click="grab(s)"
            >
              <span v-if="grabbingId === s.id" class="material-symbols-outlined spin">progress_activity</span>
              <span v-else-if="remain(s).ended">已结束</span>
              <span v-else>立即抢购</span>
            </button>
          </div>
        </div>
      </article>
    </main>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.seckill-view {
  min-height: 100vh;
  background: $color-background;
}

.hero {
  position: sticky;
  top: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 $spacing-md;
  background: linear-gradient(135deg, $color-brand, $color-brand-dark);
  color: $color-on-brand;
}

.back-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: transparent;
  color: $color-on-brand;
  border-radius: $radius-full;
  cursor: pointer;
}

.hero-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;

  .bolt {
    font-size: 24px;
  }
}

.seckill-list {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.sk-card {
  display: flex;
  gap: $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  padding: $spacing-md;
}

.sk-cover {
  position: relative;
  width: 120px;
  height: 120px;
  border-radius: $radius-default;
  overflow: hidden;
  flex-shrink: 0;
  background: $color-surface-container;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.sk-tag {
  position: absolute;
  top: 0;
  left: 0;
  padding: 2px 8px;
  background: $color-emergency;
  color: #fff;
  font-size: $font-size-label-sm;
  font-weight: 700;
  border-bottom-right-radius: $radius-sm;
}

.sk-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.sk-title {
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0 0 $spacing-sm;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.4;
}

.sk-countdown {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: $spacing-sm;
}

.cd-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-right: $spacing-xs;
}

.cd-box {
  background: $color-on-surface;
  color: $color-surface-container-lowest;
  font-size: $font-size-label-sm;
  font-weight: 700;
  padding: 2px 5px;
  border-radius: 4px;
  font-variant-numeric: tabular-nums;
}

.cd-colon {
  color: $color-on-surface;
  font-weight: 700;
}

.sk-progress {
  position: relative;
  height: 16px;
  background: $color-surface-container;
  border-radius: $radius-full;
  overflow: hidden;
  margin-bottom: $spacing-sm;
}

.sk-progress-bar {
  height: 100%;
  background: linear-gradient(90deg, $color-brand, $color-emergency);
  transition: width 0.4s ease;
}

.sk-progress-text {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 10px;
  font-weight: 600;
  color: $color-on-surface;
}

.sk-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.sk-price {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: $color-emergency;
}

.sk-cur {
  font-size: $font-size-label-md;
  font-weight: 700;
}

.sk-amount {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 800;
}

.sk-origin {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  text-decoration: line-through;
}

.sk-grab {
  padding: $spacing-xs $spacing-lg;
  border: none;
  border-radius: $radius-full;
  background: linear-gradient(135deg, $color-brand, $color-emergency);
  color: #fff;
  font-size: $font-size-label-md;
  font-weight: 700;
  font-family: inherit;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  box-shadow: 0 4px 12px rgba($color-brand, 0.3);
  transition: transform $transition-fast;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }

  .spin {
    animation: state-spin 1s linear infinite;
  }
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

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
