package com.dora.jagent.mcp.protocol;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class McpToolDefinition {

    private String name;

    private String description;

    private Map<String, Object> inputSchema;
}
