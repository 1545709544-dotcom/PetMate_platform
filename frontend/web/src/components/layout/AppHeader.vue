<script setup lang="ts">
import { ref, computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useViewport } from '@/composables/useViewport'
import { useUser } from '@/composables/useUser'
import { useCartStore } from '@/stores/cart'
import { usePointsStore } from '@/stores/points'
import UserNotificationBell from '@/components/layout/UserNotificationBell.vue'

const router = useRouter()
const route = useRoute()
const { isMobile, isDesktop } = useViewport()
const { isLoggedIn, logout, user } = useUser()
const cartStore = useCartStore()
const pointsStore = usePointsStore()

// 性别文字：0 未知 / 1 男 / 2 女
const userGenderText = computed(() => {
  const g = user.value?.gender
  return g === 1 ? '男' : g === 2 ? '女' : ''
})

// 用户浮层(鼠标悬停或头像聚焦时显示)
const popoverVisible = ref(false)
let popoverTimer: number | null = null

function onPopoverEnter() {
  if (popoverTimer) {
    window.clearTimeout(popoverTimer)
    popoverTimer = null
  }
  popoverVisible.value = true
}

function onPopoverLeave() {
  // 鼠标/失焦后略作延迟,避免从头像快速滑到浮层或 Tab 切换时闪烁
  popoverTimer = window.setTimeout(() => {
    popoverVisible.value = false
  }, 160)
}

onBeforeUnmount(() => {
  if (popoverTimer) window.clearTimeout(popoverTimer)
})

const navItems = [
  { label: '首页', to: '/' },
  { label: '商品', to: '/category' },
  { label: '社区', to: '/community' },
  { label: '知识', to: '/knowledge' },
  { label: '领养', to: '/adoption' },
  { label: 'AI 问诊', to: '/consult' },
]

const showMobileMenu = ref(false)

function toggleMenu() {
  showMobileMenu.value = !showMobileMenu.value
}

function goToProfile() {
  // 无论在哪个页面,直接到 /user。如已在 /user,避免无意义跳转,仅滚动到顶
  if (route.path === '/user') {
    window.scrollTo({ top: 0, behavior: 'smooth' })
    return
  }
  router.push('/user')
  popoverVisible.value = false
}

async function handleLogout() {
  popoverVisible.value = false
  try {
    await logout()
    ElMessage.success('已退出登录')
  } catch (e) {
    // 即便 logout 失败,前端也应跳到登录页,避免未清空状态
    console.error('logout failed', e)
  }
  router.push('/login')
}

const showCartBadge = computed(() => !cartStore.loading && cartStore.totalCount > 0)

// 每次进网页(刷新)拉一次真实购物车数据,覆盖持久化旧值,确保红点不异常
onMounted(() => {
  cartStore.fetch()
  if (isLoggedIn.value) pointsStore.fetch()
})

// 登录态变化:登录后拉数据,登出即清空(游客不显示红点)
watch(isLoggedIn, (val) => {
  if (val) {
    cartStore.fetch()
    pointsStore.fetch()
  } else {
    cartStore.clear()
  }
})
</script>

<template>
  <header class="app-header">
    <div class="header-inner">
      <!-- Logo -->
      <div class="logo" @click="router.push('/')">
        <span class="logo-icon material-symbols-outlined">pets</span>
        <span class="logo-text">PetMate</span>
      </div>

      <!-- 桌面导航 -->
      <nav v-if="isDesktop" class="desktop-nav">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: route.path === item.to }"
        >
          {{ item.label }}
        </router-link>
      </nav>

      <!-- 右侧操作 -->
      <div class="header-actions">
        <!-- 鱼干余额 -->
        <router-link
          v-if="isLoggedIn"
          to="/task-center"
          class="points-chip magnetic"
          aria-label="鱼干余额"
        >
          <span class="points-icon">🐟</span>
          <span class="points-num">{{ pointsStore.balance }}</span>
        </router-link>

        <!-- 购物车 -->
        <router-link to="/cart" class="action-btn cart-action">
          <span class="material-symbols-outlined">shopping_cart</span>
          <span v-if="showCartBadge" class="header-badge">{{ cartStore.totalCount > 99 ? '99+' : cartStore.totalCount }}</span>
        </router-link>

        <!-- 用户站内信（我的消息）铃铛 -->
        <UserNotificationBell v-if="isLoggedIn" />

        <!-- 用户:点击头像跳转到个人中心,悬停/聚焦浮出"退出登录" -->
        <div
          v-if="isLoggedIn"
          class="user-area"
          @mouseenter="onPopoverEnter"
          @mouseleave="onPopoverLeave"
        >
          <button
            type="button"
            class="avatar-btn"
            aria-label="个人中心"
            @click="goToProfile"
            @focus="onPopoverEnter"
            @blur="onPopoverLeave"
          >
            <el-avatar :size="36" :src="user?.avatar || undefined">
              <span class="material-symbols-outlined avatar-fallback">person</span>
            </el-avatar>
          </button>

          <transition name="user-popover">
            <div
              v-show="popoverVisible"
              class="user-popover"
              role="menu"
              @click.stop
            >
              <div class="popover-card">
                <router-link to="/user" class="popover-item" @click="popoverVisible = false">
                  <span class="material-symbols-outlined">person</span>
                  <span>个人中心</span>
                </router-link>
                <router-link to="/order/list" class="popover-item" @click="popoverVisible = false">
                  <span class="material-symbols-outlined">receipt_long</span>
                  <span>我的订单</span>
                </router-link>
                <router-link to="/support" class="popover-item" @click="popoverVisible = false">
                  <span class="material-symbols-outlined">support_agent</span>
                  <span>联系客服</span>
                </router-link>
                <button
                  type="button"
                  class="popover-logout"
                  @click="handleLogout"
                >
                  <span class="material-symbols-outlined">logout</span>
                  <span>退出登录</span>
                </button>
              </div>
            </div>
          </transition>
        </div>
        <el-button v-else type="primary" size="small" round @click="router.push('/login')">
          登录
        </el-button>

        <!-- 移动端汉堡菜单 -->
        <button v-if="isMobile" class="menu-btn" @click="toggleMenu">
          <span class="material-symbols-outlined">{{ showMobileMenu ? 'close' : 'menu' }}</span>
        </button>
      </div>
    </div>

    <!-- 移动端菜单 -->
    <transition name="slide-down">
      <div v-if="isMobile && showMobileMenu" class="mobile-menu">
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="mobile-nav-item"
          :class="{ active: route.path === item.to }"
          @click="showMobileMenu = false"
        >
          <span class="material-symbols-outlined">{{
            item.to === '/' ? 'home' : item.to === '/category' ? 'category' : item.to === '/community' ? 'forum' : item.to === '/knowledge' ? 'menu_book' : item.to === '/adoption' ? 'volunteer_activism' : 'psychiatry'
          }}</span>
          <span>{{ item.label }}</span>
        </router-link>
        <router-link
          to="/task-center"
          class="mobile-nav-item"
          :class="{ active: route.path === '/task-center' }"
          @click="showMobileMenu = false"
        >
          <span class="points-emoji">🐟</span>
          <span>任务中心</span>
        </router-link>
        <router-link
          to="/user/messages"
          class="mobile-nav-item"
          :class="{ active: route.path === '/user/messages' }"
          @click="showMobileMenu = false"
        >
          <span class="material-symbols-outlined">notifications</span>
          <span>我的消息</span>
        </router-link>
        <router-link
          to="/support"
          class="mobile-nav-item"
          :class="{ active: route.path === '/support' }"
          @click="showMobileMenu = false"
        >
          <span class="material-symbols-outlined">support_agent</span>
          <span>联系客服</span>
        </router-link>
        <div v-if="isLoggedIn" class="mobile-nav-item" @click="handleLogout">
          <span class="material-symbols-outlined">logout</span>
          <span>退出登录</span>
        </div>
      </div>
    </transition>
  </header>
</template>

<style lang="scss" scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: $z-index-header;
  background: $color-surface-container-lowest;
  border-bottom: 1px solid rgba($color-brand, 0.14);
  backdrop-filter: blur(10px);
}

.header-inner {
  @include container-max;
  @include flex-between;
  height: 64px;
  padding: 0 $width-gutter-mobile;
  gap: $spacing-md;

  @include desktop {
    padding: 0 $width-gutter-desktop;
  }
}

.logo {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}

.logo-icon {
  font-size: 32px;
  color: $color-brand;
  font-variation-settings: 'FILL' 1, 'wght' 600;
}

.logo-text {
  font-family: $font-family-headline;
  font-weight: $font-weight-extrabold;
  font-size: $font-size-headline-md;
  color: $color-text-primary;
  letter-spacing: -0.02em;
}

.desktop-nav {
  display: flex;
  gap: $spacing-lg;
  flex: 1;
  margin-left: $spacing-2xl;
}

.nav-item {
  position: relative;
  padding: $spacing-xs $spacing-md;
  font-size: $font-size-body-md;
  font-weight: $font-weight-medium;
  color: $color-text-secondary;
  border-radius: $radius-lg;
  transition: all $transition-fast;

  &:hover {
    color: $color-brand;
    background: $color-brand-container;
  }

  &.active {
    color: $color-brand;
    font-weight: $font-weight-semibold;

    &::after {
      content: '';
      position: absolute;
      bottom: -6px;
      left: 50%;
      transform: translateX(-50%);
      width: 24px;
      height: 3px;
      background: $color-brand;
      border-radius: $radius-full;
    }
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  flex-shrink: 0;
}

.action-btn {
  position: relative;
  @include flex-center;
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  color: $color-text-secondary;
  transition: all $transition-fast;

  // 与消息铃铛图标保持同一字号，避免顶部图标视觉大小不一
  .material-symbols-outlined {
    font-size: 24px;
  }

  &:hover {
    background: $color-surface-container;
    color: $color-brand;
  }
}

// 鱼干余额胶囊
.points-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 34px;
  padding: 0 12px;
  border-radius: $radius-full;
  background: linear-gradient(135deg, rgba($color-brand, 0.14), rgba($color-warning, 0.14));
  border: 1px solid rgba($color-brand, 0.22);
  color: $color-brand-dark;
  font-weight: $font-weight-semibold;
  font-size: $font-size-label-md;
  transition: transform $transition-fast, box-shadow $transition-fast, background $transition-fast;

.points-icon {
  font-size: 18px;
  line-height: 1;
}

.points-emoji {
  font-size: 20px;
  line-height: 1;
}

  .points-num {
    font-variant-numeric: tabular-nums;
    min-width: 10px;
    text-align: center;
  }

  &:hover {
    transform: translateY(-2px) scale(1.04);
    box-shadow: 0 6px 16px rgba($color-brand, 0.22);
    background: linear-gradient(135deg, rgba($color-brand, 0.2), rgba($color-warning, 0.2));
  }

  &:active {
    transform: translateY(0) scale(0.98);
  }
}

// 磁吸微交互
.magnetic {
  will-change: transform;
}

.user-area {
  position: relative;
  display: flex;
  align-items: center;
}

.avatar-btn {
  width: 40px;
  height: 40px;
  padding: 0;
  background: transparent;
  border: 2px solid transparent;
  border-radius: $radius-full;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color $transition-fast, transform $transition-fast;

  &:hover {
    border-color: $color-brand;
    transform: scale(1.04);
  }

  &:focus-visible {
    outline: none;
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.18);
  }

  .avatar-fallback {
    font-size: 22px;
    line-height: 1;
    color: #fff;
  }
}

.user-popover {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  z-index: $z-index-modal;
}

.user-popover-enter-active,
.user-popover-leave-active {
  transition: opacity $transition-fast, transform $transition-fast;
}

.user-popover-enter-from,
.user-popover-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.popover-card {
  width: 240px;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-lg;
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  position: relative;

  // 小三角
  &::before,
  &::after {
    content: '';
    position: absolute;
    top: -7px;
    right: 16px;
    width: 12px;
    height: 12px;
    background: $color-surface-container-lowest;
    transform: rotate(45deg);
    box-shadow: -1px -1px 0 rgba(0, 0, 0, 0.04);
  }

  &::after {
    background: transparent;
    box-shadow: none;
    top: -4px;
  }
}

.popover-head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding-bottom: $spacing-sm;
  border-bottom: 1px solid $color-outline-variant;
}

.popover-meta {
  min-width: 0;
  flex: 1;
}

.popover-name {
  font-family: $font-family-headline;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  margin: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popover-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}

.pop-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 1px 8px;
  border-radius: $radius-full;
  background: $color-surface-container-low;
  color: $color-on-surface-variant;
  font-size: $font-size-label-sm;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

.popover-sub {
  margin: 4px 0 0;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

.popover-logout {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 10px $spacing-md;
  background: rgba($color-danger, 0.08);
  color: $color-danger;
  border: none;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  &:hover {
    background: rgba($color-danger, 0.16);
    transform: translateY(-1px);
  }

  &:focus-visible {
    outline: none;
    box-shadow: 0 0 0 3px rgba($color-danger, 0.18);
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.popover-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 10px $spacing-md;
  color: $color-text-primary;
  border-radius: $radius-default;
  text-decoration: none;
  font-size: $font-size-body-md;
  font-weight: $font-weight-medium;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container-low;
    color: $color-brand;
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.menu-btn {
  @include flex-center;
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  color: $color-text-primary;
}

.mobile-menu {
  position: absolute;
  top: 64px;
  left: 0;
  right: 0;
  background: $color-surface-container-lowest;
  border-bottom: 1px solid $color-surface-container;
  padding: $spacing-md;
  box-shadow: $shadow-lg;
  z-index: $z-index-drawer;
}

.mobile-nav-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  font-size: $font-size-body-md;
  color: $color-text-secondary;
  border-radius: $radius-default;
  cursor: pointer;
  min-height: 44px;

  &:hover,
  &.active {
    background: $color-brand-container;
    color: $color-brand-dark;
  }
}

.slide-down-enter-active,
.slide-down-leave-active {
  transition: all $transition-base;
  max-height: 500px;
}

.slide-down-enter-from,
.slide-down-leave-to {
  opacity: 0;
  max-height: 0;
}
</style>