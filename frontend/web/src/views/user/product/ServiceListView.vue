<script setup lang="ts">
import { onMounted, ref, reactive, watch, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  serviceApi,
  type ServiceItem,
  type ServiceCategoryNode,
  type ServiceSort,
  type PetType,
  type ServiceMode,
} from '@/api/modules/service'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()
const route = useRoute()

const search = reactive({
  keyword: '',
  category: '',
  subCategory: '',
  petType: '' as '' | PetType,
  serviceMode: '' as '' | ServiceMode,
  sort: 'default' as ServiceSort,
})

const categories = ref<ServiceCategoryNode[]>([])
const services = ref<ServiceItem[]>([])
const total = ref(0)
const loading = ref(false)
const coverError = reactive<Record<number, boolean>>({})

const SORTS: Array<{ value: ServiceSort; label: string; icon: string }> = [
  { value: 'default', label: '综合排序', icon: 'sort' },
  { value: 'sales', label: '销量优先', icon: 'local_fire_department' },
  { value: 'rating', label: '好评优先', icon: 'star' },
  { value: 'price_asc', label: '价格从低到高', icon: 'trending_down' },
  { value: 'price_desc', label: '价格从高到低', icon: 'trending_up' },
]
const PET_TYPES: Array<{ value: '' | PetType; label: string; icon: string }> = [
  { value: '', label: '不限', icon: 'pets' },
  { value: 'dog', label: '狗狗', icon: 'sound_detection_dog_barking' },
  { value: 'cat', label: '猫咪', icon: 'pets' },
]
const MODES: Array<{ value: '' | ServiceMode; label: string; icon: string }> = [
  { value: '', label: '不限', icon: 'all_inclusive' },
  { value: 'instore', label: '到店服务', icon: 'storefront' },
  { value: 'onsite', label: '上门服务', icon: 'directions_car' },
]

/** 当前一级分类下的二级子类（含数量），未选一级时不展示 */
const subCategories = computed(() => {
  const node = categories.value.find((c) => c.name === search.category)
  return node?.subs ?? []
})

/** 已选筛选条件的可撤销标签 */
const activeFilters = computed(() => {
  const out: Array<{ key: string; label: string }> = []
  if (search.category) out.push({ key: 'category', label: search.category })
  if (search.subCategory) out.push({ key: 'subCategory', label: search.subCategory })
  if (search.petType) out.push({ key: 'petType', label: PET_TYPES.find((p) => p.value === search.petType)!.label })
  if (search.serviceMode) out.push({ key: 'serviceMode', label: MODES.find((m) => m.value === search.serviceMode)!.label })
  if (search.keyword) out.push({ key: 'keyword', label: `"${search.keyword}"` })
  return out
})

function clearFilter(key: string) {
  if (key === 'category') { search.category = ''; search.subCategory = '' }
  else if (key === 'subCategory') search.subCategory = ''
  else if (key === 'petType') search.petType = ''
  else if (key === 'serviceMode') search.serviceMode = ''
  else if (key === 'keyword') search.keyword = ''
  loadServices()
}

function resetAll() {
  search.keyword = ''
  search.category = ''
  search.subCategory = ''
  search.petType = ''
  search.serviceMode = ''
  search.sort = 'default'
  fallbackTip.value = ''
  loadServices()
}

function pickCategory(name: string) {
  search.category = search.category === name ? '' : name
  search.subCategory = ''
  loadServices()
}
function pickSub(name: string) {
  search.subCategory = search.subCategory === name ? '' : name
  loadServices()
}

async function loadCategories() {
  try {
    categories.value = (await serviceApi.categories()) || []
  } catch {
    categories.value = []
  }
}

const fallbackTip = ref('')

async function loadServices(opts: { allowFallback?: boolean } = {}) {
  loading.value = true
  try {
    const resp = await serviceApi.search({
      keyword: search.keyword || undefined,
      category: search.category || undefined,
      subCategory: search.subCategory || undefined,
      petType: search.petType || undefined,
      serviceMode: search.serviceMode || undefined,
      sort: search.sort,
      page: 1,
      size: 60,
    })
    services.value = resp?.list || []
    total.value = resp?.total ?? services.value.length

    // 从健康日程等入口带词跳来却零结果 → 回退展示全部，不让用户面对空页
    if (opts.allowFallback && search.keyword && services.value.length === 0) {
      const kw = search.keyword
      search.keyword = ''
      const all = await serviceApi.search({
        category: search.category || undefined,
        sort: search.sort,
        page: 1,
        size: 60,
      })
      services.value = all?.list || []
      total.value = all?.total ?? services.value.length
      fallbackTip.value = `暂无「${kw}」相关服务，已为你展示全部可预约服务`
    }
  } catch {
    ElMessage.error('服务列表加载失败')
    services.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onCoverError(id: number) {
  coverError[id] = true
}

function modeLabel(m?: ServiceMode) {
  return m === 'onsite' ? '上门' : '到店'
}
function petLabel(p?: PetType) {
  if (p === 'dog') return '仅狗狗'
  if (p === 'cat') return '仅猫咪'
  return '猫狗通用'
}
/** 有档位差价时展示区间，否则展示单价 */
function priceRange(s: ServiceItem) {
  if (s.maxPrice && Number(s.maxPrice) > Number(s.price)) {
    return `${Number(s.price)} - ${Number(s.maxPrice)}`
  }
  return String(Number(s.price))
}
function hasRange(s: ServiceItem) {
  return !!(s.maxPrice && Number(s.maxPrice) > Number(s.price))
}

function goDetail(s: ServiceItem) {
  router.push(`/service/${s.id}`)
}
function book(s: ServiceItem) {
  // 带上从健康日程跳来的 petId，预约下单页可直接选中该宠物
  const petId = route.query.petId
  router.push({
    path: `/appointment/booking/${s.id}`,
    query: petId ? { petId: String(petId) } : undefined,
  })
}

// 接住外部跳转带来的筛选条件（如宠物健康日程「去预约」）
function applyRouteQuery(): boolean {
  const kw = route.query.keyword
  const cat = route.query.category
  const sub = route.query.subCategory
  search.keyword = typeof kw === 'string' ? kw : ''
  search.category = typeof cat === 'string' ? cat : ''
  search.subCategory = typeof sub === 'string' ? sub : ''
  fallbackTip.value = ''
  return !!search.keyword
}

onMounted(() => {
  const fromLink = applyRouteQuery()
  loadCategories()
  loadServices({ allowFallback: fromLink })
})

watch(
  () => route.query,
  () => {
    if (route.path !== '/service') return
    const fromLink = applyRouteQuery()
    loadServices({ allowFallback: fromLink })
  },
)
</script>

<template>
  <div class="service-view">
    <div class="service-container">
      <!-- Hero -->
      <section class="hero">
        <div class="hero-glow1"></div>
        <div class="hero-glow2"></div>
        <div class="hero-content">
          <h1 class="hero-title">
            身边的专业宠物服务<br /><span class="hero-accent">触手可及</span>
          </h1>
          <p class="hero-desc">
            洗护美容、寄养托管、上门喂养、行为训练、疫苗体检……经 PetMate 严选认证的本地服务者，
            按体型毛长精准分档报价，在线选时段、到店核销，明明白白消费。
          </p>
        </div>
        <div class="search-bar">
          <div class="search-field">
            <span class="material-symbols-outlined search-icon">search</span>
            <div class="field-body">
              <span class="field-label">搜索服务</span>
              <input
                v-model="search.keyword"
                class="field-input"
                placeholder="如：洗护、寄养、疫苗、上门遛狗"
                @keyup.enter="loadServices()"
              />
            </div>
          </div>
          <button class="search-btn" @click="loadServices()">搜索服务</button>
        </div>
      </section>

      <!-- 带词跳转未命中时的降级提示 -->
      <div v-if="fallbackTip" class="fallback-tip">
        <span class="material-symbols-outlined">info</span>
        {{ fallbackTip }}
      </div>

      <!-- 一级分类 -->
      <div class="cat-tabs">
        <button
          class="cat-tab"
          :class="{ active: search.category === '' }"
          @click="pickCategory('')"
        >
          <span class="material-symbols-outlined">apps</span>
          全部
        </button>
        <button
          v-for="c in categories"
          :key="c.name"
          class="cat-tab"
          :class="{ active: search.category === c.name }"
          @click="pickCategory(c.name)"
        >
          <span class="material-symbols-outlined">{{ c.icon }}</span>
          {{ c.name }} <span class="cat-count">{{ c.count }}</span>
        </button>
      </div>

      <!-- 二级子类（选中一级后出现） -->
      <transition name="sub-fade">
        <div v-if="subCategories.length" class="sub-tabs">
          <button
            class="sub-chip"
            :class="{ active: search.subCategory === '' }"
            @click="pickSub('')"
          >
            全部{{ search.category }}
          </button>
          <button
            v-for="s in subCategories"
            :key="s.name"
            class="sub-chip"
            :class="{ active: search.subCategory === s.name }"
            @click="pickSub(s.name)"
          >
            {{ s.name }} <em>{{ s.count }}</em>
          </button>
        </div>
      </transition>

      <!-- 精细筛选条 -->
      <div class="filter-bar">
        <div class="filter-group">
          <span class="filter-label">适用宠物</span>
          <button
            v-for="p in PET_TYPES"
            :key="p.value"
            class="filter-chip"
            :class="{ active: search.petType === p.value }"
            @click="search.petType = p.value; loadServices()"
          >
            {{ p.label }}
          </button>
        </div>
        <div class="filter-group">
          <span class="filter-label">服务方式</span>
          <button
            v-for="m in MODES"
            :key="m.value"
            class="filter-chip"
            :class="{ active: search.serviceMode === m.value }"
            @click="search.serviceMode = m.value; loadServices()"
          >
            {{ m.label }}
          </button>
        </div>
        <div class="filter-group sort-group">
          <span class="filter-label">排序</span>
          <button
            v-for="s in SORTS"
            :key="s.value"
            class="filter-chip sort-chip"
            :class="{ active: search.sort === s.value }"
            @click="search.sort = s.value; loadServices()"
          >
            <span class="material-symbols-outlined">{{ s.icon }}</span>
            {{ s.label }}
          </button>
        </div>
      </div>

      <!-- 已选条件 -->
      <div v-if="activeFilters.length" class="active-bar">
        <span class="active-title">已选</span>
        <button
          v-for="f in activeFilters"
          :key="f.key"
          class="active-tag"
          @click="clearFilter(f.key)"
        >
          {{ f.label }}
          <span class="material-symbols-outlined">close</span>
        </button>
        <button class="active-reset" @click="resetAll">清空全部</button>
      </div>

      <!-- 列表 -->
      <PageSection :title="`为你找到 ${total} 个本地服务`" icon="content_cut">
        <div v-if="loading" class="state">服务加载中…</div>
        <PetEmptyState
          v-else-if="!services.length"
          icon="storefront"
          title="暂无匹配的服务"
          desc="换个关键词或放宽筛选条件试试～"
        />

        <div v-else class="service-grid">
          <article
            v-for="s in services"
            :key="s.id"
            class="service-card pet-card"
            @click="goDetail(s)"
          >
            <div class="service-cover">
              <img
                v-if="s.cover && !coverError[s.id]"
                :src="s.cover"
                :alt="s.name"
                @error="onCoverError(s.id)"
              />
              <div v-else class="cover-ph">
                <span class="material-symbols-outlined">{{ s.icon || 'pets' }}</span>
              </div>
              <span class="cat-badge">
                {{ s.subCategory || s.category }}
              </span>
              <span class="mode-badge" :class="s.serviceMode">
                <span class="material-symbols-outlined">
                  {{ s.serviceMode === 'onsite' ? 'directions_car' : 'storefront' }}
                </span>
                {{ modeLabel(s.serviceMode) }}
              </span>
            </div>

            <div class="service-body">
              <h3 class="service-name">{{ s.name }}</h3>

              <div class="meta-row">
                <span v-if="s.rating" class="meta-rating">
                  <span class="material-symbols-outlined">star</span>
                  {{ Number(s.rating).toFixed(1) }}
                </span>
                <span v-else class="meta-new">新上架</span>
                <span class="meta-dot">·</span>
                <span class="meta-sales">已售 {{ s.sales || 0 }}</span>
                <span v-if="s.reviewCount" class="meta-dot">·</span>
                <span v-if="s.reviewCount" class="meta-sales">{{ s.reviewCount }} 条评价</span>
              </div>

              <p class="service-store">
                <span class="material-symbols-outlined">store</span>
                {{ s.storeName }}
                <span class="pet-scope">{{ petLabel(s.petType) }}</span>
              </p>

              <div v-if="s.tags?.length" class="tag-row">
                <span v-for="t in s.tags.slice(0, 3)" :key="t" class="svc-tag">{{ t }}</span>
              </div>
              <p v-else-if="s.description" class="service-desc">{{ s.description }}</p>

              <div class="service-foot">
                <div class="price">
                  <span class="price-symbol">¥</span>
                  <span class="price-val">{{ priceRange(s) }}</span>
                  <span class="price-unit">
                    {{ hasRange(s) ? '' : '起' }}/{{ s.priceUnit || '次' }}
                  </span>
                </div>
                <button class="book-btn" @click.stop="book(s)">立即预约</button>
              </div>
            </div>
          </article>
        </div>
      </PageSection>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.service-view {
  min-height: 100vh;
  background: $color-background;
}
.service-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
}

// ============ Hero ============
.hero {
  position: relative;
  padding: $spacing-2xl;
  background: linear-gradient(135deg, rgba(255, 122, 50, 0.12), rgba(0, 96, 172, 0.08));
  border-radius: 32px;
  margin-bottom: $spacing-xl;
  overflow: hidden;
}
.hero-glow1 {
  position: absolute;
  top: -80px;
  right: -80px;
  width: 256px;
  height: 256px;
  background: rgba(255, 122, 50, 0.2);
  border-radius: $radius-full;
  filter: blur(64px);
}
.hero-glow2 {
  position: absolute;
  bottom: -80px;
  left: -80px;
  width: 320px;
  height: 320px;
  background: rgba(0, 96, 172, 0.12);
  border-radius: $radius-full;
  filter: blur(64px);
}
.hero-content {
  position: relative;
  z-index: 10;
  max-width: 680px;
  margin-bottom: $spacing-xl;
}
.hero-title {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: 800;
  color: $color-on-brand-container;
  line-height: 1.15;
  margin-bottom: $spacing-md;
}
.hero-accent {
  color: $color-brand;
}
.hero-desc {
  font-size: $font-size-body-lg;
  color: $color-on-surface-variant;
  line-height: 1.6;
}
.search-bar {
  position: relative;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-xs;
  background: $color-surface-container-lowest;
  border-radius: $radius-full;
  box-shadow: $shadow-lg;
  border: 1px solid rgba(223, 192, 179, 0.2);
}
.search-field {
  flex: 1;
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md $spacing-lg;
}
.search-icon {
  font-size: 22px;
  color: $color-brand;
}
.field-body {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.field-label {
  font-size: $font-size-label-sm;
  color: $color-outline;
}
.field-input {
  border: none;
  outline: none;
  background: transparent;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
  font-family: $font-family-body;
  width: 100%;
  padding: 0;
}
.search-btn {
  padding: $spacing-md $spacing-2xl;
  background: $color-brand;
  color: $color-on-brand;
  border-radius: $radius-full;
  font-weight: 700;
  cursor: pointer;
  transition: background $transition-fast;
  &:hover {
    background: $color-brand-dark;
  }
}

// ============ 降级提示 ============
.fallback-tip {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-default;
  background: rgba($color-brand, 0.1);
  color: $color-brand;
  font-size: $font-size-label-md;

  .material-symbols-outlined { font-size: 18px; }
}

// ============ 一级分类 ============
.cat-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}
.cat-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: $spacing-sm $spacing-lg;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  background: $color-surface-container-lowest;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined { font-size: 18px; }

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
    transform: translateY(-1px);
  }
  &.active {
    background: $color-brand;
    border-color: $color-brand;
    color: $color-on-brand;
    box-shadow: 0 4px 12px rgba(255, 122, 50, 0.3);
  }
  .cat-count {
    font-size: $font-size-label-sm;
    opacity: 0.7;
  }
}

// ============ 二级子类 ============
.sub-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
  padding: $spacing-sm $spacing-md;
  background: rgba($color-brand, 0.05);
  border-radius: $radius-lg;
  border: 1px dashed rgba($color-brand, 0.25);
}
.sub-chip {
  padding: 5px $spacing-md;
  border-radius: $radius-full;
  background: transparent;
  border: 1px solid transparent;
  font-size: $font-size-label-sm;
  font-weight: 600;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  em {
    font-style: normal;
    opacity: 0.55;
    margin-left: 3px;
  }
  &:hover { color: $color-brand; background: rgba(255, 255, 255, 0.7); }
  &.active {
    background: $color-surface-container-lowest;
    border-color: $color-brand;
    color: $color-brand;
    box-shadow: $shadow-sm;
  }
}
.sub-fade-enter-active,
.sub-fade-leave-active { transition: opacity $transition-fast, transform $transition-fast; }
.sub-fade-enter-from,
.sub-fade-leave-to { opacity: 0; transform: translateY(-4px); }

// ============ 筛选条 ============
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-lg;
  padding: $spacing-md $spacing-lg;
  margin-bottom: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1px solid rgba(223, 192, 179, 0.25);
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
}
.filter-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.sort-group { margin-left: auto; }
.filter-label {
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-outline;
  margin-right: 2px;
}
.filter-chip {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px $spacing-sm;
  border-radius: $radius-sm;
  border: 1px solid transparent;
  background: transparent;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined { font-size: 15px; }

  &:hover { background: rgba($color-brand, 0.08); color: $color-brand; }
  &.active {
    background: rgba($color-brand, 0.12);
    border-color: rgba($color-brand, 0.4);
    color: $color-brand;
    font-weight: 700;
  }
}

// ============ 已选条件 ============
.active-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
}
.active-title {
  font-size: $font-size-label-sm;
  color: $color-outline;
}
.active-tag {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px $spacing-sm;
  border-radius: $radius-full;
  background: rgba($color-brand, 0.12);
  color: $color-brand;
  font-size: $font-size-label-sm;
  font-weight: 600;
  cursor: pointer;
  border: none;
  transition: background $transition-fast;

  .material-symbols-outlined { font-size: 14px; }
  &:hover { background: rgba($color-brand, 0.22); }
}
.active-reset {
  padding: 3px $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-outline;
  background: none;
  border: none;
  cursor: pointer;
  text-decoration: underline;
  &:hover { color: $color-brand; }
}

.state {
  padding: $spacing-2xl;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
}

// ============ 卡片网格 ============
.service-grid {
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: $spacing-lg;
  @include desktop {
    grid-template-columns: repeat(2, 1fr);
  }
  @include desktop-large {
    grid-template-columns: repeat(3, 1fr);
  }
}
.service-card {
  display: flex;
  flex-direction: column;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-card;
  border: 1px solid rgba(223, 192, 179, 0.2);
  cursor: pointer;
  transition: transform $transition-base, box-shadow $transition-base, border-color $transition-base;
  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-lg;
    border-color: rgba(255, 122, 50, 0.4);
  }
}
.service-cover {
  position: relative;
  width: 100%;
  height: 200px;
  overflow: hidden;
  background: $color-surface-container-low;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 700ms ease;
  }
  &:hover img {
    transform: scale(1.08);
  }
}
.cover-ph {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, $color-brand-container, $color-secondary-container);
  .material-symbols-outlined {
    font-size: 56px;
    color: $color-on-brand-container;
    font-variation-settings: 'FILL' 1;
  }
}
.cat-badge {
  position: absolute;
  top: $spacing-md;
  left: $spacing-md;
  padding: 4px $spacing-md;
  background: rgba(255, 255, 255, 0.92);
  backdrop-filter: blur(8px);
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-brand;
  box-shadow: $shadow-sm;
}
.mode-badge {
  position: absolute;
  top: $spacing-md;
  right: $spacing-md;
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 4px $spacing-sm;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: #fff;
  backdrop-filter: blur(8px);
  background: rgba(0, 96, 172, 0.82);
  box-shadow: $shadow-sm;

  .material-symbols-outlined { font-size: 14px; }
  &.onsite { background: rgba(255, 122, 50, 0.88); }
}
.service-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: $spacing-lg;
}
.service-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  transition: color $transition-fast;
  @include text-ellipsis(1);
}
.service-card:hover .service-name {
  color: $color-brand;
}
.meta-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}
.meta-rating {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-weight: 700;
  color: #f5a524;
  .material-symbols-outlined {
    font-size: 15px;
    font-variation-settings: 'FILL' 1;
  }
}
.meta-new {
  padding: 1px 6px;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.12);
  color: $color-brand;
  font-weight: 700;
  font-size: 11px;
}
.meta-dot { opacity: 0.4; }
.service-store {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: $spacing-xs;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  .material-symbols-outlined {
    font-size: 16px;
  }
}
.pet-scope {
  margin-left: auto;
  padding: 1px 6px;
  border-radius: $radius-sm;
  background: $color-surface-container-low;
  font-size: 11px;
  color: $color-outline;
  white-space: nowrap;
}
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: $spacing-sm;
}
.svc-tag {
  padding: 2px $spacing-sm;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.08);
  border: 1px solid rgba($color-brand, 0.18);
  font-size: 11px;
  font-weight: 600;
  color: $color-brand;
}
.service-desc {
  margin-top: $spacing-sm;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: 1.6;
  @include text-ellipsis(2);
}
.service-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: $spacing-md;
}
.price {
  display: flex;
  align-items: baseline;
  gap: 1px;
  .price-symbol {
    font-size: $font-size-label-md;
    font-weight: 700;
    color: $color-brand;
  }
  .price-val {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 700;
    color: $color-brand;
  }
  .price-unit {
    font-size: $font-size-label-sm;
    color: $color-outline;
    margin-left: 2px;
  }
}
.book-btn {
  padding: $spacing-sm $spacing-lg;
  background: $color-on-surface;
  color: $color-surface;
  border-radius: $radius-md;
  font-weight: 700;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;
  &:hover {
    background: $color-brand;
  }
  &:active {
    transform: scale(0.95);
  }
}

// ============ 手机端响应式（≤768px）：用 rem 还原被 px-to-viewport 缩小的高度与内边距 ============
@include mobile {
  .service-container {
    padding-top: 1rem;
    padding-bottom: 1.5rem;
  }

  .hero {
    padding: 1.25rem;
    border-radius: 20px;
    margin-bottom: 1rem;
  }

  .hero-title {
    font-size: 1.75rem; // ≈28px，避免 48px 标题溢出
  }

  .hero-content {
    margin-bottom: 1rem;
  }

  .search-bar {
    flex-wrap: wrap;
  }

  .search-btn {
    width: 100%;
  }

  .filter-bar {
    gap: 0.75rem;
    padding: 0.75rem 1rem;
  }

  .service-cover {
    height: 11rem; // ≈176px
  }

  .service-body {
    padding: 0.75rem;
  }
}
</style>
