package com.dora.jagent.agent.tool.impl;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.core.ToolDescriptor;
import com.dora.jagent.agent.tool.core.ToolExecutionResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

@Component
public class SearchWorkspaceFilesTool implements AgentTool {

    private static final int MAX_RESULTS = 20;

    private final Path workspaceRoot;

    public SearchWorkspaceFilesTool(
            @Value("${jagent.tool.workspace.root:${user.dir}}") String workspaceRoot
    ) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public String getName() {
        return "search_workspace_files";
    }

    @Override
    public String getDescription() {
        return "按文件名关键字搜索工作区内的文件路径。适合先找文件，再把路径交给读取文本文件工具继续处理。";
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
                                        "description", "文件名搜索关键字，例如 chat、pom、Agent"
                                )
                        ),
                        "required", List.of("input")
                ))
                .build();
    }

    @Override
    public ToolExecutionResult execute(String input) {
        if (!StringUtils.hasText(input)) {
            return failed(input, "搜索关键字不能为空");
        }

        String keyword = input.trim().toLowerCase(Locale.ROOT);
        try (Stream<Path> pathStream = Files.walk(workspaceRoot)) {
            List<String> matches = pathStream
                    .filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.startsWith(workspaceRoot))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).contains(keyword))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_RESULTS)
                    .map(Path::toString)
                    .toList();

            if (matches.isEmpty()) {
                return ToolExecutionResult.builder()
                        .toolName(getName())
                        .success(true)
                        .input(input)
                        .output("未找到匹配文件")
                        .build();
            }

            String output = String.join("\n", matches);
            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(true)
                    .input(input)
                    .output(output)
                    .build();
        } catch (IOException e) {
            return failed(input, "搜索文件失败：" + e.getMessage());
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
