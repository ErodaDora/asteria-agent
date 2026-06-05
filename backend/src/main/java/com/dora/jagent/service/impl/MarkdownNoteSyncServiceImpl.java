package com.dora.jagent.service.impl;

import com.dora.jagent.model.response.WorkspaceNoteSyncItemResponse;
import com.dora.jagent.model.response.WorkspaceNoteSyncResponse;
import com.dora.jagent.service.MarkdownNoteSyncService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
public class MarkdownNoteSyncServiceImpl implements MarkdownNoteSyncService {

    private static final String NOTION_VERSION = "2022-06-28";
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("M月d日 HH:mm")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private final Path rootPath;
    private final String notionToken;
    private final String notionPageId;
    private final RestClient notionClient;

    public MarkdownNoteSyncServiceImpl(
            @Value("${notes-sync.root:./notes-sync}") String rootPath,
            @Value("${notion.token:}") String notionToken,
            @Value("${notion.page-id:}") String notionPageId
    ) {
        this.rootPath = Paths.get(rootPath);
        this.notionToken = notionToken;
        this.notionPageId = notionPageId;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(20_000);
        this.notionClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.notion.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + notionToken)
                .defaultHeader("Notion-Version", NOTION_VERSION)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public WorkspaceNoteSyncResponse scanNotes() {
        List<MarkdownNote> notes = loadNotes();
        return buildResponse(notes, notionConfigured() ? "已连接本地笔记目录，可同步到目标页面下的子页面。" : "Notion 未配置，当前仅扫描本地笔记。");
    }

    @Override
    public WorkspaceNoteSyncResponse syncNotes() {
        List<MarkdownNote> notes = loadNotes();
        if (!notionConfigured()) {
            return buildResponse(notes, "Notion 未配置，请先填写 notion.token 和 notion.page-id。");
        }

        if (notes.isEmpty()) {
            return buildResponse(notes, "本地目录里还没有可同步的 markdown 文件。");
        }

        try {
            int successCount = 0;
            for (MarkdownNote note : notes) {
                try {
                    String pageId = StringUtils.hasText(note.notionPageId()) ? note.notionPageId() : findExistingPageIdByTitle(note.syncTitle());
                    if (StringUtils.hasText(pageId)) {
                        updatePage(pageId, note);
                        if (!pageId.equals(note.notionPageId())) {
                            writeBackNotionPageId(note, pageId);
                        }
                    } else {
                        String createdPageId = createPage(note);
                        writeBackNotionPageId(note, createdPageId);
                    }
                    successCount++;
                } catch (Exception ignored) {
                }
            }
            List<MarkdownNote> refreshedNotes = loadNotes();
            return buildResponse(refreshedNotes, "Notion 同步完成：成功 " + successCount + " / " + notes.size() + "。");
        } catch (Exception e) {
            return buildResponse(notes, "Notion 同步失败，请检查 token、page 配置或页面共享权限。");
        }
    }

    private WorkspaceNoteSyncResponse buildResponse(List<MarkdownNote> notes, String statusText) {
        String latestUpdatedAt = notes.stream()
                .map(MarkdownNote::updatedAt)
                .max(Comparator.naturalOrder())
                .map(DISPLAY_TIME::format)
                .orElse("暂无");

        return WorkspaceNoteSyncResponse.builder()
                .rootPath(rootPath.toString())
                .notionConfigured(notionConfigured())
                .noteCount(notes.size())
                .boundCount((int) notes.stream().filter(note -> StringUtils.hasText(note.notionPageId())).count())
                .latestUpdatedAt(latestUpdatedAt)
                .statusText(statusText)
                .items(notes.stream()
                        .limit(6)
                        .map(note -> WorkspaceNoteSyncItemResponse.builder()
                                .fileName(note.path().getFileName().toString())
                                .title(note.syncTitle())
                                .relativePath(rootPath.relativize(note.path()).toString())
                                .notionPageId(note.notionPageId())
                                .updatedAt(DISPLAY_TIME.format(note.updatedAt()))
                                .build())
                        .toList())
                .build();
    }

    private List<MarkdownNote> loadNotes() {
        if (!Files.exists(rootPath)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.walk(rootPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .map(this::readNote)
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private MarkdownNote readNote(Path path) {
        try {
            String raw = Files.readString(path, StandardCharsets.UTF_8);
            FrontMatter frontMatter = parseFrontMatter(raw);
            String fileTitle = path.getFileName().toString().replaceFirst("\\.md$", "");
            return new MarkdownNote(path, fileTitle, frontMatter.title(), frontMatter.notionPageId(), frontMatter.body(), lastModified(path));
        } catch (IOException e) {
            String fallback = path.getFileName().toString().replaceFirst("\\.md$", "");
            return new MarkdownNote(path, fallback, "", "", "", lastModified(path));
        }
    }

    private FrontMatter parseFrontMatter(String raw) {
        if (!raw.startsWith("---\n") && !raw.startsWith("---\r\n")) {
            return new FrontMatter("", "", raw);
        }
        int end = raw.indexOf("\n---", 4);
        if (end < 0) {
            return new FrontMatter("", "", raw);
        }

        String header = raw.substring(4, end).trim();
        String body = raw.substring(Math.min(raw.length(), end + 4)).trim();
        String title = "";
        String notionPageId = "";
        for (String line : header.split("\\R")) {
            int idx = line.indexOf(':');
            if (idx < 0) continue;
            String key = line.substring(0, idx).trim();
            String value = line.substring(idx + 1).trim().replaceAll("^['\"]|['\"]$", "");
            if ("title".equalsIgnoreCase(key)) {
                title = value;
            } else if ("notion_page_id".equalsIgnoreCase(key)) {
                notionPageId = value;
            }
        }
        return new FrontMatter(title, notionPageId, body);
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }

    private boolean notionConfigured() {
        return StringUtils.hasText(notionToken) && StringUtils.hasText(notionPageId);
    }

    private String findExistingPageIdByTitle(String title) {
        Map<String, Object> payload = Map.of(
                "query", title,
                "filter", Map.of("value", "page", "property", "object"),
                "page_size", 20
        );
        JsonNode result = notionClient.post()
                .uri("/search")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        if (!result.path("results").isArray()) {
            return "";
        }

        for (JsonNode page : result.path("results")) {
            if (!Objects.equals(page.path("parent").path("page_id").asText(""), notionPageId)) {
                continue;
            }
            String existingTitle = extractPageTitle(page);
            if (title.equals(existingTitle)) {
                return page.path("id").asText("");
            }
        }
        return "";
    }

    private String extractPageTitle(JsonNode page) {
        JsonNode titleArray = page.path("properties").path("title").path("title");
        if (!titleArray.isArray() || titleArray.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        titleArray.forEach(node -> sb.append(node.path("plain_text").asText("")));
        return sb.toString();
    }

    private String createPage(MarkdownNote note) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parent", Map.of("page_id", notionPageId));
        payload.put("properties", buildProperties(note));
        payload.put("children", buildBlocks(note.body()));
        JsonNode response = notionClient.post()
                .uri("/pages")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);
        return response.path("id").asText("");
    }

    private void updatePage(String pageId, MarkdownNote note) {
        notionClient.patch()
                .uri("/pages/{pageId}", pageId)
                .body(Map.of("properties", buildProperties(note)))
                .retrieve()
                .body(JsonNode.class);
        replacePageContent(pageId, note.body());
    }

    private void replacePageContent(String pageId, String body) {
        JsonNode children = notionClient.get()
                .uri(uriBuilder -> uriBuilder.path("/blocks/{blockId}/children").queryParam("page_size", 100).build(pageId))
                .retrieve()
                .body(JsonNode.class);
        if (children.path("results").isArray()) {
            for (JsonNode block : children.path("results")) {
                String blockId = block.path("id").asText("");
                if (!StringUtils.hasText(blockId)) {
                    continue;
                }
                notionClient.patch()
                        .uri("/blocks/{blockId}", blockId)
                        .body(Map.of("archived", true))
                        .retrieve()
                        .body(JsonNode.class);
            }
        }
        List<Map<String, Object>> blocks = buildBlocks(body);
        if (!blocks.isEmpty()) {
            notionClient.patch()
                    .uri("/blocks/{blockId}/children", pageId)
                    .body(Map.of("children", blocks))
                    .retrieve()
                    .body(JsonNode.class);
        }
    }

    private Map<String, Object> buildProperties(MarkdownNote note) {
        return Map.of("title", Map.of(
                "title", List.of(Map.of(
                        "type", "text",
                        "text", Map.of("content", truncate(note.syncTitle(), 120))
                ))
        ));
    }

    private List<Map<String, Object>> buildBlocks(String markdown) {
        List<Map<String, Object>> blocks = new ArrayList<>();
        if (!StringUtils.hasText(markdown)) {
            return blocks;
        }
        for (String line : markdown.split("\\R")) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (trimmed.startsWith("### ")) {
                blocks.add(textBlock("heading_3", trimmed.substring(4)));
            } else if (trimmed.startsWith("## ")) {
                blocks.add(textBlock("heading_2", trimmed.substring(3)));
            } else if (trimmed.startsWith("# ")) {
                blocks.add(textBlock("heading_1", trimmed.substring(2)));
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                blocks.add(textBlock("bulleted_list_item", trimmed.substring(2)));
            } else {
                blocks.add(textBlock("paragraph", trimmed));
            }
        }
        return blocks;
    }

    private Map<String, Object> textBlock(String type, String text) {
        return Map.of(
                "object", "block",
                "type", type,
                type, Map.of(
                        "rich_text", List.of(Map.of(
                                "type", "text",
                                "text", Map.of("content", truncate(text, 1800))
                        ))
                )
        );
    }

    private void writeBackNotionPageId(MarkdownNote note, String notionPageId) throws IOException {
        String raw = Files.readString(note.path(), StandardCharsets.UTF_8);
        String body = note.body();
        List<String> headerLines = new ArrayList<>();
        if (raw.startsWith("---\n") || raw.startsWith("---\r\n")) {
            int end = raw.indexOf("\n---", 4);
            if (end > 0) {
                String header = raw.substring(4, end).trim();
                for (String line : header.split("\\R")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("notion_page_id:")) {
                        continue;
                    }
                    if (trimmed.startsWith("title:")) {
                        continue;
                    }
                    if (StringUtils.hasText(trimmed)) {
                        headerLines.add(trimmed);
                    }
                }
            }
        }

        List<String> newContent = new ArrayList<>();
        newContent.add("---");
        if (StringUtils.hasText(note.frontMatterTitle())) {
            newContent.add("title: " + note.frontMatterTitle());
        }
        newContent.add("notion_page_id: " + notionPageId);
        newContent.addAll(headerLines);
        newContent.add("---");
        newContent.add("");
        if (StringUtils.hasText(body)) {
            newContent.add(body.trim());
        }
        Files.writeString(note.path(), String.join("\n", newContent), StandardCharsets.UTF_8);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private record FrontMatter(String title, String notionPageId, String body) {}

    private record MarkdownNote(Path path, String syncTitle, String frontMatterTitle, String notionPageId, String body, Instant updatedAt) {}
}
