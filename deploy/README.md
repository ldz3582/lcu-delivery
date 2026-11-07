# 部署到阿里云 ECS（单机 Docker）

目标机器：阿里云 ECS `2 vCPU / 2 GiB`，Alibaba Cloud Linux 3.2104 LTS 64 位，公网 IP 见下。
一台机器跑完：MySQL 8.4 + Redis 7.4 + Spring Boot 后端 + Nginx（管理后台静态站 + `/api` 反代）。

```
ECS (47.102.49.46)
└── Docker
    ├── lcu-mysql   3306（仅容器网络）
    ├── lcu-redis   6379（仅容器网络）
    ├── lcu-api     8080（仅容器网络）
    └── lcu-web     80 → / 管理后台、/api 后端、/uploads 图片、/health
```

## 一条命令部署

```bash
cd lcu-delivery
SERVER=root@47.102.49.46 ./deploy/deploy.sh
```

脚本做的事（可重复执行）：

1. 本机构建后端 jar（`server/target/delivery-0.1.0.jar`）+ 管理后台 `dist`
2. 组装 `deploy/.stage/`：jar、dist、compose、nginx、SQL 初始化脚本
3. `rsync` 到服务器 `/opt/lcu`（不覆盖 `data/`、`uploads/`、`.env`）
4. 服务器基线：**2G swap**、Docker + compose 插件、registry 镜像加速、目录
5. 首次生成 `.env`（随机数据库/Redis 密码、随机 JWT/加密密钥）
6. `docker build` 出镜像 + `docker compose up -d`，然后 `http://<IP>/health` 轮询直到 UP

只想换 jar 复用构建：`SERVER=root@47.102.49.46 ./deploy/deploy.sh --reuse-jar`

## ⚠️ 部署前必做：安全组放通 80

部署成功 ≠ 能打开。阿里云默认安全组只有 22/3389/ICMP，**80 必须手动加**：

实例页 → 网络与安全组 → 安全组 → 配置规则 → 入方向 → 快速添加规则 →
勾选 **Web HTTP 流量访问**（和 **Web HTTPS 流量访问**）→ 确定。

判定方法（花 1 秒）：

```bash
nc -vz 47.102.49.46 80     # 通 = 好了
```

> 2026-11-06 实测：安全组未放通时，**服务器自己 curl 自己的公网 IP 也超时**，
> 但 `ss -ltnp | grep :80` 能看到 docker-proxy 在监听——这个组合就是安全组问题，
> 不要去改 Nginx 或 iptables。

## 部署后

| 地址 | 说明 |
| --- | --- |
| `http://47.102.49.46/` | 管理后台，`admin` / `<部署时生成的强密码，见服务器 /opt/lcu/.env>`（多角色：`kefu`/`caiwu`/`shenhe` 同密码） |
| `http://47.102.49.46/api/shops` | 后端接口自检 |
| `http://47.102.49.46/health` | 健康检查（反代 `/actuator/health`） |

实测速度（从外网）：`/` 86ms，`/api/shops` 450ms，`/api/categories` 166ms。
外网跑完整 `ops/smoke-test.sh` 27 步全通过。

调试命令（在服务器 `/opt/lcu`）：

```bash
docker compose ps
docker compose logs -f --tail=100 api
docker compose restart api
docker compose down            # 停（数据在 ./data，不丢）
```

## 关键约定（踩过的坑）

- **SQL 顺序**：`schema.sql → migration-*.sql → seed.sql → demo.sql`。
  实测 demo.sql 写在迁移之前会因 `banner` 表不存在直接中断，后端随后因缺 `notice` 列 500。
- **初始化只在空库执行**：`init/` 挂到 `/docker-entrypoint-initdb.d`，MySQL 数据目录非空时被忽略。
  要重灌数据：`docker compose down && rm -rf data/mysql && docker compose up -d`。
- **admin 初始密码**：`admin_user` 为空时后端会创建 `admin`，默认随机密码只打在日志里。
  部署包通过 `.env` 的 `ADMIN_INIT_PASSWORD` 固定成 `<部署时生成的强密码，见服务器 /opt/lcu/.env>`，演示才可登录。
- **2G 内存**：JVM `-Xmx480m`、MySQL buffer pool 128M、Redis maxmemory 96M，并强制 2G swap。
- **图片**：存在容器卷 `./uploads`，对外走 `/uploads/**`，`APP_PUBLIC_BASE_URL` 留空即按请求 Host 拼。

## 小程序端（不在这台服务器上）

小程序代码不能「部署」到 ECS，只能上传到微信平台：

1. `apps/lcu-mobile/.env.production` 填 `VITE_API_BASE=https://<已备案域名>`（微信要求 HTTPS + 备案 + 后台「服务器域名」白名单）
2. 微信开发者工具导入 `apps/lcu-mobile`，或用 `ops/publish-wechat.sh build|upload`
3. 目前只有公网 IP，`http://47.102.49.46` 只能给 **H5 预览 / 管理后台** 用；
   真机小程序需要先备案域名 + HTTPS，部署包已留好 Nginx 443 扩展位（加证书即可）

## 回滚

```bash
ssh root@47.102.49.46 'cd /opt/lcu && ls -lh delivery.jar && docker compose restart api'
```

旧 jar 建议在服务器上留一份：`cp delivery.jar delivery.jar.bak.$(date +%F-%H%M)`。
