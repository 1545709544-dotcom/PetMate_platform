<script setup lang="ts">
import { reactive, ref, onMounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { userApi } from '@/api/modules/user'
import { useUserStore } from '@/stores/user'
import { regionOptions, regionToCity } from '@/data/regions'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

// ============ 表单数据 ============
const form = reactive({
  nickname: '',
  gender: 0 as 0 | 1 | 2,           // 0未知 / 1男 / 2女 (后端约定)
  birthday: '',
  region: '',
  bio: '',
  // 本地展示用(只读,不入库)
  phone: '',
  verified: false,
  realName: '',
})

// 地区级联选择器的临时数组（省/市/区）
const regionArr = ref<string[]>([])

const avatarUrl = ref('')
const bioCount = ref(0)

// ============ 弹窗状态 ============
const realnameVisible = ref(false)
const pwdVisible = ref(false)
const deleteVisible = ref(false)

// 实名认证卡片（needVerify 跳转过来时滚动+高亮引导）
const certCardRef = ref<HTMLElement | null>(null)
const certHighlight = ref(false)

// 实名认证表单
const realnameForm = reactive({ realName: '', idCard: '' })

// 修改密码表单
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

// 注销账号表单
const deleteForm = reactive({ password: '' })

// ============ 统计动态数据（粉丝/宠物/获赞）============
const stats = reactive({
  petCount: 0,
  followerCount: 0,
  likeCount: 0,
})

// 数字缩写：>=1000 显示 "x.xk"
function formatCount(n: number): string {
  const v = Number(n) || 0
  if (v >= 1000) {
    const k = v / 1000
    return (k >= 10 ? Math.round(k) : k.toFixed(1).replace(/\.0$/, '')) + 'k'
  }
  return String(v)
}

// ============ 加载统计 ============
async function loadStats() {
  try {
    const s = await userApi.stats()
    stats.petCount = s.petCount ?? 0
    stats.followerCount = s.followerCount ?? 0
    stats.likeCount = s.likeCount ?? 0
  } catch (e: any) {
    console.error('[ProfileEdit] loadStats failed:', e)
  }
}
onMounted(loadStats)

// 由受限页面（发动态/发领养）带 needVerify=1 跳转过来时，
// 滚动到实名认证卡片并高亮引导（不再弹表单弹窗，避免与入口警告弹窗重复）
onMounted(() => {
  if (route.query.needVerify === '1') {
    // 清理 query，避免刷新后重复触发
    router.replace({ path: '/user/edit' }).catch(() => {})
    // 等 DOM 渲染后滚动
    nextTick(() => {
      certCardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      certHighlight.value = true
      setTimeout(() => (certHighlight.value = false), 3000)
    })
  }
})

function genderFromStr(g: string): 0 | 1 | 2 {
  return g === 'male' ? 1 : g === 'female' ? 2 : 0
}

// 姓名脱敏：张三 -> 张*, 张三丰 -> 张*丰
function maskName(name: string): string {
  if (!name) return ''
  if (name.length === 1) return name
  if (name.length === 2) return name[0] + '*'
  return name[0] + '*'.repeat(name.length - 2) + name[name.length - 1]
}

// ============ 加载当前用户资料 ============
async function loadMe() {
  try {
    const u: any = await userApi.me()
    form.nickname = u.nickname ?? ''
    form.gender = (Number(u.gender ?? 0) || 0) as 0 | 1 | 2
    form.birthday = u.birthday ?? ''
    form.bio = u.bio ?? ''
    avatarUrl.value = u.avatar ?? ''
    form.phone = u.phone ?? ''
    form.verified = !!u.verified
    form.realName = u.realName ?? ''
    bioCount.value = (form.bio || '').length
    // 地区：后端存 "省/市/区" 字符串，解析为级联数组
    if (u.region && typeof u.region === 'string' && u.region.includes('/')) {
      regionArr.value = u.region.split('/')
      form.region = u.region
    } else {
      regionArr.value = []
      form.region = ''
    }
  } catch (e: any) {
    console.error('[ProfileEdit] loadMe failed:', e)
    const status = e?.response?.status
    const code = e?.response?.data?.code
    ElMessage.error(`资料加载失败 (status=${status ?? '-'} code=${code ?? '-'})`)
  }
}
onMounted(loadMe)

// ============ 头像上传 ============
async function changeAvatar(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片需小于 5MB')
    input.value = ''
    return
  }
  if (!/image\/(jpeg|png|webp)/.test(file.type)) {
    ElMessage.warning('仅支持 jpg / png / webp 格式')
    input.value = ''
    return
  }
  try {
    const res: any = await userApi.uploadAvatar(file)
    avatarUrl.value = res.url
    ElMessage.success('头像已更新,点击保存生效')
  } catch (err: any) {
    console.error('[ProfileEdit] uploadAvatar failed:', err)
    const code = err?.response?.data?.code
    ElMessage.error(`头像上传失败 (code=${code ?? '-'})`)
  } finally {
    input.value = ''
  }
}

// ============ 性别切换 ============
function setGender(g: 'male' | 'female' | 'unknown') {
  form.gender = genderFromStr(g)
}

// ============ 地区级联选择 ============
function onRegionChange() {
  form.region = regionArr.value.join('/')
}

// ============ 实名认证 ============
function openRealname() {
  realnameForm.realName = form.realName || ''
  realnameForm.idCard = ''
  realnameVisible.value = true
}

async function submitRealname() {
  if (!realnameForm.realName.trim()) {
    ElMessage.warning('请输入真实姓名')
    return
  }
  if (!/^[1-9]\d{5}(18|19|20)\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{3}[\dXx]$/.test(realnameForm.idCard.trim())) {
    ElMessage.warning('请输入正确的 18 位身份证号')
    return
  }
  try {
    const updated: any = await userApi.realname(realnameForm.realName.trim(), realnameForm.idCard.trim())
    form.realName = updated.realName ?? realnameForm.realName.trim()
    // 同步刷新全局用户态（门禁依赖此状态），并以最新态为准
    await userStore.fetchUser()
    form.verified = !!(userStore.user as Record<string, any>)?.verified
    realnameVisible.value = false
    ElMessage.success('实名认证成功，已解锁更多功能 🎉')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '认证失败'
    ElMessage.error(msg)
  }
}

// ============ 修改密码 ============
function openSetPassword() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdVisible.value = true
}

async function submitPassword() {
  if (!pwdForm.oldPassword) {
    ElMessage.warning('请输入当前密码')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  try {
    await userApi.changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    pwdVisible.value = false
    ElMessage.success('密码修改成功')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '修改失败'
    ElMessage.error(msg)
  }
}

// ============ 注销账号 ============
function openDeleteAccount() {
  deleteForm.password = ''
  deleteVisible.value = true
}

async function submitDeleteAccount() {
  if (!deleteForm.password) {
    ElMessage.warning('请输入登录密码以确认注销')
    return
  }
  try {
    await ElMessageBox.confirm(
      '注销后账号将被永久停用，所有数据（宠物、动态、订单等）将无法恢复。确定要继续吗？',
      '危险操作确认',
      { confirmButtonText: '确认注销', cancelButtonText: '再想想', type: 'warning' },
    )
  } catch {
    return
  }
  try {
    await userApi.deleteAccount(deleteForm.password)
    ElMessage.success('账号已注销')
    await userStore.logout()
    deleteVisible.value = false
    router.replace('/login')
  } catch (e: any) {
    const msg = e?.response?.data?.message || '注销失败'
    ElMessage.error(msg)
  }
}

function back() {
  router.back()
}

async function save() {
  if (form.bio.length > 100) {
    ElMessage.warning('个人简介最多 100 字')
    return
  }
  if (!form.nickname.trim()) {
    ElMessage.warning('请输入用户昵称')
    return
  }
  try {
    const payload: Record<string, any> = {
      nickname: form.nickname.trim(),
      gender: form.gender,
      bio: form.bio,
    }
    if (form.birthday) payload.birthday = form.birthday
    if (form.region) {
      payload.region = form.region
      // 所在地区派生社区「附近」用的市区短名，一并写入 city，使社区自动生效
      const city = regionToCity(form.region)
      if (city) payload.city = city
    }
    if (avatarUrl.value) payload.avatar = avatarUrl.value
    const updated: any = await userApi.updateProfile(payload)
    form.nickname = updated.nickname ?? form.nickname
    form.gender = (Number(updated.gender ?? form.gender) || 0) as 0 | 1 | 2
    form.birthday = updated.birthday ?? form.birthday
    form.bio = updated.bio ?? form.bio
    if (updated.region) {
      form.region = updated.region
      regionArr.value = updated.region.includes('/') ? updated.region.split('/') : []
    }
    bioCount.value = (form.bio || '').length
    // 刷新全局用户态，确保社区等页面能立即拿到最新 city / region
    await userStore.fetchUser()
    ElMessage.success('保存成功')
    router.push('/user')
  } catch (e: any) {
    console.error('[ProfileEdit] save failed:', e)
    const status = e?.response?.status
    const code = e?.response?.data?.code
    ElMessage.error(`保存失败 (status=${status ?? '-'} code=${code ?? '-'})`)
  }
}

function cancel() {
  router.back()
}
</script>

<template>
  <div class="profile-edit">
    <main class="page-main">
      <div class="grid-layout">
        <!-- 左侧：用户卡片 -->
        <aside class="left-col">
          <div class="user-card pet-card">
            <div class="avatar-wrap">
              <img :src="avatarUrl" alt="头像" class="avatar" />
              <label class="avatar-edit">
                <span class="material-symbols-outlined">edit</span>
                <input
                  type="file"
                  accept="image/*"
                  class="avatar-input"
                  @change="changeAvatar"
                />
              </label>
            </div>
            <h2 class="user-name">{{ form.nickname }}</h2>
            <p class="user-tagline">PetMate 资深家长</p>

            <div class="stat-row">
              <div class="stat">
                <span class="stat-num">{{ formatCount(stats.petCount) }}</span>
                <span class="stat-label">养过的宠物</span>
              </div>
              <div class="stat">
                <span class="stat-num">{{ formatCount(stats.followerCount) }}</span>
                <span class="stat-label">粉丝</span>
              </div>
              <div class="stat">
                <span class="stat-num">{{ formatCount(stats.likeCount) }}</span>
                <span class="stat-label">获赞</span>
              </div>
            </div>
          </div>

          <!-- 实名认证提示卡 -->
          <div ref="certCardRef" class="cert-card" :class="{ 'cert-card--done': form.verified, 'cert-card--highlight': certHighlight }">
            <div class="cert-header">
              <span class="material-symbols-outlined cert-icon">verified_user</span>
              <span class="cert-title">实名认证</span>
              <span v-if="form.verified" class="cert-badge">已实名</span>
            </div>
            <template v-if="form.verified">
              <p class="cert-desc">已通过实名认证，解锁全部宠物服务功能。</p>
              <p class="cert-name">认证姓名：{{ maskName(form.realName) }}</p>
              <button class="cert-btn cert-btn--ghost" @click="openRealname">修改实名</button>
            </template>
            <template v-else>
              <p class="cert-desc">完成实名认证，解锁发布领养、社区发帖等更多功能。</p>
              <button class="cert-btn" @click="openRealname">去认证 ›</button>
            </template>
          </div>
        </aside>

        <!-- 右侧：表单分组 -->
        <section class="right-col">
          <!-- 基本信息 -->
          <div class="form-card pet-card">
            <h3 class="card-title">
              <span class="material-symbols-outlined title-icon">person</span>
              基本信息
            </h3>

            <div class="row">
              <div class="field">
                <label class="field-label">用户昵称</label>
                <input
                  v-model="form.nickname"
                  class="text-input"
                  type="text"
                  maxlength="20"
                  placeholder="请输入用户昵称"
                />
              </div>
              <div class="field">
                <label class="field-label">性别</label>
                <div class="seg-row">
                  <button
                    type="button"
                    class="seg-btn"
                    :class="{ active: form.gender === 1 }"
                    @click="setGender('male')"
                  >
                    <span class="material-symbols-outlined seg-icon">male</span>
                  </button>
                  <button
                    type="button"
                    class="seg-btn seg-btn-female"
                    :class="{ active: form.gender === 2 }"
                    @click="setGender('female')"
                  >
                    <span class="material-symbols-outlined seg-icon">female</span>
                  </button>
                </div>
              </div>
            </div>

            <div class="row">
              <div class="field">
                <label class="field-label">出生日期</label>
                <div class="date-input">
                  <el-date-picker
                    v-model="form.birthday"
                    type="date"
                    class="field-picker"
                    placeholder="选择出生日期"
                    value-format="YYYY-MM-DD"
                    format="YYYY/MM/DD"
                  />
                </div>
              </div>
              <div class="field">
                <label class="field-label">所在地区</label>
                <div class="date-input">
                  <el-cascader
                    v-model="regionArr"
                    :options="regionOptions"
                    class="field-picker"
                    placeholder="选择省 / 市 / 区"
                    @change="onRegionChange"
                  />
                </div>
              </div>
            </div>

            <div class="field">
              <label class="field-label">个性签名</label>
              <textarea
                v-model="form.bio"
                class="text-input textarea"
                rows="3"
                maxlength="100"
                placeholder="介绍一下你和你的宠物吧"
                @input="bioCount = form.bio.length"
              ></textarea>
              <span class="counter">{{ bioCount }}/100</span>
            </div>
          </div>

          <!-- 账号与安全 -->
          <div class="form-card pet-card">
            <h3 class="card-title">
              <span class="material-symbols-outlined title-icon">shield</span>
              账号与安全
            </h3>

            <div class="list-row">
              <div class="list-left">
                <span class="material-symbols-outlined list-icon list-icon-blue">phone_iphone</span>
                <div>
                  <p class="list-title">绑定手机</p>
                  <p class="list-desc">{{ form.phone || '未绑定' }}</p>
                </div>
              </div>
              <span class="list-desc" style="color:#999">不可修改</span>
            </div>

            <div class="list-divider"></div>

            <div class="list-row">
              <div class="list-left">
                <span class="material-symbols-outlined list-icon list-icon-blue">lock</span>
                <div>
                  <p class="list-title">登录密码</p>
                  <p class="list-desc">定期修改可保护账号安全</p>
                </div>
              </div>
              <button class="link-btn" @click="openSetPassword">设置</button>
            </div>

            <div class="list-divider"></div>

            <div class="list-row">
              <div class="list-left">
                <span class="material-symbols-outlined list-icon list-icon-blue">delete_forever</span>
                <div>
                  <p class="list-title">注销账号</p>
                  <p class="list-desc">注销后账号数据将无法恢复</p>
                </div>
              </div>
              <button class="link-btn" @click="openDeleteAccount">注销</button>
            </div>
          </div>

          <!-- 操作按钮 -->
          <div class="action-row">
            <button class="primary-btn" @click="save">保存修改</button>
            <button class="ghost-btn" @click="cancel">取消</button>
          </div>
        </section>
      </div>
    </main>

    <!-- 实名认证弹窗 -->
    <el-dialog v-model="realnameVisible" title="实名认证" width="92%" top="8vh" class="premium-dialog">
      <div class="dialog-body">
        <div class="dialog-hero">
          <span class="dialog-hero-icon dialog-hero-icon--brand">
            <span class="material-symbols-outlined">verified_user</span>
          </span>
          <p class="dialog-hero-sub">实名信息仅用于账号安全与功能解锁，请如实填写。</p>
        </div>
        <div class="dialog-fields">
          <div class="field">
            <label class="field-label">真实姓名</label>
            <input v-model="realnameForm.realName" class="text-input" type="text" placeholder="请输入真实姓名" />
          </div>
          <div class="field">
            <label class="field-label">身份证号</label>
            <input v-model="realnameForm.idCard" class="text-input" type="text" maxlength="18" placeholder="请输入 18 位身份证号" />
          </div>
        </div>
      </div>
      <template #footer>
        <button class="ghost-btn dialog-btn" @click="realnameVisible = false">取消</button>
        <button class="primary-btn dialog-btn" @click="submitRealname">
          <span class="material-symbols-outlined btn-icon">check_circle</span>
          提交认证
        </button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="设置登录密码" width="92%" top="8vh" class="premium-dialog">
      <div class="dialog-body">
        <div class="dialog-hero">
          <span class="dialog-hero-icon dialog-hero-icon--secondary">
            <span class="material-symbols-outlined">lock</span>
          </span>
          <p class="dialog-hero-sub">定期更换密码可有效保护你的账号安全。</p>
        </div>
        <div class="dialog-fields">
          <div class="field">
            <label class="field-label">当前密码</label>
            <input v-model="pwdForm.oldPassword" class="text-input" type="password" placeholder="请输入当前密码" />
          </div>
          <div class="field">
            <label class="field-label">新密码</label>
            <input v-model="pwdForm.newPassword" class="text-input" type="password" placeholder="至少 6 位" />
          </div>
          <div class="field">
            <label class="field-label">确认新密码</label>
            <input v-model="pwdForm.confirmPassword" class="text-input" type="password" placeholder="再次输入新密码" />
          </div>
        </div>
      </div>
      <template #footer>
        <button class="ghost-btn dialog-btn" @click="pwdVisible = false">取消</button>
        <button class="primary-btn dialog-btn" @click="submitPassword">
          <span class="material-symbols-outlined btn-icon">check_circle</span>
          确认修改
        </button>
      </template>
    </el-dialog>

    <!-- 注销账号弹窗 -->
    <el-dialog v-model="deleteVisible" title="注销账号" width="92%" top="8vh" class="premium-dialog">
      <div class="dialog-body">
        <div class="dialog-hero">
          <span class="dialog-hero-icon dialog-hero-icon--danger">
            <span class="material-symbols-outlined">warning</span>
          </span>
          <p class="dialog-hero-sub">此操作不可恢复，请确认后谨慎操作。</p>
        </div>
        <p class="dialog-warn">
          <span class="material-symbols-outlined">error_outline</span>
          将清除你的全部资料、宠物、动态与订单记录，且无法找回。
        </p>
        <div class="dialog-fields">
          <div class="field">
            <label class="field-label">登录密码</label>
            <input v-model="deleteForm.password" class="text-input" type="password" placeholder="请输入密码以确认注销" />
          </div>
        </div>
      </div>
      <template #footer>
        <button class="ghost-btn dialog-btn" @click="deleteVisible = false">取消</button>
        <button class="danger-btn dialog-btn" @click="submitDeleteAccount">
          <span class="material-symbols-outlined btn-icon">delete_forever</span>
          确认注销
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.profile-edit {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: $spacing-2xl;
}

// ====== 主区 ======
.page-main {
  max-width: $width-content-max;
  margin: 0 auto;
  // 与顶部导航栏保持间距
  padding: $spacing-xl $width-gutter-mobile;

  @include desktop {
    padding: $spacing-2xl $width-margin-desktop;
  }
}

.grid-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;

  @include desktop {
    grid-template-columns: 300px 1fr;
    gap: $spacing-xl;
  }
}

// ====== 左侧 ======
.left-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.user-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  padding: $spacing-xl $spacing-lg;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.avatar-wrap {
  position: relative;
  width: 96px;
  height: 96px;
  margin-bottom: $spacing-md;
}

.avatar {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid $color-surface-container-low;
}

.avatar-edit {
  position: absolute;
  right: -2px;
  bottom: -2px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: $color-brand;
  color: $color-on-brand;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  border: 3px solid $color-surface-container-lowest;
  box-shadow: $shadow-sm;

  .material-symbols-outlined {
    font-size: 16px;
  }
}

.avatar-input {
  display: none;
}

.user-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.user-tagline {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 0 0 $spacing-md;
}

.stat-row {
  display: flex;
  width: 100%;
  justify-content: space-around;
  border-top: 1px solid $color-outline-variant;
  padding-top: $spacing-md;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-num {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-brand;
}

.stat-label {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

// 认证卡
.cert-card {
  background: rgba($color-warning, 0.08);
  border: 1px solid rgba($color-warning, 0.3);
  border-radius: $radius-xl;
  padding: $spacing-md;
  transition: box-shadow 0.4s ease, border-color 0.4s ease;

  &.cert-card--done {
    background: rgba($color-secondary, 0.08);
    border-color: rgba($color-secondary, 0.3);
  }

  // needVerify 跳转过来时的高亮引导
  &.cert-card--highlight {
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.2), $shadow-md;
    animation: cert-pulse 0.6s ease 2;
  }
}

@keyframes cert-pulse {
  0%, 100% { box-shadow: 0 0 0 3px rgba($color-brand, 0.2), $shadow-md; }
  50% { box-shadow: 0 0 0 6px rgba($color-brand, 0.35), $shadow-md; }
}

.cert-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: $spacing-xs;
}

.cert-icon {
  font-size: 18px !important;
  color: $color-warning;
  font-variation-settings: 'FILL' 1;
}

.cert-card--done .cert-icon {
  color: $color-secondary;
}

.cert-title {
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
}

.cert-badge {
  margin-left: auto;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  color: $color-secondary;
  background: rgba($color-secondary, 0.14);
  padding: 2px 8px;
  border-radius: $radius-full;
}

.cert-desc {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  line-height: $line-height-normal;
  margin: 0 0 $spacing-sm;
}

.cert-name {
  font-size: $font-size-label-sm;
  color: $color-on-surface;
  font-weight: $font-weight-semibold;
  margin: 0 0 $spacing-sm;
}

.cert-btn {
  background: transparent;
  border: none;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  padding: 0;

  &:hover {
    text-decoration: underline;
  }

  &--ghost {
    color: $color-text-secondary;
  }
}

// ====== 右侧 ======
.right-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.form-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  padding: $spacing-lg;
  position: relative;
}

.card-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}

.title-icon {
  font-size: 24px !important;
  color: $color-brand;
}

// ====== Field ======
.row {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  margin-bottom: $spacing-md;

  @media (min-width: 640px) {
    grid-template-columns: 1fr 1fr;
  }
}

.field {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  position: relative;
}

.field-label {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
}

.text-input {
  width: 100%;
  padding: 12px 16px;
  background: $color-surface-container-low;
  border: 1px solid transparent;
  border-radius: $radius-default;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  font-family: inherit;
  outline: none;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &::placeholder {
    color: $color-outline;
  }

  &:focus {
    border-color: $color-secondary;
    box-shadow: 0 0 0 3px rgba($color-secondary, 0.15);
  }
}

.textarea {
  resize: vertical;
  min-height: 80px;
  line-height: $line-height-normal;
}

.counter {
  position: absolute;
  right: 8px;
  bottom: 8px;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  pointer-events: none;
}

.date-input {
  position: relative;
  width: 100%;
}

// 让 el-date-picker / el-cascader 融合自定义输入框样式
.field-picker {
  width: 100%;
  display: block;

  // 通过 CSS 变量直接改写 Element Plus 内部样式（更稳定，避免被 .el-cascader .el-input.is-focus 等高优先级覆盖）
  --el-input-bg-color: #{$color-surface-container-low};
  --el-input-border-color: transparent;
  --el-input-hover-border-color: transparent;
  --el-input-focus-border-color: transparent;
  --el-input-text-color: #{$color-on-surface};
  --el-text-color-placeholder: #{$color-outline};

  // 关键：强制组件根与内层输入框拉满列宽，与上方 / 昵称 / 性别 输入框对齐
  :deep(.el-input),
  :deep(.el-input__wrapper),
  :deep(.el-cascader) {
    width: 100%;
  }

  :deep(.el-input__wrapper),
  :deep(.el-cascader .el-input__wrapper) {
    background: var(--el-input-bg-color) !important;
    box-shadow: 0 0 0 1px var(--el-input-border-color) inset !important;
    border-radius: $radius-default;
    padding: 12px 16px;
  }

  :deep(.el-input__wrapper.is-focus),
  :deep(.el-cascader .el-input__wrapper.is-focus) {
    box-shadow: 0 0 0 3px rgba($color-secondary, 0.15) inset, 0 0 0 1px var(--el-input-border-color) inset !important;
  }

  :deep(.el-input__inner) {
    font-family: inherit;
    color: var(--el-input-text-color);
    font-size: $font-size-body-md;
  }

  // 恢复组件自带后缀图标（删除之前隐藏的覆写，避免日期/级联选择器丢失图标）
  :deep(.el-input__suffix),
  :deep(.el-cascader__suffix) {
    display: flex;
  }
}

// ====== 性别分段按钮 ======
.seg-row {
  display: flex;
  gap: $spacing-sm;
}

.seg-btn {
  flex: 1;
  height: 44px;
  background: $color-surface-container-low;
  border: 1.5px solid transparent;
  border-radius: $radius-default;
  color: $color-text-secondary;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all $transition-fast;

  .seg-icon {
    font-size: 22px;
    color: $color-secondary;
  }
}

.seg-btn-female .seg-icon {
  color: $color-danger;
}

.seg-btn:hover {
  border-color: $color-secondary;
}

.seg-btn.active {
  background: $color-surface-container-lowest;
  border-color: $color-secondary;
  box-shadow: 0 0 0 3px rgba($color-secondary, 0.1);
}

.seg-btn.active .seg-icon {
  color: $color-secondary;
  font-variation-settings: 'FILL' 1;
}

.seg-btn-female.active {
  border-color: $color-danger;

  .seg-icon {
    color: $color-danger;
  }
}

// ====== 列表行（账号与安全）======
.list-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm 0;
}

.list-left {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.list-icon {
  font-size: 22px !important;
  color: $color-secondary;
  width: 32px;
  height: 32px;
  background: $color-secondary-container;
  border-radius: $radius-default;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 4px;
}

.list-icon-blue {
  background: rgba($color-secondary, 0.12);
  color: $color-secondary;
}

.list-title {
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  margin: 0;
}

.list-desc {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 2px 0 0;
}

.list-divider {
  height: 1px;
  background: $color-outline-variant;
  opacity: 0.4;
  margin: 0;
}

.link-btn {
  background: transparent;
  border: none;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  padding: 0;

  &:hover {
    text-decoration: underline;
  }
}

// ====== 操作按钮 ======
.action-row {
  display: flex;
  gap: $spacing-md;
  margin-top: $spacing-sm;
}

.primary-btn {
  flex: 2;
  padding: 14px 0;
  background: linear-gradient(90deg, $color-brand 0%, #ff9461 100%);
  color: $color-on-brand;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba($color-brand, 0.3);
  transition: transform $transition-fast, box-shadow $transition-fast;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 24px rgba($color-brand, 0.35);
  }

  &:active {
    transform: translateY(0);
  }
}

.ghost-btn {
  flex: 1;
  padding: 14px 0;
  background: $color-surface-container-low;
  color: $color-text-secondary;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-semibold;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container;
  }
}

.danger-btn {
  flex: 1;
  padding: 14px 0;
  background: $color-emergency;
  color: #fff;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  &:hover {
    background: #d9363e;
    transform: translateY(-2px);
  }
}

// ====== 弹窗内样式 ======
.dialog-body {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

// hero 头部：圆形图标徽章 + 副标题
.dialog-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: $spacing-xs;
  margin-bottom: 0;
}

.dialog-hero-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  margin-bottom: $spacing-xs;
  box-shadow: 0 8px 20px rgba($color-brand, 0.25);

  .material-symbols-outlined {
    font-size: 28px;
  }

  &--brand {
    background: linear-gradient(135deg, $color-brand 0%, #ff9461 100%);
    color: $color-on-brand;
  }

  &--secondary {
    background: rgba($color-secondary, 0.12);
    color: $color-secondary;
    box-shadow: none;
  }

  &--danger {
    background: rgba($color-emergency, 0.12);
    color: $color-emergency;
    box-shadow: none;
  }
}

.dialog-hero-sub {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  line-height: $line-height-normal;
}

.dialog-fields {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

// 警示框（注销场景）
.dialog-warn {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 0 0 $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-default;
  background: rgba($color-emergency, 0.08);
  color: $color-danger;
  font-size: $font-size-label-md;
  line-height: $line-height-normal;
  border: 1px solid rgba($color-emergency, 0.2);

  .material-symbols-outlined {
    font-size: 20px;
    flex-shrink: 0;
    margin-top: 1px;
    color: $color-emergency;
  }
}

// 弹窗底部按钮：等宽对称、胶囊、图标+文字居中、精致过渡
.dialog-btn {
  flex: 1;
  min-height: 48px;
  padding: 0 20px;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  letter-spacing: 0.02em;
  border-radius: $radius-full;
  border: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  transition: transform $transition-fast, box-shadow $transition-fast,
    background $transition-fast, color $transition-fast, border-color $transition-fast;

  // 按钮内图标（可选）
  .btn-icon {
    font-size: 20px;
    line-height: 1;
  }

  &:active {
    transform: translateY(1px);
  }

  &:focus-visible {
    outline: 2px solid rgba($color-brand, 0.45);
    outline-offset: 2px;
  }
}

// 主按钮：品牌渐变 + 光泽阴影（覆盖基类 flex:2 与 90° 渐变）
.primary-btn.dialog-btn {
  flex: 1;
  background: linear-gradient(135deg, $color-brand 0%, #ff9a52 100%);
  color: $color-on-brand;
  box-shadow: 0 6px 16px rgba($color-brand, 0.28);

  &:hover {
    background: linear-gradient(135deg, #ff8a45 0%, #ffae6b 100%);
    box-shadow: 0 10px 22px rgba($color-brand, 0.36);
  }
}

// 次按钮：柔和描边幽灵风，不再像禁用态
.ghost-btn.dialog-btn {
  flex: 1;
  background: transparent;
  color: $color-text-secondary;
  border: 1px solid $color-outline-variant;

  &:hover {
    background: $color-surface-container-low;
    color: $color-on-surface;
    border-color: $color-secondary;
  }
}

// 危险按钮：emergency 渐变 + 光泽
.danger-btn.dialog-btn {
  flex: 1;
  background: linear-gradient(135deg, $color-emergency 0%, #ff6b6f 100%);
  color: #fff;
  box-shadow: 0 6px 16px rgba($color-emergency, 0.3);

  &:hover {
    background: linear-gradient(135deg, #ff5a5e 0%, #ff8084 100%);
    box-shadow: 0 10px 22px rgba($color-emergency, 0.38);
  }

  &:focus-visible {
    outline-color: rgba($color-emergency, 0.5);
  }
}

// 弹窗整体圆角与主题融合
.premium-dialog {
  border-radius: $radius-xl;
  overflow: hidden;

  :deep(.el-dialog__header) {
    margin: 0;
    padding: $spacing-lg $spacing-lg $spacing-sm;
    font-weight: $font-weight-bold;
  }

  :deep(.el-dialog__title) {
    font-family: $font-family-headline;
    font-weight: $font-weight-bold;
    color: $color-on-surface;
  }

  :deep(.el-dialog__body) {
    padding: $spacing-md $spacing-lg;
  }

  :deep(.el-dialog__footer) {
    padding: $spacing-md $spacing-lg $spacing-lg;
    display: flex;
    gap: $spacing-md;
    border-top: 1px solid rgba($color-outline-variant, 0.6);
  }
}
</style>
