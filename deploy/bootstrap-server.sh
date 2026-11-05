#!/usr/bin/env bash
# 服务器侧一次性基线：swap / Docker / 镜像加速 / 目录
# 由 deploy.sh 通过 ssh 调用，可重复执行（幂等）
set -uo pipefail

APP_DIR="${APP_DIR:-/opt/lcu}"
log() { printf '\033[36m▶ %s\033[0m\n' "$*"; }
warn() { printf '\033[33m! %s\033[0m\n' "$*"; }

log "系统信息"
. /etc/os-release 2>/dev/null || true
echo "  ${PRETTY_NAME:-unknown} / $(uname -m) / $(nproc) vCPU"
echo "  内存 $(free -g | awk '/Mem:/{print $2}')G 可用 $(free -g | awk '/Mem:/{print $7}')G"
echo "  磁盘 $(df -h / | awk 'NR==2{print $4}') 可用"

# ---------- 1. swap（2G 内存机器必开，否则 MySQL + JVM 容易 OOM） ----------
if ! swapon --show | grep -q .; then
  log "创建 2G swap"
  if [ ! -f /swapfile ]; then
    fallocate -l 2G /swapfile 2>/dev/null || dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
    chmod 600 /swapfile
    mkswap /swapfile >/dev/null
  fi
  swapon /swapfile || warn "swapon 失败"
  grep -q '/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
  sysctl -w vm.swappiness=10 >/dev/null 2>&1 || true
else
  log "swap 已存在"
fi
swapon --show

# ---------- 2. Docker ----------
# 坑：Alibaba Cloud Linux 3 系统源里的 `docker` 是 podman-docker 的 shim，
#     装完没有 docker.service，compose 会去找 /run/podman/podman.sock。
#     判定标准：必须有 docker.service 且 docker info 能拿到 ServerVersion。
docker_ok() { systemctl list-unit-files 2>/dev/null | grep -q '^docker.service' && docker info >/dev/null 2>&1; }

if docker_ok; then
  log "Docker 已就绪：$(docker --version)"
else
  if rpm -q podman-docker >/dev/null 2>&1; then
    log "移除 podman-docker shim（假 docker）"
    dnf remove -y podman-docker podman >/dev/null 2>&1 || true
  fi
  log "安装 Docker CE（阿里云镜像源）"
  dnf install -y dnf-plugins-core >/dev/null 2>&1 || true
  mkdir -p /etc/yum.repos.d
  # Alibaba Cloud Linux 3 的 $releasever 是 3，CentOS 8 仓库不认，这里写死 8
  cat > /etc/yum.repos.d/docker-ce.repo <<'EOF'
[docker-ce-stable]
name=Docker CE Stable - mirrors.aliyun.com
baseurl=https://mirrors.aliyun.com/docker-ce/linux/centos/8/$basearch/stable
enabled=1
gpgcheck=1
gpgkey=https://mirrors.aliyun.com/docker-ce/linux/centos/gpg
EOF
  dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin >/dev/null || {
    echo "FATAL: docker-ce 安装失败"; exit 1; }
fi

systemctl enable --now docker >/dev/null 2>&1 || true
docker_ok || { echo "FATAL: docker 守护进程未就绪"; exit 1; }
log "docker info: $(docker info --format '{{.ServerVersion}} / {{.Driver}}')"

# ---------- 3. compose ----------
if docker compose version >/dev/null 2>&1; then
  log "compose 插件：$(docker compose version --short)"
else
  warn "缺少 docker compose 插件，尝试补装"
  dnf install -y docker-compose-plugin >/dev/null 2>&1 || true
  if ! docker compose version >/dev/null 2>&1; then
    mkdir -p /usr/local/lib/docker/cli-plugins
    curl -fsSL --max-time 120 -o /usr/local/lib/docker/cli-plugins/docker-compose \
      https://mirrors.aliyun.com/docker-toolbox/linux/compose/v2/docker-compose-linux-x86_64 2>/dev/null || \
    curl -fsSL --max-time 120 -o /usr/local/lib/docker/cli-plugins/docker-compose \
      https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 || true
    chmod +x /usr/local/lib/docker/cli-plugins/docker-compose 2>/dev/null || true
  fi
  docker compose version >/dev/null 2>&1 || { echo "FATAL: docker compose 不可用"; exit 1; }
fi

# ---------- 4. 国内镜像加速 ----------
if [ ! -f /etc/docker/daemon.json ] || ! grep -q registry-mirrors /etc/docker/daemon.json; then
  log "写入 registry 镜像加速"
  mkdir -p /etc/docker
  cat > /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": [
    "https://docker.m.daocloud.io",
    "https://dockerproxy.net",
    "https://docker.1panel.live"
  ],
  "log-driver": "json-file",
  "log-opts": { "max-size": "20m", "max-file": "3" }
}
EOF
  systemctl restart docker >/dev/null 2>&1 || true
  sleep 3
else
  log "daemon.json 已有 registry-mirrors，跳过"
fi

# ---------- 5. 目录 ----------
log "准备目录 $APP_DIR"
mkdir -p "$APP_DIR"/{data/mysql,data/redis,uploads,admin,init,nginx}

# ---------- 6. rsync（deploy.sh 上传依赖它） ----------
if ! command -v rsync >/dev/null 2>&1; then
  log "安装 rsync"
  dnf install -y rsync >/dev/null 2>&1 || warn "rsync 安装失败，deploy.sh 会退回 tar 上传"
fi
command -v rsync >/dev/null 2>&1 && log "rsync：$(rsync --version | head -1)"
log "完成"
