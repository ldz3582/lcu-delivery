/** 金额单位为「分」，转成两位小数的元 */
export function formatMoney(fen: number | null | undefined): string {
  if (fen === null || fen === undefined || Number.isNaN(Number(fen))) return '0.00'
  return (Number(fen) / 100).toFixed(2)
}

/** 后端时间形如 2026-10-23T09:28:36 → 2026-10-23 09:28:36 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 19)
}

/** 校区 */
export const CAMPUS_TEXT: Record<number, string> = {
  1: '东校区',
  2: '西校区',
  3: '两校区',
}

export function campusText(campus: number | null | undefined): string {
  if (campus === null || campus === undefined) return '未选择'
  return CAMPUS_TEXT[campus] ?? `校区${campus}`
}

export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

interface StatusMeta {
  text: string
  type: TagType
}

/** 商家 / 骑手审核状态：0待审 1通过 2驳回 */
export const AUDIT_STATUS: Record<number, StatusMeta> = {
  0: { text: '待审核', type: 'warning' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已驳回', type: 'danger' },
  3: { text: '已冻结', type: 'info' },
}

export function auditMeta(status: number | null | undefined): StatusMeta {
  return AUDIT_STATUS[Number(status)] ?? { text: '未知', type: 'info' }
}

/** 订单状态 */
export const ORDER_STATUS: Record<number, StatusMeta> = {
  0: { text: '待支付', type: 'info' },
  10: { text: '待接单', type: 'warning' },
  20: { text: '待出餐', type: 'warning' },
  30: { text: '待取餐', type: 'primary' },
  40: { text: '配送中', type: 'primary' },
  50: { text: '已完成', type: 'success' },
  60: { text: '已取消', type: 'info' },
  70: { text: '售后中', type: 'danger' },
  80: { text: '已退款', type: 'danger' },
}

export const ORDER_STATUS_OPTIONS = Object.entries(ORDER_STATUS).map(([value, meta]) => ({
  value: Number(value),
  label: meta.text,
}))

/** 结算单状态：0待结算 1已结算 2已撤销 */
export const SETTLE_STATUS: Record<number, StatusMeta> = {
  0: { text: '待结算', type: 'warning' },
  1: { text: '已结算', type: 'success' },
  2: { text: '已撤销', type: 'info' },
}

/** 佣金比例展示：0.06 → 6.00% */
export function formatRate(rate: number | null | undefined): string {
  if (rate === null || rate === undefined) return '-'
  const n = Number(rate)
  if (Number.isNaN(n)) return '-'
  return `${(n <= 1 ? n * 100 : n).toFixed(2)}%`
}
