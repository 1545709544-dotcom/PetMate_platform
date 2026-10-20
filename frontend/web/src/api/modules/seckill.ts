import request from '@/api/request'

export interface Seckill {
  id: number
  productId: number
  title: string
  seckillPrice: number
  stock: number
  sold: number
  startTime: string
  endTime: string
  productName: string
  productImage: string
  originalPrice?: number
}

export const seckillApi = {
  list() {
    return request<Seckill[]>({ url: '/seckill/list', method: 'GET' })
  },
  detail(id: number) {
    return request<Seckill>({ url: `/seckill/${id}`, method: 'GET' })
  },
  grab(id: number) {
    return request<{ orderNo: string; seckillPrice: number }>({
      url: `/seckill/${id}/grab`,
      method: 'POST',
    })
  },
}
