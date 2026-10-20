package com.petmate.user;

import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class AddressService {

    /** 合法 tag 白名单 */
    private static final java.util.Set<String> VALID_TAGS = java.util.Set.of("home", "company", "school");

    /** 每个用户最多保留的地址数 */
    private static final int MAX_ADDRESSES = 20;

    private final JdbcTemplate jdbc;
    private final RowMapper<Address> MAPPER = this::mapRow;

    public AddressService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 查询 ====================

    public List<Address> listByUser(long userId) {
        return jdbc.query(
            "SELECT id, user_id, receiver, phone, province, city, district, detail, is_default, tag, created_at, updated_at " +
            "FROM t_user_address WHERE user_id = ? AND is_deleted = 0 ORDER BY is_default DESC, id DESC",
            MAPPER, userId);
    }

    public Address findById(long id) {
        try {
            return jdbc.queryForObject(
                "SELECT id, user_id, receiver, phone, province, city, district, detail, is_default, tag, created_at, updated_at " +
                "FROM t_user_address WHERE id = ? AND is_deleted = 0",
                MAPPER, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // ==================== 新增 ====================

    @Transactional
    public Address create(long userId, Map<String, Object> body) {
        String receiver  = str(body.get("receiver"));
        String phone     = str(body.get("phone"));
        String province  = str(body.get("province"));
        String city      = str(body.get("city"));
        String district  = str(body.get("district"));
        String detail    = str(body.get("detail"));
        String tag       = str(body.getOrDefault("tag", "home"));
        boolean isDefault = bool(body.get("isDefault"));

        // 校验
        validateAddress(receiver, phone, province, city, district, detail, tag);

        // 上限
        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_user_address WHERE user_id = ? AND is_deleted = 0", Integer.class, userId);
        if (count >= MAX_ADDRESSES) {
            throw new BizException(ErrorCode.PARAM_ERROR, "地址数量已达上限（" + MAX_ADDRESSES + "个）");
        }

        if (isDefault) {
            jdbc.update("UPDATE t_user_address SET is_default = 0 WHERE user_id = ?", userId);
        }

        jdbc.update(
            "INSERT INTO t_user_address (user_id, receiver, phone, province, city, district, detail, is_default, tag) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
            userId, receiver, phone, province, city, district, detail, isDefault ? 1 : 0, tag);

        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return findById(id);
    }

    // ==================== 更新 ====================

    @Transactional
    public Address update(long userId, long id, Map<String, Object> body) {
        // 确保属于当前用户
        Address existing = findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "地址不存在");
        }

        String receiver  = str(body.getOrDefault("receiver", existing.getReceiver()));
        String phone     = str(body.getOrDefault("phone", existing.getPhone()));
        String province  = str(body.getOrDefault("province", existing.getProvince()));
        String city      = str(body.getOrDefault("city", existing.getCity()));
        String district  = str(body.getOrDefault("district", existing.getDistrict()));
        String detail    = str(body.getOrDefault("detail", existing.getDetail()));
        String tag       = str(body.getOrDefault("tag", existing.getTag()));
        boolean isDefault = body.containsKey("isDefault") ? bool(body.get("isDefault")) : existing.isDefault();

        validateAddress(receiver, phone, province, city, district, detail, tag);

        if (isDefault) {
            jdbc.update("UPDATE t_user_address SET is_default = 0 WHERE user_id = ? AND id != ?", userId, id);
        }
        // 如果取消默认且这是唯一一条是默认的，不允许
        if (!isDefault && existing.isDefault()) {
            // 允许取消，不做强制约束
        }

        jdbc.update(
            "UPDATE t_user_address SET receiver=?, phone=?, province=?, city=?, district=?, detail=?, is_default=?, tag=? " +
            "WHERE id = ? AND user_id = ?",
            receiver, phone, province, city, district, detail, isDefault ? 1 : 0, tag, id, userId);

        return findById(id);
    }

    // ==================== 设为默认（专用接口） ====================

    @Transactional
    public void setDefault(long userId, long id) {
        Address existing = findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "地址不存在");
        }
        jdbc.update("UPDATE t_user_address SET is_default = 0 WHERE user_id = ?", userId);
        jdbc.update("UPDATE t_user_address SET is_default = 1 WHERE id = ? AND user_id = ?", id, userId);
    }

    // ==================== 删除 ====================

    @Transactional
    public void deleteById(long userId, long id) {
        Address existing = findById(id);
        if (existing == null || !existing.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "地址不存在");
        }
        jdbc.update("UPDATE t_user_address SET is_deleted = 1 WHERE id = ? AND user_id = ?", id, userId);

        // 如果删除的是默认地址，自动找最新一条设为默认
        if (existing.isDefault()) {
            List<Address> remaining = jdbc.query(
                "SELECT id FROM t_user_address WHERE user_id = ? AND is_deleted = 0 ORDER BY id DESC LIMIT 1",
                (rs, i) -> { Address a = new Address(); a.setId(rs.getLong("id")); return a; },
                userId);
            if (!remaining.isEmpty()) {
                jdbc.update("UPDATE t_user_address SET is_default = 1 WHERE id = ?", remaining.get(0).getId());
            }
        }
    }

    // ==================== 校验 ====================

    private void validateAddress(String receiver, String phone, String province,
                                  String city, String district, String detail, String tag) {
        if (receiver == null || receiver.trim().isEmpty() || receiver.length() > 20) {
            throw new BizException(ErrorCode.PARAM_ERROR, "收货人姓名长度需 1-20 位");
        }
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
        }
        if (province == null || province.trim().isEmpty() || province.length() > 16) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择省份");
        }
        if (city == null || city.trim().isEmpty() || city.length() > 16) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择城市");
        }
        if (district == null || district.trim().isEmpty() || district.length() > 16) {
            throw new BizException(ErrorCode.PARAM_ERROR, "请选择区县");
        }
        if (detail == null || detail.trim().isEmpty() || detail.length() > 100) {
            throw new BizException(ErrorCode.PARAM_ERROR, "详细地址长度需 1-100 位");
        }
        if (tag == null || !VALID_TAGS.contains(tag)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "地址标签无效，仅支持 home/company/school");
        }
    }

    // ==================== 辅助 ====================

    /** 转换为前端所需的 Map 格式 */
    public Map<String, Object> toMap(Address a) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("userId", a.getUserId());
        m.put("receiver", a.getReceiver());
        m.put("phone", a.getPhone());
        m.put("province", a.getProvince());
        m.put("city", a.getCity());
        m.put("district", a.getDistrict());
        m.put("detail", a.getDetail());
        m.put("isDefault", a.isDefault());
        m.put("tag", a.getTag());
        return m;
    }

    private static String str(Object v) {
        return v == null ? null : v.toString().trim();
    }

    private static boolean bool(Object v) {
        return Boolean.TRUE.equals(v);
    }

    private Address mapRow(ResultSet rs, int i) throws SQLException {
        Address a = new Address();
        a.setId(rs.getLong("id"));
        a.setUserId(rs.getLong("user_id"));
        a.setReceiver(rs.getString("receiver"));
        a.setPhone(rs.getString("phone"));
        a.setProvince(rs.getString("province"));
        a.setCity(rs.getString("city"));
        a.setDistrict(rs.getString("district"));
        a.setDetail(rs.getString("detail"));
        a.setDefault(rs.getInt("is_default") == 1);
        a.setTag(rs.getString("tag"));
        java.sql.Timestamp cr = rs.getTimestamp("created_at");
        if (cr != null) a.setCreatedAt(cr.toLocalDateTime());
        java.sql.Timestamp up = rs.getTimestamp("updated_at");
        if (up != null) a.setUpdatedAt(up.toLocalDateTime());
        return a;
    }
}
