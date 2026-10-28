<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  Bicycle,
  Money,
  Refresh,
  Shop,
  ShoppingCart,
  User,
  Van,
} from '@element-plus/icons-vue'
import {
  getOverview,
  getStatsRanking,
  getStatsRates,
  getStatsTrend,
  type Overview,
  type StatsRates,
  type StatsRanking,
  type TrendPoint,
} from '@/api'
import { formatMoney } from '@/utils/format'

const loading = ref(false)
const data = ref<Overview | null>(null)
const trend = ref<TrendPoint[]>([])
const ranking = ref<StatsRanking>({ shops: [], riders: [] })
const rates = ref<StatsRates | null>(null)

const shopTop5 = computed(() => (ranking.value.shops ?? []).slice(0, 5))
const riderTop5 = computed(() => (ranking.value.riders ?? []).slice(0, 5))

/** 订单 / GMV 各自取最大值做柱高比例（GMV 单位分，不影响比例） */
const maxOrders = computed(() => Math.max(1, ...trend.value.map((d) => Number(d.orders) || 0)))
const maxGmv = computed(() => Math.max(1, ...trend.value.map((d) => Number(d.gmv) || 0)))

function orderHeight(v: number): string {
  return `${Math.round((Number(v || 0) / maxOrders.value) * 100)}%`
}

function gmvHeight(v: number): string {
  return `${Math.round((Number(v || 0) / maxGmv.value) * 100)}%`
}

function shortDate(date: string): string {
  return String(date ?? '').slice(5)
}

function rateText(v: number | null | undefined): string {
  if (v === null || v === undefined || Number.isNaN(Number(v))) return '0.0%'
  return `${Number(v).toFixed(1)}%`
}

/**
 * 新口径「近 7 日新增占比」= newUsers7d / totalUsers。
 * 后端滚动升级期间若尚未返回 newUserShare7d，按同一口径本地兜底（不退回已废弃的 userGrowthRate7d）。
 */
const newUserShare7d = computed(() => {
  const r = rates.value
  if (!r) return 0
  const raw = r.newUserShare7d as number | null | undefined
  if (raw !== null && raw !== undefined && Number.isFinite(Number(raw))) return Number(raw)
  const total = Number(r.totalUsers || 0)
  if (total <= 0) return 0
  return Math.round((Number(r.newUsers7d || 0) * 1000) / total) / 10
})

async function load() {
  loading.value = true
  const [ov, tr, rk, rt] = await Promise.allSettled([
    getOverview(),
    getStatsTrend(7),
    getStatsRanking(5),
    getStatsRates(),
  ])
  if (ov.status === 'fulfilled') data.value = ov.value
  if (tr.status === 'fulfilled') trend.value = tr.value ?? []
  if (rk.status === 'fulfilled') {
    ranking.value = {
      shops: rk.value?.shops ?? [],
      riders: rk.value?.riders ?? [],
    }
  }
  if (rt.status === 'fulfilled') rates.value = rt.value
  loading.value = false
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-title">
      <h2>平台概览</h2>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon blue"><el-icon><ShoppingCart /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">今日订单</div>
            <div class="stat-value">{{ data?.todayOrders ?? 0 }}</div>
            <div class="stat-sub muted">单</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon green"><el-icon><Money /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">今日 GMV</div>
            <div class="stat-value">¥ {{ formatMoney(data?.todayGmv) }}</div>
            <div class="stat-sub muted">已支付订单金额</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon orange"><el-icon><Shop /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">商家数</div>
            <div class="stat-value">{{ data?.totalShops ?? 0 }}</div>
            <div class="stat-sub">
              待审核
              <el-tag size="small" :type="(data?.pendingShops ?? 0) > 0 ? 'warning' : 'info'">
                {{ data?.pendingShops ?? 0 }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon purple"><el-icon><Bicycle /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">骑手数</div>
            <div class="stat-value">{{ data?.totalRiders ?? 0 }}</div>
            <div class="stat-sub">
              待审核
              <el-tag size="small" :type="(data?.pendingRiders ?? 0) > 0 ? 'warning' : 'info'">
                {{ data?.pendingRiders ?? 0 }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon cyan"><el-icon><User /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">用户数</div>
            <div class="stat-value">{{ data?.totalUsers ?? 0 }}</div>
            <div class="stat-sub muted">注册用户</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon teal"><el-icon><Van /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">配送中</div>
            <div class="stat-value">{{ data?.inDelivery ?? 0 }}</div>
            <div class="stat-sub muted">待取餐 + 配送中</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 近 7 天订单 / GMV 趋势（纯 CSS 柱状图） -->
    <div class="panel trend-panel">
      <div class="panel-head">
        <h3>近 7 天订单 / GMV 趋势</h3>
        <div class="legend">
          <span class="legend-item"><i class="dot orders"></i>订单数</span>
          <span class="legend-item"><i class="dot gmv"></i>GMV</span>
        </div>
      </div>

      <div v-if="trend.length" class="trend-chart">
        <div v-for="day in trend" :key="day.date" class="trend-day">
          <div class="bars">
            <el-tooltip
              :content="`${day.date} 订单 ${day.orders} 单（已支付 ${day.paidOrders}）`"
              placement="top"
            >
              <div class="bar orders" :style="{ height: orderHeight(day.orders) }"></div>
            </el-tooltip>
            <el-tooltip
              :content="`${day.date} GMV ¥${formatMoney(day.gmv)}`"
              placement="top"
            >
              <div class="bar gmv" :style="{ height: gmvHeight(day.gmv) }"></div>
            </el-tooltip>
          </div>
          <div class="trend-date">{{ shortDate(day.date) }}</div>
        </div>
      </div>
      <el-empty v-else description="暂无趋势数据" :image-size="70" />
    </div>

    <!-- 排行榜 -->
    <el-row :gutter="16" class="rank-row">
      <el-col :xs="24" :lg="12" class="col">
        <div class="panel">
          <div class="panel-head">
            <h3>商家排行 Top5</h3>
            <span class="muted small">按已完成订单 GMV</span>
          </div>
          <el-table :data="shopTop5" size="small" border stripe>
            <el-table-column label="名次" width="60" align="center">
              <template #default="{ $index }">
                <span class="rank-no" :class="`rank-${$index + 1}`">{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="商家" min-width="130" show-overflow-tooltip />
            <el-table-column prop="orders" label="单量" width="72" align="right" />
            <el-table-column label="GMV" width="110" align="right">
              <template #default="{ row }">
                <span class="num">¥ {{ formatMoney(row.gmv) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="佣金" width="100" align="right">
              <template #default="{ row }">
                <span class="num muted">¥ {{ formatMoney(row.commission) }}</span>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无商家成交数据" :image-size="60" />
            </template>
          </el-table>
        </div>
      </el-col>

      <el-col :xs="24" :lg="12" class="col">
        <div class="panel">
          <div class="panel-head">
            <h3>骑手排行 Top5</h3>
            <span class="muted small">按完成单量</span>
          </div>
          <el-table :data="riderTop5" size="small" border stripe>
            <el-table-column label="名次" width="60" align="center">
              <template #default="{ $index }">
                <span class="rank-no" :class="`rank-${$index + 1}`">{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="骑手" width="92">
              <template #default="{ row }">#{{ row.riderId }}</template>
            </el-table-column>
            <el-table-column prop="orders" label="单量" width="72" align="right" />
            <el-table-column label="收入" width="100" align="right">
              <template #default="{ row }">
                <span class="num">¥ {{ formatMoney(row.income) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="评分" width="70" align="right">
              <template #default="{ row }">{{ Number(row.score ?? 0).toFixed(1) }}</template>
            </el-table-column>
            <el-table-column prop="timeoutOrders" label="超时" width="62" align="right" />
            <el-table-column prop="badReviews" label="差评" width="62" align="right" />
            <template #empty>
              <el-empty description="暂无骑手配送数据" :image-size="60" />
            </template>
          </el-table>
        </div>
      </el-col>
    </el-row>

    <!-- 率值区 -->
    <div class="panel">
      <div class="panel-head">
        <h3>关键运营率值</h3>
        <span class="muted small">数据口径见下方说明</span>
      </div>
      <el-row :gutter="14">
        <el-col :xs="12" :sm="6" class="rate-col">
          <div class="rate-card">
            <div class="rate-label">超时率</div>
            <div class="rate-value warning">{{ rateText(rates?.timeoutRate) }}</div>
            <div class="rate-sub muted">
              超时 {{ rates?.timeoutOrders ?? 0 }} / 完成 {{ rates?.finishedOrders ?? 0 }} 单
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6" class="rate-col">
          <div class="rate-card">
            <div class="rate-label">差评率</div>
            <div class="rate-value danger">{{ rateText(rates?.badReviewRate) }}</div>
            <div class="rate-sub muted">
              差评 {{ rates?.badReviews ?? 0 }} / 评价 {{ rates?.reviews ?? 0 }} 条
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6" class="rate-col">
          <div class="rate-card">
            <div class="rate-label">复购率</div>
            <div class="rate-value success">{{ rateText(rates?.repurchaseRate) }}</div>
            <div class="rate-sub muted">
              复购 {{ rates?.repeatUsers ?? 0 }} / 用户 {{ rates?.totalUsers ?? 0 }}
            </div>
          </div>
        </el-col>
        <el-col :xs="12" :sm="6" class="rate-col">
          <div class="rate-card">
            <div class="rate-label">近 7 日新增占比</div>
            <div class="rate-value primary">{{ rateText(newUserShare7d) }}</div>
            <div class="rate-sub muted">
              近 7 日新增 {{ rates?.newUsers7d ?? 0 }} 人 / 总用户 {{ rates?.totalUsers ?? 0 }}（今日
              {{ rates?.newUsersToday ?? 0 }}）
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<style scoped>
.col {
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  height: 100%;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex: none;
}

.blue {
  background: #ecf5ff;
  color: #409eff;
}
.green {
  background: #f0f9eb;
  color: #67c23a;
}
.orange {
  background: #fdf6ec;
  color: #e6a23c;
}
.purple {
  background: #f4f4ff;
  color: #7a7ae6;
}
.cyan {
  background: #eef9fa;
  color: #17b3c4;
}
.teal {
  background: #edf7f3;
  color: #2fa97c;
}

.stat-body {
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-sub {
  margin-top: 6px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* ---------- 看板通用面板 ---------- */
.panel {
  background: #fff;
  border-radius: 8px;
  padding: 16px 20px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  margin-bottom: 16px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.panel-head h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.small {
  font-size: 12px;
}

/* ---------- 趋势图 ---------- */
.legend {
  display: flex;
  gap: 15px;
  font-size: 12px;
  color: #606266;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 2px;
  display: inline-block;
}

.dot.orders {
  background: #409eff;
}

.dot.gmv {
  background: #67c23a;
}

.trend-chart {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  height: 180px;
  padding: 8px 4px 0;
  border-bottom: 1px solid #ebeef5;
}

.trend-day {
  flex: 1;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  min-width: 0;
}

.bars {
  flex: 1;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 5px;
  min-height: 0;
}

.bar {
  width: 16px;
  max-width: 40%;
  min-height: 2px;
  border-radius: 3px 3px 0 0;
  transition: height 0.35s ease;
  cursor: default;
}

.bar.orders {
  background: linear-gradient(180deg, #79bbff, #409eff);
}

.bar.gmv {
  background: linear-gradient(180deg, #95d475, #67c23a);
}

.trend-date {
  height: 26px;
  line-height: 26px;
  text-align: center;
  font-size: 12px;
  color: #909399;
  font-variant-numeric: tabular-nums;
}

/* ---------- 排行榜 ---------- */
.rank-row {
  margin-bottom: 0;
}

.rank-no {
  display: inline-flex;
  width: 20px;
  height: 20px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 12px;
  color: #606266;
  background: #f2f3f5;
}

.rank-1 {
  background: #fdf6ec;
  color: #e6a23c;
}
.rank-2 {
  background: #f4f4f5;
  color: #909399;
}
.rank-3 {
  background: #fef0f0;
  color: #f56c6c;
}

/* ---------- 率值 ---------- */
.rate-col {
  margin-bottom: 4px;
}

.rate-card {
  border: 1px solid #f0f2f5;
  border-radius: 8px;
  padding: 14px 16px;
  background: #fafbfc;
}

.rate-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}

.rate-value {
  font-size: 26px;
  font-weight: 600;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.rate-value.warning {
  color: #e6a23c;
}
.rate-value.danger {
  color: #f56c6c;
}
.rate-value.success {
  color: #67c23a;
}
.rate-value.primary {
  color: #409eff;
}

.rate-sub {
  margin-top: 8px;
  font-size: 12px;
}
</style>
