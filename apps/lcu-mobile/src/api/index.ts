import { http } from '../utils/request'

export interface Category { id: number; name: string; icon?: string }
export interface ShopCard {
  id: number; name: string; logo?: string; categoryId: number; campus: number
  bizStatus: number; minAmount: number; packFee: number; deliveryFee: number
  score: number; monthSales: number; openTime?: string; distance?: number
}
export interface Product {
  id: number; name: string; image?: string; description?: string
  price: number; originPrice?: number; stock: number; sales?: number
}
export interface ShopDetail extends ShopCard {
  address: string; lng: number; lat: number; contactPhone?: string; products: Product[]
}
export interface Address {
  id: number; campus?: number; detail: string; building?: string; floor?: string
  room?: string; remark?: string; lng?: number; lat?: number; inFence?: number; isDefault?: number
}
export interface OrderCard {
  id: number; orderNo: string; shopId: number; status: number; statusText: string
  payAmount: number; goodsAmount: number; deliveryFee: number; createdAt: string
}
export interface Bill {
  goodsAmount: number; packFee: number; deliveryFee: number; discountAmount: number
  totalAmount: number; minAmount: number; reachMin: boolean; shopName: string
}

// ---------------- 用户端 ----------------
export const api = {
  categories: () => http.get<Category[]>('/api/categories'),
  shops: (params: Record<string, any>) => http.get<{ list: ShopCard[]; total: number }>('/api/shops', params),
  shop: (id: number) => http.get<ShopDetail>(`/api/shops/${id}`),
  calculate: (shopId: number, items: { productId: number; qty: number }[]) =>
    http.post<Bill>('/api/cart/calculate', { shopId, items }),

  addresses: () => http.get<Address[]>('/api/addresses'),
  createAddress: (data: Partial<Address>) => http.post<Address>('/api/addresses', data),
  updateAddress: (id: number, data: Partial<Address>) => http.put<Address>(`/api/addresses/${id}`, data),
  deleteAddress: (id: number) => http.del(`/api/addresses/${id}`),
  parseAddress: (text: string, lng?: number, lat?: number) =>
    http.post<any>('/api/addresses/parse', { text, lng, lat }),

  createOrder: (data: any) => http.post<{ orderId: number; orderNo: string; payAmount: number }>('/api/orders', data),
  orders: (status?: number) => http.get<{ list: OrderCard[]; total: number }>('/api/orders', { status, page: 1, size: 20 }),
  order: (id: number) => http.get<any>(`/api/orders/${id}`),
  cancelOrder: (id: number, reason = '') => http.post(`/api/orders/${id}/cancel`, { reason }),
  confirmOrder: (id: number) => http.post(`/api/orders/${id}/confirm`),

  prepay: (orderId: number) => http.post<any>('/api/pay/prepay', { orderId }),
  mockPay: (orderId: number) => http.post<any>('/api/pay/mock-pay', { orderId }),

  profile: () => http.get<any>('/api/user/profile')
}

// ---------------- 商家端 ----------------
export interface MerchantShop {
  id: number; name: string; bizStatus: number; auditStatus: number; openTime: string
  minAmount: number; packFee: number; deliveryFee: number; commissionRate: number
}
export interface MerchantOrder {
  id: number; orderNo: string; status: number; statusText: string
  payAmount: number; shopIncome: number; address: string; remark?: string; createdAt: string
}
export interface MerchantProduct {
  id: number; name: string; price: number; stock: number; status: number
  description?: string; image?: string; categoryId?: number
}

export const merchantApi = {
  shop: () => http.get<MerchantShop>('/api/merchant/shop'),
  updateShop: (data: Record<string, any>) => http.put('/api/merchant/shop', data),
  orders: (status?: number) => http.get<{ list: MerchantOrder[]; total: number }>('/api/merchant/orders', { status, page: 1, size: 50 }),
  accept: (id: number) => http.post(`/api/merchant/orders/${id}/accept`),
  reject: (id: number, reason: string) => http.post(`/api/merchant/orders/${id}/reject`, { reason }),
  ready: (id: number) => http.post(`/api/merchant/orders/${id}/ready`),
  products: () => http.get<MerchantProduct[]>('/api/merchant/products', { includeOffline: true }),
  createProduct: (data: Partial<MerchantProduct>) => http.post<MerchantProduct>('/api/merchant/products', data),
  updateProduct: (id: number, data: Partial<MerchantProduct>) => http.put<MerchantProduct>(`/api/merchant/products/${id}`, data)
}

// ---------------- 骑手端 ----------------
export interface RiderPoolItem {
  orderId: number; orderNo: string; shopName: string; deliveryFee: number
  riderIncome: number; address: string; distance?: number; readyAt?: string
}

export const riderApi = {
  profile: () => http.get<any>('/api/rider/profile'),
  pool: (lng?: number, lat?: number) => http.get<RiderPoolItem[]>('/api/rider/pool', { lng, lat }),
  orders: () => http.get<any[]>('/api/rider/orders'),
  grab: (id: number) => http.post(`/api/rider/orders/${id}/grab`),
  pickup: (id: number, photo?: string) => http.post(`/api/rider/orders/${id}/pickup`, { photo }),
  deliver: (id: number, photo?: string) => http.post(`/api/rider/orders/${id}/deliver`, { photo }),
  location: (lng: number, lat: number, orderId?: number) => http.post('/api/rider/location', { lng, lat, accuracy: 15, orderId })
}

// ---------------- 管理员端 ----------------
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

export interface AdminShop {
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

export interface AdminOrder {
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

export const adminApi = {
  overview: () => http.get<Overview>('/api/admin/overview'),
  shops: (params: { auditStatus?: number; page: number; size: number }) =>
    http.get<{ list: AdminShop[]; total: number }>('/api/admin/shops', params),
  auditShop: (id: number, pass: boolean, reason = '') =>
    http.post(`/api/admin/shops/${id}/audit`, { pass, reason }),
  riders: (params: { auditStatus?: number; page: number; size: number }) =>
    http.get<{ list: any[]; total: number }>('/api/admin/riders', params),
  orders: (params: { status?: number; keyword?: string; page: number; size: number }) =>
    http.get<{ list: AdminOrder[]; total: number }>('/api/admin/orders', params)
}

// ---------------- 校内身份核验 ----------------
export interface IdentityStatus {
  required: boolean
  verified?: boolean
  stuNoMask?: string | null
}

export const identityApi = {
  /** 平台是否开启学号/工号核验（公开接口，登录页用它决定是否显示输入框） */
  config: () => http.get<IdentityStatus>('/api/identity/config'),
  status: () => http.get<IdentityStatus>('/api/identity/status'),
  verify: (stuNo: string) => http.post<{ verified: boolean; stuNoMask: string }>('/api/identity/verify', { stuNo })
}
