package com.petmate.order;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.PageResp;
import com.petmate.points.PointsService;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class OrderController {

    private final JdbcTemplate jdbc;
    private final com.petmate.pay.AlipayProperties alipayProps;
    private static final ObjectMapper SPEC_MAPPER = new ObjectMapper();
    private final com.petmate.pay.AlipayClient alipayClient;
    private final com.petmate.pay.PaymentLogService paymentLogService;
    private final com.petmate.security.IdempotencyService idempotency;
    private final PointsService pointsService;
    private final com.petmate.common.NotifyService notify;
    private final com.petmate.refund.RefundLogService refundLog;
    private final com.petmate.config.SystemConfigService sysConfig;

    public OrderController(JdbcTemplate jdbc,
                           com.petmate.pay.AlipayProperties alipayProps,
                           com.petmate.pay.AlipayClient alipayClient,
                           com.petmate.pay.PaymentLogService paymentLogService,
                           com.petmate.security.IdempotencyService idempotency,
                           PointsService pointsService,
                           com.petmate.common.NotifyService notify,
                           com.petmate.refund.RefundLogService refundLog,
                           com.petmate.config.SystemConfigService sysConfig) {
        this.jdbc = jdbc;
        this.alipayProps = alipayProps;
        this.alipayClient = alipayClient;
        this.paymentLogService = paymentLogService;
        this.idempotency = idempotency;
        this.pointsService = pointsService;
        this.notify = notify;
        this.refundLog = refundLog;
        this.sysConfig = sysConfig;
    }

    // ================= 购物车 =================

    @GetMapping("/cart")
    public ApiResp<List<Map<String, Object>>> getCart() {
        long uid = TokenContext.requireUserId();
        // 用 LEFT JOIN,即使商品/SKU 已下架也能保留记录(标记为无效,供前端"清空失效商品")
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT c.id, c.sku_id, c.product_id, p.name AS product_name, p.main_image AS product_image, " +
            "s.price, c.quantity, c.selected, s.spec_values, " +
            "p.status AS product_status, p.is_deleted AS product_deleted, s.stock AS sku_stock " +
            "FROM t_cart c " +
            "LEFT JOIN t_product_sku s ON c.sku_id = s.id AND s.is_deleted = 0 " +
            "LEFT JOIN t_product p ON c.product_id = p.id " +
            "WHERE c.user_id = ? AND c.is_deleted = 0 ORDER BY c.id DESC", uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("skuId", r.get("sku_id"));
            m.put("productId", r.get("product_id"));
            m.put("productName", r.get("product_name"));
            m.put("productImage", r.get("product_image"));
            m.put("price", r.get("price"));
            m.put("quantity", r.get("quantity"));
            m.put("selected", ((Number) r.get("selected")).intValue() == 1);
            m.put("specValues", parseSpec(r.get("spec_values")));
            // 商品已删除 / 已下架 / SKU 缺失 / 缺货 => 失效
            boolean productDeleted = r.get("product_deleted") != null
                && ((Number) r.get("product_deleted")).intValue() == 1;
            boolean offShelf = r.get("product_status") == null
                || ((Number) r.get("product_status")).intValue() != 1;
            boolean skuMissing = r.get("sku_stock") == null;
            boolean outOfStock = !skuMissing && ((Number) r.get("sku_stock")).intValue() <= 0;
            boolean valid = !productDeleted && !offShelf && !skuMissing && !outOfStock;
            String invalidReason = null;
            if (productDeleted || offShelf) invalidReason = "商品已下架";
            else if (skuMissing) invalidReason = "商品已失效";
            else if (outOfStock) invalidReason = "已售罄";
            m.put("valid", valid);
            m.put("invalidReason", invalidReason);
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    /** 把数据库里存成 JSON 字符串的 SKU 规格解析成对象返回；解析失败则回退原串。 */
    private Object parseSpec(Object raw) {
        if (raw == null) return Collections.emptyMap();
        try {
            return SPEC_MAPPER.readValue(raw.toString(), Map.class);
        } catch (Exception e) {
            return raw.toString();
        }
    }

    /** 清空失效商品(商品下架 / SKU 缺货 / 已删除的购物车项) */
    @DeleteMapping("/cart/clear-invalid")
    public ApiResp<Map<String, Object>> clearInvalidCart() {
        long uid = TokenContext.requireUserId();
        // 物理删除：避免与 uk_user_sku(user_id,sku_id,is_deleted) 已删除行冲突
        int removed = jdbc.update(
            "DELETE c FROM t_cart c " +
            "LEFT JOIN t_product_sku s ON c.sku_id = s.id AND s.is_deleted = 0 " +
            "LEFT JOIN t_product p ON c.product_id = p.id " +
            "WHERE c.user_id = ? AND c.is_deleted = 0 " +
            "AND (p.id IS NULL OR p.status != 1 OR p.is_deleted = 1 OR s.id IS NULL OR s.stock <= 0)",
            uid);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", removed);
        return ApiResp.ok(out);
    }

    /** 清空当前用户购物车的全部商品（物理删除，绕过 uk_user_sku 冲突） */
    @DeleteMapping("/cart/clear")
    public ApiResp<Map<String, Object>> clearCart() {
        long uid = TokenContext.requireUserId();
        int removed = jdbc.update("DELETE FROM t_cart WHERE user_id = ?", uid);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("removed", removed);
        return ApiResp.ok(out);
    }

    @PostMapping("/cart")
    public ApiResp<Void> addCart(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        long skuId = ((Number) body.get("skuId")).longValue();
        int quantity = body.get("quantity") == null ? 1 : ((Number) body.get("quantity")).intValue();
        // 查 product_id + 商品状态（下架/删除的商品禁止加购）
        List<Map<String, Object>> sku = jdbc.queryForList(
            "SELECT s.product_id, s.stock, p.status AS product_status, p.is_deleted AS product_deleted " +
            "FROM t_product_sku s JOIN t_product p ON s.product_id = p.id WHERE s.id = ? AND s.is_deleted = 0", skuId);
        if (sku.isEmpty()) throw new BizException(ErrorCode.PRODUCT_OFFLINE);
        long productId = ((Number) sku.get(0).get("product_id")).longValue();
        int stock = ((Number) sku.get(0).get("stock")).intValue();
        int pStatus = ((Number) sku.get(0).get("product_status")).intValue();
        int pDeleted = ((Number) sku.get(0).get("product_deleted")).intValue();
        if (pDeleted == 1 || pStatus != 1) throw new BizException(ErrorCode.PRODUCT_OFFLINE);
        if (stock < quantity) throw new BizException(ErrorCode.STOCK_NOT_ENOUGH);

        // 合并相同 SKU
        List<Map<String, Object>> exist = jdbc.queryForList(
            "SELECT id, quantity FROM t_cart WHERE user_id = ? AND sku_id = ? AND is_deleted = 0", uid, skuId);
        if (!exist.isEmpty()) {
            long id = ((Number) exist.get(0).get("id")).longValue();
            int oldQ = ((Number) exist.get(0).get("quantity")).intValue();
            jdbc.update("UPDATE t_cart SET quantity = ? WHERE id = ?", oldQ + quantity, id);
        } else {
            jdbc.update("INSERT INTO t_cart (user_id, sku_id, product_id, quantity, selected) VALUES (?, ?, ?, ?, 1)",
                    uid, skuId, productId, quantity);
        }
        return ApiResp.ok();
    }

    @PutMapping("/cart/{id}")
    public ApiResp<Void> updateCart(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        if (body.containsKey("quantity")) {
            int q = ((Number) body.get("quantity")).intValue();
            if (q < 1) throw new BizException(ErrorCode.PARAM_ERROR, "数量必须 ≥ 1");
            jdbc.update("UPDATE t_cart SET quantity = ? WHERE id = ? AND user_id = ? AND is_deleted = 0", q, id, uid);
        }
        if (body.containsKey("selected")) {
            int s = Boolean.TRUE.equals(body.get("selected")) ? 1 : 0;
            jdbc.update("UPDATE t_cart SET selected = ? WHERE id = ? AND user_id = ? AND is_deleted = 0", s, id, uid);
        }
        return ApiResp.ok();
    }

    @DeleteMapping("/cart")
    public ApiResp<Void> deleteCart(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        @SuppressWarnings("unchecked")
        List<Number> ids = (List<Number>) body.get("ids");
        if (ids != null && !ids.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
            Object[] args = new Object[ids.size() + 1];
            for (int i = 0; i < ids.size(); i++) args[i] = ids.get(i).longValue();
            args[ids.size()] = uid;
            // 物理删除：购物车为临时态，下单后商品已快照到 t_order_item；
            // 软删除会与 uk_user_sku(user_id,sku_id,is_deleted) 已删除行冲突。
            jdbc.update("DELETE FROM t_cart WHERE id IN (" + placeholders + ") AND user_id = ?", args);
        }
        return ApiResp.ok();
    }

    // ================= 订单 =================

    @PostMapping("/order")
    @Transactional
    public ApiResp<Map<String, Object>> submitOrder(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        @SuppressWarnings("unchecked")
        List<Number> cartIds = (List<Number>) body.get("cartIds");
        long addressId = ((Number) body.get("addressId")).longValue();
        if (cartIds == null || cartIds.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "购物车为空");

        // 收货地址
        List<Map<String, Object>> addr = jdbc.queryForList(
            "SELECT receiver, phone, province, city, district, detail FROM t_user_address WHERE id = ? AND user_id = ?",
            addressId, uid);
        if (addr.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "地址不存在");
        Map<String, Object> a = addr.get(0);
        String receiver = (String) a.get("receiver");
        String phone = (String) a.get("phone");
        String address = String.valueOf(a.get("province")) + String.valueOf(a.get("city"))
                + String.valueOf(a.get("district")) + String.valueOf(a.get("detail"));

        // 查 cart + sku + product
        String placeholders = String.join(",", Collections.nCopies(cartIds.size(), "?"));
        Object[] args = new Object[cartIds.size() + 1];
        for (int i = 0; i < cartIds.size(); i++) args[i] = cartIds.get(i).longValue();
        args[cartIds.size()] = uid;
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT c.id AS cart_id, c.sku_id, c.product_id, c.quantity, s.price, s.stock, p.name AS product_name, p.main_image AS product_image, p.status " +
            "FROM t_cart c JOIN t_product_sku s ON c.sku_id = s.id AND s.is_deleted = 0 " +
            "JOIN t_product p ON c.product_id = p.id " +
            "WHERE c.id IN (" + placeholders + ") AND c.user_id = ? AND c.is_deleted = 0 AND c.selected = 1 " +
            "AND p.status = 1 AND p.is_deleted = 0 AND s.stock > 0", args);
        if (items.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "未选中有效商品（部分商品可能已下架或售罄）");

        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> it : items) {
            if (((Number) it.get("status")).intValue() != 1) throw new BizException(ErrorCode.PRODUCT_OFFLINE);
            int stock = ((Number) it.get("stock")).intValue();
            int q = ((Number) it.get("quantity")).intValue();
            if (stock < q) throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, it.get("product_name") + " 库存不足");
            BigDecimal price = (BigDecimal) it.get("price");
            total = total.add(price.multiply(BigDecimal.valueOf(q)));
        }
        BigDecimal freight = BigDecimal.ZERO; // 演示：包邮
        // 运费规则：VIP 用户（vipLevel>=2 黄金及以上 且 未过期）免邮；其他用户按 t_system_config.default_freight 计费（默认 10 元）
        Integer userVipLevel = jdbc.queryForObject(
            "SELECT COALESCE(vip_level,0) FROM t_user WHERE id = ? AND is_deleted = 0", Integer.class, uid);
        boolean vipFreeShip = false;
        if (userVipLevel != null && userVipLevel >= 2) {
            Integer active = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_user WHERE id = ? AND is_deleted = 0 " +
                "AND vip_level >= 2 AND vip_expire_at IS NOT NULL AND vip_expire_at > NOW()",
                Integer.class, uid);
            vipFreeShip = active != null && active > 0;
        }
        if (!vipFreeShip) {
            freight = sysConfig.getDefaultFreight();
        }

        // ===== 爱心捐赠 =====
        BigDecimal donate = BigDecimal.ZERO;
        if (body.get("donate") != null) {
            try {
                donate = new BigDecimal(body.get("donate").toString());
            } catch (Exception ignore) {
                donate = BigDecimal.ZERO;
            }
        }
        if (donate.compareTo(BigDecimal.ZERO) < 0) donate = BigDecimal.ZERO;

        // ===== 优惠券抵扣 =====
        Long couponId = body.get("couponId") == null ? null : ((Number) body.get("couponId")).longValue();
        BigDecimal couponAmount = BigDecimal.ZERO;
        if (couponId != null) {
            couponAmount = computeCouponDiscount(couponId, uid, total);
            if (couponAmount.compareTo(BigDecimal.ZERO) > 0) {
                // 标记用户券为已使用
                jdbc.update(
                    "UPDATE t_user_coupon SET status = 'used' WHERE user_id = ? AND coupon_id = ? AND status != 'used'",
                    uid, couponId);
            } else {
                couponId = null; // 不满足条件则不抵扣
            }
        }
        BigDecimal pay = total.add(freight).subtract(couponAmount).add(donate);
        if (pay.compareTo(BigDecimal.ZERO) < 0) pay = BigDecimal.ZERO;

        // ===== 鱼干抵扣 =====
        // 10 鱼干 = ¥1，单笔最多抵订单金额 10%（后端已截断）
        int pointsUse = 0;
        BigDecimal pointsAmount = BigDecimal.ZERO;
        if (body.get("pointsUse") != null) {
            try { pointsUse = ((Number) body.get("pointsUse")).intValue(); } catch (Exception ignore) {}
        }
        if (pointsUse > 0 && pay.compareTo(BigDecimal.ZERO) > 0) {
            Map<String, Object> redeem = pointsService.redeem(uid, pointsUse, pay);
            pointsUse = (Integer) redeem.get("pointsUsed");
            pointsAmount = (BigDecimal) redeem.get("discount");
            if (pointsUse > 0) {
                pay = pay.subtract(pointsAmount);
                if (pay.compareTo(BigDecimal.ZERO) < 0) pay = BigDecimal.ZERO;
            }
        } else {
            pointsUse = 0;
        }

        // 生成订单号
        String orderNo = "P" + System.currentTimeMillis() + String.format("%04d", new Random().nextInt(10000));
        String receiverSnap = String.format("{\"receiver\":\"%s\",\"phone\":\"%s\",\"address\":\"%s\"}",
                receiver, phone, address);
        // 推导门店：取首个商品所属商家的门店（演示为单商家下单）
        Long storeId = jdbc.queryForObject(
            "SELECT s.id FROM t_store s JOIN t_product p ON p.merchant_id = s.merchant_id " +
            "WHERE p.id = ? AND s.is_deleted = 0 LIMIT 1",
            Long.class, ((Number) items.get(0).get("product_id")).longValue());
        jdbc.update(
            "INSERT INTO t_order (order_no, user_id, store_id, total_amount, pay_amount, freight_amount, coupon_id, coupon_amount, points_used, points_amount, donate_amount, status, receiver_snapshot, remark, expire_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'pending_pay', ?, ?, DATE_ADD(NOW(), INTERVAL 30 MINUTE))",
            orderNo, uid, storeId, total, pay, freight, couponId, couponAmount, pointsUse, pointsAmount, donate, receiverSnap, body.get("remark"));

        long orderId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        // 通知商家：新订单待处理
        if (storeId != null) {
            Long mId = jdbc.queryForObject("SELECT merchant_id FROM t_store WHERE id = ?", Long.class, storeId);
            if (mId != null) {
                notify.notify(mId, "order", "新订单待处理",
                    "订单 " + orderNo + " 已提交，金额 ¥" + pay, orderNo);
            }
        }
        List<Long> orderedCartIds = new ArrayList<>();
        for (Map<String, Object> it : items) {
            jdbc.update(
                "INSERT INTO t_order_item (order_id, product_id, sku_id, product_name, product_image, price, quantity) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                orderId, it.get("product_id"), it.get("sku_id"),
                it.get("product_name"), it.get("product_image"),
                it.get("price"), it.get("quantity"));
            // 原子扣库存：CAS 守卫 stock>=q，避免并发超卖变负库存（#15）
            int dec = jdbc.update("UPDATE t_product_sku SET stock = stock - ? WHERE id = ? AND stock >= ?",
                it.get("quantity"), it.get("sku_id"), it.get("quantity"));
            if (dec == 0) throw new BizException(ErrorCode.STOCK_NOT_ENOUGH, it.get("product_name") + " 库存不足");
            jdbc.update("UPDATE t_product SET sale_count = sale_count + ? WHERE id = ?", it.get("quantity"), it.get("product_id"));
            orderedCartIds.add(((Number) it.get("cart_id")).longValue());
        }
        // 仅移除实际成单的购物车项；下架/售罄等未通过校验的项保留，避免误删用户数据（#16）
        if (!orderedCartIds.isEmpty()) {
            String ph = String.join(",", Collections.nCopies(orderedCartIds.size(), "?"));
            Object[] delArgs = new Object[orderedCartIds.size() + 1];
            for (int i = 0; i < orderedCartIds.size(); i++) delArgs[i] = orderedCartIds.get(i);
            delArgs[orderedCartIds.size()] = uid;
            jdbc.update("DELETE FROM t_cart WHERE id IN (" + ph + ") AND user_id = ?", delArgs);
        }
        // 被跳过（下架/售罄）的购物车项，供前端提示
        List<Long> skippedIds = cartIds.stream().map(Number::longValue)
            .filter(c -> !orderedCartIds.contains(c)).toList();
        List<String> skippedNames = List.of();
        if (!skippedIds.isEmpty()) {
            String ph2 = String.join(",", Collections.nCopies(skippedIds.size(), "?"));
            Object[] skipArgs = new Object[skippedIds.size() + 1];
            for (int i = 0; i < skippedIds.size(); i++) skipArgs[i] = skippedIds.get(i);
            skipArgs[skippedIds.size()] = uid;
            skippedNames = jdbc.queryForList(
                "SELECT p.name FROM t_cart c LEFT JOIN t_product p ON c.product_id = p.id " +
                "WHERE c.id IN (" + ph2 + ") AND c.user_id = ?", skipArgs)
                .stream().map(r -> String.valueOf(r.get("name"))).toList();
        }

        Map<String, Object> detail = buildOrderDetail(orderNo, uid);
        detail.put("skippedItems", skippedNames);
        return ApiResp.ok(detail);
    }

    @GetMapping("/order/list")
    public ApiResp<PageResp<Map<String, Object>>> listOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statuses,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long uid = TokenContext.requireUserId();
        // statuses 优先：支持 CSV 多状态（前端"已完成"=done+refunded）。
        // 兼容旧版单值 status 入参。
        List<String> statusList = parseStatuses(statuses, status);
        String where = "WHERE user_id = ? AND is_deleted = 0";
        Object[] baseArgs;
        if (!statusList.isEmpty()) {
            String placeholders = String.join(",", Collections.nCopies(statusList.size(), "?"));
            where += " AND status IN (" + placeholders + ")";
            baseArgs = new Object[statusList.size() + 1];
            baseArgs[0] = uid;
            for (int i = 0; i < statusList.size(); i++) baseArgs[i + 1] = statusList.get(i);
        } else {
            baseArgs = new Object[]{uid};
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_order " + where, Long.class, baseArgs);
        Object[] allArgs = Arrays.copyOf(baseArgs, baseArgs.length + 2);
        allArgs[baseArgs.length] = size;
        allArgs[baseArgs.length + 1] = (page - 1) * size;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_no, user_id, total_amount, pay_amount, freight_amount, status, receiver_snapshot, coupon_amount, coupon_id, created_at " +
            "FROM t_order " + where + " ORDER BY id DESC LIMIT ? OFFSET ?", allArgs);
        // 列表也挂上「最近一张售后单」摘要，前端「已完成」tab 可据此直接展示售后状态与入口
        List<Map<String, Object>> enriched = rows.stream()
            .map(this::normalizeOrder)
            .peek(this::attachAftersaleSummary)
            .peek(this::attachReviewSummary)
            .toList();
        return ApiResp.ok(PageResp.of(enriched, total == null ? 0 : total, page, size));
    }

    /** 解析 statuses 入参：CSV 拆分 + 去空 + 去重；兼容旧的单值 status。 */
    private static List<String> parseStatuses(String statusesCsv, String singleStatus) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (statusesCsv != null && !statusesCsv.isBlank()) {
            for (String s : statusesCsv.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) set.add(t);
            }
        }
        if (set.isEmpty() && singleStatus != null && !singleStatus.isBlank()) {
            set.add(singleStatus.trim());
        }
        return new ArrayList<>(set);
    }

    /**
     * 为订单行挂上最近一张售后单摘要 + canApplyAftersale 标记。
     * 逻辑与 {@link #buildOrderDetail(String, long)} 保持一致，列表场景复用。
     */
    private void attachAftersaleSummary(Map<String, Object> order) {
        String orderNo = (String) order.get("orderNo");
        if (orderNo == null) return;
        String oStatus = (String) order.get("status");
        List<Map<String, Object>> rfs = jdbc.queryForList(
            "SELECT id, refund_type, status, amount, refund_amount, reason, handle_note, created_at " +
            "FROM t_refund WHERE order_no = ? AND is_deleted = 0 ORDER BY id DESC LIMIT 1", orderNo);
        if (!rfs.isEmpty()) {
            Map<String, Object> rf = rfs.get(0);
            String rfStatus = (String) rf.get("status");
            String rfType = (String) rf.get("refund_type");
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("id", rf.get("id"));
            a.put("type", rfType);
            a.put("typeText", com.petmate.refund.RefundStatus.typeText(rfType));
            a.put("status", rfStatus);
            a.put("statusText", com.petmate.refund.RefundStatus.statusText(rfStatus));
            a.put("amount", rf.get("amount"));
            a.put("refundAmount", rf.get("refund_amount"));
            a.put("reason", rf.get("reason"));
            a.put("handleNote", rf.get("handle_note"));
            a.put("createdAt", rf.get("created_at") == null ? null : rf.get("created_at").toString());
            a.put("isFinal", com.petmate.refund.RefundStatus.isFinal(rfStatus));
            order.put("aftersale", a);
        }
        boolean hasActive = order.get("aftersale") != null
            && !((Boolean) ((Map<?, ?>) order.get("aftersale")).get("isFinal"));
        order.put("canApplyAftersale",
            com.petmate.refund.RefundStatus.ORDER_CAN_APPLY.contains(oStatus) && !hasActive);
    }

    /**
     * 为「已完成(done)」订单挂上评价完成标记 reviewedAll。
     * 仅 done 订单需要：done 且全部商品均已评价 -> reviewedAll=true（前端「待评价」tab 据此剔除）。
     * 与 {@link ProductReviewController#checkReviewed(String)} 口径一致：按 order_id + user_id 比对
     * t_product_review（is_deleted=0）覆盖 t_order_item 的 DISTINCT product_id。
     */
    private void attachReviewSummary(Map<String, Object> order) {
        if (!"done".equals(order.get("status"))) return;
        long orderId = ((Number) order.get("id")).longValue();
        long uid = ((Number) order.get("userId")).longValue();
        List<Long> itemPids = jdbc.queryForList(
            "SELECT DISTINCT product_id FROM t_order_item WHERE order_id = ?", Long.class, orderId);
        if (itemPids.isEmpty()) {
            order.put("reviewedAll", true);
            return;
        }
        List<Long> reviewedPids = jdbc.queryForList(
            "SELECT product_id FROM t_product_review WHERE order_id = ? AND user_id = ? AND is_deleted = 0",
            Long.class, orderId, uid);
        Set<Long> reviewedSet = new HashSet<>(reviewedPids);
        order.put("reviewedAll", reviewedSet.containsAll(itemPids));
    }

    @GetMapping("/order/{orderNo}")
    public ApiResp<Map<String, Object>> orderDetail(@PathVariable String orderNo) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> od = buildOrderDetail(orderNo, uid);
        if (od == null) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        return ApiResp.ok(od);
    }

    @PostMapping("/order/{orderNo}/cancel")
    @Transactional
    public ApiResp<Void> cancelOrder(@PathVariable String orderNo) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> od = jdbc.queryForMap(
            "SELECT id, status, coupon_id, points_used FROM t_order WHERE order_no = ? AND user_id = ? AND is_deleted = 0", orderNo, uid);
        String status = (String) od.get("status");
        if (!"pending_pay".equals(status)) throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅待支付订单可取消");
        long orderId = ((Number) od.get("id")).longValue();

        // 回补库存 + 扣减销量
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT product_id, sku_id, quantity FROM t_order_item WHERE order_id = ?", orderId);
        for (Map<String, Object> it : items) {
            int q = ((Number) it.get("quantity")).intValue();
            jdbc.update("UPDATE t_product_sku SET stock = stock + ? WHERE id = ?",
                q, ((Number) it.get("sku_id")).longValue());
            jdbc.update("UPDATE t_product SET sale_count = GREATEST(sale_count - ?, 0) WHERE id = ?",
                q, ((Number) it.get("product_id")).longValue());
        }

        // 回退优惠券 + 鱼干（下单时扣了，取消必须退回，否则资产凭空蒸发）
        Object cid = od.get("coupon_id");
        int pointsUsed = od.get("points_used") == null ? 0 : ((Number) od.get("points_used")).intValue();
        rollbackOrderAssets(orderNo, orderId, uid, cid, pointsUsed);

        jdbc.update("UPDATE t_order SET status = 'cancelled' WHERE order_no = ?", orderNo);
        return ApiResp.ok();
    }

    // ================= 退款 / 售后 =================

    @PostMapping("/order/{orderNo}/refund")
    @Transactional
    public ApiResp<Map<String, Object>> applyRefund(@PathVariable String orderNo,
                                                    @RequestBody(required = false) Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> od = jdbc.queryForMap(
            "SELECT id, status, pay_amount, user_id FROM t_order WHERE order_no = ? AND user_id = ? AND is_deleted = 0",
            orderNo, uid);
        String status = (String) od.get("status");
        if (!com.petmate.refund.RefundStatus.ORDER_CAN_APPLY.contains(status)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "当前订单状态不可申请售后");
        }
        // 只要还有一单没走到终态，就不允许重复发起（原先只拦 pending，
        // 导致 returning/returned 期间还能再提一单，同一订单会出现多条并行售后）
        Integer existing = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_refund WHERE order_no = ? AND is_deleted = 0 " +
            "AND status NOT IN ('refunded','exchanged','rejected','arbitrated','closed')",
            Integer.class, orderNo);
        if (existing != null && existing > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已有进行中的售后申请，请勿重复提交");
        }
        String type = body == null ? com.petmate.refund.RefundStatus.TYPE_REFUND_ONLY
            : (String) body.getOrDefault("type", com.petmate.refund.RefundStatus.TYPE_REFUND_ONLY);
        if (!com.petmate.refund.RefundStatus.isValidType(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的售后类型");
        }
        // 换货必须已发货才有意义（没发货直接退款即可）
        if (com.petmate.refund.RefundStatus.TYPE_EXCHANGE.equals(type) && "paid".equals(status)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "订单尚未发货，请选择「仅退款」");
        }
        String reason = body == null ? null : (String) body.get("reason");
        List<String> images = body == null ? null : (List<String>) body.get("images");
        String evidenceJson = null;
        if (images != null && !images.isEmpty()) {
            try {
                evidenceJson = SPEC_MAPPER.writeValueAsString(images);
            } catch (Exception ignored) {
                evidenceJson = null;
            }
        }
        BigDecimal amount = (BigDecimal) od.get("pay_amount");
        long orderId = ((Number) od.get("id")).longValue();
        jdbc.update(
            "INSERT INTO t_refund (order_no, order_id, user_id, amount, refund_amount, reason, refund_type, " +
            "evidence_images, status, orig_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', ?)",
            orderNo, orderId, uid, amount, amount, reason, type, evidenceJson, status);
        long refundId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        // 换货不涉及资金，订单不进 refunding，保持原状态避免误导用户
        if (!com.petmate.refund.RefundStatus.TYPE_EXCHANGE.equals(type)) {
            jdbc.update("UPDATE t_order SET status = 'refunding' WHERE order_no = ?", orderNo);
        }
        String typeText = com.petmate.refund.RefundStatus.typeText(type);
        refundLog.logUser(refundId, orderNo, uid, com.petmate.refund.RefundLogService.ACT_APPLY,
            null, "pending", "买家提交" + typeText + "申请：" + (reason == null ? "" : reason));
        // 通知商家：新的售后申请
        Long sId = jdbc.queryForObject("SELECT store_id FROM t_order WHERE order_no = ?", Long.class, orderNo);
        if (sId != null) {
            Long mId = jdbc.queryForObject("SELECT merchant_id FROM t_store WHERE id = ?", Long.class, sId);
            if (mId != null) {
                notify.notify(mId, "refund", "新的售后申请",
                    "订单 " + orderNo + " 用户发起" + typeText + "：" + (reason == null ? "" : reason), orderNo);
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", true);
        r.put("orderNo", orderNo);
        r.put("refundId", refundId);
        return ApiResp.ok(r);
    }

    // ================= 支付 =================

    @PostMapping("/pay/{orderNo}")
    public ApiResp<Map<String, Object>> pay(@PathVariable String orderNo,
                                            @RequestBody(required = false) Map<String, Object> body,
                                            jakarta.servlet.http.HttpServletRequest req) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> od = jdbc.queryForMap(
            "SELECT id, status, pay_amount FROM t_order WHERE order_no = ? AND user_id = ? AND is_deleted = 0", orderNo, uid);
        String status = (String) od.get("status");
        if (!"pending_pay".equals(status)) throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "订单不可支付");

        String channel = body == null ? "alipay" : (String) body.getOrDefault("channel", "alipay");
        if (!"alipay".equals(channel)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不支持的支付方式: " + channel);
        }

        // 客户端防重：60s 内同 orderNo+uid 重复请求直接返回上次的 payUrl
        String idemKey = "pay:" + orderNo + ":" + uid;
        Object cached = idempotency.getCachedResponse(idemKey);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) cached;
            return ApiResp.ok(m);
        }

        // ===== 支付宝未启用：演示态直接置为已支付 =====
        if (!alipayProps.isEnabled()) {
            jdbc.update("UPDATE t_order SET status = 'paid', paid_at = ?, pay_channel = ? WHERE order_no = ?",
                    Timestamp.valueOf(LocalDateTime.now()),
                    "alipay".equals(channel) ? null : channel,
                    orderNo);
            // 消费奖励：1元 = 1鱼干（受每日上限约束）
            BigDecimal payAmt = (BigDecimal) od.get("pay_amount");
            int consumeReward = payAmt == null ? 0 : payAmt.intValue();
            int granted = pointsService.earn(uid, "consume", consumeReward, "消费奖励",
                    ((Number) od.get("id")).longValue());
            notifyOrderPaid(uid, orderNo, payAmt);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", true);
            data.put("payUrl", "");
            data.put("pageBody", "");
            data.put("outTradeNo", "");
            data.put("demoMode", true);
            data.put("pointsGranted", granted);
            idempotency.record(idemKey, data);
            return ApiResp.ok(data);
        }

        // ===== 真实支付宝预下单 =====
        BigDecimal amount = (BigDecimal) od.get("pay_amount");
        // 每次支付尝试生成新的 out_trade_no：避免重复预下单（取消/返回后再次支付）触发支付宝
        // TRADE_HAS_EXIST 错误。60s 内的重复点击由上层 idempotency 缓存兜底，不会走到这里。
        String outTradeNo = "PO" + System.currentTimeMillis() + uid;
        jdbc.update(
            "UPDATE t_order SET out_trade_no = ?, pay_channel = 'alipay' " +
            "WHERE order_no = ? AND status = 'pending_pay'",
            outTradeNo, orderNo);

        try {
            // return_url：优先用前端传来的动态 origin 根路径（代理/预览域名下硬编码 localhost 会 404）。
            // 根路径 + 查询参数模式由前端路由守卫重定向到 /user/order/detail/:orderNo，不依赖服务端 SPA 回退。
            String orderReturnBase = (body != null) ? (String) body.get("returnBase") : null;
            String orderReturnUrlOverride = (orderReturnBase != null && !orderReturnBase.isEmpty())
                ? orderReturnBase + "/?payType=order&orderNo=" + orderNo
                : "http://localhost:5173/?payType=order&orderNo=" + orderNo;
            com.petmate.pay.AlipayClient.PagePayResult pr =
                alipayClient.pagePay(outTradeNo, orderNo, amount, "PetMate 宠物用品-" + orderNo,
                    orderReturnUrlOverride);

            // 写预下单日志（pending）
            paymentLogService.log(orderNo, outTradeNo, null, "alipay", "page_pay",
                amount, "pending", null);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("success", true);
            data.put("payUrl", pr.payUrl == null ? "" : pr.payUrl);
            data.put("pageBody", pr.pageBody == null ? "" : pr.pageBody);
            data.put("outTradeNo", pr.outTradeNo);
            data.put("demoMode", false);
            idempotency.record(idemKey, data);
            return ApiResp.ok(data);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "预下单失败：" + e.getMessage());
        }
    }

    /**
     * 订单支付结果轮询（前端打开收银台后轮询，直到异步通知把订单置 paid）。
     * 支付宝沙箱异步通知无法回打 localhost:8080，故由前端轮询驱动此处主动查询并置 paid，
     * 解除对异步通知可达性的依赖（公网部署仍可走异步通知，二者幂等互不冲突）。
     */
    @GetMapping("/order/pay/status")
    public ApiResp<Map<String, Object>> orderPayStatus(@RequestParam String orderNo) {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_no, user_id, pay_amount, out_trade_no, status FROM t_order " +
            "WHERE order_no = ? AND user_id = ? AND is_deleted = 0", orderNo, uid);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (rows.isEmpty()) {
            out.put("status", "not_found");
            out.put("paid", false);
            return ApiResp.ok(out);
        }
        Map<String, Object> od = rows.get(0);
        String status = (String) od.get("status");

        // 待支付：主动查询支付宝交易状态（沙箱异步通知不可达时的兜底）
        if ("pending_pay".equals(status)) {
            String outTradeNo = (String) od.get("out_trade_no");
            if (outTradeNo != null && !outTradeNo.isEmpty()) {
                try {
                    com.petmate.pay.AlipayClient.TradeQueryResult qr = alipayClient.tradeQuery(outTradeNo);
                    String tradeStatus = qr.tradeStatus;
                    if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                        int affected = jdbc.update(
                            "UPDATE t_order SET status = 'paid', paid_at = NOW(), trade_no = ?, pay_channel = 'alipay' " +
                            "WHERE order_no = ? AND status = 'pending_pay'",
                            qr.tradeNo == null ? "" : qr.tradeNo, orderNo);
                        // 仅首个成功翻 paid 的调用发放消费鱼干（1 元 = 1 鱼干），与异步通知幂等互斥
                        if (affected > 0) {
                            BigDecimal payAmt = (BigDecimal) od.get("pay_amount");
                            int consumeReward = payAmt == null ? 0 : payAmt.intValue();
                            pointsService.earn(uid, "consume", consumeReward, "消费奖励",
                                ((Number) od.get("id")).longValue());
                            notifyOrderPaid(uid, orderNo, payAmt);
                        }
                        paymentLogService.log(orderNo, outTradeNo, qr.tradeNo, "alipay", "order_query",
                            (BigDecimal) od.get("pay_amount"), "success", null);
                        status = "paid";
                    }
                } catch (Exception e) {
                    // 查询失败（沙箱网关抖动 / 代理不可达）：保持 pending，前端继续轮询
                }
            }
        }

        out.put("status", status);
        out.put("paid", "paid".equals(status));
        return ApiResp.ok(out);
    }

    // ================= 确认收货 / 物流 =================

    @PostMapping("/order/{orderNo}/receive")
    @Transactional
    public ApiResp<Void> receiveOrder(@PathVariable String orderNo) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> od = jdbc.queryForMap(
            "SELECT id, status FROM t_order WHERE order_no = ? AND user_id = ? AND is_deleted = 0", orderNo, uid);
        String status = (String) od.get("status");
        if (!"shipping".equals(status)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID, "仅待收货订单可确认收货");
        }
        jdbc.update(
            "UPDATE t_order SET status = 'done', delivered_at = ? WHERE order_no = ?",
            Timestamp.valueOf(LocalDateTime.now()), orderNo);
        notify.notifyUser(uid, "order", "交易完成",
            "您已确认收货，订单 " + orderNo + " 交易完成，期待您的评价~", orderNo);
        return ApiResp.ok();
    }

    /** 支付成功通知买家（带幂等：避免演示支付 + 轮询两条路径重复推送） */
    private void notifyOrderPaid(long uid, String orderNo, BigDecimal payAmt) {
        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_user_notification WHERE user_id = ? AND type = 'order' " +
            "AND related_id = ? AND title = '支付成功'", Integer.class, uid, orderNo);
        if (exists != null && exists > 0) return;
        notify.notifyUser(uid, "order", "支付成功",
            "您的订单 " + orderNo + " 已支付成功，金额 ¥" + payAmt, orderNo);
    }

    /**
     * 物流轨迹：演示环境无第三方物流,基于订单状态与时间合成一条合理的轨迹时间线。
     * 真实接入时可替换为快递 100 / 快递鸟 等回调。
     */
    @GetMapping("/order/{orderNo}/logistics")
    public ApiResp<List<Map<String, Object>>> logistics(@PathVariable String orderNo) {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT status, created_at, paid_at, delivered_at FROM t_order " +
            "WHERE order_no = ? AND user_id = ? AND is_deleted = 0", orderNo, uid);
        if (rows.isEmpty()) throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        Map<String, Object> o = rows.get(0);
        String status = (String) o.get("status");
        LocalDateTime created = (LocalDateTime) o.get("created_at");
        LocalDateTime paid = (LocalDateTime) o.get("paid_at");
        LocalDateTime delivered = (LocalDateTime) o.get("delivered_at");

        // 已付款语义：含退款中/已退款/售后等后续态，避免「退款中」订单显示成未付款
        boolean paidDone = paid != null
                || List.of("paid", "shipping", "done", "refunding", "refunded",
                           "aftersale", "completed").contains(String.valueOf(status));
        boolean shipped = delivered != null
                || List.of("shipping", "done", "completed").contains(String.valueOf(status));
        boolean received = "done".equals(status) || "completed".equals(status);
        boolean closed = "cancelled".equals(status) || "closed".equals(status);
        boolean refundFlow = "refunding".equals(status) || "refunded".equals(status);

        List<Map<String, Object>> steps = new ArrayList<>();
        // 1) 下单（必然完成）
        steps.add(step("已提交订单", created, true, "您的订单已提交，等待商家确认"));
        // 2) 付款：优先用真实 paid_at；缺失时按「下单 +30min」推算
        LocalDateTime paidTime = paidDone ? (paid != null ? paid : shift(created, 1000L * 60 * 30)) : null;
        steps.add(step("商家已接单并付款", paidTime,
                paidDone, paidDone ? "支付成功，商家正在备货" : "等待付款"));
        // 3) 发货：必须晚于付款时间，避免「发货早于付款」的时间倒挂
        LocalDateTime shipTime = shipped ? shift(paidTime != null ? paidTime : created, 1000L * 60 * 60 * 8) : null;
        steps.add(step("商品已发货", shipTime,
                shipped, shipped ? "包裹已交由 PetMate 合作仓配" : "商家备货中"));
        // 4) 签收：优先真实 delivered_at，且不早于发货时间
        LocalDateTime recvTime = null;
        if (received) {
            recvTime = delivered != null ? delivered : shift(shipTime != null ? shipTime : created, 1000L * 60 * 60 * 40);
            if (shipTime != null && recvTime.isBefore(shipTime)) recvTime = shift(shipTime, 1000L * 60 * 60 * 40);
        }
        steps.add(step("已签收", recvTime,
                received, received ? "感谢您的信赖，期待下次相遇" : "运输中"));

        // 5) 终态补充节点：退款流程 / 订单关闭，让状态机与物流轨迹对齐
        LocalDateTime lastTime = recvTime != null ? recvTime
                : (shipTime != null ? shipTime : (paidTime != null ? paidTime : created));
        if (refundFlow) {
            boolean refunded = "refunded".equals(status);
            steps.add(step(refunded ? "退款已完成" : "退款处理中",
                    shift(lastTime, 1000L * 60 * 60 * 2), refunded,
                    refunded ? "退款金额已原路返回" : "售后申请已提交，等待处理"));
        } else if (closed) {
            steps.add(step("订单已关闭", shift(lastTime, 1000L * 60 * 30), true,
                    "订单已取消或超时关闭"));
        }

        // 倒序返回(最新在上)
        Collections.reverse(steps);
        return ApiResp.ok(steps);
    }

    private Map<String, Object> step(String title, LocalDateTime time, boolean done, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("time", time == null ? "" : time.toString().replace("T", " "));
        m.put("done", done);
        m.put("desc", desc);
        return m;
    }

    private LocalDateTime shift(LocalDateTime base, long ms) {
        if (base == null) return LocalDateTime.now();
        return base.plus(Duration.ofMillis(ms));
    }

    /**
     * 计算优惠券抵扣金额；不满足门槛 / 券不存在 / 未领取则返 0。
     */
    private BigDecimal computeCouponDiscount(long couponId, long uid, BigDecimal total) {
        List<Map<String, Object>> c = jdbc.queryForList(
            "SELECT c.type, c.threshold, c.amount, c.discount, c.is_deleted, c.valid_end " +
            "FROM t_coupon c WHERE c.id = ?", couponId);
        if (c.isEmpty()) return BigDecimal.ZERO;
        Map<String, Object> row = c.get(0);
        if (((Number) row.get("is_deleted")).intValue() == 1) return BigDecimal.ZERO;
        if (row.get("valid_end") != null
                && ((LocalDateTime) row.get("valid_end")).isBefore(LocalDateTime.now()))
            return BigDecimal.ZERO;
        // 用户须已领取该券
        Integer owned = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_user_coupon WHERE user_id = ? AND coupon_id = ? AND status != 'used'",
            Integer.class, uid, couponId);
        if (owned == null || owned == 0) return BigDecimal.ZERO;
        // 门槛校验
        BigDecimal threshold = row.get("threshold") == null ? BigDecimal.ZERO : (BigDecimal) row.get("threshold");
        if (total.compareTo(threshold) < 0) return BigDecimal.ZERO;

        String type = (String) row.get("type");
        BigDecimal discount;
        if ("discount".equals(type)) {
            BigDecimal d = row.get("discount") == null ? BigDecimal.ONE : (BigDecimal) row.get("discount");
            // discount=0.9 表示 9 折,省 10%
            discount = total.multiply(BigDecimal.ONE.subtract(d)).setScale(2, java.math.RoundingMode.HALF_UP);
        } else { // reduction 或默认: 满减 amount
            BigDecimal amount = row.get("amount") == null ? BigDecimal.ZERO : (BigDecimal) row.get("amount");
            discount = amount.min(total);
        }
        return discount;
    }

    // ================= 内部工具 =================

    /**
     * 回退下单时消耗的资产：优惠券（置回 available）+ 鱼干（调用 PointsService.refund）。
     * 在取消 / 超时 / 退款完成等订单作罢场景下调用，避免资产凭空蒸发。
     *
     * 注意：t_user_coupon.status 的合法取值只有 available / used / expired
     * （UserController 再把 available 对外映射成 claimed 供前端展示）。
     * 历史实现回退时写入了不存在的 'unused'，结果券既不在「可用」也不在「已用」列表里，
     * 用户取消订单后优惠券直接人间蒸发 —— 这是一处真实的数据损坏，务必写 available。
     */
    private void rollbackOrderAssets(String orderNo, long orderId, long uid, Object couponId, int pointsUsed) {
        if (couponId != null) {
            jdbc.update(
                "UPDATE t_user_coupon SET status = 'available', used_at = NULL " +
                "WHERE user_id = ? AND coupon_id = ? AND status = 'used'",
                uid, ((Number) couponId).longValue());
        }
        if (pointsUsed > 0) {
            pointsService.refund(uid, pointsUsed, "订单作罢退回", orderId);
        }
    }

    private Map<String, Object> buildOrderDetail(String orderNo, long uid) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, order_no, user_id, total_amount, pay_amount, freight_amount, status, receiver_snapshot, coupon_amount, coupon_id, donate_amount, created_at, expire_at " +
            "FROM t_order WHERE order_no = ? AND user_id = ? AND is_deleted = 0", orderNo, uid);
        if (rows.isEmpty()) return null;
        Map<String, Object> detail = normalizeOrder(rows.get(0));
        // 挂上该订单最近一张售后单，前端据此把「申请退款」切换成「查看售后进度」
        attachAftersaleSummary(detail);
        return detail;
    }

    private Map<String, Object> normalizeOrder(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("orderNo", r.get("order_no"));
        m.put("userId", r.get("user_id"));
        m.put("totalAmount", r.get("total_amount"));
        m.put("payAmount", r.get("pay_amount"));
        m.put("freightAmount", r.get("freight_amount"));
        m.put("couponAmount", r.get("coupon_amount"));
        m.put("couponId", r.get("coupon_id"));
        m.put("donateAmount", r.get("donate_amount"));
        m.put("status", r.get("status"));
        if (r.get("receiver_snapshot") != null) m.put("receiverSnapshot", r.get("receiver_snapshot").toString());
        m.put("createdAt", r.get("created_at") == null ? null : r.get("created_at").toString());
        m.put("expireAt", r.get("expire_at") == null ? null : r.get("expire_at").toString());
        // items
        long orderId = ((Number) r.get("id")).longValue();
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT id, product_id, sku_id, product_name, product_image, price, quantity FROM t_order_item WHERE order_id = ?", orderId);
        List<Map<String, Object>> itemOut = new ArrayList<>();
        for (Map<String, Object> it : items) {
            Map<String, Object> i = new LinkedHashMap<>();
            i.put("id", it.get("id"));
            i.put("productId", it.get("product_id"));
            i.put("skuId", it.get("sku_id"));
            i.put("productName", it.get("product_name"));
            i.put("productImage", it.get("product_image"));
            i.put("price", it.get("price"));
            i.put("quantity", it.get("quantity"));
            itemOut.add(i);
        }
        m.put("items", itemOut);
        return m;
    }
}
