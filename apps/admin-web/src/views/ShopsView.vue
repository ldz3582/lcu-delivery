<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import {
  auditShop,
  createShopPenalty,
  freezeShop,
  getShopDetail,
  getShopProducts,
  getShopRevenue,
  getShops,
  offlineProduct,
  updateShop,
  type RevenuePeriod,
  type Shop,
  type ShopProduct,
  type ShopRevenue,
  type ShopUpdatePayload,
} from '@/api'
import { auditMeta, campusText, formatDateTime, formatMoney, formatRate } from '@/utils/format'

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

// ---------------- 审核弹窗 ----------------

const auditVisible = ref(false)
const current = ref<Shop | null>(null)
const reason = ref('')
const submitting = ref(false)

function openAudit(row: Shop) {
  current.value = row
  reason.value = ''
  auditVisible.value = true
}

function closeAudit() {
  auditVisible.value = false
  current.value = null
  reason.value = ''
}

async function submitAudit(pass: boolean) {
  const row = current.value
  if (!row) return
  const text = reason.value.trim()
  if (!pass && !text) {
    ElMessage.warning('请填写驳回原因，商家端会看到这段说明')
    return
  }
  submitting.value = true
  try {
    await auditShop(row.id, pass, pass ? '' : text)
    ElMessage.success(pass ? '审核已通过' : '已驳回，已记录驳回原因')
    closeAudit()
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    submitting.value = false
  }
}

/** el-image 预览用：把单张图包成数组 */
function previewList(url: string | undefined | null): string[] {
  return url ? [url] : []
}

// ---------------- 商家编辑 ----------------

const editVisible = ref(false)
const editLoading = ref(false)
const editSubmitting = ref(false)
const editCurrent = ref<Shop | null>(null)
/** 详情接口是否成功回填（未回填时营业时间/费用等字段不提交，避免误清空） */
const detailLoaded = ref(false)

const editForm = reactive({
  name: '',
  contactPhone: '',
  openTime: '',
  notice: '',
  minAmountYuan: null as number | null,
  packFeeYuan: null as number | null,
  deliveryFeeYuan: null as number | null,
  commissionRate: null as number | null,
})

async function openEdit(row: Shop) {
  editCurrent.value = row
  detailLoaded.value = false
  editForm.name = row.name ?? ''
  editForm.contactPhone = row.contactPhone ?? ''
  editForm.openTime = ''
  editForm.notice = ''
  editForm.minAmountYuan = null
  editForm.packFeeYuan = null
  editForm.deliveryFeeYuan = null
  editForm.commissionRate = row.commissionRate != null ? Number(row.commissionRate) : null
  editVisible.value = true
  editLoading.value = true
  try {
    // 列表接口不返回营业时间/公告/费用，用公开详情接口补全
    const d = await getShopDetail(row.id)
    editForm.openTime = d.openTime ?? ''
    editForm.notice = d.notice ?? ''
    editForm.minAmountYuan = d.minAmount != null ? Number(d.minAmount) / 100 : null
    editForm.packFeeYuan = d.packFee != null ? Number(d.packFee) / 100 : null
    editForm.deliveryFeeYuan = d.deliveryFee != null ? Number(d.deliveryFee) / 100 : null
    detailLoaded.value = true
  } catch {
    // 详情失败仍可改名称/电话/佣金，其余字段留空不提交
  } finally {
    editLoading.value = false
  }
}

function fenOf(yuan: number | null): number | undefined {
  if (yuan === null || yuan === undefined || Number.isNaN(Number(yuan))) return undefined
  return Math.round(Number(yuan) * 100)
}

async function submitEdit() {
  const row = editCurrent.value
  if (!row) return
  if (!editForm.name.trim()) {
    ElMessage.warning('请填写商家名称')
    return
  }
  const rate = editForm.commissionRate
  if (rate !== null && (Number(rate) < 0 || Number(rate) > 1)) {
    ElMessage.warning('佣金比例必须在 0~1 之间（如 0.06 表示 6%）')
    return
  }
  const payload: ShopUpdatePayload = {
    name: editForm.name.trim(),
    contactPhone: editForm.contactPhone.trim(),
    commissionRate: rate !== null ? String(rate) : undefined,
  }
  if (detailLoaded.value) {
    payload.openTime = editForm.openTime.trim()
    payload.notice = editForm.notice.trim()
    payload.minAmount = fenOf(editForm.minAmountYuan)
    payload.packFee = fenOf(editForm.packFeeYuan)
    payload.deliveryFee = fenOf(editForm.deliveryFeeYuan)
  }
  editSubmitting.value = true
  try {
    await updateShop(row.id, payload)
    ElMessage.success('商家资料已更新')
    editVisible.value = false
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    editSubmitting.value = false
  }
}

// ---------------- 冻结 / 解冻 ----------------

function isFrozen(row: Shop): boolean {
  return Number(row.auditStatus) === 3
}

/** 只有已通过(1)可冻结 */
function canFreeze(row: Shop): boolean {
  return Number(row.auditStatus) === 1
}

/** 只有已冻结(3)可解冻 */
function canUnfreeze(row: Shop): boolean {
  return Number(row.auditStatus) === 3
}

function freezeTip(row: Shop): string {
  if (canFreeze(row)) return '冻结后商家不能接单、用户端不可见'
  if (canUnfreeze(row)) return '解冻后店铺为休息中，需商家自行开张'
  return '仅「已通过」的商家可冻结；未审核/已驳回不可操作'
}

async function handleFreeze(row: Shop) {
  if (!canFreeze(row) && !canUnfreeze(row)) {
    ElMessage.warning('仅「已通过」的商家可冻结，仅「已冻结」的商家可解冻')
    return
  }
  const frozen = canFreeze(row)
  try {
    await ElMessageBox.confirm(
      frozen
        ? `确认冻结「${row.name}」？冻结后商家不能接单、用户端不可见。`
        : `确认解冻「${row.name}」？解冻后店铺为休息中，需商家自行开张。`,
      frozen ? '冻结商家' : '解冻商家',
      {
        confirmButtonText: frozen ? '确认冻结' : '确认解冻',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
  } catch {
    return
  }
  try {
    await freezeShop(row.id, frozen)
    row.auditStatus = frozen ? 3 : 1
    // 后端冻结/解冻都会把营业状态置为休息，解冻不会自动开张
    row.bizStatus = 0
    ElMessage.success(
      frozen ? '已冻结，商家已停止营业' : '已解冻，店铺为休息中，需商家自行开张',
    )
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  }
}

// ---------------- 处罚 ----------------

const penaltyVisible = ref(false)
const penaltySubmitting = ref(false)
const penaltyCurrent = ref<Shop | null>(null)
const penaltyForm = reactive({
  type: 1,
  amountYuan: 0,
  remark: '',
})

const penaltyTypeOptions = [
  { value: 1, label: '警告' },
  { value: 2, label: '罚款' },
  { value: 3, label: '下架商品' },
  { value: 4, label: '暂停营业' },
]

function penaltyTypeText(type: number): string {
  return penaltyTypeOptions.find((o) => o.value === Number(type))?.label ?? `类型${type}`
}

function openPenalty(row: Shop) {
  penaltyCurrent.value = row
  penaltyForm.type = 1
  penaltyForm.amountYuan = 0
  penaltyForm.remark = ''
  penaltyVisible.value = true
}

async function submitPenalty() {
  const row = penaltyCurrent.value
  if (!row) return
  if (!penaltyForm.remark.trim()) {
    ElMessage.warning('请填写处罚备注，便于追溯')
    return
  }
  penaltySubmitting.value = true
  try {
    const pid = await createShopPenalty(row.id, {
      type: penaltyForm.type,
      amount: penaltyForm.type === 2 ? Math.round(Number(penaltyForm.amountYuan || 0) * 100) : 0,
      remark: penaltyForm.remark.trim(),
    })
    ElMessage.success(`处罚已记录（编号 ${pid}）`)
    penaltyVisible.value = false
    if (revenueVisible.value && revenueCurrent.value?.id === row.id) {
      await loadRevenue(row.id)
    }
    await load()
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    penaltySubmitting.value = false
  }
}

// ---------------- 营收 ----------------

const revenueVisible = ref(false)
const revenueLoading = ref(false)
const revenue = ref<ShopRevenue | null>(null)
const revenueCurrent = ref<Shop | null>(null)

async function loadRevenue(id: number) {
  revenueLoading.value = true
  try {
    revenue.value = await getShopRevenue(id)
  } catch {
    revenue.value = null
  } finally {
    revenueLoading.value = false
  }
}

async function openRevenue(row: Shop) {
  revenueCurrent.value = row
  revenue.value = null
  revenueVisible.value = true
  await loadRevenue(row.id)
}

function periodOf(key: 'today' | 'week' | 'month'): RevenuePeriod {
  return revenue.value?.[key] ?? { orders: 0, gmv: 0, commission: 0 }
}

function penaltyTime(p: { createdAt?: string | null; created_at?: string | null }): string {
  return formatDateTime(p.createdAt ?? p.created_at ?? null)
}

// ---------------- 商品（违规下架） ----------------

const productsVisible = ref(false)
const productsLoading = ref(false)
const products = ref<ShopProduct[]>([])
const productsShop = ref<Shop | null>(null)
const offliningId = ref<number | null>(null)

async function loadProducts(shopId: number) {
  productsLoading.value = true
  try {
    products.value = (await getShopProducts(shopId)) ?? []
  } catch {
    products.value = []
  } finally {
    productsLoading.value = false
  }
}

async function openProducts(row: Shop) {
  productsShop.value = row
  products.value = []
  productsVisible.value = true
  await loadProducts(row.id)
}

/** 是否设置了有效特价（分） */
function hasSpecialPrice(row: ShopProduct): boolean {
  const special = Number(row.specialPrice ?? 0)
  return special > 0 && special < Number(row.price ?? 0)
}

async function handleOfflineProduct(row: ShopProduct) {
  const shop = productsShop.value
  if (!shop) return
  try {
    await ElMessageBox.confirm(
      `确认下架商品「${row.name}」？下架后用户端不可购买，可在商家端重新上架。`,
      '违规下架',
      { confirmButtonText: '确认下架', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return
  }
  offliningId.value = row.id
  try {
    await offlineProduct(row.id)
    row.status = 0
    row.statusText = '已下架'
    ElMessage.success('商品已下架')
    await loadProducts(shop.id)
  } catch {
    // 错误提示由 axios 拦截器统一弹出
  } finally {
    offliningId.value = null
  }
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
        <el-table-column prop="id" label="ID" width="56" />
        <el-table-column label="商家名称" width="126" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="shop-name">{{ row.name }}</div>
            <el-tag :type="row.bizStatus === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.bizStatus === 1 ? '营业中' : '休息中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="校区" width="72">
          <template #default="{ row }">{{ campusText(row.campus) }}</template>
        </el-table-column>
        <el-table-column prop="address" label="地址" min-width="110" show-overflow-tooltip />
        <el-table-column prop="contactPhone" label="联系电话" width="112" />
        <el-table-column label="证照" width="122">
          <template #default="{ row }">
            <div class="thumbs">
              <el-image
                v-if="row.licenseUrl"
                :src="row.licenseUrl"
                :preview-src-list="previewList(row.licenseUrl)"
                fit="cover"
                class="thumb"
                title="营业执照"
              >
                <template #error>
                  <div class="thumb-err">执照</div>
                </template>
              </el-image>
              <span v-else class="muted mini">执照 -</span>
              <el-image
                v-if="row.permitUrl"
                :src="row.permitUrl"
                :preview-src-list="previewList(row.permitUrl)"
                fit="cover"
                class="thumb"
                title="食品经营许可证"
              >
                <template #error>
                  <div class="thumb-err">许可</div>
                </template>
              </el-image>
              <span v-else class="muted mini">许可 -</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="门店照片" width="122">
          <template #default="{ row }">
            <div v-if="row.photos && row.photos.length" class="thumbs">
              <el-image
                v-for="(photo, idx) in row.photos.slice(0, 3)"
                :key="idx"
                :src="photo"
                :preview-src-list="row.photos"
                :initial-index="idx"
                fit="cover"
                class="thumb"
              >
                <template #error>
                  <div class="thumb-err">门店</div>
                </template>
              </el-image>
              <span v-if="row.photos.length > 3" class="muted mini">+{{ row.photos.length - 3 }}</span>
            </div>
            <span v-else class="muted mini">未上传</span>
          </template>
        </el-table-column>
        <el-table-column label="申请时间" width="140">
          <template #default="{ row }">
            <span class="num muted">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="88">
          <template #default="{ row }">
            <el-tag :type="auditMeta(row.auditStatus).type" size="small">
              {{ auditMeta(row.auditStatus).text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="驳回原因" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.rejectReason" class="reject-reason">{{ row.rejectReason }}</span>
            <span v-else class="muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="244" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" size="small" @click="openAudit(row)">
                {{ row.auditStatus === 0 ? '审核' : '查看' }}
              </el-button>
              <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button
                link
                :type="isFrozen(row) ? 'success' : 'warning'"
                size="small"
                :disabled="!canFreeze(row) && !canUnfreeze(row)"
                :title="freezeTip(row)"
                @click="handleFreeze(row)"
              >
                {{ isFrozen(row) ? '解冻' : '冻结' }}
              </el-button>
              <el-button link type="danger" size="small" @click="openPenalty(row)">处罚</el-button>
              <el-button link type="primary" size="small" @click="openRevenue(row)">营收</el-button>
              <el-button link type="primary" size="small" @click="openProducts(row)">商品</el-button>
            </div>
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

    <!-- 审核弹窗 -->
    <el-dialog
      v-model="auditVisible"
      :title="current?.auditStatus === 0 ? '商家入驻审核' : '商家资料'"
      width="620px"
      :close-on-click-modal="false"
    >
      <template v-if="current">
        <el-descriptions :column="2" border size="small" class="mb">
          <el-descriptions-item label="商家名称">{{ current.name }}</el-descriptions-item>
          <el-descriptions-item label="校区">{{ campusText(current.campus) }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ current.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="佣金比例">{{ formatRate(current.commissionRate) }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ current.address }}</el-descriptions-item>
          <el-descriptions-item label="申请时间">
            {{ formatDateTime(current.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="审核状态">
            <el-tag :type="auditMeta(current.auditStatus).type" size="small">
              {{ auditMeta(current.auditStatus).text }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="img-block">
          <div class="img-group">
            <div class="img-label">营业执照</div>
            <el-image
              v-if="current.licenseUrl"
              :src="current.licenseUrl"
              :preview-src-list="previewList(current.licenseUrl)"
              fit="cover"
              class="img-lg"
            >
              <template #error>
                <div class="thumb-err">加载失败</div>
              </template>
            </el-image>
            <div v-else class="img-placeholder">未上传</div>
          </div>
          <div class="img-group">
            <div class="img-label">食品经营许可证</div>
            <el-image
              v-if="current.permitUrl"
              :src="current.permitUrl"
              :preview-src-list="previewList(current.permitUrl)"
              fit="cover"
              class="img-lg"
            >
              <template #error>
                <div class="thumb-err">加载失败</div>
              </template>
            </el-image>
            <div v-else class="img-placeholder">未上传</div>
          </div>
        </div>

        <div class="img-block">
          <div class="img-group img-group-wide">
            <div class="img-label">门店照片（{{ current.photos?.length ?? 0 }} 张）</div>
            <div v-if="current.photos && current.photos.length" class="thumbs">
              <el-image
                v-for="(photo, idx) in current.photos"
                :key="idx"
                :src="photo"
                :preview-src-list="current.photos"
                :initial-index="idx"
                fit="cover"
                class="img-lg"
              >
                <template #error>
                  <div class="thumb-err">加载失败</div>
                </template>
              </el-image>
            </div>
            <div v-else class="img-placeholder">未上传门店照片</div>
          </div>
        </div>

        <div v-if="current.rejectReason" class="reject-box">
          上次驳回原因：{{ current.rejectReason }}
        </div>

        <el-form label-width="90px" class="mt">
          <el-form-item label="驳回原因">
            <el-input
              v-model="reason"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              :disabled="submitting"
              placeholder="驳回时必填，例如：门店照片与营业执照信息不一致，请重新上传"
            />
          </el-form-item>
        </el-form>
      </template>

      <template #footer>
        <el-button @click="closeAudit" :disabled="submitting">关闭</el-button>
        <el-button type="danger" :loading="submitting" @click="submitAudit(false)">驳回</el-button>
        <el-button
          type="success"
          :loading="submitting"
          :disabled="current?.auditStatus === 1"
          @click="submitAudit(true)"
        >
          通过
        </el-button>
      </template>
    </el-dialog>

    <!-- 编辑商家 -->
    <el-dialog
      v-model="editVisible"
      title="编辑商家资料"
      width="640px"
      :close-on-click-modal="false"
    >
      <div v-loading="editLoading">
        <el-alert
          v-if="!editLoading && !detailLoaded"
          type="warning"
          :closable="false"
          show-icon
          title="营业时间 / 公告 / 费用详情加载失败，这些字段留空将不提交修改。"
          class="mb"
        />
        <el-form label-width="110px">
          <el-form-item label="商家名称">
            <el-input v-model="editForm.name" maxlength="64" placeholder="请输入商家名称" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="editForm.contactPhone" maxlength="20" placeholder="请输入联系电话" />
          </el-form-item>
          <el-form-item label="营业时间">
            <el-input v-model="editForm.openTime" maxlength="64" placeholder="例如 08:00-21:30" />
          </el-form-item>
          <el-form-item label="店铺公告">
            <el-input
              v-model="editForm.notice"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="展示在用户端商家页的公告"
            />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="起送价" label-width="76px">
                <el-input-number
                  v-model="editForm.minAmountYuan"
                  :min="0"
                  :precision="2"
                  :step="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="打包费" label-width="76px">
                <el-input-number
                  v-model="editForm.packFeeYuan"
                  :min="0"
                  :precision="2"
                  :step="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="配送费" label-width="76px">
                <el-input-number
                  v-model="editForm.deliveryFeeYuan"
                  :min="0"
                  :precision="2"
                  :step="1"
                  controls-position="right"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="佣金比例">
            <div class="inline-field">
              <el-input-number
                v-model="editForm.commissionRate"
                :min="0"
                :max="1"
                :step="0.01"
                :precision="4"
                controls-position="right"
                style="width: 180px"
              />
              <span class="muted">0.06 = 6%，取值 0~1</span>
            </div>
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button :disabled="editSubmitting" @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 商家处罚 -->
    <el-dialog
      v-model="penaltyVisible"
      title="商家处罚"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-descriptions v-if="penaltyCurrent" :column="2" border size="small" class="mb">
        <el-descriptions-item label="商家">{{ penaltyCurrent.name }}</el-descriptions-item>
        <el-descriptions-item label="商家 ID">{{ penaltyCurrent.id }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="处罚类型">
          <el-select v-model="penaltyForm.type" style="width: 100%">
            <el-option
              v-for="opt in penaltyTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-if="penaltyForm.type === 2" label="罚款金额">
          <div class="inline-field">
            <el-input-number
              v-model="penaltyForm.amountYuan"
              :min="0"
              :precision="2"
              :step="10"
              controls-position="right"
              style="width: 180px"
            />
            <span class="muted">元</span>
          </div>
        </el-form-item>
        <el-alert
          v-if="penaltyForm.type === 2"
          type="warning"
          :closable="false"
          show-icon
          title="将从商家钱包余额扣款，余额不足会扣至 0 并记欠款"
          description="扣款会写入商家钱包 SHOP_PENALTY 流水；余额不足时按实际余额扣款，备注记录欠款金额。"
          class="mb"
        />
        <el-alert
          v-if="penaltyForm.type === 3"
          type="warning"
          :closable="false"
          show-icon
          title="将下架该店全部商品"
          class="mb"
        />
        <el-alert
          v-if="penaltyForm.type === 4"
          type="warning"
          :closable="false"
          show-icon
          title="将暂停营业"
          class="mb"
        />
        <el-form-item label="处罚备注">
          <el-input
            v-model="penaltyForm.remark"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="请填写违规事实与处罚依据"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="penaltySubmitting" @click="penaltyVisible = false">取消</el-button>
        <el-button type="danger" :loading="penaltySubmitting" @click="submitPenalty">
          确认处罚
        </el-button>
      </template>
    </el-dialog>

    <!-- 商家营收 -->
    <el-dialog
      v-model="revenueVisible"
      :title="`营收概览 · ${revenueCurrent?.name ?? ''}`"
      width="720px"
    >
      <div v-loading="revenueLoading">
        <el-row :gutter="12" class="mb">
          <el-col :span="8" v-for="item in [
            { key: 'today' as const, label: '今日' },
            { key: 'week' as const, label: '本周' },
            { key: 'month' as const, label: '本月' },
          ]" :key="item.key">
            <div class="revenue-card">
              <div class="revenue-title">{{ item.label }}</div>
              <div class="revenue-row">
                <span class="muted">单量</span>
                <span class="num strong">{{ periodOf(item.key).orders }} 单</span>
              </div>
              <div class="revenue-row">
                <span class="muted">GMV</span>
                <span class="num strong">¥ {{ formatMoney(periodOf(item.key).gmv) }}</span>
              </div>
              <div class="revenue-row">
                <span class="muted">佣金</span>
                <span class="num strong">¥ {{ formatMoney(periodOf(item.key).commission) }}</span>
              </div>
            </div>
          </el-col>
        </el-row>

        <div class="sub-title">处罚记录（最近 50 条）</div>
        <el-table :data="revenue?.penalties ?? []" size="small" border stripe max-height="260">
          <el-table-column prop="id" label="编号" width="76" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.type === 2 ? 'danger' : 'warning'" effect="light">
                {{ penaltyTypeText(row.type) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="110" align="right">
            <template #default="{ row }">
              <span class="num">¥ {{ formatMoney(row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">
              <span class="num muted">{{ penaltyTime(row) }}</span>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无处罚记录" :image-size="60" />
          </template>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="revenueVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 商家商品（违规下架） -->
    <el-dialog
      v-model="productsVisible"
      :title="`商品管理 · ${productsShop?.name ?? ''}`"
      width="760px"
    >
      <div class="toolbar products-toolbar">
        <span class="muted">
          共 {{ products.length }} 个商品；下架后用户端不可购买，商家端可重新上架。
        </span>
      </div>
      <el-table v-loading="productsLoading" :data="products" size="small" border stripe max-height="440">
        <el-table-column prop="id" label="ID" width="64" />
        <el-table-column prop="name" label="商品名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="价格" width="168">
          <template #default="{ row }">
            <div v-if="hasSpecialPrice(row)" class="price-cell">
              <el-tag type="danger" size="small" effect="light">特价</el-tag>
              <span class="num special">¥ {{ formatMoney(row.specialPrice) }}</span>
              <span class="num origin">¥ {{ formatMoney(row.price) }}</span>
            </div>
            <span v-else class="num">¥ {{ formatMoney(row.price) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="68" align="right" />
        <el-table-column prop="sales" label="销量" width="68" align="right" />
        <el-table-column label="状态" width="86">
          <template #default="{ row }">
            <el-tag :type="Number(row.status) === 1 ? 'success' : 'info'" size="small">
              {{ row.statusText || (Number(row.status) === 1 ? '在售' : '已下架') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="88" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="Number(row.status) === 1"
              link
              type="danger"
              size="small"
              :loading="offliningId === row.id"
              @click="handleOfflineProduct(row)"
            >
              下架
            </el-button>
            <span v-else class="muted mini">已下架</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="该商家暂无商品" :image-size="70" />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="productsVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.shop-name {
  font-size: 13px;
  margin-bottom: 4px;
}

.mini {
  font-size: 12px;
}

.thumbs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.thumb {
  width: 34px;
  height: 34px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  cursor: pointer;
}

.thumb-err {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #909399;
  background: #f5f7fa;
}

.reject-reason {
  color: #f56c6c;
}

.mb {
  margin-bottom: 14px;
}

.mt {
  margin-top: 6px;
}

.img-block {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
}

.img-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.img-group-wide {
  width: 100%;
}

.img-label {
  font-size: 12px;
  color: #606266;
}

.img-lg {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  overflow: hidden;
  cursor: pointer;
}

.img-placeholder {
  width: 96px;
  height: 96px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #909399;
  background: #fafafa;
}

.reject-box {
  color: #f56c6c;
  background: #fef0f0;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  padding: 8px 12px;
  font-size: 13px;
  margin-bottom: 12px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px 6px;
}

.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.inline-field {
  display: flex;
  align-items: center;
  gap: 8px;
}

.revenue-card {
  border: 1px solid #f0f2f5;
  border-radius: 8px;
  padding: 12px 14px;
  background: #fafbfc;
}

.revenue-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.revenue-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  line-height: 1.9;
}

.strong {
  font-weight: 600;
  color: #303133;
}

.sub-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin: 6px 0 10px;
}

.products-toolbar {
  margin-bottom: 10px;
}

.price-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.special {
  color: #f56c6c;
  font-weight: 600;
}

.origin {
  color: #909399;
  font-size: 12px;
  text-decoration: line-through;
}
</style>
