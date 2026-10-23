<template>
  <view class="launch">
    <view class="logo">聊</view>
    <view class="name">聊大外卖</view>
    <view class="muted">正在进入…</view>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { currentRole, hasLogin, HOME } from '../../utils/auth'

onLoad(() => {
  const role = currentRole()
  if (hasLogin() && role && HOME[role]) {
    uni.reLaunch({ url: HOME[role] })
  } else {
    uni.reLaunch({ url: '/pages/login/index' })
  }
})
</script>

<style scoped>
.launch {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f6f8;
}
.logo {
  width: 140rpx; height: 140rpx; border-radius: 32rpx;
  background: #c2410c; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 64rpx; font-weight: 700;
}
.name { margin-top: 24rpx; font-size: 36rpx; font-weight: 700; }
.muted { margin-top: 12rpx; }
</style>
