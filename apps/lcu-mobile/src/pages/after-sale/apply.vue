<template>
  <view class="page">
    <view v-if="order" class="card">
      <view class="between">
        <text class="sec">{{ order.shopName || '店铺' }}</text>
        <text class="muted">{{ order.orderNo }}</text>
      </view>
      <view class="muted mt8">订单状态：{{ order.statusText }}</view>
      <view class="muted mt8">订单实付：{{ payYuan }} 元</view>
    </view>

    <view class="card">
      <view class="sec">售后类型</view>
      <view class="types">
        <view
          v-for="t in TYPES"
          :key="t.value"
          :class="['type', form.type === t.value ? 'on' : '']"
          @click="form.type = t.value"
        >{{ t.label }}</view>
      </view>
    </view>

    <view class="card">
      <view class="between">
        <text class="sec">问题描述 <text class="req">*</text></text>
        <text class="muted">{{ form.reason.length }}/200</text>
      </view>
      <textarea
        v-model="form.reason"
        class="ta mt"
        :maxlength="200"
        placeholder="请说明具体问题，例如少了哪份餐、餐品有什么异常……"
      />
    </view>

    <view class="card">
      <view class="between">
        <text class="sec">申请退款金额</text>
        <text class="muted">最多 {{ payYuan }} 元</text>
      </view>
      <view class="amount-row mt">
        <text class="unit">¥</text>
        <input
          v-model="form.amountYuan"
          class="amount-input"
          type="digit"
          placeholder="0.00"
        />
      </view>
      <view class="muted mt8">默认按订单实付金额申请，可按实际情况修改</view>
    </view>

    <view class="card">
      <view class="between">
        <text class="sec">凭证图片</text>
        <text class="muted">{{ images.length }}/3</text>
      </view>
      <view class="imgs mt">
        <view v-for="(img, i) in images" :key="i" class="img-item">
          <image :src="img" mode="aspectFill" class="img" @click="previewImg(i)" />
          <view class="del" @click="removeImg(i)">×</view>
        </view>
        <view v-if="images.length < 3" class="img-add" @click="chooseImage">+</view>
      </view>
      <view class="muted mt8">图片会先上传到服务器，提交时只传图片地址；最多 3 张</view>
    </view>

    <view class="card note-card">
      <view class="sec">提交后会发生什么</view>
      <view class="muted mt8">· 订单状态变为「售后处理中」，商家会在 24 小时内处理；商家不同意时转平台仲裁。</view>
      <view class="muted mt4">· 处理通过后按申请金额原路退款，订单变为「已退款」；被驳回则订单恢复为「已完成」。</view>
      <view class="muted mt4">· 待处理期间可在「我的售后」里撤销申请。</view>
    </view>

    <view v-if="!canApply" class="tip">仅「已完成」订单可申请售后，当前状态：{{ order?.statusText || '未知' }}。</view>
    <view :class="['submit', canApply && !submitting ? '' : 'off']" @click="submit">
      {{ submitting ? '提交中…' : '提交申请' }}
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { afterSaleApi, api, uploadImage } from '../../api'
import { guard } from '../../utils/auth'

const TYPES = [
  { value: 1, label: '少餐' },
  { value: 2, label: '漏餐' },
  { value: 3, label: '餐品变质' },
  { value: 4, label: '撒漏' },
  { value: 5, label: '地址错误' },
  { value: 6, label: '其他' }
]

const order = ref<any>(null)
const images = ref<string[]>([])
const submitting = ref(false)
const form = reactive({
  type: 1,
  reason: '',
  amountYuan: ''
})

let orderId = 0

const payAmount = computed(() => Number(order.value?.payAmount || 0))
const payYuan = computed(() => (payAmount.value / 100).toFixed(2))
const canApply = computed(() => Number(order.value?.status) === 50)

async function loadOrder() {
  if (!orderId) return
  try {
    const o = await api.order(orderId)
    order.value = o
    if (!form.amountYuan) form.amountYuan = (Number(o?.payAmount || 0) / 100).toFixed(2)
  } catch (e) {
    console.warn(e)
  }
}

function chooseImage() {
  uni.chooseImage({
    count: Math.max(1, 3 - images.value.length),
    sizeType: ['compressed'],
    success: async (r: any) => {
      const paths: string[] = r?.tempFilePaths || []
      if (!paths.length) return
      // 凭证必须真正上传：POST /api/upload（multipart field=file，带 Bearer token）
      uni.showLoading({ title: '上传中…', mask: true })
      try {
        for (const path of paths) {
          if (images.value.length >= 3) break
          const res = await uploadImage(path)
          if (res?.url) images.value = [...images.value, res.url].slice(0, 3)
        }
      } catch (e: any) {
        toast(String(e?.message || '图片上传失败，请重试'))
      } finally {
        uni.hideLoading()
      }
    }
  })
}

function previewImg(i: number) {
  uni.previewImage({ urls: images.value, current: images.value[i] })
}

function removeImg(i: number) {
  images.value = images.value.filter((_, idx) => idx !== i)
}

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

async function submit() {
  if (submitting.value) return
  if (!canApply.value) {
    toast('订单完成后才能申请售后')
    return
  }
  if (!form.type) {
    toast('请选择售后类型')
    return
  }
  const reason = form.reason.trim()
  if (!reason) {
    toast('请填写问题描述')
    return
  }
  const yuan = Number(form.amountYuan)
  if (!Number.isFinite(yuan) || yuan <= 0) {
    toast('请输入有效的退款金额')
    return
  }
  const amount = Math.round(yuan * 100)
  if (amount > payAmount.value) {
    toast('申请金额不能超过订单实付')
    return
  }

  submitting.value = true
  try {
    await afterSaleApi.create({
      orderId,
      type: form.type,
      reason,
      amount,
      images: images.value
    })
    uni.showToast({ title: '已提交，等待商家处理', icon: 'none' })
    setTimeout(() => uni.redirectTo({ url: '/pages/after-sale/list' }), 800)
  } catch (e: any) {
    const msg = String(e?.message || '')
    // 超出售后受理期：后端返回业务码 2000，msg 形如「订单已完成超过 3 天，超出售后受理期…」
    // toast 会截断长文案，这里用 modal 把后端原文完整展示给用户
    if (msg.includes('售后受理期')) {
      uni.showModal({ title: '无法申请售后', content: msg, showCancel: false })
    }
    // 重复申请/状态不符等，request 层已 toast 后端文案
    console.warn(e)
  } finally {
    submitting.value = false
  }
}

onLoad((q: any) => {
  orderId = Number(q?.id)
})

onShow(async () => {
  if (!guard('USER')) return
  if (!orderId) {
    uni.showToast({ title: '缺少订单参数', icon: 'none' })
    return
  }
  await loadOrder()
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
.mt4 { margin-top: 8rpx; }
.mt8 { margin-top: 8rpx; }

.types { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 18rpx; }
.type {
  width: 30%; text-align: center; padding: 18rpx 0; border-radius: 14rpx;
  background: #f7f8fa; color: #4b5563; font-size: 26rpx;
  border: 2rpx solid transparent;
}
.type.on { background: #fdf2ea; color: #c2410c; border-color: #c2410c; font-weight: 600; }

.ta {
  width: 100%; box-sizing: border-box; min-height: 160rpx;
  font-size: 26rpx; line-height: 1.6;
}
.amount-row {
  display: flex; align-items: center;
  border-bottom: 1rpx solid #f0f1f3; padding-bottom: 12rpx;
}
.unit { font-size: 40rpx; color: #c2410c; font-weight: 700; margin-right: 10rpx; }
.amount-input { flex: 1; font-size: 40rpx; color: #1a1d21; }

.imgs { display: flex; flex-wrap: wrap; gap: 16rpx; }
.img-item { position: relative; width: 160rpx; height: 160rpx; }
.img { width: 160rpx; height: 160rpx; border-radius: 14rpx; background: #f3f4f6; }
.del {
  position: absolute; right: -8rpx; top: -8rpx;
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: rgba(0, 0, 0, 0.55); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 26rpx;
}
.img-add {
  width: 160rpx; height: 160rpx; border-radius: 14rpx;
  background: #f7f8fa; color: #9aa1aa; font-size: 52rpx;
  display: flex; align-items: center; justify-content: center;
}

.note-card { background: #fff; }
.tip { color: #b45309; font-size: 24rpx; padding: 0 12rpx 16rpx; }
.submit {
  margin: 20rpx 10rpx 60rpx;
  background: #c2410c; color: #fff; text-align: center;
  padding: 24rpx; border-radius: 44rpx; font-size: 32rpx;
}
.submit.off { background: #e5e7eb; color: #9aa1aa; }
</style>
