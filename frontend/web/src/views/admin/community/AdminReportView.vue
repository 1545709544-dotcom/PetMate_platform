<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'
import { communityApi } from '@/api/modules/community'
import { productApi } from '@/api/modules/product'
import type { Report } from '@/api/modules/admin'
import type { Post } from '@/api/modules/community'
import type { Product } from '@/api/modules/product'

const router = useRouter()

type Status = 'pending' | 'resolved' | 'rejected'

const tabs: { id: Status; label: string }[] = [
  { id: 'pending', label: '待处理' },
  { id: 'resolved', label: '已处理' },
  { id: 'rejected', label: '已驳回' },
]

// 类型筛选段控
const typeFilters: { id: string; label: string }[] = [
  { id: '', label: '全部' },
  { id: 'post', label: '帖子' },
  { id: 'comment', label: '评论' },
  { id: 'user', label: '用户' },
  { id: 'product', label: '商品' },
]

const activeTab = ref<Status>('pending')
const activeType = ref<string>('')
const keyword = ref('')
const page = ref(1)
const pageSize = ref(20)

const reports = ref<Report[]>([])
const loading = ref(false)
const total = ref(0)

const empty = computed(() => !loading.value && reports.value.length === 0)

const targetTypeLabel: Record<string, string> = {
  post: '帖子',
  comment: '评论',
  user: '用户',
  product: '商品',
}

const formatTime = (s: string) => (s || '').slice(0, 16).replace('T', ' ')

// ============ 加载列表（带筛选 / 分页） ============
async function loadReports() {
  loading.value = true
  try {
    const res = await adminApi.reportList({
      status: activeTab.value,
      targetType: activeType.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: pageSize.value,
    })
    reports.value = res.list || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载举报列表失败')
    reports.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadReports)

// 切换状态 / 类型时重置到第一页
watch(activeTab, () => { page.value = 1; loadReports() })
watch(activeType, () => { page.value = 1; loadReports() })

// 关键词防抖搜索
let kwTimer: ReturnType<typeof setTimeout> | null = null
function onKeywordInput() {
  if (kwTimer) clearTimeout(kwTimer)
  kwTimer = setTimeout(() => {
    page.value = 1
    loadReports()
  }, 300)
}

function onPageChange(p: number) {
  page.value = p
  loadReports()
}
function onSizeChange(s: number) {
  pageSize.value = s
  page.value = 1
  loadReports()
}

// ============ 处理 / 驳回 ============
// ============ 处理举报：删除被举报内容 + 向作者推送违规警告 ============
const handleVisible = ref(false)
const handleTarget = ref<Report | null>(null)
const handleNote = ref('')

function openHandle(r: Report) {
  handleTarget.value = r
  handleNote.value = ''
  handleVisible.value = true
}

async function doHandle() {
  const r = handleTarget.value
  if (!r) return
  try {
    const res = await adminApi.handleReport(r.id, 'resolve', handleNote.value || undefined)
    handleVisible.value = false
    const applied = (res && res.targetAction) || 'none'
    const label =
      applied === 'removed_and_warned'
        ? (r.targetType === 'post' ? '帖子已下架并已警告作者'
          : r.targetType === 'product' ? '商品已下架并已通知商家'
          : '评论已删除并已警告作者')
        : applied === 'warned' ? '已向该用户发送违规警告'
          : applied === 'removed' ? '内容已删除（作者通知未送达）'
            : '已标记处理'
    ElMessage.success(`处理成功：${label}`)
    reports.value = reports.value.filter((x) => x.id !== r.id)
    total.value = Math.max(0, total.value - 1)
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function rejectReport(r: Report) {
  let note: string | undefined
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入驳回备注',
      '驳回举报',
      { inputPlaceholder: '备注（可选）', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
    note = value || undefined
  } catch {
    return // 用户取消
  }
  try {
    await adminApi.handleReport(r.id, 'reject', note)
    ElMessage.success('已驳回，未处置内容')
    reports.value = reports.value.filter((x) => x.id !== r.id)
    total.value = Math.max(0, total.value - 1)
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ============ 内联内容预览（弹窗，不离开当前页） ============
type PreviewState =
  | { kind: 'post'; data: Post }
  | { kind: 'comment'; data: { postId?: number; snippet?: string } }
  | { kind: 'user'; data: Record<string, any> }
  | { kind: 'product'; data: Product }
  | null

const previewVisible = ref(false)
const previewLoading = ref(false)
const preview = ref<PreviewState>(null)

async function openPreview(r: Report) {
  previewVisible.value = true
  previewLoading.value = true
  preview.value = null
  try {
    if (r.targetType === 'post') {
      const post = await communityApi.postDetail(r.targetId)
      preview.value = { kind: 'post', data: post }
    } else if (r.targetType === 'user') {
      const u = await adminApi.userDetail(r.targetId)
      preview.value = { kind: 'user', data: u }
    } else if (r.targetType === 'comment') {
      const t = await adminApi.reportTarget(r.id)
      preview.value = { kind: 'comment', data: t }
    } else if (r.targetType === 'product') {
      const p = await productApi.detail(r.targetId)
      preview.value = { kind: 'product', data: p }
    } else {
      ElMessage.info('未知举报类型，无法预览')
    }
  } catch {
    ElMessage.warning('加载预览内容失败')
  } finally {
    previewLoading.value = false
  }
}

function goPost(id: number) {
  previewVisible.value = false
  router.push(`/community/post/${id}`)
}
function goUser(id: number) {
  previewVisible.value = false
  router.push(`/admin/user/detail/${id}`)
}
function goProduct(id: number) {
  previewVisible.value = false
  router.push({ name: 'ProductDetail', params: { id } })
}
</script>

<template>
  <div class="report-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">举报处理</h1>
        <p class="page-sub">及时处理用户举报，维护社区秩序。</p>
      </div>
    </div>

    <!-- 状态 Tabs -->
    <div class="tabs">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="tab"
        :class="{ active: activeTab === t.id }"
        @click="activeTab = t.id"
      >
        {{ t.label }}
        <span v-if="activeTab === t.id" class="tab-count">({{ total }})</span>
      </button>
    </div>

    <!-- 筛选工具条：类型段控 + 关键词搜索 -->
    <div class="toolbar">
      <div class="seg">
        <button
          v-for="f in typeFilters"
          :key="f.id"
          class="seg-item"
          :class="{ active: activeType === f.id }"
          @click="activeType = f.id"
        >
          {{ f.label }}
        </button>
      </div>
      <el-input
        v-model="keyword"
        class="search"
        placeholder="搜索举报理由 / 目标 ID"
        clearable
        @input="onKeywordInput"
      >
        <template #prefix>
          <span class="material-symbols-outlined search-ico">search</span>
        </template>
      </el-input>
    </div>

    <div v-if="loading" class="empty-tip">加载中...</div>
    <div v-else-if="empty" class="empty-tip">
      <span class="material-symbols-outlined empty-ico">inbox</span>
      <p>暂无符合条件的举报记录</p>
    </div>

    <div v-else class="report-list">
      <div v-for="r in reports" :key="r.id" class="report-card">
        <div class="report-main">
          <div class="report-top">
            <span class="type-badge" :class="`t-${r.targetType}`">
              {{ targetTypeLabel[r.targetType] || r.targetType }}
            </span>
            <span class="target-id">#{{ r.targetId }}</span>
            <span class="report-time">举报时间: {{ formatTime(r.createdAt) }}</span>
          </div>

          <p class="report-reason">
            <span class="reason-label">举报理由：</span>{{ r.reason || '（未填写）' }}
          </p>
          <p class="report-meta">举报人 ID: {{ r.reporterId }}</p>
          <p v-if="r.handleNote" class="report-note">处理备注: {{ r.handleNote }}</p>
        </div>

        <div class="report-actions">
          <button class="action-btn ghost" @click="openPreview(r)">
            <span class="material-symbols-outlined">visibility</span> 查看
          </button>
          <template v-if="r.status === 'pending'">
            <button class="action-btn success" @click="openHandle(r)">
              <span class="material-symbols-outlined">check_circle</span> 处理
            </button>
            <button class="action-btn danger" @click="rejectReport(r)">
              <span class="material-symbols-outlined">block</span> 驳回
            </button>
          </template>
          <span v-else class="done-badge" :class="r.status">
            {{ r.status === 'resolved' ? '已处理' : '已驳回' }}
          </span>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="!empty && !loading" class="pager">
      <span class="total">共 {{ total }} 条</span>
      <el-pagination
        layout="prev, pager, next, sizes"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        :page-sizes="[10, 20, 50, 100]"
        background
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <!-- 内联内容预览弹窗 -->
    <el-dialog
      v-model="previewVisible"
      title="内容预览"
      width="560px"
      class="preview-dialog"
      align-center
    >
      <div v-if="previewLoading" class="preview-loading">
        <span class="material-symbols-outlined spin">progress_activity</span> 加载中...
      </div>

      <template v-else-if="preview">
        <!-- 帖子 -->
        <div v-if="preview.kind === 'post'" class="preview-post">
          <div class="pv-author">
            <img v-if="preview.data.userAvatar" :src="preview.data.userAvatar" class="pv-avatar" alt="" />
            <div>
              <div class="pv-name">{{ preview.data.userNickname }}</div>
              <div class="pv-time">{{ formatTime(preview.data.createdAt) }}</div>
            </div>
          </div>
          <p class="pv-content">{{ preview.data.content }}</p>
          <div v-if="preview.data.images && preview.data.images.length" class="pv-imgs">
            <img v-for="(img, i) in preview.data.images" :key="i" :src="img" alt="" />
          </div>
          <div class="pv-stats">
            <span>👍 {{ preview.data.likeCount }}</span>
            <span>💬 {{ preview.data.commentCount }}</span>
            <span>⭐ {{ preview.data.collectCount }}</span>
          </div>
          <button class="pv-go" @click="goPost(preview.data.id)">前往帖子 →</button>
        </div>

        <!-- 评论 -->
        <div v-else-if="preview.kind === 'comment'" class="preview-comment">
          <div class="pv-quote">
            {{ preview.data.snippet || '（该评论内容已不存在）' }}
          </div>
          <button
            v-if="preview.data.postId"
            class="pv-go"
            @click="goPost(preview.data.postId!)"
          >前往原帖 →</button>
          <p v-else class="pv-hint">该评论所属帖子已不存在</p>
        </div>

        <!-- 用户 -->
        <div v-else-if="preview.kind === 'user'" class="preview-user">
          <div class="pv-author">
            <img v-if="preview.data.avatar" :src="preview.data.avatar" class="pv-avatar" alt="" />
            <div>
              <div class="pv-name">{{ preview.data.nickname || '匿名用户' }}</div>
              <div class="pv-time">ID: {{ preview.data.id }}</div>
            </div>
          </div>
          <ul class="pv-fields">
            <li><span>手机号</span>{{ preview.data.phone || '—' }}</li>
            <li><span>VIP 等级</span>L{{ preview.data.vipLevel ?? 0 }}</li>
            <li><span>状态</span>{{ preview.data.status === 1 ? '已封禁' : '正常' }}</li>
            <li><span>注册时间</span>{{ formatTime(preview.data.registerDate) }}</li>
          </ul>
          <button class="pv-go" @click="goUser(preview.data.id)">查看用户详情 →</button>
        </div>

        <!-- 商品 -->
        <div v-else-if="preview.kind === 'product'" class="preview-product">
          <img
            v-if="preview.data.mainImage"
            :src="preview.data.mainImage"
            class="pv-prod-img"
            alt=""
          />
          <div class="pv-prod-name">{{ preview.data.name }}</div>
          <ul class="pv-fields">
            <li><span>商品 ID</span>{{ preview.data.id }}</li>
            <li><span>售价</span>¥{{ preview.data.salePrice ?? '—' }}</li>
            <li><span>销量</span>{{ preview.data.saleCount ?? 0 }}</li>
            <li>
              <span>状态</span>{{ preview.data.status === 1 ? '在售' : '已下架' }}
            </li>
          </ul>
          <button class="pv-go" @click="goProduct(preview.data.id)">前往商品 →</button>
        </div>
      </template>
    </el-dialog>

    <!-- 处理举报：删除被举报内容 + 向作者推送违规警告 -->
    <el-dialog
      v-model="handleVisible"
      title="处理举报"
      width="480px"
      align-center
    >
      <div v-if="handleTarget" class="handle-box">
        <div class="handle-meta">
          <span class="type-badge" :class="`t-${handleTarget.targetType}`">
            {{ targetTypeLabel[handleTarget.targetType] || handleTarget.targetType }}
          </span>
          <span class="target-id">#{{ handleTarget.targetId }}</span>
        </div>
        <p class="handle-reason">
          <span class="reason-label">举报理由：</span>{{ handleTarget.reason || '（未填写）' }}
        </p>
        <div class="handle-action-card">
          <span class="material-symbols-outlined">delete_forever</span>
          <div>
            <p class="ha-title">删除被举报内容</p>
            <p class="ha-sub">
              {{
                handleTarget.targetType === 'post' ? '帖子的下架（软删除）并标记审核不通过' :
                handleTarget.targetType === 'comment' ? '评论将被删除（软删除）' :
                handleTarget.targetType === 'product' ? '商品将被下架（软删除）并通知商家' :
                '该举报对象为用户，无内容可删除'
              }}
            </p>
          </div>
        </div>
        <div class="handle-action-card warn">
          <span class="material-symbols-outlined">notification_important</span>
          <div>
            <p class="ha-title">向作者推送违规警告</p>
            <p class="ha-sub">
              {{
                handleTarget.targetType === 'user' ? '向被举报用户本人发送站内警告信' :
                '向该内容作者发送违规警告站内信（前台"我的消息"可见）'
              }}
            </p>
          </div>
        </div>
        <el-input
          v-model="handleNote"
          type="textarea"
          :rows="3"
          placeholder="处理备注（可选）"
          class="handle-note"
        />
      </div>
      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="doHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.report-view {
  padding: $spacing-lg;
}

.page-head {
  margin-bottom: $spacing-lg;
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
}

.page-sub {
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  margin-top: 4px;
}

.tabs {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
}

.tab {
  padding: 10px 18px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
  font-weight: $font-weight-medium;
  cursor: pointer;
  transition: color $transition-fast, border-color $transition-fast;

  &.active {
    color: $color-brand;
    border-bottom-color: $color-brand;
    font-weight: $font-weight-bold;
  }
}

.tab-count {
  font-size: $font-size-label-sm;
}

/* 筛选工具条 */
.toolbar {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
}

.seg {
  display: inline-flex;
  background: $color-surface-container-low;
  border-radius: $radius-full;
  padding: 4px;
  gap: 2px;
}

.seg-item {
  padding: 6px 16px;
  border: none;
  background: transparent;
  border-radius: $radius-full;
  color: $color-text-secondary;
  font-size: $font-size-label-md;
  font-weight: $font-weight-medium;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast;

  &.active {
    background: $color-brand;
    color: #fff;
  }
}

.search {
  width: 280px;
  max-width: 100%;
}

.search-ico {
  font-size: 18px;
  color: $color-text-tertiary;
}

.empty-tip {
  padding: $spacing-2xl;
  text-align: center;
  color: $color-text-tertiary;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;

  .empty-ico {
    font-size: 40px;
    opacity: 0.5;
  }

  p { margin: 0; }
}

.report-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.report-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-lg;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  border: 1px solid transparent;
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 30px -10px rgba($color-brand, 0.18);
    border-color: rgba($color-brand, 0.25);
  }
}

.report-main {
  flex: 1;
  min-width: 0;
}

.report-top {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.type-badge {
  padding: 2px 10px;
  background: $color-brand-container;
  color: $color-brand;
  border-radius: $radius-sm;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;

  &.t-comment { background: rgba($color-info, 0.14); color: $color-info; }
  &.t-user { background: rgba($color-success, 0.14); color: $color-success; }
  &.t-product { background: rgba($color-brand, 0.14); color: $color-brand; }
}

.target-id {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

.report-time {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  margin-left: auto;
}

.report-reason {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  margin-bottom: 4px;
  word-break: break-word;
}

.reason-label {
  font-weight: $font-weight-semibold;
}

.report-meta,
.report-note {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin-top: 2px;
}

.report-actions {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-shrink: 0;
}

.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 14px;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: opacity $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    opacity: 0.88;
    transform: scale(1.03);
  }

  &.success {
    background: $color-success;
    color: #fff;
  }

  &.danger {
    background: $color-danger;
    color: #fff;
  }

  &.ghost {
    background: $color-surface-container-low;
    color: $color-text-secondary;
  }
}

.done-badge {
  padding: 6px 14px;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;

  &.resolved {
    background: rgba($color-success, 0.12);
    color: $color-success;
  }

  &.rejected {
    background: rgba($color-text-tertiary, 0.15);
    color: $color-text-tertiary;
  }
}

.pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: $spacing-md;
  margin-top: $spacing-lg;

  .total {
    font-size: $font-size-label-md;
    color: $color-text-secondary;
  }
}

/* 预览弹窗 */
.preview-loading {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  color: $color-text-secondary;
  justify-content: center;
  padding: $spacing-lg;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.preview-post,
.preview-comment,
.preview-user {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.pv-author {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.pv-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  background: $color-surface-container-low;
}

.pv-name {
  font-weight: $font-weight-bold;
  color: $color-on-surface;
}

.pv-time {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}

.pv-content {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
  color: $color-on-surface;
}

.pv-imgs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-sm;

  img {
    width: 100%;
    aspect-ratio: 1;
    object-fit: cover;
    border-radius: $radius-md;
  }
}

.pv-stats {
  display: flex;
  gap: $spacing-md;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
}

.pv-quote {
  background: $color-surface-container-low;
  border-left: 3px solid $color-brand;
  padding: $spacing-md;
  border-radius: $radius-md;
  color: $color-on-surface;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.pv-fields {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;

  li {
    display: flex;
    justify-content: space-between;
    font-size: $font-size-body-md;
    color: $color-on-surface;
    padding-bottom: $spacing-xs;
    border-bottom: 1px solid $color-outline-variant;

    span {
      color: $color-text-secondary;
    }
  }
}

.pv-hint {
  font-size: $font-size-label-md;
  color: $color-text-tertiary;
  margin: 0;
}

.preview-product {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.pv-prod-img {
  width: 120px;
  height: 120px;
  object-fit: cover;
  border-radius: $radius-md;
  background: $color-surface-container-low;
}

.pv-prod-name {
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  word-break: break-word;
}

.pv-go {
  align-self: flex-start;
  padding: 8px 16px;
  border: 1px solid $color-brand;
  background: transparent;
  color: $color-brand;
  border-radius: $radius-full;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast;

  &:hover {
    background: $color-brand;
    color: #fff;
  }
}

/* 处理弹窗 */
.handle-box {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.handle-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.handle-reason {
  margin: 0;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  line-height: 1.5;
}

.handle-tip {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
}

.handle-action-card {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  padding: $spacing-md;
  border-radius: $radius-lg;
  background: rgba($color-danger, 0.06);
  border: 1px solid rgba($color-danger, 0.18);

  .material-symbols-outlined {
    font-size: 22px;
    color: $color-danger;
    margin-top: 2px;
  }

  &.warn {
    background: rgba($color-warning, 0.08);
    border-color: rgba($color-warning, 0.28);

    .material-symbols-outlined {
      color: $color-warning;
    }
  }

  .ha-title {
    margin: 0;
    font-size: $font-size-body-md;
    font-weight: $font-weight-semibold;
    color: $color-on-surface;
  }

  .ha-sub {
    margin: 2px 0 0;
    font-size: $font-size-label-sm;
    color: $color-text-secondary;
    line-height: 1.4;
  }
}

.handle-note {
  margin-top: $spacing-xs;
}

@media (max-width: 720px) {
  .report-card {
    flex-direction: column;
    align-items: flex-start;
    gap: $spacing-md;
  }

  .report-actions {
    flex-wrap: wrap;
  }

  .search {
    width: 100%;
  }
}
</style>
