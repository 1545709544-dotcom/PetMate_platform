package com.petmate.config;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 可重复读取请求体的包装器。
 *
 * 与 Spring 自带的 ContentCachingRequestWrapper 不同，本类在构造时一次性读完原始 Body 并缓存，
 * 之后每次 getInputStream()/getReader() 都从该缓存「重放」，因此拦截器读取扫描、下游 @RequestBody 反序列化
 * 都能各自拿到完整内容，互不干扰。
 */
public class CachedBodyRequestWrapper extends HttpServletRequestWrapper {

    private final byte[] body;

    public CachedBodyRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.body = StreamUtils.copyToByteArray(request.getInputStream());
    }

    /** 完整请求体（UTF-8 解码前的原始字节） */
    public byte[] getBody() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream bais = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override public int read() { return bais.read(); }
            @Override public int available() { return bais.available(); }
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { /* 不支持异步读取 */ }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
    }
}
