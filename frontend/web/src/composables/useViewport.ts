import { ref, computed, onMounted, onUnmounted } from 'vue'

/**
 * 视口判断 composable
 * 基于 PRD §5.0.1：桌面 ≥1024 / 平板 768-1024 / 手机 ≤768
 */
export function useViewport() {
  const width = ref(typeof window !== 'undefined' ? window.innerWidth : 1920)
  const height = ref(typeof window !== 'undefined' ? window.innerHeight : 1080)

  const isMobile = computed(() => width.value <= 768)
  const isTablet = computed(() => width.value > 768 && width.value < 1024)
  const isDesktop = computed(() => width.value >= 1024)
  const isWide = computed(() => width.value >= 1280)

  // 产品端判断
  const isUserClientAllowed = computed(() => true) // 用户端三档都允许
  const isMerchantAllowed = computed(() => width.value >= 768) // 商家端 ≥768
  const isAdminAllowed = computed(() => width.value >= 1024) // 后台 ≥1024

  // 视口类型
  const viewportType = computed<'mobile' | 'tablet' | 'desktop'>(() => {
    if (isMobile.value) return 'mobile'
    if (isTablet.value) return 'tablet'
    return 'desktop'
  })

  let timer: number | null = null

  const onResize = () => {
    if (timer) window.clearTimeout(timer)
    timer = window.setTimeout(() => {
      width.value = window.innerWidth
      height.value = window.innerHeight
    }, 100)
  }

  onMounted(() => {
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', onResize, { passive: true })
      width.value = window.innerWidth
      height.value = window.innerHeight
    }
  })

  onUnmounted(() => {
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', onResize)
    }
    if (timer) window.clearTimeout(timer)
  })

  return {
    width,
    height,
    isMobile,
    isTablet,
    isDesktop,
    isWide,
    isUserClientAllowed,
    isMerchantAllowed,
    isAdminAllowed,
    viewportType,
  }
}