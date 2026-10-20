-- Fix: t_payment_log.order_no must allow NULL.
-- VIP / non-order payments (e.g. membership open) have no order_no,
-- but the async-notify handler still writes an audit/idempotency row.
-- Making it nullable keeps the existing order-payment rows intact.
ALTER TABLE t_payment_log MODIFY COLUMN order_no VARCHAR(32) NULL;
