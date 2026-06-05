package com.dora.jagent.repository;

import com.dora.jagent.model.entity.KnowledgeChunkBgeM3;

import java.util.List;

public interface KnowledgeChunkBgeM3Repository {

    List<KnowledgeChunkBgeM3> findByKbIdAndDocIdOrderByCreatedAtAsc(String knowledgeBaseId, String documentId);

    List<KnowledgeChunkBgeM3> similaritySearch(String knowledgeBaseId, String vectorLiteral, int limit);

    KnowledgeChunkBgeM3 save(KnowledgeChunkBgeM3 chunk);
}
