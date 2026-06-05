package com.dora.jagent.model.request;

import lombok.Data;

@Data
public class SimpleChatRequest {

    // 如果前端已经有会话，就把 sessionId 带回来继续这轮对话。
    private String sessionId;

    // 仅对 Spring AI 路线生效：
    // 用来指定当前这一轮想走哪个 ChatClient Bean。
    // 例如：deepseek-chat / glm-4.6
    private String modelKey;

    // 当前最小聊天版只收一条用户消息。
    private String message;
}
