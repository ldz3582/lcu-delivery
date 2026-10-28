<template>
  <view class="page">
    <view class="card head">
      <view class="avatar">骑</view>
      <view>
        <view class="name">骑手 {{ profile?.phoneMask || '' }}</view>
        <view class="muted">审核：{{ auditText }} · 评分 {{ profile?.score || '-' }}</view>
      </view>
    </view>

    <view class="stats">
      <view class="stat"><view class="num">{{ profile?.totalOrders || 0 }}</view><view class="muted">累计接单</view></view>
      <view class="stat"><view class="num">{{ profile?.maxConcurrent || 3 }}</view><view class="muted">同时上限</view></view>
      <view class="stat"><view class="num">{{ profile?.workStatus === 1 ? '在线' : '离线' }}</view><view class="muted">接单状态</view></view>
    </view>

    <!-- 接单设置：PUT /api/rider/settings -->
    <view class="card">
      <view class="settings-title">接单设置</view>
      <view class="field">
        <text>开启接单</text>
        <switch :checked="settings.workStatus === 1" color="#c2410c" @change="onWorkStatus" />
      </view>
      <view class="field">
        <text>同时接单上限（1~10）</text>
        <input v-model="settings.maxConcurrent" type="number" placeholder="3" />
      </view>
      <view class="field-col">
        <text>可接单时段</text>
        <input v-model="settings.workTime" placeholder="如 11:00-13:00,17:00-21:00" />
      </view>
      <view :class="['btn', saving ? 'off' : '']" @click="saveSettings()">
        {{ saving ? '保存中…' : '保存接单设置' }}
      </view>
    </view>

    <view class="card entry" @click="goFinance">
      <view class="between">
        <view>
          <view class="entry-title">我的收益 / 资金明细</view>
          <view class="muted">钱包余额、佣金明细、提现与处罚记录</view>
        </view>
        <text class="arrow">›</text>
      </view>
    </view>

    <view class="card">
      <view class="muted">收益结算：订单完成即生成结算单，T+1 冻结期满自动入钱包（见后端 SettleJob）。</view>
    </view>

    <view class="btn gray" @click="logout">退出登录</view>
    <AppTabBar :role="'RIDER'" />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { riderApi, ensureRiderApproved, type RiderProfile } from '../../api'
import { guard, logout } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const profile = ref<RiderProfile | null>(null)
const auditText = computed(() => ['待审核', '已通过', '已驳回'][profile.value?.auditStatus ?? 0])
const saving = ref(false)
/** 接单设置表单：GET /api/rider/profile 回填，PUT /api/rider/settings 保存 */
const settings = reactive({ workStatus: 0, maxConcurrent: 3 as number | string, workTime: '' })

function applyProfile(p: RiderProfile | null) {
  profile.value = p
  if (!p) return
  settings.workStatus = p.workStatus === 1 ? 1 : 0
  settings.maxConcurrent = p.maxConcurrent ?? 3
  settings.workTime = p.workTime || ''
}

/** 不传 partial 表示保存整张表单；开关单独保存时只传 workStatus */
async function saveSettings(partial?: { workStatus?: number; maxConcurrent?: number; workTime?: string }) {
  if (saving.value) return
  let payload = partial
  if (!payload) {
    const max = Number(settings.maxConcurrent)
    if (!Number.isFinite(max) || max < 1 || max > 10) {
      uni.showToast({ title: '同时接单上限为 1~10', icon: 'none' })
      return
    }
    payload = {
      workStatus: settings.workStatus,
      maxConcurrent: Math.round(max),
      workTime: settings.workTime.trim()
    }
  }
  saving.value = true
  try {
    const p = await riderApi.updateSettings(payload)
    applyProfile(p || profile.value)
    uni.showToast({ title: '已保存', icon: 'none' })
  } catch (e) {
    // 开关保存失败时回滚为服务端当前值
    settings.workStatus = profile.value?.workStatus === 1 ? 1 : 0
    console.warn(e)
  } finally {
    saving.value = false
  }
}

/** 开启接单开关直接落库，符合「switch → PUT /api/rider/settings {workStatus}」 */
function onWorkStatus(e: any) {
  const val = e?.detail?.value ? 1 : 0
  settings.workStatus = val
  saveSettings({ workStatus: val })
}

function goFinance() {
  uni.navigateTo({ url: '/pages/rider/finance' })
}

onShow(async () => {
  if (!guard('RIDER')) return
  if (!(await ensureRiderApproved())) return
  try { applyProfile(await riderApi.profile()) } catch (e) { console.warn(e) }
})
</script>

<style scoped>
.page { padding: 20rpx; }
.head { display: flex; align-items: center; gap: 24rpx; }
.avatar {
  width: 96rpx; height: 96rpx; border-radius: 50%; background: #eff6ff; color: #1d4ed8;
  display: flex; align-items: center; justify-content: center; font-size: 38rpx; font-weight: 700;
}
.name { font-size: 32rpx; font-weight: 600; }
.stats { display: flex; gap: 16rpx; margin-bottom: 20rpx; }
.stat { flex: 1; background: #fff; border-radius: 20rpx; padding: 24rpx; text-align: center; }
.num { font-size: 34rpx; font-weight: 700; color: #c2410c; }
.settings-title { font-size: 28rpx; font-weight: 600; margin-bottom: 10rpx; }
.field {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 26rpx;
}
.field input { flex: 1; margin-left: 20rpx; text-align: right; }
.field-col { padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 26rpx; }
.field-col input {
  margin-top: 10rpx; padding: 12rpx 16rpx;
  background: #f7f8fa; border-radius: 10rpx; font-size: 26rpx;
}
.btn {
  margin-top: 24rpx; padding: 20rpx; border-radius: 40rpx;
  background: #c2410c; color: #fff; text-align: center; font-size: 28rpx;
}
.btn.off { background: #e5e7eb; color: #9aa1aa; }
.btn.gray { margin-top: 30rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.entry-title { font-size: 28rpx; font-weight: 600; color: #c2410c; }
.arrow { color: #c3c7cc; }
</style>
