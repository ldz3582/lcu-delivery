// 统一身份体系：用户 / 商家 / 骑手 / 管理员 共用一个小程序
// 角色存在本地，切换身份即换一套底部导航与首页。
import { http, getToken, setToken, clearToken } from './request'

export type Role = 'USER' | 'MERCHANT' | 'RIDER'

export interface LoginResp {
  token: string
  userId?: number
  role: string
  nickname?: string
  realName?: string
  username?: string
  avatar?: string
  phoneMask?: string
}

const ROLE_KEY = 'lcu_role'
const USER_KEY = 'lcu_user'
const OPENID_KEY = 'lcu_openid'

/** 每个角色的首页 */
export const HOME: Record<Role, string> = {
  USER: '/pages/index/index',
  MERCHANT: '/pages/merchant/orders',
  RIDER: '/pages/rider/pool'
}

export const ROLE_LABEL: Record<Role, string> = {
  USER: '用户',
  MERCHANT: '商家',
  RIDER: '骑手'
}

export const ROLE_DESC: Record<Role, string> = {
  USER: '点外卖、下单、看订单',
  MERCHANT: '接单、出餐、管理菜品',
  RIDER: '抢单、取餐、送达'
}

export function currentRole(): Role | '' {
  return (uni.getStorageSync(ROLE_KEY) || '') as Role | ''
}

export function currentUser(): LoginResp | null {
  return uni.getStorageSync(USER_KEY) || null
}

export function hasLogin(): boolean {
  return !!getToken() && !!currentRole()
}

function persist(resp: LoginResp, role: Role) {
  setToken(resp.token)
  uni.setStorageSync(ROLE_KEY, role)
  uni.setStorageSync(USER_KEY, resp)
}

function deviceOpenid(): string {
  let openid = uni.getStorageSync(OPENID_KEY)
  if (!openid) {
    openid = 'dev_' + Math.random().toString(36).slice(2, 10)
    uni.setStorageSync(OPENID_KEY, openid)
  }
  return openid
}

/** 用户 / 商家 / 骑手：mock 登录（没有 AppID 也能跑通全流程） */
export async function loginByRole(role: Role): Promise<LoginResp> {
  let openid = deviceOpenid()
  if (role === 'MERCHANT') openid = 'mock_merchant'
  if (role === 'RIDER') openid = 'mock_rider'
  const resp = await http.post<LoginResp>('/api/auth/mock-login', { openid, role })
  persist(resp, role)
  return resp
}

/** 学号/工号登录（等同注册）：本地与演示环境使用，登录即完成校内身份核验 */
export async function loginByStuNo(stuNo: string): Promise<LoginResp> {
  const resp = await http.post<LoginResp>('/api/auth/stu-login', { stuNo: stuNo.trim() })
  persist(resp, 'USER')
  return resp
}

/** 真实微信登录：角色由后端账号决定 */
export async function loginByWechat(): Promise<LoginResp> {
  const code = await new Promise<string>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (r: any) => (r.code ? resolve(r.code) : reject(new Error('微信未返回 code'))),
      fail: (e: any) => reject(e)
    })
  })
  const resp = await http.post<LoginResp>('/api/auth/login', { code }, { showError: false })
  const role = ((resp.role || 'USER') === 'ADMIN' ? 'USER' : (resp.role || 'USER')) as Role
  persist(resp, role)
  return resp
}

export function logout() {
  clearToken()
  uni.reLaunch({ url: '/pages/login/index' })
}

/** 直接切身份（已登录状态下换角色，会重新走一次对应登录） */
export async function switchTo(role: Role): Promise<void> {
  clearToken()
  await loginByRole(role)
  uni.reLaunch({ url: HOME[role] })
}

/**
 * 页面守卫：未登录 → 登录页；身份不符 → 回到自己角色的首页。
 * 在页面 onShow 里调用，返回 false 时页面不要再拉数据。
 */
export function guard(role: Role): boolean {
  const r = currentRole()
  if (!getToken() || !r) {
    uni.reLaunch({ url: '/pages/login/index' })
    return false
  }
  if (r !== role) {
    uni.reLaunch({ url: HOME[r] || '/pages/login/index' })
    return false
  }
  return true
}
