<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type AdminMerchant } from '@/api/modules/admin'

const router = useRouter()

// 视图模型
interface Merchant extends AdminMerchant {
  // 显示用派生字段
  displayStatus: 'active' | 'banned' | 'removed'
  displayType: string
  displayAddress: string
  displayOwner: string
}

const merchants = ref<Merchant[]>([])
const loading = ref(false)
const selected = ref<number[]>([])

// 类型/状态映射
const TYPE_LABEL: Record<string, string> = {
  grooming: '美容洗护',
  medical: '宠物医疗',
  boarding: '寄养',
  comprehensive: '综合服务',
}
const STATUS_MAP: Record<number, { label: string; cls: string }> = {
  0: { label: '营业中', cls: 'active' },
  1: { label: '已封禁', cls: 'banned' },
  2: { label: '已注销', cls: 'removed' },
}

const toViewMerchant = (m: AdminMerchant): Merchant => {
  const statusKey = STATUS_MAP[m.status] ? m.status : 0
  return {
    ...m,
    displayStatus: statusKey === 0 ? 'active' : statusKey === 1 ? 'banned' : 'removed',
    displayType: TYPE_LABEL[m.type] || m.type || '-',
    displayAddress: m.address || '-',
    displayOwner: m.ownerName || '-',
  }
}

// 分页 + 筛选
const pagination = reactive({ page: 1, size: 20, total: 0 })
const filters = reactive({
  status: -1,
  type: '',
  keyword: '',
  dateFrom: '',
  dateTo: '',
})

async function load() {
  loading.value = true
  try {
    const res = await adminApi.merchantList({
      page: pagination.page,
      size: pagination.size,
      status: filters.status < 0 ? undefined : Number(filters.status),
      type: filters.type || undefined,
      keyword: filters.keyword.trim() || undefined,
      dateFrom: filters.dateFrom || undefined,
      dateTo: filters.dateTo || undefined,
    })
    merchants.value = (res.list || []).map(toViewMerchant)
    pagination.total = res.total ?? 0
    selected.value = []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载商家列表失败')
    merchants.value = []
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  pagination.page = 1
  load()
}
function resetFilter() {
  filters.status = -1
  filters.type = ''
  filters.keyword = ''
  filters.dateFrom = ''
  filters.dateTo = ''
  pagination.page = 1
  load()
}

const totalPages = computed(() => Math.max(1, Math.ceil(pagination.total / pagination.size)))
const pageList = computed(() => {
  const tp = totalPages.value
  const cur = pagination.page
  const start = Math.max(1, cur - 2)
  const end = Math.min(tp, start + 4)
  const arr: number[] = []
  for (let i = start; i <= end; i++) arr.push(i)
  return arr
})
function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  pagination.page = p
  load()
}

onMounted(load)

// 概览 KPI（复用 adminApi.overview 里的 totalMerchants / pendingAudits）
const kpis = ref([
  { label: '已入驻商家', value: '—', icon: 'storefront', color: 'blue' },
  { label: '营业中', value: '—', icon: 'verified_user', color: 'green' },
  { label: '已封禁', value: '—', icon: 'block', color: 'red' },
  { label: '待审核申请', value: '—', icon: 'pending_actions', color: 'orange' },
])
async function loadOverview() {
  try {
    const ov: any = await adminApi.overview()
    kpis.value[0].value = (ov.totalMerchants ?? 0).toLocaleString()
    kpis.value[1].value = (ov.activeMerchants ?? ov.totalMerchants ?? 0).toLocaleString()
    kpis.value[2].value = (ov.bannedMerchants ?? 0).toLocaleString()
    kpis.value[3].value = (ov.pendingAudits ?? 0).toLocaleString()
  } catch {
    // 静默
  }
}
onMounted(loadOverview)

// 选择
function toggleOne(id: number) {
  const i = selected.value.indexOf(id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(id)
}
const allSelected = computed(
  () => merchants.value.length > 0 && selected.value.length === merchants.value.length,
)
function toggleAll() {
  if (allSelected.value) selected.value = []
  else selected.value = merchants.value.map(m => m.id)
}

// 操作：封禁 / 解封 / 强制解除
async function doToggleBan(m: Merchant) {
  const willBan = m.status === 0
  try {
    const { value: reason } = await ElMessageBox.prompt(
      willBan ? '请填写封禁原因（必填）' : '解封备注（选填）',
      willBan ? '封禁商家' : '解封商家',
      {
        inputType: 'textarea',
        inputPlaceholder: willBan ? '违规原因将记录在操作日志中' : '可填写解封原因',
        confirmButtonText: willBan ? '确认封禁' : '确认解封',
        cancelButtonText: '取消',
      },
    )
    if (willBan && !reason?.trim()) {
      ElMessage.warning('封禁原因不能为空')
      return
    }
    await adminApi.toggleMerchantStatus(m.id, willBan ? 1 : 0, reason?.trim() || undefined)
    m.status = willBan ? 1 : 0
    ElMessage.success(willBan ? '已封禁' : '已解封')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

// 强制解除
const forceVisible = ref(false)
const forceForm = reactive({ id: 0, name: '', reason: '' })
function openForceRemove(m: Merchant) {
  forceForm.id = m.id
  forceForm.name = m.name
  forceForm.reason = ''
  forceVisible.value = true
}
async function submitForceRemove() {
  if (!forceForm.reason.trim()) {
    ElMessage.warning('请填写解除原因')
    return
  }
  try {
    await adminApi.forceRemoveMerchant(forceForm.id, forceForm.reason.trim())
    ElMessage.success('已强制解除商家关联')
    forceVisible.value = false
    load()
    loadOverview()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// 批量封禁
async function batchBan() {
  if (!selected.value.length) {
    ElMessage.warning('请先勾选要封禁的商家')
    return
  }
  const banable = merchants.value
    .filter(m => selected.value.includes(m.id) && m.status === 0)
    .map(m => m.id)
  if (!banable.length) {
    ElMessage.warning('所选商家中没有可封禁的（仅营业中可封禁）')
    return
  }
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `确定封禁已选中的 ${banable.length} 家营业中商家？`,
      '批量封禁',
      {
        inputType: 'textarea',
        inputPlaceholder: '封禁原因（必填）',
        confirmButtonText: '确认封禁',
        cancelButtonText: '取消',
      },
    )
    if (!reason?.trim()) {
      ElMessage.warning('封禁原因不能为空')
      return
    }
    let ok = 0
    for (const id of banable) {
      try {
        await adminApi.toggleMerchantStatus(id, 1, reason.trim())
        const m = merchants.value.find(x => x.id === id)
        if (m) m.status = 1
        ok++
      } catch {
        // 单条失败不影响其余
      }
    }
    ElMessage.success(`已封禁 ${ok} 家商家`)
    selected.value = []
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
  }
}

function viewDetail(m: Merchant) {
  router.push('/admin/merchant/detail/' + m.id)
}

function formatDate(d?: string | null) {
  if (!d) return '-'
  return String(d).slice(0, 10)
}
function statusLabel(s: number) {
  return STATUS_MAP[s]?.label || '未知'
}
</script>

<template>
  <div class="merchant-mgmt-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">商家管理</h1>
        <p class="page-sub">查看、封禁或解除已入驻的 PetMate 商家</p>
      </div>
    </div>

    <!-- KPI -->
    <div class="kpi-grid">
      <div v-for="k in kpis" :key="k.label" class="kpi-card">
        <div class="kpi-icon" :class="`bg-${k.color}`">
          <span class="material-symbols-outlined">{{ k.icon }}</span>
        </div>
        <p class="kpi-label">{{ k.label }}</p>
        <h3 class="kpi-value">{{ k.value }}</h3>
      </div>
    </div>

    <!-- 筛选 -->
    <section class="filter-card">
      <div class="filter-row">
        <div class="filter-group">
          <span class="filter-label">商家状态:</span>
          <select class="select-btn" v-model.number="filters.status" @change="applyFilter">
            <option :value="-1">全部状态</option>
            <option :value="0">营业中</option>
            <option :value="1">已封禁</option>
            <option :value="2">已注销</option>
          </select>
        </div>
        <div class="filter-group">
          <span class="filter-label">服务类型:</span>
          <select class="select-btn" v-model="filters.type" @change="applyFilter">
            <option value="">全部类型</option>
            <option value="grooming">美容洗护</option>
            <option value="medical">宠物医疗</option>
            <option value="boarding">寄养</option>
            <option value="comprehensive">综合服务</option>
          </select>
        </div>
        <div class="filter-group">
          <span class="filter-label">关键字:</span>
          <input
            class="select-btn"
            v-model="filters.keyword"
            placeholder="门店名 / 店主 / 手机号"
            @keyup.enter="applyFilter"
          />
        </div>
        <div class="filter-group">
          <span class="filter-label">入驻:</span>
          <input type="date" class="select-btn" v-model="filters.dateFrom" @change="applyFilter" />
          <span class="filter-label">至</span>
          <input type="date" class="select-btn" v-model="filters.dateTo" @change="applyFilter" />
        </div>
        <button class="filter-reset" @click="resetFilter" title="重置筛选">
          <span class="material-symbols-outlined">refresh</span>
        </button>
        <button class="btn-primary" @click="applyFilter">筛选</button>
      </div>
    </section>

    <!-- 批量 -->
    <div class="batch-bar">
      <label class="batch-check">
        <input type="checkbox" :checked="allSelected" @change="toggleAll" />
        已选 <span class="count">{{ selected.length }}</span> 家商家
      </label>
      <div class="batch-actions">
        <button class="batch-btn" @click="batchBan">
          <span class="material-symbols-outlined">block</span>
          批量封禁
        </button>
      </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <div v-if="loading" class="empty-tip">加载中...</div>
      <div v-else-if="!merchants.length" class="empty-tip">暂无商家数据</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th class="checkbox-col">
              <input type="checkbox" :checked="allSelected" @change="toggleAll" />
            </th>
            <th>门店</th>
            <th>店主</th>
            <th>类型</th>
            <th>地址</th>
            <th>入驻时间</th>
            <th>状态</th>
            <th class="action-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in merchants" :key="m.id" class="row-click" @click="viewDetail(m)">
            <td class="checkbox-col" @click.stop>
              <input
                type="checkbox"
                :checked="selected.includes(m.id)"
                @change="toggleOne(m.id)"
              />
            </td>
            <td>
              <div class="store-cell">
                <span class="store-icon">
                  <span class="material-symbols-outlined">storefront</span>
                </span>
                <div>
                  <div class="store-name">{{ m.name }}</div>
                  <div class="store-id">ID: M_{{ m.id }}</div>
                </div>
              </div>
            </td>
            <td>
              <div class="owner-cell">
                <span class="owner-name">{{ m.displayOwner }}</span>
                <span class="owner-phone">{{ m.ownerPhone }}</span>
              </div>
            </td>
            <td>
              <span class="type-tag" :class="`type-${m.type}`">{{ m.displayType }}</span>
            </td>
            <td class="addr-cell">{{ m.displayAddress }}</td>
            <td class="date-cell">{{ formatDate(m.approvedAt || m.submitDate) }}</td>
            <td>
              <span class="status-pill" :class="STATUS_MAP[m.status]?.cls">
                <span class="dot"></span>{{ statusLabel(m.status) }}
              </span>
            </td>
            <td class="action-col" @click.stop>
              <button class="icon-btn" @click="viewDetail(m)" title="查看详情">
                <span class="material-symbols-outlined">visibility</span>
              </button>
              <button
                v-if="m.status === 0"
                class="icon-btn ban"
                @click="doToggleBan(m)"
                title="封禁"
              >
                <span class="material-symbols-outlined">block</span>
              </button>
              <button
                v-else-if="m.status === 1"
                class="icon-btn unban"
                @click="doToggleBan(m)"
                title="解封"
              >
                <span class="material-symbols-outlined">lock_open</span>
              </button>
              <button
                v-if="m.status !== 2"
                class="icon-btn danger"
                @click="openForceRemove(m)"
                title="强制解除"
              >
                <span class="material-symbols-outlined">delete_forever</span>
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <span class="page-info">
          显示 {{ (pagination.page - 1) * pagination.size + 1 }} 到
          {{ Math.min(pagination.page * pagination.size, pagination.total) }}
          共 {{ pagination.total }} 条
        </span>
        <div class="page-buttons">
          <button class="page-btn" :disabled="pagination.page <= 1" @click="goPage(pagination.page - 1)">
            <span class="material-symbols-outlined">chevron_left</span>
          </button>
          <button
            v-for="p in pageList"
            :key="p"
            class="page-btn"
            :class="{ active: p === pagination.page }"
            @click="goPage(p)"
          >{{ p }}</button>
          <button class="page-btn" :disabled="pagination.page >= totalPages" @click="goPage(pagination.page + 1)">
            <span class="material-symbols-outlined">chevron_right</span>
          </button>
        </div>
      </div>
    </div>

    <!-- 强制解除弹窗 -->
    <el-dialog v-model="forceVisible" title="强制解除商家" width="92%" top="8vh" class="admin-dialog">
      <div class="dialog-body">
        <p class="warn-text">
          <span class="material-symbols-outlined">warning</span>
          该操作将下架门店、禁用店主账号并解除关联，<strong>不可恢复</strong>。请填写具体原因。
        </p>
        <div class="field">
          <label class="field-label">商家</label>
          <input :value="forceForm.name" disabled class="dlg-input" />
        </div>
        <div class="field">
          <label class="field-label">解除原因 <span class="req">*</span></label>
          <textarea v-model="forceForm.reason" class="dlg-input" rows="3" placeholder="严重违规 / 投诉 / 法务要求等"></textarea>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <button class="btn-outline" @click="forceVisible = false">取消</button>
          <button class="btn-danger" @click="submitForceRemove">确认解除</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.merchant-mgmt-view {
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
.btn-primary,
.btn-outline,
.btn-danger {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: none;
}
.btn-primary { background: $color-brand; color: #fff; }
.btn-outline {
  background: transparent;
  border: 1px solid $color-outline-variant;
  color: $color-on-surface;
}
.btn-danger {
  background: $color-emergency;
  color: #fff;
}

// KPI
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;
  @include desktop { grid-template-columns: repeat(4, 1fr); }
}
.kpi-card {
  display: flex;
  flex-direction: column;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.kpi-icon {
  width: 40px;
  height: 40px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: $spacing-md;
  &.bg-blue { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  &.bg-orange { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.bg-green { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.bg-red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
  .material-symbols-outlined { font-size: 22px; }
}
.kpi-label { font-size: $font-size-label-md; color: $color-on-surface-variant; margin-bottom: 4px; }
.kpi-value {
  font-family: $font-family-headline;
  font-size: 28px;
  font-weight: 700;
  color: $color-on-surface;
}

// Filter
.filter-card {
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-md;
}
.filter-group { display: flex; align-items: center; gap: $spacing-xs; }
.filter-label { font-size: $font-size-label-md; color: $color-on-surface-variant; }
.select-btn {
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  background: #fff;
  font-size: $font-size-label-md;
  cursor: pointer;
  font-family: inherit;
}
.filter-reset {
  width: 36px;
  height: 36px;
  background: $color-surface-container;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-on-surface-variant;
  cursor: pointer;
  border: none;
}

// Batch
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-low;
  border-radius: $radius-md;
}
.batch-check {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-md;
  cursor: pointer;
  .count { color: $color-brand; font-weight: 700; }
}
.batch-actions { display: flex; align-items: center; gap: $spacing-sm; }
.batch-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  background: none;
  border: none;
  &:hover { color: $color-emergency; }
}

// Table
.table-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  overflow: hidden;
}
.empty-tip {
  padding: $spacing-xl;
  text-align: center;
  color: $color-on-surface-variant;
}
.data-table { width: 100%; border-collapse: collapse; }
.data-table th {
  text-align: left;
  padding: $spacing-md;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  font-weight: 600;
  background: $color-surface-container-low;
  border-bottom: 1px solid $color-outline-variant;
}
.data-table td {
  padding: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
  font-size: $font-size-body-md;
  vertical-align: top;
}
.action-col { width: 160px; }
.checkbox-col { width: 40px; }
.row-click { cursor: pointer; transition: background 0.15s; }
.row-click:hover { background: rgba($color-brand, 0.04); }

.store-cell { display: flex; align-items: center; gap: $spacing-sm; }
.store-icon {
  width: 40px;
  height: 40px;
  border-radius: $radius-md;
  background: rgba($color-brand, 0.08);
  color: $color-brand;
  display: flex;
  align-items: center;
  justify-content: center;
  .material-symbols-outlined { font-size: 22px; }
}
.store-name { font-weight: 700; color: $color-on-surface; }
.store-id {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  font-family: $font-family-mono;
}
.owner-cell { display: flex; flex-direction: column; gap: 2px; }
.owner-name { font-weight: 600; color: $color-on-surface; }
.owner-phone {
  font-family: $font-family-mono;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.addr-cell {
  max-width: 220px;
  color: $color-on-surface-variant;
  font-size: $font-size-label-sm;
  word-break: break-all;
}
.date-cell {
  font-family: $font-family-mono;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.type-tag {
  display: inline-block;
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  font-size: $font-size-label-sm;
  font-weight: 600;
  background: $color-surface-container-low;
  color: $color-on-surface-variant;
  &.type-grooming { background: #fff1e6; color: #c2410c; }
  &.type-medical { background: #fde8e8; color: #c81e1e; }
  &.type-boarding { background: #e8f5e9; color: #2e7d32; }
  &.type-comprehensive { background: #e3f2fd; color: #1565c0; }
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-sm;
  font-weight: 700;
  .dot { width: 8px; height: 8px; border-radius: $radius-full; }
  &.active { color: $color-success; .dot { background: $color-success; } }
  &.banned { color: $color-emergency; .dot { background: $color-emergency; } }
  &.removed { color: $color-on-surface-variant; .dot { background: $color-on-surface-variant; } }
}
.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: $color-on-surface-variant;
  cursor: pointer;
  background: none;
  border: none;
  padding: 4px;
  &:hover { color: $color-brand; }
  &.ban { color: $color-emergency; &:hover { opacity: 0.7; } }
  &.unban { color: $color-success; &:hover { opacity: 0.7; } }
  &.danger { color: $color-emergency; &:hover { opacity: 0.7; } }
  .material-symbols-outlined { font-size: 18px; }
}

// Pagination
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md $spacing-lg;
  border-top: 1px solid $color-outline-variant;
}
.page-info { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.page-buttons { display: flex; gap: $spacing-xs; }
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
  &:disabled { opacity: 0.4; cursor: not-allowed; }
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
  .material-symbols-outlined { font-size: 16px; }
}

// Dialog
.admin-dialog {
  border-radius: $radius-xl;
  overflow: hidden;
  :deep(.el-dialog__header) { margin: 0; padding: $spacing-lg $spacing-lg $spacing-sm; font-weight: 700; }
  :deep(.el-dialog__title) { font-family: $font-family-headline; font-weight: 700; color: $color-on-surface; }
  :deep(.el-dialog__body) { padding: $spacing-md $spacing-lg; }
}
.dialog-body { display: flex; flex-direction: column; gap: $spacing-md; }
.field { display: flex; flex-direction: column; gap: $spacing-xs; }
.field-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  font-weight: 600;
  .req { color: $color-emergency; }
}
.dlg-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  background: $color-surface-container-lowest;
  color: $color-on-surface;
  font-family: inherit;
  &:focus { outline: none; border-color: $color-brand; box-shadow: 0 0 0 3px rgba($color-brand, 0.15); }
}
.warn-text {
  display: flex;
  align-items: flex-start;
  gap: $spacing-xs;
  padding: $spacing-md;
  background: rgba($color-emergency, 0.08);
  border-radius: $radius-md;
  color: $color-emergency;
  font-size: $font-size-body-md;
  .material-symbols-outlined { font-size: 20px; }
  strong { font-weight: 700; }
}
.modal-footer {
  display: flex;
  gap: $spacing-md;
  justify-content: flex-end;
  padding: $spacing-md $spacing-lg $spacing-lg;
  border-top: 1px solid $color-outline-variant;
  .btn-primary, .btn-outline, .btn-danger { flex: 0 0 auto; min-width: 96px; justify-content: center; }
}
</style>
