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
/**
 * 上一次的「用户端」会话快照。
 * 为什么需要：从商家/骑手切回用户端时，如果走 mock-login 会拿一个随机的
 * dev_xxx openid 建出一个全新账号，用户之前下的单、地址、优惠券全看不见。
 * 所以切回用户端一律恢复这份快照，不再重新登录。
 */
const PREV_USER_KEY = 'lcu_prev_user'
/** 每个角色记住上次选的演示账号（商家=哪家店，骑手=哪个骑手） */
const ACCOUNT_KEY = 'lcu_account_'

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
  // 用户端会话存一份快照，切去商家/骑手再切回来时用它恢复，避免建新账号
  if (role === 'USER') uni.setStorageSync(PREV_USER_KEY, { token: resp.token, user: resp })
}

function deviceOpenid(): string {
  let openid = uni.getStorageSync(OPENID_KEY)
  if (!openid) {
    openid = 'dev_' + Math.random().toString(36).slice(2, 10)
    uni.setStorageSync(OPENID_KEY, openid)
  }
  return openid
}

export interface MockAccount {
  openid: string
  nickname?: string
  /** 商家 */
  shopId?: number
  shopName?: string
  campus?: number
  auditStatus?: number
  bizStatus?: number
  /** 骑手 */
  riderId?: number
  riderName?: string
  score?: number
}

/** 上次选的演示账号（没有就返回空串） */
export function savedOpenid(role: Role): string {
  return uni.getStorageSync(ACCOUNT_KEY + role) || ''
}

/** 演示账号列表：mock 环境才有数据，真实环境返回空数组，调用方自行降级 */
export async function fetchMockAccounts(role: Role): Promise<MockAccount[]> {
  try {
    return await http.get<MockAccount[]>('/api/auth/mock-accounts', { role }, { showError: false })
  } catch (e) {
    return []
  }
}

/**
 * 用户 / 商家 / 骑手：mock 登录（没有 AppID 也能跑通全流程）。
 * 传 openid 可指定「哪家店 / 哪个骑手」，不传用上次选的，再没有才用默认账号。
 */
export async function loginByRole(role: Role, openid?: string): Promise<LoginResp> {
  let oid = openid || savedOpenid(role)
  if (!oid) {
    if (role === 'MERCHANT') oid = 'mock_merchant'
    else if (role === 'RIDER') oid = 'mock_rider'
    else oid = deviceOpenid()
  }
  const resp = await http.post<LoginResp>('/api/auth/mock-login', { openid: oid, role })
  uni.setStorageSync(ACCOUNT_KEY + role, oid)
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
  uni.removeStorageSync(PREV_USER_KEY)
  uni.reLaunch({ url: '/pages/login/index' })
}

/**
 * 切换身份。
 * 切回「用户」端时优先恢复上一次的用户会话——绝不能走 mock-login，
 * 那会用随机 dev_ openid 建出一个新账号，用户的订单/地址/券全"消失"。
 */
export async function switchTo(role: Role): Promise<void> {
  if (role === 'USER') {
    const prev = uni.getStorageSync(PREV_USER_KEY)
    if (prev && prev.token && prev.user) {
      setToken(prev.token)
      uni.setStorageSync(ROLE_KEY, 'USER')
      uni.setStorageSync(USER_KEY, prev.user)
      uni.reLaunch({ url: HOME.USER })
      return
    }
  }
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
