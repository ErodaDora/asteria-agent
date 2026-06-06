# Paper Research Stage 1

本阶段目标是把 `research-agent.html` 从前端直连 OpenAlex 的搜索页，升级为可继续扩展的论文检索闭环：

```text
OpenAlex 论文元数据检索
        ↓
后端统一规整 venue / source / PDF 候选链接
        ↓
前端展示候选论文
        ↓
收藏入库，缓存论文元数据
        ↓
摘要级 BGE-M3 向量索引
        ↓
收藏库语义检索，后续接 PDF 解析和 Notion 同步
```

## 代码修改

### 后端 API

- 新增 `PaperResearchController`
  - `GET /api/research/papers/search`
  - `GET /api/research/papers/collections`
  - `POST /api/research/papers/collections`
  - `POST /api/research/papers/{paperId}/index`
  - `POST /api/research/papers/collections/{collectionName}/index`
  - `GET /api/research/papers/semantic-search`

- 新增 `PaperResearchService` / `PaperResearchServiceImpl`
  - 将 OpenAlex 检索从前端迁移到后端。
  - 保留 venue alias 识别，例如 `ACM / ACMMM / ACM MM` 识别为 `ACM International Conference on Multimedia`。
  - 优先使用 OpenAlex source id 过滤 ACM MM、TMM 等来源。
  - 支持 `fromYear` / `toYear` 年份范围过滤；`toYear` 为空时表示从起始年份往后检索。
  - 当 proceedings 类 source id 覆盖不足时，会追加一次 venue 关键词宽召回，再在本地按 venue 名称过滤。
  - 从 OpenAlex work 中抽取标题、作者、年份、来源、DOI、摘要、landing page 和 PDF 候选链接。
  - 支持收藏入库，重复收藏同一 OpenAlex 论文时复用已有论文记录。

- 新增 `PaperIndexService` / `PaperIndexServiceImpl`
  - 将收藏论文的 `title + authors + source_name + publication_year + abstract_text` 作为 embedding 输入。
  - 调用现有 `KnowledgeEmbeddingService`，使用 BGE-M3 生成 1024 维向量。
  - 写入专用论文 chunk 表 `research_paper_chunk_bge_m3`。
  - 支持单篇论文索引、收藏库批量索引、收藏库语义检索。
  - 论文索引完成后，将收藏状态从 `saved` 更新为 `indexed`。

### 数据模型

- 新增 `ResearchPaper`
  - 表示可复用的论文元数据缓存。
  - 关注 OpenAlex 返回的稳定字段，而不是论文全文。

- 新增 `ResearchPaperCollection`
  - 表示收藏行为和流程状态。
  - MVP 阶段使用 `default` collection，后续可以扩展用户维度或项目维度。

- 新增 `ResearchPaperChunkBgeM3`
  - 表示论文 RAG 专用 chunk。
  - 当前只实现 `abstract` chunk，后续 PDF 正文可以扩展为 `pdf_page` 或 `section`。
  - `content` 保存可回显文本，`embedding_text` 保存实际送入模型的检索文本，`embedding` 保存 pgvector 向量。

- 新增响应模型
  - `ResearchPaperView`
  - `PaperSearchResponse`
  - `ResearchPaperCollectionView`
  - `ResearchPaperChunkView`
  - `PaperIndexResponse`

- 新增请求模型
  - `SaveResearchPaperRequest`

### 数据访问

- 新增 `ResearchPaperRepository` / `JdbcResearchPaperRepository`
  - 按 `id` 查询。
  - 按 `openalex_id` 查询。
  - 按 `doi` 查询。
  - 使用 `ON CONFLICT (openalex_id)` 做幂等保存。

- 新增 `ResearchPaperCollectionRepository` / `JdbcResearchPaperCollectionRepository`
  - 查询收藏列表。
  - 按 `paper_id + collection_name` 查询。
  - 使用唯一约束避免同一集合重复收藏同一篇论文。
  - 支持更新收藏状态，例如 `saved -> indexed`。

- 新增 `ResearchPaperChunkBgeM3Repository` / `JdbcResearchPaperChunkBgeM3Repository`
  - 使用 `(paper_id, chunk_type, chunk_index)` 做幂等 upsert。
  - 使用 pgvector 的 `<->` 距离运算符做相似度检索。
  - 检索时 join `research_paper_collection`，只在指定收藏集合内搜索。

### 前端

- 重写 `research-agent.html`
  - 三栏工作台结构：检索条件、论文结果、收藏库。

- 重写 `research-agent.js`
  - 改为调用后端 `/api/research/papers/search`。
  - 支持论文结果渲染、PDF 候选入口、来源入口、收藏入库。
  - 页面初始化时加载收藏库。
  - 支持单篇索引、收藏库批量索引和收藏库语义检索。

- 重写 `research-agent.css`
  - 使用偏 Notion 的简约产品界面：浅色背景、低边框、安静列表、明确动作按钮。
  - 去掉原本偏装饰的玻璃拟态，让检索和收藏动作更清晰。

## 论文实体定义

`research_paper` 只缓存论文元数据，不保存 PDF 全文。

| 字段 | 说明 |
|---|---|
| `id` | 本地 UUID 主键 |
| `openalex_id` | OpenAlex work id，唯一 |
| `doi` | DOI |
| `title` | 论文标题 |
| `abstract_text` | 摘要文本，当前可用于摘要级 RAG |
| `publication_year` | 发表年份 |
| `source_name` | 期刊、会议或论文集名称 |
| `source_type` | OpenAlex source 类型，例如 `journal` / `conference` |
| `authors` | 作者列表，MVP 阶段用逗号拼接 |
| `landing_page_url` | publisher 或 DOI 页面 |
| `pdf_url` | OpenAlex 返回的开放获取 PDF 候选链接，可能为空 |
| `metadata` | JSONB 扩展字段，例如引用数、OpenAlex 原始链接、source id |
| `created_at` / `updated_at` | 创建与更新时间 |

`research_paper_collection` 记录收藏行为。

| 字段 | 说明 |
|---|---|
| `id` | 本地 UUID 主键 |
| `paper_id` | 外键，指向 `research_paper.id` |
| `collection_name` | 收藏集合名称，MVP 默认 `default` |
| `note` | 人工备注或后续自动摘要 |
| `status` | 流程状态，当前默认 `saved` |
| `created_at` / `updated_at` | 创建与更新时间 |

`research_paper_chunk_bge_m3` 记录论文 RAG 专用分块和向量。

| 字段 | 说明 |
|---|---|
| `id` | 本地 UUID 主键 |
| `paper_id` | 外键，指向 `research_paper.id` |
| `chunk_type` | 分块类型，当前为 `abstract`，后续可扩展 `pdf_page` / `section` |
| `chunk_index` | 同一论文同一类型下的分块序号 |
| `content` | 可回显给用户的原文片段 |
| `embedding_text` | 实际送入 BGE-M3 的检索文本 |
| `metadata` | JSONB 引用信息，例如标题、年份、DOI、来源 |
| `embedding` | BGE-M3 生成的 1024 维 pgvector |
| `created_at` / `updated_at` | 创建与更新时间 |

## 数据库建表语句

完整 SQL 位于：

```text
sql/research_paper.sql
```

执行方式：

```bash
psql -d jchatmind -f sql/research_paper.sql
```

核心表：

```sql
CREATE TABLE IF NOT EXISTS research_paper (
    id UUID PRIMARY KEY,
    openalex_id VARCHAR(64) UNIQUE,
    doi VARCHAR(255),
    title TEXT NOT NULL,
    abstract_text TEXT,
    publication_year INTEGER,
    source_name VARCHAR(255),
    source_type VARCHAR(64),
    authors TEXT,
    landing_page_url TEXT,
    pdf_url TEXT,
    metadata JSONB,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS research_paper_collection (
    id UUID PRIMARY KEY,
    paper_id UUID NOT NULL,
    collection_name VARCHAR(100) NOT NULL DEFAULT 'default',
    note TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'saved',
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_research_paper_collection_paper
        FOREIGN KEY (paper_id) REFERENCES research_paper (id),
    CONSTRAINT uk_research_paper_collection_paper
        UNIQUE (paper_id, collection_name)
);

CREATE TABLE IF NOT EXISTS research_paper_chunk_bge_m3 (
    id UUID PRIMARY KEY,
    paper_id UUID NOT NULL,
    chunk_type VARCHAR(32) NOT NULL,
    chunk_index INTEGER NOT NULL DEFAULT 0,
    content TEXT NOT NULL,
    embedding_text TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding VECTOR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_research_paper_chunk_paper
        FOREIGN KEY (paper_id) REFERENCES research_paper (id) ON DELETE CASCADE,
    CONSTRAINT uk_research_paper_chunk
        UNIQUE (paper_id, chunk_type, chunk_index)
);
```

## 下一阶段

1. 论文问答
   - 在语义检索结果基础上接 LLM。
   - 将标题、年份、DOI、来源和摘要 chunk 注入 prompt。
   - 生成带引用的答案，而不是只返回命中的 chunk。

2. PDF 下载与解析
   - 使用 `pdf_url` 作为开放获取 PDF 候选。
   - 下载后用 PDFBox 或 GROBID 抽取正文。
   - 按页码和章节生成 chunk，实现页码级引用。

3. Notion 同步
   - 基于已有 Notion 客户端，将收藏论文、摘要和阅读笔记同步为 Notion 页面。
