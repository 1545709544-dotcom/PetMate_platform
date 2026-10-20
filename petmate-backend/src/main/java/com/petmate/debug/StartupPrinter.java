package com.petmate.debug;

import com.petmate.consult.glm.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时打印关键配置，便于排查环境变量是否注入。
 * 生产可删除。
 */
@Component
public class StartupPrinter implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StartupPrinter.class);
    private final AiProperties ai;

    public StartupPrinter(AiProperties ai) {
        this.ai = ai;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("[startup] ai.enabled={} provider={} model={} apiKeyLen={} baseUrl={}",
            ai.isEnabled(), ai.getProvider(), ai.getModel(),
            ai.getApiKey() == null ? 0 : ai.getApiKey().length(),
            ai.getBaseUrl());
    }
}
