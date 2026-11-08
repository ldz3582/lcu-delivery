/**
 * 支付通道选择与调起（跨小程序 / 微信内 H5 / 微信外 H5 / PC 扫描）。
 *
 * 后端 /api/pay/prepay 会根据 scene 返回不同结构：
 *  - MINIAPP / MP：JSAPI 签名参数（timeStamp/nonceStr/package/signType/paySign）
 *  - H5：h5Url（微信 MWEB，浏览器直接跳转）
 *  - NATIVE：codeUrl（二维码内容）
 * mock 模式下统一 mock=true，走 /api/pay/mock-pay。
 */

export type PayScene = 'MINIAPP' | 'MP' | 'H5' | 'NATIVE'

/** 是否在微信内置浏览器里（H5 环境下判断 UA） */
export function isInWechat(): boolean {
  // #ifdef H5
  return /MicroMessenger/i.test(navigator.userAgent)
  // #endif
  // #ifndef H5
  return false
  // #endif
}

/**
 * 当前环境应该用哪个下单通道。
 * 小程序 → MINIAPP；微信内 H5 → MP（公众号 JSAPI）；其余 H5 → H5（MWEB）；
 * 需要二维码时可显式传 NATIVE。
 */
export function pickPayScene(prefer?: PayScene): PayScene {
  if (prefer) return prefer
  // #ifdef MP-WEIXIN
  return 'MINIAPP'
  // #endif
  // #ifdef H5
  return isInWechat() ? 'MP' : 'H5'
  // #endif
  // #ifndef MP-WEIXIN
  // #ifndef H5
  return 'MINIAPP'
  // #endif
  // #endif
}

/** 微信内 H5 调起公众号 JSAPI 支付 */
function invokeMpJsapi(prepay: any): Promise<boolean> {
  return new Promise((resolve) => {
    // #ifdef H5
    const bridge = (window as any).WeixinJSBridge
    const run = () => {
      ;(window as any).WeixinJSBridge.invoke(
        'getBrandWCPayRequest',
        {
          appId: prepay.appId,
          timeStamp: String(prepay.timeStamp),
          nonceStr: prepay.nonceStr,
          package: prepay.package,
          signType: prepay.signType || 'RSA',
          paySign: prepay.paySign
        },
        (res: any) => {
          if (res && res.err_msg === 'get_brand_wcpay_request:ok') {
            resolve(true)
          } else {
            const cancelled = res && /cancel/i.test(String(res.err_msg || ''))
            if (!cancelled) {
              uni.showToast({ title: '支付失败，请重试', icon: 'none' })
            }
            resolve(false)
          }
        }
      )
    }
    if (bridge) {
      run()
    } else if (typeof (document as any).addEventListener === 'function') {
      ;(document as any).addEventListener('WeixinJSBridgeReady', run, false)
    } else {
      resolve(false)
    }
    // #endif
    // #ifndef H5
    resolve(false)
    // #endif
  })
}

/** 小程序原生 JSAPI（uni.requestPayment 内部就是 wx.requestPayment） */
function invokeMiniApp(prepay: any): Promise<boolean> {
  return new Promise((resolve) => {
    uni.requestPayment({
      provider: 'wxpay',
      timeStamp: String(prepay.timeStamp),
      nonceStr: prepay.nonceStr,
      package: prepay.package,
      signType: prepay.signType || 'RSA',
      paySign: prepay.paySign,
      success: () => resolve(true),
      fail: (err: any) => {
        const msg = String(err?.errMsg || '')
        if (/cancel/i.test(msg)) {
          uni.showToast({ title: '已取消支付', icon: 'none' })
        } else {
          uni.showToast({ title: '支付失败，请重试', icon: 'none' })
        }
        resolve(false)
      }
    } as any)
  })
}

/**
 * 按 prepay 返回值调起支付。返回 true 表示用户已支付（仍需后端确认）。
 */
export async function invokePay(prepay: any): Promise<boolean> {
  const channel = String(prepay?.channel || '').toLowerCase()
  if (channel === 'h5') {
    // #ifdef H5
    const url = prepay.h5Url
    if (!url) return false
    // 微信 MWEB 会跳出去唤起收银台，支付完成后按 redirect_url 回到站点
    const back = encodeURIComponent(window.location.href)
    const sep = url.includes('?') ? '&' : '?'
    window.location.href = `${url}${sep}redirect_url=${back}`
    // 页面即将跳走，这里按「已发起」处理
    return true
    // #endif
    // #ifndef H5
    uni.showToast({ title: '请在小程序内完成支付', icon: 'none' })
    return false
    // #endif
  }
  if (channel === 'native') {
    // 扫码支付由页面展示二维码后自行轮询，这里不阻塞
    return false
  }
  if (channel === 'mp') {
    return invokeMpJsapi(prepay)
  }
  return invokeMiniApp(prepay)
}
