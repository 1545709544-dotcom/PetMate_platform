<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { petApi, type PetPhoto, petGenderLabel, petAgeText } from '@/api/modules/pet'
import type { Pet, PetHealthSummary, HealthRecord } from '@/api/modules/pet'

const router = useRouter()
const route = useRoute()

const pets = ref<Pet[]>([])
const selectedPetId = ref<number | null>(null)
const loading = ref(false)
const summary = ref<PetHealthSummary | null>(null)

// 成长日记照片（来自后端，按宠物加载）
const photos = ref<PetPhoto[]>([])
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const selectedPet = computed<Pet | null>(
  () => pets.value.find((p) => p.id === selectedPetId.value) ?? null
)

const genderText = computed(() => petGenderLabel(selectedPet.value?.gender))

const ageText = computed(() => petAgeText(selectedPet.value?.birthday))

const neuteredText = computed(() =>
  selectedPet.value?.neutered === 1 ? '已绝育' : '未绝育'
)

// 根据后端 recordType 映射到图标与配色
function metaOf(r: HealthRecord) {
  switch (r.recordType) {
    case 'vaccine':
      return { icon: 'vaccines', color: 'blue' }
    case 'deworming':
      return { icon: 'pest_control', color: 'green' }
    case 'checkup':
      return { icon: 'medical_services', color: 'orange' }
    case 'weight':
      return { icon: 'monitoring', color: 'blue' }
    case 'illness':
      return { icon: 'healing', color: 'orange' }
    default:
      return { icon: 'event', color: 'blue' }
  }
}

const today = new Date()

// 由真实健康摘要聚合出日程列表
const schedules = computed(() => {
  if (!summary.value) return []
  return summary.value.records.map((r) => {
    const m = metaOf(r)
    const pending = !!r.nextDate && new Date(r.nextDate) >= today
    return {
      icon: m.icon,
      color: m.color,
      title: r.title,
      recordType: r.recordType as string,
      desc: pending ? `下次 ${r.nextDate}` : `记录于 ${r.date}`,
      done: !pending,
    }
  })
})

// 档案分栏：id 直接对应后端 recordType，避免出现无数据源的装饰性 Tab
const recordTabs: Array<{ id: 'all' | HealthRecord['recordType']; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'vaccine', label: '疫苗本' },
  { id: 'deworming', label: '驱虫本' },
  { id: 'checkup', label: '体检报告' },
  { id: 'weight', label: '体重记录' },
  { id: 'illness', label: '病历' },
]
const activeRecordTab = ref<'all' | HealthRecord['recordType']>('all')

// 各分栏真实条数（挂在 Tab 上，空分栏一眼可见）
const recordTabCounts = computed<Record<string, number>>(() => {
  const rs = summary.value?.records ?? []
  const m: Record<string, number> = { all: rs.length }
  for (const t of recordTabs) {
    if (t.id === 'all') continue
    m[t.id] = rs.filter((r) => r.recordType === t.id).length
  }
  return m
})

// 当前分栏下的真实记录（按日期倒序）
const filteredRecords = computed<HealthRecord[]>(() => {
  const rs = summary.value?.records ?? []
  const list = activeRecordTab.value === 'all'
    ? rs.slice()
    : rs.filter((r) => r.recordType === activeRecordTab.value)
  return list.sort((a, b) => (b.date || '').localeCompare(a.date || ''))
})

const activeTabLabel = computed(
  () => recordTabs.find((t) => t.id === activeRecordTab.value)?.label ?? '记录',
)

async function loadPets() {
  loading.value = true
  try {
    const list = await petApi.list()
    pets.value = list ?? []
    if (pets.value.length > 0) {
      const fromRoute = Number(route.params.id)
      const initial =
        pets.value.find((p) => p.id === fromRoute)?.id ?? pets.value[0].id
      await selectPet(initial)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载宠物列表失败')
  } finally {
    loading.value = false
  }
}

async function selectPet(id: number) {
  selectedPetId.value = id
  try {
    summary.value = await petApi.healthSummary(id)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载健康档案失败')
    summary.value = null
  }
  // 切换宠物时重新加载成长日记照片
  await loadPhotos(id)
}

// 加载成长日记照片
async function loadPhotos(petId: number) {
  try {
    const list = await petApi.photos(petId)
    photos.value = list ?? []
  } catch {
    photos.value = []
  }
}

// 触发文件选择
function pickFiles() {
  fileInput.value?.click()
}

// 选择文件后上传
async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = '' // 允许重复选择同一文件
  if (files.length === 0 || selectedPetId.value == null) return
  uploading.value = true
  try {
    const uploaded = await petApi.uploadPhotos(selectedPetId.value, files)
    if (uploaded?.length) {
      photos.value = [...photos.value, ...uploaded]
      ElMessage.success(`已上传 ${uploaded.length} 张照片`)
    } else {
      await loadPhotos(selectedPetId.value)
      ElMessage.success('照片已上传')
    }
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

// 删除某张成长日记照片
async function removePhoto(p: PetPhoto) {
  try {
    await petApi.deletePhoto(p.id)
    photos.value = photos.value.filter((x) => x.id !== p.id)
    ElMessage.success('已删除')
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

// 日程类型 → 本地服务搜索词，点「去预约」真实跳到服务列表并预填关键词
const BOOK_KEYWORD: Record<string, string> = {
  vaccine: '疫苗',
  deworming: '驱虫',
  checkup: '体检',
  illness: '医疗',
  weight: '体检',
}

function bookSchedule(s: { done: boolean; title: string; recordType: string }) {
  if (s.done) return
  router.push({
    path: '/service',
    query: { keyword: BOOK_KEYWORD[s.recordType] || s.title, petId: String(selectedPetId.value ?? '') },
  })
}

function addPet() {
  router.push('/pet/create')
}

function viewDetail() {
  if (selectedPetId.value != null) router.push(`/pet/detail/${selectedPetId.value}`)
}

function onAvatarError(e: Event) {
  // 头像加载失败则隐藏 img,回退到首字母占位
  ;(e.target as HTMLImageElement).style.display = 'none'
}

onMounted(loadPets)
</script>

<template>
  <div class="pet-health-view">
    <div class="pet-health-container">
      <!-- 宠物选择 -->
      <section class="pet-selector">
        <div class="selector-head">
          <h2 class="selector-title">我的宠物</h2>
          <button class="add-pet" @click="addPet">
            <span class="material-symbols-outlined">add_circle</span>
            添加新成员
          </button>
        </div>

        <div v-if="pets.length" class="pet-list">
          <div
            v-for="p in pets"
            :key="p.id"
            class="pet-avatar-item"
            :class="{ active: selectedPetId === p.id }"
            @click="selectPet(p.id)"
          >
            <div class="pet-avatar-frame">
              <img
                v-if="p.avatar"
                :src="p.avatar"
                :alt="p.name"
                @error="onAvatarError"
              />
              <span v-else class="pet-initial">{{ p.name.charAt(0) }}</span>
            </div>
            <span class="pet-name" :class="{ active: selectedPetId === p.id }">{{
              p.name
            }}</span>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else-if="!loading" class="empty-pets">
          <span class="material-symbols-outlined empty-icon">pets</span>
          <p class="empty-text">你还没有添加宠物</p>
          <p class="empty-sub">添加新成员,开启专属健康档案与成长日记</p>
          <button class="empty-btn" @click="addPet">立即添加</button>
        </div>
      </section>

      <!-- 主体布局 -->
      <template v-if="selectedPet">
        <div class="layout">
          <!-- 左侧宠物信息卡 -->
          <section class="pet-id-card">
            <div class="card-content">
              <div class="card-head">
                <div>
                  <h1 class="card-title">{{ selectedPet.name }}</h1>
                  <p class="card-sub">
                    {{ selectedPet.breedName || selectedPet.categoryName }} ·
                    {{ genderText }}
                  </p>
                </div>
                <button class="edit-btn" @click="viewDetail">
                  <span class="material-symbols-outlined">edit</span>
                </button>
              </div>

              <div class="stats-grid">
                <div class="stat-item">
                  <span class="stat-label">年龄</span>
                  <span class="stat-value">{{ ageText }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">体重</span>
                  <span class="stat-value">{{
                    selectedPet.weight != null ? selectedPet.weight + ' kg' : '未知'
                  }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">绝育</span>
                  <span class="stat-value">{{ neuteredText }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">生日</span>
                  <span class="stat-value">{{ selectedPet.birthday || '未知' }}</span>
                </div>
              </div>

              <button class="detail-btn" @click="viewDetail">查看详细档案</button>
            </div>
          </section>

          <!-- 右侧健康日程 -->
          <section class="health-schedule">
            <div class="schedule-head">
              <h3 class="schedule-title">健康日程</h3>
              <span class="schedule-count" v-if="summary"
                >疫苗 {{ summary.vaccineCount }} · 驱虫
                {{ summary.dewormCount }}</span
              >
            </div>

            <div v-if="schedules.length" class="schedule-list">
              <div
                v-for="(s, i) in schedules"
                :key="i"
                class="schedule-item"
                :class="`color-${s.color}`"
              >
                <div class="sched-icon">
                  <span class="material-symbols-outlined">{{ s.icon }}</span>
                </div>
                <div class="sched-body">
                  <div class="sched-title">{{ s.title }}</div>
                  <div class="sched-desc">{{ s.desc }}</div>
                </div>
                <button v-if="!s.done" class="sched-btn" @click="bookSchedule(s)">
                  去预约
                </button>
                <span v-else class="material-symbols-outlined sched-done"
                  >check_circle</span
                >
              </div>
            </div>
            <p v-else class="schedule-empty">暂无健康记录,添加疫苗/驱虫后自动归纳到这里</p>

            <div class="record-tabs">
              <button
                v-for="t in recordTabs"
                :key="t.id"
                class="record-tab"
                :class="{ active: activeRecordTab === t.id }"
                @click="activeRecordTab = t.id"
              >
                {{ t.label }}
                <span class="tab-num">{{ recordTabCounts[t.id] || 0 }}</span>
              </button>
            </div>

            <!-- 分栏内容：按 recordType 真实过滤 -->
            <div v-if="filteredRecords.length" class="record-list">
              <div v-for="r in filteredRecords" :key="r.id" class="record-row">
                <span
                  class="material-symbols-outlined rec-ico"
                  :class="`color-${metaOf(r).color}`"
                >{{ metaOf(r).icon }}</span>
                <div class="rec-body">
                  <div class="rec-title">{{ r.title }}</div>
                  <div class="rec-desc">
                    {{ r.date }}
                    <template v-if="r.content"> · {{ r.content }}</template>
                  </div>
                </div>
                <span v-if="r.nextDate" class="rec-next">下次 {{ r.nextDate }}</span>
              </div>
            </div>
            <p v-else class="record-empty">暂无{{ activeTabLabel }}记录</p>
          </section>
        </div>

        <!-- 成长日记 -->
        <section class="diary-section">
          <div class="diary-head">
            <div>
              <h3 class="diary-title">成长日记</h3>
              <p class="diary-sub">记录 {{ selectedPet.name }} 每一个萌动瞬间</p>
            </div>
            <button class="upload-btn" :disabled="uploading" @click="pickFiles">
              <span class="material-symbols-outlined">add_photo_alternate</span>
              {{ uploading ? '上传中…' : '上传照片' }}
            </button>
            <input
              ref="fileInput"
              type="file"
              accept="image/*"
              multiple
              hidden
              @change="onFileChange"
            />
          </div>

          <div v-if="photos.length" class="photo-grid">
            <div class="photo-main">
              <img :src="photos[0].url" :alt="photos[0].caption || selectedPet.name" />
              <button class="photo-del" title="删除" @click="removePhoto(photos[0])">
                <span class="material-symbols-outlined">close</span>
              </button>
            </div>
            <div class="photo-sub">
              <div v-for="p in photos.slice(1)" :key="p.id" class="photo-cell">
                <img :src="p.url" :alt="p.caption || selectedPet.name" />
                <button class="photo-del" title="删除" @click="removePhoto(p)">
                  <span class="material-symbols-outlined">close</span>
                </button>
              </div>
            </div>
          </div>
          <div v-else class="photo-empty">
            <span class="material-symbols-outlined">photo_library</span>
            <p>还没有成长日记照片，点击「上传照片」记录 {{ selectedPet.name }} 的萌动瞬间吧</p>
          </div>
        </section>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.pet-health-view {
  min-height: 100vh;
  background: $color-background;
}

.pet-health-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
}

// ============ 宠物选择 ============
.selector-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.selector-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

.add-pet {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  color: $color-brand;
  cursor: pointer;

  &:hover {
    opacity: 0.8;
  }
}

.pet-list {
  display: flex;
  gap: $spacing-md;
  overflow-x: auto;
  padding-bottom: $spacing-md;
  @include scrollbar-hide;
}

.pet-avatar-item {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  cursor: pointer;
  transition: opacity $transition-fast;

  &.not-active {
    opacity: 0.6;

    &:hover {
      opacity: 1;
    }
  }
}

.pet-avatar-frame {
  width: 80px;
  height: 80px;
  border-radius: $radius-full;
  background: $color-surface;
  padding: 4px;
  transition: all $transition-fast;

  @include desktop {
    width: 96px;
    height: 96px;
  }

  img {
    width: 100%;
    height: 100%;
    border-radius: $radius-full;
    object-fit: cover;
    filter: grayscale(0.2);
  }

  .active & {
    padding: 4px;
    border: 4px solid $color-brand;
    box-shadow: 0 0 0 4px rgba(255, 122, 50, 0.2);
    background: $color-surface;
    box-shadow: $shadow-lg;

    img {
      filter: none;
    }
  }
}

.pet-initial {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  background: rgba(255, 122, 50, 0.15);
  color: $color-brand;
  font-size: 28px;
  font-weight: 700;
}

.pet-name {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  font-weight: 400;

  &.active {
    color: $color-brand;
    font-weight: 700;
  }
}

// ============ 空状态 ============
.empty-pets {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-2xl;
  background: $color-surface-container-low;
  border-radius: $radius-xl;
  text-align: center;
  color: $color-on-surface-variant;
}

.empty-icon {
  font-size: 56px;
  color: $color-brand;
  opacity: 0.8;
}

.empty-text {
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.empty-sub {
  font-size: $font-size-label-sm;
  max-width: 320px;
}

.empty-btn {
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-xl;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;

  &:hover {
    opacity: 0.9;
  }
}

// ============ 主体 ============
.layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;

  @include desktop {
    grid-template-columns: 1fr 2fr;
  }
}

.pet-id-card {
  position: relative;
  padding: $spacing-xl;
  border-radius: $radius-xl;
  background: linear-gradient(135deg, $color-brand 0%, $color-brand-dark 100%);
  color: #ffffff;
  box-shadow: $shadow-floating;
  overflow: hidden;
}


.card-content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
  height: 100%;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.card-title {
  font-family: $font-family-headline;
  font-size: 36px;
  font-weight: 800;
  letter-spacing: -0.01em;

  @include desktop {
    font-size: 48px;
  }
}

.card-sub {
  margin-top: 4px;
  font-size: $font-size-label-md;
  color: rgba(255, 255, 255, 0.8);
}

.edit-btn {
  padding: $spacing-sm;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  border-radius: $radius-lg;
  color: #ffffff;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: rgba(255, 255, 255, 0.3);
  }
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;
}

.stat-item {
  padding: $spacing-md;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  border-radius: $radius-lg;
}

.stat-label {
  display: block;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.7);
  text-transform: uppercase;
  letter-spacing: 0.1em;
  margin-bottom: 4px;
}

.stat-value {
  font-size: $font-size-body-lg;
  font-weight: 700;
}

.detail-btn {
  padding: $spacing-md 0;
  background: #ffffff;
  color: $color-brand;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-2px);
  }
}

// ============ 健康日程 ============
.health-schedule {
  padding: $spacing-xl;
  background: $color-surface-container-low;
  border-radius: $radius-xl;
}

.schedule-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.schedule-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

.schedule-count {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.schedule-empty {
  padding: $spacing-lg 0;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.schedule-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  margin-bottom: $spacing-xl;
}

.schedule-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: #ffffff;
  border-radius: $radius-lg;
  border-left: 4px solid;
  transition: box-shadow $transition-fast;

  &:hover {
    box-shadow: $shadow-md;
  }

  &.color-blue {
    border-left-color: $color-secondary;
  }

  &.color-green {
    border-left-color: $color-success;
  }

  &.color-orange {
    border-left-color: $color-brand;
  }
}

.sched-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-md;

  .color-blue & {
    background: rgba(0, 96, 172, 0.15);
    color: $color-secondary;
  }
  .color-green & {
    background: rgba(82, 196, 26, 0.15);
    color: $color-success;
  }
  .color-orange & {
    background: rgba(255, 122, 50, 0.15);
    color: $color-brand;
  }

  .material-symbols-outlined {
    font-size: 24px;
  }
}

.sched-body {
  flex: 1;
}

.sched-title {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}

.sched-desc {
  margin-top: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.sched-btn {
  padding: $spacing-xs $spacing-lg;
  background: $color-secondary;
  color: #ffffff;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  cursor: pointer;
}

.sched-done {
  color: $color-success;
}

.record-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  border-top: 1px solid $color-outline-variant;
  padding-top: $spacing-md;
  margin-top: $spacing-md;
}

.record-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-sm;
  border: none;
  background: transparent;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-family: inherit;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast;

  .tab-num {
    min-width: 16px;
    padding: 0 4px;
    border-radius: $radius-full;
    background: $color-surface-container;
    font-size: 10px;
    font-weight: 700;
    line-height: 16px;
    text-align: center;
  }

  &:hover:not(.active) {
    background: $color-surface-container;
    color: $color-on-surface;
  }

  &.active {
    background: $color-secondary-container;
    color: $color-secondary;

    .tab-num {
      background: rgba(0, 96, 172, 0.18);
      color: $color-secondary;
    }
  }
}

// ── 分栏记录列表 ──
.record-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  margin-top: $spacing-sm;
  max-height: 260px;
  overflow-y: auto;
}

.record-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-xs $spacing-sm;
  border-radius: $radius-default;
  background: $color-surface-container-low;
}

.rec-ico {
  font-size: 20px;
  flex-shrink: 0;

  &.color-blue { color: $color-secondary; }
  &.color-green { color: $color-success; }
  &.color-orange { color: $color-brand; }
}

.rec-body {
  flex: 1;
  min-width: 0;
}

.rec-title {
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
  @include text-ellipsis(1);
}

.rec-desc {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  @include text-ellipsis(1);
}

.rec-next {
  flex-shrink: 0;
  padding: 2px $spacing-xs;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.12);
  color: $color-brand;
  font-size: 11px;
  font-weight: 600;
}

.record-empty {
  margin-top: $spacing-sm;
  padding: $spacing-lg 0;
  text-align: center;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

// ============ 成长日记 ============
.diary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.diary-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

.diary-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.upload-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
}

.photo-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: 2fr 1fr;
  }
}

.photo-sub {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  grid-template-rows: repeat(2, 1fr);
  gap: $spacing-md;
}

.photo-cell {
  position: relative;
  border-radius: $radius-default;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: $radius-default;
    min-height: 100px;
    display: block;
    transition: transform $transition-fast;
  }

  &:hover img {
    transform: scale(1.04);
  }
}

.photo-main {
  position: relative;
  grid-row: span 1;

  @include desktop {
    grid-row: span 2;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    border-radius: $radius-lg;
    min-height: 200px;
    display: block;
  }

  &:hover .photo-del {
    opacity: 1;
  }
}

.photo-del {
  position: absolute;
  top: $spacing-xs;
  right: $spacing-xs;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  background: rgba(0, 0, 0, 0.5);
  color: #ffffff;
  cursor: pointer;
  opacity: 0;
  transition: opacity $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    background: rgba(239, 68, 68, 0.9);
  }
}

.photo-cell:hover .photo-del,
.photo-main:hover .photo-del {
  opacity: 1;
}

.photo-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-2xl;
  background: $color-surface-container-low;
  border-radius: $radius-xl;
  text-align: center;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 48px;
    color: $color-brand;
    opacity: 0.7;
  }

  p {
    margin: 0;
    font-size: $font-size-label-md;
    max-width: 360px;
  }
}
</style>
