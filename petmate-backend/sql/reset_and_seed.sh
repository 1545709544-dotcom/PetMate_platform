#!/usr/bin/env bash
# Apply schema + seed from scratch. Idempotent.
set -e
cd "$(dirname "$0")"

MYSQL="mysql -uroot -p123456 --default-character-set=utf8mb4"

echo "[1/3] dropping & re-creating database..."
echo "DROP DATABASE IF EXISTS petmate; CREATE DATABASE petmate DEFAULT CHARACTER SET utf8mb4;" | $MYSQL

echo "[2/3] applying schema..."
$MYSQL petmate < schema.sql 2>&1 | grep -v "Using a password" || true

echo "[3/3] applying seed..."
$MYSQL petmate < seed.sql 2>&1 | grep -v "Using a password" || true

echo "--- counts ---"
$MYSQL petmate -e "SELECT
  (SELECT COUNT(*) FROM t_user) AS users,
  (SELECT COUNT(*) FROM t_pet) AS pets,
  (SELECT COUNT(*) FROM t_product) AS products,
  (SELECT COUNT(*) FROM t_product_category) AS categories,
  (SELECT COUNT(*) FROM t_post) AS posts,
  (SELECT COUNT(*) FROM t_consultation) AS consults,
  (SELECT COUNT(*) FROM t_consultation_message) AS messages,
  (SELECT COUNT(*) FROM t_article) AS articles,
  (SELECT COUNT(*) FROM t_order) AS orders,
  (SELECT COUNT(*) FROM t_review) AS reviews,
  (SELECT COUNT(*) FROM t_coupon) AS coupons,
  (SELECT COUNT(*) FROM t_sensitive_word) AS sensitive;" 2>&1 | grep -v "Using a password"
