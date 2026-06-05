package com.dora.jagent.service.impl;

import com.dora.jagent.service.HupuLolPostService;
import com.dora.jagent.service.impl.support.HupuLolPostSnapshot;
import com.dora.jagent.service.impl.support.LolEsportsMatchSnapshot;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class HupuLolPostServiceImpl implements HupuLolPostService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final List<String> BOARD_URLS = List.of(
            "https://bbs.hupu.com/lol-hot",
            "https://bbs.hupu.com/lol"
    );

    private static final Pattern BBS_ID_PATTERN = Pattern.compile("/bbs/(\\d+)");

    @Override
    public Optional<HupuLolPostSnapshot> findMatchPost(LolEsportsMatchSnapshot matchSnapshot) {
        if (matchSnapshot == null || "unstarted".equalsIgnoreCase(matchSnapshot.getState())) {
            return Optional.empty();
        }
        try {
            Set<String> aliases = buildAliases(matchSnapshot);
            List<PostCandidate> candidates = new ArrayList<>();
            for (String boardUrl : BOARD_URLS) {
                candidates.addAll(fetchCandidates(boardUrl, aliases));
            }

            return candidates.stream()
                    .sorted((left, right) -> Integer.compare(right.score(), left.score()))
                    .map(PostCandidate::url)
                    .distinct()
                    .map(this::fetchPost)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private List<PostCandidate> fetchCandidates(String boardUrl, Set<String> aliases) throws Exception {
        Document document = Jsoup.parse(sendRequest(boardUrl), boardUrl);
        List<PostCandidate> candidates = new ArrayList<>();
        for (Element link : document.select("a[href]")) {
            String text = normalizeWhitespace(link.text());
            if (!StringUtils.hasText(text)) {
                continue;
            }

            String href = link.absUrl("href");
            if (!href.contains("/bbs/")) {
                continue;
            }
            int score = scoreTitle(text, aliases);
            if (score <= 0) {
                continue;
            }

            String mobileUrl = toMobileBbsUrl(href);
            if (StringUtils.hasText(mobileUrl)) {
                candidates.add(new PostCandidate(text, mobileUrl, score));
            }
        }
        return candidates;
    }

    private Optional<HupuLolPostSnapshot> fetchPost(String url) {
        try {
            Document document = Jsoup.parse(sendRequest(url), url);
            String text = normalizeWhitespace(document.body().text());
            if (!StringUtils.hasText(text)) {
                return Optional.empty();
            }

            String title = normalizeWhitespace(document.title());
            String articleBody = extractArticleBody(text);
            List<String> topComments = extractTopComments(text);
            if (!StringUtils.hasText(articleBody) && topComments.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(HupuLolPostSnapshot.builder()
                    .title(title)
                    .sourceUrl(url)
                    .articleBody(articleBody)
                    .topComments(topComments)
                    .build());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String extractArticleBody(String text) {
        String content = text;
        content = extractBetween(content, "讯", "来源：");
        if (!StringUtils.hasText(content)) {
            content = extractBetween(text, "〖赛况简介〗", "〖赛后数据〗");
        }
        if (!StringUtils.hasText(content)) {
            content = extractBetween(text, "〖比赛概况〗", "〖赛后数据〗");
        }
        if (!StringUtils.hasText(content)) {
            content = extractBetween(text, "〖赛况简介〗", "来源：");
        }
        if (!StringUtils.hasText(content)) {
            content = extractBetween(text, "〖比赛概况〗", "来源：");
        }
        if (!StringUtils.hasText(content)) {
            return "";
        }
        return truncate(content, 500);
    }

    private List<String> extractTopComments(String text) {
        List<String> comments = new ArrayList<>();
        String commentArea = extractBetween(text, "这些回复亮了", "展开全部回复");
        if (!StringUtils.hasText(commentArea)) {
            commentArea = extractBetween(text, "立即评分", "没有更多了");
        }
        if (!StringUtils.hasText(commentArea)) {
            return comments;
        }

        for (String part : commentArea.split("点亮")) {
            String cleaned = normalizeWhitespace(part);
            if (!StringUtils.hasText(cleaned)) {
                continue;
            }
            if (cleaned.length() < 6) {
                continue;
            }
            comments.add(truncate(cleaned, 60));
            if (comments.size() >= 5) {
                break;
            }
        }
        return comments;
    }

    private int scoreTitle(String title, Set<String> aliases) {
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        int score = 0;
        if (normalizedTitle.contains("[赛后]")) {
            score += 5;
        }
        if (normalizedTitle.contains("赛后")) {
            score += 3;
        }
        if (normalizedTitle.contains("虎扑游戏电竞资讯")) {
            score += 1;
        }
        int matchedAliasCount = 0;
        for (String alias : aliases) {
            if (normalizedTitle.contains(alias.toLowerCase(Locale.ROOT))) {
                matchedAliasCount++;
                score += 2;
            }
        }
        return matchedAliasCount >= 2 ? score : 0;
    }

    private Set<String> buildAliases(LolEsportsMatchSnapshot matchSnapshot) {
        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        addAlias(aliases, matchSnapshot.getTeam1Code());
        addAlias(aliases, matchSnapshot.getTeam1Name());
        addAlias(aliases, matchSnapshot.getTeam2Code());
        addAlias(aliases, matchSnapshot.getTeam2Name());
        return aliases;
    }

    private void addAlias(Set<String> aliases, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        String trimmed = value.trim();
        aliases.add(trimmed);
        aliases.add(trimmed.replace("Esports", "").replace("esports", "").trim());
        aliases.add(trimmed.replace(" ", ""));
    }

    private String toMobileBbsUrl(String href) {
        Matcher matcher = BBS_ID_PATTERN.matcher(href);
        if (!matcher.find()) {
            return "";
        }
        return "https://m.hupu.com/bbs/" + matcher.group(1);
    }

    private String sendRequest(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("User-Agent", "Mozilla/5.0 JAgent/1.0 (workspace lol hupu)")
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Hupu 响应异常，status=" + response.statusCode());
        }
        return response.body();
    }

    private String extractBetween(String text, String startMarker, String endMarker) {
        int start = text.indexOf(startMarker);
        if (start < 0) {
            return "";
        }
        int from = start + startMarker.length();
        int end = text.indexOf(endMarker, from);
        if (end < 0) {
            end = text.length();
        }
        return normalizeWhitespace(text.substring(from, end));
    }

    private String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String truncate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private record PostCandidate(String title, String url, int score) {
    }
}
