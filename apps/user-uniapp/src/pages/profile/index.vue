<template>
  <view class="page">
    <view class="head">
      <view class="avatar">{{ (profile?.nickname || '同').slice(0, 1) }}</view>
      <view>
        <view class="nick">{{ profile?.nickname || '未登录' }}</view>
        <view class="muted">{{ profile?.phoneMask || '未绑定手机号' }}</view>
      </view>
    </view>

    <view class="card menu">
      <view class="item" @click="go('/pages/address/list')">
        <text>收货地址</text><text class="arrow">›</text>
      </view>
      <view class="item" @click="go('/pages/order/list')">
        <text>我的订单</text><text class="arrow">›</text>
      </view>
      <view class="item" @click="goWorkbench('MERCHANT')">
        <text>我是商家</text><text class="arrow">›</text>
      </view>
      <view class="item" @click="goWorkbench('RIDER')">
        <text>我是骑手</text><text class="arrow">›</text>
      </view>
    </view>

    <view class="card">
      <view class="muted">当前环境：本地联调（模拟登录 / 模拟支付）</view>
      <view class="muted mt8">切换到真实微信登录后，这里显示微信头像与手机号</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api } from '../../api'
import { ensureLogin } from '../../utils/auth'

const profile = ref<any>(null)

function go(url: string) {
  uni.navigateTo({ url, fail: () => uni.switchTab({ url }) })
}

function goWorkbench(role: 'MERCHANT' | 'RIDER') {
  // 工作台是另一个小程序，真实环境用 navigateToMiniProgram；本地先提示
  uni.showModal({
    title: '商家 / 骑手工作台',
    content: `请用微信开发者工具打开 workbench-uniapp（AppID 独立），切换为${role === 'MERCHANT' ? '商家' : '骑手'}身份登录。`,
    showCancel: false
  })
}

onShow(async () => {
  try {
    await ensureLogin()
    profile.value = await api.profile()
  } catch (e) {
    console.warn(e)
  }
})
</script>

<style scoped>
.page { padding: 20rpx; }
.head {
  display: flex; align-items: center; gap: 24rpx;
  background: #fff; border-radius: 20rpx; padding: 32rpx; margin-bottom: 20rpx;
}
.avatar {
  width: 100rpx; height: 100rpx; border-radius: 50%;
  background: #fdf2ea; color: #c2410c;
  display: flex; align-items: center; justify-content: center;
  font-size: 40rpx; font-weight: 700;
}
.nick { font-size: 34rpx; font-weight: 600; }
.menu .item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 26rpx 4rpx; border-bottom: 1rpx solid #f2f3f5;
}
.menu .item:last-child { border-bottom: none; }
.arrow { color: #c3c7cc; }
.mt8 { margin-top: 8rpx; }
</style>
