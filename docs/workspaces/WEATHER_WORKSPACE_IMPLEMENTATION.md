# Workspace 天气功能后端实现总结

## 1. 需求是怎么一步步收敛出来的

这次天气功能最开始很容易被理解成“点一下按钮，让 Agent 帮我查天气”。但需求继续明确后，目标变成了：不进入对话页、不保留聊天过程、结果直接显示在 `workspace.html`，而且展示形态必须稳定、简洁、卡片化。这样一来，它就不再是聊天功能，而是一个独立的 workspace 快捷能力。

因此实现方向也随之变化：不再让 LLM 参与天气总结，而是改成“后端直接查真实天气数据，再按规则整理成固定字段返回前端”。后面又进一步扩展成“按请求 IP 估算位置，再按坐标查天气”，这样既适合 workspace 直接使用，也保留了后续给 agent 复用的可能。


## 2. 这次后端改动的核心思想

这次实现遵循三条很明确的原则。第一，天气卡片不走 LLM，因为它需要的是稳定、统一、低成本的结构化展示，不是聊天式自然语言。第二，把“查外部天气数据”和“生成页面展示语义”拆成两层，前一层负责拿原始天气结构化数据，后一层负责输出 `headline / trend / rainAdvice / dressAdvice / detail` 这样的固定字段。第三，把“IP 定位”和“天气查询”拆成独立能力，这样 workspace 可以直接用，后续 agent 或其他快捷功能也能复用。


## 3. 最终落下来的后端链路

现在这条天气功能的后端链路是：

1. 前端点击 `workspace` 里的“当前天气”按钮
2. 请求后端接口：`GET /api/workspace/weather/today`
3. Controller 从请求里解析客户端 IP
4. `IpLocationService` 根据 IP 获取地区和坐标
5. `WeatherQueryService` 根据坐标查询今天的天气
6. `WorkspaceService` 把天气数据整理成适合卡片展示的字段
7. 返回 `WorkspaceWeatherResponse`
8. 前端把结果渲染在 workspace 页面里


## 4. 为什么最后不走 LLM

天气卡片如果继续走 LLM，会把链路变成“查数据 -> 再总结 -> 再渲染”，这样文案不稳定、格式难控、成本更高，而且多了一层故障点。这个功能真正需要的是稳定、快速和可预测，所以最终设计改成：原始天气数据来自第三方天气 API，卡片语义由后端规则生成，LLM 完全不参与这条 workspace 链路。


## 5. 当前“按位置查天气”的实现方式

这里不是浏览器定位，而是服务端根据请求 IP 做大致位置判断：先从请求头或 `remoteAddr` 里提取客户端 IP，如果是公网 IP，就调 IP 定位服务并尝试补齐到更细地区；拿到坐标后，再按坐标查天气。本地开发环境下后端通常只能看到 `127.0.0.1`、`192.168.x.x`、`10.x.x.x` 之类私网地址，所以这里专门加了本地兜底，默认回退到 `杭州市钱塘区`。这不是 bug，而是本地开发场景下拿不到真实公网位置的自然限制。


## 6. 这次新增和改动了哪些后端代码

### 6.1 新增：IP 定位服务

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/IpLocationService.java`
- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/IpLocationServiceImpl.java`
- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/support/IpLocationSnapshot.java`

职责：

- 根据 IP 查询大致地区
- 尽量补齐到区县级
- 返回显示名和坐标
- 对本地私网 IP 做兜底


### 6.2 新增：请求 IP 解析工具

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/util/ClientIpUtils.java`

职责：

- 从常见代理头中提取客户端真实 IP
- 兼容 `X-Forwarded-For` 等头
- 识别私网 / 本地 IP


### 6.3 扩展：天气查询服务

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/WeatherQueryService.java`
- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/WeatherQueryServiceImpl.java`

这次做了两类扩展：

1. 保留原来的“按城市名查天气”
2. 增加新的“按已知坐标查天气”

这样 workspace 在已经知道位置坐标时，不需要再绕回“城市名 -> 地理编码”。


### 6.4 改造：workspace 服务层

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/WorkspaceService.java`
- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/service/impl/WorkspaceServiceImpl.java`

职责变化：

原来：

- 直接按固定地区查天气

现在：

- 接收客户端 IP
- 先做 IP 定位
- 再查天气
- 最后生成卡片展示字段

这一层仍然负责“展示语义规则”，例如：

- 标题怎么写
- 温度趋势怎么判断
- 是否建议带伞
- 穿衣建议怎么生成


### 6.5 改造：workspace 接口层

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/controller/WorkspaceController.java`

职责变化：

原来只是直接调天气卡片服务。

现在会：

- 从 `HttpServletRequest` 中提取客户端 IP
- 再把 IP 传给 `WorkspaceService`


### 6.6 保留并扩展：Agent 可复用能力

文件：

- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/agent/tool/impl/GetTodayWeatherTool.java`
- `/Users/dora/Documents/项目/code/JAgent/backend/src/main/java/com/dora/jagent/agent/tool/impl/LocateIpRegionTool.java`

这一步的思想不是“workspace 依赖 agent”。

而是反过来：

- 先把底层能力做好
- workspace 直接用 service
- agent 再复用同一套能力作为 tool

所以现在：

- `get_today_weather` 继续保留
- 新增了 `locate_ip_region`

以后如果智能体要处理“先判断 IP 在哪，再继续做事”的任务，这套基础能力已经准备好了。


## 7. 这次实现里最重要的工程判断

### 判断一：快捷卡片不等于聊天

不是所有“按钮点一下得到结果”的需求都应该走 Agent。

如果页面上只需要一个稳定、简短、直接显示的结果：

- 通常更适合专用后端接口
- 而不是走 LLM 会话


### 判断二：结构化数据优先，语义展示后置

天气数据本身是结构化的。

所以最稳的做法是：

1. 先拿结构化数据
2. 再由后端做规则化解释

而不是：

1. 先让 LLM 生成一段描述
2. 再让前端或后端去猜这些描述是什么意思


### 判断三：把“位置能力”抽成独立服务

如果位置定位只写在某一个天气函数内部，那么后面复用会很麻烦。

抽成独立的 `IpLocationService` 之后：

- workspace 能用
- agent tool 能用
- 以后别的快捷能力也能用


## 8. 如果以后再接类似需求，可以怎么自然落地

以后如果再出现类似“workspace 上一个按钮，一点就有结果”的需求，可以优先按这个顺序判断：

1. 它是不是聊天需求？  
   如果不是，就别先想着走 agent 对话。

2. 它的结果是不是固定结构？  
   如果是，就优先返回专用 response，而不是聊天文本。

3. 它是不是依赖实时外部数据？  
   如果是，优先用 service 直接查数据源，不要默认接 LLM。

4. 它有没有能抽出来的中间能力？  
   例如这次的：
   - IP 定位
   - 天气查询

5. 抽出来的中间能力未来 agent 能不能复用？  
   如果能，就在 service 之外再补同款 tool。


## 9. 当前这版天气功能的最终定位

这次落下来的天气功能，可以这样定义：

**它不是“一个智能体对话能力的按钮入口”，而是“workspace 页面上的一个纯后端快捷卡片能力”。**

它的价值在于：

- 用户体验更直接
- 展示更稳定
- 后端职责更清楚
- 后续复用空间更大

同时，它又没有和 agent 生态割裂：

- 底层天气查询能力还在
- IP 定位能力也抽出来了
- 后面智能体要复用，已经有可接入点
