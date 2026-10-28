<template>
  <view class="page">
    <!-- 小程序端：真正的腾讯地图选点 -->
    <!-- #ifndef H5 -->
    <map
      class="map"
      :latitude="center.lat"
      :longitude="center.lng"
      :scale="13"
      :markers="markers"
      :polygons="polygons"
      :enable-zoom="true"
      :enable-scroll="true"
      @tap="onTap"
    />
    <!-- #endif -->

    <!-- H5：没配腾讯地图 Key 时地图组件会直接抛错，这里给可用的降级方案 -->
    <!-- #ifdef H5 -->
    <view class="fallback">
      <view class="fb-title">地图选点（H5 预览）</view>
      <view class="fb-tip">
        浏览器预览未接入腾讯地图 Key，可用下面两种方式确定收货坐标；小程序端（微信开发者工具/真机）支持在地图上直接点选。
      </view>
      <view class="fb-actions">
        <view class="mini-btn primary" @click="useMyLocation">📍 使用我的当前定位</view>
      </view>
      <view class="fb-row">
        <text class="fb-label">经度</text>
        <input v-model="manualLng" type="digit" placeholder="如 116.0010" class="fb-input" />
      </view>
      <view class="fb-row">
        <text class="fb-label">纬度</text>
        <input v-model="manualLat" type="digit" placeholder="如 36.4340" class="fb-input" />
      </view>
      <view class="mini-btn" @click="applyManual">使用这组坐标</view>
      <view v-if="manualError" class="fb-err">{{ manualError }}</view>
    </view>
    <!-- #endif -->

    <view class="bar">
      <view class="info">
        <view v-if="point" class="coords">{{ point.lng.toFixed(5) }}, {{ point.lat.toFixed(5) }}</view>
        <view v-else class="muted">在校园任意位置点一下地图</view>
        <view :class="['fence', inFence ? 'ok' : 'bad']">
          {{ point ? (inFence ? '✓ 在配送范围内' : '✗ 不在配送范围内') : '橙色区域 = 可配送范围' }}
        </view>
      </view>
      <view :class="['btn', point && inFence ? '' : 'dis']" @click="confirm">确定</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { http } from '../../utils/request'

interface Pt { lng: number; lat: number }
interface Ring { campus: number; name: string; points: Pt[] }

const center = ref({ lng: 116.0010, lat: 36.4340 })
const rings = ref<Ring[]>([])
const point = ref<Pt | null>(null)

const markers = computed(() =>
  point.value
    ? [{ id: 1, latitude: point.value.lat, longitude: point.value.lng, width: 26, height: 26 }]
    : []
)

const polygons = computed(() =>
  rings.value.map((r, i) => ({
    points: r.points.map((p) => ({ latitude: p.lat, longitude: p.lng })),
    strokeWidth: 2,
    strokeColor: i === 0 ? '#c2410c' : '#1d4ed8',
    fillColor: i === 0 ? '#c2410c22' : '#1d4ed822'
  }))
)

// 与后端 FenceService.contains 相同的射线法，前端先给即时反馈
function inside(poly: Pt[], x: number, y: number) {
  let hit = false
  for (let i = 0, j = poly.length - 1; i < poly.length; j = i++) {
    const xi = poly[i].lng, yi = poly[i].lat
    const xj = poly[j].lng, yj = poly[j].lat
    if (((yi > y) !== (yj > y)) && (x < ((xj - xi) * (y - yi)) / (yj - yi) + xi)) hit = !hit
  }
  return hit
}

const inFence = computed(() => {
  if (!point.value) return false
  return rings.value.some((r) => inside(r.points, point.value!.lng, point.value!.lat))
})

function onTap(e: any) {
  const lat = e?.detail?.latitude
  const lng = e?.detail?.longitude
  if (typeof lat === 'number' && typeof lng === 'number') {
    point.value = { lng, lat }
  }
}

// ---------- H5 降级：定位 / 手动坐标 ----------
const manualLng = ref('')
const manualLat = ref('')
const manualError = ref('')

function useMyLocation() {
  manualError.value = ''
  uni.getLocation({
    type: 'gcj02',
    success: (res: any) => {
      const lng = Number(res.longitude)
      const lat = Number(res.latitude)
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) {
        manualError.value = '定位结果无效，请手动填写经纬度'
        return
      }
      point.value = { lng, lat }
      manualLng.value = lng.toFixed(6)
      manualLat.value = lat.toFixed(6)
      if (!inFence.value) {
        manualError.value = '你的位置不在校园配送范围内，请手动填写校内坐标'
      }
    },
    fail: () => {
      manualError.value = '浏览器未授权定位，请手动填写经纬度（可参考校园地图坐标）'
    }
  })
}

function applyManual() {
  manualError.value = ''
  const lng = Number(manualLng.value)
  const lat = Number(manualLat.value)
  if (!Number.isFinite(lng) || !Number.isFinite(lat) || lng === 0 || lat === 0) {
    manualError.value = '请填写正确的经纬度（经度 116 左右、纬度 36.4 左右）'
    return
  }
  if (lng < -180 || lng > 180 || lat < -90 || lat > 90) {
    manualError.value = '经纬度超出合法范围'
    return
  }
  point.value = { lng, lat }
  if (!inFence.value) {
    manualError.value = '这组坐标不在校园配送范围内，请重新填写'
  }
}

function confirm() {
  if (!point.value || !inFence.value) {
    uni.showToast({ title: '请选在配送范围内的位置', icon: 'none' })
    return
  }
  const campus = rings.value.find((r) => inside(r.points, point.value!.lng, point.value!.lat))?.campus || 1
  uni.setStorageSync('lcu_pick_point', { ...point.value, campus })
  uni.navigateBack()
}

onLoad(async () => {
  try {
    rings.value = await http.get<Ring[]>('/api/fence')
    if (!rings.value.length) return
    // 视野中心落在所有围栏的包围盒中心
    const all = rings.value.flatMap((r) => r.points)
    center.value = {
      lng: (Math.min(...all.map((p) => p.lng)) + Math.max(...all.map((p) => p.lng))) / 2,
      lat: (Math.min(...all.map((p) => p.lat)) + Math.max(...all.map((p) => p.lat))) / 2
    }
  } catch (e) {
    console.warn(e)
  }
})
</script>

<style scoped>
.page { display: flex; flex-direction: column; height: 100vh; }
.map { flex: 1; width: 100%; }
.bar {
  display: flex; align-items: center; gap: 20rpx;
  padding: 20rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: #fff;
  border-top: 1rpx solid #eceff3;
}
.info { flex: 1; }
.coords { font-size: 28rpx; font-weight: 600; }
.fence { font-size: 22rpx; margin-top: 4rpx; }
.fence.ok { color: #047857; }
.fence.bad { color: #b91c1c; }
.btn {
  background: #c2410c; color: #fff;
  padding: 20rpx 44rpx; border-radius: 40rpx; font-size: 28rpx;
}
.btn.dis { background: #d1d5db; }

/* H5 降级面板 */
.fallback { flex: 1; overflow-y: auto; padding: 32rpx 28rpx; background: #fff; }
.fb-title { font-size: 30rpx; font-weight: 600; }
.fb-tip { font-size: 24rpx; color: #6b7280; line-height: 1.6; margin-top: 12rpx; }
.fb-actions { margin: 24rpx 0 8rpx; }
.fb-row { display: flex; align-items: center; gap: 16rpx; padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.fb-label { width: 90rpx; font-size: 26rpx; color: #4b5563; }
.fb-input { flex: 1; font-size: 28rpx; }
.fb-err { margin-top: 16rpx; font-size: 24rpx; color: #b91c1c; }
.mini-btn {
  display: inline-block; margin-top: 20rpx; padding: 18rpx 32rpx; border-radius: 36rpx;
  background: #f3f4f6; color: #374151; font-size: 26rpx;
}
.mini-btn.primary { background: #c2410c; color: #fff; }
</style>
