import request from '@/api/request'

// 举报目标类型（需与后端 ReportController.ALLOWED_TYPES 保持一致）
export type ReportTargetType = 'post' | 'comment' | 'user' | 'article' | 'product'

export interface ReportSubmitParams {
  targetType: ReportTargetType
  targetId: number
  reason: string
}

export interface ReportSubmitResult {
  id: number
  status: string
}

// 举报（用户侧提交，后端 /report）
export const reportApi = {
  /** 提交举报，登录即可；后端校验 targetType ∈ post/comment/user/article/product */
  submit(params: ReportSubmitParams) {
    return request<ReportSubmitResult>({
      url: '/report',
      method: 'POST',
      data: params,
    })
  },
}
