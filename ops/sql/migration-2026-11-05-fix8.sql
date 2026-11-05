-- ============================================================
-- 修复迁移 8：骑手「接单时段」字段
-- 现象：全新库按 schema + 迁移初始化后，骑手抢单大厅 / 接单设置接口 500，
--       报 Unknown column 'work_time' in 'field list'
-- 原因：Rider 实体（delivery/entity/Rider.java）与骑手设置接口都有 workTime，
--       但 schema.sql 和此前所有迁移都没建这一列，只有早期手工改过的库里存在。
-- 影响：骑手端「抢单大厅」(GET /api/rider/pool)、
--       「接单设置」(PUT /api/rider/settings) 直接不可用。
-- 幂等：可重复执行
-- ============================================================
DROP PROCEDURE IF EXISTS add_col8;
DELIMITER //
CREATE PROCEDURE add_col8()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = DATABASE() AND table_name = 'rider' AND column_name = 'work_time') THEN
        ALTER TABLE `rider` ADD COLUMN `work_time` VARCHAR(64) NULL
            COMMENT '接单时段：如 09:00-14:00,16:30-21:00' AFTER `work_status`;
    END IF;
END //
DELIMITER ;
CALL add_col8();
DROP PROCEDURE add_col8;

SELECT 'migration8 done' AS result;
