package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.ResearchPaper;
import com.dora.jagent.model.entity.ResearchPaperChunkBgeM3;
import com.dora.jagent.model.entity.ResearchPaperCollection;
import com.dora.jagent.model.response.PaperIndexResponse;
import com.dora.jagent.model.response.ResearchPaperChunkView;
import com.dora.jagent.model.response.ResearchPaperView;
import com.dora.jagent.repository.ResearchPaperChunkBgeM3Repository;
import com.dora.jagent.repository.ResearchPaperCollectionRepository;
import com.dora.jagent.repository.ResearchPaperRepository;
import com.dora.jagent.service.KnowledgeEmbeddingService;
import com.dora.jagent.service.PaperIndexService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaperIndexServiceImpl implements PaperIndexService {

    private static final String DEFAULT_COLLECTION = "default";
    private static final String ABSTRACT_CHUNK_TYPE = "abstract";
    private static final int DEFAULT_LIMIT = 8;
    private static final int MAX_LIMIT = 20;

    private final ResearchPaperRepository researchPaperRepository;
    private final ResearchPaperCollectionRepository researchPaperCollectionRepository;
    private final ResearchPaperChunkBgeM3Repository researchPaperChunkBgeM3Repository;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final ObjectMapper objectMapper;

    @Override
    public PaperIndexResponse indexPaper(String paperId, String collectionName) {
        if (!StringUtils.hasText(paperId)) {
            throw new BizException("paperId cannot be blank");
        }
        String safeCollectionName = normalizeCollectionName(collectionName);
        ResearchPaper paper = researchPaperRepository.findById(paperId)
                .orElseThrow(() -> new BizException("paper not found"));
        researchPaperCollectionRepository.findByPaperIdAndCollectionName(paper.getId(), safeCollectionName)
                .orElseThrow(() -> new BizException("paper is not in collection"));

        saveAbstractChunk(paper);
        researchPaperCollectionRepository.updateStatus(paper.getId(), safeCollectionName, "indexed");

        return PaperIndexResponse.builder()
                .collectionName(safeCollectionName)
                .indexedCount(1)
                .status("indexed")
                .build();
    }

    @Override
    public PaperIndexResponse indexCollection(String collectionName) {
        String safeCollectionName = normalizeCollectionName(collectionName);
        List<ResearchPaperCollection> collections = researchPaperCollectionRepository
                .findByCollectionNameOrderByCreatedAtDesc(safeCollectionName);

        int indexedCount = 0;
        for (ResearchPaperCollection collection : collections) {
            ResearchPaper paper = researchPaperRepository.findById(collection.getPaperId()).orElse(null);
            if (paper == null) {
                continue;
            }
            saveAbstractChunk(paper);
            researchPaperCollectionRepository.updateStatus(paper.getId(), safeCollectionName, "indexed");
            indexedCount++;
        }

        return PaperIndexResponse.builder()
                .collectionName(safeCollectionName)
                .indexedCount(indexedCount)
                .status("indexed")
                .build();
    }

    @Override
    public List<ResearchPaperChunkView> semanticSearch(String query, String collectionName, Integer limit) {
        if (!StringUtils.hasText(query)) {
            throw new BizException("query cannot be blank");
        }

        String vectorLiteral = toPgVectorLiteral(knowledgeEmbeddingService.embed(query.trim()));
        return researchPaperChunkBgeM3Repository
                .similaritySearch(normalizeCollectionName(collectionName), vectorLiteral, normalizeLimit(limit))
                .stream()
                .map(this::toChunkView)
                .toList();
    }

    private void saveAbstractChunk(ResearchPaper paper) {
        String content = buildContent(paper);
        String embeddingText = buildEmbeddingText(paper);
        LocalDateTime now = LocalDateTime.now();

        ResearchPaperChunkBgeM3 chunk = ResearchPaperChunkBgeM3.builder()
                .id(UUID.randomUUID().toString())
                .paperId(paper.getId())
                .chunkType(ABSTRACT_CHUNK_TYPE)
                .chunkIndex(0)
                .content(content)
                .embeddingText(embeddingText)
                .metadata(buildMetadata(paper))
                .embedding(knowledgeEmbeddingService.embed(embeddingText))
                .createdAt(now)
                .updatedAt(now)
                .build();
        researchPaperChunkBgeM3Repository.save(chunk);
    }

    private String buildContent(ResearchPaper paper) {
        String abstractText = sanitizeAbstract(paper.getAbstractText());
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "Title", paper.getTitle());
        appendLine(builder, "Year", paper.getPublicationYear() == null ? null : String.valueOf(paper.getPublicationYear()));
        appendLine(builder, "Venue", paper.getSourceName());
        appendLine(builder, "Authors", paper.getAuthors());
        appendLine(builder, "Abstract", abstractText);
        return builder.toString().trim();
    }

    private String buildEmbeddingText(ResearchPaper paper) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "title", paper.getTitle());
        appendLine(builder, "authors", paper.getAuthors());
        appendLine(builder, "venue", paper.getSourceName());
        appendLine(builder, "year", paper.getPublicationYear() == null ? null : String.valueOf(paper.getPublicationYear()));
        appendLine(builder, "abstract", sanitizeAbstract(paper.getAbstractText()));
        return builder.toString().trim();
    }

    private String buildMetadata(ResearchPaper paper) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("title", paper.getTitle());
            metadata.put("year", paper.getPublicationYear());
            metadata.put("doi", paper.getDoi());
            metadata.put("openalexId", paper.getOpenalexId());
            metadata.put("sourceName", paper.getSourceName());
            metadata.put("sourceType", paper.getSourceType());
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ResearchPaperChunkView toChunkView(ResearchPaperChunkBgeM3Repository.ResearchPaperChunkMatch match) {
        return ResearchPaperChunkView.builder()
                .id(match.getChunk().getId())
                .paperId(match.getChunk().getPaperId())
                .chunkType(match.getChunk().getChunkType())
                .chunkIndex(match.getChunk().getChunkIndex())
                .content(match.getChunk().getContent())
                .metadata(match.getChunk().getMetadata())
                .distance(match.getDistance())
                .paper(toPaperView(match.getPaper()))
                .build();
    }

    private ResearchPaperView toPaperView(ResearchPaper paper) {
        if (paper == null) return null;
        return ResearchPaperView.builder()
                .id(paper.getId())
                .openalexId(paper.getOpenalexId())
                .doi(paper.getDoi())
                .title(paper.getTitle())
                .abstractText(paper.getAbstractText())
                .publicationYear(paper.getPublicationYear())
                .sourceName(paper.getSourceName())
                .sourceType(paper.getSourceType())
                .authors(paper.getAuthors())
                .landingPageUrl(paper.getLandingPageUrl())
                .pdfUrl(paper.getPdfUrl())
                .metadata(paper.getMetadata())
                .createdAt(paper.getCreatedAt())
                .updatedAt(paper.getUpdatedAt())
                .build();
    }

    private String sanitizeAbstract(String value) {
        if (!StringUtils.hasText(value) || "暂无摘要。".equals(value.trim())) {
            return "";
        }
        return value.trim();
    }

    private void appendLine(StringBuilder builder, String key, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        builder.append(key).append(": ").append(value.trim()).append('\n');
    }

    private String normalizeCollectionName(String collectionName) {
        return StringUtils.hasText(collectionName) ? collectionName.trim() : DEFAULT_COLLECTION;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
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
}
