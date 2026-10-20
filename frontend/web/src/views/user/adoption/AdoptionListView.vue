<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adoptionApi, type Adoption } from '@/api/modules/adoption'
import { regionOptions } from '@/data/regions'
import { requireVerified } from '@/utils/requireVerified'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

// 发布领养——未实名时弹警告弹窗，不跳转
function goPublish() {
  if (!requireVerified()) return
  router.push('/adoption/publish')
}

// ============ 筛选 ============
const filterTabs = [
  { id: 'all', label: '全部' },
  { id: 'dog', label: '狗狗' },
  { id: 'cat', label: '猫咪' },
  { id: 'other', label: '其他' },
]
const activeTab = ref('all')

// 所在地区筛选：省 / 市 / 区（县）三级独立筛选（联动出选项）
const province = ref('')
const city = ref('')
const district = ref('')

const provinceOptions = regionOptions
const cityOptions = computed(
  () => regionOptions.find((p) => p.value === province.value)?.children || [],
)
const districtOptions = computed(() => {
  const p = regionOptions.find((x) => x.value === province.value)
  return p?.children?.find((c) => c.value === city.value)?.children || []
})

// 上游变更时清空下游，避免无效组合
function onProvinceChange() {
  city.value = ''
  district.value = ''
}
function onCityChange() {
  district.value = ''
}
function resetRegion() {
  province.value = ''
  city.value = ''
  district.value = ''
}

// ============ 列表数据（真实接口）============
interface AdoptionItem {
  id: number
  name: string
  type: 'dog' | 'cat' | 'other'
  cover: string
  gender: 'male' | 'female' | 'unknown'
  breed: string
  age: string
  city: string
  shelter: string
  desc: string
  tags: string[]
  status: 'available' | 'pending' | 'adopted'
}

const loading = ref(false)
const raw = ref<Adoption[]>([])

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

function toItem(a: Adoption): AdoptionItem {
  const imgs = parseImages(a.images)
  const tags: string[] = []
  if (a.vaccinated) tags.push('已疫苗')
  if (a.neutered) tags.push('已绝育')
  if (a.healthStatus) tags.push(a.healthStatus)
  if (a.breed) tags.push(a.breed)
  return {
    id: a.id,
    name: a.title,
    type: a.petType,
    cover: imgs[0] || '',
    gender: a.gender === 2 ? 'female' : a.gender === 1 ? 'male' : 'unknown',
    breed: a.breed || '',
    age: a.age || '',
    city: a.city || '',
    shelter: a.contact ? `救助热线 ${a.contact}` : '公益救助站',
    desc: a.rescueStory || a.healthStatus || '等待一个温暖的家。',
    tags,
    status: a.status === 2 ? 'adopted' : a.status === 1 ? 'available' : 'pending',
  }
}

const list = computed(() => raw.value.map(toItem))

// 选中省份对应的「所有市级名称」集合。后端 city 仅存市级（如「杭州市」）、不含省名，
// 因此选省时用该省下全部市名做 OR 匹配，才能正确筛出该市数据。
const provinceCityValues = computed(() => {
  const p = regionOptions.find((x) => x.value === province.value)
  return p?.children?.map((c) => c.value) || []
})

const filteredAdoptions = computed(() => {
  let res = list.value
  if (activeTab.value !== 'all') res = res.filter((a) => a.type === activeTab.value)
  // 省：用该省下所有市名 OR 匹配（兼容「仅市名」与「省/市/区完整路径」两种存储）
  if (province.value) {
    const cities = provinceCityValues.value
    res = res.filter((a) => a.city && cities.some((c) => a.city.includes(c)))
  }
  // 市 / 区（县）：用各自名称匹配
  if (city.value) res = res.filter((a) => a.city && a.city.includes(city.value))
  if (district.value) res = res.filter((a) => a.city && a.city.includes(district.value))
  return res
})

async function load() {
  loading.value = true
  try {
    raw.value = await adoptionApi.list()
  } catch {
    ElMessage.error('领养信息加载失败')
    raw.value = []
  } finally {
    loading.value = false
  }
}

// ============ 操作 ============
function goDetail(id: number) {
  router.push(`/adoption/detail/${id}`)
}

async function apply(a: AdoptionItem, e: Event) {
  e.stopPropagation()
  try {
    const { value: name } = await ElMessageBox.prompt('请填写联系人姓名', '提交领养申请', {
      inputValidator: (v) => (!!v && v.trim().length > 0) || '姓名不能为空',
      inputErrorMessage: '姓名不能为空',
    })
    const { value: phone } = await ElMessageBox.prompt('请填写手机号', '提交领养申请', {
      inputPattern: /^1[3-9]\d{9}$/,
      inputErrorMessage: '手机号格式不正确',
    })
    await adoptionApi.apply(a.id, { name: name.trim(), phone })
    ElMessage.success('已提交领养申请，志愿者将尽快与您联系')
  } catch (err: any) {
    if (err !== 'cancel' && err?.action !== 'cancel') {
      ElMessage.error(err?.message || '申请失败')
    }
  }
}

function showProcess() {
  ElMessage.info('领养流程：① 提交申请 ② 视频家访 ③ 签订协议 ④ 接回新家')
}

function statusLabel(s: AdoptionItem['status']) {
  return s === 'available' ? '可领养' : s === 'pending' ? '审核中' : '已领养'
}

onMounted(load)
</script>

<template>
  <div class="adoption-list">
    <!-- Hero 区 -->
    <section class="hero no-vw">
      <div class="hero-mask"></div>
      <div class="hero-content">
        <span class="hero-badge">公益项目</span>
        <h1 class="hero-title">领养代替购买</h1>
        <p class="hero-desc">每一只毛孩子都值得被温柔以待。给它们一个家，给自己的心一份温暖。</p>
        <button class="hero-btn" @click="showProcess">了解领养流程</button>
      </div>
    </section>

    <div class="page-container">
      <!-- 筛选条 -->
      <div class="filter-bar">
        <div class="filter-tabs">
          <button
            v-for="t in filterTabs"
            :key="t.id"
            class="filter-tab"
            :class="{ active: activeTab === t.id }"
            @click="activeTab = t.id"
          >{{ t.label }}</button>
        </div>
        <div class="filter-region">
          <span class="material-symbols-outlined city-icon">location_on</span>
          <el-select
            v-model="province"
            class="region-select"
            placeholder="省"
            clearable
            @change="onProvinceChange"
          >
            <el-option
              v-for="p in provinceOptions"
              :key="p.value"
              :label="p.label"
              :value="p.value"
            />
          </el-select>
          <el-select
            v-model="city"
            class="region-select"
            placeholder="市"
            clearable
            :disabled="!province"
            @change="onCityChange"
          >
            <el-option
              v-for="c in cityOptions"
              :key="c.value"
              :label="c.label"
              :value="c.value"
            />
          </el-select>
          <el-select
            v-model="district"
            class="region-select"
            placeholder="区 / 县"
            clearable
            :disabled="!city"
          >
            <el-option
              v-for="d in districtOptions"
              :key="d.value"
              :label="d.label"
              :value="d.value"
            />
          </el-select>
        </div>
        <button class="my-btn publish" @click="goPublish">
          <span class="material-symbols-outlined">add_circle</span>
          发布领养
        </button>
        <button class="my-btn" @click="router.push('/adoption/my')">
          <span class="material-symbols-outlined">assignment_ind</span>
          我的申请
        </button>
        <button class="my-btn" @click="router.push('/adoption/manage')">
          <span class="material-symbols-outlined">manage_accounts</span>
          我的领养管理
        </button>
      </div>

      <!-- 提示卡 -->
      <div class="tip-card">
        <span class="material-symbols-outlined tip-icon">info</span>
        <p>领养是完全免费的，但需要经过严格的家访审核。我们希望每一只毛孩子都能找到真正爱它的家人。</p>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="empty">
        <span class="material-symbols-outlined empty-icon spin">progress_activity</span>
        <p>加载中…</p>
      </div>

      <!-- 列表 -->
      <div v-else-if="filteredAdoptions.length > 0" class="adoption-grid">
        <article
          v-for="(a, i) in filteredAdoptions"
          :key="a.id"
          class="adoption-card pet-card"
          :class="{ 'is-pending': a.status === 'pending' }"
          :style="{ animationDelay: (i % 15) * 45 + 'ms' }"
          @click="goDetail(a.id)"
        >
          <div class="cover">
            <img v-if="a.cover" :src="a.cover" :alt="a.name" @error="(e:any)=>e.target.style.display='none'" />
            <span v-else class="material-symbols-outlined cover-ph">pets</span>
            <span class="status-pill" :class="a.status">
              {{ statusLabel(a.status) }}
            </span>
            <span class="type-tag">
              {{ a.type === 'dog' ? '🐕 狗狗' : a.type === 'cat' ? '🐈 猫咪' : '🐾 其他' }}
            </span>
          </div>
          <div class="body">
            <div class="head">
              <h3 class="name">{{ a.name }}</h3>
              <span class="meta">
                <span class="material-symbols-outlined">{{ a.gender === 'female' ? 'female' : a.gender === 'male' ? 'male' : 'help' }}</span>
              </span>
            </div>
            <p class="breed">{{ [a.breed, a.age].filter(Boolean).join(' · ') }}</p>
            <p class="desc">{{ a.desc }}</p>
            <div class="tags">
              <span v-for="t in a.tags" :key="t" class="tag">{{ t }}</span>
            </div>
            <div class="foot">
              <div class="shelter">
                <span class="material-symbols-outlined">home</span>
                <span>{{ a.shelter }}</span>
              </div>
              <div class="city">
                <span class="material-symbols-outlined">location_on</span>
                <span>{{ a.city }}</span>
              </div>
            </div>
            <button
              v-if="a.status === 'available'"
              class="apply-btn"
              @click="apply(a, $event)"
            >
              申请领养
            </button>
            <button v-else class="apply-btn disabled" disabled>
              {{ a.status === 'pending' ? '审核中' : '已被领养' }}
            </button>
          </div>
        </article>
      </div>

      <!-- 空状态 -->
      <PetEmptyState v-else icon="pets" title="暂无符合筛选条件的领养信息">
        <template #action>
          <button class="empty-btn" @click="activeTab = 'all'; resetRegion()">重置筛选</button>
        </template>
      </PetEmptyState>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.adoption-list {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: $spacing-2xl;
}

// ====== Hero ======
.hero {
  position: relative;
  height: 280px;
  overflow: hidden;
  background: linear-gradient(135deg, #ff7a32 0%, #ffb694 100%);
}

.hero-mask {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 78% 22%, rgba(255, 255, 255, 0.3) 0%, transparent 45%),
    radial-gradient(circle at 12% 95%, rgba(255, 182, 148, 0.4) 0%, transparent 52%),
    linear-gradient(135deg, rgba(255, 122, 50, 0) 45%, rgba(255, 122, 50, 0.28) 100%);
}

.hero-content {
  position: relative;
  z-index: 10;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  max-width: $width-content-max;
  margin: 0 auto;
  padding: 0 $width-gutter-mobile;

  @include desktop {
    padding: 0 $width-margin-desktop;
  }
}

.hero-badge {
  display: inline-block;
  width: fit-content;
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(8px);
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  color: #fff;
  font-weight: $font-weight-semibold;
  margin-bottom: $spacing-md;
}

.hero-title {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: $font-weight-extrabold;
  color: #fff;
  margin: 0 0 $spacing-sm;
  letter-spacing: -0.02em;

  @media (max-width: 768px) {
    font-size: $font-size-headline-lg;
  }
}

.hero-desc {
  font-size: $font-size-body-lg;
  color: rgba(255, 255, 255, 0.95);
  margin: 0 0 $spacing-lg;
  max-width: 600px;
}

.hero-btn {
  width: fit-content;
  padding: 12px 32px;
  background: #fff;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  box-shadow: $shadow-lg;
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-2px);
  }
}

// ====== Page container ======
.page-container {
  max-width: $width-content-max;
  margin: 0 auto;
  padding: $spacing-xl $width-gutter-mobile 0;

  @include desktop {
    padding: $spacing-xl $width-margin-desktop 0;
  }
}

// ====== Filter bar ======
.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  background: $color-surface-container-lowest;
  padding: $spacing-sm;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  margin-bottom: $spacing-md;
  flex-wrap: wrap;
}

.filter-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.filter-tab {
  padding: 8px 18px;
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
}

.filter-tab.active {
  background: $color-brand;
  color: $color-on-brand;
  font-weight: $font-weight-semibold;
}

.filter-region {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 3px 12px;
  background: $color-surface-container-low;
  border-radius: $radius-full;
  flex-wrap: wrap;

  :deep(.region-select) {
    width: 96px;
  }
  :deep(.region-select .el-input__wrapper) {
    background: $color-surface-container-lowest;
    box-shadow: 0 0 0 1px $color-outline-variant inset;
    border-radius: $radius-full;
    font-size: $font-size-label-sm;
  }
  :deep(.region-select .el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 1px $color-brand inset;
  }
  :deep(.region-select .el-input__inner) {
    font-size: $font-size-label-sm;
    color: $color-on-surface;
    font-family: inherit;
  }
  :deep(.region-select .el-input__inner::placeholder) {
    color: $color-text-tertiary;
  }
  // 未选上级时弱化下级选择器
  :deep(.region-select.is-disabled .el-input__wrapper) {
    background: transparent;
    box-shadow: none;
  }
}

.city-icon {
  font-size: 18px;
  color: $color-text-secondary;
}

.my-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: $color-surface-container-low;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-medium;
  font-family: inherit;
  cursor: pointer;
  white-space: nowrap;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

.my-btn.publish {
  background: $color-brand;
  color: $color-on-brand;
  border-color: $color-brand;
  font-weight: $font-weight-semibold;

  .material-symbols-outlined {
    color: $color-on-brand;
  }
  &:hover {
    background: $color-brand-dark;
    border-color: $color-brand-dark;
    color: $color-on-brand;
    box-shadow: 0 4px 12px rgba($color-brand, 0.3);
  }
}

// ====== Tip card ======
.tip-card {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: rgba($color-secondary, 0.08);
  border: 1px solid rgba($color-secondary, 0.2);
  border-radius: $radius-default;
  margin-bottom: $spacing-md;

  p {
    margin: 0;
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;
    line-height: $line-height-normal;
  }
}

.tip-icon {
  color: $color-secondary;
  font-size: 22px !important;
  flex-shrink: 0;
}

// ====== Grid ======
.adoption-grid {
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

.adoption-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  overflow: hidden;
  cursor: pointer;
  box-shadow: $shadow-card;
  transition: transform $transition-fast, box-shadow $transition-fast;
  display: flex;
  flex-direction: column;
  will-change: transform;
  animation: cardFadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) both;
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

.adoption-card:hover {
  transform: translateY(-4px);
  box-shadow: $shadow-lg;
}

.adoption-card:hover .cover img {
  transform: scale(1.06);
}

.adoption-card.is-pending {
  opacity: 0.75;
}

.cover {
  position: relative;
  aspect-ratio: 16 / 10;
  background: $color-surface-container;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;

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

.status-pill.pending {
  background: $color-warning;
}

.status-pill.adopted {
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

.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: $spacing-sm;
}

.name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
  flex: 1;
  min-width: 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
  line-height: $line-height-tight;
  // 始终预留两行高度，保证长短标题的卡片对齐
  min-height: 2.4em;
}

.meta {
  flex-shrink: 0;
}

.meta .material-symbols-outlined {
  font-size: 22px;
  color: $color-danger;
  font-variation-settings: 'FILL' 1;
  margin-top: 2px;
}

.breed {
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  margin: 0;
}

.desc {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  line-height: $line-height-normal;
  margin: 4px 0;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.6em;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 4px 0;
}

.tag {
  font-size: 11px;
  padding: 2px 8px;
  background: rgba($color-brand, 0.1);
  color: $color-brand-dark;
  border-radius: $radius-sm;
  font-weight: $font-weight-medium;
}

.foot {
  display: flex;
  justify-content: space-between;
  gap: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin-top: 4px;
  padding-top: $spacing-xs;
  border-top: 1px dashed $color-outline-variant;
}

.shelter,
.city {
  display: flex;
  align-items: center;
  gap: 4px;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

.apply-btn {
  width: 100%;
  padding: 10px 0;
  margin-top: $spacing-sm;
  background: $color-brand;
  color: $color-on-brand;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  border: none;
  border-radius: $radius-full;
  font-family: inherit;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba($color-brand, 0.25);
  transition: background $transition-fast;

  &:hover {
    background: $color-brand-dark;
  }
}

.apply-btn.disabled {
  background: $color-surface-container-high;
  color: $color-text-tertiary;
  box-shadow: none;
  cursor: not-allowed;
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

.empty-btn {
  padding: 8px 20px;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
}

.spin {
  animation: state-spin 1s linear infinite;
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .adoption-card {
    animation: none;
  }
  .cover img,
  .adoption-card:hover .cover img {
    transition: none;
    transform: none;
  }
}
</style>
