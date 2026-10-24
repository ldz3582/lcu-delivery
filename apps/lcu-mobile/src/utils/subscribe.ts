// 小程序订阅消息授权。
// 关键约束：只有后台真的配好模板 ID（configured=true）才申请授权；
// 本地/模拟环境 configured=false 时静默跳过，不弹授权框、不报错、不阻塞业务。
import { notifyApi, type NotifyTemplate } from '../api'

const CACHE_TTL = 24 * 60 * 60 * 1000

/** 模板清单缓存（避免每次下单都请求一次） */
let templatesCache: { at: number; list: NotifyTemplate[] } | null = null
/** 已申请过的模板：接受或拒绝都记 24h，避免反复弹窗 */
const askedAt = new Map<string, number>()

async function loadTemplates(): Promise<NotifyTemplate[]> {
  const now = Date.now()
  if (templatesCache && now - templatesCache.at < CACHE_TTL) return templatesCache.list
  try {
    const list = await notifyApi.templates()
    templatesCache = { at: now, list: list || [] }
  } catch (e) {
    // 接口异常一律当作「没有可申请的模板」，不给用户弹错误
    templatesCache = { at: now, list: [] }
  }
  return templatesCache.list
}

/** 是否真的存在可用模板（「我的」页用它决定是弹授权还是提示未配置） */
export async function hasConfiguredTemplate(keys: string[]): Promise<boolean> {
  if (!keys || !keys.length) return false
  const list = await loadTemplates()
  return list.some((t) => !!t && t.configured === true && !!t.templateId && keys.includes(t.key))
}

/**
 * 申请订阅授权。返回 true 表示确实调起了授权（无论用户同意还是拒绝都算发起过）。
 * 任何异常都吞掉，调用方无需 try/catch 也能保证主流程继续。
 */
export async function requestSubscribe(keys: string[]): Promise<boolean> {
  if (!keys || !keys.length) return false

  let ids: string[] = []
  try {
    const list = await loadTemplates()
    const now = Date.now()
    ids = list
      .filter((t) => t && t.configured === true && !!t.templateId && keys.includes(t.key))
      .filter((t) => {
        const last = askedAt.get(t.templateId)
        return !last || now - last >= CACHE_TTL
      })
      .map((t) => t.templateId)
  } catch (e) {
    return false
  }

  if (!ids.length) return false
  // 小程序单次最多 3 个模板
  const tmplIds = ids.slice(0, 3)

  const markAsked = () => tmplIds.forEach((id) => askedAt.set(id, Date.now()))

  return new Promise<boolean>((resolve) => {
    const req = (uni as any).requestSubscribeMessage
    if (typeof req !== 'function') {
      // H5/部分端不支持，静默跳过
      resolve(false)
      return
    }
    try {
      req.call(uni, {
        tmplIds,
        success: (res: any) => {
          markAsked()
          console.log('[subscribe] 授权结果', res)
          resolve(true)
        },
        fail: (err: any) => {
          // 用户点「拒绝」也走这里，只记录，不打扰
          markAsked()
          console.warn('[subscribe] 授权失败（用户拒绝或环境不支持）', err)
          resolve(false)
        }
      })
    } catch (e) {
      console.warn('[subscribe] 调用异常', e)
      resolve(false)
    }
  })
}
