package com.dora.jagent.repository;

import com.dora.jagent.model.entity.Agent;

import java.util.List;
import java.util.Optional;

public interface AgentRepository {

    List<Agent> findAllOrderByCreatedAtAsc();

    Optional<Agent> findById(String agentId);

    Agent save(Agent agent);

    Agent updateAllowedKbs(String agentId, String allowedKbs);
}
