package com.petmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * PetMate 智能宠物管家 — 后端入口
 *
 * 技术栈：Spring Boot 3.3 + JDK 21 + MySQL 8 + JdbcTemplate
 * 兼容 PRD §11 选型。
 *
 * v17: 开启 @EnableScheduling 以驱动 TokenBlacklist 过期清理与订单超时关闭。
 */
@SpringBootApplication
@EnableScheduling
public class PetMateApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetMateApplication.class, args);
    }
}