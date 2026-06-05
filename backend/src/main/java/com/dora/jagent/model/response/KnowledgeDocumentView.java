package com.dora.jagent.model.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentView {

    private String id;

    private String kbId;

    private String filename;

    private String filetype;

    private Long size;

    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
