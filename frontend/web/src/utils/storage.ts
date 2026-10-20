/**
 * LocalStorage 封装（含类型安全、JSON 序列化、过期控制）
 */

export interface StorageItem<T> {
  value: T
  expire?: number // 过期时间戳（毫秒）
}

export const storage = {
  set<T>(key: string, value: T, expireMs?: number): void {
    const item: StorageItem<T> = { value }
    if (expireMs) item.expire = Date.now() + expireMs
    localStorage.setItem(key, JSON.stringify(item))
  },

  get<T>(key: string, defaultValue?: T): T | undefined {
    const raw = localStorage.getItem(key)
    if (!raw) return defaultValue
    try {
      const item = JSON.parse(raw) as StorageItem<T>
      if (item.expire && Date.now() > item.expire) {
        localStorage.removeItem(key)
        return defaultValue
      }
      return item.value
    } catch {
      return defaultValue
    }
  },

  remove(key: string): void {
    localStorage.removeItem(key)
  },

  clear(): void {
    localStorage.clear()
  },
}

export const session = {
  set<T>(key: string, value: T): void {
    sessionStorage.setItem(key, JSON.stringify({ value }))
  },

  get<T>(key: string, defaultValue?: T): T | undefined {
    const raw = sessionStorage.getItem(key)
    if (!raw) return defaultValue
    try {
      const item = JSON.parse(raw) as StorageItem<T>
      return item.value
    } catch {
      return defaultValue
    }
  },

  remove(key: string): void {
    sessionStorage.removeItem(key)
  },
}

export const STORAGE_KEYS = {
  TOKEN: 'pm_token',
  USER: 'pm_user',
  CART: 'pm_cart',
  THEME: 'pm_theme',
} as const