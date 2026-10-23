<template>
  <view class="page">
    <view class="card">
      <view class="between">
        <text class="title">菜品管理</text>
        <text class="add" @click="showForm = !showForm">{{ showForm ? '收起' : '+ 新增' }}</text>
      </view>

      <view v-if="showForm" class="form">
        <view class="field"><text>名称</text><input v-model="form.name" placeholder="黄焖鸡米饭" /></view>
        <view class="field"><text>价格（元）</text><input v-model="form.priceYuan" type="digit" placeholder="15.00" /></view>
        <view class="field"><text>库存</text><input v-model="form.stock" type="number" placeholder="-1 表示不限" /></view>
        <view class="field"><text>描述</text><input v-model="form.description" placeholder="选填" /></view>
        <view class="btn" @click="save">保存菜品</view>
      </view>
    </view>

    <view class="list">
      <view v-for="p in products" :key="p.id" class="card item">
        <view class="between">
          <text class="name">{{ p.name }}</text>
          <text class="price">{{ (p.price / 100).toFixed(2) }} 元</text>
        </view>
        <view class="between mt8">
          <text class="muted">库存 {{ p.stock < 0 ? '不限' : p.stock }}</text>
          <text :class="['state', p.status === 1 ? 'on' : '']" @click="toggle(p)">{{ p.status === 1 ? '已上架' : '已下架' }}</text>
        </view>
      </view>
      <view v-if="!products.length" class="empty">还没有菜品</view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { merchantApi, type Product } from '../../api'

const products = ref<Product[]>([])
const showForm = ref(false)
const form = reactive({ name: '', priceYuan: '', stock: '', description: '' })

async function load() {
  products.value = await merchantApi.products()
}

async function save() {
  if (!form.name || !form.priceYuan) {
    uni.showToast({ title: '名称和价格必填', icon: 'none' })
    return
  }
  await merchantApi.createProduct({
    name: form.name,
    price: Math.round(Number(form.priceYuan) * 100),
    stock: form.stock === '' ? -1 : Number(form.stock),
    description: form.description,
    status: 1
  })
  form.name = ''
  form.priceYuan = ''
  form.stock = ''
  form.description = ''
  showForm.value = false
  load()
}

async function toggle(p: Product) {
  await merchantApi.updateProduct(p.id, { status: p.status === 1 ? 0 : 1 })
  load()
}

onShow(load)
</script>

<style scoped>
.page { padding: 20rpx; }
.title { font-size: 30rpx; font-weight: 700; }
.add { color: #c2410c; font-size: 26rpx; }
.form { margin-top: 20rpx; }
.field { display: flex; justify-content: space-between; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 26rpx; }
.field input { flex: 1; text-align: right; }
.name { font-weight: 600; }
.mt8 { margin-top: 10rpx; }
.state { font-size: 24rpx; color: #9ca3af; }
.state.on { color: #047857; }
.empty { text-align: center; color: #9ca3af; padding: 80rpx 0; }
</style>
