// 统一请求封装：自动带 token，业务码非 0 抛错，401 清登录态
const BASE_URL = (import.meta as any).env?.VITE_API_BASE || 'http://127.0.0.1:8080'

export interface ApiResp<T> {
  code: number
  msg: string
  data: T
}

export function getToken(): string {
  return uni.getStorageSync('lcu_token') || ''
}

export function setToken(token: string) {
  uni.setStorageSync('lcu_token', token)
}

export function clearToken() {
  uni.removeStorageSync('lcu_token')
}

export interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  showError?: boolean
}

export function request<T = any>(options: RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    uni.request({
      url: BASE_URL + options.url,
      method: (options.method || 'GET') as any,
      data: options.data,
      timeout: 15000,
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      success: (res: any) => {
        const body = res.data as ApiResp<T>
        if (body && body.code === 0) {
          resolve(body.data)
        } else {
          const msg = body?.msg || `请求失败(${res.statusCode})`
          if (body?.code === 401) clearToken()
          if (options.showError !== false) uni.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
        }
      },
      fail: (err: any) => {
        const msg = err?.errMsg || '网络异常，请检查后端是否启动'
        if (options.showError !== false) uni.showToast({ title: msg, icon: 'none' })
        reject(new Error(msg))
      }
    })
  })
}

type Opt = { showError?: boolean }

export const http = {
  get: <T = any>(url: string, data?: any, o?: Opt) => request<T>({ url, data, ...o }),
  post: <T = any>(url: string, data?: any, o?: Opt) => request<T>({ url, method: 'POST', data, ...o }),
  put: <T = any>(url: string, data?: any, o?: Opt) => request<T>({ url, method: 'PUT', data, ...o }),
  del: <T = any>(url: string, data?: any, o?: Opt) => request<T>({ url, method: 'DELETE', data, ...o })
}
