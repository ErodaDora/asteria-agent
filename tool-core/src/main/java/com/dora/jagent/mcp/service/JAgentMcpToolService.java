package com.dora.jagent.mcp.service;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.model.ToolInvocationRequest;
import com.dora.jagent.agent.tool.runtime.AgentToolRegistry;
import com.dora.jagent.mcp.protocol.McpCallToolRequest;
import com.dora.jagent.mcp.protocol.McpCallToolResponse;
import com.dora.jagent.mcp.protocol.McpListToolsResponse;
import com.dora.jagent.mcp.protocol.McpToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JAgentMcpToolService {

    private final AgentToolRegistry agentToolRegistry;

    public McpListToolsResponse listTools() {
        return McpListToolsResponse.builder()
                .tools(agentToolRegistry.getAll().stream()
                        .map(this::toToolDefinition)
                        .toList())
                .build();
    }

    public McpCallToolResponse callTool(McpCallToolRequest request) {
        AgentTool tool = agentToolRegistry.getRequired(request.getName());
        var result = tool.execute(ToolInvocationRequest.builder()
                .arguments(request.getArguments())
                .rawInput(request.getRawInput())
                .build());

        return McpCallToolResponse.builder()
                .toolName(result.getToolName())
                .success(result.isSuccess())
                .output(result.getOutput())
                .build();
    }

    private McpToolDefinition toToolDefinition(AgentTool tool) {
        return McpToolDefinition.builder()
                .name(tool.getDescriptor().getName())
                .description(tool.getDescriptor().getDescription())
                .inputSchema(tool.getDescriptor().getInputSchema())
                .build();
    }
}
