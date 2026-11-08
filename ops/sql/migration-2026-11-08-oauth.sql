-- ============================================================
-- 2026-11-08 平台侧（H5 / 浏览器）登录与支付支持
-- 1) user 补 unionid：同一自然人在小程序与公众号之间是同一个用户
-- 2) user 补 mp_openid：公众号网页授权拿到的 openid（与小程序 openid 不同）
-- 3) payment 补 scene：区分 MINIAPP / H5 / NATIVE 三种下单通道
-- 说明：migration-2026-10-24-fix4.sql 曾因「从未写入」删掉 unionid，
--       接入公众号登录后它必须存在，这里加回来并建唯一索引。
-- ============================================================
SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS add_col_oauth;
DELIMITER //
CREATE PROCEDURE add_col_oauth(IN tbl VARCHAR(64), IN col VARCHAR(64), IN ddl VARCHAR(512))
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

CALL add_col_oauth('user', 'unionid', "VARCHAR(64) DEFAULT NULL COMMENT '微信 unionid，跨小程序/公众号唯一'");
CALL add_col_oauth('user', 'mp_openid', "VARCHAR(64) DEFAULT NULL COMMENT '公众号网页授权 openid'");
CALL add_col_oauth('payment', 'scene', "VARCHAR(16) DEFAULT NULL COMMENT 'MINIAPP/H5/NATIVE 下单通道'");

DROP PROCEDURE IF EXISTS add_col_oauth;

-- unionid 唯一索引（允许 NULL 重复，MySQL 唯一索引天然支持多 NULL）
SET @idx := (SELECT COUNT(*) FROM information_schema.statistics
             WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_unionid');
SET @sql := IF(@idx = 0,
  'ALTER TABLE `user` ADD UNIQUE KEY `uk_unionid` (`unionid`)', 'SELECT 1');
PREPARE s2 FROM @sql;
EXECUTE s2;
DEALLOCATE PREPARE s2;

SET @idx2 := (SELECT COUNT(*) FROM information_schema.statistics
              WHERE table_schema = DATABASE() AND table_name = 'user' AND index_name = 'uk_mp_openid');
SET @sql2 := IF(@idx2 = 0,
  'ALTER TABLE `user` ADD UNIQUE KEY `uk_mp_openid` (`mp_openid`)', 'SELECT 1');
PREPARE s3 FROM @sql2;
EXECUTE s3;
DEALLOCATE PREPARE s3;
