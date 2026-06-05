# JAgent MCP 第二阶段说明：水印智能体

## 这次要解决什么

这一阶段的目标不是继续把所有 tool 留在 `backend` 进程里直接调用，而是把一部分能力做成“可独立启动的外部 MCP 服务”，再由 `JAgent` 去启动和调用它。这样做的直接收益有三点：

- 业务后端和推理进程解耦，模型依赖不必全部塞进 Java 进程。
- Python / PyTorch 这类重依赖能力可以独立演进，和 Java 发布节奏分开。
- 后续如果要像小红书那样打包成独立可执行文件，也有天然边界。

## 现在这套 MCP 底层概念

当前 JAgent 的“第一阶段 MCP”本质上是把本地 tool 系统整理成统一协议，而不是一步到位接完整官方 SDK。核心由三层构成：

1. `AgentTool`
   - 保留原来的 `execute(String input)` 习惯
   - 再补一个统一调用外壳 `execute(ToolInvocationRequest request)`
2. `ToolDescriptor`
   - 描述工具名、用途和输入 schema
3. `JAgentMcpToolService`
   - 负责列出工具、按统一协议调工具

## 两个核心代码片段

### 1. tool 如何保持协议兼容

下面这段来自 [`AgentTool.java`](/Users/dora/Documents/项目/code/JAgent/tool-core/src/main/java/com/dora/jagent/agent/tool/core/AgentTool.java:1)。  
关键点是：

- 老工具仍然可以只实现 `execute(String input)`
- 新协议把请求包成 `ToolInvocationRequest`
- 如果有 `arguments.input` 或 `rawInput`，就回退到旧入口

```java
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
```

### 2. MCP 服务层如何统一分发调用

下面这段来自 [`JAgentMcpToolService.java`](/Users/dora/Documents/项目/code/JAgent/tool-core/src/main/java/com/dora/jagent/mcp/service/JAgentMcpToolService.java:1)。  
关键点是：

- `listTools()` 统一把内部 tool 暴露成外部可读的 descriptor
- `callTool()` 统一把请求转成 `ToolInvocationRequest`
- MCP 层不关心具体业务，只做协议转换和工具分发

```java
public McpListToolsResponse listTools() {
    return McpListToolsResponse.builder()
            .tools(agentToolRegistry.getAll().stream()
                    .map(this::toToolDefinition)
                    .toList())
            .build();
}

public McpCallToolResponse callTool(McpCallToolRequest request) {
    AgentTool tool = agentToolRegistry.getRequired(request.getName());
    var result = tool.execute(ToolInvocationRequest.builder()
            .arguments(request.getArguments())
            .rawInput(request.getRawInput())
            .build());

    return McpCallToolResponse.builder()
            .toolName(result.getToolName())
            .success(result.isSuccess())
            .output(result.getOutput())
            .build();
}
```

## 用两个 tool 看“协议怎么保持住”

### `search_workspace_files`

这个 tool 现在仍然是一个普通 `AgentTool`，但额外补了输入 schema，所以不管是 Agent 调用，还是 MCP `/api/mcp/tools` 暴露出去，它都能被统一识别。

### `embed_watermark_images`

这一阶段新增的水印工具并不直接在 Java 里跑推理，而是把调用转给独立的 Python MCP 服务。  
也就是说：

- Java 侧 `AgentTool` 负责让 Agent 知道“有这个工具”
- 外部 Python MCP 服务负责真正做图像搜索和权重推理

这样协议保持一致，但运行时职责拆开了。

## 这次遇到的 Maven 问题是什么

改成多模块以后，`backend` 不再是一个完全自足的单模块，它依赖了新的 `tool-core`：

- 父工程：[`pom.xml`](/Users/dora/Documents/项目/code/JAgent/pom.xml:1)
- 子模块：`tool-core`、`backend`、`mcp-server`

所以如果你直接在 `backend` 目录里跑：

```bash
mvn spring-boot:run
```

Maven 只看到了当前模块，但它找不到 `jagent-tool-core` 这个本地依赖，于是报：

```text
Could not find artifact com.dora:jagent-tool-core:jar:0.0.1-SNAPSHOT
```

## `mvn -pl tool-core -am install -DskipTests` 到底在做什么

这条命令的含义可以拆开看：

- `-pl tool-core`
  - 只指定处理 `tool-core` 这个模块
- `-am`
  - 如果它还依赖别的上游模块，一并带上
- `install`
  - 先编译，再把产出的 jar 安装到本地 Maven 仓库 `~/.m2/repository`
- `-DskipTests`
  - 这次先跳过测试，加快本地启动链路

执行完后，`backend` 再启动时就能从本地 Maven 仓库拿到 `jagent-tool-core`。

它做的事情不是“启动服务”，而是“把共享模块先构建并放到 Maven 能找到的位置”。

## 外部 MCP 服务这一阶段怎么落

这次水印能力改成下面这条链路：

1. Python 仓库 `watermark-mcp`
   - 负责加载 `PyTorch` 模型和权重
2. 外部 MCP 进程
   - 暴露：
     - `GET /api/mcp/tools`
     - `POST /api/mcp/tools/call`
3. JAgent `backend`
   - 负责启动 / 停止这个进程
   - 负责把外部工具再适配给 Agent
4. Agent 会话
   - 通过已有 `/api/agents/{agentId}/chat` 发起调用

## 这次新增的两个外部 tool

- `search_image_files`
  - 在工作区内检索图片文件
- `embed_watermark_images`
  - 载入预训练权重，对输入图片做水印嵌入，并把结果写入指定目录

## 新智能体怎么创建

这次额外补了一份 SQL 种子：

- [`agent_watermark_seed.sql`](/Users/dora/Documents/项目/code/JAgent/sql/agent_watermark_seed.sql:1)

执行后会新增一个固定 ID 的 Watermark Agent，方便直接拿它开 session。
