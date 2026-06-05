package com.dora.jagent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Agent {

    // 这是进入多智能体阶段后的核心业务实体，
    // 它不是 config，也不是框架 Bean。
    // 它表达的是“数据库里一个可配置、可持久化的智能体对象”。
    private String id;

    private String name;

    private String description;

    private String systemPrompt;

    private String defaultModelKey;

    private String allowedKbs;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
