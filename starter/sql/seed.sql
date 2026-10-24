-- ============================================================
-- 初始化数据（一期）
-- 注意：fence 里的坐标只是格式示例，必须用腾讯地图「坐标拾取器」
--       沿聊大东/西校区围墙逐点取点后替换，否则围栏判定无意义。
-- ============================================================
SET NAMES utf8mb4;

INSERT INTO `shop_category` (`name`,`sort`,`status`) VALUES
  ('食堂美食', 10, 1),
  ('小吃街',   20, 1),
  ('饮品水果', 30, 1),
  ('超市零食', 40, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 平台开关：后台可改，App 启动时拉取
INSERT INTO `sys_config` (`config_key`,`config_value`,`value_type`,`group_key`,`remark`,`updated_at`) VALUES
  ('feature.identity_check','false','bool','feature','身份核验开关：开启后下单需学号/工号校验','2026-10-23 00:00:00'),
  ('feature.address_parse','true','bool','feature','地址粘贴解析开关','2026-10-23 00:00:00'),
  ('feature.rider_background_location','false','bool','feature','骑手后台定位（审核严格，一期限前台）','2026-10-23 00:00:00'),
  ('order.pay_timeout_minutes','15','int','order','未支付自动取消时长','2026-10-23 00:00:00'),
  ('order.auto_confirm_hours','24','int','order','送达后自动确认收货','2026-10-23 00:00:00'),
  ('order.complaint_days','3','int','order','售后受理期（天）','2026-10-23 00:00:00'),
  ('settle.freeze_days','1','int','settle','T+N 结算冻结天数','2026-10-23 00:00:00'),
  ('settle.min_withdraw','1000','int','settle','最低提现金额，单位分','2026-10-23 00:00:00'),
  ('delivery.base_fee','200','int','delivery','基础配送费（分）','2026-10-23 00:00:00'),
  ('delivery.rider_base_income','150','int','delivery','骑手基础佣金（分/单）','2026-10-23 00:00:00'),
  ('delivery.peak_bonus','50','int','delivery','午晚高峰加价（分/单）','2026-10-23 00:00:00'),
  ('delivery.max_concurrent','3','int','delivery','骑手最大同时接单数','2026-10-23 00:00:00'),
  ('delivery.fence_enforce','true','bool','delivery','提交订单是否强制校验围栏','2026-10-23 00:00:00')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- 围栏示例：一个四边形，点序为 (lng lat)，首尾闭合
-- 为什么不用 SRID 4326：MySQL 对 4326 按「纬度,经度」轴序解释，容易写反；
-- 校园尺度下用 SRID 0 平面坐标做点面包含判断足够精确，也更快。
-- 真实数据必须用腾讯地图坐标拾取器沿围墙取点后替换。
INSERT INTO `fence` (`name`,`campus`,`polygon`,`status`,`created_at`,`updated_at`)
VALUES (
  '东校区（示例，必须替换）', 1,
  ST_GeomFromText('POLYGON((115.9850 36.4340,115.9950 36.4340,115.9950 36.4420,115.9850 36.4420,115.9850 36.4340))'),
  1, NOW(), NOW()
);

-- 后台初始账号不在这里种：密码哈希应由后端启动时用 BCrypt 生成并打印一次性初始密码，
-- 避免把固定哈希（等于固定密码）写进代码库。见 README「后台账号」一节。
