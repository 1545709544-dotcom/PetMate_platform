import { ref, type Ref } from 'vue'
import type { AxiosRequestConfig } from 'axios'
import request from '@/api/request'

/**
 * 通用请求 composable
 * 用法：
 *   const { data, loading, error, run } = useRequest<Product[]>('/api/products')
 *   await run()
 */
export interface UseRequestResult<T> {
  data: Ref<T | null>
  loading: Ref<boolean>
  error: Ref<Error | null>
  run: (config?: AxiosRequestConfig) => Promise<T | null>
  reset: () => void
}

export function useRequest<T = unknown>(
  defaultConfig?: AxiosRequestConfig | string,
): UseRequestResult<T> {
  const data = ref<T | null>(null) as Ref<T | null>
  const loading = ref(false)
  const error = ref<Error | null>(null)

  const run = async (config?: AxiosRequestConfig): Promise<T | null> => {
    loading.value = true
    error.value = null
    try {
      let finalConfig: AxiosRequestConfig = {}
      if (typeof defaultConfig === 'string') {
        finalConfig = { url: defaultConfig, method: 'GET' }
      } else if (defaultConfig) {
        finalConfig = { ...defaultConfig }
      }
      if (config) {
        finalConfig = { ...finalConfig, ...config }
      }
      const res = await request(finalConfig)
      data.value = res as T
      return res as T
    } catch (e) {
      error.value = e as Error
      return null
    } finally {
      loading.value = false
    }
  }

  const reset = () => {
    data.value = null
    loading.value = false
    error.value = null
  }

  return { data, loading, error, run, reset }
}