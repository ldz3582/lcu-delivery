<template>
  <view class="page">
    <view v-if="!cart.items.length" class="empty">
      <view>购物车还是空的</view>
      <view class="go" @click="goHome">去逛逛</view>
    </view>

    <view v-else>
      <view class="card">
        <view class="between">
          <text class="shop-name">{{ cart.shopName }}</text>
          <text class="muted" @click="cart.clear()">清空</text>
        </view>
        <view v-for="i in cart.items" :key="i.productId" class="line">
          <view class="lname">{{ i.name }}</view>
          <view class="qty">
            <text class="op" @click="cart.dec(i.productId)">-</text>
            <text class="num">{{ i.qty }}</text>
            <text class="op" @click="cart.inc(i.productId)">+</text>
          </view>
          <view class="price">{{ ((i.price * i.qty) / 100).toFixed(2) }}</view>
        </view>
      </view>

      <view class="card" @click="chooseAddress">
        <view class="between">
          <text class="label">收货地址</text>
          <text class="muted">{{ address ? address.detail : '点击选择' }} ›</text>
        </view>
        <view v-if="address && !address.inFence" class="warn">该地址可能超出校园配送范围</view>
      </view>

      <view class="card">
        <view class="between row-line"><text>商品</text><text>{{ fen(bill?.goodsAmount ?? cart.goodsAmount) }}</text></view>
        <view class="between row-line"><text>打包费</text><text>{{ fen(bill?.packFee ?? 0) }}</text></view>
        <view class="between row-line"><text>配送费</text><text>{{ fen(bill?.deliveryFee ?? 0) }}</text></view>
        <view class="between total"><text>合计</text><text class="price">{{ fen(bill?.totalAmount ?? cart.goodsAmount) }}</text></view>
        <view v-if="bill && !bill.reachMin" class="warn">还差 {{ ((bill.minAmount - bill.goodsAmount) / 100).toFixed(2) }} 元起送</view>
      </view>

      <view class="card">
        <input v-model="remark" placeholder="备注：少放辣、放门口……" />
      </view>

      <view class="submit" @click="submit">提交订单</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api, type Address, type Bill } from '../../api'
import { useCartStore } from '../../stores/cart'

const cart = useCartStore()
const bill = ref<Bill | null>(null)
const address = ref<Address | null>(null)
const remark = ref('')
const submitting = ref(false)

function fen(v: number) { return (v / 100).toFixed(2) + ' 元' }
function goHome() { uni.switchTab({ url: '/pages/index/index' }) }

async function refresh() {
  if (!cart.items.length) { bill.value = null; return }
  bill.value = await api.calculate(cart.shopId, cart.items.map((i) => ({ productId: i.productId, qty: i.qty })))
}

async function loadAddress() {
  const list = await api.addresses()
  address.value = list.find((a) => a.isDefault === 1) || list[0] || null
}

function chooseAddress() {
  uni.navigateTo({ url: '/pages/address/list?pick=1' })
}

async function submit() {
  if (submitting.value) return
  if (!address.value) {
    uni.showToast({ title: '请先选择收货地址', icon: 'none' })
    return
  }
  if (bill.value && !bill.value.reachMin) {
    uni.showToast({ title: '未达起送价', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    const order = await api.createOrder({
      shopId: cart.shopId,
      addressId: address.value.id,
      items: cart.items.map((i) => ({ productId: i.productId, qty: i.qty })),
      remark: remark.value
    })
    cart.clear()
    const prepay = await api.prepay(order.orderId)
    if (prepay.mock) {
      const r = await new Promise<boolean>((resolve) => {
        uni.showModal({
          title: '模拟支付',
          content: `本地开发环境不产生真实扣款，确认支付 ${(order.payAmount / 100).toFixed(2)} 元？`,
          success: (res) => resolve(res.confirm)
        })
      })
      if (r) await api.mockPay(order.orderId)
    }
    uni.redirectTo({ url: `/pages/order/detail?id=${order.orderId}` })
  } catch (e) {
    console.warn(e)
  } finally {
    submitting.value = false
  }
}

onShow(async () => {
  await loadAddress()
  refresh()
})
</script>

<style scoped>
.page { padding: 20rpx; }
.empty { text-align: center; padding: 160rpx 0; color: #9ca3af; }
.go { margin-top: 30rpx; color: #c2410c; }
.shop-name { font-weight: 600; font-size: 30rpx; }
.line { display: flex; align-items: center; margin-top: 24rpx; }
.lname { flex: 1; }
.qty { display: flex; align-items: center; }
.op {
  width: 44rpx; height: 44rpx; border-radius: 50%; background: #f3f4f6;
  display: flex; align-items: center; justify-content: center;
}
.num { width: 60rpx; text-align: center; }
.price { width: 110rpx; text-align: right; color: #c2410c; }
.label { font-size: 28rpx; }
.row-line { margin-bottom: 8rpx; color: #4b5563; }
.total { margin-top: 12rpx; font-weight: 600; }
.warn { color: #b45309; font-size: 24rpx; margin-top: 10rpx; }
.submit {
  margin: 30rpx 10rpx 60rpx;
  background: #c2410c; color: #fff; text-align: center;
  padding: 24rpx; border-radius: 44rpx; font-size: 32rpx;
}
</style>
