<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createCoupon,
  getCoupons,
  updateCouponStatus,
  type Coupon,
  type CouponCreatePayload,
} from '@/api'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const list = ref<Coupon[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

async function load() {
  loading.value = true
  try {
    const res = await getCoupons({ page: page.value, size: size.value })
    list.value = res.list ?? []
    total.value = res.total ?? 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
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

/** 上架 / 下架；返回 false 时 el-switch 保持原状态 */
async function handleToggleStatus(row: Coupon): Promise<boolean> {
  const target = row.status === 1 ? 0 : 1
  const action = target === 1 ? '上架' : '下架'
  try {
    await ElMessageBox.confirm(`确定${action}优惠券「${row.name}」？`, `${action}优惠券`, {
      confirmButtonText: action,
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return false
  }
  try {
    await updateCouponStatus(row.id, target)
    row.status = target
    ElMessage.success(`已${action}`)
    return true
  } catch {
    return false
  }
}

// ---------------- 新建弹窗 ----------------

interface CouponForm {
  name: string
  type: number
  /** 门槛，单位元 */
  thresholdYuan: number
  /** 满减：减免金额（元） */
  amountYuan: number
  /** 折扣：几折（如 8.8） */
  discount: number
  total: number
  scope: number
  shopId: number | undefined
  startAt: string
  endAt: string
}

function emptyForm(): CouponForm {
  return {
    name: '',
    type: 1,
    thresholdYuan: 0,
    amountYuan: 5,
    discount: 9,
    total: 100,
    scope: 1,
    shopId: undefined,
    startAt: '',
    endAt: '',
  }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<CouponForm>(emptyForm())

const rules: FormRules = {
  name: [
    { required: true, message: '请输入券名称', trigger: 'blur' },
    { max: 30, message: '券名称最多 30 个字', trigger: 'blur' },
  ],
  type: [{ required: true, message: '请选择券类型', trigger: 'change' }],
  thresholdYuan: [
    { required: true, message: '请输入使用门槛', trigger: 'change' },
    {
      validator: (_rule, _value, callback) => {
        if (form.thresholdYuan < 0) {
          callback(new Error('使用门槛不能小于 0'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  amountYuan: [
    {
      validator: (_rule, _value, callback) => {
        if (form.type !== 1) {
          callback()
          return
        }
        if (!form.amountYuan || form.amountYuan <= 0) {
          callback(new Error('减免金额必须大于 0'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  discount: [
    {
      validator: (_rule, _value, callback) => {
        if (form.type !== 2) {
          callback()
          return
        }
        if (!form.discount || form.discount <= 0 || form.discount >= 10) {
          callback(new Error('折扣需在 0.1 ~ 9.9 折之间'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  total: [
    { required: true, message: '请输入发行量', trigger: 'change' },
    {
      validator: (_rule, _value, callback) => {
        if (!form.total || form.total < 1) {
          callback(new Error('发行量必须大于 0'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  shopId: [
    {
      validator: (_rule, _value, callback) => {
        if (form.scope === 2 && !form.shopId) {
          callback(new Error('指定店铺券必须填写店铺 ID'))
          return
        }
        callback()
      },
      trigger: 'change',
    },
  ],
  startAt: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endAt: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    {
      validator: (_rule, _value, callback) => {
        if (form.startAt && form.endAt && new Date(form.endAt) <= new Date(form.startAt)) {
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
  Object.assign(form, emptyForm())
  dialogVisible.value = true
  formRef.value?.clearValidate()
}

function handleTypeChange() {
  formRef.value?.clearValidate(['amountYuan', 'discount', 'shopId'])
}

function buildPayload(): CouponCreatePayload {
  return {
    name: form.name.trim(),
    type: form.type,
    threshold: Math.round((form.thresholdYuan || 0) * 100),
    amount:
      form.type === 1
        ? Math.round((form.amountYuan || 0) * 100)
        : Math.round((form.discount || 0) * 100),
    total: Number(form.total),
    scope: form.scope,
    shopId: form.scope === 2 ? form.shopId ?? null : null,
    startAt: form.startAt,
    endAt: form.endAt,
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await createCoupon(buildPayload())
    ElMessage.success('优惠券已创建')
    dialogVisible.value = false
    page.value = 1
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>优惠券</h2>
        <div class="title-actions">
          <el-button :icon="Refresh" @click="load">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新建优惠券</el-button>
        </div>
      </div>

      <div class="toolbar">
        <span class="muted">共 {{ total }} 张券</span>
      </div>

      <el-table v-loading="loading" :data="list" border stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="name" label="名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">
            <el-tag :type="row.type === 2 ? 'warning' : 'primary'" size="small" effect="light">
              {{ row.typeText }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="desc" label="优惠" min-width="170" show-overflow-tooltip />
        <el-table-column label="发行量" width="80" align="right">
          <template #default="{ row }"><span class="num">{{ row.total }}</span></template>
        </el-table-column>
        <el-table-column label="剩余" width="70" align="right">
          <template #default="{ row }">
            <span class="num" :class="{ warn: row.remain <= 0 }">{{ row.remain }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已领" width="70" align="right">
          <template #default="{ row }"><span class="num">{{ row.claimed }}</span></template>
        </el-table-column>
        <el-table-column label="已用" width="70" align="right">
          <template #default="{ row }"><span class="num">{{ row.used }}</span></template>
        </el-table-column>
        <el-table-column label="范围" width="125">
          <template #default="{ row }">
            <span>{{ row.scopeText }}</span>
            <span v-if="row.shopId" class="muted"> #{{ row.shopId }}</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期" width="180">
          <template #default="{ row }">
            <div class="num muted">{{ formatDateTime(row.startAt) }}</div>
            <div class="num muted">~ {{ formatDateTime(row.endAt) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 1"
              :before-change="() => handleToggleStatus(row)"
            />
            <span class="muted status-text">{{ row.status === 1 ? '上架' : '下架' }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无优惠券数据" :image-size="90" />
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

    <el-dialog
      v-model="dialogVisible"
      title="新建优惠券"
      width="580px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="110px"
        label-position="right"
      >
        <el-form-item label="券名称" prop="name">
          <el-input v-model="form.name" maxlength="30" show-word-limit placeholder="例如：新人券·满20减5" />
        </el-form-item>

        <el-form-item label="券类型" prop="type">
          <el-radio-group v-model="form.type" @change="handleTypeChange">
            <el-radio :value="1">满减</el-radio>
            <el-radio :value="2">折扣</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="使用门槛" prop="thresholdYuan">
          <el-input-number
            v-model="form.thresholdYuan"
            :min="0"
            :precision="2"
            :step="1"
            style="width: 160px"
          />
          <span class="muted unit">元（0 表示无门槛）</span>
        </el-form-item>

        <el-form-item v-if="form.type === 1" label="减免金额" prop="amountYuan">
          <el-input-number
            v-model="form.amountYuan"
            :min="0"
            :precision="2"
            :step="1"
            style="width: 160px"
          />
          <span class="muted unit">元</span>
        </el-form-item>

        <el-form-item v-else label="折扣" prop="discount">
          <el-input-number
            v-model="form.discount"
            :min="0.1"
            :max="9.9"
            :step="0.1"
            :precision="1"
            style="width: 160px"
          />
          <span class="muted unit">折（如 8.8 折填 8.8）</span>
        </el-form-item>

        <el-form-item label="发行量" prop="total">
          <el-input-number
            v-model="form.total"
            :min="1"
            :precision="0"
            :step="10"
            style="width: 160px"
          />
          <span class="muted unit">张</span>
        </el-form-item>

        <el-form-item label="适用范围" prop="scope">
          <el-radio-group v-model="form.scope" @change="handleTypeChange">
            <el-radio :value="1">全场</el-radio>
            <el-radio :value="2">指定店铺</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item v-if="form.scope === 2" label="店铺 ID" prop="shopId">
          <el-input-number
            v-model="form.shopId"
            :min="1"
            :precision="0"
            :controls="false"
            placeholder="请输入店铺 ID"
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item label="开始时间" prop="startAt">
          <el-date-picker
            v-model="form.startAt"
            type="datetime"
            placeholder="选择开始时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 220px"
          />
        </el-form-item>

        <el-form-item label="结束时间" prop="endAt">
          <el-date-picker
            v-model="form.endAt"
            type="datetime"
            placeholder="选择结束时间"
            format="YYYY-MM-DD HH:mm:ss"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 220px"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.title-actions {
  display: flex;
  gap: 10px;
}

.unit {
  margin-left: 8px;
  font-size: 13px;
}

.status-text {
  margin-left: 8px;
  font-size: 13px;
}

.warn {
  color: #e6a23c;
}
</style>
