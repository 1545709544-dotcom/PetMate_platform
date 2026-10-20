<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi, type Product as ApiProduct, type ProductReview, type ReviewSummary } from '@/api/modules/product'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { userApi } from '@/api/modules/user'
import { trackEvent } from '@/api/track'
import PageSection from '@/components/common/PageSection.vue'
import ProductCard from '@/components/common/ProductCard.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

import mainCover from '@/assets/images/category/p1-catfood.jpg'
import thumb1 from '@/assets/images/cart/p1-dogfood.jpg'
import thumb2 from '@/assets/images/cart/p2-salmon.jpg'
import thumb3 from '@/assets/images/cart/p3-fountain.jpg'
import thumb4 from '@/assets/images/cart/r1-cat-scratcher.jpg'

import detailHero from '@/assets/images/category/p2-tree.jpg'
import catDetail from '@/assets/images/pet-health/d1.jpg'

const route = useRoute()
const router = useRouter()
const cartStore = useCartStore()
const userStore = useUserStore()

// ============ 默认数据(API 加载前的占位 + 兜底) ============
const fallbackProduct = {
  id: 0,
  name: '加载中…',
  subtitle: '',
  price: 0,
  originPrice: 0,
  discount: '',
  cover: mainCover,
  gallery: [mainCover, thumb1, thumb2, thumb3, thumb4],
  tags: [],
  detailHtml: '',
  detailImages: [] as string[],
  services: [
    { icon: 'verified', label: '正品溯源' },
    { icon: 'local_shipping', label: '极速发货' },
    { icon: 'autorenew', label: '7天无理由退换' },
  ],
  flavors: [{ id: 'default', label: '默认', active: true }],
  specs: [{ id: 'default', label: '默认规格', active: true }],
  skus: [] as Array<{ id: number; specValues: Record<string, string>; price: number; stock: number }>,
  // 下面两个字段真实分支里有、骨架态必须同形，否则接口未返回时
  // 模板里的 product.specTable.length 会抛 "Cannot read properties of undefined"，
  // 规格参数 Tab 整块白屏。
  saleCount: 0,
  specTable: [] as Array<{ key: string; value: string }>,
}

const apiProduct = ref<ApiProduct | null>(null)
const skus = ref<Array<{ id: number; specValues: Record<string, string>; price: number; stock: number }>>([])
const notFound = ref(false)

async function loadProduct(id: number | string) {
  notFound.value = false
  try {
    const data: any = await productApi.detail(id)
    apiProduct.value = data
    skus.value = (data.skus || []).map((s: any) => ({
      id: s.id,
      specValues: (() => {
        const raw = s.specValues ?? s.spec_values
        if (!raw) return {}
        return typeof raw === 'string' ? JSON.parse(raw) : raw
      })(),
      price: s.price,
      stock: s.stock,
    }))
    // 多维联动模型：SKU 变化后默认选中第一个"有货"SKU 的规格组合
    // （旧实现用 activeSpec 单一 skuId，已废弃；此处由 resetSpecSelection 接管）
    resetSpecSelection()
    // 相关推荐随详情一并加载（依赖真实商品 id）
    const pid = data.id ?? id
    loadRelated(pid)
    // 评价列表与汇总一并加载
    loadReviews(pid)
    currentProductId.value = pid
    // 埋点：商品详情浏览（pv），供运营漏斗统计真实触达
    trackEvent('pv', {
      page: 'product_detail',
      targetType: 'product',
      targetId: Number(data.id ?? id),
    })
    // 登录态下静默记录浏览足迹（不阻塞详情加载，失败不提示）
    if (userStore.isLoggedIn) {
      productApi.recordFootprint(id).catch(() => {})
      // 静默读取当前商品收藏状态（不阻塞，失败不提示）
      checkCollected(Number(data.id ?? id)).catch(() => {})
    }
  } catch (e: any) {
    // 全局响应拦截器已对 404/已下架(code 30002)等弹过提示（并标记 __handled），
    // 此处避免重复弹窗；仅兜底未处理错误。
    console.error('[ProductDetailView] loadProduct failed:', e)
    if (!e?.__handled) ElMessage.error('商品加载失败')
    apiProduct.value = null
    notFound.value = true
    reviews.value = []
    reviewSummaryData.value = null
    averageRating.value = 0
    totalReviews.value = 0
  }
}

// 相关推荐商品（真实接口 productApi.related）
const relatedLoading = ref(false)
const relatedProducts = ref<
  Array<{
    id: number
    cover: string
    name: string
    price: number
    saleCount: number
    skuId?: number
    badge: string
    badgeType: string
  }>
>([])

async function loadRelated(id: number | string) {
  relatedLoading.value = true
  try {
    const list = await productApi.related(Number(id))
    // 后端 Product 无角标字段，badge 统一留空（UI 有 v-if 兜底不展示）
    relatedProducts.value = (list || []).map((p: any) => ({
      id: p.id,
      cover: p.mainImage,
      name: p.name,
      price: p.salePrice,
      saleCount: p.saleCount ?? 0,
      skuId: p.defaultSkuId,
      badge: '',
      badgeType: '',
    }))
  } catch {
    ElMessage.error('相关推荐加载失败')
    relatedProducts.value = []
  } finally {
    relatedLoading.value = false
  }
}

onMounted(() => loadProduct(route.params.id as string))
watch(() => route.params.id, (v) => v && loadProduct(v as string))

const product = computed(() => {
  const p = apiProduct.value
  if (!p) return fallbackProduct
  const tagList: string[] = (() => {
    if (!p.tags) return []
    try { return typeof p.tags === 'string' ? JSON.parse(p.tags) : p.tags } catch { return [] }
  })()
  const gallery = [p.mainImage, ...((p.subImages && typeof p.subImages === 'string' ? JSON.parse(p.subImages) : p.subImages) || []).filter(Boolean)]

  // 商品详情图（商家发布的详情长图）。MySQL json 列可能以字符串返回，统一解析为 URL 数组。
  let detailImagesArr: string[] = []
  try {
    const diRaw = p.detailImages
    if (diRaw) {
      const diParsed = typeof diRaw === 'string' ? JSON.parse(diRaw) : diRaw
      if (Array.isArray(diParsed)) detailImagesArr = diParsed.filter((x: any) => x && typeof x === 'string')
    }
  } catch { detailImagesArr = [] }

  return {
    id: p.id,
    name: p.name,
    subtitle: '',
    price: p.salePrice,
    originPrice: p.originalPrice ?? p.salePrice,
    discount: p.originalPrice && p.originalPrice > p.salePrice
      ? `${Math.round((1 - p.salePrice / p.originalPrice) * 100)}% OFF`
      : '',
    saleCount: p.saleCount ?? 0,
    cover: p.mainImage,
    gallery,
    tags: tagList,
    specTable: (() => {
      // 把 ApiProduct.specs 解析成 [{key, value}]，强制"商品名称"放第一行。
      // 兼容两种存储形状：
      //   1) 商家发布：[{name:'净含量', values:['1.5kg','3kg']}, ...]（规格组数组）
      //   2) 种子/旧数据：扁平 {品牌:'...', 规格:'...'}（键值对对象）
      const raw = p.specs
      const rows: { key: string; value: string }[] = []
      let parsed: any = null
      if (raw) {
        try { parsed = typeof raw === 'string' ? JSON.parse(raw) : raw } catch { parsed = null }
      }
      if (Array.isArray(parsed)) {
        // 形状 1：规格组数组
        parsed.forEach((g: any) => {
          const name = g?.name
          if (!name || name === '商品名称') return
          let vals = g?.values
          if (typeof vals === 'string') { try { vals = JSON.parse(vals) } catch {} }
          if (!Array.isArray(vals)) vals = vals != null ? [String(vals)] : []
          const v = (vals as any[]).filter(Boolean).join(' / ')
          if (v) rows.push({ key: name, value: v })
        })
      } else if (parsed && typeof parsed === 'object') {
        // 形状 2：扁平键值对
        Object.entries(parsed).forEach(([k, v]) => {
          if (k === '商品名称') return
          if (v == null || v === '') return
          rows.push({ key: k, value: String(v) })
        })
      }
      return rows
    })(),
    detailHtml: p.detail || '',
    detailImages: detailImagesArr,
    services: [
      { icon: 'verified', label: '正品溯源' },
      { icon: 'local_shipping', label: '极速发货' },
      { icon: 'autorenew', label: '7天无理由退换' },
    ],
    skus: skus.value,
  }
})

// ============ 规格选择（多维联动，#3/#4）============
// 旧实现：口味按钮没有 @click、active 写死第一个，选了也不参与 SKU 匹配；
//         activeSpec 初值硬编码 '5kg'，无 SKU 商品会一路 Number('5kg') → NaN。
// 新实现：从所有 SKU 的 specValues 动态抽出全部维度（口味/规格/尺寸…），
//         每个维度独立可选，组合后精确匹配唯一 SKU，匹配不到则禁用下单。

// 维度列表：[{ name: '口味', values: ['鸡肉','牛肉'] }, ...]，按 SKU 中出现的顺序稳定排列
const specDimensions = computed(() => {
  const dims = new Map<string, string[]>()
  for (const s of skus.value) {
    for (const [k, v] of Object.entries(s.specValues || {})) {
      if (!v) continue
      const arr = dims.get(k) ?? []
      if (!arr.includes(v)) arr.push(v)
      dims.set(k, arr)
    }
  }
  return Array.from(dims.entries()).map(([name, values]) => ({ name, values }))
})

// 当前每个维度的选中值
const selectedSpec = ref<Record<string, string>>({})

// 命中当前选择组合的 SKU；维度全部选中且完全匹配才算命中
const currentSku = computed(() => {
  if (!skus.value.length) return null
  const dims = specDimensions.value
  // 无规格维度的商品（默认 SKU）：直接取唯一那条
  if (!dims.length) return skus.value[0]
  if (dims.some((d) => !selectedSpec.value[d.name])) return null
  return (
    skus.value.find((s) =>
      dims.every((d) => (s.specValues || {})[d.name] === selectedSpec.value[d.name]),
    ) ?? null
  )
})

// 某个维度值是否可选：在"其他维度已选值"的约束下，存在有货 SKU
function isSpecValueAvailable(dimName: string, value: string) {
  return skus.value.some((s) => {
    const sv = s.specValues || {}
    if (sv[dimName] !== value) return false
    return specDimensions.value.every((d) => {
      if (d.name === dimName) return true
      const picked = selectedSpec.value[d.name]
      return !picked || sv[d.name] === picked
    })
  })
}

function isSpecValueSoldOut(dimName: string, value: string) {
  const matched = skus.value.filter((s) => (s.specValues || {})[dimName] === value)
  return matched.length > 0 && matched.every((s) => Number(s.stock ?? 0) <= 0)
}

function pickSpec(dimName: string, value: string) {
  if (selectedSpec.value[dimName] === value) return
  selectedSpec.value = { ...selectedSpec.value, [dimName]: value }
  // 换维度后其余维度可能变得不兼容，逐个校正到一个仍然存在的值
  for (const d of specDimensions.value) {
    if (d.name === dimName) continue
    const cur = selectedSpec.value[d.name]
    if (cur && !isSpecValueAvailable(d.name, cur)) {
      const fallback = d.values.find((v) => isSpecValueAvailable(d.name, v))
      if (fallback) selectedSpec.value = { ...selectedSpec.value, [d.name]: fallback }
    }
  }
}

// SKU 变化后重置默认选择：优先选中第一个「有货」SKU 的规格组合
function resetSpecSelection() {
  const dims = specDimensions.value
  if (!dims.length) {
    selectedSpec.value = {}
    return
  }
  const preferred = skus.value.find((s) => Number(s.stock ?? 0) > 0) ?? skus.value[0]
  const next: Record<string, string> = {}
  for (const d of dims) {
    const v = (preferred?.specValues || {})[d.name]
    next[d.name] = v || d.values[0]
  }
  selectedSpec.value = next
}

watch(skus, resetSpecSelection, { deep: true })

// 展示价格：选中 SKU 时用 SKU 价，否则回落 SPU 售价
const displayPrice = computed(() => {
  const sku = currentSku.value
  const v = sku?.price ?? apiProduct.value?.salePrice ?? 0
  return Number(v) || 0
})
const currentStock = computed(() => Number(currentSku.value?.stock ?? 0))
// 整个商品是否已无任何可售 SKU（含"商品没配 SKU"的异常数据）
const allSoldOut = computed(
  () => skus.value.length === 0 || skus.value.every((s) => Number(s.stock ?? 0) <= 0),
)
const canPurchase = computed(() => !!currentSku.value && currentStock.value > 0)
const purchaseHint = computed(() => {
  if (!skus.value.length) return '该商品暂未配置规格，无法购买'
  if (allSoldOut.value) return '该商品已售罄'
  if (!currentSku.value) return '请选择完整规格'
  if (currentStock.value <= 0) return '当前规格已售罄，请换一个规格'
  return ''
})

// ============ 状态 ============
const activeTab = ref<'detail' | 'spec' | 'review'>('detail')
const quantity = ref(1)
const activeThumb = ref(0)
const relatedScrollRef = ref<HTMLElement | null>(null)

// ============ 用户评价（真实接口）============
interface DisplayReview {
  id: number
  user: string
  avatar: string
  rating: number
  date: string
  createdAt: string
  text: string
  images: string[]
  reply: string | null
}

const reviews = ref<DisplayReview[]>([])
const currentProductId = ref<number | string>(0)
const reviewSummaryData = ref<ReviewSummary | null>(null)
const averageRating = ref(0)
const totalReviews = ref(0)
const reviewsLoading = ref(false)

function formatDate(value: string): string {
  if (!value) return ''
  const d = new Date(value.replace(' ', 'T'))
  if (isNaN(d.getTime())) return value
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function mapReview(r: ProductReview): DisplayReview {
  return {
    id: r.id,
    user: r.userNickname,
    avatar: r.userAvatar || thumb1,
    rating: r.rating,
    date: formatDate(r.createdAt),
    createdAt: r.createdAt || '',
    text: r.content,
    images: Array.isArray(r.images) ? r.images : [],
    reply: r.reply || null,
  }
}

async function loadReviews(productId: number | string, size = 10) {
  reviewsLoading.value = true
  try {
    const [listResp, summary] = await Promise.all([
      productApi.reviews(productId, 1, size),
      productApi.reviewSummary(productId),
    ])
    reviews.value = (listResp?.list || []).map(mapReview)
    reviewSummaryData.value = summary ?? null
    averageRating.value = summary?.averageRating ?? 0
    totalReviews.value = summary?.totalReviews ?? 0
  } catch {
    ElMessage.error('评价加载失败')
    reviews.value = []
    reviewSummaryData.value = null
    averageRating.value = 0
    totalReviews.value = 0
  } finally {
    reviewsLoading.value = false
  }
}

// 「查看全部评价」：切到评价 tab 并加载全量评价
function showAllReviews() {
  activeTab.value = 'review'
  if (totalReviews.value > reviews.value.length) {
    loadReviews(currentProductId.value, Math.min(Math.max(totalReviews.value, 10), 200))
  }
}

// ============ 规格参数：图标化 + 分组 ============
function specIcon(key: string): string {
  const k = key || ''
  if (/(品牌|厂商|生产)/.test(k)) return 'badge'
  if (/(净含量|重量|规格|尺寸|容量|克|kg|g)/i.test(k)) return 'scale'
  if (/(产地|国家|地区)/.test(k)) return 'public'
  if (/(成分|配方|原料|营养|口味|风味|香型)/.test(k)) return 'restaurant'
  if (/(适用|适龄|品种|猫咪|狗狗|宠物|对象)/.test(k)) return 'pets'
  if (/(保质期|生产日期|日期)/.test(k)) return 'event'
  if (/(储存|存储|保存|存放)/.test(k)) return 'inventory_2'
  if (/(认证|标准|等级|执行)/.test(k)) return 'verified'
  return 'label'
}

const specRows = computed(() =>
  (product.value.specTable || []).map((r) => ({ ...r, icon: specIcon(r.key) })),
)

// 关键参数优先展示（品牌 / 净含量 / 适用对象 等），最多取 3 项作为顶部快捷条
const corePriorities = [
  /(品牌|厂商|生产)/,
  /(净含量|重量|规格|容量|尺寸|克|kg|g)/i,
  /(适用|适龄|品种|对象)/,
]
const coreSpecs = computed<typeof specRows.value>(() => {
  const chosen: typeof specRows.value = []
  for (const re of corePriorities) {
    const hit = specRows.value.find((r) => re.test(r.key) && !chosen.includes(r))
    if (hit) chosen.push(hit)
    if (chosen.length >= 3) break
  }
  return chosen
})
const detailSpecs = computed(() => specRows.value.filter((r) => !coreSpecs.value.includes(r)))

// ============ 评价：分布 / 筛选 / 排序 ============
const ratingDistribution = computed(() => {
  const counts = (reviewSummaryData.value?.ratingCounts || {}) as Record<string, number>
  const total = totalReviews.value || 0
  return [5, 4, 3, 2, 1].map((star) => {
    const c = Number(counts[String(star)] || 0)
    return { star, count: c, pct: total ? Math.round((c / total) * 100) : 0 }
  })
})

function countOf(star: string): number {
  const c = (reviewSummaryData.value?.ratingCounts || {}) as Record<string, number>
  return Number(c[star] || 0)
}
const goodCount = computed(() => countOf('5') + countOf('4'))
const midCount = computed(() => countOf('3'))
const badCount = computed(() => countOf('2') + countOf('1'))

type RateType = 'good' | 'mid' | 'bad'
function rateTypeOf(rating: number): RateType {
  if (rating >= 4) return 'good'
  if (rating === 3) return 'mid'
  return 'bad'
}

const reviewFilter = ref<'all' | RateType>('all')
const reviewSort = ref<'latest' | 'high' | 'low'>('latest')

const filteredReviews = computed(() => {
  let list = reviews.value.slice()
  if (reviewFilter.value !== 'all') {
    list = list.filter((r) => rateTypeOf(r.rating) === reviewFilter.value)
  }
  if (reviewSort.value === 'high') {
    list.sort((a, b) => b.rating - a.rating || b.createdAt.localeCompare(a.createdAt))
  } else if (reviewSort.value === 'low') {
    list.sort((a, b) => a.rating - b.rating || b.createdAt.localeCompare(a.createdAt))
  } else {
    list.sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  }
  return list
})

// ============ 操作 ============
// 商品收藏状态（详情页入口）
const collected = ref(false)
const favLoading = ref(false)

async function checkCollected(id: number) {
  if (!userStore.isLoggedIn) {
    collected.value = false
    return
  }
  try {
    const list = await userApi.favorites()
    collected.value = list.some((f) => f.targetType === 'product' && Number(f.targetId) === id)
  } catch {
    collected.value = false
  }
}

async function toggleFav() {
  if (!product.value.id) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再收藏')
    return
  }
  if (favLoading.value) return
  favLoading.value = true
  try {
    const res = await userApi.toggleFavorite('product', product.value.id)
    collected.value = !!res.collected
    ElMessage.success(collected.value ? '已加入收藏' : '已取消收藏')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    favLoading.value = false
  }
}

function incQty() {
  // 数量上限跟随当前 SKU 真实库存，避免下单时才被后端库存校验打回
  const max = currentStock.value > 0 ? Math.min(currentStock.value, 99) : 99
  if (quantity.value < max) quantity.value++
  else if (currentStock.value > 0) ElMessage.warning(`该规格库存仅剩 ${currentStock.value} 件`)
}

function decQty() {
  if (quantity.value > 1) quantity.value--
}

// 切换规格后，把数量收敛回新规格的库存范围内
watch(currentStock, (s) => {
  if (s > 0 && quantity.value > s) quantity.value = s
})

async function addToCart() {
  const sku = currentSku.value
  if (!sku || currentStock.value <= 0) {
    ElMessage.warning(purchaseHint.value || '请选择商品规格')
    return
  }
  try {
    await cartStore.add(sku.id, quantity.value, { productId: Number(route.params.id) })
    await cartStore.fetch()
    ElMessage.success(`已加入购物车：${product.value.name} × ${quantity.value}`)
  } catch (e: any) {
    ElMessage.error(e?.message || '加入购物车失败，请重试')
  }
}

function scrollRelated(direction: -1 | 1) {
  const el = relatedScrollRef.value
  if (!el) return
  const cardWidth = 220 // 与 .related-scroll 中 ProductCard 宽度一致
  el.scrollBy({ left: direction * (cardWidth + 16), behavior: 'smooth' })
}

async function buyNow() {
  const sku = currentSku.value
  if (!sku || currentStock.value <= 0) {
    ElMessage.warning(purchaseHint.value || '请选择商品规格')
    return
  }
  try {
    await cartStore.add(sku.id, quantity.value)
    await cartStore.fetch()
    ElMessage.success('已加入购物车，前往结算')
    router.push('/checkout')
  } catch (e: any) {
    ElMessage.error(e?.message || '下单失败，请稍后重试')
  }
}

function switchTab(t: 'detail' | 'spec' | 'review') {
  activeTab.value = t
  // 滚动到 tabs 区域
  setTimeout(() => {
    document.getElementById('product-tabs')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, 50)
}

function goCategory() {
  router.push('/category')
}

// 详情图加载失败时隐藏，避免破图占位
function onImgError(e: Event) {
  ;(e.target as HTMLImageElement).style.display = 'none'
}

</script>

<template>
  <div class="product-detail">
    <main class="page-main">
      <!-- 面包屑 -->
      <nav class="breadcrumb no-vw">
        <a href="javascript:;" @click="router.push('/')">首页</a>
        <span class="sep">/</span>
        <a href="javascript:;" @click="goCategory">商品</a>
        <span class="sep">/</span>
        <span class="current">商品详情</span>
      </nav>

      <!-- 顶部：图 + 信息 -->
      <section class="hero-section">
        <!-- 左侧图片 -->
        <div class="gallery">
          <div class="main-image">
            <img :src="product.gallery[activeThumb]" :alt="product.name" />
            <span v-if="product.tags?.length" class="main-tag">
              {{ product.tags[0] }}
            </span>
          </div>
          <div class="thumb-row">
            <button
              v-for="(img, idx) in product.gallery.slice(0, 4)"
              :key="idx"
              class="thumb"
              :class="{ active: activeThumb === idx }"
              @click="activeThumb = idx"
            >
              <img :src="img" :alt="`${product.name} ${idx + 1}`" />
            </button>
          </div>
        </div>

        <!-- 右侧信息 -->
        <div class="info">
          <div class="badges">
            <span v-for="t in product.tags" :key="t" class="badge">{{ t }}</span>
          </div>

          <h1 class="name">{{ product.name }}</h1>

          <div class="price-card">
            <div class="price-row">
              <span class="price-symbol">¥</span>
              <!-- 跟随选中 SKU 的真实价格，多规格差价不再只显示 SPU 起价 -->
              <span class="price-num">{{ displayPrice.toFixed(2) }}</span>
              <span class="price-origin">¥{{ product.originPrice.toFixed(2) }}</span>
              <span class="discount-tag">{{ product.discount }}</span>
            </div>
            <div class="price-card-footer">
              <div class="service-row">
                <span
                  v-for="s in product.services"
                  :key="s.label"
                  class="service-item"
                >
                  <span class="material-symbols-outlined service-icon">{{ s.icon }}</span>
                  {{ s.label }}
                </span>
              </div>
              <span class="sales-count">已售 {{ product.saleCount }}</span>
            </div>
          </div>

          <!-- 规格选择：按 SKU 里真实存在的维度动态渲染，每个维度都可点、且参与 SKU 匹配（#3） -->
          <div v-for="dim in specDimensions" :key="dim.name" class="picker">
            <p class="picker-label">
              {{ dim.name }}：
              <span class="picker-value">{{ selectedSpec[dim.name] || '请选择' }}</span>
            </p>
            <div class="picker-tags">
              <button
                v-for="v in dim.values"
                :key="v"
                class="tag-btn"
                :class="{
                  active: selectedSpec[dim.name] === v,
                  disabled: !isSpecValueAvailable(dim.name, v) || isSpecValueSoldOut(dim.name, v),
                }"
                :disabled="!isSpecValueAvailable(dim.name, v) || isSpecValueSoldOut(dim.name, v)"
                :title="isSpecValueSoldOut(dim.name, v) ? '该规格已售罄' : ''"
                @click="pickSpec(dim.name, v)"
              >{{ v }}</button>
            </div>
          </div>

          <!-- 无 SKU / 全售罄的兜底提示（原来这里会一路 NaN，用户只能反复看到"请选择规格"）（#4） -->
          <p v-if="purchaseHint" class="stock-warn">
            <span class="material-symbols-outlined">error</span>{{ purchaseHint }}
          </p>
          <p v-else-if="currentStock > 0 && currentStock <= 10" class="stock-low">
            仅剩 {{ currentStock }} 件
          </p>

          <!-- 数量 -->
          <div class="picker">
            <p class="picker-label">数量：</p>
            <div class="qty-row">
              <button class="qty-btn" @click="decQty" :disabled="quantity <= 1">-</button>
              <span class="qty-num">{{ quantity }}</span>
              <button class="qty-btn" @click="incQty" :disabled="!canPurchase">+</button>
            </div>
          </div>

          <!-- 按钮 -->
          <div class="action-row">
            <button
              class="fav-btn"
              :class="{ active: collected, loading: favLoading }"
              :disabled="favLoading"
              @click="toggleFav"
              :title="collected ? '取消收藏' : '收藏'"
            >
              <span class="material-symbols-outlined" :class="{ filled: collected }">favorite</span>
            </button>
            <button class="add-cart" :disabled="!canPurchase" @click="addToCart">
              <span class="material-symbols-outlined">shopping_cart</span>
              {{ allSoldOut ? '已售罄' : '加入购物车' }}
            </button>
            <button class="buy-now" :disabled="!canPurchase" @click="buyNow">
              {{ allSoldOut ? '暂不可购' : '立即购买' }}
            </button>
          </div>
        </div>
      </section>

      <!-- Tabs -->
      <section class="tabs-section" id="product-tabs">
        <div class="tabs">
          <button
            class="tab"
            :class="{ active: activeTab === 'detail' }"
            @click="activeTab = 'detail'"
          >商品详情</button>
          <button
            class="tab"
            :class="{ active: activeTab === 'spec' }"
            @click="activeTab = 'spec'"
          >规格参数</button>
          <button
            class="tab"
            :class="{ active: activeTab === 'review' }"
            @click="activeTab = 'review'"
          >用户评价(<template v-if="totalReviews > 0">{{ totalReviews }}</template><template v-else>暂无</template>)</button>
        </div>
      </section>

      <!-- 商品详情 -->
      <section v-if="activeTab === 'detail'" class="detail-section">
        <div v-if="product.detailHtml" class="rich-detail" v-html="product.detailHtml"></div>
        <template v-else-if="!product.detailImages.length">
          <h2 class="section-title">科学营养，只为那一刻的守护</h2>
          <p class="section-sub">PetMate 甄选宠物营养专家，针对猫咪天性需求，打造每一颗都蕴含能量的黄金口粮。</p>

          <div class="detail-image">
            <img :src="detailHero" alt="商品详情大图 - 天然食材" />
          </div>

          <div class="detail-grid">
            <div class="detail-card blue">
              <h3 class="detail-card-title">亮泽毛发配方</h3>
              <p class="detail-card-desc">富含 Omega-3 / Omega-6 不饱和脂肪酸，有效改善毛发黯淡、增强皮毛屏障、减少掉毛困扰。</p>
            </div>
            <div class="detail-image-tall">
              <img :src="catDetail" alt="猫咪特写" />
            </div>
          </div>

          <!-- 质量承诺 -->
          <div class="promise-section">
            <h3 class="promise-title">我们的质量承诺</h3>
            <div class="promise-grid">
              <div class="promise-item">
                <div class="promise-icon">
                  <span class="material-symbols-outlined">block</span>
                </div>
                <p class="promise-label">0 添加人工色素</p>
              </div>
              <div class="promise-item">
                <div class="promise-icon">
                  <span class="material-symbols-outlined">shield</span>
                </div>
                <p class="promise-label">无谷物麸质</p>
              </div>
              <div class="promise-item">
                <div class="promise-icon">
                  <span class="material-symbols-outlined">science</span>
                </div>
                <p class="promise-label">低敏低敏易消化</p>
              </div>
              <div class="promise-item">
                <div class="promise-icon">
                  <span class="material-symbols-outlined">restaurant</span>
                </div>
                <p class="promise-label">优选宠物肉源</p>
              </div>
            </div>
          </div>
        </template>

        <!-- 商品详情图：商家发布的详情长图，正片呈现（有则渲染，置于文字/占位下方） -->
        <p v-if="product.detailImages.length" class="detail-imgs-cap">商品详情图</p>
        <div v-if="product.detailImages.length" class="detail-images">
          <figure v-for="(img, i) in product.detailImages" :key="i" class="detail-figure">
            <img :src="img" :alt="`${product.name} 详情图 ${i + 1}`" loading="lazy" @error="onImgError" />
          </figure>
        </div>
      </section>

      <!-- 规格参数：卡片式布局 + 图标标签 + 核心信息高亮 + 详细参数分栏网格 -->
      <section v-else-if="activeTab === 'spec'" class="spec-section">
        <div v-if="product.specTable.length" class="spec-card pet-card">
          <div class="spec-card-head">
            <span class="material-symbols-outlined spec-head-icon">tune</span>
            <h3 class="spec-head-title">规格参数</h3>
            <span class="spec-head-count">{{ product.specTable.length }} 项</span>
          </div>

          <div v-if="coreSpecs.length" class="spec-core">
            <div v-for="row in coreSpecs" :key="row.key" class="spec-core-item">
              <span class="material-symbols-outlined spec-core-icon">{{ row.icon }}</span>
              <div class="spec-core-body">
                <p class="spec-core-key">{{ row.key }}</p>
                <p class="spec-core-value">{{ row.value }}</p>
              </div>
            </div>
          </div>

          <div v-if="detailSpecs.length" class="spec-group">
            <p class="spec-group-title">详细参数</p>
            <div class="spec-grid">
              <div v-for="row in detailSpecs" :key="row.key" class="spec-item">
                <span class="material-symbols-outlined spec-item-icon">{{ row.icon }}</span>
                <div class="spec-item-body">
                  <p class="spec-item-key">{{ row.key }}</p>
                  <p class="spec-item-value">{{ row.value }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
        <PetEmptyState
          v-else
          icon="list_alt"
          title="暂无规格参数"
          desc="该商品暂无详细规格参数"
        />
      </section>

      <!-- 用户评价 -->
      <section v-else class="review-section">
        <!-- 评分概览 + 分布统计 -->
        <div class="review-summary">
          <div class="rating-overview">
            <div class="rating-score">
              <span class="score-num">{{ averageRating ? averageRating.toFixed(1) : '0.0' }}</span>
              <div class="score-side">
                <span class="star-line">
                  <span
                    v-for="n in 5"
                    :key="n"
                    class="material-symbols-outlined star"
                    :class="{ filled: n <= Math.round(averageRating) }"
                  >star</span>
                </span>
                <span class="score-meta">基于 {{ totalReviews }} 条真实评价</span>
              </div>
            </div>
            <div class="rating-bars">
              <div
                v-for="bar in ratingDistribution"
                :key="bar.star"
                class="bar-row"
                :class="{ active: bar.count > 0 }"
              >
                <span class="bar-label">{{ bar.star }} 星</span>
                <div class="bar-track">
                  <div class="bar-fill" :style="{ width: bar.pct + '%' }"></div>
                </div>
                <span class="bar-count">{{ bar.count }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 筛选标签 + 排序 -->
        <div class="review-toolbar">
          <div class="filter-tags">
            <button
              class="filter-tag"
              :class="{ active: reviewFilter === 'all' }"
              @click="reviewFilter = 'all'"
            >全部 <i>{{ totalReviews }}</i></button>
            <button
              class="filter-tag good"
              :class="{ active: reviewFilter === 'good' }"
              @click="reviewFilter = 'good'"
            >好评 <i>{{ goodCount }}</i></button>
            <button
              class="filter-tag mid"
              :class="{ active: reviewFilter === 'mid' }"
              @click="reviewFilter = 'mid'"
            >中评 <i>{{ midCount }}</i></button>
            <button
              class="filter-tag bad"
              :class="{ active: reviewFilter === 'bad' }"
              @click="reviewFilter = 'bad'"
            >差评 <i>{{ badCount }}</i></button>
          </div>
          <div class="sort-box">
            <span class="material-symbols-outlined sort-icon">sort</span>
            <select v-model="reviewSort" class="sort-select">
              <option value="latest">最新优先</option>
              <option value="high">评分高 → 低</option>
              <option value="low">评分低 → 高</option>
            </select>
          </div>
        </div>

        <div class="review-list">
          <p v-if="reviewsLoading" class="review-empty">评价加载中…</p>
          <PetEmptyState
            v-else-if="!filteredReviews.length"
            icon="rate_review"
            title="暂无评价"
            desc="快来抢沙发吧～"
          />
          <template v-else>
            <article v-for="r in filteredReviews" :key="r.id" class="review-card">
              <div class="review-head">
                <img :src="r.avatar" :alt="r.user" class="reviewer-avatar" />
                <div class="reviewer-info">
                  <p class="reviewer-name">{{ r.user }}</p>
                  <span class="reviewer-stars">
                    <span
                      v-for="n in 5"
                      :key="n"
                      class="material-symbols-outlined"
                      :class="{ filled: n <= r.rating }"
                    >star</span>
                  </span>
                </div>
                <span class="review-rate-tag" :class="rateTypeOf(r.rating)">
                  {{ rateTypeOf(r.rating) === 'good' ? '好评' : rateTypeOf(r.rating) === 'mid' ? '中评' : '差评' }}
                </span>
                <span class="review-date">{{ r.date }}</span>
              </div>
              <div class="review-quote">
                <span class="material-symbols-outlined quote-mark">format_quote</span>
                <p class="review-text">{{ r.text }}</p>
              </div>
              <div v-if="r.images.length" class="review-images">
                <img v-for="(img, i) in r.images" :key="i" :src="img" :alt="`评价图 ${i + 1}`" loading="lazy" />
              </div>
              <div v-if="r.reply" class="review-reply">
                <span class="reply-badge">商家回复</span>
                <p class="reply-text">{{ r.reply }}</p>
              </div>
            </article>
          </template>
        </div>

        <button v-if="totalReviews > reviews.length" class="more-reviews" @click="showAllReviews">查看全部 {{ totalReviews }} 条评价 ⌄</button>
      </section>

      <!-- 相关推荐 -->
      <PageSection class="related-section" title="相关好物推荐" icon="recommend">
        <template #actions>
          <div class="related-nav">
            <button class="nav-btn" aria-label="上一个" @click="scrollRelated(-1)">
              <span class="material-symbols-outlined">chevron_left</span>
            </button>
            <button class="nav-btn" aria-label="下一个" @click="scrollRelated(1)">
              <span class="material-symbols-outlined">chevron_right</span>
            </button>
          </div>
        </template>
        <div class="related-scroll" ref="relatedScrollRef">
          <p v-if="relatedLoading" class="related-empty">加载中…</p>
          <p v-else-if="relatedProducts.length === 0" class="related-empty">暂无相关推荐</p>
          <ProductCard
            v-for="r in relatedProducts"
            :key="r.id"
            :id="r.id"
            :cover="r.cover"
            :name="r.name"
            :price="r.price"
            :sale-count="r.saleCount"
            :sku-id="r.skuId"
            :corner-tag="r.badge"
            :corner-tag-type="r.badgeType === 'blue' ? 'blue' : 'red'"
          />
        </div>
      </PageSection>
    </main>
    <div v-if="notFound" class="empty-hint">
      <span class="material-symbols-outlined">search_off</span>
      <p>商品已下架或不存在</p>
      <button class="btn-primary" @click="router.push('/')">去首页逛逛</button>
    </div>
  </div>
</template>

<style lang="scss" scoped>
@use '@/styles/variables.scss' as *;

.product-detail {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: $spacing-2xl;
}

.empty-hint {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 96px 24px;
  text-align: center;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 48px;
    opacity: 0.5;
  }
  p {
    font-size: $font-size-body-lg;
  }
  .btn-primary {
    margin-top: 8px;
  }
}

// ====== Page main ======
.page-main {
  max-width: $width-content-max;
  margin: 0 auto;
  padding: $spacing-md $width-gutter-mobile;

  @include desktop {
    padding: $spacing-md $width-margin-desktop;
  }
}

// ====== 面包屑 ======
.breadcrumb {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin-bottom: $spacing-md;

  .sep {
    color: $color-text-tertiary;
  }

  .current {
    color: $color-on-surface;
    font-weight: $font-weight-semibold;
  }

  // 第 3 级：商品所属分类（如「狗粮」「猫粮」），作为视觉标识不跳转
  .crumb-cat {
    color: $color-text-secondary;
    cursor: default;
  }

  a {
    color: $color-text-secondary;
    text-decoration: none;
    cursor: pointer;

    &:hover {
      color: $color-brand;
    }
  }
}

// ====== Hero section ======
.hero-section {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-lg;
  box-shadow: $shadow-card;
  margin-bottom: $spacing-lg;

  @include desktop {
    grid-template-columns: 480px 1fr;
    gap: $spacing-2xl;
    padding: $spacing-xl;
  }
}

// ====== Gallery ======
.gallery {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.main-image {
  position: relative;
  aspect-ratio: 1;
  background: $color-surface-container-low;
  border-radius: $radius-lg;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.main-tag {
  position: absolute;
  top: $spacing-md;
  left: $spacing-md;
  padding: 4px 12px;
  background: linear-gradient(90deg, $color-emergency 0%, #ff7a32 100%);
  color: #fff;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  border-radius: $radius-full;
  box-shadow: $shadow-md;
}

.thumb-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-sm;
}

.thumb {
  aspect-ratio: 1;
  padding: 0;
  background: $color-surface-container-low;
  border: 2px solid transparent;
  border-radius: $radius-default;
  overflow: hidden;
  cursor: pointer;
  transition: border-color $transition-fast;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  &:hover {
    border-color: $color-outline;
  }

  &.active {
    border-color: $color-brand;
  }
}

// ====== Info ======
.info {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.badge {
  padding: 4px 10px;
  background: rgba($color-emergency, 0.1);
  color: $color-emergency;
  font-size: 11px;
  font-weight: $font-weight-bold;
  border-radius: $radius-sm;
}

.name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  line-height: $line-height-tight;
  margin: 0;

  @media (max-width: 768px) {
    font-size: $font-size-headline-md;
  }
}

.price-card {
  background: linear-gradient(135deg, rgba($color-emergency, 0.04) 0%, rgba($color-brand, 0.04) 100%);
  border-radius: $radius-default;
  padding: $spacing-md $spacing-lg;
}

.price-row {
  display: flex;
  align-items: baseline;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
  flex-wrap: wrap;
}

.price-symbol {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-emergency;
}

.price-num {
  font-family: $font-family-headline;
  font-size: 32px;
  font-weight: $font-weight-extrabold;
  color: $color-emergency;
  line-height: 1;
}

.price-origin {
  font-size: $font-size-body-md;
  color: $color-text-tertiary;
  text-decoration: line-through;
}

.discount-tag {
  margin-left: auto;
  padding: 4px 10px;
  background: $color-emergency;
  color: #fff;
  font-size: 11px;
  font-weight: $font-weight-bold;
  border-radius: $radius-sm;
}

.price-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: $spacing-md;
  padding-top: $spacing-sm;
  border-top: 1px dashed $color-outline-variant;
}

.service-row {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-md;
}

.sales-count {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  white-space: nowrap;
}

.service-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

.service-icon {
  font-size: 16px !important;
  color: $color-brand;
}

// ====== Picker ======
.picker {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.picker-label {
  font-size: $font-size-label-md;
  color: $color-on-surface;
  margin: 0;
  font-weight: $font-weight-semibold;
}

.picker-value {
  color: $color-text-secondary;
  font-weight: $font-weight-regular;
}

.picker-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.tag-btn {
  padding: 8px 18px;
  background: $color-surface-container-low;
  border: 1.5px solid transparent;
  border-radius: $radius-default;
  color: $color-on-surface;
  font-size: $font-size-label-md;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-outline;
  }

  &.active {
    background: rgba($color-brand, 0.1);
    border-color: $color-brand;
    color: $color-brand;
    font-weight: $font-weight-semibold;
  }
}

// ====== Stock hint ======
.stock-warn,
.stock-low {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin: 0;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;

  .material-symbols-outlined {
    font-size: 16px;
  }
}

.stock-warn {
  color: $color-emergency;

  .material-symbols-outlined {
    color: $color-emergency;
  }
}

.stock-low {
  color: $color-warning;
  font-weight: $font-weight-medium;

  .material-symbols-outlined {
    color: $color-warning;
  }
}

.qty-row {
  display: inline-flex;
  align-items: center;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-default;
  overflow: hidden;
  width: fit-content;
}

.qty-btn {
  width: 36px;
  height: 36px;
  background: $color-surface-container-low;
  border: none;
  color: $color-on-surface;
  font-size: $font-size-body-md;
  font-family: inherit;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover:not(:disabled) {
    background: $color-surface-container;
  }

  &:disabled {
    opacity: 0.4;
    cursor: not-allowed;
  }
}

.qty-num {
  min-width: 60px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  border-left: 1px solid $color-outline-variant;
  border-right: 1px solid $color-outline-variant;
}

// ====== Action row ======
.action-row {
  display: flex;
  gap: $spacing-md;
  margin-top: $spacing-sm;
}

.fav-btn {
  flex: 0 0 52px;
  width: 52px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-default;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: background $transition-fast, border-color $transition-fast, color $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 22px;
    font-variation-settings: 'FILL' 0;
    transition: transform $transition-fast;
  }

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
    background: rgba($color-brand, 0.05);
  }

  &.active {
    border-color: $color-brand;
    color: $color-brand;
    background: rgba($color-brand, 0.08);

    .material-symbols-outlined {
      font-variation-settings: 'FILL' 1;
    }
  }

  &:active:not(:disabled) .material-symbols-outlined {
    transform: scale(1.25);
  }

  &.loading .material-symbols-outlined {
    animation: fav-spin 0.8s linear infinite;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

@keyframes fav-spin {
  to {
    transform: rotate(360deg);
  }
}

.add-cart,
.buy-now {
  flex: 1;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  font-size: $font-size-body-md;
  font-weight: $font-weight-bold;
  font-family: inherit;
  border-radius: $radius-default;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.add-cart {
  background: #fff;
  color: $color-brand;
  border: 1.5px solid $color-brand;

  &:hover {
    background: rgba($color-brand, 0.05);
  }
}

.buy-now {
  background: linear-gradient(90deg, $color-brand 0%, #ff9461 100%);
  color: #fff;
  border: none;
  box-shadow: 0 6px 16px rgba($color-brand, 0.3);

  &:hover {
    transform: translateY(-2px);
  }
}

// ====== Tabs section ======
.tabs-section {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  margin-bottom: $spacing-md;
  position: sticky;
  top: 64px;
  z-index: 10;
}

.tabs {
  display: flex;
  gap: $spacing-md;
  padding: 0 $spacing-lg;
}

.tab {
  padding: $spacing-md $spacing-xs;
  background: transparent;
  border: none;
  border-bottom: 3px solid transparent;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  font-family: inherit;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    color: $color-brand;
  }

  &.active {
    color: $color-brand;
    border-bottom-color: $color-brand;
  }
}

// ====== Detail section ======
.detail-section {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-2xl $spacing-lg;
  box-shadow: $shadow-card;
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;

  .rich-detail {
    color: #1f2024;
    line-height: 1.7;
    font-size: $font-size-body-md;

    h2 {
      font-size: $font-size-headline-md;
      font-weight: 700;
      margin: $spacing-lg 0 $spacing-md;
      color: #1f2024;
      border-left: 4px solid $color-brand;
      padding-left: $spacing-sm;
    }
    h3 {
      font-size: $font-size-body-lg;
      font-weight: 600;
      margin: $spacing-md 0 $spacing-sm;
      color: #444;
    }
    p { margin: $spacing-sm 0; }
    ul, ol { padding-left: $spacing-xl; margin: $spacing-sm 0; li { margin: 4px 0; } }
    table { border-collapse: collapse; margin: $spacing-md 0; width: 100%; max-width: 560px;
      th, td { padding: 8px 12px; border: 1px solid #d4d4d4; text-align: left; }
      th { background: #f7f7f7; font-weight: 600; }
    }
  }
  align-items: center;
  margin-bottom: $spacing-lg;
}

.section-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
  text-align: center;
}

.section-sub {
  font-size: $font-size-body-md;
  color: $color-text-secondary;
  text-align: center;
  max-width: 600px;
  margin: 0;
}

.detail-image {
  width: 100%;
  max-width: 720px;
  border-radius: $radius-lg;
  overflow: hidden;

  img {
    width: 100%;
    height: auto;
    display: block;
  }
}

// ====== 商品详情图（商家发布的详情长图，竖排大图） ======
.detail-imgs-cap {
  width: 100%;
  max-width: 720px;
  margin: $spacing-sm auto 0;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface-variant;
  text-align: center;
}

.detail-images {
  display: flex;
  flex-direction: column;
  gap: 0; /* 拼接长图：图与图之间不留缝隙 */
  width: 100%;
  max-width: 720px;
  margin: $spacing-sm auto 0;
}

.detail-figure {
  margin: 0;
  line-height: 0; /* 消除 inline 图片底部基线留白 */
  background: transparent;
  box-shadow: none;
  border-radius: 0;

  img {
    width: 100%;
    height: auto;
    display: block;
  }
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  width: 100%;
  max-width: 720px;

  @include tablet {
    grid-template-columns: 1fr 1.2fr;
  }
}

.detail-card {
  padding: $spacing-lg;
  background: linear-gradient(135deg, $color-secondary 0%, $color-secondary-light 100%);
  border-radius: $radius-lg;
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.detail-card-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  margin: 0 0 $spacing-sm;
}

.detail-card-desc {
  font-size: $font-size-label-md;
  margin: 0;
  line-height: $line-height-normal;
  opacity: 0.95;
}

.detail-image-tall {
  border-radius: $radius-lg;
  overflow: hidden;
  aspect-ratio: 4 / 3;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

// ====== Promise ======
.promise-section {
  width: 100%;
  max-width: 720px;
  background: $color-surface-container-low;
  border-radius: $radius-lg;
  padding: $spacing-xl;
}

.promise-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0 0 $spacing-lg;
  text-align: center;
}

.promise-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-lg;

  @include tablet {
    grid-template-columns: repeat(4, 1fr);
  }
}

.promise-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  text-align: center;
}

.promise-icon {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand-container;
  border-radius: 50%;

  .material-symbols-outlined {
    font-size: 24px;
    color: $color-brand;
    font-variation-settings: 'FILL' 1;
  }
}

.promise-label {
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  margin: 0;
}

// ====== Spec section ======
.spec-section {
  // 外层仅作布局容器，不渲染成卡片；唯一视觉卡片由内部的 .spec-card(pet-card) 承担，避免两层卡片感
  margin-bottom: $spacing-lg;
}

.spec-card {
  padding: $spacing-xl;
}

.spec-card-head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;
}

.spec-head-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: $radius-md;
  background: $color-brand-container;
  color: $color-brand;
  font-size: 20px;
  font-variation-settings: 'FILL' 1;
}

.spec-head-title {
  flex: 1;
  margin: 0;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
}

.spec-head-count {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: $color-brand;
  background: $color-brand-container;
  padding: 2px 10px;
  border-radius: $radius-full;
}

.spec-core {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
}

.spec-core-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: linear-gradient(135deg, rgba(255, 122, 50, 0.1), rgba(255, 122, 50, 0.04));
  border: 1px solid rgba(255, 122, 50, 0.22);
  border-radius: $radius-lg;
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    transform: translateY(-3px);
    border-color: $color-brand;
    box-shadow: 0 6px 16px rgba(255, 122, 50, 0.18);
  }
}

.spec-core-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 42px;
  height: 42px;
  border-radius: $radius-md;
  background: $color-surface-container-lowest;
  color: $color-brand;
  font-size: 22px;
  font-variation-settings: 'FILL' 1;
}

.spec-core-body {
  min-width: 0;
}

.spec-core-key {
  margin: 0 0 2px;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

.spec-core-value {
  margin: 0;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  @include text-ellipsis(1);
}

.spec-group-title {
  margin: 0 0 $spacing-md;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  color: $color-text-secondary;
  position: relative;
  padding-left: 12px;

  &::before {
    content: '';
    position: absolute;
    left: 0;
    top: 50%;
    transform: translateY(-50%);
    width: 4px;
    height: 14px;
    border-radius: $radius-full;
    background: $color-brand;
  }
}

.spec-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: $spacing-sm;
}

.spec-item {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  padding: $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid transparent;
  border-radius: $radius-md;
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    transform: translateY(-2px);
    border-color: $color-brand-container;
    box-shadow: $shadow-sm;
  }
}

.spec-item-icon {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin-top: 2px;
  border-radius: $radius-sm;
  background: $color-surface-container-lowest;
  color: $color-brand;
  font-size: 18px;
  font-variation-settings: 'FILL' 1;
}

.spec-item-body {
  min-width: 0;
}

.spec-item-key {
  margin: 0 0 2px;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

.spec-item-value {
  margin: 0;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  word-break: break-word;
}

// ====== Review section ======
.review-section {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-xl;
  box-shadow: $shadow-card;
  margin-bottom: $spacing-lg;
}

.review-summary {
  padding-bottom: $spacing-lg;
  border-bottom: 1px solid $color-outline-variant;
  margin-bottom: $spacing-lg;
}

.rating-overview {
  display: flex;
  align-items: center;
  gap: $spacing-2xl;
  flex-wrap: wrap;
}

.rating-score {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  flex-shrink: 0;
}

.score-num {
  font-family: $font-family-headline;
  font-size: 56px;
  line-height: 1;
  font-weight: $font-weight-extrabold;
  color: $color-brand;
}

.score-side {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.score-meta {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

// 共享星级样式（概览 + 评价卡片）
.star-line,
.reviewer-stars {
  display: inline-flex;
  gap: 2px;
}

.star,
.reviewer-stars .material-symbols-outlined {
  font-size: 18px !important;
  color: $color-outline-variant;
  font-variation-settings: 'FILL' 0;
}

.star.filled,
.reviewer-stars .material-symbols-outlined.filled {
  color: $color-warning;
  font-variation-settings: 'FILL' 1;
}

.rating-bars {
  flex: 1;
  min-width: 240px;
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.bar-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;

  &.active {
    color: $color-on-surface;
  }
}

.bar-label {
  width: 36px;
  flex-shrink: 0;
  text-align: right;
}

.bar-track {
  flex: 1;
  height: 8px;
  border-radius: $radius-full;
  background: $color-surface-container;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: $radius-full;
  background: linear-gradient(90deg, $color-warning, $color-brand);
  transition: width $transition-slow;
}

.bar-count {
  width: 32px;
  flex-shrink: 0;
  text-align: left;
  font-variant-numeric: tabular-nums;
}

// 筛选 + 排序
.review-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
  margin-bottom: $spacing-lg;
}

.filter-tags {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
}

.filter-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 14px;
  background: $color-surface-container-low;
  color: $color-text-secondary;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  border: 1px solid transparent;
  border-radius: $radius-full;
  cursor: pointer;
  transition: all $transition-fast;

  i {
    font-style: normal;
    font-size: $font-size-label-sm;
    opacity: 0.7;
  }

  &:hover {
    border-color: $color-brand-container;
    color: $color-brand;
  }

  &.active {
    background: $color-brand;
    color: $color-on-brand;
    border-color: $color-brand;

    i { opacity: 0.9; }
  }

  &.good.active { background: $color-success; border-color: $color-success; }
  &.mid.active { background: $color-warning; border-color: $color-warning; color: $color-on-surface; }
  &.bad.active { background: $color-emergency; border-color: $color-emergency; }
}

.sort-box {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  background: $color-surface-container-low;
  border-radius: $radius-full;
}

.sort-icon {
  font-size: 18px;
  color: $color-text-tertiary;
}

.sort-select {
  border: none;
  background: transparent;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  cursor: pointer;
  outline: none;
  padding-right: 4px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.review-card {
  padding: $spacing-md;
  background: $color-surface-container-low;
  border: 1px solid transparent;
  border-radius: $radius-lg;
  transition: transform $transition-fast, box-shadow $transition-fast, border-color $transition-fast;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-md;
    border-color: $color-brand-container;
  }
}

.review-head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.reviewer-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
  border: 2px solid $color-surface-container-lowest;
  box-shadow: $shadow-sm;
}

.reviewer-info {
  flex: 1;
  min-width: 0;
}

.reviewer-name {
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: $color-on-surface;
  margin: 0 0 2px;
}

.review-rate-tag {
  flex-shrink: 0;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  padding: 2px 10px;
  border-radius: $radius-full;

  &.good { color: $color-success; background: rgba($color-success, 0.12); }
  &.mid { color: $color-warning; background: rgba($color-warning, 0.14); }
  &.bad { color: $color-emergency; background: rgba($color-emergency, 0.12); }
}

.review-date {
  flex-shrink: 0;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
}

.review-quote {
  position: relative;
  display: flex;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  border-left: 3px solid $color-brand-container;
}

.quote-mark {
  flex-shrink: 0;
  font-size: 22px;
  line-height: 1;
  color: $color-brand-container;
  font-variation-settings: 'FILL' 1;
}

.review-text {
  margin: 0;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  line-height: $line-height-relaxed;
  word-break: break-word;
}

.review-images {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-sm;

  img {
    width: 80px;
    height: 80px;
    object-fit: cover;
    border-radius: $radius-sm;
    cursor: pointer;
    transition: transform $transition-fast;

    &:hover {
      transform: scale(1.05);
    }
  }
}

.review-reply {
  display: flex;
  align-items: flex-start;
  gap: $spacing-xs;
  margin-top: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: $color-sky-blue-light;
  border-radius: $radius-md;
}

.reply-badge {
  flex-shrink: 0;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: $color-secondary;
  background: $color-secondary-container;
  padding: 2px 8px;
  border-radius: $radius-full;
}

.reply-text {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  line-height: $line-height-normal;
}

.review-empty {
  padding: $spacing-md;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
  text-align: center;
  margin: 0;
}

.more-reviews {
  display: block;
  margin: $spacing-md auto 0;
  padding: 8px 24px;
  background: transparent;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  border: none;
  cursor: pointer;

  &:hover {
    text-decoration: underline;
  }
}

// ====== Related ======
.related-section {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  padding: $spacing-xl;
  box-shadow: $shadow-card;
}

.related-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.related-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
}

.related-nav {
  display: flex;
  gap: 4px;
}

.nav-btn {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-surface-container-low;
  border: none;
  border-radius: 50%;
  color: $color-text-secondary;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 20px;
  }

  &:hover {
    background: $color-brand;
    color: #fff;
  }
}

.related-scroll {
  display: flex;
  gap: $spacing-md;
  overflow-x: auto;
  padding: 4px 4px $spacing-md;
  scroll-snap-type: x mandatory;
  scroll-padding: 0 4px;
  @include scrollbar-hide;
}

.related-empty {
  padding: $spacing-xl;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
  margin: 0;
}

// 横向滚动容器内的商品卡片固定宽度（复用 ProductCard 组件）
.related-scroll > .product-card {
  flex: 0 0 220px;
  width: 220px;
  scroll-snap-align: start;
}

// ============ 手机端响应式重排 ============
// 尺寸类 px(间距/头像/评价图/标签 padding)被 px-to-viewport 转 vw，手机(375)下缩成约 1/5，
// 导致评价头像 8px、评价图 16px、卡片内边距拥挤。用 rem(不缩放)重写关键尺寸。
@include mobile {
  .product-detail {
    padding-bottom: 2rem;
  }
  .page-main {
    padding: 0.75rem 0.85rem;
  }
  .breadcrumb {
    gap: 0.25rem;
    margin-bottom: 0.85rem;
  }
  .hero-section {
    gap: 1rem;
    padding: 1rem;
    border-radius: 1rem;
    margin-bottom: 1rem;
  }
  .gallery {
    gap: 0.5rem;
  }
  .thumb-row {
    gap: 0.5rem;
  }
  .info {
    gap: 1rem;
  }
  .main-tag {
    padding: 0.25rem 0.65rem;
  }
  .badges {
    gap: 0.35rem;
  }
  .badge {
    padding: 0.25rem 0.5rem;
  }
  .price-card {
    padding: 0.85rem 1rem;
    border-radius: 0.85rem;
  }
  .price-row {
    gap: 0.5rem;
    margin-bottom: 0.5rem;
  }
  .review-card {
    padding: 0.85rem;
  }
  .review-head {
    gap: 0.5rem;
    margin-bottom: 0.5rem;
  }
  .reviewer-avatar {
    width: 2.5rem;
    height: 2.5rem;
  }
  .review-images {
    gap: 0.5rem;

    img {
      width: 4.5rem;
      height: 4.5rem;
    }
  }
  .spec-section {
    padding: 1rem;
  }
  .spec-core {
    grid-template-columns: 1fr;
  }
  .spec-grid {
    grid-template-columns: 1fr;
  }
  .rating-overview {
    gap: 1rem;
  }
  .score-num {
    font-size: 44px;
  }
  .review-toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 0.75rem;
  }
  .filter-tags {
    width: 100%;
  }
  .filter-tag {
    flex: 1 1 auto;
    justify-content: center;
  }
  .sort-box {
    align-self: flex-end;
  }
}
</style>
