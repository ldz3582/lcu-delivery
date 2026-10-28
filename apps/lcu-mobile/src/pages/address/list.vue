<template>
  <view class="page">
    <view v-for="a in list" :key="a.id" class="card" @click="pickAddress(a)">
      <view class="between">
        <text class="detail">{{ a.detail }}</text>
        <text v-if="a.isDefault === 1" class="tag">默认</text>
      </view>
      <view class="muted mt8">
        {{ a.campus === 1 ? '东校区' : a.campus === 2 ? '西校区' : '未选校区' }}
        <text v-if="a.building"> · {{ a.building }}</text>
        <text v-if="a.floor"> · {{ a.floor }} 层</text>
        <text v-if="a.room"> · {{ a.room }}</text>
      </view>
      <view v-if="a.inFence !== 1" class="warn">超出校园配送范围</view>
      <view class="ops">
        <text class="op" @click.stop="edit(a.id)">编辑</text>
        <text class="op" @click.stop="setDefault(a)">设为默认</text>
        <text class="op danger" @click.stop="remove(a.id)">删除</text>
      </view>
    </view>

    <view v-if="!list.length" class="empty">还没有收货地址</view>
    <view class="add" @click="add">+ 新增收货地址</view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { api, type Address } from '../../api'
import { guard } from '../../utils/auth'

const list = ref<Address[]>([])
const picking = ref(false)

async function load() {
  list.value = await api.addresses()
}

function pickAddress(a: Address) {
  if (picking.value) {
    uni.setStorageSync('lcu_pick_address', a)
    uni.navigateBack()
  }
}

function add() {
  uni.navigateTo({ url: `/pages/address/edit${picking.value ? '?pick=1' : ''}` })
}
function edit(id: number) {
  uni.navigateTo({ url: `/pages/address/edit?id=${id}${picking.value ? '&pick=1' : ''}` })
}
async function setDefault(a: Address) {
  await api.updateAddress(a.id, { ...a, isDefault: 1 })
  load()
}
async function remove(id: number) {
  const r = await new Promise<boolean>((resolve) => {
    uni.showModal({ title: '删除地址', content: '确认删除？', success: (x) => resolve(x.confirm) })
  })
  if (r) {
    await api.deleteAddress(id)
    load()
  }
}

onLoad((q: any) => {
  picking.value = q?.pick === '1'
})
onShow(async () => {
  if (!guard('USER')) return
  await load()
})
</script>

<style scoped>
.page { padding: 20rpx; }
.detail { font-weight: 600; font-size: 30rpx; }
.tag { background: #fdf2ea; color: #c2410c; font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 16rpx; }
.ops { display: flex; gap: 30rpx; margin-top: 18rpx; }
.op { color: #4b5563; font-size: 24rpx; }
.danger { color: #b91c1c; }
.warn { color: #b45309; font-size: 24rpx; margin-top: 8rpx; }
.add {
  margin: 30rpx 6rpx; text-align: center; padding: 24rpx;
  border: 2rpx dashed #d1d5db; border-radius: 20rpx; color: #c2410c;
}
.empty { text-align: center; color: #9ca3af; padding: 90rpx 0; }
.mt8 { margin-top: 8rpx; }
</style>
