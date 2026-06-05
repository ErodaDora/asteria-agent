# Asteria

Asteria is an AI agent workbench for research reading, local knowledge workflows,
and tool-augmented assistance.

It brings together a Spring AI based backend agent runtime, RAG-powered document
search, MCP tool integration, Markdown note workflows, and lightweight browser
workspaces. The project is built as a practical laboratory for turning "chat
with a model" into "let an agent search, call tools, observe results, and keep
working".

## What Asteria Does

- **Research agent**: retrieve documents with vector search, summarize context,
  and use knowledge snippets inside an agent loop.
- **Tool calling**: expose local Java tools and external MCP tools through one
  runtime path.
- **Notes workflow**: scan Markdown notes, parse frontmatter, sync pages to
  Notion, and write stable page IDs back to local files.
- **Agent workspace**: provide static web pages for chat, agent chat, research,
  workspace sync, and image/watermark tool experiments.

## Architecture

```text
User workspace
    |
    v
Spring Boot backend
    |
    +-- Agent runtime: Agent + Runtime + Tool
    +-- Spring AI chat clients
    +-- PostgreSQL / pgvector knowledge search
    +-- Redis-backed verification flow
    +-- Local tools and MCP tool bridge
    +-- Static browser workspaces
```

## Tech Stack

- Java 17
- Spring Boot 3.5
- Spring AI 1.1
- PostgreSQL / pgvector
- Redis
- JWT
- Maven multi-module project

## Repository Layout

```text
.
├── backend      # Spring Boot API and static workspace pages
├── tool-core    # Shared tool protocol and runtime abstractions
├── mcp-server   # Standalone MCP server module
└── sql          # Database schema and seed scripts
```

## Configuration

Copy the example file and fill in your local values:

```bash
cp .env.example .env
```

Asteria reads secrets from environment variables. Keep real API keys, database
passwords, mail authorization codes, Notion tokens, cookies, and MCP credentials
out of version control.

Common variables:

- `JAGENT_DB_URL`, `JAGENT_DB_USERNAME`, `JAGENT_DB_PASSWORD`
- `JAGENT_REDIS_HOST`, `JAGENT_REDIS_PORT`
- `JAGENT_JWT_SECRET`
- `JAGENT_LLM_BASE_URL`, `JAGENT_LLM_API_KEY`, `JAGENT_LLM_MODEL`
- `NOTION_TOKEN`, `NOTION_DATABASE_ID`, `NOTION_PAGE_ID`

## Run Locally

Start PostgreSQL and Redis first, then initialize the database with the scripts
under `sql/` as needed.

```bash
cd backend
mvn spring-boot:run
```

Default entry:

```text
http://127.0.0.1:8081/
```

Useful pages:

- `/` - login and entry page
- `/chat.html` - basic chat
- `/agent-chat.html` - agent chat workspace
- `/research-agent.html` - research agent page
- `/workspace.html` - local workspace and notes sync
- `/image-agent.html` - image and watermark agent page

## Status

Asteria is a personal research and engineering workbench. Some features require
external services such as LLM providers, Ollama embeddings, Notion, or MCP
servers. The repository keeps those integrations configurable rather than
hard-coded.
