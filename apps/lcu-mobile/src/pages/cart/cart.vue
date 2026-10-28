<template>
  <view class="page">
    <view v-if="!cart.items.length" class="empty">
      <view>购物车还是空的</view>
      <view class="go" @click="goHome">去逛逛</view>
    </view>

    <view v-else>
      <view class="card">
        <view class="between">
          <text class="shop-name">{{ cart.shopName }}</text>
          <text class="muted" @click="cart.clear()">清空</text>
        </view>
        <view v-for="i in lines" :key="i.key" class="line">
          <view class="lname">
            {{ i.name }}<text v-if="i.skuName" class="sku-name">（{{ i.skuName }}）</text>
          </view>
          <view class="qty">
            <text class="op" @click="changeQty(-1, i.productId, i.skuId)">-</text>
            <text class="num">{{ i.qty }}</text>
            <text class="op" @click="changeQty(1, i.productId, i.skuId)">+</text>
          </view>
          <view class="price">{{ ((i.price * i.qty) / 100).toFixed(2) }}</view>
        </view>
      </view>

      <view class="card" @click="chooseAddress">
        <view class="between">
          <text class="label">收货地址</text>
          <text class="muted">{{ address ? address.detail : '点击选择' }} ›</text>
        </view>
        <view v-if="address && !address.inFence" class="warn">该地址可能超出校园配送范围</view>
      </view>

      <view class="card">
        <view class="between row-line"><text>商品</text><text>{{ fen(bill?.goodsAmount ?? cart.goodsAmount) }}</text></view>
        <view class="between row-line"><text>打包费</text><text>{{ fen(bill?.packFee ?? 0) }}</text></view>
        <view class="between row-line"><text>配送费</text><text>{{ fen(bill?.deliveryFee ?? 0) }}</text></view>
        <view class="between row-line" @click="openCoupon">
          <text>优惠券</text>
          <text :class="coupon ? 'coupon-picked' : 'muted'">{{ couponLabel }} ›</text>
        </view>
        <view v-if="bill && bill.discountAmount > 0" class="between row-line">
          <text class="muted">已优惠</text>
          <text class="discount">-{{ fen(bill.discountAmount) }}</text>
        </view>
        <view class="between total"><text>合计</text><text class="price">{{ fen(bill?.totalAmount ?? cart.goodsAmount) }}</text></view>
        <view v-if="bill && !bill.reachMin" class="warn">还差 {{ ((bill.minAmount - bill.goodsAmount) / 100).toFixed(2) }} 元起送</view>
      </view>

      <view class="card">
        <input v-model="remark" placeholder="备注：少放辣、放门口……" />
      </view>

      <view class="submit" @click="submit">提交订单</view>
    </view>

    <!-- 选券弹层 -->
    <view v-if="sheetVisible" class="mask" @click="closeSheet">
      <view class="sheet" @click.stop>
        <view class="between sheet-head">
          <text class="sheet-title">选择优惠券</text>
          <text class="muted" @click="closeSheet">关闭</text>
        </view>
        <scroll-view class="sheet-body" scroll-y>
          <view :class="['sheet-item', 'none']" @click="clearCoupon">
            <text>不使用优惠券</text>
            <text v-if="!coupon" class="picked">✓</text>
          </view>
          <view
            v-for="c in couponList"
            :key="c.userCouponId || c.id"
            :class="['sheet-item', c.usable ? '' : 'disabled']"
            @click="pickCoupon(c)"
          >
            <view class="si-face">{{ faceValue(c) }}</view>
            <view class="si-mid">
              <view class="si-name">{{ c.name }}</view>
              <view v-if="c.usable" class="muted si-cut">本单可减 ¥{{ ((c.discount || 0) / 100).toFixed(2) }}</view>
              <view v-else class="muted">{{ c.desc || condText(c) }}</view>
              <view v-if="c.usable === false" class="muted si-reason">{{ c.reason || '本单不可用' }}</view>
            </view>
            <text v-if="coupon && coupon.userCouponId === c.userCouponId" class="picked">✓</text>
          </view>
          <view v-if="!couponList.length" class="muted sheet-empty">本店暂无可用优惠券</view>
        </scroll-view>
        <view class="sheet-foot">
          <view class="go-coupon" @click="goCouponCenter">去领券</view>
        </view>
      </view>
    </view>

    <AppTabBar :role="'USER'" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api, couponApi, type Address, type Bill, type MyCoupon } from '../../api'
import { useCartStore } from '../../stores/cart'
import { guard } from '../../utils/auth'
import { requestSubscribe } from '../../utils/subscribe'
import AppTabBar from '../../components/AppTabBar.vue'

const cart = useCartStore()
const bill = ref<Bill | null>(null)
const address = ref<Address | null>(null)
const remark = ref('')
const submitting = ref(false)
/** 下单幂等键：一次结算内复用，成功后清空（防双击建两单） */
let clientToken: string | null = null

const coupon = ref<MyCoupon | null>(null)
const couponList = ref<MyCoupon[]>([])
const sheetVisible = ref(false)

/** 防止连点加减号时旧请求覆盖新结果 */
let calcSeq = 0

/** 同一商品不同规格是两行，key 要带 skuId */
const lines = computed(() =>
  cart.items.map((i) => ({
    ...i,
    key: i.skuId ? `${i.productId}_${i.skuId}` : String(i.productId)
  }))
)

/** 提交给后端的 items：有规格带 skuId，金额一律由后端算 */
function cartItems() {
  return cart.items.map((i) => ({ productId: i.productId, skuId: i.skuId, qty: i.qty }))
}

const usableCount = computed(() => couponList.value.filter((c) => c.usable).length)

const couponLabel = computed(() => {
  if (coupon.value) {
    const d = bill.value?.discountAmount
    const money = typeof d === 'number' && d > 0 ? `-¥${(d / 100).toFixed(2)} ` : ''
    return `${money}${coupon.value.name || '优惠券'}`
  }
  if (usableCount.value > 0) return `${usableCount.value} 张可用`
  return '暂无可用'
})

function fen(v: number) { return (v / 100).toFixed(2) + ' 元' }
function goHome() { uni.reLaunch({ url: '/pages/index/index' }) }

function yuanShort(fenVal: number) {
  const v = Number(fenVal || 0) / 100
  return Number.isInteger(v) ? String(v) : v.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

/** 与领券中心保持一致的展示口径：满减 ¥5，折扣 9.0折（type=2 时 amount=880 → 8.8 折） */
function faceValue(c: MyCoupon) {
  if (c.type === 2) return (c.amount / 100).toFixed(1) + '折'
  return '¥' + yuanShort(c.amount)
}

function condText(c: MyCoupon) {
  return c.threshold > 0 ? `满 ${yuanShort(c.threshold)} 可用` : '无门槛'
}

async function refresh() {
  if (!cart.items.length) {
    bill.value = null
    coupon.value = null
    couponList.value = []
    return
  }
  const seq = ++calcSeq
  const items = cartItems()
  try {
    const b = await api.calculate(cart.shopId, items, coupon.value?.userCouponId ?? null)
    if (seq !== calcSeq) return
    bill.value = b
  } catch (e: any) {
    if (seq !== calcSeq) return
    const msg = String(e?.message || '')
    // 券失效/门槛变化：自动解绑后按无券重算（错误本身由 request 层 toast 提示）
    if (coupon.value && msg.includes('券')) {
      coupon.value = null
      try {
        bill.value = await api.calculate(cart.shopId, items)
      } catch (e2) {
        console.warn(e2)
      }
    } else {
      console.warn(e)
    }
  }
  await loadCouponList()
}

/** 未选券时也要知道「几张可用」，选券弹层复用同一份数据 */
async function loadCouponList() {
  if (!cart.items.length) {
    couponList.value = []
    return
  }
  const amount = bill.value?.goodsAmount ?? cart.goodsAmount
  try {
    couponList.value = (await couponApi.mine({
      status: 0,
      shopId: cart.shopId,
      amount
    })) || []
  } catch (e) {
    couponList.value = []
  }
}

function changeQty(delta: number, productId: number, skuId?: number) {
  if (delta > 0) cart.inc(productId, skuId)
  else cart.dec(productId, skuId)
  refresh()
}

function openCoupon() {
  if (!cart.items.length) return
  sheetVisible.value = true
  loadCouponList()
}

function closeSheet() {
  sheetVisible.value = false
}

function pickCoupon(c: MyCoupon) {
  if (!c.usable) {
    uni.showToast({ title: c.reason || '本单不可用', icon: 'none' })
    return
  }
  coupon.value = c
  sheetVisible.value = false
  refresh()
}

function clearCoupon() {
  coupon.value = null
  sheetVisible.value = false
  refresh()
}

function goCouponCenter() {
  sheetVisible.value = false
  const amount = bill.value?.goodsAmount ?? cart.goodsAmount
  uni.navigateTo({ url: `/pages/coupon/index?shopId=${cart.shopId}&amount=${amount}` })
}

async function loadAddress() {
  // 地址列表页选中/新增后写入 lcu_pick_address，这里优先消费并清除，避免下次进入串地址
  const picked = uni.getStorageSync('lcu_pick_address')
  if (picked && picked.id) {
    uni.removeStorageSync('lcu_pick_address')
    address.value = picked as Address
    return
  }
  const list = await api.addresses()
  address.value = list.find((a) => a.isDefault === 1) || list[0] || null
}

function chooseAddress() {
  uni.navigateTo({ url: '/pages/address/list?pick=1' })
}

async function submit() {
  if (submitting.value) return
  if (!address.value) {
    uni.showToast({ title: '请先选择收货地址', icon: 'none' })
    return
  }
  if (bill.value && !bill.value.reachMin) {
    uni.showToast({ title: '未达起送价', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    // 下单前申请订单状态订阅：没配模板/用户拒绝都不影响下单
    try {
      await requestSubscribe(['order_status'])
    } catch (e) {
      console.warn(e)
    }
    // 幂等键：同一次结算只生成一次，连点/超时重试都只会建一单
    if (!clientToken) {
      clientToken = `ct_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 10)}`
    }
    const order = await api.createOrder({
      shopId: cart.shopId,
      addressId: address.value.id,
      items: cartItems(),
      remark: remark.value,
      userCouponId: coupon.value?.userCouponId ?? null,
      clientToken
    })
    cart.clear()
    // 下单成功，幂等键作废，下次结算重新生成
    clientToken = null
    const prepay = await api.prepay(order.orderId)
    if (prepay.mock) {
      const r = await new Promise<boolean>((resolve) => {
        uni.showModal({
          title: '模拟支付',
          content: `本地开发环境不产生真实扣款，确认支付 ${(order.payAmount / 100).toFixed(2)} 元？`,
          success: (res) => resolve(res.confirm)
        })
      })
      if (r) await api.mockPay(order.orderId)
    }
    uni.redirectTo({ url: `/pages/order/detail?id=${order.orderId}` })
  } catch (e: any) {
    console.warn(e)
    // 围栏拦截（后端 ErrorCode.OUT_OF_FENCE=2001）：必须用弹窗明确告知，不能只靠 toast
    if (e?.code === 2001) {
      uni.showModal({
        title: '超出校园配送范围',
        content: '当前收货地址不在聊大校园配送范围内，暂不支持下单。请重新选择校内的收货地址后再提交。',
        showCancel: false,
        confirmText: '我知道了'
      })
      return
    }
    // 下单被券拦下（门槛变化/券已用）：解绑并按最新金额重算
    if (String(e?.message || '').includes('券')) {
      coupon.value = null
      refresh()
    }
  } finally {
    submitting.value = false
  }
}

onShow(async () => {
  if (!guard('USER')) return
  await loadAddress()
  refresh()
})
</script>

<style scoped>
.page { padding: 20rpx; }
.empty { text-align: center; padding: 160rpx 0; color: #9ca3af; }
.go { margin-top: 30rpx; color: #c2410c; }
.shop-name { font-weight: 600; font-size: 30rpx; }
.line { display: flex; align-items: center; margin-top: 24rpx; }
.lname { flex: 1; }
.sku-name { color: #9aa1aa; font-size: 22rpx; }
.qty { display: flex; align-items: center; }
.op {
  width: 44rpx; height: 44rpx; border-radius: 50%; background: #f3f4f6;
  display: flex; align-items: center; justify-content: center;
}
.num { width: 60rpx; text-align: center; }
.price { width: 110rpx; text-align: right; color: #c2410c; }
.label { font-size: 28rpx; }
.row-line { margin-bottom: 8rpx; color: #4b5563; }
.total { margin-top: 12rpx; font-weight: 600; }
.warn { color: #b45309; font-size: 24rpx; margin-top: 10rpx; }
.coupon-picked { color: #c2410c; font-size: 24rpx; }
.discount { color: #c2410c; font-size: 24rpx; }
.submit {
  margin: 30rpx 10rpx 60rpx;
  background: #c2410c; color: #fff; text-align: center;
  padding: 24rpx; border-radius: 44rpx; font-size: 32rpx;
}

/* 选券弹层 */
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.45); z-index: 90;
  display: flex; align-items: flex-end;
}
.sheet {
  width: 100%; background: #fff;
  border-radius: 28rpx 28rpx 0 0;
  padding: 24rpx 24rpx 0;
  max-height: 76vh; display: flex; flex-direction: column;
}
.sheet-head { padding-bottom: 16rpx; }
.sheet-title { font-size: 30rpx; font-weight: 600; }
.sheet-body { flex: 1; max-height: 56vh; }
.sheet-item {
  display: flex; align-items: center;
  padding: 22rpx 8rpx; border-bottom: 1rpx solid #f2f3f5;
}
.sheet-item.none { justify-content: space-between; color: #4b5563; }
.sheet-item.disabled { opacity: 0.55; }
.si-face {
  width: 100rpx; flex-shrink: 0; text-align: center;
  color: #c2410c; font-weight: 700; font-size: 28rpx;
}
.sheet-item.disabled .si-face { color: #9aa1aa; }
.si-mid { flex: 1; padding-left: 12rpx; }
.si-name { font-size: 26rpx; font-weight: 600; }
.si-reason { color: #b45309; margin-top: 4rpx; }
.si-cut { color: #c2410c; }
.picked { color: #c2410c; font-size: 30rpx; padding-left: 12rpx; }
.sheet-empty { text-align: center; padding: 60rpx 0; }
.sheet-foot {
  padding: 20rpx 0 calc(20rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #f2f3f5;
}
.go-coupon {
  text-align: center; color: #c2410c; font-size: 28rpx;
  background: #fdf2ea; border-radius: 40rpx; padding: 20rpx 0;
}
</style>
