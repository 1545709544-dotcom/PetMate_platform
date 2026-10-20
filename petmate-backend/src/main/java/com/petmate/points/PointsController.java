package com.petmate.points;

import com.petmate.common.ApiResp;
import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import com.petmate.security.TokenContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
public class PointsController {

    private final PointsService pointsService;

    public PointsController(PointsService pointsService) {
        this.pointsService = pointsService;
    }

    /** 当前鱼干余额与每日上限进度 */
    @GetMapping("/points/balance")
    public ApiResp<Map<String, Object>> balance() {
        long uid = TokenContext.requireUserId();
        return ApiResp.ok(pointsService.getSummary(uid));
    }

    /** 任务中心列表（含完成状态） */
    @GetMapping("/task/list")
    public ApiResp<List<Map<String, Object>>> taskList() {
        long uid = TokenContext.requireUserId();
        return ApiResp.ok(pointsService.listTasks(uid));
    }

    /** 领取任务奖励：签到 / 完善资料 / 首次发动态 / 分享 均走此接口 */
    @PostMapping("/task/complete")
    public ApiResp<Map<String, Object>> completeTask(@RequestBody Map<String, Object> body) {
        long uid = TokenContext.requireUserId();
        String taskCode = (String) body.get("taskCode");
        if (taskCode == null || taskCode.trim().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "taskCode 不能为空");
        }
        return ApiResp.ok(pointsService.completeTask(uid, taskCode));
    }
}
