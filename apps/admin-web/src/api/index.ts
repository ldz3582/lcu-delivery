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
  /** 门店照片（绝对地址） */
  photos?: string[]
  /** 驳回原因 */
  rejectReason?: string | null
  createdAt?: string | null
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

export interface Withdrawal {
  id: number
  /** 2 商家（当前只有商家提现） */
  ownerType: number
  ownerId: number
  shopName: string | null
  /** 提现金额，单位分 */
  amount: number
  /** 0 待审核 / 1 打款中 / 2 已到账 / 3 已驳回 */
  status: number
  statusText: string
  channel: string
  failReason?: string | null
  appliedAt: string | null
  auditedAt?: string | null
}

/** 提现单列表 */
export function getWithdrawals(params: { status?: number; page: number; size: number }) {
  return get<PageResult<Withdrawal>>('/admin/withdrawals', params as Record<string, unknown>)
}

/** 提现审核：通过=打款完成（释放冻结），驳回=金额退回可用余额 */
export function auditWithdrawal(id: number, pass: boolean, reason = '') {
  return post<void>(`/admin/withdrawals/${id}/audit`, { pass, reason })
}

/** 手动结算单笔结算（正常由定时任务自动结算，用于演示/应急） */
export function settleSettlement(id: number) {
  return post<void>(`/admin/settlements/${id}/settle`)
}

/** 平台配置列表 */
export function getConfigs() {
  return get<SysConfig[]>('/admin/configs')
}

/** 修改平台配置 */
export function updateConfig(key: string, value: string) {
  return put<void>('/admin/configs', { key, value })
}

export interface Coupon {
  id: number
  name: string
  /** 1 满减 / 2 折扣 */
  type: number
  typeText: string
  /** 门槛，单位分 */
  threshold: number
  /** 满减为减免金额（分）；折扣为千分比（880 = 8.8 折） */
  amount: number
  total: number
  remain: number
  /** 1 全场 / 2 指定店铺 */
  scope: number
  scopeText: string
  shopId: number | null
  startAt: string | null
  endAt: string | null
  /** 1 上架 / 0 下架 */
  status: number
  claimed: number
  used: number
  desc: string
}

/** 优惠券列表 */
export function getCoupons(params: { page: number; size: number }) {
  return get<PageResult<Coupon>>('/admin/coupons', params as Record<string, unknown>)
}

export interface CouponCreatePayload {
  name: string
  type: number
  /** 门槛，单位分 */
  threshold: number
  /** 满减为减免金额（分）；折扣为千分比（880 = 8.8 折） */
  amount: number
  total: number
  scope: number
  shopId: number | null
  /** yyyy-MM-ddTHH:mm:ss */
  startAt: string
  endAt: string
}

/** 新建优惠券，返回新券 ID */
export function createCoupon(data: CouponCreatePayload) {
  return post<number>('/admin/coupons', data)
}

/** 上架 / 下架优惠券 */
export function updateCouponStatus(id: number, status: number) {
  return put<void>(`/admin/coupons/${id}/status`, { status })
}

export interface AfterSale {
  id: number
  orderId: number
  orderNo: string | null
  shopId: number | null
  shopName: string | null
  userId: number
  nickname: string | null
  /** 1 少餐 / 2 漏餐 / 3 餐品变质 / 4 撒漏 / 5 地址错误 / 6 其他 */
  type: number
  typeText: string
  reason: string
  images: string[]
  /** 申请退款金额，单位分 */
  amount: number
  /** 订单实付，单位分 */
  payAmount: number | null
  /** 0 待处理 / 1 处理中 / 2 已退款 / 3 已驳回 / 4 已关闭 */
  status: number
  statusText: string
  /** 1 商家 / 2 骑手 / 3 用户 */
  liableParty: number | null
  judgeRemark: string | null
  createdAt: string | null
}

/** 售后工单列表（后台仲裁） */
export function getAfterSales(params: { status?: number; page: number; size: number }) {
  return get<PageResult<AfterSale>>('/admin/after-sales', params as Record<string, unknown>)
}

export interface AfterSaleJudgePayload {
  approve: boolean
  /** 退款金额，单位分 */
  amount: number
  /** 1 商家 / 2 骑手 / 3 用户 */
  liableParty: number
  remark: string
}

/** 平台仲裁：同意退款 / 驳回 */
export function judgeAfterSale(id: number, data: AfterSaleJudgePayload) {
  return post<void>(`/admin/after-sales/${id}/judge`, data)
}

export interface Review {
  id: number
  orderId: number
  /** 后端目前未返回订单号，前端按 orderId 兜底展示 */
  orderNo?: string | null
  shopId: number
  shopName: string | null
  shopScore: number
  riderScore: number | null
  content: string | null
  images: string[]
  anonymous: boolean
  nickname: string
  avatar?: string | null
  reply: string | null
  /** 1 显示 / 0 隐藏 */
  status: number
  createdAt: string | null
}

/** 评价列表（后台评价管理） */
export function getReviews(params: {
  status?: number
  score?: number
  page: number
  size: number
}) {
  return get<PageResult<Review>>('/admin/reviews', params as Record<string, unknown>)
}

/** 隐藏 / 恢复评价 */
export function updateReviewStatus(id: number, status: number) {
  return put<void>(`/admin/reviews/${id}/status`, { status })
}

export interface NotifyLog {
  id: number
  /** 1 用户 */
  targetType: number
  targetId: number
  templateId: string | null
  bizNo: string
  channel: string
  /** 0 待发送 / 1 发送成功 / 2 发送失败 */
  status: number
  statusText: string
  error: string | null
  createdAt: string | null
}

/** 订阅消息发送日志（后端未返回 total，按页长判断下一页） */
export function getNotifyLogs(params: { page: number; size: number }) {
  return get<{ list: NotifyLog[]; page: number; size: number }>(
    '/admin/notify-logs',
    params as Record<string, unknown>,
  )
}

/** 配送围栏（只读） */
export function getFences() {
  return get<Fence[]>('/admin/fence')
}
