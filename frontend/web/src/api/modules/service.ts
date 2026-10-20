import request from '@/api/request'
import type { PageResp } from '@/api/request'

/** 服务方式：到店 / 上门 */
export type ServiceMode = 'instore' | 'onsite'
/** 适用宠物：通用 / 仅狗 / 仅猫 */
export type PetType = 'all' | 'dog' | 'cat'
/** 列表排序 */
export type ServiceSort = 'default' | 'sales' | 'price_asc' | 'price_desc' | 'rating'

/** 规格档位：体型 × 毛长分档定价，行业标准做法 */
export interface ServiceSpec {
  id: number
  name: string
  petKind?: PetType
  coat?: string
  weightMin?: number | null
  weightMax?: number | null
  price: number
  duration: number
  sortNo?: number
}

/** 单项加购（剪指甲 / 挤肛门腺 / 药浴 …） */
export interface ServiceAddon {
  id: number
  name: string
  price: number
  duration?: number
  description?: string
  sortNo?: number
}

/** 服务项（列表 / 详情共用，详情多带 specs / addons / 须知） */
export interface ServiceItem {
  id: number
  storeId: number
  storeName: string
  storeAddress?: string
  name: string
  category: string
  subCategory?: string
  serviceMode?: ServiceMode
  petType?: PetType
  price: number            // 起价（= 最低档位价）
  maxPrice?: number        // 最高档位价，用于「¥128 - 398」区间展示
  originalPrice?: number
  onsiteFee?: number
  duration?: number
  cover?: string
  description?: string
  status?: string | number
  sales?: number
  rating?: number
  reviewCount?: number
  tags?: string[]
  icon?: string
  priceUnit?: string       // 次 / 天 / 课时
  dailyPriced?: boolean    // 寄养类需选天数
  // —— 详情专属 ——
  specs?: ServiceSpec[]
  addons?: ServiceAddon[]
  includeItems?: string[]
  notices?: string[]
  refundPolicy?: string
  advanceDays?: number
  openTime?: string
  closeTime?: string
  slotMinutes?: number
  store?: StoreBrief
}

export interface StoreBrief {
  id: number
  name: string
  address?: string
  logo?: string
  phone?: string
  rating?: number
}

/** 分类树节点 */
export interface ServiceCategoryNode {
  name: string
  count: number
  icon: string
  priceUnit: string
  dailyPriced?: boolean
  subs: Array<{ name: string; count: number }>
}

/** 可预约时段 */
export interface ServiceSlot {
  time: string        // "10:00"
  label: string       // "10:00-11:00"
  remain: number
  capacity: number
  disabled: boolean
  reason?: string
}

/** 服务评价 */
export interface ServiceReview {
  id: number
  nickname: string
  avatar?: string
  rating: number
  content?: string
  images?: string[]
  specName?: string
  reply?: string
  createdAt: string
}

export interface ServiceReviewResp {
  list: ServiceReview[]
  total: number
  rating: number
  reviewCount: number
  sales: number
  dist: Record<string, number>   // { "5": 12, "4": 3, ... }
}

// 门店详情
export interface StoreService {
  id: number
  name: string
  category?: string
  subCategory?: string
  price: number
  cover?: string
  description?: string
  duration?: number
  sales?: number
  rating?: number
  priceUnit?: string
}

export interface StoreReview {
  id: number
  userId?: number
  userNickname: string
  userAvatar?: string
  rating: number
  content: string
  images?: string[]
  specName?: string
  reply?: string
  createdAt: string
}

export interface StoreDetail {
  id: number
  name: string
  address: string
  phone?: string
  logo?: string
  description?: string
  cover?: string
  rating?: number
  averageRating?: number
  reviewCount?: number
  services?: StoreService[]
  reviews?: StoreReview[]
}

export interface ServiceSearchParams {
  keyword?: string
  category?: string
  subCategory?: string
  petType?: PetType
  serviceMode?: ServiceMode
  priceMax?: number
  storeId?: number
  sort?: ServiceSort
  page?: number
  size?: number
}

export const serviceApi = {
  /** 服务搜索（多维筛选 + 排序，分页） */
  search(params: ServiceSearchParams = {}) {
    return request<PageResp<ServiceItem>>({
      url: '/service/search',
      method: 'GET',
      params,
    })
  },

  /** 服务详情（含规格档位 / 加购项 / 包含项 / 须知 / 退改） */
  detail(id: number | string) {
    return request<ServiceItem>({
      url: `/service/${id}`,
      method: 'GET',
    })
  },

  /** 指定日期的可约时段与余位 */
  slots(id: number | string, date: string) {
    return request<{ date: string; slots: ServiceSlot[]; slotMinutes: number }>({
      url: `/service/${id}/slots`,
      method: 'GET',
      params: { date },
    })
  },

  /** 服务评价（含评分分布） */
  reviews(id: number | string, params: { page?: number; size?: number } = {}) {
    return request<ServiceReviewResp>({
      url: `/service/${id}/reviews`,
      method: 'GET',
      params,
    })
  },

  /** 门店详情（含服务项列表 + 评价列表） */
  store(id: number | string) {
    return request<StoreDetail>({
      url: `/store/${id}`,
      method: 'GET',
    })
  },

  /** 标准分类树（一级 + 二级 + 数量 + 图标） */
  categories() {
    return request<ServiceCategoryNode[]>({
      url: '/service/categories',
      method: 'GET',
    })
  },
}
