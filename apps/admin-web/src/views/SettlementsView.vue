<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getFinanceFlows,
  getFinanceOverview,
  getSettlements,
  settleSettlement,
  type FinanceOverview,
  type Settlement,
  type WalletFlow,
} from '@/api'
import { SETTLE_STATUS, formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const list = ref<Settlement[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const settlingId = ref<number | null>(null)

// ---------------- 资金总览 ----------------

const financeLoading = ref(false)
const finance = ref<FinanceOverview | null>(null)

async function loadFinance() {
  financeLoading.value = true
  try {
    finance.value = await getFinanceOverview()
  } catch {
    finance.value = null
  } finally {
    financeLoading.value = false
  }
}

// ---------------- 资金流水 ----------------

const flowLoading = ref(false)
const flows = ref<WalletFlow[]>([])
const flowTotal = ref(0)
const flowPage = ref(1)
const flowSize = ref(10)
/** 空串 = 全部 */
const flowBizType = ref('')

const BIZ_TYPE_TEXT: Record<string, string> = {
  SHOP_SETTLE: '商家结算',
  RIDER_SETTLE: '骑手结算',
  SETTLE_REVERSE: '结算冲正',
  WITHDRAW: '提现冻结',
  WITHDRAW_REFUND: '提现退回',
  PENALTY: '骑手处罚扣款',
  CANCEL_PENALTY: '用户取消违约金',
  SHOP_PENALTY: '商家罚款',
}

const BIZ_TYPE_OPTIONS = Object.entries(BIZ_TYPE_TEXT).map(([value, label]) => ({
  value,
  label,
}))

const OWNER_TYPE_TEXT: Record<number, string> = {
  1: '用户',
  2: '商家',
  3: '骑手',
}

function bizTypeText(v: string): string {
  // 未知类型统一显示中文占位，不透出后端英文枚举
  return BIZ_TYPE_TEXT[v] ?? '其他业务'
}

function bizTypeTag(v: string): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (v === 'WITHDRAW' || v === 'SETTLE_REVERSE' || v === 'PENALTY' || v === 'SHOP_PENALTY') {
    return 'danger'
  }
  if (v === 'WITHDRAW_REFUND') return 'warning'
  if (v === 'SHOP_SETTLE' || v === 'RIDER_SETTLE' || v === 'CANCEL_PENALTY') return 'success'
  return 'info'
}

async function loadFlows() {
  flowLoading.value = true
  try {
    const res = await getFinanceFlows({
      bizType: flowBizType.value || undefined,
      page: flowPage.value,
      size: flowSize.value,
    })
    flows.value = res.list ?? []
    flowTotal.value = res.total ?? 0
  } catch {
    flows.value = []
    flowTotal.value = 0
  } finally {
    flowLoading.value = false
  }
}

function handleFlowSearch() {
  flowPage.value = 1
  loadFlows()
}

function handleFlowPageChange(p: number) {
  flowPage.value = p
  loadFlows()
}

function handleFlowSizeChange(s: number) {
  flowSize.value = s
  flowPage.value = 1
  loadFlows()
}

// ---------------- 结算单 ----------------

async function load() {
  loading.value = true
  try {
    const res = await getSettlements({ page: page.value, size: size.value })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handlePageChange(p: number) {
  page.value = p
  load()
}

function handleSizeChange(s: number) {
  size.value = s
  page.value = 1
  load()
}

function statusMeta(status: number) {
  return SETTLE_STATUS[status] ?? { text: '未知', type: 'info' as const }
}

/** 手动结算：正常由 T+N 定时任务自动结算，这里用于演示/应急 */
async function handleSettle(row: Settlement) {
  try {
    await ElMessageBox.confirm(
      `确认立即结算该笔结算单？商家实收 ¥${formatMoney(row.shopIncome)}、骑手收入 ¥${formatMoney(row.riderIncome)} 将立即入钱包。`,
      '手动结算',
      { confirmButtonText: '确认结算', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  settlingId.value = row.id
  try {
    await settleSettlement(row.id)
    ElMessage.success('已手动结算')
    await Promise.all([load(), loadFinance(), loadFlows()])
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    settlingId.value = null
  }
}

function refreshAll() {
  load()
  loadFinance()
  loadFlows()
}

onMounted(refreshAll)
</script>

<template>
  <div class="page">
    <!-- 资金总览 -->
    <div class="page-card overview-card" v-loading="financeLoading">
      <div class="page-title">
        <h2>结算管理</h2>
        <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
      </div>

      <div class="money-grid">
        <div class="money-item">
          <div class="money-label">GMV（已支付）</div>
          <div class="money-value">¥ {{ formatMoney(finance?.gmv) }}</div>
        </div>
        <div class="money-item">
          <div class="money-label">平台佣金</div>
          <div class="money-value green">¥ {{ formatMoney(finance?.platformCommission) }}</div>
        </div>
        <div class="money-item">
          <div class="money-label">配送服务费</div>
          <div class="money-value green">¥ {{ formatMoney(finance?.deliveryServiceFee) }}</div>
          <div class="money-sub muted">
            配送费收入 ¥{{ formatMoney(finance?.deliveryFeeIncome) }} / 骑手 ¥{{
              formatMoney(finance?.riderPay)
            }}
          </div>
        </div>
        <div class="money-item">
          <div class="money-label">待结算（商家）</div>
          <div class="money-value warning">¥ {{ formatMoney(finance?.pendingSettle) }}</div>
        </div>
        <div class="money-item">
          <div class="money-label">已结算</div>
          <div class="money-value">¥ {{ formatMoney(finance?.settledShop) }}</div>
          <div class="money-sub muted">
            商家 / 骑手 ¥{{ formatMoney(finance?.settledRider) }}
            <span v-if="(finance?.reversedSettle ?? 0) > 0">
              · 冲正 ¥{{ formatMoney(finance?.reversedSettle) }}
            </span>
          </div>
        </div>
        <div
          class="money-item"
          title="已退款：全部退款单金额；计入净收入口径：仅统计仍未取消/未退款的订单产生的退款"
        >
          <div class="money-label">已退款</div>
          <div class="money-value danger">¥ {{ formatMoney(finance?.refunded) }}</div>
          <div class="money-sub muted">
            计入净收入口径 ¥{{ formatMoney(finance?.refundedOnCounted) }}
          </div>
        </div>
        <div class="money-item">
          <div class="money-label">提现</div>
          <div class="money-value">¥ {{ formatMoney(finance?.withdrawPaid) }}</div>
          <div class="money-sub muted">
            待打款 ¥{{ formatMoney(finance?.withdrawPending) }}
          </div>
        </div>
        <div class="money-item">
          <div class="money-label">平台净收入（佣金+配送服务费-有效退款）</div>
          <div class="money-value primary">¥ {{ formatMoney(finance?.platformNetIncome) }}</div>
          <div class="money-sub muted">
            佣金 + 配送服务费 - 有效退款 ¥{{ formatMoney(finance?.refundedOnCounted) }}
          </div>
        </div>
      </div>
    </div>

    <!-- 结算单列表 -->
    <div class="page-card">
      <div class="toolbar">
        <span class="muted">正常由 T+N 定时任务自动结算；「手动结算」仅用于演示或应急。</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="结算单号" width="92" />
        <el-table-column prop="orderId" label="订单 ID" width="90" />
        <el-table-column prop="shopId" label="商家 ID" width="90" />
        <el-table-column label="骑手 ID" width="80">
          <template #default="{ row }">{{ row.riderId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="商品金额" width="104" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.goodsAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="平台佣金" width="96" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.commission) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商家收入" width="104" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.shopIncome) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="骑手收入" width="100" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.riderIncome) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">
              {{ statusMeta(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">
            <div class="num muted time-line">创建 {{ formatDateTime(row.createdAt) }}</div>
            <div class="num muted time-line">结算 {{ formatDateTime(row.settleAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="116">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              size="small"
              type="primary"
              :loading="settlingId === row.id"
              @click="handleSettle(row)"
            >
              手动结算
            </el-button>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无结算数据" :image-size="90" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          :current-page="page"
          :page-size="size"
          :page-sizes="[10, 20, 50]"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 资金流水 -->
    <div class="page-card flows-card">
      <div class="page-title">
        <h2>资金流水</h2>
        <el-button :icon="Refresh" @click="loadFlows">刷新流水</el-button>
      </div>

      <div class="toolbar">
        <el-select
          v-model="flowBizType"
          placeholder="全部业务类型"
          clearable
          style="width: 180px"
          @change="handleFlowSearch"
        >
          <el-option
            v-for="opt in BIZ_TYPE_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <span class="muted">共 {{ flowTotal }} 条</span>
      </div>

      <el-table v-loading="flowLoading" :data="flows" border stripe size="small">
        <el-table-column prop="id" label="流水号" width="84" />
        <el-table-column label="业务类型" width="110">
          <template #default="{ row }">
            <el-tag :type="bizTypeTag(row.bizType)" size="small" effect="light">
              {{ bizTypeText(row.bizType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归属" width="130">
          <template #default="{ row }">
            <span class="muted">
              {{ OWNER_TYPE_TEXT[row.ownerType] ?? '其他' }}
              {{ row.ownerId != null ? `#${row.ownerId}` : '-' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="方向" width="76">
          <template #default="{ row }">
            <el-tag :type="row.direction === 1 ? 'success' : 'danger'" size="small" effect="plain">
              {{ row.direction === 1 ? '收入' : '支出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="116" align="right">
          <template #default="{ row }">
            <span class="num" :class="row.direction === 1 ? 'income' : 'expense'">
              {{ row.direction === 1 ? '+' : '-' }}¥ {{ formatMoney(row.amount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="余额" width="112" align="right">
          <template #default="{ row }">
            <span class="num muted">¥ {{ formatMoney(row.balanceAfter) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" width="130" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="170" show-overflow-tooltip />
        <el-table-column label="时间" width="160">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无资金流水" :image-size="90" />
        </template>
      </el-table>

      <div class="pager">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :total="flowTotal"
          :current-page="flowPage"
          :page-size="flowSize"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="handleFlowPageChange"
          @size-change="handleFlowSizeChange"
        />
      </div>
    </div>
  </div>
</template>

<style scoped>
.time-line {
  font-size: 12px;
  line-height: 1.6;
}

.overview-card {
  margin-bottom: 16px;
}

.flows-card {
  margin-top: 16px;
}

.money-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

@media (max-width: 1200px) {
  .money-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

.money-item {
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 8px;
  padding: 14px 16px;
  min-width: 0;
}

.money-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.money-value {
  font-size: 21px;
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}

.money-value.green {
  color: #67c23a;
}
.money-value.warning {
  color: #e6a23c;
}
.money-value.danger {
  color: #f56c6c;
}
.money-value.primary {
  color: #409eff;
}

.money-sub {
  margin-top: 6px;
  font-size: 12px;
}

.income {
  color: #67c23a;
}

.expense {
  color: #f56c6c;
}
</style>
