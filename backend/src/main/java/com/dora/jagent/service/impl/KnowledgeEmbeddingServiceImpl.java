package com.dora.jagent.service.impl;

import com.dora.jagent.service.KnowledgeEmbeddingService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class KnowledgeEmbeddingServiceImpl implements KnowledgeEmbeddingService {

    private final RestClient restClient;

    @Value("${knowledge.embedding.model:bge-m3}")
    private String modelName;

    public KnowledgeEmbeddingServiceImpl(
            @Value("${knowledge.embedding.base-url:http://localhost:11434}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Data
    private static class EmbeddingResponse {
        private float[] embedding;
    }

    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = restClient.post()
                .uri("/api/embeddings")
                .body(Map.of(
                        "model", modelName,
                        "prompt", text
                ))
                .retrieve()
                .body(EmbeddingResponse.class);
        Assert.notNull(response, "embedding response cannot be null");
        return response.getEmbedding();
    }
}
