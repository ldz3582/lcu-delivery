-- ============================================================
-- 修复迁移 7：下单幂等键
-- client_token 由前端每次结算生成一次；同 (user_id, client_token) 唯一，
-- 双击/网络重试/并发提交都只会落一单
-- 幂等
-- ============================================================
DROP PROCEDURE IF EXISTS add_col7;
DELIMITER //
CREATE PROCEDURE add_col7()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'client_token') THEN
        ALTER TABLE `orders` ADD COLUMN `client_token` VARCHAR(64) NULL
            COMMENT '前端幂等键，防重复下单' AFTER `order_no`;
        ALTER TABLE `orders` ADD UNIQUE KEY `uk_user_client_token` (`user_id`, `client_token`);
    END IF;
END //
DELIMITER ;
CALL add_col7();
DROP PROCEDURE add_col7;

SELECT 'migration7 done' AS result;
