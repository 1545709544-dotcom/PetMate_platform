<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi, type AdminRole, type AdminPermModule } from '@/api/modules/admin'

const router = useRouter()

const roles = ref<AdminRole[]>([])
const permTree = ref<AdminPermModule[]>([])
const loading = ref(false)
const saving = ref(false)

const addVisible = ref(false)
const addForm = ref({ id: '', name: '', description: '' })

const editVisible = ref(false)
const editForm = ref<{ id: string; name: string; description: string }>({ id: '', name: '', description: '' })

// 扁平的全部权限 key 集合（用于通配符展开计数）
const allPermKeys = computed(() => {
  const set = new Set<string>()
  permTree.value.forEach((m) => m.perms.forEach((p) => set.add(p.key)))
  return set
})

function expandCount(perms: string[]): number {
  if (perms.includes('*')) return allPermKeys.value.size
  let n = 0
  for (const p of perms) {
    if (p.endsWith('.*')) {
      const prefix = p.slice(0, -1) + '.'
      allPermKeys.value.forEach((k) => { if (k.startsWith(prefix)) n++ })
    } else if (allPermKeys.value.has(p)) {
      n++
    }
  }
  return n
}

function permBadge(role: AdminRole): string {
  if (role.perms.includes('*')) return '全部权限'
  const c = expandCount(role.perms)
  return `${c} 项权限`
}

async function load() {
  loading.value = true
  try {
    const r = await adminApi.roles()
    roles.value = r.roles as AdminRole[]
    permTree.value = r.permTree
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openAdd() {
  addForm.value = { id: '', name: '', description: '' }
  addVisible.value = true
}

function submitAdd() {
  const id = addForm.value.id.trim()
  const name = addForm.value.name.trim()
  const desc = addForm.value.description.trim()
  if (!id) return ElMessage.warning('请填写角色 ID（英文标识）')
  if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(id)) return ElMessage.warning('角色 ID 只能包含字母、数字、下划线，且以字母开头')
  if (!name) return ElMessage.warning('请填写角色名称')
  if (roles.value.some((r) => r.id === id)) return ElMessage.warning('角色 ID 已存在')
  roles.value.push({ id, name, description: desc, builtin: false, perms: [] })
  addVisible.value = false
  ElMessage.success('已新增，记得点击「保存」')
}

function openEdit(role: AdminRole) {
  editForm.value = { id: role.id, name: role.name, description: role.description || '' }
  editVisible.value = true
}

function submitEdit() {
  const role = roles.value.find((r) => r.id === editForm.value.id)
  if (!role) return
  if (!editForm.value.name.trim()) return ElMessage.warning('请填写角色名称')
  role.name = editForm.value.name.trim()
  role.description = editForm.value.description.trim()
  editVisible.value = false
  ElMessage.success('已更新，记得点击「保存」')
}

async function removeRole(role: AdminRole) {
  if (role.builtin || role.id === 'admin') return ElMessage.warning('内置管理员角色不可删除')
  try {
    await ElMessageBox.confirm(`确认删除角色「${role.name}」？该角色的权限配置将一并移除。`, '删除确认', { type: 'warning' })
  } catch { return }
  roles.value = roles.value.filter((x) => x.id !== role.id)
  ElMessage.success('已删除，记得点击「保存」')
}

function goAssign(role: AdminRole) {
  router.push({ path: '/admin/role/permission', query: { role: role.id } })
}

async function save() {
  if (roles.value.some((r) => !r.name || !r.name.trim())) {
    return ElMessage.warning('存在未命名角色，请补全名称')
  }
  saving.value = true
  try {
    await adminApi.saveRoles(roles.value)
    ElMessage.success('角色配置已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="role-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">角色管理</h1>
        <p class="page-sub">维护平台角色及其职责说明，权限通过「分配权限」独立配置</p>
      </div>
      <div class="head-actions">
        <button class="btn-outline" @click="openAdd">
          <span class="material-symbols-outlined">add</span>
          新增角色
        </button>
        <button class="btn-primary" @click="save" :disabled="saving">
          <span class="material-symbols-outlined">save</span>
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </div>

    <div class="role-grid">
      <article
        v-for="r in roles"
        :key="r.id"
        class="role-card card"
        :class="{ builtin: r.builtin }"
      >
        <header class="role-card-head">
          <div class="role-title">
            <span class="role-name">{{ r.name }}</span>
            <span v-if="r.builtin" class="tag builtin-tag">内置</span>
          </div>
          <code class="role-id">{{ r.id }}</code>
        </header>

        <p class="role-desc">{{ r.description || '暂无职责说明' }}</p>

        <div class="role-meta">
          <span class="perm-badge" :class="{ all: r.perms.includes('*') }">
            <span class="material-symbols-outlined">{{ r.perms.includes('*') ? 'verified' : 'lock' }}</span>
            {{ permBadge(r) }}
          </span>
        </div>

        <footer class="role-actions">
          <button class="link primary" @click="goAssign(r)">
            <span class="material-symbols-outlined">tune</span>
            分配权限
          </button>
          <button class="link" @click="openEdit(r)">
            <span class="material-symbols-outlined">edit</span>
            编辑
          </button>
          <button
            class="link danger"
            :disabled="r.builtin"
            :title="r.builtin ? '内置角色不可删除' : '删除角色'"
            @click="removeRole(r)"
          >
            <span class="material-symbols-outlined">delete</span>
            删除
          </button>
        </footer>
      </article>

      <button class="role-card add-card card" @click="openAdd">
        <span class="material-symbols-outlined">add_circle</span>
        <span>新增角色</span>
      </button>
    </div>

    <!-- 新增角色 -->
    <el-dialog v-model="addVisible" title="新增角色" width="440px" align-center>
      <div class="form-grid">
        <label class="field">
          <span class="field-label">角色 ID（英文标识）</span>
          <input v-model="addForm.id" class="input" placeholder="如 finance" />
        </label>
        <label class="field">
          <span class="field-label">角色名称</span>
          <input v-model="addForm.name" class="input" placeholder="如 财务管理" />
        </label>
        <label class="field">
          <span class="field-label">职责说明</span>
          <textarea v-model="addForm.description" class="input" rows="2" placeholder="该角色负责哪些工作…"></textarea>
        </label>
      </div>
      <template #footer>
        <button class="btn-outline" @click="addVisible = false">取消</button>
        <button class="btn-primary" @click="submitAdd">确定新增</button>
      </template>
    </el-dialog>

    <!-- 编辑角色 -->
    <el-dialog v-model="editVisible" title="编辑角色" width="440px" align-center>
      <div class="form-grid">
        <label class="field">
          <span class="field-label">角色 ID</span>
          <input :value="editForm.id" class="input" disabled />
        </label>
        <label class="field">
          <span class="field-label">角色名称</span>
          <input v-model="editForm.name" class="input" placeholder="角色名称" />
        </label>
        <label class="field">
          <span class="field-label">职责说明</span>
          <textarea v-model="editForm.description" class="input" rows="2" placeholder="该角色负责哪些工作…"></textarea>
        </label>
      </div>
      <template #footer>
        <button class="btn-outline" @click="editVisible = false">取消</button>
        <button class="btn-primary" @click="submitEdit">保存修改</button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.role-view { display: flex; flex-direction: column; gap: $spacing-lg; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: $spacing-md; flex-wrap: wrap; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }

.head-actions { display: flex; gap: $spacing-sm; }
.btn-primary, .btn-outline {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md; font-weight: 700; cursor: pointer; border: 1px solid transparent; font-size: $font-size-body-md;
}
.btn-primary { background: $color-brand; color: #fff; &:disabled { opacity: .6; cursor: default; } }
.btn-outline { background: transparent; color: $color-brand; border-color: $color-brand; }

.role-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: $spacing-md;
}
.card { background: $color-surface-container-lowest; border-radius: $radius-lg; box-shadow: $shadow-sm; }

.role-card {
  padding: $spacing-lg; display: flex; flex-direction: column; gap: $spacing-sm;
  border: 1px solid transparent; transition: transform .2s ease, box-shadow .2s ease, border-color .2s ease;
  &:hover { transform: translateY(-2px); box-shadow: $shadow-md; border-color: rgba(255,122,50,.25); }
  &.builtin { border-color: rgba(255,122,50,.18); }
}
.role-card-head { display: flex; align-items: center; justify-content: space-between; gap: $spacing-sm; }
.role-title { display: flex; align-items: center; gap: $spacing-xs; }
.role-name { font-family: $font-family-headline; font-size: $font-size-headline-md; font-weight: 700; color: $color-on-surface; }
.role-id { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; background: $color-surface-container-low; padding: 2px $spacing-sm; border-radius: $radius-sm; }
.builtin-tag { font-size: $font-size-label-sm; font-weight: 700; color: $color-brand; background: rgba(255,122,50,.12); padding: 2px $spacing-sm; border-radius: $radius-full; }

.role-desc { font-size: $font-size-body-md; color: $color-on-surface-variant; min-height: 40px; line-height: 1.5; margin: 0; }

.role-meta { display: flex; }
.perm-badge {
  display: inline-flex; align-items: center; gap: 4px; font-size: $font-size-label-sm; font-weight: 700;
  color: $color-on-surface-variant; background: $color-surface-container-low; padding: 4px $spacing-md; border-radius: $radius-full;
  .material-symbols-outlined { font-size: 16px; }
  &.all { color: $color-emergency; background: rgba(255,77,79,.1); }
}

.role-actions { display: flex; gap: $spacing-md; margin-top: auto; padding-top: $spacing-sm; border-top: 1px solid $color-outline-variant; }
.link {
  display: inline-flex; align-items: center; gap: 4px; background: none; border: none; cursor: pointer;
  font-size: $font-size-label-md; font-weight: 700; color: $color-on-surface-variant; padding: 0;
  .material-symbols-outlined { font-size: 16px; }
  &:hover { color: $color-brand; }
  &.primary { color: $color-brand; }
  &.danger { color: $color-emergency; &:hover { color: $color-emergency; } }
  &:disabled { opacity: .4; cursor: not-allowed; }
}

.add-card {
  align-items: center; justify-content: center; gap: $spacing-xs; cursor: pointer;
  color: $color-on-surface-variant; border: 1px dashed $color-outline-variant; background: transparent;
  font-weight: 700; min-height: 120px;
  .material-symbols-outlined { font-size: 28px; }
  &:hover { color: $color-brand; border-color: $color-brand; transform: translateY(-2px); }
}

.form-grid { display: flex; flex-direction: column; gap: $spacing-md; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field-label { font-size: $font-size-label-md; font-weight: 600; color: $color-on-surface-variant; }
.input {
  padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-default;
  background: #fff; font-size: $font-size-body-md; font-family: inherit; color: $color-on-surface;
  &:focus { outline: none; border-color: $color-brand; }
  &:disabled { background: $color-surface-container-low; color: $color-on-surface-variant; }
}
</style>
