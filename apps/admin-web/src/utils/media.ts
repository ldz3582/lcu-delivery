/**
 * 媒体地址归一化。
 *
 * 历史数据里 /uploads/... 存的是绝对地址（如 http://127.0.0.1:8082/uploads/2026/10/x.jpg），
 * 后端一换端口图片就 404。这里在响应层统一把「任意 host 的 /uploads/ 地址」还原成同源相对路径，
 * 由前端自身的反向代理（serve.mjs / vite proxy）转发到当前后端，端口变化无需改库。
 */

const UPLOAD_ABS_RE = /^https?:\/\/[^/]+(\/uploads\/.+)$/i

export function toLocalMediaUrl(url: string): string {
  if (typeof url !== 'string' || !url) return url
  const m = UPLOAD_ABS_RE.exec(url)
  return m ? m[1] : url
}

/** 递归处理接口返回数据里的字符串字段（数组 / 对象 / 基本类型） */
export function normalizeMedia<T>(data: T): T {
  if (typeof data === 'string') return toLocalMediaUrl(data) as unknown as T
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
