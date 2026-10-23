<template>
  <view class="page">
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
</style>
