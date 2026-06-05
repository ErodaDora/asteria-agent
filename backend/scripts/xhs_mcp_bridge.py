#!/usr/bin/env python3
import argparse
import asyncio
import json
import sys
import traceback
from typing import Any

from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client


def print_json(payload: dict[str, Any]) -> None:
    sys.stdout.write(json.dumps(payload, ensure_ascii=False))
    sys.stdout.flush()


async def list_tools(endpoint: str) -> dict[str, Any]:
    async with streamablehttp_client(endpoint) as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            tools_result = await session.list_tools()
            return {"success": True, "tools": [tool.name for tool in tools_result.tools]}


async def call_tool(endpoint: str, tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    async with streamablehttp_client(endpoint) as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            result = await session.call_tool(tool_name, arguments=arguments)

    payload: dict[str, Any] = {"success": True}
    content = getattr(result, "content", None) or []
    if content:
        first = content[0]
        text = getattr(first, "text", "")
        if text:
            payload["raw"] = text
            try:
                parsed = json.loads(text)
            except json.JSONDecodeError:
                return payload
            if isinstance(parsed, dict):
                payload.update(parsed)
            else:
                payload["data"] = parsed
    return payload


async def run(args: argparse.Namespace) -> dict[str, Any]:
    if args.action == "list-tools":
        return await list_tools(args.endpoint)
    if args.action == "call-tool":
        tool_args = json.loads(args.args or "{}")
        return await call_tool(args.endpoint, args.tool, tool_args)
    raise ValueError(f"Unsupported action: {args.action}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--endpoint", required=True)
    parser.add_argument("--action", required=True, choices=["list-tools", "call-tool"])
    parser.add_argument("--tool")
    parser.add_argument("--args")
    args = parser.parse_args()

    try:
        result = asyncio.run(run(args))
        print_json(result)
        return 0
    except Exception as exc:
        print_json({"success": False, "error": str(exc), "traceback": traceback.format_exc()})
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
