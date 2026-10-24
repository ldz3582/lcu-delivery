<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditRider, getRiders, type Rider } from '@/api'
import { auditMeta, formatDateTime } from '@/utils/format'

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

/** 协议签署时间压成 MM-DD HH:mm，列宽友好 */
function shortTime(value: string | null | undefined): string {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(5, 16)
}

// ---------------- 审核弹窗 ----------------

const auditVisible = ref(false)
const current = ref<Rider | null>(null)
const reason = ref('')
const submitting = ref(false)

function openAudit(row: Rider) {
  current.value = row
  reason.value = ''
  auditVisible.value = true
}

function closeAudit() {
  auditVisible.value = false
  current.value = null
  reason.value = ''
}

async function submitAudit(pass: boolean) {
  const row = current.value
  if (!row) return
  const text = reason.value.trim()
  if (!pass && !text) {
    ElMessage.warning('请填写驳回原因，骑手端会看到这段说明')
    return
  }
  submitting.value = true
  try {
    await auditRider(row.id, pass, pass ? '' : text)
    ElMessage.success(pass ? '审核已通过' : '已驳回，已记录驳回原因')
    closeAudit()
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

function previewList(url: string | undefined | null): string[] {
  return url ? [url] : []
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
        <el-table-column prop="id" label="ID" width="46" />
        <el-table-column prop="phoneMask" label="手机号" width="112" />
        <el-table-column label="证件照" width="130">
          <template #default="{ row }">
            <div class="thumbs">
              <el-image
                v-if="row.idCardFront"
                :src="row.idCardFront"
                :preview-src-list="previewList(row.idCardFront)"
                fit="cover"
                class="thumb"
                title="身份证人像面"
              >
                <template #error><div class="thumb-err">正面</div></template>
              </el-image>
              <span v-else class="muted mini">正面 -</span>
              <el-image
                v-if="row.idCardBack"
                :src="row.idCardBack"
                :preview-src-list="previewList(row.idCardBack)"
                fit="cover"
                class="thumb"
                title="身份证国徽面"
              >
                <template #error><div class="thumb-err">反面</div></template>
              </el-image>
              <span v-else class="muted mini">反面 -</span>
              <el-image
                v-if="row.studentCard"
                :src="row.studentCard"
                :preview-src-list="previewList(row.studentCard)"
                fit="cover"
                class="thumb"
                title="校园卡 / 学生证"
              >
                <template #error><div class="thumb-err">校园卡</div></template>
              </el-image>
              <span v-else class="muted mini">校园卡 -</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="90">
          <template #default="{ row }">
            <el-tag :type="auditMeta(row.auditStatus).type" size="small">
              {{ auditMeta(row.auditStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工作状态" width="86">
          <template #default="{ row }">
            <el-tag :type="workStatusType[row.workStatus] ?? 'info'" size="small">
              {{ workStatusText[row.workStatus] ?? '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="评分" width="56" />
        <el-table-column prop="totalOrders" label="累计单量" width="82" />
        <el-table-column label="超时单数" width="82">
          <template #default="{ row }">
            <span class="num" :class="{ warn: (row.timeoutOrders ?? 0) > 0 }">
              {{ row.timeoutOrders ?? 0 }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="协议签署" width="116">
          <template #default="{ row }">
            <span class="num muted">{{ shortTime(row.agreementSignedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="保单号" width="108" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.insuranceNo">{{ row.insuranceNo }}</span>
            <span v-else class="muted">未填写</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="128">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="驳回原因" min-width="92" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.rejectReason" class="reject-reason">{{ row.rejectReason }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="82">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openAudit(row)">
              {{ row.auditStatus === 0 ? '审核' : '查看' }}
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

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="auditVisible"
      :title="current?.auditStatus === 0 ? '骑手入驻审核' : '骑手资料'"
      width="640px"
      :close-on-click-modal="false"
    >
      <template v-if="current">
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="骑手 ID">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ current.userId }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ current.phoneMask }}</el-descriptions-item>
          <el-descriptions-item label="工作状态">
            <el-tag :type="workStatusType[current.workStatus] ?? 'info'" size="small">
              {{ workStatusText[current.workStatus] ?? '未知' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="评分 / 单量">
            {{ current.score }} 分 · 累计 {{ current.totalOrders ?? 0 }} 单 · 超时
            {{ current.timeoutOrders ?? 0 }} 单
          </el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="auditMeta(current.auditStatus).type" size="small">
              {{ auditMeta(current.auditStatus).text }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="协议签署">
            {{ formatDateTime(current.agreementSignedAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="意外险保单号">
            {{ current.insuranceNo || '未填写' }}
          </el-descriptions-item>
          <el-descriptions-item label="申请时间" :span="2">
            {{ formatDateTime(current.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="img-block">
          <div class="img-group">
            <div class="img-label">身份证人像面</div>
            <el-image
              v-if="current.idCardFront"
              :src="current.idCardFront"
              :preview-src-list="previewList(current.idCardFront)"
              fit="cover"
              class="img-lg"
            >
              <template #error><div class="thumb-err">加载失败</div></template>
            </el-image>
            <div v-else class="img-placeholder">未上传</div>
          </div>
          <div class="img-group">
            <div class="img-label">身份证国徽面</div>
            <el-image
              v-if="current.idCardBack"
              :src="current.idCardBack"
              :preview-src-list="previewList(current.idCardBack)"
              fit="cover"
              class="img-lg"
            >
              <template #error><div class="thumb-err">加载失败</div></template>
            </el-image>
            <div v-else class="img-placeholder">未上传</div>
          </div>
          <div class="img-group">
            <div class="img-label">校园卡 / 学生证</div>
            <el-image
              v-if="current.studentCard"
              :src="current.studentCard"
              :preview-src-list="previewList(current.studentCard)"
              fit="cover"
              class="img-lg"
            >
              <template #error><div class="thumb-err">加载失败</div></template>
            </el-image>
            <div v-else class="img-placeholder">未上传</div>
          </div>
        </div>

        <div v-if="current.rejectReason" class="reject-box">
          上次驳回原因：{{ current.rejectReason }}
        </div>

        <el-form label-width="90px" class="mt">
          <el-form-item label="驳回原因">
            <el-input
              v-model="reason"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              :disabled="submitting"
              placeholder="驳回时必填，例如：身份证照片反光看不清，请重新上传"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeAudit" :disabled="submitting">关闭</el-button>
        <el-button type="danger" :loading="submitting" @click="submitAudit(false)">驳回</el-button>
        <el-button
          type="success"
          :loading="submitting"
          :disabled="current?.auditStatus === 1"
          @click="submitAudit(true)"
        >
          通过
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mini {
  font-size: 12px;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.thumb {
  width: 29px;
  height: 29px;
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

.reject-reason {
  color: #f56c6c;
}

.warn {
  color: #e6a23c;
}

.mb {
  margin-bottom: 14px;
}

.mt {
  margin-top: 6px;
}

.img-block {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.img-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.img-label {
  font-size: 12px;
  color: #606266;
}

.img-lg {
  width: 100px;
  height: 100px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  cursor: pointer;
}

.img-placeholder {
  width: 100px;
  height: 100px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  background: #fafafa;
}

.reject-box {
  color: #f56c6c;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  padding: 8px 12px;
  font-size: 13px;
  margin-bottom: 12px;
}
</style>
