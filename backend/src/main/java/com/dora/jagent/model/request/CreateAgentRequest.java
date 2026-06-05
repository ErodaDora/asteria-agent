package com.dora.jagent.model.request;

import lombok.Data;

import java.util.List;

@Data
public class CreateAgentRequest {

    private String name;

    private String description;

    private String systemPrompt;

    // 前端当前传的是 modelName，先兼容它。
    private String modelName;

    // 也兼容后续如果前端改成 defaultModelKey。
    private String defaultModelKey;

    private List<String> allowedKnowledgeBaseIds;
}
