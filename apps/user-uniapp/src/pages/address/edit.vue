<template>
  <view class="page">
    <view class="card">
      <view class="label">粘贴地址文本（可选）</view>
      <textarea v-model="raw" class="textarea" placeholder="例如：东校区3号宿舍楼502 张三 13800001111" />
      <view class="between mt">
        <text class="btn-ghost small" @click="paste">读取剪贴板</text>
        <text class="btn-primary small" @click="parse">解析并回填</text>
      </view>
      <view v-if="hint" class="hint">{{ hint }}</view>
    </view>

    <view class="card">
      <view class="field">
        <text class="label">校区</text>
        <picker :range="campusNames" @change="onCampus">
          <view class="picker">{{ campusName || '请选择（选填）' }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">详细地址</text>
        <input v-model="form.detail" placeholder="楼栋 + 房间，如 3 号宿舍楼 502" />
      </view>
      <view class="field">
        <text class="label">楼层</text>
        <input v-model="form.floor" placeholder="如 5" />
      </view>
      <view class="field">
        <text class="label">备注</text>
        <input v-model="form.remark" placeholder="放门口 / 打电话" />
      </view>
      <view class="field" @click="chooseLocation">
        <text class="label">地图定位</text>
        <text :class="['picker', form.lng ? '' : 'muted']">
          {{ form.lng ? '已选坐标 ' + form.lng.toFixed(4) + ',' + form.lat.toFixed(4) : '点击在地图上选择' }}
        </text>
      </view>
      <!-- #ifdef H5 -->
      <view class="hint demo" @click="useDemoPoint">浏览器预览：填入东校区示例坐标（115.9905, 36.4385）</view>
      <!-- #endif -->
      <view v-if="inFence === false" class="hint warn">该位置不在校园配送范围内</view>
      <view v-if="inFence === true" class="hint ok">该位置在校园配送范围内 ✓</view>
    </view>

    <view class="actions">
      <text class="btn-primary block" @click="save">保存</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { api } from '../../api'

const campusNames = ['不选', '东校区', '西校区']
const campusName = ref('')
const raw = ref('')
const hint = ref('')
const inFence = ref<boolean | null>(null)
const form = reactive<any>({ id: 0, campus: null, detail: '', building: '', floor: '', room: '', remark: '', lng: 0, lat: 0, isDefault: 0 })

function onCampus(e: any) {
  const idx = Number(e.detail.value)
  form.campus = idx === 0 ? null : idx
  campusName.value = campusNames[idx]
}

async function paste() {
  const res = await new Promise<string>((resolve) => {
    uni.getClipboardData({ success: (r) => resolve(r.data || ''), fail: () => resolve('') })
  })
  if (res) raw.value = res
}

async function parse() {
  if (!raw.value.trim()) {
    hint.value = '先粘贴一段地址文本'
    return
  }
  const r = await api.parseAddress(raw.value, form.lng || undefined, form.lat || undefined)
  form.campus = r.campus ?? form.campus
  form.building = r.building || form.building
  form.floor = r.floor || form.floor
  form.room = r.room || form.room
  form.detail = r.detail || form.detail
  campusName.value = r.campusName || campusName.value
  hint.value = `识别置信度 ${Math.round((r.confidence || 0) * 100)}%${r.needConfirm ? '，请核对后保存' : ''}`
}

function chooseLocation() {
  uni.chooseLocation({
    success: (res: any) => {
      form.lng = res.longitude
      form.lat = res.latitude
      if (!form.detail && res.name) form.detail = res.name
      checkFence()
    },
    fail: () => uni.showToast({ title: '需要位置权限，可在开发者工具中允许', icon: 'none' })
  })
}

function useDemoPoint() {
  form.lng = 115.9905
  form.lat = 36.4385
  if (!form.campus) { form.campus = 1; campusName.value = '东校区' }
  if (!form.detail) form.detail = '3 号宿舍楼 502'
  checkFence()
}

async function checkFence() {
  if (!form.lng) return
  const r = await api.parseAddress(form.detail || '校内地址', form.lng, form.lat)
  inFence.value = !!r.inFence
}

async function save() {
  if (!form.detail) {
    uni.showToast({ title: '请填写详细地址', icon: 'none' })
    return
  }
  const payload = {
    campus: form.campus,
    detail: form.detail,
    building: form.building,
    floor: form.floor,
    room: form.room,
    remark: form.remark,
    lng: form.lng || null,
    lat: form.lat || null,
    isDefault: form.isDefault
  }
  if (form.id) await api.updateAddress(form.id, payload)
  else await api.createAddress(payload)
  uni.showToast({ title: '已保存' })
  setTimeout(() => uni.navigateBack(), 500)
}

onLoad(async (q: any) => {
  if (q?.id) {
    const list = await api.addresses()
    const a: any = list.find((x) => String(x.id) === String(q.id))
    if (a) {
      Object.assign(form, a)
      campusName.value = a.campus ? campusNames[a.campus] : ''
      inFence.value = a.inFence === 1
    }
  }
})
</script>

<style scoped>
.page { padding: 20rpx; }
.label { font-size: 26rpx; color: #4b5563; }
.textarea { width: 100%; height: 140rpx; margin-top: 12rpx; background: #f7f8fa; border-radius: 12rpx; padding: 16rpx; font-size: 26rpx; }
.btn-primary, .btn-ghost { padding: 12rpx 28rpx; border-radius: 30rpx; font-size: 26rpx; }
.btn-primary { background: #c2410c; color: #fff; }
.btn-ghost { border: 2rpx solid #e5e7eb; color: #374151; }
.small { font-size: 26rpx; }
.block { display: block; text-align: center; padding: 24rpx; font-size: 30rpx; border-radius: 44rpx; }
.field { display: flex; align-items: center; justify-content: space-between; padding: 22rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.field input { flex: 1; text-align: right; }
.picker { color: #374151; font-size: 26rpx; }
.hint { margin-top: 14rpx; font-size: 24rpx; color: #6b7280; }
.warn { color: #b45309; }
.demo { color: #1d4ed8; text-decoration: underline; }
.ok { color: #047857; }
.muted { color: #9ca3af; }
.actions { margin-top: 30rpx; }
.mt { margin-top: 16rpx; }
</style>
