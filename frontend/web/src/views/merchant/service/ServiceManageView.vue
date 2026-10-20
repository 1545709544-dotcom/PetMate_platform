<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'
import type { MerchantProduct } from '@/api/modules/merchant'

const router = useRouter()
const route = useRoute()

interface Service {
  id: number
  name: string
  category: string
  price: number
  duration: number
  status: number
  bookedCount: number
  cover: string | null
}

const activeTab = ref('product')
const searchText = ref('')
const statusFilter = ref<'all' | 'on' | 'off'>('all')

const products = ref<MerchantProduct[]>([])
const services = ref<Service[]>([])
const selected = ref<number[]>([])
const loading = ref(false)

const filteredProducts = computed(() => {
  let list = products.value
  if (statusFilter.value === 'on') list = list.filter((p) => Number(p.status) === 1)
  else if (statusFilter.value === 'off') list = list.filter((p) => Number(p.status) !== 1)
  const q = searchText.value.trim().toLowerCase()
  if (q) list = list.filter((p) => (p.name || '').toLowerCase().includes(q))
  return list
})

function productStatusLabel(p: MerchantProduct): string {
  return Number(p.status) === 1 ? '出售中' : '已下架'
}
function productStatusClass(p: MerchantProduct): string {
  return Number(p.status) === 1 ? 'green' : 'gray'
}

async function loadProducts() {
  loading.value = true
  try {
    products.value = await merchantApi.productStock()
  } catch (e: any) {
    ElMessage.error(e?.message || '商品加载失败')
    products.value = []
  } finally {
    loading.value = false
  }
}

async function loadServices() {
  loading.value = true
  try {
    const rows = await merchantApi.services()
    services.value = (rows || []).map((r: any) => ({
      id: Number(r.id),
      name: String(r.name),
      category: String(r.category),
      price: Number(r.price),
      duration: Number(r.duration),
      status: Number(r.status),
      bookedCount: Number(r.booked_count ?? 0),
      cover: r.cover || null,
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '服务加载失败')
    services.value = []
  } finally {
    loading.value = false
  }
}

function reload() {
  if (activeTab.value === 'product') loadProducts()
  else loadServices()
}

onMounted(() => {
  const tab = (route.query.tab as string) || 'product'
  activeTab.value = tab === 'service' ? 'service' : 'product'
  if (activeTab.value === 'product') loadProducts()
  else loadServices()
})

function toggleAll() {
  if (selected.value.length === products.value.length) selected.value = []
  else selected.value = products.value.map(p => p.id)
}

function toggleOne(id: number) {
  const i = selected.value.indexOf(id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(id)
}

async function remove(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该商品？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await merchantApi.deleteProduct(id)
    ElMessage.success('商品已删除')
    await loadProducts()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function onEdit(p: MerchantProduct) {
  router.push({ path: '/merchant/product/edit', query: { id: p.id } })
}

async function onToggleStatus(p: MerchantProduct) {
  try {
    await merchantApi.toggleProductStatus(p.id)
    ElMessage.success('状态已切换')
    await loadProducts()
  } catch (e: any) {
    ElMessage.error(e?.message || '切换失败')
  }
}

function onPublish() {
  router.push({ path: '/merchant/product/edit' })
}

// ============ 服务编辑 ============

function onServiceCreate() {
  router.push({ path: '/merchant/service/edit' })
}

function onServiceEdit(s: Service) {
  router.push({ path: '/merchant/service/edit', query: { id: s.id } })
}

async function onServiceToggle(s: Service) {
  try {
    const next = s.status === 1 ? 0 : 1
    await merchantApi.updateService(s.id, { status: next })
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function onServiceDelete(s: Service) {
  try {
    await ElMessageBox.confirm(`确认删除服务「${s.name}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await merchantApi.deleteService(s.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

const totalStockValue = computed(() =>
  products.value.reduce((s, p) => s + Number(p.totalStock ?? 0), 0),
)
const onShelfCount = computed(() =>
  products.value.filter(p => Number(p.status) === 1).length,
)
const lowStockCount = computed(() =>
  products.value.filter(p => Number(p.totalStock ?? 0) <= 5 && Number(p.totalStock ?? 0) > 0).length,
)
</script>

<template>
  <div class="service-mgmt-view">
    <!-- 搜索筛选 -->
    <div class="filter-bar">
      <div class="search-box">
        <span class="material-symbols-outlined">search</span>
        <input v-model="searchText" class="search-input" placeholder="搜索商品名称..." />
      </div>
      <button class="select-btn" :class="{ active: statusFilter === 'all' }" @click="statusFilter = 'all'">全部</button>
      <button class="select-btn" :class="{ active: statusFilter === 'on' }" @click="statusFilter = 'on'">出售中</button>
      <button class="select-btn" :class="{ active: statusFilter === 'off' }" @click="statusFilter = 'off'">已下架</button>
    </div>

    <!-- 标题 + 操作 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">商品与服务管理</h1>
        <p class="page-sub">管理您店铺内的实物商品与宠物护理服务项目。</p>
      </div>
      <div class="head-actions">
        <button v-if="activeTab === 'product'" class="btn-primary" @click="onPublish">
          <span class="material-symbols-outlined">add</span>
          发布新商品
        </button>
      </div>
    </div>

    <!-- Tabs -->
    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'product' }" @click="activeTab = 'product'; reload()">商品管理</button>
      <button class="tab" :class="{ active: activeTab === 'service' }" @click="activeTab = 'service'; reload()">服务管理</button>
    </div>

    <!-- 商品表格 -->
    <div v-if="activeTab === 'product'" class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th class="checkbox-col">
              <input type="checkbox" :checked="!loading && products.length > 0 && selected.length === products.length" :disabled="loading || products.length === 0" @change="toggleAll" />
            </th>
            <th>商品信息</th>
            <th>分类</th>
            <th>价格</th>
            <th>库存</th>
            <th>销量/浏览</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody v-if="loading">
          <tr><td colspan="8" class="state-cell">加载中...</td></tr>
        </tbody>
        <tbody v-else-if="products.length === 0">
          <tr><td colspan="8" class="state-cell">暂无商品</td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="p in filteredProducts" :key="p.id">
            <td class="checkbox-col">
              <input type="checkbox" :checked="selected.includes(p.id)" @change="toggleOne(p.id)" />
            </td>
            <td>
              <div class="product-cell">
                <span v-if="!p.mainImage" class="product-placeholder">
                  <span class="material-symbols-outlined">image</span>
                </span>
                <img v-else :src="p.mainImage" :alt="p.name" />
                <div>
                  <div class="product-name">{{ p.name }}</div>
                  <div class="product-sku">ID: {{ p.id }}</div>
                </div>
              </div>
            </td>
            <td>{{ p.categoryName || '—' }}</td>
            <td class="price">¥{{ Number(p.salePrice ?? 0).toFixed(2) }}</td>
            <td :class="{ low: Number(p.totalStock ?? 0) <= 5 }">
              {{ p.totalStock ?? 0 }} <span class="sku-count" v-if="p.skuCount">/ {{ p.skuCount }} SKU</span>
            </td>
            <td class="metrics">
              <div>销量 {{ p.saleCount ?? 0 }}</div>
              <div class="metric-sub">浏览 {{ p.viewCount ?? 0 }}</div>
            </td>
            <td>
              <span class="status-pill" :class="productStatusClass(p)">{{ productStatusLabel(p) }}</span>
            </td>
            <td class="action-col">
              <a class="action-edit" @click="onEdit(p)">编辑</a>
              <a class="action-edit" @click="onToggleStatus(p)">上下架</a>
              <a class="action-del" @click="remove(p.id)">删除</a>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <span class="page-info">共 {{ products.length }} 项 · 出售中 {{ onShelfCount }} · 库存预警 {{ lowStockCount }} · 库存总数 {{ totalStockValue }}</span>
      </div>
    </div>

    <!-- 服务表格 -->
    <div v-else class="table-card">
      <div class="service-head">
        <h2 class="sub-title">服务列表</h2>
        <button class="btn-primary" @click="onServiceCreate">
          <span class="material-symbols-outlined">add</span>
          新增服务
        </button>
      </div>
      <table class="data-table">
        <thead>
          <tr>
            <th>服务名称</th>
            <th>分类</th>
            <th>价格</th>
            <th>时长</th>
            <th>累计预约</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody v-if="loading">
          <tr><td colspan="7" class="state-cell">加载中...</td></tr>
        </tbody>
        <tbody v-else-if="services.length === 0">
          <tr><td colspan="7" class="state-cell">暂未发布服务</td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="s in services" :key="s.id">
            <td>
              <div class="service-cell">
                <span v-if="!s.cover" class="product-placeholder">
                  <span class="material-symbols-outlined">spa</span>
                </span>
                <img v-else :src="s.cover" :alt="s.name" />
                <div>
                  <div class="product-name">{{ s.name }}</div>
                  <div class="product-sku">ID: {{ s.id }}</div>
                </div>
              </div>
            </td>
            <td>{{ s.category || '—' }}</td>
            <td class="price">¥{{ s.price.toFixed(2) }}</td>
            <td>{{ s.duration }} 分钟</td>
            <td>{{ s.bookedCount }}</td>
            <td>
              <span class="status-pill" :class="s.status === 1 ? 'green' : 'gray'">
                {{ s.status === 1 ? '上架中' : '已下架' }}
              </span>
            </td>
            <td>
              <div class="row-actions">
                <a class="action-edit" @click="onServiceEdit(s)">编辑</a>
                <a class="action-edit" @click="onServiceToggle(s)">
                  {{ s.status === 1 ? '下架' : '上架' }}
                </a>
                <a class="action-del" @click="onServiceDelete(s)">删除</a>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.service-mgmt-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

// ============ Filter Bar ============
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.search-box {
  flex: 1;
  min-width: 240px;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-full;

  .material-symbols-outlined {
    color: $color-on-surface-variant;
  }
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: $font-size-body-md;
}

.select-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  cursor: pointer;
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
}

.filter-btn {
  padding: $spacing-xs $spacing-lg;
  background: $color-on-surface;
  color: $color-surface;
  border-radius: $radius-md;
  font-weight: 600;
}

// ============ Page Head ============
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
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

.head-actions {
  display: flex;
  gap: $spacing-sm;
}

.btn-primary,
.btn-outline {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
}

.btn-primary {
  background: $color-brand;
  color: #ffffff;
}

.btn-outline {
  background: transparent;
  border: 1px solid $color-brand;
  color: $color-brand;
}

// ============ Tabs ============
.tabs {
  display: flex;
  gap: $spacing-lg;
  border-bottom: 1px solid $color-outline-variant;
}

.tab {
  position: relative;
  padding: $spacing-sm 0;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface-variant;
  cursor: pointer;

  &.active {
    color: $color-brand;

    &::after {
      content: '';
      position: absolute;
      bottom: -1px;
      left: 0;
      right: 0;
      height: 3px;
      background: $color-brand;
      border-radius: $radius-full;
    }
  }
}

// ============ Table ============
.table-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;

  th {
    text-align: left;
    padding: $spacing-md;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    font-weight: 600;
    background: $color-surface-container-low;
    border-bottom: 1px solid $color-outline-variant;
  }

  td {
    padding: $spacing-md;
    border-bottom: 1px solid $color-outline-variant;
    font-size: $font-size-body-md;
  }
}

.checkbox-col {
  width: 40px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: $spacing-md;

  img {
    width: 64px;
    height: 64px;
    border-radius: $radius-default;
    object-fit: cover;
  }
}

.product-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  border-radius: $radius-default;
  background: $color-surface-container;
  color: $color-on-surface-variant;
  flex-shrink: 0;

  .material-symbols-outlined {
    font-size: 28px;
  }
}

.sku-count {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  font-weight: 400;
}

.low {
  color: $color-warning;
  font-weight: 700;
}

.service-cell {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  img {
    width: 64px;
    height: 64px;
    border-radius: $radius-default;
    object-fit: cover;
  }
}

.metrics {
  font-size: $font-size-body-md;

  .metric-sub {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    margin-top: 2px;
  }
}

.state-cell {
  text-align: center;
  padding: $spacing-2xl !important;
  color: $color-on-surface-variant;
}

.product-name {
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.product-sku {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.price {
  color: $color-brand;
  font-weight: 700;
}

.low {
  color: $color-warning;
  font-weight: 700;
}

.status-pill {
  padding: 4px $spacing-sm;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;

  &.green { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.gray { background: $color-surface-container; color: $color-on-surface-variant; }
}

.action-col {
  display: flex;
  gap: $spacing-md;
}

.action-edit {
  font-size: $font-size-label-md;
  color: $color-brand;
  cursor: pointer;
}

.action-del {
  font-size: $font-size-label-md;
  color: $color-emergency;
  cursor: pointer;
}

.row-actions {
  display: flex;
  gap: $spacing-md;
}

.service-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
}

.sub-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

// ============ Pagination ============
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md $spacing-lg;
  border-top: 1px solid $color-outline-variant;
}

.page-info {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.page-buttons {
  display: flex;
  gap: $spacing-xs;
}

.page-btn {
  min-width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 $spacing-sm;
  border-radius: $radius-default;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  cursor: pointer;

  &.active {
    background: $color-brand;
    color: #ffffff;
    border-color: $color-brand;
  }

  .material-symbols-outlined {
    font-size: 16px;
  }
}
</style>