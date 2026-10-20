<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { petApi, type Pet, type PetCategory, type PetBreed, type PetGender } from '@/api/modules/pet'
import { usePetStore } from '@/stores/pet'

const route = useRoute()
const router = useRouter()
const petStore = usePetStore()

// ============ 状态 ============
const loading = ref(false)
const saving = ref(false)
const error = ref<string | null>(null)

const categories = ref<PetCategory[]>([])
const breeds = ref<PetBreed[]>([])
const pet = ref<Pet | null>(null)

const petId = computed(() => Number(route.params.id))

const form = ref({
  name: '',
  categoryId: null as number | null,
  breedId: null as number | null,
  breedCustom: '', // 选「其他」时手填的品种名
  gender: 2 as PetGender,
  birthday: '',
  weight: '',
  neutered: false,
  avatar: '',
  description: '',
  vaccineDone: false,
  dewormDone: false,
  allergies: '',
})

// 「其他」自定义品种哨兵值：breedId 传 -1，品种名由 breedCustom 提供
const OTHER_BREED_ID = -1
const isOtherBreed = computed(() => form.value.breedId === OTHER_BREED_ID)

// 解析 description 中按 \n 拆分出过敏史 / 备注
function splitDescription(desc?: string) {
  if (!desc) return { allergies: '', notes: '' }
  const parts = desc.split('\n')
  return { allergies: parts[0] || '', notes: parts.slice(1).join('\n') || '' }
}

// ============ 加载数据 ============
async function loadCategories() {
  try {
    categories.value = await petApi.categories()
  } catch (e) {
    ElMessage.error('分类加载失败')
  }
}

async function loadBreeds(categoryId: number) {
  try {
    breeds.value = await petApi.breeds(categoryId)
  } catch (e) {
    breeds.value = []
  }
}

async function loadPet() {
  if (!petId.value || isNaN(petId.value)) {
    error.value = '无效的宠物 ID'
    return
  }
  loading.value = true
  error.value = null
  try {
    const data = await petApi.detail(petId.value)
    pet.value = data
    const { allergies, notes } = splitDescription(data.description)
    form.value = {
      name: data.name || '',
      categoryId: data.categoryId ?? null,
      breedId: data.breedId ?? null,
      breedCustom: '',
      gender: (data.gender ?? 2) as PetGender,
      birthday: data.birthday || '',
      weight: data.weight != null ? String(data.weight) : '',
      neutered: data.neutered === 1,
      avatar: data.avatar || '',
      description: data.description || '',
      vaccineDone: false,
      dewormDone: false,
      allergies,
    }
    // 自定义品种：breedId = -1，回显手填的品种名
    if (data.breedId === OTHER_BREED_ID) {
      form.value.breedId = OTHER_BREED_ID
      form.value.breedCustom = data.breedName || ''
    }
    if (data.categoryId != null) await loadBreeds(data.categoryId)
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

// 仅在用户主动切换宠物类型时才重载品种并清空已选品种；
// 初始回填时由 loadPet 直接载入 breeds，避免 watch 把已回填的 breedId 误清空
function onPickCategory(id: number) {
  if (id === form.value.categoryId || id == null) return
  form.value.categoryId = id
  breeds.value = []
  loadBreeds(id)
  form.value.breedId = null
  form.value.breedCustom = ''
}

onMounted(async () => {
  await loadCategories()
  await loadPet()
})

// ============ 校验 ============
const today = new Date().toISOString().slice(0, 10)

const valid = computed(() => {
  if (!form.value.name.trim()) return false
  if (form.value.name.length > 20) return false
  if (form.value.birthday && form.value.birthday > today) return false
  if (form.value.categoryId == null) return false
  if (form.value.breedId == null) return false
  if (isOtherBreed.value && form.value.breedCustom.trim().length === 0) return false
  if (isOtherBreed.value && form.value.breedCustom.trim().length > 64) return false
  if (![0, 1, 2].includes(form.value.gender as number)) return false
  if (form.value.weight) {
    const w = Number(form.value.weight)
    if (isNaN(w) || w <= 0 || w > 200) return false
  }
  return true
})

// ============ 头像 ============
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

// ============ 操作 ============
function back() {
  router.back()
}

async function save() {
  if (!valid.value) {
    ElMessage.warning('请检查表单字段')
    return
  }
  if (!pet.value) return
  saving.value = true
  try {
    const description = [form.value.allergies, form.value.description].filter(Boolean).join('\n') || undefined
    const updated = await petStore.save({
      id: pet.value.id,
      name: form.value.name.trim(),
      categoryId: form.value.categoryId!,
      breedId: form.value.breedId!,
      // 选「其他」时把自定义品种名一并提交
      ...(isOtherBreed.value ? { breedName: form.value.breedCustom.trim() } : {}),
      gender: form.value.gender as PetGender,
      birthday: form.value.birthday || undefined,
      weight: form.value.weight ? Number(form.value.weight) : undefined,
      neutered: form.value.neutered ? 1 : 0,
      avatar: form.value.avatar || undefined,
      description,
    })
    ElMessage.success('已保存')
    router.replace(`/pet/detail/${updated.id}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function categoryIcon(name: string) {
  const n = (name || '').toLowerCase()
  if (n.includes('猫')) return 'pets'
  if (n.includes('狗')) return 'cruelty_free'
  if (n.includes('兔')) return 'cruelty_free'
  return 'help'
}
</script>

<template>
  <div class="pet-edit">
    <header class="topbar no-vw">
      <button class="icon-btn" aria-label="返回" @click="back">
        <span class="material-symbols-outlined">arrow_back</span>
      </button>
      <h1 class="topbar-title">编辑宠物档案</h1>
      <button
        class="icon-btn primary"
        :disabled="!valid || saving"
        @click="save"
        aria-label="保存"
      >
        <span class="material-symbols-outlined">
          {{ saving ? 'progress_activity' : 'check' }}
        </span>
      </button>
    </header>

    <main class="page-main">
      <div class="edit-card">
        <!-- 加载中 -->
        <div v-if="loading" class="state-card">
          <span class="spinner"></span>
          <p>正在加载宠物档案…</p>
        </div>

        <!-- 错误 -->
        <div v-else-if="error" class="state-card error">
          <span class="material-symbols-outlined">error_outline</span>
          <p>{{ error }}</p>
          <button class="retry-btn" @click="loadPet">重试</button>
        </div>

        <template v-else-if="pet">
          <!-- 头像 -->
          <div class="avatar-row">
            <div class="avatar-wrap">
              <div class="avatar-circle">
                <img v-if="form.avatar" :src="form.avatar" alt="宠物头像" />
                <span v-else class="material-symbols-outlined avatar-icon">add_a_photo</span>
              </div>
              <label class="avatar-add">
                <span class="material-symbols-outlined">add_a_photo</span>
                <input type="file" accept="image/*" class="avatar-input" @change="onAvatarChange" />
              </label>
              <button
                v-if="form.avatar"
                type="button"
                class="avatar-remove"
                @click="clearAvatar"
                aria-label="移除头像"
              >
                <span class="material-symbols-outlined">close</span>
              </button>
            </div>
            <p class="avatar-hint">点击更换头像 · 最大 2MB</p>
          </div>

          <div class="field">
            <label class="field-label">宠物姓名 <span class="req">*</span></label>
            <input v-model="form.name" class="text-input" type="text" maxlength="20" />
          </div>

          <div class="grid-2">
            <div class="field">
              <label class="field-label">出生日期</label>
              <div class="date-input">
                <input
                  v-model="form.birthday"
                  class="text-input"
                  type="date"
                  :max="today"
                />
                <span class="material-symbols-outlined date-icon">calendar_month</span>
              </div>
            </div>
            <div class="field">
              <label class="field-label">体重（kg）</label>
              <input
                v-model="form.weight"
                class="text-input"
                type="number"
                min="0.1"
                max="200"
                step="0.1"
              />
            </div>
          </div>

          <div class="field">
            <label class="field-label">宠物类型 <span class="req">*</span></label>
            <div class="cat-grid">
              <button
                v-for="c in categories"
                :key="c.id"
                type="button"
                class="cat-card"
                :class="{ active: form.categoryId === c.id }"
                @click="onPickCategory(c.id)"
              >
                <span class="material-symbols-outlined cat-icon">{{ categoryIcon(c.name) }}</span>
                <span class="cat-label">{{ c.name }}</span>
              </button>
            </div>
          </div>

          <div class="field">
            <label class="field-label">品种 <span class="req">*</span></label>
            <select v-model="form.breedId" class="text-input" :disabled="!form.categoryId">
              <option :value="null" disabled>请选择品种</option>
              <option v-for="b in breeds" :key="b.id" :value="b.id">{{ b.name }}</option>
              <option :value="OTHER_BREED_ID">其他（手动填写）</option>
            </select>
            <input
              v-if="isOtherBreed"
              v-model="form.breedCustom"
              class="text-input"
              type="text"
              maxlength="64"
              placeholder="请输入品种名称，如：田园猫、串串狗、雪貂…"
            />
          </div>

          <div class="field">
            <label class="field-label">性别 <span class="req">*</span></label>
            <div class="gender-row">
              <button
                type="button"
                class="gender-btn"
                :class="{ active: form.gender === 0 }"
                @click="form.gender = 0"
              >公 ♂</button>
              <button
                type="button"
                class="gender-btn"
                :class="{ active: form.gender === 1 }"
                @click="form.gender = 1"
              >母 ♀</button>
              <button
                type="button"
                class="gender-btn"
                :class="{ active: form.gender === 2 }"
                @click="form.gender = 2"
              >未知</button>
            </div>
          </div>

          <div class="field">
            <label class="field-label">是否已绝育</label>
            <div class="switch-row">
              <button
                type="button"
                class="switch-btn"
                :class="{ on: form.neutered }"
                @click="form.neutered = !form.neutered"
              >
                <span class="dot"></span>
              </button>
              <span class="switch-text">{{ form.neutered ? '已绝育' : '未绝育' }}</span>
            </div>
          </div>

          <div class="field">
            <label class="field-label">过敏史</label>
            <textarea
              v-model="form.allergies"
              class="text-input textarea"
              rows="2"
              maxlength="200"
              placeholder="如：青霉素、海鲜等"
            ></textarea>
          </div>

          <div class="field">
            <label class="field-label">备注</label>
            <textarea
              v-model="form.description"
              class="text-input textarea"
              rows="3"
              maxlength="200"
              placeholder="性格、习惯、其他需要记录的信息"
            ></textarea>
          </div>
        </template>
      </div>
    </main>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.pet-edit {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: $spacing-2xl;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 64px;
  padding: 0 $width-gutter-desktop;
  position: sticky;
  top: 0;
  background: transparent;
  z-index: 10;
}

.topbar-title {
  flex: 1;
  text-align: center;
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

  &:hover:not(:disabled) {
    background: rgba($color-brand, 0.1);
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }

  &.primary {
    color: #fff;
    background: $color-brand;

    &:hover:not(:disabled) {
      background: darken($color-brand, 6%);
    }
  }

  .material-symbols-outlined {
    font-size: 22px;
  }
}

.page-main {
  @include container-max;
  padding: $spacing-md $width-gutter-mobile $spacing-2xl;
}

.edit-card {
  max-width: 640px;
  margin: 0 auto;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  padding: $spacing-xl $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.state-card {
  text-align: center;
  padding: $spacing-2xl $spacing-md;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;

  p {
    color: $color-text-secondary;
    margin: 0;
  }

  .spinner {
    width: 32px;
    height: 32px;
    border: 3px solid rgba($color-brand, 0.2);
    border-top-color: $color-brand;
    border-radius: 50%;
    animation: pm-spin 0.8s linear infinite;
  }
}

.state-card.error .material-symbols-outlined {
  font-size: 40px;
  color: $color-danger;
}

.retry-btn {
  margin-top: $spacing-sm;
  padding: 8px 20px;
  background: $color-brand;
  color: #fff;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  font-weight: $font-weight-semibold;
}

// ====== 头像 ======
.avatar-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
}

.avatar-wrap {
  position: relative;
  width: 110px;
  height: 110px;
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
  font-size: 44px !important;
  color: $color-outline;
}

.avatar-add {
  position: absolute;
  right: -4px;
  bottom: -4px;
  width: 32px;
  height: 32px;
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
    font-size: 16px;
  }
}

.avatar-remove {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  border: 2px solid $color-surface-container-lowest;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  font-size: 14px;
}

.avatar-input {
  display: none;
}

.avatar-hint {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 0;
}

// ====== 表单 ======
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
  gap: 6px;
}

.field-label {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
}

.req {
  color: $color-danger;
  font-weight: $font-weight-bold;
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
}

.textarea {
  resize: vertical;
  min-height: 70px;
  line-height: $line-height-normal;
}

.date-input {
  position: relative;

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

.cat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-sm;

  @media (max-width: 480px) {
    grid-template-columns: repeat(2, 1fr);
  }
}

.cat-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: $spacing-sm $spacing-xs;
  background: $color-surface-container-low;
  border: 1.5px solid transparent;
  border-radius: $radius-default;
  color: $color-text-secondary;
  font-size: $font-size-label-sm;
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
  font-size: 28px !important;
  color: $color-brand;
}

.cat-label {
  font-size: $font-size-label-sm;
}

.gender-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: $spacing-sm;
}

.gender-btn {
  padding: 10px 0;
  background: $color-surface-container-low;
  border: 1.5px solid transparent;
  border-radius: $radius-default;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
  font-weight: $font-weight-medium;
  cursor: pointer;
  transition: all $transition-fast;

  &.active {
    background: $color-surface-container-lowest;
    border-color: $color-secondary;
    color: $color-secondary;
    font-weight: $font-weight-semibold;
    box-shadow: 0 0 0 3px rgba($color-secondary, 0.1);
  }
}

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

@keyframes pm-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 768px) {
  .pet-edit {
    padding-bottom: 1.5rem;
  }
  .topbar {
    height: 3.5rem;
    padding: 0 0.75rem;
  }
  .topbar-title {
    font-size: 1.125rem;
  }
  .page-main {
    padding: 0.75rem 0.75rem 1.5rem;
  }
  .icon-btn {
    width: 2.5rem;
    height: 2.5rem;
  }
  .icon-btn .material-symbols-outlined {
    font-size: 1.375rem;
  }
  .edit-card {
    padding: 1.25rem 1rem;
    gap: 1rem;
  }
  .avatar-wrap {
    width: 5.5rem;
    height: 5.5rem;
  }
  .avatar-icon {
    font-size: 2.75rem !important;
  }
  .avatar-add {
    width: 2rem;
    height: 2rem;
  }
  .avatar-add .material-symbols-outlined {
    font-size: 1rem;
  }
  .avatar-remove {
    width: 1.625rem;
    height: 1.625rem;
    font-size: 0.875rem;
  }
  .text-input {
    padding: 0.75rem 1rem;
  }
  .date-input .date-icon {
    right: 0.75rem;
    font-size: 1.375rem;
  }
  .field {
    gap: 0.375rem;
  }
  .cat-card {
    gap: 0.25rem;
    padding: 0.5rem 0.25rem;
  }
  .cat-icon {
    font-size: 1.75rem !important;
  }
  .gender-btn {
    padding: 0.625rem 0;
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
}

</style>
