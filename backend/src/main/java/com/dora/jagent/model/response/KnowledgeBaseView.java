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
public class KnowledgeBaseView {

    private String id;

    private String name;

    private String description;

    private String metadata;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
