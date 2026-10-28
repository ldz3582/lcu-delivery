<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAnnouncement,
  deleteAnnouncement,
  getAnnouncements,
  updateAnnouncement,
  updateAnnouncementStatus,
  type Announcement,
} from '@/api'
import { formatDateTime } from '@/utils/format'

const POSITION_TEXT: Record<number, string> = {
  1: '首页',
  2: '用户中心',
}

function positionText(position: number | null | undefined): string {
  if (position === null || position === undefined) return '-'
  return POSITION_TEXT[Number(position)] ?? `位置${position}`
}

const loading = ref(false)
const list = ref<Announcement[]>([])
/** '' = 全部 / 1 已发布 / 0 已下线 */
const statusFilter = ref<number | ''>('')

async function load() {
  loading.value = true
  try {
    list.value = (await getAnnouncements({
      status: statusFilter.value === '' ? undefined : Number(statusFilter.value),
    })) ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// ---------------- 新建 / 编辑 ----------------

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const currentId = ref<number | null>(null)
const form = reactive({
  title: '',
  content: '',
  position: 1,
  startAt: '',
  endAt: '',
})

function openCreate() {
  dialogMode.value = 'create'
  currentId.value = null
  form.title = ''
  form.content = ''
  form.position = 1
  form.startAt = ''
  form.endAt = ''
  dialogVisible.value = true
}

function openEdit(row: Announcement) {
  dialogMode.value = 'edit'
  currentId.value = row.id
  form.title = row.title ?? ''
  form.content = row.content ?? ''
  form.position = Number(row.position) || 1
  form.startAt = row.startAt ? row.startAt.replace(' ', 'T').slice(0, 19) : ''
  form.endAt = row.endAt ? row.endAt.replace(' ', 'T').slice(0, 19) : ''
  dialogVisible.value = true
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写公告标题')
    return
  }
  if (!form.content.trim()) {
    ElMessage.warning('请填写公告内容')
    return
  }
  if (form.startAt && form.endAt && form.startAt > form.endAt) {
    ElMessage.warning('结束时间不能早于开始时间')
    return
  }
  const payload = {
    title: form.title.trim(),
    content: form.content.trim(),
    position: form.position,
    startAt: form.startAt || null,
    endAt: form.endAt || null,
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createAnnouncement(payload)
      ElMessage.success('公告已创建')
    } else if (currentId.value != null) {
      await updateAnnouncement(currentId.value, payload)
      ElMessage.success('公告已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

// ---------------- 上下线 / 删除 ----------------

async function toggleStatus(row: Announcement) {
  const next = Number(row.status) === 1 ? 0 : 1
  const action = next === 1 ? '上线' : '下线'
  try {
    await ElMessageBox.confirm(`确认${action}公告「${row.title}」？`, `${action}公告`, {
      confirmButtonText: `确认${action}`,
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await updateAnnouncementStatus(row.id, next)
    row.status = next
    row.statusText = next === 1 ? '已发布' : '已下线'
    ElMessage.success(`已${action}`)
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  }
}

async function handleDelete(row: Announcement) {
  try {
    await ElMessageBox.confirm(`确认删除公告「${row.title}」？删除后不可恢复。`, '删除公告', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'error',
    })
  } catch {
    return
  }
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success('公告已删除')
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>平台公告</h2>
        <div class="title-actions">
          <el-button type="primary" :icon="Plus" @click="openCreate">新建公告</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <div class="toolbar">
        <el-select
          v-model="statusFilter"
          placeholder="全部状态"
          clearable
          style="width: 140px"
          @change="load"
        >
          <el-option label="已发布" :value="1" />
          <el-option label="已下线" :value="0" />
        </el-select>
        <span class="muted">共 {{ list.length }} 条</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="48" />
        <el-table-column prop="title" label="标题" min-width="120" show-overflow-tooltip />
        <el-table-column label="内容" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.content }}</span>
          </template>
        </el-table-column>
        <el-table-column label="位置" width="68">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ positionText(row.position) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="68">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">
              {{ row.statusText || (Number(row.status) === 1 ? '已发布' : '已下线') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效时间" width="138">
          <template #default="{ row }">
            <div class="num muted time-line">{{ formatDateTime(row.startAt) }}</div>
            <div class="num muted time-line">至 {{ formatDateTime(row.endAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="118">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button
                link
                :type="Number(row.status) === 1 ? 'warning' : 'success'"
                size="small"
                @click="toggleStatus(row)"
              >
                {{ Number(row.status) === 1 ? '下线' : '上线' }}
              </el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无公告，点击右上角新建" :image-size="90" />
        </template>
      </el-table>
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建公告' : '编辑公告'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="请输入公告标题" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="5"
            maxlength="2000"
            show-word-limit
            placeholder="请输入公告正文"
          />
        </el-form-item>
        <el-form-item label="展示位置">
          <el-select v-model="form.position" style="width: 100%">
            <el-option label="首页" :value="1" />
            <el-option label="用户中心" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空表示立即生效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="form.endAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="留空表示长期有效"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.title-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.time-line {
  font-size: 11px;
  line-height: 1.6;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 6px;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
</style>
