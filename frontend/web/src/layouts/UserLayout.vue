<script setup lang="ts">
import AppHeader from '@/components/layout/AppHeader.vue'
import BottomTabBar from '@/components/layout/BottomTabBar.vue'
import FloatingConsultBtn from '@/components/layout/FloatingConsultBtn.vue'
import { useViewport } from '@/composables/useViewport'

const { isMobile } = useViewport()
</script>

<template>
  <div class="user-layout">
    <AppHeader />
    <main class="main-content" :class="{ 'with-bottom-bar': isMobile }">
      <router-view v-slot="{ Component }">
        <transition name="page" mode="out-in">
          <!-- 单一包裹层：保证 <Transition> 始终有单一元素根，避免多根页面组件（如 CartView）导致白屏 -->
          <div class="page-wrapper" :key="$route.path">
            <component :is="Component" />
          </div>
        </transition>
      </router-view>
    </main>
    <BottomTabBar v-if="isMobile" />
    <FloatingConsultBtn />
  </div>
</template>

<style lang="scss" scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  // 统一宠物主题氛围：干净底色 + 极淡暖橙/信任蓝径向光晕（仅渐变，无平铺水印）
  background-color: $color-background;
  background-image:
    radial-gradient(1100px 560px at 8% -10%, rgba($color-brand, 0.07), transparent 60%),
    radial-gradient(960px 520px at 102% -4%, rgba($color-secondary, 0.06), transparent 55%);
  background-repeat: no-repeat, no-repeat;
  background-size: cover, cover;
  background-position: center, center;
}

.main-content {
  flex: 1;
  width: 100%;

  &.with-bottom-bar {
    padding-bottom: 64px; // BottomTabBar 高度
  }
}
</style>