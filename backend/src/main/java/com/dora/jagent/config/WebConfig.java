package com.dora.jagent.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/auth/me", "/api/chat/**", "/api/agents/**", "/api/workspace/**", "/api/xhs/**")
                .excludePathPatterns(
                        "/", "/index.html", "/styles.css", "/app.js",
                        "/workspace.html", "/workspace.css", "/workspace.js",
                        "/chat.html", "/chat.css", "/chat.js",
                        "/agent-chat.html", "/agent-chat.js",
                        "/xhs-agent.html", "/xhs-agent.css", "/xhs-agent.js",
                        "/research-agent.html", "/research-agent.css", "/research-agent.js"
                );
    }
}
