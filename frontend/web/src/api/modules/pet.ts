import request from '@/api/request'

// 宠物分类
export interface PetCategory {
  id: number
  name: string
  icon?: string
  sort?: number
}

// 宠物品种
export interface PetBreed {
  id: number
  categoryId: number
  name: string
}

// 宠物
export type PetGender = 0 | 1 | 2 // 0公 / 1母 / 2未知
export type PetNeuter = 0 | 1

export interface Pet {
  id: number
  userId: number
  name: string
  categoryId: number
  breedId: number
  categoryName?: string
  breedName?: string
  gender: PetGender
  birthday?: string
  weight?: number
  neutered: PetNeuter
  avatar?: string
  tags?: string[]
  description?: string
}

// 创建/更新时的请求载荷
export interface PetSavePayload {
  id?: number
  name: string
  categoryId: number
  breedId: number
  // 选「其他」自定义品种时:breedId 传 -1,并通过 breedName 传入手填的品种名
  breedName?: string
  gender: PetGender
  birthday?: string
  weight?: number
  neutered: PetNeuter | boolean
  avatar?: string
  tags?: string[]
  description?: string
  // 初值健康记录(可选):提交时一次性落到 t_pet_health_record
  vaccineDone?: boolean
  dewormDone?: boolean
  allergies?: string
}

// 健康记录
export type HealthRecordType =
  | 'vaccine'
  | 'deworming'
  | 'checkup'
  | 'weight'
  | 'illness'
  | 'allergy'
  | 'surgery'

export interface HealthRecord {
  id: number
  petId: number
  recordType: HealthRecordType
  title: string
  content?: string
  date: string
  nextDate?: string
  reminder?: boolean
}

// 新增健康记录的载荷:比 HealthRecord 多了生成提醒用的两个可选字段
export interface HealthRecordPayload {
  petId: number
  recordType: HealthRecordType
  title: string
  content?: string
  /** 记录发生日期 yyyy-MM-dd */
  date: string
  /** 下次到期日,配合 reminder=true 生成关怀提醒 */
  nextDate?: string
  reminder?: boolean
  /** 提前几天提醒(默认 3) */
  advanceDays?: number
  /** 循环周期天数(默认 0=一次性) */
  cycleDays?: number
}

// 关怀提醒(疫苗/驱虫/体检/自定义 的到期待办)
// 与 HealthRecord 的区别:record 是"已发生的日志",reminder 是"未来要做的任务"
export type ReminderType = 'vaccine' | 'deworming' | 'checkup' | 'custom'
export type ReminderStatus = 'pending' | 'overdue' | 'done'

export interface PetReminder {
  id: number
  petId: number
  petName?: string
  type: ReminderType
  title: string
  /** 到期日 yyyy-MM-dd */
  dueDate: string
  /** 提前几天推送站内信 */
  advanceDays: number
  /** 循环周期(天),0 = 一次性 */
  cycleDays: number
  status: ReminderStatus
  doneAt?: string
  createdAt?: string
}

// 标记完成的返回:cycled=true 时后端已自动生成下一次
export interface ReminderDoneResult {
  id: number
  cycled: boolean
  nextReminderId?: number
  nextDueDate?: string
}

// 宠物健康摘要(来自后端聚合,省去前端多次请求)
export interface PetHealthSummary {
  petId: number
  vaccineDone: boolean
  dewormDone: boolean
  vaccineCount: number
  dewormCount: number
  records: HealthRecord[]
}

// ============ 前端共享工具：性别 / 年龄 统一口径 ============
// 性别：0=公 1=母 2=未知（与后端、建档表单按钮一致）
export const PET_GENDER_LABEL: Record<number, string> = { 0: '公', 1: '母', 2: '未知' }
export function petGenderLabel(g?: number | null): string {
  if (g == null) return '未知'
  return PET_GENDER_LABEL[g] ?? '未知'
}

// 年龄：统一显示「X岁Y个月」（不足 1 岁显示「X个月」）；生日缺失/非法返回「未知」
export function petAgeText(birthday?: string | null): string {
  if (!birthday) return '未知'
  const b = new Date(birthday)
  if (isNaN(b.getTime())) return '未知'
  const now = new Date()
  let months = (now.getFullYear() - b.getFullYear()) * 12 + (now.getMonth() - b.getMonth())
  if (now.getDate() < b.getDate()) months--
  if (months < 0) return '未知'
  const y = Math.floor(months / 12)
  const m = months % 12
  if (y <= 0) return `${m} 个月`
  if (m === 0) return `${y} 岁`
  return `${y} 岁 ${m} 个月`
}

// 快速记录预设（疫苗/驱虫/体检/称重）的默认复检周期(天)，集中维护便于调整
export interface QuickPreset {
  type: HealthRecordType
  label: string
  icon: string
  title: string
  cycleDays: number
  placeholder: string
}
export const PET_QUICK_PRESETS: QuickPreset[] = [
  { type: 'vaccine', label: '疫苗', icon: 'vaccines', title: '疫苗接种', cycleDays: 365, placeholder: '如:狂犬疫苗 / 猫三联' },
  { type: 'deworming', label: '驱虫', icon: 'bug_report', title: '体内外驱虫', cycleDays: 90, placeholder: '如:大宠爱 体外驱虫' },
  { type: 'checkup', label: '体检', icon: 'assessment', title: '健康体检', cycleDays: 180, placeholder: '如:年度全面体检' },
  { type: 'weight', label: '称重', icon: 'monitor_weight', title: '体重记录', cycleDays: 0, placeholder: '如:4.2 kg' },
]

export const petApi = {
  // 字典:宠物分类
  categories() {
    return request<PetCategory[]>({
      url: '/pet/categories',
      method: 'GET',
    })
  },

  // 字典:品种(可按 categoryId 过滤)
  breeds(categoryId?: number) {
    return request<PetBreed[]>({
      url: '/pet/breeds',
      method: 'GET',
      params: categoryId != null ? { categoryId } : undefined,
    })
  },

  // 宠物列表
  list() {
    return request<Pet[]>({
      url: '/pet/list',
      method: 'GET',
    })
  },

  // 宠物详情
  detail(id: number) {
    return request<Pet>({
      url: `/pet/${id}`,
      method: 'GET',
    })
  },

  // 新增/编辑宠物
  save(pet: PetSavePayload) {
    return request<Pet>({
      url: pet.id ? `/pet/${pet.id}` : '/pet',
      method: pet.id ? 'PUT' : 'POST',
      data: pet,
    })
  },

  // 删除宠物
  remove(id: number) {
    return request<void>({
      url: `/pet/${id}`,
      method: 'DELETE',
    })
  },

  // 健康档案
  health(id: number) {
    return request<HealthRecord[]>({
      url: `/pet/${id}/health`,
      method: 'GET',
    })
  },

  // 宠物健康摘要
  healthSummary(id: number) {
    return request<PetHealthSummary>({
      url: `/pet/${id}/health/summary`,
      method: 'GET',
    })
  },

  // 添加健康记录(reminder=true 且带 nextDate 时,后端会同步生成关怀提醒)
  addHealth(record: HealthRecordPayload) {
    return request<{ petId: number; reminderId?: number }>({
      url: '/pet/health',
      method: 'POST',
      data: record,
    })
  },

  // ===== 关怀提醒 =====

  // 某只宠物的提醒列表(按 逾期 > 待办 > 已完成 排序)
  reminders(petId: number) {
    return request<PetReminder[]>({
      url: `/pet/${petId}/reminders`,
      method: 'GET',
    })
  },

  // 当前用户全部宠物的提醒聚合(今日待办数据源)
  userReminders() {
    return request<{
      overdue: PetReminder[]
      dueSoon: PetReminder[]
      done: PetReminder[]
      counts: { overdue: number; dueSoon: number; done: number; pending: number }
    }>({
      url: '/pet/user/reminders',
      method: 'GET',
    })
  },

  // 手动创建提醒
  createReminder(data: {
    petId: number
    title: string
    type?: ReminderType
    dueDate: string
    advanceDays?: number
    cycleDays?: number
  }) {
    return request<{ id: number }>({
      url: '/pet/reminder',
      method: 'POST',
      data,
    })
  },

  // 标记完成(设置了 cycleDays 会自动生成下一次)
  doneReminder(id: number) {
    return request<ReminderDoneResult>({
      url: `/pet/reminder/${id}/done`,
      method: 'POST',
    })
  },

  // 删除提醒
  deleteReminder(id: number) {
    return request<void>({
      url: `/pet/reminder/${id}`,
      method: 'DELETE',
    })
  },

  // 成长日记照片列表
  photos(petId: number) {
    return request<PetPhoto[]>({
      url: `/pet/${petId}/photos`,
      method: 'GET',
    })
  },

  // 上传成长日记照片（多张）
  uploadPhotos(petId: number, files: File[]) {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return request<PetPhoto[]>({
      url: `/pet/${petId}/photos`,
      method: 'POST',
      data: form,
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },

  // 删除成长日记照片
  deletePhoto(photoId: number) {
    return request<void>({
      url: `/pet/photo/${photoId}`,
      method: 'DELETE',
    })
  },

  // 批量删除成长日记照片（传照片 id 数组）
  deletePhotos(photoIds: number[]) {
    return request<{ deleted: number }>({
      url: `/pet/photos/batch-delete`,
      method: 'POST',
      data: { photoIds },
    })
  },
}

// 成长日记照片
export interface PetPhoto {
  id: number
  petId: number
  url: string
  caption?: string
  createdAt?: string
}
