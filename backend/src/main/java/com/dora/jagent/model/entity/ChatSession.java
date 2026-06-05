package com.dora.jagent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    private String id;

    private String userId;

    // 当 agentId 为空时，表示这是普通 Simple Chat 会话。
    // 当 agentId 有值时，表示这段对话归属于某个具体智能体。
    private String agentId;

    private String title;

    // 方案 2：把较早的历史消息压缩成摘要，
    // 后续构造 prompt 时优先带 summary，再带最近几条原始消息。
    private String summary;

    private LocalDateTime summaryUpdatedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
