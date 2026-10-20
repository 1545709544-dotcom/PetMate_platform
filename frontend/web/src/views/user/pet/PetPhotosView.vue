<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { petApi } from '@/api/modules/pet'
import type { Pet, PetPhoto } from '@/api/modules/pet'

const route = useRoute()
const router = useRouter()

const petId = computed(() => Number(route.params.id))
const pet = ref<Pet | null>(null)
const photos = ref<PetPhoto[]>([])
const loading = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

async function loadPet() {
  try {
    pet.value = await petApi.detail(petId.value)
  } catch {
    pet.value = null
  }
}

async function loadPhotos() {
  loading.value = true
  try {
    photos.value = (await petApi.photos(petId.value)) || []
  } catch {
    photos.value = []
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push(`/pet/detail/${petId.value}`)
}

function uploadPhoto() {
  fileInput.value?.click()
}

function onPhotosSelected(e: Event) {
  const target = e.target as HTMLInputElement
  const files = target.files ? Array.from(target.files) : []
  target.value = ''
  if (!files.length) return
  doUpload(files)
}

async function doUpload(files: File[]) {
  uploading.value = true
  try {
    await petApi.uploadPhotos(petId.value, files)
    ElMessage.success(`已上传 ${files.length} 张照片`)
    await loadPhotos()
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败,请稍后重试')
  } finally {
    uploading.value = false
  }
}

async function removePhoto(p: PetPhoto) {
  try {
    await ElMessageBox.confirm('确定删除这张照片吗?删除后无法恢复。', '删除照片', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await petApi.deletePhoto(p.id)
    photos.value = photos.value.filter((x) => x.id !== p.id)
    ElMessage.success('已删除')
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

// ===== 批量管理模式 =====
const selectedMode = ref(false)
const selectedIds = ref<number[]>([])
const batching = ref(false)

function isSelected(id: number) {
  return selectedIds.value.includes(id)
}
function toggleMode() {
  selectedMode.value = !selectedMode.value
  selectedIds.value = []
}
function toggleSelect(p: PetPhoto) {
  selectedIds.value = selectedIds.value.includes(p.id)
    ? selectedIds.value.filter((x) => x !== p.id)
    : [...selectedIds.value, p.id]
}
const allSelected = computed(
  () => photos.value.length > 0 && selectedIds.value.length === photos.value.length,
)
function toggleAll() {
  selectedIds.value = allSelected.value ? [] : photos.value.map((p) => p.id)
}
async function batchDelete() {
  if (!selectedIds.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedIds.value.length} 张照片吗？删除后无法恢复。`,
      '批量删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  batching.value = true
  try {
    const res = await petApi.deletePhotos(selectedIds.value)
    const del = (res && (res as any).deleted) ?? selectedIds.value.length
    const set = new Set(selectedIds.value)
    photos.value = photos.value.filter((x) => !set.has(x.id))
    ElMessage.success(`已删除 ${del} 张照片`)
    selectedMode.value = false
    selectedIds.value = []
  } catch (err: any) {
    ElMessage.error(err?.message || '批量删除失败')
  } finally {
    batching.value = false
  }
}

onMounted(() => {
  loadPet()
  loadPhotos()
})
</script>

<template>
  <div class="photo-gallery">
    <div class="pg-inner">
      <header class="pg-head">
        <button class="pg-back" type="button" @click="goBack">
          <span class="material-symbols-outlined" aria-hidden="true">arrow_back</span>
          <span>返回</span>
        </button>

        <div class="pg-titles">
          <h1 class="pg-title">{{ pet?.name || '宠物' }} 的成长日记</h1>
          <p class="pg-sub">
            <span class="material-symbols-outlined" aria-hidden="true">photo_library</span>
            <span>共 {{ photos.length }} 张照片</span>
          </p>
        </div>

        <button class="pg-upload" type="button" :disabled="uploading" @click="uploadPhoto">
          <span v-if="uploading" class="spinner" aria-hidden="true"></span>
          <span v-else class="material-symbols-outlined" aria-hidden="true">add_a_photo</span>
          <span>{{ uploading ? '上传中…' : '上传照片' }}</span>
        </button>

        <button class="pg-manage" type="button" @click="toggleMode">
          <span class="material-symbols-outlined" aria-hidden="true">{{ selectedMode ? 'close' : 'checklist' }}</span>
          <span>{{ selectedMode ? '取消选择' : '批量管理' }}</span>
        </button>
        <input
          ref="fileInput"
          class="hidden-file"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          @change="onPhotosSelected"
        />
      </header>

      <div v-if="selectedMode" class="pg-batchbar">
        <label class="pg-selectall">
          <input type="checkbox" :checked="allSelected" @change="toggleAll" />
          <span>全选</span>
        </label>
        <span class="pg-batch-count">已选 {{ selectedIds.length }} 张</span>
        <button
          class="pg-batch-del"
          type="button"
          :disabled="selectedIds.length === 0 || batching"
          @click="batchDelete"
        >
          <span v-if="batching" class="spinner" aria-hidden="true"></span>
          <span v-else class="material-symbols-outlined" aria-hidden="true">delete</span>
          <span>删除所选 ({{ selectedIds.length }})</span>
        </button>
      </div>

      <div v-if="loading" class="pg-loading">
        <span class="spinner" aria-hidden="true"></span>
        <span>加载成长记录…</span>
      </div>

      <div v-else-if="photos.length" class="pg-grid">
        <div
          v-for="p in photos"
          :key="p.id"
          class="pg-photo"
          :class="{ selected: selectedMode && isSelected(p.id) }"
          :style="{ backgroundImage: `url(${p.url})` }"
          :title="p.caption || pet?.name"
          @click="selectedMode ? toggleSelect(p) : undefined"
        >
          <span v-if="selectedMode && isSelected(p.id)" class="pg-check" aria-hidden="true">
            <span class="material-symbols-outlined">check</span>
          </span>
          <button
            v-if="!selectedMode"
            class="pg-del"
            type="button"
            @click.stop="removePhoto(p)"
            aria-label="删除照片"
          >
            <span class="material-symbols-outlined" aria-hidden="true">close</span>
          </button>
        </div>
      </div>

      <div v-else class="pg-empty">
        <span class="material-symbols-outlined" aria-hidden="true">photo_library</span>
        <p>还没有照片,上传一张开始记录 {{ pet?.name || '它' }} 的成长吧</p>
        <button class="pg-upload-empty" type="button" :disabled="uploading" @click="uploadPhoto">
          <span class="material-symbols-outlined" aria-hidden="true">add_a_photo</span>
          <span>上传第一张照片</span>
        </button>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.photo-gallery {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: $spacing-3xl;
}

.pg-inner {
  @include container-max;
  padding-top: $spacing-lg;
}

.pg-head {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;

  @media (max-width: 640px) {
    flex-wrap: wrap;
  }
}

.pg-back {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  border: 1px solid $color-outline;
  background: $color-surface-container-lowest;
  color: $color-on-surface;
  border-radius: $radius-full;
  padding: $spacing-sm $spacing-md;
  font-size: $font-size-body-md;
  cursor: pointer;
  transition: background 0.15s ease;

  &:hover {
    background: $color-surface-container-low;
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.pg-titles {
  flex: 1;
  min-width: 0;

  .pg-title {
    margin: 0;
    font-size: $font-size-headline-md;
    font-weight: 700;
    color: $color-on-surface;
  }

  .pg-sub {
    margin: $spacing-base 0 0;
    display: inline-flex;
    align-items: center;
    gap: $spacing-base;
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;

    .material-symbols-outlined {
      font-size: 16px;
    }
  }
}

.pg-upload {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  border: none;
  background: $color-brand;
  color: #fff;
  border-radius: $radius-full;
  padding: $spacing-sm $spacing-md;
  font-size: $font-size-body-md;
  font-weight: 600;
  cursor: pointer;
  box-shadow: $shadow-sm;
  transition: filter 0.15s ease;

  &:hover:not(:disabled) {
    filter: brightness(1.05);
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.hidden-file {
  display: none;
}

.pg-loading,
.pg-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-3xl $spacing-md;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 56px;
    opacity: 0.6;
  }

  p {
    margin: 0;
    font-size: $font-size-body-md;
  }
}

.pg-upload-empty {
  margin-top: $spacing-sm;
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  border: none;
  background: $color-brand;
  color: #fff;
  border-radius: $radius-full;
  padding: $spacing-sm $spacing-lg;
  font-size: $font-size-body-md;
  font-weight: 600;
  cursor: pointer;

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.pg-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(168px, 1fr));
  gap: $spacing-md;

  @media (max-width: 640px) {
    grid-template-columns: repeat(auto-fill, minmax(108px, 1fr));
    gap: $spacing-sm;
  }
}

.pg-photo {
  position: relative;
  aspect-ratio: 1;
  border-radius: $radius-lg;
  background-size: cover;
  background-position: center;
  background-color: $color-surface-container-low;
  box-shadow: $shadow-sm;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-card;

    .pg-del {
      opacity: 1;
    }
  }
}

.pg-del {
  position: absolute;
  top: $spacing-xs;
  right: $spacing-xs;
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s ease, background 0.15s ease;

  &:hover {
    background: $color-emergency;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.pg-manage {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  border: 1px solid $color-outline;
  background: $color-surface-container-lowest;
  color: $color-on-surface;
  border-radius: $radius-full;
  padding: $spacing-sm $spacing-md;
  font-size: $font-size-body-md;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;

  &:hover {
    background: $color-surface-container-low;
    border-color: $color-brand;
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.pg-batchbar {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline;
  border-radius: $radius-lg;

  @media (max-width: 640px) {
    flex-wrap: wrap;
    gap: $spacing-sm;
  }
}

.pg-selectall {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  cursor: pointer;

  input {
    width: 16px;
    height: 16px;
    accent-color: $color-brand;
    cursor: pointer;
  }
}

.pg-batch-count {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}

.pg-batch-del {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  border: none;
  background: $color-emergency;
  color: #fff;
  border-radius: $radius-full;
  padding: $spacing-sm $spacing-md;
  font-size: $font-size-body-md;
  font-weight: 600;
  cursor: pointer;
  transition: filter 0.15s ease;

  &:hover:not(:disabled) {
    filter: brightness(1.05);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.pg-photo {
  &.selected {
    outline: 3px solid $color-brand;
    outline-offset: 2px;

    .pg-check {
      opacity: 1;
    }
  }
}

.pg-check {
  position: absolute;
  top: $spacing-xs;
  left: $spacing-xs;
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: $color-brand;
  color: #fff;
  box-shadow: $shadow-sm;

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.5);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pg-spin 0.7s linear infinite;
}

@keyframes pg-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
