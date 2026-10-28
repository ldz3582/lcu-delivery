-- ============================================================
-- 验收整改迁移 4：删除死列 / 死配置项
-- 依据：.local/reports/dead-code.md 第五、四节
-- 幂等：可重复执行
-- ============================================================

DROP PROCEDURE IF EXISTS drop_column_if_exists;
DELIMITER //
CREATE PROCEDURE drop_column_if_exists(IN tbl VARCHAR(64), IN col VARCHAR(64))
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
        SET @s = CONCAT('ALTER TABLE `', tbl, '` DROP COLUMN `', col, '`');
        PREPARE stmt FROM @s;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END //
DELIMITER ;

-- 代码/前端零读写的列
CALL drop_column_if_exists('withdraw', 'out_batch_no');        -- 微信转账批次号预留，从未写入
CALL drop_column_if_exists('payment', 'raw');                  -- 支付回调原文，从未写入
CALL drop_column_if_exists('user', 'unionid');                 -- 微信 unionid，从未写入
CALL drop_column_if_exists('rider_location', 'speed');         -- 上报不带速度
CALL drop_column_if_exists('delivery_task', 'timeout_flag');   -- 超时由时间实时计算

DROP PROCEDURE IF EXISTS drop_column_if_exists;

-- 无代码读取的配置项（后台配置页不再显示这些"假开关"）
DELETE FROM sys_config WHERE config_key IN (
  'feature.rider_background_location',  -- 后台持续定位未实现
  'order.auto_confirm_hours',           -- 自动确认收货未实现
  'order.complaint_days',               -- 售后时效拦截未实现
  'delivery.max_concurrent'             -- 已由 rider.max_concurrent 取代
);

SELECT 'migration4 done' AS result;
