package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.ChatSession;
import com.dora.jagent.model.response.ChatMessageView;
import com.dora.jagent.model.response.ChatSessionView;
import com.dora.jagent.model.response.SimpleChatResponse;
import com.dora.jagent.repository.ChatMessageRepository;
import com.dora.jagent.repository.ChatSessionRepository;
import com.dora.jagent.service.ChatService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ManualApiChatServiceImpl implements ChatService {

    // 当前先直接调一个模型服务，
    // 把“用户一句话 -> 模型一句回复”这条最小链跑通。
    private final RestClient restClient;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    @Value("${llm.api-key}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.system-prompt}")
    private String systemPrompt;

    @Value("${llm.recent-message-limit}")
    private Integer recentMessageLimit;

    @Value("${llm.summary-trigger-message-count}")
    private Integer summaryTriggerMessageCount;

    public ManualApiChatServiceImpl(
            @Value("${llm.base-url}") String baseUrl,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Override
    public SimpleChatResponse chat(String userId, String sessionId, String message) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message cannot be blank");
        }

        ChatSession chatSession = resolveSession(userId, sessionId, message.trim());

        chatMessageRepository.save(com.dora.jagent.model.entity.ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(chatSession.getId())
                .role("user")
                .content(message.trim())
                .createdAt(LocalDateTime.now())
                .build());

        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        // 方案 2：这里不再只靠“最近 N 条消息窗口”。
        // 而是优先带 session summary，再补最近几条原始消息。
        // 这样既能保留长期关键信息，又不会无限重复喂完整历史。
        request.setMessages(buildContextMessages(chatSession));

        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || !StringUtils.hasText(response.getChoices().get(0).getMessage().getContent())) {
            throw new BizException("model response is empty");
        }

        String assistantMessage = response.getChoices().get(0).getMessage().getContent();
        chatMessageRepository.save(com.dora.jagent.model.entity.ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(chatSession.getId())
                .role("assistant")
                .content(assistantMessage)
                .createdAt(LocalDateTime.now())
                .build());

        refreshSessionSummaryIfNeeded(chatSession);

        return SimpleChatResponse.builder()
                .sessionId(chatSession.getId())
                .userMessage(message.trim())
                .assistantMessage(assistantMessage)
                .model(model)
                .implementation("manual-rest-client")
                .build();
    }

    @Override
    public List<ChatMessageView> getSessionMessages(String userId, String sessionId) {
        ensureSessionBelongsToUser(sessionId, userId);
        return chatMessageRepository.findBySessionId(sessionId).stream()
                .map(message -> ChatMessageView.builder()
                        .id(message.getId())
                        .sessionId(message.getSessionId())
                        .role(message.getRole())
                        .content(message.getContent())
                        .createdAt(message.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public List<ChatSessionView> getSessions(String userId) {
        return chatSessionRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(session -> ChatSessionView.builder()
                        .id(session.getId())
                        .title(session.getTitle())
                        .createdAt(session.getCreatedAt())
                        .updatedAt(session.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public ChatSessionView renameSession(String userId, String sessionId, String title) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("session title cannot be blank");
        }

        ChatSession session = ensureSessionBelongsToUser(sessionId, userId);
        session.setTitle(title.trim());
        session.setUpdatedAt(LocalDateTime.now());
        ChatSession updatedSession = chatSessionRepository.update(session);
        return ChatSessionView.builder()
                .id(updatedSession.getId())
                .title(updatedSession.getTitle())
                .createdAt(updatedSession.getCreatedAt())
                .updatedAt(updatedSession.getUpdatedAt())
                .build();
    }

    @Override
    public void deleteSession(String userId, String sessionId) {
        ensureSessionBelongsToUser(sessionId, userId);
        chatMessageRepository.deleteBySessionId(sessionId);
        chatSessionRepository.deleteById(sessionId);
    }

    private ChatSession resolveSession(String userId, String sessionId, String message) {
        if (StringUtils.hasText(sessionId)) {
            ChatSession existingSession = ensureSessionBelongsToUser(sessionId.trim(), userId);
            existingSession.setUpdatedAt(LocalDateTime.now());
            return chatSessionRepository.update(existingSession);
        }

        LocalDateTime now = LocalDateTime.now();
        ChatSession chatSession = ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .title(buildSessionTitle(message))
                .createdAt(now)
                .updatedAt(now)
                .build();
        return chatSessionRepository.save(chatSession);
    }

    private ChatSession ensureSessionBelongsToUser(String sessionId, String userId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BizException("chat session not found"));
    }

    private String buildSessionTitle(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 20 ? trimmed : trimmed.substring(0, 20) + "...";
    }

    private List<ApiChatMessage> buildContextMessages(ChatSession chatSession) {
        List<com.dora.jagent.model.entity.ChatMessage> historyMessages = chatMessageRepository.findBySessionId(chatSession.getId());
        int startIndex = Math.max(0, historyMessages.size() - recentMessageLimit);

        List<ApiChatMessage> contextMessages = new ArrayList<>();
        contextMessages.add(ApiChatMessage.of("system", systemPrompt));

        if (StringUtils.hasText(chatSession.getSummary())) {
            contextMessages.add(ApiChatMessage.of(
                    "system",
                    "以下是这段会话的历史摘要，请在回答时继承其中的重要背景信息：\n" + chatSession.getSummary()
            ));
        }

        for (com.dora.jagent.model.entity.ChatMessage historyMessage : historyMessages.subList(startIndex, historyMessages.size())) {
            contextMessages.add(ApiChatMessage.of(historyMessage.getRole(), historyMessage.getContent()));
        }

        return contextMessages;
    }

    private void refreshSessionSummaryIfNeeded(ChatSession chatSession) {
        List<com.dora.jagent.model.entity.ChatMessage> historyMessages = chatMessageRepository.findBySessionId(chatSession.getId());
        if (historyMessages.size() < summaryTriggerMessageCount) {
            return;
        }

        int keepStartIndex = Math.max(0, historyMessages.size() - recentMessageLimit);
        List<com.dora.jagent.model.entity.ChatMessage> messagesToSummarize = historyMessages.subList(0, keepStartIndex);
        if (messagesToSummarize.isEmpty()) {
            return;
        }

        String summary = summarizeMessages(messagesToSummarize, chatSession.getSummary());
        chatSession.setSummary(summary);
        chatSession.setSummaryUpdatedAt(LocalDateTime.now());
        chatSession.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.update(chatSession);
    }

    private String summarizeMessages(List<com.dora.jagent.model.entity.ChatMessage> messagesToSummarize, String existingSummary) {
        List<ApiChatMessage> summaryMessages = new ArrayList<>();
        summaryMessages.add(ApiChatMessage.of(
                "system",
                "你是对话摘要助手。请把以下历史对话压缩成简洁摘要，保留用户目标、已确认事实、偏好、约束和待继续的问题。输出纯文本摘要。"
        ));

        if (StringUtils.hasText(existingSummary)) {
            summaryMessages.add(ApiChatMessage.of("system", "已有摘要：\n" + existingSummary));
        }

        for (com.dora.jagent.model.entity.ChatMessage message : messagesToSummarize) {
            summaryMessages.add(ApiChatMessage.of(message.getRole(), message.getContent()));
        }

        return callModel(summaryMessages);
    }

    private String callModel(List<ApiChatMessage> messages) {
        ChatCompletionRequest request = new ChatCompletionRequest();
        request.setModel(model);
        request.setMessages(messages);

        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null
                || response.getChoices() == null
                || response.getChoices().isEmpty()
                || response.getChoices().get(0).getMessage() == null
                || !StringUtils.hasText(response.getChoices().get(0).getMessage().getContent())) {
            throw new BizException("model response is empty");
        }

        return response.getChoices().get(0).getMessage().getContent();
    }

    @Data
    private static class ChatCompletionRequest {
        private String model;
        private List<ApiChatMessage> messages;
    }

    @Data
    private static class ChatCompletionResponse {
        private List<Choice> choices;
    }

    @Data
    private static class Choice {
        private ApiChatMessage message;
    }

    @Data
    private static class ApiChatMessage {
        private String role;
        private String content;

        static ApiChatMessage of(String role, String content) {
            ApiChatMessage chatMessage = new ApiChatMessage();
            chatMessage.setRole(role);
            chatMessage.setContent(content);
            return chatMessage;
        }
    }
}
