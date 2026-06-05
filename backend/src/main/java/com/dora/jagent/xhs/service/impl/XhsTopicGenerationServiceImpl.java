package com.dora.jagent.xhs.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.xhs.model.XhsAnalysisResult;
import com.dora.jagent.xhs.model.XhsTopicGenerateResponse;
import com.dora.jagent.xhs.model.XhsTopicItem;
import com.dora.jagent.xhs.service.XhsTopicGenerationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class XhsTopicGenerationServiceImpl implements XhsTopicGenerationService {

    private final Map<String, ChatClient> chatClients;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Override
    public List<XhsTopicItem> generateTopics(String modelKey, XhsAnalysisResult analysisResult, String audience, int count) {
        ChatClient chatClient = getRequiredChatClient(modelKey);
        String prompt = buildPrompt(analysisResult, audience, Math.max(1, count));
        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        if (!StringUtils.hasText(content)) {
            throw new BizException("选题生成失败：模型返回为空");
        }

        try {
            XhsTopicGenerateResponse response = objectMapper.readValue(stripCodeFence(content), XhsTopicGenerateResponse.class);
            if (response.getTopics() == null || response.getTopics().isEmpty()) {
                throw new BizException("选题生成失败：未返回有效 topics");
            }
            return response.getTopics().stream()
                    .limit(Math.max(1, count))
                    .toList();
        } catch (IOException exception) {
            throw new BizException("选题生成失败：结果解析异常 - " + exception.getMessage());
        }
    }

    private ChatClient getRequiredChatClient(String modelKey) {
        ChatClient chatClient = chatClients.get(modelKey);
        if (chatClient == null) {
            throw new BizException("unsupported model: " + modelKey);
        }
        return chatClient;
    }

    private String buildPrompt(XhsAnalysisResult analysisResult, String audience, int count) {
        String template = readPrompt("classpath:prompts/xhs/topic-generation-prompt.txt");
        return template
                .replace("{{audienceInstruction}}", buildAudienceInstruction(audience))
                .replace("{{count}}", String.valueOf(count))
                .replace("{{summary}}", defaultText(analysisResult.getSummary(), "暂无分析摘要"))
                .replace("{{topKeywords}}", String.join("、", safeList(analysisResult.getTopKeywords())))
                .replace("{{topTags}}", String.join("、", safeList(analysisResult.getTopTags())))
                .replace("{{titlePatterns}}", String.join("、", safeList(analysisResult.getTitlePatterns())))
                .replace("{{insightPoints}}", String.join("\n- ", prependDash(safeList(analysisResult.getInsightPoints()))));
    }

    private String readPrompt(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BizException("读取 prompt 模板失败: " + location);
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private List<String> prependDash(List<String> values) {
        if (values.isEmpty()) {
            return List.of("暂无补充洞察");
        }
        return values;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String buildAudienceInstruction(String audience) {
        return StringUtils.hasText(audience)
                ? "目标人群：" + audience.trim()
                : "目标人群：不做特定限制，可面向更广泛的小红书用户。";
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }
}
