<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

const router = useRouter()

const page = ref(1)
const size = ref(20)
const total = ref(0)
const loading = ref(false)
const filter = ref<'all' | 0 | 1 | 2>('all')
const items = ref<any[]>([])

// 详情抽屉
const drawerVisible = ref(false)
const detail = ref<any>(null)
const previewVisible = ref(false)
const previewImageUrl = ref('')

// ============== 常量 ==============
const STATUS_MAP: Record<number, { label: string; cls: string }> = {
  0: { label: '待审核', cls: 'pending' },
  1: { label: '已通过', cls: 'approved' },
  2: { label: '已驳回', cls: 'rejected' },
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

// ============== 计算 ==============
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

/**
 * 解析地址字符串：拆分「省/市/区」chips + 详细地址
 * 处理直辖市重复（「北京市 北京市 西城区」→ 北京市 / 西城区）
 * 以及脏数据：纯数字串 / 无中文字符视为异常。
 * 单 token 含中文的合法地址（如「北京市朝阳街226号」）整段作 detail。
 */
function parseAddress(addr: string | undefined | null): {
  chips: string[]
  detail: string
  raw: string
  bad: boolean
} {
  const raw = (addr || '').trim()
  if (!raw) return { chips: [], detail: '', raw: '', bad: false }

  // 完全无中文 → 脏数据（手机号 / 乱码 / 英文等）
  const hasChinese = /[\u4e00-\u9fa5]/.test(raw)
  if (!hasChinese) {
    return { chips: [], detail: '', raw, bad: true }
  }

  // 按空白拆分
  const parts = raw.split(/\s+/).filter(Boolean)

  // 单 token（无空格）→ 整段作 detail（合法中文地址）
  if (parts.length === 1) {
    return { chips: [], detail: raw, raw, bad: false }
  }

  // 多 token：去连续重复（北京市 北京市 → 北京市）
  const deduped: string[] = []
  for (const p of parts) {
    if (!deduped.length || deduped[deduped.length - 1] !== p) deduped.push(p)
  }
  // 过滤掉地址里混入的纯数字串（如手机号）
  const clean = deduped.filter(
    (s) => !(/^\d{5,}$/.test(s) && !/[\u4e00-\u9fa5]/.test(s))
  )
  if (!clean.length) {
    return { chips: [], detail: '', raw, bad: true }
  }
  // 末段作详细地址，余下作省市区 chips
  const detail = clean.length > 1 ? clean[clean.length - 1] : ''
  const chips = clean.slice(0, -1)
  return { chips, detail, raw, bad: false }
}

/** 安全解析营业时间 JSON */
function parseBusinessHours(json: any): Record<string, string> {
  if (!json) return {}
  try {
    if (typeof json === 'string') return JSON.parse(json)
    if (typeof json === 'object') return json as Record<string, string>
  } catch {}
  return {}
}

/** 图片完整 URL：相对路径补 /api/v1，绝对路径直接用 */
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

// ============== 数据加载 ==============
async function load() {
  loading.value = true
  try {
    const statusArg = filter.value === 'all' ? undefined : Number(filter.value)
    const res: any = await adminApi.applyList(page.value, size.value, statusArg)
    items.value = (res.list || []) as any[]
    total.value = res.total ?? items.value.length
  } catch (e: any) {
    ElMessage.error(e?.message || '加载申请列表失败')
    items.value = []
  } finally {
    loading.value = false
  }
}

function setFilter(f: typeof filter.value) {
  filter.value = f
  page.value = 1
  load()
}

function goPage(p: number) {
  if (p < 1 || p > totalPages.value) return
  page.value = p
  load()
}

// ============== 抽屉 ==============
function openDetail(m: any) {
  detail.value = m
  drawerVisible.value = true
}
function closeDrawer() {
  drawerVisible.value = false
  // 延迟清空数据，让 drawer 关闭动画完成
  setTimeout(() => {
    detail.value = null
    previewImageUrl.value = ''
    previewVisible.value = false
  }, 250)
}

function previewImage(url: string) {
  if (!url) return
  previewImageUrl.value = url
  previewVisible.value = true
}

// ============== 审核操作 ==============
async function audit(m: any, approved: boolean) {
  try {
    const promptCfg: any = {
      inputType: 'textarea',
      inputPlaceholder: approved ? '选填备注' : '请填写驳回原因',
      confirmButtonText: approved ? '通过' : '驳回',
      cancelButtonText: '取消',
    }
    const { value: reason } = await ElMessageBox.prompt(
      approved ? '通过入驻申请（可填备注）' : '驳回入驻申请（必填原因）',
      approved ? '通过审核' : '驳回审核',
      promptCfg,
    )
    const res: any = await adminApi.auditApply(m.id, approved, reason || undefined)
    m.status = approved ? 1 : 2
    m.reviewedAt = new Date().toISOString().slice(0, 19).replace('T', ' ')
    m.reviewNote = reason || ''
    ElMessage.success(approved ? '已通过,商家账号已自动开通' : '已驳回')
    // 同步更新详情（若抽屉开着）
    if (detail.value && detail.value.id === m.id) {
      detail.value = { ...m }
    }
    // 通过后询问是否跳到新商家详情（依赖后端在 auditApply 响应中返回 merchantId）
    if (approved) {
      const merchantId = res?.merchantId ?? res?.data?.merchantId
      if (merchantId) {
        try {
          await ElMessageBox.confirm(
            '商家账号已开通，是否立即查看该商家详情？',
            '审核通过',
            { confirmButtonText: '去看看', cancelButtonText: '留在本页', type: 'success' },
          )
          router.push('/admin/merchant/detail/' + merchantId)
        } catch {
          // 用户留在列表
        }
      }
    }
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

// ============== 模板辅助 ==============
function statusLabel(s: number) {
  return STATUS_MAP[s]?.label || '未知'
}
function typeLabel(t: string) {
  return TYPE_LABEL[t] || t || '-'
}
function subjectLabel(s: string) {
  return SUBJECT_LABEL[s] || s || '-'
}
function formatDate(d?: string | null) {
  if (!d) return '-'
  return d.slice(0, 19).replace('T', ' ')
}

onMounted(load)
</script>

<template>
  <div class="audit-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">入驻申请审核</h1>
        <p class="page-sub">
          点击「查看详情」即可看到完整申请表，再决定通过或驳回
        </p>
      </div>
    </div>

    <div class="filter-row">
      <button class="chip" :class="{ active: filter === 'all' }" @click="setFilter('all')">全部</button>
      <button class="chip" :class="{ active: filter === 0 }" @click="setFilter(0)">待审核</button>
      <button class="chip" :class="{ active: filter === 1 }" @click="setFilter(1)">已通过</button>
      <button class="chip" :class="{ active: filter === 2 }" @click="setFilter(2)">已驳回</button>
    </div>

    <section class="table-card">
      <div v-if="loading" class="empty-tip">加载中...</div>
      <div v-else-if="!items.length" class="empty-tip">暂无申请数据</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>申请人</th>
            <th>门店</th>
            <th>类型</th>
            <th>提交时间</th>
            <th>状态</th>
            <th class="action-col">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in items" :key="m.id" @click="openDetail(m)" class="row-click">
            <td class="mono">#{{ m.id }}</td>
            <td>
              <div class="owner-cell">
                <span class="owner-name">{{ m.ownerName }}</span>
                <span class="owner-phone">{{ m.phone }}</span>
              </div>
            </td>
            <td class="name">{{ m.storeName }}</td>
            <td>
              <span class="type-tag" :class="`type-${m.type}`">{{ typeLabel(m.type) }}</span>
            </td>
            <td class="mono">{{ formatDate(m.submitAt) }}</td>
            <td>
              <span class="status-pill" :class="STATUS_MAP[m.status]?.cls">
                <span class="dot"></span>{{ statusLabel(m.status) }}
              </span>
            </td>
            <td class="action-col" @click.stop>
              <button class="act view" @click="openDetail(m)">
                <span class="material-symbols-outlined">visibility</span>查看详情
              </button>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination" v-if="items.length">
        <span class="page-info">共 {{ total }} 条</span>
        <div class="page-buttons">
          <button class="page-btn" @click="goPage(page - 1)">
            <span class="material-symbols-outlined">chevron_left</span>
          </button>
          <button class="page-btn active">{{ page }}</button>
          <button class="page-btn" @click="goPage(page + 1)">
            <span class="material-symbols-outlined">chevron_right</span>
          </button>
        </div>
      </div>
    </section>

    <!-- ========== 详情抽屉 ========== -->
    <el-drawer
      v-model="drawerVisible"
      direction="rtl"
      size="720px"
      :with-header="false"
      :modal-class="'apply-detail-modal'"
      @close="closeDrawer"
    >
      <div v-if="detail" class="drawer-wrap">
        <!-- 抽屉头部 -->
        <header class="drawer-head">
          <div class="head-left">
            <span class="head-badge">
              <span class="material-symbols-outlined">inventory_2</span>
            </span>
            <div>
              <h2 class="head-title">入驻申请详情</h2>
              <p class="head-sub">#{{ detail.id }} · {{ formatDate(detail.submitAt) }} 提交</p>
            </div>
          </div>
          <button class="head-close" @click="closeDrawer" aria-label="关闭">
            <span class="material-symbols-outlined">close</span>
          </button>
        </header>

        <!-- 状态条 -->
        <div class="status-bar">
          <span class="status-pill lg" :class="STATUS_MAP[detail.status]?.cls">
            <span class="dot"></span>{{ statusLabel(detail.status) }}
          </span>
          <span v-if="detail.reviewNote" class="review-note">
            <span class="material-symbols-outlined">sticky_note_2</span>
            审核备注：{{ detail.reviewNote }}
          </span>
        </div>

        <div class="drawer-body">
          <!-- 店主信息 -->
          <section class="block">
            <h3 class="block-title"><span class="material-symbols-outlined">person</span>店主信息</h3>
            <div class="grid two">
              <div class="kv">
                <span class="k">姓名</span>
                <span class="v">{{ detail.ownerName || '-' }}</span>
              </div>
              <div class="kv">
                <span class="k">手机号</span>
                <span class="v mono">{{ detail.phone || '-' }}</span>
              </div>
              <div class="kv">
                <span class="k">身份证号</span>
                <span class="v mono">{{ detail.idCard || '未填写' }}</span>
              </div>
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
              <div class="kv">
                <span class="k">门店名称</span>
                <span class="v strong">{{ detail.storeName || '-' }}</span>
              </div>
              <div class="kv">
                <span class="k">服务类型</span>
                <span class="v">
                  <span class="type-tag" :class="`type-${detail.type}`">{{ typeLabel(detail.type) }}</span>
                </span>
              </div>
              <div class="kv">
                <span class="k">主体类型</span>
                <span class="v">{{ subjectLabel(detail.subjectType) }}</span>
              </div>
              <div class="kv">
                <span class="k">企业名称</span>
                <span class="v">{{ detail.enterpriseName || '-' }}</span>
              </div>
              <div class="kv">
                <span class="k">统一信用代码</span>
                <span class="v mono">{{ detail.unifiedCreditCode || detail.licenseNo || '-' }}</span>
              </div>
              <div class="kv">
                <span class="k">门店电话</span>
                <span class="v mono">{{ detail.storePhone || '-' }}</span>
              </div>
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
            <template v-else-if="parseAddress(detail.address).raw">
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

          <!-- 审核信息（已处理时） -->
          <section v-if="detail.status !== 0" class="block audit-block">
            <h3 class="block-title"><span class="material-symbols-outlined">verified</span>审核记录</h3>
            <div class="grid two">
              <div class="kv">
                <span class="k">审核人 ID</span>
                <span class="v mono">#{{ detail.reviewerId || '-' }}</span>
              </div>
              <div class="kv">
                <span class="k">审核时间</span>
                <span class="v mono">{{ formatDate(detail.reviewedAt) }}</span>
              </div>
            </div>
          </section>
        </div>

        <!-- 抽屉底部审核按钮（仅待审核状态可操作） -->
        <footer v-if="detail.status === 0" class="drawer-footer">
          <button class="btn ghost" @click="closeDrawer">关闭</button>
          <button class="btn reject" @click="audit(detail, false)">
            <span class="material-symbols-outlined">close</span>驳回
          </button>
          <button class="btn approve" @click="audit(detail, true)">
            <span class="material-symbols-outlined">check</span>通过申请
          </button>
        </footer>
        <footer v-else class="drawer-footer done">
          <span class="done-tip">该申请已处理</span>
          <button class="btn ghost" @click="closeDrawer">关闭</button>
        </footer>
      </div>
    </el-drawer>

    <!-- ========== 图片预览 ========== -->
    <el-dialog
      v-model="previewVisible"
      :show-close="false"
      width="auto"
      align-center
      class="image-preview-dialog"
    >
      <img v-if="previewImageUrl" :src="previewImageUrl" class="preview-img" alt="预览" />
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

// ================= 列表页 =================
.audit-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}
.page-sub {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  margin-top: 4px;
}

.filter-row { display: flex; gap: $spacing-sm; }
.chip {
  padding: $spacing-xs $spacing-lg;
  border-radius: $radius-full;
  border: 1px solid $color-outline-variant;
  background: $color-surface-container-lowest;
  font-size: $font-size-label-md;
  cursor: pointer;
  color: $color-on-surface-variant;
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
}

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
.action-col { width: 140px; }

.row-click { cursor: pointer; transition: background 0.15s; }
.row-click:hover { background: rgba($color-brand, 0.04); }

.owner-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.owner-name { font-weight: 600; color: $color-on-surface; }
.owner-phone {
  font-family: $font-family-mono;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.name { font-weight: 700; color: $color-on-surface; }
.mono { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; }

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

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-sm;
  font-weight: 700;
  .dot { width: 8px; height: 8px; border-radius: $radius-full; }
  &.pending { color: $color-warning; .dot { background: $color-warning; } }
  &.approved { color: $color-success; .dot { background: $color-success; } }
  &.rejected { color: $color-emergency; .dot { background: $color-emergency; } }
  &.lg {
    font-size: $font-size-label-md;
    padding: 4px $spacing-md;
    border-radius: $radius-full;
    background: $color-surface-container-low;
  }
}

.act {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px $spacing-md;
  border-radius: $radius-md;
  border: none;
  font-size: $font-size-label-sm;
  font-weight: 700;
  cursor: pointer;
  color: #fff;
  &.view {
    background: $color-brand;
    &:hover { filter: brightness(1.05); }
  }
  .material-symbols-outlined { font-size: 14px; }
}

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
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
  .material-symbols-outlined { font-size: 16px; }
}

// ================= 抽屉 =================
.drawer-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: $color-surface-container-lowest;
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg $spacing-xl;
  background: linear-gradient(135deg, rgba($color-brand, 0.08) 0%, rgba($color-brand, 0.02) 100%);
  border-bottom: 1px solid $color-outline-variant;
}
.head-left { display: flex; align-items: center; gap: $spacing-md; }
.head-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: linear-gradient(135deg, $color-brand 0%, lighten($color-brand, 8%) 100%);
  color: #fff;
  box-shadow: 0 6px 18px -6px rgba($color-brand, 0.4);
  .material-symbols-outlined { font-size: 24px; }
}
.head-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  line-height: 1.2;
}
.head-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 2px;
}
.head-close {
  width: 36px;
  height: 36px;
  border: none;
  background: transparent;
  border-radius: $radius-default;
  cursor: pointer;
  color: $color-on-surface-variant;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
  &:hover { background: $color-surface-container; color: $color-on-surface; }
}

.status-bar {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-xl;
  background: $color-surface-container-low;
  border-bottom: 1px solid $color-outline-variant;
}
.review-note {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 14px; }
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-lg $spacing-xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
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
  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
  }
}
.grid { display: grid; gap: $spacing-md; }
.grid.two { grid-template-columns: 1fr 1fr; }
@media (max-width: 720px) { .grid.two { grid-template-columns: 1fr; } }

.kv {
  display: flex;
  flex-direction: column;
  gap: 4px;
  &.full { grid-column: 1 / -1; }
}
.k {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.v {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  word-break: break-all;
  &.strong { font-weight: 700; font-size: $font-size-body-lg; }
  &.muted { color: $color-on-surface-variant; }
}
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
  &:hover {
    background: rgba($color-brand, 0.08);
    border-color: $color-brand;
  }
  .material-symbols-outlined { font-size: 14px; }
  &.lg { padding: 6px $spacing-md; font-size: $font-size-label-md; }
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
}
.addr-detail.lg {
  font-size: $font-size-body-md;
  color: $color-on-surface;
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
  tr.rest {
    td { color: $color-on-surface-variant; font-style: italic; }
  }
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

.audit-block {
  background: $color-surface-container-low;
  border-style: dashed;
}

.drawer-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: $spacing-md;
  padding: $spacing-md $spacing-xl;
  border-top: 1px solid $color-outline-variant;
  background: $color-surface-container-low;
  &.done { justify-content: space-between; }
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  height: 44px;
  padding: 0 $spacing-lg;
  border-radius: $radius-md;
  border: none;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  transition: all 0.15s;
  .material-symbols-outlined { font-size: 18px; }
  &.ghost {
    background: transparent;
    color: $color-on-surface-variant;
    border: 1px solid $color-outline-variant;
    &:hover { background: $color-surface-container; color: $color-on-surface; }
  }
  &.reject {
    background: $color-surface-container-lowest;
    color: $color-emergency;
    border: 1px solid $color-emergency;
    &:hover { background: rgba($color-emergency, 0.08); }
  }
  &.approve {
    background: linear-gradient(135deg, $color-success 0%, darken($color-success, 6%) 100%);
    color: #fff;
    box-shadow: 0 4px 12px -4px rgba($color-success, 0.45);
    &:hover { filter: brightness(1.05); transform: translateY(-1px); }
  }
}
.done-tip {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

// ================= 图片预览 =================
:deep(.image-preview-dialog) {
  background: transparent;
  box-shadow: none;
  .el-dialog__body {
    padding: 0;
    display: flex;
    justify-content: center;
  }
}
.preview-img {
  max-width: 80vw;
  max-height: 85vh;
  border-radius: $radius-md;
  box-shadow: $shadow-lg;
}
</style>
