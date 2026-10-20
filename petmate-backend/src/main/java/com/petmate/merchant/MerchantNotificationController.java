package com.petmate.merchant;

import com.petmate.common.ApiResp;
import com.petmate.common.ErrorCode;
import com.petmate.common.BizException;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商家消息通知中心
 *  - 订单/评价/退款/系统公告等事件的统一读取与已读管理
 */
@RestController
public class MerchantNotificationController {

    private final JdbcTemplate jdbc;
    public MerchantNotificationController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private long mid() {
        long uid = TokenContext.requireUserId();
        TokenContext.requireRoleAndActive("merchant");
        return TokenContext.requireMerchantId();
    }

    /** 列表 + 未读总数 */
    @GetMapping("/merchant/notifications")
    public ApiResp<Map<String, Object>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        long merchantId = mid();
        StringBuilder where = new StringBuilder("WHERE merchant_id = ? ");
        List<Object> args = new java.util.ArrayList<>();
        args.add(merchantId);
        if (type != null && !type.isEmpty()) { where.append("AND type = ? "); args.add(type); }
        if (unreadOnly) where.append("AND is_read = 0 ");

        long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_merchant_notification " + where, Long.class, args.toArray());
        int offset = (Math.max(page, 1) - 1) * size;
        List<Map<String, Object>> list = jdbc.queryForList(
            "SELECT id, type, title, content, related_id AS relatedId, is_read AS isRead, created_at AS createdAt " +
            "FROM t_merchant_notification " + where + "ORDER BY id DESC LIMIT ? OFFSET ?",
            append(args, size, offset).toArray());
        long unread = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_merchant_notification WHERE merchant_id = ? AND is_read = 0",
            Long.class, merchantId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("unreadCount", unread);
        data.put("page", page);
        data.put("size", size);
        return ApiResp.ok(data);
    }

    /** 未读数量（侧边栏角标轮询） */
    @GetMapping("/merchant/notifications/unread-count")
    public ApiResp<Map<String, Object>> unreadCount() {
        long merchantId = mid();
        long unread = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_merchant_notification WHERE merchant_id = ? AND is_read = 0",
            Long.class, merchantId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("count", unread);
        return ApiResp.ok(data);
    }

    /** 标记单条已读 */
    @PostMapping("/merchant/notifications/{id}/read")
    public ApiResp<Void> read(@PathVariable long id) {
        long merchantId = mid();
        int n = jdbc.update(
            "UPDATE t_merchant_notification SET is_read = 1 WHERE id = ? AND merchant_id = ?", id, merchantId);
        if (n == 0) throw new BizException(ErrorCode.PARAM_ERROR, "通知不存在");
        return ApiResp.ok();
    }

    /** 全部已读 */
    @PostMapping("/merchant/notifications/read-all")
    public ApiResp<Void> readAll() {
        long merchantId = mid();
        jdbc.update("UPDATE t_merchant_notification SET is_read = 1 WHERE merchant_id = ?", merchantId);
        return ApiResp.ok();
    }

    private List<Object> append(List<Object> base, Object... extra) {
        List<Object> out = new java.util.ArrayList<>(base);
        for (Object e : extra) out.add(e);
        return out;
    }
}
