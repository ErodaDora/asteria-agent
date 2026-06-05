# Workspace LoL Esports 功能实现总结

## 1. 这次功能的思维流程

这次英雄联盟赛事功能也是按“两步走”落的：先把真实赛事信息稳定展示到 `workspace.html`，再在真实信息之上接入 LLM 写一句精炼短评。这样做是为了先把“今天到底打了什么、比分是什么、展示哪几场”这些事实层问题解决掉，再让模型只负责表达层。整个功能从一开始就被定义成 workspace 看板，而不是对话或 agent chat，所以设计上始终坚持：数据获取必须先于 LLM，模型只能基于真实输入写短评。


## 2. 最终整体链路

现在这条功能的完整后端链路是：

1. 前端点击 `workspace` 里的“今日赛事”按钮
2. 请求后端接口：`GET /api/workspace/lol-esports/today`
3. `WorkspaceController` 调 `WorkspaceService`
4. `WorkspaceServiceImpl` 先调 `LolEsportsService`
5. `LolEsportsServiceImpl` 从 LoL Esports 官方接口拉取今日赛程
6. 后端先筛出：
   - LCK
   - 官方国际赛事（MSI / Worlds / First Stand）
7. 再根据比赛日状态决定“今天显示什么”：
   - 如果今天有比赛正在打，就展示今天全部相关比赛
   - 如果今天两场都没开打，就回退展示昨天已结束的比赛
   - 如果今天比赛都打完了，就展示今天的比赛
8. 拿到最终要展示的比赛列表后，`WorkspaceServiceImpl` 再调 `LolEsportsRecapService`
9. `LolEsportsRecapServiceImpl` 会先尝试补充虎扑赛后帖，再把比赛事实和帖子摘要一起交给 LLM，只生成一句短评
10. 后端把比赛结构 + 短评一起打包成 `WorkspaceLolEsportsResponse`
11. 前端把每场比赛和对应短评渲染到 workspace 卡片上


## 3. 为什么先做基础实现，再接 LLM

这次没有一上来就上 RAG，也没有先碰向量库。原因很直接：这是实时信息，数据量不大，可以现抓，当前真正难的是“不要编造”，而不是“检索不到”。所以第一阶段最合理的方案就是官方赛程接口 + 后端过滤 + 前端展示；等事实链路稳定，再让 LLM 做语言层加工。


## 4. LLM 在这次功能里承担什么职责

LLM 在这里是一个很受限的表达层，不是数据源，也不是裁判。它只负责基于后端已经拿到的真实比赛事实写一句中文短评；它不负责查询比赛、不负责判断赛区、不负责编造选手名、POM、KDA 或团战细节。这条边界非常关键，因为一旦让模型越过它，整套看板就会迅速从“真实赛事摘要”滑向“看起来像懂比赛的幻觉文本”。


## 5. 当前版本能做到什么，不能做到什么

### 当前已经做到的

- 拿到 LoL Esports 官方赛程
- 按今天过滤比赛
- 按赛区过滤出 LCK / 国际赛事
- 展示比赛时间、对阵、比分、状态、Bo 信息
- 在每场比赛下增加一句 LLM 生成的短评
- 会尝试抓取虎扑英雄联盟版块最近的 `[赛后]` 战报帖，补充标题、正文摘要和亮评
- 已经支持“按比赛日状态切换展示今天或昨天的比赛”

### 当前还没做到的

- 玩家级信息分析
- POM 展示
- KDA 分析
- “某位选手送了”这种有根据的点名吐槽

原因不是前端不行，而是：

**当前接入的官方赛程数据源本身没有提供足够稳定的选手级事实。**

所以如果现在强行让 LLM 去写：

- `JackeyLove 狂送`
- `Chovy 拿下 POM`

那就有很大概率变成编造。

因此当前版本把 LLM 约束在：先基于官方赛程事实出一句短评；如果能拿到虎扑赛后帖，就再参考帖子正文和亮评；但仍然不允许跳到选手级硬结论。


## 6. 这次新增和改动的代码文件

### 6.1 新增：赛事查询服务接口

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/LolEsportsService.java`

职责：

- 对外定义“获取今日重点比赛”这件事

主要方法：

- `getTodayKeyMatches()`


### 6.2 新增：赛事查询服务实现

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/LolEsportsServiceImpl.java`

职责：

- 请求 LoL Esports 官方赛程接口
- 从返回中抽取 match 事件
- 只保留目标赛区
- 只保留今天的比赛
- 把结果整理成后端内部结构

核心函数：

- `getTodayKeyMatches()`
  - 主入口
- `fetchSchedule()`
  - 请求官方接口
- `selectDisplayMatches(...)`
  - 按“正在打 / 未开打 / 已打完”决定最终展示哪天的比赛
- `isTargetLeague(...)`
  - 判断是不是我们要的赛区
- `parseStartTime(...)`
  - 解析比赛时间

这一层只负责“拿真数据”，不负责文案生成。


### 6.3 新增：赛事内部快照模型

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/support/LolEsportsMatchSnapshot.java`

职责：

- 承接官方赛事数据
- 统一在后端内部流转

它存的主要信息有：

- `eventId`
- `leagueName`
- `blockName`
- `state`
- `startTime`
- `team1Name / team1Code / team1Wins`
- `team2Name / team2Code / team2Wins`
- `bestOf`

这是“真实比赛事实”的基础结构。


### 6.4 新增：虎扑赛后帖补充服务

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/HupuLolPostService.java`
- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/HupuLolPostServiceImpl.java`
- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/support/HupuLolPostSnapshot.java`

职责：

- 在虎扑英雄联盟版块最近帖子中查找和当前比赛匹配的帖子
- 优先匹配 `[赛后]` 相关标题
- 抓取帖子标题
- 抓取战报正文摘要
- 尝试抽取亮评 / 评论摘要

关键函数：

- `findMatchPost(...)`
- `fetchCandidates(...)`
- `fetchPost(...)`
- `extractArticleBody(...)`
- `extractTopComments(...)`

这一层的设计思想是：

- **官方赛程保事实**
- **虎扑战报帖补内容**
- **虎扑亮评补社区情绪**

另外这里还加了一条很小但很必要的短路：

- 如果比赛还没开打（`unstarted`）
- 就不去虎扑找赛后帖

因为这时去找 `[赛后]` 帖很容易误匹配旧内容。


### 6.5 新增：LLM 赛事短评服务接口

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/LolEsportsRecapService.java`

职责：

- 定义“给比赛生成一句短评”这件事

主要方法：

- `generateRecaps(List<LolEsportsMatchSnapshot> matches)`


### 6.6 新增：LLM 赛事短评服务实现

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/LolEsportsRecapServiceImpl.java`

职责：

- 先拿真实比赛信息
- 再尝试补充虎扑赛后帖内容
- 把“官方事实 + 虎扑帖子摘要 + 亮评”一起批量交给模型
- 让模型生成每场比赛的一句话短评
- 限制模型不能编造选手与局内细节
- 解析 JSON 返回
- 如果模型失败或内容不合规，就退回到后端兜底文案

这层是这次 LLM 接入的核心。

关键函数：

- `generateRecaps(...)`
  - 整个短评生成入口
- `buildSystemPrompt()`
  - 给模型下规则
- `buildUserPrompt(...)`
  - 组装比赛事实，并在可能时补入虎扑帖子数据
- `sanitizeRecap(...)`
  - 清洗模型输出
- `fallbackRecap(...)`
  - 兜底生成一条不会胡编的短评

这层的设计思想很明确：

- **模型只写文案**
- **模型不能发明事实**
- **一旦不稳，后端立即兜底**


## 7. 这层 LLM 提示词在控制什么

这次短评提示词不是开放式写作，而是强约束提示词。

主要约束包括：

1. 只能基于提供的数据写
2. 不许编造选手名、POM、KDA、团战细节
3. 如果提供了虎扑帖子和亮评，可以把它们当作社区观感参考
4. 每场只写一句
5. 尽量短
6. 可以带 emoji
7. 最终只输出 JSON

这一步的目的不是“让它更聪明”，而是“让它更老实”。


## 8. 为什么还需要 sanitize 和 fallback

即使提示词写得很严，也不能把安全性完全交给模型。

所以这次后端还做了两层保险：

### 第一层：输出清洗

在 `sanitizeRecap(...)` 里做：

- 去换行
- 限制长度
- 过滤掉明显不该出现的编造成分

### 第二层：兜底文案

如果模型：

- 返回空
- JSON 解析失败
- 输出越界
- 写了不该写的内容

就直接回退到后端规则文案，例如：

- `T1 2:0 NS 打完收工，比分已经摆上来了 🔥`
- `DK 对 DNS 还在激战，先别走开 👀`
- `GEN 对 T1 今晚开打，先把闹钟定上 ⏰`

这样即使模型表现不好，页面也不会崩，也不会胡说八道。


## 9. workspace 服务层这次如何被扩展

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/WorkspaceService.java`
- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/WorkspaceServiceImpl.java`

这次扩展了一个新的方法：

- `getTodayLolEsportsCard()`

它负责把两层能力串起来：

1. 先调 `LolEsportsService` 拿到“最终该展示的比赛快照”
2. 再调 `LolEsportsRecapService` 生成短评
3. 根据最终展示的是今天还是昨天，设置标题和副标题
4. 最后映射成前端使用的 response 结构

关键函数：

- `getTodayLolEsportsCard()`
- `toMatchView(...)`
- `buildScoreLine(...)`
- `toMatchStatus(...)`
- `buildLolSubtitle(...)`

这一层的角色是：

**把“真实赛事数据”和“短评文案”合并成最终页面结构。**


## 10. workspace 接口层如何接入

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/controller/WorkspaceController.java`

这次新增接口：

- `GET /api/workspace/lol-esports/today`

它的职责很简单：

- 校验登录态（沿用原有拦截器）
- 调用 `WorkspaceService`
- 返回统一响应


## 11. 前端展示结构怎么接的

### 11.1 页面结构

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/workspace.html`

这次新增了：

- “今日赛事”按钮
- LoL Esports 专属展示面板


### 11.2 前端请求与渲染

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/workspace.js`

新增逻辑：

- `loadTodayLolEsportsCard()`
  - 请求后端接口
- `renderLolPanel(report)`
  - 把比赛列表渲染成卡片

现在每场比赛的渲染信息包括：

- 联赛名
- 阶段
- 开赛时间
- 对阵
- 比分
- 状态
- 一句话短评


### 11.3 样式

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/resources/static/workspace.css`

这次补了：

- `lol-panel`
- `lol-match-list`
- `lol-match-row`
- `lol-match-recap`

其中 `lol-match-recap` 就是战报短评展示位。


## 12. 这次实现最关键的工程判断

### 判断一：实时赛事功能不应直接靠模型“知道今天发生了什么”

必须先有真实赛事源。


### 判断二：LLM 应该只做表达层，而不是事实层

事实层属于：

- 官方赛程
- 虎扑赛后帖正文
- 虎扑亮评摘要
- 可信数据源
- 后端结构化字段

表达层才属于 LLM。


### 判断三：当前版本宁可少说，也不要编

因为这版还没接到稳定的选手级详情数据。

所以当前最正确的选择不是“放开让它吐槽”，而是：

- 先基于比分和赛程写短评
- 如果能拿到虎扑赛后帖，就补一点真实社区语感
- 暂时不碰选手级锐评


## 13. 后续最自然的下一步

如果后面要把这套功能继续升级，最自然的方向是：

1. 继续稳定虎扑帖子映射  
   目标：让更多当天比赛都能命中正确战报帖

2. 增加比赛详情源  
   目标：拿到更细的 game / player / MVP 信息

3. 再扩大战报分析权限  
   目标：允许写：
   - 谁发挥亮眼
   - 谁被打爆
   - 哪场是强强对抗

4. 再考虑把分析模板抽成 skill  
   目标：
   - 固定语气
   - 节省 prompt token
   - 固定输出风格

也就是说，这次实现已经把最重要的骨架搭好了：

- 真数据链路
- workspace 卡片
- LLM 文案层
- 后端兜底

后面再往“更懂比赛”的方向加细节，会顺很多。
