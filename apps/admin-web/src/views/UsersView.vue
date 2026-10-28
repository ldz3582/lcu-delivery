<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { banUser, getUserOrders, getUsers, type AdminUser, type UserOrder } from '@/api'
import { ORDER_STATUS, formatDateTime, formatMoney } from '@/utils/format'

const loading = ref(false)
const list = ref<AdminUser[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const query = reactive({ keyword: '' })

const roleText: Record<string, string> = {
  USER: '用户',
  MERCHANT: '商家',
  RIDER: '骑手',
  ADMIN: '管理员',
}

async function load() {
  loading.value = true
  try {
    const res = await getUsers({
      keyword: query.keyword || undefined,
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

function search() {
  page.value = 1
  load()
}

function reset() {
  query.keyword = ''
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

function fmtTime(v: string | null) {
  if (!v) return '-'
  return v.replace('T', ' ').slice(0, 19)
}

// ---------------- 查看订单 ----------------

const ordersVisible = ref(false)
const ordersLoading = ref(false)
const orders = ref<UserOrder[]>([])
const ordersUser = ref<AdminUser | null>(null)

async function openOrders(row: AdminUser) {
  ordersUser.value = row
  orders.value = []
  ordersVisible.value = true
  ordersLoading.value = true
  try {
    orders.value = (await getUserOrders(row.id)) ?? []
  } catch {
    orders.value = []
  } finally {
    ordersLoading.value = false
  }
}

function orderStatusMeta(status: number) {
  return ORDER_STATUS[status] ?? { text: '未知', type: 'info' as const }
}

// ---------------- 封禁 / 解封 ----------------

/** 后端用户列表暂未返回 status，操作后就地记忆 */
const bannedState = reactive<Record<number, boolean>>({})

function isBanned(row: AdminUser): boolean {
  if (row.status !== undefined && row.status !== null) return Number(row.status) === 0
  return Boolean(bannedState[row.id])
}

async function handleBan(row: AdminUser) {
  const currentlyBanned = isBanned(row)
  const target = currentlyBanned ? 1 : 0
  try {
    await ElMessageBox.confirm(
      currentlyBanned
        ? `确认解封用户「${row.nickname || row.id}」？解封后可正常下单。`
        : `确认封禁用户「${row.nickname || row.id}」？封禁后该用户不能下单。`,
      currentlyBanned ? '解封用户' : '封禁用户',
      {
        confirmButtonText: currentlyBanned ? '确认解封' : '确认封禁',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await banUser(row.id, target)
    row.status = target
    bannedState[row.id] = target === 0
    ElMessage.success(currentlyBanned ? '已解封' : '已封禁')
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-title">
      <h2>用户管理</h2>
      <div class="tools">
        <el-input
          v-model="query.keyword"
          placeholder="搜昵称 / openid"
          clearable
          style="width: 240px"
          @keyup.enter="search"
        />
        <el-button type="primary" :icon="Search" @click="search">查询</el-button>
        <el-button :icon="Refresh" @click="reset">重置</el-button>
      </div>
    </div>

    <el-table :data="list" border stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="nickname" label="昵称" min-width="140" />
      <el-table-column prop="openid" label="openid" min-width="220" show-overflow-tooltip />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ roleText[row.role] || row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="校内核验" width="110">
        <template #default="{ row }">
          <el-tag v-if="row.identityStatus === 1" type="success" size="small">已核验</el-tag>
          <el-tag v-else type="info" size="small">未核验</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="phoneMask" label="手机号" width="130">
        <template #default="{ row }">{{ row.phoneMask || '-' }}</template>
      </el-table-column>
      <el-table-column prop="orderCount" label="下单数" width="100" />
      <el-table-column label="注册时间" width="180">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="176" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button link type="primary" size="small" @click="openOrders(row)">查看订单</el-button>
            <el-button
              link
              :type="isBanned(row) ? 'success' : 'danger'"
              size="small"
              @click="handleBan(row)"
            >
              {{ isBanned(row) ? '解封' : '封禁' }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        :current-page="page"
        :page-size="size"
        :page-sizes="[20, 50, 100]"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <!-- 用户订单 -->
    <el-dialog
      v-model="ordersVisible"
      :title="`用户订单 · ${ordersUser?.nickname ?? ordersUser?.id ?? ''}`"
      width="760px"
    >
      <el-table v-loading="ordersLoading" :data="orders" size="small" border stripe max-height="420">
        <el-table-column prop="id" label="订单 ID" width="86" />
        <el-table-column prop="orderNo" label="订单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="shopId" label="商家 ID" width="90" />
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <el-tag :type="orderStatusMeta(row.status).type" size="small" effect="light">
              {{ row.statusText || orderStatusMeta(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实付" width="110" align="right">
          <template #default="{ row }">
            <span class="num">¥ {{ formatMoney(row.payAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="下单时间" width="160">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无订单" :image-size="70" />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="ordersVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page { padding: 8px; }
.page-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.page-title h2 { margin: 0; font-size: 20px; }
.tools { display: flex; gap: 8px; align-items: center; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.row-actions { display: flex; gap: 8px; align-items: center; }
.row-actions :deep(.el-button + .el-button) { margin-left: 0; }
</style>
