package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.Agent;
import com.dora.jagent.repository.AgentKnowledgeBaseRepository;
import com.dora.jagent.repository.AgentRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcAgentRepository implements AgentRepository {

    private final JdbcTemplate jdbcTemplate;
    private final AgentKnowledgeBaseRepository agentKnowledgeBaseRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<Agent> findAllOrderByCreatedAtAsc() {
        String sql = """
                SELECT
                    a.id,
                    a.name,
                    a.description,
                    a.system_prompt,
                    a.default_model_key,
                    COALESCE(
                        jsonb_agg(akb.kb_id::text ORDER BY akb.created_at ASC, akb.kb_id ASC)
                            FILTER (WHERE akb.kb_id IS NOT NULL),
                        COALESCE(a.allowed_kbs, '[]'::jsonb)
                    ) AS allowed_kbs,
                    a.created_at,
                    a.updated_at
                FROM jagent_agent
                a
                LEFT JOIN agent_knowledge_base akb
                    ON akb.agent_id = a.id
                GROUP BY a.id
                ORDER BY a.created_at ASC
                """;
        return jdbcTemplate.query(sql, new AgentRowMapper());
    }

    @Override
    public Optional<Agent> findById(String agentId) {
        String sql = """
                SELECT
                    a.id,
                    a.name,
                    a.description,
                    a.system_prompt,
                    a.default_model_key,
                    COALESCE(
                        jsonb_agg(akb.kb_id::text ORDER BY akb.created_at ASC, akb.kb_id ASC)
                            FILTER (WHERE akb.kb_id IS NOT NULL),
                        COALESCE(a.allowed_kbs, '[]'::jsonb)
                    ) AS allowed_kbs,
                    a.created_at,
                    a.updated_at
                FROM jagent_agent
                a
                LEFT JOIN agent_knowledge_base akb
                    ON akb.agent_id = a.id
                WHERE a.id = CAST(? AS uuid)
                GROUP BY a.id
                """;
        List<Agent> agents = jdbcTemplate.query(sql, new AgentRowMapper(), agentId);
        return agents.stream().findFirst();
    }

    @Override
    public Agent save(Agent agent) {
        String sql = """
                INSERT INTO jagent_agent (
                    id, name, description, system_prompt, default_model_key, allowed_kbs, created_at, updated_at
                )
                VALUES (
                    CAST(? AS uuid), ?, ?, ?, ?, CAST(? AS jsonb), ?, ?
                )
                """;
        jdbcTemplate.update(
                sql,
                agent.getId(),
                agent.getName(),
                agent.getDescription(),
                agent.getSystemPrompt(),
                agent.getDefaultModelKey(),
                agent.getAllowedKbs(),
                Timestamp.valueOf(agent.getCreatedAt()),
                Timestamp.valueOf(agent.getUpdatedAt())
        );
        agentKnowledgeBaseRepository.replaceKnowledgeBaseIds(agent.getId(), parseAllowedKbs(agent.getAllowedKbs()));
        return agent;
    }

    @Override
    public Agent updateAllowedKbs(String agentId, String allowedKbs) {
        String sql = """
                UPDATE jagent_agent
                SET allowed_kbs = CAST(? AS jsonb),
                    updated_at = NOW()
                WHERE id = CAST(? AS uuid)
                """;
        jdbcTemplate.update(sql, allowedKbs, agentId);
        agentKnowledgeBaseRepository.replaceKnowledgeBaseIds(agentId, parseAllowedKbs(allowedKbs));
        return findById(agentId).orElseThrow();
    }

    private List<String> parseAllowedKbs(String allowedKbs) {
        if (!StringUtils.hasText(allowedKbs)) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(allowedKbs, new TypeReference<List<String>>() {});
            return ids == null ? List.of() : ids.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        } catch (Exception e) {
            return List.of();
        }
    }

    private static class AgentRowMapper implements RowMapper<Agent> {
        @Override
        public Agent mapRow(ResultSet rs, int rowNum) throws SQLException {
            return Agent.builder()
                    .id(rs.getString("id"))
                    .name(rs.getString("name"))
                    .description(rs.getString("description"))
                    .systemPrompt(rs.getString("system_prompt"))
                    .defaultModelKey(rs.getString("default_model_key"))
                    .allowedKbs(rs.getString("allowed_kbs"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
