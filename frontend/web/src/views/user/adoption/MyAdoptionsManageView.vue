<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adoptionApi, type Adoption } from '@/api/modules/adoption'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

// ============ 筛选 / 搜索 ============
type StatusFilter = 'all' | '0' | '1' | '2'
type TypeFilter = 'all' | 'dog' | 'cat' | 'other'

const statusFilter = ref<StatusFilter>('all')
const typeFilter = ref<TypeFilter>('all')
const keyword = ref('')

const statusTabs: { id: StatusFilter; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: '1', label: '待领养' },
  { id: '2', label: '已领养' },
  { id: '0', label: '已下架' },
]
const typeTabs: { id: TypeFilter; label: string }[] = [
  { id: 'all', label: '全部' },
  { id: 'dog', label: '狗狗' },
  { id: 'cat', label: '猫咪' },
  { id: 'other', label: '其他' },
]

// ============ 数据 ============
const loading = ref(false)
const list = ref<Adoption[]>([])

function parseImages(images: Adoption['images']): string[] {
  if (!images) return []
  if (Array.isArray(images)) return images
  try {
    const arr = JSON.parse(images as string)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

const statusMeta: Record<number, { label: string; cls: string }> = {
  0: { label: '已下架', cls: 'offline' },
  1: { label: '待领养', cls: 'available' },
  2: { label: '已领养', cls: 'adopted' },
}

const typeLabel: Record<string, string> = { dog: '🐕 狗狗', cat: '🐈 猫咪', other: '🐾 其他' }

function formatTime(v?: string | null): string {
  if (!v) return '—'
  return v.replace('T', ' ')
}

const cards = computed(() =>
  list.value.map((a) => {
    const imgs = parseImages(a.images)
    return {
      raw: a,
      id: a.id,
      title: a.title,
      petType: a.petType,
      typeText: typeLabel[a.petType] || '🐾 其他',
      cover: imgs[0] || '',
      breed: a.breed || '',
      age: a.age || '',
      city: a.city || '',
      status: a.status,
      createdAt: formatTime((a as any).createdAt),
    }
  }),
)

let debounceTimer: any = null
function scheduleLoad() {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(load, 300)
}

watch([statusFilter, typeFilter], load)
watch(keyword, scheduleLoad)

async function load() {
  loading.value = true
  try {
    const params: { status?: string; petType?: string; keyword?: string } = {}
    if (statusFilter.value !== 'all') params.status = statusFilter.value
    if (typeFilter.value !== 'all') params.petType = typeFilter.value
    if (keyword.value.trim()) params.keyword = keyword.value.trim()
    list.value = await adoptionApi.mine(params)
  } catch {
    ElMessage.error('领养数据加载失败')
    list.value = []
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  statusFilter.value = 'all'
  typeFilter.value = 'all'
  keyword.value = ''
}

// ============ 操作：查看详情 / 发布 ============
function goDetail(id: number) {
  router.push(`/adoption/detail/${id}`)
}
function goPublish() {
  router.push('/adoption/publish')
}

// ============ 操作：手动更新状态 ============
async function changeStatus(card: any, status: number) {
  if (card.status === status) return
  try {
    await adoptionApi.setStatusMine(card.id, status)
    card.status = status
    card.raw.status = status
    ElMessage.success(`已更新为「${statusMeta[status].label}」`)
  } catch (err: any) {
    ElMessage.error(err?.message || '状态更新失败')
  }
}

// ============ 操作：删除 ============
async function removeItem(card: any) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${card.title}」这条领养信息吗？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    await adoptionApi.deleteMine(card.id)
    list.value = list.value.filter((x) => x.id !== card.id)
    ElMessage.success('已删除')
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

// ============ 操作：编辑 ============
const editVisible = ref(false)
const editLoading = ref(false)
const editId = ref<number | null>(null)
const editForm = ref({
  title: '',
  petType: 'other' as 'cat' | 'dog' | 'other',
  breed: '',
  gender: 0,
  age: '',
  city: '',
  contact: '',
  healthStatus: '',
  vaccinated: false,
  neutered: false,
  rescueStory: '',
  images: [] as string[],
})

function openEdit(card: any) {
  const a = card.raw
  editId.value = a.id
  editForm.value = {
    title: a.title || '',
    petType: a.petType || 'other',
    breed: a.breed || '',
    gender: a.gender ?? 0,
    age: a.age || '',
    city: a.city || '',
    contact: a.contact || '',
    healthStatus: a.healthStatus || '',
    vaccinated: !!a.vaccinated,
    neutered: !!a.neutered,
    rescueStory: a.rescueStory || '',
    images: parseImages(a.images),
  }
  editVisible.value = true
}

const fileInput = ref<HTMLInputElement | null>(null)
function triggerUpload() {
  fileInput.value?.click()
}
async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files || !input.files.length) return
  const files = Array.from(input.files)
  input.value = ''
  try {
    const res = await adoptionApi.userUpload(files)
    editForm.value.images.push(...res.urls)
    ElMessage.success('图片已添加')
  } catch (err: any) {
    ElMessage.error(err?.message || '图片上传失败')
  }
}
function removeImage(idx: number) {
  editForm.value.images.splice(idx, 1)
}

async function saveEdit() {
  if (!editId.value) return
  if (!editForm.value.title.trim()) {
    ElMessage.warning('请填写宠物名称/标题')
    return
  }
  editLoading.value = true
  try {
    await adoptionApi.updateMine(editId.value, { ...editForm.value })
    ElMessage.success('已保存修改')
    editVisible.value = false
    await load()
  } catch (err: any) {
    ElMessage.error(err?.message || '保存失败')
  } finally {
    editLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="my-adoptions">
    <div class="page-container">
      <!-- 头部 -->
      <header class="head">
        <div>
          <h1 class="title">我的领养管理</h1>
          <p class="subtitle">管理你发布的领养信息，随时更新状态、编辑详情或下架</p>
        </div>
        <button class="publish-btn" @click="goPublish">
          <span class="material-symbols-outlined">add_circle</span>
          发布领养
        </button>
      </header>

      <!-- 筛选 + 搜索 -->
      <div class="filter-bar">
        <div class="seg-group">
          <button
            v-for="t in statusTabs"
            :key="t.id"
            class="seg"
            :class="{ active: statusFilter === t.id }"
            @click="statusFilter = t.id"
          >{{ t.label }}</button>
        </div>
        <div class="seg-group">
          <button
            v-for="t in typeTabs"
            :key="t.id"
            class="seg"
            :class="{ active: typeFilter === t.id }"
            @click="typeFilter = t.id"
          >{{ t.label }}</button>
        </div>
        <div class="search">
          <span class="material-symbols-outlined">search</span>
          <input
            v-model="keyword"
            type="text"
            placeholder="搜索宠物名称 / 品种"
            @keyup.enter="load"
          />
        </div>
        <button class="reset-btn" @click="resetFilters">
          <span class="material-symbols-outlined">restart_alt</span>
          重置
        </button>
      </div>

      <!-- 加载 -->
      <div v-if="loading" class="empty">
        <span class="material-symbols-outlined empty-icon spin">progress_activity</span>
        <p>加载中…</p>
      </div>

      <!-- 列表 -->
      <div v-else-if="cards.length > 0" class="grid">
        <article
          v-for="(c, i) in cards"
          :key="c.id"
          class="card"
          :style="{ animationDelay: (i % 12) * 45 + 'ms' }"
        >
          <div class="cover" @click="goDetail(c.id)">
            <img v-if="c.cover" :src="c.cover" :alt="c.title" @error="(e:any)=>e.target.style.display='none'" />
            <span v-else class="material-symbols-outlined cover-ph">pets</span>
            <span class="status-pill" :class="statusMeta[c.status].cls">
              {{ statusMeta[c.status].label }}
            </span>
            <span class="type-tag">{{ c.typeText }}</span>
          </div>

          <div class="body">
            <h3 class="name" @click="goDetail(c.id)">{{ c.title }}</h3>
            <p class="meta">
              <span>{{ [c.breed, c.age].filter(Boolean).join(' · ') || '—' }}</span>
            </p>
            <p class="time">
              <span class="material-symbols-outlined">schedule</span>
              发布于 {{ c.createdAt }}
            </p>
            <p v-if="c.city" class="city">
              <span class="material-symbols-outlined">location_on</span>
              {{ c.city }}
            </p>

            <!-- 手动更新状态 -->
            <div class="status-edit">
              <span class="se-label">状态</span>
              <div class="se-opts">
                <button
                  v-for="s in [1, 2, 0]"
                  :key="s"
                  class="se-opt"
                  :class="{ active: c.status === s }"
                  @click="changeStatus(c, s)"
                >{{ statusMeta[s].label }}</button>
              </div>
            </div>

            <!-- 操作 -->
            <div class="actions">
              <button class="act" @click="goDetail(c.id)">
                <span class="material-symbols-outlined">visibility</span>详情
              </button>
              <button class="act" @click="openEdit(c)">
                <span class="material-symbols-outlined">edit</span>编辑
              </button>
              <button class="act danger" @click="removeItem(c)">
                <span class="material-symbols-outlined">delete</span>删除
              </button>
            </div>
          </div>
        </article>
      </div>

      <!-- 空状态 -->
      <PetEmptyState v-else icon="pets" title="你还没有发布领养信息">
        <template #action>
          <button class="publish-btn" @click="goPublish">去发布一只</button>
        </template>
      </PetEmptyState>
    </div>

    <!-- 编辑弹窗 -->
    <el-dialog
      v-model="editVisible"
      title="编辑领养信息"
      width="560px"
      class="edit-dialog"
      :close-on-click-modal="false"
    >
      <div v-if="editVisible" class="edit-form">
        <label class="fld">
          <span class="lab">宠物名称 / 标题 <em>*</em></span>
          <input v-model="editForm.title" type="text" placeholder="如：两个月银渐层妹妹" />
        </label>

        <div class="row">
          <label class="fld">
            <span class="lab">类型</span>
            <select v-model="editForm.petType">
              <option value="dog">狗狗</option>
              <option value="cat">猫咪</option>
              <option value="other">其他</option>
            </select>
          </label>
          <label class="fld">
            <span class="lab">性别</span>
            <select v-model.number="editForm.gender">
              <option :value="0">未知</option>
              <option :value="1">男</option>
              <option :value="2">女</option>
            </select>
          </label>
        </div>

        <div class="row">
          <label class="fld">
            <span class="lab">品种</span>
            <input v-model="editForm.breed" type="text" placeholder="如：银渐层" />
          </label>
          <label class="fld">
            <span class="lab">年龄</span>
            <input v-model="editForm.age" type="text" placeholder="如：2个月" />
          </label>
        </div>

        <div class="row">
          <label class="fld">
            <span class="lab">城市</span>
            <input v-model="editForm.city" type="text" placeholder="如：杭州市" />
          </label>
          <label class="fld">
            <span class="lab">联系方式</span>
            <input v-model="editForm.contact" type="text" placeholder="手机号等" />
          </label>
        </div>

        <label class="fld">
          <span class="lab">健康状况</span>
          <input v-model="editForm.healthStatus" type="text" placeholder="如：已疫苗、驱虫" />
        </label>

        <div class="switches">
          <label class="sw">
            <input v-model="editForm.vaccinated" type="checkbox" />
            <span>已疫苗</span>
          </label>
          <label class="sw">
            <input v-model="editForm.neutered" type="checkbox" />
            <span>已绝育</span>
          </label>
        </div>

        <label class="fld">
          <span class="lab">救助故事 / 描述</span>
          <textarea v-model="editForm.rescueStory" rows="3" placeholder="介绍它的性格、来历…"></textarea>
        </label>

        <div class="fld">
          <span class="lab">照片（{{ editForm.images.length }}）</span>
          <div class="img-list">
            <div v-for="(u, idx) in editForm.images" :key="idx" class="img-item">
              <img :src="u" :alt="`img-${idx}`" @error="(e:any)=>e.target.style.display='none'" />
              <button type="button" class="img-del" @click="removeImage(idx)">
                <span class="material-symbols-outlined">close</span>
              </button>
            </div>
            <button type="button" class="img-add" @click="triggerUpload">
              <span class="material-symbols-outlined">add_a_photo</span>
              <span>添加</span>
            </button>
          </div>
          <input ref="fileInput" type="file" accept="image/*" multiple hidden @change="onFileChange" />
        </div>
      </div>

      <template #footer>
        <button class="dlg-cancel" @click="editVisible = false">取消</button>
        <button class="dlg-save" :disabled="editLoading" @click="saveEdit">
          {{ editLoading ? '保存中…' : '保存修改' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.my-adoptions {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: $spacing-2xl;
}

.page-container {
  max-width: $width-content-max;
  margin: 0 auto;
  padding: $spacing-xl $width-gutter-mobile 0;

  @include desktop {
    padding: $spacing-xl $width-margin-desktop 0;
  }
}

// ====== Head ======
.head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
}

.title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: $font-weight-extrabold;
  color: $color-on-surface;
  margin: 0 0 4px;
}

.subtitle {
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  margin: 0;
}

.publish-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 10px 22px;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  font-family: inherit;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba($color-brand, 0.25);
  transition: background $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    color: $color-on-brand;
  }
  &:hover {
    background: $color-brand-dark;
    transform: translateY(-2px);
  }
}

// ====== Filter ======
.filter-bar {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  background: $color-surface-container-lowest;
  padding: $spacing-sm;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
}

.seg-group {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.seg {
  padding: 7px 16px;
  background: transparent;
  border: none;
  border-radius: $radius-full;
  color: $color-text-secondary;
  font-size: $font-size-label-md;
  font-weight: $font-weight-medium;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: $color-surface-container-low;
  }
  &.active {
    background: $color-brand;
    color: $color-on-brand;
    font-weight: $font-weight-semibold;
  }
}

.search {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 180px;
  padding: 7px 14px;
  background: $color-surface-container-low;
  border-radius: $radius-full;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-text-tertiary;
  }
  input {
    flex: 1;
    border: none;
    background: transparent;
    outline: none;
    font-size: $font-size-label-md;
    color: $color-on-surface;
    font-family: inherit;

    &::placeholder {
      color: $color-text-tertiary;
    }
  }
}

.reset-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 14px;
  background: $color-surface-container-low;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;

  .material-symbols-outlined {
    font-size: 18px;
  }
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

// ====== Grid ======
.grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;

  @include tablet {
    grid-template-columns: repeat(2, 1fr);
  }
  @include desktop {
    grid-template-columns: repeat(3, 1fr);
  }
}

.card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-card;
  display: flex;
  flex-direction: column;
  transition: transform $transition-fast, box-shadow $transition-fast;
  animation: cardFadeUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-lg;
  }
  &:hover .cover img {
    transform: scale(1.06);
  }
}

@keyframes cardFadeUp {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.cover {
  position: relative;
  aspect-ratio: 16 / 10;
  background: $color-surface-container;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.55s cubic-bezier(0.16, 1, 0.3, 1);
  }
}

.cover-ph {
  font-size: 56px;
  color: $color-on-surface-variant;
}

.status-pill {
  position: absolute;
  top: $spacing-sm;
  left: $spacing-sm;
  padding: 4px 12px;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: #fff;
}
.status-pill.available {
  background: $color-success;
}
.status-pill.adopted {
  background: $color-brand;
}
.status-pill.offline {
  background: $color-text-tertiary;
}

.type-tag {
  position: absolute;
  top: $spacing-sm;
  right: $spacing-sm;
  padding: 4px 10px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
}

.body {
  padding: $spacing-md;
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
  cursor: pointer;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
  line-height: $line-height-tight;
  min-height: 2.4em;

  &:hover {
    color: $color-brand;
  }
}

.meta {
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  margin: 0;
}

.time,
.city {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 0;

  .material-symbols-outlined {
    font-size: 15px;
  }
}

// ====== 状态编辑 ======
.status-edit {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  padding-top: $spacing-sm;
  border-top: 1px dashed $color-outline-variant;
}

.se-label {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  flex-shrink: 0;
}

.se-opts {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.se-opt {
  padding: 4px 12px;
  background: $color-surface-container-low;
  border: 1px solid transparent;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-family: inherit;
  color: $color-text-secondary;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-outline-variant;
  }
  &.active {
    background: rgba($color-brand, 0.12);
    border-color: $color-brand;
    color: $color-brand-dark;
    font-weight: $font-weight-semibold;
  }
}

// ====== 操作 ======
.actions {
  display: flex;
  gap: 6px;
  margin-top: $spacing-sm;
}

.act {
  flex: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 8px 0;
  background: $color-surface-container-low;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  font-size: $font-size-label-sm;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 16px;
  }
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
  &.danger:hover {
    border-color: $color-emergency;
    color: $color-emergency;
  }
}

// ====== Empty ======
.empty {
  text-align: center;
  padding: $spacing-3xl 0;
}
.empty-icon {
  font-size: 64px !important;
  color: $color-text-tertiary;
  margin-bottom: $spacing-sm;
}
.empty p {
  font-size: $font-size-body-md;
  color: $color-text-secondary;
  margin: 0 0 $spacing-md;
}
.spin {
  animation: state-spin 1s linear infinite;
}
@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}

// ====== 编辑弹窗 ======
.edit-dialog :deep(.el-dialog__body) {
  padding-top: $spacing-md;
}

.edit-form {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.fld {
  display: flex;
  flex-direction: column;
  gap: 4px;

  .lab {
    font-size: $font-size-label-sm;
    color: $color-text-secondary;
    font-weight: $font-weight-medium;

    em {
      color: $color-emergency;
      font-style: normal;
    }
  }

  input,
  select,
  textarea {
    padding: 9px 12px;
    border: 1px solid $color-outline-variant;
    border-radius: $radius-default;
    background: $color-surface-container-lowest;
    font-size: $font-size-label-md;
    color: $color-on-surface;
    font-family: inherit;
    outline: none;
    transition: border-color $transition-fast;

    &:focus {
      border-color: $color-brand;
    }
  }
  textarea {
    resize: vertical;
  }
}

.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-sm;
}

.switches {
  display: flex;
  gap: $spacing-lg;
  padding: 4px 0;
}
.sw {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: $font-size-label-md;
  color: $color-on-surface;
  cursor: pointer;

  input {
    width: 16px;
    height: 16px;
    accent-color: $color-brand;
  }
}

.img-list {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}
.img-item {
  position: relative;
  width: 76px;
  height: 76px;
  border-radius: $radius-default;
  overflow: hidden;
  background: $color-surface-container;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}
.img-del {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 22px;
  height: 22px;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;

  .material-symbols-outlined {
    font-size: 15px;
  }
}
.img-add {
  width: 76px;
  height: 76px;
  border: 1px dashed $color-outline-variant;
  border-radius: $radius-default;
  background: $color-surface-container-lowest;
  color: $color-text-secondary;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: $font-size-label-sm;

  .material-symbols-outlined {
    font-size: 24px;
  }
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

.edit-dialog :deep(.el-dialog__footer) {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
}
.dlg-cancel {
  padding: 9px 20px;
  background: $color-surface-container-low;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;
}
.dlg-save {
  padding: 9px 22px;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  font-family: inherit;
  cursor: pointer;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

@media (prefers-reduced-motion: reduce) {
  .card {
    animation: none;
  }
  .cover img,
  .card:hover .cover img {
    transition: none;
    transform: none;
  }
}
</style>
