package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.KnowledgeDocument;
import com.dora.jagent.model.entity.KnowledgeChunkBgeM3;
import com.dora.jagent.model.request.CreateKnowledgeDocumentRequest;
import com.dora.jagent.model.response.CreateKnowledgeDocumentResponse;
import com.dora.jagent.model.response.KnowledgeDocumentView;
import com.dora.jagent.repository.KnowledgeBaseRepository;
import com.dora.jagent.repository.KnowledgeChunkBgeM3Repository;
import com.dora.jagent.repository.KnowledgeDocumentRepository;
import com.dora.jagent.service.DocumentStorageService;
import com.dora.jagent.service.KnowledgeEmbeddingService;
import com.dora.jagent.service.KnowledgeDocumentService;
import com.dora.jagent.service.MarkdownParserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeChunkBgeM3Repository knowledgeChunkBgeM3Repository;
    private final DocumentStorageService documentStorageService;
    private final MarkdownParserService markdownParserService;
    private final KnowledgeEmbeddingService knowledgeEmbeddingService;

    @Override
    public List<KnowledgeDocumentView> getDocumentsByKnowledgeBase(String knowledgeBaseId) {
        ensureKnowledgeBaseExists(knowledgeBaseId);
        return knowledgeDocumentRepository.findByKbIdOrderByCreatedAtAsc(knowledgeBaseId).stream()
                .map(this::toView)
                .toList();
    }

    @Override
    public KnowledgeDocumentView getDocument(String documentId) {
        return toView(getRequiredDocument(documentId));
    }

    @Override
    public CreateKnowledgeDocumentResponse createDocument(String knowledgeBaseId, CreateKnowledgeDocumentRequest request) {
        ensureKnowledgeBaseExists(knowledgeBaseId);

        if (request == null || !StringUtils.hasText(request.getFilename())) {
            throw new IllegalArgumentException("document filename cannot be blank");
        }

        LocalDateTime now = LocalDateTime.now();
        KnowledgeDocument document = KnowledgeDocument.builder()
                .id(UUID.randomUUID().toString())
                .kbId(knowledgeBaseId)
                .filename(request.getFilename().trim())
                .filetype(trimToNull(request.getFiletype()))
                .size(request.getSize())
                .metadata(trimToNull(request.getMetadata()))
                .createdAt(now)
                .updatedAt(now)
                .build();

        knowledgeDocumentRepository.save(document);

        return CreateKnowledgeDocumentResponse.builder()
                .documentId(document.getId())
                .build();
    }

    @Override
    public CreateKnowledgeDocumentResponse uploadDocument(String knowledgeBaseId, MultipartFile file) {
        ensureKnowledgeBaseExists(knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("uploaded file cannot be empty");
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("uploaded file name cannot be blank");
        }

        knowledgeDocumentRepository.findByKbIdAndFilename(knowledgeBaseId, originalFilename.trim())
                .ifPresent(document -> {
                    throw new BizException("document with same filename already exists in this knowledge base");
                });

        String documentId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        String filePath;
        try {
            filePath = documentStorageService.saveFile(knowledgeBaseId, documentId, file);
        } catch (Exception e) {
            throw new BizException("failed to save uploaded file: " + e.getMessage());
        }

        KnowledgeDocument document = KnowledgeDocument.builder()
                .id(documentId)
                .kbId(knowledgeBaseId)
                .filename(originalFilename.trim())
                .filetype(detectFiletype(originalFilename))
                .size(file.getSize())
                .metadata(buildDocumentMetadataJson(filePath))
                .createdAt(now)
                .updatedAt(now)
                .build();

        knowledgeDocumentRepository.save(document);

        if (isMarkdownDocument(document.getFiletype())) {
            ingestMarkdownDocument(knowledgeBaseId, documentId, filePath);
        }

        return CreateKnowledgeDocumentResponse.builder()
                .documentId(documentId)
                .build();
    }

    private void ensureKnowledgeBaseExists(String knowledgeBaseId) {
        if (!knowledgeBaseRepository.findById(knowledgeBaseId).isPresent()) {
            throw new BizException("knowledge base not found");
        }
    }

    private KnowledgeDocument getRequiredDocument(String documentId) {
        return knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new BizException("knowledge document not found"));
    }

    private KnowledgeDocumentView toView(KnowledgeDocument document) {
        return KnowledgeDocumentView.builder()
                .id(document.getId())
                .kbId(document.getKbId())
                .filename(document.getFilename())
                .filetype(document.getFiletype())
                .size(document.getSize())
                .metadata(document.getMetadata())
                .createdAt(document.getCreatedAt())
                .updatedAt(document.getUpdatedAt())
                .build();
    }

    private String trimToNull(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        return text.trim();
    }

    private boolean isMarkdownDocument(String filetype) {
        return "md".equalsIgnoreCase(filetype) || "markdown".equalsIgnoreCase(filetype);
    }

    private String detectFiletype(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            return "unknown";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private String buildDocumentMetadataJson(String filePath) {
        return "{\"filePath\":\"" + filePath.replace("\\", "/").replace("\"", "\\\"") + "\"}";
    }

    private String buildChunkMetadataJson(String title) {
        return "{\"title\":\"" + title.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private void ingestMarkdownDocument(String knowledgeBaseId, String documentId, String filePath) {
        try {
            Path path = documentStorageService.getFilePath(filePath);
            try (InputStream inputStream = Files.newInputStream(path)) {
                List<MarkdownParserService.MarkdownSection> sections = markdownParserService.parseMarkdown(inputStream);
                LocalDateTime now = LocalDateTime.now();
                for (MarkdownParserService.MarkdownSection section : sections) {
                    if (!StringUtils.hasText(section.getTitle())) {
                        continue;
                    }
                    KnowledgeChunkBgeM3 chunk = KnowledgeChunkBgeM3.builder()
                            .id(UUID.randomUUID().toString())
                            .kbId(knowledgeBaseId)
                            .docId(documentId)
                            .content(section.getContent() == null ? "" : section.getContent())
                            .metadata(buildChunkMetadataJson(section.getTitle()))
                            .embedding(knowledgeEmbeddingService.embed(section.getTitle()))
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    knowledgeChunkBgeM3Repository.save(chunk);
                }
            }
        } catch (Exception e) {
            log.error("failed to ingest markdown document, kbId={}, documentId={}", knowledgeBaseId, documentId, e);
            throw new BizException("failed to ingest markdown document: " + e.getMessage());
        }
    }
}
