<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getAfterSales, judgeAfterSale, type AfterSale } from '@/api'
import { formatDateTime, formatMoney, type TagType } from '@/utils/format'

const loading = ref(false)
const list = ref<AfterSale[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const STATUS_TABS = [
  { value: '', label: '全部' },
  { value: 0, label: '待处理' },
  { value: 1, label: '处理中' },
  { value: 2, label: '已退款' },
  { value: 3, label: '已驳回' },
  { value: 4, label: '已关闭' },
]

const STATUS_TYPE: Record<number, TagType> = {
  0: 'warning',
  1: 'primary',
  2: 'success',
  3: 'danger',
  4: 'info',
}

const LIABLE_PARTY_TEXT: Record<number, string> = {
  1: '商家',
  2: '骑手',
  3: '用户',
}

const LIABLE_PARTY_OPTIONS = [
  { value: 1, label: '商家' },
  { value: 2, label: '骑手' },
  { value: 3, label: '用户' },
]

const query = reactive({ status: '' as number | '' })

function statusType(status: number | null | undefined): TagType {
  return STATUS_TYPE[Number(status)] ?? 'info'
}

function liableText(value: number | null | undefined): string {
  if (value === null || value === undefined) return '-'
  return LIABLE_PARTY_TEXT[Number(value)] ?? `#${value}`
}

/** 待处理(0) / 处理中(1) 才能仲裁 */
function canJudge(row: AfterSale): boolean {
  return row.status === 0 || row.status === 1
}

async function load() {
  loading.value = true
  try {
    const res = await getAfterSales({
      status: query.status === '' ? undefined : query.status,
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

function handleStatusChange() {
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

// ---------------- 仲裁弹窗 ----------------

const judgeVisible = ref(false)
const judging = ref(false)
const judgeRow = ref<AfterSale | null>(null)
const judgeForm = reactive({
  amountYuan: 0,
  liableParty: 1,
  remark: '',
})

/** 实付金额（元），缺失时按申请金额兜底 */
const judgePayYuan = computed(() => {
  const row = judgeRow.value
  if (!row) return 0
  return (row.payAmount ?? row.amount ?? 0) / 100
})

const judgeAmountHint = computed(() => {
  const row = judgeRow.value
  if (!row) return ''
  const apply = formatMoney(row.amount)
  const pay = row.payAmount === null || row.payAmount === undefined ? '未知' : formatMoney(row.payAmount)
  return `申请退款 ¥${apply} / 订单实付 ¥${pay}`
})

function openJudge(row: AfterSale) {
  judgeRow.value = row
  const applyYuan = (row.amount || 0) / 100
  const payYuan = (row.payAmount ?? row.amount ?? 0) / 100
  judgeForm.amountYuan = Math.min(applyYuan, payYuan)
  judgeForm.liableParty = row.liableParty ?? 1
  judgeForm.remark = ''
  judgeVisible.value = true
}

async function submitJudge(approve: boolean) {
  const row = judgeRow.value
  if (!row) return

  const remark = judgeForm.remark.trim()
  if (!approve && !remark) {
    ElMessage.warning('驳回时请填写仲裁备注，便于用户理解处理结果')
    return
  }

  const fen = Math.round((judgeForm.amountYuan || 0) * 100)
  if (approve) {
    if (fen <= 0) {
      ElMessage.warning('退款金额必须大于 0')
      return
    }
    if (row.payAmount !== null && row.payAmount !== undefined && fen > row.payAmount) {
      ElMessage.warning('退款金额不能超过订单实付')
      return
    }
    const full = row.payAmount !== null && row.payAmount !== undefined && fen >= row.payAmount
    try {
      await ElMessageBox.confirm(
        `确认向用户退款 ¥${formatMoney(fen)}（${full ? '全额退款，订单将变已退款' : '部分退款，订单回已完成'}）？`,
        '同意退款',
        { confirmButtonText: '确认退款', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }
  } else {
    try {
      await ElMessageBox.confirm('确认驳回该售后申请？订单将回到「已完成」。', '驳回售后', {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        type: 'warning',
      })
    } catch {
      return
    }
  }

  judging.value = true
  try {
    await judgeAfterSale(row.id, {
      approve,
      amount: approve ? fen : 0,
      liableParty: judgeForm.liableParty,
      remark,
    })
    ElMessage.success(approve ? '已同意退款' : '已驳回')
    judgeVisible.value = false
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    judging.value = false
  }
}

// ---------------- 详情抽屉 ----------------

const drawerVisible = ref(false)
const detailRow = ref<AfterSale | null>(null)

function openDetail(row: AfterSale) {
  detailRow.value = row
  drawerVisible.value = true
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>售后仲裁</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="handleStatusChange">
          <el-radio-button v-for="tab in STATUS_TABS" :key="String(tab.value)" :value="tab.value">
            {{ tab.label }}
          </el-radio-button>
        </el-radio-group>
        <span class="muted">共 {{ total }} 条</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="工单ID" width="68" />
        <el-table-column label="订单号" width="190" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="num">{{ row.orderNo || `#${row.orderId}` }}</span>
          </template>
        </el-table-column>
        <el-table-column label="店铺" width="124" show-overflow-tooltip>
          <template #default="{ row }">{{ row.shopName || `#${row.shopId ?? '-'}` }}</template>
        </el-table-column>
        <el-table-column label="用户" width="96" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.nickname || `用户#${row.userId}` }}
          </template>
        </el-table-column>
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag size="small" effect="light" type="info">{{ row.typeText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="申请退款" width="92" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="100" show-overflow-tooltip />
        <el-table-column label="责任方" width="76" align="center">
          <template #default="{ row }">{{ liableText(row.liableParty) }}</template>
        </el-table-column>
        <el-table-column label="申请时间" width="138">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="84">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="136">
          <template #default="{ row }">
            <el-button size="small" type="primary" :disabled="!canJudge(row)" @click="openJudge(row)">
              仲裁
            </el-button>
            <el-button size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无售后工单" :image-size="90" />
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

    <!-- 仲裁弹窗 -->
    <el-dialog
      v-model="judgeVisible"
      title="售后仲裁"
      width="560px"
      :close-on-click-modal="false"
    >
      <template v-if="judgeRow">
        <el-alert :title="judgeAmountHint" type="info" :closable="false" show-icon class="mb" />
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="工单ID">{{ judgeRow.id }}</el-descriptions-item>
          <el-descriptions-item label="订单号">
            {{ judgeRow.orderNo || `#${judgeRow.orderId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="店铺">{{ judgeRow.shopName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户">
            {{ judgeRow.nickname || `用户#${judgeRow.userId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ judgeRow.typeText }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ judgeRow.statusText }}</el-descriptions-item>
          <el-descriptions-item label="申请原因" :span="2">{{ judgeRow.reason }}</el-descriptions-item>
        </el-descriptions>

        <el-form label-width="100px">
          <el-form-item label="退款金额">
            <el-input-number
              v-model="judgeForm.amountYuan"
              :min="0"
              :max="judgePayYuan"
              :precision="2"
              :step="1"
              :disabled="judging"
              style="width: 180px"
            />
            <span class="muted unit">元（驳回时忽略）</span>
          </el-form-item>
          <el-form-item label="责任方">
            <el-select v-model="judgeForm.liableParty" style="width: 180px" :disabled="judging">
              <el-option
                v-for="opt in LIABLE_PARTY_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="仲裁备注">
            <el-input
              v-model="judgeForm.remark"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="例如：核实漏餐，商家赔付；驳回请写明理由"
              :disabled="judging"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="judgeVisible = false" :disabled="judging">取消</el-button>
        <el-button type="danger" :loading="judging" @click="submitJudge(false)">驳回</el-button>
        <el-button type="primary" :loading="judging" @click="submitJudge(true)">同意退款</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="售后工单详情" size="420px">
      <template v-if="detailRow">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="工单ID">{{ detailRow.id }}</el-descriptions-item>
          <el-descriptions-item label="订单号">
            {{ detailRow.orderNo || `#${detailRow.orderId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="店铺">{{ detailRow.shopName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="用户">
            {{ detailRow.nickname || `用户#${detailRow.userId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="类型">{{ detailRow.typeText }}</el-descriptions-item>
          <el-descriptions-item label="申请退款">
            ¥ {{ formatMoney(detailRow.amount) }}
          </el-descriptions-item>
          <el-descriptions-item label="订单实付">
            ¥ {{ formatMoney(detailRow.payAmount) }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(detailRow.status)" size="small" effect="light">
              {{ detailRow.statusText }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="责任方">
            {{ liableText(detailRow.liableParty) }}
          </el-descriptions-item>
          <el-descriptions-item label="申请时间">
            {{ formatDateTime(detailRow.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="申请原因">{{ detailRow.reason }}</el-descriptions-item>
          <el-descriptions-item label="仲裁备注">
            {{ detailRow.judgeRemark || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="drawer-block">
          <div class="drawer-label">凭证图片</div>
          <div v-if="detailRow.images && detailRow.images.length" class="images">
            <el-image
              v-for="(img, idx) in detailRow.images"
              :key="idx"
              :src="img"
              :preview-src-list="detailRow.images"
              :initial-index="idx"
              fit="cover"
              class="thumb"
            >
              <template #error>
                <div class="thumb-placeholder">图片加载失败</div>
              </template>
            </el-image>
          </div>
          <div v-else class="images">
            <div class="thumb-placeholder">用户未上传图片</div>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.mb {
  margin-bottom: 14px;
}

.unit {
  margin-left: 8px;
  font-size: 13px;
}

.drawer-block {
  margin-top: 18px;
}

.drawer-label {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.thumb {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}

.thumb-placeholder {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  text-align: center;
  padding: 4px;
  background: #fafafa;
}
</style>
