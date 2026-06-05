package com.dora.jagent.model.request;

import lombok.Data;

import java.util.List;

@Data
public class UpdateAgentKnowledgeBasesRequest {

    private List<String> knowledgeBaseIds;
}
