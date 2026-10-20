<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import heroGrooming from '@/assets/images/home/hero-grooming.jpg'
import heroConsult from '@/assets/images/home/hero-consult.jpg'
import heroShop from '@/assets/images/home/hero-shop.jpg'
import articleFood from '@/assets/images/home/article-food.jpg'
import postCat from '@/assets/images/home/post-cat.jpg'
import p1Fountain from '@/assets/images/home/p1-fountain.jpg'
import p2Catfood from '@/assets/images/home/p2-catfood.jpg'
import p3Bed from '@/assets/images/home/p3-bed.jpg'
import p4Toys from '@/assets/images/home/p4-toys.jpg'
import { productApi, type Product as ApiProduct } from '@/api/modules/product'
import { serviceApi, type ServiceItem as ApiServiceItem } from '@/api/modules/service'
import { useProductCategories } from '@/composables/useProductCategories'

const router = useRouter()
const { tree, ensureTree } = useProductCategories()

// ============ 路由跳转 ============
function goCategory() {
  router.push('/category/browse')
}
function goProductCategory(catName: string) {
  const l1 = tree.value.find((l) => l.name === catName)
  const id = l1?.id
  if (id) {
    router.push({ path: '/category/browse', query: { cat: String(id) } })
  } else {
    router.push('/category/browse')
  }
}
function goConsult() {
  router.push('/consult')
}
function goKnowledge() {
  router.push('/knowledge')
}
function goCommunity() {
  router.push('/community')
}
function goEntry(to: string) {
  router.push(to)
}
function goHeroCta() {
  router.push(currentSlideData.value.to)
}

// ============ Hero 轮播图 ============
// 文案沿用早期系统：三张 slide 各自独立（图、角标、标题、描述、按钮、跳转）
interface HeroSlide {
  image: string
  alt: string
  badge: string
  title: string
  subtitle: string
  cta: string
  to: string
}
const heroSlides: HeroSlide[] = [
  {
    image: heroGrooming,
    alt: '专业宠物美容师为金毛做护理',
    badge: '夏季特惠',
    title: '夏季宠物美容预约\n全线 8 折优惠中',
    subtitle: '让您的爱宠在炎热夏日清爽变身，专业美容师一对一服务。',
    cta: '立即预约',
    to: '/service',
  },
  {
    image: heroConsult,
    alt: '兽医与宠物主人沟通',
    badge: 'AI 问诊',
    title: '24h 在线 AI 健康顾问\n描述症状秒级响应',
    subtitle: '基于百万真实病例训练，7×24 小时不间断守护爱宠健康。',
    cta: '立即咨询',
    to: '/consult',
  },
  {
    image: heroShop,
    alt: '精选宠物用品与美食',
    badge: '商城优选',
    title: '优选宠物用品\n全场满 99 包邮',
    subtitle: '主粮、零食、玩具、用品一站购齐，品质溯源安心喂养。',
    cta: '去逛商城',
    to: '/category',
  },
]
const currentSlideData = computed(() => heroSlides[currentSlide.value])
const currentSlide = ref(0)
const carouselPaused = ref(false)
let carouselTimer: ReturnType<typeof setInterval> | undefined

function startCarousel() {
  stopCarousel()
  carouselTimer = setInterval(() => {
    if (!carouselPaused.value) {
      currentSlide.value = (currentSlide.value + 1) % heroSlides.length
    }
  }, 5000)
}
function stopCarousel() {
  if (carouselTimer) {
    clearInterval(carouselTimer)
    carouselTimer = undefined
  }
}
function goToSlide(i: number) {
  currentSlide.value = i
  startCarousel()
}
function nextSlide() {
  currentSlide.value = (currentSlide.value + 1) % heroSlides.length
  startCarousel()
}
function prevSlide() {
  currentSlide.value = (currentSlide.value - 1 + heroSlides.length) % heroSlides.length
  startCarousel()
}

// ============ 优质服务（从后端拉取真实服务）============
type ServiceAccent = 'blue' | 'brand' | 'green' | 'rose'
interface HomeServiceCard {
  icon: string
  title: string
  desc: string
  tag?: string
  to: string
  accent: ServiceAccent
  featured?: boolean
}

const ACCENTS: ServiceAccent[] = ['blue', 'brand', 'green', 'rose']

/** 接口异常时的兜底卡（保持四卡布局不塌） */
const FALLBACK_SERVICES: HomeServiceCard[] = [
  {
    icon: 'calendar_month',
    title: '兽医预约',
    desc: '在线预约附近宠物医院，零等待，全面体检一站式。',
    tag: '随约随诊',
    to: '/service',
    accent: 'blue',
  },
  {
    icon: 'home_repair_service',
    title: '上门服务',
    desc: '认证美容师与保姆上门，洗澡美容、寄养喂养都省心。',
    tag: '上门可选',
    to: '/service',
    accent: 'brand',
  },
  {
    icon: 'local_hospital',
    title: '宠物医院',
    desc: '24 小时急诊与专科诊疗，守护毛孩子的关键时刻。',
    tag: '24h 急诊',
    to: '/service',
    accent: 'green',
  },
  {
    icon: 'smart_toy',
    title: 'AI 兽医咨询',
    desc: '描述症状即得专业建议，常见小问题不用急着跑医院。',
    tag: 'AI 秒回',
    to: '/consult',
    accent: 'rose',
  },
]

/** 接口返回的真实服务，按名称补一个卖点标签 */
const SERVICE_TAGS: Record<string, string> = {
  兽医预约: '随约随诊',
  上门服务: '上门可选',
  宠物医院: '24h 急诊',
  AI兽医咨询: 'AI 秒回',
  'AI 兽医咨询': 'AI 秒回',
}

function mapServiceCard(s: ApiServiceItem, index: number): HomeServiceCard {
  return {
    icon: s.icon || 'pets',
    title: s.name,
    desc: s.description || '',
    tag: SERVICE_TAGS[s.name] ?? '',
    to: `/service/${s.id}`,
    accent: ACCENTS[index % ACCENTS.length],
  }
}

const services = ref<HomeServiceCard[]>([])
const hoveredService = ref<number | null>(null)

// ============ 宠爱商城 ============
interface HomeProduct {
  id: number
  name: string
  price: number
  origin?: number
  cover?: string
  tag?: string
  sales?: number
  rating?: number
  discount?: number // 直降百分比（原价 > 现价时计算）
}
const products = ref<HomeProduct[]>([])

interface ShopItem {
  id: number
  name: string
  desc: string
  price: number
  cover: string
}

// ============ 甄选好物分类卡（参考新 UI：深色左文右图）============
interface ShopCategory {
  key: string
  title: string
  catName: string
  desc: string
  cover: string
  to: string
  tag: string
  tint: string
}
const shopCategories: ShopCategory[] = [
  {
    key: 'food',
    title: '宠物食品',
    catName: '宠物食品',
    desc: '严选全球宠粮，科学定制专属营养。',
    cover: p2Catfood,
    to: '/category',
    tag: '热销推荐',
    tint: 'linear-gradient(135deg, #f97316 0%, #fb923c 100%)',
  },
  {
    key: 'supplies',
    title: '宠物用品',
    catName: '宠物用品',
    desc: '极简美学与前沿科技完美融合。',
    cover: p1Fountain,
    to: '/category',
    tag: '新品上市',
    tint: 'linear-gradient(135deg, #06b6d4 0%, #67e8f9 100%)',
  },
  {
    key: 'health',
    title: '医疗保健',
    catName: '医疗保健',
    desc: '顶尖兽医团队研发，医疗级日常守护。',
    cover: heroConsult,
    to: '/category',
    tag: '专业推荐',
    tint: 'linear-gradient(135deg, #10b981 0%, #34d399 100%)',
  },
  {
    key: 'toys',
    title: '玩具服饰',
    catName: '玩具服饰',
    desc: '环保创新材质，让互动充满质感。',
    cover: p4Toys,
    to: '/category',
    tag: '限时特惠',
    tint: 'linear-gradient(135deg, #ec4899 0%, #f472b6 100%)',
  },
]

// ============ 社区新鲜事（首页运营位：写死最新 3 条真实动态）============
interface StoryItem {
  id: number
  title: string
  excerpt: string
  cover: string
  author: string
  avatar: string
  likeCount: number
  commentCount: number
}
const STORY_COVERS = [postCat, articleFood, p3Bed]

/** 首页社区新鲜事写死数据（来源：t_post 最新 3 条，用户 90332 李小） */
const FRESH_POSTS: StoryItem[] = [
  {
    id: 68,
    title: '想看看大家的柯基宝宝',
    excerpt: '麦兜的快乐时光，秋天的周末，在公园的大草坪晒太阳撒欢，这大概是普通狗里最好的生活了吧',
    cover: '/api/v1/static/uploads/post/p90332_1787126265869_0.jpg',
    author: '李小',
    avatar: '/api/v1/static/uploads/avatar/u90332_1787124260672.jpg',
    likeCount: 0,
    commentCount: 0,
  },
  {
    id: 66,
    title: '普通猫的一周穿搭指南',
    excerpt: '周一到周日，帽门最前线、宝藏新品、春日开新小会，给猫猫的一周穿搭做猫猫版',
    cover: '/api/v1/static/uploads/post/p90332_1787125182771_0.jpg',
    author: '李小',
    avatar: '/api/v1/static/uploads/avatar/u90332_1787124260672.jpg',
    likeCount: 0,
    commentCount: 0,
  },
  {
    id: 65,
    title: '浅草寺 pignic 小🐽贝咖啡',
    excerpt: '我也玩到了 Serena、Kate 和 Miranda！软乎乎的小猪贝一直用鼻子拱手心',
    cover: '/api/v1/static/uploads/post/p90332_1787124781023_0.jpg',
    author: '李小',
    avatar: '/api/v1/static/uploads/avatar/u90332_1787124260672.jpg',
    likeCount: 0,
    commentCount: 0,
  },
]

const stories = computed<StoryItem[]>(() => FRESH_POSTS)

function authorInitial(name: string) {
  return (name || 'P').slice(0, 1)
}

// 抽屉式：当前展开项（默认第 1 个）
const activeStory = ref(0)

// ============ 健康咨询特点 ============
const wellnessFeatures = [
  { title: 'AI 即时响应', desc: '秒级回复，深夜也能随时解答养宠疑惑。' },
  { title: '个性化建议', desc: '结合宠物档案、品种与病史给出针对性方案。' },
  { title: '异常智能提示', desc: '识别危险症状自动提醒及时线下就诊。' },
]

// ============ AI 问诊真实案例（首页展示卡）============
interface ConsultCase {
  title: string
  tag: string
  rating: string
  avatarIco: string
  avatarBg: string
  question: string
  answer: string
  result: string
}
const consultCases: ConsultCase[] = [
  {
    title: '布偶猫频繁呕吐',
    tag: '毛球症 · 已解决',
    rating: '4.9',
    avatarIco: 'pets',
    avatarBg: 'linear-gradient(135deg, #ff7a32, #ffb27a)',
    question: '我家布偶这两天吐了三次，都带毛球，精神还行，要紧吗？',
    answer: '典型毛球症。建议每日化毛膏 2cm、增加饮水与梳毛；若 24h 内呕吐＞5 次或精神变差需及时就医。',
    result: '按建议 3 天后未再呕吐',
  },
  {
    title: '金毛皮肤红疹瘙痒',
    tag: '皮炎 · 已建议就医',
    rating: '4.8',
    avatarIco: 'pets',
    avatarBg: 'linear-gradient(135deg, #2f8fff, #7ec8ff)',
    question: '金毛肚子上长了一片红疹，一直挠，换了沐浴露也没好转。',
    answer: '疑似接触性皮炎或真菌。建议改用宠物药浴、戴伊丽莎白圈防抓挠，持续 3 天无改善请线下就诊。',
    result: '药浴 2 次后瘙痒明显减轻',
  },
  {
    title: '幼猫软便带血',
    tag: '肠胃 · 已紧急提示',
    rating: '5.0',
    avatarIco: 'pets',
    avatarBg: 'linear-gradient(135deg, #16c79a, #7ef0d0)',
    question: '2 个月小猫今天软便还带一点血，需要马上去医院吗？',
    answer: '幼猫便血需重视。建议先禁食 4h 后少量喂水并密切观察；若持续或精神萎靡请立即就诊，避免脱水。',
    result: '及时就诊确诊肠炎，已康复',
  },
]

// ============ 常见问题 ============
interface FaqItem {
  q: string
  a: string
}
const faqs: FaqItem[] = [
  {
    q: 'PetMate 提供哪些服务？',
    a: 'PetMate 提供一系列以宠物为中心的服务，包括温和美容、健康咨询、行为训练和宠物寄养，同时还提供营养、领养与 AI 辅助护理建议。',
  },
  {
    q: '我如何预约？',
    a: '在首页或服务页选择需要的服务，点击「预约」并填写宠物信息与到店时间即可。预约成功后会收到站内消息提醒。',
  },
  {
    q: '我的宠物最好的饮食是什么？',
    a: '不同年龄、体型和品种的宠物营养需求差异很大。建议先通过 AI 兽医咨询做一次基础评估，再由营养师给出定制化喂养方案。',
  },
  {
    q: 'AI 兽医是如何工作的？',
    a: 'AI 兽医基于大量真实病例训练，您描述症状后即可获得初步分析与护理建议。它不替代线下诊疗，遇到紧急情况请及时就医。',
  },
]
const openFaq = ref(0)
function toggleFaq(i: number) {
  openFaq.value = openFaq.value === i ? -1 : i
}

// ============ 订阅 ============
const subscribeEmail = ref('')

// ============ 滚动入场动画指令 ============
// 元素进入视口时淡入上浮；reduced-motion 或页面不可见时直接展示，避免内容停在 opacity:0
const vReveal = {
  mounted(el: HTMLElement, binding: { value?: number }) {
    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) return
    if (document.visibilityState !== 'visible') return
    const delay = binding.value ?? 0
    el.style.opacity = '0'
    el.style.transform = 'translateY(26px)'
    el.style.willChange = 'opacity, transform'
    el.style.transition =
      `opacity 640ms cubic-bezier(0.22, 1, 0.36, 1) ${delay}ms, ` +
      `transform 640ms cubic-bezier(0.22, 1, 0.36, 1) ${delay}ms`
    const reveal = () => {
      el.style.opacity = '1'
      el.style.transform = 'translateY(0)'
    }
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            reveal()
            io.unobserve(el)
          }
        })
      },
      { threshold: 0.08, rootMargin: '0px 0px -40px 0px' },
    )
    io.observe(el)
    // 兜底：页面被节流（IDE 内嵌预览、后台标签页）时 IO 可能不触发，超时强制显示
    const inView = () => {
      const r = el.getBoundingClientRect()
      return r.top < (window.innerHeight || 0) && r.bottom > 0
    }
    setTimeout(() => {
      if (inView()) reveal()
    }, 1200)
  },
}

// 相对时间：把后端返回的 LocalDateTime 字符串转成 "x 分钟/小时/天/月前"
function timeAgo(s?: string) {
  if (!s) return '刚刚'
  const d = new Date(s.replace(' ', 'T'))
  if (Number.isNaN(d.getTime())) return s.slice(0, 10)
  const diff = Date.now() - d.getTime()
  const sec = Math.floor(diff / 1000)
  if (sec < 60) return '刚刚'
  const min = Math.floor(sec / 60)
  if (min < 60) return `${min}分钟前`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}小时前`
  const day = Math.floor(hr / 24)
  if (day < 30) return `${day}天前`
  const mon = Math.floor(day / 30)
  if (mon < 12) return `${mon}个月前`
  const yr = Math.floor(mon / 12)
  return `${yr}年前`
}

async function loadHome() {
  try {
    // 首页分类卡片区需要根据分类名称匹配真实 id，提前把分类树也拉好
    const treePromise = ensureTree().catch(() => undefined)
    const [prodResp, svcResp] = await Promise.allSettled([
      productApi.listByCategory(undefined, 1, 10),
      serviceApi.search({ page: 1, size: 4 }),
    ])
    await treePromise
    if (prodResp.status === 'fulfilled') {
      products.value = (prodResp.value.list || []).map((p: ApiProduct) => ({
        id: p.id,
        name: p.name,
        price: p.salePrice,
        origin: p.originalPrice,
        cover: p.mainImage,
        tag: p.tags?.[0],
        sales: p.saleCount ?? 0,
        rating: p.rating,
        discount:
          p.originalPrice && p.originalPrice > p.salePrice
            ? Math.round((1 - p.salePrice / p.originalPrice) * 100)
            : 0,
      }))
    }
    if (svcResp.status === 'fulfilled' && svcResp.value?.list?.length) {
      services.value = svcResp.value.list.map((s: ApiServiceItem, i: number) => mapServiceCard(s, i))
    } else {
      services.value = FALLBACK_SERVICES
    }
  } catch {
    /* 首页降级：保留兜底内容，不阻断渲染 */
    services.value = FALLBACK_SERVICES
  }
}

let safetyTimer: ReturnType<typeof setTimeout> | undefined
onMounted(() => {
  loadHome()
  startCarousel()
  // 安全网：无论动画是否被节流/暂停，2.5s 后强制确保内容可见
  safetyTimer = setTimeout(() => {
    document.querySelectorAll('.home-view [style*="opacity: 0"]').forEach((el) => {
      const e = el as HTMLElement
      e.style.opacity = '1'
      e.style.transform = 'none'
    })
  }, 2500)
})
onUnmounted(() => {
  if (safetyTimer) clearTimeout(safetyTimer)
  stopCarousel()
})
</script>

<template>
  <div class="home-view">
    <!-- ============ 1. Hero ============ -->
    <section class="hero">
      <div class="section-inner hero-inner">
        <div class="hero-text" v-reveal="0">
          <div :key="currentSlide" class="hero-text-group">
            <span class="hero-badge">{{ currentSlideData.badge }}</span>
            <h1 class="hero-title">{{ currentSlideData.title }}</h1>
            <p class="hero-sub">{{ currentSlideData.subtitle }}</p>
            <button class="hero-cta" type="button" @click="goHeroCta">
              {{ currentSlideData.cta }} <span class="material-symbols-outlined">arrow_forward</span>
            </button>
          </div>
        </div>

        <div
          class="hero-media"
          v-reveal="120"
          @mouseenter="carouselPaused = true"
          @mouseleave="carouselPaused = false"
        >
          <span class="blob blob-a"></span>
          <span class="blob blob-b"></span>

          <div class="hero-carousel">
            <div
              v-for="(slide, i) in heroSlides"
              :key="i"
              class="hero-slide"
              :class="{ active: currentSlide === i }"
            >
              <img :src="slide.image" :alt="slide.alt" class="hero-img" />
            </div>
          </div>

          <button
            class="hero-carousel-arrow hero-carousel-arrow--prev"
            type="button"
            aria-label="上一张"
            @click="prevSlide"
          >
            <span class="material-symbols-outlined">chevron_left</span>
          </button>
          <button
            class="hero-carousel-arrow hero-carousel-arrow--next"
            type="button"
            aria-label="下一张"
            @click="nextSlide"
          >
            <span class="material-symbols-outlined">chevron_right</span>
          </button>

          <div class="hero-carousel-dots">
            <button
              v-for="(slide, i) in heroSlides"
              :key="'dot-' + i"
              type="button"
              class="hero-carousel-dot"
              :class="{ active: currentSlide === i }"
              :aria-label="'跳转到第' + (i + 1) + '张'"
              @click="goToSlide(i)"
            ></button>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 2. 优质服务 ============ -->
    <section class="services tint-gray">
      <div class="section-inner">
        <div class="section-head" v-reveal="0">
          <div class="section-head-text">
            <span class="services-eyebrow">SERVICES · 一站式宠护</span>
            <h2 class="section-title">优质服务</h2>
            <p class="section-desc">从预约挂号到上门护理、从急诊医院到 AI 问诊，PetMate 把专业宠护送到家。</p>
          </div>
          <a class="section-link" @click="goEntry('/service')">探索所有服务</a>
        </div>

        <div class="service-grid" :class="{ 'has-hover': hoveredService !== null }">
          <article
            v-for="(s, i) in services"
            :key="s.to + i"
            class="service-card"
            :class="[`accent-${s.accent}`]"
            v-reveal="i * 80"
            @mouseenter="hoveredService = i"
            @mouseleave="hoveredService = null"
            @click="goEntry(s.to)"
          >
            <span v-if="s.tag" class="service-tag">{{ s.tag }}</span>
            <span class="service-icon">
              <span class="material-symbols-outlined">{{ s.icon }}</span>
            </span>
            <h3 class="service-title">{{ s.title }}</h3>
            <p class="service-desc">{{ s.desc }}</p>
            <span class="service-cta">
              了解更多
              <span class="material-symbols-outlined">arrow_forward</span>
            </span>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 3. 甄选好物（分类入口卡） ============ -->
    <section class="shop">
      <div class="section-inner">
        <div class="section-head" v-reveal="0">
          <div class="section-head-text">
            <h2 class="section-title">甄选好物</h2>
            <p class="section-desc">精选全球顶级宠物用品，品质保障</p>
          </div>
          <a class="section-link" @click="goCategory">
            查看全部 <span class="material-symbols-outlined">arrow_forward</span>
          </a>
        </div>

        <div class="shop-grid">
          <article
            v-for="(c, i) in shopCategories"
            :key="c.key"
            class="shop-card"
            v-reveal="i * 70"
          >
            <div class="shop-info">
              <span class="shop-tag">{{ c.tag }}</span>
              <h3 class="shop-name">{{ c.title }}</h3>
              <p class="shop-desc">{{ c.desc }}</p>
              <button class="shop-more" type="button" @click="goProductCategory(c.catName)">
                查看更多 <span class="material-symbols-outlined">arrow_forward</span>
              </button>
            </div>
            <div class="shop-media" :style="{ background: c.tint }">
              <img :src="c.cover" :alt="c.title" loading="lazy" />
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- ============ 4. 健康咨询 ============ -->
    <section class="wellness tint-orange">
      <div class="section-inner wellness-inner">
        <div class="wellness-text" v-reveal="0">
          <span class="wellness-eyebrow">AI · 7×24 小时在线</span>
          <h2 class="section-title">AI 智能问诊</h2>
          <p class="section-desc">随时描述宠物的异常症状，AI 结合它的品种与病史给出专业护理建议，常见小问题不用急着跑医院。</p>
          <h4 class="wellness-sub">为什么选择它</h4>
          <ul class="wellness-list">
            <li v-for="f in wellnessFeatures" :key="f.title" class="wellness-item">
              <span class="dot"></span>
              <div>
                <h5 class="wellness-item-title">{{ f.title }}</h5>
                <p class="wellness-item-desc">{{ f.desc }}</p>
              </div>
            </li>
          </ul>
          <button class="btn-primary" type="button" @click="goConsult">去问诊</button>
        </div>

        <div class="wellness-media" v-reveal="120">
          <span class="wellness-backdrop"></span>
          <div class="case-stack">
            <article
              v-for="(c, i) in consultCases"
              :key="c.title"
              class="case-card"
              :class="`case-${i + 1}`"
            >
              <div class="case-head">
                <span class="case-avatar" :style="{ background: c.avatarBg }">
                  <span class="material-symbols-outlined">{{ c.avatarIco }}</span>
                </span>
                <div class="case-meta">
                  <h6 class="case-title">{{ c.title }}</h6>
                  <span class="case-tag">{{ c.tag }}</span>
                </div>
                <span class="case-stars">
                  <span class="material-symbols-outlined">star</span>{{ c.rating }}
                </span>
              </div>
              <div class="case-chat">
                <div class="bubble user">
                  <span class="bubble-who">问</span>
                  <span class="bubble-text">{{ c.question }}</span>
                </div>
                <div class="bubble ai">
                  <span class="bubble-ai">AI</span>
                  <span class="bubble-text">{{ c.answer }}</span>
                </div>
              </div>
              <div class="case-foot">
                <span class="material-symbols-outlined">verified</span>
                {{ c.result }}
              </div>
            </article>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 5. 社区新鲜事（抽屉式） ============ -->
    <section class="stories">
      <div class="section-inner">
        <div class="section-head center" v-reveal="0">
          <span class="stories-eyebrow">COMMUNITY · 宠友圈</span>
          <h2 class="section-title">社区新鲜事</h2>
          <p class="section-desc">悬停展开抽屉，与数百万宠主分享养宠的快乐与经验。</p>
        </div>

        <div class="story-drawers" v-reveal="120" v-if="stories.length">
          <article
            v-for="(s, i) in stories"
            :key="`${s.id}-${i}`"
            class="story-drawer"
            :class="{ 'is-open': activeStory === i }"
            @mouseenter="activeStory = i"
            @focusin="activeStory = i"
            @click="goCommunity"
            tabindex="0"
          >
            <div class="drawer-bg" :style="{ backgroundImage: `url(${s.cover})` }"></div>
            <div class="drawer-shade"></div>

            <!-- 收起态：编号 + 竖排标题 -->
            <div class="drawer-tab">
              <span class="drawer-no">0{{ i + 1 }}</span>
              <span class="drawer-tab-title">{{ s.title }}</span>
            </div>

            <!-- 展开态：完整内容 -->
            <div class="drawer-panel">
              <span class="drawer-no-lg">0{{ i + 1 }}</span>
              <h3 class="drawer-title">{{ s.title }}</h3>
              <p class="drawer-excerpt">{{ s.excerpt }}</p>
              <div class="drawer-foot">
                <div class="story-author">
                  <img v-if="s.avatar" :src="s.avatar" :alt="s.author" class="story-avatar" />
                  <span v-else class="story-avatar fallback">{{ authorInitial(s.author) }}</span>
                  <span class="story-author-name">{{ s.author }}</span>
                </div>
                <div class="story-metrics">
                  <span class="metric">
                    <span class="material-symbols-outlined">favorite</span>{{ s.likeCount }}
                  </span>
                  <span class="metric">
                    <span class="material-symbols-outlined">chat_bubble</span>{{ s.commentCount }}
                  </span>
                </div>
              </div>
            </div>
          </article>
        </div>
        <p v-else class="empty-hint">社区暂时还没有动态，快来发布第一条吧～</p>

        <div class="section-foot">
          <button class="btn-soft" type="button" @click="goCommunity">浏览更多社区动态</button>
        </div>
      </div>
    </section>

    <!-- ============ 6. 常见问题 ============ -->
    <section class="faq tint-gray">
      <div class="faq-inner">
        <h2 class="section-title center-title" v-reveal="0">常见问题</h2>
        <div class="faq-list">
          <div
            v-for="(f, i) in faqs"
            :key="f.q"
            class="faq-item"
            :class="{ open: openFaq === i }"
            v-reveal="i * 60"
          >
            <button class="faq-head" type="button" :aria-expanded="openFaq === i" @click="toggleFaq(i)">
              <span class="faq-q">{{ f.q }}</span>
              <span class="material-symbols-outlined">{{ openFaq === i ? 'remove' : 'add' }}</span>
            </button>
            <p v-if="openFaq === i" class="faq-a">{{ f.a }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============ 7. Footer ============ -->
    <footer class="site-footer">
      <div class="section-inner">
        <div class="footer-grid">
          <div class="footer-brand">
            <div class="footer-logo">
              <span class="material-symbols-outlined">pets</span>
              <span class="footer-logo-text">PetMate</span>
            </div>
            <p class="footer-about">
              为您的毛茸茸朋友提供完美的护理，致力于打造领先的 AI 宠物医疗与生活服务平台。
            </p>
          </div>

          <div class="footer-col">
            <h4 class="footer-col-title">快速链接</h4>
            <a @click="goCategory">宠爱商城</a>
            <a @click="goConsult">AI 兽医</a>
            <a @click="goKnowledge">宠友百科</a>
          </div>

          <div class="footer-col">
            <h4 class="footer-col-title">服务</h4>
            <a @click="goEntry('/service')">上门服务</a>
            <a @click="goEntry('/service')">健康咨询</a>
            <a @click="goCommunity">社区动态</a>
          </div>

          <div class="footer-col">
            <h4 class="footer-col-title">联系我们</h4>
            <p class="footer-mail">support@petmate.com</p>
          </div>
        </div>

        <div class="footer-bottom">
          <p>© 2026 PetMate.com 保留所有权利。</p>
          <p>ICP: 20241024-1</p>
        </div>
      </div>
    </footer>
  </div>
</template>

<style lang="scss" scoped>
// ============ 基础排版 ============
.home-view {
  width: 100%;
  min-height: 100vh;
  background: $color-surface-container-lowest;
}

.section-inner {
  @include container-max;
}

section {
  width: 100%;
  padding-top: 80px;
  padding-bottom: 80px;
}

.tint-gray {
  background: $color-surface-soft;
}

.tint-orange {
  background: #fff9f5;
}

.shop,
.stories {
  background: $color-surface-container-lowest;
}

.section-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $spacing-lg;
  margin-bottom: $spacing-2xl;

  &.center {
    flex-direction: column;
    align-items: center;
    text-align: center;
  }
}

.section-head-text {
  max-width: 560px;
}

.section-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: $font-weight-extrabold;
  line-height: 1.25;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;

  &.center-title {
    text-align: center;
    margin-bottom: $spacing-2xl;
  }
}

.section-desc {
  font-size: $font-size-body-md;
  line-height: $line-height-normal;
  color: $color-on-surface-variant;
  max-width: 620px;
}

.section-link {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: $color-brand;
  cursor: pointer;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    text-decoration: underline;
  }
}

.section-foot {
  display: flex;
  justify-content: center;
  margin-top: $spacing-2xl;
}

// 通用按钮
.btn-primary {
  padding: $spacing-sm $spacing-xl;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  cursor: pointer;
  box-shadow: 0 8px 20px rgba($color-brand, 0.3);
  transition: background $transition-fast, transform $transition-fast;

  &:hover {
    background: $color-brand-dark;
    transform: translateY(-2px);
  }
}

.btn-ghost {
  padding: $spacing-xs $spacing-lg;
  background: transparent;
  color: $color-on-surface;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast, border-color $transition-fast;

  &:hover {
    background: $color-surface-container-low;
    border-color: $color-brand;
    color: $color-brand;
  }
}

.btn-soft {
  padding: $spacing-xs $spacing-lg;
  background: rgba($color-brand, 0.1);
  color: $color-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: rgba($color-brand, 0.2);
  }
}

// ============ 1. Hero ============
.hero {
  background: $color-surface-container-lowest;
  padding-top: $spacing-2xl;
}

.hero-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-2xl;

  @include desktop {
    flex-direction: row;
  }
}

.hero-text {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
}

.hero-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: $font-weight-extrabold;
  line-height: 1.18;
  letter-spacing: -0.02em;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
  white-space: pre-line;

  @include desktop {
    font-size: 56px;
  }
}

.hero-text-group {
  animation: hero-text-in 600ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes hero-text-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.hero-sub {
  max-width: 440px;
  font-size: $font-size-body-lg;
  line-height: $line-height-relaxed;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-xl;
}

.hero-cta {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-md $spacing-xl;
  background: $color-brand;
  color: $color-on-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  cursor: pointer;
  box-shadow: 0 10px 26px rgba($color-brand, 0.32);
  transition: background $transition-fast, transform $transition-fast, box-shadow $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    background: $color-brand-dark;
    transform: translateY(-2px);
    box-shadow: 0 14px 32px rgba($color-brand, 0.38);
  }

  &:active {
    transform: scale(0.98);
  }
}

.hero-badge {
  display: inline-flex;
  align-self: flex-start;
  padding: 6px $spacing-md;
  border-radius: $radius-full;
  background: $color-brand;
  color: $color-on-brand;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 0.04em;
  margin-bottom: $spacing-md;
}

.hero-media {
  position: relative;
  flex: 1;
  width: 100%;
  min-width: 0;
  height: 500px;

  @include mobile {
    height: 18rem;
  }
}

.blob {
  position: absolute;
  z-index: 0;
  border-radius: $radius-full;
  pointer-events: none;
}

.blob-a {
  top: 40px;
  right: 40px;
  width: 256px;
  height: 256px;
  background: rgba($color-brand, 0.22);
  filter: blur(64px);
}

.blob-b {
  bottom: 40px;
  left: 40px;
  width: 192px;
  height: 192px;
  background: rgba($color-secondary-container, 0.9);
  filter: blur(48px);
}

// Hero 轮播图
.hero-carousel {
  position: relative;
  z-index: 1;
  width: 100%;
  height: 100%;
  border-radius: 40px;
  box-shadow: $shadow-lg;
  overflow: hidden;
}

.hero-slide {
  position: absolute;
  inset: 0;
  opacity: 0;
  transform: scale(1.04);
  transition: opacity 700ms ease, transform 700ms ease;

  &.active {
    opacity: 1;
    transform: scale(1);
    z-index: 1;
  }
}

.hero-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.hero-carousel-arrow {
  position: absolute;
  z-index: 2;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border: none;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.82);
  color: $color-on-surface;
  cursor: pointer;
  opacity: 0;
  transition: opacity $transition-fast, background $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 22px;
  }

  &:hover {
    background: #ffffff;
    transform: translateY(-50%) scale(1.06);
  }

  &--prev {
    left: 16px;
  }

  &--next {
    right: 16px;
  }
}

.hero-media:hover .hero-carousel-arrow {
  opacity: 1;
}

.hero-carousel-dots {
  position: absolute;
  z-index: 2;
  left: 50%;
  bottom: 18px;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.hero-carousel-dot {
  width: 8px;
  height: 8px;
  border: none;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.55);
  cursor: pointer;
  transition: width $transition-fast, background $transition-fast;

  &.active {
    width: 24px;
    background: #ffffff;
  }

  &:hover {
    background: #ffffff;
  }
}

// ============ 2. 优质服务 ============
.services-eyebrow {
  display: inline-block;
  margin-bottom: $spacing-sm;
  font-family: $font-family-headline;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 1.5px;
  color: $color-brand;
  background: rgba($color-brand, 0.1);
  border: 1px solid rgba($color-brand, 0.25);
  border-radius: 999px;
  padding: 4px 14px;
}

.service-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;

  @include tablet {
    grid-template-columns: repeat(2, 1fr);
  }

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
  }
}

.service-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: $spacing-xl $spacing-lg $spacing-lg;
  background: $color-surface-container-lowest;
  border: 1px solid rgba($color-outline-variant, 0.26);
  border-radius: 22px;
  box-shadow: 0 10px 30px rgba(22, 29, 31, 0.04);
  overflow: hidden;
  transition: transform $transition-base, box-shadow $transition-base, background $transition-base, border-color $transition-base, opacity $transition-base, filter $transition-base;
  cursor: pointer;

  --svc: #{$color-secondary};
  --svc-soft: #{rgba($color-secondary, 0.12)};

  &.accent-blue { --svc: #{$color-secondary}; --svc-soft: #{rgba($color-secondary, 0.12)}; }
  &.accent-brand { --svc: #{$color-brand}; --svc-soft: #{rgba($color-brand, 0.12)}; }
  &.accent-green { --svc: #00795c; --svc-soft: rgba(0, 121, 92, 0.12); }
  &.accent-rose { --svc: #{$color-danger}; --svc-soft: #{rgba($color-danger, 0.12)}; }

  &:hover {
    transform: translateY(-6px);
    background: $color-brand;
    border-color: transparent;
    color: $color-on-brand;
    box-shadow: 0 18px 44px rgba($color-brand, 0.3);

    .service-icon {
      background: rgba(255, 255, 255, 0.22);
      color: #ffffff;
    }

    .service-title {
      color: #ffffff;
    }

    .service-desc {
      color: rgba(255, 255, 255, 0.88);
    }

    .service-tag {
      background: rgba(255, 255, 255, 0.22);
      color: #fff;
      border-color: transparent;
    }

    .service-cta {
      color: #fff;
      opacity: 1;

      .material-symbols-outlined {
        transform: translateX(4px);
      }
    }
  }

  // 鼠标进入卡片区后，其他未 hover 的卡片变暗、缩小，让颜色"停留"在当前卡上
  .service-grid.has-hover &:not(:hover) {
    opacity: 0.5;
    transform: scale(0.97);
    filter: grayscale(0.3);
  }
}

.service-tag {
  align-self: flex-start;
  margin-bottom: $spacing-md;
  padding: 4px 11px;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-medium;
  line-height: 1.4;
  border-radius: $radius-full;
  background: var(--svc-soft);
  color: var(--svc);
  border: 1px solid transparent;
  transition: background $transition-base, color $transition-base, border-color $transition-base;
}

.service-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 52px;
  height: 52px;
  margin-bottom: $spacing-md;
  border-radius: 16px;
  background: var(--svc-soft);
  color: var(--svc);
  transition: background $transition-base, color $transition-base;

  .material-symbols-outlined {
    font-size: 26px;
  }
}

.service-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;
}

.service-desc {
  flex: 1;
  font-size: $font-size-label-md;
  line-height: $line-height-normal;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-md;
}

.service-cta {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  font-weight: $font-weight-medium;
  color: var(--svc);
  opacity: 0.9;
  transition: color $transition-base, opacity $transition-base;

  .material-symbols-outlined {
    font-size: 18px;
    transition: transform $transition-base;
  }
}

// ============ 3. 甄选好物（深色左文右图卡） ============
.shop-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;

  @include tablet {
    grid-template-columns: repeat(2, 1fr);
  }

  @include desktop {
    grid-template-columns: repeat(4, 1fr);
    gap: $spacing-lg;
  }
}

.shop-card {
  display: flex;
  flex-direction: row;
  height: 180px;
  overflow: hidden;
  background: linear-gradient(135deg, $color-brand 0%, #ff9a5c 100%);
  border-radius: $radius-lg;
  box-shadow: 0 10px 28px rgba(255, 122, 50, 0.22);
  cursor: pointer;
  transition: transform $transition-base, box-shadow $transition-base;

  &:hover {
    transform: translateY(-4px);
    box-shadow: 0 18px 44px rgba(22, 29, 31, 0.18);

    .shop-media img {
      transform: scale(1.06);
    }
  }

  @include mobile {
    flex-direction: column;
    height: auto;
  }
}

.shop-info {
  display: flex;
  flex: 0 0 55%;
  flex-direction: column;
  justify-content: center;
  padding: $spacing-md $spacing-lg;
  color: #ffffff;

  @include mobile {
    flex: none;
    padding: $spacing-lg;
  }
}

.shop-tag {
  align-self: flex-start;
  padding: 3px 10px;
  margin-bottom: 8px;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: #1a1d26;
  background: #ffffff;
  border-radius: $radius-full;
}

.shop-name {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: #ffffff;
  margin-bottom: 4px;
}

.shop-desc {
  font-size: $font-size-label-md;
  line-height: 1.55;
  color: rgba(255, 255, 255, 0.88);
  margin-bottom: $spacing-md;
  @include text-ellipsis(2);
}

.shop-more {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  align-self: flex-start;
  padding: 0;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  color: #ffffff;
  background: transparent;
  border: none;
  cursor: pointer;
  transition: color $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
    transition: transform $transition-fast;
  }

  &:hover {
    color: rgba(255, 255, 255, 0.82);

    .material-symbols-outlined {
      transform: translateX(3px);
    }
  }
}

.shop-media {
  position: relative;
  flex: 1;
  min-width: 0;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 600ms cubic-bezier(0.16, 1, 0.3, 1);
  }

  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(to right, $color-brand 0%, transparent 35%);
    pointer-events: none;
  }
}

// ============ 4. 健康咨询 ============
.wellness-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-2xl;

  @include desktop {
    flex-direction: row;
    gap: 64px;
  }
}

.wellness-text {
  flex: 1;
  min-width: 0;
  order: 2;

  @include desktop {
    order: 1;
  }
}

.wellness-eyebrow {
  display: inline-block;
  margin-bottom: $spacing-sm;
  font-family: $font-family-headline;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 1.5px;
  color: $color-brand;
  background: rgba($color-brand, 0.1);
  border: 1px solid rgba($color-brand, 0.25);
  border-radius: 999px;
  padding: 4px 14px;
}

.wellness-sub {
  margin-top: $spacing-xl;
  margin-bottom: $spacing-lg;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
}

.wellness-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  margin-bottom: $spacing-xl;
  list-style: none;
  padding: 0;
}

.wellness-item {
  display: flex;
  gap: $spacing-md;

  .dot {
    flex-shrink: 0;
    width: 8px;
    height: 8px;
    margin-top: 8px;
    border-radius: $radius-full;
    background: $color-brand;
  }
}

.wellness-item-title {
  font-size: $font-size-body-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.wellness-item-desc {
  font-size: $font-size-label-md;
  line-height: $line-height-normal;
  color: $color-on-surface-variant;
}

.wellness-media {
  position: relative;
  display: flex;
  flex: 1;
  justify-content: center;
  width: 100%;
  min-width: 0;
  order: 1;

  @include desktop {
    order: 2;
  }
}

.wellness-backdrop {
  position: absolute;
  inset: -20px -10px;
  z-index: 0;
  background: radial-gradient(circle at 30% 20%, rgba($color-brand, 0.18), transparent 60%),
              radial-gradient(circle at 80% 90%, rgba($color-secondary, 0.16), transparent 55%);
  border-radius: 48px;
  filter: blur(8px);
}

// —— AI 问诊真实案例卡（整洁垂直列表）——
.case-stack {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  width: 100%;
  max-width: 460px;
}

.case-card {
  position: relative;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border: 1px solid rgba($color-outline-variant, 0.3);
  border-radius: 22px;
  box-shadow: 0 10px 26px rgba(22, 29, 31, 0.06);
  transition: transform $transition-base, box-shadow $transition-base, border-color $transition-base;
  cursor: pointer;

  // 触碰到的卡片：上浮 + 变黄高亮
  &:hover {
    border-color: $color-brand;
    box-shadow: 0 16px 40px rgba($color-brand, 0.22);
    transform: translateY(-4px);
  }
}

.case-head {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.case-avatar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: $radius-full;
  color: #fff;

  .material-symbols-outlined { font-size: 20px; }
}

.case-meta {
  flex: 1;
  min-width: 0;
}

.case-title {
  font-family: $font-family-headline;
  font-size: $font-size-body-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
  @include text-ellipsis(1);
}

.case-tag {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.case-stars {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  color: $color-on-surface-variant;

  .material-symbols-outlined { font-size: 15px; color: #ffb020; }
}

.case-chat {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.bubble {
  display: flex;
  gap: $spacing-xs;
  padding: 10px 12px;
  border-radius: 14px;
  font-size: $font-size-label-md;
  line-height: $line-height-normal;

  .bubble-text { flex: 1; min-width: 0; }
}

.bubble.user {
  align-self: flex-end;
  max-width: 88%;
  background: rgba($color-secondary, 0.1);
  color: $color-on-surface;
  border-bottom-right-radius: 4px;

  .bubble-who {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: $radius-full;
    background: $color-secondary;
    color: #fff;
    font-size: 11px;
    font-weight: $font-weight-bold;
  }
}

.bubble.ai {
  align-self: flex-start;
  max-width: 94%;
  background: rgba($color-brand, 0.08);
  color: $color-on-surface;
  border-bottom-left-radius: 4px;

  .bubble-ai {
    flex-shrink: 0;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 22px;
    height: 22px;
    border-radius: $radius-full;
    background: $color-brand;
    color: #fff;
    font-size: 10px;
    font-weight: $font-weight-bold;
    letter-spacing: .5px;
  }
}

.case-foot {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-top: $spacing-sm;
  border-top: 1px dashed rgba($color-outline-variant, 0.35);
  font-size: $font-size-label-sm;
  color: $color-brand;
  font-weight: $font-weight-bold;

  .material-symbols-outlined { font-size: 16px; }
}

// ============ 5. 社区新鲜事（抽屉式） ============
.stories-eyebrow {
  display: inline-block;
  font-family: $font-family-headline;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  letter-spacing: 2px;
  color: $color-brand;
  background: rgba($color-brand, 0.1);
  border: 1px solid rgba($color-brand, 0.25);
  border-radius: 999px;
  padding: 4px 14px;
  margin-bottom: $spacing-sm;
}

.story-drawers {
  display: flex;
  gap: $spacing-md;
  height: 400px;

  @include tablet {
    height: 360px;
  }

  @include mobile {
    flex-direction: column;
    height: auto;
    gap: $spacing-md;
  }
}

.empty-hint {
  margin: $spacing-lg 0;
  padding: $spacing-xl $spacing-lg;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
  background: $color-surface-container-low;
  border: 1px dashed rgba($color-outline-variant, 0.5);
  border-radius: 18px;
}

.story-drawer {
  position: relative;
  flex: 1 1 0;
  min-width: 92px;
  border-radius: 22px;
  overflow: hidden;
  cursor: pointer;
  outline: none;
  background: $color-surface-container-low;
  box-shadow: 0 12px 30px rgba(22, 29, 31, 0.08);
  transition: flex 600ms cubic-bezier(0.16, 1, 0.3, 1), box-shadow 400ms ease;

  &.is-open {
    flex: 3.6 1 0;
    box-shadow: 0 24px 54px rgba(22, 29, 31, 0.18);
  }

  &:focus-visible {
    box-shadow: 0 0 0 3px rgba($color-brand, 0.5), 0 24px 54px rgba(22, 29, 31, 0.18);
  }

  @include mobile {
    flex: none;
    height: 260px;
    min-width: 0;
  }
}

.drawer-bg {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  transition: transform 700ms cubic-bezier(0.16, 1, 0.3, 1);

  .story-drawer:hover &,
  .story-drawer.is-open & {
    transform: scale(1.08);
  }
}

.drawer-shade {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(15, 18, 22, 0.08) 0%,
    rgba(15, 18, 22, 0.28) 42%,
    rgba(15, 18, 22, 0.82) 100%
  );

  .story-drawer.is-open & {
    background: linear-gradient(
      180deg,
      rgba(15, 18, 22, 0.12) 0%,
      rgba(15, 18, 22, 0.32) 38%,
      rgba(15, 18, 22, 0.86) 100%
    );
  }
}

/* 收起态：编号 + 竖排标题 */
.drawer-tab {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: $spacing-sm;
  padding: $spacing-lg $spacing-sm;
  transition: opacity 280ms ease;

  .story-drawer.is-open & {
    opacity: 0;
    pointer-events: none;
  }
}

.drawer-no {
  flex-shrink: 0;
  font-family: $font-family-headline;
  font-size: 13px;
  font-weight: $font-weight-extrabold;
  color: $color-brand;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 999px;
  padding: 3px 10px;
}

.drawer-tab-title {
  writing-mode: vertical-rl;
  text-orientation: mixed;
  color: #fff;
  font-family: $font-family-headline;
  font-size: $font-size-body-md;
  font-weight: $font-weight-bold;
  letter-spacing: 3px;
  line-height: 1.3;
  max-height: 240px;
  overflow: hidden;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.45);
}

/* 展开态：完整内容滑入 */
.drawer-panel {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: $spacing-lg;
  opacity: 0;
  transform: translateY(16px);
  transition: opacity 380ms ease 120ms, transform 480ms cubic-bezier(0.16, 1, 0.3, 1) 120ms;

  .story-drawer.is-open & {
    opacity: 1;
    transform: translateY(0);
  }
}

.drawer-no-lg {
  display: inline-block;
  font-family: $font-family-headline;
  font-size: $font-size-label-md;
  font-weight: $font-weight-extrabold;
  color: $color-brand;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 999px;
  padding: 3px 12px;
  margin-bottom: $spacing-sm;
}

.drawer-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  line-height: 1.3;
  color: #fff;
  margin-bottom: $spacing-xs;
  text-shadow: 0 2px 10px rgba(0, 0, 0, 0.45);
  @include text-ellipsis(2);
}

.drawer-excerpt {
  font-size: $font-size-label-md;
  line-height: $line-height-normal;
  color: rgba(255, 255, 255, 0.86);
  margin-bottom: $spacing-md;
  max-width: 46ch;
  @include text-ellipsis(2);
}

.drawer-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
}

/* 作者与互动（复用既有视觉） */
.story-author {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  min-width: 0;
}

.story-avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: $radius-full;
  object-fit: cover;
  background: $color-surface-variant;
  border: 2px solid rgba(255, 255, 255, 0.85);

  &.fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    background: linear-gradient(135deg, $color-brand, #ffa76b);
    color: #fff;
    font-size: $font-size-label-sm;
    font-weight: $font-weight-bold;
  }
}

.story-author-name {
  font-size: $font-size-label-sm;
  font-weight: $font-weight-semibold;
  color: #fff;
  @include text-ellipsis(1);
}

.story-metrics {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.9);
}

.metric {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  font-variant-numeric: tabular-nums;

  .material-symbols-outlined {
    font-size: 16px;
  }
}

/* 移动端：抽屉纵向堆叠，始终展开 */
@include mobile {
  .story-drawer .drawer-tab {
    display: none;
  }
  .story-drawer .drawer-panel {
    opacity: 1;
    transform: none;
  }
}

// ============ 6. 常见问题 ============
.faq-inner {
  max-width: 768px;
  margin: 0 auto;
  padding-left: $width-gutter-mobile;
  padding-right: $width-gutter-mobile;

  @include desktop {
    padding-left: $width-gutter-desktop;
    padding-right: $width-gutter-desktop;
  }
}

.faq-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.faq-item {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border: 1px solid rgba($color-outline-variant, 0.4);
  border-radius: 20px;
  transition: background $transition-base, border-color $transition-base;

  &:hover:not(.open) {
    background: $color-surface-container-low;
  }

  &.open {
    background: $color-on-surface;
    border-color: transparent;
    box-shadow: 0 10px 30px rgba(22, 29, 31, 0.12);

    .faq-head {
      color: $color-surface-container-lowest;
      margin-bottom: $spacing-md;
    }
  }
}

.faq-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  width: 100%;
  padding: 0;
  background: transparent;
  border: none;
  color: $color-on-surface;
  cursor: pointer;
  text-align: left;

  .material-symbols-outlined {
    flex-shrink: 0;
    font-size: 22px;
  }
}

.faq-q {
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
}

.faq-a {
  font-size: $font-size-label-md;
  line-height: $line-height-relaxed;
  color: rgba(255, 255, 255, 0.8);
}

// ============ 7. Footer ============
.site-footer {
  width: 100%;
  margin-top: $spacing-xl;
  padding-top: 80px;
  padding-bottom: $spacing-xl;
  background: $color-on-surface;
  color: $color-surface-container-lowest;
  border-radius: 40px 40px 0 0;
}

.footer-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-2xl;
  margin-bottom: $spacing-2xl;

  @include tablet {
    grid-template-columns: repeat(2, 1fr);
  }

  @include desktop {
    grid-template-columns: 2fr 1fr 1fr 1.4fr;
  }
}

.footer-brand {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.footer-logo {
  display: flex;
  align-items: center;
  gap: $spacing-xs;

  .material-symbols-outlined {
    font-size: 30px;
    color: $color-brand;
  }
}

.footer-logo-text {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-extrabold;
  color: #ffffff;
}

.footer-about {
  max-width: 340px;
  font-size: $font-size-label-md;
  line-height: $line-height-relaxed;
  color: rgba(255, 255, 255, 0.7);
}

.footer-social {
  display: flex;
  gap: $spacing-sm;

  button {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    background: rgba(255, 255, 255, 0.1);
    border: none;
    border-radius: $radius-default;
    color: rgba(255, 255, 255, 0.85);
    font-size: $font-size-label-sm;
    font-weight: $font-weight-bold;
    cursor: pointer;
    transition: background $transition-fast;

    &:hover {
      background: $color-brand;
      color: #ffffff;
    }
  }
}

.footer-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;

  a {
    font-size: $font-size-label-md;
    color: rgba(255, 255, 255, 0.7);
    cursor: pointer;
    transition: color $transition-fast;

    &:hover {
      color: #ffffff;
    }
  }
}

.footer-col-title {
  margin-bottom: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-body-md;
  font-weight: $font-weight-bold;
  color: #ffffff;
}

.subscribe {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 4px;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: $radius-full;

  input {
    flex: 1;
    min-width: 0;
    padding: $spacing-xs $spacing-md;
    background: transparent;
    border: none;
    outline: none;
    color: #ffffff;
    font-size: $font-size-label-md;

    &::placeholder {
      color: rgba(255, 255, 255, 0.5);
    }
  }

  button {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    flex-shrink: 0;
    background: $color-brand;
    border: none;
    border-radius: $radius-full;
    color: #ffffff;
    cursor: pointer;
    transition: background $transition-fast;

    .material-symbols-outlined {
      font-size: 18px;
    }

    &:hover {
      background: $color-brand-dark;
    }
  }
}

.footer-mail {
  font-size: $font-size-label-sm;
  color: rgba(255, 255, 255, 0.5);
}

.footer-bottom {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
  padding-top: $spacing-xl;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: $font-size-label-sm;
  color: rgba(255, 255, 255, 0.5);

  @include desktop {
    flex-direction: row;
  }
}

// ============ 移动端收尾 ============
// px-to-viewport 会把 px 等比缩到 vw，手机端间距/字号会被压得过小，
// 这里用 rem 重写关键值（rem 不参与 vw 换算），恢复正常呼吸感。
@include mobile {
  section {
    padding-top: 3rem;
    padding-bottom: 3rem;
  }

  .hero {
    padding-top: 2rem;
  }

  .hero-inner,
  .wellness-inner {
    gap: 2rem;
  }

  .hero-title {
    font-size: 2rem;
  }

  .hero-sub {
    font-size: 1rem;
    margin-bottom: 1.5rem;
  }

  .hero-cta {
    padding: 0.875rem 1.75rem;
    font-size: 0.9375rem;
  }

  .section-title {
    font-size: 1.625rem;

    &.center-title {
      margin-bottom: 2rem;
    }
  }

  .section-desc {
    font-size: 0.9375rem;
  }

  .section-head {
    margin-bottom: 2rem;
  }

  .section-foot {
    margin-top: 2rem;
  }

  .service-grid,
  .shop-grid,
  .story-drawers,
  .faq-list {
    gap: 1rem;
  }

  .service-card,
  .faq-item {
    padding: 1.25rem;
  }

  .service-icon {
    width: 3rem;
    height: 3rem;
    margin-bottom: 1rem;
  }

  .service-desc,
  .drawer-excerpt {
    margin-bottom: 1rem;
  }

  .shop-card {
    height: auto;
  }

  .shop-info {
    padding: 1.25rem;
  }

  .shop-media {
    height: 160px;
  }

  .shop-desc {
    min-height: auto;
  }

  .wellness-list {
    gap: 1.25rem;
    margin-bottom: 1.5rem;
  }

  .wellness-sub {
    margin-top: 1.5rem;
    margin-bottom: 1rem;
  }

  .site-footer {
    padding-top: 3rem;
    padding-bottom: 2rem;
    border-radius: 1.75rem 1.75rem 0 0;
  }

  .footer-grid {
    gap: 2rem;
    margin-bottom: 2rem;
  }

  .footer-bottom {
    padding-top: 1.5rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .service-card,
  .shop-card,
  .story-drawer {
    transition: none;
  }
}
</style>
