// 登录策略（自动降级）：
// 1. 有 AppID/AppSecret：uni.login 拿 code -> /api/auth/login -> 真实 openid
// 2. 没配置（touristappid / 后端未填密钥 / H5 环境）：自动回退到 mock-login
// 上线前把 app.mock-login 设为 false，未配置就会直接报错，不会静默降级。
import { http, getToken, setToken, clearToken } from './request'

export interface LoginResp {
  token: string
  userId: number
  role: string
  nickname: string
  avatar?: string
}

function deviceOpenid(): string {
  let openid = uni.getStorageSync('lcu_openid')
  if (!openid) {
    openid = 'dev_' + Math.random().toString(36).slice(2, 10)
    uni.setStorageSync('lcu_openid', openid)
  }
  return openid
}

function wxCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (r: any) => (r.code ? resolve(r.code) : reject(new Error('no code'))),
      fail: (e: any) => reject(e)
    })
  })
}

function persist(resp: LoginResp) {
  setToken(resp.token)
  uni.setStorageSync('lcu_role', resp.role)
  uni.setStorageSync('lcu_user', resp)
}

export async function loginWithRole(role: 'USER' | 'MERCHANT' | 'RIDER'): Promise<LoginResp> {
  let openid = deviceOpenid()
  // 商家/骑手用固定 openid，方便和演示数据对上
  if (role === 'MERCHANT') openid = 'mock_merchant'
  if (role === 'RIDER') openid = 'mock_rider'
  const resp = await http.post<LoginResp>('/api/auth/mock-login', { openid, role })
  persist(resp)
  return resp
}

/** 真实微信登录；失败时抛错，由调用方决定是否回退 */
export async function loginByWechat(): Promise<LoginResp> {
  const code = await wxCode()
  const resp = await http.post<LoginResp>('/api/auth/login', { code }, { showError: false })
  persist(resp)
  return resp
}

export async function ensureLogin(): Promise<LoginResp> {
  const cached = uni.getStorageSync('lcu_user')
  if (getToken() && cached) {
    return cached
  }
  try {
    return await loginByWechat()
  } catch (e) {
    console.info('微信登录不可用，回退到本地模拟登录', e)
    return loginWithRole('USER')
  }
}

export function logout() {
  clearToken()
  uni.removeStorageSync('lcu_user')
}

export function currentUser(): LoginResp | null {
  return uni.getStorageSync('lcu_user') || null
}
