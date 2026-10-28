<template>
  <view class="page">
    <scroll-view class="tabs" scroll-x>
      <view
        v-for="t in tabs"
        :key="String(t.value ?? 'all')"
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
        <!-- reviewed 由 GET /api/orders 每行返回：已评价不再显示「去评价」入口 -->
        <view v-if="o.status === 50" class="between mt8">
          <text class="muted">{{ o.reviewed ? '已评价' : '完成后可评价' }}</text>
          <text v-if="o.reviewed === false" class="review-link" @click.stop="goReview(o.id)">去评价 ›</text>
        </view>
      </view>
      <view v-if="loading" class="foot muted">加载中…</view>
      <view v-else-if="finished && orders.length" class="foot muted">没有更多了</view>
      <view v-if="!orders.length && !loading" class="empty">还没有订单</view>
    </view>
    <AppTabBar :role="'USER'" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh, onReachBottom } from '@dcloudio/uni-app'
import { api, type OrderCard } from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

type TabValue = number | 'after' | undefined

const tabs: { label: string; value: TabValue }[] = [
  { label: '全部', value: undefined },
  { label: '待支付', value: 0 },
  { label: '待接单', value: 10 },
  { label: '待出餐', value: 20 },
  { label: '待取餐', value: 30 },
  { label: '配送中', value: 40 },
  // status=50 且 reviewed=0（服务端过滤），避免待评价 tab 混入已评价订单
  { label: '待评价', value: 50 },
  // 售后中(70) / 已退款(80)：服务端 statuses=70,80 过滤，分页由列表自己维护
  { label: '售后', value: 'after' }
]

const PAGE_SIZE = 20
const active = ref<TabValue>(undefined)
const orders = ref<OrderCard[]>([])
const loading = ref(false)
const finished = ref(false)
let page = 0
let total = 0
let timer: any = null
/** 切 tab/重置时让旧请求的结果失效 */
let seq = 0

/** 把当前 tab 翻译成 GET /api/orders 的过滤参数 */
function queryParams() {
  if (active.value === 'after') return { statuses: '70,80' }
  if (active.value === 50) return { status: 50, reviewed: 0 }
  return { status: active.value }
}

/** 列表页也要自动同步：骑手一送达，这里不手动刷新也能看到「已完成」 */
function startPoll() {
  stopPoll()
  timer = setInterval(() => {
    // 已经下拉加载过多页时不自动重置，避免用户滚动位置被轮询打断
    if (page === 1 && !loading.value) load(true)
  }, 8000)
}

function stopPoll() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

/** reset=true 回到第一页；false 追加下一页（触底加载） */
async function load(reset = true) {
  if (loading.value && !reset) return
  const my = ++seq
  if (reset) {
    page = 0
    finished.value = false
  }
  loading.value = true
  try {
    const next = reset ? 1 : page + 1
    const res = await api.orders({ ...queryParams(), page: next, size: PAGE_SIZE })
    if (my !== seq) return
    const list = res.list || []
    orders.value = reset ? list : orders.value.concat(list)
    page = next
    total = Number(res.total || 0)
    finished.value = orders.value.length >= total || list.length < PAGE_SIZE
  } catch (e) {
    if (my === seq) console.warn(e)
  } finally {
    if (my === seq) loading.value = false
  }
}

function pick(v: TabValue) {
  active.value = v
  load(true)
}

function go(id: number) {
  uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
}

function goReview(id: number) {
  uni.navigateTo({ url: `/pages/review/edit?id=${id}` })
}

onShow(async () => {
  if (!guard('USER')) return
  await load(true)
  startPoll()
})

onHide(stopPoll)
onUnload(stopPoll)

onReachBottom(() => {
  if (!finished.value && !loading.value) load(false)
})

onPullDownRefresh(async () => {
  await load(true)
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
.st60 { background: #f3f4f6; color: #6b7280; }
.st70 { background: #fef3c7; color: #b45309; }
.st80 { background: #f3f4f6; color: #6b7280; }
.mt { margin-top: 16rpx; }
.mt8 { margin-top: 8rpx; }
.review-link { color: #c2410c; font-size: 24rpx; padding: 6rpx 0 6rpx 16rpx; }
.foot { text-align: center; padding: 24rpx 0; font-size: 24rpx; }
.empty { text-align: center; color: #9ca3af; padding: 100rpx 0; }
</style>
