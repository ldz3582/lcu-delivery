<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditShop, getShops, type Shop } from '@/api'
import { auditMeta, campusText, formatDateTime, formatRate } from '@/utils/format'

const loading = ref(false)
const list = ref<Shop[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const auditOptions = [
  { value: '', label: '全部状态' },
  { value: 0, label: '待审核' },
  { value: 1, label: '已通过' },
  { value: 2, label: '已驳回' },
]

const query = reactive({ auditStatus: 0 as number | '' })

async function load() {
  loading.value = true
  try {
    const res = await getShops({
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

// ---------------- 审核弹窗 ----------------

const auditVisible = ref(false)
const current = ref<Shop | null>(null)
const reason = ref('')
const submitting = ref(false)

function openAudit(row: Shop) {
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
    ElMessage.warning('请填写驳回原因，商家端会看到这段说明')
    return
  }
  submitting.value = true
  try {
    await auditShop(row.id, pass, pass ? '' : text)
    ElMessage.success(pass ? '审核已通过' : '已驳回，已记录驳回原因')
    closeAudit()
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

/** el-image 预览用：把单张图包成数组 */
function previewList(url: string | undefined | null): string[] {
  return url ? [url] : []
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>商家审核</h2>
        <el-button :icon="Refresh" @click="load">刷新</el-button>
      </div>

      <div class="toolbar">
        <el-select
          v-model="query.auditStatus"
          style="width: 160px"
          @change="handleSearch"
        >
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

      <el-table v-loading="loading" :data="list" border stripe size="default">
        <el-table-column prop="id" label="ID" width="56" />
        <el-table-column label="商家名称" width="126" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="shop-name">{{ row.name }}</div>
            <el-tag :type="row.bizStatus === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.bizStatus === 1 ? '营业中' : '休息中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="校区" width="72">
          <template #default="{ row }">{{ campusText(row.campus) }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="110" show-overflow-tooltip />
        <el-table-column prop="contactPhone" label="联系电话" width="112" />
        <el-table-column label="证照" width="122">
          <template #default="{ row }">
            <div class="thumbs">
              <el-image
                v-if="row.licenseUrl"
                :src="row.licenseUrl"
                :preview-src-list="previewList(row.licenseUrl)"
                fit="cover"
                class="thumb"
                title="营业执照"
              >
                <template #error>
                  <div class="thumb-err">执照</div>
                </template>
              </el-image>
              <span v-else class="muted mini">执照 -</span>
              <el-image
                v-if="row.permitUrl"
                :src="row.permitUrl"
                :preview-src-list="previewList(row.permitUrl)"
                fit="cover"
                class="thumb"
                title="食品经营许可证"
              >
                <template #error>
                  <div class="thumb-err">许可</div>
                </template>
              </el-image>
              <span v-else class="muted mini">许可 -</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="门店照片" width="122">
          <template #default="{ row }">
            <div v-if="row.photos && row.photos.length" class="thumbs">
              <el-image
                v-for="(photo, idx) in row.photos.slice(0, 3)"
                :key="idx"
                :src="photo"
                :preview-src-list="row.photos"
                :initial-index="idx"
                fit="cover"
                class="thumb"
              >
                <template #error>
                  <div class="thumb-err">门店</div>
                </template>
              </el-image>
              <span v-if="row.photos.length > 3" class="muted mini">+{{ row.photos.length - 3 }}</span>
            </div>
            <span v-else class="muted mini">未上传</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="140">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="88">
          <template #default="{ row }">
            <el-tag :type="auditMeta(row.auditStatus).type" size="small">
              {{ auditMeta(row.auditStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="驳回原因" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.rejectReason" class="reject-reason">{{ row.rejectReason }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="86">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openAudit(row)">
              {{ row.auditStatus === 0 ? '审核' : '查看' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无商家数据" :image-size="90" />
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
      :title="current?.auditStatus === 0 ? '商家入驻审核' : '商家资料'"
      width="620px"
      :close-on-click-modal="false"
    >
      <template v-if="current">
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="商家名称">{{ current.name }}</el-descriptions-item>
          <el-descriptions-item label="校区">{{ campusText(current.campus) }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ current.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="佣金比例">{{ formatRate(current.commissionRate) }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ current.address }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">
            {{ formatDateTime(current.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="auditMeta(current.auditStatus).type" size="small">
              {{ auditMeta(current.auditStatus).text }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="img-block">
          <div class="img-group">
            <div class="img-label">营业执照</div>
            <el-image
              v-if="current.licenseUrl"
              :src="current.licenseUrl"
              :preview-src-list="previewList(current.licenseUrl)"
              fit="cover"
              class="img-lg"
            >
              <template #error>
                <div class="thumb-err">加载失败</div>
              </template>
            </el-image>
            <div v-else class="img-placeholder">未上传</div>
          </div>
          <div class="img-group">
            <div class="img-label">食品经营许可证</div>
            <el-image
              v-if="current.permitUrl"
              :src="current.permitUrl"
              :preview-src-list="previewList(current.permitUrl)"
              fit="cover"
              class="img-lg"
            >
              <template #error>
                <div class="thumb-err">加载失败</div>
              </template>
            </el-image>
            <div v-else class="img-placeholder">未上传</div>
          </div>
        </div>

        <div class="img-block">
          <div class="img-group img-group-wide">
            <div class="img-label">门店照片（{{ current.photos?.length ?? 0 }} 张）</div>
            <div v-if="current.photos && current.photos.length" class="thumbs">
              <el-image
                v-for="(photo, idx) in current.photos"
                :key="idx"
                :src="photo"
                :preview-src-list="current.photos"
                :initial-index="idx"
                fit="cover"
                class="img-lg"
              >
                <template #error>
                  <div class="thumb-err">加载失败</div>
                </template>
              </el-image>
            </div>
            <div v-else class="img-placeholder">未上传门店照片</div>
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
              placeholder="驳回时必填，例如：门店照片与营业执照信息不一致，请重新上传"
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
.shop-name {
  font-size: 13px;
  margin-bottom: 4px;
}

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
  width: 34px;
  height: 34px;
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

.img-group-wide {
  width: 100%;
}

.img-label {
  font-size: 12px;
  color: #606266;
}

.img-lg {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  cursor: pointer;
}

.img-placeholder {
  width: 96px;
  height: 96px;
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
