package com.dora.jagent.repository;

import java.util.List;

public interface AgentKnowledgeBaseRepository {

    List<String> findKnowledgeBaseIdsByAgentId(String agentId);

    void replaceKnowledgeBaseIds(String agentId, List<String> knowledgeBaseIds);
}
