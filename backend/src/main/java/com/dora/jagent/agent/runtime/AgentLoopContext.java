package com.dora.jagent.agent.runtime;

import com.dora.jagent.agent.tool.core.AgentTool;
import com.dora.jagent.agent.tool.runtime.AgentToolCallbackAdapter;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

@Getter
public class AgentLoopContext {

    private final List<AgentTool> availableTools;

    private final ToolCallingManager toolCallingManager;

    private final List<ToolCallback> availableToolCallbacks;

    private final ToolCallingChatOptions chatOptions;

    @Setter
    private ChatResponse lastChatResponse;

    public AgentLoopContext(List<AgentTool> availableTools) {
        this.availableTools = availableTools == null ? List.of() : List.copyOf(availableTools);
        this.availableToolCallbacks = this.availableTools.stream()
                .map(AgentToolCallbackAdapter::adapt)
                .toList();
        this.toolCallingManager = ToolCallingManager.builder().build();
        this.chatOptions = DefaultToolCallingChatOptions.builder()
                .toolCallbacks(this.availableToolCallbacks)
                .internalToolExecutionEnabled(false)
                .build();
    }
}
