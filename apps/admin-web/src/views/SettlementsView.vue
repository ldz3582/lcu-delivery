<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getSettlements, type Settlement } from '@/api'
import { SETTLE_STATUS, formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const list = ref<Settlement[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

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

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>结算管理</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="结算单号" width="100" />
        <el-table-column prop="orderId" label="订单 ID" width="100" />
        <el-table-column prop="shopId" label="商家 ID" width="100" />
        <el-table-column label="骑手 ID" width="100">
          <template #default="{ row }">{{ row.riderId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="商品金额" width="120" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.goodsAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="平台佣金" width="120" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.commission) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商家收入" width="120" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.shopIncome) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="骑手收入" width="120" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.riderIncome) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row.status).type" size="small">
              {{ statusMeta(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结算时间" width="170">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.settleAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="170">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
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
