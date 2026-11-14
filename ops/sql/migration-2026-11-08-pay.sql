-- ============================================================
-- 2026-11-08 支付 A 方案（平台代收）真实通道落地
-- 1) refund 表补微信退款单号与失败原因，便于对账与排查
-- 2) 幂等：列已存在则跳过
-- ============================================================
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_col_pay;
DELIMITER //
CREATE PROCEDURE add_col_pay(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(512))
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = tbl AND column_name = col) THEN
    SET @s = CONCAT('ALTER TABLE `', tbl, '` ADD COLUMN `', col, '` ', ddl);
    PREPARE stmt FROM @s;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END //
DELIMITER ;

CALL add_col_pay('refund', 'refund_id', "VARCHAR(64) DEFAULT NULL COMMENT '微信退款单号'");
CALL add_col_pay('refund', 'fail_reason', "VARCHAR(255) DEFAULT NULL COMMENT '退款失败原因'");

DROP PROCEDURE IF EXISTS add_col_pay;
