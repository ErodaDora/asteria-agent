package com.dora.jagent.xhs.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.xhs.model.XhsMcpPublishPayload;
import com.dora.jagent.xhs.model.XhsMcpServiceStatusResponse;
import com.dora.jagent.xhs.service.XhsMcpService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class XhsMcpServiceImpl implements XhsMcpService {

    private final ObjectMapper objectMapper;

    @Value("${xhs.mcp.url:http://localhost:18060}")
    private String xhsMcpUrl;

    @Value("${xhs.mcp.endpoint:http://localhost:18060/mcp}")
    private String xhsMcpEndpoint;

    @Value("${xhs.mcp.binary:}")
    private String xhsMcpBinary;

    @Value("${xhs.mcp.port:18060}")
    private int xhsMcpPort;

    @Value("${xhs.mcp.python:python3}")
    private String xhsMcpPython;

    @Value("${xhs.mcp.bridge-script:./scripts/xhs_mcp_bridge.py}")
    private String xhsMcpBridgeScript;

    @Value("${xhs.mcp.bridge-timeout-seconds:120}")
    private int bridgeTimeoutSeconds;

    @Value("${xhs.mcp.cookie-path:./data/xhs/mcp_cookies.json}")
    private String xhsMcpCookiePath;

    private volatile Process mcpProcess;

    @Override
    public XhsMcpServiceStatusResponse getServiceStatus() {
        boolean running = isRunning();
        return XhsMcpServiceStatusResponse.builder()
                .running(running)
                .port(xhsMcpPort)
                .binary(xhsMcpBinary)
                .message(running ? "MCP 服务运行中" : "MCP 服务未运行")
                .build();
    }

    @Override
    public XhsMcpServiceStatusResponse startService(boolean headless) {
        if (isRunning()) {
            return XhsMcpServiceStatusResponse.builder()
                    .running(true)
                    .port(xhsMcpPort)
                    .binary(xhsMcpBinary)
                    .message("服务已在运行")
                    .build();
        }
        if (!StringUtils.hasText(xhsMcpBinary)) {
            throw new BizException("未配置 xhs.mcp.binary，无法启动小红书 MCP 服务");
        }

        try {
            ProcessBuilder builder = new ProcessBuilder(buildStartCommand(headless));
            builder.directory(Path.of(xhsMcpBinary).toAbsolutePath().getParent().toFile());
            builder.environment().put("COOKIES_PATH", resolvedCookiePath());
            mcpProcess = builder.start();
            for (int i = 0; i < 16; i++) {
                Thread.sleep(500);
                if (isPortOpen()) {
                    return XhsMcpServiceStatusResponse.builder()
                            .running(true)
                            .port(xhsMcpPort)
                            .binary(xhsMcpBinary)
                            .message("MCP 服务已启动")
                            .build();
                }
            }
            return XhsMcpServiceStatusResponse.builder()
                    .running(false)
                    .port(xhsMcpPort)
                    .binary(xhsMcpBinary)
                    .message("进程已启动，但端口未就绪，请稍后重试")
                    .build();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("启动 MCP 服务失败：" + exception.getMessage());
        }
    }

    @Override
    public XhsMcpServiceStatusResponse stopService() {
        Process current = mcpProcess;
        if (current != null && current.isAlive()) {
            current.destroy();
            try {
                if (!current.waitFor(5, TimeUnit.SECONDS)) {
                    current.destroyForcibly();
                }
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            mcpProcess = null;
        }
        return XhsMcpServiceStatusResponse.builder()
                .running(false)
                .port(xhsMcpPort)
                .binary(xhsMcpBinary)
                .message("服务已停止")
                .build();
    }

    @Override
    public Map<String, Object> runLogin() {
        if (!StringUtils.hasText(xhsMcpBinary)) {
            throw new BizException("未配置 xhs.mcp.binary，无法启动登录工具");
        }

        try {
            Path binaryDir = Path.of(xhsMcpBinary).toAbsolutePath().getParent();
            if (binaryDir == null) {
                throw new BizException("无法定位 xhs.mcp.binary 所在目录");
            }
            Path loginBinary = findLoginBinary(binaryDir);
            ProcessBuilder builder = new ProcessBuilder(loginBinary.toAbsolutePath().toString());
            builder.directory(binaryDir.toFile());
            builder.environment().put("COOKIES_PATH", resolvedCookiePath());
            builder.start();
            return Map.of(
                    "success", true,
                    "message", "已启动登录工具，请在弹出的浏览器中扫码登录",
                    "binary", loginBinary.toString()
            );
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("启动登录工具失败：" + exception.getMessage());
        }
    }

    @Override
    public Map<String, Object> checkLoginStatus() {
        return callTool("check_login_status", Map.of());
    }

    @Override
    public List<String> listTools() {
        try {
            Map<String, Object> result = runBridge("list-tools", null, null);
            Object tools = result.get("tools");
            if (!(tools instanceof List<?> list)) {
                return List.of();
            }
            return list.stream().map(String::valueOf).toList();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("列出 MCP 工具失败：" + exception.getMessage());
        }
    }

    @Override
    public Map<String, Object> listFeeds(Map<String, Object> arguments) {
        return callTool("list_feeds", arguments);
    }

    @Override
    public Map<String, Object> searchFeeds(Map<String, Object> arguments) {
        return callTool("search_feeds", arguments);
    }

    @Override
    public Map<String, Object> publishContent(XhsMcpPublishPayload payload) {
        // Pre-flight: verify login state before spending bridgeTimeoutSeconds on a doomed session
        try {
            Map<String, Object> loginCheck = checkLoginStatus();
            Object loggedIn = loginCheck.get("is_logged_in");
            if (Boolean.FALSE.equals(loggedIn)) {
                throw new BizException("小红书创作者中心未登录，请先点击「重新登录」完成扫码，再发布");
            }
        } catch (BizException exception) {
            throw exception;
        } catch (Exception ignored) {
            // Login check failed (e.g. tool not supported) — proceed and let publish surface the real error
        }

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("title", payload.getTitle());
        arguments.put("content", payload.getContent());
        arguments.put("images", payload.getImages());
        if (payload.getTags() != null && !payload.getTags().isEmpty()) {
            arguments.put("tags", payload.getTags());
        }
        if (StringUtils.hasText(payload.getVisibility())) {
            arguments.put("visibility", payload.getVisibility());
        }
        arguments.put("is_original", payload.isIsOriginal());
        return callTool("publish_content", arguments);
    }

    @Override
    public Map<String, Object> publishVideo(Map<String, Object> arguments) {
        return callTool("publish_with_video", arguments);
    }

    @Override
    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        if (!StringUtils.hasText(toolName)) {
            throw new BizException("toolName 不能为空");
        }
        ensureServiceRunning();

        try {
            Map<String, Object> result = runBridge("call-tool", toolName, arguments == null ? Map.of() : arguments);
            if (Boolean.FALSE.equals(result.get("success")) && StringUtils.hasText(String.valueOf(result.get("error")))) {
                throw new BizException("MCP 工具调用失败：" + result.get("error"));
            }
            return result;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("MCP 工具调用失败：" + exception.getMessage());
        }
    }

    private List<String> buildStartCommand(boolean headless) {
        if (headless) {
            return List.of(xhsMcpBinary);
        }
        return List.of(xhsMcpBinary, "-headless=false");
    }

    private String resolvedCookiePath() {
        Path cookiePath = Path.of(xhsMcpCookiePath).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(cookiePath.getParent());
        } catch (IOException ignored) {}
        return cookiePath.toString();
    }

    private Path findLoginBinary(Path binaryDir) throws IOException {
        try (var stream = java.nio.file.Files.list(binaryDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase().contains("login"))
                    .findFirst()
                    .orElseThrow(() -> new BizException("未找到登录工具，请确认目录中存在 *login* 可执行文件"));
        }
    }

    private boolean isRunning() {
        Process current = mcpProcess;
        return (current != null && current.isAlive()) || isPortOpen();
    }

    private boolean isPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", xhsMcpPort), 1000);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void ensureServiceRunning() {
        if (!isRunning()) {
            throw new BizException("小红书 MCP 服务未运行，请先在页面顶部启动 MCP 服务");
        }
    }

    private Map<String, Object> runBridge(String action, String toolName, Map<String, Object> arguments) {
        try {
            Path scriptPath = Path.of(xhsMcpBridgeScript).toAbsolutePath().normalize();
            List<String> command = new java.util.ArrayList<>(List.of(
                    xhsMcpPython,
                    scriptPath.toString(),
                    "--endpoint",
                    xhsMcpEndpoint,
                    "--action",
                    action
            ));
            if (StringUtils.hasText(toolName)) {
                command.add("--tool");
                command.add(toolName);
            }
            if (arguments != null) {
                command.add("--args");
                command.add(objectMapper.writeValueAsString(arguments));
            }

            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(scriptPath.getParent().getParent().toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();

            String output;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().reduce("", (left, right) -> left + right);
            }

            boolean finished = process.waitFor(bridgeTimeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BizException(
                        "MCP bridge 调用超时（" + bridgeTimeoutSeconds + "秒），发布未完成。" +
                        "常见原因：小红书登录态已过期，请重新登录后再试；或 MCP 服务内部出现页面跳转异常");
            }
            int exitCode = process.exitValue();
            if (!StringUtils.hasText(output)) {
                throw new BizException("Python MCP bridge 没有返回内容");
            }
            Map<String, Object> result = objectMapper.readValue(output, new TypeReference<>() {});
            if (exitCode != 0) {
                throw new BizException("Python MCP bridge 执行失败：" + result.getOrDefault("error", output));
            }
            return result;
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("调用 Python MCP bridge 失败：" + exception.getMessage());
        }
    }
}
