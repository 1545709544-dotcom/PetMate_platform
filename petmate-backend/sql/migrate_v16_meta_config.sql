-- migrate_v16: 将 MetaController 中交易关键参数(VIP 套餐价 / 新人礼包券额)从代码硬编码迁到 t_system_config
-- 目标：管理端可通过 /admin/system-config 改价（前端 vip/plan、coupon/gift-pack 已改为优先读配置、缺失回退常量）。
-- 幂等：config_key 为主键，ON DUPLICATE KEY UPDATE 保证重跑安全。

INSERT INTO t_system_config (config_key, config_value) VALUES
('vip.plans', '[{"key":"l1","level":1,"tag":"L1","name":"白银会员","monthly":18,"price":18,"badge":"新晋福利","perks":["双倍 AI 咨询额度","商城购物免邮","每月 1 张满减券"]},{"key":"l2","level":2,"tag":"L2","name":"黄金会员","monthly":29,"price":29,"badge":"人气之选","perks":["全年无限次免费问诊","专属客服 7×12","生日礼包","商城购物免邮"]},{"key":"l3","level":3,"tag":"L3","name":"钻石会员","monthly":98,"price":98,"badge":"全权益","perks":["全部黄金权益","线下门店 9 折","优先新品体验","专属客服 7×24","每年 2 次免费体检"]}]')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

INSERT INTO t_system_config (config_key, config_value) VALUES
('coupon.gift_pack', '[{"id":1,"name":"新人 10 元券","amount":10,"threshold":0},{"id":2,"name":"新人 30 元券","amount":30,"threshold":99},{"id":3,"name":"新人 50 元券","amount":50,"threshold":199}]')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);
