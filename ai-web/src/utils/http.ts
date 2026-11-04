import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'

import type { ApiResponse } from '@/types/api'
import { clearStoredSession, getToken } from '@/utils/auth-storage'

declare module 'axios' {
  export interface AxiosRequestConfig {
    silent?: boolean
  }
}

const baseURL = import.meta.env.VITE_API_BASE_URL?.trim() || '/api'

const client = axios.create({
  baseURL,
  timeout: 60_000,
  headers: {
    'Content-Type': 'application/json',
  },
})

client.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

client.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const status = error.response?.status
    const code = error.response?.data?.code
    const message = error.response?.data?.message
    const currentPath = window.location.pathname
    const requestUrl = error.config?.url ?? ''

    if (status === 401 && !requestUrl.includes('/auth/login')) {
      clearStoredSession()
      ElMessage.warning('登录已过期，请重新登录')
      window.location.replace(`/login?expired=1&redirect=${encodeURIComponent(currentPath)}`)
      return Promise.reject(error)
    }

    if (status === 403 && code === 40301) {
      if (currentPath !== '/change-password') {
        ElMessage.warning('首次登录请先修改初始密码')
        window.location.replace('/change-password?forced=1')
      }
      return Promise.reject(error)
    }

    if (!error.config?.silent) {
      if (message) {
        ElMessage.error(message)
      } else if (error.code === 'ECONNABORTED') {
        ElMessage.error('请求超时，请稍后重试')
      } else if (!error.response) {
        ElMessage.error('无法连接服务器，请确认后端服务已启动')
      } else {
        ElMessage.error(defaultMessage(status))
      }
    }
    return Promise.reject(error)
  },
)

function defaultMessage(status?: number): string {
  switch (status) {
    case 403:
      return '没有权限执行此操作'
    case 404:
      return '请求的资源不存在'
    case 413:
      return '上传内容超过系统限制'
    case 429:
      return '请求过于频繁，请稍后重试'
    default:
      return '服务器开小差了，请稍后重试'
  }
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly code: number,
    public readonly traceId?: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

/**
 * 统一处理后端 { code, message, data } 包装。
 * HTTP 层错误在拦截器中处理；业务错误在这里转换并保证调用方拿到 data 本身。
 */
export async function http<T>(config: AxiosRequestConfig): Promise<T> {
  const response: AxiosResponse<ApiResponse<T>> = await client.request<ApiResponse<T>>(config)
  const payload = response.data
  if (payload && typeof payload.code === 'number') {
    if (payload.code !== 0) {
      if (!config.silent) {
        ElMessage.error(payload.message || '请求处理失败')
      }
      throw new ApiError(payload.message, payload.code, payload.traceId)
    }
    return payload.data
  }
  return payload as unknown as T
}

export function getErrorMessage(error: unknown, fallback = '操作失败'): string {
  if (error instanceof ApiError) return error.message
  if (axios.isAxiosError(error)) {
    const payload = error.response?.data as ApiResponse<unknown> | undefined
    return payload?.message || fallback
  }
  return error instanceof Error ? error.message : fallback
}

export default client
