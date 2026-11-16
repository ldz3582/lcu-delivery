-- ============================================================
-- 真实扣款测试用的「1 分钱」数据
--
-- 用途：把一家店和它的一个商品临时改成 1 分钱，方便真机跑真实微信支付时
--       只花 0.01 元验证全链路（微信支付最小金额为 1 分）。
--
-- 用法：
--   mysql -ulcu -plcu123456 -P3307 lcu_delivery < ops/sql/test-01fen.sql
--
-- 测完必须还原（见文件末尾 RECOVER 段），否则演示数据被改坏。
-- ============================================================
SET NAMES utf8mb4;

-- 记录原值，便于还原核对
SELECT id, name, price, stock FROM product WHERE id = 4;
SELECT id, name, min_amount, pack_fee, delivery_fee FROM shop WHERE id = 1;

-- 1) 店铺：起送价降到 0.01 元，打包费/配送费归零，避免总价被抬高
UPDATE shop
SET min_amount = 1, pack_fee = 0, delivery_fee = 0, updated_at = NOW()
WHERE id = 1;

-- 2) 商品：冰红茶（id=4）改成 1 分钱，库存给足
UPDATE product
SET price = 1, origin_price = NULL, stock = 9999, has_sku = 0, status = 1,
    special_price = NULL, special_start_at = NULL, special_end_at = NULL, updated_at = NOW()
WHERE id = 4;

-- 3) 确认：下面应输出 total = 1
SELECT '首单金额应为 1 分（含打包 0 + 配送 0）' AS hint;

-- ============================================================
-- 恢复演示数据（测完执行）
-- ============================================================
-- UPDATE shop SET min_amount = 1000, pack_fee = 100, delivery_fee = 200, updated_at = NOW() WHERE id = 1;
-- UPDATE product SET price = 300, origin_price = 400, stock = -1, updated_at = NOW() WHERE id = 4;
