package com.dora.jagent.service;

import com.dora.jagent.model.response.ChatMessageView;
import com.dora.jagent.model.response.ChatSessionView;
import com.dora.jagent.model.response.SimpleChatResponse;

import java.util.List;

public interface ChatService {

    SimpleChatResponse chat(String userId, String sessionId, String message);

    List<ChatMessageView> getSessionMessages(String userId, String sessionId);

    List<ChatSessionView> getSessions(String userId);

    ChatSessionView renameSession(String userId, String sessionId, String title);

    void deleteSession(String userId, String sessionId);
}
