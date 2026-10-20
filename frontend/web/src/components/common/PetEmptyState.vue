<script setup lang="ts">
/**
 * PetEmptyState —— 统一的空状态
 * 柔和圆形图标底 + 宠物主题文案，消除各页面「空数据」样式不一的问题。
 */
defineProps<{
  /** Material Symbols 图标名 */
  icon?: string
  title?: string
  desc?: string
  /** 行动按钮文案（配合 actionTo 或默认 slot 使用） */
  actionText?: string
  actionTo?: string
}>()
</script>

<template>
  <div class="pet-empty">
    <div class="pet-empty__circle">
      <span class="material-symbols-outlined pet-empty__icon">{{ icon || 'pets' }}</span>
    </div>
    <p v-if="title" class="pet-empty__title">{{ title }}</p>
    <p v-if="desc" class="pet-empty__desc">{{ desc }}</p>
    <slot name="action">
      <router-link v-if="actionTo && actionText" :to="actionTo" class="pet-empty__btn">
        {{ actionText }}
      </router-link>
    </slot>
  </div>
</template>

<style lang="scss" scoped>
.pet-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: $spacing-2xl $spacing-lg;
  color: $color-text-tertiary;
}

.pet-empty__circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 88px;
  height: 88px;
  border-radius: $radius-full;
  background: $color-brand-container;
  margin-bottom: $spacing-md;
}

.pet-empty__icon {
  font-size: 44px;
  line-height: 1;
  color: $color-brand;
  font-variation-settings: 'FILL' 1, 'wght' 500;
}

.pet-empty__title {
  margin: 0;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-semibold;
  color: $color-text-secondary;
}

.pet-empty__desc {
  margin: 4px 0 0;
  font-size: $font-size-label-md;
  color: $color-text-tertiary;
  max-width: 280px;
}

.pet-empty__btn {
  @include button-primary;
  margin-top: $spacing-md;
  text-decoration: none;
}
</style>
