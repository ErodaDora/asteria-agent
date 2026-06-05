package com.dora.jagent.model.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateKnowledgeBaseRequest {

    private String name;

    private String description;

    // 当前先直接接 JSON 字符串，后续如果需要再抽成结构化对象。
    private String metadata;
}
