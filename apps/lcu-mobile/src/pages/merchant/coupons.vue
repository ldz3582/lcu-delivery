<template>
  <view class="page">
    <view class="card">
      <view class="between">
        <text class="title">店铺优惠券</text>
        <text class="add" @click="toggleForm">{{ formVisible ? '收起' : '+ 新建优惠券' }}</text>
      </view>
      <view class="muted mt8">
        店铺券只对本店生效，用户在本店下单时可用；满减=满 X 减 Y，折扣=打 N 折
      </view>
    </view>

    <!-- 新建表单 -->
    <view v-if="formVisible" class="card">
      <view class="field">
        <text>券名称 <text class="req">*</text></text>
        <input v-model="form.name" placeholder="如：新客立减 5 元" />
      </view>

      <view class="field">
        <text>类型 <text class="req">*</text></text>
        <view class="types">
          <view :class="['type', form.type === 1 ? 'on' : '']" @click="pickType(1)">满减</view>
          <view :class="['type', form.type === 2 ? 'on' : '']" @click="pickType(2)">折扣</view>
        </view>
      </view>

      <view class="field">
        <text>使用门槛（元）</text>
        <input v-model="form.thresholdYuan" type="digit" placeholder="0 表示无门槛" />
      </view>

      <view v-if="form.type === 1" class="field">
        <text>减免金额（元）<text class="req">*</text></text>
        <input v-model="form.amountYuan" type="digit" placeholder="5.00" />
      </view>
      <view v-else class="field">
        <text>折扣（如 8.8 折）<text class="req">*</text></text>
        <input v-model="form.discountInput" type="digit" placeholder="8.8" />
      </view>

      <view class="field">
        <text>发行量 <text class="req">*</text></text>
        <input v-model="form.total" type="number" placeholder="如 100" />
      </view>

      <view class="field">
        <text>开始时间</text>
        <view class="dt">
          <picker mode="date" :value="form.startDate" @change="onStartDate">
            <view class="dt-btn">{{ form.startDate }}</view>
          </picker>
          <picker mode="time" :value="form.startTime" @change="onStartTime">
            <view class="dt-btn">{{ form.startTime }}</view>
          </picker>
        </view>
      </view>

      <view class="field">
        <text>结束时间</text>
        <view class="dt">
          <picker mode="date" :value="form.endDate" @change="onEndDate">
            <view class="dt-btn">{{ form.endDate }}</view>
          </picker>
          <picker mode="time" :value="form.endTime" @change="onEndTime">
            <view class="dt-btn">{{ form.endTime }}</view>
          </picker>
        </view>
      </view>

      <view class="btn" @click="submit">创建优惠券</view>
    </view>

    <!-- 列表 -->
    <view v-if="loading" class="muted loading">加载中…</view>

    <view v-for="c in coupons" :key="c.id" :class="['card', 'ticket', c.status === 1 ? '' : 'off']">
      <view class="left">
        <view class="face">{{ faceValue(c) }}</view>
        <view class="cond">{{ condText(c) }}</view>
      </view>

      <view class="right">
        <view class="name">{{ c.name }}</view>
        <view class="muted">{{ c.desc }}</view>
        <view class="muted">有效期 {{ rangeText(c.startAt, c.endAt) }}</view>
        <view class="muted">剩余 {{ c.remain ?? 0 }} · 已领 {{ c.claimed ?? 0 }} · 已用 {{ c.used ?? 0 }}</view>
      </view>

      <view class="right-ops">
        <switch :checked="c.status === 1" color="#c2410c" @change="toggleStatus(c)" />
        <text :class="['state', c.status === 1 ? 'on' : '']">{{ c.status === 1 ? '上架中' : '已下架' }}</text>
      </view>
    </view>

    <view v-if="!loading && !coupons.length" class="empty">还没有店铺券，点右上角「+ 新建优惠券」</view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { ensureMerchantApproved, merchantApi, type MerchantCoupon } from '../../api'
import { guard } from '../../utils/auth'

const coupons = ref<MerchantCoupon[]>([])
const loading = ref(false)
const formVisible = ref(false)

const form = reactive({
  name: '',
  type: 1,
  thresholdYuan: '0',
  amountYuan: '',
  discountInput: '',
  total: '100',
  startDate: '',
  startTime: '00:00',
  endDate: '',
  endTime: '23:59'
})

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

function pad(n: number) {
  return n < 10 ? '0' + n : '' + n
}

function dateStr(d: Date) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

function initRange() {
  const now = new Date()
  const end = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000)
  form.startDate = dateStr(now)
  form.endDate = dateStr(end)
}

function yuanShort(fen?: number | null) {
  const v = Number(fen || 0) / 100
  return Number.isInteger(v) ? String(v) : v.toFixed(2).replace(/0+$/, '').replace(/\.$/, '')
}

function faceValue(c: MerchantCoupon) {
  if (c.type === 2) return (Number(c.amount || 0) / 100).toFixed(1) + '折'
  return '¥' + yuanShort(c.amount)
}

function condText(c: MerchantCoupon) {
  return Number(c.threshold || 0) > 0 ? `满 ${yuanShort(c.threshold)} 可用` : '无门槛'
}

function rangeText(startAt?: string, endAt?: string) {
  const fmt = (v?: string) => (v ? String(v).replace('T', ' ').slice(0, 16) : '')
  const s = fmt(startAt)
  const e = fmt(endAt)
  if (!s && !e) return '长期有效'
  return `${s} 至 ${e}`
}

async function load() {
  loading.value = true
  try {
    coupons.value = (await merchantApi.coupons()) || []
  } catch (e) {
    coupons.value = []
  } finally {
    loading.value = false
  }
}

function toggleForm() {
  formVisible.value = !formVisible.value
  if (formVisible.value && !form.startDate) initRange()
}

function pickType(t: number) {
  form.type = t
}

function onStartDate(e: any) {
  form.startDate = e?.detail?.value || form.startDate
}

function onStartTime(e: any) {
  form.startTime = e?.detail?.value || form.startTime
}

function onEndDate(e: any) {
  form.endDate = e?.detail?.value || form.endDate
}

function onEndTime(e: any) {
  form.endTime = e?.detail?.value || form.endTime
}

function iso(date: string, time: string) {
  return `${date}T${time}:00`
}

async function submit() {
  const name = form.name.trim()
  if (!name) {
    toast('请填写券名称')
    return
  }
  const threshold = Math.max(0, Math.round(Number(form.thresholdYuan || 0) * 100))

  let amount = 0
  if (form.type === 1) {
    amount = Math.round(Number(form.amountYuan) * 100)
    if (!Number.isFinite(amount) || amount <= 0) {
      toast('请填写减免金额')
      return
    }
  } else {
    // 8.8 折 → 880（千分比）
    const d = Number(form.discountInput)
    if (!Number.isFinite(d) || d <= 0 || d >= 10) {
      toast('折扣请填 0.1~9.9 之间，如 8.8')
      return
    }
    amount = Math.round(d * 100)
    if (amount < 1 || amount > 999) {
      toast('折扣需在 1~999 之间（880 = 8.8 折）')
      return
    }
  }

  const total = Number(form.total)
  if (!Number.isFinite(total) || total <= 0) {
    toast('发行量必须大于 0')
    return
  }

  const startAt = iso(form.startDate, form.startTime)
  const endAt = iso(form.endDate, form.endTime)
  if (endAt <= startAt) {
    toast('结束时间必须晚于开始时间')
    return
  }

  try {
    await merchantApi.createCoupon({
      name,
      type: form.type,
      threshold,
      amount,
      total: Math.floor(total),
      startAt,
      endAt
    })
    uni.showToast({ title: '已创建' })
    formVisible.value = false
    form.name = ''
    form.amountYuan = ''
    form.discountInput = ''
    await load()
  } catch (e) {
    // 名称空/折扣范围/发行量/时间倒置/店铺未过审(403) 等由 request 层 toast 后端文案
    console.warn(e)
  }
}

async function toggleStatus(c: MerchantCoupon) {
  const next = c.status === 1 ? 0 : 1
  try {
    await merchantApi.toggleCoupon(c.id, next)
    uni.showToast({ title: next === 1 ? '已上架' : '已下架', icon: 'none' })
  } catch (e) {
    console.warn(e)
  }
  load()
}

onShow(async () => {
  if (!guard('MERCHANT')) return
  if (!(await ensureMerchantApproved())) return
  if (!form.startDate) initRange()
  await load()
})
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.title { font-size: 30rpx; font-weight: 700; }
.add { color: #c2410c; font-size: 26rpx; }
.muted { color: #7a828c; font-size: 24rpx; }
.req { color: #dc2626; }
.mt8 { margin-top: 8rpx; }

.field {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 26rpx;
}
.field input { flex: 1; text-align: right; margin-left: 20rpx; font-size: 26rpx; }
.types { display: flex; gap: 12rpx; }
.type {
  font-size: 24rpx; padding: 8rpx 22rpx; border-radius: 26rpx;
  background: #f7f8fa; color: #4b5563;
}
.type.on { background: #fdf2ea; color: #c2410c; font-weight: 600; }
.dt { display: flex; gap: 10rpx; }
.dt-btn {
  background: #f7f8fa; border-radius: 10rpx; padding: 10rpx 18rpx;
  font-size: 24rpx; color: #374151;
}

.btn {
  margin-top: 24rpx; background: #c2410c; color: #fff; text-align: center;
  padding: 20rpx; border-radius: 40rpx; font-size: 28rpx;
}

.ticket { display: flex; align-items: center; position: relative; }
.ticket.off { background: #f3f4f6; }
.ticket.off .face, .ticket.off .name { color: #9aa1aa; }
.left {
  width: 160rpx; flex-shrink: 0; text-align: center;
  border-right: 2rpx dashed #f0e2d8; padding-right: 14rpx;
}
.ticket.off .left { border-right-color: #e5e7eb; }
.face { font-size: 36rpx; font-weight: 700; color: #c2410c; line-height: 1.2; }
.ticket.off .face { color: #9aa1aa; }
.cond { font-size: 20rpx; color: #9aa1aa; margin-top: 6rpx; }
.right { flex: 1; padding-left: 20rpx; min-width: 0; }
.name { font-size: 28rpx; font-weight: 600; margin-bottom: 6rpx; color: #1a1d21; }
.right .muted { font-size: 22rpx; line-height: 1.6; }
.right-ops { display: flex; flex-direction: column; align-items: flex-end; gap: 6rpx; margin-left: 10rpx; }
.state { font-size: 20rpx; color: #9aa1aa; }
.state.on { color: #047857; }

.empty { text-align: center; color: #9ca3af; padding: 100rpx 0; font-size: 26rpx; }
.loading { text-align: center; padding: 30rpx 0; }
</style>
