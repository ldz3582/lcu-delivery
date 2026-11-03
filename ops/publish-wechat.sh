#!/usr/bin/env bash
# 微信小程序 发布流水线：体检 → 生产构建 → 上传 / 出体验版二维码
#
# 用法：
#   ./ops/publish-wechat.sh check                       # 只体检，不改任何东西
#   ./ops/publish-wechat.sh build https://api.xxx.com   # 用生产域名构建，并校验产物
#   ./ops/publish-wechat.sh preview ~/wx.key            # 构建 + 出体验版二维码（手机扫码就能看）
#   ./ops/publish-wechat.sh upload  ~/wx.key 1.0.0 "首个版本"   # 构建 + 上传到「开发版本」，之后网页提审
#
# 密钥从哪来：小程序后台 → 开发管理 → 开发设置 → 小程序代码上传 → 生成代码上传密钥
#            同一页要把本机公网 IP 加到「IP 白名单」，否则上传会报 40164。
set -euo pipefail
cd "$(dirname "$0")/.."

ROOT="$(pwd)"
APP_DIR="apps/lcu-mobile"
DIST="$APP_DIR/dist/build/mp-weixin"
ENV_FILE="$APP_DIR/.env.production"
MANIFEST="$APP_DIR/src/manifest.json"

C_OK="\033[32m"; C_NO="\033[31m"; C_WARN="\033[33m"; C_DIM="\033[2m"; C_OFF="\033[0m"
ok()   { printf "  ${C_OK}✓${C_OFF} %s\n" "$1"; }
no()   { printf "  ${C_NO}✗${C_OFF} %s\n" "$1"; }
warn() { printf "  ${C_WARN}!${C_OFF} %s\n" "$1"; }
dim()  { printf "    ${C_DIM}%s${C_OFF}\n" "$1"; }

appid() { python3 -c "import json;print(json.load(open('$MANIFEST'))['mp-weixin']['appid'])"; }
api_base() { [ -f "$ENV_FILE" ] && sed -n 's/^VITE_API_BASE=//p' "$ENV_FILE" | tail -1 || echo ""; }

# ───────────────────────────────────────── 体检
do_check() {
  echo "微信小程序发布体检"
  echo "────────────────────────────────────────"
  local id; id=$(appid)
  if [[ "$id" =~ ^wx[0-9a-f]{16}$ ]]; then ok "AppID：$id"; else no "AppID 还不是真实的：$id"; dim "跑 ./ops/use-wechat.sh <AppID> <AppSecret>"; fi

  if [ -f "$DIST/app.json" ]; then
    local n; n=$(python3 -c "import json;print(len(json.load(open('$DIST/app.json'))['pages']))")
    ok "已有构建产物（$n 个页面）"
  else
    no "还没有构建产物"; dim "跑 ./ops/publish-wechat.sh build <API域名>"
  fi

  local base; base=$(api_base)
  if [ -z "$base" ]; then
    no "没配生产 API 域名（$ENV_FILE 不存在）"; dim "跑 ./ops/publish-wechat.sh build https://api.你的域名.com"
  elif [[ "$base" == http://* ]] || [[ "$base" == *127.0.0.1* ]] || [[ "$base" == *example.com* ]]; then
    no "API 域名不是线上可用的：$base"; dim "正式发布必须是已备案的 https:// 域名；只给朋友试可以用局域网 IP + 体验版调试开关"
  else
    ok "生产 API：$base"
  fi

  echo ""
  echo "需要你在微信公众平台后台完成的事（脚本做不了）："
  echo "  □ 主体已认证（个体工商户 / 企业）—— 个人主体不能选外卖类目、不能开支付"
  echo "  □ 服务类目：餐饮服务 / 外卖平台（要《食品经营许可证》）"
  echo "  □ 小程序备案（2023-09 起强制，未备案不能发布）"
  echo "  □ 开发管理 → 服务器域名：request/uploadFile/downloadFile 填 https://API域名"
  echo "  □ 开发管理 → 接口设置：开通「地理位置」"
  echo "  □ 设置 → 服务内容声明 → 隐私保护指引：勾选 位置、相册、剪贴板"
  echo "  □ 开发管理 → 开发设置 → 小程序代码上传：生成密钥 + 配 IP 白名单"
  echo ""
  echo "⚠ 当前代码最大的拦路虎：微信支付还是空壳（WechatPayGateway 直接抛异常），"
  echo "  application-prod.yml 里 mock-pay=false，所以线上环境用户下完单付不了款。"
  echo "  详见 docs/发布微信小程序.md 的「支付怎么办」一节。"
}

# ───────────────────────────────────────── 构建
do_build() {
  local base="${1:-}"
  if [ -z "$base" ]; then
    base=$(api_base)
    [ -n "$base" ] || { no "用法: ./ops/publish-wechat.sh build https://api.你的域名.com"; exit 1; }
    echo "▶ 沿用 $ENV_FILE 里的 VITE_API_BASE=$base"
  else
    case "$base" in
      http://*|https://*) ;;
      *) no "API 地址要以 http:// 或 https:// 开头"; exit 1 ;;
    esac
    printf '# 生产构建用（uni build 自动加载）。必须是已备案 HTTPS 域名，且已在后台「服务器域名」里填过。\nVITE_API_BASE=%s\n' "$base" > "$ENV_FILE"
    echo "▶ 写入 $ENV_FILE"
  fi

  echo "▶ 构建 mp-weixin"
  ( cd "$APP_DIR" && npm run build:mp-weixin ) > /tmp/lcu-mp-build.log 2>&1 || {
    no "构建失败"; tail -25 /tmp/lcu-mp-build.log; exit 1
  }
  ok "构建完成"

  echo "▶ 校验产物"
  # 1) 127.0.0.1 / localhost 一定不通（手机上的 127.0.0.1 指手机自己）
  local hard
  hard=$(grep -rIl -E "https?://(127\.0\.0\.1|localhost)" "$DIST" 2>/dev/null | head -5 || true)
  if [ -n "$hard" ]; then
    no "产物里还有 127.0.0.1 / localhost（手机打不开）："; echo "$hard" | sed 's/^/      /'
    dim "检查 $ENV_FILE 是否生效"; exit 1
  else ok "没有残留 127.0.0.1 / localhost"; fi

  # 1b) 局域网地址：体验版能用，但正式版必挂
  local lan
  lan=$(grep -rIl -E "https?://(192\.168\.[0-9]+\.[0-9]+|10\.[0-9]+\.[0-9]+\.[0-9]+)" "$DIST" 2>/dev/null | head -3 || true)
  if [ -n "$lan" ]; then
    warn "产物连的是局域网地址（只有体验版 + 手机开「调试」才通，正式版不行）"
  fi

  # 2) 域名和声明的一致
  if grep -rq "$base" "$DIST" 2>/dev/null; then ok "API 域名已打进产物：$base"; else no "产物里找不到 $base"; exit 1; fi

  # 3) urlCheck 跟着 API 地址走：
  #    https 域名 → 开校验，跟线上一致；http/局域网 → 必须关，否则 IDE 直接把请求拦掉
  python3 - "$DIST/project.config.json" "$base" <<'PY'
import json, sys, pathlib
p = pathlib.Path(sys.argv[1]); base = sys.argv[2]
d = json.loads(p.read_text(encoding="utf-8"))
d.setdefault("setting", {})["urlCheck"] = base.startswith("https://")
if not d.get("libVersion"):
    d["libVersion"] = "3.5.5"
d.setdefault("setting", {})["es6"] = True
d.setdefault("setting", {})["minified"] = True
p.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
print("  urlCheck =", d["setting"]["urlCheck"])
PY
  if [[ "$base" == https://* ]]; then
    ok "project.config.json：urlCheck=true（域名已备案，跟线上行为一致）"
  else
    ok "project.config.json：urlCheck=false（本地/局域网联调必须关，否则请求被 IDE 拦掉）"
  fi

  # 3b) 新版开发者工具把「本地设置」存在 project.private.config.json 里，
  #     而 uni build 会清空 dist，所以每次构建都要重新写一份，否则模拟器会拿旧值把请求拦掉
  python3 - "$DIST/project.private.config.json" "$base" <<'PY'
import json, sys, pathlib
p = pathlib.Path(sys.argv[1]); base = sys.argv[2]
d = {
    "libVersion": "3.7.7",
    "projectname": "%E8%81%8A%E5%A4%A7%E5%A4%96%E5%8D%96",
    "condition": {},
    "setting": {
        "urlCheck": base.startswith("https://"),
        "compileHotReLoad": True,
        "bigPackageSizeSupport": True,
        "skylineRenderEnable": False,
    },
}
p.write_text(json.dumps(d, ensure_ascii=False, indent=2), encoding="utf-8")
PY
  ok "project.private.config.json 已写入（本地设置不会被旧值覆盖）"

  # 4) 体积
  local kb; kb=$(du -sk "$DIST" | cut -f1)
  if [ "$kb" -gt 2048 ]; then no "主包 ${kb}KB，超过 2MB 限制"; exit 1; else ok "主包体积 ${kb}KB（上限 2048KB）"; fi

  echo ""
  echo "产物：$DIST"
  echo "想在开发者工具里看一眼：微信开发者工具 → 导入项目 → 选上面这个目录"
}

# ───────────────────────────────────────── 上传 / 预览
need_ci() {
  [ -d tools/mpci/node_modules/miniprogram-ci ] || {
    echo "▶ 首次使用，安装 miniprogram-ci"
    ( cd tools/mpci && npm install --no-audit --no-fund --loglevel=error ) || { no "安装失败"; exit 1; }
  }
}

do_preview() {
  local key="${1:-}"
  [ -n "$key" ] || { no "用法: ./ops/publish-wechat.sh preview <上传密钥.key>"; exit 1; }
  do_build "${API_BASE:-}"
  need_ci
  echo "▶ 生成体验版二维码"
  node tools/mpci/ci.js preview --key "$key" --desc "预览 $(date '+%m-%d %H:%M')"
  echo ""
  echo "手机微信扫一扫 apps/lcu-mobile/dist/preview.jpg 就能打开。"
  echo "体验版要连本机后端的话：微信里点右上角「…」→ 打开调试 → 重启小程序，域名校验才会被跳过。"
}

do_upload() {
  local key="${1:-}"; local ver="${2:-}"; local desc="${3:-}"
  [ -n "$key" ] || { no "用法: ./ops/publish-wechat.sh upload <上传密钥.key> <版本号> <描述>"; exit 1; }
  [ -n "$ver" ] || { no "版本号不能为空，例如 1.0.0"; exit 1; }
  do_build "${API_BASE:-}"
  need_ci
  echo "▶ 上传到「开发版本」"
  node tools/mpci/ci.js upload --key "$key" --version "$ver" --desc "${desc:-$ver}"
}

case "${1:-}" in
  check)   do_check ;;
  build)   shift; do_build "$@" ;;
  preview) shift; do_preview "$@" ;;
  upload)  shift; do_upload "$@" ;;
  *)
    sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
    exit 1 ;;
esac
