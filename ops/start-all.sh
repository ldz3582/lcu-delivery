#!/usr/bin/env bash
# 一条命令起全部：MySQL/Redis + 后端 + 管理后台 + 两个 H5 预览
# 用法：~/lcu-delivery/ops/start-all.sh
set -uo pipefail
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

echo "▶ 1/4 数据库"
MYSQL_PORT="${MYSQL_PORT:-3307}" ./ops/dev.sh || exit 1

echo "▶ 2/4 管理后台 (5174)"
if lsof -nP -iTCP:5174 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  已在运行"
else
  (cd apps/admin-web && nohup npm run dev > /tmp/admin-web.log 2>&1 &)
fi

echo "▶ 3/4 用户端 H5 (5173)"
if lsof -nP -iTCP:5173 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  已在运行"
else
  (cd apps/user-uniapp && nohup npm run dev:h5 > /tmp/user-h5.log 2>&1 &)
fi

echo "▶ 4/4 工作台 H5 (5175)"
if lsof -nP -iTCP:5175 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "  已在运行"
else
  (cd apps/workbench-uniapp && nohup npm run dev:h5 > /tmp/workbench-h5.log 2>&1 &)
fi

sleep 12
echo ""
echo "══════════ 全部就绪 ══════════"
echo " 用户端 H5      http://127.0.0.1:5173"
echo " 管理后台      http://127.0.0.1:5174   admin / Admin@123456"
echo " 商家·骑手台   http://127.0.0.1:5175   商家 mock_merchant / 骑手 mock_rider"
echo " 后端接口      http://127.0.0.1:8080/actuator/health"
echo ""
echo " 停止：./ops/stop-all.sh"
