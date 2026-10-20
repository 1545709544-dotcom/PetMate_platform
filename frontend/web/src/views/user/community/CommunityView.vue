<script setup lang="ts">
import { ref, onMounted, computed, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { communityApi } from '@/api/modules/community'
import type { Post } from '@/api/modules/community'
import { CITIES } from '@/constants/cities'
import { userApi, type FollowUser } from '@/api/modules/user'
import { metaApi, type CommunityActivity } from '@/api/modules/meta'
import { useUserStore } from '@/stores/user'
import { regionToCity } from '@/utils/region'
import { requireVerified } from '@/utils/requireVerified'
import PageSection from '@/components/common/PageSection.vue'
import PetEmptyState from '@/components/common/PetEmptyState.vue'
const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const feedLoading = ref(false)

// ============ 知乎式内联发布器 ============
const postText = ref('')
const publishing = ref(false)
const uploading = ref(false)
const expanded = ref(false)
const contentInputRef = ref<HTMLTextAreaElement | null>(null)
const imgInput = ref<HTMLInputElement | null>(null)

const uploadedUrls = ref<string[]>([])
const previews = ref<string[]>([])
const selectedTopicId = ref<number | null>(null)

const VISIBILITY_OPTIONS = [
  { value: 'public', label: '公开', desc: '所有人可见' },
  { value: 'follow', label: '仅关注', desc: '互相关注可见' },
  { value: 'private', label: '私密', desc: '仅自己可见' },
] as const
type Visible = (typeof VISIBILITY_OPTIONS)[number]['value']
const visible = ref<Visible>('public')
const showVisibility = ref(false)
const visibleLabel = computed(() => VISIBILITY_OPTIONS.find((v) => v.value === visible.value)?.label || '公开')

const showEmoji = ref(false)
const EMOJIS = [
  '🐶', '🐱', '🐰', '🐻', '🐼', '🦊', '🐹', '🐯',
  '🐾', '🐟', '🌟', '✨', '💕', '❤️', '🥰', '😊',
  '😂', '🤔', '👍', '🎉', '🌈', '🌸', '🍰', '☕',
  '🏠', '🎈', '🌞', '🌙', '🔥', '💡', '✅', '📸',
]

const showLocation = ref(false)
const locationTemp = ref('')
const city = ref<string>('')
if (userStore.user?.city) city.value = userStore.user.city

const MAX_LEN = 1000
const MAX_IMAGES = 9
const remaining = computed(() => MAX_LEN - postText.value.length)
const canPublish = computed(() => postText.value.trim().length > 0 || uploadedUrls.value.length > 0)
const hasUnsaved = computed(
  () =>
    postText.value.trim().length > 0 ||
    uploadedUrls.value.length > 0 ||
    selectedTopicId.value !== null ||
    !!city.value ||
    visible.value !== 'public',
)
// 发帖框头像加载失败兜底
const entryAvatarFailed = ref(false)

const activities = ref<CommunityActivity[]>([])

async function loadActivities() {
  try {
    const list = await metaApi.communityActivities()
    activities.value = list || []
  } catch (e) {
    console.warn('[Community] activities 加载失败', e)
    activities.value = []
  }
}

// 推荐关注（真实接口：返回我尚未关注的其他活跃用户，按粉丝数降序）
const recommends = ref<FollowUser[]>([])
const recommendLoading = ref(false)

const feed = ref<Post[]>([])

// ============ 分类 Tab：推荐 / 附近(=用户所在城市) ============
type FeedTab = 'recommend' | 'nearby'
// 社区默认进入「推荐」流，「附近」由用户主动点击 Tab 进入
const activeTab = ref<FeedTab>('recommend')
const userCity = computed(() => userStore.user?.city || '')
// 首次进入「附近」未设城市时引导选择
const showCityGuide = ref(false)
const guideCity = ref('')
// 同会话内仅自动提示一次地区选择，避免每次进入社区都弹窗打扰
const CITY_PROMPT_KEY = 'pm_community_city_prompted'

// 注：不再做地址自动归一化（去掉从收货地址/地区偷读后强行写回的逻辑）。
// 社区「附近」严格使用用户资料市区字段 user.city；无地址时由用户在引导弹窗中自行选择。

// ============ 搜索 ============
const searchKeyword = ref('')
const searchActive = ref(false)
const searchLoading = ref(false)
const searchResults = ref<Post[]>([])
const searchTotal = ref(0)
const allTopics = ref<{ id: number; name: string }[]>([])

async function loadFeed(city?: string) {
  feedLoading.value = true
  try {
    const res = await communityApi.feed(1, 10, city)
    feed.value = res?.list || []
  } catch (e: any) {
    ElMessage.error(e?.message || '动态加载失败')
    feed.value = []
  } finally {
    feedLoading.value = false
  }
}

// 切换分类 Tab：推荐(全部) / 附近(按用户城市筛选)
function selectTab(tab: FeedTab) {
  if (tab === activeTab.value) return
  clearSearch()
  if (tab === 'recommend') {
    activeTab.value = 'recommend'
    loadFeed()
  } else if (userCity.value) {
    // 附近：已有城市则直接筛选
    activeTab.value = 'nearby'
    loadFeed(userCity.value)
  } else {
    openCityGuide()
    return
  }
  // 把当前 Tab 写回 URL，保证「进入详情→返回」能还原到同一 Tab
  router.replace({ query: { ...route.query, tab: activeTab.value } })
}

function openCityGuide() {
  guideCity.value = userCity.value
  showCityGuide.value = true
}

// 暂不设置：仅关闭引导，停留在推荐流（用户可稍后从「附近」Tab 或引导条再次进入）
function closeCityGuide() {
  showCityGuide.value = false
}

async function confirmCityGuide() {
  const c = guideCity.value.trim()
  if (!c) {
    ElMessage.warning('请先选择城市')
    return
  }
  showCityGuide.value = false
  sessionStorage.removeItem(CITY_PROMPT_KEY)
  try {
    await userApi.updateProfile({ city: c })
    if (userStore.user) userStore.user.city = c
    ElMessage.success(`已切换到「${c}」附近`)
    activeTab.value = 'nearby'
    loadFeed(c)
    router.replace({ query: { ...route.query, tab: 'nearby' } })
  } catch (e: any) {
    // 全局拦截器已统一提示，避免重复弹窗
    if (!e?.__handled) ElMessage.error(e?.message || '设置失败')
  }
}

// 热门话题（搜索框下方快捷入口）
async function loadTopics() {
  try {
    allTopics.value = (await communityApi.topicList()) || []
  } catch {
    allTopics.value = []
  }
}

// 触发搜索（回车 / 点按钮）
async function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) {
    clearSearch()
    return
  }
  searchActive.value = true
  searchLoading.value = true
  try {
    const res = await communityApi.search(kw, 1, 20)
    searchResults.value = res?.list || []
    searchTotal.value = res?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '搜索失败')
    searchResults.value = []
    searchTotal.value = 0
  } finally {
    searchLoading.value = false
  }
}

// 清空搜索，回到动态流
function clearSearch() {
  searchActive.value = false
  searchKeyword.value = ''
  searchResults.value = []
  searchTotal.value = 0
}

// 点击热门话题快捷搜索
function quickSearch(name: string) {
  searchKeyword.value = name.startsWith('#') ? name.slice(1) : name
  doSearch()
}

// 统一列表：搜索态显示结果，否则显示普通动态流
const displayList = computed<Post[]>(() => (searchActive.value ? searchResults.value : feed.value))
const listLoading = computed(() => (searchActive.value ? searchLoading.value : feedLoading.value))
const listEmpty = computed(() => displayList.value.length === 0)

// 推荐关注：加载真实数据并同步初始关注态
async function loadRecommends() {
  recommendLoading.value = true
  try {
    const list = await userApi.recommend(5)
    recommends.value = list || []
    for (const r of recommends.value) {
      if (followed.value[r.id] === undefined) followed.value[r.id] = !!r.following
    }
  } catch (e: any) {
    recommends.value = []
  } finally {
    recommendLoading.value = false
  }
}

onMounted(async () => {
  loadActivities()
  loadRecommends()
  loadTopics()
  // 等待用户资料就绪；仅静默同步 region→city（修复同城匹配），不影响默认展示
  await userStore.whenReady()
  if (!userCity.value && userStore.user?.region) {
    // 资料「所在地区」(region) 有值但缺市区 city：派生市区短名并写回，让用户发布的动态能正确归类到同城
    const derived = regionToCity(userStore.user.region)
    if (derived) {
      try {
        await userApi.updateProfile({ city: derived })
        if (userStore.user) userStore.user.city = derived
      } catch {
        /* 写回失败不影响本次展示 */
      }
    }
  }
  // 根据 URL 的 tab 参数还原（推荐/附近），默认「推荐」；
  // 这样从动态详情「返回」时能回到进入前的同一个 Tab
  const incoming = route.query.tab
  if (incoming === 'nearby' && userCity.value) {
    activeTab.value = 'nearby'
    loadFeed(userCity.value)
  } else {
    activeTab.value = 'recommend'
    loadFeed()
  }
})

// 进入用户主页：本人 → 个人中心，他人 → 对面主页
function goUser(userId?: number) {
  if (!userId) return
  if (userId === userStore.userId) {
    router.push({ path: '/user' })
  } else {
    router.push({ path: `/user/home/${userId}` })
  }
}

// ============ 知乎式发布器：展开 / 收起 / 重置 ============
function expandComposer() {
  expanded.value = true
  nextTick(() => contentInputRef.value?.focus())
}

function resetComposer() {
  postText.value = ''
  uploadedUrls.value = []
  previews.value = []
  selectedTopicId.value = null
  visible.value = 'public'
  showVisibility.value = false
  city.value = userStore.user?.city || ''
  showEmoji.value = false
  expanded.value = false
}

async function cancelCompose() {
  if (hasUnsaved.value) {
    try {
      await ElMessageBox.confirm('还有未发布的动态，确定收起吗？', '提示', {
        confirmButtonText: '收起',
        cancelButtonText: '继续编辑',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  resetComposer()
}

// ============ 图片上传（逐项模型，与发布页一致） ============
async function onPickImages(e: Event) {
  const input = e.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return

  const valid: File[] = []
  let skipped = 0
  for (const f of files) {
    if (!/image\/(jpeg|png|webp)/.test(f.type)) {
      skipped++
      continue
    }
    if (f.size > 5 * 1024 * 1024) {
      skipped++
      continue
    }
    valid.push(f)
  }

  if (!valid.length) {
    ElMessage.warning('仅支持 jpg / png / webp，且单张不超过 5MB')
    return
  }
  if (uploadedUrls.value.length + valid.length > MAX_IMAGES) {
    ElMessage.warning(`最多上传 ${MAX_IMAGES} 张图片`)
    return
  }

  uploading.value = true
  try {
    const res = await communityApi.uploadImages(valid)
    const urls = res?.urls || []
    urls.forEach((u) => {
      uploadedUrls.value.push(u)
      previews.value.push(u)
    })
    ElMessage.success(skipped ? `上传成功，已跳过 ${skipped} 张不支持的文件` : '图片上传成功')
  } catch (err: any) {
    ElMessage.error(err?.message || '图片上传失败')
  } finally {
    uploading.value = false
  }
}

function removeImage(idx: number) {
  uploadedUrls.value.splice(idx, 1)
  previews.value.splice(idx, 1)
}

// ============ 表情 / @ ============
function insertAtCursor(text: string) {
  const el = contentInputRef.value
  if (!el) {
    postText.value += text
    return
  }
  const start = el.selectionStart ?? postText.value.length
  const end = el.selectionEnd ?? postText.value.length
  postText.value = postText.value.slice(0, start) + text + postText.value.slice(end)
  nextTick(() => {
    const pos = start + text.length
    el.focus()
    el.setSelectionRange(pos, pos)
  })
}
function insertEmoji(e: string) {
  insertAtCursor(e)
}
// ============ 话题 / 位置 ============
function toggleTopic(id: number) {
  selectedTopicId.value = selectedTopicId.value === id ? null : id
}
function openLocation() {
  locationTemp.value = city.value
  showLocation.value = true
}
function confirmLocation() {
  city.value = locationTemp.value.trim()
  showLocation.value = false
}
function clearLocation() {
  city.value = ''
  showLocation.value = false
}

// ============ 发布（知乎式：留在本页，新动态置顶） ============
async function publish() {
  const text = postText.value.trim()
  if (!text && uploadedUrls.value.length === 0) {
    ElMessage.warning('说点什么或加张图片吧~')
    return
  }
  if (!requireVerified()) return
  publishing.value = true
  try {
    const created = await communityApi.publish({
      content: text,
      images: uploadedUrls.value.length ? uploadedUrls.value : undefined,
      topicId: selectedTopicId.value ?? undefined,
      visible: visible.value,
      city: city.value || undefined,
    })
    // 知乎式：不跳走，新动态直接置顶（切到推荐流保证可见）
    if (created?.id) {
      if (activeTab.value !== 'recommend' || searchActive.value) {
        clearSearch()
        activeTab.value = 'recommend'
        router.replace({ query: { ...route.query, tab: 'recommend' } })
      }
      feed.value.unshift(created)
    } else {
      await loadFeed(activeTab.value === 'nearby' ? userCity.value : undefined)
    }
    ElMessage.success('发布成功')
    resetComposer()
  } catch (e: any) {
    // 全局拦截器已对错误码统一弹过提示（含敏感词），避免重复弹窗
    if (!e?.__handled) ElMessage.error(e?.message || '发布失败')
  } finally {
    publishing.value = false
  }
}

// 进入帖子详情
function goDetail(p: Post) {
  // 携带当前 Tab，详情页「返回」时据此回到推荐/附近
  router.push({ name: 'PostDetail', params: { id: p.id }, query: { tab: activeTab.value } })
}

// 关注 / 取关（按 userId 维护本地态）
const followed = ref<Record<number, boolean>>({})
async function toggleFollow(userId: number) {
  if (!userId) return
  try {
    const res = await userApi.follow(userId)
    followed.value[userId] = !!res.following
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function likePost(p: Post) {
  try {
    const res = await communityApi.like(p.id)
    if (typeof res?.likeCount === 'number') {
      p.likeCount = res.likeCount
      if (typeof res.liked === 'boolean') p.liked = res.liked
    } else {
      p.liked = !p.liked
      p.likeCount = (p.likeCount || 0) + (p.liked ? 1 : -1)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function toggleCollect(p: Post) {
  try {
    const res = await communityApi.collect(p.id)
    p.collected = res.collected
    p.collectCount = res.collectCount
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

const commentingId = ref<number | null>(null)
const commentText = ref<Record<number, string>>({})
const deletingId = ref<number | null>(null)

async function submitComment(p: Post) {
  const text = (commentText.value[p.id] || '').trim()
  if (!text) {
    ElMessage.warning('写点评论吧~')
    return
  }
  try {
    await communityApi.comment(p.id, text)
    p.commentCount = (p.commentCount || 0) + 1
    commentText.value[p.id] = ''
    ElMessage.success('评论成功')
  } catch (e: any) {
    if (!e?.__handled) ElMessage.error(e?.message || '评论失败')
  }
}

// 分享帖子：优先调用系统分享，否则复制链接到剪贴板
async function sharePost(p: Post) {
  const url = `${window.location.origin}/community/post/${p.id}`
  const title = p.userNickname ? `${p.userNickname} 的萌宠动态` : 'PetMate 社区动态'
  const text = p.content ? p.content.slice(0, 50) : title
  try {
    if (navigator.share) {
      await navigator.share({ title, text, url })
      return
    }
  } catch {
    // 用户取消或环境不支持，降级到复制链接
  }
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('分享链接已复制到剪贴板~')
  } catch {
    ElMessage.info(`分享链接：${url}`)
  }
}

// 点击活动卡片：跳转对应活动落地页
function goActivity(a: { to?: string }) {
  if (a.to) router.push(a.to)
}

// 删除自己的帖子：仅当 userId 与当前用户一致才显示按钮，后端再次校验归属
async function deletePost(p: Post) {
  if (p.userId !== userStore.userId) return
  try {
    await ElMessageBox.confirm('确定删除这条动态吗？删除后不可恢复。', '删除动态', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // 用户取消
  }
  deletingId.value = p.id
  try {
    await communityApi.deletePost(p.id)
    feed.value = feed.value.filter((x) => x.id !== p.id)
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  } finally {
    deletingId.value = null
  }
}
</script>

<template>
  <div class="community-view">
    <div class="community-container">
      <!-- 主列 -->
      <div class="main-col">
        <!-- 搜索区 -->
        <div class="search-section">
          <div class="search-bar">
            <span class="material-symbols-outlined search-ico">search</span>
            <input
              v-model="searchKeyword"
              class="search-input"
              type="text"
              placeholder="搜索动态、话题、萌宠..."
              @keyup.enter="doSearch"
            />
            <button v-if="searchKeyword" class="search-clear" title="清空" @click="searchKeyword = ''">
              <span class="material-symbols-outlined">close</span>
            </button>
            <button class="search-btn" :disabled="searchLoading" @click="doSearch">
              <span class="material-symbols-outlined">search</span>
              搜索
            </button>
          </div>

          <!-- 未搜索时：热门话题快捷入口 -->
          <div v-if="!searchActive && allTopics.length" class="hot-topics">
            <span class="hot-label"><span class="material-symbols-outlined">local_fire_department</span>热门话题</span>
            <button
              v-for="t in allTopics"
              :key="t.id"
              class="topic-chip"
              @click="quickSearch(t.name)"
            >#{{ t.name }}</button>
          </div>

          <!-- 搜索态：结果头部 -->
          <div v-else-if="searchActive" class="search-result-head">
            <div class="result-title">
              “<strong>{{ searchKeyword }}</strong>” 的搜索结果
              <span class="result-count">共 {{ searchTotal }} 条</span>
            </div>
            <button class="back-btn" @click="clearSearch">
              <span class="material-symbols-outlined">arrow_back</span>返回动态
            </button>
          </div>
        </div>

        <!-- 未设置地区：常驻轻引导条，点选即进入「附近」 -->
        <div v-if="!userCity" class="city-hint">
          <span class="material-symbols-outlined">location_on</span>
          <span class="city-hint-text">设置所在地区后，社区将为你展示「附近」的萌宠动态</span>
          <button class="city-hint-btn" @click="openCityGuide">选择地区</button>
        </div>

        <!-- 分类 Tab：推荐 / 附近[=用户所在城市] -->
        <div class="feed-tabs">
          <button class="feed-tab" :class="{ active: activeTab === 'recommend' }" @click="selectTab('recommend')">
            推荐
          </button>
          <button class="feed-tab" :class="{ active: activeTab === 'nearby' }" @click="selectTab('nearby')">
            <span class="material-symbols-outlined">location_on</span>{{ userCity || '附近' }}
          </button>
        </div>

        <!-- 附近模式：当前城市 + 切换城市 -->
        <div v-if="activeTab === 'nearby' && userCity" class="nearby-bar">
          <span class="nearby-title">
            <span class="material-symbols-outlined">location_on</span>{{ userCity }} · 附近动态
          </span>
          <button class="switch-city" @click="openCityGuide">切换城市</button>
        </div>

        <!-- 知乎式内联发布器：折叠态点按即展开，发布后留在本页 -->
        <div class="post-entry" :class="{ expanded }">
          <img
            v-if="userStore.user?.avatar && !entryAvatarFailed"
            :src="userStore.user.avatar"
            :alt="userStore.user?.nickname"
            class="entry-avatar"
            @error="entryAvatarFailed = true"
          />
          <div v-else class="entry-avatar"></div>

          <div class="entry-body">
            <!-- 折叠态：单行假输入，点按即展开 -->
            <div v-if="!expanded" class="entry-fake" @click="expandComposer">
              <span class="entry-fake-text">分享你和你家毛孩子的精彩瞬间...</span>
              <div class="entry-fake-tools">
                <span class="fake-tool"><span class="material-symbols-outlined">image</span></span>
                <span class="fake-tool"><span class="material-symbols-outlined">mood</span></span>
              </div>
            </div>

            <!-- 展开态：完整编辑器，原地发布不跳走 -->
            <template v-else>
              <textarea
                ref="contentInputRef"
                v-model="postText"
                class="entry-textarea"
                :maxlength="MAX_LEN"
                placeholder="分享你和你家毛孩子的精彩瞬间..."
                @keydown.ctrl.enter="publish"
                @keydown.meta.enter="publish"
              ></textarea>
              <div class="entry-counter" :class="{ danger: remaining < 50 }">
                {{ postText.length }} / {{ MAX_LEN }}
              </div>

              <!-- 图片九宫格 -->
              <div v-if="previews.length" class="composer-imgs">
                <div v-for="(img, i) in previews" :key="i" class="composer-img">
                  <img :src="img" alt="预览" />
                  <button class="composer-img-del" @click="removeImage(i)">
                    <span class="material-symbols-outlined">close</span>
                  </button>
                </div>
              </div>

              <!-- 话题 -->
              <div v-if="allTopics.length" class="composer-topics">
                <button
                  v-for="t in allTopics"
                  :key="t.id"
                  class="ctopic"
                  :class="{ active: selectedTopicId === t.id }"
                  @click="toggleTopic(t.id)"
                ># {{ t.name }}</button>
              </div>

              <!-- 工具栏 -->
              <div class="composer-tools">
                <button
                  class="ctool"
                  :class="{ active: uploading }"
                  :disabled="uploading || previews.length >= MAX_IMAGES"
                  @click="imgInput?.click()"
                >
                  <span class="material-symbols-outlined">add_a_photo</span><span>图片</span>
                </button>
                <button class="ctool" :class="{ active: showEmoji }" @click="showEmoji = !showEmoji">
                  <span class="material-symbols-outlined">mood</span><span>表情</span>
                </button>
                <button class="ctool" :class="{ active: !!city }" @click="openLocation">
                  <span class="material-symbols-outlined">location_on</span><span>{{ city || '位置' }}</span>
                </button>
                <button class="ctool" @click="showVisibility = !showVisibility">
                  <span class="material-symbols-outlined">visibility</span><span>{{ visibleLabel }}</span>
                </button>

                <input ref="imgInput" type="file" accept="image/jpeg,image/png,image/webp" multiple class="hidden-file" @change="onPickImages" />

                <transition name="emoji-pop">
                  <div v-if="showEmoji" class="emoji-panel" @click.stop>
                    <button v-for="e in EMOJIS" :key="e" class="emoji-item" @click="insertEmoji(e)">{{ e }}</button>
                  </div>
                </transition>

                <transition name="vis-pop">
                  <div v-if="showVisibility" class="vis-pop" @click.stop>
                    <button
                      v-for="opt in VISIBILITY_OPTIONS"
                      :key="opt.value"
                      class="vis-pop-item"
                      :class="{ active: visible === opt.value }"
                      @click="visible = opt.value; showVisibility = false"
                    >
                      <span class="vp-label">{{ opt.label }}</span>
                      <span class="vp-desc">{{ opt.desc }}</span>
                    </button>
                  </div>
                </transition>
              </div>

              <!-- 底部操作行 -->
              <div class="composer-footer">
                <button class="cf-btn ghost" @click="cancelCompose">取消</button>
                <span class="cf-spacer"></span>
                <button class="cf-btn primary" :disabled="publishing || !canPublish" @click="publish">
                  {{ publishing ? '发布中...' : '发布' }}
                </button>
              </div>
            </template>
          </div>
        </div>

        <!-- 加载中 -->
        <div v-if="listLoading" class="feed-card">{{ searchActive ? '搜索中...' : '加载中...' }}</div>

        <!-- 空状态 -->
        <PetEmptyState
          v-else-if="listEmpty"
          class="feed-card"
          :icon="searchActive ? 'search_off' : 'inbox'"
          :title="searchActive ? '没有找到相关动态' : (activeTab === 'nearby' ? `「${userCity}」附近暂无动态` : '暂无动态')"
          :desc="searchActive ? `换个关键词试试吧~` : (activeTab === 'nearby' ? `你所在的「${userCity}」暂时还没有附近的萌宠动态，先看看推荐吧~` : '来发布第一篇吧~')"
        >
          <template #action>
            <button v-if="searchActive" class="retry-btn" @click="clearSearch">返回动态</button>
          </template>
        </PetEmptyState>

        <!-- 动态 / 搜索结果列表 -->
        <div v-for="p in displayList" :key="p.id" class="feed-card pet-card" @click="goDetail(p)">
          <div class="feed-head">
            <div class="feed-author" @click.stop="goUser(p.userId)">
              <img v-if="p.userAvatar" :src="p.userAvatar" :alt="p.userNickname" class="feed-avatar-img" @error="p.userAvatar = ''" />
              <span v-else class="feed-avatar"></span>
              <div>
                <div class="feed-name">{{ p.userNickname || '匿名用户' }}</div>
                <div class="feed-sub">
                  {{ p.createdAt }}
                  <template v-if="p.topicName"> • 来自 {{ p.topicName }}</template>
                  <template v-if="p.city"> • 📍 {{ p.city }}</template>
                </div>
              </div>
            </div>
            <button
              v-if="p.userId === userStore.userId"
              class="del-btn"
              :disabled="deletingId === p.id"
              @click.stop="deletePost(p)"
            >
              <span class="material-symbols-outlined">delete</span>
              {{ deletingId === p.id ? '删除中' : '删除' }}
            </button>
            <button
              v-else
              class="follow-btn"
              :class="{ following: followed[p.userId] }"
              @click.stop="toggleFollow(p.userId)"
            >
              {{ followed[p.userId] ? '已关注' : '关注' }}
            </button>
          </div>
          <p class="feed-text">{{ p.content }}</p>
          <div
            v-if="p.images && p.images.length"
            class="feed-imgs"
            :class="p.images.length === 1 ? 'one' : p.images.length === 2 ? 'two' : 'multi'"
          >
            <img
              v-for="(img, i) in p.images"
              :key="i"
              :src="img"
              :alt="p.userNickname"
              @error="($event.target as HTMLImageElement).style.display = 'none'"
            />
          </div>
          <div class="feed-meta">
            <button class="meta-btn" :class="{ active: p.liked }" @click.stop="likePost(p)">
              <span class="material-symbols-outlined">{{ p.liked ? 'favorite' : 'favorite_border' }}</span>
              {{ p.likeCount || 0 }}
            </button>
            <button class="meta-btn" @click.stop="commentingId = commentingId === p.id ? null : p.id">
              <span class="material-symbols-outlined">chat_bubble</span>
              {{ p.commentCount || 0 }}
            </button>
            <button class="meta-btn" @click.stop="sharePost(p)">
              <span class="material-symbols-outlined">share</span>
              分享
            </button>
          </div>

          <!-- 内联评论 -->
          <div v-if="commentingId === p.id" class="comment-box" @click.stop>
            <input
              v-model="commentText[p.id]"
              class="comment-input"
              placeholder="说点什么..."
              @keyup.enter="submitComment(p)"
            />
            <button class="comment-send" @click="submitComment(p)">发送</button>
          </div>
        </div>
      </div>

      <!-- 右侧栏 -->
      <aside class="side-col">
        <div class="side-card">
          <PageSection title="热门活动" icon="local_fire_department">
            <div class="activities">
              <div v-for="a in activities" :key="a.title" class="activity" @click="goActivity(a)">
                <div
                  class="activity-grad"
                  :class="['variant-' + a.variant]"
                  :style="{ backgroundImage: 'url(' + a.cover + ')' }"
                >
                  <span class="deco material-symbols-outlined">{{ a.icon }}</span>
                </div>
                <div class="activity-mask">
                  <div class="activity-title">{{ a.title }}</div>
                  <div class="activity-desc">{{ a.desc }}</div>
                </div>
              </div>
            </div>
          </PageSection>

          <PageSection title="推荐关注" icon="person_add">
            <div v-if="recommendLoading" class="recommend-tip">加载中...</div>
            <div v-else-if="recommends.length === 0" class="recommend-tip">暂无推荐~</div>
            <div v-else class="recommend-list">
              <div v-for="r in recommends" :key="r.id" class="recommend-item">
                <div class="recommend-left" @click="goUser(r.id)">
                  <img v-if="r.avatar" :src="r.avatar" :alt="r.nickname" class="recommend-avatar" @error="r.avatar = undefined" />
                  <span v-else class="recommend-avatar recommend-avatar-fallback">{{ (r.nickname || '?').charAt(0) }}</span>
                  <div class="recommend-meta">
                    <div class="recommend-name">
                      {{ r.nickname || ('用户' + r.id) }}
                      <span v-if="r.vipLevel && r.vipLevel > 0" class="recommend-vip">VIP{{ r.vipLevel }}</span>
                    </div>
                    <div class="recommend-desc">{{ r.bio || '这个人很懒，什么都没留下~' }}</div>
                  </div>
                </div>
                <button
                  class="recommend-follow"
                  :class="{ following: followed[r.id] }"
                  @click="toggleFollow(r.id)"
                >
                  <span class="material-symbols-outlined">
                    {{ followed[r.id] ? 'how_to_reg' : 'person_add' }}
                  </span>
                </button>
              </div>
            </div>
          </PageSection>
        </div>
      </aside>
    </div>

    <!-- 发布器：添加地点弹窗 -->
    <el-dialog
      v-model="showLocation"
      title="添加地点"
      width="90%"
      max-width="420px"
      align-center
      class="location-dialog"
    >
      <p class="dialog-tip">选择你所在的城市，发布后将归类到社区「附近 / 市区」。</p>
      <el-select
        v-model="locationTemp"
        filterable
        placeholder="搜索城市，如 北京"
        class="location-select"
      >
        <el-option v-for="c in CITIES" :key="c" :label="c" :value="c" />
      </el-select>
      <template #footer>
        <button class="dlg-btn ghost" @click="clearLocation">不添加</button>
        <button class="dlg-btn primary" @click="confirmLocation">确定</button>
      </template>
    </el-dialog>

    <!-- 首次进入「附近」未设城市：引导选择所在城市 -->
    <el-dialog
      v-model="showCityGuide"
      title="选择你所在的城市"
      width="90%"
      max-width="420px"
      align-center
      class="city-guide-dialog"
    >
      <p class="guide-tip">选择城市后，社区将为你展示「附近」的萌宠动态，你的发布也会归类到这里。</p>
      <el-select
        v-model="guideCity"
        filterable
        placeholder="搜索城市，如 北京"
        class="guide-select"
      >
        <el-option v-for="c in CITIES" :key="c" :label="c" :value="c" />
      </el-select>
      <template #footer>
        <button class="guide-btn" @click="closeCityGuide">暂不设置</button>
        <button class="guide-btn primary" @click="confirmCityGuide">确定</button>
      </template>
    </el-dialog>
  </div>
</template>

<style lang="scss" scoped>
.community-view {
  min-height: 100vh;
  background: $color-background;
}

.community-container {
  @include container-max;
  padding-top: $spacing-xl;
  padding-bottom: $spacing-2xl;
  display: grid;
  grid-template-columns: 1fr;
  gap: $width-gutter-desktop;

  @include desktop {
    grid-template-columns: 2fr 1fr;
  }
}

.main-col {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
  min-width: 0;
}

// ============ 搜索区 ============
.search-section {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  padding: $spacing-md $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
}

.search-bar {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: 6px 6px 6px $spacing-md;
  background: $color-surface-container-low;
  border: 1.5px solid transparent;
  border-radius: $radius-full;
  transition: border-color $transition-fast, box-shadow $transition-fast, background $transition-fast;

  &:focus-within {
    border-color: $color-brand;
    background: $color-surface-container-lowest;
    box-shadow: 0 0 0 4px rgba(255, 122, 50, 0.12);
  }
}

.search-ico {
  color: $color-on-surface-variant;
  font-size: 22px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-family: $font-family-body;
  font-size: $font-size-body-md;
  color: $color-on-surface;

  &::placeholder {
    color: rgba(88, 66, 56, 0.5);
  }
}

.search-clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: rgba(88, 66, 56, 0.08);
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: background $transition-fast, color $transition-fast;

  &:hover {
    background: rgba(88, 66, 56, 0.16);
    color: $color-on-surface;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.search-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px $spacing-lg;
  border: none;
  border-radius: $radius-full;
  background: $color-brand;
  color: $color-on-brand-container;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  box-shadow: $shadow-md;
  transition: transform $transition-fast, opacity $transition-fast;

  &:hover:not(:disabled) {
    transform: scale(1.04);
  }

  &:active:not(:disabled) {
    transform: scale(0.96);
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.hot-topics {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.hot-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-emergency;

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.topic-chip {
  padding: 4px $spacing-md;
  border: 1px solid rgba(255, 122, 50, 0.35);
  border-radius: $radius-full;
  background: $color-brand-container;
  color: $color-on-brand-container;
  font-size: $font-size-label-sm;
  font-weight: 700;
  cursor: pointer;
  transition: transform $transition-fast, background $transition-fast, box-shadow $transition-fast;

  &:hover {
    transform: translateY(-1px);
    background: $color-brand;
    color: #ffffff;
    box-shadow: $shadow-sm;
  }
}

.search-result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: $spacing-sm;
}

.result-title {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;

  strong {
    color: $color-brand;
  }
}

.result-count {
  margin-left: $spacing-sm;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px $spacing-md;
  border: 1px solid $color-brand;
  border-radius: $radius-full;
  background: transparent;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: $color-brand;
    color: #ffffff;
  }

  .material-symbols-outlined {
    font-size: 18px;
  }
}

.retry-btn {
  margin-top: $spacing-sm;
  padding: 6px $spacing-lg;
  border: 1px solid $color-brand;
  border-radius: $radius-full;
  background: transparent;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: $color-brand;
    color: #ffffff;
  }
}

// ============ 知乎式内联发布器 ============
.post-entry {
  display: flex;
  gap: $spacing-md;
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
  transition: box-shadow $transition-fast, border-color $transition-fast;

  &.expanded {
    border-color: rgba(255, 122, 50, 0.35);
    box-shadow: $shadow-md;
  }
}

.entry-avatar {
  width: 48px;
  height: 48px;
  border-radius: $radius-full;
  background: $color-brand-container;
  flex-shrink: 0;
  object-fit: cover;
}

.entry-body {
  flex: 1;
  min-width: 0;
  position: relative;
}

// 折叠态：单行假输入，点按即展开
.entry-fake {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
  min-height: 48px;
  padding: 0 $spacing-md;
  background: $color-surface-container-low;
  border-radius: $radius-full;
  cursor: text;
  transition: background $transition-fast;

  &:hover {
    background: $color-surface-container;
  }

  .entry-fake-text {
    color: rgba(88, 66, 56, 0.5);
    font-size: $font-size-body-md;
  }

  .entry-fake-tools {
    display: flex;
    gap: $spacing-xs;

    .fake-tool {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 34px;
      height: 34px;
      border-radius: $radius-full;
      color: $color-brand;
      background: $color-brand-container;
    }
  }
}

.entry-textarea {
  width: 100%;
  min-height: 110px;
  padding: $spacing-md;
  background: $color-surface-container-low;
  border: none;
  border-radius: $radius-md;
  font-family: $font-family-body;
  font-size: $font-size-body-md;
  color: $color-on-surface;
  resize: vertical;
  outline: none;
  transition: box-shadow $transition-fast;

  &:focus {
    box-shadow: 0 0 0 2px $color-brand;
  }

  &::placeholder {
    color: rgba(88, 66, 56, 0.5);
  }
}

.entry-counter {
  text-align: right;
  font-size: $font-size-label-sm;
  color: $color-text-tertiary;
  margin: $spacing-xs 0 0;

  &.danger {
    color: $color-emergency;
  }
}

// 图片九宫格
.composer-imgs {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: $spacing-sm;
  margin-top: $spacing-md;
}

.composer-img {
  position: relative;
  aspect-ratio: 1;
  border-radius: $radius-md;
  overflow: hidden;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .composer-img-del {
    position: absolute;
    top: 5px;
    right: 5px;
    width: 22px;
    height: 22px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border: 2px solid #fff;
    background: $color-emergency;
    color: #fff;
    border-radius: $radius-full;
    cursor: pointer;

    .material-symbols-outlined {
      font-size: 15px;
      line-height: 1;
    }

    &:hover {
      background: #d9363e;
    }
  }
}

// 话题
.composer-topics {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-md;
}

.ctopic {
  padding: 6px $spacing-md;
  border: 1px solid $color-outline;
  background: $color-surface-container-low;
  color: $color-on-surface;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  font-weight: 600;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    border-color: $color-brand;
    color: $color-brand;
  }

  &.active {
    background: $color-brand;
    border-color: $color-brand;
    color: $color-on-brand;
  }
}

// 工具栏
.composer-tools {
  position: relative;
  display: flex;
  align-items: center;
  gap: $spacing-xs;
  margin-top: $spacing-md;
  flex-wrap: wrap;
}

.ctool {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 7px $spacing-md;
  border: 1px solid transparent;
  background: $color-surface-container-low;
  color: $color-on-surface-variant;
  border-radius: $radius-full;
  font-size: $font-size-label-sm;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover,
  &.active {
    border-color: $color-brand;
    color: $color-brand;
    background: $color-brand-container;
  }

  &:disabled {
    opacity: 0.55;
    cursor: not-allowed;
  }

  .material-symbols-outlined {
    font-size: 19px;
  }
}

.emoji-panel {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  right: 0;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
  padding: $spacing-sm;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  box-shadow: $shadow-lg;
  z-index: 20;
}

.emoji-item {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  padding: 6px 0;
  border-radius: $radius-sm;
  cursor: pointer;
  transition: transform $transition-fast, background $transition-fast;

  &:hover {
    transform: scale(1.2);
    background: $color-surface-container-low;
  }
}

.emoji-pop-enter-active,
.emoji-pop-leave-active {
  transition: opacity $transition-fast, transform $transition-fast;
}
.emoji-pop-enter-from,
.emoji-pop-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

// 可见性弹层
.vis-pop {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  width: 220px;
  padding: $spacing-sm;
  background: $color-surface-container-lowest;
  border: 1px solid $color-outline-variant;
  border-radius: $radius-md;
  box-shadow: $shadow-lg;
  z-index: 20;
}

.vis-pop-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1px;
  padding: 8px $spacing-md;
  border: 1px solid transparent;
  background: transparent;
  border-radius: $radius-sm;
  cursor: pointer;
  text-align: left;
  transition: all $transition-fast;

  &:hover {
    background: $color-surface-container-low;
  }

  &.active {
    background: $color-brand-container;
    border-color: $color-brand;
  }

  .vp-label {
    font-size: $font-size-label-md;
    font-weight: 700;
    color: $color-on-surface;
  }

  .vp-desc {
    font-size: $font-size-label-sm;
    color: $color-text-tertiary;
  }
}

// 底部操作行
.composer-footer {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1px solid $color-outline-variant;
}

.cf-spacer {
  flex: 1;
}

.cf-btn {
  padding: 9px $spacing-lg;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform $transition-fast, opacity $transition-fast, background $transition-fast;

  &:hover {
    transform: translateY(-1px);
  }

  &.ghost {
    background: transparent;
    border-color: $color-outline-variant;
    color: $color-on-surface-variant;
  }

  &.text {
    background: transparent;
    color: $color-on-surface-variant;
    padding: 9px $spacing-sm;

    &:hover {
      color: $color-brand;
    }
  }

  &.primary {
    background: $color-brand;
    color: $color-on-brand;
    box-shadow: $shadow-md;

    &:disabled {
      opacity: 0.5;
      cursor: not-allowed;
      transform: none;
    }
  }
}

.hidden-file {
  display: none;
}

// 添加地点弹窗
.location-dialog {
  :deep(.el-dialog) {
    border-radius: $radius-lg;
    max-width: 420px;
  }
  :deep(.el-dialog__title) {
    font-weight: $font-weight-bold;
    color: $color-on-surface;
  }
}

.dialog-tip {
  margin: 0 0 $spacing-md;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  line-height: $line-height-normal;
}

.location-select {
  width: 100%;
}

.dlg-btn {
  padding: 9px $spacing-xl;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform $transition-fast, opacity $transition-fast;

  &:hover {
    transform: translateY(-1px);
  }

  &.primary {
    background: $color-brand;
    color: $color-on-brand;
    box-shadow: $shadow-md;
  }

  &:not(.primary) {
    border-color: $color-outline-variant;
    color: $color-on-surface-variant;

    &:hover {
      transform: none;
      background: rgba(88, 66, 56, 0.06);
    }
  }
}

// 手机端重排（关键尺寸用 rem，避免 px-to-viewport 缩成过小）
@include mobile {
  .post-entry {
    padding: 0.85rem 1rem;
    gap: 0.6rem;
  }
  .entry-avatar {
    width: 3rem;
    height: 3rem;
  }
  .entry-textarea {
    min-height: 6rem;
  }
  .composer-imgs {
    gap: 0.5rem;
  }
}

// ============ PK 投票帖 ============
.pk-card {
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  overflow: hidden;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
}

.pk-body {
  padding: $spacing-lg;
}

.pk-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.pk-author {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.pk-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  background: $color-secondary-container;
}

.pk-name {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
}

.pk-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.pk-badge {
  padding: 2px $spacing-xs;
  background: rgba(250, 173, 20, 0.1);
  color: $color-warning;
  border-radius: $radius-full;
  font-size: 10px;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.pk-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
  margin-bottom: $spacing-xs;
}

.pk-desc {
  font-size: $font-size-body-md;
  color: $color-on-surface-variant;
  margin-bottom: $spacing-lg;
}

.pk-vs {
  position: relative;
  height: 192px;
  border-radius: $radius-md;
  overflow: hidden;
  margin-bottom: $spacing-lg;
}

.pk-vs-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, $color-brand 0%, $color-secondary 100%);
  opacity: 0.9;
}

.pk-vs-label {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  font-family: $font-family-headline;
  font-size: $font-size-display-lg;
  font-style: italic;
  font-weight: 800;
  opacity: 0.8;
  z-index: 1;
}

.pk-vs-imgs {
  position: absolute;
  inset: 0;
  display: flex;
}

.pk-vs-slot {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $spacing-md;

  img {
    width: 96px;
    height: 96px;
    border-radius: $radius-default;
    object-fit: cover;
    border: 2px solid rgba(255, 255, 255, 0.5);
    box-shadow: $shadow-lg;
    transition: transform 500ms ease;
  }

  &:hover img {
    transform: scale(1.1);
  }
}

.pk-result {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.pk-percent {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;

  .left {
    color: $color-secondary;
    font-weight: 700;
    font-size: $font-size-headline-md;
  }

  .right {
    color: $color-brand;
    font-weight: 700;
    font-size: $font-size-headline-md;
  }
}

.pk-bar {
  display: flex;
  width: 100%;
  height: 12px;
  background: $color-surface-container;
  border-radius: $radius-full;
  overflow: hidden;

  .pk-bar-left {
    height: 100%;
    background: $color-secondary;
  }

  .pk-bar-right {
    height: 100%;
    background: $color-brand;
  }
}

.pk-options {
  display: flex;
  justify-content: space-between;
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;

  .pk-opt {
    display: flex;
    align-items: center;
    gap: 4px;
    cursor: pointer;
    transition: color $transition-fast;

    &:hover {
      color: $color-brand;
    }

    .material-symbols-outlined {
      font-size: 16px;
    }

    .thumb {
      color: $color-brand;
    }
  }
}

.pk-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-sm $spacing-lg;
  background: $color-surface-container;
}

.pk-voters {
  display: flex;
  align-items: center;

  .voter {
    width: 24px;
    height: 24px;
    border-radius: $radius-full;
    border: 2px solid $color-surface;
    background: $color-brand-light;
    margin-left: -8px;

    &:first-child {
      margin-left: 0;
    }
  }

  .voter-count {
    font-size: $font-size-label-sm;
    color: $color-on-surface-variant;
    margin-left: $spacing-md;
  }
}

.pk-foot-actions {
  display: flex;
  gap: $spacing-md;

  .material-symbols-outlined {
    color: $color-on-surface-variant;
    cursor: pointer;
    transition: color $transition-fast;

    &:hover {
      color: $color-brand;
    }
  }
}

// ============ 图文帖 ============
.feed-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
  cursor: pointer;
  transition: box-shadow $transition-fast, transform $transition-fast, border-color $transition-fast;

  &:hover {
    box-shadow: $shadow-md;
    transform: translateY(-3px);
    border-color: rgba(255, 122, 50, 0.35);
  }
}

.feed-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.feed-author {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  cursor: pointer;

  &:hover .feed-name {
    color: $color-brand;
  }
}

.feed-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  background: $color-tertiary-fixed;
}

.feed-name {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
  transition: color $transition-fast;
}

.feed-sub {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
}

.follow-btn {
  padding: 4px $spacing-md;
  border: 1px solid $color-brand;
  border-radius: $radius-full;
  color: $color-brand;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover,
  &.following {
    background: $color-brand;
    color: #ffffff;
  }
}

// 删除自己的帖子：危险态按钮
.del-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px $spacing-md;
  border: 1px solid $color-emergency;
  border-radius: $radius-full;
  color: $color-emergency;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover:not(:disabled) {
    background: $color-emergency;
    color: #ffffff;
  }

  &:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
}

.feed-text {
  font-size: $font-size-body-md;
  color: $color-on-surface;
  margin-bottom: $spacing-md;
}

.feed-imgs {
  display: grid;
  gap: $spacing-xs;
  border-radius: $radius-md;
  overflow: hidden;
  margin-bottom: $spacing-md;

  img {
    width: 100%;
    object-fit: cover;
    border-radius: $radius-sm;
    background: $color-surface-container-low;
  }

  // 单张：放大展示（不再压成方格）
  &.one {
    grid-template-columns: 1fr;

    img {
      aspect-ratio: 4 / 3;
      max-height: 420px;
    }
  }

  // 两张：左右并排
  &.two {
    grid-template-columns: repeat(2, 1fr);

    img {
      aspect-ratio: 4 / 3;
    }
  }

  // 三张及以上：九宫格
  &.multi {
    grid-template-columns: repeat(3, 1fr);

    img {
      aspect-ratio: 1;
    }
  }
}

.feed-meta {
  display: flex;
  gap: $spacing-lg;
}

.meta-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: $font-size-label-md;
  color: $color-on-surface-variant;
  cursor: pointer;
  transition: color $transition-fast;

  &:hover,
  &.active {
    color: $color-brand;
  }
}

.feed-avatar-img {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  object-fit: cover;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  color: $color-on-surface-variant;
  padding: $spacing-2xl $spacing-lg;
  text-align: center;

  .material-symbols-outlined {
    font-size: 48px;
    opacity: 0.5;
  }

  p {
    margin: 0;
    font-size: $font-size-body-md;
  }
}

.comment-box {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-top: $spacing-md;
  padding-top: $spacing-md;
  border-top: 1px dashed $color-outline-variant;
}

.comment-input {
  flex: 1;
  padding: $spacing-xs $spacing-md;
  background: $color-surface-container-low;
  border: none;
  border-radius: $radius-full;
  font-family: inherit;
  font-size: $font-size-label-md;
  outline: none;
}

.comment-send {
  padding: $spacing-xs $spacing-md;
  background: $color-brand;
  color: #ffffff;
  border: none;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;

  &:hover {
    opacity: 0.9;
  }
}

// ============ 右侧栏 ============
.side-col {
  min-width: 0;
}

.side-card {
  padding: $spacing-lg;
  background: $color-surface-container-lowest;
  border-radius: $radius-md;
  box-shadow: $shadow-sm;
  border: 1px solid rgba(223, 192, 179, 0.2);
  position: sticky;
  top: 96px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-lg;
}

.side-title {
  font-family: $font-family-headline;
  font-size: $font-size-headline-md;
  font-weight: 600;
  color: $color-on-surface;
}

.side-more {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-brand;
  cursor: pointer;
}

.activities {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.activity {
  position: relative;
  height: 168px;
  border-radius: $radius-md;
  overflow: hidden;
  cursor: pointer;

  img {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    object-fit: cover;
    transition: transform 700ms ease;
  }

  &:hover img {
    transform: scale(1.1);
  }
}

// 设计感封面：图片背景 + 玻璃高光 + 角标水印
.activity-grad {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background-color: $color-surface-container;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
  transition: transform 700ms cubic-bezier(0.16, 1, 0.3, 1), filter 700ms ease;
  will-change: transform;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.18);

  // 顶部柔和高光（玻璃感）
  &::before {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.16) 0%, transparent 45%);
    pointer-events: none;
    z-index: 1;
  }

  // 角标水印：右上角半透白色徽标
  .deco {
    position: absolute;
    top: $spacing-sm;
    right: $spacing-sm;
    width: 36px;
    height: 36px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.22);
    backdrop-filter: blur(8px);
    -webkit-backdrop-filter: blur(8px);
    color: #ffffff;
    font-size: 20px;
    z-index: 2;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    transition: transform 350ms cubic-bezier(0.16, 1, 0.3, 1), background 250ms ease;
  }

  .activity:hover & {
    transform: scale(1.08);
    .deco {
      transform: scale(1.12) rotate(-6deg);
      background: rgba(255, 255, 255, 0.34);
    }
  }
}

// 变体占位（背景图已通过内联 style 设置；这里仅作为图片加载失败时的 fallback 底色）
.activity-grad.variant-donate {
  background-color: #ff7a32;
}
.activity-grad.variant-photo {
  background-color: #ffb199;
}

.activity-mask {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: $spacing-md;
  // 加深底部渐变 mask，确保文字在亮色图片上仍然清晰
  background: linear-gradient(0deg, rgba(0, 0, 0, 0.78) 0%, rgba(0, 0, 0, 0.45) 40%, transparent 70%);
  z-index: 3;
}

.activity-title {
  color: #ffffff;
  font-size: $font-size-label-md;
  font-weight: 700;
  margin-bottom: 2px;
}

.activity-desc {
  color: rgba(255, 255, 255, 0.8);
  font-size: 10px;
}

.recommend {
  margin-top: $spacing-2xl;
}

.recommend-title {
  margin-bottom: $spacing-md;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.recommend-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.recommend-left {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  cursor: pointer;
  min-width: 0;

  &:hover .recommend-name {
    color: $color-brand;
  }
}

.recommend-avatar {
  width: 40px;
  height: 40px;
  border-radius: $radius-full;
  flex-shrink: 0;
  object-fit: cover;
  background: $color-brand-container;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  color: $color-on-brand-container;
}

.recommend-avatar-fallback {
  font-size: $font-size-label-md;
}

.recommend-meta {
  min-width: 0;
}

.recommend-name {
  font-size: $font-size-label-md;
  font-weight: 700;
  color: $color-on-surface;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: color $transition-fast;
}

.recommend-vip {
  font-size: 10px;
  font-weight: 700;
  line-height: 1;
  padding: 2px 4px;
  border-radius: $radius-sm;
  color: #fff;
  background: linear-gradient(135deg, #ffb300, #ff7a32);
}

.recommend-desc {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  @include text-ellipsis;
}

.recommend-tip {
  font-size: $font-size-label-sm;
  color: $color-on-surface-variant;
  padding: $spacing-xs 0;
}

.recommend-follow {
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-brand;
  cursor: pointer;
  transition: color $transition-fast, transform $transition-fast;

  &:hover {
    transform: scale(1.1);
  }

  &.following {
    color: $color-on-surface-variant;
  }

  .material-symbols-outlined {
    font-size: 22px;
  }
}

// ============ 分类 Tab ============
.feed-tabs {
  display: flex;
  gap: $spacing-sm;
  padding: 4px;
  background: $color-surface-container-low;
  border-radius: $radius-full;
  width: fit-content;
}

.feed-tab {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px $spacing-lg;
  border: none;
  background: transparent;
  color: $color-on-surface-variant;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: 700;
  cursor: pointer;
  transition: all $transition-fast;

  .material-symbols-outlined {
    font-size: 18px;
  }

  &:hover {
    color: $color-brand;
  }

  &.active {
    background: $color-brand;
    color: $color-on-brand-container;
    box-shadow: $shadow-sm;
  }
}

.nearby-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: $color-brand-container;
  color: $color-on-brand-container;
  border-radius: $radius-md;
}

.nearby-title {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: $font-size-label-md;
  font-weight: 700;

  .material-symbols-outlined {
    font-size: 18px;
    color: $color-brand;
  }
}

.switch-city {
  padding: 4px $spacing-md;
  border: 1px solid $color-brand;
  border-radius: $radius-full;
  background: transparent;
  color: $color-brand;
  font-size: $font-size-label-sm;
  font-weight: 700;
  cursor: pointer;
  transition: all $transition-fast;

  &:hover {
    background: $color-brand;
    color: #fff;
  }
}

// ============ 未设地区常驻引导条 ============
.city-hint {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  padding: $spacing-sm $spacing-md;
  background: $color-brand-container;
  color: $color-on-brand-container;
  border-radius: $radius-md;
  font-size: $font-size-label-md;
  flex-wrap: wrap;

  .material-symbols-outlined {
    color: $color-brand;
    font-size: 20px;
    flex-shrink: 0;
  }

  .city-hint-text {
    flex: 1;
    min-width: 0;
  }

  .city-hint-btn {
    flex-shrink: 0;
    padding: 5px $spacing-md;
    border: 1px solid $color-brand;
    border-radius: $radius-full;
    background: $color-brand;
    color: #fff;
    font-size: $font-size-label-sm;
    font-weight: 700;
    cursor: pointer;
    transition: transform $transition-fast, opacity $transition-fast;

    &:hover {
      transform: translateY(-1px);
      opacity: 0.92;
    }
  }
}

// ============ 城市引导弹窗 ============
.city-guide-dialog {
  :deep(.el-dialog) {
    border-radius: $radius-lg;
    max-width: 420px;
  }
  :deep(.el-dialog__title) {
    font-weight: $font-weight-bold;
    color: $color-on-surface;
  }
}

.guide-tip {
  margin: 0 0 $spacing-md;
  font-size: $font-size-label-md;
  color: $color-text-secondary;
  line-height: $line-height-normal;
}

.guide-select {
  width: 100%;
}

.guide-btn {
  padding: 9px $spacing-xl;
  border-radius: $radius-full;
  font-size: $font-size-label-md;
  font-weight: $font-weight-bold;
  cursor: pointer;
  border: 1px solid transparent;
  transition: transform $transition-fast, opacity $transition-fast;

  &:hover {
    transform: translateY(-1px);
  }

  &.primary {
    background: $color-brand;
    color: $color-on-brand;
    box-shadow: $shadow-md;
  }

  // 非主按钮（暂不设置）：幽灵态
  &:not(.primary) {
    border-color: $color-outline-variant;
    color: $color-on-surface-variant;

    &:hover {
      transform: none;
      background: rgba(88, 66, 56, 0.06);
    }
  }
}

// ============ 手机端响应式重排 ============
// 尺寸类 px(容器/搜索区/发帖框/头像/按钮)被 px-to-viewport 转 vw，手机(375)下缩成约 1/5：
// 容器 padding 拥挤、头像 48px→9.6px、发帖框 padding 24px→5px。用 rem(不缩放)重写关键尺寸。
@include mobile {
  .community-container {
    padding-top: 1rem;
    padding-bottom: 1.5rem;
  }
  .main-col {
    gap: 1rem;
  }
  .search-section {
    padding: 0.85rem 1rem;
    gap: 0.75rem;
  }
  .search-bar {
    padding: 0.4rem 0.4rem 0.4rem 0.85rem;
  }
  .post-entry {
    padding: 0.85rem 1rem;
    gap: 0.6rem;
  }
  .entry-avatar {
    width: 3rem;
    height: 3rem;
  }
  .entry-textarea {
    min-height: 6rem;
  }
  .guide-btn {
    padding: 0.6rem 1rem;
  }
}
</style>
