# JAgent 数据库初始化指南

## 问题分析
前端显示"暂无可用智能体"，这是因为数据库表中没有智能体数据。

## 解决方案

### 1. 执行 SQL 文件（按顺序）

在 PostgreSQL 中依次执行以下 SQL 文件，创建表结构和初始数据：

#### 第一步：创建用户表
```bash
psql -U dora -d jchatmind -f /Users/dora/Documents/项目/code/JAgent/sql/user.sql
```

#### 第二步：创建知识库表
```bash
psql -U dora -d jchatmind -f /Users/dora/Documents/项目/code/JAgent/sql/knowledge.sql
```

#### 第三步：创建智能体表和初始数据 ★ **最重要**
```bash
psql -U dora -d jchatmind -f /Users/dora/Documents/项目/code/JAgent/sql/agent.sql
```

这个文件会：
- 创建 `jagent_agent` 表
- 插入一个默认智能体："Basic Agent"（ID: 10000000-0000-0000-0000-000000000001）
- 配置默认系统提示词和默认模型（deepseek-chat）

#### 第四步：创建聊天会话表
```bash
psql -U dora -d jchatmind -f /Users/dora/Documents/项目/code/JAgent/sql/chat.sql
```

#### 第五步：应用聊天会话补丁（添加 agent_id 外键）
```bash
psql -U dora -d jchatmind -f /Users/dora/Documents/项目/code/JAgent/sql/chat_agent_patch.sql
```

#### 第六步：应用智能体补丁（添加 allowed_kbs 列）
```bash
psql -U dora -d jchatmind -f /Users/dora/Documents/项目/code/JAgent/sql/agent_allowed_kbs_patch.sql
```

#### 第七步：应用聊天摘要补丁
```bash
psql -U dora -d jchatmind -f /Users/dora/Documents/项目/code/JAgent/sql/chat_summary_patch.sql
```

### 2. 一键执行所有 SQL 文件

或者直接运行这条命令，一次性执行所有 SQL：

```bash
cd /Users/dora/Documents/项目/code/JAgent/sql && \
psql -U dora -d jchatmind < user.sql && \
psql -U dora -d jchatmind < knowledge.sql && \
psql -U dora -d jchatmind < agent.sql && \
psql -U dora -d jchatmind < chat.sql && \
psql -U dora -d jchatmind < chat_agent_patch.sql && \
psql -U dora -d jchatmind < agent_allowed_kbs_patch.sql && \
psql -U dora -d jchatmind < chat_summary_patch.sql
```

### 3. 验证数据库初始化

执行以下查询验证表是否创建成功：

```sql
-- 检查智能体表
SELECT * FROM jagent_agent;

-- 应该返回：
-- id: 10000000-0000-0000-0000-000000000001
-- name: Basic Agent
-- description: 最小智能体入门，用于承接后续工具调用与任务编排能力。
```

## 前端现状分析

- ✅ 前端已有完整的智能体功能
- ✅ 前端已有查询智能体的接口调用 (`/api/agents`)
- ✅ 前端侧栏可以显示智能体列表
- ✅ 后端控制器已实现 `/api/agents` 接口

## 后续：如何创建新的智能体

目前系统中 **暂未实现前端创建智能体的功能**。如果需要创建新的智能体，有两种方式：

### 方式1：直接插入 SQL
```sql
INSERT INTO jagent_agent (
    id, name, description, system_prompt, 
    default_model_key, allowed_kbs, created_at, updated_at
) VALUES (
    gen_random_uuid(),
    '我的智能体',
    '我的智能体描述',
    '你是一个智能的AI助手。',
    'deepseek-chat',
    '[]'::jsonb,
    NOW(),
    NOW()
);
```

### 方式2：实现后端接口
可以在 `AgentController` 中添加 POST 接口来创建智能体：
```java
@PostMapping
public ApiResponse<AgentView> createAgent(@RequestBody CreateAgentRequest request) {
    return ApiResponse.success(agentService.createAgent(request));
}
```

然后在前端 `workspace.html` 添加"新建智能体"按钮。

## 总结

| 问题 | 解决方案 |
|------|---------|
| 前端无法显示智能体 | 执行 SQL 文件初始化数据库 |
| 没有"新建智能体"按钮 | 这是预期的，因为功能尚未实现 |
| 不知道怎么创建新智能体 | 可以直接 SQL 插入，或实现后端接口 |

