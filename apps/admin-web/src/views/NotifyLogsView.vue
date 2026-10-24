<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { getNotifyLogs, type NotifyLog } from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<NotifyLog[]>([])
const page = ref(1)
const size = ref(20)
const statusFilter = ref<number | null>(null)

/** 后端这个接口没返回 total，用「本页是否满页」判断有没有下一页 */
const hasNext = computed(() => list.value.length >= size.value)
const hasPrev = computed(() => page.value > 1)

const STATUS_TEXT: Record<number, string> = { 0: '待发送', 1: '发送成功', 2: '发送失败' }
const STATUS_TYPE: Record<number, 'info' | 'success' | 'danger'> =
  { 0: 'info', 1: 'success', 2: 'danger' }

const filtered = computed(() =>
  statusFilter.value === null ? list.value : list.value.filter((r) => r.status === statusFilter.value),
)

async function load() {
  loading.value = true
  try {
    const res = await getNotifyLogs({ page: page.value, size: size.value })
    list.value = res.list ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function prev() {
  if (!hasPrev.value) return
  page.value -= 1
  load()
}

function next() {
  if (!hasNext.value) return
  page.value += 1
  load()
}

function refresh() {
  load()
}

function templateText(id: string | null): string {
  return id ? id : '未配置'
}

onMounted(load)
</script>

<template>
  <div class="page">
    <el-card shadow="never">
      <div class="toolbar">
        <div class="left">
          <span class="title">消息日志</span>
          <span class="tip">
            模拟环境未配 AppID / 模板 ID 时，这里会记一条「发送失败 + 原因」，配好后即为真实下发结果
          </span>
        </div>
        <div class="right">
          <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
            <el-option :value="0" label="待发送" />
            <el-option :value="1" label="发送成功" />
            <el-option :value="2" label="发送失败" />
          </el-select>
          <el-button :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        </div>
      </div>

      <el-table :data="filtered" v-loading="loading" border size="small" empty-text="还没有消息记录">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="目标" width="110">
          <template #default="{ row }">
            {{ row.targetType === 1 ? '用户' : '类型' + row.targetType }} #{{ row.targetId }}
          </template>
        </el-table-column>
        <el-table-column label="模板 ID" min-width="150">
          <template #default="{ row }">
            <span :class="{ muted: !row.templateId }">{{ templateText(row.templateId) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务号" min-width="190" show-overflow-tooltip />
        <el-table-column prop="channel" label="渠道" width="110" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="STATUS_TYPE[row.status] ?? 'info'" size="small">
              {{ row.statusText || STATUS_TEXT[row.status] || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="失败原因" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ danger: !!row.error }">{{ row.error || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-button size="small" :disabled="!hasPrev" @click="prev">上一页</el-button>
        <span class="page-no">第 {{ page }} 页</span>
        <el-button size="small" :disabled="!hasNext" @click="next">下一页</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page { padding: 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; gap: 12px; }
.left { display: flex; flex-direction: column; gap: 4px; }
.title { font-size: 16px; font-weight: 600; color: #1f2328; }
.tip { font-size: 12px; color: #909399; }
.right { display: flex; gap: 10px; align-items: center; }
.muted { color: #b1b3b8; }
.danger { color: #f56c6c; }
.pager { display: flex; align-items: center; justify-content: flex-end; gap: 10px; margin-top: 14px; }
.page-no { font-size: 13px; color: #606266; }
</style>
