<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getIncidents, handleIncident, type Incident } from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<Incident[]>([])

const STATUS_TABS = [
  { value: '', label: '全部' },
  { value: 0, label: '待处理' },
  { value: 1, label: '已处理' },
]

const query = reactive({ status: '' as number | '' })

async function load() {
  loading.value = true
  try {
    const res = await getIncidents({
      status: query.status === '' ? undefined : query.status,
    })
    list.value = res ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  load()
}

// ---------------- 处置弹窗 ----------------

const handleVisible = ref(false)
const current = ref<Incident | null>(null)
const handleRemark = ref('')
const submitting = ref(false)

function openHandle(row: Incident) {
  current.value = row
  handleRemark.value = ''
  handleVisible.value = true
}

function closeHandle() {
  handleVisible.value = false
  current.value = null
  handleRemark.value = ''
}

async function submitHandle() {
  const row = current.value
  if (!row) return
  const remark = handleRemark.value.trim()
  if (!remark) {
    ElMessage.warning('请填写处置说明，便于后续追溯')
    return
  }
  submitting.value = true
  try {
    await handleIncident(row.id, remark)
    ElMessage.success('已处置')
    closeHandle()
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

function previewList(urls: string[] | undefined | null): string[] {
  return urls ?? []
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>配送异常</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="handleStatusChange">
          <el-radio-button v-for="tab in STATUS_TABS" :key="String(tab.value)" :value="tab.value">
            {{ tab.label }}
          </el-radio-button>
        </el-radio-group>
        <span class="muted">共 {{ list.length }} 条</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="56" />
        <el-table-column label="订单号" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="num order-no">{{ row.orderNo || `#${row.orderId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="店铺" width="124" show-overflow-tooltip>
          <template #default="{ row }">{{ row.shopName || '-' }}</template>
        </el-table-column>
        <el-table-column label="异常类型" width="132">
          <template #default="{ row }">
            <el-tag size="small" effect="light" type="warning">{{ row.typeText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="凭证图片" width="112">
          <template #default="{ row }">
            <div v-if="row.images && row.images.length" class="thumbs">
              <el-image
                v-for="(img, idx) in row.images.slice(0, 3)"
                :key="idx"
                :src="img"
                :preview-src-list="previewList(row.images)"
                :initial-index="idx"
                fit="cover"
                class="thumb"
              >
                <template #error><div class="thumb-err">图</div></template>
              </el-image>
              <span v-if="row.images.length > 3" class="muted mini">+{{ row.images.length - 3 }}</span>
            </div>
            <span v-else class="muted mini">无</span>
          </template>
        </el-table-column>
        <el-table-column label="上报时间" width="140">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="84">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small" effect="light">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="96">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              :disabled="row.status === 1"
              @click="openHandle(row)"
            >
              处置
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无配送异常上报" :image-size="90" />
        </template>
      </el-table>
    </div>

    <!-- 处置弹窗 -->
    <el-dialog v-model="handleVisible" title="处置配送异常" width="560px" :close-on-click-modal="false">
      <template v-if="current">
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="异常单号">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="异常类型">{{ current.typeText }}</el-descriptions-item>
          <el-descriptions-item label="订单号" :span="2">
            {{ current.orderNo || `#${current.orderId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="店铺">{{ current.shopName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="上报时间">
            {{ formatDateTime(current.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="上报说明" :span="2">
            {{ current.remark || '-' }}
          </el-descriptions-item>
          <el-descriptions-item v-if="current.handleRemark" label="已处理说明" :span="2">
            {{ current.handleRemark }}
          </el-descriptions-item>
        </el-descriptions>

        <el-form label-width="90px">
          <el-form-item label="处置说明">
            <el-input
              v-model="handleRemark"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              :disabled="submitting"
              placeholder="必填，例如：已电话联系用户改为驿站代收，订单正常送达"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeHandle" :disabled="submitting">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">确认处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mini {
  font-size: 12px;
}

.order-no {
  font-size: 12px;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.thumb {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  cursor: pointer;
}

.thumb-err {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #909399;
  background: #f5f7fa;
}

.mb {
  margin-bottom: 14px;
}
</style>
