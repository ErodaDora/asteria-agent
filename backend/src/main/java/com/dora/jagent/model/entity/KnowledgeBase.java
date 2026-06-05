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
public class KnowledgeBase {

    private String id;

    private String name;

    private String description;

    // 先直接用 JSON 字符串承接，保持和当前项目的轻量 JDBC 风格一致。
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
