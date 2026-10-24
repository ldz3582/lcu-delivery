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

    <!-- ================= 概览 ================= -->
    <view v-if="tab === 'overview'">
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

      <view class="card">
        <view class="between">
          <text class="sec">营收统计</text>
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
        <view class="between"><text class="sec">最近资金流水</text></view>
        <view v-for="f in flows" :key="f.id" class="flow">
          <view class="flow-left">
            <view class="flow-type">{{ bizText(f.bizType) }}</view>
            <view class="muted flow-time">{{ f.createdAt }}</view>
            <view v-if="f.remark" class="muted flow-remark">{{ f.remark }}</view>
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
    </view>

    <!-- ================= 对账 ================= -->
    <view v-else-if="tab === 'statement'">
      <view class="card">
        <view class="date-row">
          <picker mode="date" :value="from" @change="onFromDate">
            <view class="date-btn">{{ from }}</view>
          </picker>
          <text class="muted">至</text>
          <picker mode="date" :value="to" @change="onToDate">
            <view class="date-btn">{{ to }}</view>
          </picker>
          <view class="query-btn" @click="loadStatements(1)">查询</view>
        </view>

        <view class="st-sum mt">
          <view class="ss-item">
            <view class="muted">营业额</view>
            <view class="ss-v">{{ yuan(statement?.summary?.gmv) }}</view>
          </view>
          <view class="ss-item">
            <view class="muted">平台佣金</view>
            <view class="ss-v">{{ yuan(statement?.summary?.commission) }}</view>
          </view>
          <view class="ss-item">
            <view class="muted">商家实收</view>
            <view class="ss-v primary">{{ yuan(statement?.summary?.income) }}</view>
          </view>
          <view class="ss-item">
            <view class="muted">待结算</view>
            <view class="ss-v">{{ yuan(statement?.summary?.pendingIncome) }}</view>
          </view>
        </view>
      </view>

      <view v-for="r in statementRows" :key="r.id" class="card">
        <view class="between">
          <text class="order-no">{{ r.orderNo || ('订单 ' + r.orderId) }}</text>
          <text :class="['tag', r.status === 1 ? 'ok' : 'wait']">{{ r.statusText }}</text>
        </view>
        <view class="st-lines mt">
          <text class="muted">商品 {{ yuan(r.goodsAmount) }}</text>
          <text class="muted">打包 {{ yuan(r.packFee) }}</text>
          <text class="muted">佣金 -{{ yuan(r.commission) }}</text>
          <text class="primary">实收 {{ yuan(r.shopIncome) }}</text>
        </view>
        <view class="muted mt8">{{ r.createdAt }}{{ r.settleAt ? ' · 结算 ' + r.settleAt : '' }}</view>
      </view>

      <view v-if="statement && !statementRows.length" class="muted empty">该时间段暂无对账单</view>

      <view v-if="statement" class="pager">
        <view :class="['pg-btn', curPage <= 1 ? 'off' : '']" @click="prevPage">上一页</view>
        <text class="muted">{{ curPage }} / {{ totalPages }}（共 {{ totalCount }} 条）</text>
        <view :class="['pg-btn', curPage >= totalPages ? 'off' : '']" @click="nextPage">下一页</view>
      </view>
    </view>

    <!-- ================= 提现 ================= -->
    <view v-else>
      <view class="card">
        <view class="between">
          <text class="muted">可提现余额</text>
          <text class="balance-mini">{{ yuan(wallet?.balance) }} 元</text>
        </view>
        <view class="muted mt8">
          最低提现 {{ yuan(wallet?.minWithdraw) }} 元{{ wallet?.frozen ? ` · 冻结中 ${yuan(wallet.frozen)} 元` : '' }}
        </view>

        <view class="amount-row mt">
          <text class="unit">¥</text>
          <input v-model="withdrawYuan" class="amount-input" type="digit" placeholder="请输入提现金额（元）" />
          <text class="all-btn" @click="fillAll">全部</text>
        </view>

        <view :class="['submit', submitting ? 'off' : '']" @click="doWithdraw">
          {{ submitting ? '提交中…' : '申请提现' }}
        </view>
        <view class="muted tip">申请后金额转入冻结，平台审核通过即打款；驳回自动退回余额。</view>
      </view>

      <view class="card">
        <view class="between"><text class="sec">提现记录</text></view>
        <view v-for="w in withdrawals" :key="w.id" class="wd">
          <view class="between">
            <text class="wd-amount">¥{{ yuan(w.amount) }}</text>
            <text :class="['tag', wdClass(w.status)]">{{ w.statusText }}</text>
          </view>
          <view class="muted mt8">{{ w.appliedAt }}{{ w.channel ? ' · ' + w.channel : '' }}</view>
          <view v-if="w.failReason" class="fail mt8">驳回原因：{{ w.failReason }}</view>
        </view>
        <view v-if="!withdrawals.length" class="muted empty">还没有提现记录</view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  ensureMerchantApproved,
  merchantApi,
  type MerchantSummary,
  type MerchantWallet,
  type RevenueRange,
  type StatementPage,
  type Withdrawal,
  type WalletFlow
} from '../../api'
import { guard } from '../../utils/auth'

type Tab = 'overview' | 'statement' | 'withdraw'

const TABS: { value: Tab; label: string }[] = [
  { value: 'overview', label: '概览' },
  { value: 'statement', label: '对账' },
  { value: 'withdraw', label: '提现' }
]

const BIZ_TEXT: Record<string, string> = {
  SHOP_SETTLE: '货款结算',
  WITHDRAW: '提现',
  WITHDRAW_REFUND: '提现退回'
}

const STAT_METRICS: { key: keyof RevenueRange; label: string; money: boolean }[] = [
  { key: 'orderCount', label: '订单数', money: false },
  { key: 'gmv', label: '营业额', money: true },
  { key: 'commission', label: '平台佣金', money: true },
  { key: 'income', label: '商家实收', money: true },
  { key: 'avgPrice', label: '客单价', money: true }
]

const PAGE_SIZE = 10

const tab = ref<Tab>('overview')
const wallet = ref<MerchantWallet | null>(null)
const summary = ref<MerchantSummary | null>(null)
const statement = ref<StatementPage | null>(null)
const withdrawals = ref<Withdrawal[]>([])
const withdrawYuan = ref('')
const submitting = ref(false)

const curPage = ref(1)
const from = ref('')
const to = ref('')

function pad(n: number) {
  return n < 10 ? '0' + n : '' + n
}

function initMonthRange() {
  const now = new Date()
  const y = now.getFullYear()
  const m = now.getMonth() + 1
  from.value = `${y}-${pad(m)}-01`
  to.value = `${y}-${pad(m)}-${pad(now.getDate())}`
}

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

const flows = computed<WalletFlow[]>(() => wallet.value?.flows || [])

/** 表格行先在 JS 里算好，避免小程序模板里传参调用方法 */
const statRows = computed(() =>
  STAT_METRICS.map((m) => {
    const cell = (r?: RevenueRange) => {
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

const statementRows = computed(() => statement.value?.page?.list || [])
const totalCount = computed(() => statement.value?.page?.total || 0)
const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / PAGE_SIZE)))

async function loadWallet() {
  try {
    wallet.value = await merchantApi.wallet()
  } catch (e) {
    wallet.value = null
  }
}

async function loadSummary() {
  try {
    summary.value = await merchantApi.summary()
  } catch (e) {
    summary.value = null
  }
}

async function loadStatements(page = 1) {
  if (from.value > to.value) {
    uni.showToast({ title: '开始日期不能晚于结束日期', icon: 'none' })
    return
  }
  curPage.value = page
  try {
    statement.value = await merchantApi.statements({
      from: from.value,
      to: to.value,
      page,
      size: PAGE_SIZE
    })
  } catch (e) {
    statement.value = null
  }
}

async function loadWithdrawals() {
  try {
    withdrawals.value = (await merchantApi.withdrawals()) || []
  } catch (e) {
    withdrawals.value = []
  }
}

function switchTab(t: Tab) {
  if (tab.value === t) return
  tab.value = t
  if (t === 'statement') loadStatements(curPage.value)
  if (t === 'withdraw') loadWithdrawals()
}

function onFromDate(e: any) {
  from.value = e?.detail?.value || from.value
  loadStatements(1)
}

function onToDate(e: any) {
  to.value = e?.detail?.value || to.value
  loadStatements(1)
}

function prevPage() {
  if (curPage.value > 1) loadStatements(curPage.value - 1)
}

function nextPage() {
  if (curPage.value < totalPages.value) loadStatements(curPage.value + 1)
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
    await merchantApi.applyWithdraw(amount)
    uni.showToast({ title: '已提交，等待平台审核', icon: 'none' })
    withdrawYuan.value = ''
    await Promise.all([loadWallet(), loadWithdrawals()])
  } catch (e) {
    // 余额不足/最低提现等由 request 层 toast 后端文案
    console.warn(e)
  } finally {
    submitting.value = false
  }
}

onShow(async () => {
  if (!guard('MERCHANT')) return
  if (!(await ensureMerchantApproved())) return
  if (!from.value) initMonthRange()
  await Promise.all([loadWallet(), loadSummary()])
  if (tab.value === 'statement') loadStatements(curPage.value)
  if (tab.value === 'withdraw') loadWithdrawals()
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; background: #f5f6f8; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.sec { font-size: 28rpx; font-weight: 600; }
.muted { color: #7a828c; font-size: 24rpx; }
.primary { color: #c2410c; }
.mt { margin-top: 16rpx; }
.mt8 { margin-top: 8rpx; }
.empty { text-align: center; padding: 60rpx 0; }

.tabs { display: flex; gap: 12rpx; margin-bottom: 20rpx; }
.tab {
  flex: 1; text-align: center; padding: 16rpx 0; border-radius: 14rpx;
  background: #fff; color: #4b5563; font-size: 28rpx;
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

.stat-table { border-top: 1rpx solid #f2f3f5; }
.st-row {
  display: flex; align-items: center; padding: 14rpx 0;
  border-bottom: 1rpx solid #f7f8fa; font-size: 24rpx;
}
.st-head { color: #9aa1aa; font-size: 22rpx; }
.st-k { width: 130rpx; color: #7a828c; }
.st-v { flex: 1; text-align: right; color: #1a1d21; }

.flow {
  display: flex; justify-content: space-between; align-items: flex-start;
  padding: 20rpx 0; border-bottom: 1rpx solid #f7f8fa;
}
.flow:last-child { border-bottom: none; }
.flow-left { flex: 1; min-width: 0; }
.flow-type { font-size: 26rpx; color: #1a1d21; font-weight: 600; }
.flow-time { margin-top: 4rpx; }
.flow-remark { margin-top: 4rpx; }
.flow-right { text-align: right; margin-left: 20rpx; }
.flow-amount { font-size: 30rpx; font-weight: 700; }
.flow-amount.in { color: #047857; }
.flow-amount.out { color: #dc2626; }

.date-row { display: flex; align-items: center; gap: 12rpx; }
.date-btn {
  background: #f7f8fa; border-radius: 12rpx; padding: 12rpx 20rpx;
  font-size: 24rpx; color: #374151;
}
.query-btn {
  margin-left: auto; background: #c2410c; color: #fff;
  border-radius: 30rpx; padding: 10rpx 28rpx; font-size: 24rpx;
}
.st-sum { display: flex; flex-wrap: wrap; gap: 16rpx; }
.ss-item {
  width: calc(50% - 8rpx); background: #f9fafb; border-radius: 14rpx; padding: 16rpx;
}
.ss-v { font-size: 28rpx; font-weight: 600; margin-top: 6rpx; }

.order-no { font-size: 26rpx; font-weight: 600; color: #1a1d21; }
.tag { font-size: 20rpx; padding: 4rpx 14rpx; border-radius: 18rpx; }
.tag.ok { background: #d1fae5; color: #047857; }
.tag.wait { background: #fef3c7; color: #b45309; }
.tag.doing { background: #dbeafe; color: #1d4ed8; }
.tag.fail { background: #fee2e2; color: #b91c1c; }
.st-lines { display: flex; flex-wrap: wrap; gap: 8rpx 22rpx; font-size: 24rpx; }

.pager { display: flex; align-items: center; justify-content: space-between; padding: 10rpx 6rpx 40rpx; }
.pg-btn {
  background: #fff; border-radius: 30rpx; padding: 12rpx 28rpx; font-size: 24rpx; color: #374151;
}
.pg-btn.off { color: #c3c7cc; }

.balance-mini { color: #c2410c; font-size: 32rpx; font-weight: 700; }
.amount-row {
  display: flex; align-items: center; gap: 10rpx;
  border-bottom: 1rpx solid #f0f1f3; padding-bottom: 12rpx;
}
.unit { font-size: 40rpx; color: #c2410c; font-weight: 700; }
.amount-input { flex: 1; font-size: 40rpx; color: #1a1d21; }
.all-btn { color: #c2410c; font-size: 24rpx; background: #fdf2ea; border-radius: 26rpx; padding: 8rpx 20rpx; }
.submit {
  margin-top: 26rpx;
  background: #c2410c; color: #fff; text-align: center;
  padding: 22rpx; border-radius: 44rpx; font-size: 30rpx;
}
.submit.off { background: #e5e7eb; color: #9aa1aa; }
.tip { margin-top: 16rpx; line-height: 1.6; }

.wd { padding: 20rpx 0; border-bottom: 1rpx solid #f7f8fa; }
.wd:last-child { border-bottom: none; }
.wd-amount { font-size: 32rpx; font-weight: 700; color: #1a1d21; }
.fail { color: #b91c1c; font-size: 24rpx; }
</style>
