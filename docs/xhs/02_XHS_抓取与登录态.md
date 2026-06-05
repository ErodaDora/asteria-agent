# XHS 抓取与登录态

## 1. 本次新增

- 已接入 Java Playwright 依赖。
- 已拆出“小红书登录态服务”和“Playwright 抓取服务”。
- `XhsCrawlServiceImpl` 现在只负责总流程编排。
- 前端页面已新增：
  - 启动登录态
  - 检查登录态
  - 开始采集

核心文件：

- [backend/pom.xml](/Users/dora/Documents/项目/code/JAgent/backend/pom.xml)
- [application.yaml](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/application.yaml)
- [XhsAgentController.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/controller/XhsAgentController.java)
- [XhsBrowserSessionServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsBrowserSessionServiceImpl.java)
- [XhsPlaywrightCrawlerServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsPlaywrightCrawlerServiceImpl.java)
- [XhsCrawlServiceImpl.java](/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/XhsCrawlServiceImpl.java)
- [xhs-agent.js](/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/xhs-agent.js)

## 2. 登录态处理

### `XhsBrowserSessionServiceImpl.startLoginSession()`

登陆状态 True/False 定义类不是 bool 	是 AtomicBoolean 线程安全版本的bool ， 两个用户同时触发可能会各自开两个页面

get()                 // 获取当前值
set(true)             // 设置值
compareAndSet(a, b)   // 如果当前是 a，就改成 b

loginThread.setDaemon(true); 守护线程-主程序退出，本线程不会阻止JVM关闭。

​			loginThread.start(); 启动线程

功能：
- 启动一个有头浏览器窗口，给用户手动登录小红书。
- 等待配置时长后保存登录态到本地。

输入：
- 无

输出：
- `XhsLoginStatusResponse`
  - `loggedIn`
  - `loginWindowRunning`
  - `storageStatePath`
  - `message`

### `XhsBrowserSessionServiceImpl.getLoginStatus()`

功能：
- 查询当前本地登录态文件是否存在，以及登录窗口是否仍在运行。

输入：
- 无

输出：
- `XhsLoginStatusResponse`

### `XhsBrowserSessionServiceImpl.openSession(boolean headless)`

功能：
- 创建一个 Playwright 会话。
- 如果本地已存在登录态，则加载该登录态。

输入：
- `headless`：是否无头运行

输出：
- `XhsBrowserSession`
  - `playwright`
  - `browser`
  - `context`
  - `page`

## 3. 抓取服务

### `XhsPlaywrightCrawlerServiceImpl.crawl(XhsCrawlRequest request)`

功能：
- 使用已保存的小红书登录态执行真实抓取。
- 关键词搜索 -> 搜索卡片 -> 详情页补全 -> 过滤 -> 返回结构化笔记列表。

输入：
- `XhsCrawlRequest`
  - `keywords`
  - `topicWords`
  - `minComments`
  - `minLikes`
  - `minFavorites`
  - `targetCount`

输出：
- `List<XhsNoteItemView>`

### `collectCardLinks(Page page, String keyword)`

功能：
- 进入小红书搜索页
- 滚动页面
- 提取候选卡片链接、作者、时间

输入：
- `page`
- `keyword`

输出：
- `List<SearchCard>`

### `fetchDetail(Page page, SearchCard card, List<String> topicWords)`

功能：
- 进入单条笔记详情页
- 提取标题、正文、标签、互动数据、发布时间

输入：
- `page`
- `card`
- `topicWords`

输出：
- `XhsNoteItemView`

### `isValid(XhsNoteItemView note, XhsCrawlRequest request, List<String> topicWords)`

功能：
- 对抓取结果做过滤：
  - 跳过广告
  - 按评论/点赞/收藏阈值过滤
  - 按话题词过滤

输入：
- `note`
- `request`
- `topicWords`

输出：
- `boolean`

## 4. 编排层

### `XhsCrawlServiceImpl.crawl(String userId, XhsCrawlRequest request)`

功能：
- 检查关键词
- 检查是否已有登录态
- 调 Playwright 抓取
- 调文本清洗
- 调暂存
- 返回本次采集结果

输入：
- `userId`
- `XhsCrawlRequest`

输出：
- `XhsCrawlResponse`

### `XhsCrawlServiceImpl.getLatest(String userId)`

功能：
- 返回当前用户最近一次采集结果

输入：
- `userId`

输出：
- `XhsCrawlResponse`

## 5. 对外接口

### `POST /api/xhs/browser/login/start`

功能：
- 启动登录窗口

返回：
- `XhsLoginStatusResponse`

### `GET /api/xhs/browser/login/status`

功能：
- 查询登录状态

返回：
- `XhsLoginStatusResponse`

### `POST /api/xhs/crawl/search`

功能：
- 发起真实采集

输入：
- `XhsCrawlRequest`

返回：
- `XhsCrawlResponse`

## 6. 当前注意点

- 编译已通过。
- 真实运行依赖本机可启动 Playwright 浏览器。
- 首次抓取前，需要先点击“启动登录态”并手动完成一次登录。
