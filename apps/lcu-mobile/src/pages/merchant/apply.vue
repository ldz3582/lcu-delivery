<template>
  <view class="page">
    <view v-if="loading" class="muted loading">加载中…</view>

    <!-- 审核中 -->
    <view v-else-if="mode === 'pending'">
      <view class="status-card">
        <view class="status-title">审核中</view>
        <view class="status-sub">资料已提交，平台会在 1 个工作日内完成审核</view>
      </view>

      <view class="card">
        <view class="between">
          <text class="sec">{{ st?.name || '店铺' }}</text>
          <text class="tag">待审核</text>
        </view>
        <view class="muted mt8">{{ st?.address || '地址待补充' }}</view>
        <view class="muted mt8">联系电话 {{ st?.contactPhone || '—' }}</view>

        <view v-if="submittedPics.length" class="pics mt">
          <image
            v-for="(p, i) in submittedPics"
            :key="i"
            :src="p"
            mode="aspectFill"
            class="pic"
            @click="preview(p)"
          />
        </view>

        <view class="muted mt8">审核期间资料不可修改，通过后即可开始接单。</view>
      </view>

      <view class="link" @click="goMine">返回我的</view>
    </view>

    <!-- 表单：未入驻 / 已驳回 -->
    <view v-else>
      <view v-if="mode === 'rejected'" class="reject">
        <view class="reject-title">入驻申请被驳回</view>
        <view class="reject-reason">{{ st?.rejectReason || '未填写驳回原因' }}</view>
        <view class="reject-tip">按提示修改下方资料后重新提交即可</view>
      </view>

      <view class="card">
        <view class="field">
          <text>店铺名称 <text class="req">*</text></text>
          <input v-model="form.name" placeholder="如：一食堂·黄焖鸡" />
        </view>

        <picker mode="selector" :range="categoryNames" :value="categoryIndex" @change="onCategory">
          <view class="field">
            <text>经营品类 <text class="req">*</text></text>
            <text :class="form.categoryId ? '' : 'placeholder'">{{ categoryText }} ›</text>
          </view>
        </picker>

        <picker mode="selector" :range="CAMPUS_LABELS" :value="campusIndex" @change="onCampus">
          <view class="field">
            <text>所在校区 <text class="req">*</text></text>
            <text>{{ CAMPUS_LABELS[campusIndex] }} ›</text>
          </view>
        </picker>

        <view class="field">
          <text>店铺地址 <text class="req">*</text></text>
          <input v-model="form.address" placeholder="如：东校区一食堂2楼12号窗口" />
        </view>

        <view class="field">
          <text>联系电话 <text class="req">*</text></text>
          <input v-model="form.contactPhone" type="number" placeholder="手机号或座机" />
        </view>

        <view class="field">
          <text>经度</text>
          <input v-model="form.lng" type="digit" placeholder="可留空" />
        </view>

        <view class="field">
          <text>纬度</text>
          <input v-model="form.lat" type="digit" placeholder="可留空" />
        </view>

        <view class="pick-line">
          <text class="muted">
            {{ form.lng && form.lat ? `已选点 ${form.lng}, ${form.lat}` : '地图选点（可选，便于骑手导航）' }}
          </text>
          <text class="pick-btn" @click="pickPoint">去选点 ›</text>
        </view>
      </view>

      <!-- 三证上传 -->
      <view class="card">
        <view class="sec">证照与门店照片</view>

        <view class="uploads mt">
          <view class="up-col">
            <view v-if="form.licenseUrl" class="up-box">
              <image :src="form.licenseUrl" mode="aspectFill" class="up-img" @click="preview(form.licenseUrl)" />
              <view class="up-del" @click.stop="clearPic('license')">×</view>
            </view>
            <view v-else class="up-add" @click="chooseAndUpload('license')">
              <text class="up-plus">{{ uploading === 'license' ? '⏳' : '+' }}</text>
            </view>
            <view class="up-label">营业执照 <text class="req">*</text></view>
          </view>

          <view class="up-col">
            <view v-if="form.permitUrl" class="up-box">
              <image :src="form.permitUrl" mode="aspectFill" class="up-img" @click="preview(form.permitUrl)" />
              <view class="up-del" @click.stop="clearPic('permit')">×</view>
            </view>
            <view v-else class="up-add" @click="chooseAndUpload('permit')">
              <text class="up-plus">{{ uploading === 'permit' ? '⏳' : '+' }}</text>
            </view>
            <view class="up-label">食品经营许可证 <text class="req">*</text></view>
          </view>
        </view>

        <view class="uploads mt">
          <view v-for="(p, i) in form.photos" :key="'p' + i" class="up-col">
            <view class="up-box">
              <image :src="p" mode="aspectFill" class="up-img" @click="preview(p)" />
              <view class="up-del" @click.stop="removePhoto(i)">×</view>
            </view>
            <view class="up-label">门店照</view>
          </view>
          <view v-if="form.photos.length < 3" class="up-col">
            <view class="up-add" @click="chooseAndUpload('photos')">
              <text class="up-plus">{{ uploading === 'photos' ? '⏳' : '+' }}</text>
            </view>
            <view class="up-label">门店照（最多3张）<text class="req">*</text></view>
          </view>
        </view>

        <view class="muted mt8">支持 jpg / png / webp / gif，单张不超过 5MB</view>
      </view>

      <view :class="['submit', submitting || uploading ? 'off' : '']" @click="submit">
        {{ submitting ? '提交中…' : (mode === 'rejected' ? '重新提交审核' : '提交入驻申请') }}
      </view>
      <view class="link" @click="goMine">返回我的</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { api, merchantApi, uploadImage, type Category, type MerchantStatus } from '../../api'
import { guard } from '../../utils/auth'

type UploadKind = 'license' | 'permit' | 'photos'

const CAMPUS_LABELS = ['东校区', '西校区']

const loading = ref(true)
const submitting = ref(false)
const uploading = ref<'' | UploadKind>('')
const st = ref<MerchantStatus | null>(null)
const categories = ref<Category[]>([])

const form = reactive({
  name: '',
  categoryId: 0,
  campus: 1,
  address: '',
  contactPhone: '',
  lng: '',
  lat: '',
  licenseUrl: '',
  permitUrl: '',
  photos: [] as string[]
})

/** 只预填一次，避免从地图选点页返回时把用户改动覆盖掉 */
let prefilled = false

const mode = computed<'form' | 'pending' | 'rejected'>(() => {
  const s = st.value
  if (!s || !s.applied) return 'form'
  if (s.auditStatus === 0) return 'pending'
  if (s.auditStatus === 2) return 'rejected'
  return 'form'
})

const categoryNames = computed(() => categories.value.map((c) => c.name))
const categoryIndex = computed(() => {
  const i = categories.value.findIndex((c) => c.id === form.categoryId)
  return i < 0 ? 0 : i
})
const categoryText = computed(() => categories.value.find((c) => c.id === form.categoryId)?.name || '请选择')
const campusIndex = computed(() => Math.max(0, Math.min(1, Number(form.campus) - 1)))

const submittedPics = computed(() => {
  const s = st.value
  if (!s) return []
  return [s.licenseUrl, s.permitUrl, ...(s.photos || [])].filter((x): x is string => !!x)
})

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

function onCategory(e: any) {
  const i = Number(e?.detail?.value || 0)
  const c = categories.value[i]
  if (c) form.categoryId = c.id
}

function onCampus(e: any) {
  form.campus = Number(e?.detail?.value || 0) + 1
}

async function loadStatus() {
  try {
    st.value = await merchantApi.status()
  } catch (e) {
    st.value = { applied: false }
  }
}

function prefill() {
  const s = st.value
  if (!s?.applied) return
  form.name = s.name || ''
  form.categoryId = Number(s.categoryId || 0)
  form.campus = Number(s.campus || 1)
  form.address = s.address || ''
  form.contactPhone = s.contactPhone || ''
  form.licenseUrl = s.licenseUrl || ''
  form.permitUrl = s.permitUrl || ''
  form.photos = [...(s.photos || [])].slice(0, 3)
}

function chooseAndUpload(kind: UploadKind) {
  if (uploading.value) {
    toast('图片上传中，请稍候')
    return
  }
  const count = kind === 'photos' ? Math.max(1, 3 - form.photos.length) : 1
  uni.chooseImage({
    count,
    sizeType: ['compressed'],
    success: async (r: any) => {
      const paths: string[] = r?.tempFilePaths || []
      for (const p of paths) {
        uploading.value = kind
        try {
          const res = await uploadImage(p)
          if (kind === 'license') form.licenseUrl = res.url
          else if (kind === 'permit') form.permitUrl = res.url
          else if (form.photos.length < 3) form.photos.push(res.url)
        } catch (e: any) {
          toast(String(e?.message || '上传失败，请重试'))
          break
        }
      }
      uploading.value = ''
    }
  })
}

function preview(url?: string) {
  if (!url) return
  const urls = [form.licenseUrl, form.permitUrl, ...form.photos].filter(Boolean) as string[]
  uni.previewImage({ urls: urls.length ? urls : [url], current: url })
}

function clearPic(kind: 'license' | 'permit') {
  if (kind === 'license') form.licenseUrl = ''
  else form.permitUrl = ''
}

function removePhoto(i: number) {
  form.photos = form.photos.filter((_, idx) => idx !== i)
}

function pickPoint() {
  uni.navigateTo({ url: '/pages/address/pick' })
}

function goMine() {
  uni.reLaunch({ url: '/pages/mine/index' })
}

async function submit() {
  if (submitting.value) return
  if (uploading.value) {
    toast('图片还在上传中，请稍候')
    return
  }
  const name = form.name.trim()
  const address = form.address.trim()
  const phone = form.contactPhone.trim()

  if (!name) return toast('请填写店铺名称')
  if (!form.categoryId) return toast('请选择经营品类')
  if (!address) return toast('请填写店铺地址')
  if (!/^1\d{10}$/.test(phone) && !/^0\d{2,3}-?\d{7,8}$/.test(phone)) return toast('请填写正确的联系电话')
  if (!form.licenseUrl) return toast('请上传营业执照')
  if (!form.permitUrl) return toast('请上传食品经营许可证')
  if (!form.photos.length) return toast('请上传门店照片')

  submitting.value = true
  try {
    await merchantApi.apply({
      name,
      categoryId: form.categoryId,
      campus: form.campus,
      address,
      lng: form.lng ? Number(form.lng) : null,
      lat: form.lat ? Number(form.lat) : null,
      contactPhone: phone,
      licenseUrl: form.licenseUrl,
      permitUrl: form.permitUrl,
      photos: [...form.photos]
    })
    uni.showToast({ title: '已提交，等待审核', icon: 'none' })
    await loadStatus()
  } catch (e) {
    // 参数校验/重复提交等由 request 层 toast 后端文案
    console.warn(e)
  } finally {
    submitting.value = false
  }
}

onShow(async () => {
  if (!guard('MERCHANT')) return
  loading.value = true
  await loadStatus()

  if (!categories.value.length) {
    try {
      categories.value = await api.categories()
    } catch (e) {
      categories.value = []
    }
  }

  // 已通过审核：直接进商家端，不留在入驻页
  if (st.value?.applied && st.value.auditStatus === 1) {
    uni.reLaunch({ url: '/pages/merchant/orders' })
    return
  }

  // 被驳回时预填上次提交的资料，方便改完重提
  if (mode.value === 'rejected' && !prefilled) {
    prefill()
    prefilled = true
  }

  // 地图选点结果（pick 页写 storage 后返回）
  const picked = uni.getStorageSync('lcu_pick_point')
  if (picked && picked.lng) {
    uni.removeStorageSync('lcu_pick_point')
    form.lng = String(picked.lng)
    form.lat = String(picked.lat)
    if (picked.campus) form.campus = Number(picked.campus)
  }

  loading.value = false
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; background: #f5f6f8; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.sec { font-size: 28rpx; font-weight: 600; }
.req { color: #dc2626; }
.muted { color: #7a828c; font-size: 24rpx; }
.mt { margin-top: 16rpx; }
.mt8 { margin-top: 8rpx; }

.status-card {
  background: linear-gradient(135deg, #c2410c, #ea580c);
  color: #fff; border-radius: 20rpx; padding: 32rpx; margin-bottom: 20rpx;
}
.status-title { font-size: 40rpx; font-weight: 700; }
.status-sub { font-size: 24rpx; opacity: 0.9; margin-top: 8rpx; }
.tag {
  font-size: 22rpx; color: #b45309; background: #fef3c7;
  padding: 4rpx 16rpx; border-radius: 18rpx;
}
.pics { display: flex; flex-wrap: wrap; gap: 14rpx; }
.pic { width: 150rpx; height: 150rpx; border-radius: 12rpx; background: #f3f4f6; }

.reject {
  background: #fef2f2; border: 1rpx solid #fecaca; border-radius: 20rpx;
  padding: 24rpx; margin-bottom: 20rpx;
}
.reject-title { color: #b91c1c; font-size: 28rpx; font-weight: 600; }
.reject-reason { color: #b91c1c; font-size: 26rpx; margin-top: 10rpx; line-height: 1.5; }
.reject-tip { color: #9ca3af; font-size: 22rpx; margin-top: 10rpx; }

.field {
  display: flex; align-items: center; justify-content: space-between;
  padding: 22rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 28rpx;
}
.field:last-of-type { border-bottom: none; }
.field input { flex: 1; text-align: right; font-size: 26rpx; margin-left: 20rpx; }
.placeholder { color: #b6bcc4; font-size: 26rpx; }
.pick-line {
  display: flex; align-items: center; justify-content: space-between;
  padding-top: 18rpx; border-top: 1rpx solid #f2f3f5;
}
.pick-btn { color: #c2410c; font-size: 24rpx; }

.uploads { display: flex; flex-wrap: wrap; gap: 18rpx; }
.up-col { width: 190rpx; }
.up-box, .up-add {
  width: 190rpx; height: 190rpx; border-radius: 14rpx; position: relative; overflow: hidden;
}
.up-box { background: #f3f4f6; }
.up-img { width: 190rpx; height: 190rpx; }
.up-del {
  position: absolute; right: 0; top: 0;
  width: 44rpx; height: 44rpx; border-radius: 0 14rpx 0 14rpx;
  background: rgba(0, 0, 0, 0.55); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 26rpx;
}
.up-add {
  background: #f7f8fa; border: 2rpx dashed #d9dee5;
  display: flex; align-items: center; justify-content: center;
}
.up-plus { color: #9aa1aa; font-size: 48rpx; }
.up-label { margin-top: 8rpx; font-size: 22rpx; color: #7a828c; text-align: center; }

.submit {
  margin: 10rpx 10rpx 20rpx;
  background: #c2410c; color: #fff; text-align: center;
  padding: 24rpx; border-radius: 44rpx; font-size: 32rpx;
}
.submit.off { background: #e5e7eb; color: #9aa1aa; }
.link { text-align: center; color: #7a828c; font-size: 24rpx; padding: 10rpx 0 50rpx; }
.loading { text-align: center; padding: 120rpx 0; }
</style>
