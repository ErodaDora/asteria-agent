package com.dora.jagent.xhs.model;

import lombok.Data;

import java.util.Map;

@Data
public class XhsMcpToolCallRequest {

    private String toolName;

    private Map<String, Object> arguments;
}
