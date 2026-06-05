package com.dora.jagent.agent.tool.impl;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.core.ToolExecutionResult;
import com.dora.jagent.service.RagService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KnowledgeTool implements AgentTool {

    private final RagService ragService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "knowledge_query";
    }

    @Override
    public String getDescription() {
        return "从指定知识库执行语义检索。input 字符串应为 JSON，例如 {\"kbId\":\"知识库ID\",\"query\":\"用户问题\"}。";
    }

    @Override
    public ToolExecutionResult execute(String input) {
        if (!StringUtils.hasText(input)) {
            return failed(input, "knowledge query input cannot be blank");
        }

        try {
            JsonNode root = objectMapper.readTree(input);
            String kbId = root.path("kbId").asText();
            String query = root.path("query").asText();
            if (!StringUtils.hasText(kbId) || !StringUtils.hasText(query)) {
                return failed(input, "input must contain kbId and query");
            }

            List<String> results = ragService.similaritySearch(kbId.trim(), query.trim());
            String output = results.isEmpty() ? "未检索到相关知识片段" : String.join("\n\n---\n\n", results);
            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(true)
                    .input(input)
                    .output(output)
                    .build();
        } catch (Exception e) {
            return failed(input, "knowledge query failed: " + e.getMessage());
        }
    }

    private ToolExecutionResult failed(String input, String message) {
        return ToolExecutionResult.builder()
                .toolName(getName())
                .success(false)
                .input(input)
                .output(message)
                .build();
    }
}
