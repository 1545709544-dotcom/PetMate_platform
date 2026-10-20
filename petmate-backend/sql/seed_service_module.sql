-- 服务预约模块增强：第二家门店 + 多服务项(使「选择门店」有意义)
-- 运行：mysql --default-character-set=utf8mb4 -h localhost -u root -p123456 petmate < sql/seed_service_module.sql
-- 注意：t_appointment.category 列由 schema.sql 创建；此处只补数据 + 回填。

-- 第二家门店(同一商家 merchant_id=20001,便于演示商家端聚合)
INSERT INTO t_store (id, merchant_id, name, type, address, lng, lat, business_hours, photos, audit_status, created_at, updated_at)
VALUES (5002, 20001, 'PetMate 宠物家（徐汇店）', 'comprehensive', '上海市徐汇区漕溪北路 18 号', 121.4365, 31.1864,
 '{"mon":"09:00-21:00","tue":"09:00-21:00","wed":"09:00-21:00","thu":"09:00-21:00","fri":"09:00-21:00","sat":"09:00-22:00","sun":"09:00-22:00"}',
 NULL, 1, NOW(), NOW());

-- 该门店的服务项(覆盖 美容/寄养/遛狗/训练)
INSERT INTO t_service_item (store_id, name, category, price, duration, cover, description, status, created_at) VALUES
 (5002, '宠物造型美容套餐', '美容', 188.00, 90, NULL, '资深美容师一对一造型,含洗澡、修剪、指甲与耳部护理。', 1, NOW()),
 (5002, '温馨家庭寄养', '寄养', 99.00, 1440, NULL, '独立安静空间家庭式寄养,每日视频打卡报平安。', 1, NOW()),
 (5002, '晚间上门遛狗', '遛狗', 50.00, 60, NULL, '晚间 1 小时上门遛狗,熟悉小区路线,安全牵引。', 1, NOW()),
 (5002, '幼猫行为训练课', '训练', 880.00, 90, NULL, '针对幼猫的社交与行为规范训练,温柔正向引导。', 1, NOW());

-- 回填已有预约的分类(幂等)
UPDATE t_appointment a JOIN t_service_item s ON a.service_id = s.id SET a.category = s.category WHERE a.category IS NULL;
