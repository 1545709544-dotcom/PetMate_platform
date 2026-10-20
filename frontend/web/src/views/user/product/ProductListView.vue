<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { productApi, type Product } from '@/api/modules/product'
import { userApi } from '@/api/modules/user'
import { metaApi, type GiftPackCoupon, type CommunityActivity } from '@/api/modules/meta'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { usePointsStore } from '@/stores/points'
import { useProductCategories } from '@/composables/useProductCategories'
import { trackEvent } from '@/api/track'
import PageSection from '@/components/common/PageSection.vue'
import ProductCard from '@/components/common/ProductCard.vue'

const router = useRouter()
const route = useRoute()
const cartStore = useCartStore()
const userStore = useUserStore()
const pointsStore = usePointsStore()
const { topTabs, ensureTree } = useProductCategories()
const activeCat = ref('all')

// 顶部搜索模块：回车或点击箭头 → 跳转 /search 结果页
const searchKeyword = ref('')
function onSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  router.push({ path: '/search', query: { keyword: kw } })
}

const products = ref<Array<{
  id: number
  cover: string
  name: string
  tags: string[]
  price: number
  /** 后端 t_product_review 真实条数，不再用销量冒充 */
  reviewCount: number
  saleCount: number
  defaultSkuId?: number
}>>([])

// 排序：销量优先（点击切换，本地排序，不改后端）
const sortBySales = ref(false)
const sortedProducts = computed(() => {
  if (!sortBySales.value) return products.value
  return [...products.value].sort((a, b) => (b.saleCount ?? 0) - (a.saleCount ?? 0))
})
function toggleSalesSort() {
  sortBySales.value = !sortBySales.value
}

const loading = ref(false)

async function loadProducts() {
  const cat = topTabs.value.find((c) => c.key === activeCat.value)
  if (!cat) return
  loading.value = true
  try {
    // 拉取全部在售商品,再按当前品类聚合的二级类目集合过滤(覆盖该品类下所有二级,修复漏显)
    const resp: any = await productApi.listByCategory(undefined, 1, 200)
    let list: Product[] = resp?.list ?? []
    if (cat.ids && cat.ids.length) {
      list = list.filter((p: Product) => cat.ids!.includes(p.categoryId))
    }
    products.value = list.map((p: Product) => ({
      id: p.id,
      cover: p.mainImage,
      name: p.name,
      tags: parseTags(p.tags),
      price: p.salePrice,
      // 用后端真实评价数，不能再拿销量冒充（#2）；无评价时展示销量或"新上架"
      reviewCount: p.reviewCount ?? 0,
      saleCount: p.saleCount ?? 0,
      defaultSkuId: p.defaultSkuId,
    }))
  } catch (e) {
    ElMessage.error('商品加载失败')
    products.value = []
  } finally {
    loading.value = false
  }
}

// 评价/销量文案：有评价显示真实条数，没评价但有销量显示销量，全新商品显示"新上架"
function reviewLabel(p: { reviewCount?: number; saleCount?: number }): string {
  if ((p.reviewCount ?? 0) > 0) return `${p.reviewCount} 条评价`
  if ((p.saleCount ?? 0) > 0) return `已售 ${p.saleCount}`
  return '新上架'
}

// Banner 活动位：取自真实接口 /community/activities（爱心捐助榜 / 宠物摄影大赛等）
const bannerActivity = ref<CommunityActivity | null>(null)
async function loadBanner() {
  try {
    const list = await metaApi.communityActivities()
    bannerActivity.value = list && list.length ? list[0] : null
  } catch {
    bannerActivity.value = null
  }
}

// 角标：用真实销量驱动 —— 销量最高的商品标记为 HOT（不再硬编码 id）
const maxSale = computed(() => Math.max(0, ...products.value.map((p) => p.saleCount || 0)))
function cornerOf(p: { saleCount?: number }): { tag: string; type: string } {
  if (maxSale.value > 0 && (p.saleCount || 0) >= maxSale.value) return { tag: 'HOT', type: 'red' }
  return { tag: '', type: '' }
}

// 从商品名末尾提取容量/规格片段（如 473ml、5kg、200g），用作卡片规格行
function parseTags(raw: unknown): string[] {
  if (!raw) return []
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : (raw as string[])
    return Array.isArray(arr) ? arr.slice(0, 3) : []
  } catch { return [] }
}

watch(activeCat, loadProducts)
onMounted(async () => {
  await loadGiftPack()
  // 先确保分类树加载完成,顶部分类 tab 才能正确渲染与过滤
  try {
    await ensureTree()
  } catch {
    /* 分类加载失败不阻塞商品列表 */
  }
  // 首页分类卡片区跳转会带 ?cat=一级分类id，匹配到对应 tab
  const catQuery = route.query.cat
  if (catQuery && topTabs.value.some((t) => t.key === String(catQuery))) {
    activeCat.value = String(catQuery)
  }
  loadProducts()
  loadBanner()
  checkGiftClaimed()
  checkSignedToday()
  // 埋点：商品列表浏览（pv）
  trackEvent('pv', { page: 'product_list' })
})

// 登录态就绪后(可能是异步从本地恢复)再核对一次礼包领取状态,
// 避免刷新页面时 onMounted 早于 token 恢复、checkGiftClaimed 提前 return 而卡片误显示
watch(
  () => userStore.isLoggedIn,
  (logged) => {
    if (logged) {
      checkGiftClaimed()
      checkSignedToday()
    }
  },
)

// Banner「立即抢购」：加购后前往结算
async function buyNow(productId?: number) {
  const target = products.value.find(p => p.id === productId) ?? products.value[0]
  if (!target) {
    ElMessage.warning('暂无可购买的商品')
    return
  }
  if (!target.defaultSkuId) {
    ElMessage.warning('该商品暂无可选规格')
    return
  }
  try {
    await cartStore.add(target.defaultSkuId, 1)
    await cartStore.fetch()
    ElMessage.success('已加入购物车，前往结算')
    router.push('/checkout')
  } catch {
    ElMessage.error('下单失败，请稍后重试')
  }
}

// 侧栏会员卡：未开通 → 开通页；已开通 → 会员中心
function goMember() {
  router.push(userStore.isVip ? '/user' : '/vip/open')
}

// ============ 新宠大礼包（来自 metaApi.giftPack()）============
// 后端 /coupon/claim 已保证「每张券每人限领一次」,所以整包天然每人只能领一次
const giftPackCoupons = ref<GiftPackCoupon[]>([])
// 已领取 → 整张卡片消失
const giftClaimed = ref(false)
const claiming = ref(false)

async function loadGiftPack() {
  try {
    const list = await metaApi.giftPack()
    giftPackCoupons.value = list || []
  } catch (e) {
    console.warn('[ProductList] giftPack 加载失败', e)
    giftPackCoupons.value = []
  }
}

// 进入页面时,若当前登录用户已领过礼包(代表券已 claimed),则直接隐藏卡片
async function checkGiftClaimed() {
  if (!userStore.isLoggedIn || giftPackCoupons.value.length === 0) return
  try {
    const list = await userApi.coupons()
    const rep = list.find((c) => c.id === giftPackCoupons.value[0].id)
    giftClaimed.value = rep?.status === 'claimed'
  } catch {
    // 忽略:默认显示卡片,用户仍可点击领取
  }
}

async function claimGift() {
  if (giftClaimed.value || claiming.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再领取礼包')
    router.push('/login')
    return
  }
  claiming.value = true
  try {
    // 逐张领取:单张已领过/失败忽略,继续领其余,保证整包尽可能完整到账
    for (const c of giftPackCoupons.value) {
      try {
        await userApi.claimCoupon(c.id)
      } catch {
        /* 已领取或异常:忽略,继续 */
      }
    }
  } catch {
    /* 整包异常:忽略,下面以服务端状态为准 */
  } finally {
    claiming.value = false
  }

  // 以服务端真实领取状态为准(不乐观置位)
  await checkGiftClaimed()
  if (giftClaimed.value) {
    ElMessage.success('恭喜!新宠大礼包已领取,3 张优惠券已存入卡包')
    // 领取成功后自动刷新网页:刷新后 onMounted 会重新读取服务端「已领取」状态并隐藏模块
    setTimeout(() => window.location.reload(), 900)
  } else {
    ElMessage.error('领取失败,请稍后重试')
  }
}

// ============ 签到领鱼干 ============
const signing = ref(false)
const signedToday = ref(false)
const signReward = 5 // 与后端 t_task_def(signin) 对齐

// 进入页面时,若今日已签到则直接置为已签状态(不发奖励)
async function checkSignedToday() {
  if (!userStore.isLoggedIn) return
  try {
    const list = await userApi.taskList()
    signedToday.value = !!list.find((t) => t.code === 'signin' && t.completed)
  } catch {
    // 忽略
  }
}

async function signIn() {
  if (signedToday.value || signing.value) return
  if (!userStore.isLoggedIn) {
    ElMessage.warning('请先登录后再签到')
    router.push('/login')
    return
  }
  signing.value = true
  try {
    const res = await userApi.completeTask('signin')
    signedToday.value = true
    // 同步全局鱼干余额
    pointsStore.setBalance(res.balance)
    if (res.alreadyDone) {
      ElMessage.info('今天已经签到过啦~')
    } else {
      ElMessage.success(`签到成功，获得 ${res.granted} 鱼干 🐟`)
    }
  } catch {
    ElMessage.error('签到失败，请稍后重试')
  } finally {
    signing.value = false
  }
}
</script>

<template>
  <div class="category-view">
    <div class="category-container">
      <div class="layout">
        <!-- 侧栏分类 -->
        <aside class="side">
          <!-- 搜索模块（位于商品分类上方） -->
          <div class="search-module">
            <div class="search-box">
              <span class="material-symbols-outlined search-ico">search</span>
              <input
                v-model="searchKeyword"
                class="search-input"
                type="text"
                placeholder="搜索商品、品牌、分类…"
                @keyup.enter="onSearch"
              />
              <button class="search-go" type="button" aria-label="搜索" @click="onSearch">
                <span class="material-symbols-outlined">arrow_forward</span>
              </button>
            </div>
          </div>
          <div class="side-card">
            <h3 class="side-title">商品分类</h3>
            <nav class="cat-nav">
              <button
                v-for="c in topTabs"
                :key="c.key"
                class="cat-item"
                :class="{ active: activeCat === c.key }"
                @click="activeCat = c.key"
              >
                <span class="material-symbols-outlined">{{ c.icon }}</span>
                <span class="cat-label">{{ c.label }}</span>
              </button>
            </nav>
              <div
                class="member-card"
                :class="{ 'is-vip': userStore.isVip }"
                role="button"
                tabindex="0"
                @click="goMember"
                @keydown.enter="goMember"
                @keydown.space.prevent="goMember"
              >
                <div class="member-body">
                  <template v-if="userStore.isVip">
                    <p class="member-tag">
                      <span class="material-symbols-outlined member-crown">workspace_premium</span>
                      尊享会员
                    </p>
                    <p class="member-headline">V{{ userStore.vipLevel }} · 8.5折</p>
                    <button class="member-btn" type="button" @click.stop="goMember">会员中心</button>
                  </template>
                  <template v-else>
                    <p class="member-tag">成为会员享</p>
                    <p class="member-headline">8.5折优惠</p>
                    <button class="member-btn" type="button" @click.stop="goMember">立即开启</button>
                  </template>
                </div>
                <span class="material-symbols-outlined member-bg">{{ userStore.isVip ? 'workspace_premium' : 'loyalty' }}</span>
              </div>
          </div>
        </aside>

        <!-- 主区 -->
        <div class="main">
          <!-- Banner + 右侧两个卡 -->
          <section class="banner-row">
            <div class="banner-main" v-if="bannerActivity" :style="bannerActivity.cover ? { backgroundImage: 'linear-gradient(rgba(0,0,0,.28),rgba(0,0,0,.28)), url(' + bannerActivity.cover + ')' } : {}">
              <div class="banner-content">
                <div class="banner-badge">
                  <span class="material-symbols-outlined">campaign</span>
                  活动推荐
                </div>
                <h2 class="banner-title">{{ bannerActivity.title }}</h2>
                <p class="banner-desc">{{ bannerActivity.desc }}</p>
                <div class="banner-foot">
                  <button class="banner-btn" @click="router.push(bannerActivity.to)">立即查看</button>
                </div>
              </div>
              <div class="banner-glow"></div>
              <div class="banner-circle" v-if="!bannerActivity.cover">
                <span class="material-symbols-outlined">pets</span>
              </div>
            </div>
            <div class="banner-main banner-fallback" v-else>
              <div class="banner-content">
                <div class="banner-badge">
                  <span class="material-symbols-outlined">campaign</span>
                  活动推荐
                </div>
                <h2 class="banner-title">宠友社区活动</h2>
                <p class="banner-desc">参与爱心捐助、宠物摄影大赛等精彩活动</p>
                <div class="banner-foot">
                  <button class="banner-btn" @click="router.push('/community')">立即查看</button>
                </div>
              </div>
              <div class="banner-glow"></div>
              <div class="banner-circle">
                <span class="material-symbols-outlined">pets</span>
              </div>
            </div>

            <div class="banner-side">
              <div class="mini-card signin" :class="{ done: signedToday }">
                <div class="mini-card-left">
                  <div class="mini-card-head">
                    <span class="mini-card-title">签到领鱼干</span>
                    <span class="material-symbols-outlined">calendar_month</span>
                  </div>
                  <p class="mini-card-desc">每日签到得 {{ signReward }} 鱼干 🐟</p>
                </div>
                <button
                  class="mini-card-btn"
                  type="button"
                  :disabled="signedToday || signing"
                  @click="signIn"
                >
                  <span v-if="signing" class="gift-spinner" aria-hidden="true"></span>
                  <span>{{ signedToday ? '今日已签到' : signing ? '签到中…' : '立即签到' }}</span>
                </button>
              </div>
              <div
                v-if="!giftClaimed"
                class="mini-card gift"
                role="button"
                tabindex="0"
                :aria-disabled="claiming"
                @click="claimGift"
                @keydown.enter="claimGift"
                @keydown.space.prevent="claimGift"
              >
                <div class="mini-card-left">
                  <div class="mini-card-head">
                    <span class="mini-card-title">新宠大礼包</span>
                    <span class="material-symbols-outlined">card_giftcard</span>
                  </div>
                  <p class="mini-card-desc">新用户专享 3 张券 · 满100减10 · 9.5折 · 满200减30</p>
                </div>
                <button
                  class="mini-card-btn gift-btn"
                  type="button"
                  :disabled="claiming"
                  @click.stop="claimGift"
                >
                  <span v-if="claiming" class="gift-spinner" aria-hidden="true"></span>
                  <span>{{ claiming ? '领取中…' : '立即领取' }}</span>
                </button>
              </div>
            </div>
          </section>

          <!-- 严选精品 -->
          <PageSection title="严选精品" icon="verified">
            <template #actions>
              <button class="sort-btn" :class="{ active: sortBySales }" @click="toggleSalesSort">
                销量优先 <span class="material-symbols-outlined" :class="{ open: sortBySales }">expand_more</span>
              </button>
            </template>
            <div class="product-grid">
              <ProductCard
                v-for="p in sortedProducts"
                :key="p.id"
                :id="p.id"
                :cover="p.cover"
                :name="p.name"
                :price="p.price"
                :sale-count="p.saleCount"
                :sku-id="p.defaultSkuId"
                :corner-tag="cornerOf(p).tag"
                :corner-tag-type="cornerOf(p).type"
              />
            </div>

            <div class="more-row">
              <button class="more-btn" @click="router.push('/category/browse')">查看更多精品商品</button>
            </div>
          </PageSection>
        </div>
      </div>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.category-view {
  min-height: 100vh;
  background: $color-background;
}

.category-container {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-2xl;
}

.layout {
  display: flex;
  flex-direction: column;
  gap: $width-gutter-desktop;

  @include desktop {
    flex-direction: row;
  }
}

.side {
  width: 100%;
  flex-shrink: 0;

  @include desktop {
    width: 256px;
    // 整条侧栏(搜索 + 商品分类 + 会员卡)粘性固定,不随右侧主区滚动
    position: sticky;
    top: 96px;
    align-self: flex-start;
  }
}

// ============ 搜索模块（位于商品分类上方） ============
.search-module {
  margin-bottom: $spacing-md;
}

.search-box {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 0 $spacing-md;
  height: 48px;
  background: #ffffff;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-full;
  box-shadow: $shadow-sm;
  transition: border-color $transition-fast, box-shadow $transition-fast;

  &:focus-within {
    border-color: $color-brand;
    box-shadow: 0 6px 16px rgba(255, 122, 50, 0.18);
  }
}

.search-ico {
  font-size: 22px;
  color: $color-on-surface-variant;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: $font-size-body-md;
  font-family: inherit;
  color: $color-on-surface;

  &::placeholder {
    color: $color-on-surface-variant;
  }
}

.search-go {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: $radius-full;
  background: $color-brand;
  color: #ffffff;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 20px;
  }

  &:hover {
    background: $color-brand-dark;
    transform: scale(1.06);
  }

  &:active {
    transform: scale(0.95);
  }
}

.side-card {
  padding: $spacing-lg;
  background: $color-surface-container-low;
  border-radius: $radius-xl;
}

.side-title {
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface-variant;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  margin-bottom: $spacing-lg;
}

.cat-nav {
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
}

.cat-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  border-radius: $radius-lg;
  color: $color-on-surface;
  font-size: $font-size-body-md;
  cursor: pointer;
  transition: background $transition-fast;

  .material-symbols-outlined {
    font-size: 22px;
  }

  &:hover {
    background: $color-surface-container-high;
  }

  &.active {
    background: $color-brand;
    color: #ffffff;
    font-weight: 700;
    box-shadow: 0 6px 16px rgba(255, 122, 50, 0.25);

    .material-symbols-outlined {
      font-variation-settings: 'FILL' 1;
    }
  }
}

.member-card {
  position: relative;
  margin-top: $spacing-2xl;
  padding: $spacing-md;
  background: linear-gradient(135deg, #fff5ec 0%, #ffe2cf 100%);
  border: 1px solid rgba(255, 122, 50, 0.25);
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s ease, border-color 0.25s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 24px rgba(255, 122, 50, 0.18);
    border-color: rgba(255, 122, 50, 0.5);
  }

  &:focus-visible {
    outline: 2px solid $color-brand;
    outline-offset: 2px;
  }
}

.member-body {
  position: relative;
  z-index: 10;
}

.member-tag {
  font-size: $font-size-label-sm;
  color: #6b4427;
}

.member-headline {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: #3a2415;
  margin: 4px 0 $spacing-sm;
}

.member-btn {
  padding: $spacing-xs $spacing-md;
  background: rgba(255, 122, 50, 0.08);
  color: $color-brand;
  border: 1px solid rgba(255, 122, 50, 0.45);
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;
  cursor: pointer;
  transition: background $transition-fast, border-color $transition-fast, transform 0.15s ease;

  &:hover {
    background: rgba(255, 122, 50, 0.16);
    border-color: rgba(255, 122, 50, 0.65);
  }

  &:active {
    transform: scale(0.96);
  }
}

.member-bg {
  position: absolute;
  right: -8px;
  bottom: -8px;
  font-size: 80px;
  color: rgba(255, 122, 50, 0.18);
  transform: rotate(12deg);
}

// 已开通会员：金质高亮态（与会员中心风格呼应）
.member-card.is-vip {
  background: linear-gradient(135deg, #fff3d6 0%, #ffe39e 100%);
  border-color: rgba(214, 158, 46, 0.45);

  .member-tag {
    color: #8a5a00;
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  .member-crown {
    font-size: 16px;
    color: #c8901f;
  }

  .member-headline {
    color: #6b4400;
  }

  .member-btn {
    background: rgba(214, 158, 46, 0.12);
    color: #b9791a;
    border-color: rgba(214, 158, 46, 0.55);

    &:hover {
      background: rgba(214, 158, 46, 0.2);
      border-color: rgba(214, 158, 46, 0.75);
    }
  }

  .member-bg {
    color: rgba(214, 158, 46, 0.22);
  }
}

// ============ Banner Row ============
.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
}

.banner-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;

  @include tablet {
    grid-template-columns: 2fr 1fr;
  }
}

.banner-main {
  position: relative;
  min-height: 220px;
  padding: $spacing-xl;
  background: linear-gradient(135deg, $color-brand 0%, $color-brand-dark 100%);
  border-radius: $radius-xl;
  color: #ffffff;
  box-shadow: 0 12px 32px rgba(255, 122, 50, 0.3);
  overflow: hidden;
}

.banner-content {
  position: relative;
  z-index: 10;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 100%;
  min-height: 200px;
}

.banner-badge {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  align-self: flex-start;
  padding: 4px $spacing-sm;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(8px);
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  margin-bottom: $spacing-md;

  .material-symbols-outlined {
    font-size: 18px;
    animation: pulse 1.5s infinite;
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.banner-title {
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-weight: 800;
  margin-bottom: $spacing-xs;
}

.banner-desc {
  font-size: $font-size-body-lg;
  color: $color-brand-container;
}

.banner-foot {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $spacing-md;
  margin-top: $spacing-md;
}

.banner-price {
  display: flex;
  flex-direction: column;

  .banner-price-tag {
    font-size: $font-size-label-sm;
    opacity: 0.8;
    margin-bottom: 4px;
  }

  .banner-price-val {
    font-family: $font-family-headline;
    font-size: $font-size-headline-lg;
    font-weight: 700;
  }
}

.banner-btn {
  padding: $spacing-sm $spacing-xl;
  background: #ffffff;
  color: $color-brand;
  border-radius: $radius-lg;
  font-weight: 700;
  cursor: pointer;
  box-shadow: $shadow-lg;
  transition: transform $transition-fast;

  &:hover {
    transform: scale(1.05);
  }

  &:active {
    transform: scale(0.95);
  }
}

.banner-glow {
  position: absolute;
  right: -48px;
  bottom: -48px;
  width: 320px;
  height: 320px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: $radius-full;
  filter: blur(64px);
}

.banner-circle {
  position: absolute;
  right: 32px;
  top: 50%;
  transform: translateY(-50%) rotate(12deg);
  width: 192px;
  height: 192px;
  background: rgba(255, 255, 255, 0.2);
  backdrop-filter: blur(4px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: $radius-full;
  display: none;
  align-items: center;
  justify-content: center;

  @include desktop {
    display: flex;
  }

  .material-symbols-outlined {
    font-size: 96px;
    opacity: 0.4;
  }
}

.banner-side {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.mini-card {
  flex: 1;
  padding: $spacing-lg;
  border-radius: $radius-xl;
  color: #ffffff;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  box-shadow: $shadow-md;
  min-height: 100px;
}

.mini-card-left {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.mini-card.signin {
  background: $color-secondary-light;
}

.mini-card.gift {
  background: $color-surface-container-low;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
  cursor: pointer;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s ease, border-color 0.25s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 24px rgba(255, 122, 50, 0.16);
    border-color: rgba(255, 122, 50, 0.5);
  }

  &:focus-visible {
    outline: 2px solid $color-brand;
    outline-offset: 2px;
  }
}

.gift-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: $color-brand;
  color: #ffffff;
  border: none;
  transition: background $transition-fast, transform 0.15s ease, box-shadow $transition-fast;

  &:hover:not(:disabled) {
    background: $color-brand-dark;
    box-shadow: 0 8px 18px rgba(255, 122, 50, 0.32);
    transform: translateY(-1px);
  }

  &:active:not(:disabled) {
    transform: scale(0.96);
  }

  &:disabled {
    opacity: 0.8;
    cursor: not-allowed;
  }

  .gift-spinner {
    width: 14px;
    height: 14px;
    border: 2px solid rgba(255, 255, 255, 0.4);
    border-top-color: #fff;
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }
}

.mini-card-head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;

  .mini-card-title {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 700;
  }

  .material-symbols-outlined {
    opacity: 0.5;
  }
}

.mini-card-desc {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin: 0;
}

.mini-card-btn {
  flex-shrink: 0;
  padding: $spacing-xs $spacing-lg;
  background: #ffffff;
  color: $color-secondary;
  border-radius: $radius-full;
  font-weight: 700;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast, transform 0.15s ease, box-shadow $transition-fast;

  &:hover {
    background: $color-secondary;
    color: #ffffff;
    box-shadow: 0 8px 18px rgba(0, 96, 172, 0.28);
    transform: translateY(-1px);
  }

  &:active {
    transform: scale(0.96);
  }
}

// ============ 严选精品 ============
.featured-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.featured-title {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;

  .bar {
    width: 4px;
    height: 20px;
    background: $color-brand;
    border-radius: $radius-full;
  }
}

.sort-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  transition: color 0.18s ease, background-color 0.18s ease;

  .material-symbols-outlined {
    font-size: 18px;
    transition: transform 0.22s cubic-bezier(0.16, 1, 0.3, 1);
  }

  &.active {
    color: $color-brand;
    background-color: rgba(255, 122, 50, 0.1);
    font-weight: 600;

    .material-symbols-outlined.open {
      transform: rotate(180deg);
    }
  }
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
  }
}

.product-card {
  cursor: pointer;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: $color-surface;
  border-radius: $radius-xl;
  padding: $spacing-sm;
  box-shadow: $shadow-sm;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s ease;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 12px 28px rgba(0, 0, 0, 0.08);

    .prod-cover img {
      transform: scale(1.04);
    }
  }
}

.prod-cover {
  position: relative;
  aspect-ratio: 1;
  border-radius: $radius-lg;
  overflow: hidden;
  background: $color-surface-container;
  margin-bottom: $spacing-sm;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 0.4s ease;
  }
}

.prod-tag {
  position: absolute;
  top: $spacing-sm;
  left: $spacing-sm;
  padding: 3px 6px;
  border-radius: $radius-sm;
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  line-height: 1;

  &.red {
    background: $color-emergency;
  }

  &.blue {
    background: $color-secondary;
  }
}

.prod-cart {
  position: absolute;
  bottom: $spacing-sm;
  right: $spacing-sm;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand;
  color: #ffffff;
  border: none;
  border-radius: $radius-full;
  box-shadow: $shadow-md;
  cursor: pointer;
  transition: transform $transition-fast, background $transition-fast, opacity $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    transform: scale(1.1);
    background: $color-brand-dark;
  }

  &:disabled {
    background: $color-outline-variant;
    cursor: not-allowed;
    transform: none;
    opacity: 0.7;
  }

  &.loading {
    background: $color-brand-dark;
    pointer-events: none;
  }

  .spinning {
    animation: spin 0.8s linear infinite;
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.prod-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  flex: 1;
}

.prod-name {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  line-height: 1.45;
  @include text-ellipsis(2);
  // 固定 2 行高度，保证卡片底部对齐
  min-height: calc(#{$font-size-body-md} * 1.45 * 2);
}

.prod-spec {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1.2;
  min-height: calc(#{$font-size-label-md} * 1.2);
}

.prod-services {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  min-height: 18px;
}

.service-tag {
  padding: 2px 6px;
  border-radius: $radius-sm;
  font-size: 10px;
  color: $color-on-surface-variant;
  background: $color-surface-container-high;
  line-height: 1;
}

.prod-price-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 4px;
}

.prod-price {
  display: flex;
  align-items: flex-start;
  font-family: $font-family-headline;
  color: $color-brand;

  .yen {
    font-size: $font-size-label-md;
    font-weight: 700;
    margin-top: 2px;
    margin-right: 1px;
  }

  .amount {
    font-size: $font-size-headline-md;
    font-weight: 700;
    line-height: 1;
  }
}

.prod-sold {
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1;
}

.more-row {
  display: flex;
  justify-content: center;
  margin-top: $spacing-lg;
}

.more-btn {
  padding: $spacing-md $spacing-3xl;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  font-size: $font-size-label-md;
  cursor: pointer;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }
}

// ============ 手机端响应式重排 ============
// 关键尺寸(高/宽/padding/gap)原本是 px，被 px-to-viewport 转成 vw，在手机(375)被缩成约 1/5，
// 导致搜索框塌陷、购物车按钮点不到、间距拥挤。此处用 rem(不被 vw 缩放)重写，保证手机正常。
@include mobile {
  .category-container {
    padding-top: 1rem;
    padding-bottom: 2rem;
  }
  .layout {
    gap: 1rem;
  }
  .search-module {
    margin-bottom: 0.85rem;
  }
  .search-box {
    height: 2.75rem;
  }
  .search-go {
    width: 2rem;
    height: 2rem;
  }
  .side-card {
    padding: 1rem;
  }
  .side-title {
    margin-bottom: 0.85rem;
  }
  .cat-item {
    padding: 0.6rem 0.85rem;
  }
  .member-card {
    margin-top: 1.5rem;
    padding: 0.85rem;
  }
  .main {
    gap: 1.25rem;
  }
  .banner-row {
    gap: 0.75rem;
  }
  .banner-main {
    min-height: 9rem;
    padding: 1.1rem;
  }
  .product-grid {
    gap: 0.75rem;
  }
  .product-card {
    padding: 0.6rem;
    border-radius: 1rem;
  }
  .prod-cover {
    margin-bottom: 0.5rem;
    border-radius: 0.75rem;
  }
  .prod-cart {
    width: 2rem;
    height: 2rem;
    right: 0.4rem;
    bottom: 0.4rem;
  }
  .prod-tag {
    top: 0.4rem;
    left: 0.4rem;
    padding: 0.2rem 0.4rem;
  }
  .prod-services {
    gap: 0.25rem;
  }
  .service-tag {
    padding: 0.15rem 0.4rem;
  }
  .prod-body {
    gap: 0.35rem;
  }
  .more-row {
    margin-top: 1rem;
  }
  .more-btn {
    padding: 0.7rem 1.5rem;
  }
}
</style>