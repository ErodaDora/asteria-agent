package com.dora.jagent.watermark.service.impl;

import com.dora.jagent.exception.BizException;
import com.dora.jagent.watermark.model.WatermarkMcpServiceStatusResponse;
import com.dora.jagent.watermark.service.WatermarkMcpService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class WatermarkMcpServiceImpl implements WatermarkMcpService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper;

    @Value("${watermark.mcp.url:http://127.0.0.1:18160}")
    private String watermarkMcpUrl;

    @Value("${watermark.mcp.binary:}")
    private String watermarkMcpBinary;

    @Value("${watermark.mcp.script:}")
    private String watermarkMcpScript;

    @Value("${watermark.mcp.python:python3}")
    private String watermarkMcpPython;

    @Value("${watermark.mcp.port:18160}")
    private int watermarkMcpPort;

    @Value("${watermark.mcp.workspace-root:.}")
    private String watermarkWorkspaceRoot;

    @Value("${watermark.mcp.output-dir:./watermark-outputs}")
    private String watermarkOutputDir;

    private volatile Process mcpProcess;

    @Override
    public WatermarkMcpServiceStatusResponse getServiceStatus() {
        boolean running = isRunning();
        return WatermarkMcpServiceStatusResponse.builder()
                .running(running)
                .port(watermarkMcpPort)
                .binary(watermarkMcpBinary)
                .script(watermarkMcpScript)
                .message(running ? "Watermark MCP 服务运行中" : "Watermark MCP 服务未运行")
                .build();
    }

    @Override
    public WatermarkMcpServiceStatusResponse startService() {
        if (isRunning()) {
            return WatermarkMcpServiceStatusResponse.builder()
                    .running(true)
                    .port(watermarkMcpPort)
                    .binary(watermarkMcpBinary)
                    .script(watermarkMcpScript)
                    .message("服务已在运行")
                    .build();
        }

        List<String> command = buildStartCommand();
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.directory(resolveWorkingDirectory().toFile());
            builder.redirectErrorStream(true);
            builder.environment().put("PYTHONUNBUFFERED", "1");
            mcpProcess = builder.start();

            for (int i = 0; i < 20; i++) {
                Thread.sleep(500);
                if (isPortOpen()) {
                    return WatermarkMcpServiceStatusResponse.builder()
                            .running(true)
                            .port(watermarkMcpPort)
                            .binary(watermarkMcpBinary)
                            .script(watermarkMcpScript)
                            .message("Watermark MCP 服务已启动")
                            .build();
                }
            }

            return WatermarkMcpServiceStatusResponse.builder()
                    .running(false)
                    .port(watermarkMcpPort)
                    .binary(watermarkMcpBinary)
                    .script(watermarkMcpScript)
                    .message("进程已启动，但端口未就绪，请稍后重试")
                    .build();
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("启动 Watermark MCP 服务失败：" + exception.getMessage());
        }
    }

    @Override
    public WatermarkMcpServiceStatusResponse stopService() {
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

        return WatermarkMcpServiceStatusResponse.builder()
                .running(false)
                .port(watermarkMcpPort)
                .binary(watermarkMcpBinary)
                .script(watermarkMcpScript)
                .message("服务已停止")
                .build();
    }

    @Override
    public List<Map<String, Object>> listTools() {
        ensureServiceRunning();
        Map<String, Object> result = sendGet("/api/mcp/tools");
        Object tools = result.get("tools");
        if (!(tools instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                normalized.add(new LinkedHashMap<>((Map<String, Object>) map));
            }
        }
        return normalized;
    }

    @Override
    public Map<String, Object> searchImages(Map<String, Object> arguments) {
        return callTool("search_image_files", arguments);
    }

    @Override
    public Map<String, Object> embedImages(Map<String, Object> arguments) {
        return callTool("embed_watermark_images", arguments);
    }

    @Override
    public Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        if (!StringUtils.hasText(toolName)) {
            throw new BizException("toolName 不能为空");
        }
        ensureServiceRunning();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", toolName);
        payload.put("arguments", arguments == null ? Map.of() : arguments);
        Map<String, Object> result = sendPost("/api/mcp/tools/call", payload);
        if (Boolean.FALSE.equals(result.get("success"))) {
            throw new BizException("Watermark MCP 工具调用失败：" + result.getOrDefault("output", "unknown error"));
        }
        return result;
    }

    private void ensureServiceRunning() {
        if (isRunning()) {
            return;
        }
        WatermarkMcpServiceStatusResponse startResult = startService();
        if (!startResult.isRunning()) {
            throw new BizException(startResult.getMessage());
        }
    }

    private boolean isRunning() {
        Process current = mcpProcess;
        return (current != null && current.isAlive()) || isPortOpen();
    }

    private boolean isPortOpen() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", watermarkMcpPort), 1000);
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private List<String> buildStartCommand() {
        List<String> command = new ArrayList<>();
        if (StringUtils.hasText(watermarkMcpBinary)) {
            command.add(Path.of(watermarkMcpBinary).toAbsolutePath().toString());
        } else if (StringUtils.hasText(watermarkMcpScript)) {
            command.add(watermarkMcpPython);
            command.add(Path.of(watermarkMcpScript).toAbsolutePath().toString());
        } else {
            throw new BizException("未配置 watermark.mcp.binary 或 watermark.mcp.script，无法启动 Watermark MCP 服务");
        }

        command.add("--port");
        command.add(String.valueOf(watermarkMcpPort));
        command.add("--workspace-root");
        command.add(Path.of(watermarkWorkspaceRoot).toAbsolutePath().toString());
        command.add("--output-dir");
        command.add(Path.of(watermarkOutputDir).toAbsolutePath().toString());
        return command;
    }

    private Path resolveWorkingDirectory() {
        if (StringUtils.hasText(watermarkMcpBinary)) {
            Path binaryPath = Path.of(watermarkMcpBinary).toAbsolutePath();
            Path parent = binaryPath.getParent();
            if (parent == null) {
                throw new BizException("无法定位 watermark.mcp.binary 所在目录");
            }
            return parent;
        }

        Path scriptPath = Path.of(watermarkMcpScript).toAbsolutePath();
        Path parent = scriptPath.getParent();
        if (parent == null) {
            throw new BizException("无法定位 watermark.mcp.script 所在目录");
        }
        return parent;
    }

    private Map<String, Object> sendGet(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(watermarkMcpUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseJsonResponse(response);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("调用 Watermark MCP 服务失败：" + exception.getMessage());
        }
    }

    private Map<String, Object> sendPost(String path, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(watermarkMcpUrl + path))
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseJsonResponse(response);
        } catch (BizException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BizException("调用 Watermark MCP 服务失败：" + exception.getMessage());
        }
    }

    private Map<String, Object> parseJsonResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BizException("Watermark MCP 服务响应异常，status=" + response.statusCode());
        }
        return objectMapper.readValue(response.body(), new TypeReference<Map<String, Object>>() {});
    }
}
