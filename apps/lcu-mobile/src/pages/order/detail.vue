<template>
  <view class="page" v-if="order">
    <view class="card status-card">
      <view class="big">{{ statusText }}</view>
      <view class="muted mt8">订单号 {{ order.orderNo }}</view>
      <view v-if="etaText" class="eta">{{ etaText }}</view>
    </view>

    <!-- 商家信息：GET /api/orders/{id} 已返回 shopName / shopPhone -->
    <view v-if="order.shopName || order.shopPhone" class="card">
      <view class="between">
        <text class="sec">商家</text>
        <text v-if="order.shopPhone" class="call" @click="callShop">📞 联系商家</text>
      </view>
      <view class="shop-name mt8">{{ order.shopName || '店铺' }}</view>
      <view v-if="order.shopPhone" class="muted mt8" @click="callShop">电话 {{ maskPhone(order.shopPhone) }}</view>
    </view>

    <!-- 配送进度时间轴（track 接口未就绪时整块不渲染，保持原样式） -->
    <view v-if="steps.length" class="card">
      <view class="between">
        <text class="sec">配送进度</text>
        <text v-if="locationUpdatedAt" class="muted">{{ locationUpdatedAt }}</text>
      </view>

      <view class="steps mt">
        <view v-for="(s, i) in steps" :key="s.key || i" :class="['step', { cur: s.current }]">
          <view class="rail">
            <view :class="['dot', { done: s.done, cur: s.current }]"></view>
            <view v-if="i < steps.length - 1" :class="['line', { done: s.done }]"></view>
          </view>
          <view class="sinfo">
            <view :class="['sname', { done: s.done, cur: s.current }]">{{ s.name }}</view>
            <view v-if="s.time" class="muted stime">{{ s.time }}</view>
          </view>
        </view>
      </view>

      <view v-if="timeline.length" class="tl-toggle" @click="showTimeline = !showTimeline">
        <text>{{ showTimeline ? '收起配送流水' : '查看配送流水' }}</text>
        <text class="arrow">{{ showTimeline ? '▲' : '▼' }}</text>
      </view>

      <view v-if="showTimeline" class="timeline">
        <view v-for="(t, i) in timeline" :key="i" class="tl-item">
          <view class="tl-dot"></view>
          <view class="tl-body">
            <view class="between">
              <text class="tl-st">{{ t.statusText }}</text>
              <text class="muted">{{ t.time }}</text>
            </view>
            <view v-if="t.remark" class="muted mt8">{{ t.remark }}</view>
          </view>
        </view>
      </view>
    </view>

    <!-- 骑手卡片：status >= 30 -->
    <view v-if="showRider" class="card">
      <view class="sec">配送骑手</view>
      <view class="rider mt">
        <view class="avatar">{{ riderInitial }}</view>
        <view class="rinfo">
          <view class="rname">{{ riderName }}</view>
          <view class="muted">评分 {{ riderScore }}</view>
        </view>
        <view class="call" @click="callRider">📞 {{ riderPhoneMasked }}</view>
      </view>
    </view>

    <!-- 骑手位置：status 30/40，有坐标才渲染地图，否则文字降级 -->
    <view v-if="showMapSection" class="card">
      <view class="between"><text class="sec">骑手位置</text></view>
      <map
        v-if="hasMap"
        class="map mt"
        :latitude="mapCenter.lat"
        :longitude="mapCenter.lng"
        :scale="14"
        :markers="markers"
        :polyline="polyline"
        :enable-zoom="true"
        :enable-scroll="true"
      />
      <view v-else class="muted mt">骑手位置获取中，请稍候…</view>
    </view>

    <view class="card">
      <view class="between"><text class="sec">收货信息</text></view>
      <view class="muted mt8">{{ addressText }}</view>
      <view v-if="order.remark" class="muted mt8">备注：{{ order.remark }}</view>
    </view>

    <view class="card">
      <view class="sec">商品</view>
      <view v-for="it in order.items" :key="it.id" class="line">
        <text class="lname">{{ it.name }} × {{ it.qty }}</text>
        <text class="price">{{ ((it.price * it.qty) / 100).toFixed(2) }}</text>
      </view>
      <view class="between mt">
        <text class="muted">打包费</text>
        <text class="muted">¥{{ ((order.packFee || 0) / 100).toFixed(2) }}</text>
      </view>
      <view class="between mt8">
        <text class="muted">配送费</text>
        <text class="muted">¥{{ ((order.deliveryFee || 0) / 100).toFixed(2) }}</text>
      </view>
      <view v-if="(order.discountAmount || 0) > 0" class="between mt8">
        <text class="muted">优惠券</text>
        <text class="discount">-¥{{ (order.discountAmount / 100).toFixed(2) }}</text>
      </view>
      <view class="between total">
        <text>实付</text>
        <text class="price">{{ (order.payAmount / 100).toFixed(2) }} 元</text>
      </view>
      <view v-if="penaltyAmount > 0" class="between mt8">
        <text class="muted">违约金</text>
        <text class="penalty">已扣违约金 ¥{{ (penaltyAmount / 100).toFixed(2) }}</text>
      </view>
    </view>

    <view class="actions">
      <view v-if="order.status === 0" class="btn-primary" @click="pay">去支付</view>
      <view v-if="canCancel" class="btn-ghost mt12" @click="cancel">取消订单</view>
      <view v-if="order.status === 40" class="btn-primary" @click="confirm">确认收货</view>

      <view v-if="order.status === 50" class="row-btns">
        <view :class="['btn-primary', 'half', reviewed ? 'btn-disabled' : '']" @click="goReview">
          {{ reviewed ? '已评价' : '去评价' }}
        </view>
        <view class="btn-ghost half" @click="goApplyAfterSale">申请售后</view>
      </view>
      <view v-if="order.status === 50" class="btn-ghost mt12" @click="goList">再来一单</view>

      <view v-if="order.status === 70" class="btn-ghost mt12" @click="goAfterSaleList">售后处理中 · 查看进度</view>
      <view v-if="order.status === 80" class="btn-ghost mt12" @click="goAfterSaleList">已退款 · 查看售后</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onHide, onLoad, onPullDownRefresh, onShow, onUnload } from '@dcloudio/uni-app'
import { api, reviewApi, type OrderTrack } from '../../api'
import { guard } from '../../utils/auth'

const order = ref<any>(null)
const track = ref<OrderTrack | null>(null)
const showTimeline = ref(false)
/** status=50 时判断这单是否已评价（404 视为未评价） */
const reviewed = ref(false)

let orderId = 0

/** 需要轮询的状态：待接单/待出餐/待取餐/配送中 */
const POLL_STATUSES = [10, 20, 30, 40]
const POLL_INTERVAL = 5000
/** 进度步骤展示顺序（后端 steps.key 契约） */
const STEP_ORDER = ['UNPAID', 'WAIT_ACCEPT', 'COOKING', 'WAIT_PICKUP', 'DELIVERING', 'FINISHED']

let pollTimer: any = null
let trackFails = 0

const currentStatus = computed<number>(() => {
  const s = track.value?.status
  if (typeof s === 'number') return s
  return typeof order.value?.status === 'number' ? order.value.status : -1
})

const statusText = computed(() => track.value?.statusText || order.value?.statusText || '')

/** 商家违约金（分），接口返回 penaltyAmount，>0 才展示 */
const penaltyAmount = computed(() => Number(order.value?.penaltyAmount || 0))

/** 0 待支付 / 10 待接单 / 20 待出餐 / 30 待取餐 可取消；后者会扣违约金 */
const canCancel = computed(() => [0, 10, 20, 30].includes(Number(order.value?.status)))

const isDelivering = computed(() => currentStatus.value === 30 || currentStatus.value === 40)

const etaText = computed(() => {
  const n = track.value?.etaMinutes
  return isDelivering.value && typeof n === 'number' ? `预计 ${n} 分钟送达` : ''
})

const steps = computed(() => {
  const arr = track.value?.steps
  if (!Array.isArray(arr)) return []
  return [...arr].sort((a, b) => rank(a?.key) - rank(b?.key))
})

function rank(key?: string) {
  const i = STEP_ORDER.indexOf(String(key || ''))
  return i < 0 ? 99 : i
}

const timeline = computed(() => (Array.isArray(track.value?.timeline) ? track.value!.timeline : []))

// ---------------- 骑手 ----------------
const showRider = computed(() => currentStatus.value >= 30 && !!track.value?.rider)
const riderName = computed(() => track.value?.rider?.name || '骑手')
const riderInitial = computed(() => riderName.value.slice(0, 1))
const riderScore = computed(() => {
  const s = track.value?.rider?.score
  return typeof s === 'number' ? s.toFixed(1) : '暂无'
})
const riderPhoneMasked = computed(() => maskPhone(track.value?.rider?.phone || ''))

/** 后端可能已脱敏，已含 * 直接透出，否则按 3-4-4 打码 */
function maskPhone(phone: string) {
  const p = String(phone || '')
  if (!p) return '暂无电话'
  if (p.includes('*')) return p
  return p.replace(/^(\d{3})\d{4}(\d{4})$/, '$1****$2')
}

function callRider() {
  const digits = String(track.value?.rider?.phone || '').replace(/\D/g, '')
  if (digits.length < 7) {
    uni.showToast({ title: '暂无骑手联系方式', icon: 'none' })
    return
  }
  // H5/部分端不支持拨号，失败不打扰用户
  uni.makePhoneCall({ phoneNumber: digits, fail: () => {} })
}

function callShop() {
  const phone = String(order.value?.shopPhone || '')
  const digits = phone.replace(/\D/g, '')
  // 后端可能返回脱敏号码（含 *），脱敏后位数不足时不拨号
  if (digits.length < 7) {
    uni.showToast({ title: '暂无商家联系电话', icon: 'none' })
    return
  }
  uni.makePhoneCall({
    phoneNumber: digits,
    fail: () => uni.showToast({ title: '当前环境不支持拨号', icon: 'none' })
  })
}

// ---------------- 地图 ----------------
function hasCoord(p: any) {
  return !!p && Number.isFinite(Number(p.lng)) && Number.isFinite(Number(p.lat))
}

const showMapSection = computed(() => isDelivering.value && !!track.value)

const hasMap = computed(() => hasCoord(track.value?.location))

const mapCenter = computed(() => {
  const t = track.value
  const p = hasCoord(t?.location) ? t!.location! : hasCoord(t?.destination) ? t!.destination! : t?.shop
  return { lat: Number(p?.lat ?? 36.434) || 36.434, lng: Number(p?.lng ?? 116.001) || 116.001 }
})

function callout(content: string, bgColor: string) {
  return { content, color: '#ffffff', bgColor, fontSize: 11, padding: 6, borderRadius: 6, display: 'ALWAYS' }
}

const markers = computed(() => {
  const t = track.value
  const ms: any[] = []
  if (!t) return ms
  if (hasCoord(t.shop)) {
    ms.push({ id: 1, latitude: Number(t.shop.lat), longitude: Number(t.shop.lng), width: 26, height: 26, callout: callout('店铺', '#1d4ed8') })
  }
  if (hasCoord(t.destination)) {
    ms.push({ id: 2, latitude: Number(t.destination.lat), longitude: Number(t.destination.lng), width: 26, height: 26, callout: callout('收货点', '#047857') })
  }
  if (hasCoord(t.location)) {
    ms.push({ id: 3, latitude: Number(t.location!.lat), longitude: Number(t.location!.lng), width: 30, height: 30, callout: callout('骑手', '#c2410c') })
  }
  return ms
})

const polyline = computed(() => {
  const t = track.value
  if (!hasCoord(t?.location) || !hasCoord(t?.destination)) return []
  return [
    {
      points: [
        { latitude: Number(t!.location!.lat), longitude: Number(t!.location!.lng) },
        { latitude: Number(t!.destination!.lat), longitude: Number(t!.destination!.lng) }
      ],
      color: '#c2410cdd',
      width: 4,
      arrowLine: true
    }
  ]
})

const locationUpdatedAt = computed(() => {
  const u = track.value?.location?.updatedAt
  return u ? `位置更新 ${u}` : ''
})

// ---------------- 轮询 ----------------
function stopPoll() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPoll() {
  stopPoll()
  if (!orderId) return
  if (!POLL_STATUSES.includes(currentStatus.value)) return
  pollTimer = setInterval(refreshTrack, POLL_INTERVAL)
}

/** 静默刷新：不动整页 loading，失败也不弹 toast */
async function refreshTrack() {
  if (!orderId) return
  const prevStatus = Number(order.value?.status)
  try {
    const t = await api.track(orderId)
    track.value = t || null
    trackFails = 0
    // 回写状态：让「确认收货 / 再来一单 / 去评价」等按钮随轮询即时出现
    if (t && order.value) {
      if (typeof t.status === 'number') order.value.status = t.status
      if (t.statusText) order.value.statusText = t.statusText
    }
  } catch (e) {
    // 网络抖动不要直接停轮询：现在停一次就再也不刷新，用户会一直看到旧状态。
    // 只有长时间连续失败（约 5 分钟）才停，避免彻底断网时空转。
    trackFails += 1
    if (trackFails >= 60) stopPoll()
    return
  }
  if (!POLL_STATUSES.includes(currentStatus.value)) {
    stopPoll()
    // 状态刚推进到已完成时补一次评价态查询
    if (Number(order.value?.status) === 50 && prevStatus !== 50) loadReviewState()
  }
}

/** 已评价则按钮置灰（GET /api/reviews/order/{id}，404 视为未评价） */
async function loadReviewState() {
  if (Number(order.value?.status) !== 50) {
    reviewed.value = false
    return
  }
  try {
    const r = await reviewApi.byOrder(orderId)
    reviewed.value = !!r
  } catch (e) {
    reviewed.value = false
  }
}

const addressText = computed(() => {
  try {
    const a = JSON.parse(order.value?.address || '{}')
    return [a.campus === 1 ? '东校区' : a.campus === 2 ? '西校区' : '', a.detail].filter(Boolean).join(' ')
  } catch {
    return ''
  }
})

async function load() {
  order.value = await api.order(orderId)
  await refreshTrack()
  startPoll()
  loadReviewState()
}

async function pay() {
  await api.prepay(orderId)
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '模拟支付',
      content: `确认支付 ${(order.value.payAmount / 100).toFixed(2)} 元？`,
      success: (r) => resolve(r.confirm)
    })
  })
  if (ok) {
    await api.mockPay(orderId)
    uni.showToast({ title: '支付成功' })
    load()
  }
}

async function cancel() {
  const status = Number(order.value?.status)
  // 20/30 商家已接单：后端按 order.cancel_penalty_rate（默认 20%）扣违约金，剩余原路退回
  const risky = status === 20 || status === 30
  const pay = Number(order.value?.payAmount || 0)
  const penalty = ((pay * 0.2) / 100).toFixed(2)
  const content = risky
    ? `商家已接单，取消将扣除违约金（约实付的 20%，约 ¥${penalty}），剩余原路退回。确定取消吗？`
    : '确认取消这一单吗？'
  const r = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: risky ? '取消订单（将扣违约金）' : '取消订单',
      content,
      success: (x) => resolve(!!x.confirm)
    })
  })
  if (!r) return
  try {
    await api.cancelOrder(orderId, risky ? '用户取消（商家已接单，同意扣违约金）' : '用户取消')
    uni.showToast({ title: risky ? '已取消，违约金已扣除' : '已取消', icon: 'none' })
    load()
  } catch (e) {
    // 「配送中/已完成不能取消」等由 request 层提示
    console.warn(e)
  }
}

async function confirm() {
  await api.confirmOrder(orderId)
  uni.showToast({ title: '已确认收货' })
  load()
}

function goList() {
  uni.reLaunch({ url: '/pages/order/list' })
}

function goReview() {
  if (reviewed.value) {
    uni.showToast({ title: '这单已经评价过了', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/review/edit?id=${orderId}` })
}

function goApplyAfterSale() {
  uni.navigateTo({ url: `/pages/after-sale/apply?id=${orderId}` })
}

function goAfterSaleList() {
  uni.navigateTo({ url: '/pages/after-sale/list' })
}

onLoad((q: any) => {
  orderId = Number(q?.id)
})

onShow(async () => {
  if (orderId) {
    if (!guard('USER')) return
    load()
  }
})

onHide(() => stopPoll())
onUnload(() => stopPoll())

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page { padding: 20rpx; }
.status-card { background: linear-gradient(135deg, #c2410c, #ea580c); color: #fff; }
.big { font-size: 40rpx; font-weight: 700; }
.status-card .muted { color: rgba(255, 255, 255, 0.85); }
.eta { margin-top: 10rpx; font-size: 24rpx; color: #fff; background: rgba(255, 255, 255, 0.18); border-radius: 999rpx; padding: 6rpx 18rpx; display: inline-block; }
.sec { font-weight: 600; font-size: 28rpx; }
.line { display: flex; justify-content: space-between; margin-top: 16rpx; }
.lname { color: #374151; }
.total { margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #f0f1f3; font-weight: 600; }
.actions { padding: 20rpx 6rpx 60rpx; }
.btn-primary, .btn-ghost { text-align: center; padding: 24rpx; border-radius: 44rpx; font-size: 30rpx; }
.btn-primary { background: #c2410c; color: #fff; }
.btn-ghost { border: 2rpx solid #e5e7eb; color: #374151; }
.btn-disabled { background: #e5e7eb; color: #9aa1aa; }
.row-btns { display: flex; gap: 20rpx; margin-top: 12rpx; }
.row-btns .half { flex: 1; }
.mt { margin-top: 16rpx; }
.discount { color: #c2410c; font-size: 24rpx; }
.mt8 { margin-top: 8rpx; }
.mt12 { margin-top: 20rpx; }

/* 时间轴 */
.steps { padding: 4rpx 0; }
.step { display: flex; }
.rail { width: 40rpx; display: flex; flex-direction: column; align-items: center; }
.dot { width: 18rpx; height: 18rpx; border-radius: 50%; background: #e5e7eb; margin-top: 10rpx; flex-shrink: 0; }
.dot.done { background: #c2410c; }
.dot.cur { background: #c2410c; animation: lcu-pulse 1.4s ease-out infinite; }
.line { width: 3rpx; flex: 1; min-height: 42rpx; background: #e5e7eb; }
.line.done { background: #f0b79c; }
.sinfo { flex: 1; padding-bottom: 22rpx; }
.sname { font-size: 27rpx; color: #9aa1aa; }
.sname.done { color: #1a1d21; font-weight: 600; }
.sname.cur { color: #c2410c; font-weight: 700; }
.stime { margin-top: 6rpx; }
@keyframes lcu-pulse {
  0% { box-shadow: 0 0 0 0 rgba(194, 65, 12, 0.5); }
  70% { box-shadow: 0 0 0 14rpx rgba(194, 65, 12, 0); }
  100% { box-shadow: 0 0 0 0 rgba(194, 65, 12, 0); }
}

.tl-toggle { margin-top: 8rpx; padding-top: 18rpx; border-top: 1rpx solid #f0f1f3; display: flex; justify-content: center; align-items: center; gap: 8rpx; font-size: 24rpx; color: #7a828c; }
.arrow { font-size: 18rpx; }
.timeline { margin-top: 14rpx; padding-left: 6rpx; }
.tl-item { position: relative; padding: 0 0 20rpx 26rpx; }
.tl-item::before { content: ''; position: absolute; left: 5rpx; top: 14rpx; bottom: -2rpx; width: 2rpx; background: #f0f1f3; }
.tl-item:last-child::before { display: none; }
.tl-dot { position: absolute; left: 0; top: 10rpx; width: 12rpx; height: 12rpx; border-radius: 50%; background: #f0b79c; }
.tl-body { flex: 1; }
.tl-st { font-size: 26rpx; color: #374151; font-weight: 600; }

/* 骑手 */
.rider { display: flex; align-items: center; }
.avatar { width: 72rpx; height: 72rpx; border-radius: 50%; background: #fdf2ea; color: #c2410c; font-weight: 700; font-size: 30rpx; display: flex; align-items: center; justify-content: center; }
.rinfo { flex: 1; margin-left: 18rpx; }
.rname { font-size: 28rpx; font-weight: 600; }
.call { font-size: 24rpx; color: #c2410c; border: 1rpx solid #f0b79c; background: #fdf2ea; border-radius: 999rpx; padding: 10rpx 20rpx; }
.shop-name { font-size: 30rpx; font-weight: 600; }
.penalty { color: #b91c1c; font-size: 24rpx; }

/* 地图 */
.map { width: 100%; height: 380rpx; border-radius: 16rpx; overflow: hidden; }
</style>
