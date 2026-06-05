# XHS Notion 链路笔记

## 当前覆盖范围

- 采集结果同步到 Notion
- 生成结果同步到 Notion
- 首图链接、正文、高赞评论、标签等字段映射

## 核心入口

- 采集结果同步入口：
  [XhsAgentController.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/controller/XhsAgentController.java)
  - `POST /api/xhs/storage/notion/sync`
- 生成结果同步入口：
  [XhsPublishController.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/controller/XhsPublishController.java)
  - `POST /api/xhs/generated/notion/sync`

## 写入链路

### 1. 采集结果

- 前端点击 `同步到 Notion`
- 后端进入：
  [XhsStorageServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsStorageServiceImpl.java)
  - `syncLatestToNotion(...)`
- 再调用：
  [NotionSyncServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/NotionSyncServiceImpl.java)
  - `syncCrawlResult(...)`

### 2. 生成结果

- 前端点击 `生成结果同步到 Notion`
- 后端进入：
  [XhsGenerationWorkflowServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsGenerationWorkflowServiceImpl.java)
  - `syncLatestToNotion(...)`
- 再调用：
  [NotionSyncServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/NotionSyncServiceImpl.java)
  - `syncGeneratedResult(...)`

## 读库思路

当前没有做 “从 Notion 反查回 JAgent” 的业务读链路。  
现在的 `读` 只发生在同步前：

- 先请求 Notion database schema
- 识别数据库里现有的字段和类型
- 只对存在的字段做赋值

核心函数：

```java
private JsonNode fetchDatabase()
private String resolveTitlePropertyName(JsonNode propertiesNode)
private void putIfPresent(Map<String, Object> properties, JsonNode propertiesNode, String propertyName, Object value)
```

这样做的作用是：

- 不强依赖数据库列必须全有
- 没有的列自动跳过
- 降低字段变更时的报错概率

## 采集结果字段映射

主要在：
`buildCreatePagePayload(...)`

当前重点字段：

- `标题` <- 笔记标题
- `正文` <- 正文 + 高赞评论
- `作者` <- 原作者
- `链接` <- 原帖链接
- `首图链接` <- 抓到的第一张图
- `点赞数 / 评论数 / 收藏数`
- `发布时间`
- `关键词`
- `内容类型`
- `标签`

## 生成结果字段映射

主要在：
`buildGeneratedPagePayload(...)`

当前重点字段：

- `标题` <- 生成文案标题
- `正文` <- 选题 + 选题理由 + 文案正文 + CTA + 标签 + 配图建议 + 分析摘要
- `作者` <- 固定写 `AI 生成`
- `首图链接` <- 最近一次采集结果里的第一张可用封面图
- `关键词` <- 生成选题标题
- `内容类型`
- `标签`

## 正文拼接核心

采集结果正文：

```java
private String buildNotionBody(XhsNoteItemView item)
```

生成结果正文：

```java
private String buildGeneratedBody(
    XhsGeneratedTopicWithContents group,
    XhsContentItem content,
    XhsGenerateResponse generatedResponse
)
```

## 连接层实现

当前 Notion 客户端在：
[NotionSyncServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/NotionSyncServiceImpl.java)

关键点：

- `RestClient`
- `SimpleClientHttpRequestFactory`
- 超时配置
- 轻量重试

核心代码段：

```java
SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
requestFactory.setConnectTimeout(10_000);
requestFactory.setReadTimeout(20_000);

this.restClient = RestClient.builder()
        .requestFactory(requestFactory)
        .baseUrl("https://api.notion.com/v1")
        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .defaultHeader("Notion-Version", NOTION_VERSION)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
```

## 当前定调

- `Notion` 现在承担的是外部持久化角色
- `JAgent` 内存里仍保留最近结果，便于前端快速回显
- 真正长期保留靠 Notion，而不是当前进程内存
