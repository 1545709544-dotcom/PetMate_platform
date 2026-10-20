package com.petmate.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 商家消息通知中心：订单/评价/退款/系统公告等事件的统一写入。
 */
@Service
public class NotifyService {
    private final JdbcTemplate jdbc;
    public NotifyService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void notify(long merchantId, String type, String title, String content, String relatedId) {
        if (merchantId <= 0) return;
        jdbc.update(
            "INSERT INTO t_merchant_notification (merchant_id, type, title, content, related_id) VALUES (?,?,?,?,?)",
            merchantId, type, title, content, relatedId);
    }

    /**
     * 用户侧消息通知（前台"我的消息"）：向某个用户推送一条站内信。
     * type 取值：warning（违规警告）/ system（系统通知）等。
     */
    public void notifyUser(long userId, String type, String title, String content, String relatedId) {
        if (userId <= 0) return;
        jdbc.update(
            "INSERT INTO t_user_notification (user_id, type, title, content, related_id) VALUES (?,?,?,?,?)",
            userId, type, title, content, relatedId);
    }
}
