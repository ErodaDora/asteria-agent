package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.Agent;
import com.dora.jagent.model.request.CreateAgentRequest;
import com.dora.jagent.model.request.UpdateAgentKnowledgeBasesRequest;
import com.dora.jagent.model.response.AgentView;
import com.dora.jagent.repository.AgentRepository;
import com.dora.jagent.repository.KnowledgeBaseRepository;
import com.dora.jagent.service.AgentService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentRepository agentRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<AgentView> getAgents() {
        return agentRepository.findAllOrderByCreatedAtAsc().stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public Agent getRequiredAgent(String agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new BizException("agent not found"));
    }

    @Override
    public AgentView createAgent(CreateAgentRequest request) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("agent name cannot be blank");
        }

        String selectedModelKey = normalizeModelKey(request);
        String allowedKbsJson = serializeAllowedKnowledgeBaseIds(request.getAllowedKnowledgeBaseIds());
        String systemPrompt = StringUtils.hasText(request.getSystemPrompt())
                ? request.getSystemPrompt().trim()
                : buildDefaultSystemPrompt(request.getName().trim());

        LocalDateTime now = LocalDateTime.now();
        Agent agent = Agent.builder()
                .id(UUID.randomUUID().toString())
                .name(request.getName().trim())
                .description(trimToNull(request.getDescription()))
                .systemPrompt(systemPrompt)
                .defaultModelKey(selectedModelKey)
                .allowedKbs(allowedKbsJson)
                .createdAt(now)
                .updatedAt(now)
                .build();

        return toView(agentRepository.save(agent));
    }

    @Override
    public AgentView updateAllowedKnowledgeBases(String agentId, UpdateAgentKnowledgeBasesRequest request) {
        Agent agent = getRequiredAgent(agentId);

        List<String> requestedIds = request == null || request.getKnowledgeBaseIds() == null
                ? List.of()
                : request.getKnowledgeBaseIds();

        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        for (String requestedId : requestedIds) {
            if (!StringUtils.hasText(requestedId)) {
                continue;
            }
            String normalizedId = requestedId.trim();
            knowledgeBaseRepository.findById(normalizedId)
                    .orElseThrow(() -> new BizException("knowledge base not found: " + normalizedId));
            normalizedIds.add(normalizedId);
        }

        String allowedKbsJson;
        try {
            allowedKbsJson = objectMapper.writeValueAsString(new ArrayList<>(normalizedIds));
        } catch (Exception e) {
            throw new BizException("failed to serialize allowed knowledge bases");
        }

        Agent updated = agentRepository.updateAllowedKbs(agent.getId(), allowedKbsJson);
        return toView(updated);
    }

    private AgentView toView(Agent agent) {
        List<String> allowedKnowledgeBaseIds = parseAllowedKnowledgeBaseIds(agent.getAllowedKbs());
        List<String> allowedKnowledgeBaseNames = allowedKnowledgeBaseIds.stream()
                .map(knowledgeBaseRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(kb -> kb.getName())
                .toList();

        return AgentView.builder()
                .id(agent.getId())
                .name(agent.getName())
                .description(agent.getDescription())
                .defaultModelKey(agent.getDefaultModelKey())
                .allowedKnowledgeBaseIds(allowedKnowledgeBaseIds)
                .allowedKnowledgeBaseNames(allowedKnowledgeBaseNames)
                .knowledgeEnabled(!allowedKnowledgeBaseIds.isEmpty())
                .build();
    }

    private List<String> parseAllowedKnowledgeBaseIds(String allowedKbs) {
        if (!StringUtils.hasText(allowedKbs)) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(allowedKbs, new TypeReference<List<String>>() {});
            return ids == null ? List.of() : ids.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            throw new BizException("failed to parse agent allowed knowledge bases");
        }
    }

    private String serializeAllowedKnowledgeBaseIds(List<String> requestedIds) {
        LinkedHashSet<String> normalizedIds = new LinkedHashSet<>();
        if (requestedIds != null) {
            for (String requestedId : requestedIds) {
                if (!StringUtils.hasText(requestedId)) {
                    continue;
                }
                String normalizedId = requestedId.trim();
                knowledgeBaseRepository.findById(normalizedId)
                        .orElseThrow(() -> new BizException("knowledge base not found: " + normalizedId));
                normalizedIds.add(normalizedId);
            }
        }

        try {
            return objectMapper.writeValueAsString(new ArrayList<>(normalizedIds));
        } catch (Exception e) {
            throw new BizException("failed to serialize allowed knowledge bases");
        }
    }

    private String normalizeModelKey(CreateAgentRequest request) {
        if (request == null) {
            return "deepseek-chat";
        }
        if (StringUtils.hasText(request.getDefaultModelKey())) {
            return request.getDefaultModelKey().trim();
        }
        if (StringUtils.hasText(request.getModelName())) {
            return request.getModelName().trim();
        }
        return "deepseek-chat";
    }

    private String buildDefaultSystemPrompt(String agentName) {
        return "你是 JAgent 的智能体“" + agentName + "”，请先给出简洁、清楚、面向执行的回答。";
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }
}
