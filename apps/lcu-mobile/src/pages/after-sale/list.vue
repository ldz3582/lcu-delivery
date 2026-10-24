<template>
  <view class="page">
    <view class="tabs">
      <view
        v-for="f in FILTERS"
        :key="f.value"
        :class="['tab', filter === f.value ? 'on' : '']"
        @click="pickFilter(f.value)"
      >{{ f.label }}</view>
    </view>

    <view v-if="loading" class="muted loading">加载中…</view>

    <view
      v-for="it in list"
      :key="it.id"
      class="card record"
      @click="goOrder(it.orderId)"
    >
      <view class="between">
        <text class="order-no">{{ it.orderNo || ('订单 #' + it.orderId) }}</text>
        <text :class="['status', statusClass(it.status)]">{{ it.statusText }}</text>
      </view>

      <view class="between mt8">
        <text class="muted">{{ it.shopName || '店铺' }} · {{ it.typeText }}</text>
        <text class="amount">¥{{ yuan(it.amount) }}</text>
      </view>

      <view class="reason mt8">{{ it.reason }}</view>

      <view v-if="it.judgeRemark" class="judge mt8">处理结果：{{ it.judgeRemark }}</view>

      <view class="between mt8 foot">
        <text class="muted">{{ it.createdAt }}</text>
        <view v-if="it.status === 0" class="cancel" @click.stop="cancel(it)">撤销申请</view>
      </view>
    </view>

    <view v-if="!loading && !list.length" class="empty">
      <view>还没有售后记录</view>
      <view class="muted mt8">在已完成订单的详情页可以申请售后</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { afterSaleApi, type AfterSale } from '../../api'
import { guard } from '../../utils/auth'

const FILTERS = [
  { value: -1, label: '全部' },
  { value: 0, label: '待处理' },
  { value: 1, label: '处理中' },
  { value: 2, label: '已退款' },
  { value: 3, label: '已驳回' },
  { value: 4, label: '已关闭' }
]

const list = ref<AfterSale[]>([])
const loading = ref(false)
/** -1 = 全部；其余直接作为 status 传给后端筛选 */
const filter = ref(-1)

function yuan(fen: number) {
  return Number((Number(fen || 0)) / 100).toFixed(2)
}

function statusClass(status: number) {
  if (status === 0) return 's0'
  if (status === 1) return 's1'
  if (status === 2) return 's2'
  if (status === 3) return 's3'
  return 's4'
}

async function load() {
  loading.value = true
  try {
    // 外层是 {list,total,page,size}，不是裸数组；status 由后端筛选
    const res = await afterSaleApi.mine({
      status: filter.value < 0 ? undefined : filter.value,
      page: 1,
      size: 20
    })
    list.value = res?.list || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function pickFilter(v: number) {
  if (filter.value === v) return
  filter.value = v
  load()
}

function goOrder(orderId: number) {
  if (!orderId) return
  uni.navigateTo({ url: `/pages/order/detail?id=${orderId}` })
}

async function cancel(it: AfterSale) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '撤销申请',
      content: '撤销后订单会恢复为「已完成」，确定撤销这次售后吗？',
      success: (r) => resolve(!!r.confirm)
    })
  })
  if (!ok) return
  try {
    await afterSaleApi.cancel(it.id)
    uni.showToast({ title: '已撤销', icon: 'success' })
    load()
  } catch (e) {
    // 已受理等错误由 request 层 toast
    console.warn(e)
  }
}

onShow(async () => {
  if (!guard('USER')) return
  await load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; background: #f5f6f8; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.muted { color: #7a828c; font-size: 24rpx; }
.mt8 { margin-top: 8rpx; }

.tabs { display: flex; flex-wrap: wrap; gap: 12rpx; margin-bottom: 18rpx; }
.tab {
  padding: 10rpx 24rpx; border-radius: 28rpx; font-size: 24rpx;
  background: #fff; color: #6b7280;
}
.tab.on { background: #c2410c; color: #fff; }

.order-no { font-size: 26rpx; font-weight: 600; color: #1a1d21; }
.status { font-size: 22rpx; padding: 4rpx 14rpx; border-radius: 18rpx; }
.status.s0 { background: #fef3c7; color: #b45309; }
.status.s1 { background: #dbeafe; color: #1d4ed8; }
.status.s2 { background: #d1fae5; color: #047857; }
.status.s3 { background: #fee2e2; color: #b91c1c; }
.status.s4 { background: #e5e7eb; color: #6b7280; }

.amount { color: #c2410c; font-weight: 600; font-size: 26rpx; }
.reason { font-size: 26rpx; color: #374151; line-height: 1.5; }
.judge { font-size: 24rpx; color: #4b5563; background: #f7f8fa; border-radius: 12rpx; padding: 14rpx; }
.foot { border-top: 1rpx solid #f2f3f5; padding-top: 14rpx; }
.cancel {
  font-size: 24rpx; color: #c2410c; border: 1rpx solid #f0b79c;
  background: #fdf2ea; border-radius: 26rpx; padding: 8rpx 20rpx;
}

.empty { text-align: center; color: #9ca3af; padding: 120rpx 0; font-size: 26rpx; }
.loading { text-align: center; padding: 30rpx 0; }
</style>
