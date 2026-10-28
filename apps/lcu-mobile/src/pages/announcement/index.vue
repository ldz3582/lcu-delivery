<template>
  <view class="page">
    <view v-if="loading" class="state muted">加载中…</view>

    <block v-else>
      <view v-for="a in list" :key="a.id" class="card">
        <view class="between">
          <text class="title">{{ a.title }}</text>
          <text v-if="a.createdAt" class="muted time">{{ dt(a.createdAt) }}</text>
        </view>
        <view class="content">{{ a.content }}</view>
      </view>

      <view v-if="!list.length" class="empty">
        <view class="empty-icon">📢</view>
        <view>暂无公告</view>
      </view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { announcementApi, type Announcement } from '../../api'

const list = ref<Announcement[]>([])
const loading = ref(false)

/** 后端可能是 '2026-10-28 10:00:00' 或 ISO，统一截到分钟展示 */
function dt(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  loading.value = true
  try {
    list.value = (await announcementApi.list()) || []
  } catch (e) {
    // 公开接口失败按「暂无公告」降级，不弹错误
    list.value = []
  } finally {
    loading.value = false
  }
}

onShow(load)

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; }
.title { flex: 1; padding-right: 16rpx; font-size: 30rpx; font-weight: 600; }
.time { flex-shrink: 0; }
.content {
  margin-top: 14rpx; color: #4b5563; font-size: 26rpx;
  line-height: 1.7; white-space: pre-wrap; word-break: break-all;
}
.state { text-align: center; padding: 80rpx 0; }
.empty { text-align: center; color: #9ca3af; padding: 160rpx 0; font-size: 28rpx; }
.empty-icon { font-size: 68rpx; margin-bottom: 18rpx; }
</style>
