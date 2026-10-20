<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi, type Store } from '@/api/modules/merchant'
import { userApi } from '@/api/modules/user'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const store = ref<Store | null>(null)

const form = reactive({
  name: '',
  type: 'comprehensive',
  address: '',
  logo: '',
  description: '',
})
const uploadingLogo = ref(false)

const stats = ref({ productCount: 0, serviceCount: 0, reviewCount: 0, appointmentCount: 0 })

async function load() {
  loading.value = true
  try {
    const s = await merchantApi.store()
    store.value = s
    form.name = s.name || ''
    form.type = s.type || 'comprehensive'
    form.address = s.address || ''
    form.logo = (s.logo as string) || ''
    form.description = (s.description as string) || ''
    stats.value = {
      productCount: Number(s.productCount ?? 0),
      serviceCount: Number(s.serviceCount ?? 0),
      reviewCount: Number(s.reviewCount ?? 0),
      appointmentCount: Number(s.appointmentCount ?? 0),
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '门店信息加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写门店名称')
    return
  }
  saving.value = true
  try {
    await merchantApi.updateStore({
      name: form.name,
      type: form.type,
      address: form.address,
      logo: form.logo,
      description: form.description,
    })
    ElMessage.success('门店信息已保存')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function goSchedule() {
  router.push('/merchant/schedule')
}
function goPhotos() {
  router.push('/merchant/store/photos')
}

// ===== 店铺 Logo 上传（复用商品上传接口）=====
function onLogoPick(e: Event) {
  const f = (e.target as HTMLInputElement).files?.[0]
  if (!f) return
  uploadingLogo.value = true
  merchantApi.uploadImages([f])
    .then((res) => {
      form.logo = res.urls?.[0] || ''
      ElMessage.success('Logo 已上传')
    })
    .catch((err: any) => ElMessage.error(err?.message || '上传失败'))
    .finally(() => (uploadingLogo.value = false))
}

// ===== 账号安全（从原「商家设置」迁入）=====
const passwordForm = ref({ oldPwd: '', newPwd: '', confirm: '' })
const showPwd = ref(false)
const changingPwd = ref(false)

async function changePassword() {
  if (!passwordForm.value.oldPwd || !passwordForm.value.newPwd) return ElMessage.warning('请填写完整')
  if (passwordForm.value.newPwd !== passwordForm.value.confirm) return ElMessage.warning('两次新密码不一致')
  if (passwordForm.value.newPwd.length < 6) return ElMessage.warning('新密码至少 6 位')
  try {
    await ElMessageBox.confirm('确认修改密码？修改后需用新密码重新登录', '修改密码', { type: 'warning' })
  } catch { return }
  changingPwd.value = true
  try {
    await userApi.changePassword(passwordForm.value.oldPwd, passwordForm.value.newPwd)
    ElMessage.success('密码修改成功')
    passwordForm.value = { oldPwd: '', newPwd: '', confirm: '' }
    showPwd.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '密码修改失败')
  } finally {
    changingPwd.value = false
  }
}

const auditLabel = (s?: number) => {
  if (s === 1) return { text: '已通过', cls: 'green' }
  if (s === 2) return { text: '已驳回', cls: 'red' }
  return { text: '待审核', cls: 'gold' }
}

onMounted(load)
</script>

<template>
  <div class="store-info-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">门店信息</h1>
        <p class="page-sub">维护门店资料、营业时间与联系方式，便于用户在客户端准确浏览。</p>
      </div>
      <button class="btn-primary" :disabled="saving" @click="save">
        <span class="material-symbols-outlined">save</span>
        {{ saving ? '保存中...' : '保存修改' }}
      </button>
    </div>

    <!-- 概览卡 -->
    <section v-if="store" class="overview-card">
      <div class="overview-main">
        <div class="badge">
          <span class="material-symbols-outlined">storefront</span>
        </div>
        <div>
          <h2 class="overview-name">{{ store.name }}</h2>
          <p class="overview-addr">{{ store.address || '—' }}</p>
          <span class="audit-pill" :class="auditLabel(store.auditStatus).cls">
            {{ auditLabel(store.auditStatus).text }}
          </span>
        </div>
      </div>
      <div class="overview-stats">
        <div class="stat-item">
          <span class="stat-val">{{ stats.productCount }}</span>
          <span class="stat-label">在售商品</span>
        </div>
        <div class="stat-item">
          <span class="stat-val">{{ stats.serviceCount }}</span>
          <span class="stat-label">上架服务</span>
        </div>
        <div class="stat-item">
          <span class="stat-val">{{ stats.reviewCount }}</span>
          <span class="stat-label">评价总数</span>
        </div>
        <div class="stat-item">
          <span class="stat-val">{{ stats.appointmentCount }}</span>
          <span class="stat-label">累计预约</span>
        </div>
      </div>
    </section>

    <!-- 表单 -->
    <section class="form-card">
      <h3 class="form-title">基础信息</h3>
      <div class="form-grid">
        <div class="field">
          <label>门店名称</label>
          <input v-model="form.name" placeholder="请输入门店名称" />
        </div>
        <div class="field">
          <label>门店类型</label>
          <select v-model="form.type">
            <option value="comprehensive">综合服务</option>
            <option value="grooming">美容洗护</option>
            <option value="medical">宠物医疗</option>
            <option value="boarding">寄养训练</option>
          </select>
        </div>
        <div class="field full">
          <label>门店地址</label>
          <input v-model="form.address" placeholder="街道 / 楼栋号" />
        </div>
        <div class="field">
          <label>店铺 Logo</label>
          <div class="logo-row">
            <img v-if="form.logo" :src="form.logo" class="logo-prev" alt="店铺Logo" />
            <span v-else class="logo-ph">未设置</span>
            <label class="btn-outline sm">
              <span class="material-symbols-outlined">upload</span>
              {{ uploadingLogo ? '上传中...' : '上传 Logo' }}
              <input type="file" accept="image/*" hidden :disabled="uploadingLogo" @change="onLogoPick" />
            </label>
          </div>
        </div>
        <div class="field full">
          <label>店铺简介</label>
          <textarea v-model="form.description" rows="3" placeholder="一句话介绍门店特色、服务理念..."></textarea>
        </div>
      </div>

      <div class="store-extra">
        <button type="button" class="extra-btn" @click="goSchedule">
          <span class="material-symbols-outlined">schedule</span>
          <span class="extra-text">
            <strong>营业时间设置</strong>
            <small>配置每周营业时段（排班）</small>
          </span>
          <span class="material-symbols-outlined chevron">chevron_right</span>
        </button>
        <button type="button" class="extra-btn" @click="goPhotos">
          <span class="material-symbols-outlined">photo_library</span>
          <span class="extra-text">
            <strong>实景相册管理</strong>
            <small>上传门店环境 / 服务实拍</small>
          </span>
          <span class="material-symbols-outlined chevron">chevron_right</span>
        </button>
      </div>
    </section>

    <!-- 账号安全（原「商家设置」迁入）-->
    <section class="form-card security-card">
      <div class="pwd-head">
        <h3 class="form-title">
          <span class="material-symbols-outlined pwd-icon">key</span>
          账号安全
        </h3>
        <button class="btn-outline" @click="showPwd = !showPwd">
          {{ showPwd ? '取消' : '修改密码' }}
        </button>
      </div>
      <div v-if="showPwd" class="pwd-form">
        <div class="field">
          <label>原密码</label>
          <input v-model="passwordForm.oldPwd" type="password" placeholder="请输入当前密码" />
        </div>
        <div class="field">
          <label>新密码</label>
          <input v-model="passwordForm.newPwd" type="password" placeholder="至少 6 位" />
        </div>
        <div class="field">
          <label>确认新密码</label>
          <input v-model="passwordForm.confirm" type="password" placeholder="再次输入新密码" />
        </div>
        <div class="pwd-actions">
          <button class="btn-primary" :disabled="changingPwd" @click="changePassword">
            {{ changingPwd ? '提交中...' : '提交修改' }}
          </button>
        </div>
      </div>
      <p v-else class="muted">密码为账号安全凭据，建议每 3 个月更换一次。</p>
    </section>
  </div>
</template>

<style lang="scss" scoped>
.store-info-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: $spacing-md;
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}
.page-sub {
  margin-top: 4px;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}
.btn-primary {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-md;
  font-weight: 700;
  cursor: pointer;
  &:disabled { opacity: 0.6; cursor: not-allowed; }
  .material-symbols-outlined { font-size: 18px; }
}

.overview-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-md;
  padding: $spacing-lg;
  background: linear-gradient(135deg, $color-brand-container, #fff5ee);
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.overview-main {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}
.badge {
  width: 56px;
  height: 56px;
  border-radius: $radius-md;
  background: $color-brand;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  .material-symbols-outlined { font-size: 28px; }
}
.overview-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}
.overview-addr {
  margin-top: 4px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}
.audit-pill {
  display: inline-block;
  margin-top: $spacing-sm;
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  &.green { background: rgba(82, 196, 26, 0.15); color: $color-success; }
  &.gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.red { background: rgba(255, 77, 79, 0.15); color: $color-emergency; }
}
.overview-stats {
  display: flex;
  gap: $spacing-xl;
}
.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.stat-val {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}
.stat-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 4px;
}

.form-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.form-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}
.form-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;
}
.field {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  &.full { grid-column: 1 / -1; }
  label {
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;
  }
  input, select, textarea {
    padding: $spacing-sm $spacing-md;
    border: 1px solid $color-outline-variant;
    border-radius: $radius-md;
    font-size: $font-size-body-md;
    font-family: inherit;
    background: #ffffff;
    color: $color-on-surface;
    outline: none;
    &:focus { border-color: $color-brand; }
  }
  textarea { resize: vertical; font-family: $font-family-mono; font-size: $font-size-label-sm; }
}

.store-extra {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  margin-top: $spacing-lg;
  @include desktop { grid-template-columns: 1fr 1fr; }
}
.extra-btn {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s, background 0.2s;
  .material-symbols-outlined:first-child {
    font-size: 28px;
    color: $color-brand;
    flex-shrink: 0;
  }
  &:hover { border-color: $color-brand; background: $color-surface-container; }
  .extra-text { flex: 1; display: flex; flex-direction: column; gap: 2px; }
  .extra-text strong { font-size: $font-size-body-md; color: $color-on-surface; }
  .extra-text small { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
  .chevron { color: $color-on-surface-variant; }
}

.logo-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}
.logo-prev {
  width: 56px;
  height: 56px;
  border-radius: $radius-md;
  object-fit: cover;
  border: 1px solid $color-outline-variant;
  background: $color-surface-container-low;
}
.logo-ph {
  width: 56px;
  height: 56px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  background: $color-surface-container-low;
  border: 1px dashed $color-outline-variant;
}
.btn-outline.sm {
  padding: $spacing-sm $spacing-md;
  font-size: $font-size-label-md;
  .material-symbols-outlined { font-size: 18px; }
}

// ===== 账号安全 =====
.security-card { margin-top: $spacing-md; }
.pwd-head { display: flex; justify-content: space-between; align-items: center; }
.form-title .pwd-icon {
  font-size: 22px;
  color: $color-secondary;
  margin-right: $spacing-xs;
  vertical-align: middle;
}
.btn-outline {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border: 1px solid $color-brand;
  border-radius: $radius-md;
  background: transparent;
  color: $color-brand;
  font-weight: 700;
  cursor: pointer;
  &:hover { background: rgba($color-brand, 0.08); }
}
.pwd-form {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  max-width: 420px;
  margin-top: $spacing-md;
}
.pwd-actions { display: flex; justify-content: flex-end; }
.muted {
  margin-top: $spacing-sm;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
}
</style>