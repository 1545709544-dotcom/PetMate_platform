<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  adoptionApi,
  type AdminAdoption,
  type AdoptionApplicationItem,
} from '@/api/modules/adoption'

const activeTab = ref<'info' | 'apply'>('info')

// ===================== 领养信息管理 =====================
const infoLoading = ref(false)
const adoptions = ref<AdminAdoption[]>([])
const infoKeyword = ref('')
const infoType = ref('')
const infoStatus = ref<'' | number>('')

async function loadInfo() {
  infoLoading.value = true
  try {
    const params: Record<string, any> = {}
    if (infoType.value) params.petType = infoType.value
    if (infoStatus.value !== '') params.status = infoStatus.value
    if (infoKeyword.value.trim()) params.keyword = infoKeyword.value.trim()
    adoptions.value = await adoptionApi.adminList(params)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    infoLoading.value = false
  }
}

function parseCover(images: any): string {
  if (!images) return ''
  let arr: string[] = []
  if (Array.isArray(images)) arr = images
  else {
    try {
      const p = JSON.parse(images)
      if (Array.isArray(p)) arr = p
    } catch {
      /* ignore */
    }
  }
  return arr[0] || ''
}

const typeLabel = (t: string) => ({ cat: '猫咪', dog: '狗狗', other: '其他' }[t] || '其他')
const statusInfo = (s: number) =>
  s === 1 ? { text: '上架', type: 'success' as const } : { text: '下架', type: 'info' as const }

// ---- 发布 / 编辑弹窗 ----
const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const submitting = ref(false)
const form = reactive({
  title: '',
  petType: 'cat',
  breed: '',
  gender: 1,
  age: '',
  city: '',
  contact: '',
  vaccinated: false,
  neutered: false,
  healthStatus: '',
  rescueStory: '',
  status: 1,
  images: [] as string[],
})
const imageInput = ref<HTMLInputElement | null>(null)

function resetForm() {
  Object.assign(form, {
    title: '',
    petType: 'cat',
    breed: '',
    gender: 1,
    age: '',
    city: '',
    contact: '',
    vaccinated: false,
    neutered: false,
    healthStatus: '',
    rescueStory: '',
    status: 1,
    images: [],
  })
}

function openCreate() {
  dialogMode.value = 'create'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(a: AdminAdoption) {
  dialogMode.value = 'edit'
  editingId.value = a.id
  resetForm()
  Object.assign(form, {
    title: a.title,
    petType: a.petType,
    breed: a.breed || '',
    gender: a.gender,
    age: a.age || '',
    city: a.city || '',
    status: a.status,
  })
  dialogVisible.value = true
  // 拉详情补全图片/描述字段
  try {
    const d = await adoptionApi.detail(a.id)
    form.images = (() => {
      try {
        const p = typeof d.images === 'string' ? JSON.parse(d.images) : d.images
        return Array.isArray(p) ? p : []
      } catch {
        return []
      }
    })()
    form.vaccinated = !!d.vaccinated
    form.neutered = !!d.neutered
    form.healthStatus = d.healthStatus || ''
    form.rescueStory = d.rescueStory || ''
    form.contact = d.contact || ''
  } catch {
    /* ignore */
  }
}

async function onPickImages(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (!files || !files.length) return
  try {
    const res = await adoptionApi.adminUpload(Array.from(files))
    form.images.push(...res.urls)
    ElMessage.success(`已上传 ${res.urls.length} 张`)
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  } finally {
    ;(e.target as HTMLInputElement).value = ''
  }
}

function removeImage(idx: number) {
  form.images.splice(idx, 1)
}

async function submitForm() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写宠物名称/标题')
    return
  }
  submitting.value = true
  const payload = {
    title: form.title.trim(),
    petType: form.petType,
    breed: form.breed || undefined,
    gender: form.gender,
    age: form.age || undefined,
    city: form.city || undefined,
    contact: form.contact || undefined,
    vaccinated: form.vaccinated,
    neutered: form.neutered,
    healthStatus: form.healthStatus || undefined,
    rescueStory: form.rescueStory || undefined,
    status: form.status,
    images: form.images,
  }
  try {
    if (dialogMode.value === 'create') await adoptionApi.adminCreate(payload)
    else await adoptionApi.adminUpdate(editingId.value!, payload)
    ElMessage.success(dialogMode.value === 'create' ? '发布成功' : '保存成功')
    dialogVisible.value = false
    loadInfo()
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function toggleShelf(a: AdminAdoption) {
  const next = a.status === 1 ? 0 : 1
  try {
    await adoptionApi.adminSetStatus(a.id, next)
    a.status = next
    ElMessage.success(next === 1 ? '已上架' : '已下架')
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

async function removeAdoption(a: AdminAdoption) {
  try {
    await ElMessageBox.confirm(`确认删除「${a.title}」？此操作不可恢复。`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adoptionApi.adminDelete(a.id)
    ElMessage.success('已删除')
    loadInfo()
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

// ===================== 申请审核 =====================
const applyLoading = ref(false)
const applications = ref<AdoptionApplicationItem[]>([])
const applyStatus = ref<'' | number>('')

async function loadApplications() {
  applyLoading.value = true
  try {
    const params: Record<string, any> = {}
    if (applyStatus.value !== '') params.status = applyStatus.value
    applications.value = await adoptionApi.adminApplications(params)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    applyLoading.value = false
  }
}

const appStatusInfo = (s: number) =>
  s === 0
    ? { text: '待审核', type: 'warning' as const }
    : s === 1
      ? { text: '已通过', type: 'success' as const }
      : { text: '已驳回', type: 'danger' as const }

async function reviewApp(item: AdoptionApplicationItem, approved: boolean) {
  let reason: string | undefined
  if (!approved) {
    try {
      const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回申请', {
        inputPlaceholder: '原因（可选）',
      })
      reason = value || undefined
    } catch {
      return
    }
  }
  try {
    await adoptionApi.adminReview(item.id, approved, reason)
    item.status = approved ? 1 : 2
    ElMessage.success(approved ? '已通过' : '已驳回')
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  }
}

onMounted(() => {
  loadInfo()
  loadApplications()
})
</script>

<template>
  <div class="adopt-admin">
    <header class="page-head">
      <h2 class="page-title">领养救助管理</h2>
      <p class="page-desc">发布 / 编辑待领养宠物信息，并审核用户的领养申请。</p>
    </header>

    <!-- Tabs -->
    <div class="tabs">
      <button class="tab" :class="{ active: activeTab === 'info' }" @click="activeTab = 'info'">
        领养信息
      </button>
      <button class="tab" :class="{ active: activeTab === 'apply' }" @click="activeTab = 'apply'">
        申请审核
      </button>
    </div>

    <!-- ============ Tab 1：领养信息 ============ -->
    <section v-show="activeTab === 'info'" class="panel">
      <div class="toolbar">
        <el-input v-model="infoKeyword" placeholder="搜索名称" clearable class="kw" @clear="loadInfo" @keyup.enter="loadInfo" />
        <el-select v-model="infoType" placeholder="类型" clearable class="sel" @change="loadInfo">
          <el-option label="全部类型" value="" />
          <el-option label="猫咪" value="cat" />
          <el-option label="狗狗" value="dog" />
          <el-option label="其他" value="other" />
        </el-select>
        <el-select v-model="infoStatus" placeholder="状态" clearable class="sel" @change="loadInfo">
          <el-option label="全部状态" :value="'' as any" />
          <el-option label="上架" :value="1" />
          <el-option label="下架" :value="0" />
        </el-select>
        <el-button type="primary" @click="openCreate">发布新领养</el-button>
        <el-button @click="loadInfo">刷新</el-button>
      </div>

      <el-table :data="adoptions" v-loading="infoLoading" class="tbl" empty-text="暂无领养信息">
        <el-table-column label="封面" width="90">
          <template #default="{ row }">
            <img v-if="parseCover(row.images)" :src="parseCover(row.images)" class="cell-cover" @error="(e:any)=>e.target.style.display='none'" />
            <span v-else class="material-symbols-outlined cell-ph">pets</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="名称" min-width="160" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ typeLabel(row.petType) }}</template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="90" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type" size="small">{{ statusInfo(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="toggleShelf(row)">
              {{ row.status === 1 ? '下架' : '上架' }}
            </el-button>
            <el-button link type="danger" @click="removeAdoption(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ Tab 2：申请审核 ============ -->
    <section v-show="activeTab === 'apply'" class="panel">
      <div class="toolbar">
        <el-select v-model="applyStatus" placeholder="审核状态" clearable class="sel" @change="loadApplications">
          <el-option label="全部状态" :value="'' as any" />
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已驳回" :value="2" />
        </el-select>
        <el-button @click="loadApplications">刷新</el-button>
      </div>

      <el-table :data="applications" v-loading="applyLoading" class="tbl" empty-text="暂无领养申请">
        <el-table-column label="宠物" min-width="160">
          <template #default="{ row }">
            <div class="pet-cell">
              <img v-if="parseCover(row.images)" :src="parseCover(row.images)" class="cell-cover sm" @error="(e:any)=>e.target.style.display='none'" />
              <span v-else class="material-symbols-outlined cell-ph sm">pets</span>
              <span class="pet-name">{{ row.adoptionTitle || ('#' + row.adoptionId) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="申请人" width="110" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="reason" label="领养理由" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="appStatusInfo(row.status).type" size="small">{{ appStatusInfo(row.status).text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="申请时间" width="160" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0" link type="success" @click="reviewApp(row, true)">通过</el-button>
            <el-button v-if="row.status === 0" link type="danger" @click="reviewApp(row, false)">驳回</el-button>
            <span v-if="row.status !== 0" class="done-tip">已处理</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- ============ 发布 / 编辑弹窗 ============ -->
    <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '发布新领养' : '编辑领养信息'" width="640px">
      <el-form label-width="90px" class="form">
        <el-form-item label="名称/标题" required>
          <el-input v-model="form.title" placeholder="如：橘猫「豆豆」寻找温暖的家" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.petType">
            <el-option label="猫咪" value="cat" />
            <el-option label="狗狗" value="dog" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="品种">
          <el-input v-model="form.breed" placeholder="如：中华田园橘猫" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">公</el-radio>
            <el-radio :value="2">母</el-radio>
            <el-radio :value="0">未知</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄">
          <el-input v-model="form.age" placeholder="如：约 1 岁" />
        </el-form-item>
        <el-form-item label="城市">
          <el-input v-model="form.city" placeholder="如：北京" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contact" placeholder="救助站/联系人电话" />
        </el-form-item>
        <el-form-item label="健康">
          <el-checkbox v-model="form.vaccinated">已疫苗</el-checkbox>
          <el-checkbox v-model="form.neutered">已绝育</el-checkbox>
        </el-form-item>
        <el-form-item label="健康状况">
          <el-input v-model="form.healthStatus" placeholder="如：健康，已绝育" />
        </el-form-item>
        <el-form-item label="救助故事">
          <el-input v-model="form.rescueStory" type="textarea" :rows="3" placeholder="简单描述救助经历" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">上架（可被领养）</el-radio>
            <el-radio :value="0">下架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="图片">
          <div class="img-zone">
            <div v-for="(u, i) in form.images" :key="i" class="img-thumb">
              <img :src="u" @error="(e:any)=>e.target.style.display='none'" />
              <button class="img-del" @click="removeImage(i)">
                <span class="material-symbols-outlined">close</span>
              </button>
            </div>
            <button class="img-add" @click="imageInput?.click()">
              <span class="material-symbols-outlined">add_a_photo</span>
              <span>上传</span>
            </button>
            <input ref="imageInput" type="file" accept="image/*" multiple hidden @change="onPickImages" />
          </div>
          <p class="img-tip">支持多图，第一张作为封面。</p>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.adopt-admin {
  padding: $spacing-lg;
  max-width: 1100px;
  margin: 0 auto;
}

.page-head {
  margin-bottom: $spacing-md;
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0 0 $spacing-xs;
}
.page-desc {
  margin: 0;
  color: $color-text-secondary;
  font-size: $font-size-label-md;
}

.tabs {
  display: flex;
  gap: 4px;
  margin-bottom: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
}
.tab {
  padding: 10px 20px;
  background: transparent;
  border: none;
  border-bottom: 2px solid transparent;
  color: $color-text-secondary;
  font-size: $font-size-label-md;
  font-weight: $font-weight-medium;
  font-family: inherit;
  cursor: pointer;
  &.active {
    color: $color-brand;
    border-bottom-color: $color-brand;
    font-weight: $font-weight-semibold;
  }
}

.panel {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-md;
  box-shadow: $shadow-card;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
  .kw {
    width: 200px;
  }
  .sel {
    width: 140px;
  }
}

.tbl {
  width: 100%;
}

.cell-cover {
  width: 60px;
  height: 44px;
  border-radius: $radius-sm;
  object-fit: cover;
  background: $color-surface-container;
  &.sm {
    width: 44px;
    height: 36px;
  }
}
.cell-ph {
  font-size: 28px;
  color: $color-text-tertiary;
  &.sm {
    font-size: 22px;
  }
}
.pet-cell {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  .pet-name {
    font-weight: $font-weight-medium;
    color: $color-on-surface;
  }
}
.done-tip {
  color: $color-text-tertiary;
  font-size: $font-size-label-sm;
}

// 弹窗表单
.form {
  :deep(.el-form-item) {
    margin-bottom: $spacing-md;
  }
}
.img-zone {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}
.img-thumb {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: $radius-sm;
  overflow: hidden;
  border: 1px solid $color-outline-variant;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .img-del {
    position: absolute;
    top: 2px;
    right: 2px;
    width: 20px;
    height: 20px;
    border: none;
    border-radius: 50%;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
    .material-symbols-outlined {
      font-size: 14px;
    }
  }
}
.img-add {
  width: 72px;
  height: 72px;
  border: 1px dashed $color-outline;
  border-radius: $radius-sm;
  background: $color-surface-container-low;
  color: $color-text-secondary;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  font-size: $font-size-label-sm;
  font-family: inherit;
  .material-symbols-outlined {
    font-size: 22px;
  }
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}
.img-tip {
  margin: $spacing-xs 0 0;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}
</style>
