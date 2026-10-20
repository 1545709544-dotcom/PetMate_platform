package com.petmate.merchant;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 商家员工（子账号）管理 + 角色定义。仅店主(OWNER)可操作。
 * 角色：OWNER(店主) / OPERATOR(运营) / CS(客服) / WAREHOUSE(仓管)
 */
@RestController
public class MerchantStaffController {

    private final JdbcTemplate jdbc;
    public MerchantStaffController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private long ownerMid() {
        long uid = TokenContext.requireUserId();
        TokenContext.requireRoleAndActive("merchant");
        TokenContext.requireSubRole("OWNER");
        return TokenContext.requireMerchantId();
    }

    /** 角色定义（前端用于展示权限说明） */
    @GetMapping("/merchant/roles")
    public ApiResp<List<Map<String, Object>>> roles() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(roleDef("OWNER", "店主", "全部权限", List.of("order.*", "product.*", "review.*", "finance.*", "staff.*", "settings.*")));
        list.add(roleDef("OPERATOR", "运营", "商品/服务/订单管理", List.of("order.list", "order.ship", "product.*", "service.*")));
        list.add(roleDef("CS", "客服", "订单/评价/客户", List.of("order.list", "review.*", "customer.*")));
        list.add(roleDef("WAREHOUSE", "仓管", "仅发货", List.of("order.list", "order.ship")));
        return ApiResp.ok(list);
    }

    private Map<String, Object> roleDef(String key, String name, String desc, List<String> perms) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("name", name);
        m.put("desc", desc);
        m.put("perms", perms);
        return m;
    }

    /** 员工列表（脱敏，不含密码） */
    @GetMapping("/merchant/staff")
    public ApiResp<List<Map<String, Object>>> list() {
        long merchantId = ownerMid();
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT id, username, nickname, role, status, created_at FROM t_merchant_staff " +
            "WHERE merchant_id = ? ORDER BY id DESC", merchantId);
        return ApiResp.ok(rows);
    }

    /** 新增员工（默认密码 123456） */
    @PostMapping("/merchant/staff")
    public ApiResp<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long merchantId = ownerMid();
        String username = (String) body.get("username");
        String nickname = (String) body.get("nickname");
        String role = (String) body.get("role");
        if (username == null || username.trim().isEmpty()) throw new BizException(ErrorCode.PARAM_ERROR, "账号不能为空");
        if (role == null || role.trim().isEmpty()) role = "OPERATOR";
        if (!List.of("OPERATOR", "CS", "WAREHOUSE").contains(role)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "非法角色");
        }
        Integer exists = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_merchant_staff WHERE merchant_id = ? AND username = ?",
            Integer.class, merchantId, username);
        if (exists != null && exists > 0) throw new BizException(ErrorCode.PARAM_ERROR, "该账号已存在");
        String pwd = (String) body.get("password");
        if (pwd == null || pwd.isEmpty()) pwd = "123456";
        String hash = org.springframework.security.crypto.bcrypt.BCrypt.hashpw(pwd, org.springframework.security.crypto.bcrypt.BCrypt.gensalt(10));
        if (nickname == null || nickname.isEmpty()) nickname = username;
        jdbc.update(
            "INSERT INTO t_merchant_staff (merchant_id, username, password, nickname, role, status) VALUES (?,?,?,?,?,1)",
            merchantId, username, hash, nickname, role);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        out.put("username", username);
        out.put("nickname", nickname);
        out.put("role", role);
        out.put("status", 1);
        return ApiResp.ok(out);
    }

    /** 修改员工（角色/状态/昵称） */
    @PutMapping("/merchant/staff/{id}")
    public ApiResp<Void> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long merchantId = ownerMid();
        Integer cnt = jdbc.queryForObject(
            "SELECT COUNT(*) FROM t_merchant_staff WHERE id = ? AND merchant_id = ?",
            Integer.class, id, merchantId);
        if (cnt == null || cnt == 0) throw new BizException(ErrorCode.PARAM_ERROR, "员工不存在");
        String nickname = (String) body.get("nickname");
        String role = (String) body.get("role");
        Object status = body.get("status");
        StringBuilder sql = new StringBuilder("UPDATE t_merchant_staff SET ");
        List<Object> args = new ArrayList<>();
        boolean first = true;
        if (nickname != null) { sql.append(first ? "" : ", ").append("nickname = ?"); args.add(nickname); first = false; }
        if (role != null) {
            if (!List.of("OPERATOR", "CS", "WAREHOUSE").contains(role)) throw new BizException(ErrorCode.PARAM_ERROR, "非法角色");
            sql.append(first ? "" : ", ").append("role = ?"); args.add(role); first = false;
        }
        if (status != null) { sql.append(first ? "" : ", ").append("status = ?"); args.add(((Number) status).intValue()); first = false; }
        if (!first) {
            sql.append(" WHERE id = ? AND merchant_id = ?");
            args.add(id); args.add(merchantId);
            jdbc.update(sql.toString(), args.toArray());
        }
        return ApiResp.ok();
    }

    /** 删除（停用）员工 */
    @DeleteMapping("/merchant/staff/{id}")
    public ApiResp<Void> remove(@PathVariable long id) {
        long merchantId = ownerMid();
        int n = jdbc.update("UPDATE t_merchant_staff SET status = 0 WHERE id = ? AND merchant_id = ?", id, merchantId);
        if (n == 0) throw new BizException(ErrorCode.PARAM_ERROR, "员工不存在");
        return ApiResp.ok();
    }
}
