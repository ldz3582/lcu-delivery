#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
for p in 5173 5174 5175; do
  pid=$(lsof -ti:$p 2>/dev/null | head -1)
  [ -n "$pid" ] && kill "$pid" 2>/dev/null && echo "已停止端口 $p"
done
./dev.sh stop
