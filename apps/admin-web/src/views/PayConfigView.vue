<template>
  <div class="page">
    <div class="head">
      <h2>支付配置</h2>
      <div class="head-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :loading="testing" @click="runTest">测试连通性</el-button>
      </div>
    </div>

    <!-- 状态总览 -->
    <el-card shadow="never" class="card">
      <div class="status-row">
        <div class="status-item">
          <span class="lbl">当前模式</span>
          <el-tag :type="status.mock ? 'info' : 'success'" size="large">
            {{ status.mock ? '模拟支付（不扣款）' : '真实支付（真扣款）' }}
          </el-tag>
        </div>
        <div class="status-item">
          <span class="lbl">生效通道</span>
          <el-tag type="primary">{{ status.activeChannel }}</el-tag>
        </div>
        <div class="status-item">
          <span class="lbl">打款通道</span>
          <el-tag type="primary">{{ status.payoutChannel }}</el-tag>
        </div>
        <div class="status-item">
          <span class="lbl">配置来源</span>
          <el-tag>{{ status.source === 'db' ? '后台配置' : '环境变量' }}</el-tag>
        </div>
        <div class="status-item">
          <span class="lbl">就绪</span>
          <el-tag :type="status.ready ? 'success' : 'warning'">
            {{ status.ready ? '可以下单' : '配置不完整' }}
          </el-tag>
        </div>
      </div>

      <el-alert
        v-if="status.missing && status.missing.length"
        type="warning"
        :closable="false"
        show-icon
        class="mt12"
        :title="'还缺：' + status.missing.join('、')"
      />
      <el-alert
        v-else-if="!status.mock"
        type="success"
        :closable="false"
        show-icon
        class="mt12"
        title="真实支付配置已齐备"
      />
      <el-alert
        v-else
        type="info"
        :closable="false"
        show-icon
        class="mt12"
        title="当前是模拟支付：下单不产生真实扣款。填好下面的配置并把模式切到 real，即可真实收款。"
      />
    </el-card>

    <!-- 配置表单 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>通道参数</span>
          <span class="muted">密钥类字段留空表示不修改；保存后立即生效，无需重启</span>
        </div>
      </template>

      <el-form :model="form" label-width="140px" class="form">
        <el-form-item label="支付模式">
          <el-radio-group v-model="form.mode">
            <el-radio-button value="mock">模拟支付</el-radio-button>
            <el-radio-button value="real">真实支付</el-radio-button>
          </el-radio-group>
          <div class="tip">
            切换为真实支付前，务必先点「测试连通性」确认商户号与私钥可用。
          </div>
        </el-form-item>

        <el-form-item label="小程序 AppID">
          <el-input v-model="form.appId" placeholder="wx 开头，JSAPI 下单必需" clearable />
        </el-form-item>

        <el-form-item label="公众号 AppID">
          <el-input v-model="form.mpAppId" placeholder="微信内 H5 支付用，可留空" clearable />
        </el-form-item>

        <el-form-item label="商户号">
          <el-input v-model="form.mchId" placeholder="微信支付商户号 mchid" clearable />
        </el-form-item>

        <el-form-item label="证书序列号">
          <el-input v-model="form.serialNo" placeholder="商户 API 证书序列号" clearable />
        </el-form-item>

        <el-form-item label="APIv3 密钥">
          <el-input
            v-model="form.apiV3Key"
            type="password"
            show-password
            :placeholder="status.apiV3KeySet ? '已配置（留空保持不变）' : '32 位字符'"
          />
        </el-form-item>

        <el-form-item label="商户私钥">
          <el-input
            v-model="form.privateKeyPem"
            type="textarea"
            :rows="4"
            :placeholder="
              status.privateKeySet
                ? '已配置（留空保持不变）'
                : '粘贴 apiclient_key.pem 的全部内容，含 BEGIN/END PRIVATE KEY'
            "
          />
          <div class="tip">
            私钥只在服务端解密使用，数据库里存的是 AES-GCM 密文。请通过 HTTPS 访问后台再提交。
          </div>
        </el-form-item>

        <el-form-item label="回调地址">
          <el-input v-model="form.notifyUrl" placeholder="https://你的域名/api/pay/notify" clearable />
          <div class="tip">必须是公网可达的 https；不配置「主动查单」也能确认支付，但退款/打款通知会收不到。</div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
          <el-button @click="load">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 测试结果 -->
    <el-card v-if="testResult" shadow="never" class="card">
      <template #header>连通性测试结果</template>
      <el-alert
        :type="testResult.ok ? 'success' : 'error'"
        :closable="false"
        show-icon
        :title="testResult.message"
      />
      <div class="muted mt12">耗时 {{ testResult.elapsedMs }} ms</div>
    </el-card>

    <!-- 引导步骤 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>接入真实支付的 7 步</span>
          <span class="muted">前 5 步在微信侧完成，第 6 步回到本页</span>
        </div>
      </template>
      <el-steps direction="vertical" :active="0" class="steps">
        <el-step v-for="s in status.steps" :key="s.no" :title="s.no + '. ' + s.title" :description="s.detail" />
      </el-steps>
      <el-alert
        type="info"
        :closable="false"
        class="mt12"
        title="提示：没有营业执照就拿不到商户号；微信支付没有沙箱环境，模拟支付只能验证业务逻辑，不能验证真实扣款。"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  getPayConfig,
  savePayConfig,
  testPayConfig,
  type PayConfigStatus,
} from '@/api'

const loading = ref(false)
const saving = ref(false)
const testing = ref(false)

const status = ref<PayConfigStatus>({
  mode: 'mock',
  source: 'env',
  mock: true,
  appId: '',
  mpAppId: '',
  mchId: '',
  serialNo: '',
  apiV3KeySet: false,
  apiV3KeyMask: '',
  privateKeySet: false,
  privateKeyMask: '',
  notifyUrl: '',
  missing: [],
  ready: true,
  activeChannel: 'mock',
  payoutChannel: 'mock',
  steps: [],
})

const form = reactive({
  mode: 'mock',
  appId: '',
  mpAppId: '',
  mchId: '',
  serialNo: '',
  apiV3Key: '',
  privateKeyPem: '',
  notifyUrl: '',
})

const testResult = ref<{ ok: boolean; message: string; elapsedMs: number } | null>(null)

function fill(s: PayConfigStatus) {
  status.value = s
  form.mode = s.mode || 'mock'
  form.appId = s.appId || ''
  form.mpAppId = s.mpAppId || ''
  form.mchId = ''
  form.serialNo = ''
  form.apiV3Key = ''
  form.privateKeyPem = ''
  form.notifyUrl = s.notifyUrl || ''
}

async function load() {
  loading.value = true
  try {
    const s = await getPayConfig()
    // 商户号/序列号是脱敏的，不直接回填（避免把 **** 存回去）
    const keepMch = form.mchId
    const keepSerial = form.serialNo
    fill(s)
    form.mchId = keepMch && keepMch !== s.mchId ? keepMch : ''
    form.serialNo = keepSerial && keepSerial !== s.serialNo ? keepSerial : ''
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

async function save() {
  if (form.mode === 'real') {
    const hasMch = form.mchId || status.value.mchId
    if (!hasMch) {
      ElMessage.warning('切到真实支付前必须填商户号')
      return
    }
  }
  saving.value = true
  try {
    const next = await savePayConfig({
      mode: form.mode,
      appId: form.appId,
      mpAppId: form.mpAppId,
      mchId: form.mchId,
      serialNo: form.serialNo,
      apiV3Key: form.apiV3Key || undefined,
      privateKeyPem: form.privateKeyPem || undefined,
      notifyUrl: form.notifyUrl,
    })
    ElMessage.success('已保存，立即生效')
    fill(next)
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function runTest() {
  testing.value = true
  testResult.value = null
  try {
    testResult.value = await testPayConfig()
  } catch (e) {
    testResult.value = { ok: false, message: '测试请求失败', elapsedMs: 0 }
  } finally {
    testing.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  padding: 4px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.head h2 {
  margin: 0;
  font-size: 20px;
}
.head-actions {
  display: flex;
  gap: 8px;
}
.card {
  margin-bottom: 16px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}
.status-item {
  display: flex;
  align-items: center;
  gap: 8px;
}
.lbl {
  color: #6b7280;
  font-size: 13px;
}
.form {
  max-width: 820px;
}
.tip {
  color: #909399;
  font-size: 12px;
  line-height: 1.6;
  margin-top: 4px;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.mt12 {
  margin-top: 12px;
}
.steps {
  padding-left: 4px;
}
</style>
