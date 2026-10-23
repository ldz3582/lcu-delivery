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

echo "▶ 构建后端"
(cd ../server && $MAVEN -B -q package -DskipTests)

echo "▶ 启动后端（日志：/tmp/lcu-delivery.log）"
DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT:-3307}/lcu_delivery?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
REDIS_PORT="${REDIS_PORT:-6379}" \
REDIS_PASSWORD="$(grep -E '^REDIS_PASSWORD=' .env | cut -d= -f2)" \
ADMIN_INIT_PASSWORD="${ADMIN_INIT_PASSWORD:-Admin@123456}" \
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
