package com.dora.jagent.agent.runtime;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.exception.BizException;
import com.dora.jagent.model.entity.Agent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class BasicAgentRuntime {

    private final Agent agent;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final AgentLoopContext loopContext;

    // 这是一个最小运行时 Agent 对象。
    // 现在这里承担的是“循环中的单轮规划”：
    // 1. 看当前上下文里是否已经有足够信息
    // 2. 如果不够，就决定下一步要调用哪个工具
    // 3. 如果够了，就返回 useTool = false，让上层结束 loop 并产出最终回复
    public boolean think(List<Message> contextMessages) {
        String thinkPrompt = """
                现在你是一个智能的「决策模块」。
                请根据当前对话上下文，决定下一步的动作。
                如果需要调用工具来完成任务，请调用相应的工具。
                """;

        Prompt prompt = Prompt.builder()
                .messages(contextMessages)
                .chatOptions(loopContext.getChatOptions())
                .build();

        ToolCallback[] callbacks = loopContext.getAvailableToolCallbacks().toArray(new ToolCallback[0]);
        loopContext.setLastChatResponse(chatClient.prompt(prompt)
                .system(thinkPrompt)
                .toolCallbacks(callbacks)
                .call()
                .chatClientResponse()
                .chatResponse());

        if (loopContext.getLastChatResponse() == null) {
            throw new BizException("agent think response is empty");
        }

        return loopContext.getLastChatResponse().hasToolCalls();
    }

    public org.springframework.ai.model.tool.ToolExecutionResult executeToolCalls(List<Message> contextMessages) {
        if (loopContext.getLastChatResponse() == null) {
            throw new BizException("last chat response is empty before tool execution");
        }

        Prompt prompt = Prompt.builder()
                .messages(contextMessages)
                .chatOptions(loopContext.getChatOptions())
                .build();

        return loopContext.getToolCallingManager().executeToolCalls(prompt, loopContext.getLastChatResponse());
    }

    public AssistantMessage getLastAssistantMessage() {
        ChatResponse chatResponse = loopContext.getLastChatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return null;
        }
        return chatResponse.getResult().getOutput();
    }

    public ToolResponseMessage getLastToolResponseMessage(org.springframework.ai.model.tool.ToolExecutionResult result) {
        List<Message> history = result.conversationHistory();
        if (history.isEmpty()) {
            throw new BizException("tool execution history is empty");
        }
        return (ToolResponseMessage) history.get(history.size() - 1);
    }

    // =================== 基础版写法（保留作对照学习） ===================
    // 这套写法不是走 Spring AI 原生 tool calling，而是让模型严格输出 JSON，
    // 再由我们自己把 JSON 解析成 ToolCallPlan。
    public ToolCallPlan planToolCall(List<Message> contextMessages) {
        String toolDescription = loopContext.getAvailableTools().stream()
                .map(tool -> "- " + tool.getName() + ": " + tool.getDescription())
                .collect(Collectors.joining("\n"));

        // 这里不是让模型直接执行工具，而是让它在每一轮 loop 中回答：
        // 1. 是否还需要继续调用工具
        // 2. 如果要，下一步该调用哪个工具
        // 3. 工具输入是什么
        // 如果当前上下文已经足够生成最终答案，就返回 useTool = false。
        String planningInstruction = """
                你是一个工具规划助手。请结合当前全部上下文，判断是否还需要继续调用工具。
                你只能从下面工具中选择：
                %s

                如果当前信息还不够，请严格输出 JSON：
                {"useTool":true,"toolName":"工具名","toolInput":"工具输入","reason":"原因"}

                如果当前信息已经足够让智能体直接回答用户，请严格输出 JSON：
                {"useTool":false,"toolName":"","toolInput":"","reason":"原因"}

                不要输出 JSON 之外的任何内容。
                """.formatted(toolDescription);

        List<Message> planningMessages = new ArrayList<>();
        planningMessages.add(new SystemMessage(planningInstruction));
        planningMessages.addAll(contextMessages);

        Prompt prompt = Prompt.builder()
                .messages(planningMessages)
                .chatOptions(loopContext.getChatOptions())
                .build();
        String content = extractContent(chatClient.prompt(prompt)
                .call()
                .chatClientResponse()
                .chatResponse());

        if (!StringUtils.hasText(content)) {
            throw new BizException("tool planning response is empty");
        }

        try {
            return objectMapper.readValue(stripCodeFence(content), ToolCallPlan.class);
        } catch (JsonProcessingException e) {
            throw new BizException("tool planning response is not valid JSON");
        }
    }
    // =================== 基础版写法结束 ===================

    public String reply(List<Message> messages) {
        Prompt prompt = Prompt.builder()
                .messages(messages)
                .chatOptions(loopContext.getChatOptions())
                .build();
        String content = extractContent(chatClient.prompt(prompt)
                .call()
                .chatClientResponse()
                .chatResponse());
        if (!StringUtils.hasText(content)) {
            throw new BizException("agent response is empty");
        }
        return content;
    }

    public String getName() {
        return agent.getName();
    }

    public String getDefaultModelKey() {
        return agent.getDefaultModelKey();
    }

    public AgentLoopContext getLoopContext() {
        return loopContext;
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```json", "")
                    .replaceFirst("^```", "")
                    .replaceFirst("```$", "")
                    .trim();
        }
        return trimmed;
    }

    private String extractContent(ChatResponse chatResponse) {
        loopContext.setLastChatResponse(chatResponse);
        if (chatResponse == null) {
            return null;
        }
        Generation generation = chatResponse.getResult();
        if (generation == null) {
            return null;
        }
        AssistantMessage output = generation.getOutput();
        return output == null ? null : output.getText();
    }
}
