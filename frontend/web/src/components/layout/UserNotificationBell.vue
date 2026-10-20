<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/modules/user'

const router = useRouter()
const visible = ref(false)
const list = ref<any[]>([])
const unread = ref(0)
const loading = ref(false)
let timer: any = null

const typeIcon: Record<string, string> = {
  warning: 'gpp_maybe',
  system: 'campaign',
  order: 'shopping_bag',
  refund: 'assignment_return',
  vip: 'workspace_premium',
  social: 'person_add',
  care: 'pets',
}
const typeText: Record<string, string> = {
  warning: '违规警告',
  system: '系统',
  order: '订单',
  refund: '售后',
  vip: '会员',
  social: '互动',
  care: '关怀',
}

function goAll() {
  visible.value = false
  router.push('/user/messages')
}

async function refreshCount() {
  try {
    const r = await userApi.notificationUnreadCount()
    unread.value = r.count || 0
  } catch {
    /* 静默：未登录或接口抖动时不影响主流程 */
  }
}

async function open() {
  visible.value = true
  await load()
}

async function load() {
  loading.value = true
  try {
    const r = await userApi.notifications({ page: 1, size: 30 })
    list.value = r.list || []
    unread.value = r.unreadCount || 0
  } catch {
    ElMessage.error('消息加载失败')
  } finally {
    loading.value = false
  }
}

async function markRead(item: any) {
  if (item.isRead) return
  try {
    await userApi.readNotification(item.id)
    item.isRead = 1
    unread.value = Math.max(0, unread.value - 1)
  } catch {
    /* ignore */
  }
}

async function readAll() {
  try {
    await userApi.readAllNotifications()
    list.value.forEach((i) => (i.isRead = 1))
    unread.value = 0
    ElMessage.success('已全部标为已读')
  } catch {
    ElMessage.error('操作失败')
  }
}

function fmtTime(t: string) {
  if (!t) return ''
  const d = new Date(t.replace(' ', 'T'))
  const now = new Date()
  const diff = (now.getTime() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  return t.slice(5, 16)
}

onMounted(() => {
  refreshCount()
  timer = setInterval(refreshCount, 30000)
})
onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<template>
  <div class="notif">
    <button class="notif-bell" @click="open" title="我的消息">
      <span class="material-symbols-outlined">notifications</span>
      <span v-if="unread > 0" class="header-badge">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <el-drawer
      v-model="visible"
      :with-header="false"
      direction="rtl"
      size="380px"
      :z-index="9999"
      append-to-body
      modal-class="notif-modal"
      class="notif-drawer"
    >
      <div class="drawer-head">
        <span class="drawer-title">我的消息</span>
        <div class="drawer-actions">
          <el-button link type="primary" @click="goAll">查看全部</el-button>
          <el-button v-if="unread > 0" link type="primary" @click="readAll">全部已读</el-button>
        </div>
      </div>

      <el-divider class="drawer-divider" />

      <div v-loading="loading" class="notif-list">
        <div v-if="!loading && list.length === 0" class="notif-empty">
          <span class="material-symbols-outlined">check_circle</span>
          <p>暂无消息</p>
        </div>
        <div
          v-for="item in list"
          :key="item.id"
          class="notif-item"
          :class="{ unread: !item.isRead }"
          @click="markRead(item)"
        >
          <div class="notif-icon" :class="'t-' + item.type">
            <span class="material-symbols-outlined">{{ typeIcon[item.type] || 'campaign' }}</span>
          </div>
          <div class="notif-body">
            <div class="notif-top">
              <span class="notif-type">{{ typeText[item.type] || '系统' }}</span>
              <span class="notif-time">{{ fmtTime(item.createdAt) }}</span>
            </div>
            <p class="notif-title">{{ item.title }}</p>
            <p v-if="item.content" class="notif-content">{{ item.content }}</p>
          </div>
          <span v-if="!item.isRead" class="dot" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

/* 铃铛按钮 —— 与购物车 .action-btn(40×40) 完全一致 */
.notif-bell {
  position: relative;
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: $radius-full;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-text-secondary;
  transition: background-color 150ms ease, color 150ms ease;

  /* 与购物车 icon 同尺寸 */
  .material-symbols-outlined {
    font-size: 24px;
  }
  &:hover {
    background: $color-surface-container;
    color: $color-brand;
  }
}

/* ====== 抽屉内布局 ====== */
.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 0 0;
}

.drawer-title {
  font-size: $font-size-body-md;
  font-weight: $font-weight-bold;
  color: $color-text-primary;
}

.drawer-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.drawer-divider {
  margin: 8px 0;
}

.notif-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 160px;
  overflow-y: auto;
  padding-right: 4px;
}

.notif-empty {
  text-align: center;
  color: $color-text-tertiary;
  padding: 40px 0;
  .material-symbols-outlined {
    font-size: 36px;
  }
  p {
    margin: 8px 0 0;
  }
}

.notif-item {
  position: relative;
  display: flex;
  gap: 12px;
  padding: 12px;
  border-radius: $radius-lg;
  background: $color-surface-container-lowest;
  cursor: pointer;
  transition: background-color 150ms ease;
  &:hover {
    background: $color-surface-container-low;
  }
  &.unread {
    background: rgba($color-brand, 0.06);
  }
}

.notif-icon {
  width: 38px;
  height: 38px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  &.t-warning { background: $color-emergency; }
  &.t-system  { background: $color-text-secondary; }
  &.t-order   { background: #ff7a32; }
  &.t-refund  { background: #f59f00; }
  &.t-vip     { background: #c79a3a; }
  &.t-social  { background: #4263eb; }
  &.t-care    { background: #2f9e44; }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.notif-body {
  min-width: 0;
  flex: 1;
}

.notif-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}

.notif-type {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  color: $color-text-secondary;
}

.notif-time {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}

.notif-title {
  margin: 0;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-text-primary;
}

.notif-content {
  margin: 2px 0 0;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  line-height: 1.4;
  word-break: break-all;
}

.dot {
  position: absolute;
  top: 14px;
  right: 14px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: $color-brand;
}
</style>
