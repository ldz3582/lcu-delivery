<template>
  <view class="page">
    <view v-if="shop" class="head">
      <view class="between">
        <view class="title">{{ shop.name }}</view>
        <view :class="['fav', favorited ? 'on' : '']" @click="toggleFav">
          {{ favorited ? '❤ 已收藏' : '♡ 收藏' }}
        </view>
      </view>
      <view class="muted">{{ shop.address }}</view>
      <view class="muted">起送 {{ fen(shop.minAmount) }} · 配送 {{ fen(shop.deliveryFee) }} · 营业 {{ shop.openTime }}</view>
      <view v-if="shop.deliveryRangeMeters" class="muted">配送范围 约 {{ rangeText(shop.deliveryRangeMeters) }}</view>
      <view v-if="shop.notice" class="notice">📢 {{ shop.notice }}</view>
    </view>

    <!-- 本店优惠：GET /api/shops/{id} 的 coupons（本店券 + 全场券），空数组时整块隐藏 -->
    <view v-if="coupons.length" class="coupon-sec">
      <view class="coupon-head">
        <text class="coupon-title">本店优惠</text>
        <text class="muted">点击领取</text>
      </view>
      <scroll-view class="coupon-scroll" scroll-x>
        <view
          v-for="c in coupons"
          :key="c.id"
          :class="['shop-coupon', c.claimed ? 'claimed' : '']"
          @click="claim(c)"
        >
          <text class="coupon-label">{{ c.label || c.name }}</text>
          <text class="coupon-state">{{ c.claimed ? '已领取' : (claimingId === c.id ? '领取中…' : '领取') }}</text>
        </view>
      </scroll-view>
    </view>

    <view class="products">
      <view v-for="p in shop?.products || []" :key="p.id" class="card item">
        <view class="thumb">
          <image v-if="p.image" :src="p.image" mode="aspectFill" class="thumb-img" />
          <text v-else class="thumb-text">{{ p.name.slice(0, 1) }}</text>
        </view>

        <view class="info">
          <view class="name-row">
            <text class="name">{{ p.name }}</text>
            <text v-if="isCombo(p)" class="combo-tag">套餐</text>
          </view>

          <view v-if="p.description" class="muted">{{ p.description }}</view>

          <view v-if="isCombo(p)" class="combo">
            <text class="combo-toggle" @click="toggleCombo(p.id)">
              {{ expanded[p.id] ? '收起套餐组成' : '查看套餐组成' }}
            </text>
            <view v-if="expanded[p.id]" class="combo-list">
              <view v-for="(c, i) in p.comboItems" :key="i" class="combo-item">
                · {{ c.name }} × {{ c.qty }}
              </view>
            </view>
          </view>

          <view class="between price-row">
            <view class="price-group">
              <text v-if="p.specialActive" class="special-tag">限时特价</text>
              <text class="price">{{ fen(priceOf(p)) }}</text>
              <text v-if="p.specialActive" class="origin">{{ fen(p.price) }}</text>
              <text v-else-if="showOrigin(p)" class="origin">{{ fen(p.originPrice) }}</text>
              <text v-if="p.hasSku === 1" class="from">起</text>
            </view>
            <view class="add" @click="onAdd(p)">+</view>
          </view>
          <view v-if="p.specialActive && specialTimeText(p)" class="special-time">
            {{ specialTimeText(p) }}
          </view>
        </view>
      </view>
    </view>

    <view class="bar" v-if="cart.count">
      <view class="bar-left">
        <text class="badge">{{ cart.count }}</text>
        <text class="price">{{ (cart.goodsAmount / 100).toFixed(2) }} 元</text>
      </view>
      <view class="bar-btn" @click="toCart">去结算</view>
    </view>

    <!-- 规格选择弹层 -->
    <view v-if="skuProduct" class="mask" @click="closeSku">
      <view class="sheet" @click.stop>
        <view class="between sheet-head">
          <text class="sheet-title">{{ skuProduct.name }}</text>
          <text class="muted" @click="closeSku">关闭</text>
        </view>
        <view class="muted">请选择规格</view>
        <view class="sku-list">
          <view
            v-for="s in skuProduct.skus || []"
            :key="s.id"
            :class="['sku', soldOut(s) ? 'disabled' : '']"
            @click="pickSku(s)"
          >
            <text class="sku-name">{{ s.name }}</text>
            <text class="sku-price">{{ fen(s.price) }}</text>
            <text v-if="soldOut(s)" class="sku-out">售罄</text>
          </view>
        </view>
        <view v-if="!(skuProduct.skus || []).length" class="muted empty-sku">该商品暂未配置规格</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api, couponApi, favoriteApi, type Product, type ProductSku, type ShopCoupon, type ShopDetail } from '../../api'
import { useCartStore } from '../../stores/cart'

const shop = ref<ShopDetail | null>(null)
const cart = useCartStore()
const favorited = ref(false)
const favBusy = ref(false)
/** 本店可领券；shop.coupons 为空时模板整块隐藏 */
const coupons = computed(() => shop.value?.coupons || [])
const claimingId = ref(0)
/** 正在选规格的商品 */
const skuProduct = ref<Product | null>(null)
/** 套餐组成展开状态 */
const expanded = reactive<Record<number, boolean>>({})

let shopId = 0

function fen(v?: number | null) {
  return (Number(v || 0) / 100).toFixed(2) + ' 元'
}

/** deliveryRangeMeters 单位为米，空值不展示 */
function rangeText(m: number) {
  const n = Number(m)
  if (!Number.isFinite(n) || n <= 0) return ''
  return n >= 1000 ? `${(n / 1000).toFixed(1)} km` : `${Math.round(n)} m`
}

function isCombo(p: Product) {
  return Array.isArray(p.comboItems) && p.comboItems.length > 0
}

function showOrigin(p: Product) {
  return typeof p.originPrice === 'number' && p.originPrice > p.price
}

/** 特价生效时成交价用 priceNow（缺字段时退回 price），加购也只传 productId，最终价由后端算 */
function priceOf(p: Product) {
  if (p.specialActive && typeof p.priceNow === 'number' && p.priceNow > 0) return p.priceNow
  return p.price
}

/** 特价截止时间：ISO '2026-10-25T18:00:00' → '特价至 10-25 18:00' */
function specialTimeText(p: Product) {
  const raw = p.specialEndAt
  if (!raw) return ''
  const s = String(raw)
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  if (m) return `特价至 ${m[2]}-${m[3]} ${m[4]}:${m[5]}`
  return ''
}

function toggleCombo(id: number) {
  expanded[id] = !expanded[id]
}

function soldOut(s: ProductSku) {
  return typeof s.stock === 'number' && s.stock === 0
}

onLoad(async (q: any) => {
  shopId = Number(q?.id || 1)
  shop.value = await api.shop(shopId)
  uni.setNavigationBarTitle({ title: shop.value.name })
})

/** 收藏状态：未登录/接口异常都按「未收藏」降级，不影响浏览和加购 */
async function loadFavorite() {
  if (!shopId) return
  try {
    const r = await favoriteApi.check(shopId)
    favorited.value = !!r?.favorited
  } catch (e) {
    favorited.value = false
  }
}

/** 领券：POST /api/coupons/{id}/claim；claimed 置 true 后按钮不可再点 */
async function claim(c: ShopCoupon) {
  if (c.claimed || claimingId.value) return
  claimingId.value = c.id
  try {
    await couponApi.claim(c.id)
    c.claimed = true
    uni.showToast({ title: '领取成功', icon: 'none' })
  } catch (e: any) {
    const msg = String(e?.message || '')
    if (msg.includes('领过') || msg.includes('已领')) {
      c.claimed = true
      uni.showToast({ title: '这张券已经领过了', icon: 'none' })
    } else {
      uni.showToast({ title: msg || '领取失败，请稍后重试', icon: 'none' })
    }
  } finally {
    claimingId.value = 0
  }
}

async function toggleFav() {
  if (favBusy.value || !shopId) return
  favBusy.value = true
  try {
    const r = await favoriteApi.toggle(shopId)
    favorited.value = !!r?.favorited
    uni.showToast({ title: favorited.value ? '已收藏' : '已取消收藏', icon: 'none' })
  } catch (e: any) {
    // 失败只提示，不阻塞浏览/加购
    uni.showToast({ title: String(e?.message || '操作失败，请稍后再试'), icon: 'none' })
  } finally {
    favBusy.value = false
  }
}

// onShow 放在 onLoad 之后执行，此时 shopId 已就绪；从收藏页返回也能同步状态
onShow(() => {
  loadFavorite()
})

/** 有规格的商品先弹规格选择，选完再入购物车 */
function onAdd(p: Product) {
  if (!shop.value) return
  if (shop.value.bizStatus !== 1) {
    uni.showToast({ title: '商家休息中', icon: 'none' })
    return
  }
  if (p.hasSku === 1) {
    if (!p.skus || !p.skus.length) {
      uni.showToast({ title: '该商品暂未配置规格', icon: 'none' })
      return
    }
    skuProduct.value = p
    return
  }
  cart.add(shop.value.id, shop.value.name, {
    id: p.id,
    name: p.name,
    image: p.image,
    // 展示价用当前成交价（特价生效即 priceNow），提交后仍以 /api/cart/calculate 为准
    price: priceOf(p)
  })
  uni.showToast({ title: '已加入购物车', icon: 'none' })
}

function pickSku(s: ProductSku) {
  if (soldOut(s)) {
    uni.showToast({ title: '该规格已售罄', icon: 'none' })
    return
  }
  const p = skuProduct.value
  if (!p || !shop.value) return
  cart.add(shop.value.id, shop.value.name, p, { id: s.id, name: s.name, price: s.price })
  skuProduct.value = null
  uni.showToast({ title: '已加入购物车', icon: 'none' })
}

function closeSku() {
  skuProduct.value = null
}

function toCart() {
  uni.reLaunch({ url: '/pages/cart/cart' })
}
</script>

<style scoped>
.page { padding-bottom: 140rpx; }
.head { background: #fff; padding: 28rpx; }
.title { font-size: 38rpx; font-weight: 700; margin-bottom: 10rpx; flex: 1; }
.notice {
  margin-top: 16rpx; padding: 16rpx 20rpx;
  background: #fff7ed; border: 1rpx solid #fed7aa; border-radius: 12rpx;
  color: #9a3412; font-size: 24rpx; line-height: 1.6;
}
.fav {
  flex-shrink: 0; margin-left: 20rpx;
  font-size: 24rpx; color: #c2410c; background: #fdf2ea;
  border: 1rpx solid #f0b79c; border-radius: 30rpx; padding: 10rpx 22rpx;
}
.fav.on { background: #c2410c; color: #fff; border-color: #c2410c; }
.coupon-sec { background: #fff; padding: 18rpx 24rpx 20rpx; border-top: 1rpx solid #f3f4f6; }
.coupon-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 12rpx; }
.coupon-title { font-size: 28rpx; font-weight: 600; color: #c2410c; }
.coupon-scroll { white-space: nowrap; }
.shop-coupon {
  display: inline-flex; align-items: center; gap: 14rpx;
  margin-right: 16rpx; padding: 14rpx 22rpx;
  background: #fff7ed; border: 1rpx dashed #f0b79c; border-radius: 12rpx;
}
.shop-coupon.claimed { background: #f5f6f8; border-color: #e5e7eb; opacity: 0.7; }
.coupon-label { font-size: 24rpx; color: #c2410c; font-weight: 600; }
.shop-coupon.claimed .coupon-label { color: #9ca3af; }
.coupon-state { font-size: 20rpx; color: #fff; background: #c2410c; border-radius: 20rpx; padding: 4rpx 14rpx; }
.shop-coupon.claimed .coupon-state { background: #d1d5db; color: #6b7280; }
.products { padding: 20rpx; }
.item { display: flex; }
.thumb {
  width: 130rpx; height: 130rpx; border-radius: 16rpx; background: #f3f4f6;
  display: flex; align-items: center; justify-content: center; color: #9ca3af;
  margin-right: 20rpx; overflow: hidden; flex-shrink: 0;
}
.thumb-img { width: 130rpx; height: 130rpx; }
.thumb-text { font-size: 36rpx; }
.info { flex: 1; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 10rpx; }
.name { font-weight: 600; font-size: 30rpx; }
.combo-tag {
  font-size: 20rpx; color: #c2410c; background: #fdf2ea;
  border: 1rpx solid #f0b79c; border-radius: 16rpx; padding: 2rpx 10rpx;
}
.combo { margin-top: 6rpx; }
.combo-toggle { font-size: 22rpx; color: #c2410c; }
.combo-list { margin-top: 6rpx; background: #fafafa; border-radius: 10rpx; padding: 10rpx 14rpx; }
.combo-item { font-size: 22rpx; color: #6b7280; line-height: 1.7; }
.price-row { margin-top: 10rpx; }
.price-group { display: flex; align-items: baseline; gap: 10rpx; }
.price { color: #c2410c; font-weight: 600; font-size: 30rpx; }
.origin { color: #9ca3af; font-size: 22rpx; text-decoration: line-through; }
.from { color: #9ca3af; font-size: 20rpx; }
.special-tag {
  font-size: 20rpx; color: #fff; background: #dc2626;
  border-radius: 6rpx; padding: 2rpx 10rpx;
}
.special-time { margin-top: 6rpx; font-size: 20rpx; color: #dc2626; }
.add {
  width: 52rpx; height: 52rpx; border-radius: 50%; background: #c2410c; color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 34rpx;
}
.bar {
  position: fixed; left: 0; right: 0; bottom: 0; background: #1f2328; color: #fff;
  display: flex; align-items: center; justify-content: space-between; padding: 20rpx 28rpx;
}
.bar-left { display: flex; align-items: center; }
.badge {
  background: #c2410c; border-radius: 50%; width: 40rpx; height: 40rpx;
  display: inline-flex; align-items: center; justify-content: center; font-size: 22rpx; margin-right: 12rpx;
}
.bar-btn { background: #c2410c; padding: 16rpx 40rpx; border-radius: 40rpx; }

/* 规格弹层 */
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.45); z-index: 90;
  display: flex; align-items: flex-end;
}
.sheet {
  width: 100%; background: #fff; border-radius: 28rpx 28rpx 0 0;
  padding: 24rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
}
.sheet-head { padding-bottom: 12rpx; }
.sheet-title { font-size: 30rpx; font-weight: 600; }
.sku-list { margin-top: 16rpx; }
.sku {
  display: flex; align-items: center; justify-content: space-between;
  padding: 22rpx 20rpx; border: 1rpx solid #eee; border-radius: 14rpx;
  margin-bottom: 14rpx;
}
.sku.disabled { background: #f7f8fa; opacity: 0.6; }
.sku-name { font-size: 28rpx; color: #1a1d21; }
.sku-price { color: #c2410c; font-weight: 600; font-size: 28rpx; }
.sku-out { color: #9ca3af; font-size: 22rpx; margin-left: 12rpx; }
.empty-sku { text-align: center; padding: 40rpx 0; }
</style>
