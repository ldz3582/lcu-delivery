<template>
  <view class="page">
    <view v-for="o in orders" :key="o.orderId" class="card">
      <view class="between">
        <text class="shop">{{ o.shopName }}</text>
        <text class="status">{{ o.statusText }}</text>
      </view>
      <view class="muted mt8">{{ addressText(o.address) }}</view>
      <view class="between mt8">
        <text class="muted">本单收入 {{ (o.riderIncome / 100).toFixed(2) }} 元</text>
        <view class="row-inline">
          <view v-if="o.status === 30" class="btn small" @click="pickup(o.orderId)">已取餐</view>
          <view v-if="o.status === 40" class="btn small" @click="deliver(o.orderId)">确认送达</view>
        </view>
      </view>
    </view>
    <view v-if="!orders.length" class="empty">还没有配送记录</view>
    <AppTabBar :role="'RIDER'" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { riderApi } from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const orders = ref<any[]>([])

function addressText(s: string) {
  try {
    const a = JSON.parse(s || '{}')
    return `${a.campus === 1 ? '东区' : a.campus === 2 ? '西区' : ''} ${a.detail || ''}`
  } catch { return '' }
}

async function load() {
  orders.value = await riderApi.orders()
}

async function pickup(id: number) {
  await riderApi.pickup(id, 'https://example.com/pickup.jpg')
  uni.showToast({ title: '已取餐' })
  load()
}

async function deliver(id: number) {
  await riderApi.deliver(id, 'https://example.com/deliver.jpg')
  uni.showToast({ title: '已送达' })
  load()
}

onShow(() => { if (!guard('RIDER')) return; load() })

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page { padding: 20rpx; }
.shop { font-weight: 600; }
.status { font-size: 24rpx; color: #1d4ed8; background: #eff6ff; padding: 4rpx 14rpx; border-radius: 16rpx; }
.mt8 { margin-top: 10rpx; }
.row-inline { display: flex; gap: 12rpx; }
.btn.small { padding: 10rpx 28rpx; font-size: 24rpx; border-radius: 26rpx; }
.empty { text-align: center; color: #9ca3af; padding: 100rpx 0; }
</style>
