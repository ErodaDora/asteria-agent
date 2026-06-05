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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class EmbedWatermarkImagesTool implements AgentTool {

    private final WatermarkMcpService watermarkMcpService;
    private final ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "embed_watermark_images";
    }

    @Override
    public String getDescription() {
        return "调用外部 Watermark MCP 服务，把图片批量做水印嵌入并写入输出目录。input 建议传 JSON，例如 {\"inputDir\":\"code/watermark-mcp/Datasets/images\",\"outputDir\":\"watermark-outputs/demo\",\"limit\":5}。";
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
                                        "description", "JSON 字符串，字段可包含 inputPaths、inputDir、outputDir、limit"
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
            Map<String, Object> result = watermarkMcpService.embedImages(arguments);
            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(true)
                    .input(input)
                    .output(String.valueOf(result.getOrDefault("output", "水印嵌入完成")))
                    .build();
        } catch (Exception exception) {
            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(false)
                    .input(input)
                    .output("水印嵌入失败：" + exception.getMessage())
                    .build();
        }
    }

    private Map<String, Object> parseArguments(String input) throws Exception {
        if (!StringUtils.hasText(input)) {
            return Map.of();
        }
        JsonNode root = objectMapper.readTree(input);
        if (!root.isObject()) {
            return Map.of("inputDir", input.trim());
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        if (root.has("inputPaths")) {
            arguments.put("inputPaths", objectMapper.convertValue(root.get("inputPaths"), new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
        }
        if (root.has("inputDir")) {
            arguments.put("inputDir", root.get("inputDir").asText());
        }
        if (root.has("outputDir")) {
            arguments.put("outputDir", root.get("outputDir").asText());
        }
        if (root.has("limit")) {
            arguments.put("limit", root.get("limit").asInt());
        }
        return arguments;
    }
}
