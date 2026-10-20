<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adminApi,
  SENSITIVE_CATEGORIES,
  SENSITIVE_LEVELS,
  SENSITIVE_ACTIONS,
  type SensitiveWordDTO,
  type SensitiveTestHit,
} from '@/api/modules/admin'

// ============ 列表 + 筛选 ============
const list = ref<SensitiveWordDTO[]>([])
const total = ref(0)
const loading = ref(false)
const page = ref(1)
const size = ref(50)
const filters = reactive({
  keyword: '',
  category: '',
})

const categoryTagType: Record<string, string> = {
  政治: 'danger',
  暴力: 'danger',
  色情: 'warning',
  广告: 'warning',
  侮辱: 'info',
  其他: 'info',
}
const levelLabel = (lv: number) => SENSITIVE_LEVELS.find((x) => x.value === lv)?.label ?? lv
const actionLabel = (a: string) => SENSITIVE_ACTIONS.find((x) => x.value === a)?.label ?? a

async function load() {
  loading.value = true
  try {
    const resp = await adminApi.sensitiveList({
      page: page.value,
      size: size.value,
      keyword: filters.keyword.trim() || undefined,
      category: filters.category || undefined,
    })
    list.value = resp.list ?? []
    total.value = resp.total ?? 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载敏感词失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  load()
}

// 新增 / 编辑 / 批量导入成功后调用：重置可能隐藏新词的筛选并回到第一页，确保刚操作过的词立即可见
function afterMutate() {
  filters.keyword = ''
  filters.category = ''
  page.value = 1
  load()
}

onMounted(load)

// ============ 新增 ============
const addVisible = ref(false)
const addForm = reactive({ word: '', category: '其他', level: 1, action: 'block' })

function openAdd() {
  addForm.word = ''
  addForm.category = '其他'
  addForm.level = 1
  addForm.action = 'block'
  addVisible.value = true
}

async function submitAdd() {
  const word = addForm.word.trim()
  if (!word) {
    ElMessage.warning('请输入敏感词')
    return
  }
  try {
    const res = await adminApi.sensitiveAdd({
      word,
      category: addForm.category,
      level: addForm.level,
      action: addForm.action,
    })
    ElMessage.success(`添加成功（新增 ${res.added ?? 0}${res.skipped ? `，跳过 ${res.skipped}` : ''}）`)
    addVisible.value = false
    afterMutate()
  } catch (e: any) {
    ElMessage.error(e?.message || '添加失败')
  }
}

// ============ 批量导入 ============
const batchVisible = ref(false)
const batchText = ref('')
const batchForm = reactive({ category: '其他', level: 1, action: 'block' })

function openBatch() {
  batchText.value = ''
  batchForm.category = '其他'
  batchForm.level = 1
  batchForm.action = 'block'
  batchVisible.value = true
}

async function submitBatch() {
  const words = batchText.value
    .split(/[\n,，、]/)
    .map((w) => w.trim())
    .filter((w) => w.length > 0)
  if (words.length === 0) {
    ElMessage.warning('请输入至少一个敏感词（每行一个，或用逗号分隔）')
    return
  }
  try {
    const res = await adminApi.sensitiveAdd({
      words,
      category: batchForm.category,
      level: batchForm.level,
      action: batchForm.action,
    })
    ElMessage.success(`导入完成：新增 ${res.added ?? 0}，跳过重复 ${res.skipped ?? 0}`)
    batchVisible.value = false
    afterMutate()
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  }
}

// ============ 编辑 ============
const editVisible = ref(false)
const editId = ref<number | null>(null)
const editForm = reactive({ word: '', category: '其他', level: 1, action: 'block' })

function openEdit(row: SensitiveWordDTO) {
  editId.value = row.id
  editForm.word = row.word
  editForm.category = row.category
  editForm.level = row.level
  editForm.action = row.action
  editVisible.value = true
}

async function submitEdit() {
  if (editId.value == null) return
  const word = editForm.word.trim()
  if (!word) {
    ElMessage.warning('请输入敏感词')
    return
  }
  try {
    await adminApi.sensitiveUpdate(editId.value, {
      word,
      category: editForm.category,
      level: editForm.level,
      action: editForm.action,
    })
    ElMessage.success('保存成功')
    editVisible.value = false
    afterMutate()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  }
}

// ============ 删除 ============
async function remove(row: SensitiveWordDTO) {
  try {
    await ElMessageBox.confirm(`确定删除敏感词「${row.word}」吗？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminApi.sensitiveDelete(row.id)
    ElMessage.success('删除成功')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// ============ 批量操作 ============
const tableRef = ref()
const selection = ref<SensitiveWordDTO[]>([])

function handleSelectionChange(rows: SensitiveWordDTO[]) {
  selection.value = rows
}

const isEnabled = (row: SensitiveWordDTO) => row.enabled === true || row.enabled === 1

async function toggleStatus(row: SensitiveWordDTO) {
  const next = !isEnabled(row)
  try {
    await adminApi.sensitiveBatchStatus([row.id], next)
    ElMessage.success(next ? '已启用' : '已停用')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function batchEnable() {
  if (!selection.value.length) return
  try {
    const res = await adminApi.sensitiveBatchStatus(selection.value.map((r) => r.id), true)
    ElMessage.success(`已启用 ${res.updated} 条`)
    clearSelectionAndReload()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量启用失败')
  }
}

async function batchDisable() {
  if (!selection.value.length) return
  try {
    const res = await adminApi.sensitiveBatchStatus(selection.value.map((r) => r.id), false)
    ElMessage.success(`已停用 ${res.updated} 条`)
    clearSelectionAndReload()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量停用失败')
  }
}

async function batchDelete() {
  if (!selection.value.length) return
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selection.value.length} 条敏感词吗？`, '批量删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    const res = await adminApi.sensitiveBatchDelete(selection.value.map((r) => r.id))
    ElMessage.success(`已删除 ${res.deleted} 条`)
    clearSelectionAndReload()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量删除失败')
  }
}

function clearSelectionAndReload() {
  tableRef.value?.clearSelection()
  selection.value = []
  load()
}

// ============ 测试面板 ============
const testVisible = ref(false)
const testText = ref('')
const testHits = ref<SensitiveTestHit[]>([])

function openTest() {
  testText.value = ''
  testHits.value = []
  testVisible.value = true
}

async function runTest() {
  if (!testText.value.trim()) {
    ElMessage.warning('请输入测试文本')
    return
  }
  try {
    testHits.value = await adminApi.sensitiveTest(testText.value)
  } catch (e: any) {
    ElMessage.error(e?.message || '测试失败')
  }
}

function escapeHtml(s: string) {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

const highlightedHtml = computed(() => {
  if (!testText.value) return ''
  const hits = [...testHits.value].sort((a, b) => a.start - b.start)
  let html = ''
  let cursor = 0
  for (const h of hits) {
    if (h.start < cursor) continue
    html += escapeHtml(testText.value.slice(cursor, h.start))
    html += `<mark class="hit-mark">${escapeHtml(testText.value.slice(h.start, h.end + 1))}</mark>`
    cursor = h.end + 1
  }
  html += escapeHtml(testText.value.slice(cursor))
  return html
})
</script>

<template>
  <div class="sw-page">
    <div class="page-head">
      <div>
        <h1 class="page-title">敏感词管理</h1>
        <p class="page-sub">基于 DFA 实时检测，支持分类 / 分级 / 词级处置策略与命中统计。</p>
      </div>
      <div class="head-actions">
        <button class="btn-ghost" @click="openTest">
          <span class="material-symbols-outlined">fact_check</span> 测试面板
        </button>
        <button class="btn-ghost" @click="openBatch">
          <span class="material-symbols-outlined">playlist_add</span> 批量导入
        </button>
        <button class="btn-primary" @click="openAdd">
          <span class="material-symbols-outlined">add</span> 新增敏感词
        </button>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-input v-model="filters.keyword" placeholder="搜索敏感词 / 分类" clearable class="filter-search"
                @keyup.enter="onSearch" @clear="onSearch" />
      <el-select v-model="filters.category" placeholder="全部分类" class="filter-select" @change="onSearch">
        <el-option label="全部" value="" />
        <el-option v-for="c in SENSITIVE_CATEGORIES" :key="c" :label="c" :value="c" />
      </el-select>
      <button class="btn-primary sm" @click="onSearch">查询</button>
    </div>

    <!-- 批量操作栏 -->
    <div v-if="selection.length" class="batch-bar">
      <span class="batch-count">已选 {{ selection.length }} 项</span>
      <div class="batch-actions">
        <button class="btn-ghost sm" @click="batchEnable">
          <span class="material-symbols-outlined">check_circle</span> 批量启用
        </button>
        <button class="btn-ghost sm" @click="batchDisable">
          <span class="material-symbols-outlined">do_not_disturb_on</span> 批量停用
        </button>
        <button class="btn-danger sm" @click="batchDelete">
          <span class="material-symbols-outlined">delete_sweep</span> 批量删除
        </button>
        <button class="btn-link sm" @click="tableRef?.clearSelection()">取消选择</button>
      </div>
    </div>

    <!-- 表格 -->
    <el-table ref="tableRef" :data="list" v-loading="loading" class="sw-table" empty-text="暂无敏感词" row-key="id">
      <el-table-column type="selection" width="48" :reserve-selection="true" />
      <el-table-column prop="word" label="敏感词" min-width="140">
        <template #default="{ row }">
          <span class="kw-cell">{{ row.word }}</span>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="100">
        <template #default="{ row }">
          <el-tag :type="(categoryTagType[row.category] as any)" size="small" effect="light">
            {{ row.category }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="级别" width="80">
        <template #default="{ row }">{{ levelLabel(row.level) }}</template>
      </el-table-column>
      <el-table-column label="处置" width="90">
        <template #default="{ row }">
          <el-tag :type="row.action === 'block' ? 'danger' : row.action === 'mask' ? 'warning' : 'info'"
                  size="small" effect="plain">
            {{ actionLabel(row.action) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="92">
        <template #default="{ row }">
          <el-tag :type="isEnabled(row) ? 'success' : 'info'" size="small" effect="light"
                  class="status-tag" title="点击切换启用 / 停用" @click="toggleStatus(row)">
            {{ isEnabled(row) ? '启用中' : '已停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="hitCount" label="命中数" width="90" />
      <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <button class="link-btn" @click="openEdit(row)">编辑</button>
          <button class="link-btn danger" @click="remove(row)">删除</button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        :current-page="page"
        :page-size="size"
        :total="total"
        :page-sizes="[20, 50, 100, 200]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="onPageChange"
        @size-change="onSizeChange" />
    </div>

    <!-- 新增 -->
    <el-dialog v-model="addVisible" title="新增敏感词" width="480px">
      <el-form label-width="80px">
        <el-form-item label="敏感词">
          <el-input v-model="addForm.word" placeholder="如：垃圾广告" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="addForm.category" class="w-full">
            <el-option v-for="c in SENSITIVE_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="addForm.level" class="w-full">
            <el-option v-for="l in SENSITIVE_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置">
          <el-select v-model="addForm.action" class="w-full">
            <el-option v-for="a in SENSITIVE_ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAdd">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量导入 -->
    <el-dialog v-model="batchVisible" title="批量导入敏感词" width="520px">
      <p class="dialog-tip">每行一个，或用逗号 / 顿号分隔。重复词自动跳过。</p>
      <el-input v-model="batchText" type="textarea" :rows="8" placeholder="垃圾广告&#10;暴力&#10;违法信息" />
      <el-form label-width="80px" class="batch-form">
        <el-form-item label="默认分类">
          <el-select v-model="batchForm.category" class="w-full">
            <el-option v-for="c in SENSITIVE_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认级别">
          <el-select v-model="batchForm.level" class="w-full">
            <el-option v-for="l in SENSITIVE_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认处置">
          <el-select v-model="batchForm.action" class="w-full">
            <el-option v-for="a in SENSITIVE_ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" @click="submitBatch">导入</el-button>
      </template>
    </el-dialog>

    <!-- 编辑 -->
    <el-dialog v-model="editVisible" title="编辑敏感词" width="480px">
      <el-form label-width="80px">
        <el-form-item label="敏感词">
          <el-input v-model="editForm.word" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="editForm.category" class="w-full">
            <el-option v-for="c in SENSITIVE_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="editForm.level" class="w-full">
            <el-option v-for="l in SENSITIVE_LEVELS" :key="l.value" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置">
          <el-select v-model="editForm.action" class="w-full">
            <el-option v-for="a in SENSITIVE_ACTIONS" :key="a.value" :label="a.label" :value="a.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 测试面板 -->
    <el-drawer v-model="testVisible" title="敏感词测试" size="480px">
      <el-input v-model="testText" type="textarea" :rows="6" placeholder="粘贴一段文本，实时检测命中的敏感词（不计入命中统计）。" />
      <el-button type="primary" class="test-run" @click="runTest">检测</el-button>
      <div v-if="testHits.length" class="test-result">
        <div class="test-summary">命中 {{ testHits.length }} 处：</div>
        <div class="test-highlight" v-html="highlightedHtml" />
        <ul class="hit-list">
          <li v-for="(h, i) in testHits" :key="i">
            <el-tag size="small" :type="h.action === 'block' ? 'danger' : h.action === 'mask' ? 'warning' : 'info'" effect="plain">
              {{ actionLabel(h.action) }}
            </el-tag>
            <span class="hit-word">{{ h.word }}</span>
            <span class="hit-meta">（{{ h.category }} · {{ levelLabel(h.level) }}）</span>
          </li>
        </ul>
      </div>
      <el-empty v-else-if="testText && !testHits.length" description="未命中任何敏感词" />
    </el-drawer>
  </div>
</template>

<style lang="scss" scoped>
.sw-page {
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
  flex-wrap: wrap;
}
.btn-primary,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: none;
  &.sm { padding: $spacing-xs $spacing-md; }
}
.btn-primary {
  background: $color-brand;
  color: #fff;
}
.btn-ghost {
  background: $color-surface-container-low;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
}
.filter-bar {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: wrap;
  align-items: center;
}
.filter-search { max-width: 280px; }
.filter-select { width: 150px; }
.sw-table {
  border-radius: $radius-md;
  overflow: hidden;
}
.kw-cell {
  font-family: $font-family-headline;
  font-weight: 600;
  color: $color-on-surface;
}
.link-btn {
  background: none;
  border: none;
  color: $color-brand;
  cursor: pointer;
  font-size: $font-size-label-md;
  margin-right: $spacing-md;
  &.danger { color: $color-emergency; }
}
.pager {
  display: flex;
  justify-content: flex-end;
}
.dialog-tip {
  margin-bottom: $spacing-sm;
  color: $color-on-surface-variant;
  font-size: $font-size-label-sm;
}
.batch-form { margin-top: $spacing-md; }
.w-full { width: 100%; }
.test-run { margin: $spacing-md 0; }
.test-summary {
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-sm;
}
.test-highlight {
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  line-height: 1.8;
  font-size: $font-size-body-md;
  white-space: pre-wrap;
  word-break: break-all;
  :deep(.hit-mark) {
    background: rgba(255, 77, 79, 0.22);
    color: $color-emergency;
    border-radius: 3px;
    padding: 0 2px;
  }
}
.hit-list {
  margin-top: $spacing-md;
  padding-left: $spacing-lg;
  li { margin-bottom: $spacing-xs; }
}
.hit-word { font-weight: 700; margin: 0 6px; }
.hit-meta { color: $color-on-surface-variant; font-size: $font-size-label-sm; }
.batch-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
}
.batch-count { font-weight: 700; color: $color-on-surface; }
.batch-actions { display: flex; gap: $spacing-sm; flex-wrap: wrap; }
.btn-danger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: none;
  background: $color-emergency;
  color: #fff;
  &.sm { padding: $spacing-xs $spacing-md; }
}
.btn-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: none;
  background: transparent;
  color: $color-on-surface-variant;
  &.sm { padding: $spacing-xs $spacing-md; }
}
.status-tag {
  cursor: pointer;
  user-select: none;
}
</style>
