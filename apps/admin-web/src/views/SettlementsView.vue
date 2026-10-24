<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getSettlements, settleSettlement, type Settlement } from '@/api'
import { SETTLE_STATUS, formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const list = ref<Settlement[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const settlingId = ref<number | null>(null)

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
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    settlingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>结算管理</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

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
  </div>
</template>

<style scoped>
.time-line {
  font-size: 12px;
  line-height: 1.6;
}
</style>
