<template>
  <view class="page">
    <scroll-view class="tabs" scroll-x>
      <view
        v-for="t in tabs"
        :key="t.value ?? -1"
        :class="['tab', active === t.value ? 'on' : '']"
        @click="pick(t.value)"
      >{{ t.label }}</view>
    </scroll-view>

    <view class="list">
      <view v-for="o in orders" :key="o.id" class="card" @click="go(o.id)">
        <view class="between">
          <text class="no">{{ o.orderNo }}</text>
          <text :class="['status', 'st' + o.status]">{{ o.statusText }}</text>
        </view>
        <view class="between mt">
          <text class="muted">{{ o.createdAt }}</text>
          <text class="price">{{ (o.payAmount / 100).toFixed(2) }} 元</text>
        </view>
      </view>
      <view v-if="!orders.length" class="empty">还没有订单</view>
    </view>
    <AppTabBar :role="'USER'" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import { api, type OrderCard } from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const tabs = [
  { label: '全部', value: undefined },
  { label: '待支付', value: 0 },
  { label: '待接单', value: 10 },
  { label: '待出餐', value: 20 },
  { label: '待取餐', value: 30 },
  { label: '配送中', value: 40 },
  { label: '已完成', value: 50 }
] as { label: string; value?: number }[]

const active = ref<number | undefined>(undefined)
const orders = ref<OrderCard[]>([])
let timer: any = null

/** 列表页也要自动同步：骑手一送达，这里不手动刷新也能看到「已完成」 */
function startPoll() {
  stopPoll()
  timer = setInterval(load, 8000)
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

async function load() {
  const res = await api.orders(active.value)
  orders.value = res.list || []
}

function pick(v?: number) {
  active.value = v
  load()
}

function go(id: number) {
  uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
}

onShow(async () => {
  if (!guard('USER')) return
  await load()
  startPoll()
})

onHide(stopPoll)
onUnload(stopPoll)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page { padding-bottom: 30rpx; }
.tabs { white-space: nowrap; background: #fff; padding: 16rpx 12rpx; }
.tab {
  display: inline-block; padding: 10rpx 26rpx; margin: 0 6rpx;
  border-radius: 28rpx; font-size: 26rpx; color: #4b5563; background: #f3f4f6;
}
.tab.on { background: #fdf2ea; color: #c2410c; font-weight: 600; }
.list { padding: 20rpx; }
.no { font-size: 26rpx; color: #374151; }
.status { font-size: 24rpx; padding: 4rpx 14rpx; border-radius: 20rpx; background: #f3f4f6; color: #4b5563; }
.st0 { background: #fff7ed; color: #c2410c; }
.st40 { background: #eff6ff; color: #1d4ed8; }
.st50 { background: #ecfdf5; color: #047857; }
.st60, .st80 { background: #f3f4f6; color: #6b7280; }
.mt { margin-top: 16rpx; }
.empty { text-align: center; color: #9ca3af; padding: 100rpx 0; }
</style>
