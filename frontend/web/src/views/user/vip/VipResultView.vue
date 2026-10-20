<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { userApi } from '@/api/modules/user'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const outTradeNo = String(route.params.outTradeNo || '')
const loading = ref(true)
const paid = ref(false)
const vipLevel = ref(0)
const vipExpireAt = ref<string | null>(null)
let timer: number | null = null
let tries = 0

async function check(): Promise<boolean> {
  try {
    const s: any = await userApi.vipPayStatus(outTradeNo)
    paid.value = !!s.paid
    vipLevel.value = Number(s.vipLevel ?? 0)
    vipExpireAt.value = s.vipExpireAt ?? null
    if (paid.value) {
      await userStore.fetchUser()
      // 支付成功：展示成功态 1.5s 后自动引导至会员购买页（用户期望回跳会员中心）
      window.setTimeout(() => router.replace('/vip/open'), 1500)
      return true
    }
  } catch {
    // 忽略单次异常，继续轮询
  }
  return false
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

onMounted(async () => {
  // 先查一次；未支付则最多轮询约 60s（20 次 × 3s），覆盖支付宝回跳延迟
  const done = await check()
  loading.value = false
  if (!done) {
    timer = window.setInterval(async () => {
      tries++
      const ok = await check()
      if (ok || tries >= 20) {
        loading.value = false
        stopPoll()
      }
    }, 3000)
  }
})

onUnmounted(stopPoll)

function goVip() {
  router.replace('/vip/open')
}

function goHome() {
  router.replace('/user')
}
</script>

<template>
  <div class="vip-result">
    <div class="result-card">
      <div v-if="loading" class="state-loading">
        <span class="spinner" />
        <p>正在确认支付结果…</p>
      </div>

      <template v-else>
        <div class="state-icon" :class="{ success: paid, fail: !paid }">
          <span class="material-symbols-outlined">{{ paid ? 'check_circle' : 'schedule' }}</span>
        </div>
        <h1 class="state-title">{{ paid ? '会员开通成功' : '支付处理中' }}</h1>
        <p class="state-sub">
          <template v-if="paid">
            您已成功开通 PetMate 会员，专属权益即时生效。
            <template v-if="vipExpireAt">
              有效期至 <b>{{ String(vipExpireAt).replace('T', ' ').slice(0, 10) }}</b>
            </template>
          </template>
          <template v-else>
            我们已收到您的支付，正在为您开通会员，请稍后在「我的」中查看会员状态。
          </template>
        </p>

        <div class="actions">
          <button class="btn-primary" @click="goVip">返回会员中心</button>
          <button class="btn-ghost" @click="goHome">回到个人中心</button>
        </div>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.vip-result {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
  background: $color-background;
}

.result-card {
  width: 100%;
  max-width: 440px;
  background: #ffffff;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-xl;
  box-shadow: $shadow-md;
  padding: $spacing-2xl $spacing-lg;
  text-align: center;
}

.state-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;

  .spinner {
    width: 34px;
    height: 34px;
    border: 3px solid $color-brand-light;
    border-top-color: $color-brand;
    border-radius: $radius-full;
    animation: spin 0.8s linear infinite;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.state-icon {
  width: 76px;
  height: 76px;
  margin: 0 auto $spacing-md;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;

  .material-symbols-outlined {
    font-size: 46px;
    font-variation-settings: 'FILL' 1, 'wght' 600;
  }

  &.success {
    background: rgba(82, 196, 26, 0.12);
    color: $color-success;
  }

  &.fail {
    background: rgba(255, 122, 50, 0.12);
    color: $color-brand;
  }
}

.state-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0 0 $spacing-sm;
}

.state-sub {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: 1.6;
  margin: 0 0 $spacing-xl;

  b {
    color: $color-brand;
  }
}

.actions {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.btn-primary,
.btn-ghost {
  width: 100%;
  padding: $spacing-md;
  border-radius: $radius-md;
  font-size: $font-size-body-lg;
  font-weight: 700;
  font-family: $font-family-headline;
  cursor: pointer;
  transition: all $transition-base;
}

.btn-primary {
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  box-shadow: $shadow-sm;

  &:hover {
    background: $color-brand-dark;
  }
}

.btn-ghost {
  background: transparent;
  border: 1.5px solid $color-outline-variant;
  color: $color-on-surface;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}
</style>
