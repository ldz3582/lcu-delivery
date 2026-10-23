import { get, post, put } from './http'

/** 分页结构 */
export interface PageResult<T> {
  list: T[]
  total: number
  page?: number
  size?: number
}

export interface LoginResp {
  token: string
  username?: string
  realName: string
  role: string
}

export interface Overview {
  todayOrders: number
  todayGmv: number
  totalShops: number
  pendingShops: number
  totalRiders: number
  pendingRiders: number
  totalUsers: number
  inDelivery: number
}

export interface Shop {
  id: number
  name: string
  campus: number | null
  address: string
  contactPhone: string
  auditStatus: number
  bizStatus: number
  licenseUrl?: string
  permitUrl?: string
  commissionRate?: number
}

export interface Rider {
  id: number
  userId: number
  phoneMask: string
  auditStatus: number
  workStatus: number
  score: number
  totalOrders: number
}

export interface Order {
  id: number
  orderNo: string
  userId: number
  shopId: number
  riderId: number | null
  status: number
  statusText: string
  payAmount: number
  commission: number
  createdAt: string
}

export interface Settlement {
  id: number
  orderId: number
  shopId: number
  riderId: number | null
  goodsAmount: number
  commission: number
  shopIncome: number
  riderIncome: number
  status: number
  settleAt: string | null
  createdAt: string
}

export interface SysConfig {
  configKey: string
  configValue: string
  valueType: string
  groupKey: string
  remark: string
}

export interface Fence {
  id: number
  name: string
  campus: number
  wkt: string
}

/** 登录 */
export function login(username: string, password: string) {
  return post<LoginResp>('/admin/login', { username, password })
}

export interface AdminUser {
  id: number
  openid: string
  nickname: string
  role: string
  phoneMask: string | null
  identityStatus: number
  orderCount: number
  createdAt: string | null
}

/** 用户列表 */
export function getUsers(params: { keyword?: string; page: number; size: number }) {
  return get<PageResult<AdminUser>>('/admin/users', params as Record<string, unknown>)
}

/** 概览统计 */
export function getOverview() {
  return get<Overview>('/admin/overview')
}

/** 商家列表 */
export function getShops(params: { auditStatus?: number | ''; page: number; size: number }) {
  return get<PageResult<Shop>>('/admin/shops', params as Record<string, unknown>)
}

/** 商家审核 */
export function auditShop(id: number, pass: boolean, reason = '') {
  return post<void>(`/admin/shops/${id}/audit`, { pass, reason })
}

/** 骑手列表 */
export function getRiders(params: { auditStatus?: number | ''; page: number; size: number }) {
  return get<PageResult<Rider>>('/admin/riders', params as Record<string, unknown>)
}

/** 骑手审核 */
export function auditRider(id: number, pass: boolean) {
  return post<void>(`/admin/riders/${id}/audit`, { pass })
}

/** 订单列表 */
export function getOrders(params: {
  status?: number | ''
  keyword?: string
  page: number
  size: number
}) {
  return get<PageResult<Order>>('/admin/orders', params as Record<string, unknown>)
}

/** 结算单列表 */
export function getSettlements(params: { page: number; size: number }) {
  return get<PageResult<Settlement>>('/admin/settlements', params as Record<string, unknown>)
}

/** 平台配置列表 */
export function getConfigs() {
  return get<SysConfig[]>('/admin/configs')
}

/** 修改平台配置 */
export function updateConfig(key: string, value: string) {
  return put<void>('/admin/configs', { key, value })
}

/** 配送围栏（只读） */
export function getFences() {
  return get<Fence[]>('/admin/fence')
}
