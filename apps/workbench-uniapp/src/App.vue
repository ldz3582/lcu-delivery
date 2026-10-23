<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { currentToken } from './utils/auth'

onLaunch(() => {
  if (!currentToken()) {
    uni.reLaunch({ url: '/pages/login/login' })
  }
})
</script>

<style>
page {
  background: #f5f6f8;
  color: #1a1d21;
  font-size: 28rpx;
}
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.muted { color: #7a828c; font-size: 24rpx; }
.price { color: #c2410c; font-weight: 600; }
.btn { background: #c2410c; color: #fff; text-align: center; padding: 20rpx; border-radius: 12rpx; font-size: 28rpx; }
.btn.ghost { background: #fff; color: #c2410c; border: 2rpx solid #c2410c; }
.btn.gray { background: #eef0f3; color: #374151; }
.row { display: flex; gap: 16rpx; margin-top: 18rpx; }
.row .btn { flex: 1; }
</style>
