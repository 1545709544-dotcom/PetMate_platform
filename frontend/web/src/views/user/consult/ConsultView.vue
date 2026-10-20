<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useConsultStore } from '@/stores/consult'
import { useUserStore } from '@/stores/user'
import { consultApi, type Consultation } from '@/api/modules/consult'
import { petApi, type Pet } from '@/api/modules/pet'
import { communityApi } from '@/api/modules/community'
import { resolveCover } from '@/utils/articleCovers'
import { formatAiAnswer } from '@/utils/aiMarkdown'
import { metaApi, type QuickSymptom } from '@/api/modules/meta'

const router = useRouter()
const route = useRoute()
const consult = useConsultStore()
const userStore = useUserStore()

const inputText = ref('')
const pendingImages = ref<string[]>([])   // 待发送图片（base64 data URL）
const fileInput = ref<HTMLInputElement | null>(null)
const chatFlow = ref<HTMLElement | null>(null)
const initError = ref<string | null>(null)

// 登录用户头像（动态从 userStore 取）
const userAvatar = computed(() => userStore.user?.avatar || '')
const userName = computed(() => userStore.user?.nickname || '我')
// 会员状态：优先取用户态 vipLevel；同时兼容额度接口返回的 unlimited（重新进入页面时 quota 会刷新）
const isVip = computed(() => (userStore.user?.vipLevel ?? 0) >= 1 || !!quota.value?.unlimited)

// AI 问诊额度（普通用户上限 10 次，按用户实际提问条数计：每条提问消耗 1 次）
const quota = ref<{ limit: number; used: number; remaining: number; unlimited: boolean } | null>(null)
const quotaLoading = ref(false)
const quotaError = ref(false)
async function loadQuota(showError = false) {
  quotaLoading.value = true
  quotaError.value = false
  try {
    quota.value = await consultApi.quota()
  } catch (e: any) {
    quota.value = null
    quotaError.value = true
    console.error('[Consult] quota 加载失败', e)
    if (showError) {
      const msg = e?.__handled ? '' : (e?.message || '额度信息加载失败，请稍后重试')
      if (msg) ElMessage.error(msg)
    }
  } finally {
    quotaLoading.value = false
  }
}
const quotaPercent = computed(() => {
  if (!quota.value || quota.value.unlimited || quota.value.limit <= 0) return 0
  return Math.min(100, Math.round((quota.value.used / quota.value.limit) * 100))
})
const quotaExhausted = computed(
  () => !!quota.value && !quota.value.unlimited && quota.value.remaining <= 0,
)
function quotaLimit(): number {
  return quota.value?.limit ?? 10
}

// 今日历史抽屉
const historyOpen = ref(false)
const historyLoading = ref(false)
const deletingId = ref<number | null>(null)

// 左栏宠物档案（动态获取）
const pets = ref<Pet[]>([])
const petsLoading = ref(true)
// 当前选中的宠物 id（切换用）
const selectedPetId = ref<number | null>(null)
const currentPet = computed<Pet | null>(() => {
  if (!pets.value.length) return null
  return pets.value.find((p) => p.id === selectedPetId.value) ?? pets.value[0]
})

// 切换宠物弹窗
const switchOpen = ref(false)
function selectPet(id: number) {
  if (id === selectedPetId.value) {
    switchOpen.value = false
    return
  }
  selectedPetId.value = id
  switchOpen.value = false
  // 切换宠物后，用新宠物重新初始化问诊会话，让 AI 上下文更精准
  consult
    .init(currentPet.value?.id)
    .then(() => consult.greet(greetingText()))
    .catch(() => {})
}

const quickSymptoms = ref<QuickSymptom[]>([])

async function loadQuickSymptoms() {
  try {
    const list = await metaApi.quickSymptoms()
    quickSymptoms.value = list || []
  } catch (e) {
    console.warn('[Consult] quickSymptoms 加载失败', e)
    quickSymptoms.value = []
  }
}

// AI 消息渲染：流式阶段也走模板化渲染（每 chunk 重跑 formatAiAnswer）。
// 风险徽章已移除（按需求不再顶部展示）；免责声明由模板底部 .ai-foot-bar 统一渲染。
function formatAiRich(m: { text?: string; streaming?: boolean; riskLevel?: string }): string {
  const html = formatAiAnswer(m.text, !!m.streaming)
  if (!html) return ''
  // 流式阶段：在末尾追加 caret 光标（光标自然贴在最后一段正文后）
  if (m.streaming) {
    return html + '<span class="caret caret-inline"></span>'
  }
  return html
}

function genderText(g?: number): string {
  return g === 0 ? '公' : g === 1 ? '母' : '未知'
}

// AI 进入页时的自我介绍文案（结合当前宠物档案做个性化，并先"读取"宠物信息）
function greetingText(): string {
  const intro = `你好，我是 PetMate AI 资深医生 🐾\n\n`
  const pet = currentPet.value
  if (!pet) {
    return (
      intro +
      `我可以帮你解答毛孩子的健康、饮食、养护与行为问题。直接描述症状或上传照片都可以，` +
      `我会结合科学建议给你参考，必要时提醒你及时就医。`
    )
  }
  // 先"读取"宠物档案，拼出关键信息行
  const facts: string[] = []
  if (pet.breedName || pet.categoryName) facts.push(pet.breedName || (pet.categoryName as string))
  const age = petAge(pet.birthday)
  if (age) facts.push(age)
  facts.push(genderText(pet.gender))
  if (pet.weight != null) facts.push(`${pet.weight}kg`)
  facts.push(pet.neutered === 1 || pet.neutered === true ? '已绝育' : '未绝育')
  const factLine = facts.filter(Boolean).join(' · ')

  let text = intro
  text += `📋 已为你读取「${pet.name}」的宠物档案：${factLine}。\n\n`
  if (pet.description) {
    text += `档案备注：${pet.description}\n\n`
  }
  text +=
    `接下来你可以直接描述它的症状，或上传照片，我会结合这份档案给出更针对性的建议，必要时提醒你及时就医。`
  return text
}

// 新建对话：重新初始化会话，并让 AI 先读取当前宠物档案
function newConversation() {
  if (quotaExhausted.value) {
    ElMessage.warning(`您已达 AI 问诊使用上限（普通用户最多 ${quotaLimit()} 次），无法新建对话`)
    return
  }
  consult
    .init(currentPet.value?.id)
    .then(() => consult.greet(greetingText()))
    .catch(() => {})
}

// 实时问答专属会话，按当前选中宠物绑定 petId（让 AI 更精准）
onMounted(async () => {
  // 进入页面时拉一下今日历史（不阻塞 UI）
  consult.loadTodayHistory()
  await loadQuickSymptoms()
  loadKbArticles()
  await loadPets()
  // 带 ?session= 时载入历史会话；否则用当前选中宠物初始化新会话
  try {
    const sessionId = route.query.session
    if (sessionId) {
      await consult.loadConsultation(Number(sessionId))
    } else {
      await consult.init(currentPet.value?.id)
      // 初始化成功后，AI 先自我介绍
      consult.greet(greetingText())
    }
  } catch (e: any) {
    initError.value = e?.message || '会话初始化失败'
  }
  // 兜底：刷新页面后 store 没用户信息，主动拉一次
  if (!userStore.user && userStore.isLoggedIn) {
    userStore.fetchUser()
  }
  // 加载 AI 问诊额度（普通用户上限展示）
  loadQuota()
})

async function loadPets() {
  petsLoading.value = true
  try {
    pets.value = (await petApi.list()) || []
    // 首次进入默认选中第一只；已选中的宠物若仍在列表则保持不变
    if (pets.value.length && !pets.value.find((p) => p.id === selectedPetId.value)) {
      selectedPetId.value = pets.value[0].id
    }
  } catch {
    pets.value = []
  } finally {
    petsLoading.value = false
  }
}

// 宠物年龄（由 birthday 推算）
function petAge(birthday?: string): string {
  if (!birthday) return ''
  const b = new Date(birthday)
  if (isNaN(b.getTime())) return ''
  const now = new Date()
  let months =
    (now.getFullYear() - b.getFullYear()) * 12 + (now.getMonth() - b.getMonth())
  if (now.getDate() < b.getDate()) months -= 1
  if (months < 0) months = 0
  if (months < 12) return `${months}个月`
  const years = Math.floor(months / 12)
  const rest = months % 12
  return rest ? `${years}岁${rest}个月` : `${years}岁`
}

const petMeta = computed(() => {
  const p = currentPet.value
  if (!p) return ''
  return [p.breedName, petAge(p.birthday), p.weight ? `${p.weight}kg` : '']
    .filter(Boolean)
    .join(' · ')
})

const messages = computed(() =>
  consult.messages.map((m) => ({
    role: (m.role === 'assistant' ? 'ai' : 'user') as 'ai' | 'user',
    text: m.content,
    images: m.images,
    streaming: (m as any).streaming === true,
    riskLevel: (m as any).riskLevel,
    isIntro: (m as any).isIntro === true,
  })),
)

// 滚到最下
async function scrollBottom() {
  await nextTick()
  if (chatFlow.value) {
    chatFlow.value.scrollTop = chatFlow.value.scrollHeight
  }
}

watch(messages, scrollBottom, { deep: true })

async function send() {
  const text = inputText.value.trim()
  if ((!text && pendingImages.value.length === 0) || consult.sending) return
  if (quotaExhausted.value) {
    ElMessage.warning(`您已达 AI 问诊使用上限（普通用户最多 ${quotaLimit()} 次）`)
    return
  }
  const images = pendingImages.value.slice()
  inputText.value = ''
  pendingImages.value = []
  try {
    await consult.send(text, images.length ? images : undefined)
  } catch {
    // store 已插入"发送失败"提示气泡；request 拦截器也 toast 过错误，这里不重复
  } finally {
    // 每次问诊后实时刷新剩余次数
    loadQuota()
  }
}

function triggerImage() {
  fileInput.value?.click()
}

function onImageSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files
  if (!files) return
  for (const file of Array.from(files)) {
    if (!file.type.startsWith('image/')) continue
    const reader = new FileReader()
    reader.onload = () => {
      if (typeof reader.result === 'string') pendingImages.value.push(reader.result)
    }
    reader.readAsDataURL(file)
  }
  input.value = '' // 允许重复选择同一文件
}

function removeImage(idx: number) {
  pendingImages.value.splice(idx, 1)
}

function quickSend(s: string) {
  inputText.value = s
  send()
}

// ===== 今日历史抽屉 =====

async function openHistory() {
  historyOpen.value = true
  historyLoading.value = true
  try {
    await consult.loadTodayHistory()
  } finally {
    historyLoading.value = false
  }
}

function closeHistory() {
  historyOpen.value = false
}

async function selectConsultation(c: Consultation) {
  await consult.loadConsultation(c.id)
  closeHistory()
}

async function deleteConsultation(c: Consultation) {
  if (deletingId.value) return
  deletingId.value = c.id
  try {
    await consult.removeConsultation(c.id)
  } catch {
    // 忽略，保留列表
  } finally {
    deletingId.value = null
  }
}

function fmtTime(s: string): string {
  // createdAt 形如 "yyyy-MM-dd HH:mm:ss" → 显示 "M月D日 HH:mm"
  if (!s) return ''
  const [datePart, timePart] = s.split(' ')
  if (!datePart) return s
  const [, m, d] = datePart.split('-')
  const t = (timePart || '').substring(0, 5)
  return `${parseInt(m, 10)}月${parseInt(d, 10)}日 ${t || ''}`.trim()
}

// ===== 右栏：健康知识库（真实交互）=====
// 知识库卡片标题 → 进入宠友百科列表
function goKnowledge() {
  router.push({ name: 'Knowledge' })
}

// ===== 健康知识库「推荐问诊」：动态拉取数据库真实文章 =====
interface KbArticle {
  id: number
  title: string
  summary: string
  cover: string
  viewCount: number
}
const kbArticles = ref<KbArticle[]>([])
const kbLoading = ref(false)

// 简易摘要：去 HTML/Markdown 标记，截前 46 字
function stripHtml(s?: string): string {
  if (!s) return ''
  const plain = s.replace(/<[^>]+>/g, '').replace(/[#>*_`]/g, '').replace(/\s+/g, ' ').trim()
  return plain.length > 46 ? plain.slice(0, 46) + '...' : plain
}

async function loadKbArticles() {
  kbLoading.value = true
  try {
    const res: any = await communityApi.articleList(undefined, 1, 8)
    const list = (res?.list ?? res?.records ?? []) as any[]
    // 按浏览量降序作为「推荐」排序，取前 4 篇
    kbArticles.value = list
      .map((a: any) => ({
        id: a.id,
        title: a.title,
        summary: stripHtml(a.content),
        cover: resolveCover(a.cover),
        viewCount: a.view_count ?? a.viewCount ?? 0,
      }))
      .sort((x: KbArticle, y: KbArticle) => y.viewCount - x.viewCount)
      .slice(0, 4)
  } catch (e: any) {
    ElMessage.error(e?.message || '知识库加载失败')
    kbArticles.value = []
  } finally {
    kbLoading.value = false
  }
}

// 点击推荐问诊条目 → 直接进入文章详情页
function goArticle(a: KbArticle) {
  router.push(`/knowledge/${a.id}`)
}

// 超级宠物卡（VIP）点击跳转到会员开通页
function goVipOpen() {
  router.push('/vip/open')
}
</script>

<template>
  <div class="consult-view">
    <div class="consult-container">
      <!-- 左栏宠物档案 -->
      <aside class="pet-aside">
        <!-- 加载中 -->
        <div v-if="petsLoading" class="pet-card pet-loading">
          <span class="material-symbols-outlined spin">progress_activity</span>
          <p>加载宠物信息...</p>
        </div>

        <!-- 有宠物 -->
        <div v-else-if="currentPet" class="pet-card">
          <button
            v-if="pets.length > 1"
            class="pet-switch-trigger"
            type="button"
            title="切换宠物"
            @click="switchOpen = true"
          >
            <span class="material-symbols-outlined">swap_horiz</span>
          </button>
          <div class="pet-avatar-wrap">
            <img
              v-if="currentPet.avatar"
              :src="currentPet.avatar"
              :alt="currentPet.name"
              class="pet-avatar"
            />
            <div v-else class="pet-avatar pet-avatar-fallback">
              <span class="material-symbols-outlined">pets</span>
            </div>
            <span class="online-dot"></span>
          </div>
          <h2 class="pet-name">{{ currentPet.name }}</h2>
          <p class="pet-meta">{{ petMeta || '暂无详细资料' }}</p>

          <div v-if="currentPet.description" class="pet-record">
            <span class="record-label">备注</span>
            <p class="record-val">{{ currentPet.description }}</p>
          </div>
        </div>

        <!-- 无宠物空态 -->
        <div v-else class="pet-card pet-empty">
          <span class="material-symbols-outlined pet-empty-icon">pets</span>
          <p class="pet-empty-title">还没有添加宠物</p>
          <p class="pet-empty-tip">添加宠物档案后，问诊会更精准</p>
          <RouterLink to="/pet/create" class="pet-empty-btn">去添加宠物</RouterLink>
        </div>

        <!-- AI 问诊额度进度（普通用户上限展示，实时更新） -->
        <div v-if="quota" class="quota-card">
          <div class="quota-head">
            <span class="material-symbols-outlined quota-icon">monitor_heart</span>
            <span class="quota-title">AI 问诊额度</span>
            <span v-if="quota.unlimited" class="quota-tag unlimited">VIP 无限次</span>
          </div>
          <template v-if="!quota.unlimited">
            <div class="quota-bar">
              <div
                class="quota-fill"
                :class="{ full: quota.remaining === 0 }"
                :style="{ width: quotaPercent + '%' }"
              ></div>
            </div>
            <div class="quota-meta">
              <span>已用 {{ quota.used }} / {{ quota.limit }}</span>
              <span class="quota-remain" :class="{ zero: quota.remaining === 0 }">剩余 {{ quota.remaining }} 次</span>
            </div>
          </template>
          <p v-else class="quota-vip-tip">🎉 尊享全年无限次免费 AI 问诊</p>
        </div>
        <div v-else-if="quotaError" class="quota-card quota-card-error">
          <div class="quota-head">
            <span class="material-symbols-outlined quota-icon">error</span>
            <span class="quota-title">AI 问诊额度</span>
          </div>
          <p class="quota-error-text">额度信息加载失败</p>
          <button class="quota-retry" type="button" @click="loadQuota(true)">点击重试</button>
        </div>
        <div v-else-if="quotaLoading" class="quota-card quota-loading">
          <span class="material-symbols-outlined spin">progress_activity</span>
          <span>额度加载中…</span>
        </div>

        <div
          class="vip-card"
          :class="{ 'is-vip': isVip }"
          role="button"
          tabindex="0"
          @click="goVipOpen"
          @keydown.enter="goVipOpen"
          @keydown.space.prevent="goVipOpen"
        >
          <div class="vip-glow"></div>
          <div class="vip-info">
            <div class="vip-head">
              <span class="material-symbols-outlined vip-crown">workspace_premium</span>
              <h3 class="vip-title">超级宠物卡</h3>
              <span v-if="isVip" class="vip-badge">已开通</span>
            </div>
            <p class="vip-desc">享全年无限次免费问诊，专属宠物医生在线守候</p>
            <div class="vip-perks">
              <span class="vip-perk">
                <span class="material-symbols-outlined">check_circle</span>无限次问诊
              </span>
              <span class="vip-perk">
                <span class="material-symbols-outlined">check_circle</span>会员专属折扣
              </span>
            </div>
          </div>
          <button v-if="!isVip" class="vip-btn" type="button" @click.stop="goVipOpen">立即开通</button>
          <button v-else class="vip-btn vip-btn-done" type="button" @click.stop="goVipOpen">查看权益</button>
        </div>
      </aside>

      <!-- 中栏聊天区 -->
      <section class="chat-section">
        <header class="chat-header">
          <div class="chat-doctor">
            <div class="doctor-avatar">
              <span class="material-symbols-outlined">psychiatry</span>
            </div>
            <div>
              <h1 class="doctor-name">PetMate AI 资深医生</h1>
              <div class="doctor-status">
                <span class="status-dot"></span>
                <span>正在为您提供实时专业建议</span>
              </div>
            </div>
          </div>
          <div class="chat-actions">
            <button class="chat-new" type="button" :disabled="quotaExhausted" @click="newConversation" title="新建对话">
              <span class="material-symbols-outlined">add_comment</span>
              <span class="chat-new-text">新建对话</span>
            </button>
            <button
              class="chat-history"
              :class="{ active: consult.todayHistoryCount > 0 }"
              @click="openHistory"
              :title="`今日历史 (${consult.todayHistoryCount})`"
            >
              <span class="material-symbols-outlined">history</span>
              <span v-if="consult.todayHistoryCount > 0" class="history-badge">
                {{ consult.todayHistoryCount }}
              </span>
            </button>
          </div>
        </header>

        <!-- 额度用尽醒目提示 -->
        <div v-if="quotaExhausted" class="quota-banner">
          <span class="material-symbols-outlined">block</span>
          <span>您已达 AI 问诊使用上限（普通用户最多 {{ quotaLimit() }} 次）。如需继续，可开通 VIP 享受无限次问诊。</span>
        </div>

        <div ref="chatFlow" class="chat-flow">
          <div v-if="initError" class="msg-row ai">
            <div class="msg-bubble ai">
              <p>{{ initError }}，请稍后再试。</p>
            </div>
          </div>
          <div
            v-for="(m, i) in messages"
            :key="i"
            class="msg-row"
            :class="m.role"
          >
            <div v-if="m.role === 'ai'" class="msg-avatar ai-avatar">
              <span class="material-symbols-outlined">auto_awesome</span>
            </div>
            <div
              class="msg-bubble"
              :class="[m.role === 'ai' ? 'ai' : 'user', { typing: m.streaming && !m.text }]"
            >
              <div v-if="m.images && m.images.length" class="msg-images">
                <img v-for="(img, idx) in m.images" :key="idx" :src="img" alt="图片" />
              </div>
              <!-- AI 消息：流式阶段也走模板化渲染（每 chunk 重跑 formatAiAnswer），让结构随打字实时成型；尾部追加 caret 光标 -->
              <template v-if="m.role === 'ai'">
                <div
                  v-if="m.text"
                  class="ai-rich"
                  :class="{ streaming: m.streaming }"
                  v-html="formatAiRich(m)"
                ></div>
                <div v-if="m.text && !m.streaming && !m.isIntro" class="ai-foot-bar">
                  <div class="ai-foot-note">
                    <span class="material-symbols-outlined ai-foot-icon">info</span>
                    <span>内容均为 AI 生成，仅供参考</span>
                  </div>
                </div>
                <div v-else-if="!m.text" class="typing-dots">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                </div>
              </template>
              <!-- 用户消息：保持纯文本（避免误把 ## 当 markdown 渲染） -->
              <p v-else-if="m.text" class="msg-raw">{{ m.text }}</p>
              <template v-else-if="m.streaming">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </template>
            </div>
            <div v-if="m.role === 'user'" class="msg-avatar user-avatar">
              <img v-if="userAvatar" :src="userAvatar" :alt="userName" />
              <span v-else class="material-symbols-outlined">person</span>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <div class="quick-symptoms">
            <button
              v-for="s in quickSymptoms"
              :key="s.key"
              class="quick-btn"
              @click="quickSend(s.prompt)"
            >
              {{ s.label }}
            </button>
          </div>
          <div v-if="pendingImages.length" class="pending-images">
            <div v-for="(img, idx) in pendingImages" :key="idx" class="pending-img">
              <img :src="img" alt="待发送图片" />
              <button class="pending-remove" type="button" @click="removeImage(idx)" title="移除图片">
                <span class="material-symbols-outlined">close</span>
              </button>
            </div>
          </div>
          <div class="input-box">
            <button class="input-tool" type="button" :disabled="quotaExhausted" @click="triggerImage" title="上传图片">
              <span class="material-symbols-outlined">add_circle</span>
            </button>
            <textarea
              v-model="inputText"
              class="input-textarea"
              placeholder="描述症状或上传照片..."
              rows="1"
              :disabled="quotaExhausted"
              @keydown.enter.exact.prevent="send"
            >            </textarea>
            <button class="send-btn" :disabled="quotaExhausted" @click="send">
              <span class="material-symbols-outlined">arrow_upward</span>
            </button>
          </div>
          <input ref="fileInput" type="file" accept="image/*" multiple class="hidden-file" @change="onImageSelected" />
        </div>

        <!-- 今日历史抽屉 -->
        <transition name="drawer">
          <div v-if="historyOpen" class="history-drawer" @click.self="closeHistory">
            <div class="drawer-panel" @click.stop>
              <header class="drawer-header">
                <div class="drawer-title">
                  <span class="material-symbols-outlined">schedule</span>
                  <h3>今日问诊回顾</h3>
                  <span class="drawer-count">{{ consult.todayHistoryCount }} 次</span>
                </div>
                <button class="drawer-close" @click="closeHistory">
                  <span class="material-symbols-outlined">close</span>
                </button>
              </header>

              <div class="drawer-body">
                <div v-if="historyLoading" class="drawer-loading">
                  <span class="material-symbols-outlined">progress_activity</span>
                  加载中...
                </div>
                <div v-else-if="consult.todayHistory.length === 0" class="drawer-empty">
                  <span class="material-symbols-outlined">forum</span>
                  <p>今天还没有问诊记录</p>
                  <p class="drawer-empty-tip">开始一段新对话，记录会自动出现在这里</p>
                </div>
                <ul v-else class="history-list">
                  <li
                    v-for="c in consult.todayHistory"
                    :key="c.id"
                    class="history-item"
                    :class="{ active: consult.currentConsultationId === c.id }"
                    @click="selectConsultation(c)"
                  >
                    <div class="history-main">
                      <div class="history-row1">
                        <span class="history-time">{{ fmtTime(c.createdAt) }}</span>
                        <span class="history-msg-count">{{ c.messageCount }} 条消息</span>
                      </div>
                      <div class="history-preview">
                        {{ c.preview || c.title || '（无内容）' }}
                      </div>
                    </div>
                    <button
                      class="history-delete"
                      :disabled="deletingId === c.id"
                      title="删除此问诊"
                      @click.stop="deleteConsultation(c)"
                    >
                      <span class="material-symbols-outlined">
                        {{ deletingId === c.id ? 'progress_activity' : 'delete' }}
                      </span>
                    </button>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </transition>
      </section>

      <!-- 右栏知识库 + 推荐 -->
      <aside class="info-aside">
        <div class="knowledge-card">
          <div class="kb-head" role="button" @click="goKnowledge">
            <span class="material-symbols-outlined kb-icon">lightbulb</span>
            <h3 class="kb-title">健康知识库</h3>
            <span class="kb-more">更多 ›</span>
          </div>
          <div class="kb-list">
            <a
              v-for="a in kbArticles"
              :key="a.id"
              class="kb-item"
              @click="goArticle(a)"
            >
              <h4>{{ a.title }}</h4>
              <p>{{ a.summary || '点击阅读全文' }}</p>
              <span class="kb-meta">
                <span class="material-symbols-outlined">visibility</span>
                {{ a.viewCount }} 阅读
              </span>
            </a>
            <p v-if="kbLoading" class="kb-empty">加载中…</p>
            <p v-else-if="kbArticles.length === 0" class="kb-empty">暂无推荐内容</p>
          </div>
        </div>
      </aside>

      <!-- 切换宠物弹窗 -->
      <transition name="modal">
        <div v-if="switchOpen" class="pet-switch-modal" @click.self="switchOpen = false">
          <div class="pet-switch-panel">
            <header class="psw-head">
              <h3>选择宠物</h3>
              <button class="psw-close" type="button" @click="switchOpen = false">
                <span class="material-symbols-outlined">close</span>
              </button>
            </header>
            <ul class="psw-list">
              <li
                v-for="p in pets"
                :key="p.id"
                class="psw-item"
                :class="{ active: p.id === currentPet?.id }"
                @click="selectPet(p.id)"
              >
                <img v-if="p.avatar" :src="p.avatar" :alt="p.name" class="psw-avatar" />
                <div v-else class="psw-avatar psw-avatar-fallback">
                  <span class="material-symbols-outlined">pets</span>
                </div>
                <div class="psw-info">
                  <div class="psw-name">{{ p.name }}</div>
                  <div class="psw-meta">{{ [p.breedName, petAge(p.birthday)].filter(Boolean).join(' · ') }}</div>
                </div>
                <span v-if="p.id === currentPet?.id" class="material-symbols-outlined psw-check">check_circle</span>
              </li>
            </ul>
            <RouterLink to="/pet/create" class="psw-add" @click="switchOpen = false">
              <span class="material-symbols-outlined">add</span> 添加新宠物
            </RouterLink>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<!-- ========== AI 富文本 v-html 子元素样式（非 scoped，因为 v-html 插入的 DOM 没有 data-v 属性）========== -->
<style lang="scss">
// 主题色：与全站品牌橙统一（$color-brand = #ff7a32），保证视觉一致性。
$ai-accent: $color-brand;
$ai-accent-dark: $color-brand;
$ai-accent-soft: rgba(255, 122, 50, 0.10);
$ai-accent-softer: rgba(255, 122, 50, 0.04);

.ai-rich {
  // 强制 normal 渲染：避免 .msg-bubble 的 white-space:pre-wrap 干扰 v-html 出来的结构化 DOM
  white-space: normal;
  position: relative;

  // 关键词：橙色加粗 + 浅橙底药丸
  strong.ai-key {
    display: inline-block;
    padding: 1px 8px;
    margin-right: 4px;
    background: rgba(255, 122, 50, 0.12);
    color: #ff7a32;
    font-weight: 700;
    border-radius: 4px;
    font-size: 0.95em;
    line-height: 1.6;
  }

  // 行内链接
  a.ai-link {
    color: #ff7a32;
    text-decoration: none;
    font-weight: 600;
    border-bottom: 1px dashed rgba(255, 122, 50, 0.5);

    &:hover {
      color: #ff7a32;
      border-bottom-color: #ff7a32;
    }
  }

  // 行内 code
  code.ai-code {
    display: inline-block;
    padding: 1px 7px;
    margin: 0 2px;
    background: rgba(255, 122, 50, 0.08);
    color: #ff7a32;
    font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
    font-size: 0.9em;
    border-radius: 5px;
    border: 1px solid rgba(255, 122, 50, 0.15);
  }

  // ---------- 卡片头：标题条（左侧橙色竖条 + 主标题，统一品牌橙）----------
  .ai-title-bar {
    margin: 0 0 $spacing-md;
    padding: $spacing-sm $spacing-md;
    background: rgba(255, 122, 50, 0.05);
    border-left: 4px solid $ai-accent;
    border-radius: 0 $radius-default $radius-default 0;
  }

  .ai-h1 {
    font-family: $font-family-headline;
    font-size: 20px; // 长标题场景下比 headline-md 更克制
    font-weight: 800;
    color: $ai-accent;
    line-height: 1.45;
    margin: 0;
    word-break: normal; // 允许 CJK 自然换行，避免 keep-all 导致单字独行
    overflow-wrap: break-word;
  }

  // ---------- 小节：纯文字编号 + 标题（加大上下间距）----------
  .ai-sec {
    display: flex;
    align-items: baseline;
    gap: $spacing-xs;
    margin: $spacing-lg 0 $spacing-sm;
    animation: aiSecIn 0.32s cubic-bezier(0.16, 1, 0.3, 1) both;
  }

  .ai-sec-no {
    flex-shrink: 0;
    color: $ai-accent;
    font-size: $font-size-body-md;
    font-weight: 700;
    font-family: $font-family-headline;
  }

  .ai-sec-title {
    font-size: $font-size-body-md;
    font-weight: 700;
    color: $color-on-surface;
    letter-spacing: 0.01em;
  }

  // ---------- 段落 ----------
  .ai-p {
    color: $color-on-surface-variant;
    line-height: 1.8;
    margin: $spacing-sm 0;
  }

  // 标题条后的第一个段落/列表紧贴标题，避免过大空白
  .ai-title-bar + .ai-p,
  .ai-title-bar + .ai-list,
  .ai-title-bar + .ai-olist,
  .ai-title-bar + .ai-sec {
    margin-top: 0;
  }

  // ---------- 列表：极简小灰点 ----------
  .ai-list {
    list-style: none;
    padding-left: 0;
    margin: $spacing-xs 0 $spacing-md;

    li {
      position: relative;
      padding-left: $spacing-md;
      color: $color-on-surface-variant;
      line-height: 1.8;
      margin: 6px 0; // 与有序列表统一

      &::before {
        content: '';
        position: absolute;
        left: 4px;
        top: 0.85em;
        width: 4px;
        height: 4px;
        border-radius: 50%;
        background: $color-on-surface-variant;
        opacity: 0.5;
      }
    }
  }

  // ---------- 引用块 ----------
  .ai-quote {
    margin: $spacing-sm 0;
    padding: $spacing-sm $spacing-md;
    background: $ai-accent-softer;
    border-radius: $radius-default;
    color: $color-on-surface;
    line-height: 1.7;

    .ai-quote-mark {
      color: $ai-accent;
      font-size: 20px;
      font-weight: 700;
      margin-right: 4px;
      opacity: 0.7;
    }
  }

  // ---------- 分隔线 ----------
  .ai-divider {
    border: 0;
    height: 1px;
    background: linear-gradient(to right, transparent, rgba(255, 122, 50, 0.3), transparent);
    margin: $spacing-md 0;
  }

  // ---------- 表格 ----------
  .ai-table-wrap {
    overflow-x: auto;
    margin: $spacing-sm 0;
    border-radius: $radius-default;
    border: 1px solid rgba(255, 122, 50, 0.15);
  }

  .ai-table {
    width: 100%;
    border-collapse: collapse;
    font-size: $font-size-label-md;
    line-height: 1.5;

    th, td {
      padding: 8px 12px;
      text-align: left;
      border-bottom: 1px solid rgba(255, 122, 50, 0.1);
      color: $color-on-surface;
    }

    th {
      background: rgba(255, 122, 50, 0.10);
      color: $ai-accent-dark;
      font-weight: 700;
      font-family: $font-family-headline;
    }

    tr:last-child td {
      border-bottom: 0;
    }
  }

  // ---------- 有序列表 ----------
  .ai-olist {
    list-style: none;
    padding-left: 0;
    margin: $spacing-xs 0 $spacing-sm;
    counter-reset: ai-olist;

    li {
      position: relative;
      padding-left: $spacing-lg;
      color: $color-on-surface-variant;
      line-height: 1.8;
      margin: 6px 0; // 与无序列表统一
      counter-increment: ai-olist;

      &::before {
        content: counter(ai-olist) '.';
        position: absolute;
        left: 0;
        top: 0;
        color: $ai-accent;
        font-weight: 700;
        font-size: $font-size-body-md;
        font-family: $font-family-headline;
      }
    }
  }

  // ---------- 嵌套列表 ----------
  li.ai-list-sub {
    font-size: 0.92em;
    color: $color-on-surface-variant;
    opacity: 0.88;
  }
}

// ---------- 底部操作栏：免责声明（与 .ai-rich 同级，模板直接渲染）----------
.ai-foot-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: $spacing-sm;
  margin-top: $spacing-sm;
  padding-top: $spacing-sm;
  border-top: 1px solid rgba(255, 122, 50, 0.18);
}

.ai-foot-note {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  letter-spacing: 0.02em;
}

.ai-foot-icon {
  font-size: 14px;
  color: $ai-accent;
  opacity: 0.75;
}

// 响应式：移动端主标题缩小，底部免责声明精简为仅图标
@media (max-width: 768px) {
  .ai-title-bar {
    margin: 0 0 $spacing-sm;
    padding: 8px $spacing-sm;
    border-left-width: 3px;
  }
  .ai-h1 {
    font-size: 17px; // 移动端再小一号，保证长标题尽量单行或合理换行
  }
  .ai-foot-bar {
    gap: $spacing-xs;
  }
  .ai-foot-note span:last-child {
    display: none;
  }
}
</style>

<style lang="scss" scoped>
.consult-view {
  height: calc(100vh - 80px);
  background: $color-background;
  overflow: hidden;
}

.consult-container {
  @include container-max;
  height: 100%;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-lg;
  display: grid;
  grid-template-columns: 1fr;
  gap: $spacing-md;

  @include desktop {
    grid-template-columns: 1fr 2fr;
  }

  @include desktop {
    grid-template-columns: 1fr 2fr 320px;
  }
}

// ============ 左栏 ============
.pet-aside {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  overflow-y: auto;
}

.pet-card {
  position: relative;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  text-align: center;
}

.pet-avatar-wrap {
  position: relative;
  display: inline-block;
  margin-bottom: $spacing-md;
}

.pet-avatar {
  width: 96px;
  height: 96px;
  border-radius: $radius-full;
  object-fit: cover;
  border: 3px solid $color-brand;
}

.pet-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-secondary-container;

  .material-symbols-outlined {
    font-size: 44px;
    color: #ffffff;
    font-variation-settings: 'FILL' 1;
  }
}

// 加载 / 空态
.pet-loading,
.pet-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  color: $color-on-surface-variant;

  .spin {
    animation: spin 1s linear infinite;
  }
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.pet-empty-icon {
  font-size: 56px;
  color: $color-outline-variant;
  margin-bottom: $spacing-xs;
}

.pet-empty-title {
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.pet-empty-tip {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-md;
}

.pet-empty-btn {
  display: inline-block;
  padding: $spacing-xs $spacing-lg;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  text-decoration: none;

  &:hover {
    opacity: 0.9;
  }
}

.pet-switch-trigger {
  position: absolute;
  top: $spacing-sm;
  right: $spacing-sm;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  background: $color-surface-container-low;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    color: $color-brand;
    background: $color-brand-container;
    transform: rotate(180deg);
  }

  .material-symbols-outlined {
    font-size: 20px;
  }
}

.online-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 18px;
  height: 18px;
  background: $color-success;
  border: 3px solid #ffffff;
  border-radius: $radius-full;
}

.pet-name {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin-bottom: 4px;
}

.pet-meta {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-lg;
}

.pet-record {
  margin-bottom: $spacing-md;

  .record-label {
    display: block;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    text-align: left;
    margin-bottom: 4px;
  }

  .record-val {
    padding: $spacing-sm;
    background: $color-surface-container-low;
    border-radius: $radius-sm;
    font-size: $font-size-label-md;
    font-weight: 600;
    color: $color-on-surface;
    text-align: left;
  }
}

.vip-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  padding: $spacing-lg;
  background: linear-gradient(135deg, #fff6ef 0%, #ffe2cf 100%);
  border: 1px solid rgba(255, 122, 50, 0.22);
  border-radius: $radius-md;
  color: #43291a;
  box-shadow: $shadow-sm;
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1), box-shadow 0.25s ease, border-color 0.25s ease;

  &:hover {
    transform: translateY(-3px);
    box-shadow: 0 10px 24px rgba(255, 122, 50, 0.14);
    border-color: rgba(255, 122, 50, 0.4);
  }

  &:focus-visible {
    outline: 2px solid $color-brand;
    outline-offset: 2px;
  }
}

.vip-glow {
  position: absolute;
  right: -70px;
  bottom: -70px;
  width: 150px;
  height: 150px;
  background: rgba(255, 122, 50, 0.16);
  border-radius: $radius-full;
  filter: blur(44px);
}


.vip-info {
  position: relative;
  z-index: 10;
}

.vip-head {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;

  .vip-crown {
    color: #c9a36a;
    font-variation-settings: 'FILL' 1;
  }
}

.vip-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: #3a2415;
}

.vip-desc {
  margin-top: 4px;
  font-size: $font-size-label-md;
  color: rgba(67, 41, 26, 0.78);
  line-height: 1.5;
}

.vip-perks {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-md;
}

.vip-perk {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px $spacing-sm;
  background: rgba(255, 122, 50, 0.1);
  border-radius: $radius-default;
  font-size: $font-size-label-sm;
  color: #6b4427;

  .material-symbols-outlined {
    font-size: 16px;
    color: $color-brand;
  }
}

.vip-btn {
  position: relative;
  z-index: 10;
  align-self: flex-start;
  padding: $spacing-sm $spacing-xl;
  background: rgba(255, 122, 50, 0.08);
  color: $color-brand;
  border: 1px solid rgba(255, 122, 50, 0.4);
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: background $transition-fast, border-color $transition-fast, transform 0.15s ease;

  &:hover {
    background: rgba(255, 122, 50, 0.16);
    border-color: rgba(255, 122, 50, 0.6);
  }

  &:active {
    transform: scale(0.97);
  }
}

.vip-card.is-vip {
  background: linear-gradient(135deg, #eafff1 0%, #c9f7d8 100%);
  border-color: rgba(52, 199, 89, 0.35);

  &:hover {
    border-color: rgba(52, 199, 89, 0.6);
    box-shadow: 0 10px 24px rgba(52, 199, 89, 0.16);
  }
}

.vip-badge {
  margin-left: auto;
  padding: 2px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #34c759;
  border-radius: 999px;
}

.vip-btn-done {
  background: #34c759;
  color: #fff;
  border-color: #34c759;

  &:hover {
    background: #2bb14e;
    border-color: #2bb14e;
  }
}

// ============ AI 问诊额度卡片 ============
.quota-card {
  background: $color-surface-container-lowest;
  border: 1px solid rgba(223, 192, 179, 0.25);
  border-radius: $radius-md;
  padding: $spacing-md;
  box-shadow: $shadow-sm;
}

.quota-head {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-sm;
}

.quota-icon {
  font-size: 20px;
  color: $color-brand;
}

.quota-title {
  flex: 1;
  font-family: $font-family-headline;
  font-size: $font-size-label-md;
  font-weight: 600;
  color: $color-on-surface;
}

.quota-tag {
  padding: 2px 8px;
  border-radius: $radius-full;
  font-size: 11px;
  font-weight: 700;

  &.unlimited {
    background: linear-gradient(135deg, $color-brand, $color-brand-dark);
    color: #fff;
  }
}

.quota-bar {
  height: 8px;
  border-radius: $radius-full;
  background: $color-surface-container-high;
  overflow: hidden;
  margin-bottom: $spacing-xs;
}

.quota-fill {
  height: 100%;
  border-radius: $radius-full;
  background: linear-gradient(90deg, $color-brand, $color-brand-dark);
  transition: width 0.45s cubic-bezier(0.16, 1, 0.3, 1);

  &.full {
    background: $color-emergency;
  }
}

.quota-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

.quota-remain {
  font-weight: 600;
  color: $color-brand;

  &.zero {
    color: $color-emergency;
  }
}

.quota-vip-tip {
  margin: 0;
  font-size: $font-size-label-md;
  color: $color-brand;
  font-weight: 600;
}

.quota-card-error {
  border-color: rgba(255, 77, 79, 0.25);
  background: rgba(255, 77, 79, 0.04);
}

.quota-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xs;
  color: $color-on-surface-variant;
  min-height: 80px;
}

.quota-error-text {
  margin: 0 0 $spacing-sm;
  font-size: $font-size-label-md;
  color: $color-emergency;
}

.quota-retry {
  padding: $spacing-xs $spacing-md;
  background: rgba(255, 122, 50, 0.08);
  border: 1px solid rgba(255, 122, 50, 0.4);
  border-radius: $radius-md;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: background $transition-fast, border-color $transition-fast;

  &:hover {
    background: rgba(255, 122, 50, 0.16);
    border-color: rgba(255, 122, 50, 0.6);
  }
}

// ============ 额度用尽横幅 ============
.quota-banner {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin: 0 $spacing-lg $spacing-md;
  padding: $spacing-sm $spacing-md;
  background: rgba(255, 77, 79, 0.1);
  border: 1px solid rgba(255, 77, 79, 0.35);
  border-radius: $radius-md;
  color: $color-emergency;
  font-size: $font-size-body-md;
  font-weight: 600;
  line-height: 1.4;

  .material-symbols-outlined {
    font-size: 20px;
    flex-shrink: 0;
  }
}

// ============ 中栏聊天 ============
.chat-section {
  position: relative;
  display: flex;
  flex-direction: column;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-lg;
  border: 1px solid rgba(223, 192, 179, 0.2);
  overflow: hidden;
  min-height: 0;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md;
  background: #ffffff;
  border-bottom: 1px solid rgba(223, 192, 179, 0.1);
}

.chat-doctor {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.doctor-avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-secondary-container;
  border-radius: $radius-full;

  .material-symbols-outlined {
    font-size: 22px;
    color: #ffffff;
    font-variation-settings: 'FILL' 1;
  }
}

.doctor-name {
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.doctor-status {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;

  .status-dot {
    width: 8px;
    height: 8px;
    background: $color-success;
    border-radius: $radius-full;
    animation: pulse 1.5s infinite;
  }
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

// 右上角操作区（新建对话 + 历史）
.chat-actions {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
}

// 新建对话按钮
.chat-new {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px $spacing-sm;
  border: 1px solid rgba($color-brand, 0.35);
  border-radius: $radius-full;
  background: transparent;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: $font-weight-semibold;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast, transform $transition-fast;

  .material-symbols-outlined {
    font-size: 20px;
  }

  &:hover {
    background: $color-brand;
    color: #ffffff;
    transform: translateY(-1px);
  }

  &:active {
    transform: translateY(0);
  }

  @media (max-width: 480px) {
    .chat-new-text {
      display: none;
    }
    padding: 8px;
  }
}

// 历史按钮：带今日历史计数 badge
.chat-history {
  position: relative;
  padding: $spacing-xs;
  color: $color-on-surface-variant;
  cursor: pointer;

  &:hover,
  &.active {
    color: $color-brand;
  }

  .history-badge {
    position: absolute;
    top: -2px;
    right: -6px;
    min-width: 18px;
    height: 18px;
    padding: 0 5px;
    border-radius: $radius-full;
    background: #ef4444;
    color: #ffffff;
    font-size: 11px;
    font-weight: 700;
    line-height: 18px;
    text-align: center;
  }
}

// 抽屉 overlay
.history-drawer {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.35);
  z-index: 100;
  display: flex;
  justify-content: flex-end;
}

.drawer-panel {
  width: 100%;
  max-width: 420px;
  height: 100%;
  background: $color-surface-container-lowest;
  display: flex;
  flex-direction: column;
  box-shadow: $shadow-lg;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md $spacing-lg;
  border-bottom: 1px solid rgba(223, 192, 179, 0.15);
}

.drawer-title {
  display: flex;
  align-items: center;
  gap: $spacing-xs;

  h3 {
    font-size: $font-size-body-lg;
    font-weight: 700;
    color: $color-on-surface;
    margin: 0;
  }

  .drawer-count {
    padding: 2px 8px;
    border-radius: $radius-full;
    background: $color-secondary-container;
    color: $color-secondary;
    font-size: $font-size-label-sm;
    font-weight: 600;
  }

  .material-symbols-outlined {
    color: $color-brand;
  }
}

.drawer-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  cursor: pointer;

  &:hover {
    background: $color-surface-container-low;
  }
}

.drawer-body {
  flex: 1;
  overflow-y: auto;
  padding: $spacing-md;
}

.drawer-loading,
.drawer-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: $spacing-xl;
  color: $color-on-surface-variant;

  .material-symbols-outlined {
    font-size: 48px;
    margin-bottom: $spacing-sm;
    opacity: 0.4;
  }

  p {
    margin: 0;
  }

  .drawer-empty-tip {
    margin-top: $spacing-xs;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    opacity: 0.7;
  }
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  list-style: none;
  padding: 0;
  margin: 0;
}

.history-item {
  padding: $spacing-md;
  background: $color-surface-container-lowest;
  border: 1px solid rgba(223, 192, 179, 0.2);
  border-radius: $radius-md;
  cursor: pointer;
  transition: all $transition-fast;
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;

  &:hover {
    border-color: $color-brand;
    background: $color-surface-soft;

    .history-delete {
      opacity: 1;
    }
  }

  &.active {
    border-color: $color-brand;
    background: $color-surface-soft;
    box-shadow: $shadow-sm;
  }
}

.history-main {
  flex: 1;
  min-width: 0;
}

.history-delete {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  cursor: pointer;
  opacity: 0;
  transition: all $transition-fast;

  &:hover {
    background: rgba(239, 68, 68, 0.12);
    color: #ef4444;
  }

  &:disabled {
    cursor: default;
    opacity: 0.6;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.history-row1 {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-xs;
}

.history-time {
  font-family: $font-family-headline;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}

.history-msg-count {
  margin-left: auto;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.history-preview {
  font-size: $font-size-label-md;
  color: $color-on-surface;
  line-height: 1.5;
  @include text-ellipsis(2);
}

.drawer-enter-active,
.drawer-leave-active {
  transition: opacity 0.2s ease;

  .drawer-panel {
    transition: transform 0.25s ease;
  }
}

.drawer-enter-from,
.drawer-leave-to {
  opacity: 0;

  .drawer-panel {
    transform: translateX(100%);
  }
}

.chat-flow {
  flex: 1;
  padding: $spacing-lg;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  background: $color-background;
}

.msg-row {
  display: flex;
  gap: $spacing-md;
  max-width: 85%;
  animation: msgIn 0.32s cubic-bezier(0.16, 1, 0.3, 1) both;

  &.ai {
    align-self: flex-start;
  }

  &.user {
    // 用户消息：整组占满一行宽度，气泡在左、头像钉在最右
    align-self: stretch;
    width: 100%;
    max-width: 100%;
    display: grid;
    grid-template-columns: 1fr auto;
    align-items: flex-start;
  }
}

.msg-avatar {
  width: 32px;
  height: 32px;
  flex-shrink: 0;
  border-radius: $radius-full;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;

  &.ai-avatar {
    background: $color-secondary-container;

    .material-symbols-outlined {
      font-size: 18px;
      color: #ffffff;
      font-variation-settings: 'FILL' 1;
    }
  }

  &.user-avatar {
    border: 2px solid $color-brand;
    overflow: hidden;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .material-symbols-outlined {
      font-size: 20px;
      color: $color-brand;
      font-variation-settings: 'FILL' 1;
    }
  }
}

.msg-bubble {
  position: relative;
  padding: $spacing-md;
  border-radius: $radius-lg;
  box-shadow: $shadow-sm;
  line-height: 1.6;
  max-width: 80%;
  white-space: pre-wrap;
  word-break: break-word;

  // 气泡内 p 元素清掉浏览器默认 margin（避免与 .ai-rich 内部 p 的自定义 margin 冲突）
  p {
    margin: 0;
  }

  &.ai {
    // AI 气泡改为纯白底，去掉渐变/边框，只保留关键词 chip 做视觉强调
    background: #ffffff;
    color: $color-on-surface;
    border: 0;
    box-shadow: 0 1px 3px rgba(22, 29, 31, 0.06);
  }

  &.user {
    // 用户气泡：grid 第 1 列、贴右
    justify-self: end;
    // 改用淡橙容器底 + 深棕字，与 AI 气泡的浅色容器语言统一化
    background: $color-brand-container;
    color: $color-on-brand-container;
  }

  &.typing {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: $spacing-md $spacing-lg;
  }

  .dot {
    width: 6px;
    height: 6px;
    background: $color-secondary-container;
    border-radius: $radius-full;
    animation: bounce 1.4s infinite;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }
    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

@keyframes bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.5; }
  30% { transform: translateY(-8px); opacity: 1; }
}

@keyframes msgIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes aiSecIn {
  from { opacity: 0; transform: translateX(-6px); }
  to   { opacity: 1; transform: translateX(0); }
}

// 流式打字光标（逐字渲染时跟随在文本末尾）
.caret {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  background: currentColor;
  vertical-align: text-bottom;
  animation: caret-blink 1s step-end infinite;
}

// v-html 容器内嵌的光标：强制 inline-block（不写 ai-key 等词标签也能跟随末尾）
.caret-inline {
  display: inline-block;
  width: 2px;
  height: 1em;
  margin-left: 2px;
  background: $color-on-surface-variant;
  vertical-align: text-bottom;
  animation: caret-blink 1s step-end infinite;
}

@keyframes caret-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

// （流式阶段也用 .ai-rich，.msg-raw 保留以备不时之需）
.msg-raw {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

// 三点打字 loading（AI 还没回字时）
.typing-dots {
  display: inline-flex;
  gap: 4px;
  padding: 4px 0;

  .dot {
    width: 6px;
    height: 6px;
    border-radius: $radius-full;
    background: $color-on-surface-variant;
    animation: bounce 1.4s infinite;

    &:nth-child(2) { animation-delay: 0.2s; }
    &:nth-child(3) { animation-delay: 0.4s; }
  }
}

// ============ 输入区 ============
.chat-input {
  padding: $spacing-lg;
  background: #ffffff;
  border-top: 1px solid rgba(223, 192, 179, 0.1);
}

.quick-symptoms {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
}

.quick-btn {
  padding: $spacing-xs $spacing-md;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: $color-sky-blue-light;
    border-color: $color-secondary;
  }
}

.input-box {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm;
  background: $color-surface-container-low;
  border: 2px solid transparent;
  border-radius: $radius-full;
  transition: border-color $transition-fast;

  &:focus-within {
    border-color: $color-secondary;
  }
}

.input-tool {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-on-surface-variant;
  cursor: pointer;
  flex-shrink: 0;
  border-radius: $radius-full;
  transition: all $transition-fast;

  &:hover {
    color: $color-brand;
    background: $color-brand-container;
  }
}

.input-textarea {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  resize: none;
  padding: $spacing-xs 0;
  font-family: $font-family-body;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  line-height: 1.4;
  max-height: 128px;
  min-height: 40px;
}

.pending-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}
.pending-img {
  position: relative;
  width: 64px;
  height: 64px;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid rgba(0, 0, 0, 0.08);
}
.pending-img img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.pending-remove {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid #fff;
  border-radius: 50%;
  background: $color-emergency;
  color: #fff;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.35);
  opacity: 1; // 待发送区常显，更明显
  transition: transform $transition-fast, background $transition-fast;

  .material-symbols-outlined {
    font-size: 15px;
    line-height: 1;
  }
  &:hover {
    transform: scale(1.12);
    background: #d9363e;
  }
}
.msg-images {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.msg-images img {
  max-width: 160px;
  max-height: 160px;
  border-radius: 10px;
  object-fit: cover;
  display: block;
}

.hidden-file {
  display: none;
}

.send-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: $color-brand;
  color: #ffffff;
  border-radius: $radius-full;
  box-shadow: $shadow-md;
  cursor: pointer;
  flex-shrink: 0;
  transition: transform $transition-fast;

  &:hover {
    transform: scale(1.05);
  }

  &:active {
    transform: scale(0.95);
  }

  .material-symbols-outlined {
    font-variation-settings: 'FILL' 1;
  }
}

// 额度用尽时禁用输入区/按钮
.chat-new:disabled,
.input-tool:disabled,
.send-btn:disabled,
.input-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
  pointer-events: none;
}

// ============ 右栏 ============
.info-aside {
  display: none;
  flex-direction: column;
  gap: $spacing-md;
  overflow-y: auto;

  @include desktop {
    display: flex;
  }
}

.knowledge-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.3);
}

.kb-head {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-bottom: $spacing-md;
}

.kb-head {
  cursor: pointer;

  .kb-more {
    margin-left: auto;
    font-size: $font-size-label-sm;
    font-weight: 600;
    color: $color-secondary;
  }
}

.kb-icon {
  color: $color-warning;
}

.kb-title {
  font-size: $font-size-body-lg;
  font-weight: 700;
  color: $color-on-surface;
}

.kb-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.kb-item {
  display: block;
  padding: $spacing-sm;
  background: $color-surface-soft;
  border-radius: $radius-default;
  cursor: pointer;
  transition: background $transition-fast;

  &:hover {
    background: $color-sky-blue-light;

    h4 {
      color: $color-secondary;
    }
  }

  h4 {
    font-size: $font-size-label-md;
    font-weight: 700;
    color: $color-on-surface;
    margin-bottom: 4px;
  }

  p {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
  }

  .kb-meta {
    display: flex;
    align-items: center;
    gap: 2px;
    margin-top: 6px;
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;

    .material-symbols-outlined {
      font-size: 14px;
    }
  }
}

.kb-empty {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  text-align: center;
  padding: $spacing-sm 0;
}

// ============ 切换宠物弹窗 ============
.pet-switch-modal {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-md;
  background: rgba(0, 0, 0, 0.4);
}

.pet-switch-panel {
  width: 100%;
  max-width: 360px;
  background: $color-surface-container-lowest;
  border-radius: $radius-lg;
  box-shadow: $shadow-lg;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.psw-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-md $spacing-lg;
  border-bottom: 1px solid rgba(223, 192, 179, 0.15);

  h3 {
    font-family: $font-family-headline;
    font-size: $font-size-headline-md;
    font-weight: 700;
    color: $color-on-surface;
    margin: 0;
  }
}

.psw-close {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-full;
  color: $color-on-surface-variant;
  cursor: pointer;

  &:hover {
    background: $color-surface-container-low;
  }
}

.psw-list {
  list-style: none;
  margin: 0;
  padding: $spacing-sm;
  display: flex;
  flex-direction: column;
  gap: $spacing-xs;
  max-height: 320px;
  overflow-y: auto;
}

.psw-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm;
  border-radius: $radius-md;
  cursor: pointer;
  border: 1px solid transparent;
  transition: background $transition-fast, border-color $transition-fast;

  &:hover {
    background: $color-surface-container-low;
  }

  &.active {
    background: $color-brand-container;
    border-color: rgba(255, 122, 50, 0.4);
  }
}

.psw-avatar {
  width: 44px;
  height: 44px;
  border-radius: $radius-full;
  object-fit: cover;
  border: 2px solid $color-brand;
  flex-shrink: 0;

  &.psw-avatar-fallback {
    display: flex;
    align-items: center;
    justify-content: center;
    background: $color-secondary-container;

    .material-symbols-outlined {
      font-size: 24px;
      color: #ffffff;
      font-variation-settings: 'FILL' 1;
    }
  }
}

.psw-info {
  flex: 1;
  min-width: 0;
}

.psw-name {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}

.psw-meta {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.psw-check {
  color: $color-brand;
  font-variation-settings: 'FILL' 1;
  flex-shrink: 0;
}

.psw-add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: $spacing-md;
  border-top: 1px solid rgba(223, 192, 179, 0.15);
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;

  &:hover {
    background: $color-surface-container-low;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

// 弹窗动画
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;

  .pet-switch-panel {
    transition: transform 0.25s cubic-bezier(0.16, 1, 0.3, 1);
  }
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;

  .pet-switch-panel {
    transform: scale(0.92);
  }
}

// ============ 手机端响应式（≤768px）：单列堆叠 + 整页可滚，避免被 px-to-viewport 缩放裁切 ============
// 注意：块内一律用 rem / % / 1fr，不得用 px（px 会被插件再转成 vw 导致手机上缩小）
@include mobile {
  .consult-view {
    height: auto;
    min-height: calc(100vh - 64px);
    overflow: visible;
  }

  .consult-container {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    height: auto;
    padding-top: 0.75rem;
    padding-bottom: 0.75rem;
  }

  .pet-aside {
    overflow: visible;
  }

  .pet-card {
    padding: 0.75rem;
  }

  .pet-avatar {
    width: 72px;
    height: 72px;
  }

  .vip-card {
    padding: 0.75rem;
    gap: 0.5rem;
  }

  .vip-btn {
    align-self: stretch;
    text-align: center;
  }

  .chat-section {
    min-height: 72vh;
  }

  .chat-header {
    flex-wrap: wrap;
    gap: 0.5rem;
    padding: 0.75rem;
  }

  .chat-flow {
    padding: 0.75rem;
    gap: 0.75rem;
  }

  .msg-row {
    max-width: 100%;
  }

  .msg-bubble {
    max-width: 88%;
  }

  .chat-input {
    padding: 0.75rem;
  }

  .quick-symptoms {
    flex-wrap: nowrap;
    overflow-x: auto;
    @include scrollbar-hide;
    margin-bottom: 0.5rem;
  }

  .info-aside {
    display: flex;
  }

  .knowledge-card {
    padding: 0.75rem;
  }
}
</style>