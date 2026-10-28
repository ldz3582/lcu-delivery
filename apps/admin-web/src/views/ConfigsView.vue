<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getConfigs, getFences, updateConfig, type Fence, type SysConfig } from '@/api'
import { campusText } from '@/utils/format'

const loading = ref(false)
const fenceLoading = ref(false)
const configs = ref<SysConfig[]>([])
const fences = ref<Fence[]>([])

/** 非布尔配置的编辑草稿，key = configKey */
const drafts = reactive<Record<string, string>>({})
const saving = reactive<Record<string, boolean>>({})

function isBooleanConfig(row: SysConfig): boolean {
  const type = String(row.valueType || '').toUpperCase()
  const value = String(row.configValue ?? '').toLowerCase()
  return type.includes('BOOL') || value === 'true' || value === 'false'
}

function isTrue(row: SysConfig): boolean {
  return String(row.configValue ?? '').toLowerCase() === 'true'
}

/** 配送费模式：shop 按店铺设置 / platform 统一按平台基础费 */
function isFeeMode(row: SysConfig): boolean {
  return row.configKey === 'delivery.fee_mode'
}

/** 抽佣比例配置：用数字输入（0.06 = 6%） */
function isCommissionRate(row: SysConfig): boolean {
  return row.configKey === 'commission.rate_campus' || row.configKey === 'commission.rate_off_campus'
}

const feeModeOptions = [
  { value: 'shop', label: '按店铺设置（shop）' },
  { value: 'platform', label: '统一按平台基础费（platform）' },
]

/** 下拉切换后立即保存 */
async function handleFeeMode(row: SysConfig, value: string) {
  const prev = row.configValue
  saving[row.configKey] = true
  try {
    await updateConfig(row.configKey, value)
    row.configValue = value
    drafts[row.configKey] = value
    ElMessage.success(`「${row.configKey}」已更新为 ${value}`)
  } catch {
    row.configValue = prev
    drafts[row.configKey] = prev
  } finally {
    saving[row.configKey] = false
  }
}

function onRateInput(row: SysConfig, value: number | undefined | null) {
  drafts[row.configKey] = value === undefined || value === null ? '' : String(value)
}

async function loadConfigs() {
  loading.value = true
  try {
    const res = await getConfigs()
    configs.value = res ?? []
    for (const item of configs.value) {
      drafts[item.configKey] = item.configValue ?? ''
    }
  } catch {
    configs.value = []
  } finally {
    loading.value = false
  }
}

async function loadFences() {
  fenceLoading.value = true
  try {
    fences.value = (await getFences()) ?? []
  } catch {
    fences.value = []
  } finally {
    fenceLoading.value = false
  }
}

function refresh() {
  loadConfigs()
  loadFences()
}

async function handleSwitch(row: SysConfig, checked: unknown) {
  const next = checked ? 'true' : 'false'
  const prev = row.configValue
  saving[row.configKey] = true
  try {
    await updateConfig(row.configKey, next)
    row.configValue = next
    drafts[row.configKey] = next
    ElMessage.success(`「${row.configKey}」已更新`)
  } catch {
    row.configValue = prev
  } finally {
    saving[row.configKey] = false
  }
}

function isDirty(row: SysConfig): boolean {
  return (drafts[row.configKey] ?? '') !== (row.configValue ?? '')
}

async function handleSave(row: SysConfig) {
  const value = (drafts[row.configKey] ?? '').trim()
  if (value === (row.configValue ?? '')) return
  saving[row.configKey] = true
  try {
    await updateConfig(row.configKey, value)
    row.configValue = value
    drafts[row.configKey] = value
    ElMessage.success(`「${row.configKey}」已保存`)
  } catch {
    drafts[row.configKey] = row.configValue ?? ''
  } finally {
    saving[row.configKey] = false
  }
}

onMounted(refresh)
</script>

<template>
  <div class="page">
    <div class="page-card">
      <div class="page-title">
        <h2>平台配置</h2>
        <el-button :icon="Refresh" @click="refresh">刷新</el-button>
      </div>

      <el-alert
        type="success"
        :closable="false"
        show-icon
        title="这些开关改动立即生效（后端已读取）"
        description="布尔开关与配送费模式保存后立即写入并生效；抽佣比例等数值填写后点「保存」。"
        class="config-alert"
      />

      <el-table v-loading="loading" :data="configs" border stripe>
        <el-table-column prop="configKey" label="配置项" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <code class="key">{{ row.configKey }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ row.remark || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="groupKey" label="分组" width="90">
          <template #default="{ row }">
            <el-tag size="small" type="info" effect="plain">{{ row.groupKey || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="70">
          <template #default="{ row }">
            <span class="muted">{{ row.valueType || 'STRING' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前值" min-width="260">
          <template #default="{ row }">
            <el-switch
              v-if="isBooleanConfig(row)"
              :model-value="isTrue(row)"
              :loading="saving[row.configKey]"
              active-text="开启"
              inactive-text="关闭"
              inline-prompt
              @change="(val: boolean | string | number) => handleSwitch(row, val)"
            />
            <div v-else-if="isFeeMode(row)" class="edit-row">
              <el-select
                :model-value="drafts[row.configKey]"
                :loading="saving[row.configKey]"
                style="width: 210px"
                @change="(val: string) => handleFeeMode(row, val)"
              >
                <el-option
                  v-for="opt in feeModeOptions"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </div>
            <div v-else-if="isCommissionRate(row)" class="edit-row">
              <el-input-number
                :model-value="drafts[row.configKey] === '' ? undefined : Number(drafts[row.configKey])"
                :min="0"
                :max="1"
                :step="0.01"
                :precision="4"
                controls-position="right"
                style="width: 150px"
                title="0.06 = 6%，取值 0~1"
                @change="(val: number | undefined) => onRateInput(row, val)"
              />
              <el-button
                type="primary"
                size="default"
                :disabled="!isDirty(row)"
                :loading="saving[row.configKey]"
                @click="handleSave(row)"
              >
                保存
              </el-button>
            </div>
            <div v-else class="edit-row">
              <el-input
                v-model="drafts[row.configKey]"
                size="default"
                placeholder="请输入配置值"
                style="width: 170px"
                @keyup.enter="handleSave(row)"
              />
              <el-button
                type="primary"
                size="default"
                :disabled="!isDirty(row)"
                :loading="saving[row.configKey]"
                @click="handleSave(row)"
              >
                保存
              </el-button>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无配置项" :image-size="90" />
        </template>
      </el-table>
    </div>

    <div class="page-card fence-card">
      <div class="page-title">
        <h2>配送围栏（只读）</h2>
      </div>
      <el-table v-loading="fenceLoading" :data="fences" border stripe size="small">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="围栏名称" width="180" />
        <el-table-column label="校区" width="120">
          <template #default="{ row }">{{ campusText(row.campus) }}</template>
        </el-table-column>
        <el-table-column prop="wkt" label="多边形 WKT" min-width="320" show-overflow-tooltip />
        <template #empty>
          <el-empty description="暂无围栏数据" :image-size="70" />
        </template>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.key {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
  color: #4b5563;
}

.edit-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.config-alert {
  margin-bottom: 14px;
}

.fence-card {
  margin-top: 16px;
}
</style>
