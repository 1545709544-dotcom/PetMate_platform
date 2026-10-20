<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type AdminLogItem } from '@/api/modules/admin'

const loading = ref(false)
const logs = ref<AdminLogItem[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = ref({ module: '', operator: '', keyword: '', start: '', end: '' })

const drawerVisible = ref(false)
const current = ref<AdminLogItem | null>(null)

const MODULE_TEXT: Record<string, string> = {
  user: '用户', merchant: '商家', post: '内容社区', product: '商品', order: '订单',
  refund: '退款', marketing: '营销', risk: '风控', sensitive: '敏感词', role: '角色权限',
  report: '举报', notify: '通知', system: '系统', config: '系统配置',
}
const ACTION_TEXT: Record<string, string> = {
  BAN_USER: '封禁用户', UNBAN_USER: '解封用户', EDIT_USER: '编辑用户', CREATE_USER: '新增用户',
  APPLY_PASS: '入驻审核通过', MERCHANT_BAN: '封禁商家', MERCHANT_UNBAN: '解封商家', MERCHANT_FORCE_REMOVE: '强制解除商家',
  POST_PASS: '动态通过', POST_REJECT: '动态驳回',
  PRODUCT_ON: '上架商品', PRODUCT_OFF: '下架商品', PRODUCT_DELETE: '删除商品',
  REFUND_REFUNDED: '退款通过', REFUND_REJECTED: '退款驳回',
  UPDATE_ROLES: '更新角色权限',
  EMERGENCY_BREAK_ON: '开启紧急熔断', EMERGENCY_BREAK_OFF: '关闭紧急熔断',
  ADD_SENSITIVE: '新增敏感词', EDIT_SENSITIVE: '编辑敏感词', DELETE_SENSITIVE: '删除敏感词',
  PUSH_NOTIFY: '推送通知',
  REPORT_RESOLVE: '举报处理', REPORT_REJECT: '举报驳回',
}
const ROLE_TEXT: Record<string, string> = { admin: '系统管理员' }

const moduleOptions = [
  { value: '', label: '全部模块' },
  ...Object.entries(MODULE_TEXT).map(([value, label]) => ({ value, label })),
]

const moduleText = (m: string) => MODULE_TEXT[m] || m
const actionText = (a: string) => ACTION_TEXT[a] || a
const roleText = (r: string | null) => (r ? (ROLE_TEXT[r] || r) : '—')

async function load() {
  loading.value = true
  try {
    const res = await adminApi.logs({
      page: page.value,
      size: size.value,
      module: filters.value.module || undefined,
      operator: filters.value.operator || undefined,
      keyword: filters.value.keyword || undefined,
      start: filters.value.start || undefined,
      end: filters.value.end || undefined,
    })
    logs.value = res.list || []
    total.value = res.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载操作日志失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function resetFilters() {
  filters.value = { module: '', operator: '', keyword: '', start: '', end: '' }
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  load()
}

function openDetail(l: AdminLogItem) {
  current.value = l
  drawerVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="log-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">操作日志</h1>
        <p class="page-sub">后台关键操作的审计轨迹，可按模块、操作人、关键字与时间检索</p>
      </div>
    </div>

    <section class="filters card">
      <div class="filter-item">
        <label>模块</label>
        <el-select v-model="filters.module" placeholder="全部模块" clearable @change="search">
          <el-option v-for="o in moduleOptions" :key="o.value" :label="o.label" :value="o.value" />
        </el-select>
      </div>
      <div class="filter-item">
        <label>操作人</label>
        <input v-model="filters.operator" class="input" placeholder="昵称或账号 ID" @keyup.enter="search" />
      </div>
      <div class="filter-item grow">
        <label>关键字</label>
        <input v-model="filters.keyword" class="input" placeholder="动作 / 对象 / 详情关键字" @keyup.enter="search" />
      </div>
      <div class="filter-item">
        <label>开始日期</label>
        <el-date-picker v-model="filters.start" type="date" value-format="YYYY-MM-DD" placeholder="起始" @change="search" />
      </div>
      <div class="filter-item">
        <label>结束日期</label>
        <el-date-picker v-model="filters.end" type="date" value-format="YYYY-MM-DD" placeholder="截止" @change="search" />
      </div>
      <div class="filter-actions">
        <button class="btn-primary" @click="search">
          <span class="material-symbols-outlined">search</span>
          查询
        </button>
        <button class="btn-outline" @click="resetFilters">重置</button>
      </div>
    </section>

    <section class="table-card" v-loading="loading">
      <table class="data-table">
        <thead>
          <tr>
            <th>操作人</th>
            <th>角色</th>
            <th>模块</th>
            <th>动作</th>
            <th>对象</th>
            <th>详情</th>
            <th>时间</th>
            <th class="col-op">操作</th>
          </tr>
        </thead>
        <tbody v-if="!loading && logs.length === 0">
          <tr><td colspan="8" class="state-cell">暂无操作记录</td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="l in logs" :key="l.id" @click="openDetail(l)">
            <td>
              <div class="op-name">{{ l.operatorName || '系统' }}</div>
              <div class="op-id" v-if="l.operatorId">#{{ l.operatorId }}</div>
            </td>
            <td><span class="role-tag">{{ roleText(l.role) }}</span></td>
            <td><span class="module-tag">{{ moduleText(l.module) }}</span></td>
            <td><span class="action-tag">{{ actionText(l.action) }}</span></td>
            <td class="mono">{{ l.target || '—' }}</td>
            <td class="detail">{{ l.detail || '—' }}</td>
            <td class="time">{{ (l.createdAt || '').replace('T', ' ') }}</td>
            <td class="col-op">
              <button class="link" @click.stop="openDetail(l)">详情</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pager">
        <span class="total">共 {{ total }} 条</span>
        <el-pagination
          layout="prev, pager, next, sizes"
          :total="total"
          :page-size="size"
          :current-page="page"
          :page-sizes="[10, 20, 50, 100]"
          background
          @current-change="onPageChange"
          @size-change="onSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="drawerVisible" title="操作详情" size="420px" direction="rtl">
      <div class="detail-panel" v-if="current">
        <div class="detail-row">
          <span class="d-label">操作人</span>
          <span class="d-value">{{ current.operatorName || '系统' }} <em v-if="current.operatorId">#{{ current.operatorId }}</em></span>
        </div>
        <div class="detail-row">
          <span class="d-label">角色</span>
          <span class="d-value">{{ roleText(current.role) }}</span>
        </div>
        <div class="detail-row">
          <span class="d-label">模块</span>
          <span class="d-value">{{ moduleText(current.module) }}</span>
        </div>
        <div class="detail-row">
          <span class="d-label">动作</span>
          <span class="d-value">
            <span class="action-tag">{{ actionText(current.action) }}</span>
            <code class="raw">{{ current.action }}</code>
          </span>
        </div>
        <div class="detail-row">
          <span class="d-label">对象</span>
          <span class="d-value mono">{{ current.target || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="d-label">详情</span>
          <span class="d-value">{{ current.detail || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="d-label">IP 地址</span>
          <span class="d-value mono">{{ current.ip || '—' }}</span>
        </div>
        <div class="detail-row">
          <span class="d-label">时间</span>
          <span class="d-value mono">{{ (current.createdAt || '').replace('T', ' ') }}</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.log-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; margin: 0; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin: 4px 0 0; }

.card { background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; }

.filters {
  display: flex; flex-wrap: wrap; align-items: flex-end; gap: $spacing-md; padding: $spacing-lg;
}
.filter-item { display: flex; flex-direction: column; gap: 4px; }
.filter-item.grow { flex: 1; min-width: 220px; }
.filter-item label { font-size: $font-size-label-sm; font-weight: 600; color: $color-on-surface-variant; }
.input {
  padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-default;
  background: #fff; font-size: $font-size-body-md; color: $color-on-surface; min-width: 180px;
  &:focus { outline: none; border-color: $color-brand; }
}
.filter-actions { display: flex; gap: $spacing-sm; margin-left: auto; }
.btn-primary, .btn-outline {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md; font-weight: 700; cursor: pointer; border: 1px solid transparent; font-size: $font-size-body-md;
}
.btn-primary { background: $color-brand; color: #fff; }
.btn-outline { background: transparent; color: $color-brand; border-color: $color-brand; }

.table-card { background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; overflow: hidden; }
.data-table { width: 100%; border-collapse: collapse; font-size: $font-size-body-md; }
.data-table th {
  text-align: left; padding: $spacing-md; font-size: $font-size-label-sm; font-weight: 600;
  color: $color-on-surface-variant; background: $color-surface-container-low; border-bottom: 1px solid $color-outline-variant;
  white-space: nowrap;
}
.data-table td { padding: $spacing-md; border-bottom: 1px solid $color-outline-variant; color: $color-on-surface; vertical-align: top; }
.data-table tbody tr { cursor: pointer; transition: background .15s ease; }
.data-table tbody tr:hover { background: $color-surface-container-low; }
.state-cell { text-align: center; color: $color-on-surface-variant; padding: $spacing-xl !important; }

.op-name { font-weight: 700; }
.op-id { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.mono { font-family: $font-family-mono; font-size: $font-size-label-md; color: $color-on-surface-variant; }
.detail { max-width: 320px; color: $color-on-surface-variant; word-break: break-all; }
.time { white-space: nowrap; color: $color-on-surface-variant; font-size: $font-size-label-md; }
.col-op { width: 72px; text-align: center; }

.role-tag, .module-tag {
  font-size: $font-size-label-sm; padding: 2px 10px; border-radius: 999px;
  background: $color-surface-container; color: $color-on-surface-variant; white-space: nowrap;
}
.module-tag { background: rgba(0,96,172,.1); color: $color-secondary; }
.action-tag {
  font-size: $font-size-label-sm; padding: 2px 10px; border-radius: $radius-sm;
  background: rgba(255,122,50,.12); color: $color-brand; font-weight: 700; white-space: nowrap;
}
.link { background: none; border: none; cursor: pointer; font-size: $font-size-label-md; font-weight: 700; color: $color-brand; }

.pager { display: flex; align-items: center; justify-content: space-between; padding: $spacing-md $spacing-lg; border-top: 1px solid $color-outline-variant; flex-wrap: wrap; gap: $spacing-sm; }
.total { font-size: $font-size-label-md; color: $color-on-surface-variant; }

.detail-panel { display: flex; flex-direction: column; gap: $spacing-md; }
.detail-row { display: flex; flex-direction: column; gap: 4px; padding-bottom: $spacing-md; border-bottom: 1px solid $color-outline-variant; }
.d-label { font-size: $font-size-label-sm; font-weight: 600; color: $color-on-surface-variant; }
.d-value { font-size: $font-size-body-md; color: $color-on-surface; display: flex; align-items: center; gap: $spacing-xs; flex-wrap: wrap; }
.d-value em { font-style: normal; color: $color-on-surface-variant; font-size: $font-size-label-sm; }
.d-value .raw { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; background: $color-surface-container-low; padding: 1px 6px; border-radius: $radius-sm; }
</style>
