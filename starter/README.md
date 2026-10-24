# 聊大外卖小程序 · 启动包（starter）

配套文档：《聊大外卖小程序_开发行动手册.docx》。这个目录只做一件事：让你在 30 分钟内把**本地开发环境**跑起来，并拿到一套能直接用的库表结构。

## 目录

```
starter/
├── docker-compose.yml   # MySQL 8.4 + Redis 7.4 + MinIO
├── .env.example         # 所有环境变量模板
├── sql/
│   ├── schema.sql       # 29 张核心表
│   └── seed.sql         # 分类、平台开关、围栏示例、后台初始账号
└── README.md
```

## 一、启动依赖服务

```bash
# 1. 确认 Docker Desktop 已启动
docker info

# 2. 准备环境变量（不要提交 .env 到 Git）
cp .env.example .env

# 3. 启动
docker compose up -d

# 4. 看状态：三个服务都应为 healthy
docker compose ps
```

首次启动会自动执行 `sql/schema.sql` 与 `sql/seed.sql`（仅当数据卷为空时执行）。

### 连接信息

| 服务 | 地址 | 账号 | 密码 |
| --- | --- | --- | --- |
| MySQL | 127.0.0.1:3306 | lcu | lcu123456 |
| MySQL(root) | 127.0.0.1:3306 | root | root123456 |
| Redis | 127.0.0.1:6379 | - | redis123456 |
| MinIO 控制台 | http://127.0.0.1:9001 | minioadmin | minioadmin123 |

### 实测结果（2026-10-23，MySQL 8.4.13）

```
tables_cnt = 29      -- 29 张表全部创建成功
cfg_cnt    = 13      -- 平台开关
cat_cnt    = 4       -- 店铺分类
fence 判定  = 校内点命中、校外点不命中
```

### 两个容易卡住的点

1. **3306 被占用**：报 `address already in use` 时，改 `.env` 的 `MYSQL_PORT=3307`，连接串同步改端口。
2. **docker pull 超时**：直连 Docker Hub 不可用时，用云厂商镜像加速器，或临时用公共镜像站前缀（如 `docker.m.daocloud.io/library/mysql:8.4`，再 `docker tag` 成本地 `mysql:8.4`）。

### 常用命令

```bash
# 进 MySQL
docker exec -it lcu-mysql mysql -ulcu -plcu123456 lcu_delivery

# 进 Redis
docker exec -it lcu-redis redis-cli -a redis123456

# 看日志
docker compose logs -f mysql

# 彻底重置（会删数据）
docker compose down -v && rm -rf data/ && docker compose up -d
```

## 二、验证库表

```sql
-- 应返回 29 张表
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema='lcu_delivery' AND table_type='BASE TABLE';

-- 围栏判定示例（把坐标换成校内一点）
SELECT id, name FROM fence
WHERE status=1 AND ST_Contains(polygon, POINT(115.9900, 36.4380));
```

## 三、下一步

1. 后端：`curl https://start.spring.io/starter.zip -d dependencies=web,validation,data-redis,mysql,lombok,actuator -o delivery.zip`，然后把 `src/main/resources/application.yml` 指向上面这套连接串。
2. 小程序：`npx degit dcloudio/uni-preset-vue#vite-ts campus-user`，`pnpm i && pnpm dev:mp-weixin`，用微信开发者工具导入 `dist/dev/mp-weixin`。
3. 后台账号：不要在 SQL 里写死密码哈希。让 Spring Boot 首次启动时检查 `admin_user` 是否为空，为空则生成 12 位随机密码、打印到日志并强制首次登录改密。

4. 围栏：先打开腾讯地图坐标拾取器，把东、西校区实际边界取点（每 10–20 米一个点即可），替换 `seed.sql` 里的示例多边形。

> 所有 `.env` 里的密钥只用于本地。生产环境的 AppSecret、APIv3 密钥、商户证书必须放云 KMS / 服务器密钥管理，不能进 Git。
