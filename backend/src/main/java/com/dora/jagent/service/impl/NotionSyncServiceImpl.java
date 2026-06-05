package com.dora.jagent.service.impl;

import com.dora.jagent.model.response.XhsCrawlResponse;
import com.dora.jagent.model.response.XhsNoteItemView;
import com.dora.jagent.model.response.XhsNotionSyncResponse;
import com.dora.jagent.service.NotionSyncService;
import com.dora.jagent.xhs.model.XhsContentItem;
import com.dora.jagent.xhs.model.XhsGenerateResponse;
import com.dora.jagent.xhs.model.XhsGeneratedTopicWithContents;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NotionSyncServiceImpl implements NotionSyncService {

    private static final String NOTION_VERSION = "2022-06-28";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String databaseId;
    private final String token;

    public NotionSyncServiceImpl(
            ObjectMapper objectMapper,
            @Value("${notion.token:}") String token,
            @Value("${notion.database-id:}") String databaseId
    ) {
        this.objectMapper = objectMapper;
        this.token = token;
        this.databaseId = databaseId;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(20_000);
        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl("https://api.notion.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader("Notion-Version", NOTION_VERSION)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public XhsNotionSyncResponse syncCrawlResult(XhsCrawlResponse response) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(databaseId)) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message("Notion 配置未填写，请设置 notion.token 和 notion.database-id")
                    .build();
        }

        try {
            JsonNode database = fetchDatabase();

            if (database == null || database.path("object").isMissingNode()) {
                return XhsNotionSyncResponse.builder()
                        .success(false)
                        .message("Notion 数据库访问失败：未返回有效数据库信息")
                        .build();
            }

            JsonNode propertiesNode = database.path("properties");
            String titlePropertyName = resolveTitlePropertyName(propertiesNode);
            if (!StringUtils.hasText(titlePropertyName)) {
                return XhsNotionSyncResponse.builder()
                        .success(false)
                        .message("Notion 数据库缺少 title 类型主列，请先确认数据库结构")
                        .build();
            }

            List<XhsNoteItemView> items = response == null || response.getItems() == null ? List.of() : response.getItems();
            if (items.isEmpty()) {
                return XhsNotionSyncResponse.builder()
                        .success(true)
                        .message("Notion 已成功连通并可访问数据库，但当前没有采集结果可写入")
                        .build();
            }

            int successCount = 0;
            List<String> errors = new ArrayList<>();
            for (XhsNoteItemView item : items) {
                try {
                    Map<String, Object> payload = buildCreatePagePayload(item, titlePropertyName, propertiesNode);
                    createPage(payload);
                    successCount++;
                } catch (Exception e) {
                    errors.add("标题=" + resolveNotionTitle(item) + " -> " + buildDetailedErrorMessage("创建页面失败", e));
                }
            }

            if (successCount == items.size()) {
                return XhsNotionSyncResponse.builder()
                        .success(true)
                        .message("Notion 同步成功，共写入 " + successCount + " 条")
                        .build();
            }

            return XhsNotionSyncResponse.builder()
                    .success(successCount > 0)
                    .message("Notion 同步完成：成功 " + successCount + " 条，失败 " + (items.size() - successCount)
                            + (errors.isEmpty() ? "" : "；首个错误：" + errors.get(0)))
                    .build();
        } catch (Exception e) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message(buildDetailedErrorMessage("Notion 同步失败", e))
                    .build();
        }
    }

    @Override
    public XhsNotionSyncResponse syncGeneratedResult(XhsGenerateResponse generatedResponse, XhsCrawlResponse crawlResponse) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(databaseId)) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message("Notion 配置未填写，请设置 notion.token 和 notion.database-id")
                    .build();
        }
        if (generatedResponse == null || generatedResponse.getResults() == null || generatedResponse.getResults().isEmpty()) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message("当前没有可同步的生成结果")
                    .build();
        }

        try {
            JsonNode database = fetchDatabase();
            JsonNode propertiesNode = database.path("properties");
            String titlePropertyName = resolveTitlePropertyName(propertiesNode);
            if (!StringUtils.hasText(titlePropertyName)) {
                return XhsNotionSyncResponse.builder()
                        .success(false)
                        .message("Notion 数据库缺少 title 类型主列，请先确认数据库结构")
                        .build();
            }

            String fallbackCover = crawlResponse == null || crawlResponse.getItems() == null ? "" : crawlResponse.getItems().stream()
                    .map(XhsNoteItemView::getCoverImageUrl)
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("");

            int successCount = 0;
            List<String> errors = new ArrayList<>();
            for (XhsGeneratedTopicWithContents group : generatedResponse.getResults()) {
                List<XhsContentItem> contents = group.getContents() == null ? List.of() : group.getContents();
                for (XhsContentItem content : contents) {
                    try {
                        Map<String, Object> payload = buildGeneratedPagePayload(group, content, generatedResponse, titlePropertyName, propertiesNode, fallbackCover);
                        createPage(payload);
                        successCount++;
                    } catch (Exception exception) {
                        errors.add("标题=" + defaultGeneratedTitle(content) + " -> " + buildDetailedErrorMessage("创建生成页面失败", exception));
                    }
                }
            }

            if (errors.isEmpty()) {
                return XhsNotionSyncResponse.builder()
                        .success(true)
                        .message("生成结果已同步到 Notion，共写入 " + successCount + " 条")
                        .build();
            }

            return XhsNotionSyncResponse.builder()
                    .success(successCount > 0)
                    .message("生成结果同步完成：成功 " + successCount + " 条，失败 " + errors.size()
                            + (errors.isEmpty() ? "" : "；首个错误：" + errors.get(0)))
                    .build();
        } catch (Exception exception) {
            return XhsNotionSyncResponse.builder()
                    .success(false)
                    .message(buildDetailedErrorMessage("生成结果同步 Notion 失败", exception))
                    .build();
        }
    }

    private JsonNode fetchDatabase() {
        try {
            return executeWithRetry(() -> restClient.get()
                    .uri("/databases/{databaseId}", databaseId)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (Exception e) {
            throw new IllegalStateException(buildDetailedErrorMessage("访问 Notion 数据库失败", e), e);
        }
    }

    private void createPage(Map<String, Object> payload) {
        try {
            executeWithRetry(() -> restClient.post()
                    .uri("/pages")
                    .body(payload)
                    .retrieve()
                    .body(JsonNode.class));
        } catch (Exception e) {
            throw new IllegalStateException(buildDetailedErrorMessage("写入 Notion 页面失败", e), e);
        }
    }

    private String resolveTitlePropertyName(JsonNode propertiesNode) {
        if (propertiesNode == null || propertiesNode.isMissingNode()) {
            return null;
        }
        Iterator<String> fieldNames = propertiesNode.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if ("title".equals(propertiesNode.path(fieldName).path("type").asText())) {
                return fieldName;
            }
        }
        return null;
    }

    private Map<String, Object> buildCreatePagePayload(
            XhsNoteItemView item,
            String titlePropertyName,
            JsonNode propertiesNode
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parent", Map.of("database_id", databaseId));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(titlePropertyName, Map.of(
                "title", List.of(Map.of(
                        "type", "text",
                        "text", Map.of("content", resolveNotionTitle(item))
                ))
        ));

        putIfPresent(properties, propertiesNode, "正文", richTextProperty(buildNotionBody(item)));
        putIfPresent(properties, propertiesNode, "作者", richTextProperty(item.getAuthor()));
        putIfPresent(properties, propertiesNode, "链接", urlProperty(item.getUrl()));
        putIfPresent(properties, propertiesNode, "首图链接", urlProperty(item.getCoverImageUrl()));
        putIfPresent(properties, propertiesNode, "点赞数", numberProperty(item.getLikes()));
        putIfPresent(properties, propertiesNode, "评论数", numberProperty(item.getComments()));
        putIfPresent(properties, propertiesNode, "收藏数", numberProperty(item.getFavorites()));
        putIfPresent(properties, propertiesNode, "发布时间", richTextProperty(item.getPublishTime()));
        putIfPresent(properties, propertiesNode, "关键词", richTextProperty(item.getKeywordUsed()));
        putIfPresent(properties, propertiesNode, "内容类型", selectOrRichTextProperty(propertiesNode.path("内容类型"), item.getContentType()));
        putIfPresent(properties, propertiesNode, "标签", tagsProperty(propertiesNode.path("标签"), item.getTags()));

        payload.put("properties", properties);
        return payload;
    }

    private Map<String, Object> buildGeneratedPagePayload(
            XhsGeneratedTopicWithContents group,
            XhsContentItem content,
            XhsGenerateResponse generatedResponse,
            String titlePropertyName,
            JsonNode propertiesNode,
            String fallbackCover
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("parent", Map.of("database_id", databaseId));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put(titlePropertyName, Map.of(
                "title", List.of(Map.of(
                        "type", "text",
                        "text", Map.of("content", truncate(defaultGeneratedTitle(content), 80))
                ))
        ));

        putIfPresent(properties, propertiesNode, "正文", richTextProperty(buildGeneratedBody(group, content, generatedResponse)));
        putIfPresent(properties, propertiesNode, "作者", richTextProperty("AI 生成"));
        putIfPresent(properties, propertiesNode, "首图链接", urlProperty(fallbackCover));
        putIfPresent(properties, propertiesNode, "关键词", richTextProperty(group != null && group.getTopic() != null ? group.getTopic().getTitle() : ""));
        putIfPresent(properties, propertiesNode, "内容类型", selectOrRichTextProperty(propertiesNode.path("内容类型"), content.getContentType()));
        putIfPresent(properties, propertiesNode, "标签", tagsProperty(propertiesNode.path("标签"), content.getHashtags()));

        payload.put("properties", properties);
        return payload;
    }

    private String resolveNotionTitle(XhsNoteItemView item) {
        String title = item == null ? null : item.getTitle();
        String content = item == null ? null : item.getContent();
        if (StringUtils.hasText(title) && !looksLikeBody(title, content)) {
            return truncate(title.trim(), 80);
        }

        String keyword = item == null ? null : item.getKeywordUsed();
        if (StringUtils.hasText(keyword)) {
            return truncate("未命名笔记 - " + keyword.trim(), 80);
        }

        if (StringUtils.hasText(content)) {
            return truncate(content.trim().replaceAll("\\s+", " "), 40);
        }

        return "未命名笔记";
    }

    private String defaultGeneratedTitle(XhsContentItem content) {
        if (content == null || !StringUtils.hasText(content.getTitle())) {
            return "AI 生成笔记";
        }
        return content.getTitle().trim();
    }

    private boolean looksLikeBody(String title, String content) {
        String normalizedTitle = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        if (!StringUtils.hasText(normalizedTitle)) {
            return false;
        }
        if (normalizedTitle.length() > 60) {
            return true;
        }
        String normalizedContent = content == null ? "" : content.trim().replaceAll("\\s+", " ");
        return StringUtils.hasText(normalizedContent)
                && normalizedContent.startsWith(normalizedTitle)
                && normalizedTitle.length() > 20;
    }

    private void putIfPresent(Map<String, Object> properties, JsonNode propertiesNode, String propertyName, Object value) {
        if (value == null) {
            return;
        }
        JsonNode propertyNode = propertiesNode.path(propertyName);
        if (propertyNode.isMissingNode()) {
            return;
        }
        properties.put(propertyName, value);
    }

    private Map<String, Object> richTextProperty(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Map.of(
                "rich_text", List.of(Map.of(
                        "type", "text",
                        "text", Map.of("content", truncate(value, 1800))
                ))
        );
    }

    private String buildNotionBody(XhsNoteItemView item) {
        if (item == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(item.getContent())) {
            parts.add(item.getContent().trim());
        }
        if (item.getHotComments() != null && !item.getHotComments().isEmpty()) {
            parts.add("高赞评论：\n- " + String.join("\n- ", item.getHotComments()));
        }
        return truncate(String.join("\n\n", parts), 1800);
    }

    private String buildGeneratedBody(XhsGeneratedTopicWithContents group, XhsContentItem content, XhsGenerateResponse generatedResponse) {
        List<String> parts = new ArrayList<>();
        if (group != null && group.getTopic() != null && StringUtils.hasText(group.getTopic().getTitle())) {
            parts.add("选题：" + group.getTopic().getTitle().trim());
        }
        if (group != null && group.getTopic() != null && StringUtils.hasText(group.getTopic().getReason())) {
            parts.add("选题理由：" + group.getTopic().getReason().trim());
        }
        if (content != null && StringUtils.hasText(content.getBody())) {
            parts.add(content.getBody().trim());
        }
        if (content != null && StringUtils.hasText(content.getCta())) {
            parts.add("互动引导：" + content.getCta().trim());
        }
        if (content != null && content.getHashtags() != null && !content.getHashtags().isEmpty()) {
            parts.add("标签：" + String.join(" ", content.getHashtags()));
        }
        if (content != null && StringUtils.hasText(content.getImageSuggestion())) {
            parts.add("配图建议：" + content.getImageSuggestion().trim());
        }
        if (generatedResponse != null && StringUtils.hasText(generatedResponse.getAnalysisSummary())) {
            parts.add("分析摘要：" + generatedResponse.getAnalysisSummary().trim());
        }
        return truncate(String.join("\n\n", parts), 1800);
    }

    private Map<String, Object> urlProperty(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Map.of("url", value.trim());
    }

    private Map<String, Object> numberProperty(Integer value) {
        if (value == null) {
            return null;
        }
        return Map.of("number", value);
    }

    private Object selectOrRichTextProperty(JsonNode propertyNode, String value) {
        if (!StringUtils.hasText(value) || propertyNode.isMissingNode()) {
            return null;
        }
        String type = propertyNode.path("type").asText();
        if ("select".equals(type)) {
            return Map.of("select", Map.of("name", truncate(value, 100)));
        }
        return richTextProperty(value);
    }

    private Object tagsProperty(JsonNode propertyNode, List<String> tags) {
        if (tags == null || tags.isEmpty() || propertyNode.isMissingNode()) {
            return null;
        }
        String type = propertyNode.path("type").asText();
        if ("multi_select".equals(type)) {
            return Map.of("multi_select", tags.stream()
                    .filter(StringUtils::hasText)
                    .map(tag -> Map.of("name", truncate(tag, 100)))
                    .toList());
        }
        return richTextProperty(String.join(" | ", tags));
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String buildDetailedErrorMessage(String prefix, Exception exception) {
        Throwable root = rootCause(exception);

        if (root instanceof RestClientResponseException responseException) {
            String body = truncate(StringUtils.hasText(responseException.getResponseBodyAsString())
                    ? responseException.getResponseBodyAsString()
                    : "(empty body)", 500);
            return prefix + "：HTTP " + responseException.getStatusCode().value()
                    + " " + responseException.getStatusText()
                    + "；响应体：" + body;
        }

        if (root instanceof ResourceAccessException resourceAccessException) {
            return prefix + "：网络访问异常（" + root.getClass().getSimpleName() + "）"
                    + "；信息：" + safeMessage(resourceAccessException.getMessage());
        }

        if (root instanceof IOException ioException) {
            return prefix + "：I/O 异常（" + root.getClass().getSimpleName() + "）"
                    + "；信息：" + safeMessage(ioException.getMessage());
        }

        return prefix + "："
                + root.getClass().getSimpleName()
                + "；信息：" + safeMessage(root.getMessage());
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private String safeMessage(String message) {
        return StringUtils.hasText(message) ? message : "(empty message)";
    }

    private <T> T executeWithRetry(IoSupplier<T> supplier) throws Exception {
        try {
            return supplier.get();
        } catch (Exception first) {
            Throwable root = rootCause(first);
            boolean retryable = root instanceof ResourceAccessException
                    || root instanceof IOException
                    || root.getClass().getSimpleName().contains("ClosedChannelException");
            if (!retryable) {
                throw first;
            }
            return supplier.get();
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws Exception;
    }
}
