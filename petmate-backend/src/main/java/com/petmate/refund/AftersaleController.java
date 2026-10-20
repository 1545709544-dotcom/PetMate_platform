package com.petmate.refund;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.NotifyService;
import com.petmate.common.PageResp;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 用户端售后中心。
 *
 * <p>此前系统只有「商家端 + 管理端」处理入口，用户提交完申请就彻底失联：
 * 既看不到进度，也没法回填退货物流（导致 returned 是个永远没人赋值的死状态）。
 * 本控制器补齐买家侧闭环：列表 / 详情时间线 / 回填物流 / 换货确认收货 / 撤销 / 申请平台介入。
 */
@RestController
public class AftersaleController {

    private final JdbcTemplate jdbc;
    private final RefundLogService refundLog;
    private final NotifyService notify;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public AftersaleController(JdbcTemplate jdbc, RefundLogService refundLog, NotifyService notify) {
        this.jdbc = jdbc;
        this.refundLog = refundLog;
        this.notify = notify;
    }

    // =====================================================================
    // 查询
    // =====================================================================

    /**
     * 我的售后列表。
     * status: all(默认) / processing(进行中) / done(已完成) / 或具体状态值
     */
    @GetMapping("/refund/list")
    public ApiResp<PageResp<Map<String, Object>>> list(
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long uid = TokenContext.requireUserId();
        page = Math.max(page, 1);
        size = Math.min(Math.max(size, 1), 50);

        StringBuilder where = new StringBuilder("WHERE r.user_id = ? AND r.is_deleted = 0 ");
        List<Object> args = new ArrayList<>();
        args.add(uid);
        if ("processing".equals(status)) {
            where.append("AND r.status NOT IN ('refunded','exchanged','rejected','arbitrated','closed') ");
        } else if ("done".equals(status)) {
            where.append("AND r.status IN ('refunded','exchanged','rejected','arbitrated','closed') ");
        } else if (!"all".equals(status)) {
            where.append("AND r.status = ? ");
            args.add(status);
        }

        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_refund r " + where, Long.class, args.toArray());

        List<Object> listArgs = new ArrayList<>(args);
        listArgs.add(size);
        listArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT r.id, r.order_no, r.refund_type, r.status, r.amount, r.refund_amount, " +
            "r.reason, r.handle_note, r.created_at, r.handled_at, r.finished_at, " +
            "r.logistics_no, r.logistics_company, r.new_logistics_no, r.new_logistics_company, " +
            "s.name AS store_name " +
            "FROM t_refund r " +
            "LEFT JOIN t_order o ON r.order_no = o.order_no " +
            "LEFT JOIN t_store s ON o.store_id = s.id " +
            where + "ORDER BY r.id DESC LIMIT ? OFFSET ?", listArgs.toArray());

        for (Map<String, Object> r : rows) {
            decorate(r);
            r.put("items", orderItems((String) r.get("order_no")));
        }
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, size));
    }

    /** 各状态数量，用于「我的售后」角标 */
    @GetMapping("/refund/count")
    public ApiResp<Map<String, Object>> count() {
        long uid = TokenContext.requireUserId();
        Map<String, Object> row = jdbc.queryForMap(
            "SELECT COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN status NOT IN ('refunded','exchanged','rejected','arbitrated','closed') " +
            "  THEN 1 ELSE 0 END),0) AS processing, " +
            "COALESCE(SUM(CASE WHEN status IN ('returning','exchanging') THEN 1 ELSE 0 END),0) AS todo " +
            "FROM t_refund WHERE user_id = ? AND is_deleted = 0", uid);
        return ApiResp.ok(row);
    }

    /** 售后详情：含完整进度时间线 */
    @GetMapping("/refund/{id}")
    public ApiResp<Map<String, Object>> detail(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> r = mustOwn(id, uid);
        decorate(r);
        r.put("items", orderItems((String) r.get("order_no")));
        r.put("timeline", buildTimeline(id));
        return ApiResp.ok(r);
    }

    /** 按订单号查该订单的售后单（订单详情页「查看售后进度」用） */
    @GetMapping("/refund/by-order/{orderNo}")
    public ApiResp<Map<String, Object>> byOrder(@PathVariable String orderNo) {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rs = jdbc.queryForList(
            "SELECT * FROM t_refund WHERE order_no = ? AND user_id = ? AND is_deleted = 0 ORDER BY id DESC LIMIT 1",
            orderNo, uid);
        if (rs.isEmpty()) return ApiResp.ok(null);
        Map<String, Object> r = rs.get(0);
        decorate(r);
        r.put("timeline", buildTimeline(((Number) r.get("id")).longValue()));
        return ApiResp.ok(r);
    }

    // =====================================================================
    // 用户操作
    // =====================================================================

    /** 回填寄回物流（退货退款 / 换货，状态 returning|exchanging） */
    @PostMapping("/refund/{id}/logistics")
    @Transactional
    public ApiResp<Void> fillLogistics(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> r = mustOwn(id, uid);
        String cur = (String) r.get("status");
        if (!RefundStatus.CAN_FILL_LOGISTICS.contains(cur)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前状态不可填写退货物流");
        }
        String company = str(body.get("logisticsCompany"));
        String no = str(body.get("logisticsNo"));
        if (company == null || company.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "请选择物流公司");
        if (no == null || no.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "请填写物流单号");

        String orderNo = (String) r.get("order_no");
        jdbc.update("UPDATE t_refund SET status = ?, logistics_company = ?, logistics_no = ?, " +
                "shipped_at = NOW(), updated_at = NOW() WHERE id = ?",
            RefundStatus.RETURNED, company, no, id);
        refundLog.logUser(id, orderNo, uid, RefundLogService.ACT_SHIP, cur, RefundStatus.RETURNED,
            "买家已寄回：" + company + " " + no);
        notifyMerchant(orderNo, "买家已寄回商品",
            "售后单 #" + id + "（订单 " + orderNo + "）买家已寄回：" + company + " " + no + "，请及时收货处理");
        return ApiResp.ok();
    }

    /** 换货：用户确认收到商家重发的商品 → 换货完成 */
    @PostMapping("/refund/{id}/receive")
    @Transactional
    public ApiResp<Void> confirmReceive(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> r = mustOwn(id, uid);
        String cur = (String) r.get("status");
        if (!RefundStatus.RESHIPPED.equals(cur)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前状态不可确认收货");
        }
        String orderNo = (String) r.get("order_no");
        String orig = (String) r.get("orig_status");
        jdbc.update("UPDATE t_refund SET status = ?, finished_at = NOW(), updated_at = NOW() WHERE id = ?",
            RefundStatus.EXCHANGED, id);
        // 换货不退钱，订单回到售后前的状态（一般是 done）
        jdbc.update("UPDATE t_order SET status = ? WHERE order_no = ?", orig == null ? "done" : orig, orderNo);
        refundLog.logUser(id, orderNo, uid, RefundLogService.ACT_RECEIVE, cur, RefundStatus.EXCHANGED,
            "买家已确认收到换货商品，售后完成");
        notifyMerchant(orderNo, "换货已完成", "售后单 #" + id + "（订单 " + orderNo + "）买家已确认收货，换货流程结束");
        return ApiResp.ok();
    }

    /** 撤销售后申请（商家尚未处理完时） */
    @PostMapping("/refund/{id}/cancel")
    @Transactional
    public ApiResp<Void> cancel(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> r = mustOwn(id, uid);
        String cur = (String) r.get("status");
        if (!RefundStatus.CAN_CANCEL.contains(cur)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前状态不可撤销");
        }
        String orderNo = (String) r.get("order_no");
        String orig = (String) r.get("orig_status");
        jdbc.update("UPDATE t_refund SET status = ?, finished_at = NOW(), updated_at = NOW() WHERE id = ?",
            RefundStatus.CLOSED, id);
        jdbc.update("UPDATE t_order SET status = ? WHERE order_no = ?", orig == null ? "paid" : orig, orderNo);
        refundLog.logUser(id, orderNo, uid, RefundLogService.ACT_CLOSE, cur, RefundStatus.CLOSED,
            "买家主动撤销了售后申请");
        notifyMerchant(orderNo, "买家撤销售后", "售后单 #" + id + "（订单 " + orderNo + "）买家已撤销申请");
        return ApiResp.ok();
    }

    /** 申请平台客服介入 */
    @PostMapping("/refund/{id}/appeal")
    @Transactional
    public ApiResp<Void> appeal(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        Map<String, Object> r = mustOwn(id, uid);
        String cur = (String) r.get("status");
        if (RefundStatus.APPEALING.equals(cur)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "平台正在处理中，请耐心等待");
        }
        if (!RefundStatus.CAN_APPEAL.contains(cur)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "当前状态不可申请平台介入");
        }
        String reason = str(body.get("reason"));
        if (reason == null || reason.isBlank()) throw new BizException(ErrorCode.PARAM_ERROR, "请填写申请介入的原因");

        String imagesJson = toJsonArray(body.get("images"));
        String orderNo = (String) r.get("order_no");
        jdbc.update("UPDATE t_refund SET status = ?, prev_status = ?, appeal_reason = ?, " +
                "appeal_images = ?, appealed_at = NOW(), updated_at = NOW() WHERE id = ?",
            RefundStatus.APPEALING, cur, reason, imagesJson, id);
        refundLog.logUser(id, orderNo, uid, RefundLogService.ACT_APPEAL, cur, RefundStatus.APPEALING,
            "买家申请平台介入：" + reason);
        notifyMerchant(orderNo, "买家申请平台介入",
            "售后单 #" + id + "（订单 " + orderNo + "）买家已申请平台客服介入，请配合处理");
        return ApiResp.ok();
    }

    // =====================================================================
    // 内部工具
    // =====================================================================

    /** 校验售后单归属当前用户，并返回整行 */
    private Map<String, Object> mustOwn(long id, long uid) {
        List<Map<String, Object>> rs = jdbc.queryForList(
            "SELECT r.*, s.name AS store_name FROM t_refund r " +
            "LEFT JOIN t_order o ON r.order_no = o.order_no " +
            "LEFT JOIN t_store s ON o.store_id = s.id " +
            "WHERE r.id = ? AND r.user_id = ? AND r.is_deleted = 0", id, uid);
        if (rs.isEmpty()) throw new BizException(ErrorCode.NO_PERMISSION, "售后单不存在");
        return rs.get(0);
    }

    /** 补充前端展示字段：类型/状态文案、可执行动作、凭证图数组 */
    private void decorate(Map<String, Object> r) {
        String type = (String) r.get("refund_type");
        String status = (String) r.get("status");
        r.put("typeText", RefundStatus.typeText(type));
        r.put("statusText", RefundStatus.statusText(status));
        r.put("canFillLogistics", RefundStatus.CAN_FILL_LOGISTICS.contains(status));
        r.put("canCancel", RefundStatus.CAN_CANCEL.contains(status));
        r.put("canAppeal", RefundStatus.CAN_APPEAL.contains(status));
        r.put("canReceive", RefundStatus.RESHIPPED.equals(status));
        r.put("isFinal", RefundStatus.isFinal(status));
        r.put("evidenceList", parseJsonArray(r.get("evidence_images")));
        r.put("appealImageList", parseJsonArray(r.get("appeal_images")));
    }

    private List<Map<String, Object>> orderItems(String orderNo) {
        if (orderNo == null) return List.of();
        return jdbc.queryForList(
            "SELECT oi.product_id, oi.product_name, oi.product_image, oi.price, oi.quantity " +
            "FROM t_order_item oi JOIN t_order o ON oi.order_id = o.id WHERE o.order_no = ?", orderNo);
    }

    /**
     * 组装进度时间线。
     * 优先用 t_refund_log；历史单（v8 之前产生、无流水）则从字段回溯出一条兜底时间线，
     * 免得老数据点进详情页是一片空白。
     */
    private List<Map<String, Object>> buildTimeline(long refundId) {
        List<Map<String, Object>> logs = refundLog.timeline(refundId);
        if (!logs.isEmpty()) return logs;
        List<Map<String, Object>> fallback = new ArrayList<>();
        Map<String, Object> r = jdbc.queryForMap(
            "SELECT status, reason, handle_note, created_at, handled_at, shipped_at, finished_at " +
            "FROM t_refund WHERE id = ?", refundId);
        fallback.add(node("apply", "提交售后申请", (String) r.get("reason"), r.get("created_at")));
        if (r.get("handled_at") != null) {
            fallback.add(node("approve", "商家已处理", (String) r.get("handle_note"), r.get("handled_at")));
        }
        if (r.get("shipped_at") != null) {
            fallback.add(node("ship", "买家已寄回", null, r.get("shipped_at")));
        }
        if (r.get("finished_at") != null) {
            fallback.add(node("done", RefundStatus.statusText((String) r.get("status")), null, r.get("finished_at")));
        }
        return fallback;
    }

    private Map<String, Object> node(String action, String title, String note, Object time) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", action);
        m.put("note", note == null || note.isBlank() ? title : note);
        m.put("actor_type", "system");
        m.put("created_at", time);
        return m;
    }

    /** 给订单所属商家发通知 */
    private void notifyMerchant(String orderNo, String title, String content) {
        try {
            Long mId = jdbc.queryForObject(
                "SELECT s.merchant_id FROM t_order o JOIN t_store s ON o.store_id = s.id WHERE o.order_no = ?",
                Long.class, orderNo);
            if (mId != null) notify.notify(mId, "refund", title, content, orderNo);
        } catch (Exception ignored) {
            // 通知失败不影响主流程
        }
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    @SuppressWarnings("unchecked")
    private static String toJsonArray(Object images) {
        if (!(images instanceof List<?> list) || list.isEmpty()) return null;
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<String> parseJsonArray(Object json) {
        if (json == null) return List.of();
        String s = String.valueOf(json);
        if (s.isBlank()) return List.of();
        try {
            return MAPPER.readValue(s, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }
}
