<template>
  <view class="page">
    <view class="top card">
      <view class="between">
        <view>
          <view class="title">抢单大厅</view>
          <view class="muted">{{ profile?.auditStatus === 1 ? '已认证骑手 · 评分 ' + profile?.score : '资质审核中，暂不能接单' }}</view>
        </view>
        <text class="refresh" @click="load">刷新</text>
      </view>
    </view>

    <view class="list">
      <view v-for="o in pool" :key="o.orderId" class="card">
        <view class="between">
          <text class="shop">{{ o.shopName }}</text>
          <text class="income">+{{ (o.riderIncome / 100).toFixed(2) }} 元</text>
        </view>
        <view class="muted mt8">{{ addressText(o.address) }}</view>
        <view class="between mt8">
          <text class="muted" v-if="o.distance">{{ o.distance }} m</text>
          <text class="muted" v-else>距离未知</text>
          <view class="btn small" @click="grab(o.orderId)">抢单</view>
        </view>
      </view>
      <view v-if="!pool.length" class="empty">暂时没有可抢的订单</view>
    </view>

    <view class="nav">
      <text @click="go('/pages/rider/orders')">我的配送</text>
      <text @click="go('/pages/rider/profile')">骑手中心</text>
      <text @click="logout">退出</text>
    </view>
    <AppTabBar :role="'RIDER'" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { riderApi, type RiderPoolItem } from '../../api'
import { logout } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const pool = ref<RiderPoolItem[]>([])
const profile = ref<any>(null)
let timer: any = null

function addressText(s: string) {
  try {
    const a = JSON.parse(s || '{}')
    return `${a.campus === 1 ? '东区' : a.campus === 2 ? '西区' : ''} ${a.detail || ''}`
  } catch { return '' }
}

async function load() {
  try {
    profile.value = await riderApi.profile()
    pool.value = await riderApi.pool()
  } catch (e) {
    console.warn(e)
  }
}

async function grab(id: number) {
  await riderApi.grab(id)
  uni.showToast({ title: '抢单成功' })
  load()
}

function go(url: string) { uni.navigateTo({ url }) }

onShow(() => {
  if (!guard('RIDER')) return
  load()
  timer = setInterval(load, 8000)
})
</script>

<style scoped>
.page { padding: 20rpx 20rpx 120rpx; }
.title { font-size: 34rpx; font-weight: 700; }
.refresh { color: #c2410c; font-size: 26rpx; }
.list { margin-top: 16rpx; }
.shop { font-weight: 600; font-size: 30rpx; }
.income { color: #c2410c; font-weight: 600; }
.mt8 { margin-top: 10rpx; }
.btn.small { padding: 12rpx 34rpx; font-size: 26rpx; border-radius: 30rpx; }
.empty { text-align: center; color: #9ca3af; padding: 100rpx 0; }
.nav {
  position: fixed; bottom: 0; left: 0; right: 0; background: #fff;
  display: flex; justify-content: space-around; padding: 24rpx 0; border-top: 1rpx solid #eef0f3; font-size: 26rpx; color: #4b5563;
}
</style>
