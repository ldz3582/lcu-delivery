<template>
  <view class="page">
    <view v-if="shop" class="head">
      <view class="title">{{ shop.name }}</view>
      <view class="muted">{{ shop.address }}</view>
      <view class="muted">起送 {{ fen(shop.minAmount) }} · 配送 {{ fen(shop.deliveryFee) }} · 营业 {{ shop.openTime }}</view>
    </view>

    <view class="products">
      <view v-for="p in shop?.products || []" :key="p.id" class="card item">
        <view class="thumb">{{ p.name.slice(0, 1) }}</view>
        <view class="info">
          <view class="name">{{ p.name }}</view>
          <view class="muted">{{ p.description }}</view>
          <view class="between">
            <text class="price">{{ fen(p.price) }}</text>
            <view class="add" @click="add(p)">+</view>
          </view>
        </view>
      </view>
    </view>

    <view class="bar" v-if="cart.count">
      <view class="bar-left">
        <text class="badge">{{ cart.count }}</text>
        <text class="price">{{ (cart.goodsAmount / 100).toFixed(2) }} 元</text>
      </view>
      <view class="bar-btn" @click="toCart">去结算</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api, type ShopDetail } from '../../api'
import { useCartStore } from '../../stores/cart'

const shop = ref<ShopDetail | null>(null)
const cart = useCartStore()

function fen(v: number) { return (v / 100).toFixed(2) + ' 元' }

onLoad(async (q: any) => {
  const id = Number(q?.id || 1)
  shop.value = await api.shop(id)
  uni.setNavigationBarTitle({ title: shop.value.name })
})

function add(p: any) {
  if (!shop.value) return
  if (shop.value.bizStatus !== 1) {
    uni.showToast({ title: '商家休息中', icon: 'none' })
    return
  }
  cart.add(shop.value.id, shop.value.name, p)
}

function toCart() {
  uni.reLaunch({ url: '/pages/cart/cart' })
}
</script>

<style scoped>
.page { padding-bottom: 140rpx; }
.head { background: #fff; padding: 28rpx; }
.title { font-size: 38rpx; font-weight: 700; margin-bottom: 10rpx; }
.products { padding: 20rpx; }
.item { display: flex; }
.thumb {
  width: 110rpx; height: 110rpx; border-radius: 16rpx; background: #f3f4f6;
  display: flex; align-items: center; justify-content: center; color: #9ca3af; margin-right: 20rpx;
}
.info { flex: 1; }
.name { font-weight: 600; font-size: 30rpx; }
.add {
  width: 52rpx; height: 52rpx; border-radius: 50%; background: #c2410c; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 34rpx;
}
.bar {
  position: fixed; left: 0; right: 0; bottom: 0; background: #1f2328; color: #fff;
  display: flex; align-items: center; justify-content: space-between; padding: 20rpx 28rpx;
}
.bar-left { display: flex; align-items: center; }
.badge {
  background: #c2410c; border-radius: 50%; width: 40rpx; height: 40rpx;
  display: inline-flex; align-items: center; justify-content: center; font-size: 22rpx; margin-right: 12rpx;
}
.bar-btn { background: #c2410c; padding: 16rpx 40rpx; border-radius: 40rpx; }
</style>
