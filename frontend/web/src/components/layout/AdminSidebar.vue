<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()

interface MenuGroup {
  title: string
  items: Array<{ name: string; label: string; icon: string; to: string }>
}

const menuGroups: MenuGroup[] = [
  {
    title: '运营',
    items: [
      { name: 'AdminDashboard', label: '运营仪表盘', icon: 'space_dashboard', to: '/admin/dashboard' },
    ],
  },
  {
    title: '用户',
    items: [
      { name: 'AdminUserList', label: '用户管理', icon: 'people', to: '/admin/user/list' },
      { name: 'AdminMerchantList', label: '商家管理', icon: 'storefront', to: '/admin/merchant/list' },
      { name: 'AdminApplyAudit', label: '入驻申请审核', icon: 'how_to_reg', to: '/admin/merchant/apply/audit' },
    ],
  },
  {
    title: '商品订单',
    items: [
      { name: 'AdminProductList', label: '商品管理', icon: 'inventory_2', to: '/admin/product/list' },
      { name: 'AdminCategory', label: '分类管理', icon: 'category', to: '/admin/product/category' },
      { name: 'AdminOrderList', label: '订单管理', icon: 'receipt_long', to: '/admin/order/list' },
      { name: 'AdminRefundAudit', label: '退款审核', icon: 'currency_exchange', to: '/admin/order/refund' },
    ],
  },
  {
    title: '内容',
    items: [
      { name: 'AdminPostAudit', label: '帖子审核', icon: 'forum', to: '/admin/community/post' },
      { name: 'AdminReport', label: '举报处理', icon: 'flag', to: '/admin/community/report' },
      { name: 'AdminAdoptionAudit', label: '领养审核', icon: 'volunteer_activism', to: '/admin/adoption/audit' },
      { name: 'AdminAnnouncement', label: '公告管理', icon: 'campaign', to: '/admin/content/announcement' },
    ],
  },
  {
    title: '营销',
    items: [
      { name: 'AdminSeckill', label: '秒杀配置', icon: 'flash_on', to: '/admin/marketing/seckill' },
      { name: 'AdminCoupon', label: '优惠券配置', icon: 'local_offer', to: '/admin/marketing/coupon' },
      { name: 'AdminBanner', label: '轮播管理', icon: 'view_carousel', to: '/admin/marketing/banner' },
    ],
  },
  {
    title: '风控',
    items: [
      { name: 'AdminSensitive', label: '敏感词管理', icon: 'block', to: '/admin/risk/sensitive' },
      { name: 'AdminRiskCenter', label: '风控中心', icon: 'shield', to: '/admin/risk/center' },
    ],
  },
  {
    title: '系统',
    items: [
      { name: 'AdminRoleList', label: '角色管理', icon: 'admin_panel_settings', to: '/admin/role/list' },
      { name: 'AdminPermission', label: '权限分配', icon: 'key', to: '/admin/role/permission' },
      { name: 'AdminSystemConfig', label: '系统参数', icon: 'tune', to: '/admin/system/config' },
      { name: 'AdminLog', label: '操作日志', icon: 'history', to: '/admin/system/log' },
    ],
  },
]

const activeMenu = computed(() => route.name as string)
</script>

<template>
  <aside class="admin-sidebar no-vw">
    <div class="brand">
      <span class="brand-icon material-symbols-outlined">admin_panel_settings</span>
      <div class="brand-meta">
        <span class="brand-text">PetMate 后台</span>
        <span class="brand-sub">运营管理</span>
      </div>
    </div>

    <el-menu
      :default-active="activeMenu"
      class="sidebar-menu"
      background-color="transparent"
      text-color="#cfd8dc"
      active-text-color="#ffffff"
    >
      <template v-for="group in menuGroups" :key="group.title">
        <div class="menu-group-title">{{ group.title }}</div>
        <el-menu-item
          v-for="item in group.items"
          :key="item.name"
          :index="item.name"
          @click="$router.push(item.to)"
        >
          <span class="material-symbols-outlined">{{ item.icon }}</span>
          <template #title>{{ item.label }}</template>
        </el-menu-item>
      </template>
    </el-menu>
  </aside>
</template>

<style lang="scss" scoped>
.admin-sidebar {
  width: 240px;
  flex-shrink: 0;
  background: #161d1f; // 后台深色侧栏
  color: $color-on-surface;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow-y: auto;
  position: sticky;
  top: 0;
}

.brand {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-lg $spacing-md;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.brand-icon {
  font-size: 28px;
  color: $color-brand;
  font-variation-settings: 'FILL' 1, 'wght' 600;
}

.brand-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
}

.brand-text {
  font-family: $font-family-headline;
  font-weight: $font-weight-bold;
  font-size: $font-size-body-lg;
  color: #ffffff;
}

.brand-sub {
  font-size: $font-size-label-sm;
  color: rgba(255, 255, 255, 0.5);
}

.sidebar-menu {
  flex: 1;
  border-right: none !important;
  padding: $spacing-sm 0;
}

:deep(.el-menu-item) {
  margin: 2px $spacing-sm;
  border-radius: $radius-default;
  height: 40px;
  line-height: 40px;
  color: #cfd8dc !important;

  &:hover {
    background: rgba(255, 255, 255, 0.06) !important;
    color: #ffffff !important;
  }

  &.is-active {
    background: $color-brand !important;
    color: #ffffff !important;
    font-weight: $font-weight-semibold;
  }

  .material-symbols-outlined {
    font-size: 18px;
    margin-right: $spacing-sm;
    color: inherit;
  }
}

.menu-group-title {
  padding: $spacing-md $spacing-md $spacing-xs;
  font-size: $font-size-label-sm;
  color: rgba(255, 255, 255, 0.4);
  text-transform: uppercase;
  letter-spacing: 0.05em;
  font-weight: $font-weight-semibold;
}
</style>