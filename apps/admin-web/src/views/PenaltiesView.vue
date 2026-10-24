<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import { createPenalty, getPenalties, type Penalty, type PenaltyPayload } from '@/api'
import { formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const list = ref<Penalty[]>([])
const riderIdFilter = ref<number | undefined>(undefined)
const appliedRiderId = ref<number | undefined>(undefined)

const TYPE_OPTIONS = [
  { value: 1, label: '超时' },
  { value: 2, label: '差评' },
  { value: 3, label: '违规操作' },
]

const TYPE_MAP: Record<number, { text: string; type: 'warning' | 'danger' | 'info' }> = {
  1: { text: '超时', type: 'warning' },
  2: { text: '差评', type: 'danger' },
  3: { text: '违规操作', type: 'danger' },
}

function typeMeta(type: number | null | undefined) {
  return TYPE_MAP[Number(type)] ?? { text: `类型${type ?? '-'}`, type: 'info' as const }
}

async function load() {
  loading.value = true
  try {
    const res = await getPenalties(
      appliedRiderId.value ? { riderId: appliedRiderId.value } : {},
    )
    list.value = res ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  appliedRiderId.value = riderIdFilter.value || undefined
  load()
}

function handleReset() {
  riderIdFilter.value = undefined
  appliedRiderId.value = undefined
  load()
}

// ---------------- 开罚单 ----------------

const dialogVisible = ref(false)
const submitting = ref(false)
const form = reactive({
  riderId: undefined as number | undefined,
  type: 1 as number,
  amountYuan: 3,
  remark: '',
  orderId: undefined as number | undefined,
})

function openCreate() {
  form.riderId = appliedRiderId.value
  form.type = 1
  form.amountYuan = 3
  form.remark = ''
  form.orderId = undefined
  dialogVisible.value = true
}

async function submit() {
  if (!form.riderId || form.riderId <= 0) {
    ElMessage.warning('请填写骑手 ID')
    return
  }
  const amount = Math.round((form.amountYuan || 0) * 100)
  if (amount <= 0) {
    ElMessage.warning('扣罚金额必须大于 0')
    return
  }
  const remark = form.remark.trim()
  if (!remark) {
    ElMessage.warning('请填写处罚说明')
    return
  }
  const payload: PenaltyPayload = {
    type: form.type,
    amount,
    remark,
  }
  if (form.orderId && form.orderId > 0) {
    payload.orderId = form.orderId
  }
  submitting.value = true
  try {
    await createPenalty(form.riderId, payload)
    ElMessage.success('已开具罚单')
    dialogVisible.value = false
    // 开完单顺便按该骑手过滤，便于看到结果
    riderIdFilter.value = form.riderId
    appliedRiderId.value = form.riderId
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>骑手处罚</h2>
        <div class="title-actions">
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">开罚单</el-button>
        </div>
      </div>

      <div class="toolbar">
        <el-input
          v-model.number="riderIdFilter"
          placeholder="按骑手 ID 过滤"
          clearable
          style="width: 180px"
          :prefix-icon="Search"
          @keyup.enter="handleSearch"
          @clear="handleReset"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
        <span class="muted">共 {{ list.length }} 条</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="骑手" width="100">
          <template #default="{ row }">骑手 #{{ row.riderId }}</template>
        </el-table-column>
        <el-table-column label="关联订单号" width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.orderNo" class="num order-no">{{ row.orderNo }}</span>
            <span v-else-if="row.orderId" class="num muted">#{{ row.orderId }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeMeta(row.type).type" size="small" effect="light">
              {{ row.typeText || typeMeta(row.type).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="扣罚金额" width="112" align="right">
          <template #default="{ row }">
            <span class="num penalty">¥ {{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="142">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无处罚记录" :image-size="90" />
        </template>
      </el-table>
    </div>

    <!-- 开罚单 -->
    <el-dialog v-model="dialogVisible" title="开罚单" width="520px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="骑手 ID" required>
          <el-input-number
            v-model="form.riderId"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="请输入骑手 ID"
            style="width: 180px"
            :disabled="submitting"
          />
          <span class="muted unit">可在骑手审核/处罚记录里查看</span>
        </el-form-item>
        <el-form-item label="处罚类型" required>
          <el-select v-model="form.type" style="width: 180px" :disabled="submitting">
            <el-option
              v-for="opt in TYPE_OPTIONS"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="扣罚金额" required>
          <el-input-number
            v-model="form.amountYuan"
            :min="0"
            :precision="2"
            :step="1"
            style="width: 180px"
            :disabled="submitting"
          />
          <span class="muted unit">元</span>
        </el-form-item>
        <el-form-item label="关联订单 ID">
          <el-input-number
            v-model="form.orderId"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="选填"
            style="width: 180px"
            :disabled="submitting"
          />
        </el-form-item>
        <el-form-item label="处罚说明" required>
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            :disabled="submitting"
            placeholder="必填，例如：超时 15 分钟，按规则扣罚 3 元"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false" :disabled="submitting">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认开单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.title-actions {
  display: flex;
  gap: 10px;
}

.order-no {
  font-size: 12px;
}

.penalty {
  color: #f56c6c;
}

.unit {
  margin-left: 8px;
  font-size: 12px;
}
</style>
