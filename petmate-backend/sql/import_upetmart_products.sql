-- 替换商品库：清空旧的购物车/订单/商品，插入 20 个 upetmart 商品
DELETE FROM t_cart;
DELETE FROM t_order_item;
DELETE FROM t_order;
DELETE FROM t_product_sku;
DELETE FROM t_product;

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3001, '皇家 成犬低脂易消化处方粮 LF22 1.5kg', 12, NULL, '/static/uploads/products/3001/main.jpg', '["/static/uploads/products/3001/main.jpg", "/static/uploads/products/3001/d1.jpg", "/static/uploads/products/3001/d2.jpg", "/static/uploads/products/3001/d3.jpg", "/static/uploads/products/3001/d4.jpg"]', '<h2>皇家 成犬低脂易消化处方粮 LF22 1.5kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为狗狗打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3001/main.jpg" alt="皇家 成犬低脂易消化处方粮 LF22 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3001/d1.jpg" alt="皇家 成犬低脂易消化处方粮 LF22 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3001/d2.jpg" alt="皇家 成犬低脂易消化处方粮 LF22 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3001/d3.jpg" alt="皇家 成犬低脂易消化处方粮 LF22 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3001/d4.jpg" alt="皇家 成犬低脂易消化处方粮 LF22 1.5kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 237, 0, 4.70, 166.00, 190.90, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3001, 'SKU03001', '{"规格": "标准装"}', 166.00, 99, '/static/uploads/products/3001/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3002, '皇家 成犬肠道全价处方粮 GI25 2kg', 12, NULL, '/static/uploads/products/3002/main.jpg', '["/static/uploads/products/3002/main.jpg", "/static/uploads/products/3002/d1.jpg", "/static/uploads/products/3002/d2.jpg", "/static/uploads/products/3002/d3.jpg", "/static/uploads/products/3002/d4.jpg"]', '<h2>皇家 成犬肠道全价处方粮 GI25 2kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为狗狗打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3002/main.jpg" alt="皇家 成犬肠道全价处方粮 GI25 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3002/d1.jpg" alt="皇家 成犬肠道全价处方粮 GI25 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3002/d2.jpg" alt="皇家 成犬肠道全价处方粮 GI25 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3002/d3.jpg" alt="皇家 成犬肠道全价处方粮 GI25 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3002/d4.jpg" alt="皇家 成犬肠道全价处方粮 GI25 2kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 260, 0, 4.80, 213.54, 245.57, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3002, 'SKU03002', '{"规格": "标准装"}', 213.54, 99, '/static/uploads/products/3002/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3003, '皇家 成犬低脂易消化全价处方粮 LF22 6kg', 12, NULL, '/static/uploads/products/3003/main.jpg', '["/static/uploads/products/3003/main.jpg", "/static/uploads/products/3003/d1.jpg", "/static/uploads/products/3003/d2.jpg", "/static/uploads/products/3003/d3.jpg", "/static/uploads/products/3003/d4.jpg"]', '<h2>皇家 成犬低脂易消化全价处方粮 LF22 6kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为狗狗打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3003/main.jpg" alt="皇家 成犬低脂易消化全价处方粮 LF22 6kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3003/d1.jpg" alt="皇家 成犬低脂易消化全价处方粮 LF22 6kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3003/d2.jpg" alt="皇家 成犬低脂易消化全价处方粮 LF22 6kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3003/d3.jpg" alt="皇家 成犬低脂易消化全价处方粮 LF22 6kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3003/d4.jpg" alt="皇家 成犬低脂易消化全价处方粮 LF22 6kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 283, 0, 4.90, 615.87, 708.25, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3003, 'SKU03003', '{"规格": "标准装"}', 615.87, 99, '/static/uploads/products/3003/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3004, '皇家 成犬低过敏性全价处方粮 DR21 2kg', 12, NULL, '/static/uploads/products/3004/main.jpg', '["/static/uploads/products/3004/main.jpg", "/static/uploads/products/3004/d1.jpg", "/static/uploads/products/3004/d2.jpg", "/static/uploads/products/3004/d3.jpg", "/static/uploads/products/3004/d4.jpg"]', '<h2>皇家 成犬低过敏性全价处方粮 DR21 2kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为狗狗打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3004/main.jpg" alt="皇家 成犬低过敏性全价处方粮 DR21 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3004/d1.jpg" alt="皇家 成犬低过敏性全价处方粮 DR21 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3004/d2.jpg" alt="皇家 成犬低过敏性全价处方粮 DR21 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3004/d3.jpg" alt="皇家 成犬低过敏性全价处方粮 DR21 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3004/d4.jpg" alt="皇家 成犬低过敏性全价处方粮 DR21 2kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 306, 0, 4.60, 243.92, 280.51, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3004, 'SKU03004', '{"规格": "标准装"}', 243.92, 99, '/static/uploads/products/3004/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3005, '皇家 成犬泌尿道全价处方粮 LP18 2kg', 12, NULL, '/static/uploads/products/3005/main.png', '["/static/uploads/products/3005/main.png", "/static/uploads/products/3005/d1.png", "/static/uploads/products/3005/d2.png", "/static/uploads/products/3005/d3.png", "/static/uploads/products/3005/d4.jpg"]', '<h2>皇家 成犬泌尿道全价处方粮 LP18 2kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为狗狗打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3005/main.png" alt="皇家 成犬泌尿道全价处方粮 LP18 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3005/d1.png" alt="皇家 成犬泌尿道全价处方粮 LP18 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3005/d2.png" alt="皇家 成犬泌尿道全价处方粮 LP18 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3005/d3.png" alt="皇家 成犬泌尿道全价处方粮 LP18 2kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3005/d4.jpg" alt="皇家 成犬泌尿道全价处方粮 LP18 2kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 329, 0, 4.70, 220.24, 253.28, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3005, 'SKU03005', '{"规格": "标准装"}', 220.24, 99, '/static/uploads/products/3005/main.png');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3006, '皇家 小型犬幼犬奶糕粮 MIS30 1kg', 12, NULL, '/static/uploads/products/3006/main.jpg', '["/static/uploads/products/3006/main.jpg", "/static/uploads/products/3006/d1.jpg", "/static/uploads/products/3006/d2.jpg", "/static/uploads/products/3006/d3.jpg", "/static/uploads/products/3006/d4.jpg"]', '<h2>皇家 小型犬幼犬奶糕粮 MIS30 1kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为狗狗打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3006/main.jpg" alt="皇家 小型犬幼犬奶糕粮 MIS30 1kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3006/d1.jpg" alt="皇家 小型犬幼犬奶糕粮 MIS30 1kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3006/d2.jpg" alt="皇家 小型犬幼犬奶糕粮 MIS30 1kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3006/d3.jpg" alt="皇家 小型犬幼犬奶糕粮 MIS30 1kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3006/d4.jpg" alt="皇家 小型犬幼犬奶糕粮 MIS30 1kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 352, 0, 4.80, 78.00, 89.70, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3006, 'SKU03006', '{"规格": "标准装"}', 78.00, 99, '/static/uploads/products/3006/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3007, '皇家 成猫泌尿道全价处方粮 LP34 1.5kg', 11, NULL, '/static/uploads/products/3007/main.png', '["/static/uploads/products/3007/main.png", "/static/uploads/products/3007/d1.png", "/static/uploads/products/3007/d2.png", "/static/uploads/products/3007/d3.png", "/static/uploads/products/3007/d4.jpg"]', '<h2>皇家 成猫泌尿道全价处方粮 LP34 1.5kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫咪打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3007/main.png" alt="皇家 成猫泌尿道全价处方粮 LP34 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3007/d1.png" alt="皇家 成猫泌尿道全价处方粮 LP34 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3007/d2.png" alt="皇家 成猫泌尿道全价处方粮 LP34 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3007/d3.png" alt="皇家 成猫泌尿道全价处方粮 LP34 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3007/d4.jpg" alt="皇家 成猫泌尿道全价处方粮 LP34 1.5kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 375, 0, 4.90, 206.00, 236.90, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3007, 'SKU03007', '{"规格": "标准装"}', 206.00, 99, '/static/uploads/products/3007/main.png');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3008, '皇家 成猫低过敏性全价处方粮 DR25 1.5kg', 11, NULL, '/static/uploads/products/3008/main.jpg', '["/static/uploads/products/3008/main.jpg", "/static/uploads/products/3008/d1.jpg", "/static/uploads/products/3008/d2.jpg", "/static/uploads/products/3008/d3.jpg", "/static/uploads/products/3008/d4.jpg"]', '<h2>皇家 成猫低过敏性全价处方粮 DR25 1.5kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫咪打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3008/main.jpg" alt="皇家 成猫低过敏性全价处方粮 DR25 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3008/d1.jpg" alt="皇家 成猫低过敏性全价处方粮 DR25 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3008/d2.jpg" alt="皇家 成猫低过敏性全价处方粮 DR25 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3008/d3.jpg" alt="皇家 成猫低过敏性全价处方粮 DR25 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3008/d4.jpg" alt="皇家 成猫低过敏性全价处方粮 DR25 1.5kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 398, 0, 4.60, 221.14, 254.31, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3008, 'SKU03008', '{"规格": "标准装"}', 221.14, 99, '/static/uploads/products/3008/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3009, '皇家 成猫泌尿道全价处方粮 LP34 3.5kg', 11, NULL, '/static/uploads/products/3009/main.png', '["/static/uploads/products/3009/main.png", "/static/uploads/products/3009/d1.png", "/static/uploads/products/3009/d2.png", "/static/uploads/products/3009/d3.png", "/static/uploads/products/3009/d4.jpg"]', '<h2>皇家 成猫泌尿道全价处方粮 LP34 3.5kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫咪打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3009/main.png" alt="皇家 成猫泌尿道全价处方粮 LP34 3.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3009/d1.png" alt="皇家 成猫泌尿道全价处方粮 LP34 3.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3009/d2.png" alt="皇家 成猫泌尿道全价处方粮 LP34 3.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3009/d3.png" alt="皇家 成猫泌尿道全价处方粮 LP34 3.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3009/d4.jpg" alt="皇家 成猫泌尿道全价处方粮 LP34 3.5kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 30, 0, 4.70, 426.70, 490.70, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3009, 'SKU03009', '{"规格": "标准装"}', 426.70, 99, '/static/uploads/products/3009/main.png');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3010, '皇家 幼猫肠道全价处方粮 GIK35 1kg', 11, NULL, '/static/uploads/products/3010/main.png', '["/static/uploads/products/3010/main.png", "/static/uploads/products/3010/d1.png", "/static/uploads/products/3010/d2.jpg", "/static/uploads/products/3010/d3.png", "/static/uploads/products/3010/d4.jpg"]', '<h2>皇家 幼猫肠道全价处方粮 GIK35 1kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫咪打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3010/main.png" alt="皇家 幼猫肠道全价处方粮 GIK35 1kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3010/d1.png" alt="皇家 幼猫肠道全价处方粮 GIK35 1kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3010/d2.jpg" alt="皇家 幼猫肠道全价处方粮 GIK35 1kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3010/d3.png" alt="皇家 幼猫肠道全价处方粮 GIK35 1kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3010/d4.jpg" alt="皇家 幼猫肠道全价处方粮 GIK35 1kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 53, 0, 4.80, 155.00, 178.25, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3010, 'SKU03010', '{"规格": "标准装"}', 155.00, 99, '/static/uploads/products/3010/main.png');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3011, '皇家 成猫减肥全价处方粮 SAT34 1.5kg', 11, NULL, '/static/uploads/products/3011/main.png', '["/static/uploads/products/3011/main.png", "/static/uploads/products/3011/d1.png", "/static/uploads/products/3011/d2.jpg", "/static/uploads/products/3011/d3.png", "/static/uploads/products/3011/d4.jpg"]', '<h2>皇家 成猫减肥全价处方粮 SAT34 1.5kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫咪打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3011/main.png" alt="皇家 成猫减肥全价处方粮 SAT34 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3011/d1.png" alt="皇家 成猫减肥全价处方粮 SAT34 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3011/d2.jpg" alt="皇家 成猫减肥全价处方粮 SAT34 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3011/d3.png" alt="皇家 成猫减肥全价处方粮 SAT34 1.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3011/d4.jpg" alt="皇家 成猫减肥全价处方粮 SAT34 1.5kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 76, 0, 4.90, 193.86, 222.94, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3011, 'SKU03011', '{"规格": "标准装"}', 193.86, 99, '/static/uploads/products/3011/main.png');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3012, '皇家 成猫肠道处方粮 GI32 3.5kg', 11, NULL, '/static/uploads/products/3012/main.png', '["/static/uploads/products/3012/main.png", "/static/uploads/products/3012/d1.png", "/static/uploads/products/3012/d2.png", "/static/uploads/products/3012/d3.png", "/static/uploads/products/3012/d4.jpg"]', '<h2>皇家 成猫肠道处方粮 GI32 3.5kg · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫咪打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3012/main.png" alt="皇家 成猫肠道处方粮 GI32 3.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3012/d1.png" alt="皇家 成猫肠道处方粮 GI32 3.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3012/d2.png" alt="皇家 成猫肠道处方粮 GI32 3.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3012/d3.png" alt="皇家 成猫肠道处方粮 GI32 3.5kg"></div>
<div class="detail-image"><img src="/static/uploads/products/3012/d4.jpg" alt="皇家 成猫肠道处方粮 GI32 3.5kg"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 99, 0, 4.60, 394.72, 453.93, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3012, 'SKU03012', '{"规格": "标准装"}', 394.72, 99, '/static/uploads/products/3012/main.png');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3013, '礼蓝拜耳 爱沃克 4-8kg猫用 体内外驱虫滴剂 2盒', 31, NULL, '/static/uploads/products/3013/main.jpg', '["/static/uploads/products/3013/main.jpg", "/static/uploads/products/3013/d1.jpg", "/static/uploads/products/3013/d2.jpg", "/static/uploads/products/3013/d3.jpg", "/static/uploads/products/3013/d4.jpg"]', '<h2>礼蓝拜耳 爱沃克 4-8kg猫用 体内外驱虫滴剂 2盒 · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫狗驱虫打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3013/main.jpg" alt="礼蓝拜耳 爱沃克 4-8kg猫用 体内外驱虫滴剂 2盒"></div>
<div class="detail-image"><img src="/static/uploads/products/3013/d1.jpg" alt="礼蓝拜耳 爱沃克 4-8kg猫用 体内外驱虫滴剂 2盒"></div>
<div class="detail-image"><img src="/static/uploads/products/3013/d2.jpg" alt="礼蓝拜耳 爱沃克 4-8kg猫用 体内外驱虫滴剂 2盒"></div>
<div class="detail-image"><img src="/static/uploads/products/3013/d3.jpg" alt="礼蓝拜耳 爱沃克 4-8kg猫用 体内外驱虫滴剂 2盒"></div>
<div class="detail-image"><img src="/static/uploads/products/3013/d4.jpg" alt="礼蓝拜耳 爱沃克 4-8kg猫用 体内外驱虫滴剂 2盒"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 122, 0, 4.70, 259.00, 297.85, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3013, 'SKU03013', '{"规格": "标准装"}', 259.00, 99, '/static/uploads/products/3013/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3014, '博来恩 2.5-7.5kg猫用 体内外驱虫滴剂 3支装', 31, NULL, '/static/uploads/products/3014/main.jpg', '["/static/uploads/products/3014/main.jpg", "/static/uploads/products/3014/d1.jpg", "/static/uploads/products/3014/d2.jpg", "/static/uploads/products/3014/d3.jpg", "/static/uploads/products/3014/d4.jpg"]', '<h2>博来恩 2.5-7.5kg猫用 体内外驱虫滴剂 3支装 · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫狗驱虫打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3014/main.jpg" alt="博来恩 2.5-7.5kg猫用 体内外驱虫滴剂 3支装"></div>
<div class="detail-image"><img src="/static/uploads/products/3014/d1.jpg" alt="博来恩 2.5-7.5kg猫用 体内外驱虫滴剂 3支装"></div>
<div class="detail-image"><img src="/static/uploads/products/3014/d2.jpg" alt="博来恩 2.5-7.5kg猫用 体内外驱虫滴剂 3支装"></div>
<div class="detail-image"><img src="/static/uploads/products/3014/d3.jpg" alt="博来恩 2.5-7.5kg猫用 体内外驱虫滴剂 3支装"></div>
<div class="detail-image"><img src="/static/uploads/products/3014/d4.jpg" alt="博来恩 2.5-7.5kg猫用 体内外驱虫滴剂 3支装"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 145, 0, 4.80, 248.00, 285.20, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3014, 'SKU03014', '{"规格": "标准装"}', 248.00, 99, '/static/uploads/products/3014/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3015, '大宠爱 2.6-7.5kg猫用 体内外驱虫滴剂 3支', 31, NULL, '/static/uploads/products/3015/main.jpg', '["/static/uploads/products/3015/main.jpg", "/static/uploads/products/3015/d1.jpg", "/static/uploads/products/3015/d2.jpg", "/static/uploads/products/3015/d3.jpg", "/static/uploads/products/3015/d4.jpg"]', '<h2>大宠爱 2.6-7.5kg猫用 体内外驱虫滴剂 3支 · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫狗驱虫打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3015/main.jpg" alt="大宠爱 2.6-7.5kg猫用 体内外驱虫滴剂 3支"></div>
<div class="detail-image"><img src="/static/uploads/products/3015/d1.jpg" alt="大宠爱 2.6-7.5kg猫用 体内外驱虫滴剂 3支"></div>
<div class="detail-image"><img src="/static/uploads/products/3015/d2.jpg" alt="大宠爱 2.6-7.5kg猫用 体内外驱虫滴剂 3支"></div>
<div class="detail-image"><img src="/static/uploads/products/3015/d3.jpg" alt="大宠爱 2.6-7.5kg猫用 体内外驱虫滴剂 3支"></div>
<div class="detail-image"><img src="/static/uploads/products/3015/d4.jpg" alt="大宠爱 2.6-7.5kg猫用 体内外驱虫滴剂 3支"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 168, 0, 4.90, 210.00, 241.50, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3015, 'SKU03015', '{"规格": "标准装"}', 210.00, 99, '/static/uploads/products/3015/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3016, '礼蓝拜耳 爱沃克 4kg以下猫用 体内外驱虫滴剂 3支', 31, NULL, '/static/uploads/products/3016/main.jpg', '["/static/uploads/products/3016/main.jpg", "/static/uploads/products/3016/d1.jpg", "/static/uploads/products/3016/d2.jpg", "/static/uploads/products/3016/d3.jpg", "/static/uploads/products/3016/d4.jpg"]', '<h2>礼蓝拜耳 爱沃克 4kg以下猫用 体内外驱虫滴剂 3支 · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫狗驱虫打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3016/main.jpg" alt="礼蓝拜耳 爱沃克 4kg以下猫用 体内外驱虫滴剂 3支"></div>
<div class="detail-image"><img src="/static/uploads/products/3016/d1.jpg" alt="礼蓝拜耳 爱沃克 4kg以下猫用 体内外驱虫滴剂 3支"></div>
<div class="detail-image"><img src="/static/uploads/products/3016/d2.jpg" alt="礼蓝拜耳 爱沃克 4kg以下猫用 体内外驱虫滴剂 3支"></div>
<div class="detail-image"><img src="/static/uploads/products/3016/d3.jpg" alt="礼蓝拜耳 爱沃克 4kg以下猫用 体内外驱虫滴剂 3支"></div>
<div class="detail-image"><img src="/static/uploads/products/3016/d4.jpg" alt="礼蓝拜耳 爱沃克 4kg以下猫用 体内外驱虫滴剂 3支"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 191, 0, 4.60, 138.00, 158.70, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3016, 'SKU03016', '{"规格": "标准装"}', 138.00, 99, '/static/uploads/products/3016/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3017, '法国维克 复合酶牙膏 犬猫通用 70g', 42, NULL, '/static/uploads/products/3017/main.jpg', '["/static/uploads/products/3017/main.jpg", "/static/uploads/products/3017/d1.png", "/static/uploads/products/3017/d2.jpg", "/static/uploads/products/3017/d3.jpg", "/static/uploads/products/3017/d4.jpg"]', '<h2>法国维克 复合酶牙膏 犬猫通用 70g · 商品详情</h2>
<p>本商品由 PetMate 严选，专为犬猫洗护打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3017/main.jpg" alt="法国维克 复合酶牙膏 犬猫通用 70g"></div>
<div class="detail-image"><img src="/static/uploads/products/3017/d1.png" alt="法国维克 复合酶牙膏 犬猫通用 70g"></div>
<div class="detail-image"><img src="/static/uploads/products/3017/d2.jpg" alt="法国维克 复合酶牙膏 犬猫通用 70g"></div>
<div class="detail-image"><img src="/static/uploads/products/3017/d3.jpg" alt="法国维克 复合酶牙膏 犬猫通用 70g"></div>
<div class="detail-image"><img src="/static/uploads/products/3017/d4.jpg" alt="法国维克 复合酶牙膏 犬猫通用 70g"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 214, 0, 4.70, 78.00, 89.70, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3017, 'SKU03017', '{"规格": "标准装"}', 78.00, 99, '/static/uploads/products/3017/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3018, '爱哒哒 宠物训导尿垫 L码（40片）', 21, NULL, '/static/uploads/products/3018/main.jpg', '["/static/uploads/products/3018/main.jpg", "/static/uploads/products/3018/d1.jpg", "/static/uploads/products/3018/d2.png", "/static/uploads/products/3018/d3.jpg", "/static/uploads/products/3018/d4.jpg"]', '<h2>爱哒哒 宠物训导尿垫 L码（40片） · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫砂与日常打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3018/main.jpg" alt="爱哒哒 宠物训导尿垫 L码（40片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3018/d1.jpg" alt="爱哒哒 宠物训导尿垫 L码（40片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3018/d2.png" alt="爱哒哒 宠物训导尿垫 L码（40片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3018/d3.jpg" alt="爱哒哒 宠物训导尿垫 L码（40片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3018/d4.jpg" alt="爱哒哒 宠物训导尿垫 L码（40片）"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 237, 0, 4.80, 36.00, 41.40, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3018, 'SKU03018', '{"规格": "标准装"}', 36.00, 99, '/static/uploads/products/3018/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3019, '派锐 百伊除臭吸水宠物尿垫 芒果香型 XL码（20片）', 21, NULL, '/static/uploads/products/3019/main.jpg', '["/static/uploads/products/3019/main.jpg", "/static/uploads/products/3019/d1.jpg", "/static/uploads/products/3019/d2.jpg", "/static/uploads/products/3019/d3.jpg", "/static/uploads/products/3019/d4.jpg"]', '<h2>派锐 百伊除臭吸水宠物尿垫 芒果香型 XL码（20片） · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫砂与日常打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3019/main.jpg" alt="派锐 百伊除臭吸水宠物尿垫 芒果香型 XL码（20片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3019/d1.jpg" alt="派锐 百伊除臭吸水宠物尿垫 芒果香型 XL码（20片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3019/d2.jpg" alt="派锐 百伊除臭吸水宠物尿垫 芒果香型 XL码（20片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3019/d3.jpg" alt="派锐 百伊除臭吸水宠物尿垫 芒果香型 XL码（20片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3019/d4.jpg" alt="派锐 百伊除臭吸水宠物尿垫 芒果香型 XL码（20片）"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 260, 0, 4.90, 25.20, 28.98, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3019, 'SKU03019', '{"规格": "标准装"}', 25.20, 99, '/static/uploads/products/3019/main.jpg');

INSERT INTO t_product (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags, is_deleted) VALUES (3020, '爱哒哒 宠物训导尿垫 M码（50片）', 21, NULL, '/static/uploads/products/3020/main.jpg', '["/static/uploads/products/3020/main.jpg", "/static/uploads/products/3020/d1.jpg", "/static/uploads/products/3020/d2.png", "/static/uploads/products/3020/d3.jpg", "/static/uploads/products/3020/d4.jpg"]', '<h2>爱哒哒 宠物训导尿垫 M码（50片） · 商品详情</h2>
<p>本商品由 PetMate 严选，专为猫砂与日常打造，精选优质原料、科学配比，守护爱宠健康成长。</p>
<div class="detail-image"><img src="/static/uploads/products/3020/main.jpg" alt="爱哒哒 宠物训导尿垫 M码（50片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3020/d1.jpg" alt="爱哒哒 宠物训导尿垫 M码（50片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3020/d2.png" alt="爱哒哒 宠物训导尿垫 M码（50片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3020/d3.jpg" alt="爱哒哒 宠物训导尿垫 M码（50片）"></div>
<div class="detail-image"><img src="/static/uploads/products/3020/d4.jpg" alt="爱哒哒 宠物训导尿垫 M码（50片）"></div>
<p>支持正品验真、闪电发货与 7 天无忧退换。更多规格与参数请见「规格参数」标签。</p>', 1, 283, 0, 4.60, 36.00, 41.40, '["正品保障", "闪电发货"]', 0);
INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES (3020, 'SKU03020', '{"规格": "标准装"}', 36.00, 99, '/static/uploads/products/3020/main.jpg');
