<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'

const route = useRoute()
const router = useRouter()

const phone = ref(String(route.query.phone || ''))
const loading = ref(false)
const exists = ref(false)
const status = ref<number | null>(null)  // 0 待审核 / 1 通过 / 2 驳回
const ownerName = ref('')
const storeName = ref('')
const submitAt = ref('')
const reviewedAt = ref('')
const reviewNote = ref('')
const isMerchant = ref(false)

const statusClass = computed(() => {
  if (status.value === 1) return 'success'
  if (status.value === 2) return 'error'
  return 'pending'
})
const statusIcon = computed(() => {
  if (status.value === 1) return 'check_circle'
  if (status.value === 2) return 'cancel'
  return 'hourglass_top'
})
const statusTitle = computed(() => {
  if (status.value === 1) return '审核已通过'
  if (status.value === 2) return '审核未通过'
  return '审核中, 请耐心等待'
})

async function refresh() {
  if (!phone.value) return
  loading.value = true
  try {
    const r: any = await merchantApi.queryApplyStatus(phone.value)
    exists.value = !!r.exists
    status.value = r.status ?? null
    ownerName.value = r.ownerName || ''
    storeName.value = r.storeName || ''
    submitAt.value = r.submitAt || ''
    reviewedAt.value = r.reviewedAt || ''
    reviewNote.value = r.reviewNote || ''
    isMerchant.value = !!r.isMerchant
  } catch (e: any) {
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

onMounted(refresh)
</script>

<template>
  <div class="result-view">
    <div class="result-container">
      <button class="back" @click="router.push('/merchant/landing')">
        <span class="material-symbols-outlined">arrow_back</span>
        返回登录
      </button>

      <h1 class="title">入驻申请状态</h1>

      <div v-if="loading" class="state">加载中...</div>

      <div v-else-if="!exists && !isMerchant" class="empty card">
        <span class="material-symbols-outlined">help</span>
        <p>该手机号（<b>{{ phone }}</b>）尚未提交入驻申请</p>
        <button class="btn-primary" @click="router.push('/merchant/apply')">立即申请</button>
      </div>

      <div v-else-if="isMerchant" class="state-card success card">
        <span class="material-symbols-outlined">check_circle</span>
        <h2>账号已开通</h2>
        <p>该手机号已是商家账号, 请直接登录</p>
        <button class="btn-primary" @click="router.push('/merchant/landing')">前往登录</button>
      </div>

      <div v-else class="state-card card" :class="statusClass">
        <span class="material-symbols-outlined">{{ statusIcon }}</span>
        <h2>{{ statusTitle }}</h2>

        <div class="info-list">
          <div class="info-row">
            <span class="info-label">手机号</span>
            <span class="info-value">{{ phone }}</span>
          </div>
          <div class="info-row" v-if="ownerName">
            <span class="info-label">店主</span>
            <span class="info-value">{{ ownerName }}</span>
          </div>
          <div class="info-row" v-if="storeName">
            <span class="info-label">门店</span>
            <span class="info-value">{{ storeName }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">提交时间</span>
            <span class="info-value">{{ submitAt || '—' }}</span>
          </div>
          <div class="info-row" v-if="reviewedAt">
            <span class="info-label">审核时间</span>
            <span class="info-value">{{ reviewedAt }}</span>
          </div>
          <div class="info-row" v-if="reviewNote">
            <span class="info-label">{{ status === 2 ? '驳回原因' : '审核备注' }}</span>
            <span class="info-value">{{ reviewNote }}</span>
          </div>
        </div>

        <p v-if="status === 1" class="hint success-hint">
          审核已通过,可使用该手机号和申请时填写的密码登录商家中心
        </p>
        <p v-else-if="status === 2" class="hint error-hint">
          申请未通过,如有疑问请联系运营团队
        </p>

        <div class="actions">
          <button class="btn-secondary" @click="refresh">刷新状态</button>
          <button v-if="status === 1" class="btn-primary" @click="router.push('/merchant/landing')">
            前往登录
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.result-view { min-height: 100vh; background: $color-background; padding: $spacing-lg 0 $spacing-2xl; }
.result-container { @include container-max; max-width: 720px; }
.back {
  display: inline-flex; align-items: center; gap: 4px;
  background: transparent; border: none;
  color: $color-secondary; font-size: $font-size-label-md;
  font-weight: 600; cursor: pointer; margin-bottom: $spacing-md;
  .material-symbols-outlined { font-size: 18px; }
  &:hover { color: $color-brand; }
}
.title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg; font-weight: 700;
  color: $color-on-surface; margin-bottom: $spacing-lg;
}
.card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-lg;
}
.state { padding: $spacing-2xl; text-align: center; color: $color-on-surface-variant; }
.empty {
  text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: $spacing-sm;
  .material-symbols-outlined { font-size: 56px; color: $color-on-surface-variant; opacity: 0.6; }
  p { margin: 0; color: $color-on-surface-variant; }
}
.state-card {
  text-align: center;
  .material-symbols-outlined { font-size: 64px; }
  h2 { font-family: $font-family-headline; font-size: $font-size-headline-md; font-weight: 700; margin: $spacing-sm 0; }
  &.success .material-symbols-outlined { color: $color-success; }
  &.error .material-symbols-outlined { color: $color-emergency; }
  &.pending .material-symbols-outlined { color: $color-warning; }
}
.info-list {
  background: $color-surface-container;
  border-radius: $radius-md;
  padding: $spacing-md;
  margin: $spacing-md 0;
  text-align: left;
}
.info-row {
  display: flex; justify-content: space-between;
  padding: $spacing-xs 0;
  font-size: $font-size-body-md;
  border-bottom: 1px dashed $color-outline-variant;
  &:last-child { border-bottom: none; }
}
.info-label { color: $color-on-surface-variant; }
.info-value { color: $color-on-surface; font-weight: 600; }
.hint {
  font-size: $font-size-body-md;
  margin: $spacing-sm 0 $spacing-md;
  &.success-hint { color: $color-success; }
  &.error-hint { color: $color-emergency; }
}
.actions {
  display: flex; gap: $spacing-sm; justify-content: center;
}
.btn-primary, .btn-secondary {
  padding: $spacing-sm $spacing-xl;
  border-radius: $radius-md;
  font-weight: 700; font-size: $font-size-body-md;
  cursor: pointer; border: 1px solid transparent;
  transition: background $transition-fast;
}
.btn-primary {
  background: $color-brand; color: #fff;
  &:hover { background: $color-brand-dark; }
}
.btn-secondary {
  background: transparent; border-color: $color-outline-variant; color: $color-on-surface;
  &:hover { border-color: $color-brand; color: $color-brand; }
}
</style>