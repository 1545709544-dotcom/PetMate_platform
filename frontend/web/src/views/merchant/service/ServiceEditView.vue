<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'
import type {
  ServiceEditPayload,
  ServiceSpecInput,
  ServiceAddonInput,
  ServiceCatalogDict,
} from '@/api/modules/merchant'

const route = useRoute()
const router = useRouter()
const id = computed(() => {
  const v = route.query.id
  return v ? Number(v) : null
})
const isEdit = computed(() => id.value != null)

const catalog = ref<ServiceCatalogDict | null>(null)
const loading = ref(false)
const submitting = ref(false)
const uploadingCover = ref(false)

const form = reactive({
  name: '',
  category: '' as string,
  subCategory: '' as string,
  serviceMode: 'instore' as 'instore' | 'onsite',
  petType: 'all' as 'all' | 'dog' | 'cat',
  price: '',
  originalPrice: '',
  onsiteFee: '',
  duration: 60,
  cover: '',
  description: '',
  status: 1 as 0 | 1,
  tags: '',
  includeItems: '',
  notice: '',
  refundPolicy: '',
  openTime: '09:00',
  closeTime: '20:00',
  slotMinutes: 60,
  slotCapacity: 2,
  advanceDays: 7,
})

const specs = ref<ServiceSpecInput[]>([])
const addons = ref<ServiceAddonInput[]>([])

const currentCategory = computed(
  () => catalog.value?.categories.find((c) => c.name === form.category) || null,
)
const subOptions = computed(() => currentCategory.value?.subs || [])
const isDaily = computed(() => !!currentCategory.value?.dailyPriced)
const coats = computed(() => catalog.value?.coats || ['不限', '短毛', '长毛'])
const petKinds = computed(() => catalog.value?.petKinds || ['all', 'dog', 'cat'])

async function loadCatalog() {
  try {
    catalog.value = await merchantApi.serviceCatalog()
    // 默认选中第一个一级分类，避免空选项
    if (!form.category && catalog.value.categories.length) {
      form.category = catalog.value.categories[0].name
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '分类字典加载失败')
  }
}

async function load() {
  if (!id.value) return
  loading.value = true
  try {
    const s = await merchantApi.getService(id.value)
    form.name = s.name || ''
    form.category = s.category || (catalog.value?.categories[0]?.name || '')
    form.subCategory = s.subCategory || ''
    form.serviceMode = s.serviceMode === 'onsite' ? 'onsite' : 'instore'
    form.petType = ['all', 'dog', 'cat'].includes(s.petType) ? (s.petType as any) : 'all'
    form.price = s.price != null ? String(s.price) : ''
    form.originalPrice = s.originalPrice != null ? String(s.originalPrice) : ''
    form.onsiteFee = s.onsiteFee != null ? String(s.onsiteFee) : ''
    form.duration = Number(s.duration ?? 60)
    form.cover = s.cover || ''
    form.description = s.description || ''
    form.status = Number(s.status) === 1 ? 1 : 0
    form.tags = s.tags || ''
    form.includeItems = typeof s.includeItems === 'string'
      ? s.includeItems
      : Array.isArray(s.includeItems)
        ? (s.includeItems as string[]).join('\n')
        : ''
    form.notice = typeof s.notice === 'string'
      ? s.notice
      : Array.isArray(s.notice)
        ? (s.notice as string[]).join('\n')
        : ''
    form.refundPolicy = s.refundPolicy || ''
    form.openTime = s.openTime || '09:00'
    form.closeTime = s.closeTime || '20:00'
    form.slotMinutes = Number(s.slotMinutes ?? 60)
    form.slotCapacity = Number(s.slotCapacity ?? 2)
    form.advanceDays = Number(s.advanceDays ?? 7)
    specs.value = (s.specs || []).map((x: any) => ({
      id: x.id,
      name: x.name || '',
      petKind: ['all', 'dog', 'cat'].includes(x.petKind) ? (x.petKind as any) : 'all',
      coat: x.coat || '不限',
      weightMin: x.weightMin != null ? Number(x.weightMin) : null,
      weightMax: x.weightMax != null ? Number(x.weightMax) : null,
      price: Number(x.price ?? 0),
      duration: Number(x.duration ?? 60),
    }))
    addons.value = (s.addons || []).map((x: any) => ({
      id: x.id,
      name: x.name || '',
      price: Number(x.price ?? 0),
      duration: Number(x.duration ?? 0),
      description: x.description || '',
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载服务失败')
  } finally {
    loading.value = false
  }
}

function onCategoryChange() {
  // 切换一级分类后，若当前子类不属于新分类则清空
  if (form.subCategory && !subOptions.value.includes(form.subCategory)) form.subCategory = ''
}

function addSpec() {
  specs.value.push({
    name: '',
    petKind: 'all',
    coat: '不限',
    weightMin: null,
    weightMax: null,
    price: 0,
    duration: 60,
  })
}
function removeSpec(i: number) {
  specs.value.splice(i, 1)
}

function addAddon() {
  addons.value.push({ name: '', price: 0, duration: 0, description: '' })
}
function removeAddon(i: number) {
  addons.value.splice(i, 1)
}

function buildPayload(): ServiceEditPayload {
  return {
    name: form.name.trim(),
    category: form.category,
    subCategory: form.subCategory || '',
    serviceMode: form.serviceMode,
    petType: form.petType,
    price: Number(form.price || 0),
    duration: Number(form.duration || 60),
    cover: form.cover || undefined,
    description: form.description.trim() || undefined,
    status: form.status,
    tags: form.tags.trim() || undefined,
    includeItems: form.includeItems.trim() || undefined,
    notice: form.notice.trim() || undefined,
    refundPolicy: form.refundPolicy.trim() || undefined,
    openTime: form.openTime,
    closeTime: form.closeTime,
    slotMinutes: Number(form.slotMinutes || 60),
    slotCapacity: Number(form.slotCapacity || 2),
    advanceDays: Number(form.advanceDays || 7),
    specs: specs.value
      .filter((s) => s.name.trim())
      .map((s) => ({
        ...(s.id ? { id: s.id } : {}),
        name: s.name.trim(),
        petKind: s.petKind,
        coat: s.coat,
        weightMin: s.weightMin ? Number(s.weightMin) : null,
        weightMax: s.weightMax ? Number(s.weightMax) : null,
        price: Number(s.price),
        duration: Number(s.duration),
      })),
    addons: addons.value
      .filter((a) => a.name.trim())
      .map((a) => ({
        ...(a.id ? { id: a.id } : {}),
        name: a.name.trim(),
        price: Number(a.price),
        duration: Number(a.duration),
        description: a.description?.trim() || undefined,
      })),
    ...(form.originalPrice ? { originalPrice: Number(form.originalPrice) } : {}),
    ...(form.serviceMode === 'onsite' ? { onsiteFee: Number(form.onsiteFee || 0) } : {}),
  }
}

async function save() {
  if (!form.name.trim()) return ElMessage.warning('请填写服务名称')
  if (!form.category) return ElMessage.warning('请选择服务分类')
  if (specs.value.some((s) => !s.name.trim())) return ElMessage.warning('规格档位名称不能为空')
  if (specs.value.some((s) => Number(s.price) <= 0)) return ElMessage.warning('规格档位价格需大于 0')
  if (addons.value.some((a) => !a.name.trim())) return ElMessage.warning('加购项名称不能为空')
  if (specs.value.length === 0 && (!form.price || Number(form.price) <= 0))
    return ElMessage.warning('请填写价格，或添加至少一个规格档位')
  if (form.serviceMode === 'onsite' && Number(form.onsiteFee) < 0)
    return ElMessage.warning('上门费不能为负数')

  submitting.value = true
  try {
    const payload = buildPayload()
    if (isEdit.value && id.value) {
      await merchantApi.updateService(id.value, payload)
      ElMessage.success('已更新服务')
    } else {
      await merchantApi.createService(payload)
      ElMessage.success('已创建服务')
    }
    router.push('/merchant/service/list')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function onCoverPick(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingCover.value = true
  try {
    const { urls } = await merchantApi.uploadImages([file])
    if (urls.length) {
      form.cover = urls[0]
      ElMessage.success('封面已上传')
    }
  } catch (err: any) {
    ElMessage.error(err.message || '上传失败')
  } finally {
    uploadingCover.value = false
    input.value = ''
  }
}

async function onDelete() {
  if (!isEdit.value || !id.value) return
  try {
    await ElMessageBox.confirm('确认删除该服务？删除后客户将无法预约（进行中的预约会阻止删除）。', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await merchantApi.deleteService(id.value)
    ElMessage.success('已删除')
    router.push('/merchant/service/list')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(async () => {
  await loadCatalog()
  await load()
})
</script>

<template>
  <div class="service-edit-view" v-loading="loading">
    <div class="page-head">
      <div>
        <h1 class="page-title">{{ isEdit ? '编辑服务' : '新增服务' }}</h1>
        <p class="page-sub">配置规格分档、加购项、营业时段与预约须知，符合行业标准服务逻辑</p>
      </div>
      <div class="head-actions">
        <button class="btn-outline" @click="router.back()">
          <span class="material-symbols-outlined">arrow_back</span> 返回
        </button>
        <button v-if="isEdit" class="btn-danger" @click="onDelete">
          <span class="material-symbols-outlined">delete</span> 删除
        </button>
        <button class="btn-primary" :disabled="submitting" @click="save">
          <span class="material-symbols-outlined">save</span>
          {{ submitting ? '保存中...' : '保存' }}
        </button>
      </div>
    </div>

    <!-- 1. 基础信息 -->
    <section class="card">
      <h2 class="card-title"><span class="material-symbols-outlined">info</span> 基础信息</h2>
      <div class="form-grid">
        <div class="field">
          <label class="field-label">服务名称 <span class="required">*</span></label>
          <input v-model="form.name" class="input" maxlength="40" placeholder="例：精致洗澡 SPA" />
        </div>
        <div class="field">
          <label class="field-label">一级分类 <span class="required">*</span></label>
          <select v-model="form.category" class="input" @change="onCategoryChange">
            <option v-for="c in catalog?.categories" :key="c.name" :value="c.name">{{ c.name }}</option>
          </select>
        </div>
        <div class="field full">
          <label class="field-label">二级子类</label>
          <div class="chips">
            <button
              v-for="sub in subOptions"
              :key="sub"
              type="button"
              class="chip"
              :class="{ active: form.subCategory === sub }"
              @click="form.subCategory = form.subCategory === sub ? '' : sub"
            >
              {{ sub }}
            </button>
            <span v-if="!subOptions.length" class="chip-hint">请先选择一级分类</span>
          </div>
        </div>
        <div class="field">
          <label class="field-label">封面图</label>
          <div class="cover-upload">
            <label class="btn-outline upload-btn">
              <span class="material-symbols-outlined">add_photo_alternate</span>
              {{ uploadingCover ? '上传中...' : '本地上传' }}
              <input type="file" accept="image/*" class="file-input" :disabled="uploadingCover" @change="onCoverPick" />
            </label>
            <button v-if="form.cover" class="btn-text danger" type="button" @click="form.cover = ''">清除</button>
            <img v-if="form.cover" :src="form.cover" class="cover-preview" alt="cover"
                 @error="($event.target as HTMLImageElement).style.opacity = '0.2'" />
          </div>
        </div>
        <div class="field">
          <label class="field-label">状态</label>
          <select v-model="form.status" class="input">
            <option :value="1">上架</option>
            <option :value="0">下架</option>
          </select>
        </div>
        <div class="field full">
          <label class="field-label">服务描述</label>
          <textarea v-model="form.description" class="textarea" rows="4" placeholder="详细介绍服务内容、流程、适用场景"></textarea>
        </div>
      </div>
    </section>

    <!-- 2. 适用范围与服务方式 -->
    <section class="card">
      <h2 class="card-title"><span class="material-symbols-outlined">pets</span> 适用范围与服务方式</h2>
      <div class="form-grid">
        <div class="field">
          <label class="field-label">适用宠物</label>
          <div class="radio-row">
            <button
              v-for="p in (catalog?.petTypes || [])"
              :key="p.value"
              type="button"
              class="radio-pill"
              :class="{ active: form.petType === p.value }"
              @click="form.petType = p.value as any"
            >
              {{ p.label }}
            </button>
          </div>
        </div>
        <div class="field">
          <label class="field-label">服务方式</label>
          <div class="radio-row">
            <button
              v-for="m in (catalog?.serviceModes || [])"
              :key="m.value"
              type="button"
              class="radio-pill"
              :class="{ active: form.serviceMode === m.value }"
              @click="form.serviceMode = m.value as any"
            >
              {{ m.label }}
            </button>
          </div>
          <div v-if="form.serviceMode === 'onsite'" class="inline-field">
            <label class="field-label">上门费 (¥)</label>
            <input v-model.number="form.onsiteFee" type="number" min="0" step="1" class="input sm" placeholder="0" />
          </div>
        </div>
      </div>
    </section>

    <!-- 3. 价格与展示 -->
    <section class="card">
      <h2 class="card-title"><span class="material-symbols-outlined">sell</span> 价格与展示</h2>
      <div class="form-grid">
        <div class="field">
          <label class="field-label">
            手动起价 (¥) <span class="required">*</span>
            <span v-if="specs.length" class="hint">（有规格档位时自动取最低档价）</span>
          </label>
          <input v-model.number="form.price" type="number" min="0" step="0.01" class="input" placeholder="0.00" />
        </div>
        <div class="field">
          <label class="field-label">划线原价 (¥) <span class="hint">选填</span></label>
          <input v-model.number="form.originalPrice" type="number" min="0" step="0.01" class="input" placeholder="0.00" />
        </div>
        <div class="field">
          <label class="field-label">单次时长 (分钟) <span class="hint">无档位时生效</span></label>
          <input v-model.number="form.duration" type="number" min="1" class="input" />
        </div>
        <div class="field">
          <label class="field-label">标签 <span class="hint">逗号分隔</span></label>
          <input v-model="form.tags" class="input" maxlength="120" placeholder="例：热销, 节假日不加价" />
        </div>
      </div>
    </section>

    <!-- 4. 营业时段与预约 -->
    <section class="card">
      <h2 class="card-title"><span class="material-symbols-outlined">schedule</span> 营业时段与预约余位</h2>
      <div class="form-grid">
        <div class="field">
          <label class="field-label">营业开始</label>
          <input v-model="form.openTime" type="time" class="input" />
        </div>
        <div class="field">
          <label class="field-label">营业结束</label>
          <input v-model="form.closeTime" type="time" class="input" />
        </div>
        <div class="field">
          <label class="field-label">单时段时长 (分钟)</label>
          <select v-model.number="form.slotMinutes" class="input">
            <option :value="30">30 分钟</option>
            <option :value="45">45 分钟</option>
            <option :value="60">60 分钟</option>
            <option :value="90">90 分钟</option>
            <option :value="120">120 分钟</option>
          </select>
        </div>
        <div class="field">
          <label class="field-label">每时段可约名额</label>
          <input v-model.number="form.slotCapacity" type="number" min="1" max="99" class="input" />
        </div>
        <div class="field">
          <label class="field-label">可提前预约天数</label>
          <input v-model.number="form.advanceDays" type="number" min="1" max="90" class="input" />
        </div>
      </div>
    </section>

    <!-- 5. 规格档位 -->
    <section class="card">
      <h2 class="card-title">
        <span class="material-symbols-outlined">straighten</span> 规格档位
        <span class="hint">按体型 / 毛长分档定价{{ isDaily ? '（按天计费）' : '' }}</span>
      </h2>
      <div v-if="!specs.length" class="empty-block">
        <span class="material-symbols-outlined">add_box</span>
        <p>暂无规格档位，可添加「小型犬 / 大型犬」等区分定价</p>
      </div>
      <div v-else class="spec-list">
        <div v-for="(s, i) in specs" :key="i" class="spec-row">
          <div class="spec-idx">{{ i + 1 }}</div>
          <div class="spec-fields">
            <input v-model="s.name" class="input" maxlength="40" placeholder="档位名，如：小型犬洗护" />
            <select v-model="s.petKind" class="input sm">
              <option v-for="k in petKinds" :key="k" :value="k">{{ k === 'all' ? '全部' : k === 'dog' ? '狗' : '猫' }}</option>
            </select>
            <select v-model="s.coat" class="input sm">
              <option v-for="c in coats" :key="c" :value="c">{{ c }}</option>
            </select>
            <input v-model.number="s.weightMin" type="number" min="0" class="input xs" placeholder="体重下限kg" />
            <input v-model.number="s.weightMax" type="number" min="0" class="input xs" placeholder="体重上限kg" />
            <input v-model.number="s.price" type="number" min="0" step="0.01" class="input xs" placeholder="价格" />
            <input v-model.number="s.duration" type="number" min="1" class="input xs" placeholder="分钟" />
            <button class="row-del" type="button" @click="removeSpec(i)" title="删除">
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
        </div>
      </div>
      <button class="btn-add" type="button" @click="addSpec">
        <span class="material-symbols-outlined">add</span> 添加规格档位
      </button>
    </section>

    <!-- 6. 加购项 -->
    <section class="card">
      <h2 class="card-title">
        <span class="material-symbols-outlined">add_shopping_cart</span> 加购项
        <span class="hint">选填，如：指甲修剪、挤肛门腺</span>
      </h2>
      <div v-if="!addons.length" class="empty-block">
        <span class="material-symbols-outlined">add_box</span>
        <p>暂无加购项</p>
      </div>
      <div v-else class="spec-list">
        <div v-for="(a, i) in addons" :key="i" class="spec-row">
          <div class="spec-idx">{{ i + 1 }}</div>
          <div class="spec-fields">
            <input v-model="a.name" class="input" maxlength="40" placeholder="加购项名称" />
            <input v-model.number="a.price" type="number" min="0" step="0.01" class="input xs" placeholder="价格" />
            <input v-model.number="a.duration" type="number" min="0" class="input xs" placeholder="加时(分)" />
            <input v-model="a.description" class="input" maxlength="120" placeholder="说明（选填）" />
            <button class="row-del" type="button" @click="removeAddon(i)" title="删除">
              <span class="material-symbols-outlined">close</span>
            </button>
          </div>
        </div>
      </div>
      <button class="btn-add" type="button" @click="addAddon">
        <span class="material-symbols-outlined">add</span> 添加加购项
      </button>
    </section>

    <!-- 7. 包含项 / 须知 / 退改 -->
    <section class="card">
      <h2 class="card-title"><span class="material-symbols-outlined">menu_book</span> 服务包含 · 预约须知 · 退改</h2>
      <div class="form-grid">
        <div class="field full">
          <label class="field-label">服务包含项 <span class="hint">每行一项</span></label>
          <textarea v-model="form.includeItems" class="textarea" rows="4" placeholder="例：&#10;全身沐浴&#10;耳朵清洁&#10;毛发吹干"></textarea>
        </div>
        <div class="field full">
          <label class="field-label">预约须知 <span class="hint">每行一项</span></label>
          <textarea v-model="form.notice" class="textarea" rows="4" placeholder="例：&#10;到店请提前 10 分钟&#10;长毛犬建议每日梳理"></textarea>
        </div>
        <div class="field full">
          <label class="field-label">退改政策</label>
          <textarea v-model="form.refundPolicy" class="textarea" rows="3" placeholder="例：预约后 2 小时内可免费取消"></textarea>
        </div>
      </div>
    </section>

    <div class="footer-actions">
      <button class="btn-primary" :disabled="submitting" @click="save">
        <span class="material-symbols-outlined">save</span>
        {{ submitting ? '保存中...' : '保存服务' }}
      </button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.service-edit-view { display: flex; flex-direction: column; gap: $spacing-md; }
.page-head { display: flex; justify-content: space-between; align-items: flex-end; flex-wrap: wrap; gap: $spacing-md; }
.page-title { font-family: $font-family-headline; font-size: $font-size-headline-lg; font-weight: 700; color: $color-on-surface; }
.page-sub { font-size: $font-size-body-md; color: $color-on-surface-variant; margin-top: 4px; }

.head-actions { display: flex; gap: $spacing-sm; flex-wrap: wrap; }
.btn-primary, .btn-outline, .btn-danger {
  display: inline-flex; align-items: center; gap: $spacing-xs; padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md; font-weight: 700; cursor: pointer; border: 1px solid transparent;
}
.btn-primary { background: $color-brand; color: #fff; &:disabled { opacity: 0.6; cursor: not-allowed; } }
.btn-outline { background: transparent; color: $color-brand; border-color: $color-brand; }
.btn-danger { background: transparent; color: $color-emergency; border-color: $color-emergency; }

// ===== 卡片 =====
.card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
}
.card-title {
  display: flex; align-items: center; gap: 6px;
  font-family: $font-family-headline; font-size: $font-size-headline-md; font-weight: 700;
  color: $color-on-surface; margin-bottom: $spacing-md;
  .material-symbols-outlined { font-size: 20px; color: $color-brand; }
  .hint { font-size: $font-size-label-sm; font-weight: 400; color: $color-outline; }
}

.form-grid { display: grid; grid-template-columns: 1fr; gap: $spacing-md; @include desktop { grid-template-columns: 1fr 1fr; } }
.field { display: flex; flex-direction: column; gap: 4px; }
.field.full { grid-column: 1 / -1; }
.field-label { font-size: $font-size-label-sm; color: $color-on-surface-variant; }
.required { color: $color-emergency; }
.hint { color: $color-outline; font-weight: 400; }

.input, .textarea {
  padding: $spacing-sm $spacing-md; border: 1px solid $color-outline-variant; border-radius: $radius-md;
  background: #fff; font-size: $font-size-body-md; color: $color-on-surface; font-family: inherit;
  &:focus { outline: none; border-color: $color-brand; box-shadow: 0 0 0 3px rgba(255, 122, 50, 0.12); }
}
.input.sm { padding: $spacing-xs $spacing-sm; font-size: $font-size-label-md; }
.input.xs { padding: $spacing-xs $spacing-sm; font-size: $font-size-label-md; width: 96px; }
.textarea { resize: vertical; min-height: 90px; line-height: 1.6; }

// ===== 子类 chips =====
.chips { display: flex; flex-wrap: wrap; gap: $spacing-xs; }
.chip {
  padding: $spacing-xs $spacing-md; border-radius: $radius-full;
  border: 1px solid $color-outline-variant; background: $color-surface-container-low;
  font-size: $font-size-label-md; color: $color-on-surface-variant; cursor: pointer;
  transition: all $transition-fast;
  &:hover { border-color: rgba($color-brand, 0.5); }
  &.active { background: $color-brand; color: #fff; border-color: $color-brand; }
}
.chip-hint { font-size: $font-size-label-sm; color: $color-outline; padding: $spacing-xs; }

// ===== 单选 pill =====
.radio-row { display: flex; flex-wrap: wrap; gap: $spacing-sm; }
.radio-pill {
  padding: $spacing-sm $spacing-lg; border-radius: $radius-full;
  border: 1px solid $color-outline-variant; background: $color-surface-container-low;
  font-size: $font-size-label-md; font-weight: 600; color: $color-on-surface-variant; cursor: pointer;
  transition: all $transition-fast;
  &:hover { border-color: rgba($color-brand, 0.5); }
  &.active { background: rgba($color-brand, 0.12); color: $color-brand; border-color: $color-brand; }
}
.inline-field { display: flex; align-items: center; gap: $spacing-sm; margin-top: $spacing-sm; }

// ===== 封面 =====
.cover-upload { display: flex; align-items: center; gap: $spacing-sm; flex-wrap: wrap; }
.upload-btn { position: relative; overflow: hidden; }
.upload-btn .material-symbols-outlined { font-size: 18px; }
.file-input { position: absolute; inset: 0; opacity: 0; width: 100%; height: 100%; cursor: pointer; }
.cover-preview { width: 96px; height: 72px; object-fit: cover; border-radius: $radius-sm; background: $color-surface-container; }
.btn-text.danger { background: none; border: none; color: $color-emergency; font-size: $font-size-label-sm; cursor: pointer; }

// ===== 规格 / 加购 列表 =====
.empty-block {
  display: flex; align-items: center; gap: $spacing-sm; padding: $spacing-md;
  border: 1px dashed $color-outline-variant; border-radius: $radius-md; color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 26px; color: $color-brand; }
  p { font-size: $font-size-label-md; margin: 0; }
}
.spec-list { display: flex; flex-direction: column; gap: $spacing-sm; }
.spec-row { display: flex; align-items: flex-start; gap: $spacing-sm; }
.spec-idx {
  width: 24px; height: 24px; flex-shrink: 0; margin-top: 6px;
  display: flex; align-items: center; justify-content: center;
  border-radius: $radius-full; background: $color-surface-container-low;
  font-size: $font-size-label-sm; color: $color-on-surface-variant; font-weight: 700;
}
.spec-fields { flex: 1; display: flex; flex-wrap: wrap; gap: $spacing-xs; align-items: center; }
.row-del {
  width: 30px; height: 30px; flex-shrink: 0; border-radius: $radius-full;
  border: 1px solid $color-outline-variant; background: $color-surface-container-low;
  color: $color-emergency; cursor: pointer; display: inline-flex; align-items: center; justify-content: center;
  .material-symbols-outlined { font-size: 18px; }
  &:hover { background: rgba(244, 67, 54, 0.1); }
}
.btn-add {
  margin-top: $spacing-md; display: inline-flex; align-items: center; gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg; border-radius: $radius-full;
  border: 1px dashed $color-brand; background: rgba($color-brand, 0.06);
  color: $color-brand; font-weight: 700; font-size: $font-size-label-md; cursor: pointer;
  .material-symbols-outlined { font-size: 18px; }
  &:hover { background: rgba($color-brand, 0.12); }
}

.footer-actions { display: flex; justify-content: flex-end; padding-bottom: $spacing-lg; }
</style>
