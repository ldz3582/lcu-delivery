<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getOrders, type Order } from '@/api'
import {
  ORDER_STATUS,
  ORDER_STATUS_OPTIONS,
  formatDateTime,
  formatMoney,
} from '@/utils/format'

const loading = ref(false)
const list = ref<Order[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const query = reactive({
  status: '' as number | '',
  keyword: '',
})

async function load() {
  loading.value = true
  try {
    const res = await getOrders({
      status: query.status === '' ? undefined : query.status,
      keyword: query.keyword.trim() || undefined,
      page: page.value,
      size: size.value,
    })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function handleReset() {
  query.status = ''
  query.keyword = ''
  page.value = 1
  load()
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

function statusMeta(row: Order) {
  return ORDER_STATUS[row.status] ?? { text: row.statusText || '未知', type: 'info' as const }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>订单管理</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div class="toolbar">
        <el-select
          v-model="query.status"
          placeholder="全部状态"
          clearable
          style="width: 160px"
          @change="handleSearch"
        >
          <el-option
            v-for="opt in ORDER_STATUS_OPTIONS"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="按订单号搜索"
          clearable
          style="width: 240px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <span class="muted">共 {{ total }} 条</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="orderNo" label="订单号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="shopId" label="商家 ID" width="100" />
        <el-table-column prop="userId" label="用户 ID" width="100" />
        <el-table-column label="骑手 ID" width="100">
          <template #default="{ row }">{{ row.riderId ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="statusText" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusMeta(row).type" size="small" effect="light">
              {{ statusMeta(row).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实付金额" width="120" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="佣金" width="110" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.commission) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="170">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无订单数据" :image-size="90" />
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
