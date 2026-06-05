package com.dora.jagent.xhs.service.impl;

import com.dora.jagent.model.response.XhsNoteItemView;
import com.dora.jagent.xhs.model.XhsAnalysisResult;
import com.dora.jagent.xhs.service.XhsAnalysisService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class XhsAnalysisServiceImpl implements XhsAnalysisService {

    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "和", "是", "我", "也", "很", "都", "就", "又", "太", "在", "真的", "一个", "这", "可以", "不会", "就是"
    );
    private static final List<String> TITLE_PATTERNS = List.of("推荐", "合集", "测评", "避雷", "平替", "必备", "清单", "教程", "分享", "对比");

    @Override
    public XhsAnalysisResult analyze(List<XhsNoteItemView> items) {
        List<XhsNoteItemView> safeItems = items == null ? List.of() : items;
        Map<String, Integer> keywordCounter = new HashMap<>();
        Map<String, Integer> tagCounter = new HashMap<>();
        Map<String, Integer> titlePatternCounter = new HashMap<>();

        for (XhsNoteItemView item : safeItems) {
            tokenize(item.getTitle()).forEach(token -> keywordCounter.merge(token, 1, Integer::sum));
            if (item.getTags() != null) {
                item.getTags().stream()
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .forEach(tag -> tagCounter.merge(tag, 1, Integer::sum));
            }
            String title = item.getTitle() == null ? "" : item.getTitle();
            for (String pattern : TITLE_PATTERNS) {
                if (title.contains(pattern)) {
                    titlePatternCounter.merge(pattern, 1, Integer::sum);
                }
            }
        }

        List<String> topKeywords = topKeys(keywordCounter, 10);
        List<String> topTags = topKeys(tagCounter, 10);
        List<String> titlePatterns = topKeys(titlePatternCounter, 10);

        List<String> insights = List.of(
                topTags.isEmpty() ? "当前样本标签较分散，建议继续扩大采样范围。" : "高频标签集中在：" + String.join("、", topTags.stream().limit(3).toList()) + "。",
                topKeywords.isEmpty() ? "标题关键词还不明显。" : "高频标题关键词包括：" + String.join("、", topKeywords.stream().limit(5).toList()) + "。",
                titlePatterns.isEmpty() ? "标题模式词暂不明显。" : "标题里常见的表达包括：" + String.join("、", titlePatterns.stream().limit(5).toList()) + "。"
        );

        String summary = "共分析 " + safeItems.size() + " 条笔记。高表现内容更偏向 "
                + (topTags.isEmpty() ? "若干热门话题" : String.join("、", topTags.stream().limit(3).toList()))
                + "，标题常带有 "
                + (titlePatterns.isEmpty() ? "推荐/对比/分享" : String.join("、", titlePatterns.stream().limit(3).toList()))
                + " 等表达。";

        return XhsAnalysisResult.builder()
                .totalCount(safeItems.size())
                .topKeywords(topKeywords)
                .topTags(topTags)
                .titlePatterns(titlePatterns)
                .insightPoints(insights)
                .summary(summary)
                .build();
    }

    private List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        return List.of(text.split("[\\s,，。！!？?、/()（）【】\\[\\]：:]+")).stream()
                .map(String::trim)
                .filter(token -> token.length() >= 2)
                .filter(token -> !STOPWORDS.contains(token.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private List<String> topKeys(Map<String, Integer> counter, int limit) {
        return counter.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }
}
