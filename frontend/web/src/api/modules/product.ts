import request from '@/api/request'
import type { PageResp } from '@/api/request'

// 商品 SPU
export interface Product {
  id: number
  name: string
  categoryId: number
  categoryName?: string
  brandId?: number
  mainImage: string
  subImages?: string[]
  detailImages?: string[]
  detail?: string
  salePrice: number
  originalPrice?: number
  saleCount: number
  /** 真实评价条数（后端 t_product_review 统计），不要再用 saleCount 冒充 */
  reviewCount?: number
  /** 上下架状态：1 上架、0 下架。详情接口已返回，列表页可据此打标 */
  status?: number
  rating?: number
  /** 后端 t_product.default_sku_id：列表页/推荐位「一键加购」用，为空表示必须进详情选规格 */
  defaultSkuId?: number
  tags?: string[]
  /** 上架/创建时间，列表接口已返回，前端「新品」排序使用 */
  createdAt?: string
  // 规格定义（规格组）与 SKU 列表（编辑回填用）
  specs?: Array<{ name: string; values: string[] }>
  skus?: Array<{
    id?: number
    productId?: number
    skuCode?: string
    specValues?: Record<string, string>
    price?: number
    stock?: number
    image?: string
  }>
}

// 商品分类
export interface Category {
  id: number
  parentId: number
  name: string
  icon?: string
  level: 1 | 2 | 3
  sort?: number
  children?: Category[]
}

// SKU
export interface Sku {
  id: number
  productId: number
  skuCode: string
  specValues: Record<string, string>
  price: number
  stock: number
  image?: string
}

// 商品评价
export interface ProductReview {
  id: number
  productId: number
  userId: number
  userNickname: string
  userAvatar?: string
  rating: number
  content: string
  images?: string[]
  createdAt: string
  /** 商家回复内容（后端已下发，前端需展示） */
  reply?: string | null
  /** 商家回复时间 */
  repliedAt?: string | null
}

// 评价汇总(后端 ratingCounts key 为字符串 "1"~"5")
export interface ReviewSummary {
  averageRating: number
  totalReviews: number
  ratingCounts: Record<string, number>
}

export const productApi = {
  // 分类树
  tree() {
    return request<Category[]>({
      url: '/category/tree',
      method: 'GET',
    })
  },

  // 商品详情
  detail(id: number | string) {
    return request<Product>({
      url: `/product/${id}`,
      method: 'GET',
    })
  },

  // 记录商品浏览足迹（登录态；静默失败，不阻塞详情加载）
  recordFootprint(id: number | string) {
    return request<void>({
      url: `/product/${id}/footprint`,
      method: 'POST',
    })
  },

  // 搜索商品(带分页)
  search(params: { keyword: string; page?: number; size?: number; categoryId?: number }) {
    return request<PageResp<Product>>({
      url: '/search',
      method: 'GET',
      params,
    })
  },

  // 商品列表(按分类)
  /** categoryId 传 undefined 表示不限品类（首页/全部商品列表用法），后端 /product/list 该参数可选 */
  listByCategory(categoryId?: number, page = 1, size = 20) {
    return request<PageResp<Product>>({
      url: '/product/list',
      method: 'GET',
      params: { categoryId, page, size },
    })
  },

  // 相关推荐
  related(productId: number) {
    return request<Product[]>({
      url: `/recommend/related?productId=${productId}`,
      method: 'GET',
    })
  },

  // 首页/推荐 feed：按销量+浏览排序的热门好物（不依赖具体分类，恒有内容）
  feed() {
    return request<Product[]>({
      url: '/recommend/feed',
      method: 'GET',
    })
  },

  // 商品评价列表(分页)
  reviews(productId: number | string, page = 1, size = 10) {
    return request<PageResp<ProductReview>>({
      url: `/product/${productId}/reviews`,
      method: 'GET',
      params: { page, size },
    })
  },

  // 评价汇总
  reviewSummary(productId: number | string) {
    return request<ReviewSummary>({
      url: `/product/${productId}/reviews/summary`,
      method: 'GET',
    })
  },

  // 发表评价（每单每商品只能评价一次，需传 orderNo）
  addReview(productId: number | string, payload: { orderNo: string; rating: number; content: string; images?: string[] }) {
    return request<ProductReview>({
      url: `/product/${productId}/review`,
      method: 'POST',
      data: payload,
    })
  },

  // 评价资格（是否已购买该商品）
  reviewEligible(productId: number | string) {
    return request<{ eligible: boolean }>({
      url: `/product/${productId}/review/eligible`,
      method: 'GET',
    })
  },

  // 查询某订单已评价的商品 ID 列表 + 评价详情
  reviewedProducts(orderNo: string) {
    return request<{
      reviewedProductIds: number[]
      reviews: Array<{
        productId: number
        rating: number
        content: string
        images: string | null
        createdAt: string
      }>
    }>({
      url: `/product/review/check`,
      method: 'GET',
      params: { orderNo },
    })
  },
}