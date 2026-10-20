<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type AdminUser } from '@/api/modules/admin'

const router = useRouter()

// 视图模型：UI 字段由 AdminUser 派生（status 字段在视图里覆盖为字符串语义）
interface User extends Omit<AdminUser, 'status'> {
  name: string
  userId: string
  vip: string
  vipColor: string
  /** 实际生效等级（考虑过期降级） */
  effectiveVipLevel: number
  /** 是否已过期（vipLevel > 0 但 vipExpireAt <= now） */
  vipExpired: boolean
  /** 到期日 YYYY-MM-DD，永久/L0 时为 '' */
  vipExpireDate: string
  /** 剩余天数（负数表示已过期 N 天）；永久/L0 时为 null */
  vipDaysLeft: number | null
  status: 'normal' | 'banned'
  regDate: string
}

const users = ref<User[]>([])
const loading = ref(false)
const selected = ref<number[]>([])

// VIP 等级映射（vipLevel: 0=普通, 1=白银, 2=黄金）
// 与用户端开通页（VipOpenView）保持一致，系统仅此三档，无铂金档
// 实际生效等级仍受 vipExpireAt 约束：过期 → 视为普通用户
const VIP_MAP: Record<number, { vip: string; vipColor: string }> = {
  0: { vip: '普通', vipColor: 'gray' },
  1: { vip: '白银', vipColor: 'silver' },
  2: { vip: '黄金', vipColor: 'gold' },
}

/** 计算到期日（YYYY-MM-DD，UTC 切片避免时区漂移） */
function calcExpireDate(iso?: string | null): string {
  if (!iso) return ''
  // 后端如果返回 "2027-01-15T23:59:59" 或 "2027-01-15 23:59:59" 都兼容
  const s = String(iso).slice(0, 10)
  return /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : ''
}

/** 计算到期日对应的「今日已过 23:59:59」毫秒数；空值返回 null */
function expireEndOfDayTs(iso?: string | null): number | null {
  const date = calcExpireDate(iso)
  if (!date) return null
  return new Date(date + 'T23:59:59').getTime()
}

/** 剩余天数：正数=剩 N 天，负数=已过期 N 天，null=永久或 L0 */
function calcDaysLeft(iso?: string | null): number | null {
  const ts = expireEndOfDayTs(iso)
  if (ts == null) return null
  const now = Date.now()
  // 用「天」粒度，向上取整避免当天差 1 天
  const MS = 24 * 60 * 60 * 1000
  return Math.ceil((ts - now) / MS)
}

const toViewUser = (u: AdminUser): User => {
  const rawLevel = u.vipLevel || 0
  const daysLeft = calcDaysLeft(u.vipExpireAt)
  // L0 用户无到期日或已过期 → 视为普通
  const expired = rawLevel > 0 && daysLeft != null && daysLeft < 0
  const effectiveLevel = expired ? 0 : rawLevel
  // 兼容历史遗留等级（如库里曾出现的 5）：>=2 一律按黄金展示，避免显示错乱
  const vipInfo =
    VIP_MAP[effectiveLevel] ||
    (effectiveLevel >= 2 ? { vip: '黄金', vipColor: 'gold' } : { vip: `L${effectiveLevel}`, vipColor: 'gray' })
  return {
    ...u,
    name: u.nickname,
    userId: `PM_${u.id}`,
    vip: vipInfo.vip,
    vipColor: vipInfo.vipColor,
    effectiveVipLevel: effectiveLevel,
    vipExpired: expired,
    vipExpireDate: calcExpireDate(u.vipExpireAt),
    vipDaysLeft: daysLeft,
    status: u.status === 1 ? 'banned' : 'normal',
    regDate: (u.registerDate || '').slice(0, 10),
  }
}

// 分页 + 筛选状态
const pagination = reactive({ page: 1, size: 20, total: 0 })
const filters = reactive({
  status: -1, // -1 表示全部
  vip: -1,
  keyword: '',
  dateFrom: '',
  dateTo: '',
})

const allStatus = computed(() => (filters.status < 0 ? undefined : Number(filters.status)))
const allVip = computed(() => (filters.vip < 0 ? undefined : Number(filters.vip)))

const loadUsers = async () => {
  loading.value = true
  try {
    const res = await adminApi.userList({
      page: pagination.page,
      size: pagination.size,
      status: allStatus.value,
      vip: allVip.value,
      keyword: filters.keyword.trim() || undefined,
      dateFrom: filters.dateFrom || undefined,
      dateTo: filters.dateTo || undefined,
    })
    users.value = (res.list || []).map(toViewUser)
    pagination.total = res.total ?? 0
    selected.value = []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载用户列表失败')
    users.value = []
  } finally {
    loading.value = false
  }
}

const applyFilter = () => {
  pagination.page = 1
  loadUsers()
}

const resetFilter = () => {
  filters.status = -1
  filters.vip = -1
  filters.keyword = ''
  filters.dateFrom = ''
  filters.dateTo = ''
  pagination.page = 1
  loadUsers()
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

const goPage = (p: number) => {
  if (p < 1 || p > totalPages.value) return
  pagination.page = p
  loadUsers()
}

onMounted(loadUsers)

// KPI（后端 dashboard 现已返回 todayNewUsers / vipCount / abnormalCount）
const kpis = ref([
  { label: '累计用户', value: '—', icon: 'group', color: 'blue' },
  { label: '新增用户（今日）', value: '—', icon: 'person_add', color: 'orange' },
  { label: 'VIP 会员数', value: '—', icon: 'stars', color: 'gold' },
  { label: '异常账号', value: '—', icon: 'block', color: 'red' },
])

const loadOverview = async () => {
  try {
    const ov = await adminApi.overview()
    kpis.value[0].value = (ov.totalUsers ?? 0).toLocaleString()
    kpis.value[1].value = (ov.todayNewUsers ?? 0).toLocaleString()
    kpis.value[2].value = (ov.vipCount ?? 0).toLocaleString()
    kpis.value[3].value = (ov.abnormalCount ?? 0).toLocaleString()
  } catch {
    // 静默失败，KPI 保留占位
  }
}

onMounted(loadOverview)

function toggleOne(id: number) {
  const i = selected.value.indexOf(id)
  if (i >= 0) selected.value.splice(i, 1)
  else selected.value.push(id)
}

const allSelected = computed(() =>
  users.value.length > 0 && selected.value.length === users.value.length,
)

function toggleAll() {
  if (allSelected.value) selected.value = []
  else selected.value = users.value.map(u => u.id)
}

async function doBan(u: User) {
  const willBan = u.status === 'normal'
  try {
    await ElMessageBox.confirm(
      `确定要${willBan ? '封禁' : '解封'}用户「${u.name}」吗？`,
      '操作确认',
      { type: willBan ? 'warning' : 'success' },
    )
    await adminApi.banUser(u.id, willBan)
    u.status = willBan ? 'banned' : 'normal'
    ElMessage.success(`${willBan ? '封禁' : '解封'}成功`)
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

// 批量封禁
async function batchBan() {
  if (!selected.value.length) {
    ElMessage.warning('请先勾选要封禁的用户')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要封禁已选中的 ${selected.value.length} 位用户吗？`,
      '批量封禁',
      { type: 'warning' },
    )
    let ok = 0
    for (const id of selected.value) {
      try {
        await adminApi.banUser(id, true)
        const u = users.value.find(x => x.id === id)
        if (u) u.status = 'banned'
        ok++
      } catch {
        // 单条失败不影响其余
      }
    }
    ElMessage.success(`已封禁 ${ok} 位用户`)
    selected.value = []
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
  }
}

// 发送通知弹窗
const notifyVisible = ref(false)
const notifyForm = reactive({ title: '', content: '', target: 'all' })
function openNotify() {
  notifyForm.title = ''
  notifyForm.content = ''
  notifyForm.target = 'all'
  notifyVisible.value = true
}
async function submitNotify() {
  if (!notifyForm.content.trim()) {
    ElMessage.warning('请输入通知内容')
    return
  }
  try {
    await adminApi.notifyUser({
      title: notifyForm.title.trim() || '系统通知',
      content: notifyForm.content.trim(),
      target: notifyForm.target,
    })
    ElMessage.success('通知已发送')
    notifyVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败')
  }
}

function viewDetail(u: User) {
  router.push('/admin/user/detail/' + u.id)
}

// 编辑用户弹窗
const editVisible = ref(false)
const editForm = reactive({ id: 0, nickname: '', phone: '', vipLevel: 0, status: 0 })
function openEdit(u: User) {
  editForm.id = u.id
  editForm.nickname = u.nickname
  editForm.phone = u.phone
  editForm.vipLevel = u.vipLevel
  editForm.status = u.status === 'banned' ? 1 : 0
  editVisible.value = true
}
async function submitEdit() {
  try {
    await adminApi.editUser(editForm.id, {
      nickname: editForm.nickname.trim() || undefined,
      phone: editForm.phone.trim() || undefined,
      vipLevel: editForm.vipLevel,
      status: editForm.status,
    })
    ElMessage.success('用户信息已更新')
    editVisible.value = false
    loadUsers()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  }
}

// 新增虚拟用户弹窗
const createVisible = ref(false)
const createForm = reactive({ nickname: '', phone: '', password: '123456', vipLevel: 0 })
function openCreate() {
  createForm.nickname = ''
  createForm.phone = ''
  createForm.password = '123456'
  createForm.vipLevel = 0
  createVisible.value = true
}
async function submitCreate() {
  if (!createForm.nickname.trim()) {
    ElMessage.warning('请输入昵称')
    return
  }
  if (!/^1[3-9]\d{9}$/.test(createForm.phone.trim())) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  try {
    await adminApi.createUser({
      nickname: createForm.nickname.trim(),
      phone: createForm.phone.trim(),
      password: createForm.password || '123456',
      vipLevel: createForm.vipLevel,
    })
    ElMessage.success('虚拟用户已创建')
    createVisible.value = false
    loadUsers()
    loadOverview()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  }
}

// 导出当前页 CSV
function exportCsv() {
  if (!users.value.length) {
    ElMessage.warning('当前没有可导出的数据')
    return
  }
  const header = ['用户ID', '昵称', '手机号', 'VIP等级', 'VIP到期日', 'VIP状态', '注册时间', '账号状态']
  const rows = users.value.map(u => {
    let vipStatus = '有效'
    if (u.effectiveVipLevel === 0) vipStatus = u.vipExpired ? '已过期' : '普通'
    const expireText = u.vipExpireDate || '永久'
    return [
      u.userId, u.name, u.phone, u.vip, expireText, vipStatus, u.regDate,
      u.status === 'normal' ? '正常' : '封禁中',
    ]
  })
  const csv = [header, ...rows]
    .map(r => r.map(c => `"${String(c).replace(/"/g, '""')}"`).join(','))
    .join('\r\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `用户列表_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success('已导出当前页数据')
}
</script>

<template>
  <div class="user-mgmt-view">
    <!-- 标题 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-sub">实时查看并管理您的 PetMate 客户群体</p>
      </div>
      <div class="head-actions">
        <button class="btn-outline" @click="exportCsv">
          <span class="material-symbols-outlined">download</span>
          导出数据
        </button>
        <button class="btn-primary" @click="openCreate">
          <span class="material-symbols-outlined">person_add</span>
          新增虚拟用户
        </button>
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
          <span class="filter-label">用户状态:</span>
          <select class="select-btn" v-model.number="filters.status" @change="applyFilter">
            <option :value="-1">全部状态</option>
            <option :value="0">正常</option>
            <option :value="1">封禁中</option>
          </select>
        </div>
        <div class="filter-group">
          <span class="filter-label">会员等级:</span>
          <select class="select-btn" v-model.number="filters.vip" @change="applyFilter">
            <option :value="-1">全部等级</option>
            <option :value="0">普通用户</option>
            <option :value="1">白银</option>
            <option :value="2">黄金</option>
          </select>
        </div>
        <div class="filter-group">
          <span class="filter-label">关键字:</span>
          <input class="select-btn" v-model="filters.keyword" placeholder="昵称 / 手机号" @keyup.enter="applyFilter" />
        </div>
        <div class="filter-group">
          <span class="filter-label">注册:</span>
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

    <!-- 批量操作 -->
    <div class="batch-bar">
      <label class="batch-check">
        <input
          type="checkbox"
          :checked="allSelected"
          @change="toggleAll"
        />
        已选 <span class="count">{{ selected.length }}</span> 位用户
      </label>
        <div class="batch-actions">
          <button class="batch-btn" @click="batchBan">
            <span class="material-symbols-outlined">block</span>
            批量封禁
          </button>
          <span class="batch-sep">|</span>
          <button class="batch-btn" @click="openNotify">
            <span class="material-symbols-outlined">mail</span>
            发送通知
          </button>
        </div>
    </div>

    <!-- 表格 -->
    <div class="table-card">
      <div v-if="loading" class="empty-tip">加载中...</div>
      <div v-else-if="!users.length" class="empty-tip">暂无用户数据</div>
      <table v-else class="data-table">
        <thead>
          <tr>
            <th class="checkbox-col">
              <input
                type="checkbox"
                :checked="allSelected"
                @change="toggleAll"
              />
            </th>
            <th>用户信息</th>
            <th>手机号</th>
            <th>VIP 等级</th>
            <th>注册时间</th>
            <th>状态</th>
            <th>操作栏</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id">
            <td class="checkbox-col">
              <input
                type="checkbox"
                :checked="selected.includes(u.id)"
                @change="toggleOne(u.id)"
              />
            </td>
            <td>
              <div class="user-cell">
                <img :src="u.avatar" :alt="u.name" class="user-avatar" />
                <div>
                  <div class="user-name">{{ u.name }}</div>
                  <div class="user-id">ID: {{ u.userId }}</div>
                </div>
              </div>
            </td>
            <td class="phone-cell">{{ u.phone }}</td>
            <td>
              <div class="vip-cell" v-if="u.effectiveVipLevel === 0">
                <span class="vip-text-muted">普通用户</span>
                <span v-if="u.vipExpired" class="vip-sub">已于 {{ u.vipExpireDate }} 到期</span>
              </div>
              <div class="vip-cell" v-else>
                <span class="vip-badge" :class="u.vipColor">
                  <span class="material-symbols-outlined">stars</span>
                  {{ u.vip }}
                  <span v-if="u.vipExpired" class="vip-tag-expired">已过期</span>
                </span>
                <span class="vip-sub" v-if="u.vipDaysLeft != null">
                  <template v-if="u.vipExpired">已于 {{ u.vipExpireDate }} 到期</template>
                  <template v-else-if="u.vipDaysLeft <= 30">剩 {{ u.vipDaysLeft }} 天到期</template>
                  <template v-else>到期 {{ u.vipExpireDate }}</template>
                </span>
              </div>
            </td>
            <td class="date-cell">{{ u.regDate }}</td>
            <td>
              <span class="status-pill" :class="u.status">
                <span class="dot"></span>
                {{ u.status === 'normal' ? '正常' : '封禁中' }}
              </span>
            </td>
            <td>
              <div class="action-row">
                <button class="icon-btn" @click="viewDetail(u)" title="查看">
                  <span class="material-symbols-outlined">visibility</span>
                </button>
                <button class="icon-btn" @click="openEdit(u)" title="编辑">
                  <span class="material-symbols-outlined">edit</span>
                </button>
                <button
                  class="icon-btn"
                  :class="{ ban: u.status === 'normal', unban: u.status === 'banned' }"
                  @click="doBan(u)"
                  :title="u.status === 'normal' ? '封禁' : '解封'"
                >
                  <span class="material-symbols-outlined">{{ u.status === 'normal' ? 'lock' : 'lock_open' }}</span>
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <span class="page-info">
          显示 {{ (pagination.page - 1) * pagination.size + 1 }} 到
          {{ Math.min(pagination.page * pagination.size, pagination.total) }}
          共 {{ pagination.total }} 条记录
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

    <!-- 编辑用户 -->
    <el-dialog v-model="editVisible" title="编辑用户" width="92%" top="8vh" class="admin-dialog">
      <div class="dialog-body">
        <div class="field">
          <label class="field-label">昵称</label>
          <input v-model="editForm.nickname" class="dlg-input" type="text" placeholder="用户昵称" />
        </div>
        <div class="field">
          <label class="field-label">手机号</label>
          <input v-model="editForm.phone" class="dlg-input" type="tel" maxlength="11" placeholder="手机号" />
        </div>
        <div class="field">
          <label class="field-label">会员等级</label>
          <select v-model.number="editForm.vipLevel" class="dlg-input">
            <option :value="0">普通用户</option>
            <option :value="1">白银</option>
            <option :value="2">黄金</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label">账号状态</label>
          <select v-model.number="editForm.status" class="dlg-input">
            <option :value="0">正常</option>
            <option :value="1">封禁</option>
          </select>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <button class="btn-outline" @click="editVisible = false">取消</button>
          <button class="btn-primary" @click="submitEdit">保存</button>
        </div>
      </template>
    </el-dialog>

    <!-- 新增虚拟用户 -->
    <el-dialog v-model="createVisible" title="新增虚拟用户" width="92%" top="8vh" class="admin-dialog">
      <div class="dialog-body">
        <div class="field">
          <label class="field-label">昵称 <span class="req">*</span></label>
          <input v-model="createForm.nickname" class="dlg-input" type="text" placeholder="用户昵称" />
        </div>
        <div class="field">
          <label class="field-label">手机号 <span class="req">*</span></label>
          <input v-model="createForm.phone" class="dlg-input" type="tel" maxlength="11" placeholder="11 位手机号" />
        </div>
        <div class="field">
          <label class="field-label">登录密码</label>
          <input v-model="createForm.password" class="dlg-input" type="text" placeholder="默认 123456" />
        </div>
        <div class="field">
          <label class="field-label">会员等级</label>
          <select v-model.number="createForm.vipLevel" class="dlg-input">
            <option :value="0">普通用户</option>
            <option :value="1">白银</option>
            <option :value="2">黄金</option>
          </select>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <button class="btn-outline" @click="createVisible = false">取消</button>
          <button class="btn-primary" @click="submitCreate">创建</button>
        </div>
      </template>
    </el-dialog>

    <!-- 发送通知 -->
    <el-dialog v-model="notifyVisible" title="发送系统通知" width="92%" top="8vh" class="admin-dialog">
      <div class="dialog-body">
        <div class="field">
          <label class="field-label">标题</label>
          <input v-model="notifyForm.title" class="dlg-input" type="text" placeholder="默认：系统通知" />
        </div>
        <div class="field">
          <label class="field-label">内容 <span class="req">*</span></label>
          <textarea v-model="notifyForm.content" class="dlg-input" rows="3" placeholder="通知内容"></textarea>
        </div>
        <div class="field">
          <label class="field-label">接收对象</label>
          <select v-model="notifyForm.target" class="dlg-input">
            <option value="all">全部用户</option>
            <option value="vip">仅 VIP 用户</option>
          </select>
        </div>
      </div>
      <template #footer>
        <div class="modal-footer">
          <button class="btn-outline" @click="notifyVisible = false">取消</button>
          <button class="btn-primary" @click="submitNotify">发送</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.user-mgmt-view {
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
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: none;
}

.btn-primary {
  background: $color-brand;
  color: #ffffff;
}

.btn-outline {
  background: transparent;
  border: 1px solid $color-outline-variant;
  color: $color-on-surface;
}

// ============ KPI ============
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
  }
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
  &.bg-gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.bg-red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }

  .material-symbols-outlined {
    font-size: 22px;
  }
}

.kpi-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  margin-bottom: 4px;
}

.kpi-value {
  font-family: $font-family-headline;
  font-size: 28px;
  font-weight: 700;
  color: $color-on-surface;
}

// ============ Filter ============
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

.filter-group {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
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
}

// ============ Batch Bar ============
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

  .count {
    color: $color-brand;
    font-weight: 700;
  }
}

.batch-actions {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.batch-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;

  &:hover {
    color: $color-brand;
  }
}

.batch-sep {
  color: $color-outline-variant;
}

// ============ Table ============
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
  font-size: $font-size-body-md;
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

.checkbox-col {
  width: 40px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  object-fit: cover;
}

.user-name {
  font-weight: 700;
  color: $color-on-surface;
}

.user-id {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  font-family: $font-family-mono;
}

.phone-cell {
  font-family: $font-family-mono;
}

.vip-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.vip-text-muted {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}
.vip-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  font-family: $font-family-mono;
}

.vip-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  width: fit-content;

  .material-symbols-outlined {
    font-size: 14px;
    font-variation-settings: 'FILL' 1;
  }

  &.gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.silver { background: rgba(0, 96, 172, 0.12); color: #6b7a8a; }
  &.platinum { background: linear-gradient(135deg, rgba(32, 178, 178, 0.15) 0%, rgba(0, 96, 172, 0.15) 100%); color: #0f8a8a; }
  &.gray { background: $color-surface-container; color: $color-on-surface-variant; }

  &.expired {
    background: $color-surface-container !important;
    color: $color-on-surface-variant !important;
    opacity: 0.7;
  }
}

.vip-tag-expired {
  margin-left: 4px;
  padding: 0 6px;
  border-radius: $radius-sm;
  background: rgba($color-emergency, 0.12);
  color: $color-emergency;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  font-variation-settings: normal;
}

.date-cell {
  font-family: $font-family-mono;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.status-pill {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;

  .dot {
    width: 8px;
    height: 8px;
    border-radius: $radius-full;
  }

  &.normal { color: $color-success; .dot { background: $color-success; } }
  &.banned { color: $color-emergency; .dot { background: $color-emergency; } }
}

.action-row {
  display: flex;
  gap: $spacing-md;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-on-surface-variant;
  cursor: pointer;

  &:hover {
    color: $color-brand;
  }

  &.ban {
    color: $color-emergency;

    &:hover {
      color: $color-emergency;
      opacity: 0.7;
    }
  }

  &.unban {
    color: $color-success;

    &:hover {
      color: $color-success;
      opacity: 0.7;
    }
  }
}

// ============ Pagination ============
.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md $spacing-lg;
  border-top: 1px solid $color-outline-variant;
}

.page-info {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.page-buttons {
  display: flex;
  gap: $spacing-xs;
}

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

  &.active {
    background: $color-brand;
    color: #ffffff;
    border-color: $color-brand;
  }

  .material-symbols-outlined {
    font-size: 16px;
  }
}

.page-ellipsis {
  color: $color-text-tertiary;
  padding: 0 4px;
}

// ============ 弹窗（编辑 / 新增 / 通知） ============
.admin-dialog {
  border-radius: $radius-xl;
  overflow: hidden;

  :deep(.el-dialog__header) {
    margin: 0;
    padding: $spacing-lg $spacing-lg $spacing-sm;
    font-weight: 700;
  }

  :deep(.el-dialog__title) {
    font-family: $font-family-headline;
    font-weight: 700;
    color: $color-on-surface;
  }

  :deep(.el-dialog__body) {
    padding: $spacing-md $spacing-lg;
  }
}

.dialog-body {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.field {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.field-label {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  font-weight: 600;

  .req {
    color: $color-emergency;
  }
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
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &:focus {
    outline: none;
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.15);
  }
}

.modal-footer {
  display: flex;
  gap: $spacing-md;
  justify-content: flex-end;
  padding: $spacing-md $spacing-lg $spacing-lg;
  border-top: 1px solid $color-outline-variant;

  .btn-primary,
  .btn-outline {
    // 弹窗按钮不撑满，保持紧凑
    flex: 0 0 auto;
    min-width: 96px;
    justify-content: center;
  }
}
</style>