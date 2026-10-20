package com.petmate.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 请求体缓存过滤器。
 *
 * 为「写类请求」（POST/PUT/PATCH、非 multipart、非支付回调）包裹 CachedBodyRequestWrapper，
 * 使 EmergencyBreakInterceptor 能读取请求体做敏感词扫描，而下游 @RequestBody 仍可读到原始内容（缓存重放）。
 */
public class RequestBodyCacheFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String method = request.getMethod();
        boolean bodyMethod = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
        String contentType = request.getContentType();
        boolean multipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");
        String uri = request.getRequestURI();
        // 文件上传（multipart）不能包裹，支付回调（外部 POST）无需扫描，均跳过
        boolean skip = !bodyMethod || multipart || uri.contains("/pay/") || uri.endsWith("/pay");

        if (skip) {
            chain.doFilter(request, response);
        } else {
            chain.doFilter(new CachedBodyRequestWrapper(request), response);
        }
    }
}
