-- ============================================================
-- 额外 7 个示例商品（演示下单闭环用）
-- 覆盖 7 个不同二级分类，复用前端本地图片，插入 petmate 库。
-- 执行：mysql -h localhost -u root -p123456 petmate < seed_products_extra.sql
-- ============================================================
USE petmate;

INSERT INTO t_product
  (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags)
VALUES
  -- 11 猫粮
  (2001, '网易严选 冻干双拼猫粮 2kg', 11, 1, '/src/assets/images/category/p1-catfood.jpg',
     JSON_ARRAY('/src/assets/images/category/p1-catfood.jpg'), '双重冻干工艺，82% 动物蛋白，呵护猫咪肠胃，亮泽毛发', 1, 320, 2100, 4.70, 158.00, 198.00, JSON_ARRAY('猫粮','冻干','高蛋白')),
  -- 12 狗粮
  (2002, '伯纳天纯 鸭肉梨狗粮 5kg', 12, 2, '/src/assets/images/products/p3-dog-food.jpg',
     JSON_ARRAY('/src/assets/images/products/p3-dog-food.jpg'), '低敏鸭肉配方，添加雪梨降火，美毛护肤易消化', 1, 280, 1800, 4.80, 268.00, 328.00, JSON_ARRAY('狗粮','低敏','鸭肉')),
  -- 13 零食罐头
  (2003, '巅峰 Ziwi 牛肉主食罐头 185g', 13, 3, '/src/assets/images/products/p6-can-food.jpg',
     JSON_ARRAY('/src/assets/images/products/p6-can-food.jpg'), '新西兰进口，93% 鲜肉含量，无谷主食罐，适口性极佳', 1, 960, 5400, 4.90, 39.90, 49.00, JSON_ARRAY('罐头','主食罐','牛肉')),
  -- 22 食盆水具
  (2004, '陶瓷慢食防噎碗', 22, 4, '/src/assets/images/products/p1-cat-bowl.jpg',
     JSON_ARRAY('/src/assets/images/products/p1-cat-bowl.jpg'), '斜坡慢食设计，防呛防噎，陶瓷材质易清洁无异味', 1, 410, 2600, 4.60, 39.00, 59.00, JSON_ARRAY('食盆','慢食','防噎')),
  -- 32 营养补剂
  (2005, '麦德氏 关节软骨素片', 32, 5, '/src/assets/images/products/p7-cat-treat.jpg',
     JSON_ARRAY('/src/assets/images/products/p7-cat-treat.jpg'), '硫酸软骨素 + 氨糖，呵护关节，老年犬猫适用', 1, 520, 3100, 4.70, 79.00, 99.00, JSON_ARRAY('营养','关节','软骨素')),
  -- 41 宠物玩具
  (2006, '猫薄荷小鱼逗猫玩具', 41, 6, '/src/assets/images/cart/r3-toys.jpg',
     JSON_ARRAY('/src/assets/images/cart/r3-toys.jpg'), '天然猫薄荷，耐咬耐抓，释放猫咪天性，互动解闷', 1, 1300, 6800, 4.80, 19.90, 29.90, JSON_ARRAY('玩具','猫薄荷','互动')),
  -- 23 窝床家具
  (2007, '四季保暖猫窝 蓬软款', 23, 7, '/src/assets/images/home/p3-bed.jpg',
     JSON_ARRAY('/src/assets/images/home/p3-bed.jpg'), '记忆棉底垫，可拆洗，四季恒温，给主子安稳睡眠', 1, 360, 2400, 4.50, 99.00, 139.00, JSON_ARRAY('窝床','保暖','可拆洗'));

-- ==================== SKU ====================
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES
  (2001, 'SKU-2001-001', JSON_OBJECT('规格','2kg','口味','鸡肉'), 158.00, 200, NULL),
  (2001, 'SKU-2001-002', JSON_OBJECT('规格','5kg','口味','鸡肉'), 358.00, 120, NULL),
  (2002, 'SKU-2002-001', JSON_OBJECT('规格','5kg','口味','鸭肉梨'), 268.00, 150, NULL),
  (2002, 'SKU-2002-002', JSON_OBJECT('规格','10kg','口味','鸭肉梨'), 498.00, 80, NULL),
  (2003, 'SKU-2003-001', JSON_OBJECT('规格','185g','口味','牛肉'), 39.90, 500, NULL),
  (2004, 'SKU-2004-001', JSON_OBJECT('颜色','粉色'), 39.00, 400, NULL),
  (2004, 'SKU-2004-002', JSON_OBJECT('颜色','蓝色'), 39.00, 400, NULL),
  (2005, 'SKU-2005-001', JSON_OBJECT('规格','60片'), 79.00, 300, NULL),
  (2005, 'SKU-2005-002', JSON_OBJECT('规格','120片'), 139.00, 200, NULL),
  (2006, 'SKU-2006-001', JSON_OBJECT('套装','单只'), 19.90, 600, NULL),
  (2006, 'SKU-2006-002', JSON_OBJECT('套装','3只装'), 49.90, 400, NULL),
  (2007, 'SKU-2007-001', JSON_OBJECT('尺寸','M'), 99.00, 200, NULL),
  (2007, 'SKU-2007-002', JSON_OBJECT('尺寸','L'), 139.00, 150, NULL);
