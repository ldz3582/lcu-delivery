<template>
  <view class="page">
    <view class="top">
      <view class="between">
        <view>
          <view class="shop">{{ shop?.name || '店铺' }}</view>
          <view class="muted">{{ shop?.bizStatus === 1 ? '营业中' : '已打烊' }} · {{ shop?.openTime }}</view>
        </view>
        <view class="switch" @click="toggleBiz">{{ shop?.bizStatus === 1 ? '打烊' : '营业' }}</view>
      </view>
      <view class="notice-row">
        <text class="muted">新订单提醒：震动 + 弹窗提示</text>
        <view class="sound-toggle">
          <text class="muted sound-label">提示音</text>
          <switch :checked="soundOn" color="#c2410c" @change="toggleSound" />
        </view>
      </view>
    </view>

    <scroll-view class="tabs" scroll-x>
      <view v-for="t in tabs" :key="String(t.v ?? 'all')" :class="['tab', active === t.v ? 'on' : '']" @click="pick(t.v)">{{ t.label }}</view>
    </scroll-view>

    <view class="list">
      <view v-for="o in orders" :key="o.id" class="card">
        <view class="between">
          <text class="no">{{ o.orderNo }}</text>
          <text class="status">{{ o.statusText }}</text>
        </view>
        <view class="muted mt8">{{ addressText(o.address) }}</view>
        <view v-if="o.remark" class="muted mt8">备注：{{ o.remark }}</view>
        <view class="between mt">
          <text class="muted">实付 {{ (o.payAmount / 100).toFixed(2) }} 元</text>
          <text class="price">到手 {{ (o.shopIncome / 100).toFixed(2) }} 元</text>
        </view>
        <view class="row">
          <view v-if="o.status === 10" class="btn ghost" @click="reject(o.id)">拒单</view>
          <view v-if="o.status === 10" class="btn" @click="accept(o.id)">接单</view>
          <view v-if="o.status === 20" class="btn" @click="ready(o.id)">出餐</view>
          <view v-if="o.status >= 30" class="btn gray" @click="noop">已出餐</view>
        </view>
      </view>
      <view v-if="!orders.length" class="empty">暂无订单</view>
    </view>

    <view class="nav">
      <text @click="go('/pages/merchant/products')">菜品管理</text>
      <text @click="go('/pages/merchant/shop')">店铺设置</text>
      <text @click="logout">退出</text>
    </view>
    <AppTabBar :role="'MERCHANT'" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow, onHide, onUnload, onPullDownRefresh } from '@dcloudio/uni-app'
import { merchantApi, ensureMerchantApproved, type MerchantOrder, type MerchantShop } from '../../api'
import { guard, logout } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

type TabValue = number | 'after' | undefined

const tabs: { label: string; v: TabValue }[] = [
  { label: '待接单', v: 10 },
  { label: '待出餐', v: 20 },
  { label: '待取餐', v: 30 },
  { label: '已完成', v: 50 },
  { label: '已取消', v: 60 },
  // 售后中(70) / 已退款(80)：接口只收单个 status，拉全部后本地过滤
  { label: '售后', v: 'after' },
  { label: '全部', v: undefined }
]

const shop = ref<MerchantShop | null>(null)
const orders = ref<MerchantOrder[]>([])
const active = ref<TabValue>(10)
let timer: any = null
/** 上一轮待接单 id 集合，用于识别轮询期间新增的订单 */
let pendingIds: Set<number> | null = null

/**
 * 提示音：随包音频 `static/ding.wav`（H5 与小程序都支持本地路径），
 * 播放失败（浏览器自动播放策略/静音）时静默降级为震动 + 弹窗。
 */
const SOUND_URL = '/static/ding.wav'
const soundOn = ref(uni.getStorageSync('lcu_merchant_sound') === 1)
let audioCtx: any = null

function playNoticeSound() {
  if (!soundOn.value || !SOUND_URL) return
  try {
    if (!audioCtx) {
      audioCtx = uni.createInnerAudioContext()
      audioCtx.obeyMuteSwitch = false
      audioCtx.onError?.(() => {
        /* 静音/自动播放被拦时忽略，不影响接单 */
      })
    }
    audioCtx.stop?.()
    audioCtx.src = SOUND_URL
    audioCtx.play()
  } catch (e) {
    console.warn(e)
  }
}

function toggleSound(e: any) {
  soundOn.value = !!e?.detail?.value
  uni.setStorageSync('lcu_merchant_sound', soundOn.value ? 1 : 0)
  if (soundOn.value) playNoticeSound()
}

function notifyNewOrder(count: number) {
  // H5 不支持震动时走 fail 静默降级，仍保留 toast 提醒
  try {
    uni.vibrateLong({ fail: () => {} })
  } catch (e) {
    // ignore
  }
  playNoticeSound()
  uni.showToast({ title: `有 ${count} 个新订单待接单`, icon: 'none', duration: 3000 })
}

/** 首次只记录基线，之后发现新 id 才提醒，避免进页面就误报 */
function detectNewPending(list: MerchantOrder[]) {
  const ids = new Set((list || []).map((o) => o.id))
  if (pendingIds === null) {
    pendingIds = ids
    return
  }
  const added = (list || []).filter((o) => !pendingIds!.has(o.id))
  pendingIds = ids
  if (added.length) notifyNewOrder(added.length)
}

function addressText(s: string) {
  try {
    const a = JSON.parse(s || '{}')
    return `${a.campus === 1 ? '东区' : a.campus === 2 ? '西区' : ''} ${a.detail || ''}`
  } catch { return '' }
}

async function load() {
  const listReq = active.value === 'after' ? merchantApi.orders() : merchantApi.orders(active.value as number | undefined)
  const pendingReq = active.value === 10 ? null : merchantApi.orders(10)
  const [s, o, pending] = await Promise.all([merchantApi.shop(), listReq, pendingReq])
  shop.value = s
  const list = o.list || []
  orders.value = active.value === 'after'
    ? list.filter((x) => x.status === 70 || x.status === 80)
    : list
  // 无论当前在哪个 tab，都用待接单列表识别新单；active=10 时复用列表结果
  detectNewPending((pending?.list || (active.value === 10 ? list : [])) as MerchantOrder[])
}

function pick(v: TabValue) { active.value = v; load() }
function go(url: string) { uni.navigateTo({ url }) }
function noop() {}
function reject(id: number) {
  uni.showModal({
    title: '拒单原因', editable: true, placeholderText: '如：菜品售罄',
    success: async (r) => {
      if (!r.confirm) return
      await merchantApi.reject(id, r.content || '商家拒单')
      load()
    }
  })
}
async function accept(id: number) { await merchantApi.accept(id); uni.showToast({ title: '已接单' }); load() }
async function ready(id: number) { await merchantApi.ready(id); uni.showToast({ title: '已出餐' }); load() }
async function toggleBiz() {
  if (!shop.value) return
  await merchantApi.updateShop({ bizStatus: shop.value.bizStatus === 1 ? 0 : 1 })
  load()
}

onShow(async () => {
  if (!guard('MERCHANT')) return
  // 没入驻/未通过审核：先去入驻页，避免 /api/merchant/shop 404 弹「还没有店铺」
  if (!(await ensureMerchantApproved())) return
  load()
  if (timer) clearInterval(timer)
  timer = setInterval(load, 10000)
})
onHide(() => { if (timer) clearInterval(timer) })
onUnload(() => { if (timer) clearInterval(timer) })

onPullDownRefresh(async () => {
  if (await ensureMerchantApproved()) await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page { padding-bottom: 110rpx; }
.top { background: #fff; padding: 24rpx; }
.shop { font-size: 34rpx; font-weight: 700; }
.switch { background: #fdf2ea; color: #c2410c; padding: 12rpx 26rpx; border-radius: 30rpx; font-size: 26rpx; }
.notice-row {
  display: flex; align-items: center; justify-content: space-between;
  margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx solid #f2f3f5;
}
.sound-toggle { display: flex; align-items: center; gap: 8rpx; }
.sound-label { font-size: 24rpx; }
.tabs { white-space: nowrap; background: #fff; padding: 0 12rpx 16rpx; }
.tab { display: inline-block; padding: 10rpx 26rpx; margin-right: 10rpx; border-radius: 26rpx; background: #f3f4f6; font-size: 26rpx; }
.tab.on { background: #fdf2ea; color: #c2410c; font-weight: 600; }
.list { padding: 20rpx; }
.no { font-size: 26rpx; }
.status { font-size: 24rpx; color: #c2410c; background: #fff7ed; padding: 4rpx 14rpx; border-radius: 16rpx; }
.mt { margin-top: 16rpx; }
.mt8 { margin-top: 8rpx; }
.empty { text-align: center; color: #9ca3af; padding: 100rpx 0; }
.nav {
  position: fixed; bottom: 0; left: 0; right: 0; background: #fff;
  display: flex; justify-content: space-around; padding: 24rpx 0; border-top: 1rpx solid #eef0f3; font-size: 26rpx; color: #4b5563;
}
</style>
