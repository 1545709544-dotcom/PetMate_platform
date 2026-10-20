<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'

const router = useRouter()
const visible = ref(false)
const list = ref<any[]>([])
const unread = ref(0)
const loading = ref(false)
let timer: any = null

const typeIcon: Record<string, string> = {
  order: 'shopping_cart_checkout',
  review: 'reviews',
  refund: 'assignment_return',
  system: 'campaign',
}
const typeText: Record<string, string> = {
  order: '订单',
  review: '评价',
  refund: '退款',
  system: '系统',
}

async function refreshCount() {
  try {
    const r = await merchantApi.notificationUnreadCount()
    unread.value = r.count || 0
  } catch {
    /* 静默 */
  }
}

async function open() {
  visible.value = true
  await load()
}

async function load() {
  loading.value = true
  try {
    const r = await merchantApi.notifications({ page: 1, size: 30 })
    list.value = r.list || []
    unread.value = r.unreadCount || 0
  } catch {
    ElMessage.error('通知加载失败')
  } finally {
    loading.value = false
  }
}

async function markRead(item: any) {
  if (item.isRead) return
  try {
    await merchantApi.readNotification(item.id)
    item.isRead = 1
    unread.value = Math.max(0, unread.value - 1)
  } catch {
    /* ignore */
  }
}

async function readAll() {
  try {
    await merchantApi.readAllNotifications()
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
    <button class="notif-bell" @click="open" title="消息通知">
      <span class="material-symbols-outlined">notifications</span>
      <span v-if="unread > 0" class="header-badge">{{ unread > 99 ? '99+' : unread }}</span>
    </button>

    <el-drawer v-model="visible" title="消息通知" direction="rtl" size="380px" append-to-body modal-class="notif-modal">
      <template #header>
        <div class="drawer-head">
          <span>消息通知</span>
          <el-button v-if="unread > 0" link type="primary" @click="readAll">全部已读</el-button>
        </div>
      </template>

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

.notif-bell {
  position: relative;
  width: 40px;
  height: 40px;
  border: none;
  background: transparent;
  border-radius: $radius-lg;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-text-secondary;
  transition: background-color 150ms ease, color 150ms ease;

  .material-symbols-outlined {
    font-size: 22px;
  }
  &:hover {
    background: $color-surface-container-low;
    color: $color-brand;
  }
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-weight: $font-weight-bold;
}

.notif-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 200px;
}

.notif-empty {
  text-align: center;
  color: $color-text-tertiary;
  padding: 48px 0;
  .material-symbols-outlined {
    font-size: 40px;
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
  &.t-order {
    background: $color-brand;
  }
  &.t-review {
    background: #7c5cff;
  }
  &.t-refund {
    background: $color-emergency;
  }
  &.t-system {
    background: $color-text-secondary;
  }
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
