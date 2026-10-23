#!/usr/bin/env bash
# 一键启动本地开发环境：MySQL/Redis + 后端
# 用法：./ops/dev.sh          启动
#       ./ops/dev.sh stop     停止
set -euo pipefail
cd "$(dirname "$0")"

# 优先用仓库自带的 Maven，其次系统 mvn，最后用 mvnw
if [ -x "../../tools/apache-maven-3.9.9/bin/mvn" ]; then
  MAVEN="../../tools/apache-maven-3.9.9/bin/mvn"
elif command -v mvn >/dev/null 2>&1; then
  MAVEN="mvn"
else
  MAVEN="../server/mvnw"
fi
PIDFILE="/tmp/lcu-delivery.pid"

if [ "${1:-}" = "stop" ]; then
  docker compose down
  [ -f "$PIDFILE" ] && kill "$(cat "$PIDFILE")" 2>/dev/null || true
  pid=$(lsof -ti:8080 -sTCP:LISTEN 2>/dev/null | head -1); [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
  rm -f "$PIDFILE"
  echo "已停止"
  exit 0
fi

echo "▶ 启动 MySQL / Redis"
[ -f .env ] || cp .env.example .env
MYSQL_PORT="${MYSQL_PORT:-3307}" docker compose up -d mysql redis
printf "  等待 MySQL 健康检查"
for i in $(seq 1 40); do
  s=$(docker inspect -f '{{.State.Health.Status}}' lcu-mysql 2>/dev/null || echo starting)
  [ "$s" = "healthy" ] && { echo " ✓"; break; }
  printf "."; sleep 3
done

# 先停掉占用 8080 的旧进程：否则重新打包会覆盖 jar，旧进程再加载类会 ClassNotFoundException
# 注意必须加 -sTCP:LISTEN：不加的话 lsof 会把「连过 8080 的客户端」也列出来
# （比如微信开发者工具），结果杀错进程，旧后端继续占端口，新后端起不来。
old=$(lsof -ti:8080 -sTCP:LISTEN 2>/dev/null | head -1)
if [ -n "$old" ]; then
  echo "▶ 停止旧后端进程 $old"
  kill "$old" 2>/dev/null || true
  for i in $(seq 1 10); do
    lsof -ti:8080 -sTCP:LISTEN >/dev/null 2>&1 || break
    sleep 1
  done
fi

echo "▶ 构建后端"
(cd ../server && $MAVEN -B -q package -DskipTests)

echo "▶ 启动后端（日志：/tmp/lcu-delivery.log）"
# 从 .env 读取微信相关配置（没有就留空，后端会走 mock）
# 读取 .env；把「占位值」当成空（否则后端会拿假 AppID 去调微信）
env_get() {
  v=$(grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2- || true)
  case "$v" in
    ""|wx0000000000000000|replace_me|*replace_me*) echo "" ;;
    *) echo "$v" ;;
  esac
}

DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT:-3307}/lcu_delivery?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
REDIS_PORT="${REDIS_PORT:-6379}" \
REDIS_PASSWORD="$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2)" \
ADMIN_INIT_PASSWORD="${ADMIN_INIT_PASSWORD:-Admin@123456}" \
WX_USER_APPID="$(env_get WX_USER_APPID)" \
WX_USER_SECRET="$(env_get WX_USER_SECRET)" \
WX_WORK_APPID="$(env_get WX_WORK_APPID)" \
WX_WORK_SECRET="$(env_get WX_WORK_SECRET)" \
WXPAY_MCH_ID="$(env_get WXPAY_MCH_ID)" \
WXPAY_MCH_SERIAL_NO="$(env_get WXPAY_MCH_SERIAL_NO)" \
WXPAY_API_V3_KEY="$(env_get WXPAY_API_V3_KEY)" \
WXPAY_PRIVATE_KEY_PATH="$(env_get WXPAY_PRIVATE_KEY_PATH)" \
WXPAY_NOTIFY_URL="$(env_get WXPAY_NOTIFY_URL)" \
nohup java -jar ../server/target/delivery-0.1.0.jar > /tmp/lcu-delivery.log 2>&1 &
echo $! > "$PIDFILE"

printf "  等待后端就绪"
for i in $(seq 1 60); do
  if curl -s -m 2 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q UP; then
    echo " ✓"
    echo ""
    echo "后端      http://127.0.0.1:8080"
    echo "管理后台  cd apps/admin-web && npm run dev   → http://127.0.0.1:5174"
    echo "小程序    微信开发者工具导入 apps/user-uniapp/dist/build/mp-weixin"
    echo "          微信开发者工具导入 apps/workbench-uniapp/dist/build/mp-weixin"
    echo "管理员    admin / Admin@123456（仅当 admin_user 为空时自动创建）"
    exit 0
  fi
  printf "."; sleep 2
done
echo " ✗ 启动超时，请看 /tmp/lcu-delivery.log"
exit 1
