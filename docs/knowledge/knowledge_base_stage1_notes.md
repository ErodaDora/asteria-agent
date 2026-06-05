# 知识库 Stage 1 极简笔记

## 目标

先不做检索，先把知识库作为一个系统能力建起来：

1. 能创建知识库
2. 能把文档挂到知识库下
3. 能上传 markdown 文档
4. 上传后自动完成解析、embedding、chunk 入库

一句话：**先把“建库”跑通，再谈“查库”。**

---

## 思维链路

### 1. 先分清两条流程

- 低频建库流程：创建知识库、上传文档、解析、向量化、入库
- 高频查库流程：用户提问、检索 chunk、返回给 Agent

这一阶段只做第一条。

### 2. 先建容器，再放内容

不是先上传文件，而是先有：

- `knowledge_base`

然后才有：

- `document`
- `chunk_bge_m3`

因为文档必须先知道“属于哪个知识库”。

### 3. 先做管理层，再做 RAG 层

所以顺序定成：

1. SQL 表
2. entity / repository
3. knowledge base service
4. knowledge document service
5. markdown parser
6. ingest 链路
7. 前端入口

---

## 代码修改流程

### 第一步：补知识库底层表

新增：

- [knowledge.sql](/Users/dora/Documents/项目/code/jagent/sql/knowledge.sql)

包含三张表：

- `knowledge_base`
- `document`
- `chunk_bge_m3`

作用：

- 给后面知识库、文档、chunk、向量检索提供存储底座

### 第二步：补 entity 和 repository

新增：

- `KnowledgeBase`
- `KnowledgeDocument`
- `KnowledgeChunkBgeM3`

以及对应 repository / jdbc repository。

作用：

- 先让代码能表示这三类数据
- 先让代码能做最小增查

### 第三步：补知识库管理 service

新增：

- [KnowledgeBaseService.java](/Users/dora/Documents/项目/code/jagent/backend/src/main/java/com/dora/jagent/service/KnowledgeBaseService.java)
- [KnowledgeBaseServiceImpl.java](/Users/dora/Documents/项目/code/jagent/backend/src/main/java/com/dora/jagent/service/impl/KnowledgeBaseServiceImpl.java)
- [KnowledgeBaseController.java](/Users/dora/Documents/项目/code/jagent/backend/src/main/java/com/dora/jagent/controller/KnowledgeBaseController.java)

作用：

- 创建知识库
- 查询知识库列表
- 查询单个知识库

核心思想：

**知识库先是一个空容器，不是上传文件后才临时生成。**

### 第四步：补文档挂载 service

新增：

- `KnowledgeDocumentService`
- `KnowledgeDocumentServiceImpl`
- `KnowledgeDocumentController`

作用：

- 把文档记录挂到某个知识库下面
- 固定 `knowledge_base -> document` 关系

核心思想：

**先让系统知道“文档属于谁”，再处理文档内容。**

### 第五步：补 markdown 解析层

新增：

- [MarkdownParserService.java](/Users/dora/Documents/项目/code/jagent/backend/src/main/java/com/dora/jagent/service/MarkdownParserService.java)
- [MarkdownParserServiceImpl.java](/Users/dora/Documents/项目/code/jagent/backend/src/main/java/com/dora/jagent/service/impl/MarkdownParserServiceImpl.java)

并在：

- [pom.xml](/Users/dora/Documents/项目/code/jagent/backend/pom.xml)

加入 `flexmark-all`。

作用：

- 按 Markdown 标题拆 section
- 输出 `(title, content)` 列表

核心思想：

**后面 embedding 不是对整篇文档做，而是对 section 的标题做。**

### 第六步：补 ingest 链路

新增：

- `DocumentStorageService`
- `DocumentStorageServiceImpl`
- `KnowledgeEmbeddingService`
- `KnowledgeEmbeddingServiceImpl`

并扩展：

- `KnowledgeDocumentService.uploadDocument(...)`

作用：

上传 markdown 后自动做：

1. 保存原文件
2. 生成 document 记录
3. 解析 markdown
4. 对标题做 embedding
5. 把正文和向量写入 `chunk_bge_m3`

核心思想：

**上传不等于直接进 prompt，上传是进入待检索状态。**

### 第七步：补最小前端入口

更新：

- [workspace.html](/Users/dora/Documents/项目/code/jagent/backend/src/main/resources/static/workspace.html)
- [workspace.js](/Users/dora/Documents/项目/code/jagent/backend/src/main/resources/static/workspace.js)
- [workspace.css](/Users/dora/Documents/项目/code/jagent/backend/src/main/resources/static/workspace.css)

现在前端支持：

- 新建知识库
- 查看知识库列表
- 上传文档到当前知识库
- 查看当前知识库下的文档

---

## 当前做到哪

现在已经完成的是：

- 知识库管理
- 文档上传
- markdown 自动解析
- 标题 embedding
- chunk 自动入库

现在还没完成的是：

- `similaritySearch`
- `KnowledgeTool`
- Agent 真正调用知识库检索
- `allowedKbs` 配置

一句话总结：

**现在已经把“知识放进去”做完了，下一阶段才是“让智能体把知识拿出来”。**
