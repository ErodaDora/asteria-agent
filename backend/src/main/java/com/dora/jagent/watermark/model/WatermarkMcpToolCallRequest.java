package com.dora.jagent.watermark.model;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class WatermarkMcpToolCallRequest {

    private String toolName;

    private Map<String, Object> arguments = new HashMap<>();
}
