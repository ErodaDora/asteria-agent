package com.dora.jagent.agent.tool.impl;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.core.ToolDescriptor;
import com.dora.jagent.agent.tool.core.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ReadLocalTextFileTool implements AgentTool {

    private static final Set<String> ALLOWED_SUFFIXES = Set.of(".md", ".txt", ".java", ".yaml", ".yml", ".json");

    private final Path workspaceRoot;

    public ReadLocalTextFileTool(
            @Value("${jagent.tool.workspace.root:${user.dir}}") String workspaceRoot
    ) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public String getName() {
        return "read_local_text_file";
    }

    @Override
    public String getDescription() {
        return "读取本地文本文件内容。当前仅允许读取工作区内的 .md/.txt/.java/.yaml/.yml/.json 文件。";
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
                                        "description", "工作区内文件路径，可以是绝对路径或相对路径"
                                )
                        ),
                        "required", List.of("input")
                ))
                .build();
    }

    @Override
    public ToolExecutionResult execute(String input) {
        if (!StringUtils.hasText(input)) {
            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(false)
                    .input(input)
                    .output("文件路径不能为空")
                    .build();
        }

        try {
            Path candidate = Path.of(input.trim());
            Path normalized = candidate.isAbsolute()
                    ? candidate.toAbsolutePath().normalize()
                    : workspaceRoot.resolve(candidate).normalize();

            if (!normalized.startsWith(workspaceRoot)) {
                return failed(input, "当前工具只允许读取工作区内文件");
            }

            String fileName = normalized.getFileName().toString().toLowerCase();
            boolean allowed = ALLOWED_SUFFIXES.stream().anyMatch(fileName::endsWith);
            if (!allowed) {
                return failed(input, "当前工具只允许读取文本文件类型：" + ALLOWED_SUFFIXES);
            }

            if (!Files.exists(normalized) || Files.isDirectory(normalized)) {
                return failed(input, "文件不存在，或路径指向的是目录");
            }

            String content = Files.readString(normalized, StandardCharsets.UTF_8);
            String truncated = content.length() > 6000 ? content.substring(0, 6000) + "\n...[内容过长，已截断]" : content;

            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(true)
                    .input(normalized.toString())
                    .output(truncated)
                    .build();
        } catch (IOException e) {
            return failed(input, "读取文件失败：" + e.getMessage());
        } catch (Exception e) {
            return failed(input, "工具执行失败：" + e.getMessage());
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
