package com.dora.jagent.service.impl;

import com.dora.jagent.model.response.WorkspaceLolEsportsResponse;
import com.dora.jagent.model.response.WorkspaceLolMatchView;
import com.dora.jagent.model.response.WorkspaceNoteSyncResponse;
import com.dora.jagent.model.response.WorkspaceWeatherResponse;
import com.dora.jagent.service.MarkdownNoteSyncService;
import com.dora.jagent.service.IpLocationService;
import com.dora.jagent.service.LolEsportsService;
import com.dora.jagent.service.LolEsportsRecapService;
import com.dora.jagent.service.WeatherQueryService;
import com.dora.jagent.service.WorkspaceService;
import com.dora.jagent.service.impl.support.IpLocationSnapshot;
import com.dora.jagent.service.impl.support.LolEsportsMatchSnapshot;
import com.dora.jagent.service.impl.support.WeatherSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkspaceServiceImpl implements WorkspaceService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");

    private final IpLocationService ipLocationService;
    private final LolEsportsService lolEsportsService;
    private final LolEsportsRecapService lolEsportsRecapService;
    private final WeatherQueryService weatherQueryService;
    private final MarkdownNoteSyncService markdownNoteSyncService;

    @Override
    public WorkspaceWeatherResponse getTodayWeatherCard(String clientIp) {
        IpLocationSnapshot locationSnapshot = ipLocationService.locateByIp(clientIp);
        WeatherSnapshot snapshot = weatherQueryService.getTodayWeather(
                locationSnapshot.getDisplayName(),
                locationSnapshot.getLatitude(),
                locationSnapshot.getLongitude()
        );

        return WorkspaceWeatherResponse.builder()
                .location(locationSnapshot.getDisplayName())
                .headline(buildHeadline(snapshot))
                .trend(buildTrend(snapshot))
                .rainAdvice(buildRainAdvice(snapshot))
                .dressAdvice(buildDressAdvice(snapshot))
                .detail(buildDetail(snapshot))
                .build();
    }

    @Override
    public WorkspaceLolEsportsResponse getTodayLolEsportsCard() {
        List<LolEsportsMatchSnapshot> snapshots = lolEsportsService.getTodayKeyMatches();
        var recapMap = lolEsportsRecapService.generateRecaps(snapshots);

        List<WorkspaceLolMatchView> matches = snapshots.stream()
                .map(snapshot -> toMatchView(snapshot, recapMap.get(snapshot.getEventId())))
                .toList();

        LocalDate today = LocalDate.now(DISPLAY_ZONE);
        LocalDate displayedDate = snapshots.isEmpty() ? today : snapshots.get(0).getStartTime().toLocalDate();
        boolean showingToday = displayedDate.equals(today);
        boolean allUnstarted = !snapshots.isEmpty() && snapshots.stream().allMatch(this::isUnstarted);
        boolean anyInProgress = snapshots.stream().anyMatch(this::isInProgress);

        return WorkspaceLolEsportsResponse.builder()
                .title(showingToday ? "今日赛事" : "最近赛事")
                .subtitle(buildLolSubtitle(showingToday, allUnstarted, anyInProgress))
                .dateLabel(DateTimeFormatter.ofPattern("yyyy年M月d日").format(displayedDate))
                .matches(matches)
                .build();
    }

    @Override
    public WorkspaceNoteSyncResponse getNoteSyncCard() {
        return markdownNoteSyncService.scanNotes();
    }

    @Override
    public WorkspaceNoteSyncResponse syncNotesToNotion() {
        return markdownNoteSyncService.syncNotes();
    }

    private String buildHeadline(WeatherSnapshot snapshot) {
        return switch (snapshot.getWeatherCode()) {
            case 0 -> "今日晴";
            case 1, 2 -> "今日晴间多云";
            case 3 -> "今日阴";
            case 45, 48 -> "今日有雾";
            case 51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> "今日有雨";
            case 71, 73, 75, 77, 85, 86 -> "今日有雪";
            case 95, 96, 99 -> "今日雷雨";
            default -> "今日天气平稳";
        };
    }

    private String buildTrend(WeatherSnapshot snapshot) {
        double spread = snapshot.getMaxTemperature() - snapshot.getMinTemperature();
        if (spread >= 8) {
            return "白天气温回升明显";
        }
        if (snapshot.getCurrentTemperature() <= snapshot.getMinTemperature() + 2) {
            return "气温还会继续上升";
        }
        if (snapshot.getCurrentTemperature() >= snapshot.getMaxTemperature() - 2) {
            return "今天体感较稳定";
        }
        return "昼夜温差较平稳";
    }

    private String buildRainAdvice(WeatherSnapshot snapshot) {
        if (snapshot.getPrecipitationProbability() >= 60 || snapshot.getCurrentPrecipitation() > 0.2) {
            return "建议带伞";
        }
        if (snapshot.getPrecipitationProbability() >= 25) {
            return "可视情况带伞";
        }
        return "无需带伞";
    }

    private String buildDressAdvice(WeatherSnapshot snapshot) {
        double max = snapshot.getMaxTemperature();
        if (max >= 30) {
            return "轻薄夏装，注意防晒";
        }
        if (max >= 22) {
            return "短袖或薄外套即可";
        }
        if (max >= 15) {
            return "建议长袖加薄外套";
        }
        if (max >= 8) {
            return "建议外套或针织衫";
        }
        return "注意保暖";
    }

    private String buildDetail(WeatherSnapshot snapshot) {
        return "%.1f°C - %.1f°C，降水概率 %d%%，风速 %.1f km/h".formatted(
                snapshot.getMinTemperature(),
                snapshot.getMaxTemperature(),
                snapshot.getPrecipitationProbability(),
                snapshot.getWindSpeed()
        );
    }

    private WorkspaceLolMatchView toMatchView(LolEsportsMatchSnapshot snapshot, String recap) {
        String team1 = snapshot.getTeam1Code() != null && !snapshot.getTeam1Code().isBlank()
                ? snapshot.getTeam1Code()
                : snapshot.getTeam1Name();
        String team2 = snapshot.getTeam2Code() != null && !snapshot.getTeam2Code().isBlank()
                ? snapshot.getTeam2Code()
                : snapshot.getTeam2Name();

        return WorkspaceLolMatchView.builder()
                .league(snapshot.getLeagueName())
                .stage(snapshot.getBlockName() == null || snapshot.getBlockName().isBlank() ? "常规场次" : snapshot.getBlockName())
                .status(toMatchStatus(snapshot.getState()))
                .timeLabel(snapshot.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                .matchup(team1 + " vs " + team2)
                .scoreLine(buildScoreLine(snapshot))
                .bestOfLabel(snapshot.getBestOf() != null ? "Bo" + snapshot.getBestOf() : "赛制待定")
                .recap(recap)
                .build();
    }

    private String buildScoreLine(LolEsportsMatchSnapshot snapshot) {
        if ("completed".equalsIgnoreCase(snapshot.getState())
                || "inprogress".equalsIgnoreCase(snapshot.getState())
                || "in_progress".equalsIgnoreCase(snapshot.getState())) {
            int team1Wins = snapshot.getTeam1Wins() == null ? 0 : snapshot.getTeam1Wins();
            int team2Wins = snapshot.getTeam2Wins() == null ? 0 : snapshot.getTeam2Wins();
            return team1Wins + " : " + team2Wins;
        }
        return "未开赛";
    }

    private String toMatchStatus(String state) {
        if (state == null) {
            return "状态未知";
        }
        return switch (state.toLowerCase()) {
            case "unstarted" -> "未开始";
            case "inprogress", "in_progress" -> "进行中";
            case "completed" -> "已结束";
            default -> "状态未知";
        };
    }

    private String buildLolSubtitle(boolean showingToday, boolean allUnstarted, boolean anyInProgress) {
        if (!showingToday) {
            return "今日比赛尚未开打，先展示昨日已结束的重点赛果";
        }
        if (anyInProgress) {
            return "今日有比赛正在进行中，展示今天全部相关赛程";
        }
        if (allUnstarted) {
            return "今日赛程尚未开打";
        }
        return "仅展示 LCK 与官方国际赛事（MSI / Worlds / First Stand）";
    }

    private boolean isUnstarted(LolEsportsMatchSnapshot match) {
        return "unstarted".equalsIgnoreCase(match.getState());
    }

    private boolean isInProgress(LolEsportsMatchSnapshot match) {
        return "inprogress".equalsIgnoreCase(match.getState())
                || "in_progress".equalsIgnoreCase(match.getState());
    }
}
