package com.dora.jagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// @SpringBootApplication 是 Spring Boot 项目的总入口注解。
// 它等价于几个常用注解的组合，表示：
// 1. 这是一个 Spring Boot 应用
// 2. 启动时会自动做组件扫描
// 3. 会启用自动配置
@SpringBootApplication
public class JAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(JAgentApplication.class, args);
    }
}
