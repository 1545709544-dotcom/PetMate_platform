<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

// 后端返回 postAuditList 项类型
interface PendingPost {
  id: number
  content: string
  userNickname: string
  createdAt: string
}

// 仅"社区动态"走审核流（t_post.audit_status）。知识科普(t_article)/领养救助(t_adoption)
// 数据模型无 audit_status 审核字段，系统并不对它们做审核，故不再展示这两个误导性的占位 Tab。
type TabId = 'community'

const activeTab = ref<TabId>('community')

const tabs = ref<Array<{ id: TabId; label: string; status: 'active' | 'wip' }>>([
  { id: 'community', label: '社区动态', status: 'active' },
])

const pendingPosts = ref<PendingPost[]>([])
const loadingPosts = ref(false)
const pendingTotal = ref(0)

// 今日审核进度：按后端真实返回计算
// 今日"已处理" = pending → approved/rejected 的数量（演示:用 approved total 兜底）
const progressToday = ref({ done: 0, total: 0 })
const progressPercent = computed(() => {
  if (progressToday.value.total === 0) return 0
  return Math.round((progressToday.value.done / progressToday.value.total) * 100)
})

async function loadPosts() {
  if (activeTab.value !== 'community') return  // 其他 tab 不发请求
  loadingPosts.value = true
  try {
    const res = await adminApi.postAuditList('pending', 1, 20)
    pendingPosts.value = res.list || []
    pendingTotal.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载审核列表失败')
    pendingPosts.value = []
  } finally {
    loadingPosts.value = false
  }
}

async function loadProgress() {
  // 今日进度：拉"已通过" + "已驳回" 当日数量,与 pending 总数合并为今日总量
  try {
    const [a, r] = await Promise.all([
      adminApi.postAuditList('approved', 1, 1),
      adminApi.postAuditList('rejected', 1, 1),
    ])
    const done = (a?.total || 0) + (r?.total || 0)
    progressToday.value = { done, total: done + pendingTotal.value }
  } catch { /* 静默 */ }
}

onMounted(async () => {
  await loadPosts()
  await loadProgress()
})

watch(activeTab, () => {
  loadPosts()
})

// 时间格式化
const formatTime = (s: string) => (s || '').slice(0, 16).replace('T', ' ')

async function reviewPost(p: PendingPost, action: 'approve' | 'reject' | 'violate') {
  const labelMap = {
    approve: '通过',
    reject: '驳回',
    violate: '标记违规',
  } as const
  let reason: string | undefined
  if (action !== 'approve') {
    try {
      const { value } = await ElMessageBox.prompt(
        `请输入${labelMap[action]}原因`,
        `${labelMap[action]}帖子`,
        { inputPlaceholder: '原因（可选）', confirmButtonText: '确认', cancelButtonText: '取消' },
      )
      reason = value || undefined
    } catch { return }
  }
  try {
    const approved = action === 'approve'
    await adminApi.auditPost(p.id, approved, reason)
    ElMessage.success(`${labelMap[action]}成功`)
    pendingPosts.value = pendingPosts.value.filter(x => x.id !== p.id)
    pendingTotal.value = Math.max(0, pendingTotal.value - 1)
    loadProgress()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const empty = computed(() => !loadingPosts.value && pendingPosts.value.length === 0)
</script>

<template>
  <div class="audit-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">内容审核中心</h1>
        <p class="page-sub">确保社区质量，为每一个生命负责。</p>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="tab"
        :class="{ active: activeTab === t.id }"
        @click="activeTab = t.id"
      >
        {{ t.label }}
        <span v-if="t.status === 'wip'" class="wip-tag">开发中</span>
      </button>
    </div>

    <div class="layout">
      <div class="left-col">
        <!-- 社区动态：接真实接口 -->
        <template v-if="activeTab === 'community'">
          <div v-if="loadingPosts" class="empty-tip">加载中...</div>
          <div v-else-if="empty" class="empty-tip">暂无待审核内容</div>
          <div v-for="p in pendingPosts" v-else :key="p.id" class="post-card">
            <div class="post-body">
              <div class="post-head">
                <div class="post-user">
                  <div class="user-avatar">
                    <span class="material-symbols-outlined">person</span>
                  </div>
                  <div>
                    <div class="user-name-row">
                      <span class="user-name">{{ p.userNickname }}</span>
                    </div>
                    <span class="post-time">申请时间: {{ formatTime(p.createdAt) }}</span>
                  </div>
                </div>
                <span class="status-badge">待审核</span>
              </div>

              <p class="post-excerpt">{{ p.content }}</p>

              <div class="post-actions">
                <button class="action-btn success" @click="reviewPost(p, 'approve')">
                  <span class="material-symbols-outlined">check_circle</span> 快速通过
                </button>
                <button class="action-btn warning" @click="reviewPost(p, 'reject')">
                  <span class="material-symbols-outlined">block</span> 填写驳回
                </button>
                <button class="action-btn danger" @click="reviewPost(p, 'violate')">
                  <span class="material-symbols-outlined">report</span> 标记违规
                </button>
              </div>
            </div>
          </div>
        </template>

      </div>

      <!-- 右栏：审核进度（真实数据） -->
      <aside class="right-col">
        <div class="progress-card">
          <div class="progress-icon">
            <span class="material-symbols-outlined">verified_user</span>
          </div>
          <div class="progress-info">
            <h3 class="progress-title">今日审核进度</h3>
            <p class="progress-sub">已处理 {{ progressToday.done }} / 共 {{ progressToday.total }} 条</p>
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
            </div>
            <div class="progress-percent">{{ progressPercent }}%</div>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.audit-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}
.page-sub {
  margin-top: 4px;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}
.empty-tip {
  padding: $spacing-xl;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
}

// ============ Tabs ============
.tabs {
  display: flex;
  gap: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
}
.tab {
  padding: $spacing-sm 0;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  position: relative;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  &.active {
    color: $color-brand;
    font-weight: 700;
    &::after {
      content: '';
      position: absolute;
      bottom: -1px; left: 0; right: 0;
      height: 3px;
      background: $color-brand;
      border-radius: $radius-full;
    }
  }
}
.wip-tag {
  padding: 2px $spacing-xs;
  background: rgba(0, 96, 172, 0.12);
  color: $color-secondary;
  border-radius: $radius-sm;
  font-size: 10px;
  font-weight: 700;
}

// ============ Layout ============
.layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  @include desktop {
    grid-template-columns: 2fr 1fr;
  }
}
.left-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

// ============ Post Card ============
.post-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.post-body {
  display: flex;
  flex-direction: column;
}
.post-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: $spacing-md;
}
.post-user { display: flex; gap: $spacing-sm; }
.user-avatar {
  width: 40px; height: 40px;
  background: $color-secondary-container;
  border-radius: $radius-full;
  display: flex; align-items: center; justify-content: center;
}
.user-name-row {
  display: flex; align-items: center; gap: $spacing-xs; margin-bottom: 4px;
}
.user-name { font-weight: 700; color: $color-on-surface; }
.post-time {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.status-badge {
  padding: 4px $spacing-md;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
}
.post-excerpt {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: 1.7;
  margin-bottom: $spacing-md;
  @include text-ellipsis(3);
}
.post-actions {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: wrap;
}
.action-btn {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: none;
  &.success { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.warning { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.danger { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
  &.outline { background: transparent; border: 1px solid $color-outline-variant; color: $color-on-surface; }
}

// ============ WIP Card ============
.wip-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-2xl $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  text-align: center;
  .material-symbols-outlined {
    font-size: 56px;
    color: $color-secondary;
    opacity: 0.6;
  }
  h3 {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 700;
    color: $color-on-surface;
  }
  p { color: $color-on-surface-variant; margin: 0; }
  .muted { color: $color-on-surface-variant; opacity: 0.7; font-size: $font-size-label-sm; }
}

// ============ Right Column ============
.right-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.progress-card {
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  padding: $spacing-lg;
  background: linear-gradient(135deg, $color-secondary 0%, $color-secondary-light 100%);
  color: #ffffff;
  border-radius: $radius-md;
  box-shadow: $shadow-md;
}
.progress-icon {
  width: 80px; height: 80px;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  .material-symbols-outlined {
    font-size: 80px;
    opacity: 0.5;
  }
}
.progress-info { flex: 1; }
.progress-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  margin-bottom: 4px;
}
.progress-sub {
  font-size: $font-size-label-sm;
  opacity: 0.85;
  margin-bottom: $spacing-sm;
}
.progress-bar {
  height: 8px;
  background: rgba(255, 255, 255, 0.25);
  border-radius: $radius-full;
  overflow: hidden;
  margin-bottom: 6px;
}
.progress-fill {
  height: 100%;
  background: #ffffff;
  border-radius: $radius-full;
  transition: width $transition-base;
}
.progress-percent {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
}
</style>