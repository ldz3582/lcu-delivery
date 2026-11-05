#!/usr/bin/env bash
# 服务器侧：首次生成 .env（随机密钥），已存在则不动
set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  echo "  沿用已有 .env（如需轮换密钥请手工编辑）"
  exit 0
fi

[ -f .env.example ] || { echo "FATAL: 缺少 .env.example"; exit 1; }

rand() { openssl rand -hex "${1:-16}"; }

MYSQL_ROOT_PASSWORD="$(rand 16)"
MYSQL_PASSWORD="$(rand 16)"
REDIS_PASSWORD="$(rand 16)"
JWT_SECRET="$(rand 32)"
APP_CRYPTO_KEY="$(rand 16)"

sed -e "s|^MYSQL_ROOT_PASSWORD=.*|MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD}|" \
    -e "s|^MYSQL_PASSWORD=.*|MYSQL_PASSWORD=${MYSQL_PASSWORD}|" \
    -e "s|^REDIS_PASSWORD=.*|REDIS_PASSWORD=${REDIS_PASSWORD}|" \
    -e "s|^JWT_SECRET=.*|JWT_SECRET=${JWT_SECRET}|" \
    -e "s|^APP_CRYPTO_KEY=.*|APP_CRYPTO_KEY=${APP_CRYPTO_KEY}|" \
    .env.example > .env
chmod 600 .env
echo "  已生成 .env（随机密钥，数据库密码见该文件）"
