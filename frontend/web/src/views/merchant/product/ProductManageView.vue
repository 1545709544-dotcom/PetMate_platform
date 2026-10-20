<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'
import type { MerchantProduct } from '@/api/modules/merchant'
import { useProductCategories } from '@/composables/useProductCategories'

const router = useRouter()
const route = useRoute()
const activeTab = ref('product')
const { ensureTree, l1NameOf, topTabs } = useProductCategories()

interface KpiItem {
  label: string
  value: string
  trend?: string
  sub?: string
  color: string
}

const kpis = ref<KpiItem[]>([])

/** KPI 随 Tab 切换：商品 Tab 显示商品统计，服务 Tab 显示服务统计 */
const displayKpis = computed<KpiItem[]>(() => {
  if (activeTab.value === 'service') {
    const totalSvc = services.value.length
    const onSvc = services.value.filter((s) => s.status === 1).length
    const offSvc = totalSvc - onSvc
    const totalBooked = services.value.reduce((s, svc) => s + (svc.bookedCount || 0), 0)
    return [
      { label: '服务总数', value: String(totalSvc), sub: '项', color: 'orange' },
      { label: '上架中', value: String(onSvc), sub: `下架 ${offSvc}`, color: 'blue' },
      { label: '累计预约', value: String(totalBooked), sub: '单', color: 'gold' },
      { label: '已下架', value: String(offSvc), sub: '项', color: 'red' },
    ]
  }
  // 商品 Tab：保持原有逻辑
  return kpis.value
})
const products = ref<MerchantProduct[]>([])
const loading = ref(false)

function getInitials(name?: string): string {
  if (!name) return '?'
  const cleaned = name.replace(/\s+/g, ' ').trim()
  if (!cleaned) return '?'
  const parts = cleaned.split(' ')
  if (parts.length === 1) return parts[0].slice(0, 2)
  return parts[0][0] + parts[1][0]
}

function getAvatarColor(name?: string): string {
  const palette = ['#d4e3ff', '#ffdf9e', '#ffd6e7', '#c8f0d6', '#ffe2c2', '#dcd0ff']
  if (!name) return palette[0]
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) >>> 0
  return palette[h % palette.length]
}

function mapStatusLabel(raw: any): string {
  const v = Number(raw)
  if (v === 1) return '出售中'
  if (v === 0) return '已下架'
  return '未知'
}

function mapStatusColor(raw: any): 'green' | 'gray' {
  return Number(raw) === 1 ? 'green' : 'gray'
}

async function loadData() {
  loading.value = true
  try {
    const [kpi, stock] = await Promise.all([
      merchantApi.dashboard(),
      merchantApi.productStock(),
    ])
    // 确保分类树已加载，便于把商品 categoryId（二级）反查为一级分类名
    try { await ensureTree() } catch { /* 分类加载失败不影响商品列表 */ }

    const totalSale = stock.reduce((s, p) => s + Number(p.saleCount ?? p.sale_count ?? 0), 0)
    const onShelf = stock.filter((p) => Number(p.status) === 1).length
    const offShelf = stock.length - onShelf
    const lowStock = stock.filter((p) => Number(p.totalStock ?? p.total_stock ?? 0) <= 5).length

    kpis.value = [
      { label: '今日销售额', value: `¥${Number(kpi.todaySales).toLocaleString('zh-CN', { maximumFractionDigits: 2 })}`, trend: '', color: 'orange' },
      { label: '今日订单', value: String(kpi.todayOrders), sub: '笔', color: 'gold' },
      { label: '出售中商品', value: String(onShelf), sub: `下架 ${offShelf}`, color: 'blue' },
      { label: '库存预警', value: String(lowStock), sub: '件 ≤ 5', color: 'red' },
    ]
    products.value = stock
  } catch (e: any) {
    ElMessage.error(e?.message || '商品加载失败')
    products.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const tab = (route.query.tab as string) || 'product'
  if (tab === 'service') activeTab.value = 'service'
  loadData()
  loadServices()
})

const searchText = ref('')
const statusFilter = ref<'all' | 'on' | 'off'>('all')
const selected = ref<number[]>([])

// ============ 分类筛选（下拉箭头） ============
const categoryFilter = ref<string>('all')
const catOpen = ref(false)
const categoryTabs = computed(() => topTabs.value)
const currentCategoryLabel = computed(() => {
  const t = categoryTabs.value.find((x) => x.key === categoryFilter.value)
  return t ? t.label : '全部分类'
})
function inCategory(p: MerchantProduct, key: string): boolean {
  if (key === 'all') return true
  const cid = Number(p.categoryId)
  if (cid === Number(key)) return true
  const tab = categoryTabs.value.find((t) => t.key === key)
  return tab ? (tab.ids || []).includes(cid) : true
}

// ============ 列排序（价格 / 库存 / 销量） ============
// 改成下拉式：点列头右侧箭头 → 弹出「升序 / 降序 / 全部」三选一菜单，
// 比之前的三态循环（swap_vert→上→下）更直观，符合"看最畅销"的人体直觉。
type SortKey = '' | 'price' | 'stock' | 'sales'
const sortKey = ref<SortKey>('')
const sortOrder = ref<'asc' | 'desc'>('asc')
// 当前打开排序菜单的列；null = 全关。同一时间只允许一个排序菜单展开。
const sortMenuOpen = ref<SortKey | null>(null)

function sortField(p: MerchantProduct, key: SortKey): number {
  if (key === 'price') return Number(p.salePrice ?? p.sale_price ?? 0)
  if (key === 'stock') return Number(p.totalStock ?? p.total_stock ?? 0)
  if (key === 'sales') return Number(p.saleCount ?? p.sale_count ?? 0)
  return 0
}
function openSortMenu(key: SortKey) {
  sortMenuOpen.value = sortMenuOpen.value === key ? null : key
}
/** order='' 表示清除该列排序（"全部"） */
function applySort(key: SortKey, order: 'asc' | 'desc' | '') {
  if (order === '') {
    sortKey.value = ''
    sortOrder.value = 'asc'
  } else {
    sortKey.value = key
    sortOrder.value = order
  }
  sortMenuOpen.value = null
}
/** 表头图标：无排序=展开箭头；已排序=对应的上下箭头 */
function sortMenuIcon(key: SortKey): string {
  if (sortKey.value !== key) return 'expand_more'
  return sortOrder.value === 'asc' ? 'arrow_upward' : 'arrow_downward'
}

const filtered = computed(() => {
  let list = products.value
  if (categoryFilter.value !== 'all') list = list.filter((p) => inCategory(p, categoryFilter.value))
  if (statusFilter.value === 'on') list = list.filter((p) => Number(p.status) === 1)
  else if (statusFilter.value === 'off') list = list.filter((p) => Number(p.status) !== 1)
  const q = searchText.value.trim().toLowerCase()
  if (q) list = list.filter((p) => (p.name || '').toLowerCase().includes(q))
  return list
})

const sorted = computed(() => {
  if (!sortKey.value) return filtered.value
  const dir = sortOrder.value === 'asc' ? 1 : -1
  return [...filtered.value].sort(
    (a, b) => (sortField(a, sortKey.value) - sortField(b, sortKey.value)) * dir,
  )
})

function toggleSelect(id: number) {
  const i = selected.value.indexOf(id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(id)
}
function toggleSelectAll() {
  if (selected.value.length === sorted.value.length) selected.value = []
  else selected.value = sorted.value.map((p) => p.id)
}

async function batchToggle(targetStatus: 0 | 1) {
  if (!selected.value.length) { ElMessage.warning('请先勾选商品'); return }
  const label = targetStatus === 1 ? '上架' : '下架'
  // 只挑出状态确实需要变更的，已是目标态的跳过（省请求，确认弹窗数字也如实）
  const targets = products.value.filter(
    (p) => selected.value.includes(p.id) && Number(p.status) !== targetStatus,
  )
  if (!targets.length) {
    ElMessage.info(`选中商品已全部处于「${label}」状态`)
    selected.value = []
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认将选中的 ${targets.length} 个商品${label}？`,
      '批量操作确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch { return }
  try {
    // 必须用"幂等设值"而非"取反"：取反会把选中里已上架的商品反而下架（#9）
    await Promise.all(targets.map((p) => merchantApi.setProductStatus(p.id, targetStatus)))
    ElMessage.success(`已${label} ${targets.length} 个商品`)
    selected.value = []
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量操作失败')
    await loadData()
  }
}

function onDetail(p: MerchantProduct) {
  router.push({ path: '/merchant/product/edit', query: { id: p.id } })
}

function onCreate() {
  if (activeTab.value === 'service') router.push({ path: '/merchant/service/edit' })
  else router.push({ path: '/merchant/product/edit' })
}

async function onToggle(p: MerchantProduct) {
  try {
    await ElMessageBox.confirm(
      `确认将「${p.name}」${Number(p.status) === 1 ? '下架' : '上架'}？`,
      `${Number(p.status) === 1 ? '下架' : '上架'}确认`,
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    // 幂等设值：以页面上看到的状态推导目标态，避免列表数据陈旧时"取反"取错方向
    const next: 0 | 1 = Number(p.status) === 1 ? 0 : 1
    await merchantApi.setProductStatus(p.id, next)
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ============ 服务管理（合并到本页「服务」Tab） ============
const services = ref<any[]>([])
const serviceCount = computed(() => services.value.length)

async function loadServices() {
  try {
    const rows = await merchantApi.services()
    services.value = (rows || []).map((r: any) => ({
      id: Number(r.id),
      name: String(r.name || ''),
      category: String(r.category || ''),
      price: Number(r.price || 0),
      duration: Number(r.duration || 0),
      status: Number(r.status || 0),
      bookedCount: Number(r.booked_count ?? 0),
      cover: r.cover || null,
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '服务加载失败')
    services.value = []
  }
}

const serviceTabFiltered = computed(() => {
  let list = services.value
  if (statusFilter.value === 'on') list = list.filter((s) => s.status === 1)
  else if (statusFilter.value === 'off') list = list.filter((s) => s.status !== 1)
  const q = searchText.value.trim().toLowerCase()
  if (q) list = list.filter((s) => (s.name || '').toLowerCase().includes(q))
  return list
})

function serviceStatusText(s: any): string {
  return s.status === 1 ? '上架中' : '已下架'
}

function onServiceCreate() {
  router.push({ path: '/merchant/service/edit' })
}
function onServiceEdit(s: any) {
  router.push({ path: '/merchant/service/edit', query: { id: s.id } })
}
async function onServiceToggle(s: any) {
  try {
    const next = s.status === 1 ? 0 : 1
    await merchantApi.updateService(s.id, { status: next })
    ElMessage.success(next === 1 ? '已上架' : '已下架')
    await loadServices()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}
async function onServiceDelete(s: any) {
  try {
    await ElMessageBox.confirm(`确认删除服务「${s.name}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await merchantApi.deleteService(s.id)
    ElMessage.success('已删除')
    await loadServices()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}
</script>

<template>
  <div class="product-mgmt-view">
    <!-- 标题 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">商品与服务管理</h1>
        <p class="page-sub">统一管理实物商品与宠物护理服务项目，掌握库存、销量与服务预约情况。</p>
      </div>
      <div class="head-actions">
        <button class="btn-outline" @click="onCreate">
          <span class="material-symbols-outlined">add</span>
          {{ activeTab === 'service' ? '新增服务' : '新增商品' }}
        </button>
      </div>
    </div>

    <!-- Tabs（分段控件 + 计数） -->
    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'product' }" @click="activeTab = 'product'">
        <span class="material-symbols-outlined">inventory_2</span>
        商品
        <span class="tab-count">{{ products.length }}</span>
      </button>
      <button class="tab" :class="{ active: activeTab === 'service' }" @click="activeTab = 'service'">
        <span class="material-symbols-outlined">handyman</span>
        服务
        <span class="tab-count">{{ serviceCount }}</span>
      </button>
    </div>

    <!-- KPI（随 Tab 切换内容） -->
    <div class="kpi-grid">
      <div v-for="k in displayKpis" :key="k.label" class="kpi-card" :class="`border-${k.color}`">
        <p class="kpi-label">{{ k.label }}</p>
        <div class="kpi-value-row">
          <h3 class="kpi-value">{{ k.value }}</h3>
          <span v-if="k.trend" class="kpi-trend" :class="`text-${k.color}`">{{ k.trend }}</span>
          <span v-if="k.sub" class="kpi-sub">{{ k.sub }}</span>
        </div>
      </div>
    </div>

    <!-- 商品区：搜索 + 筛选 + 批量 -->
    <template v-if="activeTab === 'product'">
    <div class="filter-bar">
      <input v-model="searchText" class="search-input" placeholder="搜索商品名称..." />
      <div class="filter-group">
        <button class="select-btn" :class="{ active: statusFilter === 'all' }" @click="statusFilter = 'all'">全部</button>
        <button class="select-btn" :class="{ active: statusFilter === 'on' }" @click="statusFilter = 'on'">出售中</button>
        <button class="select-btn" :class="{ active: statusFilter === 'off' }" @click="statusFilter = 'off'">已下架</button>
      </div>

      <!-- 分类筛选下拉（右侧小箭头） -->
      <div class="cat-select" :class="{ open: catOpen }">
        <button class="cat-trigger" @click="catOpen = !catOpen">
          <span class="cat-label">{{ currentCategoryLabel }}</span>
          <span class="material-symbols-outlined cat-arrow" :class="{ up: catOpen }">expand_more</span>
        </button>
        <div class="cat-menu" v-if="catOpen">
          <div
            v-for="t in categoryTabs"
            :key="t.key"
            class="cat-item"
            :class="{ active: categoryFilter === t.key }"
            @click="categoryFilter = t.key; catOpen = false"
          >
            <span class="material-symbols-outlined" v-if="categoryFilter === t.key">check</span>
            <span v-else class="cat-dot"></span>
            {{ t.label }}
          </div>
        </div>
      </div>
      <div class="dropdown-backdrop" v-if="catOpen" @click="catOpen = false"></div>
      <div class="dropdown-backdrop" v-if="sortMenuOpen" @click="sortMenuOpen = null"></div>
      <div class="batch-group" v-if="selected.length">
        <span class="batch-count">已选 {{ selected.length }} 件</span>
        <button class="batch-btn" @click="batchToggle(1)">批量上架</button>
        <button class="batch-btn" @click="batchToggle(0)">批量下架</button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th class="col-check"><input type="checkbox" :checked="selected.length === sorted.length && sorted.length > 0" @change="toggleSelectAll" /></th>
            <th>商品</th>
            <th>分类</th>
            <th class="sortable" :class="{ active: sortKey === 'price' }" @click="openSortMenu('price')">
              <span class="th-label">价格</span>
              <button
                type="button"
                class="sort-trigger"
                :class="{ open: sortMenuOpen === 'price' }"
                @click.stop="openSortMenu('price')"
                aria-label="价格排序"
              >
                <span class="material-symbols-outlined sort-ico">{{ sortMenuIcon('price') }}</span>
              </button>
              <div class="sort-menu" v-if="sortMenuOpen === 'price'" @click.stop>
                <div
                  class="sort-item"
                  :class="{ active: sortKey === 'price' && sortOrder === 'asc' }"
                  @click="applySort('price', 'asc')"
                >
                  <span class="material-symbols-outlined item-ico">arrow_upward</span>
                  升序
                </div>
                <div
                  class="sort-item"
                  :class="{ active: sortKey === 'price' && sortOrder === 'desc' }"
                  @click="applySort('price', 'desc')"
                >
                  <span class="material-symbols-outlined item-ico">arrow_downward</span>
                  降序
                </div>
                <div
                  class="sort-item"
                  :class="{ active: sortKey !== 'price' }"
                  @click="applySort('price', '')"
                >
                  <span class="material-symbols-outlined item-ico">unfold_more</span>
                  全部
                </div>
              </div>
            </th>
            <th class="sortable" :class="{ active: sortKey === 'stock' }" @click="openSortMenu('stock')">
              <span class="th-label">库存</span>
              <button
                type="button"
                class="sort-trigger"
                :class="{ open: sortMenuOpen === 'stock' }"
                @click.stop="openSortMenu('stock')"
                aria-label="库存排序"
              >
                <span class="material-symbols-outlined sort-ico">{{ sortMenuIcon('stock') }}</span>
              </button>
              <div class="sort-menu" v-if="sortMenuOpen === 'stock'" @click.stop>
                <div
                  class="sort-item"
                  :class="{ active: sortKey === 'stock' && sortOrder === 'asc' }"
                  @click="applySort('stock', 'asc')"
                >
                  <span class="material-symbols-outlined item-ico">arrow_upward</span>
                  升序
                </div>
                <div
                  class="sort-item"
                  :class="{ active: sortKey === 'stock' && sortOrder === 'desc' }"
                  @click="applySort('stock', 'desc')"
                >
                  <span class="material-symbols-outlined item-ico">arrow_downward</span>
                  降序
                </div>
                <div
                  class="sort-item"
                  :class="{ active: sortKey !== 'stock' }"
                  @click="applySort('stock', '')"
                >
                  <span class="material-symbols-outlined item-ico">unfold_more</span>
                  全部
                </div>
              </div>
            </th>
            <th class="sortable" :class="{ active: sortKey === 'sales' }" @click="openSortMenu('sales')">
              <span class="th-label">销量/浏览</span>
              <button
                type="button"
                class="sort-trigger"
                :class="{ open: sortMenuOpen === 'sales' }"
                @click.stop="openSortMenu('sales')"
                aria-label="销量排序"
              >
                <span class="material-symbols-outlined sort-ico">{{ sortMenuIcon('sales') }}</span>
              </button>
              <div class="sort-menu" v-if="sortMenuOpen === 'sales'" @click.stop>
                <div
                  class="sort-item"
                  :class="{ active: sortKey === 'sales' && sortOrder === 'asc' }"
                  @click="applySort('sales', 'asc')"
                >
                  <span class="material-symbols-outlined item-ico">arrow_upward</span>
                  升序
                </div>
                <div
                  class="sort-item"
                  :class="{ active: sortKey === 'sales' && sortOrder === 'desc' }"
                  @click="applySort('sales', 'desc')"
                >
                  <span class="material-symbols-outlined item-ico">arrow_downward</span>
                  降序
                </div>
                <div
                  class="sort-item"
                  :class="{ active: sortKey !== 'sales' }"
                  @click="applySort('sales', '')"
                >
                  <span class="material-symbols-outlined item-ico">unfold_more</span>
                  全部
                </div>
              </div>
            </th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody v-if="loading">
          <tr><td colspan="8" class="state-cell">加载中...</td></tr>
        </tbody>
        <tbody v-else-if="sorted.length === 0">
          <tr><td colspan="8" class="state-cell">暂无商品</td></tr>
        </tbody>
        <tbody v-else>
          <tr v-for="p in sorted" :key="p.id">
            <td class="col-check"><input type="checkbox" :checked="selected.includes(p.id)" @change="toggleSelect(p.id)" /></td>
            <td>
              <div class="product-cell">
                <span class="product-placeholder" v-if="!p.mainImage">
                  <span class="material-symbols-outlined">image</span>
                </span>
                <img v-else :src="p.mainImage" :alt="p.name" />
                <div>
                  <div class="product-name">{{ p.name }}</div>
                  <div class="product-id">ID: {{ p.id }}</div>
                </div>
              </div>
            </td>
            <td>{{ l1NameOf(p.categoryId) || '—' }}</td>
            <td class="amount">¥{{ Number(p.salePrice ?? p.sale_price ?? 0).toFixed(2) }}</td>
            <td>
              <span :class="{ low: Number(p.totalStock ?? 0) <= 5 }">
                {{ p.totalStock ?? 0 }} <span class="sku-count" v-if="p.skuCount">/ {{ p.skuCount }} SKU</span>
              </span>
            </td>
            <td>
              <div class="product-metrics">
                销量 {{ p.saleCount ?? p.sale_count ?? 0 }}
                <div class="metric-sub">浏览 {{ p.viewCount ?? p.view_count ?? 0 }}</div>
              </div>
            </td>
            <td>
              <span class="status-pill" :class="mapStatusColor(p.status)">{{ mapStatusLabel(p.status) }}</span>
            </td>
            <td>
              <div class="row-actions">
                <a class="action-detail" @click="onDetail(p)">编辑</a>
                <a class="action-detail" @click="onToggle(p)">
                  {{ Number(p.status) === 1 ? '下架' : '上架' }}
                </a>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    </template>

    <!-- 服务区：卡片网格 -->
    <template v-else>
      <div class="filter-bar simple">
        <input v-model="searchText" class="search-input" placeholder="搜索服务名称..." />
        <div class="filter-group">
          <button class="select-btn" :class="{ active: statusFilter === 'all' }" @click="statusFilter = 'all'">全部</button>
          <button class="select-btn" :class="{ active: statusFilter === 'on' }" @click="statusFilter = 'on'">上架中</button>
          <button class="select-btn" :class="{ active: statusFilter === 'off' }" @click="statusFilter = 'off'">已下架</button>
        </div>
      </div>

      <div class="service-section">
        <div v-if="loading" class="grid-state">
          <span class="material-symbols-outlined spin">progress_activity</span> 加载中...
        </div>
        <div v-else-if="serviceTabFiltered.length === 0" class="grid-state">
          <span class="material-symbols-outlined">spa</span>
          <p>暂未发布服务，点击右上角「新增服务」开始</p>
        </div>
        <div v-else class="service-grid">
          <div v-for="s in serviceTabFiltered" :key="s.id" class="service-card">
            <div class="sc-cover">
              <img v-if="s.cover" :src="s.cover" :alt="s.name" />
              <span v-else class="sc-placeholder"><span class="material-symbols-outlined">spa</span></span>
              <span class="sc-status" :class="s.status === 1 ? 'green' : 'gray'">{{ serviceStatusText(s) }}</span>
            </div>
            <div class="sc-body">
              <div class="sc-name">{{ s.name }}</div>
              <div class="sc-meta">ID: {{ s.id }}</div>
              <div class="sc-tags">
                <span class="sc-tag">{{ s.category || '未分类' }}</span>
                <span class="sc-tag outline">{{ s.duration }} 分钟</span>
              </div>
              <div class="sc-foot">
                <div class="sc-price">¥{{ s.price.toFixed(2) }}</div>
                <div class="sc-booked">已预约 {{ s.bookedCount }}</div>
              </div>
            </div>
            <div class="sc-actions">
              <button class="sc-btn" @click="onServiceEdit(s)">编辑</button>
              <button class="sc-btn" @click="onServiceToggle(s)">{{ s.status === 1 ? '下架' : '上架' }}</button>
              <button class="sc-btn danger" @click="onServiceDelete(s)">删除</button>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.product-mgmt-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

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

// ============ Tabs（分段控件 + 计数） ============
.tabs {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  background: $color-surface-container-low;
  border-radius: $radius-full;
  align-self: flex-start;
}

.tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border: none;
  border-radius: $radius-full;
  background: transparent;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: background 0.2s ease, color 0.2s ease;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    color: $color-brand;
  }

  &.active {
    background: $color-surface-container-lowest;
    color: $color-brand;
    box-shadow: $shadow-sm;
  }

  .tab-count {
    min-width: 20px;
    height: 20px;
    padding: 0 6px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 10px;
    background: $color-surface-container;
    color: $color-on-surface-variant;
    font-size: $font-size-label-sm;
    font-weight: 700;
  }

  &.active .tab-count {
    background: $color-brand-container;
    color: $color-brand;
  }
}

// ============ 服务卡片网格 ============
.filter-bar.simple {
  margin-top: 0;
}

.service-section {
  margin-top: $spacing-xs;
}

.service-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: $spacing-md;
}

.grid-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-2xl;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 40px;
    opacity: 0.5;
  }
  p {
    margin: 0;
  }
  .spin {
    animation: spin 1s linear infinite;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.service-card {
  display: flex;
  flex-direction: column;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  overflow: hidden;
  border: 1px solid transparent;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: $shadow-md;
    border-color: rgba($color-brand, 0.25);
  }
}

.sc-cover {
  position: relative;
  height: 150px;
  background: $color-surface-container-low;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.sc-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: $color-on-surface-variant;
  .material-symbols-outlined {
    font-size: 44px;
  }
}

.sc-status {
  position: absolute;
  top: 10px;
  left: 10px;
  padding: 3px 10px;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  backdrop-filter: blur(4px);

  &.green {
    background: rgba(82, 196, 26, 0.92);
    color: #fff;
  }
  &.gray {
    background: rgba(0, 0, 0, 0.5);
    color: #fff;
  }
}

.sc-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: $spacing-md;
  flex: 1;
}

.sc-name {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.sc-meta {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.sc-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.sc-tag {
  padding: 2px 10px;
  border-radius: $radius-full;
  background: $color-brand-container;
  color: $color-brand;
  font-size: $font-size-label-sm;
  font-weight: 600;

  &.outline {
    background: transparent;
    border: 1px solid $color-outline-variant;
    color: $color-on-surface-variant;
  }
}

.sc-foot {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: auto;
  padding-top: $spacing-sm;
}

.sc-price {
  font-size: $font-size-headline-md;
  font-weight: 800;
  color: $color-brand;
}

.sc-booked {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.sc-actions {
  display: flex;
  border-top: 1px solid $color-outline-variant;

  .sc-btn {
    flex: 1;
    padding: $spacing-sm;
    border: none;
    background: transparent;
    font-size: $font-size-label-md;
    font-weight: 600;
    color: $color-brand;
    cursor: pointer;
    transition: background 0.15s ease;

    & + .sc-btn {
      border-left: 1px solid $color-outline-variant;
    }
    &:hover {
      background: $color-surface-container-low;
    }
    &.danger {
      color: $color-emergency;
    }
  }
}

// ============ KPI ============
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include tablet {
    grid-template-columns: repeat(4, 1fr);
  }
}

.kpi-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border-top: 4px solid;

  &.border-orange { border-top-color: $color-brand; }
  &.border-blue { border-top-color: $color-secondary; }
  &.border-gold { border-top-color: $color-warning; }
  &.border-red { border-top-color: $color-emergency; }
}

.kpi-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-sm;
}

.kpi-value-row {
  display: flex;
  align-items: baseline;
  gap: $spacing-sm;
}

.kpi-value {
  font-family: $font-family-headline;
  font-size: 32px;
  font-weight: 700;
  color: $color-on-surface;
}

.kpi-trend {
  font-size: $font-size-label-sm;
  font-weight: 700;

  &.text-orange { color: $color-success; }
  &.text-blue { color: $color-secondary; }
  &.text-gold { color: $color-warning; }
  &.text-red { color: $color-emergency; }
}

.kpi-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

// ============ Filter Bar ============
.filter-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}

.filter-group {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.filter-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

.select-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  background: #ffffff;
  font-size: $font-size-label-md;
  cursor: pointer;
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
}

// ============ 分类筛选下拉 ============
.cat-select {
  position: relative;
  display: inline-block;
}
.cat-trigger {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  background: #ffffff;
  font-size: $font-size-label-md;
  color: $color-on-surface;
  cursor: pointer;
  min-width: 132px;
  justify-content: space-between;
  &:hover { border-color: $color-brand; }
  .cat-label { font-weight: 600; }
}
.cat-arrow {
  font-size: 18px;
  color: $color-on-surface-variant;
  transition: transform 0.2s ease;
  &.up { transform: rotate(180deg); }
}
.cat-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 0;
  z-index: 30;
  min-width: 100%;
  max-height: 320px;
  overflow-y: auto;
  padding: $spacing-xs;
  background: #ffffff;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  box-shadow: $shadow-md;
}
.cat-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-xs $spacing-md;
  border-radius: $radius-sm;
  font-size: $font-size-label-md;
  color: $color-on-surface;
  cursor: pointer;
  white-space: nowrap;
  &:hover { background: $color-surface-container-low; }
  &.active { color: $color-brand; font-weight: 700; }
  .material-symbols-outlined { font-size: 18px; }
}
.cat-dot { width: 18px; height: 18px; display: inline-block; }
.dropdown-backdrop {
  position: fixed;
  inset: 0;
  z-index: 20;
}

// ============ 可排序表头（点击列头/箭头 → 弹出升序/降序/全部 三选一菜单） ============
.sortable {
  position: relative;
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
  .th-label { vertical-align: middle; }
  // 当前列已排序：label 跟随主题色
  &.active .th-label { color: $color-brand; }
}
// 排序触发按钮：仅图标部分可点，文本 label 不再承担点击
.sort-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-left: 2px;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: $radius-sm;
  cursor: pointer;
  vertical-align: middle;
  transition: background 0.15s ease;
  .sort-ico {
    font-size: 18px;
    color: $color-on-surface-variant;
    opacity: 0.55;
    transition: transform 0.2s ease, opacity 0.15s ease, color 0.15s ease;
  }
  &:hover { background: $color-surface-container-low; }
  &:hover .sort-ico { opacity: 0.95; }
  // 当前列已排序：图标高亮；菜单展开：箭头旋转 180° 提示下拉
  &.open .sort-ico,
  .sortable.active & .sort-ico {
    color: $color-brand;
    opacity: 1;
  }
  &.open .sort-ico { transform: rotate(180deg); }
}
// 排序下拉菜单：定位在表头下方居中
.sort-menu {
  position: absolute;
  top: calc(100% + 6px);
  left: 50%;
  transform: translateX(-50%);
  z-index: 30;
  min-width: 110px;
  padding: $spacing-xs;
  background: #ffffff;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  box-shadow: $shadow-md;
}
.sort-item {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 6px $spacing-sm;
  border-radius: $radius-sm;
  font-size: $font-size-label-md;
  color: $color-on-surface;
  cursor: pointer;
  white-space: nowrap;
  transition: background 0.15s ease;
  .item-ico { font-size: 16px; color: $color-on-surface-variant; }
  &:hover { background: $color-surface-container-low; }
  &.active {
    color: $color-brand;
    font-weight: 600;
    .item-ico { color: $color-brand; }
  }
}

.search-input {
  flex: 1;
  min-width: 200px;
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  background: #ffffff;
  font-size: $font-size-body-md;
  font-family: inherit;
  color: $color-on-surface;
  outline: none;
  &:focus { border-color: $color-brand; }
}

.col-check { width: 40px; text-align: center; }
.batch-group {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-left: auto;
}
.batch-count { font-size: $font-size-label-sm; color: $color-brand; font-weight: 700; }
.batch-btn {
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-brand;
  border-radius: $radius-md;
  background: transparent;
  color: $color-brand;
  font-size: $font-size-label-sm;
  font-weight: 600;
  cursor: pointer;
  &:hover { background: $color-brand; color: #fff; }
}

.filter-icon {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-secondary;
  background: rgba(0, 96, 172, 0.1);
  border-radius: $radius-md;
  cursor: pointer;
}

.filter-advanced {
  margin-left: auto;
  font-size: $font-size-label-md;
  color: $color-secondary;
  font-weight: 700;
  cursor: pointer;
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

.order-id {
  font-weight: 700;
  color: $color-on-surface;
}

.order-time {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 2px;
}

.product-cell {
  display: flex;
  align-items: center;
  gap: $spacing-sm;

  img {
    width: 48px;
    height: 48px;
    border-radius: $radius-default;
    object-fit: cover;
  }
}

.product-placeholder {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: $radius-default;
  background: $color-surface-container;
  color: $color-on-surface-variant;
  flex-shrink: 0;

  .material-symbols-outlined {
    font-size: 22px;
  }
}

.product-name {
  font-weight: 700;
  color: $color-on-surface;
}

.product-id {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.product-metrics {
  font-size: $font-size-body-md;
  .metric-sub {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    margin-top: 2px;
  }
}

.sku-count {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.state-cell {
  text-align: center;
  padding: $spacing-2xl !important;
  color: $color-on-surface-variant;
}

.amount {
  font-weight: 700;
  color: $color-brand;
}

.low {
  color: $color-warning;
  font-weight: 700;
}

.status-pill {
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;

  &.blue { background: $color-secondary-container; color: $color-secondary; }
  &.gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.green { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.cyan { background: rgba(32, 178, 178, 0.15); color: #20b2b2; }
  &.red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
  &.gray { background: $color-surface-container; color: $color-on-surface-variant; }
}

.action-detail {
  font-size: $font-size-label-md;
  color: $color-secondary;
  font-weight: 700;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

.row-actions {
  display: flex;
  gap: $spacing-md;
}
</style>