package com.dora.jagent.mcp.protocol;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class McpCallToolResponse {

    private String toolName;

    private boolean success;

    private String output;
}
