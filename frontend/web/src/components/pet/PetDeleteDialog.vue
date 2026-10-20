<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { petApi } from '@/api/modules/pet'
import type { Pet } from '@/api/modules/pet'

interface Props {
  modelValue: boolean
  pet: Pet | null
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'deleted', pet: Pet): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// 用户必须输入宠物的名字才能点"确认删除"
const confirmText = ref('')
const submitting = ref(false)

const canConfirm = computed(() => {
  if (!props.pet) return false
  return confirmText.value.trim() === props.pet.name.trim()
})

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      // 每次打开重置
      confirmText.value = ''
      submitting.value = false
    }
  },
)

function close() {
  if (submitting.value) return
  open.value = false
}

async function submit() {
  if (!props.pet || !canConfirm.value || submitting.value) return
  submitting.value = true
  try {
    await petApi.remove(props.pet.id)
    ElMessage.success(`「${props.pet.name}」已删除`)
    emit('deleted', props.pet)
    open.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败,请稍后重试')
  } finally {
    submitting.value = false
  }
}

function ageLabel(birthday?: string): string {
  if (!birthday) return '—'
  const b = new Date(birthday)
  if (isNaN(b.getTime())) return '—'
  const now = new Date()
  let years = now.getFullYear() - b.getFullYear()
  const m = now.getMonth() - b.getMonth()
  if (m < 0 || (m === 0 && now.getDate() < b.getDate())) years--
  if (years >= 1) return `${years}岁`
  const months = Math.max(1, years * 12 + m)
  return `${months}个月`
}
</script>

<template>
  <Teleport to="body">
    <Transition name="pm-fade">
      <div v-if="open" class="pm-dialog-mask" @mousedown.self="close">
        <div class="pm-dialog" role="dialog" aria-modal="true">
          <header class="dialog-head">
            <div class="head-icon">
              <span class="material-symbols-outlined">warning</span>
            </div>
            <div>
              <h3 class="head-title">删除宠物档案</h3>
              <p class="head-sub">这是一项不可撤销的操作,请仔细确认</p>
            </div>
          </header>

          <div v-if="pet" class="dialog-body">
            <!-- 宠物预览 -->
            <div class="pet-preview">
              <div class="preview-avatar">
                <img v-if="pet.avatar" :src="pet.avatar" :alt="pet.name" />
                <span v-else class="material-symbols-outlined">pets</span>
              </div>
              <div class="preview-info">
                <p class="preview-name">{{ pet.name }}</p>
                <p class="preview-meta">
                  {{ pet.breedName || '未分类品种' }} · {{ ageLabel(pet.birthday) }}
                </p>
              </div>
            </div>

            <!-- 影响清单 -->
            <div class="impact-card">
              <p class="impact-title">
                <span class="material-symbols-outlined">error_outline</span>
                以下数据将被一并清理
              </p>
              <ul class="impact-list">
                <li>
                  <span class="dot vaccine"></span>
                  完整的健康档案与疫苗记录
                </li>
                <li>
                  <span class="dot deworm"></span>
                  驱虫、就医、体重记录
                </li>
                <li>
                  <span class="dot checkup"></span>
                  过敏史、慢病管理数据
                </li>
              </ul>
              <p class="impact-warning">
                <span class="material-symbols-outlined">info</span>
                历史订单、商家服务关联中的宠物信息将只读保留,但无法继续管理。
              </p>
            </div>

            <!-- 防误触二次确认 -->
            <div class="confirm-field">
              <label class="confirm-label">
                请输入
                <span class="confirm-name">{{ pet.name }}</span>
                以确认删除
              </label>
              <input
                v-model="confirmText"
                class="confirm-input"
                :class="{ 'is-match': canConfirm }"
                type="text"
                :placeholder="`请输入:${pet.name}`"
                maxlength="30"
                autocomplete="off"
                :disabled="submitting"
              />
            </div>
          </div>

          <footer class="dialog-foot">
            <button class="ghost-btn" type="button" :disabled="submitting" @click="close">
              取消
            </button>
            <button
              class="danger-btn"
              type="button"
              :disabled="!canConfirm || submitting"
              @click="submit"
            >
              <span v-if="submitting" class="btn-spinner" aria-hidden="true"></span>
              <span v-else class="material-symbols-outlined">delete</span>
              <span>永久删除</span>
            </button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.pm-dialog-mask {
  position: fixed;
  inset: 0;
  background: rgba(22, 29, 31, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  padding: $spacing-md;
}

.pm-dialog {
  width: 100%;
  max-width: 460px;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-lg;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  max-height: 90vh;
}

.dialog-head {
  display: flex;
  align-items: flex-start;
  gap: $spacing-md;
  padding: $spacing-lg $spacing-lg $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
}

.head-icon {
  width: 44px;
  height: 44px;
  border-radius: $radius-full;
  background: rgba($color-danger, 0.12);
  color: $color-danger;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .material-symbols-outlined {
    font-size: 24px;
    font-variation-settings: 'FILL' 1;
  }
}

.head-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0 0 4px;
}

.head-sub {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 0;
}

.dialog-body {
  padding: $spacing-md $spacing-lg;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.pet-preview {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-default;
}

.preview-avatar {
  width: 56px;
  height: 56px;
  border-radius: $radius-full;
  overflow: hidden;
  background: $color-surface-container;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .material-symbols-outlined {
    font-size: 32px;
    color: $color-outline;
  }
}

.preview-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
}

.preview-meta {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 4px 0 0;
}

.impact-card {
  background: rgba($color-danger, 0.05);
  border: 1px solid rgba($color-danger, 0.18);
  border-radius: $radius-default;
  padding: $spacing-md;
}

.impact-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: $color-danger;
  margin: 0 0 $spacing-sm;

  .material-symbols-outlined {
    font-size: 18px;
    font-variation-settings: 'FILL' 1;
  }
}

.impact-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: $font-size-label-md;
  color: $color-on-surface;

  li {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
  }
}

.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;

  &.vaccine { background: $color-secondary; }
  &.deworm { background: $color-success; }
  &.checkup { background: $color-warning; }
}

.impact-warning {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: $spacing-sm 0 0;
  line-height: 1.5;

  .material-symbols-outlined {
    font-size: 16px;
    color: $color-text-tertiary;
    flex-shrink: 0;
    margin-top: 2px;
  }
}

.confirm-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.confirm-label {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

.confirm-name {
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  padding: 0 4px;
}

.confirm-input {
  padding: 12px 16px;
  background: $color-surface-container-low;
  border: 1px solid transparent;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  outline: none;
  transition: border-color $transition-fast, box-shadow $transition-fast;
  font-family: inherit;

  &:focus {
    border-color: $color-danger;
    box-shadow: 0 0 0 3px rgba($color-danger, 0.15);
  }

  &.is-match {
    border-color: $color-danger;
    box-shadow: 0 0 0 3px rgba($color-danger, 0.1);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

.dialog-foot {
  padding: $spacing-md $spacing-lg $spacing-lg;
  border-top: 1px solid $color-outline-variant;
  display: flex;
  gap: $spacing-sm;
}

.ghost-btn {
  flex: 1;
  padding: 12px $spacing-lg;
  background: $color-surface-container-low;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
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

.danger-btn {
  flex: 1.5;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 12px $spacing-lg;
  background: $color-danger;
  color: #fff;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  box-shadow: 0 6px 14px rgba($color-danger, 0.3);
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 10px 18px rgba($color-danger, 0.4);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    box-shadow: none;
    transform: none;
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.btn-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(255, 255, 255, 0.4);
  border-top-color: #fff;
  border-radius: 50%;
  animation: pm-spin 0.8s linear infinite;
}

.pm-fade-enter-active,
.pm-fade-leave-active {
  transition: opacity $transition-fast;

  .pm-dialog {
    transition: transform $transition-fast;
  }
}

.pm-fade-enter-from,
.pm-fade-leave-to {
  opacity: 0;

  .pm-dialog {
    transform: scale(0.96);
  }
}

@keyframes pm-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
