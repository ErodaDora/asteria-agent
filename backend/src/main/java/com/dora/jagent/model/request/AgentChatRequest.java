package com.dora.jagent.model.request;

import lombok.Data;

@Data
public class AgentChatRequest {

    // agent 对话也允许续用旧 session。
    private String sessionId;

    // 当前阶段先只收一条用户消息。
    private String message;
}
