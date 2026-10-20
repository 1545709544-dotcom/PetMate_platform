<script setup lang="ts">
/**
 * 我的售后（售后中心）。
 *
 * 此前系统只有商家端和管理端能看到售后单，用户提交完就彻底失联。
 * 这里对齐主流电商的「我的售后」列表：Tab 分进行中/已完成，卡片直接给出
 * 下一步动作（填写退货物流、确认收货、申请平台介入…），不用点进详情。
 */
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { aftersaleApi, aftersaleTagType, type Aftersale } from '@/api/modules/aftersale'
import LogisticsFillDialog from './LogisticsFillDialog.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

const TABS = [
  { key: 'all', label: '全部' },
  { key: 'processing', label: '进行中' },
  { key: 'done', label: '已完成' },
]

const activeTab = ref('all')
const list = ref<Aftersale[]>([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)

const isEmpty = computed(() => !loading.value && list.value.length === 0)

async function load() {
  loading.value = true
  try {
    const res = await aftersaleApi.list({ status: activeTab.value, page: page.value, size })
    list.value = res?.list || []
    total.value = Number(res?.total || 0)
  } catch (e: any) {
    ElMessage.error(e?.message || '售后列表加载失败')
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function switchTab(key: string) {
  if (activeTab.value === key) return
  activeTab.value = key
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

onMounted(load)

// ---------- 行内动作 ----------
const logisticsTarget = ref<Aftersale | null>(null)
const logisticsVisible = ref(false)

function openLogistics(r: Aftersale) {
  logisticsTarget.value = r
  logisticsVisible.value = true
}

async function confirmReceive(r: Aftersale) {
  try {
    await ElMessageBox.confirm(
      '确认已收到商家重新寄出的商品？确认后本次换货流程将结束。',
      '确认收货',
      { confirmButtonText: '确认收到', cancelButtonText: '再等等', type: 'warning' },
    )
    await aftersaleApi.confirmReceive(r.id)
    ElMessage.success('换货已完成')
    load()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

async function cancelApply(r: Aftersale) {
  try {
    await ElMessageBox.confirm(
      '撤销后本次售后申请将关闭，订单恢复原状态。确定撤销吗？',
      '撤销售后申请',
      { confirmButtonText: '确定撤销', cancelButtonText: '再想想', type: 'warning' },
    )
    await aftersaleApi.cancel(r.id)
    ElMessage.success('已撤销售后申请')
    load()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

async function appeal(r: Aftersale) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请说明申请平台介入的原因，平台客服会核实双方情况后作出裁定。',
      '申请平台介入',
      {
        confirmButtonText: '提交申请',
        cancelButtonText: '再想想',
        inputType: 'textarea',
        inputPlaceholder: '如商家迟迟不处理、驳回理由不成立等',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写申请原因'),
      },
    )
    await aftersaleApi.appeal(r.id, { reason: value.trim() })
    ElMessage.success('已提交，平台客服将尽快介入处理')
    load()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '提交失败')
  }
}

function goDetail(r: Aftersale) {
  router.push(`/aftersale/${r.id}`)
}

// ---------- 展示辅助 ----------
function fmtTime(s?: string) {
  return (s || '').replace('T', ' ').slice(0, 16)
}

/** 当前售后单实际会退多少钱 */
function amountOf(r: Aftersale) {
  const v = r.refund_amount ?? r.amount
  return Number(v || 0).toFixed(2)
}

/** 给用户一句「现在该干嘛」的提示，比干巴巴的状态词有用 */
function hintOf(r: Aftersale): string {
  switch (r.status) {
    case 'pending':
      return '商家将在 48 小时内处理，请耐心等待'
    case 'returning':
      return '商家已同意，请尽快寄回商品并填写物流单号'
    case 'exchanging':
      return '商家已同意换货，请尽快寄回商品并填写物流单号'
    case 'returned':
      return '商品已寄出，等待商家确认收货'
    case 'reshipped':
      return `商家已重新发货${r.new_logistics_no ? `：${r.new_logistics_company} ${r.new_logistics_no}` : ''}`
    case 'refunded':
      return '退款已原路返回至你的付款账户'
    case 'exchanged':
      return '换货已完成，感谢你的耐心等待'
    case 'rejected':
      return r.handle_note ? `商家驳回：${r.handle_note}` : '商家已驳回，如有异议可申请平台介入'
    case 'appealing':
      return '平台客服正在核实，请留意处理结果'
    case 'arbitrated':
      return r.arbitrate_note ? `平台裁定：${r.arbitrate_note}` : '平台已作出裁定'
    case 'closed':
      return '你已撤销本次售后申请'
    default:
      return ''
  }
}
</script>

<template>
  <div class="aftersale-center">
    <div class="page-container">
      <nav class="topbar">
        <button class="back-btn" @click="router.back()">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <span class="topbar-title">我的售后</span>
      </nav>

      <!-- Tab -->
      <div class="tab-bar">
        <button
          v-for="t in TABS"
          :key="t.key"
          class="tab-item"
          :class="{ active: activeTab === t.key }"
          @click="switchTab(t.key)"
        >
          {{ t.label }}
        </button>
      </div>

      <!-- 加载 -->
      <div v-if="loading" class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <span>加载中…</span>
      </div>

      <!-- 空态 -->
      <PetEmptyState
        v-else-if="isEmpty"
        icon="inventory_2"
        title="暂无售后记录"
        desc="如需申请售后，请前往「我的订单」中对应订单发起"
      >
        <template #action>
          <button class="go-order-btn" @click="router.push('/order/list')">去我的订单</button>
        </template>
      </PetEmptyState>

      <!-- 列表 -->
      <div v-else class="card-list">
        <article v-for="r in list" :key="r.id" class="as-card pet-card">
          <!-- 头 -->
          <header class="card-head">
            <div class="head-left">
              <span class="material-symbols-outlined store-icon">storefront</span>
              <span class="store-name">{{ r.store_name || '宠伴商城' }}</span>
              <span class="type-tag">{{ r.typeText }}</span>
            </div>
            <el-tag :type="aftersaleTagType(r.status)" size="small" effect="light">
              {{ r.statusText }}
            </el-tag>
          </header>

          <!-- 商品 -->
          <div class="goods-list" @click="goDetail(r)">
            <div v-for="(it, i) in (r.items || []).slice(0, 2)" :key="i" class="goods-row">
              <img class="goods-img" :src="it.product_image" :alt="it.product_name" />
              <div class="goods-info">
                <div class="goods-name">{{ it.product_name }}</div>
                <div class="goods-meta">¥{{ Number(it.price).toFixed(2) }} × {{ it.quantity }}</div>
              </div>
            </div>
            <div v-if="(r.items || []).length > 2" class="more-goods">
              等 {{ r.items!.length }} 件商品
            </div>
          </div>

          <!-- 进度提示 -->
          <div class="hint-row" :class="{ warn: r.canFillLogistics || r.canReceive }">
            <span class="material-symbols-outlined">
              {{ r.canFillLogistics || r.canReceive ? 'pending_actions' : 'info' }}
            </span>
            <span class="hint-text">{{ hintOf(r) }}</span>
          </div>

          <!-- 底 -->
          <footer class="card-foot">
            <div class="foot-left">
              <span v-if="r.refund_type !== 'EXCHANGE'" class="amount">
                退款金额 <strong>¥{{ amountOf(r) }}</strong>
              </span>
              <span class="apply-time">申请于 {{ fmtTime(r.created_at) }}</span>
            </div>
            <div class="foot-actions">
              <button class="act-btn" @click="goDetail(r)">查看详情</button>
              <button v-if="r.canCancel" class="act-btn" @click="cancelApply(r)">撤销申请</button>
              <button v-if="r.canAppeal" class="act-btn" @click="appeal(r)">申请平台介入</button>
              <button v-if="r.canReceive" class="act-btn primary" @click="confirmReceive(r)">
                确认收货
              </button>
              <button v-if="r.canFillLogistics" class="act-btn primary" @click="openLogistics(r)">
                填写退货物流
              </button>
            </div>
          </footer>
        </article>
      </div>

      <!-- 分页 -->
      <div v-if="total > size" class="pager">
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page"
          :page-size="size"
          :total="total"
          @current-change="onPageChange"
        />
      </div>
    </div>

    <LogisticsFillDialog
      v-model="logisticsVisible"
      :refund="logisticsTarget"
      @success="load"
    />
  </div>
</template>

<style scoped lang="scss">
.aftersale-center {
  min-height: 100vh;
  background: $color-surface-variant;
  padding-bottom: 60px;
}

.page-container {
  max-width: 900px;
  margin: 0 auto;
  padding: 0 20px;
}

// ---------- 顶栏 ----------
.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 64px;
}

.back-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: $color-surface;
  color: $color-on-surface;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: rgba(255, 122, 50, 0.1);
    color: $color-brand;
  }
}

.topbar-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}

// ---------- Tab ----------
.tab-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  padding: 6px;
  border-radius: 14px;
  background: $color-surface;
}

.tab-item {
  flex: 1;
  padding: 10px 0;
  border: none;
  border-radius: 10px;
  background: transparent;
  font-size: $font-size-body-md;
  font-weight: 500;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    color: $color-brand;
  }

  &.active {
    background: $color-brand;
    color: #fff;
    font-weight: 600;
  }
}

// ---------- 状态块 ----------
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 80px 20px;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 52px;
    opacity: 0.5;
  }

  p {
    font-size: $font-size-body-lg;
    font-weight: 600;
    color: $color-on-surface;
    margin: 0;
  }
}

.empty-sub {
  font-size: $font-size-label-md;
}

.go-order-btn {
  margin-top: 10px;
  padding: 9px 22px;
  border: none;
  border-radius: 999px;
  background: $color-brand;
  color: #fff;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-1px);
  }
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

// ---------- 卡片 ----------
.card-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.as-card {
  background: $color-surface;
  border-radius: 16px;
  padding: 16px 18px;
  transition: box-shadow $transition-fast;

  &:hover {
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.06);
  }
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 12px;
  border-bottom: 1px solid $color-outline-variant;
}

.head-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.store-icon {
  font-size: 20px;
  color: $color-brand;
}

.store-name {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.type-tag {
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(255, 122, 50, 0.12);
  color: $color-brand;
  font-size: $font-size-label-sm;
  font-weight: 500;
  flex-shrink: 0;
}

// ---------- 商品 ----------
.goods-list {
  padding: 12px 0;
  cursor: pointer;
}

.goods-row {
  display: flex;
  gap: 12px;
  padding: 6px 0;
}

.goods-img {
  width: 62px;
  height: 62px;
  border-radius: 10px;
  object-fit: cover;
  background: $color-surface-variant;
  flex-shrink: 0;
}

.goods-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 5px;
}

.goods-name {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.goods-meta {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.more-goods {
  padding-top: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  text-align: right;
}

// ---------- 提示 ----------
.hint-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: $color-surface-variant;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1.5;

  .material-symbols-outlined {
    font-size: 18px;
    flex-shrink: 0;
  }

  &.warn {
    background: rgba(255, 122, 50, 0.1);
    color: $color-brand;
    font-weight: 500;
  }
}

.hint-text {
  flex: 1;
  min-width: 0;
}

// ---------- 底部 ----------
.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid $color-outline-variant;
}

.foot-left {
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.amount strong {
  font-size: $font-size-body-md;
  color: $color-emergency;
}

.foot-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.act-btn {
  padding: 7px 16px;
  border: 1px solid $color-outline-variant;
  border-radius: 999px;
  background: transparent;
  font-size: $font-size-label-md;
  color: $color-on-surface;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }

  &.primary {
    border-color: $color-brand;
    background: $color-brand;
    color: #fff;
    font-weight: 600;

    &:hover {
      color: #fff;
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(255, 122, 50, 0.3);
    }
  }
}

.pager {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
