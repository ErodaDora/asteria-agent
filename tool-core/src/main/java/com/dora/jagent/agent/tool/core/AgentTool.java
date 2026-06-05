package com.dora.jagent.agent.tool.core;

import com.dora.jagent.agent.tool.model.ToolInvocationRequest;
import org.springframework.util.StringUtils;

/**
 * =================== Tool 目录结构说明 ===================
 * 当前 JAgent 的工具相关代码，按职责拆成 4 个子目录：
 *
 * 1. core
 *    - 放工具体系最核心的抽象与通用结果对象
 *    - 例如 AgentTool、ToolExecutionResult
 *
 * 2. runtime
 *    - 放运行时装配逻辑
 *    - 例如工具注册表、Spring AI ToolCallback 适配器
 *
 * 3. model
 *    - 放工具调用时使用的输入/输出模型
 *    - 例如给 Spring AI 原生 tool calling 使用的 AgentToolCallInput
 *
 * 4. impl
 *    - 放具体工具实现
 *    - 例如读取文件、搜索文件等真正可执行的工具
 *
 * 这样拆分之后，后续新增工具时，通常只需要在 impl 目录下增加一个实现类；
 * 而抽象协议、运行时适配、输入模型这些公共代码不会和具体工具混在一起。
 * =======================================================
 */
public interface AgentTool {

    String getName();

    String getDescription();

    default ToolDescriptor getDescriptor() {
        return ToolDescriptor.builder()
                .name(getName())
                .description(getDescription())
                .inputSchema(ToolDescriptor.singleStringInputSchema("input", "工具输入字符串"))
                .build();
    }

    ToolExecutionResult execute(String input);

    default ToolExecutionResult execute(ToolInvocationRequest request) {
        if (request == null) {
            return execute((String) null);
        }

        Object input = request.getArguments().get("input");
        if (input instanceof String text && StringUtils.hasText(text)) {
            return execute(text);
        }

        if (StringUtils.hasText(request.getRawInput())) {
            return execute(request.getRawInput());
        }

        return execute((String) null);
    }
}
