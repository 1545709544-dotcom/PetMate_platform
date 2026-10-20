<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// ============================================================
// 表单数据
// ============================================================
const phone = ref('')
const nickname = ref('')
const password = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const agreed = ref(false)

// 密码显隐
const showPassword = ref(false)
const showConfirmPassword = ref(false)

// 密码强度：最少 6 位，恰好 6 位（最低长度）一律判为「弱」
const passwordStrength = computed(() => {
  const p = password.value
  if (!p) return 0
  if (p.length < 6) return 0 // 不足最低长度，视为未达标（配合校验提示需至少 6 位）
  if (p.length === 6) return 1 // 6 位即「弱」
  let score = 1
  if (p.length >= 8) score += 1
  if (/[A-Za-z]/.test(p) && /\d/.test(p)) score += 1
  if (/[^A-Za-z0-9]/.test(p) || p.length >= 12) score += 1
  return Math.min(score, 3)
})
const strengthLabel = computed(() => ['请输入密码', '弱', '中', '强'][passwordStrength.value])
const strengthColor = computed(() => {
  return ['#e2e9ec', '#ff4d4f', '#faad14', '#52c41a'][passwordStrength.value]
})

// 校验
const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const nicknameValid = computed(() => nickname.value.trim().length >= 1 && nickname.value.trim().length <= 20)
const passwordValid = computed(() => password.value.length >= 6 && password.value.length <= 32)
const passwordMatch = computed(() => password.value.length > 0 && password.value === confirmPassword.value)
const canSubmit = computed(() =>
  phoneValid.value &&
  nicknameValid.value &&
  passwordValid.value &&
  passwordMatch.value &&
  agreed.value,
)

// ============================================================
// 提交注册
// ============================================================
function validate(): string | null {
  if (!phoneValid.value) return '请输入正确的手机号'
  if (!nicknameValid.value) return '请输入 1-20 字昵称'
  if (!passwordValid.value) return '密码长度需 6-32 位'
  if (!passwordMatch.value) return '两次密码输入不一致'
  if (!agreed.value) return '请先同意用户协议与隐私政策'
  return null
}

async function handleRegister() {
  const err = validate()
  if (err) {
    ElMessage.warning(err)
    return
  }
  submitting.value = true
  try {
    await userStore.register({
      phone: phone.value,
      nickname: nickname.value.trim(),
      password: password.value,
    })
    ElMessage.success('注册成功，已自动登录')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    // 错误已由拦截器提示
  } finally {
    submitting.value = false
  }
}

function goLogin() {
  router.push('/login')
}
</script>

<template>
  <main class="register-page">
    <div class="bg-decoration" aria-hidden="true"></div>

    <div class="container">
      <!-- 左侧：视觉叙事（与登录保持完全一致） -->
      <section class="visual-section">
        <div class="visual-header">
          <h1 class="brand-title">PetMate</h1>
          <p class="brand-tagline">智慧宠物关怀，让爱更有温度。</p>
        </div>

        <div class="visual-image-wrap">
          <div class="image-glow"></div>
          <div class="image-frame">
            <img
              class="visual-image"
              alt="一位年轻女性在阳光客厅中与金毛犬相拥，温暖明亮的家庭氛围"
              src="https://lh3.googleusercontent.com/aida-public/AB6AXuCTxwzcZ_B7FvVufBlWev7n04X1O2EH7pbyh79Hgp_8VmgH-4dbLpw5umw_J0VBFARrqFQ1E31bKDAUqhuLPbGfh9hm8S1Bd9odaPeDJWeOqyFAJmHfxPl_rFNV0_yxKvJ6FXuTKZdq5wxB-v8dabfBpn1YiO0XJPPXK0PPr9XorJFbfePBI8M_jG2m2v_FGV714CQgq8J15MVF5UMvkdul8BDd0IG672UFponl_yRRbQxJDTZAJgVBhRV26JoJa0U4qNiJUfxodyE"
            />
          </div>
          <div class="floating-badge">
            <div class="badge-icon">
              <span class="material-symbols-outlined">pets</span>
            </div>
            <div class="badge-text">
              <p class="badge-title">10,000+ 宠物</p>
              <p class="badge-sub">已加入社区</p>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧：注册表单 -->
      <section class="form-section">
        <div class="form-card">
          <div class="card-pattern" aria-hidden="true"></div>

          <header class="form-header">
            <h2 class="mobile-brand">PetMate</h2>
            <h2 class="form-title">加入 PetMate</h2>
            <p class="form-subtitle">填写以下信息，开启您的智慧养宠之旅</p>
          </header>

          <form class="form" @submit.prevent="handleRegister">
            <!-- 手机号 -->
            <div class="field">
              <label class="field-label">手机号码</label>
              <div class="input-wrap">
                <span class="input-icon material-symbols-outlined">smartphone</span>
                <input
                  v-model="phone"
                  class="form-input"
                  type="tel"
                  placeholder="请输入手机号"
                  maxlength="11"
                />
              </div>
            </div>

            <!-- 昵称 -->
            <div class="field">
              <label class="field-label">昵称</label>
              <div class="input-wrap">
                <span class="input-icon material-symbols-outlined">person</span>
                <input
                  v-model="nickname"
                  class="form-input"
                  type="text"
                  placeholder="您希望被怎么称呼"
                  maxlength="20"
                />
              </div>
            </div>

            <!-- 密码 -->
            <div class="field">
              <label class="field-label">密码</label>
              <div class="input-wrap">
                <span class="input-icon material-symbols-outlined">lock</span>
                <input
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  class="form-input"
                  placeholder="6-32 位密码"
                  maxlength="32"
                />
                <button
                  type="button"
                  class="toggle-pwd"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <span class="material-symbols-outlined">
                    {{ showPassword ? 'visibility' : 'visibility_off' }}
                  </span>
                </button>
              </div>
              <!-- 密码强度 -->
              <div v-if="password" class="strength">
                <div class="strength-bar">
                  <div
                    class="strength-fill"
                    :style="{
                      width: `${(passwordStrength / 3) * 100}%`,
                      background: strengthColor,
                    }"
                  />
                </div>
                <span class="strength-label" :style="{ color: strengthColor }">
                  {{ strengthLabel }}
                </span>
              </div>
            </div>

            <!-- 确认密码 -->
            <div class="field">
              <label class="field-label">确认密码</label>
              <div class="input-wrap">
                <span class="input-icon material-symbols-outlined">lock_reset</span>
                <input
                  v-model="confirmPassword"
                  :type="showConfirmPassword ? 'text' : 'password'"
                  class="form-input"
                  placeholder="请再次输入密码"
                  maxlength="32"
                />
                <button
                  type="button"
                  class="toggle-pwd"
                  :aria-label="showConfirmPassword ? '隐藏密码' : '显示密码'"
                  @click="showConfirmPassword = !showConfirmPassword"
                >
                  <span class="material-symbols-outlined">
                    {{ showConfirmPassword ? 'visibility' : 'visibility_off' }}
                  </span>
                </button>
              </div>
              <!-- 密码一致提示 -->
              <div
                v-if="confirmPassword"
                class="match-hint"
                :class="{ ok: passwordMatch, fail: !passwordMatch }"
              >
                <span class="material-symbols-outlined">{{
                  passwordMatch ? 'check_circle' : 'cancel'
                }}</span>
                <span>{{ passwordMatch ? '两次密码一致' : '两次密码不一致' }}</span>
              </div>
            </div>

            <!-- 协议 -->
            <div class="agreement">
              <input id="agree" v-model="agreed" class="agree-checkbox" type="checkbox" />
              <label for="agree" class="agree-label">
                我已阅读并同意
                <a class="link-secondary" href="javascript:;" @click.stop>《用户服务协议》</a> 与
                <a class="link-secondary" href="javascript:;" @click.stop>《隐私政策》</a>
              </label>
            </div>

            <!-- 主按钮 -->
            <button
              type="submit"
              class="primary-btn"
              :class="{ ready: canSubmit }"
              :disabled="submitting"
            >
              <span>立即注册</span>
            </button>
          </form>

          <div class="footer-row">
            <span class="footer-text">已有账号？</span>
            <a class="link-primary" href="javascript:;" @click="goLogin">立即登录</a>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

// ====== 页面骨架（与 LoginView 完全一致） ======
.register-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: $color-background;
  padding: $width-margin-mobile;
}

.bg-decoration {
  position: absolute;
  inset: 0;
  z-index: 0;
  opacity: 0.2;
  background: radial-gradient(circle at 20% 30%, rgba($color-brand, 0.12), transparent 60%),
    radial-gradient(circle at 80% 70%, rgba($color-secondary-light, 0.18), transparent 60%);
}

.container {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: $width-content-max;
  display: grid;
  gap: $spacing-3xl;
  align-items: center;

  @include desktop {
    grid-template-columns: 1fr 1fr;
    padding: 0 $width-margin-desktop;
    gap: 48px;
  }
}

// ====== 左侧视觉 ======
.visual-section {
  display: none;

  @include desktop {
    display: flex;
    flex-direction: column;
    gap: $spacing-xl;
    padding-right: 48px;
  }
}

.visual-header {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.brand-title {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: $font-weight-extrabold;
  line-height: $line-height-tight;
  letter-spacing: -0.02em;
  color: $color-brand;
}

.brand-tagline {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-semibold;
  line-height: $line-height-normal;
  color: $color-text-secondary;
  max-width: 28rem;
}

.visual-image-wrap {
  position: relative;
}

.image-glow {
  position: absolute;
  inset: -16px;
  background: rgba($color-brand-container, 0.2);
  border-radius: $radius-xl;
  filter: blur(32px);
  transition: background $transition-slow;
}

.image-frame {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: $radius-xl;
  overflow: hidden;
  box-shadow: $shadow-lg;
  border: 4px solid $color-surface-container-lowest;
}

.visual-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.floating-badge {
  position: absolute;
  right: -24px;
  bottom: -24px;
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-lg;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: $radius-xl;
  box-shadow: $shadow-lg;
  border: 1px solid rgba(255, 255, 255, 0.5);
  animation: bounce 2s infinite;
}

.floating-badge:hover {
  animation: none;
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-6px);
  }
}

.badge-icon {
  width: 48px;
  height: 48px;
  background: $color-brand-container;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-on-brand;
}

.badge-icon .material-symbols-outlined {
  font-size: 28px;
}

.badge-title {
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  letter-spacing: 0.01em;
  color: $color-on-surface;
}

.badge-sub {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

// ====== 右侧表单 ======
.form-section {
  width: 100%;
  max-width: 28rem;
  margin: 0 auto;
}

.form-card {
  position: relative;
  overflow: hidden;
  background: $color-surface-container-lowest;
  padding: $spacing-xl $spacing-xl;
  border-radius: $radius-xl;
  box-shadow: $shadow-lg;
  border: 1px solid rgba($color-outline-variant, 0.3);

  @include desktop {
    padding: 40px;
  }
}

.card-pattern {
  position: absolute;
  top: 0;
  right: 0;
  width: 128px;
  height: 128px;
  margin-right: -48px;
  margin-top: -48px;
  border-radius: 50%;
  background-image: radial-gradient($color-brand 0.5px, transparent 0.5px);
  background-size: 24px 24px;
  opacity: 0.05;
  pointer-events: none;
}

.form-header {
  position: relative;
  text-align: left;
  margin-bottom: $spacing-xl;

  @include respond-to('mobile') {
    text-align: center;
  }
}

.mobile-brand {
  display: none;
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg-mobile;
  font-weight: $font-weight-bold;
  color: $color-brand;
  margin-bottom: $spacing-lg;

  @include respond-to('mobile') {
    display: block;
    text-align: center;
  }
}

.form-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: $font-weight-bold;
  line-height: $line-height-tight;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;
}

.form-subtitle {
  font-size: $font-size-body-md;
  color: $color-text-secondary;
}

// ====== 表单 ======
.form {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.field {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.field-label {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-medium;
  color: $color-text-secondary;
  padding-left: $spacing-xs;
}

.input-wrap {
  position: relative;
}

.input-icon {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: $color-outline;
  font-size: 22px;
  pointer-events: none;
  transition: color $transition-fast;
}

.form-input {
  width: 100%;
  padding: 12px 16px 12px 40px;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  outline: none;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &::placeholder {
    color: $color-outline;
  }

  &:focus {
    border-color: $color-secondary;
    box-shadow: 0 0 0 2px rgba($color-secondary, 0.2);
  }
}

.input-wrap:focus-within .input-icon {
  color: $color-secondary;
}

.toggle-pwd {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  background: transparent;
  border: none;
  color: $color-outline;
  cursor: pointer;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    color: $color-on-surface;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

// ====== 密码强度 ======
.strength {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 0 $spacing-xs;
}

.strength-bar {
  flex: 1;
  height: 4px;
  background: $color-surface-container;
  border-radius: $radius-full;
  overflow: hidden;
}

.strength-fill {
  height: 100%;
  transition: width $transition-base, background $transition-base;
  border-radius: $radius-full;
}

.strength-label {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  min-width: 16px;
  text-align: right;
}

// ====== 密码匹配提示 ======
.match-hint {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 0 $spacing-xs;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-medium;

  .material-symbols-outlined {
    font-size: 16px;
  }

  &.ok {
    color: #52c41a;
  }

  &.fail {
    color: $color-danger;
  }
}

// ====== 协议 ======
.agreement {
  margin-top: $spacing-sm;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  gap: $spacing-xs;
}

.agree-checkbox {
  width: 16px;
  height: 16px;
  accent-color: $color-brand;
  cursor: pointer;
  margin-top: 3px;
  flex-shrink: 0;
}

.agree-label {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  cursor: pointer;
  line-height: 1.6;
}

// ====== 链接 ======
.link-secondary {
  font-size: inherit;
  font-weight: $font-weight-medium;
  color: $color-secondary;
  text-decoration: none;
}

.link-secondary:hover {
  text-decoration: underline;
}

.link-primary {
  color: $color-brand;
  font-weight: $font-weight-bold;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

// ====== 主按钮 ======
.primary-btn {
  position: relative;
  width: 100%;
  padding: 16px;
  margin-top: $spacing-xs;
  background: $color-brand-container;
  color: $color-on-brand;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-semibold;
  border: none;
  border-radius: $radius-lg;
  cursor: pointer;
  box-shadow: 0 4px 0 0 $color-brand-dark;
  transition: background $transition-fast, box-shadow 100ms ease, transform 100ms ease;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;

  // 未 ready：淡色（保持立体阴影但背景低饱和）
  &:not(.ready) {
    opacity: 0.55;
  }

  // ready：实色橙
  &.ready {
    background: $color-brand;
    box-shadow: 0 4px 0 0 $color-brand-dark;
  }

  &:hover {
    box-shadow: 0 2px 0 0 $color-brand-dark;
    transform: translateY(2px);
  }

  &.ready:hover {
    background: $color-brand-dark;
  }

  &:active {
    box-shadow: none;
    transform: translateY(4px);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

// ====== 底部 ======
.footer-row {
  margin-top: $spacing-xl;
  text-align: center;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}
@include mobile {
  .register-page {
    padding: 0.75rem;
  }
  .form-section {
    max-width: 100%;
  }
  .form-card {
    padding: 1.25rem 1rem;
  }
  .form-input {
    padding: 0.75rem 1rem 0.75rem 2.5rem;
    font-size: 0.9375rem;
  }
  .input-icon {
    left: 0.75rem;
    font-size: 1.375rem;
  }
  .toggle-pwd {
    right: 0.75rem;
  }
  .field-label {
    padding-left: 0.25rem;
  }
  .agree-checkbox {
    width: 1rem;
    height: 1rem;
  }
  .match-hint {
    gap: 0.25rem;
  }
  .primary-btn {
    padding: 1rem;
    font-size: 1rem;
  }
  .agreement {
    margin-top: 0.5rem;
  }
  .footer-row {
    margin-top: 1.25rem;
  }
}

</style>