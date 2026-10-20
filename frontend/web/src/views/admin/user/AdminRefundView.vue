<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

// 售后状态机元数据（与后端 RefundStatus 对齐；后端已返回 typeText/statusText/isFinal/canArbitrate）
const STATUS_META: Record<string, { label: string; cls: string }> = {
  pending: { label: '待商家处理', cls: 'pending' },
  returning: { label: '待用户寄回', cls: 'progress' },
  exchanging: { label: '待用户寄回', cls: 'progress' },
  returned: { label: '待商家收货', cls: 'returned' },
  reshipped: { label: '商家已重发', cls: 'reshipped' },
  refunded: { label: '退款成功', cls: 'success' },
  exchanged: { label: '换货完成', cls: 'exchanged' },
  rejected: { label: '商家已驳回', cls: 'danger' },
  appealing: { label: '平台介入中', cls: 'appeal' },
  arbitrated: { label: '平台已裁定', cls: 'arbitrated' },
  closed: { label: '已撤销', cls: 'closed' },
}

const TABS = [
  { key: 'all', label: '全部' },
  { key: 'processing', label: '进行中' },
  { key: 'appealing', label: '待平台介入' },
  { key: 'refunded', label: '已退款' },
  { key: 'exchanged', label: '已换货' },
  { key: 'rejected', label: '已驳回' },
  { key: 'arbitrated', label: '已裁定' },
  { key: 'closed', label: '已撤销' },
] as const

const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const filter = ref<string>('all')
const refunds = ref<any[]>([])
const stat = ref<Record<string, number>>({})

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

const TAB_BADGE: Record<string, string> = {
  all: 'total',
  processing: 'processing',
  appealing: 'appealing',
  refunded: 'refunded',
  arbitrated: 'arbitrated',
}

async function loadStat() {
  try {
    stat.value = (await adminApi.refundStat()) as Record<string, number>
  } catch {
    /* 角标非关键，失败静默 */
  }
}

async function load() {
  loading.value = true
  try {
    const res = await adminApi.refundList({ status: filter.value, page: page.value, size: size.value })
    refunds.value = (res.list || []) as any[]
    total.value = res.total ?? refunds.value.length
  } catch (e: any) {
    ElMessage.error(e?.message || '加载售后列表失败')
  } finally {
    loading.value = false
  }
}

function setFilter(f: string) {
  filter.value = f
  page.value = 1
  load()
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  load()
}

// 该售后单在当前状态下，平台（管理员）可执行的操作
function actionsFor(r: any): Array<{ key: string; label: string; cls: string }> {
  const acts: Array<{ key: string; label: string; cls: string }> = []
  if (r.status === 'appealing') {
    acts.push({ key: 'arbitrate', label: '平台仲裁', cls: 'primary' })
  } else if (r.status === 'pending') {
    acts.push({ key: 'approve', label: '同意', cls: 'success' })
    acts.push({ key: 'reject', label: '驳回', cls: 'danger' })
  } else if (r.status === 'returned' && r.refund_type === 'RETURN_REFUND') {
    acts.push({ key: 'confirm', label: '确认收货并退款', cls: 'success' })
  }
  return acts
}

async function handleAction(r: any, key: string) {
  try {
    if (key === 'arbitrate') {
      openArbitrate(r)
      return
    }
    const map: Record<string, { title: string; prompt: string; confirm: string }> = {
      approve: { title: '平台代商家同意', prompt: '平台客服代商家处理（可填备注）', confirm: '同意' },
      reject: { title: '平台代商家驳回', prompt: '驳回原因（可填，将展示给买家）', confirm: '驳回' },
      confirm: { title: '确认收货并退款', prompt: '确认已收到用户寄回商品，并退款（可填备注）', confirm: '确认退款' },
    }
    const cfg = map[key]
    const { value: note } = await ElMessageBox.prompt(cfg.prompt, cfg.title, {
      inputType: 'textarea',
      inputPlaceholder: '选填',
      confirmButtonText: cfg.confirm,
      cancelButtonText: '取消',
    }).catch(() => ({ value: '' }))
    await adminApi.refundHandle(r.id, key as any, note || undefined)
    ElMessage.success('操作成功')
    await refresh()
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

async function refresh() {
  await Promise.all([load(), loadStat()])
}

// ============ 详情抽屉（含进度时间线） ============
const detailVisible = ref(false)
const current = ref<any>(null)
const timeline = ref<any[]>([])
const timelineLoading = ref(false)

async function openDetail(r: any) {
  current.value = r
  detailVisible.value = true
  timelineLoading.value = true
  try {
    timeline.value = (await adminApi.refundTimeline(r.id)) as any[]
  } catch {
    timeline.value = []
  } finally {
    timelineLoading.value = false
  }
}

// ============ 平台仲裁弹窗 ============
const arbitrateVisible = ref(false)
const arbitrateTarget = ref<number | null>(null)
const arbitrateResult = ref<'refund' | 'reject'>('refund')
const arbitrateNote = ref('')

function openArbitrate(r: any) {
  arbitrateTarget.value = r.id
  arbitrateResult.value = 'refund'
  arbitrateNote.value = ''
  arbitrateVisible.value = true
}

async function submitArbitrate() {
  if (!arbitrateNote.value.trim()) {
    ElMessage.warning('请填写仲裁说明，该说明会展示给买家与商家')
    return
  }
  if (arbitrateTarget.value == null) return
  try {
    await adminApi.refundHandle(arbitrateTarget.value, 'arbitrate', arbitrateNote.value.trim(), arbitrateResult.value)
    ElMessage.success(arbitrateResult.value === 'refund' ? '已裁定支持买家并退款' : '已裁定维持商家处理')
    arbitrateVisible.value = false
    await refresh()
  } catch (e: any) {
    ElMessage.error(e?.message || '仲裁失败')
  }
}

// ============ 工具 ============
function toArray(v: any): string[] {
  if (!v) return []
  if (Array.isArray(v)) return v
  if (typeof v === 'string') {
    const s = v.trim()
    if (!s) return []
    try {
      const p = JSON.parse(s)
      return Array.isArray(p) ? p : [s]
    } catch {
      return [s]
    }
  }
  return []
}

const ACTOR_LABEL: Record<string, string> = { user: '买家', merchant: '商家', admin: '平台客服' }
const ACTION_LABEL: Record<string, string> = {
  apply: '提交申请',
  approve: '同意',
  reject: '驳回',
  ship: '寄回商品',
  confirm: '确认收货',
  reship: '重新发货',
  receive: '确认收货',
  appeal: '申请平台介入',
  arbitrate: '平台仲裁',
  close: '撤销申请',
}

function statusMeta(s: string) {
  return STATUS_META[s] || { label: s || '未知', cls: 'closed' }
}

function fmtAmount(v: any) {
  return '¥' + Number(v || 0).toFixed(2)
}

onMounted(async () => {
  await loadStat()
  await load()
})
</script>

<template>
  <div class="refund-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">售后管理</h1>
        <p class="page-sub">处理用户售后申请、代商家操作，并仲裁平台介入工单</p>
      </div>
    </div>

    <div class="filter-row">
      <button
        v-for="t in TABS"
        :key="t.key"
        class="chip"
        :class="[{ active: filter === t.key }, { 'chip-appeal': t.key === 'appealing' }]"
        @click="setFilter(t.key)"
      >
        {{ t.label }}
        <span
          v-if="TAB_BADGE[t.key] && (stat[TAB_BADGE[t.key]] || 0) > 0"
          class="chip-badge"
          :class="{ 'badge-appeal': t.key === 'appealing' }"
        >{{ stat[TAB_BADGE[t.key]] }}</span>
      </button>
    </div>

    <section class="table-card">
      <div v-if="loading" class="empty-tip">加载中...</div>
      <div v-else-if="!refunds.length" class="empty-tip">暂无售后申请</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>售后单</th>
            <th>订单号</th>
            <th>店铺</th>
            <th>用户</th>
            <th>类型</th>
            <th>金额</th>
            <th>原因</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in refunds" :key="r.id">
            <td class="mono">#{{ r.id }}</td>
            <td class="mono">{{ r.order_no }}</td>
            <td>{{ r.store_name || '—' }}</td>
            <td>{{ r.userName || '—' }}</td>
            <td><span class="type-tag">{{ r.typeText || r.refund_type }}</span></td>
            <td class="amount">
              {{ fmtAmount(r.amount) }}
              <span v-if="r.refund_amount && Number(r.refund_amount) !== Number(r.amount)" class="refund-amt">
                / 退 {{ fmtAmount(r.refund_amount) }}
              </span>
            </td>
            <td class="reason">{{ r.reason || '—' }}</td>
            <td>
              <span class="status-pill" :class="statusMeta(r.status).cls">
                <span class="dot"></span>{{ r.statusText || statusMeta(r.status).label }}
              </span>
            </td>
            <td>
              <div class="action-row">
                <button class="act" :class="a.cls" v-for="a in actionsFor(r)" :key="a.key" @click="handleAction(r, a.key)">
                  {{ a.label }}
                </button>
                <button class="act ghost" @click="openDetail(r)">查看</button>
              </div>
              <span v-if="!actionsFor(r).length" class="done-tip">{{ r.handle_note || '已处理' }}</span>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="refunds.length">
        <span class="page-info">共 {{ total }} 条</span>
        <div class="page-buttons">
          <button class="page-btn" @click="goPage(page - 1)"><span class="material-symbols-outlined">chevron_left</span></button>
          <button class="page-btn active">{{ page }}</button>
          <button class="page-btn" @click="goPage(page + 1)"><span class="material-symbols-outlined">chevron_right</span></button>
        </div>
      </div>
    </section>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="`售后单 #${current?.id ?? ''}`" size="520px" direction="rtl">
      <div v-if="current" class="detail">
        <div class="detail-head">
          <span class="type-tag big">{{ current.typeText || current.refund_type }}</span>
          <span class="status-pill" :class="statusMeta(current.status).cls">
            <span class="dot"></span>{{ current.statusText || statusMeta(current.status).label }}
          </span>
        </div>

        <div class="info-grid">
          <div class="info-item"><span class="k">订单号</span><span class="v mono">{{ current.order_no }}</span></div>
          <div class="info-item"><span class="k">店铺</span><span class="v">{{ current.store_name || '—' }}</span></div>
          <div class="info-item"><span class="k">买家</span><span class="v">{{ current.userName || '—' }}</span></div>
          <div class="info-item"><span class="k">申请金额</span><span class="v">{{ fmtAmount(current.amount) }}</span></div>
          <div class="info-item"><span class="k">实退/实结金额</span><span class="v">{{ fmtAmount(current.refund_amount) }}</span></div>
          <div class="info-item"><span class="k">申请时间</span><span class="v">{{ current.created_at || '—' }}</span></div>
        </div>

        <div class="block">
          <div class="block-title">申请原因</div>
          <div class="block-body">{{ current.reason || '—' }}</div>
          <div class="img-row" v-if="toArray(current.evidence_images).length">
            <img v-for="(img, i) in toArray(current.evidence_images)" :key="i" :src="img" class="thumb" alt="凭证" />
          </div>
        </div>

        <div class="block" v-if="current.logistics_no">
          <div class="block-title">用户寄回物流</div>
          <div class="block-body">{{ current.logistics_company || '快递' }} · {{ current.logistics_no }}</div>
        </div>

        <div class="block" v-if="current.new_logistics_no">
          <div class="block-title">换货重发物流</div>
          <div class="block-body">{{ current.new_logistics_company || '快递' }} · {{ current.new_logistics_no }}</div>
        </div>

        <div class="block" v-if="current.appeal_reason || current.appeal_images">
          <div class="block-title">平台介入申请</div>
          <div class="block-body">{{ current.appeal_reason || '—' }}</div>
          <div class="img-row" v-if="toArray(current.appeal_images).length">
            <img v-for="(img, i) in toArray(current.appeal_images)" :key="i" :src="img" class="thumb" alt="介入凭证" />
          </div>
        </div>

        <div class="block" v-if="current.arbitrate_result">
          <div class="block-title">平台仲裁结果</div>
          <div class="block-body">
            <strong :class="current.arbitrate_result === 'refund' ? 'ok' : 'no'">
              {{ current.arbitrate_result === 'refund' ? '支持买家（退款）' : '维持商家处理' }}
            </strong>
            <div v-if="current.arbitrate_note" class="arbitrate-note">{{ current.arbitrate_note }}</div>
          </div>
        </div>

        <div class="block">
          <div class="block-title">进度时间线</div>
          <div v-if="timelineLoading" class="block-body muted">加载中...</div>
          <ul v-else class="timeline">
            <li v-for="(t, i) in timeline" :key="i" class="tl-item">
              <div class="tl-dot" :class="`tl-${t.action}`"></div>
              <div class="tl-main">
                <div class="tl-line">
                  <strong>{{ ACTOR_LABEL[t.actor_type] || t.actor_type || '系统' }}</strong>
                  {{ ACTION_LABEL[t.action] || t.action }}
                  <span class="tl-status" v-if="t.from_status && t.to_status">
                    {{ statusMeta(t.from_status).label }} → {{ statusMeta(t.to_status).label }}
                  </span>
                </div>
                <div class="tl-note" v-if="t.note">{{ t.note }}</div>
                <div class="tl-time">{{ t.created_at }}</div>
              </div>
            </li>
            <li v-if="!timeline.length" class="tl-empty">暂无流转记录</li>
          </ul>
        </div>

        <div class="detail-actions">
          <button
            v-for="a in actionsFor(current)"
            :key="a.key"
            class="act"
            :class="a.cls"
            @click="handleAction(current, a.key)"
          >{{ a.label }}</button>
        </div>
      </div>
    </el-drawer>

    <!-- 平台仲裁弹窗 -->
    <el-dialog v-model="arbitrateVisible" title="平台仲裁" width="460px">
      <div class="arbitrate-form">
        <div class="arbitrate-tip">
          该售后单买家已申请平台介入。请作出裁定：裁定结果将直接结案，并展示给买家与商家。
        </div>
        <div class="form-row">
          <span class="form-label">裁定结果</span>
          <el-radio-group v-model="arbitrateResult">
            <el-radio value="refund">支持买家（退款结案）</el-radio>
            <el-radio value="reject">维持商家处理</el-radio>
          </el-radio-group>
        </div>
        <div class="form-row">
          <span class="form-label">仲裁说明</span>
          <el-input
            v-model="arbitrateNote"
            type="textarea"
            :rows="3"
            placeholder="必填，将展示给买家与商家"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="arbitrateVisible = false">取消</el-button>
        <el-button type="primary" @click="submitArbitrate">提交裁定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.refund-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }

.filter-row { display: flex; gap: $spacing-sm; flex-wrap: wrap; }
.chip {
  display: inline-flex; align-items: center; gap: 6px;
  padding: $spacing-xs $spacing-lg; border-radius: $radius-full; border: 1px solid $color-outline-variant;
  background: $color-surface-container-lowest; font-size: $font-size-label-md; cursor: pointer; color: $color-on-surface-variant;
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
  &.chip-appeal.active { background: $color-emergency; border-color: $color-emergency; }
}
.chip-badge {
  min-width: 18px; height: 18px; padding: 0 5px; border-radius: 9px; background: $color-surface-container;
  color: $color-on-surface-variant; font-size: 11px; font-weight: 700; display: inline-flex; align-items: center; justify-content: center;
  .chip.active & { background: rgba(255,255,255,.25); color: #fff; }
  &.badge-appeal { background: $color-emergency; color: #fff; }
  .chip.active.chip-appeal & { background: rgba(255,255,255,.3); color: #fff; }
}

.table-card { background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; overflow: hidden; }
.empty-tip { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }
.data-table { width: 100%; border-collapse: collapse; }
.data-table th {
  text-align: left; padding: $spacing-md; font-size: $font-size-label-sm; color: $color-on-surface-variant; font-weight: 600;
  background: $color-surface-container-low; border-bottom: 1px solid $color-outline-variant;
}
.data-table td { padding: $spacing-md; border-bottom: 1px solid $color-outline-variant; font-size: $font-size-body-md; vertical-align: middle; }
.mono { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.amount { font-weight: 700; color: $color-on-surface; }
.refund-amt { font-weight: 600; color: $color-danger; font-size: $font-size-label-sm; }
.reason { max-width: 220px; color: $color-on-surface-variant; }

.type-tag {
  display: inline-block; padding: 2px 10px; border-radius: $radius-sm; background: $color-surface-container;
  color: $color-on-surface-variant; font-size: $font-size-label-sm; font-weight: 600;
  &.big { font-size: $font-size-label-md; padding: 4px 14px; }
}

.status-pill { display: inline-flex; align-items: center; gap: $spacing-xs; font-size: $font-size-label-sm; font-weight: 700; }
.status-pill .dot { width: 8px; height: 8px; border-radius: $radius-full; }
.status-pill.pending { color: $color-warning; .dot { background: $color-warning; } }
.status-pill.progress { color: $color-brand; .dot { background: $color-brand; } }
.status-pill.returned { color: $color-brand; .dot { background: $color-brand; } }
.status-pill.reshipped { color: $color-brand; .dot { background: $color-brand; } }
.status-pill.success { color: $color-success; .dot { background: $color-success; } }
.status-pill.exchanged { color: $color-success; .dot { background: $color-success; } }
.status-pill.danger { color: $color-emergency; .dot { background: $color-emergency; } }
.status-pill.appeal { color: $color-emergency; .dot { background: $color-emergency; } }
.status-pill.arbitrated { color: $color-on-surface-variant; .dot { background: $color-on-surface-variant; } }
.status-pill.closed { color: $color-on-surface-variant; .dot { background: $color-on-surface-variant; } }

.action-row { display: flex; gap: $spacing-sm; flex-wrap: wrap; align-items: center; }
.act {
  display: inline-flex; align-items: center; padding: 4px $spacing-md; border-radius: $radius-md; border: none;
  font-size: $font-size-label-sm; font-weight: 700; cursor: pointer; color: #fff;
  &.success { background: $color-success; }
  &.danger { background: $color-emergency; }
  &.primary { background: $color-brand; }
  &.ghost { background: transparent; color: $color-on-surface-variant; border: 1px solid $color-outline-variant; }
}
.done-tip { font-size: $font-size-label-sm; color: $color-on-surface-variant; max-width: 160px; }

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

// 详情抽屉
.detail { display: flex; flex-direction: column; gap: $spacing-md; }
.detail-head { display: flex; align-items: center; gap: $spacing-sm; }
.info-grid { display: grid; grid-template-columns: 1fr 1fr; gap: $spacing-sm $spacing-lg; }
.info-item { display: flex; flex-direction: column; gap: 2px; }
.info-item .k { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.info-item .v { font-size: $font-size-body-md; color: $color-on-surface; font-weight: 600; }

.block { border-top: 1px solid $color-outline-variant; padding-top: $spacing-md; }
.block-title { font-size: $font-size-label-md; font-weight: 700; color: $color-on-surface; margin-bottom: $spacing-xs; }
.block-body { font-size: $font-size-body-md; color: $color-on-surface-variant; line-height: 1.6; }
.block-body.muted { color: $color-on-surface-variant; opacity: .7; }
.arbitrate-note { margin-top: 6px; padding: $spacing-sm; background: $color-surface-container-low; border-radius: $radius-sm; font-size: $font-size-label-md; }
.ok { color: $color-success; }
.no { color: $color-emergency; }

.img-row { display: flex; gap: $spacing-sm; flex-wrap: wrap; margin-top: $spacing-xs; }
.thumb { width: 76px; height: 76px; object-fit: cover; border-radius: $radius-sm; border: 1px solid $color-outline-variant; }

.timeline { list-style: none; margin: 0; padding: 0; position: relative; }
.tl-item { display: flex; gap: $spacing-md; padding-bottom: $spacing-md; position: relative; }
.tl-item::before {
  content: ''; position: absolute; left: 5px; top: 14px; bottom: -4px; width: 2px; background: $color-outline-variant;
}
.tl-item:last-child::before { display: none; }
.tl-dot { width: 12px; height: 12px; border-radius: 50%; background: $color-brand; flex-shrink: 0; margin-top: 4px; z-index: 1; }
.tl-dot.tl-apply { background: $color-brand; }
.tl-dot.tl-approve { background: $color-success; }
.tl-dot.tl-reject { background: $color-emergency; }
.tl-dot.tl-ship { background: $color-brand; }
.tl-dot.tl-confirm { background: $color-success; }
.tl-dot.tl-reship { background: $color-brand; }
.tl-dot.tl-receive { background: $color-success; }
.tl-dot.tl-appeal { background: $color-emergency; }
.tl-dot.tl-arbitrate { background: $color-emergency; }
.tl-dot.tl-close { background: $color-on-surface-variant; }
.tl-main { flex: 1; }
.tl-line { font-size: $font-size-body-md; color: $color-on-surface; }
.tl-status { font-size: $font-size-label-sm; color: $color-on-surface-variant; margin-left: 6px; }
.tl-note { font-size: $font-size-label-md; color: $color-on-surface-variant; margin-top: 2px; line-height: 1.5; }
.tl-time { font-size: $font-size-label-sm; color: $color-on-surface-variant; opacity: .8; margin-top: 2px; }
.tl-empty { color: $color-on-surface-variant; font-size: $font-size-label-md; padding: $spacing-xs 0; }

.detail-actions { display: flex; gap: $spacing-sm; border-top: 1px solid $color-outline-variant; padding-top: $spacing-md; }

// 仲裁弹窗
.arbitrate-form { display: flex; flex-direction: column; gap: $spacing-md; }
.arbitrate-tip { font-size: $font-size-label-md; color: $color-on-surface-variant; background: $color-error-container; padding: $spacing-sm $spacing-md; border-radius: $radius-sm; line-height: 1.6; }
.form-row { display: flex; flex-direction: column; gap: $spacing-xs; }
.form-label { font-size: $font-size-label-md; font-weight: 700; color: $color-on-surface; }
</style>
