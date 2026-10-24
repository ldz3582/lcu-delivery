<template>
  <view class="page">
    <view class="tabs">
      <view
        v-for="t in TABS"
        :key="String(t.value)"
        :class="['tab', tab === t.value ? 'on' : '']"
        @click="switchTab(t.value)"
      >{{ t.label }}</view>
    </view>

    <view v-if="loading && !hasRows" class="muted loading">加载中…</view>

    <!-- 配送单 -->
    <block v-if="tab !== 'incident'">
      <view v-for="o in orders" :key="o.orderId" class="card">
        <view class="between">
          <view class="shop-row">
            <text class="shop">{{ o.shopName }}</text>
            <text :class="['tag', 's' + o.status]">{{ o.statusText }}</text>
            <text v-if="o.incident" class="tag warn">异常 {{ o.incident }}</text>
          </view>
          <text class="income">+{{ yuan(o.riderIncome) }}</text>
        </view>

        <view class="muted mt8">{{ o.orderNo }}</view>
        <view class="addr mt8">{{ addressText(o.address) }}</view>
        <view v-if="o.deliveredAt" class="muted mt8">送达 {{ dt(o.deliveredAt) }}</view>
        <view v-else-if="o.pickedAt" class="muted mt8">取餐 {{ dt(o.pickedAt) }}</view>

        <view class="ops">
          <text v-if="o.shopLng && o.shopLat" class="op" @click="navShop(o)">导航到店</text>
          <text v-if="o.lng && o.lat" class="op" @click="navDest(o)">导航到收货点</text>
          <text v-if="o.status === 30" class="op primary" @click="doPickup(o)">
            {{ busyId === o.orderId ? '上传中…' : '去取餐（拍照）' }}
          </text>
          <text v-if="o.status === 40" class="op primary" @click="doDeliver(o)">
            {{ busyId === o.orderId ? '上传中…' : '送达（拍照）' }}
          </text>
          <text v-if="o.status === 30 || o.status === 40" class="op danger" @click="openIncident(o)">上报异常</text>
        </view>
      </view>

      <view v-if="!loading && !orders.length" class="empty">当前没有「{{ tabLabel }}」的订单</view>
    </block>

    <!-- 异常记录 -->
    <block v-else>
      <view v-for="it in incidents" :key="it.id" class="card">
        <view class="between">
          <view class="shop-row">
            <text class="shop">{{ it.typeText }}</text>
            <text :class="['tag', it.status === 1 ? 'done' : 'warn']">{{ it.statusText }}</text>
          </view>
          <text class="muted">{{ it.createdAt }}</text>
        </view>
        <view class="muted mt8">{{ it.shopName || '店铺' }} · {{ it.orderNo || ('订单 ' + it.orderId) }}</view>
        <view class="remark mt8">{{ it.remark || '（未填写说明）' }}</view>
        <view v-if="it.images && it.images.length" class="imgs mt8">
          <image
            v-for="(p, i) in it.images"
            :key="i"
            :src="p"
            mode="aspectFill"
            class="img"
            @click="previewImages(it.images || [], i)"
          />
        </view>
        <view v-if="it.handleRemark" class="handle mt8">平台处置：{{ it.handleRemark }}</view>
      </view>

      <view v-if="!loading && !incidents.length" class="empty">还没有上报过配送异常</view>
    </block>

    <!-- 异常上报弹层 -->
    <view v-if="incidentOrder" class="mask" @click="closeIncident">
      <view class="sheet" @click.stop>
        <view class="between sheet-head">
          <text class="sheet-title">上报异常 · {{ incidentOrder.shopName }}</text>
          <text class="muted" @click="closeIncident">关闭</text>
        </view>

        <view class="types">
          <view
            v-for="t in INCIDENT_TYPES"
            :key="t.value"
            :class="['type', incidentForm.type === t.value ? 'on' : '']"
            @click="incidentForm.type = t.value"
          >{{ t.label }}</view>
        </view>

        <textarea
          v-model="incidentForm.remark"
          class="ta mt"
          :maxlength="200"
          placeholder="请说明具体情况，例如：用户电话一直无人接听……（必填）"
        />

        <view class="imgs mt">
          <view v-for="(p, i) in incidentForm.images" :key="i" class="img-box">
            <image :src="p" mode="aspectFill" class="img" @click="previewImages(incidentForm.images, i)" />
            <view class="img-del" @click.stop="removeIncidentImage(i)">×</view>
          </view>
          <view v-if="incidentForm.images.length < 3" class="img-add" @click="chooseIncidentImage">+</view>
        </view>

        <view :class="['submit', incidentSubmitting ? 'off' : '']" @click="submitIncident">
          {{ incidentSubmitting ? '提交中…' : '提交上报' }}
        </view>
      </view>
    </view>

    <AppTabBar :role="'RIDER'" />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import {
  ensureRiderApproved,
  riderApi,
  uploadImage,
  type RiderIncident,
  type RiderOrder
} from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

type TabValue = number | 'incident'

const TABS: { value: TabValue; label: string }[] = [
  { value: 30, label: '待取餐' },
  { value: 40, label: '配送中' },
  { value: 50, label: '已完成' },
  { value: 'incident', label: '异常' }
]

const INCIDENT_TYPES = [
  { value: 1, label: '联系不上用户' },
  { value: 2, label: '餐品撒漏' },
  { value: 3, label: '地址错误' },
  { value: 4, label: '其他' }
]

const tab = ref<TabValue>(30)
const orders = ref<RiderOrder[]>([])
const incidents = ref<RiderIncident[]>([])
const loading = ref(false)
/** 正在拍照/上传的订单，防重复点击 */
const busyId = ref(0)

const incidentOrder = ref<RiderOrder | null>(null)
const incidentForm = reactive({ type: 1, remark: '', images: [] as string[] })
const incidentSubmitting = ref(false)

const tabLabel = computed(() => TABS.find((t) => t.value === tab.value)?.label || '')
const hasRows = computed(() => (tab.value === 'incident' ? incidents.value.length : orders.value.length) > 0)

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

function yuan(fen?: number | null) {
  return (Number(fen || 0) / 100).toFixed(2) + ' 元'
}

function dt(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : ''
}

/** 订单地址是 JSON 字符串：{campus, detail, building, floor, room, ...} */
function addressText(s?: string) {
  try {
    const a = JSON.parse(s || '{}')
    const campus = a.campus === 1 ? '东校区' : a.campus === 2 ? '西校区' : ''
    const room = [a.building, a.floor, a.room].filter(Boolean).join(' ')
    return [campus, a.detail, room].filter(Boolean).join(' ') || '收货地址未知'
  } catch (e) {
    return '收货地址未知'
  }
}

async function load() {
  loading.value = true
  try {
    if (tab.value === 'incident') {
      incidents.value = (await riderApi.incidents()) || []
    } else {
      orders.value = (await riderApi.orders(tab.value as number)) || []
    }
  } catch (e) {
    if (tab.value === 'incident') incidents.value = []
    else orders.value = []
  } finally {
    loading.value = false
  }
}

function switchTab(v: TabValue) {
  if (tab.value === v) return
  tab.value = v
  load()
}

function navShop(o: RiderOrder) {
  if (!o.shopLng || !o.shopLat) return
  uni.openLocation({
    latitude: Number(o.shopLat),
    longitude: Number(o.shopLng),
    name: o.shopName,
    scale: 16
  })
}

function navDest(o: RiderOrder) {
  if (!o.lng || !o.lat) return
  uni.openLocation({
    latitude: Number(o.lat),
    longitude: Number(o.lng),
    name: '收货点',
    address: addressText(o.address),
    scale: 16
  })
}

/** 取餐/送达都必须先拍照上传，再把返回的 url 传给后端 */
function pickPhoto(): Promise<string | null> {
  return new Promise((resolve) => {
    uni.chooseImage({
      count: 1,
      sizeType: ['compressed'],
      sourceType: ['camera', 'album'],
      success: async (r: any) => {
        const path = r?.tempFilePaths?.[0]
        if (!path) {
          resolve(null)
          return
        }
        uni.showLoading({ title: '照片上传中', mask: true })
        try {
          const res = await uploadImage(path)
          resolve(res.url)
        } catch (e: any) {
          toast(String(e?.message || '照片上传失败，请重试'))
          resolve(null)
        } finally {
          uni.hideLoading()
        }
      },
      fail: () => resolve(null)
    })
  })
}

async function doPickup(o: RiderOrder) {
  if (busyId.value) return
  busyId.value = o.orderId
  try {
    const photo = await pickPhoto()
    if (!photo) return
    await riderApi.pickup(o.orderId, photo)
    uni.showToast({ title: '已取餐，开始配送', icon: 'none' })
    await load()
  } catch (e) {
    console.warn(e)
  } finally {
    busyId.value = 0
  }
}

async function doDeliver(o: RiderOrder) {
  if (busyId.value) return
  busyId.value = o.orderId
  try {
    const photo = await pickPhoto()
    if (!photo) return
    await riderApi.deliver(o.orderId, photo)
    uni.showToast({ title: '已送达', icon: 'success' })
    await load()
  } catch (e) {
    console.warn(e)
  } finally {
    busyId.value = 0
  }
}

// ---------------- 异常上报 ----------------
function openIncident(o: RiderOrder) {
  incidentOrder.value = o
  incidentForm.type = 1
  incidentForm.remark = ''
  incidentForm.images = []
}

function closeIncident() {
  incidentOrder.value = null
}

function chooseIncidentImage() {
  uni.chooseImage({
    count: Math.max(1, 3 - incidentForm.images.length),
    sizeType: ['compressed'],
    success: async (r: any) => {
      const paths: string[] = r?.tempFilePaths || []
      for (const p of paths) {
        uni.showLoading({ title: '上传中', mask: true })
        try {
          const res = await uploadImage(p)
          if (incidentForm.images.length < 3) incidentForm.images.push(res.url)
        } catch (e: any) {
          toast(String(e?.message || '上传失败，请重试'))
        } finally {
          uni.hideLoading()
        }
      }
    }
  })
}

function removeIncidentImage(i: number) {
  incidentForm.images = incidentForm.images.filter((_, idx) => idx !== i)
}

function previewImages(urls: string[], current: number) {
  if (!urls || !urls.length) return
  uni.previewImage({ urls, current: urls[current] })
}

async function submitIncident() {
  const o = incidentOrder.value
  if (!o || incidentSubmitting.value) return
  const remark = incidentForm.remark.trim()
  if (!remark) {
    toast('请填写异常说明')
    return
  }
  incidentSubmitting.value = true
  try {
    await riderApi.reportIncident(o.orderId, {
      type: incidentForm.type,
      remark,
      images: incidentForm.images
    })
    uni.showToast({ title: '已上报，等待平台处理', icon: 'none' })
    incidentOrder.value = null
    tab.value = 'incident'
    await load()
  } catch (e) {
    console.warn(e)
  } finally {
    incidentSubmitting.value = false
  }
}

onShow(async () => {
  if (!guard('RIDER')) return
  if (!(await ensureRiderApproved())) return
  load()
})

onPullDownRefresh(async () => {
  if (await ensureRiderApproved()) await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped>
.page { padding: 20rpx 20rpx 120rpx; }
.tabs { display: flex; gap: 12rpx; margin-bottom: 18rpx; }
.tab {
  flex: 1; text-align: center; padding: 16rpx 0; border-radius: 14rpx;
  background: #fff; color: #4b5563; font-size: 26rpx;
}
.tab.on { background: #fdf2ea; color: #c2410c; font-weight: 600; }

.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.shop-row { display: flex; align-items: center; gap: 10rpx; flex-wrap: wrap; }
.shop { font-size: 30rpx; font-weight: 600; }
.income { color: #c2410c; font-weight: 700; font-size: 28rpx; }
.muted { color: #7a828c; font-size: 24rpx; }
.mt8 { margin-top: 10rpx; }
.addr { font-size: 26rpx; color: #374151; line-height: 1.5; }
.remark { font-size: 26rpx; color: #374151; line-height: 1.5; }
.handle { font-size: 24rpx; color: #4b5563; background: #f7f8fa; border-radius: 12rpx; padding: 14rpx; }

.tag { font-size: 20rpx; padding: 4rpx 12rpx; border-radius: 16rpx; }
.tag.s30 { background: #fef3c7; color: #b45309; }
.tag.s40 { background: #dbeafe; color: #1d4ed8; }
.tag.s50 { background: #d1fae5; color: #047857; }
.tag.warn { background: #fee2e2; color: #b91c1c; }
.tag.done { background: #d1fae5; color: #047857; }

.ops {
  display: flex; flex-wrap: wrap; gap: 14rpx;
  margin-top: 18rpx; padding-top: 16rpx; border-top: 1rpx solid #f7f8fa;
}
.op {
  font-size: 24rpx; color: #4b5563; background: #f5f6f8;
  border-radius: 26rpx; padding: 10rpx 24rpx;
}
.op.primary { background: #c2410c; color: #fff; }
.op.danger { background: #fef2f2; color: #b91c1c; }

.imgs { display: flex; flex-wrap: wrap; gap: 12rpx; }
.img { width: 150rpx; height: 150rpx; border-radius: 12rpx; background: #f3f4f6; }
.img-box { position: relative; width: 150rpx; height: 150rpx; }
.img-del {
  position: absolute; right: -6rpx; top: -6rpx; width: 38rpx; height: 38rpx;
  border-radius: 50%; background: rgba(0, 0, 0, 0.55); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 24rpx;
}
.img-add {
  width: 150rpx; height: 150rpx; border-radius: 12rpx; background: #f7f8fa;
  border: 2rpx dashed #d9dee5; color: #9aa1aa; font-size: 44rpx;
  display: flex; align-items: center; justify-content: center;
}

.empty { text-align: center; color: #9ca3af; padding: 100rpx 40rpx; font-size: 26rpx; }
.loading { text-align: center; padding: 30rpx 0; }

/* 异常弹层 */
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.45); z-index: 90;
  display: flex; align-items: flex-end;
}
.sheet {
  width: 100%; background: #fff; border-radius: 28rpx 28rpx 0 0;
  padding: 24rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
}
.sheet-head { padding-bottom: 16rpx; }
.sheet-title { font-size: 28rpx; font-weight: 600; }
.types { display: flex; flex-wrap: wrap; gap: 14rpx; }
.type {
  font-size: 24rpx; padding: 10rpx 24rpx; border-radius: 26rpx;
  background: #f7f8fa; color: #4b5563;
}
.type.on { background: #fdf2ea; color: #c2410c; font-weight: 600; }
.ta {
  width: 100%; box-sizing: border-box; min-height: 140rpx;
  font-size: 26rpx; line-height: 1.6;
}
.submit {
  margin-top: 24rpx; background: #c2410c; color: #fff; text-align: center;
  padding: 22rpx; border-radius: 44rpx; font-size: 30rpx;
}
.submit.off { background: #e5e7eb; color: #9aa1aa; }
</style>
