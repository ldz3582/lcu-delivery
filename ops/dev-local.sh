#!/usr/bin/env bash
# 不装 Docker 也能起后端：用 Homebrew 的 MySQL 8.4 + Redis
#
# 用法：
#   ./ops/dev-local.sh          启动（幂等，重复跑没事）
#   ./ops/dev-local.sh stop     停后端（MySQL/Redis 服务保留）
#   ./ops/dev-local.sh status   看状态
#
# 跟 dev.sh 的区别：dev.sh 用 docker compose 起依赖；这个用 brew services。
# 本机 3306 被另一个 MySQL 占着，所以 brew 的 MySQL 挪到 3307，互不影响。
set -euo pipefail
cd "$(dirname "$0")/.."

OPS="$(pwd)/ops"
MYSQL_BIN="/opt/homebrew/opt/mysql@8.4/bin/mysql"
MYSQL_SOCK="/tmp/mysql3307.sock"
MYSQL_PORT=3307
PIDFILE="/tmp/lcu-delivery.pid"
LOG="/tmp/lcu-delivery.log"

[ -f "$OPS/.env" ] || { echo "✗ 缺 ops/.env"; exit 1; }
env_get() { grep -E "^$1=" "$OPS/.env" 2>/dev/null | head -1 | cut -d= -f2- || true; }

DB=$(env_get MYSQL_DATABASE); U=$(env_get MYSQL_USER); P=$(env_get MYSQL_PASSWORD)
DBP=$(env_get DB_PASSWORD); RP=$(env_get REDIS_PASSWORD)

case "${1:-start}" in
stop)
  pid=$(lsof -ti:8080 -sTCP:LISTEN 2>/dev/null | head -1 || true)
  [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
  rm -f "$PIDFILE"
  echo "已停止后端（MySQL/Redis 仍在跑）"
  exit 0 ;;
status)
  curl -s -m 2 http://127.0.0.1:8080/actuator/health | grep -q UP \
    && echo "后端 UP  http://127.0.0.1:8080" || echo "后端 未运行"
  /opt/homebrew/opt/redis/bin/redis-cli -a "$RP" --no-auth-warning ping >/dev/null 2>&1 \
    && echo "Redis  UP  :6379" || echo "Redis  未运行"
  "$MYSQL_BIN" -u"$U" -p"$P" -S "$MYSQL_SOCK" -N -e "SELECT 1" >/dev/null 2>&1 \
    && echo "MySQL  UP  :$MYSQL_PORT" || echo "MySQL  未运行"
  exit 0 ;;
esac

echo "▶ 启动 MySQL / Redis"
HOMEBREW_NO_AUTO_UPDATE=1 brew services start mysql@8.4 >/dev/null 2>&1 || true
HOMEBREW_NO_AUTO_UPDATE=1 brew services start redis   >/dev/null 2>&1 || true

printf "  等 MySQL"
for i in $(seq 1 40); do
  "$MYSQL_BIN" -uroot -S "$MYSQL_SOCK" -e "SELECT 1" >/dev/null 2>&1 && { echo " ✓"; break; }
  printf "."; sleep 2
done
/opt/homebrew/opt/redis/bin/redis-cli -a "$RP" --no-auth-warning ping >/dev/null 2>&1 \
  && echo "  Redis ✓" || { echo "  ✗ Redis 起不来（检查 /opt/homebrew/etc/redis.conf 的 requirepass）"; exit 1; }

echo "▶ 确保库表存在（幂等，已有数据不会重复插）"
"$MYSQL_BIN" -uroot -S "$MYSQL_SOCK" -e "
CREATE DATABASE IF NOT EXISTS \`$DB\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$U'@'localhost' IDENTIFIED BY '$P';
GRANT ALL PRIVILEGES ON \`$DB\`.* TO '$U'@'localhost';
FLUSH PRIVILEGES;" 2>/dev/null || true

n=$("$MYSQL_BIN" -u"$U" -p"$P" -S "$MYSQL_SOCK" "$DB" -N -e \
  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB';" 2>/dev/null || echo 0)
if [ "${n:-0}" -lt 20 ]; then
  echo "  表数量 ${n}，导入 schema/seed/demo"
  for f in schema seed demo; do
    "$MYSQL_BIN" -u"$U" -p"$P" -S "$MYSQL_SOCK" "$DB" < "$OPS/sql/$f.sql" 2>/dev/null
  done
  echo "  ✓ 导入完成"
else
  echo "  已有 $n 张表，跳过导入"
fi

echo "▶ 构建后端"
[ -f server/target/delivery-0.1.0.jar ] || {
  ( cd server && JAVA_HOME=$(/usr/libexec/java_home -v 25 2>/dev/null || /usr/libexec/java_home) \
      ../../tools/apache-maven-3.9.9/bin/mvn -B -q package -DskipTests )
}

old=$(lsof -ti:8080 -sTCP:LISTEN 2>/dev/null | head -1 || true)
[ -n "$old" ] && { echo "▶ 停掉旧后端 $old"; kill "$old" 2>/dev/null || true; sleep 2; }

echo "▶ 启动后端（日志 ${LOG}）"

# 上传图片对外地址：隧道在跑就用公网地址，否则用局域网 IP。
# 不设这个，后端在隧道后面会把图片地址拼成 http://域名:80/uploads/...，
# 微信直接拦（不安全资源），用户端/后台的图全都显示不出来。
TUNNEL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" /tmp/lcu-tunnel.log 2>/dev/null | head -1 || true)
LANIP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")
PUBLIC_BASE="${APP_PUBLIC_BASE_URL:-${TUNNEL:-http://$LANIP:8080}}"

DB_URL="jdbc:mysql://127.0.0.1:${MYSQL_PORT}/${DB}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true" \
DB_USERNAME="$U" DB_PASSWORD="${DBP:-$P}" \
REDIS_HOST=127.0.0.1 REDIS_PORT=6379 REDIS_PASSWORD="$RP" \
ADMIN_INIT_PASSWORD="${ADMIN_INIT_PASSWORD:-Admin@123456}" \
APP_UPLOAD_DIR="$OPS/uploads" \
APP_PUBLIC_BASE_URL="$PUBLIC_BASE" \
WX_USER_APPID="$(env_get WX_USER_APPID)" \
WX_USER_SECRET="$(env_get WX_USER_SECRET)" \
WX_WORK_APPID="$(env_get WX_WORK_APPID)" \
WX_WORK_SECRET="$(env_get WX_WORK_SECRET)" \
nohup java -jar server/target/delivery-0.1.0.jar > "$LOG" 2>&1 &
echo $! > "$PIDFILE"

printf "  等后端就绪"
for i in $(seq 1 60); do
  curl -s -m 2 http://127.0.0.1:8080/actuator/health 2>/dev/null | grep -q UP && { echo " ✓"; break; }
  printf "."; sleep 2
done

IP=$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || echo "127.0.0.1")
echo ""
echo "后端       http://127.0.0.1:8080"
echo "局域网      http://$IP:8080    ← 手机/体验版要连这个"
echo "管理后台    admin / ${ADMIN_INIT_PASSWORD:-Admin@123456}"
echo ""
echo "小程序产物目前指向：$(sed -n 's/^VITE_API_BASE=//p' apps/lcu-mobile/.env.production 2>/dev/null || echo '未构建')"
echo "换地址重新构建： ./ops/publish-wechat.sh build http://$IP:8080"
