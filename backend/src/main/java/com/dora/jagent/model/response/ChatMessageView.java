package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageView {

    private String id;

    private String sessionId;

    private String role;

    private String content;

    private LocalDateTime createdAt;
}
