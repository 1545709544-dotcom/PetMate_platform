<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'

interface Customer {
  userId: number
  nickname: string
  avatar: string | null
  phone: string
  vipLevel: number
  orderCount: number
  totalAmount: number
  lastOrderAt: string
}

const loading = ref(false)
const customers = ref<Customer[]>([])
const searchText = ref('')

async function load() {
  loading.value = true
  try {
    customers.value = await merchantApi.customers(searchText.value.trim() || undefined)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载客户列表失败')
    customers.value = []
  } finally {
    loading.value = false
  }
}

function formatTime(s: string) {
  if (!s) return '—'
  return s.replace('T', ' ').slice(0, 16)
}

function vipLabel(level: number) {
  if (level >= 3) return '钻石会员'
  if (level >= 2) return '黄金会员'
  if (level >= 1) return '普通会员'
  return '非会员'
}

onMounted(load)
</script>

<template>
  <div class="customer-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">客户管理</h1>
        <p class="page-sub">查看消费客户画像，了解复购情况</p>
      </div>
      <button class="btn-primary" @click="load">
        <span class="material-symbols-outlined">refresh</span>
        刷新
      </button>
    </div>

    <div class="filter-bar">
      <input v-model="searchText" class="search-input" placeholder="搜索昵称或手机号..." @keyup.enter="load" />
      <button class="btn-search" @click="load">搜索</button>
    </div>

    <div v-if="loading" class="state">加载中...</div>
    <div v-else-if="!customers.length" class="state empty-state">
      <span class="material-symbols-outlined">people</span>
      <p>暂无客户数据</p>
    </div>
    <table v-else class="data-table">
      <thead>
        <tr>
          <th>客户</th>
          <th>手机号</th>
          <th>会员等级</th>
          <th>订单数</th>
          <th>消费总额</th>
          <th>最近下单</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in customers" :key="c.userId">
          <td>
            <div class="user-cell">
              <img v-if="c.avatar" :src="c.avatar" class="avatar" />
              <span v-else class="avatar-placeholder">
                <span class="material-symbols-outlined">person</span>
              </span>
              <span class="user-name">{{ c.nickname || `用户${c.userId}` }}</span>
            </div>
          </td>
          <td class="mono">{{ c.phone || '—' }}</td>
          <td>
            <span class="vip-tag" :class="`vip-${c.vipLevel >= 3 ? 3 : c.vipLevel}`">{{ vipLabel(c.vipLevel) }}</span>
          </td>
          <td>{{ c.orderCount }} 单</td>
          <td class="amount">¥{{ Number(c.totalAmount).toFixed(2) }}</td>
          <td class="mono">{{ formatTime(c.lastOrderAt) }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style lang="scss" scoped>
.customer-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }
.btn-primary {
  display: inline-flex; align-items: center; gap: 4px;
  padding: $spacing-sm $spacing-lg; border-radius: $radius-md;
  background: $color-brand; color: #fff; border: none;
  font-size: $font-size-label-md; font-weight: 700; cursor: pointer;
  .material-symbols-outlined { font-size: 18px; }
}
.filter-bar { display: flex; gap: $spacing-sm; }
.search-input {
  flex: 1; padding: $spacing-xs $spacing-md;
  border: 1px solid $color-outline-variant; border-radius: $radius-md;
  background: #fff; font-size: $font-size-body-md; font-family: inherit;
  color: $color-on-surface; outline: none;
  &:focus { border-color: $color-brand; }
}
.btn-search {
  padding: $spacing-xs $spacing-lg; border-radius: $radius-md;
  background: $color-on-surface; color: $color-surface; border: none;
  font-weight: 700; cursor: pointer;
}
.state { padding: $spacing-2xl; text-align: center; color: $color-on-surface-variant; }
.empty-state {
  display: flex; flex-direction: column; align-items: center; gap: $spacing-sm;
  .material-symbols-outlined { font-size: 48px; opacity: 0.4; }
  p { margin: 0; }
}
.data-table {
  width: 100%; border-collapse: collapse;
  background: $color-surface-container-lowest; border-radius: $radius-md;
  overflow: hidden; box-shadow: $shadow-sm;
}
.data-table th {
  text-align: left; padding: $spacing-md;
  font-size: $font-size-label-sm; color: $color-on-surface-variant;
  font-weight: 600; background: $color-surface-container-low;
  border-bottom: 1px solid $color-outline-variant;
}
.data-table td {
  padding: $spacing-md; border-bottom: 1px solid $color-outline-variant;
  font-size: $font-size-body-md; vertical-align: middle;
}
.user-cell { display: flex; align-items: center; gap: $spacing-sm; }
.avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; }
.avatar-placeholder {
  width: 36px; height: 36px; border-radius: 50%;
  background: $color-surface-container; display: flex;
  align-items: center; justify-content: center;
  .material-symbols-outlined { font-size: 20px; color: $color-on-surface-variant; }
}
.user-name { font-weight: 600; color: $color-on-surface; }
.mono { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.amount { font-weight: 700; color: $color-emergency; }
.vip-tag {
  padding: 2px $spacing-sm; border-radius: $radius-full;
  font-size: $font-size-label-sm; font-weight: 700;
  &.vip-3 { background: rgba($color-warning, 0.15); color: #8a6500; }
  &.vip-2 { background: rgba($color-secondary, 0.1); color: $color-secondary; }
  &.vip-1 { background: $color-surface-container; color: $color-on-surface-variant; }
  &.vip-0 { background: $color-surface-container; color: $color-on-surface-variant; }
}
</style>