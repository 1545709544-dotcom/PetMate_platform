<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

interface MenuItem {
  name: string
  label: string
  icon: string
  iconFilled?: boolean
  to: string
  // 哪些路由名也算作"激活"此菜单
  matchNames?: string[]
  // 需要的商家子角色（为空=所有角色可见）
  requiredRoles?: string[]
}

// 功能分类分组（经营概览 / 交易履约 / 商品服务 / 客户运营 / 门店团队）
interface MenuGroup {
  title: string
  items: MenuItem[]
}

const menuGroups: MenuGroup[] = [
  {
    title: '经营概览',
    items: [
      {
        name: 'Dashboard',
        label: '工作台',
        icon: 'dashboard',
        iconFilled: true,
        to: '/merchant/dashboard',
        matchNames: ['MerchantDashboard'],
      },
      {
        name: 'Analytics',
        label: '数据统计',
        icon: 'bar_chart',
        to: '/merchant/data/sales',
        requiredRoles: ['OPERATOR'],
        matchNames: ['MerchantSales', 'MerchantVisitor', 'MerchantConvert'],
      },
    ],
  },
  {
    title: '交易履约',
    items: [
      {
        name: 'Orders',
        label: '订单管理',
        icon: 'shopping_cart',
        to: '/merchant/order',
        matchNames: ['MerchantOrder', 'MerchantOrderDetail'],
      },
      {
        name: 'Appointments',
        label: '预约管理',
        icon: 'event_available',
        to: '/merchant/appointments',
        requiredRoles: ['OPERATOR'],
        matchNames: ['MerchantAppointments'],
      },
      {
        name: 'Refunds',
        label: '售后退款',
        icon: 'assignment_return',
        to: '/merchant/refunds',
        requiredRoles: ['OPERATOR'],
        matchNames: ['MerchantRefunds'],
      },
      {
        name: 'Finance',
        label: '财务结算',
        icon: 'account_balance_wallet',
        to: '/merchant/finance',
        requiredRoles: ['OWNER'],
        matchNames: ['MerchantFinance'],
      },
    ],
  },
  {
    title: '商品服务',
    items: [
      {
        name: 'Products',
        label: '商品与服务管理',
        icon: 'inventory_2',
        to: '/merchant/product/list',
        requiredRoles: ['OPERATOR'],
        matchNames: ['MerchantProductList', 'MerchantProductEdit', 'MerchantServiceEdit', 'MerchantSchedule'],
      },
    ],
  },
  {
    title: '客户运营',
    items: [
      {
        name: 'Reviews',
        label: '评价管理',
        icon: 'reviews',
        to: '/merchant/review',
        requiredRoles: ['CS'],
        matchNames: ['MerchantReview'],
      },
      {
        name: 'Customers',
        label: '客户管理',
        icon: 'group',
        to: '/merchant/customers',
        requiredRoles: ['CS'],
        matchNames: ['MerchantCustomers'],
      },
    ],
  },
  {
    title: '门店团队',
    items: [
      {
        name: 'StoreInfo',
        label: '门店信息',
        icon: 'storefront',
        to: '/merchant/store/info',
        requiredRoles: ['OWNER'],
        matchNames: ['MerchantStoreInfo'],
      },
      {
        name: 'Staff',
        label: '员工管理',
        icon: 'badge',
        to: '/merchant/staff',
        requiredRoles: ['OWNER'],
        matchNames: ['MerchantStaff'],
      },
    ],
  },
]

/** 单个菜单项是否对当前商家子角色可见（店主可见全部） */
function canView(m: MenuItem): boolean {
  if (!m.requiredRoles || m.requiredRoles.length === 0) return true
  if (userStore.isOwner) return true
  return m.requiredRoles.some((r) => {
    if (r === 'OPERATOR') return userStore.isOperator
    if (r === 'CS') return userStore.isCs
    if (r === 'WAREHOUSE') return userStore.isWarehouse
    return false
  })
}

/** 扁平化所有可见菜单项（用于激活态匹配） */
const visibleItems = computed(() => menuGroups.flatMap((g) => g.items).filter(canView))

/** 按分类分组的可见菜单（空分类自动隐藏） */
const visibleGroups = computed(() =>
  menuGroups
    .map((g) => ({ title: g.title, items: g.items.filter(canView) }))
    .filter((g) => g.items.length > 0),
)

const activeName = computed(() => {
  const current = route.name as string | undefined
  if (!current) return ''
  const match = visibleItems.value.find(
    (m) => m.name === current || (m.matchNames?.includes(current) ?? false),
  )
  return match?.name ?? ''
})

function go(item: MenuItem) {
  router.push(item.to)
}

function quickPublish() {
  if (!userStore.isOperator) {
    ElMessage.warning('当前角色无权限发布商品/服务')
    return
  }
  router.push('/merchant/service/edit')
}
</script>

<template>
  <aside class="merchant-sidebar no-vw">
    <!-- 品牌头部 -->
    <div class="brand">
      <div class="brand-logo">
        <span class="material-symbols-outlined">pets</span>
      </div>
      <div class="brand-text">
        <h1 class="brand-title">
          <span class="brand-title-row">PetMate</span>
          <span class="brand-title-row">Pro</span>
        </h1>
        <p class="brand-subtitle">商家后台中心</p>
      </div>
    </div>

    <!-- 菜单导航（按功能分类分组） -->
    <nav class="menu">
      <div v-for="group in visibleGroups" :key="group.title" class="menu-group">
        <p class="menu-group-title">{{ group.title }}</p>
        <a
          v-for="item in group.items"
          :key="item.name"
          class="menu-item"
          :class="{ active: activeName === item.name }"
          href="javascript:;"
          @click.prevent="go(item)"
        >
          <span
            class="material-symbols-outlined menu-icon"
            :class="{ 'icon-filled': item.iconFilled }"
          >
            {{ item.icon }}
          </span>
          <span class="menu-label">{{ item.label }}</span>
        </a>
      </div>
    </nav>

    <!-- 底部 CTA -->
    <button type="button" class="publish-btn" @click="quickPublish">
      <span class="material-symbols-outlined">add</span>
      <span>发布新服务</span>
    </button>
  </aside>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.merchant-sidebar {
  position: sticky;
  top: 0;
  left: 0;
  flex-shrink: 0;
  width: 256px; // 16rem = w-64
  height: 100vh;
  background: $color-surface-container-lowest;
  box-shadow: 0 1px 2px rgba(22, 29, 31, 0.05);
  display: flex;
  flex-direction: column;
  padding: 24px 16px;
  z-index: 50;
  overflow-y: auto;
}

// ====== 品牌头部 ======
.brand {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 4px 8px 40px;
}

.brand-logo {
  width: 52px;
  height: 52px;
  background: $color-brand; // 实色橙,UI 图同款
  border-radius: $radius-lg; // 圆角方块
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 6px 16px rgba($color-brand, 0.28);

  .material-symbols-outlined {
    font-size: 30px;
    color: #ffffff;
    font-weight: $font-weight-bold;
    font-variation-settings: 'FILL' 1, 'wght' 700;
  }
}

.brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.brand-title {
  font-family: $font-family-headline;
  font-size: 22px;
  font-weight: $font-weight-extrabold;
  line-height: 1.05;
  color: $color-brand-dark; // 深橙,UI 图大标题色
  white-space: nowrap;
  margin: 0;
  letter-spacing: -0.02em;
}

.brand-title-row {
  display: block;
}

.brand-subtitle {
  font-family: $font-family-body;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 4px 0 0;
  white-space: nowrap;
  letter-spacing: 0.02em;
}

// ====== 菜单（按功能分类分组）======
.menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.menu-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.menu-group-title {
  margin: 0 0 6px;
  padding: 0 16px;
  font-family: $font-family-body;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.08em;
  color: $color-on-surface-variant;
  white-space: nowrap;
}

.menu-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  border-radius: $radius-xl;
  color: $color-text-secondary;
  font-family: $font-family-body;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  letter-spacing: 0.01em;
  text-decoration: none;
  cursor: pointer;
  transition: background-color 150ms ease, color 150ms ease;
}

.menu-item:hover {
  background: $color-surface-container-low;
  color: $color-text-primary;
}

.menu-item.active {
  background: $color-surface-container-low;
  color: $color-brand;
  font-weight: $font-weight-bold;
}

.menu-item.active::after {
  content: '';
  position: absolute;
  right: -16px;
  top: 0;
  bottom: 0;
  width: 4px;
  background-color: $color-brand;
  border-radius: 0 2px 2px 0;
}

.menu-icon {
  font-size: 22px;
  flex-shrink: 0;
}

.menu-icon.icon-filled {
  font-variation-settings: 'FILL' 1;
}

.menu-label {
  white-space: nowrap;
}

// ====== 底部 CTA ======
.publish-btn {
  margin-top: auto;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 12px 16px;
  background: $color-brand;
  color: $color-on-brand;
  font-family: $font-family-body;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  letter-spacing: 0.01em;
  border: none;
  border-radius: $radius-xl;
  cursor: pointer;
  box-shadow: 0 8px 16px rgba($color-brand, 0.2);
  transition: transform 150ms ease;

  &:hover {
    background: $color-brand-dark;
  }

  &:active {
    transform: scale(0.95);
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}
</style>
