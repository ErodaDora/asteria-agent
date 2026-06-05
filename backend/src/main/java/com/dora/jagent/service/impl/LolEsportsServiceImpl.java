package com.dora.jagent.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.service.LolEsportsService;
import com.dora.jagent.service.impl.support.LolEsportsMatchSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LolEsportsServiceImpl implements LolEsportsService {

    private static final String API_KEY = "0TvQnueqKa5mxJntVWt0w4LpLfEkrV1Ta8rQBb9Z";
    private static final String SCHEDULE_URL = "https://esports-api.lolesports.com/persisted/gw/getSchedule?hl=en-US";
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final Set<String> TARGET_LEAGUE_SLUGS = Set.of(
            "lck",
            "msi",
            "worlds",
            "firststand"
    );
    private static final Set<String> TARGET_LEAGUE_NAMES = Set.of(
            "lck",
            "msi",
            "worlds",
            "first stand"
    );

    @Override
    public List<LolEsportsMatchSnapshot> getTodayKeyMatches() {
        try {
            JsonNode root = fetchSchedule();
            JsonNode events = root.path("data").path("schedule").path("events");
            if (!events.isArray() || events.isEmpty()) {
                return List.of();
            }

            LocalDate today = LocalDate.now(DISPLAY_ZONE);
            LocalDate yesterday = today.minusDays(1);
            List<LolEsportsMatchSnapshot> matches = new ArrayList<>();
            for (JsonNode event : events) {
                if (!"match".equalsIgnoreCase(event.path("type").asText())) {
                    continue;
                }

                JsonNode league = event.path("league");
                if (!isTargetLeague(league)) {
                    continue;
                }

                ZonedDateTime startTime = parseStartTime(event.path("startTime").asText(""));
                if (startTime == null) {
                    continue;
                }
                LocalDate eventDate = startTime.withZoneSameInstant(DISPLAY_ZONE).toLocalDate();
                if (!today.equals(eventDate) && !yesterday.equals(eventDate)) {
                    continue;
                }

                JsonNode match = event.path("match");
                JsonNode teams = match.path("teams");
                if (!teams.isArray() || teams.size() < 2) {
                    continue;
                }

                JsonNode team1 = teams.get(0);
                JsonNode team2 = teams.get(1);
                matches.add(LolEsportsMatchSnapshot.builder()
                        .eventId(event.path("id").asText(match.path("id").asText("")))
                        .leagueName(league.path("name").asText(""))
                        .leagueSlug(league.path("slug").asText(""))
                        .blockName(event.path("blockName").asText(""))
                        .state(event.path("state").asText(""))
                        .startTime(startTime.withZoneSameInstant(DISPLAY_ZONE))
                        .team1Name(team1.path("name").asText(""))
                        .team1Code(team1.path("code").asText(""))
                        .team1Wins(readTeamWins(team1))
                        .team2Name(team2.path("name").asText(""))
                        .team2Code(team2.path("code").asText(""))
                        .team2Wins(readTeamWins(team2))
                        .bestOf(match.path("strategy").path("count").isNumber()
                                ? match.path("strategy").path("count").asInt()
                                : null)
                        .build());
            }

            matches.sort(Comparator.comparing(LolEsportsMatchSnapshot::getStartTime));
            return selectDisplayMatches(matches, today, yesterday);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("LoL Esports 赛程查询失败：" + e.getMessage());
        }
    }

    private JsonNode fetchSchedule() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SCHEDULE_URL))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .header("Accept", "application/json")
                .header("x-api-key", API_KEY)
                .header("User-Agent", "JAgent/1.0 (workspace lol schedule)")
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("LoL Esports 服务响应异常，status=" + response.statusCode());
        }
        return OBJECT_MAPPER.readTree(response.body());
    }

    private boolean isTargetLeague(JsonNode leagueNode) {
        String slug = normalizeLeagueToken(leagueNode.path("slug").asText(""));
        String name = normalizeLeagueToken(leagueNode.path("name").asText(""));
        return TARGET_LEAGUE_SLUGS.contains(slug) || TARGET_LEAGUE_NAMES.contains(name);
    }

    private String normalizeLeagueToken(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
    }

    private Integer readTeamWins(JsonNode teamNode) {
        JsonNode result = teamNode.path("result");
        return result.path("gameWins").isNumber() ? result.path("gameWins").asInt() : null;
    }

    private ZonedDateTime parseStartTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private List<LolEsportsMatchSnapshot> selectDisplayMatches(
            List<LolEsportsMatchSnapshot> matches,
            LocalDate today,
            LocalDate yesterday
    ) {
        List<LolEsportsMatchSnapshot> todayMatches = filterByDate(matches, today);
        List<LolEsportsMatchSnapshot> yesterdayMatches = filterByDate(matches, yesterday);

        if (todayMatches.isEmpty()) {
            return latestCompleted(yesterdayMatches, 2);
        }

        if (todayMatches.stream().anyMatch(this::isInProgress)) {
            return todayMatches;
        }

        if (todayMatches.stream().allMatch(this::isUnstarted)) {
            List<LolEsportsMatchSnapshot> fallback = latestCompleted(yesterdayMatches, 2);
            return fallback.isEmpty() ? todayMatches.stream().limit(2).toList() : fallback;
        }

        if (todayMatches.stream().allMatch(this::isCompleted)) {
            return todayMatches;
        }

        return todayMatches;
    }

    private List<LolEsportsMatchSnapshot> filterByDate(List<LolEsportsMatchSnapshot> matches, LocalDate date) {
        return matches.stream()
                .filter(match -> date.equals(match.getStartTime().toLocalDate()))
                .collect(Collectors.toList());
    }

    private List<LolEsportsMatchSnapshot> latestCompleted(List<LolEsportsMatchSnapshot> matches, int limit) {
        return matches.stream()
                .filter(this::isCompleted)
                .sorted(Comparator.comparing(LolEsportsMatchSnapshot::getStartTime).reversed())
                .limit(limit)
                .sorted(Comparator.comparing(LolEsportsMatchSnapshot::getStartTime))
                .toList();
    }

    private boolean isCompleted(LolEsportsMatchSnapshot match) {
        return "completed".equalsIgnoreCase(match.getState());
    }

    private boolean isUnstarted(LolEsportsMatchSnapshot match) {
        return "unstarted".equalsIgnoreCase(match.getState());
    }

    private boolean isInProgress(LolEsportsMatchSnapshot match) {
        return "inprogress".equalsIgnoreCase(match.getState())
                || "in_progress".equalsIgnoreCase(match.getState());
    }
}
