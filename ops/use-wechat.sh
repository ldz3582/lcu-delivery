#!/usr/bin/env bash
# 把真实微信小程序 AppID/AppSecret 接入项目
# 流程：校验凭据 → 写 ops/.env → 改 manifest → 重建 lcu-mobile → 重启后端 → 自检
#
# 用法：
#   ./ops/use-wechat.sh wx1234567890abcdef 你的AppSecret
#
# 说明：合并后只有一个小程序 apps/lcu-mobile，用户/商家/骑手/管理员共用同一个 AppID。
set -euo pipefail
cd "$(dirname "$0")/.."
OPS="$(cd ops && pwd)"

APPID="${1:-}"
SECRET="${2:-}"
APP_DIR="apps/lcu-mobile"

if [ -z "$APPID" ] || [ -z "$SECRET" ]; then
  echo "用法: ./ops/use-wechat.sh <AppID> <AppSecret>"
  echo "AppID 形如 wx1234567890abcdef（小程序后台 → 开发管理 → 开发设置）"
  exit 1
fi

case "$APPID" in
  touristappid|wx0000000000000000|wx1234567890abcdef)
    echo "✗ 这是占位值，请填真实 AppID"; exit 1 ;;
  wx*) ;;
  *) echo "✗ AppID 应以 wx 开头"; exit 1 ;;
esac

echo "▶ 1/6 校验 AppID / AppSecret"
RESP=$(curl -s -m 10 "https://api.weixin.qq.com/sns/jscode2session?appid=${APPID}&secret=${SECRET}&js_code=test&grant_type=authorization_code")
echo "  $RESP"
ERR=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('errcode',0))" 2>/dev/null || echo 999)
case "$ERR" in
  40029) echo "  ✓ 凭据有效（40029 = 测试 code 无效，属预期）" ;;
  40013) echo "  ✗ AppID 无效，检查是否复制完整"; exit 1 ;;
  40125) echo "  ✗ AppSecret 无效，到「开发管理 → 开发设置」重置后重试"; exit 1 ;;
  0)     echo "  ✓ 凭据有效" ;;
  *)     echo "  ! 未预期的返回，请人工确认"; exit 1 ;;
esac

echo "▶ 2/6 写入 ops/.env"
python3 - "$OPS/.env" "$APPID" "$SECRET" <<'PY'
import sys, pathlib
env, appid, secret = sys.argv[1:4]
p = pathlib.Path(env)
lines = p.read_text(encoding="utf-8").splitlines() if p.exists() else []
kv = {"WX_USER_APPID": appid, "WX_USER_SECRET": secret}
out, seen = [], set()
for line in lines:
    k = line.split("=", 1)[0].strip()
    if k in kv:
        out.append(f"{k}={kv[k]}"); seen.add(k)
    else:
        out.append(line)
for k, v in kv.items():
    if k not in seen:
        out.append(f"{k}={v}")
p.write_text("\n".join(out) + "\n", encoding="utf-8")
print("  已写入", env)
PY

echo "▶ 3/6 更新 $APP_DIR/src/manifest.json 的 appid"
python3 - "$APP_DIR/src/manifest.json" "$APPID" <<'PY'
import json, sys
path, appid = sys.argv[1], sys.argv[2]
d = json.load(open(path, encoding="utf-8"))
d.setdefault("mp-weixin", {})["appid"] = appid
json.dump(d, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
print(f"  {path} -> {appid}")
PY

echo "▶ 4/6 重建小程序（mp-weixin）"
(cd "$APP_DIR" && npm run build:mp-weixin >/tmp/build-mobile.log 2>&1) \
  && echo "  lcu-mobile ✓" \
  || { echo "  构建失败，见 /tmp/build-mobile.log"; tail -20 /tmp/build-mobile.log; exit 1; }

echo "▶ 5/6 重启后端（带真实凭据）"
./ops/dev.sh >/dev/null 2>&1 || { echo "  后端启动失败，见 /tmp/lcu-delivery.log"; exit 1; }
curl -s http://127.0.0.1:8080/actuator/health | grep -q UP && echo "  后端 ✓" || { echo "  后端未就绪"; exit 1; }

echo "▶ 6/6 自检：真实登录通道是否已配置"
curl -s -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"code":"fake-code-for-check"}' | head -c 300
echo ""
echo ""

echo "════════════════════════════════════════"
echo " 接线完成。接下来在微信开发者工具里："
echo " 1) 用小程序管理员微信扫码登录"
echo " 2) 导入 $APP_DIR/dist/build/mp-weixin（AppID 会自动读取）"
echo " 3) 详情 → 本地设置 → 勾选「不校验合法域名」"
echo ""
echo " 还要去小程序后台开两个开关（只能你在网页上操作）："
echo "   · 开发管理 → 接口设置 → 开通「地理位置」"
echo "   · 设置 → 服务内容声明 → 隐私保护指引 → 勾选「位置信息」"
echo ""
echo " 详细步骤见 docs/接入微信公众平台.md"
