package com.petmate.pay;

import com.petmate.common.BizException;
import com.petmate.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.*;

/**
 * 支付宝客户端（手写实现，不依赖 alipay-sdk-java）
 *
 * 支持两种支付方式：
 *  1) precreate：当面付，组装参数 + method=alipay.trade.precreate，返回 qr_code（扫码）
 *  2) pagePay：电脑网站支付，method=alipay.trade.page.pay，返回收银台表单 HTML（跳转）
 *
 * 二者均：用应用私钥 RSA2 签名 → POST 到 gateway → 解析响应。
 *
 * 异步通知验签（AlipayNotifyController 用）：
 *  - 解析表单参数
 *  - 排除 sign / sign_type，用应用公钥验签
 */
@Component
public class AlipayClient {

    private static final Logger log = LoggerFactory.getLogger(AlipayClient.class);

    private final AlipayProperties props;
    private final HttpClient http;

    public AlipayClient(AlipayProperties props) {
        this.props = props;
        // 默认安全：使用 JDK 系统信任证书校验。仅在显式 insecure=true（sandbox 临时应急）
        // 才走 buildInsecureClient()。
        this.http = props.isInsecure() ? buildInsecureClient() : buildDefaultClient();
    }

    /**
     * 默认 HttpClient：JDK 系统信任证书 + 标准 TLS hostname 校验。
     * 生产环境必须走这条路。
     */
    private static HttpClient buildDefaultClient() {
        java.net.ProxySelector ps = buildProxySelector();
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .proxy(ps)
                .build();
    }

    /**
     * 跳过 SSL 证书与 hostname 校验的 HttpClient。
     * 仅 sandbox 偶发证书过期时临时使用（由 alipay.insecure=true 触发）。
     * ⚠️ 生产环境绝对不要使用。
     */
    private static HttpClient buildInsecureClient() {
        try {
            javax.net.ssl.TrustManager[] tm = new javax.net.ssl.TrustManager[]{
                new javax.net.ssl.X509TrustManager() {
                    public void checkClientTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public void checkServerTrusted(java.security.cert.X509Certificate[] c, String a) {}
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[0];
                    }
                }
            };
            javax.net.ssl.SSLContext sc = javax.net.ssl.SSLContext.getInstance("TLS");
            sc.init(null, tm, new java.security.SecureRandom());
            javax.net.ssl.SSLParameters params = new javax.net.ssl.SSLParameters();
            params.setEndpointIdentificationAlgorithm(""); // 禁用 hostname 验证
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .sslContext(sc)
                    .sslParameters(params)
                    .proxy(buildProxySelector())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("创建支付宝沙箱 HTTP 客户端失败", e);
        }
    }

    /**
     * 代理选择策略：只在显式设置了 HTTPS_PROXY/HTTP_PROXY 环境变量时才走代理，
     * 不再做"沙箱兜底 127.0.0.1:7890"——开发机没装代理软件时硬指定反而导致 connect 超时。
     * 安全路径与 insecure 路径共用同一套代理策略。
     */
    private static java.net.ProxySelector buildProxySelector() {
        java.net.ProxySelector ps = java.net.ProxySelector.getDefault();
        String proxyEnv = System.getenv("HTTPS_PROXY");
        if (proxyEnv == null || proxyEnv.isEmpty()) proxyEnv = System.getenv("HTTP_PROXY");
        if (proxyEnv != null && !proxyEnv.isEmpty()) {
            try {
                URI pu = URI.create(proxyEnv);
                String ph = pu.getHost();
                int pp = pu.getPort() == -1 ? 80 : pu.getPort();
                if (ph != null && !ph.isEmpty()) {
                    ps = java.net.ProxySelector.of(new java.net.InetSocketAddress(ph, pp));
                    log.info("[alipay] using proxy {}", ph + ":" + pp);
                }
            } catch (Exception e) {
                log.warn("[alipay] parse proxy failed: {}", proxyEnv);
            }
        } else {
            log.info("[alipay] no proxy env, using direct connect");
        }
        return ps;
    }

    public static class PrecreateResult {
        public String outTradeNo;
        public String qrCode;     // 扫码字符串
        public String payUrl;     // 通常为空，当面付走 qrCode
    }

    public static class PagePayResult {
        public String outTradeNo;
        public String pageBody;   // 支付宝返回的自动提交表单 HTML（注入页面后 submit 即跳转收银台）
        public String payUrl;     // 兜底跳转 URL（gateway?签名串），pageBody 缺失时使用
    }

    /** 交易查询返回：trade_status 决定是否已支付 */
    public static class TradeQueryResult {
        public String outTradeNo;
        public String tradeNo;      // 支付宝交易号
        public String tradeStatus;  // TRADE_SUCCESS / TRADE_FINISHED / WAIT_BUYER_PAY / TRADE_CLOSED / null
    }

    public PrecreateResult precreate(String outTradeNo, BigDecimal amount, String subject) {
        if (!props.isEnabled()) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝未启用");
        }
        if (props.getAppId().isEmpty() || props.getPrivateKey().isEmpty()) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝 appId/privateKey 未配置");
        }

        Map<String, String> biz = new TreeMap<>();
        biz.put("app_id", props.getAppId());
        biz.put("method", "alipay.trade.precreate");
        biz.put("charset", props.getCharset());
        biz.put("sign_type", props.getSignType());
        biz.put("timestamp", isoTimestamp());
        biz.put("version", "1.0");
        biz.put("notify_url", props.getNotifyUrl());
        biz.put("biz_content",
            "{\"out_trade_no\":\"" + outTradeNo + "\","
            + "\"total_amount\":\"" + amount.toPlainString() + "\","
            + "\"subject\":\"" + escape(subject) + "\"}");

        // 签名
        String sign = sign(buildSignContent(biz), props.getPrivateKey());
        biz.put("sign", sign);

        // 组装 form
        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, String> e : biz.entrySet()) {
            if (form.length() > 0) form.append("&");
            form.append(urlEncode(e.getKey())).append("=").append(urlEncode(e.getValue()));
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getGateway()))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                log.warn("[alipay] precreate http {} body={}", resp.statusCode(), truncate(resp.body(), 300));
                throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝 HTTP " + resp.statusCode());
            }
            String body = resp.body();
            String qr = extractJsonField(body, "qr_code");
            if (qr == null || qr.isEmpty()) {
                log.warn("[alipay] precreate no qr_code body={}", truncate(body, 300));
                throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝返回无 qr_code");
            }
            PrecreateResult r = new PrecreateResult();
            r.outTradeNo = outTradeNo;
            r.qrCode = qr;
            r.payUrl = "";
            return r;
        } catch (BizException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            // 网关建立连接但上游无响应（沙箱 spanner 上游偶发 502/挂起），属支付宝侧临时不可用
            log.warn("[alipay] precreate timeout (Alipay sandbox gateway upstream unresponsive)");
            throw new BizException(ErrorCode.PAY_CREATE_FAILED,
                "支付宝沙箱网关暂不可用（上游无响应）。请稍后重试，或在支付宝沙箱控制台确认已开通「当面付」并 APPID 正确。");
        } catch (Exception e) {
            log.warn("[alipay] precreate error", e);
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("502") || msg.contains("Bad Gateway")) {
                throw new BizException(ErrorCode.PAY_CREATE_FAILED,
                    "支付宝沙箱网关返回 502（上游服务不可用）。请稍后重试或检查沙箱控制台「当面付」开通状态。");
            }
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝调用异常：" + msg);
        }
    }

    /**
     * 电脑网站支付（alipay.trade.page.pay）—— 使用 props.returnUrl 作为同步回跳基址。
     *
     * @param outTradeNo 商户订单号
     * @param orderNo    平台订单号（用于拼接 return_url 回跳支付页，便于前端轮询）
     * @param amount     金额
     * @param subject    订单标题
     * @return 收银台表单 HTML（pageBody）+ 兜底跳转 URL（payUrl）
     */
    public PagePayResult pagePay(String outTradeNo, String orderNo, BigDecimal amount, String subject) {
        return pagePay(outTradeNo, orderNo, amount, subject, null);
    }

    /**
     * 电脑网站支付（alipay.trade.page.pay）—— 支持自定义同步回跳基址（如会员支付回跳 /user/vip/result）。
     *
     * @param outTradeNo        商户订单号
     * @param orderNo            平台订单号（用于拼接 return_url 回跳支付页路径段）
     * @param amount             金额
     * @param subject            订单标题
     * @param returnUrlOverride  同步回跳基址覆盖（非空时取代 props.returnUrl）
     * @return 收银台表单 HTML（pageBody）+ 兜底跳转 URL（payUrl）
     */
    public PagePayResult pagePay(String outTradeNo, String orderNo, BigDecimal amount, String subject,
                                 String returnUrlOverride) {
        if (!props.isEnabled()) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝未启用");
        }
        if (props.getAppId().isEmpty() || props.getPrivateKey().isEmpty()) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝 appId/privateKey 未配置");
        }

        String returnUrl = buildReturnUrl(orderNo, returnUrlOverride);

        Map<String, String> biz = new TreeMap<>();
        biz.put("app_id", props.getAppId());
        biz.put("method", "alipay.trade.page.pay");
        biz.put("charset", props.getCharset());
        biz.put("sign_type", props.getSignType());
        biz.put("timestamp", isoTimestamp());
        biz.put("version", "1.0");
        biz.put("notify_url", props.getNotifyUrl());
        biz.put("return_url", returnUrl);
        biz.put("biz_content",
            "{\"out_trade_no\":\"" + outTradeNo + "\","
            + "\"product_code\":\"FAST_INSTANT_TRADE_PAY\","
            + "\"total_amount\":\"" + amount.toPlainString() + "\","
            + "\"subject\":\"" + escape(subject) + "\"}");

        String sign = sign(buildSignContent(biz), props.getPrivateKey());
        biz.put("sign", sign);

        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, String> e : biz.entrySet()) {
            if (form.length() > 0) form.append("&");
            form.append(urlEncode(e.getKey())).append("=").append(urlEncode(e.getValue()));
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getGateway()))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // 电脑网站支付：沙箱网关通常直接 302 跳转到收银台（appAssign.htm?...），属正常成功响应
            if (resp.statusCode() == 302) {
                String loc = resp.headers().firstValue("Location").orElse(null);
                if (loc != null && !loc.isEmpty()) {
                    PagePayResult r = new PagePayResult();
                    r.outTradeNo = outTradeNo;
                    r.pageBody = null;
                    r.payUrl = loc; // 收银台跳转地址（浏览器打开即进入支付宝收银台）
                    return r;
                }
                log.warn("[alipay] pagePay 302 但无 Location 头");
                throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝未返回收银台跳转地址");
            }
            if (resp.statusCode() != 200) {
                log.warn("[alipay] pagePay http {} body={}", resp.statusCode(), truncate(resp.body(), 300));
                throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝 HTTP " + resp.statusCode());
            }
            String body = resp.body();
            // 用 Jackson 解析嵌套响应（body 字段含 HTML，正则易截断，必须用 JSON 解析器）
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = om.readValue(body, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> respObj = (Map<String, Object>) root.get("alipay_trade_page_pay_response");
            if (respObj == null) {
                log.warn("[alipay] pagePay no response node body={}", truncate(body, 300));
                throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝返回格式异常");
            }
            String code = String.valueOf(respObj.get("code"));
            if (!"10000".equals(code)) {
                String subMsg = String.valueOf(respObj.getOrDefault("sub_msg", respObj.get("msg")));
                log.warn("[alipay] pagePay biz fail code={} msg={}", code, subMsg);
                throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝：" + subMsg);
            }
            Object bodyObj = respObj.get("body");
            String pageBody = bodyObj == null ? null : String.valueOf(bodyObj);

            PagePayResult r = new PagePayResult();
            r.outTradeNo = outTradeNo;
            r.pageBody = pageBody;
            r.payUrl = props.getGateway() + "?" + form.toString(); // 兜底：直接跳转网关
            return r;
        } catch (BizException e) {
            throw e;
        } catch (java.net.http.HttpTimeoutException e) {
            // 网关建立连接但上游无响应（沙箱 spanner 上游偶发 502/挂起），属支付宝侧临时不可用
            log.warn("[alipay] pagePay timeout (Alipay sandbox gateway upstream unresponsive)");
            throw new BizException(ErrorCode.PAY_CREATE_FAILED,
                "支付宝沙箱网关暂不可用（上游无响应）。请稍后重试，或在支付宝沙箱控制台确认已开通「电脑网站支付」并 APPID 正确。");
        } catch (Exception e) {
            log.warn("[alipay] pagePay error", e);
            String msg = e.getMessage() == null ? "" : e.getMessage();
            if (msg.contains("502") || msg.contains("Bad Gateway")) {
                throw new BizException(ErrorCode.PAY_CREATE_FAILED,
                    "支付宝沙箱网关返回 502（上游服务不可用）。请稍后重试或检查沙箱控制台「电脑网站支付」开通状态。");
            }
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝调用异常：" + msg);
        }
    }

    /**
     * 主动查询支付宝交易状态（alipay.trade.query）。
     * 用途：沙箱异步通知无法回打 localhost，由前端轮询驱动后端主动查询并开通会员。
     */
    public TradeQueryResult tradeQuery(String outTradeNo) {
        if (!props.isEnabled()) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝未启用");
        }
        Map<String, String> biz = new TreeMap<>();
        biz.put("app_id", props.getAppId());
        biz.put("method", "alipay.trade.query");
        biz.put("charset", props.getCharset());
        biz.put("sign_type", props.getSignType());
        biz.put("timestamp", isoTimestamp());
        biz.put("version", "1.0");
        biz.put("biz_content", "{\"out_trade_no\":\"" + outTradeNo + "\"}");
        String sign = sign(buildSignContent(biz), props.getPrivateKey());
        biz.put("sign", sign);

        StringBuilder form = new StringBuilder();
        for (Map.Entry<String, String> e : biz.entrySet()) {
            if (form.length() > 0) form.append("&");
            form.append(urlEncode(e.getKey())).append("=").append(urlEncode(e.getValue()));
        }

        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getGateway()))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(form.toString(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200) {
                throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝查询 HTTP " + resp.statusCode());
            }
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Object> root = om.readValue(resp.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> respObj = (Map<String, Object>) root.get("alipay_trade_query_response");
            TradeQueryResult r = new TradeQueryResult();
            r.outTradeNo = outTradeNo;
            if (respObj == null) {
                r.tradeStatus = null;
                return r;
            }
            r.tradeStatus = respObj.get("trade_status") == null ? null : String.valueOf(respObj.get("trade_status"));
            r.tradeNo = respObj.get("trade_no") == null ? null : String.valueOf(respObj.get("trade_no"));
            return r;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[alipay] tradeQuery error", e);
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "支付宝查询异常：" + e.getMessage());
        }
    }

    /** 拼接同步回跳地址：returnUrl 基址 + "/{orderNo}"（前端支付页路由，便于回跳后继续轮询） */
    private String buildReturnUrl(String orderNo, String returnUrlOverride) {
        if (returnUrlOverride != null && !returnUrlOverride.isEmpty()) {
            // 前端动态 origin 的「根路径 + 查询参数」模式已自带完整 URL，原样返回（避免再拼 /{orderNo}）
            if (returnUrlOverride.contains("?")) return returnUrlOverride;
            String base = returnUrlOverride;
            if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            return base + "/" + orderNo;
        }
        String base = props.getReturnUrl();
        if (base == null || base.isEmpty()) base = "http://localhost:5173/pay";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/" + orderNo;
    }

    /**
     * 异步通知验签
     * @param params 支付宝 POST 表单参数（含 sign/sign_type）
     * @return true 表示签名通过
     */
    public boolean verifyNotify(Map<String, String> params) {
        if (props.getAlipayPublicKey() == null || props.getAlipayPublicKey().isEmpty()) {
            // R2.1: 公钥未配置时不再静默 return true（生产会被攻击者伪造通知）。
            // 此时 enabled 必须是 false（演示态），不应有真实回调进入此路径；
            // 若 enabled=true 且公钥为空 → 配置错误，直接拒绝。
            log.error("[alipay] alipayPublicKey 未配置,生产风险：可能接受伪造通知！请在 application.yml 设置 petmate.alipay.alipay-public-key");
            return false;
        }
        String sign = params.get("sign");
        String signType = params.getOrDefault("sign_type", props.getSignType());
        if (sign == null || sign.isEmpty()) {
            log.warn("[alipay] 通知缺少 sign 字段");
            return false;
        }

        // 排除 sign / sign_type
        Map<String, String> sorted = new TreeMap<>();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if ("sign".equals(e.getKey()) || "sign_type".equals(e.getKey())) continue;
            sorted.put(e.getKey(), e.getValue());
        }
        String content = buildSignContent(sorted);

        try {
            PublicKey pub = loadPublicKey(props.getAlipayPublicKey());
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(pub);
            sig.update(content.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(sign));
        } catch (Exception e) {
            log.warn("[alipay] verifyNotify error", e);
            return false;
        }
    }

    // ============= 工具方法 =============

    /** 拼接待签名字符串（按 key 排序，& 连接，剔除空值） */
    static String buildSignContent(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            String v = e.getValue();
            if (v == null || v.isEmpty()) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(e.getKey()).append("=").append(v);
        }
        return sb.toString();
    }

    /** RSA2 签名（应用私钥，PKCS8） */
    private static String sign(String content, String privateKeyPem) {
        try {
            PrivateKey key = loadPrivateKey(privateKeyPem);
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initSign(key);
            sig.update(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "签名失败：" + e.getMessage());
        }
    }

    private static PrivateKey loadPrivateKey(String pem) {
        String content = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                            .replaceAll("-----END [A-Z ]+-----", "")
                            .replaceAll("\\s+", "");
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(content));
            return KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PAY_CREATE_FAILED, "应用私钥格式错误（需 PKCS8 PEM）");
        }
    }

    private static PublicKey loadPublicKey(String pem) {
        String content = pem.replaceAll("-----BEGIN [A-Z ]+-----", "")
                            .replaceAll("-----END [A-Z ]+-----", "")
                            .replaceAll("\\s+", "");
        try {
            X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.getDecoder().decode(content));
            return KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception e) {
            throw new IllegalArgumentException("公钥格式错误", e);
        }
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String isoTimestamp() {
        return java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /** 极简 JSON 字段提取（不引 fastjson/Gson） */
    static String extractJsonField(String json, String field) {
        if (json == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("\"" + java.util.regex.Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"")
            .matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}