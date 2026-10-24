/**
 * 打开地图导航。
 *
 * 说明：`wx.openLocation` 在真机会打开微信内置地图（可一键唤起腾讯/高德导航）；
 * 但在「微信开发者工具」模拟器里，内置地图页要拉起 qqmap:// 这类本地 App 协议，
 * 浏览器会报 ERR_UNKNOWN_URL_SCHEME、整页变成「无法访问此网站」。
 * 所以模拟器里改为弹窗展示地址与坐标（可复制），避免卡在错误页。
 */
export interface NavTarget {
  latitude: number
  longitude: number
  name: string
  address?: string
}

function isDevtools(): boolean {
  try {
    return uni.getSystemInfoSync().platform === 'devtools'
  } catch (e) {
    return false
  }
}

function showFallback(t: NavTarget, reason: string) {
  const coord = `${t.latitude.toFixed(6)},${t.longitude.toFixed(6)}`
  uni.showModal({
    title: '模拟器不支持地图导航',
    content: `${t.name}${t.address ? '\n' + t.address : ''}\n坐标：${coord}\n\n${reason}`,
    confirmText: '复制坐标',
    cancelText: '关闭',
    success: (r) => {
      if (r.confirm) {
        uni.setClipboardData({ data: coord })
      }
    }
  })
}

export function openNavigation(t: NavTarget) {
  if (!t || !Number.isFinite(t.latitude) || !Number.isFinite(t.longitude)) {
    uni.showToast({ title: '缺少坐标，无法导航', icon: 'none' })
    return
  }
  if (isDevtools()) {
    showFallback(t, '真机上会打开微信内置地图，可直接一键导航。')
    return
  }
  uni.openLocation({
    latitude: t.latitude,
    longitude: t.longitude,
    name: t.name,
    address: t.address,
    scale: 16,
    fail: () => showFallback(t, '未安装地图类 App 时可复制坐标手动搜索。')
  })
}
