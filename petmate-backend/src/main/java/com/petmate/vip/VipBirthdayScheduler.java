package com.petmate.vip;

import com.petmate.common.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * VIP 生日礼包自动发放（落地"生日礼包"权益）。
 *
 * 规则：
 *   - 每天扫一次 t_user 表里 birthday = 今天、vip_level>=1 且未过期 的用户
 *   - 给每人发一张"VIP 生日专享券"（面额 20 元，无门槛，有效期 30 天）
 *   - 幂等：t_user_coupon 加 unique index (user_id, coupon_id, claim_year)
 *     当天重复扫描/跨天不会重复发；同一用户每年最多 1 张
 *   - 发送站内信提醒（"您的生日礼包已到账，30 天内有效"）
 *
 * 演示态容错：
 *   - t_user_coupon 没有 claim_year 字段 → 启动时通过 ensureSchema() 加列；
 *     unique index 缺失则补建，避免迁移脚本耦合
 */
@Component
public class VipBirthdayScheduler {

    private static final Logger log = LoggerFactory.getLogger(VipBirthdayScheduler.class);
    private static final long BIRTHDAY_COUPON_ID = 9001L;  // 生日专享券固定 ID
    private static final String BIRTHDAY_COUPON_NAME = "VIP 生日专享券（20 元）";
    private static final int AMOUNT = 20;
    private static final int VALID_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final NotifyService notify;

    public VipBirthdayScheduler(JdbcTemplate jdbc, NotifyService notify) {
        this.jdbc = jdbc;
        this.notify = notify;
    }

    /**
     * 每小时扫一次（演示态不需要更密；节日 0 点高峰也能覆盖）
     */
    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 30 * 1000)
    public void sweep() {
        try {
            ensureSchema();
            ensureCouponTemplate();

            LocalDate today = LocalDate.now();
            int year = today.getYear();

            // 今天生日的活跃 VIP 用户（vip_level>=1 且未过期）
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, nickname FROM t_user " +
                "WHERE is_deleted = 0 AND status = 0 " +
                "AND vip_level >= 1 " +
                "AND vip_expire_at IS NOT NULL AND vip_expire_at > NOW() " +
                "AND birthday IS NOT NULL " +
                "AND DATE_FORMAT(birthday, '%m-%d') = ?",
                String.format("%02d-%02d", today.getMonthValue(), today.getDayOfMonth()));

            if (rows.isEmpty()) return;
            log.info("[vip-birthday] {} VIP user(s) with birthday today", rows.size());

            int granted = 0;
            int skipped = 0;
            for (Map<String, Object> u : rows) {
                long uid = ((Number) u.get("id")).longValue();
                String nickname = (String) u.get("nickname");
                if (grantOne(uid, year)) {
                    granted++;
                    notify.notifyUser(uid, "vip_birthday", "🎂 生日快乐！生日礼包已到账",
                        String.format("%s，PetMate 祝您生日快乐！生日专享 20 元券已自动放入您的账户，「优惠券中心」可查看，%d 天内有效。",
                            nickname == null || nickname.isEmpty() ? "亲爱的会员" : nickname, VALID_DAYS),
                        "coupon:" + BIRTHDAY_COUPON_ID);
                } else {
                    skipped++;
                }
            }
            log.info("[vip-birthday] granted={}, skipped(already)={}", granted, skipped);
        } catch (Exception e) {
            log.warn("[vip-birthday] sweep error", e);
        }
    }

    private boolean grantOne(long uid, int year) {
        try {
            int n = jdbc.update(
                "INSERT INTO t_user_coupon (user_id, coupon_id, status, claim_at, claim_year) " +
                "VALUES (?, ?, 'available', NOW(), ?)",
                uid, BIRTHDAY_COUPON_ID, year);
            return n == 1;
        } catch (DuplicateKeyException dup) {
            // unique (user_id, coupon_id, claim_year) 命中 → 已发过，跳过
            return false;
        }
    }

    /** 兼容老库：补 claim_year 列与唯一索引 */
    private void ensureSchema() {
        try {
            // 检查列是否存在
            Integer hasCol = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns " +
                "WHERE table_schema = DATABASE() AND table_name = 't_user_coupon' AND column_name = 'claim_year'",
                Integer.class);
            if (hasCol == null || hasCol == 0) {
                jdbc.execute("ALTER TABLE t_user_coupon ADD COLUMN claim_year INT NOT NULL DEFAULT 0 AFTER claim_at");
                log.info("[vip-birthday] added claim_year column to t_user_coupon");
            }
            // 唯一索引
            Integer hasIdx = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics " +
                "WHERE table_schema = DATABASE() AND table_name = 't_user_coupon' " +
                "AND index_name = 'uk_user_coupon_year'",
                Integer.class);
            if (hasIdx == null || hasIdx == 0) {
                try {
                    jdbc.execute("CREATE UNIQUE INDEX uk_user_coupon_year ON t_user_coupon (user_id, coupon_id, claim_year)");
                    log.info("[vip-birthday] created uk_user_coupon_year index");
                } catch (Exception dupIdx) {
                    // 老数据有重复（同一用户多次领同一 coupon），跳过
                    log.warn("[vip-birthday] skip uk index creation (data conflict): {}", dupIdx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[vip-birthday] ensureSchema warn: {}", e.getMessage());
        }
    }

    /** 兜底种入生日券模板（管理员没建也保证能发） */
    private void ensureCouponTemplate() {
        try {
            Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_coupon WHERE id = ?", Integer.class, BIRTHDAY_COUPON_ID);
            if (exists != null && exists > 0) return;
            jdbc.update(
                "INSERT INTO t_coupon (id, name, type, threshold, amount, valid_start, valid_end, status, is_deleted) " +
                "VALUES (?, ?, 'reduction', 0, ?, NOW(), DATE_ADD(NOW(), INTERVAL 10 YEAR), 1, 0)",
                BIRTHDAY_COUPON_ID, BIRTHDAY_COUPON_NAME, AMOUNT);
            log.info("[vip-birthday] seeded birthday coupon template id={}", BIRTHDAY_COUPON_ID);
        } catch (DuplicateKeyException dup) {
            // 并发启动时另一个实例先建了，无害
        } catch (Exception e) {
            log.warn("[vip-birthday] ensureCouponTemplate warn: {}", e.getMessage());
        }
    }
}