package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatSessionView {

    private String id;

    private String title;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
