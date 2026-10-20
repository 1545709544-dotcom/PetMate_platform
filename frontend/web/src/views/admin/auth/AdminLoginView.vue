<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore, normalizeRole } from '@/stores/user'
import heroImg from '@/assets/images/admin-login/hero.jpg'

const router = useRouter()
const userStore = useUserStore()

// 表单
const adminId = ref('')
const password = ref('')
const captchaInput = ref('')
const showPassword = ref(false)
const submitting = ref(false)

// 图形验证码（点击刷新，纯前端，不接后端）
const CAPTCHA_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
const captcha = ref('8K2m')
const captchaPulse = ref(false)

function refreshCaptcha() {
  let result = ''
  for (let i = 0; i < 4; i++) {
    result += CAPTCHA_CHARS.charAt(Math.floor(Math.random() * CAPTCHA_CHARS.length))
  }
  captcha.value = result
  captchaPulse.value = true
  setTimeout(() => (captchaPulse.value = false), 100)
}

// 当前访问信息（演示）—— 已移除硬编码 IP/节点,由 /admin/dashboard 真实数据提供

onMounted(() => {
  // 首次进入随机化验证码，避免固定值
  refreshCaptcha()
})

async function handleLogin() {
  if (!adminId.value) {
    ElMessage.warning('请输入管理员账号')
    return
  }
  if (!password.value) {
    ElMessage.warning('请输入密码')
    return
  }
  if (!captchaInput.value) {
    ElMessage.warning('请输入图形验证码')
    return
  }
  if (captchaInput.value.toLowerCase() !== captcha.value.toLowerCase()) {
    ElMessage.error('验证码错误')
    refreshCaptcha()
    captchaInput.value = ''
    return
  }

  submitting.value = true
  try {
    const res = await userStore.login({
      loginType: 'password',
      phone: adminId.value,
      password: password.value,
    })
    // 管理端入口以后端下发的真实角色为准。
    // 历史实现按 userId 阈值推断（>=90000 即 admin），普通用户 90002/90004 都能刷进后台。
    if (normalizeRole(res.role) !== 'admin') {
      ElMessage.error('该账号不是管理员账号')
      await userStore.logout().catch(() => {})
      refreshCaptcha()
      captchaInput.value = ''
      return
    }
    ElMessage.success('登录成功')
    router.push('/admin/dashboard')
  } catch (err: any) {
    ElMessage.error(err?.message || '登录失败,请稍后重试')
    refreshCaptcha()
    captchaInput.value = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="admin-login">
    <!-- 左侧：品牌视觉 -->
    <section class="brand-side">
      <div
        class="brand-bg"
        :style="{ backgroundImage: `url(${heroImg})` }"
        aria-label="现代化宠物医院前台，明亮整洁的高端诊疗环境"
      ></div>
      <div class="brand-overlay"></div>

      <div class="brand-content">
        <div class="brand-logo">
          <span class="material-symbols-outlined logo-icon">pets</span>
          <span class="logo-text">PetMate Admin</span>
        </div>

        <div class="brand-hero">
          <h1 class="brand-title">PetMate <br />平台管理中心</h1>
          <p class="brand-slogan">
            高效管理，智领未来。通过人工智能驱动的生态系统，为每一位毛孩子提供顶级的健康监护与服务支持。
          </p>
          <div class="brand-dots">
            <span class="dot active"></span>
            <span class="dot"></span>
            <span class="dot"></span>
          </div>
        </div>

        <div class="brand-copyright">© 2024 PetMate Ecosystem. All rights reserved.</div>
      </div>
    </section>

    <!-- 右侧：登录表单 -->
    <section class="form-side">
      <div class="form-wrap">
        <!-- 移动端 Logo -->
        <div class="mobile-logo">
          <span class="material-symbols-outlined logo-icon">pets</span>
          <span class="logo-text">PetMate Admin</span>
        </div>

        <div class="form-head">
          <h2 class="form-title">系统登录</h2>
          <p class="form-subtitle">欢迎回来，请输入您的管理员凭据以访问控制台。</p>
        </div>

        <!-- 安全警告 -->
        <div class="security-warning">
          <span class="material-symbols-outlined warn-icon">security</span>
          <p class="warn-text">
            仅限授权人员访问，所有操作将被记录。未经授权的尝试将触发布署的安全响应机制。
          </p>
        </div>

        <form class="login-form" @submit.prevent="handleLogin">
          <!-- 管理员账号 -->
          <div class="field">
            <label class="field-label">管理员账号</label>
            <div class="input-box">
              <span class="material-symbols-outlined field-icon">account_circle</span>
              <input v-model="adminId" type="text" class="field-input" placeholder="请输入管理员ID" />
            </div>
          </div>

          <!-- 密码 -->
          <div class="field">
            <label class="field-label">安全密码</label>
            <div class="input-box">
              <span class="material-symbols-outlined field-icon">lock</span>
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                class="field-input"
                placeholder="请输入您的密码"
              />
              <button type="button" class="toggle-pw" @click="showPassword = !showPassword">
                <span class="material-symbols-outlined">{{ showPassword ? 'visibility_off' : 'visibility' }}</span>
              </button>
            </div>
          </div>

          <!-- 图形验证码 -->
          <div class="field">
            <label class="field-label">图形验证码</label>
            <div class="captcha-row">
              <div class="input-box captcha-input">
                <span class="material-symbols-outlined field-icon">verified_user</span>
                <input v-model="captchaInput" type="text" class="field-input" placeholder="验证码" />
              </div>
              <div class="captcha-box" :class="{ pulse: captchaPulse }" @click="refreshCaptcha">
                <span class="captcha-text">{{ captcha }}</span>
              </div>
            </div>
          </div>

          <!-- 登录按钮 -->
          <button type="submit" class="submit-btn" :disabled="submitting">
            <span>{{ submitting ? '登录中…' : '登录系统' }}</span>
            <span class="material-symbols-outlined arrow">arrow_forward</span>
          </button>
        </form>

        <!-- 安全徽章 -->
        <div class="secure-footer">
          <div class="badges">
            <div class="badge">
              <span class="material-symbols-outlined">verified</span>
              <span class="badge-text">TLS 1.3 SECURE</span>
            </div>
            <div class="badge-divider"></div>
            <div class="badge">
              <span class="material-symbols-outlined">shield_locked</span>
              <span class="badge-text">ISO 27001</span>
            </div>
          </div>
          <div class="client-info">
            <p class="protect">由 PetMate AI 安全架构提供保护</p>
          </div>
        </div>
      </div>
    </section>
  </main>
</template>

<style lang="scss" scoped>
.admin-login {
  display: flex;
  min-height: 100vh;
  background: $color-surface;
  overflow: hidden;
}

// ============ 左侧品牌墙 ============
.brand-side {
  display: none;
  position: relative;
  width: 50%;
  overflow: hidden;
  background: $color-brand-dark;

  @include desktop {
    display: flex;
  }
}

.brand-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.brand-overlay {
  position: absolute;
  inset: 0;
  background: $color-brand-dark;
  opacity: 0.55;
  mix-blend-mode: multiply;
}

.brand-content {
  position: relative;
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 100%;
  padding: $spacing-3xl;
  color: #ffffff;
}

.brand-logo,
.mobile-logo {
  display: flex;
  align-items: center;
  gap: $spacing-sm;

  .logo-icon {
    font-size: 36px;
    font-variation-settings: 'FILL' 1;
  }

  .logo-text {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 800;
    letter-spacing: -0.01em;
  }
}

.brand-hero {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.brand-title {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: 800;
  line-height: 1.1;
  letter-spacing: -0.02em;
}

.brand-slogan {
  max-width: 28rem;
  font-size: $font-size-body-lg;
  line-height: 1.6;
  opacity: 0.9;
}

.brand-dots {
  display: flex;
  gap: $spacing-md;
  padding-top: $spacing-md;

  .dot {
    height: 4px;
    width: 16px;
    border-radius: $radius-full;
    background: rgba(255, 255, 255, 0.4);

    &.active {
      width: 48px;
      background: #ffffff;
    }
  }
}

.brand-copyright {
  font-size: $font-size-label-md;
  opacity: 0.7;
}

// ============ 右侧表单 ============
.form-side {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: $spacing-lg;
  background: $color-surface;

  @include desktop {
    width: 50%;
    padding: $spacing-3xl;
  }
}

.form-wrap {
  width: 100%;
  max-width: 480px;
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
}

.mobile-logo {
  color: $color-brand;
  margin-bottom: $spacing-md;

  @include desktop {
    display: none;
  }
}

.form-head {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.form-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.form-subtitle {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

.security-warning {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: rgba(255, 218, 214, 0.3);
  border: 1px solid rgba(186, 26, 26, 0.1);
  border-radius: $radius-md;

  .warn-icon {
    font-size: 20px;
    color: $color-danger;
    margin-top: 2px;
  }

  .warn-text {
    font-size: $font-size-label-sm;
    line-height: 1.6;
    color: #93000a;
  }
}

.login-form {
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
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-left: 4px;
}

.input-box {
  position: relative;
  display: flex;
  align-items: center;

  .field-icon {
    position: absolute;
    left: $spacing-md;
    font-size: 22px;
    color: $color-on-surface-variant;
    transition: color $transition-fast;
    pointer-events: none;
  }

  &:focus-within .field-icon {
    color: $color-secondary;
  }
}

.field-input {
  width: 100%;
  height: 52px;
  padding: 0 $spacing-md 0 48px;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  font-family: $font-family-body;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  outline: none;
  transition: all $transition-fast;

  &::placeholder {
    color: $color-text-tertiary;
  }

  &:focus {
    border-color: $color-secondary;
    box-shadow: 0 0 0 3px rgba(0, 96, 172, 0.15);
  }
}

.toggle-pw {
  position: absolute;
  right: $spacing-md;
  display: flex;
  align-items: center;
  color: $color-on-surface-variant;
  cursor: pointer;

  &:hover {
    color: $color-on-surface;
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.captcha-row {
  display: flex;
  gap: $spacing-md;
}

.captcha-input {
  flex: 1;
}

.captcha-box {
  width: 128px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-surface-container-high;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  cursor: pointer;
  user-select: none;
  overflow: hidden;
  transition: transform $transition-fast, background $transition-fast;

  &:hover {
    background: $color-surface-variant;
  }

  &.pulse {
    transform: scale(0.95);
  }

  .captcha-text {
    font-family: serif;
    font-size: 24px;
    font-style: italic;
    font-weight: 700;
    letter-spacing: 0.2em;
    color: $color-secondary;
    opacity: 0.8;
  }
}

.forgot {
  font-size: $font-size-label-md;
  color: $color-secondary;
  transition: color $transition-fast;

  &:hover {
    color: #003e73; // on-secondary-container（深蓝，原型 hover 色）
  }
}

.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  width: 100%;
  height: 56px;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-md;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  cursor: pointer;
  box-shadow: $shadow-card;
  transition: all $transition-fast;

  &:hover:not(:disabled) {
    background: #ff9461;
  }

  &:active:not(:disabled) {
    transform: scale(0.98);
    background: $color-brand-dark;
  }

  &:disabled {
    opacity: 0.7;
    cursor: not-allowed;
  }

  .arrow {
    font-size: 22px;
    transition: transform $transition-fast;
  }

  &:hover:not(:disabled) .arrow {
    transform: translateX(4px);
  }
}

// ============ 安全徽章 ============
.secure-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-lg;
  padding-top: $spacing-xl;
}

.badges {
  display: flex;
  align-items: center;
  gap: $spacing-xl;
}

.badge {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  opacity: 0.5;
  filter: grayscale(1);
  transition: all $transition-base;

  &:hover {
    opacity: 1;
    filter: grayscale(0);
  }

  .material-symbols-outlined {
    font-size: 24px;
    color: $color-on-surface;
  }

  .badge-text {
    font-size: $font-size-label-sm;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: $color-on-surface;
  }
}

.badge-divider {
  width: 1px;
  height: 16px;
  background: $color-outline-variant;
}

.client-info {
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 4px;

  p {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
  }

  .protect {
    color: $color-outline;
  }
}
</style>
