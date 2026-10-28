<template>
  <view class="page">
    <view class="card">
      <view class="title">{{ shop?.name }}</view>
      <view class="muted mt8">审核状态：{{ auditText }}</view>
      <view class="muted">佣金比例：{{ ((shop?.commissionRate || 0) * 100).toFixed(1) }}%</view>
    </view>

    <view class="card">
      <view class="field"><text>营业状态</text><switch :checked="shop?.bizStatus === 1" @change="toggle" color="#c2410c" /></view>
      <view class="field"><text>营业时间</text><input v-model="form.openTime" /></view>
      <view class="field"><text>起送价（分）</text><input v-model="form.minAmount" type="number" /></view>
      <view class="field"><text>打包费（分）</text><input v-model="form.packFee" type="number" /></view>
      <view class="field"><text>配送费（分）</text><input v-model="form.deliveryFee" type="number" /></view>
      <view class="field-col">
        <text class="col-label">店铺公告</text>
        <textarea
          v-model="form.notice"
          class="notice-ta"
          :maxlength="200"
          placeholder="例如：今日新品上线 / 雨天配送可能延迟，请耐心等待"
        />
      </view>
      <view class="field">
        <text>接收消息推送</text>
        <switch :checked="form.pushEnabled === 1" color="#c2410c" @change="onPush" />
      </view>
      <view class="field">
        <text>配送范围（米）</text>
        <input v-model="form.deliveryRangeMeters" type="number" placeholder="留空表示不限" />
      </view>
      <view class="btn" @click="save">保存设置</view>
    </view>

    <view class="card">
      <view class="entry-row" @click="goFinance">
        <view>
          <view class="entry-title">资金与对账</view>
          <view class="muted mt8">可提现余额、营收统计、对账单与提现记录</view>
        </view>
        <text class="arrow">›</text>
      </view>
      <view class="entry-row" @click="goCoupons">
        <view>
          <view class="entry-title">店铺优惠券</view>
          <view class="muted mt8">创建满减/折扣券，仅本店可用</view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <AppTabBar :role="'MERCHANT'" />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { merchantApi, ensureMerchantApproved, type MerchantShop } from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const shop = ref<MerchantShop | null>(null)
const form = reactive<any>({
  openTime: '',
  minAmount: 0,
  packFee: 0,
  deliveryFee: 0,
  notice: '',
  pushEnabled: 0,
  deliveryRangeMeters: ''
})

const auditText = computed(() => ['待审核', '已通过', '已驳回', '已冻结'][shop.value?.auditStatus ?? 0])

async function load() {
  const s = await merchantApi.shop()
  shop.value = s
  form.openTime = s.openTime
  form.minAmount = s.minAmount
  form.packFee = s.packFee
  form.deliveryFee = s.deliveryFee
  form.notice = s.notice || ''
  form.pushEnabled = s.pushEnabled ?? 0
  form.deliveryRangeMeters = s.deliveryRangeMeters == null ? '' : String(s.deliveryRangeMeters)
}

async function toggle(e: any) {
  await merchantApi.updateShop({ bizStatus: e.detail.value ? 1 : 0 })
  load()
}

function onPush(e: any) {
  form.pushEnabled = e?.detail?.value ? 1 : 0
}

function goFinance() {
  uni.navigateTo({ url: '/pages/merchant/finance' })
}

function goCoupons() {
  uni.navigateTo({ url: '/pages/merchant/coupons' })
}

async function save() {
  // 配送范围：留空 = null（不限），否则必须是 >=0 的数字（米）
  const range = form.deliveryRangeMeters === '' ? null : Number(form.deliveryRangeMeters)
  if (range !== null && (!Number.isFinite(range) || range < 0)) {
    uni.showToast({ title: '配送范围请填不小于 0 的数字', icon: 'none' })
    return
  }
  await merchantApi.updateShop({
    openTime: form.openTime,
    minAmount: Number(form.minAmount),
    packFee: Number(form.packFee),
    deliveryFee: Number(form.deliveryFee),
    // PUT /api/merchant/shop 已支持 notice / pushEnabled / deliveryRangeMeters
    notice: form.notice || '',
    pushEnabled: form.pushEnabled === 1 ? 1 : 0,
    deliveryRangeMeters: range
  })
  uni.showToast({ title: '已保存' })
  load()
}

onShow(async () => {
  if (!guard('MERCHANT')) return
  if (!(await ensureMerchantApproved())) return
  load()
})
</script>

<style scoped>
.page { padding: 20rpx; }
.title { font-size: 34rpx; font-weight: 700; }
.field { display: flex; justify-content: space-between; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 26rpx; }
.field input { flex: 1; text-align: right; }
.field-col { padding: 20rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.col-label { font-size: 26rpx; }
.notice-ta {
  width: 100%; box-sizing: border-box; min-height: 140rpx; margin-top: 12rpx;
  background: #f7f8fa; border-radius: 12rpx; padding: 16rpx; font-size: 26rpx;
}
.btn { margin-top: 24rpx; }
.mt8 { margin-top: 8rpx; }
.entry-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20rpx 0; border-bottom: 1rpx solid #f7f8fa;
}
.entry-row:last-child { border-bottom: none; }
.entry-title { font-size: 28rpx; font-weight: 600; color: #c2410c; }
.arrow { color: #c3c7cc; }
</style>
