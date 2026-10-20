<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '@/api/request'

const router = useRouter()

// ============================================================
// 表单
// ============================================================
const category = ref('订单问题')
const content = ref('')
const contact = ref('')
const submitting = ref(false)

const CATEGORIES = ['订单问题', '支付/退款', '会员权益', 'AI 问诊', '账号问题', '其他']
const remaining = computed(() => 500 - content.value.length)
const canSubmit = computed(() => content.value.trim().length > 0 && content.value.length <= 500 && !submitting.value)

async function submit() {
  if (!canSubmit.value) {
    ElMessage.warning('请填写问题描述（不超过 500 字）')
    return
  }
  submitting.value = true
  try {
    const res: any = await request({
      url: '/support/contact',
      method: 'POST',
      data: {
        category: category.value,
        content: content.value.trim(),
        contact: contact.value.trim() || undefined,
      },
    })
    ElMessage.success(`已提交（工单 #${res.id || '?'}），请留意「消息中心」回复`)
    setTimeout(() => router.replace('/user/messages'), 1200)
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败，请稍后再试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="support-view">
    <div class="support-card">
      <header class="head">
        <span class="material-symbols-outlined icon">support_agent</span>
        <div>
          <h1 class="title">联系客服</h1>
          <p class="sub">提交后我们会通过站内信回复，平均响应 24 小时内。</p>
        </div>
      </header>

      <form class="form" @submit.prevent="submit">
        <label class="field">
          <span class="field-label">问题类型</span>
          <div class="cat-grid">
            <button
              v-for="c in CATEGORIES"
              :key="c"
              type="button"
              class="cat-chip"
              :class="{ active: category === c }"
              @click="category = c"
            >
              {{ c }}
            </button>
          </div>
        </label>

        <label class="field">
          <span class="field-label">问题描述</span>
          <textarea
            v-model="content"
            class="textarea"
            placeholder="请尽量描述清楚场景、订单号或时间，便于我们快速定位。"
            rows="6"
            maxlength="500"
          />
          <div class="field-foot">
            <span class="counter" :class="{ danger: remaining < 50 }">
              还能输入 {{ remaining }} 字
            </span>
          </div>
        </label>

        <label class="field">
          <span class="field-label">联系方式（选填）</span>
          <input
            v-model="contact"
            class="input"
            placeholder="如手机号 / 微信号，方便客服主动联系您"
            maxlength="64"
          />
        </label>

        <div class="actions">
          <button type="button" class="btn-ghost" @click="router.back()">返回</button>
          <button type="submit" class="btn-primary" :disabled="!canSubmit">
            <span v-if="submitting" class="material-symbols-outlined spin">progress_activity</span>
            <span v-else class="material-symbols-outlined">send</span>
            提交咨询
          </button>
        </div>

        <p class="tip">
          紧急情况请直接拨打当地 24h 宠物医院电话；
          涉及账号安全（封禁、密码），请通过「修改密码」自助处理。
        </p>
      </form>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.support-view {
  min-height: calc(100vh - 200px);
  padding: $spacing-xl $spacing-md;
  background: $color-background;
  display: flex;
  justify-content: center;
}

.support-card {
  width: 100%;
  max-width: 640px;
  background: #ffffff;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-xl;
  box-shadow: $shadow-md;
  padding: $spacing-2xl;
}

.head {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding-bottom: $spacing-xl;
  border-bottom: 1px solid $color-surface-variant;
  margin-bottom: $spacing-xl;

  .icon {
    font-size: 40px;
    color: $color-brand;
    font-variation-settings: 'FILL' 1;
  }
  .title {
    margin: 0;
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: $font-weight-bold;
    color: $color-on-surface;
  }
  .sub {
    margin: 4px 0 0;
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;
  }
}

.form {
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
}

.field {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.field-label {
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
}

.cat-grid {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.cat-chip {
  padding: 6px $spacing-md;
  background: $color-surface-container-low;
  border: 1.5px solid transparent;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand-light;
    color: $color-brand;
  }

  &.active {
    background: rgba(255, 122, 50, 0.1);
    border-color: $color-brand;
    color: $color-brand-dark;
    font-weight: $font-weight-semibold;
  }
}

.textarea,
.input {
  width: 100%;
  padding: $spacing-md;
  background: #ffffff;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-md;
  font-family: inherit;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  resize: vertical;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &:focus {
    outline: none;
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba(255, 122, 50, 0.12);
  }

  &::placeholder {
    color: $color-text-tertiary;
  }
}

.field-foot {
  display: flex;
  justify-content: flex-end;
}

.counter {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;

  &.danger {
    color: #d33;
  }
}

.actions {
  display: flex;
  gap: $spacing-md;
  justify-content: flex-end;
}

.btn-primary,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: $spacing-md $spacing-xl;
  border-radius: $radius-md;
  font-family: $font-family-headline;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: all $transition-base;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:disabled {
    cursor: not-allowed;
    opacity: 0.5;
  }
}

.btn-primary {
  background: linear-gradient(135deg, #ff7a32 0%, #ff9a4d 100%);
  color: #ffffff;
  border: none;
  box-shadow: 0 4px 12px rgba(255, 122, 50, 0.3);

  &:hover:not(:disabled) {
    box-shadow: 0 6px 16px rgba(255, 122, 50, 0.45);
    transform: translateY(-1px);
  }
}

.btn-ghost {
  background: transparent;
  border: 1.5px solid $color-outline-variant;
  color: $color-on-surface;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

.tip {
  margin: 0;
  padding: $spacing-md;
  background: rgba(255, 122, 50, 0.05);
  border-radius: $radius-md;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  line-height: 1.6;
}

.spin {
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>