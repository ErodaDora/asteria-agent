package com.dora.jagent.agent.tool.core;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ToolExecutionResult {

    private String toolName;

    private boolean success;

    private String input;

    private String output;
}
