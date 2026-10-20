<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { merchantApi } from '@/api/modules/merchant'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const staff = ref<any[]>([])
const roles = ref<any[]>([])
const dialogVisible = ref(false)
const editing = ref<any>(null)
const form = ref({ username: '', nickname: '', password: '', role: 'OPERATOR' })

// 筛选 / 搜索
const search = ref('')
const roleFilter = ref('ALL')

const ROLE_ICON: Record<string, string> = {
  OWNER: 'shield',
  OPERATOR: 'storefront',
  CS: 'headset_mic',
  WAREHOUSE: 'local_shipping',
}
const roleIcon = (k: string) => ROLE_ICON[k] || 'person'
const ROLE_BG: Record<string, string> = {
  OWNER: 'linear-gradient(135deg,#ff7a32,#ff9a5a)',
  OPERATOR: 'linear-gradient(135deg,#7c5cff,#a78bfa)',
  CS: 'linear-gradient(135deg,#0a9b8e,#34d3c4)',
  WAREHOUSE: 'linear-gradient(135deg,#5b6b73,#8a9aa3)',
}
const roleName = (k: string) => roles.value.find((r) => r.key === k)?.name || k
const roleDesc = (k: string) => roles.value.find((r) => r.key === k)?.desc || ''

// 统计概览
const stats = computed(() => ({
  total: staff.value.length,
  active: staff.value.filter((s) => s.status === 1).length,
  inactive: staff.value.filter((s) => s.status !== 1).length,
  roles: roles.value.length,
}))

// 筛选后的员工
const filtered = computed(() => {
  const kw = search.value.trim().toLowerCase()
  return staff.value.filter((s) => {
    const byRole = roleFilter.value === 'ALL' || s.role === roleFilter.value
    const byKw =
      !kw ||
      (s.nickname || '').toLowerCase().includes(kw) ||
      (s.username || '').toLowerCase().includes(kw)
    return byRole && byKw
  })
})

// 角色筛选 chips（含数量）
const roleChips = computed(() => [
  { key: 'ALL', name: '全部', icon: 'group', count: staff.value.length },
  ...roles.value.map((r) => ({
    key: r.key,
    name: r.name,
    icon: roleIcon(r.key),
    count: staff.value.filter((s) => s.role === r.key).length,
  })),
])

const initial = (s: any) => (s.nickname || s.username || '?').trim().charAt(0).toUpperCase()

async function load() {
  loading.value = true
  try {
    const [s, r] = await Promise.all([merchantApi.staffList(), merchantApi.roleDefs()])
    staff.value = s || []
    roles.value = r || []
  } catch {
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = { username: '', nickname: '', password: '', role: 'OPERATOR' }
  dialogVisible.value = true
}

function openEdit(row: any) {
  editing.value = row
  form.value = { username: row.username, nickname: row.nickname, password: '', role: row.role }
  dialogVisible.value = true
}

async function submit() {
  if (!form.value.username.trim()) {
    ElMessage.warning('账号不能为空')
    return
  }
  try {
    if (editing.value) {
      await merchantApi.updateStaff(editing.value.id, {
        nickname: form.value.nickname || undefined,
        role: form.value.role,
        status: editing.value.status,
      })
      ElMessage.success('已更新')
    } else {
      await merchantApi.createStaff({
        username: form.value.username.trim(),
        nickname: form.value.nickname.trim() || undefined,
        password: form.value.password || undefined,
        role: form.value.role,
      })
      ElMessage.success('员工已创建（默认密码 123456）')
    }
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function toggleStatus(row: any) {
  const next = row.status === 1 ? 0 : 1
  try {
    await merchantApi.updateStaff(row.id, { status: next })
    row.status = next
    ElMessage.success(next === 1 ? '已启用' : '已停用')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function remove(row: any) {
  try {
    await ElMessageBox.confirm(`确认停用并删除员工「${row.nickname}」？`, '提示', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await merchantApi.deleteStaff(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <!-- 头部 -->
    <div class="page-head">
      <div>
        <h1 class="page-title">员工管理</h1>
        <p class="page-sub">创建子账号并分配角色，实现多员工协作与权限隔离（仅店主可管理）。</p>
      </div>
      <button class="btn-primary" @click="openCreate">
        <span class="material-symbols-outlined">person_add</span>
        新增员工
      </button>
    </div>

    <!-- 统计概览 -->
    <div class="stats">
      <div class="stat-card">
        <span class="material-symbols-outlined">group</span>
        <div>
          <div class="stat-num">{{ stats.total }}</div>
          <div class="stat-label">员工总数</div>
        </div>
      </div>
      <div class="stat-card ok">
        <span class="material-symbols-outlined">check_circle</span>
        <div>
          <div class="stat-num">{{ stats.active }}</div>
          <div class="stat-label">启用中</div>
        </div>
      </div>
      <div class="stat-card off">
        <span class="material-symbols-outlined">do_not_disturb_on</span>
        <div>
          <div class="stat-num">{{ stats.inactive }}</div>
          <div class="stat-label">已停用</div>
        </div>
      </div>
      <div class="stat-card role">
        <span class="material-symbols-outlined">admin_panel_settings</span>
        <div>
          <div class="stat-num">{{ stats.roles }}</div>
          <div class="stat-label">角色类型</div>
        </div>
      </div>
    </div>

    <!-- 工具栏：角色筛选 + 搜索 -->
    <div class="toolbar">
      <div class="role-chips">
        <button
          v-for="c in roleChips"
          :key="c.key"
          class="chip"
          :class="{ active: roleFilter === c.key }"
          :title="c.key !== 'ALL' ? roleDesc(c.key) : '全部员工'"
          @click="roleFilter = c.key"
        >
          <span class="material-symbols-outlined">{{ c.icon }}</span>
          {{ c.name }}
          <span class="chip-count">{{ c.count }}</span>
        </button>
      </div>
      <div class="search">
        <span class="material-symbols-outlined">search</span>
        <input v-model="search" class="search-input" placeholder="搜索昵称或账号" />
      </div>
    </div>

    <!-- 员工卡片网格 -->
    <div v-if="loading" class="state">加载中…</div>
    <div v-else-if="filtered.length === 0" class="state">
      {{ staff.length === 0 ? '暂无员工，点击右上角新增' : '没有匹配的员工' }}
    </div>
    <div v-else class="staff-grid">
      <div v-for="s in filtered" :key="s.id" class="staff-card">
        <span class="status-dot" :class="s.status === 1 ? 'on' : 'off'"></span>
        <div class="card-head">
          <div class="avatar" :style="{ background: ROLE_BG[s.role] || ROLE_BG.OPERATOR }">
            {{ initial(s) }}
          </div>
          <div class="who">
            <div class="name">{{ s.nickname || s.username }}</div>
            <div class="account">{{ s.username }}</div>
          </div>
        </div>
        <div class="card-meta">
          <span class="role-tag" :class="'rt-' + s.role">
            <span class="material-symbols-outlined">{{ roleIcon(s.role) }}</span>
            {{ roleName(s.role) }}
          </span>
          <span class="status" :class="s.status === 1 ? 'on' : 'off'">
            {{ s.status === 1 ? '启用' : '停用' }}
          </span>
        </div>
        <div class="card-actions">
          <button class="act" @click="openEdit(s)">
            <span class="material-symbols-outlined">edit</span>编辑
          </button>
          <button class="act" @click="toggleStatus(s)">
            <span class="material-symbols-outlined">{{ s.status === 1 ? 'block' : 'check_circle' }}</span>
            {{ s.status === 1 ? '停用' : '启用' }}
          </button>
          <button class="act danger" @click="remove(s)">
            <span class="material-symbols-outlined">delete</span>删除
          </button>
        </div>
      </div>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑员工' : '新增员工'" width="420px">
      <div class="form-row">
        <label>账号</label>
        <input v-model="form.username" class="input" :disabled="!!editing" placeholder="登录账号" />
      </div>
      <div class="form-row">
        <label>昵称</label>
        <input v-model="form.nickname" class="input" placeholder="显示名称" />
      </div>
      <div class="form-row" v-if="!editing">
        <label>密码</label>
        <input v-model="form.password" class="input" placeholder="默认 123456，可留空" />
      </div>
      <div class="form-row">
        <label>角色</label>
        <select v-model="form.role" class="input">
          <option value="OPERATOR">运营</option>
          <option value="CS">客服</option>
          <option value="WAREHOUSE">仓管</option>
        </select>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.page {
  width: 100%;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.page-title {
  margin: 0;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-extrabold;
  color: $color-text-primary;
}

.page-sub {
  margin: 4px 0 0;
  font-size: $font-size-body-md;
  color: $color-text-secondary;
}

.btn-primary {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  border: none;
  border-radius: $radius-md;
  background: $color-brand;
  color: #fff;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  box-shadow: 0 6px 18px rgba(255, 122, 50, 0.28);

  &:hover {
    transform: translateY(-1px);
    box-shadow: 0 10px 24px rgba(255, 122, 50, 0.36);
  }
  &:active {
    transform: translateY(0);
  }
}

// ============ 统计概览 ============
.stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md;
  border-radius: $radius-xl;
  background: $color-surface-container-lowest;
  border: 1px solid $color-surface-container;
  box-shadow: 0 1px 3px rgba(22, 29, 31, 0.04);

  .material-symbols-outlined {
    font-size: 30px;
    color: $color-brand;
  }
  .stat-num {
    font-size: $font-size-headline-md;
    font-weight: $font-weight-extrabold;
    line-height: 1.1;
    color: $color-text-primary;
  }
  .stat-label {
    font-size: $font-size-label-sm;
    color: $color-text-secondary;
  }
  &.ok .material-symbols-outlined { color: #0a9b8e; }
  &.off .material-symbols-outlined { color: $color-text-tertiary; }
  &.role .material-symbols-outlined { color: #7c5cff; }
}

// ============ 工具栏 ============
.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
  flex-wrap: wrap;
}

.role-chips {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px $spacing-sm;
  border-radius: 999px;
  border: 1px solid $color-surface-container;
  background: $color-surface-container-lowest;
  color: $color-text-secondary;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: all 0.18s ease;

  .material-symbols-outlined {
    font-size: 18px;
  }
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
  &.active {
    background: $color-brand;
    border-color: $color-brand;
    color: #fff;
  }
  .chip-count {
    font-size: $font-size-label-sm;
    padding: 0 6px;
    border-radius: 999px;
    background: rgba(0, 0, 0, 0.08);
  }
  &.active .chip-count {
    background: rgba(255, 255, 255, 0.25);
  }
}

.search {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px $spacing-sm;
  border-radius: 999px;
  border: 1px solid $color-surface-container;
  background: $color-surface-container-lowest;
  color: $color-text-tertiary;

  .search-input {
    border: none;
    outline: none;
    background: transparent;
    font-size: $font-size-label-md;
    color: $color-text-primary;
    width: 160px;
  }
}

// ============ 空 / 加载 ============
.state {
  text-align: center;
  padding: $spacing-3xl 0;
  color: $color-text-tertiary;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  border: 1px dashed $color-surface-container;
}

// ============ 员工卡片网格 ============
.staff-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: $spacing-md;
}

.staff-card {
  position: relative;
  background: $color-surface-container-lowest;
  border: 1px solid $color-surface-container;
  border-radius: $radius-xl;
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 12px 30px rgba(22, 29, 31, 0.1);
    border-color: $color-brand;
  }
}

.status-dot {
  position: absolute;
  top: $spacing-md;
  right: $spacing-md;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  &.on { background: #0a9b8e; box-shadow: 0 0 0 3px rgba(10, 155, 142, 0.18); }
  &.off { background: $color-text-tertiary; }
}

.card-head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: $font-weight-extrabold;
  font-size: 20px;
  flex-shrink: 0;
}

.who {
  min-width: 0;
  .name {
    font-size: $font-size-body-lg;
    font-weight: $font-weight-bold;
    color: $color-text-primary;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .account {
    font-family: monospace;
    font-size: $font-size-label-sm;
    color: $color-text-secondary;
  }
}

.card-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.role-tag {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  font-size: $font-size-label-sm;
  padding: 3px 10px;
  border-radius: 999px;
  font-weight: $font-weight-semibold;

  .material-symbols-outlined {
    font-size: 15px;
  }
  &.rt-OWNER { background: rgba($color-brand, 0.12); color: $color-brand; }
  &.rt-OPERATOR { background: rgba(#7c5cff, 0.12); color: #7c5cff; }
  &.rt-CS { background: rgba(#0a9b8e, 0.12); color: #0a9b8e; }
  &.rt-WAREHOUSE { background: rgba($color-text-secondary, 0.12); color: $color-text-secondary; }
}

.status {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  &.on { color: #0a9b8e; }
  &.off { color: $color-text-tertiary; }
}

.card-actions {
  margin-top: auto;
  padding-top: $spacing-sm;
  border-top: 1px solid $color-surface-container-low;
  display: flex;
  gap: $spacing-xs;
}

.act {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  border: none;
  background: $color-surface-container-low;
  color: $color-text-primary;
  border-radius: $radius-md;
  padding: 6px 4px;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background 0.18s ease, color 0.18s ease;

  .material-symbols-outlined {
    font-size: 16px;
  }
  &:hover {
    background: rgba($color-brand, 0.12);
    color: $color-brand;
  }
  &.danger:hover {
    background: rgba($color-emergency, 0.12);
    color: $color-emergency;
  }
}

// ============ 弹窗表单 ============
.form-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-md;

  label {
    width: 48px;
    font-size: $font-size-label-md;
    color: $color-text-secondary;
  }
  .input {
    flex: 1;
    padding: $spacing-xs $spacing-md;
    border: 1px solid $color-outline-variant;
    border-radius: $radius-md;
    font-size: $font-size-body-md;
  }
}

@media (max-width: 720px) {
  .stats {
    grid-template-columns: repeat(2, 1fr);
  }
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }
  .search {
    width: 100%;
    .search-input { width: 100%; }
  }
}
</style>
