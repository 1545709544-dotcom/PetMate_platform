<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

const router = useRouter()

const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref('all')
const orders = ref<any[]>([])
const drawer = ref(false)
const current = ref<any>(null)

const STATUS_MAP: Record<string, { label: string; cls: string }> = {
  pending_pay: { label: '待付款', cls: 'pending' },
  paid: { label: '已付款', cls: 'paid' },
  shipping: { label: '待收货', cls: 'shipping' },
  done: { label: '已完成', cls: 'done' },
  cancelled: { label: '已取消', cls: 'cancelled' },
  refunding: { label: '退款中', cls: 'refunding' },
  refunded: { label: '已退款', cls: 'refunded' },
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const STATUS_OPTIONS = [
  { v: 'all', label: '全部状态' },
  { v: 'pending_pay', label: '待付款' },
  { v: 'paid', label: '已付款' },
  { v: 'shipping', label: '待收货' },
  { v: 'done', label: '已完成' },
  { v: 'cancelled', label: '已取消' },
  { v: 'refunding', label: '退款中' },
  { v: 'refunded', label: '已退款' },
]

async function load() {
  loading.value = true
  try {
    const res = await adminApi.orderList({
      page: page.value,
      size: size.value,
      status: statusFilter.value === 'all' ? '' : statusFilter.value,
      keyword: keyword.value.trim(),
    })
    orders.value = (res.list || []) as any[]
    total.value = res.total ?? orders.value.length
  } catch (e: any) {
    ElMessage.error(e?.message || '加载订单列表失败')
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function openDetail(o: any) {
  current.value = o
  drawer.value = true
}

function openInUserView(o: any) {
  router.push(`/order/detail/${o.order_no}`)
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  load()
}

onMounted(load)
</script>

<template>
  <div class="order-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">订单管理</h1>
        <p class="page-sub">全平台订单查询与监控</p>
      </div>
    </div>

    <section class="filter-card">
      <div class="filter-row">
        <select v-model="statusFilter" class="select" @change="search">
          <option v-for="o in STATUS_OPTIONS" :key="o.v" :value="o.v">{{ o.label }}</option>
        </select>
        <input v-model="keyword" class="input" placeholder="订单号 / 手机号" @keyup.enter="search" />
        <button class="btn-primary" @click="search"><span class="material-symbols-outlined">search</span>查询</button>
      </div>
    </section>

    <section class="table-card">
      <div v-if="loading" class="empty-tip">加载中...</div>
      <div v-else-if="!orders.length" class="empty-tip">暂无订单</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>订单号</th>
            <th>用户</th>
            <th>实付</th>
            <th>状态</th>
            <th>下单时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="o in orders" :key="o.id">
            <td class="mono">{{ o.order_no }}</td>
            <td>{{ o.userName || '—' }}</td>
            <td class="amount">¥{{ Number(o.pay_amount || 0).toFixed(2) }}</td>
            <td>
              <span class="status-pill" :class="STATUS_MAP[o.status]?.cls">
                <span class="dot"></span>{{ STATUS_MAP[o.status]?.label || o.status }}
              </span>
            </td>
            <td class="mono">{{ (o.created_at || '').replace('T', ' ').slice(0, 19) }}</td>
            <td>
              <button class="link" @click="openDetail(o)">速览</button>
              <button class="link" @click="openInUserView(o)">用户端详情</button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="orders.length">
        <span class="page-info">共 {{ total }} 条</span>
        <div class="page-buttons">
          <button class="page-btn" @click="goPage(page - 1)"><span class="material-symbols-outlined">chevron_left</span></button>
          <button class="page-btn active">{{ page }}</button>
          <button class="page-btn" @click="goPage(page + 1)"><span class="material-symbols-outlined">chevron_right</span></button>
        </div>
      </div>
    </section>

    <!-- 详情抽屉 -->
    <div v-if="drawer" class="drawer-mask" @click.self="drawer = false">
      <aside class="drawer">
        <div class="drawer-head">
          <h3>订单详情</h3>
          <button class="close" @click="drawer = false"><span class="material-symbols-outlined">close</span></button>
        </div>
        <div class="drawer-body" v-if="current">
          <div class="kv"><span>订单号</span><b class="mono">{{ current.order_no }}</b></div>
          <div class="kv"><span>用户</span><b>{{ current.userName || '—' }}</b></div>
          <div class="kv"><span>状态</span><b>{{ STATUS_MAP[current.status]?.label || current.status }}</b></div>
          <div class="kv"><span>商品金额</span><b>¥{{ Number(current.total_amount || 0).toFixed(2) }}</b></div>
          <div class="kv"><span>运费</span><b>¥{{ Number(current.freight_amount || 0).toFixed(2) }}</b></div>
          <div class="kv"><span>优惠</span><b>-¥{{ Number(current.coupon_amount || 0).toFixed(2) }}</b></div>
          <div class="kv" v-if="current.donate_amount"><span>爱心捐赠</span><b>¥{{ Number(current.donate_amount || 0).toFixed(2) }}</b></div>
          <div class="kv total"><span>实付</span><b>¥{{ Number(current.pay_amount || 0).toFixed(2) }}</b></div>
          <div class="kv"><span>下单时间</span><b class="mono">{{ (current.created_at || '').replace('T', ' ').slice(0, 19) }}</b></div>
        </div>
      </aside>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.order-view { display: flex; flex-direction: column; gap: $spacing-md; }
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
.mono { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.amount { font-weight: 700; color: $color-on-surface; }

.status-pill { display: inline-flex; align-items: center; gap: $spacing-xs; font-size: $font-size-label-sm; font-weight: 700; }
.status-pill .dot { width: 8px; height: 8px; border-radius: $radius-full; }
.status-pill.pending { color: $color-warning; .dot { background: $color-warning; } }
.status-pill.paid { color: $color-secondary; .dot { background: $color-secondary; } }
.status-pill.shipping { color: $color-brand; .dot { background: $color-brand; } }
.status-pill.done { color: $color-success; .dot { background: $color-success; } }
.status-pill.cancelled { color: $color-on-surface-variant; .dot { background: $color-on-surface-variant; } }
.status-pill.refunding { color: $color-warning; .dot { background: $color-warning; } }
.status-pill.refunded { color: $color-emergency; .dot { background: $color-emergency; } }

.link { color: $color-brand; cursor: pointer; background: none; border: none; font-size: $font-size-label-md; font-weight: 700; margin-right: $spacing-sm; }

.pagination { display: flex; align-items: center; justify-content: space-between; padding: $spacing-md $spacing-lg; border-top: 1px solid $color-outline-variant; }
.page-info { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.page-buttons { display: flex; gap: $spacing-xs; }
.page-btn {
  min-width: 32px; height: 32px; display: flex; align-items: center; justify-content: center; padding: 0 $spacing-sm;
  border-radius: $radius-default; background: $color-surface-container-lowest; border: 1px solid $color-outline-variant;
  color: $color-on-surface-variant; font-size: $font-size-label-md; cursor: pointer;
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
  .material-symbols-outlined { font-size: 16px; }
}

.drawer-mask { position: fixed; inset: 0; background: rgba(0,0,0,0.4); display: flex; justify-content: flex-end; z-index: 50; }
.drawer {
  width: 420px; max-width: 90vw; height: 100%; background: $color-surface;
  box-shadow: $shadow-card; display: flex; flex-direction: column; animation: slideIn 0.2s ease;
}
@keyframes slideIn { from { transform: translateX(20px); opacity: 0.6; } to { transform: none; opacity: 1; } }
.drawer-head { display: flex; justify-content: space-between; align-items: center; padding: $spacing-lg; border-bottom: 1px solid $color-outline-variant; }
.drawer-head h3 { font-family: $font-family-headline; font-size: $font-size-headline-md; color: $color-on-surface; }
.close { background: none; border: none; cursor: pointer; color: $color-on-surface-variant; }
.drawer-body { padding: $spacing-lg; display: flex; flex-direction: column; gap: $spacing-md; }
.kv { display: flex; justify-content: space-between; font-size: $font-size-body-md; color: $color-on-surface-variant; }
.kv b { color: $color-on-surface; }
.kv.total { border-top: 1px dashed $color-outline-variant; padding-top: $spacing-md; font-size: $font-size-headline-md; }
.kv.total b { color: $color-brand; }
</style>
