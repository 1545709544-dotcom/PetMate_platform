<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'

const photos = ref<string[]>([])
const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)

async function load() {
  loading.value = true
  try {
    const s = await merchantApi.store()
    // photos 字段是 JSON 字符串
    let arr: string[] = []
    if (s.photos) {
      try { arr = typeof s.photos === 'string' ? JSON.parse(s.photos) : s.photos }
      catch { arr = [] }
    }
    photos.value = Array.isArray(arr) ? arr : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function onPickFiles(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return
  uploading.value = true
  try {
    const { urls } = await merchantApi.uploadImages(files)
    const merged = [...photos.value]
    urls.forEach((u) => { if (!merged.includes(u)) merged.push(u) })
    photos.value = merged
    ElMessage.success(`已上传 ${urls.length} 张`)
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

function removePhoto(url: string) {
  photos.value = photos.value.filter((p) => p !== url)
}

async function save() {
  saving.value = true
  try {
    await merchantApi.savePhotos(photos.value)
    ElMessage.success('已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="photos-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">实景相册</h1>
        <p class="page-sub">上传门店照片与视频</p>
      </div>
      <button class="btn-primary" :disabled="saving" @click="save">
        <span class="material-symbols-outlined">save</span>
        {{ saving ? '保存中...' : '保存' }}
      </button>
    </div>

    <section class="card">
      <div class="add-row">
        <label class="btn-outline upload-btn">
          <span class="material-symbols-outlined">add_photo_alternate</span>
          {{ uploading ? '上传中...' : '本地上传' }}
          <input type="file" accept="image/*" multiple class="file-input" :disabled="uploading" @change="onPickFiles" />
        </label>
      </div>

      <div v-if="photos.length" class="grid">
        <div v-for="url in photos" :key="url" class="card-tile">
          <img :src="url" alt="门店照片" @error="($event.target as HTMLImageElement).style.opacity = '0.2'" />
          <button class="remove-btn" @click="removePhoto(url)">
            <span class="material-symbols-outlined">close</span>
          </button>
        </div>
      </div>
      <div v-else class="empty">暂无门店照片</div>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.photos-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }

.btn-primary, .btn-outline {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md; font-weight: 700; cursor: pointer; border: 1px solid transparent;
}
.btn-primary { background: $color-brand; color: #fff; &:disabled { opacity: 0.6; } }
.btn-outline { background: transparent; color: $color-brand; border-color: $color-brand; }

.card { padding: $spacing-lg; background: $color-surface-container-lowest; border-radius: $radius-md; box-shadow: $shadow-sm; }
.add-row { display: flex; align-items: center; gap: $spacing-sm; margin-bottom: $spacing-lg; }
.upload-btn { position: relative; overflow: hidden; }
.upload-btn .material-symbols-outlined { font-size: 18px; }
.file-input { position: absolute; inset: 0; opacity: 0; width: 100%; height: 100%; cursor: pointer; }
.input { flex: 1; padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-default; background: #fff; font-size: $font-size-body-md; }

.grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: $spacing-md; @include tablet { grid-template-columns: repeat(4, 1fr); } @include desktop { grid-template-columns: repeat(5, 1fr); } }
.card-tile { position: relative; aspect-ratio: 1; background: $color-surface-container; border-radius: $radius-md; overflow: hidden; }
.card-tile img { width: 100%; height: 100%; object-fit: cover; }
.remove-btn { position: absolute; top: 4px; right: 4px; width: 28px; height: 28px; border-radius: $radius-full; background: rgba(0,0,0,0.5); color: #fff; border: none; cursor: pointer; display: flex; align-items: center; justify-content: center; }
.remove-btn .material-symbols-outlined { font-size: 16px; }
.empty { padding: $spacing-xl; text-align: center; color: $color-on-surface-variant; }
</style>
