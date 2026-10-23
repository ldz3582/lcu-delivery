<!-- 自定义底部导航：一个页面一套，按当前身份切换。
     微信原生 tabBar 的页面是编译期写死的，做不到「用户/商家/骑手/管理员」四套导航，
     所以这里用普通页面 + reLaunch 自己实现。 -->
<template>
  <view>
    <view class="tabbar">
      <view
        v-for="it in items"
        :key="it.path"
        class="tb"
        :class="{ on: it.path === active }"
        @click="go(it.path)"
      >
        <text class="ico">{{ it.icon }}</text>
        <text class="txt">{{ it.text }}</text>
      </view>
    </view>
    <view class="tabbar-gap" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Role } from '../utils/auth'

interface TabItem { path: string; text: string; icon: string }

const props = defineProps<{ role: Role }>()

const TABS: Record<Role, TabItem[]> = {
  USER: [
    { path: '/pages/index/index', text: '首页', icon: '🏠' },
    { path: '/pages/cart/cart', text: '购物车', icon: '🛒' },
    { path: '/pages/order/list', text: '订单', icon: '🧾' },
    { path: '/pages/mine/index', text: '我的', icon: '👤' }
  ],
  MERCHANT: [
    { path: '/pages/merchant/orders', text: '接单', icon: '🔔' },
    { path: '/pages/merchant/products', text: '菜品', icon: '🍱' },
    { path: '/pages/merchant/shop', text: '店铺', icon: '🏪' },
    { path: '/pages/mine/index', text: '我的', icon: '👤' }
  ],
  RIDER: [
    { path: '/pages/rider/pool', text: '抢单', icon: '📣' },
    { path: '/pages/rider/orders', text: '配送', icon: '🛵' },
    { path: '/pages/rider/profile', text: '收益', icon: '💰' },
    { path: '/pages/mine/index', text: '我的', icon: '👤' }
  ],
}

const items = computed(() => TABS[props.role] || [])

const active = computed(() => {
  const pages = getCurrentPages()
  if (!pages.length) return ''
  return '/' + (pages[pages.length - 1] as any).route
})

function go(path: string) {
  if (path === active.value) return
  uni.reLaunch({ url: path })
}
</script>

<style scoped>
.tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  background: #fff;
  border-top: 1rpx solid #eceff3;
  padding-bottom: env(safe-area-inset-bottom);
}
.tb {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 12rpx 0 10rpx;
  color: #7a828c;
}
.tb.on { color: #c2410c; }
.ico { font-size: 38rpx; line-height: 1.1; }
.txt { font-size: 22rpx; margin-top: 4rpx; }
.tb.on .txt { font-weight: 600; }
.tabbar-gap { height: 130rpx; }
</style>
