<script setup lang="ts">
import MerchantSidebar from '@/components/layout/MerchantSidebar.vue'
import MerchantNotificationBell from '@/components/layout/MerchantNotificationBell.vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const roleLabel: Record<string, string> = {
  OWNER: '店主',
  OPERATOR: '运营',
  CS: '客服',
  WAREHOUSE: '仓管',
}

function goHome() {
  router.push('/')
}

async function logout() {
  // 真正退出:清 token + user + role,后端 session 注销,然后回商家登录页
  await userStore.logout().catch(() => {})
  ElMessage.success('已退出商家中心')
  router.push('/merchant/landing')
}
</script>

<template>
  <div class="merchant-layout">
    <MerchantSidebar />
    <div class="merchant-main">
      <header class="merchant-header no-vw">
        <div class="breadcrumb">
          <span class="material-symbols-outlined">home</span>
          <span class="sep">/</span>
          <span class="current">{{ route.meta.title || '商家中心' }}</span>
        </div>
        <div class="header-actions">
          <span v-if="userStore.isMerchant && userStore.subRole" class="role-badge" :class="'rb-' + userStore.subRole">
            {{ roleLabel[userStore.subRole] || userStore.subRole }}
          </span>
          <MerchantNotificationBell />
          <el-button text @click="goHome">
            <span class="material-symbols-outlined">public</span>
            访问前台
          </el-button>
          <el-button text @click="logout">
            <span class="material-symbols-outlined">logout</span>
            退出
          </el-button>
        </div>
      </header>

      <main class="merchant-content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <!-- 单一包裹层：保证 <Transition> 始终有单一元素根 -->
            <div class="page-wrapper" :key="$route.path">
              <component :is="Component" />
            </div>
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.merchant-layout {
  display: flex;
  min-height: 100vh;
  background: $color-background;
}

.merchant-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0; // 防止 flex 子项溢出
}

.merchant-header {
  height: 56px;
  padding: 0 $spacing-lg;
  background: $color-surface-container-lowest;
  border-bottom: 1px solid $color-surface-container;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: $z-index-header - 1;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  color: $color-text-secondary;
  font-size: $font-size-body-md;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
  }

  .sep {
    color: $color-text-tertiary;
  }

  .current {
    color: $color-text-primary;
    font-weight: $font-weight-semibold;
  }
}

.header-actions {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.role-badge {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  padding: 4px 10px;
  border-radius: 999px;
  line-height: 1;

  &.rb-OWNER {
    background: rgba($color-brand, 0.12);
    color: $color-brand;
  }
  &.rb-OPERATOR {
    background: rgba(#7c5cff, 0.12);
    color: #7c5cff;
  }
  &.rb-CS {
    background: rgba(#0a9b8e, 0.12);
    color: #0a9b8e;
  }
  &.rb-WAREHOUSE {
    background: rgba($color-text-secondary, 0.12);
    color: $color-text-secondary;
  }
}

.merchant-content {
  flex: 1;
  padding: $spacing-lg;
  overflow-x: auto;
}
</style>