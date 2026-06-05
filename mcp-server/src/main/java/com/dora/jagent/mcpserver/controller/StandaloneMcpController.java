package com.dora.jagent.mcpserver.controller;

import com.dora.jagent.mcp.protocol.McpCallToolRequest;
import com.dora.jagent.mcp.protocol.McpCallToolResponse;
import com.dora.jagent.mcp.protocol.McpListToolsResponse;
import com.dora.jagent.mcp.service.JAgentMcpToolService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class StandaloneMcpController {

    private final JAgentMcpToolService jAgentMcpToolService;

    @GetMapping("/tools")
    public McpListToolsResponse listTools() {
        return jAgentMcpToolService.listTools();
    }

    @PostMapping("/tools/call")
    public McpCallToolResponse callTool(@RequestBody McpCallToolRequest request) {
        return jAgentMcpToolService.callTool(request);
    }
}
