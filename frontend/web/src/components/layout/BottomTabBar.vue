<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()

interface TabItem {
  name: string
  label: string
  icon: string
  to: string
  activeIcon?: string
}

const tabs: TabItem[] = [
  { name: 'Home', label: '首页', icon: 'home', to: '/' },
  { name: 'Category', label: '商品', icon: 'storefront', to: '/category' },
  { name: 'Service', label: '服务', icon: 'spa', to: '/service' },
  { name: 'Community', label: '社区', icon: 'forum', to: '/community' },
  { name: 'Cart', label: '购物车', icon: 'shopping_cart', to: '/cart' },
  { name: 'User', label: '我的', icon: 'person', to: '/user' },
]

function isActive(tab: TabItem): boolean {
  if (tab.to === '/') return route.path === '/'
  return route.path.startsWith(tab.to)
}
</script>

<template>
  <nav class="bottom-tab-bar">
    <router-link
      v-for="tab in tabs"
      :key="tab.name"
      :to="tab.to"
      class="tab-item"
      :class="{ active: isActive(tab) }"
    >
      <span class="material-symbols-outlined icon" :class="{ filled: isActive(tab) }">
        {{ tab.icon }}
      </span>
      <span class="label">{{ tab.label }}</span>
    </router-link>
  </nav>
</template>

<style lang="scss" scoped>
.bottom-tab-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: $z-index-header;
  display: flex;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid $color-surface-container;
  padding-bottom: env(safe-area-inset-bottom, 0);

  // 仅移动端显示
  @include desktop {
    display: none;
  }
}

.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  padding: $spacing-xs 0;
  color: $color-text-tertiary;
  transition: color $transition-fast;
  min-height: 56px;

  &.active {
    color: $color-brand;
  }

  .icon {
    font-size: 24px;
    font-variation-settings: 'FILL' 0, 'wght' 400;

    &.filled {
      font-variation-settings: 'FILL' 1, 'wght' 600;
    }
  }

  .label {
    font-size: 11px;
    font-weight: $font-weight-medium;
  }
}
</style>