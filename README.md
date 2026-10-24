# 聊城大学校园外卖小程序 · 一期工程

配套文档见 [`docs/design/`](docs/design)：设计方案与开发行动手册（Word）。这里是可以直接跑起来的一期骨架。

## 目录

```
lcu-delivery/
├── server/                 Spring Boot 3.5 + MyBatis-Plus 后端（JDK 21 目标字节码）
├── apps/
│   ├── lcu-mobile/         ★ 统一小程序：用户/商家/骑手/管理员 四端合一（19 个页面）
│   ├── user-uniapp/        [已废弃] 原用户端，已并入 lcu-mobile
│   ├── workbench-uniapp/   [已废弃] 原商家/骑手工作台，已并入 lcu-mobile
│   └── admin-web/          管理后台（Vue3 + Element Plus，11 个页面：+优惠券/售后仲裁/评价/消息日志）
├── ops/
│   ├── docker-compose.yml  MySQL 8.4 + Redis 7.4 + MinIO
│   ├── sql/                schema.sql（29 表）/ seed.sql（开关）/ demo.sql（演示数据）
│   ├── dev.sh              一键启动
│   └── smoke-test.sh       端到端冒烟测试
├── starter/                启动包：30 分钟拉起依赖服务 + 29 张表的独立最小版本
├── tools/docgen/           行动手册 docx 的生成脚本（python-docx）
└── docs/
    ├── API.md              接口清单（123 个，含评价/售后/券/订阅/收藏/商家资金/骑手派单/上传）
    ├── gen_api.py          从 Controller 注解生成接口文档
    ├── gen_entities.py     从字段定义生成实体/Mapper
    └── design/             设计方案.docx、开发行动手册.docx
```

## 交付物

| 内容 | 位置 | 说明 |
| --- | --- | --- |
| 设计方案 | `docs/design/聊大外卖小程序设计方案.docx` | 需求、角色、数据模型 |
| 开发行动手册 | `docs/design/聊大外卖小程序_开发行动手册.docx` | 9 章 + 附录，含环境、接口、上线、验收 |
| 手册生成脚本 | `tools/docgen/` | 改内容后 `python build.py` 重新出 Word |
| 启动包 | `starter/` | `docker compose up -d` 拉起 MySQL/Redis/MinIO 并建表 |
| 一期代码 | `server/` `apps/` | 后端 + 四端小程序 + 管理后台 |

> `starter/` 是手册配套的最小启动包；日常开发请用演进的 `ops/`。
> 仓库内只有 `.env.example` 占位符，真实密钥（AppSecret、APIv3、证书）一律不入库。


## 统一小程序（lcu-mobile）

一个小程序，内部切换四种身份，**不再需要装两个**：

| 身份 | 入口首页 | 底部导航 |
| --- | --- | --- |
| 用户 | `pages/index/index` | 首页 / 购物车 / 订单 / 我的 |
| 商家 | `pages/merchant/orders` | 接单 / 菜品 / 店铺 / 我的 |
| 骑手 | `pages/rider/pool` | 抢单 / 配送 / 收益 / 我的 |

切换方式：「我的 → 切换身份」，或登录页直接选身份。

> **管理员只在 web 后台**（`apps/admin-web`，http://127.0.0.1:5174），
> 小程序端已彻底移除管理员身份与相关页面，只服务 用户 / 商家 / 骑手。
> 用户登录后在后台「用户管理」页可见（昵称、openid、核验状态、下单数）。

> **本地演示账号**：商家端登录页可以直接选店（一食堂·黄焖鸡 / 二食堂·麻辣香锅 / … 共 11 家），
> 对应账号 `mock_merchant`、`mock_merchant_2` … `mock_merchant_11`；骑手端 `mock_rider`。
> 已登录状态下也可以从「我的 → 切换店铺 / 切换骑手账号」重新选，不用退出登录。
> 账号清单来自 `GET /api/auth/mock-accounts?role=MERCHANT`（只在 `app.mock-login=true` 时有数据）。

> 微信原生 tabBar 的页面是编译期写死的，做不到四套导航，所以底部栏用
> `components/AppTabBar.vue` + `uni.reLaunch` 自己实现；每个页面在 `onShow`
> 里用 `guard(role)` 做身份守卫，越权会自动跳回自己的首页。


## 用户端功能覆盖（对照需求清单）

| 需求 | 状态 | 实现位置 |
| --- | --- | --- |
| 商家浏览 / 分类筛选（食堂·小吃·饮品·超市）/ 校区切换 | ✅ | `GET /api/shops`、`/api/categories` |
| 菜品搜索（搜店名**或菜名**，卡片回显命中的菜） | ✅ | `ShopService.list`，命中菜名反查店铺并带 `matchedProducts` |
| 收藏店铺 | ❌ 未做 | 无表无接口（二期） |
| 购物车 / 下单 | ✅ | 本地 Pinia + `POST /api/cart/calculate` 服务端算价、库存原子扣减、围栏与起送拦截 |
| 微信支付 | ⚠️ 仅模拟 | `WechatPayGateway` 是待接 SDK 的空壳；本地 `app.mock-pay=true` 走 mock |
| 优惠券 / 满减 | ✅ | 领券中心、我的券、算价抵扣、下单核销、取消/退款自动返券；后台可发券上下架 |
| 订单追踪（接单→出餐→取餐→送达） | ✅ | `GET /api/orders/{id}/track`：六节点时间轴 + 状态流水，前端 5 秒轮询 |
| 查看骑手位置 | ✅ | 骑手上报 `rider_location`，用户端 track 返回最新坐标 + 地图/连线 + ETA |
| 订单评价 | ✅ | 一单一评、店铺/骑手评分、商家回复、后台隐藏；写入后重算店铺与骑手均分 |
| 售后申请（少餐/漏餐/变质/撒漏/地址错误/其他） | ✅ | 工单 + 责任方 + 图片；申请后订单进「售后中」 |
| 退款 | ✅（模拟通道） | 用户自助退（未完成单）、商家同意退、平台仲裁部分/全额退，均落 `refund` 流水且累计不超实付 |
| 消息订阅（订单状态 / 优惠券） | ⚠️ 代码就绪待模板 | `NotifyService` + `notify_log`；配好 `wx.template.*` 与 AppID/Secret 即真实下发，否则只记日志 |

> 用户端 14 项：9 项完整、2 项「代码就绪但缺微信侧配置」（真实支付、订阅模板）、1 项未做（收藏店铺）、
> 2 项为基础设施（购物车、下单）。三批联调脚本：`ops/smoke-test.sh`（18 步，含越权退款防护）。

## 商家端功能覆盖（对照需求 2.2）

| 需求 | 状态 | 实现位置 |
| --- | --- | --- |
| 入驻：营业执照 / 食品经营许可证 / 门店照片 + 平台审核 | ✅ | `POST /api/merchant/apply`、`GET /api/merchant/status`、`POST /api/admin/shops/{id}/audit`（驳回原因可回传，改资料可重提） |
| 店铺信息管理 + 一键暂停营业 | ✅ | `PUT /api/merchant/shop`；关店后下单返回 `2003` |
| 菜品：新增/上下架/价格/库存/图片/分类 | ✅ | `POST|PUT /api/merchant/products` + `POST /api/upload`（本地磁盘，接口与 OSS 一致） |
| 菜品：规格 SKU | ✅ | `product_sku` 表 + `/api/merchant/products/{id}/skus`；下单按规格价与规格库存，取消自动回滚 |
| 菜品：套餐 | ⚠️ 简化 | `product.combo_items`（JSON 组成）；只扣套餐自身库存，**不联动扣组成商品库存** |
| 订单接收 / 接单 / 拒单 / 标记出餐 | ✅ | 10s 轮询 + 下拉刷新；未通过审核的商家会被准入守卫挡回入驻页 |
| 小票打印 | ❌ 未做 | 需蓝牙小票机或云打印账号（易联云/飞鹅），属外部依赖，建议二期 |
| 营销：满减 / 折扣 | ✅ | 商家自建店铺券 `/api/merchant/coupons`（`scope=2`，只对本店生效）；平台券仍在后台发 |
| 营销：特价商品 | ✅ | `product.origin_price` 划线价 + 现价 |
| 订单对账 / 营收统计 | ✅ | `/api/merchant/statements`（按日筛选 + 汇总）、`/api/merchant/summary`（今日/本周/本月 GMV、佣金、实收、客单价） |
| 平台佣金自动扣除 | ✅ | 下单即算 `commission`，订单完成生成结算单，T+N 入钱包 |
| 提现申请 / 记录 / 平台审核 | ✅ | `/api/merchant/withdrawals`（申请即冻结）、`/api/admin/withdrawals`（通过释放冻结 / 驳回退回余额） |

> 商家端资金目前是**模拟打款**：审核通过只把冻结金额释放并置「已到账」，真实环境要接微信「商家转账到零钱」。

## 骑手端功能覆盖（对照需求 2.3）

| 需求 | 状态 | 实现位置 |
| --- | --- | --- |
| 注册 + 身份核验（学号/身份证/校园卡照片） | ✅ | `POST /api/rider/apply`，姓名/身份证/学号 **AES-GCM 加密**入库 + 学号 SHA-256 查重；三证照片走 `/api/upload` |
| 兼职协议签署 + 意外险 | ⚠️ 半 | 勾选同意即记录签署时间与协议存证 URL；**意外险只存保单号**，实际投保是运营动作 |
| 抢单 + 后台智能派单双模式 | ✅ | 高峰时段（`delivery.dispatch_peak_windows`）按「距离 → 在手单量 → 评分」规则派单，骑手 2 分钟未确认自动退回；闲时仍走抢单大厅 |
| 订单列表：待取餐/配送中/已完成/异常 | ✅ | `GET /api/rider/orders?status=` + 异常单独列表 `/api/rider/incidents` |
| 校园地图导航 | ✅ | 骑手点「导航到店/到收货点」调 `uni.openLocation`（后端返回店铺与收货点坐标） |
| 取餐/送达拍照确认 | ✅ | 必须真实拍照上传，后端缺照片直接 `1001` 拒绝（原来是写死 example.com 占位图） |
| 实时轨迹上报 | ✅ | 抢单页 15s 一次 `POST /api/rider/location`，用户端 5s 轮询看到骑手位置 |
| 订单异常上报 | ✅ | 联系不上用户 / 餐品撒漏 / 地址错误 / 其他，带图片，后台可处置 |
| 佣金明细 / 可提现余额 / 提现 / 账单 / 处罚 | ✅ | `/api/rider/wallet`、`/settlements`、`/summary`、`/withdrawals`、`/penalties`；超时与差评自动开罚单 |

> 派单是**规则版**不是算法模型：`delivery.dispatch_enabled` 开关 + 高峰时段配置，后台随时可调；不想要派单就关掉，回到纯抢单。
> 骑手提现与商家共用 `withdraw` 表（`owner_type=3`），后台「提现审核」页统一处理。

## 订阅消息配置（不配也能跑，只是不发真消息）

1. 公众平台 → 订阅消息 → 选「订单状态」「优惠券到账」类目模板，复制模板 ID
2. 后台「配置」页填：`wx.template.order_status`、`wx.template.coupon`
3. `ops/.env` 里填 `WX_USER_APPID` / `WX_USER_SECRET` 后重启
4. 没配时：`GET /api/notify/templates` 返回 `configured=false`，小程序端静默跳过授权弹窗；
   每次本该发送的消息都会在 `notify_log` 留一条 `status=2 + 原因`，后台「消息日志」页可查


## 接入真实微信小程序（拿 AppID 之后）

```bash
./ops/use-wechat.sh wx你的AppID 你的AppSecret      # 校验 → 写配置 → 改 manifest → 重建 → 重启并自检
```

完整步骤（注册、拿密钥、开发者工具、域名备案、上线四件套、错误码）见
[`docs/接入微信公众平台.md`](docs/接入微信公众平台.md)。

## 平台开关（后台即时生效，不用重启）

| 配置键 | 默认 | 作用 |
| --- | --- | --- |
| `feature.identity_check` | `false` | 校内身份核验。关：不限制非本校人员注册下单；开：未核验用户下单返回 `2005` |
| `feature.address_parse` | `true` | 地址粘贴解析 |
| `delivery.fence_enforce` | `true` | 下单强制校验校园围栏，超出返回 `2001` |
| `delivery.peak_bonus` 等 | — | 计价、结算、并发上限等 |

改法：管理后台「配置」页直接切开关（`el-switch`），或
`PUT /api/admin/configs {"key":"feature.identity_check","value":"true"}`。
`SysConfigService` 更新时会**同时写库和内存缓存**，下一个请求立即生效。

### 校内身份核验

- 登录页启动时调 `GET /api/identity/config`（公开接口），开关开启才显示「学号/工号」输入框
- 核验接口 `POST /api/identity/verify {"stuNo":"..."}`：6~20 位字母数字，AES-GCM 加密存 `stu_no_enc`，
  同时存 `stu_no_hash`（SHA-256 + 唯一索引）用于查重——密文含随机 IV 不可比较，必须用哈希
- 拦截点在**下单**而不是登录，避免把用户卡在登录环节
- 「我的」页可随时补核验，显示脱敏号码（`2021****4567`）

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
