package com.petmate.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Map;

/**
 * 全局异常处理 —— 转换为 ApiResp 统一返回
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResp<Object>> handleBiz(BizException e) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResp<Object>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + " " + fe.getDefaultMessage())
                .orElse("参数错误");
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResp<Object>> handleIllegal(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, e.getMessage()));
    }

    /** 缺少必填请求参数 —— 之前会掉进兜底变成「系统异常」，误导前端以为服务崩了 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResp<Object>> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, "缺少必填参数：" + e.getParameterName()));
    }

    /** 参数类型不匹配（如 id 传了非数字） */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResp<Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, "参数格式错误：" + e.getName()));
    }

    /** 请求体缺失或 JSON 格式错误 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResp<Object>> handleBadBody(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, "请求内容格式错误"));
    }

    /** HTTP 方法不支持 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResp<Object>> handleMethod(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, "请求方式不支持：" + e.getMethod()));
    }

    /** 路由不存在 */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResp<Object>> handleNoHandler(NoHandlerFoundException e) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, "接口不存在"));
    }

    /**
     * Spring Boot 3 中未匹配到任何 @RequestMapping 的请求会落到静态资源解析，
     * 最终抛 NoResourceFoundException（而非 NoHandlerFoundException）。
     * 不单独处理会被兜底成「系统异常，请稍后重试」，让前端把「路径写错」误判成「后端崩了」。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResp<Object>> handleNoResource(NoResourceFoundException e) {
        log.warn("no handler for path: {}", e.getResourcePath());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, "接口不存在：/" + e.getResourcePath()));
    }

    /** 唯一键冲突 / 外键约束等数据完整性问题，给出可读提示而非「系统异常」 */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResp<Object>> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.PARAM_ERROR, "数据已存在或关联数据不完整"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResp<Object>> handleOther(Exception e) {
        log.error("unhandled exception", e);
        // 不向客户端暴露异常原文(SQL 片段/表名/列名等),仅返回通用文案,详情落日志
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResp.fail(ErrorCode.SYSTEM_ERROR, "系统异常，请稍后重试"));
    }

    /** 直接返回指定 code+msg 的便捷方法 */
    public static Map<String, Object> failMap(int code, String msg) {
        return Map.of("code", code, "msg", msg, "data", null);
    }
}
