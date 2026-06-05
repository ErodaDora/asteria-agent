# JAgent MCP 第一阶段说明

## 这次做了什么

- 保留原有 `AgentTool.execute(String input)`，不打断现有 agent loop。
- 给 `AgentTool` 补上了统一调用外壳 `ToolInvocationRequest`。
- 给 `AgentTool` 补上了工具描述对象 `ToolDescriptor`。
- 新增了一个 MCP 风格的工具暴露入口：
  - `GET /api/mcp/tools`
  - `POST /api/mcp/tools/call`

## 现在的结构

- `agent/tool/core/AgentTool.java`
  - 仍然是工具主接口
  - 现在新增了：
    - `getDescriptor()`
    - `execute(ToolInvocationRequest request)`
- `agent/tool/core/ToolDescriptor.java`
  - 描述工具名、说明、参数 schema
- `agent/tool/model/ToolInvocationRequest.java`
  - 统一工具调用外壳
  - 当前核心字段：
    - `arguments`
    - `rawInput`
- `mcp/protocol/*`
  - 放 MCP 风格的请求/响应模型
- `mcp/service/JAgentMcpToolService.java`
  - 负责列出工具、调用工具
- `controller/JAgentMcpController.java`
  - 对外暴露 `/api/mcp/*`

## 为什么这样改

- 第一阶段目标不是一次性接入完整外部 MCP SDK。
- 重点是先把当前本地工具系统整理成：
  - 有统一调用请求
  - 有工具 descriptor
  - 有标准化暴露入口
- 这样后面无论：
  - 接真正的 MCP Java SDK
  - 接外部 MCP client
  - 或把小红书能力并进来
  都会更顺。

## 目前兼容策略

- 旧工具不需要立刻全部重写。
- 默认情况下：
  - `ToolInvocationRequest.arguments.input`
  - 或 `rawInput`
  会自动回退到旧的 `execute(String input)`。
- 所以当前工具体系是“新协议 + 旧实现兼容”。

## 当前示例工具

以下工具已经补了更明确的 schema：

- `search_workspace_files`
- `read_local_text_file`
- `search_code_content`

它们现在已经具备：

- 工具名
- 描述
- 输入 schema
- 统一调用入口

## 下一阶段建议

1. 把其余工具逐个补齐精确 schema
2. 给工具参数增加显式 DTO，而不是长期直接取 `Map`
3. 视情况接入真正的 MCP Java SDK
4. 在这个底座上开始接小红书场景工具和工作流
