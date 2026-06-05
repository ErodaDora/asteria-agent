package com.dora.jagent.repository;

import com.dora.jagent.model.entity.KnowledgeDocument;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository {

    Optional<KnowledgeDocument> findById(String documentId);

    Optional<KnowledgeDocument> findByKbIdAndFilename(String knowledgeBaseId, String filename);

    List<KnowledgeDocument> findByKbIdOrderByCreatedAtAsc(String knowledgeBaseId);

    KnowledgeDocument save(KnowledgeDocument document);
}
