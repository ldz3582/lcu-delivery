<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  Bicycle,
  Money,
  Refresh,
  Shop,
  ShoppingCart,
  User,
  Van,
} from '@element-plus/icons-vue'
import { getOverview, type Overview } from '@/api'
import { formatMoney } from '@/utils/format'

const loading = ref(false)
const data = ref<Overview | null>(null)

async function load() {
  loading.value = true
  try {
    data.value = await getOverview()
  } catch {
    // 已统一提示
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page" v-loading="loading">
    <div class="page-title">
      <h2>平台概览</h2>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-row :gutter="16">
      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon blue"><el-icon><ShoppingCart /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">今日订单</div>
            <div class="stat-value">{{ data?.todayOrders ?? 0 }}</div>
            <div class="stat-sub muted">单</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon green"><el-icon><Money /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">今日 GMV</div>
            <div class="stat-value">¥ {{ formatMoney(data?.todayGmv) }}</div>
            <div class="stat-sub muted">已支付订单金额</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon orange"><el-icon><Shop /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">商家数</div>
            <div class="stat-value">{{ data?.totalShops ?? 0 }}</div>
            <div class="stat-sub">
              待审核
              <el-tag size="small" :type="(data?.pendingShops ?? 0) > 0 ? 'warning' : 'info'">
                {{ data?.pendingShops ?? 0 }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon purple"><el-icon><Bicycle /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">骑手数</div>
            <div class="stat-value">{{ data?.totalRiders ?? 0 }}</div>
            <div class="stat-sub">
              待审核
              <el-tag size="small" :type="(data?.pendingRiders ?? 0) > 0 ? 'warning' : 'info'">
                {{ data?.pendingRiders ?? 0 }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon cyan"><el-icon><User /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">用户数</div>
            <div class="stat-value">{{ data?.totalUsers ?? 0 }}</div>
            <div class="stat-sub muted">注册用户</div>
          </div>
        </div>
      </el-col>

      <el-col :xs="24" :sm="12" :lg="8" class="col">
        <div class="stat-card">
          <div class="stat-icon teal"><el-icon><Van /></el-icon></div>
          <div class="stat-body">
            <div class="stat-label">配送中</div>
            <div class="stat-value">{{ data?.inDelivery ?? 0 }}</div>
            <div class="stat-sub muted">待取餐 + 配送中</div>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.col {
  margin-bottom: 16px;
}

.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  display: flex;
  align-items: center;
  gap: 16px;
  height: 100%;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.stat-icon {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex: none;
}

.blue {
  background: #ecf5ff;
  color: #409eff;
}
.green {
  background: #f0f9eb;
  color: #67c23a;
}
.orange {
  background: #fdf6ec;
  color: #e6a23c;
}
.purple {
  background: #f4f4ff;
  color: #7a7ae6;
}
.cyan {
  background: #eef9fa;
  color: #17b3c4;
}
.teal {
  background: #edf7f3;
  color: #2fa97c;
}

.stat-body {
  min-width: 0;
}

.stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.stat-sub {
  margin-top: 6px;
  font-size: 12px;
  display: flex;
  align-items: center;
  gap: 6px;
}
</style>
