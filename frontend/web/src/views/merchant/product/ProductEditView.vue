<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/modules/merchant'
import { productApi, type Category } from '@/api/modules/product'
import type { ProductSpecGroup } from '@/api/modules/merchant'

const route = useRoute()
const router = useRouter()

const id = computed(() => {
  const v = route.query.id
  if (!v) return null
  return Number(v)
})

const isEdit = computed(() => id.value != null)

interface SpecGroup {
  name: string
  values: string[]
  draft?: string
}
interface SkuRow {
  specValues: Record<string, string>
  specText: string
  price: number
  stock: number
  image: string
  id?: number
}

const form = ref({
  name: '',
  categoryId: null as number | null,
  mainImage: '',
  subImages: [] as string[],
  detailImages: [] as string[],
  originalPrice: '',
  salePrice: '',
  detail: '',
  status: 1 as 0 | 1,
})

const specGroups = ref<SpecGroup[]>([])
const skuRows = ref<SkuRow[]>([])
const singleStock = ref(0)

const categories = ref<Category[]>([])
const flat = ref<Array<{ id: number; name: string }>>([])

const submitting = ref(false)
const loading = ref(false)
const uploading = ref(false)

const hasSpecs = computed(() => skuRows.value.length > 0)

function flatten(nodes: Category[], parent = ''): Array<{ id: number; name: string }> {
  const list: Array<{ id: number; name: string }> = []
  for (const n of nodes) {
    const label = parent ? `${parent} / ${n.name}` : n.name
    list.push({ id: n.id, name: label })
    if (n.children?.length) list.push(...flatten(n.children, label))
  }
  return list
}

function categoryName(cid: number | null | undefined) {
  if (!cid) return '—'
  return flat.value.find((c) => c.id === cid)?.name || String(cid)
}

async function loadCategories() {
  try {
    const tree = await productApi.tree()
    categories.value = tree
    flat.value = flatten(tree)
  } catch {
    flat.value = []
  }
}

function skuKey(specValues: Record<string, string>) {
  return Object.values(specValues).sort().join('|')
}

function rebuildSkus() {
  const active = specGroups.value.filter((g) => g.name.trim() && g.values.some((v) => v.trim()))
  const prev = new Map(skuRows.value.map((r) => [skuKey(r.specValues), r]))
  if (active.length === 0) {
    skuRows.value = []
    return
  }
  let combos: Record<string, string>[] = [{}]
  for (const g of active) {
    const vals = g.values.filter((v) => v.trim())
    const next: Record<string, string>[] = []
    for (const c of combos) for (const v of vals) next.push({ ...c, [g.name.trim()]: v.trim() })
    combos = next
  }
  skuRows.value = combos.map((c) => {
    const k = skuKey(c)
    const old = prev.get(k)
    return {
      specValues: c,
      specText: Object.entries(c).map(([k, v]) => `${k}:${v}`).join(' / '),
      price: old ? old.price : Number(form.value.salePrice) || 0,
      stock: old ? old.stock : 0,
      image: old?.image ?? '',
      id: old?.id,
    }
  })
}

function addSpecGroup() {
  specGroups.value.push({ name: '', values: [], draft: '' })
}
function removeSpecGroup(i: number) {
  specGroups.value.splice(i, 1)
  rebuildSkus()
}
function addSpecValue(g: SpecGroup) {
  const v = (g.draft ?? '').trim()
  if (!v) return
  if (!g.values.includes(v)) g.values.push(v)
  g.draft = ''
  rebuildSkus()
}
function removeSpecValue(g: SpecGroup, i: number) {
  g.values.splice(i, 1)
  rebuildSkus()
}

// 后端 json 列可能以字符串形式返回（MySQL 驱动），统一解析为数组
function asArr(raw: any): string[] {
  if (!raw) return []
  let v = raw
  if (typeof raw === 'string') {
    try {
      v = JSON.parse(raw)
    } catch {
      return []
    }
  }
  return Array.isArray(v) ? v.map(String) : []
}

// 规格组：specs 是对象数组 [{name, values}]，必须保留结构
// （不能复用 asArr——asArr 会把每个对象 map(String) 成 '[object Object]'，导致 g.name 为 undefined 后 .trim() 崩溃）
function asSpecGroups(raw: any): SpecGroup[] {
  if (!raw) return []
  let arr: any = raw
  if (typeof raw === 'string') {
    try {
      arr = JSON.parse(raw)
    } catch {
      return []
    }
  }
  if (!Array.isArray(arr)) return []
  return arr.map((g: any) => ({
    name: typeof g?.name === 'string' ? g.name : '',
    values: Array.isArray(g?.values)
      ? g.values.map((x: any) => String(x)).filter((x: string) => x.trim())
      : [],
    draft: '',
  }))
}

// SKU 的 spec_values 由 JDBC 直出，是 JSON 字符串（如 '{"规格":"标准装"}'），
// 直接丢给 skuKey 会被 Object.values 拆成字符数组，导致规格键完全错配、价格/库存回填不上（#6/#7）
function asSpecValues(raw: any): Record<string, string> {
  if (!raw) return {}
  let v = raw
  if (typeof raw === 'string') {
    const s = raw.trim()
    if (!s || s === 'null') return {}
    try {
      v = JSON.parse(s)
    } catch {
      return {}
    }
  }
  if (!v || typeof v !== 'object' || Array.isArray(v)) return {}
  const out: Record<string, string> = {}
  for (const [k, val] of Object.entries(v)) out[String(k)] = String(val)
  return out
}

function hideImg(e: Event) {
  ;(e.target as HTMLImageElement).style.display = 'none'
}

async function loadProduct() {
  if (!id.value) return
  loading.value = true
  try {
    // 用商家侧详情：带归属校验、能读到已下架商品、返回未软删的 SKU，且不会污染 C 端浏览量（#14）
    const p = await merchantApi.productDetail(id.value)
    form.value = {
      name: p.name || '',
      categoryId: p.categoryId ?? null,
      mainImage: p.mainImage || '',
      subImages: asArr(p.subImages),
      detailImages: asArr(p.detailImages),
      originalPrice: p.originalPrice != null ? String(p.originalPrice) : '',
      salePrice: p.salePrice != null ? String(p.salePrice) : '',
      detail: p.detail || '',
      status: Number(p.status) === 0 ? 0 : 1,
    }
    // 规格组（specs 同样可能以字符串返回，且是对象数组，用专用解析器保留结构）
    specGroups.value = asSpecGroups(p.specs)
    rebuildSkus()
    // 回填已有 SKU 的 id/价格/库存/图：spec_values 需先 JSON.parse，否则 key 错配导致库存恒为 0（#6/#7）
    if (Array.isArray(p.skus) && p.skus.length) {
      const normSpec = (s: any) => asSpecValues(s?.specValues ?? s?.spec_values)
      const map = new Map(p.skus.map((s: any) => [skuKey(normSpec(s)), s]))
      for (const r of skuRows.value) {
        const s: any = map.get(skuKey(r.specValues))
        if (s) {
          r.id = s.id
          r.price = Number(s.price ?? form.value.salePrice) || 0
          r.stock = Number(s.stock ?? 0)
          r.image = s.image || ''
        }
      }
      // 无规格商品：回填默认 SKU（spec_values 为 NULL）的库存
      if (skuRows.value.length === 0) {
        const def: any =
          p.skus.find((s: any) => Object.keys(normSpec(s)).length === 0) ?? p.skus[0]
        if (def) singleStock.value = Number(def.stock ?? 0)
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载商品失败')
    router.replace('/merchant/product/list')
  } finally {
    loading.value = false
  }
}

function onPick(e: Event, target: 'main' | 'sub' | 'detail' | 'sku', skuIndex?: number) {
  const input = e.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length) return
  uploading.value = true
  merchantApi
    .uploadProductImages(files)
    .then((res) => {
      const urls = res.urls || []
      if (target === 'main') form.value.mainImage = urls[0] || form.value.mainImage
      else if (target === 'sub') form.value.subImages.push(...urls)
      else if (target === 'detail') form.value.detailImages.push(...urls)
      else if (target === 'sku' && skuIndex != null) skuRows.value[skuIndex].image = urls[0]
    })
    .catch(() => {})
    .finally(() => (uploading.value = false))
}

function removeSub(i: number) {
  form.value.subImages.splice(i, 1)
}
function removeDetail(i: number) {
  form.value.detailImages.splice(i, 1)
}

async function submit() {
  if (!form.value.name.trim()) return ElMessage.warning('请填写商品名称')
  if (!form.value.categoryId) return ElMessage.warning('请选择商品分类')
  if (!form.value.mainImage.trim()) return ElMessage.warning('请上传主图')
  if (!form.value.salePrice || Number(form.value.salePrice) <= 0) {
    return ElMessage.warning('请填写有效的售价')
  }
  if (hasSpecs.value) {
    for (const r of skuRows.value) {
      if (!r.price || Number(r.price) <= 0) return ElMessage.warning(`规格「${r.specText}」价格无效`)
      if (r.stock == null || Number(r.stock) < 0) return ElMessage.warning(`规格「${r.specText}」库存无效`)
    }
  } else if (singleStock.value == null || Number(singleStock.value) < 0) {
    return ElMessage.warning('请填写有效的库存量')
  }

  submitting.value = true
  try {
    const payload: any = {
      name: form.value.name.trim(),
      categoryId: form.value.categoryId,
      mainImage: form.value.mainImage.trim(),
      salePrice: Number(form.value.salePrice),
      status: form.value.status,
    }
    // 编辑模式必须无条件下发可清空字段：后端是"字段非 null 才更新"的部分更新，
    // 省略字段 = 保持原值，会导致用户删掉详情/副图后保存仍是旧内容（#12）。
    // 下发空串 / 空数组即可正确清空；新建模式则只带有值的字段，避免写入无意义空值。
    if (isEdit.value) {
      payload.detail = form.value.detail ?? ''
      payload.subImages = form.value.subImages
      payload.detailImages = form.value.detailImages
      payload.originalPrice = form.value.originalPrice ? Number(form.value.originalPrice) : null
    } else {
      if (form.value.originalPrice) payload.originalPrice = Number(form.value.originalPrice)
      if (form.value.detail) payload.detail = form.value.detail
      if (form.value.subImages.length) payload.subImages = form.value.subImages
      if (form.value.detailImages.length) payload.detailImages = form.value.detailImages
    }

    if (hasSpecs.value) {
      const active = specGroups.value
        .filter((g) => g.name.trim() && g.values.some((v) => v.trim()))
        .map((g) => ({ name: g.name.trim(), values: g.values.filter((v) => v.trim()) }))
      payload.specs = active
      payload.skus = skuRows.value.map((r) => ({
        id: r.id,
        specValues: r.specValues,
        price: Number(r.price),
        stock: Number(r.stock),
        image: r.image || null,
      }))
    } else {
      payload.stock = Number(singleStock.value)
      // 由"多规格"改回"无规格"时必须把 specs 清成空数组，
      // 否则旧规格定义残留在 t_product.specs，下次进编辑页又会重建出幽灵规格行（#10）
      if (isEdit.value) payload.specs = []
    }

    if (isEdit.value && id.value) {
      await merchantApi.updateProduct(id.value, payload)
      ElMessage.success('商品已更新')
    } else {
      await merchantApi.createProduct(payload)
      ElMessage.success('商品已创建')
    }
    router.push('/merchant/product/list')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function toggleStatus() {
  if (!isEdit.value || !id.value) return
  try {
    const r = await merchantApi.toggleProductStatus(id.value)
    form.value.status = Number(r.status) === 1 ? 1 : 0
    ElMessage.success(form.value.status === 1 ? '已上架' : '已下架')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function onDelete() {
  if (!isEdit.value || !id.value) return
  try {
    await ElMessageBox.confirm('确认删除该商品？删除后无法在商品列表中显示。', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await merchantApi.deleteProduct(id.value)
    ElMessage.success('商品已删除')
    router.push('/merchant/product/list')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// 售价变动时，未单独设价的 SKU 行自动跟随默认售价
watch(
  () => form.value.salePrice,
  (v) => {
    const def = Number(v) || 0
    for (const r of skuRows.value) if (!r.price) r.price = def
  },
)

onMounted(async () => {
  await loadCategories()
  await loadProduct()
})
</script>

<template>
  <div class="pev" v-loading="loading">
    <!-- 顶栏 -->
    <header class="pev-head">
      <div class="pev-head__title">
        <h1 class="pev-title">{{ isEdit ? '编辑商品' : '新增商品' }}</h1>
        <p class="pev-sub">
          <template v-if="isEdit">商品 ID {{ id }} · 分类：{{ categoryName(form.categoryId) }}</template>
          <template v-else>填写商品信息，带 <span class="req">*</span> 为必填项</template>
        </p>
      </div>
      <div class="pev-head__actions">
        <button class="btn ghost" @click="router.back()">
          <span class="material-symbols-outlined">arrow_back</span>返回
        </button>
        <button v-if="isEdit" class="btn ghost" @click="toggleStatus">
          <span class="material-symbols-outlined">swap_vert</span>{{ form.status === 1 ? '下架' : '上架' }}
        </button>
        <button v-if="isEdit" class="btn danger" @click="onDelete">
          <span class="material-symbols-outlined">delete</span>删除
        </button>
        <button class="btn primary" :disabled="submitting || uploading" @click="submit">
          <span class="material-symbols-outlined">{{ submitting ? 'hourglass_top' : 'save' }}</span>
          {{ submitting ? '保存中…' : '保存商品' }}
        </button>
      </div>
    </header>

    <div class="pev-grid">
      <!-- 左：主表单 -->
      <div class="pev-main">
        <!-- 基础信息 -->
        <section class="card">
          <div class="card__head">
            <span class="chip"><span class="material-symbols-outlined">info</span></span>
            <h2 class="card__title">基础信息</h2>
          </div>
          <div class="card__body">
            <div class="field">
              <label class="label">商品名称 <span class="req">*</span></label>
              <input v-model="form.name" class="input" placeholder="例如：皇家猫粮 K36 4kg" maxlength="80" />
            </div>
            <div class="field">
              <label class="label">商品分类 <span class="req">*</span></label>
              <div class="select-wrap">
                <select v-model="form.categoryId" class="input">
                  <option :value="null">请选择分类</option>
                  <option v-for="c in flat" :key="c.id" :value="c.id">{{ c.name }}</option>
                </select>
              </div>
            </div>
            <div class="grid-3">
              <div class="field">
                <label class="label">售价 (¥) <span class="req">*</span></label>
                <input v-model="form.salePrice" type="number" min="0" step="0.01" class="input" placeholder="99.00" />
              </div>
              <div class="field">
                <label class="label">原价 (¥)</label>
                <input v-model="form.originalPrice" type="number" min="0" step="0.01" class="input" placeholder="129.00" />
              </div>
              <div class="field">
                <label class="label">状态</label>
                <div class="select-wrap">
                  <select v-model="form.status" class="input">
                    <option :value="1">上架</option>
                    <option :value="0">下架</option>
                  </select>
                </div>
              </div>
            </div>
          </div>
        </section>

        <!-- 规格与库存 -->
        <section class="card">
          <div class="card__head">
            <span class="chip"><span class="material-symbols-outlined">tune</span></span>
            <h2 class="card__title">规格与库存</h2>
            <span v-if="hasSpecs" class="badge">{{ skuRows.length }} 个规格组合</span>
          </div>
          <div class="card__body">
            <!-- 无规格：统一库存 -->
            <div v-if="!hasSpecs" class="single-stock">
              <div class="field stock-field">
                <label class="label">库存量 <span class="req">*</span></label>
                <input v-model="singleStock" type="number" min="0" step="1" class="input" placeholder="0" />
              </div>
              <p class="hint">未设置规格时，使用整个商品统一的库存量。</p>
            </div>

            <!-- 有规格：SKU 表 -->
            <div v-else class="sku-wrap">
              <div class="sku-scroll">
                <table class="sku-table">
                  <thead>
                    <tr>
                      <th>规格组合</th>
                      <th class="num">价格 (¥)</th>
                      <th class="num">库存量</th>
                      <th class="ctr">规格图</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="(r, idx) in skuRows" :key="r.specText">
                      <td>
                        <span v-for="(v, k) in r.specValues" :key="k" class="sku-pill">{{ k }}：{{ v }}</span>
                      </td>
                      <td class="num"><input v-model="r.price" type="number" min="0" step="0.01" class="cell" /></td>
                      <td class="num"><input v-model="r.stock" type="number" min="0" step="1" class="cell" /></td>
                      <td class="ctr">
                        <div class="sku-img">
                          <img v-if="r.image" :src="r.image" alt="" @error="hideImg" />
                          <label class="sku-add" :class="{ filled: !!r.image }">
                            <span class="material-symbols-outlined">{{ r.image ? 'edit' : 'add_a_photo' }}</span>
                            <input type="file" accept="image/*" hidden @change="onPick($event, 'sku', idx)" :disabled="uploading" />
                          </label>
                        </div>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </div>

            <!-- 规格定义 -->
            <div class="spec-def">
              <div class="divider"><span>规格定义</span></div>
              <p class="hint">添加规格后会自动生成「规格组合 × 价格 × 库存」列表；不添加则使用上方统一库存量。</p>
              <div class="spec-groups">
                <div v-for="(g, gi) in specGroups" :key="gi" class="spec-group">
                  <div class="spec-group__head">
                    <span class="spec-idx">{{ gi + 1 }}</span>
                    <input v-model="g.name" class="input spec-name" placeholder="规格名，如：净含量" @blur="rebuildSkus" />
                    <button class="icon-btn danger" @click="removeSpecGroup(gi)" title="删除该规格">
                      <span class="material-symbols-outlined">close</span>
                    </button>
                  </div>
                  <div class="spec-values">
                    <span v-for="(val, vi) in g.values" :key="vi" class="tag">
                      {{ val }}
                      <button class="tag-x" @click="removeSpecValue(g, vi)">×</button>
                    </span>
                    <input
                      v-model="g.draft"
                      class="input spec-value-input"
                      placeholder="输入可选值后回车"
                      @keyup.enter="addSpecValue(g)"
                    />
                  </div>
                </div>
              </div>
              <button class="add-btn" @click="addSpecGroup">
                <span class="material-symbols-outlined">add</span>添加规格（如：净含量 / 口味）
              </button>
            </div>
          </div>
        </section>

        <!-- 商品详情 -->
        <section class="card">
          <div class="card__head">
            <span class="chip"><span class="material-symbols-outlined">description</span></span>
            <h2 class="card__title">商品详情</h2>
          </div>
          <div class="card__body">
            <textarea v-model="form.detail" class="textarea" rows="8" placeholder="商品描述、卖点、喂养建议等"></textarea>
          </div>
        </section>
      </div>

      <!-- 右：素材（吸顶） -->
      <aside class="pev-aside">
        <section class="card">
          <div class="card__head">
            <span class="chip"><span class="material-symbols-outlined">photo_library</span></span>
            <h2 class="card__title">展示素材</h2>
          </div>
          <div class="card__body media">
            <div class="field">
              <label class="label">主图 <span class="req">*</span></label>
              <label class="drop hero" :class="{ empty: !form.mainImage }">
                <input type="file" accept="image/*" hidden @change="onPick($event, 'main')" :disabled="uploading" />
                <img v-if="form.mainImage" :src="form.mainImage" class="drop-img" alt="主图" @error="hideImg" />
                <div v-else class="drop-ph">
                  <span class="material-symbols-outlined">cloud_upload</span>
                  <span>点击上传主图</span>
                </div>
                <button v-if="form.mainImage" type="button" class="drop-x" @click.prevent="form.mainImage = ''">
                  <span class="material-symbols-outlined">delete</span>
                </button>
              </label>
            </div>

            <div class="field">
              <label class="label">副图 <span class="muted">（最多 9 张）</span></label>
              <div class="gallery">
                <div v-for="(img, i) in form.subImages" :key="i" class="g-thumb">
                  <img :src="img" alt="" @error="hideImg" />
                  <button class="g-x" @click="removeSub(i)">×</button>
                </div>
                <label v-if="form.subImages.length < 9" class="g-add">
                  <span class="material-symbols-outlined">add</span>
                  <input type="file" accept="image/*" multiple hidden @change="onPick($event, 'sub')" :disabled="uploading" />
                </label>
              </div>
            </div>

            <div class="field">
              <label class="label">商品详情图 <span class="muted">（多张）</span></label>
              <div class="gallery">
                <div v-for="(img, i) in form.detailImages" :key="i" class="g-thumb">
                  <img :src="img" alt="" @error="hideImg" />
                  <button class="g-x" @click="removeDetail(i)">×</button>
                </div>
                <label class="g-add">
                  <span class="material-symbols-outlined">add</span>
                  <input type="file" accept="image/*" multiple hidden @change="onPick($event, 'detail')" :disabled="uploading" />
                </label>
              </div>
            </div>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.pev {
  max-width: 1200px;
  margin: 0 auto;
  padding: $spacing-lg $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

/* ===== 顶栏 ===== */
.pev-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: $spacing-md;
}

.pev-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.pev-sub {
  margin-top: 4px;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

.pev-head__actions {
  display: flex;
  gap: $spacing-sm;
  flex-wrap: wrap;
}

.btn {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform 0.15s ease, box-shadow 0.2s ease, background 0.2s ease;

  &:active {
    transform: translateY(1px);
  }
}

.btn.primary {
  background: $color-brand;
  color: #fff;
  box-shadow: 0 6px 16px rgba(255, 122, 50, 0.28);

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
    box-shadow: none;
  }
}

.btn.ghost {
  background: transparent;
  border-color: $color-outline-variant;
  color: $color-on-surface-variant;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

.btn.danger {
  background: transparent;
  border-color: $color-emergency;
  color: $color-emergency;

  &:hover {
    background: $color-error-container;
  }
}

/* ===== 布局 ===== */
.pev-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  align-items: start;

  @media (min-width: 980px) {
    grid-template-columns: minmax(0, 1.7fr) minmax(320px, 1fr);
  }
}

.pev-main {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  min-width: 0;
}

.pev-aside {
  min-width: 0;

  @media (min-width: 980px) {
    position: sticky;
    top: $spacing-lg;
  }
}

/* ===== 卡片 ===== */
.card {
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  overflow: hidden;
}

.card__head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md $spacing-lg;
  border-bottom: 1px solid $color-outline-variant;
}

.chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: $radius-md;
  background: $color-surface-container;
  color: $color-brand;

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.card__title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0;
}

.badge {
  margin-left: auto;
  padding: 2px $spacing-sm;
  border-radius: $radius-full;
  background: $color-surface-container;
  color: $color-on-surface-variant;
  font-size: $font-size-label-sm;
  font-weight: 600;
}

.card__body {
  padding: $spacing-lg;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

/* ===== 表单字段 ===== */
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.label {
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
}

.req {
  color: $color-emergency;
}

.muted {
  color: $color-on-surface-variant;
  font-weight: 400;
}

.hint {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  line-height: 1.5;
}

.input,
.textarea {
  padding: $spacing-sm $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  background: #fff;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  font-family: inherit;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:focus {
    outline: none;
    border-color: $color-brand;
    box-shadow: 0 0 0 3px rgba(255, 122, 50, 0.12);
  }
}

.textarea {
  resize: vertical;
  min-height: 160px;
  line-height: 1.6;
}

.select-wrap {
  position: relative;

  &::after {
    content: '';
    position: absolute;
    right: $spacing-md;
    top: 50%;
    width: 8px;
    height: 8px;
    border-right: 2px solid $color-on-surface-variant;
    border-bottom: 2px solid $color-on-surface-variant;
    transform: translateY(-65%) rotate(45deg);
    pointer-events: none;
  }

  select {
    appearance: none;
    width: 100%;
    cursor: pointer;
  }
}

.grid-3 {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;

  @media (min-width: 640px) {
    grid-template-columns: 1fr 1fr 1fr;
  }
}

/* ===== 规格 / SKU ===== */
.single-stock {
  display: flex;
  align-items: flex-end;
  gap: $spacing-md;
  flex-wrap: wrap;
}

.stock-field {
  max-width: 280px;
  flex: 1;
}

.sku-wrap {
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  overflow: hidden;
}

.sku-scroll {
  overflow-x: auto;
}

.sku-table {
  width: 100%;
  min-width: 460px;
  border-collapse: collapse;
  font-size: $font-size-body-md;

  th,
  td {
    padding: $spacing-sm $spacing-md;
    border-bottom: 1px solid $color-outline-variant;
    text-align: left;
    vertical-align: middle;
  }

  th {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    background: $color-surface-container;
    font-weight: 600;
    white-space: nowrap;
  }

  tbody tr:last-child td {
    border-bottom: none;
  }

  th.num,
  td.num {
    text-align: right;
    width: 120px;
  }

  th.ctr,
  td.ctr {
    text-align: center;
    width: 72px;
  }
}

.sku-pill {
  display: inline-block;
  margin: 2px 6px 2px 0;
  padding: 2px 8px;
  border-radius: $radius-full;
  background: $color-surface-container;
  font-size: $font-size-label-sm;
  color: $color-on-surface;
  white-space: nowrap;
}

.cell {
  width: 100%;
  padding: 6px 8px;
  text-align: right;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-sm;
  background: #fff;
  font-size: $font-size-body-md;
  font-family: inherit;
  color: $color-on-surface;

  &:focus {
    outline: none;
    border-color: $color-brand;
  }
}

.sku-img {
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 40px;
    height: 40px;
    border-radius: $radius-sm;
    object-fit: cover;
    border: 1px solid $color-outline-variant;
  }
}

.sku-add {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: $radius-sm;
  border: 1px dashed $color-outline-variant;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }

  &.filled {
    border-style: solid;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

/* 规格定义 */
.spec-def {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.divider {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  font-weight: 600;

  &::before,
  &::after {
    content: '';
    flex: 1;
    height: 1px;
    background: $color-outline-variant;
  }
}

.spec-groups {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.spec-group {
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.spec-group__head {
  display: flex;
  gap: $spacing-sm;
  align-items: center;
}

.spec-idx {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: $color-brand;
  color: #fff;
  font-size: $font-size-label-sm;
  font-weight: 700;
}

.spec-name {
  max-width: 260px;
  flex: 1;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  border-radius: $radius-full;
  border: 1px solid $color-outline-variant;
  background: transparent;
  cursor: pointer;
  color: $color-on-surface-variant;
  transition: all 0.2s ease;

  &.danger:hover {
    border-color: $color-emergency;
    color: $color-emergency;
    background: $color-error-container;
  }
}

.spec-values {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  align-items: center;
}

.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px $spacing-sm;
  background: $color-surface-container;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  color: $color-on-surface;
}

.tag-x {
  border: none;
  background: transparent;
  color: $color-on-surface-variant;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0;

  &:hover {
    color: $color-emergency;
  }
}

.spec-value-input {
  width: 200px;
  flex: 1;
  min-width: 160px;
}

.add-btn {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-full;
  border: 1px dashed $color-brand;
  color: $color-brand;
  background: transparent;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  transition: background 0.2s ease;

  &:hover {
    background: $color-surface-container;
  }
}

/* ===== 媒体上传 ===== */
.media {
  gap: $spacing-lg;
}

.drop {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1.5px dashed $color-outline-variant;
  border-radius: $radius-md;
  background: $color-surface-container-lowest;
  cursor: pointer;
  overflow: hidden;
  transition: border-color 0.2s ease, background 0.2s ease;

  &:hover {
    border-color: $color-brand;
    background: $color-surface-container;
  }

  &.hero {
    width: 100%;
    max-width: 240px;
    margin: 0 auto;
    aspect-ratio: 1 / 1;

    .drop-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .drop-ph {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    color: $color-on-surface-variant;
    font-size: $font-size-label-md;

    .material-symbols-outlined {
      font-size: 28px;
    }
  }

  .drop-x {
    position: absolute;
    top: 6px;
    right: 6px;
    width: 28px;
    height: 28px;
    border-radius: 50%;
    border: none;
    background: rgba(0, 0, 0, 0.55);
    color: #fff;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;

    &:hover {
      background: $color-emergency;
    }
  }
}

.gallery {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(84px, 1fr));
  gap: $spacing-sm;
}

.g-thumb {
  position: relative;
  aspect-ratio: 1 / 1;
  border-radius: $radius-sm;
  overflow: hidden;
  border: 1px solid $color-outline-variant;
  background: $color-surface-container;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.g-x {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;

  &:hover {
    background: $color-emergency;
  }
}

.g-add {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 1 / 1;
  border: 1.5px dashed $color-outline-variant;
  border-radius: $radius-sm;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
    background: $color-surface-container;
  }

  .material-symbols-outlined {
    font-size: 24px;
  }
}
</style>
