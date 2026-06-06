package com.dora.jagent.repository.impl;

import com.dora.jagent.repository.AgentKnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcAgentKnowledgeBaseRepository implements AgentKnowledgeBaseRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<String> findKnowledgeBaseIdsByAgentId(String agentId) {
        String sql = """
                SELECT kb_id
                FROM agent_knowledge_base
                WHERE agent_id = CAST(? AS uuid)
                ORDER BY created_at ASC, kb_id ASC
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("kb_id"), agentId);
    }

    @Override
    public void replaceKnowledgeBaseIds(String agentId, List<String> knowledgeBaseIds) {
        jdbcTemplate.update(
                "DELETE FROM agent_knowledge_base WHERE agent_id = CAST(? AS uuid)",
                agentId
        );
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return;
        }

        String sql = """
                INSERT INTO agent_knowledge_base (agent_id, kb_id, created_at)
                VALUES (CAST(? AS uuid), CAST(? AS uuid), ?)
                ON CONFLICT (agent_id, kb_id) DO NOTHING
                """;
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        for (String knowledgeBaseId : knowledgeBaseIds) {
            jdbcTemplate.update(sql, agentId, knowledgeBaseId, now);
        }
    }
}
