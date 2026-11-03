<template>
  <view class="page">
    <view class="brand">
      <view class="logo">聊</view>
      <view class="title">聊大外卖</view>
      <view class="muted">校内点餐 · 学生骑手配送</view>
    </view>

    <!-- 身份：用户 / 商家 / 骑手（管理员在 web 后台，不在这里） -->
    <view class="tabs">
      <view
        v-for="r in roles"
        :key="r.key"
        :class="['tab', role === r.key ? 'on' : '']"
        @click="pickRole(r.key)"
      >{{ r.label }}</view>
    </view>

    <!-- 用户：学号登录 或 微信登录 -->
    <view v-if="role === 'USER'" class="card">
      <view class="field">
        <text class="label">学号 / 工号</text>
        <input v-model="stuNo" placeholder="如 20211234567" maxlength="20" />
      </view>
      <view class="hint">
        {{ requireIdentity ? '平台已开启校内身份核验，登录即完成核验' : '输入学号即可登录，首次登录自动注册' }}
      </view>

      <view class="btn" @click="doStuLogin">{{ loading ? '处理中…' : '学号登录 / 注册' }}</view>

      <view class="divider"><text>或</text></view>

      <view class="btn ghost" @click="doWxLogin">微信一键登录</view>
      <view class="hint center">登录后可浏览店铺、下单与查看订单</view>
    </view>

    <!-- 商家 / 骑手：选择账号后进入 -->
    <view v-else class="card">
      <view class="muted pick-title">
        {{ role === 'MERCHANT' ? '选择要进入的店铺：' : '选择骑手账号：' }}
      </view>

      <view v-if="accounts.length" class="acct-list">
        <view
          v-for="a in accounts"
          :key="a.openid"
          :class="['acct', picked === a.openid ? 'on' : '']"
          @click="picked = a.openid"
        >
          <view class="acct-main">
            <text class="acct-name">{{ role === 'MERCHANT' ? a.shopName : (a.riderName || a.nickname) }}</text>
            <text class="acct-sub">
              {{ a.openid }}
              <template v-if="role === 'MERCHANT' && a.auditStatus !== 1">· 待审核</template>
              <template v-else-if="role === 'MERCHANT' && a.bizStatus !== 1">· 休息中</template>
            </text>
          </view>
          <text v-if="picked === a.openid" class="acct-check">✓</text>
        </view>
      </view>
      <view v-else class="muted">
        暂无可用账号，请先在「我的」里提交{{ role === 'MERCHANT' ? '商家入驻' : '骑手入驻' }}申请
      </view>

      <view class="btn" @click="doStaffLogin">{{ loading ? '处理中…' : '进入' + ROLE_LABEL[role] + '端' }}</view>
      <view v-if="accounts.length" class="hint center">账号列表来自平台已开通的店铺 / 骑手</view>
    </view>

    <view class="foot">
      <text class="muted">校园配送服务由平台统一运营</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import {
  HOME,
  ROLE_LABEL,
  fetchMockAccounts,
  loginByRole,
  loginByStuNo,
  loginByWechat,
  currentRole,
  savedOpenid,
  logout,
  type MockAccount,
  type Role
} from '../../utils/auth'
import { identityApi } from '../../api'

const roles: { key: Role; label: string }[] = [
  { key: 'USER', label: '用户' },
  { key: 'MERCHANT', label: '商家' },
  { key: 'RIDER', label: '骑手' }
]

const role = ref<Role>('USER')
const stuNo = ref('')
const loading = ref(false)
const requireIdentity = ref(false)
const accounts = ref<MockAccount[]>([])
const picked = ref('')

/** 切换身份时按角色拉演示账号：商家选店、骑手选人 */
async function pickRole(r: Role) {
  role.value = r
  picked.value = ''
  accounts.value = []
  if (r === 'USER') return
  const list = await fetchMockAccounts(r)
  accounts.value = list
  const saved = savedOpenid(r)
  const hit = list.find((a) => a.openid === saved)
  picked.value = (hit || list[0])?.openid || ''
}

onLoad(async (q: any) => {
  if (q?.role && ['USER', 'MERCHANT', 'RIDER'].includes(q.role)) role.value = q.role
  const cur = currentRole()
  if (cur) role.value = cur
  await pickRole(role.value)
  try {
    const cfg = await identityApi.config()
    requireIdentity.value = !!cfg.required
  } catch (e) {
    requireIdentity.value = false
  }
})

async function doStuLogin() {
  if (loading.value) return
  const no = stuNo.value.trim()
  if (!no) {
    uni.showToast({ title: '请填写学号/工号', icon: 'none' })
    return
  }
  if (!/^[A-Za-z0-9]{6,20}$/.test(no)) {
    uni.showToast({ title: '学号/工号需为 6~20 位字母或数字', icon: 'none' })
    return
  }
  loading.value = true
  try {
    await loginByStuNo(no)
    uni.reLaunch({ url: HOME.USER })
  } catch (e) {
    // request 层已提示
  } finally {
    loading.value = false
  }
}

async function doWxLogin() {
  if (loading.value) return
  loading.value = true
  try {
    const resp = await loginByWechat()
    const r = ((resp.role || 'USER') === 'ADMIN' ? 'USER' : (resp.role || 'USER')) as Role
    // 平台开启核验时，微信登录后仍需绑定学号
    if (requireIdentity && r === 'USER') {
      const no = stuNo.value.trim()
      if (!no) {
        logout()
        uni.showToast({ title: '请先填写学号/工号', icon: 'none' })
        return
      }
      try {
        await identityApi.verify(no)
      } catch (e: any) {
        logout()
        uni.showToast({ title: e?.message || '身份核验失败', icon: 'none' })
        return
      }
    }
    uni.reLaunch({ url: HOME[r] || HOME.USER })
  } catch (e: any) {
    uni.showModal({
      title: '微信登录不可用',
      content: (e?.message || '未配置 AppSecret 时会这样') + '\n\n请先用「学号登录 / 注册」进入。',
      showCancel: false
    })
  } finally {
    loading.value = false
  }
}

async function doStaffLogin() {
  if (loading.value) return
  loading.value = true
  try {
    await loginByRole(role.value, picked.value || undefined)
    uni.reLaunch({ url: HOME[role.value] })
  } catch (e) {
    // request 层已提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 90rpx 40rpx 60rpx; }
.pick-title { margin-bottom: 16rpx; }
.acct-list { max-height: 620rpx; overflow-y: auto; margin-bottom: 20rpx; }
.acct {
  display: flex; align-items: center; justify-content: space-between;
  padding: 20rpx 22rpx; margin-bottom: 12rpx; border-radius: 16rpx;
  background: #f7f8fa; border: 2rpx solid transparent;
}
.acct.on { border-color: #c2410c; background: #fff5ef; }
.acct-main { display: flex; flex-direction: column; }
.acct-name { font-size: 28rpx; color: #1f2328; font-weight: 600; }
.acct-sub { font-size: 22rpx; color: #9ca3af; margin-top: 4rpx; }
.acct-check { color: #c2410c; font-size: 32rpx; font-weight: 700; }
.brand { text-align: center; margin-bottom: 40rpx; }
.logo {
  width: 132rpx; height: 132rpx; border-radius: 32rpx; margin: 0 auto 20rpx;
  background: linear-gradient(135deg, #c2410c, #ea580c); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 62rpx; font-weight: 700;
}
.title { font-size: 40rpx; font-weight: 700; }
.muted { color: #7a828c; font-size: 24rpx; margin-top: 8rpx; }

.tabs { display: flex; background: #f3f4f6; border-radius: 30rpx; padding: 6rpx; margin-bottom: 24rpx; }
.tab { flex: 1; text-align: center; padding: 16rpx; border-radius: 26rpx; font-size: 28rpx; color: #4b5563; }
.tab.on { background: #fff; color: #c2410c; font-weight: 600; }

.card { background: #fff; border-radius: 24rpx; padding: 28rpx; }
.field { display: flex; align-items: center; justify-content: space-between; padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.label { font-size: 28rpx; color: #4b5563; }
.field input { flex: 1; text-align: right; font-size: 30rpx; }
.hint { margin-top: 14rpx; font-size: 22rpx; color: #9ca3af; }
.hint.center { text-align: center; }

.btn {
  margin-top: 28rpx; background: #c2410c; color: #fff;
  text-align: center; padding: 26rpx; border-radius: 48rpx;
  font-size: 30rpx; font-weight: 600;
}
.btn.ghost { background: #fff; color: #c2410c; border: 2rpx solid #c2410c; }

.divider { text-align: center; margin: 24rpx 0 4rpx; color: #c3c7cc; font-size: 22rpx; }
.foot { margin-top: 40rpx; text-align: center; }
</style>
