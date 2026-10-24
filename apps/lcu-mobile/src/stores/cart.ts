import { defineStore } from 'pinia'

export interface CartItem {
  productId: number
  name: string
  price: number
  image?: string
  qty: number
  /** 规格：同一商品的不同规格各占一行，只有 productId + skuId 都相同才合并数量 */
  skuId?: number
  skuName?: string
}

export interface CartSku {
  id: number
  name: string
  price: number
}

/** 加购行的唯一键：有规格按规格区分 */
function sameLine(i: CartItem, productId: number, skuId?: number) {
  return i.productId === productId && (i.skuId || 0) === (skuId || 0)
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
    /** 带规格加购用规格价展示；最终金额仍以后端 calculate 为准 */
    add(
      shopId: number,
      shopName: string,
      product: { id: number; name: string; price: number; image?: string },
      sku?: CartSku
    ) {
      if (this.shopId && this.shopId !== shopId) {
        this.items = []
      }
      this.shopId = shopId
      this.shopName = shopName
      const hit = this.items.find((i) => sameLine(i, product.id, sku?.id))
      if (hit) {
        hit.qty += 1
      } else {
        this.items.push({
          productId: product.id,
          name: product.name,
          price: sku ? sku.price : product.price,
          image: product.image,
          qty: 1,
          skuId: sku?.id,
          skuName: sku?.name
        })
      }
    },
    inc(productId: number, skuId?: number) {
      const hit = this.items.find((i) => sameLine(i, productId, skuId))
      if (hit) hit.qty += 1
    },
    dec(productId: number, skuId?: number) {
      const idx = this.items.findIndex((i) => sameLine(i, productId, skuId))
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
