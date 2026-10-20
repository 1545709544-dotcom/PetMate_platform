package com.petmate.admin;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.PageResp;
import com.petmate.community.SensitiveWordService;
import com.petmate.security.TokenContext;
import com.petmate.track.MetricService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class AdminController {

    private final JdbcTemplate jdbc;
    private final SensitiveWordService sensitive;
    private final MetricService metricService;
    private final com.petmate.refund.RefundLogService refundLog;

    public AdminController(JdbcTemplate jdbc, SensitiveWordService sensitive, MetricService metricService,
                           com.petmate.refund.RefundLogService refundLog) {
        this.jdbc = jdbc;
        this.sensitive = sensitive;
        this.metricService = metricService;
        this.refundLog = refundLog;
    }

    /** 当前操作人昵称（用于 t_operation_log.operator_name）。读不到则退化为 ID 串，保证审计可追溯。 */
    private String currentOperatorName() {
        long uid = TokenContext.get().userId;
        try {
            String n = jdbc.queryForObject("SELECT nickname FROM t_user WHERE id = ?", String.class, uid);
            return n == null ? ("#" + uid) : n;
        } catch (Exception e) {
            return "#" + uid;
        }
    }

    @GetMapping("/admin/dashboard")
    public ApiResp<Map<String, Object>> overview() {
        adminOnly();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("totalUsers",       cnt("SELECT COUNT(*) FROM t_user WHERE is_deleted = 0"));
        r.put("totalMerchants",   cnt("SELECT COUNT(DISTINCT merchant_id) FROM t_store WHERE is_deleted = 0"));
        // v7: 商家管理 KPI 细分(运营状态)
        r.put("activeMerchants",  cnt("SELECT COUNT(DISTINCT merchant_id) FROM t_store WHERE is_deleted = 0 AND status = 0"));
        r.put("bannedMerchants",  cnt("SELECT COUNT(DISTINCT merchant_id) FROM t_store WHERE is_deleted = 0 AND status = 1"));
        r.put("totalOrders",      cnt("SELECT COUNT(*) FROM t_order WHERE is_deleted = 0"));
        r.put("totalSales",       sumPay("SELECT COALESCE(SUM(pay_amount),0) FROM t_order WHERE is_deleted = 0 AND status IN ('paid','done')"));
        r.put("pendingAudits",    cnt("SELECT COUNT(*) FROM t_post WHERE audit_status = 'pending' AND is_deleted = 0"));
        r.put("todayActiveUsers", cnt("SELECT COUNT(DISTINCT user_id) FROM t_order WHERE DATE(created_at) = CURDATE()"));
        // 今日销售额
        r.put("todaySales",       sumPay("SELECT COALESCE(SUM(pay_amount),0) FROM t_order WHERE is_deleted = 0 AND DATE(paid_at) = CURDATE() AND status IN ('paid','done')"));
        // 今日新增订单数（用 todayOrders 命名,跟商家 dashboard 对齐）
        r.put("todayOrders",      cnt("SELECT COUNT(*) FROM t_order WHERE is_deleted = 0 AND DATE(created_at) = CURDATE()"));
        // 客单价 = 总销售额 / 总订单数(实付)
        // cnt() 返回 Long, sumPay() 返回 Double; r.get(...) 拿出来的就是 Long,不能强转 Integer
        Double ts = (Double) r.get("totalSales");
        Long toL = (Long) r.get("totalOrders");
        long to = toL == null ? 0L : toL;
        r.put("avgOrderAmount", to > 0 ? Math.round((ts == null ? 0 : ts) * 100.0 / to) / 100.0 : 0.0);
        // 订单转化率: paid+done 订单 / 总非取消订单(无埋点,用订单支付率近似)
        Long paidCnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_order WHERE is_deleted = 0 AND status IN ('paid','done','shipping')", Long.class);
        long pc = paidCnt == null ? 0L : paidCnt;
        r.put("conversionRate", to > 0
            ? Math.round(pc * 10000.0 / to) / 10000.0 : 0.0);
        // 库存预警: t_product_sku.stock <= 5 的 SPU 数
        r.put("stockAlerts", cnt(
            "SELECT COUNT(DISTINCT product_id) FROM t_product_sku WHERE stock > 0 AND stock <= 5"));
        // 待处理工单: 用"待退款 + 待审核帖子"做兜底(无独立工单表)
        Long pendingRefund = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_order WHERE status = 'refunding' AND is_deleted = 0", Long.class);
        Long pendingPost = (Long) r.get("pendingAudits");
        r.put("pendingTickets", (pendingRefund == null ? 0L : pendingRefund) + (pendingPost == null ? 0L : pendingPost));
        // 客户好评率: 真实评价落在 t_product_review（t_review 为历史死表，已弃用）
        Long totalReview = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product_review WHERE is_deleted = 0", Long.class);
        Long goodReview = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_product_review WHERE is_deleted = 0 AND rating >= 4", Long.class);
        long tr = totalReview == null ? 0L : totalReview;
        long gr = goodReview == null ? 0L : goodReview;
        r.put("positiveRate", tr > 0
            ? Math.round(gr * 10000.0 / tr) / 10000.0 : 0.0);
        // 用户管理页 KPI 所需统计（v5 完善）
        r.put("todayNewUsers", cnt("SELECT COUNT(*) FROM t_user WHERE is_deleted = 0 AND DATE(created_at) = CURDATE()"));
        r.put("vipCount", cnt("SELECT COUNT(*) FROM t_user WHERE is_deleted = 0 AND vip_level > 0"));
        r.put("abnormalCount", cnt("SELECT COUNT(*) FROM t_user WHERE is_deleted = 0 AND status = 1"));
        return ApiResp.ok(r);
    }

    /**
     * 客流趋势（近 7 天）：按 t_order.created_at 聚合每日订单数(代理访客),
     * 与商家端不同:这里聚合所有商家订单。
     */
    @GetMapping("/admin/dashboard/visitor-trend")
    public ApiResp<List<Map<String, Object>>> visitorTrend(
            @RequestParam(defaultValue = "7d") String range) {
        adminOnly();
        int days = "30d".equals(range) ? 30 : "90d".equals(range) ? 90 : 7;
        java.time.LocalDate today = java.time.LocalDate.now();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS date, " +
            "COUNT(*) AS orderCount, COUNT(DISTINCT user_id) AS visitorCount " +
            "FROM t_order WHERE is_deleted = 0 " +
            "  AND created_at >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
            "GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d') ORDER BY date", days - 1);
        // 补齐空日期(0 占位)
        java.util.Map<String, Map<String, Object>> byDate = new java.util.HashMap<>();
        for (Map<String, Object> r : rows) byDate.put(String.valueOf(r.get("date")), r);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String d = today.minusDays(i).toString();
            Map<String, Object> e = byDate.get(d);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", d);
            m.put("orderCount", e == null ? 0 : ((Number) e.get("orderCount")).intValue());
            m.put("visitorCount", e == null ? 0 : ((Number) e.get("visitorCount")).intValue());
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    /**
     * 销售品类分布:按 t_product.category_id 分组聚合(实付订单的 order_item)
     */
    @GetMapping("/admin/dashboard/category-share")
    public ApiResp<List<Map<String, Object>>> categoryShare() {
        adminOnly();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT c.id, c.name, COALESCE(SUM(oi.price * oi.quantity),0) AS amount, " +
            "COUNT(DISTINCT o.id) AS orderCount " +
            "FROM t_product_category c " +
            "LEFT JOIN t_product p ON p.category_id = c.id AND p.is_deleted = 0 " +
            "LEFT JOIN t_order_item oi ON oi.product_id = p.id " +
            "LEFT JOIN t_order o ON o.id = oi.order_id AND o.is_deleted = 0 " +
            "  AND o.status IN ('paid','done','shipping') " +
            "WHERE c.parent_id = 0 " +
            "GROUP BY c.id, c.name ORDER BY amount DESC LIMIT 8");
        return ApiResp.ok(rows);
    }

    /**
     * 转化漏斗（管理员视角，跨商家）。
     * v2.0: 走 MetricService，按 petmate.metrics.real 切换真读 t_event / 旧估算。
     */
    @GetMapping("/admin/dashboard/funnel")
    public ApiResp<Map<String, Object>> funnel() {
        adminOnly();
        MetricService.FunnelStats fs = metricService.funnel(null, 30);
        return ApiResp.ok(fs.toMap());
    }

    /** 热销商品 top N（按销量） */
    @GetMapping("/admin/dashboard/hot-products")
    public ApiResp<List<Map<String, Object>>> hotProducts(
            @RequestParam(defaultValue = "10") int limit) {
        adminOnly();
        if (limit <= 0 || limit > 100) limit = 10;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, name, category_id, main_image, sale_price, original_price, sale_count, rating, status " +
            "FROM t_product WHERE is_deleted = 0 ORDER BY sale_count DESC, view_count DESC LIMIT ?", limit);
        return ApiResp.ok(rows);
    }

    @GetMapping("/admin/user/list")
    public ApiResp<PageResp<Map<String, Object>>> userList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer vip,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        adminOnly();
        StringBuilder where = new StringBuilder("WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (status != null && status >= 0) { where.append(" AND status = ?"); args.add(status); }
        if (vip != null && vip >= 0) { where.append(" AND vip_level = ?"); args.add(vip); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (nickname LIKE ? OR phone LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (dateFrom != null && !dateFrom.isBlank()) { where.append(" AND DATE(created_at) >= ?"); args.add(dateFrom); }
        if (dateTo != null && !dateTo.isBlank()) { where.append(" AND DATE(created_at) <= ?"); args.add(dateTo); }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_user " + where, args.toArray(), Long.class);
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, nickname, avatar, phone, vip_level AS vipLevel, status, " +
            "DATE_FORMAT(created_at, '%Y-%m-%d') AS registerDate, last_login_at AS lastLoginAt, " +
            "vip_expire_at AS vipExpireAt " +
            "FROM t_user " + where + " ORDER BY id DESC LIMIT ? OFFSET ?", pageArgs.toArray());
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, size));
    }

    @PostMapping("/admin/user/{id}/ban")
    public ApiResp<Void> banUser(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        boolean banned = Boolean.TRUE.equals(body.get("banned"));
        jdbc.update("UPDATE t_user SET status = ? WHERE id = ?", banned ? 1 : 0, id);
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, ?, ?, 'user', ?, ?, '')",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role,
                banned ? "BAN_USER" : "UNBAN_USER", String.valueOf(id));
        return ApiResp.ok();
    }

    // ===== 用户编辑 / 新增 / 通知（v5 完善） =====
    @PutMapping("/admin/user/{id}")
    public ApiResp<Void> editUser(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        String nickname = optStr(body, "nickname");
        String phone = optStr(body, "phone");
        Integer vipLevel = optInt(body, "vipLevel");
        Integer status = optInt(body, "status");
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (nickname != null && !nickname.isBlank()) { sets.add("nickname = ?"); args.add(nickname); }
        if (phone != null && !phone.isBlank()) { sets.add("phone = ?"); args.add(phone); }
        if (vipLevel != null) {
            // 仅当 VIP 等级真变化才动 vip_expire_at。修复：之前无脑 +1 年导致编辑昵称时偷偷续费。
            Integer curLevel = jdbc.queryForObject("SELECT vip_level FROM t_user WHERE id = ?", Integer.class, id);
            if (curLevel == null) curLevel = 0;
            if (vipLevel != curLevel) {
                sets.add("vip_level = ?");
                args.add(vipLevel);
                if (vipLevel > 0) {
                    sets.add("vip_expire_at = ?");
                    args.add(Timestamp.valueOf(LocalDateTime.now().plusYears(1)));
                } else {
                    sets.add("vip_expire_at = NULL");
                }
            }
        }
        if (status != null) { sets.add("status = ?"); args.add(status); }
        if (!sets.isEmpty()) {
            args.add(id);
            jdbc.update("UPDATE t_user SET " + String.join(", ", sets) + " WHERE id = ?", args.toArray());
        }
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, ?, ?, 'user', 'EDIT_USER', ?, '')",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role, String.valueOf(id));
        return ApiResp.ok();
    }

    @PostMapping("/admin/user/create")
    public ApiResp<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        adminOnly();
        String nickname = optStr(body, "nickname");
        String phone = optStr(body, "phone");
        String password = optStr(body, "password");
        Integer vipLevel = optInt(body, "vipLevel");
        if (nickname == null || nickname.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "昵称必填");
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        Long dup = jdbc.queryForObject("SELECT COUNT(*) FROM t_user WHERE phone = ?", Long.class, phone);
        if (dup != null && dup > 0) throw new BizException(ErrorCode.PARAM_ERROR, "手机号已存在");
        if (password == null || password.length() < 6) password = "123456";
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(10));
        Long maxId = jdbc.queryForObject("SELECT COALESCE(MAX(id), 10000) FROM t_user", Long.class);
        long newId = (maxId == null ? 10000 : maxId) + 1;
        int lvl = vipLevel == null ? 0 : vipLevel;
        // VIP 用户必须写入有效期，否则一出生即过期（无法享受权益）
        Timestamp vipExpire = lvl > 0 ? Timestamp.valueOf(LocalDateTime.now().plusYears(1)) : null;
        jdbc.update("INSERT INTO t_user (id, nickname, phone, password, vip_level, vip_expire_at, status, created_at, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, 0, NOW(), 0)",
                newId, nickname, phone, hash, lvl, vipExpire);
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, ?, ?, 'user', 'CREATE_USER', ?, '')",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role, String.valueOf(newId));
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", newId);
        r.put("nickname", nickname);
        r.put("phone", phone);
        return ApiResp.ok(r);
    }

    @PostMapping("/admin/notify")
    public ApiResp<Void> notify(@RequestBody Map<String, Object> body) {
        adminOnly();
        String title = optStr(body, "title");
        String content = optStr(body, "content");
        String target = optStr(body, "target");
        if (title == null) title = "系统通知";
        if (content == null) content = "";
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, ?, ?, 'notify', 'PUSH_NOTIFY', ?, ?)",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role,
                target == null ? "all" : target, title + "|" + content);
        return ApiResp.ok();
    }

    private String optStr(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : String.valueOf(v).trim();
    }

    private Integer optInt(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v instanceof Number) return ((Number) v).intValue();
        if (v instanceof String) {
            try { return Integer.parseInt(((String) v).trim()); } catch (Exception e) { return null; }
        }
        return null;
    }

    /** 申请列表（按 status 过滤，可选分页） */
    @GetMapping("/admin/merchant/apply/list")
    public ApiResp<PageResp<Map<String, Object>>> applyList(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminOnly();
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        StringBuilder where = new StringBuilder("WHERE 1=1");
        Object[] args;
        if (status != null) {
            where.append(" AND status = ?");
            args = new Object[]{status};
        } else {
            args = new Object[]{};
        }
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_merchant_apply " + where, Long.class, args);

        Object[] paged = Arrays.copyOf(args, args.length + 2);
        paged[args.length] = size;
        paged[args.length + 1] = (page - 1) * size;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, phone, owner_name AS ownerName, store_name AS storeName, " +
            "type, address, license_no AS licenseNo, status, submit_at AS submitAt, " +
            "reviewed_at AS reviewedAt, review_note AS reviewNote, reviewer_id AS reviewerId, " +
            "subject_type AS subjectType, enterprise_name AS enterpriseName, " +
            "store_phone AS storePhone, unified_credit_code AS unifiedCreditCode, " +
            "business_license_url AS businessLicenseUrl, id_card AS idCard, " +
            "id_card_front_url AS idCardFrontUrl, id_card_back_url AS idCardBackUrl, " +
            "business_hours AS businessHours, photos, lng, lat " +
            "FROM t_merchant_apply " + where + " ORDER BY id DESC LIMIT ? OFFSET ?", paged);

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("phone", r.get("phone"));
            m.put("ownerName", r.get("ownerName"));
            m.put("storeName", r.get("storeName"));
            m.put("type", r.get("type"));
            m.put("address", r.get("address"));
            m.put("licenseNo", r.get("licenseNo"));
            m.put("status", r.get("status"));
            m.put("submitAt", r.get("submitAt") == null ? null : r.get("submitAt").toString());
            m.put("reviewedAt", r.get("reviewedAt") == null ? null : r.get("reviewedAt").toString());
            m.put("reviewNote", r.get("reviewNote"));
            m.put("reviewerId", r.get("reviewerId"));
            // 详情字段（v5 审核页直接渲染，无需二次请求）
            m.put("subjectType", r.get("subjectType"));
            m.put("enterpriseName", r.get("enterpriseName"));
            m.put("storePhone", r.get("storePhone"));
            m.put("unifiedCreditCode", r.get("unifiedCreditCode"));
            m.put("businessLicenseUrl", r.get("businessLicenseUrl"));
            m.put("idCard", r.get("idCard"));
            m.put("idCardFrontUrl", r.get("idCardFrontUrl"));
            m.put("idCardBackUrl", r.get("idCardBackUrl"));
            m.put("businessHours", r.get("businessHours"));
            m.put("photos", r.get("photos"));
            m.put("lng", r.get("lng"));
            m.put("lat", r.get("lat"));
            out.add(m);
        }
        return ApiResp.ok(PageResp.of(out, total == null ? 0 : total, page, size));
    }

    /**
     * 审核入驻申请。
     * body: { applyId, approved, reason? }
     * 通过：分配 merchant id（20000+），写入 t_user + t_store(audit_status=1)，更新申请单。
     * 驳回：仅更新申请单 status=2 + review_note。
     */
    @PostMapping("/admin/merchant/apply/audit")
    @Transactional
    public ApiResp<Map<String, Object>> auditApply(@RequestBody Map<String, Object> body) {
        adminOnly();
        long applyId = ((Number) body.get("applyId")).longValue();
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String reason = body.get("reason") == null ? null : String.valueOf(body.get("reason"));

        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, phone, password_hash, owner_name, store_name, type, address, " +
            "lng, lat, business_hours, photos, status FROM t_merchant_apply WHERE id = ?", applyId);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "申请单不存在");
        Map<String, Object> apply = rows.get(0);
        int curStatus = ((Number) apply.get("status")).intValue();
        if (curStatus != 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该申请已处理,当前状态：" + statusLabel(curStatus));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("applyId", applyId);
        out.put("approved", approved);

        if (!approved) {
            // 驳回：仅写状态
            jdbc.update(
                "UPDATE t_merchant_apply SET status = 2, reviewed_at = NOW(), review_note = ?, reviewer_id = ? " +
                "WHERE id = ? AND status = 0",
                reason, TokenContext.get().userId, applyId);
            return ApiResp.ok(out);
        }

        // 通过：分配 merchant id
        Long newId = jdbc.queryForObject(
            "SELECT IFNULL(MAX(id), 19999) + 1 FROM t_user WHERE id BETWEEN 20000 AND 89999", Long.class);
        if (newId == null) newId = 20000L;
        String phone = (String) apply.get("phone");
        String passwordHash = (String) apply.get("password_hash");
        String ownerName = (String) apply.get("owner_name");
        String storeName = (String) apply.get("store_name");
        String type = (String) apply.get("type");
        String address = (String) apply.get("address");

        // 防重入
        Integer dup = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_user WHERE phone = ? AND is_deleted = 0", Integer.class, phone);
        if (dup != null && dup > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该手机号已注册,审核拒绝");
        }

        // 1) 写 t_user
        jdbc.update(
            "INSERT INTO t_user (id, phone, password, nickname, status) VALUES (?, ?, ?, ?, 1)",
            newId, phone, passwordHash, ownerName);
        // 2) 写 t_store (audit_status=1 已通过)
        jdbc.update(
            "INSERT INTO t_store (merchant_id, name, type, address, lng, lat, business_hours, photos, audit_status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1)",
            newId, storeName, type, address,
            apply.get("lng"), apply.get("lat"),
            apply.get("business_hours"), apply.get("photos"));
        // 3) 更新申请单
        jdbc.update(
            "UPDATE t_merchant_apply SET status = 1, reviewed_at = NOW(), reviewer_id = ? " +
            "WHERE id = ? AND status = 0",
            TokenContext.get().userId, applyId);

        // 4) 操作日志
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, ?, ?, 'merchant', ?, ?, '')",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role, "APPLY_PASS", String.valueOf(applyId));

        out.put("merchantId", newId);
        out.put("storeCreated", true);
        return ApiResp.ok(out);
    }

    private static String statusLabel(int s) {
        return s == 1 ? "已通过" : s == 2 ? "已驳回" : "待审核";
    }

    // ============================================================
    // 商家管理（v7 完善）
    //  - 已入驻商家列表 / 详情 / 启停 / 强制解除
    //  - 数据源：t_store(主) JOIN t_user(店主) LEFT JOIN t_merchant_apply(资质)
    // ============================================================

    /**
     * 已入驻商家列表（运营视角）
     * 数据模型：
     *   t_store.merchant_id = 店主 t_user.id
     *   t_merchant_apply 唯一(uk_phone)，用 u.phone 关联取资质
     */
    @GetMapping("/admin/merchant/list")
    public ApiResp<PageResp<Map<String, Object>>> merchantList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo) {
        adminOnly();
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;

        StringBuilder where = new StringBuilder("WHERE s.is_deleted = 0");
        List<Object> args = new ArrayList<>();
        // 0=营业 1=封禁 2=注销；不传=全部
        if (status != null && status >= 0) { where.append(" AND s.status = ?"); args.add(status); }
        if (type != null && !type.isBlank()) { where.append(" AND s.type = ?"); args.add(type); }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.name LIKE ? OR u.nickname LIKE ? OR u.phone LIKE ?)");
            String k = "%" + keyword + "%";
            args.add(k); args.add(k); args.add(k);
        }
        if (dateFrom != null && !dateFrom.isBlank()) { where.append(" AND DATE(s.created_at) >= ?"); args.add(dateFrom); }
        if (dateTo != null && !dateTo.isBlank())   { where.append(" AND DATE(s.created_at) <= ?"); args.add(dateTo); }

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_store s JOIN t_user u ON s.merchant_id = u.id " + where,
            Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT s.id, s.merchant_id AS merchantId, s.name, s.type, s.address, s.status, s.audit_status AS auditStatus, " +
            "       s.created_at AS createdAt, s.updated_at AS updatedAt, " +
            "       a.submit_at AS submitAt, a.reviewed_at AS reviewedAt, " +
            "       u.id AS ownerUserId, u.nickname AS ownerName, u.phone AS ownerPhone, u.status AS ownerUserStatus " +
            "FROM t_store s " +
            "JOIN t_user u ON s.merchant_id = u.id " +
            "LEFT JOIN t_merchant_apply a ON a.phone = u.phone AND a.status = 1 " + where +
            " ORDER BY s.id DESC LIMIT ? OFFSET ?", pageArgs.toArray());

        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("name", r.get("name"));
            m.put("type", r.get("type"));
            m.put("address", r.get("address"));
            m.put("status", r.get("status"));
            m.put("auditStatus", r.get("auditStatus"));
            m.put("ownerUserId", r.get("ownerUserId"));
            m.put("ownerName", r.get("ownerName"));
            m.put("ownerPhone", r.get("ownerPhone"));
            // submitDate = 入驻申请提交时间（如有）；否则退化为店铺创建时间（演示种子无申请记录的店）
            m.put("submitDate", r.get("submitAt") == null
                ? (r.get("createdAt") == null ? null : r.get("createdAt").toString())
                : r.get("submitAt").toString());
            // approvedAt = 入驻申请审核通过时间（如有）；否则退化为店铺创建时间
            m.put("approvedAt", r.get("reviewedAt") == null
                ? (r.get("createdAt") == null ? null : r.get("createdAt").toString())
                : r.get("reviewedAt").toString());
            out.add(m);
        }
        return ApiResp.ok(PageResp.of(out, total == null ? 0 : total, page, size));
    }

    /**
     * 商家详情（运营视角）
     * 复用 t_merchant_apply 的资质/营业时间/图片（一个手机号对应一份通过的申请）
     */
    @GetMapping("/admin/merchant/{id}")
    public ApiResp<Map<String, Object>> merchantDetail(@PathVariable long id) {
        adminOnly();
        // 入参是「店铺 id」。用 queryForList 而非 queryForMap：
        // queryForMap 在无结果时抛 EmptyResultDataAccessException，会被全局兜底成 10500「系统异常」，
        // 管理员从举报/申请等入口带着 merchant_id（而非 store_id）跳进来时页面直接白屏且无法定位原因。
        List<Map<String, Object>> storeRows = jdbc.queryForList(
            "SELECT s.id, s.merchant_id AS merchantId, s.name, s.type, s.address, " +
            "       s.lng, s.lat, s.business_hours AS businessHours, s.photos, s.audit_status AS auditStatus, " +
            "       s.status, s.created_at AS createdAt, s.updated_at AS updatedAt " +
            "FROM t_store s WHERE s.id = ? AND s.is_deleted = 0", id);
        if (storeRows.isEmpty()) {
            // 兼容：调用方误传店主 user_id，按 merchant_id 再兜一次，取其名下最新店铺
            storeRows = jdbc.queryForList(
                "SELECT s.id, s.merchant_id AS merchantId, s.name, s.type, s.address, " +
                "       s.lng, s.lat, s.business_hours AS businessHours, s.photos, s.audit_status AS auditStatus, " +
                "       s.status, s.created_at AS createdAt, s.updated_at AS updatedAt " +
                "FROM t_store s WHERE s.merchant_id = ? AND s.is_deleted = 0 ORDER BY s.id DESC LIMIT 1", id);
        }
        if (storeRows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "商家不存在或已删除");
        Map<String, Object> s = storeRows.get(0);
        id = ((Number) s.get("id")).longValue();

        long merchantId = ((Number) s.get("merchantId")).longValue();
        List<Map<String, Object>> ownerRows = jdbc.queryForList(
            "SELECT id, nickname, phone, status FROM t_user WHERE id = ? AND is_deleted = 0", merchantId);
        Map<String, Object> owner = ownerRows.isEmpty() ? new LinkedHashMap<>() : ownerRows.get(0);
        Map<String, Object> r = new LinkedHashMap<>(s);
        r.put("ownerUserId", owner.getOrDefault("id", merchantId));
        r.put("ownerName", owner.getOrDefault("nickname", "（店主账号已注销）"));
        r.put("ownerPhone", owner.get("phone"));
        r.put("ownerUserStatus", owner.get("status"));

        // 关联通过的申请（用于展示资质/营业时间/营业执照/身份证）
        String phone = (String) owner.get("phone");
        if (phone != null) {
            List<Map<String, Object>> applyRows = jdbc.queryForList(
                "SELECT id, owner_name, store_name, type, address, lng, lat, business_hours, photos, " +
                "       license_no, subject_type, enterprise_name, unified_credit_code, store_phone, " +
                "       business_license_url, id_card, id_card_front_url, id_card_back_url, " +
                "       submit_at, reviewed_at, review_note, reviewer_id, status " +
                "FROM t_merchant_apply WHERE phone = ? AND status = 1 ORDER BY id DESC LIMIT 1", phone);
            if (!applyRows.isEmpty()) {
                Map<String, Object> a = applyRows.get(0);
                r.put("subjectType", a.get("subject_type"));
                r.put("enterpriseName", a.get("enterprise_name"));
                r.put("storePhone", a.get("store_phone"));
                r.put("unifiedCreditCode", a.get("unified_credit_code"));
                r.put("licenseNo", a.get("license_no"));
                r.put("businessLicenseUrl", a.get("business_license_url"));
                r.put("idCard", a.get("id_card"));
                r.put("idCardFrontUrl", a.get("id_card_front_url"));
                r.put("idCardBackUrl", a.get("id_card_back_url"));
                r.put("applyReviewedAt", a.get("reviewed_at"));
                r.put("reviewNote", a.get("review_note"));
                r.put("reviewerId", a.get("reviewer_id"));
            }
        }

        // 业务数据统计
        long productCount = cnt("SELECT COUNT(*) FROM t_product WHERE merchant_id = ? AND is_deleted = 0", merchantId);
        long orderCount = cnt("SELECT COUNT(DISTINCT oi.order_id) FROM t_order_item oi " +
                         "JOIN t_product p ON oi.product_id = p.id " +
                         "JOIN t_order o ON o.id = oi.order_id " +
                         "WHERE p.merchant_id = ? AND o.is_deleted = 0", merchantId);
        long serviceCount = cnt("SELECT COUNT(*) FROM t_service_item si JOIN t_store st ON si.store_id = st.id " +
                                "WHERE st.id = ? AND si.is_deleted = 0", id);
        r.put("productCount", productCount);
        r.put("orderCount", orderCount);
        r.put("serviceCount", serviceCount);

        return ApiResp.ok(r);
    }

    /**
     * 启停：status=0 营业中 / 1 封禁
     * 仅修改 t_store.status + 同步店主 t_user.status
     */
    @PostMapping("/admin/merchant/{id}/status")
    @Transactional
    public ApiResp<Void> toggleMerchantStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        Integer newStatus = optInt(body, "status");
        if (newStatus == null || (newStatus != 0 && newStatus != 1)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "status 必须为 0(营业) 或 1(封禁)");
        }
        String reason = optStr(body, "reason");
        if (newStatus == 1 && (reason == null || reason.isBlank())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "封禁原因必填");
        }

        Map<String, Object> s = jdbc.queryForMap(
            "SELECT merchant_id, status FROM t_store WHERE id = ? AND is_deleted = 0", id);
        long merchantId = ((Number) s.get("merchant_id")).longValue();
        int cur = ((Number) s.get("status")).intValue();
        if (cur == newStatus) {
            // 已经是目标状态,幂等返回
            return ApiResp.ok();
        }
        if (cur == 2) {
            throw new BizException(ErrorCode.PARAM_ERROR, "已注销的商家不能启停,请联系技术恢复");
        }

        // 1) t_store.status
        jdbc.update("UPDATE t_store SET status = ? WHERE id = ?", newStatus, id);
        // 2) 店主账号同步:封禁 1 / 解封 0
        jdbc.update("UPDATE t_user SET status = ? WHERE id = ?", newStatus, merchantId);

        // 3) 操作日志
        String action = newStatus == 1 ? "MERCHANT_BAN" : "MERCHANT_UNBAN";
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (?, ?, ?, ?, 'merchant', ?, ?, ?)",
                merchantId, TokenContext.get().userId, currentOperatorName(), TokenContext.get().role,
                action, String.valueOf(id), reason == null ? "" : reason);
        return ApiResp.ok();
    }

    /**
     * 强制解除（严重违规）
     * 事务内: t_store.status=2, 下架该商家商品/服务, 店主 t_user.status=1, 写操作日志
     */
    @PostMapping("/admin/merchant/{id}/force-remove")
    @Transactional
    public ApiResp<Void> forceRemoveMerchant(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        String reason = optStr(body, "reason");
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "解除原因必填");
        }
        Map<String, Object> s = jdbc.queryForMap(
            "SELECT merchant_id, status FROM t_store WHERE id = ? AND is_deleted = 0", id);
        long merchantId = ((Number) s.get("merchant_id")).longValue();

        // 1) 门店注销
        jdbc.update("UPDATE t_store SET status = 2 WHERE id = ?", id);
        // 2) 下架该商家所有商品
        jdbc.update("UPDATE t_product SET status = 0, is_deleted = 1 WHERE merchant_id = ?", merchantId);
        // 3) 下架该门店所有服务
        jdbc.update("UPDATE t_service_item si JOIN t_store st ON si.store_id = st.id " +
                    "SET si.status = 0, si.is_deleted = 1 WHERE st.id = ?", id);
        // 4) 店主账号封禁
        jdbc.update("UPDATE t_user SET status = 1 WHERE id = ?", merchantId);
        // 5) 下属员工也一并封禁(若 t_merchant_staff 有对应账号)
        //    注:员工表 status 与 t_user 解耦,这里仅示意
        // 6) 操作日志
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (?, ?, ?, ?, 'merchant', 'MERCHANT_FORCE_REMOVE', ?, ?)",
                merchantId, TokenContext.get().userId, currentOperatorName(), TokenContext.get().role,
                String.valueOf(id), reason);
        return ApiResp.ok();
    }

    @GetMapping("/admin/post/audit")
    public ApiResp<PageResp<Map<String, Object>>> postAuditList(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminOnly();
        String dbStatus = switch (status) {
            case "approved" -> "approved";
            case "rejected" -> "rejected";
            default -> "pending";
        };
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_post WHERE audit_status = ? AND is_deleted = 0", Long.class, dbStatus);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, content, user_nickname AS userNickname, created_at AS createdAt " +
            "FROM t_post WHERE audit_status = ? AND is_deleted = 0 ORDER BY id DESC LIMIT ? OFFSET ?",
            dbStatus, size, (page - 1) * size);
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, size));
    }

    @PostMapping("/admin/post/audit")
    public ApiResp<Void> auditPost(@RequestBody Map<String, Object> body) {
        adminOnly();
        long postId = ((Number) body.get("postId")).longValue();
        boolean approved = Boolean.TRUE.equals(body.get("approved"));
        String reason = (String) body.get("reason");
        jdbc.update("UPDATE t_post SET audit_status = ?, audit_reason = ? WHERE id = ?",
                approved ? "approved" : "rejected", reason, postId);
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, ?, ?, 'post', ?, ?, '')",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role,
                approved ? "POST_PASS" : "POST_REJECT", String.valueOf(postId));
        return ApiResp.ok();
    }

    /**
     * 操作日志（审计）：返回完整字段（操作人/角色/模块/动作/目标/详情/IP/时间），
     * 支持模块、操作人、关键字、时间区间过滤。前端在"操作日志"页做中文映射与详情抽屉。
     */
    @GetMapping("/admin/log")
    public ApiResp<PageResp<Map<String, Object>>> logs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operator,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String start,
            @RequestParam(required = false) String end) {
        adminOnly();
        if (page < 1) page = 1;
        if (size < 1 || size > 200) size = 50;

        StringBuilder where = new StringBuilder("WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (module != null && !module.isBlank()) {
            where.append(" AND module = ?");
            args.add(module);
        }
        if (operator != null && !operator.isBlank()) {
            // 按操作人昵称或账号(id)模糊匹配
            where.append(" AND (operator_name LIKE ? OR CAST(operator_id AS CHAR) LIKE ?)");
            args.add("%" + operator + "%");
            args.add("%" + operator + "%");
        }
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (action LIKE ? OR target LIKE ? OR detail LIKE ?)");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
            args.add("%" + keyword + "%");
        }
        if (start != null && !start.isBlank()) { where.append(" AND DATE(created_at) >= ?"); args.add(start); }
        if (end != null && !end.isBlank())     { where.append(" AND DATE(created_at) <= ?"); args.add(end); }

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_operation_log " + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, operator_id AS operatorId, operator_name AS operatorName, role, module, action, " +
            "target, detail, ip, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt " +
            "FROM t_operation_log " + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            pageArgs.toArray());
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, size));
    }

    // ============ 敏感词管理（DFA，支持分类/级别/处置/启停/统计） ============
    @GetMapping("/admin/sensitive")
    public ApiResp<PageResp> sensitiveList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        adminOnly();
        int limit = Math.min(Math.max(size, 1), 500);
        int offset = (Math.max(page, 1) - 1) * limit;
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (word LIKE ? OR category LIKE ?)");
            params.add("%" + keyword.trim() + "%");
            params.add("%" + keyword.trim() + "%");
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            params.add(category.trim());
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM t_sensitive_word" + where, params.toArray(), Integer.class);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(limit);
        pageParams.add(offset);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, word, category, level, action, enabled, hit_count AS hitCount, " +
                "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS createdAt FROM t_sensitive_word" + where +
                " ORDER BY id DESC LIMIT ? OFFSET ?",
                pageParams.toArray());
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, limit));
    }

    @PostMapping("/admin/sensitive")
    public ApiResp<Map<String, Object>> addSensitive(@RequestBody Map<String, Object> body) {
        adminOnly();
        String category = body.get("category") == null ? "其他" : (String) body.get("category");
        int level = body.get("level") == null ? 1 : ((Number) body.get("level")).intValue();
        String action = body.get("action") == null ? "block" : (String) body.get("action");
        List<String> words = new ArrayList<>();
        if (body.get("words") instanceof List) {
            for (Object o : (List<?>) body.get("words")) words.add(o.toString().trim());
        } else if (body.get("word") != null) {
            words.add(((String) body.get("word")).trim());
        }
        int added = 0, skipped = 0;
        List<String> addedWords = new ArrayList<>();
        for (String w : words) {
            if (w.isEmpty()) { skipped++; continue; }
            try {
                jdbc.update("INSERT INTO t_sensitive_word (word, category, level, action) VALUES (?, ?, ?, ?)", w, category, level, action);
                added++;
                addedWords.add(w);
            } catch (Exception ignore) { skipped++; }   // 重复词跳过
        }
        sensitive.refresh();
        if (added > 0) {
            jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                            "VALUES (0, ?, ?, ?, 'sensitive', 'ADD_SENSITIVE', ?, ?)",
                    TokenContext.get().userId, currentOperatorName(), TokenContext.get().role,
                    String.join(",", addedWords), "added=" + added + ",skipped=" + skipped);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("added", added);
        result.put("skipped", skipped);
        return ApiResp.ok(result);
    }

    @PutMapping("/admin/sensitive/{id}")
    public ApiResp<Void> updateSensitive(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        List<String> sets = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (body.get("word") != null) { sets.add("word = ?"); params.add(((String) body.get("word")).trim()); }
        if (body.get("category") != null) { sets.add("category = ?"); params.add(body.get("category")); }
        if (body.get("level") != null) { sets.add("level = ?"); params.add(((Number) body.get("level")).intValue()); }
        if (body.get("action") != null) { sets.add("action = ?"); params.add(body.get("action")); }
        if (sets.isEmpty()) return ApiResp.ok();
        params.add(id);
        jdbc.update("UPDATE t_sensitive_word SET " + String.join(", ", sets) + " WHERE id = ?", params.toArray());
        sensitive.refresh();
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                        "VALUES (0, ?, ?, ?, 'sensitive', 'EDIT_SENSITIVE', ?, '')",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role, String.valueOf(id));
        return ApiResp.ok();
    }

    @DeleteMapping("/admin/sensitive/{id}")
    public ApiResp<Void> removeSensitive(@PathVariable long id) {
        adminOnly();
        jdbc.update("DELETE FROM t_sensitive_word WHERE id = ?", id);
        sensitive.refresh();
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                        "VALUES (0, ?, ?, ?, 'sensitive', 'DELETE_SENSITIVE', ?, '')",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role, String.valueOf(id));
        return ApiResp.ok();
    }

    @PostMapping("/admin/sensitive/batch-delete")
    public ApiResp<Map<String, Object>> batchDeleteSensitive(@RequestBody Map<String, Object> body) {
        adminOnly();
        List<Long> ids = parseIds(body.get("ids"));
        if (ids.isEmpty()) return ApiResp.ok(Map.of("deleted", 0));
        int deleted = 0;
        for (Long id : ids) deleted += jdbc.update("DELETE FROM t_sensitive_word WHERE id = ?", id);
        sensitive.refresh();
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                        "VALUES (0, ?, ?, ?, 'sensitive', 'DELETE_SENSITIVE', ?, ?)",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role,
                ids.size() + " 条批量删除", "ids=" + ids);
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        return ApiResp.ok(result);
    }

    @PostMapping("/admin/sensitive/batch-status")
    public ApiResp<Map<String, Object>> batchStatusSensitive(@RequestBody Map<String, Object> body) {
        adminOnly();
        List<Long> ids = parseIds(body.get("ids"));
        if (ids.isEmpty()) return ApiResp.ok(Map.of("updated", 0));
        Object en = body.get("enabled");
        boolean enabled;
        if (en instanceof Boolean) enabled = (Boolean) en;
        else if (en instanceof Number) enabled = ((Number) en).intValue() != 0;
        else enabled = Boolean.parseBoolean(String.valueOf(en));
        StringBuilder ph = new StringBuilder();
        List<Object> params = new ArrayList<>();
        params.add(enabled ? 1 : 0);
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) ph.append(", ");
            ph.append("?");
            params.add(ids.get(i));
        }
        int updated = jdbc.update("UPDATE t_sensitive_word SET enabled = ? WHERE id IN (" + ph + ")", params.toArray());
        sensitive.refresh();
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                        "VALUES (0, ?, ?, ?, 'sensitive', ?, ?, ?)",
                TokenContext.get().userId, currentOperatorName(), TokenContext.get().role,
                enabled ? "ENABLE_SENSITIVE" : "DISABLE_SENSITIVE",
                ids.size() + " 条", "ids=" + ids);
        Map<String, Object> result = new HashMap<>();
        result.put("updated", updated);
        return ApiResp.ok(result);
    }

    private List<Long> parseIds(Object raw) {
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                if (o instanceof Number) ids.add(((Number) o).longValue());
                else if (o != null) {
                    try { ids.add(Long.parseLong(o.toString().trim())); } catch (NumberFormatException ignore) { /* skip invalid */ }
                }
            }
        }
        return ids;
    }

    @PostMapping("/admin/sensitive/test")
    public ApiResp<List<Map<String, Object>>> testSensitive(@RequestBody Map<String, Object> body) {
        adminOnly();
        String text = body.get("text") == null ? "" : (String) body.get("text");
        List<Map<String, Object>> hits = new ArrayList<>();
        for (SensitiveWordService.Hit h : sensitive.findAll(text)) {
            Map<String, Object> m = new HashMap<>();
            m.put("word", h.meta.word);
            m.put("category", h.meta.category);
            m.put("level", h.meta.level);
            m.put("action", h.meta.action);
            m.put("start", h.start);
            m.put("end", h.end);
            m.put("matched", h.matched);
            hits.add(m);
        }
        return ApiResp.ok(hits);
    }

    /** 用户详情（管理视角）：基础资料 + 订单数/动态数 */
    @GetMapping("/admin/user/{id}")
    public ApiResp<Map<String, Object>> userDetail(@PathVariable long id) {
        adminOnly();
        // queryForList + 显式判空：避免用户已删除/id 非法时抛 EmptyResultDataAccessException → 10500 白屏
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, nickname, avatar, phone, vip_level AS vipLevel, status, role, gender, bio, " +
            "DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS registerDate, last_login_at AS lastLoginAt, " +
            "vip_expire_at AS vipExpireAt " +
            "FROM t_user WHERE id = ? AND is_deleted = 0", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "用户不存在或已注销");
        Map<String, Object> u = rows.get(0);
        long orderCount = cnt("SELECT COUNT(*) FROM t_order WHERE user_id = ? AND is_deleted = 0", id);
        long postCount = cnt("SELECT COUNT(*) FROM t_post WHERE user_id = ? AND is_deleted = 0", id);
        Map<String, Object> r = new LinkedHashMap<>(u);
        r.put("orderCount", orderCount);
        r.put("postCount", postCount);
        return ApiResp.ok(r);
    }

    /** 订单管理：跨用户订单列表，支持状态/关键字（订单号或手机号）过滤 */
    @GetMapping("/admin/order/list")
    public ApiResp<PageResp<Map<String, Object>>> orderList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "") String keyword) {
        adminOnly();
        StringBuilder where = new StringBuilder("WHERE o.is_deleted = 0");
        List<Object> whereArgs = new ArrayList<>();
        if (status != null && !status.isEmpty()) {
            where.append(" AND o.status = ?");
            whereArgs.add(status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            where.append(" AND (o.order_no LIKE ? OR u.phone LIKE ?)");
            whereArgs.add("%" + keyword + "%");
            whereArgs.add("%" + keyword + "%");
        }
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id " + where,
            Long.class, whereArgs.toArray());
        List<Object> listArgs = new ArrayList<>(whereArgs);
        listArgs.add(size);
        listArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT o.id, o.order_no, o.user_id, u.nickname AS userName, o.total_amount, o.pay_amount, " +
            "o.freight_amount, o.coupon_amount, o.donate_amount, o.status, o.created_at " +
            "FROM t_order o LEFT JOIN t_user u ON o.user_id = u.id " + where +
            " ORDER BY o.id DESC LIMIT ? OFFSET ?", listArgs.toArray());
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, size));
    }

    /**
     * 售后申请列表（管理员）。
     * status: appealing(平台介入工单) / processing(进行中) / all / 或具体状态值
     */
    @GetMapping("/admin/order/refund")
    public ApiResp<PageResp<Map<String, Object>>> refundList(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        adminOnly();
        StringBuilder where = new StringBuilder("WHERE r.is_deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if ("processing".equals(status)) {
            where.append("AND r.status NOT IN ('refunded','exchanged','rejected','arbitrated','closed') ");
        } else if ("handled".equals(status)) {
            where.append("AND r.status IN ('refunded','exchanged','rejected','arbitrated','closed') ");
        } else if (!"all".equals(status)) {
            where.append("AND r.status = ? ");
            args.add(status);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_refund r " + where,
            Long.class, args.toArray());
        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.order_no, r.user_id, u.nickname AS userName, r.amount, r.refund_amount, r.reason, " +
            "r.status, r.orig_status, r.handle_note, r.created_at, r.handled_at, r.finished_at, " +
            "r.refund_type, r.evidence_images, r.logistics_no, r.logistics_company, r.return_address, " +
            "r.new_logistics_no, r.new_logistics_company, " +
            "r.appeal_reason, r.appeal_images, r.appealed_at, r.prev_status, " +
            "r.arbitrate_result, r.arbitrate_note, r.arbitrated_at, s.name AS store_name " +
            "FROM t_refund r LEFT JOIN t_user u ON r.user_id = u.id " +
            "LEFT JOIN t_order o ON r.order_no = o.order_no " +
            "LEFT JOIN t_store s ON o.store_id = s.id " +
            where + "ORDER BY r.id DESC LIMIT ? OFFSET ?", listArgs.toArray());
        for (Map<String, Object> r : rows) {
            String st = (String) r.get("status");
            r.put("typeText", com.petmate.refund.RefundStatus.typeText((String) r.get("refund_type")));
            r.put("statusText", com.petmate.refund.RefundStatus.statusText(st));
            r.put("isFinal", com.petmate.refund.RefundStatus.isFinal(st));
            r.put("canArbitrate", com.petmate.refund.RefundStatus.APPEALING.equals(st));
        }
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, size));
    }

    /** 售后单进度时间线（管理员，仲裁时需要看完整往来） */
    @GetMapping("/admin/order/refund/{id}/timeline")
    public ApiResp<List<Map<String, Object>>> refundTimeline(@PathVariable long id) {
        adminOnly();
        return ApiResp.ok(refundLog.timeline(id));
    }

    /** 各状态计数，用于管理端 Tab 角标（介入工单需要显眼） */
    @GetMapping("/admin/order/refund/stat")
    public ApiResp<Map<String, Object>> refundStat() {
        adminOnly();
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN status='appealing' THEN 1 ELSE 0 END),0) AS appealing, " +
            "COALESCE(SUM(CASE WHEN status='pending' THEN 1 ELSE 0 END),0) AS pending, " +
            "COALESCE(SUM(CASE WHEN status NOT IN " +
            "  ('refunded','exchanged','rejected','arbitrated','closed') THEN 1 ELSE 0 END),0) AS processing, " +
            "COALESCE(SUM(CASE WHEN status='refunded' THEN 1 ELSE 0 END),0) AS refunded, " +
            "COALESCE(SUM(CASE WHEN status='arbitrated' THEN 1 ELSE 0 END),0) AS arbitrated " +
            "FROM t_refund WHERE is_deleted = 0");
        return ApiResp.ok(row);
    }

    /** 处理售后申请（管理员代处理 / 平台仲裁）：
     *  - approve  ：仅退款→直接退款(refunded)；退货退款→待寄回(returning)；换货→待寄回(exchanging)
     *  - confirm  ：退货退款确认收货→退款完成(refunded)
     *  - reject   ：驳回，订单恢复原状态
     *  - arbitrate：平台仲裁（仅 appealing 单）。result=refund 支持买家并退款，
     *               result=reject 维持商家决定，回退到申请介入前的状态
     */
    @PostMapping("/admin/order/refund/{id}/handle")
    @Transactional
    public ApiResp<Void> refundHandle(@PathVariable long id, @RequestBody Map<String, Object> body) {
        adminOnly();
        String action = body.get("action") == null ? null : (String) body.get("action");
        String note = body.get("note") == null ? null : (String) body.get("note");
        if (action == null) throw new BizException(ErrorCode.PARAM_ERROR, "action 不能为空");
        long adminId = TokenContext.get().userId;

        // 仲裁走独立分支：只认 appealing 状态
        if ("arbitrate".equals(action)) {
            return arbitrate(id, body, note, adminId);
        }

        Map<String, Object> rf = jdbc.queryForMap(
            "SELECT order_no, orig_status, refund_type, status, amount, refund_amount FROM t_refund " +
            "WHERE id = ? AND status IN ('pending','returning','exchanging','returned') AND is_deleted = 0", id);
        String orderNo = (String) rf.get("order_no");
        String orig = (String) rf.get("orig_status");
        String refundType = (String) rf.get("refund_type");
        String curStatus = (String) rf.get("status");
        boolean needReturn = com.petmate.refund.RefundStatus.needReturn(refundType);
        String newStatus;
        String logNote;
        boolean finished = false;

        if ("approve".equals(action)) {
            newStatus = com.petmate.refund.RefundStatus.afterApprove(refundType);
            if (!needReturn) {
                restoreStockForOrder(orderNo);
                jdbc.update("UPDATE t_order SET status = 'refunded' WHERE order_no = ?", orderNo);
                finished = true;
            }
            logNote = "平台客服代商家同意" + com.petmate.refund.RefundStatus.typeText(refundType)
                + (note == null || note.isBlank() ? "" : "：" + note);
        } else if ("confirm".equals(action)) {
            if (!needReturn) throw new BizException(ErrorCode.PARAM_ERROR, "仅退款无需确认收货");
            newStatus = com.petmate.refund.RefundStatus.REFUNDED;
            restoreStockForOrder(orderNo);
            jdbc.update("UPDATE t_order SET status = 'refunded' WHERE order_no = ?", orderNo);
            finished = true;
            logNote = "平台客服确认收货并退款" + (note == null || note.isBlank() ? "" : "：" + note);
        } else if ("reject".equals(action)) {
            newStatus = com.petmate.refund.RefundStatus.REJECTED;
            jdbc.update("UPDATE t_order SET status = ? WHERE order_no = ?",
                orig == null ? "paid" : orig, orderNo);
            finished = true;
            logNote = "平台客服驳回售后" + (note == null || note.isBlank() ? "" : "：" + note);
        } else {
            throw new BizException(ErrorCode.PARAM_ERROR, "action 必须为 approve/confirm/reject/arbitrate");
        }
        jdbc.update("UPDATE t_refund SET status = ?, handle_note = ?, handled_at = NOW(), " +
                (finished ? "finished_at = NOW(), " : "") + "updated_at = NOW() WHERE id = ?",
            newStatus, note, id);
        refundLog.logAdmin(id, orderNo, adminId, action, curStatus, newStatus, logNote);
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, ?, ?, 'refund', ?, ?, '')",
                adminId, currentOperatorName(), TokenContext.get().role,
                "REFUND_" + newStatus.toUpperCase(), orderNo);
        return ApiResp.ok();
    }

    /** 平台仲裁：对 appealing 状态的售后单作出裁定 */
    private ApiResp<Void> arbitrate(long id, Map<String, Object> body, String note, long adminId) {
        String result = body.get("result") == null ? null : (String) body.get("result");
        if (!"refund".equals(result) && !"reject".equals(result)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "result 必须为 refund（支持买家）或 reject（维持原判）");
        }
        if (note == null || note.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请填写仲裁说明，该说明会展示给买家和商家");
        }
        List<Map<String, Object>> rs = jdbc.queryForList(
            "SELECT order_no, orig_status, prev_status, refund_type, amount, refund_amount " +
            "FROM t_refund WHERE id = ? AND status = 'appealing' AND is_deleted = 0", id);
        if (rs.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "该售后单不在平台介入中，无法仲裁");
        Map<String, Object> rf = rs.get(0);
        String orderNo = (String) rf.get("order_no");
        String orig = (String) rf.get("orig_status");
        String logNote;

        if ("refund".equals(result)) {
            // 支持买家：直接退款结案
            restoreStockForOrder(orderNo);
            jdbc.update("UPDATE t_order SET status = 'refunded' WHERE order_no = ?", orderNo);
            Object amt = rf.get("refund_amount") == null ? rf.get("amount") : rf.get("refund_amount");
            logNote = "平台裁定：支持买家，退款 ¥" + amt + " 已原路退回。" + note;
        } else {
            // 维持商家决定：订单回到售后前状态
            jdbc.update("UPDATE t_order SET status = ? WHERE order_no = ?",
                orig == null ? "paid" : orig, orderNo);
            logNote = "平台裁定：维持商家处理结果。" + note;
        }
        jdbc.update("UPDATE t_refund SET status = ?, arbitrate_result = ?, arbitrate_note = ?, " +
                "arbitrated_at = NOW(), finished_at = NOW(), updated_at = NOW() WHERE id = ?",
            com.petmate.refund.RefundStatus.ARBITRATED, result, note, id);
        refundLog.logAdmin(id, orderNo, adminId, com.petmate.refund.RefundLogService.ACT_ARBITRATE,
            com.petmate.refund.RefundStatus.APPEALING, com.petmate.refund.RefundStatus.ARBITRATED, logNote);
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, ?, ?, 'refund', 'REFUND_ARBITRATED', ?, ?)",
            adminId, currentOperatorName(), TokenContext.get().role, orderNo, logNote);
        return ApiResp.ok();
    }

    /** 恢复订单商品库存（退款/退货完成时调用） */
    private void restoreStockForOrder(String orderNo) {
        List<Map<String, Object>> items = jdbc.queryForList(
            "SELECT sku_id, quantity FROM t_order_item WHERE order_id = (SELECT id FROM t_order WHERE order_no = ?)", orderNo);
        for (Map<String, Object> it : items) {
            jdbc.update("UPDATE t_product_sku SET stock = stock + ? WHERE id = ?",
                ((Number) it.get("quantity")).intValue(), ((Number) it.get("sku_id")).longValue());
        }
    }

    // ============= 内部 =============

    private long cnt(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
    private long cnt(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }
    private double sumPay(String sql) {
        java.math.BigDecimal n = jdbc.queryForObject(sql, java.math.BigDecimal.class);
        return n == null ? 0 : n.doubleValue();
    }
    private double round2(double v) {
        return Math.round(v * 10000) / 100.0;
    }
    private void adminOnly() {
        TokenContext.requireRoleAndActive("admin");
    }
}
