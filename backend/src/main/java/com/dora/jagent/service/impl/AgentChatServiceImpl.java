package com.dora.jagent.service.impl;

import com.dora.jagent.agent.runtime.AgentLoopContext;
import com.dora.jagent.agent.runtime.BasicAgentRuntime;
import com.dora.jagent.agent.runtime.ToolCallPlan;
import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.core.ToolExecutionResult;
import com.dora.jagent.agent.tool.runtime.AgentToolRegistry;
import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.Agent;
import com.dora.jagent.model.entity.ChatSession;
import com.dora.jagent.model.entity.KnowledgeBase;
import com.dora.jagent.model.response.ChatMessageView;
import com.dora.jagent.model.response.ChatSessionView;
import com.dora.jagent.model.response.SimpleChatResponse;
import com.dora.jagent.repository.ChatMessageRepository;
import com.dora.jagent.repository.ChatSessionRepository;
import com.dora.jagent.repository.KnowledgeBaseRepository;
import com.dora.jagent.service.AgentChatService;
import com.dora.jagent.service.AgentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentChatServiceImpl implements AgentChatService {

    private final AgentService agentService;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final Map<String, ChatClient> chatClients;
    private final AgentToolRegistry agentToolRegistry;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final ObjectMapper objectMapper;

    @Value("${agent.loop.max-tool-steps:3}")
    private int maxToolSteps;

    @Override
    public SimpleChatResponse chat(String userId, String agentId, String sessionId, String message) {
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message cannot be blank");
        }

        Agent agent = agentService.getRequiredAgent(agentId);
        ChatClient selectedChatClient = getRequiredChatClient(agent.getDefaultModelKey());
        AgentLoopContext loopContext = new AgentLoopContext(agentToolRegistry.getAll());
        BasicAgentRuntime runtime = new BasicAgentRuntime(agent, selectedChatClient, objectMapper, loopContext);

        // Agent 链条的第一步：
        // 先确定“这条消息属于哪个 agent 的哪个 session”，
        // 再决定后面构造什么 system prompt / 走哪个模型。
        ChatSession chatSession = resolveSession(userId, agentId, sessionId, message.trim());

        // 第一步先把用户消息落库。
        // 这样即使后面工具调用或模型回复失败，当前这次用户输入也不会丢。
        chatMessageRepository.save(com.dora.jagent.model.entity.ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(chatSession.getId())
                .role("user")
                .content(message.trim())
                .createdAt(LocalDateTime.now())
                .build());

        // 从这一版开始，Agent 不再只支持“规划一次 -> 调一个工具”，
        // 而是进入最小多步 loop：
        // 1. 基于当前上下文规划下一步
        // 2. 如果需要工具，就执行工具并把轨迹塞回上下文
        // 3. 再进入下一轮规划
        // 4. 直到模型判断“不需要工具了”，再生成最终回复
        List<Message> promptMessages = new ArrayList<>(buildPromptMessages(agent, chatSession));

        int toolStepCount = 0;
        String assistantMessage = null;
        while (toolStepCount < maxToolSteps) {
            // =================== 教材版：原生 tool calling 链路 ===================
            // 1. think() 让模型直接产出原生 tool_calls，并写入 lastChatResponse
            // 2. executeToolCalls() 读取 lastChatResponse，执行工具，并返回标准对话历史
            boolean hasToolCalls = runtime.think(promptMessages);
            if (!hasToolCalls) {
                AssistantMessage output = runtime.getLastAssistantMessage();
                assistantMessage = output == null ? "" : output.getText();
                break;
            }

            org.springframework.ai.model.tool.ToolExecutionResult nativeToolExecutionResult =
                    runtime.executeToolCalls(promptMessages);
            promptMessages = new ArrayList<>(nativeToolExecutionResult.conversationHistory());

            ToolResponseMessage toolResponseMessage = runtime.getLastToolResponseMessage(nativeToolExecutionResult);
            saveToolResponseMessages(chatSession.getId(), toolResponseMessage);
            toolStepCount++;
        }

        if (assistantMessage == null) {
            assistantMessage = runtime.reply(promptMessages);
        }

        chatMessageRepository.save(com.dora.jagent.model.entity.ChatMessage.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(chatSession.getId())
                .role("assistant")
                .content(assistantMessage)
                .createdAt(LocalDateTime.now())
                .build());

        return SimpleChatResponse.builder()
                .sessionId(chatSession.getId())
                .userMessage(message.trim())
                .assistantMessage(assistantMessage)
                .model(runtime.getDefaultModelKey())
                .implementation("basic-agent-runtime")
                .build();
    }

    @Override
    public List<ChatSessionView> getSessions(String userId, String agentId) {
        return chatSessionRepository.findByUserIdAndAgentIdOrderByUpdatedAtDesc(userId, agentId).stream()
                .map(session -> ChatSessionView.builder()
                        .id(session.getId())
                        .title(session.getTitle())
                        .createdAt(session.getCreatedAt())
                        .updatedAt(session.getUpdatedAt())
                .build())
                .toList();
    }

    private void saveToolResponseMessages(String sessionId, ToolResponseMessage toolResponseMessage) {
        for (ToolResponseMessage.ToolResponse toolResponse : toolResponseMessage.getResponses()) {
            chatMessageRepository.save(com.dora.jagent.model.entity.ChatMessage.builder()
                    .id(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .role("tool")
                    .content(buildNativeToolTrace(toolResponse))
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    @Override
    public List<ChatMessageView> getSessionMessages(String userId, String agentId, String sessionId) {
        ensureSessionBelongsToUserAndAgent(sessionId, userId, agentId);
        return chatMessageRepository.findBySessionId(sessionId).stream()
                .map(message -> ChatMessageView.builder()
                        .id(message.getId())
                        .sessionId(message.getSessionId())
                        .role(message.getRole())
                        .content(message.getContent())
                        .createdAt(message.getCreatedAt())
                        .build())
                .toList();
    }

    private ChatClient getRequiredChatClient(String modelKey) {
        ChatClient chatClient = chatClients.get(modelKey);
        if (chatClient == null) {
            throw new BizException("unsupported model: " + modelKey);
        }
        return chatClient;
    }

    private ChatSession resolveSession(String userId, String agentId, String sessionId, String message) {
        if (StringUtils.hasText(sessionId)) {
            ChatSession existingSession = ensureSessionBelongsToUserAndAgent(sessionId.trim(), userId, agentId);
            existingSession.setUpdatedAt(LocalDateTime.now());
            return chatSessionRepository.update(existingSession);
        }

        LocalDateTime now = LocalDateTime.now();
        ChatSession chatSession = ChatSession.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .agentId(agentId)
                .title(buildSessionTitle(message))
                .createdAt(now)
                .updatedAt(now)
                .build();
        return chatSessionRepository.save(chatSession);
    }

    private ChatSession ensureSessionBelongsToUserAndAgent(String sessionId, String userId, String agentId) {
        return chatSessionRepository.findByIdAndUserIdAndAgentId(sessionId, userId, agentId)
                .orElseThrow(() -> new BizException("agent chat session not found"));
    }

    private String buildSessionTitle(String message) {
        String trimmed = message.trim();
        return trimmed.length() <= 20 ? trimmed : trimmed.substring(0, 20) + "...";
    }

    private List<Message> buildPromptMessages(Agent agent, ChatSession chatSession) {
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(agent.getSystemPrompt()));
        String knowledgeInstruction = buildKnowledgeInstruction(agent);
        if (StringUtils.hasText(knowledgeInstruction)) {
            messages.add(new SystemMessage(knowledgeInstruction));
        }

        List<com.dora.jagent.model.entity.ChatMessage> historyMessages =
                chatMessageRepository.findBySessionId(chatSession.getId());

        // Agent 的最小多轮上下文策略：
        // 1. user / assistant 消息分别按角色恢复
        // 2. tool 消息不当作普通 assistant 文本，而是作为“工具执行轨迹”重新塞回上下文
        // 这样模型能知道：之前用户问了什么、自己回复了什么、以及工具真实执行过什么
        for (com.dora.jagent.model.entity.ChatMessage historyMessage : historyMessages) {
            if ("assistant".equals(historyMessage.getRole())) {
                messages.add(new AssistantMessage(historyMessage.getContent()));
            } else if ("tool".equals(historyMessage.getRole())) {
                messages.add(new SystemMessage("工具执行轨迹：\n" + historyMessage.getContent()));
            } else {
                messages.add(new UserMessage(historyMessage.getContent()));
            }
        }

        return messages;
    }

    private String buildKnowledgeInstruction(Agent agent) {
        List<String> knowledgeBaseIds = parseAllowedKnowledgeBaseIds(agent.getAllowedKbs());
        if (knowledgeBaseIds.isEmpty()) {
            return null;
        }

        List<KnowledgeBase> knowledgeBases = knowledgeBaseIds.stream()
                .map(knowledgeBaseRepository::findById)
                .flatMap(Optional::stream)
                .toList();
        if (knowledgeBases.isEmpty()) {
            return null;
        }

        String kbLines = knowledgeBases.stream()
                .map(kb -> "- kbId=" + kb.getId() + ", 名称=" + kb.getName()
                        + (StringUtils.hasText(kb.getDescription()) ? ", 用途=" + kb.getDescription() : ""))
                .collect(Collectors.joining("\n"));

        return """
                当前智能体允许访问以下知识库：
                %s

                当用户问题涉及项目文档、数据库设计、规格说明、知识库内容时，优先考虑调用 knowledge_query 工具。
                调用 knowledge_query 时，input 字符串必须是 JSON，例如：
                {"kbId":"某个知识库ID","query":"用户当前问题"}
                只允许使用上面列出的 kbId。
                """.formatted(kbLines);
    }

    private List<String> parseAllowedKnowledgeBaseIds(String allowedKbs) {
        if (!StringUtils.hasText(allowedKbs)) {
            return List.of();
        }
        try {
            List<String> ids = objectMapper.readValue(allowedKbs, new TypeReference<List<String>>() {});
            return ids == null ? List.of() : ids.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .toList();
        } catch (Exception e) {
            throw new BizException("failed to parse allowed knowledge bases");
        }
    }

    private String buildToolTrace(ToolCallPlan toolCallPlan, ToolExecutionResult toolExecutionResult) {
        return """
                toolName: %s
                toolInput: %s
                reason: %s
                success: %s
                output:
                %s
                """.formatted(
                toolCallPlan.getToolName(),
                toolExecutionResult.getInput(),
                toolCallPlan.getReason(),
                toolExecutionResult.isSuccess(),
                toolExecutionResult.getOutput()
        );
    }

    private String buildNativeToolTrace(ToolResponseMessage.ToolResponse toolResponse) {
        return """
                toolName: %s
                toolCallId: %s
                output:
                %s
                """.formatted(
                toolResponse.name(),
                toolResponse.id(),
                toolResponse.responseData()
        );
    }
}
