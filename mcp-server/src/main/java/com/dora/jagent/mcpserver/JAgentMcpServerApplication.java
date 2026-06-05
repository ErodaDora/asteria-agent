package com.dora.jagent.mcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.dora.jagent")
public class JAgentMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JAgentMcpServerApplication.class, args);
    }
}
