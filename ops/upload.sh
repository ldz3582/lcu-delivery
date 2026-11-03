#!/usr/bin/env bash
# 一条命令上传小程序：自动找上传密钥 → 预检 → 上传
#
# 用法：
#   ./ops/upload.sh                 # 默认 1.0.2，描述带时间
#   ./ops/upload.sh 1.0.3 "改了什么"
#
# 前提：密钥文件放在 ~/Downloads（或在参数里给路径），且本机公网 IP 在后台白名单里
set -euo pipefail
cd "$(dirname "$0")/.."

VER="${1:-1.0.2}"
DESC="${2:-构建 $(date '+%m-%d %H:%M')}"
MANIFEST="apps/lcu-mobile/src/manifest.json"
APPID=$(python3 -c "import json;print(json.load(open('$MANIFEST'))['mp-weixin']['appid'])")

echo "▶ AppID  $APPID"
echo "▶ 版本   $VER"

KEY="${KEY:-}"
if [ -z "$KEY" ]; then
  KEY=$(ls -t "$HOME/Downloads"/private.wx*.key "$HOME"/Downloads/*.key 2>/dev/null | head -1 || true)
fi

if [ -z "$KEY" ] || [ ! -f "$KEY" ]; then
  cat <<EOF

✗ 没找到上传密钥（*.key）

密钥只能由你在后台生成，位置固定：
  1) https://mp.weixin.qq.com → 开发管理 → 开发设置
  2) 页面往下拉到「小程序代码上传」→ 点「生成」（生成过就点「重置」）
  3) 下载到的 private.wx6c9641b562b16363.key 拖到 ~/Downloads

放好后再跑一次： ./ops/upload.sh $VER

（不想用密钥：微信开发者工具导入 apps/lcu-mobile/dist/build/mp-weixin，点右上角「上传」，
  效果相同，且不需要配 IP 白名单。）
EOF
  exit 1
fi

echo "▶ 密钥   $KEY"
echo "▶ 公网IP $(curl -s -m 5 https://api.ipify.org || echo '取不到')"

if [ ! -f "${KEY%.key}.md5" ] && [ -f "apps/lcu-mobile/dist/build/mp-weixin/app.json" ]; then
  : # 产物存在，交给 ci 自检
fi

[ -f "apps/lcu-mobile/dist/build/mp-weixin/app.json" ] || {
  echo "✗ 产物不存在，先构建： ./ops/publish-wechat.sh build https://你的隧道域名"
  exit 1
}

echo "▶ 上传中…"
./ops/publish-wechat.sh upload "$KEY" "$VER" "$DESC"

cat <<EOF

════════════════════════════════════════
接下来只能你在网页上点（微信不开放接口）：
  1) https://mp.weixin.qq.com → 管理 → 版本管理 → 开发版本
  2) 找到版本 $VER → 右侧「…」→ 选为体验版本
  3) 同页「体验版本」→ 下载二维码（7 天有效）
  4) 用户身份 → 成员管理 → 体验成员 → 添加对方微信号

上传报 40164 = 本机 IP 不在白名单，去「小程序代码上传」同页加：
  $(curl -s -m 5 https://api.ipify.org || echo '取不到')
EOF
