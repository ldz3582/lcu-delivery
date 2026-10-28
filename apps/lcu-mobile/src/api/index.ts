import { http, getToken } from '../utils/request'

export interface Category { id: number; name: string; icon?: string }
export interface ShopCard {
  id: number; name: string; logo?: string; categoryId: number; campus: number
  bizStatus: number; minAmount: number; packFee: number; deliveryFee: number
  score: number; monthSales: number; openTime?: string
  /** 传 lng/lat 时后端返回的直线距离（米） */
  distance?: number
  /** 传 lng/lat 时后端返回的预计送达分钟数；旧数据可能没有，前端按距离兜底 */
  etaMinutes?: number
}
export interface ProductSku {
  id: number; productId?: number; name: string; price: number; stock: number; status?: number
}
export interface ComboItem {
  productId: number; name: string; qty: number
}
export interface Product {
  id: number; name: string; image?: string; description?: string
  price: number; originPrice?: number; stock: number; sales?: number
  /** 1 = 该商品有规格，加购前必须先选规格 */
  hasSku?: number
  /** 套餐组成（店铺详情接口已解析成数组） */
  comboItems?: ComboItem[]
  /** hasSku=1 时后端返回启用中的规格 */
  skus?: ProductSku[]
  status?: number
  categoryId?: number
  /** 限时特价活动价（分），null/0 表示没设特价 */
  specialPrice?: number | null
  specialStartAt?: string | null
  specialEndAt?: string | null
  /** true = 此刻正处于特价时段（后端 CartService.specialActive 计算） */
  specialActive?: boolean
  /** 当前实际成交价（分）：特价生效时等于 specialPrice，否则等于 price */
  priceNow?: number
}
export interface ShopDetail extends ShopCard {
  address: string; lng: number; lat: number; contactPhone?: string; products: Product[]
  /** 店铺公告（GET /api/shops/{id}） */
  notice?: string | null
  /** 配送范围（米），空表示未限制/未配置 */
  deliveryRangeMeters?: number | null
  /** 本店券 + 全场券（GET /api/shops/{id}）；空数组时不展示 */
  coupons?: ShopCoupon[]
}

/** 店铺页可领取券（label 已由后端拼好：满20减5 / 满15打8.8折） */
export interface ShopCoupon {
  id: number; name: string; label: string
  type: number; threshold: number; amount: number
  scope: number; claimed: boolean
}

/** 平台公告（GET /api/announcements，公开） */
export interface Announcement {
  id: number; title: string; content: string
  position?: number; createdAt?: string
}

/** 首页运营位（GET /api/banners，公开） */
export interface Banner {
  id: number; title: string; image: string
  /** 1 无跳转 / 2 店铺（linkValue 是 shopId）/ 3 优惠券中心 */
  linkType: number
  /** linkType=2 时是 shopId 字符串 */
  linkValue?: string | number | null
  sort?: number; status?: number
  startAt?: string | null; endAt?: string | null
}

/** 首页限时特价卡片（GET /api/specials，公开） */
export interface SpecialItem {
  productId: number; name: string; image?: string
  /** 原价（分） */
  price: number
  /** 特价（分） */
  specialPrice: number
  specialEndAt?: string | null
  /** 例如 85 表示 8.5 折 */
  discountPercent: number
  shopId: number; shopName?: string; campus?: number
}
export interface Address {
  id: number; campus?: number; detail: string; building?: string; floor?: string
  room?: string; remark?: string; lng?: number; lat?: number; inFence?: number; isDefault?: number
}
export interface OrderCard {
  id: number; orderNo: string; shopId: number; status: number; statusText: string
  payAmount: number; goodsAmount: number; deliveryFee: number; createdAt: string
  /** GET /api/orders 每行返回：这单是否已评价（待评价 tab 用 reviewed=0 过滤） */
  reviewed?: boolean
}
export interface Bill {
  goodsAmount: number; packFee: number; deliveryFee: number; discountAmount: number
  totalAmount: number; minAmount: number; reachMin: boolean; shopName: string
  /** 未用券时后端返回 null，且服务端 non_null 会把 null 字段整个省略 */
  userCouponId?: number | null; couponName?: string | null
}

// ---------------- 优惠券 ----------------
/** 领券中心卡片（GET /api/coupons/available） */
export interface CouponTemplate {
  id: number; name: string; type: number; threshold: number; amount: number
  desc?: string; scope?: number; shopId?: number; remain?: number
  startAt?: string; endAt?: string; typeText?: string
}
/** 我的券（GET /api/coupons/mine），usable/discount/reason/nearExpire 仅未使用场景返回 */
export interface MyCoupon {
  id: number; name: string; type: number; threshold: number; amount: number
  userCouponId?: number; desc?: string; scope?: number; shopId?: number; remain?: number
  startAt?: string; endAt?: string; gotAt?: string
  status: number; statusText?: string
  usable?: boolean; discount?: number; reason?: string; nearExpire?: boolean
}

/** 订单配送进度（GET /api/orders/{id}/track） */
export interface OrderTrackStep {
  key: string; name: string; done: boolean; current: boolean; time: string | null
}
export interface OrderTrackEvent {
  status: number; statusText: string; remark: string; time: string
}
export interface OrderTrackRider {
  id: number; name: string; phone: string; score: number
}
export interface OrderTrackLocation {
  lng: number; lat: number; updatedAt: string
}
export interface OrderTrackShop {
  id: number; name: string; lng: number; lat: number
}
export interface OrderTrackDestination {
  lng: number; lat: number; text: string
}
export interface OrderTrack {
  orderId: number; status: number; statusText: string; etaMinutes: number | null
  steps: OrderTrackStep[]; timeline: OrderTrackEvent[]
  rider: OrderTrackRider | null; location: OrderTrackLocation | null
  shop: OrderTrackShop; destination: OrderTrackDestination
}

// ---------------- 用户端 ----------------
export const api = {
  categories: () => http.get<Category[]>('/api/categories'),
  shops: (params: Record<string, any>) => http.get<{ list: ShopCard[]; total: number }>('/api/shops', params),
  shop: (id: number) => http.get<ShopDetail>(`/api/shops/${id}`),
  /** 首页 Banner 轮播（公开；失败时首页只是不渲染该区块） */
  banners: () => http.get<Banner[]>('/api/banners', undefined, { showError: false }),
  /** 首页限时特价专区（公开；只返回此刻正在特价且在售的菜品，按折扣力度升序） */
  specials: (limit = 10) =>
    http.get<SpecialItem[]>('/api/specials', { limit }, { showError: false }),
  calculate: (shopId: number, items: { productId: number; qty: number }[], userCouponId?: number | null) =>
    http.post<Bill>('/api/cart/calculate', { shopId, items, userCouponId }),

  addresses: () => http.get<Address[]>('/api/addresses'),
  createAddress: (data: Partial<Address>) => http.post<Address>('/api/addresses', data),
  updateAddress: (id: number, data: Partial<Address>) => http.put<Address>(`/api/addresses/${id}`, data),
  deleteAddress: (id: number) => http.del(`/api/addresses/${id}`),
  parseAddress: (text: string, lng?: number, lat?: number) =>
    http.post<any>('/api/addresses/parse', { text, lng, lat }),

  createOrder: (data: any) => http.post<{ orderId: number; orderNo: string; payAmount: number }>('/api/orders', data),
  /**
   * 我的订单。statuses 传 "70,80" 做多状态过滤；reviewed=0/1 只看未评价/已评价；
   * 分页由调用方控制，避免只取第一页导致售后单漏显示。
   */
  orders: (params?: { status?: number; statuses?: string; reviewed?: number; page?: number; size?: number }) =>
    http.get<{ list: OrderCard[]; total: number }>('/api/orders', {
      status: params?.status,
      statuses: params?.statuses,
      reviewed: params?.reviewed,
      page: params?.page ?? 1,
      size: params?.size ?? 20
    }),
  order: (id: number) => http.get<any>(`/api/orders/${id}`),
  /** 配送进度/骑手位置；轮询场景静默失败，由页面降级，不弹 toast */
  track: (id: number) => http.get<OrderTrack>(`/api/orders/${id}/track`, undefined, { showError: false }),
  cancelOrder: (id: number, reason = '') => http.post(`/api/orders/${id}/cancel`, { reason }),
  confirmOrder: (id: number) => http.post(`/api/orders/${id}/confirm`),

  prepay: (orderId: number) => http.post<any>('/api/pay/prepay', { orderId }),
  mockPay: (orderId: number) => http.post<any>('/api/pay/mock-pay', { orderId }),

  profile: () => http.get<any>('/api/user/profile')
}

/** 用户端优惠券：领券中心 + 我的券 */
export const couponApi = {
  /** shopId 用于额外返回该店铺专属券（全场券始终返回） */
  available: (shopId?: number) => http.get<CouponTemplate[]>('/api/coupons/available', { shopId }),
  /** 重复领取返回 code=1001「这张券你已经领过了」，由调用方 catch 后提示 */
  claim: (id: number) => http.post<null>(`/api/coupons/${id}/claim`, undefined, { showError: false }),
  /** status: 0 未使用 / 1 已使用 / 2 已过期；shopId+amount 用于计算 usable/discount/reason */
  mine: (params?: { status?: number; shopId?: number; amount?: number }) =>
    http.get<MyCoupon[]>('/api/coupons/mine', params)
}

// ---------------- 评价 ----------------
export interface Review {
  id: number; orderId: number; shopId?: number; shopName?: string
  shopScore: number; riderScore?: number | null; content?: string; images?: string[]
  anonymous?: boolean; nickname?: string; avatar?: string | null
  reply?: string | null; status?: number; createdAt?: string
}
export interface ReviewCreateReq {
  orderId: number; shopScore: number; riderScore?: number | null
  content?: string; images?: string[]; anonymous?: boolean
}

export const reviewApi = {
  /** 只能对已完成订单评价；重复评价 code=2000「这单已经评价过了」，由调用方提示 */
  create: (data: ReviewCreateReq) => http.post<number>('/api/reviews', data, { showError: false }),
  /** 未评价时后端返回 code=404，属正常分支，这里静默处理 */
  byOrder: (orderId: number) =>
    http.get<Review>(`/api/reviews/order/${orderId}`, undefined, { showError: false }),
  mine: (params?: { page?: number; size?: number }) =>
    http.get<{ list: Review[]; total: number }>('/api/reviews/mine', params)
}

// ---------------- 售后 ----------------
export interface AfterSale {
  id: number; orderId: number; orderNo?: string; shopId?: number; shopName?: string
  type: number; typeText: string; reason: string; images?: string[]
  amount: number; payAmount?: number; status: number; statusText: string
  liableParty?: number | null; judgeRemark?: string | null; createdAt?: string
}
export interface AfterSaleCreateReq {
  orderId: number; type: number; reason: string; amount?: number; images?: string[]
}

export const afterSaleApi = {
  /** 仅已完成(50)订单可申请，成功后订单变售后中(70)；返回工单 id */
  create: (data: AfterSaleCreateReq) => http.post<number>('/api/after-sales', data),
  /** 外层是 {list,total,page,size}，不是裸数组；status: 0 待处理 / 1 处理中 / 2 已退款 / 3 已驳回 / 4 已关闭 */
  mine: (params?: { status?: number; page?: number; size?: number }) =>
    http.get<{ list: AfterSale[]; total: number; page?: number; size?: number }>('/api/after-sales/mine', params),
  /** 仅「待处理」可撤销 */
  cancel: (id: number) => http.post(`/api/after-sales/${id}/cancel`)
}

// ---------------- 意见反馈 ----------------
/** 我的反馈（GET /api/feedback/mine）：status 0 待处理 / 1 处理中 / 2 已处理 */
export interface Feedback {
  id: number; type: number; typeText: string; content: string
  /** 库里存的是 JSON 字符串，后端原样返回；解析函数见 parseFeedbackImages */
  images?: string | string[] | null
  contact?: string | null
  status: number; statusText: string
  reply?: string | null
  createdAt?: string; handledAt?: string | null
}
export interface FeedbackCreateReq {
  /** 1 意见建议 / 2 投诉 / 3 支付问题 / 4 其他 */
  type: number
  /** 最多 1000 字，后端校验 */
  content: string
  images?: string[]
  contact?: string
}

export const feedbackApi = {
  /** 提交反馈，返回反馈 id；内容为空/超 1000 字由后端返回业务错误文案 */
  submit: (data: FeedbackCreateReq) => http.post<number>('/api/feedback', data),
  /** 我的反馈列表（最近 50 条），images 需要前端解析 JSON 字符串 */
  mine: () => http.get<Feedback[]>('/api/feedback/mine', undefined, { showError: false })
}

/** 反馈图片：后端返回 JSON 字符串，这里统一转成数组（脏数据降级为空数组） */
export function parseFeedbackImages(v?: string | string[] | null): string[] {
  if (!v) return []
  if (Array.isArray(v)) return v.filter((x) => !!x)
  try {
    const arr = JSON.parse(v)
    return Array.isArray(arr) ? arr.filter((x: any) => !!x) : []
  } catch (e) {
    return []
  }
}

// ---------------- 订阅消息 ----------------
/** configured=false（本地/模拟环境）时前端不该调 requestSubscribeMessage */
export interface NotifyTemplate {
  key: string; title: string; templateId: string; configured: boolean
}

export const notifyApi = {
  templates: () => http.get<NotifyTemplate[]>('/api/notify/templates', undefined, { showError: false })
}

// ---------------- 收藏店铺 ----------------
export interface FavoriteShop {
  /** 店铺 id */
  id: number; name: string; logo?: string; categoryId?: number; campus?: number
  bizStatus: number; auditStatus?: number
  minAmount: number; packFee: number; deliveryFee: number
  score: number; monthSales: number; openTime?: string; favoritedAt?: string
}

export const favoriteApi = {
  list: () => http.get<FavoriteShop[]>('/api/favorites'),
  /** 未收藏返回 favorited=false；接口异常由页面降级为「未收藏」 */
  check: (shopId: number) =>
    http.get<{ shopId: number; favorited: boolean }>('/api/favorites/check', { shopId }, { showError: false }),
  /** 收藏 / 取消收藏同一个接口切换 */
  toggle: (shopId: number) =>
    http.post<{ shopId: number; favorited: boolean }>(`/api/favorites/${shopId}/toggle`, undefined, { showError: false }),
  /** 未收藏时后端返回 404「还没有收藏这家店」 */
  remove: (shopId: number) =>
    http.del(`/api/favorites/${shopId}`, undefined, { showError: false })
}

// ---------------- 商家端 ----------------
export interface MerchantShop {
  id: number; name: string; bizStatus: number; auditStatus: number; openTime: string
  minAmount: number; packFee: number; deliveryFee: number; commissionRate: number
  /** 店铺公告 */
  notice?: string | null
  /** 1 接收消息推送 / 0 关闭 */
  pushEnabled?: number | null
  /** 配送范围（米），null 表示不限 */
  deliveryRangeMeters?: number | null
}
export interface MerchantOrder {
  id: number; orderNo: string; status: number; statusText: string
  payAmount: number; shopIncome: number; address: string; remark?: string; createdAt: string
}
export interface MerchantProduct {
  id: number; name: string; price: number; stock: number; status: number
  description?: string; image?: string; categoryId?: number
  originPrice?: number | null
  hasSku?: number
  /** 后端实体直接返回，这里是 JSON 字符串：'[{"productId":1,"name":"黄焖鸡","qty":2}]' */
  comboItems?: string | null
  sort?: number
  /** 限时特价（分）：>0 且在 [specialStartAt, specialEndAt] 内才生效；0/null 表示关闭 */
  specialPrice?: number | null
  /** ISO 字符串，如 2026-10-25T18:00:00 */
  specialStartAt?: string | null
  specialEndAt?: string | null
}

/** 库存预警（GET /api/merchant/stock-alerts），stock 范围 0..threshold */
export interface StockAlert {
  productId: number; name: string; stock: number; status: number
  statusText: string; soldOut: boolean
}

/** 商家入驻状态：没店 applied=false；其余带店铺资料与审核状态 */
export interface MerchantStatus {
  applied: boolean
  shopId?: number; name?: string; logo?: string; categoryId?: number; campus?: number
  address?: string; contactPhone?: string
  auditStatus?: number; auditText?: string; rejectReason?: string | null
  bizStatus?: number; licenseUrl?: string; permitUrl?: string; photos?: string[]
}
/** 入驻申请：已驳回的店可重新提交，后端会清空驳回原因并回到待审核 */
export interface MerchantApplyReq {
  name: string; logo?: string; categoryId: number; campus: number; address: string
  lng?: number | null; lat?: number | null; contactPhone: string
  licenseUrl: string; permitUrl: string; photos?: string[]
}
export interface UploadResult { url: string; path: string; size: number }

// ---------------- 商家资金（钱包 / 营收 / 对账 / 提现） ----------------
export interface WalletFlow {
  id: number; bizType: string; bizNo?: string; direction: number
  amount: number; balanceAfter?: number; remark?: string; createdAt?: string
}
export interface MerchantWallet {
  shopId: number; balance: number; frozen: number
  totalIncome: number; todayIncome: number; monthIncome: number; minWithdraw: number
  flows: WalletFlow[]
}
export interface RevenueRange {
  orderCount: number; gmv: number; commission: number; income: number; avgPrice: number
}
export interface MerchantSummary {
  today: RevenueRange; week: RevenueRange; month: RevenueRange; settleFreezeDays: number
}
export interface StatementRow {
  id: number; orderId: number; orderNo?: string; goodsAmount: number; packFee: number
  commission: number; shopIncome: number; status: number; statusText: string
  createdAt?: string; settleAt?: string | null
}
export interface StatementSummary {
  gmv: number; commission: number; income: number
  settledIncome: number; pendingIncome: number; from?: string; to?: string
}
export interface StatementPage {
  summary: StatementSummary
  page: { list: StatementRow[]; total: number; page?: number; size?: number }
}
/** 商家自建店铺券（scope=2，仅本店可用） */
export interface MerchantCoupon {
  id: number; name: string; type: number; threshold: number; amount: number
  desc?: string; scope?: number; shopId?: number
  startAt?: string; endAt?: string
  remain?: number; claimed?: number; used?: number
  status: number
}

export interface Withdrawal {
  id: number; amount: number; status: number; statusText: string; channel?: string
  failReason?: string | null; appliedAt?: string; auditedAt?: string; paidAt?: string
}

export const merchantApi = {
  /** 入驻状态：没店 / 待审核 / 已驳回（带原因）/ 已通过 */
  status: () => http.get<MerchantStatus>('/api/merchant/status'),
  apply: (data: MerchantApplyReq) =>
    http.post<{ shopId: number; auditStatus: number }>('/api/merchant/apply', data),
  shop: () => http.get<MerchantShop>('/api/merchant/shop'),
  updateShop: (data: Record<string, any>) => http.put('/api/merchant/shop', data),
  orders: (status?: number) => http.get<{ list: MerchantOrder[]; total: number }>('/api/merchant/orders', { status, page: 1, size: 50 }),
  accept: (id: number) => http.post(`/api/merchant/orders/${id}/accept`),
  reject: (id: number, reason: string) => http.post(`/api/merchant/orders/${id}/reject`, { reason }),
  ready: (id: number) => http.post(`/api/merchant/orders/${id}/ready`),
  products: () => http.get<MerchantProduct[]>('/api/merchant/products', { includeOffline: true }),
  /** 库存预警：返回库存 0..threshold 的在售/已下架菜品，soldOut=true 表示已售罄 */
  stockAlerts: (threshold = 5) =>
    http.get<StockAlert[]>('/api/merchant/stock-alerts', { threshold }, { showError: false }),
  createProduct: (data: Partial<MerchantProduct>) => http.post<MerchantProduct>('/api/merchant/products', data),
  updateProduct: (id: number, data: Partial<MerchantProduct>) => http.put<MerchantProduct>(`/api/merchant/products/${id}`, data),
  /** 规格 SKU：新增/删除会自动维护商品的 hasSku 与展示价（取最低规格价） */
  skus: (productId: number) => http.get<ProductSku[]>(`/api/merchant/products/${productId}/skus`),
  createSku: (productId: number, data: { name: string; price: number; stock: number; status?: number }) =>
    http.post<ProductSku>(`/api/merchant/products/${productId}/skus`, data),
  updateSku: (skuId: number, data: Partial<{ name: string; price: number; stock: number; status: number }>) =>
    http.put<ProductSku>(`/api/merchant/skus/${skuId}`, data),
  deleteSku: (skuId: number) => http.del(`/api/merchant/skus/${skuId}`),

  // ---- 资金 ----
  wallet: () => http.get<MerchantWallet>('/api/merchant/wallet'),
  summary: () => http.get<MerchantSummary>('/api/merchant/summary'),
  /** from/to 传 YYYY-MM-DD；返回 {summary, page:{list,total,page,size}} */
  statements: (params?: { from?: string; to?: string; page?: number; size?: number }) =>
    http.get<StatementPage>('/api/merchant/statements', params),
  withdrawals: () => http.get<Withdrawal[]>('/api/merchant/withdrawals'),
  /** amount 单位分，后端校验最低提现与余额 */
  applyWithdraw: (amount: number) => http.post<number>('/api/merchant/withdrawals', { amount }),

  // ---- 店铺券（满减/折扣，仅本店可用） ----
  coupons: () => http.get<MerchantCoupon[]>('/api/merchant/coupons'),
  /** type 1=满减(amount 为减免金额分)，2=折扣(amount 为千分比，880=8.8折)；startAt/endAt 传 ISO */
  createCoupon: (data: {
    name: string; type: number; threshold: number; amount: number
    total: number; startAt: string; endAt: string
  }) => http.post<number>('/api/merchant/coupons', data),
  toggleCoupon: (id: number, status: number) =>
    http.put(`/api/merchant/coupons/${id}/status`, { status })
}

/**
 * 上传接口按 uni.uploadFile 走，复用一个 base（与 utils/request.ts 的 BASE_URL 保持一致）：
 * 不配 VITE_API_BASE 时同样走「同源相对路径」，由 serve.mjs 把 /api 代理到后端，
 * 避免 H5 里请求打到相对路径、上传却打到写死的 8080 端口。
 */
const UPLOAD_BASE = (import.meta as any).env?.VITE_API_BASE || ''

/**
 * 图片上传：uni.uploadFile 不是 XHR，得单独封装。
 * 后端要求 multipart field 名为 file，且要带 Bearer token；返回 {url,path,size}，url 是可直接渲染的绝对地址。
 */
export function uploadImage(filePath: string): Promise<UploadResult> {
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: UPLOAD_BASE + '/api/upload',
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${getToken()}` },
      success: (res: any) => {
        let body: any = null
        try {
          body = typeof res.data === 'string' ? JSON.parse(res.data) : res.data
        } catch (e) {
          reject(new Error('上传响应解析失败'))
          return
        }
        if (body && body.code === 0 && body.data?.url) resolve(body.data as UploadResult)
        else reject(new Error(body?.msg || `上传失败(${res.statusCode})`))
      },
      fail: (err: any) => reject(new Error(err?.errMsg || '上传失败，请重试'))
    })
  })
}

/**
 * 商家端准入守卫：没店或审核未通过时 reLaunch 到入驻页，避免命中「还没有店铺」404 弹窗。
 * 返回 true = 可以继续加载商家端数据；状态查询失败时放行，避免网络抖动把人踢出商家端。
 */
export async function ensureMerchantApproved(): Promise<boolean> {
  try {
    const st = await merchantApi.status()
    if (st?.applied && st.auditStatus === 1) return true
  } catch (e) {
    return true
  }
  uni.reLaunch({ url: '/pages/merchant/apply' })
  return false
}

// ---------------- 骑手端 ----------------
/** 骑手入驻状态：没提交 applied=false；其余带审核状态、证件图、协议与保单信息 */
export interface RiderStatus {
  applied: boolean
  riderId?: number; auditStatus?: number; auditText?: string; rejectReason?: string | null
  phoneMask?: string; score?: number; totalOrders?: number
  /** 驳回重提时展示的脱敏信息（后端不返回明文） */
  realNameMask?: string; idCardMask?: string; stuNoMask?: string
  workStatus?: number; maxConcurrent?: number
  idCardFront?: string; idCardBack?: string; studentCard?: string
  agreementSignedAt?: string; insuranceNo?: string | null
}
/** 骑手中心资料（GET /api/rider/profile，PUT /api/rider/settings 也返回同一结构） */
export interface RiderProfile {
  id: number; auditStatus: number; workStatus: number
  score?: number; totalOrders?: number; maxConcurrent?: number
  workTime?: string | null; phoneMask?: string
}

export interface RiderApplyReq {
  /** 首次提交必填；驳回后重提可留空，后端沿用原值 */
  realName?: string; idCard?: string; stuNo?: string; phone?: string
  idCardFront?: string; idCardBack?: string; studentCard?: string
  agreementUrl?: string; agreeProtocol: boolean; insuranceNo?: string
}

export interface RiderPoolItem {
  orderId: number; orderNo: string; shopName: string; deliveryFee: number
  riderIncome: number; address: string; distance?: number; readyAt?: string
  shopLng?: number; shopLat?: number
}

/** 系统派给我的单（高峰时段派单，需在 expireSeconds 内确认） */
export interface RiderDispatchedOrder {
  orderId: number; orderNo: string; shopName: string
  shopLng?: number; shopLat?: number
  address?: string
  riderIncome: number; dispatchedAt?: string; expireSeconds: number
}

/** 骑手「我的配送」订单（status 30 待取餐 / 40 配送中 / 50 已完成） */
export interface RiderOrder {
  orderId: number; orderNo: string; status: number; statusText: string
  shopId?: number; shopName: string; shopLng?: number; shopLat?: number
  /** 收货地址 JSON 字符串 */
  address?: string
  /** 收货点坐标（可能为空） */
  lng?: number; lat?: number
  riderIncome?: number; pickedAt?: string; deliveredAt?: string
  /** 订单备注（后端未返回时页面从 address JSON.remark 兜底） */
  remark?: string
  /** 该单已上报异常条数 */
  incident?: number
}

// ---------------- 骑手资金 ----------------
export interface RiderWalletFlow {
  id: number; bizType: string; bizNo?: string; direction: number
  amount: number; balanceAfter?: number; remark?: string; createdAt?: string
}
export interface RiderWallet {
  riderId: number; balance: number; frozen: number
  totalIncome: number; todayIncome: number; monthIncome: number
  totalPenalty: number; minWithdraw: number
  score?: number; totalOrders?: number; timeoutOrders?: number
  flows: RiderWalletFlow[]
}
export interface RiderRangeStats {
  orderCount: number; income: number; avgIncome: number
}
export interface RiderSummary {
  today: RiderRangeStats; week: RiderRangeStats; month: RiderRangeStats
  settleFreezeDays?: number
}
export interface RiderSettlement {
  id: number; orderId: number; orderNo?: string; goodsAmount: number
  riderIncome: number; status: number; statusText: string
  createdAt?: string; settleAt?: string
}
export interface RiderSettlementPage {
  summary: { orderCount: number; pageIncome: number; pendingIncome: number }
  list: RiderSettlement[]; total: number; page?: number; size?: number
}
export interface RiderPenalty {
  id: number; riderId?: number; orderId?: number | null; orderNo?: string
  type: number; typeText: string; amount: number; remark?: string; createdAt?: string
}

/** 配送异常上报记录 */
export interface RiderIncident {
  id: number; orderId: number; orderNo?: string; shopName?: string
  type: number; typeText: string; remark?: string; images?: string[]
  status: number; statusText: string; handleRemark?: string | null; createdAt?: string
}

export const riderApi = {
  /** 入驻状态：没提交 / 待审核 / 已通过 / 已驳回（带原因） */
  status: () => http.get<RiderStatus>('/api/rider/status'),
  /** 提交/重新提交入驻申请；后端校验身份证/学号/手机/三张图/协议勾选 */
  apply: (data: RiderApplyReq) =>
    http.post<{ riderId: number; auditStatus: number }>('/api/rider/apply', data),
  profile: () => http.get<RiderProfile>('/api/rider/profile'),
  /** 接单设置：开关接单 / 同时接单上限 1~10 / 可接单时段 */
  updateSettings: (data: { workStatus?: number; maxConcurrent?: number; workTime?: string }) =>
    http.put<RiderProfile>('/api/rider/settings', data),
  pool: (lng?: number, lat?: number) => http.get<RiderPoolItem[]>('/api/rider/pool', { lng, lat }),
  /** 系统派给我的单（需骑手确认） */
  dispatched: () => http.get<RiderDispatchedOrder[]>('/api/rider/dispatched'),
  acceptDispatch: (orderId: number) => http.post(`/api/rider/dispatched/${orderId}/accept`),
  rejectDispatch: (orderId: number) => http.post(`/api/rider/dispatched/${orderId}/reject`),
  /** status: 30 待取餐 / 40 配送中 / 50 已完成；不传返回全部 */
  orders: (status?: number) => http.get<RiderOrder[]>('/api/rider/orders', { status }),
  /** 配送异常上报：type 1 联系不上用户 / 2 餐品撒漏 / 3 地址错误 / 4 其他 */
  reportIncident: (orderId: number, data: { type: number; remark: string; images?: string[] }) =>
    http.post<number>(`/api/rider/orders/${orderId}/incident`, data),
  incidents: () => http.get<RiderIncident[]>('/api/rider/incidents'),
  grab: (id: number) => http.post(`/api/rider/orders/${id}/grab`),
  /** 取消配送：仅 30 待取餐可取消，订单退回抢单大厅；40 需走异常上报 */
  cancel: (id: number) => http.post(`/api/rider/orders/${id}/cancel`),
  pickup: (id: number, photo?: string) => http.post(`/api/rider/orders/${id}/pickup`, { photo }),
  deliver: (id: number, photo?: string) => http.post(`/api/rider/orders/${id}/deliver`, { photo }),
  location: (lng: number, lat: number, orderId?: number) => http.post('/api/rider/location', { lng, lat, accuracy: 15, orderId }),

  // ---- 资金 ----
  wallet: () => http.get<RiderWallet>('/api/rider/wallet'),
  summary: () => http.get<RiderSummary>('/api/rider/summary'),
  /** 佣金明细（按单分页）+ 账单汇总 */
  settlements: (params?: { page?: number; size?: number }) =>
    http.get<RiderSettlementPage>('/api/rider/settlements', params),
  withdrawals: () => http.get<Withdrawal[]>('/api/rider/withdrawals'),
  /** amount 单位分；后端校验最低提现与余额 */
  applyWithdraw: (amount: number) => http.post<number>('/api/rider/withdrawals', { amount }),
  penalties: () => http.get<RiderPenalty[]>('/api/rider/penalties')
}

/**
 * 骑手端准入守卫：没提交或审核未通过时 reLaunch 到入驻页，
 * 避免直接打 /api/rider/pool 等接口报「还没有通过审核」。
 * 状态查询失败时放行，避免网络抖动把人踢出骑手端。
 */
export async function ensureRiderApproved(): Promise<boolean> {
  try {
    const st = await riderApi.status()
    if (st?.applied && st.auditStatus === 1) return true
  } catch (e) {
    return true
  }
  uni.reLaunch({ url: '/pages/rider/apply' })
  return false
}

// ---------------- 校内身份核验 ----------------
export interface IdentityStatus {
  required: boolean
  verified?: boolean
  stuNoMask?: string | null
  /** 平台功能开关：地址文本解析（GET /api/identity/config） */
  addressParse?: boolean
  /** 平台功能开关：围栏校验 */
  fenceEnforce?: boolean
}

/** 平台公告：公开接口，失败时页面显示「暂无公告」 */
export const announcementApi = {
  list: () => http.get<Announcement[]>('/api/announcements', undefined, { showError: false })
}

export const identityApi = {
  /** 平台是否开启学号/工号核验（公开接口，登录页用它决定是否显示输入框） */
  config: () => http.get<IdentityStatus>('/api/identity/config'),
  status: () => http.get<IdentityStatus>('/api/identity/status'),
  verify: (stuNo: string) => http.post<{ verified: boolean; stuNoMask: string }>('/api/identity/verify', { stuNo })
}
