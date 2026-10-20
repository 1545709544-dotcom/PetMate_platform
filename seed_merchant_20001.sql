-- ============================================================
-- PetMate 商家中心种子数据
-- 目标商家: 13900000001 (user 20001, 门店 5001 / 5002)
-- 覆盖模块: 商品 / 订单(含订单项, 多状态) / 商品评价 / 服务预约
-- 幂等: 重复执行会先清理本次标记的 seed 数据
-- ============================================================

SET @m  = 20001;   -- 商家 user id
SET @s1 = 5001;    -- 门店1
SET @s2 = 5002;    -- 门店2

-- ---------- 清理历史 seed (幂等) ----------
DELETE FROM t_product_review WHERE product_id IN (SELECT id FROM t_product WHERE merchant_id = @m);
DELETE FROM t_appointment   WHERE store_id IN (@s1, @s2) AND note = 'SEED_APT';
DELETE FROM t_order_item    WHERE order_id IN (SELECT id FROM t_order WHERE order_no LIKE 'PMSEED%');
DELETE FROM t_order         WHERE order_no LIKE 'PMSEED%';
DELETE FROM t_product       WHERE merchant_id = @m;

-- ============================================================
-- 1. 商品 (12 个, merchant_id=20001)
-- ============================================================
INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('皇家成猫粮 2kg 全价均衡主食', 11, @m, 'https://picsum.photos/seed/pmcat1/400/400', '皇家 ROYAL CANIN 成猫粮，精准营养配比，呵护泌尿系统。', 1, 320, 1280, 4.80, 158.00, 198.00, DATE_SUB(NOW(), INTERVAL 58 DAY));
SET @p1 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('Orijen 渴望原生大块肉狗粮 5kg', 12, @m, 'https://picsum.photos/seed/pmdog2/400/400', '渴望无谷狗粮，新鲜肉类占比高，毛发亮泽。', 1, 156, 890, 4.90, 420.00, 498.00, DATE_SUB(NOW(), INTERVAL 52 DAY));
SET @p2 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('ZIWI 巅峰主食罐头 12罐装', 13, @m, 'https://picsum.photos/seed/pmcan3/400/400', '新西兰进口风干主食罐头，无谷无淀粉。', 1, 480, 2100, 4.70, 228.00, 268.00, DATE_SUB(NOW(), INTERVAL 45 DAY));
SET @p3 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('猫太郎豆腐猫砂 6L 可冲厕', 21, @m, 'https://picsum.photos/seed/pmlit4/400/400', '天然豆腐砂，结团强、粉尘少、可冲马桶。', 1, 920, 3400, 4.60, 39.00, 49.00, DATE_SUB(NOW(), INTERVAL 40 DAY));
SET @p4 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('不锈钢双层防打翻食盆', 22, @m, 'https://picsum.photos/seed/pmbowl5/400/400', '加厚不锈钢，底层防滑，易清洗。', 1, 210, 670, 4.50, 59.00, 79.00, DATE_SUB(NOW(), INTERVAL 36 DAY));
SET @p5 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('记忆棉恒温宠物窝 中号', 23, @m, 'https://picsum.photos/seed/pmbed6/400/400', '慢回弹记忆棉，可拆卸水洗外套。', 1, 88, 430, 4.80, 129.00, 159.00, DATE_SUB(NOW(), INTERVAL 32 DAY));
SET @p6 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('博来恩体内外同驱虫滴剂', 31, @m, 'https://picsum.photos/seed/pmdeworm7/400/400', '广谱驱虫，跳蚤蜱虫蛔虫一次性搞定。', 1, 540, 1800, 4.90, 89.00, 109.00, DATE_SUB(NOW(), INTERVAL 28 DAY));
SET @p7 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('麦德氏关节灵软骨素补剂', 32, @m, 'https://picsum.photos/seed/pmjoint8/400/400', '氨糖+软骨素，呵护老年犬猫关节。', 1, 130, 560, 4.70, 168.00, 199.00, DATE_SUB(NOW(), INTERVAL 24 DAY));
SET @p8 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('耐咬橡胶漏食球玩具', 41, @m, 'https://picsum.photos/seed/pmtoy9/400/400', '食品级橡胶，可塞零食，缓解拆家。', 1, 1120, 4500, 4.40, 25.00, 35.00, DATE_SUB(NOW(), INTERVAL 20 DAY));
SET @p9 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('宠物氨基酸沐浴露 500ml', 42, @m, 'https://picsum.photos/seed/pmshampoo10/400/400', '温和配方，洗后蓬松不伤皮肤。', 1, 360, 980, 4.60, 45.00, 59.00, DATE_SUB(NOW(), INTERVAL 16 DAY));
SET @p10 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('冻干生骨肉猫粮 1.5kg', 11, @m, 'https://picsum.photos/seed/pmfreeze11/400/400', '98% 鲜肉冻干，高适口性主食。', 1, 95, 520, 4.90, 268.00, 328.00, DATE_SUB(NOW(), INTERVAL 12 DAY));
SET @p11 = LAST_INSERT_ID();

INSERT INTO t_product (name, category_id, merchant_id, main_image, detail, status, sale_count, view_count, rating, sale_price, original_price, created_at)
VALUES ('智能静音循环饮水机', 22, @m, 'https://picsum.photos/seed/pmwater12/400/400', '4L 大容量，三重过滤，诱导多喝水。', 1, 175, 720, 4.70, 99.00, 129.00, DATE_SUB(NOW(), INTERVAL 8 DAY));
SET @p12 = LAST_INSERT_ID();

-- ============================================================
-- 2. 订单 (18 笔, 多状态, store_id 指向 5001/5002)
--    状态: pending_pay / paid / shipping / done / cancelled / refunding
--    买家: 10001 李丽 / 10002 小糯团 / 10003 奶昔主人 / 10004 豆豆
-- ============================================================

-- @o1 pending_pay (买1, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, receiver_snapshot, expire_at, created_at)
VALUES ('PMSEED0001', 10001, @s1, 166.00, 166.00, 10.00, 'pending_pay', '{"receiver":"李丽","phone":"13811112222","address":"上海市浦东新区世纪大道100号"}', DATE_ADD(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 1 DAY));
SET @o1 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o1, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p1;

-- @o2 pending_pay (买2, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, receiver_snapshot, expire_at, created_at)
VALUES ('PMSEED0002', 10002, @s2, 430.00, 430.00, 10.00, 'pending_pay', '{"receiver":"小糯团","phone":"13722223333","address":"上海市徐汇区漕溪北路18号"}', DATE_ADD(NOW(), INTERVAL 30 MINUTE), DATE_SUB(NOW(), INTERVAL 2 DAY));
SET @o2 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o2, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p2;

-- @o3 paid (买3, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0003', 10003, @s1, 238.00, 238.00, 10.00, 'paid', 'alipay', 'OUT0003', 'TRD0003', '{"receiver":"奶昔主人","phone":"13933334444","address":"北京市朝阳区建国路88号"}', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));
SET @o3 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o3, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p3;

-- @o4 paid (买4, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0004', 10004, @s2, 99.00, 99.00, 10.00, 'paid', 'wechat', 'OUT0004', 'TRD0004', '{"receiver":"豆豆","phone":"13644445555","address":"上海市静安区南京西路1000号"}', DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY));
SET @o4 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o4, id, 0, name, main_image, sale_price, 2, NULL FROM t_product WHERE id = @p4;

-- @o5 paid 2项 (买1, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0005', 10001, @s1, 214.00, 214.00, 10.00, 'paid', 'alipay', 'OUT0005', 'TRD0005', '{"receiver":"李丽","phone":"13811112222","address":"上海市浦东新区世纪大道100号"}', DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 1 DAY));
SET @o5 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o5, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p5;
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o5, id, 0, name, main_image, sale_price, 2, NULL FROM t_product WHERE id = @p9;

-- @o6 paid (买2, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0006', 10002, @s2, 178.00, 178.00, 10.00, 'paid', 'wechat', 'OUT0006', 'TRD0006', '{"receiver":"小糯团","phone":"13722223333","address":"上海市徐汇区漕溪北路18号"}', DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 12 HOUR));
SET @o6 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o6, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p10;

-- @o7 shipping (买3, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0007', 10003, @s1, 89.00, 89.00, 10.00, 'shipping', 'alipay', 'OUT0007', 'TRD0007', '{"receiver":"奶昔主人","phone":"13933334444","address":"北京市朝阳区建国路88号"}', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));
SET @o7 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o7, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p7;

-- @o8 shipping (买4, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0008', 10004, @s2, 268.00, 268.00, 10.00, 'shipping', 'wechat', 'OUT0008', 'TRD0008', '{"receiver":"豆豆","phone":"13644445555","address":"上海市静安区南京西路1000号"}', DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY));
SET @o8 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o8, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p11;

-- @o9 shipping 2项 (买1, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0009', 10001, @s1, 288.00, 288.00, 10.00, 'shipping', 'alipay', 'OUT0009', 'TRD0009', '{"receiver":"李丽","phone":"13811112222","address":"上海市浦东新区世纪大道100号"}', DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY));
SET @o9 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o9, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p6;
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o9, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p12;

-- @o10 shipping (买2, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0010', 10002, @s2, 25.00, 25.00, 10.00, 'shipping', 'wechat', 'OUT0010', 'TRD0010', '{"receiver":"小糯团","phone":"13722223333","address":"上海市徐汇区漕溪北路18号"}', DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));
SET @o10 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o10, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p9;

-- @o11 done (买3, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, delivered_at, finished_at, created_at)
VALUES ('PMSEED0011', 10003, @s1, 158.00, 158.00, 10.00, 'done', 'alipay', 'OUT0011', 'TRD0011', '{"receiver":"奶昔主人","phone":"13933334444","address":"北京市朝阳区建国路88号"}', DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 17 DAY), DATE_SUB(NOW(), INTERVAL 21 DAY));
SET @o11 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o11, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p1;

-- @o12 done (买4, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, delivered_at, finished_at, created_at)
VALUES ('PMSEED0012', 10004, @s2, 420.00, 420.00, 10.00, 'done', 'wechat', 'OUT0012', 'TRD0012', '{"receiver":"豆豆","phone":"13644445555","address":"上海市静安区南京西路1000号"}', DATE_SUB(NOW(), INTERVAL 15 DAY), DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_SUB(NOW(), INTERVAL 12 DAY), DATE_SUB(NOW(), INTERVAL 16 DAY));
SET @o12 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o12, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p2;

-- @o13 done 2项 (买1, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, delivered_at, finished_at, created_at)
VALUES ('PMSEED0013', 10001, @s1, 284.00, 284.00, 10.00, 'done', 'alipay', 'OUT0013', 'TRD0013', '{"receiver":"李丽","phone":"13811112222","address":"上海市浦东新区世纪大道100号"}', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 11 DAY));
SET @o13 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o13, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p8;
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o13, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p7;

-- @o14 done (买2, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, delivered_at, finished_at, created_at)
VALUES ('PMSEED0014', 10002, @s2, 39.00, 39.00, 10.00, 'done', 'wechat', 'OUT0014', 'TRD0014', '{"receiver":"小糯团","phone":"13722223333","address":"上海市徐汇区漕溪北路18号"}', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 10 DAY));
SET @o14 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o14, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p4;

-- @o15 done (买3, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, delivered_at, finished_at, created_at)
VALUES ('PMSEED0015', 10003, @s1, 129.00, 129.00, 10.00, 'done', 'alipay', 'OUT0015', 'TRD0015', '{"receiver":"奶昔主人","phone":"13933334444","address":"北京市朝阳区建国路88号"}', DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY));
SET @o15 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o15, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p6;

-- @o16 cancelled (买4, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, receiver_snapshot, created_at)
VALUES ('PMSEED0016', 10004, @s2, 59.00, 59.00, 10.00, 'cancelled', '{"receiver":"豆豆","phone":"13644445555","address":"上海市静安区南京西路1000号"}', DATE_SUB(NOW(), INTERVAL 3 DAY));
SET @o16 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o16, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p5;

-- @o17 refunding (买1, s1)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, refund_status, refund_amount, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0017', 10001, @s1, 228.00, 228.00, 10.00, 'refunding', 'refunding', 228.00, 'alipay', 'OUT0017', 'TRD0017', '{"receiver":"李丽","phone":"13811112222","address":"上海市浦东新区世纪大道100号"}', DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY));
SET @o17 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o17, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p3;

-- @o18 paid 2项 (买4, s2)
INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, pay_channel, out_trade_no, trade_no, receiver_snapshot, paid_at, created_at)
VALUES ('PMSEED0018', 10004, @s2, 193.00, 193.00, 10.00, 'paid', 'wechat', 'OUT0018', 'TRD0018', '{"receiver":"豆豆","phone":"13644445555","address":"上海市静安区南京西路1000号"}', DATE_SUB(NOW(), INTERVAL 6 HOUR), DATE_SUB(NOW(), INTERVAL 10 HOUR));
SET @o18 = LAST_INSERT_ID();
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o18, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p10;
INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity, snapshot_json)
SELECT @o18, id, 0, name, main_image, sale_price, 1, NULL FROM t_product WHERE id = @p9;

-- ============================================================
-- 3. 商品评价 (12 条, 关联上面商品 + 买家)
-- ============================================================
INSERT INTO t_product_review (product_id, user_id, user_nickname, user_avatar, rating, content, created_at) VALUES
(@p1, 10001, '李丽', NULL, 5, '猫咪很爱吃，便便也比以前成型了，包装也很严实。', DATE_SUB(NOW(), INTERVAL 18 DAY)),
(@p2, 10004, '豆豆', NULL, 5, '狗子毛发明显亮了，便便也正常，值得回购。', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(@p3, 10003, '奶昔主人', NULL, 4, '罐头不错就是有点小贵，猫咪胃口很好。', DATE_SUB(NOW(), INTERVAL 9 DAY)),
(@p4, 10002, '小糯团', NULL, 5, '结团特别快，几乎没粉尘，家里没味道了。', DATE_SUB(NOW(), INTERVAL 22 DAY)),
(@p5, 10001, '李丽', NULL, 4, '质量不错，就是底盘稍微浅了一点。', DATE_SUB(NOW(), INTERVAL 7 DAY)),
(@p6, 10003, '奶昔主人', NULL, 5, '窝很软，猫主子一秒躺平，拆洗也方便。', DATE_SUB(NOW(), INTERVAL 11 DAY)),
(@p7, 10004, '豆豆', NULL, 5, '驱虫很方便，滴完没看到跳蚤了，推荐。', DATE_SUB(NOW(), INTERVAL 4 DAY)),
(@p8, 10001, '李丽', NULL, 4, '老狗吃了关节好像灵活了点，坚持喂。', DATE_SUB(NOW(), INTERVAL 6 DAY)),
(@p9, 10002, '小糯团', NULL, 4, '耐咬，塞了零食能玩半天，性价比高。', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(@p10, 10004, '豆豆', NULL, 5, '洗完毛很蓬松，味道也清新，不刺鼻。', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(@p11, 10003, '奶昔主人', NULL, 5, '冻干肉眼可见，猫超爱，就是有点心疼钱包。', DATE_SUB(NOW(), INTERVAL 5 DAY)),
(@p12, 10002, '小糯团', NULL, 5, '饮水机很安静，猫喝水次数明显多了。', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ============================================================
-- 4. 服务预约 (10 笔, store_id 5001/5002, 关联已有服务项 1-9)
-- ============================================================
INSERT INTO t_appointment (user_id, service_id, service_name, store_id, store_name, category, appointment_time, note, status, create_time)
VALUES
(10001, 1, '精致洗护 SPA', @s1, 'PetMate 宠物家体验店', '美容', DATE_ADD(NOW(), INTERVAL 2 DAY), 'SEED_APT', 'confirmed', DATE_SUB(NOW(), INTERVAL 3 DAY)),
(10002, 5, '宠物造型美容套餐', @s2, 'PetMate 宠物家（徐汇店）', '美容', DATE_ADD(NOW(), INTERVAL 4 DAY), 'SEED_APT', 'confirmed', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(10003, 3, '宠物寄养 24h', @s1, 'PetMate 宠物家体验店', '寄养', DATE_ADD(NOW(), INTERVAL 6 DAY), 'SEED_APT', 'confirmed', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(10004, 4, '上门遛狗 1h', @s1, 'PetMate 宠物家体验店', '遛狗', DATE_ADD(NOW(), INTERVAL 1 DAY), 'SEED_APT', 'confirmed', DATE_SUB(NOW(), INTERVAL 2 DAY)),
(10001, 7, '温馨家庭寄养', @s2, 'PetMate 宠物家（徐汇店）', '寄养', DATE_SUB(NOW(), INTERVAL 5 DAY), 'SEED_APT', 'done', DATE_SUB(NOW(), INTERVAL 8 DAY)),
(10002, 2, '幼犬基础训练课', @s1, 'PetMate 宠物家体验店', '训练', DATE_SUB(NOW(), INTERVAL 10 DAY), 'SEED_APT', 'done', DATE_SUB(NOW(), INTERVAL 13 DAY)),
(10003, 6, '晚间上门遛狗', @s2, 'PetMate 宠物家（徐汇店）', '遛狗', DATE_ADD(NOW(), INTERVAL 3 DAY), 'SEED_APT', 'confirmed', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(10004, 8, '幼猫行为训练课', @s2, 'PetMate 宠物家（徐汇店）', '训练', DATE_ADD(NOW(), INTERVAL 7 DAY), 'SEED_APT', 'confirmed', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(10001, 4, '上门遛狗 1h', @s1, 'PetMate 宠物家体验店', '遛狗', DATE_SUB(NOW(), INTERVAL 12 DAY), 'SEED_APT', 'done', DATE_SUB(NOW(), INTERVAL 15 DAY)),
(10003, 5, '宠物造型美容套餐', @s2, 'PetMate 宠物家（徐汇店）', '美容', DATE_ADD(NOW(), INTERVAL 5 DAY), 'SEED_APT', 'confirmed', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ============================================================
-- 确认行数
-- ============================================================
SELECT '商品' module, COUNT(*) cnt FROM t_product WHERE merchant_id = @m
UNION ALL SELECT '订单', COUNT(*) FROM t_order WHERE order_no LIKE 'PMSEED%'
UNION ALL SELECT '订单项', COUNT(*) FROM t_order_item WHERE order_id IN (SELECT id FROM t_order WHERE order_no LIKE 'PMSEED%')
UNION ALL SELECT '评价', COUNT(*) FROM t_product_review WHERE product_id IN (SELECT id FROM t_product WHERE merchant_id = @m)
UNION ALL SELECT '预约', COUNT(*) FROM t_appointment WHERE note = 'SEED_APT';
