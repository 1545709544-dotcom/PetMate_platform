<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'
import type { MerchantAppointment } from '@/api/modules/appointment'

const list = ref<MerchantAppointment[]>([])
const loading = ref(false)
const activeStatus = ref('')

const STATUS_TABS = [
  { value: '', label: '全部' },
  { value: 'pending', label: '已预约' },
  { value: 'confirmed', label: '进行中' },
  { value: 'done', label: '已完成' },
  { value: 'rejected', label: '已拒绝' },
  { value: 'cancelled', label: '已取消' },
]

const statusMeta: Record<string, { label: string; cls: string }> = {
  pending: { label: '已预约', cls: 'pending' },
  confirmed: { label: '进行中', cls: 'confirmed' },
  done: { label: '已完成', cls: 'done' },
  rejected: { label: '已拒绝', cls: 'rejected' },
  cancelled: { label: '已取消', cls: 'cancelled' },
}

const filtered = computed(() =>
  list.value.filter((a) => !activeStatus.value || a.status === activeStatus.value),
)

async function load() {
  loading.value = true
  try {
    list.value = (await merchantApi.appointments())?.list ?? []
  } catch (e: any) {
    ElMessage.error(e?.message || '预约加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

async function setStatus(
  a: MerchantAppointment,
  status: 'confirmed' | 'done' | 'cancelled' | 'rejected',
  okMsg: string,
  reason?: string,
) {
  try {
    await merchantApi.updateAppointmentStatus(a.id, status, reason)
    ElMessage.success(okMsg)
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// 拒绝预约（收集原因，后端会给用户发站内信）
async function reject(a: MerchantAppointment) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写拒绝原因，用户将收到通知', '拒绝预约', {
      type: 'warning',
      confirmButtonText: '确认拒绝',
      cancelButtonText: '再想想',
      inputType: 'textarea',
      inputValidator: (v: string) => (v && v.trim() ? true : '请填写拒绝原因'),
    })
    reason = value.trim()
  } catch {
    return
  }
  await setStatus(a, 'rejected', '已拒绝该预约', reason)
}

// 取消预约（收集原因，后端会给用户发站内信）
async function cancel(a: MerchantAppointment) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写取消原因，用户将收到通知', '取消预约', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想',
      inputType: 'textarea',
      inputValidator: (v: string) => (v && v.trim() ? true : '请填写取消原因'),
    })
    reason = value.trim()
  } catch {
    return
  }
  await setStatus(a, 'cancelled', '已取消预约', reason)
}

// 改期
const rescheduleVisible = ref(false)
const rescheduleTarget = ref<MerchantAppointment | null>(null)
const rescheduleTime = ref('')
function openReschedule(a: MerchantAppointment) {
  rescheduleTarget.value = a
  rescheduleTime.value = ''
  rescheduleVisible.value = true
}
async function submitReschedule() {
  if (!rescheduleTarget.value) return
  if (!rescheduleTime.value) {
    ElMessage.warning('请选择新的预约时间')
    return
  }
  try {
    await merchantApi.rescheduleAppointment(rescheduleTarget.value.id, rescheduleTime.value)
    ElMessage.success('改期成功，已通知用户')
    rescheduleVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '改期失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="appt-mgmt">
    <div class="page-head">
      <div>
        <h1 class="page-title">预约管理</h1>
        <p class="page-sub">处理用户在本店的服务预约，确认后进入「进行中」，服务完成置「已完成」。</p>
      </div>
    </div>

    <!-- 状态筛选 -->
    <div class="tabs">
      <button
        v-for="s in STATUS_TABS"
        :key="s.value"
        class="tab"
        :class="{ active: activeStatus === s.value }"
        @click="activeStatus = s.value"
      >
        {{ s.label }}
      </button>
    </div>

    <div v-if="loading" class="state">加载中…</div>
    <div v-else-if="!filtered.length" class="state">暂无预约记录</div>

    <div v-else class="table-card">
      <table class="data-table">
        <thead>
          <tr>
            <th>服务 / 分类</th>
            <th>预约人</th>
            <th>预约时间</th>
            <th>备注</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in filtered" :key="a.id">
            <td>
              <div class="svc-name">{{ a.serviceName }}</div>
              <span class="svc-cat">{{ a.category }}</span>
            </td>
            <td>
              <div>{{ a.userName || '—' }}</div>
              <div class="sub">{{ a.userPhone || '' }}</div>
            </td>
            <td>{{ a.appointmentTime }}</td>
            <td class="note-cell">{{ a.note || '—' }}</td>
            <td>
              <span class="status-pill" :class="statusMeta[a.status]?.cls">
                {{ statusMeta[a.status]?.label || a.status }}
              </span>
            </td>
            <td>
              <div class="actions">
                <template v-if="a.status === 'pending'">
                  <a class="act confirm" @click="setStatus(a, 'confirmed', '已接单，进入进行中')">接单</a>
                  <a class="act danger" @click="reject(a)">拒绝</a>
                  <a class="act reschedule" @click="openReschedule(a)">改期</a>
                </template>
                <template v-else-if="a.status === 'confirmed'">
                  <a class="act done" @click="setStatus(a, 'done', '已标记完成')">完成</a>
                  <a class="act reschedule" @click="openReschedule(a)">改期</a>
                  <a class="act danger" @click="cancel(a)">取消</a>
                </template>
                <span v-else class="no-action">—</span>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.appt-mgmt {
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
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}
.tab {
  padding: $spacing-xs $spacing-lg;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  background: $color-surface-container-lowest;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;
  &:hover { border-color: $color-brand; color: $color-brand; }
  &.active { background: $color-brand; border-color: $color-brand; color: $color-on-brand; }
}
.state {
  padding: $spacing-2xl;
  text-align: center;
  color: $color-on-surface-variant;
}
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
    vertical-align: top;
  }
}
.svc-name { font-weight: 700; color: $color-on-surface; }
.svc-cat {
  display: inline-block;
  margin-top: 2px;
  padding: 1px $spacing-sm;
  background: $color-brand-container;
  color: $color-on-brand-container;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
}
.sub { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.note-cell { max-width: 220px; color: $color-on-surface-variant; }
.status-pill {
  padding: 4px $spacing-sm;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;
  &.pending { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.confirmed { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.cancelled { background: $color-surface-container; color: $color-on-surface-variant; }
  &.done { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
}
.actions { display: flex; gap: $spacing-md; }
.act {
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  &.confirm { color: $color-success; }
  &.done { color: $color-secondary; }
  &.danger { color: $color-emergency; }
  &:hover { text-decoration: underline; }
}
.no-action { color: $color-outline; }
</style>
