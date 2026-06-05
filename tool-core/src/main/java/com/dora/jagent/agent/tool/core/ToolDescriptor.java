package com.dora.jagent.agent.tool.core;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ToolDescriptor {

    private String name;

    private String description;

    private Map<String, Object> inputSchema;

    public static Map<String, Object> singleStringInputSchema(String fieldName, String description) {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        fieldName, Map.of(
                                "type", "string",
                                "description", description
                        )
                ),
                "required", List.of(fieldName)
        );
    }
}
