package com.petmate.user;

import com.petmate.common.ApiResp;
import com.petmate.security.TokenContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户收货地址接口。地址 CRUD + 设默认均走本 Controller。
 */
@RestController
@RequestMapping("/user/address")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    /** 地址列表（默认在前） */
    @GetMapping
    public ApiResp<List<Map<String, Object>>> list() {
        long uid = TokenContext.requireUserId();
        List<Address> list = addressService.listByUser(uid);
        return ApiResp.ok(list.stream().map(addressService::toMap).collect(Collectors.toList()));
    }

    /** 单条查询（编辑页直接拉一条，避免全量 list + find） */
    @GetMapping("/{id}")
    public ApiResp<Map<String, Object>> get(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        Address a = addressService.findById(id);
        if (a == null || !a.getUserId().equals(uid)) {
            return ApiResp.ok(null);
        }
        return ApiResp.ok(addressService.toMap(a));
    }

    /** 新增 */
    @PostMapping
    public ApiResp<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        Address a = addressService.create(uid, body);
        return ApiResp.ok(addressService.toMap(a));
    }

    /** 全量更新 */
    @PutMapping("/{id}")
    public ApiResp<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        Address a = addressService.update(uid, id, body);
        return ApiResp.ok(addressService.toMap(a));
    }

    /** 设为默认（专用接口，只改默认标志，不动其他字段） */
    @PutMapping("/{id}/default")
    public ApiResp<Void> setDefault(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        addressService.setDefault(uid, id);
        return ApiResp.ok();
    }

    /** 软删除（若删的是默认地址，自动把最新一条设为默认） */
    @DeleteMapping("/{id}")
    public ApiResp<Void> delete(@PathVariable long id) {
        long uid = TokenContext.requireUserId();
        addressService.deleteById(uid, id);
        return ApiResp.ok();
    }
}
