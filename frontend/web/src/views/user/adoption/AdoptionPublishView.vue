<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { adoptionApi } from '@/api/modules/adoption'
import { regionOptions } from '@/data/regions'
import { ensureVerified, isVerified } from '@/utils/requireVerified'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

// 实名认证门禁：未认证展示 gate UI（入口按钮已由 requireVerified() 弹警告弹窗拦截）
const blocked = ref(!isVerified())
onMounted(async () => {
  if (blocked.value) return
  const ok = await ensureVerified()
  if (!ok) {
    blocked.value = true
  }
})

const submitting = ref(false)
const imageInput = ref<HTMLInputElement | null>(null)

const form = reactive({
  title: '',
  petType: 'cat' as 'cat' | 'dog' | 'other',
  breed: '',
  gender: 1,
  age: '',
  city: '',
  contact: '',
  vaccinated: false,
  neutered: false,
  healthStatus: '',
  rescueStory: '',
})

// 所在地区：省 / 市 / 区（县）三级联动
const cityArr = ref<string[]>([])
function onCityChange() {
  form.city = cityArr.value.join('/')
}
// 兼容已带斜杠格式的回填（如编辑场景）
if (form.city && form.city.includes('/')) {
  cityArr.value = form.city.split('/')
}

// ===== 图片上传（逐项模型：即时预览 / 进度 / 失败重试 / 顺序保序）=====
const ALLOWED = ['image/jpeg', 'image/png', 'image/webp']
const MAX_IMAGES = 9
const MAX_SIZE = 10 * 1024 * 1024 // 单张上限 10MB

type ImgStatus = 'uploading' | 'done' | 'error'
interface ImgItem {
  id: number
  file: File
  localUrl: string
  status: ImgStatus
  progress: number
  url: string | null
  error?: string
}
const items = ref<ImgItem[]>([])
let seq = 0

const uploadingCount = computed(() => items.value.filter((i) => i.status === 'uploading').length)
const imageUrls = computed(() =>
  items.value.filter((i) => i.status === 'done' && i.url).map((i) => i.url as string),
)

function revokeItem(it: ImgItem) {
  if (it.localUrl.startsWith('blob:')) {
    try {
      URL.revokeObjectURL(it.localUrl)
    } catch {
      /* noop */
    }
  }
}

async function uploadItem(id: number) {
  // 始终按 id 从响应式数组里取代理对象操作，避免「改了原始对象却不触发重渲染」的坑
  const it = items.value.find((x) => x.id === id)
  if (!it) return
  it.status = 'uploading'
  it.progress = 0
  it.error = undefined
  try {
    const res = await adoptionApi.userUpload([it.file], (p) => {
      it.progress = p
    })
    if (res.urls && res.urls.length) {
      it.url = res.urls[0]
      it.status = 'done'
      it.progress = 100
    } else {
      throw new Error('上传未返回图片地址')
    }
  } catch (err: any) {
    it.status = 'error'
    it.error = err?.message || '上传失败'
    // 业务码错误已由全局拦截器统一提示，避免重复弹窗
    if (!err?.__handled) ElMessage.error(it.error)
  }
}

function onPickImages(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files || !files.length) return
  const remaining = MAX_IMAGES - items.value.length
  if (remaining <= 0) {
    ElMessage.warning(`最多上传 ${MAX_IMAGES} 张照片`)
    input.value = ''
    return
  }
  const picked = Array.from(files).slice(0, remaining)
  const rejected: string[] = []
  for (const f of picked) {
    if (!ALLOWED.includes(f.type)) {
      rejected.push(`${f.name}：格式不支持（仅 JPG / PNG / WebP）`)
      continue
    }
    if (f.size > MAX_SIZE) {
      rejected.push(`${f.name}：超过 10MB 上限`)
      continue
    }
    const it: ImgItem = {
      id: ++seq,
      file: f,
      localUrl: URL.createObjectURL(f),
      status: 'uploading',
      progress: 0,
      url: null,
    }
    // 必须先 push，再取数组里的响应式代理传给 uploadItem；
    // 直接传原始 `it` 不会触发重渲染，视图会卡在 uploading 状态。
    items.value.push(it)
    uploadItem(it.id)
  }
  if (rejected.length) {
    const head = rejected.slice(0, 3).join('；')
    ElMessage.warning(head + (rejected.length > 3 ? ` 等 ${rejected.length} 项已忽略` : ''))
  }
  input.value = ''
}

function removeImage(idx: number) {
  const it = items.value[idx]
  if (it) revokeItem(it)
  items.value.splice(idx, 1)
}

function retryImage(idx: number) {
  const it = items.value[idx]
  if (it) uploadItem(it.id)
}

function validate(): boolean {
  if (!form.title.trim()) {
    ElMessage.warning('请填写宠物名称 / 标题')
    return false
  }
  if (!form.contact.trim()) {
    ElMessage.warning('请填写联系电话')
    return false
  }
  if (!/^1[3-9]\d{9}$/.test(form.contact.trim())) {
    ElMessage.warning('请填写正确的手机号')
    return false
  }
  if (!imageUrls.value.length) {
    ElMessage.warning('请至少上传一张宠物照片')
    return false
  }
  return true
}

async function submit() {
  if (uploadingCount.value > 0) {
    ElMessage.warning('照片还在上传中，请稍候片刻～')
    return
  }
  if (!validate()) return
  submitting.value = true
  const payload = {
    title: form.title.trim(),
    petType: form.petType,
    breed: form.breed || undefined,
    gender: form.gender,
    age: form.age || undefined,
    city: form.city || undefined,
    contact: form.contact.trim(),
    vaccinated: form.vaccinated,
    neutered: form.neutered,
    healthStatus: form.healthStatus || undefined,
    rescueStory: form.rescueStory || undefined,
    images: imageUrls.value,
  }
  try {
    const res = await adoptionApi.userCreate(payload)
    ElMessage.success('发布成功，感谢你的善举！🐾')
    router.replace(`/adoption/detail/${res.id}`)
  } catch (err: any) {
    ElMessage.error(err?.message || '发布失败')
  } finally {
    submitting.value = false
  }
}

onBeforeUnmount(() => {
  items.value.forEach(revokeItem)
})
</script>

<template>
  <div class="publish-page">
    <!-- 实名认证门禁 -->
    <PetEmptyState
      v-if="blocked"
      class="verify-gate"
      icon="verified_user"
      title="请先完成实名认证"
      desc="发布领养信息需要先通过实名认证，以保障领养双方的权益与安全。"
    >
      <template #action>
        <button class="gate-btn" @click="router.push('/user/edit')">前往实名认证</button>
      </template>
    </PetEmptyState>

    <template v-else>
    <!-- 顶栏（渐变 Hero 横幅） -->
    <header class="pub-head">
      <button class="back" @click="router.back()">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <div class="pub-head-text">
        <h1 class="pub-title">发布领养</h1>
        <p class="pub-sub">为毛孩子找一个温暖的家 🐾</p>
      </div>
      <span class="pub-head-icon material-symbols-outlined">volunteer_activism</span>
    </header>

    <!-- 表单卡片 -->
    <section class="pub-card">
      <el-form label-position="top" class="pub-form">
        <div class="form-grid">
          <!-- 左栏：基本信息 -->
          <div class="field-group">
            <h3 class="group-title"><span class="dot"></span>基本信息</h3>
            <el-form-item label="名称 / 标题" required>
              <el-input v-model="form.title" placeholder="如：橘猫「豆豆」寻找温暖的家" maxlength="50" show-word-limit />
            </el-form-item>

            <div class="grid-2">
              <el-form-item label="宠物类型">
                <el-select v-model="form.petType" placeholder="请选择">
                  <el-option label="🐈 猫咪" value="cat" />
                  <el-option label="🐕 狗狗" value="dog" />
                  <el-option label="🐾 其他" value="other" />
                </el-select>
              </el-form-item>
              <el-form-item label="品种">
                <el-input v-model="form.breed" placeholder="如：中华田园橘猫" />
              </el-form-item>
            </div>

            <div class="grid-2">
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
            </div>

            <el-form-item label="所在地区">
              <el-cascader
                v-model="cityArr"
                :options="regionOptions"
                class="region-cascader"
                placeholder="选择省 / 市 / 区（县）"
                @change="onCityChange"
              />
            </el-form-item>
          </div>

          <!-- 右栏：健康与联系方式 -->
          <div class="field-group">
            <h3 class="group-title"><span class="dot"></span>健康与联系方式</h3>
            <el-form-item label="联系电话" required>
              <el-input v-model="form.contact" placeholder="便于志愿者联系你（手机号）" />
            </el-form-item>

            <el-form-item label="健康状态">
              <div class="switch-row">
                <el-checkbox v-model="form.vaccinated">已疫苗</el-checkbox>
                <el-checkbox v-model="form.neutered">已绝育</el-checkbox>
              </div>
            </el-form-item>

            <el-form-item label="健康状况说明">
              <el-input v-model="form.healthStatus" placeholder="如：健康，已驱虫，体格良好" />
            </el-form-item>

            <div class="hint-card">
              <span class="material-symbols-outlined">lightbulb</span>
              <p>清晰、真实的资料能大幅提升领养成功率，请尽量完整填写。</p>
            </div>
          </div>

          <!-- 通栏：故事与照片 -->
          <div class="field-group full">
            <h3 class="group-title"><span class="dot"></span>故事与照片</h3>
            <el-form-item label="救助 / 领养故事">
              <el-input
                v-model="form.rescueStory"
                type="textarea"
                :rows="4"
                maxlength="500"
                show-word-limit
                placeholder="简单描述它的性格、经历，让领养人更了解它～"
              />
            </el-form-item>

            <el-form-item label="宠物照片" required>
              <div class="img-zone">
                <div v-for="(it, i) in items" :key="it.id" class="img-thumb" :class="it.status">
                  <img :src="it.localUrl || it.url || ''" alt="pet" @error="(e:any)=>e.target.style.display='none'" />

                  <!-- 上传中：进度遮罩 -->
                  <div v-if="it.status === 'uploading'" class="img-mask">
                    <div class="img-progress">
                      <div class="img-progress-bar" :style="{ width: it.progress + '%' }"></div>
                    </div>
                    <span class="img-progress-text">{{ it.progress }}%</span>
                  </div>

                  <!-- 失败：重试遮罩 -->
                  <div v-else-if="it.status === 'error'" class="img-mask error">
                    <span class="img-error-text">{{ it.error }}</span>
                    <button class="img-retry" type="button" @click="retryImage(i)">
                      <span class="material-symbols-outlined">refresh</span> 重试
                    </button>
                  </div>

                  <!-- 成功：删除 + 封面标记 -->
                  <template v-else>
                    <button class="img-del" type="button" @click="removeImage(i)">
                      <span class="material-symbols-outlined">close</span>
                    </button>
                    <span v-if="i === 0" class="cover-badge">封面</span>
                  </template>
                </div>

                <button
                  class="img-add"
                  type="button"
                  :class="{ disabled: uploadingCount > 0 || items.length >= MAX_IMAGES }"
                  @click="items.length < MAX_IMAGES && !uploadingCount && imageInput?.click()"
                >
                  <span class="material-symbols-outlined">{{ uploadingCount ? 'progress_activity' : 'add_a_photo' }}</span>
                  <span>{{ uploadingCount ? '上传中' : '上传' }}</span>
                </button>
                <input ref="imageInput" type="file" accept="image/jpeg,image/png,image/webp" multiple hidden @change="onPickImages" />
              </div>
              <p class="img-tip">
                支持 JPG / PNG / WebP，单张 ≤ 10MB，最多 {{ MAX_IMAGES }} 张（第一张为封面）。
                已选 <b>{{ items.length }}/{{ MAX_IMAGES }}</b> 张。
              </p>
            </el-form-item>
          </div>
        </div>
      </el-form>

      <div class="actions">
        <button class="btn ghost" type="button" @click="router.back()">取消</button>
        <button class="btn primary" type="button" :disabled="submitting" @click="submit">
          <span v-if="submitting" class="material-symbols-outlined spin">progress_activity</span>
          <span>{{ submitting ? '发布中…' : '确认发布' }}</span>
        </button>
      </div>
    </section>
    </template>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.publish-page {
  min-height: 100vh;
  background:
    radial-gradient(120% 40% at 50% -14%, rgba($color-brand, 0.06) 0%, transparent 60%),
    $color-background;
  padding-bottom: $spacing-2xl;
  max-width: 1080px;
  margin: 0 auto;
  padding-left: $width-gutter-mobile;
  padding-right: $width-gutter-mobile;

  @include desktop {
    padding-left: $width-margin-desktop;
    padding-right: $width-margin-desktop;
  }
}

// ===== 实名认证门禁 =====
.verify-gate {
  margin-top: $spacing-xl;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  padding: $spacing-2xl $spacing-lg;
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  animation: pubFadeUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;

  .gate-icon {
    font-size: 48px;
    color: $color-brand;
    font-variation-settings: 'FILL' 1;
  }

  .gate-title {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: $font-weight-bold;
    color: $color-on-surface;
    margin: 0;
  }

  .gate-desc {
    font-size: $font-size-body-md;
    color: $color-text-secondary;
    margin: 0 0 $spacing-sm;
    max-width: 420px;
    line-height: $line-height-normal;
  }

  .gate-btn {
    padding: 12px 28px;
    background: linear-gradient(135deg, $color-brand 0%, $color-brand-dark 100%);
    color: $color-on-brand;
    border: none;
    border-radius: $radius-full;
    font-size: $font-size-label-md;
    font-weight: $font-weight-bold;
    cursor: pointer;
    box-shadow: 0 6px 18px rgba($color-brand, 0.3);
    transition: transform $transition-fast;

    &:hover {
      transform: translateY(-2px);
    }
  }
}

// ===== 顶栏（渐变 Hero 横幅）=====
.pub-head {
  position: relative;
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-xl $width-gutter-mobile $spacing-lg;
  margin: 0 (-$width-gutter-mobile);
  background: linear-gradient(135deg, $color-brand 0%, $color-brand-light 100%);
  overflow: hidden;
  animation: pubFadeUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;

  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background:
      radial-gradient(circle at 82% 18%, rgba(255, 255, 255, 0.3) 0%, transparent 46%),
      radial-gradient(circle at 8% 100%, rgba(255, 182, 148, 0.45) 0%, transparent 52%);
  }

  @include desktop {
    padding-left: $width-margin-desktop;
    padding-right: $width-margin-desktop;
    margin-left: (-$width-margin-desktop);
    margin-right: (-$width-margin-desktop);
  }
}

.pub-head .back {
  position: relative;
  z-index: 1;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.22);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 24px;
  }
  &:hover {
    background: rgba(255, 255, 255, 0.34);
    transform: translateY(-1px);
  }
}

.pub-head-text {
  position: relative;
  z-index: 1;
}

.pub-title {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: $font-weight-extrabold;
  color: #fff;
  margin: 0;
  letter-spacing: -0.02em;

  @media (max-width: 480px) {
    font-size: $font-size-headline-lg;
  }
}

.pub-sub {
  font-size: $font-size-body-md;
  color: rgba(255, 255, 255, 0.95);
  margin: 4px 0 0;
}

.pub-head-icon {
  position: relative;
  z-index: 1;
  margin-left: auto;
  font-size: 40px;
  color: rgba(255, 255, 255, 0.5);
  font-variation-settings: 'FILL' 1;

  @media (max-width: 480px) {
    display: none;
  }
}

// ===== 卡片 =====
.pub-card {
  position: relative;
  margin-top: $spacing-lg;
  background:
    linear-gradient(180deg, rgba($color-brand, 0.05) 0%, transparent 160px),
    $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  box-shadow: $shadow-lg, 0 0 0 1px rgba($color-brand, 0.08);
  border: 1px solid $color-outline-variant;
  animation: pubFadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) 0.08s both;

  @include desktop {
    padding: $spacing-xl;
  }
}

// ===== 2 栏表单网格 =====
.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-xl;

  @media (max-width: 860px) {
    grid-template-columns: 1fr;
    gap: $spacing-lg;
  }
}

.field-group {
  min-width: 0;

  &.full {
    grid-column: 1 / -1;
  }
}

.group-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0 0 $spacing-md;

  .dot {
    width: 6px;
    height: 18px;
    border-radius: $radius-full;
    background: linear-gradient(180deg, $color-brand 0%, $color-brand-dark 100%);
  }
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-md;

  @media (max-width: 520px) {
    grid-template-columns: 1fr;
  }
}

.switch-row {
  display: flex;
  gap: $spacing-lg;
}

.hint-card {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  margin-top: $spacing-sm;
  padding: $spacing-md;
  background: rgba($color-brand, 0.06);
  border: 1px dashed rgba($color-brand, 0.25);
  border-radius: $radius-default;

  .material-symbols-outlined {
    font-size: 20px;
    color: $color-brand;
    flex-shrink: 0;
    margin-top: 1px;
  }
  p {
    margin: 0;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    line-height: $line-height-normal;
  }
}

// ===== 表单深度样式（与主题融合）=====
.pub-form {
  :deep(.el-form-item__label) {
    font-size: $font-size-label-md;
    font-weight: $font-weight-semibold;
    color: $color-on-surface;
    padding-bottom: 4px;
  }
  :deep(.el-input__wrapper),
  :deep(.el-textarea__inner),
  :deep(.el-select__wrapper) {
    background: $color-surface-container-low;
    box-shadow: 0 0 0 1px $color-outline-variant inset;
    border-radius: $radius-default;
    font-family: inherit;
  }
  :deep(.el-input__wrapper.is-focus),
  :deep(.el-textarea__inner:focus),
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 2px $color-brand inset;
  }
  :deep(.el-cascader) {
    width: 100%;
  }
  :deep(.el-cascader .el-input__wrapper) {
    background: $color-surface-container-low;
    box-shadow: 0 0 0 1px $color-outline-variant inset;
    border-radius: $radius-default;
    font-family: inherit;
  }
  :deep(.el-cascader .el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 2px $color-brand inset;
  }
  :deep(.el-cascader .el-input__inner) {
    font-family: inherit;
    font-size: $font-size-body-md;
  }
  :deep(.el-radio),
  :deep(.el-checkbox) {
    color: $color-on-surface;
    font-family: inherit;
  }
  :deep(.el-radio__input.is-checked .el-radio__inner),
  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    background: $color-brand;
    border-color: $color-brand;
  }
}

// ===== 图片上传 =====
.img-zone {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.img-thumb {
  position: relative;
  width: 96px;
  height: 96px;
  border-radius: $radius-default;
  overflow: hidden;
  border: 1px solid $color-outline-variant;
  background: $color-surface-container;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .img-del {
    position: absolute;
    top: 3px;
    right: 3px;
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
    padding: 0;
    .material-symbols-outlined {
      font-size: 14px;
    }
  }

  .cover-badge {
    position: absolute;
    left: 0;
    bottom: 0;
    width: 100%;
    text-align: center;
    font-size: 11px;
    font-weight: $font-weight-semibold;
    color: #fff;
    background: linear-gradient(0deg, rgba(0, 0, 0, 0.6), transparent);
    padding: 2px 0;
  }
}

.img-add {
  width: 96px;
  height: 96px;
  border: 1px dashed $color-outline;
  border-radius: $radius-default;
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
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 24px;
  }
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
  &.disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.img-tip {
  margin: $spacing-xs 0 0;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  line-height: $line-height-normal;

  b {
    color: $color-brand;
  }
}

// ===== 上传中 / 失败遮罩 =====
.img-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  text-align: center;

  .img-progress {
    width: 80%;
    height: 6px;
    border-radius: $radius-full;
    background: rgba(255, 255, 255, 0.25);
    overflow: hidden;
  }
  .img-progress-bar {
    height: 100%;
    background: linear-gradient(90deg, $color-brand 0%, $color-brand-light 100%);
    transition: width 0.2s ease;
  }
  .img-progress-text {
    font-size: 12px;
    font-weight: $font-weight-semibold;
  }

  &.error {
    background: rgba(180, 30, 30, 0.72);
    gap: 6px;

    .img-error-text {
      font-size: 11px;
      line-height: $line-height-normal;
      word-break: break-all;
    }
    .img-retry {
      display: inline-flex;
      align-items: center;
      gap: 2px;
      padding: 3px 10px;
      border: none;
      border-radius: $radius-full;
      background: #fff;
      color: $color-emergency;
      font-size: 11px;
      font-weight: $font-weight-bold;
      font-family: inherit;
      cursor: pointer;

      .material-symbols-outlined {
        font-size: 14px;
      }
    }
  }
}

// ===== 操作按钮 =====
.actions {
  display: flex;
  gap: $spacing-md;
  margin-top: $spacing-xl;
}

.btn {
  flex: 1;
  height: 48px;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  font-family: inherit;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: transform $transition-fast, box-shadow $transition-fast;

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.btn.ghost {
  background: $color-surface-container-low;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

.btn.primary {
  background: linear-gradient(135deg, $color-brand 0%, $color-brand-dark 100%);
  color: $color-on-brand;
  border: none;
  box-shadow: 0 6px 18px rgba($color-brand, 0.3);
  &:hover:not(:disabled) {
    transform: translateY(-2px);
  }
  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }
}

.spin {
  animation: pub-spin 1s linear infinite;
}
@keyframes pub-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes pubFadeUp {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .pub-head,
  .pub-card {
    animation: none;
  }
}
</style>
