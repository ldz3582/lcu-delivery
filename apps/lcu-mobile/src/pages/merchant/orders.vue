<template>
  <view class="page">
    <view class="top">
      <view class="between">
        <view>
          <view class="shop">{{ shop?.name || '店铺' }}</view>
          <view class="muted">{{ shop?.bizStatus === 1 ? '营业中' : '已打烊' }} · {{ shop?.openTime }}</view>
        </view>
        <view class="switch" @click="toggleBiz">{{ shop?.bizStatus === 1 ? '打烊' : '营业' }}</view>
      </view>
    </view>

    <scroll-view class="tabs" scroll-x>
      <view v-for="t in tabs" :key="t.v ?? -1" :class="['tab', active === t.v ? 'on' : '']" @click="pick(t.v)">{{ t.label }}</view>
    </scroll-view>

    <view class="list">
      <view v-for="o in orders" :key="o.id" class="card">
        <view class="between">
          <text class="no">{{ o.orderNo }}</text>
          <text class="status">{{ o.statusText }}</text>
        </view>
        <view class="muted mt8">{{ addressText(o.address) }}</view>
        <view v-if="o.remark" class="muted mt8">备注：{{ o.remark }}</view>
        <view class="between mt">
          <text class="muted">实付 {{ (o.payAmount / 100).toFixed(2) }} 元</text>
          <text class="price">到手 {{ (o.shopIncome / 100).toFixed(2) }} 元</text>
        </view>
        <view class="row">
          <view v-if="o.status === 10" class="btn ghost" @click="reject(o.id)">拒单</view>
          <view v-if="o.status === 10" class="btn" @click="accept(o.id)">接单</view>
          <view v-if="o.status === 20" class="btn" @click="ready(o.id)">出餐</view>
          <view v-if="o.status >= 30" class="btn gray" @click="noop">已出餐</view>
        </view>
      </view>
      <view v-if="!orders.length" class="empty">暂无订单</view>
    </view>

    <view class="nav">
      <text @click="go('/pages/merchant/products')">菜品管理</text>
      <text @click="go('/pages/merchant/shop')">店铺设置</text>
      <text @click="logout">退出</text>
    </view>
    <AppTabBar :role="'MERCHANT'" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onHide } from '@dcloudio/uni-app'
import { merchantApi, type MerchantOrder, type MerchantShop } from '../../api'
import { logout } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const tabs = [
  { label: '待接单', v: 10 },
  { label: '待出餐', v: 20 },
  { label: '待取餐', v: 30 },
  { label: '全部', v: undefined }
] as { label: string; v?: number }[]

const shop = ref<MerchantShop | null>(null)
const orders = ref<MerchantOrder[]>([])
const active = ref<number | undefined>(10)
let timer: any = null

function addressText(s: string) {
  try {
    const a = JSON.parse(s || '{}')
    return `${a.campus === 1 ? '东区' : a.campus === 2 ? '西区' : ''} ${a.detail || ''}`
  } catch { return '' }
}

async function load() {
  const [s, o] = await Promise.all([merchantApi.shop(), merchantApi.orders(active.value)])
  shop.value = s
  orders.value = o.list || []
}

function pick(v?: number) { active.value = v; load() }
function go(url: string) { uni.navigateTo({ url }) }
function noop() {}
function reject(id: number) {
  uni.showModal({
    title: '拒单原因', editable: true, placeholderText: '如：菜品售罄',
    success: async (r) => {
      if (!r.confirm) return
      await merchantApi.reject(id, r.content || '商家拒单')
      load()
    }
  })
}
async function accept(id: number) { await merchantApi.accept(id); uni.showToast({ title: '已接单' }); load() }
async function ready(id: number) { await merchantApi.ready(id); uni.showToast({ title: '已出餐' }); load() }
async function toggleBiz() {
  if (!shop.value) return
  await merchantApi.updateShop({ bizStatus: shop.value.bizStatus === 1 ? 0 : 1 })
  load()
}

onShow(() => {
  if (!guard('MERCHANT')) return
  load()
  timer = setInterval(load, 10000)
})
onHide(() => { if (timer) clearInterval(timer) })
</script>

<style scoped>
.page { padding-bottom: 110rpx; }
.top { background: #fff; padding: 24rpx; }
.shop { font-size: 34rpx; font-weight: 700; }
.switch { background: #fdf2ea; color: #c2410c; padding: 12rpx 26rpx; border-radius: 30rpx; font-size: 26rpx; }
.tabs { white-space: nowrap; background: #fff; padding: 0 12rpx 16rpx; }
.tab { display: inline-block; padding: 10rpx 26rpx; margin-right: 10rpx; border-radius: 26rpx; background: #f3f4f6; font-size: 26rpx; }
.tab.on { background: #fdf2ea; color: #c2410c; font-weight: 600; }
.list { padding: 20rpx; }
.no { font-size: 26rpx; }
.status { font-size: 24rpx; color: #c2410c; background: #fff7ed; padding: 4rpx 14rpx; border-radius: 16rpx; }
.mt { margin-top: 16rpx; }
.mt8 { margin-top: 8rpx; }
.empty { text-align: center; color: #9ca3af; padding: 100rpx 0; }
.nav {
  position: fixed; bottom: 0; left: 0; right: 0; background: #fff;
  display: flex; justify-content: space-around; padding: 24rpx 0; border-top: 1rpx solid #eef0f3; font-size: 26rpx; color: #4b5563;
}
</style>
