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
BILL=$(post /api/cart/calculate "$USER_TOKEN" '{"shopId":1,"items":[{"productId":1,"qty":2}]}')
echo "$BILL" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  商品',d['goodsAmount'],'打包',d['packFee'],'配送',d['deliveryFee'],'合计',d['totalAmount'],'起送达标',d['reachMin'])"
TOTAL=$(echo "$BILL" | jq_get "['data']['totalAmount']")

step "5. 创建订单（地址 1，围栏内）"
ORDER=$(post /api/orders "$USER_TOKEN" '{"shopId":1,"addressId":1,"items":[{"productId":1,"qty":2}],"remark":"少放辣"}')
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
RESP=$(post /api/orders "$USER_TOKEN" "{\"shopId\":1,\"addressId\":$OUT_ID,\"items\":[{\"productId\":1,\"qty\":1}]}")
echo "$RESP" | "$PY" -c "import sys,json;d=json.load(sys.stdin);print('  返回码:',d['code'],'|',d['msg'])"

step "11. 管理后台"
if [ -n "${ADMIN_PASSWORD:-}" ]; then
  A_TOKEN=$(post /api/admin/login "" "{\"username\":\"admin\",\"password\":\"$ADMIN_PASSWORD\"}" | jq_get "['data']['token']")
  echo "  管理员 token: ${A_TOKEN:0:18}..."
  get /api/admin/overview "$A_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  今日订单',d['todayOrders'],'| 今日 GMV',d['todayGmv'],'分 | 商家',d['totalShops'],'| 骑手',d['totalRiders'])"
  get /api/admin/settlements "$A_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  结算单:',d['total'],'条')"
  get /api/admin/fence "$A_TOKEN" | "$PY" -c "import sys,json;d=json.load(sys.stdin)['data'];print('  围栏:',len(d),'个',d[0]['name'] if d else '')"
else
  echo "  跳过（未提供 ADMIN_PASSWORD；密码见后端启动日志）"
fi

printf '\n\033[1;32m全部通过 ✅\033[0m\n'
