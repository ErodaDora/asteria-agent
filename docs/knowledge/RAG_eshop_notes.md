# RAG 与 `eshop.md` 学习笔记

## 1. 这一章在讲什么

在 `JChatMindV1 / V2` 已经具备：

- 工具调用能力
- Agent Loop

之后，新的问题不是“缺实时数据”，而是“缺系统内部知识”，例如：

- 项目文档
- 设计说明
- 业务规则
- 数据库结构说明

这类信息不适合硬塞进 system prompt，也不能指望模型自己记住，所以需要引入 RAG。

这一章的核心判断是：

- `RAG 不是模型能力，而是系统能力`
- 在 Agent 体系里，RAG 不是特殊模式，而是一个工具入口

也就是：

1. 用户提问
2. Agent 判断当前是否缺背景信息
3. 如果缺，就调用 `KnowledgeTool`
4. 系统做检索
5. 返回相关文档片段
6. Agent 基于这些片段继续回答

Agent 最关心的一件事是：**现在缺的背景信息是什么**。

---

## 2. 知识库与 RAG 两条主线

这一章其实分两条线：

### 2.1 把内容放进知识库

流程是：

1. 文档拆分成 chunk
2. 对 chunk 的可检索部分做 embedding
3. 把 embedding 和文本内容一起存入数据库

在 `jchatmind` 里使用的是：

- 向量模型：`bge-m3`
- 存储：`PostgreSQL + pgvector`

### 2.2 从知识库里把内容找出来

流程是：

1. 用户问题到来
2. 对问题做 embedding
3. 在向量库中做相似度搜索
4. 取 Top-K 结果
5. 把命中的内容作为补充上下文交给 LLM
6. LLM 生成最终回答

一句话概括：

- `RAG 负责找对信息`
- `LLM 负责把信息说清楚`

---

## 3. 为什么文档必须先拆分

如果把整篇文档直接交给模型，会有几个问题：

- 文档太长，容易超上下文
- 用户问题通常只和其中一小部分相关
- token 成本高
- 干扰信息太多

所以第一步不是立刻 embedding，而是先把文档结构整理清楚。

教材这里采用的是一个很适合入门工程的拆分策略：

- **按 Markdown 标题拆分**

也就是把每个标题以及它下面直到下一个标题之前的内容，视为一个语义单元。

---

## 4. `eshop.md` 为什么这样写

`eshop.md` 不是普通“知识说明文”，而是**数据库设计规格文档**。

它的写法是：

- 开头先给出全局约束
- 再给出设计目标
- 再列出表清单
- 再逐表写用途、字段要点、DDL

因此它同时承担两种作用：

1. 给人看，解释为什么这么设计
2. 给系统用，作为 RAG 检索和后续生成 SQL/实现的上游输入

所以开头那段“说明”不是备注，而是全局规则，例如：

- 主键统一 `UUID + gen_random_uuid()`
- 时间统一 `TIMESTAMPTZ`
- 扩展字段统一 `JSONB`
- 评论相关表如何组织

这些规则会直接影响后面的表设计和生成结果。

而每张表下面既有“用途/字段要点”，又有 DDL，是因为这份文档不只是要“被检索”，还要“能驱动实现”。

---

## 5. `eshop.md` 的知识结构

以 `eshop.md` 为例，文档内容大致分成：

- 全局说明
- 设计目标
- 表清单
- 每张表的独立章节

表清单里主要包括：

- `t_app_user`
- `t_role`
- `t_user_role`
- `t_product_category`
- `t_product`
- `t_product_category_relation`
- `t_order_header`
- `t_order_item`
- `t_payment`
- `t_shipment`
- `t_comment_topic`
- `t_comment`
- `t_comment_topic_mapping`
- `t_comment_summary_daily`
- `t_system_kv`

其中重点不只是“有哪些表”，而是每张表本身就是一个独立语义块，比如：

- 标题：`3.1 用户表 t_app_user`
- 内容：用途、字段要点、建表 SQL

这就很适合被拆成一个 chunk。

---

## 6. `jchatmind` 怎么拆 `eshop.md`

### 6.1 拆分原则

教材里给出的思路是：

- 每篇 Markdown 文档拆成多块
- 每一块分成两部分：
  - 标题
  - 内容

在 `jchatmind` 的实现里，对应接口是：

- [MarkdownParserService.java](/Users/dora/Documents/项目/code/jchatmind/jchatmind/src/main/java/com/kama/jchatmind/service/MarkdownParserService.java:13)

它定义的结果结构就是：

- `title`
- `content`

### 6.2 具体实现

对应实现文件：

- [MarkdownParserServiceImpl.java](/Users/dora/Documents/项目/code/jchatmind/jchatmind/src/main/java/com/kama/jchatmind/service/impl/MarkdownParserServiceImpl.java:19)

这段实现做的事是：

1. 解析 Markdown AST
2. 找出顶层标题节点
3. 从当前标题开始，收集到下一个标题之前的内容
4. 输出一组 `MarkdownSection(title, content)`

补充点：

- 表格内容做了特殊处理，尽量保留原始 Markdown 格式
- 这是为了避免表结构说明在解析后失真

---

## 7. 为什么只对“标题”做 Embedding

教材这里是一个很关键的设计点：

- **不对整段内容 embedding**
- **只对章节标题 embedding**
- **正文原样保存**

原因有三个：

1. 标题本身就是这一段内容的高度概括
2. 标题更短、更稳定，embedding 成本更低
3. 真正要交给模型阅读的是正文，不是标题

可以把它记成一句话：

- `标题负责找`
- `内容负责看`

这正是 `jchatmind` 当前实现的选择。

在文档入库时：

- `title` 用来生成 embedding
- `content` 直接存文本

对应实现文件：

- [DocumentFacadeServiceImpl.java](/Users/dora/Documents/项目/code/jchatmind/jchatmind/src/main/java/com/kama/jchatmind/service/impl/DocumentFacadeServiceImpl.java:184)

这里的关键逻辑是：

1. 上传 Markdown 文档
2. 解析出多个 section
3. 对每个 `section.title` 调 `ragService.embed(title)`
4. 把 `section.content` 作为 chunk 文本存入 `chunk_bge_m3`

注意：当前实现里 `title` 没有单独存列，embedding 是由标题算出来的，但最终落库的主要文本是 `content`。

---

## 8. 向量库是怎么存的

`jchatmind` 的知识库核心表是：

- `knowledge_base`
- `document`
- `chunk_bge_m3`

对应 SQL 文件：

- [jchatmind.sql](/Users/dora/Documents/项目/code/jchatmind/examples/jchatmind_assert/jchatmind.sql:43)

其中 `chunk_bge_m3` 的关键字段是：

- `kb_id`
- `doc_id`
- `content`
- `metadata`
- `embedding VECTOR(1024)`

这说明：

- 业务库和向量库没有分开
- 直接使用 PostgreSQL + pgvector

优点是：

- 不需要单独引入向量数据库
- 查询链路清晰
- 便于调试和观察

---

## 9. 相似度检索是怎么做的

对应接口：

- [RagService.java](/Users/dora/Documents/项目/code/jchatmind/jchatmind/src/main/java/com/kama/jchatmind/service/RagService.java:5)

对应实现：

- [RagServiceImpl.java](/Users/dora/Documents/项目/code/jchatmind/jchatmind/src/main/java/com/kama/jchatmind/service/impl/RagServiceImpl.java:12)

当前检索链路是：

1. 对查询文本做 embedding
2. 转成 pgvector 字面量
3. 在指定 `kb_id` 范围内做相似度排序
4. 取前 `3` 条 chunk
5. 返回 chunk 的 `content`

对应 SQL：

- [ChunkBgeM3Mapper.xml](/Users/dora/Documents/项目/code/jchatmind/jchatmind/src/main/resources/mapper/ChunkBgeM3Mapper.xml:91)

核心语句是：

```sql
SELECT ...
FROM chunk_bge_m3
WHERE kb_id = CAST(#{kbId} AS uuid)
ORDER BY embedding <-> #{vectorLiteral}::vector
LIMIT #{limit}
```

这里的 `embedding <-> query_vector` 就是 pgvector 的相似度搜索入口。

要点是：

- embedding 只是“索引键”
- 真正返回给 Agent 的仍然是原始文本内容

---

## 10. RAG 在 Agent 世界里的入口

Agent 并不直接知道 `RagService`，它只知道一个工具：

- `KnowledgeTool`

对应文件：

- [KnowledgeTools.java](/Users/dora/Documents/项目/code/jchatmind/jchatmind/src/main/java/com/kama/jchatmind/agent/tools/KnowledgeTools.java:9)

它暴露的方法是：

```java
knowledgeQuery(String kbsId, String query)
```

做的事情很简单：

1. 调 `ragService.similaritySearch(kbsId, query)`
2. 拿到若干命中结果
3. 拼成文本返回给 Agent

所以在 Agent 视角里，RAG 就是一种工具调用，和“查天气”“读文件”在模式上没有本质区别。

---

## 11. `jchatmind` 基于 `eshop` 实现了什么

围绕 `eshop.md`，`jchatmind` 实际完成的是一个最小可跑通的工程型 RAG 链路：

1. 可以创建知识库
2. 可以上传 Markdown 文档到知识库
3. 上传后自动解析 `eshop.md`
4. 按 Markdown 标题拆成多个 section
5. 对每个 section 的标题做 embedding
6. 将正文内容和向量写入 `chunk_bge_m3`
7. Agent 在需要时通过 `KnowledgeTool` 检索
8. 把命中的数据库设计片段交给 LLM 用于回答或继续推理

也就是说，它不是“让模型记住数据库设计”，而是让系统具备：

- **按需取出数据库设计说明**
- **再把这段说明交给模型使用**

---

## 12. 为了实现它，相关文件是按什么思路写的

可以把这套文件理解成四层：

### 12.1 知识源文件层

- `eshop.md`

职责：

- 用结构化 Markdown 写数据库设计规格
- 既面向人，也面向 RAG 拆分

### 12.2 文档解析层

- `MarkdownParserService`
- `MarkdownParserServiceImpl`

职责：

- 把 Markdown 转成 `(title, content)` 章节对

### 12.3 检索存储层

- `DocumentFacadeServiceImpl`
- `RagService`
- `RagServiceImpl`
- `ChunkBgeM3Mapper.xml`

职责：

- 文档上传后入库
- 标题向量化
- chunk 存储
- 相似度检索

### 12.4 Agent 工具层

- `KnowledgeTools`

职责：

- 给 Agent 一个稳定、简单的 RAG 调用入口

---

## 13. 这一套设计的核心思想

`jchatmind` 对 `eshop` 这个案例的处理，不是“把整篇数据库文档直接塞给模型”，而是：

1. 先把数据库设计文档写成结构化 Markdown
2. 再把每个章节视作最小知识单元
3. 用标题负责检索定位
4. 用正文负责提供真实上下文
5. 最终通过 `KnowledgeTool` 挂到 Agent 上

所以这份 `eshop.md` 的本质是：

- 上游是数据库设计规格文档
- 中游被拆成可检索 chunk
- 下游变成 Agent 的 RAG 知识源

一句话收束：

**`jchatmind` 用 `eshop.md` 演示的，不是“文档问答”而已，而是“把工程规格文档做成 Agent 可按需调用的系统知识”。**
