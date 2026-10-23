<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh, Search } from '@element-plus/icons-vue'
import { getUsers, type AdminUser } from '@/api'

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
</style>
