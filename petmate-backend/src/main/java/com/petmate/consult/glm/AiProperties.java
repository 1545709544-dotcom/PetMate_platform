package com.petmate.consult.glm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 问诊配置（GLM-4-Flash-250414 / 智谱开放平台）
 *
 * petmate.ai.* 配置项：
 *   enabled       true=真实调用；false=降级到原关键词 mock
 *   provider      glm / openai  （当前仅 glm 实现）
 *   api-key       智谱 API Key（生产从环境变量 ZHIPU_API_KEY 注入）
 *   base-url      https://open.bigmodel.cn/api/paas/v4
 *   model         glm-4-flash-250414
 *   timeout-ms    HTTP 超时（默认 8000）
 */
@Component
@ConfigurationProperties(prefix = "petmate.ai")
public class AiProperties {
    private boolean enabled = false;
    private String provider = "glm";
    private String apiKey = "";
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private String model = "glm-4-flash-250414";
    private String visionModel = "glm-4v-flash";
    private int timeoutMs = 8000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getVisionModel() { return visionModel; }
    public void setVisionModel(String visionModel) { this.visionModel = visionModel; }

    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
}