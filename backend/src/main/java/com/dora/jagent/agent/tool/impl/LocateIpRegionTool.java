package com.dora.jagent.agent.tool.impl;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.core.ToolExecutionResult;
import com.dora.jagent.service.IpLocationService;
import com.dora.jagent.service.impl.support.IpLocationSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class LocateIpRegionTool implements AgentTool {

    private final IpLocationService ipLocationService;

    @Override
    public String getName() {
        return "locate_ip_region";
    }

    @Override
    public String getDescription() {
        return "根据输入的 IP 地址查询所在地区和近似坐标。适合需要判断某个 IP 大致位于哪个国家、省市区时使用。输入应是 IPv4 或 IPv6 地址。";
    }

    @Override
    public ToolExecutionResult execute(String input) {
        if (!StringUtils.hasText(input)) {
            return failed(input, "IP 地址不能为空");
        }

        String ip = input.trim();
        try {
            // =================== Tool 协同说明 ===================
            // 这个工具负责“IP -> 地区”的基础定位，不直接回答天气。
            // 推荐协同链路是：
            // 1. 先调用 locate_ip_region 拿到目标区域
            // 2. 再把区域名传给 get_today_weather
            // 3. 由模型把定位结果和天气结果组织成最终回复
            // ================================================
            IpLocationSnapshot snapshot = ipLocationService.locateByIp(ip);
            String output = """
                    IP：%s
                    定位地区：%s
                    国家：%s
                    省份/州：%s
                    城市：%s
                    区县：%s
                    坐标：%.4f, %.4f
                    """.formatted(
                    snapshot.getResolvedIp(),
                    snapshot.getDisplayName(),
                    defaultText(snapshot.getCountry()),
                    defaultText(snapshot.getRegion()),
                    defaultText(snapshot.getCity()),
                    defaultText(snapshot.getDistrict()),
                    snapshot.getLatitude(),
                    snapshot.getLongitude()
            );

            return ToolExecutionResult.builder()
                    .toolName(getName())
                    .success(true)
                    .input(ip)
                    .output(output)
                    .build();
        } catch (Exception e) {
            return failed(ip, "IP 定位失败：" + e.getMessage());
        }
    }

    private String defaultText(String value) {
        return StringUtils.hasText(value) ? value : "未知";
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
