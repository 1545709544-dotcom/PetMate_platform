<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

// 紧急熔断
interface Emergency { enabled: boolean; updatedBy: string; updatedAt: string }
const emergency = ref<Emergency>({ enabled: false, updatedBy: '', updatedAt: '' })
const emergencyLoading = ref(false)

async function loadEmergency() {
  emergencyLoading.value = true
  try {
    emergency.value = await adminApi.getEmergencyBreak()
  } catch (e: any) {
    console.error('[RiskCenterView] loadEmergency failed:', e)
  } finally {
    emergencyLoading.value = false
  }
}
onMounted(loadEmergency)

async function toggleEmergency() {
  const next = !emergency.value.enabled
  try {
    await ElMessageBox.confirm(
      next ? '开启紧急熔断模式？开启后敏感词审核将自动驳回所有命中内容。' : '关闭紧急熔断模式？',
      next ? '开启紧急熔断' : '关闭紧急熔断',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch { return }
  try {
    emergency.value = await adminApi.setEmergencyBreak(next)
    ElMessage.success(next ? '紧急熔断已开启' : '紧急熔断已关闭')
  } catch (e: any) {
    ElMessage.error(e?.message || '切换失败')
  }
}
</script>

<template>
  <div class="risk-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">风控中心</h1>
        <p class="page-sub">全局安全与熔断控制。敏感词的新增 / 编辑 / 统计请在「敏感词管理」页进行。</p>
      </div>
    </div>

    <section class="card">
      <div class="card-head">
        <div class="card-title-row">
          <span class="material-symbols-outlined">shield</span>
          <h2>紧急熔断</h2>
        </div>
      </div>

      <div class="emergency-body" :class="{ active: emergency.enabled }">
        <div class="emergency-info">
          <div class="status-tag" :class="{ on: emergency.enabled }">
            {{ emergency.enabled ? '已开启' : '未开启' }}
          </div>
          <p class="emergency-desc">
            开启后，平台所有内容发布入口（社区动态、AI 问诊、评论、商品评价、用户资料等）命中敏感词的内容将被统一自动驳回。
          </p>
          <span class="emergency-scope">一处开关 · 全局生效；关闭后各模块恢复原有策略</span>
          <span v-if="emergency.updatedAt" class="emergency-meta">
            最后更新：{{ emergency.updatedAt.slice(0, 19) }}
          </span>
        </div>
        <button class="btn-emergency" :disabled="emergencyLoading" @click="toggleEmergency">
          <span class="material-symbols-outlined">power_settings_circle</span>
          {{ emergency.enabled ? '关闭紧急熔断' : '开启紧急熔断' }}
        </button>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.risk-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: $spacing-md;
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
.card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.card-head {
  margin-bottom: $spacing-md;
}
.card-title-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  .material-symbols-outlined { color: $color-secondary; }
  h2 {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 600;
    color: $color-on-surface;
  }
}
.emergency-body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $spacing-md;
  flex-wrap: wrap;
  padding: $spacing-md;
  border-radius: $radius-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  &.active {
    border-color: $color-emergency;
    background: rgba(255, 77, 79, 0.06);
  }
}
.status-tag {
  display: inline-block;
  padding: 2px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  background: $color-surface-container;
  color: $color-on-surface-variant;
  &.on { background: $color-emergency; color: #fff; }
}
.emergency-desc {
  margin: $spacing-sm 0;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  max-width: 640px;
}
.emergency-scope {
  display: inline-block;
  margin-top: $spacing-xs;
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  background: $color-surface-container;
}
.emergency-meta {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.btn-emergency {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-full;
  font-weight: 700;
  cursor: pointer;
  border: none;
  background: $color-brand;
  color: #fff;
  &:disabled { opacity: 0.6; cursor: not-allowed; }
  .emergency-body.active & { background: $color-emergency; }
}
</style>
