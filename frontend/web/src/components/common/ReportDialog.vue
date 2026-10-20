<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { reportApi, type ReportTargetType } from '@/api/modules/report'

const props = defineProps<{
  modelValue: boolean
  targetType: ReportTargetType
  targetId: number
  title?: string
}>()

const emit = defineEmits(['update:modelValue', 'submitted'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

// 预置举报理由
const PRESETS = [
  '垃圾广告 / 刷屏',
  '色情低俗内容',
  '辱骂 / 人身攻击',
  '虚假信息 / 诈骗',
  '侵犯权益 / 抄袭',
  '其他违规',
]

const selected = ref('')
const detail = ref('')
const submitting = ref(false)

const canSubmit = computed(() => !!selected.value && !submitting.value)

async function handleSubmit() {
  if (!selected.value) {
    ElMessage.warning('请选择举报理由')
    return
  }
  submitting.value = true
  try {
    const reason = selected.value + (detail.value.trim() ? `：${detail.value.trim()}` : '')
    await reportApi.submit({
      targetType: props.targetType,
      targetId: props.targetId,
      reason,
    })
    ElMessage.success('举报已提交，感谢你的反馈')
    emit('submitted')
    visible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '举报提交失败')
  } finally {
    submitting.value = false
  }
}

function onClosed() {
  selected.value = ''
  detail.value = ''
  submitting.value = false
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title || '举报'"
    width="460px"
    align-center
    @closed="onClosed"
  >
    <div class="report-dialog">
      <p class="tip">请选择举报理由，我们会尽快核实处理：</p>

      <div class="presets">
        <button
          v-for="p in PRESETS"
          :key="p"
          type="button"
          class="preset"
          :class="{ active: selected === p }"
          @click="selected = p"
        >
          {{ p }}
        </button>
      </div>

      <textarea
        v-model="detail"
        class="detail"
        rows="3"
        maxlength="200"
        placeholder="补充说明（选填，最多 200 字）"
      />
    </div>

    <template #footer>
      <button class="btn-cancel" type="button" @click="visible = false">取消</button>
      <button class="btn-submit" type="button" :disabled="!canSubmit" @click="handleSubmit">
        {{ submitting ? '提交中…' : '提交举报' }}
      </button>
    </template>
  </el-dialog>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.report-dialog {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.tip {
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  margin: 0;
}

.presets {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.preset {
  padding: 8px 14px;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  background: $color-surface-container-lowest;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  cursor: pointer;
  transition: border-color $transition-fast, color $transition-fast, background $transition-fast;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }

  &.active {
    border-color: $color-brand;
    background: $color-brand-container;
    color: $color-brand;
    font-weight: $font-weight-semibold;
  }
}

.detail {
  width: 100%;
  box-sizing: border-box;
  padding: $spacing-sm $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  background: $color-surface-container-lowest;
  color: $color-on-surface;
  font-size: $font-size-body-md;
  font-family: inherit;
  resize: vertical;
  outline: none;
  transition: border-color $transition-fast;

  &:focus {
    border-color: $color-brand;
  }
}

.btn-cancel,
.btn-submit {
  padding: 8px 20px;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  border: none;
}

.btn-cancel {
  background: $color-surface-container-low;
  color: $color-text-secondary;

  &:hover {
    background: $color-surface-container;
  }
}

.btn-submit {
  background: $color-brand;
  color: $color-on-brand;

  &:hover:not(:disabled) {
    background: $color-brand-dark;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}
</style>
