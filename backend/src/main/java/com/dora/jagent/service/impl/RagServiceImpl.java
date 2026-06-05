package com.dora.jagent.service.impl;

import com.dora.jagent.model.entity.KnowledgeChunkBgeM3;
import com.dora.jagent.repository.KnowledgeChunkBgeM3Repository;
import com.dora.jagent.service.KnowledgeEmbeddingService;
import com.dora.jagent.service.RagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    private static final int DEFAULT_LIMIT = 3;

    private final KnowledgeEmbeddingService knowledgeEmbeddingService;
    private final KnowledgeChunkBgeM3Repository knowledgeChunkBgeM3Repository;

    @Override
    public float[] embed(String text) {
        return knowledgeEmbeddingService.embed(text);
    }

    @Override
    public List<String> similaritySearch(String knowledgeBaseId, String query) {
        if (!StringUtils.hasText(knowledgeBaseId) || !StringUtils.hasText(query)) {
            return List.of();
        }
        String vectorLiteral = toPgVector(embed(query.trim()));
        List<KnowledgeChunkBgeM3> chunks =
                knowledgeChunkBgeM3Repository.similaritySearch(knowledgeBaseId.trim(), vectorLiteral, DEFAULT_LIMIT);
        return chunks.stream()
                .map(KnowledgeChunkBgeM3::getContent)
                .toList();
    }

    private String toPgVector(float[] vector) {
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
}
