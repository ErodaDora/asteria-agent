package com.dora.jagent.repository;

import com.dora.jagent.model.entity.ChatSession;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository {

    Optional<ChatSession> findByIdAndUserId(String sessionId, String userId);

    Optional<ChatSession> findByIdAndUserIdAndAgentId(String sessionId, String userId, String agentId);

    List<ChatSession> findByUserIdOrderByUpdatedAtDesc(String userId);

    List<ChatSession> findByUserIdAndAgentIdOrderByUpdatedAtDesc(String userId, String agentId);

    ChatSession save(ChatSession chatSession);

    ChatSession update(ChatSession chatSession);

    void deleteById(String sessionId);
}
