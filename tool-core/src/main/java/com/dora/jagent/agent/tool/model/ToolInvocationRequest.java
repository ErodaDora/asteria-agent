package com.dora.jagent.agent.tool.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolInvocationRequest {

    @Builder.Default
    private Map<String, Object> arguments = new HashMap<>();

    private String rawInput;
}
