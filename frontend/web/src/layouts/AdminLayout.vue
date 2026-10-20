<script setup lang="ts">
import AdminSidebar from '@/components/layout/AdminSidebar.vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

function goHome() {
  router.push('/')
}

async function logout() {
  // 真正退出:清 token + user + role,后端 session 注销,然后回管理端登录页
  await userStore.logout().catch(() => {})
  ElMessage.success('已退出后台')
  router.push('/admin/login')
}
</script>

<template>
  <div class="admin-layout">
    <AdminSidebar />
    <div class="admin-main">
      <header class="admin-header no-vw">
        <div class="breadcrumb">
          <span class="material-symbols-outlined">admin_panel_settings</span>
          <span class="sep">/</span>
          <span class="current">{{ route.meta.title || '运营管理' }}</span>
        </div>
        <div class="header-actions">
          <el-button text @click="goHome">
            <span class="material-symbols-outlined">public</span>
            访问前台
          </el-button>
          <el-button text @click="logout">
            <span class="material-symbols-outlined">logout</span>
            退出登录
          </el-button>
        </div>
      </header>

      <main class="admin-content">
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
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: $color-background;
}

.admin-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.admin-header {
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
  gap: $spacing-sm;
}

.admin-content {
  flex: 1;
  padding: $spacing-lg;
  overflow-x: auto;
}
</style>