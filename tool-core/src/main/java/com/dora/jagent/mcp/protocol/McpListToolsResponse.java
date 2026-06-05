package com.dora.jagent.mcp.protocol;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class McpListToolsResponse {

    private List<McpToolDefinition> tools;
}
