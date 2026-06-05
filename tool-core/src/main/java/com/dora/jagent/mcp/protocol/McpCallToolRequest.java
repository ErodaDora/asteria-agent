package com.dora.jagent.mcp.protocol;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class McpCallToolRequest {

    private String name;

    private Map<String, Object> arguments = new HashMap<>();

    private String rawInput;
}
