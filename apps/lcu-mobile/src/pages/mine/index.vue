<template>
  <view class="page">
    <view class="head">
      <view class="avatar">{{ (nickname || '同').slice(0, 1) }}</view>
      <view class="hinfo">
        <view class="nick">{{ nickname }}</view>
        <view class="role">{{ roleLabel }}端</view>
      </view>
    </view>

    <view v-if="role === 'USER'" class="card menu">
      <view class="item" @click="go('/pages/address/list')">
        <text>收货地址</text><text class="arrow">›</text>
      </view>
      <view class="item" @click="go('/pages/order/list')">
        <text>我的订单</text><text class="arrow">›</text>
      </view>
      <view class="item" @click="go('/pages/coupon/index')">
        <text>我的优惠券</text><text class="arrow">›</text>
      </view>
      <view class="item" @click="go('/pages/favorite/index')">
        <text>我的收藏</text><text class="arrow">›</text>
      </view>
      <view class="item" @click="onNotify">
        <view>
          <view>消息通知</view>
          <view class="muted">订单状态、优惠券提醒</view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <view v-if="role !== 'USER'" class="card menu">
      <view class="item" @click="switchAccount">
        <view>
          <view>{{ role === 'MERCHANT' ? '切换店铺' : '切换骑手账号' }}</view>
          <view class="muted">{{ accountName || '点击选择要进入的账号' }}</view>
        </view>
        <text class="arrow">›</text>
      </view>
      <view v-if="role === 'MERCHANT'" class="item" @click="go('/pages/merchant/apply')">
        <view>
          <view>入驻状态</view>
          <view class="muted">{{ merchantStatusText }}</view>
        </view>
        <text class="arrow">›</text>
      </view>
      <view v-if="role === 'MERCHANT'" class="item" @click="go('/pages/merchant/finance')">
        <view>
          <view>资金与提现</view>
          <view class="muted">余额、对账单、提现记录</view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <view v-if="role === 'USER'" class="card">
      <view class="sec-title">校内身份</view>
      <view class="row between ident">
        <view>
          <text class="ident-state">
            {{ identity.verified ? '已核验 ' + (identity.stuNoMask || '') : '未核验' }}
          </text>
          <view class="muted ident-sub">
            {{ identity.required ? '平台已开启学号/工号核验，未核验无法下单' : '平台未开启核验，非本校人员也可下单' }}
          </view>
        </view>
        <text v-if="!identity.verified" class="ident-btn" @click="doVerify">
          {{ identity.required ? '去核验' : '可选核验' }}
        </text>
      </view>
    </view>

    <view class="card">
      <view class="sec-title">切换身份</view>
      <view class="grid">
        <view
          v-for="r in roleList"
          :key="r.key"
          :class="['chip', role === r.key ? 'on' : '']"
          @click="switchRole(r.key)"
        >
          <text class="cicon">{{ r.icon }}</text>
          <text class="cname">{{ r.label }}</text>
        </view>
      </view>
    </view>

    <view class="card">
      <view class="muted">当前环境：本地联调（模拟登录 / 模拟支付）</view>
      <view class="muted mt8">配置真实 AppID 后，登录会走微信 openid</view>
    </view>

    <view class="logout" @click="onLogout">退出登录</view>

    <AppTabBar :role="role" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import AppTabBar from '../../components/AppTabBar.vue'
import { ROLE_LABEL, currentRole, currentUser, guard, logout, switchTo, type Role } from '../../utils/auth'
import { api, identityApi, merchantApi } from '../../api'
import { hasConfiguredTemplate, requestSubscribe } from '../../utils/subscribe'

const role = ref<Role>(currentRole() || 'USER')
const nickname = ref('')
const merchantStatusText = ref('查看入驻资料与审核进度')
const identity = ref<{ required: boolean; verified: boolean; stuNoMask?: string | null }>({ required: false, verified: false })

const roleLabel = computed(() => ROLE_LABEL[role.value] || '用户')
const accountName = ref('')

/** 商家显示当前店铺名，骑手显示昵称，方便确认「我现在是哪个账号」 */
async function loadAccount() {
  if (role.value === 'MERCHANT') {
    // 用 status 接口：没店时返回 applied=false，不会再命中 /api/merchant/shop 的 404 提示
    try {
      const st = await merchantApi.status()
      accountName.value = st?.applied ? (st.name || '') : ''
      if (!st?.applied) merchantStatusText.value = '尚未入驻，点击提交资料'
      else if (st.auditStatus === 0) merchantStatusText.value = '审核中，请等待平台审核'
      else if (st.auditStatus === 2) merchantStatusText.value = '已驳回：' + (st.rejectReason || '请修改资料后重新提交')
      else merchantStatusText.value = '已通过，可开始接单'
    } catch {
      accountName.value = ''
      merchantStatusText.value = '查看入驻资料与审核进度'
    }
  } else if (role.value === 'RIDER') {
    const u = currentUser()
    accountName.value = u?.nickname ? '骑手 ' + u.nickname : ''
  }
}

/** 换一家店 / 换一个骑手：回到登录页的账号选择 */
function switchAccount() {
  uni.reLaunch({ url: '/pages/login/index?role=' + role.value })
}

const roleList: { key: Role; label: string; icon: string }[] = [
  { key: 'USER', label: '用户', icon: '🍜' },
  { key: 'MERCHANT', label: '商家', icon: '🏪' },
  { key: 'RIDER', label: '骑手', icon: '🛵' }
]

function go(url: string) {
  uni.navigateTo({ url })
}

/** 消息通知：后台没配模板时只提示，不弹授权框、不报错 */
async function onNotify() {
  try {
    const configured = await hasConfiguredTemplate(['order_status', 'coupon'])
    if (!configured) {
      uni.showToast({ title: '后台未配置订阅模板，暂不可用', icon: 'none' })
      return
    }
    const opened = await requestSubscribe(['order_status', 'coupon'])
    if (!opened) {
      uni.showToast({ title: '未开启，可在小程序设置中开启通知', icon: 'none' })
    } else {
      uni.showToast({ title: '已申请消息通知', icon: 'none' })
    }
  } catch (e) {
    console.warn(e)
  }
}

async function switchRole(r: Role) {
  if (r === role.value) return
  const label = ROLE_LABEL[r]
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '切换身份',
      content: `切换到「${label}」端？`,
      success: (x) => resolve(!!x.confirm)
    })
  })
  if (!ok) return
  try {
    await switchTo(r)
  } catch (e) {
    console.warn(e)
  }
}

async function loadIdentity() {
  try {
    identity.value = (await identityApi.status()) as any
  } catch (e) {
    // 忽略
  }
}

function doVerify() {
  uni.showModal({
    title: '校内身份核验',
    editable: true,
    placeholderText: '请输入学号或工号（6~20 位字母数字）',
    success: async (x: any) => {
      if (!x.confirm) return
      const no = (x.content || '').trim()
      if (!no) {
        uni.showToast({ title: '请输入学号/工号', icon: 'none' })
        return
      }
      try {
        const r = await identityApi.verify(no)
        uni.showToast({ title: '核验成功', icon: 'success' })
        identity.value = { required: identity.value.required, verified: true, stuNoMask: (r as any).stuNoMask }
      } catch (e) {
        // request 层已提示原因
      }
    }
  })
}

function onLogout() {
  uni.showModal({
    title: '退出登录',
    content: '确定退出当前身份？',
    success: (x) => { if (x.confirm) logout() }
  })
}

onShow(async () => {
  const r = currentRole()
  if (!r) return
  if (!guard(r)) return
  role.value = r
  const u = currentUser()
  nickname.value = u?.nickname || u?.realName || u?.username || ROLE_LABEL[r]
  await loadAccount()
  await loadIdentity()
  if (r === 'USER') {
    try {
      const p = await api.profile()
      if (p?.nickname) nickname.value = p.nickname
    } catch (e) {
      // 忽略
    }
  }
})
</script>

<style scoped>
.page { padding: 20rpx; }
.head {
  display: flex; align-items: center; gap: 24rpx;
  background: #fff; border-radius: 20rpx; padding: 32rpx; margin-bottom: 20rpx;
}
.avatar {
  width: 104rpx; height: 104rpx; border-radius: 50%;
  background: #fdf2ea; color: #c2410c;
  display: flex; align-items: center; justify-content: center;
  font-size: 42rpx; font-weight: 700;
}
.hinfo { flex: 1; }
.nick { font-size: 34rpx; font-weight: 600; }
.role {
  display: inline-block; margin-top: 8rpx;
  background: #fdf2ea; color: #c2410c;
  font-size: 22rpx; padding: 4rpx 16rpx; border-radius: 20rpx;
}
.menu .item {
  display: flex; justify-content: space-between; align-items: center;
  padding: 26rpx 4rpx; border-bottom: 1rpx solid #f2f3f5;
}
.menu .item:last-child { border-bottom: none; }
.arrow { color: #c3c7cc; }
.sec-title { font-size: 28rpx; font-weight: 600; margin-bottom: 18rpx; }
.ident { gap: 20rpx; }
.ident-state { font-size: 28rpx; font-weight: 600; }
.ident-sub { margin-top: 6rpx; }
.ident-btn { background: #fdf2ea; color: #c2410c; font-size: 24rpx; padding: 10rpx 24rpx; border-radius: 28rpx; }
.grid { display: flex; gap: 16rpx; }
.chip {
  flex: 1;
  display: flex; flex-direction: column; align-items: center; gap: 6rpx;
  padding: 20rpx 0;
  border-radius: 16rpx;
  background: #f7f8fa;
  border: 2rpx solid transparent;
}
.chip.on { background: #fdf2ea; border-color: #c2410c; }
.cicon { font-size: 36rpx; }
.cname { font-size: 22rpx; color: #4b5563; }
.chip.on .cname { color: #c2410c; font-weight: 600; }
.tip { margin-top: 18rpx; }
.mt8 { margin-top: 8rpx; }
.logout {
  margin-top: 30rpx;
  text-align: center;
  background: #fff;
  color: #dc2626;
  padding: 26rpx;
  border-radius: 20rpx;
  font-size: 28rpx;
}
</style>
