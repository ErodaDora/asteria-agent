package com.dora.jagent.xhs.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.xhs.model.XhsContentGenerateResponse;
import com.dora.jagent.xhs.model.XhsContentItem;
import com.dora.jagent.xhs.model.XhsTopicItem;
import com.dora.jagent.xhs.service.XhsContentGenerationService;
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
public class XhsContentGenerationServiceImpl implements XhsContentGenerationService {

    private final Map<String, ChatClient> chatClients;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Override
    public List<XhsContentItem> generateContents(String modelKey, XhsTopicItem topic, String audience, String tone, int count) {
        ChatClient chatClient = getRequiredChatClient(modelKey);
        String prompt = buildPrompt(topic, audience, tone, Math.max(1, count));
        String content = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        if (!StringUtils.hasText(content)) {
            throw new BizException("文案生成失败：模型返回为空");
        }

        try {
            XhsContentGenerateResponse response = objectMapper.readValue(stripCodeFence(content), XhsContentGenerateResponse.class);
            if (response.getContents() == null || response.getContents().isEmpty()) {
                throw new BizException("文案生成失败：未返回有效 contents");
            }
            return response.getContents().stream()
                    .limit(Math.max(1, count))
                    .toList();
        } catch (IOException exception) {
            throw new BizException("文案生成失败：结果解析异常 - " + exception.getMessage());
        }
    }

    private ChatClient getRequiredChatClient(String modelKey) {
        ChatClient chatClient = chatClients.get(modelKey);
        if (chatClient == null) {
            throw new BizException("unsupported model: " + modelKey);
        }
        return chatClient;
    }

    private String buildPrompt(XhsTopicItem topic, String audience, String tone, int count) {
        String template = readPrompt("classpath:prompts/xhs/content-generation-prompt.txt");
        return template
                .replace("{{topicTitle}}", defaultText(topic.getTitle(), "未命名选题"))
                .replace("{{topicReason}}", defaultText(topic.getReason(), "贴近当前热门内容特征"))
                .replace("{{audienceInstruction}}", buildAudienceInstruction(audience))
                .replace("{{tone}}", defaultText(tone, "真实分享"))
                .replace("{{count}}", String.valueOf(count));
    }

    private String readPrompt(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BizException("读取 prompt 模板失败: " + location);
        }
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
