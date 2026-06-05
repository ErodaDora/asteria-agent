package com.dora.jagent.agent.tool.impl;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.core.ToolDescriptor;
import com.dora.jagent.agent.tool.core.ToolExecutionResult;
import com.dora.jagent.watermark.service.WatermarkMcpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SearchWatermarkImagesTool implements AgentTool {

    private final WatermarkMcpService watermarkMcpService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "search_watermark_images";
    }

    @Override
    public String getDescription() {
        return "在工作区中搜索适合做水印推理的图片文件。input 建议传 JSON，例如 {\"directory\":\"code/watermark-mcp/Datasets/images\",\"keyword\":\"29\",\"limit\":10}。";
    }

    @Override
    public ToolDescriptor getDescriptor() {
        return ToolDescriptor.builder()
                .name(getName())
                .description(getDescription())
                .inputSchema(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "input", Map.of(
                                        "type", "string",
                                        "description", "JSON 字符串，字段可包含 directory、keyword、limit"
                                )
                        ),
                        "required", List.of("input")
                ))
                .build();
    }

    @Override
    public ToolExecutionResult execute(String input) {
        try {
            Map<String, Object> arguments = parseArguments(input);
            Map<String, Object> result = watermarkMcpService.searchImages(arguments);
            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(true)
                    .input(input)
                    .output(String.valueOf(result.getOrDefault("output", "搜索完成")))
                    .build();
        } catch (Exception exception) {
            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(false)
                    .input(input)
                    .output("搜索图片失败：" + exception.getMessage())
                    .build();
        }
    }

    private Map<String, Object> parseArguments(String input) throws Exception {
        if (!StringUtils.hasText(input)) {
            return Map.of();
        }
        JsonNode root = objectMapper.readTree(input);
        if (!root.isObject()) {
            return Map.of("keyword", input.trim());
        }
        return objectMapper.convertValue(root, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    }
}
