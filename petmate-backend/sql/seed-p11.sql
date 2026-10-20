-- 仅追加 p11 一条商品 + 2 个 SKU(不重跑 seed,不冲突其他表)
USE petmate;

INSERT INTO t_product
  (id, name, category_id, brand_id, main_image, sub_images, detail, status, sale_count, view_count, rating, sale_price, original_price, tags)
VALUES
  (1010, 'PetMate 户外便携宠物水瓶 550ml', 22, 3, '/src/assets/images/products/p11-shiba-bottle.jpg',
     JSON_ARRAY('/src/assets/images/products/p11-shiba-bottle.jpg'),
     '<section><h2>核心卖点</h2><ul><li>户外便携:一体式收纳,可挂背包侧袋</li><li>550ml 大容量,一按即出水</li><li>食品级 ABS 材质,不含 BPA,安全无异味</li><li>防漏锁扣设计,倒置不洒</li><li>适用中小型犬 / 猫 / 通用宠物</li></ul><h2>产品参数</h2><table border="1" cellpadding="6" cellspacing="0" style="border-collapse:collapse;width:100%;max-width:520px;"><tr><th align="left" style="background:#f7f7f7;">参数</th><th align="left" style="background:#f7f7f7;">规格</th></tr><tr><td>容量</td><td>550ml</td></tr><tr><td>材质</td><td>食品级 ABS + 硅胶密封圈</td></tr><tr><td>颜色</td><td>蓝色 / 灰色</td></tr><tr><td>尺寸</td><td>直径 7cm × 高 23cm</td></tr><tr><td>净重</td><td>约 180g</td></tr><tr><td>适用</td><td>中小型犬 / 猫 / 通用宠物</td></tr></table><h2>使用建议</h2><p>徒步、露营、长途遛狗时为宠物随时补水。按压式出水口让陌生环境下的爱宠也能轻松喝到干净的水,告别一次性水瓶的浪费与不便。出行前请装满清水,按压顶部按钮即可出流;锁扣关闭后倒置、摇晃均不渗漏。</p><h2>清洁保养</h2><p>瓶身可拆解清洗,建议每周用温水加少量中性洗涤剂彻底清洁一次,晾干后再组装。硅胶圈可单独取出清洁,避免长期积水产生异味。</p><h2>包装清单</h2><p>水瓶主体 × 1、防漏硅胶圈(备用)× 1、说明书 × 1</p></section>',
     1, 268, 1980, 4.80, 119.00, 159.00, JSON_ARRAY('户外','便携','饮水','PetMate'));

INSERT INTO t_product_sku (product_id, sku_code, spec_values, price, stock, image) VALUES
  (1010, 'SKU-1010-001', JSON_OBJECT('颜色','蓝色','容量','550ml'), 119.00, 200, NULL),
  (1010, 'SKU-1010-002', JSON_OBJECT('颜色','灰色','容量','550ml'), 119.00, 200, NULL);
