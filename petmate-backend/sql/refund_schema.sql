-- ============================================================
-- 售后 / 退款表（2026-07-26 建，2026-07-30 v8 升级为完整售后）
--   本文件是「全新建库」用的最终态 DDL，与 migrate_v8_aftersale.sql 保持一致。
--   已有库请执行 migrate_v8_aftersale.sql 做增量迁移，不要跑本文件。
--   注意：这里刻意不写 DROP TABLE —— 之前误带 DROP，重建库会清掉存量售后单。
-- ============================================================

CREATE TABLE IF NOT EXISTS t_refund (
  id                    BIGINT        NOT NULL AUTO_INCREMENT,
  order_no              VARCHAR(32)   NOT NULL,
  order_id              BIGINT        NOT NULL,
  user_id               BIGINT        NOT NULL,
  amount                DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '可退上限（下单实付金额）',
  refund_amount         DECIMAL(10,2) DEFAULT NULL COMMENT '商家核定实退金额；NULL 表示按 amount 全额退',
  reason                VARCHAR(500)  DEFAULT NULL COMMENT '用户申请售后的说明',
  refund_type           VARCHAR(32)   NOT NULL DEFAULT 'REFUND_ONLY'
                          COMMENT '售后类型 REFUND_ONLY=仅退款 RETURN_REFUND=退货退款 EXCHANGE=换货',
  evidence_images       TEXT          DEFAULT NULL COMMENT '用户上传凭证图 URL，JSON 数组字符串',
  status                VARCHAR(24)   NOT NULL DEFAULT 'pending'
                          COMMENT 'pending/returning/exchanging/returned/reshipped/refunded/exchanged/rejected/appealing/arbitrated/closed',
  orig_status           VARCHAR(24)   DEFAULT NULL COMMENT '申请售后前的订单状态，驳回时还原',
  handle_note           VARCHAR(500)  DEFAULT NULL COMMENT '商家/平台处理备注',

  -- 退货 / 换货链路
  return_address        VARCHAR(255)  DEFAULT NULL COMMENT '商家提供的退货/换货收件地址',
  logistics_no          VARCHAR(64)   DEFAULT NULL COMMENT '用户寄回物流单号',
  logistics_company     VARCHAR(64)   DEFAULT NULL COMMENT '用户寄回物流公司',
  shipped_at            DATETIME      DEFAULT NULL COMMENT '用户回填寄回物流的时间',
  new_logistics_no      VARCHAR(64)   DEFAULT NULL COMMENT '换货：商家重发物流单号',
  new_logistics_company VARCHAR(64)   DEFAULT NULL COMMENT '换货：商家重发物流公司',
  reshipped_at          DATETIME      DEFAULT NULL COMMENT '换货：商家重新发货时间',

  -- 平台客服介入 / 仲裁
  prev_status           VARCHAR(24)   DEFAULT NULL COMMENT '申请介入前的状态，仲裁驳回时回退用',
  appeal_reason         VARCHAR(500)  DEFAULT NULL COMMENT '用户申请平台介入的理由',
  appeal_images         TEXT          DEFAULT NULL COMMENT '介入补充凭证图 URL，JSON 数组字符串',
  appealed_at           DATETIME      DEFAULT NULL COMMENT '申请介入时间',
  arbitrate_result      VARCHAR(16)   DEFAULT NULL COMMENT '裁定结果 refund=支持退款 reject=维持驳回',
  arbitrate_note        VARCHAR(500)  DEFAULT NULL COMMENT '平台仲裁说明',
  arbitrated_at         DATETIME      DEFAULT NULL COMMENT '平台仲裁时间',

  created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at            DATETIME      DEFAULT NULL COMMENT '商家首次处理时间',
  finished_at           DATETIME      DEFAULT NULL COMMENT '流转到终态的时间',
  updated_at            DATETIME      DEFAULT NULL COMMENT '更新时间',
  is_deleted            TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_order (order_no),
  KEY idx_status (status),
  KEY idx_user (user_id),
  KEY idx_user_status (user_id, status),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后申请单';

-- 售后操作流水：用户端「售后进度时间线」的数据源
CREATE TABLE IF NOT EXISTS t_refund_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  refund_id   BIGINT       NOT NULL COMMENT '售后单 id',
  order_no    VARCHAR(32)  NOT NULL COMMENT '冗余订单号，便于排查',
  actor_type  VARCHAR(16)  NOT NULL COMMENT '操作方 user/merchant/admin/system',
  actor_id    BIGINT       DEFAULT NULL COMMENT '操作人 id',
  actor_name  VARCHAR(64)  DEFAULT NULL COMMENT '操作人展示名',
  action      VARCHAR(32)  NOT NULL COMMENT 'apply/approve/reject/ship/confirm/reship/receive/appeal/arbitrate/close',
  from_status VARCHAR(24)  DEFAULT NULL COMMENT '流转前状态',
  to_status   VARCHAR(24)  DEFAULT NULL COMMENT '流转后状态',
  note        VARCHAR(500) DEFAULT NULL COMMENT '节点说明，直接展示给用户',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_refund (refund_id),
  KEY idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='售后操作流水（进度时间线）';
