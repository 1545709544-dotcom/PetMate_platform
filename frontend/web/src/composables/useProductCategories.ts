import { ref, computed } from 'vue'
import { productApi, type Category } from '@/api/modules/product'

// ============================================================
// 商品分类单一数据源
// 所有页面（商品详情 / 全部商品 / 浏览 / 商家端管理 / 后台）
// 都从这里取分类结构，杜绝各页各自硬编码 id 映射导致的归类不一致。
// 数据来自后端 /category/tree（与数据库 t_product_category 实时同步）。
// ============================================================

// 一级分类图标（仅展示用，数据库无该字段时回落）
const L1_ICONS: Record<number, string> = {
  1: 'restaurant', // 宠物食品
  2: 'home_health', // 宠物用品
  3: 'medical_services', // 医疗保健
  4: 'toys', // 玩具服饰
}

const tree = ref<Category[]>([])
const loaded = ref(false)
let loadPromise: Promise<Category[]> | null = null

async function ensureTree(): Promise<Category[]> {
  if (loaded.value) return tree.value
  if (!loadPromise) {
    loadPromise = productApi
      .tree()
      .then((data) => {
        tree.value = data ?? []
        loaded.value = true
        return tree.value
      })
      .catch((e) => {
        loadPromise = null
        throw e
      })
  }
  return loadPromise
}

export interface CategoryTab {
  key: string
  label: string
  icon: string
  ids: number[] // 该一级分类下所有二级类目 id（用于过滤）
}

export function useProductCategories() {
  // 顶部分类 tab：全部 + 各一级分类（每个一级覆盖其全部二级）
  const topTabs = computed<CategoryTab[]>(() => {
    const tabs: CategoryTab[] = [
      { key: 'all', label: '全部商品', icon: 'apps', ids: [] },
    ]
    for (const l1 of tree.value) {
      tabs.push({
        key: String(l1.id),
        label: l1.name,
        icon: L1_ICONS[l1.id] ?? 'apps',
        ids: (l1.children ?? []).map((c) => c.id),
      })
    }
    return tabs
  })

  // tabKey -> 二级 id 集合（兼容旧调用方式）
  const tabCategoryMap = computed<Record<string, number[]>>(() => {
    const map: Record<string, number[]> = {}
    for (const t of topTabs.value) map[t.key] = t.ids
    return map
  })

  // id -> 名称 全量查找表（含一二级）
  const nameMap = computed<Record<number, string>>(() => {
    const m: Record<number, string> = {}
    for (const l1 of tree.value) {
      m[l1.id] = l1.name
      for (const c of l1.children ?? []) m[c.id] = c.name
    }
    return m
  })

  function nameOf(id?: number | null): string {
    if (id == null) return ''
    return nameMap.value[id] ?? ''
  }

  // 给定商品 categoryId（通常为二级 id），返回其所属**一级分类**的显示名
  function l1NameOf(categoryId?: number | null): string {
    if (!categoryId) return ''
    for (const l1 of tree.value) {
      if (l1.id === categoryId) return l1.name // 本身就是一级
      if ((l1.children ?? []).some((c) => c.id === categoryId)) return l1.name
    }
    return ''
  }

  // 给定商品 categoryId，返回其所属一级分类 tab key（用于回显激活态）
  function topKeyOf(categoryId?: number | null): string {
    if (!categoryId) return 'all'
    for (const l1 of tree.value) {
      if (l1.id === categoryId) return String(l1.id)
      if ((l1.children ?? []).some((c) => c.id === categoryId)) return String(l1.id)
    }
    return 'all'
  }

  return { tree, topTabs, tabCategoryMap, nameOf, l1NameOf, topKeyOf, ensureTree, ready: loaded }
}
