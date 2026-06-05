package com.dora.jagent.service;

import com.dora.jagent.model.response.ChatMessageView;
import com.dora.jagent.model.response.ChatSessionView;
import com.dora.jagent.model.response.SimpleChatResponse;

import java.util.List;

public interface AgentChatService {

    SimpleChatResponse chat(String userId, String agentId, String sessionId, String message);

    List<ChatSessionView> getSessions(String userId, String agentId);

    List<ChatMessageView> getSessionMessages(String userId, String agentId, String sessionId);
}
