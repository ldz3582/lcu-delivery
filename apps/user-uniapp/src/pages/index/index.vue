<template>
  <view class="page">
    <view class="hero">
      <view class="between">
        <view>
          <view class="hero-title">聊大外卖</view>
          <view class="hero-sub">校内食堂直达 · 学生骑手配送</view>
        </view>
        <view class="campus" @click="switchCampus">{{ campusName }}</view>
      </view>
      <view class="search">
        <input v-model="keyword" placeholder="搜店铺、菜品" confirm-type="search" @confirm="load" />
        <text class="search-btn" @click="load">搜索</text>
      </view>
    </view>

    <scroll-view class="cats" scroll-x>
      <view :class="['cat', activeCat === null ? 'active' : '']" @click="pickCategory(null)">全部</view>
      <view
        v-for="c in categories"
        :key="c.id"
        :class="['cat', activeCat === c.id ? 'active' : '']"
        @click="pickCategory(c.id)"
      >{{ c.name }}</view>
    </scroll-view>

    <view class="list">
      <view v-for="s in shops" :key="s.id" class="card shop" @click="goShop(s.id)">
        <view class="logo">{{ s.name.slice(0, 1) }}</view>
        <view class="info">
          <view class="between">
            <text class="name">{{ s.name }}</text>
            <text v-if="s.distance !== undefined" class="muted">{{ s.distance }}m</text>
          </view>
          <view class="muted">评分 {{ s.score }} · 月售 {{ s.monthSales }} · 起送 {{ fen(s.minAmount) }}</view>
          <view class="between meta">
            <text class="muted">{{ s.openTime || '营业中' }}</text>
            <text class="ship">配送 {{ fen(s.deliveryFee) }}</text>
          </view>
        </view>
        <view v-if="s.bizStatus !== 1" class="closed">休息中</view>
      </view>
      <view v-if="!shops.length && !loading" class="empty">这个分类下还没有店铺</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { api, type Category, type ShopCard } from '../../api'
import { ensureLogin } from '../../utils/auth'

const categories = ref<Category[]>([])
const shops = ref<ShopCard[]>([])
const activeCat = ref<number | null>(null)
const keyword = ref('')
const campus = ref(0)
const loading = ref(false)

const CAMPUS_LABELS = ['全部校区', '东校区', '西校区']
const campusName = computed(() => CAMPUS_LABELS[campus.value] || CAMPUS_LABELS[0])

function fen(v: number) {
  return (v / 100).toFixed(2) + ' 元'
}

async function load() {
  loading.value = true
  try {
    const res = await api.shops({
      campus: campus.value || undefined,
      categoryId: activeCat.value || undefined,
      keyword: keyword.value || undefined,
      page: 1,
      size: 20
    })
    shops.value = res.list || []
  } finally {
    loading.value = false
  }
}

function pickCategory(id: number | null) {
  activeCat.value = id
  load()
}

function switchCampus() {
  uni.showActionSheet({
    itemList: ['全部校区', '东校区', '西校区'],
    success: (r) => {
      campus.value = [0, 1, 2][r.tapIndex]
      load()
    }
  })
}

function goShop(id: number) {
  uni.navigateTo({ url: `/pages/shop/detail?id=${id}` })
}

onShow(async () => {
  await ensureLogin()
  if (!categories.value.length) {
    categories.value = await api.categories()
  }
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.hero {
  background: linear-gradient(135deg, #c2410c, #ea580c);
  padding: 28rpx 28rpx 36rpx;
  color: #fff;
}
.hero-title { font-size: 44rpx; font-weight: 700; }
.hero-sub { font-size: 24rpx; opacity: 0.9; margin-top: 6rpx; }
.campus {
  background: rgba(255, 255, 255, 0.18);
  padding: 10rpx 20rpx;
  border-radius: 30rpx;
  font-size: 24rpx;
}
.search {
  margin-top: 24rpx;
  background: #fff;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  padding: 0 24rpx;
  height: 76rpx;
}
.search input { flex: 1; font-size: 28rpx; }
.search-btn { color: #c2410c; font-size: 28rpx; padding-left: 16rpx; }
.cats { white-space: nowrap; padding: 20rpx 16rpx 4rpx; background: #fff; }
.cat {
  display: inline-block;
  padding: 12rpx 28rpx;
  margin-right: 16rpx;
  border-radius: 30rpx;
  background: #f3f4f6;
  color: #4b5563;
  font-size: 26rpx;
}
.cat.active { background: #fdf2ea; color: #c2410c; font-weight: 600; }
.list { padding: 20rpx; }
.shop { display: flex; position: relative; }
.logo {
  width: 96rpx; height: 96rpx; border-radius: 20rpx;
  background: #fdf2ea; color: #c2410c;
  display: flex; align-items: center; justify-content: center;
  font-size: 34rpx; font-weight: 700; margin-right: 20rpx;
}
.info { flex: 1; }
.name { font-size: 30rpx; font-weight: 600; }
.meta { margin-top: 8rpx; }
.ship { color: #c2410c; font-size: 24rpx; }
.closed {
  position: absolute; right: 24rpx; top: 24rpx;
  background: #e5e7eb; color: #6b7280; font-size: 22rpx;
  padding: 4rpx 14rpx; border-radius: 20rpx;
}
.empty { text-align: center; color: #9ca3af; padding: 80rpx 0; font-size: 26rpx; }
</style>
