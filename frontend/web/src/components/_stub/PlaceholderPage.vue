<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ArrowLeft, Tools } from '@element-plus/icons-vue'

interface Props {
  pageTitle?: string
  pageDesc?: string
}

withDefaults(defineProps<Props>(), {
  pageTitle: '页面开发中',
  pageDesc: '该功能正在建设中，敬请期待',
})

const router = useRouter()

function goHome() {
  router.push('/')
}

function goBack() {
  if (window.history.length > 1) router.back()
  else router.push('/')
}
</script>

<template>
  <div class="placeholder-page">
    <div class="placeholder-card">
      <div class="icon-wrap">
        <el-icon :size="64" color="#ff7a32">
          <Tools />
        </el-icon>
      </div>
      <h1 class="title">{{ pageTitle }}</h1>
      <p class="desc">{{ pageDesc }}</p>
      <div class="actions">
        <el-button type="primary" :icon="ArrowLeft" @click="goBack">返回上一页</el-button>
        <el-button @click="goHome">回到首页</el-button>
      </div>
      <div class="tips">
        <el-tag type="info" size="small">占位页面</el-tag>
        <span class="tip-text">该页面将由后续迭代完善</span>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.placeholder-page {
  min-height: 70vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-lg;
}

.placeholder-card {
  max-width: 560px;
  padding: $spacing-3xl $spacing-2xl;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  text-align: center;
}

.icon-wrap {
  margin-bottom: $spacing-lg;
  display: flex;
  justify-content: center;
}

.title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-text-primary;
  margin-bottom: $spacing-sm;
}

.desc {
  font-size: $font-size-body-md;
  color: $color-text-secondary;
  margin-bottom: $spacing-xl;
  line-height: $line-height-normal;
}

.actions {
  display: flex;
  gap: $spacing-md;
  justify-content: center;
  margin-bottom: $spacing-lg;
}

.tips {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding-top: $spacing-md;
  border-top: 1px dashed $color-outline-variant;
}

.tip-text {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}
</style>