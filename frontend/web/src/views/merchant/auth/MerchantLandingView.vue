<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { merchantApi } from '@/api/modules/merchant'
import heroImg from '@/assets/images/merchant-landing/hero.jpg'

const router = useRouter()
const userStore = useUserStore()

// 登录身份：merchant = 商家账号(手机号)；staff = 员工子账号(账号名)
const mode = ref<'merchant' | 'staff'>('merchant')
const phone = ref('')
const username = ref('')
const password = ref('')
const showPassword = ref(false)
const submitting = ref(false)

async function handleLogin() {
  const isStaff = mode.value === 'staff'
  const account = isStaff ? username.value.trim() : phone.value.trim()
  if (!account) {
    ElMessage.warning(isStaff ? '请输入员工账号' : '请输入商家手机号')
    return
  }
  if (!isStaff && !/^1[3-9]\d{9}$/.test(phone.value)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  if (!password.value) {
    ElMessage.warning('请输入密码')
    return
  }

  submitting.value = true
  try {
    const res = await userStore.login(
      isStaff
        ? { loginType: 'staff', username: account, password: password.value }
        : { loginType: 'password', phone: account, password: password.value }
    )
    if (res.role !== 'merchant') {
      ElMessage.error('该账号不是商家或员工账号')
      await userStore.logout().catch(() => {})
      return
    }

    // 登录后预热: 拉门店信息, 让工作台"店铺名"立刻可用
    try { await merchantApi.store() } catch {}
    ElMessage.success('欢迎回来！')
    router.push('/merchant/dashboard')
  } catch (err: any) {
    ElMessage.error(err?.message || '登录失败,请稍后重试')
  } finally {
    submitting.value = false
  }
}

function applyJoin() {
  router.push('/merchant/apply')
}
</script>

<template>
  <div class="merchant-landing">
    <!-- 左侧品牌视觉 -->
    <div class="brand-side">
      <div class="brand-bg-layer">
        <div
          class="brand-bg"
          :style="{ backgroundImage: `url(${heroImg})` }"
          aria-label="明亮现代的宠物精品店内景，木质货架与整洁陈列"
        ></div>
        <div class="brand-overlay"></div>
      </div>

      <div class="brand-content">
        <div class="brand-logo">
          <div class="logo-badge">
            <span class="material-symbols-outlined">pets</span>
          </div>
          <span class="logo-text">PetMate Pro</span>
        </div>
        <h1 class="brand-title">赋能全球顶尖宠物商家。</h1>
        <p class="brand-desc">
          加入数以千计的诊所、商店和服务商，利用我们的 AI 驱动专业生态系统管理业务。
        </p>
      </div>

      <div class="brand-glow"></div>
    </div>

    <!-- 右侧登录表单 -->
    <div class="form-side">
      <!-- 移动端 Logo -->
      <div class="mobile-logo">
        <span class="material-symbols-outlined">pets</span>
        <span class="logo-text">PetMate Pro</span>
      </div>

      <div class="form-wrap">
        <div class="form-head">
          <h2 class="form-title">商家中心</h2>
          <p class="form-subtitle">高效管理您的诊所、店铺或服务。</p>
        </div>

        <!-- 登录身份切换：商家 / 员工 共用此入口 -->
        <div class="login-tabs">
          <button
            type="button"
            class="login-tab"
            :class="{ active: mode === 'merchant' }"
            @click="mode = 'merchant'"
          >
            商家账号
          </button>
          <button
            type="button"
            class="login-tab"
            :class="{ active: mode === 'staff' }"
            @click="mode = 'staff'"
          >
            员工账号
          </button>
        </div>

        <form class="login-form" @submit.prevent="handleLogin">
          <!-- 手机号 / 员工账号 -->
          <div class="field">
            <label class="field-label">{{ mode === 'staff' ? '员工账号' : '商家手机号' }}</label>
            <div class="input-box">
              <span class="material-symbols-outlined field-icon">{{ mode === 'staff' ? 'badge' : 'smartphone' }}</span>
              <input v-if="mode === 'merchant'" v-model="phone" type="tel" class="field-input" placeholder="请输入商家手机号" />
              <input v-else v-model="username" type="text" class="field-input" placeholder="请输入员工账号" autocomplete="username" />
            </div>
          </div>

          <!-- 密码 -->
          <div class="field">
            <div class="field-label-row">
              <label class="field-label">密码</label>
            </div>
            <div class="input-box">
              <span class="material-symbols-outlined field-icon">lock</span>
              <input
                v-model="password"
                :type="showPassword ? 'text' : 'password'"
                class="field-input"
                placeholder="••••••••"
              />
              <button type="button" class="toggle-pw" @click="showPassword = !showPassword">
                <span class="material-symbols-outlined">{{ showPassword ? 'visibility_off' : 'visibility' }}</span>
              </button>
            </div>
          </div>

          <!-- 提交 -->
          <button type="submit" class="submit-btn" :disabled="submitting">
            <template v-if="submitting">
              <span class="material-symbols-outlined spin">progress_activity</span>
              正在验证...
            </template>
            <template v-else>
              登录商家中心
              <span class="material-symbols-outlined">arrow_forward</span>
            </template>
          </button>
        </form>

        <!-- 申请入驻 -->
        <div class="partnership">
          <p>还不是合作伙伴？</p>
          <button class="apply-btn" @click="applyJoin">
            <span class="material-symbols-outlined">handshake</span>
            申请入驻
          </button>
        </div>
      </div>

      <!-- Footer -->
      <div class="form-footer">
        <span>© 2026 PetMate Global Inc.</span>
        <div class="footer-links">
          <span class="footer-link">服务条款</span>
          <span class="footer-link">隐私政策</span>
          <span class="footer-link">帮助中心</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.merchant-landing {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: $color-surface-container-lowest;

  @include desktop {
    flex-direction: row;
  }
}

// ============ 左侧品牌墙 ============
.brand-side {
  display: none;
  position: relative;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: $color-brand-dark;

  @include desktop {
    display: flex;
    width: 58.333%; // 7/12
  }
}

.brand-bg-layer {
  position: absolute;
  inset: 0;
  z-index: 0;
}

.brand-bg {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  opacity: 0.4;
}

.brand-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(161, 64, 0, 0.8), rgba(161, 64, 0, 0.4));
  mix-blend-mode: multiply;
}

.brand-content {
  position: relative;
  z-index: 10;
  padding: 0 $spacing-3xl;
  color: #ffffff;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-xl;

  .logo-badge {
    width: 64px;
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: #ffffff;
    border-radius: $radius-xl;
    box-shadow: $shadow-lg;

    .material-symbols-outlined {
      font-size: 36px;
      color: $color-brand-dark;
      font-variation-settings: 'FILL' 1;
    }
  }

  .logo-text {
    font-family: $font-family-headline;
    font-size: $font-size-display-lg;
    font-weight: 800;
    letter-spacing: -0.03em;
  }
}

.brand-title {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: 800;
  line-height: 1.15;
  margin-bottom: $spacing-lg;
}

.brand-desc {
  max-width: 36rem;
  font-size: $font-size-body-lg;
  line-height: 1.6;
  color: $color-brand-container;
  opacity: 0.9;
}

.brand-stats {
  display: flex;
  gap: $spacing-2xl;
  margin-top: $spacing-2xl;

  .stat {
    display: flex;
    flex-direction: column;
  }

  .stat-value {
    font-size: 30px;
    font-weight: 700;
    line-height: 1.2;
  }

  .stat-label {
    font-size: $font-size-label-md;
    opacity: 0.75;
  }
}

.brand-glow {
  position: absolute;
  bottom: -80px;
  left: -80px;
  width: 320px;
  height: 320px;
  background: rgba(255, 122, 50, 0.2);
  border-radius: $radius-full;
  filter: blur(64px);
}

// ============ 右侧表单 ============
.form-side {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: $spacing-3xl $width-gutter-mobile;
  background: $color-surface-container-lowest;

  @include desktop {
    padding: $spacing-3xl 96px;
  }
}

.mobile-logo {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-2xl;
  color: $color-brand;

  .material-symbols-outlined {
    font-size: 30px;
    font-variation-settings: 'FILL' 1;
  }

  .logo-text {
    font-family: $font-family-headline;
    font-size: $font-size-headline-lg;
    font-weight: 700;
  }

  @include desktop {
    display: none;
  }
}

.form-wrap {
  width: 100%;
  max-width: 448px;
  margin: 0 auto;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.form-head {
  margin-bottom: $spacing-2xl;
}

.form-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;
}

.form-subtitle {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
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

.field-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.field-label {
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
}

.input-box {
  position: relative;
  display: flex;
  align-items: center;

  .field-icon {
    position: absolute;
    left: $spacing-md;
    font-size: 22px;
    color: $color-outline;
    pointer-events: none;
  }
}

.field-input {
  width: 100%;
  height: 52px;
  padding: 0 $spacing-md 0 48px;
  background: $color-surface-bright;
  border: 2px solid $color-surface-container-high;
  border-radius: $radius-md;
  font-family: $font-family-body;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  outline: none;
  transition: all $transition-fast;

  &::placeholder {
    color: rgba(140, 113, 102, 0.6);
  }

  &:focus {
    border-color: $color-secondary;
  }
}

.toggle-pw {
  position: absolute;
  right: $spacing-md;
  display: flex;
  align-items: center;
  color: $color-outline;
  cursor: pointer;

  &:hover {
    color: $color-on-surface;
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

// 橙色 3D 按压按钮（还原原型 shadow-[0_4px_0_0_#7b2f00]）
.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  width: 100%;
  padding: $spacing-md 0;
  background: $color-brand;
  color: $color-on-brand-container;
  border-radius: $radius-md;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 0 0 #7b2f00;
  transition: all 100ms ease;

  &:hover:not(:disabled) {
    box-shadow: 0 2px 0 0 #7b2f00;
    transform: translateY(2px);
  }

  &:active:not(:disabled) {
    transform: translateY(4px);
    box-shadow: none;
  }

  &:disabled {
    cursor: not-allowed;
  }

  .material-symbols-outlined {
    font-size: 22px;
  }

  .spin {
    animation: spin 0.8s linear infinite;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

// ============ 登录身份切换 ============
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

// ============ 申请入驻 ============
.partnership {
  margin-top: $spacing-2xl;
  padding-top: $spacing-xl;
  border-top: 1px solid $color-surface-variant;
  text-align: center;

  p {
    font-size: $font-size-body-md;
    color: $color-on-surface-variant;
    margin-bottom: $spacing-md;
  }
}

.apply-btn {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-xl;
  border: 2px solid $color-secondary;
  border-radius: $radius-full;
  color: $color-secondary;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: all $transition-base;

  &:hover {
    background: $color-secondary;
    color: $color-on-secondary;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

// ============ Footer ============
.form-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
  padding-top: $spacing-2xl;
  color: $color-outline;
  font-size: $font-size-label-sm;

  @include desktop {
    flex-direction: row;
    justify-content: center;
    gap: $spacing-2xl;
  }
}

.footer-links {
  display: flex;
  gap: $spacing-lg;

  a {
    color: $color-outline;
    transition: color $transition-fast;

    &:hover {
      color: $color-on-surface;
    }
  }
}
</style>
