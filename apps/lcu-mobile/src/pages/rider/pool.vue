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

    <view class="dispatch-note muted">
      高峰时段系统会优先派单，需要你在 2 分钟内确认，超时自动退回抢单大厅。
    </view>

    <!-- 派给我的单 -->
    <view v-if="dispatchedView.length" class="dispatch-wrap">
      <view class="between dispatch-head">
        <text class="dispatch-title">派给我的单</text>
        <text class="muted">请尽快确认</text>
      </view>

      <view v-for="d in dispatchedView" :key="d.orderId" class="card dispatch-card">
        <view class="between">
          <view class="shop-row">
            <text class="badge">系统派单</text>
            <text class="shop">{{ d.shopName }}</text>
          </view>
          <text class="income">+{{ (d.riderIncome / 100).toFixed(2) }} 元</text>
        </view>
        <view class="muted mt8">{{ d.orderNo }}</view>
        <view class="muted mt8">{{ addressText(d.address) }}</view>
        <view class="between mt8">
          <text :class="['countdown', d.left <= 30 ? 'urgent' : '']">{{ d.leftText }}</text>
          <view class="card-ops">
            <view class="btn small ghost" @click="rejectDispatchOrder(d)">拒绝</view>
            <view class="btn small" @click="acceptDispatchOrder(d)">接受</view>
          </view>
        </view>
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
          <view class="card-ops">
            <view v-if="o.shopLng && o.shopLat" class="btn small ghost" @click="navShop(o)">导航到店</view>
            <view class="btn small" @click="grab(o.orderId)">抢单</view>
          </view>
        </view>
      </view>
      <view v-if="!pool.length" class="empty">
        <view>暂时没有可抢的订单</view>
        <view class="empty-tip">商家点「出餐」后，订单才会进抢单大厅（每 8 秒自动刷新，也可下拉刷新）</view>
      </view>
    </view>

    <AppTabBar :role="'RIDER'" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import { riderApi, ensureRiderApproved, type RiderDispatchedOrder, type RiderPoolItem } from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const pool = ref<RiderPoolItem[]>([])
const dispatched = ref<RiderDispatchedOrder[]>([])
const profile = ref<any>(null)
/** 每秒刷新一次，用于派单倒计时 */
const now = ref(Date.now())
let tickTimer: any = null
let lastExpireReload = 0
/** 抢单轮询 */
let timer: any = null
/** 轨迹上报（15s 一次） */
let locTimer: any = null

function addressText(s: string) {
  try {
    const a = JSON.parse(s || '{}')
    return `${a.campus === 1 ? '东区' : a.campus === 2 ? '西区' : ''} ${a.detail || ''}`
  } catch { return '' }
}

/** 派单倒计时：按 dispatchedAt + expireSeconds 本地计算 */
const dispatchedView = computed(() =>
  dispatched.value.map((d) => {
    const base = d.dispatchedAt ? new Date(String(d.dispatchedAt).replace(' ', 'T')).getTime() : now.value
    const total = Number(d.expireSeconds || 0)
    const left = Math.max(0, Math.round((base + total * 1000 - now.value) / 1000))
    const m = Math.floor(left / 60)
    const s = left % 60
    return {
      ...d,
      left,
      leftText: left <= 0 ? '已超时，等待系统回收' : `剩余 ${m}:${s < 10 ? '0' + s : s}`
    }
  })
)

async function load() {
  try {
    profile.value = await riderApi.profile()
  } catch (e) {
    console.warn(e)
  }
  try {
    pool.value = await riderApi.pool()
  } catch (e) {
    pool.value = []
  }
  try {
    dispatched.value = (await riderApi.dispatched()) || []
  } catch (e) {
    dispatched.value = []
  }
}

/** 有派单超时了就静默刷新一次（5s 内不重复触发） */
function startTick() {
  stopTick()
  now.value = Date.now()
  tickTimer = setInterval(() => {
    now.value = Date.now()
    const expired = dispatchedView.value.some((d) => d.left <= 0)
    if (expired && Date.now() - lastExpireReload > 5000) {
      lastExpireReload = Date.now()
      load()
    }
  }, 1000)
}

function stopTick() {
  if (tickTimer) {
    clearInterval(tickTimer)
    tickTimer = null
  }
}

async function acceptDispatchOrder(d: RiderDispatchedOrder) {
  try {
    await riderApi.acceptDispatch(d.orderId)
    uni.showToast({ title: '已接受，请尽快取餐', icon: 'none' })
    setTimeout(() => uni.reLaunch({ url: '/pages/rider/orders' }), 700)
  } catch (e) {
    // 「这单状态已变化，请刷新」等由 request 层 toast
    load()
  }
}

async function rejectDispatchOrder(d: RiderDispatchedOrder) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '拒绝派单',
      content: '拒绝后这单会退回抢单大厅给别人接，确定拒绝吗？',
      success: (r) => resolve(!!r.confirm)
    })
  })
  if (!ok) return
  try {
    await riderApi.rejectDispatch(d.orderId)
    uni.showToast({ title: '已拒绝', icon: 'none' })
  } catch (e) {
    console.warn(e)
  }
  load()
}

function startPolling() {
  stopPolling()
  timer = setInterval(load, 8000)
}

function stopPolling() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

function navShop(o: RiderPoolItem) {
  if (!o.shopLng || !o.shopLat) return
  uni.openLocation({
    latitude: Number(o.shopLat),
    longitude: Number(o.shopLng),
    name: o.shopName,
    scale: 16
  })
}

/** 取一次定位并上报；定位失败静默跳过，避免 toast 刷屏 */
function reportLocationOnce() {
  uni.getLocation({
    type: 'gcj02',
    success: async (res: any) => {
      const lng = Number(res?.longitude)
      const lat = Number(res?.latitude)
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) return
      try {
        await riderApi.location(lng, lat)
      } catch (e) {
        // 静默：上报失败不影响接单
      }
    },
    fail: () => {
      // 用户没开定位/模拟器不支持：跳过这一轮
    }
  })
}

function startTracking() {
  stopTracking()
  reportLocationOnce()
  locTimer = setInterval(reportLocationOnce, 15000)
}

function stopTracking() {
  if (locTimer) {
    clearInterval(locTimer)
    locTimer = null
  }
}

async function grab(id: number) {
  await riderApi.grab(id)
  uni.showToast({ title: '抢单成功' })
  load()
}


onShow(async () => {
  if (!guard('RIDER')) return
  // 没入驻/未通过审核先去入驻页，避免打接口报「还没有通过审核」
  if (!(await ensureRiderApproved())) return
  load()
  startPolling()
  startTracking()
  startTick()
})

onHide(() => {
  stopPolling()
  stopTracking()
  stopTick()
})
onUnload(() => {
  stopPolling()
  stopTracking()
  stopTick()
})

onPullDownRefresh(async () => {
  if (await ensureRiderApproved()) await load()
  uni.stopPullDownRefresh()
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
.card-ops { display: flex; align-items: center; gap: 14rpx; }
.dispatch-note { padding: 14rpx 20rpx; background: #fff7ed; border-radius: 14rpx; color: #b45309; line-height: 1.6; }
.dispatch-wrap { margin-top: 16rpx; }
.dispatch-head { padding: 0 6rpx 10rpx; }
.dispatch-title { font-size: 30rpx; font-weight: 700; color: #c2410c; }
.dispatch-card { border: 2rpx solid #c2410c; border-left: 8rpx solid #c2410c; }
.shop-row { display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
.badge {
  font-size: 20rpx; color: #fff; background: #c2410c;
  border-radius: 16rpx; padding: 4rpx 12rpx;
}
.countdown { font-size: 24rpx; color: #b45309; font-weight: 600; }
.countdown.urgent { color: #b91c1c; }
.btn.small.ghost { background: #f5f6f8; color: #4b5563; }
.empty { text-align: center; color: #9ca3af; padding: 80rpx 40rpx; }
.empty-tip { font-size: 24rpx; color: #c2c6cd; margin-top: 12rpx; line-height: 1.6; }

</style>
