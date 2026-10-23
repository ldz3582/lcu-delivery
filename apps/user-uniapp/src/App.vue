<script setup lang="ts">
import { onLaunch } from '@dcloudio/uni-app'
import { ensureLogin } from './utils/auth'

onLaunch(async () => {
  try {
    await ensureLogin()
  } catch (e) {
    console.warn('登录失败', e)
  }
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
.btn-primary {
  background: #c2410c;
  color: #fff;
  border-radius: 44rpx;
  text-align: center;
  padding: 22rpx 0;
  font-size: 30rpx;
}
.btn-ghost {
  border: 2rpx solid #e5e7eb;
  border-radius: 44rpx;
  text-align: center;
  padding: 20rpx 0;
  color: #1a1d21;
}
</style>
