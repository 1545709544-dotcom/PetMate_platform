/**
 * 通用类型定义
 */

// 性别
export type Gender = 0 | 1 | 2 // 0未知 / 1男 / 2女
// 宠物性别
export type PetGender = 0 | 1 | 2 // 0公 / 1母 / 2未知

// 订单状态
export type OrderStatus =
  | 'pending_pay'
  | 'paid'
  | 'shipping'
  | 'done'
  | 'cancelled'
  | 'refunding'

// 用户角色
export type UserRole = 'guest' | 'user' | 'vip' | 'merchant' | 'admin'

// 风险等级（AI 问诊）
export type RiskLevel = 'urgent' | 'normal' | 'good'

// 收藏目标类型
export type FavoriteTarget = 'product' | 'post' | 'article' | 'service'

// 优惠券类型
export type CouponType = 'reduction' | 'discount' | 'category'

// 支付方式
export type PayChannel = 'alipay'