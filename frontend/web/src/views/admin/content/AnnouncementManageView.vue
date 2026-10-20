<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'
import { merchantApi } from '@/api/modules/merchant'

interface Announcement {
  id: number
  title: string
  content: string | null
  cover: string | null
  target: string
  status: 0 | 1
  sort: number
  created_at: string
  published_at: string | null
}

const list = ref<Announcement[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const uploading = ref(false)
const form = ref({
  title: '',
  content: '',
  cover: '',
  target: 'all',
  status: 0 as 0 | 1,
  sort: 0,
})

const targetLabel: Record<string, string> = { all: '全部用户', user: '仅用户', merchant: '仅商家' }

async function load() {
  loading.value = true
  try {
    list.value = (await adminApi.announcementList()) as Announcement[]
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  form.value = { title: '', content: '', cover: '', target: 'all', status: 0, sort: 0 }
  dialogVisible.value = true
}

function openEdit(a: Announcement) {
  editingId.value = a.id
  form.value = {
    title: a.title,
    content: a.content || '',
    cover: a.cover || '',
    target: a.target,
    status: a.status,
    sort: a.sort,
  }
  dialogVisible.value = true
}

async function onPickCover(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    const { urls } = await merchantApi.uploadImages([file])
    if (urls.length) {
      form.value.cover = urls[0]
      ElMessage.success('封面已上传')
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
  submitting.value = true
  try {
    const payload: Record<string, any> = {
      title: form.value.title,
      content: form.value.content || undefined,
      cover: form.value.cover || undefined,
      target: form.value.target,
      status: form.value.status,
      sort: Number(form.value.sort) || 0,
    }
    if (editingId.value) await adminApi.updateAnnouncement(editingId.value, payload)
    else await adminApi.createAnnouncement(payload)
    ElMessage.success('已保存')
    dialogVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function onPush(a: Announcement) {
  try {
    await ElMessageBox.confirm(`确认向「${targetLabel[a.target] || a.target}」推送公告「${a.title}」？`, '推送确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminApi.pushAnnouncement(a.id)
    ElMessage.success('已推送')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '推送失败')
  }
}

async function onDelete(a: Announcement) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${a.title}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await adminApi.deleteAnnouncement(a.id)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="ann-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">公告管理</h1>
        <p class="page-sub">发布平台公告并向指定人群推送</p>
      </div>
      <button class="btn-primary" @click="openCreate">
        <span class="material-symbols-outlined">add</span>
        新建公告
      </button>
    </div>

    <section class="card">
      <table v-if="list.length" class="tbl">
        <thead>
          <tr>
            <th style="width: 48px">封面</th>
            <th>标题</th>
            <th style="width: 110px">推送对象</th>
            <th style="width: 90px">状态</th>
            <th style="width: 160px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="a in list" :key="a.id">
            <td>
              <img v-if="a.cover" :src="a.cover" class="thumb" alt="cover"
                   @error="($event.target as HTMLImageElement).style.opacity = '0.2'" />
              <span v-else class="muted">—</span>
            </td>
            <td>
              <div class="title">{{ a.title }}</div>
              <div class="sub">{{ (a.content || '').slice(0, 40) || '无正文' }}</div>
            </td>
            <td><span class="tag">{{ targetLabel[a.target] || a.target }}</span></td>
            <td>
              <span class="badge" :class="a.status === 1 ? 'pub' : 'draft'">
                {{ a.status === 1 ? '已发布' : '草稿' }}
              </span>
            </td>
            <td>
              <button class="btn-text" @click="onPush(a)">推送</button>
              <button class="btn-text" @click="openEdit(a)">编辑</button>
              <button class="btn-text danger" @click="onDelete(a)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty">暂无公告，点击右上角新建</div>
    </section>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑公告' : '新建公告'" width="560px">
      <div class="form-grid">
        <div class="field full">
          <label class="field-label">标题 <span class="required">*</span></label>
          <input v-model="form.title" class="input" maxlength="160" placeholder="公告标题" />
        </div>
        <div class="field full">
          <label class="field-label">正文</label>
          <textarea v-model="form.content" class="textarea" rows="4" placeholder="公告正文内容"></textarea>
        </div>
        <div class="field full">
          <label class="field-label">封面图</label>
          <div class="upload-row">
            <input type="file" accept="image/*" class="file-input" @change="onPickCover" :disabled="uploading" />
            <span v-if="uploading" class="muted">上传中...</span>
            <button v-if="form.cover" class="btn-text danger" type="button" @click="form.cover = ''">清除</button>
          </div>
          <img v-if="form.cover" :src="form.cover" class="preview" alt="cover"
               @error="($event.target as HTMLImageElement).style.opacity = '0.2'" />
        </div>
        <div class="field">
          <label class="field-label">推送对象</label>
          <select v-model="form.target" class="input">
            <option value="all">全部用户</option>
            <option value="user">仅用户</option>
            <option value="merchant">仅商家</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label">状态</label>
          <select v-model="form.status" class="input">
            <option :value="0">草稿</option>
            <option :value="1">发布</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label">排序</label>
          <input v-model.number="form.sort" type="number" class="input" />
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
.ann-view { display: flex; flex-direction: column; gap: $spacing-md; }
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
.tbl th, .tbl td { text-align: left; padding: $spacing-sm $spacing-md; border-bottom: 1px solid $color-outline-variant; font-size: $font-size-body-md; vertical-align: middle; }
.tbl th { color: $color-on-surface-variant; font-weight: 600; }
.thumb { width: 48px; height: 48px; object-fit: cover; border-radius: $radius-sm; background: $color-surface-container; }
.title { font-weight: 600; color: $color-on-surface; }
.sub { font-size: $font-size-label-sm; color: $color-on-surface-variant; margin-top: 2px; max-width: 360px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.muted { color: $color-on-surface-variant; font-size: $font-size-label-sm; }

.tag { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.badge { display: inline-block; padding: 2px 10px; border-radius: $radius-full; font-size: $font-size-label-sm; }
.badge.pub { background: rgba(46, 160, 67, 0.14); color: $color-success; }
.badge.draft { background: rgba(120, 120, 120, 0.14); color: $color-on-surface-variant; }

.btn-text { background: none; border: none; color: $color-brand; cursor: pointer; font-size: $font-size-body-md; padding: 0 6px; }
.btn-text.danger { color: $color-emergency; }
.empty { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }

.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: $spacing-md; }
.field { display: flex; flex-direction: column; gap: 4px; }
.field.full { grid-column: 1 / -1; }
.field-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.required { color: $color-emergency; }
.input, .textarea {
  padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-default;
  background: #fff; font-size: $font-size-body-md; color: $color-on-surface; font-family: inherit;
  &:focus { outline: none; border-color: $color-brand; }
}
.textarea { resize: vertical; min-height: 96px; }
.upload-row { display: flex; align-items: center; gap: $spacing-sm; }
.preview { margin-top: $spacing-sm; max-width: 200px; max-height: 120px; border-radius: $radius-sm; object-fit: cover; }
</style>
