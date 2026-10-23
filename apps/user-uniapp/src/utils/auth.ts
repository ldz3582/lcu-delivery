// 本地/内测登录：没有小程序 AppSecret 时，用 mock-login 换 token。
// 接真实微信登录时，把这里换成 uni.login 拿 code，再调 /api/auth/login。
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

export async function loginWithRole(role: 'USER' | 'MERCHANT' | 'RIDER'): Promise<LoginResp> {
  let openid = deviceOpenid()
  // 商家/骑手用固定 openid，方便和演示数据对上
  if (role === 'MERCHANT') openid = 'mock_merchant'
  if (role === 'RIDER') openid = 'mock_rider'
  const resp = await http.post<LoginResp>('/api/auth/mock-login', { openid, role })
  setToken(resp.token)
  uni.setStorageSync('lcu_role', resp.role)
  uni.setStorageSync('lcu_user', resp)
  return resp
}

export async function ensureLogin(): Promise<LoginResp> {
  if (getToken()) {
    return uni.getStorageSync('lcu_user')
  }
  return loginWithRole('USER')
}

export function logout() {
  clearToken()
  uni.removeStorageSync('lcu_user')
}

export function currentUser(): LoginResp | null {
  return uni.getStorageSync('lcu_user') || null
}
