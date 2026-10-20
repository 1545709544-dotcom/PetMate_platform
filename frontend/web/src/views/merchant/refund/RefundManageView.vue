<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { merchantApi, type MerchantRefundRow } from '@/api/modules/merchant'

const router = useRouter()

type RefundRow = MerchantRefundRow

const TYPE_EXCHANGE = 'EXCHANGE'
const TYPE_REFUND_ONLY = 'REFUND_ONLY'

const loading = ref(false)
const activeTab = ref<'todo' | 'processing' | 'handled'>('todo')
const list = ref<RefundRow[]>([])
const counts = ref({
  pending: 0, todo: 0, returned: 0, appealing: 0,
  refunded: 0, exchanged: 0, rejected: 0,
})

// 各 Tab 角标：processing = 全部非终态；handled = 全部终态
// 后端仅返回各 status 的明细计数，这里在客户端聚合即可。
const processingCount = computed(() =>
  counts.value.pending + counts.value.returned + counts.value.appealing,
)
const handledCount = computed(() =>
  counts.value.refunded + counts.value.exchanged + counts.value.rejected,
)

const submitting = ref(false)

// 同意售后弹窗（退货/换货必须给退货地址，仅退款可核定金额）
const approveVisible = ref(false)
const approveTarget = ref<RefundRow | null>(null)
const approveForm = ref({ returnAddress: '', refundAmount: 0, note: '' })

// 确认收货 / 换货重发 / 驳回
const confirmVisible = ref(false)
const confirmTarget = ref<RefundRow | null>(null)
const confirmNote = ref('')

const reshipVisible = ref(false)
const reshipTarget = ref<RefundRow | null>(null)
const reshipForm = ref({ logisticsCompany: '', logisticsNo: '', note: '' })

const rejectVisible = ref(false)
const rejectNote = ref('')
const rejectTarget = ref<RefundRow | null>(null)

// 进度时间线
const timelineVisible = ref(false)
const timelineLoading = ref(false)
const timeline = ref<Array<Record<string, any>>>([])
const timelineTarget = ref<RefundRow | null>(null)

const tabs = computed(() => [
  { id: 'todo' as const, label: '待我处理', badge: counts.value.todo },
  { id: 'processing' as const, label: '进行中', badge: processingCount.value },
  { id: 'handled' as const, label: '已完结', badge: handledCount.value },
])

// 状态 → 配色。文案直接用后端 statusText，前后端不会各说一套
const STATUS_CLS: Record<string, string> = {
  pending: 'pending',
  returning: 'progress',
  exchanging: 'progress',
  returned: 'warn',
  reshipped: 'progress',
  appealing: 'warn',
  refunded: 'approved',
  exchanged: 'approved',
  rejected: 'rejected',
  arbitrated: 'rejected',
  closed: 'rejected',
}
function statusCls(s: string) {
  return STATUS_CLS[s] || 'pending'
}

function money(n: number | null | undefined) {
  return '¥' + Number(n || 0).toFixed(2)
}
function fmt(s: string | null) {
  return (s || '').replace('T', ' ').slice(0, 16)
}
function buyerName(r: RefundRow) {
  return r.buyer_name || r.userName || `用户${r.user_id}`
}
function buyerPhone(r: RefundRow) {
  return r.buyer_phone || r.userPhone || '—'
}
function evidenceList(r: RefundRow): string[] {
  if (!r.evidence_images) return []
  try {
    const v = JSON.parse(r.evidence_images)
    return Array.isArray(v) ? v : []
  } catch {
    return r.evidence_images.split(',').filter(Boolean)
  }
}

// 当前状态下商家能做什么（严格对齐后端状态机可达性）
function canApprove(r: RefundRow) {
  return r.status === 'pending'
}
function canConfirm(r: RefundRow) {
  // 后端允许 returning / exchanging / returned 三态确认收货
  return ['returning', 'exchanging', 'returned'].includes(r.status)
}
function canReship(r: RefundRow) {
  return r.refund_type === TYPE_EXCHANGE && r.status === 'returned'
}
function canReject(r: RefundRow) {
  return ['pending', 'returning', 'exchanging', 'returned'].includes(r.status)
}

async function load() {
  loading.value = true
  try {
    const res = await merchantApi.refunds({ status: activeTab.value })
    list.value = res.list || []
    counts.value = {
      pending: Number(res.pendingCount ?? 0),
      todo: Number(res.todoCount ?? 0),
      returned: Number(res.returnedCount ?? 0),
      appealing: Number(res.appealingCount ?? 0),
      refunded: Number(res.refundedCount ?? 0),
      exchanged: Number(res.exchangedCount ?? 0),
      rejected: Number(res.rejectedCount ?? 0),
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载售后列表失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

// ===== 同意售后 =====
function openApprove(r: RefundRow) {
  approveTarget.value = r
  approveForm.value = {
    returnAddress: r.return_address || '',
    refundAmount: Number(r.amount || 0),
    note: '',
  }
  approveVisible.value = true
}

async function submitApprove() {
  const r = approveTarget.value
  if (!r) return
  const needReturn = r.refund_type !== TYPE_REFUND_ONLY
  if (needReturn && !approveForm.value.returnAddress.trim()) {
    return ElMessage.warning('退货/换货必须填写退货收件地址')
  }
  if (approveForm.value.refundAmount > Number(r.amount)) {
    return ElMessage.warning(`退款金额不能超过订单实付 ${money(r.amount)}`)
  }
  if (approveForm.value.refundAmount < 0) {
    return ElMessage.warning('退款金额不能为负数')
  }
  submitting.value = true
  try {
    await merchantApi.approveRefund(r.id, {
      note: approveForm.value.note.trim() || undefined,
      returnAddress: needReturn ? approveForm.value.returnAddress.trim() : undefined,
      refundAmount: r.refund_type === TYPE_EXCHANGE ? undefined : approveForm.value.refundAmount,
    })
    ElMessage.success(needReturn ? '已同意，等待买家寄回' : '已同意退款')
    approveVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ===== 确认收货 =====
function openConfirm(r: RefundRow) {
  confirmTarget.value = r
  confirmNote.value = ''
  confirmVisible.value = true
}

async function submitConfirm() {
  const r = confirmTarget.value
  if (!r) return
  submitting.value = true
  try {
    await merchantApi.confirmRefund(r.id, confirmNote.value.trim() || undefined)
    ElMessage.success(
      r.refund_type === TYPE_EXCHANGE ? '已确认收货，请尽快重新发货' : '已确认收货，退款完成',
    )
    confirmVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ===== 换货重新发货 =====
function openReship(r: RefundRow) {
  reshipTarget.value = r
  reshipForm.value = { logisticsCompany: '', logisticsNo: '', note: '' }
  reshipVisible.value = true
}

async function submitReship() {
  const r = reshipTarget.value
  if (!r) return
  if (!reshipForm.value.logisticsCompany.trim()) return ElMessage.warning('请填写物流公司')
  if (!reshipForm.value.logisticsNo.trim()) return ElMessage.warning('请填写物流单号')
  submitting.value = true
  try {
    await merchantApi.reshipRefund(r.id, {
      logisticsCompany: reshipForm.value.logisticsCompany.trim(),
      logisticsNo: reshipForm.value.logisticsNo.trim(),
      note: reshipForm.value.note.trim() || undefined,
    })
    ElMessage.success('已重新发货')
    reshipVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ===== 进度时间线 =====
async function openTimeline(r: RefundRow) {
  timelineTarget.value = r
  timelineVisible.value = true
  timelineLoading.value = true
  timeline.value = []
  try {
    timeline.value = (await merchantApi.refundTimeline(r.id)) || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载进度失败')
  } finally {
    timelineLoading.value = false
  }
}

function openReject(r: RefundRow) {
  rejectTarget.value = r
  rejectNote.value = ''
  rejectVisible.value = true
}

async function submitReject() {
  if (!rejectTarget.value) return
  if (!rejectNote.value.trim()) return ElMessage.warning('请填写驳回理由')
  submitting.value = true
  try {
    await merchantApi.rejectRefund(rejectTarget.value.id, rejectNote.value.trim())
    ElMessage.success('已驳回售后申请')
    rejectVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="refund-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">售后退款</h1>
        <p class="page-sub">覆盖仅退款 / 退货退款 / 换货全流程，及时处理以保障店铺口碑。</p>
      </div>
      <button class="btn-refresh" :disabled="loading" @click="load">
        <span class="material-symbols-outlined" :class="{ spinning: loading }">refresh</span>
        {{ loading ? '加载中' : '刷新' }}
      </button>
    </div>

    <!-- Tabs -->
    <div class="tabs-bar">
      <button
        v-for="t in tabs"
        :key="t.id"
        class="tab"
        :class="{ active: activeTab === t.id }"
        @click="activeTab = t.id; load()"
      >
        {{ t.label }}
        <span v-if="t.badge" class="badge" :class="{ zero: t.badge === 0 }">{{ t.badge }}</span>
      </button>
    </div>

    <div v-if="loading" class="state">
      <span class="material-symbols-outlined spinning">progress_activity</span>
      <p>加载中...</p>
    </div>
    <div v-else-if="!list.length" class="state">
      <span class="material-symbols-outlined">check_circle</span>
      <p>
        {{ activeTab === 'todo' ? '太棒了，没有待你处理的售后～'
          : activeTab === 'processing' ? '暂无进行中的售后单' : '暂无已完结的售后记录' }}
      </p>
    </div>

    <div v-else class="rf-list">
      <div v-for="r in list" :key="r.id" class="rf-card">
        <div class="rf-top">
          <div class="rf-order">
            <span class="rf-no mono">{{ r.order_no }}</span>
            <span class="rf-type-tag">{{ r.typeText }}</span>
            <span class="rf-time">{{ fmt(r.created_at) }}</span>
          </div>
          <span class="status-pill" :class="statusCls(r.status)">{{ r.statusText }}</span>
        </div>

        <div class="rf-body">
          <div class="rf-amount">
            <span class="rf-amount-label">{{ r.refund_amount != null ? '核定退款' : '申请金额' }}</span>
            <span class="rf-amount-val">{{ money(r.refund_amount ?? r.amount) }}</span>
            <span
              v-if="r.refund_amount != null && Number(r.refund_amount) < Number(r.amount)"
              class="rf-amount-orig"
            >原 {{ money(r.amount) }}</span>
          </div>
          <div class="rf-buyer">
            <span class="material-symbols-outlined">person</span>
            {{ buyerName(r) }}
            <span class="rf-phone">{{ buyerPhone(r) }}</span>
          </div>
        </div>

        <div v-if="r.reason" class="rf-reason">
          <span class="rf-reason-label">售后原因</span>
          <p class="rf-reason-text">{{ r.reason }}</p>
        </div>

        <!-- 凭证图 -->
        <div v-if="evidenceList(r).length" class="rf-evidence">
          <img v-for="(img, i) in evidenceList(r)" :key="i" :src="img" alt="凭证" />
        </div>

        <!-- 物流信息：买家寄回 / 商家重发 -->
        <div v-if="r.logistics_no || r.new_logistics_no || r.return_address" class="rf-logistics">
          <div v-if="r.return_address" class="lg-row">
            <span class="lg-label">退货地址</span>
            <span class="lg-val">{{ r.return_address }}</span>
          </div>
          <div v-if="r.logistics_no" class="lg-row">
            <span class="lg-label">买家寄回</span>
            <span class="lg-val mono">{{ r.logistics_company }} {{ r.logistics_no }}</span>
          </div>
          <div v-if="r.new_logistics_no" class="lg-row">
            <span class="lg-label">重发物流</span>
            <span class="lg-val mono">{{ r.new_logistics_company }} {{ r.new_logistics_no }}</span>
          </div>
        </div>

        <div v-if="r.handle_note" class="rf-handle-note">
          <span class="rf-reason-label">处理备注</span>
          <p class="rf-reason-text">{{ r.handle_note }}</p>
        </div>
        <div v-if="r.status === 'appealing'" class="rf-appeal">
          <span class="material-symbols-outlined">gavel</span>
          买家已申请平台介入{{ r.appeal_reason ? '：' + r.appeal_reason : '' }}，等待管理员仲裁
        </div>

        <div class="rf-actions">
          <button class="link" @click="router.push(`/merchant/order/${r.order_no}`)">查看订单</button>
          <button class="link" @click="openTimeline(r)">进度详情</button>
          <span class="flex-fill"></span>
          <button v-if="canApprove(r)" class="btn-approve" :disabled="submitting" @click="openApprove(r)">
            同意售后
          </button>
          <button v-if="canConfirm(r)" class="btn-approve" :disabled="submitting" @click="openConfirm(r)">
            确认收货
          </button>
          <button v-if="canReship(r)" class="btn-ship" :disabled="submitting" @click="openReship(r)">
            重新发货
          </button>
          <button v-if="canReject(r)" class="btn-reject" :disabled="submitting" @click="openReject(r)">
            驳回
          </button>
          <span v-if="r.isFinal" class="rf-done-time">完结于 {{ fmt(r.finished_at || r.handled_at) }}</span>
        </div>
      </div>
    </div>

    <!-- 同意售后 -->
    <el-dialog v-model="approveVisible" title="同意售后" width="480px" align-center>
      <template v-if="approveTarget">
        <p class="dialog-tip">
          售后类型：<b>{{ approveTarget.typeText }}</b> · 订单 {{ approveTarget.order_no }}
        </p>

        <div v-if="approveTarget.refund_type !== 'EXCHANGE'" class="form-row">
          <label class="form-label">核定退款金额（不超过实付 {{ money(approveTarget.amount) }}）</label>
          <input
            v-model.number="approveForm.refundAmount"
            class="form-input"
            type="number"
            min="0"
            :max="Number(approveTarget.amount)"
            step="0.01"
          />
        </div>

        <div v-if="approveTarget.refund_type !== 'REFUND_ONLY'" class="form-row">
          <label class="form-label">退货收件地址（必填，买家按此寄回）</label>
          <textarea
            v-model="approveForm.returnAddress"
            class="reject-textarea"
            rows="2"
            maxlength="200"
            placeholder="例如：广东省广州市天河区 xx 路 1 号 仓储部 张三 13800000000"
          />
        </div>

        <div class="form-row">
          <label class="form-label">处理备注（可选）</label>
          <textarea v-model="approveForm.note" class="reject-textarea" rows="2" maxlength="200" />
        </div>

        <p class="dialog-note">
          {{ approveTarget.refund_type === 'REFUND_ONLY'
            ? '同意后立即恢复库存、退回优惠券与鱼干，订单置为已退款。'
            : '同意后等待买家寄回，收到货再点「确认收货」才会真正退款。' }}
        </p>
      </template>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="primary" :disabled="submitting" @click="submitApprove">确认同意</el-button>
      </template>
    </el-dialog>

    <!-- 确认收货 -->
    <el-dialog v-model="confirmVisible" title="确认收到退回商品" width="440px" align-center>
      <p v-if="confirmTarget" class="dialog-tip">
        订单 {{ confirmTarget.order_no }} · {{ confirmTarget.typeText }}
      </p>
      <p v-if="confirmTarget" class="dialog-note">
        {{ confirmTarget.refund_type === 'EXCHANGE'
          ? '确认后流转到「待重新发货」，不退款、不恢复库存。'
          : `确认后将退款 ${money(confirmTarget.refund_amount ?? confirmTarget.amount)}，并恢复库存、退回优惠券与鱼干。` }}
      </p>
      <textarea
        v-model="confirmNote"
        class="reject-textarea"
        rows="3"
        maxlength="200"
        placeholder="验货备注（可选），例如：商品完好，配件齐全"
      />
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="primary" :disabled="submitting" @click="submitConfirm">确认收货</el-button>
      </template>
    </el-dialog>

    <!-- 换货重新发货 -->
    <el-dialog v-model="reshipVisible" title="换货重新发货" width="440px" align-center>
      <p v-if="reshipTarget" class="dialog-tip">订单 {{ reshipTarget.order_no }}</p>
      <div class="form-row">
        <label class="form-label">物流公司</label>
        <input v-model="reshipForm.logisticsCompany" class="form-input" placeholder="如：顺丰速运" />
      </div>
      <div class="form-row">
        <label class="form-label">物流单号</label>
        <input v-model="reshipForm.logisticsNo" class="form-input" placeholder="如：SF1234567890" />
      </div>
      <div class="form-row">
        <label class="form-label">备注（可选）</label>
        <textarea v-model="reshipForm.note" class="reject-textarea" rows="2" maxlength="200" />
      </div>
      <template #footer>
        <el-button @click="reshipVisible = false">取消</el-button>
        <el-button type="primary" :disabled="submitting" @click="submitReship">确认发货</el-button>
      </template>
    </el-dialog>

    <!-- 进度时间线 -->
    <el-dialog v-model="timelineVisible" title="售后进度" width="520px" align-center>
      <p v-if="timelineTarget" class="dialog-tip">
        订单 {{ timelineTarget.order_no }} · {{ timelineTarget.typeText }}
      </p>
      <div v-if="timelineLoading" class="state">
        <span class="material-symbols-outlined spinning">progress_activity</span>
      </div>
      <div v-else-if="!timeline.length" class="dialog-note">暂无进度记录</div>
      <ol v-else class="tl-list">
        <li v-for="(t, i) in timeline" :key="i" class="tl-item">
          <span class="tl-dot" :class="{ first: i === 0 }"></span>
          <div class="tl-body">
            <div class="tl-head">
              <span class="tl-action">{{ t.actionText || t.action || '进度更新' }}</span>
              <span class="tl-time">{{ fmt(t.createdAt || t.created_at) }}</span>
            </div>
            <p v-if="t.note" class="tl-note">{{ t.note }}</p>
            <p class="tl-role">{{ t.operatorName || t.operator || t.roleText || '' }}</p>
          </div>
        </li>
      </ol>
      <template #footer>
        <el-button @click="timelineVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 驳回理由弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回售后" width="420px" align-center>
      <p class="dialog-tip">请填写驳回理由，买家将在前台看到该说明，并可申请平台介入。</p>
      <textarea
        v-model="rejectNote"
        class="reject-textarea"
        rows="4"
        maxlength="200"
        placeholder="例如：商品已拆封影响二次销售，无法退款"
      />
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :disabled="submitting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.refund-view { display: flex; flex-direction: column; gap: $spacing-lg; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title {
  font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface;
  margin: 0;
}
.page-sub { margin-top: 4px; font-size: $font-size-body-md; color: $color-on-surface-variant; }
.btn-refresh {
  display: flex; align-items: center; gap: 6px; padding: $spacing-xs $spacing-lg;
  border: 1px solid $color-outline-variant; border-radius: $radius-md;
  background: $color-surface-container-lowest; color: $color-on-surface;
  font-size: $font-size-label-md; font-weight: 600; cursor: pointer;
  .material-symbols-outlined { font-size: 18px; }
  &:hover { border-color: $color-brand; color: $color-brand; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}
.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.tabs-bar {
  display: flex; gap: $spacing-xs; padding: $spacing-xs;
  background: $color-surface-container-lowest; border-radius: $radius-lg; box-shadow: $shadow-sm; overflow-x: auto;
}
.tab {
  display: flex; align-items: center; gap: 6px; padding: $spacing-xs $spacing-lg;
  border-radius: $radius-md; font-size: $font-size-label-md; font-weight: 600;
  color: $color-on-surface-variant; cursor: pointer; white-space: nowrap; transition: all 0.2s;
  &:hover { background: $color-surface-container; }
  &.active { background: $color-brand; color: #fff; }
}
.badge {
  padding: 0 6px; min-width: 18px; line-height: 18px; text-align: center;
  background: $color-brand; color: #fff; border-radius: $radius-full; font-size: 10px; font-weight: 700;
  &.zero { background: $color-on-surface-variant; opacity: 0.5; }
}
.tab.active .badge { background: rgba(255, 255, 255, 0.25); }

.state {
  display: flex; flex-direction: column; align-items: center; gap: $spacing-sm;
  padding: $spacing-3xl; background: $color-surface-container-lowest; border-radius: $radius-lg;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 48px; opacity: 0.3; }
  p { margin: 0; font-size: $font-size-body-md; }
}

.rf-list { display: flex; flex-direction: column; gap: $spacing-md; }
.rf-card {
  background: $color-surface-container-lowest; border-radius: $radius-lg; box-shadow: $shadow-sm;
  padding: $spacing-lg; display: flex; flex-direction: column; gap: $spacing-md;
}
.rf-top { display: flex; align-items: center; justify-content: space-between; }
.rf-order { display: flex; align-items: baseline; gap: $spacing-md; }
.rf-no { font-family: $font-family-mono; font-size: $font-size-label-sm; font-weight: 700; color: $color-on-surface; }
.rf-time { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.status-pill {
  padding: 3px $spacing-md; border-radius: $radius-full; font-size: $font-size-label-sm; font-weight: 700;
  &.pending { background: rgba(250, 173, 20, 0.15); color: #b45309; }
  &.progress { background: rgba(22, 119, 255, 0.15); color: #1677ff; }
  &.warn { background: rgba(250, 173, 20, 0.18); color: #b45309; }
  &.approved { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.rejected { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
}
.rf-body { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: $spacing-md; }
.rf-amount { display: flex; flex-direction: column; }
.rf-amount-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.rf-amount-val { font-family: $font-family-headline; font-size: $font-size-headline-md; font-weight: 700; color: $color-emergency; }
.rf-buyer { display: flex; align-items: center; gap: 4px; font-size: $font-size-body-md; color: $color-on-surface; }
.rf-buyer .material-symbols-outlined { font-size: 18px; color: $color-on-surface-variant; }
.rf-phone { margin-left: $spacing-xs; color: $color-on-surface-variant; font-size: $font-size-label-sm; }
.rf-reason, .rf-handle-note {
  background: $color-surface-container-low; border-radius: $radius-md; padding: $spacing-md;
}
.rf-reason-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; font-weight: 600; }
.rf-reason-text { margin: 4px 0 0; font-size: $font-size-body-md; color: $color-on-surface; line-height: 1.6; }
.rf-actions { display: flex; align-items: center; gap: $spacing-md; flex-wrap: wrap; }
.link { background: none; border: none; color: $color-brand; font-weight: 600; cursor: pointer; font-size: $font-size-label-md; }
.btn-approve {
  padding: $spacing-xs $spacing-lg; border-radius: $radius-md; border: 1px solid $color-success;
  background: $color-success; color: #fff; font-weight: 700; cursor: pointer; font-size: $font-size-label-md;
  &:disabled { opacity: 0.6; }
}
.btn-reject {
  padding: $spacing-xs $spacing-lg; border-radius: $radius-md; border: 1px solid $color-emergency;
  background: transparent; color: $color-emergency; font-weight: 700; cursor: pointer; font-size: $font-size-label-md;
  &:disabled { opacity: 0.6; }
}
.rf-done-time { font-size: $font-size-label-sm; color: $color-on-surface-variant; }

.dialog-tip { margin: 0 0 $spacing-md; font-size: $font-size-body-md; color: $color-on-surface-variant; }
.reject-textarea {
  width: 100%; padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant;
  border-radius: $radius-md; font-family: inherit; font-size: $font-size-body-md; color: $color-on-surface;
  resize: vertical; outline: none; &:focus { border-color: $color-brand; }
}

/* ===== 新增：退款卡片扩展元素 ===== */
.rf-type-tag {
  padding: 2px 8px; border-radius: $radius-full; font-size: $font-size-label-sm; font-weight: 700;
  background: $color-surface-container; color: $color-on-surface-variant; white-space: nowrap;
}
.rf-amount-orig { margin-top: 2px; font-size: $font-size-label-sm; color: $color-on-surface-variant; }

.rf-evidence { display: flex; gap: $spacing-sm; flex-wrap: wrap; }
.rf-evidence img {
  width: 80px; height: 80px; object-fit: cover; border-radius: $radius-md;
  border: 1px solid $color-outline-variant; cursor: pointer; transition: transform 0.2s;
  &:hover { transform: scale(1.05); }
}

.rf-logistics {
  display: flex; flex-direction: column; gap: 4px;
  background: $color-surface-container-low; border-radius: $radius-md; padding: $spacing-md;
}
.lg-row { display: flex; gap: $spacing-sm; font-size: $font-size-body-md; }
.lg-label { color: $color-on-surface-variant; font-weight: 600; min-width: 64px; flex-shrink: 0; }
.lg-val { color: $color-on-surface; word-break: break-all; }

.rf-appeal {
  display: flex; align-items: center; gap: 6px; font-size: $font-size-body-md; color: $color-emergency;
  background: rgba(255, 77, 79, 0.08); border-radius: $radius-md; padding: $spacing-sm $spacing-md;
  .material-symbols-outlined { font-size: 18px; }
}

.flex-fill { flex: 1; }

.btn-ship {
  padding: $spacing-xs $spacing-lg; border-radius: $radius-md; border: 1px solid $color-brand;
  background: $color-brand; color: #fff; font-weight: 700; cursor: pointer; font-size: $font-size-label-md;
  &:disabled { opacity: 0.6; }
}

.form-row { display: flex; flex-direction: column; gap: 6px; margin-bottom: $spacing-md; }
.form-label { font-size: $font-size-label-md; color: $color-on-surface-variant; font-weight: 600; }
.form-input {
  width: 100%; padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant;
  border-radius: $radius-md; font-family: inherit; font-size: $font-size-body-md; color: $color-on-surface;
  outline: none; &:focus { border-color: $color-brand; }
}

.dialog-note { margin: 0 0 $spacing-md; font-size: $font-size-body-md; color: $color-on-surface-variant; line-height: 1.6; }

/* ===== 进度时间线 ===== */
.tl-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; }
.tl-item { position: relative; display: flex; gap: $spacing-md; padding-bottom: $spacing-lg; }
.tl-item:last-child { padding-bottom: 0; }
.tl-dot {
  width: 10px; height: 10px; border-radius: 50%; background: $color-outline-variant;
  margin-top: 6px; flex-shrink: 0; position: relative;
  &::after {
    content: ''; position: absolute; top: 14px; left: 4px; width: 2px;
    height: calc(100% + 8px); background: $color-outline-variant;
  }
  &.first { background: $color-brand; }
}
.tl-item:last-child .tl-dot::after { display: none; }
.tl-body { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.tl-head { display: flex; justify-content: space-between; align-items: baseline; gap: $spacing-md; }
.tl-action { font-size: $font-size-body-md; font-weight: 700; color: $color-on-surface; }
.tl-time { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.tl-note { margin: 0; font-size: $font-size-body-md; color: $color-on-surface; line-height: 1.6; }
.tl-role { margin: 0; font-size: $font-size-label-sm; color: $color-on-surface-variant; }
</style>
