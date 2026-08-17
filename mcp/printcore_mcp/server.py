"""Minimal MCP stdio server for PrintCore. No third-party MCP SDK required."""

from __future__ import annotations

import json
import sys
from typing import Any

from .cli import compile_ir_file, submit_compiled
from .client import PrintCoreClient, PrintCoreError

TOOLS = [
    {
        "name": "printcore_status",
        "description": "Check whether a PrintCore HTTP endpoint is reachable and return TPS/job queue.",
        "inputSchema": {"type": "object", "properties": {}},
    },
    {
        "name": "printcore_registry",
        "description": "List registered block ids, optionally filtered by prefix.",
        "inputSchema": {
            "type": "object",
            "properties": {"prefix": {"type": "string"}},
        },
    },
    {
        "name": "printcore_heightmap",
        "description": "Read a heightmap rectangle from the live world.",
        "inputSchema": {
            "type": "object",
            "required": ["x1", "z1", "x2", "z2"],
            "properties": {
                "x1": {"type": "integer"},
                "z1": {"type": "integer"},
                "x2": {"type": "integer"},
                "z2": {"type": "integer"},
                "dimension": {"type": "string"},
                "type": {"type": "string"},
            },
        },
    },
    {
        "name": "printcore_get_blocks",
        "description": "Read blocks in a bounded volume. Do not use this to dump large builds.",
        "inputSchema": {
            "type": "object",
            "required": ["x1", "y1", "z1", "x2", "y2", "z2"],
            "properties": {
                "x1": {"type": "integer"},
                "y1": {"type": "integer"},
                "z1": {"type": "integer"},
                "x2": {"type": "integer"},
                "y2": {"type": "integer"},
                "z2": {"type": "integer"},
                "dimension": {"type": "string"},
            },
        },
    },
    {
        "name": "compile_ir",
        "description": "Compile a terrain/structure/city IR JSON file into schem + print_plan. Works offline.",
        "inputSchema": {
            "type": "object",
            "required": ["ir"],
            "properties": {
                "ir": {"type": "string"},
                "out": {"type": "string"},
                "palette": {"type": "string"},
            },
        },
    },
    {
        "name": "validate_job",
        "description": "Upload compiled artifacts if needed and dry-run a print plan against the live world.",
        "inputSchema": {
            "type": "object",
            "required": ["out"],
            "properties": {"out": {"type": "string", "description": "compiler output directory"}},
        },
    },
    {
        "name": "submit_job",
        "description": "Submit a compiled print_plan directory as a PrintCore job (does not print until commit).",
        "inputSchema": {
            "type": "object",
            "required": ["out"],
            "properties": {"out": {"type": "string"}},
        },
    },
    {
        "name": "dry_run_job",
        "description": "Dry-run an existing job id.",
        "inputSchema": {
            "type": "object",
            "required": ["job_id"],
            "properties": {"job_id": {"type": "string"}},
        },
    },
    {
        "name": "commit_job",
        "description": "Commit a dry-run-ok job so PrintCore starts tick-sliced printing.",
        "inputSchema": {
            "type": "object",
            "required": ["job_id"],
            "properties": {"job_id": {"type": "string"}},
        },
    },
    {
        "name": "rollback_job",
        "description": "Restore the snapshot taken before commit.",
        "inputSchema": {
            "type": "object",
            "required": ["job_id"],
            "properties": {"job_id": {"type": "string"}},
        },
    },
    {
        "name": "job_status",
        "description": "Get job status, message, and blocksApplied.",
        "inputSchema": {
            "type": "object",
            "required": ["job_id"],
            "properties": {"job_id": {"type": "string"}},
        },
    },
]


def serve_stdio() -> None:
    client = PrintCoreClient()
    while True:
        line = sys.stdin.readline()
        if not line:
            return
        line = line.strip()
        if not line:
            continue
        try:
            message = json.loads(line)
        except json.JSONDecodeError:
            continue
        response = handle(message, client)
        if response is not None:
            sys.stdout.write(json.dumps(response) + "\n")
            sys.stdout.flush()


def handle(message: dict[str, Any], client: PrintCoreClient) -> dict[str, Any] | None:
    method = message.get("method")
    msg_id = message.get("id")
    if method == "initialize":
        return _result(
            msg_id,
            {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "printcore", "version": "0.1.0"},
            },
        )
    if method == "notifications/initialized" or method == "initialized":
        return None
    if method == "tools/list":
        return _result(msg_id, {"tools": TOOLS})
    if method == "tools/call":
        params = message.get("params") or {}
        name = params.get("name")
        args = params.get("arguments") or {}
        try:
            text = json.dumps(call_tool(name, args, client), indent=2)
            return _result(msg_id, {"content": [{"type": "text", "text": text}]})
        except Exception as exc:  # noqa: BLE001
            return _result(
                msg_id,
                {"content": [{"type": "text", "text": str(exc)}], "isError": True},
            )
    if msg_id is not None:
        return {"jsonrpc": "2.0", "id": msg_id, "error": {"code": -32601, "message": f"unknown method {method}"}}
    return None


def call_tool(name: str, args: dict[str, Any], client: PrintCoreClient) -> Any:
    if name == "compile_ir":
        return compile_ir_file(args["ir"], args.get("out"), args.get("palette"))
    if name == "printcore_status":
        if not client.reachable():
            return {"reachable": False, "hint": "PrintCore HTTP is down. Compile IR only; do not claim the world was printed."}
        return {"reachable": True, **client.status()}
    if name == "printcore_registry":
        return client.registry(args.get("prefix", ""))
    if name == "printcore_heightmap":
        return client.heightmap(args["x1"], args["z1"], args["x2"], args["z2"], args.get("dimension", "minecraft:overworld"), args.get("type", "MOTION_BLOCKING_NO_LEAVES"))
    if name == "printcore_get_blocks":
        return client.blocks(args["x1"], args["y1"], args["z1"], args["x2"], args["y2"], args["z2"], args.get("dimension", "minecraft:overworld"))
    if name == "submit_job":
        return submit_compiled(args["out"], client)
    if name == "validate_job":
        submitted = submit_compiled(args["out"], client)
        return client.dry_run(submitted["id"])
    if name == "dry_run_job":
        return client.dry_run(args["job_id"])
    if name == "commit_job":
        return client.commit(args["job_id"])
    if name == "rollback_job":
        return client.rollback(args["job_id"])
    if name == "job_status":
        return client.job(args["job_id"])
    raise PrintCoreError(400, f"unknown tool {name}")


def _result(msg_id: Any, result: Any) -> dict[str, Any]:
    return {"jsonrpc": "2.0", "id": msg_id, "result": result}


if __name__ == "__main__":
    serve_stdio()
