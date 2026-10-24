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
          <text class="sec">实名信息</text>
          <text class="tag">待审核</text>
        </view>
        <view class="muted mt8">姓名 {{ st?.realNameMask || '—' }}</view>
        <view class="muted mt8">身份证 {{ st?.idCardMask || '—' }}</view>
        <view class="muted mt8">学号/工号 {{ st?.stuNoMask || '—' }}</view>
        <view class="muted mt8">手机号 {{ st?.phoneMask || '—' }}</view>
        <view class="muted mt8">审核期间资料不可修改，通过后即可开始抢单。</view>
      </view>

      <view class="card">
        <view class="sec">证件照片</view>
        <view class="pics mt">
          <view v-for="(p, i) in submittedPics" :key="i" class="pic-wrap">
            <image :src="p.url" mode="aspectFill" class="pic" @click="preview(p.url)" />
            <view class="pic-label">{{ p.label }}</view>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="between">
          <text class="muted">协议签署时间</text>
          <text>{{ dtText(st?.agreementSignedAt) }}</text>
        </view>
        <view class="between mt8">
          <text class="muted">意外险保单号</text>
          <text>{{ st?.insuranceNo || '待运营填写' }}</text>
        </view>
        <view class="between mt8">
          <text class="muted">累计接单</text>
          <text>{{ st?.totalOrders ?? 0 }} 单</text>
        </view>
      </view>

      <view class="link" @click="goMine">返回我的</view>
    </view>

    <!-- 入驻表单：未提交 / 已驳回 -->
    <view v-else>
      <view v-if="mode === 'rejected'" class="reject">
        <view class="reject-title">入驻申请被驳回</view>
        <view class="reject-reason">{{ st?.rejectReason || '未填写驳回原因' }}</view>
        <view class="reject-tip">
          按提示修改后重新提交即可。当前资料：{{ maskText }}；
          姓名 / 身份证 / 学号 / 手机<text class="strong">留空表示不修改</text>，只改要改的字段。
        </view>
      </view>

      <view v-if="isResubmit" class="resubmit-tip">
        当前资料：{{ maskText }}。姓名 / 身份证 / 学号 / 手机留空即沿用原值，三张图留空也沿用原图。
      </view>

      <view class="card">
        <view class="field">
          <text>真实姓名 <text v-if="!isResubmit" class="req">*</text></text>
          <input v-model="form.realName" placeholder="与校园卡一致" />
        </view>
        <view class="field">
          <text>身份证号 <text class="req">*</text></text>
          <input v-model="form.idCard" maxlength="18" placeholder="18 位，末位可为 X" />
        </view>
        <view class="field">
          <text>学号/工号 <text class="req">*</text></text>
          <input v-model="form.stuNo" maxlength="20" placeholder="6~20 位字母或数字" />
        </view>
        <view class="field">
          <text>手机号 <text class="req">*</text></text>
          <input v-model="form.phone" type="number" maxlength="11" placeholder="11 位手机号" />
        </view>
      </view>

      <view class="card">
        <view class="sec">证件照片 <text class="req">*</text></view>
        <view class="uploads mt">
          <view class="up-col">
            <view v-if="form.idCardFront" class="up-box">
              <image :src="form.idCardFront" mode="aspectFill" class="up-img" @click="preview(form.idCardFront)" />
              <view class="up-del" @click.stop="form.idCardFront = ''">×</view>
            </view>
            <view v-else class="up-add" @click="chooseAndUpload('idCardFront')">
              <text class="up-plus">{{ uploading === 'idCardFront' ? '⏳' : '+' }}</text>
            </view>
            <view class="up-label">身份证人像面</view>
          </view>

          <view class="up-col">
            <view v-if="form.idCardBack" class="up-box">
              <image :src="form.idCardBack" mode="aspectFill" class="up-img" @click="preview(form.idCardBack)" />
              <view class="up-del" @click.stop="form.idCardBack = ''">×</view>
            </view>
            <view v-else class="up-add" @click="chooseAndUpload('idCardBack')">
              <text class="up-plus">{{ uploading === 'idCardBack' ? '⏳' : '+' }}</text>
            </view>
            <view class="up-label">身份证国徽面</view>
          </view>

          <view class="up-col">
            <view v-if="form.studentCard" class="up-box">
              <image :src="form.studentCard" mode="aspectFill" class="up-img" @click="preview(form.studentCard)" />
              <view class="up-del" @click.stop="form.studentCard = ''">×</view>
            </view>
            <view v-else class="up-add" @click="chooseAndUpload('studentCard')">
              <text class="up-plus">{{ uploading === 'studentCard' ? '⏳' : '+' }}</text>
            </view>
            <view class="up-label">校园卡/学生证</view>
          </view>
        </view>
        <view class="muted mt8">支持 jpg / png / webp / gif，单张不超过 5MB</view>
      </view>

      <view class="card">
        <view class="between" @click="showProtocol = !showProtocol">
          <text class="sec">《校园骑手兼职协议》</text>
          <text class="muted">{{ showProtocol ? '收起 ▲' : '展开 ▼' }}</text>
        </view>
        <view v-if="showProtocol" class="protocol mt">
          <view class="muted">1. 你以兼职身份为平台提供校内配送服务，不构成劳动关系。</view>
          <view class="muted">2. 须遵守校园交通与配送规范，配送过程中注意人身与餐品安全。</view>
          <view class="muted">3. 平台统一投保意外险，出车时段内发生意外的按保单约定理赔。</view>
          <view class="muted">4. 超时、虚假送达、恶意取消等行为按平台规则扣除相应佣金。</view>
          <view class="muted">5. 每周可自主开启/关闭接单状态，已接订单需完成配送。</view>
        </view>

        <view class="agree mt" @click="toggleAgree">
          <view :class="['checkbox', agree ? 'on' : '']">{{ agree ? '✓' : '' }}</view>
          <text class="agree-text">我已阅读并同意《校园骑手兼职协议》</text>
        </view>
      </view>

      <view class="card">
        <view class="field">
          <text>意外险保单号</text>
          <input v-model="form.insuranceNo" placeholder="选填" />
        </view>
        <view class="muted mt8">平台统一投保，保单号由运营填写；没有可留空。</view>
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
import { riderApi, uploadImage, type RiderStatus } from '../../api'
import { guard } from '../../utils/auth'

type UploadField = 'idCardFront' | 'idCardBack' | 'studentCard'

const loading = ref(true)
const submitting = ref(false)
const uploading = ref<'' | UploadField>('')
const showProtocol = ref(false)
const agree = ref(false)
const st = ref<RiderStatus | null>(null)

const form = reactive({
  realName: '',
  idCard: '',
  stuNo: '',
  phone: '',
  idCardFront: '',
  idCardBack: '',
  studentCard: '',
  insuranceNo: ''
})

const mode = computed<'form' | 'pending' | 'rejected'>(() => {
  const s = st.value
  if (!s || !s.applied) return 'form'
  if (s.auditStatus === 0) return 'pending'
  if (s.auditStatus === 2) return 'rejected'
  return 'form'
})

const isResubmit = computed(() => mode.value === 'rejected')

const maskText = computed(() => {
  const s = st.value
  const parts = [s?.realNameMask, s?.idCardMask, s?.stuNoMask].filter(Boolean)
  return parts.length ? parts.join(' / ') : '证件信息已提交'
})

const submittedPics = computed(() => {
  const s = st.value
  if (!s) return []
  return [
    { label: '身份证人像面', url: s.idCardFront },
    { label: '身份证国徽面', url: s.idCardBack },
    { label: '校园卡/学生证', url: s.studentCard }
  ].filter((x): x is { label: string; url: string } => !!x.url)
})

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

async function loadStatus() {
  try {
    st.value = await riderApi.status()
  } catch (e) {
    st.value = { applied: false }
  }
}

/** 驳回后回填能回填的资料（证件号码后端加密存储、不回传，需重新填） */
function prefill() {
  const s = st.value
  if (!s?.applied) return
  form.idCardFront = s.idCardFront || ''
  form.idCardBack = s.idCardBack || ''
  form.studentCard = s.studentCard || ''
  form.insuranceNo = s.insuranceNo || ''
}

/** 后端返回 ISO 时间（2026-10-24T15:38:14），展示成空格分隔的前 16 位 */
function dtText(v?: string) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

function toggleAgree() {
  agree.value = !agree.value
}

function chooseAndUpload(field: UploadField) {
  if (uploading.value) {
    toast('图片上传中，请稍候')
    return
  }
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (r: any) => {
      const path = r?.tempFilePaths?.[0]
      if (!path) return
      uploading.value = field
      try {
        const res = await uploadImage(path)
        form[field] = res.url
      } catch (e: any) {
        toast(String(e?.message || '上传失败，请重试'))
      } finally {
        uploading.value = ''
      }
    }
  })
}

function preview(url?: string) {
  if (!url) return
  const urls = [form.idCardFront, form.idCardBack, form.studentCard].filter(Boolean) as string[]
  uni.previewImage({ urls: urls.length ? urls : [url], current: url })
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

  const realName = form.realName.trim()
  const idCard = form.idCard.trim().toUpperCase()
  const stuNo = form.stuNo.trim()
  const phone = form.phone.trim()

  if (!isResubmit.value) {
    // 首次提交：全部必填
    if (!realName) return toast('请填写真实姓名')
    if (!/^\d{17}[\dX]$/.test(idCard)) return toast('身份证号格式不正确（18 位）')
    if (!/^[A-Za-z0-9]{6,20}$/.test(stuNo)) return toast('学号/工号需为 6~20 位字母或数字')
    if (!/^\d{11}$/.test(phone)) return toast('手机号格式不正确')
    if (!form.idCardFront || !form.idCardBack || !form.studentCard) {
      return toast('请上传身份证正反面和校园卡照片')
    }
  } else {
    // 驳回重提：留空 = 沿用原值，只校验填了的部分
    if (idCard && !/^\d{17}[\dX]$/.test(idCard)) return toast('身份证号格式不正确（18 位）')
    if (stuNo && !/^[A-Za-z0-9]{6,20}$/.test(stuNo)) return toast('学号/工号需为 6~20 位字母或数字')
    if (phone && !/^\d{11}$/.test(phone)) return toast('手机号格式不正确')
  }
  if (!agree.value) return toast('请先阅读并同意兼职协议')

  submitting.value = true
  try {
    const payload: Record<string, any> = { agreeProtocol: true }
    if (realName) payload.realName = realName
    if (idCard) payload.idCard = idCard
    if (stuNo) payload.stuNo = stuNo
    if (phone) payload.phone = phone
    if (form.idCardFront) payload.idCardFront = form.idCardFront
    if (form.idCardBack) payload.idCardBack = form.idCardBack
    if (form.studentCard) payload.studentCard = form.studentCard
    if (form.insuranceNo.trim()) payload.insuranceNo = form.insuranceNo.trim()
    await riderApi.apply(payload as any)
    uni.showToast({ title: '已提交，等待审核', icon: 'none' })
    await loadStatus()
  } catch (e) {
    // 校验/重复提交等由 request 层 toast 后端文案
    console.warn(e)
  } finally {
    submitting.value = false
  }
}

onShow(async () => {
  if (!guard('RIDER')) return
  loading.value = true
  await loadStatus()

  // 已通过审核：直接进抢单大厅
  if (st.value?.applied && st.value.auditStatus === 1) {
    uni.reLaunch({ url: '/pages/rider/pool' })
    return
  }

  if (mode.value === 'rejected') prefill()
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
.pics { display: flex; flex-wrap: wrap; gap: 16rpx; }
.pic-wrap { width: 200rpx; }
.pic { width: 200rpx; height: 140rpx; border-radius: 12rpx; background: #f3f4f6; }
.pic-label { font-size: 20rpx; color: #7a828c; text-align: center; margin-top: 6rpx; }

.reject {
  background: #fef2f2; border: 1rpx solid #fecaca; border-radius: 20rpx;
  padding: 24rpx; margin-bottom: 20rpx;
}
.reject-title { color: #b91c1c; font-size: 28rpx; font-weight: 600; }
.reject-reason { color: #b91c1c; font-size: 26rpx; margin-top: 10rpx; line-height: 1.5; }
.reject-tip { color: #9ca3af; font-size: 22rpx; margin-top: 10rpx; line-height: 1.6; }
.strong { color: #b45309; font-weight: 600; }
.resubmit-tip {
  background: #fff7ed; border: 1rpx solid #fed7aa; color: #b45309;
  border-radius: 16rpx; padding: 18rpx 20rpx; font-size: 22rpx; line-height: 1.6;
  margin-bottom: 20rpx;
}

.field {
  display: flex; align-items: center; justify-content: space-between;
  padding: 22rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 28rpx;
}
.field:last-child { border-bottom: none; }
.field input { flex: 1; text-align: right; margin-left: 20rpx; font-size: 26rpx; }

.uploads { display: flex; flex-wrap: wrap; gap: 18rpx; }
.up-col { width: 200rpx; }
.up-box, .up-add { width: 200rpx; height: 140rpx; border-radius: 14rpx; position: relative; overflow: hidden; }
.up-box { background: #f3f4f6; }
.up-img { width: 200rpx; height: 140rpx; }
.up-del {
  position: absolute; right: 0; top: 0; width: 42rpx; height: 42rpx;
  border-radius: 0 14rpx 0 14rpx; background: rgba(0, 0, 0, 0.55); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 24rpx;
}
.up-add {
  background: #f7f8fa; border: 2rpx dashed #d9dee5;
  display: flex; align-items: center; justify-content: center;
}
.up-plus { color: #9aa1aa; font-size: 44rpx; }
.up-label { margin-top: 8rpx; font-size: 22rpx; color: #7a828c; text-align: center; }

.protocol { background: #fafafa; border-radius: 14rpx; padding: 16rpx; }
.protocol .muted { line-height: 1.8; }
.agree { display: flex; align-items: center; gap: 14rpx; }
.checkbox {
  width: 40rpx; height: 40rpx; border-radius: 8rpx; border: 2rpx solid #d1d5db;
  display: flex; align-items: center; justify-content: center;
  color: #fff; font-size: 26rpx; flex-shrink: 0;
}
.checkbox.on { background: #c2410c; border-color: #c2410c; }
.agree-text { font-size: 24rpx; color: #374151; }

.submit {
  margin: 10rpx 10rpx 20rpx;
  background: #c2410c; color: #fff; text-align: center;
  padding: 24rpx; border-radius: 44rpx; font-size: 32rpx;
}
.submit.off { background: #e5e7eb; color: #9aa1aa; }
.link { text-align: center; color: #7a828c; font-size: 24rpx; padding: 10rpx 0 50rpx; }
.loading { text-align: center; padding: 120rpx 0; }
</style>
