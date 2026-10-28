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

    <!-- 活动 Banner：GET /api/banners；无数据时整块不渲染 -->
    <view v-if="banners.length" class="banner-wrap">
      <swiper
        class="banner"
        circular
        autoplay
        :interval="4000"
        :duration="300"
        indicator-dots
        indicator-color="rgba(255,255,255,0.5)"
        indicator-active-color="#ffffff"
      >
        <swiper-item v-for="b in banners" :key="b.id" @click="goBanner(b)">
          <image :src="b.image" mode="aspectFill" class="banner-img" />
        </swiper-item>
      </swiper>
    </view>

    <view class="coupon-bar" @click="goCoupons">
      <text class="cb-icon">🎟️</text>
      <text class="cb-text">领券中心 · 先领券再下单更划算</text>
      <text class="cb-arrow">去看看 ›</text>
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

    <!-- 限时特价专区：GET /api/specials；无数据时整块不渲染 -->
    <view v-if="specials.length" class="special-sec">
      <view class="special-head">
        <text class="special-title">⚡ 限时特价</text>
        <text class="special-sub">手慢无 · 折扣由低到高</text>
      </view>
      <scroll-view class="special-scroll" scroll-x>
        <view v-for="s in specials" :key="s.productId" class="special-card" @click="goShop(s.shopId)">
          <view class="special-thumb">
            <image v-if="s.image" :src="s.image" mode="aspectFill" class="special-img" />
            <text v-else class="special-letter">{{ (s.name || '').slice(0, 1) }}</text>
            <text v-if="discountText(s)" class="discount-tag">{{ discountText(s) }}</text>
          </view>
          <view class="special-name">{{ s.name }}</view>
          <view class="special-price-row">
            <text class="special-price">{{ fen(s.specialPrice) }}</text>
            <text v-if="s.price > s.specialPrice" class="special-origin">{{ fen(s.price) }}</text>
          </view>
          <view v-if="s.shopName" class="special-shop">{{ s.shopName }}</view>
        </view>
      </scroll-view>
    </view>

    <view class="list">
      <view v-for="s in shops" :key="s.id" class="card shop" @click="goShop(s.id)">
        <view class="logo">{{ s.name.slice(0, 1) }}</view>
        <view class="info">
          <view class="between">
            <text class="name">{{ s.name }}</text>
            <text v-if="distanceLine(s)" class="dist">{{ distanceLine(s) }}</text>
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
    <AppTabBar :role="'USER'" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import { api, type Banner, type Category, type ShopCard, type SpecialItem } from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const categories = ref<Category[]>([])
const shops = ref<ShopCard[]>([])
/** 首页 Banner（GET /api/banners）：只保留有图的，空数组时不渲染区块 */
const banners = ref<Banner[]>([])
/** 限时特价（GET /api/specials）：后端只返回此刻生效的，空数组时不渲染区块 */
const specials = ref<SpecialItem[]>([])
const activeCat = ref<number | null>(null)
const keyword = ref('')
const campus = ref(0)
const loading = ref(false)
/** 当前定位（gcj02）；为空时按校区+销量筛选，不影响正常浏览 */
const myLoc = ref<{ lng: number; lat: number } | null>(null)
let locTried = false
let locBusy = false

const CAMPUS_LABELS = ['全部校区', '东校区', '西校区']
const campusName = computed(() => CAMPUS_LABELS[campus.value] || CAMPUS_LABELS[0])

function fen(v: number) {
  return (v / 100).toFixed(2) + ' 元'
}

/** 折扣标签：discountPercent=85 → 8.5 折；>=100（没折扣）不显示 */
function discountText(s: SpecialItem) {
  const p = Number(s.discountPercent)
  if (!Number.isFinite(p) || p <= 0 || p >= 100) return ''
  const d = p / 10
  return `${Number.isInteger(d) ? d : d.toFixed(1)} 折`
}

/** Banner 跳转：1 不跳 / 2 店铺详情 / 3 领券中心 */
function goBanner(b: Banner) {
  if (b.linkType === 2 && b.linkValue !== null && b.linkValue !== undefined && b.linkValue !== '') {
    uni.navigateTo({ url: `/pages/shop/detail?id=${b.linkValue}` })
  } else if (b.linkType === 3) {
    uni.navigateTo({ url: '/pages/coupon/index' })
  }
}

/** Banner / 特价都是公开接口，失败时静默留空（区块自然隐藏），不影响店铺列表 */
async function loadBanners() {
  try {
    const list = (await api.banners()) || []
    banners.value = list.filter((b) => b && !!b.image)
  } catch (e) {
    banners.value = []
  }
}

async function loadSpecials() {
  try {
    const list = (await api.specials(10)) || []
    specials.value = list.filter((s) => s && s.productId && Number(s.specialPrice) > 0)
  } catch (e) {
    specials.value = []
  }
}

/** 卡片右上角：距离 + 预计送达。etaMinutes 由后端返回，旧数据按 250m/分钟兜底 */
function distanceLine(s: ShopCard) {
  const d = Number(s.distance)
  const hasDistance = typeof s.distance === 'number' && Number.isFinite(d)
  const parts: string[] = []
  if (hasDistance) {
    parts.push(d >= 1000 ? `距离 ${(d / 1000).toFixed(1)}km` : `距离 ${Math.round(d)}m`)
  }
  // 休息中不承诺送达时间：只显示距离 + 休息中
  if (Number(s.bizStatus) !== 1) {
    parts.push('休息中')
    return parts.join(' · ')
  }
  const eta = Number(s.etaMinutes)
  const minutes = Number.isFinite(eta) && eta > 0
    ? Math.round(eta)
    : hasDistance ? Math.max(1, Math.ceil(d / 250)) : 0
  if (minutes > 0) parts.push(`约 ${minutes} 分钟`)
  return parts.join(' · ')
}

async function load() {
  loading.value = true
  try {
    const res = await api.shops({
      campus: campus.value || undefined,
      categoryId: activeCat.value || undefined,
      keyword: keyword.value || undefined,
      lng: myLoc.value?.lng,
      lat: myLoc.value?.lat,
      page: 1,
      size: 20
    })
    shops.value = res.list || []
  } finally {
    loading.value = false
  }
}

/** H5/小程序都可能因权限或非 https 失败：失败静默返回 null，页面继续按校区筛选 */
function getLocationSafe(): Promise<{ lng: number; lat: number } | null> {
  return new Promise((resolve) => {
    try {
      uni.getLocation({
        type: 'gcj02',
        success: (res: any) => {
          const lng = Number(res?.longitude)
          const lat = Number(res?.latitude)
          resolve(Number.isFinite(lng) && Number.isFinite(lat) ? { lng, lat } : null)
        },
        fail: () => resolve(null)
      })
    } catch (e) {
      resolve(null)
    }
  })
}

async function loadLocation() {
  if (locBusy) return
  locBusy = true
  try {
    const p = await getLocationSafe()
    if (!p) return
    myLoc.value = p
    // 拿到坐标后重查一次：后端会返回 distance / etaMinutes 并按距离排序
    await load()
  } catch (e) {
    console.warn(e)
  } finally {
    locBusy = false
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

function goCoupons() {
  uni.navigateTo({ url: '/pages/coupon/index' })
}

onShow(async () => {
  if (!guard('USER')) return
  if (!categories.value.length) {
    categories.value = await api.categories()
  }
  // Banner / 特价专区跟着首页刷新：运营改了 Banner 或商家新开特价，回来就能看到
  loadBanners()
  loadSpecials()
  // 先按校区出结果，定位成功后再带着坐标重查，避免定位权限弹窗阻塞首屏
  load()
  if (!myLoc.value && !locTried) {
    locTried = true
    loadLocation()
  }
})

onPullDownRefresh(async () => {
  await Promise.all([load(), loadBanners(), loadSpecials()])
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
.coupon-bar {
  display: flex; align-items: center; gap: 12rpx;
  margin: 16rpx 20rpx 0;
  background: #fff7ed; border: 1rpx solid #fed7aa;
  border-radius: 16rpx; padding: 18rpx 22rpx;
}
.cb-icon { font-size: 28rpx; }
.cb-text { flex: 1; font-size: 24rpx; color: #c2410c; }
.cb-arrow { font-size: 22rpx; color: #ea580c; }
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
.info { flex: 1; min-width: 0; }
.name { font-size: 30rpx; font-weight: 600; }
.dist { flex-shrink: 0; margin-left: 10rpx; font-size: 22rpx; color: #6b7280; }
.meta { margin-top: 8rpx; }
.ship { color: #c2410c; font-size: 24rpx; }
.closed {
  position: absolute; right: 24rpx; top: 24rpx;
  background: #e5e7eb; color: #6b7280; font-size: 22rpx;
  padding: 4rpx 14rpx; border-radius: 20rpx;
}
.empty { text-align: center; color: #9ca3af; padding: 80rpx 0; font-size: 26rpx; }

/* 活动 Banner 轮播 */
.banner-wrap { padding: 16rpx 20rpx 0; }
.banner { height: 140rpx; border-radius: 16rpx; overflow: hidden; }
.banner-img { width: 100%; height: 140rpx; border-radius: 16rpx; background: #f3f4f6; }

/* 限时特价专区 */
.special-sec { background: #fff; padding: 20rpx 0 16rpx; margin-top: 16rpx; }
.special-head {
  display: flex; align-items: baseline; gap: 12rpx;
  padding: 0 20rpx 12rpx;
}
.special-title { font-size: 30rpx; font-weight: 700; color: #c2410c; }
.special-sub { font-size: 22rpx; color: #9ca3af; }
.special-scroll { white-space: nowrap; padding: 0 12rpx; }
.special-card {
  display: inline-block; vertical-align: top;
  width: 220rpx; margin: 0 8rpx;
  background: #fff7ed; border: 1rpx solid #fed7aa; border-radius: 16rpx;
  padding: 12rpx;
}
.special-thumb {
  position: relative; width: 100%; height: 150rpx;
  border-radius: 12rpx; overflow: hidden; background: #f3f4f6;
  display: flex; align-items: center; justify-content: center;
}
.special-img { width: 100%; height: 150rpx; }
.special-letter { font-size: 40rpx; color: #9ca3af; }
.discount-tag {
  position: absolute; left: 0; top: 0;
  background: #dc2626; color: #fff; font-size: 20rpx;
  padding: 2rpx 10rpx; border-radius: 0 0 12rpx 0;
}
.special-name {
  margin-top: 10rpx; font-size: 26rpx; color: #1a1d21;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.special-price-row { margin-top: 6rpx; display: flex; align-items: baseline; gap: 8rpx; }
.special-price { color: #dc2626; font-weight: 700; font-size: 28rpx; }
.special-origin {
  color: #9ca3af; font-size: 20rpx;
  text-decoration: line-through;
}
.special-shop {
  margin-top: 4rpx; font-size: 20rpx; color: #9ca3af;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
</style>
