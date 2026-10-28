<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createAccount,
  getAccountRoles,
  getAccounts,
  updateAccount,
  type AccountRole,
  type AdminAccount,
} from '@/api'
import { formatDateTime } from '@/utils/format'

/** 角色中文名兜底（后端 /accounts 也会返回 roleText） */
const ROLE_TEXT: Record<string, string> = {
  SUPER: '超级管理员',
  CS: '订单客服',
  FINANCE: '财务',
  AUDITOR: '商家/骑手审核员',
  OPERATOR: '运营',
}

const ROLE_TAG: Record<string, 'danger' | 'warning' | 'primary' | 'success' | 'info'> = {
  SUPER: 'danger',
  FINANCE: 'warning',
  AUDITOR: 'primary',
  CS: 'success',
  OPERATOR: 'info',
}

const ROLE_FALLBACK: AccountRole[] = Object.entries(ROLE_TEXT).map(([value, label]) => ({
  value,
  label,
}))

const loading = ref(false)
const list = ref<AdminAccount[]>([])
const roles = ref<AccountRole[]>(ROLE_FALLBACK)

function roleName(row: AdminAccount): string {
  return row.roleText || ROLE_TEXT[row.role] || row.role || '-'
}

function roleTagType(role: string) {
  return ROLE_TAG[role] ?? 'info'
}

async function load() {
  loading.value = true
  try {
    list.value = (await getAccounts()) ?? []
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function loadRoles() {
  try {
    const res = await getAccountRoles()
    if (res && res.length) roles.value = res
  } catch {
    // 下拉用兜底角色表
  }
}

// ---------------- 新建账号 ----------------

const createVisible = ref(false)
const createSubmitting = ref(false)
const createForm = reactive({
  username: '',
  password: '',
  realName: '',
  role: 'OPERATOR',
})

function openCreate() {
  createForm.username = ''
  createForm.password = ''
  createForm.realName = ''
  createForm.role = 'OPERATOR'
  createVisible.value = true
}

async function submitCreate() {
  const username = createForm.username.trim()
  if (username.length < 3) {
    ElMessage.warning('账号至少 3 位')
    return
  }
  if (createForm.password.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  createSubmitting.value = true
  try {
    await createAccount({
      username,
      password: createForm.password,
      realName: createForm.realName.trim() || username,
      role: createForm.role,
    })
    ElMessage.success('账号已创建')
    createVisible.value = false
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    createSubmitting.value = false
  }
}

// ---------------- 编辑账号 ----------------

const editVisible = ref(false)
const editSubmitting = ref(false)
const editCurrent = ref<AdminAccount | null>(null)
const editForm = reactive({
  realName: '',
  role: 'OPERATOR',
  status: 1,
  password: '',
})

function openEdit(row: AdminAccount) {
  editCurrent.value = row
  editForm.realName = row.realName ?? ''
  editForm.role = row.role || 'OPERATOR'
  editForm.status = Number(row.status) === 1 ? 1 : 0
  editForm.password = ''
  editVisible.value = true
}

async function submitEdit() {
  const row = editCurrent.value
  if (!row) return
  if (editForm.password && editForm.password.length < 6) {
    ElMessage.warning('重置密码至少 6 位')
    return
  }
  editSubmitting.value = true
  try {
    await updateAccount(row.id, {
      realName: editForm.realName.trim(),
      role: editForm.role,
      status: editForm.status,
      password: editForm.password.trim() || undefined,
    })
    ElMessage.success('账号已更新')
    editVisible.value = false
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    editSubmitting.value = false
  }
}

onMounted(() => {
  load()
  loadRoles()
})
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>账号权限</h2>
        <div class="title-actions">
          <el-button type="primary" :icon="Plus" @click="openCreate">新建账号</el-button>
          <el-button :icon="Refresh" @click="load">刷新</el-button>
        </div>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="角色权限由后端强制校验：超级管理员全通，财务/审核/客服/运营按模块隔离，越权访问会返回 403。"
        class="tip-alert"
      />

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="48" />
        <el-table-column prop="username" label="账号" min-width="90" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" min-width="80" show-overflow-tooltip />
        <el-table-column label="角色" width="140">
          <template #default="{ row }">
            <el-tag :type="roleTagType(row.role)" size="small" effect="light">
              {{ roleName(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="64">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">
              {{ row.statusText || (Number(row.status) === 1 ? '启用' : '停用') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登录 / 创建" width="160">
          <template #default="{ row }">
            <div class="num muted time-line">登 {{ formatDateTime(row.lastLoginAt) }}</div>
            <div class="num muted time-line">建 {{ formatDateTime(row.createdAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="60">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无后台账号" :image-size="90" />
        </template>
      </el-table>
    </div>

    <!-- 新建账号 -->
    <el-dialog v-model="createVisible" title="新建后台账号" width="480px" :close-on-click-modal="false">
      <el-form label-width="90px">
        <el-form-item label="账号" required>
          <el-input v-model="createForm.username" maxlength="32" placeholder="至少 3 位，登录用" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input
            v-model="createForm.password"
            type="password"
            show-password
            maxlength="64"
            placeholder="至少 6 位"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="createForm.realName" maxlength="32" placeholder="留空则使用账号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option
              v-for="opt in roles"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="createSubmitting" @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createSubmitting" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑账号 -->
    <el-dialog
      v-model="editVisible"
      :title="`编辑账号 · ${editCurrent?.username ?? ''}`"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="姓名">
          <el-input v-model="editForm.realName" maxlength="32" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option
              v-for="opt in roles"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
            inline-prompt
          />
        </el-form-item>
        <el-form-item label="重置密码">
          <el-input
            v-model="editForm.password"
            type="password"
            show-password
            maxlength="64"
            placeholder="留空则不修改密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="editSubmitting" @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存</el-button>
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

.tip-alert {
  margin-bottom: 14px;
}

.time-line {
  font-size: 11px;
  line-height: 1.6;
}
</style>
