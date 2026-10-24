<template>
  <view class="page">
    <view class="tabs">
      <view :class="['tab', tab === 'available' ? 'on' : '']" @click="switchTab('available')">可领取</view>
      <view :class="['tab', tab === 'mine' ? 'on' : '']" @click="switchTab('mine')">我的券</view>
    </view>

    <!-- 可领取 -->
    <view v-if="tab === 'available'">
      <view
        v-for="c in available"
        :key="c.id"
        :class="['ticket', claimedIds[c.id] ? 'off' : '']"
      >
        <view class="left">
          <view class="face">{{ faceValue(c) }}</view>
          <view class="cond">{{ condText(c) }}</view>
        </view>
        <view class="right">
          <view class="name">{{ c.name }}</view>
          <view v-if="c.desc" class="muted">{{ c.desc }}</view>
          <view class="muted">有效期 {{ rangeText(c.startAt, c.endAt) }}</view>
          <view v-if="c.remain != null" class="muted">剩余 {{ c.remain }} 张</view>
        </view>
        <view :class="['btn', claimedIds[c.id] ? 'disabled' : '']" @click="claim(c)">
          {{ claimedIds[c.id] ? '已领取' : (claiming === c.id ? '领取中' : '领取') }}
        </view>
      </view>
      <view v-if="!available.length && !loading" class="empty">暂无可领取的优惠券</view>
    </view>

    <!-- 我的券 -->
    <view v-else>
      <view class="filters">
        <view
          v-for="f in MINE_FILTERS"
          :key="f.value"
          :class="['f', myFilter === f.value ? 'on' : '']"
          @click="pickFilter(f.value)"
        >{{ f.label }}</view>
      </view>

      <view
        v-for="c in mine"
        :key="c.userCouponId || c.id"
        :class="['ticket', c.status !== 0 ? 'off' : '']"
      >
        <view class="left">
          <view class="face">{{ faceValue(c) }}</view>
          <view class="cond">{{ condText(c) }}</view>
        </view>
        <view class="right">
          <view class="name">{{ c.name }}</view>
          <view v-if="c.desc" class="muted">{{ c.desc }}</view>
          <view class="muted">有效期 {{ rangeText(c.startAt, c.endAt) }}</view>
          <view v-if="c.status === 0 && c.usable === false && c.reason" class="muted reason">{{ c.reason }}</view>
        </view>
        <view class="tags">
          <view v-if="c.status !== 0" class="tag gray">{{ c.statusText || (c.status === 1 ? '已使用' : '已过期') }}</view>
          <view v-else-if="c.nearExpire" class="tag warn">即将过期</view>
          <view v-else-if="c.usable" class="tag ok">可用</view>
        </view>
      </view>

      <view v-if="!mine.length && !loading" class="empty">还没有优惠券，去「可领取」看看</view>
    </view>

    <view v-if="loading" class="muted loading">加载中…</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { couponApi, type CouponTemplate, type MyCoupon } from '../../api'
import { guard } from '../../utils/auth'
import { requestSubscribe } from '../../utils/subscribe'

const tab = ref<'available' | 'mine'>('available')
const myFilter = ref(0)
const available = ref<CouponTemplate[]>([])
const mine = ref<MyCoupon[]>([])
const claimedIds = ref<Record<number, boolean>>({})
const claiming = ref<number | null>(null)
const loading = ref(false)

/** 从购物车带过来的上下文：店铺 + 商品金额，用来算「这单能不能用」 */
let shopId = 0
let amount = 0

const MINE_FILTERS = [
  { value: 0, label: '未使用' },
  { value: 1, label: '已使用' },
  { value: 2, label: '已过期' }
]

/** 金额单位分；满减显示 ¥5，折扣显示 9.0折（后端 type=2 时 amount 为 880 表示 8.8 折） */
function faceValue(c: CouponTemplate | MyCoupon) {
  if (c.type === 2) return (c.amount / 100).toFixed(1) + '折'
  return '¥' + yuanShort(c.amount)
}

function condText(c: CouponTemplate | MyCoupon) {
  return c.threshold > 0 ? `满 ${yuanShort(c.threshold)} 可用` : '无门槛'
}

function yuanShort(fen: number) {
  const v = Number(fen || 0) / 100
  return Number.isInteger(v) ? String(v) : v.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

function rangeText(startAt?: string, endAt?: string) {
  const end = endAt ? endAt.slice(0, 10) : ''
  const start = startAt ? startAt.slice(0, 10) : ''
  if (!end) return '长期有效'
  return start ? `${start} 至 ${end}` : `至 ${end}`
}

async function loadAvailable() {
  loading.value = true
  try {
    available.value = (await couponApi.available(shopId || undefined)) || []
  } catch (e) {
    available.value = []
  } finally {
    loading.value = false
  }
}

async function loadMine() {
  loading.value = true
  try {
    mine.value = (await couponApi.mine({
      status: myFilter.value,
      shopId: shopId || undefined,
      amount: amount || undefined
    })) || []
  } catch (e) {
    mine.value = []
  } finally {
    loading.value = false
  }
}

function reload() {
  return tab.value === 'available' ? loadAvailable() : loadMine()
}

function switchTab(t: 'available' | 'mine') {
  if (tab.value === t) return
  tab.value = t
  reload()
}

function pickFilter(v: number) {
  if (myFilter.value === v) return
  myFilter.value = v
  loadMine()
}

async function claim(c: CouponTemplate) {
  if (claimedIds.value[c.id] || claiming.value) return
  claiming.value = c.id
  try {
    // 领券前后申请优惠券订阅授权：未配置模板时静默跳过，失败/拒绝都照常领取
    try {
      await requestSubscribe(['coupon'])
    } catch (e) {
      console.warn(e)
    }
    await couponApi.claim(c.id)
    claimedIds.value = { ...claimedIds.value, [c.id]: true }
    uni.showToast({ title: '领取成功' })
  } catch (e: any) {
    const msg = String(e?.message || '').trim()
    uni.showToast({ title: msg || '领取失败', icon: 'none' })
    // 重复领取（code=1001）也算已领，顺手置灰
    if (msg.includes('领过') || msg.includes('已领取')) {
      claimedIds.value = { ...claimedIds.value, [c.id]: true }
    }
  } finally {
    claiming.value = null
  }
}

onLoad((q: any) => {
  shopId = Number(q?.shopId) || 0
  amount = Number(q?.amount) || 0
  if (q?.tab === 'mine') tab.value = 'mine'
})

onShow(async () => {
  if (!guard('USER')) return
  reload()
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; background: #f5f6f8; }
.tabs {
  display: flex; background: #fff; border-radius: 20rpx; padding: 8rpx; margin-bottom: 20rpx;
}
.tab {
  flex: 1; text-align: center; padding: 18rpx 0; border-radius: 14rpx;
  font-size: 28rpx; color: #4b5563;
}
.tab.on { background: #fdf2ea; color: #c2410c; font-weight: 600; }

.filters { display: flex; gap: 14rpx; margin-bottom: 18rpx; }
.f {
  padding: 10rpx 26rpx; border-radius: 30rpx; font-size: 24rpx;
  background: #fff; color: #6b7280;
}
.f.on { background: #c2410c; color: #fff; }

.ticket {
  display: flex; align-items: center; background: #fff; border-radius: 20rpx;
  padding: 24rpx 20rpx; margin-bottom: 18rpx; position: relative;
}
.ticket.off { background: #f3f4f6; }
.ticket.off .face, .ticket.off .name { color: #9aa1aa; }

.left {
  width: 168rpx; flex-shrink: 0; text-align: center;
  border-right: 2rpx dashed #f0e2d8; padding-right: 16rpx;
}
.ticket.off .left { border-right-color: #e5e7eb; }
.face { font-size: 40rpx; font-weight: 700; color: #c2410c; line-height: 1.2; }
.cond { font-size: 20rpx; color: #9aa1aa; margin-top: 6rpx; }
.ticket.off .face { color: #9aa1aa; }

.right { flex: 1; padding-left: 20rpx; }
.name { font-size: 28rpx; font-weight: 600; margin-bottom: 6rpx; color: #1a1d21; }
.right .muted { font-size: 22rpx; line-height: 1.6; }
.reason { color: #b45309; }

.btn {
  flex-shrink: 0; margin-left: 12rpx;
  background: #c2410c; color: #fff; font-size: 24rpx;
  padding: 12rpx 26rpx; border-radius: 30rpx;
}
.btn.disabled { background: #e5e7eb; color: #9aa1aa; }

.tags { position: absolute; right: 20rpx; top: 18rpx; }
.tag { font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 18rpx; }
.tag.ok { background: #fdf2ea; color: #c2410c; }
.tag.warn { background: #fef3c7; color: #b45309; }
.tag.gray { background: #e5e7eb; color: #6b7280; }

.empty { text-align: center; color: #9ca3af; padding: 120rpx 0; font-size: 26rpx; }
.loading { text-align: center; padding: 30rpx 0; }
</style>
