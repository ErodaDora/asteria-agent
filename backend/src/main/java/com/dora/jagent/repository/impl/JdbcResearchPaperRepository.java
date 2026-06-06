package com.dora.jagent.repository.impl;

import com.dora.jagent.model.entity.ResearchPaper;
import com.dora.jagent.repository.ResearchPaperRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class JdbcResearchPaperRepository implements ResearchPaperRepository {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public Optional<ResearchPaper> findById(String paperId) {
        String sql = """
                SELECT id, openalex_id, doi, title, abstract_text, publication_year, source_name,
                       source_type, authors, landing_page_url, pdf_url, metadata, created_at, updated_at
                FROM research_paper
                WHERE id = CAST(? AS uuid)
                """;
        return jdbcTemplate.query(sql, new ResearchPaperRowMapper(), paperId).stream().findFirst();
    }

    @Override
    public Optional<ResearchPaper> findByOpenalexId(String openalexId) {
        if (!StringUtils.hasText(openalexId)) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, openalex_id, doi, title, abstract_text, publication_year, source_name,
                       source_type, authors, landing_page_url, pdf_url, metadata, created_at, updated_at
                FROM research_paper
                WHERE openalex_id = ?
                """;
        return jdbcTemplate.query(sql, new ResearchPaperRowMapper(), openalexId.trim()).stream().findFirst();
    }

    @Override
    public Optional<ResearchPaper> findByDoi(String doi) {
        if (!StringUtils.hasText(doi)) {
            return Optional.empty();
        }
        String sql = """
                SELECT id, openalex_id, doi, title, abstract_text, publication_year, source_name,
                       source_type, authors, landing_page_url, pdf_url, metadata, created_at, updated_at
                FROM research_paper
                WHERE doi = ?
                ORDER BY updated_at DESC
                LIMIT 1
                """;
        return jdbcTemplate.query(sql, new ResearchPaperRowMapper(), doi.trim()).stream().findFirst();
    }

    @Override
    public ResearchPaper save(ResearchPaper paper) {
        String sql = """
                INSERT INTO research_paper (
                    id, openalex_id, doi, title, abstract_text, publication_year, source_name,
                    source_type, authors, landing_page_url, pdf_url, metadata, created_at, updated_at
                )
                VALUES (
                    CAST(? AS uuid), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?
                )
                ON CONFLICT (openalex_id) DO UPDATE SET
                    doi = EXCLUDED.doi,
                    title = EXCLUDED.title,
                    abstract_text = EXCLUDED.abstract_text,
                    publication_year = EXCLUDED.publication_year,
                    source_name = EXCLUDED.source_name,
                    source_type = EXCLUDED.source_type,
                    authors = EXCLUDED.authors,
                    landing_page_url = EXCLUDED.landing_page_url,
                    pdf_url = EXCLUDED.pdf_url,
                    metadata = EXCLUDED.metadata,
                    updated_at = EXCLUDED.updated_at
                """;
        jdbcTemplate.update(
                sql,
                paper.getId(),
                paper.getOpenalexId(),
                paper.getDoi(),
                paper.getTitle(),
                paper.getAbstractText(),
                paper.getPublicationYear(),
                paper.getSourceName(),
                paper.getSourceType(),
                paper.getAuthors(),
                paper.getLandingPageUrl(),
                paper.getPdfUrl(),
                paper.getMetadata(),
                Timestamp.valueOf(paper.getCreatedAt()),
                Timestamp.valueOf(paper.getUpdatedAt())
        );
        return findByOpenalexId(paper.getOpenalexId()).orElse(paper);
    }

    private static class ResearchPaperRowMapper implements RowMapper<ResearchPaper> {
        @Override
        public ResearchPaper mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            return ResearchPaper.builder()
                    .id(rs.getString("id"))
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
                    .metadata(rs.getString("metadata"))
                    .createdAt(createdAt == null ? null : createdAt.toLocalDateTime())
                    .updatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime())
                    .build();
        }
    }
}
