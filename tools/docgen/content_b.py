# -*- coding: utf-8 -*-
"""第三章 技术方案、第四章 环境配置"""


def chapter3(h):
    h.h1("三、技术方案定稿")
    h.p(
        "文档给的栈（uni-app + Spring Boot + MySQL + Redis + MQ + Vue + Element）方向没问题，"
        "只需要三处调整：去掉 MQ、Vue2 换 Vue3、三端合一。下面是可直接开工的版本。",
    )

    h.h2("3.1 总体架构")
    h.table(
        ["层", "组成", "说明"],
        [
            ["客户端", "用户端小程序（uni-app）\n工作台小程序（商家 / 骑手）\n管理后台（Vue3 + Element Plus）", "两个小程序共用一套后端 API；后台独立部署"],
            ["接入层", "Nginx / 云 SLB、HTTPS、限流", "小程序要求所有域名 HTTPS 且已备案"],
            ["应用层", "Spring Boot 单体，按模块分包：auth / user / shop / product / order / pay / delivery / settle / admin", "**一期不要拆微服务**。单体能省掉 60% 的运维与调试成本"],
            ["数据层", "MySQL 8（交易）+ Redis 7（缓存/锁/验证码）+ 对象存储（图片）", "订单、资金必须落 MySQL；Redis 只做加速，不做唯一数据源"],
            ["外部能力", "微信登录、微信支付 + 分账、订阅消息、腾讯位置服务、云打印、短信", "全部封装成 adapter，接口变了只改一处"],
            ["运营支撑", "定时任务（超时取消、自动确认、结算、对账）、日志、监控告警", "一期用 @Scheduled + ShedLock，多实例也不会重复执行"],
        ],
        widths=[2.1, 8.0, 6.3],
        caption="表 3-1　分层架构（自上而下即请求路径）",
        first_col_bold=True,
    )

    h.h2("3.2 选型表")
    h.table(
        ["组件", "推荐", "备选", "为什么选它"],
        [
            ["小程序框架", "uni-app 3（Vue3 + Vite + TS）", "Taro 3 / 原生小程序", "一套代码可扩 H5 与 App；文档方案已选，团队好招人"],
            ["UI 组件", "uview-plus 或 uni-ui", "Vant Weapp", "uni-app 生态内，改造成本低"],
            ["状态管理", "Pinia", "Vuex", "Vue3 官方推荐"],
            ["管理后台", "Vue3 + Vite + Element Plus + ECharts", "Vue2 + ElementUI（已停更）", "别用 Vue2，2023 年底已停止维护"],
            ["后端", "Spring Boot 3.5.x + JDK 21", "NestJS / Go", "文档已选 Java；单体足够撑到日均 5000 单"],
            ["ORM", "MyBatis-Plus", "Spring Data JPA", "分页、条件构造、逻辑删除开箱可用，国内资料多"],
            ["鉴权", "Sa-Token", "Spring Security + JWT", "小程序场景下 Sa-Token 更轻，多端登录好写"],
            ["支付 SDK", "wechatpay-java（微信官方 v3）", "自己签名", "签名、验签、平台证书轮换都封装好了，别自己造"],
            ["小程序 SDK", "WxJava（binarywang）", "手写 HTTP", "code2session、订阅消息、手机号解密都有现成封装"],
            ["缓存", "Redis 7", "无", "菜单缓存、库存预扣、分布式锁、验证码"],
            ["队列", "一期不用", "RocketMQ / Kafka（二期）", "日均 < 1000 单，定时任务足够；MQ 是给自己加运维"],
            ["定时任务", "@Scheduled + ShedLock", "XXL-Job / Quartz", "单体 + 多实例场景最省事"],
            ["地图", "腾讯位置服务", "高德 / 百度", "与微信同源，坐标不用转换，逆地址解析准确"],
            ["图片存储", "腾讯 COS / 阿里 OSS（本地用 MinIO）", "七牛", "与服务器同厂商，内网流量免费"],
            ["部署", "Docker + 云服务器 + Nginx", "Serverless / 云托管", "学生团队可控成本，出问题能登机器排查"],
        ],
        widths=[2.4, 4.6, 3.6, 5.8],
        caption="表 3-2　技术选型（推荐 / 备选 / 理由）",
        first_col_bold=True,
    )
    h.callout(
        "关于你机器上的 JDK 25",
        [
            "Spring Boot 3.5 官方支持到 JDK 24，JDK 25 下 Lombok 需要 1.18.40 以上，注解处理器也偶发不兼容——这类问题最消耗新手时间。",
            "建议项目固定 **JDK 21（Temurin）**，用 jenv 切换；JDK 25 留着跑别的。真要追新，就用 Spring Boot 4.x + JDK 25，但 MyBatis-Plus、Sa-Token 等生态要自己验证兼容性，不建议在第一个项目上冒险。",
        ],
        kind="note",
    )

    h.h2("3.3 仓库结构")
    h.p("一个 Git 仓库装下所有端，避免「改了接口忘了同步」的老问题。")
    h.code(
        """
lcu-delivery/
├── apps/
│   ├── user-uniapp/          # 用户端（AppID A）
│   ├── workbench-uniapp/     # 商家 + 骑手工作台（AppID B）
│   └── admin-web/            # 管理后台 Vue3 + Element Plus
├── server/                   # Spring Boot 单体
│   ├── src/main/java/com/lcu/delivery/
│   │   ├── modules/{auth,user,shop,product,order,pay,delivery,settle,admin}/
│   │   ├── common/{result,exception,config,interceptor,util}
│   │   └── job/              # 超时取消、自动确认、结算、对账
│   └── src/main/resources/
│       ├── application.yml
│       ├── application-dev.yml
│       └── db/{schema.sql,seed.sql}
├── ops/
│   ├── docker-compose.yml    # 本地 MySQL / Redis / MinIO
│   ├── nginx/
│   └── sql/
├── docs/                     # 本手册、接口文档、状态机图
└── .env.example
        """,
        caption="代码 3-1　建议的仓库结构",
    )
    h.p(
        "`modules/` 下按业务分包，每个包里固定 `controller / service / mapper / entity / dto`。"
        "不要按 `controller / service / mapper` 平铺——20 个模块平铺后，找一个接口要翻三分钟。",
    )

    h.h2("3.4 两个小程序怎么分工")
    h.table(
        ["小程序", "角色", "入口", "审核关注点"],
        [
            ["A 用户端", "师生（含非本校用户）", "扫码 / 搜索 / 分享", "交易真实性、类目一致性、隐私协议"],
            ["B 工作台", "商家、骑手", "邀请码 + 手机号绑定后进入", "身份证采集必要性、位置权限、协议与保险"],
        ],
        widths=[2.6, 3.4, 4.6, 5.8],
        caption="表 3-3　两个小程序的边界",
        first_col_bold=True,
    )
    h.p(
        "为什么不合成一个：商家与骑手要拿身份证、相册、位置权限，用户端一旦被要求解释这些，"
        "整个审核都会被拖慢。为什么不拆成三个：每多一个小程序，就多一次认证（300 元/年）、"
        "多一次备案、多一条发版流水线。两个是性价比最高的切法。",
    )
    h.callout(
        "实现提醒：tabBar 不能动态切换",
        "微信小程序的 tabBar 是编译期配置，无法运行时按角色切换。工作台有两种做法："
        "① 用自定义 tabBar（`custom-tab-bar` 目录），按角色渲染不同项；"
        "② 干脆不用 tabBar，登录后进「工作台首页」，用宫格导航进各功能。第二种更简单，推荐。",
        kind="accent",
    )

    h.h2("3.5 六个核心设计")

    h.h3("3.5.1 订单状态机")
    h.p("状态只能单向推进，任何一次变更都要写流水，所有变更接口都要有幂等键。")
    h.table(
        ["状态值", "名称", "谁触发", "允许的下一步"],
        [
            ["0", "待支付", "用户提交订单", "10 支付成功 / 60 超时取消"],
            ["10", "待接单", "支付回调", "20 商家接单 / 60 商家拒单（自动退款）"],
            ["20", "待出餐", "商家接单", "30 商家点击出餐"],
            ["30", "待取餐", "商家出餐", "40 骑手接单并取餐"],
            ["40", "配送中", "骑手取餐", "50 骑手确认送达 / 70 异常售后"],
            ["50", "已完成", "骑手送达（或超时自动确认）", "70 售后 / 评价"],
            ["60", "已取消", "用户 / 商家 / 系统", "80 退款完成"],
            ["70", "售后中", "用户申请或系统判定异常", "80 退款 / 回退到 50"],
            ["80", "已退款", "管理员仲裁 / 自动退款", "终态"],
        ],
        widths=[1.6, 2.6, 5.4, 6.8],
        caption="表 3-4　订单状态机",
    )
    h.p(
        "三条铁律：① 除管理员补偿，状态不允许跳跃；② 每次变更写 `order_status_log`（谁、从什么到什么、为什么）；"
        "③ 支付回调、分账回调都必须**幂等**——微信会重试，重复回调不能重复发货、重复记账。",
    )

    h.h3("3.5.2 地理围栏")
    h.p(
        "文档要求「超出校园围栏禁止提交订单」。实现上不要依赖任何付费接口：自己存多边形，用数据库判断点是否在面内。",
    )
    h.steps([
        "**取点**：打开腾讯地图坐标拾取器，沿东、西校区实际可配送边界取点（每 10–20 米一个点足够，一个校区 30–60 个点）。",
        "**存储**：`fence` 表存 `POLYGON`，用 SRID 0 平面坐标（按经度、纬度书写）。不用 4326 是因为 MySQL 对 4326 按「纬度、经度」轴序解释，极容易写反。",
        "**判定**：`ST_Contains(polygon, POINT(lng, lat))`，校园尺度下平面计算完全够用。",
        "**性能**：启动时把 2 个多边形加载进内存，判定走内存；只有后台改围栏时才刷新缓存。",
        "**两处校验**：保存地址时「软提示」（提示可能超出配送范围，允许保存）；提交订单时「硬拦截」（不允许下单）。",
    ])
    h.code(
        """
-- 建表（见 starter/sql/schema.sql）
CREATE TABLE fence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  campus TINYINT NOT NULL,
  polygon POLYGON NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  SPATIAL INDEX (polygon)
);

-- 判定：提交订单前执行
SELECT COUNT(*) FROM fence
WHERE status = 1 AND ST_Contains(polygon, POINT(#{lng}, #{lat}));
        """,
        caption="代码 3-2　围栏存储与判定",
    )

    h.h3("3.5.3 地址粘贴解析")
    h.p("这是文档里最有特色的功能，也是最容易做成「看起来能用、实际天天出错」的功能。用三级降级 + 置信度。")
    h.table(
        ["级别", "做法", "能解决", "成本/延迟"],
        [
            ["L1 正则抽取", "校区关键词、楼栋、楼层、房间号、手机号", "「东校区 3 号宿舍楼 502 张三 138xxxx」这类规范写法", "0 成本，< 1ms"],
            ["L2 地图地址解析", "腾讯位置服务地址解析 / 智能解析，返回结构化地址 + 坐标", "「聊大东校图书馆后面那栋」这类模糊描述", "免费额度内，100–300ms"],
            ["L3 大模型兜底", "把原文 + 校区楼栋清单丢给 LLM，要求输出 JSON", "长段落、口语化、含多个地址的粘贴文本", "约 0.001 元/次，1–3s"],
        ],
        widths=[2.4, 6.2, 5.3, 2.5],
        caption="表 3-5　地址解析的三级降级",
    )
    h.p(
        "产品上的关键不是「解析得多准」，而是**解析错了用户能不能一眼发现**：把结果回填到表单、"
        "每个字段单独可改、给出置信度提示（如「楼栋识别置信度低，请确认」）。"
        "另外，这个功能有开关（文档 4.4.6 要求），开关值放 `sys_config`，小程序启动时拉取。",
    )

    h.h3("3.5.4 派单：一期别做算法")
    h.p(
        "文档写「智能派单 + 抢单双模式」。一期只做抢单大厅 + 后台手动指派，原因有三："
        "骑手实时位置需要后台定位权限（审核难）、算法需要历史数据（冷启动没有）、"
        "单校区同时在线骑手通常不到 20 人，人工指派比算法更准。",
    )
    h.p("二期要上算法时，评分公式可以简单到这样，效果比想象中好：")
    h.code(
        """
score = 0.5 * (1 - 距离/最大半径)
      + 0.2 * (1 - 当前在手单数/最大并发)
      + 0.2 * 历史准时率
      + 0.1 * 评分/5
# 约束：在手单数 >= max_concurrent 的骑手不参与派单
        """,
        caption="代码 3-3　派单评分（二期）",
    )

    h.h3("3.5.5 结算与分账时序")
    h.table(
        ["时点", "动作", "幂等键", "失败兜底"],
        [
            ["支付成功", "写 payment，标记待分账冻结", "out_trade_no", "回调重试；定时任务补查支付状态"],
            ["订单完成", "写 settlement（待结算）", "order_id", "状态机补偿"],
            ["T+N 到期", "调用分账 API 分给商家", "settlement.id", "分账失败进差错表，人工重试"],
            ["同上", "骑手佣金入 wallet（或分账）", "order_id + rider_id", "同上"],
            ["提现申请", "冻结余额 → 审核 → 打款", "withdraw.id", "打款失败自动解冻"],
        ],
        widths=[2.6, 6.4, 3.4, 4.0],
        caption="表 3-6　结算时序与幂等设计",
    )

    h.h3("3.5.6 金额、幂等与对账")
    h.bullets([
        "**金额一律用「分」存 INT/BIGINT**，禁止用 float/double。0.1 + 0.2 ≠ 0.3 这种问题会在对账时集中爆发。",
        "**唯一键就是幂等键**：`payment.out_trade_no`、`wallet_flow(biz_type, biz_no)`、`settlement.order_id`。插入冲突就当作已处理。",
        "**每日对账**：下载微信对账单，与本地 payment 表按单号比对，差异写差错表，人工处理。别指望「应该不会错」。",
        "**回调必须验签**，且先落库再执行业务；业务失败返回失败让微信重试，不要吞掉异常。",
    ])

    h.h2("3.6 安全与合规设计")
    h.table(
        ["项", "必须做的", "为什么"],
        [
            ["隐私协议", "在小程序后台配置《用户隐私保护指引》，声明：位置、相册、手机号、**剪贴板**", "粘贴解析要读剪贴板，不声明会被驳回；2023 年后微信对隐私校验很严"],
            ["敏感信息", "手机号、身份证、学号用 AES-GCM 加密入库；列表与日志只显示脱敏值", "文档 9.4 有要求，也是个人信息保护法的最低线"],
            ["越权", "每个接口校验资源归属：订单只能本人查、商家只能改自己的商品", "最常见的低级漏洞，审核不查，出事就是大事"],
            ["后台权限", "RBAC：超管 / 客服 / 财务 / 审核员，四个固定角色先够用", "文档 4.4.1 要求多角色隔离"],
            ["资金", "所有余额变动只走 wallet_flow，禁止直接 update balance", "可追溯、可对账、可回滚"],
            ["等保", "若接入学校网络或使用校园数据，提前问清是否要求等保二级", "后期补做成本极高"],
        ],
        widths=[2.2, 8.6, 5.6],
        caption="表 3-7　安全与合规清单",
        first_col_bold=True,
    )


def chapter4(h):
    h.h1("四、环境配置：从 0 到能跑起来")
    h.p(
        "这一章按顺序执行即可。每一步都给了「验收命令」——上一步没通过，不要往下走。"
        "命令按 macOS + Apple Silicon 的路径整理，可直接照抄；Homebrew 首次安装需要联网并可sudo。",
    )

    h.h2("4.1 本机开发环境")
    h.h3("4.1.1 装 Homebrew（后面全都靠它）")
    h.code(
        """
/bin/bash -c "$(curl -fsSL \\
  https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

# Apple Silicon 装完后把 brew 加进 PATH（脚本结束时会提示这两行）
echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
eval "$(/opt/homebrew/bin/brew shellenv)"
brew -v
        """,
        caption="代码 4-1　安装 Homebrew",
    )

    h.h3("4.1.2 装 JDK 21 与 Maven")
    h.code(
        """
brew install openjdk@21 maven

# 让系统识别 JDK 21（/usr/libexec/java_home -V 能看到它）
sudo ln -sfn /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk \\
  /Library/Java/JavaVirtualMachines/openjdk-21.jdk

# 建议装 jenv 管理多版本，避免 JDK 25 干扰
brew install jenv
echo 'export PATH="$HOME/.jenv/bin:$PATH"' >> ~/.zprofile
echo 'eval "$(jenv init -)"' >> ~/.zprofile
eval "$(jenv init -)"
jenv add /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
jenv global 21
        """,
        caption="代码 4-2　安装 JDK 21 与 Maven",
    )

    h.h3("4.1.3 前端工具链与开发工具")
    h.code(
        """
# pnpm（Node 22 自带 corepack）
corepack enable pnpm
corepack prepare pnpm@latest --activate
pnpm -v

# 微信开发者工具（必装）
brew install --cask wechatwebdevtools

# 数据库客户端
brew install --cask dbeaver-community

# 启动 Docker Desktop
open -a Docker

# VS Code 插件（已装 VS Code）
code --install-extension Vue.volar
code --install-extension vscjava.vscode-java-pack
code --install-extension vmware.vscode-spring-boot
code --install-extension dbaeumer.vscode-eslint
        """,
        caption="代码 4-3　前端工具链与开发工具",
    )

    h.h3("4.1.4 验收：逐条跑，全部通过再往下")
    h.code(
        """
java -version          # 期望 openjdk version "21.x"
mvn -v                 # 期望 Maven 3.9.x, Java 21
node -v                # v22.16.0
pnpm -v                # 9.x / 10.x
docker info            # 无报错，Server Version 有值
/Applications/wechatwebdevtools.app/Contents/MacOS/cli -h   # 有输出即成功
        """,
        caption="代码 4-4　环境验收",
    )
    h.callout(
        "必须手动打开的一个开关",
        "微信开发者工具 → 设置 → 安全设置 → **服务端口** 打开。不打开，CLI 预览、miniprogram-ci 上传都会失败，"
        "而错误提示只会说「连接失败」，非常难查。",
        kind="accent",
    )

    h.h2("4.2 本地依赖服务（Docker）")
    h.p("starter 包里已经写好 `docker-compose.yml`，直接起三个服务：MySQL 8.4、Redis 7.4、MinIO（替代云对象存储）。")
    h.code(
        """
cd starter
cp .env.example .env
docker compose up -d
docker compose ps          # 三个服务都应为 healthy

# 验证库表（首次启动会自动执行 schema.sql / seed.sql）
docker exec -it lcu-mysql mysql -ulcu -plcu123456 lcu_delivery \\
  -e "SELECT COUNT(*) AS tables_cnt FROM information_schema.tables
      WHERE table_schema='lcu_delivery' AND table_type='BASE TABLE';"
# 期望输出 29
        """,
        caption="代码 4-5　启动本地依赖服务",
    )
    h.p("连接信息：MySQL `127.0.0.1:3306`（lcu / lcu123456）、Redis `127.0.0.1:6379`（密码 redis123456）、MinIO 控制台 `http://127.0.0.1:9001`。")
    h.callout(
        "这两个坑我在你这台机器上都遇到了",
        [
            "**docker pull 卡住**：直连 Docker Hub 超时。到「阿里云 → 容器镜像服务 → 镜像加速器」拿专属地址填进 Docker Desktop → Settings → Docker Engine 的 `registry-mirrors`；临时应急也可以直接用公共镜像站的前缀拉取（本次测试用 `docker.m.daocloud.io/library/mysql:8.4` 成功，再 `docker tag` 成本地 `mysql:8.4` 即可）。",
            "**3306 被占用**：`docker compose up` 报 `address already in use`。改 `.env` 里的 `MYSQL_PORT=3307` 再起，连接串跟着改成 3307；或先查是谁占了端口（`lsof -nP -iTCP:3306 -sTCP:LISTEN`）。",
            "兜底方案：`brew install mysql@8.4 redis`，`brew services start mysql@8.4`、`brew services start redis`，连接串不变。",
        ],
        kind="note",
    )

    h.h2("4.3 后端工程初始化")
    h.p("用 Spring Initializr 拉骨架，别手写 pom。")
    h.code(
        """
mkdir -p ~/code/lcu-delivery && cd ~/code/lcu-delivery

curl -G https://start.spring.io/starter.zip \\
  -d type=maven-project -d language=java -d javaVersion=21 \\
  -d groupId=com.lcu -d artifactId=delivery -d name=delivery \\
  -d packageName=com.lcu.delivery \\
  -d dependencies=web,validation,data-redis,mysql,lombok,actuator \\
  -o delivery.zip
unzip delivery.zip -d server && rm delivery.zip
cd server && ./mvnw -v
        """,
        caption="代码 4-6　生成后端骨架",
    )
    h.p("除官方 starter 外，再手工补三个依赖（版本以 Maven Central 最新稳定版为准）：")
    h.table(
        ["依赖", "用途", "备注"],
        [
            ["mybatis-plus-spring-boot3-starter", "ORM、分页、逻辑删除", "注意选 `spring-boot3` 版本，别用 boot2 版"],
            ["wechatpay-java", "微信支付 v3 签名、下单、分账、回调验签", "官方维护，别自己写签名"],
            ["wx-java-miniapp", "code2session、手机号解密、订阅消息", "binarywang 维护，国内用得最多"],
        ],
        widths=[6.6, 5.4, 4.4],
        caption="表 4-1　需要手动补充的依赖",
    )
    h.p("`application.yml` 用环境变量占位，密钥不落代码：")
    h.code(
        """
server:
  port: 8080
spring:
  datasource:
    # 完整连接串见 starter/.env.example
    url: ${DB_URL}
    username: ${DB_USERNAME:lcu}
    password: ${DB_PASSWORD:lcu123456}
  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT_NO:6379}
      password: ${REDIS_PASSWORD_ENV}
wechat:
  miniapp:
    appid: ${WX_USER_APPID}
    secret: ${WX_USER_SECRET}
  pay:
    mch-id: ${WXPAY_MCH_ID}
    api-v3-key: ${WXPAY_API_V3_KEY}
    private-key-path: ${WXPAY_PRIVATE_KEY_PATH}
    notify-url: ${WXPAY_NOTIFY_URL}
logging:
  level:
    com.lcu.delivery: debug
        """,
        caption="代码 4-7　application.yml 关键配置",
    )
    h.p(
        "跑起来验证：`./mvnw spring-boot:run`，访问 `http://127.0.0.1:8080/actuator/health` 返回 `{\"status\":\"UP\"}` 即通过。"
        "生产环境记得关闭 actuator 的敏感端点、关闭 Swagger。",
    )

    h.h2("4.4 小程序工程初始化")
    h.code(
        """
cd ~/code/lcu-delivery/apps
npx degit dcloudio/uni-preset-vue#vite-ts user-uniapp
cd user-uniapp && pnpm install

# 编译到微信小程序（产物在 dist/dev/mp-weixin）
pnpm dev:mp-weixin
        """,
        caption="代码 4-8　初始化用户端小程序",
    )
    h.p(
        "然后用微信开发者工具「导入项目」，目录选 `dist/dev/mp-weixin`，AppID 填你自己的。"
        "开发期在「详情 → 本地设置」勾选「不校验合法域名」，否则请求本地接口会被拦。",
    )
    h.p("`manifest.json` 需要补的三处（少一处都会在真机上出问题）：")
    h.code(
        """
{
  "mp-weixin": {
    "appid": "wx你的AppID",
    "setting": { "urlCheck": false, "es6": true, "minified": true },
    "requiredPrivateInfos": ["getLocation", "chooseLocation"],
    "permission": {
      "scope.userLocation": { "desc": "用于判断收货地址是否在校园配送范围内" }
    },
    "usingComponents": true
  }
}
        """,
        caption="代码 4-9　manifest.json 的 mp-weixin 段",
    )
    h.bullets([
        "`requiredPrivateInfos` 不声明，`wx.getLocation` 直接失败，且报错信息很含糊。",
        "`permission.scope.userLocation.desc` 会展示给用户，写清楚用途通过率更高。",
        "主包上限 2MB、总包 20MB：把「我的」「售后」等放分包，别一开始就撑爆。",
    ])

    h.h2("4.5 微信后台要配的 7 项")
    h.table(
        ["#", "配置项", "位置", "填什么 / 注意", "时点"],
        [
            ["1", "服务器域名", "开发管理 → 开发设置", "request / uploadFile / downloadFile 三个都填 `https://api.你的域名.com`，必须已备案", "后端有公网地址后"],
            ["2", "隐私保护指引", "设置 → 服务内容声明", "勾选：位置信息、相册、手机号、**剪贴板**", "提交审核前必做"],
            ["3", "地理位置接口", "开发管理 → 接口设置", "申请 `wx.getLocation`、`wx.chooseLocation`，写清用途：校验收货地址是否在校园配送范围", "开发中即可申请"],
            ["4", "订阅消息模板", "功能 → 订阅消息", "新订单、接单、出餐、骑手已取餐、已送达、退款结果（一次性订阅）", "联调前"],
            ["5", "成员与体验成员", "成员管理", "把测试同学加成体验成员，用体验版内测", "开发第 2 周"],
            ["6", "关联商户号", "微信支付商户平台 → 产品中心 → AppID 授权", "把小程序的 AppID 与商户号绑定，否则调不起支付", "商户号下来后"],
            ["7", "备案", "设置 → 基本设置 → 小程序备案", "主体信息、负责人、服务内容；备案中不能发布", "第 1 周就提交"],
        ],
        widths=[0.7, 2.6, 4.0, 7.1, 2.0],
        caption="表 4-2　微信后台配置清单",
    )

    h.h2("4.6 支付联调环境")
    h.p("微信支付 v3 没有沙箱，只能用真实小额（1 分钱）测试。需要三样东西：公网 HTTPS 回调地址、商户证书、APIv3 密钥。")
    h.steps([
        "**内网穿透**：`cpolar` / `ngrok` / `frp` 任选，把本地 8080 映射成 `https://xxx.cpolar.top`，回调地址填 `https://xxx.cpolar.top/api/pay/notify`。免费版域名每次重启会变，记得同步改配置。",
        "**证书**：从商户平台下载 `apiclient_key.pem`、`apiclient_cert.pem`，放 `server/certs/`，**写进 .gitignore**。平台证书由 SDK 自动下载并轮换，别写死。",
        "**下单**：`amount.total` 填 1（单位分），`notify_url` 必须是公网 https，`profit_sharing` 设 `Y` 才能后续分账。",
        "**验证**：支付成功 → 收到回调 → 订单变「待接单」；再调退款 → 钱回到零钱。两个都通了，支付模块才算过关。",
    ])
    h.table(
        ["现象", "最可能的原因", "怎么查"],
        [
            ["签名错误 SIGN_ERROR", "APIv3 密钥填错，或私钥与证书序列号不匹配", "核对 `mch_serial_no` 与证书序列号；重下密钥"],
            ["appid 与 mchid 不匹配", "小程序 AppID 没在商户平台关联", "商户平台 → 产品中心 → AppID 授权"],
            ["回调收不到", "本地地址不可达 / 不是 https / 被防火墙拦", "内网穿透面板看请求日志；用 curl 自测"],
            ["回调验签失败", "用了旧的平台证书 / 未更新", "让 SDK 自动下载平台证书，别缓存死"],
            ["分账失败", "未开通分账、接收方未添加、金额超比例", "商户平台分账产品页 + 分账接收方列表"],
        ],
        widths=[4.4, 6.2, 5.4],
        caption="表 4-3　支付联调常见错误",
    )

    h.h2("4.7 体验版、灰度与发布")
    h.p("小程序的发布节奏和 App 不一样，没有应用商店，但有审核。标准流程是：开发版 → 体验版 → 提交审核 → 发布。")
    h.steps([
        "编译上传：开发者工具「上传」，或用 `miniprogram-ci` 走 CI（下例）。",
        "设为体验版：后台「版本管理 → 开发版本 → 选为体验版」，把链接发给体验成员。",
        "提交审核：填功能页面、类目、测试账号（必须提供可登录的测试账号，否则必被拒）。",
        "发布：审核通过后发布；如后台提供分阶段发布，先 10% → 50% → 100%。",
    ])
    h.code(
        """
# .github/workflows/deploy-miniapp.yml（片段）
- name: Upload to WeChat
  run: |
    npx miniprogram-ci upload \\
      --pp ./apps/user-uniapp/dist/build/mp-weixin \\
      --pkp ${{ secrets.WX_PRIVATE_KEY_PATH }} \\
      --appid ${{ secrets.WX_APPID }} \\
      --uv 1.0.${{ github.run_number }} \\
      --desc "ci build"
        """,
        caption="代码 4-10　用 miniprogram-ci 自动上传",
    )
    h.callout(
        "审核被拒的三个高频原因",
        [
            "**类目不符**：页面里出现了「多家商户入驻」的表述，但类目是「餐饮服务场所」。要么改类目，要么改文案。",
            "**没给测试账号**：审核员进不去，直接拒。测试账号要能走完「选店 → 下单 → 支付」全流程，余额里留点钱或用 1 分钱商品。",
            "**隐私声明缺失**：用了位置/相册/剪贴板却没在隐私指引里声明，100% 被拒。",
        ],
        kind="accent",
    )

    h.h2("4.8 生产环境")
    h.table(
        ["资源", "起步规格", "数量", "说明"],
        [
            ["应用服务器", "4C8G / 100G SSD", "1–2", "单体起步 1 台够用；上线两周后按峰值加一台 + SLB"],
            ["数据库", "云 RDS MySQL 8.0 / 2C4G / 100G", "1", "**不要自建在应用机上**，备份和恢复会救你命"],
            ["缓存", "云 Redis 1G 标准版", "1", "别用开源版自建，主从切换很麻烦"],
            ["对象存储", "COS / OSS 50G + CDN", "1", "菜品图、凭证照；开图片处理（缩略图）省流量"],
            ["域名", "api / admin / img 三个子域", "1", "全部备案；配免费 SSL 证书"],
            ["Nginx", "与应用机同机", "1", "反向代理、gzip、静态缓存、限流"],
            ["监控", "云监控 + Sentry", "1", "接口错误率、慢 SQL、支付失败告警必须配"],
            ["备份", "RDS 自动备份 7 天 + 每日逻辑备份到 OSS", "1", "每月做一次恢复演练，别只备份不验证"],
        ],
        widths=[2.6, 5.8, 1.6, 6.4],
        caption="表 4-4　生产环境清单",
    )

    h.h2("4.9 配置与环境隔离")
    h.bullets([
        "三套环境：`dev`（本地）、`test`（一台低配云机，给体验版用）、`prod`。配置文件用 `application-{profile}.yml` + 环境变量。",
        "**所有密钥只走环境变量或云 KMS**：AppSecret、APIv3 密钥、数据库密码、JWT secret。仓库里只留 `.env.example`。",
        "平台开关（身份核验、地址解析、围栏强制）**不要放配置文件**，放 `sys_config` 表，后台可改、实时生效（加 Redis 缓存，改完删缓存）。",
        "日志：生产只打 INFO，敏感字段脱敏；按天切分，保留 30 天。",
        "上线前 checklist：关闭 Swagger、关闭 actuator 敏感端点、关闭 `urlCheck:false`、确认回调地址是 prod 域名、确认对账任务已开。",
    ])
