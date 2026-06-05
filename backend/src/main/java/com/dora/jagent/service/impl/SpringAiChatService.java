package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.ChatSession;
import com.dora.jagent.model.response.SimpleChatResponse;
import com.dora.jagent.repository.ChatMessageRepository;
import com.dora.jagent.repository.ChatSessionRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SpringAiChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final Map<String, ChatClient> chatClients;

    @Value("${llm.model}")
    private String defaultModelKey;

    @Value("${llm.system-prompt}")
    private String systemPrompt;

    @Value("${llm.recent-message-limit}")
    private Integer recentMessageLimit;

    @Value("${llm.summary-trigger-message-count}")
    private Integer summaryTriggerMessageCount;

    public SpringAiChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            Map<String, ChatClient> chatClients
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.chatClients = chatClients;
    }

    public SimpleChatResponse chat(String userId, String sessionId, String modelKey, String message) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message cannot be blank");
        }

        String selectedModelKey = StringUtils.hasText(modelKey) ? modelKey.trim() : defaultModelKey;
        ChatClient selectedChatClient = chatClients.get(selectedModelKey);
        if (selectedChatClient == null) {
            throw new BizException("unsupported model: " + selectedModelKey);
        }

        ChatSession chatSession = resolveSession(userId, sessionId, message.trim());

        chatMessageRepository.save(com.dora.jagent.model.entity.ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(chatSession.getId())
                .role("user")
                .content(message.trim())
                .createdAt(LocalDateTime.now())
                .build());

        // 这条实现链保留和手写 RestClient 版一致的 session / message / summary 策略，
        // 唯一区别放在“真正调模型”的这一步：
        // 这里把上下文转成 Spring AI 的 Prompt + Message，然后交给 ChatClient。
        String assistantMessage = callModel(selectedChatClient, buildPromptMessages(chatSession));

        chatMessageRepository.save(com.dora.jagent.model.entity.ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(chatSession.getId())
                .role("assistant")
                .content(assistantMessage)
                .createdAt(LocalDateTime.now())
                .build());

        refreshSessionSummaryIfNeeded(chatSession, selectedChatClient);

        return SimpleChatResponse.builder()
                .sessionId(chatSession.getId())
                .userMessage(message.trim())
                .assistantMessage(assistantMessage)
                .model(selectedModelKey)
                .implementation("spring-ai-chatclient")
                .build();
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

    private List<Message> buildPromptMessages(ChatSession chatSession) {
        List<com.dora.jagent.model.entity.ChatMessage> historyMessages =
                chatMessageRepository.findBySessionId(chatSession.getId());
        int startIndex = Math.max(0, historyMessages.size() - recentMessageLimit);

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));

        if (StringUtils.hasText(chatSession.getSummary())) {
            messages.add(new SystemMessage(
                    "以下是这段会话的历史摘要，请在回答时继承其中的重要背景信息：\n" + chatSession.getSummary()
            ));
        }

        for (com.dora.jagent.model.entity.ChatMessage historyMessage : historyMessages.subList(startIndex, historyMessages.size())) {
            if ("assistant".equals(historyMessage.getRole())) {
                messages.add(new AssistantMessage(historyMessage.getContent()));
            } else {
                messages.add(new UserMessage(historyMessage.getContent()));
            }
        }

        return messages;
    }

    private void refreshSessionSummaryIfNeeded(ChatSession chatSession, ChatClient selectedChatClient) {
        List<com.dora.jagent.model.entity.ChatMessage> historyMessages = chatMessageRepository.findBySessionId(chatSession.getId());
        if (historyMessages.size() < summaryTriggerMessageCount) {
            return;
        }

        int keepStartIndex = Math.max(0, historyMessages.size() - recentMessageLimit);
        List<com.dora.jagent.model.entity.ChatMessage> messagesToSummarize = historyMessages.subList(0, keepStartIndex);
        if (messagesToSummarize.isEmpty()) {
            return;
        }

        String summary = summarizeMessages(selectedChatClient, messagesToSummarize, chatSession.getSummary());
        chatSession.setSummary(summary);
        chatSession.setSummaryUpdatedAt(LocalDateTime.now());
        chatSession.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.update(chatSession);
    }

    private String summarizeMessages(
            ChatClient selectedChatClient,
            List<com.dora.jagent.model.entity.ChatMessage> messagesToSummarize,
            String existingSummary
    ) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(
                "你是对话摘要助手。请把以下历史对话压缩成简洁摘要，保留用户目标、已确认事实、偏好、约束和待继续的问题。输出纯文本摘要。"
        ));

        if (StringUtils.hasText(existingSummary)) {
            messages.add(new SystemMessage("已有摘要：\n" + existingSummary));
        }

        for (com.dora.jagent.model.entity.ChatMessage message : messagesToSummarize) {
            if ("assistant".equals(message.getRole())) {
                messages.add(new AssistantMessage(message.getContent()));
            } else {
                messages.add(new UserMessage(message.getContent()));
            }
        }

        return callModel(selectedChatClient, messages);
    }

    private String callModel(ChatClient selectedChatClient, List<Message> messages) {
        String response = selectedChatClient.prompt(new Prompt(messages))
                .call()
                .content();

        if (!StringUtils.hasText(response)) {
            throw new BizException("model response is empty");
        }
        return response;
    }
}
