#!/usr/bin/env bash
# 内网穿透：把本机 8080 暴露成一个公网 HTTPS 地址，别人任何网络都能连
#
# 踩过的坑（都写进脚本了）：
#   1) 默认走 QUIC/UDP，校园网/公司网常把 UDP 封掉 → 连不上，一律用 --protocol http2
#   2) 快速隧道会自己掉线，进程还活着但对外 530 → 带健康检查，挂了自动重连
#   3) 电脑休眠隧道就断 → 用 caffeinate 顶住
#
# 用法：
#   ./ops/tunnel.sh start     起隧道（带守护），打印公网地址
#   ./ops/tunnel.sh url       打印当前地址
#   ./ops/tunnel.sh check     体检：本地后端 + 公网出口
#   ./ops/tunnel.sh rebuild   按当前地址重新构建小程序
#   ./ops/tunnel.sh stop      停隧道和守护
set -euo pipefail
cd "$(dirname "$0")/.."
LOG=/tmp/lcu-tunnel.log
PIDF=/tmp/lcu-tunnel.pid
WATCH_LOG=/tmp/lcu-tunnel-watch.log

cur_url() { grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$LOG" 2>/dev/null | head -1; }
health() { curl -s -o /dev/null -m 12 -w "%{http_code}" "$1/api/shops?page=1&size=1" 2>/dev/null || true; }

start_once() {
  : > "$LOG"
  nohup caffeinate -i cloudflared tunnel --url http://127.0.0.1:8080 \
    --no-autoupdate --protocol http2 > "$LOG" 2>&1 &
  echo $! > "$PIDF"
  printf "  等隧道注册"
  for i in $(seq 1 45); do
    grep -q "Registered tunnel connection" "$LOG" 2>/dev/null && { echo " ✓"; return 0; }
    printf "."; sleep 2
  done
  echo " ✗"; return 1
}

case "${1:-start}" in
url)
  u=$(cur_url); [ -n "$u" ] && echo "$u" || { echo "隧道未运行"; exit 1; } ;;

check)
  echo "本地后端   HTTP $(curl -s -o /dev/null -m 5 -w '%{http_code}' 'http://127.0.0.1:8080/api/shops?page=1&size=1' 2>/dev/null)"
  u=$(cur_url || true)
  if [ -n "${u:-}" ]; then echo "公网出口   HTTP $(health "$u")   $u"; else echo "公网出口   隧道未运行"; fi ;;

stop)
  pkill -f "cloudflared tunnel --url http://127.0.0.1:8080" 2>/dev/null || true
  pkill -f "lcu-tunnel-watch" 2>/dev/null || true
  rm -f "$PIDF"; echo "隧道已停" ;;

rebuild)
  u=$(cur_url); [ -n "$u" ] || { echo "✗ 先跑 ./ops/tunnel.sh start"; exit 1; }
  ./ops/publish-wechat.sh build "$u"
  echo ""
  echo "下一步：重新出预览码（或在开发者工具里点「预览」/「上传」）"
  echo "  /Applications/wechatwebdevtools.app/Contents/MacOS/cli preview \\"
  echo "    --project \"\$PWD/apps/lcu-mobile/dist/build/mp-weixin\" -f image -o \"\$PWD/preview_qr.png\"" ;;

start|*)
  command -v cloudflared >/dev/null || { echo "✗ 没装 cloudflared：brew install cloudflared"; exit 1; }
  curl -s -m 3 http://127.0.0.1:8080/actuator/health | grep -q UP \
    || { echo "✗ 后端没起，先跑 ./ops/dev-local.sh"; exit 1; }

  u=$(cur_url || true)
  if [ -n "${u:-}" ] && [ "$(health "$u")" = "200" ]; then
    echo "隧道已在跑且健康：$u"; exit 0
  fi

  echo "▶ 启动隧道（HTTP/2 + 防休眠）"
  pkill -f "cloudflared tunnel --url http://127.0.0.1:8080" 2>/dev/null || true
  sleep 1
  start_once || { echo "  见 $LOG"; exit 1; }
  u=$(cur_url)
  for i in $(seq 1 20); do [ "$(health "$u")" = "200" ] && break; sleep 3; done
  code=$(health "$u")
  echo "  公网自检 HTTP $code"
  [ "$code" = "200" ] || { echo "  ✗ 隧道注册了但回源不通，见 $LOG"; exit 1; }

  if ! pgrep -f "lcu-tunnel-watch" >/dev/null; then
    nohup bash -c '
      while true; do
        sleep 30
        u=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" /tmp/lcu-tunnel.log 2>/dev/null | head -1)
        [ -z "$u" ] && continue
        c=$(curl -s -o /dev/null -m 10 -w "%{http_code}" "$u/api/shops?page=1&size=1" 2>/dev/null || echo 000)
        if [ "$c" != "200" ] && [ "$c" != "401" ] && [ "$c" != "400" ]; then
          echo "$(date "+%H:%M:%S") 隧道异常 ${c}，重连" >> /tmp/lcu-tunnel-watch.log
          pkill -f "cloudflared tunnel --url http://127.0.0.1:8080" 2>/dev/null
          sleep 2
          : > /tmp/lcu-tunnel.log
          nohup caffeinate -i cloudflared tunnel --url http://127.0.0.1:8080 \
            --no-autoupdate --protocol http2 > /tmp/lcu-tunnel.log 2>&1 &
        fi
      done' lcu-tunnel-watch > /dev/null 2>&1 &
  fi

  echo ""
  echo "公网地址  $u"
  echo "           地址变了就跑： ./ops/tunnel.sh rebuild"
  echo "守护日志  $WATCH_LOG"
  ;;
esac
