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
