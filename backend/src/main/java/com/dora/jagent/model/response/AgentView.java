package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentView {

    private String id;

    private String name;

    private String description;

    private String defaultModelKey;

    private List<String> allowedKnowledgeBaseIds;

    private List<String> allowedKnowledgeBaseNames;

    private boolean knowledgeEnabled;
}
