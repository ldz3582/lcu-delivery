import { del, download, get, post, put } from './http'

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
  /** 以下字段后台列表暂未返回，编辑时留空表示不修改 */
  openTime?: string | null
  notice?: string | null
  minAmount?: number | null
  packFee?: number | null
  deliveryFee?: number | null
  categoryId?: number | null
  deliveryRangeMeters?: number | null
}

export interface Rider {
  id: number
  userId: number
  phoneMask: string
  auditStatus: number
  workStatus: number
  score: number
  totalOrders: number
  /** 超时单数 */
  timeoutOrders?: number
  /** 身份证人像面 */
  idCardFront?: string | null
  /** 身份证国徽面 */
  idCardBack?: string | null
  /** 校园卡 / 学生证 */
  studentCard?: string | null
  /** 兼职协议文件地址 */
  agreementUrl?: string | null
  /** 协议签署时间 */
  agreementSignedAt?: string | null
  /** 意外险保单号 */
  insuranceNo?: string | null
  /** 驳回原因（通过后清空） */
  rejectReason?: string | null
  createdAt?: string | null
  /** 1 正常 / 0 封禁（列表暂未返回，前端本地记忆） */
  status?: number
  banReason?: string | null
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
  /** 1 正常 / 0 封禁（列表暂未返回，前端本地记忆） */
  status?: number
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

/** 骑手审核：驳回时 reason 必填，通过时后端会清空驳回原因 */
export function auditRider(id: number, pass: boolean, reason = '') {
  return post<void>(`/admin/riders/${id}/audit`, { pass, reason })
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
  /** 商家名（ownerType=2） */
  shopName: string | null
  /** 骑手昵称（ownerType=3） */
  riderName?: string | null
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

export interface Incident {
  id: number
  orderId: number
  orderNo: string | null
  shopName: string | null
  /** 1 联系不上用户 / 2 餐品撒漏 / 3 地址错误 / 4 其他 */
  type: number
  typeText: string
  remark: string
  images: string[]
  /** 0 待处理 / 1 已处理 */
  status: number
  statusText: string
  handleRemark: string | null
  createdAt: string | null
}

/** 配送异常上报列表（后端返回数组，不分页） */
export function getIncidents(params: { status?: number }) {
  return get<Incident[]>('/admin/incidents', params as Record<string, unknown>)
}

/** 处置配送异常 */
export function handleIncident(id: number, remark: string) {
  return post<void>(`/admin/incidents/${id}/handle`, { remark })
}

export interface Penalty {
  id: number
  riderId: number
  orderId: number | null
  orderNo: string | null
  /** 1 超时 / 2 差评 / 3 违规操作 */
  type: number
  typeText: string
  /** 扣罚金额，单位分 */
  amount: number
  remark: string | null
  createdAt: string | null
}

/** 骑手处罚记录 */
export function getPenalties(params: { riderId?: number }) {
  return get<Penalty[]>('/admin/penalties', params as Record<string, unknown>)
}

export interface PenaltyPayload {
  /** 1 超时 / 2 差评 / 3 违规操作 */
  type: number
  /** 扣罚金额，单位分 */
  amount: number
  remark: string
  orderId?: number
}

/** 给骑手开罚单 */
export function createPenalty(riderId: number, data: PenaltyPayload) {
  return post<number>(`/admin/riders/${riderId}/penalty`, data)
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

// ==================== 数据看板（A15/A16） ====================

export interface TrendPoint {
  /** yyyy-MM-dd */
  date: string
  orders: number
  paidOrders: number
  /** GMV，单位分 */
  gmv: number
  finished: number
}

export interface ShopRank {
  shopId: number
  name: string
  orders: number
  /** 单位分 */
  gmv: number
  /** 单位分 */
  commission: number
}

export interface RiderRank {
  riderId: number
  orders: number
  /** 单位分 */
  income: number
  score: number
  timeoutOrders: number
  badReviews: number
}

export interface StatsRanking {
  shops: ShopRank[]
  riders: RiderRank[]
}

export interface StatsRates {
  finishedOrders: number
  timeoutOrders: number
  timeoutRate: number
  reviews: number
  badReviews: number
  badReviewRate: number
  totalUsers: number
  repeatUsers: number
  repurchaseRate: number
  newUsersToday: number
  newUsers7d: number
  /** 近 7 日新增用户占全部用户比例（%） */
  newUserShare7d: number
}

/** 近 N 天订单 / GMV 趋势 */
export function getStatsTrend(days = 7) {
  return get<TrendPoint[]>('/admin/stats/trend', { days })
}

/** 商家 / 骑手排行榜 */
export function getStatsRanking(limit = 10) {
  return get<StatsRanking>('/admin/stats/ranking', { limit })
}

/** 关键率值 */
export function getStatsRates() {
  return get<StatsRates>('/admin/stats/rates')
}

// ==================== 财务总览（A13） ====================

export interface FinanceOverview {
  gmv: number
  platformCommission: number
  deliveryFeeIncome: number
  riderPay: number
  deliveryServiceFee: number
  /** 全部已退款金额 */
  refunded: number
  /** 计入 GMV/佣金口径（未取消未退款订单）上的退款，净利润只扣这一部分 */
  refundedOnCounted: number
  pendingSettle: number
  settledShop: number
  settledRider: number
  reversedSettle: number
  withdrawPending: number
  withdrawPaid: number
  platformNetIncome: number
}

export interface WalletFlow {
  id: number
  walletId?: number
  /** 2 商家 / 3 骑手 */
  ownerType: number | null
  ownerId: number | null
  /** SHOP_SETTLE / RIDER_SETTLE / SETTLE_REVERSE / WITHDRAW / WITHDRAW_REFUND / PENALTY / CANCEL_PENALTY / SHOP_PENALTY */
  bizType: string
  bizNo: string | null
  /** 1 收入 / -1 支出 */
  direction: number
  amount: number
  balanceAfter: number
  remark: string | null
  createdAt: string | null
}

export function getFinanceOverview() {
  return get<FinanceOverview>('/admin/finance/overview')
}

export function getFinanceFlows(params: { bizType?: string; page: number; size: number }) {
  return get<PageResult<WalletFlow>>('/admin/finance/flows', params as Record<string, unknown>)
}

/** 导出订单 CSV（按当前筛选条件，带 token 下载） */
export function exportOrders(params: { status?: number; keyword?: string }) {
  const today = new Date().toISOString().slice(0, 10)
  return download('/admin/orders/export', params as Record<string, unknown>, `orders-${today}.csv`)
}

// ==================== 平台公告（A12/A17） ====================

export interface Announcement {
  id: number
  title: string
  content: string
  /** 1 首页 / 2 用户中心 */
  position: number
  /** 1 已发布 / 0 已下线 */
  status: number
  statusText: string
  startAt: string | null
  endAt: string | null
  createdAt: string | null
}

export interface AnnouncementPayload {
  title: string
  content: string
  position: number
  status?: number
  /** yyyy-MM-ddTHH:mm:ss */
  startAt?: string | null
  endAt?: string | null
}

export function getAnnouncements(params: { status?: number } = {}) {
  return get<Announcement[]>('/admin/announcements', params as Record<string, unknown>)
}

export function createAnnouncement(data: AnnouncementPayload) {
  return post<{ id: number }>('/admin/announcements', data)
}

export function updateAnnouncement(id: number, data: AnnouncementPayload) {
  return put<void>(`/admin/announcements/${id}`, data)
}

export function updateAnnouncementStatus(id: number, status: number) {
  return put<void>(`/admin/announcements/${id}/status`, { status })
}

export function deleteAnnouncement(id: number) {
  return del<void>(`/admin/announcements/${id}`)
}

// ==================== 后台账号权限（A1） ====================

export interface AdminAccount {
  id: number
  username: string
  realName: string
  /** SUPER / CS / FINANCE / AUDITOR / OPERATOR */
  role: string
  roleText: string
  phoneMask: string | null
  /** 1 启用 / 0 停用 */
  status: number
  statusText: string
  lastLoginAt: string | null
  createdAt: string | null
}

export interface AccountRole {
  value: string
  label: string
}

export interface AccountCreatePayload {
  username: string
  password: string
  realName: string
  role: string
}

export interface AccountUpdatePayload {
  realName?: string
  role?: string
  status?: number
  /** 填写则重置密码 */
  password?: string
}

export function getAccounts() {
  return get<AdminAccount[]>('/admin/accounts')
}

export function createAccount(data: AccountCreatePayload) {
  return post<{ id: number; role: string }>('/admin/accounts', data)
}

export function updateAccount(id: number, data: AccountUpdatePayload) {
  return put<void>(`/admin/accounts/${id}`, data)
}

export function getAccountRoles() {
  return get<AccountRole[]>('/admin/accounts/roles')
}

// ==================== 商家运营（A3/A4） ====================

export interface ShopUpdatePayload {
  name?: string
  contactPhone?: string
  openTime?: string
  notice?: string
  /** 起送价，单位分 */
  minAmount?: number
  /** 打包费，单位分 */
  packFee?: number
  /** 配送费，单位分 */
  deliveryFee?: number
  /** 佣金比例，0.06 表示 6% */
  commissionRate?: string
}

export interface RevenuePeriod {
  orders: number
  gmv: number
  commission: number
}

export interface MerchantPenalty {
  id: number
  /** 1 警告 / 2 罚款 / 3 下架商品 / 4 暂停营业 */
  type: number
  amount: number
  remark: string | null
  /** 后端直接返回数据库列名 */
  created_at?: string | null
  createdAt?: string | null
}

export interface ShopRevenue {
  today: RevenuePeriod
  week: RevenuePeriod
  month: RevenuePeriod
  penalties: MerchantPenalty[]
}

export function updateShop(id: number, data: ShopUpdatePayload) {
  return put<void>(`/admin/shops/${id}`, data)
}

export interface ShopDetail {
  id: number
  name: string
  categoryId: number | null
  campus: number | null
  bizStatus: number
  minAmount: number | null
  packFee: number | null
  deliveryFee: number | null
  openTime: string | null
  contactPhone: string | null
  notice: string | null
  deliveryRangeMeters: number | null
  address?: string | null
}

/** 商家详情（公开接口，后台编辑弹窗用于回填营业时间/公告/费用） */
export function getShopDetail(id: number) {
  return get<ShopDetail>(`/shops/${id}`)
}

export function freezeShop(id: number, frozen: boolean) {
  return post<void>(`/admin/shops/${id}/freeze`, { frozen })
}

export function getShopRevenue(id: number) {
  return get<ShopRevenue>(`/admin/shops/${id}/revenue`)
}

export interface ShopPenaltyPayload {
  /** 1 警告 / 2 罚款 / 3 下架商品 / 4 暂停营业 */
  type: number
  /** 罚款金额，单位分（仅 type=2 生效） */
  amount: number
  remark: string
}

export function createShopPenalty(id: number, data: ShopPenaltyPayload) {
  return post<number>(`/admin/shops/${id}/penalty`, data)
}

// ==================== 用户 / 骑手运营（A2/A5） ====================

export interface UserOrder {
  id: number
  orderNo: string
  shopId: number
  status: number
  statusText: string
  payAmount: number
  createdAt: string | null
}

export function getUserOrders(id: number) {
  return get<UserOrder[]>(`/admin/users/${id}/orders`)
}

/** 封禁 / 解封用户：status=1 解封，0 封禁 */
export function banUser(id: number, status: number) {
  return post<void>(`/admin/users/${id}/ban`, { status })
}

/** 封禁 / 解封骑手：status=1 解封，0 封禁 */
export function updateRiderStatus(id: number, status: number, reason = '') {
  return post<void>(`/admin/riders/${id}/status`, { status, reason })
}

// ==================== Banner / 用户反馈 / 商家商品 ====================

export interface Banner {
  id: number
  title: string
  /** 图片 URL */
  image: string
  /** 1 无跳转 / 2 店铺 / 3 券中心 */
  linkType: number
  /** 店铺 ID（linkType=2 时） */
  linkValue: string | null
  sort: number
  /** 1 上架 / 0 下架 */
  status: number
  startAt: string | null
  endAt: string | null
  createdAt: string | null
}

export interface BannerPayload {
  title: string
  image: string
  linkType: number
  linkValue: string | null
  sort: number
  status: number
  startAt: string | null
  endAt: string | null
}

export function getBanners() {
  return get<Banner[]>('/admin/banners')
}

export function createBanner(data: BannerPayload) {
  return post<number>('/admin/banners', data)
}

export function updateBanner(id: number, data: BannerPayload) {
  return put<void>(`/admin/banners/${id}`, data)
}

export function updateBannerStatus(id: number, status: number) {
  return put<void>(`/admin/banners/${id}/status`, { status })
}

export function deleteBanner(id: number) {
  return del<void>(`/admin/banners/${id}`)
}

export interface Feedback {
  id: number
  userId: number
  /** 1 意见建议 / 2 投诉 / 3 支付问题 / 4 其他 */
  type: number
  typeText: string
  content: string
  /** 后端存的是 JSON 字符串（也可能是数组），页面统一解析；无图时字段可能不返回 */
  images?: string | string[] | null
  contact?: string | null
  /** 0 待处理 / 1 处理中 / 2 已处理 */
  status: number
  statusText: string
  reply?: string | null
  createdAt: string | null
  handledAt?: string | null
}

export interface FeedbackHandlePayload {
  /** 1 处理中 / 2 已处理 */
  status: number
  reply?: string
}

export function getFeedbacks(params: { status?: number } = {}) {
  return get<Feedback[]>('/admin/feedbacks', params as Record<string, unknown>)
}

export function handleFeedback(id: number, data: FeedbackHandlePayload) {
  return post<void>(`/admin/feedbacks/${id}/handle`, data)
}

export interface ShopProduct {
  id: number
  name: string
  /** 原价，单位分 */
  price: number
  /** 特价，单位分（无特价时字段可能不返回） */
  specialPrice?: number | null
  stock: number
  /** 1 在售 / 0 已下架 */
  status: number
  statusText: string
  sales: number
}

export function getShopProducts(id: number) {
  return get<ShopProduct[]>(`/admin/shops/${id}/products`)
}

/** 商品强制下架（违规处置） */
export function offlineProduct(id: number) {
  return post<void>(`/admin/products/${id}/offline`)
}
