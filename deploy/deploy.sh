#!/usr/bin/env bash
# 本地一键部署到 ECS：打包 → 上传 → 装 Docker → compose 起栈 → 验活
#
#   SERVER=root@47.102.49.46 ./deploy/deploy.sh
#   SERVER=root@47.102.49.46 ./deploy/deploy.sh --reuse-jar   # 不重新 mvn package
#
# 前置：本机能 ssh 到 SERVER（公钥已写入），本地已装 Java 21+/Maven/Node/npm
set -euo pipefail

SERVER="${SERVER:-}"
APP_DIR="${APP_DIR:-/opt/lcu}"
REUSE_JAR=0
[ "${1:-}" = "--reuse-jar" ] && REUSE_JAR=1

if [ -z "$SERVER" ]; then
  echo "用法：SERVER=root@<公网IP> $0" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MVN="${MVN:-$ROOT/../tools/apache-maven-3.9.9/bin/mvn}"
STAGE="$ROOT/deploy/.stage"
SSH_KEY="${SSH_KEY:-}"
SSH_OPTS=(-o StrictHostKeyChecking=accept-new -o ConnectTimeout=15 -o ServerAliveInterval=15)
[ -n "$SSH_KEY" ] && SSH_OPTS+=(-i "$SSH_KEY" -o IdentitiesOnly=yes)

say() { printf '\n\033[1;36m== %s\033[0m\n' "$*"; }

say "0/6 连通性检查：$SERVER"
ssh "${SSH_OPTS[@]}" "$SERVER" 'echo "  登录成功：$(hostname) / $(. /etc/os-release; echo $PRETTY_NAME)"'

if [ "$REUSE_JAR" = "0" ]; then
  say "1/6 构建后端 jar（Maven）"
  ( cd "$ROOT/server" && JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 25)}" \
      "$MVN" -B -q clean package -DskipTests )
else
  say "1/6 复用已有 jar"
fi
JAR="$ROOT/server/target/delivery-0.1.0.jar"
[ -f "$JAR" ] || { echo "找不到 $JAR" >&2; exit 1; }

say "2/6 构建管理后台（Vite）"
( cd "$ROOT/apps/admin-web" && npm run build >/tmp/lcu-admin-build.log 2>&1 ) || {
  tail -30 /tmp/lcu-admin-build.log; exit 1; }
echo "  dist $(du -sh "$ROOT/apps/admin-web/dist" | cut -f1)"

say "3/6 组装部署包"
rm -rf "$STAGE"
mkdir -p "$STAGE"/{admin,init,nginx,h5}
cp "$ROOT/deploy/Dockerfile.api" "$ROOT/deploy/docker-compose.yml" "$ROOT/deploy/.env.example" "$STAGE/"
cp "$ROOT/deploy/bootstrap-server.sh" "$ROOT/deploy/gen-env.sh" "$STAGE/"
cp "$ROOT/deploy/nginx/default.conf" "$STAGE/nginx/"
cp "$JAR" "$STAGE/delivery.jar"
cp -R "$ROOT/apps/admin-web/dist/." "$STAGE/admin/"
# 初始化顺序很关键：schema → 迁移（建 banner 等新表）→ seed → demo
# 实测：若 seed/demo 先于迁移执行，demo.sql 会因 banner 表不存在而中断
cp "$ROOT/ops/sql/schema.sql" "$STAGE/init/10-schema.sql"
cat "$ROOT"/ops/sql/migration-*.sql > "$STAGE/init/15-migrations.sql"
cp "$ROOT/ops/sql/seed.sql"   "$STAGE/init/20-seed.sql"
cp "$ROOT/ops/sql/demo.sql"   "$STAGE/init/30-demo.sql"
echo "  包大小 $(du -sh "$STAGE" | cut -f1)  文件数 $(find "$STAGE" -type f | wc -l | tr -d ' ')"

say "4/6 上传到 $SERVER:$APP_DIR"
ssh "${SSH_OPTS[@]}" "$SERVER" "mkdir -p $APP_DIR"
if ssh "${SSH_OPTS[@]}" "$SERVER" 'command -v rsync >/dev/null'; then
  rsync -az --delete --progress \
    --exclude 'data/**' --exclude 'uploads/**' --exclude '.env' \
    -e "ssh ${SSH_OPTS[*]}" "$STAGE/" "$SERVER:$APP_DIR/"
else
  echo "  服务器无 rsync，改用 tar 流上传"
  tar -C "$STAGE" -czf - . | ssh "${SSH_OPTS[@]}" "$SERVER" "tar -xzf - -C $APP_DIR"
fi

say "5/6 服务器基线（swap / Docker / 镜像加速 / 目录）"
ssh "${SSH_OPTS[@]}" "$SERVER" "APP_DIR=$APP_DIR bash $APP_DIR/bootstrap-server.sh"

say "6/6 生成 .env（仅首次）并启动"
# 坑：compose v5 的 `up --build` 要求 buildx >= 0.17，而阿里云源里是 0.14，
#     所以镜像用 `docker build` 单独构建，compose 只负责起容器。
ssh "${SSH_OPTS[@]}" "$SERVER" "cd $APP_DIR && bash gen-env.sh \
  && docker build -t lcu/api:0.1.0 -f Dockerfile.api . \
  && docker compose up -d"

say "验活"
ssh "${SSH_OPTS[@]}" "$SERVER" "cd $APP_DIR && docker compose ps"
IP="${SERVER#*@}"
for i in $(seq 1 30); do
  if curl -fsS --max-time 5 "http://$IP/health" >/dev/null 2>&1; then
    echo "  后端健康：$(curl -fsS http://$IP/health)"
    break
  fi
  sleep 5
  [ "$i" = "30" ] && { echo "  ✗ 健康检查超时，看日志：ssh $SERVER 'cd $APP_DIR && docker compose logs --tail=80 api'"; }
done

echo
echo "══════════ 部署完成 ══════════"
echo "  管理后台   http://$IP/          admin / Admin@123456"
echo "  接口       http://$IP/api/shops"
echo "  健康检查   http://$IP/health"
echo "  日志       ssh $SERVER 'cd $APP_DIR && docker compose logs -f api'"
echo "  重启       ssh $SERVER 'cd $APP_DIR && docker compose restart api'"
