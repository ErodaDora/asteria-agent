package com.dora.jagent.watermark.service;

import com.dora.jagent.watermark.model.WatermarkMcpServiceStatusResponse;

import java.util.List;
import java.util.Map;

public interface WatermarkMcpService {

    WatermarkMcpServiceStatusResponse getServiceStatus();

    WatermarkMcpServiceStatusResponse startService();

    WatermarkMcpServiceStatusResponse stopService();

    List<Map<String, Object>> listTools();

    Map<String, Object> searchImages(Map<String, Object> arguments);

    Map<String, Object> embedImages(Map<String, Object> arguments);

    Map<String, Object> callTool(String toolName, Map<String, Object> arguments);
}
