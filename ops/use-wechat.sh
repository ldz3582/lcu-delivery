#!/usr/bin/env bash
# 把真实微信小程序 AppID/AppSecret 接入项目（校验 → 写配置 → 改 manifest → 重建 → 重启后端）
#
# 用法：
#   ./ops/use-wechat.sh wx1234567890abcdef 你的AppSecret
#   ./ops/use-wechat.sh wx1234567890abcdef 你的AppSecret wx工作台AppID 工作台AppSecret   # 工作台另建小程序时
set -euo pipefail
cd "$(dirname "$0")/.."
OPS="$(cd ops && pwd)"

APPID="${1:-}"
SECRET="${2:-}"
WORK_APPID="${3:-$APPID}"
WORK_SECRET="${4:-$SECRET}"

if [ -z "$APPID" ] || [ -z "$SECRET" ]; then
  echo "用法: ./ops/use-wechat.sh <AppID> <AppSecret> [工作台AppID] [工作台AppSecret]"
  exit 1
fi

echo "▶ 1/6 校验 AppID / AppSecret"
RESP=$(curl -s -m 10 "https://api.weixin.qq.com/sns/jscode2session?appid=${APPID}&secret=${SECRET}&js_code=test&grant_type=authorization_code")
echo "  $RESP"
ERR=$(echo "$RESP" | python3 -c "import sys,json;print(json.load(sys.stdin).get('errcode',0))" 2>/dev/null || echo 999)
case "$ERR" in
  40029) echo "  ✓ 凭据有效（40029 = code 无效，属预期，说明 AppID/AppSecret 正确）" ;;
  40013) echo "  ✗ AppID 无效，检查是否复制完整"; exit 1 ;;
  40125) echo "  ✗ AppSecret 无效，到「开发管理 → 开发设置」重置后重试"; exit 1 ;;
  0)     echo "  ✓ 凭据有效" ;;
  *)     echo "  ! 未预期的返回，请人工确认"; exit 1 ;;
esac

echo "▶ 2/6 写入 ops/.env"
python3 - "$OPS/.env" "$APPID" "$SECRET" "$WORK_APPID" "$WORK_SECRET" <<'PY'
import sys, pathlib
env, appid, secret, wappid, wsecret = sys.argv[1:6]
p = pathlib.Path(env)
lines = p.read_text(encoding="utf-8").splitlines() if p.exists() else []
kv = {"WX_USER_APPID": appid, "WX_USER_SECRET": secret,
      "WX_WORK_APPID": wappid, "WX_WORK_SECRET": wsecret}
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

echo "▶ 3/6 更新两个小程序的 manifest.json appid"
python3 - "$APPID" "$WORK_APPID" <<'PY'
import json, sys
appid, wappid = sys.argv[1], sys.argv[2]
for path, val in (("apps/user-uniapp/src/manifest.json", appid),
                  ("apps/workbench-uniapp/src/manifest.json", wappid)):
    d = json.load(open(path, encoding="utf-8"))
    d.setdefault("mp-weixin", {})["appid"] = val
    json.dump(d, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"  {path} -> {val}")
PY

echo "▶ 4/6 重建小程序（mp-weixin）"
(cd apps/user-uniapp && npm run build:mp-weixin >/tmp/build-user.log 2>&1) && echo "  用户端 ✓" || { echo "  用户端构建失败，见 /tmp/build-user.log"; exit 1; }
(cd apps/workbench-uniapp && npm run build:mp-weixin >/tmp/build-workbench.log 2>&1) && echo "  工作台 ✓" || { echo "  工作台构建失败，见 /tmp/build-workbench.log"; exit 1; }

echo "▶ 5/6 重启后端（带真实凭据）"
./ops/dev.sh >/dev/null 2>&1 || { echo "  后端启动失败，见 /tmp/lcu-delivery.log"; exit 1; }
curl -s http://127.0.0.1:8080/actuator/health | grep -q UP && echo "  后端 ✓" || { echo "  后端未就绪"; exit 1; }

echo "▶ 6/6 自检：真实登录通道是否已配置"
curl -s -X POST http://127.0.0.1:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"code":"fake-code-for-check"}' | head -c 300
echo ""
echo ""
echo "════════════════════════════════════════"
echo " 已完成接线。接下来在微信开发者工具里："
echo " 1) 用小程序管理员微信扫码登录"
echo " 2) 导入 apps/user-uniapp/dist/build/mp-weixin（AppID 会自动读取）"
echo " 3) 详情 → 本地设置 → 勾选「不校验合法域名」（本地联调阶段）"
echo " 4) 真机预览时把 API 改成公网 HTTPS 地址，并在后台配置服务器域名"
echo ""
echo " 详细步骤见 docs/接入微信公众平台.md"
