<script setup lang="ts">
/**
 * PageSection —— 用户端统一的「区块」容器
 * 提供一致的区块标题（可选前缀图标 + 标题 + 右侧「更多/操作」），
 * 让所有列表/详情页的分组头部视觉统一。
 */
defineProps<{
  title?: string
  /** Material Symbols 图标名，渲染在标题前缀 */
  icon?: string
  /** 右侧「更多」跳转地址；与 actions slot 互斥，slot 优先 */
  to?: string
  /** 「更多」文案 */
  moreText?: string
}>()
</script>

<template>
  <section class="page-section">
    <header v-if="title || $slots.actions" class="section-head">
      <h2 class="section-title">
        <span v-if="icon" class="material-symbols-outlined pet-icon">{{ icon }}</span>
        <span>{{ title }}</span>
      </h2>
      <slot name="actions">
        <router-link v-if="to" :to="to" class="section-more">
          <span>{{ moreText || '更多' }}</span>
          <span class="material-symbols-outlined">chevron_right</span>
        </router-link>
      </slot>
    </header>
    <div class="section-body">
      <slot />
    </div>
  </section>
</template>

<style lang="scss" scoped>
.page-section {
  margin-bottom: $spacing-2xl;

  // 移动端收紧间距，保持呼吸感
  @include mobile {
    margin-bottom: $spacing-xl;
  }
}
</style>
