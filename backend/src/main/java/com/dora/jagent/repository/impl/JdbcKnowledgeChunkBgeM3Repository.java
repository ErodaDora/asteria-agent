package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.KnowledgeChunkBgeM3;
import com.dora.jagent.repository.KnowledgeChunkBgeM3Repository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class JdbcKnowledgeChunkBgeM3Repository implements KnowledgeChunkBgeM3Repository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<KnowledgeChunkBgeM3> findByKbIdAndDocIdOrderByCreatedAtAsc(String knowledgeBaseId, String documentId) {
        String sql = """
                SELECT id, kb_id, doc_id, content, metadata, embedding, created_at, updated_at
                FROM chunk_bge_m3
                WHERE kb_id = CAST(? AS uuid)
                  AND doc_id = CAST(? AS uuid)
                ORDER BY created_at ASC
                """;
        return jdbcTemplate.query(sql, new KnowledgeChunkBgeM3RowMapper(), knowledgeBaseId, documentId);
    }

    @Override
    public List<KnowledgeChunkBgeM3> similaritySearch(String knowledgeBaseId, String vectorLiteral, int limit) {
        String sql = """
                SELECT id, kb_id, doc_id, content, metadata, embedding, created_at, updated_at
                FROM chunk_bge_m3
                WHERE kb_id = CAST(? AS uuid)
                ORDER BY embedding <-> CAST(? AS vector) 
                LIMIT ?
                """;
                //  <-> 是 pgvector 提供的向量相似度运算符，CAST(? AS vector) 将输入的向量字符串转换为 pgvector 类型，以便进行距离计算。
        return jdbcTemplate.query(
                sql,
                new KnowledgeChunkBgeM3RowMapper(),
                knowledgeBaseId,
                vectorLiteral,
                limit
        );
    }

    @Override
    public KnowledgeChunkBgeM3 save(KnowledgeChunkBgeM3 chunk) {
        String sql = """
                INSERT INTO chunk_bge_m3 (id, kb_id, doc_id, content, metadata, embedding, created_at, updated_at)
                VALUES (CAST(? AS uuid), CAST(? AS uuid), CAST(? AS uuid), ?, CAST(? AS jsonb), ?::vector, ?, ?)
                """;
        jdbcTemplate.update(
                sql,
                chunk.getId(),
                chunk.getKbId(),
                chunk.getDocId(),
                chunk.getContent(),
                chunk.getMetadata(),
                toPgVectorLiteral(chunk.getEmbedding()),
                Timestamp.valueOf(chunk.getCreatedAt()),
                Timestamp.valueOf(chunk.getUpdatedAt())
        );
        return chunk;
    }

    private String toPgVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            builder.append(vector[i]);
            if (i < vector.length - 1) {
                builder.append(",");
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private static class KnowledgeChunkBgeM3RowMapper implements RowMapper<KnowledgeChunkBgeM3> {
        @Override
        public KnowledgeChunkBgeM3 mapRow(ResultSet rs, int rowNum) throws SQLException {
            Object embeddingValue = rs.getObject("embedding");
            return KnowledgeChunkBgeM3.builder()
                    .id(rs.getString("id"))
                    .kbId(rs.getString("kb_id"))
                    .docId(rs.getString("doc_id"))
                    .content(rs.getString("content"))
                    .metadata(rs.getString("metadata"))
                    // 当前先保留为 null，后续真正做检索时再补 pgvector 读取方案。
                    .embedding(embeddingValue == null ? null : null)
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}
