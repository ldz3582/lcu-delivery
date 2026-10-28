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
  ('order.pay_timeout_minutes','15','int','order','未支付自动取消时长','2026-10-23 00:00:00'),
  ('settle.freeze_days','1','int','settle','T+N 结算冻结天数','2026-10-23 00:00:00'),
  ('settle.min_withdraw','1000','int','settle','最低提现金额，单位分','2026-10-23 00:00:00'),
  ('delivery.base_fee','200','int','delivery','基础配送费（分）','2026-10-23 00:00:00'),
  ('delivery.rider_base_income','150','int','delivery','骑手基础佣金（分/单）','2026-10-23 00:00:00'),
  ('delivery.peak_bonus','50','int','delivery','午晚高峰加价（分/单）','2026-10-23 00:00:00'),
  ('delivery.eta_minutes','25','int','delivery','默认预计送达时长（分钟，无骑手定位时用）','2026-10-23 00:00:00'),
  ('delivery.dispatch_enabled','false','bool','delivery','智能派单开关（关闭则只走抢单大厅）','2026-10-23 00:00:00'),
  ('delivery.dispatch_peak_windows','11:00-13:00,17:00-19:00','string','delivery','派单高峰时段，逗号分隔','2026-10-23 00:00:00'),
  ('delivery.dispatch_timeout_seconds','120','int','delivery','派单确认超时（秒），超时退回抢单池','2026-10-23 00:00:00'),
  ('delivery.timeout_minutes','45','int','delivery','出餐到送达的超时判定（分钟），超时自动扣罚','2026-10-23 00:00:00'),
  ('delivery.timeout_penalty','200','int','delivery','超时扣罚金额（分）','2026-10-23 00:00:00'),
  ('delivery.bad_review_penalty','300','int','delivery','差评（≤2星）扣罚金额（分）','2026-10-23 00:00:00'),
  ('wx.template.order_status','','string','notify','订单状态订阅消息模板 ID（公众平台-订阅消息里复制）','2026-10-23 00:00:00'),
  ('wx.template.coupon','','string','notify','优惠券订阅消息模板 ID','2026-10-23 00:00:00'),
  ('delivery.fence_enforce','true','bool','delivery','提交订单是否强制校验围栏','2026-10-23 00:00:00')
ON DUPLICATE KEY UPDATE `config_value` = VALUES(`config_value`);

-- 围栏：聊城大学东/西两个校区的真实轮廓（OpenStreetMap way 328518268 / 328518269）
-- 重要：已由 WGS-84 转成 **GCJ-02**（国测局坐标系），因为微信小程序地图与 getLocation
-- 都使用 GCJ-02；直接存 WGS-84 会导致围栏整体偏移约 500 米。
-- 点序为 (lng lat)，首尾闭合，SRID 0 平面坐标。
INSERT INTO `fence` (`name`,`campus`,`polygon`,`status`,`created_at`,`updated_at`)
VALUES
  ('聊城大学东校区', 1, ST_GeomFromText('POLYGON((116.003705 36.427753,116.019237 36.427316,116.019484 36.427567,116.020053 36.435415,116.016843 36.435481,116.017037 36.436661,116.015009 36.436687,116.014871 36.436250,116.013019 36.436493,116.012340 36.436556,116.011905 36.436837,116.010436 36.437038,116.010301 36.437004,116.010244 36.436876,116.009754 36.434251,116.006185 36.434331,116.003600 36.435130,116.003177 36.435249,116.002908 36.434715,116.002230 36.429626,116.003705 36.427753))'), 1, NOW(), NOW()),
  ('聊城大学西校区', 2, ST_GeomFromText('POLYGON((115.993431 36.427812,115.996001 36.427746,115.998499 36.435562,115.998481 36.439252,115.998457 36.440690,115.998305 36.440780,115.993585 36.440739,115.993536 36.440675,115.993512 36.438351,115.993431 36.427812))'), 1, NOW(), NOW());

-- 后台初始账号不在这里种：密码哈希应由后端启动时用 BCrypt 生成并打印一次性初始密码，
-- 避免把固定哈希（等于固定密码）写进代码库。见 README「后台账号」一节。
