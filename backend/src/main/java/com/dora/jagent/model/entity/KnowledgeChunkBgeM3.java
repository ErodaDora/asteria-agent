package com.dora.jagent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Arrays;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeChunkBgeM3 {

    private String id;

    private String kbId;

    private String docId;

    private String content;

    // 先直接存 JSON 字符串，例如标题、chunk 序号等。
    private String metadata;

    private float[] embedding;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof KnowledgeChunkBgeM3 that)) {
            return false;
        }
        return java.util.Objects.equals(id, that.id)
                && java.util.Objects.equals(kbId, that.kbId)
                && java.util.Objects.equals(docId, that.docId)
                && java.util.Objects.equals(content, that.content)
                && java.util.Objects.equals(metadata, that.metadata)
                && Arrays.equals(embedding, that.embedding)
                && java.util.Objects.equals(createdAt, that.createdAt)
                && java.util.Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        int result = java.util.Objects.hash(id, kbId, docId, content, metadata, createdAt, updatedAt);
        result = 31 * result + Arrays.hashCode(embedding);
        return result;
    }
}
