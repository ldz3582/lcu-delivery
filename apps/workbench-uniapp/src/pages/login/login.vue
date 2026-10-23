<template>
  <view class="page">
    <view class="brand">
      <view class="logo">聊</view>
      <view class="title">聊大外卖工作台</view>
      <view class="muted">商家接单 · 骑手配送</view>
    </view>

    <view class="card">
      <view class="tabs">
        <view :class="['tab', role === 'MERCHANT' ? 'on' : '']" @click="role = 'MERCHANT'">商家</view>
        <view :class="['tab', role === 'RIDER' ? 'on' : '']" @click="role = 'RIDER'">骑手</view>
      </view>
      <view class="field">
        <text class="label">登录账号（openid）</text>
        <input v-model="openid" placeholder="本地联调可用 demo 账号" />
      </view>
      <view class="muted tip">微信内打开会优先走真实微信登录；本地/浏览器环境自动回退到模拟登录</view>
      <view class="submit" @click="doWxLogin">微信一键登录</view>
      <view class="submit ghost" @click="doLogin">本地测试账号登录</view>
    </view>

    <view class="muted foot">演示账号：商家 mock_merchant / 骑手 mock_rider</view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { login, loginByWechat } from '../../utils/auth'

const role = ref<'MERCHANT' | 'RIDER'>('MERCHANT')
const openid = ref('mock_merchant')
const loading = ref(false)

watch(role, (r) => {
  openid.value = r === 'MERCHANT' ? 'mock_merchant' : 'mock_rider'
})

async function doWxLogin() {
  if (loading.value) return
  loading.value = true
  try {
    await loginByWechat(role.value)
    uni.reLaunch({ url: role.value === 'MERCHANT' ? '/pages/merchant/orders' : '/pages/rider/pool' })
  } catch (e: any) {
    uni.showModal({ title: '微信登录不可用', content: (e?.message || '请改用下方本地测试账号') + '\n\n（未配置 AppID 时会这样，详见 docs/接入微信公众平台.md）', showCancel: false })
  } finally {
    loading.value = false
  }
}

async function doLogin() {
  if (loading.value) return
  loading.value = true
  try {
    await login(role.value, openid.value.trim())
    uni.reLaunch({ url: role.value === 'MERCHANT' ? '/pages/merchant/orders' : '/pages/rider/pool' })
  } catch (e) {
    console.warn(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 80rpx 32rpx; }
.brand { text-align: center; margin-bottom: 50rpx; }
.logo {
  width: 120rpx; height: 120rpx; border-radius: 28rpx; margin: 0 auto 20rpx;
  background: #c2410c; color: #fff; font-size: 56rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.title { font-size: 40rpx; font-weight: 700; }
.tabs { display: flex; background: #f3f4f6; border-radius: 30rpx; padding: 6rpx; margin-bottom: 24rpx; }
.tab { flex: 1; text-align: center; padding: 14rpx; border-radius: 26rpx; font-size: 28rpx; color: #4b5563; }
.tab.on { background: #fff; color: #c2410c; font-weight: 600; }
.field { display: flex; align-items: center; justify-content: space-between; padding: 20rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.label { font-size: 26rpx; color: #4b5563; }
.field input { flex: 1; text-align: right; }
.tip { margin-top: 16rpx; }
.submit { margin-top: 24rpx; background: #c2410c; color: #fff; text-align: center; padding: 24rpx; border-radius: 44rpx; }
.submit.ghost { background: #fff; color: #c2410c; border: 2rpx solid #c2410c; }
.foot { text-align: center; margin-top: 40rpx; }
</style>
