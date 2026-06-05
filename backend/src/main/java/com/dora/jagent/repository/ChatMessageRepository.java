package com.dora.jagent.repository;

import com.dora.jagent.model.entity.ChatMessage;

import java.util.List;

public interface ChatMessageRepository {

    ChatMessage save(ChatMessage chatMessage);

    List<ChatMessage> findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
