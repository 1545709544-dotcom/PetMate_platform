<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  petApi,
  type Pet,
  type HealthRecord,
  type HealthRecordType,
  type PetReminder,
  petGenderLabel,
  petAgeText,
  PET_QUICK_PRESETS,
  type QuickPreset,
} from '@/api/modules/pet'
import { usePetStore } from '@/stores/pet'

const route = useRoute()
const router = useRouter()
const petStore = usePetStore()

// 当前激活的宠物 + 全部宠物(供顶部 chip 切换)
const pet = ref<Pet | null>(null)
const pets = computed<Pet[]>(() => petStore.pets)
const loading = ref(false)
const error = ref<string | null>(null)

// 健康日程 + 成长日记照片
const healthRecords = ref<HealthRecord[]>([])
const diaryPhotos = ref<Array<{ id: number; url: string; caption?: string; createdAt?: string }>>([])
const diaryLoading = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
// 成长日记:全部照片(详情页仅预览前 N 张,完整照片墙跳转到 /pet/photos/:id)
const diaryAll = ref<Array<{ id: number; url: string; caption?: string; createdAt?: string }>>([])
const DIARY_PREVIEW = 6

// 相册固定 6 个槽位(1 主图 + 右侧上下 2 + 底部 3);不足 6 张时缺失槽位以 null 占位(留空、不隐藏)
const diarySlots = computed(() => {
  const arr: Array<null | { id: number; url: string; caption?: string; createdAt?: string }> = []
  for (let i = 0; i < 6; i++) arr.push(diaryPhotos.value[i] ?? null)
  return arr
})

// 健康日程 Tab（对齐参考设计：体检报告/病历本/过敏史/手术记录）
interface ScheduleTab {
  key: string
  label: string
  icon: string
  // 映射到后端 recordType（空串=全部）
  filterTypes: string[]
}

const scheduleTabs: ScheduleTab[] = [
  { key: 'vaccine', label: '疫苗', icon: 'vaccines', filterTypes: ['vaccine'] },
  { key: 'deworming', label: '驱虫', icon: 'bug_report', filterTypes: ['deworming'] },
  { key: 'checkup', label: '体检报告', icon: 'assessment', filterTypes: ['checkup'] },
  { key: 'illness', label: '病历本', icon: 'medical_information', filterTypes: ['illness'] },
  { key: 'allergy', label: '过敏史', icon: 'coronavirus', filterTypes: ['allergy'] },
  { key: 'surgery', label: '手术记录', icon: 'surgical', filterTypes: ['surgery'] },
]
const activeTabKey = ref<string>('')

// ============ 关怀提醒（待办任务） ============
const reminders = ref<PetReminder[]>([])
const remindersLoading = ref(false)

// 提醒 type → 可过滤的 tab key（提醒只有 4 种类型）
const reminderTabMap: Record<string, string> = {
  vaccine: 'vaccine',
  deworming: 'deworming',
  checkup: 'checkup',
  custom: '',
}

// 待办提醒（未完成），按当前 Tab 过滤
const pendingReminders = computed(() => {
  const list = reminders.value.filter((r) => r.status !== 'done')
  if (!activeTabKey.value) return list
  return list.filter((r) => reminderTabMap[r.type] === activeTabKey.value)
})

// 逾期 / 即将到期 计数（用于顶部提示条）
const reminderStats = computed(() => {
  let overdue = 0
  let soon = 0
  reminders.value
    .filter((r) => r.status !== 'done')
    .forEach((r) => {
      const d = daysUntil(r.dueDate)
      if (d !== null && d < 0) overdue++
      else if (d !== null && d <= r.advanceDays) soon++
    })
  return { overdue, soon, total: overdue + soon }
})

// 把后端 recordType 翻译到视觉（图标+颜色）
const typeStyleMap: Record<string, { icon: string; color: 'orange' | 'green' | 'red' | 'blue' | 'purple' }> = {
  vaccine:   { icon: 'vaccines', color: 'orange' },
  deworming: { icon: 'bug_report', color: 'green' },
  checkup:   { icon: 'assessment', color: 'blue' },
  weight:    { icon: 'monitor_weight', color: 'purple' },
  illness:   { icon: 'sick', color: 'red' },
  allergy:   { icon: 'coronavirus', color: 'red' },
  surgery:   { icon: 'surgical', color: 'red' },
}

function mapType(t: string) {
  return typeStyleMap[t] || { icon: 'medical_services', color: 'blue' }
}

// 当前激活 Tab 对应的过滤类型
const activeFilterTypes = computed(() => {
  if (!activeTabKey.value) return [] // 全部
  const tab = scheduleTabs.find((t) => t.key === activeTabKey.value)
  return tab?.filterTypes || []
})

// 计算属性
const petId = computed(() => Number(route.params.id))

const ageLabel = computed(() => petAgeText(pet.value?.birthday))

const genderLabel = computed(() => petGenderLabel(pet.value?.gender))

const neuteredLabel = computed(() => (pet.value?.neutered === 1 ? '已绝育' : '未绝育'))

// 后端记录类型定义（保留供 mapType 形参使用）
type RecordType = HealthRecord['recordType']

// 把 MM-DD 字符串格式化为 05.15
function fmtShortDate(s?: string): string {
  if (!s) return ''
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(s)
  if (!m) return s
  return `${m[2]}.${m[3]}`
}

// 计算还剩天数(基于 nextDate)
function daysUntil(s?: string): number | null {
  if (!s) return null
  const d = new Date(s)
  if (isNaN(d.getTime())) return null
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  d.setHours(0, 0, 0, 0)
  return Math.round((d.getTime() - today.getTime()) / 86400000)
}

// 待办提醒条目（UI 视图模型）：紧迫度文案 + 配色
const reminderItems = computed(() =>
  pendingReminders.value.map((r) => {
    const meta = mapType(r.type)
    const d = daysUntil(r.dueDate)
    let urgency: 'overdue' | 'soon' | 'normal' = 'normal'
    let dueText: string
    if (d === null) {
      dueText = r.dueDate
    } else if (d < 0) {
      urgency = 'overdue'
      dueText = `已逾期 ${-d} 天`
    } else if (d === 0) {
      urgency = 'overdue'
      dueText = '今天到期'
    } else if (d <= r.advanceDays) {
      urgency = 'soon'
      dueText = `还剩 ${d} 天`
    } else {
      dueText = `${fmtShortDate(r.dueDate)} 到期`
    }
    const cycleText = r.cycleDays > 0 ? ` · 每 ${r.cycleDays} 天` : ''
    return {
      raw: r,
      id: r.id,
      icon: meta.icon,
      color: meta.color,
      title: r.title,
      meta: dueText + cycleText,
      urgency,
    }
  })
)

// 历史记录（已完成的健康日志），按当前 Tab 过滤 + 折叠
const RECORD_PREVIEW = 4
const showAllRecords = ref(false)

const filteredRecords = computed(() => {
  let list = [...healthRecords.value]
  if (activeFilterTypes.value.length) {
    list = list.filter((r) => activeFilterTypes.value.includes(r.recordType))
  }
  return list.sort((a, b) => (a.date > b.date ? -1 : 1))
})

const recordItems = computed(() => {
  const list = showAllRecords.value
    ? filteredRecords.value
    : filteredRecords.value.slice(0, RECORD_PREVIEW)
  return list.map((r) => {
    const meta = mapType(r.recordType)
    return {
      id: r.id,
      icon: meta.icon,
      color: meta.color,
      title: r.title || '健康记录',
      meta: `${fmtShortDate(r.date)}${r.content ? ' · ' + r.content : ''}`,
    }
  })
})

// 各 Tab 记录数量(供 tab 计数徽标)
const tabCounts = computed<Record<string, number>>(() => {
  const c: Record<string, number> = { '': healthRecords.value.length } // 全部
  scheduleTabs.forEach((tab) => {
    c[tab.key] = healthRecords.value.filter((r) => tab.filterTypes.includes(r.recordType)).length
  })
  return c
})

// ============ 数据加载 ============

async function loadPet() {
  if (!petId.value || isNaN(petId.value)) {
    error.value = '无效的宠物 ID'
    return
  }
  loading.value = true
  error.value = null
  try {
    // 先确保全部宠物已拉取(chip 切换器要用)
    if (pets.value.length === 0) {
      try { await petStore.fetch() } catch { /* 列表失败不阻塞主显示 */ }
    }
    // 详情数据始终以接口最新为准，避免 store 缓存导致编辑返回后显示旧值
    const data = await petApi.detail(petId.value)
    pet.value = data
    // 同步回写 store，保证顶部 chip / 列表与详情一致
    const idx = petStore.pets.findIndex((p) => p.id === data.id)
    if (idx >= 0) petStore.pets[idx] = data
    else petStore.pets.push(data)
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }

  // 拉健康记录 + 关怀提醒 + 成长日记(后台并行,失败不阻塞主卡片)
  loadHealth()
  loadReminders()
  loadDiaryPhotos()
}

async function loadHealth() {
  if (!pet.value) return
  try {
    healthRecords.value = (await petApi.health(pet.value.id)) || []
  } catch {
    healthRecords.value = []
  }
}

async function loadReminders() {
  if (!pet.value) return
  remindersLoading.value = true
  try {
    reminders.value = (await petApi.reminders(pet.value.id)) || []
  } catch {
    reminders.value = []
  } finally {
    remindersLoading.value = false
  }
}

async function loadDiaryPhotos() {
  if (!pet.value) return
  diaryLoading.value = true
  try {
    const list = (await petApi.photos(pet.value.id)) || []
    // 保存全部照片,初始仅展示前 5 张
    diaryAll.value = list
    diaryPhotos.value = list.slice(0, DIARY_PREVIEW)
  } catch {
    diaryAll.value = []
    diaryPhotos.value = []
  } finally {
    diaryLoading.value = false
  }
}

// 跳转到成长日记照片墙(完整照片列表 / 上传 / 删除)
function viewAllPhotos() {
  router.push(`/pet/photos/${petId.value}`)
}

watch(petId, loadPet)
onMounted(() => {
  loadPet()
})

// ============ 行为 ============

function switchPet(id: number) {
  if (id === petId.value) return
  router.push(`/pet/detail/${id}`)
}

function goCreate() {
  router.push('/pet/create')
}

function goEdit() {
  if (!pet.value) return
  router.push(`/pet/edit/${pet.value.id}`)
}

// ============ 关怀提醒：完成 / 删除 ============

const completing = ref<number | null>(null)

// 标记完成：后端若配置了循环周期会自动生成下一次
async function completeReminder(r: PetReminder) {
  if (completing.value) return
  completing.value = r.id
  try {
    const res = await petApi.doneReminder(r.id)
    if (res?.cycled && res.nextDueDate) {
      ElMessage.success(`「${r.title}」已完成,下次提醒 ${res.nextDueDate}`)
    } else {
      ElMessage.success(`「${r.title}」已完成`)
    }
    await loadReminders()
  } catch (err: any) {
    ElMessage.error(err?.message || '操作失败')
  } finally {
    completing.value = null
  }
}

async function removeReminder(r: PetReminder) {
  try {
    await ElMessageBox.confirm(`确定删除提醒「${r.title}」吗?`, '删除提醒', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await petApi.deleteReminder(r.id)
    reminders.value = reminders.value.filter((x) => x.id !== r.id)
    ElMessage.success('已删除')
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

// ============ 快速记录（一键补录 + 自动排下次） ============

// 周期取自常见兽医建议：疫苗年免、驱虫季度、体检半年（集中维护于 api/modules/pet.ts 的 PET_QUICK_PRESETS）
const quickPresets: QuickPreset[] = PET_QUICK_PRESETS

const quickOpen = ref(false)
const quickSaving = ref(false)
const quickForm = ref({
  type: 'vaccine' as HealthRecordType,
  title: '',
  content: '',
  date: todayISO(),
  reminder: true,
  nextDate: '',
  cycleDays: 365,
  placeholder: '',
})

function todayISO() {
  const d = new Date()
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function addDaysISO(base: string, days: number) {
  const d = new Date(base)
  if (isNaN(d.getTime())) return ''
  d.setDate(d.getDate() + days)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

function openQuickAdd(preset: QuickPreset) {
  const today = todayISO()
  quickForm.value = {
    type: preset.type,
    title: preset.title,
    content: '',
    date: today,
    reminder: preset.cycleDays > 0,
    nextDate: preset.cycleDays > 0 ? addDaysISO(today, preset.cycleDays) : '',
    cycleDays: preset.cycleDays,
    placeholder: preset.placeholder,
  }
  quickOpen.value = true
}

// 记录日期或周期变化时,自动重算下次到期日
function syncNextDate() {
  if (!quickForm.value.reminder) return
  if (quickForm.value.cycleDays > 0) {
    quickForm.value.nextDate = addDaysISO(quickForm.value.date, quickForm.value.cycleDays)
  }
}

async function submitQuickAdd() {
  if (!pet.value) return
  const f = quickForm.value
  if (!f.title.trim()) {
    ElMessage.warning('请填写记录名称')
    return
  }
  if (!f.date) {
    ElMessage.warning('请选择记录日期')
    return
  }
  if (f.reminder && !f.nextDate) {
    ElMessage.warning('开启提醒时需要设置下次到期日')
    return
  }
  quickSaving.value = true
  try {
    await petApi.addHealth({
      petId: pet.value.id,
      recordType: f.type,
      title: f.title.trim(),
      content: f.content.trim() || undefined,
      date: f.date,
      reminder: f.reminder,
      nextDate: f.reminder ? f.nextDate : undefined,
      cycleDays: f.reminder ? f.cycleDays : 0,
    })
    ElMessage.success(f.reminder ? '已记录,并为你排好下次提醒' : '已记录')
    quickOpen.value = false
    await Promise.all([loadHealth(), loadReminders()])
  } catch (err: any) {
    ElMessage.error(err?.message || '保存失败')
  } finally {
    quickSaving.value = false
  }
}

function goHome() {
  router.push('/')
}

function uploadPhoto() {
  if (uploading.value) return
  fileInput.value?.click()
}

function onPhotosSelected(e: Event) {
  const input = e.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  // 立即清空,允许重复选择同一文件
  input.value = ''
  if (!files.length) return

  const MAX = 9
  const MAX_SIZE = 5 * 1024 * 1024
  const ok = files.every(
    (f) => /^image\/(jpeg|png|webp)$/.test(f.type) && f.size <= MAX_SIZE
  )
  if (!ok) {
    ElMessage.warning('仅支持 jpg / png / webp 格式,单张不超过 5MB')
    return
  }
  if (files.length > MAX) {
    ElMessage.warning(`一次最多上传 ${MAX} 张`)
    return
  }

  doUpload(files)
}

async function doUpload(files: File[]) {
  if (!pet.value) return
  uploading.value = true
  try {
    await petApi.uploadPhotos(pet.value.id, files)
    ElMessage.success(`已上传 ${files.length} 张照片`)
    await loadDiaryPhotos()
  } catch (err: any) {
    ElMessage.error(err?.message || '上传失败,请稍后重试')
  } finally {
    uploading.value = false
  }
}

async function deletePhoto(p: { id: number }) {
  try {
    await ElMessageBox.confirm('确定删除这张照片吗?删除后无法恢复。', '删除照片', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await petApi.deletePhoto(p.id)
    diaryAll.value = diaryAll.value.filter((x) => x.id !== p.id)
    diaryPhotos.value = diaryPhotos.value.filter((x) => x.id !== p.id)
    ElMessage.success('已删除')
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

// 内联 SVG 占位头像(无依赖)
function defaultAvatar() {
  return (
    'data:image/svg+xml;utf8,' +
    encodeURIComponent(
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><rect width="100" height="100" fill="#FFE2CC"/><text x="50" y="62" text-anchor="middle" font-size="48" font-family="Arial">🐾</text></svg>'
    )
  )
}

function avatarOf(p?: Pet | null) {
  return p?.avatar || defaultAvatar()
}
</script>

<template>
  <div class="pet-detail">
    <div class="dashboard">
      <!-- 加载 -->
      <div v-if="loading && !pet" class="state-card loading">
        <span class="spinner" aria-hidden="true"></span>
        <p>正在加载毛孩子的档案…</p>
      </div>

      <!-- 错误 -->
      <div v-else-if="error" class="state-card error">
        <span class="material-symbols-outlined">error_outline</span>
        <p>{{ error }}</p>
        <button class="retry-btn" @click="loadPet">重试</button>
      </div>

      <template v-else-if="pet">
        <!-- ============ 顶部 chip 切换 ============ -->
        <header class="pets-head">
          <h2 class="pets-title">我的宠物</h2>
          <button class="pets-add" @click="goCreate">
            <span class="material-symbols-outlined">add_circle</span>
            <span>添加新成员</span>
          </button>
        </header>

        <nav v-if="pets.length" class="pets-chips" aria-label="宠物切换">
          <button
            v-for="p in pets"
            :key="p.id"
            type="button"
            class="pets-chip"
            :class="{ active: p.id === petId }"
            @click="switchPet(p.id)"
          >
            <span class="chip-avatar">
              <img :src="avatarOf(p)" :alt="p.name" />
            </span>
            <span class="chip-name">{{ p.name }}</span>
          </button>
        </nav>

        <!-- ============ 中部双栏: 橙色档案 + 健康日程 ============ -->
        <section class="dashboard-grid">
          <div class="dash-left">
          <!-- LEFT: 橙色档案卡 -->
          <aside class="profile-card" aria-label="宠物档案">
            <button class="profile-edit" type="button" @click="goEdit" aria-label="编辑档案">
              <span class="material-symbols-outlined">edit</span>
              <span>编辑</span>
            </button>

            <div class="profile-head">
              <h1 class="profile-name">{{ pet.name }}</h1>
              <p class="profile-sub">
                {{ pet.breedName || '未分类品种' }} · {{ genderLabel }}
              </p>
            </div>

            <div class="profile-stats">
              <div class="profile-stat">
                <span class="stat-key">年龄</span>
                <span class="stat-val">{{ ageLabel }}</span>
              </div>
              <div class="profile-stat">
                <span class="stat-key">体重</span>
                <span class="stat-val">{{ pet.weight ?? '—' }} kg</span>
              </div>
              <div class="profile-stat">
                <span class="stat-key">绝育</span>
                <span class="stat-val">{{ neuteredLabel }}</span>
              </div>
              <div class="profile-stat">
                <span class="stat-key">生日</span>
                <span class="stat-val">{{ pet.birthday || '—' }}</span>
              </div>
            </div>
          </aside>

          <!-- ============ 成长日记（移入左栏） ============ -->
          <section class="diary-card" aria-label="成长日记">
            <header class="diary-head">
              <div class="diary-titles">
                <h3 class="diary-title">成长日记</h3>
                <p class="diary-sub">记录 {{ pet.name }} 每一个精彩瞬间</p>
              </div>
              <button class="diary-upload" type="button" :disabled="uploading" @click="uploadPhoto">
                <span v-if="uploading" class="spinner" aria-hidden="true"></span>
                <span v-else class="material-symbols-outlined" aria-hidden="true">add_a_photo</span>
                <span>{{ uploading ? '上传中…' : '上传照片' }}</span>
              </button>
              <input
                ref="fileInput"
                class="hidden-file"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                @change="onPhotosSelected"
              />
            </header>

            <div v-if="diaryLoading" class="diary-loading">
              <span class="spinner small" aria-hidden="true"></span>
              <span>加载成长记录…</span>
            </div>
            <div v-else-if="diaryAll.length" class="diary-collage">
              <div class="diary-row-top">
                <div
                  class="diary-photo diary-hero"
                  :class="{ 'is-empty': !diarySlots[0] }"
                  :style="diarySlots[0] ? { backgroundImage: `url(${diarySlots[0].url})` } : {}"
                  :title="diarySlots[0]?.caption || pet.name"
                >
                  <button v-if="diarySlots[0]" class="diary-del" type="button" @click.stop="deletePhoto(diarySlots[0])" aria-label="删除照片">
                    <span class="material-symbols-outlined" aria-hidden="true">close</span>
                  </button>
                </div>
                <div class="diary-col-side">
                  <div
                    v-for="(slot, i) in diarySlots.slice(1, 3)"
                    :key="slot ? slot.id : `empty-side-${i}`"
                    class="diary-photo"
                    :class="{ 'is-empty': !slot }"
                    :style="slot ? { backgroundImage: `url(${slot.url})` } : {}"
                    :title="slot?.caption || pet.name"
                  >
                    <button v-if="slot" class="diary-del" type="button" @click.stop="deletePhoto(slot)" aria-label="删除照片">
                      <span class="material-symbols-outlined" aria-hidden="true">close</span>
                    </button>
                  </div>
                </div>
              </div>
              <div class="diary-row-bottom">
                <div
                  v-for="(slot, i) in diarySlots.slice(3, 6)"
                  :key="slot ? slot.id : `empty-bottom-${i}`"
                  class="diary-photo"
                  :class="{ 'is-empty': !slot }"
                  :style="slot ? { backgroundImage: `url(${slot.url})` } : {}"
                  :title="slot?.caption || pet.name"
                >
                  <button v-if="slot" class="diary-del" type="button" @click.stop="deletePhoto(slot)" aria-label="删除照片">
                    <span class="material-symbols-outlined" aria-hidden="true">close</span>
                  </button>
                </div>
              </div>
            </div>
            <div v-else class="diary-empty">
              <span class="material-symbols-outlined" aria-hidden="true">photo_library</span>
              <p>还没有照片,上传一张开始记录 {{ pet.name }} 的成长吧</p>
            </div>

            <button
              v-if="diaryAll.length > 0"
              type="button"
              class="diary-more"
              @click="viewAllPhotos"
            >
              {{ `查看更多照片 (${diaryAll.length})` }}
              <span class="material-symbols-outlined" aria-hidden="true">arrow_forward</span>
            </button>
          </section>
          </div>

          <!-- RIGHT: 健康日程 -->
          <section class="schedule-card" aria-label="健康日程">
            <header class="schedule-head">
              <h3 class="schedule-title">
                <span class="material-symbols-outlined" aria-hidden="true">monitor_heart</span>
                <span>健康日程</span>
              </h3>
              <span
                v-if="reminderStats.total"
                class="schedule-badge"
                :class="{ danger: reminderStats.overdue > 0 }"
              >
                <span class="material-symbols-outlined" aria-hidden="true">
                  {{ reminderStats.overdue ? 'error' : 'schedule' }}
                </span>
                {{
                  reminderStats.overdue
                    ? `${reminderStats.overdue} 项已逾期`
                    : `${reminderStats.soon} 项即将到期`
                }}
              </span>
            </header>

            <!-- 快速记录：一键补录并自动排下次 -->
            <div class="quick-bar">
              <span class="quick-label">快速记录</span>
              <div class="quick-chips">
                <button
                  v-for="p in quickPresets"
                  :key="p.type"
                  type="button"
                  class="quick-chip"
                  @click="openQuickAdd(p)"
                >
                  <span class="material-symbols-outlined" aria-hidden="true">{{ p.icon }}</span>
                  <span>{{ p.label }}</span>
                </button>
              </div>
            </div>

            <!-- 待办提醒 -->
            <div v-if="reminderItems.length" class="sched-group">
              <p class="group-label">
                <span class="material-symbols-outlined" aria-hidden="true">notifications_active</span>
                <span>待办提醒</span>
              </p>
              <ul class="schedule-list">
                <li
                  v-for="item in reminderItems"
                  :key="'r' + item.id"
                  class="schedule-row"
                  :class="['border-' + item.color, item.urgency]"
                >
                  <span class="schedule-icon" :class="item.color">
                    <span class="material-symbols-outlined" aria-hidden="true">{{ item.icon }}</span>
                  </span>
                  <div class="schedule-body">
                    <p class="schedule-name">{{ item.title }}</p>
                    <p class="schedule-meta" :class="item.urgency">{{ item.meta }}</p>
                  </div>
                  <button
                    type="button"
                    class="schedule-cta primary"
                    :disabled="completing === item.id"
                    @click="completeReminder(item.raw)"
                  >
                    <span class="material-symbols-outlined" aria-hidden="true">check</span>
                    <span>完成</span>
                  </button>
                  <button
                    type="button"
                    class="schedule-del"
                    title="删除提醒"
                    @click="removeReminder(item.raw)"
                  >
                    <span class="material-symbols-outlined" aria-hidden="true">close</span>
                  </button>
                </li>
              </ul>
            </div>

            <!-- 历史记录 -->
            <div class="sched-group">
              <p class="group-label">
                <span class="material-symbols-outlined" aria-hidden="true">history</span>
                <span>健康记录</span>
              </p>
              <ul v-if="recordItems.length" class="schedule-list">
                <li
                  v-for="item in recordItems"
                  :key="'h' + item.id"
                  class="schedule-row"
                  :class="'border-' + item.color"
                >
                  <span class="schedule-icon" :class="item.color">
                    <span class="material-symbols-outlined" aria-hidden="true">{{ item.icon }}</span>
                  </span>
                  <div class="schedule-body">
                    <p class="schedule-name">{{ item.title }}</p>
                    <p class="schedule-meta">{{ item.meta }}</p>
                  </div>
                  <span class="schedule-done-mark">
                    <span class="material-symbols-outlined" aria-hidden="true">check</span>
                  </span>
                </li>
              </ul>
              <div v-else class="schedule-empty">
                <span class="material-symbols-outlined" aria-hidden="true">event_busy</span>
                <p>
                  {{
                    activeTabKey
                      ? '该分类下暂无记录'
                      : '还没有健康记录,用上方「快速记录」补一条吧'
                  }}
                </p>
              </div>
              <button
                v-if="filteredRecords.length > RECORD_PREVIEW"
                type="button"
                class="record-more"
                @click="showAllRecords = !showAllRecords"
              >
                {{ showAllRecords ? '收起' : `查看全部 ${filteredRecords.length} 条` }}
              </button>
            </div>

            <nav class="schedule-tabs" aria-label="健康分类">
              <button
                type="button"
                class="tab"
                :class="{ active: activeTabKey === '' }"
                @click="activeTabKey = ''"
              >
                全部<span class="tab-count">{{ tabCounts[''] || 0 }}</span>
              </button>
              <button
                v-for="t in scheduleTabs"
                :key="t.key"
                type="button"
                class="tab"
                :class="{ active: activeTabKey === t.key }"
                @click="activeTabKey = t.key"
              >
                {{ t.label }}<span class="tab-count">{{ tabCounts[t.key] || 0 }}</span>
              </button>
            </nav>
          </section>
        </section>

        <button class="back-btn" type="button" @click="goHome">返回首页</button>
      </template>
    </div>

    <!-- ============ 快速记录弹窗 ============ -->
    <el-dialog
      v-model="quickOpen"
      title="快速记录"
      width="440px"
      class="quick-add-dialog"
      :close-on-click-modal="false"
    >
      <div class="qa-form">
        <label class="qa-field">
          <span class="qa-label">记录名称</span>
          <input
            v-model="quickForm.title"
            class="qa-input"
            type="text"
            maxlength="64"
            :placeholder="quickForm.placeholder"
          />
        </label>

        <label class="qa-field">
          <span class="qa-label">{{ quickForm.type === 'weight' ? '体重 / 备注' : '备注' }}</span>
          <input
            v-model="quickForm.content"
            class="qa-input"
            type="text"
            maxlength="120"
            :placeholder="quickForm.type === 'weight' ? '如:4.2 kg' : '可填医院、批次、用药等(选填)'"
          />
        </label>

        <label class="qa-field">
          <span class="qa-label">记录日期</span>
          <input v-model="quickForm.date" class="qa-input" type="date" @change="syncNextDate" />
        </label>

        <label class="qa-switch">
          <input v-model="quickForm.reminder" type="checkbox" @change="syncNextDate" />
          <span>到期自动提醒我（站内消息提前推送）</span>
        </label>

        <template v-if="quickForm.reminder">
          <label class="qa-field">
            <span class="qa-label">重复周期</span>
            <select v-model.number="quickForm.cycleDays" class="qa-input" @change="syncNextDate">
              <option :value="0">不重复（仅提醒一次）</option>
              <option :value="30">每 30 天</option>
              <option :value="90">每 90 天（常见驱虫）</option>
              <option :value="180">每 180 天（半年体检）</option>
              <option :value="365">每 365 天（年度疫苗）</option>
            </select>
          </label>

          <label class="qa-field">
            <span class="qa-label">下次到期日</span>
            <input v-model="quickForm.nextDate" class="qa-input" type="date" />
          </label>

          <p class="qa-hint">
            <span class="material-symbols-outlined" aria-hidden="true">info</span>
            <span>
              到期前会推送站内消息。设置了重复周期时，点「完成」会自动排下一次。
            </span>
          </p>
        </template>
      </div>

      <template #footer>
        <button class="qa-btn ghost" type="button" @click="quickOpen = false">取消</button>
        <button class="qa-btn primary" type="button" :disabled="quickSaving" @click="submitQuickAdd">
          {{ quickSaving ? '保存中…' : '保存记录' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.pet-detail {
  min-height: 100vh;
  background: $color-background;
  padding-bottom: $spacing-3xl;
}

.dashboard {
  @include container-max;
  padding-top: $spacing-lg;
  padding-bottom: $spacing-lg;
}

// ====== State (loading / error) ======
.state-card {
  text-align: center;
  padding: $spacing-2xl $spacing-md;
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-card;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-sm;

  p {
    color: $color-text-secondary;
    margin: 0;
  }

  .spinner {
    width: 32px;
    height: 32px;
    border: 3px solid rgba($color-brand, 0.2);
    border-top-color: $color-brand;
    border-radius: 50%;
    animation: pd-spin 0.8s linear infinite;

    &.small {
      width: 18px;
      height: 18px;
      border-width: 2px;
    }
  }
}

.state-card.error .material-symbols-outlined {
  font-size: 40px;
  color: $color-danger;
}

.retry-btn {
  margin-top: $spacing-sm;
  padding: 8px 20px;
  background: $color-brand;
  color: #fff;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  font-weight: 600;
}

// ====== 顶部 我的宠物 标题栏 + chip ======
.pets-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
}

.pets-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-lg;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0;
}

.pets-add {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: none;
  color: $color-brand;
  font-size: $font-size-body-md;
  font-weight: 600;
  cursor: pointer;
  padding: $spacing-xs $spacing-sm;
  transition: color $transition-fast, transform $transition-fast;

  &:hover {
    color: $color-brand-dark;
    transform: translateY(-1px);
  }

  .material-symbols-outlined {
    font-size: 22px;
    font-variation-settings: 'FILL' 1;
  }
}

.pets-chips {
  display: flex;
  gap: $spacing-lg;
  margin-bottom: $spacing-lg;
  flex-wrap: wrap;
}

.pets-chip {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-xs;
  background: transparent;
  border: none;
  cursor: pointer;
  padding: $spacing-xs;
  transition: transform $transition-fast;

  .chip-avatar {
    width: 64px;
    height: 64px;
    border-radius: $radius-full;
    overflow: hidden;
    border: 3px solid transparent;
    transition: border-color $transition-fast, transform $transition-fast;
    background: $color-surface-container;

    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }

  .chip-name {
    font-size: $font-size-label-sm;
    color: $color-text-secondary;
    font-weight: 500;
    transition: color $transition-fast;
  }

  &.active {
    .chip-avatar {
      border-color: $color-brand;
    }
    .chip-name {
      color: $color-brand;
      font-weight: 700;
    }
  }

  &:hover:not(.active) {
    .chip-avatar {
      transform: scale(1.06);
    }
    .chip-name {
      color: $color-on-surface;
    }
  }

  &:focus-visible {
    outline: none;

    .chip-avatar {
      box-shadow: 0 0 0 3px rgba($color-brand, 0.25);
    }
  }
}

// ====== 双栏: 档案 + 日程 ======
.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1.7fr;
  gap: $spacing-md;
  margin-bottom: $spacing-lg;
  align-items: stretch;

  @media (max-width: 900px) {
    grid-template-columns: 1fr;
  }
}

// 左栏:档案卡 + 成长日记 纵向堆叠;stretch 让左栏整体与右栏等高,日记卡 flex:1 撑满使底部齐平
.dash-left {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  min-width: 0;
  height: 100%;
}

// LEFT — 橙色档案卡
.profile-card {
  position: relative;
  padding: $spacing-xl;
  background: linear-gradient(135deg, #ff8a3d 0%, #ff6f1a 100%);
  border-radius: $radius-xl;
  color: #fff;
  overflow: hidden;
  box-shadow: 0 12px 32px rgba(255, 122, 50, 0.28);
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  min-height: 420px;
}

.profile-edit {
  align-self: flex-end;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px $spacing-md;
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: $radius-full;
  color: #fff;
  font-size: $font-size-label-sm;
  font-weight: 600;
  cursor: pointer;
  position: relative;
  z-index: 10;
  transition: background $transition-fast;

  &:hover {
    background: rgba(255, 255, 255, 0.3);
  }

  .material-symbols-outlined {
    font-size: 16px;
  }
}

.profile-head {
  position: relative;
  z-index: 10;
}

.profile-name {
  font-family: $font-family-headline;
  font-size: 48px;
  font-weight: 700;
  line-height: 1;
  margin: 0 0 $spacing-xs;
  letter-spacing: -0.02em;
}

.profile-sub {
  font-size: $font-size-body-md;
  color: rgba(255, 255, 255, 0.88);
  margin: 0;
}

.profile-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: $spacing-sm;
  position: relative;
  z-index: 10;
}

.profile-stat {
  background: rgba(255, 255, 255, 0.18);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  border-radius: $radius-default;
  padding: $spacing-sm $spacing-md;
}

.stat-key {
  display: block;
  font-size: $font-size-label-sm;
  color: rgba(255, 255, 255, 0.82);
  margin-bottom: 4px;
}

.stat-val {
  display: block;
  font-family: $font-family-headline;
  font-size: $font-size-body-md;
  font-weight: 700;
  color: #fff;
}

.profile-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12px $spacing-xl;
  background: rgba(255, 255, 255, 0.96);
  color: $color-brand;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-body-md;
  font-weight: 700;
  cursor: pointer;
  position: relative;
  z-index: 10;
  align-self: flex-start;
  transition: background $transition-fast, transform $transition-fast, box-shadow $transition-fast;

  &:hover {
    background: #fff;
    transform: translateY(-1px);
    box-shadow: 0 8px 20px rgba(255, 122, 50, 0.28);
  }
}


// RIGHT — 健康日程
.schedule-card {
  padding: $spacing-lg $spacing-xl;
  background: linear-gradient(180deg, rgba($color-secondary, 0.06) 0%, rgba($color-secondary, 0.02) 100%);
  border: 1px solid rgba($color-secondary, 0.1);
  border-radius: $radius-xl;
  box-shadow: $shadow-sm;
  display: flex;
  flex-direction: column;
}

.schedule-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.schedule-title {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0;

  .material-symbols-outlined {
    color: $color-brand;
    font-variation-settings: 'FILL' 1;
  }
}

// ====== 逾期/待办 角标 ======
.schedule-badge {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: #b26a00;
  background: rgba(255, 168, 0, 0.14);
  border-radius: $radius-full;
  white-space: nowrap;

  .material-symbols-outlined {
    font-size: 15px;
    font-variation-settings: 'FILL' 1;
  }

  &.danger {
    color: $color-danger;
    background: $color-error-container;
  }
}

// ====== 快速记录 ======
.quick-bar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  flex-wrap: wrap;
  padding: $spacing-sm $spacing-md;
  margin-bottom: $spacing-md;
  background: rgba(#fff, 0.7);
  border: 1px dashed rgba($color-brand, 0.35);
  border-radius: $radius-default;
}

.quick-label {
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-text-secondary;
  flex-shrink: 0;
}

.quick-chips {
  display: flex;
  gap: $spacing-xs;
  flex-wrap: wrap;
}

.quick-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  font-size: $font-size-label-sm;
  font-weight: 600;
  color: $color-brand;
  background: rgba($color-brand, 0.08);
  border: 1px solid rgba($color-brand, 0.22);
  border-radius: $radius-full;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 16px;
    font-variation-settings: 'FILL' 1;
  }

  &:hover {
    color: #fff;
    background: $color-brand;
    border-color: $color-brand;
    transform: translateY(-1px);
  }
}

// ====== 分组标题 ======
.sched-group {
  margin-bottom: $spacing-sm;
}

.group-label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  margin: 0 0 $spacing-sm;
  font-size: $font-size-label-sm;
  font-weight: 700;
  color: $color-text-secondary;
  letter-spacing: 0.02em;

  .material-symbols-outlined {
    font-size: 16px;
  }
}

// ====== 删除提醒 / 展开更多 ======
.schedule-del {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  flex-shrink: 0;
  color: $color-text-secondary;
  background: transparent;
  border: none;
  border-radius: $radius-full;
  cursor: pointer;
  opacity: 0.5;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 17px;
  }

  &:hover {
    opacity: 1;
    color: $color-danger;
    background: $color-error-container;
  }
}

.record-more {
  display: block;
  width: 100%;
  padding: 7px;
  margin-bottom: $spacing-sm;
  font-size: $font-size-label-sm;
  font-weight: 600;
  color: $color-brand;
  background: transparent;
  border: 1px solid rgba($color-brand, 0.28);
  border-radius: $radius-full;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: rgba($color-brand, 0.08);
  }
}

.tab-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  margin-left: 6px;
  font-size: $font-size-label-sm;
  font-weight: 700;
  line-height: 1;
  color: $color-text-secondary;
  background: rgba($color-secondary, 0.1);
  border-radius: $radius-full;
  transition: color $transition-fast, background $transition-fast;
}

.tab.active .tab-count {
  color: #fff;
  background: $color-brand;
}

.schedule-list {
  list-style: none;
  padding: 0;
  margin: 0 0 $spacing-md;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.schedule-row {
  display: flex;
  align-items: center;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: #fff;
  border-radius: $radius-lg;
  border-left: 3px solid transparent;
  transition: box-shadow $transition-fast, transform $transition-fast, border-color $transition-fast;

  &.border-orange { border-left-color: $color-brand; }
  &.border-green { border-left-color: $color-success; }
  &.border-red { border-left-color: $color-danger; }
  &.border-blue { border-left-color: $color-secondary; }
  &.border-purple { border-left-color: #a855f7; }

  // 逾期项整行淡红底 + 红色左条，扫一眼就知道该处理了
  &.overdue {
    background: rgba($color-emergency, 0.05);
    border-left-color: $color-emergency !important;
  }

  &:hover {
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.07);
    transform: translateY(-1px);
  }
}

.schedule-icon {
  width: 42px;
  height: 42px;
  border-radius: $radius-default;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .material-symbols-outlined {
    font-size: 22px;
    font-variation-settings: 'FILL' 1;
  }

  &.orange {
    background: rgba($color-brand, 0.12);
    color: $color-brand;
  }
  &.green {
    background: rgba($color-success, 0.12);
    color: $color-success;
  }
  &.red {
    background: rgba($color-danger, 0.12);
    color: $color-danger;
  }
  &.blue {
    background: rgba($color-secondary, 0.12);
    color: $color-secondary;
  }
  &.purple {
    background: rgba(168, 85, 247, 0.12);
    color: #a855f7;
  }
}

.schedule-body {
  flex: 1;
  min-width: 0;
}

.schedule-name {
  font-size: $font-size-body-md;
  font-weight: 600;
  color: $color-on-surface;
  margin: 0 0 2px;
}

.schedule-meta {
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
  margin: 0;

  // 紧迫度：逾期红字加粗 / 临期橙字
  &.overdue {
    color: $color-danger;
    font-weight: 700;
  }

  &.soon {
    color: #b26a00;
    font-weight: 600;
  }
}

.schedule-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  padding: 7px $spacing-md;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  border: none;
  cursor: pointer;
  flex-shrink: 0;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 17px;
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
    transform: none !important;
  }

  // 「完成」是收敛动作,用成功绿更贴合语义
  &.primary {
    background: $color-success;
    color: #fff;

    &:hover:not(:disabled) {
      filter: brightness(0.93);
      transform: translateY(-1px);
      box-shadow: 0 4px 12px rgba(47, 158, 68, 0.3);
    }
  }

  &.done {
    background: transparent;
    color: $color-success;
    padding: 6px;
    cursor: default;
  }
}

.schedule-done-mark {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: rgba($color-success, 0.1);
  color: $color-success;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;

  .material-symbols-outlined {
    font-size: 18px;
    font-variation-settings: 'FILL' 1;
  }
}

.schedule-empty {
  text-align: center;
  padding: $spacing-lg $spacing-md;
  color: $color-text-secondary;

  .material-symbols-outlined {
    font-size: 36px;
    opacity: 0.4;
    font-variation-settings: 'FILL' 1;
  }

  p {
    margin: $spacing-xs 0 0;
    font-size: $font-size-label-sm;
  }
}

.schedule-tabs {
  display: flex;
  border-top: 1px solid rgba($color-secondary, 0.12);
  padding-top: $spacing-md;
  gap: $spacing-md;
  flex-wrap: wrap;
}

.tab {
  position: relative;
  padding: 4px 0;
  background: transparent;
  border: none;
  color: $color-text-secondary;
  font-size: $font-size-body-md;
  font-weight: 500;
  cursor: pointer;
  transition: color $transition-fast;

  &:hover {
    color: $color-brand;
  }

  &.active {
    color: $color-brand;
    font-weight: 700;

    &::after {
      content: '';
      position: absolute;
      bottom: -8px;
      left: 50%;
      transform: translateX(-50%);
      width: 24px;
      height: 2px;
      background: $color-brand;
      border-radius: 2px;
    }
  }
}

// ====== 成长日记 ======
.diary-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-xl;
  box-shadow: $shadow-sm;
  padding: $spacing-lg $spacing-xl;
  margin-bottom: 0;
  min-height: 420px;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.diary-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: $spacing-md;
  padding-top: $spacing-xs;
  margin-bottom: $spacing-md;
}

.diary-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 700;
  color: $color-on-surface;
  margin: 0;
}

.diary-sub {
  margin: 4px 0 0;
  font-size: $font-size-label-sm;
  color: $color-text-secondary;
}

.diary-upload {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  padding: 8px $spacing-lg;
  background: $color-brand;
  color: #fff;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 600;
  cursor: pointer;
  transition: background $transition-fast, transform $transition-fast, box-shadow $transition-fast;

  &:hover:not(:disabled) {
    background: $color-brand-dark;
    transform: translateY(-1px);
    box-shadow: 0 8px 20px rgba(255, 122, 50, 0.28);
  }

  &:disabled {
    opacity: 0.75;
    cursor: not-allowed;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }

  .spinner {
    width: 16px;
    height: 16px;
    border: 2px solid rgba(255, 255, 255, 0.4);
    border-top-color: #fff;
    border-radius: 50%;
    animation: pd-spin 0.8s linear infinite;
  }
}

.hidden-file {
  display: none;
}

.diary-collage {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.diary-row-top {
  display: flex;
  gap: $spacing-sm;
  height: 220px;

  @media (max-width: 720px) {
    height: 156px;
  }
}

.diary-hero {
  flex: 2 1 0;
  min-width: 0;
}

.diary-col-side {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.diary-col-side .diary-photo {
  flex: 1 1 0;
  min-height: 0;
}

.diary-row-bottom {
  display: flex;
  gap: $spacing-sm;
}

.diary-row-bottom .diary-photo {
  flex: 1 1 0;
  min-width: 0;
  height: 120px;

  @media (max-width: 720px) {
    height: 84px;
  }
}

.diary-photo {
  position: relative;
  background-size: cover;
  background-position: center;
  background-color: $color-surface-container;
  border-radius: $radius-lg;
  cursor: pointer;
  transition: transform $transition-fast, box-shadow $transition-fast;
  overflow: hidden;

  &:hover {
    transform: scale(1.01);
    box-shadow: 0 12px 28px rgba(0, 0, 0, 0.12);

    .diary-del {
      opacity: 1;
      transform: scale(1);
    }
  }

  // 缺图槽位:留空占位(不隐藏、不塌陷),仅显示为浅色空块
  &.is-empty {
    background-image: none;
    background-color: $color-surface-container;
    cursor: default;

    &:hover {
      transform: none;
      box-shadow: none;
    }
  }
}

.diary-del {
  position: absolute;
  top: 6px;
  right: 6px;
  width: 26px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  opacity: 0;
  transform: scale(0.8);
  transition: opacity $transition-fast, transform $transition-fast, background $transition-fast;
  z-index: 5;

  .material-symbols-outlined {
    font-size: 16px;
    font-variation-settings: 'FILL' 1;
  }

  &:hover {
    background: $color-danger;
  }
}

.diary-loading {
  display: inline-flex;
  align-items: center;
  gap: $spacing-xs;
  color: $color-text-secondary;
  font-size: $font-size-label-sm;
  padding: $spacing-sm 0;
}

.diary-empty {
  text-align: center;
  padding: $spacing-xl $spacing-md;
  color: $color-text-secondary;

  .material-symbols-outlined {
    font-size: 48px;
    opacity: 0.3;
    font-variation-settings: 'FILL' 1;
  }

  p {
    margin: $spacing-sm 0 0;
    font-size: $font-size-label-md;
  }
}

.diary-more {
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  width: fit-content;
  margin: $spacing-md auto 0;
  padding: 8px $spacing-xl;
  background: transparent;
  color: $color-brand;
  border: 1px solid rgba($color-brand, 0.35);
  border-radius: $radius-full;
  font-size: $font-size-body-md;
  font-weight: 600;
  cursor: pointer;
  transition: background $transition-fast, border-color $transition-fast, transform $transition-fast;

  &:hover {
    background: rgba($color-brand, 0.08);
    border-color: $color-brand;
    transform: translateY(-1px);
  }

  .material-symbols-outlined {
    font-size: 18px;
    font-variation-settings: 'FILL' 1;
  }
}

// ====== 返回按钮 ======
.back-btn {
  display: block;
  width: 100%;
  padding: $spacing-sm $spacing-md;
  background: transparent;
  color: $color-text-secondary;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-full;
  cursor: pointer;
  font-size: $font-size-body-md;
  transition: all $transition-fast;

  &:hover {
    background: $color-surface-container-low;
    border-color: $color-brand;
    color: $color-brand;
  }
}

@keyframes pd-spin {
  to { transform: rotate(360deg); }
}

// ============ 手机端响应式重排 ============
// 尺寸类 px(卡片内边距/标签/按钮/删除图标)被 px-to-viewport 转 vw，手机(375)下缩成约 1/5：
// 橙色档案卡 min-height 420px→84px 塌陷、内边距 32px→6px、删除按钮 26px→5px。用 rem(不缩放)重写关键尺寸。
@include mobile {
  .pet-detail {
    padding-bottom: 2rem;
  }
  .dashboard {
    padding-top: 1rem;
    padding-bottom: 1rem;
  }
  .state-card {
    padding: 1.5rem 1rem;
  }
  .retry-btn {
    padding: 0.5rem 1.1rem;
  }
  .pets-head {
    gap: 0.5rem;
    margin-bottom: 0.75rem;
  }
  .pets-add {
    padding: 0.4rem 0.6rem;
  }
  .pets-chip {
    padding: 0.4rem 0.5rem;
  }
  .profile-card {
    padding: 1.1rem;
    gap: 0.85rem;
    min-height: auto;
    border-radius: 1.25rem;
  }
  .profile-edit {
    padding: 0.35rem 0.6rem;
  }
  .profile-stat {
    padding: 0.5rem 0.65rem;
  }
  .profile-cta {
    padding: 0.7rem 1rem;
    align-self: stretch;
  }
  .schedule-card {
    padding: 1rem 1.1rem;
  }
  .diary-del {
    width: 1.6rem;
    height: 1.6rem;
    top: 0.35rem;
    right: 0.35rem;
  }
  .diary-collage {
    gap: 0.5rem;
  }
  .diary-more {
    padding: 0.5rem 1rem;
  }
}
</style>

<!-- el-dialog 会 teleport 到 body，scoped 样式无法命中，故单独用全局块并以 .quick-add-dialog 收窄作用域 -->
<style lang="scss">
.quick-add-dialog {
  border-radius: $radius-xl;

  .el-dialog__title {
    font-family: $font-family-headline;
    font-weight: 700;
    color: $color-on-surface;
  }

  .el-dialog__body {
    padding-top: $spacing-sm;
  }

  .qa-form {
    display: flex;
    flex-direction: column;
    gap: $spacing-md;
  }

  .qa-field {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .qa-label {
    font-size: $font-size-label-sm;
    font-weight: 600;
    color: $color-text-secondary;
  }

  .qa-input {
    width: 100%;
    padding: 9px 12px;
    font-size: $font-size-label-md;
    color: $color-on-surface;
    background: #fff;
    border: 1px solid rgba($color-secondary, 0.25);
    border-radius: $radius-default;
    outline: none;
    transition: border-color $transition-fast, box-shadow $transition-fast;

    &:focus {
      border-color: $color-brand;
      box-shadow: 0 0 0 3px rgba($color-brand, 0.14);
    }
  }

  .qa-switch {
    display: flex;
    align-items: center;
    gap: $spacing-xs;
    font-size: $font-size-label-md;
    color: $color-on-surface;
    cursor: pointer;

    input {
      width: 16px;
      height: 16px;
      accent-color: $color-brand;
      cursor: pointer;
    }
  }

  .qa-hint {
    display: flex;
    align-items: flex-start;
    gap: 6px;
    margin: 0;
    padding: $spacing-sm $spacing-md;
    font-size: $font-size-label-sm;
    line-height: 1.5;
    color: $color-text-secondary;
    background: rgba($color-brand, 0.06);
    border-radius: $radius-default;

    .material-symbols-outlined {
      font-size: 16px;
      color: $color-brand;
      flex-shrink: 0;
      margin-top: 1px;
    }
  }

  .qa-btn {
    padding: 8px $spacing-xl;
    font-size: $font-size-label-md;
    font-weight: 600;
    border-radius: $radius-full;
    cursor: pointer;
    transition: all $transition-fast;

    &.ghost {
      color: $color-text-secondary;
      background: transparent;
      border: 1px solid rgba($color-secondary, 0.25);

      &:hover {
        color: $color-on-surface;
        border-color: $color-secondary;
      }
    }

    &.primary {
      margin-left: $spacing-sm;
      color: #fff;
      background: $color-brand;
      border: none;

      &:hover:not(:disabled) {
        background: $color-brand-dark;
      }

      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
    }
  }
}
</style>
