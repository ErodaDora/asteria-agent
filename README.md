# JAgent Workbench

JAgent Workbench is a Spring Boot based AI Agent workbench for experimenting with
agent runtime, tool calling, RAG search, MCP integration, and local knowledge
workflows. It includes a Java backend, static web workspaces, PostgreSQL-backed
chat/agent/knowledge modules, and optional integrations such as Notion sync,
watermark tools, and content workflow tools.

## Features

- **Agent runtime**: `Agent + Runtime + Tool` execution chain with a bounded
  think-execute-reply loop.
- **Tool calling**: local Java tools and external MCP tools share a unified
  registration and invocation path.
- **RAG knowledge search**: document storage, chunking, embedding, pgvector
  similarity search, and `knowledge_query` tool integration.
- **Markdown / Notion workflow**: scan local Markdown notes, parse frontmatter,
  create or update Notion pages, and write `notion_page_id` back to local notes.
- **Static workspaces**: browser pages for login, chat, agent chat, research
  agent, image/watermark agent, workspace, and content workflow experiments.

## Tech Stack

- Java 17
- Spring Boot 3.5
- Spring AI 1.1
- PostgreSQL / pgvector
- Redis
- JWT
- Maven multi-module project

## Project Structure

```text
.
├── backend      # Spring Boot API + static web pages
├── tool-core    # Shared tool protocol and runtime abstractions
├── mcp-server   # Standalone MCP server module
└── sql          # Database schema and seed scripts
```

## Configuration

Copy the example environment file and fill in local values:

```bash
cp .env.example .env
```

The application reads secrets from environment variables. Do not commit real API
keys, database passwords, mail authorization codes, Notion tokens, cookies, or
MCP credentials.

Important variables:

- `JAGENT_DB_URL`, `JAGENT_DB_USERNAME`, `JAGENT_DB_PASSWORD`
- `JAGENT_REDIS_HOST`, `JAGENT_REDIS_PORT`
- `JAGENT_JWT_SECRET`
- `JAGENT_LLM_BASE_URL`, `JAGENT_LLM_API_KEY`, `JAGENT_LLM_MODEL`
- `NOTION_TOKEN`, `NOTION_DATABASE_ID`, `NOTION_PAGE_ID`

## Run Locally

Start PostgreSQL and Redis first, then initialize the database with scripts in
`sql/` as needed.

Run the backend:

```bash
cd backend
mvn spring-boot:run
```

Default server:

```text
http://127.0.0.1:8081/
```

Useful pages:

- `/` - login / entry page
- `/chat.html` - basic chat
- `/agent-chat.html` - agent chat workspace
- `/research-agent.html` - research agent page
- `/workspace.html` - local workspace / notes sync page
- `/image-agent.html` - image / watermark agent page

## Notes

This project is an experimental workbench. Some optional abilities depend on
external services such as LLM providers, Ollama embeddings, Notion, and MCP
servers. Keep those credentials and runtime data outside version control.
