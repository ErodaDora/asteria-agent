package com.dora.jagent.agent.tool.impl;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.core.ToolExecutionResult;
import com.dora.jagent.service.WeatherQueryService;
import com.dora.jagent.service.impl.support.WeatherSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class GetTodayWeatherTool implements AgentTool {

    private final WeatherQueryService weatherQueryService;

    @Override
    public String getName() {
        return "get_today_weather";
    }

    @Override
    public String getDescription() {
        return "查询指定城市的今天天气。适合响应“今日天气”“穿衣建议”“是否容易下雨”这类实时天气问题。输入应是城市名，例如：上海、北京、Hangzhou。";
    }

    @Override
    public ToolExecutionResult execute(String input) {
        if (!StringUtils.hasText(input)) {
            return failed(input, "城市名不能为空");
        }

        String city = input.trim();
        try {
            // =================== Tool 协同说明 ===================
            // 这个天气工具通常可以单独完成任务，不依赖工作区搜索或读文件工具。
            // Agent 的典型链路是：
            // 1. 模型判断用户需要实时天气
            // 2. 调用 get_today_weather
            // 3. 将天气结果回填到 loop 上下文
            // 4. 再由模型组织成自然语言回复
            // ================================================
            WeatherSnapshot snapshot = weatherQueryService.getTodayWeather(city);
            String summary = buildWeatherSummary(snapshot);

            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(true)
                    .input(city)
                    .output(summary)
                    .build();
        } catch (Exception e) {
            return failed(city, "查询天气失败：" + e.getMessage());
        }
    }

    private String buildWeatherSummary(WeatherSnapshot snapshot) {
        String suggestion = buildClothingSuggestion(snapshot.getMaxTemperature(), snapshot.getMinTemperature(),
                snapshot.getPrecipitationProbability());
        return """
                城市：%s
                今日天气：%s
                当前温度：%.1f°C
                体感温度：%.1f°C
                今日最高 / 最低：%.1f°C / %.1f°C
                当前降水量：%.1f mm
                最大降水概率：%d%%
                当前风速：%.1f km/h
                穿衣建议：%s
                """.formatted(
                snapshot.getResolvedLocation(),
                snapshot.getWeatherText(),
                snapshot.getCurrentTemperature(),
                snapshot.getApparentTemperature(),
                snapshot.getMaxTemperature(),
                snapshot.getMinTemperature(),
                snapshot.getCurrentPrecipitation(),
                snapshot.getPrecipitationProbability(),
                snapshot.getWindSpeed(),
                suggestion
        );
    }

    private String buildClothingSuggestion(double maxTemp, double minTemp, int precipitationProbability) {
        String temperatureAdvice;
        if (maxTemp >= 30) {
            temperatureAdvice = "天气偏热，建议轻薄透气衣物，注意补水和防晒";
        } else if (maxTemp >= 22) {
            temperatureAdvice = "体感较舒适，建议短袖或薄外套";
        } else if (maxTemp >= 15) {
            temperatureAdvice = "早晚可能偏凉，建议长袖加薄外套";
        } else if (maxTemp >= 8) {
            temperatureAdvice = "天气偏凉，建议外套或针织衫";
        } else {
            temperatureAdvice = "天气较冷，建议保暖外套";
        }

        if (precipitationProbability >= 60) {
            return temperatureAdvice + "，并携带雨具";
        }
        return temperatureAdvice;
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
