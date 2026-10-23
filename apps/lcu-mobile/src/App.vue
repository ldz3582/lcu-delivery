<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'

onLaunch(() => {
  // 登录态由 pages/launch/index 统一判断，这里不做自动登录，避免和页面守卫打架
  console.log('[lcu] app launch')
})
</script>

<style>
page {
  background: #f5f6f8;
  color: #1a1d21;
  font-size: 28rpx;
}
.container { padding: 24rpx; }
.card {
  background: #fff;
  border-radius: 20rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}
.row { display: flex; align-items: center; }
.between { display: flex; align-items: center; justify-content: space-between; }
.price { color: #c2410c; font-weight: 600; }
.muted { color: #7a828c; font-size: 24rpx; }
</style>
