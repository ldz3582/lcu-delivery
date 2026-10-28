<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getFeedbacks, handleFeedback, type Feedback } from '@/api'
import { formatDateTime, type TagType } from '@/utils/format'

const loading = ref(false)
const list = ref<Feedback[]>([])
const query = reactive({ status: '' as number | '' })

const STATUS_TABS = [
  { value: '', label: '全部' },
  { value: 0, label: '待处理' },
  { value: 1, label: '处理中' },
  { value: 2, label: '已处理' },
]

const STATUS_TYPE: Record<number, TagType> = {
  0: 'warning',
  1: 'primary',
  2: 'success',
}

function statusType(status: number | null | undefined): TagType {
  return STATUS_TYPE[Number(status)] ?? 'info'
}

/** 列宽有限，时间显示 MM-DD HH:mm */
function shortTime(value: string | null | undefined): string {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(5, 16)
}

/** 后端 feedback.images 存的是 JSON 字符串，这里兼容字符串 / 数组 / 空 */
function imagesOf(value: string | string[] | null | undefined): string[] {
  if (!value) return []
  if (Array.isArray(value)) return value.filter((v) => typeof v === 'string' && v)
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.filter((v) => typeof v === 'string' && v)
  } catch {
    // 非 JSON 时忽略
  }
  return []
}

async function load() {
  loading.value = true
  try {
    list.value =
      (await getFeedbacks({
        status: query.status === '' ? undefined : Number(query.status),
      })) ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function handleStatusChange() {
  load()
}

// ---------------- 处理弹窗 ----------------

const handleVisible = ref(false)
const submitting = ref(false)
const current = ref<Feedback | null>(null)
const form = reactive({
  /** 1 处理中 / 2 已处理 */
  status: 2,
  reply: '',
})

function openHandle(row: Feedback) {
  current.value = row
  form.status = Number(row.status) === 2 ? 2 : Number(row.status) === 1 ? 1 : 2
  form.reply = row.reply ?? ''
  handleVisible.value = true
}

async function submitHandle() {
  const row = current.value
  if (!row) return
  const reply = form.reply.trim()
  if (form.status === 2 && !reply) {
    ElMessage.warning('标记「已处理」时请填写回复内容')
    return
  }
  submitting.value = true
  try {
    await handleFeedback(row.id, {
      status: form.status,
      // 空白回复不传，后端 COALESCE 保留原回复
      reply: reply || undefined,
    })
    ElMessage.success(form.status === 2 ? '已处理并回复' : '已标记为处理中')
    handleVisible.value = false
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
        <h2>用户反馈</h2>
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
        <el-table-column prop="id" label="ID" width="44" />
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag size="small" effect="light" type="info">{{ row.typeText }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="110" show-overflow-tooltip />
        <el-table-column label="图片" width="64">
          <template #default="{ row }">
            <div v-if="imagesOf(row.images).length" class="thumb-wrap">
              <el-image
                :src="imagesOf(row.images)[0]"
                :preview-src-list="imagesOf(row.images)"
                preview-teleported
                fit="cover"
                class="fb-thumb"
              >
                <template #error>
                  <div class="thumb-err">失效</div>
                </template>
              </el-image>
              <span v-if="imagesOf(row.images).length > 1" class="thumb-count">
                +{{ imagesOf(row.images).length - 1 }}
              </span>
            </div>
            <span v-else class="muted mini">-</span>
          </template>
        </el-table-column>
        <el-table-column label="联系方式" width="90" show-overflow-tooltip>
          <template #default="{ row }">{{ row.contact || '-' }}</template>
        </el-table-column>
        <el-table-column label="时间" width="126">
          <template #default="{ row }">
            <div class="num muted time-line">提交 {{ shortTime(row.createdAt) }}</div>
            <div v-if="row.handledAt" class="num muted time-line">
              处理 {{ shortTime(row.handledAt) }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" effect="light">
              {{ row.statusText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="86">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openHandle(row)">
              {{ Number(row.status) === 2 ? '查看/处理' : '处理' }}
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户反馈" :image-size="90" />
        </template>
      </el-table>
    </div>

    <!-- 处理弹窗 -->
    <el-dialog
      v-model="handleVisible"
      title="处理用户反馈"
      width="620px"
      :close-on-click-modal="false"
    >
      <template v-if="current">
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="反馈 ID">{{ current.id }}</el-descriptions-item>
          <el-descriptions-item label="用户 ID">{{ current.userId }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ current.typeText }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(current.status)" size="small" effect="light">
              {{ current.statusText }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="联系方式">{{ current.contact || '-' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">
            {{ formatDateTime(current.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="current.reply" label="当前回复" :span="2">
            {{ current.reply }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="block-title">反馈内容</div>
        <div class="content-box">{{ current.content }}</div>

        <div class="block-title">凭证图片</div>
        <div v-if="imagesOf(current.images).length" class="images">
          <el-image
            v-for="(img, idx) in imagesOf(current.images)"
            :key="idx"
            :src="img"
            :preview-src-list="imagesOf(current.images)"
            :initial-index="idx"
            preview-teleported
            fit="cover"
            class="fb-img"
          >
            <template #error>
              <div class="thumb-err">加载失败</div>
            </template>
          </el-image>
        </div>
        <div v-else class="muted empty-line">用户未上传图片</div>

        <el-form label-width="90px" class="handle-form">
          <el-form-item label="处理状态">
            <el-radio-group v-model="form.status" :disabled="submitting">
              <el-radio :value="1">处理中</el-radio>
              <el-radio :value="2">已处理</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="回复内容">
            <el-input
              v-model="form.reply"
              type="textarea"
              :rows="4"
              maxlength="500"
              show-word-limit
              :disabled="submitting"
              placeholder="填写给用户的回复；标记已处理时必填"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button :disabled="submitting" @click="handleVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">提交处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.thumb-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.fb-thumb {
  width: 36px;
  height: 36px;
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

.thumb-count {
  font-size: 10px;
  color: #909399;
  margin-left: 2px;
}

.mini {
  font-size: 11px;
}

.time-line {
  font-size: 11px;
  line-height: 1.6;
}

.mb {
  margin-bottom: 14px;
}

.block-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin: 14px 0 8px;
}

.content-box {
  white-space: pre-wrap;
  word-break: break-word;
  background: #fafbfc;
  border: 1px solid #f0f2f5;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.7;
  color: #606266;
  max-height: 160px;
  overflow-y: auto;
}

.images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.fb-img {
  width: 72px;
  height: 72px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  cursor: pointer;
}

.empty-line {
  font-size: 13px;
}

.handle-form {
  margin-top: 14px;
}
</style>
