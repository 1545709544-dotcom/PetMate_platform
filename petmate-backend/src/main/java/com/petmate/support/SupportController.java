package com.petmate.support;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * C 端「联系客服」入口。
 *
 * 系统没有在线 IM / 工单系统，演示态采用「站内信 → 管理后台 support/admin 角色」方案：
 *   1. 用户提交问题文本 → 写入 t_support_message（沉淀为工单）
 *   2. 给 role IN ('admin','support') 的管理员各发一条 t_user_notification，作为待办
 *   3. 给用户回一条 t_user_notification，告知「已收到，客服将尽快回复」
 *
 * 表 t_support_message 在首次调用时由 ensureTable() 自动创建，避免单独迁移脚本。
 * 演示态：管理员在「消息中心」能直接看到用户提交的咨询，配合管理后台的
 * t_merchant_notification（商家侧）形成对内待办通道。
 */
@RestController
public class SupportController {

    private static final Logger log = LoggerFactory.getLogger(SupportController.class);

    private final JdbcTemplate jdbc;

    public SupportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/support/contact")
    public ApiResp<Map<String, Object>> contact(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String content = body.get("content") == null ? "" : String.valueOf(body.get("content")).trim();
        String contact = body.get("contact") == null ? "" : String.valueOf(body.get("contact")).trim();
        String category = body.get("category") == null ? "其他" : String.valueOf(body.get("category")).trim();

        if (content.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "请填写问题描述");
        if (content.length() > 500) throw new BizException(ErrorCode.PARAM_ERROR, "问题描述不能超过 500 字");
        if (category.length() > 16) category = "其他";

        ensureTable();

        // 1) 落库（工单主表）
        jdbc.update(
            "INSERT INTO t_support_message (user_id, category, content, contact, status, created_at) " +
            "VALUES (?, ?, ?, ?, 'pending', NOW())",
            uid, category, content, contact);
        long msgId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // 2) 给所有管理员 / 客服角色发待办
        List<Map<String, Object>> admins = jdbc.queryForList(
            "SELECT id FROM t_user WHERE role IN ('admin','support') AND is_deleted = 0 AND status = 0");
        String preview = truncate(content, 80);
        for (Map<String, Object> a : admins) {
            long adminId = ((Number) a.get("id")).longValue();
            jdbc.update(
                "INSERT INTO t_user_notification (user_id, type, title, content, related_id) " +
                "VALUES (?, 'support_inbox', ?, ?, ?)",
                adminId,
                "用户咨询待处理（#" + msgId + "）",
                "用户 #" + uid + " 提交了一条咨询（" + category + "）：" + preview,
                "support:" + msgId);
        }

        // 3) 给用户回执
        jdbc.update(
            "INSERT INTO t_user_notification (user_id, type, title, content, related_id) " +
            "VALUES (?, 'support_reply', '客服已收到您的咨询', ?, ?)",
            uid, "您提交的问题已送达客服（工单 #" + msgId + "），我们会在 24 小时内通过站内信回复，请留意「消息中心」。",
            "support:" + msgId);

        log.info("[support] user {} submitted msg #{} (notify {} admin(s))", uid, msgId, admins.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("id", msgId);
        return ApiResp.ok(out);
    }

    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }

    /** 首次启动建表（演示态：避免每个模块都要单独迁移脚本） */
    private void ensureTable() {
        try {
            jdbc.execute(
                "CREATE TABLE IF NOT EXISTS t_support_message (\n" +
                "  id BIGINT PRIMARY KEY AUTO_INCREMENT,\n" +
                "  user_id BIGINT NOT NULL,\n" +
                "  category VARCHAR(32) NOT NULL DEFAULT '其他',\n" +
                "  content TEXT NOT NULL,\n" +
                "  contact VARCHAR(64) DEFAULT NULL,\n" +
                "  status VARCHAR(16) NOT NULL DEFAULT 'pending',\n" +
                "  reply TEXT DEFAULT NULL,\n" +
                "  replied_by BIGINT DEFAULT NULL,\n" +
                "  replied_at DATETIME DEFAULT NULL,\n" +
                "  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,\n" +
                "  KEY idx_user (user_id),\n" +
                "  KEY idx_status (status)\n" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception ignore) {
            // 表已存在或权限问题忽略
        }
    }
}