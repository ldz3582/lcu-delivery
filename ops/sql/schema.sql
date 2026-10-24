-- ============================================================
-- 聊城大学校园外卖小程序 · 核心库表（一期）
-- MySQL 8.0+ / 8.4，字符集 utf8mb4
-- 约定：
--   1) 所有金额字段单位为「分」，类型 INT/BIGINT，禁止用浮点
--   2) 所有时间字段为 DATETIME，由应用层写入（不用 ON UPDATE 隐式更新）
--   3) 状态字段用 TINYINT + 注释枚举，禁止用中文枚举入库
--   4) 逻辑删除统一用 deleted_at DATETIME NULL
-- ============================================================
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------- 1. 用户 ----------
CREATE TABLE IF NOT EXISTS `user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `openid`        VARCHAR(64)  NOT NULL COMMENT '用户端小程序 openid',
  `unionid`       VARCHAR(64)  DEFAULT NULL,
  `nickname`      VARCHAR(64)  DEFAULT NULL,
  `avatar`        VARCHAR(512) DEFAULT NULL,
  `phone_enc`     VARBINARY(256) DEFAULT NULL COMMENT '手机号密文 AES-GCM',
  `phone_mask`    VARCHAR(20)  DEFAULT NULL COMMENT '脱敏展示 138****8000',
  `role`          VARCHAR(16)  NOT NULL DEFAULT 'USER' COMMENT 'USER/MERCHANT/RIDER/ADMIN',
  `stu_no_enc`    VARBINARY(256) DEFAULT NULL COMMENT '学号/工号密文（AES-GCM，含随机 IV）',
  `stu_no_hash`   CHAR(64) DEFAULT NULL COMMENT '学号/工号 SHA-256，用于查重（密文不可比较）',
  `identity_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0未提交 1待审 2通过 3驳回',
  `status`        TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0封禁',
  `last_login_at` DATETIME DEFAULT NULL,
  `created_at`    DATETIME NOT NULL,
  `updated_at`    DATETIME NOT NULL,
  `deleted_at`    DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  KEY `idx_phone_mask` (`phone_mask`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户（师生）';

-- ---------- 2. 收货地址 ----------
CREATE TABLE IF NOT EXISTS `user_address` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `campus`     TINYINT DEFAULT NULL COMMENT '1东校区 2西校区 NULL未选',
  `detail`     VARCHAR(255) NOT NULL COMMENT '详细地址（楼栋/房间）',
  `building`   VARCHAR(64)  DEFAULT NULL COMMENT '解析出的楼栋，如 1号教学楼',
  `floor`      VARCHAR(32)  DEFAULT NULL,
  `room`       VARCHAR(32)  DEFAULT NULL,
  `remark`     VARCHAR(255) DEFAULT NULL,
  `lng`        DECIMAL(10,7) DEFAULT NULL,
  `lat`        DECIMAL(10,7) DEFAULT NULL,
  `in_fence`   TINYINT NOT NULL DEFAULT 0 COMMENT '坐标是否在围栏内 1是 0否',
  `parse_source` TINYINT DEFAULT NULL COMMENT '1手动 2正则 3地图解析 4大模型',
  `is_default` TINYINT NOT NULL DEFAULT 0,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  `deleted_at` DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_geo` (`lng`,`lat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收货地址';

CREATE TABLE IF NOT EXISTS `favorite` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `shop_id`    BIGINT UNSIGNED NOT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_shop` (`user_id`,`shop_id`),
  KEY `idx_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏店铺';

-- ---------- 3. 店铺 ----------
CREATE TABLE IF NOT EXISTS `shop` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`          VARCHAR(64) NOT NULL,
  `logo`          VARCHAR(512) DEFAULT NULL,
  `owner_user_id` BIGINT UNSIGNED DEFAULT NULL COMMENT '商家账号 user.id',
  `category_id`   BIGINT UNSIGNED NOT NULL COMMENT '店铺分类：食堂/小吃/饮品/超市',
  `campus`        TINYINT NOT NULL DEFAULT 1 COMMENT '1东 2西 3两校区',
  `address`       VARCHAR(255) NOT NULL,
  `lng`           DECIMAL(10,7) NOT NULL,
  `lat`           DECIMAL(10,7) NOT NULL,
  `contact_phone` VARCHAR(20) NOT NULL,
  `biz_status`    TINYINT NOT NULL DEFAULT 0 COMMENT '0休息 1营业',
  `open_time`     VARCHAR(64) DEFAULT NULL COMMENT '如 10:30-13:30,16:30-19:30',
  `min_amount`    INT NOT NULL DEFAULT 0 COMMENT '起送价（分）',
  `pack_fee`      INT NOT NULL DEFAULT 0 COMMENT '打包费（分）',
  `delivery_fee`  INT NOT NULL DEFAULT 200 COMMENT '基础配送费（分）',
  `commission_rate` DECIMAL(5,4) NOT NULL DEFAULT 0.0600 COMMENT '平台抽佣比例',
  `score`         DECIMAL(3,2) NOT NULL DEFAULT 5.00,
  `month_sales`   INT NOT NULL DEFAULT 0,
  `audit_status`  TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回 3冻结',
  `license_url`   VARCHAR(512) DEFAULT NULL,
  `permit_url`    VARCHAR(512) DEFAULT NULL COMMENT '食品经营许可证',
  `photos`        TEXT DEFAULT NULL COMMENT '门店照片 JSON 数组',
  `reject_reason` VARCHAR(255) DEFAULT NULL COMMENT '审核驳回原因',
  `settle_status` TINYINT NOT NULL DEFAULT 0 COMMENT '0未签约 1已签约',
  `created_at`    DATETIME NOT NULL,
  `updated_at`    DATETIME NOT NULL,
  `deleted_at`    DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_campus_cat` (`campus`,`category_id`,`biz_status`),
  KEY `idx_geo` (`lng`,`lat`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺';

CREATE TABLE IF NOT EXISTS `shop_category` (
  `id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`    VARCHAR(32) NOT NULL,
  `icon`    VARCHAR(512) DEFAULT NULL,
  `sort`    INT NOT NULL DEFAULT 0,
  `status`  TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺分类';

-- ---------- 4. 商品 ----------
CREATE TABLE IF NOT EXISTS `product_category` (
  `id`       BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id`  BIGINT UNSIGNED NOT NULL,
  `name`     VARCHAR(32) NOT NULL,
  `sort`     INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店内菜品分类';

CREATE TABLE IF NOT EXISTS `product` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `shop_id`     BIGINT UNSIGNED NOT NULL,
  `category_id` BIGINT UNSIGNED NOT NULL,
  `name`        VARCHAR(64) NOT NULL,
  `image`       VARCHAR(512) DEFAULT NULL,
  `description` VARCHAR(500) DEFAULT NULL,
  `price`       INT NOT NULL COMMENT '售价（分）',
  `origin_price` INT DEFAULT NULL COMMENT '划线价（分）',
  `stock`       INT NOT NULL DEFAULT 0 COMMENT '-1 表示不限库存',
  `sales`       INT NOT NULL DEFAULT 0,
  `has_sku`     TINYINT NOT NULL DEFAULT 0,
  `combo_items` TEXT DEFAULT NULL COMMENT '套餐组成 JSON [{productId,name,qty}]',
  `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '1上架 0下架',
  `sort`        INT NOT NULL DEFAULT 0,
  `created_at`  DATETIME NOT NULL,
  `updated_at`  DATETIME NOT NULL,
  `deleted_at`  DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop_status` (`shop_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品/菜品';

CREATE TABLE IF NOT EXISTS `product_sku` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `product_id` BIGINT UNSIGNED NOT NULL,
  `name`       VARCHAR(64) NOT NULL COMMENT '如 大份/加辣',
  `price`      INT NOT NULL COMMENT '分',
  `stock`      INT NOT NULL DEFAULT -1,
  `status`     TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品规格';

CREATE TABLE IF NOT EXISTS `cart_item` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `shop_id`    BIGINT UNSIGNED NOT NULL,
  `product_id` BIGINT UNSIGNED NOT NULL,
  `sku_id`     BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0 表示无规格',
  `qty`        INT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_sku` (`user_id`,`product_id`,`sku_id`),
  KEY `idx_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车';

-- ---------- 5. 订单 ----------
CREATE TABLE IF NOT EXISTS `orders` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_no`      VARCHAR(32) NOT NULL COMMENT '业务单号 202610231530xxxxxx',
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `shop_id`       BIGINT UNSIGNED NOT NULL,
  `rider_id`      BIGINT UNSIGNED DEFAULT NULL,
  `status`        TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 10待接单 20待出餐 30待取餐 40配送中 50已完成 60已取消 70售后中 80已退款',
  `campus`        TINYINT DEFAULT NULL,
  `address_json`  JSON NOT NULL COMMENT '下单地址快照，防止用户改地址影响历史单',
  `lng`           DECIMAL(10,7) NOT NULL,
  `lat`           DECIMAL(10,7) NOT NULL,
  `goods_amount`  INT NOT NULL COMMENT '商品金额（分）',
  `pack_fee`      INT NOT NULL DEFAULT 0,
  `delivery_fee`  INT NOT NULL DEFAULT 0,
  `discount_amount` INT NOT NULL DEFAULT 0 COMMENT '优惠券+满减',
  `pay_amount`    INT NOT NULL COMMENT '实付（分）',
  `commission`    INT NOT NULL DEFAULT 0 COMMENT '平台佣金（分）',
  `rider_income`  INT NOT NULL DEFAULT 0 COMMENT '骑手收入（分）',
  `shop_income`   INT NOT NULL DEFAULT 0 COMMENT '商家应得（分）',
  `coupon_id`     BIGINT UNSIGNED DEFAULT NULL,
  `remark`        VARCHAR(255) DEFAULT NULL,
  `expect_time`   DATETIME DEFAULT NULL COMMENT '用户期望送达',
  `pay_expire_at` DATETIME DEFAULT NULL COMMENT '支付超时时间',
  `paid_at`       DATETIME DEFAULT NULL,
  `accepted_at`   DATETIME DEFAULT NULL,
  `ready_at`      DATETIME DEFAULT NULL,
  `picked_at`     DATETIME DEFAULT NULL,
  `delivered_at`  DATETIME DEFAULT NULL,
  `finished_at`   DATETIME DEFAULT NULL,
  `cancel_reason` VARCHAR(255) DEFAULT NULL,
  `created_at`    DATETIME NOT NULL,
  `updated_at`    DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`,`status`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_rider` (`rider_id`,`status`),
  KEY `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单';

CREATE TABLE IF NOT EXISTS `order_item` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`   BIGINT UNSIGNED NOT NULL,
  `product_id` BIGINT UNSIGNED NOT NULL,
  `sku_id`     BIGINT UNSIGNED DEFAULT NULL,
  `name`       VARCHAR(64) NOT NULL COMMENT '下单时快照',
  `image`      VARCHAR(512) DEFAULT NULL,
  `price`      INT NOT NULL COMMENT '单价（分）',
  `qty`        INT NOT NULL,
  `subtotal`   INT NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细';

CREATE TABLE IF NOT EXISTS `order_status_log` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`      BIGINT UNSIGNED NOT NULL,
  `from_status`   TINYINT NOT NULL,
  `to_status`     TINYINT NOT NULL,
  `operator_type` TINYINT NOT NULL COMMENT '1用户 2商家 3骑手 4系统 5管理员',
  `operator_id`   BIGINT UNSIGNED DEFAULT NULL,
  `remark`        VARCHAR(255) DEFAULT NULL,
  `created_at`    DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单状态流水';

-- ---------- 6. 支付/退款 ----------
CREATE TABLE IF NOT EXISTS `payment` (
  `id`             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`       BIGINT UNSIGNED NOT NULL,
  `out_trade_no`   VARCHAR(32) NOT NULL,
  `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '微信支付单号',
  `amount`         INT NOT NULL,
  `status`         TINYINT NOT NULL DEFAULT 0 COMMENT '0待支付 1成功 2失败 3已关闭',
  `prepay_id`      VARCHAR(128) DEFAULT NULL,
  `profit_sharing` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已分账冻结 1是',
  `paid_at`        DATETIME DEFAULT NULL,
  `raw`            JSON DEFAULT NULL,
  `created_at`     DATETIME NOT NULL,
  `updated_at`     DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_out_trade_no` (`out_trade_no`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付单';

CREATE TABLE IF NOT EXISTS `refund` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT UNSIGNED NOT NULL,
  `payment_id`  BIGINT UNSIGNED NOT NULL,
  `refund_no`   VARCHAR(32) NOT NULL,
  `amount`      INT NOT NULL,
  `reason`      VARCHAR(255) DEFAULT NULL,
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0处理中 1成功 2失败',
  `operator_type` TINYINT DEFAULT NULL COMMENT '4系统 5管理员',
  `operator_id` BIGINT UNSIGNED DEFAULT NULL,
  `refunded_at` DATETIME DEFAULT NULL,
  `created_at`  DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refund_no` (`refund_no`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款单';

-- ---------- 7. 骑手 ----------
CREATE TABLE IF NOT EXISTS `rider` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `user_id`       BIGINT UNSIGNED NOT NULL COMMENT '骑手也是小程序用户',
  `real_name_enc` VARCHAR(256) NOT NULL COMMENT 'AES-GCM 密文（Base64）',
  `id_card_enc`   VARCHAR(256) NOT NULL COMMENT 'AES-GCM 密文（Base64）',
  `stu_no_enc`    VARCHAR(256) NOT NULL COMMENT 'AES-GCM 密文（Base64）',
  `stu_no_hash`   CHAR(64) DEFAULT NULL COMMENT '学号/工号 SHA-256，用于查重',
  `phone_mask`    VARCHAR(20) DEFAULT NULL,
  `id_card_front` VARCHAR(512) DEFAULT NULL,
  `id_card_back`  VARCHAR(512) DEFAULT NULL,
  `student_card`  VARCHAR(512) DEFAULT NULL,
  `agreement_url` VARCHAR(512) DEFAULT NULL COMMENT '电子兼职协议存证',
  `audit_status`  TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1通过 2驳回',
  `work_status`   TINYINT NOT NULL DEFAULT 0 COMMENT '0离线 1接单中 2忙碌',
  `deposit`       INT NOT NULL DEFAULT 0 COMMENT '保证金（分）',
  `max_concurrent` TINYINT NOT NULL DEFAULT 3 COMMENT '同时最多接单数',
  `score`         DECIMAL(3,2) NOT NULL DEFAULT 5.00,
  `total_orders`  INT NOT NULL DEFAULT 0,
  `timeout_orders` INT NOT NULL DEFAULT 0,
  `created_at`    DATETIME NOT NULL,
  `updated_at`    DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user` (`user_id`),
  KEY `idx_audit` (`audit_status`,`work_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骑手';

CREATE TABLE IF NOT EXISTS `delivery_task` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`    BIGINT UNSIGNED NOT NULL,
  `rider_id`    BIGINT UNSIGNED DEFAULT NULL,
  `assign_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1抢单 2派单',
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0待接 1已接 2已取餐 3已送达 4已取消',
  `pickup_photo`  VARCHAR(512) DEFAULT NULL,
  `deliver_photo` VARCHAR(512) DEFAULT NULL,
  `grab_at`     DATETIME DEFAULT NULL,
  `pickup_at`   DATETIME DEFAULT NULL,
  `delivered_at` DATETIME DEFAULT NULL,
  `timeout_flag` TINYINT NOT NULL DEFAULT 0 COMMENT '是否超时',
  `created_at`  DATETIME NOT NULL,
  `updated_at`  DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_rider_status` (`rider_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送任务';

CREATE TABLE IF NOT EXISTS `rider_location` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `rider_id`    BIGINT UNSIGNED NOT NULL,
  `order_id`    BIGINT UNSIGNED DEFAULT NULL,
  `lng`         DECIMAL(10,7) NOT NULL,
  `lat`         DECIMAL(10,7) NOT NULL,
  `accuracy`    INT DEFAULT NULL COMMENT '米',
  `speed`       DECIMAL(5,2) DEFAULT NULL,
  `reported_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rider_time` (`rider_id`,`reported_at`),
  KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骑手轨迹（按需归档/分区）';

-- ---------- 8. 资金 ----------
CREATE TABLE IF NOT EXISTS `wallet` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `owner_type` TINYINT NOT NULL COMMENT '2商家 3骑手（用户钱包一期不做）',
  `owner_id`   BIGINT UNSIGNED NOT NULL,
  `balance`    INT NOT NULL DEFAULT 0 COMMENT '可用余额（分）',
  `frozen`     INT NOT NULL DEFAULT 0 COMMENT '冻结（分）',
  `version`    INT NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_owner` (`owner_type`,`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='钱包';

CREATE TABLE IF NOT EXISTS `wallet_flow` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `wallet_id`     BIGINT UNSIGNED NOT NULL,
  `biz_type`      VARCHAR(32) NOT NULL COMMENT 'ORDER_INCOME/COMMISSION/PENALTY/WITHDRAW/REFUND',
  `biz_no`        VARCHAR(64) NOT NULL,
  `direction`     TINYINT NOT NULL COMMENT '1入账 -1出账',
  `amount`        INT NOT NULL,
  `balance_after` INT NOT NULL,
  `remark`        VARCHAR(255) DEFAULT NULL,
  `created_at`    DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_biz` (`biz_type`,`biz_no`),
  KEY `idx_wallet_time` (`wallet_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资金流水（幂等键 biz_type+biz_no）';

CREATE TABLE IF NOT EXISTS `settlement` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`     BIGINT UNSIGNED NOT NULL,
  `shop_id`      BIGINT UNSIGNED NOT NULL,
  `rider_id`     BIGINT UNSIGNED DEFAULT NULL,
  `goods_amount` INT NOT NULL,
  `commission`   INT NOT NULL,
  `shop_income`  INT NOT NULL,
  `rider_income` INT NOT NULL,
  `status`       TINYINT NOT NULL DEFAULT 0 COMMENT '0待结算 1已结算 2已撤销',
  `settle_at`    DATETIME DEFAULT NULL,
  `created_at`   DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_shop` (`shop_id`,`status`),
  KEY `idx_rider` (`rider_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单';

CREATE TABLE IF NOT EXISTS `withdraw` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `owner_type` TINYINT NOT NULL COMMENT '2商家 3骑手',
  `owner_id`   BIGINT UNSIGNED NOT NULL,
  `amount`     INT NOT NULL,
  `status`     TINYINT NOT NULL DEFAULT 0 COMMENT '0待审 1打款中 2成功 3驳回',
  `channel`    VARCHAR(32) DEFAULT 'WXPAY' COMMENT 'WXPAY/BANK',
  `out_batch_no` VARCHAR(32) DEFAULT NULL,
  `fail_reason` VARCHAR(255) DEFAULT NULL,
  `applied_at` DATETIME NOT NULL,
  `audited_at` DATETIME DEFAULT NULL,
  `paid_at`    DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`owner_type`,`owner_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现申请';

-- ---------- 9. 营销 ----------
CREATE TABLE IF NOT EXISTS `coupon` (
  `id`        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`      VARCHAR(64) NOT NULL,
  `type`      TINYINT NOT NULL DEFAULT 1 COMMENT '1满减 2折扣',
  `threshold` INT NOT NULL DEFAULT 0 COMMENT '门槛（分）',
  `amount`    INT NOT NULL COMMENT '减免（分）或折扣千分比',
  `total`     INT NOT NULL DEFAULT 0,
  `remain`    INT NOT NULL DEFAULT 0,
  `scope`     TINYINT NOT NULL DEFAULT 1 COMMENT '1全场 2指定店铺',
  `shop_id`   BIGINT UNSIGNED DEFAULT NULL,
  `start_at`  DATETIME NOT NULL,
  `end_at`    DATETIME NOT NULL,
  `status`    TINYINT NOT NULL DEFAULT 1,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_time` (`start_at`,`end_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板';

CREATE TABLE IF NOT EXISTS `user_coupon` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `coupon_id`     BIGINT UNSIGNED NOT NULL,
  `user_id`       BIGINT UNSIGNED NOT NULL,
  `status`        TINYINT NOT NULL DEFAULT 0 COMMENT '0未使用 1已使用 2已过期',
  `used_order_id` BIGINT UNSIGNED DEFAULT NULL,
  `got_at`        DATETIME NOT NULL,
  `used_at`       DATETIME DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户券';

-- ---------- 10. 评价与售后 ----------
CREATE TABLE IF NOT EXISTS `review` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`   BIGINT UNSIGNED NOT NULL,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `shop_id`    BIGINT UNSIGNED NOT NULL,
  `rider_id`   BIGINT UNSIGNED DEFAULT NULL,
  `shop_score` TINYINT NOT NULL COMMENT '1-5',
  `rider_score` TINYINT DEFAULT NULL,
  `content`    VARCHAR(500) DEFAULT NULL,
  `images`     JSON DEFAULT NULL,
  `is_anonymous` TINYINT NOT NULL DEFAULT 0,
  `reply`      VARCHAR(500) DEFAULT NULL,
  `status`     TINYINT NOT NULL DEFAULT 1 COMMENT '1显示 0隐藏(违规)',
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order` (`order_id`),
  KEY `idx_shop` (`shop_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价';

CREATE TABLE IF NOT EXISTS `after_sale` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `order_id`   BIGINT UNSIGNED NOT NULL,
  `user_id`    BIGINT UNSIGNED NOT NULL,
  `type`       TINYINT NOT NULL COMMENT '1少餐 2漏餐 3变质 4撒漏 5地址错误 6其他',
  `reason`     VARCHAR(500) NOT NULL,
  `images`     JSON DEFAULT NULL,
  `amount`     INT NOT NULL DEFAULT 0 COMMENT '申请退款金额（分）',
  `status`     TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2已退款 3已驳回 4已关闭',
  `liable_party` TINYINT DEFAULT NULL COMMENT '1商家 2骑手 3用户',
  `judge_remark` VARCHAR(500) DEFAULT NULL,
  `operator_id` BIGINT UNSIGNED DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order` (`order_id`),
  KEY `idx_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后工单';

-- ---------- 11. 平台配置 ----------
CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `config_key`   VARCHAR(64) NOT NULL,
  `config_value` TEXT NOT NULL,
  `value_type`   VARCHAR(16) NOT NULL DEFAULT 'string' COMMENT 'string/int/bool/json',
  `group_key`    VARCHAR(32) NOT NULL DEFAULT 'base',
  `remark`       VARCHAR(255) DEFAULT NULL,
  `updated_by`   BIGINT UNSIGNED DEFAULT NULL,
  `updated_at`   DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台配置（开关都放这里，禁止写死）';

CREATE TABLE IF NOT EXISTS `fence` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `name`       VARCHAR(64) NOT NULL,
  `campus`     TINYINT NOT NULL COMMENT '1东 2西',
  `polygon`    POLYGON NOT NULL COMMENT 'SRID 0 平面坐标，按 (lng,lat) 顺序书写，见 seed.sql 注释',
  `status`     TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `created_at` DATETIME NOT NULL,
  `updated_at` DATETIME NOT NULL,
  SPATIAL INDEX `spx_polygon` (`polygon`),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='校园配送围栏（多边形）';

CREATE TABLE IF NOT EXISTS `announcement` (
  `id`         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `title`      VARCHAR(128) NOT NULL,
  `content`    TEXT NOT NULL,
  `position`   TINYINT NOT NULL DEFAULT 1 COMMENT '1首页 2用户中心',
  `status`     TINYINT NOT NULL DEFAULT 1,
  `start_at`   DATETIME DEFAULT NULL,
  `end_at`     DATETIME DEFAULT NULL,
  `created_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台公告';

-- ---------- 12. 后台账号 ----------
CREATE TABLE IF NOT EXISTS `admin_user` (
  `id`            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `username`      VARCHAR(32) NOT NULL,
  `password_hash` VARCHAR(128) NOT NULL COMMENT 'BCrypt',
  `real_name`     VARCHAR(32) NOT NULL,
  `role`          VARCHAR(32) NOT NULL COMMENT 'SUPER/CS/FINANCE/AUDITOR/OPERATOR',
  `phone_mask`    VARCHAR(20) DEFAULT NULL,
  `status`        TINYINT NOT NULL DEFAULT 1,
  `last_login_at` DATETIME DEFAULT NULL,
  `created_at`    DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='后台账号';

CREATE TABLE IF NOT EXISTS `notify_log` (
  `id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  `target_type` TINYINT NOT NULL COMMENT '1用户 2商家 3骑手',
  `target_id`   BIGINT UNSIGNED NOT NULL,
  `template_id` VARCHAR(64) DEFAULT NULL,
  `biz_no`      VARCHAR(64) NOT NULL,
  `channel`     VARCHAR(16) NOT NULL DEFAULT 'SUBSCRIBE',
  `status`      TINYINT NOT NULL DEFAULT 0 COMMENT '0待发 1成功 2失败',
  `error`       VARCHAR(255) DEFAULT NULL,
  `created_at`  DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_biz` (`biz_no`),
  KEY `idx_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息发送日志';

SET FOREIGN_KEY_CHECKS = 1;
