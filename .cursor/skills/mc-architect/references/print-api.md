# Print API

默认 `PRINTCORE_URL=http://127.0.0.1:9471`，`PRINTCORE_TOKEN=printcore-dev-token`。Header：`Authorization: Bearer <token>`。

## HTTP

- `GET /v1/status`
- `GET /v1/registry/blocks?prefix=`
- `GET /v1/world/blocks?x1&y1&z1&x2&y2&z2&dimension=`
- `GET /v1/world/heightmap?x1&z1&x2&z2&type=&dimension=`
- `POST /v1/schematics`（body=`.schem`，`X-Filename`）
- `POST /v1/jobs`（print_plan JSON）
- `POST /v1/jobs/{id}/dry-run`
- `POST /v1/jobs/{id}/commit`
- `POST /v1/jobs/{id}/rollback`
- `GET /v1/jobs/{id}`

游戏内：`/printcore status|validate <id>|print <id>|rollback <id>`（权限 2）。

## MCP 工具

`printcore_status`、`printcore_registry`、`printcore_heightmap`、`printcore_get_blocks`、`compile_ir`、`validate_job`、`submit_job`、`dry_run_job`、`commit_job`、`rollback_job`、`job_status`。

Cursor 配置示例：

```json
{
  "mcpServers": {
    "printcore": {
      "command": "python3",
      "args": ["-m", "printcore_mcp", "serve"],
      "env": {
        "PYTHONPATH": "compiler:mcp",
        "PRINTCORE_URL": "http://127.0.0.1:9471",
        "PRINTCORE_TOKEN": "printcore-dev-token"
      }
    }
  }
}
```

作业状态：`SUBMITTED` → `DRY_RUN_OK` → `RUNNING` → `COMPLETED`。失败为 `FAILED`，回滚为 `ROLLED_BACK`。
