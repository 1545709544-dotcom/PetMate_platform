package com.petmate.admin;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.NotifyService;
import com.petmate.common.PageResp;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 举报接口：
 *  - /report             用户提交举报（登录即可）
 *  - /admin/report/list  管理员查看举报列表
 *  - /admin/report/{id}/handle  处理举报
 */
@RestController
public class ReportController {

    private final JdbcTemplate jdbc;
    private final NotifyService notifyService;

    public ReportController(JdbcTemplate jdbc, NotifyService notifyService) {
        this.jdbc = jdbc;
        this.notifyService = notifyService;
    }

    // 商品举报已接入：补齐商品域唯一缺失的举报类型
    private static final Set<String> ALLOWED_TYPES = Set.of("post", "comment", "user", "article", "product");

    /** 提交举报 */
    @PostMapping("/report")
    public ApiResp<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String type = (String) body.get("targetType");
        if (type == null || !ALLOWED_TYPES.contains(type)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "targetType 必须为 post/comment/user/article/product");
        }
        long targetId = ((Number) body.get("targetId")).longValue();
        String reason = (String) body.get("reason");

        jdbc.update(
            "INSERT INTO t_report (reporter_id, target_type, target_id, reason, status) " +
            "VALUES (?, ?, ?, ?, 'pending')",
            uid, type, targetId, reason);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", "pending");
        return ApiResp.ok(out);
    }

    /** 管理员 - 举报列表（支持按类型 / 关键词筛选） */
    @GetMapping("/admin/report/list")
    public ApiResp<PageResp<Map<String, Object>>> list(
            @RequestParam(defaultValue = "pending") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String keyword) {
        TokenContext.requireRoleAndActive("admin");

        String dbStatus;
        switch (status == null ? "" : status) {
            case "resolved" -> dbStatus = "resolved";
            case "rejected" -> dbStatus = "rejected";
            default -> dbStatus = "pending";
        }

        // 动态拼装 WHERE 条件，COUNT 与列表查询共用，保证总数与分页一致
        StringBuilder where = new StringBuilder("WHERE status = ?");
        List<Object> params = new ArrayList<>();
        params.add(dbStatus);

        if (targetType != null && !targetType.isBlank() && ALLOWED_TYPES.contains(targetType)) {
            where.append(" AND target_type = ?");
            params.add(targetType);
        }

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            if (kw.matches("\\d+")) {
                // 纯数字：按举报目标 ID 精确匹配
                where.append(" AND target_id = ?");
                params.add(Long.parseLong(kw));
            } else {
                // 否则按举报理由模糊匹配，转义 !/%/_ 避免 ESCAPE 语法吞参
                String escaped = kw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
                where.append(" AND reason LIKE ? ESCAPE '!'");
                params.add("%" + escaped + "%");
            }
        }

        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_report " + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, reporter_id, target_type, target_id, reason, status, handle_note, " +
            "       created_at, handled_at " +
            "FROM t_report " + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
            pageParams.toArray());

        return ApiResp.ok(PageResp.of(rows.stream().map(ReportController::normalize).toList(),
                total == null ? 0 : total, page, size));
    }

    private static Map<String, Object> normalize(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("reporterId", r.get("reporter_id"));
        m.put("targetType", r.get("target_type"));
        m.put("targetId", r.get("target_id"));
        m.put("reason", r.get("reason"));
        m.put("status", r.get("status"));
        m.put("handleNote", r.get("handle_note"));
        m.put("createdAt", r.get("created_at") == null ? null : r.get("created_at").toString());
        m.put("handledAt", r.get("handled_at") == null ? null : r.get("handled_at").toString());
        return m;
    }

    /**
     * 管理员 - 处理举报。
     *  - resolve：固定执行「删除被举报内容（post/comment/article/product）+ 向内容作者推送违规警告站内信」；
     *            user 类被举报时无内容可删，仅向被举报用户本人推送违规警告。
     *  - reject ：仅关闭工单，不处置内容、不发送警告。
     */
    @PostMapping("/admin/report/{id}/handle")
    public ApiResp<Map<String, Object>> handle(@PathVariable long id,
                                               @RequestBody Map<String, Object> body) {
        TokenContext.requireRoleAndActive("admin");
        String action = (String) body.get("action");
        String note = (String) body.get("note");

        String newStatus;
        switch (action == null ? "" : action) {
            case "resolve" -> newStatus = "resolved";
            case "reject"  -> newStatus = "rejected";
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "action 必须为 resolve/reject");
        }

        // 取举报行（含目标类型/ID）并校验仍为 pending
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, target_type, target_id, status FROM t_report WHERE id = ?", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "举报不存在");
        Map<String, Object> r = rows.get(0);
        if (!"pending".equals(r.get("status"))) throw new BizException(ErrorCode.PARAM_ERROR, "举报已处理");

        // 举报成立(resolve)：删除内容 + 向被举报内容作者推送违规警告
        String applied = "none";
        if ("resolved".equals(newStatus)) {
            applied = resolveReport(
                String.valueOf(r.get("target_type")),
                ((Number) r.get("target_id")).longValue(),
                id);
        }

        jdbc.update(
            "UPDATE t_report SET status = ?, handle_note = ?, handled_at = NOW() WHERE id = ?",
            newStatus, note, id);
        jdbc.update("INSERT INTO t_operation_log (merchant_id, operator_id, operator_name, role, module, action, target, detail) " +
                "VALUES (0, ?, NULL, ?, 'report', ?, ?, ?)",
                TokenContext.get().userId, TokenContext.get().role,
                "REPORT_" + newStatus.toUpperCase(), String.valueOf(id), "applied=" + applied);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("status", newStatus);
        out.put("targetAction", applied);
        return ApiResp.ok(out);
    }

    /**
     * 处理举报成立(resolve)时的组合动作：删除被举报内容 + 向作者推送违规警告，
     * 返回实际执行标记（供审计 / 前端提示）：
     *  - post    → 帖子软删(is_deleted=1, audit_status=rejected) + 向帖子作者发警告，返回 "removed_and_warned"
     *  - comment → 评论软删(is_deleted=1) + 向评论作者发警告，返回 "removed_and_warned"
     *  - article → 文章软删(is_deleted=1) + 向文章作者发警告，返回 "removed_and_warned"
     *  - product → 商品软删(is_deleted=1) + 向所属商家发警告，返回 "removed_and_warned"
     *  - user    → 无内容可删，仅向被举报用户本人发警告，返回 "warned"
     * 作者/商家定位失败（孤儿内容/不存在）时跳过发信但内容照删，返回相应标记避免误判。
     */
    private String resolveReport(String targetType, long targetId, long reportId) {
        return switch (targetType) {
            case "post" -> {
                jdbc.update("UPDATE t_post SET is_deleted = 1, audit_status = 'rejected' WHERE id = ?", targetId);
                Long authorId = jdbc.queryForObject(
                    "SELECT user_id FROM t_post WHERE id = ? AND is_deleted = 1", Long.class, targetId);
                sendContentWarning(authorId, "帖子", targetId, "post", reportId);
                yield authorId != null ? "removed_and_warned" : "removed";
            }
            case "comment" -> {
                jdbc.update("UPDATE t_post_comment SET is_deleted = 1 WHERE id = ?", targetId);
                Long authorId = jdbc.queryForObject(
                    "SELECT user_id FROM t_post_comment WHERE id = ? AND is_deleted = 1", Long.class, targetId);
                sendContentWarning(authorId, "评论", targetId, "comment", reportId);
                yield authorId != null ? "removed_and_warned" : "removed";
            }
            case "user" -> {
                // 被举报对象就是用户本人：无内容可删，仅向该用户推送违规警告
                sendContentWarning(targetId, "账号", targetId, "user", reportId);
                yield "warned";
            }
            case "article" -> {
                jdbc.update("UPDATE t_article SET is_deleted = 1 WHERE id = ?", targetId);
                Long authorId = jdbc.queryForObject(
                    "SELECT author_id FROM t_article WHERE id = ? AND is_deleted = 1", Long.class, targetId);
                sendContentWarning(authorId, "文章", targetId, "article", reportId);
                yield authorId != null ? "removed_and_warned" : "removed";
            }
            case "product" -> {
                jdbc.update("UPDATE t_product SET is_deleted = 1 WHERE id = ?", targetId);
                Long merchantId = jdbc.queryForObject(
                    "SELECT merchant_id FROM t_product WHERE id = ? AND is_deleted = 1", Long.class, targetId);
                if (merchantId != null) {
                    sendMerchantWarning(merchantId, "商品", targetId, "product", reportId);
                    yield "removed_and_warned";
                }
                yield "removed";
            }
            default -> "none";
        };
    }

    /**
     * 向内容作者（或被举报用户）推送违规警告站内信；作者/用户定位失败则静默跳过，避免阻断举报流程。
     */
    private void sendContentWarning(Long authorId, String kindLabel, long targetId, String targetType, long reportId) {
        if (authorId == null || authorId <= 0) return;
        String title = "内容违规处理通知";
        String content;
        if ("user".equals(targetType)) {
            content = "您好，您被其他用户举报存在违规行为，经核实管理员已对您作出违规警告。请遵守《PetMate 社区公约》，多次违规可能导致账号受限或封禁。";
        } else {
            content = "您好，您发布的" + kindLabel + "（#" + targetId + "）因被举报违反社区规范，已被管理员下架/删除处理。请遵守《PetMate 社区公约》，多次违规可能导致账号受限。如有异议请联系客服。";
        }
        notifyService.notifyUser(authorId, "warning", title, content, String.valueOf(reportId));
    }

    /**
     * 向被举报商品的所属商家推送违规警告站内信（商品域专用）。
     */
    private void sendMerchantWarning(Long merchantId, String kindLabel, long targetId, String targetType, long reportId) {
        if (merchantId == null || merchantId <= 0) return;
        String title = "商品违规处理通知";
        String content = "您好，您发布的商品（#" + targetId + "）因被举报违反平台规范，已被管理员下架处理。请遵守《PetMate 商品发布规范》，多次违规可能导致店铺受限。如有异议请联系客服。";
        notifyService.notify(merchantId, "warning", title, content, String.valueOf(reportId));
    }

    /**
     * 解析举报目标，便于后台「查看」跳转：
     *  - post   → { type:'post',   targetId }
     *  - user   → { type:'user',   targetId }（跳转后台用户详情）
     *  - comment→ { type:'comment', targetId, postId, snippet }（评论归属的帖子 id）
     *  - product→ { type:'product', targetId }（跳转后台商品管理）
     */
    @GetMapping("/admin/report/{id}/target")
    public ApiResp<Map<String, Object>> target(@PathVariable long id) {
        TokenContext.requireRoleAndActive("admin");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, target_type, target_id FROM t_report WHERE id = ?", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "举报不存在");
        Map<String, Object> r = rows.get(0);
        String type = String.valueOf(r.get("target_type"));
        long targetId = ((Number) r.get("target_id")).longValue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", type);
        out.put("targetId", targetId);
        if ("comment".equals(type)) {
            List<Map<String, Object>> cm = jdbc.queryForList(
                "SELECT post_id, content FROM t_post_comment WHERE id = ? AND is_deleted = 0", targetId);
            if (!cm.isEmpty()) {
                out.put("postId", cm.get(0).get("post_id"));
                String c = String.valueOf(cm.get(0).get("content") == null ? "" : cm.get(0).get("content"));
                out.put("snippet", c.length() > 80 ? c.substring(0, 80) + "…" : c);
            }
        }
        return ApiResp.ok(out);
    }
}
