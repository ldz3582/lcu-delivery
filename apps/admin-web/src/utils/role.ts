/**
 * 后台角色默认落地页。
 * 非 SUPER 角色登录后不再统一落 /overview，避免落到「菜单里没有概览」的角色上。
 */
export const ROLE_HOME: Record<string, string> = {
  SUPER: '/overview',
  FINANCE: '/settlements',
  AUDITOR: '/shops',
  CS: '/orders',
  OPERATOR: '/overview',
}

export function roleHome(role: string | null | undefined): string {
  const key = String(role || '').toUpperCase()
  return ROLE_HOME[key] ?? '/overview'
}
