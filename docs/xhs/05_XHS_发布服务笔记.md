# XHS 发布服务笔记

## 当前功能

- 从“最近一次生成结果”里选一条文案
- 自动取“最近一次采集结果”里的第一张可用封面图
- 下载到本地临时目录
- 复用已保存的小红书登录态
- 打开发布页并自动填充：
  - 标题
  - 正文
  - 图片
- 自动点击发布按钮

## 核心文件

- 发布控制器：
  [XhsPublishController.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/controller/XhsPublishController.java)
- 发布 service：
  [XhsPublishServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/xhs/service/impl/XhsPublishServiceImpl.java)
- 登录态复用：
  [XhsBrowserSessionServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsBrowserSessionServiceImpl.java)

## 入口接口

```text
POST /api/xhs/generated/publish
```

请求体：

```json
{
  "datasetId": "最近一次生成结果的 datasetId",
  "topicIndex": 0,
  "contentIndex": 0
}
```

含义：

- `topicIndex`：发布第几个选题
- `contentIndex`：发布该选题下第几条文案

## 主流程

### 1. 读取最近生成结果

核心函数：

```java
public XhsPublishResponse publishLatest(String userId, XhsPublishRequest request)
```

做的事：

- 读取 `latest generated result`
- 校验 `datasetId`
- 定位要发布的 `topicIndex/contentIndex`

### 2. 取默认配图

当前策略：

- 从最近一次采集结果里
- 找第一条有 `coverImageUrl` 的笔记
- 默认用它的首图

核心代码段：

```java
XhsNoteItemView sourceItem = crawlResponse.getItems().stream()
        .filter(item -> StringUtils.hasText(item.getCoverImageUrl()))
        .findFirst()
        .orElseThrow(() -> new BizException("当前采集结果没有可用首图，暂时无法自动发布"));
```

### 3. 下载图片到本地

核心函数：

```java
private Path downloadCoverImage(String imageUrl)
```

做的事：

- 创建 `./data/xhs/publish`
- 下载远程图片
- 生成本地临时文件
- 后面交给小红书发布页的 `input[type=file]`

### 4. 复用登录态打开发布页

核心代码段：

```java
try (XhsBrowserSession session = xhsBrowserSessionService.openSession(false)) {
    Page page = session.getPage();
    page.navigate(XHS_PUBLISH_URL);
    page.waitForTimeout(4000);
    fillPublishForm(page, content, localImagePath);
    clickPublish(page);
}
```

说明：

- `openSession(false)` 复用之前保存的 `storageState`
- `false` 表示非 headless，方便观察发布过程

### 5. 自动填表

核心函数：

```java
private void fillPublishForm(Page page, XhsContentItem content, Path imagePath)
```

填充内容：

- 图片上传
- 标题
- 正文

当前正文拼接规则：

```java
private String buildPublishBody(XhsContentItem content)
```

结果是：

- `body`
- `cta`
- `hashtags`

按段落拼起来。

### 6. 自动点击发布

核心函数：

```java
private void clickPublish(Page page)
```

当前会查找：

- `发布`
- `立即发布`

按钮，然后点击。

## 当前实现特点

- 优点：
  - 工程内闭环
  - 不依赖外部 MCP 发布服务
  - 和你现有登录态/Playwright 体系统一

- 限制：
  - 依赖小红书前端页面结构，后续可能要随页面变化调整选择器
  - 当前默认只用“最近采集结果里的第一张可用首图”
  - 自动点击发布后，是否真正发出仍以小红书页面实际结果为准

## 前端使用方式

当前在生成结果卡片里，每条文案下都有：

- `发布到小红书`

按钮。  
点击后会把：

- `topicIndex`
- `contentIndex`

发给后端发布 service。

相关前端文件：
[xhs-agent.js](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.js)

## 当前定调

这个 service 的目标不是做“强平台化的发布中台”，而是：

- 先把 JAgent 内部的小红书闭环跑通
- 用最少抽象完成：
  - 选文案
  - 取图
  - 打开发布页
  - 自动填充
  - 点击发布
