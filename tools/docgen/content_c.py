# -*- coding: utf-8 -*-
"""第五至九章 + 附录"""


def chapter5(h):
    h.h1("五、90 天行动路线")
    h.p(
        "下面的周计划假设 3 人（1 后端 + 1 前端 + 1 兼产品/测试），资质与开发并行。"
        "如果你的团队只有 1–2 人，看 5.3 的压缩版。",
    )

    h.h2("5.1 逐周计划")
    h.table(
        ["周", "主题", "关键交付物", "验收标准"],
        [
            ["W1", "决策 + 环境", "主体确定；小程序注册与认证提交；本机环境跑通；docker compose 起来", "`actuator/health` 返回 UP；小程序能真机预览"],
            ["W1", "技术验证", "登录、定位、围栏判定、1 分钱支付与退款的 demo", "四个 demo 在真机上全部成功"],
            ["W2", "交易主干", "库表落地；用户端首页 / 店铺 / 商品 / 购物车", "能加购、能算价（含配送费、满减）"],
            ["W3", "下单支付", "下单、微信支付、订单状态机、订单列表与详情", "完整走通「下单 → 支付 → 待接单」"],
            ["W4", "商家侧", "商家接单 / 拒单 / 出餐、菜品管理、营业开关", "商家端能收到推送并出餐，订单流转到待取餐"],
            ["W5", "骑手侧", "抢单大厅、取餐/送达拍照、收益明细", "骑手能抢单、上传照片、订单完成"],
            ["W6", "资金与后台", "结算、钱包、提现申请；后台审核、订单查询、围栏与开关配置", "后台改开关，小程序实时生效"],
            ["W7", "打磨与压测", "评价、售后、订阅消息；JMeter 压测下单接口", "200 并发下 P99 < 800ms；无超卖"],
            ["W8", "灰度内测", "体验版；10–20 名师生、5 家商家、5 名骑手", "真实跑 3 天，收集 bug 并修复"],
            ["W9", "提审", "补齐隐私协议、测试账号、类目材料", "审核通过，发布 1.0"],
            ["W10–12", "试点运营", "单校区全量；每日复盘；优化派单与售后", "日均 50 单，超时率 < 10%，差评率 < 3%"],
        ],
        widths=[1.2, 2.6, 7.0, 5.6],
        caption="表 5-1　12 周计划",
    )

    h.h2("5.2 每周固定动作（别省）")
    h.bullets([
        "**周一 30 分钟**：过一遍资质进度（备案到哪一步、类目有没有被驳回）。",
        "**周三**：真机走一遍主流程，不要只在开发者工具里点。定位、支付、拍照这三件事工具里和真机差异最大。",
        "**周五**：看四个数——下单转化率、接单时长、配送时长、退款率。前三周只要能看到数就行，别急着优化。",
        "**每周一次数据备份演练**：从备份恢复到一个临时库，确认能恢复。",
    ])

    h.h2("5.3 如果只有 1–2 个人")
    h.p("把范围再砍一半，8–10 周做到「能内测」，功能按下面顺序做，做不完的直接砍：")
    h.steps([
        "登录 + 地址（先只做手动录入，粘贴解析放第二版）。",
        "店铺列表 + 商品 + 购物车 + 下单 + 支付。这一步能跑通，项目就活了 70%。",
        "商家接单 / 出餐（先不做菜品管理，后台直接改数据库也行）。",
        "骑手抢单 / 送达（拍照先不做，用「确认送达」按钮）。",
        "后台只做三个页面：商家审核、订单查询、平台开关。",
        "评价与售后第一版只做「申请退款 + 后台同意/拒绝」，不做自动仲裁。",
    ])
    h.callout(
        "一个人做项目最大的风险",
        "不是技术，是**范围蔓延**。每加一个功能，就要多测一条路径、多写一套接口、多一次联调。"
        "把「二期」清单打印出来贴在显示器上，想加功能时先看一眼它是不是已经在那儿了。",
        kind="note",
    )


def chapter6(h):
    h.h1("六、库表与接口先落地")
    h.p(
        "starter 包里已经有完整的 29 张表（`starter/sql/schema.sql`），字段注释齐全，"
        "首次 `docker compose up` 会自动建库。这里说明分组与关键设计，细节看 SQL。",
    )

    h.h2("6.1 表分组")
    h.table(
        ["分组", "表", "要点"],
        [
            ["用户", "user、user_address", "openid 唯一；手机号/学号密文存储 + 脱敏字段"],
            ["商家与商品", "shop、shop_category、product、product_category、product_sku、cart_item", "商品价格单位分；库存 -1 表示不限"],
            ["交易", "orders、order_item、order_status_log、payment、refund", "订单存地址快照 JSON；金额全为分；状态流水独立表"],
            ["配送", "rider、delivery_task、rider_location", "配送任务与订单一对一；轨迹单独表，便于归档"],
            ["资金", "wallet、wallet_flow、settlement、withdraw", "流水表有业务唯一键，天然幂等"],
            ["营销", "coupon、user_coupon", "一期只做满减券"],
            ["售后与评价", "review、after_sale", "评价与售后分表；售后带责任方字段"],
            ["平台", "sys_config、fence、announcement、admin_user、notify_log", "所有开关进 sys_config；围栏存 POLYGON"],
        ],
        widths=[2.4, 8.6, 5.4],
        caption="表 6-1　29 张表的分组",
        first_col_bold=True,
    )

    h.h2("6.2 orders 表的关键字段")
    h.table(
        ["字段", "类型", "为什么这么设计"],
        [
            ["order_no", "VARCHAR(32) 唯一", "对外单号，不能暴露自增 ID（会被爬单量）"],
            ["status", "TINYINT", "枚举值见状态机；不要用字符串，查询慢且易写错"],
            ["address_json", "JSON", "下单瞬间的地址快照。用户后来改地址，历史订单不能被改"],
            ["lng / lat", "DECIMAL(10,7)", "独立存坐标，围栏复核、退款取证、热力图都用得到"],
            ["goods_amount / pay_amount", "INT（分）", "商品总额与实付分开存，售后按商品退时有依据"],
            ["commission / rider_income / shop_income", "INT（分）", "下单时算好快照，避免事后改佣金比例影响历史单"],
            ["pay_expire_at", "DATETIME", "定时任务据此取消超时未支付订单"],
            ["各状态时间戳", "DATETIME", "paid_at / accepted_at / ready_at / picked_at / delivered_at，是算时效的唯一依据"],
        ],
        widths=[4.4, 3.4, 8.6],
        caption="表 6-2　orders 关键字段",
        first_col_bold=True,
    )

    h.h2("6.3 首批接口清单（36 个）")
    h.p("按「先跑通闭环」排序。统一前缀 `/api`，统一响应 `{code, msg, data}`，`code=0` 成功。")
    h.code(
        """
# 鉴权
POST   /api/auth/login                  # code -> openid，签发 token
POST   /api/auth/phone                  # 手机号授权解密
GET    /api/user/profile

# 地址
GET    /api/addresses
POST   /api/addresses
PUT    /api/addresses/{id}
DELETE /api/addresses/{id}
POST   /api/addresses/parse             # 粘贴文本 -> 结构化地址

# 门店与商品
GET    /api/shops                       # 附近/分类/搜索，支持 lat,lng,page
GET    /api/shops/{id}
GET    /api/shops/{id}/products
GET    /api/categories

# 购物车与订单
POST   /api/cart/calculate              # 服务端算价：商品+打包+配送-优惠
POST   /api/orders                      # 创建订单（校验围栏、库存、起送价）
GET    /api/orders                      # 按状态分页
GET    /api/orders/{id}
POST   /api/orders/{id}/cancel
POST   /api/orders/{id}/confirm
POST   /api/orders/{id}/review
POST   /api/orders/{id}/after-sale

# 支付
POST   /api/pay/prepay                  # 返回小程序调起支付参数
POST   /api/pay/notify                  # 微信回调（幂等 + 验签）
POST   /api/pay/refund

# 商家 / 骑手
POST   /api/merchant/apply
GET    /api/merchant/orders
POST   /api/merchant/orders/{id}/accept | reject | ready
POST   /api/merchant/products
GET    /api/rider/pool
POST   /api/rider/orders/{id}/grab
POST   /api/rider/orders/{id}/pickup | deliver
POST   /api/rider/location

# 后台
POST   /api/admin/shops/{id}/audit
POST   /api/admin/riders/{id}/audit
GET    /api/admin/orders
PUT    /api/admin/configs
PUT    /api/admin/fence
        """,
        caption="代码 6-1　接口清单",
    )

    h.h2("6.4 接口约定（先定死，后面不用吵）")
    h.bullets([
        "**金额全部是分**，前端不做任何除法，展示时才 `/100` 并保留两位。",
        "**时间**统一为 `yyyy-MM-dd HH:mm:ss`，时区 `Asia/Shanghai`；数据库用 DATETIME，不用 TIMESTAMP（避免时区坑）。",
        "**分页**：`page` 从 1 开始，`size` 默认 10、上限 50；返回 `{list, total, page, size}`。",
        "**错误码分段**：0 成功；1xxx 参数；2xxx 业务（如 2001 超出配送范围、2002 库存不足、2003 商家休息）；3xxx 权限；4xxx 支付。前端按段位做不同提示。",
        "**幂等**：所有「创建/支付/回调」接口要求客户端带 `requestId`，服务端去重。",
    ])


def chapter7(h):
    h.h1("七、预算：修正文档里的成本表")
    h.p(
        "文档第十二章的表方向对，但漏了几项（数据库、Redis、对象存储流量、CDN、备份），"
        "并且 4 核 8G 一台扛「2.5 万师生午晚高峰并发」的说法过于乐观。下面是按一期真实规模重算的。",
    )
    h.table(
        ["项目", "规格", "首年费用", "说明"],
        [
            ["微信小程序认证", "企业主体", "300 元/年", "每年年审，不可复用公众号认证"],
            ["云服务器", "4C8G / 100G SSD", "2000–4000 元/年", "新用户活动价能到 2000 以内；续费会涨"],
            ["云数据库 RDS", "MySQL 2C4G / 100G", "2000–3500 元/年", "文档漏项。省这笔钱就要自己承担备份与宕机风险"],
            ["云 Redis", "1G 标准版", "600–1200 元/年", "文档漏项。可用同机自建替代，但不建议上生产"],
            ["对象存储", "50G + 每月 50G 流量", "100–300 元/年", "菜品图 + 骑手凭证照，开缩略图能省一半"],
            ["CDN", "按量", "100–500 元/年", "图片走 CDN，接口不走"],
            ["域名", "3 个子域 + 备案", "60–100 元/年", "备案免费，但要等"],
            ["SSL 证书", "免费 DV", "0", "云厂商免费证书够用"],
            ["短信", "约 2000 条", "80–150 元/年", "能改用订阅消息就接近 0"],
            ["腾讯位置服务", "免费额度内", "0", "超出约 30 元/万次"],
            ["微信支付手续费", "交易额 0.6%", "按流水", "月流水 3 万时约 180 元/月"],
            ["云打印（可选）", "云打印机 1 台", "300–600 元/台", "比蓝牙小票稳定，商家更愿意用"],
            ["**合计（不含手续费与人力）**", "", "**约 5500–10700 元**", "文档里 3600 元的估算只覆盖了服务器"],
        ],
        widths=[4.6, 4.2, 4.0, 4.0],
        caption="表 7-1　首年成本（一期规模）",
        first_col_bold=True,
    )
    h.p(
        "顺带说一下人力参照：这套东西外包报价通常在 8–15 万（四端 + 后台 + 结算）。"
        "自己做省下的是钱，付出的是 2–3 个月的时间和一堆踩坑——这也是这份手册存在的意义。",
    )


def chapter8(h):
    h.h1("八、风险与踩坑清单")
    h.table(
        ["#", "风险", "触发条件", "后果", "应对"],
        [
            ["1", "类目被拒", "选了「外卖平台」但没有第三方平台备案", "无法发布，重来一次 3 周", "一期走「餐饮服务场所」，按 2.3 节"],
            ["2", "支付开通被拒", "平台代收后转账给商家", "支付功能开不下来，项目停摆", "用分账；见 2.4"],
            ["3", "备案没下来", "先开发后备案", "体验版能内测，但无法对外发布", "第 1 周就提交，1–20 个工作日"],
            ["4", "位置接口被拒", "用途描述含糊、被判定非必要", "定位拿不到，围栏功能失效", "申请时写清「校验收货地址是否在校内」，附页面截图"],
            ["5", "审核认定多商户", "页面上出现「商家入驻」「多家店铺」等表述", "要求补平台资质", "文案改为「平台合作门店」，商家由平台代运营"],
            ["6", "商家资质过期/造假", "只上传不核验", "食品安全事故时平台连带责任", "证照 OCR + 人工核验 + 到期提醒 + 定期抽查"],
            ["7", "骑手身份与保险", "学生骑手无协议、无保险", "出事故平台担责，且学校会介入", "电子兼职协议 + 短期意外险（按天投保），留存学籍证明"],
            ["8", "高峰性能", "午高峰 200 单集中在 10 分钟", "下单超时、重复支付、超卖", "库存预扣（Redis）+ 接口限流 + 压测；订单表按时间索引"],
            ["9", "资金对账差错", "只看余额不看流水", "商家/骑手投诉，甚至资金缺口", "每日对账任务 + wallet_flow 唯一键 + 差错表人工处理"],
            ["10", "隐私合规", "读剪贴板、存身份证未声明", "审核被拒或被投诉下架", "隐私指引逐项声明；敏感字段加密；最小化采集"],
        ],
        widths=[0.7, 3.0, 4.4, 3.9, 4.8],
        caption="表 8-1　十个真实会卡住你的坑",
    )
    h.callout(
        "写给你的一句话",
        "这十条里，只有第 8 条是技术问题，其余九条都是「先办证、先设计、先想清楚」。"
        "如果只记住一件事：**在做任何页面之前，先把主体、类目、备案、支付四件事启动**。",
        kind="ok",
    )


def chapter9(h):
    h.h1("九、随手册交付的启动包")
    h.p("`starter/` 目录里的文件可以直接用，不需要改就能跑起来（密码等按需改 `.env`）。")
    h.table(
        ["文件", "内容", "状态"],
        [
            ["starter/docker-compose.yml", "MySQL 8.4 + Redis 7.4 + MinIO，含健康检查与初始化挂载", "已通过 `docker compose config` 校验"],
            ["starter/.env.example", "全部环境变量模板（数据库、微信、支付、OSS、开关）", "可用"],
            ["starter/sql/schema.sql", "29 张表，字段带中文注释，金额统一为分", "已在 MySQL 8.4 实机建库，29 张表全部创建成功"],
            ["starter/sql/seed.sql", "分类、13 个平台开关、围栏示例、说明", "需替换示例围栏坐标"],
            ["starter/README.md", "启动步骤、连接信息、常用命令、下一步", "可用"],
        ],
        widths=[4.6, 8.6, 3.2],
        caption="表 9-1　启动包清单",
        first_col_bold=True,
    )
    h.p("三步用起来：")
    h.code(
        """
cd starter
cp .env.example .env
docker compose up -d

docker exec -it lcu-mysql mysql -ulcu -plcu123456 lcu_delivery -e "show tables;"
        """,
        caption="代码 9-1　最短路径",
    )
    h.callout(
        "关于验证程度，如实说明",
        [
            "已实测：`docker compose config` 通过；MySQL 8.4 容器起库后自动执行 schema 与 seed，**29 张表全部创建成功**，13 个开关与 4 个分类写入正常，`ST_Contains` 在围栏内外各测一次，结果符合预期；Redis 7.4 健康检查通过。",
            "未全量实测：MinIO 因镜像拉取受限未起容器（本地可用，上线前请用真实 COS/OSS 再验一次图片上传）；微信登录、支付、分账需真实 AppID 与商户号，无法在此环境验证。",
            "围栏示例坐标只是格式演示，不是聊大真实边界，**必须用腾讯地图坐标拾取器重新取点**，否则围栏判定没有意义。",
        ],
        kind="note",
    )


def appendix(h):
    h.h1("附录 A　命令速查", page_break=True)
    h.code(
        """
# ---------- 本地服务 ----------
docker compose up -d                     # 起 MySQL/Redis/MinIO
docker compose ps                        # 看状态
docker compose logs -f mysql             # 看日志
docker exec -it lcu-mysql mysql -ulcu -plcu123456 lcu_delivery
docker exec -it lcu-redis redis-cli -a redis123456
docker compose down -v && rm -rf data/   # 彻底重置（会删数据）

# ---------- 后端 ----------
./mvnw spring-boot:run                   # 本地启动
./mvnw clean package -DskipTests         # 打包
curl http://127.0.0.1:8080/actuator/health

# ---------- 小程序 ----------
pnpm dev:mp-weixin                       # 开发编译
pnpm build:mp-weixin                     # 生产编译
npx miniprogram-ci upload --pp dist/build/mp-weixin \\
  --pkp ./private.key --appid wxXXXX --uv 1.0.0 --desc "release"

# ---------- 数据库 ----------
mysqldump -h127.0.0.1 -ulcu -p lcu_delivery > backup_$(date +%F).sql
        """,
        caption="代码 A-1　常用命令",
    )

    h.h1("附录 B　官方文档索引")
    h.table(
        ["用途", "地址"],
        [
            ["微信小程序运营规范", "developers.weixin.qq.com/miniprogram/product/"],
            ["开放的服务类目（查资质）", "developers.weixin.qq.com/miniprogram/product/material/"],
            ["小程序备案指引", "developers.weixin.qq.com/miniprogram/product/record/guidelines"],
            ["微信认证指引", "developers.weixin.qq.com/miniprogram/product/renzheng"],
            ["微信支付 APIv3", "pay.weixin.qq.com/doc/v3/merchant/"],
            ["分账产品介绍", "pay.weixin.qq.com/doc/v3/merchant/4012067962"],
            ["小程序开发文档", "developers.weixin.qq.com/miniprogram/dev/framework/"],
            ["uni-app 文档", "uniapp.dcloud.net.cn"],
            ["腾讯位置服务", "lbs.qq.com"],
            ["Spring Boot 文档", "docs.spring.io/spring-boot/"],
        ],
        widths=[6.4, 10.0],
        caption="表 B-1　常用官方入口",
        first_col_bold=True,
    )

    h.h1("结语：7 天启动清单")
    h.p(
        "把第一章的「5 件事」落成日历。这 7 天不需要写业务代码，目标是让资质在跑、环境能跑、"
        "三个关键 demo 通——这三件事决定了后面 12 周是否顺利。",
    )
    h.table(
        ["天", "做什么", "完成标志"],
        [
            ["D1", "定主体（A / B / C），同步给团队；拉一个「资质进度」共享表格", "一句话结论：用哪个主体，谁去跑"],
            ["D2", "核名 + 提交注册；注册小程序（企业主体）并提交微信认证", "拿到受理号；认证订单已支付"],
            ["D3", "提交小程序备案；申请地理位置接口权限", "备案状态变为「审核中」"],
            ["D4", "装 Homebrew / JDK 21 / 微信开发者工具；起 docker compose；跑通后端 health 与小程序登录", "actuator/health 返回 UP，真机登录成功"],
            ["D5", "接通内网穿透，跑通 1 分钱支付与退款", "订单状态从「待支付」变「待接单」，退款到账"],
            ["D6", "谈 3 家档口，收齐营业执照与食品经营许可证；提交服务类目", "类目状态「审核中」"],
            ["D7", "复盘：把卡点写下来，对照 1.2 节再确认一次一期范围", "形成一页「本周结论 + 下周计划」"],
        ],
        widths=[1.2, 9.4, 5.8],
        caption="表 C-1　第一周日历",
    )

    h.h2("三个需要你现在就决定的问题")
    h.steps([
        "**主体用哪个？** 学校后勤 / 自己的公司 / 挂靠承包方。这个决定会改变后面所有流程，别拖。",
        "**一期要不要校外商家？** 要，就意味着 1–2 个月备案与平台资质；不要，一个学期内能上线。",
        "**谁来跑资质？** 需要一个能去市场监管局、能去银行、能接审核电话的人，通常是团队里最靠谱的那个，不是写代码的那个。",
    ])
    h.p(
        "这三件事定了，剩下的按第四章配环境、第五章按周推进即可。遇到具体卡点，把报错和上下文带回来，"
        "我们接着往下拆。",
    )
