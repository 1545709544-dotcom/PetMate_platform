-- 爱心捐助排行演示数据：为 seed 用户插入已支付订单的爱心捐赠，使排行榜有内容
-- 仅插入 status='paid' 且 donate_amount>0 的订单；排行榜只统计这类订单
-- 注意：幂等靠 order_no 唯一约束；重复执行会报重复键，属正常（演示数据已存在时跳过即可）

INSERT INTO t_order (order_no, user_id, total_amount, pay_amount, freight_amount, points_used, points_amount, status, donate_amount, paid_at, is_deleted)
VALUES
  ('SEEDDON90001', 90001, 330.00, 330.00, 0.00, 0, 0.00, 'paid', 330.00, NOW(), 0),
  ('SEEDDON10002', 10002, 268.00, 268.00, 0.00, 0, 0.00, 'paid', 268.00, NOW(), 0),
  ('SEEDDON10001', 10001, 215.00, 215.00, 0.00, 0, 0.00, 'paid', 215.00, NOW(), 0),
  ('SEEDDON10003', 10003, 156.00, 156.00, 0.00, 0, 0.00, 'paid', 156.00, NOW(), 0),
  ('SEEDDON10004', 10004, 88.00,  88.00,  0.00, 0, 0.00, 'paid', 88.00,  NOW(), 0),
  ('SEEDDON90003B', 90003, 42.00, 42.00, 0.00, 0, 0.00, 'paid', 42.00, NOW(), 0);
