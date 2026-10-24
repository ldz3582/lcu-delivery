<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getReviews, updateReviewStatus, type Review } from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<Review[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const STATUS_OPTIONS = [
  { value: '', label: '全部状态' },
  { value: 1, label: '显示中' },
  { value: 0, label: '已隐藏' },
]

/** 后端按「店铺评分 >= score」过滤 */
const SCORE_OPTIONS = [
  { value: '', label: '全部评分' },
  { value: 5, label: '5 星' },
  { value: 4, label: '4 星及以上' },
  { value: 3, label: '3 星及以上' },
  { value: 2, label: '2 星及以上' },
  { value: 1, label: '1 星及以上' },
]

const query = reactive({
  status: '' as number | '',
  score: '' as number | '',
})

const togglingId = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await getReviews({
      status: query.status === '' ? undefined : query.status,
      score: query.score === '' ? undefined : query.score,
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
  query.score = ''
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

async function handleToggle(row: Review) {
  const hide = row.status === 1
  const action = hide ? '隐藏' : '恢复'
  try {
    await ElMessageBox.confirm(
      `确认${action}该评价？${hide ? '隐藏后用户端店铺评价页将立即看不到这条评价。' : '恢复后评价会重新展示并计入店铺评分。'}`,
      `${action}评价`,
      { confirmButtonText: action, cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  togglingId.value = row.id
  try {
    await updateReviewStatus(row.id, hide ? 0 : 1)
    ElMessage.success(`已${action}`)
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    togglingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>评价管理</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div class="toolbar">
        <el-select v-model="query.status" placeholder="全部状态" style="width: 140px" @change="handleSearch">
          <el-option
            v-for="opt in STATUS_OPTIONS"
            :key="String(opt.value)"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <el-select v-model="query.score" placeholder="全部评分" style="width: 150px" @change="handleSearch">
          <el-option
            v-for="opt in SCORE_OPTIONS"
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
        <el-table-column prop="id" label="ID" width="55" />
        <el-table-column label="订单" width="176" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="num order-no">{{ row.orderNo || `#${row.orderId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="店铺" width="124" show-overflow-tooltip>
          <template #default="{ row }">{{ row.shopName || `#${row.shopId}` }}</template>
        </el-table-column>
        <el-table-column label="用户" width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.anonymous ? '匿名用户' : row.nickname || '同学' }}</span>
            <el-tag v-if="row.anonymous" size="small" type="info" effect="plain" class="anonym">匿名</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="店铺评分" width="134">
          <template #default="{ row }">
            <el-rate :model-value="row.shopScore" disabled size="small" />
          </template>
        </el-table-column>
        <el-table-column label="骑手评分" width="134">
          <template #default="{ row }">
            <el-rate v-if="row.riderScore" :model-value="row.riderScore" disabled size="small" />
            <span v-else class="muted">未评分</span>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="100" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: !row.content }">{{ row.content || '（未填写文字）' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="商家回复" min-width="90" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ muted: !row.reply }">{{ row.reply || '未回复' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="132">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="78">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 1 ? '显示中' : '已隐藏' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button
              size="small"
              :type="row.status === 1 ? 'danger' : 'success'"
              :loading="togglingId === row.id"
              @click="handleToggle(row)"
            >
              {{ row.status === 1 ? '隐藏' : '恢复' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无评价数据" :image-size="90" />
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
.anonym {
  margin-left: 6px;
}

.order-no {
  font-size: 12px;
}
</style>
