# XHS Agent 归档说明

这部分代码已从 `Workspace` 主入口下线，但代码与链路保留，后续仍可回看其技术栈与实现思路。

## 前端文件

- `backend/src/main/resources/static/xhs-agent.html`
- `backend/src/main/resources/static/xhs-agent.css`
- `backend/src/main/resources/static/xhs-agent.js`

## 后端入口

- `backend/src/main/java/com/dora/jagent/controller/XhsAgentController.java`
- `backend/src/main/java/com/dora/jagent/xhs/controller/XhsPublishController.java`

## 主要服务

- `backend/src/main/java/com/dora/jagent/service/impl/XhsStorageServiceImpl.java`
- `backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsGenerationWorkflowServiceImpl.java`
- `backend/src/main/java/com/dora/jagent/service/impl/NotionSyncServiceImpl.java`

## MCP 与运行数据

- `backend/scripts/xhs_mcp_bridge.py`
- `backend/data/xhs/xhs_state.json`

## 文档笔记

- `docs/xhs/00_XHS_阶段总结.md`
- `docs/xhs/01_XHS_当前进度.md`
- `docs/xhs/02_XHS_抓取与登录态.md`
- `docs/xhs/03_XHS_生成链路与目录整理.md`
- `docs/xhs/04_XHS_Notion链路笔记.md`
- `docs/xhs/05_XHS_发布服务笔记.md`

## 当前结论

- 小红书链路适合作为 `MCP + Playwright + Notion 同步` 的专项案例保留。
- 主页入口已替换为 `Research Agent`，不再作为当前默认工作流。
- Notion 同步逻辑仍可复用到后续 `Markdown -> Notion` 笔记同步方案中。
