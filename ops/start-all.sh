#!/usr/bin/env bash
# 一条命令起全部：MySQL/Redis + 后端 + 管理后台 + 小程序 H5 预览
# 用法：~/lcu-delivery/ops/start-all.sh
set -uo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

echo "▶ 1/3 数据库"
MYSQL_PORT="${MYSQL_PORT:-3307}" ./ops/dev.sh || exit 1

echo "▶ 2/3 管理后台 (5174)"
if lsof -nP -iTCP:5174 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  已在运行"
else
  (cd apps/admin-web && nohup npm run dev > /tmp/admin-web.log 2>&1 &)
fi

echo "▶ 3/3 小程序 H5 (5173，用户/商家/骑手共用)"
if lsof -nP -iTCP:5173 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  已在运行"
else
  (cd apps/lcu-mobile && nohup npm run dev:h5 > /tmp/lcu-mobile-h5.log 2>&1 &)
fi

sleep 12
echo ""
echo "══════════ 全部就绪 ══════════"
echo " 小程序 H5      http://127.0.0.1:5173   用户 / 商家 mock_merchant / 骑手 mock_rider"
echo " 管理后台      http://127.0.0.1:5174   admin / Admin@123456"
echo " 后端接口      http://127.0.0.1:8080/actuator/health"
echo ""
echo " 停止：./ops/stop-all.sh"
