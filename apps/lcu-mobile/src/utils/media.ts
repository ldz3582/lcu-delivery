/**
 * 媒体地址归一化。
 *
 * 历史数据里 /uploads/... 存的是绝对地址（如 http://127.0.0.1:8082/uploads/2026/10/x.jpg），
 * 后端换端口就 404。这里统一还原成「当前 API 基址 + /uploads/路径」：
 *  - H5：BASE_URL 为空时保持相对路径，由 serve.mjs 把 /uploads 代理到后端，端口无关；
 *  - 小程序：BASE_URL 是构建时注入的绝对地址，拼出的地址可直接加载。
 */

import { BASE_URL } from './config'

const UPLOAD_ABS_RE = /^https?:\/\/[^/]+(\/uploads\/.+)$/i

export function toMediaUrl(url: string): string {
  if (typeof url !== 'string' || !url) return url
  const m = UPLOAD_ABS_RE.exec(url)
  if (!m) return url
  return BASE_URL ? BASE_URL + m[1] : m[1]
}

/** 递归处理接口返回数据里的字符串字段（数组 / 对象 / 基本类型） */
export function normalizeMedia<T>(data: T): T {
  if (typeof data === 'string') return toMediaUrl(data) as unknown as T
  if (Array.isArray(data)) return data.map((v) => normalizeMedia(v)) as unknown as T
  if (data && typeof data === 'object') {
    const out: Record<string, unknown> = {}
    Object.keys(data as Record<string, unknown>).forEach((k) => {
      out[k] = normalizeMedia((data as Record<string, unknown>)[k])
    })
    return out as T
  }
  return data
}
