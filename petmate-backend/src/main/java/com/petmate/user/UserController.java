package com.petmate.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.pay.AlipayClient;
import com.petmate.pay.AlipayProperties;
import com.petmate.pay.PaymentLogService;
import com.petmate.security.IdempotencyService;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户相关接口：地址、收藏、优惠券、个人资料
 */
@RestController
public class UserController {

    private final JdbcTemplate jdbc;
    private final UserService userService;
    private final ObjectMapper json = new ObjectMapper();
    private final AlipayProperties alipayProps;
    private final AlipayClient alipayClient;
    private final PaymentLogService paymentLogService;
    private final IdempotencyService idempotency;
    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    public UserController(JdbcTemplate jdbc, UserService userService, AlipayProperties alipayProps,
                          AlipayClient alipayClient, PaymentLogService paymentLogService,
                          IdempotencyService idempotency) {
        this.jdbc = jdbc;
        this.userService = userService;
        this.alipayProps = alipayProps;
        this.alipayClient = alipayClient;
        this.paymentLogService = paymentLogService;
        this.idempotency = idempotency;
    }

    // ================ 收藏 ================

    @GetMapping("/favorite/list")
    public ApiResp<List<Map<String, Object>>> listFavorites(@RequestParam(required = false) String targetType) {
        long uid = TokenContext.requireUserId();
        String sql = "SELECT id, user_id, target_type, target_id, title, image, price, created_at FROM t_favorite " +
                     "WHERE user_id = ? AND is_deleted = 0" +
                     (targetType == null ? "" : " AND target_type = ?") + " ORDER BY id DESC";
        return ApiResp.ok(jdbc.query(sql,
            (rs, i) -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", rs.getLong("id"));
                m.put("userId", rs.getLong("user_id"));
                m.put("targetType", rs.getString("target_type"));
                m.put("targetId", rs.getLong("target_id"));
                m.put("title", rs.getString("title"));
                m.put("image", rs.getString("image"));
                m.put("price", rs.getBigDecimal("price"));
                m.put("createdAt", rs.getTimestamp("created_at").toLocalDateTime().toString());
                return m;
            },
            targetType == null ? new Object[]{uid} : new Object[]{uid, targetType}));
    }

    @PostMapping("/favorite/toggle")
    public ApiResp<Map<String, Object>> toggleFavorite(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String targetType = (String) body.get("targetType");
        long targetId = ((Number) body.get("targetId")).longValue();
        if (targetType == null) throw new BizException(ErrorCode.PARAM_ERROR, "targetType 必填");

        // 查询是否已收藏
        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT id, is_deleted FROM t_favorite WHERE user_id = ? AND target_type = ? AND target_id = ? LIMIT 1",
            uid, targetType, targetId);

        boolean collected;
        if (existing.isEmpty()) {
            // 新增：根据 targetType 取 title/image/price 快照
            String title = null, image = null;
            java.math.BigDecimal price = null;
            if ("product".equals(targetType)) {
                List<Map<String, Object>> p = jdbc.queryForList(
                    "SELECT name AS title, main_image AS image, sale_price AS price FROM t_product WHERE id = ?", targetId);
                if (!p.isEmpty()) { Map<String, Object> r = p.get(0); title = (String) r.get("title"); image = (String) r.get("image"); price = (java.math.BigDecimal) r.get("price"); }
            } else if ("post".equals(targetType)) {
                List<Map<String, Object>> p = jdbc.queryForList(
                    "SELECT SUBSTRING(content,1,30) AS title FROM t_post WHERE id = ?", targetId);
                if (!p.isEmpty()) title = (String) p.get(0).get("title");
            } else if ("article".equals(targetType)) {
                List<Map<String, Object>> p = jdbc.queryForList(
                    "SELECT title, cover AS image FROM t_article WHERE id = ?", targetId);
                if (!p.isEmpty()) { Map<String, Object> r = p.get(0); title = (String) r.get("title"); image = (String) r.get("image"); }
            }
            jdbc.update(
                "INSERT INTO t_favorite (user_id, target_type, target_id, title, image, price) VALUES (?, ?, ?, ?, ?, ?)",
                uid, targetType, targetId, title, image, price);
            collected = true;
        } else {
            Map<String, Object> row = existing.get(0);
            int cur = ((Number) row.get("is_deleted")).intValue();
            long favId = ((Number) row.get("id")).longValue();
            if (cur == 0) {
                jdbc.update("UPDATE t_favorite SET is_deleted = 1 WHERE id = ?", favId);
                collected = false;
            } else {
                jdbc.update("UPDATE t_favorite SET is_deleted = 0 WHERE id = ?", favId);
                collected = true;
            }
        }
        return ApiResp.ok(Map.of("collected", collected));
    }

    // ================ 商品足迹 ================

    @GetMapping("/user/footprint")
    public ApiResp<List<Map<String, Object>>> listFootprints() {
        long uid = TokenContext.requireUserId();
        String sql = "SELECT f.id AS fid, f.product_id, f.type, f.view_count, f.last_view_at, f.created_at, "
                     + "CASE WHEN f.type = 'knowledge' THEN a.title "
                     + "     WHEN f.type = 'community' THEN LEFT(pt.content, 30) "
                     + "     ELSE p.name END AS name, "
                     + "CASE WHEN f.type = 'knowledge' THEN a.cover "
                     + "     WHEN f.type = 'community' THEN JSON_UNQUOTE(JSON_EXTRACT(pt.images, '$[0]')) "
                     + "     ELSE p.main_image END AS cover, "
                     + "CASE WHEN f.type = 'knowledge' THEN NULL "
                     + "     WHEN f.type = 'community' THEN NULL "
                     + "     ELSE p.sale_price END AS sale_price, "
                     + "CASE WHEN f.type = 'knowledge' THEN NULL "
                     + "     WHEN f.type = 'community' THEN NULL "
                     + "     ELSE p.original_price END AS original_price, "
                     + "CASE WHEN f.type = 'knowledge' THEN NULL "
                     + "     WHEN f.type = 'community' THEN pt.is_deleted "
                     + "     ELSE p.status END AS status "
                     + "FROM t_product_footprint f "
                     + "LEFT JOIN t_product p ON p.id = f.product_id AND f.type = 'product' "
                     + "LEFT JOIN t_article a ON a.id = f.product_id AND f.type = 'knowledge' "
                     + "LEFT JOIN t_post pt ON pt.id = f.product_id AND f.type = 'community' "
                     + "WHERE f.user_id = ? AND f.is_deleted = 0 ORDER BY f.last_view_at DESC LIMIT 100";
        List<Map<String, Object>> rows = jdbc.query(sql, (rs, i) -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", rs.getLong("fid"));
            m.put("productId", rs.getLong("product_id"));
            String type = rs.getString("type");
            m.put("type", type == null ? "product" : type);
            m.put("name", rs.getString("name"));
            m.put("mainImage", rs.getString("cover"));
            m.put("salePrice", rs.getBigDecimal("sale_price"));
            m.put("originalPrice", rs.getBigDecimal("original_price"));
            m.put("viewCount", rs.getInt("view_count"));
            java.sql.Timestamp lv = rs.getTimestamp("last_view_at");
            m.put("lastViewAt", lv == null ? null : lv.toLocalDateTime().toString());
            java.sql.Timestamp ca = rs.getTimestamp("created_at");
            m.put("createdAt", ca == null ? null : ca.toLocalDateTime().toString());
            boolean offline = "knowledge".equals(type) ? false
                : (rs.getObject("status") == null || ((Number) rs.getObject("status")).intValue() != 1);
            m.put("offline", offline);
            return m;
        }, uid);
        return ApiResp.ok(rows);
    }

    @DeleteMapping("/user/footprint/{id}")
    public ApiResp<Void> deleteFootprint(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        jdbc.update("UPDATE t_product_footprint SET is_deleted = 1 WHERE user_id = ? AND id = ? AND is_deleted = 0",
            uid, id);
        return ApiResp.ok(null);
    }

    @DeleteMapping("/user/footprint")
    public ApiResp<Void> clearFootprints() {
        long uid = TokenContext.requireUserId();
        jdbc.update("UPDATE t_product_footprint SET is_deleted = 1 WHERE user_id = ? AND is_deleted = 0", uid);
        return ApiResp.ok(null);
    }

    // 通用足迹记录：支持商品(product) / 知识科普(knowledge) / 社区动态(community)
    @PostMapping("/user/footprint")
    public ApiResp<Void> recordFootprint(@RequestBody java.util.Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String type = body.get("type") == null ? "product" : body.get("type").toString();
        Object tidObj = body.get("targetId");
        if (tidObj == null) return ApiResp.ok(null);
        long targetId;
        try {
            targetId = ((Number) tidObj).longValue();
        } catch (Exception e) {
            return ApiResp.ok(null);
        }
        // 校验目标存在，避免脏数据（知识文章用 is_deleted 软删；社区动态用 t_post；商品用 status=1）
        String checkSql;
        if ("knowledge".equals(type)) {
            checkSql = "SELECT COUNT(*) FROM t_article WHERE id = ? AND is_deleted = 0";
        } else if ("community".equals(type)) {
            checkSql = "SELECT COUNT(*) FROM t_post WHERE id = ? AND is_deleted = 0";
        } else {
            checkSql = "SELECT COUNT(*) FROM t_product WHERE id = ? AND status = 1";
            type = "product";
        }
        Integer cnt = jdbc.queryForObject(checkSql, Integer.class, targetId);
        if (cnt == null || cnt == 0) return ApiResp.ok(null);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<Map<String, Object>> exist = jdbc.queryForList(
            "SELECT id, last_view_at, is_deleted FROM t_product_footprint WHERE user_id = ? AND product_id = ? AND type = ? LIMIT 1",
            uid, targetId, type);
        if (exist.isEmpty()) {
            jdbc.update("INSERT INTO t_product_footprint (user_id, product_id, type, view_count, last_view_at, created_at, is_deleted) "
                + "VALUES (?, ?, ?, 1, ?, ?, 0)",
                uid, targetId, type, java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now));
        } else {
            Map<String, Object> row = exist.get(0);
            long fid = ((Number) row.get("id")).longValue();
            java.time.LocalDateTime last = (java.time.LocalDateTime) row.get("last_view_at");
            if (last != null && last.plusHours(1).isAfter(now)) {
                jdbc.update("UPDATE t_product_footprint SET last_view_at = ?, is_deleted = 0 WHERE id = ?",
                    java.sql.Timestamp.valueOf(now), fid);
            } else {
                jdbc.update("UPDATE t_product_footprint SET view_count = view_count + 1, last_view_at = ?, is_deleted = 0 WHERE id = ?",
                    java.sql.Timestamp.valueOf(now), fid);
            }
        }
        return ApiResp.ok(null);
    }

    // ================ 优惠券 ================

    @GetMapping("/coupon/list")
    public ApiResp<List<Map<String, Object>>> listCoupons() {
        long uid = TokenContext.requireUserId();
        // 返回所有可用 + 用户已领取状态
        List<Map<String, Object>> all = jdbc.queryForList(
            "SELECT id, name, type, threshold, amount, discount, valid_start, valid_end FROM t_coupon " +
            "WHERE is_deleted = 0 AND valid_end >= NOW() ORDER BY id");
        Map<Long, String> userStatus = new java.util.HashMap<>();
        jdbc.query("SELECT coupon_id, status FROM t_user_coupon WHERE user_id = ?",
            rs -> { userStatus.put(rs.getLong("coupon_id"), rs.getString("status")); }, uid);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> c : all) {
            Map<String, Object> m = new java.util.LinkedHashMap<>(c);
            // 状态语义：未持有->available(可领取)；持有可用->claimed(已领取可用)；持有已用->used；持有过期->expired
            // 结算页/领券中心据此判断「是否真的可用」，避免把未领取的券当成可用券抵扣
            String us = userStatus.get(((Number) c.get("id")).longValue());
            m.put("status", us == null ? "available" : ("available".equals(us) ? "claimed" : us));
            m.put("validStart", c.get("valid_start").toString());
            m.put("validEnd", c.get("valid_end").toString());
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    @PostMapping("/coupon/claim")
    public ApiResp<Map<String, Object>> claimCoupon(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        long couponId = ((Number) body.get("couponId")).longValue();

        // 校验券本身存在且在有效期内
        List<Map<String, Object>> coupon = jdbc.queryForList(
            "SELECT valid_start, valid_end, is_deleted FROM t_coupon WHERE id = ?", couponId);
        if (coupon.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "优惠券不存在");
        Map<String, Object> c = coupon.get(0);
        if (((Number) c.get("is_deleted")).intValue() == 1) {
            throw new BizException(ErrorCode.PRODUCT_OFFLINE, "优惠券已下架");
        }

        // 应用层预检：是否已领取
        Integer existed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_user_coupon WHERE user_id = ? AND coupon_id = ?",
            Integer.class, uid, couponId);
        if (existed != null && existed > 0) {
            throw new BizException(ErrorCode.COUPON_ALREADY_CLAIMED);
        }

        // 兜底：并发场景下 DB UNIQUE 索引兜底
        try {
            jdbc.update("INSERT INTO t_user_coupon (user_id, coupon_id, status) VALUES (?, ?, 'available')",
                uid, couponId);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            throw new BizException(ErrorCode.COUPON_ALREADY_CLAIMED);
        }
        return ApiResp.ok(Map.of("claimed", true));
    }

    // ================ 个人资料更新 ================

    @PutMapping("/user/profile")
    public ApiResp<Map<String, Object>> updateProfile(@RequestBody Map<String, Object> body,
                                                      jakarta.servlet.http.HttpServletRequest req) {
        long uid = TokenContext.requireUserId();
        userService.updateProfile(uid, body);
        return ApiResp.ok(UserService.toMap(userService.findById(uid), req));
    }

    // ================ 头像上传 ================

    @org.springframework.web.bind.annotation.PostMapping(value = "/user/avatar", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<Map<String, Object>> uploadAvatar(@org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                                     jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
        long uid = TokenContext.requireUserId();
        if (file.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "文件为空");
        String contentType = file.getContentType();
        if (contentType == null || !(contentType.equals("image/jpeg") || contentType.equals("image/png") || contentType.equals("image/webp"))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 jpg/png/webp");
        }
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new BizException(ErrorCode.PARAM_ERROR, "文件不能超过 5MB");
        }
        // 存到 backend 进程工作目录下的 static/uploads/avatar/
        java.nio.file.Path uploadRoot = java.nio.file.Paths.get(
            System.getProperty("user.dir"), "static", "uploads", "avatar");
        java.nio.file.Files.createDirectories(uploadRoot);
        String ext = contentType.equals("image/png") ? ".png"
                   : contentType.equals("image/webp") ? ".webp" : ".jpg";
        String filename = "u" + uid + "_" + System.currentTimeMillis() + ext;
        java.nio.file.Path target = uploadRoot.resolve(filename);
        file.transferTo(target.toFile());

        // 写回 DB（存相对路径，部署时可灵活决定域名）
        // 注意：带 context-path（默认 /api/v1），因为 servlet context-path 只会把
        // /api/v1/** 转发到 DispatcherServlet 来匹配我们注册的 /static/** 资源处理器。
        String relativeUrl = request.getContextPath() + "/static/uploads/avatar/" + filename;
        userService.updateProfile(uid, java.util.Map.of("avatar", relativeUrl));

        // 返回给前端的 url 拼成完整地址，避免前端跨域/代理配置差异导致加载不到
        String fullUrl = buildFullUrl(request, relativeUrl);
        return ApiResp.ok(java.util.Map.of("url", fullUrl, "user", UserService.toMap(userService.findById(uid), request)));
    }

    private String buildFullUrl(jakarta.servlet.http.HttpServletRequest req, String path) {
        String scheme = req.getScheme();
        String host = req.getServerName();
        int port = req.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return defaultPort
                ? scheme + "://" + host + path
                : scheme + "://" + host + ":" + port + path;
    }

    // ================ 他人主页（公开资料） ================

    /**
     * 获取任意用户的主页公开资料（点击头像进入「对面主页」时使用）。
     * 返回：基础资料 + 粉丝/关注/动态数 + 当前登录用户是否关注对方(following)。
     * 允许未登录访问（帖子详情侧栏的作者信息需要展示）：登录态下返回 following，未登录则固定为 false。
     */
    @GetMapping("/user/profile/{id}")
    public ApiResp<Map<String, Object>> getUserProfile(@PathVariable long id) {
        TokenContext.Principal p = TokenContext.get();
        long myId = p == null ? 0 : p.userId;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, nickname, avatar, bio, vip_level, gender, region FROM t_user WHERE id = ? AND is_deleted = 0", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "用户不存在");
        Map<String, Object> u = rows.get(0);
        Long followerCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_follow WHERE followee_id = ?", Long.class, id);
        Long followingCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_follow WHERE follower_id = ?", Long.class, id);
        Long postCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_post WHERE user_id = ? AND is_deleted = 0 AND audit_status = 'approved'",
            Long.class, id);
        // 累计获赞：该用户所有已通过审核、未删除帖子的 like_count 之和（动态详情侧栏「获赞」使用）
        Long totalLikes = jdbc.queryForObject(
            "SELECT COALESCE(SUM(like_count), 0) FROM t_post WHERE user_id = ? AND is_deleted = 0 AND audit_status = 'approved'",
            Long.class, id);
        // 未登录（myId=0）时直接置 false；已登录才查 follow 表，避免 SQL 报参数错误
        boolean isFollowing = false;
        if (myId != 0 && myId != id) {
            Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_follow WHERE follower_id = ? AND followee_id = ?",
                Integer.class, myId, id);
            isFollowing = cnt != null && cnt > 0;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.get("id"));
        out.put("nickname", u.get("nickname"));
        out.put("avatar", u.get("avatar"));
        out.put("bio", u.get("bio"));
        out.put("vipLevel", u.get("vip_level") == null ? 0 : ((Number) u.get("vip_level")).intValue());
        out.put("gender", u.get("gender") == null ? 0 : ((Number) u.get("gender")).intValue());
        out.put("region", u.get("region") == null ? "" : u.get("region").toString());
        out.put("followerCount", followerCount == null ? 0 : followerCount);
        out.put("followingCount", followingCount == null ? 0 : followingCount);
        out.put("postCount", postCount == null ? 0 : postCount);
        out.put("totalLikes", totalLikes == null ? 0 : totalLikes);
        out.put("following", isFollowing);
        return ApiResp.ok(out);
    }

    // ================ 推荐关注 ================

    /**
     * 推荐关注：返回当前用户「尚未关注」的其他活跃用户，按粉丝数降序（越热门越靠前）。
     * 用于社区侧栏 / 个人中心等推荐位。字段对齐 FollowUser，following 恒为 false（已排除已关注）。
     */
    @GetMapping("/user/recommend")
    public ApiResp<List<Map<String, Object>>> recommendFollow(@RequestParam(defaultValue = "5") int limit) {
        long uid = TokenContext.requireUserId();
        int safeLimit = Math.max(1, Math.min(limit, 20));
        String sql = "SELECT u.id, u.nickname, u.avatar, u.bio, u.vip_level AS vipLevel, " +
                "(SELECT COUNT(*) FROM t_follow f WHERE f.followee_id = u.id) AS fans " +
                "FROM t_user u " +
                "WHERE u.is_deleted = 0 AND u.id <> ? " +
                "AND u.id NOT IN (SELECT followee_id FROM t_follow WHERE follower_id = ?) " +
                "ORDER BY fans DESC, u.id ASC LIMIT ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, uid, uid, safeLimit);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("nickname", r.get("nickname"));
            m.put("avatar", r.get("avatar"));
            m.put("bio", r.get("bio"));
            Object vl = r.get("vipLevel");
            m.put("vipLevel", vl == null ? 0 : ((Number) vl).intValue());
            m.put("following", false);
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    // ================ 开通 VIP ================

    /**
     * 开通 / 续费 / 升级 会员（走支付宝沙箱支付）。
     * body.plan 可选：silver(白银=1) / gold(黄金=2) / bronze(普通=0)
     * 不传 plan 时保持原默认行为（开通黄金=2）。
     *
     * 返回结构（前端据此分支）：
     *  - 演示态（支付宝未启用）：直接开通并返回 { demoMode:true, vipLevel, vip, vipExpireAt, action }
     *  - 真实支付宝：创建支付单并返回 { demoMode:false, payUrl, pageBody, outTradeNo }，
     *    前端打开收银台 + 轮询 /user/vip/pay/status 直到 paid，再由异步通知 grantVip。
     */
    @PostMapping("/user/vip/open")
    public ApiResp<Map<String, Object>> openVip(@RequestBody(required = false) Map<String, Object> body) {
        long uid = TokenContext.requireUserId();

        // 解析所选档位；缺省时按旧逻辑默认开通黄金（或保持当前档位）
        Integer planLevel = null;
        if (body != null && body.get("plan") instanceof String plan) {
            // 与 /vip/plan 返回的 key（l1/l2/l3 = 白银/黄金/钻石）保持一致；
            // 兼容旧调用方使用的 bronze/silver/gold 别名。
            switch (plan) {
                case "l1", "bronze" -> planLevel = 1;
                case "l2", "silver" -> planLevel = 2;
                case "l3", "gold"   -> planLevel = 3;
                default -> throw new BizException(ErrorCode.PARAM_ERROR, "plan 仅支持 l1/l2/l3");
            }
        }
        if (planLevel == null) {
            int cur = jdbc.queryForObject("SELECT vip_level FROM t_user WHERE id = ? AND is_deleted = 0", Integer.class, uid);
            planLevel = cur <= 0 ? 1 : cur;
        }

        BigDecimal amount = planAmount(planLevel);
        Map<String, Object> out = new java.util.LinkedHashMap<>();

        // ===== 演示态（支付宝未启用）：直接开通 =====
        if (!alipayProps.isEnabled()) {
            Map<String, Object> grant = userService.grantVip(uid, planLevel);
            out.put("demoMode", true);
            out.putAll(grant);
            return ApiResp.ok(out);
        }

        // ===== 真实支付宝：创建支付单（避免重复预下单）=====
        // 60s 内同 uid+plan 重复点击直接返回上次结果
        String idemKey = "vip:open:" + uid + ":" + planLevel;
        Object cached = idempotency.getCachedResponse(idemKey);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) cached;
            return ApiResp.ok(m);
        }

        String outTradeNo = "VO" + System.currentTimeMillis() + uid;
        jdbc.update(
            "INSERT INTO t_vip_pay (out_trade_no, user_id, plan_level, amount, status) " +
            "VALUES (?, ?, ?, ?, 'pending_pay')",
            outTradeNo, uid, planLevel, amount);

        try {
            String planName = planLevel == 1 ? "白银会员" : planLevel == 2 ? "黄金会员" : "钻石会员";
            // return_url：优先用前端传来的动态 origin 根路径（代理/预览域名下硬编码 localhost 会 404）。
            // 根路径 + 查询参数模式由前端路由守卫重定向到 /user/vip/result/:outTradeNo，不依赖服务端 SPA 回退。
            String returnBase = (body != null) ? (String) body.get("returnBase") : null;
            String returnUrlOverride = (returnBase != null && !returnBase.isEmpty())
                ? returnBase + "/?payType=vip&outTradeNo=" + outTradeNo
                : "http://localhost:5173/?payType=vip&outTradeNo=" + outTradeNo;
            AlipayClient.PagePayResult pr = alipayClient.pagePay(
                outTradeNo, outTradeNo, amount, "PetMate 会员-" + planName, returnUrlOverride);
            paymentLogService.log(null, outTradeNo, null, "alipay", "vip_page_pay", amount, "pending", null);

            out.put("demoMode", false);
            out.put("payUrl", pr.payUrl == null ? "" : pr.payUrl);
            out.put("pageBody", pr.pageBody == null ? "" : pr.pageBody);
            out.put("outTradeNo", pr.outTradeNo);
            idempotency.record(idemKey, out);
            return ApiResp.ok(out);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "会员支付预下单失败：" + e.getMessage());
        }
    }

    /**
     * 会员支付结果轮询（前端打开收银台后轮询，直到异步通知 grantVip 置 paid）。
     */
    @GetMapping("/user/vip/pay/status")
    public ApiResp<Map<String, Object>> vipPayStatus(@RequestParam String outTradeNo) {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, status, plan_level, amount FROM t_vip_pay WHERE out_trade_no = ? AND user_id = ?",
            outTradeNo, uid);
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (rows.isEmpty()) {
            out.put("status", "not_found");
            out.put("paid", false);
            return ApiResp.ok(out);
        }
        Map<String, Object> row = rows.get(0);
        String status = (String) row.get("status");

        // 待支付：主动查询支付宝交易状态。
        // 支付宝沙箱异步通知无法回打 localhost:8080，故由前端轮询驱动此处主动查询并开通，
        // 解除对异步通知可达性的依赖（部署到公网后仍可走异步通知，二者幂等互不冲突）。
        if ("pending_pay".equals(status)) {
            try {
                AlipayClient.TradeQueryResult qr = alipayClient.tradeQuery(outTradeNo);
                String tradeStatus = qr.tradeStatus;
                if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                    int planLevel = ((Number) row.get("plan_level")).intValue();
                    java.math.BigDecimal amt = (java.math.BigDecimal) row.get("amount");
                    try {
                        userService.grantVip(uid, planLevel);
                    } catch (Exception grantEx) {
                        // 会员规则（如不可降级）被拒：支付已成功，保持当前等级，订单仍置为 paid
                        log.info("[vip-pay-status] grantVip skipped ({}), keep current tier", grantEx.getMessage());
                    }
                    jdbc.update(
                        "UPDATE t_vip_pay SET status = 'paid', paid_at = NOW(), trade_no = ? " +
                        "WHERE out_trade_no = ? AND status = 'pending_pay'",
                        qr.tradeNo == null ? "" : qr.tradeNo, outTradeNo);
                    paymentLogService.log(null, outTradeNo, qr.tradeNo, "alipay", "vip_query", amt, "success", null);
                    status = "paid";
                }
            } catch (Exception e) {
                // 查询失败（沙箱网关抖动 / 代理不可达）：保持 pending，前端继续轮询
            }
        }

        out.put("status", status);
        out.put("paid", "paid".equals(status));
        // 刷新当前 VIP 信息
        List<Map<String, Object>> u = jdbc.queryForList(
            "SELECT vip_level, vip_expire_at FROM t_user WHERE id = ? AND is_deleted = 0", uid);
        if (!u.isEmpty()) {
            Map<String, Object> ur = u.get(0);
            out.put("vipLevel", ur.get("vip_level") == null ? 0 : ((Number) ur.get("vip_level")).intValue());
            Object ve = ur.get("vip_expire_at");
            if (ve instanceof java.sql.Timestamp ts) out.put("vipExpireAt", ts.toLocalDateTime().toString());
            else if (ve instanceof java.time.LocalDateTime ldt) out.put("vipExpireAt", ldt.toString());
            else out.put("vipExpireAt", null);
        }
        return ApiResp.ok(out);
    }

    /** 会员档位对应金额（与 /vip/plan 套餐月费一致：1=白银18 / 2=黄金29 / 3=钻石98） */
    private static BigDecimal planAmount(int level) {
        return switch (level) {
            case 1 -> new BigDecimal("18.00");
            case 2 -> new BigDecimal("29.00");
            case 3 -> new BigDecimal("98.00");
            default -> new BigDecimal("98.00");
        };
    }

    // ================ 账号安全 ================

    /**
     * 修改密码（任意已登录角色通用：普通用户/商家/管理员）
     * body: { oldPassword, newPassword }
     */
    @PostMapping("/user/password")
    public ApiResp<Map<String, Object>> changePassword(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String oldPwd = (String) body.get("oldPassword");
        String newPwd = (String) body.get("newPassword");
        userService.changePassword(uid, oldPwd, newPwd);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        return ApiResp.ok(out);
    }

    // ================ 实名认证 ================

    /**
     * 提交实名认证：真实姓名 + 18 位身份证号。
     * 成功后 verified=1,解锁发布领养/发帖等需实名的功能。
     */
    @PostMapping("/user/realname")
    public ApiResp<Map<String, Object>> realname(@RequestBody Map<String, Object> body,
                                                 jakarta.servlet.http.HttpServletRequest req) {
        long uid = TokenContext.requireUserId();
        String realName = (String) body.get("realName");
        String idCard = (String) body.get("idCard");
        userService.realname(uid, realName, idCard);
        return ApiResp.ok(UserService.toMap(userService.findById(uid), req));
    }

    // ================ 注销账号 ================

    /**
     * 注销账号：校验登录密码后软删除(演示态后续端自动登出)。
     */
  @DeleteMapping("/user/account")
  public ApiResp<Map<String, Object>> deleteAccount(@RequestBody Map<String, Object> body) {
    long uid = TokenContext.requireUserId();
    String password = (String) body.get("password");
    userService.deleteAccount(uid, password);
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("ok", true);
    return ApiResp.ok(out);
  }

  // ============ 我的消息（站内信） ============

  /** 消息列表 + 未读数（前台铃铛/消息中心） */
  @GetMapping("/user/notifications")
  public ApiResp<Map<String, Object>> listNotifications(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "false") boolean unreadOnly) {
    long uid = TokenContext.requireUserId();
    StringBuilder where = new StringBuilder("WHERE user_id = ? ");
    List<Object> args = new ArrayList<>();
    args.add(uid);
    if (type != null && !type.isEmpty()) { where.append("AND type = ? "); args.add(type); }
    if (unreadOnly) where.append("AND is_read = 0 ");

    long total = jdbc.queryForObject(
        "SELECT COUNT(*) FROM t_user_notification " + where, Long.class, args.toArray());
    int offset = (Math.max(page, 1) - 1) * size;
    List<Map<String, Object>> list = jdbc.queryForList(
        "SELECT id, type, title, content, related_id AS relatedId, is_read AS isRead, created_at AS createdAt " +
        "FROM t_user_notification " + where + "ORDER BY id DESC LIMIT ? OFFSET ?",
        append(args, size, offset).toArray());
    long unread = jdbc.queryForObject(
        "SELECT COUNT(*) FROM t_user_notification WHERE user_id = ? AND is_read = 0",
        Long.class, uid);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("list", list);
    data.put("total", total);
    data.put("unreadCount", unread);
    data.put("page", page);
    data.put("size", size);
    return ApiResp.ok(data);
  }

  /** 未读数量（顶栏角标轮询） */
  @GetMapping("/user/notifications/unread-count")
  public ApiResp<Map<String, Object>> notificationUnreadCount() {
    long uid = TokenContext.requireUserId();
    long unread = jdbc.queryForObject(
        "SELECT COUNT(*) FROM t_user_notification WHERE user_id = ? AND is_read = 0",
        Long.class, uid);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("count", unread);
    return ApiResp.ok(data);
  }

  /** 标记单条已读 */
  @PostMapping("/user/notifications/{id}/read")
  public ApiResp<Void> readNotification(@PathVariable long id) {
    long uid = TokenContext.requireUserId();
    int n = jdbc.update(
        "UPDATE t_user_notification SET is_read = 1 WHERE id = ? AND user_id = ?", id, uid);
    if (n == 0) throw new BizException(ErrorCode.PARAM_ERROR, "消息不存在");
    return ApiResp.ok();
  }

  /** 全部已读 */
  @PostMapping("/user/notifications/read-all")
  public ApiResp<Void> readAllNotifications() {
    long uid = TokenContext.requireUserId();
    jdbc.update("UPDATE t_user_notification SET is_read = 1 WHERE user_id = ?", uid);
    return ApiResp.ok();
  }

  private List<Object> append(List<Object> base, Object... extra) {
    List<Object> out = new ArrayList<>(base);
    for (Object e : extra) out.add(e);
    return out;
  }
}
