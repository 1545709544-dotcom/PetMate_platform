<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/modules/user'

interface MsgItem {
  id: number
  type: string
  title: string
  content: string | null
  relatedId: string | null
  isRead: number
  createdAt: string
}

const router = useRouter()

const list = ref<MsgItem[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const activeType = ref('')
const unreadOnly = ref(false)
const unreadCount = ref(0)

const tabs = [
  { value: '', label: '全部' },
  { value: 'system', label: '系统' },
  { value: 'order', label: '订单' },
  { value: 'refund', label: '售后' },
  { value: 'vip', label: '会员' },
  { value: 'social', label: '互动' },
  { value: 'warning', label: '违规警告' },
]

const typeIcon: Record<string, string> = {
  warning: 'gpp_maybe',
  system: 'campaign',
  order: 'shopping_bag',
  refund: 'assignment_return',
  vip: 'workspace_premium',
  social: 'person_add',
}
const typeText: Record<string, string> = {
  warning: '违规警告',
  system: '系统',
  order: '订单',
  refund: '售后',
  vip: '会员',
  social: '互动',
}

const isOrderLike = (t: string) => t === 'order' || t === 'refund'

function fmtTime(t?: string): string {
  if (!t) return ''
  const d = new Date(t.replace(' ', 'T'))
  const now = new Date()
  const diff = (now.getTime() - d.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  return t.slice(0, 16)
}

async function load(resetPage = true) {
  if (loading.value) return
  if (resetPage) page.value = 1
  loading.value = true
  try {
    const r = await userApi.notifications({
      page: page.value,
      size,
      type: activeType.value || undefined,
      unreadOnly: unreadOnly.value,
    })
    list.value = r.list || []
    total.value = r.total ?? 0
    unreadCount.value = r.unreadCount ?? 0
  } catch (e) {
    console.error('[MessageCenter] load failed:', e)
    ElMessage.error('消息加载失败')
  } finally {
    loading.value = false
  }
}

function onTab(v: string) {
  activeType.value = v
  load()
}

function onUnreadToggle() {
  load()
}

function onPageChange(p: number) {
  page.value = p
  load(false)
}

async function markRead(item: MsgItem) {
  if (item.isRead) {
    maybeNav(item)
    return
  }
  try {
    await userApi.readNotification(item.id)
    item.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  } catch {
    /* ignore */
  }
  maybeNav(item)
}

function maybeNav(item: MsgItem) {
  if (!item.relatedId) {
    // 无关联业务 id 的社交通知兜底到社区
    if (item.type === 'social') router.push('/community')
    return
  }
  if (isOrderLike(item.type)) {
    router.push(`/order/detail/${item.relatedId}`)
  } else if (item.type === 'social') {
    // relatedId 形如 post:<id> / user:<id>（后端社交通知写入）
    if (item.relatedId.startsWith('post:')) router.push(`/post/${item.relatedId.slice(5)}`)
    else if (item.relatedId.startsWith('user:')) router.push(`/user/home/${item.relatedId.slice(5)}`)
    else router.push('/community')
  }
}

async function readAll() {
  try {
    await userApi.readAllNotifications()
    list.value.forEach((i) => (i.isRead = 1))
    unreadCount.value = 0
    ElMessage.success('已全部标为已读')
  } catch {
    ElMessage.error('操作失败')
  }
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size)))

onMounted(() => load())
</script>

<template>
  <div class="msg-center">
    <div class="mc-container">
      <!-- 顶部 -->
      <div class="mc-topbar">
        <button class="mc-back" @click="router.back()" aria-label="返回">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <h1 class="mc-title">我的消息</h1>
        <el-button v-if="unreadCount > 0" link type="primary" @click="readAll">全部已读</el-button>
      </div>

      <p class="mc-sub" v-if="unreadCount > 0">
        你有 <b>{{ unreadCount }}</b> 条未读消息
      </p>

      <!-- 分类筛选 -->
      <div class="mc-tabs">
        <button
          v-for="t in tabs"
          :key="t.value"
          class="mc-tab"
          :class="{ active: activeType === t.value }"
          @click="onTab(t.value)"
        >
          {{ t.label }}
        </button>
        <label class="mc-switch">
          <input type="checkbox" v-model="unreadOnly" @change="onUnreadToggle" />
          <span>仅看未读</span>
        </label>
      </div>

      <!-- 列表 -->
      <div v-loading="loading" class="mc-list">
        <div v-if="!loading && list.length === 0" class="mc-empty">
          <span class="material-symbols-outlined">check_circle</span>
          <p>暂无消息</p>
        </div>

        <div
          v-for="item in list"
          :key="item.id"
          class="mc-item"
          :class="{ unread: !item.isRead, clickable: isOrderLike(item.type) && item.relatedId }"
          @click="markRead(item)"
        >
          <div class="mc-icon" :class="'t-' + item.type">
            <span class="material-symbols-outlined">{{ typeIcon[item.type] || 'campaign' }}</span>
          </div>
          <div class="mc-body">
            <div class="mc-top">
              <span class="mc-tag" :class="'t-' + item.type">{{ typeText[item.type] || '系统' }}</span>
              <span class="mc-time">{{ fmtTime(item.createdAt) }}</span>
            </div>
            <p class="mc-title">{{ item.title }}</p>
            <p v-if="item.content" class="mc-content">{{ item.content }}</p>
          </div>
          <span v-if="!item.isRead" class="mc-dot" />
          <span
            v-if="isOrderLike(item.type) && item.relatedId"
            class="material-symbols-outlined mc-arrow"
            >chevron_right</span
          >
        </div>
      </div>

      <!-- 分页 -->
      <div v-if="totalPages > 1" class="mc-pager">
        <button class="mc-page-btn" :disabled="page <= 1" @click="onPageChange(page - 1)">上一页</button>
        <span class="mc-page-info">{{ page }} / {{ totalPages }}</span>
        <button
          class="mc-page-btn"
          :disabled="page >= totalPages"
          @click="onPageChange(page + 1)"
        >
          下一页
        </button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.msg-center {
  min-height: calc(100vh - 64px);
  background: $color-surface-container-low;
  padding: $spacing-xl 0;
}

.mc-container {
  max-width: 760px;
  margin: 0 auto;
  padding: 0 $spacing-md;
}

.mc-topbar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.mc-back {
  width: 40px;
  height: 40px;
  border: none;
  background: $color-surface-container;
  border-radius: $radius-full;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-text-primary;
  transition: background 150ms ease;
  &:hover {
    background: $color-surface-container-high;
  }
}

.mc-title {
  flex: 1;
  margin: 0;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-text-primary;
}

.mc-sub {
  margin: 0 0 $spacing-md;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
  b {
    color: $color-brand;
  }
}

.mc-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  align-items: center;
  margin-bottom: $spacing-md;
}

.mc-tab {
  padding: 6px 14px;
  border: 1px solid $color-outline;
  background: $color-surface-container-lowest;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  cursor: pointer;
  transition: all 150ms ease;
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
  &.active {
    background: $color-brand;
    border-color: $color-brand;
    color: #fff;
  }
}

.mc-switch {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  cursor: pointer;
  input {
    width: 16px;
    height: 16px;
    accent-color: $color-brand;
  }
}

.mc-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  min-height: 200px;
}

.mc-empty {
  text-align: center;
  color: $color-text-tertiary;
  padding: 60px 0;
  .material-symbols-outlined {
    font-size: 44px;
  }
  p {
    margin: 8px 0 0;
  }
}

.mc-item {
  display: flex;
  gap: $spacing-md;
  padding: $spacing-md;
  border-radius: $radius-lg;
  background: $color-surface-container-lowest;
  position: relative;
  transition: background 150ms ease, transform 150ms ease;
  &:hover {
    background: $color-surface-container-low;
  }
  &.unread {
    background: rgba($color-brand, 0.06);
  }
  &.clickable {
    cursor: pointer;
    &:hover {
      transform: translateY(-2px);
      box-shadow: $shadow-sm;
    }
  }
}

.mc-icon {
  width: 42px;
  height: 42px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
  &.t-warning {
    background: $color-emergency;
  }
  &.t-system {
    background: $color-text-secondary;
  }
  &.t-order {
    background: #ff7a32;
  }
  &.t-refund {
    background: #f59f00;
  }
  &.t-vip {
    background: #c79a3a;
  }
  &.t-social {
    background: #4263eb;
  }
  .material-symbols-outlined {
    font-size: 22px;
  }
}

.mc-body {
  min-width: 0;
  flex: 1;
}

.mc-top {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: 2px;
}

.mc-tag {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  padding: 1px 8px;
  border-radius: $radius-full;
  background: $color-surface-container;
  color: $color-text-secondary;
  &.t-warning {
    background: rgba($color-emergency, 0.12);
    color: $color-emergency;
  }
  &.t-order {
    background: rgba(#ff7a32, 0.12);
    color: #ff7a32;
  }
  &.t-refund {
    background: rgba(#f59f00, 0.14);
    color: #c97a00;
  }
  &.t-vip {
    background: rgba(#c79a3a, 0.16);
    color: #a87f24;
  }
  &.t-social {
    background: rgba(#4263eb, 0.12);
    color: #4263eb;
  }
}

.mc-time {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}

.mc-title {
  margin: 2px 0 0;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-text-primary;
}

.mc-content {
  margin: 4px 0 0;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  line-height: 1.5;
  word-break: break-all;
}

.mc-dot {
  position: absolute;
  top: 16px;
  right: 16px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: $color-brand;
}

.mc-arrow {
  align-self: center;
  color: $color-text-tertiary;
  font-size: 20px;
}

.mc-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-md;
  margin-top: $spacing-lg;
}

.mc-page-btn {
  padding: 8px 18px;
  border: 1px solid $color-outline;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  cursor: pointer;
  color: $color-text-primary;
  font-size: $font-size-label-md;
  transition: all 150ms ease;
  &:hover:not(:disabled) {
    border-color: $color-brand;
    color: $color-brand;
  }
  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.mc-page-info {
  font-size: $font-size-label-md;
  color: $color-text-secondary;
}
</style>
