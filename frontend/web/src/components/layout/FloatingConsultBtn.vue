<script setup lang="ts">
import { useRouter, useRoute } from 'vue-router'
import { computed } from 'vue'

const router = useRouter()
const route = useRoute()

const isVisible = computed(() => {
  // 在登录、AI 问诊、404 等页面不显示悬浮按钮
  const hidden = ['/login', '/register', '/consult']
  return !hidden.some((p) => route.path.startsWith(p))
})

function goConsult() {
  router.push('/consult')
}
</script>

<template>
  <transition name="fab">
    <button v-if="isVisible" class="floating-consult-btn" aria-label="AI 问诊" @click="goConsult">
      <span class="material-symbols-outlined icon">auto_awesome</span>
      <span class="text">AI 问诊</span>
    </button>
  </transition>
</template>

<style lang="scss" scoped>
.floating-consult-btn {
  position: fixed;
  right: 24px;
  bottom: 80px;
  z-index: $z-index-header - 1;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-md;
  background: linear-gradient(135deg, $color-secondary, lighten($color-secondary, 15%));
  color: $color-on-secondary;
  border-radius: $radius-full;
  box-shadow: $shadow-floating;
  cursor: pointer;
  transition: all $transition-base;
  user-select: none;

  // 移动端：仅显示图标，节省空间
  @include mobile {
    bottom: 76px; // 在 BottomTabBar 上方
    right: 16px;
    padding: $spacing-sm;
  }

  &:hover {
    transform: scale(1.05) translateY(-2px);
    box-shadow: 0 12px 32px rgba(0, 96, 172, 0.4);
  }

  &:active {
    transform: scale(0.98);
  }

  .icon {
    font-size: 24px;
    font-variation-settings: 'FILL' 1, 'wght' 600;
  }

  .text {
    font-size: $font-size-label-md;
    font-weight: $font-weight-semibold;

    @include mobile {
      display: none;
    }
  }
}

.fab-enter-active,
.fab-leave-active {
  transition: all $transition-base;
}

.fab-enter-from,
.fab-leave-to {
  opacity: 0;
  transform: scale(0.5) translateY(20px);
}
</style>