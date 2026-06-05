package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.KnowledgeBase;
import com.dora.jagent.repository.KnowledgeBaseRepository;
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
public class JdbcKnowledgeBaseRepository implements KnowledgeBaseRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<KnowledgeBase> findAllOrderByCreatedAtAsc() {
        String sql = """
                SELECT id, name, description, metadata, created_at, updated_at
                FROM knowledge_base
                ORDER BY created_at ASC
                """;
        return jdbcTemplate.query(sql, new KnowledgeBaseRowMapper());
    }

    @Override
    public Optional<KnowledgeBase> findById(String knowledgeBaseId) {
        String sql = """
                SELECT id, name, description, metadata, created_at, updated_at
                FROM knowledge_base
                WHERE id = CAST(? AS uuid)
                """;
        List<KnowledgeBase> knowledgeBases = jdbcTemplate.query(sql, new KnowledgeBaseRowMapper(), knowledgeBaseId);
        return knowledgeBases.stream().findFirst();
    }

    @Override
    public KnowledgeBase save(KnowledgeBase knowledgeBase) {
        String sql = """
                INSERT INTO knowledge_base (id, name, description, metadata, created_at, updated_at)
                VALUES (CAST(? AS uuid), ?, ?, CAST(? AS jsonb), ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                knowledgeBase.getId(),
                knowledgeBase.getName(),
                knowledgeBase.getDescription(),
                knowledgeBase.getMetadata(),
                Timestamp.valueOf(knowledgeBase.getCreatedAt()),
                Timestamp.valueOf(knowledgeBase.getUpdatedAt())
        );
        return knowledgeBase;
    }

    private static class KnowledgeBaseRowMapper implements RowMapper<KnowledgeBase> {
        @Override
        public KnowledgeBase mapRow(ResultSet rs, int rowNum) throws SQLException {
            return KnowledgeBase.builder()
                    .id(rs.getString("id"))
                    .name(rs.getString("name"))
                    .description(rs.getString("description"))
                    .metadata(rs.getString("metadata"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
