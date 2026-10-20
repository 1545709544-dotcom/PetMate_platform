import request from '@/api/request'

export interface Adoption {
  id: number
  title: string
  petType: 'cat' | 'dog' | 'other'
  breed?: string | null
  gender: number
  age?: string | null
  healthStatus?: string | null
  vaccinated: boolean
  neutered: boolean
  city?: string | null
  contact?: string | null
  images: string[] | string | null
  status: number
  rescueStory?: string | null
}

export interface AdoptionApplication {
  id: number
  status: string
}

// 管理后台列表项
export interface AdminAdoption {
  id: number
  title: string
  petType: string
  breed?: string | null
  gender: number
  age?: string | null
  city?: string | null
  status: number
  createdAt?: string
}

// 申请（后台 / 我的申请 共用）
export interface AdoptionApplicationItem {
  id: number
  adoptionId: number
  name: string
  phone: string
  reason?: string | null
  status: number
  createdAt?: string
  adoptionTitle?: string
  petType?: string
  images?: string[] | string | null
  city?: string | null
}

export const adoptionApi = {
  list() {
    return request<Adoption[]>({ url: '/adoption/list', method: 'GET' })
  },
  detail(id: number) {
    return request<Adoption>({ url: `/adoption/${id}`, method: 'GET' })
  },
  apply(id: number, payload: { name: string; phone: string; reason?: string }) {
    return request<AdoptionApplication>({
      url: `/adoption/${id}/apply`,
      method: 'POST',
      data: payload,
    })
  },

  // ===== 用户端：我的领养申请 =====
  myApplications() {
    return request<AdoptionApplicationItem[]>({ url: '/adoption/my-applications', method: 'GET' })
  },

  // 撤销自己的领养申请（仅待审核可撤销）
  cancelApplication(id: number | string) {
    return request<null>({ url: `/adoption/applications/${id}/cancel`, method: 'POST' })
  },

  // ===== 用户端：发布领养（图片上传 + 创建）=====
  userUpload(files: File[], onProgress?: (percent: number) => void) {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return request<{ urls: string[] }>({
      url: '/adoption/upload',
      method: 'POST',
      data: form,
      onUploadProgress: onProgress
        ? (evt: any) => {
            const total = evt?.total ?? 0
            const percent = total ? Math.round((evt.loaded / total) * 100) : 0
            onProgress(percent)
          }
        : undefined,
    })
  },
  userCreate(data: Partial<Adoption> & { images?: string[] }) {
    return request<{ id: number }>({ url: '/adoption', method: 'POST', data })
  },

  // ===== 用户端：我的领养数据管理 =====
  mine(params: { status?: number | string; petType?: string; keyword?: string } = {}) {
    return request<Adoption[]>({ url: '/adoption/mine', method: 'GET', params })
  },
  updateMine(id: number, data: Partial<Adoption> & { images?: string[] }) {
    return request<void>({ url: `/adoption/${id}`, method: 'PUT', data })
  },
  deleteMine(id: number) {
    return request<void>({ url: `/adoption/${id}`, method: 'DELETE' })
  },
  setStatusMine(id: number, status: number) {
    return request<void>({ url: `/adoption/${id}/status`, method: 'POST', data: { status } })
  },

  // ===== 管理后台 =====
  adminList(params: { petType?: string; status?: number; keyword?: string } = {}) {
    return request<AdminAdoption[]>({ url: '/admin/adoption', method: 'GET', params })
  },
  adminCreate(data: Partial<Adoption> & { images?: string[] }) {
    return request<{ id: number }>({ url: '/admin/adoption', method: 'POST', data })
  },
  adminUpdate(id: number, data: Partial<Adoption> & { images?: string[] }) {
    return request<void>({ url: `/admin/adoption/${id}`, method: 'PUT', data })
  },
  adminDelete(id: number) {
    return request<void>({ url: `/admin/adoption/${id}`, method: 'DELETE' })
  },
  adminSetStatus(id: number, status: number) {
    return request<void>({ url: `/admin/adoption/${id}/status`, method: 'POST', data: { status } })
  },
  adminApplications(params: { status?: number; adoptionId?: number } = {}) {
    return request<AdoptionApplicationItem[]>({ url: '/admin/adoption/applications', method: 'GET', params })
  },
  adminReview(id: number, approved: boolean, reason?: string) {
    return request<void>({ url: `/admin/adoption/application/${id}/review`, method: 'POST', data: { approved, reason } })
  },
  adminUpload(files: File[]) {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return request<{ urls: string[] }>({ url: '/admin/adoption/upload', method: 'POST', data: form })
  },
}
