package com.dora.jagent.service;

import com.dora.jagent.model.entity.Agent;
import com.dora.jagent.model.request.CreateAgentRequest;
import com.dora.jagent.model.request.UpdateAgentKnowledgeBasesRequest;
import com.dora.jagent.model.response.AgentView;

import java.util.List;

public interface AgentService {

    List<AgentView> getAgents();

    Agent getRequiredAgent(String agentId);

    AgentView createAgent(CreateAgentRequest request);

    AgentView updateAllowedKnowledgeBases(String agentId, UpdateAgentKnowledgeBasesRequest request);
}
