package com.petmate.community;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.CityUtil;
import com.petmate.common.ErrorCode;
import com.petmate.common.PageResp;
import com.petmate.points.PointsService;
import java.math.BigDecimal;
import com.petmate.security.TokenContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class CommunityController {

    private final JdbcTemplate jdbc;
    private final SensitiveWordService sensitive;
    private final ObjectMapper objectMapper;
    private final PointsService pointsService;
    private final com.petmate.common.NotifyService notify;

    public CommunityController(JdbcTemplate jdbc, SensitiveWordService sensitive, ObjectMapper objectMapper, PointsService pointsService, com.petmate.common.NotifyService notify) {
        this.jdbc = jdbc;
        this.sensitive = sensitive;
        this.objectMapper = objectMapper;
        this.pointsService = pointsService;
        this.notify = notify;
    }

    // ============= 帖子 feed =============

    @GetMapping("/community/feed")
    public ApiResp<PageResp<Map<String, Object>>> feed(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String city) {
        long uid = optionalUserId();
        // 同城筛选走精确匹配，入参必须与落库口径一致地归一化（详见 CityUtil），
        // 否则「上海市/上海市/徐汇区」这类资料页写入的整串永远匹配不到帖子的「上海」。
        String normCity = CityUtil.normalize(city);
        boolean hasCity = normCity != null;
        String cityFilter = hasCity ? " AND city = ?" : "";
        Object[] cityArg = hasCity ? new Object[]{ normCity } : new Object[]{};
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved'" + cityFilter,
            Long.class, cityArg);
        List<Object> feedArgs = new ArrayList<>(Arrays.asList(cityArg));
        feedArgs.add(size);
        feedArgs.add((page - 1) * size);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, user_id, user_nickname, user_avatar, content, images, video_url, " +
            "topic_id, topic_name, like_count, comment_count, collect_count, visible, created_at, city " +
            "FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved'" + cityFilter +
            " ORDER BY id DESC LIMIT ? OFFSET ?", feedArgs.toArray());
        // 当前用户是否点赞 / 收藏
        Set<Long> likedIds = likedSet(uid, rows);
        Set<Long> collectedIds = collectedSet(uid, rows);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = normalizePost(r);
            long pid = ((Number) r.get("id")).longValue();
            m.put("liked", likedIds.contains(pid));
            m.put("collected", collectedIds.contains(pid));
            out.add(m);
        }
        return ApiResp.ok(PageResp.of(out, total == null ? 0 : total, page, size));
    }

    // ============= 首页「为你精选」策展接口 =============
    // 与 /community/feed（按最新）不同：本接口按「互动量 / 阅读量」做真实策展排序，
    // 让首页「为你精选」名副其实；seed 决定轮换起点，供前端「换一批」拉取不同一批内容。

    @GetMapping("/community/for-you")
    public ApiResp<Map<String, Object>> forYou(@RequestParam(defaultValue = "0") int seed) {
        long uid = optionalUserId();

        // 社区精选：按互动量(点赞*2 + 评论 + 收藏)降序，互动量相同按最新
        List<Map<String, Object>> postRows = jdbc.queryForList(
            "SELECT id, user_id, user_nickname, user_avatar, content, images, video_url, " +
            "topic_id, topic_name, like_count, comment_count, collect_count, visible, created_at, city " +
            "FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved' " +
            "ORDER BY (like_count * 2 + comment_count + collect_count) DESC, id DESC LIMIT 12");
        Set<Long> likedIds = likedSet(uid, postRows);
        Set<Long> collectedIds = collectedSet(uid, postRows);
        List<Map<String, Object>> posts = new ArrayList<>();
        for (Map<String, Object> r : postRows) {
            Map<String, Object> m = normalizePost(r);
            long pid = ((Number) r.get("id")).longValue();
            m.put("liked", likedIds.contains(pid));
            m.put("collected", collectedIds.contains(pid));
            posts.add(m);
        }

        // 科普精选：按阅读量降序
        List<Map<String, Object>> articleRows = jdbc.queryForList(
            "SELECT id, category_id, title, content, cover, author, author_id, view_count, like_count, seo_url, created_at " +
            "FROM t_article WHERE is_deleted = 0 " +
            "ORDER BY view_count DESC, id DESC LIMIT 12");
        List<Map<String, Object>> articles = new ArrayList<>();
        for (Map<String, Object> m : articleRows) {
            m.put("createdAt", m.get("created_at") == null ? null : m.get("created_at").toString());
            articles.add(m);
        }

        // 按 seed 轮换起点，使「换一批」每次拿到不同排序（无内容时不做轮换）
        if (!posts.isEmpty() && seed > 0) posts = rotate(posts, seed % posts.size());
        if (!articles.isEmpty() && seed > 0) articles = rotate(articles, seed % articles.size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("posts", posts);
        out.put("articles", articles);
        out.put("seed", seed);
        return ApiResp.ok(out);
    }

    private static <T> List<T> rotate(List<T> list, int k) {
        if (list.isEmpty() || k <= 0) return list;
        k = k % list.size();
        List<T> res = new ArrayList<>(list.size());
        res.addAll(list.subList(k, list.size()));
        res.addAll(list.subList(0, k));
        return res;
    }

    // ============= 社区搜索（按内容 / 话题名模糊匹配） =============

    @GetMapping("/community/search")
    public ApiResp<PageResp<Map<String, Object>>> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        long uid = optionalUserId();
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            return ApiResp.ok(PageResp.of(new ArrayList<>(), 0, page, size));
        }
        // 防 SQL 注入：统一用 ? 占位符，且对模糊匹配做转义（把 % 与 _ 当普通字符处理）。
        // 注意：不能用反斜杠做 ESCAPE 字符，因为 SQL 里 ESCAPE '\' 的 \' 会被当成转义引号而破坏语法；
        // 这里改用 '!' 作为转义字符，把 !、%、_ 分别转义为 !!、!%、!_。
        String escaped = kw.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        String like = "%" + escaped + "%";
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved' " +
            "AND (content LIKE ? ESCAPE '!' OR topic_name LIKE ? ESCAPE '!')", Long.class, like, like);
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, user_id, user_nickname, user_avatar, content, images, video_url, " +
            "topic_id, topic_name, like_count, comment_count, collect_count, visible, created_at " +
            "FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved' " +
            "AND (content LIKE ? ESCAPE '!' OR topic_name LIKE ? ESCAPE '!') " +
            "ORDER BY id DESC LIMIT ? OFFSET ?", like, like, safeSize, offset);
        Set<Long> likedIds = likedSet(uid, rows);
        Set<Long> collectedIds = collectedSet(uid, rows);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = normalizePost(r);
            long pid = ((Number) r.get("id")).longValue();
            m.put("liked", likedIds.contains(pid));
            m.put("collected", collectedIds.contains(pid));
            out.add(m);
        }
        return ApiResp.ok(PageResp.of(out, total == null ? 0 : total, safePage, safeSize));
    }

    @GetMapping("/post/{id}")
    public ApiResp<Map<String, Object>> postDetail(@PathVariable long id) {
        long uid = optionalUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, user_id, user_nickname, user_avatar, content, images, video_url, " +
            "topic_id, topic_name, like_count, comment_count, collect_count, visible, created_at " +
            "FROM t_post WHERE id = ? AND is_deleted = 0", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "帖子不存在");
        Set<Long> liked = likedSet(uid, rows);
        Set<Long> collected = collectedSet(uid, rows);
        Map<String, Object> m = normalizePost(rows.get(0));
        m.put("liked", liked.contains(id));
        m.put("collected", collected.contains(id));
        return ApiResp.ok(m);
    }

    @PostMapping("/post/{id}/collect")
    public ApiResp<Map<String, Object>> collect(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_post WHERE id = ? AND is_deleted = 0", Integer.class, id);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "帖子不存在");
        List<Map<String, Object>> exist = jdbc.queryForList(
            "SELECT id FROM t_post_collect WHERE post_id = ? AND user_id = ?", id, uid);
        boolean collected;
        if (exist.isEmpty()) {
            jdbc.update("INSERT INTO t_post_collect (post_id, user_id) VALUES (?, ?)", id, uid);
            jdbc.update("UPDATE t_post SET collect_count = collect_count + 1 WHERE id = ?", id);
            collected = true;
        } else {
            jdbc.update("DELETE FROM t_post_collect WHERE id = ?", exist.get(0).get("id"));
            jdbc.update("UPDATE t_post SET collect_count = GREATEST(collect_count - 1, 0) WHERE id = ?", id);
            collected = false;
        }
        long collectCount = jdbc.queryForObject("SELECT collect_count FROM t_post WHERE id = ?", Long.class, id);
        return ApiResp.ok(Map.of("collected", collected, "collectCount", collectCount));
    }

    @PostMapping("/post")
    public ApiResp<Map<String, Object>> publish(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        // ===== 实名认证门禁（源头拦截）=====
        // 未实名用户禁止发布动态。即便前端路由/组件门禁被绕过，后端也拒绝落库，
        // 与 /user/me 返回的 verified 状态保持一致。
        Integer verified = jdbc.queryForObject("SELECT verified FROM t_user WHERE id = ?", Integer.class, uid);
        if (verified == null || verified != 1) {
            throw new BizException(ErrorCode.NEED_REALNAME);
        }
        String content = (String) body.get("content");
        if (content == null || content.trim().isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "内容不能为空");
        // 落库前归一化，保证同城筛选口径统一
        String city = CityUtil.normalize((String) body.get("city"));
        if (sensitive.hasBlock(content)) throw new BizException(ErrorCode.SENSITIVE_WORD);
        Long topicId = body.get("topicId") == null ? null : ((Number) body.get("topicId")).longValue();
        String topicName = null;
        if (topicId != null) {
            List<String> t = jdbc.queryForList("SELECT name FROM t_topic WHERE id = ?", String.class, topicId);
            if (!t.isEmpty()) topicName = t.get(0);
        }
        String images = null;
        Object imgObj = body.get("images");
        if (imgObj != null) {
            try {
                images = objectMapper.writeValueAsString(imgObj);
            } catch (Exception e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "图片格式错误");
            }
        }
        // 可见性（public / follow / private，默认 public）
        String visible = "public";
        Object visObj = body.get("visible");
        if (visObj != null) {
            String v = visObj.toString();
            if (v.equals("public") || v.equals("follow") || v.equals("private")) visible = v;
        }
        // 用户信息
        Map<String, Object> u = jdbc.queryForMap(
            "SELECT nickname, avatar FROM t_user WHERE id = ?", uid);
        jdbc.update(
            "INSERT INTO t_post (user_id, user_nickname, user_avatar, content, images, video_url, topic_id, topic_name, visible, city, audit_status) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'approved')",
            uid, u.get("nickname"), u.get("avatar"), content, images, body.get("videoUrl"), topicId, topicName, visible, city);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        // 发动态奖励鱼干（+10，受每日上限约束）
        int pointsGranted = pointsService.earn(uid, "post", 10, "发布动态", id);
        // 返回完整 normalize 后的帖子，便于前端乐观插入 feed
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, user_id, user_nickname, user_avatar, content, images, video_url, " +
            "topic_id, topic_name, like_count, comment_count, collect_count, visible, created_at, city " +
            "FROM t_post WHERE id = ?", id);
        Map<String, Object> post = normalizePost(rows.get(0));
        post.put("liked", false);
        post.put("collected", false);
        post.put("auditStatus", "approved");
        post.put("pointsGranted", pointsGranted);
        return ApiResp.ok(post);
    }

    @PostMapping("/post/{id}/like")
    public ApiResp<Map<String, Object>> like(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        // 存在性
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM t_post WHERE id = ? AND is_deleted = 0", Integer.class, id);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "帖子不存在");
        // 切换
        List<Map<String, Object>> exist = jdbc.queryForList(
            "SELECT id FROM t_post_like WHERE post_id = ? AND user_id = ?", id, uid);
        boolean liked;
        if (exist.isEmpty()) {
            jdbc.update("INSERT INTO t_post_like (post_id, user_id) VALUES (?, ?)", id, uid);
            jdbc.update("UPDATE t_post SET like_count = like_count + 1 WHERE id = ?", id);
            liked = true;
            // 通知帖主（不通知自己）
            Map<String, Object> post = jdbc.queryForMap("SELECT user_id, content FROM t_post WHERE id = ?", id);
            long authorId = ((Number) post.get("user_id")).longValue();
            if (authorId != uid) {
                String title = post.get("content") == null ? "" : post.get("content").toString();
                if (title.length() > 20) title = title.substring(0, 20) + "…";
                notify.notifyUser(authorId, "social", "有人赞了你的帖子",
                    "你的帖子《" + title + "》收到了一个赞", "post:" + id);
            }
        } else {
            jdbc.update("DELETE FROM t_post_like WHERE id = ?", exist.get(0).get("id"));
            jdbc.update("UPDATE t_post SET like_count = GREATEST(like_count - 1, 0) WHERE id = ?", id);
            liked = false;
        }
        long likeCount = jdbc.queryForObject("SELECT like_count FROM t_post WHERE id = ?", Long.class, id);
        return ApiResp.ok(Map.of("liked", liked, "likeCount", likeCount));
    }

    @DeleteMapping("/post/{id}")
    public ApiResp<Void> deletePost(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT user_id, is_deleted FROM t_post WHERE id = ?", id);
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "帖子不存在");
        Map<String, Object> r = rows.get(0);
        if (((Number) r.get("is_deleted")).intValue() == 1)
            throw new BizException(ErrorCode.PARAM_ERROR, "帖子不存在");
        if (((Number) r.get("user_id")).longValue() != uid)
            throw new BizException(ErrorCode.PARAM_ERROR, "只能删除自己发布的帖子");
        // 软删帖子，并连带软删其评论，避免孤儿数据
        jdbc.update("UPDATE t_post SET is_deleted = 1 WHERE id = ?", id);
        jdbc.update("UPDATE t_post_comment SET is_deleted = 1 WHERE post_id = ?", id);
        return ApiResp.ok(null);
    }

    @GetMapping("/post/{id}/comments")
    public ApiResp<List<Map<String, Object>>> comments(@PathVariable long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, post_id, user_id, user_nickname, user_avatar, content, like_count, created_at " +
            "FROM t_post_comment WHERE post_id = ? AND is_deleted = 0 ORDER BY id ASC", id);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.get("id"));
            m.put("postId", r.get("post_id"));
            m.put("userId", r.get("user_id"));
            m.put("userNickname", r.get("user_nickname"));
            m.put("userAvatar", r.get("user_avatar"));
            m.put("content", r.get("content"));
            m.put("likeCount", r.get("like_count"));
            m.put("createdAt", r.get("created_at") == null ? null : r.get("created_at").toString());
            out.add(m);
        }
        return ApiResp.ok(out);
    }

    @PostMapping("/post/{id}/comment")
    public ApiResp<Map<String, Object>> comment(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String content = (String) body.get("content");
        if (content == null || content.trim().isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "评论不能为空");
        if (sensitive.hasBlock(content)) throw new BizException(ErrorCode.SENSITIVE_WORD);
        Map<String, Object> u = jdbc.queryForMap("SELECT nickname, avatar FROM t_user WHERE id = ?", uid);
        jdbc.update(
            "INSERT INTO t_post_comment (post_id, user_id, user_nickname, user_avatar, content) " +
            "VALUES (?, ?, ?, ?, ?)",
            id, uid, u.get("nickname"), u.get("avatar"), content);
        long cid = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        jdbc.update("UPDATE t_post SET comment_count = comment_count + 1 WHERE id = ?", id);
        // 通知帖主（不通知自己）
        Map<String, Object> post = jdbc.queryForMap("SELECT user_id, content FROM t_post WHERE id = ?", id);
        long authorId = ((Number) post.get("user_id")).longValue();
        if (authorId != uid) {
            String title = post.get("content") == null ? "" : post.get("content").toString();
            if (title.length() > 20) title = title.substring(0, 20) + "…";
            String cname = u.get("nickname") == null ? "有人" : u.get("nickname").toString();
            notify.notifyUser(authorId, "social", "有人评论了你的帖子",
                cname + " 评论了你的帖子《" + title + "》", "post:" + id);
        }
        return ApiResp.ok(Map.of("id", cid, "postId", id));
    }

    // ============= 图片上传 =============

    @PostMapping(value = "/community/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResp<Map<String, Object>> upload(@RequestParam("files") MultipartFile[] files,
                                               HttpServletRequest request) throws IOException {
        long uid = TokenContext.requireUserId();
        if (files == null || files.length == 0) throw new BizException(ErrorCode.PARAM_ERROR, "未选择文件");
        if (files.length > 9) throw new BizException(ErrorCode.PARAM_ERROR, "最多上传 9 张");
        Path uploadRoot = Paths.get(System.getProperty("user.dir"), "static", "uploads", "post");
        Files.createDirectories(uploadRoot);
        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            String ct = file.getContentType();
            if (ct == null || !(ct.equals("image/jpeg") || ct.equals("image/png") || ct.equals("image/webp")))
                throw new BizException(ErrorCode.PARAM_ERROR, "仅支持 jpg/png/webp");
            if (file.getSize() > 5 * 1024 * 1024)
                throw new BizException(ErrorCode.PARAM_ERROR, "单张图片不能超过 5MB");
            String ext = ct.equals("image/png") ? ".png" : ct.equals("image/webp") ? ".webp" : ".jpg";
            String filename = "p" + uid + "_" + System.currentTimeMillis() + "_" + urls.size() + ext;
            file.transferTo(uploadRoot.resolve(filename).toFile());
            urls.add(request.getContextPath() + "/static/uploads/post/" + filename);
        }
        if (urls.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "未选择有效文件");
        return ApiResp.ok(Map.of("urls", urls));
    }

    @GetMapping("/community/topics")
    public ApiResp<List<Map<String, Object>>> topics() {
        return ApiResp.ok(jdbc.queryForList(
            "SELECT id, name FROM t_topic ORDER BY id"));
    }

    // ============= 文章 =============

    @GetMapping("/article/list")
    public ApiResp<PageResp<Map<String, Object>>> articleList(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        String where = "WHERE is_deleted = 0";
        Object[] args;
        if (categoryId != null) { where += " AND category_id = ?"; args = new Object[]{categoryId}; }
        else args = new Object[]{};
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM t_article " + where, Long.class, args);
        Object[] all = Arrays.copyOf(args, args.length + 2);
        all[args.length] = size;
        all[args.length + 1] = (page - 1) * size;
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, category_id, title, content, cover, author, author_id, view_count, like_count, seo_url, created_at " +
            "FROM t_article " + where + " ORDER BY id DESC LIMIT ? OFFSET ?", all);
        for (Map<String, Object> m : rows) {
            m.put("createdAt", m.get("created_at") == null ? null : m.get("created_at").toString());
        }
        return ApiResp.ok(PageResp.of(rows, total == null ? 0 : total, page, size));
    }

    @GetMapping("/article/categories")
    public ApiResp<List<Map<String, Object>>> articleCategories() {
        return ApiResp.ok(jdbc.queryForList("SELECT id, name, icon, sort FROM t_article_category ORDER BY sort, id"));
    }

    @GetMapping("/article/{key}")
    public ApiResp<Map<String, Object>> articleDetail(@PathVariable String key) {
        List<Map<String, Object>> rows;
        if (key.matches("\\d+")) {
            rows = jdbc.queryForList(
                "SELECT id, category_id, title, content, cover, author, author_id, view_count, like_count, seo_url, created_at " +
                "FROM t_article WHERE id = ? AND is_deleted = 0", Long.parseLong(key));
        } else {
            rows = jdbc.queryForList(
                "SELECT id, category_id, title, content, cover, author, author_id, view_count, like_count, seo_url, created_at " +
                "FROM t_article WHERE seo_url = ? AND is_deleted = 0", key);
        }
        if (rows.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "文章不存在");
        long aid = ((Number) rows.get(0).get("id")).longValue();
        jdbc.update("UPDATE t_article SET view_count = view_count + 1 WHERE id = ?", aid);
        Map<String, Object> m = rows.get(0);
        m.put("createdAt", m.get("created_at") == null ? null : m.get("created_at").toString());
        // 点赞数 + 当前用户是否已赞
        long likeCount = m.get("like_count") == null ? 0 : ((Number) m.get("like_count")).longValue();
        m.put("likeCount", likeCount);
        // 评论数
        Long commentCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_article_comment WHERE article_id = ? AND is_deleted = 0", Long.class, aid);
        m.put("commentCount", commentCount == null ? 0 : commentCount);
        // 当前用户收藏状态
        long uid = TokenContext.get() == null ? 0 : TokenContext.get().userId;
        boolean liked = false;
        boolean collected = false;
        if (uid != 0) {
            Integer lc = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_article_like WHERE article_id = ? AND user_id = ? AND is_deleted = 0",
                Integer.class, aid, uid);
            liked = lc != null && lc > 0;
            Integer fc = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_favorite WHERE user_id = ? AND target_type = 'article' AND target_id = ? AND is_deleted = 0",
                Integer.class, uid, aid);
            collected = fc != null && fc > 0;
        }
        m.put("liked", liked);
        m.put("collected", collected);
        return ApiResp.ok(m);
    }

    @PostMapping("/article/{id}/like")
    public ApiResp<Map<String, Object>> likeArticle(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM t_article WHERE id = ? AND is_deleted = 0", Integer.class, id);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "文章不存在");
        List<Map<String, Object>> exist = jdbc.queryForList(
            "SELECT id, is_deleted FROM t_article_like WHERE article_id = ? AND user_id = ?", id, uid);
        boolean liked;
        if (exist.isEmpty()) {
            jdbc.update("INSERT INTO t_article_like (article_id, user_id) VALUES (?, ?)", id, uid);
            jdbc.update("UPDATE t_article SET like_count = like_count + 1 WHERE id = ?", id);
            liked = true;
        } else {
            Map<String, Object> row = exist.get(0);
            int del = ((Number) row.get("is_deleted")).intValue();
            if (del == 0) {
                jdbc.update("UPDATE t_article_like SET is_deleted = 1 WHERE id = ?", row.get("id"));
                jdbc.update("UPDATE t_article SET like_count = GREATEST(like_count - 1, 0) WHERE id = ?", id);
                liked = false;
            } else {
                jdbc.update("UPDATE t_article_like SET is_deleted = 0 WHERE id = ?", row.get("id"));
                jdbc.update("UPDATE t_article SET like_count = like_count + 1 WHERE id = ?", id);
                liked = true;
            }
        }
        long likeCount = jdbc.queryForObject("SELECT like_count FROM t_article WHERE id = ?", Long.class, id);
        return ApiResp.ok(Map.of("liked", liked, "likeCount", likeCount));
    }

    // ============= 文章评论 =============

    @GetMapping("/article/{id}/comments")
    public ApiResp<List<Map<String, Object>>> articleComments(@PathVariable long id) {
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM t_article WHERE id = ? AND is_deleted = 0", Integer.class, id);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "文章不存在");
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, article_id, user_id, user_nickname, user_avatar, content, like_count, created_at " +
            "FROM t_article_comment WHERE article_id = ? AND is_deleted = 0 ORDER BY id ASC", id);
        for (Map<String, Object> m : rows) {
            m.put("createdAt", m.get("created_at") == null ? null : m.get("created_at").toString());
            m.put("likeCount", m.get("like_count") == null ? 0 : ((Number) m.get("like_count")).longValue());
        }
        return ApiResp.ok(rows);
    }

    @PostMapping("/article/{id}/comment")
    public ApiResp<Map<String, Object>> commentArticle(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM t_article WHERE id = ? AND is_deleted = 0", Integer.class, id);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "文章不存在");
        String content = body.get("content") == null ? "" : body.get("content").toString().trim();
        if (content.isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "评论内容不能为空");
        if (content.length() > 500) throw new BizException(ErrorCode.PARAM_ERROR, "评论最多 500 字");
        // 敏感词校验
        if (sensitive.hasBlock(content)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "评论包含敏感词，请修改后重试");
        }
        Map<String, Object> user = jdbc.queryForMap(
            "SELECT nickname, avatar FROM t_user WHERE id = ? AND is_deleted = 0", uid);
        if (user == null) throw new BizException(ErrorCode.PARAM_ERROR, "用户不存在");
        jdbc.update(
            "INSERT INTO t_article_comment (article_id, user_id, user_nickname, user_avatar, content) VALUES (?, ?, ?, ?, ?)",
            id, uid, user.get("nickname"), user.get("avatar"), content);
        jdbc.update("UPDATE t_article SET comment_count = comment_count + 1 WHERE id = ?", id);
        Long total = jdbc.queryForObject("SELECT comment_count FROM t_article WHERE id = ?", Long.class, id);
        return ApiResp.ok(Map.of("commentCount", total == null ? 0 : total));
    }

    // ============= 爱心捐助排行 =============

    /**
     * 奖励档位：threshold = 累计捐赠金额门槛(元)，reward = 奖励鱼干数。
     * 顺序按门槛从高到低，领取时取已达到的最高档位。
     */
    private static final String[][] DONATE_TIERS = {
        {"diamond", "钻石爱心大使", "200", "200"},
        {"gold",    "黄金爱心大使", "100", "120"},
        {"silver",  "白银爱心大使", "50",  "60"},
        {"bronze",  "青铜爱心大使", "20",  "20"},
    };

    // 宠物摄影大赛话题标签：发布动态包含该 #话题 即视为参赛，按点赞数排名
    private static final String PHOTO_CONTEST_HASHTAG = "#宠物摄影大赛";

    @GetMapping("/community/donate-rank")
    public ApiResp<Map<String, Object>> donateRank(@RequestParam(defaultValue = "20") int limit) {
        long uid = optionalUserId();
        // 排行榜：仅统计已支付订单的爱心捐赠
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT u.id AS user_id, u.nickname, u.avatar, SUM(o.donate_amount) AS total_donate " +
            "FROM t_order o JOIN t_user u ON u.id = o.user_id " +
            "WHERE o.status IN ('paid','shipping','done','received') AND o.donate_amount > 0 AND o.is_deleted = 0 " +
            "GROUP BY o.user_id " +
            "ORDER BY total_donate DESC LIMIT ?", limit);
        List<Map<String, Object>> list = new ArrayList<>();
        int rank = 0;
        for (Map<String, Object> r : rows) {
            rank++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank);
            m.put("userId", r.get("user_id"));
            m.put("nickname", r.get("nickname"));
            m.put("avatar", r.get("avatar"));
            m.put("totalDonate", r.get("total_donate"));
            list.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);

        // 当前用户的捐赠总额 / 排名 / 已领档位 / 可领档位
        Map<String, Object> mine = new LinkedHashMap<>();
        if (uid != 0) {
            BigDecimal myTotal = jdbc.queryForObject(
                "SELECT COALESCE(SUM(donate_amount),0) FROM t_order " +
                "WHERE user_id = ? AND status IN ('paid','shipping','done','received') AND donate_amount > 0 AND is_deleted = 0",
                BigDecimal.class, uid);
            if (myTotal == null) myTotal = BigDecimal.ZERO;
            long myRank = myTotal.compareTo(BigDecimal.ZERO) > 0
                ? jdbc.queryForObject(
                    "SELECT COUNT(*) + 1 FROM (" +
                    "SELECT user_id FROM t_order WHERE status IN ('paid','shipping','done','received') AND donate_amount>0 AND is_deleted=0 " +
                    "GROUP BY user_id HAVING SUM(donate_amount) > ?) t", Long.class, myTotal)
                : 0;
            mine.put("totalDonate", myTotal);
            mine.put("rank", myRank == 0 ? null : myRank);
            mine.put("claimedTiers", jdbc.queryForList(
                "SELECT tier FROM t_donate_reward WHERE user_id = ?", String.class, uid));
            String claimable = null;
            for (String[] t : DONATE_TIERS) {
                if (myTotal.compareTo(new BigDecimal(t[2])) >= 0) { claimable = t[0]; break; }
            }
            mine.put("claimableTier", claimable);
        }
        out.put("mine", mine);

        // 档位定义（前端展示）
        List<Map<String, Object>> tiers = new ArrayList<>();
        for (String[] t : DONATE_TIERS) {
            Map<String, Object> tm = new LinkedHashMap<>();
            tm.put("tier", t[0]);
            tm.put("name", t[1]);
            tm.put("threshold", new BigDecimal(t[2]));
            tm.put("reward", Integer.parseInt(t[3]));
            tiers.add(tm);
        }
        out.put("tiers", tiers);
        out.put("totalDonors", jdbc.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM t_order WHERE status IN ('paid','shipping','done','received') AND donate_amount>0 AND is_deleted=0",
            Integer.class));
        out.put("totalDonated", jdbc.queryForObject(
            "SELECT COALESCE(SUM(donate_amount),0) FROM t_order WHERE status IN ('paid','shipping','done','received') AND donate_amount>0 AND is_deleted=0",
            BigDecimal.class));
        return ApiResp.ok(out);
    }

    @PostMapping("/community/donate-rank/claim")
    public ApiResp<Map<String, Object>> claimDonateRank() {
        long uid = TokenContext.requireUserId();
        BigDecimal myTotal = jdbc.queryForObject(
            "SELECT COALESCE(SUM(donate_amount),0) FROM t_order " +
            "WHERE user_id = ? AND status IN ('paid','shipping','done','received') AND donate_amount > 0 AND is_deleted = 0",
            BigDecimal.class, uid);
        if (myTotal == null) myTotal = BigDecimal.ZERO;
        String[] best = null;
        for (String[] t : DONATE_TIERS) {
            if (myTotal.compareTo(new BigDecimal(t[2])) >= 0) { best = t; break; }
        }
        if (best == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "捐赠额还不足以领取奖励，继续奉献爱心吧~");
        }
        String tier = best[0];
        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_donate_reward WHERE user_id = ? AND tier = ?", Integer.class, uid, tier);
        int reward = Integer.parseInt(best[3]);
        if (exists != null && exists > 0) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("alreadyClaimed", true);
            r.put("tier", tier);
            r.put("tierName", best[1]);
            r.put("reward", 0);
            r.put("balance", pointsService.getBalance(uid));
            return ApiResp.ok(r);
        }
        // 发放奖励（不受每日上限约束），并落奖励记录（唯一键幂等）
        pointsService.earnUnlimited(uid, "donate_rank", reward, "爱心捐助排行-" + best[1], null);
        jdbc.update(
            "INSERT INTO t_donate_reward (user_id, tier, reward_points, total_donate) VALUES (?, ?, ?, ?)",
            uid, tier, reward, myTotal);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("alreadyClaimed", false);
        r.put("tier", tier);
        r.put("tierName", best[1]);
        r.put("reward", reward);
        r.put("balance", pointsService.getBalance(uid));
        return ApiResp.ok(r);
    }

    // ============= 宠物摄影大赛 =============

    /**
     * 参赛规则：发布动态时内容包含活动 #话题（#{PHOTO_CONTEST_HASHTAG}）即视为参赛，
     * 排行榜按帖子点赞数（like_count）降序排列，点赞数即比赛数据；
     * 每人仅以其点赞最高的 1 篇作品计入榜单（避免同一人多篇霸榜）。
     */
    @GetMapping("/community/contest/photo")
    public ApiResp<Map<String, Object>> photoContest(@RequestParam(defaultValue = "50") int limit) {
        long uid = optionalUserId();
        String likePattern = "%" + PHOTO_CONTEST_HASHTAG + "%";
        // 排行榜：每人仅以点赞最高的 1 篇参赛作品计入（PARTITION BY user_id 取 like_count 最大者）
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT * FROM (" +
            "  SELECT id, user_id, user_nickname, user_avatar, content, images, video_url, " +
            "    topic_id, topic_name, like_count, comment_count, collect_count, visible, created_at, " +
            "    ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY like_count DESC, id DESC) AS rn " +
            "  FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved' AND content LIKE ?" +
            ") t WHERE rn = 1 " +
            "ORDER BY like_count DESC, id DESC LIMIT ?", likePattern, limit);
        Set<Long> likedIds = likedSet(uid, rows);
        Set<Long> collectedIds = collectedSet(uid, rows);
        List<Map<String, Object>> list = new ArrayList<>();
        int rank = 0;
        for (Map<String, Object> r : rows) {
            rank++;
            Map<String, Object> m = normalizePost(r);
            long pid = ((Number) r.get("id")).longValue();
            m.put("rank", rank);
            m.put("liked", likedIds.contains(pid));
            m.put("collected", collectedIds.contains(pid));
            list.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("list", list);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("participantCount", jdbc.queryForObject(
            "SELECT COUNT(DISTINCT user_id) FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved' AND content LIKE ?",
            Integer.class, likePattern));
        stats.put("postCount", jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved' AND content LIKE ?",
            Integer.class, likePattern));
        stats.put("totalLikes", jdbc.queryForObject(
            "SELECT COALESCE(SUM(like_count), 0) FROM t_post WHERE is_deleted = 0 AND audit_status = 'approved' AND content LIKE ?",
            Long.class, likePattern));
        out.put("stats", stats);

        Map<String, Object> mine = new LinkedHashMap<>();
        if (uid != 0) {
            List<Map<String, Object>> my = jdbc.queryForList(
                "SELECT id, like_count FROM t_post WHERE user_id = ? AND is_deleted = 0 AND audit_status = 'approved' AND content LIKE ?",
                uid, likePattern);
            mine.put("participated", !my.isEmpty());
            mine.put("postCount", my.size());
            long best = 0;
            for (Map<String, Object> r : my) {
                long lc = r.get("like_count") == null ? 0 : ((Number) r.get("like_count")).longValue();
                if (lc > best) best = lc;
            }
            mine.put("bestLikeCount", best);
        } else {
            mine.put("participated", false);
            mine.put("postCount", 0);
            mine.put("bestLikeCount", 0);
        }
        out.put("mine", mine);
        return ApiResp.ok(out);
    }

    // ============= 某用户的动态（他人主页用） =============

    @GetMapping("/post/user/{userId}")
    public ApiResp<PageResp<Map<String, Object>>> postsByUser(
            @PathVariable long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        long uid = optionalUserId();
        int safeSize = Math.max(1, Math.min(size, 50));
        int safePage = Math.max(1, page);
        int offset = (safePage - 1) * safeSize;
        Long total = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_post WHERE user_id = ? AND is_deleted = 0 AND audit_status = 'approved'",
            Long.class, userId);
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, user_id, user_nickname, user_avatar, content, images, video_url, " +
            "topic_id, topic_name, like_count, comment_count, collect_count, visible, created_at " +
            "FROM t_post WHERE user_id = ? AND is_deleted = 0 AND audit_status = 'approved' " +
            "ORDER BY id DESC LIMIT ? OFFSET ?", userId, safeSize, offset);
        Set<Long> likedIds = likedSet(uid, rows);
        Set<Long> collectedIds = collectedSet(uid, rows);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Map<String, Object> m = normalizePost(r);
            long pid = ((Number) r.get("id")).longValue();
            m.put("liked", likedIds.contains(pid));
            m.put("collected", collectedIds.contains(pid));
            out.add(m);
        }
        return ApiResp.ok(PageResp.of(out, total == null ? 0 : total, safePage, safeSize));
    }

    // ============= 内部 =============

    private long optionalUserId() {
        return TokenContext.get() == null ? 0 : TokenContext.get().userId;
    }

    private Set<Long> likedSet(long uid, List<Map<String, Object>> rows) {
        Set<Long> set = new HashSet<>();
        if (uid == 0 || rows.isEmpty()) return set;
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        try {
            List<Map<String, Object>> liked = jdbc.queryForList(
                "SELECT post_id FROM t_post_like WHERE user_id = ? AND post_id IN (" + placeholders + ")",
                appendFront(uid, rows));
            for (Map<String, Object> r : liked) set.add(((Number) r.get("post_id")).longValue());
        } catch (Exception ignore) {}
        return set;
    }

    private Set<Long> collectedSet(long uid, List<Map<String, Object>> rows) {
        Set<Long> set = new HashSet<>();
        if (uid == 0 || rows.isEmpty()) return set;
        String placeholders = String.join(",", Collections.nCopies(rows.size(), "?"));
        try {
            List<Map<String, Object>> collected = jdbc.queryForList(
                "SELECT post_id FROM t_post_collect WHERE user_id = ? AND post_id IN (" + placeholders + ")",
                appendFront(uid, rows));
            for (Map<String, Object> r : collected) set.add(((Number) r.get("post_id")).longValue());
        } catch (Exception ignore) {}
        return set;
    }

    private Object[] appendFront(long uid, List<Map<String, Object>> rows) {
        Object[] all = new Object[rows.size() + 1];
        all[0] = uid;
        for (int i = 0; i < rows.size(); i++) all[i + 1] = ((Number) rows.get(i).get("id")).longValue();
        return all;
    }

    private Map<String, Object> normalizePost(Map<String, Object> r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.get("id"));
        m.put("userId", r.get("user_id"));
        m.put("userNickname", r.get("user_nickname"));
        m.put("userAvatar", r.get("user_avatar"));
        m.put("content", r.get("content"));
        Object imagesRaw = r.get("images");
        Object imagesOut = null;
        if (imagesRaw != null) {
            try {
                imagesOut = objectMapper.readValue(imagesRaw.toString(), List.class);
            } catch (Exception e) {
                imagesOut = Collections.emptyList();
            }
        }
        m.put("images", imagesOut);
        m.put("videoUrl", r.get("video_url"));
        m.put("topicId", r.get("topic_id"));
        m.put("topicName", r.get("topic_name"));
        m.put("likeCount", r.get("like_count"));
        m.put("commentCount", r.get("comment_count"));
        m.put("collectCount", r.get("collect_count"));
        m.put("visible", r.get("visible"));
        m.put("city", r.get("city"));
        m.put("createdAt", r.get("created_at") == null ? null : r.get("created_at").toString());
        return m;
    }
}
