-- PetMate 演示数据
USE petmate;

-- ==================== 用户 ====================
-- 演示账号统一密码 "123456"，下方是该密码在 BCrypt(cost=10) 下当次生成的哈希
-- （每次 BCrypt.gensalt(10) 都不同；同一个明文会生成不同的哈希，但 BCrypt.checkpw 全部能验过）。
-- 登录走真实 BCrypt.checkpw 校验，不再依赖 password IS NULL 的兜底分支。
-- $2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO  -> "123456"
INSERT INTO t_user (id, phone, nickname, avatar, gender, vip_level, status, password) VALUES
  (10001, '13800138000', '萌宠家长', 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate', 1, 1, 0, '$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),
  (10002, '13800138001', '小糯团',    'https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',  1, 0, 0, '$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),
  (10003, '13800138002', '奶昔主人',  'https://api.dicebear.com/7.x/avataaars/svg?seed=moka',   2, 2, 0, '$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'),
  (10004, '13800138003', '豆豆',      'https://api.dicebear.com/7.x/avataaars/svg?seed=pepper', 0, 0, 0, '$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO');

-- ==================== 商家账户（特殊用户，role=merchant） ====================
INSERT INTO t_user (id, phone, nickname, avatar, gender, vip_level, status, password) VALUES
  (20001, '13900000001', '宠物家旗舰店', 'https://api.dicebear.com/7.x/avataaars/svg?seed=store1', 0, 5, 0, '$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO');

-- ==================== 管理员 ====================
INSERT INTO t_user (id, phone, nickname, avatar, gender, vip_level, status, password) VALUES
  (90001, '13700000000', '超管 Admin', 'https://api.dicebear.com/7.x/avataaars/svg?seed=admin', 1, 5, 0, '$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO');

-- ==================== 演示账号密码补齐（幂等） ====================
-- 已跑过老 seed、但 t_user.password 为 NULL 或仍是老错误哈希的库，重跑本文件即可补齐为真实 BCrypt("123456")。
UPDATE t_user SET password = '$2a$10$tZH51NL1gx8xpxU0T3urYOwCUoG6Lf6VTt1Ojh/FUY9mUYHpzysWO'
WHERE id IN (10001,10002,10003,10004,20001,90001)
  AND (password IS NULL
       OR password = ''
       OR password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');

-- ==================== 收货地址 ====================
INSERT INTO t_user_address (user_id, receiver, phone, province, city, district, detail, is_default, tag) VALUES
  (10001, '萌萌', '13800138000', '上海市', '上海市', '浦东新区', '世纪大道 100 号', 1, 'home'),
  (10001, '萌萌', '13800138000', '上海市', '上海市', '徐汇区',   '漕溪北路 88 号',  0, 'company'),
  (10002, '小糯', '13800138001', '北京市', '北京市', '朝阳区',   '建国路 1 号',      1, 'home');

-- ==================== 宠物种类 & 品种 ====================
INSERT INTO t_pet_category (id, name, icon) VALUES
  (1, '犬',  'https://img.icons8.com/color/96/dog.png'),
  (2, '猫',  'https://img.icons8.com/color/96/cat.png'),
  (3, '兔',  'https://img.icons8.com/color/96/rabbit.png'),
  (4, '异宠','https://img.icons8.com/color/96/parrot.png');

INSERT INTO t_pet_breed (category_id, name) VALUES
  (1, '金毛'), (1, '柯基'), (1, '柴犬'), (1, '拉布拉多'), (1, '萨摩耶'),
  (2, '英短'), (2, '美短'), (2, '布偶猫'), (2, '橘猫'),   (2, '暹罗猫'),
  (3, '荷兰垂耳兔'), (3, '安哥拉兔'),
  (4, '虎皮鹦鹉'),  (4, '仓鼠');

-- ==================== 宠物档案 ====================
INSERT INTO t_pet (user_id, name, category_id, breed_id, gender, birthday, weight, neutered, avatar, tags, description) VALUES
  (10001, 'Buddy',  1, 1, 0, '2022-03-15', 25.50, 1, 'https://api.dicebear.com/7.x/dog/svg?seed=buddy',
   JSON_ARRAY('亲人','活泼'), '我的金毛宝宝'),
  (10001, 'Moka',   2, 7, 1, '2023-06-01',  4.20, 0, 'https://api.dicebear.com/7.x/cat/svg?seed=moka',
   JSON_ARRAY('高冷','挑食'), '布偶小公主'),
  (10002, 'Pepper', 2, 6, 0, '2023-09-20',  3.80, 1, 'https://api.dicebear.com/7.x/cat/svg?seed=pepper',
   JSON_ARRAY('黏人'), '英短弟弟');

-- ==================== 健康记录 ====================
INSERT INTO t_pet_health_record (pet_id, record_type, title, content, record_date, next_date, reminder) VALUES
  (1, 'vaccine',   '狂犬疫苗',   '已注射进口狂犬疫苗',  '2025-04-12', '2026-04-12', 1),
  (1, 'deworming','体内驱虫',   '拜耳拜宠清',          '2025-05-10', '2025-08-10', 0),
  (1, 'checkup',   '年度体检',   '血常规、生化全项正常', '2025-06-20', '2026-06-20', 1),
  (2, 'vaccine',   '猫三联',     '首免完成',            '2025-03-15', '2026-03-15', 1),
  (2, 'weight',    '体重记录',   '4.2kg',                '2025-07-01', NULL,         0);

-- ==================== 商品分类（3 级） ====================
INSERT INTO t_product_category (id, parent_id, name, icon, level, sort) VALUES
  (1, 0, '宠物食品',   'https://img.icons8.com/color/96/pet-food.png',     1, 1),
  (2, 0, '宠物用品',   'https://img.icons8.com/color/96/pet-supplies.png', 1, 2),
  (3, 0, '医疗保健',   'https://img.icons8.com/color/96/medicine.png',     1, 3),
  (4, 0, '玩具服饰',   'https://img.icons8.com/color/96/pet-toys.png',     1, 4),
  (11, 1, '猫粮',       NULL, 2, 1),
  (12, 1, '狗粮',       NULL, 2, 2),
  (13, 1, '零食罐头',   NULL, 2, 3),
  (21, 2, '猫砂用品',   NULL, 2, 1),
  (22, 2, '食盆水具',   NULL, 2, 2),
  (23, 2, '窝床家具',   NULL, 2, 3),
  (31, 3, '驱虫保健',   NULL, 2, 1),
  (32, 3, '营养补剂',   NULL, 2, 2),
  (41, 4, '宠物玩具',   NULL, 2, 1),
  (42, 4, '美容洗护',   NULL, 2, 2);

-- ==================== 商品 SPU ====================
INSERT INTO t_product
  (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags)
VALUES
  (1001, '皇家猫粮 K36 成猫专用 4kg', 11, 1, '/src/assets/images/cart/p1-dogfood.jpg',
     JSON_ARRAY('/src/assets/images/cart/p1-dogfood.jpg'), '专为成猫设计,均衡营养,亮泽毛发', 1, 1280, 8400, 4.80, 198.00, 268.00, JSON_ARRAY('猫粮','成猫','进口')),
  (1002, '渴望 Orijen 鸡肉味狗粮 2kg', 12, 2, '/src/assets/images/cart/p2-salmon.jpg',
     JSON_ARRAY('/src/assets/images/cart/p2-salmon.jpg'), '加拿大原装进口,鲜肉配方', 1, 856, 5210, 4.90, 320.00, 399.00, JSON_ARRAY('狗粮','进口','鲜肉')),
  (1003, '小佩 SOLO 智能宠物饮水机', 22, 3, '/src/assets/images/cart/p3-fountain.jpg',
     JSON_ARRAY('/src/assets/images/cart/p3-fountain.jpg'), '活水循环,三重过滤,App 远程控制', 1, 532, 3420, 4.70, 469.00, 599.00, JSON_ARRAY('饮水机','智能')),
  (1004, 'Cat Magic 猫抓板剑麻猫窝', 23, 4, '/src/assets/images/cart/r1-cat-scratcher.jpg',
     JSON_ARRAY('/src/assets/images/cart/r1-cat-scratcher.jpg'), '天然剑麻,耐磨耐抓,猫咪最爱', 1, 412, 2890, 4.60, 89.00, 128.00, JSON_ARRAY('猫窝','剑麻')),
  (1005, 'PetSafe 智能自动喂食器', 22, 3, '/src/assets/images/cart/r2-feeder.jpg',
     JSON_ARRAY('/src/assets/images/cart/r2-feeder.jpg'), '可定时定量,App 远程投喂', 1, 268, 1980, 4.50, 369.00, 459.00, JSON_ARRAY('喂食器','智能')),
  (1006, 'ZEZE 宠物互动玩具组合', 41, 5, '/src/assets/images/cart/r3-toys.jpg',
     JSON_ARRAY('/src/assets/images/cart/r3-toys.jpg'), '逗猫棒+激光+响纸组合,让爱宠活力满满', 1, 723, 4120, 4.70, 59.00, 89.00, JSON_ARRAY('玩具','互动')),
  (1007, 'PETFIT 宠物窝床四季通用', 23, 6, '/src/assets/images/cart/r4-bed.jpg',
     JSON_ARRAY('/src/assets/images/cart/r4-bed.jpg'), '柔软舒适,可拆洗,适合中小型犬猫', 1, 198, 1560, 4.40, 129.00, 169.00, JSON_ARRAY('窝床','四季')),
  (1008, '卫仕 猫用营养膏 120g', 32, 7, '/src/assets/images/consult/prod1-probiotic.jpg',
     JSON_ARRAY('/src/assets/images/consult/prod1-probiotic.jpg'), '补充维生素,亮毛护肤', 1, 1450, 6890, 4.80, 39.90, 59.00, JSON_ARRAY('营养','猫用')),
  (1009, '谷登 化毛膏 营养补充', 32, 7, '/src/assets/images/consult/prod2-hairball.jpg',
     JSON_ARRAY('/src/assets/images/consult/prod2-hairball.jpg'), '温和化毛,改善肠胃', 1, 982, 4520, 4.60, 49.00, 69.00, JSON_ARRAY('化毛','营养')),
  (1010, 'PetMate 户外便携宠物水瓶 550ml', 22, 3, '/src/assets/images/products/p11-shiba-bottle.jpg',
     JSON_ARRAY('/src/assets/images/products/p11-shiba-bottle.jpg'),
     '<section><h2>核心卖点</h2><ul><li>户外便携:一体式收纳,可挂背包侧袋</li><li>550ml 大容量,一按即出水</li><li>食品级 ABS 材质,不含 BPA,安全无异味</li><li>防漏锁扣设计,倒置不洒</li><li>适用中小型犬 / 猫 / 通用宠物</li></ul><h2>产品参数</h2><table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%;max-width:520px;"><tr><th align="left" style="background:#f7f7f7;">参数</th><th align="left" style="background:#f7f7f7;">规格</th></tr><tr><td>容量</td><td>550ml</td></tr><tr><td>材质</td><td>食品级 ABS + 硅胶密封圈</td></tr><tr><td>颜色</td><td>蓝色 / 灰色</td></tr><tr><td>尺寸</td><td>直径 7cm × 高 23cm</td></tr><tr><td>净重</td><td>约 180g</td></tr><tr><td>适用</td><td>中小型犬 / 猫 / 通用宠物</td></tr></table><h2>使用建议</h2><p>徒步、露营、长途遛狗时为宠物随时补水。按压式出水口让陌生环境下的爱宠也能轻松喝到干净的水,告别一次性水瓶的浪费与不便。出行前请装满清水,按压顶部按钮即可出流;锁扣关闭后倒置、摇晃均不渗漏。</p><h2>清洁保养</h2><p>瓶身可拆解清洗,建议每周用温水加少量中性洗涤剂彻底清洁一次,晾干后再组装。硅胶圈可单独取出清洁,避免长期积水产生异味。</p><h2>包装清单</h2><p>水瓶主体 × 1、防漏硅胶圈（备用）× 1、说明书 × 1</p></section>',
     1, 268, 1980, 4.80, 119.00, 159.00, JSON_ARRAY('户外','便携','饮水','PetMate'));

-- ==================== SKU ====================
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES
  (1001, 'SKU-1001-001', JSON_OBJECT('规格','4kg','口味','鸡肉'), 198.00, 320, NULL),
  (1001, 'SKU-1001-002', JSON_OBJECT('规格','8kg','口味','鸡肉'), 358.00, 180, NULL),
  (1002, 'SKU-1002-001', JSON_OBJECT('规格','2kg','口味','鸡肉'), 320.00, 220, NULL),
  (1002, 'SKU-1002-002', JSON_OBJECT('规格','6kg','口味','鸡肉'), 720.00, 120, NULL),
  (1003, 'SKU-1003-001', JSON_OBJECT('颜色','白色'), 469.00, 80,  NULL),
  (1003, 'SKU-1003-002', JSON_OBJECT('颜色','灰色'), 469.00, 60,  NULL),
  (1004, 'SKU-1004-001', JSON_OBJECT('颜色','米白'), 89.00,  200, NULL),
  (1005, 'SKU-1005-001', JSON_OBJECT('容量','3L'), 369.00, 150, NULL),
  (1006, 'SKU-1006-001', JSON_OBJECT('套装','标准'), 59.00, 500, NULL),
  (1007, 'SKU-1007-001', JSON_OBJECT('尺寸','M'),  129.00, 220, NULL),
  (1007, 'SKU-1007-002', JSON_OBJECT('尺寸','L'),  169.00, 160, NULL),
  (1008, 'SKU-1008-001', JSON_OBJECT('口味','鸡肉'), 39.90, 600, NULL),
  (1009, 'SKU-1009-001', JSON_OBJECT('规格','120g'), 49.00, 400, NULL),
  (1010, 'SKU-1010-001', JSON_OBJECT('颜色','蓝色','容量','550ml'), 119.00, 200, NULL),
  (1010, 'SKU-1010-002', JSON_OBJECT('颜色','灰色','容量','550ml'), 119.00, 200, NULL);

-- ==================== 购物车演示数据 ====================
INSERT INTO t_cart (user_id, sku_id, product_id, quantity, selected) VALUES
  (10001, 1, 1001, 1, 1),
  (10001, 3, 1003, 1, 1),
  (10001, 6, 1006, 2, 0);

-- ==================== 收藏 ====================
INSERT INTO t_favorite (user_id, target_type, target_id, title, image, price) VALUES
  (10001, 'product', 1001, '皇家猫粮 K36', '/src/assets/images/cart/p1-dogfood.jpg', 198.00),
  (10001, 'product', 1004, 'Cat Magic 猫抓板', '/src/assets/images/cart/r1-cat-scratcher.jpg', 89.00),
  (10001, 'post',    1, '今天 Buddy 第一次游泳', NULL, NULL);

-- ==================== 优惠券 ====================
INSERT INTO t_coupon (name, type, threshold, amount, discount, valid_start, valid_end) VALUES
  ('新用户满100减10',  'reduction', 100.00, 10.00, NULL, '2025-01-01', '2026-12-31'),
  ('会员9.5折',       'discount',  0.00,   0.00,  0.95, '2025-01-01', '2026-12-31'),
  ('猫粮品类满200减30','category', 200.00, 30.00, NULL, '2025-01-01', '2026-12-31');

-- ==================== 话题 ====================
INSERT INTO t_topic (id, name, icon) VALUES
  (1, '晒萌宠',  'https://img.icons8.com/color/96/pets.png'),
  (2, '日常护理', 'https://img.icons8.com/color/96/dog.png'),
  (3, '美食分享', 'https://img.icons8.com/color/96/pet-food.png'),
  (4, '健康问答', 'https://img.icons8.com/color/96/heart-with-pulse.png');

-- ==================== 帖子 ====================
INSERT INTO t_post
  (id, user_id, user_nickname, user_avatar, content, images, topic_id, topic_name, like_count, comment_count, collect_count, visible, audit_status, created_at)
VALUES
  (1, 10002, '小糯团', 'https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',
     '今天 Buddy 第一次去泳池,兴奋得不行！',
     JSON_ARRAY('https://api.dicebear.com/7.x/dog/svg?seed=buddy'),
     1, '晒萌宠', 38, 12, 8, 'public', 'approved', '2026-07-15 14:30:00'),
  (2, 10003, '奶昔主人', 'https://api.dicebear.com/7.x/avataaars/svg?seed=moka',
     'Moka 挑食怎么办?试了三种猫粮都兴趣不大,求支招 🥺',
     JSON_ARRAY('https://api.dicebear.com/7.x/cat/svg?seed=moka'),
     2, '日常护理', 25, 36, 4, 'public', 'approved', '2026-07-14 09:12:00'),
  (3, 10004, '豆豆', 'https://api.dicebear.com/7.x/avataaars/svg?seed=pepper',
     '新手养猫记第 1 天,奶糕喂多少合适?',
     JSON_ARRAY('https://api.dicebear.com/7.x/cat/svg?seed=pepper'),
     2, '日常护理', 9, 5, 1, 'public', 'approved', '2026-07-16 22:08:00'),
  (4, 10002, '小糯团', 'https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',
     '推荐一款性价比超高的猫抓板,主子爱得不得了 ✨',
     JSON_ARRAY('https://api.dicebear.com/7.x/cat/svg?seed=buddy2'),
     4, '健康问答', 17, 6, 3, 'public', 'approved', '2026-07-13 16:45:00'),
  (5, 10001, '萌宠家长', 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate',
     '刚入手的饮水机,App 控制真的香~',
     JSON_ARRAY('/src/assets/images/cart/p3-fountain.jpg'),
     3, '美食分享', 12, 3, 2, 'public', 'approved', '2026-07-17 08:20:00'),
  (6, 10003, '奶昔主人', 'https://api.dicebear.com/7.x/avataaars/svg?seed=moka',
     '含敏感词测试【垃圾广告】应被审核拦截',
     NULL, 1, '晒萌宠', 0, 0, 0, 'public', 'pending', '2026-07-18 10:00:00');

-- ==================== 帖子评论 ====================
INSERT INTO t_post_comment (post_id, user_id, user_nickname, user_avatar, content, like_count) VALUES
  (1, 10001, '萌宠家长', 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate', '太可爱了！', 5),
  (1, 10004, '豆豆',     'https://api.dicebear.com/7.x/avataaars/svg?seed=pepper', 'Buddy 棒棒的', 2),
  (2, 10001, '萌宠家长', 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate', '可以试试罐头拌粮', 8),
  (2, 10004, '豆豆',     'https://api.dicebear.com/7.x/avataaars/svg?seed=pepper', '换冻干试试', 3),
  (3, 10002, '小糯团',   'https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',  '幼猫 30-50g/天', 4);

-- ==================== 文章分类 ====================
INSERT INTO t_article_category (id, name, icon, sort) VALUES
  (1, '喂养指南', 'https://img.icons8.com/color/96/pet-food.png', 1),
  (2, '健康护理', 'https://img.icons8.com/color/96/heart-with-pulse.png', 2),
  (3, '行为训练', 'https://img.icons8.com/color/96/dog-training.png', 3),
  (4, '品种科普', 'https://img.icons8.com/color/96/pet-supplies.png', 4);

-- ==================== 知识文章 ====================
INSERT INTO t_article (category_id, title, content, cover, author, view_count, seo_url) VALUES
  (1, '幼猫喂养完全指南：从奶糕到成猫的过渡',
     '# 幼猫喂养\n\n幼猫(0-12个月)处于快速生长期,对营养需求高。\n\n## 0-2 月龄\n- 以母乳或专用猫咪代乳粉为主\n- 每天 4-6 次少量喂食\n\n## 2-6 月龄\n- 奶糕为主,每日 3-4 次\n- 用温水或羊奶粉泡软\n\n## 6-12 月龄\n- 逐渐过渡到幼猫粮\n- 每天 2-3 次\n\n> 关键提示:换粮遵循 7 天过渡法,避免肠胃应激。',
     '/src/assets/images/knowledge/c2-cat-food.jpg', '李医生 · 兽医', 1240, 'kitten-feeding-guide'),
  (3, '幼犬基础服从训练 5 步法',
     '# 幼犬基础训练\n\n## 1. 名字识别\n每天 10 次,零食诱导,3 天见效。\n\n## 2. 坐下\n手压臀部+口令\"坐\"。\n\n## 3. 等待\n短时间等待→奖励。\n\n## 4. 召回\n不可惩罚召回,否则它再也不会回来。\n\n## 5. 定点排便\n固定时间+固定地点+奖励。',
     '/src/assets/images/knowledge/c1-dog-train.jpg', '王教练 · 训犬师', 890, 'puppy-obedience'),
  (2, '猫疫苗怎么打？猫三联/妙三多超全攻略',
     '# 猫疫苗\n\n## 核心疫苗\n- 猫三联(妙三多):预防猫瘟/猫鼻支/猫杯状\n- 狂犬疫苗\n\n## 免疫程序\n- 8 周龄:第一针\n- 12 周龄:第二针\n- 16 周龄:第三针 + 狂犬\n- 以后每年加强一针',
     '/src/assets/images/knowledge/c3-vet-kitten.jpg', '李医生 · 兽医', 2150, 'cat-vaccine-guide'),
  (1, '哪些植物对猫有毒？家庭绿植黑名单',
     '# 危险的家居植物\n\n百合、郁金香、水仙、绿萝、滴水观音、龟背竹…… 都会对猫咪造成不同程度的伤害。\n\n## 推荐替代\n- 猫草(小麦苗)\n- 猫薄荷\n- 安全的:波士顿蕨、空气凤梨',
     '/src/assets/images/knowledge/c4-cat-plant.jpg', '陈编辑 · 内容', 1860, 'toxic-plants-for-cat'),
  (2, '狗狗口腔健康：刷牙与洁齿零食',
     '# 狗狗口腔\n\n80% 的 3 岁以上狗狗存在口腔问题。\n\n## 每日刷牙\n- 用宠物专用牙刷\n- 宠物专用牙膏(可食用)\n- 每周 3 次以上\n\n## 替代方案\n- 洁齿零食\n- 咬胶\n- 洁齿玩具',
     '/src/assets/images/knowledge/c5-dog-dental.jpg', '李医生 · 兽医', 920, 'dog-dental-care'),
  (3, '怎么消耗狗狗过剩的精力？',
     '# 消耗精力\n\n## 嗅觉游戏\n把零食藏在家里,让狗找。\n\n## 拔河比赛\n- 限时 3 分钟\n- 让狗赢几次\n\n## 飞盘\n低强度,30 分钟即可。',
     '/src/assets/images/knowledge/c6-dog-play.jpg', '王教练 · 训犬师', 660, 'burn-dog-energy');

-- ==================== 商家 / 门店 ====================
INSERT INTO t_store (id, merchant_id, name, type, address, lng, lat, business_hours, photos, audit_status) VALUES
  (5001, 20001, 'PetMate 宠物家体验店', 'comprehensive', '上海市浦东新区世纪大道 100 号', 121.5045, 31.2389,
     JSON_OBJECT('周一-周五','10:00-21:00','周末','09:00-22:00'),
     JSON_ARRAY('https://api.dicebear.com/7.x/dog/svg?seed=store'),
     1);

-- ==================== 服务项 ====================
INSERT INTO t_service_item (store_id, name, category, price, duration, cover, description) VALUES
  (5001, '精致洗护 SPA',  '美容',  158.00, 60, '/src/assets/images/home/hero-grooming.jpg', '专业宠物美容师,含洗澡+修剪+吹干'),
  (5001, '幼犬基础训练课','训练',  1200.00, 90, '/src/assets/images/knowledge/c1-dog-train.jpg', '5 次课,训犬师一对一教学'),
  (5001, '宠物寄养 24h',  '寄养',  120.00, 1440,'/src/assets/images/service/p1-board.jpg',   '24 小时专人照看,含两餐'),
  (5001, '上门遛狗 1h',   '遛狗',   60.00, 60, '/src/assets/images/service/p2-home.jpg',    '专业遛狗师上门服务');

-- ==================== 评价 ====================
INSERT INTO t_review (store_id, order_id, user_id, user_nickname, user_avatar, rating, content, images, reply, replied_at) VALUES
  (5001, 90001, 10001, '萌宠家长', 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate', 5.0,
   '环境很棒,Buddy 剪完毛精神多了,推荐李老师！', NULL, '谢谢您的支持,期待再次光临~', '2026-07-16 18:30:00'),
  (5001, 90002, 10002, '小糯团',   'https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',  4.5,
   '服务态度好,价格略贵', NULL, NULL, NULL),
  (5001, 90003, 10003, '奶昔主人', 'https://api.dicebear.com/7.x/avataaars/svg?seed=moka',   5.0,
   '寄养很放心,猫咪回来一点都不认生', NULL, '感谢信任~', '2026-07-15 10:20:00');

-- ==================== 订单（已支付演示） ====================
INSERT INTO t_order (id, order_no, user_id, store_id, total_amount, pay_amount, freight_amount, status, receiver_snapshot, paid_at, created_at) VALUES
  (70001, '2026070100010001', 10001, 5001, 198.00, 198.00, 0, 'done',
   JSON_OBJECT('receiver','萌萌','phone','13800138000','address','上海市浦东新区世纪大道 100 号'),
   '2026-07-01 10:30:00', '2026-07-01 10:25:00'),
  (70002, '2026071500010002', 10001, 5001, 158.00, 158.00, 0, 'paid',
   JSON_OBJECT('receiver','萌萌','phone','13800138000','address','上海市浦东新区世纪大道 100 号'),
   '2026-07-15 14:00:00', '2026-07-15 13:55:00'),
  (70003, '2026071600010003', 10001, NULL, 89.00, 89.00, 0, 'pending_pay',
   JSON_OBJECT('receiver','萌萌','phone','13800138000','address','上海市浦东新区世纪大道 100 号'),
   NULL, '2026-07-16 09:10:00');

INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity) VALUES
  (70001, 1001, 1, '皇家猫粮 K36 成猫专用 4kg', '/src/assets/images/cart/p1-dogfood.jpg', 198.00, 1),
  (70003, 1004, 4, 'Cat Magic 猫抓板剑麻猫窝', '/src/assets/images/cart/r1-cat-scratcher.jpg', 89.00, 1);

-- ==================== 问诊会话 ====================
INSERT INTO t_consultation (id, user_id, pet_id, title, status, risk_level, message_count) VALUES
  (1, 10001, 1, 'Buddy 呕吐咨询', 'active', 'normal', 4),
  (2, 10001, 2, 'Moka 拉稀怎么办', 'active', 'urgent', 6);

INSERT INTO t_consultation_message (consultation_id, role, content, risk_level) VALUES
  (1, 'user',      '我家 Buddy 昨晚吐了两次,精神还可以,要紧吗?', NULL),
  (1, 'assistant', '您好,呕吐的原因很多,常见包括:吃得太快、食物变质、误食异物、肠胃炎等。请问狗狗的呕吐物是什么颜色?是否带血?最近有没有换粮?', 'normal'),
  (1, 'user',      '黄色泡沫,没有血。昨天偷吃了一根袜子。', NULL),
  (1, 'assistant', '⚠️ 这种情况需要警惕异物阻塞。建议:1) 立即禁食 8-12 小时;2) 观察是否还有呕吐/精神萎靡;3) 如有异常,立即送医做 X 光。建议同时使用 [卫仕营养膏](javascript:void(0)) 补充体力。', 'urgent'),
  (2, 'user',      'Moka 拉稀 3 天,带血丝', NULL),
  (2, 'assistant', '⚠️ 拉稀带血丝需要立即送医!考虑急性肠胃炎/寄生虫/猫瘟。建议:1) 立即送医做粪检+猫瘟试纸;2) 期间禁食不禁水;3) 记录排便次数和性状。可备 [谷登化毛膏](javascript:void(0)) 备用。', 'urgent');

-- ==================== 敏感词 ====================
INSERT INTO t_sensitive_word (word, category, level, action) VALUES
  ('垃圾广告', '广告', 2, 'block'),
  ('违法', '其他', 3, 'block'),
  ('敏感', '其他', 1, 'review'),
  ('暴力', '暴力', 3, 'block');

-- ==================== 系统配置 ====================
INSERT INTO t_system_config (config_key, config_value) VALUES
  ('site_name',         'PetMate 智能宠物管家'),
  ('default_freight',   '10'),
  ('consult_quota_day', '50');

-- ==================== 商品评价 ====================
INSERT INTO t_product_review (product_id, user_id, user_nickname, user_avatar, rating, content, images, created_at) VALUES
  (1001, 10001, '萌宠家长', 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate', 5,
   '我家猫主子挑食,这款一吃就爱上,毛发都变亮了,回购第三次了!',
   JSON_ARRAY('/src/assets/images/cart/p1-dogfood.jpg'),
   '2026-07-12 10:25:00'),
  (1001, 10002, '小糯团',   'https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',  4,
   '性价比不错,8kg 装更划算,快递略慢。', NULL, '2026-07-10 18:08:00'),
  (1001, 10003, '奶昔主人', 'https://api.dicebear.com/7.x/avataaars/svg?seed=moka',   5,
   '正品,大包装吃得放心,推荐！', NULL, '2026-07-08 09:30:00'),
  (1003, 10001, '萌宠家长', 'https://api.dicebear.com/7.x/avataaars/svg?seed=PetMate', 5,
   'App 控制真香,出差也能远程给猫加水,三重过滤水喝着放心。',
   JSON_ARRAY('/src/assets/images/cart/p3-fountain.jpg'),
   '2026-07-15 11:20:00'),
  (1004, 10002, '小糯团',   'https://api.dicebear.com/7.x/avataaars/svg?seed=buddy',  4,
   '猫主子终于不挠沙发了,剑麻材质很耐磨。', NULL, '2026-07-14 20:45:00'),
  (1010, 10003, '奶昔主人', 'https://api.dicebear.com/7.x/avataaars/svg?seed=moka',   5,
   '出门带水太方便了,一键出水,硅胶圈不漏。',
   JSON_ARRAY('/src/assets/images/products/p11-shiba-bottle.jpg'),
   '2026-07-16 08:10:00');

-- ==================== 关注关系 ====================
INSERT INTO t_follow (follower_id, followee_id, created_at) VALUES
  (10001, 10002, '2026-07-10 09:00:00'),
  (10001, 10003, '2026-07-11 14:30:00'),
  (10002, 10001, '2026-07-12 08:15:00'),
  (10003, 10001, '2026-07-13 20:00:00'),
  (10004, 10002, '2026-07-14 10:20:00');

-- ==================== 举报 ====================
INSERT INTO t_report (reporter_id, target_type, target_id, reason, status, created_at) VALUES
  (10001, 'post',    6, '明显是垃圾广告,内容空泛且刷屏', 'pending', '2026-07-18 11:00:00'),
  (10002, 'comment', 4, '评论含人身攻击,请求管理员处理', 'pending', '2026-07-18 15:30:00'),
  (10004, 'user',    10003, '多次发广告,私信骚扰',         'pending', '2026-07-19 09:45:00');
