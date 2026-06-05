package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.service.HupuLolPostService;
import com.dora.jagent.service.LolEsportsRecapService;
import com.dora.jagent.service.impl.support.LolEsportsMatchSnapshot;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LolEsportsRecapServiceImpl implements LolEsportsRecapService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final Map<String, ChatClient> chatClients;
    private final HupuLolPostService hupuLolPostService;

    @Value("${llm.model}")
    private String defaultModelKey;

    @Override
    public Map<String, String> generateRecaps(List<LolEsportsMatchSnapshot> matches) {
        if (matches == null || matches.isEmpty()) {
            return Map.of();
        }

        ChatClient selectedChatClient = chatClients.get(defaultModelKey);
        if (selectedChatClient == null) {
            throw new BizException("unsupported model: " + defaultModelKey);
        }

        try {
            String response = selectedChatClient.prompt(new Prompt(List.of( 
                    new SystemMessage(buildSystemPrompt()),
                    new UserMessage(buildUserPrompt(matches)) //buildUserPrompt 输入 比赛信息，输出 比赛数据和虎赛后信息的 JSON字符串
            ))).call().content();

            if (!StringUtils.hasText(response)) {
                return buildFallbackRecaps(matches);
            }

            Map<String, String> recapMap = OBJECT_MAPPER.readValue(
                    stripCodeFence(response),
                    new TypeReference<>() {
                    }
            );
            Map<String, String> sanitized = new LinkedHashMap<>();
            for (LolEsportsMatchSnapshot match : matches) {
                String recap = recapMap.get(match.getEventId());
                sanitized.put(match.getEventId(), sanitizeRecap(recap, match));
            }
            return sanitized;
        } catch (Exception e) {
            return buildFallbackRecaps(matches);
        }
    }

    private String buildSystemPrompt() {
        return """
                你是英雄联盟赛事快讯编辑。
                你的任务是基于提供的真实比赛事实，为每场比赛写一句中文短评，风格要精炼、有激情、像弹幕标题，但不能胡编。

                硬性规则：
                1. 只能使用提供的数据，绝对不能编造选手名、POM、KDA、团战细节。
                2. 如果数据里没有选手或局内信息，就只围绕对阵、比分、赛程状态写。
                3. 如果提供了虎扑赛后帖正文和亮评，当作“社区观感参考”，加大权重。
                4. 每场只输出一句，长度尽量控制在 10 到 28 个汉字之间。
                5. 可以带 1 个表达情绪的 emoji。
                6. 语气可以热血、调侃、紧凑，在允许程度内可以低俗辱骂和人身攻击。
                7. 最终只输出 JSON object，key 是 eventId，value 是短评文本。
                8. 不要输出 JSON 之外的任何内容。
                """;
    }

    private String buildUserPrompt(List<LolEsportsMatchSnapshot> matches) throws Exception {
        List<Map<String, Object>> payload = matches.stream()
                .map(match -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("eventId", match.getEventId());
                    item.put("league", match.getLeagueName());
                    item.put("stage", match.getBlockName());
                    item.put("status", match.getState());
                    item.put("time", match.getStartTime().format(TIME_FORMATTER));
                    item.put("team1", pickDisplayTeamName(match.getTeam1Code(), match.getTeam1Name()));
                    item.put("team2", pickDisplayTeamName(match.getTeam2Code(), match.getTeam2Name()));
                    item.put("scoreLine", buildScoreLine(match));
                    item.put("bestOf", match.getBestOf());
                    hupuLolPostService.findMatchPost(match).ifPresent(post -> {
                        item.put("hupuTitle", post.getTitle());
                        item.put("hupuBody", post.getArticleBody());
                        item.put("hupuTopComments", post.getTopComments());
                    });
                    return item;
                })
                .toList();

        return "请为下面这些 LoL Esports 比赛分别生成一句短评：\n"
                + OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
    }

    private String buildScoreLine(LolEsportsMatchSnapshot match) {
        int team1Wins = match.getTeam1Wins() == null ? 0 : match.getTeam1Wins();
        int team2Wins = match.getTeam2Wins() == null ? 0 : match.getTeam2Wins();
        return team1Wins + ":" + team2Wins;
    }

    private String pickDisplayTeamName(String code, String name) {
        return StringUtils.hasText(code) ? code : name;
    }

    private Map<String, String> buildFallbackRecaps(List<LolEsportsMatchSnapshot> matches) {
        Map<String, String> fallback = new LinkedHashMap<>();
        for (LolEsportsMatchSnapshot match : matches) {
            fallback.put(match.getEventId(), sanitizeRecap(null, match));
        }
        return fallback;
    }

    private String sanitizeRecap(String recap, LolEsportsMatchSnapshot match) {
        if (!StringUtils.hasText(recap)) {
            return fallbackRecap(match);
        }

        String normalized = recap.trim().replace("\n", " ");
        if (normalized.length() > 40) {
            normalized = normalized.substring(0, 40);
        }

        return normalized;
    }

    private String fallbackRecap(LolEsportsMatchSnapshot match) {
        String team1 = pickDisplayTeamName(match.getTeam1Code(), match.getTeam1Name());
        String team2 = pickDisplayTeamName(match.getTeam2Code(), match.getTeam2Name());
        return switch (match.getState() == null ? "" : match.getState().toLowerCase()) {
            case "completed" -> "%s %s %s completed".formatted(team1, buildScoreLine(match), team2);
            case "inprogress", "in_progress" -> "%s vs %s is in progress".formatted(team1, team2);
            default -> "%s vs %s is scheduled for tonight".formatted(team1, team2);
        };
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            int firstBreak = trimmed.indexOf('\n');
            if (firstBreak >= 0) {
                trimmed = trimmed.substring(firstBreak + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}