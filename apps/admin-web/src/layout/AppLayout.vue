<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  Bicycle,
  User,
  DataBoard,
  Document,
  Money,
  CreditCard,
  Setting,
  Shop,
  Ticket,
  FirstAidKit,
  ChatDotRound,
  Warning,
  CircleClose,
  Message,
  SwitchButton,
} from '@element-plus/icons-vue'
import { NAME_KEY, ROLE_KEY, TOKEN_KEY } from '@/api/http'

const router = useRouter()
const route = useRoute()

const realName = ref(localStorage.getItem(NAME_KEY) || '管理员')
const role = ref(localStorage.getItem(ROLE_KEY) || '')

const activeMenu = computed(() => route.path)

const menus = [
  { path: '/overview', label: '概览', icon: DataBoard },
  { path: '/users', label: '用户管理', icon: User },
  { path: '/shops', label: '商家审核', icon: Shop },
  { path: '/riders', label: '骑手审核', icon: Bicycle },
  { path: '/orders', label: '订单', icon: Document },
  { path: '/settlements', label: '结算', icon: Money },
  { path: '/withdrawals', label: '提现审核', icon: CreditCard },
  { path: '/coupons', label: '优惠券', icon: Ticket },
  { path: '/after-sales', label: '售后仲裁', icon: FirstAidKit },
  { path: '/incidents', label: '配送异常', icon: Warning },
  { path: '/penalties', label: '骑手处罚', icon: CircleClose },
  { path: '/reviews', label: '评价管理', icon: ChatDotRound },
  { path: '/notify-logs', label: '消息日志', icon: Message },
  { path: '/configs', label: '平台配置', icon: Setting },
]

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(NAME_KEY)
  localStorage.removeItem(ROLE_KEY)
  router.replace('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="210px" class="aside">
      <div class="logo">
        <span class="logo-mark">校</span>
        <span class="logo-text">校园外卖后台</span>
      </div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-title">{{ route.meta.title || '' }}</div>
        <div class="header-right">
          <span class="user">{{ realName }}</span>
          <el-tag v-if="role" size="small" type="info">{{ role }}</el-tag>
          <el-button link type="primary" :icon="SwitchButton" @click="handleLogout">
            退出
          </el-button>
        </div>
      </el-header>

      <el-main class="main">
        <router-view v-slot="{ Component }">
          <component :is="Component" />
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.aside {
  background: #ffffff;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 18px;
  border-bottom: 1px solid #f2f3f5;
}

.logo-mark {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #409eff;
  color: #fff;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.logo-text {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.menu {
  border-right: none;
  padding-top: 8px;
}

.header {
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user {
  font-size: 14px;
  color: #606266;
}

.main {
  background: #f5f7fa;
  padding: 16px;
  overflow-y: auto;
}
</style>
