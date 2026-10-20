<script setup lang="ts">
/**
 * 售后进度详情。
 *
 * 核心是那条时间线 —— 数据来自后端 t_refund_log，每次流转都有留痕，
 * 不是从状态字段猜出来的。用户能看清「谁、在什么时候、做了什么」。
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  aftersaleApi,
  aftersaleTagType,
  type Aftersale,
  type AftersaleTimelineNode,
} from '@/api/modules/aftersale'
import LogisticsFillDialog from './LogisticsFillDialog.vue'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const route = useRoute()
const router = useRouter()

const refundId = computed(() => Number(route.params.id || 0))
const data = ref<Aftersale | null>(null)
const loading = ref(false)
const errorMsg = ref('')

async function load() {
  loading.value = true
  errorMsg.value = ''
  try {
    data.value = await aftersaleApi.detail(refundId.value)
  } catch (e: any) {
    errorMsg.value = e?.message || '售后详情加载失败'
    data.value = null
  } finally {
    loading.value = false
  }
}
onMounted(load)

// ---------- 时间线 ----------
const ACTION_ICON: Record<string, string> = {
  apply: 'edit_note',
  approve: 'task_alt',
  reject: 'cancel',
  ship: 'local_shipping',
  confirm: 'inventory',
  reship: 'send',
  receive: 'done_all',
  appeal: 'gavel',
  arbitrate: 'balance',
  close: 'block',
  done: 'flag',
}

const ACTOR_LABEL: Record<string, string> = {
  user: '我',
  merchant: '商家',
  admin: '平台客服',
  system: '系统',
}

const timeline = computed<AftersaleTimelineNode[]>(() => data.value?.timeline || [])

function iconOf(n: AftersaleTimelineNode) {
  return ACTION_ICON[n.action] || 'radio_button_checked'
}

function actorOf(n: AftersaleTimelineNode) {
  return n.actor_name || ACTOR_LABEL[n.actor_type] || '系统'
}

function fmtTime(s?: string) {
  return (s || '').replace('T', ' ').slice(0, 19)
}

// ---------- 动作 ----------
const logisticsVisible = ref(false)

async function confirmReceive() {
  if (!data.value) return
  try {
    await ElMessageBox.confirm(
      '确认已收到商家重新寄出的商品？确认后本次换货流程将结束。',
      '确认收货',
      { confirmButtonText: '确认收到', cancelButtonText: '再等等', type: 'warning' },
    )
    await aftersaleApi.confirmReceive(data.value.id)
    ElMessage.success('换货已完成')
    load()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

async function cancelApply() {
  if (!data.value) return
  try {
    await ElMessageBox.confirm(
      '撤销后本次售后申请将关闭，订单恢复原状态。确定撤销吗？',
      '撤销售后申请',
      { confirmButtonText: '确定撤销', cancelButtonText: '再想想', type: 'warning' },
    )
    await aftersaleApi.cancel(data.value.id)
    ElMessage.success('已撤销售后申请')
    load()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

async function appeal() {
  if (!data.value) return
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
    await aftersaleApi.appeal(data.value.id, { reason: value.trim() })
    ElMessage.success('已提交，平台客服将尽快介入处理')
    load()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '提交失败')
  }
}

function goOrder() {
  if (data.value) router.push(`/order/detail/${data.value.order_no}`)
}

const amountText = computed(() => {
  if (!data.value) return '0.00'
  return Number(data.value.refund_amount ?? data.value.amount ?? 0).toFixed(2)
})

/** 商家把金额改小了就提示一下，避免用户以为被少退了 */
const amountReduced = computed(() => {
  const d = data.value
  if (!d || d.refund_amount == null) return false
  return Number(d.refund_amount) < Number(d.amount)
})
</script>

<template>
  <div class="aftersale-detail">
    <div class="page-container">
      <nav class="topbar">
        <button class="back-btn" @click="router.back()">
          <span class="material-symbols-outlined">arrow_back</span>
        </button>
        <span class="topbar-title">售后详情</span>
      </nav>

      <div v-if="loading" class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <span>加载中…</span>
      </div>

      <PetEmptyState v-else-if="errorMsg" icon="error_outline" :title="errorMsg">
        <template #action>
          <button class="retry-btn" @click="load">重新加载</button>
        </template>
      </PetEmptyState>

      <template v-else-if="data">
        <!-- 状态头 -->
        <section class="status-hero pet-card" :class="{ done: data.isFinal }">
          <div class="hero-main">
            <span class="material-symbols-outlined hero-icon">support_agent</span>
            <div class="hero-text">
              <h2 class="hero-title">{{ data.statusText }}</h2>
              <p class="hero-sub">
                {{ data.typeText }} · 售后单号 #{{ data.id }} · 订单 {{ data.order_no }}
              </p>
            </div>
          </div>
          <el-tag :type="aftersaleTagType(data.status)" effect="dark" size="large">
            {{ data.typeText }}
          </el-tag>
        </section>

        <!-- 待办提示条 -->
        <div v-if="data.canFillLogistics" class="todo-bar">
          <span class="material-symbols-outlined">pending_actions</span>
          <div class="todo-text">
            <strong>商家已同意，请尽快寄回商品</strong>
            <em>寄出后填写物流单号，商家收货后才会继续处理</em>
          </div>
          <button class="todo-btn" @click="logisticsVisible = true">填写物流</button>
        </div>
        <div v-else-if="data.canReceive" class="todo-bar">
          <span class="material-symbols-outlined">local_shipping</span>
          <div class="todo-text">
            <strong>商家已重新发货</strong>
            <em>{{ data.new_logistics_company }} {{ data.new_logistics_no }}</em>
          </div>
          <button class="todo-btn" @click="confirmReceive">确认收货</button>
        </div>

        <div class="detail-grid">
          <!-- 左：时间线 -->
          <PageSection class="panel timeline-panel" title="售后进度" icon="timeline">
            <ul class="timeline">
              <li
                v-for="(n, i) in timeline"
                :key="n.id || i"
                class="tl-item"
                :class="{ latest: i === timeline.length - 1 }"
              >
                <span class="tl-dot">
                  <span class="material-symbols-outlined">{{ iconOf(n) }}</span>
                </span>
                <div class="tl-body">
                  <div class="tl-head">
                    <span class="tl-actor" :class="`actor-${n.actor_type}`">{{ actorOf(n) }}</span>
                    <span class="tl-time">{{ fmtTime(n.created_at) }}</span>
                  </div>
                  <p class="tl-note">{{ n.note }}</p>
                </div>
              </li>
              <li v-if="!timeline.length" class="tl-empty">暂无进度记录</li>
            </ul>
          </PageSection>

          <!-- 右：信息 -->
          <aside class="side-col">
            <!-- 金额 -->
            <section v-if="data.refund_type !== 'EXCHANGE'" class="panel amount-panel">
              <div class="amount-label">退款金额</div>
              <div class="amount-value">¥{{ amountText }}</div>
              <div v-if="amountReduced" class="amount-note">
                商家核定金额，原订单实付 ¥{{ Number(data.amount).toFixed(2) }}
              </div>
              <div class="amount-note">退款将原路返回至你的付款账户</div>
            </section>

            <!-- 申请信息 -->
            <PageSection class="panel" title="申请信息" icon="description">
              <dl class="info-list">
                <div class="info-row">
                  <dt>售后类型</dt>
                  <dd>{{ data.typeText }}</dd>
                </div>
                <div class="info-row">
                  <dt>申请原因</dt>
                  <dd>{{ data.reason || '—' }}</dd>
                </div>
                <div class="info-row">
                  <dt>申请时间</dt>
                  <dd>{{ fmtTime(data.created_at) }}</dd>
                </div>
                <div v-if="data.handle_note" class="info-row">
                  <dt>商家回复</dt>
                  <dd>{{ data.handle_note }}</dd>
                </div>
                <div v-if="data.return_address" class="info-row">
                  <dt>退货地址</dt>
                  <dd>{{ data.return_address }}</dd>
                </div>
                <div v-if="data.logistics_no" class="info-row">
                  <dt>寄回物流</dt>
                  <dd>{{ data.logistics_company }} {{ data.logistics_no }}</dd>
                </div>
                <div v-if="data.new_logistics_no" class="info-row">
                  <dt>换货物流</dt>
                  <dd>{{ data.new_logistics_company }} {{ data.new_logistics_no }}</dd>
                </div>
              </dl>

              <!-- 凭证图 -->
              <div v-if="data.evidenceList?.length" class="evidence">
                <div class="evidence-label">凭证图</div>
                <el-image
                  v-for="(img, i) in data.evidenceList"
                  :key="i"
                  class="evidence-img"
                  :src="img"
                  :preview-src-list="data.evidenceList"
                  :initial-index="i"
                  fit="cover"
                  preview-teleported
                />
              </div>
            </PageSection>

            <!-- 平台介入 -->
            <PageSection v-if="data.appeal_reason" class="panel appeal-panel" title="平台介入" icon="gavel">
              <dl class="info-list">
                <div class="info-row">
                  <dt>介入原因</dt>
                  <dd>{{ data.appeal_reason }}</dd>
                </div>
                <div v-if="data.arbitrate_result" class="info-row">
                  <dt>裁定结果</dt>
                  <dd :class="data.arbitrate_result === 'refund' ? 'ok' : 'no'">
                    {{ data.arbitrate_result === 'refund' ? '支持买家，予以退款' : '维持商家处理结果' }}
                  </dd>
                </div>
                <div v-if="data.arbitrate_note" class="info-row">
                  <dt>裁定说明</dt>
                  <dd>{{ data.arbitrate_note }}</dd>
                </div>
              </dl>
            </PageSection>

            <!-- 商品 -->
            <PageSection class="panel" title="售后商品" icon="inventory_2">
              <div v-for="(it, i) in data.items || []" :key="i" class="goods-row">
                <img class="goods-img" :src="it.product_image" :alt="it.product_name" />
                <div class="goods-info">
                  <div class="goods-name">{{ it.product_name }}</div>
                  <div class="goods-meta">¥{{ Number(it.price).toFixed(2) }} × {{ it.quantity }}</div>
                </div>
              </div>
            </PageSection>
          </aside>
        </div>

        <!-- 底部操作 -->
        <div class="foot-bar">
          <button class="foot-btn" @click="goOrder">查看订单</button>
          <button v-if="data.canCancel" class="foot-btn" @click="cancelApply">撤销申请</button>
          <button v-if="data.canAppeal" class="foot-btn" @click="appeal">申请平台介入</button>
          <button v-if="data.canFillLogistics" class="foot-btn primary" @click="logisticsVisible = true">
            填写退货物流
          </button>
          <button v-if="data.canReceive" class="foot-btn primary" @click="confirmReceive">
            确认收货
          </button>
        </div>
      </template>
    </div>

    <LogisticsFillDialog v-model="logisticsVisible" :refund="data" @success="load" />
  </div>
</template>

<style scoped lang="scss">
.aftersale-detail {
  min-height: 100vh;
  background: $color-surface-variant;
  padding-bottom: 60px;
}

.page-container {
  max-width: 1100px;
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

// ---------- 状态区 ----------
.status-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 22px 24px;
  border-radius: 18px;
  background: linear-gradient(135deg, #ff9a5a 0%, #ff7a32 100%);
  color: #fff;
  margin-bottom: 14px;

  &.done {
    background: linear-gradient(135deg, #8d9aa5 0%, #6b7784 100%);
  }
}

.hero-main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.hero-icon {
  font-size: 40px;
  opacity: 0.9;
}

.hero-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  margin: 0 0 4px;
  line-height: 1.3;
}

.hero-sub {
  margin: 0;
  font-size: $font-size-label-md;
  opacity: 0.9;
  line-height: 1.4;
}

// ---------- 待办条 ----------
.todo-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  margin-bottom: 14px;
  border-radius: 14px;
  border: 1px solid $color-error-container;
  background: rgba(255, 218, 214, 0.45);

  > .material-symbols-outlined {
    font-size: 26px;
    color: $color-danger;
    flex-shrink: 0;
  }
}

.todo-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong {
    font-size: $font-size-body-md;
    font-weight: 600;
    color: $color-danger;
  }

  em {
    font-style: normal;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
  }
}

.todo-btn {
  padding: 9px 20px;
  border: none;
  border-radius: 999px;
  background: $color-emergency;
  color: #fff;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  flex-shrink: 0;
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-1px);
  }
}

// ---------- 布局 ----------
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 380px;
  gap: 16px;
  align-items: start;
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}

.side-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.panel {
  background: $color-surface;
  border-radius: 16px;
  padding: 18px 20px;
}

.panel-title {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0 0 16px;

  .material-symbols-outlined {
    font-size: 20px;
    color: $color-brand;
  }
}

// ---------- 时间线 ----------
.timeline {
  list-style: none;
  margin: 0;
  padding: 0;
}

.tl-item {
  position: relative;
  display: flex;
  gap: 14px;
  padding-bottom: 22px;

  // 竖线连接各节点，最后一个不画
  &:not(:last-child)::before {
    content: '';
    position: absolute;
    left: 15px;
    top: 32px;
    bottom: 0;
    width: 2px;
    background: $color-outline-variant;
  }

  &.latest {
    .tl-dot {
      background: $color-brand;
      color: #fff;
      box-shadow: 0 0 0 4px rgba(255, 122, 50, 0.18);
    }

    .tl-note {
      color: $color-on-surface;
      font-weight: 500;
    }
  }
}

.tl-dot {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: $color-surface-variant;
  color: $color-on-surface-variant;
  flex-shrink: 0;
  z-index: 1;

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.tl-body {
  flex: 1;
  min-width: 0;
  padding-top: 2px;
}

.tl-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.tl-actor {
  font-size: $font-size-label-sm;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
  background: $color-surface-variant;
  color: $color-on-surface-variant;

  &.actor-user {
    background: rgba(255, 122, 50, 0.12);
    color: $color-brand;
  }

  &.actor-merchant {
    background: rgba(104, 171, 255, 0.16);
    color: #2b6cb0;
  }

  &.actor-admin {
    background: $color-error-container;
    color: $color-danger;
  }
}

.tl-time {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.tl-note {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1.6;
  word-break: break-word;
}

.tl-empty {
  padding: 30px 0;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
}

// ---------- 金额 ----------
.amount-panel {
  text-align: center;
}

.amount-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: 6px;
}

.amount-value {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-emergency;
  line-height: 1.2;
  margin-bottom: 8px;
}

.amount-note {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  line-height: 1.5;
}

// ---------- 信息列表 ----------
.info-list {
  margin: 0;
}

.info-row {
  display: flex;
  gap: 12px;
  padding: 7px 0;
  font-size: $font-size-label-md;
  line-height: 1.6;

  dt {
    width: 68px;
    flex-shrink: 0;
    color: $color-on-surface-variant;
  }

  dd {
    flex: 1;
    min-width: 0;
    margin: 0;
    color: $color-on-surface;
    word-break: break-word;

    &.ok {
      color: #16a34a;
      font-weight: 600;
    }

    &.no {
      color: $color-danger;
      font-weight: 600;
    }
  }
}

// ---------- 凭证 ----------
.evidence {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid $color-outline-variant;
}

.evidence-label {
  width: 100%;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: 2px;
}

.evidence-img {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  cursor: zoom-in;
}

.appeal-panel {
  border: 1px solid $color-error-container;
}

// ---------- 商品 ----------
.goods-row {
  display: flex;
  gap: 12px;
  padding: 8px 0;
}

.goods-img {
  width: 56px;
  height: 56px;
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
  gap: 4px;
}

.goods-name {
  font-size: $font-size-label-md;
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

// ---------- 底部 ----------
.foot-bar {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 18px;
  padding: 16px 20px;
  background: $color-surface;
  border-radius: 16px;
}

.foot-btn {
  padding: 9px 20px;
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
    margin: 0;
    font-size: $font-size-body-lg;
    color: $color-on-surface;
  }
}

.retry-btn {
  margin-top: 8px;
  padding: 8px 20px;
  border: none;
  border-radius: 999px;
  background: $color-brand;
  color: #fff;
  cursor: pointer;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
