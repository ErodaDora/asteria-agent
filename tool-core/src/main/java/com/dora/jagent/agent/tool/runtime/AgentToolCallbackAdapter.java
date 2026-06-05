package com.dora.jagent.agent.tool.runtime;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.core.ToolExecutionResult;
import com.dora.jagent.agent.tool.model.AgentToolCallInput;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

public final class AgentToolCallbackAdapter {

    private AgentToolCallbackAdapter() {
    }

    public static ToolCallback adapt(AgentTool tool) {
        return FunctionToolCallback.<AgentToolCallInput, ToolExecutionResult>builder(
                        tool.getName(),
                        input -> tool.execute(input == null ? null : input.getInput())
                )
                .description(tool.getDescription() + " 输入参数使用 JSON：{\"input\":\"...\"}")
                .inputType(AgentToolCallInput.class)
                .build();
    }
}
