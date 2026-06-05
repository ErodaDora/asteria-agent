package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.KnowledgeDocument;
import com.dora.jagent.repository.KnowledgeDocumentRepository;
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
public class JdbcKnowledgeDocumentRepository implements KnowledgeDocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<KnowledgeDocument> findById(String documentId) {
        String sql = """
                SELECT id, kb_id, filename, filetype, size, metadata, created_at, updated_at
                FROM document
                WHERE id = CAST(? AS uuid)
                """;
        List<KnowledgeDocument> documents = jdbcTemplate.query(sql, new KnowledgeDocumentRowMapper(), documentId);
        return documents.stream().findFirst();
    }

    @Override
    public Optional<KnowledgeDocument> findByKbIdAndFilename(String knowledgeBaseId, String filename) {
        String sql = """
                SELECT id, kb_id, filename, filetype, size, metadata, created_at, updated_at
                FROM document
                WHERE kb_id = CAST(? AS uuid)
                  AND filename = ?
                ORDER BY created_at DESC
                LIMIT 1
                """;
        List<KnowledgeDocument> documents = jdbcTemplate.query(
                sql,
                new KnowledgeDocumentRowMapper(),
                knowledgeBaseId,
                filename
        );
        return documents.stream().findFirst();
    }

    @Override
    public List<KnowledgeDocument> findByKbIdOrderByCreatedAtAsc(String knowledgeBaseId) {
        String sql = """
                SELECT id, kb_id, filename, filetype, size, metadata, created_at, updated_at
                FROM document
                WHERE kb_id = CAST(? AS uuid)
                ORDER BY created_at ASC
                """;
        return jdbcTemplate.query(sql, new KnowledgeDocumentRowMapper(), knowledgeBaseId);
    }

    @Override
    public KnowledgeDocument save(KnowledgeDocument document) {
        String sql = """
                INSERT INTO document (id, kb_id, filename, filetype, size, metadata, created_at, updated_at)
                VALUES (CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?, CAST(? AS jsonb), ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                document.getId(),
                document.getKbId(),
                document.getFilename(),
                document.getFiletype(),
                document.getSize(),
                document.getMetadata(),
                Timestamp.valueOf(document.getCreatedAt()),
                Timestamp.valueOf(document.getUpdatedAt())
        );
        return document;
    }

    private static class KnowledgeDocumentRowMapper implements RowMapper<KnowledgeDocument> {
        @Override
        public KnowledgeDocument mapRow(ResultSet rs, int rowNum) throws SQLException {
            return KnowledgeDocument.builder()
                    .id(rs.getString("id"))
                    .kbId(rs.getString("kb_id"))
                    .filename(rs.getString("filename"))
                    .filetype(rs.getString("filetype"))
                    .size(rs.getLong("size"))
                    .metadata(rs.getString("metadata"))
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
