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
public class KnowledgeDocument {

    private String id;

    private String kbId;

    private String filename;

    private String filetype;

    private Long size;

    // 先直接存 JSON 字符串，后面再按需要抽结构。
    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
