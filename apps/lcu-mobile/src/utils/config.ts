/**
 * 接口基址。
 *
 * VITE_API_BASE 不配置时走「同源相对路径」：H5 由 serve.mjs 把 /api、/uploads 代理到后端，
 * 端口怎么变都不用重新打包；小程序（mp-weixin）没有代理，构建时必须显式传
 * VITE_API_BASE=http://<host>:<port>。
 */
const env = (import.meta as any).env || {}

export const BASE_URL: string = env.VITE_API_BASE || ''

export default BASE_URL
