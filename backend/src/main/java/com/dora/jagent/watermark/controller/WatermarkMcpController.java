package com.dora.jagent.watermark.controller;

import com.dora.jagent.model.common.ApiResponse;
import com.dora.jagent.watermark.model.WatermarkEmbedRequest;
import com.dora.jagent.watermark.model.WatermarkMcpServiceStatusResponse;
import com.dora.jagent.watermark.model.WatermarkMcpToolCallRequest;
import com.dora.jagent.watermark.service.WatermarkMcpService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/watermark/mcp")
@RequiredArgsConstructor
public class WatermarkMcpController {

    private final WatermarkMcpService watermarkMcpService;

    @GetMapping("/status")
    public ApiResponse<WatermarkMcpServiceStatusResponse> getStatus() {
        return ApiResponse.success(watermarkMcpService.getServiceStatus());
    }

    @PostMapping("/start")
    public ApiResponse<WatermarkMcpServiceStatusResponse> startService() {
        return ApiResponse.success(watermarkMcpService.startService());
    }

    @PostMapping("/stop")
    public ApiResponse<WatermarkMcpServiceStatusResponse> stopService() {
        return ApiResponse.success(watermarkMcpService.stopService());
    }

    @GetMapping("/tools")
    public ApiResponse<List<Map<String, Object>>> listTools() {
        return ApiResponse.success(watermarkMcpService.listTools());
    }

    @PostMapping("/call")
    public ApiResponse<Map<String, Object>> callTool(@RequestBody WatermarkMcpToolCallRequest request) {
        return ApiResponse.success(watermarkMcpService.callTool(request.getToolName(), request.getArguments()));
    }

    @PostMapping("/embed")
    public ApiResponse<Map<String, Object>> embed(@RequestBody WatermarkEmbedRequest request) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("inputPaths", request.getInputPaths());
        arguments.put("inputDir", request.getInputDir());
        arguments.put("outputDir", request.getOutputDir());
        arguments.put("limit", request.getLimit());
        return ApiResponse.success(watermarkMcpService.embedImages(arguments));
    }
}
