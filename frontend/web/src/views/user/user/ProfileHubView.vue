<script setup lang="ts">
import { ref, computed, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/modules/user'
import { orderApi } from '@/api/modules/order'
import { appointmentApi } from '@/api/modules/appointment'
import { communityApi, type Post } from '@/api/modules/community'
import { metaApi, type VipPlan } from '@/api/modules/meta'
import { usePetStore } from '@/stores/pet'
import { useUserStore } from '@/stores/user'
import type { Pet } from '@/api/modules/pet'
import { petAgeText } from '@/api/modules/pet'
import defaultPetAvatar from '@/assets/images/profile/pet-buddy.jpg'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()

// ============ 真实用户数据 ============
const me = ref<Record<string, any> | null>(null)
const avatarUrl = ref('')
const avatarError = ref(false)
const nickname = ref('加载中…')
const bio = ref('')
const gender = ref<0 | 1 | 2>(0)
const region = ref('')
const genderLabel = computed(() => (gender.value === 1 ? '男' : gender.value === 2 ? '女' : ''))
const vipLevel = ref(0)
const vipExpireAt = ref('')
// 会员身份标签：依赖 vipLevel 与套餐表(tiers)，异步加载完成后自动更新，
// 避免 VIP 用户在 tiers 就绪前被一次性固定成「普通用户」
const vipLabel = computed(() => {
  const t = tierOf(vipLevel.value)
  return t ? t.name : '普通用户'
})

async function loadMe() {
  try {
    const u: any = await userApi.me()
    me.value = u
    nickname.value = u.nickname || '萌宠家长'
    bio.value = u.bio || '这位家长很懒,还没写简介~'
    gender.value = (Number(u.gender ?? 0) || 0) as 0 | 1 | 2
    region.value = u.region || ''
    avatarUrl.value = u.avatar || ''
    avatarError.value = false
    vipLevel.value = Number(u.vipLevel ?? 0)
    vipExpireAt.value = u.vipExpireAt || ''
    // 用户资料就绪后，顺带拉取「我发布的动态」
    await loadPosts()
  } catch (e) {
    console.error('[ProfileHub] loadMe failed:', e)
    ElMessage.error('用户数据加载失败')
  }
}
onMounted(loadMe)

// ============ 统计(粉丝/关注/获赞与收藏) ============
// clickable + tab 仅粉丝/关注需要跳转；获赞与收藏点开看明细弹窗
// 缓存原始数值，给明细弹窗用
const lastLikeCount = ref(0)
const lastFavoriteCount = ref(0)
const lastPostCount = ref(0)

const stats = ref<
  { value: string; label: string; clickable?: boolean; tab?: 'following' | 'fans' }[]
>([
  { value: '0', label: '粉丝', clickable: true, tab: 'fans' },
  { value: '0', label: '关注', clickable: true, tab: 'following' },
  { value: '0', label: '获赞与收藏', clickable: true },
])

// 点击粉丝/关注 → 跳转到关注与粉丝列表
function goList(tab?: 'following' | 'fans') {
  if (!tab) return
  router.push({ path: '/user/follow-list', query: { tab } })
}

// 「获赞与收藏」明细弹窗：缓存最近一次拿到的原始数值，避免开窗时再发请求
const breakdownVisible = ref(false)
const breakdown = ref({ postCount: 0, likeCount: 0, favoriteCount: 0 })

function openLikesAndFavorites() {
  breakdown.value = {
    postCount: lastPostCount.value,
    likeCount: lastLikeCount.value,
    favoriteCount: lastFavoriteCount.value,
  }
  breakdownVisible.value = true
}

// stat 点击路由：粉丝/关注 → 跳转列表；获赞与收藏 → 打开明细弹窗
function onStatClick(s: { tab?: 'following' | 'fans'; label: string }) {
  if (s.label === '获赞与收藏') {
    openLikesAndFavorites()
    return
  }
  goList(s.tab)
}

async function loadStats() {
  try {
    const s = await userApi.stats()
    // 缓存原始数值，给「获赞与收藏」明细弹窗用
    lastLikeCount.value = s.likeCount ?? 0
    lastFavoriteCount.value = s.favoriteCount ?? 0
    lastPostCount.value = s.postCount ?? 0
    // 保留每项自带的 clickable/tab 元数据，只更新数值
    const map: Record<string, number | undefined> = {
      粉丝: s.followerCount,
      关注: s.followingCount,
      // 获赞与收藏：点赞数 + 收藏数
      获赞与收藏: (s.likeCount ?? 0) + (s.favoriteCount ?? 0),
    }
    stats.value = stats.value.map((it) => ({ ...it, value: String(map[it.label] ?? 0) }))
  } catch (e) {
    console.error('[ProfileHub] loadStats failed:', e)
    // 失败时保持 0,不弹错误打扰用户
  }
}
onMounted(loadStats)

// ============ 我的萌宠 (动态,来自数据库) ============
const petStore = usePetStore()
const userStore = useUserStore()
const pets = computed(() => petStore.pets)

// 根据生日计算年龄描述
function petAge(birthday?: string): string {
  return petAgeText(birthday)
}

// 拼出"品种 · 年龄"描述
function petDesc(p: Pet): string {
  const parts = [p.breedName || p.categoryName || '', petAge(p.birthday)].filter(Boolean)
  return parts.join(' · ')
}

async function loadPets() {
  try {
    await petStore.fetch()
  } catch (e) {
    console.error('[ProfileHub] loadPets failed:', e)
    ElMessage.error('宠物数据加载失败')
  }
}
onMounted(loadPets)
onMounted(loadVipPlan)
onMounted(loadTodoCounts)

// 售后入口已整合进 /order/list 的「已完成」tab：用户从那里看历史订单 + 申请/查看售后。
// 个人中心快捷入口不再单独保留「我的售后」卡片，避免重复入口分散心智。

const orderCount = ref(0)
const appointmentCount = ref(0)

// 待办角标：用真实接口计数（我的订单 / 我的预约），其余无未读则保持 0
async function loadTodoCounts() {
  try {
    const r = await orderApi.listOrders(undefined, 1, 1)
    orderCount.value = r?.total ?? 0
  } catch {
    /* 不影响其余加载 */
  }
  try {
    const list = await appointmentApi.list()
    appointmentCount.value = (list || []).length
  } catch {
    /* 不影响其余加载 */
  }
}

const services = computed(() => [
  { icon: 'shopping_bag', label: '我的订单', color: '#ff7a32', to: '/order/list', badge: orderCount.value },
  { icon: 'calendar_month', label: '预约挂号', color: '#68abff', to: '/appointments', badge: appointmentCount.value },
  { icon: 'favorite', label: '我的收藏', color: '#cb9900', to: '/user/collect', badge: 0 },
  { icon: 'location_on', label: '收货地址', color: '#584238', to: '/user/address', badge: 0 },
  { icon: 'confirmation_number', label: '优惠券', color: '#faad14', to: '/coupon-center', badge: 0 },
  { icon: 'history', label: '我的足迹', color: '#7a5cff', to: '/user/footprint', badge: 0 },
])

// ============ 我发布的动态（缩略图在左） ============
const posts = ref<Post[]>([])
const POST_LIMIT = 4
// 默认最多展示 4 条（不展开）
const displayPosts = computed(() => posts.value.slice(0, POST_LIMIT))

function fmtPostTime(s?: string): string {
  return (s || '').replace('T', ' ').slice(0, 16)
}

// 缩略图取第一张图；无图时回退到品牌渐变占位
function postThumb(p: Post): string {
  return p.images && p.images.length ? p.images[0] : ''
}

async function loadPosts() {
  if (!me.value?.id) return
  try {
    const resp = await communityApi.postsByUser(me.value.id, 1, 30)
    posts.value = resp.list || []
  } catch (e) {
    console.error('[ProfileHub] loadPosts failed:', e)
    posts.value = []
  }
}

// ============ 会员等级与权益（来自 metaApi.vipPlan()）============
const tiers = ref<VipPlan[]>([])

async function loadVipPlan() {
  try {
    const data = await metaApi.vipPlan()
    tiers.value = data.plans || []
  } catch (e) {
    console.warn('[ProfileHub] vipPlan 加载失败', e)
  }
}

function tierOf(level: number): VipPlan | null {
  // 按 level 精确匹配；当前端 level 大于已有套餐时回退到最高档
  const exact = tiers.value.find((t) => t.level === level)
  if (exact) return exact
  return tiers.value.length > 0 ? tiers.value[tiers.value.length - 1] : null
}

const currentPerks = computed(() => {
  const t = tierOf(vipLevel.value)
  return t ? t.perks : []
})

function formatExpire(s?: string): string {
  if (!s) return ''
  return s.replace('T', ' ').slice(0, 10)
}

const vipOpening = ref(false)
function goEdit() {
  router.push('/user/edit')
}
// 跳转会员开通页（卡片按钮）：置 vipOpening 标记，返回本页时复位
function goVipOpen() {
  vipOpening.value = true
  router.push('/vip/open')
}
onActivated(() => {
  vipOpening.value = false
})
</script>

<template>
  <div class="profile-view">
    <div class="profile-container">
      <!-- 用户头部 -->
      <section class="user-header">
        <div class="avatar-wrap">
          <div class="avatar">
            <img
              v-if="avatarUrl && !avatarError"
              :src="avatarUrl"
              :alt="nickname + ' 的头像'"
              @error="avatarError = true"
            />
            <span v-else class="avatar-fallback">{{ (nickname || '?').slice(0, 1) }}</span>
          </div>
          <div class="vip-badge" v-if="vipLevel > 0">
            <span class="material-symbols-outlined">stars</span>
            VIP V{{ vipLevel }}
          </div>
        </div>
        <div class="user-info">
          <div class="user-name-row">
            <h1 class="user-name">{{ nickname }}</h1>
            <button class="edit-btn" @click="goEdit">编辑资料</button>
          </div>
          <p class="user-bio">{{ bio }}</p>
          <div class="user-meta" v-if="genderLabel || region">
            <span v-if="genderLabel" class="meta-item">
              <span class="material-symbols-outlined">{{ gender === 1 ? 'male' : 'female' }}</span>{{ genderLabel }}
            </span>
            <span v-if="region" class="meta-item">
              <span class="material-symbols-outlined">location_on</span>{{ region }}
            </span>
          </div>
          <div class="user-stats">
            <div
              v-for="s in stats"
              :key="s.label"
              class="stat"
              :class="{ clickable: s.clickable }"
              @click="onStatClick(s)"
            >
              <span class="stat-value">{{ s.value }}</span>
              <span class="stat-label">{{ s.label }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 会员卡（精简版） -->
      <section class="membership" :class="{ 'is-vip': vipLevel > 0 }">
        <div class="member-glow"></div>
        <div class="member-info">
          <div class="member-head">
            <span class="material-symbols-outlined member-crown">workspace_premium</span>
            <h2 class="member-title">PetMate 会员中心</h2>
          </div>
          <p class="member-status">
            <template v-if="vipLevel > 0">
              当前身份：{{ vipLabel }}（V{{ vipLevel }}） | {{ formatExpire(vipExpireAt) }} 到期
            </template>
            <template v-else>
              尚未开通会员，即刻享受专属权益与会员折扣
            </template>
          </p>
          <div class="member-chips">
            <span v-for="(p, i) in currentPerks.slice(0, 2)" :key="i" class="member-chip">
              <span class="material-symbols-outlined">check_circle</span>
              {{ p }}
            </span>
          </div>
        </div>
        <button class="member-btn" :disabled="vipOpening" @click="goVipOpen">
          {{ vipLevel > 0 ? '立即续费 / 升级' : '立即开通' }}
        </button>
      </section>

      <!-- 主体布局 -->
      <div class="content-layout">
        <div class="left-col">
          <!-- 我的萌宠 -->
          <PageSection class="pets-section" title="我的萌宠" icon="pets">
            <template #actions>
              <button class="add-pet" @click="router.push('/pet/create')">
                <span class="material-symbols-outlined">add_circle</span>
                添加宠物
              </button>
            </template>
            <div class="pets-scroll">
              <div
                v-for="p in pets"
                :key="p.id"
                class="pet-card"
                @click="router.push(`/pet/detail/${p.id}`)"
              >
                <div class="pet-avatar">
                  <img :src="p.avatar || defaultPetAvatar" :alt="p.name" />
                </div>
                <span class="pet-name">{{ p.name }}</span>
                <span class="pet-desc">{{ petDesc(p) }}</span>
              </div>
              <div v-if="!petStore.loading && pets.length === 0" class="pets-empty">
                还没有添加毛孩子，点击右上角「添加宠物」吧~
              </div>
            </div>
          </PageSection>

          <!-- 快捷服务 -->
          <PageSection class="services-section" title="快捷服务" icon="apps">
            <div class="services-grid">
              <a
                v-for="s in services"
                :key="s.label"
                class="service-item"
                @click="router.push(s.to)"
              >
                <span class="service-icon-wrap">
                  <span class="material-symbols-outlined" :style="{ color: s.color }">{{ s.icon }}</span>
                  <em v-if="s.badge > 0" class="service-badge">{{ s.badge > 99 ? '99+' : s.badge }}</em>
                </span>
                <span class="service-label">{{ s.label }}</span>
              </a>
            </div>
          </PageSection>
        </div>

        <!-- 发布的动态 -->
        <aside class="right-col">
          <PageSection class="post-card" title="发布的动态" icon="dynamic_feed">
            <template #actions>
              <span class="post-count" v-if="posts.length">{{ posts.length }} 条</span>
              <span v-else class="material-symbols-outlined">more_horiz</span>
            </template>
            <div class="post-list">
              <PetEmptyState
                v-if="!posts.length"
                icon="post_add"
                title="还没有发布过动态"
              />
              <div
                v-for="p in displayPosts"
                v-else
                :key="p.id"
                class="post-item"
                @click="router.push(`/community/post/${p.id}`)"
              >
                <div class="post-thumb">
                  <img
                    v-if="postThumb(p)"
                    :src="postThumb(p)"
                    :alt="p.content || '动态'"
                    @error="(e:any)=>e.target.style.display='none'"
                  />
                  <span v-else class="material-symbols-outlined post-thumb-ph">pets</span>
                </div>
                <div class="post-body">
                  <h4 class="post-text">{{ p.content }}</h4>
                  <div class="post-meta">
                    <span class="post-time">{{ fmtPostTime(p.createdAt) }}</span>
                    <span class="post-stat">
                      <span class="material-symbols-outlined">favorite</span>{{ p.likeCount }}
                    </span>
                    <span class="post-stat">
                      <span class="material-symbols-outlined">chat_bubble</span>{{ p.commentCount }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
            <button class="post-more" @click="router.push('/user/posts')">查看全部</button>
          </PageSection>
        </aside>
      </div>
    </div>

    <!-- 「获赞与收藏」明细弹窗 -->
    <el-dialog
      v-model="breakdownVisible"
      title="获赞与收藏明细"
      width="320px"
      align-center
      :show-close="true"
      class="breakdown-dialog"
    >
      <ul class="breakdown-list">
        <li class="breakdown-item">
          <span class="material-symbols-outlined">dynamic_feed</span>
          <span class="breakdown-label">动态</span>
          <span class="breakdown-value">{{ breakdown.postCount }}</span>
        </li>
        <li class="breakdown-item">
          <span class="material-symbols-outlined">favorite</span>
          <span class="breakdown-label">点赞</span>
          <span class="breakdown-value">{{ breakdown.likeCount }}</span>
        </li>
        <li class="breakdown-item">
          <span class="material-symbols-outlined">bookmark</span>
          <span class="breakdown-label">收藏</span>
          <span class="breakdown-value">{{ breakdown.favoriteCount }}</span>
        </li>
      </ul>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.profile-view {
  min-height: 100vh;
  background: $color-background;
}

.profile-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
}

// ============ 用户头部 ============
.user-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xl;
  margin-bottom: $spacing-2xl;

  @include desktop {
    flex-direction: row;
    align-items: flex-start;
  }
}

.avatar-wrap {
  position: relative;
  flex-shrink: 0;
}

.avatar {
  width: 128px;
  height: 128px;
  border-radius: $radius-full;
  border: 4px solid #ffffff;
  box-shadow: $shadow-lg;
  overflow: hidden;

  @include desktop {
    width: 160px;
    height: 160px;
  }

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: #ffffff;
  background: linear-gradient(135deg, $color-brand 0%, #ff9c5b 100%);
}

.vip-badge {
  position: absolute;
  bottom: 8px;
  right: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px $spacing-sm;
  background: $color-tertiary-fixed;
  color: #5b4300;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;
  box-shadow: $shadow-md;

  .material-symbols-outlined {
    font-size: 16px;
    font-variation-settings: 'FILL' 1;
  }
}

.user-info {
  flex: 1;
  text-align: center;

  @include desktop {
    text-align: left;
  }
}

.user-name-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-xs;

  @include desktop {
    flex-direction: row;
    align-items: center;
  }
}

.user-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.edit-btn {
  padding: $spacing-xs $spacing-lg;
  background: $color-brand-dark;
  color: #ffffff;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: background $transition-fast;

  &:hover {
    background: #7b2f00;
  }
}

.user-bio {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-md;
}

.user-meta {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: $radius-full;
  background: $color-surface-container-low;
  color: $color-on-surface-variant;
  font-size: $font-size-label-sm;

  .material-symbols-outlined {
    font-size: 15px;
  }
}

.user-stats {
  display: flex;
  justify-content: center;
  gap: $spacing-xl;

  @include desktop {
    justify-content: flex-start;
  }
}

.stat {
  text-align: center;

  @include desktop {
    text-align: left;
  }

  .stat-value {
    display: block;
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 600;
    color: $color-on-surface;
  }

  .stat-label {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    letter-spacing: 0.05em;
  }

  &.clickable {
    cursor: pointer;

    .stat-value,
    .stat-label {
      transition: color $transition-fast;
    }

    &:hover {
      .stat-value,
      .stat-label {
        color: $color-brand;
      }
    }
  }
}

// ============ 会员卡 ============
.membership {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-lg;
  padding: $spacing-xl;
  margin-bottom: $spacing-2xl;
  background: linear-gradient(135deg, #2b3234, #161d1f);
  border-radius: $radius-lg;
  color: #ffffff;
  box-shadow: $shadow-lg;
  overflow: hidden;

  @include desktop {
    flex-direction: row;
    padding: $spacing-xl $spacing-2xl;
  }
}

.member-glow {
  position: absolute;
  right: -80px;
  bottom: -80px;
  width: 256px;
  height: 256px;
  background: rgba(255, 122, 50, 0.2);
  border-radius: $radius-full;
  filter: blur(64px);
}


.member-info {
  position: relative;
  z-index: 10;
}

.member-head {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;

  .member-crown {
    color: $color-tertiary-fixed;
    font-variation-settings: 'FILL' 1;
  }
}

.member-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
}

.member-status {
  font-size: $font-size-body-md;
  color: $color-surface-variant;
  opacity: 0.9;
  margin-bottom: $spacing-md;
}

.member-perks {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-md;
}

.perk {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 4px $spacing-sm;
  background: rgba(255, 255, 255, 0.1);
  border-radius: $radius-default;
  font-size: $font-size-label-sm;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-tertiary-fixed;
  }
}

.member-btn {
  position: relative;
  z-index: 10;
  padding: $spacing-sm $spacing-xl;
  background: $color-tertiary-fixed;
  color: #5b4300;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  box-shadow: $shadow-lg;
  transition: background $transition-fast;

  &:hover {
    background: $color-tertiary-container;
  }

  &:active {
    transform: scale(0.95);
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
    transform: none;
  }
}

// ============ 会员卡增强：等级阶梯 + 有效期 ============
.member-chips {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-md;
  margin-top: $spacing-md;
}

.member-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px $spacing-md;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: $radius-full;
  color: rgba(255, 255, 255, 0.92);
  font-size: $font-size-label-sm;
  font-weight: 500;
  backdrop-filter: blur(8px);

  .material-symbols-outlined {
    font-size: 16px;
    color: $color-tertiary-fixed;
    font-variation-settings: 'FILL' 1;
  }
}

// ============ 主体 ============
.content-layout {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-xl;

  @include desktop {
    grid-template-columns: 2fr 1fr;
  }
}

.left-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-2xl;
  min-width: 0;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
}

.section-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;

  .material-symbols-outlined {
    color: $color-brand;
  }

  &.plain {
    margin-bottom: $spacing-lg;
  }
}

.add-pet {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px 16px;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, $color-brand 0%, #ff9c5b 100%);
  border: none;
  border-radius: 999px;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(255, 122, 50, 0.28);
  transition: transform 0.22s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.22s ease,
    filter 0.22s ease;

  .material-symbols-outlined {
    font-size: 20px;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 8px 20px rgba(255, 122, 50, 0.38);
    filter: brightness(1.03);
  }

  &:active {
    transform: translateY(0);
    box-shadow: 0 3px 10px rgba(255, 122, 50, 0.3);
  }
}

.pets-scroll {
  display: flex;
  gap: $spacing-md;
  overflow-x: auto;
  padding-bottom: $spacing-md;
  @include scrollbar-hide;
}

.pets-empty {
  display: flex;
  align-items: center;
  padding: $spacing-lg;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
}

.pet-card {
  min-width: 160px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-md;
  background: #ffffff;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.3);
  cursor: pointer;
  transition: box-shadow $transition-base;

  @include desktop {
    min-width: 180px;
  }

  &:hover {
    box-shadow: $shadow-md;

    .pet-avatar {
      border-color: $color-brand;
    }
  }
}

.pet-avatar {
  width: 80px;
  height: 80px;
  border-radius: $radius-full;
  overflow: hidden;
  margin-bottom: $spacing-sm;
  border: 2px solid rgba(255, 122, 50, 0.2);
  transition: border-color $transition-fast;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.pet-name {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.pet-desc {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

// 快捷服务
.service-icon-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

// 进行中售后角标：小红点数字，贴在图标右上
.service-badge {
  position: absolute;
  top: -6px;
  right: -12px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: $color-emergency;
  color: #fff;
  font-size: 11px;
  font-style: normal;
  font-weight: 600;
  line-height: 18px;
  text-align: center;
  box-shadow: 0 2px 6px rgba(255, 77, 79, 0.4);
}

.services-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: $spacing-md;

  @include tablet {
    grid-template-columns: repeat(3, 1fr);
  }

  @include desktop {
    grid-template-columns: repeat(3, 1fr);
  }
}

.service-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  padding: $spacing-lg;
  background: $color-surface-container-low;
  border-radius: $radius-lg;
  border: 1px solid rgba(223, 192, 179, 0.2);
  cursor: pointer;
  transition: background $transition-base;

  &:hover {
    background: rgba(255, 219, 204, 0.2);
  }

  .material-symbols-outlined {
    font-size: 36px;
  }

  .service-label {
    font-size: $font-size-label-md;
    font-weight: 600;
    color: $color-on-surface;
  }
}

// ============ 发布的动态 ============
.right-col {
  min-width: 0;
}

.post-card {
  padding: $spacing-lg;
  background: #ffffff;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.3);
  position: sticky;
  top: 96px;
}

.post-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-lg;

  .material-symbols-outlined {
    color: $color-on-surface-variant;
    cursor: pointer;
  }

  .post-count {
    font-size: $font-size-label-sm;
    font-weight: 500;
    color: $color-text-tertiary;
  }
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.post-empty {
  padding: $spacing-lg;
  text-align: center;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
  background: $color-surface-container-low;
  border-radius: $radius-md;
}

.post-item {
  display: flex;
  gap: $spacing-md;
  padding: $spacing-sm;
  border-radius: $radius-md;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container-low;

    .post-thumb img {
      transform: scale(1.06);
    }
  }
}

.post-thumb {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: $radius-default;
  overflow: hidden;
  background: linear-gradient(135deg, $color-brand-container 0%, $color-surface-container 100%);
  display: flex;
  align-items: center;
  justify-content: center;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform $transition-base;
  }

  .post-thumb-ph {
    font-size: 32px;
    color: $color-brand;
    font-variation-settings: 'FILL' 1;
  }
}

.post-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.post-text {
  margin: 0 0 4px;
  font-size: $font-size-label-md;
  font-weight: 500;
  color: $color-on-surface;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-meta {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;

  .post-stat {
    display: inline-flex;
    align-items: center;
    gap: 2px;

    .material-symbols-outlined {
      font-size: 14px;
    }
  }
}

.post-time {
  margin-right: auto;
}

.post-more {
  width: 100%;
  margin-top: $spacing-lg;
  padding: $spacing-sm;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  color: $color-on-surface;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container-low;
  }
}

// ============ 「获赞与收藏」明细弹窗 ============
.breakdown-dialog {
  .breakdown-list {
    list-style: none;
    padding: 0;
    margin: 0;
  }

  .breakdown-item {
    display: flex;
    align-items: center;
    gap: $spacing-sm;
    padding: $spacing-sm 0;

    .material-symbols-outlined {
      font-size: 22px;
      color: $color-brand;
    }

    .breakdown-label {
      flex: 1;
      font-size: $font-size-body-md;
      color: $color-on-surface;
    }

    .breakdown-value {
      font-family: $font-family-headline;
      font-size: $font-size-body-lg;
      font-weight: 600;
      color: $color-on-surface;
    }
  }
}
@include mobile {
  .profile-container {
    padding-top: 1rem;
    padding-bottom: 1.5rem;
  }
  .user-header {
    gap: 1rem;
    margin-bottom: 1.5rem;
  }
  .avatar {
    width: 7.5rem;
    height: 7.5rem;
  }
  .vip-badge {
    bottom: 0.5rem;
    right: 0.5rem;
    padding: 0.25rem 0.5rem;
  }
  .edit-btn {
    padding: 0.375rem 1rem;
  }
  .user-meta {
    gap: 0.5rem;
    margin-bottom: 1rem;
  }
  .user-stats {
    gap: 1.25rem;
  }
  .membership {
    flex-direction: column;
    align-items: stretch;
    gap: 0.75rem;
    padding: 1rem;
    margin-bottom: 1.5rem;
  }
  .member-perks {
    gap: 0.5rem;
  }
  .perk {
    padding: 0.25rem 0.5rem;
  }
  .member-btn {
    width: 100%;
    padding: 0.625rem 1rem;
  }
  .member-chips {
    gap: 0.5rem;
    margin-top: 0.75rem;
  }
  .member-chip {
    padding: 0.375rem 0.75rem;
  }
  .content-layout {
    gap: 1rem;
  }
  .left-col {
    gap: 1.25rem;
  }
  .section-head {
    margin-bottom: 1rem;
  }
  .pets-scroll {
    gap: 0.75rem;
    padding-bottom: 0.75rem;
  }
  .pet-card {
    min-width: 10rem;
    padding: 0.75rem;
  }
  .pet-avatar {
    width: 5rem;
    height: 5rem;
  }
  .services-grid {
    gap: 0.5rem;
  }
  .service-item {
    padding: 0.75rem;
  }
  .post-card {
    position: static;
    top: auto;
    padding: 1rem;
  }
  .post-item {
    gap: 0.5rem;
    padding: 0.5rem;
  }
  .post-thumb {
    width: 4.5rem;
    height: 4.5rem;
  }
  .post-more {
    margin-top: 1rem;
    padding: 0.5rem;
  }
}

</style>
