<template>
  <view class="page">
    <!-- 提交反馈：POST /api/feedback -->
    <view class="card">
      <view class="sec">反馈类型</view>
      <view class="types">
        <view
          v-for="t in TYPES"
          :key="t.value"
          :class="['type', form.type === t.value ? 'on' : '']"
          @click="form.type = t.value"
        >{{ t.label }}</view>
      </view>
    </view>

    <view class="card">
      <view class="between">
        <text class="sec">反馈内容 <text class="req">*</text></text>
        <text class="muted">{{ form.content.length }}/{{ MAX_CONTENT }}</text>
      </view>
      <textarea
        v-model="form.content"
        class="ta mt"
        :maxlength="MAX_CONTENT"
        placeholder="请描述你的意见、投诉或遇到的问题，尽量写清楚时间地点"
      />
    </view>

    <view class="card">
      <view class="between">
        <text class="sec">图片（选填）</text>
        <text class="muted">{{ images.length }}/{{ MAX_IMAGES }}</text>
      </view>
      <view class="imgs mt">
        <view v-for="(img, i) in images" :key="i" class="img-item">
          <image :src="img" mode="aspectFill" class="img" @click="previewImg(i)" />
          <view class="del" @click="removeImg(i)">×</view>
        </view>
        <view v-if="images.length < MAX_IMAGES" class="img-add" @click="chooseImage">+</view>
      </view>
      <view class="muted mt8">图片会先上传到服务器，提交时只传图片地址；最多 {{ MAX_IMAGES }} 张</view>
    </view>

    <view class="card">
      <view class="field">
        <text>联系方式</text>
        <input v-model="form.contact" placeholder="手机号 / 微信，选填" />
      </view>
      <view class="muted mt8">方便平台或商家联系你核实情况</view>
    </view>

    <view :class="['submit', submitting ? 'off' : '']" @click="submit">
      {{ submitting ? '提交中…' : '提交反馈' }}
    </view>

    <!-- 我的反馈：GET /api/feedback/mine -->
    <view class="between list-head">
      <text class="sec">我的反馈</text>
      <text class="muted">{{ list.length }} 条</text>
    </view>

    <view v-for="f in list" :key="f.id" class="card fb">
      <view class="between">
        <view class="fb-head">
          <text class="fb-type">{{ f.typeText }}</text>
          <text :class="['fb-status', statusClass(f.status)]">{{ f.statusText }}</text>
        </view>
        <text class="muted">{{ timeText(f.createdAt) }}</text>
      </view>

      <view class="fb-content">{{ f.content }}</view>

      <view v-if="imageList(f).length" class="fb-imgs">
        <image
          v-for="(u, i) in imageList(f)"
          :key="i"
          :src="u"
          mode="aspectFill"
          class="fb-img"
          @click="previewList(imageList(f), i)"
        />
      </view>

      <view v-if="f.reply" class="fb-reply">
        <text class="fb-reply-label">回复</text>
        <text class="fb-reply-text">{{ f.reply }}</text>
        <view v-if="timeText(f.handledAt)" class="muted">处理时间 {{ timeText(f.handledAt) }}</view>
      </view>
    </view>

    <view v-if="loading" class="empty">加载中…</view>
    <view v-else-if="!list.length" class="empty">还没有提交过反馈</view>
  </view>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { feedbackApi, parseFeedbackImages, uploadImage, type Feedback } from '../../api'
import { guard } from '../../utils/auth'

const TYPES = [
  { value: 1, label: '意见' },
  { value: 2, label: '投诉' },
  { value: 3, label: '支付问题' },
  { value: 4, label: '其他' }
]
/** 后端 content 上限 1000 字 */
const MAX_CONTENT = 1000
/** 后端 images 最多接受 9 张，产品要求最多 3 张 */
const MAX_IMAGES = 3

const form = reactive({ type: 1, content: '', contact: '' })
const images = ref<string[]>([])
const submitting = ref(false)
const loading = ref(false)
const list = ref<Feedback[]>([])

function toast(title: string) {
  uni.showToast({ title, icon: 'none' })
}

/** 后端返回 ISO（2026-10-25T18:00:00），页面显示成 2026-10-25 18:00 */
function timeText(v?: string | null) {
  const m = String(v || '').match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/)
  return m ? `${m[1]}-${m[2]}-${m[3]} ${m[4]}:${m[5]}` : String(v || '')
}

function statusClass(status: number) {
  if (status === 2) return 'done'
  if (status === 1) return 'doing'
  return 'wait'
}

/** 列表里的 images 后端原样返回 JSON 字符串，统一解析成数组 */
function imageList(f: Feedback) {
  return parseFeedbackImages(f.images)
}

function chooseImage() {
  uni.chooseImage({
    count: Math.max(1, MAX_IMAGES - images.value.length),
    sizeType: ['compressed'],
    success: async (r: any) => {
      const paths: string[] = r?.tempFilePaths || []
      if (!paths.length) return
      // 复用统一上传封装：uni.uploadFile → POST /api/upload（field=file，带 Bearer token）
      uni.showLoading({ title: '上传中…', mask: true })
      try {
        for (const path of paths) {
          if (images.value.length >= MAX_IMAGES) break
          const res = await uploadImage(path)
          if (res?.url) images.value = [...images.value, res.url].slice(0, MAX_IMAGES)
        }
      } catch (e: any) {
        toast(String(e?.message || '图片上传失败，请重试'))
      } finally {
        uni.hideLoading()
      }
    }
  })
}

function previewImg(i: number) {
  uni.previewImage({ urls: images.value, current: images.value[i] })
}

function previewList(urls: string[], i: number) {
  uni.previewImage({ urls, current: urls[i] })
}

function removeImg(i: number) {
  images.value = images.value.filter((_, idx) => idx !== i)
}

async function loadMine() {
  loading.value = true
  try {
    list.value = (await feedbackApi.mine()) || []
  } catch (e) {
    // 列表加载失败不影响提交（接口层已静默）
    list.value = []
  } finally {
    loading.value = false
  }
}

async function submit() {
  if (submitting.value) return
  const content = form.content.trim()
  if (!content) {
    toast('请填写反馈内容')
    return
  }
  if (content.length > MAX_CONTENT) {
    toast(`反馈内容不能超过 ${MAX_CONTENT} 字`)
    return
  }
  submitting.value = true
  try {
    await feedbackApi.submit({
      type: form.type,
      content,
      images: images.value,
      contact: form.contact.trim() || undefined
    })
    uni.showToast({ title: '反馈已提交，感谢你的建议' })
    // 提交成功后清空表单并刷新「我的反馈」
    form.content = ''
    form.contact = ''
    images.value = []
    form.type = 1
    loadMine()
  } catch (e) {
    // request 层已 toast 后端文案（如「反馈内容不能超过 1000 字」）
    console.warn(e)
  } finally {
    submitting.value = false
  }
}

onShow(() => {
  if (!guard('USER')) return
  loadMine()
})
</script>

<style scoped>
.page { padding: 20rpx; min-height: 100vh; background: #f5f6f8; }
.card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 20rpx; }
.between { display: flex; align-items: center; justify-content: space-between; }
.sec { font-size: 28rpx; font-weight: 600; }
.req { color: #dc2626; }
.muted { color: #7a828c; font-size: 24rpx; }
.mt { margin-top: 16rpx; }
.mt8 { margin-top: 8rpx; }

.types { display: flex; gap: 12rpx; margin-top: 18rpx; }
.type {
  flex: 1; text-align: center; padding: 18rpx 0; border-radius: 14rpx;
  background: #f7f8fa; color: #4b5563; font-size: 26rpx;
  border: 2rpx solid transparent;
}
.type.on { background: #fdf2ea; color: #c2410c; border-color: #c2410c; font-weight: 600; }

.ta {
  width: 100%; box-sizing: border-box; min-height: 220rpx;
  font-size: 26rpx; line-height: 1.6;
}

.imgs { display: flex; flex-wrap: wrap; gap: 16rpx; }
.img-item { position: relative; width: 160rpx; height: 160rpx; }
.img { width: 160rpx; height: 160rpx; border-radius: 14rpx; background: #f3f4f6; }
.del {
  position: absolute; right: -8rpx; top: -8rpx;
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: rgba(0, 0, 0, 0.55); color: #fff;
  display: flex; align-items: center; justify-content: center; font-size: 26rpx;
}
.img-add {
  width: 160rpx; height: 160rpx; border-radius: 14rpx;
  background: #f7f8fa; color: #9aa1aa; font-size: 52rpx;
  display: flex; align-items: center; justify-content: center;
}

.field {
  display: flex; align-items: center; justify-content: space-between;
  padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; font-size: 26rpx;
}
.field input { flex: 1; text-align: right; margin-left: 20rpx; font-size: 26rpx; }

.submit {
  margin: 10rpx 10rpx 30rpx;
  background: #c2410c; color: #fff; text-align: center;
  padding: 24rpx; border-radius: 44rpx; font-size: 30rpx;
}
.submit.off { background: #e5e7eb; color: #9aa1aa; }

.list-head { padding: 10rpx 6rpx 16rpx; }
.fb { padding: 22rpx 24rpx; }
.fb-head { display: flex; align-items: center; gap: 12rpx; }
.fb-type { font-size: 26rpx; font-weight: 600; color: #1a1d21; }
.fb-status { font-size: 20rpx; border-radius: 16rpx; padding: 2rpx 12rpx; }
.fb-status.wait { color: #b45309; background: #fef3c7; }
.fb-status.doing { color: #1d4ed8; background: #e0ecff; }
.fb-status.done { color: #047857; background: #dcfce7; }
.fb-content {
  margin-top: 12rpx; font-size: 26rpx; color: #374151; line-height: 1.6;
  word-break: break-all;
}
.fb-imgs { display: flex; flex-wrap: wrap; gap: 12rpx; margin-top: 14rpx; }
.fb-img { width: 130rpx; height: 130rpx; border-radius: 12rpx; background: #f3f4f6; }
.fb-reply {
  margin-top: 16rpx; padding: 14rpx 18rpx;
  background: #f7f8fa; border-radius: 12rpx;
}
.fb-reply-label {
  font-size: 22rpx; color: #c2410c; margin-right: 12rpx;
}
.fb-reply-text { font-size: 24rpx; color: #374151; line-height: 1.6; }
.empty { text-align: center; color: #9ca3af; padding: 60rpx 0; font-size: 26rpx; }
</style>
