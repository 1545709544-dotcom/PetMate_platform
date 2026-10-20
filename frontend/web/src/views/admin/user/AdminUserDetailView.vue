<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '@/api/modules/admin'

const route = useRoute()
const router = useRouter()
const userId = Number(route.params.id)

const loading = ref(false)
const user = ref<Record<string, any> | null>(null)

const STATUS_MAP: Record<number, { label: string; cls: string }> = {
  0: { label: '正常', cls: 'normal' },
  1: { label: '封禁中', cls: 'banned' },
  2: { label: '已注销', cls: 'banned' },
}

// VIP 等级映射（与 UserManageView 保持一致：0=普通, 1=银卡, 2=金卡, 3=铂金）
const VIP_MAP: Record<number, { label: string; color: string }> = {
  0: { label: '普通用户', color: 'gray' },
  1: { label: '银卡会员', color: 'silver' },
  2: { label: '金卡会员', color: 'gold' },
  3: { label: '铂金会员', color: 'platinum' },
}

/** 切片 YYYY-MM-DD，兼容 "2027-01-15T..." 与 "2027-01-15 ..." */
function pickDate(iso?: string | null): string {
  if (!iso) return ''
  const s = String(iso).slice(0, 10)
  return /^\d{4}-\d{2}-\d{2}$/.test(s) ? s : ''
}

const vipInfo = computed(() => {
  if (!user.value) return null
  const rawLevel = Number(user.value.vipLevel) || 0
  const date = pickDate(user.value.vipExpireAt)
  let expired = false
  let daysLeft: number | null = null
  if (date) {
    const endTs = new Date(date + 'T23:59:59').getTime()
    daysLeft = Math.ceil((endTs - Date.now()) / 86400000)
    expired = rawLevel > 0 && daysLeft < 0
  }
  const effective = expired ? 0 : rawLevel
  return {
    rawLevel,
    effectiveLevel: effective,
    label: VIP_MAP[effective]?.label || `L${effective}`,
    color: VIP_MAP[effective]?.color || 'gray',
    expired,
    date,
    daysLeft,
  }
})

async function load() {
  loading.value = true
  try {
    const data = await adminApi.userDetail(userId)
    user.value = data as Record<string, any>
  } catch (e: any) {
    ElMessage.error(e?.message || '加载用户详情失败')
  } finally {
    loading.value = false
  }
}

async function toggleBan() {
  if (!user.value) return
  const willBan = user.value.status !== 1
  try {
    await ElMessageBox.confirm(
      `确定要${willBan ? '封禁' : '解封'}该用户吗？`,
      '操作确认',
      { type: willBan ? 'warning' : 'success' },
    )
    await adminApi.banUser(userId, willBan)
    user.value.status = willBan ? 1 : 0
    ElMessage.success(`${willBan ? '封禁' : '解封'}成功`)
  } catch (e: any) {
    if (e === 'cancel' || e === 'close') return
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(load)
</script>

<template>
  <div class="user-detail-view">
    <div class="page-head">
      <div>
        <button class="back-btn" @click="router.back()">
          <span class="material-symbols-outlined">arrow_back</span> 返回
        </button>
        <h1 class="page-title">用户详情</h1>
        <p class="page-sub">ID: {{ userId }}</p>
      </div>
      <button v-if="user && user.status !== 2" class="btn-primary" @click="toggleBan">
        <span class="material-symbols-outlined">{{ user.status === 1 ? 'lock_open' : 'lock' }}</span>
        {{ user.status === 1 ? '解封用户' : '封禁用户' }}
      </button>
    </div>

    <div v-if="loading" class="empty-tip">加载中...</div>
    <div v-else-if="!user" class="empty-tip">未找到该用户</div>
    <template v-else>
      <!-- 基础资料 -->
      <section class="card profile-card">
        <img :src="user.avatar" :alt="user.nickname" class="avatar" />
        <div class="profile-main">
          <div class="profile-name">
            {{ user.nickname }}
            <span class="status-pill" :class="STATUS_MAP[user.status]?.cls">
              <span class="dot"></span>{{ STATUS_MAP[user.status]?.label || '未知' }}
            </span>
          </div>
          <div class="profile-meta">
            <span>手机号：{{ user.phone || '—' }}</span>
            <span v-if="vipInfo">
              会员：
              <span class="vip-badge" :class="[vipInfo.color, { expired: vipInfo.expired }]">
                <span class="material-symbols-outlined">stars</span>
                {{ vipInfo.label }}
                <span v-if="vipInfo.expired" class="vip-tag-expired">已过期</span>
              </span>
              <template v-if="vipInfo.expired">
                （已于 {{ vipInfo.date }} 到期）
              </template>
              <template v-else-if="vipInfo.effectiveLevel > 0 && vipInfo.daysLeft != null">
                <template v-if="vipInfo.daysLeft <= 30">（剩 {{ vipInfo.daysLeft }} 天到期）</template>
                <template v-else>（到期 {{ vipInfo.date }}）</template>
              </template>
            </span>
            <span>注册：{{ user.registerDate || '—' }}</span>
            <span>最近登录：{{ user.lastLoginAt || '—' }}</span>
          </div>
          <p v-if="user.bio" class="profile-bio">{{ user.bio }}</p>
        </div>
      </section>

      <!-- 统计 -->
      <div class="kpi-grid">
        <div class="kpi-card">
          <div class="kpi-icon bg-blue"><span class="material-symbols-outlined">receipt_long</span></div>
          <p class="kpi-label">订单数</p>
          <h3 class="kpi-value">{{ user.orderCount ?? 0 }}</h3>
        </div>
        <div class="kpi-card">
          <div class="kpi-icon bg-orange"><span class="material-symbols-outlined">forum</span></div>
          <p class="kpi-label">动态数</p>
          <h3 class="kpi-value">{{ user.postCount ?? 0 }}</h3>
        </div>
        <div class="kpi-card" v-if="vipInfo">
          <div class="kpi-icon bg-gold"><span class="material-symbols-outlined">stars</span></div>
          <p class="kpi-label">会员等级</p>
          <h3 class="kpi-value" :class="{ 'is-expired': vipInfo.expired }">{{ vipInfo.label }}</h3>
          <p class="kpi-sub">
            <template v-if="vipInfo.expired">已于 {{ vipInfo.date }} 到期</template>
            <template v-else-if="vipInfo.effectiveLevel === 0">未开通</template>
            <template v-else-if="vipInfo.daysLeft != null && vipInfo.daysLeft <= 30">
              剩 {{ vipInfo.daysLeft }} 天到期
            </template>
            <template v-else-if="vipInfo.date">到期 {{ vipInfo.date }}</template>
            <template v-else>永久有效</template>
          </p>
        </div>
      </div>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.user-detail-view {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}
.page-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: $spacing-md;
}
.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: none;
  color: $color-on-surface-variant;
  cursor: pointer;
  font-size: $font-size-label-md;
  &:hover { color: $color-brand; }
}
.page-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin-top: $spacing-xs;
}
.page-sub {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  font-family: $font-family-mono;
}
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: $spacing-sm $spacing-lg;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 700;
  border: none;
  background: $color-brand;
  color: #fff;
  cursor: pointer;
}
.empty-tip {
  padding: $spacing-xl;
  text-align: center;
  color: $color-on-surface-variant;
}
.card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  padding: $spacing-lg;
}
.profile-card {
  display: flex;
  gap: $spacing-lg;
  align-items: center;
}
.avatar {
  width: 88px;
  height: 88px;
  border-radius: $radius-full;
  object-fit: cover;
  background: $color-surface-variant;
}
.profile-main { flex: 1; }
.profile-name {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
}
.profile-meta {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-lg;
  margin-top: $spacing-sm;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
}
.profile-bio {
  margin-top: $spacing-sm;
  color: $color-on-surface-variant;
  font-size: $font-size-body-md;
}
.status-pill {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  font-size: $font-size-label-sm;
  font-weight: 700;
  .dot { width: 8px; height: 8px; border-radius: $radius-full; }
  &.normal { color: $color-success; .dot { background: $color-success; } }
  &.banned { color: $color-emergency; .dot { background: $color-emergency; } }
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-md;
}
.kpi-card {
  display: flex;
  flex-direction: column;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
}
.kpi-icon {
  width: 40px; height: 40px;
  border-radius: $radius-md;
  display: flex; align-items: center; justify-content: center;
  margin-bottom: $spacing-md;
  &.bg-blue { background: rgba(0, 96, 172, 0.15); color: $color-secondary; }
  &.bg-orange { background: rgba(255, 122, 50, 0.15); color: $color-brand; }
  &.bg-gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  .material-symbols-outlined { font-size: 22px; }
}
.kpi-label { font-size: $font-size-label-md; color: $color-on-surface-variant; margin-bottom: 4px; }
.kpi-value {
  font-family: $font-family-headline;
  font-size: 28px; font-weight: 700; color: $color-on-surface;
  &.is-expired { color: $color-on-surface-variant; text-decoration: line-through; opacity: 0.7; }
}
.kpi-sub {
  margin-top: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.vip-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 700;
  vertical-align: middle;
  .material-symbols-outlined {
    font-size: 14px;
    font-variation-settings: 'FILL' 1;
  }
  &.gold { background: rgba(250, 173, 20, 0.15); color: $color-warning; }
  &.silver { background: rgba(0, 96, 172, 0.12); color: #6b7a8a; }
  &.platinum { background: linear-gradient(135deg, rgba(32, 178, 178, 0.15) 0%, rgba(0, 96, 172, 0.15) 100%); color: #0f8a8a; }
  &.gray { background: $color-surface-container; color: $color-on-surface-variant; }
  &.expired {
    background: $color-surface-container !important;
    color: $color-on-surface-variant !important;
    opacity: 0.7;
  }
}
.vip-tag-expired {
  margin-left: 4px;
  padding: 0 6px;
  border-radius: $radius-sm;
  background: rgba($color-emergency, 0.12);
  color: $color-emergency;
  font-size: 10px;
  font-weight: 700;
  line-height: 16px;
  font-variation-settings: normal;
}
</style>
