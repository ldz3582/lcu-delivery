<template>
  <view class="page">
    <view v-if="order" class="card">
      <view class="between">
        <text class="sec">{{ order.shopName || '店铺' }}</text>
        <text class="muted">{{ order.orderNo }}</text>
      </view>
      <view class="muted mt8">订单状态：{{ order.statusText }}</view>
    </view>

    <view v-if="loading" class="muted loading">加载中…</view>

    <!-- 已评价：只读展示 -->
    <view v-else-if="existing" class="card">
      <view class="between">
        <text class="sec">这单已经评价过了</text>
        <text class="muted">{{ existing.createdAt }}</text>
      </view>
      <view class="row mt">
        <text class="muted">店铺评分</text>
        <text class="stars-readonly">{{ existingShopStars }}</text>
      </view>
      <view v-if="existing.riderScore" class="row mt">
        <text class="muted">骑手评分</text>
        <text class="stars-readonly">{{ existingRiderStars }}</text>
      </view>
      <view v-if="existing.content" class="content mt">{{ existing.content }}</view>
      <view v-if="existing.reply" class="reply mt">商家回复：{{ existing.reply }}</view>
      <view class="back mt" @click="goBack">返回订单</view>
    </view>

    <!-- 未评价：表单 -->
    <view v-else>
      <view class="card">
        <view class="between">
          <text class="sec">店铺评分 <text class="req">*</text></text>
          <text class="muted">{{ shopScore }} 分</text>
        </view>
        <view class="stars">
          <text
            v-for="n in 5"
            :key="'s' + n"
            :class="['star', n <= shopScore ? 'on' : '']"
            @click="shopScore = n"
          >★</text>
        </view>

        <view v-if="hasRider" class="between mt2">
          <text class="sec">骑手评分</text>
          <text class="muted">{{ riderScore }} 分</text>
        </view>
        <view v-if="hasRider" class="stars">
          <text
            v-for="n in 5"
            :key="'r' + n"
            :class="['star', n <= riderScore ? 'on' : '']"
            @click="riderScore = n"
          >★</text>
        </view>
      </view>

      <view class="card">
        <view class="between">
          <text class="sec">评价内容</text>
          <text class="muted">{{ content.length }}/500</text>
        </view>
        <textarea
          v-model="content"
          class="ta mt"
          :maxlength="500"
          placeholder="说说这次的味道、出餐速度和包装吧～"
        />
      </view>

      <view class="card between">
        <text class="sec">匿名评价</text>
        <switch :checked="anonymous" color="#c2410c" @change="onAnonChange" />
      </view>

      <view v-if="!canReview" class="tip">订单完成（送达）后才能评价，请先在订单详情点「确认收货」。</view>
      <view :class="['submit', canReview && !submitting ? '' : 'off']" @click="submit">
        {{ submitting ? '提交中…' : '提交评价' }}
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api, reviewApi, type Review } from '../../api'
import { guard } from '../../utils/auth'

const order = ref<any>(null)
const existing = ref<Review | null>(null)
const loading = ref(false)
const submitting = ref(false)

const shopScore = ref(5)
const riderScore = ref(5)
const content = ref('')
const anonymous = ref(false)

let orderId = 0
let loadedReview = false

const canReview = computed(() => Number(order.value?.status) === 50 && !existing.value)

function starText(score?: number | null) {
  const n = Math.max(0, Math.min(5, Number(score || 0)))
  return '★'.repeat(n) + '☆'.repeat(5 - n)
}
const existingShopStars = computed(() => starText(existing.value?.shopScore))
const existingRiderStars = computed(() => starText(existing.value?.riderScore))

/** 无骑手订单（自提/未接单）不展示骑手评分 */
const hasRider = computed(() => {
  const o = order.value
  if (!o) return false
  return !!o.riderId || !!o.riderName
})

function onAnonChange(e: any) {
  anonymous.value = !!e?.detail?.value
}

async function loadOrder() {
  if (!orderId) return
  try {
    order.value = await api.order(orderId)
  } catch (e) {
    console.warn(e)
  }
}

async function loadReview() {
  if (!orderId || loadedReview) return
  loadedReview = true
  try {
    existing.value = await reviewApi.byOrder(orderId)
  } catch (e) {
    // code=404 表示还没评价，属正常分支
    existing.value = null
  }
}

function goBack() {
  const pages = getCurrentPages()
  if (pages.length > 1) uni.navigateBack()
  else uni.redirectTo({ url: `/pages/order/detail?id=${orderId}` })
}

async function submit() {
  if (submitting.value) return
  if (!canReview.value) {
    uni.showToast({ title: '订单完成后才能评价', icon: 'none' })
    return
  }
  if (!shopScore.value) {
    uni.showToast({ title: '请先给店铺打分', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await reviewApi.create({
      orderId,
      shopScore: shopScore.value,
      riderScore: hasRider.value ? riderScore.value : null,
      content: content.value.trim(),
      anonymous: anonymous.value
    })
    uni.showToast({ title: '评价成功', icon: 'success' })
    setTimeout(goBack, 700)
  } catch (e: any) {
    // 重复评价 code=2000「这单已经评价过了」等，后端文案已足够明确
    const msg = String(e?.message || '')
    uni.showToast({ title: msg || '提交失败，请稍后再试', icon: 'none' })
    if (msg.includes('评价过')) {
      loadedReview = false
      loadReview()
    }
  } finally {
    submitting.value = false
  }
}

onLoad((q: any) => {
  orderId = Number(q?.id)
})

onShow(async () => {
  if (!guard('USER')) return
  if (!orderId) {
    uni.showToast({ title: '缺少订单参数', icon: 'none' })
    return
  }
  loading.value = true
  await loadOrder()
  await loadReview()
  loading.value = false
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; background: #f5f6f8; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.row { display: flex; align-items: center; gap: 16rpx; }
.sec { font-size: 28rpx; font-weight: 600; }
.req { color: #dc2626; }
.muted { color: #7a828c; font-size: 24rpx; }
.mt { margin-top: 16rpx; }
.mt2 { margin-top: 30rpx; }
.mt8 { margin-top: 8rpx; }

.stars { display: flex; gap: 18rpx; margin-top: 18rpx; }
.star { font-size: 56rpx; line-height: 1; color: #e5e7eb; }
.star.on { color: #f59e0b; }
.stars-readonly { color: #f59e0b; font-size: 28rpx; letter-spacing: 2rpx; }

.ta {
  width: 100%; box-sizing: border-box; min-height: 180rpx;
  font-size: 26rpx; line-height: 1.6; color: #1a1d21;
}
.content { font-size: 26rpx; line-height: 1.6; color: #374151; }
.reply { font-size: 24rpx; color: #7a828c; background: #f7f8fa; border-radius: 12rpx; padding: 16rpx; }

.tip { color: #b45309; font-size: 24rpx; padding: 0 12rpx 16rpx; }
.submit {
  margin: 20rpx 10rpx 60rpx;
  background: #c2410c; color: #fff; text-align: center;
  padding: 24rpx; border-radius: 44rpx; font-size: 32rpx;
}
.submit.off { background: #e5e7eb; color: #9aa1aa; }
.back {
  text-align: center; color: #c2410c; font-size: 28rpx;
  background: #fdf2ea; border-radius: 40rpx; padding: 20rpx 0;
}
.loading { text-align: center; padding: 60rpx 0; }
</style>
