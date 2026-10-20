import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { Consultation, ConsultMessage, ConsultHistoryRange } from '@/api/modules/consult'
import { consultApi } from '@/api/modules/consult'

/**
 * AI 问诊 Store
 *
 * v2.1：后端已升级为 GLM JSON mode + 结构化输出，
 *      AI 内容由 ConsultAiService.parseAiOutput 统一清洗，前端无需再兜底。
 */

export const useConsultStore = defineStore('consult', () => {
  const messages = ref<ConsultMessage[]>([])
  const history = ref<Consultation[]>([])
  const todayHistory = ref<Consultation[]>([])
  const currentConsultationId = ref<number | null>(null)
  const loading = ref(false)
  const sending = ref(false)

  // 风险等级（由 LLM 返回）
  const currentRiskLevel = ref<'urgent' | 'normal' | 'good'>('normal')

  const hasMessages = computed(() => messages.value.length > 0)
  const todayHistoryCount = computed(() => todayHistory.value.length)

  async function init(petId?: number) {
    messages.value = []
    currentConsultationId.value = null
    currentRiskLevel.value = 'normal'
    // 创建新会话
    const c = await consultApi.create(petId)
    currentConsultationId.value = c.id
  }

  /** 拉取所有历史（默认 all，可传 7d / 30d / today） */
  async function loadHistory(range: ConsultHistoryRange = 'all') {
    loading.value = true
    try {
      history.value = (await consultApi.history(range)) || []
    } finally {
      loading.value = false
    }
  }

  /** 专门拉一天内的历史，用于主页/页面顶部"今日回顾"卡片 */
  async function loadTodayHistory() {
    try {
      todayHistory.value = (await consultApi.history('today')) || []
    } catch (e) {
      todayHistory.value = []
    }
    return todayHistory.value
  }

  async function loadConsultation(consultationId: number) {
    loading.value = true
    try {
      currentConsultationId.value = consultationId
      messages.value = (await consultApi.detail(consultationId)) || []
    } finally {
      loading.value = false
    }
  }

  /** 进入问诊时，AI 先自我介绍（仅在会话为空时注入，避免重复） */
  function greet(text: string) {
    if (messages.value.length > 0) return
    messages.value.push({
      id: Date.now(),
      consultationId: currentConsultationId.value ?? 0,
      role: 'assistant',
      content: text,
      isIntro: true,
      createdAt: new Date().toISOString(),
    })
  }

  /** 删除某条历史会话；若删除的是当前会话，则清空并重新开一段新会话 */
  async function removeConsultation(consultationId: number) {
    await consultApi.remove(consultationId)
    todayHistory.value = todayHistory.value.filter((c) => c.id !== consultationId)
    history.value = history.value.filter((c) => c.id !== consultationId)
    if (currentConsultationId.value === consultationId) {
      await init()
    }
  }

  async function send(content: string, images?: string[]) {
    if (!currentConsultationId.value) {
      await init()
    }
    // 添加用户消息到 UI
    const userMsg: ConsultMessage = {
      id: Date.now(),
      consultationId: currentConsultationId.value!,
      role: 'user',
      content,
      images,
      createdAt: new Date().toISOString(),
    }
    messages.value.push(userMsg)

    // 流式助手占位气泡（先空，随 token 逐字增长）。
    // 注意：Vue3 响应式要求通过 reactive 代理引用去变更，故 push 后取回代理对象再 mutate。
    messages.value.push({
      id: Date.now() + 1,
      consultationId: currentConsultationId.value!,
      role: 'assistant',
      content: '',
      streaming: true,
      riskLevel: 'normal',
      createdAt: new Date().toISOString(),
    })
    const aiMsg = messages.value[messages.value.length - 1]

    sending.value = true
    try {
      await consultApi.sendStream(
        {
          consultationId: currentConsultationId.value!,
          content,
          images,
        },
        {
          onToken: (chunk) => {
            aiMsg.content += chunk
          },
          onDone: (data) => {
            aiMsg.id = data.id ?? aiMsg.id
            aiMsg.riskLevel = data.riskLevel
            aiMsg.products = data.products
            // 后端已剥离结构化 JSON 块；前端无需再清洗
            aiMsg.streaming = false
            // 用后端返回的真实消息 id 覆盖本地临时 id，保证后续单图删除能命中 DB
            if (data.userMessageId != null) userMsg.id = data.userMessageId
            if (aiMsg.riskLevel) currentRiskLevel.value = aiMsg.riskLevel
            // 刷新今日历史
            loadTodayHistory()
          },
          onError: (message) => {
            aiMsg.streaming = false
            const text = message || 'AI 服务异常，请稍后重试'
            if (!aiMsg.content) {
              aiMsg.content = `（${text}）`
            } else {
              aiMsg.content += `\n\n（${text}）`
            }
            ElMessage.error(text)
          },
        },
      )
      if (!aiMsg.content) {
        // AI 没给内容：替换成提示，避免气泡空白
        aiMsg.content = '抱歉，本次没收到 AI 的回复，请稍后再试。'
      }
    } catch (e: any) {
      // 连接级异常：把异常冒给 view 层 toast，不再静默
      aiMsg.streaming = false
      if (!aiMsg.content) {
        aiMsg.content = `（消息发送失败：${e?.message || '网络异常'}，请稍后重试）`
      }
    } finally {
      sending.value = false
    }
    return aiMsg
  }

  /** 永久删除某条消息里的某张图片：先调后端落库删除，成功后再本地移除 */
  async function removeMessageImage(messageId: number, imageIndex: number) {
    const msg = messages.value.find((m) => m.id === messageId)
    if (!msg || !msg.images || imageIndex < 0 || imageIndex >= msg.images.length) return
    await consultApi.removeMessageImage(messageId, imageIndex)
    msg.images.splice(imageIndex, 1)
  }

  return {
    messages,
    history,
    todayHistory,
    todayHistoryCount,
    currentConsultationId,
    currentRiskLevel,
    loading,
    sending,
    hasMessages,
    init,
    loadHistory,
    loadTodayHistory,
    loadConsultation,
    removeConsultation,
    greet,
    removeMessageImage,
    send,
  }
})