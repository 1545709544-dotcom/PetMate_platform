<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { merchantApi } from '@/api/modules/merchant'
import { ElMessage } from 'element-plus'

const loading = ref(false)
const list = ref<any[]>([])
const total = ref(0)
const moduleFilter = ref('')

const moduleText: Record<string, string> = {
  order: '订单',
  product: '商品',
  service: '服务',
  review: '评价',
  staff: '员工',
  settings: '设置',
}
const actionText: Record<string, string> = {
  ship: '发货',
  create: '新增',
  update: '编辑',
  remark: '备注',
  delete: '删除',
  refund_approve: '同意退款',
  refund_confirm: '确认退款',
  refund_reject: '拒绝退款',
  refund_reship: '退款重寄',
}

async function load() {
  loading.value = true
  try {
    const r = await merchantApi.operationLogs({
      page: 1,
      size: 50,
      module: moduleFilter.value || undefined,
    })
    list.value = r.list || []
    total.value = r.total || 0
  } catch {
    ElMessage.error('日志加载失败')
  } finally {
    loading.value = false
  }
}

function fmtTime(t: string) {
  return t ? t.replace('T', ' ').slice(0, 19) : '-'
}

function roleTag(role: string) {
  return { OWNER: '店主', OPERATOR: '运营', CS: '客服', WAREHOUSE: '仓管' }[role] || role
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1 class="page-title">操作日志</h1>
        <p class="page-sub">记录本店铺内所有关键操作，满足企业级后台的可追溯要求。</p>
      </div>
      <div class="head-actions">
        <select v-model="moduleFilter" class="select-btn" @change="load">
          <option value="">全部模块</option>
          <option value="order">订单</option>
          <option value="product">商品</option>
          <option value="service">服务</option>
          <option value="review">评价</option>
          <option value="staff">员工</option>
          <option value="settings">设置</option>
        </select>
        <button class="btn-outline" @click="load">
          <span class="material-symbols-outlined">refresh</span>
          刷新
        </button>
      </div>
    </div>

    <div class="table-card">
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
          </tr>
        </thead>
        <tbody v-if="loading">
          <tr><td colspan="7" class="state-cell">加载中...</td></tr>
        </tbody>
        <tbody v-else-if="list.length === 0">
          <tr><td colspan="7" class="state-cell">暂无操作记录</td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="log in list" :key="log.id">
            <td>{{ log.operatorName || '-' }}</td>
            <td><span class="role-tag">{{ roleTag(log.role) }}</span></td>
            <td>{{ moduleText[log.module] || log.module }}</td>
            <td><span class="action-tag">{{ actionText[log.action] || log.action }}</span></td>
            <td class="mono">{{ log.target || '-' }}</td>
            <td class="detail">{{ log.detail || '-' }}</td>
            <td class="time">{{ fmtTime(log.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <p class="total">共 {{ total }} 条记录</p>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.page {
  max-width: 1080px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.page-title {
  margin: 0;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-extrabold;
  color: $color-text-primary;
}

.page-sub {
  margin: 4px 0 0;
  font-size: $font-size-body-md;
  color: $color-text-secondary;
}

.head-actions {
  display: flex;
  gap: $spacing-sm;
}

.select-btn {
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  background: #fff;
  font-size: $font-size-label-md;
  cursor: pointer;
}

.btn-outline {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-brand;
  border-radius: $radius-md;
  background: #fff;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
}

.table-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-md;
  box-shadow: 0 1px 3px rgba(22, 29, 31, 0.04);
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: $font-size-body-md;

  th {
    text-align: left;
    padding: $spacing-sm $spacing-md;
    color: $color-text-secondary;
    font-weight: $font-weight-semibold;
    border-bottom: 1px solid $color-surface-container;
    white-space: nowrap;
  }
  td {
    padding: $spacing-sm $spacing-md;
    border-bottom: 1px solid $color-surface-container-low;
    color: $color-text-primary;
    vertical-align: top;
  }
  tbody tr:hover {
    background: $color-surface-container-low;
  }
}

.state-cell {
  text-align: center;
  color: $color-text-tertiary;
  padding: $spacing-xl !important;
}

.role-tag {
  font-size: $font-size-label-sm;
  padding: 2px 8px;
  border-radius: 999px;
  background: $color-surface-container;
  color: $color-text-secondary;
}

.action-tag {
  font-size: $font-size-label-sm;
  padding: 2px 8px;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.1);
  color: $color-brand;
  font-weight: $font-weight-semibold;
}

.mono {
  font-family: monospace;
  font-size: $font-size-label-md;
}

.detail {
  max-width: 280px;
  color: $color-text-secondary;
  word-break: break-all;
}

.time {
  white-space: nowrap;
  color: $color-text-secondary;
  font-size: $font-size-label-md;
}

.total {
  margin: $spacing-sm 0 0;
  font-size: $font-size-label-md;
  color: $color-text-tertiary;
}
</style>
