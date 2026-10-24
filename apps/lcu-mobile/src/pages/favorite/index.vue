<template>
  <view class="page">
    <view v-if="loading" class="muted loading">加载中…</view>

    <view v-else-if="list.length" class="list">
      <view v-for="s in list" :key="s.id" class="card shop" @click="goShop(s.id)">
        <view class="logo">{{ s.name.slice(0, 1) }}</view>
        <view class="info">
          <view class="name">{{ s.name }}</view>
          <view class="muted">评分 {{ s.score }} · 月售 {{ s.monthSales }}</view>
          <view class="between meta">
            <text class="muted">起送 {{ fen(s.minAmount) }} · 配送 {{ fen(s.deliveryFee) }}</text>
            <text v-if="s.bizStatus !== 1" class="closed">休息中</text>
          </view>
        </view>
        <view class="ops">
          <view class="unfav" @click.stop="unfavorite(s)">取消收藏</view>
          <text class="muted fav-time">{{ dateText(s.favoritedAt) }}</text>
        </view>
      </view>
    </view>

    <view v-else class="empty">
      <view class="empty-text">还没有收藏的店铺，去逛逛</view>
      <view class="go" @click="goHome">去逛逛</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { favoriteApi, type FavoriteShop } from '../../api'
import { guard } from '../../utils/auth'

const list = ref<FavoriteShop[]>([])
const loading = ref(false)

function fen(v: number) {
  return (Number(v || 0) / 100).toFixed(2) + ' 元'
}

/** favoritedAt 是 ISO 时间串，列表只显示日期 */
function dateText(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 10) : ''
}

async function load() {
  loading.value = true
  try {
    list.value = (await favoriteApi.list()) || []
  } catch (e) {
    list.value = []
  } finally {
    loading.value = false
  }
}

function goShop(id: number) {
  uni.navigateTo({ url: `/pages/shop/detail?id=${id}` })
}

function goHome() {
  uni.reLaunch({ url: '/pages/index/index' })
}

async function unfavorite(s: FavoriteShop) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '取消收藏',
      content: `不再收藏「${s.name}」？`,
      success: (r) => resolve(!!r.confirm)
    })
  })
  if (!ok) return
  try {
    await favoriteApi.remove(s.id)
    list.value = list.value.filter((x) => x.id !== s.id)
    uni.showToast({ title: '已取消收藏', icon: 'none' })
  } catch (e: any) {
    uni.showToast({ title: String(e?.message || '操作失败，请稍后再试'), icon: 'none' })
  }
}

onShow(async () => {
  if (!guard('USER')) return
  await load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; background: #f5f6f8; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.muted { color: #7a828c; font-size: 24rpx; }

.shop { display: flex; align-items: center; }
.logo {
  width: 96rpx; height: 96rpx; border-radius: 20rpx;
  background: #fdf2ea; color: #c2410c;
  display: flex; align-items: center; justify-content: center;
  font-size: 34rpx; font-weight: 700; margin-right: 20rpx; flex-shrink: 0;
}
.info { flex: 1; min-width: 0; }
.name { font-size: 30rpx; font-weight: 600; margin-bottom: 6rpx; }
.meta { margin-top: 8rpx; }
.closed {
  background: #e5e7eb; color: #6b7280; font-size: 22rpx;
  padding: 4rpx 14rpx; border-radius: 20rpx;
}

.ops {
  width: 160rpx; flex-shrink: 0; margin-left: 12rpx;
  display: flex; flex-direction: column; align-items: flex-end; gap: 12rpx;
}
.unfav {
  font-size: 22rpx; color: #c2410c; border: 1rpx solid #f0b79c;
  background: #fdf2ea; border-radius: 24rpx; padding: 8rpx 18rpx;
}
.fav-time { font-size: 20rpx; }

.empty { text-align: center; padding: 160rpx 0; color: #9ca3af; }
.empty-text { font-size: 26rpx; }
.go {
  display: inline-block; margin-top: 30rpx; color: #c2410c;
  border: 1rpx solid #f0b79c; background: #fdf2ea;
  border-radius: 40rpx; padding: 14rpx 46rpx; font-size: 26rpx;
}
.loading { text-align: center; padding: 60rpx 0; }
</style>
