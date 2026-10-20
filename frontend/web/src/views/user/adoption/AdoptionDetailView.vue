<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adoptionApi, type Adoption } from '@/api/modules/adoption'
import { requireVerified } from '@/utils/requireVerified'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const notFound = ref(false)
const raw = ref<Adoption | null>(null)
// 多图画廊：当前展示的图片索引（0 = 封面）
const activeIndex = ref(0)

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

const gallery = computed<string[]>(() => (raw.value ? parseImages(raw.value.images) : []))
const cover = computed(() => gallery.value[0] || '')
// 当前主图：用户点击缩略图切换时改变
const activeImage = computed(() => gallery.value[activeIndex.value] || cover.value)
const name = computed(() => raw.value?.title || '')
const petType = computed(() => raw.value?.petType || 'other')
// 性别：1=公 2=母 0=未知（与发布表单一致；原来未知(0)被误判为「公」）
type GenderKind = 'male' | 'female' | 'unknown'
const gender = computed<GenderKind>(() => {
  if (raw.value?.gender === 2) return 'female'
  if (raw.value?.gender === 1) return 'male'
  return 'unknown'
})
const breed = computed(() => raw.value?.breed || '')
const age = computed(() => raw.value?.age || '')
const vaccinated = computed(() => !!raw.value?.vaccinated)
const sterilized = computed(() => !!raw.value?.neutered)
const healthStatus = computed(() => raw.value?.healthStatus || '健康状况良好')
const rescueStory = computed(() => raw.value?.rescueStory || '暂无更多故事。')
const contact = computed(() => raw.value?.contact || '')
const city = computed(() => raw.value?.city || '')
const available = computed(() => raw.value?.status === 1)

const tags = computed<string[]>(() => {
  const t: string[] = []
  if (vaccinated.value) t.push('已疫苗')
  if (sterilized.value) t.push('已绝育')
  if (breed.value) t.push(breed.value)
  if (healthStatus.value) t.push(healthStatus.value)
  return t
})

const typeLabel = computed(() => ({ dog: '狗狗', cat: '猫咪', other: '其他' }[petType.value]))

const requirements = [
  '有独立居住空间，全家同意养宠',
  '有稳定的经济来源',
  '科学喂养，不离不弃',
]
const process = [
  '在线提交领养申请',
  '志愿者 1-3 个工作日内电话回访',
  '安排视频家访或线下面访',
  '签订领养协议',
  '上门 / 站点接回新成员',
]

async function load() {
  loading.value = true
  notFound.value = false
  try {
    raw.value = await adoptionApi.detail(Number(route.params.id))
    activeIndex.value = 0
  } catch {
    notFound.value = true
    raw.value = null
  } finally {
    loading.value = false
  }
}

function back() {
  router.back()
}

async function apply() {
  if (!raw.value) return
  // 实名认证门禁：未实名弹警告弹窗，不继续
  if (!requireVerified()) return
  try {
    const { value: nm } = await ElMessageBox.prompt('请填写联系人姓名', '提交领养申请', {
      inputValidator: (v) => (!!v && v.trim().length > 0) || '姓名不能为空',
      inputErrorMessage: '姓名不能为空',
    })
    const { value: phone } = await ElMessageBox.prompt('请填写手机号', '提交领养申请', {
      inputPattern: /^1[3-9]\d{9}$/,
      inputErrorMessage: '手机号格式不正确',
    })
    await adoptionApi.apply(raw.value.id, { name: nm.trim(), phone })
    ElMessage.success('已提交领养申请，志愿者将尽快与您联系')
  } catch (err: any) {
    if (err !== 'cancel' && err?.action !== 'cancel') {
      ElMessage.error(err?.message || '申请失败')
    }
  }
}

async function share() {
  const url = window.location.href
  const title = name.value || '宠物领养'
  const text = `来看看这个等待被领养的毛孩子：${name.value}`
  // 移动端优先调用系统分享面板
  if (typeof navigator !== 'undefined' && typeof navigator.share === 'function') {
    try {
      await navigator.share({ title, text, url })
      return
    } catch (e: any) {
      if (e && e.name === 'AbortError') return // 用户主动取消，不提示
    }
  }
  // 兜底：复制链接到剪贴板
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(url)
    } else {
      throw new Error('clipboard unavailable')
    }
    ElMessage.success('分享链接已复制到剪贴板')
  } catch {
    // 再兜底：临时文本域 + execCommand
    try {
      const ta = document.createElement('textarea')
      ta.value = url
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      document.body.removeChild(ta)
      ElMessage.success('分享链接已复制到剪贴板')
    } catch {
      ElMessage.info('请手动复制地址栏链接分享：' + url)
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="adoption-detail">
    <!-- 顶栏 -->
    <header class="topbar no-vw">
      <button class="icon-btn" @click="back">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <h1 class="topbar-title">领养详情</h1>
      <button class="icon-btn" @click="share">
        <span class="material-symbols-outlined">share</span>
      </button>
    </header>

    <main class="page-main" v-if="loading">
      <div class="state-block">
        <span class="material-symbols-outlined spin">progress_activity</span>
        <p>加载中…</p>
      </div>
    </main>

    <main class="page-main" v-else-if="notFound">
      <PetEmptyState icon="pets" title="未找到该领养信息">
        <template #action>
          <button class="retry-btn" @click="router.push('/adoption')">返回列表</button>
        </template>
      </PetEmptyState>
    </main>

    <main class="page-main" v-else>
      <!-- Hero：左侧 cover + 缩略条纵向堆叠，右侧紧凑信息卡 -->
      <section class="hero-card pet-card">
        <div class="hero-gallery">
          <div class="cover">
            <img v-if="activeImage" :src="activeImage" :alt="name" @error="(e:any)=>e.target.style.display='none'" />
            <span v-else class="material-symbols-outlined cover-ph">pets</span>
            <span class="status-pill" :class="adopted ? 'adopted' : available ? 'available' : 'pending'">
              {{ adopted ? '已领养' : available ? '可领养' : '审核中' }}
            </span>
            <span v-if="gallery.length > 1" class="gallery-count">
              <span class="material-symbols-outlined">photo_library</span>
              {{ activeIndex + 1 }} / {{ gallery.length }}
            </span>
          </div>
          <!-- 多图画廊：缩略条独立在 cover 下方，不覆盖主图 -->
          <div class="thumbs" v-if="gallery.length > 1">
            <button
              v-for="(img, i) in gallery"
              :key="i"
              type="button"
              class="thumb"
              :class="{ active: i === activeIndex }"
              @click="activeIndex = i"
              :aria-label="`查看第 ${i + 1} 张图片`"
            >
              <img :src="img" :alt="`图片 ${i + 1}`" @error="(e:any)=>e.target.style.display='none'" />
            </button>
          </div>
        </div>

        <div class="hero-body">
          <div class="head">
            <span class="type-pill">{{ typeLabel }}</span>
            <span class="city-chip" v-if="city">
              <span class="material-symbols-outlined">location_on</span>
              {{ city }}
            </span>
          </div>

          <h2 class="name">
            <span class="name-text">{{ name }}</span>
            <span class="gender">
              <span class="material-symbols-outlined">{{ gender === 'female' ? 'female' : gender === 'male' ? 'male' : 'help' }}</span>
            </span>
          </h2>

          <p class="meta">
            <span class="meta-item">{{ breed || '混血' }}</span>
            <span class="meta-sep" aria-hidden="true">·</span>
            <span class="meta-item">{{ age || '年龄未知' }}</span>
          </p>

          <div class="tags" v-if="tags.length">
            <span v-for="t in tags" :key="t" class="tag">{{ t }}</span>
          </div>

          <button v-if="available" class="hero-cta" @click="apply">立即申请领养</button>
          <button v-else-if="adopted" class="hero-cta disabled" disabled>已被领养</button>
          <button v-else class="hero-cta disabled" disabled>审核中</button>
        </div>
      </section>

      <!-- 主栏 + 侧栏 -->
      <div class="detail-grid">
        <div class="detail-main">
          <PageSection class="block-card" title="健康档案" icon="health_and_safety">
            <div class="health-grid">
              <div class="health-item" :class="{ done: vaccinated }">
                <span class="material-symbols-outlined">vaccines</span>
                <div>
                  <p class="health-label">疫苗</p>
                  <p class="health-value">{{ vaccinated ? '已完成' : '未完成' }}</p>
                </div>
              </div>
              <div class="health-item" :class="{ done: sterilized }">
                <span class="material-symbols-outlined">medical_services</span>
                <div>
                  <p class="health-label">绝育</p>
                  <p class="health-value">{{ sterilized ? '已绝育' : '未绝育' }}</p>
                </div>
              </div>
              <div class="health-item">
                <span class="material-symbols-outlined">monitor_heart</span>
                <div>
                  <p class="health-label">健康状态</p>
                  <p class="health-value">{{ healthStatus }}</p>
                </div>
              </div>
            </div>
          </PageSection>

          <PageSection class="block-card" title="它的故事" icon="auto_stories">
            <p class="story">{{ rescueStory }}</p>
          </PageSection>
        </div>

        <aside class="detail-side">
          <PageSection class="block-card side-card" title="领养要求" icon="rule">
            <ul class="req-list">
              <li v-for="(r, idx) in requirements" :key="idx">
                <span class="bullet">·</span>
                <span>{{ r }}</span>
              </li>
            </ul>
          </PageSection>

          <PageSection class="block-card side-card" title="领养流程" icon="format_list_numbered">
            <ol class="process-list">
              <li v-for="(p, idx) in process" :key="idx">
                <span class="step-num">{{ idx + 1 }}</span>
                <span>{{ p }}</span>
              </li>
            </ol>
          </PageSection>

          <PageSection class="block-card side-card shelter-card" title="救助站信息" icon="contact_phone">
            <div class="shelter-info">
              <div>
                <p class="shelter-name">公益救助站</p>
                <p class="shelter-addr">
                  <span class="material-symbols-outlined">location_on</span>
                  {{ city }}
                </p>
              </div>
              <a v-if="contact" class="call-btn" :href="`tel:${contact}`">
                <span class="material-symbols-outlined">call</span>
                联系
              </a>
              <span v-else class="call-none">暂无联系方式</span>
            </div>
          </PageSection>
        </aside>
      </div>

      <div class="bottom-spacer"></div>
    </main>

    <!-- 底部操作（移动端固定） -->
    <footer class="bottom-bar no-vw" v-if="!loading && !notFound">
      <button class="consult-btn" @click="router.push('/consult')">
        <span class="material-symbols-outlined">forum</span>
        <span>咨询</span>
      </button>
      <button v-if="available" class="apply-btn" @click="apply">立即申请领养</button>
      <button v-else-if="adopted" class="apply-btn disabled" disabled>已被领养</button>
      <button v-else class="apply-btn disabled" disabled>审核中</button>
    </footer>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.adoption-detail {
  min-height: 100vh;
  background:
    radial-gradient(120% 42% at 50% -12%, rgba($color-brand, 0.07) 0%, transparent 60%),
    $color-background;
  padding-bottom: 100px;
  // 兜底：防止任何子元素意外把页面撑宽导致横向滚动
  overflow-x: clip;
}

// ====== Topbar（毛玻璃）======
.topbar {
  display: flex;
  align-items: center;
  height: 64px;
  padding: 0 $width-gutter-mobile;
  background: rgba($color-surface-container-lowest, 0.72);
  backdrop-filter: blur(16px) saturate(160%);
  -webkit-backdrop-filter: blur(16px) saturate(160%);
  border-bottom: 1px solid $color-outline-variant;
  position: sticky;
  top: 0;
  z-index: 10;

  @include desktop {
    padding: 0 $width-margin-desktop;
  }
}

.topbar-title {
  flex: 1;
  text-align: center;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
}

.icon-btn {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  background: transparent;
  border: none;
  color: $color-brand;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  &:hover {
    background: rgba($color-brand, 0.1);
  }

  .material-symbols-outlined {
    font-size: 24px;
  }
}

// ====== Page main（宽屏）======
.page-main {
  max-width: 1080px;
  margin: 0 auto;
  padding: 0 $width-gutter-mobile;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;

  @include desktop {
    padding: 0 $width-margin-desktop;
  }
}

// ====== Hero card（2 栏：左 cover+缩略条 纵向 / 右 信息卡）======
.hero-card {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-card, 0 0 0 1px rgba($color-brand, 0.1);
  animation: detailFadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) both;
  min-width: 0;
}

.hero-gallery {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: $color-surface-container;
}

.cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: $color-surface-container;
  max-height: 520px;
  // 关键：overflow:hidden + 图片绝对定位脱离 flex 流，避免 flex item 的
  // min-height:auto 被图片原始比例撑开，导致不同比例图片主图高度跳变(页面乱)
  overflow: hidden;
  min-width: 0;

  img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.cover-ph {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 64px;
  color: $color-on-surface-variant;
}

// ====== 多图画廊缩略条（独立在 cover 下方，不覆盖主图）======
.thumbs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-lowest;
  border-top: 1px solid rgba($color-outline-variant, 0.6);
  scrollbar-width: thin;
  &::-webkit-scrollbar {
    height: 4px;
  }
  &::-webkit-scrollbar-thumb {
    background: rgba($color-outline, 0.3);
    border-radius: 2px;
  }
}

.thumb {
  flex: 0 0 auto;
  width: 64px;
  height: 64px;
  border-radius: $radius-default;
  overflow: hidden;
  border: 2px solid transparent;
  padding: 0;
  cursor: pointer;
  background: $color-surface-container;
  transition: border-color $transition-fast, transform $transition-fast, box-shadow $transition-fast;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }

  &:hover {
    transform: translateY(-2px);
    border-color: rgba($color-brand, 0.45);
  }

  &.active {
    border-color: $color-brand;
    box-shadow: 0 0 0 2px rgba($color-brand, 0.18);
  }
}

.status-pill {
  position: absolute;
  top: $spacing-md;
  left: $spacing-md;
  padding: 6px 16px;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  color: #fff;
  box-shadow: $shadow-md;
  z-index: 2;
}

.status-pill.available {
  background: $color-success;
}

.status-pill.pending {
  background: $color-warning;
}

.status-pill.adopted {
  background: #9aa0a6;
}

.gallery-count {
  position: absolute;
  top: $spacing-md;
  right: $spacing-md;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: $radius-full;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  color: #fff;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  z-index: 2;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

// ====== Hero 右侧信息卡 ======
.hero-body {
  padding: $spacing-xl $spacing-lg;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: $spacing-sm;
  min-width: 0;
}

.head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.type-pill {
  padding: 4px 12px;
  background: $color-brand-container;
  color: $color-on-brand-container;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  white-space: nowrap;
}

.city-chip {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 4px 10px;
  background: $color-surface-container-low;
  color: $color-text-secondary;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  white-space: nowrap;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

.name {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  min-width: 0;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md; // 24px,避免 32px 标题在窄列里被折成 6 行
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
  line-height: 1.3;
}

.name-text {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.gender .material-symbols-outlined {
  font-size: 22px;
  color: $color-danger;
  font-variation-settings: 'FILL' 1;
  flex-shrink: 0;
}

.meta {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  margin: 0;
}

.meta-item {
  overflow-wrap: break-word;
}

.meta-sep {
  color: $color-text-tertiary;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}

.tag {
  padding: 3px 10px;
  background: rgba($color-brand, 0.1);
  color: $color-brand-dark;
  border-radius: $radius-sm;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-medium;
  overflow-wrap: break-word;
  white-space: nowrap;
}

.hero-cta {
  margin-top: $spacing-sm;
  width: 100%;
  padding: 14px 0;
  background: linear-gradient(90deg, $color-brand 0%, #ff9461 100%);
  color: $color-on-brand;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  border: none;
  border-radius: $radius-full;
  font-family: inherit;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba($color-brand, 0.3);
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 12px 26px rgba($color-brand, 0.38);
  }

  &.disabled {
    background: $color-surface-container-high;
    color: $color-text-tertiary;
    box-shadow: none;
    cursor: not-allowed;
  }
}

// ====== Detail grid ======
.detail-grid {
  display: grid;
  grid-template-columns: 1.7fr 1fr;
  gap: $spacing-md;
  align-items: start;
}

.detail-main {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  min-width: 0;
}

.detail-side {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  position: sticky;
  top: 80px;
  min-width: 0;
}

// ====== Block card ======
.block-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  box-shadow: $shadow-card, 0 0 0 1px rgba($color-outline-variant, 0.5);
  animation: detailFadeUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
  min-width: 0;
}

.side-card {
  padding: $spacing-md $spacing-lg;
}

.block-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0 0 $spacing-md;
  min-width: 0;
  flex-wrap: wrap;

  > span:last-child {
    min-width: 0;
    overflow-wrap: anywhere;
  }
}

.title-icon {
  font-size: 22px !important;
  color: $color-brand;
}

// ====== Health grid ======
.health-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-sm;
}

.health-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-default;
  color: $color-text-tertiary;
  transition: transform $transition-fast;

  &:hover {
    transform: translateY(-2px);
  }

  .material-symbols-outlined {
    font-size: 22px;
    color: $color-text-tertiary;
    background: $color-surface-container;
    width: 40px;
    height: 40px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    transition: background $transition-fast;
  }
}

.health-item.done {
  background: rgba($color-success, 0.1);
  color: $color-success;

  .material-symbols-outlined {
    color: $color-success;
    font-variation-settings: 'FILL' 1;
  }
}

.health-label {
  font-size: $font-size-label-sm;
  margin: 0;
  color: inherit;
  opacity: 0.8;
}

.health-value {
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  margin: 2px 0 0;
  color: inherit;
}

// ====== Text content ======
.desc,
.story {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: $line-height-relaxed;
  margin: 0;
  white-space: pre-line;
  overflow-wrap: break-word;
}

// ====== Requirements ======
.req-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.req-list li {
  display: flex;
  gap: $spacing-sm;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  line-height: $line-height-normal;
}

.bullet {
  color: $color-brand;
  font-size: 20px;
  font-weight: $font-weight-bold;
  flex-shrink: 0;
}

// ====== Process ======
.process-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.process-list li {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: $font-size-body-md;
  color: $color-on-surface;
}

.step-num {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand;
  color: $color-on-brand;
  border-radius: 50%;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  flex-shrink: 0;
}

// ====== Shelter info ======
.shelter-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $spacing-md;
}

.shelter-name {
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  margin: 0;
}

.shelter-addr {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 4px 0 0;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

.call-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  background: $color-secondary;
  color: $color-on-secondary;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
  text-decoration: none;

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.call-none {
  font-size: $font-size-label-md;
  color: $color-text-tertiary;
}

.bottom-spacer {
  height: 20px;
}

// ====== Bottom bar（仅移动端）======
.bottom-bar {
  display: none;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  gap: $spacing-sm;
  padding: $spacing-sm $width-gutter-mobile;
  background: rgba($color-surface-container-lowest, 0.82);
  backdrop-filter: blur(18px) saturate(160%);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  border-top: 1px solid $color-outline-variant;
  box-shadow: 0 -6px 22px rgba(0, 0, 0, 0.08);
  z-index: 20;
}

.consult-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 4px 12px;
  background: transparent;
  border: none;
  color: $color-text-secondary;
  font-size: $font-size-label-sm;
  font-family: inherit;
  cursor: pointer;

  .material-symbols-outlined {
    font-size: 22px;
  }
}

.apply-btn {
  flex: 1;
  padding: 14px 0;
  background: linear-gradient(90deg, $color-brand 0%, #ff9461 100%);
  color: $color-on-brand;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  border: none;
  border-radius: $radius-full;
  font-family: inherit;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba($color-brand, 0.3);
}

.apply-btn.disabled {
  background: $color-surface-container-high;
  color: $color-text-tertiary;
  box-shadow: none;
  cursor: not-allowed;
}

// ====== State ======
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-3xl 0;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 48px;
    color: $color-text-tertiary;
  }

  p {
    margin: 0;
    font-size: $font-size-body-md;
  }

  .spin {
    animation: state-spin 1s linear infinite;
  }
}

.retry-btn {
  margin-top: $spacing-sm;
  padding: 8px 20px;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
}

// ====== 响应式 ======
// 平板及以下：Hero 与详情栅格统一在 880px 折叠为单列，避免中屏两栏挤压
@media (max-width: 880px) {
  .hero-card {
    grid-template-columns: 1fr;
  }
  .cover {
    aspect-ratio: 16 / 10;
  }
  .hero-body {
    padding: $spacing-lg;
  }
  .detail-grid {
    grid-template-columns: 1fr;
  }
  .detail-side {
    position: static;
  }
}

@media (max-width: 720px) {
  // 移动端申请入口统一用底部固定操作栏，隐藏正文内重复按钮
  .hero-cta {
    display: none;
  }
  .health-grid {
    grid-template-columns: 1fr;
  }
  .thumb {
    width: 56px;
    height: 56px;
  }
  .bottom-bar {
    display: flex;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-card,
  .block-card {
    animation: none;
  }
}

@keyframes state-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes detailFadeUp {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
