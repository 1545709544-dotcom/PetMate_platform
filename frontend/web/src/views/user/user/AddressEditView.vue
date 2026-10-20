<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi, type Address } from '@/api/modules/user'

// 省市区三级联动数据，来自 china-division
import pcaData from 'china-division/dist/pca.json'

const router = useRouter()
const route = useRoute()

// ============ 状态 ============
const isEdit = computed(() => !!route.params.id)
const addressId = computed(() => (route.params.id ? Number(route.params.id) : null))

const form = reactive<Partial<Address>>({
  receiver: '',
  phone: '',
  province: '',
  city: '',
  district: '',
  detail: '',
  tag: 'home',
  isDefault: false,
})

const saving = ref(false)
const loading = ref(false)

// ============ 省市区数据 ============
const provinces = computed(() =>
  Object.keys(pcaData).map((p) => ({ label: p, value: p }))
)

const cityOptions = computed(() => {
  if (!form.province) return []
  const cities = (pcaData as Record<string, Record<string, string[]>>)[form.province]
  return cities ? Object.keys(cities).map((c) => ({ label: c, value: c })) : []
})

const districtOptions = computed(() => {
  if (!form.province || !form.city) return []
  const cities = (pcaData as Record<string, Record<string, string[]>>)[form.province]
  if (!cities) return []
  const districts = cities[form.city]
  return districts ? districts.map((d: string) => ({ label: d, value: d })) : []
})

// ============ 标签选项 ============
const tagOptions = [
  { value: 'home', label: '家', icon: 'home' },
  { value: 'company', label: '公司', icon: 'business' },
  { value: 'school', label: '学校', icon: 'school' },
]

// ============ 加载地址详情（编辑模式） ============
async function loadAddress() {
  if (!addressId.value) return
  loading.value = true
  try {
    const addr = await userApi.getAddress(addressId.value)
    if (addr) {
      form.receiver = addr.receiver
      form.phone = addr.phone
      form.province = addr.province
      form.city = addr.city
      form.district = addr.district
      form.detail = addr.detail
      form.tag = addr.tag || 'home'
      form.isDefault = addr.isDefault
      form.id = addr.id
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '地址加载失败')
  } finally {
    loading.value = false
  }
}
onMounted(loadAddress)

// ============ 表单验证 ============
function validateForm(): boolean {
  if (!form.receiver?.trim()) {
    ElMessage.warning('请输入收货人姓名')
    return false
  }
  if (!form.phone?.trim()) {
    ElMessage.warning('请输入手机号码')
    return false
  }
  if (!/^1\d{10}$/.test(form.phone)) {
    ElMessage.warning('请输入正确的手机号码')
    return false
  }
  if (!form.province) {
    ElMessage.warning('请选择所在省份')
    return false
  }
  if (!form.city) {
    ElMessage.warning('请选择所在城市')
    return false
  }
  if (!form.district) {
    ElMessage.warning('请选择所在区县')
    return false
  }
  if (!form.detail?.trim()) {
    ElMessage.warning('请输入详细地址')
    return false
  }
  return true
}

// ============ 保存地址 ============
async function saveAddress() {
  if (!validateForm()) return
  saving.value = true
  try {
    const payload = {
      ...form,
      isDefault: form.isDefault || false,
    } as Partial<Address>
    await userApi.saveAddress(payload)
    ElMessage.success(isEdit.value ? '保存成功' : '新增成功')
    router.back()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ============ 返回 ============
function goBack() {
  router.back()
}

// ============ 重置城市/区县（省份/城市变更时） ============
function onProvinceChange() {
  form.city = ''
  form.district = ''
}
function onCityChange() {
  form.district = ''
}
</script>

<template>
  <div class="address-edit-view">
    <div class="address-edit-container">
      <!-- 页头（与项目一致：带图标标题 + 返回按钮） -->
      <header class="page-header">
        <div class="page-title-wrap">
          <span class="material-symbols-outlined page-icon">edit_location</span>
          <div>
            <h1 class="page-title">{{ isEdit ? '编辑收货地址' : '新增收货地址' }}</h1>
            <p class="page-subtitle">{{ isEdit ? '修改后点击保存即可更新' : '填写收货信息，方便下单配送' }}</p>
          </div>
        </div>
        <button class="back-btn" @click="goBack">
          <span class="material-symbols-outlined">arrow_back</span>
          返回
        </button>
      </header>

      <!-- 表单内容 -->
      <div v-if="!loading" class="form-wrapper">
        <form class="address-form" @submit.prevent="saveAddress">
          <!-- 收货人 & 电话 -->
          <section class="form-section">
            <h3 class="section-title">
              <span class="material-symbols-outlined">person</span>
              收货信息
            </h3>
            <div class="form-row two-col">
              <div class="form-field">
                <label class="field-label">收货人</label>
                <input
                  v-model="form.receiver"
                  type="text"
                  class="text-input"
                  placeholder="请输入收货人姓名"
                  maxlength="20"
                  required
                />
              </div>
              <div class="form-field">
                <label class="field-label">手机号码</label>
                <input
                  v-model="form.phone"
                  type="tel"
                  class="text-input"
                  placeholder="请输入11位手机号"
                  maxlength="11"
                  required
                />
              </div>
            </div>
          </section>

          <!-- 所在地区 -->
          <section class="form-section">
            <h3 class="section-title">
              <span class="material-symbols-outlined">location_on</span>
              所在地区
            </h3>
            <div class="form-row three-col">
              <div class="form-field">
                <label class="field-label">省份</label>
                <select
                  v-model="form.province"
                  class="select-input"
                  @change="onProvinceChange"
                  required
                >
                  <option value="">请选择</option>
                  <option v-for="p in provinces" :key="p.value" :value="p.value">
                    {{ p.label }}
                  </option>
                </select>
              </div>
              <div class="form-field">
                <label class="field-label">城市</label>
                <select
                  v-model="form.city"
                  class="select-input"
                  @change="onCityChange"
                  :disabled="!form.province"
                  required
                >
                  <option value="">请选择</option>
                  <option v-for="c in cityOptions" :key="c.value" :value="c.value">
                    {{ c.label }}
                  </option>
                </select>
              </div>
              <div class="form-field">
                <label class="field-label">区县</label>
                <select
                  v-model="form.district"
                  class="select-input"
                  :disabled="!form.city"
                  required
                >
                  <option value="">请选择</option>
                  <option v-for="d in districtOptions" :key="d.value" :value="d.value">
                    {{ d.label }}
                  </option>
                </select>
              </div>
            </div>
          </section>

          <!-- 详细地址 -->
          <section class="form-section">
            <h3 class="section-title">
              <span class="material-symbols-outlined">home</span>
              详细地址
            </h3>
            <div class="form-field full-width">
              <label class="field-label">街道、门牌号、小区、楼栋、房间号等</label>
              <textarea
                v-model="form.detail"
                class="text-input textarea"
                placeholder="请输入详细地址，如：某某路某某号某某小区A栋101室"
                rows="3"
                maxlength="100"
                required
              ></textarea>
            </div>
          </section>

          <!-- 地址标签 -->
          <section class="form-section">
            <h3 class="section-title">
              <span class="material-symbols-outlined">label</span>
              地址标签
            </h3>
            <div class="tag-group">
              <label
                v-for="t in tagOptions"
                :key="t.value"
                class="tag-option"
                :class="{ active: form.tag === t.value }"
              >
                <input
                  type="radio"
                  v-model="form.tag"
                  :value="t.value"
                  class="tag-radio"
                />
                <span class="tag-content">
                  <span class="material-symbols-outlined tag-icon">{{ t.icon }}</span>
                  <span class="tag-label">{{ t.label }}</span>
                </span>
                <span class="tag-check">
                  <span class="material-symbols-outlined">check_circle</span>
                </span>
              </label>
            </div>
          </section>

          <!-- 设为默认地址 -->
          <section class="form-section">
            <label class="switch-row">
              <span class="switch-label">
                <span class="material-symbols-outlined switch-icon">flag</span>
                设为默认收货地址
              </span>
              <label class="switch">
                <input
                  type="checkbox"
                  v-model="form.isDefault"
                  class="switch-input"
                />
                <span class="switch-slider"></span>
              </label>
            </label>
          </section>

          <!-- 提交按钮 -->
          <div class="action-bar">
            <button type="submit" class="submit-btn" :disabled="saving">
              <span v-if="saving" class="material-symbols-outlined spinning">refresh</span>
              <span v-else>{{ isEdit ? '保存修改' : '保存并使用' }}</span>
            </button>
          </div>
        </form>
      </div>

      <!-- 加载中 -->
      <div v-else class="loading-state">
        <div class="spinner"></div>
        <p>加载中...</p>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

.address-edit-view {
  min-height: 100vh;
  background: $color-background;
}

.address-edit-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
}

// ============ 页头 ============
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: $spacing-md;
  margin-bottom: $spacing-2xl;
}

.page-title-wrap {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.page-icon {
  font-size: 32px;
  color: $color-brand;
  font-variation-settings: 'FILL' 1;
}

.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  line-height: 1.2;
}

.page-subtitle {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  margin-top: 4px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-xs $spacing-md;
  background: $color-surface-container;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: $color-surface-container-high;
    border-color: $color-brand;
    color: $color-brand;
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

// ============ 表单内容 ============
.form-wrapper {
  max-width: 760px;
  margin: 0 auto;
}

.address-form {
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
}

.form-section {
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.3);
  padding: $spacing-lg;

  &:hover {
    box-shadow: $shadow-md;
  }
}

.section-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-lg;

  .material-symbols-outlined {
    color: $color-brand;
    font-size: 22px;
  }
}

// ============ 表单字段 ============
.form-row {
  display: grid;
  gap: $spacing-md;

  &.two-col {
    grid-template-columns: 1fr 1fr;

    @media (max-width: 480px) {
      grid-template-columns: 1fr;
    }
  }

  &.three-col {
    grid-template-columns: 1fr 1fr 1fr;

    @media (max-width: 480px) {
      grid-template-columns: 1fr;
    }
  }
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;

  &.full-width {
    grid-column: 1 / -1;
  }
}

.field-label {
  font-size: $font-size-label-sm;
  font-weight: 600;
  color: $color-on-surface;
}

.text-input {
  width: 100%;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  font-family: inherit;
  outline: none;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &::placeholder {
    color: $color-text-tertiary;
  }

  &:focus {
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba(255, 122, 50, 0.15);
  }

  &.textarea {
    resize: vertical;
    min-height: 80px;
    line-height: $line-height-normal;
  }
}

.select-input {
  width: 100%;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  font-family: inherit;
  outline: none;
  cursor: pointer;
  appearance: none;
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='20' height='20' viewBox='0 0 24 24' fill='none' stroke='%23584238' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'%3E%3Cpath d='M6 9l6 6 6-6'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right $spacing-md center;
  padding-right: 40px;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &:focus {
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba(255, 122, 50, 0.15);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    background-color: $color-surface-container;
  }
}

// ============ 标签组 ============
.tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.tag-option {
  position: relative;
  flex: 1;
  min-width: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-low;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-full;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand-light;
  }

  &.active {
    background: rgba(255, 122, 50, 0.08);
    border-color: $color-brand;

    .tag-content {
      color: $color-brand;
    }

    .tag-icon {
      color: $color-brand;
    }

    .tag-check {
      opacity: 1;
      transform: scale(1);
    }
  }
}

.tag-radio {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.tag-content {
  display: flex;
  align-items: center;
  gap: 4px;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  font-weight: 500;
  transition: color $transition-fast;
}

.tag-icon {
  font-size: 18px;
  color: $color-on-surface-variant;
  transition: color $transition-fast;
}

.tag-check {
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transform: scale(0.5);
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
  }
}

// ============ 开关 ============
.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-xs 0;
}

.switch-label {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: $font-size-body-md;
  font-weight: 500;
  color: $color-on-surface;
  cursor: pointer;

  .switch-icon {
    font-size: 20px;
    color: $color-brand;
  }
}

.switch {
  position: relative;
  width: 52px;
  height: 28px;
  flex-shrink: 0;
}

.switch-input {
  position: absolute;
  opacity: 0;
  width: 0;
  height: 0;
}

.switch-slider {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: $color-outline-variant;
  border-radius: 14px;
  transition: background $transition-fast;

  &::before {
    content: '';
    position: absolute;
    top: 2px;
    left: 2px;
    width: 24px;
    height: 24px;
    background: #ffffff;
    border-radius: 50%;
    box-shadow: $shadow-sm;
    transition: transform $transition-fast;
  }
}

.switch-input:checked + .switch-slider {
  background: $color-brand;

  &::before {
    transform: translateX(24px);
  }
}

.switch-input:focus-visible + .switch-slider {
  box-shadow: 0 0 0 3px rgba(255, 122, 50, 0.3);
}

// ============ 操作栏 ============
.action-bar {
  margin-top: $spacing-md;
}

.submit-btn {
  @include button-primary;
  width: 100%;
  gap: $spacing-xs;
  font-size: $font-size-body-lg;
  box-shadow: $shadow-md;

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.spinning {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

// ============ 加载状态 ============
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-3xl;
  min-height: 40vh;

  .spinner {
    width: 32px;
    height: 32px;
    border: 3px solid $color-outline-variant;
    border-top-color: $color-brand;
    border-radius: 50%;
    animation: spin 1s linear infinite;
    margin-bottom: $spacing-md;
  }

  p {
    font-size: $font-size-body-md;
    color: $color-on-surface-variant;
  }
}
</style>
