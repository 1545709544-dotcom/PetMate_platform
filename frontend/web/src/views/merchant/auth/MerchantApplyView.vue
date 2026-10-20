<script setup lang="ts">
import { reactive, ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'
import { regionOptions, type RegionNode } from '@/data/regions'

const router = useRouter()
const submitting = ref(false)

const DAYS = [
  { key: 'mon', label: '周一' },
  { key: 'tue', label: '周二' },
  { key: 'wed', label: '周三' },
  { key: 'thu', label: '周四' },
  { key: 'fri', label: '周五' },
  { key: 'sat', label: '周六' },
  { key: 'sun', label: '周日' },
]

const form = reactive({
  phone: '',
  password: '',
  confirm: '',
  ownerName: '',
  idCard: '',
  enterpriseName: '',
  storeName: '',
  // 门店地址（省/市/区三级 + 详细地址），提交时合并为单一字符串
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  storePhone: '',
  // 资质
  licenseNo: '',            // 统一社会信用代码 / 营业执照号
  businessLicenseUrl: '',   // 营业执照照片（图片上传）
  // 营业时间（结构化 -> JSON）
  sameAll: true,
  masterRange: null as [string, string] | null,
  hours: {
    mon: null, tue: null, wed: null, thu: null, fri: null, sat: null, sun: null,
  } as Record<string, [string, string] | null>,
  remark: '',
  agreed: false,
})
// region select (after form)
const provinces = computed<RegionNode[]>(() => regionOptions)
const cities = computed<RegionNode[]>(() => {
  const p = provinces.value.find((x) => x.value === form.province)
  return p?.children ?? []
})
const districts = computed<RegionNode[]>(() => {
  const c = cities.value.find((x) => x.value === form.city)
  return c?.children ?? []
})
watch(() => form.province, () => { form.city = ''; form.district = ''})
watch(() => form.city, () => { form.district = '' })


// ============ 营业执照图片上传 ============
const licInput = ref<HTMLInputElement | null>(null)
const uploadingLic = ref(false)

async function onPickLicense(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    ElMessage.warning('仅支持 JPG / PNG / WebP / GIF 图片')
    input.value = ''
    return
  }
  uploadingLic.value = true
  try {
    const res = await merchantApi.uploadLicense(file)
    form.businessLicenseUrl = res.url
    ElMessage.success('营业执照已上传')
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败')
  } finally {
    uploadingLic.value = false
    input.value = ''
  }
}
function removeLicense() {
  form.businessLicenseUrl = ''
}

// ============ 营业时间 ============
function applyPreset(range: [string, string]) {
  form.masterRange = range
}
function toggleRest(day: string, checked: boolean) {
  form.hours[day] = checked ? null : ['09:00', '21:00']
}
function buildBusinessHours(): string {
  const map: Record<string, string> = {}
  if (form.sameAll) {
    const v = form.masterRange
    const val = v && v.length === 2 ? `${v[0]}-${v[1]}` : '休息'
    DAYS.forEach((d) => (map[d.key] = val))
  } else {
    DAYS.forEach((d) => {
      const v = form.hours[d.key]
      map[d.key] = v && v.length === 2 ? `${v[0]}-${v[1]}` : '休息'
    })
  }
  return JSON.stringify(map)
}

// ============ 校验 & 提交 ============
function validate(): string | null {
  if (!/^1[3-9]\d{9}$/.test(form.phone)) return '请输入正确的 11 位手机号'
  if (form.password.length < 6) return '密码至少 6 位'
  if (form.password !== form.confirm) return '两次输入的密码不一致'
  if (!form.ownerName.trim()) return '请填写店主 / 法人姓名'
  if (!/^(\d{15}|\d{17}[\dXx])$/.test(form.idCard.trim())) return '请输入正确的身份证号'
  if (!form.storeName.trim()) return '请填写门店名称'
  if (!form.enterpriseName.trim()) return '请填写企业名称'
  if (!form.licenseNo.trim()) return '请填写统一社会信用代码 / 营业执照号'
  if (!form.businessLicenseUrl) return '请上传营业执照照片'
  if (!form.agreed) return '请阅读并同意《商家入驻服务协议》'
  return null
}

async function submit() {
  const msg = validate()
  if (msg) { ElMessage.warning(msg); return }
  // 合并省/市/区/详细地址为单一字符串
  const fullAddress = [form.province, form.city, form.district, form.detailAddress.trim()]
    .filter(Boolean)
    .join(' ')
  submitting.value = true
  try {
    await merchantApi.applyMerchant({
      phone: form.phone,
      password: form.password,
      ownerName: form.ownerName,
      idCard: form.idCard.trim(),
      storeName: form.storeName,
      // 门店类型选择已从前端移除（无实际业务意义），固定默认「综合服务」以满足后端必填校验
      type: 'comprehensive',
      address: fullAddress,
      lng: undefined,
      lat: undefined,
      businessHours: buildBusinessHours(),
      licenseNo: form.licenseNo || undefined,
      unifiedCreditCode: form.licenseNo || undefined,
      enterpriseName: form.enterpriseName || undefined,
      storePhone: form.storePhone || undefined,
      businessLicenseUrl: form.businessLicenseUrl || undefined,
      remark: form.remark || undefined,
    })
    ElMessage.success('申请已提交,请等待审核')
    router.push({ path: '/merchant/apply/result', query: { phone: form.phone } })
  } catch (e: any) {
    ElMessage.error(e?.message || '申请提交失败,请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="apply-view">
    <div class="apply-container">
      <div class="head">
        <button class="back" @click="router.push('/merchant/landing')">
          <span class="material-symbols-outlined">arrow_back</span>
          返回登录
        </button>
        <h1 class="title">商家入驻申请</h1>
        <p class="sub">提交资料后,我们将在 1-3 个工作日内审核完成</p>
      </div>

      <form class="form" @submit.prevent="submit">
        <!-- 账号信息 -->
        <section class="card account-card">
          <div class="card-hero">
            <span class="hero-badge">
              <span class="material-symbols-outlined">badge</span>
            </span>
            <div class="hero-text">
              <h2 class="card-title">账号信息</h2>
              <p class="card-sub">用于登录与法人身份核验，请如实填写</p>
            </div>
          </div>
          <div class="grid">
            <div class="field">
              <label class="field-label">手机号 <span class="req">*</span></label>
              <input v-model="form.phone" class="input" type="tel" maxlength="11" placeholder="商家登录账号" />
            </div>
            <div class="field">
              <label class="field-label">店主 / 法人姓名 <span class="req">*</span></label>
              <input v-model="form.ownerName" class="input" maxlength="32" placeholder="真实姓名" />
            </div>
            <div class="field full">
              <label class="field-label">身份证号 <span class="req">*</span></label>
              <input v-model="form.idCard" class="input" type="text" maxlength="18" placeholder="15 或 18 位居民身份证号" />
            </div>
            <div class="field">
              <label class="field-label">密码 <span class="req">*</span></label>
              <input v-model="form.password" type="password" class="input" placeholder="审核通过后用于登录" />
            </div>
            <div class="field">
              <label class="field-label">确认密码 <span class="req">*</span></label>
              <input v-model="form.confirm" type="password" class="input" placeholder="再次输入密码" />
            </div>
          </div>
        </section>

        <!-- 门店信息 -->
        <section class="card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">storefront</span>
            门店信息
          </h2>
          <div class="grid">
            <div class="field">
              <label class="field-label">门店名称 <span class="req">*</span></label>
              <input v-model="form.storeName" class="input" maxlength="64" />
            </div>
            <div class="field">
              <label class="field-label">门店联系电话（可选）</label>
              <input v-model="form.storePhone" class="input" type="tel" maxlength="20" placeholder="便于客户与平台联系" />
            </div>

            <!-- 省 / 市 / 区 三级选择 -->
            <div class="field region-row">
              <div class="region-cell">
                <label class="field-label">省份</label>
                <el-select v-model="form.province" placeholder="请选择" clearable class="region-select">
                  <el-option
                    v-for="p in provinces"
                    :key="p.value"
                    :label="p.label"
                    :value="p.value" />
                </el-select>
              </div>
              <div class="region-cell">
                <label class="field-label">城市</label>
                <el-select v-model="form.city" placeholder="请选择" clearable :disabled="!form.province" class="region-select">
                  <el-option
                    v-for="c in cities"
                    :key="c.value"
                    :label="c.label"
                    :value="c.value" />
                </el-select>
              </div>
              <div class="region-cell">
                <label class="field-label">区县</label>
                <el-select v-model="form.district" placeholder="请选择" clearable :disabled="!form.city" class="region-select">
                  <el-option
                    v-for="d in districts"
                    :key="d.value"
                    :label="d.label"
                    :value="d.value" />
                </el-select>
              </div>
            </div>

            <!-- 详细地址（街道、门牌号、小区、楼栋、房间号等） -->
            <div class="field full">
              <h3 class="addr-title">
                <span class="material-symbols-outlined">home</span>
                详细地址
              </h3>
              <p class="addr-hint">街道、门牌号、小区、楼栋、房间号等</p>
              <textarea
                v-model="form.detailAddress"
                class="input textarea detail-textarea"
                maxlength="120"
                rows="3"
                placeholder="请输入详细地址，如：某某路某某号某某小区A栋101室" />
            </div>
          </div>
        </section>

        <!-- 资质信息 -->
        <section class="card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">verified</span>
            资质信息
          </h2>
          <div class="grid">
            <div class="field">
              <label class="field-label">企业名称 <span class="req">*</span></label>
              <input v-model="form.enterpriseName" class="input" maxlength="128" placeholder="营业执照上的企业全称" />
            </div>
            <div class="field">
              <label class="field-label">统一社会信用代码 / 营业执照号 <span class="req">*</span></label>
              <input v-model="form.licenseNo" class="input" maxlength="32" placeholder="如: 91110108MA01XXXXXX" />
            </div>

            <div class="field full">
              <label class="field-label">营业执照照片 <span class="req">*</span></label>
              <div class="lic-upload">
                <div v-if="!form.businessLicenseUrl" class="lic-box" :class="{ disabled: uploadingLic }"
                     @click="!uploadingLic && licInput?.click()">
                  <span class="material-symbols-outlined">{{ uploadingLic ? 'progress_activity' : 'add_a_photo' }}</span>
                  <span>{{ uploadingLic ? '上传中…' : '点击上传营业执照' }}</span>
                  <small>支持 JPG / PNG / WebP / GIF，单张图片</small>
                </div>
                <div v-else class="lic-preview">
                  <img :src="form.businessLicenseUrl" alt="营业执照"
                       @error="(e: any) => (e.target.style.display = 'none')" />
                  <button type="button" class="lic-del" @click="removeLicense">
                    <span class="material-symbols-outlined">refresh</span> 重新上传
                  </button>
                </div>
                <input ref="licInput" type="file" accept="image/jpeg,image/png,image/webp,image/gif" hidden
                       @change="onPickLicense" />
              </div>
            </div>

            <!-- 营业时间 -->
            <div class="field full">
              <label class="field-label">营业时间（可选）</label>
              <div class="hours-editor">
                <div class="hours-head">
                  <label class="same-all">
                    <input type="checkbox" v-model="form.sameAll" />
                    <span>每天营业时间相同</span>
                  </label>
                  <div v-if="form.sameAll" class="presets">
                    <button type="button" class="preset" @click="applyPreset(['09:00', '21:00'])">09:00–21:00</button>
                    <button type="button" class="preset" @click="applyPreset(['10:00', '22:00'])">10:00–22:00</button>
                    <button type="button" class="preset" @click="applyPreset(['09:00', '18:00'])">09:00–18:00</button>
                  </div>
                </div>

                <!-- 统一时间段 -->
                <div v-if="form.sameAll" class="hours-master">
                  <span class="hm-label">每日营业</span>
                  <el-time-picker
                    v-model="form.masterRange"
                    is-range
                    value-format="HH:mm"
                    format="HH:mm"
                    range-separator="至"
                    start-placeholder="开始时间"
                    end-placeholder="结束时间" />
                  <span class="hm-tip">（留空表示每日休息）</span>
                </div>

                <!-- 逐日设置 -->
                <div v-else class="hours-days">
                  <div v-for="d in DAYS" :key="d.key" class="hours-row">
                    <span class="hd-label">{{ d.label }}</span>
                    <el-time-picker
                      v-model="form.hours[d.key]"
                      is-range
                      value-format="HH:mm"
                      format="HH:mm"
                      range-separator="至"
                      start-placeholder="开始"
                      end-placeholder="结束"
                      :disabled="form.hours[d.key] === null" />
                    <label class="hd-rest">
                      <input type="checkbox"
                             :model-value="form.hours[d.key] === null"
                             @change="(e: any) => toggleRest(d.key, e.target.checked)" />
                      <span>休息</span>
                    </label>
                  </div>
                </div>
              </div>
            </div>

            <div class="field full">
              <label class="field-label">补充说明（可选）</label>
              <textarea v-model="form.remark" class="input textarea" maxlength="255" rows="3"
                        placeholder="其他需要告知运营团队的内容" />
            </div>
          </div>
        </section>

        <!-- 服务协议 -->
        <section class="card agreement-card">
          <h2 class="card-title">
            <span class="material-symbols-outlined">description</span>
            商家入驻服务协议
          </h2>
          <div class="agreement-box">
            <h4>PetMate 平台商家入驻服务协议</h4>
            <p><b>一、协议主体与接受。</b>本协议由您（以下简称"商家"）与 PetMate 平台运营方（以下简称"平台"）共同缔结。您点击"同意并提交"即视为已阅读、理解并自愿接受本协议全部条款。</p>
            <p><b>二、服务内容。</b>平台为商家提供店铺展示、商品 / 服务发布、订单交易、客户管理及营销工具等技术服务。平台保留对服务内容、功能及规则进行调整与解释的权利。</p>
            <p><b>三、入驻资质与信息真实性。</b>商家须确保提交的营业执照、经营场所、联系人及商品 / 服务信息真实、合法、有效，且具备相应的经营资质。因信息不实导致的法律责任由商家自行承担，平台有权随时下线违规内容并终止合作。</p>
            <p><b>四、经营规范。</b>商家应依法合规经营，不得发布违禁品、虚假宣传或侵犯第三方知识产权的内容；提供的宠物商品 / 服务应符合国家相关卫生、安全与检疫标准。</p>
            <p><b>五、费用与结算。</b>平台按公示的费率收取技术服务费，交易款项依据平台结算规则按期打款。具体费率、提现周期及退费政策以平台届时公示为准。</p>
            <p><b>六、数据与隐私。</b>商家在经营过程中收集的消费者个人信息，须严格遵守《个人信息保护法》等相关规定，不得超范围收集、滥用或对外泄露。</p>
            <p><b>七、知识产权。</b>平台提供的技术、界面及品牌素材归平台所有；商家上传的内容（含图片、文字）由商家保证拥有合法权利并授权平台在运营范围内使用。</p>
            <p><b>八、违规与违约责任。</b>商家违反本协议或平台规则的，平台可采取警示、限流、下架、冻结结算款乃至终止合作等措施；造成平台或第三方损失的，商家应予以赔偿。</p>
            <p><b>九、协议变更与终止。</b>平台可不时修订本协议，修订后以平台公告方式告知；商家继续使用服务即视为接受修订。任一方均可依约定终止合作，终止后已产生订单仍按原协议履行。</p>
            <p><b>十、法律适用。</b>本协议的订立、效力、解释及争议解决均适用中华人民共和国大陆地区法律；协商不成的，提交平台运营方所在地有管辖权的人民法院诉讼解决。</p>
            <p class="agr-foot">如对本协议有疑问，可在提交前联系平台运营团队（service@petmate.example）。</p>
          </div>
          <label class="agree-row">
            <input type="checkbox" v-model="form.agreed" />
            <span>我已阅读并同意《PetMate 平台商家入驻服务协议》</span>
          </label>
        </section>

        <div class="actions">
          <button type="button" class="btn-secondary" @click="router.push('/merchant/landing')">取消</button>
          <button type="submit" class="btn-primary" :disabled="submitting || !form.agreed">
            {{ submitting ? '提交中…' : '提交申请' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.apply-view {
  min-height: 100vh;
  background: $color-background;
  padding: $spacing-lg 0 $spacing-2xl;
}
.apply-container {
  @include container-max;
  max-width: 880px;
}
.head {
  text-align: center;
  margin-bottom: $spacing-xl;
}
.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: none;
  color: $color-secondary;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  margin-bottom: $spacing-md;

  .material-symbols-outlined { font-size: 18px; }
  &:hover { color: $color-brand; }
}
.title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;
}
.sub {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

.card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
}
.card-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
  .material-symbols-outlined { color: $color-secondary; }
}

/* 账号信息模块：重做后的 hero 头部 + 聚焦高亮 */
.account-card {
  border-color: rgba($color-brand, 0.35);
  box-shadow: $shadow-sm, 0 2px 14px rgba($color-brand, 0.06);

  .input:focus {
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba($color-brand, 0.14);
  }
}
.card-hero {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $color-outline-variant;
}
.hero-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  flex-shrink: 0;
  border-radius: $radius-full;
  background: linear-gradient(135deg, $color-brand, $color-brand-dark);
  color: #fff;
  box-shadow: 0 6px 16px rgba($color-brand, 0.32);

  .material-symbols-outlined { font-size: 26px; }
}
.hero-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.card-sub {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-md;
  @include desktop {
    grid-template-columns: 1fr 1fr 1fr;
    .full { grid-column: 1 / -1; }
  }
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  &.full { grid-column: 1 / -1; }
}
.field-label {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  font-weight: 600;
}
.req { color: $color-emergency; }

.input {
  padding: $spacing-sm $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  background: #fff;
  font-size: $font-size-body-md;
  font-family: inherit;
  color: $color-on-surface;
  outline: none;
  transition: border-color $transition-fast;
  &:focus { border-color: $color-secondary; }
}
.textarea {
  resize: vertical;
  min-height: 80px;
}

/* 省 / 市 / 区 三级联动选择 */
.region-row {
  display: flex;
  gap: $spacing-md;
  grid-column: 1 / -1;        // 跨整行（无视 grid 列数变化）
  flex-wrap: wrap;

  // 字段本身在 grid 里也是 .field，沿用 .field 的 display
  // 但用 row 模式盖掉 grid 内的纵向布局
  flex-direction: row;
  align-items: flex-end;
}
.region-cell {
  flex: 1 1 200px;             // 三个等宽；窄屏自动换行
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.region-select {
  width: 100%;

  // 样式上贴近原生 input
  :deep(.el-select__wrapper) {
    padding: $spacing-sm $spacing-md;
    min-height: 42px;
    border-radius: $radius-default;
    background: #fff;
    box-shadow: 0 0 0 1px $color-outline-variant inset;
  }
  :deep(.el-select__wrapper.is-focused) {
    box-shadow: 0 0 0 1px $color-secondary inset !important;
  }
  :deep(.el-select__placeholder) {
    color: $color-on-surface-variant;
  }
}

/* 详细地址段：与原 SCSS 设计系统一致的卡片小标题 */
.addr-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0 0 $spacing-xs;
  .material-symbols-outlined {
    color: $color-secondary;
    font-size: 22px;
  }
}
.addr-hint {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin: 0 0 $spacing-xs;
}
.detail-textarea {
  min-height: 96px;
  line-height: 1.6;
}

/* 营业执照上传 */
.lic-upload { width: 100%; }
.lic-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 150px;
  border: 1.5px dashed $color-outline-variant;
  border-radius: $radius-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  background: $color-surface-container-low;
  transition: border-color $transition-fast, color $transition-fast;

  .material-symbols-outlined { font-size: 34px; color: $color-secondary; }
  small { font-size: $font-size-label-sm; opacity: 0.8; }
  &:hover { border-color: $color-brand; color: $color-brand; }
  &.disabled { opacity: 0.6; cursor: not-allowed; }
}
.lic-preview {
  position: relative;
  display: inline-block;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  padding: 8px;
  background: #fff;

  img {
    display: block;
    max-width: 320px;
    max-height: 200px;
    border-radius: $radius-sm;
    object-fit: contain;
  }
  .lic-del {
    position: absolute;
    top: 12px;
    right: 12px;
    display: inline-flex;
    align-items: center;
    gap: 3px;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    border: none;
    border-radius: 999px;
    padding: 4px 10px;
    font-size: $font-size-label-sm;
    cursor: pointer;

    .material-symbols-outlined { font-size: 15px; }
    &:hover { background: $color-emergency; }
  }
}

/* 营业时间编辑器 */
.hours-editor {
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  padding: $spacing-md;
  background: $color-surface-container-low;
}
.hours-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-sm;
}
.same-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  cursor: pointer;
  input { width: 16px; height: 16px; accent-color: $color-brand; }
}
.presets { display: flex; gap: 6px; flex-wrap: wrap; }
.preset {
  border: 1px solid $color-outline-variant;
  background: #fff;
  border-radius: 999px;
  padding: 3px 10px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;
  &:hover { border-color: $color-brand; color: $color-brand; }
}
.hours-master {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-wrap: wrap;
  .hm-label { font-size: $font-size-body-md; font-weight: 600; }
  .hm-tip { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
}
.hours-days {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-sm;
}
.hours-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  .hd-label { width: 44px; font-size: $font-size-body-md; font-weight: 600; }
  .hd-rest {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;
    cursor: pointer;
    input { width: 15px; height: 15px; accent-color: $color-brand; }
  }
}

/* 服务协议 */
.agreement-card { border-color: $color-brand; }
.agreement-box {
  max-height: 260px;
  overflow-y: auto;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-low;
  font-size: $font-size-label-md;
  line-height: 1.7;
  color: $color-on-surface-variant;

  h4 { margin: 0 0 $spacing-sm; font-size: $font-size-body-lg; color: $color-on-surface; }
  p { margin: 0 0 $spacing-sm; }
  .agr-foot { opacity: 0.8; font-style: italic; }
  // 自定义滚动条
  &::-webkit-scrollbar { width: 8px; }
  &::-webkit-scrollbar-thumb { background: $color-outline-variant; border-radius: 4px; }
}
.agree-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: $spacing-md;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  cursor: pointer;
  input { width: 18px; height: 18px; accent-color: $color-brand; }
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: $spacing-sm;
  margin-top: $spacing-lg;
}
.btn-primary, .btn-secondary {
  padding: $spacing-sm $spacing-xl;
  border-radius: $radius-md;
  font-weight: 700;
  font-size: $font-size-body-md;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background $transition-fast;
}
.btn-primary {
  background: $color-brand;
  color: #fff;
  &:hover:not(:disabled) { background: $color-brand-dark; }
  &:disabled { opacity: 0.55; cursor: not-allowed; }
}
.btn-secondary {
  background: transparent;
  border-color: $color-outline-variant;
  color: $color-on-surface;
  &:hover { border-color: $color-brand; color: $color-brand; }
}
</style>
