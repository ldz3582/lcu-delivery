#!/usr/bin/env bash
# 端到端冒烟测试：下单 → 支付 → 商家接单出餐 → 骑手取送 → 结算
# 用法：
#   ./smoke-test.sh                 # 默认 http://127.0.0.1:8080
#   ADMIN_PASSWORD=xxx ./smoke-test.sh
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8080}"
PY="${PY:-python3}"

jq_get() { "$PY" -c "import sys,json;d=json.load(sys.stdin);print(eval('d'+sys.argv[1]))" "$1"; }
post() { curl -s -X POST "$BASE$1" -H 'Content-Type: application/json' ${2:+-H "Authorization: Bearer $2"} -d "$3"; }
get() { curl -s "$BASE$1" -H "Authorization: Bearer $2"; }

step() { printf '\n\033[1;36m▶ %s\033[0m\n' "$1"; }
ok() { printf '  \033[32m✓\033[0m %s\n' "$1"; }

step "1. 用户登录（mock）"
USER_TOKEN=$(post /api/auth/mock-login "" '{"openid":"mock_user","role":"USER"}' | jq_get "['data']['token']")
ok "user token: ${USER_TOKEN:0:18}..."

step "2. 店铺列表"
SHOPS=$(get "/api/shops?campus=1" "$USER_TOKEN")
echo "$SHOPS" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  店铺数:',d['total']);[print('   -',s['id'],s['name'],s['deliveryFee'],'分配送费') for s in d['list']]"

step "3. 店铺详情"
get "/api/shops/1" "$USER_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  ',d['name'],'| 营业:',d['bizStatus'],'| 商品:',len(d['products']))"

step "4. 算价"
# 用无规格商品（商品 1 已配规格，必须传 skuId）
BILL=$(post /api/cart/calculate "$USER_TOKEN" '{"shopId":1,"items":[{"productId":2,"qty":2}]}')
echo "$BILL" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  商品',d['goodsAmount'],'打包',d['packFee'],'配送',d['deliveryFee'],'合计',d['totalAmount'],'起送达标',d['reachMin'])"
TOTAL=$(echo "$BILL" | jq_get "['data']['totalAmount']")

step "5. 创建订单（地址 1，围栏内）"
ORDER=$(post /api/orders "$USER_TOKEN" '{"shopId":1,"addressId":1,"items":[{"productId":2,"qty":2}],"remark":"少放辣"}')
ORDER_ID=$(echo "$ORDER" | jq_get "['data']['orderId']")
echo "  订单 $ORDER_ID / $(echo "$ORDER" | jq_get "['data']['orderNo']") / 实付 $(echo "$ORDER" | jq_get "['data']['payAmount']") 分"

step "6. 预支付 + 模拟支付"
post /api/pay/prepay "$USER_TOKEN" "{\"orderId\":$ORDER_ID}" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  mock:',d.get('mock'),'| 支付单:',d['outTradeNo'])"
post /api/pay/mock-pay "$USER_TOKEN" "{\"orderId\":$ORDER_ID}" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  已支付:',d['paid'],'| 订单状态:',d['status'])"

step "7. 商家接单 + 出餐"
M_TOKEN=$(post /api/auth/mock-login "" '{"openid":"mock_merchant","role":"MERCHANT"}' | jq_get "['data']['token']")
post "/api/merchant/orders/$ORDER_ID/accept" "$M_TOKEN" '{}' >/dev/null; ok "商家已接单"
post "/api/merchant/orders/$ORDER_ID/ready" "$M_TOKEN" '{}' >/dev/null; ok "商家已出餐"

step "8. 骑手抢单 + 取餐 + 送达"
R_TOKEN=$(post /api/auth/mock-login "" '{"openid":"mock_rider","role":"RIDER"}' | jq_get "['data']['token']")
POOL=$(get "/api/rider/pool?lng=115.9905&lat=36.4385" "$R_TOKEN")
echo "$POOL" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  抢单大厅:',len(d),'单')"
post "/api/rider/orders/$ORDER_ID/grab" "$R_TOKEN" '{}' >/dev/null; ok "骑手已抢单"
post "/api/rider/orders/$ORDER_ID/pickup" "$R_TOKEN" '{"photo":"https://example.com/pickup.jpg"}' >/dev/null; ok "已取餐"
post "/api/rider/location" "$R_TOKEN" '{"lng":115.9902,"lat":36.4381,"accuracy":12}' >/dev/null; ok "已上报位置"
post "/api/rider/orders/$ORDER_ID/deliver" "$R_TOKEN" '{"photo":"https://example.com/deliver.jpg"}' >/dev/null; ok "已送达"

step "9. 用户查看订单"
get "/api/orders/$ORDER_ID" "$USER_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  状态:',d['status'],d['statusText'],'| 商品:',len(d['items']),'项 | 店铺:',d['shopName'])"

step "10. 围栏拦截（校外坐标）"
OUT=$(post /api/addresses "$USER_TOKEN" '{"campus":1,"detail":"测试校外地址","lng":116.5000,"lat":36.9000,"isDefault":0}')
OUT_ID=$(echo "$OUT" | jq_get "['data']['id']")
RESP=$(post /api/orders "$USER_TOKEN" "{\"shopId\":1,\"addressId\":$OUT_ID,\"items\":[{\"productId\":2,\"qty\":1}]}")
echo "$RESP" | "$PY" -c "import sys,json;d=json.load(sys.stdin);print('  返回码:',d['code'],'|',d['msg'])"

step "11. 管理后台"
if [ -n "${ADMIN_PASSWORD:-Admin@123456}" ]; then
  ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123456}"
  A_TOKEN=$(post /api/admin/login "" "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" | jq_get "['data']['token']")
  echo "  管理员 token: ${A_TOKEN:0:18}..."
  get /api/admin/overview "$A_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  今日订单',d['todayOrders'],'| 今日 GMV',d['todayGmv'],'分 | 商家',d['totalShops'],'| 骑手',d['totalRiders'])"
  get /api/admin/settlements "$A_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  结算单:',d['total'],'条')"
  get /api/admin/fence "$A_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  围栏:',len(d),'个',d[0]['name'] if d else '')"
else
  echo "  跳过（未提供 ADMIN_PASSWORD；密码见后端启动日志）"
fi


step "12. 菜品名搜索（issue：搜索框写「搜店铺、菜品」，实际只搜店名）"
SEARCH=$(get "/api/shops?keyword=%E6%8E%92%E9%AA%A8" "$USER_TOKEN")
echo "$SEARCH" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  命中店铺:',d['total']);[print('   -',s['name'],'→',s.get('matchedProducts')) for s in d['list'][:3]]"

step "13. 订单追踪（时间轴 + 骑手 + 位置）"
get "/api/orders/$ORDER_ID/track" "$USER_TOKEN" | "$PY" -c "
import sys,json
d=json.load(sys.stdin)['data']
print('  状态:',d['status'],d['statusText'],'| ETA:',d.get('etaMinutes'),'分钟')
print('  节点:',' → '.join(('✓' if s['done'] else '·')+s['name'] for s in d['steps']))
r=d.get('rider'); loc=d.get('location')
print('  骑手:',(r or {}).get('name'),(r or {}).get('phone'),'| 位置:',(loc or {}).get('lng'),(loc or {}).get('lat'))
"

step "14. 优惠券：可领 → 领取 → 我的券 → 算价抵扣"
COUPONS=$(get "/api/coupons/available?shopId=1" "$USER_TOKEN")
echo "$COUPONS" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  可领:',len(d),[(c['name'],c['desc']) for c in d[:3]])"
COUPON_ID=$(echo "$COUPONS" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print(next((c['id'] for c in d if c['type']==1 and c['threshold']<=2400), 0))")
if [ "$COUPON_ID" != "0" ]; then
  post "/api/coupons/$COUPON_ID/claim" "$USER_TOKEN" '{}' >/dev/null; ok "已领取券 #$COUPON_ID"
  post "/api/coupons/$COUPON_ID/claim" "$USER_TOKEN" '{}' | "$PY" -c "import sys,json;d=json.load(sys.stdin);print('  重复领取被拒:',d['code'],d['msg'])"
  UCID=$(get "/api/coupons/mine?shopId=1&amount=2400" "$USER_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print(next((c['userCouponId'] for c in d if c['usable']), 0))")
  post /api/cart/calculate "$USER_TOKEN" "{\"shopId\":1,\"items\":[{\"productId\":2,\"qty\":2}],\"userCouponId\":$UCID}" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  用券后: 商品',d['goodsAmount'],'优惠',d['discountAmount'],'实付',d['totalAmount'],'|',d.get('couponName'))"
else
  echo "  跳过（没有门槛≤30元的满减券）"
fi

step "15. 订单评价（已完成单）"
post /api/reviews "$USER_TOKEN" "{\"orderId\":$ORDER_ID,\"shopScore\":5,\"riderScore\":5,\"content\":\"冒烟测试：分量足，送得快\"}" | "$PY" -c "import sys,json;d=json.load(sys.stdin);print('  评价结果:',d['code'],d['msg'])"
post /api/reviews "$USER_TOKEN" "{\"orderId\":$ORDER_ID,\"shopScore\":3,\"content\":\"再来一次\"}" | "$PY" -c "import sys,json;d=json.load(sys.stdin);print('  重复评价被拒:',d['code'],d['msg'])"
get "/api/shops/1/reviews" "$USER_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  店铺评价数:',d['total'],'最新:',(d['list'][0]['nickname']+' '+str(d['list'][0]['shopScore'])+'星') if d['list'] else '-')"

step "16. 售后申请 → 后台仲裁退款"
AS=$(post /api/after-sales "$USER_TOKEN" "{\"orderId\":$ORDER_ID,\"type\":1,\"reason\":\"冒烟测试：少餐\",\"amount\":500}")
AS_ID=$(echo "$AS" | jq_get "['data']")
ok "售后工单 #$AS_ID 已提交"
get "/api/orders/$ORDER_ID" "$USER_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  订单状态:',d['status'],d['statusText'])"
if [ -n "${A_TOKEN:-}" ]; then
  post "/api/admin/after-sales/$AS_ID/judge" "$A_TOKEN" '{"approve":true,"amount":500,"liableParty":1,"remark":"冒烟仲裁：同意部分退款"}' | "$PY" -c "import sys,json;d=json.load(sys.stdin);print('  仲裁结果:',d['code'],d['msg'])"
  get "/api/after-sales/$AS_ID" "$USER_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  工单状态:',d['statusText'],'| 退款',d['amount'],'分')"
else
  echo "  跳过仲裁（未拿到管理员 token）"
fi

step "17. 订阅消息模板与发送日志"
get /api/notify/templates "$USER_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  模板:',[(t['key'],'已配置' if t['configured'] else '未配置') for t in d])"
if [ -n "${A_TOKEN:-}" ]; then
  get "/api/admin/notify-logs?page=1&size=5" "$A_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  最近消息:',[(r['statusText'],r.get('error') or r['bizNo']) for r in d['list'][:3]])"
fi

step "18. 越权退款防护（用别人的 token 退这一单）"
ATTACKER=$(post /api/auth/mock-login "" '{"openid":"attacker-smoke","role":"USER"}' | jq_get "['data']['token']")
post /api/pay/refund "$ATTACKER" "{\"orderId\":$ORDER_ID,\"reason\":\"hack\"}" | "$PY" -c "import sys,json;d=json.load(sys.stdin);print('  返回:',d['code'],d['msg'],'(期望 404)')"


step "19. 收藏店铺"
post "/api/favorites/1/toggle" "$USER_TOKEN" '{}' | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  收藏店铺 1 →',d['favorited'])"
get "/api/favorites" "$USER_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  我的收藏:',len(d),'家',[x['name'] for x in d][:3])"
get "/api/favorites/check?shopId=1" "$USER_TOKEN" | "$PY" -c "import sys,json;print('  检查状态:',json.load(sys.stdin)['data']['favorited'])"


step "20. 演示账号（商家可选店 / 骑手可选人）"
ACCTS=$(curl -s "$BASE/api/auth/mock-accounts?role=MERCHANT")
echo "$ACCTS" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  商家演示账号:',len(d),'家 →',[a['shopName'] for a in d[:3]])"
SPICY=$(echo "$ACCTS" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print(next((a['openid'] for a in d if a['shopId']==3), ''))")
if [ -n "$SPICY" ]; then
  MT=$(post /api/auth/mock-login "" "{\"openid\":\"$SPICY\",\"role\":\"MERCHANT\"}" | jq_get "['data']['token']")
  get /api/merchant/shop "$MT" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  用该账号登录后进入的店铺:',d['id'],d['name'])"
else
  echo "  跳过（没有 shopId=3 的演示商家）"
fi


step "21. 商家入驻状态与店铺券（批次B/D）"
M_TOKEN2=$(post /api/auth/mock-login "" '{"openid":"mock_merchant","role":"MERCHANT"}' | jq_get "['data']['token']")
get /api/merchant/status "$M_TOKEN2" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  入驻:',d.get('auditText'),'| 店:',d.get('name'),'| 营业:',d.get('bizStatus'),'| 门店照:',len(d.get('photos') or []))"
get /api/merchant/coupons "$M_TOKEN2" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  店铺券:',len(d),'张',[(c['name'],c['desc'],'上架' if c['status']==1 else '下架') for c in d[:3]])"

step "22. 规格 SKU 算价（批次C）"
SKU_LINE=$(curl -s "$BASE/api/shops/1/products" | "$PY" -c "
import sys,json
d=json.load(sys.stdin)['data']
p=next((x for x in d if x.get('hasSku')==1 and x.get('skus')), None)
print(f\"{p['id']} {p['skus'][0]['id']} {p['skus'][0]['name']} {p['skus'][0]['price']}\" if p else '')")
if [ -n "$SKU_LINE" ]; then
  set -- $SKU_LINE
  post /api/cart/calculate "$USER_TOKEN" "{\"shopId\":1,\"items\":[{\"productId\":$1,\"skuId\":$2,\"qty\":2}]}" \
    | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  规格价算价: 商品',d['goodsAmount'],'合计',d['totalAmount'])"
  post /api/cart/calculate "$USER_TOKEN" "{\"shopId\":1,\"items\":[{\"productId\":$1,\"qty\":2}]}" \
    | "$PY" -c "import sys,json;d=json.load(sys.stdin);print('  不选规格:',d['code'],d['msg'])"
else
  echo "  跳过（店铺 1 没有带规格的商品）"
fi

step "23. 商家资金：钱包 / 营收 / 对账单（批次A）"
get /api/merchant/wallet "$M_TOKEN2" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  钱包: 余额',d['balance'],'冻结',d['frozen'],'累计',d['totalIncome'],'流水',len(d['flows']),'条')"
get /api/merchant/summary "$M_TOKEN2" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];t=d['today'];print('  今日: 订单',t['orderCount'],'GMV',t['gmv'],'佣金',t['commission'],'实收',t['income'])"
get "/api/merchant/statements?page=1&size=5" "$M_TOKEN2" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  对账单:',d['page']['total'],'条 | 待结算',d['summary']['pendingIncome'],'分')"
get /api/merchant/withdrawals "$M_TOKEN2" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  提现记录:',len(d),'条',[(x['amount'],x['statusText']) for x in d[:3]])"

printf '\n\033[1;32m全部通过 ✅\033[0m\n'
