// 统一请求封装：自动带 token，业务码非 0 抛错，401 清登录态并回登录页
// VITE_API_BASE 不配置时走「同源相对路径」：H5 由 serve.mjs 把 /api 代理到后端，端口怎么变都不用重新打包；
// 小程序（mp-weixin）没有代理，构建时必须显式传 VITE_API_BASE=http://127.0.0.1:8081
const BASE_URL = (import.meta as any).env?.VITE_API_BASE || ''

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
  uni.removeStorageSync('lcu_role')
  uni.removeStorageSync('lcu_user')
}

export interface RequestOptions {
  url: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  data?: any
  showError?: boolean
}

/**
 * GET 请求里 uni.request 会把 undefined 序列化成字符串 "undefined" 拼进 URL，
 * 后端 @RequestParam Integer/BigDecimal 收到 "undefined" 会抛类型转换异常。
 * 这里统一剔除空值，避免脏参数发出去。
 */
function compact(data: any, dropEmptyString: boolean) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return data
  const out: Record<string, any> = {}
  Object.keys(data).forEach((k) => {
    const v = data[k]
    if (v === undefined || v === null) return
    if (dropEmptyString && v === '') return
    out[k] = v
  })
  return out
}

function gotoLogin() {
  const pages = getCurrentPages()
  const cur = pages.length ? pages[pages.length - 1].route : ''
  if (cur === 'pages/login/index') return
  setTimeout(() => uni.reLaunch({ url: '/pages/login/index' }), 50)
}

export function request<T = any>(options: RequestOptions): Promise<T> {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const method = (options.method || 'GET').toUpperCase()
    uni.request({
      url: BASE_URL + options.url,
      method: method as any,
      data: compact(options.data, method === 'GET'),
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
          if (body?.code === 401) {
            clearToken()
            gotoLogin()
          }
          if (options.showError !== false) uni.showToast({ title: msg, icon: 'none' })
          // 把业务码挂到 Error 上：页面需要按 2001(超出围栏)/1001 等分支处理
          const err: any = new Error(msg)
          err.code = body?.code
          reject(err)
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
