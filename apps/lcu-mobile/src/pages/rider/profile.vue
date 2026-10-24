<template>
  <view class="page">
    <view class="card head">
      <view class="avatar">骑</view>
      <view>
        <view class="name">骑手 {{ profile?.phoneMask || '' }}</view>
        <view class="muted">审核：{{ auditText }} · 评分 {{ profile?.score || '-' }}</view>
      </view>
    </view>

    <view class="stats">
      <view class="stat"><view class="num">{{ profile?.totalOrders || 0 }}</view><view class="muted">累计接单</view></view>
      <view class="stat"><view class="num">{{ profile?.maxConcurrent || 3 }}</view><view class="muted">同时上限</view></view>
      <view class="stat"><view class="num">{{ profile?.workStatus === 1 ? '在线' : '离线' }}</view><view class="muted">接单状态</view></view>
    </view>

    <view class="card">
      <view class="muted">收益结算：订单完成即生成结算单，T+1 冻结期满自动入钱包（见后端 SettleJob）。</view>
    </view>

    <view class="btn gray" @click="logout">退出登录</view>
    <AppTabBar :role="'RIDER'" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { riderApi } from '../../api'
import { guard, logout } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const profile = ref<any>(null)
const auditText = computed(() => ['待审核', '已通过', '已驳回'][profile.value?.auditStatus ?? 0])

onShow(async () => {
  if (!guard('RIDER')) return
  try { profile.value = await riderApi.profile() } catch (e) { console.warn(e) }
})
</script>

<style scoped>
.page { padding: 20rpx; }
.head { display: flex; align-items: center; gap: 24rpx; }
.avatar {
  width: 96rpx; height: 96rpx; border-radius: 50%; background: #eff6ff; color: #1d4ed8;
  display: flex; align-items: center; justify-content: center; font-size: 38rpx; font-weight: 700;
}
.name { font-size: 32rpx; font-weight: 600; }
.stats { display: flex; gap: 16rpx; margin-bottom: 20rpx; }
.stat { flex: 1; background: #fff; border-radius: 20rpx; padding: 24rpx; text-align: center; }
.num { font-size: 34rpx; font-weight: 700; color: #c2410c; }
.btn.gray { margin-top: 30rpx; }
</style>
