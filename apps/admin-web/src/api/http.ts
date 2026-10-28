import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { ElMessage } from 'element-plus'
import { normalizeMedia } from '@/utils/media'

export const TOKEN_KEY = 'lcu_admin_token'
export const NAME_KEY = 'lcu_admin_real_name'
export const ROLE_KEY = 'lcu_admin_role'

/** 后端统一响应：{ code, msg, data }，code === 0 表示成功 */
export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

/** 清除登录态并跳转登录页（整页跳转，避免与路由模块互相引用） */
function redirectToLogin() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(NAME_KEY)
  localStorage.removeItem(ROLE_KEY)
  const { pathname, search } = window.location
  if (pathname !== '/login') {
    const current = `${pathname}${search}`
    window.location.replace(`/login?redirect=${encodeURIComponent(current)}`)
  }
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response: AxiosResponse<ApiResult>) => {
    const body = response.data
    // 统一响应体
    if (body && typeof body === 'object' && typeof body.code === 'number') {
      if (body.code === 0) {
        // 统一把历史数据里的绝对图片地址还原成同源相对路径（见 utils/media.ts）
        return normalizeMedia(body.data) as never
      }
      if (body.code === 401) {
        ElMessage.error(body.msg || '登录已过期，请重新登录')
        redirectToLogin()
      } else {
        ElMessage.error(body.msg || '请求失败')
      }
      return Promise.reject(new Error(body.msg || '请求失败'))
    }
    // 非标准响应（如纯文本）直接返回
    return body as never
  },
  (error) => {
    const status = error?.response?.status
    if (status === 401) {
      ElMessage.error('登录已过期，请重新登录')
      redirectToLogin()
    } else {
      const msg =
        error?.response?.data?.msg || error?.message || '网络异常，请稍后重试'
      ElMessage.error(msg)
    }
    return Promise.reject(error)
  },
)

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return http.get(url, { params }) as unknown as Promise<T>
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return http.post(url, data) as unknown as Promise<T>
}

export function put<T>(url: string, data?: unknown): Promise<T> {
  return http.put(url, data) as unknown as Promise<T>
}

export function del<T>(url: string, data?: unknown): Promise<T> {
  return http.delete(url, { data }) as unknown as Promise<T>
}

/**
 * 带 token 下载二进制文件（如订单导出 CSV）。
 * 走 axios 实例以复用 Authorization 拦截器；后端出错时返回 JSON，这里解析后抛出。
 */
export async function download(
  url: string,
  params?: Record<string, unknown>,
  fallbackName = 'download.csv',
): Promise<void> {
  const res = (await http.get(url, {
    params,
    responseType: 'blob',
  })) as unknown as Blob
  const isJson = res?.type?.includes('json')
  if (isJson) {
    const text = await res.text()
    try {
      const body = JSON.parse(text) as ApiResult
      ElMessage.error(body.msg || '导出失败')
      throw new Error(body.msg || '导出失败')
    } catch (e) {
      if (e instanceof Error && e.message) throw e
      throw new Error('导出失败')
    }
  }
  const objectUrl = URL.createObjectURL(res)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = fallbackName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

export default http
