<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/modules/auth'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const phone = ref('')
const password = ref('')
const submitting = ref(false)
const agreed = ref(false)
const showPwd = ref(false)

// 表单是否填写完整（用于按钮"实色 vs 淡色"切换）
const phoneOk = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const passwordOk = computed(() => password.value.length >= 6)
const formReady = computed(() => agreed.value && phoneOk.value && passwordOk.value)

async function handleLogin() {
  if (!agreed.value) {
    ElMessage.warning('请先同意用户协议')
    return
  }
  if (!phoneOk.value) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!passwordOk.value) {
    ElMessage.warning('密码至少 6 位')
    return
  }

  submitting.value = true
  try {
    await userStore.login({
      phone: phone.value,
      username: phone.value,
      password: password.value,
      loginType: 'password',
    })
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    // 错误已在拦截器中提示
  } finally {
    submitting.value = false
  }
}

function goRegister() {
  router.push('/register')
}

function goMerchant() {
  router.push('/merchant/landing')
}

function togglePwd() {
  showPwd.value = !showPwd.value
}

// ============ 找回密码（演示态：手机号 + 新密码，无短信验证码）============
const resetOpen = ref(false)
const resetPhone = ref('')
const resetPwd = ref('')
const resetPwd2 = ref('')
const resetting = ref(false)
const resetPhoneOk = computed(() => /^1[3-9]\d{9}$/.test(resetPhone.value))
const resetPwdOk = computed(() => resetPwd.value.length >= 6 && resetPwd.value === resetPwd2.value)

function openReset() {
  resetPhone.value = phoneOk.value ? phone.value : ''
  resetPwd.value = ''
  resetPwd2.value = ''
  resetOpen.value = true
}

async function submitReset() {
  if (!resetPhoneOk.value) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!resetPwdOk.value) {
    ElMessage.warning('两次输入的密码需一致且至少 6 位')
    return
  }
  resetting.value = true
  try {
    await authApi.resetPassword(resetPhone.value, resetPwd.value)
    ElMessage.success('密码已重置，请用新密码登录')
    resetOpen.value = false
    phone.value = resetPhone.value
    password.value = ''
  } catch {
    // 错误已由全局拦截器提示
  } finally {
    resetting.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <div class="container">
      <!-- 左侧：视觉叙事 -->
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
              src="/img/login-hero.jpg"
            />
          </div>
          <!-- 浮动徽章 -->
          <div class="floating-badge">
            <div class="badge-icon">
              <span class="material-symbols-outlined">pets</span>
            </div>
            <div class="badge-text">
              <p class="badge-title">10,000+ 宠物</p>
              <p class="badge-sub">已加入社区</p>
            </div>
          </div>

          <!-- 评分胶囊 -->
          <div class="visual-chip">
            <span class="material-symbols-outlined">verified</span>
            <span>4.9 分 · 宠主信赖之选</span>
          </div>
        </div>
      </section>

      <!-- 右侧：登录卡片 -->
      <section class="form-section">
        <div class="form-card">
          <div class="card-pattern" aria-hidden="true"></div>

          <!-- 头部 -->
          <header class="form-header">
            <h2 class="mobile-brand">PetMate</h2>
            <h2 class="form-title">欢迎回来</h2>
            <p class="form-subtitle">与您的爱宠共享智慧生活</p>
          </header>

          <!-- 表单 -->
          <form class="form" @submit.prevent="handleLogin">
            <!-- 手机号（用户模式） -->
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

            <!-- 密码 -->
            <div class="field">
              <label class="field-label">登录密码</label>
              <div class="input-wrap">
                <span class="input-icon material-symbols-outlined">lock</span>
                <input
                  v-model="password"
                  class="form-input"
                  :type="showPwd ? 'text' : 'password'"
                  placeholder="请输入密码"
                  autocomplete="current-password"
                />
                <button
                  type="button"
                  class="toggle-pwd"
                  :aria-label="showPwd ? '隐藏密码' : '显示密码'"
                  @click="togglePwd"
                >
                  <span class="material-symbols-outlined">{{ showPwd ? 'visibility' : 'visibility_off' }}</span>
                </button>
              </div>
              <div class="field-extra">
                <a class="link-secondary" href="javascript:;" @click="openReset">忘记密码？</a>
              </div>
            </div>

            <!-- 主按钮 -->
            <button
              type="submit"
              class="primary-btn"
              :class="{ ready: formReady }"
              :disabled="submitting"
            >
              <span>立即登录</span>
            </button>

            <!-- 跳转注册 -->
            <div class="to-register">
              还没有账号?
              <a class="link-primary" href="javascript:;" @click="goRegister">立即注册</a>
            </div>
          </form>

          <!-- 商家入口 -->
          <div class="merchant-entry">
            <a class="merchant-link" href="javascript:;" @click="goMerchant">
              <span class="merchant-text">我是商家，跳转到商家中心</span>
              <span class="material-symbols-outlined arrow">chevron_right</span>
            </a>
          </div>

          <!-- 协议 -->
          <div class="agreement">
            <input id="agree" v-model="agreed" class="agree-checkbox" type="checkbox" />
            <label for="agree" class="agree-label">
              我已阅读并同意
              <a class="link-secondary" href="javascript:;">用户协议</a> 与
              <a class="link-secondary" href="javascript:;">隐私政策</a>
            </label>
          </div>
        </div>

        <!-- 移动端：注册入口 -->
        <p class="mobile-register">
          还没有账号？
          <a class="link-primary" href="javascript:;" @click="router.push('/register')">立即注册</a>
        </p>
      </section>
    </div>

    <!-- 找回密码 -->
    <el-dialog v-model="resetOpen" title="找回密码" width="420px" align-center>
      <div class="reset-form">
        <div class="field">
          <label class="field-label">手机号</label>
          <div class="input-wrap">
            <span class="input-icon material-symbols-outlined">smartphone</span>
            <input v-model="resetPhone" class="form-input" type="tel" placeholder="请输入注册手机号" maxlength="11" />
          </div>
        </div>
        <div class="field">
          <label class="field-label">新密码</label>
          <div class="input-wrap">
            <span class="input-icon material-symbols-outlined">lock</span>
            <input v-model="resetPwd" class="form-input" type="password" placeholder="至少 6 位" />
          </div>
        </div>
        <div class="field">
          <label class="field-label">确认新密码</label>
          <div class="input-wrap">
            <span class="input-icon material-symbols-outlined">lock</span>
            <input v-model="resetPwd2" class="form-input" type="password" placeholder="再次输入新密码" />
          </div>
        </div>
        <p class="reset-tip">演示环境：直接设置新密码即可，无需短信验证码。</p>
      </div>
      <template #footer>
        <button class="primary-btn" :class="{ ready: resetPhoneOk && resetPwdOk }" :disabled="resetting" @click="submitReset">
          <span>{{ resetting ? '提交中…' : '确认重置' }}</span>
        </button>
      </template>
    </el-dialog>
  </main>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;
@use '@/styles/mixin.scss' as *;

// ====== 页面骨架 ======
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  // 安静的暖色渐变：不流动、不抢眼，仅提供柔和的层次与温度
  background:
    radial-gradient(1100px 620px at 88% -8%, rgba($color-brand, 0.07), transparent 60%),
    radial-gradient(900px 560px at -8% 112%, rgba($color-secondary, 0.05), transparent 60%),
    $color-background;
  padding: $width-margin-mobile;
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
  background: linear-gradient(120deg, $color-brand-dark, $color-brand 55%, $color-brand-light);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
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
  transition: transform 0.9s cubic-bezier(0.16, 1, 0.3, 1);
}

.visual-image-wrap:hover .visual-image {
  transform: scale(1.05);
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
}

// 评分胶囊（左侧视觉面板的玻璃标签）
.visual-chip {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-radius: $radius-full;
  box-shadow: $shadow-lg;
  border: 1px solid rgba(255, 255, 255, 0.5);
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
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
  background: rgba($color-surface-container-lowest, 0.78);
  backdrop-filter: blur(20px) saturate(160%);
  -webkit-backdrop-filter: blur(20px) saturate(160%);
  padding: $spacing-xl $spacing-xl;
  border-radius: $radius-xl;
  box-shadow: $shadow-lg, 0 24px 60px -28px rgba($color-brand, 0.28);
  border: 1px solid rgba($color-outline-variant, 0.45);
  animation: fadeIn 0.6s ease both;

  @include desktop {
    padding: 44px;
  }
}

// 顶部高光反射（静态，增强玻璃质感）
.form-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 14%;
  right: 14%;
  height: 1px;
  background: linear-gradient(
    90deg,
    transparent,
    rgba(255, 255, 255, 0.7),
    transparent
  );
  pointer-events: none;
  z-index: 1;
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
  margin-bottom: $spacing-lg;

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

// ====== 登录模式切换 ======
.login-tabs {
  display: flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: $spacing-lg;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
}

.login-tab {
  flex: 1;
  padding: 10px 0;
  border: none;
  background: transparent;
  border-radius: $radius-default;
  font-family: $font-family-body;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: $color-text-secondary;
  cursor: pointer;
  transition: background-color $transition-fast, color $transition-fast, box-shadow $transition-fast;

  &:hover {
    color: $color-on-surface;
  }

  &.active {
    background: $color-surface-container-lowest;
    color: $color-brand;
    box-shadow: 0 2px 8px -2px rgba($color-brand, 0.25);
  }
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
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.18);
  }
}

.input-wrap:focus-within .input-icon {
  color: $color-brand;
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

.field-extra {
  text-align: right;
}

.link-secondary {
  font-size: $font-size-label-sm;
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
  overflow: hidden;
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
  box-shadow: 0 8px 20px -6px rgba($color-brand, 0.45);
  transition: background $transition-fast, box-shadow $transition-fast, transform $transition-fast;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;

  // 未 ready：淡色（低饱和的 brand-container），仍有柔和阴影
  &:not(.ready) {
    opacity: 0.55;
  }

  // ready：实色橙
  &.ready {
    background: $color-brand;
    box-shadow: 0 8px 20px -6px rgba($color-brand, 0.45);
  }

  &:hover {
    box-shadow: 0 10px 26px -6px rgba($color-brand, 0.55);
    transform: translateY(-1px);
  }

  &.ready:hover {
    background: $color-brand-dark;
  }

  &:active {
    box-shadow: 0 4px 12px -6px rgba($color-brand, 0.45);
    transform: translateY(1px);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  span {
    position: relative;
    z-index: 1;
  }

  // 扫光
  &::after {
    content: '';
    position: absolute;
    top: 0;
    left: -130%;
    width: 65%;
    height: 100%;
    background: linear-gradient(
      120deg,
      transparent,
      rgba(255, 255, 255, 0.45),
      transparent
    );
    transform: skewX(-20deg);
    pointer-events: none;
  }

  &.ready:hover::after {
    animation: btnShimmer 0.9s ease;
  }
}

@keyframes btnShimmer {
  to {
    left: 130%;
  }
}

.to-register {
  margin-top: $spacing-sm;
  text-align: center;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

// ====== 协议 ======
.agreement {
  margin-top: $spacing-xl;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
}

.agree-checkbox {
  width: 16px;
  height: 16px;
  accent-color: $color-brand;
  cursor: pointer;
}

.agree-label {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  cursor: pointer;
}

// ====== 商家入口 ======
.merchant-entry {
  margin-top: $spacing-lg;
  text-align: center;
}

.merchant-link {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-medium;
  color: $color-brand;
  text-decoration: none;
  transition: color $transition-fast;

  // 下划线只作用于文字，不波及箭头图标，避免压到图标导致显示不平
  &:hover {
    color: $color-brand-dark;
  }

  &:hover .merchant-text {
    text-decoration: underline;
    text-underline-offset: 3px;
  }

  .merchant-text {
    line-height: 1.4;
  }

  .arrow {
    font-size: 16px;
    transition: transform $transition-fast;
  }

  &:hover .arrow {
    transform: translateX(2px);
  }
}

// ====== 移动端注册 ======
.mobile-register {
  display: block;
  margin-top: $spacing-lg;
  text-align: center;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;

  @include desktop {
    display: none;
  }
}

// ====== 入场错落动画（backwards：延迟期用 from 防闪，结束后回归正常层叠，不挡 hover/弱化态） ======
.form-header {
  animation: fadeUp 0.6s cubic-bezier(0.16, 1, 0.3, 1) backwards;
  animation-delay: 0.06s;
}

.form .field {
  animation: fadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) backwards;
}

.form .field:nth-of-type(1) { animation-delay: 0.14s; }
.form .field:nth-of-type(2) { animation-delay: 0.22s; }

.form .to-register {
  animation: fadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) backwards;
  animation-delay: 0.3s;
}

.primary-btn {
  animation: fadeIn 0.55s ease backwards;
  animation-delay: 0.3s;
}

.merchant-entry {
  animation: fadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) backwards;
  animation-delay: 0.46s;
}

.agreement {
  animation: fadeUp 0.55s cubic-bezier(0.16, 1, 0.3, 1) backwards;
  animation-delay: 0.52s;
}

@keyframes fadeUp {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

// 尊重减少动效偏好（无障碍）
@media (prefers-reduced-motion: reduce) {
  .form-card,
  .form-header,
  .login-tabs,
  .form .field,
  .form .to-register,
  .primary-btn,
  .merchant-entry,
  .agreement,
  .primary-btn::after {
    animation: none !important;
    transition: none !important;
  }
}
@include mobile {
  .login-page {
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
  .login-tabs {
    gap: 0.25rem;
    padding: 0.25rem;
  }
  .login-tab {
    padding: 0.625rem 0;
  }
  .agree-checkbox {
    width: 1rem;
    height: 1rem;
  }
  .primary-btn {
    padding: 1rem;
    font-size: 1rem;
  }
  .agreement {
    margin-top: 1rem;
  }
}

</style>
