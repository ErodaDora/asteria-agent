# XHS 生成链路与目录整理

## 这次新增了什么

- 基于 `Spring AI` 接通了小红书的分析、选题生成、文案生成、生成结果查看。
- 前端新增了 `LLM` 选择，默认 `deepseek-chat`。
- 加了规则：
  - 当前模型是 `deepseek-chat` 时，跳过自动生图 service，只保留配图建议文本。

## 代码目录怎么读

### 1. 抓取与同步主链路

- 控制器：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/controller/XhsAgentController.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/controller/XhsAgentController.java)
- 采集 / 登录态 / Notion：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsCrawlServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsCrawlServiceImpl.java)
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsBrowserSessionServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsBrowserSessionServiceImpl.java)
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsPlaywrightCrawlerServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsPlaywrightCrawlerServiceImpl.java)
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsStorageServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsStorageServiceImpl.java)
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/NotionSyncServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/NotionSyncServiceImpl.java)

### 2. 新整理出的生成模块

- 统一放到 `com.dora.jagent.xhs` 下：
  - `xhs/controller`
  - `xhs/model`
  - `xhs/service`
  - `xhs/service/impl`

### 3. 生成链路入口

- 控制器：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/controller/XhsGenerationController.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/controller/XhsGenerationController.java)
- 总工作流：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsGenerationWorkflowServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsGenerationWorkflowServiceImpl.java)

## 生成模块各自负责什么

- 规则分析：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsAnalysisServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsAnalysisServiceImpl.java)
- 选题生成：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsTopicGenerationServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsTopicGenerationServiceImpl.java)
- 文案生成：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsContentGenerationServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsContentGenerationServiceImpl.java)
- 生图策略判断：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsImageGenerationServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsImageGenerationServiceImpl.java)

## Prompt 模板

- [/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/prompts/xhs/topic-generation-prompt.txt](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/prompts/xhs/topic-generation-prompt.txt)
- [/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/prompts/xhs/content-generation-prompt.txt](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/prompts/xhs/content-generation-prompt.txt)

## 前端入口

- 页面：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.html](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.html)
- 交互：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.js](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.js)
- 样式：
  - [/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.css](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.css)

## 当前整理策略

- 已跑通的抓取链路先不大搬家，避免把 Playwright / Notion 现有功能搞坏。
- 新增的“分析与生成”统一进入 `xhs` 模块。
- 下一轮如果你愿意，可以继续把现有 `XhsCrawl* / XhsBrowser* / XhsStorage*` 也整体迁到 `com.dora.jagent.xhs` 下，做第二次目录收口。
