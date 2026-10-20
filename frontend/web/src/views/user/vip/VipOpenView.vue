<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { userApi } from '@/api/modules/user'
import { metaApi, type VipPlan } from '@/api/modules/meta'
import { useUserStore } from '@/stores/user'

const router = useRouter()

// ============================================================
// 套餐（核心文案来自后端 metaApi.vipPlan()；视觉配置(渐变/图标)保留在前端）
// ============================================================
interface PlanVisual {
  icon: string
  gradient: string
  borderColor: string
  recommended?: boolean
}
// 视觉配置必须以后端套餐 key（l1/l2/l3）为索引。
// 历史键名是 bronze/silver/gold，与接口返回对不上 → visualMap[p.key] 恒为 undefined，
// 三张套餐卡的图标、渐变、主题色、「推荐」角标全部失效，整页只剩白底方块。
const visualMap: Record<string, PlanVisual> = {
  l1: { icon: 'workspace_premium', gradient: 'linear-gradient(135deg, #f7f9fb 0%, #e6ecf2 100%)', borderColor: '#8e99a6' },
  l2: { icon: 'military_tech',     gradient: 'linear-gradient(135deg, #fff5ec 0%, #ffd9b8 100%)', borderColor: '#ff7a32', recommended: true },
  l3: { icon: 'diamond',           gradient: 'linear-gradient(135deg, #f3f0ff 0%, #ded6ff 100%)', borderColor: '#7c5cff' },
}

const plans = ref<VipPlan[]>([])
const compareRows = ref<Array<{ feature: string; desc: string; basic: string; hasVip: string }>>([])

// 把后端返回的套餐与前端视觉配置(渐变/边框/icon/推荐)合并，便于模板渲染
const plansWithVisual = computed(() =>
  plans.value.map((p) => ({
    ...p,
    ...(visualMap[p.key] || {}),
  })),
)

async function loadVipPlan() {
  try {
    const data = await metaApi.vipPlan()
    plans.value = data.plans || []
    compareRows.value = data.compareRows || []
  } catch (e) {
    console.warn('[VipOpen] vipPlan 加载失败', e)
  }
}

// 当前用户等级（用于区分续费 vs 升级 vs 首次开通）
const currentLevel = ref(0)
const currentExpire = ref('') // 当前会员有效期（YYYY-MM-DD）
const currentPlanKey = computed(() => {
  const k = plans.value.find((p) => p.level === currentLevel.value)?.key
  return k || null
})
const currentVipName = computed(() =>
  plans.value.find((p) => p.level === currentLevel.value)?.name || '普通会员',
)

// 选中档位相对当前等级的语义：renew=续费 / upgrade=升级 / open=开通 / down=降级(锁定)
const selectedRelation = computed<'renew' | 'upgrade' | 'open' | 'down'>(() => {
  const sel = plans.value.find((p) => p.key === selectedKey.value)
  if (!sel) return 'open'
  if (sel.level < currentLevel.value) return 'down'
  if (sel.level === currentLevel.value) return 'renew'
  return 'upgrade'
})

onMounted(async () => {
  await loadVipPlan()
  // 默认选中「推荐档」，否则选中间档
  const recommended = plans.value.find((p) => visualMap[p.key]?.recommended)
  selectedKey.value = recommended?.key || plans.value[Math.floor(plans.value.length / 2)]?.key || ''
  try {
    const me: any = await userApi.me()
    currentLevel.value = Number(me.vipLevel ?? 0)
    currentExpire.value = me.vipExpireAt ? String(me.vipExpireAt).replace('T', ' ').slice(0, 10) : ''
  } catch (e) {
    console.warn('[VipOpen] me 加载失败', e)
  }
})

// 低于当前等级的套餐（降级无意义）不可购买，避免“误购降级档”造成困惑
function isPlanLocked(p: VipPlan): boolean {
  return p.level < currentLevel.value
}
function lockedHint(p: VipPlan): string {
  const names: Record<number, string> = { 1: '白银', 2: '黄金', 3: '钻石' }
  return `已是${names[p.level] || '更高'}会员`
}

// ============================================================
// 选中档位（onMounted 中根据后端数据决定默认值）
// ============================================================
const selectedKey = ref<string>('')
// 套餐数据异步加载完成前（首屏同步渲染时 plans 为空），selectedPlan 不能取 undefined，
// 否则模板访问 selectedPlan.name/borderColor/perks 会抛 TypeError 导致整页白屏“进不去”。
// 这里用一个兜底对象，保证首屏安全渲染，接口返回后自动切到真实数据。
const fallbackPlan: VipPlan & PlanVisual = {
  key: '',
  level: 0,
  tag: '',
  name: '会员',
  monthly: 0,
  price: 0,
  badge: '',
  perks: [] as string[],
  icon: 'workspace_premium',
  gradient: 'linear-gradient(135deg, #f7f9fb 0%, #e6ecf2 100%)',
  borderColor: '#8e99a6',
}
const selectedPlan = computed(
  () =>
    plansWithVisual.value.find((p) => p.key === selectedKey.value) ||
    plansWithVisual.value[0] ||
    fallbackPlan,
)
const totalAmount = computed(() => selectedPlan.value?.monthly ?? 0)

function pickPlan(key: string) {
  const plan = plans.value.find((p) => p.key === key)
  if (plan && isPlanLocked(plan)) return
  selectedKey.value = key
  // 移动端：选中后平滑滚动到订单摘要，便于直接支付
  if (typeof window !== 'undefined' && window.innerWidth < 960) {
    document.getElementById('order-summary')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
  }
}

// ============================================================
// 会员服务承诺（替代原 AI 政策提示）
// 去除"专属客服"等无法落地的承诺，保留有平台能力背书的承诺
const promises = [
  { icon: 'verified', title: '正品保障', desc: '全部商品由品牌方直供，假一赔十更安心。' },
  { icon: 'autorenew', title: '长期记录', desc: 'AI 问诊记录与会员身份长期保留，随时回溯。' },
]

// ============================================================
// 协议 + 支付
// ============================================================
const agreedGold = ref(false)
const agreedPay = ref(false)
const agreed = computed(() => agreedGold.value && agreedPay.value)
const paying = ref(false)
const redirecting = ref(false) // 真实支付宝：已打开收银台，等待异步通知
let pollTimer: number | null = null

const userStore = useUserStore()

// 轮询会员支付结果，直到异步通知把 t_vip_pay 置为 paid（grantVip 已执行）
function startVipPoll(outTradeNo: string) {
  stopVipPoll()
  pollTimer = window.setInterval(async () => {
    try {
      const s: any = await userApi.vipPayStatus(outTradeNo)
      if (s.paid) {
        stopVipPoll()
        onVipPaid(s)
      }
    } catch {
      // 忽略单次查询异常，继续轮询
    }
  }, 3000)
}

function stopVipPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

// 支付成功后的统一处理
async function onVipPaid(s: any) {
  const sel = plans.value.find((p) => p.key === selectedKey.value)
  const pre = currentLevel.value
  const post = Number(s.vipLevel ?? pre)
  // 根据升级前/后等级区分文案，避免「已是黄金再付黄金」被误认为没生效
  const actionText =
    post > pre ? '升级成功' : post === pre && pre > 0 ? '续费成功' : '开通成功'
  const date = s.vipExpireAt ? String(s.vipExpireAt).replace('T', ' ').slice(0, 10) : ''
  ElMessage.success(`${actionText}！${sel?.name} 有效期至 ${date}`)
  currentLevel.value = post
  currentExpire.value = date
  // 刷新全局用户态（顶栏 VIP 标识等）
  await userStore.fetchUser()
  redirecting.value = false
  setTimeout(() => router.push('/user'), 1200)
}

// 打开支付宝收银台：注入表单（电脑网站支付 page_pay 返回的自动提交 HTML）或跳转兜底 URL
function openAlipay(res: any) {
  const html = res.pageBody
  if (html) {
    let mount = document.getElementById('alipay-form-mount')
    if (!mount) {
      mount = document.createElement('div')
      mount.id = 'alipay-form-mount'
      mount.style.display = 'none'
      document.body.appendChild(mount)
    }
    mount.innerHTML = html
    const form = mount.querySelector('form') as HTMLFormElement | null
    if (form) {
      form.target = '_blank'
      form.submit()
      ElMessage.success('已打开支付宝收银台，请完成支付')
      return
    }
  }
  if (res.payUrl) {
    window.open(res.payUrl, '_blank')
    ElMessage.success('已打开支付宝收银台，请完成支付')
    return
  }
  ElMessage.warning('跳转失败，请重试')
}

async function confirmPay() {
  if (!agreed.value) {
    ElMessage.warning('请先勾选并同意会员服务协议')
    return
  }
  const sel = plans.value.find((p) => p.key === selectedKey.value)
  if (sel && isPlanLocked(sel)) {
    ElMessage.warning('您已是更高等级会员，无需重复购买低档套餐')
    return
  }
  if (paying.value || redirecting.value) return
  paying.value = true
  try {
    const res: any = await userApi.openVip(selectedKey.value)

    // 演示态（支付宝未启用）：直接开通成功
    if (res.demoMode) {
      const date = res.vipExpireAt ? String(res.vipExpireAt).replace('T', ' ').slice(0, 10) : ''
      const actionText =
        res.action === 'renew' ? '续费成功' : res.action === 'upgrade' ? '升级成功' : '开通成功'
      const t = plans.value.find((p) => p.key === selectedKey.value)
      ElMessage.success(`${actionText}！${t?.name} 有效期至 ${date}`)
      currentLevel.value = Number(res.vipLevel ?? currentLevel.value)
      await userStore.fetchUser()
      setTimeout(() => router.push('/user'), 1200)
      return
    }

    // 真实支付宝：打开收银台 + 轮询
    if (res.pageBody || res.payUrl) {
      openAlipay(res)
      redirecting.value = true
      startVipPoll(res.outTradeNo)
    } else {
      ElMessage.error('未获取到支付宝支付信息')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '支付失败，请稍后重试')
  } finally {
    if (!redirecting.value) paying.value = false
  }
}

// 手动重新跳转（自动跳转被拦截时）
function reopenAlipay() {
  ElMessage.info('请重新发起支付')
  redirecting.value = false
  paying.value = false
}

onUnmounted(() => {
  stopVipPoll()
})

// 顶部导航锚点跳转
function scrollTo(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<template>
  <div class="vip-open">
    <!-- ==================== Hero ==================== -->
    <section class="hero">
      <div class="hero-glow"></div>
      <div class="hero-inner">
        <div class="hero-copy">
          <span class="hero-eyebrow">
            <span class="material-symbols-outlined">workspace_premium</span>
            PetMate 会员
          </span>
          <h1 class="hero-title">
            升级会员，<br />
            <em>解锁专属养宠特权</em>
          </h1>
          <p class="hero-desc">
            商城购物免邮、生日礼包、问诊记录长期保存……让每一次养宠都被认真对待。
          </p>
          <div class="hero-cta-row">
            <button class="btn-primary" @click="scrollTo('plans')">
              选择方案
              <span class="material-symbols-outlined">arrow_forward</span>
            </button>
            <button class="btn-ghost" @click="scrollTo('benefits')">查看权益</button>
          </div>
          <ul class="hero-badges">
            <li><span class="material-symbols-outlined">check_circle</span>商城购物免邮</li>
            <li><span class="material-symbols-outlined">check_circle</span>生日礼包</li>
            <li><span class="material-symbols-outlined">check_circle</span>问诊记录长期保存</li>
          </ul>
        </div>

        <!-- 会员卡视觉（随所选方案实时变化） -->
        <div class="hero-visual" :style="{ '--card-color': selectedPlan.borderColor }">
          <div class="member-card">
            <div class="mc-top">
              <span class="mc-brand">
                <span class="material-symbols-outlined">pets</span> PetMate
              </span>
              <span class="mc-crown material-symbols-outlined">crown</span>
            </div>
            <div class="mc-name">{{ selectedPlan.name }}</div>
            <div class="mc-perks">
              <span v-for="(pk, i) in selectedPlan.perks.slice(0, 3)" :key="i" class="mc-perk">
                {{ pk }}
              </span>
            </div>
            <div class="mc-foot">
              <span class="mc-label">MEMBERSHIP</span>
              <span class="mc-chip material-symbols-outlined">wifi</span>
            </div>
          </div>
          <span class="mc-shadow"></span>
        </div>
      </div>
    </section>

    <!-- ==================== 方案 + 订单摘要（两栏 web 布局） ==================== -->
    <section id="plans" class="plans-section">
      <div class="plans-layout">
        <!-- 左：方案选择 + 权益对比 -->
        <div class="plans-main">
          <header class="block-head">
            <h2 class="block-title">选择适合您的会员方案</h2>
            <p class="block-sub">所有方案均按月开通，随时可续费或升级，权益即时生效。</p>
          </header>

          <div class="plan-grid">
            <article
              v-for="p in plansWithVisual"
              :key="p.key"
              class="plan-card"
              :class="{
                'is-recommended': p.recommended,
                'is-selected': selectedKey === p.key,
                'is-current': currentPlanKey === p.key,
                'is-locked': isPlanLocked(p),
              }"
              :style="{ background: p.gradient, '--plan-color': p.borderColor }"
              @click="pickPlan(p.key)"
            >
              <span v-if="p.tag" class="plan-ribbon">{{ p.tag }}</span>
              <div class="plan-header">
                <span class="plan-icon">
                  <span class="material-symbols-outlined">{{ p.icon }}</span>
                </span>
                <h3 class="plan-name">{{ p.name }}</h3>
                <p v-if="currentPlanKey === p.key" class="plan-current-tag">
                  <span class="material-symbols-outlined">check_circle</span>
                  您当前的身份
                </p>
              </div>

              <div class="plan-price">
                <span class="price-yuan">¥</span>
                <span class="price-num">{{ p.monthly }}</span>
                <span class="price-unit">/月</span>
              </div>

              <ul class="plan-perks">
                <li v-for="(perk, idx) in p.perks" :key="idx">
                  <span class="material-symbols-outlined">check_circle</span>
                  {{ perk }}
                </li>
              </ul>

              <button
                class="plan-cta"
                :class="{ selected: selectedKey === p.key, locked: isPlanLocked(p) }"
                :disabled="isPlanLocked(p)"
                @click.stop="pickPlan(p.key)"
              >
                <template v-if="isPlanLocked(p)">
                  <span class="material-symbols-outlined">lock</span> {{ lockedHint(p) }}
                </template>
                <template v-else-if="currentPlanKey === p.key">
                  <span class="material-symbols-outlined">autorenew</span> 续费
                </template>
                <template v-else-if="selectedKey === p.key">
                  <span class="material-symbols-outlined">check</span> 已选择
                </template>
                <template v-else>选择此方案</template>
              </button>
            </article>
          </div>

          <!-- 会员服务承诺 -->
          <div class="promise-grid">
            <div v-for="(p, i) in promises" :key="i" class="promise-card">
              <span class="promise-icon material-symbols-outlined">{{ p.icon }}</span>
              <div>
                <h4 class="promise-title">{{ p.title }}</h4>
                <p class="promise-desc">{{ p.desc }}</p>
              </div>
            </div>
          </div>

          <!-- 权益对比表 -->
          <div id="benefits" class="benefits">
            <header class="block-head">
              <h2 class="block-title">会员专属权益对比</h2>
            </header>
            <div class="compare-wrap">
              <table class="compare-table">
                <thead>
                  <tr>
                    <th class="col-feature">VIP 专属权益</th>
                    <th class="col-basic">普通用户</th>
                    <th class="col-vip">
                      <span class="vip-pill">VIP 会员</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, i) in compareRows" :key="i">
                    <td class="col-feature">
                      <div class="feature-title">{{ row.feature }}</div>
                      <div class="feature-desc">{{ row.desc }}</div>
                    </td>
                    <td class="col-basic">{{ row.basic }}</td>
                    <td class="col-vip">
                      <span class="vip-cell">
                        <span class="material-symbols-outlined">check</span>
                        {{ row.hasVip }}
                      </span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- 右：吸顶订单摘要 -->
        <aside id="order-summary" class="order-summary">
          <div class="summary-card" :style="{ '--plan-color': selectedPlan.borderColor }">
            <div v-if="currentLevel > 0" class="summary-current">
              <span class="material-symbols-outlined">verified</span>
              <span>
                您当前：{{ currentVipName }}
                <template v-if="currentExpire"> · 有效期至 {{ currentExpire }}</template>
              </span>
            </div>
            <div class="summary-head">
              <span class="summary-icon material-symbols-outlined">{{ selectedPlan.icon }}</span>
              <div>
                <div class="summary-name">{{ selectedPlan.name }}</div>
                <div class="summary-meta">
                  {{ selectedRelation === 'renew' ? '续费 · 延长 1 年有效期' : selectedRelation === 'upgrade' ? '升级开通 · 1 年有效期' : '开通 · 1 年有效期' }}
                </div>
              </div>
            </div>

            <ul class="summary-perks">
              <li v-for="(pk, i) in selectedPlan.perks.slice(0, 3)" :key="i">
                <span class="material-symbols-outlined">check_circle</span>{{ pk }}
              </li>
            </ul>

            <div class="summary-amount">
              <span class="amount-label">应付总计</span>
              <span class="amount-num">¥{{ totalAmount.toFixed(2) }}</span>
            </div>

            <div class="agreements">
              <label class="agree-item">
                <input type="checkbox" v-model="agreedGold" />
                <span class="agree-tick" :class="{ checked: agreedGold }">
                  <span class="material-symbols-outlined">check</span>
                </span>
                <span class="agree-text">
                  我已阅读并同意
                  <a href="javascript:;" class="agree-link">《PetMate 会员服务协议》</a>
                </span>
              </label>
              <label class="agree-item">
                <input type="checkbox" v-model="agreedPay" />
                <span class="agree-tick" :class="{ checked: agreedPay }">
                  <span class="material-symbols-outlined">check</span>
                </span>
                <span class="agree-text">
                  我已阅读并同意
                  <a href="javascript:;" class="agree-link">《PetMate 付费服务协议》</a>
                </span>
              </label>
            </div>

            <button class="pay-btn" :disabled="!agreed || paying || redirecting" @click="confirmPay">
              <template v-if="paying && !redirecting">
                <span class="material-symbols-outlined spin">progress_activity</span>
                支付中…
              </template>
              <template v-else-if="redirecting">
                <span class="material-symbols-outlined spin">progress_activity</span>
                等待支付结果…
              </template>
              <template v-else>
                确认并支付
                <span class="material-symbols-outlined">arrow_forward</span>
              </template>
            </button>

            <!-- 真实支付宝：跳转收银台中 -->
            <div v-if="redirecting" class="vip-redirect">
              <span class="redirect-spinner" />
              <div class="vip-redirect-tip">正在等待支付宝支付结果，完成支付后将自动开通会员</div>
              <button class="redirect-link" type="button" @click="reopenAlipay">
                未完成支付？点此返回
              </button>
            </div>

            <p class="summary-tip">支付成功后权益即时生效，会员有效期自动顺延。</p>
          </div>
        </aside>
      </div>
    </section>

    <!-- ==================== 底部 ==================== -->
    <footer class="vip-footer">
      <div class="footer-inner">
        <div class="footer-logo">
          <span class="material-symbols-outlined">pets</span>
          PetMate
        </div>
        <nav class="footer-links">
          <a href="javascript:;">关于我们</a>
          <a href="javascript:;">企业入驻</a>
          <a href="javascript:;">用户协议</a>
          <a href="javascript:;">联系客服</a>
        </nav>
        <p class="footer-copyright">© 2024 PetMate 宠物智能管家平台 · 由团队倾心打造</p>
      </div>
    </footer>
  </div>
</template>

<style lang="scss" scoped>
// ==================== 页面壳 ====================
.vip-open {
  min-height: 100vh;
  background: $color-background;
  display: flex;
  flex-direction: column;
}

// ==================== Hero ====================
.hero {
  position: relative;
  overflow: hidden;
  padding: $spacing-3xl $spacing-xl $spacing-2xl;
  background: linear-gradient(135deg, #1a0f08 0%, #2a1810 35%, #4a2510 60%, #ff7a32 100%);
  color: #ffffff;
  isolation: isolate;
}

.hero-glow {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 85% 55%, rgba(255, 222, 158, 0.45) 0%, transparent 45%),
    radial-gradient(circle at 15% 30%, rgba(255, 122, 50, 0.25) 0%, transparent 55%);
  z-index: -1;
}

.hero-inner {
  max-width: $width-content-max;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-2xl;

  @include desktop {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: $spacing-3xl;
    min-height: 420px;
  }
}

.hero-copy {
  flex: 1;
  text-align: center;

  @include desktop {
    text-align: left;
  }
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px $spacing-md;
  background: rgba(255, 222, 158, 0.18);
  border: 1px solid rgba(255, 222, 158, 0.35);
  border-radius: $radius-full;
  color: $color-tertiary-fixed;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  letter-spacing: 0.04em;
  margin-bottom: $spacing-lg;

  .material-symbols-outlined {
    font-size: 18px;
    font-variation-settings: 'FILL' 1;
  }
}

.hero-title {
  font-family: $font-family-headline;
  font-size: 36px;
  line-height: 1.18;
  font-weight: $font-weight-extrabold;
  letter-spacing: 0.01em;
  margin: 0 0 $spacing-md;

  @include desktop {
    font-size: 56px;
  }

  em {
    display: block;
    font-style: normal;
    background: linear-gradient(90deg, #ffdf9e 0%, #ff9a4d 50%, #ffe9c8 100%);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
    filter: drop-shadow(0 2px 8px rgba(255, 154, 77, 0.3));
  }
}

.hero-desc {
  font-size: $font-size-body-lg;
  color: rgba(255, 255, 255, 0.78);
  margin: 0 auto $spacing-xl;
  line-height: 1.6;
  max-width: 480px;

  @include desktop {
    margin-left: 0;
  }
}

.hero-cta-row {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-md;
  justify-content: center;
  margin-bottom: $spacing-lg;

  @include desktop {
    justify-content: flex-start;
  }
}

.btn-primary,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: $spacing-md $spacing-xl;
  border-radius: $radius-full;
  font-size: $font-size-body-md;
  font-family: inherit;
  cursor: pointer;
  transition: transform $transition-base, box-shadow $transition-base, background $transition-base;
}

.btn-primary {
  background: linear-gradient(135deg, #ffdf9e 0%, #ff9a4d 100%);
  color: #5b4300;
  border: none;
  font-weight: $font-weight-bold;
  box-shadow: 0 8px 24px rgba(255, 154, 77, 0.45);

  .material-symbols-outlined {
    font-size: 20px;
  }

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 12px 28px rgba(255, 154, 77, 0.55);
  }
  &:active {
    transform: translateY(0);
  }
}

.btn-ghost {
  background: transparent;
  color: rgba(255, 255, 255, 0.85);
  border: 1.5px solid rgba(255, 255, 255, 0.35);
  font-weight: $font-weight-semibold;

  &:hover {
    background: rgba(255, 255, 255, 0.1);
    border-color: rgba(255, 255, 255, 0.55);
  }
}

.hero-badges {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-lg;
  list-style: none;
  padding: 0;
  margin: 0;
  justify-content: center;

  @include desktop {
    justify-content: flex-start;
  }

  li {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: $font-size-label-md;
    color: rgba(255, 255, 255, 0.72);

    .material-symbols-outlined {
      font-size: 16px;
      color: $color-tertiary-fixed;
      font-variation-settings: 'FILL' 1;
    }
  }
}

// 会员卡视觉
.hero-visual {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  max-width: 380px;
  perspective: 1200px;

  @include desktop {
    max-width: 420px;
  }
}

.member-card {
  position: relative;
  width: 100%;
  aspect-ratio: 1.6 / 1;
  padding: $spacing-lg $spacing-xl;
  border-radius: $radius-xl;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.16) 0%, rgba(255, 255, 255, 0.04) 100%),
    linear-gradient(135deg, var(--card-color) 0%, #1a1a1a 130%);
  border: 1px solid rgba(255, 255, 255, 0.25);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(8px);
  color: #fff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transform: rotateY(-12deg) rotateX(6deg);
  transition: transform $transition-base, box-shadow $transition-base;
  animation: cardFloat 6s ease-in-out infinite;

  &:hover {
    transform: rotateY(-6deg) rotateX(3deg) translateY(-6px);
    box-shadow: 0 32px 70px rgba(0, 0, 0, 0.5);
  }
}

@keyframes cardFloat {
  0%,
  100% {
    transform: rotateY(-12deg) rotateX(6deg) translateY(0);
  }
  50% {
    transform: rotateY(-12deg) rotateX(6deg) translateY(-10px);
  }
}

.mc-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.mc-brand {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: $font-family-headline;
  font-weight: $font-weight-bold;
  font-size: $font-size-body-md;

  .material-symbols-outlined {
    font-size: 20px;
    font-variation-settings: 'FILL' 1;
  }
}

.mc-crown {
  font-size: 28px;
  color: #ffdf9e;
  font-variation-settings: 'FILL' 1;
}

.mc-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-extrabold;
  margin-top: $spacing-xs;
}

.mc-perks {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: $spacing-sm;
}

.mc-perk {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: $radius-full;
  background: rgba(255, 255, 255, 0.16);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.mc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: $spacing-sm;
}

.mc-label {
  font-size: 11px;
  letter-spacing: 0.18em;
  opacity: 0.8;
}

.mc-chip {
  font-size: 18px;
  opacity: 0.85;
}

.mc-shadow {
  position: absolute;
  left: 8%;
  bottom: -22px;
  width: 84%;
  height: 30px;
  background: radial-gradient(ellipse at center, rgba(0, 0, 0, 0.45) 0%, transparent 70%);
  filter: blur(10px);
  z-index: -1;
}

// ==================== 方案区（两栏） ====================
.plans-section {
  padding: $spacing-3xl 0 $spacing-2xl;
}

.plans-layout {
  max-width: $width-content-max;
  margin: 0 auto;
  padding: 0 $spacing-xl;
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-2xl;

  @include desktop {
    grid-template-columns: minmax(0, 1fr) 360px;
    align-items: start;
    gap: $spacing-3xl;
  }
}

.plans-main {
  min-width: 0;
}

.block-head {
  margin-bottom: $spacing-xl;
}

.block-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg-mobile;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;

  @include desktop {
    font-size: $font-size-headline-lg;
  }
}

.block-sub {
  margin: $spacing-xs 0 0;
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

// 方案卡片
.plan-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-lg;

  @include tablet {
    grid-template-columns: repeat(3, 1fr);
    gap: $spacing-xl;
    align-items: stretch;
  }
}

.plan-card {
  position: relative;
  display: flex;
  flex-direction: column;
  padding: $spacing-xl $spacing-lg $spacing-lg;
  border-radius: $radius-xl;
  border: 2px solid transparent;
  box-shadow: $shadow-sm;
  cursor: pointer;
  transition: transform $transition-base, box-shadow $transition-base, border-color $transition-base;

  &:hover {
    transform: translateY(-4px);
    box-shadow: $shadow-lg;
    border-color: var(--plan-color);
  }

  &.is-selected {
    border-color: var(--plan-color);
    box-shadow: 0 12px 32px rgba(255, 122, 50, 0.18);
    transform: translateY(-4px);
  }

  &.is-recommended {
    @include tablet {
      transform: scale(1.04);
      z-index: 2;
    }
    box-shadow: 0 8px 24px rgba(255, 122, 50, 0.25);
  }

  &.is-recommended.is-selected {
    box-shadow: 0 16px 40px rgba(255, 122, 50, 0.35);
  }

  &.is-current {
    &::after {
      content: '';
      position: absolute;
      inset: -2px;
      border-radius: $radius-xl;
      border: 2px dashed var(--plan-color);
      opacity: 0.5;
      pointer-events: none;
    }
  }

  &.is-locked {
    opacity: 0.55;
    cursor: not-allowed;
    filter: grayscale(0.25);

    &:hover {
      transform: none;
      box-shadow: $shadow-sm;
      border-color: transparent;
    }

    .plan-cta {
      background: $color-surface-container;
      color: $color-text-tertiary;
      border-color: $color-outline-variant;
      cursor: not-allowed;

      &:hover {
        background: $color-surface-container;
        color: $color-text-tertiary;
      }
    }
  }
}

.plan-ribbon {
  position: absolute;
  top: 12px;
  right: 12px;
  padding: 4px $spacing-md;
  background: linear-gradient(135deg, #ff7a32 0%, #ff9a4d 100%);
  color: #ffffff;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: $font-weight-bold;
  box-shadow: 0 4px 10px rgba(255, 122, 50, 0.4);
  letter-spacing: 0.02em;
}

.plan-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  margin-bottom: $spacing-lg;
}

.plan-icon {
  width: 56px;
  height: 56px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.7);
  border: 1.5px solid var(--plan-color);
  border-radius: $radius-full;
  color: var(--plan-color);
  margin-bottom: $spacing-md;
  box-shadow: 0 4px 12px rgba(255, 122, 50, 0.15);

  .material-symbols-outlined {
    font-size: 30px;
    font-variation-settings: 'FILL' 1;
  }
}

.plan-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
  margin: 0;
}

.plan-current-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 6px;
  padding: 2px $spacing-sm;
  background: var(--plan-color);
  color: #ffffff;
  border-radius: $radius-full;
  font-size: 11px;
  font-weight: $font-weight-semibold;

  .material-symbols-outlined {
    font-size: 12px;
    font-variation-settings: 'FILL' 1;
  }
}

.plan-price {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 2px;
  margin-bottom: $spacing-lg;
  color: var(--plan-color);
  font-family: $font-family-headline;
}

.price-yuan {
  font-size: $font-size-body-lg;
  font-weight: $font-weight-semibold;
}

.price-num {
  font-size: 48px;
  font-weight: $font-weight-extrabold;
  line-height: 1;
}

.price-unit {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  font-weight: $font-weight-regular;
  margin-left: 4px;
}

.plan-perks {
  list-style: none;
  padding: 0;
  margin: 0 0 $spacing-lg;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;

  li {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: $font-size-body-md;
    color: $color-on-surface;
    line-height: 1.5;

    .material-symbols-outlined {
      font-size: 18px;
      color: var(--plan-color);
      font-variation-settings: 'FILL' 1;
      flex-shrink: 0;
    }
  }
}

.plan-cta {
  width: 100%;
  padding: $spacing-sm $spacing-lg;
  background: rgba(255, 255, 255, 0.85);
  color: var(--plan-color);
  border: 1.5px solid var(--plan-color);
  border-radius: $radius-md;
  font-size: $font-size-body-md;
  font-weight: $font-weight-semibold;
  font-family: inherit;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  transition: all $transition-base;

  &:hover {
    background: var(--plan-color);
    color: #ffffff;
  }

  &.selected {
    background: var(--plan-color);
    color: #ffffff;
    box-shadow: 0 4px 14px rgba(255, 122, 50, 0.35);

    .material-symbols-outlined {
      font-size: 18px;
      font-variation-settings: 'FILL' 1;
    }
  }

  &:disabled {
    background: $color-surface-container;
    color: $color-text-tertiary;
    border-color: $color-outline-variant;
    cursor: not-allowed;

    &:hover {
      background: $color-surface-container;
      color: $color-text-tertiary;
    }
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

// 服务承诺
.promise-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;
  margin-top: $spacing-2xl;

  @include tablet {
    grid-template-columns: repeat(3, 1fr);
  }
}

.promise-card {
  display: flex;
  align-items: flex-start;
  gap: $spacing-md;
  padding: $spacing-lg;
  background: #ffffff;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  transition: transform $transition-base, box-shadow $transition-base;

  &:hover {
    transform: translateY(-2px);
    box-shadow: $shadow-md;
  }
}

.promise-icon {
  font-size: 28px;
  color: $color-brand;
  font-variation-settings: 'FILL' 1;
  flex-shrink: 0;
}

.promise-title {
  margin: 0 0 4px;
  font-family: $font-family-headline;
  font-size: $font-size-body-md;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
}

.promise-desc {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  line-height: 1.5;
}

// 权益对比
.benefits {
  margin-top: $spacing-3xl;
}

.compare-wrap {
  overflow-x: auto;
  @include scrollbar-hide;
}

.compare-table {
  width: 100%;
  min-width: 640px;
  border-collapse: separate;
  border-spacing: 0;
  background: #ffffff;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: $shadow-md;

  th,
  td {
    padding: $spacing-md $spacing-lg;
    text-align: center;
    vertical-align: middle;
  }

  thead {
    background: $color-surface-container-low;

    th {
      font-family: $font-family-headline;
      font-size: $font-size-body-md;
      font-weight: $font-weight-semibold;
      color: $color-on-surface;
      border-bottom: 1px solid $color-outline-variant;
    }

    .col-feature {
      text-align: left;
      width: 40%;
    }

    .col-vip {
      width: 35%;
      background: linear-gradient(135deg, rgba(255, 122, 50, 0.08) 0%, rgba(255, 222, 158, 0.12) 100%);
    }
  }

  tbody {
    tr {
      transition: background $transition-fast;

      &:hover {
        background: rgba(255, 219, 204, 0.25);
      }

      &:not(:last-child) td {
        border-bottom: 1px solid $color-surface-variant;
      }
    }

    .col-feature {
      text-align: left;
    }

    .feature-title {
      font-family: $font-family-headline;
      font-size: $font-size-body-md;
      font-weight: $font-weight-semibold;
      color: $color-on-surface;
    }

    .feature-desc {
      margin-top: 2px;
      font-size: $font-size-label-sm;
      color: $color-on-surface-variant;
    }

    .col-basic {
      color: $color-on-surface-variant;
      font-size: $font-size-body-md;
    }

    .col-vip {
      background: linear-gradient(135deg, rgba(255, 122, 50, 0.04) 0%, rgba(255, 222, 158, 0.06) 100%);
    }
  }
}

.vip-pill {
  display: inline-block;
  padding: 4px $spacing-md;
  background: linear-gradient(135deg, #ff7a32 0%, #ff9a4d 100%);
  color: #ffffff;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  letter-spacing: 0.04em;
  box-shadow: 0 4px 10px rgba(255, 122, 50, 0.3);
}

.vip-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px $spacing-md;
  background: rgba(255, 122, 50, 0.12);
  color: $color-brand-dark;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;

  .material-symbols-outlined {
    font-size: 16px;
    color: $color-brand;
    font-variation-settings: 'FILL' 1;
  }
}

// ==================== 订单摘要（吸顶） ====================
.order-summary {
  min-width: 0;

  @include desktop {
    position: sticky;
    top: 96px;
  }
}

.summary-card {
  background: #ffffff;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-xl;
  box-shadow: $shadow-md;
  padding: $spacing-lg;
  transition: border-color $transition-base, box-shadow $transition-base;

  &:hover {
    box-shadow: 0 16px 36px rgba(255, 122, 50, 0.12);
    border-color: var(--plan-color);
  }
}

.summary-head {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding-bottom: $spacing-md;
  border-bottom: 1px solid $color-surface-variant;
}

.summary-current {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: $spacing-md;
  padding: $spacing-sm $spacing-md;
  background: rgba(255, 122, 50, 0.1);
  border: 1px solid $color-brand-light;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  color: $color-brand-dark;
  font-weight: $font-weight-semibold;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
    font-variation-settings: 'FILL' 1;
    flex-shrink: 0;
  }
}

.summary-icon {
  width: 48px;
  height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 122, 50, 0.1);
  border: 1.5px solid var(--plan-color);
  border-radius: $radius-md;
  color: var(--plan-color);
  font-size: 26px;
  font-variation-settings: 'FILL' 1;
  flex-shrink: 0;
}

.summary-name {
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-on-surface;
}

.summary-meta {
  margin-top: 2px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.summary-perks {
  list-style: none;
  padding: 0;
  margin: $spacing-md 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;

  li {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: $font-size-label-md;
    color: $color-on-surface;

    .material-symbols-outlined {
      font-size: 16px;
      color: var(--plan-color);
      font-variation-settings: 'FILL' 1;
      flex-shrink: 0;
    }
  }
}

.summary-amount {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: $spacing-md 0;
  border-top: 1px dashed $color-outline-variant;
  border-bottom: 1px dashed $color-outline-variant;
}

.amount-label {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
}

.amount-num {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: $font-weight-extrabold;
  color: $color-brand;
}

.agreements {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  margin: $spacing-md 0;
}

.agree-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  cursor: pointer;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  user-select: none;
  line-height: 1.5;

  input {
    display: none;
  }
}

.agree-tick {
  width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: #ffffff;
  border: 1.5px solid $color-outline-variant;
  border-radius: $radius-sm;
  flex-shrink: 0;
  margin-top: 1px;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 14px;
    color: transparent;
    font-variation-settings: 'FILL' 1;
  }

  &.checked {
    background: $color-brand;
    border-color: $color-brand;

    .material-symbols-outlined {
      color: #ffffff;
    }
  }
}

.agree-link {
  color: $color-brand;
  text-decoration: none;

  &:hover {
    text-decoration: underline;
  }
}

.pay-btn {
  width: 100%;
  padding: $spacing-md;
  background: linear-gradient(135deg, #ff7a32 0%, #ff9a4d 100%);
  color: #ffffff;
  border: none;
  border-radius: $radius-md;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 6px 18px rgba(255, 122, 50, 0.35);
  transition: transform $transition-base, box-shadow $transition-base;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 10px 24px rgba(255, 122, 50, 0.45);
  }

  &:active:not(:disabled) {
    transform: translateY(0);
  }

  &:disabled {
    background: $color-surface-container;
    color: $color-text-tertiary;
    cursor: not-allowed;
    box-shadow: none;
  }

  .spin {
    animation: spin 0.8s linear infinite;
    font-variation-settings: 'FILL' 0;
  }
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.summary-tip {
  margin: $spacing-md 0 0;
  text-align: center;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

// 跳转支付宝收银台中
.vip-redirect {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;
  margin-top: $spacing-md;
  padding: $spacing-md;
  background: rgba(255, 122, 50, 0.06);
  border: 1px solid $color-brand-light;
  border-radius: $radius-md;
}

.redirect-spinner {
  display: inline-block;
  width: 26px;
  height: 26px;
  border: 3px solid $color-brand-light;
  border-top-color: $color-brand;
  border-radius: $radius-full;
  animation: spin 0.8s linear infinite;
}

.vip-redirect-tip {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  text-align: center;
  line-height: 1.5;
}

.redirect-link {
  padding: 4px $spacing-md;
  background: transparent;
  border: 1px solid $color-brand;
  color: $color-brand;
  border-radius: $radius-md;
  font-size: $font-size-label-sm;
  font-weight: 600;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: rgba(255, 122, 50, 0.08);
  }
}

// ==================== 底部 ====================
.vip-footer {
  margin-top: auto;
  padding: $spacing-2xl $spacing-xl $spacing-xl;
  background: $color-surface-container-low;
  border-top: 1px solid $color-outline-variant;
}

.footer-inner {
  max-width: $width-content-max;
  margin: 0 auto;
  text-align: center;
}

.footer-logo {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-family: $font-family-headline;
  font-size: $font-size-body-lg;
  font-weight: $font-weight-bold;
  color: $color-brand;
  margin-bottom: $spacing-md;

  .material-symbols-outlined {
    font-size: 22px;
    font-variation-settings: 'FILL' 1;
  }
}

.footer-links {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: $spacing-lg;
  margin-bottom: $spacing-md;

  a {
    color: $color-on-surface-variant;
    font-size: $font-size-label-md;
    text-decoration: none;
    transition: color $transition-fast;

    &:hover {
      color: $color-brand;
    }
  }
}

.footer-copyright {
  margin: 0;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  opacity: 0.7;
}
</style>
