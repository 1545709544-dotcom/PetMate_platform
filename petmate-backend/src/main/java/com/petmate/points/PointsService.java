package com.petmate.points;

import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 鱼干（积分）核心服务。
 * 获取：签到 +5 / 发动态 +10 / 做任务 +20(首充资料) / 消费 1元=1鱼干，每日上限 50。
 * 抵扣：10 鱼干 = ¥1，单笔最多抵订单金额 10%。
 */
@Service
public class PointsService {

    /** 每日获取上限（仅正向获得受此约束，redeem 不受影响） */
    public static final int DAILY_LIMIT = 50;
    /** 10 鱼干 = ¥1 */
    public static final int REDEEM_RATE = 10;
    /** 单笔最多抵扣订单金额比例 */
    public static final BigDecimal REDEEM_MAX_RATIO = new BigDecimal("0.1");

    private final JdbcTemplate jdbc;

    public PointsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** TINYINT(1) 在 JDBC 下可能映射为 Boolean，统一按「真值」解析 */
    private static boolean asBool(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() != 0;
        return "1".equals(v.toString()) || "true".equalsIgnoreCase(v.toString());
    }

    // ============ 账户 ============

    private void ensureAccount(long uid) {
        jdbc.update("INSERT IGNORE INTO t_points_account (user_id) VALUES (?)", uid);
    }

    public int getBalance(long uid) {
        ensureAccount(uid);
        return jdbc.queryForObject(
                "SELECT balance FROM t_points_account WHERE user_id = ?", Integer.class, uid);
    }

    /** 今日已获得（用于每日上限统计，仅统计正向 change_value>0） */
    public int dailyEarned(long uid) {
        Integer v = jdbc.queryForObject(
                "SELECT COALESCE(SUM(change_value),0) FROM t_points_log " +
                        "WHERE user_id = ? AND change_value > 0 AND DATE(created_at) = CURDATE()",
                Integer.class, uid);
        return v == null ? 0 : v;
    }

    public Map<String, Object> getSummary(long uid) {
        ensureAccount(uid);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT balance, total_earned, total_spent FROM t_points_account WHERE user_id = ?", uid);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("balance", row.get("balance"));
        r.put("totalEarned", row.get("total_earned"));
        r.put("totalSpent", row.get("total_spent"));
        r.put("dailyEarned", dailyEarned(uid));
        r.put("dailyLimit", DAILY_LIMIT);
        return r;
    }

    // ============ 获得（带每日上限） ============

    /**
     * 发放鱼干，自动应用每日上限：返回实际发放数（可能被截断）。
     * changeType: signin/post/task/consume
     */
    @Transactional
    public int earn(long uid, String changeType, int reward, String remark, Long refId) {
        if (reward <= 0) return 0;
        ensureAccount(uid);
        int remaining = DAILY_LIMIT - dailyEarned(uid);
        if (remaining <= 0) return 0;
        int granted = Math.min(reward, remaining);
        int balanceAfter = jdbc.queryForObject(
                "SELECT balance FROM t_points_account WHERE user_id = ?", Integer.class, uid) + granted;
        jdbc.update(
                "UPDATE t_points_account SET balance = balance + ?, total_earned = total_earned + ? WHERE user_id = ?",
                granted, granted, uid);
        jdbc.update(
                "INSERT INTO t_points_log (user_id, change_type, change_value, balance_after, remark, ref_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                uid, changeType, granted, balanceAfter, remark, refId);
        return granted;
    }

    /**
     * 发放鱼干（不受每日上限约束）。用于活动/排行等「奖励」类发放，避免被日常 50 上限截断。
     * 其余语义与 earn 一致。
     */
    @Transactional
    public int earnUnlimited(long uid, String changeType, int reward, String remark, Long refId) {
        if (reward <= 0) return 0;
        ensureAccount(uid);
        int balanceAfter = jdbc.queryForObject(
                "SELECT balance FROM t_points_account WHERE user_id = ?", Integer.class, uid) + reward;
        jdbc.update(
                "UPDATE t_points_account SET balance = balance + ?, total_earned = total_earned + ? WHERE user_id = ?",
                reward, reward, uid);
        jdbc.update(
                "INSERT INTO t_points_log (user_id, change_type, change_value, balance_after, remark, ref_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                uid, changeType, reward, balanceAfter, remark, refId);
        return reward;
    }

    // ============ 任务 / 签到 ============

    public Map<String, Object> completeTask(long uid, String taskCode) {
        Map<String, Object> def = jdbc.queryForMap(
                "SELECT id, code, title, reward, daily FROM t_task_def WHERE code = ? AND enabled = 1", taskCode);
        boolean daily = asBool(def.get("daily"));
        String title = (String) def.get("title");
        int reward = ((Number) def.get("reward")).intValue();

        // 去重：每日任务按天，一次性任务按历史
        boolean already;
        if (daily) {
            Integer c = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_task_record WHERE user_id = ? AND task_code = ? AND DATE(created_at) = CURDATE()",
                    Integer.class, uid, taskCode);
            already = c != null && c > 0;
        } else {
            Integer c = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_task_record WHERE user_id = ? AND task_code = ?",
                    Integer.class, uid, taskCode);
            already = c != null && c > 0;
        }
        if (already) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("done", true);
            r.put("alreadyDone", true);
            r.put("granted", 0);
            r.put("balance", getBalance(uid));
            r.put("taskCode", taskCode);
            return r;
        }

        int granted = earn(uid, "task", reward, "任务：" + title, null);
        jdbc.update(
                "INSERT INTO t_task_record (user_id, task_code) VALUES (?, ?)", uid, taskCode);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("done", true);
        r.put("alreadyDone", false);
        r.put("granted", granted);
        r.put("balance", getBalance(uid));
        r.put("dailyLimitReached", granted < reward);
        r.put("taskCode", taskCode);
        return r;
    }

    /** 任务列表（含当前用户完成状态） */
    public List<Map<String, Object>> listTasks(long uid) {
        List<Map<String, Object>> defs = jdbc.queryForList(
                "SELECT id, code, title, description, reward, daily, sort FROM t_task_def " +
                        "WHERE enabled = 1 ORDER BY sort ASC");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> d : defs) {
            String code = (String) d.get("code");
            boolean daily = asBool(d.get("daily"));
            boolean completed;
            if (daily) {
                Integer c = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM t_task_record WHERE user_id = ? AND task_code = ? AND DATE(created_at) = CURDATE()",
                        Integer.class, uid, code);
                completed = c != null && c > 0;
            } else {
                Integer c = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM t_task_record WHERE user_id = ? AND task_code = ?",
                        Integer.class, uid, code);
                completed = c != null && c > 0;
            }
            Map<String, Object> m = new LinkedHashMap<>(d);
            m.put("completed", completed);
            result.add(m);
        }
        return result;
    }

    // ============ 抵扣 ============

    /**
     * 下单时抵扣鱼干。
     * @param pointsUse 用户期望使用的鱼干数（后端会按余额与 10% 上限截断）
     * @param payAmount 订单应付金额（抵扣前）
     * @return 实际抵扣金额(¥) 与 实际消耗鱼干数
     */
    @Transactional
    public Map<String, Object> redeem(long uid, int pointsUse, BigDecimal payAmount) {
        ensureAccount(uid);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("discount", BigDecimal.ZERO);
        r.put("pointsUsed", 0);
        if (pointsUse <= 0 || payAmount == null || payAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return r;
        }
        int balance = getBalance(uid);
        if (balance <= 0) return r;

        // 上限：订单金额的 10%
        BigDecimal maxDiscount = payAmount.multiply(REDEEM_MAX_RATIO).setScale(2, RoundingMode.DOWN);
        int maxByOrder = maxDiscount.multiply(BigDecimal.valueOf(REDEEM_RATE)).setScale(0, RoundingMode.DOWN).intValue();
        int used = Math.min(pointsUse, Math.min(balance, maxByOrder));
        if (used <= 0) return r;

        BigDecimal discount = new BigDecimal(used).divide(BigDecimal.valueOf(REDEEM_RATE)).setScale(2, RoundingMode.DOWN);
        // 二次保障：抵扣不能超过应付
        if (discount.compareTo(payAmount) > 0) {
            discount = payAmount.setScale(2, RoundingMode.DOWN);
            used = discount.multiply(BigDecimal.valueOf(REDEEM_RATE)).setScale(0, RoundingMode.DOWN).intValue();
        }
        int balanceAfter = balance - used;
        jdbc.update(
                "UPDATE t_points_account SET balance = balance - ?, total_spent = total_spent + ? WHERE user_id = ?",
                used, used, uid);
        jdbc.update(
                "INSERT INTO t_points_log (user_id, change_type, change_value, balance_after, remark) " +
                        "VALUES (?, 'redeem', ?, ?, '订单抵扣')",
                uid, -used, balanceAfter);

        r.put("discount", discount);
        r.put("pointsUsed", used);
        return r;
    }

    /**
     * 退回鱼干（订单取消 / 超时 / 退款完成时调用）。
     * 与 redeem 对应，把下单时抵扣消耗掉的鱼干加回账户；不受每日上限约束（退回不是获得奖励）。
     */
    @Transactional
    public int refund(long uid, int pointsUsed, String remark, Long refId) {
        if (pointsUsed <= 0) return 0;
        ensureAccount(uid);
        int balanceAfter = getBalance(uid) + pointsUsed;
        jdbc.update(
                "UPDATE t_points_account SET balance = balance + ?, total_earned = total_earned + ? WHERE user_id = ?",
                pointsUsed, pointsUsed, uid);
        jdbc.update(
                "INSERT INTO t_points_log (user_id, change_type, change_value, balance_after, remark, ref_id) " +
                        "VALUES (?, 'refund', ?, ?, ?, ?)",
                uid, pointsUsed, balanceAfter, remark, refId);
        return pointsUsed;
    }
}
