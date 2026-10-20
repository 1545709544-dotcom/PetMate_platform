<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  serviceApi,
  type ServiceItem,
  type ServiceSpec,
  type ServiceSlot,
} from '@/api/modules/service'
import { appointmentApi } from '@/api/modules/appointment'
import { petApi, type Pet } from '@/api/modules/pet'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const serviceId = Number(route.params.id)

const loading = ref(false)
const submitting = ref(false)
const slotLoading = ref(false)

const service = ref<ServiceItem | null>(null)
const pets = ref<Pet[]>([])
const slots = ref<ServiceSlot[]>([])

// —— 表单状态 ——
const specId = ref<number | null>(null)
const addonIds = ref<number[]>([])
const petId = ref<number | null>(null)
const days = ref(1)
const serviceMode = ref<'instore' | 'onsite'>('instore')
const pickDate = ref('')
const pickTime = ref('')
const contactName = ref('')
const contactPhone = ref('')
const address = ref('')
const note = ref('')

// —— 计算 ——
const specs = computed(() => service.value?.specs || [])
const addons = computed(() => service.value?.addons || [])
const needSpec = computed(() => specs.value.length > 0)
const isDaily = computed(() => !!service.value?.dailyPriced)
const canOnsite = computed(() => service.value?.serviceMode === 'onsite')
const isOnsite = computed(() => canOnsite.value && serviceMode.value === 'onsite')

const selectedSpec = computed<ServiceSpec | null>(
  () => specs.value.find((s) => s.id === specId.value) || null,
)
const selectedAddons = computed(() => addons.value.filter((a) => addonIds.value.includes(a.id)))
const selectedPet = computed(() => pets.value.find((p) => p.id === petId.value) || null)

/** 按服务的 petType 过滤可选宠物，避免「猫送去狗专场」这类脏单 */
const availablePets = computed(() => {
  const t = service.value?.petType
  if (!t || t === 'all') return pets.value
  const kw = t === 'dog' ? '狗' : '猫'
  return pets.value.filter((p) => (p.categoryName || '').includes(kw))
})

/** 单价：有档位取档位价，无档位取服务起价 */
const unitPrice = computed(() => {
  if (selectedSpec.value) return Number(selectedSpec.value.price)
  return Number(service.value?.price || 0)
})
const baseAmount = computed(() => unitPrice.value * (isDaily.value ? days.value : 1))
const addonAmount = computed(() =>
  selectedAddons.value.reduce((sum, a) => sum + Number(a.price), 0),
)
const onsiteFee = computed(() => (isOnsite.value ? Number(service.value?.onsiteFee || 0) : 0))
const totalAmount = computed(() => baseAmount.value + addonAmount.value + onsiteFee.value)

/** 预计时长 = 档位时长 + 加购时长 */
const totalDuration = computed(() => {
  const base = selectedSpec.value?.duration ?? Number(service.value?.duration || 60)
  const extra = selectedAddons.value.reduce((s, a) => s + Number(a.duration || 0), 0)
  return base + extra
})

/** 可选日期范围：今天 → 今天 + advanceDays */
const dateOptions = computed(() => {
  const n = Math.max(1, Number(service.value?.advanceDays || 7))
  const out: Array<{ value: string; day: string; week: string; isToday: boolean }> = []
  const WEEK = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  for (let i = 0; i < n && i < 30; i++) {
    const d = new Date()
    d.setDate(d.getDate() + i)
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const dd = String(d.getDate()).padStart(2, '0')
    out.push({
      value: `${y}-${m}-${dd}`,
      day: `${m}/${dd}`,
      week: i === 0 ? '今天' : i === 1 ? '明天' : WEEK[d.getDay()],
      isToday: i === 0,
    })
  }
  return out
})

/** 完整下单时间 */
const appointmentTime = computed(() =>
  pickDate.value && pickTime.value ? `${pickDate.value} ${pickTime.value}:00` : '',
)

/** 步骤完成度，用于顶部进度指示 */
const steps = computed(() => [
  { key: 'spec', label: '选规格', done: !needSpec.value || !!specId.value },
  { key: 'pet', label: '选宠物', done: !!petId.value },
  { key: 'time', label: '选时段', done: !!appointmentTime.value },
  { key: 'contact', label: '填联系人', done: !!contactName.value && /^1\d{10}$/.test(contactPhone.value) },
])
const allDone = computed(() => steps.value.every((s) => s.done) && (!isOnsite.value || !!address.value.trim()))

async function loadService() {
  loading.value = true
  try {
    const s = await serviceApi.detail(serviceId)
    service.value = s
    serviceMode.value = s.serviceMode === 'onsite' ? 'onsite' : 'instore'
    // 默认选中最低档位；若从详情页带 specId 进来则优先命中
    const fromQuery = Number(route.query.specId)
    if (fromQuery && s.specs?.some((x) => x.id === fromQuery)) specId.value = fromQuery
    else if (s.specs?.length) specId.value = s.specs[0].id
    // 默认选今天
    pickDate.value = dateOptions.value[0]?.value || ''
  } catch (e) {
    ElMessage.error((e as Error)?.message || '服务信息加载失败')
  } finally {
    loading.value = false
  }
}

async function loadPets() {
  try {
    pets.value = (await petApi.list()) || []
    const fromQuery = Number(route.query.petId)
    if (fromQuery && pets.value.some((p) => p.id === fromQuery)) petId.value = fromQuery
    else if (availablePets.value.length === 1) petId.value = availablePets.value[0].id
  } catch {
    pets.value = []
  }
}

async function loadSlots() {
  if (!pickDate.value) return
  slotLoading.value = true
  pickTime.value = ''
  try {
    const r = await serviceApi.slots(serviceId, pickDate.value)
    slots.value = r?.slots || []
    // 自动选中第一个可约时段，减少一次点击
    const first = slots.value.find((s) => !s.disabled)
    if (first) pickTime.value = first.time
  } catch {
    slots.value = []
  } finally {
    slotLoading.value = false
  }
}

function prefillContact() {
  const u = userStore.user
  if (u) {
    contactName.value = contactName.value || u.nickname || ''
    contactPhone.value = contactPhone.value || u.phone || ''
  }
}

function toggleAddon(id: number) {
  const i = addonIds.value.indexOf(id)
  if (i >= 0) addonIds.value.splice(i, 1)
  else {
    if (addonIds.value.length >= 10) {
      ElMessage.warning('最多选择 10 个加购项')
      return
    }
    addonIds.value.push(id)
  }
}

function weightText(s: ServiceSpec) {
  const min = s.weightMin != null ? Number(s.weightMin) : null
  const max = s.weightMax != null ? Number(s.weightMax) : null
  if (min != null && max != null) return `${min}-${max}kg`
  if (max != null) return `${max}kg 以下`
  if (min != null) return `${min}kg 以上`
  return ''
}

function goAddPet() {
  router.push('/pet/create')
}

async function submit() {
  if (needSpec.value && !specId.value) return ElMessage.warning('请选择服务规格')
  if (!petId.value) return ElMessage.warning('请选择要服务的宠物')
  if (!appointmentTime.value) return ElMessage.warning('请选择预约时段')
  if (!contactName.value.trim()) return ElMessage.warning('请填写联系人姓名')
  if (!/^1\d{10}$/.test(contactPhone.value.trim())) return ElMessage.warning('请填写正确的手机号')
  if (isOnsite.value && !address.value.trim()) return ElMessage.warning('上门服务请填写详细地址')
  if (isDaily.value && (days.value < 1 || days.value > 90)) return ElMessage.warning('寄养天数需在 1-90 天之间')

  submitting.value = true
  try {
    const resp = await appointmentApi.create({
      serviceId,
      specId: specId.value || undefined,
      addonIds: addonIds.value.length ? addonIds.value : undefined,
      petId: petId.value,
      days: isDaily.value ? days.value : undefined,
      serviceMode: serviceMode.value,
      appointmentTime: appointmentTime.value,
      contactName: contactName.value.trim(),
      contactPhone: contactPhone.value.trim(),
      address: isOnsite.value ? address.value.trim() : undefined,
      note: note.value.trim() || undefined,
    })
    ElMessage.success(`预约成功！核销码 ${resp?.verifyCode || ''}，到店出示即可`)
    router.replace('/appointments')
  } catch (e) {
    ElMessage.error((e as Error)?.message || '预约提交失败')
  } finally {
    submitting.value = false
  }
}

function back() {
  router.back()
}

watch(pickDate, loadSlots)
watch(availablePets, (list) => {
  if (petId.value && !list.some((p) => p.id === petId.value)) petId.value = null
  if (!petId.value && list.length === 1) petId.value = list[0].id
})

onMounted(async () => {
  await loadService()
  prefillContact()
  loadPets()
  loadSlots()
})
</script>

<template>
  <div class="booking-view">
    <div class="booking-container">
      <button class="back-btn" @click="back">
        <span class="material-symbols-outlined">arrow_back</span> 返回
      </button>

      <h1 class="page-title">预约下单</h1>

      <div v-if="loading" class="state">加载服务信息中…</div>

      <template v-else-if="service">
        <!-- 步骤指示 -->
        <div class="steps">
          <div
            v-for="(s, i) in steps"
            :key="s.key"
            class="step"
            :class="{ done: s.done }"
          >
            <span class="step-dot">
              <span v-if="s.done" class="material-symbols-outlined">check</span>
              <template v-else>{{ i + 1 }}</template>
            </span>
            <span class="step-label">{{ s.label }}</span>
            <span v-if="i < steps.length - 1" class="step-line"></span>
          </div>
        </div>

        <div class="booking-layout">
          <!-- 左：表单 -->
          <div class="form-col">
            <!-- 服务摘要 -->
            <section class="service-card">
              <div class="svc-cover">
                <img v-if="service.cover" :src="service.cover" :alt="service.name" />
                <span v-else class="material-symbols-outlined">{{ service.icon || 'pets' }}</span>
              </div>
              <div class="service-info">
                <span class="tag">{{ service.subCategory || service.category }}</span>
                <h2 class="service-name">{{ service.name }}</h2>
                <p class="svc-store">
                  <span class="material-symbols-outlined">store</span>
                  {{ service.store?.name || service.storeName }}
                </p>
              </div>
            </section>

            <!-- 1. 选规格 -->
            <section v-if="needSpec" class="form-card">
              <h3 class="form-title">
                <span class="material-symbols-outlined">straighten</span> 选择规格档位
                <em class="req">必选</em>
              </h3>
              <p class="form-tip">按宠物体型 / 毛长分档，到店以实际称重为准，如有差异现场调整</p>
              <div class="spec-options">
                <button
                  v-for="sp in specs"
                  :key="sp.id"
                  class="spec-opt"
                  :class="{ active: specId === sp.id }"
                  @click="specId = sp.id"
                >
                  <span class="so-name">{{ sp.name }}</span>
                  <span v-if="weightText(sp)" class="so-weight">{{ weightText(sp) }}</span>
                  <span v-if="sp.coat && sp.coat !== '不限'" class="so-coat">{{ sp.coat }}</span>
                  <span class="so-price">¥{{ Number(sp.price) }}</span>
                  <span class="so-dur">{{ sp.duration }} 分钟</span>
                </button>
              </div>
            </section>

            <!-- 2. 选宠物 -->
            <section class="form-card">
              <h3 class="form-title">
                <span class="material-symbols-outlined">pets</span> 选择服务宠物
                <em class="req">必选</em>
              </h3>
              <div v-if="!availablePets.length" class="empty-pet">
                <span class="material-symbols-outlined">add_circle</span>
                <div>
                  <p>{{ pets.length ? '你的宠物档案不符合本服务的适用范围' : '还没有宠物档案' }}</p>
                  <button class="link-btn" @click="goAddPet">去添加宠物档案</button>
                </div>
              </div>
              <div v-else class="pet-options">
                <button
                  v-for="p in availablePets"
                  :key="p.id"
                  class="pet-opt"
                  :class="{ active: petId === p.id }"
                  @click="petId = p.id"
                >
                  <img v-if="p.avatar" :src="p.avatar" class="pet-avatar" :alt="p.name" />
                  <span v-else class="pet-avatar ph material-symbols-outlined">pets</span>
                  <span class="pet-meta">
                    <span class="pet-name">{{ p.name }}</span>
                    <span class="pet-sub">
                      {{ p.breedName || p.categoryName || '未知品种' }}
                      <template v-if="p.weight"> · {{ p.weight }}kg</template>
                    </span>
                  </span>
                  <span v-if="petId === p.id" class="material-symbols-outlined pet-check">check_circle</span>
                </button>
              </div>
            </section>

            <!-- 3. 服务方式（仅上门类可切换） -->
            <section v-if="canOnsite" class="form-card">
              <h3 class="form-title">
                <span class="material-symbols-outlined">directions_car</span> 服务方式
              </h3>
              <div class="mode-options">
                <button
                  class="mode-opt"
                  :class="{ active: serviceMode === 'onsite' }"
                  @click="serviceMode = 'onsite'"
                >
                  <span class="material-symbols-outlined">home</span>
                  <span class="mo-main">上门服务</span>
                  <span v-if="Number(service.onsiteFee) > 0" class="mo-fee">
                    +¥{{ Number(service.onsiteFee) }} 上门费
                  </span>
                </button>
                <button
                  class="mode-opt"
                  :class="{ active: serviceMode === 'instore' }"
                  @click="serviceMode = 'instore'"
                >
                  <span class="material-symbols-outlined">storefront</span>
                  <span class="mo-main">到店服务</span>
                  <span class="mo-fee">免上门费</span>
                </button>
              </div>
              <div v-if="isOnsite" class="field">
                <label class="field-label">上门地址 <em>*</em></label>
                <input
                  v-model="address"
                  class="field-input"
                  placeholder="请填写详细地址，如：杭州市西湖区文一西路 100 号 3 幢 502"
                  maxlength="120"
                />
              </div>
            </section>

            <!-- 4. 寄养天数 -->
            <section v-if="isDaily" class="form-card">
              <h3 class="form-title">
                <span class="material-symbols-outlined">date_range</span> 寄养天数
              </h3>
              <div class="days-picker">
                <button class="day-btn" :disabled="days <= 1" @click="days = Math.max(1, days - 1)">
                  <span class="material-symbols-outlined">remove</span>
                </button>
                <span class="day-val">{{ days }} 天</span>
                <button class="day-btn" :disabled="days >= 90" @click="days = Math.min(90, days + 1)">
                  <span class="material-symbols-outlined">add</span>
                </button>
                <span class="day-hint">¥{{ unitPrice }} × {{ days }} 天 = ¥{{ baseAmount }}</span>
              </div>
            </section>

            <!-- 5. 选日期与时段 -->
            <section class="form-card">
              <h3 class="form-title">
                <span class="material-symbols-outlined">event</span> 选择日期与时段
                <em class="req">必选</em>
              </h3>
              <p class="form-tip">
                营业时间 {{ service.openTime }} - {{ service.closeTime }}，
                每 {{ service.slotMinutes }} 分钟一档，显示剩余可约名额
              </p>

              <div class="date-strip">
                <button
                  v-for="d in dateOptions"
                  :key="d.value"
                  class="date-chip"
                  :class="{ active: pickDate === d.value }"
                  @click="pickDate = d.value"
                >
                  <span class="dc-week">{{ d.week }}</span>
                  <span class="dc-day">{{ d.day }}</span>
                </button>
              </div>

              <div v-if="slotLoading" class="slot-state">时段加载中…</div>
              <div v-else-if="!slots.length" class="slot-state">该日期暂无可约时段</div>
              <div v-else class="slot-grid">
                <button
                  v-for="s in slots"
                  :key="s.time"
                  class="slot"
                  :class="{ active: pickTime === s.time, disabled: s.disabled }"
                  :disabled="s.disabled"
                  @click="pickTime = s.time"
                >
                  <span class="slot-time">{{ s.time }}</span>
                  <span class="slot-remain">
                    {{ s.disabled ? (s.reason || '不可约') : `余 ${s.remain}` }}
                  </span>
                </button>
              </div>
            </section>

            <!-- 6. 加购项 -->
            <section v-if="addons.length" class="form-card">
              <h3 class="form-title">
                <span class="material-symbols-outlined">add_shopping_cart</span> 可选加购
                <em class="opt">选填</em>
              </h3>
              <div class="addon-options">
                <button
                  v-for="a in addons"
                  :key="a.id"
                  class="addon-opt"
                  :class="{ active: addonIds.includes(a.id) }"
                  @click="toggleAddon(a.id)"
                >
                  <span class="ao-check material-symbols-outlined">
                    {{ addonIds.includes(a.id) ? 'check_box' : 'check_box_outline_blank' }}
                  </span>
                  <span class="ao-body">
                    <span class="ao-name">{{ a.name }}</span>
                    <span v-if="a.description" class="ao-desc">{{ a.description }}</span>
                  </span>
                  <span class="ao-price">+¥{{ Number(a.price) }}</span>
                </button>
              </div>
            </section>

            <!-- 7. 联系人 -->
            <section class="form-card">
              <h3 class="form-title">
                <span class="material-symbols-outlined">contact_phone</span> 联系方式
                <em class="req">必填</em>
              </h3>
              <div class="field-row">
                <div class="field">
                  <label class="field-label">联系人 <em>*</em></label>
                  <input v-model="contactName" class="field-input" placeholder="您的称呼" maxlength="20" />
                </div>
                <div class="field">
                  <label class="field-label">手机号 <em>*</em></label>
                  <input
                    v-model="contactPhone"
                    class="field-input"
                    placeholder="用于门店联系您"
                    maxlength="11"
                  />
                </div>
              </div>
              <div class="field">
                <label class="field-label">备注</label>
                <textarea
                  v-model="note"
                  class="field-input textarea"
                  rows="3"
                  maxlength="200"
                  placeholder="宠物性格、健康状况、特殊要求等（选填）"
                ></textarea>
              </div>
            </section>

            <!-- 须知 -->
            <section v-if="service.notices?.length" class="form-card notice-card">
              <h3 class="form-title">
                <span class="material-symbols-outlined">info</span> 预约须知
              </h3>
              <ol class="notice-list">
                <li v-for="(n, i) in service.notices" :key="i">{{ n }}</li>
              </ol>
            </section>
          </div>

          <!-- 右：费用明细（吸顶） -->
          <aside class="summary-col">
            <div class="summary-card">
              <h3 class="summary-title">费用明细</h3>

              <div class="sum-row">
                <span class="sum-key">
                  {{ selectedSpec?.name || service.name }}
                  <em v-if="isDaily"> × {{ days }} 天</em>
                </span>
                <span class="sum-val">¥{{ baseAmount.toFixed(2) }}</span>
              </div>

              <template v-if="selectedAddons.length">
                <div v-for="a in selectedAddons" :key="a.id" class="sum-row addon">
                  <span class="sum-key">+ {{ a.name }}</span>
                  <span class="sum-val">¥{{ Number(a.price).toFixed(2) }}</span>
                </div>
              </template>

              <div v-if="onsiteFee > 0" class="sum-row">
                <span class="sum-key">上门服务费</span>
                <span class="sum-val">¥{{ onsiteFee.toFixed(2) }}</span>
              </div>

              <div class="sum-divider"></div>

              <div class="sum-row info">
                <span class="sum-key">预计时长</span>
                <span class="sum-val">{{ totalDuration }} 分钟</span>
              </div>
              <div v-if="selectedPet" class="sum-row info">
                <span class="sum-key">服务宠物</span>
                <span class="sum-val">{{ selectedPet.name }}</span>
              </div>
              <div v-if="appointmentTime" class="sum-row info">
                <span class="sum-key">预约时间</span>
                <span class="sum-val">{{ pickDate }} {{ pickTime }}</span>
              </div>

              <div class="sum-divider"></div>

              <div class="sum-total">
                <span class="st-key">应付金额</span>
                <span class="st-val">¥{{ totalAmount.toFixed(2) }}</span>
              </div>

              <div class="pay-note">
                <span class="material-symbols-outlined">storefront</span>
                <div>
                  <strong>到店支付</strong>
                  <p>预约不收取任何费用，服务完成后到店结算。提交后生成 6 位核销码。</p>
                </div>
              </div>

              <button
                class="submit-btn"
                :class="{ disabled: !allDone }"
                :disabled="submitting"
                @click="submit"
              >
                <span v-if="submitting" class="material-symbols-outlined spin">progress_activity</span>
                <span v-else class="material-symbols-outlined">event_available</span>
                {{ submitting ? '提交中…' : '提交预约' }}
              </button>

              <p v-if="!allDone" class="submit-hint">
                还需完成：{{ steps.filter((s) => !s.done).map((s) => s.label).join('、')
                }}{{ isOnsite && !address.trim() ? '、填地址' : '' }}
              </p>

              <p v-if="service.refundPolicy" class="refund-mini">
                {{ service.refundPolicy }}
              </p>
            </div>
          </aside>
        </div>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.booking-view {
  min-height: 100vh;
  background: $color-background;
}
.booking-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  &:hover { color: $color-brand; }
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin: $spacing-sm 0 $spacing-lg;
}
.state {
  padding: $spacing-2xl;
  text-align: center;
  color: $color-on-surface-variant;
}

// ============ 步骤 ============
.steps {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-lg;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-full;
  border: 1px solid rgba(223, 192, 179, 0.25);
  overflow-x: auto;
}
.step {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.step-dot {
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  background: $color-surface-container-low;
  color: $color-outline;
  font-size: 12px;
  font-weight: 700;
  transition: all $transition-fast;
  .material-symbols-outlined { font-size: 15px; }
}
.step-label {
  font-size: $font-size-label-sm;
  color: $color-outline;
  white-space: nowrap;
}
.step.done {
  .step-dot { background: $color-brand; color: $color-on-brand; }
  .step-label { color: $color-on-surface; font-weight: 700; }
}
.step-line {
  width: 28px;
  height: 2px;
  background: $color-outline-variant;
  margin: 0 $spacing-xs;
}

// ============ 布局 ============
.booking-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;
  @include desktop {
    grid-template-columns: 1fr 340px;
    align-items: start;
  }
}
.form-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  min-width: 0;
}

// ============ 服务摘要 ============
.service-card {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md;
  background: linear-gradient(135deg, rgba(255, 122, 50, 0.1), rgba(0, 96, 172, 0.06));
  border-radius: $radius-xl;
  border: 1px solid rgba(255, 122, 50, 0.2);
}
.svc-cover {
  width: 72px;
  height: 72px;
  border-radius: $radius-lg;
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-surface-container-low;
  img { width: 100%; height: 100%; object-fit: cover; }
  .material-symbols-outlined { font-size: 34px; color: $color-brand; }
}
.service-info { min-width: 0; }
.tag {
  display: inline-block;
  padding: 1px $spacing-sm;
  border-radius: $radius-sm;
  background: rgba($color-brand, 0.15);
  color: $color-brand;
  font-size: 11px;
  font-weight: 700;
}
.service-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin-top: 3px;
}
.svc-store {
  display: flex;
  align-items: center;
  gap: 3px;
  margin-top: 2px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 15px; }
}

// ============ 表单卡片 ============
.form-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  border: 1px solid rgba(223, 192, 179, 0.2);
}
.form-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;

  .material-symbols-outlined { font-size: 20px; color: $color-brand; }
  em {
    font-style: normal;
    padding: 1px 6px;
    border-radius: $radius-sm;
    font-size: 11px;
    font-weight: 700;
    &.req { background: rgba(244, 67, 54, 0.1); color: #e53935; }
    &.opt { background: $color-surface-container-low; color: $color-outline; }
  }
}
.form-tip {
  margin-top: 4px;
  font-size: $font-size-label-sm;
  color: $color-outline;
  line-height: 1.5;
}

// ============ 规格 ============
.spec-options {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: $spacing-sm;
  margin-top: $spacing-md;
}
.spec-opt {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: $spacing-md;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-lg;
  background: $color-surface-container-lowest;
  text-align: left;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover { border-color: rgba($color-brand, 0.5); transform: translateY(-2px); }
  &.active {
    border-color: $color-brand;
    background: rgba($color-brand, 0.06);
    box-shadow: 0 4px 14px rgba(255, 122, 50, 0.18);
  }
}
.so-name {
  font-weight: 700;
  font-size: $font-size-label-md;
  color: $color-on-surface;
}
.so-weight,
.so-coat {
  font-size: 11px;
  color: $color-outline;
}
.so-coat {
  align-self: flex-start;
  padding: 0 5px;
  border-radius: $radius-sm;
  background: $color-surface-container-low;
}
.so-price {
  margin-top: 4px;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-brand;
}
.so-dur { font-size: 11px; color: $color-outline; }

// ============ 宠物 ============
.pet-options {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: $spacing-sm;
  margin-top: $spacing-md;
}
.pet-opt {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-lg;
  background: $color-surface-container-lowest;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover { border-color: rgba($color-brand, 0.5); }
  &.active { border-color: $color-brand; background: rgba($color-brand, 0.06); }
}
.pet-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  object-fit: cover;
  flex-shrink: 0;
  &.ph {
    display: flex;
    align-items: center;
    justify-content: center;
    background: $color-surface-container-low;
    color: $color-brand;
    font-size: 22px;
  }
}
.pet-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  text-align: left;
}
.pet-name {
  font-weight: 700;
  font-size: $font-size-label-md;
  color: $color-on-surface;
}
.pet-sub {
  font-size: 11px;
  color: $color-outline;
  @include text-ellipsis(1);
}
.pet-check {
  color: $color-brand;
  font-size: 20px;
  font-variation-settings: 'FILL' 1;
}
.empty-pet {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-top: $spacing-md;
  padding: $spacing-lg;
  border: 1px dashed $color-outline-variant;
  border-radius: $radius-lg;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 32px; color: $color-brand; }
  p { font-size: $font-size-body-md; }
}
.link-btn {
  margin-top: 4px;
  color: $color-brand;
  font-weight: 700;
  font-size: $font-size-label-md;
  cursor: pointer;
  text-decoration: underline;
  background: none;
  border: none;
  padding: 0;
}

// ============ 服务方式 ============
.mode-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-sm;
  margin-top: $spacing-md;
}
.mode-opt {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: $spacing-md;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-lg;
  background: $color-surface-container-lowest;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined { font-size: 26px; color: $color-outline; }
  &:hover { border-color: rgba($color-brand, 0.5); }
  &.active {
    border-color: $color-brand;
    background: rgba($color-brand, 0.06);
    .material-symbols-outlined { color: $color-brand; }
  }
}
.mo-main { font-weight: 700; font-size: $font-size-label-md; color: $color-on-surface; }
.mo-fee { font-size: 11px; color: $color-outline; }

// ============ 天数 ============
.days-picker {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  margin-top: $spacing-md;
}
.day-btn {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  border: 1px solid $color-outline-variant;
  background: $color-surface-container-lowest;
  color: $color-on-surface;
  cursor: pointer;
  transition: all $transition-fast;
  .material-symbols-outlined { font-size: 18px; }
  &:hover:not(:disabled) { border-color: $color-brand; color: $color-brand; }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
}
.day-val {
  min-width: 60px;
  text-align: center;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}
.day-hint {
  font-size: $font-size-label-sm;
  color: $color-brand;
  font-weight: 600;
}

// ============ 日期 / 时段 ============
.date-strip {
  display: flex;
  gap: 6px;
  margin-top: $spacing-md;
  padding-bottom: 4px;
  overflow-x: auto;
}
.date-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  min-width: 62px;
  padding: $spacing-sm;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-lg;
  background: $color-surface-container-lowest;
  cursor: pointer;
  flex-shrink: 0;
  transition: all $transition-fast;

  &:hover { border-color: rgba($color-brand, 0.5); }
  &.active {
    border-color: $color-brand;
    background: $color-brand;
    .dc-week, .dc-day { color: $color-on-brand; }
  }
}
.dc-week { font-size: 11px; color: $color-outline; }
.dc-day { font-size: $font-size-label-md; font-weight: 700; color: $color-on-surface; }

.slot-state {
  padding: $spacing-lg;
  text-align: center;
  font-size: $font-size-label-md;
  color: $color-outline;
}
.slot-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(94px, 1fr));
  gap: $spacing-sm;
  margin-top: $spacing-md;
}
.slot {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1px;
  padding: $spacing-sm;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-md;
  background: $color-surface-container-lowest;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover:not(.disabled) { border-color: rgba($color-brand, 0.5); transform: translateY(-2px); }
  &.active {
    border-color: $color-brand;
    background: $color-brand;
    .slot-time, .slot-remain { color: $color-on-brand; }
  }
  &.disabled {
    opacity: 0.42;
    cursor: not-allowed;
    background: $color-surface-container-low;
  }
}
.slot-time { font-weight: 700; font-size: $font-size-label-md; color: $color-on-surface; }
.slot-remain { font-size: 11px; color: $color-outline; }

// ============ 加购 ============
.addon-options {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: $spacing-md;
}
.addon-opt {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-md;
  background: $color-surface-container-lowest;
  cursor: pointer;
  transition: all $transition-fast;
  text-align: left;

  &:hover { border-color: rgba($color-brand, 0.5); }
  &.active {
    border-color: $color-brand;
    background: rgba($color-brand, 0.06);
    .ao-check { color: $color-brand; }
  }
}
.ao-check {
  font-size: 20px;
  color: $color-outline;
  flex-shrink: 0;
}
.ao-body { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.ao-name { font-weight: 600; font-size: $font-size-label-md; color: $color-on-surface; }
.ao-desc { font-size: 11px; color: $color-outline; @include text-ellipsis(1); }
.ao-price {
  font-family: $font-family-headline;
  font-weight: 700;
  color: $color-brand;
  white-space: nowrap;
}

// ============ 输入 ============
.field-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  @include desktop { grid-template-columns: 1fr 1fr; }
}
.field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: $spacing-md;
}
.field-label {
  font-size: $font-size-label-sm;
  font-weight: 600;
  color: $color-on-surface-variant;
  em { color: #e53935; font-style: normal; }
}
.field-input {
  width: 100%;
  padding: $spacing-sm $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  background: $color-surface-container-lowest;
  font-size: $font-size-body-md;
  font-family: $font-family-body;
  color: $color-on-surface;
  outline: none;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &:focus {
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba(255, 122, 50, 0.12);
  }
  &.textarea { resize: vertical; line-height: 1.6; }
}

// ============ 须知 ============
.notice-card { background: rgba(255, 122, 50, 0.04); }
.notice-list {
  margin: $spacing-sm 0 0;
  padding-left: 1.2em;
  li {
    font-size: $font-size-label-md;
    color: $color-on-surface-variant;
    line-height: 1.8;
  }
}

// ============ 费用明细 ============
.summary-col {
  @include desktop {
    position: sticky;
    top: 88px;
  }
}
.summary-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-lg;
  border: 1px solid rgba(223, 192, 179, 0.25);
}
.summary-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}
.sum-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: $spacing-sm;
  padding: 4px 0;
  font-size: $font-size-label-md;

  .sum-key {
    color: $color-on-surface;
    em { font-style: normal; color: $color-outline; }
  }
  .sum-val { font-weight: 700; color: $color-on-surface; white-space: nowrap; }

  &.addon .sum-key { color: $color-on-surface-variant; padding-left: 6px; }
  &.info {
    .sum-key { color: $color-outline; font-size: $font-size-label-sm; }
    .sum-val { color: $color-on-surface-variant; font-weight: 600; font-size: $font-size-label-sm; }
  }
}
.sum-divider {
  height: 1px;
  background: $color-outline-variant;
  margin: $spacing-sm 0;
}
.sum-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-top: $spacing-xs;
}
.st-key {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}
.st-val {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: 800;
  color: $color-brand;
  line-height: 1;
}
.pay-note {
  display: flex;
  gap: 6px;
  margin-top: $spacing-md;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-md;
  background: rgba(76, 175, 80, 0.1);

  .material-symbols-outlined { font-size: 18px; color: #2e7d32; flex-shrink: 0; }
  strong { font-size: $font-size-label-md; color: #2e7d32; }
  p {
    margin-top: 2px;
    font-size: 11px;
    color: $color-on-surface-variant;
    line-height: 1.5;
  }
}
.submit-btn {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: $spacing-md;
  padding: $spacing-md;
  border-radius: $radius-full;
  background: $color-brand;
  color: $color-on-brand;
  font-weight: 700;
  font-size: $font-size-label-md;
  cursor: pointer;
  border: none;
  transition: all $transition-fast;

  .material-symbols-outlined { font-size: 19px; }
  &:hover:not(:disabled) { background: $color-brand-dark; transform: translateY(-1px); box-shadow: $shadow-md; }
  &:active:not(:disabled) { transform: scale(0.97); }
  &.disabled { opacity: 0.55; }
  &:disabled { cursor: not-allowed; }
}
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.submit-hint {
  margin-top: 6px;
  text-align: center;
  font-size: 11px;
  color: $color-outline;
}
.refund-mini {
  margin-top: $spacing-md;
  padding-top: $spacing-sm;
  border-top: 1px dashed $color-outline-variant;
  font-size: 11px;
  color: $color-outline;
  line-height: 1.6;
  white-space: pre-line;
}

// ============ 手机端响应式（≤768px）：用 rem 还原被 px-to-viewport 缩小的卡片内边距/间距 ============
@include mobile {
  .booking-container {
    padding-top: 1rem;
    padding-bottom: 1.5rem;
  }

  .booking-layout {
    gap: 1rem;
  }

  .form-col {
    gap: 1rem;
  }

  .form-card {
    padding: 1rem;
  }

  .service-card {
    padding: 0.75rem;
  }

  .summary-card {
    padding: 1rem;
  }
}
</style>
