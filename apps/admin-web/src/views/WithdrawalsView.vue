<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditWithdrawal, getWithdrawals, type Withdrawal } from '@/api'
import { formatDateTime, formatMoney, type TagType } from '@/utils/format'

const loading = ref(false)
const list = ref<Withdrawal[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const STATUS_TABS = [
  { value: '', label: '全部' },
  { value: 0, label: '待审核' },
  { value: 1, label: '打款中' },
  { value: 2, label: '已到账' },
  { value: 3, label: '已驳回' },
]

const STATUS_TYPE: Record<number, TagType> = {
  0: 'warning',
  1: 'primary',
  2: 'success',
  3: 'danger',
}

const query = reactive({ status: '' as number | '' })

/** 提现对象类型：后端分页，这里只对当前页做前端筛选 */
const OWNER_TYPES = [
  { value: '', label: '全部对象' },
  { value: 2, label: '商家' },
  { value: 3, label: '骑手' },
]

const ownerType = ref<number | ''>('')

const displayList = computed(() =>
  list.value.filter((row) => ownerType.value === '' || row.ownerType === ownerType.value),
)

function ownerLabel(row: Withdrawal): string {
  if (row.ownerType === 3) {
    return row.riderName || `骑手#${row.ownerId}`
  }
  return row.shopName || `商家#${row.ownerId}`
}

function ownerTypeText(row: Withdrawal): string {
  return row.ownerType === 3 ? '骑手' : '商家'
}

function statusType(status: number | null | undefined): TagType {
  return STATUS_TYPE[Number(status)] ?? 'info'
}

/** 待审核(0) / 打款中(1) 才能审核 */
function canAudit(row: Withdrawal): boolean {
  return row.status === 0 || row.status === 1
}

async function load() {
  loading.value = true
  try {
    const res = await getWithdrawals({
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

// 通过打款
const passingId = ref<number | null>(null)

async function handlePass(row: Withdrawal) {
  try {
    await ElMessageBox.confirm(
      `确认已向商家「${row.shopName || `#${row.ownerId}`}」打款 ¥${formatMoney(row.amount)}？通过后冻结金额释放、提现单变为已到账。`,
      '通过打款',
      { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  passingId.value = row.id
  try {
    await auditWithdrawal(row.id, true)
    ElMessage.success('已通过打款')
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    passingId.value = null
  }
}

// 驳回弹窗（原因必填）
const rejectVisible = ref(false)
const rejectRow = ref<Withdrawal | null>(null)
const rejectReason = ref('')
const rejecting = ref(false)

function openReject(row: Withdrawal) {
  rejectRow.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

function closeReject() {
  rejectVisible.value = false
  rejectRow.value = null
  rejectReason.value = ''
}

async function submitReject() {
  const row = rejectRow.value
  if (!row) return
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写驳回原因，商家端会看到这段说明')
    return
  }
  rejecting.value = true
  try {
    await auditWithdrawal(row.id, false, reason)
    ElMessage.success('已驳回，金额已退回商家可用余额')
    closeReject()
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    rejecting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>提现审核</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <el-alert
        class="tip"
        type="info"
        :closable="false"
        show-icon
        title="通过 = 打款完成（释放冻结金额）；驳回 = 金额退回商家可用余额。"
      />

      <div class="toolbar">
        <el-radio-group v-model="query.status" @change="handleStatusChange">
          <el-radio-button v-for="tab in STATUS_TABS" :key="String(tab.value)" :value="tab.value">
            {{ tab.label }}
          </el-radio-button>
        </el-radio-group>
        <el-select v-model="ownerType" placeholder="全部对象" style="width: 130px">
          <el-option
            v-for="opt in OWNER_TYPES"
            :key="String(opt.value)"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
        <span class="muted">
          共 {{ total }} 条
          <template v-if="ownerType !== ''">（当前页显示 {{ displayList.length }} 条）</template>
        </span>
      </div>

      <el-table v-loading="loading" :data="displayList" border stripe>
        <el-table-column prop="id" label="单号" width="80" />
        <el-table-column label="提现对象" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ ownerLabel(row) }}</span>
            <el-tag size="small" type="info" effect="plain" class="owner-tag">
              {{ ownerTypeText(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提现金额" width="115" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="渠道" width="100">
          <template #default="{ row }">{{ row.channel || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="驳回原因" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.failReason" class="fail-reason">{{ row.failReason }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="150">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.appliedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核时间" width="150">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.auditedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="172">
          <template #default="{ row }">
            <template v-if="canAudit(row)">
              <el-button
                size="small"
                type="success"
                :loading="passingId === row.id"
                @click="handlePass(row)"
              >
                通过打款
              </el-button>
              <el-button size="small" type="danger" @click="openReject(row)">驳回</el-button>
            </template>
            <span v-else class="muted">已处理</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无提现申请" :image-size="90" />
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

    <!-- 驳回弹窗 -->
    <el-dialog v-model="rejectVisible" title="驳回提现申请" width="520px" :close-on-click-modal="false">
      <template v-if="rejectRow">
        <el-alert
          type="warning"
          :closable="false"
          show-icon
          class="tip"
          :title="`商家「${rejectRow.shopName || `#${rejectRow.ownerId}`}」申请提现 ¥${formatMoney(rejectRow.amount)}，驳回后金额立即退回商家可用余额。`"
        />
        <el-form label-width="90px">
          <el-form-item label="驳回原因">
            <el-input
              v-model="rejectReason"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              :disabled="rejecting"
              placeholder="必填，例如：收款账户信息有误，请核对后重新申请"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeReject" :disabled="rejecting">取消</el-button>
        <el-button type="danger" :loading="rejecting" @click="submitReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tip {
  margin-bottom: 14px;
}

.fail-reason {
  color: #f56c6c;
}

.owner-tag {
  margin-left: 6px;
}
</style>
