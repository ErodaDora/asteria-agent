package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.Agent;
import com.dora.jagent.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcAgentRepository implements AgentRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<Agent> findAllOrderByCreatedAtAsc() {
        String sql = """
                SELECT id, name, description, system_prompt, default_model_key, allowed_kbs, created_at, updated_at
                FROM jagent_agent
                ORDER BY created_at ASC
                """;
        return jdbcTemplate.query(sql, new AgentRowMapper());
    }

    @Override
    public Optional<Agent> findById(String agentId) {
        String sql = """
                SELECT id, name, description, system_prompt, default_model_key, allowed_kbs, created_at, updated_at
                FROM jagent_agent
                WHERE id = CAST(? AS uuid)
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
        return findById(agentId).orElseThrow();
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
