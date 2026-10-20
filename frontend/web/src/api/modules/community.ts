import request from '@/api/request'
import type { PageResp } from '@/api/request'

// 社区帖子
export interface Post {
  id: number
  userId: number
  userNickname: string
  userAvatar: string
  content: string
  images?: string[]
  videoUrl?: string
  topicId?: number
  topicName?: string
  likeCount: number
  commentCount: number
  collectCount: number
  liked?: boolean
  collected?: boolean
  visible: 'public' | 'follow' | 'private'
  createdAt: string
  /** 发布时所在城市(如 北京)，用于社区「附近/市区」分类展示 */
  city?: string
}

// 评论
export interface Comment {
  id: number
  postId: number
  userId: number
  userNickname: string
  userAvatar: string
  content: string
  likeCount: number
  createdAt: string
}

// 文章
export interface Article {
  id: number
  categoryId: number
  title: string
  content: string
  cover?: string
  author?: string
  viewCount: number
  seoUrl?: string
  createdAt: string
}

// 文章分类
export interface ArticleCategory {
  id: number
  name: string
  icon?: string
  sort: number
}

export const communityApi = {
  // 社区 feed（city 可选：传用户所在城市即「附近/市区」分类）
  feed(page = 1, size = 10, city?: string) {
    return request<PageResp<Post>>({
      url: '/community/feed',
      method: 'GET',
      params: city ? { page, size, city } : { page, size },
    })
  },

  // 首页「为你精选」策展接口：按互动量/阅读量真实排序，seed 用于「换一批」轮换
  forYou(seed = 0) {
    return request<{ posts: Post[]; articles: Article[]; seed: number }>({
      url: '/community/for-you',
      method: 'GET',
      params: { seed },
    })
  },

  // 社区搜索（按内容 / 话题名模糊匹配）
  search(keyword: string, page = 1, size = 20) {
    return request<PageResp<Post>>({
      url: '/community/search',
      method: 'GET',
      params: { keyword, page, size },
    })
  },

  // 帖子详情
  postDetail(id: number) {
    return request<Post>({
      url: `/post/${id}`,
      method: 'GET',
    })
  },

  // 帖子评论
  comments(postId: number) {
    return request<Comment[]>({
      url: `/post/${postId}/comments`,
      method: 'GET',
    })
  },

  // 话题列表
  topicList() {
    return request<{ id: number; name: string }[]>({
      url: '/community/topics',
      method: 'GET',
    })
  },

  // 发布帖子
  publish(params: {
    content: string
    images?: string[]
    videoUrl?: string
    topicId?: number
    visible?: 'public' | 'follow' | 'private'
    city?: string
  }) {
    return request<Post>({
      url: '/post',
      method: 'POST',
      data: params,
    })
  },

  // 点赞 / 取消点赞
  like(postId: number) {
    return request<{ liked: boolean; likeCount: number }>({
      url: `/post/${postId}/like`,
      method: 'POST',
    })
  },

  // 收藏 / 取消收藏
  collect(postId: number) {
    return request<{ collected: boolean; collectCount: number }>({
      url: `/post/${postId}/collect`,
      method: 'POST',
    })
  },

  // 删除自己发布的帖子（后端校验归属）
  deletePost(id: number) {
    return request({
      url: `/post/${id}`,
      method: 'DELETE',
    })
  },

  // 上传帖子图片（多图）
  uploadImages(files: File[]) {
    const form = new FormData()
    files.forEach((f) => form.append('files', f))
    return request<{ urls: string[] }>({
      url: '/community/upload',
      method: 'POST',
      data: form,
    })
  },

  // 评论
  comment(postId: number, content: string) {
    return request<Comment>({
      url: `/post/${postId}/comment`,
      method: 'POST',
      data: { content },
    })
  },

  // 知识文章列表
  articleList(categoryId?: number, page = 1, size = 20) {
    return request<PageResp<Article>>({
      url: '/article/list',
      method: 'GET',
      params: { categoryId, page, size },
    })
  },

  // 文章详情
  articleDetail(id: number | string) {
    return request<Article>({
      url: `/article/${id}`,
      method: 'GET',
    })
  },

  // 文章点赞 / 取消点赞
  likeArticle(id: number | string) {
    return request<{ liked: boolean; likeCount: number }>({
      url: `/article/${id}/like`,
      method: 'POST',
    })
  },

  // 文章评论列表
  articleComments(id: number | string) {
    return request<Comment[]>({
      url: `/article/${id}/comments`,
      method: 'GET',
    })
  },

  // 发表评论
  commentArticle(id: number | string, content: string) {
    return request<{ commentCount: number }>({
      url: `/article/${id}/comment`,
      method: 'POST',
      data: { content },
    })
  },

  // 文章分类
  articleCategories() {
    return request<ArticleCategory[]>({
      url: '/article/categories',
      method: 'GET',
    })
  },

  // 爱心捐助排行（排行榜 + 当前用户 mine + 档位定义）
  donateRank(limit = 20) {
    return request<{
      list: { rank: number; userId: number; nickname: string; avatar?: string; totalDonate: number }[]
      mine: { totalDonate: number; rank: number | null; claimedTiers: string[]; claimableTier: string | null }
      tiers: { tier: string; name: string; threshold: number; reward: number }[]
      totalDonors: number
      totalDonated: number
    }>({
      url: '/community/donate-rank',
      method: 'GET',
      params: { limit },
    })
  },

  // 领取爱心捐助排行奖励（幂等，按当前已达最高档位发放）
  claimDonateRank() {
    return request<{
      alreadyClaimed: boolean
      tier: string
      tierName: string
      reward: number
      balance: number
    }>({
      url: '/community/donate-rank/claim',
      method: 'POST',
    })
  },

  // 某用户的动态列表（他人主页用）
  postsByUser(userId: number, page = 1, size = 10) {
    return request<PageResp<Post>>({
      url: `/post/user/${userId}`,
      method: 'GET',
      params: { page, size },
    })
  },

  // 宠物摄影大赛：含活动 #话题 的帖子按点赞数排名（点赞数即比赛数据）
  photoContest(limit = 50) {
    return request<{
      list: {
        rank: number
        id: number
        userId: number
        userNickname: string
        userAvatar?: string
        content: string
        images?: string[]
        likeCount: number
        commentCount: number
        liked: boolean
        collected: boolean
        createdAt?: string
      }[]
      stats: { participantCount: number; postCount: number; totalLikes: number }
      mine: { participated: boolean; postCount: number; bestLikeCount: number }
    }>({
      url: '/community/contest/photo',
      method: 'GET',
      params: { limit },
    })
  },
}