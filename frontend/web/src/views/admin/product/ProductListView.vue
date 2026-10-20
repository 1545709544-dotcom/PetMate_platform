<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('')
const rows = ref<any[]>([])

const STATUS_MAP: Record<number, { label: string; cls: string }> = {
  1: { label: '在售', cls: 'on' },
  0: { label: '下架', cls: 'off' },
}
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

async function load() {
  loading.value = true
  try {
    const res = await adminApi.productList({
      page: page.value, size: size.value, keyword: keyword.value, status: statusFilter.value,
    })
    rows.value = (res.list || []) as any[]
    total.value = res.total ?? rows.value.length
  } catch (e: any) {
    ElMessage.error(e?.message || '加载商品失败')
  } finally {
    loading.value = false
  }
}

function search() { page.value = 1; load() }

async function toggleStatus(row: any) {
  const next = Number(row.status) === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确认将「${row.name}」${next === 1 ? '上架' : '下架'}？`,
      '状态切换',
      { type: 'warning' },
    )
  } catch { return }
  try {
    await adminApi.toggleProductStatus(row.id, next)
    row.status = next
    ElMessage.success(next === 1 ? '已上架' : '已下架')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.name}」？该操作不可恢复`,
      '删除确认',
      { type: 'error', confirmButtonText: '确认删除', cancelButtonText: '取消' },
    )
  } catch { return }
  try {
    await adminApi.deleteProduct(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p; load()
}

onMounted(load)
</script>

<template>
  <div class="product-admin-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">商品管理</h1>
        <p class="page-sub">全平台商品上下架、价格、库存</p>
      </div>
    </div>

    <section class="filter-card">
      <div class="filter-row">
        <select v-model="statusFilter" class="select" @change="search">
          <option value="">全部状态</option>
          <option value="1">在售</option>
          <option value="0">下架</option>
        </select>
        <input v-model="keyword" class="input" placeholder="搜索商品名" @keyup.enter="search" />
        <button class="btn-primary" @click="search"><span class="material-symbols-outlined">search</span>查询</button>
      </div>
    </section>

    <section class="table-card">
      <div v-if="loading" class="empty-tip">加载中...</div>
      <div v-else-if="!rows.length" class="empty-tip">暂无商品</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>商品</th>
            <th>分类</th>
            <th>价格</th>
            <th>库存</th>
            <th>销量</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in rows" :key="r.id">
            <td>
              <div class="product-cell">
                <span class="img-stub" v-if="!r.main_image">🛍️</span>
                <img v-else :src="r.main_image" :alt="r.name" />
                <div>
                  <div class="name">{{ r.name }}</div>
                  <div class="id">ID: {{ r.id }}</div>
                </div>
              </div>
            </td>
            <td>{{ r.categoryName || '—' }}</td>
            <td class="amount">¥{{ Number(r.sale_price || 0).toFixed(2) }}</td>
            <td>{{ r.total_stock ?? 0 }} <span class="sub" v-if="r.sku_count">/ {{ r.sku_count }} SKU</span></td>
            <td>{{ r.sale_count || 0 }}</td>
            <td>
              <span class="status-pill" :class="STATUS_MAP[Number(r.status)]?.cls">
                {{ STATUS_MAP[Number(r.status)]?.label || '未知' }}
              </span>
            </td>
            <td>
              <div class="actions">
                <button class="link" @click="toggleStatus(r)">
                  {{ Number(r.status) === 1 ? '下架' : '上架' }}
                </button>
                <button class="link danger" @click="remove(r)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="rows.length">
        <span class="page-info">共 {{ total }} 条</span>
        <div class="page-buttons">
          <button class="page-btn" @click="goPage(page - 1)"><span class="material-symbols-outlined">chevron_left</span></button>
          <button class="page-btn active">{{ page }}</button>
          <button class="page-btn" @click="goPage(page + 1)"><span class="material-symbols-outlined">chevron_right</span></button>
        </div>
      </div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.product-admin-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }

.filter-card { padding: $spacing-md; background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; }
.filter-row { display: flex; gap: $spacing-md; flex-wrap: wrap; }
.select, .input {
  height: 40px; padding: 0 $spacing-md; border-radius: $radius-md;
  border: 1px solid $color-outline-variant; background: #fff; font-size: $font-size-body-md; color: $color-on-surface;
}
.input { min-width: 240px; }
.btn-primary {
  display: inline-flex; align-items: center; gap: $spacing-xs; height: 40px; padding: 0 $spacing-lg;
  border: none; border-radius: $radius-md; background: $color-brand; color: #fff; font-weight: 700; cursor: pointer;
}

.table-card { background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; overflow: hidden; }
.empty-tip { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th {
  text-align: left; padding: $spacing-md; font-size: $font-size-label-sm; color: $color-on-surface-variant; font-weight: 600;
  background: $color-surface-container-low; border-bottom: 1px solid $color-outline-variant;
}
.data-table td { padding: $spacing-md; border-bottom: 1px solid $color-outline-variant; font-size: $font-size-body-md; }

.product-cell { display: flex; align-items: center; gap: $spacing-sm; }
.product-cell img, .img-stub { width: 48px; height: 48px; border-radius: $radius-default; object-fit: cover; }
.img-stub { background: $color-surface-container; display: flex; align-items: center; justify-content: center; font-size: 24px; }
.name { font-weight: 700; color: $color-on-surface; }
.id { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.sub { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.amount { font-weight: 700; color: $color-brand; }

.status-pill { display: inline-block; padding: 2px $spacing-md; border-radius: $radius-full; font-size: $font-size-label-sm; font-weight: 700; }
.status-pill.on { background: rgba(82, 196, 26, 0.15); color: $color-success; }
.status-pill.off { background: $color-surface-container; color: $color-on-surface-variant; }

.actions { display: flex; gap: $spacing-md; }
.link { background: none; border: none; color: $color-secondary; font-weight: 700; font-size: $font-size-label-md; cursor: pointer; }
.link.danger { color: $color-emergency; }
.link:hover { text-decoration: underline; }

.pagination { display: flex; align-items: center; justify-content: space-between; padding: $spacing-md $spacing-lg; border-top: 1px solid $color-outline-variant; }
.page-info { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.page-buttons { display: flex; gap: $spacing-xs; }
.page-btn {
  min-width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;
  border-radius: $radius-default; background: $color-surface-container-lowest; border: 1px solid $color-outline-variant;
  color: $color-on-surface-variant; font-size: $font-size-label-md; cursor: pointer;
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
  .material-symbols-outlined { font-size: 16px; }
}
</style>
