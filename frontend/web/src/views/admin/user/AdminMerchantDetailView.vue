<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

const route = useRoute()
const router = useRouter()
const merchantId = Number(route.params.id)

const loading = ref(false)
const detail = ref<any>(null)

// 状态/类型映射
const STATUS_MAP: Record<number, { label: string; cls: string }> = {
  0: { label: '营业中', cls: 'active' },
  1: { label: '已封禁', cls: 'banned' },
  2: { label: '已注销', cls: 'removed' },
}
const TYPE_LABEL: Record<string, string> = {
  grooming: '美容洗护',
  medical: '宠物医疗',
  boarding: '寄养',
  comprehensive: '综合服务',
}
const SUBJECT_LABEL: Record<string, string> = {
  individual: '个体工商户',
  enterprise: '企业商家',
  personal: '个人入驻',
}
const WEEKDAYS = [
  { key: 'mon', label: '周一' },
  { key: 'tue', label: '周二' },
  { key: 'wed', label: '周三' },
  { key: 'thu', label: '周四' },
  { key: 'fri', label: '周五' },
  { key: 'sat', label: '周六' },
  { key: 'sun', label: '周日' },
]

// ============ 工具函数（与申请审核抽屉保持一致） ============
function parseAddress(addr: string | undefined | null): { chips: string[]; detail: string; bad: boolean } {
  const raw = (addr || '').trim()
  if (!raw) return { chips: [], detail: '', bad: false }
  if (!/[一-龥]/.test(raw)) return { chips: [], detail: '', bad: true }
  const parts = raw.split(/\s+/).filter(Boolean)
  if (parts.length === 1) return { chips: [], detail: raw, bad: false }
  const deduped: string[] = []
  for (const p of parts) if (!deduped.length || deduped[deduped.length - 1] !== p) deduped.push(p)
  const clean = deduped.filter((s) => !(/^\d{5,}$/.test(s) && !/[一-龥]/.test(s)))
  if (!clean.length) return { chips: [], detail: '', bad: true }
  const detail = clean.length > 1 ? clean[clean.length - 1] : ''
  const chips = clean.slice(0, -1)
  return { chips, detail, bad: false }
}
function parseBusinessHours(json: any): Record<string, string> {
  if (!json) return {}
  try {
    if (typeof json === 'string') return JSON.parse(json)
    if (typeof json === 'object') return json
  } catch {}
  return {}
}
function imgUrl(p?: string | null): string {
  if (!p) return ''
  if (p.startsWith('http')) return p
  return p.startsWith('/api/v1') ? p : '/api/v1' + (p.startsWith('/') ? p : '/' + p)
}
function photosArr(p: any): string[] {
  if (!p) return []
  try {
    const arr = typeof p === 'string' ? JSON.parse(p) : p
    return Array.isArray(arr) ? arr.filter(Boolean) : []
  } catch {
    return []
  }
}

async function load() {
  loading.value = true
  try {
    const data: any = await adminApi.merchantDetail(merchantId)
    detail.value = data
  } catch (e: any) {
    ElMessage.error(e?.message || '加载商家详情失败')
  } finally {
    loading.value = false
  }
}

// 操作
async function doToggleBan() {
  if (!detail.value) return
  const willBan = detail.value.status === 0
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
    await adminApi.toggleMerchantStatus(merchantId, willBan ? 1 : 0, reason?.trim() || undefined)
    detail.value.status = willBan ? 1 : 0
    ElMessage.success(willBan ? '已封禁' : '已解封')
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

// 强制解除
const forceVisible = ref(false)
const forceReason = ref('')
function openForceRemove() {
  forceReason.value = ''
  forceVisible.value = true
}
async function submitForceRemove() {
  if (!forceReason.value.trim()) {
    ElMessage.warning('请填写解除原因')
    return
  }
  try {
    await adminApi.forceRemoveMerchant(merchantId, forceReason.value.trim())
    ElMessage.success('已强制解除商家关联')
    forceVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const previewVisible = ref(false)
const previewImageUrl = ref('')
function previewImage(url: string) {
  if (!url) return
  previewImageUrl.value = url
  previewVisible.value = true
}

function statusLabel(s: number) { return STATUS_MAP[s]?.label || '未知' }
function typeLabel(t: string) { return TYPE_LABEL[t] || t || '-' }
function subjectLabel(s: string) { return SUBJECT_LABEL[s] || s || '-' }
function formatDate(d?: string | null) {
  if (!d) return '-'
  return String(d).slice(0, 19).replace('T', ' ')
}

onMounted(load)
</script>

<template>
  <div class="merchant-detail-view">
    <div class="page-head">
      <div>
        <button class="back-btn" @click="router.back()">
          <span class="material-symbols-outlined">arrow_back</span> 返回
        </button>
        <h1 class="page-title">商家详情</h1>
        <p class="page-sub" v-if="detail">ID: M_{{ detail.id }}</p>
      </div>
      <div class="head-actions" v-if="detail">
        <button
          v-if="detail.status === 0"
          class="btn-outline danger"
          @click="doToggleBan"
        >
          <span class="material-symbols-outlined">block</span>封禁商家
        </button>
        <button
          v-else-if="detail.status === 1"
          class="btn-outline success"
          @click="doToggleBan"
        >
          <span class="material-symbols-outlined">lock_open</span>解封
        </button>
        <button
          v-if="detail.status !== 2"
          class="btn-danger"
          @click="openForceRemove"
        >
          <span class="material-symbols-outlined">delete_forever</span>强制解除
        </button>
      </div>
    </div>

    <div v-if="loading" class="empty-tip">加载中...</div>
    <div v-else-if="!detail" class="empty-tip">未找到该商家</div>
    <template v-else>
      <!-- 状态条 -->
      <div class="status-bar">
        <span class="status-pill lg" :class="STATUS_MAP[detail.status]?.cls">
          <span class="dot"></span>{{ statusLabel(detail.status) }}
        </span>
        <span class="meta-text">
          <span class="material-symbols-outlined">event</span>
          入驻：{{ formatDate(detail.createdAt || detail.approvedAt || detail.submitDate) }}
        </span>
        <span class="meta-text" v-if="detail.reviewNote">
          <span class="material-symbols-outlined">sticky_note_2</span>
          审核备注：{{ detail.reviewNote }}
        </span>
      </div>

      <!-- 店主信息 -->
      <section class="block">
        <h3 class="block-title"><span class="material-symbols-outlined">person</span>店主信息</h3>
        <div class="grid two">
          <div class="kv"><span class="k">姓名</span><span class="v">{{ detail.ownerName || '-' }}</span></div>
          <div class="kv"><span class="k">手机号</span><span class="v mono">{{ detail.ownerPhone || detail.phone || '-' }}</span></div>
          <div class="kv"><span class="k">身份证号</span><span class="v mono">{{ detail.idCard || '未填写' }}</span></div>
          <div class="kv">
            <span class="k">身份证</span>
            <span class="v pics">
              <span v-if="detail.idCardFrontUrl" class="pic-pill" @click="previewImage(imgUrl(detail.idCardFrontUrl))">
                <span class="material-symbols-outlined">id_card</span>正面
              </span>
              <span v-if="detail.idCardBackUrl" class="pic-pill" @click="previewImage(imgUrl(detail.idCardBackUrl))">
                <span class="material-symbols-outlined">id_card</span>反面
              </span>
              <span v-if="!detail.idCardFrontUrl && !detail.idCardBackUrl" class="muted">未上传</span>
            </span>
          </div>
        </div>
      </section>

      <!-- 门店信息 -->
      <section class="block">
        <h3 class="block-title"><span class="material-symbols-outlined">storefront</span>门店信息</h3>
        <div class="grid two">
          <div class="kv"><span class="k">门店名称</span><span class="v strong">{{ detail.name || detail.storeName || '-' }}</span></div>
          <div class="kv">
            <span class="k">服务类型</span>
            <span class="v"><span class="type-tag" :class="`type-${detail.type}`">{{ typeLabel(detail.type) }}</span></span>
          </div>
          <div class="kv"><span class="k">主体类型</span><span class="v">{{ subjectLabel(detail.subjectType) }}</span></div>
          <div class="kv"><span class="k">企业名称</span><span class="v">{{ detail.enterpriseName || '-' }}</span></div>
          <div class="kv"><span class="k">统一信用代码</span><span class="v mono">{{ detail.unifiedCreditCode || detail.licenseNo || '-' }}</span></div>
          <div class="kv"><span class="k">门店电话</span><span class="v mono">{{ detail.storePhone || '-' }}</span></div>
          <div class="kv full">
            <span class="k">营业执照</span>
            <span class="v">
              <span v-if="detail.businessLicenseUrl" class="pic-pill lg" @click="previewImage(imgUrl(detail.businessLicenseUrl))">
                <span class="material-symbols-outlined">image</span>查看大图
              </span>
              <span v-else class="muted">未上传</span>
            </span>
          </div>
        </div>
      </section>

      <!-- 地址 -->
      <section class="block">
        <h3 class="block-title"><span class="material-symbols-outlined">location_on</span>门店地址</h3>
        <template v-if="parseAddress(detail.address).bad">
          <span class="addr-bad">
            <span class="material-symbols-outlined">priority_high</span>
            地址信息异常
          </span>
          <div class="raw-text">{{ detail.address }}</div>
        </template>
        <template v-else-if="parseAddress(detail.address).chips.length || parseAddress(detail.address).detail">
          <div class="addr-display">
            <span
              v-for="(c, i) in parseAddress(detail.address).chips"
              :key="i"
              class="addr-chip lg"
            >{{ c }}</span>
            <span
              v-if="parseAddress(detail.address).detail"
              class="addr-detail lg"
            >{{ parseAddress(detail.address).detail }}</span>
          </div>
          <div v-if="detail.lng && detail.lng !== 'null'" class="coord">
            <span class="material-symbols-outlined">my_location</span>
            经纬度：{{ detail.lng }}, {{ detail.lat }}
          </div>
        </template>
        <span v-else class="muted">未填写地址</span>
      </section>

      <!-- 营业时间 -->
      <section class="block">
        <h3 class="block-title"><span class="material-symbols-outlined">schedule</span>营业时间</h3>
        <template v-if="Object.keys(parseBusinessHours(detail.businessHours)).length">
          <table class="hours-table">
            <tbody>
              <tr
                v-for="w in WEEKDAYS"
                :key="w.key"
                :class="{ rest: parseBusinessHours(detail.businessHours)[w.key] === '休息' }"
              >
                <th>{{ w.label }}</th>
                <td>{{ parseBusinessHours(detail.businessHours)[w.key] || '未填写' }}</td>
              </tr>
            </tbody>
          </table>
        </template>
        <span v-else class="muted">未填写营业时间</span>
      </section>

      <!-- 实景相册 -->
      <section class="block" v-if="photosArr(detail.photos).length">
        <h3 class="block-title"><span class="material-symbols-outlined">photo_library</span>实景相册</h3>
        <div class="photo-grid">
          <img
            v-for="(p, i) in photosArr(detail.photos)"
            :key="i"
            :src="imgUrl(p)"
            class="photo-thumb"
            @click="previewImage(imgUrl(p))"
            alt="门店实景"
          />
        </div>
      </section>

      <!-- 业务统计 -->
      <section class="block" v-if="detail.orderCount != null || detail.productCount != null || detail.serviceCount != null">
        <h3 class="block-title"><span class="material-symbols-outlined">insights</span>业务数据</h3>
        <div class="grid three">
          <div class="kpi-mini">
            <span class="material-symbols-outlined">receipt_long</span>
            <div>
              <p class="kpi-mini-label">订单数</p>
              <h4 class="kpi-mini-value">{{ detail.orderCount ?? 0 }}</h4>
            </div>
          </div>
          <div class="kpi-mini">
            <span class="material-symbols-outlined">inventory_2</span>
            <div>
              <p class="kpi-mini-label">在售商品</p>
              <h4 class="kpi-mini-value">{{ detail.productCount ?? 0 }}</h4>
            </div>
          </div>
          <div class="kpi-mini">
            <span class="material-symbols-outlined">design_services</span>
            <div>
              <p class="kpi-mini-label">在售服务</p>
              <h4 class="kpi-mini-value">{{ detail.serviceCount ?? 0 }}</h4>
            </div>
          </div>
        </div>
      </section>

      <!-- 审核记录 -->
      <section v-if="detail.reviewerId" class="block audit-block">
        <h3 class="block-title"><span class="material-symbols-outlined">verified</span>审核记录</h3>
        <div class="grid two">
          <div class="kv"><span class="k">审核人 ID</span><span class="v mono">#{{ detail.reviewerId }}</span></div>
          <div class="kv"><span class="k">审核时间</span><span class="v mono">{{ formatDate(detail.applyReviewedAt || detail.reviewedAt) }}</span></div>
        </div>
      </section>
    </template>

    <!-- 强制解除弹窗 -->
    <el-dialog v-model="forceVisible" title="强制解除商家" width="92%" top="8vh" class="admin-dialog">
      <div class="dialog-body">
        <p class="warn-text">
          <span class="material-symbols-outlined">warning</span>
          该操作将下架门店、禁用店主账号并解除关联，<strong>不可恢复</strong>。
        </p>
        <div class="field">
          <label class="field-label">解除原因 <span class="req">*</span></label>
          <textarea v-model="forceReason" class="dlg-input" rows="3" placeholder="严重违规 / 投诉 / 法务要求等"></textarea>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <button class="btn-outline" @click="forceVisible = false">取消</button>
          <button class="btn-danger" @click="submitForceRemove">确认解除</button>
        </div>
      </template>
    </el-dialog>

    <!-- 图片预览 -->
    <el-dialog v-model="previewVisible" :show-close="false" width="auto" align-center class="image-preview-dialog">
      <img v-if="previewImageUrl" :src="previewImageUrl" class="preview-img" alt="预览" />
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.merchant-detail-view {
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
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: none;
  color: $color-on-surface-variant;
  cursor: pointer;
  font-size: $font-size-label-md;
  &:hover { color: $color-brand; }
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-top: $spacing-xs;
}
.page-sub {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  font-family: $font-family-mono;
  margin-top: 4px;
}
.head-actions { display: flex; gap: $spacing-sm; flex-wrap: wrap; }
.btn-primary, .btn-outline, .btn-danger {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: none;
  white-space: nowrap;
}
.btn-primary { background: $color-brand; color: #fff; }
.btn-outline {
  background: transparent;
  border: 1px solid $color-outline-variant;
  color: $color-on-surface;
  &.danger { color: $color-emergency; border-color: $color-emergency; }
  &.success { color: $color-success; border-color: $color-success; }
}
.btn-danger { background: $color-emergency; color: #fff; }
.empty-tip {
  padding: $spacing-xl;
  text-align: center;
  color: $color-on-surface-variant;
}

.status-bar {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-low;
  border-radius: $radius-md;
  flex-wrap: wrap;
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
  &.lg {
    font-size: $font-size-label-md;
    padding: 4px $spacing-md;
    border-radius: $radius-full;
    background: $color-surface-container-lowest;
  }
}
.meta-text {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 14px; }
}

.block {
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
}
.block-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0 0 $spacing-md;
  padding-bottom: $spacing-sm;
  border-bottom: 1px dashed $color-outline-variant;
  .material-symbols-outlined { font-size: 18px; color: $color-brand; }
}
.grid { display: grid; gap: $spacing-md; }
.grid.two { grid-template-columns: 1fr 1fr; }
.grid.three { grid-template-columns: repeat(3, 1fr); }
@media (max-width: 720px) {
  .grid.two, .grid.three { grid-template-columns: 1fr; }
}
.kv {
  display: flex;
  flex-direction: column;
  gap: 4px;
  &.full { grid-column: 1 / -1; }
}
.k { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.v {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  word-break: break-all;
  &.strong { font-weight: 700; font-size: $font-size-body-lg; }
  &.muted { color: $color-on-surface-variant; }
}
.mono { font-family: $font-family-mono; }
.pics { display: flex; flex-wrap: wrap; gap: $spacing-xs; }
.muted { color: $color-on-surface-variant; font-size: $font-size-label-sm; }

.pic-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px $spacing-sm;
  border-radius: $radius-full;
  background: $color-surface-container;
  color: $color-brand;
  font-size: $font-size-label-sm;
  font-weight: 600;
  cursor: pointer;
  border: 1px solid transparent;
  transition: all 0.15s;
  &:hover { background: rgba($color-brand, 0.08); border-color: $color-brand; }
  .material-symbols-outlined { font-size: 14px; }
  &.lg { padding: 6px $spacing-md; font-size: $font-size-label-md; }
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

.addr-display {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  align-items: center;
  padding: $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-md;
  border: 1px dashed $color-outline-variant;
}
.addr-chip.lg {
  padding: 4px $spacing-md;
  font-size: $font-size-body-md;
  background: rgba($color-brand, 0.08);
  color: $color-brand;
  font-weight: 600;
  border-radius: $radius-full;
}
.addr-detail.lg {
  font-size: $font-size-body-md;
  color: $color-on-surface;
}
.addr-bad {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  color: $color-emergency;
  font-size: $font-size-label-sm;
  .material-symbols-outlined { font-size: 14px; }
}
.raw-text {
  margin-top: 6px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  word-break: break-all;
  background: $color-surface-container-lowest;
  padding: 4px 8px;
  border-radius: $radius-sm;
  font-family: $font-family-mono;
}
.coord {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 14px; }
}

.hours-table {
  width: 100%;
  border-collapse: collapse;
  font-size: $font-size-body-md;
  th, td {
    padding: $spacing-sm $spacing-md;
    border-bottom: 1px solid $color-outline-variant;
    text-align: left;
  }
  th {
    width: 80px;
    color: $color-on-surface-variant;
    font-weight: 600;
    font-size: $font-size-label-md;
    background: $color-surface-container-low;
  }
  tr.rest td { color: $color-on-surface-variant; font-style: italic; }
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: $spacing-sm;
}
.photo-thumb {
  width: 100%;
  height: 120px;
  object-fit: cover;
  border-radius: $radius-md;
  cursor: zoom-in;
  transition: transform 0.15s;
  &:hover { transform: scale(1.03); }
}

.kpi-mini {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-md;
  .material-symbols-outlined { font-size: 28px; color: $color-brand; }
}
.kpi-mini-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin: 0;
}
.kpi-mini-value {
  font-family: $font-family-headline;
  font-size: 22px;
  font-weight: 700;
  color: $color-on-surface;
  margin: 2px 0 0;
}

.audit-block {
  background: $color-surface-container-low;
  border-style: dashed;
}

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

:deep(.image-preview-dialog) {
  background: transparent;
  box-shadow: none;
  .el-dialog__body { padding: 0; display: flex; justify-content: center; }
}
.preview-img {
  max-width: 80vw;
  max-height: 85vh;
  border-radius: $radius-md;
  box-shadow: $shadow-lg;
}
</style>
