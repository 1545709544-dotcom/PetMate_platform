import request from '@/api/request'

// VIP 套餐
export interface VipPlan {
  /** 套餐标识（l1/l2/l3），下单与选中态均以此为准 */
  key: string
  level: number
  tag: string
  name: string
  /** 月费展示价 */
  monthly: number
  price: number
  badge: string
  perks: string[]
}
export interface VipPlanResp {
  plans: VipPlan[]
  /** 权益对比表，字段与 MetaController 下发一致（此前误写成 label/l1/l2/l3） */
  compareRows: { feature: string; desc: string; basic: string; hasVip: string }[]
}

// 知识小贴士
export interface KnowledgeTip {
  emoji: string
  title: string
  text: string
}

// 咨询快捷症状
export interface QuickSymptom {
  key: string
  label: string
  prompt: string
}

// 宠物健康分类
export interface HealthCategory {
  key: string
  label: string
  icon: string
  color: 'orange' | 'green' | 'red' | 'blue' | 'purple'
}

// 社区活动卡
export interface CommunityActivity {
  title: string
  desc: string
  to: string
  variant: string
  icon: string
  cover: string
}

// 新人礼包券
export interface GiftPackCoupon {
  id: number
  name: string
  amount: number
  threshold: number
}

export const metaApi = {
  vipPlan() {
    return request<VipPlanResp>({ url: '/vip/plan', method: 'GET' })
  },
  knowledgeTip() {
    return request<{ tips: KnowledgeTip[] }>({ url: '/knowledge/tip', method: 'GET' })
  },
  hotWords() {
    return request<string[]>({ url: '/search/hot-words', method: 'GET' })
  },
  communityActivities() {
    return request<CommunityActivity[]>({ url: '/community/activities', method: 'GET' })
  },
  quickSymptoms() {
    return request<QuickSymptom[]>({ url: '/consult/quick-symptoms', method: 'GET' })
  },
  healthCategories() {
    return request<HealthCategory[]>({ url: '/pet/health-categories', method: 'GET' })
  },
  giftPack() {
    return request<GiftPackCoupon[]>({ url: '/coupon/gift-pack', method: 'GET' })
  },
}