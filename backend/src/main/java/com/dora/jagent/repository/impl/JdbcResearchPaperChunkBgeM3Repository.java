package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.ResearchPaper;
import com.dora.jagent.model.entity.ResearchPaperChunkBgeM3;
import com.dora.jagent.repository.ResearchPaperChunkBgeM3Repository;
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
public class JdbcResearchPaperChunkBgeM3Repository implements ResearchPaperChunkBgeM3Repository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public ResearchPaperChunkBgeM3 save(ResearchPaperChunkBgeM3 chunk) {
        String sql = """
                INSERT INTO research_paper_chunk_bge_m3 (
                    id, paper_id, chunk_type, chunk_index, content, embedding_text,
                    metadata, embedding, created_at, updated_at
                )
                VALUES (
                    CAST(? AS uuid), CAST(? AS uuid), ?, ?, ?, ?,
                    CAST(? AS jsonb), ?::vector, ?, ?
                )
                ON CONFLICT (paper_id, chunk_type, chunk_index) DO UPDATE SET
                    content = EXCLUDED.content,
                    embedding_text = EXCLUDED.embedding_text,
                    metadata = EXCLUDED.metadata,
                    embedding = EXCLUDED.embedding,
                    updated_at = EXCLUDED.updated_at
                """;
        jdbcTemplate.update(
                sql,
                chunk.getId(),
                chunk.getPaperId(),
                chunk.getChunkType(),
                chunk.getChunkIndex(),
                chunk.getContent(),
                chunk.getEmbeddingText(),
                chunk.getMetadata(),
                toPgVectorLiteral(chunk.getEmbedding()),
                Timestamp.valueOf(chunk.getCreatedAt()),
                Timestamp.valueOf(chunk.getUpdatedAt())
        );
        return chunk;
    }

    @Override
    public List<ResearchPaperChunkMatch> similaritySearch(String collectionName, String vectorLiteral, int limit) {
        String sql = """
                SELECT
                    c.id AS chunk_id,
                    c.paper_id AS chunk_paper_id,
                    c.chunk_type,
                    c.chunk_index,
                    c.content,
                    c.embedding_text,
                    c.metadata AS chunk_metadata,
                    c.created_at AS chunk_created_at,
                    c.updated_at AS chunk_updated_at,
                    c.embedding <-> CAST(? AS vector) AS distance,
                    p.id AS paper_id,
                    p.openalex_id,
                    p.doi,
                    p.title,
                    p.abstract_text,
                    p.publication_year,
                    p.source_name,
                    p.source_type,
                    p.authors,
                    p.landing_page_url,
                    p.pdf_url,
                    p.metadata AS paper_metadata,
                    p.created_at AS paper_created_at,
                    p.updated_at AS paper_updated_at
                FROM research_paper_chunk_bge_m3 c
                JOIN research_paper p ON p.id = c.paper_id
                JOIN research_paper_collection pc ON pc.paper_id = p.id
                WHERE pc.collection_name = ?
                ORDER BY c.embedding <-> CAST(? AS vector)
                LIMIT ?
                """;
        return jdbcTemplate.query(sql, new ResearchPaperChunkMatchRowMapper(), vectorLiteral, collectionName, vectorLiteral, limit);
    }

    private String toPgVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            builder.append(vector[i]);
            if (i < vector.length - 1) {
                builder.append(',');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private static class ResearchPaperChunkMatchRowMapper implements RowMapper<ResearchPaperChunkMatch> {
        @Override
        public ResearchPaperChunkMatch mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp chunkCreatedAt = rs.getTimestamp("chunk_created_at");
            Timestamp chunkUpdatedAt = rs.getTimestamp("chunk_updated_at");
            Timestamp paperCreatedAt = rs.getTimestamp("paper_created_at");
            Timestamp paperUpdatedAt = rs.getTimestamp("paper_updated_at");

            ResearchPaperChunkBgeM3 chunk = ResearchPaperChunkBgeM3.builder()
                    .id(rs.getString("chunk_id"))
                    .paperId(rs.getString("chunk_paper_id"))
                    .chunkType(rs.getString("chunk_type"))
                    .chunkIndex(rs.getInt("chunk_index"))
                    .content(rs.getString("content"))
                    .embeddingText(rs.getString("embedding_text"))
                    .metadata(rs.getString("chunk_metadata"))
                    .createdAt(chunkCreatedAt == null ? null : chunkCreatedAt.toLocalDateTime())
                    .updatedAt(chunkUpdatedAt == null ? null : chunkUpdatedAt.toLocalDateTime())
                    .build();

            ResearchPaper paper = ResearchPaper.builder()
                    .id(rs.getString("paper_id"))
                    .openalexId(rs.getString("openalex_id"))
                    .doi(rs.getString("doi"))
                    .title(rs.getString("title"))
                    .abstractText(rs.getString("abstract_text"))
                    .publicationYear((Integer) rs.getObject("publication_year"))
                    .sourceName(rs.getString("source_name"))
                    .sourceType(rs.getString("source_type"))
                    .authors(rs.getString("authors"))
                    .landingPageUrl(rs.getString("landing_page_url"))
                    .pdfUrl(rs.getString("pdf_url"))
                    .metadata(rs.getString("paper_metadata"))
                    .createdAt(paperCreatedAt == null ? null : paperCreatedAt.toLocalDateTime())
                    .updatedAt(paperUpdatedAt == null ? null : paperUpdatedAt.toLocalDateTime())
                    .build();

            return ResearchPaperChunkMatch.builder()
                    .chunk(chunk)
                    .paper(paper)
                    .distance(rs.getDouble("distance"))
                    .build();
        }
    }
}
