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
      <view class="btn" @click="save">保存设置</view>
    </view>
    <AppTabBar :role="'MERCHANT'" />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { merchantApi, type MerchantShop } from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const shop = ref<MerchantShop | null>(null)
const form = reactive<any>({ openTime: '', minAmount: 0, packFee: 0, deliveryFee: 0 })

const auditText = computed(() => ['待审核', '已通过', '已驳回', '已冻结'][shop.value?.auditStatus ?? 0])

async function load() {
  const s = await merchantApi.shop()
  shop.value = s
  form.openTime = s.openTime
  form.minAmount = s.minAmount
  form.packFee = s.packFee
  form.deliveryFee = s.deliveryFee
}

async function toggle(e: any) {
  await merchantApi.updateShop({ bizStatus: e.detail.value ? 1 : 0 })
  load()
}

async function save() {
  await merchantApi.updateShop({
    openTime: form.openTime,
    minAmount: Number(form.minAmount),
    packFee: Number(form.packFee),
    deliveryFee: Number(form.deliveryFee)
  })
  uni.showToast({ title: '已保存' })
  load()
}

onShow(() => { if (!guard('MERCHANT')) return; load() })
</script>

<style scoped>
.page { padding: 20rpx; }
.title { font-size: 34rpx; font-weight: 700; }
.field { display: flex; justify-content: space-between; align-items: center; padding: 20rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 26rpx; }
.field input { flex: 1; text-align: right; }
.btn { margin-top: 24rpx; }
.mt8 { margin-top: 8rpx; }
</style>
