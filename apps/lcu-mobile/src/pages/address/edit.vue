<template>
  <view class="page">
    <!-- 后台开关 feature.address_parse 关闭时，整个「粘贴/解析」入口隐藏 -->
    <view v-if="parseEnabled" class="card">
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
        <view class="lab-wrap">
          <text class="label">校区</text>
          <text class="lab-sub">选填 · 仅用于商家筛选</text>
        </view>
        <picker :range="campusNames" @change="onCampus">
          <view class="picker">{{ campusName || '不选' }}</view>
        </picker>
      </view>
      <view class="field">
        <text class="label">详细地址</text>
        <input v-model="form.detail" placeholder="楼栋 + 房间，如 3 号宿舍楼 502" />
      </view>
      <view class="field">
        <text class="label">快速定位</text>
        <picker :range="buildings" range-key="label" @change="onBuilding">
          <view :class="['picker', buildingLabel ? '' : 'muted']">
            {{ buildingLabel || '按楼栋自动填坐标' }}
          </view>
        </picker>
      </view>
      <view class="field">
        <text class="label">楼层</text>
        <input v-model="form.floor" placeholder="如 5" />
      </view>
      <view class="field">
        <text class="label">备注</text>
        <input v-model="form.remark" placeholder="放门口 / 打电话" />
      </view>
      <view class="field">
        <text class="label">地图定位</text>
        <text :class="['picker', form.lng ? '' : 'muted']">
          {{ form.lng ? '已选 ' + form.lng.toFixed(4) + ',' + form.lat.toFixed(4) : '未选择' }}
        </text>
      </view>
      <view class="loc-row">
        <text class="loc-btn" @click="getCurrent">📍 当前位置</text>
        <text class="loc-btn" @click="pickOnMap">🗺 地图选点</text>
        <text class="loc-btn" @click="useDemoPoint">🏫 校园坐标</text>
      </view>
      <view class="hint">「地图选点」可在整个东西校区任意位置取点（不受定位接口权限影响）</view>
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
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api, identityApi } from '../../api'

const campusNames = ['不选', '东校区', '西校区']

/** 后台「地址文本解析」开关，默认展示；接口返回 addressParse === false 才隐藏 */
const parseEnabled = ref(true)
/** 是否从结算页进入的选地址流程：保存后把新地址写回 lcu_pick_address 并直接回购物车 */
const picking = ref(false)

async function loadFeatureSwitch() {
  try {
    const cfg = await identityApi.config()
    parseEnabled.value = cfg?.addressParse !== false
  } catch (e) {
    // 开关接口异常时保留入口，避免误伤正常下单选地址
    parseEnabled.value = true
  }
}

// 东校区楼栋坐标表（全部落在后端围栏 POLYGON 内，已逐个校验 inFence=true）。
// 校园场景下学生地址本来就是「几号楼几零几」，比 GPS 更准，也能绕开游客模式禁用定位的问题。
// 校园地点坐标表：坐标落在后端围栏多边形内（东校区/西校区各自独立围栏），已逐个校验。
// 校园场景下学生地址本来就是「几号楼几零几」，比 GPS 更准，也不受定位接口权限影响。
const buildings = [
  { label: '东·1 号宿舍楼', detail: '1 号宿舍楼', campus: 1, lng: 116.012280, lat: 36.427721 },
  { label: '东·2 号宿舍楼', detail: '2 号宿舍楼', campus: 1, lng: 116.011380, lat: 36.428531 },
  { label: '东·3 号宿舍楼', detail: '3 号宿舍楼', campus: 1, lng: 116.011080, lat: 36.429341 },
  { label: '东·4 号宿舍楼', detail: '4 号宿舍楼', campus: 1, lng: 116.011080, lat: 36.430152 },
  { label: '东·5 号宿舍楼', detail: '5 号宿舍楼', campus: 1, lng: 116.011080, lat: 36.430962 },
  { label: '东·6 号宿舍楼', detail: '6 号宿舍楼', campus: 1, lng: 116.011380, lat: 36.431772 },
  { label: '东·一食堂', detail: '一食堂', campus: 1, lng: 116.011380, lat: 36.432582 },
  { label: '东·二食堂', detail: '二食堂', campus: 1, lng: 116.011380, lat: 36.433392 },
  { label: '东·图书馆', detail: '图书馆', campus: 1, lng: 116.011380, lat: 36.434202 },
  { label: '东·教学楼 A', detail: '教学楼 A', campus: 1, lng: 116.014680, lat: 36.435013 },
  { label: '东·教学楼 B', detail: '教学楼 B', campus: 1, lng: 116.013480, lat: 36.435823 },
  { label: '东·体育馆', detail: '体育馆', campus: 1, lng: 116.015280, lat: 36.436633 },
  { label: '西·1 号宿舍楼', detail: '1 号宿舍楼', campus: 2, lng: 115.995081, lat: 36.428832 },
  { label: '西·2 号宿舍楼', detail: '2 号宿舍楼', campus: 2, lng: 115.995381, lat: 36.431005 },
  { label: '西·3 号宿舍楼', detail: '3 号宿舍楼', campus: 2, lng: 115.995681, lat: 36.433177 },
  { label: '西·食堂', detail: '西区食堂', campus: 2, lng: 115.995981, lat: 36.435349 },
  { label: '西·图书馆', detail: '西区图书馆', campus: 2, lng: 115.995981, lat: 36.437521 },
  { label: '西·教学楼', detail: '西区教学楼', campus: 2, lng: 115.995981, lat: 36.439694 }
]
const buildingLabel = ref('')
const campusLabel: Record<number, string> = { 1: '东校区', 2: '西校区' }

function onBuilding(e: any) {
  const b = buildings[Number(e.detail.value)]
  if (!b) return
  buildingLabel.value = b.label
  form.campus = b.campus
  campusName.value = campusLabel[b.campus] || ''
  form.lng = b.lng
  form.lat = b.lat
  form.detail = form.detail ? form.detail : b.detail
  checkFence()
}

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
  if (!res) {
    uni.showToast({ title: '剪贴板是空的', icon: 'none' })
    return
  }
  raw.value = res
  // 粘贴后直接解析回填，少点一次
  await parse()
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

function applyLoc(res: any) {
  form.lng = res.longitude
  form.lat = res.latitude
  checkFence()
}

function locFailHint() {
  uni.showModal({
    title: '定位不可用',
    content: '常见原因：\n1) 小程序后台「地理位置」接口还没开通\n2) 隐私保护指引没勾选「位置信息」\n3) 手机没给微信定位权限\n\n可以直接选「楼栋」自动填坐标，或点「🏫 校园坐标」继续。',
    showCancel: false
  })
}

function getCurrent() {
  // 注意：微信规定 requiredPrivateInfos 里 getLocation 与 getFuzzyLocation 互斥，
  // 我们声明的是精确的 getLocation（围栏要区分楼栋），所以这里只调它。
  uni.getLocation({ type: 'gcj02', success: applyLoc, fail: locFailHint })
}

function pickOnMap() {
  uni.navigateTo({ url: '/pages/address/pick' })
}

function useDemoPoint() {
  form.lng = 116.011380
  form.lat = 36.432582
  if (!form.campus) { form.campus = 1; campusName.value = '东校区' }
  if (!form.detail) form.detail = '一食堂 2 楼'
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
  if (!form.lng) {
    const ok = await new Promise<boolean>((resolve) => {
      uni.showModal({
        title: '还没选坐标',
        content: '没有坐标的地址不能下单（会提示「请在地图上确认收货位置」）。\n建议先点「地图选点」或选一个楼栋。要继续保存吗？',
        confirmText: '继续保存',
        cancelText: '去选点',
        success: (x) => resolve(!!x.confirm)
      })
    })
    if (!ok) return
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
  const saved: any = form.id
    ? await api.updateAddress(form.id, payload)
    : await api.createAddress(payload)
  uni.showToast({ title: '已保存' })
  // 从结算页新增/编辑地址：写回选地址结果，并一次退回到购物车（cart → address/list → edit）
  if (picking.value && saved?.id) {
    uni.setStorageSync('lcu_pick_address', { ...payload, ...saved })
    setTimeout(() => {
      uni.navigateBack({ delta: 2, fail: () => uni.navigateBack() })
    }, 500)
    return
  }
  setTimeout(() => uni.navigateBack(), 500)
}

onShow(() => {
  const picked = uni.getStorageSync('lcu_pick_point')
  if (!picked || !picked.lng) return
  uni.removeStorageSync('lcu_pick_point')
  form.lng = picked.lng
  form.lat = picked.lat
  if (picked.campus && !form.campus) {
    form.campus = picked.campus
    campusName.value = campusLabel[picked.campus] || ''
  }
  checkFence()
})

onLoad(async (q: any) => {
  picking.value = q?.pick === '1'
  loadFeatureSwitch()
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
.lab-wrap { display: flex; flex-direction: column; }
.lab-sub { font-size: 20rpx; color: #9ca3af; margin-top: 2rpx; }
.loc-row { display: flex; gap: 14rpx; margin-top: 18rpx; }
.loc-btn {
  flex: 1; text-align: center; padding: 18rpx 0;
  background: #f7f8fa; border-radius: 14rpx;
  font-size: 24rpx; color: #374151;
}
</style>
