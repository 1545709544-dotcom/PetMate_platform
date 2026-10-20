<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { petApi, type PetCategory, type PetBreed, type PetGender } from '@/api/modules/pet'

const router = useRouter()

// ============ Wizard 状态 ============
const step = ref(1)
const totalSteps = 3
const submitting = ref(false)

const stepDefs = [
  { num: 1, title: '基本信息' },
  { num: 2, title: '品种与体征' },
  { num: 3, title: '健康初始数据' },
]

// ============ 表单数据 ============
const form = ref({
  // Step 1
  avatar: '',
  name: '',
  birthday: '',
  categoryId: null as number | null,
  gender: '' as '' | PetGender,
  // Step 2
  breedId: null as number | null,
  breedCustom: '', // 选「其他」时手填的品种名
  weightKg: '',
  isNeutered: false,
  // Step 3
  vaccineDone: false,
  dewormDone: false,
  allergies: '',
  notes: '',
})

// ============ 字典数据 ============
const categories = ref<PetCategory[]>([])
const breeds = ref<PetBreed[]>([])
const dictionaryLoading = ref(false)

// 「其他」自定义品种哨兵值：breedId 传 -1,品种名由 breedCustom 提供
const OTHER_BREED_ID = -1
const isOtherBreed = computed(() => form.value.breedId === OTHER_BREED_ID)

// 分类完全来自后端字典（/pet/categories），不在前端写死白名单，保证与数据库一致（含兔等）
const displayCategories = computed(() => categories.value)

// 图标加载失败时的兜底（离线/外链不可达时回退到通用爪印）
const brokenIcons = ref<Set<number>>(new Set())
function onIconError(id: number) {
  const s = new Set(brokenIcons.value)
  s.add(id)
  brokenIcons.value = s
}

const selectedCategory = computed(() => {
  return categories.value.find((c) => c.id === form.value.categoryId)
})

async function loadCategories() {
  dictionaryLoading.value = true
  try {
    const list = await petApi.categories()
    categories.value = list || []
  } catch (e) {
    ElMessage.error('宠物分类加载失败')
  } finally {
    dictionaryLoading.value = false
  }
}

// 选择品种时,联动 breeds
watch(
  () => form.value.categoryId,
  async (newId, oldId) => {
    if (newId === oldId) return
    if (!newId) {
      breeds.value = []
      form.value.breedId = null
      return
    }
    try {
      const list = await petApi.breeds(newId)
      breeds.value = list || []
      form.value.breedId = null
    } catch (e) {
      breeds.value = []
      form.value.breedId = null
      ElMessage.error('品种列表加载失败')
    }
  },
)

onMounted(loadCategories)

// ============ 校验 ============
const today = new Date().toISOString().slice(0, 10)

const step1Valid = computed(() => {
  if (!form.value.name.trim()) return false
  if (form.value.name.trim().length > 20) return false
  if (!form.value.birthday) return false
  if (form.value.birthday > today) return false
  if (form.value.categoryId == null) return false
  if (form.value.gender === '' || form.value.gender == null) return false
  return true
})

const step2Valid = computed(() => {
  if (form.value.breedId == null) return false
  // 选「其他」时必须填写自定义品种名(1~64 字)
  if (isOtherBreed.value && form.value.breedCustom.trim().length === 0) return false
  if (isOtherBreed.value && form.value.breedCustom.trim().length > 64) return false
  const w = Number(form.value.weightKg)
  if (!form.value.weightKg || isNaN(w) || w <= 0 || w > 200) return false
  return true
})

function canNext(): boolean {
  if (step.value === 1) return step1Valid.value
  if (step.value === 2) return step2Valid.value
  return true
}

function next() {
  if (step.value === 1 && !step1Valid.value) {
    if (!form.value.name.trim()) ElMessage.warning('请输入宠物姓名')
    else if (!form.value.birthday) ElMessage.warning('请选择出生日期')
    else if (form.value.birthday > today) ElMessage.warning('出生日期不能晚于今天')
    else if (form.value.categoryId == null) ElMessage.warning('请选择宠物类型')
    else if (form.value.gender === '') ElMessage.warning('请选择性别')
    return
  }
  if (step.value === 2 && !step2Valid.value) {
    if (form.value.breedId == null) ElMessage.warning('请选择品种')
    else if (isOtherBreed.value && form.value.breedCustom.trim().length === 0)
      ElMessage.warning('请填写品种名称')
    else if (!form.value.weightKg || Number(form.value.weightKg) <= 0 || Number(form.value.weightKg) > 200)
      ElMessage.warning('体重需在 0.1~200 kg 之间')
    return
  }
  if (step.value < totalSteps) step.value++
}

function prev() {
  if (step.value > 1) step.value--
}

function back() {
  router.back()
}

function help() {
  ElMessage.info('创建档案后可随时在「我的宠物」中修改')
}

// ============ 头像上传 ============
function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片文件')
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片需小于 2MB')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    form.value.avatar = reader.result as string
  }
  reader.readAsDataURL(file)
}

function clearAvatar() {
  form.value.avatar = ''
}

// ============ 提交 ============
async function submit() {
  if (submitting.value) return
  if (!step1Valid.value || !step2Valid.value) {
    ElMessage.warning('请完成必要的必填项')
    return
  }
  submitting.value = true
  try {
    const created = await petApi.save({
      name: form.value.name.trim(),
      categoryId: form.value.categoryId!,
      breedId: form.value.breedId!,
      // 选「其他」时把自定义品种名一并提交
      ...(isOtherBreed.value ? { breedName: form.value.breedCustom.trim() } : {}),
      gender: form.value.gender as PetGender,
      birthday: form.value.birthday || undefined,
      weight: Number(form.value.weightKg),
      neutered: form.value.isNeutered ? 1 : 0,
      avatar: form.value.avatar || undefined,
      description: [form.value.allergies, form.value.notes].filter(Boolean).join('\n') || undefined,
      vaccineDone: form.value.vaccineDone,
      dewormDone: form.value.dewormDone,
      allergies: form.value.allergies || undefined,
    })
    ElMessage.success(`「${created.name}」档案创建成功`)
    router.replace(`/pet/detail/${created.id}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败,请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="pet-create">
    <!-- 顶部导航 -->
    <header class="topbar no-vw">
      <button class="icon-btn" aria-label="返回" @click="back">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <h1 class="topbar-title">创建宠物档案</h1>
      <button class="icon-btn" aria-label="帮助" @click="help">
        <span class="material-symbols-outlined">help</span>
      </button>
    </header>

    <main class="page-main">
      <div class="wizard-card">
        <!-- 步骤进度条 -->
        <ol class="stepper">
          <li
            v-for="(s, idx) in stepDefs"
            :key="s.num"
            class="step-item"
            :class="{ active: step === s.num, done: step > s.num }"
          >
            <div class="step-circle">
              <span v-if="step > s.num" class="material-symbols-outlined check">check</span>
              <span v-else>{{ s.num }}</span>
            </div>
            <span class="step-title">{{ s.title }}</span>
            <div v-if="idx < stepDefs.length - 1" class="step-line"></div>
          </li>
        </ol>

        <!-- Step 1: 基本信息 -->
        <section v-if="step === 1" class="step-panel">
          <!-- 头像上传 -->
          <div class="avatar-row">
            <div class="avatar-wrap">
              <div class="avatar-circle">
                <img v-if="form.avatar" :src="form.avatar" alt="宠物头像" />
                <span v-else class="material-symbols-outlined avatar-icon">add_a_photo</span>
              </div>
              <label class="avatar-add">
                <span class="material-symbols-outlined">add</span>
                <input
                  type="file"
                  accept="image/*"
                  class="avatar-input"
                  @change="onAvatarChange"
                />
              </label>
              <button
                v-if="form.avatar"
                type="button"
                class="avatar-remove"
                aria-label="移除头像"
                @click="clearAvatar"
              >
                <span class="material-symbols-outlined">close</span>
              </button>
            </div>
            <p class="avatar-hint">点击上传宠物头像 · 最大 2MB</p>
          </div>

          <!-- 姓名 + 生日 -->
          <div class="grid-2">
            <div class="field">
              <label class="field-label">宠物姓名 <span class="req">*</span></label>
              <input
                v-model="form.name"
                class="text-input"
                type="text"
                placeholder="输入宝宝的名字"
                maxlength="20"
              />
            </div>
            <div class="field">
              <label class="field-label">出生日期 <span class="req">*</span></label>
              <div class="date-input">
                <input
                  v-model="form.birthday"
                  class="text-input"
                  type="date"
                  :max="today"
                  placeholder="yyyy/mm/dd"
                />
                <span class="material-symbols-outlined date-icon">calendar_month</span>
              </div>
            </div>
          </div>

          <!-- 宠物类型 -->
          <div class="field">
            <label class="field-label">
              宠物类型 <span class="req">*</span>
              <span v-if="dictionaryLoading" class="field-loading">加载中…</span>
            </label>
            <div class="cat-grid">
              <button
                v-for="c in displayCategories"
                :key="c.id"
                type="button"
                class="cat-card"
                :class="{ active: form.categoryId === c.id }"
                @click="form.categoryId = c.id"
              >
                <img
                  v-if="c.icon && !brokenIcons.has(c.id)"
                  :src="c.icon"
                  class="cat-icon-img"
                  :alt="c.name"
                  @error="onIconError(c.id)"
                />
                <span v-else class="material-symbols-outlined cat-icon">pets</span>
                <span class="cat-label">{{ c.name }}</span>
              </button>
            </div>
          </div>

          <!-- 性别 -->
          <div class="field">
            <label class="field-label">性别 <span class="req">*</span></label>
            <div class="gender-row">
              <button
                type="button"
                class="gender-btn"
                :class="{ active: form.gender === 0 }"
                @click="form.gender = 0"
              >
                公 ♂
              </button>
              <button
                type="button"
                class="gender-btn"
                :class="{ active: form.gender === 1 }"
                @click="form.gender = 1"
              >
                母 ♀
              </button>
              <button
                type="button"
                class="gender-btn"
                :class="{ active: form.gender === 2 }"
                @click="form.gender = 2"
              >
                未知
              </button>
            </div>
          </div>
        </section>

        <!-- Step 2: 品种与体征 -->
        <section v-else-if="step === 2" class="step-panel">
          <div v-if="!selectedCategory" class="hint-banner">
            请先在第一步选择宠物类型
          </div>
          <div v-else>
            <p class="sub-context">当前分类:<strong>{{ selectedCategory.name }}</strong></p>
          </div>

          <div class="field">
            <label class="field-label">品种 <span class="req">*</span></label>
            <select
              v-model="form.breedId"
              class="text-input"
              :disabled="!selectedCategory"
              :class="{ 'is-missing': selectedCategory && form.breedId == null }"
            >
              <option :value="null" disabled>请选择品种</option>
              <option v-for="b in breeds" :key="b.id" :value="b.id">{{ b.name }}</option>
              <option :value="OTHER_BREED_ID">其他（手动填写）</option>
            </select>
            <!-- 选「其他」时展示自定义品种输入框 -->
            <input
              v-if="isOtherBreed"
              v-model="form.breedCustom"
              class="text-input"
              type="text"
              maxlength="64"
              placeholder="请输入品种名称，如：田园猫、串串狗、雪貂…"
            />
            <p v-if="selectedCategory && breeds.length === 0" class="field-tip">
              没有找到合适的品种？可选择「其他」手动填写
            </p>
          </div>

          <div class="field">
            <label class="field-label">体重（kg） <span class="req">*</span></label>
            <input
              v-model="form.weightKg"
              class="text-input"
              type="number"
              step="0.1"
              min="0.1"
              max="200"
              placeholder="例:4.5"
            />
            <p class="field-tip">支持 0.1 ~ 200 kg</p>
          </div>

          <div class="field">
            <label class="field-label">是否已绝育</label>
            <div class="switch-row">
              <button
                type="button"
                class="switch-btn"
                :class="{ on: form.isNeutered }"
                @click="form.isNeutered = !form.isNeutered"
              >
                <span class="dot"></span>
              </button>
              <span class="switch-text">
                {{ form.isNeutered ? '已绝育' : '未绝育' }}
              </span>
            </div>
          </div>
        </section>

        <!-- Step 3: 健康初始数据 -->
        <section v-else class="step-panel">
          <p class="sub-context">这些信息有助于 AI 问诊与健康提醒</p>

          <div class="check-field">
            <div class="check-text">
              <span class="check-label">已完成基础疫苗接种</span>
              <span class="check-desc">
                {{ form.vaccineDone ? '已建档' : '暂未完成,可后续补充' }}
              </span>
            </div>
            <button
              type="button"
              class="switch-btn"
              :class="{ on: form.vaccineDone }"
              @click="form.vaccineDone = !form.vaccineDone"
            >
              <span class="dot"></span>
            </button>
          </div>

          <div class="check-field">
            <div class="check-text">
              <span class="check-label">已完成体内驱虫</span>
              <span class="check-desc">
                {{ form.dewormDone ? '已建档' : '暂未完成,可后续补充' }}
              </span>
            </div>
            <button
              type="button"
              class="switch-btn"
              :class="{ on: form.dewormDone }"
              @click="form.dewormDone = !form.dewormDone"
            >
              <span class="dot"></span>
            </button>
          </div>

          <div class="field">
            <label class="field-label">过敏史（如有）</label>
            <textarea
              v-model="form.allergies"
              class="text-input textarea"
              rows="3"
              placeholder="如：青霉素、海鲜等"
              maxlength="200"
            ></textarea>
          </div>

          <div class="field">
            <label class="field-label">备注</label>
            <textarea
              v-model="form.notes"
              class="text-input textarea"
              rows="3"
              placeholder="性格、习惯、其他需要记录的信息"
              maxlength="200"
            ></textarea>
          </div>
        </section>

        <!-- 底部按钮 -->
        <div class="actions">
          <button
            v-if="step > 1"
            class="ghost-btn"
            type="button"
            :disabled="submitting"
            @click="prev"
          >
            上一步
          </button>
          <button
            v-if="step < totalSteps"
            class="primary-btn"
            type="button"
            :disabled="!canNext()"
            @click="next"
          >
            下一步
            <span class="material-symbols-outlined">arrow_forward</span>
          </button>
          <button
            v-else
            class="primary-btn"
            type="button"
            :disabled="submitting"
            @click="submit"
          >
            <span v-if="submitting" class="btn-spinner" aria-hidden="true"></span>
            <span v-else>完成创建</span>
            <span v-if="!submitting" class="material-symbols-outlined">check</span>
          </button>
        </div>
      </div>
    </main>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.pet-create {
  min-height: 100vh;
  background: linear-gradient(135deg, #e8f0ff 0%, $color-background 35%, #fff1ea 100%);
  padding-bottom: $spacing-2xl;
}

// ====== 顶栏 ======
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 $width-gutter-desktop;
  background: transparent;
  position: sticky;
  top: 0;
  z-index: 10;
}

.topbar-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
}

.icon-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  background: transparent;
  color: $color-brand;
  border: none;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: rgba($color-brand, 0.1);
  }

  .material-symbols-outlined {
    font-size: 24px;
  }
}

// ====== 主区 ======
.page-main {
  @include container-max;
  padding: $spacing-md $width-gutter-desktop $spacing-2xl;
}

.wizard-card {
  max-width: 720px;
  margin: 0 auto;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-lg;
  padding: $spacing-xl $spacing-2xl $spacing-2xl;

  @media (max-width: 768px) {
    padding: $spacing-lg $spacing-md $spacing-xl;
  }
}

// ====== 步骤进度条 ======
.stepper {
  display: flex;
  align-items: flex-start;
  position: relative;
  margin: 0 auto $spacing-xl;
  max-width: 560px;
  list-style: none;
  padding: 0;
}

.step-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.step-circle {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: $color-surface-container;
  color: $color-text-tertiary;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  border: 2px solid transparent;
  position: relative;
  z-index: 2;
  transition: background $transition-fast, color $transition-fast;

  .check {
    font-size: 22px;
  }
}

.step-title {
  margin-top: $spacing-xs;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  font-weight: $font-weight-medium;
  transition: color $transition-fast;
}

.step-line {
  position: absolute;
  top: 18px;
  left: 50%;
  width: 100%;
  height: 2px;
  background: $color-surface-container;
  z-index: 1;
}

.step-item.done .step-circle {
  background: $color-brand-container;
  color: $color-on-brand;
}

.step-item.done .step-title,
.step-item.active .step-title {
  color: $color-brand;
  font-weight: $font-weight-semibold;
}

.step-item.active .step-circle {
  background: $color-brand;
  color: $color-on-brand;
  border-color: $color-brand;
  box-shadow: 0 0 0 4px rgba($color-brand, 0.15);
}

.step-item.done .step-line {
  background: $color-brand;
}

// ====== Step Panel ======
.step-panel {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  padding-top: $spacing-md;
}

// ====== 头像 ======
.avatar-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm 0;
}

.avatar-wrap {
  position: relative;
  width: 120px;
  height: 120px;
}

.avatar-circle {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: $color-surface-container;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.avatar-icon {
  font-size: 48px !important;
  color: $color-outline;
}

.avatar-add {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: $color-brand;
  color: $color-on-brand;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: $shadow-md;
  border: 3px solid $color-surface-container-lowest;

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.avatar-remove {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  border: 2px solid $color-surface-container-lowest;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;

  &:hover {
    background: rgba(0, 0, 0, 0.75);
  }

  .material-symbols-outlined {
    font-size: 16px;
  }
}

.avatar-input {
  display: none;
}

.avatar-hint {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 0;
}

// ====== Grid & Fields ======
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-md;

  @media (max-width: 480px) {
    grid-template-columns: 1fr;
  }
}

.field {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.field-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
}

.req {
  color: $color-danger;
  font-weight: $font-weight-bold;
}

.field-loading {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  font-weight: $font-weight-regular;
}

.field-tip {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  margin: 0;
}

.sub-context {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 0;
  background: rgba($color-brand, 0.06);
  padding: $spacing-xs $spacing-sm;
  border-radius: $radius-default;
  text-align: center;

  strong {
    color: $color-brand;
  }
}

.hint-banner {
  text-align: center;
  padding: $spacing-md;
  background: rgba($color-warning, 0.1);
  color: $color-warning;
  border-radius: $radius-default;
  font-size: $font-size-label-md;
}

.text-input {
  width: 100%;
  padding: 12px 16px;
  background: $color-surface-container-low;
  border: 1px solid transparent;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  outline: none;
  transition: border-color $transition-fast, box-shadow $transition-fast;
  font-family: inherit;

  &::placeholder {
    color: $color-outline;
  }

  &:focus {
    border-color: $color-secondary;
    box-shadow: 0 0 0 3px rgba($color-secondary, 0.15);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  &.is-missing {
    border-color: rgba($color-warning, 0.5);
  }
}

.textarea {
  resize: vertical;
  min-height: 80px;
  line-height: $line-height-normal;
}

.date-input {
  position: relative;

  // 隐藏原生 date 选择器指示器（避免与自定义图标重叠），
  // 但保留其覆盖整框的点击区，仍可点开日历
  .text-input::-webkit-calendar-picker-indicator {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    margin: 0;
    padding: 0;
    opacity: 0;
    cursor: pointer;
  }

  // 自定义图标左侧留出点击空间
  .text-input {
    padding-right: 44px;
  }

  .date-icon {
    position: absolute;
    right: 12px;
    top: 50%;
    transform: translateY(-50%);
    color: $color-text-tertiary;
    pointer-events: none;
    font-size: 22px;
  }
}

// ====== 分类卡片 ======
.cat-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-sm;

  @media (max-width: 480px) {
    gap: $spacing-xs;
  }
}

.cat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: $spacing-md $spacing-xs;
  background: $color-surface-container-low;
  border: 1.5px solid transparent;
  border-radius: $radius-default;
  color: $color-text-secondary;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-medium;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: rgba($color-brand, 0.05);
  }

  &.active {
    background: $color-surface-container-lowest;
    border-color: $color-brand;
    color: $color-brand;
  }
}

.cat-icon {
  font-size: 32px !important;
  color: $color-brand;
}

.cat-icon-img {
  width: 40px;
  height: 40px;
  object-fit: contain;
}

.cat-label {
  font-size: $font-size-label-sm;
}

// ====== 性别按钮 ======
.gender-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: $spacing-sm;
}

.gender-btn {
  padding: 12px 0;
  background: $color-surface-container-low;
  border: 1.5px solid transparent;
  border-radius: $radius-default;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
  font-weight: $font-weight-medium;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: rgba($color-secondary, 0.05);
  }

  &.active {
    background: $color-surface-container-lowest;
    border-color: $color-secondary;
    color: $color-secondary;
    font-weight: $font-weight-semibold;
    box-shadow: 0 0 0 3px rgba($color-secondary, 0.1);
  }
}

// ====== 开关 ======
.switch-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.switch-text {
  font-size: $font-size-body-md;
  color: $color-on-surface;
}

.switch-btn {
  width: 48px;
  height: 28px;
  background: $color-surface-container-high;
  border-radius: $radius-full;
  border: none;
  position: relative;
  padding: 2px;
  cursor: pointer;
  transition: background $transition-fast;

  .dot {
    display: block;
    width: 24px;
    height: 24px;
    background: #fff;
    border-radius: 50%;
    box-shadow: $shadow-sm;
    transition: transform $transition-fast;
  }
}

.switch-btn.on {
  background: $color-brand;

  .dot {
    transform: translateX(20px);
  }
}

// ====== Check field (Step 3) ======
.check-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-default;
}

.check-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.check-label {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  font-weight: $font-weight-medium;
}

.check-desc {
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}

// ====== 底部按钮 ======
.actions {
  display: flex;
  gap: $spacing-sm;
  margin-top: $spacing-xl;

  button {
    flex: 1;
  }
}

.primary-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  padding: 16px $spacing-xl;
  background: linear-gradient(90deg, $color-brand 0%, #ff9461 100%);
  color: $color-on-brand;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba($color-brand, 0.3);
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover:not(:disabled) {
    transform: translateY(-2px);
    box-shadow: 0 12px 24px rgba($color-brand, 0.35);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
    box-shadow: none;
    transform: none;
  }

  .material-symbols-outlined {
    font-size: 22px;
  }
}

.ghost-btn {
  padding: 16px $spacing-xl;
  background: $color-surface-container-low;
  color: $color-text-secondary;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-semibold;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover:not(:disabled) {
    background: $color-surface-container;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.btn-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pm-spin 0.8s linear infinite;
}

@keyframes pm-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 768px) {
  .pet-create {
    padding-bottom: 1.5rem;
  }
  .topbar {
    height: 3.5rem;
    padding: 0 0.75rem;
  }
  .page-main {
    padding: 0.75rem 0.75rem 1.5rem;
  }
  .wizard-card {
    padding: 1rem 0.75rem 1.5rem;
  }
  .icon-btn {
    width: 2.5rem;
    height: 2.5rem;
  }
  .avatar-wrap {
    width: 5.5rem;
    height: 5.5rem;
  }
  .avatar-icon {
    font-size: 3rem !important;
  }
  .avatar-add {
    width: 2.25rem;
    height: 2.25rem;
  }
  .avatar-remove {
    width: 1.75rem;
    height: 1.75rem;
  }
  .text-input {
    padding: 0.75rem 1rem;
  }
  .date-input .text-input {
    padding-right: 2.75rem;
  }
  .field {
    gap: 0.25rem;
  }
  .field-label {
    gap: 0.375rem;
  }
  .cat-card {
    gap: 0.375rem;
    padding: 1rem 0.25rem;
  }
  .gender-btn {
    padding: 0.75rem 0;
  }
  .switch-btn {
    width: 3rem;
    height: 1.75rem;
    padding: 0.125rem;
  }
  .switch-btn .dot {
    width: 1.5rem;
    height: 1.5rem;
  }
  .switch-btn.on .dot {
    transform: translateX(1.25rem);
  }
  .check-field {
    padding: 1rem;
  }
  .stepper {
    margin-bottom: 1.5rem;
  }
  .step-circle {
    width: 2.25rem;
    height: 2.25rem;
  }
  .step-line {
    top: 1.125rem;
  }
  .step-title {
    margin-top: 0.25rem;
  }
  .primary-btn,
  .ghost-btn {
    padding: 1rem 1.25rem;
  }
}

</style>
