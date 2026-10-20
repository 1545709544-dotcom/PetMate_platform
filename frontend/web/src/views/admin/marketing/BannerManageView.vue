<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'
import { merchantApi } from '@/api/modules/merchant'

interface Banner {
  id: number
  title: string
  image_url: string
  link_url: string | null
  sort: number
  status: 0 | 1
  start_time: string | null
  end_time: string | null
}

const list = ref<Banner[]>([])
const loading = ref(false)

// 弹窗
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const uploading = ref(false)
const form = ref({
  title: '',
  image_url: '',
  link_url: '',
  sort: 0,
  status: 1 as 0 | 1,
  startTime: '',
  endTime: '',
})

async function load() {
  loading.value = true
  try {
    list.value = (await adminApi.bannerList()) as Banner[]
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { title: '', image_url: '', link_url: '', sort: 0, status: 1, startTime: '', endTime: '' }
  dialogVisible.value = true
}

function openEdit(b: Banner) {
  editingId.value = b.id
  form.value = {
    title: b.title,
    image_url: b.image_url,
    link_url: b.link_url || '',
    sort: b.sort,
    status: b.status,
    startTime: (b.start_time || '').slice(0, 16),
    endTime: (b.end_time || '').slice(0, 16),
  }
  dialogVisible.value = true
}

async function onPickImage(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const { urls } = await merchantApi.uploadImages([file])
    if (urls.length) {
      form.value.image_url = urls[0]
      ElMessage.success('图片已上传')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function submit() {
  if (!form.value.title.trim()) return ElMessage.warning('请填写标题')
  if (!form.value.image_url) return ElMessage.warning('请上传 Banner 图片')
  submitting.value = true
  try {
    const payload: Record<string, any> = {
      title: form.value.title,
      image_url: form.value.image_url,
      link_url: form.value.link_url || undefined,
      sort: Number(form.value.sort) || 0,
      status: form.value.status,
      startTime: form.value.startTime || undefined,
      endTime: form.value.endTime || undefined,
    }
    if (editingId.value) await adminApi.updateBanner(editingId.value, payload)
    else await adminApi.createBanner(payload)
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(b: Banner) {
  const next = b.status === 1 ? 0 : 1
  try {
    await adminApi.toggleBannerStatus(b.id, next)
    b.status = next as 0 | 1
    ElMessage.success(next === 1 ? '已上线' : '已下线')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function onDelete(b: Banner) {
  try {
    await ElMessageBox.confirm(`确认删除 Banner「${b.title}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminApi.deleteBanner(b.id)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="banner-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">轮播管理</h1>
        <p class="page-sub">配置首页 / 运营位轮播 Banner</p>
      </div>
      <button class="btn-primary" @click="openCreate">
        <span class="material-symbols-outlined">add</span>
        新建 Banner
      </button>
    </div>

    <section class="card">
      <table v-if="list.length" class="tbl">
        <thead>
          <tr>
            <th style="width: 96px">预览</th>
            <th>标题</th>
            <th>跳转链接</th>
            <th style="width: 90px">排序</th>
            <th style="width: 90px">状态</th>
            <th style="width: 160px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in list" :key="b.id">
            <td>
              <img v-if="b.image_url" :src="b.image_url" class="thumb" alt="banner"
                   @error="($event.target as HTMLImageElement).style.opacity = '0.2'" />
              <span v-else class="muted">无图</span>
            </td>
            <td>{{ b.title }}</td>
            <td class="link-cell">{{ b.link_url || '—' }}</td>
            <td>{{ b.sort }}</td>
            <td>
              <button class="switch" :class="{ on: b.status === 1 }" @click="toggleStatus(b)">
                <span class="dot"></span>{{ b.status === 1 ? '上线' : '下线' }}
              </button>
            </td>
            <td>
              <button class="btn-text" @click="openEdit(b)">编辑</button>
              <button class="btn-text danger" @click="onDelete(b)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无 Banner，点击右上角新建</div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑 Banner' : '新建 Banner'" width="560px">
      <div class="form-grid">
        <div class="field full">
          <label class="field-label">标题 <span class="required">*</span></label>
          <input v-model="form.title" class="input" maxlength="120" placeholder="Banner 标题" />
        </div>
        <div class="field full">
          <label class="field-label">封面图 <span class="required">*</span></label>
          <div class="upload-row">
            <input type="file" accept="image/*" class="file-input" @change="onPickImage" :disabled="uploading" />
            <span v-if="uploading" class="muted">上传中...</span>
            <button v-if="form.image_url" class="btn-text danger" type="button" @click="form.image_url = ''">清除</button>
          </div>
          <img v-if="form.image_url" :src="form.image_url" class="preview" alt="preview"
               @error="($event.target as HTMLImageElement).style.opacity = '0.2'" />
        </div>
        <div class="field full">
          <label class="field-label">跳转链接</label>
          <input v-model="form.link_url" class="input" placeholder="点击 Banner 后跳转的 URL（可空）" />
        </div>
        <div class="field">
          <label class="field-label">排序</label>
          <input v-model.number="form.sort" type="number" class="input" />
        </div>
        <div class="field">
          <label class="field-label">状态</label>
          <select v-model="form.status" class="input">
            <option :value="1">上线</option>
            <option :value="0">下线</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label">生效开始</label>
          <input v-model="form.startTime" type="datetime-local" class="input" />
        </div>
        <div class="field">
          <label class="field-label">生效结束</label>
          <input v-model="form.endTime" type="datetime-local" class="input" />
        </div>
      </div>
      <template #footer>
        <button class="btn-outline" @click="dialogVisible = false">取消</button>
        <button class="btn-primary" :disabled="submitting" @click="submit">
          {{ submitting ? '保存中...' : '保存' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.banner-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: $spacing-md; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }

.btn-primary, .btn-outline {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md; font-weight: 700; cursor: pointer; border: 1px solid transparent;
}
.btn-primary { background: $color-brand; color: #fff; &:disabled { opacity: 0.6; } }
.btn-outline { background: transparent; color: $color-brand; border-color: $color-brand; }

.card { padding: $spacing-lg; background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; }
.tbl { width: 100%; border-collapse: collapse; }
.tbl th, .tbl td { text-align: left; padding: $spacing-sm $spacing-md; border-bottom: 1px solid $color-outline-variant; font-size: $font-size-body-md; }
.tbl th { color: $color-on-surface-variant; font-weight: 600; }
.thumb { width: 80px; height: 48px; object-fit: cover; border-radius: $radius-sm; background: $color-surface-container; }
.link-cell { color: $color-on-surface-variant; max-width: 280px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.muted { color: $color-on-surface-variant; font-size: $font-size-label-sm; }

.switch {
  display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: $radius-full;
  border: 1px solid $color-outline-variant; background: transparent; cursor: pointer; font-size: $font-size-label-sm; color: $color-on-surface-variant;
  .dot { width: 8px; height: 8px; border-radius: 50%; background: $color-on-surface-variant; }
  &.on { color: $color-success; border-color: $color-success; .dot { background: $color-success; } }
}

.btn-text { background: none; border: none; color: $color-brand; cursor: pointer; font-size: $font-size-body-md; padding: 0 6px; }
.btn-text.danger { color: $color-emergency; }
.empty { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: $spacing-md; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field.full { grid-column: 1 / -1; }
.field-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.required { color: $color-emergency; }
.input {
  padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-default;
  background: #fff; font-size: $font-size-body-md; color: $color-on-surface; font-family: inherit;
  &:focus { outline: none; border-color: $color-brand; }
}
.upload-row { display: flex; align-items: center; gap: $spacing-sm; }
.preview { margin-top: $spacing-sm; max-width: 240px; max-height: 120px; border-radius: $radius-sm; object-fit: cover; }
</style>
