<template>
  <view class="page">
    <view class="card">
      <view class="between">
        <text class="title">菜品管理</text>
        <text class="add" @click="toggleForm">{{ formVisible ? '收起' : '+ 新增' }}</text>
      </view>
    </view>

    <!-- 表单：新增 / 编辑 -->
    <view v-if="formVisible" class="card">
      <view class="between">
        <text class="form-title">{{ editingId ? '编辑菜品' : '新增菜品' }}</text>
        <text class="muted" @click="closeForm">收起</text>
      </view>

      <view class="field">
        <text>名称 <text class="req">*</text></text>
        <input v-model="form.name" placeholder="黄焖鸡米饭" />
      </view>
      <view class="field">
        <text>现价（元）<text class="req">*</text></text>
        <input v-model="form.priceYuan" type="digit" placeholder="15.00" />
      </view>
      <view class="field">
        <text>划线原价（元）</text>
        <input v-model="form.originPriceYuan" type="digit" placeholder="特价时填，留空不显示" />
      </view>
      <view class="field">
        <text>库存</text>
        <input v-model="form.stock" type="number" placeholder="-1 表示不限" />
      </view>
      <view class="field">
        <text>描述</text>
        <input v-model="form.description" placeholder="选填" />
      </view>

      <picker mode="selector" :range="categoryNames" :value="categoryIndex" @change="onCategory">
        <view class="field">
          <text>分类</text>
          <text class="picker-value">{{ categoryText }} ›</text>
        </view>
      </picker>

      <view class="field">
        <text>上架</text>
        <switch :checked="form.status === 1" color="#c2410c" @change="onStatus" />
      </view>

      <view class="field">
        <text>图片</text>
        <view class="img-wrap">
          <view v-if="form.image" class="img-box">
            <image :src="form.image" mode="aspectFill" class="img" @click="previewImage(form.image)" />
            <view class="img-del" @click.stop="form.image = ''">×</view>
          </view>
          <view v-else class="img-add" @click="uploadProductImage">
            <text class="img-plus">{{ uploading ? '⏳' : '+' }}</text>
          </view>
        </view>
      </view>

      <view class="field">
        <text>这是套餐</text>
        <switch :checked="form.isCombo" color="#c2410c" @change="onCombo" />
      </view>

      <view v-if="form.isCombo" class="combo-editor">
        <view v-for="(c, i) in form.comboItems" :key="i" class="combo-row">
          <text class="combo-name">{{ c.name }}</text>
          <view class="qty">
            <text class="op" @click="changeComboQty(i, -1)">-</text>
            <text class="num">{{ c.qty }}</text>
            <text class="op" @click="changeComboQty(i, 1)">+</text>
          </view>
          <text class="combo-del" @click="removeCombo(i)">移除</text>
        </view>

        <picker mode="selector" :range="comboNames" :value="0" @change="addCombo">
          <view class="combo-add">+ 添加套餐组成（选本店菜品）</view>
        </picker>
        <view class="muted mt8">下单只扣套餐自身库存，不扣组成菜品的库存</view>
      </view>

      <view class="btn" @click="save">{{ editingId ? '保存修改' : '创建菜品' }}</view>
    </view>

    <!-- 列表 -->
    <view class="list">
      <view v-for="p in products" :key="p.id" class="card item">
        <view class="row">
          <view class="thumb">
            <image v-if="p.image" :src="p.image" mode="aspectFill" class="thumb-img" />
            <text v-else class="thumb-text">{{ p.name.slice(0, 1) }}</text>
          </view>

          <view class="info">
            <view class="name-row">
              <text class="name">{{ p.name }}</text>
              <text v-if="p.hasSku === 1" class="tag">多规格</text>
              <text v-if="isComboRow(p)" class="tag combo">套餐</text>
            </view>
            <view class="price-row">
              <text class="price">{{ yuan(p.price) }}</text>
              <text v-if="showOrigin(p)" class="origin">{{ yuan(p.originPrice) }}</text>
            </view>
            <view class="muted">库存 {{ stockText(p.stock) }}</view>
          </view>

          <view class="right">
            <switch :checked="p.status === 1" color="#c2410c" @change="toggle(p)" />
            <text :class="['state', p.status === 1 ? 'on' : '']">{{ p.status === 1 ? '已上架' : '已下架' }}</text>
          </view>
        </view>

        <view class="ops">
          <text class="op-btn" @click="openSku(p)">规格</text>
          <text class="op-btn" @click="edit(p)">编辑</text>
          <text class="op-btn" @click="toggle(p)">{{ p.status === 1 ? '下架' : '上架' }}</text>
        </view>
      </view>
      <view v-if="!products.length" class="empty">还没有菜品，点右上角「+ 新增」</view>
    </view>

    <!-- 规格弹层 -->
    <view v-if="skuPanel" class="mask" @click="closeSku">
      <view class="sheet" @click.stop>
        <view class="between sheet-head">
          <text class="sheet-title">规格 · {{ skuProduct?.name }}</text>
          <text class="muted" @click="closeSku">关闭</text>
        </view>

        <scroll-view class="sku-scroll" scroll-y>
          <view v-for="s in skus" :key="s.id" class="sku-row">
            <view>
              <view class="sku-name">{{ s.name }}</view>
              <view class="muted">{{ yuan(s.price) }} · 库存 {{ stockText(s.stock) }}</view>
            </view>
            <view class="sku-ops">
              <text class="op-btn" @click="editSku(s)">编辑</text>
              <text class="op-btn danger" @click="removeSku(s)">删除</text>
            </view>
          </view>
          <view v-if="!skus.length" class="muted empty-sku">
            还没有规格。添加 1 条以上后商品会自动变成「多规格」，展示价取最低规格价。
          </view>

          <view class="sku-form">
            <view class="field">
              <text>{{ skuEditingId ? '编辑规格' : '新增规格' }}</text>
              <text class="muted">{{ skuEditingId ? '修改中' : '' }}</text>
            </view>
            <view class="field">
              <text>名称 <text class="req">*</text></text>
              <input v-model="skuForm.name" placeholder="小份 / 大份" />
            </view>
            <view class="field">
              <text>价格（元）<text class="req">*</text></text>
              <input v-model="skuForm.priceYuan" type="digit" placeholder="15.00" />
            </view>
            <view class="field">
              <text>库存</text>
              <input v-model="skuForm.stock" type="number" placeholder="-1 表示不限" />
            </view>
            <view class="btn" @click="saveSku">{{ skuEditingId ? '保存规格' : '添加规格' }}</view>
            <view v-if="skuEditingId" class="cancel-edit" @click="resetSkuForm">取消编辑</view>
          </view>
        </scroll-view>
      </view>
    </view>

    <AppTabBar :role="'MERCHANT'" />
  </view>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import {
  api,
  ensureMerchantApproved,
  merchantApi,
  uploadImage,
  type Category,
  type ComboItem,
  type MerchantProduct as Product,
  type ProductSku
} from '../../api'
import { guard } from '../../utils/auth'
import AppTabBar from '../../components/AppTabBar.vue'

const products = ref<Product[]>([])
const categories = ref<Category[]>([])
const formVisible = ref(false)
const editingId = ref(0)
const uploading = ref(false)

const form = reactive({
  name: '',
  priceYuan: '',
  originPriceYuan: '',
  stock: '',
  description: '',
  image: '',
  status: 1,
  categoryId: 0,
  isCombo: false,
  comboItems: [] as ComboItem[]
})

const skuPanel = ref(false)
const skuProduct = ref<Product | null>(null)
const skus = ref<ProductSku[]>([])
const skuEditingId = ref(0)
const skuForm = reactive({ name: '', priceYuan: '', stock: '' })

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

function yuan(fen?: number | null) {
  return (Number(fen || 0) / 100).toFixed(2) + ' 元'
}

function stockText(stock?: number | null) {
  if (typeof stock !== 'number') return '—'
  return stock < 0 ? '不限' : String(stock)
}

/** 商家列表接口返回的 comboItems 是 JSON 字符串 */
function parseCombo(p: Product): ComboItem[] {
  const raw: any = (p as any).comboItems
  if (!raw) return []
  if (Array.isArray(raw)) return raw as ComboItem[]
  try {
    const arr = JSON.parse(String(raw))
    return Array.isArray(arr) ? (arr as ComboItem[]) : []
  } catch (e) {
    return []
  }
}

function isComboRow(p: Product) {
  return parseCombo(p).length > 0
}

function showOrigin(p: Product) {
  return typeof p.originPrice === 'number' && p.originPrice > 0 && p.originPrice > p.price
}

const categoryNames = computed(() => categories.value.map((c) => c.name))
const categoryText = computed(() => categories.value.find((c) => c.id === form.categoryId)?.name || '默认分类')
const categoryIndex = computed(() => {
  const i = categories.value.findIndex((c) => c.id === form.categoryId)
  return i < 0 ? 0 : i
})

/** 套餐组成候选：本店非套餐商品、未加入的 */
const comboCandidates = computed(() =>
  products.value.filter(
    (p) => p.id !== editingId.value && !isComboRow(p) && !form.comboItems.some((c) => c.productId === p.id)
  )
)
const comboNames = computed(() => comboCandidates.value.map((p) => p.name))

async function load() {
  products.value = await merchantApi.products()
}

async function loadCategories() {
  if (categories.value.length) return
  try {
    categories.value = await api.categories()
  } catch (e) {
    categories.value = []
  }
}

function closeForm() {
  formVisible.value = false
}

function toggleForm() {
  if (formVisible.value) closeForm()
  else openCreate()
}

function openCreate() {
  editingId.value = 0
  Object.assign(form, {
    name: '',
    priceYuan: '',
    originPriceYuan: '',
    stock: '',
    description: '',
    image: '',
    status: 1,
    categoryId: categories.value[0]?.id || 0,
    isCombo: false,
    comboItems: []
  })
  formVisible.value = true
}

function edit(p: Product) {
  editingId.value = p.id
  Object.assign(form, {
    name: p.name,
    priceYuan: (Number(p.price || 0) / 100).toFixed(2),
    originPriceYuan: p.originPrice ? (Number(p.originPrice) / 100).toFixed(2) : '',
    stock: typeof p.stock === 'number' ? String(p.stock) : '',
    description: p.description || '',
    image: p.image || '',
    status: p.status === 1 ? 1 : 0,
    categoryId: p.categoryId || categories.value[0]?.id || 0,
    isCombo: parseCombo(p).length > 0,
    comboItems: parseCombo(p).map((c) => ({ productId: c.productId, name: c.name, qty: c.qty }))
  })
  formVisible.value = true
  try {
    uni.pageScrollTo({ scrollTop: 0, duration: 200 })
  } catch (e) {
    // 部分端不支持，忽略
  }
}

function onCategory(e: any) {
  const c = categories.value[Number(e?.detail?.value || 0)]
  if (c) form.categoryId = c.id
}

function onStatus(e: any) {
  form.status = e?.detail?.value ? 1 : 0
}

function onCombo(e: any) {
  form.isCombo = !!e?.detail?.value
  if (!form.isCombo) form.comboItems = []
}

function previewImage(url?: string) {
  if (!url) return
  uni.previewImage({ urls: [url], current: url })
}

function uploadProductImage() {
  if (uploading.value) {
    toast('图片上传中，请稍候')
    return
  }
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    success: async (r: any) => {
      const path = r?.tempFilePaths?.[0]
      if (!path) return
      uploading.value = true
      try {
        const res = await uploadImage(path)
        form.image = res.url
      } catch (e: any) {
        toast(String(e?.message || '上传失败，请重试'))
      } finally {
        uploading.value = false
      }
    }
  })
}

function addCombo(e: any) {
  const p = comboCandidates.value[Number(e?.detail?.value || 0)]
  if (!p) return
  form.comboItems.push({ productId: p.id, name: p.name, qty: 1 })
}

function changeComboQty(i: number, delta: number) {
  const c = form.comboItems[i]
  if (!c) return
  c.qty = Math.max(1, c.qty + delta)
}

function removeCombo(i: number) {
  form.comboItems.splice(i, 1)
}

async function save() {
  if (uploading.value) {
    toast('图片还在上传中')
    return
  }
  const name = form.name.trim()
  if (!name) {
    toast('请填写菜品名称')
    return
  }
  const price = Math.round(Number(form.priceYuan) * 100)
  if (!Number.isFinite(price) || price <= 0) {
    toast('请填写正确的价格')
    return
  }
  if (form.isCombo && !form.comboItems.length) {
    toast('套餐至少要有一个组成')
    return
  }

  const payload: any = {
    name,
    price,
    // 0 表示取消划线价（后端 update 只忽略 null，0 会正常写入）
    originPrice: form.originPriceYuan ? Math.round(Number(form.originPriceYuan) * 100) : 0,
    stock: form.stock === '' ? -1 : Number(form.stock),
    description: form.description.trim(),
    image: form.image,
    categoryId: form.categoryId || undefined,
    status: form.status,
    // 非套餐传 '[]'：空串会被后端 normalize 成 null，而 MyBatis-Plus updateById 跳过 null，清不掉
    comboItems: form.isCombo ? JSON.stringify(form.comboItems) : '[]'
  }

  try {
    if (editingId.value) await merchantApi.updateProduct(editingId.value, payload)
    else await merchantApi.createProduct(payload)
    uni.showToast({ title: '已保存' })
    formVisible.value = false
    load()
  } catch (e) {
    console.warn(e)
  }
}

/** 列表 switch 与「上架/下架」按钮共用：按当前状态取反，失败也刷新回真实状态 */
async function toggle(p: Product) {
  try {
    await merchantApi.updateProduct(p.id, { status: p.status === 1 ? 0 : 1 })
  } catch (err) {
    console.warn(err)
  }
  load()
}

// ---------------- 规格 ----------------
async function loadSkus(productId: number) {
  try {
    skus.value = (await merchantApi.skus(productId)) || []
  } catch (e) {
    skus.value = []
  }
}

function resetSkuForm() {
  skuEditingId.value = 0
  skuForm.name = ''
  skuForm.priceYuan = ''
  skuForm.stock = ''
}

async function openSku(p: Product) {
  skuProduct.value = p
  skuPanel.value = true
  skus.value = []
  resetSkuForm()
  await loadSkus(p.id)
}

function closeSku() {
  skuPanel.value = false
  skuProduct.value = null
}

function editSku(s: ProductSku) {
  skuEditingId.value = s.id
  skuForm.name = s.name
  skuForm.priceYuan = (Number(s.price || 0) / 100).toFixed(2)
  skuForm.stock = typeof s.stock === 'number' ? String(s.stock) : ''
}

async function saveSku() {
  const p = skuProduct.value
  if (!p) return
  const name = skuForm.name.trim()
  if (!name) {
    toast('请填写规格名称')
    return
  }
  const price = Math.round(Number(skuForm.priceYuan) * 100)
  if (!Number.isFinite(price) || price <= 0) {
    toast('请填写正确的规格价格')
    return
  }
  const stock = skuForm.stock === '' ? -1 : Number(skuForm.stock)
  try {
    if (skuEditingId.value) await merchantApi.updateSku(skuEditingId.value, { name, price, stock })
    else await merchantApi.createSku(p.id, { name, price, stock })
    uni.showToast({ title: '已保存' })
    resetSkuForm()
    await loadSkus(p.id)
    // 新增/删除规格会刷新商品展示价与 hasSku，列表跟着刷新
    load()
  } catch (e) {
    console.warn(e)
  }
}

async function removeSku(s: ProductSku) {
  const ok = await new Promise<boolean>((resolve) => {
    uni.showModal({
      title: '删除规格',
      content: `确定删除「${s.name}」？删完最后一条后商品会恢复为单规格。`,
      success: (r) => resolve(!!r.confirm)
    })
  })
  if (!ok) return
  const p = skuProduct.value
  try {
    await merchantApi.deleteSku(s.id)
    uni.showToast({ title: '已删除', icon: 'none' })
    if (p) await loadSkus(p.id)
    load()
  } catch (e) {
    console.warn(e)
  }
}

onShow(async () => {
  if (!guard('MERCHANT')) return
  if (!(await ensureMerchantApproved())) return
  await loadCategories()
  load()
})
</script>

<style scoped>
.page { padding: 20rpx; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.title { font-size: 30rpx; font-weight: 700; }
.add { color: #c2410c; font-size: 26rpx; }
.form-title { font-size: 28rpx; font-weight: 600; }
.muted { color: #7a828c; font-size: 24rpx; }
.req { color: #dc2626; }
.mt8 { margin-top: 10rpx; }

.field {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 26rpx;
}
.field input { flex: 1; text-align: right; margin-left: 20rpx; font-size: 26rpx; }
.picker-value { color: #4b5563; }

.img-wrap { display: flex; align-items: center; }
.img-box { position: relative; width: 120rpx; height: 120rpx; }
.img { width: 120rpx; height: 120rpx; border-radius: 12rpx; background: #f3f4f6; }
.img-del {
  position: absolute; right: -8rpx; top: -8rpx; width: 36rpx; height: 36rpx;
  border-radius: 50%; background: rgba(0, 0, 0, 0.55); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 24rpx;
}
.img-add {
  width: 120rpx; height: 120rpx; border-radius: 12rpx; background: #f7f8fa;
  border: 2rpx dashed #d9dee5; display: flex; align-items: center; justify-content: center;
  color: #9aa1aa; font-size: 44rpx;
}

.combo-editor { margin-top: 10rpx; background: #fafafa; border-radius: 14rpx; padding: 16rpx; }
.combo-row { display: flex; align-items: center; justify-content: space-between; padding: 10rpx 0; }
.combo-name { font-size: 26rpx; flex: 1; }
.qty { display: flex; align-items: center; margin-right: 16rpx; }
.qty .op {
  width: 40rpx; height: 40rpx; border-radius: 50%; background: #eee;
  display: flex; align-items: center; justify-content: center;
}
.qty .num { width: 52rpx; text-align: center; font-size: 26rpx; }
.combo-del { color: #b91c1c; font-size: 22rpx; }
.combo-add { color: #c2410c; font-size: 24rpx; padding: 12rpx 0; }

.btn {
  margin-top: 24rpx; background: #c2410c; color: #fff; text-align: center;
  padding: 20rpx; border-radius: 40rpx; font-size: 28rpx;
}

.item .row { display: flex; }
.thumb {
  width: 120rpx; height: 120rpx; border-radius: 14rpx; background: #f3f4f6;
  display: flex; align-items: center; justify-content: center;
  margin-right: 20rpx; overflow: hidden; flex-shrink: 0;
}
.thumb-img { width: 120rpx; height: 120rpx; }
.thumb-text { color: #9ca3af; font-size: 34rpx; }
.info { flex: 1; min-width: 0; }
.name-row { display: flex; align-items: center; gap: 10rpx; }
.name { font-weight: 600; font-size: 28rpx; }
.tag {
  font-size: 20rpx; color: #c2410c; background: #fdf2ea;
  border-radius: 16rpx; padding: 2rpx 10rpx;
}
.tag.combo { color: #1d4ed8; background: #e0ecff; }
.price-row { margin-top: 8rpx; display: flex; align-items: baseline; gap: 10rpx; }
.price { color: #c2410c; font-weight: 600; font-size: 28rpx; }
.origin { color: #9ca3af; font-size: 22rpx; text-decoration: line-through; }
.right { display: flex; flex-direction: column; align-items: flex-end; gap: 6rpx; margin-left: 12rpx; }
.state { font-size: 22rpx; color: #9ca3af; }
.state.on { color: #047857; }

.ops {
  display: flex; justify-content: flex-end; gap: 14rpx;
  margin-top: 16rpx; padding-top: 16rpx; border-top: 1rpx solid #f7f8fa;
}
.op-btn {
  font-size: 24rpx; color: #4b5563; background: #f7f8fa;
  border-radius: 26rpx; padding: 8rpx 22rpx;
}
.op-btn.danger { color: #b91c1c; background: #fef2f2; }

.empty { text-align: center; color: #9ca3af; padding: 80rpx 0; }

/* 规格弹层 */
.mask {
  position: fixed; left: 0; right: 0; top: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.45); z-index: 90;
  display: flex; align-items: flex-end;
}
.sheet {
  width: 100%; background: #fff; border-radius: 28rpx 28rpx 0 0;
  padding: 24rpx 24rpx 0; max-height: 82vh; display: flex; flex-direction: column;
}
.sheet-head { padding-bottom: 16rpx; }
.sheet-title { font-size: 28rpx; font-weight: 600; }
.sku-scroll { flex: 1; max-height: 68vh; }
.sku-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18rpx 0; border-bottom: 1rpx solid #f7f8fa;
}
.sku-name { font-size: 28rpx; font-weight: 600; }
.sku-ops { display: flex; gap: 12rpx; }
.sku-form { margin-top: 10rpx; padding-bottom: calc(24rpx + env(safe-area-inset-bottom)); }
.cancel-edit { text-align: center; color: #7a828c; font-size: 24rpx; padding: 14rpx 0; }
.empty-sku { text-align: center; padding: 30rpx 0; font-size: 24rpx; }
</style>
