-- ============================================================
-- 验收整改迁移 2：骑手封禁状态、商家处罚表
-- 幂等：可重复执行
-- ============================================================

DROP PROCEDURE IF EXISTS add_column_if_missing2;
DELIMITER //
CREATE PROCEDURE add_column_if_missing2(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(512))
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

-- 骑手账号状态：1 正常 0 封禁（封禁后不能接单）
CALL add_column_if_missing2('rider', 'status', '`status` TINYINT NOT NULL DEFAULT 1 COMMENT ''1 正常 0 封禁'' AFTER `audit_status`');
CALL add_column_if_missing2('rider', 'ban_reason', '`ban_reason` VARCHAR(255) NULL COMMENT ''封禁原因'' AFTER `status`');

DROP PROCEDURE IF EXISTS add_column_if_missing2;

-- 商家违规处罚记录（警告/罚款/下架商品/暂停营业）
CREATE TABLE IF NOT EXISTS `merchant_penalty` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `shop_id` bigint unsigned NOT NULL,
  `type` tinyint NOT NULL COMMENT '1 警告 2 罚款 3 下架商品 4 暂停营业',
  `amount` int NOT NULL DEFAULT 0 COMMENT '罚款金额（分）',
  `remark` varchar(255) DEFAULT NULL,
  `order_id` bigint unsigned DEFAULT NULL,
  `operator_id` bigint unsigned DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_shop` (`shop_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家违规处罚';

-- 抽佣差异化默认值（校内 6% / 校外小吃街 10%）
INSERT INTO sys_config (config_key, config_value, value_type, group_key, remark, updated_at) VALUES
 ('commission.rate_campus', '0.0600', 'string', 'commission', '校内店铺默认抽佣比例', NOW()),
 ('commission.rate_off_campus', '0.1000', 'string', 'commission', '校外小吃街默认抽佣比例', NOW())
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

SELECT 'migration2 done' AS result;
