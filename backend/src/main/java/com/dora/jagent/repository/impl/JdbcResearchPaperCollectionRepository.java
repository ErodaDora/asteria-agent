package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.ResearchPaperCollection;
import com.dora.jagent.repository.ResearchPaperCollectionRepository;
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
public class JdbcResearchPaperCollectionRepository implements ResearchPaperCollectionRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ResearchPaperCollection> findByCollectionNameOrderByCreatedAtDesc(String collectionName) {
        String sql = """
                SELECT id, paper_id, collection_name, note, status, created_at, updated_at
                FROM research_paper_collection
                WHERE collection_name = ?
                ORDER BY created_at DESC
                """;
        return jdbcTemplate.query(sql, new ResearchPaperCollectionRowMapper(), collectionName);
    }

    @Override
    public Optional<ResearchPaperCollection> findByPaperIdAndCollectionName(String paperId, String collectionName) {
        String sql = """
                SELECT id, paper_id, collection_name, note, status, created_at, updated_at
                FROM research_paper_collection
                WHERE paper_id = CAST(? AS uuid)
                  AND collection_name = ?
                """;
        return jdbcTemplate.query(sql, new ResearchPaperCollectionRowMapper(), paperId, collectionName)
                .stream()
                .findFirst();
    }

    @Override
    public ResearchPaperCollection save(ResearchPaperCollection collection) {
        String sql = """
                INSERT INTO research_paper_collection (
                    id, paper_id, collection_name, note, status, created_at, updated_at
                )
                VALUES (
                    CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?, ?, ?
                )
                ON CONFLICT (paper_id, collection_name) DO UPDATE SET
                    note = COALESCE(EXCLUDED.note, research_paper_collection.note),
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at
                """;
        jdbcTemplate.update(
                sql,
                collection.getId(),
                collection.getPaperId(),
                collection.getCollectionName(),
                collection.getNote(),
                collection.getStatus(),
                Timestamp.valueOf(collection.getCreatedAt()),
                Timestamp.valueOf(collection.getUpdatedAt())
        );
        return findByPaperIdAndCollectionName(collection.getPaperId(), collection.getCollectionName())
                .orElse(collection);
    }

    @Override
    public void updateStatus(String paperId, String collectionName, String status) {
        String sql = """
                UPDATE research_paper_collection
                SET status = ?,
                    updated_at = ?
                WHERE paper_id = CAST(? AS uuid)
                  AND collection_name = ?
                """;
        jdbcTemplate.update(sql, status, Timestamp.valueOf(java.time.LocalDateTime.now()), paperId, collectionName);
    }

    private static class ResearchPaperCollectionRowMapper implements RowMapper<ResearchPaperCollection> {
        @Override
        public ResearchPaperCollection mapRow(ResultSet rs, int rowNum) throws SQLException {
            return ResearchPaperCollection.builder()
                    .id(rs.getString("id"))
                    .paperId(rs.getString("paper_id"))
                    .collectionName(rs.getString("collection_name"))
                    .note(rs.getString("note"))
                    .status(rs.getString("status"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
