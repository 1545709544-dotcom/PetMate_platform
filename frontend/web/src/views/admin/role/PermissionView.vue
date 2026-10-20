<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adminApi, type AdminRole, type AdminPermModule } from '@/api/modules/admin'

const route = useRoute()
const router = useRouter()

const roles = ref<AdminRole[]>([])
const permTree = ref<AdminPermModule[]>([])
const loading = ref(false)
const saving = ref(false)
const selectedId = ref<string>('')

const selectedRole = computed<AdminRole | undefined>(() =>
  roles.value.find((r) => r.id === selectedId.value),
)

const totalPerms = computed(() => {
  let n = 0
  permTree.value.forEach((m) => (n += m.perms.length))
  return n
})

const allKeys = computed(() => {
  const set = new Set<string>()
  permTree.value.forEach((m) => m.perms.forEach((p) => set.add(p.key)))
  return set
})

function expandedCount(perms: string[]): number {
  if (perms.includes('*')) return allKeys.value.size
  let n = 0
  for (const p of perms) {
    if (p.endsWith('.*')) {
      const prefix = p.slice(0, -1) + '.'
      allKeys.value.forEach((k) => { if (k.startsWith(prefix)) n++ })
    } else if (allKeys.value.has(p)) {
      n++
    }
  }
  return n
}

const selectedCount = computed(() => (selectedRole.value ? expandedCount(selectedRole.value.perms) : 0))

function hasStar(): boolean {
  return !!selectedRole.value?.perms.includes('*')
}
function isAdmin(): boolean {
  return selectedRole.value?.id === 'admin'
}

function isPermChecked(mk: string, pk: string): boolean {
  const r = selectedRole.value
  if (!r) return false
  if (r.perms.includes('*')) return true
  if (r.perms.includes(mk + '.*')) return true
  return r.perms.includes(pk)
}

function moduleStat(mk: string, perms: { key: string; label: string }[]) {
  const r = selectedRole.value
  if (!r) return { all: false, some: false }
  let checked = 0
  for (const p of perms) if (isPermChecked(mk, p.key)) checked++
  const all = checked === perms.length
  return { all, some: checked > 0 && !all }
}

function setPerms(next: string[]) {
  const r = selectedRole.value
  if (r) r.perms = next
}

function toggleStar() {
  const r = selectedRole.value
  if (!r || isAdmin()) return // admin 必须保留 *
  setPerms(r.perms.includes('*') ? [] : ['*'])
}

function toggleModule(mk: string, perms: { key: string; label: string }[]) {
  const r = selectedRole.value
  if (!r || hasStar()) return
  const stat = moduleStat(mk, perms)
  if (stat.all) {
    // 取消整模块：移除 module.* 及该模块全部具体权限
    const next = r.perms.filter((x) => x !== mk + '.*' && !perms.some((p) => p.key === x))
    setPerms(next)
  } else {
    // 全选整模块：移除具体权限，写入 module.*
    const next = r.perms.filter((x) => !perms.some((p) => p.key === x))
    next.push(mk + '.*')
    setPerms(next)
  }
}

function togglePerm(mk: string, pk: string) {
  const r = selectedRole.value
  if (!r || hasStar()) return
  let perms = r.perms.filter((p) => p !== mk + '.*')
  if (perms.includes(pk)) perms = perms.filter((p) => p !== pk)
  else perms = [...perms, pk]
  setPerms(perms)
}

async function load() {
  loading.value = true
  try {
    const r = await adminApi.roles()
    roles.value = r.roles as AdminRole[]
    permTree.value = r.permTree
    const q = route.query.role
    const fromQuery = typeof q === 'string' ? q : ''
    selectedId.value =
      roles.value.some((x) => x.id === fromQuery) ? fromQuery : (roles.value[0]?.id ?? '')
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!roles.value.length) return
  saving.value = true
  try {
    await adminApi.saveRoles(roles.value)
    ElMessage.success(`「${selectedRole.value?.name}」的权限已保存`)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="perm-view" v-loading="loading">
    <div class="page-head">
      <div>
        <button class="back-link" @click="router.push('/admin/role/list')">
          <span class="material-symbols-outlined">arrow_back</span>
          角色管理
        </button>
        <h1 class="page-title">权限分配</h1>
        <p class="page-sub">为所选角色勾选可操作的模块与功能</p>
      </div>
      <div class="head-actions">
        <el-select v-model="selectedId" placeholder="选择角色" class="role-select">
          <el-option v-for="r in roles" :key="r.id" :label="`${r.name}（${r.id}）`" :value="r.id" />
        </el-select>
        <button class="btn-primary" @click="save" :disabled="saving">
          <span class="material-symbols-outlined">save</span>
          {{ saving ? '保存中…' : '保存权限' }}
        </button>
      </div>
    </div>

    <section class="ctx-bar card" v-if="selectedRole">
      <div class="ctx-info">
        <span class="ctx-name">{{ selectedRole.name }}</span>
        <span v-if="selectedRole.builtin" class="tag builtin-tag">内置</span>
        <span class="ctx-desc">{{ selectedRole.description || '暂无职责说明' }}</span>
      </div>
      <div class="ctx-count">
        <span class="count-num" :class="{ all: hasStar() }">{{ hasStar() ? '全部' : selectedCount }}</span>
        <span class="count-total">/ {{ totalPerms }} 项权限</span>
      </div>
    </section>

    <label class="star-row card" :class="{ locked: isAdmin() }">
      <input type="checkbox" :checked="hasStar()" :disabled="isAdmin()" @change="toggleStar" />
      <span class="star-icon material-symbols-outlined">shield</span>
      <span class="star-text">
        <strong>超级权限（*）</strong>
        <small>{{ isAdmin() ? '内置管理员必须保留全部权限，不可取消' : '勾选后该角色拥有平台所有操作的权限' }}</small>
      </span>
    </label>

    <div class="tree" v-if="selectedRole && !hasStar()">
      <section v-for="m in permTree" :key="m.module" class="module card">
        <header class="module-head">
          <label class="module-check">
            <input
              type="checkbox"
              :checked="moduleStat(m.module, m.perms).all"
              :indeterminate.prop="moduleStat(m.module, m.perms).some"
              @change="toggleModule(m.module, m.perms)"
            />
            <span class="module-label">{{ m.label }}</span>
          </label>
          <span class="module-key">{{ m.module }}.*</span>
        </header>
        <div class="perm-list">
          <label v-for="p in m.perms" :key="p.key" class="perm-item" :class="{ on: isPermChecked(m.module, p.key) }">
            <input type="checkbox" :checked="isPermChecked(m.module, p.key)" @change="togglePerm(m.module, p.key)" />
            <span class="perm-label">{{ p.label }}</span>
            <code class="perm-key">{{ p.key }}</code>
          </label>
        </div>
      </section>
    </div>
    <div class="tree" v-else-if="selectedRole && hasStar()">
      <section class="module card all-card">
        <p>该角色已拥有 <strong>全部权限（*）</strong>，无需逐项勾选。</p>
      </section>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.perm-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; gap: $spacing-md; flex-wrap: wrap; }
.back-link {
  display: inline-flex; align-items: center; gap: 2px; background: none; border: none; cursor: pointer;
  color: $color-on-surface-variant; font-size: $font-size-label-md; font-weight: 700; padding: 0; margin-bottom: 2px;
  .material-symbols-outlined { font-size: 16px; }
  &:hover { color: $color-brand; }
}
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; margin: 0; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin: 4px 0 0; }

.head-actions { display: flex; gap: $spacing-sm; align-items: center; }
.role-select { width: 240px; }
.btn-primary {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md; font-weight: 700; cursor: pointer; border: 1px solid transparent;
  background: $color-brand; color: #fff; font-size: $font-size-body-md;
  &:disabled { opacity: .6; cursor: default; }
}

.card { background: $color-surface-container-lowest; border-radius: $radius-lg; box-shadow: $shadow-sm; }

.ctx-bar { display: flex; align-items: center; justify-content: space-between; gap: $spacing-md; padding: $spacing-md $spacing-lg; }
.ctx-info { display: flex; align-items: center; gap: $spacing-sm; flex-wrap: wrap; }
.ctx-name { font-family: $font-family-headline; font-size: $font-size-headline-md; font-weight: 700; color: $color-on-surface; }
.ctx-desc { font-size: $font-size-body-md; color: $color-on-surface-variant; }
.builtin-tag { font-size: $font-size-label-sm; font-weight: 700; color: $color-brand; background: rgba(255,122,50,.12); padding: 2px $spacing-sm; border-radius: $radius-full; }
.ctx-count { display: flex; align-items: baseline; gap: 4px; white-space: nowrap; }
.count-num { font-family: $font-family-headline; font-size: $font-size-headline-md; font-weight: 800; color: $color-brand; &.all { color: $color-emergency; } }
.count-total { font-size: $font-size-label-sm; color: $color-on-surface-variant; }

.star-row {
  display: flex; align-items: center; gap: $spacing-sm; padding: $spacing-md $spacing-lg; cursor: pointer;
  border: 1px solid rgba(255,77,79,.25); background: rgba(255,77,79,.06);
  &.locked { opacity: .85; }
  input { width: 18px; height: 18px; accent-color: $color-emergency; }
  .star-icon { color: $color-emergency; }
  .star-text { display: flex; flex-direction: column; }
  .star-text strong { color: $color-emergency; font-size: $font-size-body-md; }
  .star-text small { color: $color-on-surface-variant; font-size: $font-size-label-sm; }
}

.tree { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: $spacing-md; }
.module { padding: $spacing-md $spacing-lg; display: flex; flex-direction: column; gap: $spacing-sm; }
.module-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: $spacing-sm; border-bottom: 1px solid $color-outline-variant; }
.module-check { display: flex; align-items: center; gap: $spacing-xs; cursor: pointer; }
.module-check input { width: 18px; height: 18px; accent-color: $color-brand; }
.module-label { font-family: $font-family-headline; font-size: $font-size-body-lg; font-weight: 700; color: $color-on-surface; }
.module-key { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; }

.perm-list { display: grid; grid-template-columns: 1fr; gap: 4px; }
.perm-item {
  display: flex; align-items: center; gap: $spacing-sm; padding: $spacing-xs $spacing-sm; border-radius: $radius-default;
  cursor: pointer; transition: background .15s ease;
  &:hover { background: $color-surface-container-low; }
  &.on { background: rgba(255,122,50,.08); }
  input { width: 16px; height: 16px; accent-color: $color-brand; }
}
.perm-label { font-size: $font-size-body-md; color: $color-on-surface; flex: 1; }
.perm-key { font-family: $font-family-mono; font-size: $font-size-label-sm; color: $color-on-surface-variant; }

.all-card { padding: $spacing-lg; color: $color-on-surface-variant; text-align: center; strong { color: $color-emergency; } }
</style>
