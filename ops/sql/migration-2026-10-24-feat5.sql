-- ============================================================
-- 功能完善迁移 5：限时特价 / 首页 Banner / 用户反馈
-- 幂等：可重复执行
-- ============================================================

DROP PROCEDURE IF EXISTS add_col5;
DELIMITER //
CREATE PROCEDURE add_col5(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(512))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
        SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN ', ddl);
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 菜品限时特价：special_price 为特价（分），在 [special_start_at, special_end_at] 内生效
CALL add_col5('product', 'special_price', '`special_price` INT NULL COMMENT ''限时特价（分），NULL/0 表示无特价''');
CALL add_col5('product', 'special_start_at', '`special_start_at` DATETIME NULL COMMENT ''特价开始''');
CALL add_col5('product', 'special_end_at', '`special_end_at` DATETIME NULL COMMENT ''特价结束''');

DROP PROCEDURE IF EXISTS add_col5;

-- 首页运营位（Banner）
CREATE TABLE IF NOT EXISTS `banner` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `title` varchar(64) NOT NULL,
  `image` varchar(512) NOT NULL COMMENT '图片地址',
  `link_type` tinyint NOT NULL DEFAULT 1 COMMENT '1 无跳转 2 店铺 3 优惠券中心',
  `link_value` varchar(64) DEFAULT NULL COMMENT '店铺 id 等',
  `sort` int NOT NULL DEFAULT 0,
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='首页 Banner';

-- 用户反馈 / 投诉
CREATE TABLE IF NOT EXISTS `feedback` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `user_id` bigint unsigned NOT NULL,
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '1 意见建议 2 投诉 3 支付问题 4 其他',
  `content` varchar(1000) NOT NULL,
  `images` text COMMENT '图片 URL JSON 数组',
  `contact` varchar(64) DEFAULT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 待处理 1 处理中 2 已处理',
  `reply` varchar(1000) DEFAULT NULL,
  `operator_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL,
  `handled_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户反馈';

-- 恢复「售后受理期」配置（这次是真实现了，不再是假开关）
INSERT INTO sys_config (config_key, config_value, value_type, group_key, remark, updated_at) VALUES
 ('order.complaint_days', '3', 'int', 'order', '售后受理期（天）：订单完成后超过该天数不再受理售后', NOW())
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

SELECT 'migration5 done' AS result;
