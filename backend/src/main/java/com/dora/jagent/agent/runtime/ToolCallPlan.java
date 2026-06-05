package com.dora.jagent.agent.runtime;

import lombok.Data;

@Data
public class ToolCallPlan {

    private boolean useTool;

    private String toolName;

    private String toolInput;

    private String reason;
}
