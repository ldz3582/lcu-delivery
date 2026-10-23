import { http, getToken, setToken, clearToken } from './request'

export interface LoginResp { token: string; userId: number; role: string; nickname: string }

export async function login(role: 'MERCHANT' | 'RIDER', openid?: string) {
  const oid = openid || (role === 'MERCHANT' ? 'mock_merchant' : 'mock_rider')
  const resp = await http.post<LoginResp>('/api/auth/mock-login', { openid: oid, role })
  setToken(resp.token)
  uni.setStorageSync('wb_role', role)
  uni.setStorageSync('wb_user', resp)
  return resp
}

export function currentToken() { return getToken() }
export function currentRole() { return uni.getStorageSync('wb_role') }
export function currentUser() { return uni.getStorageSync('wb_user') }
export function logout() {
  clearToken()
  uni.removeStorageSync('wb_user')
  uni.removeStorageSync('wb_role')
  uni.reLaunch({ url: '/pages/login/login' })
}
