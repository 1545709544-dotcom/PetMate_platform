package com.petmate.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;
import java.nio.file.Paths;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final EmergencyBreakInterceptor emergencyBreakInterceptor;

    @Autowired
    public WebConfig(EmergencyBreakInterceptor emergencyBreakInterceptor) {
        this.emergencyBreakInterceptor = emergencyBreakInterceptor;
    }

    @Bean
    @ConfigurationProperties(prefix = "petmate.cors")
    public CorsProps corsProps() {
        return new CorsProps();
    }

    /**
     * 请求体缓存过滤器：包裹写类请求的 Body，供紧急熔断拦截器读取（不破坏 @RequestBody 反序列化）。
     * 注册顺序放在字符编码过滤器之后、DispatcherServlet 之前即可。
     */
    @Bean
    public FilterRegistrationBean<RequestBodyCacheFilter> requestBodyCacheFilter() {
        FilterRegistrationBean<RequestBodyCacheFilter> bean =
                new FilterRegistrationBean<>(new RequestBodyCacheFilter());
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 100);
        return bean;
    }

    /**
     * 紧急熔断全局拦截器：一处开关（risk.emergency_break）、全局生效。
     * 作用于所有「用户内容写入」类路径；排除受信任后台、鉴权、交易/支付、静态资源等。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(emergencyBreakInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/admin/**",                                   // 受信任后台（含敏感词库编辑，避免自我拦截）
                        "/auth/**", "/login", "/register",
                        "/user/login", "/user/register",
                        "/pay/**", "/alipay/**",                       // 外部支付回调（不可控、无需扫描）
                        "/order/**", "/cart/**",                       // 交易/购物车操作
                        "/points/**", "/vip/**",                       // 鱼干/会员操作
                        "/static/**", "/error", "/actuator/**", "/favicon.ico",
                        "/track/**");   // 匿名埋点上报，无需熔断拦截/鉴权
    }

    @Bean
    public CorsFilter corsFilter(CorsProps props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.getAllowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("X-Trace-Id", "Authorization"));
        cfg.setAllowCredentials(true);
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return new CorsFilter(src);
    }

    /**
     * 把后端工作目录下的 static/ 目录暴露为 /static/**，
     * 解决头像上传后 /static/uploads/avatar/xxx.jpg 拿不到的问题。
     * （Spring Boot 默认只服务 classpath:/static/，运行时写在 user.dir 下的目录不会自动暴露。）
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 注意：Windows 下 Paths.get 会产生反斜杠，而 file: 协议 URL 必须用正斜杠，
        // 否则 Spring 的 UrlResource 无法解析（请求 /static/** 全部 404）。
        // 运行工作目录下的 static（新上传的文件落在这里，由上传接口写入）
        String runtimeDir = Paths.get(System.getProperty("user.dir"), "static")
                .toAbsolutePath().toString().replace("\\", "/");
        // 统一静态根：与 Vite /static 代理 rewrite 目标一致，历史 seed 商品图在此
        String baseDir = "D:/PetMate/static".replace("\\", "/");
        File baseFile = new File(baseDir);
        System.out.println("[WebConfig] registering /static/** -> base=" + baseDir
                + " (exists=" + baseFile.exists() + "), runtime=" + runtimeDir);
        // 同时注册两个位置：baseDir 优先(历史文件)，runtimeDir 兜底(新上传)
        if (baseFile.exists()) {
            registry.addResourceHandler("/static/**")
                    .addResourceLocations("file:///" + baseDir + "/", "file:///" + runtimeDir + "/");
        } else {
            registry.addResourceHandler("/static/**")
                    .addResourceLocations("file:///" + runtimeDir + "/");
        }
    }

    public static class CorsProps {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
        public List<String> getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(List<String> v) { this.allowedOrigins = v; }
    }
}
