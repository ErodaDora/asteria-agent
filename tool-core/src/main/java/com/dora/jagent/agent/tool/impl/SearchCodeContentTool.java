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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class SearchCodeContentTool implements AgentTool {

    private static final Set<String> ALLOWED_SUFFIXES =
            Set.of(".java", ".xml", ".yaml", ".yml", ".json", ".md", ".txt", ".sql");
    private static final int MAX_RESULTS = 30;

    private final Path workspaceRoot;

    public SearchCodeContentTool(
            @Value("${jagent.tool.workspace.root:${user.dir}}") String workspaceRoot
    ) {
        this.workspaceRoot = Path.of(workspaceRoot).toAbsolutePath().normalize();
    }

    @Override
    public String getName() {
        return "search_code_content";
    }

    @Override
    public String getDescription() {
        return "按文件内容搜索工作区内的代码或文本片段，返回匹配位置和行内容。适合先定位类名、方法名、配置项、SQL 关键字，再决定读取哪些文件。";
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
                                        "description", "代码内容搜索关键字，例如 ChatClient、knowledge_query、spring.ai"
                                )
                        ),
                        "required", List.of("input")
                ))
                .build();
    }

    @Override
    public ToolExecutionResult execute(String input) {
        if (!StringUtils.hasText(input)) {
            return failed(input, "搜索关键词不能为空");
        }

        String keyword = input.trim();
        String keywordLower = keyword.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();

        try (Stream<Path> pathStream = Files.walk(workspaceRoot)) {
            List<Path> candidates = pathStream
                    .filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.startsWith(workspaceRoot))
                    .filter(this::isAllowedFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();

            for (Path file : candidates) {
                if (matches.size() >= MAX_RESULTS) {
                    break;
                }
                collectMatches(file, keywordLower, matches);
            }

            String output = matches.isEmpty()
                    ? "未找到匹配内容"
                    : String.join("\n", matches);

            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(true)
                    .input(keyword)
                    .output(output)
                    .build();
        } catch (IOException e) {
            return failed(input, "搜索代码内容失败：" + e.getMessage());
        } catch (Exception e) {
            return failed(input, "工具执行失败：" + e.getMessage());
        }
    }

    private void collectMatches(Path file, String keywordLower, List<String> matches) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size() && matches.size() < MAX_RESULTS; i++) {
            String line = lines.get(i);
            if (line.toLowerCase(Locale.ROOT).contains(keywordLower)) {
                String snippet = line.trim();
                if (snippet.length() > 160) {
                    snippet = snippet.substring(0, 160) + "...";
                }
                matches.add(file + ":" + (i + 1) + " | " + snippet);
            }
        }
    }

    private boolean isAllowedFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return ALLOWED_SUFFIXES.stream().anyMatch(fileName::endsWith);
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
