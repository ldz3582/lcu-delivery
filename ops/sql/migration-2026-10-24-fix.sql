-- ============================================================
-- 验收整改迁移（2026-10-24）
-- 内容：店铺公告/推送开关/配送范围/拒单计数、结算冲正字段、订单违约金字段、
--       整改后新增的平台配置项、晚高峰窗口对齐需求
-- 幂等：可重复执行（先查 information_schema 再 ADD COLUMN）
-- ============================================================

DROP PROCEDURE IF EXISTS add_column_if_missing;
DELIMITER //
CREATE PROCEDURE add_column_if_missing(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(512))
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

-- 商家：店铺公告 / 推送开关 / 配送范围 / 拒单计数
CALL add_column_if_missing('shop', 'notice', '`notice` VARCHAR(500) NULL COMMENT ''店铺公告'' AFTER `open_time`');
CALL add_column_if_missing('shop', 'push_enabled', '`push_enabled` TINYINT NOT NULL DEFAULT 1 COMMENT ''接收消息推送开关'' AFTER `notice`');
CALL add_column_if_missing('shop', 'delivery_range_meters', '`delivery_range_meters` INT NULL COMMENT ''配送范围（米），NULL=跟随平台'' AFTER `delivery_fee`');
CALL add_column_if_missing('shop', 'reject_count', '`reject_count` INT NOT NULL DEFAULT 0 COMMENT ''累计拒单次数'' AFTER `push_enabled`');

-- 结算：冲正
CALL add_column_if_missing('settlement', 'reverse_reason', '`reverse_reason` VARCHAR(255) NULL COMMENT ''冲正原因''');
CALL add_column_if_missing('settlement', 'reversed_at', '`reversed_at` DATETIME NULL COMMENT ''冲正时间''');

-- 订单：取消违约金
CALL add_column_if_missing('orders', 'penalty_amount', '`penalty_amount` INT NOT NULL DEFAULT 0 COMMENT ''用户取消违约金（分）'' AFTER `discount_amount`');

DROP PROCEDURE IF EXISTS add_column_if_missing;

-- ============================================================
-- 新增平台配置项
-- ============================================================
INSERT INTO sys_config (config_key, config_value, value_type, group_key, remark, updated_at) VALUES
 ('order.accept_timeout_minutes', '10', 'int', 'order', '商家接单超时（分钟），超时自动取消并退款', NOW()),
 ('order.cancel_penalty_rate', '0.20', 'string', 'order', '商家接单后用户取消的违约金比例（0~1）', NOW()),
 ('delivery.fee_mode', 'shop', 'string', 'delivery', '配送费模式：shop=按店铺设置 / platform=统一按平台基础费', NOW()),
 ('delivery.rider_distance_free_meters', '800', 'int', 'delivery', '骑手佣金：免费距离段（米）', NOW()),
 ('delivery.rider_distance_step_meters', '500', 'int', 'delivery', '骑手佣金：每档距离（米）', NOW()),
 ('delivery.rider_distance_step_bonus', '25', 'int', 'delivery', '骑手佣金：每档距离加价（分）', NOW()),
 ('delivery.rider_distance_bonus_max', '100', 'int', 'delivery', '骑手佣金：距离加价上限（分）', NOW()),
 ('delivery.rider_income_min', '150', 'int', 'delivery', '骑手单均佣金下限（分）', NOW()),
 ('delivery.rider_income_max', '300', 'int', 'delivery', '骑手单均佣金上限（分）', NOW()),
 ('merchant.reject_limit', '3', 'int', 'merchant', '商家累计拒单达到该次数自动暂停营业', NOW())
ON DUPLICATE KEY UPDATE remark = VALUES(remark);

-- 晚高峰窗口对齐需求（17:30-19:00）
UPDATE sys_config SET config_value = '11:00-13:00,17:30-19:00'
 WHERE config_key = 'delivery.dispatch_peak_windows';

SELECT 'migration done' AS result;
