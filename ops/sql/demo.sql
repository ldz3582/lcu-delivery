-- ============================================================
-- 演示数据：一家营业中的食堂档口 + 一个骑手 + 一个测试用户
-- 仅用于本地联调，跑通用 curl 或小程序走完“下单 → 出餐 → 送达”。
-- 真实环境不要执行本文件。
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO `user` (`id`,`openid`,`nickname`,`role`,`status`,`created_at`,`updated_at`) VALUES
  (1,'mock_merchant','一食堂·黄焖鸡','MERCHANT',1,NOW(),NOW()),
  (2,'mock_rider','骑手小张','RIDER',1,NOW(),NOW()),
  (3,'mock_user','测试同学','USER',1,NOW(),NOW())
ON DUPLICATE KEY UPDATE `nickname` = VALUES(`nickname`);

INSERT INTO `shop` (`id`,`owner_user_id`,`name`,`logo`,`category_id`,`campus`,`address`,`lng`,`lat`,
                    `contact_phone`,`biz_status`,`open_time`,`min_amount`,`pack_fee`,`delivery_fee`,
                    `commission_rate`,`score`,`month_sales`,`audit_status`,`settle_status`,`created_at`,`updated_at`)
VALUES (1,1,'一食堂·黄焖鸡','',1,1,'聊大东校区一食堂 2 楼 12 号窗口',115.9900,36.4380,
        '13800000001',1,'10:30-13:30,16:30-19:30',1000,100,200,0.0600,4.80,326,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `product_category` (`id`,`shop_id`,`name`,`sort`) VALUES
  (1,1,'招牌',10),(2,1,'饮料',20)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `product` (`id`,`shop_id`,`category_id`,`name`,`image`,`description`,`price`,`origin_price`,
                       `stock`,`sales`,`has_sku`,`status`,`sort`,`created_at`,`updated_at`) VALUES
  (1,1,1,'黄焖鸡米饭（小份）','','鸡腿肉 + 米饭，微辣',1500,1800,100,210,0,1,10,NOW(),NOW()),
  (2,1,1,'黄焖鸡米饭（大份）','','加量鸡腿肉',1800,2200,100,180,0,1,20,NOW(),NOW()),
  (3,1,1,'黄焖排骨饭','','排骨 + 土豆',2000,2400,50,96,0,1,30,NOW(),NOW()),
  (4,1,2,'冰红茶','','500ml',300,400,-1,320,0,1,40,NOW(),NOW())
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `rider` (`id`,`user_id`,`real_name_enc`,`id_card_enc`,`stu_no_enc`,`phone_mask`,
                     `audit_status`,`work_status`,`deposit`,`max_concurrent`,`score`,`total_orders`,
                     `timeout_orders`,`created_at`,`updated_at`)
VALUES (1,2,'demo','demo','demo','138****0002',1,1,0,3,4.95,128,2,NOW(),NOW())
ON DUPLICATE KEY UPDATE `audit_status` = VALUES(`audit_status`);

-- 测试用户的收货地址（坐标在东校区围栏内）
INSERT INTO `user_address` (`id`,`user_id`,`campus`,`detail`,`building`,`floor`,`room`,`remark`,
                            `lng`,`lat`,`in_fence`,`parse_source`,`is_default`,`created_at`,`updated_at`)
VALUES (1,3,1,'东校区 3 号宿舍楼 502','3号楼','5','502','放宿舍门口',115.9905,36.4385,1,1,1,NOW(),NOW())
ON DUPLICATE KEY UPDATE `detail` = VALUES(`detail`);
