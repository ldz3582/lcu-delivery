# 聊城大学校园外卖小程序 · 一期工程

配套文档：《聊大外卖小程序_开发行动手册.docx》。这里是可以直接跑起来的一期骨架。

## 目录

```
lcu-delivery/
├── server/                 Spring Boot 3.5 + MyBatis-Plus 后端（JDK 21 目标字节码）
├── apps/
│   ├── user-uniapp/        用户端小程序（uni-app Vue3，8 个页面）
│   ├── workbench-uniapp/   商家 / 骑手工作台（uni-app Vue3，7 个页面）
│   └── admin-web/          管理后台（Vue3 + Element Plus，7 个页面）
├── ops/
│   ├── docker-compose.yml  MySQL 8.4 + Redis 7.4 + MinIO
│   ├── sql/                schema.sql（29 表）/ seed.sql（开关）/ demo.sql（演示数据）
│   ├── dev.sh              一键启动
│   └── smoke-test.sh       端到端冒烟测试
└── docs/
    ├── API.md              接口清单（50 个）
    ├── gen_api.py          从 Controller 注解生成接口文档
    └── gen_entities.py     从字段定义生成实体/Mapper
```

## 接入真实微信小程序（拿 AppID 之后）

```bash
./ops/use-wechat.sh wx你的AppID 你的AppSecret      # 校验 → 写配置 → 改 manifest → 重建 → 重启并自检
```

完整步骤（注册、拿密钥、开发者工具、域名备案、上线四件套、错误码）见
[`docs/接入微信公众平台.md`](docs/接入微信公众平台.md)。

## 快速开始

```bash
cd ops
./dev.sh                 # 起 MySQL/Redis + 构建并启动后端
./smoke-test.sh          # 另开终端，跑完整链路（可选 ADMIN_PASSWORD=... 测后台）
```

三个前端：

```bash
# 管理后台（已配好 /api 代理）
cd apps/admin-web && npm install && npm run dev      # http://127.0.0.1:5174

# 小程序：先构建，再用微信开发者工具导入 dist/build/mp-weixin
cd apps/user-uniapp && npm install && npm run build:mp-weixin
cd apps/workbench-uniapp && npm install && npm run build:mp-weixin
```

小程序开发时用 `npm run dev:mp-weixin` 可热更新；导入目录选 `dist/dev/mp-weixin`。
开发者工具里勾选「不校验合法域名」，因为本地接口是 `http://127.0.0.1:8080`。

真机预览时手机访问不到 127.0.0.1：复制 `apps/user-uniapp/.env.example` 为 `.env`，
把 `VITE_API_BASE` 改成电脑的局域网 IP（如 `http://192.168.1.10:8080`）后重新构建。

## 演示账号（本地 mock 模式）

| 角色 | 登录方式 | 账号 |
| --- | --- | --- |
| 用户 | 小程序自动登录 | 首次进入自动生成 openid |
| 商家 | 工作台 → 商家 | `mock_merchant` |
| 骑手 | 工作台 → 骑手 | `mock_rider` |
| 管理员 | 后台登录页 | `admin` / `Admin@123456` |

管理员密码只在 `admin_user` 表为空时创建；已有数据时改库或删行重跑。

## 已实现的功能（一期闭环）

- 登录：mock 登录（无 AppID 可用）+ 真实 `jscode2session` 通道（`WechatClient`）
- 地址：增删改查、默认地址、**粘贴解析**（正则 + 置信度）、坐标入库
- 围栏：数据库存多边形，内存射线法判定；保存地址软提示、下单硬拦截（返回 2001）
- 店铺：分类、列表（距离排序/搜索）、详情、商品
- 购物车：服务端算价（商品 + 打包 + 配送 − 优惠），起送价校验
- 下单：服务端复核价格、条件更新扣库存防超卖、订单号生成、地址快照
- 支付：预支付 + 模拟支付回调（幂等），真实微信支付 v3 适配器留好接口
- 商家：接单 / 拒单（自动退款）/ 出餐、营业开关、菜品增改与上下架
- 骑手：抢单（并发保护 + 同时接单上限）、取餐、送达、位置上报、收益归属
- 结算：完成即生成结算单，T+N 冻结期满 `SettleJob` 入钱包并记流水（幂等）
- 后台：登录、概览、商家/骑手审核、订单查询、结算列表、平台开关、围栏查看与更新
- 定时任务：未支付超时关单（每分钟）、结算（每 5 分钟）

## 与生产的差距（按手册第四章补齐）

1. **真实微信登录**：`app.mock-login=false` + 填 `WX_USER_APPID/SECRET`
2. **真实支付**：按 `WechatPayGateway` 注释接入官方 SDK，`app.mock-pay=false`
3. **Redis 缓存**：目前开关走内存缓存，规模化后换 Redis
4. **对象存储**：图片先存 URL，上传接口接 COS/OSS
5. **订阅消息**：模板 ID 到位后接 `notify_log` 表发送
6. **代码审核材料**：隐私指引、类目、测试账号（见手册 2.3 / 4.5）

## 验证记录

- 后端 `mvn package` 通过；`/actuator/health` = UP
- 库表在 MySQL 8.4 实机创建 29 张，demo 数据正常
- `ops/smoke-test.sh` 全绿：下单 → 支付 → 接单 → 出餐 → 抢单 → 取餐 → 送达 → 已完成，围栏拦截返回 2001
- 用户端 / 工作台 `uni build -p mp-weixin` 通过；管理后台 `npm run build` 通过
