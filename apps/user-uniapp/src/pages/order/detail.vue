<template>
  <view class="page" v-if="order">
    <view class="card status-card">
      <view class="big">{{ order.statusText }}</view>
      <view class="muted mt8">订单号 {{ order.orderNo }}</view>
    </view>

    <view class="card">
      <view class="between"><text class="sec">收货信息</text></view>
      <view class="muted mt8">{{ addressText }}</view>
      <view v-if="order.remark" class="muted mt8">备注：{{ order.remark }}</view>
    </view>

    <view class="card">
      <view class="sec">商品</view>
      <view v-for="it in order.items" :key="it.id" class="line">
        <text class="lname">{{ it.name }} × {{ it.qty }}</text>
        <text class="price">{{ ((it.price * it.qty) / 100).toFixed(2) }}</text>
      </view>
      <view class="between mt">
        <text class="muted">配送费</text>
        <text class="muted">{{ ((order.deliveryFee || 0) / 100).toFixed(2) }}</text>
      </view>
      <view class="between total">
        <text>实付</text>
        <text class="price">{{ (order.payAmount / 100).toFixed(2) }} 元</text>
      </view>
    </view>

    <view class="actions">
      <view v-if="order.status === 0" class="btn-primary" @click="pay">去支付</view>
      <view v-if="order.status === 0 || order.status === 10" class="btn-ghost mt12" @click="cancel">取消订单</view>
      <view v-if="order.status === 40" class="btn-primary" @click="confirm">确认收货</view>
      <view v-if="order.status === 50" class="btn-ghost mt12" @click="goList">再来一单</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api } from '../../api'
import { ensureLogin } from '../../utils/auth'

const order = ref<any>(null)
let orderId = 0

const addressText = computed(() => {
  try {
    const a = JSON.parse(order.value?.address || '{}')
    return [a.campus === 1 ? '东校区' : a.campus === 2 ? '西校区' : '', a.detail].filter(Boolean).join(' ')
  } catch {
    return ''
  }
})

async function load() {
  order.value = await api.order(orderId)
}

async function pay() {
  await api.prepay(orderId)
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '模拟支付',
      content: `确认支付 ${(order.value.payAmount / 100).toFixed(2)} 元？`,
      success: (r) => resolve(r.confirm)
    })
  })
  if (ok) {
    await api.mockPay(orderId)
    uni.showToast({ title: '支付成功' })
    load()
  }
}

async function cancel() {
  const r = await new Promise<boolean>((resolve) => {
    uni.showModal({ title: '取消订单', content: '确认取消这一单吗？', success: (x) => resolve(x.confirm) })
  })
  if (r) {
    await api.cancelOrder(orderId, '用户取消')
    load()
  }
}

async function confirm() {
  await api.confirmOrder(orderId)
  uni.showToast({ title: '已确认收货' })
  load()
}

function goList() {
  uni.switchTab({ url: '/pages/order/list' })
}

onLoad((q: any) => {
  orderId = Number(q?.id)
})

onShow(async () => {
  if (orderId) {
    await ensureLogin()
    load()
  }
})
</script>

<style scoped>
.page { padding: 20rpx; }
.status-card { background: linear-gradient(135deg, #c2410c, #ea580c); color: #fff; }
.big { font-size: 40rpx; font-weight: 700; }
.status-card .muted { color: rgba(255, 255, 255, 0.85); }
.sec { font-weight: 600; font-size: 28rpx; }
.line { display: flex; justify-content: space-between; margin-top: 16rpx; }
.lname { color: #374151; }
.total { margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #f0f1f3; font-weight: 600; }
.actions { padding: 20rpx 6rpx 60rpx; }
.mt { margin-top: 16rpx; }
.mt8 { margin-top: 8rpx; }
.mt12 { margin-top: 20rpx; }
</style>
