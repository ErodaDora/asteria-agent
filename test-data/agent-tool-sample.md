# Agent Tool Sample

这是给 JAgent 第一个工具测试准备的本地 markdown 文件。

## 文件目标

1. 证明 Agent 已经不只是普通聊天，而是可以调用本地文本读取工具。
2. 让前端会话里能够出现一条 `tool` 类型的执行轨迹消息。
3. 给后续做“摘要、提炼重点、生成行动项”这类二次处理提供简单样例。

## 三个关键信息

- JAgent 当前已经支持 `read_local_text_file` 工具。
- 这个工具只允许读取工作区内的文本类文件。
- 读取结果会先回到 Agent，再由 Agent 组织最终回复给用户。

## 推荐测试问题

你可以在 Agent 页面里输入：

请读取 `test-data/agent-tool-sample.md`，然后总结里面的三个重点。
