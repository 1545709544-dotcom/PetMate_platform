package com.petmate.user;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.common.PageResp;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户关注 + 个人统计 + 活动流。
 * 涉及三块独立但都属于当前用户视角的接口，合并放一个 Controller 避免再分多个文件。
 */
@RestController
public class FollowController {

    private final JdbcTemplate jdbc;
    private final com.petmate.common.NotifyService notify;

    public FollowController(JdbcTemplate jdbc, com.petmate.common.NotifyService notify) {
        this.jdbc = jdbc;
        this.notify = notify;
    }

    /** 当前用户统计 */
    @GetMapping("/user/stats")
    public ApiResp<Map<String, Object>> stats() {
        long uid = TokenContext.requireUserId();
        Map<String, Object> out = new LinkedHashMap<>();
        Long followers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_follow WHERE followee_id = ?", Long.class, uid);
        Long following = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_follow WHERE follower_id = ?", Long.class, uid);
        Long posts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_post WHERE user_id = ? AND is_deleted = 0", Long.class, uid);
        Long pets = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_pet WHERE user_id = ? AND is_deleted = 0", Long.class, uid);
        Long likes = jdbc.queryForObject(
            "SELECT COALESCE(SUM(like_count), 0) FROM t_post WHERE user_id = ? AND is_deleted = 0",
            Long.class, uid);
        // 收藏总数：跨商品/帖子/文章/服务，统一按 is_deleted=0 过滤避免与 uk_user_target 冲突
        Long favorites = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_favorite WHERE user_id = ? AND is_deleted = 0", Long.class, uid);
        out.put("followerCount", followers == null ? 0 : followers);
        out.put("followingCount", following == null ? 0 : following);
        out.put("postCount", posts == null ? 0 : posts);
        out.put("petCount", pets == null ? 0 : pets);
        out.put("likeCount", likes == null ? 0 : likes);
        out.put("favoriteCount", favorites == null ? 0 : favorites);
        return ApiResp.ok(out);
    }

    /** 当前用户活动流（帖子 + 商品评价） */
    @GetMapping("/user/activities")
    public ApiResp<PageResp<Map<String, Object>>> activities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long uid = TokenContext.requireUserId();
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        // 帖子 + 商品评价 union,外部按时间倒序分页
        // 拆为 app 层 merge:任意一张表查询失败只丢那部分,不会让整接口 500
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        try {
            rows.addAll(jdbc.queryForList(
                "SELECT 'post' AS type, id AS ref_id, " +
                "       SUBSTRING(content, 1, 80) AS title, " +
                "       SUBSTRING(content, 1, 120) AS description, " +
                "       created_at AS activity_time " +
                "FROM t_post WHERE user_id = ? AND is_deleted = 0",
                uid));
        } catch (Exception ignore) {
            // post 表查询失败时仅记录,不影响 review 部分
        }
        try {
            rows.addAll(jdbc.queryForList(
                "SELECT 'review' AS type, id AS ref_id, " +
                "       CONCAT('评价了商品 #', product_id) AS title, " +
                "       IFNULL(SUBSTRING(content, 1, 120), '') AS description, " +
                "       created_at AS activity_time " +
                "FROM t_product_review WHERE user_id = ? AND is_deleted = 0",
                uid));
        } catch (Exception ignore) {
        }

        // 应用层排序 + 分页
        rows.sort((a, b) -> {
            Object ta = a.get("activity_time");
            Object tb = b.get("activity_time");
            if (ta == null && tb == null) return 0;
            if (ta == null) return 1;
            if (tb == null) return -1;
            if (ta instanceof java.sql.Timestamp && tb instanceof java.sql.Timestamp) {
                return ((java.sql.Timestamp) tb).compareTo((java.sql.Timestamp) ta);
            }
            return tb.toString().compareTo(ta.toString());
        });
        int from = Math.min(offset, rows.size());
        int to = Math.min(from + safeSize, rows.size());
        List<Map<String, Object>> pageRows = rows.subList(from, to);

        long total = rows.size();
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> r : pageRows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", r.get("type"));
            m.put("refId", r.get("ref_id"));
            m.put("title", r.get("title"));
            m.put("description", r.get("description"));
            m.put("time", r.get("activity_time"));
            out.add(m);
        }
        return ApiResp.ok(PageResp.of(out, total, safePage, safeSize));
    }

    /** 我关注的人（关注列表） */
    @GetMapping("/user/following")
    public ApiResp<PageResp<Map<String, Object>>> following(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listFollow(page, size, false);
    }

    /** 我的粉丝（粉丝列表） */
    @GetMapping("/user/followers")
    public ApiResp<PageResp<Map<String, Object>>> followers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return listFollow(page, size, true);
    }

    /**
     * 关注/粉丝列表共用：返回对方用户摘要 + 当前用户是否关注对方(following)。
     * fans=true 查「关注我的人」(followee_id=我, 对方是 follower_id)；
     * fans=false 查「我关注的人」(follower_id=我, 对方是 followee_id)。
     */
    private ApiResp<PageResp<Map<String, Object>>> listFollow(int page, int size, boolean fans) {
        long uid = TokenContext.requireUserId();
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;

        String countSql, listSql;
        if (fans) {
            countSql = "SELECT COUNT(*) FROM t_follow WHERE followee_id = ?";
            listSql =
                "SELECT u.id, u.nickname, u.avatar, u.bio, u.vip_level AS vipLevel, "
                    + "       CASE WHEN mf.id IS NOT NULL THEN 1 ELSE 0 END AS following "
                    + "FROM t_follow f "
                    + "JOIN t_user u ON u.id = f.follower_id "
                    + "LEFT JOIN t_follow mf ON mf.follower_id = ? AND mf.followee_id = u.id "
                    + "WHERE f.followee_id = ? AND u.is_deleted = 0 "
                    + "ORDER BY f.id DESC LIMIT ? OFFSET ?";
        } else {
            countSql = "SELECT COUNT(*) FROM t_follow WHERE follower_id = ?";
            listSql =
                "SELECT u.id, u.nickname, u.avatar, u.bio, u.vip_level AS vipLevel, "
                    + "       CASE WHEN mf.id IS NOT NULL THEN 1 ELSE 0 END AS following "
                    + "FROM t_follow f "
                    + "JOIN t_user u ON u.id = f.followee_id "
                    + "LEFT JOIN t_follow mf ON mf.follower_id = ? AND mf.followee_id = u.id "
                    + "WHERE f.follower_id = ? AND u.is_deleted = 0 "
                    + "ORDER BY f.id DESC LIMIT ? OFFSET ?";
        }

        Long total = jdbc.queryForObject(countSql, Long.class, uid);
        if (total == null) total = 0L;

        List<Map<String, Object>> rows = jdbc.queryForList(listSql, uid, uid, safeSize, offset);
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("nickname", r.get("nickname"));
            m.put("avatar", r.get("avatar"));
            m.put("bio", r.get("bio"));
            Object vl = r.get("vipLevel");
            m.put("vipLevel", vl == null ? 0 : ((Number) vl).intValue());
            Object fg = r.get("following");
            m.put("following", fg != null && ((Number) fg).intValue() == 1);
            out.add(m);
        }
        return ApiResp.ok(PageResp.of(out, total, safePage, safeSize));
    }

    /** 关注/取关 toggle */
    @PostMapping("/user/follow")
    public ApiResp<Map<String, Object>> toggle(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        long followeeId = ((Number) body.get("followeeId")).longValue();
        if (followeeId == uid) throw new BizException(ErrorCode.PARAM_ERROR, "不能关注自己");

        // 检查被关注者存在
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_user WHERE id = ? AND is_deleted = 0", Integer.class, followeeId);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "用户不存在");

        List<Map<String, Object>> existing = jdbc.queryForList(
            "SELECT id FROM t_follow WHERE follower_id = ? AND followee_id = ?", uid, followeeId);
        boolean following;
        if (existing.isEmpty()) {
            jdbc.update(
                "INSERT INTO t_follow (follower_id, followee_id) VALUES (?, ?)", uid, followeeId);
            following = true;
            // 通知被关注者
            String followerName = jdbc.queryForObject(
                "SELECT nickname FROM t_user WHERE id = ?", String.class, uid);
            notify.notifyUser(followeeId, "social", "新的关注",
                (followerName == null ? "有人" : followerName) + " 关注了你", "user:" + uid);
        } else {
            jdbc.update(
                "DELETE FROM t_follow WHERE follower_id = ? AND followee_id = ?", uid, followeeId);
            following = false;
        }
        return ApiResp.ok(Map.of("following", following, "followeeId", followeeId));
    }

    /** 是否已关注 userId */
    @GetMapping("/user/follow/status")
    public ApiResp<Map<String, Object>> status(@RequestParam long userId) {
        long uid = TokenContext.requireUserId();
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_follow WHERE follower_id = ? AND followee_id = ?",
            Integer.class, uid, userId);
        boolean following = cnt != null && cnt > 0;
        return ApiResp.ok(Map.of("following", following, "userId", userId));
    }
}