package com.dora.jagent.agent.tool.runtime;

import com.dora.jagent.agent.tool.core.AgentTool;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> toolMap;

    public AgentToolRegistry(List<AgentTool> tools) {
        this.toolMap = tools.stream().collect(Collectors.toMap(AgentTool::getName, tool -> tool));
    }

    public AgentTool getRequired(String toolName) {
        AgentTool tool = toolMap.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("tool not found: " + toolName);
        }
        return tool;
    }

    public List<AgentTool> getAll() {
        return List.copyOf(toolMap.values());
    }
}
