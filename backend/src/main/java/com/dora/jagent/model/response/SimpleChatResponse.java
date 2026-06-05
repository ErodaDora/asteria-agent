package com.dora.jagent.model.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimpleChatResponse {

    private String sessionId;

    private String userMessage;

    private String assistantMessage;

    private String model;

    // 用来标记当前回复是哪条实现链：
    // manual-rest-client / spring-ai-chatclient
    private String implementation;
}
