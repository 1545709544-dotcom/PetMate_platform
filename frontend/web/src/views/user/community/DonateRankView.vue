<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { communityApi } from '@/api/modules/community'
import { useUserStore } from '@/stores/user'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'

const router = useRouter()
const userStore = useUserStore()

interface RankItem {
  rank: number
  userId: number
  nickname: string
  avatar?: string
  totalDonate: number
}
interface Tier {
  tier: string
  name: string
  threshold: number
  reward: number
}
interface Mine {
  totalDonate: number
  rank: number | null
  claimedTiers: string[]
  claimableTier: string | null
}

const loading = ref(false)
const list = ref<RankItem[]>([])
const tiers = ref<Tier[]>([])
const mine = ref<Mine | null>(null)
const totalDonors = ref(0)
const totalDonated = ref(0)
const claiming = ref(false)

const tierMap = computed<Record<string, Tier>>(() => {
  const m: Record<string, Tier> = {}
  tiers.value.forEach((t) => (m[t.tier] = t))
  return m
})

const podium = computed(() => list.value.slice(0, 3))
const rest = computed(() => list.value.slice(3))
const isLogin = computed(() => userStore.isLoggedIn)

// 当前用户是否还有可领取档位
const claimable = computed(() => {
  if (!mine.value || !mine.value.claimableTier) return null
  if (mine.value.claimedTiers.includes(mine.value.claimableTier)) return null
  return tierMap.value[mine.value.claimableTier] || null
})

async function load() {
  loading.value = true
  try {
    const res = await communityApi.donateRank(20)
    list.value = (res?.list || []).map((x) => ({
      ...x,
      totalDonate: Number(x.totalDonate || 0),
    }))
    tiers.value = res?.tiers || []
    mine.value = res?.mine ? (res.mine as Mine) : null
    totalDonors.value = res?.totalDonors || 0
    totalDonated.value = Number(res?.totalDonated || 0)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

async function claim() {
  if (!isLogin.value) {
    ElMessage.warning('请先登录后再领取奖励~')
    router.push({ name: 'Login' })
    return
  }
  if (!claimable.value) return
  claiming.value = true
  try {
    const res = await communityApi.claimDonateRank()
    if (res?.alreadyClaimed) {
      ElMessage.info('该档位奖励已领取过啦~')
    } else {
      ElMessage.success(`领取成功！获得 ${res?.reward} 鱼干 🐟`)
      await userStore.fetchUser()
    }
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '领取失败')
  } finally {
    claiming.value = false
  }
}

function avatarText(name?: string) {
  return name ? name.slice(0, 1) : '?'
}

// 档位 medal 渐变（前端展示用）
const tierGradient: Record<string, string> = {
  diamond: 'linear-gradient(135deg,#7f7fd5,#86a8e7,#91eae4)',
  gold: 'linear-gradient(135deg,#f9d423,#ff7a32)',
  silver: 'linear-gradient(135deg,#bdc3c7,#e8edf0)',
  bronze: 'linear-gradient(135deg,#cd7f32,#e6a86b)',
}
</script>

<template>
  <div class="dr-view">
    <!-- 顶部 hero -->
    <header class="dr-hero">
      <div class="dr-hero-inner">
        <div class="dr-hero-icon">
          <span class="material-symbols-outlined">favorite</span>
        </div>
        <div class="dr-hero-text">
          <h1 class="dr-title">爱心捐助排行</h1>
          <p class="dr-sub">每一份捐赠都在为毛孩子点亮希望 · 捐助越多，奖励越丰厚</p>
        </div>
        <div class="dr-hero-stats">
          <div class="dr-stat">
            <div class="dr-stat-num">¥{{ totalDonated.toFixed(2) }}</div>
            <div class="dr-stat-label">累计爱心</div>
          </div>
          <div class="dr-stat">
            <div class="dr-stat-num">{{ totalDonors }}</div>
            <div class="dr-stat-label">爱心伙伴</div>
          </div>
        </div>
      </div>
    </header>

    <div class="dr-container">
      <div v-if="loading" class="dr-loading">加载中...</div>

      <template v-else>
        <!-- 我的爱心面板 -->
        <section v-if="isLogin" class="dr-mine" :class="{ 'dr-mine-claim': claimable }">
          <div class="dr-mine-info">
            <span class="material-symbols-outlined dr-mine-heart">volunteer_activism</span>
            <div>
              <div class="dr-mine-line">
                我的排名：<b>{{ mine?.rank ?? '—' }}</b>
                <span class="dr-mine-sep">·</span>
                累计捐赠 <b>¥{{ (mine?.totalDonate || 0).toFixed(2) }}</b>
              </div>
              <div class="dr-mine-tip" v-if="!mine?.rank">还没有捐赠记录，去结算页勾选「爱心捐赠」奉献一份力吧~</div>
              <div class="dr-mine-tip" v-else-if="!claimable">已领取的档位：{{ mine?.claimedTiers?.length ? mine!.claimedTiers.map(t => tierMap[t]?.name).join('、') : '无' }}</div>
            </div>
          </div>
          <button v-if="claimable" class="dr-claim-btn" :disabled="claiming" @click="claim">
            <span class="material-symbols-outlined">redeem</span>
            {{ claiming ? '领取中' : `领取 ${claimable.reward} 鱼干` }}
          </button>
        </section>

        <!-- 领奖台 Top3 -->
        <section v-if="podium.length" class="dr-podium">
          <div
            v-for="p in [podium[1], podium[0], podium[2]].filter(Boolean)"
            :key="p.userId"
            class="dr-pod"
            :class="['dr-pod-' + p.rank]"
          >
            <div class="dr-pod-avatar" :style="{ background: tierGradient[p.rank === 1 ? 'gold' : p.rank === 2 ? 'silver' : 'bronze'] }">
              <img v-if="p.avatar" :src="p.avatar" :alt="p.nickname" />
              <span v-else>{{ avatarText(p.nickname) }}</span>
              <div class="dr-pod-medal">
                <span class="material-symbols-outlined">
                  {{ p.rank === 1 ? 'military_tech' : p.rank === 2 ? 'workspace_premium' : 'emoji_events' }}
                </span>
              </div>
            </div>
            <div class="dr-pod-name">{{ p.nickname }}</div>
            <div class="dr-pod-amt">¥{{ p.totalDonate.toFixed(2) }}</div>
            <div class="dr-pod-rank">No.{{ p.rank }}</div>
          </div>
        </section>

        <!-- 完整榜单 -->
        <PageSection class="dr-list" title="爱心榜单" icon="leaderboard">
          <PetEmptyState
            v-if="!list.length"
            icon="pets"
            title="还没有人参与爱心捐赠"
            desc="成为第一个点亮榜单的人吧~"
          />
          <div v-else class="dr-list-rows">
            <div v-for="p in list" :key="p.userId" class="dr-row" :class="{ 'dr-row-me': mine && p.userId === userStore.userId }">
              <div class="dr-row-rank" :class="{ 'dr-row-rank-top': p.rank <= 3 }">{{ p.rank }}</div>
              <div class="dr-row-avatar">
                <img v-if="p.avatar" :src="p.avatar" :alt="p.nickname" />
                <span v-else>{{ avatarText(p.nickname) }}</span>
              </div>
              <div class="dr-row-name">{{ p.nickname }}<span v-if="mine && p.userId === userStore.userId" class="dr-row-me-tag">我</span></div>
              <div class="dr-row-amt">¥{{ p.totalDonate.toFixed(2) }}</div>
            </div>
          </div>
        </PageSection>

        <!-- 奖励档位 -->
        <PageSection class="dr-tiers" title="奖励档位" icon="redeem">
          <div class="dr-tier-grid">
            <div
              v-for="t in tiers"
              :key="t.tier"
              class="dr-tier pet-card"
              :class="['dr-tier-' + t.tier, { 'dr-tier-claimed': mine?.claimedTiers?.includes(t.tier) }]"
              :style="{ '--tier-grad': tierGradient[t.tier] }"
            >
              <div class="dr-tier-badge">
                <span class="material-symbols-outlined">favorite</span>
              </div>
              <div class="dr-tier-name">{{ t.name }}</div>
              <div class="dr-tier-cond">累计捐赠 ≥ ¥{{ t.threshold }}</div>
              <div class="dr-tier-reward">{{ t.reward }} <small>鱼干</small> 🐟</div>
              <div v-if="mine?.claimedTiers?.includes(t.tier)" class="dr-tier-flag">
                <span class="material-symbols-outlined">check_circle</span> 已领取
              </div>
            </div>
          </div>
          <p class="dr-tier-note">奖励按已达到的最高档位一次性发放，每档限领一次；鱼干可在结算时抵扣现金。</p>
        </PageSection>
      </template>
    </div>
  </div>
</template>

<style lang="scss" scoped>
.dr-view {
  min-height: 100vh;
  background: $color-background;
}

// ============ Hero ============
.dr-hero {
  background: linear-gradient(135deg, #ff7a32 0%, #ff4d4f 100%);
  color: #fff;
  padding: $spacing-2xl 0 $spacing-xl;
}
.dr-hero-inner {
  @include container-max;
  display: flex;
  align-items: center;
  gap: $spacing-lg;
  flex-wrap: wrap;
}
.dr-hero-icon {
  width: 64px;
  height: 64px;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.18);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  .material-symbols-outlined {
    font-size: 38px;
    color: #fff;
  }
}
.dr-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  margin: 0 0 4px;
}
.dr-sub {
  font-size: $font-size-label-md;
  opacity: 0.92;
  margin: 0;
}
.dr-hero-stats {
  margin-left: auto;
  display: flex;
  gap: $spacing-xl;
}
.dr-stat {
  text-align: center;
}
.dr-stat-num {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 800;
}
.dr-stat-label {
  font-size: $font-size-label-sm;
  opacity: 0.9;
}

// ============ 容器 ============
.dr-container {
  @include container-max;
  padding: $spacing-xl 0 $spacing-3xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-xl;
}
.dr-loading {
  text-align: center;
  padding: $spacing-2xl;
  color: $color-on-surface-variant;
}
.dr-section-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0 0 $spacing-md;
}

// ============ 我的爱心 ============
.dr-mine {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  flex-wrap: wrap;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
  &.dr-mine-claim {
    border-color: $color-brand;
    box-shadow: 0 4px 16px rgba(255, 122, 50, 0.18);
  }
}
.dr-mine-info {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}
.dr-mine-heart {
  font-size: 34px;
  color: $color-emergency;
}
.dr-mine-line {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  b { color: $color-brand; }
}
.dr-mine-sep {
  margin: 0 6px;
  color: $color-on-surface-variant;
}
.dr-mine-tip {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 4px;
}
.dr-claim-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: $spacing-sm $spacing-lg;
  background: linear-gradient(135deg, #ff7a32, #ff4d4f);
  color: #fff;
  border: none;
  border-radius: $radius-full;
  font-weight: 700;
  font-size: $font-size-label-md;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: transform $transition-fast;
  .material-symbols-outlined { font-size: 20px; }
  &:hover:not(:disabled) { transform: scale(1.05); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}

// ============ 领奖台 ============
.dr-podium {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-md;
  align-items: end;
}
.dr-pod {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-lg $spacing-md $spacing-md;
  text-align: center;
  border: 1px solid rgba(223, 192, 179, 0.2);
  transition: transform $transition-fast;
  &:hover { transform: translateY(-4px); }
}
.dr-pod-1 { order: 2; transform: translateY(-12px); }
.dr-pod-2 { order: 1; }
.dr-pod-3 { order: 3; }
.dr-pod-avatar {
  position: relative;
  width: 72px;
  height: 72px;
  border-radius: $radius-full;
  margin: 0 auto $spacing-sm;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: $font-size-headline-md;
  font-weight: 800;
  overflow: visible;
  img { width: 100%; height: 100%; border-radius: $radius-full; object-fit: cover; }
}
.dr-pod-medal {
  position: absolute;
  right: -6px;
  bottom: -6px;
  width: 28px;
  height: 28px;
  border-radius: $radius-full;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-sm;
  .material-symbols-outlined { font-size: 20px; color: #ffb300; }
  .dr-pod-2 & .material-symbols-outlined { color: #9aa6b2; }
  .dr-pod-3 & .material-symbols-outlined { color: #cd7f32; }
}
.dr-pod-name {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}
.dr-pod-amt {
  font-size: $font-size-body-md;
  font-weight: 800;
  color: $color-brand;
  margin-top: 2px;
}
.dr-pod-rank {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: 2px;
}

// ============ 榜单 ============
.dr-list-rows {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}
.dr-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid transparent;
  transition: border-color $transition-fast;
  &:hover { border-color: rgba(223, 192, 179, 0.4); }
  &.dr-row-me {
    border-color: $color-brand;
    background: $color-brand-container;
  }
}
.dr-row-rank {
  width: 28px;
  text-align: center;
  font-weight: 800;
  color: $color-on-surface-variant;
  font-family: $font-family-headline;
  &.dr-row-rank-top { color: $color-brand; }
}
.dr-row-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  background: $color-tertiary-fixed;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-tertiary;
  font-weight: 700;
  overflow: hidden;
  img { width: 100%; height: 100%; object-fit: cover; }
}
.dr-row-name {
  flex: 1;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
  display: flex;
  align-items: center;
  gap: 6px;
}
.dr-row-me-tag {
  font-size: 10px;
  background: $color-brand;
  color: #fff;
  border-radius: $radius-full;
  padding: 1px 7px;
}
.dr-row-amt {
  font-weight: 800;
  color: $color-brand;
}
.dr-empty {
  text-align: center;
  padding: $spacing-2xl;
  color: $color-on-surface-variant;
  .material-symbols-outlined { font-size: 44px; opacity: 0.5; }
  p { margin: $spacing-sm 0 0; }
}

// ============ 档位 ============
.dr-tier-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: $spacing-md;
}
.dr-tier {
  position: relative;
  padding: $spacing-lg $spacing-md;
  border-radius: $radius-md;
  background: $color-surface-container-lowest;
  border: 1px solid rgba(223, 192, 179, 0.2);
  text-align: center;
  box-shadow: $shadow-sm;
  overflow: hidden;
  &::before {
    content: '';
    position: absolute;
    inset: 0 0 auto 0;
    height: 4px;
    background: var(--tier-grad);
  }
  &.dr-tier-claimed { opacity: 0.78; }
}
.dr-tier-badge {
  width: 44px;
  height: 44px;
  border-radius: $radius-full;
  margin: 0 auto $spacing-sm;
  background: var(--tier-grad);
  display: flex;
  align-items: center;
  justify-content: center;
  .material-symbols-outlined { color: #fff; font-size: 26px; }
}
.dr-tier-name {
  font-weight: 700;
  color: $color-on-surface;
  font-size: $font-size-label-md;
}
.dr-tier-cond {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin: 4px 0;
}
.dr-tier-reward {
  font-family: $font-family-headline;
  font-weight: 800;
  font-size: $font-size-headline-md;
  color: $color-brand;
  small { font-size: $font-size-label-sm; font-weight: 600; }
}
.dr-tier-flag {
  margin-top: $spacing-xs;
  font-size: $font-size-label-sm;
  color: $color-success;
  display: inline-flex;
  align-items: center;
  gap: 2px;
  .material-symbols-outlined { font-size: 16px; }
}
.dr-tier-note {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-top: $spacing-md;
  text-align: center;
}

// ============ 响应式 ============
@include desktop {
}
@media (max-width: 1024px) {
  .dr-tier-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 768px) {
  .dr-hero-stats { margin-left: 0; width: 100%; justify-content: space-around; }
  .dr-podium { grid-template-columns: 1fr; }
  .dr-pod-1 { order: 1; transform: none; }
  .dr-pod-2 { order: 2; }
  .dr-pod-3 { order: 3; }
  .dr-tier-grid { grid-template-columns: 1fr; }
}
</style>
