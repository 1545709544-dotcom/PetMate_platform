<script setup lang="ts">
/**
 * 售后申请弹窗。
 *
 * 替代原先那个只收一句 reason 的 ElMessageBox.prompt —— 后端早就支持
 * type + images 了，前端一直没给出口，等于把退货退款和凭证功能白白浪费。
 * 这里补齐：类型单选卡片 + 原因 + 凭证图（最多 6 张）。
 */
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { aftersaleApi, type AftersaleType } from '@/api/modules/aftersale'
import { communityApi } from '@/api/modules/community'

const props = defineProps<{
  modelValue: boolean
  orderNo: string
  /** 订单当前状态，决定哪些售后类型可选 */
  orderStatus: string
  /** 订单实付金额，展示「最多可退」 */
  payAmount: number
}>()

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const TYPE_OPTIONS: Array<{
  value: AftersaleType
  title: string
  desc: string
  icon: string
}> = [
  {
    value: 'REFUND_ONLY',
    title: '仅退款',
    desc: '未收到货，或与商家协商一致无需退货',
    icon: 'currency_yuan',
  },
  {
    value: 'RETURN_REFUND',
    title: '退货退款',
    desc: '已收到货，需寄回商品后退款',
    icon: 'assignment_return',
  },
  {
    value: 'EXCHANGE',
    title: '换货',
    desc: '商品有问题，寄回后由商家换新，不退款',
    icon: 'swap_horiz',
  },
]

// 未发货时换货没有意义（东西还没出库），直接退款更快
const availableTypes = computed(() =>
  props.orderStatus === 'paid'
    ? TYPE_OPTIONS.filter((t) => t.value !== 'EXCHANGE')
    : TYPE_OPTIONS,
)

const form = ref<{ type: AftersaleType; reason: string; images: string[] }>({
  type: 'REFUND_ONLY',
  reason: '',
  images: [],
})
const submitting = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const REASON_PRESETS = [
  '商品质量问题',
  '收到商品与描述不符',
  '包装破损 / 少件漏发',
  '临期或已过期',
  '宠物不适应 / 不爱吃',
  '不想要了',
]

watch(visible, (v) => {
  if (v) {
    form.value = { type: availableTypes.value[0].value, reason: '', images: [] }
  }
})

function pickPreset(text: string) {
  form.value.reason = form.value.reason ? `${form.value.reason}；${text}` : text
}

function triggerUpload() {
  if (form.value.images.length >= 6) {
    ElMessage.warning('最多上传 6 张凭证图')
    return
  }
  fileInput.value?.click()
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = '' // 允许重复选同一张
  if (!files.length) return

  const remain = 6 - form.value.images.length
  const picked = files.slice(0, remain)
  const oversize = picked.find((f) => f.size > 5 * 1024 * 1024)
  if (oversize) {
    ElMessage.warning('单张图片不能超过 5MB')
    return
  }

  uploading.value = true
  try {
    const res = await communityApi.uploadImages(picked)
    form.value.images.push(...(res?.urls || []))
  } catch (err: any) {
    ElMessage.error(err?.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function removeImage(idx: number) {
  form.value.images.splice(idx, 1)
}

async function submit() {
  const reason = form.value.reason.trim()
  if (!reason) {
    ElMessage.warning('请填写售后原因，方便商家快速处理')
    return
  }
  submitting.value = true
  try {
    await aftersaleApi.apply(props.orderNo, {
      type: form.value.type,
      reason,
      images: form.value.images,
    })
    ElMessage.success('售后申请已提交，可在「我的售后」查看进度')
    visible.value = false
    emit('success')
  } catch (err: any) {
    ElMessage.error(err?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="申请售后"
    width="560px"
    :close-on-click-modal="false"
    class="aftersale-apply-dialog"
  >
    <div class="apply-body">
      <!-- 售后类型 -->
      <div class="field">
        <label class="field-label">售后类型</label>
        <div class="type-list">
          <button
            v-for="t in availableTypes"
            :key="t.value"
            type="button"
            class="type-card"
            :class="{ active: form.type === t.value }"
            @click="form.type = t.value"
          >
            <span class="material-symbols-outlined type-icon">{{ t.icon }}</span>
            <span class="type-text">
              <strong>{{ t.title }}</strong>
              <em>{{ t.desc }}</em>
            </span>
            <span class="material-symbols-outlined check">
              {{ form.type === t.value ? 'radio_button_checked' : 'radio_button_unchecked' }}
            </span>
          </button>
        </div>
      </div>

      <!-- 退款金额提示 -->
      <div v-if="form.type !== 'EXCHANGE'" class="amount-tip">
        <span class="material-symbols-outlined">info</span>
        最多可退 <strong>¥{{ payAmount.toFixed(2) }}</strong>（订单实付金额），
        商家审核时可能会与你协商调整实退金额
      </div>
      <div v-else class="amount-tip">
        <span class="material-symbols-outlined">info</span>
        换货不涉及退款，商家收到退回商品后会为你寄出新的商品
      </div>

      <!-- 原因 -->
      <div class="field">
        <label class="field-label">售后原因 <i class="required">*</i></label>
        <div class="preset-row">
          <button
            v-for="p in REASON_PRESETS"
            :key="p"
            type="button"
            class="preset-chip"
            @click="pickPreset(p)"
          >
            {{ p }}
          </button>
        </div>
        <el-input
          v-model="form.reason"
          type="textarea"
          :rows="3"
          maxlength="200"
          show-word-limit
          placeholder="请描述遇到的问题，描述越清楚处理越快"
        />
      </div>

      <!-- 凭证图 -->
      <div class="field">
        <label class="field-label">
          上传凭证
          <span class="field-hint">最多 6 张，有图的申请通过更快</span>
        </label>
        <div class="image-grid">
          <div v-for="(img, i) in form.images" :key="i" class="image-cell">
            <img :src="img" alt="凭证" />
            <button type="button" class="remove-btn" @click="removeImage(i)">
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
          <button
            v-if="form.images.length < 6"
            type="button"
            class="image-cell upload-cell"
            :disabled="uploading"
            @click="triggerUpload"
          >
            <span class="material-symbols-outlined">
              {{ uploading ? 'hourglass_top' : 'add_a_photo' }}
            </span>
            <em>{{ uploading ? '上传中' : '添加图片' }}</em>
          </button>
        </div>
        <input
          ref="fileInput"
          type="file"
          accept="image/*"
          multiple
          hidden
          @change="onFileChange"
        />
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">再想想</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交申请</el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.apply-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.field-label {
  display: block;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: 10px;

  .required {
    color: $color-emergency;
    font-style: normal;
  }

  .field-hint {
    margin-left: 8px;
    font-weight: 400;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
  }
}

// ---------- 类型选择 ----------
.type-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.type-card {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1.5px solid $color-outline-variant;
  border-radius: 12px;
  background: $color-surface;
  cursor: pointer;
  text-align: left;
  transition: all $transition-fast;

  &:hover {
    border-color: rgba(255, 122, 50, 0.5);
  }

  &.active {
    border-color: $color-brand;
    background: rgba(255, 122, 50, 0.06);

    .type-icon,
    .check {
      color: $color-brand;
    }
  }
}

.type-icon {
  font-size: 24px;
  color: $color-on-surface-variant;
  flex-shrink: 0;
}

.type-text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;

  strong {
    font-size: $font-size-body-md;
    font-weight: 600;
    color: $color-on-surface;
  }

  em {
    font-style: normal;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
  }
}

.check {
  font-size: 20px;
  color: $color-outline;
  flex-shrink: 0;
}

// ---------- 金额提示 ----------
.amount-tip {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border-radius: 10px;
  background: rgba(255, 122, 50, 0.08);
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1.5;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
    flex-shrink: 0;
  }

  strong {
    color: $color-emergency;
  }
}

// ---------- 快捷原因 ----------
.preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.preset-chip {
  padding: 5px 12px;
  border: 1px solid $color-outline-variant;
  border-radius: 999px;
  background: transparent;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
    background: rgba(255, 122, 50, 0.06);
  }
}

// ---------- 凭证图 ----------
.image-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, 88px);
  gap: 10px;
}

.image-cell {
  position: relative;
  width: 88px;
  height: 88px;
  border-radius: 10px;
  overflow: hidden;
  background: $color-surface-variant;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.remove-btn {
  position: absolute;
  top: 2px;
  right: 2px;
  width: 20px;
  height: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;

  .material-symbols-outlined {
    font-size: 14px;
  }
}

.upload-cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  border: 1.5px dashed $color-outline-variant;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover:not(:disabled) {
    border-color: $color-brand;
    color: $color-brand;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }

  .material-symbols-outlined {
    font-size: 22px;
  }

  em {
    font-style: normal;
    font-size: 11px;
  }
}
</style>
