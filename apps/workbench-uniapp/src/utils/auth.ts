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

function wxCode(): Promise<string> {
  return new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (r: any) => (r.code ? resolve(r.code) : reject(new Error('no code'))),
      fail: (e: any) => reject(e)
    })
  })
}

/** 真实微信登录：角色由后端账号决定，不符直接提示 */
export async function loginByWechat(role: 'MERCHANT' | 'RIDER') {
  const code = await wxCode()
  const resp = await http.post<LoginResp>('/api/auth/login', { code }, { showError: false })
  if (resp.role !== role) {
    throw new Error(role === 'MERCHANT' ? '该微信号还不是商家，请先联系平台开通' : '该微信号还不是骑手，请先提交入驻申请')
  }
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
