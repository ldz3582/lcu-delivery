<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { auditShop, getShops, type Shop } from '@/api'
import { auditMeta, campusText, formatRate } from '@/utils/format'

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

async function handlePass(row: Shop) {
  try {
    await ElMessageBox.confirm(`确认通过商家「${row.name}」的入驻审核？`, '审核通过', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      type: 'info',
    })
  } catch {
    return
  }
  await auditShop(row.id, true, '')
  ElMessage.success('审核已通过')
  load()
}

async function handleReject(row: Shop) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(
      `请输入驳回商家「${row.name}」的原因`,
      '驳回审核',
      {
        confirmButtonText: '驳回',
        cancelButtonText: '取消',
        inputPlaceholder: '例如：证照信息不清晰',
        inputValidator: (v: string) => (v && v.trim() ? true : '请填写驳回原因'),
      },
    )
    reason = (value || '').trim()
  } catch {
    return
  }
  await auditShop(row.id, false, reason)
  ElMessage.success('已驳回')
  load()
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
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="商家名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="校区" width="100">
          <template #default="{ row }">{{ campusText(row.campus) }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column label="佣金比例" width="110">
          <template #default="{ row }">{{ formatRate(row.commissionRate) }}</template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag :type="auditMeta(row.auditStatus).type" size="small">
              {{ auditMeta(row.auditStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="营业状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.bizStatus === 1 ? 'success' : 'info'" size="small">
              {{ row.bizStatus === 1 ? '营业中' : '休息中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="证照" width="150">
          <template #default="{ row }">
            <el-link
              v-if="row.licenseUrl"
              :href="row.licenseUrl"
              target="_blank"
              type="primary"
              class="link"
            >
              营业执照
            </el-link>
            <span v-else class="muted">-</span>
            <el-link
              v-if="row.permitUrl"
              :href="row.permitUrl"
              target="_blank"
              type="primary"
              class="link"
            >
              许可证
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              type="success"
              :disabled="row.auditStatus === 1"
              @click="handlePass(row)"
            >
              通过
            </el-button>
            <el-button
              size="small"
              type="danger"
              :disabled="row.auditStatus === 2"
              @click="handleReject(row)"
            >
              驳回
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
  </div>
</template>

<style scoped>
.link + .link {
  margin-left: 8px;
}
</style>
