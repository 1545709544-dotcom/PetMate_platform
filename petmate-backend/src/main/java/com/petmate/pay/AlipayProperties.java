package com.petmate.pay;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 支付宝当面付配置
 *
 * petmate.alipay.* 配置项：
 *   enabled            true=走真实预下单；false=演示态直接置 paid
 *   app-id             沙箱/生产 appId
 *   private-key        应用私钥（PKCS8 格式 PEM，去头去尾换行）
 *   alipay-public-key  支付宝公钥（验签用）
 *   gateway            https://openapi.alipaydev.com/gateway.do（沙箱）
 *   notify-url         异步通知 URL（公网可达，沙箱可用 ngrok）
 *   return-url         同步回跳地址（浏览器跳转，本地 http://localhost:5173/pay 即可）
 *   sign-type          RSA2（默认）
 *   charset            UTF-8
 */
@Component
@ConfigurationProperties(prefix = "petmate.alipay")
public class AlipayProperties {
    private boolean enabled = false;
    private String appId = "";
    private String privateKey = "";
    private String alipayPublicKey = "";
    private String gateway = "https://openapi.alipaydev.com/gateway.do";
    private String notifyUrl = "";
    private String returnUrl = "http://localhost:5173/pay";
    private String signType = "RSA2";
    private String charset = "UTF-8";
    /** 是否跳过 TLS 证书与 hostname 校验（仅 sandbox 偶发证书过期时临时用） */
    private boolean insecure = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public String getAlipayPublicKey() { return alipayPublicKey; }
    public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }

    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }

    public String getCharset() { return charset; }
    public void setCharset(String charset) { this.charset = charset; }

    public boolean isInsecure() { return insecure; }
    public void setInsecure(boolean insecure) { this.insecure = insecure; }
}