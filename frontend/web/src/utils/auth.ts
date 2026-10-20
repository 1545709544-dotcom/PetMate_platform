import { session, STORAGE_KEYS } from './storage'

export const tokenManager = {
  /**
   * 读取当前 token（仅会话级：存于 sessionStorage，关闭浏览器即失效）
   */
  get(): string | null {
    return session.get<string>(STORAGE_KEYS.TOKEN) || null
  },

  /**
   * 写入 token（会话级，关闭浏览器清除）
   */
  set(token: string): void {
    session.set(STORAGE_KEYS.TOKEN, token)
  },

  clear(): void {
    session.remove(STORAGE_KEYS.TOKEN)
  },

  isLoggedIn(): boolean {
    return !!tokenManager.get()
  },
}