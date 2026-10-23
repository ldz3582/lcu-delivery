<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { login } from '@/api'
import { NAME_KEY, ROLE_KEY, TOKEN_KEY } from '@/api/http'

const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: 'admin',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const data = await login(form.username.trim(), form.password)
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(NAME_KEY, data.realName || data.username || '管理员')
    localStorage.setItem(ROLE_KEY, data.role || '')
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.replace(redirect)
  } catch {
    // 错误提示已由 axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <h1>校园外卖</h1>
        <p>运营管理后台</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入管理员账号"
            clearable
            autocomplete="username"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
            autocomplete="current-password"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="submit-btn"
            :loading="loading"
            native-type="submit"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <p class="tip">仅限平台管理员使用，请妥善保管账号密码</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef2f7 0%, #f7f9fc 45%, #eaf0f9 100%);
  padding: 24px;
}

.login-card {
  width: 380px;
  max-width: 100%;
  background: #fff;
  border-radius: 10px;
  padding: 32px 32px 20px;
  box-shadow: 0 8px 30px rgba(31, 45, 61, 0.09);
}

.brand {
  text-align: center;
  margin-bottom: 22px;
}

.brand h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 1px;
}

.brand p {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
  letter-spacing: 2px;
}

.submit-btn {
  width: 100%;
}

.tip {
  margin: 4px 0 0;
  text-align: center;
  font-size: 12px;
  color: #b1b5bd;
}
</style>
