import { http } from '../utils/request'

export interface MerchantShop {
  id: number; name: string; bizStatus: number; auditStatus: number; openTime: string
  minAmount: number; packFee: number; deliveryFee: number; commissionRate: number
}
export interface MerchantOrder {
  id: number; orderNo: string; status: number; statusText: string
  payAmount: number; shopIncome: number; address: string; remark?: string; createdAt: string
}
export interface Product {
  id: number; name: string; price: number; stock: number; status: number
  description?: string; image?: string; categoryId?: number
}
export interface RiderPoolItem {
  orderId: number; orderNo: string; shopName: string; deliveryFee: number
  riderIncome: number; address: string; distance?: number; readyAt?: string
}

export const merchantApi = {
  shop: () => http.get<MerchantShop>('/api/merchant/shop'),
  updateShop: (data: Record<string, any>) => http.put('/api/merchant/shop', data),
  orders: (status?: number) => http.get<{ list: MerchantOrder[]; total: number }>('/api/merchant/orders', { status, page: 1, size: 50 }),
  accept: (id: number) => http.post(`/api/merchant/orders/${id}/accept`),
  reject: (id: number, reason: string) => http.post(`/api/merchant/orders/${id}/reject`, { reason }),
  ready: (id: number) => http.post(`/api/merchant/orders/${id}/ready`),
  products: () => http.get<Product[]>('/api/merchant/products', { includeOffline: true }),
  createProduct: (data: Partial<Product>) => http.post<Product>('/api/merchant/products', data),
  updateProduct: (id: number, data: Partial<Product>) => http.put<Product>(`/api/merchant/products/${id}`, data)
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
