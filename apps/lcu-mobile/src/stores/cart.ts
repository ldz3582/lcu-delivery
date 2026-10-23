import { defineStore } from 'pinia'

export interface CartItem {
  productId: number
  name: string
  price: number
  image?: string
  qty: number
}

export const useCartStore = defineStore('cart', {
  state: () => ({
    shopId: 0,
    shopName: '',
    items: [] as CartItem[]
  }),
  getters: {
    count: (s) => s.items.reduce((n, i) => n + i.qty, 0),
    goodsAmount: (s) => s.items.reduce((n, i) => n + i.price * i.qty, 0)
  },
  actions: {
    add(shopId: number, shopName: string, product: { id: number; name: string; price: number; image?: string }) {
      if (this.shopId && this.shopId !== shopId) {
        this.items = []
      }
      this.shopId = shopId
      this.shopName = shopName
      const hit = this.items.find((i) => i.productId === product.id)
      if (hit) {
        hit.qty += 1
      } else {
        this.items.push({ productId: product.id, name: product.name, price: product.price, image: product.image, qty: 1 })
      }
    },
    inc(productId: number) {
      const hit = this.items.find((i) => i.productId === productId)
      if (hit) hit.qty += 1
    },
    dec(productId: number) {
      const idx = this.items.findIndex((i) => i.productId === productId)
      if (idx >= 0) {
        this.items[idx].qty -= 1
        if (this.items[idx].qty <= 0) this.items.splice(idx, 1)
      }
      if (!this.items.length) this.clear()
    },
    clear() {
      this.items = []
      this.shopId = 0
      this.shopName = ''
    }
  }
})
