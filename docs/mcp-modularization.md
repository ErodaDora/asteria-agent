# JAgent MCP 模块化说明

## 当前拆分

- `tool-core`
  - 放共享的 tool 抽象、运行时注册、MCP DTO 和可独立运行的本地文件类工具。
- `backend`
  - 继续承接 JAgent 业务后端，保留现有聊天、知识库、鉴权和依赖业务 service 的工具。
- `mcp-server`
  - 独立的 MCP 风格服务入口，当前直接复用 `tool-core` 中的共享工具。

## 这次为什么先这样拆

- 先把真正通用的部分抽成模块，避免以后再做 MCP 时重复复制 `AgentTool`、注册表、协议 DTO。
- 先让 `mcp-server` 跑通一批无业务依赖的工具，降低独立部署门槛。
- 暂时保留 `backend` 里的业务型工具，避免一次性把 `RagService`、天气、IP 定位这类依赖链全搬走。

## 下一步建议

1. 新建 `tool-app` 或 `tool-biz` 模块，承接依赖 `RagService`、`WeatherQueryService`、`IpLocationService` 的工具实现。
2. 把 `backend` 里的 `/api/mcp` controller 逐步收缩为兼容入口，主入口迁移到 `mcp-server`。
3. 再把当前 HTTP 风格接口升级成标准 MCP transport，例如 stdio 或 streamable HTTP。

## 运行方式

- 后端业务：在 `backend` 模块下启动原 `JAgentApplication`
- 独立工具服务：在 `mcp-server` 模块下启动 `JAgentMcpServerApplication`

两个应用都支持通过 `JAGENT_TOOL_WORKSPACE_ROOT` 指定工具允许访问的工作区根目录。
