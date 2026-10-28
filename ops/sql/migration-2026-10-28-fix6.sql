-- ============================================================
-- 复检修复迁移 6：售罄自动下架标记
-- auto_offline=1 表示该商品是被「库存售罄」自动下架的；
-- 库存因取消订单回补到 >0 时，自动上架并清标记（手动下架的商品不受影响）
-- 幂等
-- ============================================================
DROP PROCEDURE IF EXISTS add_col6;
DELIMITER //
CREATE PROCEDURE add_col6()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'product' AND column_name = 'auto_offline') THEN
        ALTER TABLE `product` ADD COLUMN `auto_offline` TINYINT NOT NULL DEFAULT 0
            COMMENT '1 = 因售罄自动下架，库存回补时自动上架' AFTER `status`;
    END IF;
END //
DELIMITER ;
CALL add_col6();
DROP PROCEDURE add_col6;

SELECT 'migration6 done' AS result;
