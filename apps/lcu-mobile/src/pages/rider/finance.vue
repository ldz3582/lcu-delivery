<template>
  <view class="page">
    <view class="tabs">
      <view
        v-for="t in TABS"
        :key="t.value"
        :class="['tab', tab === t.value ? 'on' : '']"
        @click="switchTab(t.value)"
      >{{ t.label }}</view>
    </view>

    <!-- ================= 钱包（含提现） ================= -->
    <block v-if="tab === 'wallet'">
      <view class="wallet-card">
        <view class="wallet-label">可提现余额（元）</view>
        <view class="wallet-balance">{{ yuan(wallet?.balance) }}</view>
        <view class="wallet-meta">
          <view class="wm">
            <text class="wm-k">冻结中</text>
            <text class="wm-v">{{ yuan(wallet?.frozen) }}</text>
          </view>
          <view class="wm">
            <text class="wm-k">累计收入</text>
            <text class="wm-v">{{ yuan(wallet?.totalIncome) }}</text>
          </view>
        </view>
        <view class="wallet-sub">
          今日收入 {{ yuan(wallet?.todayIncome) }} · 本月收入 {{ yuan(wallet?.monthIncome) }}
        </view>
      </view>

      <view class="card stats">
        <view class="st">
          <text class="st-v">{{ wallet?.totalOrders ?? 0 }}</text>
          <text class="muted">累计单量</text>
        </view>
        <view class="st">
          <text class="st-v">{{ wallet?.timeoutOrders ?? 0 }}</text>
          <text class="muted">超时单数</text>
        </view>
        <view class="st">
          <text class="st-v">{{ wallet?.score ?? '-' }}</text>
          <text class="muted">评分</text>
        </view>
        <view class="st">
          <text class="st-v penalty">-{{ yuan(wallet?.totalPenalty) }}</text>
          <text class="muted">罚金合计</text>
        </view>
      </view>

      <!-- 提现 -->
      <view class="card">
        <view class="between">
          <text class="sec">提现</text>
          <text class="muted">最低 {{ yuan(wallet?.minWithdraw) }} 元</text>
        </view>
        <view class="amount-row mt">
          <text class="unit">¥</text>
          <input v-model="withdrawYuan" class="amount-input" type="digit" placeholder="提现金额（元）" />
          <text class="all-btn" @click="fillAll">全部</text>
        </view>
        <view :class="['submit', submitting ? 'off' : '']" @click="doWithdraw">
          {{ submitting ? '提交中…' : '申请提现' }}
        </view>
        <view class="muted tip">申请即冻结，平台审核通过即打款；驳回自动退回余额。</view>
      </view>

      <view class="card">
        <view class="between"><text class="sec">提现记录</text></view>
        <view v-for="w in withdrawals" :key="w.id" class="row-item">
          <view class="between">
            <text class="wd-amount">¥{{ yuan(w.amount) }}</text>
            <text :class="['tag', wdClass(w.status)]">{{ w.statusText }}</text>
          </view>
          <view class="muted mt8">{{ w.appliedAt }}{{ w.channel ? ' · ' + w.channel : '' }}</view>
          <view v-if="w.failReason" class="fail mt8">驳回原因：{{ w.failReason }}</view>
        </view>
        <view v-if="!withdrawals.length" class="muted empty">还没有提现记录</view>
      </view>

      <view class="card">
        <view class="between"><text class="sec">资金流水</text></view>
        <view v-for="f in flows" :key="f.id" class="flow">
          <view class="flow-left">
            <view class="flow-type">{{ bizText(f.bizType) }}</view>
            <view class="muted">{{ f.createdAt }}</view>
            <view v-if="f.remark" class="muted">{{ f.remark }}</view>
          </view>
          <view class="flow-right">
            <text :class="['flow-amount', f.direction === 1 ? 'in' : 'out']">
              {{ f.direction === 1 ? '+' : '-' }}{{ yuan(f.amount) }}
            </text>
            <view class="muted">余额 {{ yuan(f.balanceAfter) }}</view>
          </view>
        </view>
        <view v-if="!flows.length" class="muted empty">暂无资金流水</view>
      </view>
    </block>

    <!-- ================= 佣金明细 ================= -->
    <block v-else-if="tab === 'settle'">
      <view class="card">
        <view class="between">
          <text class="sec">收入统计</text>
          <text class="muted">仅统计已完成订单</text>
        </view>
        <view class="stat-table mt">
          <view class="st-row st-head">
            <text class="st-k">指标</text>
            <text class="st-v">今日</text>
            <text class="st-v">本周</text>
            <text class="st-v">本月</text>
          </view>
          <view v-for="row in statRows" :key="row.key" class="st-row">
            <text class="st-k">{{ row.label }}</text>
            <text class="st-v">{{ row.today }}</text>
            <text class="st-v">{{ row.week }}</text>
            <text class="st-v">{{ row.month }}</text>
          </view>
        </view>
      </view>

      <view class="card">
        <view class="st-sum">
          <view class="ss-item">
            <view class="muted">累计单数</view>
            <view class="ss-v">{{ settle?.summary?.orderCount ?? 0 }}</view>
          </view>
          <view class="ss-item">
            <view class="muted">本页收入</view>
            <view class="ss-v primary">{{ yuan(settle?.summary?.pageIncome) }}</view>
          </view>
          <view class="ss-item">
            <view class="muted">待结算</view>
            <view class="ss-v">{{ yuan(settle?.summary?.pendingIncome) }}</view>
          </view>
        </view>
      </view>

      <view v-for="s in settleRows" :key="s.id" class="card">
        <view class="between">
          <text class="order-no">{{ s.orderNo || ('订单 ' + s.orderId) }}</text>
          <text :class="['tag', s.status === 1 ? 'ok' : 'wait']">{{ s.statusText }}</text>
        </view>
        <view class="between mt8">
          <text class="muted">{{ s.createdAt }}{{ s.settleAt ? ' · 入账 ' + s.settleAt : '' }}</text>
          <text class="income">+{{ yuan(s.riderIncome) }}</text>
        </view>
      </view>
      <view v-if="!settleRows.length" class="muted empty">还没有佣金明细</view>

      <view v-if="settle" class="pager">
        <view :class="['pg-btn', curPage <= 1 ? 'off' : '']" @click="prevPage">上一页</view>
        <text class="muted">{{ curPage }} / {{ totalPages }}（共 {{ totalCount }} 条）</text>
        <view :class="['pg-btn', curPage >= totalPages ? 'off' : '']" @click="nextPage">下一页</view>
      </view>
    </block>

    <!-- ================= 处罚 ================= -->
    <block v-else>
      <view class="card">
        <view class="muted">
          超时送达与差评会自动扣罚，扣款会记在钱包明细分里的「处罚扣款」流水上；有异议可联系运营申诉。
        </view>
      </view>

      <view v-for="p in penalties" :key="p.id" class="card">
        <view class="between">
          <text class="penalty-type">{{ p.typeText }}</text>
          <text class="penalty-amount">-{{ yuan(p.amount) }}</text>
        </view>
        <view class="muted mt8">{{ p.orderNo || '—' }}{{ p.createdAt ? ' · ' + p.createdAt : '' }}</view>
        <view v-if="p.remark" class="remark mt8">{{ p.remark }}</view>
      </view>

      <view v-if="!penalties.length" class="muted empty">没有处罚记录，继续保持</view>
    </block>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  ensureRiderApproved,
  riderApi,
  type RiderPenalty,
  type RiderRangeStats,
  type RiderSettlementPage,
  type RiderSummary,
  type RiderWallet,
  type RiderWalletFlow,
  type Withdrawal
} from '../../api'
import { guard } from '../../utils/auth'

type Tab = 'wallet' | 'settle' | 'penalty'

const TABS: { value: Tab; label: string }[] = [
  { value: 'wallet', label: '钱包' },
  { value: 'settle', label: '佣金明细' },
  { value: 'penalty', label: '处罚' }
]

const BIZ_TEXT: Record<string, string> = {
  RIDER_SETTLE: '配送收入',
  WITHDRAW: '提现',
  WITHDRAW_REFUND: '提现退回',
  PENALTY: '处罚扣款'
}

const STAT_METRICS: { key: keyof RiderRangeStats; label: string; money: boolean }[] = [
  { key: 'orderCount', label: '订单数', money: false },
  { key: 'income', label: '收入', money: true },
  { key: 'avgIncome', label: '单均收入', money: true }
]

const PAGE_SIZE = 20

const tab = ref<Tab>('wallet')
const wallet = ref<RiderWallet | null>(null)
const summary = ref<RiderSummary | null>(null)
const settle = ref<RiderSettlementPage | null>(null)
const withdrawals = ref<Withdrawal[]>([])
const penalties = ref<RiderPenalty[]>([])
const withdrawYuan = ref('')
const submitting = ref(false)
const curPage = ref(1)

function yuan(fen?: number | null) {
  return (Number(fen || 0) / 100).toFixed(2)
}

function bizText(t?: string) {
  if (!t) return '资金变动'
  return BIZ_TEXT[t] || t
}

function wdClass(status: number) {
  if (status === 2) return 'ok'
  if (status === 3) return 'fail'
  if (status === 1) return 'doing'
  return 'wait'
}

const flows = computed<RiderWalletFlow[]>(() => wallet.value?.flows || [])
const settleRows = computed(() => settle.value?.list || [])
const totalCount = computed(() => settle.value?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / PAGE_SIZE)))

/** 表格数据在 JS 里算好，避免小程序模板里传参调用方法 */
const statRows = computed(() =>
  STAT_METRICS.map((m) => {
    const cell = (r?: RiderRangeStats) => {
      const v = r ? r[m.key] : 0
      return m.money ? yuan(Number(v)) : String(Number(v || 0))
    }
    return {
      key: String(m.key),
      label: m.label,
      today: cell(summary.value?.today),
      week: cell(summary.value?.week),
      month: cell(summary.value?.month)
    }
  })
)

async function loadWallet() {
  try {
    wallet.value = await riderApi.wallet()
  } catch (e) {
    wallet.value = null
  }
  try {
    withdrawals.value = (await riderApi.withdrawals()) || []
  } catch (e) {
    withdrawals.value = []
  }
}

async function loadSummary() {
  try {
    summary.value = await riderApi.summary()
  } catch (e) {
    summary.value = null
  }
}

async function loadSettlements(page = 1) {
  curPage.value = page
  try {
    settle.value = await riderApi.settlements({ page, size: PAGE_SIZE })
  } catch (e) {
    settle.value = null
  }
}

async function loadPenalties() {
  try {
    penalties.value = (await riderApi.penalties()) || []
  } catch (e) {
    penalties.value = []
  }
}

function loadAll() {
  return Promise.all([loadWallet(), loadSummary()])
}

function switchTab(t: Tab) {
  if (tab.value === t) return
  tab.value = t
  if (t === 'wallet') loadWallet()
  if (t === 'settle') {
    loadSummary()
    loadSettlements(1)
  }
  if (t === 'penalty') loadPenalties()
}

function prevPage() {
  if (curPage.value > 1) loadSettlements(curPage.value - 1)
}

function nextPage() {
  if (curPage.value < totalPages.value) loadSettlements(curPage.value + 1)
}

function fillAll() {
  withdrawYuan.value = ((wallet.value?.balance || 0) / 100).toFixed(2)
}

async function doWithdraw() {
  if (submitting.value) return
  const balance = wallet.value?.balance || 0
  const min = wallet.value?.minWithdraw || 0
  const yuanVal = Number(withdrawYuan.value)
  if (!Number.isFinite(yuanVal) || yuanVal <= 0) {
    uni.showToast({ title: '请输入提现金额', icon: 'none' })
    return
  }
  const amount = Math.round(yuanVal * 100)
  if (amount < min) {
    uni.showToast({ title: `最低提现 ${(min / 100).toFixed(2)} 元`, icon: 'none' })
    return
  }
  if (amount > balance) {
    uni.showToast({ title: '超过可提现余额', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    await riderApi.applyWithdraw(amount)
    uni.showToast({ title: '已提交，等待平台审核', icon: 'none' })
    withdrawYuan.value = ''
    await loadWallet()
  } catch (e) {
    // 余额不足/最低提现等由 request 层 toast 后端文案
    console.warn(e)
  } finally {
    submitting.value = false
  }
}

onShow(async () => {
  if (!guard('RIDER')) return
  if (!(await ensureRiderApproved())) return
  await loadAll()
  if (tab.value === 'settle') loadSettlements(curPage.value)
  if (tab.value === 'penalty') loadPenalties()
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; background: #f5f6f8; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.sec { font-size: 28rpx; font-weight: 600; }
.muted { color: #7a828c; font-size: 24rpx; }
.mt { margin-top: 16rpx; }
.mt8 { margin-top: 8rpx; }
.primary { color: #c2410c; }
.empty { text-align: center; padding: 60rpx 0; }

.tabs { display: flex; gap: 12rpx; margin-bottom: 20rpx; }
.tab {
  flex: 1; text-align: center; padding: 16rpx 0; border-radius: 14rpx;
  background: #fff; color: #4b5563; font-size: 26rpx;
}
.tab.on { background: #fdf2ea; color: #c2410c; font-weight: 600; }

.wallet-card {
  background: linear-gradient(135deg, #c2410c, #ea580c);
  color: #fff; border-radius: 20rpx; padding: 32rpx; margin-bottom: 20rpx;
}
.wallet-label { font-size: 24rpx; opacity: 0.9; }
.wallet-balance { font-size: 64rpx; font-weight: 700; margin-top: 8rpx; }
.wallet-meta { display: flex; gap: 60rpx; margin-top: 20rpx; }
.wm { display: flex; flex-direction: column; }
.wm-k { font-size: 22rpx; opacity: 0.85; }
.wm-v { font-size: 30rpx; font-weight: 600; margin-top: 4rpx; }
.wallet-sub { margin-top: 20rpx; font-size: 22rpx; opacity: 0.9; }

.stats { display: flex; justify-content: space-between; }
.st { display: flex; flex-direction: column; align-items: center; gap: 6rpx; flex: 1; }
.st-v { font-size: 30rpx; font-weight: 700; color: #1a1d21; }
.st-v.penalty { color: #b91c1c; }

.amount-row {
  display: flex; align-items: center; gap: 10rpx;
  border-bottom: 1rpx solid #f0f1f3; padding-bottom: 12rpx;
}
.unit { font-size: 40rpx; color: #c2410c; font-weight: 700; }
.amount-input { flex: 1; font-size: 40rpx; color: #1a1d21; }
.all-btn { color: #c2410c; font-size: 24rpx; background: #fdf2ea; border-radius: 26rpx; padding: 8rpx 20rpx; }
.submit {
  margin-top: 26rpx; background: #c2410c; color: #fff; text-align: center;
  padding: 22rpx; border-radius: 44rpx; font-size: 30rpx;
}
.submit.off { background: #e5e7eb; color: #9aa1aa; }
.tip { margin-top: 16rpx; line-height: 1.6; }

.row-item { padding: 18rpx 0; border-bottom: 1rpx solid #f7f8fa; }
.row-item:last-child { border-bottom: none; }
.wd-amount { font-size: 30rpx; font-weight: 700; }
.fail { color: #b91c1c; font-size: 24rpx; }
.tag { font-size: 20rpx; padding: 4rpx 14rpx; border-radius: 18rpx; }
.tag.ok { background: #d1fae5; color: #047857; }
.tag.wait { background: #fef3c7; color: #b45309; }
.tag.doing { background: #dbeafe; color: #1d4ed8; }
.tag.fail { background: #fee2e2; color: #b91c1c; }

.flow {
  display: flex; justify-content: space-between; align-items: flex-start;
  padding: 20rpx 0; border-bottom: 1rpx solid #f7f8fa;
}
.flow:last-child { border-bottom: none; }
.flow-left { flex: 1; min-width: 0; }
.flow-type { font-size: 26rpx; font-weight: 600; color: #1a1d21; }
.flow-right { text-align: right; margin-left: 20rpx; }
.flow-amount { font-size: 30rpx; font-weight: 700; }
.flow-amount.in { color: #047857; }
.flow-amount.out { color: #dc2626; }

.stat-table { border-top: 1rpx solid #f2f3f5; }
.st-row {
  display: flex; align-items: center; padding: 14rpx 0;
  border-bottom: 1rpx solid #f7f8fa; font-size: 24rpx;
}
.st-head { color: #9aa1aa; font-size: 22rpx; }
.st-k { width: 150rpx; color: #7a828c; }
.st-v { flex: 1; text-align: right; color: #1a1d21; }

.st-sum { display: flex; gap: 14rpx; }
.ss-item { flex: 1; background: #f9fafb; border-radius: 14rpx; padding: 16rpx; }
.ss-v { font-size: 28rpx; font-weight: 600; margin-top: 6rpx; }

.order-no { font-size: 26rpx; font-weight: 600; color: #1a1d21; }
.income { color: #c2410c; font-weight: 700; font-size: 28rpx; }

.pager { display: flex; align-items: center; justify-content: space-between; padding: 10rpx 6rpx 40rpx; }
.pg-btn { background: #fff; border-radius: 30rpx; padding: 12rpx 28rpx; font-size: 24rpx; color: #374151; }
.pg-btn.off { color: #c3c7cc; }

.penalty-type { font-size: 28rpx; font-weight: 600; color: #1a1d21; }
.penalty-amount { color: #b91c1c; font-weight: 700; font-size: 30rpx; }
.remark { font-size: 24rpx; color: #4b5563; background: #f7f8fa; border-radius: 12rpx; padding: 14rpx; }
</style>
