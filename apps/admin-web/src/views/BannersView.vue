<script setup lang="ts">
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createBanner,
  deleteBanner,
  getBanners,
  updateBanner,
  updateBannerStatus,
  type Banner,
  type BannerPayload,
} from '@/api'

const loading = ref(false)
const list = ref<Banner[]>([])

const LINK_TYPE_TEXT: Record<number, string> = {
  1: '无跳转',
  2: '店铺',
  3: '券中心',
}

function linkTypeText(type: number | null | undefined): string {
  return LINK_TYPE_TEXT[Number(type)] ?? '无跳转'
}

/** 有效期列宽有限，展示 MM-DD HH:mm */
function shortTime(value: string | null | undefined): string {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(5, 16)
}

async function load() {
  loading.value = true
  try {
    list.value = (await getBanners()) ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

// ---------------- 上下架 ----------------

/** 返回 false 时 el-switch 保持原状态 */
async function handleToggleStatus(row: Banner): Promise<boolean> {
  const target = Number(row.status) === 1 ? 0 : 1
  const action = target === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确定${action} Banner「${row.title}」？`, `${action} Banner`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return false
  }
  try {
    await updateBannerStatus(row.id, target)
    row.status = target
    ElMessage.success(`已${action}`)
    return true
  } catch {
    return false
  }
}

// ---------------- 新建 / 编辑 ----------------

interface BannerForm {
  title: string
  image: string
  linkType: number
  linkValue: string
  sort: number
  status: number
  startAt: string
  endAt: string
}

function emptyForm(): BannerForm {
  return {
    title: '',
    image: '',
    linkType: 1,
    linkValue: '',
    sort: 0,
    status: 1,
    startAt: '',
    endAt: '',
  }
}

/** 后端时间可能是 2026-10-24 12:00:00 或带 T */
function toPickerValue(value: string | null | undefined): string {
  if (!value) return ''
  return String(value).replace(' ', 'T').slice(0, 19)
}

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const submitting = ref(false)
const currentId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive<BannerForm>(emptyForm())

const rules: FormRules = {
  title: [
    { required: true, message: '请输入 Banner 标题', trigger: 'blur' },
    { max: 64, message: '标题最多 64 个字', trigger: 'blur' },
  ],
  image: [{ required: true, message: '请填写图片 URL', trigger: 'blur' }],
  linkType: [{ required: true, message: '请选择跳转类型', trigger: 'change' }],
  linkValue: [
    {
      validator: (_rule, _value, callback) => {
        if (form.linkType === 2 && !form.linkValue.trim()) {
          callback(new Error('跳转店铺时请填写店铺 ID'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
  endAt: [
    {
      validator: (_rule, _value, callback) => {
        if (form.startAt && form.endAt && form.endAt <= form.startAt) {
          callback(new Error('结束时间必须晚于开始时间'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

function openCreate() {
  dialogMode.value = 'create'
  currentId.value = null
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row: Banner) {
  dialogMode.value = 'edit'
  currentId.value = row.id
  Object.assign(form, {
    title: row.title ?? '',
    image: row.image ?? '',
    linkType: Number(row.linkType) || 1,
    linkValue: row.linkValue != null ? String(row.linkValue) : '',
    sort: Number(row.sort) || 0,
    status: Number(row.status) === 1 ? 1 : 0,
    startAt: toPickerValue(row.startAt),
    endAt: toPickerValue(row.endAt),
  })
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function buildPayload(): BannerPayload {
  return {
    title: form.title.trim(),
    image: form.image.trim(),
    linkType: form.linkType,
    // 非店铺跳转传空串，利用 COALESCE 语义清掉旧店铺 ID
    linkValue: form.linkType === 2 ? form.linkValue.trim() : '',
    sort: Number(form.sort) || 0,
    status: form.status,
    startAt: form.startAt || null,
    endAt: form.endAt || null,
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (dialogMode.value === 'create') {
      await createBanner(buildPayload())
      ElMessage.success('Banner 已创建')
    } else if (currentId.value != null) {
      await updateBanner(currentId.value, buildPayload())
      ElMessage.success('Banner 已更新')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

// ---------------- 删除 ----------------

async function handleDelete(row: Banner) {
  try {
    await ElMessageBox.confirm(`确定删除 Banner「${row.title}」？删除后不可恢复。`, '删除 Banner', {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'error',
    })
  } catch {
    return
  }
  try {
    await deleteBanner(row.id)
    ElMessage.success('Banner 已删除')
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
        <h2>Banner 管理</h2>
        <div class="title-actions">
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建 Banner</el-button>
        </div>
      </div>

      <div class="toolbar">
        <span class="muted">共 {{ list.length }} 条 · 按排序值升序展示</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="48" />
        <el-table-column label="图片" width="76">
          <template #default="{ row }">
            <el-image
              v-if="row.image"
              :src="row.image"
              :preview-src-list="[row.image]"
              preview-teleported
              fit="cover"
              class="banner-thumb"
            >
              <template #error>
                <div class="thumb-err">失效</div>
              </template>
            </el-image>
            <span v-else class="muted mini">未设置</span>
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="110" show-overflow-tooltip />
        <el-table-column label="跳转" width="90">
          <template #default="{ row }">
            <el-tag size="small" effect="plain" type="info">{{ linkTypeText(row.linkType) }}</el-tag>
            <div v-if="row.linkType === 2 && row.linkValue" class="muted mini">
              店铺 #{{ row.linkValue }}
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="58" align="right" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="Number(row.status) === 1"
              inline-prompt
              active-text="上架"
              inactive-text="下架"
              :before-change="() => handleToggleStatus(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="130">
          <template #default="{ row }">
            <div class="num muted time-line">{{ shortTime(row.startAt) }}</div>
            <div class="num muted time-line">至 {{ shortTime(row.endAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无 Banner，点击右上角新建" :image-size="90" />
        </template>
      </el-table>
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建 Banner' : '编辑 Banner'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" maxlength="64" show-word-limit placeholder="例如：开学季满减活动" />
        </el-form-item>

        <el-form-item label="图片 URL" prop="image">
          <div class="image-field">
            <el-input
              v-model="form.image"
              clearable
              placeholder="https://... 或 /uploads/xxx.jpg"
            />
            <div class="preview-box">
              <el-image
                v-if="form.image"
                :src="form.image"
                :preview-src-list="[form.image]"
                preview-teleported
                fit="cover"
                class="preview-img"
              >
                <template #error>
                  <div class="preview-err">图片加载失败</div>
                </template>
              </el-image>
              <div v-else class="preview-empty">图片预览</div>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="跳转类型" prop="linkType">
          <el-radio-group v-model="form.linkType">
            <el-radio :value="1">无跳转</el-radio>
            <el-radio :value="2">店铺</el-radio>
            <el-radio :value="3">券中心</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.linkType === 2" label="店铺 ID" prop="linkValue">
          <el-input v-model="form.linkValue" maxlength="32" placeholder="请输入要跳转的店铺 ID" />
        </el-form-item>
        <el-form-item v-else-if="form.linkType === 3" label="跳转目标">
          <span class="muted">券中心为固定入口，无需填写</span>
        </el-form-item>

        <el-form-item label="排序">
          <el-input-number
            v-model="form.sort"
            :min="0"
            :precision="0"
            :step="1"
            style="width: 150px"
          />
          <span class="muted unit">数字越小越靠前</span>
        </el-form-item>

        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="上架"
            inactive-text="下架"
            inline-prompt
          />
        </el-form-item>

        <el-form-item label="开始时间">
          <el-date-picker
            v-model="form.startAt"
            type="datetime"
            placeholder="留空表示立即生效"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 220px"
          />
        </el-form-item>

        <el-form-item label="结束时间" prop="endAt">
          <el-date-picker
            v-model="form.endAt"
            type="datetime"
            placeholder="留空表示长期有效"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 220px"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button :disabled="submitting" @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.title-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.banner-thumb {
  width: 54px;
  height: 30px;
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

.mini {
  font-size: 11px;
  line-height: 1.5;
  margin-top: 2px;
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

.image-field {
  width: 100%;
}

.preview-box {
  margin-top: 8px;
}

.preview-img {
  width: 240px;
  height: 108px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
}

.preview-empty {
  width: 240px;
  height: 108px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  background: #fafafa;
}

.preview-err {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #f56c6c;
  background: #fef0f0;
}

.unit {
  margin-left: 8px;
  font-size: 13px;
}
</style>
