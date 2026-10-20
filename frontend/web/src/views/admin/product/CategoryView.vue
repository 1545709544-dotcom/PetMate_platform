<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

interface Category {
  id: number
  parentId: number
  name: string
  icon?: string
  level: number
  sort?: number
  children?: Category[]
}

const all = ref<Category[]>([])
const loading = ref(false)
const editing = ref<Category | null>(null)
const dialog = ref(false)
const form = ref({ id: 0, name: '', parentId: 0, icon: '', sort: 0, level: 1 })

const tree = computed(() => {
  const map = new Map<number, Category & { children: Category[] }>()
  all.value.forEach((c) => map.set(c.id, { ...c, children: [] }))
  const roots: Category[] = []
  map.forEach((c) => {
    if (c.parentId && map.has(c.parentId)) map.get(c.parentId)!.children.push(c)
    else roots.push(c)
  })
  return roots
})

const flat = computed(() => all.value)

async function load() {
  loading.value = true
  try {
    all.value = (await adminApi.categories()) as Category[]
  } catch (e: any) {
    ElMessage.error(e?.message || '加载分类失败')
  } finally {
    loading.value = false
  }
}

function openCreate(parentId = 0, level = 1) {
  form.value = { id: 0, name: '', parentId, icon: '', sort: 0, level }
  editing.value = null
  dialog.value = true
}

function openEdit(c: Category) {
  form.value = { id: c.id, name: c.name, parentId: c.parentId, icon: c.icon || '', sort: c.sort || 0, level: c.level }
  editing.value = c
  dialog.value = true
}

async function save() {
  if (!form.value.name.trim()) return ElMessage.warning('请填写分类名称')
  try {
    if (form.value.id) {
      await adminApi.updateCategory(form.value.id, {
        name: form.value.name,
        parentId: form.value.parentId,
        icon: form.value.icon,
        sort: form.value.sort,
      })
      ElMessage.success('已更新')
    } else {
      await adminApi.createCategory({
        name: form.value.name,
        parentId: form.value.parentId,
        icon: form.value.icon,
        sort: form.value.sort,
        level: form.value.level,
      })
      ElMessage.success('已创建')
    }
    dialog.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

async function remove(c: Category) {
  try {
    await ElMessageBox.confirm(`确认删除分类「${c.name}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  try {
    await adminApi.deleteCategory(c.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

function parentName(parentId: number) {
  if (!parentId) return '顶级'
  return flat.value.find((c) => c.id === parentId)?.name || `#${parentId}`
}

onMounted(load)
</script>

<template>
  <div class="category-view">
    <div class="page-head">
      <div>
        <h1 class="page-title">分类管理</h1>
        <p class="page-sub">商品三级分类树</p>
      </div>
      <button class="btn-primary" @click="openCreate(0, 1)">
        <span class="material-symbols-outlined">add</span>
        新增一级分类
      </button>
    </div>

    <section class="tree-card" v-loading="loading">
      <div v-if="!tree.length" class="empty-tip">暂无分类，点击右上角创建</div>
      <ul v-else class="tree">
        <li v-for="r in tree" :key="r.id" class="tree-node">
          <div class="node-row">
            <span class="material-symbols-outlined">folder</span>
            <span class="name">{{ r.name }}</span>
            <span class="meta">顶级</span>
            <div class="actions">
              <button class="link" @click="openCreate(r.id, 2)">+ 子分类</button>
              <button class="link" @click="openEdit(r)">编辑</button>
              <button class="link danger" @click="remove(r)">删除</button>
            </div>
          </div>
          <ul v-if="r.children && r.children.length" class="tree-sub">
            <li v-for="sub in r.children" :key="sub.id" class="tree-node sub">
              <div class="node-row">
                <span class="material-symbols-outlined">label</span>
                <span class="name">{{ sub.name }}</span>
                <span class="meta">父级：{{ r.name }}</span>
                <div class="actions">
                  <button class="link" @click="openEdit(sub)">编辑</button>
                  <button class="link danger" @click="remove(sub)">删除</button>
                </div>
              </div>
            </li>
          </ul>
        </li>
      </ul>
    </section>

    <el-dialog v-model="dialog" :title="form.id ? '编辑分类' : '新增分类'" width="500px">
      <div class="form">
        <div class="field">
          <label class="field-label">分类名称 <span class="required">*</span></label>
          <input v-model="form.name" class="input" placeholder="例：宠物零食" maxlength="20" />
        </div>
        <div class="field" v-if="!form.id">
          <label class="field-label">父级分类</label>
          <select v-model="form.parentId" class="input">
            <option :value="0">顶级分类</option>
            <option v-for="c in flat" :key="c.id" :value="c.id">{{ c.name }} (#{{ c.id }})</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label">图标 (可选)</label>
          <input v-model="form.icon" class="input" placeholder="emoji 或 URL" />
        </div>
        <div class="field">
          <label class="field-label">排序</label>
          <input v-model.number="form.sort" type="number" class="input" placeholder="数字越小越靠前" />
        </div>
      </div>
      <template #footer>
        <div class="dialog-actions">
          <button class="link" @click="dialog = false">取消</button>
          <button class="btn-primary" @click="save">保存</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.category-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }

.btn-primary {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  background: $color-brand; color: #fff; border: none; border-radius: $radius-md; font-weight: 700; cursor: pointer;
}

.tree-card { padding: $spacing-lg; background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; }
.empty-tip { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }

.tree, .tree-sub { list-style: none; padding: 0; margin: 0; }
.tree-sub { margin-left: $spacing-xl; }

.tree-node { padding: $spacing-xs 0; }

.node-row {
  display: flex; align-items: center; gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-default;
  &:hover { background: $color-surface-container-low; }
}

.name { font-weight: 700; color: $color-on-surface; flex: 1; }
.meta { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.actions { display: flex; gap: $spacing-sm; }
.link { background: none; border: none; color: $color-secondary; cursor: pointer; font-size: $font-size-label-sm; font-weight: 700; }
.link.danger { color: $color-emergency; }
.link:hover { text-decoration: underline; }

.form { display: flex; flex-direction: column; gap: $spacing-md; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.required { color: $color-emergency; }
.input {
  padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-default;
  background: #fff; font-size: $font-size-body-md; color: $color-on-surface; font-family: inherit;
}
.dialog-actions { display: flex; justify-content: flex-end; gap: $spacing-sm; }
.dialog-actions .link { padding: $spacing-sm $spacing-lg; color: $color-on-surface-variant; }
</style>
