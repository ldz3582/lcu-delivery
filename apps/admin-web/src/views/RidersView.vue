<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditRider, getRiders, type Rider } from '@/api'
import { auditMeta } from '@/utils/format'

const loading = ref(false)
const list = ref<Rider[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const query = reactive({ auditStatus: 0 as number | '' })

const auditOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待审核' },
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' },
]

const workStatusText: Record<number, string> = {
  0: '离线',
  1: '接单中',
  2: '忙碌',
}

const workStatusType: Record<number, 'info' | 'success' | 'warning'> = {
  0: 'info',
  1: 'success',
  2: 'warning',
}

async function load() {
  loading.value = true
  try {
    const res = await getRiders({
      auditStatus: query.auditStatus === '' ? undefined : query.auditStatus,
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
  query.auditStatus = ''
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

async function handlePass(row: Rider) {
  try {
    await ElMessageBox.confirm(`确认通过骑手 #${row.id} 的入驻审核？`, '审核通过', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'info',
    })
  } catch {
    return
  }
  await auditRider(row.id, true)
  ElMessage.success('审核已通过')
  load()
}

async function handleReject(row: Rider) {
  try {
    await ElMessageBox.confirm(`确认驳回骑手 #${row.id} 的入驻申请？`, '驳回审核', {
      confirmButtonText: '驳回',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  await auditRider(row.id, false)
  ElMessage.success('已驳回')
  load()
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>骑手审核</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div class="toolbar">
        <el-select v-model="query.auditStatus" style="width: 160px" @change="handleSearch">
          <el-option
            v-for="opt in auditOptions"
            :key="String(opt.value)"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <span class="muted">共 {{ total }} 条</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="用户 ID" width="110" />
        <el-table-column prop="phoneMask" label="手机号" width="150" />
        <el-table-column label="审核状态" width="120">
          <template #default="{ row }">
            <el-tag :type="auditMeta(row.auditStatus).type" size="small">
              {{ auditMeta(row.auditStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工作状态" width="120">
          <template #default="{ row }">
            <el-tag :type="workStatusType[row.workStatus] ?? 'info'" size="small">
              {{ workStatusText[row.workStatus] ?? '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="评分" width="100" />
        <el-table-column prop="totalOrders" label="累计单量" width="120" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="success"
              :disabled="row.auditStatus === 1"
              @click="handlePass(row)"
            >
              通过
            </el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="row.auditStatus === 2"
              @click="handleReject(row)"
            >
              驳回
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无骑手数据" :image-size="90" />
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
