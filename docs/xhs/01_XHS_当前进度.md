# XHS 当前进度

## 1. MCP 第一阶段

- 已将 `JAgent` 现有工具体系补成“可协议化”的第一阶段形态。
- 保留了原有 `execute(String input)`，没有打断现有 agent loop。
- 新增了统一工具外壳和 descriptor。

核心文件：

- [AgentTool.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/agent/tool/core/AgentTool.java)
  - `getDescriptor()`
  - `execute(ToolInvocationRequest request)`
- [ToolDescriptor.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/agent/tool/core/ToolDescriptor.java)
- [ToolInvocationRequest.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/agent/tool/model/ToolInvocationRequest.java)
- [JAgentMcpToolService.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/mcp/service/JAgentMcpToolService.java)
  - `listTools()`
  - `callTool(...)`
- [JAgentMcpController.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/controller/JAgentMcpController.java)

## 2. 小红书智能体入口页

- 已在 workspace 中新增独立的 `XHS Agent` 入口。
- 已新增独立页面，前端风格对齐小红书项目的四步式布局。

核心文件：

- [workspace.html](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/workspace.html)
- [workspace.js](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/workspace.js)
  - `openXhsAgent()`
- [xhs-agent.html](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.html)
- [xhs-agent.css](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.css)
- [xhs-agent.js](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.js)
  - `startCrawl()`
  - `loadLatest()`
  - `syncToNotion()`

## 3. 小红书后端骨架

- 已建立 `/api/xhs/*` 路由骨架。
- 已打通“采集请求 -> 文本处理 -> 结果暂存 -> 最近结果查询”链路。
- 当前真实 Playwright 抓取尚未接入，状态会返回 `pending_playwright`。

核心文件：

- [XhsAgentController.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/controller/XhsAgentController.java)
  - `/api/xhs/crawl/search`
  - `/api/xhs/crawl/latest`
  - `/api/xhs/storage/notion/sync`
- [XhsCrawlService.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/XhsCrawlService.java)
- [XhsCrawlServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsCrawlServiceImpl.java)
  - `crawl(...)`
  - `getLatest(...)`
- [XhsTextProcessingServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsTextProcessingServiceImpl.java)
  - `normalize(...)`
- [XhsStorageServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsStorageServiceImpl.java)
  - `saveLatest(...)`
  - `getLatest(...)`
  - `syncLatestToNotion(...)`

## 4. Notion 真实接入

- 已接入 Notion API。
- 当前点击“同步到 Notion”会真实访问数据库。
- 即使还没有采集结果，也会先验证 token / database 权限是否可用。

核心文件：

- [NotionSyncService.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/NotionSyncService.java)
- [NotionSyncServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/NotionSyncServiceImpl.java)
  - `syncCrawlResult(...)`
  - `buildCreatePagePayload(...)`
  - `resolveTitlePropertyName(...)`
- [application.yaml](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/application.yaml)
  - `notion.token`
  - `notion.database-id`

## 5. 当前未完成

- 真实 Playwright 小红书抓取
- 登录态处理
- 搜索页卡片抓取
- 详情页补全
- 采集结果真实写入后再进入 AI 生成链路
